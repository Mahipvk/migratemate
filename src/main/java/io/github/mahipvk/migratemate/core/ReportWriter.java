package io.github.mahipvk.migratemate.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** Writes MIGRATION_REPORT.md into the upgraded project and appends one row to eval/results.csv. */
public final class ReportWriter {

    public record Iteration(int number, int failuresBefore, List<String> filesChanged, List<String> rejections) {
    }

    public record Outcome(String project, String mode, String model, boolean baselinePassed, String baselineTests,
                          boolean recipesApplied, List<Iteration> iterations, boolean finalPassed, String finalTests,
                          String stopReason, String diffStat, long seconds, Path workDir) {

        public int aiFilesChanged() {
            return iterations.stream().mapToInt(i -> i.filesChanged().size()).sum();
        }

        public int guardRejections() {
            return iterations.stream().mapToInt(i -> i.rejections().size()).sum();
        }
    }

    private ReportWriter() {
    }

    public static String markdown(Outcome o) {
        StringBuilder md = new StringBuilder();
        md.append("# MigrateMate report: ").append(o.project()).append("\n\n");
        md.append("| | |\n|---|---|\n");
        md.append("| Mode | `").append(o.mode()).append("` |\n");
        md.append("| Model | `").append(o.model()).append("` |\n");
        md.append("| Baseline (before upgrade) | ").append(status(o.baselinePassed())).append(" (").append(o.baselineTests()).append(") |\n");
        md.append("| OpenRewrite recipes | ").append(o.recipesApplied() ? "applied" : "not used").append(" |\n");
        md.append("| AI fix iterations | ").append(o.iterations().size()).append(" |\n");
        md.append("| Files changed by AI | ").append(o.aiFilesChanged()).append(" |\n");
        md.append("| Changes blocked by guardrails | ").append(o.guardRejections()).append(" |\n");
        md.append("| **Final build** | **").append(status(o.finalPassed())).append("** (").append(o.finalTests()).append(") |\n");
        md.append("| Stopped because | ").append(o.stopReason()).append(" |\n");
        md.append("| Time | ").append(o.seconds()).append(" s |\n\n");

        if (!o.iterations().isEmpty()) {
            md.append("## AI iterations\n\n");
            for (Iteration it : o.iterations()) {
                md.append("### Iteration ").append(it.number()).append(" (").append(it.failuresBefore()).append(" problems)\n");
                it.filesChanged().forEach(f -> md.append("- changed `").append(f).append("`\n"));
                it.rejections().forEach(r -> md.append("- blocked: ").append(r).append('\n'));
                md.append('\n');
            }
        }
        md.append("## Files changed since baseline\n\n```\n").append(o.diffStat().isBlank() ? "(none)" : o.diffStat()).append("\n```\n\n");
        md.append("See the full diff with `git diff baseline` inside `").append(o.workDir()).append("`.\n");
        md.append("Prompts and model answers for every iteration are in `.migratemate/`.\n");
        return md.toString();
    }

    public static void appendCsv(Path csv, Outcome o) {
        try {
            Files.createDirectories(csv.getParent());
            if (!Files.exists(csv)) {
                Files.writeString(csv, "project,mode,model,baseline,final,final_tests,ai_iterations,ai_files_changed,guard_blocks,seconds,stop_reason\n");
            }
            String row = String.join(",", o.project(), o.mode(), o.model(),
                    status(o.baselinePassed()), status(o.finalPassed()), quote(o.finalTests()),
                    String.valueOf(o.iterations().size()), String.valueOf(o.aiFilesChanged()),
                    String.valueOf(o.guardRejections()), String.valueOf(o.seconds()), quote(o.stopReason())) + "\n";
            Files.writeString(csv, row, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String status(boolean ok) {
        return ok ? "PASS" : "FAIL";
    }

    private static String quote(String s) {
        return "\"" + s.replace("\"", "'") + "\"";
    }
}
