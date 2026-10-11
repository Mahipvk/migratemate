package io.github.mahipvk.migratemate;

import io.github.mahipvk.migratemate.core.BuildFailure;
import io.github.mahipvk.migratemate.core.BuildLogParser;
import io.github.mahipvk.migratemate.core.ChangeGuard;
import io.github.mahipvk.migratemate.core.FileBlocks;
import io.github.mahipvk.migratemate.core.FixPlanner;
import io.github.mahipvk.migratemate.core.PomBumper;
import io.github.mahipvk.migratemate.core.ReportWriter;
import io.github.mahipvk.migratemate.core.ReportWriter.Iteration;
import io.github.mahipvk.migratemate.core.ReportWriter.Outcome;
import io.github.mahipvk.migratemate.core.Shell;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The agent loop:
 * copy project → baseline build → deterministic upgrade (OpenRewrite or version bump)
 * → build → [ask model for fixes → guardrails → apply → build] until green or a stop condition.
 */
@Service
public class UpgradeAgent {

    public enum Mode {
        /** OpenRewrite recipes, then the AI fixes what is left. */
        FULL,
        /** OpenRewrite recipes only (no AI). Baseline for the eval. */
        RECIPES,
        /** Only bump versions, then the AI does all the work. */
        AI;

        public static Mode parse(String s) {
            return Mode.valueOf(s.trim().toUpperCase(Locale.ROOT));
        }
    }

    private static final Set<String> SKIP_DIRS = Set.of("target", ".git", ".idea", "node_modules", ".migratemate");

    private final MigrateMateProperties props;
    private final AiFixer ai;

    public UpgradeAgent(MigrateMateProperties props, AiFixer ai) {
        this.props = props;
        this.ai = ai;
    }

    public Outcome run(Path source, Mode mode) {
        long start = System.currentTimeMillis();
        Path src = source.toAbsolutePath().normalize();
        if (!Files.isRegularFile(src.resolve("pom.xml"))) {
            throw new IllegalArgumentException("No pom.xml in " + src + " (only Maven projects are supported for now)");
        }
        String name = src.getFileName().toString();
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path work = Path.of(props.workDir()).toAbsolutePath().resolve(name + "-" + mode.name().toLowerCase(Locale.ROOT) + "-" + stamp);

        say("Copying " + src + "\n   -> " + work + "  (your original is never touched)");
        copyProject(src, work);
        write(work.resolve(".gitignore"), "target/\n.migratemate/\nMIGRATION_REPORT.md\n", true);
        git(work, "init", "-q");
        commit(work, "baseline");
        git(work, "tag", "baseline");

        say("Baseline build (before upgrade)...");
        Shell.Result baseline = mvnTest(work);
        sayResult(baseline);

        boolean recipesApplied = false;
        if (mode == Mode.FULL || mode == Mode.RECIPES) {
            say("Running OpenRewrite recipes: " + props.recipes());
            Shell.Result rewrite = mvn(work, List.of("-U", "org.openrewrite.maven:rewrite-maven-plugin:run",
                    "-Drewrite.recipeArtifactCoordinates=" + props.recipeCoordinates(),
                    "-Drewrite.activeRecipes=" + props.recipes(),
                    "-Drewrite.exportDatatables=false"));
            recipesApplied = rewrite.ok();
            if (!recipesApplied) {
                say("   OpenRewrite failed, continuing without it. Last lines:\n" + rewrite.tail(15));
            }
            commit(work, "OpenRewrite recipes");
        } else {
            say("Bumping pom.xml to Spring Boot " + props.targetSpringBootVersion() + " / Java " + props.targetJavaVersion());
            Path pom = work.resolve("pom.xml");
            write(pom, PomBumper.bump(read(pom), props.targetSpringBootVersion(), props.targetJavaVersion()), false);
            commit(work, "Version bump");
        }

        say("Build after upgrade step...");
        Shell.Result build = mvnTest(work);
        sayResult(build);

        List<Iteration> iterations = new ArrayList<>();
        String stopReason;
        if (mode == Mode.RECIPES) {
            stopReason = "recipes-only mode (no AI)";
        } else {
            stopReason = aiLoop(work, build, iterations);
            build = lastBuild;
        }

        String diffStat = git(work, "diff", "--stat", "baseline", "HEAD").output().strip();
        Outcome outcome = new Outcome(name, mode.name().toLowerCase(Locale.ROOT), mode == Mode.RECIPES ? "-" : ai.model(),
                baseline.ok(), BuildLogParser.testSummary(baseline.output()), recipesApplied, iterations,
                build.ok(), BuildLogParser.testSummary(build.output()), stopReason, diffStat,
                (System.currentTimeMillis() - start) / 1000, work);

        String report = ReportWriter.markdown(outcome);
        write(work.resolve("MIGRATION_REPORT.md"), report, false);
        Path reports = Path.of("reports").toAbsolutePath();
        write(reports.resolve(work.getFileName() + ".md"), report, false);
        ReportWriter.appendCsv(Path.of("eval", "results.csv").toAbsolutePath(), outcome);
        return outcome;
    }

    private Shell.Result lastBuild;

