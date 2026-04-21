package com.aiagent.agents;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class XPathAgentTest {

    private XPathAgent agent;

    @BeforeEach
    void setUp() {
        agent = new XPathAgent();
    }

    @Test
    void runGeneratesXPathsForElementsWithId() {
        Map<String, Object> scraped = buildScrapedData(
                buildElement("input", "", Map.of("id", "username", "type", "text")));

        Map<String, Object> result = agent.run(scraped);

        assertEquals(1, result.get("element_count"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) result.get("elements");
        assertEquals("//input[@id='username']", elements.get(0).get("primary_xpath"));
        assertEquals("id", elements.get(0).get("xpath_strategy"));
    }

    @Test
    void runGeneratesXPathByDataTestId() {
        Map<String, Object> scraped = buildScrapedData(
                buildElement("button", "Submit", Map.of("data-testid", "submit-btn")));

        Map<String, Object> result = agent.run(scraped);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) result.get("elements");
        assertEquals("//button[@data-testid='submit-btn']", elements.get(0).get("primary_xpath"));
        assertEquals("data-testid", elements.get(0).get("xpath_strategy"));
    }

    @Test
    void runGeneratesXPathByName() {
        Map<String, Object> scraped = buildScrapedData(
                buildElement("input", "", Map.of("name", "email")));

        Map<String, Object> result = agent.run(scraped);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) result.get("elements");
        assertEquals("//input[@name='email']", elements.get(0).get("primary_xpath"));
        assertEquals("name", elements.get(0).get("xpath_strategy"));
    }

    @Test
    void runGeneratesXPathByAriaLabel() {
        Map<String, Object> scraped = buildScrapedData(
                buildElement("button", "", Map.of("aria-label", "Close dialog")));

        Map<String, Object> result = agent.run(scraped);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) result.get("elements");
        assertEquals("//button[@aria-label='Close dialog']", elements.get(0).get("primary_xpath"));
        assertEquals("aria-label", elements.get(0).get("xpath_strategy"));
    }

    @Test
    void runGeneratesXPathByPlaceholder() {
        Map<String, Object> scraped = buildScrapedData(
                buildElement("input", "", Map.of("placeholder", "Enter name")));

        Map<String, Object> result = agent.run(scraped);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) result.get("elements");
        assertEquals("//input[@placeholder='Enter name']", elements.get(0).get("primary_xpath"));
        assertEquals("placeholder", elements.get(0).get("xpath_strategy"));
    }

    @Test
    void runGeneratesXPathByText() {
        Map<String, Object> scraped = buildScrapedData(
                buildElement("h1", "Welcome", Map.of()));

        Map<String, Object> result = agent.run(scraped);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) result.get("elements");
        assertEquals("//h1[normalize-space()='Welcome']", elements.get(0).get("primary_xpath"));
        assertEquals("text", elements.get(0).get("xpath_strategy"));
    }

    @Test
    void runFallsBackToTagOnlyWhenNoAttributesOrText() {
        Map<String, Object> scraped = buildScrapedData(
                buildElement("div", "", Map.of()));

        Map<String, Object> result = agent.run(scraped);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) result.get("elements");
        assertEquals("//div", elements.get(0).get("primary_xpath"));
        assertEquals("tag-only", elements.get(0).get("xpath_strategy"));
    }

    @Test
    void runGeneratesAlternateXPaths() {
        // Element with id AND name AND placeholder — should have primary by id,
        // alternates for others
        Map<String, Object> scraped = buildScrapedData(
                buildElement("input", "", Map.of("id", "user", "name", "username", "placeholder", "Enter user")));

        Map<String, Object> result = agent.run(scraped);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) result.get("elements");
        assertEquals("//input[@id='user']", elements.get(0).get("primary_xpath"));

        @SuppressWarnings("unchecked")
        List<String> alternates = (List<String>) elements.get(0).get("alternate_xpaths");
        assertFalse(alternates.isEmpty());
    }

    @Test
    void runGeneratesXPathByHrefForAnchor() {
        Map<String, Object> scraped = buildScrapedData(
                buildElement("a", "Home", Map.of("href", "/home")));

        Map<String, Object> result = agent.run(scraped);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) result.get("elements");
        // href strategy is lower priority than text for short text
        String primary = (String) elements.get(0).get("primary_xpath");
        assertNotNull(primary);
        // Check alternates contain href-based xpath
        @SuppressWarnings("unchecked")
        List<String> alternates = (List<String>) elements.get(0).get("alternate_xpaths");
        boolean hasHref = primary.contains("@href") ||
                alternates.stream().anyMatch(x -> x.contains("@href"));
        assertTrue(hasHref);
    }

    @Test
    void runGeneratesXPathByRole() {
        Map<String, Object> scraped = buildScrapedData(
                buildElement("nav", "", Map.of("role", "navigation")));

        Map<String, Object> result = agent.run(scraped);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) result.get("elements");
        assertEquals("//nav[@role='navigation']", elements.get(0).get("primary_xpath"));
        assertEquals("role", elements.get(0).get("xpath_strategy"));
    }

    @Test
    void runHandlesMultipleElements() {
        Map<String, Object> scraped = buildScrapedData(
                buildElement("input", "", Map.of("id", "user")),
                buildElement("input", "", Map.of("id", "pass")),
                buildElement("button", "Login", Map.of("id", "login-btn")));

        Map<String, Object> result = agent.run(scraped);

        assertEquals(3, result.get("element_count"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) result.get("elements");
        assertEquals(3, elements.size());
    }

    @Test
    void runPreservesUrlAndTitle() {
        Map<String, Object> scraped = buildScrapedData(
                buildElement("h1", "Title", Map.of()));

        Map<String, Object> result = agent.run(scraped);

        assertEquals("https://example.com", result.get("url"));
        assertEquals("Test Page", result.get("title"));
    }

    @Test
    void runTruncatesLongText() {
        String longText = "A".repeat(150);
        Map<String, Object> scraped = buildScrapedData(
                buildElement("p", longText, Map.of("id", "para")));

        Map<String, Object> result = agent.run(scraped);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) result.get("elements");
        String resultText = (String) elements.get(0).get("text");
        assertTrue(resultText.length() <= 100);
    }

    // ── Helpers ──

    @SafeVarargs
    private Map<String, Object> buildScrapedData(Map<String, Object>... elements) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", "https://example.com");
        data.put("title", "Test Page");
        data.put("element_count", elements.length);
        data.put("elements", new ArrayList<>(Arrays.asList(elements)));
        return data;
    }

    private Map<String, Object> buildElement(String tag, String text, Map<String, String> attrs) {
        Map<String, Object> el = new LinkedHashMap<>();
        el.put("tag", tag);
        el.put("text", text);
        el.put("attributes", new LinkedHashMap<>(attrs));
        el.put("is_displayed", true);
        el.put("is_enabled", true);
        return el;
    }
}
