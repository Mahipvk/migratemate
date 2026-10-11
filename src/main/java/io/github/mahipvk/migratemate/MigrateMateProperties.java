package io.github.mahipvk.migratemate;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("migratemate")
public record MigrateMateProperties(
        String recipeCoordinates,
        String recipes,
        String targetSpringBootVersion,
        String targetJavaVersion,
        int maxIterations,
        int maxFilesPerIteration,
        int buildTimeoutMinutes,
        String workDir) {
}
