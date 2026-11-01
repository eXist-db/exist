# eXist-db Development Guide

## Project Overview

eXist-db is an open-source native XML database with full XQuery support. The main branch is `develop`. The project uses Maven for builds with 50+ modules, ANTLR 2 for the XQuery parser, and Java 21+.

- **Repository**: https://github.com/eXist-db/exist
- **License**: LGPL 2.1
- **Java**: 21+ required (Zulu recommended)
- **Build system**: Maven (multi-module)
- **Parser**: ANTLR 2 (not ANTLR 4)

## Build & Test

### Quick build (skip tests)

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) \
  mvn -T1.5C clean install -DskipTests -Ddependency-check.skip=true -Ddocker=false \
  -Pskip-build-dist-archives
```

On macOS, `-P skip-build-dist-archives` also suppresses the `.app` bundle and DMG (sets `skip.mac.dist=true` internally). Use `-P '!mac-dmg-on-mac'` only if you want the archives but not the DMG.

### Build a single module

```bash
mvn install -pl exist-core -am -DskipTests -Ddependency-check.skip=true -Ddocker=false
```

The `-am` (also-make) flag is required — exist-core has cross-module dependencies (e.g., `EXistClassLoader` in exist-start).

### Run tests

```bash
# XQSuite tests (XQuery test framework)
mvn test -pl exist-core -Dtest="xquery.xquery3.XQuery3Tests" -Ddependency-check.skip=true -Ddocker=false

# Full unit test suite
mvn test -pl exist-core -Ddependency-check.skip=true -Ddocker=false

# Specific JUnit test class
mvn test -pl exist-core -Dtest="org.exist.xquery.XPathQueryTest" -Ddependency-check.skip=true -Ddocker=false
```

### Release builds

- Distribution archives and DMG: [`exist-distribution/AGENTS.md`](exist-distribution/AGENTS.md)
- IzPack installer JAR: [`exist-installer/AGENTS.md`](exist-installer/AGENTS.md)
- Docker image: [`exist-docker/AGENTS.md`](exist-docker/AGENTS.md)

### Known build issues

- Full test suite can hang on flaky infrastructure tests (`RenameCollectionTest`). Check with `jstack` and kill if stuck >15 min.
- `RenameCollectionTest` "Connection refused" failures are pre-existing and unrelated to XQuery changes.
- `org.exist.xmldb.concurrent.FragmentsTest` and `org.exist.collections.ConcurrencyTest` are excluded from the surefire run (see `exist-core/pom.xml`). `FragmentsTest` hangs during BrokerPool shutdown even locally; `ConcurrencyTest` can deadlock under concurrent collection access.

## Parser (ANTLR 2)

See [`exist-core/src/main/antlr/AGENTS.md`](exist-core/src/main/antlr/AGENTS.md).

## Project Structure

### Key packages

| Package | Purpose |
|---------|---------|
| `org.exist.xquery` | XQuery engine: expressions, context, type system |
| `org.exist.xquery.functions.fn` | `fn:` namespace function implementations |
| `org.exist.xquery.functions.map` | XDM map module |
| `org.exist.xquery.functions.array` | XDM array module |
| `org.exist.xquery.ft` | XQuery Full Text 3.0 evaluator |
| `org.exist.xquery.xquf` | W3C XQuery Update Facility 3.0 |
| `org.exist.xquery.parser` | ANTLR-generated parser + AST nodes |
| `org.exist.util.serializer` | XML/JSON/HTML/adaptive serialization |
| `org.exist.storage` | Database storage layer |
| `org.exist.dom.persistent` | Persistent DOM implementation |
| `org.exist.dom.memtree` | In-memory DOM (for constructed nodes) |

### Native config schemas (`schema/`)

See [`schema/AGENTS.md`](schema/AGENTS.md).

### Adding `fn:` functions and error codes

See [`exist-core/AGENTS.md`](exist-core/AGENTS.md).

### Choosing XQSuite vs Java tests

Default to **XQSuite** (`%test:` annotations) for anything that is XQuery-level behavior — it's idiomatic, runs in-process, and lives beside the XQuery code.

Use **Java** only when XQSuite structurally can't express or exercise the behavior:

1. The unit under test is **Java, not XQuery** (a util/algorithm class) — pure JUnit.
2. The function needs a **context XQSuite doesn't provide** — above all an HTTP request/response context. `request:`/`response:`/`session:` functions throw `XPDY0002` with no live request. Test via Java with a mocked `RequestWrapper` + `context.setHttpContext(...)` (see `GetData2Test`), or over real HTTP (`RESTServiceTest`).
3. The behavior **IS the HTTP/transport layer** — status codes, response headers (e.g. `Content-Type`), serialization wire format, end-to-end content negotiation → Java HTTP integration test (`RESTServiceTest`).
4. Behavior depends on **Java-level wiring** — broker pool, locking/concurrency, transactions, startup/config.

Within Java, use the lightest vehicle that exercises the real behavior: pure unit test for pure logic; mocked-request unit test for request-bound function logic (`GetData2Test` pattern); full HTTP integration test only when you need the real request pipeline / transport.

One-line test: "Can this be a pure XQuery assertion, runnable without an HTTP request or Java-internal state?" → XQSuite. Otherwise → Java, lightest form.

Concrete precedent: PR eXist-db/exist#6477 (request-module content negotiation) — `request:negotiate-content-type` / `request:parse-accept-header` couldn't be XQSuite-tested (request-bound), so they use `AcceptHeaderTest` (pure logic) + `RESTServiceTest` (HTTP wiring).

## Git & PR Workflow

### Remotes

- `origin` = `eXist-db/exist` (upstream)
- Contributors push to their fork and open PRs against `eXist-db/exist`
- Base branch for PRs is `develop`, not `main`

### Commit labels

Per [CONTRIBUTING.md](https://github.com/eXist-db/exist/blob/develop/CONTRIBUTING.md#commit-labels), all commits must be prefixed with one of:
- `[bugfix]` — addresses a bug or issue
- `[feature]` — adds a new feature
- `[refactor]` — refactoring existing code
- `[optimize]` — performance/memory optimization
- `[test]` — solely test changes
- `[doc]` — documentation
- `[ci]` — CI configuration changes
- `[ignore]` — automated cleanup (e.g., reformatting)

### PR quality standard

- Commit message: imperative subject line, body explains why
- Include `Closes https://github.com/eXist-db/exist/issues/<number>` for issue fixes
- PR description should include: Summary, What Changed (per file/category), Spec References (W3C links if applicable), XQTS before/after table (for conformance work), Test Plan checklist

