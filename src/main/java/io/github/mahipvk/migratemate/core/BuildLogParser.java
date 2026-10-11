package io.github.mahipvk.migratemate.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns raw `mvn test` output into a list of {@link BuildFailure}s. */
public final class BuildLogParser {

    // [ERROR] /path/to/Foo.java:[12,5] cannot find symbol
    private static final Pattern COMPILE = Pattern.compile("^\\[ERROR\\] (/.*?\\.java):\\[(\\d+),\\d+\\] (.*)$");
    // "  symbol:   method save(..)"  or  "[ERROR]   location: variable repo of type ..."
    private static final Pattern CONTINUATION = Pattern.compile("^(?:\\[ERROR\\])?\\s+(symbol|location|required|found|reason):\\s*(.*)$");
    // [ERROR]   AccountControllerTest.trailingSlash:40 Status expected:<200> but was:<404>
    private static final Pattern TEST = Pattern.compile("^\\[ERROR\\]\\s+([A-Z][\\w$]*)\\.([a-z_$][\\w$]*)(?::(\\d+))?\\s+(.*)$");
    // Tests run: 5, Failures: 1, Errors: 0, Skipped: 0
    private static final Pattern TESTS_RUN = Pattern.compile("Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+), Skipped: (\\d+)");
    private static final Pattern GOAL_FAILED = Pattern.compile("^\\[ERROR\\] Failed to execute goal (.*)$");

    private BuildLogParser() {
    }

    public static List<BuildFailure> parse(String log) {
        Map<String, BuildFailure> found = new LinkedHashMap<>();
        String goalFailure = null;

        // compile error being assembled (javac prints "symbol:" / "location:" on following lines)
        String cFile = null;
        int cLine = 0;
        StringBuilder cMsg = null;

        for (String line : log.split("\\R")) {
            Matcher cont = CONTINUATION.matcher(line);
            if (cMsg != null && cont.matches()) {
                cMsg.append(" | ").append(cont.group(1)).append(": ").append(cont.group(2).trim());
                continue;
            }
            if (cMsg != null) {
                BuildFailure f = BuildFailure.compile(cFile, cLine, cMsg.toString());
                found.putIfAbsent(f.key(), f);
                cMsg = null;
            }

            Matcher c = COMPILE.matcher(line);
            if (c.matches()) {
                cFile = c.group(1);
                cLine = Integer.parseInt(c.group(2));
                cMsg = new StringBuilder(c.group(3).trim());
                continue;
            }
            Matcher t = TEST.matcher(line);
            if (t.matches()) {
                int ln = t.group(3) == null ? 0 : Integer.parseInt(t.group(3));
                BuildFailure f = BuildFailure.test(t.group(1), t.group(2), ln, t.group(4).trim());
                found.putIfAbsent(f.key(), f);
                continue;
            }
            Matcher g = GOAL_FAILED.matcher(line);
            if (g.matches() && goalFailure == null) {
                goalFailure = g.group(1).trim();
            }
        }
        if (cMsg != null) {
            BuildFailure f = BuildFailure.compile(cFile, cLine, cMsg.toString());
            found.putIfAbsent(f.key(), f);
        }

        List<BuildFailure> result = new ArrayList<>(found.values());
        if (result.isEmpty() && goalFailure != null) {
            result.add(BuildFailure.other(goalFailure));
        }
        return result;
    }

    /** Returns the final "Tests run" summary, e.g. "5 run, 1 failed, 0 errors", or "-" if tests never ran. */
    public static String testSummary(String log) {
        Matcher m = TESTS_RUN.matcher(log);
        String summary = "-";
        while (m.find()) {
            summary = m.group(1) + " run, " + m.group(2) + " failed, " + m.group(3) + " errors";
        }
        return summary;
    }
}
