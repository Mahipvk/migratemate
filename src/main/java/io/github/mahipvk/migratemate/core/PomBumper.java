package io.github.mahipvk.migratemate.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal, deterministic version bump used by the "ai" mode (no OpenRewrite):
 * Spring Boot parent version and Java version only. Everything else is left to the agent.
 */
public final class PomBumper {

    private static final Pattern PARENT = Pattern.compile(
            "(?s)(<parent>.*?<artifactId>spring-boot-starter-parent</artifactId>.*?<version>)([^<]+)(</version>)");
    private static final Pattern JAVA_VERSION = Pattern.compile("<java\\.version>[^<]*</java\\.version>");
    private static final Pattern COMPILER_PROP = Pattern.compile("<maven\\.compiler\\.(source|target|release)>[^<]*</maven\\.compiler\\.\\1>");
    private static final Pattern PROPERTIES_OPEN = Pattern.compile("<properties>");

    private PomBumper() {
    }

    public static String bump(String pom, String bootVersion, String javaVersion) {
        String out = pom;
        Matcher parent = PARENT.matcher(out);
        if (parent.find()) {
            out = parent.replaceFirst("$1" + Matcher.quoteReplacement(bootVersion) + "$3");
        }
        if (JAVA_VERSION.matcher(out).find()) {
            out = JAVA_VERSION.matcher(out).replaceAll("<java.version>" + javaVersion + "</java.version>");
        } else if (PROPERTIES_OPEN.matcher(out).find()) {
            out = PROPERTIES_OPEN.matcher(out).replaceFirst("<properties>\n        <java.version>" + javaVersion + "</java.version>");
        }
        Matcher compiler = COMPILER_PROP.matcher(out);
        StringBuilder sb = new StringBuilder();
        while (compiler.find()) {
            String name = compiler.group(1);
            compiler.appendReplacement(sb, "<maven.compiler." + name + ">" + javaVersion + "</maven.compiler." + name + ">");
        }
        compiler.appendTail(sb);
        return sb.toString();
    }
}