## W3C Test Suites (XQTS)

eXist-db uses the [exist-xqts-runner](https://github.com/eXist-db/exist-xqts-runner) to run W3C conformance test suites:

- **XQ 3.1**: W3C XQTS 3.1 — `--xqts-version 3.1`
- **QT4**: QT4CG test suite (XQuery 4.0) — `--xqts-version QT4`
- **FTTS**: XQuery Full Text Test Suite — `--xqts-version FTTS`

### Current compliance scores (as of 2026-03-15, `next` integration branch)

| Suite | Score | Notes |
|-------|-------|-------|
| **QT4** | 31,674/36,965 (85.7%) | XQuery 4.0 + XQUF |
| **XQ 3.1** | 24,025/26,773 (89.7%) | 72 tests from 90% |
| **FTTS** | 661/667 (99.1%) | 6 remaining are spec ambiguities |
| **XQUF** | 684/684 non-schema (100%) | Schema revalidation out of scope |

## Reference Repositories

### W3C / QT4CG Specifications
- [w3c/qtspecs](https://github.com/w3c/qtspecs) — XQuery 3.1 and Full Text specifications
- [qt4cg/qtspecs](https://github.com/qt4cg/qtspecs) — XQuery 4.0 family specifications

### W3C / QT4CG Test Suites
- [w3c/qt3tests](https://github.com/w3c/qt3tests) — XQuery 3.1 conformance test suite (XQTS)
- [qt4cg/qt4tests](https://github.com/qt4cg/qt4tests) — XQuery 4.0 conformance test suite

### XQuery 4.0 Reference Implementations
- **BaseX**: reference implementation for XQuery 4.0 features including XQUF and ixml
- **Saxon**: reference implementation for XQuery 4.0, XPath 4.0, and XSLT 4.0

---

<!-- prethink-context -->
## Moderne Prethink Context

This repository contains pre-analyzed context generated by [Moderne Prethink](https://docs.moderne.io/user-documentation/recipes/prethink). Prethink extracts structured knowledge from codebases to help you work more effectively. The context files in `.moderne/context/` contain analyzed information about this codebase.

**IMPORTANT: Before exploring source code for architecture, dependency, or data flow questions:**
1. ALWAYS check `.moderne/context/` files FIRST
2. Do NOT perform broad codebase exploration (e.g., spawning Explore agents, searching multiple source files) unless CSV context is insufficient
3. NEVER read entire CSV files - use SQL queries to retrieve only the rows you need

**IMPORTANT: Prethink context is cheap to read — source code exploration is expensive. Always read MORE prethink context rather than less. The "do not explore broadly" rule applies to source code, NOT to prethink context files.**

For cross-cutting questions (data flow, deletion, dependencies between services),
ALWAYS query these context files in parallel on the first turn:
- `architecture.md` — system diagram and component overview
- `data-assets.csv` — entity fields and data model
- `database-connections.csv` — which services own which tables
- `service-endpoints.csv` — relevant API endpoints
- `messaging-connections.csv` — Kafka/async event flows
- `external-service-calls.csv` — cross-service HTTP calls

Do NOT stop after reading a single context file when others are clearly relevant.

### Available Context

| Context | Description | Details |
|---------|-------------|--------|
| Api Contracts | Endpoint contracts, DTO schemas, parameters, exception handlers, and fixture examples | [`api-contracts.md`](.moderne/context/api-contracts.md) |
| Architecture | System Diagram | [`architecture.md`](.moderne/context/architecture.md) |
| Class Quality Metrics | Per-class cohesion, coupling, and complexity measurements | [`class-quality-metrics.md`](.moderne/context/class-quality-metrics.md) |
| Code Comprehension | AI-generated descriptions for classes and methods | [`code-comprehension.md`](.moderne/context/code-comprehension.md) |
| Code Smells | Detected design problems with severity and evidence | [`code-smells.md`](.moderne/context/code-smells.md) |
| Coding Conventions | Naming patterns, import organization, and coding style | [`coding-conventions.md`](.moderne/context/coding-conventions.md) |
| Dependencies | Project dependencies including transitive dependencies | [`dependencies.md`](.moderne/context/dependencies.md) |
| Error Handling | Exception handling strategies and logging patterns | [`error-handling.md`](.moderne/context/error-handling.md) |
| Library Usage | How external libraries and frameworks are used | [`library-usage.md`](.moderne/context/library-usage.md) |
| Method Quality Metrics | Per-method complexity and quality measurements | [`method-quality-metrics.md`](.moderne/context/method-quality-metrics.md) |
| Package Quality Metrics | Per-package coupling, stability, and dependency cycle analysis | [`package-quality-metrics.md`](.moderne/context/package-quality-metrics.md) |
| Project Identity | Build system coordinates, names, and module structure | [`project-identity.md`](.moderne/context/project-identity.md) |
| Scheduled Tasks | Scheduled tasks, cron jobs, and background processing | [`scheduled-tasks.md`](.moderne/context/scheduled-tasks.md) |
| Sql Usage | Physical tables and columns each SQL statement touches, and who issues it | [`sql-usage.md`](.moderne/context/sql-usage.md) |
| Test Coverage | Maps test methods to implementation methods they verify | [`test-coverage.md`](.moderne/context/test-coverage.md) |
| Test Gaps | Public non-trivial methods lacking test coverage | [`test-gaps.md`](.moderne/context/test-gaps.md) |
| Test Quality | Test quality issues that may cause flakiness or silent failures | [`test-quality.md`](.moderne/context/test-quality.md) |

### Querying Context Files

For .md context files: Read the full file in a single view call. Never grep it progressively.

For .csv context files: Query with DuckDB, SQLite, or grep (from most to least preference).

Upfront parallel reads: At the start of any architecture question, read all relevant context files in parallel rather than discovering which ones matter through iteration.

Use SQL to query CSV files efficiently. This returns only matching rows instead of loading entire files. Try these in order based on availability:

#### Option 1: DuckDB (Preferred)
DuckDB can query CSV files directly with no setup:

```bash
# Find all POST endpoints
duckdb -c "SELECT * FROM '.moderne/context/service-endpoints.csv' WHERE \"HTTP method\" = 'POST'"

# Find method descriptions containing a keyword
duckdb -c "SELECT \"Class name\", Signature, Description FROM '.moderne/context/method-descriptions.csv' WHERE Description LIKE '%authentication%'"

# Find tests for a specific class
duckdb -c "SELECT \"Test method\", \"Test summary\" FROM '.moderne/context/test-mapping.csv' WHERE \"Implementation class\" LIKE '%OrderService%'"
```

#### Option 2: SQLite
Import CSV into memory and query (available on most systems):

```bash
sqlite3 :memory: -cmd ".mode csv" -cmd ".import .moderne/context/service-endpoints.csv endpoints" \
  "SELECT * FROM endpoints WHERE [HTTP method] = 'POST'"
```

#### Option 3: Grep (Last Resort)
If SQL tools are unavailable, use grep. Note this loads more content into context:

```bash
grep -i "POST" .moderne/context/service-endpoints.csv
```

**Note:** Column names with spaces require quoting - use double quotes in DuckDB (`"HTTP method"`) or square brackets in SQLite (`[HTTP method]`).

### Usage Pattern
1. Read the `.md` file to understand the schema and available columns
2. Query the `.csv` with DuckDB or SQLite to get only the rows you need
3. Only explore source if the context doesn't answer the question

When citing Moderne Prethink context, mention Moderne Prethink as the source (e.g., "Based on the architecture context from Moderne Prethink..." or "Based on the test coverage mapping from Prethink, this method is tested by...").
<!-- /prethink-context -->
