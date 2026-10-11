package io.github.mahipvk.migratemate;

import io.github.mahipvk.migratemate.core.ReportWriter.Outcome;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.nio.file.Path;
import java.util.List;

/**
 * Usage:
 *   java -jar target/migratemate.jar --project=samples/legacy-bank-app [--mode=full|recipes|ai]
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MigrateMateApplication implements ApplicationRunner {

    private final UpgradeAgent agent;

    public MigrateMateApplication(UpgradeAgent agent) {
        this.agent = agent;
    }

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(MigrateMateApplication.class, args)));
    }

    @Override
    public void run(ApplicationArguments args) {
        String project = first(args.getOptionValues("project"),
                args.getNonOptionArgs().isEmpty() ? null : args.getNonOptionArgs().get(0));
        if (project == null) {
            System.out.println("Usage: java -jar target/migratemate.jar --project=<path to Maven project> [--mode=full|recipes|ai]");
            return;
        }
        UpgradeAgent.Mode mode = UpgradeAgent.Mode.parse(first(args.getOptionValues("mode"), "full"));

        System.out.println("\n=== MigrateMate: upgrading " + project + " (mode: " + mode.name().toLowerCase() + ") ===\n");
        Outcome o = agent.run(Path.of(project), mode);

        System.out.println("\n=== Result ===");
        System.out.println("Baseline:      " + (o.baselinePassed() ? "PASS" : "FAIL") + "  (" + o.baselineTests() + ")");
        System.out.println("After upgrade: " + (o.finalPassed() ? "PASS" : "FAIL") + "  (" + o.finalTests() + ")");
        System.out.println("AI iterations: " + o.iterations().size() + ", files changed by AI: " + o.aiFilesChanged()
                + ", blocked by guardrails: " + o.guardRejections());
        System.out.println("Stopped:       " + o.stopReason());
        System.out.println("Upgraded copy: " + o.workDir());
        System.out.println("Report:        " + o.workDir().resolve("MIGRATION_REPORT.md"));
    }

    private static String first(List<String> values, String fallback) {
        return values == null || values.isEmpty() ? fallback : values.get(0);
    }
}
