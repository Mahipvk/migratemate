package io.github.mahipvk.migratemate.core;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guardrails applied to every file the model wants to change.
 * The agent can only "pass" by fixing code, never by deleting or weakening tests
 * (OWASP LLM Top 10: LLM06 Excessive Agency).
 */
public final class ChangeGuard {

    private static final Pattern TEST_ANNOTATION = Pattern.compile("@(Test|ParameterizedTest|RepeatedTest)\\b");
    private static final Pattern ASSERTION = Pattern.compile("\\b(assert\\w*|andExpect|verify|expect\\w*)\\s*\\(");
    private static final Pattern DISABLED = Pattern.compile("@Disabled\\b");
    private static final Pattern SKIP_TESTS = Pattern.compile("skipTests|maven\\.test\\.skip|testFailureIgnore|<skip>\\s*true");

    private ChangeGuard() {
    }

    /**
     * @param path      project-relative path the model wants to write
     * @param before    current content (null if the file does not exist)
     * @param after     proposed content
     * @param allowed   paths the model was shown in this step
     * @return a rejection reason, or empty if the change is allowed
     */
    public static Optional<String> check(String path, String before, String after, Set<String> allowed) {
        if (path.contains("..") || path.startsWith("/")) {
            return Optional.of("path escapes the project");
        }
        if (!allowed.contains(path)) {
            return Optional.of("file was not given to the agent");
        }
        if (after == null || after.isBlank()) {
            return Optional.of("empty file");
        }
        if (before != null && before.equals(after)) {
            return Optional.of("no change");
        }
        if (path.endsWith(".java")) {
            String typeName = path.substring(path.lastIndexOf('/') + 1, path.length() - ".java".length());
            Pattern decl = Pattern.compile("\\b(class|interface|enum|record|@interface)\\s+" + Pattern.quote(typeName) + "\\b");
            if (!decl.matcher(after).find()) {
                return Optional.of("type " + typeName + " missing (truncated or wrong file?)");
            }
            if (before != null && after.length() < before.length() * 0.3) {
                return Optional.of("file shrank by more than 70% (looks truncated)");
            }
        }
        if (path.contains("src/test/") && before != null) {
            if (count(TEST_ANNOTATION, after) < count(TEST_ANNOTATION, before)) {
                return Optional.of("removes test methods");
            }
            if (count(ASSERTION, after) < count(ASSERTION, before)) {
                return Optional.of("removes assertions");
            }
            if (count(DISABLED, after) > count(DISABLED, before)) {
                return Optional.of("disables tests");
            }
        }
        if (path.endsWith("pom.xml")) {
            int beforeSkips = before == null ? 0 : count(SKIP_TESTS, before);
            if (count(SKIP_TESTS, after) > beforeSkips) {
                return Optional.of("tries to skip tests in pom.xml");
            }
        }
        return Optional.empty();
    }

    static int count(Pattern p, String text) {
        Matcher m = p.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
