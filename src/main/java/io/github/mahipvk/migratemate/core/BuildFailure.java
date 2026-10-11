package io.github.mahipvk.migratemate.core;

/**
 * One problem found in a Maven build log.
 *
 * @param kind       COMPILE (javac error), TEST (failing test) or OTHER (anything else, e.g. dependency errors)
 * @param file       absolute path of the source file for COMPILE errors, otherwise null
 * @param line       line number when known, otherwise 0
 * @param testClass  simple name of the failing test class for TEST failures
 * @param testMethod name of the failing test method for TEST failures
 * @param message    the error text
 */
public record BuildFailure(Kind kind, String file, int line, String testClass, String testMethod, String message) {

    public enum Kind { COMPILE, TEST, OTHER }

    public static BuildFailure compile(String file, int line, String message) {
        return new BuildFailure(Kind.COMPILE, file, line, null, null, message);
    }

    public static BuildFailure test(String testClass, String testMethod, int line, String message) {
        return new BuildFailure(Kind.TEST, null, line, testClass, testMethod, message);
    }

    public static BuildFailure other(String message) {
        return new BuildFailure(Kind.OTHER, null, 0, null, null, message);
    }

    /** Stable key used to detect when the agent stops making progress. */
    public String key() {
        return switch (kind) {
            case COMPILE -> "C|" + file + "|" + line + "|" + message;
            case TEST -> "T|" + testClass + "." + testMethod + "|" + message;
            case OTHER -> "O|" + message;
        };
    }

    public String describe() {
        return switch (kind) {
            case COMPILE -> file + ":" + line + "  " + message;
            case TEST -> testClass + "." + testMethod + (line > 0 ? ":" + line : "") + "  " + message;
            case OTHER -> message;
        };
    }
}
