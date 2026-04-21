# Multi-Agent Web Test Generator

A Java multi-agent framework that takes a webpage URL, scrapes all web elements, extracts XPath locators, and uses ChatGPT to generate Selenium test cases automatically.

## Architecture

```
 ┌─────────────┐     ┌─────────────┐     ┌───────────────┐
 │  Scraper     │ ──▶ │  XPath      │ ──▶ │  TestCase     │
 │  Agent       │     │  Agent      │     │  Agent (GPT)  │
 └─────────────┘     └─────────────┘     └───────────────┘
        ▲                                        │
        │          Orchestrator                   ▼
      URL ──────────────────────────────▶  Test_*.java file
```

| Agent | Responsibility |
|-------|---------------|
| **ScraperAgent** | Launches Chrome via Selenium, navigates to the URL, and collects metadata for every interactive DOM element (inputs, buttons, links, forms, etc.) |
| **XPathAgent** | Generates robust XPath locators using multiple strategies (id, name, data-testid, aria-label, text, class, etc.) and picks the most reliable one |
| **TestCaseAgent** | Sends the XPath-enriched element data to OpenAI ChatGPT which returns Selenium + TestNG test code in Java |

## Prerequisites

- Java 17+
- Maven 3.8+
- Google Chrome installed
- An OpenAI API key

## Setup

```bash
# Set your OpenAI API key
# Windows PowerShell:
$env:OPENAI_API_KEY = "sk-..."

# Linux / macOS:
export OPENAI_API_KEY="sk-..."

# Build the project
mvn clean package
```

## Usage

```bash
# Option 1: via Maven
mvn exec:java -Dexec.args="https://www.saucedemo.com/"

# Option 2: via fat JAR
java -jar target/ai-agent-1.0-SNAPSHOT.jar https://www.saucedemo.com/
```

### Output

The framework creates three directories under `output/`:

```
output/
├── scraped_data/   # Raw element JSON from ScraperAgent
├── xpath_data/     # XPath-enriched JSON from XPathAgent
└── testcases/      # Generated Test_*.java files from TestCaseAgent
```

## Configuration

Edit `Config.java` to adjust:

| Setting | Default | Description |
|---------|---------|-------------|
| `OPENAI_MODEL` | `gpt-4` | OpenAI model to use |
| `HEADLESS` | `false` | Run Chrome in headless mode |
| `PAGE_LOAD_TIMEOUT` | `30` | Seconds to wait for page load |
| `IMPLICIT_WAIT` | `10` | Selenium implicit wait (seconds) |

## Project Structure

```
AIAgent/
├── pom.xml
├── README.md
└── src/main/java/com/aiagent/
    ├── Main.java              # CLI entry point
    ├── Config.java            # Global configuration
    ├── Orchestrator.java      # Pipeline coordinator
    └── agents/
        ├── ScraperAgent.java    # Agent 1 – Web scraping
        ├── XPathAgent.java      # Agent 2 – XPath generation
        └── TestCaseAgent.java   # Agent 3 – ChatGPT test generation
```
