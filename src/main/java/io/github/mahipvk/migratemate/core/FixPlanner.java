package io.github.mahipvk.migratemate.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Decides which files the model gets to see (and may edit) for a set of build failures.
 * Order of priority:
 * 1. files with compile errors,
 * 2. project types mentioned in those errors (e.g. the repository behind "cannot find symbol save"),
 * 3. for failing tests: the production classes in the stack trace / used by the test, then the test itself,
 * 4. fallback when nothing could be located: pom.xml.
 */
public final class FixPlanner {

    /**
     * @param errors  error text for the prompt
     * @param files   files the model may edit
     * @param context read-only files shown for context only (e.g. a passing-to-compile test that defines expected behaviour)
     */
    public record FixRequest(String errors, Map<String, String> files, Map<String, String> context) {
    }

    private static final Pattern TYPE_NAME = Pattern.compile("\\b([A-Z][A-Za-z0-9_]*)\\b");
    private static final Pattern URL_LITERAL = Pattern.compile("\"(/[A-Za-z0-9_\\-/{}]+)");
    private static final int MAX_ERROR_CHARS = 6000;
    private static final int MAX_REPORT_LINES = 40;

    private final Path project;
    private final int maxFiles;
    private final Map<String, String> javaIndex = new LinkedHashMap<>(); // simple name -> relative path

    public FixPlanner(Path project, int maxFiles) {
        this.project = project.toAbsolutePath().normalize();
        this.maxFiles = maxFiles;
        indexJavaFiles();
    }

    public FixRequest plan(List<BuildFailure> failures, String buildLogTail) {
        Set<String> picked = new LinkedHashSet<>();
        Set<String> readOnly = new LinkedHashSet<>();
        StringBuilder errors = new StringBuilder();

        // 1 + 2: compile errors, main code first
        failures.stream()
                .filter(f -> f.kind() == BuildFailure.Kind.COMPILE)
                .sorted((a, b) -> Boolean.compare(a.file().contains("/src/test/"), b.file().contains("/src/test/")))
                .forEach(f -> {
                    String rel = relative(f.file());
                    errors.append("- ").append(rel).append(":").append(f.line()).append("  ").append(f.message()).append('\n');
                    picked.add(rel);
                });
        failures.stream()
                .filter(f -> f.kind() == BuildFailure.Kind.COMPILE)
                .forEach(f -> picked.addAll(typesMentioned(f.message(), true)));

        // 3: failing tests
        for (BuildFailure f : failures) {
            if (f.kind() != BuildFailure.Kind.TEST) {
                continue;
            }
            errors.append("- test ").append(f.testClass()).append('.').append(f.testMethod())
                    .append(f.line() > 0 ? ":" + f.line() : "").append("  ").append(f.message()).append('\n');
            String report = surefireReport(f.testClass());
            if (!report.isBlank() && errors.indexOf(report) < 0) {
                errors.append("  details:\n").append(report.indent(4));
            }
            Set<String> suspects = new LinkedHashSet<>(typesMentioned(report, false));
            Optional.ofNullable(javaIndex.get(f.testClass())).ifPresent(testPath -> {
                String testSource = read(testPath);
                suspects.addAll(controllersForUrls(testSource));
                if (suspects.isEmpty()) {
                    suspects.addAll(typesMentioned(testSource, false));
                }
                // The test compiles and is the spec: show it, but the fix belongs in production code.
                readOnly.add(testPath);
            });
            picked.addAll(suspects);
            if (report.contains("ApplicationContext")) {
                addIfExists(picked, "src/main/resources/application.properties");
                addIfExists(picked, "src/main/resources/application.yml");
            }
        }

        // 4: other failures (dependency resolution, plugin errors...)
        for (BuildFailure f : failures) {
            if (f.kind() == BuildFailure.Kind.OTHER) {
                errors.append("- ").append(f.message()).append('\n');
            }
        }
        if (picked.isEmpty()) {
            errors.append("\nLast lines of the build log:\n").append(buildLogTail);
            addIfExists(picked, "pom.xml");
        }

        readOnly.removeAll(picked); // a file with its own compile error stays editable
        Map<String, String> files = new LinkedHashMap<>();
        for (String rel : picked) {
            if (files.size() >= maxFiles) {
                break;
            }
            if (Files.isRegularFile(project.resolve(rel))) {
                files.put(rel, read(rel));
            }
        }
        Map<String, String> context = new LinkedHashMap<>();
        for (String rel : readOnly) {
            if (Files.isRegularFile(project.resolve(rel))) {
                context.put(rel, read(rel));
            }
        }
        String errorText = errors.length() > MAX_ERROR_CHARS ? errors.substring(0, MAX_ERROR_CHARS) + "\n...(truncated)" : errors.toString();
        return new FixRequest(errorText, files, context);
    }

