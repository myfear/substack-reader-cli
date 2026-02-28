# Substack Reader

A terminal-based Substack reader built with Java and [TamboUI](https://github.com/tamboui/tamboui). Browse and read articles from **The Main Thread** directly in your terminal.

For a detailed walkthrough of how it was built, see [*Building a Terminal Substack Reader with TamboUI*](https://www.the-main-thread.com/p/terminal-substack-reader-java-tamboui-jsoup) on The Main Thread.

## Features

- **Article list** — View recent posts with date and free/paid indicators (🔒 for paid, 🟢 for free)
- **Article view** — Read full articles with scrollable content; HTML is converted to plain text
- **Keyboard navigation** — Navigate the list and scroll articles without leaving the terminal

## Requirements

- **Java 21** or later
- **Maven 3.6+**

## Build & Run

```bash
mvn compile exec:java -q
```

Or compile and run the JAR:

```bash
mvn package -q
java -cp target/substack-reader-1.0-SNAPSHOT.jar substack.reader.SubstackReader
```

(Note: the exec plugin pulls in dependencies at runtime; for a standalone JAR you’d need the Maven Shade or Assembly plugin.)

## Controls

| Screen   | Key        | Action              |
|----------|------------|---------------------|
| List     | ↑ / ↓      | Move selection      |
| List     | Enter      | Open article        |
| List     | q          | Quit                |
| Article  | ↑ / ↓      | Scroll              |
| Article  | Esc / q    | Back to list        |

## How It Works

The app uses the public Substack API (`/api/v1/posts`) to fetch recent posts from a configured publication. Article bodies are parsed from HTML with [Jsoup](https://jsoup.org/) and rendered as plain text in the TamboUI interface. Paywalled content shows a short message and the article URL so you can open it in a browser.

## Tech Stack

- **Java 21**
- **TamboUI** — Terminal UI toolkit (widgets, layout, input)
- **Gson** — JSON parsing for the Substack API
- **Jsoup** — HTML parsing for article content

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