    private String aiLoop(Path work, Shell.Result build, List<Iteration> iterations) {
        lastBuild = build;
        Set<String> previousKeys = Set.of();
        int sameErrorsStreak = 0;
        int noChangeStreak = 0;
        String feedback = "";

        for (int i = 1; ; i++) {
            if (lastBuild.ok()) {
                return "all tests pass";
            }
            if (i > props.maxIterations()) {
                return "reached max iterations (" + props.maxIterations() + ")";
            }
            List<BuildFailure> failures = BuildLogParser.parse(lastBuild.output());
            if (noChangeStreak == 0) { // only compare after a real rebuild
                Set<String> keys = failures.stream().map(BuildFailure::key).collect(Collectors.toSet());
                sameErrorsStreak = keys.equals(previousKeys) ? sameErrorsStreak + 1 : 0;
                previousKeys = keys;
                if (sameErrorsStreak >= 2) {
                    return "no progress: same errors after 2 applied fixes";
                }
            }

            FixPlanner.FixRequest request = new FixPlanner(work, props.maxFilesPerIteration()).plan(failures, lastBuild.tail(60));
            if (request.files().isEmpty()) {
                return "could not find the files behind the errors";
            }
            say("AI iteration " + i + ": " + failures.size() + " problem(s), sending " + request.files().size() + " file(s) to the model");
            failures.stream().limit(5).forEach(f -> say("   - " + shorten(f.describe().replace(work + "/", ""), 150)));

            String prompt = ai.buildPrompt(request, i, feedback);
            write(work.resolve(".migratemate/iter-" + i + "-prompt.md"), prompt, false);
            String answer;
            try {
                answer = ai.ask(prompt);
            } catch (RuntimeException e) {
                return "model error: " + e.getMessage();
            }
            write(work.resolve(".migratemate/iter-" + i + "-answer.md"), answer == null ? "" : answer, false);

            List<String> changed = new ArrayList<>();
            List<String> rejected = new ArrayList<>();
            List<String> unchanged = new ArrayList<>();
            Set<String> allowed = new HashSet<>(request.files().keySet());
            Map<String, String> proposed = FileBlocks.parse(answer);
            for (Map.Entry<String, String> e : proposed.entrySet()) {
                String path = e.getKey();
                Path target = work.resolve(path).normalize();
                String before = Files.isRegularFile(target) && target.startsWith(work) ? read(target) : null;
                var reason = ChangeGuard.check(path, before, e.getValue(), allowed);
                if (reason.isPresent()) {
                    if (reason.get().equals("no change")) {
                        unchanged.add(path);
                    } else {
                        rejected.add(path + " (" + reason.get() + ")");
                        say("   BLOCKED " + path + ": " + reason.get());
                    }
                    continue;
                }
                write(target, e.getValue(), false);
                changed.add(path);
                say("   changed " + path);
            }
            iterations.add(new Iteration(i, failures.size(), changed, rejected));

            if (changed.isEmpty()) {
                // Self-correction: tell the model exactly why its answer was not usable.
                StringBuilder fb = new StringBuilder();
                if (proposed.isEmpty()) {
                    fb.append("- Your answer did not contain any `=== FILE: ... ===` block.\n");
                }
                unchanged.forEach(p -> fb.append("- You returned `").append(p).append("` without any change.\n"));
                rejected.forEach(r -> fb.append("- Rejected by guardrails: ").append(r).append('\n'));
                fb.append("- The failing behaviour must be fixed in one of: ").append(String.join(", ", request.files().keySet()))
                        .append(". Return that file with the fix applied.\n");
                feedback = fb.toString();
                say("   no usable change; retrying with feedback");
                if (++noChangeStreak >= 3) {
                    return "model proposed no usable changes 3 times in a row";
                }
                continue; // same build output, ask again
            }
            feedback = "";
            noChangeStreak = 0;
            commit(work, "AI fix iteration " + i);
            say("Rebuilding...");
            lastBuild = mvnTest(work);
            sayResult(lastBuild);
        }
    }

    // ---------- helpers ----------

    private Shell.Result mvnTest(Path work) {
        return mvn(work, List.of("clean", "test"));
    }

    private Shell.Result mvn(Path work, List<String> args) {
        List<String> cmd = new ArrayList<>();
        Path wrapper = work.resolve("mvnw");
        if (Files.isRegularFile(wrapper)) {
            wrapper.toFile().setExecutable(true);
            cmd.add(wrapper.toString());
        } else {
            cmd.add("mvn");
        }
        cmd.addAll(List.of("-B", "-ntp", "-Dstyle.color=never"));
        cmd.addAll(args);
        return Shell.run(work, Duration.ofMinutes(props.buildTimeoutMinutes()), cmd);
    }

    private static Shell.Result git(Path work, String... args) {
        List<String> cmd = new ArrayList<>(List.of("git", "-c", "user.name=MigrateMate", "-c", "user.email=migratemate@local"));
        cmd.addAll(List.of(args));
        return Shell.run(work, Duration.ofMinutes(2), cmd);
    }

    private static void commit(Path work, String message) {
        git(work, "add", "-A");
        git(work, "commit", "-q", "--allow-empty", "-m", message);
    }

    private static void copyProject(Path from, Path to) {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                Path rel = from.relativize(p);
                if (rel.toString().isEmpty()) {
                    Files.createDirectories(to);
                    continue;
                }
                boolean skip = false;
                for (Path part : rel) {
                    if (SKIP_DIRS.contains(part.toString())) {
                        skip = true;
                        break;
                    }
                }
                if (skip) {
                    continue;
                }
                Path dest = to.resolve(rel.toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(p, dest);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path p, String content, boolean append) {
        try {
            Files.createDirectories(p.getParent());
            if (append) {
                Files.writeString(p, content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.writeString(p, content);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void sayResult(Shell.Result r) {
        if (r.ok()) {
            say("   PASS  (" + BuildLogParser.testSummary(r.output()) + ", " + r.millis() / 1000 + " s)");
        } else {
            int n = BuildLogParser.parse(r.output()).size();
            say("   FAIL  (" + n + " problem(s), tests: " + BuildLogParser.testSummary(r.output()) + ", " + r.millis() / 1000 + " s)");
        }
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    static void say(String s) {
        System.out.println(s);
    }
}