    /** Project classes whose simple name appears in the text. */
    private Set<String> typesMentioned(String text, boolean includeTests) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = TYPE_NAME.matcher(text);
        while (m.find()) {
            String path = javaIndex.get(m.group(1));
            if (path != null && (includeTests || path.startsWith("src/main/"))) {
                out.add(path);
            }
        }
        return out;
    }

    /**
     * Web tests reference controllers by URL, not by class name. Find main classes whose source
     * contains the first path segments of URLs used in the test (e.g. "/api/accounts").
     */
    private Set<String> controllersForUrls(String testSource) {
        Set<String> prefixes = new LinkedHashSet<>();
        Matcher m = URL_LITERAL.matcher(testSource);
        while (m.find()) {
            String[] parts = m.group(1).split("/");
            // "/api/accounts/1" -> "/api/accounts"; "/accounts" -> "/accounts"
            String prefix = parts.length > 2 ? "/" + parts[1] + "/" + parts[2] : m.group(1);
            prefixes.add(prefix.replaceAll("/+$", ""));
        }
        Set<String> out = new LinkedHashSet<>();
        for (String path : javaIndex.values()) {
            if (!path.startsWith("src/main/")) {
                continue;
            }
            String src = read(path);
            if (!src.contains("Mapping")) {
                continue;
            }
            for (String prefix : prefixes) {
                if (!prefix.isEmpty() && src.contains("\"" + prefix)) {
                    out.add(path);
                    break;
                }
            }
        }
        return out;
    }

    private String surefireReport(String testClass) {
        Path dir = project.resolve("target/surefire-reports");
        if (!Files.isDirectory(dir)) {
            return "";
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith("." + testClass + ".txt")
                            || p.getFileName().toString().equals(testClass + ".txt"))
                    .findFirst()
                    .map(p -> {
                        try (Stream<String> lines = Files.lines(p)) {
                            return String.join("\n", lines.limit(MAX_REPORT_LINES).toList());
                        } catch (IOException e) {
                            return "";
                        }
                    })
                    .orElse("");
        } catch (IOException e) {
            return "";
        }
    }

    private void indexJavaFiles() {
        for (String root : List.of("src/main/java", "src/test/java")) {
            Path dir = project.resolve(root);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> s = Files.walk(dir)) {
                s.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                    String name = p.getFileName().toString().replace(".java", "");
                    javaIndex.putIfAbsent(name, relative(p.toString()));
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private void addIfExists(Set<String> picked, String rel) {
        if (Files.isRegularFile(project.resolve(rel))) {
            picked.add(rel);
        }
    }

    private String relative(String absolute) {
        Path p = Path.of(absolute).toAbsolutePath().normalize();
        if (p.startsWith(project)) {
            return project.relativize(p).toString().replace('\\', '/');
        }
        // macOS: /var/... vs /private/var/... — fall back to matching on "src/"
        String s = absolute.replace('\\', '/');
        int i = s.indexOf("/src/");
        return i >= 0 ? s.substring(i + 1) : s;
    }

    private String read(String rel) {
        try {
            return Files.readString(project.resolve(rel));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
