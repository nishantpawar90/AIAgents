package com.aiagent;

import java.io.File;

/**
 * Global configuration for the Multi-Agent Web Test Generator.
 */
public class Config {

    // OpenAI Configuration
    public static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY") != null
            ? System.getenv("OPENAI_API_KEY")
            : "";
    public static final String OPENAI_MODEL = "gpt-4o";

    // Selenium Configuration
    public static final boolean HEADLESS = false;
    public static final int PAGE_LOAD_TIMEOUT = 30; // seconds
    public static final int IMPLICIT_WAIT = 10; // seconds

    // Output directories
    public static final String OUTPUT_DIR;
    public static final String SCRAPED_DATA_DIR;
    public static final String XPATH_DATA_DIR;
    public static final String TESTCASE_DIR;

    static {
        String base = System.getProperty("user.dir");
        OUTPUT_DIR = base + File.separator + "output";
        SCRAPED_DATA_DIR = OUTPUT_DIR + File.separator + "scraped_data";
        XPATH_DATA_DIR = OUTPUT_DIR + File.separator + "xpath_data";
        TESTCASE_DIR = OUTPUT_DIR + File.separator + "testcases";

        // Ensure output directories exist
        new File(SCRAPED_DATA_DIR).mkdirs();
        new File(XPATH_DATA_DIR).mkdirs();
        new File(TESTCASE_DIR).mkdirs();
    }

    private Config() {
        // utility class
    }
}
