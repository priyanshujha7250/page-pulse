# 📡 Page Pulse

> **Instant SEO & performance audit for any URL.**  
> Paste a link, hit "Run Audit", and get a structured report — HTTP status, response time, title, meta description, H1 count, image alt coverage, and word count — all in under a second.

Built as a focused, production-quality project demonstrating clean separation of concerns across a Java/Spring Boot backend and a vanilla-JS frontend.

---

## 🗂️ Table of Contents

1. [About the Project](#about-the-project)
2. [Tech Stack](#tech-stack)
3. [Project Structure](#project-structure)
4. [Setup & Running Locally](#setup--running-locally)
5. [Running the Tests](#running-the-tests)
6. [API Contract](#api-contract-get-audit)
7. [Deploying to Render / Railway](#deploying-to-render--railway)
8. [Design Decisions](#design-decisions)

---

## About the Project

Page Pulse is a single-endpoint web tool that accepts a URL, fetches the page server-side, and returns a JSON audit report. The frontend (a single `index.html`) calls that endpoint and renders the results as a clean, colour-coded dashboard.

**What it audits:**

| Metric | Description |
|---|---|
| `httpStatus` | HTTP status code the remote server returned |
| `responseTimeMs` | Round-trip fetch time measured in milliseconds |
| `title` | Content of the `<title>` element |
| `metaDescription` | Content of `<meta name="description">`, or `null` if missing |
| `h1Count` | Number of `<h1>` elements (SEO best practice: exactly 1) |
| `imagesMissingAlt` | Count of `<img>` tags where `alt` is absent or blank |
| `totalImages` | Total number of `<img>` tags on the page |
| `wordCount` | Approximate word count of visible body text |

**Error handling coverage:**
- Missing / blank URL → `400`
- Structurally invalid URL → `400` (caught before any network call)
- Request timeout (5 s limit) → `400`
- Non-HTML response (PDF, image, etc.) → `400`
- Any unexpected failure → `500` (clean JSON, no stack trace ever reaches the client)

---

## Tech Stack

### Backend

| Technology | Version | Why it's used |
|---|---|---|
| **Java** | 17 | LTS release; records, text blocks, and modern language features keep the model and test code concise |
| **Spring Boot** | 3.2.5 | Auto-configures an embedded Tomcat server, Jackson JSON serialisation, and the `@RestController` / `@ExceptionHandler` infrastructure — no boilerplate XML or servlet configuration needed |
| **Spring Web** | (via Boot) | Provides `@GetMapping`, `ResponseEntity`, and the MVC dispatcher that routes `GET /audit` to the controller |
| **Jsoup** | 1.17.2 | Does two jobs in one library: fetches the remote page over HTTP (with timeout, redirect following, and custom User-Agent) **and** parses the returned HTML into a traversable DOM. Using a single library for both tasks keeps the dependency list small and avoids impedance mismatch between a separate HTTP client and a separate parser |
| **Maven** | 3.8+ | Build tool and dependency manager; `spring-boot-maven-plugin` produces a self-contained runnable JAR |

### Frontend

| Technology | Why it's used |
|---|---|
| **HTML5 (single `index.html`)** | Served as a static file by Spring Boot's built-in resource handler — no separate server or build step required |
| **Vanilla JavaScript (ES2020)** | `fetch()` + `async/await` to call `/audit`, DOM manipulation to render results; no framework overhead for a single-page tool of this scope |
| **Vanilla CSS** | Custom design tokens (CSS variables), CSS Grid for the metric card layout, `@keyframes` for card entrance animations, and `@media` queries for mobile responsiveness — all without a CSS framework |
| **Google Fonts (Inter + JetBrains Mono)** | Inter for UI copy (readable at all sizes); JetBrains Mono for URLs and numeric values (monospaced alignment) |

### Testing

| Technology | Why it's used |
|---|---|
| **JUnit 5 (Jupiter)** | Included transitively via `spring-boot-starter-test`; `@DisplayName` and `assertAll` make test intent self-documenting |
| **Jsoup (in tests)** | `Jsoup.parse(String)` builds a `Document` from an inline HTML string — lets us test the parsing logic completely network-free |

---

## Project Structure

```
page-pulse/
├── pom.xml                                         # Maven build descriptor — dependencies & plugins
└── src/
    ├── main/
    │   ├── java/com/pagepulse/
    │   │   ├── PagePulseApplication.java           # Spring Boot entry point (@SpringBootApplication)
    │   │   │
    │   │   ├── controller/
    │   │   │   └── AuditController.java            # HTTP layer ONLY: routes GET /audit,
    │   │   │                                       # delegates to service, maps exceptions → JSON
    │   │   │
    │   │   ├── service/
    │   │   │   └── AuditService.java               # All business logic:
    │   │   │                                       #   audit()         — validates URL, fetches page
    │   │   │                                       #   parseDocument() — pure parsing, no network I/O
    │   │   │
    │   │   ├── model/
    │   │   │   ├── AuditReport.java                # Immutable Java record (the success response body)
    │   │   │   └── ErrorResponse.java              # Uniform error body { "error": "..." }
    │   │   │
    │   │   └── exception/
    │   │       └── AuditException.java             # Typed exception carrying HttpStatus;
    │   │                                           # thrown for all expected failures
    │   │
    │   └── resources/
    │       ├── application.properties              # server.port=${PORT:8080}
    │       └── static/
    │           └── index.html                      # Complete SPA: markup + CSS + JS in one file
    │
    └── test/
        └── java/com/pagepulse/service/
            └── AuditServiceTest.java               # 6 unit tests, zero network calls
```

---

## Setup & Running Locally

### Prerequisites

- **Java 17 or later** — [Download from Adoptium](https://adoptium.net)
- **Maven 3.8+** — [Download Maven](https://maven.apache.org/download.cgi)  
  *(or use `./mvnw` if you add the Maven Wrapper to the project)*

### Clone and run

```bash
# 1. Navigate into the project directory
cd page-pulse

# 2. Start the development server
mvn spring-boot:run
```

The application starts on **http://localhost:8080**.  
Open that URL in your browser — the frontend loads automatically.

### Build a standalone JAR

```bash
mvn package -DskipTests
java -jar target/page-pulse-0.0.1-SNAPSHOT.jar
```

---

## Running the Tests

```bash
mvn test
```

**Test coverage at a glance:**

| Test | What it verifies |
|---|---|
| `happyPath_allFieldsParsedCorrectly` | All 8 report fields parse correctly from well-formed HTML — no network call |
| `missingMetaDescription_returnsNull` | `metaDescription` is `null` (not `""`) when the tag is absent |
| `noImages_bothImageCountsAreZero` | Both image counts are `0` on a text-only page |
| `allImagesHaveAlt_missingAltIsZero` | `imagesMissingAlt` is `0` when every image has valid alt text |
| `blankUrl_throwsAuditExceptionMentioningUrl` | Blank URL → `AuditException` with "url" in the message |
| `nullUrl_throwsAuditExceptionMentioningUrl` | `null` URL → same exception |
| `malformedUrl_throwsAuditExceptionWithoutNetworkCall` | Invalid URL string → `400 BAD_REQUEST` before any HTTP attempt |

---

## API Contract: `GET /audit`

### Request

| Parameter | Type | Required | Description |
|---|---|---|---|
| `url` | String | ✅ Yes | Fully-qualified URL including scheme (e.g. `https://example.com`) |

**Example request:**
```
GET /audit?url=https://example.com
```

---

### Success Response — `200 OK`

```jsonc
{
  "httpStatus":       200,       // HTTP status returned by the audited page
  "responseTimeMs":   312,       // Round-trip fetch time in ms
  "title":            "Example Domain",
  "metaDescription":  "An illustrative domain used in documentation.",
                                 // null when <meta name="description"> is absent
  "h1Count":          1,         // Number of <h1> elements
  "imagesMissingAlt": 0,         // <img> tags with missing or blank alt
  "totalImages":      3,         // Total <img> tags
  "wordCount":        1542       // Approximate visible word count
}
```

> **`metaDescription`** is `null` (JSON `null`, not `""`) when the page has no `<meta name="description">` tag.

---

### Error Response — `4xx` / `5xx`

Every error — expected or unexpected — returns this consistent shape:

```json
{
  "error": "Human-readable description of what went wrong."
}
```

| Scenario | HTTP Status |
|---|---|
| Missing or blank `url` parameter | `400 Bad Request` |
| Structurally invalid URL | `400 Bad Request` |
| Remote server did not respond within 5 seconds | `400 Bad Request` |
| Response is not `text/html` (e.g. PDF, image) | `400 Bad Request` |
| Any other unexpected failure | `500 Internal Server Error` |

**Error example:**
```
GET /audit?url=not-a-real-url
```
```json
{
  "error": "The provided url is not a valid URL: not-a-real-url"
}
```

---

## Deploying to Render / Railway

The app reads the port from the `PORT` environment variable, which both platforms inject automatically. No extra configuration is needed.

```properties
# application.properties
server.port=${PORT:8080}
```

### Render

1. Create a **New Web Service** and connect your repository.
2. **Runtime:** Java
3. **Build Command:** `mvn package -DskipTests`
4. **Start Command:** `java -jar target/page-pulse-0.0.1-SNAPSHOT.jar`
5. Deploy — `PORT` is set automatically.

### Railway

1. Create a **New Project → Deploy from GitHub repo**.
2. Add a **Start Command:** `java -jar target/page-pulse-0.0.1-SNAPSHOT.jar`
3. Railway detects the Maven build automatically.
4. Deploy — `PORT` is set automatically.

---

## Design Decisions

1. **Jsoup for both fetching and parsing.** I used Jsoup because it handles both fetching the URL and parsing the HTML in a single library. This avoided needing a separate HTTP client plus a separate parser, which kept the code simpler for a one-day project.

2. **5-second request timeout.** I set a 5 second timeout on every request because without one, a slow or dead site would make the tool hang indefinitely. A short timeout keeps the tool responsive and forces a clear error instead of an infinite wait.

3. **Parsing logic separated from network code.** I kept the parsing logic in a separate method inside the service class, apart from the network-calling code. This meant I could unit test the parsing (title, meta description, H1 count, etc.) using plain HTML strings, without needing an actual network call in the tests.

---

## AI Usage Note

I used AI (via Antigravity/Claude) to scaffold the initial project structure, boilerplate code, and README. I reviewed the generated code, tested it locally, and wrote the design decisions and reasoning myself based on my own understanding of the trade-offs.

---

## Built For

**[Digital Heroes Training Task](https://digitalheroesco.com)**
