package io.github.mahipvk.migratemate.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileBlocksAndPomTest {

    @Test
    void parsesModelAnswerAndStripsFences() {
        String answer = """
                Sure! Here is the fix:
                === FILE: src/main/java/A.java ===
                ```java
                class A {}
                ```
                === END FILE ===
                === FILE: src/main/java/B.java ===
                class B {}
                === END FILE ===
                """;
        Map<String, String> files = FileBlocks.parse(answer);
        assertEquals(2, files.size());
        assertEquals("class A {}\n", files.get("src/main/java/A.java"));
        assertEquals("class B {}\n", files.get("src/main/java/B.java"));
    }

    @Test
    void roundTripsFormat() {
        String block = FileBlocks.format("pom.xml", "<project/>");
        assertEquals("<project/>\n", FileBlocks.parse(block).get("pom.xml"));
    }

    @Test
    void bumpsBootParentAndJavaVersion() {
        String pom = """
                <project>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>2.7.18</version>
                  </parent>
                  <version>1.0.0</version>
                  <properties>
                    <java.version>1.8</java.version>
                  </properties>
                </project>
                """;
        String out = PomBumper.bump(pom, "3.4.5", "21");
        assertTrue(out.contains("<version>3.4.5</version>"));
        assertTrue(out.contains("<version>1.0.0</version>"), "project version must not change");
        assertTrue(out.contains("<java.version>21</java.version>"));
    }

    @Test
    void picksRelevantNotes() {
        MigrationNotes notes = MigrationNotes.parse("""
                ## jakarta
                keywords: javax
                Use jakarta.
                ## status
                keywords: HttpStatusCode
                Use HttpStatusCode.
                """);
        assertEquals(2, notes.all().size());
        assertEquals("status", notes.relevantTo("incompatible types: HttpStatusCode cannot be converted").get(0).name());
    }
}
