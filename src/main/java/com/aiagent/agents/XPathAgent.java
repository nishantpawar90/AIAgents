package com.aiagent.agents;

import com.aiagent.Config;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Agent 2: XPath Extractor Agent
 * Takes scraped element data and generates robust, unique XPath expressions
 * for every element using multiple strategies (id, name, attributes, text,
 * etc.).
 */
public class XPathAgent {

    // ── XPath generation strategies ──────────────────────────────────

    private static String byId(String tag, Map<String, String> attrs) {
        String id = attrs.get("id");
        return id != null ? "//" + tag + "[@id='" + id + "']" : null;
    }

    private static String byDataTestId(String tag, Map<String, String> attrs) {
        String v = attrs.get("data-testid");
        return v != null ? "//" + tag + "[@data-testid='" + v + "']" : null;
    }

    private static String byName(String tag, Map<String, String> attrs) {
        String v = attrs.get("name");
        return v != null ? "//" + tag + "[@name='" + v + "']" : null;
    }

    private static String byAriaLabel(String tag, Map<String, String> attrs) {
        String v = attrs.get("aria-label");
        return v != null ? "//" + tag + "[@aria-label='" + v + "']" : null;
    }

    private static String byPlaceholder(String tag, Map<String, String> attrs) {
        String v = attrs.get("placeholder");
        return v != null ? "//" + tag + "[@placeholder='" + v + "']" : null;
    }

    private static String byTypeAndName(String tag, Map<String, String> attrs) {
        String type = attrs.get("type");
        String name = attrs.get("name");
        return (type != null && name != null)
                ? "//" + tag + "[@type='" + type + "' and @name='" + name + "']"
                : null;
    }

    private static String byHref(String tag, Map<String, String> attrs) {
        if (!"a".equals(tag))
            return null;
        String href = attrs.get("href");
        if (href == null)
            return null;
        if (href.length() < 120)
            return "//a[@href='" + href + "']";
        return "//a[contains(@href,'" + href.substring(0, 60) + "')]";
    }

    private static String byText(String tag, String text) {
        if (text != null && !text.isEmpty() && text.length() < 80) {
            String sanitized = text.replace("'", "\\'");
            return "//" + tag + "[normalize-space()='" + sanitized + "']";
        }
        return null;
    }

    private static String byClassAndText(String tag, Map<String, String> attrs, String text) {
        String cls = attrs.get("class");
        if (cls != null && text != null && !text.isEmpty() && text.length() < 60) {
            String firstClass = cls.split("\\s+")[0];
            String sanitized = text.replace("'", "\\'");
            return "//" + tag + "[contains(@class,'" + firstClass + "') and normalize-space()='" + sanitized + "']";
        }
        return null;
    }

    private static String byRole(String tag, Map<String, String> attrs) {
        String v = attrs.get("role");
        return v != null ? "//" + tag + "[@role='" + v + "']" : null;
    }

    // ── Generate XPath for a single element ──────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> generateXPath(Map<String, Object> element) {
        String tag = (String) element.get("tag");
        Map<String, String> attrs = (Map<String, String>) element.getOrDefault("attributes", Collections.emptyMap());
        String text = (String) element.getOrDefault("text", "");

        // Strategies in priority order
        String[][] strategies = {
                { "id", byId(tag, attrs) },
                { "data-testid", byDataTestId(tag, attrs) },
                { "name", byName(tag, attrs) },
                { "aria-label", byAriaLabel(tag, attrs) },
                { "placeholder", byPlaceholder(tag, attrs) },
                { "type+name", byTypeAndName(tag, attrs) },
                { "href", byHref(tag, attrs) },
                { "text", byText(tag, text) },
                { "class+text", byClassAndText(tag, attrs, text) },
                { "role", byRole(tag, attrs) },
        };

        List<String[]> valid = new ArrayList<>();
        for (String[] s : strategies) {
            if (s[1] != null)
                valid.add(s);
        }

        String primaryXpath;
        String xpathStrategy;
        if (!valid.isEmpty()) {
            primaryXpath = valid.get(0)[1];
            xpathStrategy = valid.get(0)[0];
        } else {
            primaryXpath = "//" + tag;
            xpathStrategy = "tag-only";
        }

        List<String> alternates = new ArrayList<>();
        for (int i = 1; i < Math.min(valid.size(), 4); i++) {
            alternates.add(valid.get(i)[1]);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tag", tag);
        result.put("text", text != null && text.length() > 100 ? text.substring(0, 100) : text);
        result.put("attributes", attrs);
        result.put("is_displayed", element.getOrDefault("is_displayed", true));
        result.put("is_enabled", element.getOrDefault("is_enabled", true));
        result.put("primary_xpath", primaryXpath);
        result.put("xpath_strategy", xpathStrategy);
        result.put("alternate_xpaths", alternates);

        return result;
    }

    // ── Public API ───────────────────────────────────────────────────

    /**
     * Accept scraper output and return xpath-enriched element list.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> run(Map<String, Object> scrapedData) {
        int count = ((Number) scrapedData.get("element_count")).intValue();
        System.out.println("[XPathAgent] Generating XPaths for " + count + " elements");

        List<Map<String, Object>> elements = (List<Map<String, Object>>) scrapedData.get("elements");
        List<Map<String, Object>> enriched = new ArrayList<>();

        for (Map<String, Object> el : elements) {
            enriched.add(generateXPath(el));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", scrapedData.get("url"));
        result.put("title", scrapedData.get("title"));
        result.put("element_count", enriched.size());
        result.put("elements", enriched);

        // Persist to disk
        String url = (String) scrapedData.get("url");
        String safe = md5(url).substring(0, 10);
        String outPath = Config.XPATH_DATA_DIR + "/" + safe + "_xpaths.json";
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        try (FileWriter writer = new FileWriter(outPath, StandardCharsets.UTF_8)) {
            gson.toJson(result, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write XPath data", e);
        }

        System.out.println("[XPathAgent] Wrote XPaths → " + outPath);
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
