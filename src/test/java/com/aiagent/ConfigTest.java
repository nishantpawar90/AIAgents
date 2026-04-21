package com.aiagent;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    @Test
    void outputDirectoriesAreNonNull() {
        assertNotNull(Config.OUTPUT_DIR);
        assertNotNull(Config.SCRAPED_DATA_DIR);
        assertNotNull(Config.XPATH_DATA_DIR);
        assertNotNull(Config.TESTCASE_DIR);
    }

    @Test
    void outputDirectoriesExist() {
        assertTrue(new File(Config.OUTPUT_DIR).isDirectory());
        assertTrue(new File(Config.SCRAPED_DATA_DIR).isDirectory());
        assertTrue(new File(Config.XPATH_DATA_DIR).isDirectory());
        assertTrue(new File(Config.TESTCASE_DIR).isDirectory());
    }

    @Test
    void directoryPathsAreNested() {
        assertTrue(Config.SCRAPED_DATA_DIR.startsWith(Config.OUTPUT_DIR));
        assertTrue(Config.XPATH_DATA_DIR.startsWith(Config.OUTPUT_DIR));
        assertTrue(Config.TESTCASE_DIR.startsWith(Config.OUTPUT_DIR));
    }

    @Test
    void pageLoadTimeoutIsPositive() {
        assertTrue(Config.PAGE_LOAD_TIMEOUT > 0);
    }

    @Test
    void implicitWaitIsPositive() {
        assertTrue(Config.IMPLICIT_WAIT > 0);
    }

    @Test
    void openaiModelIsSet() {
        assertNotNull(Config.OPENAI_MODEL);
        assertFalse(Config.OPENAI_MODEL.isEmpty());
    }
}
