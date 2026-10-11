package io.github.mahipvk.migratemate;

import io.github.mahipvk.migratemate.core.FileBlocks;
import io.github.mahipvk.migratemate.core.FixPlanner.FixRequest;
import io.github.mahipvk.migratemate.core.MigrationNotes;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Talks to the LLM through Spring AI's ChatClient (Ollama by default, swappable via config). */
@Component
public class AiFixer {

    private final ChatClient chat;
    private final String systemPrompt;
    private final MigrationNotes notes;
    private final String model;

    public AiFixer(ChatClient.Builder builder,
                   @Value("${spring.ai.ollama.chat.options.model:unknown}") String model) throws IOException {
        this.chat = builder.build();
        this.model = model;
        this.systemPrompt = new ClassPathResource("prompts/system.md").getContentAsString(StandardCharsets.UTF_8);
        this.notes = MigrationNotes.parse(
                new ClassPathResource("prompts/migration-notes.md").getContentAsString(StandardCharsets.UTF_8));
    }

    public String model() {
        return model;
    }

    /**
     * @param feedback what went wrong in the previous attempt (empty on the first attempt)
     */
    public String buildPrompt(FixRequest request, int iteration, String feedback) {
        StringBuilder files = new StringBuilder();
        request.files().forEach((path, content) -> files.append(FileBlocks.format(path, content)).append('\n'));

        StringBuilder context = new StringBuilder();
        request.context().forEach((path, content) -> context.append("--- ").append(path).append(" (read-only) ---\n")
                .append(content.stripTrailing()).append("\n\n"));

        // Retrieve notes using the errors only, so the model sees just the guidance that matters.
        StringBuilder relevantNotes = new StringBuilder();
        notes.relevantTo(request.errors())
                .forEach(n -> relevantNotes.append("- ").append(n.text().replace("\n", " ")).append('\n'));

        String editable = String.join(", ", request.files().keySet());
        return """
                ## Goal
                This project is being upgraded to Java 21 and Spring Boot 3. Make `mvn test` pass.
                This is fix attempt %d.
                %s
                ## Build and test errors
                %s
                ## Migration notes that may help
                %s
                ## Read-only context (the tests are correct: do NOT return or change these files)
                %s
                ## Files you may edit
                %s
                ## Output format
                Return ONLY the files you changed (choose from: %s), each one complete, in exactly this format:
                === FILE: <path exactly as given above> ===
                <complete new file content>
                === END FILE ===
                No explanations.
                """.formatted(iteration,
                feedback.isBlank() ? "" : "\n## Your previous attempt did not work\n" + feedback + "\n",
                request.errors(),
                relevantNotes.isEmpty() ? "(none)\n" : relevantNotes,
                context.isEmpty() ? "(none)\n" : context,
                files, editable);
    }

    /** Messages are passed as a Prompt object so Java braces in source code are never treated as template variables. */
    public String ask(String userPrompt) {
        Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)));
        return chat.prompt(prompt).call().content();
    }
}
