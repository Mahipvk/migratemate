package io.github.mahipvk.migratemate.core;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeGuardTest {

    static final String TEST_PATH = "src/test/java/com/bank/FooTest.java";
    static final String TEST_BEFORE = """
            class FooTest {
                @Test void a() { assertEquals(1, foo()); }
                @Test void b() { assertTrue(bar()); }
            }
            """;

    @Test
    void allowsNormalFix() {
        String after = TEST_BEFORE.replace("foo()", "fooV2()");
        assertTrue(ChangeGuard.check(TEST_PATH, TEST_BEFORE, after, Set.of(TEST_PATH)).isEmpty());
    }

    @Test
    void blocksRemovedTest() {
        String after = "class FooTest {\n    @Test void a() { assertEquals(1, foo()); }\n}\n";
        assertEquals("removes test methods", ChangeGuard.check(TEST_PATH, TEST_BEFORE, after, Set.of(TEST_PATH)).orElseThrow());
    }

    @Test
    void blocksRemovedAssertion() {
        String after = TEST_BEFORE.replace("assertTrue(bar());", "bar();");
        assertEquals("removes assertions", ChangeGuard.check(TEST_PATH, TEST_BEFORE, after, Set.of(TEST_PATH)).orElseThrow());
    }

    @Test
    void blocksDisabledTest() {
        String after = TEST_BEFORE.replace("@Test void b()", "@Disabled @Test void b()");
        assertEquals("disables tests", ChangeGuard.check(TEST_PATH, TEST_BEFORE, after, Set.of(TEST_PATH)).orElseThrow());
    }

    @Test
    void blocksFilesNotShownToAgent() {
        assertEquals("file was not given to the agent",
                ChangeGuard.check("src/main/java/Other.java", "class Other {}", "class Other { }", Set.of(TEST_PATH)).orElseThrow());
    }

    @Test
    void blocksSkippingTestsInPom() {
        String before = "<project><properties></properties></project>";
        String after = "<project><properties><skipTests>true</skipTests></properties></project>";
        assertEquals("tries to skip tests in pom.xml", ChangeGuard.check("pom.xml", before, after, Set.of("pom.xml")).orElseThrow());
    }

    @Test
    void blocksTruncatedFile() {
        String path = "src/main/java/com/bank/Big.java";
        String before = "public class Big {\n" + "    int x;\n".repeat(100) + "}\n";
        String after = "public class Big {\n}\n";
        assertTrue(ChangeGuard.check(path, before, after, Set.of(path)).orElseThrow().contains("truncated"));
    }
}
