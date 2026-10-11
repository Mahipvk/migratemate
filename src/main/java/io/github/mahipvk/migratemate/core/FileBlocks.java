package io.github.mahipvk.migratemate.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The simple text format used to send files to the model and read its answer:
 * <pre>
 * === FILE: src/main/java/Foo.java ===
 * ...full content...
 * === END FILE ===
 * </pre>
 */
public final class FileBlocks {

    private static final Pattern BLOCK = Pattern.compile("(?s)=== FILE: (.+?) ===\\R(.*?)\\R?=== END FILE ===");
    private static final Pattern OPEN_FENCE = Pattern.compile("^\\s*```[\\w-]*\\s*\\R");
    private static final Pattern CLOSE_FENCE = Pattern.compile("\\R?\\s*```\\s*$");

    private FileBlocks() {
    }

    public static String format(String path, String content) {
        return "=== FILE: " + path + " ===\n" + content.stripTrailing() + "\n=== END FILE ===\n";
    }

    /** Parses the model's answer. Strips markdown code fences that small models like to add. */
    public static Map<String, String> parse(String text) {
        Map<String, String> files = new LinkedHashMap<>();
        if (text == null) {
            return files;
        }
        Matcher m = BLOCK.matcher(text);
        while (m.find()) {
            String path = m.group(1).trim().replace('\\', '/');
            String body = m.group(2);
            body = OPEN_FENCE.matcher(body).replaceFirst("");
            body = CLOSE_FENCE.matcher(body).replaceFirst("");
            files.put(path, body.stripTrailing() + "\n");
        }
        return files;
    }
}
