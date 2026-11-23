package com.popclub.ai;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class ClaudeVisionClient {

    private static final String API_URL    = "https://api.anthropic.com/v1/messages";
    private static final String MODEL      = "claude-haiku-4-5-20251001";
    private static final int    MAX_TOKENS = 512;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String assertScreen(byte[] png, String assertion) throws Exception {
        String prompt =
            "You are a UI test assertion engine. Look at this screenshot and determine if the assertion is TRUE.\n\n" +
            "Assertion: " + assertion + "\n\n" +
            "Reply with exactly one of:\n" +
            "PASS\n" +
            "FAIL: <brief reason>";
        return call(png, prompt);
    }

    public static String findDefects(byte[] png) throws Exception {
        String prompt =
            "You are a UI quality checker. Inspect this screenshot for visual defects: " +
            "truncated text, overlapping elements, misaligned components, broken layouts, incorrect spacing.\n\n" +
            "If NO defects found, reply with exactly: PASS\n" +
            "If defects found, reply with: FAIL: <description of each defect>";
        return call(png, prompt);
    }

    public static String extractText(byte[] png, String query) throws Exception {
        String prompt = "Look at this screenshot and extract: " + query +
                        "\n\nReturn only the extracted value, nothing else.";
        return call(png, prompt);
    }

    private static String call(byte[] imageBytes, String prompt) throws Exception {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("ANTHROPIC_API_KEY environment variable not set");
        }

        String base64 = Base64.getEncoder().encodeToString(imageBytes);

        Map<String, Object> body = Map.of(
            "model", MODEL,
            "max_tokens", MAX_TOKENS,
            "messages", List.of(Map.of(
                "role", "user",
                "content", List.of(
                    Map.of("type", "image",
                           "source", Map.of(
                               "type",       "base64",
                               "media_type", "image/png",
                               "data",       base64)),
                    Map.of("type", "text", "text", prompt)
                )
            ))
        );

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("x-api-key",           apiKey)
            .header("anthropic-version",   "2023-06-01")
            .header("content-type",        "application/json")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Claude API " + response.statusCode() + ": " + response.body());
        }

        Map<?, ?> resp    = MAPPER.readValue(response.body(), Map.class);
        List<?>   content = (List<?>) resp.get("content");
        Map<?, ?>  first  = (Map<?, ?>) content.get(0);
        return ((String) first.get("text")).trim();
    }
}
