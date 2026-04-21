package com.aiagent;

import com.aiagent.agents.ScraperAgent;
import com.aiagent.agents.TestCaseAgent;
import com.aiagent.agents.XPathAgent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Orchestrator – coordinates the three agents in sequence:
 * 1. ScraperAgent → scrape the webpage
 * 2. XPathAgent → generate XPath for each element
 * 3. TestCaseAgent → call ChatGPT to produce test cases
 */
public class Orchestrator {

    private final ScraperAgent scraper;
    private final XPathAgent xpathAgent;
    private final TestCaseAgent testCaseAgent;

    public Orchestrator() {
        this.scraper = new ScraperAgent();
        this.xpathAgent = new XPathAgent();
        this.testCaseAgent = new TestCaseAgent();
    }

    /**
     * Execute the full pipeline for a given URL.
     */
    public Map<String, Object> run(String url) {
        return run(url, null);
    }

    /**
     * Execute the full pipeline for a given URL with an optional custom scenario.
     */
    public Map<String, Object> run(String url, String scenario) {
        long t0 = System.currentTimeMillis();

        System.out.println("============================================================");
        System.out.println("  Multi-Agent Test Generator");
        System.out.println("  Target: " + url);
        System.out.println("============================================================");

        // ── Stage 1: Scrape ──
        System.out.println("\n>>> Stage 1/3 – Scraping web elements …");
        Map<String, Object> scraped = scraper.run(url, scenario);
        System.out.println("    ✓ " + scraped.get("element_count") + " elements found\n");

        // ── Stage 2: XPath ──
        System.out.println(">>> Stage 2/3 – Generating XPath locators …");
        Map<String, Object> xpaths = xpathAgent.run(scraped);
        System.out.println("    ✓ " + xpaths.get("element_count") + " XPaths generated\n");

        // ── Stage 3: Test cases ──
        System.out.println(">>> Stage 3/3 – Generating test cases via ChatGPT …");
        if (scenario != null && !scenario.isBlank()) {
            System.out.println("    Scenario: " + scenario);
        }
        Map<String, Object> tests = testCaseAgent.run(xpaths, scenario);
        System.out.println("    ✓ Test file: " + tests.get("output_file") + "\n");

        double elapsed = (System.currentTimeMillis() - t0) / 1000.0;

        System.out.println("============================================================");
        System.out.printf("  Done in %.1fs%n", elapsed);
        System.out.println("  Test file → " + tests.get("output_file"));
        System.out.println("============================================================");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", url);
        result.put("scraped_elements", scraped.get("element_count"));
        result.put("xpath_elements", xpaths.get("element_count"));
        result.put("test_file", tests.get("output_file"));
        result.put("elapsed_seconds", elapsed);
        return result;
    }
}
