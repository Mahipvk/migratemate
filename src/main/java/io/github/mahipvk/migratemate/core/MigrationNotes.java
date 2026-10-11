package io.github.mahipvk.migratemate.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A tiny knowledge base of Spring Boot 3 / Java 21 breaking changes.
 * Notes are picked by keyword match against the current build errors, so the model only sees
 * guidance relevant to the problem in front of it (retrieval, kept deliberately simple).
 */
public final class MigrationNotes {

    public record Note(String name, List<String> keywords, String text) {
    }

    private final List<Note> notes;

    public MigrationNotes(List<Note> notes) {
        this.notes = notes;
    }

    /** Parses the markdown file: "## name", a "keywords:" line, then the note text. */
    public static MigrationNotes parse(String markdown) {
        List<Note> notes = new ArrayList<>();
        for (String section : markdown.split("(?m)^## ")) {
            if (section.isBlank()) {
                continue;
            }
            String[] lines = section.split("\\R", -1);
            String name = lines[0].trim();
            List<String> keywords = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            for (int i = 1; i < lines.length; i++) {
                String l = lines[i];
                if (l.startsWith("keywords:")) {
                    for (String k : l.substring("keywords:".length()).split(",")) {
                        if (!k.isBlank()) {
                            keywords.add(k.trim().toLowerCase(Locale.ROOT));
                        }
                    }
                } else {
                    text.append(l).append('\n');
                }
            }
            notes.add(new Note(name, keywords, text.toString().trim()));
        }
        return new MigrationNotes(notes);
    }

    public List<Note> relevantTo(String errorText) {
        String haystack = errorText.toLowerCase(Locale.ROOT);
        List<Note> hits = new ArrayList<>();
        for (Note n : notes) {
            for (String k : n.keywords()) {
                if (haystack.contains(k)) {
                    hits.add(n);
                    break;
                }
            }
        }
        return hits;
    }

    public List<Note> all() {
        return notes;
    }
}
