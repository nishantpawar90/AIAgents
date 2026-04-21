package com.aiagent.agents;

import com.aiagent.Config;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Agent 3: Test-Case Generator Agent
 * Sends the xpath-enriched element data to OpenAI ChatGPT and generates
 * Selenium-based automated test cases (Java / TestNG or JUnit).
 */
public class TestCaseAgent {

    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";

    private static final String BASE_SYSTEM_PROMPT = """
            You are an expert QA automation engineer. You will receive a JSON description
            of web elements scraped from a webpage. Each element includes its tag,
            attributes, XPath locator, and visibility/enabled state.

            Your task:
            1. Analyze the elements and identify meaningful test scenarios
               (login flows, navigation, form submission, link verification, etc.).
            2. Generate a **BDD feature file** using **Gherkin** syntax.
            3. At the TOP of the feature file, add a comment block listing ALL page elements
               with their locators in this format:
               # URL: <page url>
               # <Element description>: <locator strategy>=<locator value> | xpath=<xpath>
               For example:
               # URL: https://example.com
               # Username field: id="user-name" | xpath="//input[@id='user-name']"
               # Login button: id="login-button" | xpath="//input[@id='login-button']"
            4. Each Scenario must:
               - Use clear Given/When/Then steps.
               - Reference element names and values from the scraped data.
               - Have a descriptive Scenario name.
            5. Include a Feature description and Background section where appropriate.
            6. Add positive AND negative scenarios where applicable.
            7. Output ONLY valid Gherkin syntax — no markdown fences, no explanations outside comments.
            """;

    private final OkHttpClient httpClient;
    private final Gson gson;

    public TestCaseAgent() {
        if (Config.OPENAI_API_KEY == null || Config.OPENAI_API_KEY.isEmpty()) {
            throw new IllegalStateException(
                    "OPENAI_API_KEY is not set. Set it via environment variable.");
        }
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    // ── Build prompts ────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String buildUserPrompt(Map<String, Object> xpathData) {
        String url = (String) xpathData.get("url");
        String title = (String) xpathData.get("title");
        List<Map<String, Object>> elements = (List<Map<String, Object>>) xpathData.get("elements");

        List<Map<String, Object>> summary = new ArrayList<>();
        for (Map<String, Object> el : elements) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("tag", el.get("tag"));
            String text = (String) el.getOrDefault("text", "");
            entry.put("text", text.length() > 80 ? text.substring(0, 80) : text);
            entry.put("xpath", el.get("primary_xpath"));
            entry.put("strategy", el.getOrDefault("xpath_strategy", ""));
            // Trim attribute values
            Map<String, String> attrs = (Map<String, String>) el.getOrDefault("attributes", Collections.emptyMap());
            Map<String, String> trimmed = new LinkedHashMap<>();
            attrs.forEach((k, v) -> trimmed.put(k, v.length() > 80 ? v.substring(0, 80) : v));
            entry.put("attrs", trimmed);
            entry.put("displayed", el.getOrDefault("is_displayed", true));
            entry.put("enabled", el.getOrDefault("is_enabled", true));
            summary.add(entry);
        }

        String elementsJson = gson.toJson(summary);

        return String.format("""
                **Page URL:** %s
                **Page Title:** %s
                **Total Elements:** %d

                Below is the JSON array of web elements with their XPath locators:

                %s

                Generate comprehensive BDD Gherkin scenarios in a feature file for this page.
                Include a Background section for common setup steps and group scenarios logically.
                Use the XPaths as references in the element locator comments at the top.
                """, url, title, summary.size(), elementsJson);
    }

    // ── Call OpenAI API ──────────────────────────────────────────────

    private String callChatGPT(String userMessage, String systemPrompt) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", Config.OPENAI_MODEL);
        requestBody.addProperty("temperature", 0.3);
        requestBody.addProperty("max_tokens", 4096);

        JsonArray messages = new JsonArray();

        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", systemPrompt);
        messages.add(systemMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);

        requestBody.add("messages", messages);

        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(OPENAI_CHAT_URL)
                .addHeader("Authorization", "Bearer " + Config.OPENAI_API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "no body";
                throw new IOException("OpenAI API error " + response.code() + ": " + errBody);
            }

            String responseJson = response.body().string();
            JsonObject parsed = JsonParser.parseString(responseJson).getAsJsonObject();
            return parsed.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString()
                    .trim();
        }
    }

    // ── Public API ───────────────────────────────────────────────────

    /**
     * Generate test cases for the given xpath-enriched element data.
     */
    public Map<String, Object> run(Map<String, Object> xpathData, String scenario) {
        String url = (String) xpathData.get("url");
        int count = ((Number) xpathData.get("element_count")).intValue();
        System.out.println("[TestCaseAgent] Sending " + count + " elements to ChatGPT");

        String systemPrompt = BASE_SYSTEM_PROMPT;
        if (scenario != null && !scenario.isBlank()) {
            systemPrompt += "\n8. **MANDATORY SCENARIO**: You MUST generate Gherkin scenarios that implement this EXACT scenario as the PRIMARY focus. "
                    +
                    "Do NOT generate generic scenarios. EVERY Scenario must be directly related to this scenario:\n"
                    +
                    scenario + "\n" +
                    "Generate a complete end-to-end Scenario that performs ALL the steps described above in sequence, plus individual Scenarios for each step.\n";
            System.out.println("[TestCaseAgent] Custom scenario: " + scenario);
        }

        String userPrompt = buildUserPrompt(xpathData);
        if (scenario != null && !scenario.isBlank()) {
            userPrompt += "\n\n**IMPORTANT — USER SCENARIO (must be implemented):**\n" + scenario +
                    "\n\nGenerate Gherkin scenarios that execute this EXACT scenario. " +
                    "Include an end-to-end Scenario that performs all steps in sequence, and individual Scenarios for each step. "
                    +
                    "Do NOT generate unrelated generic scenarios.\n";
        }

        String testCode;
        try {
            testCode = callChatGPT(userPrompt, systemPrompt);
        } catch (IOException e) {
            throw new RuntimeException("ChatGPT API call failed", e);
        }

        // Strip markdown fences if model wraps them
        if (testCode.startsWith("```")) {
            String[] lines = testCode.split("\n");
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                if (!line.trim().startsWith("```")) {
                    sb.append(line).append("\n");
                }
            }
            testCode = sb.toString().trim();
        }

        // Save to disk — use scenario-aware filename to avoid overwriting generic tests
        String safe = md5(url).substring(0, 10);
        String outPath;
        if (scenario != null && !scenario.isBlank()) {
            String scenarioSafe = md5(url + scenario).substring(0, 10);
            outPath = Config.TESTCASE_DIR + "/Test_Scenario_" + scenarioSafe + ".feature";
        } else {
            outPath = Config.TESTCASE_DIR + "/Test_" + safe + ".feature";
        }
        try (FileWriter writer = new FileWriter(outPath, StandardCharsets.UTF_8)) {
            writer.write(testCode);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write test file", e);
        }

        System.out.println("[TestCaseAgent] Test cases written → " + outPath);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", url);
        result.put("title", xpathData.get("title"));
        result.put("test_code", testCode);
        result.put("output_file", outPath);
        return result;
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
