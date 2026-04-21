package com.aiagent.agents;

import com.aiagent.Config;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Agent 1: Web Scraper Agent
 * Launches Chrome, navigates to the target URL, and collects metadata
 * for every interactive / important DOM element.
 */
public class ScraperAgent {

    private static final String[] CSS_SELECTORS = {
            "input", "button", "a", "select", "textarea", "form",
            "label", "img", "[role]", "h1", "h2", "h3", "h4", "h5", "h6",
            "nav", "header", "footer", "table"
    };

    private static final String[] CAPTURE_ATTRS = {
            "id", "name", "class", "type", "value", "placeholder",
            "href", "src", "alt", "title", "role", "aria-label",
            "data-testid", "action", "method"
    };

    private WebDriver driver;

    private void startBrowser() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        if (Config.HEADLESS) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");

        driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(Config.PAGE_LOAD_TIMEOUT));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(Config.IMPLICIT_WAIT));
    }

    private void stopBrowser() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }

    private List<Map<String, Object>> collectElements() {
        List<Map<String, Object>> elements = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // Temporarily reduce implicit wait for faster element scanning
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(500));

        for (String selector : CSS_SELECTORS) {
            List<WebElement> found;
            try {
                found = driver.findElements(By.cssSelector(selector));
            } catch (Exception e) {
                continue;
            }

            for (WebElement el : found) {
                try {
                    String outerHtml = el.getAttribute("outerHTML");
                    if (outerHtml == null)
                        outerHtml = "";
                    String snippet = outerHtml.length() > 500 ? outerHtml.substring(0, 500) : outerHtml;
                    String uid = md5(snippet);
                    if (seen.contains(uid))
                        continue;
                    seen.add(uid);

                    String tag = el.getTagName().toLowerCase();
                    String text = el.getText();
                    if (text == null)
                        text = "";
                    if (text.length() > 200)
                        text = text.substring(0, 200);
                    text = text.trim();

                    Map<String, String> attrs = new LinkedHashMap<>();
                    for (String attr : CAPTURE_ATTRS) {
                        String val = el.getAttribute(attr);
                        if (val != null && !val.isEmpty()) {
                            attrs.put(attr, val.length() > 300 ? val.substring(0, 300).trim() : val.trim());
                        }
                    }

                    boolean isDisplayed = el.isDisplayed();
                    boolean isEnabled = el.isEnabled();

                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("tag", tag);
                    entry.put("text", text);
                    entry.put("attributes", attrs);
                    entry.put("is_displayed", isDisplayed);
                    entry.put("is_enabled", isEnabled);
                    entry.put("outer_html_snippet", snippet);

                    elements.add(entry);
                } catch (Exception e) {
                    // stale element or transient issue
                }
            }
        }
        // Restore implicit wait
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(Config.IMPLICIT_WAIT));
        return elements;
    }

    /**
     * Scrape the given URL and return structured element data.
     * If a scenario is provided and a login form is detected, attempt login
     * and scrape post-login pages as well.
     */
    public Map<String, Object> run(String url) {
        return run(url, null);
    }

    public Map<String, Object> run(String url, String scenario) {
        System.out.println("[ScraperAgent] Starting browser and navigating to " + url);
        startBrowser();

        try {
            driver.get(url);

            new WebDriverWait(driver, Duration.ofSeconds(Config.PAGE_LOAD_TIMEOUT))
                    .until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

            String title = driver.getTitle();
            List<Map<String, Object>> elements = collectElements();
            System.out.println("[ScraperAgent] Page 1 (" + title + "): " + elements.size() + " elements");

            // If scenario mentions credentials and page has a login form, attempt login
            if (scenario != null && !scenario.isBlank()) {
                String[] credentials = extractCredentials(scenario);
                if (credentials != null) {
                    boolean loggedIn = attemptLogin(credentials[0], credentials[1]);
                    if (loggedIn) {
                        String postLoginTitle = driver.getTitle();
                        List<Map<String, Object>> postLoginElements = collectElements();
                        System.out.println("[ScraperAgent] Page 2 (" + postLoginTitle + "): " + postLoginElements.size()
                                + " elements");
                        elements.addAll(postLoginElements);

                        // Perform additional scenario steps (add to cart, navigate, etc.)
                        List<Map<String, Object>> scenarioElements = performScenarioSteps(scenario);
                        if (!scenarioElements.isEmpty()) {
                            elements.addAll(scenarioElements);
                        }
                    }
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("url", url);
            result.put("title", title);
            result.put("timestamp", Instant.now().toString());
            result.put("element_count", elements.size());
            result.put("elements", elements);

            // Persist to disk
            String safeName = md5(url).substring(0, 10);
            if (scenario != null && !scenario.isBlank()) {
                safeName = md5(url + scenario).substring(0, 10);
            }
            String outPath = Config.SCRAPED_DATA_DIR + "/" + safeName + ".json";
            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            try (FileWriter writer = new FileWriter(outPath, StandardCharsets.UTF_8)) {
                gson.toJson(result, writer);
            }

            System.out.println("[ScraperAgent] Scraped " + elements.size() + " elements → " + outPath);
            return result;

        } catch (IOException e) {
            throw new RuntimeException("Failed to write scraped data", e);
        } finally {
            stopBrowser();
        }
    }

    /**
     * Perform multi-step scenario actions beyond login.
     * Parses the scenario text for action keywords and executes them in order,
     * scraping elements after each navigation/state change.
     */
    private List<Map<String, Object>> performScenarioSteps(String scenario) {
        List<Map<String, Object>> allElements = new ArrayList<>();
        String lower = scenario.toLowerCase();

        // Step: Add item to cart — look for "add ... <item name> ... to cart"
        java.util.regex.Matcher addToCartMatcher = java.util.regex.Pattern
                .compile("add(?:\\s+the)?\\s+(?:item\\s+)?[\"']?(.+?)[\"']?\\s+to\\s+cart",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(scenario);
        if (addToCartMatcher.find()) {
            String itemName = addToCartMatcher.group(1).trim();
            System.out.println("[ScraperAgent] Scenario step: add \"" + itemName + "\" to cart");
            try {
                // Find the inventory item by its name text
                List<WebElement> itemLabels = driver.findElements(By.cssSelector(
                        ".inventory_item_name, .inventory_item_label, [class*='item_name']"));
                WebElement targetItem = null;
                for (WebElement label : itemLabels) {
                    if (label.getText().trim().equalsIgnoreCase(itemName)) {
                        targetItem = label;
                        break;
                    }
                }
                if (targetItem != null) {
                    // Find the "Add to cart" button in the same inventory item container
                    WebElement container = targetItem
                            .findElement(By.xpath("./ancestor::div[contains(@class,'inventory_item')]"));
                    WebElement addBtn = container
                            .findElement(By.cssSelector("button[id*='add-to-cart'], button.btn_inventory"));
                    addBtn.click();
                    System.out.println("[ScraperAgent] Clicked 'Add to cart' for: " + itemName);

                    // Brief wait for UI update
                    new WebDriverWait(driver, Duration.ofSeconds(5))
                            .until(d -> d.findElements(By.cssSelector(".shopping_cart_badge")).size() > 0);

                    List<Map<String, Object>> postAddElements = collectElements();
                    System.out
                            .println("[ScraperAgent] Page after add-to-cart: " + postAddElements.size() + " elements");
                    allElements.addAll(postAddElements);
                } else {
                    System.out.println("[ScraperAgent] Could not find item: " + itemName);
                }
            } catch (Exception e) {
                System.out.println("[ScraperAgent] Add-to-cart step failed: " + e.getMessage());
            }
        }

        // Step: Click on cart icon
        if (lower.contains("click on cart") || lower.contains("click cart")
                || lower.contains("go to cart") || lower.contains("open cart")) {
            System.out.println("[ScraperAgent] Scenario step: click cart icon");
            try {
                WebElement cartLink = driver.findElement(By.cssSelector(
                        ".shopping_cart_link, a.shopping_cart_link, [class*='shopping_cart'], a[href*='cart']"));
                String urlBefore = driver.getCurrentUrl();
                cartLink.click();

                new WebDriverWait(driver, Duration.ofSeconds(Config.PAGE_LOAD_TIMEOUT))
                        .until(d -> !d.getCurrentUrl().equals(urlBefore)
                                || d.findElements(By.cssSelector(".cart_list, .cart_contents, [class*='cart']"))
                                        .size() > 0);

                System.out.println("[ScraperAgent] Navigated to cart → " + driver.getCurrentUrl());
                List<Map<String, Object>> cartElements = collectElements();
                System.out.println("[ScraperAgent] Cart page: " + cartElements.size() + " elements");
                allElements.addAll(cartElements);
            } catch (Exception e) {
                System.out.println("[ScraperAgent] Cart navigation failed: " + e.getMessage());
            }
        }

        return allElements;
    }

    /**
     * Extract username and password from scenario text.
     * Looks for patterns like "user as X and password as Y" or "username X password
     * Y".
     */
    private String[] extractCredentials(String scenario) {
        String lower = scenario.toLowerCase();
        String user = null;
        String pass = null;

        // Pattern: "user as <value>" or "username as <value>" or "user <value>"
        java.util.regex.Matcher userMatcher = java.util.regex.Pattern
                .compile("(?:user(?:name)?\\s+(?:as\\s+)?)(\\S+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(scenario);
        if (userMatcher.find()) {
            user = userMatcher.group(1);
        }

        // Pattern: "password as <value>" or "password <value>"
        java.util.regex.Matcher passMatcher = java.util.regex.Pattern
                .compile("(?:password\\s+(?:as\\s+)?)(\\S+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(scenario);
        if (passMatcher.find()) {
            pass = passMatcher.group(1);
        }

        if (user != null && pass != null) {
            System.out.println("[ScraperAgent] Detected credentials — user: " + user);
            return new String[] { user, pass };
        }
        return null;
    }

    /**
     * Attempt to log in by filling username/password fields and clicking submit.
     */
    private boolean attemptLogin(String username, String password) {
        try {
            // Find username field
            List<WebElement> userFields = driver.findElements(By.cssSelector(
                    "input[type='text'][name*='user'], input[type='text'][id*='user'], " +
                            "input[type='email'][name*='user'], input[type='email'][id*='user'], " +
                            "input[type='text'][placeholder*='ser']"));
            // Find password field
            List<WebElement> passFields = driver.findElements(By.cssSelector("input[type='password']"));
            // Find submit button
            List<WebElement> submitBtns = driver.findElements(By.cssSelector(
                    "input[type='submit'], button[type='submit'], button.login, input.login"));

            if (userFields.isEmpty() || passFields.isEmpty() || submitBtns.isEmpty()) {
                System.out.println("[ScraperAgent] No login form detected, skipping auto-login");
                return false;
            }

            String urlBefore = driver.getCurrentUrl();

            userFields.get(0).clear();
            userFields.get(0).sendKeys(username);
            passFields.get(0).clear();
            passFields.get(0).sendKeys(password);
            submitBtns.get(0).click();

            // Wait for page to change
            new WebDriverWait(driver, Duration.ofSeconds(Config.PAGE_LOAD_TIMEOUT))
                    .until(d -> !d.getCurrentUrl().equals(urlBefore)
                            || d.findElements(By.cssSelector(".inventory_list, .dashboard, [class*='product']"))
                                    .size() > 0);

            System.out.println("[ScraperAgent] Login successful → " + driver.getCurrentUrl());
            return true;

        } catch (Exception e) {
            System.out.println("[ScraperAgent] Auto-login failed: " + e.getMessage());
            return false;
        }
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
