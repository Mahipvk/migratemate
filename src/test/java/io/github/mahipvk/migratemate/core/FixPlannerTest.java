package io.github.mahipvk.migratemate.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Uses the real sample project in samples/legacy-bank-app. */
class FixPlannerTest {

    static final Path SAMPLE = Path.of("samples/legacy-bank-app").toAbsolutePath();

    @Test
    void includesTheRepositoryBehindACannotFindSymbolError() {
        String log = "[ERROR] " + SAMPLE + "/src/main/java/com/bank/service/AccountService.java:[22,26] cannot find symbol\n"
                + "  symbol:   method findById(java.lang.Long)\n"
                + "  location: variable repository of type com.bank.repository.AccountRepository\n";
        var request = new FixPlanner(SAMPLE, 5).plan(BuildLogParser.parse(log), "");
        List<String> files = List.copyOf(request.files().keySet());
        assertEquals("src/main/java/com/bank/service/AccountService.java", files.get(0));
        assertTrue(files.contains("src/main/java/com/bank/repository/AccountRepository.java"));
    }

    @Test
    void findsTheControllerForAFailingWebTestByUrl() {
        String log = "[ERROR]   AccountControllerTest.legacyClientsUseTrailingSlash:52 Status expected:<200> but was:<404>\n";
        var request = new FixPlanner(SAMPLE, 5).plan(BuildLogParser.parse(log), "");
        List<String> files = List.copyOf(request.files().keySet());
        assertEquals(List.of("src/main/java/com/bank/web/AccountController.java"), files);
        // the test is shown as read-only context, so the model fixes production code instead of echoing the test
        assertTrue(request.context().containsKey("src/test/java/com/bank/web/AccountControllerTest.java"));
    }

    @Test
    void retrievesOnlyTheTrailingSlashNoteForA404() throws Exception {
        MigrationNotes notes = MigrationNotes.parse(java.nio.file.Files.readString(
                Path.of("src/main/resources/prompts/migration-notes.md")));
        var hits = notes.relevantTo("- test AccountControllerTest.legacyClientsUseTrailingSlash:51  Status expected:<200> but was:<404>");
        assertEquals(List.of("trailing-slash"), hits.stream().map(MigrationNotes.Note::name).toList());
    }
}
