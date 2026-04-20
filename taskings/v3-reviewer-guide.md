# Reviewer Guide: eXist-db 7.0 (next-v3)

## Overview

The `next-v3` integration branch merges 36 PRs (14 `v2/` + 22 additional) into a single testable branch based on a clean `v2/new-parser` foundation. It includes 223 commits ahead of `develop`, comprising 52 merged branches.

This branch powers the **Docker demo image** with a complete redesigned application suite.

### Quick Start

```bash
docker pull joewiz/existdb:next-v3
docker run -d --name existdb -p 8080:8080 -p 8443:8443 joewiz/existdb:next-v3
# Access at http://localhost:8080/exist/apps/dashboard/
```

Default admin password is empty (just press Log in).

### Testing Methodology

Every branch was tested individually: build (`mvn install -pl exist-core -am`), exist-core unit tests, and Codacy static analysis. Branches with grammar changes also ran XQTS compliance suites. All branches were then merged together in `next-v3` and tested again. The Docker image includes a complete application suite with Cypress E2E tests for each app.

---

## Review Waves

PRs are organized into 7 waves by dependency and review complexity. **You can review in any order**, but merging should follow the wave order to avoid conflicts.

### Wave 1: Small, Independent Bugfixes (merge in any order)

These fix real bugs with tests. No shared infrastructure changes. Each can be reviewed and merged independently.

