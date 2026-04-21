package com.aiagent;

import java.util.Map;

/**
 * Multi-Agent Web Test Generator
 * <p>
 * Usage: java -jar ai-agent.jar &lt;URL&gt;
 * <p>
 * Example: java -jar ai-agent.jar https://www.saucedemo.com/
 * <p>
 * Environment: OPENAI_API_KEY must be set.
 */
public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java -jar ai-agent.jar <URL> [--scenario <scenario>]");
            System.out.println("       mvn compile exec:java '-Dexec.args=<URL> --scenario <scenario>'");
            System.out.println();
            System.out.println("Example: mvn compile exec:java '-Dexec.args=https://www.saucedemo.com/'");
            System.out.println(
                    "         mvn compile exec:java '-Dexec.args=https://www.saucedemo.com/ --scenario Login with standard_user and add item to cart'");
            System.exit(1);
        }

        String url = args[0].trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            System.err.println("Error: URL must start with http:// or https://  (got: " + url + ")");
            System.exit(1);
        }

        String scenario = parseScenario(args);
        if (scenario != null) {
            System.out.println("Scenario: " + scenario);
        }

        Orchestrator orchestrator = new Orchestrator();
        Map<String, Object> result = orchestrator.run(url, scenario);

        System.out.println("\nSummary:");
        System.out.println("  Elements scraped : " + result.get("scraped_elements"));
        System.out.println("  XPaths generated : " + result.get("xpath_elements"));
        System.out.println("  Test file        : " + result.get("test_file"));
        System.out.printf("  Total time       : %.1fs%n", result.get("elapsed_seconds"));
    }

    private static String parseScenario(String[] args) {
        for (int i = 1; i < args.length; i++) {
            if ("--scenario".equals(args[i]) && i + 1 < args.length) {
                StringBuilder sb = new StringBuilder();
                for (int j = i + 1; j < args.length; j++) {
                    if (j > i + 1)
                        sb.append(" ");
                    sb.append(args[j]);
                }
                return sb.toString();
            }
        }
        return null;
    }
}
