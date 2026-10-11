package io.github.mahipvk.migratemate.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs a command in a directory and captures its output (via a temp file, so large logs never block). */
public final class Shell {

    public record Result(int exitCode, String output, long millis) {
        public boolean ok() {
            return exitCode == 0;
        }

        public String tail(int lines) {
            String[] all = output.split("\\R");
            int from = Math.max(0, all.length - lines);
            return String.join("\n", List.of(all).subList(from, all.length));
        }
    }

    private Shell() {
    }

    public static Result run(Path dir, Duration timeout, List<String> command) {
        long start = System.currentTimeMillis();
        Path log = null;
        try {
            log = Files.createTempFile("migratemate-", ".log");
            Process p = new ProcessBuilder(command)
                    .directory(dir.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .start();
            boolean finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
            }
            String out = Files.readString(log);
            int code = finished ? p.exitValue() : -1;
            if (!finished) {
                out += "\n[MIGRATEMATE] command timed out after " + timeout.toMinutes() + " minutes";
            }
            return new Result(code, out, System.currentTimeMillis() - start);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not run " + String.join(" ", command)
                    + " (is it installed and on your PATH?)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } finally {
            if (log != null) {
                try {
                    Files.deleteIfExists(log);
                } catch (IOException ignored) {
                    // temp file, nothing to do
                }
            }
        }
    }
}