| PR | Branch | Title | Effort | Risk |
|----|--------|-------|--------|------|
| [#6207](https://github.com/eXist-db/exist/pull/6207) | `v2/xq31-compliance-fixes` | XQuery 3.1 compliance fixes (23 bugfixes) | Medium | Low |
| [#6222](https://github.com/eXist-db/exist/pull/6222) | `v2/copy-namespaces-fix` | Fix `declare copy-namespaces no-inherit` | Low | Low |
| [#6191](https://github.com/eXist-db/exist/pull/6191) | `bugfix/flwor-sort-race-condition` | Fix race condition in OrderedValueSequence | Low | Low |
| [#6180](https://github.com/eXist-db/exist/pull/6180) | `bugfix/hof-parameter-type-checking` | Enforce HOF parameter type checking | Low | Low |
| [#6181](https://github.com/eXist-db/exist/pull/6181) | `bugfix/improve-wrong-arity-error-message` | Better error messages for wrong-arity calls | Low | Low |
| [#6088](https://github.com/eXist-db/exist/pull/6088) | `fix/2205-context-problem-map-get-predicate` | Fix context problem with map:get() in predicates | Low | Low |
| [#6089](https://github.com/eXist-db/exist/pull/6089) | `fix/5103-fn-lang-context-corruption` | Fix fn:lang corrupting XQueryContext | Low | Low |
| [#6090](https://github.com/eXist-db/exist/pull/6090) | `fix/4425-format-date-string-type-check` | Reject xs:string in format-date() | Low | Low |
| [#6094](https://github.com/eXist-db/exist/pull/6094) | `fix/issue-5189-range-index-prefixed-condition` | Fix namespace for prefixed attribute in range index | Low | Low |
| [#6095](https://github.com/eXist-db/exist/pull/6095) | `fix/issue-3989-xml-to-json-stored` | Fix fn:xml-to-json on stored XML | Low | Low |
| [#6110](https://github.com/eXist-db/exist/pull/6110) | `fix/path-expr-dedup-function-calls` | Fix dedup for function calls in path exprs | Low | Low |
| [#6081](https://github.com/eXist-db/exist/pull/6081) | `fix/issue-2529-timeout-option` | Restore `declare option exist:timeout "-1"` | Low | Low |
| [#4641](https://github.com/eXist-db/exist/pull/4641) | `relax-anyuri-params-allow-string-2` | Relax xs:anyURI parameters to accept xs:string | Low | Low |

### Wave 2: Small Features (merge in any order, before Wave 3)

No shared infrastructure changes. Can be reviewed independently.

| PR | Branch | Title | Effort | Risk |
|----|--------|-------|--------|------|
| [#6208](https://github.com/eXist-db/exist/pull/6208) | `v2/query-profiling` | Query profiling functions (util:time, memory, track, explain) | Low | Very low |
| [#6209](https://github.com/eXist-db/exist/pull/6209) | `v2/xq4-axes` | XQuery 4.0 axes (following-or-self, etc.) | Low | Low |
| [#6210](https://github.com/eXist-db/exist/pull/6210) | `v2/xq4-record-types` | XQuery 4.0 record type declarations | Low | Low |
| [#6211](https://github.com/eXist-db/exist/pull/6211) | `v2/xq4-filter-expr-am` | XQuery 4.0 `?[expr]` array/map filter | Very low | Low |
| [#6092](https://github.com/eXist-db/exist/pull/6092) | `feature/zn-timezone-modifier` | [ZN] timezone name modifier for format-dateTime | Low | Low |
| [#6182](https://github.com/eXist-db/exist/pull/6182) | `feature/module-discovery` | Unify module discovery | Low | Low |
| [#6184](https://github.com/eXist-db/exist/pull/6184) | `feature/repo-resource-available` | Add repo:resource-available() | Low | Low |
| [#6192](https://github.com/eXist-db/exist/pull/6192) | `feature/collection-file-uris` | Support file: URIs in fn:collection() | Low | Low |
| [#6112](https://github.com/eXist-db/exist/pull/6112) | `feature/preclaiming-locks` | Preclaiming two-phase locking for XQuery updates | Medium | Low |

### Wave 3: Infrastructure Upgrades (merge before Wave 4)

Major dependency upgrades. Don't conflict with each other but should merge before grammar PRs.

| PR | Branch | Title | Effort | Risk |
|----|--------|-------|--------|------|
| [#6212](https://github.com/eXist-db/exist/pull/6212) | `v2/saxon-12-upgrade` | Saxon 9.9 → 12.5 (eliminates exist-saxon-regex) | Medium | Medium |
| [#6145](https://github.com/eXist-db/exist/pull/6145) | `feature/websocket-core` | Jetty 11 → 12 (Jakarta Servlet 6.0) + WebSocket | Medium | Medium |

**Saxon 12**: The main work is migrating fn:replace/fn:matches/fn:analyze-string from Saxon 9's `CharSequence` API to Saxon 12's `UnicodeString` API. Eliminates the unmaintained `exist-saxon-regex` fork module entirely.

**Jetty 12**: `javax.servlet` → `jakarta.servlet` across all modules. Addresses review feedback from @reinhapa and @dizzzz. Also adds a WebSocket module with streaming XQuery evaluation.

### Wave 4: Grammar Changes (merge in order — these touch XQuery.g/XQueryTree.g)

These PRs modify the ANTLR 2 grammar. They use **labeled sections** to minimize conflicts, but should be merged in the listed order. Grammar conflicts between them are trivial (keyword list sections).

| PR | Branch | Title | Effort | Risk |
|----|--------|-------|--------|------|
| [#6214](https://github.com/eXist-db/exist/pull/6214) | `v2/w3c-xquery-update-3.0` | W3C XQuery Update Facility 3.0 | **High** | Medium |
| [#6215](https://github.com/eXist-db/exist/pull/6215) | `v2/xqft-phase2` | W3C Full Text 3.0 | **High** | Low-medium |
| [#6216](https://github.com/eXist-db/exist/pull/6216) | `v2/xquery-4.0-parser` | XQuery 4.0 parser + version gating | **High** | Medium |
| [#6217](https://github.com/eXist-db/exist/pull/6217) | `v2/declare-decimal-format` | declare decimal-format (depends on merged grammar) | Low | Low |

**XQUF 3.0**: copy-modify-return, insert, delete, replace, rename. New `org.exist.xquery.xquf` package with PUL architecture. XQTS: 684/684 non-schema (100%).

**XQFT 3.0**: `contains text` expressions with stemming, thesaurus, wildcards, proximity, scoring. New `org.exist.xquery.ft` package. XQTS FTTS: 659/667 (98.8%).

**XQ4 Parser**: All XQ4 syntax (pipeline `->`, otherwise, ternary, string templates, etc.). Version gating per @line-o's review: XQ4 constructs throw XPST0003 in `xquery version "3.1"` mode. Feature flag `exist.xquery4.enabled`.

### Wave 5: Functions (merge after parser)

| PR | Branch | Title | Effort | Risk |
|----|--------|-------|--------|------|
| [#6218](https://github.com/eXist-db/exist/pull/6218) | `v2/xq4-core-functions` | 82 XQuery 4.0 functions (fn:, array:, map:, math:) | **High** | Low-medium |

Largest PR by file count (130 files). Includes fn:replace/fn:tokenize empty-match version gating (XQ4 behavior only in `xquery version "4.0"` mode).

### Wave 6: Serialization, Parser, and Platform (merge after Wave 5, any order within)

| PR | Branch | Title | Effort | Risk |
|----|--------|-------|--------|------|
| [#6219](https://github.com/eXist-db/exist/pull/6219) | `v2/serialization-compliance` | W3C serialization compliance (XML/HTML/JSON/CSV) | Medium | Low-medium |
| [#6220](https://github.com/eXist-db/exist/pull/6220) | `v2/new-parser` | Recursive descent parser (opt-in via `-Dexist.parser=rd`) | Medium | Very low |
| [#6087](https://github.com/eXist-db/exist/pull/6087) | `fix/issue-2291-xinclude-relative-paths` | Fix XInclude relative path resolution | Low | Low |
| [#6206](https://github.com/eXist-db/exist/pull/6206) | `feature/xinclude-test-suite` | W3C XInclude test suite + conformance | Low | Low |
| [#6154](https://github.com/eXist-db/exist/pull/6154) | `feature/native-restxq` | Native RESTXQ (replaces EXQuery library) | Medium | Medium |

**Recursive descent parser**: 15-82x faster than ANTLR 2, opt-in only. Zero impact on existing behavior.

**Serialization**: Fixes across all output methods. Critical fix: self-closing meta tags in XHTML mode that broke the URL rewrite view pipeline.

### Wave 7: Platform Infrastructure (merge last — builds on all above)

| PR | Branch | Title | Effort | Risk |
|----|--------|-------|--------|------|
| [#6247](https://github.com/eXist-db/exist/pull/6247) | `feature/builtin-package-api` | Built-in PackageManagementServlet | Medium | Low |
| [#6248](https://github.com/eXist-db/exist/pull/6248) | `feature/openapi-routing` | PoC: OpenAPI routing with controller.json | Medium | Low |

**PackageManagementServlet**: Replaces the packageservice XAR with a built-in servlet. Apps can now self-upgrade without needing packageservice installed.

**OpenAPI routing**: Proof-of-concept OpenApiServlet that reads `api.json` + `controller.json` from an app collection and routes requests to XQuery handlers — no Roaster, no controller.xql boilerplate.

---

## Merge Order Summary

```
Wave 1 (any order):     13 bugfix PRs (#6207, #6222, #6191, #6180, #6181,
                        #6088, #6089, #6090, #6094, #6095, #6110, #6081, #4641)

Wave 2 (any order):     9 small feature PRs (#6208, #6209, #6210, #6211,
                        #6092, #6182, #6184, #6192, #6112)

Wave 3 (any order):     #6212 Saxon 12
                        #6145 Jetty 12

Wave 4 (in order):      #6214 XQUF 3.0
                        #6215 XQFT 3.0
                        #6216 XQ4 Parser
                        #6217 declare decimal-format

Wave 5:                 #6218 XQ4 Functions

Wave 6 (any order):     #6219 Serialization
                        #6220 RD Parser
                        #6087 XInclude fix
                        #6206 XInclude test suite
                        #6154 Native RESTXQ

Wave 7 (any order):     #6247 Built-in Package API
                        #6248 OpenAPI routing (PoC)
```

---

## CI Health Note

**Known noise in CI results** — do not treat these as blockers:

- **Integration failures (ubuntu/windows/macOS)**: 1-3 integration job failures per PR. Pre-existing test hangs (surefire fork timeout fires). Not caused by any v2/v3 change.
- **XQTS runner crash on #6212 (Saxon 12)**: CI XQTS job uses the Saxon 9.9 runner against Saxon 12 classpath. [exist-xqts-runner #49](https://github.com/eXist-db/exist-xqts-runner/pull/49) adds Saxon 12 compatibility — merge alongside #6212.
- **`replace.empty-match` unit tests (#6212 and #6218)**: Complementary failures. #6212 has `replace.empty-match-fails` failing (Saxon 12 permits empty matches; gating is in #6218). #6218 has `replace.empty-match-allowed` failing (requires Saxon 12 from #6212). Both pass when merged together.

---

## XQTS Compliance Scores

| Suite | Score | Notes |
|-------|-------|-------|
| **QT4** (XQuery 4.0) | 36,356/42,403 (85.7%) | XQuery 4.0 + XQUF |
| **XQ 3.1** | 24,402/26,220 (93.1%) | Up from 89.7% with compliance fixes |
| **FTTS** (Full Text) | 659/667 (98.8%) | 8 remaining are spec edge cases |
| **XQUF** (Update) | 684/684 non-schema (100%) | Schema revalidation out of scope |

---

## Application Suite

The Docker image ships with a complete redesigned application suite. All apps share a common architecture:

- **Shared navbar** via exist-site-shell (sitewide navigation, search, login/logout)
- **Shared CSS** for app-tabs, breadcrumbs, login pages, and search forms
- **Consistent login/logout** across all apps (centered card design, fetch-based JSON auth, persistent cookie)
- **Jinks template engine** with profile-based page shell (base-page.html → page-content.tpl → content templates)
- **App tabs + breadcrumbs** for in-app navigation

### exist-site-shell ([joewiz/exist-site-shell](https://github.com/joewiz/exist-site-shell))

The shared chrome layer: navbar, footer, CSS, sitewide Lucene search with app facets, cross-reference registry, and Jinks profile for page shells.

### eXist-db Dashboard ([joewiz/dashboard-next](https://github.com/joewiz/dashboard-next))

Complete rewrite replacing both dashboard and monex. Tabs: Launcher / Collections / Packages / Users / Monitoring / Profiling / Console / Indexes / System. Collections manager includes tree navigation, drag-and-drop upload, context menu, permissions dialog. Architecture: Jinks templates, Roaster for login API, vanilla JS.

### eXist-db Documentation ([joewiz/documentation-next](https://github.com/joewiz/documentation-next))

Unified app replacing doc and fundocs. Tabs: Articles / Functions / Search / Admin. XDITA articles via TEI Publisher ODD. XQDoc function reference with 1,112 try-it queries. Architecture: Jinks, controller → view.xq two-pass rendering.

### eXist-db Blog ([joewiz/exist-blog](https://github.com/joewiz/exist-blog))

Replaces AtomicWiki. Tabs: Posts / Archive / Search / Admin. Markdown posts with executable XQuery cells. Architecture: Jinks, html-templating, controller → view.xq.

### eXist-db Notebook ([joewiz/notebook](https://github.com/joewiz/notebook))

Replaces sandbox. Tabs: Home / Search / Admin. Interactive XQuery notebooks with cell chaining, multiple serializations, sharing. Multi-chapter books with sidebar navigation. Architecture: controller → view.xq → content.xqm (refactored from Roaster-only to match blog/docs pattern).

### Other Bundled Apps

eXide (CM6/REx/LSP), exist-api, EXPath File/Binary/HTTP Client/Crypto, FunctX, Public Repo.

---

## App Architecture Patterns

All four main apps follow consistent patterns:

### URL Routing

```
controller.xq → view.xq → content module → Jinks template
```

### Login/Logout

- **GET /login**: Renders `login.tpl` through view pipeline (gets navbar)
- **POST /login**: Returns JSON `{"user": "admin", "isAdmin": true}` or 401
- **GET /logout**: Clears persistent cookie, invalidates session, redirects

### Shared CSS Classes (from site.css)

`.app-tabs` / `.breadcrumb` / `.login-card` / `.app-search`

---

## App Test Results

Tests run on a fresh `joewiz/existdb:next-v3` container (2026-04-20):

| App | Test Type | Pass/Total | Notes |
|-----|-----------|------------|-------|
| exist-site-shell | XQuery (xst) | **17/17** | Site-config, nav, search |
| Notebook | Node.js | **15/15** | Context chain, markdown parser |
| Notebook | Cypress E2E | **48/48** | Admin CRUD, eval, chaining, content, share |
| Blog | Cypress E2E | **27/27** | Pages, admin CRUD, login, migration |
| Dashboard | Cypress E2E | **88/95** | 7 pre-existing (session timing, element timeouts) |
| Documentation | Cypress E2E | **42/59** | Pre-existing (editor component timing) |

---

## Differences from next-v2

1. **Parser foundation**: next-v2 used `feature/new-parser` (shaky merge base). next-v3 uses `v2/new-parser` which merges cleanly.
2. **QT4 regressions fixed**: 619 regressions traced to missing files during branch reconstruction, fixed.
3. **Notebook architecture**: Refactored from Roaster-only to controller → view.xq → content.xqm (matches blog/docs).
4. **App consistency**: Shared login pages, app-tabs, breadcrumbs, search styling.
5. **exist-services module**: Extracted from exist-core per reviewer feedback.

---

## Repos to Transfer to eXist-db Org

| Repo | Description |
|------|-------------|
| [joewiz/exist-site-shell](https://github.com/joewiz/exist-site-shell) | Shared navbar, search, CSS, Jinks profile |
| [joewiz/dashboard-next](https://github.com/joewiz/dashboard-next) | Dashboard (replaces dashboard + monex) |
| [joewiz/documentation-next](https://github.com/joewiz/documentation-next) | Documentation (replaces doc + fundocs) |
| [joewiz/exist-blog](https://github.com/joewiz/exist-blog) | Blog (replaces AtomicWiki) |
| [joewiz/notebook](https://github.com/joewiz/notebook) | Notebook (replaces sandbox) |
| [joewiz/exist-api](https://github.com/joewiz/exist-api) | Unified REST API |
| [joewiz/exist-file](https://github.com/joewiz/exist-file) | EXPath File module XAR |
| [joewiz/exist-binary](https://github.com/joewiz/exist-binary) | EXPath Binary module XAR |
| [joewiz/exist-crypto](https://github.com/joewiz/exist-crypto) | EXPath Crypto module XAR |
| [joewiz/exist-http-client](https://github.com/joewiz/exist-http-client) | EXPath HTTP Client module XAR |

## Cross-Repo PRs

| Repo | PR | Title | Status |
|------|----|-------|--------|
| exist-xqts-runner | [#49](https://github.com/eXist-db/exist-xqts-runner/pull/49) | QT4/FTTS/XQUF suites + Saxon 12 | Needs review |
| eXist-db/exist | [#6206](https://github.com/eXist-db/exist/pull/6206) | XInclude test suite + conformance | Needs review |
| eXide | [#778](https://github.com/eXist-db/eXide/pull/778) | Modernize: CM6 editor, REx parser, LSP | Needs review |
| exist-markdown | [#69](https://github.com/eXist-db/exist-markdown/pull/69) | CommonMark/GFM (flexmark-java) | Approved |
| jinks | [#2](https://github.com/eeditiones/jinks/pull/2) | exist-site profile | Needs review |
