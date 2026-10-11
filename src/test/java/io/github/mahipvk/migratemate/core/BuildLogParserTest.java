package io.github.mahipvk.migratemate.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildLogParserTest {

    static final String COMPILE_LOG = """
            [INFO] -------------------------------------------------------------
            [ERROR] COMPILATION ERROR :
            [INFO] -------------------------------------------------------------
            [ERROR] /Users/k/work/app/src/main/java/com/bank/service/AccountService.java:[22,26] cannot find symbol
              symbol:   method save(com.bank.model.Account)
              location: variable repository of type com.bank.repository.AccountRepository
            [ERROR] /Users/k/work/app/src/main/java/com/bank/client/FxRateClient.java:[25,58] incompatible types: org.springframework.http.HttpStatusCode cannot be converted to org.springframework.http.HttpStatus
            [INFO] 2 errors
            [ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.13.0:compile (default-compile) on project app: Compilation failure: Compilation failure:
            [ERROR] /Users/k/work/app/src/main/java/com/bank/service/AccountService.java:[22,26] cannot find symbol
            [ERROR]   symbol:   method save(com.bank.model.Account)
            [ERROR]   location: variable repository of type com.bank.repository.AccountRepository
            [ERROR] /Users/k/work/app/src/main/java/com/bank/client/FxRateClient.java:[25,58] incompatible types: org.springframework.http.HttpStatusCode cannot be converted to org.springframework.http.HttpStatus
            [ERROR] -> [Help 1]
            """;

    static final String TEST_LOG = """
            [ERROR] Tests run: 4, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 2.1 s <<< FAILURE! -- in com.bank.web.AccountControllerTest
            [ERROR] com.bank.web.AccountControllerTest.legacyClientsUseTrailingSlash -- Time elapsed: 0.05 s <<< FAILURE!
            [INFO]
            [INFO] Results:
            [ERROR] Failures:
            [ERROR]   AccountControllerTest.legacyClientsUseTrailingSlash:52 Status expected:<200> but was:<404>
            [ERROR] Tests run: 6, Failures: 1, Errors: 0, Skipped: 0
            [ERROR] Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin:3.5.2:test (default-test) on project app: There are test failures.
            [ERROR] Please refer to /Users/k/work/app/target/surefire-reports for the individual test results.
            """;

    @Test
    void parsesCompileErrorsWithDetailAndDeduplicates() {
        List<BuildFailure> failures = BuildLogParser.parse(COMPILE_LOG);
        assertEquals(2, failures.size());
        BuildFailure first = failures.get(0);
        assertEquals(BuildFailure.Kind.COMPILE, first.kind());
        assertEquals(22, first.line());
        assertTrue(first.message().contains("location: variable repository of type com.bank.repository.AccountRepository"));
        assertTrue(failures.get(1).message().startsWith("incompatible types"));
    }

    @Test
    void parsesFailingTests() {
        List<BuildFailure> failures = BuildLogParser.parse(TEST_LOG);
        assertEquals(1, failures.size());
        BuildFailure f = failures.get(0);
        assertEquals(BuildFailure.Kind.TEST, f.kind());
        assertEquals("AccountControllerTest", f.testClass());
        assertEquals("legacyClientsUseTrailingSlash", f.testMethod());
        assertEquals(52, f.line());
        assertEquals("6 run, 1 failed, 0 errors", BuildLogParser.testSummary(TEST_LOG));
    }

    @Test
    void fallsBackToGoalFailure() {
        String log = "[ERROR] Failed to execute goal on project app: Could not resolve dependencies for project x\n";
        List<BuildFailure> failures = BuildLogParser.parse(log);
        assertEquals(1, failures.size());
        assertEquals(BuildFailure.Kind.OTHER, failures.get(0).kind());
    }
}
