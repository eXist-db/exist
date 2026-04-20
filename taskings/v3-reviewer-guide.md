# Reviewer Guide: eXist-db 7.0 (next-v3)

## Overview

The `next-v3` integration branch merges all v2/ PRs plus additional features and bugfixes into a single testable branch, based on a clean `v2/new-parser` foundation (fixing the shaky `feature/new-parser` base that next-v2 used). It includes 223 commits ahead of `develop`, comprising 52 merged branches.

This branch powers the **Docker demo image** with a complete set of redesigned eXist-db 7.0 applications.

### Quick Start

```bash
docker pull joewiz/existdb:next-v3
docker run -d --name existdb -p 8080:8080 -p 8443:8443 joewiz/existdb:next-v3
# Access at http://localhost:8080/exist/apps/dashboard/
```

Default admin password is empty (just press Log in).

---

## What's in next-v3

### Core Engine (from v2/ PRs)

All 14 v2/ PRs from the [v2 Reviewer Guide](v2-reviewer-guide.md) are included:

| Feature | PR | Status |
|---------|-------|--------|
| XQuery 3.1 compliance fixes (23 bugfixes) | #6207 | Merged |
| Query profiling functions | #6208 | Merged |
| XQuery 4.0 axes | #6209 | Merged |
| XQuery 4.0 record types | #6210 | Merged |
| XQuery 4.0 array/map filter | #6211 | Merged |
| Saxon 9.9 → 12.5 | #6212 | Merged |
| Jetty 11 → 12 (Jakarta Servlet 6.0) + WebSocket | #6145 | Merged |
| W3C XQuery Update Facility 3.0 | #6214 | Merged |
| W3C Full Text 3.0 | #6215 | Merged |
| XQuery 4.0 parser + version gating | #6216 | Merged |
| declare decimal-format | #6217 | Merged |
| 82 XQuery 4.0 functions | #6218 | Merged |
| W3C serialization compliance | #6219 | Merged |
| Recursive descent parser (opt-in) | #6220 | Merged |

### Additional Merged Branches (beyond v2/)

| Feature | Branch | Description |
|---------|--------|-------------|
| FLWOR order-by/count/window fixes | fix/issue-5089-flwor-orderby | W3C §3.9.4 compliance |
| copy-namespaces fix | v2/copy-namespaces-fix | Namespace handling |
| Native RESTXQ | feature/native-restxq | Built-in RESTXQ support |
| XInclude test suite | feature/xinclude-test-suite | W3C XInclude 1.0 conformance |
| EXPath File module | feature/expath-file-module | Coexists with native file module |
| Built-in Package API | feature/builtin-package-api | PackageManagementServlet (replaces packageservice XAR) |
| OpenAPI routing (PoC) | feature/openapi-routing | OpenApiServlet with controller.json |
| exist-services extraction | (direct commit) | Extracted from exist-core per reviewer feedback |
| Collection/file URI improvements | feature/collection-file-uris | URI handling |
| Module discovery unification | feature/module-discovery | Unified module loading |
| 10+ bugfix branches | fix/* | Various bugfixes with approvals |

### XQTS Compliance Scores

| Suite | Score | Notes |
|-------|-------|-------|
| **QT4** (XQuery 4.0) | 31,674/36,965 (85.7%) | XQuery 4.0 + XQUF |
| **XQ 3.1** | 24,025/26,773 (89.7%) | 72 tests from 90% |
| **FTTS** (Full Text) | 661/667 (99.1%) | 6 remaining are spec ambiguities |
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

The shared chrome layer providing navbar, footer, CSS, and sitewide search across all apps.

- Sitewide Lucene search with app facets and scope selector
- Cross-app navigation bar with login/logout
- Jinks profile: apps generated from profile get base-page.html, nav module, site-config module
- Shared CSS: `.app-tabs`, `.breadcrumb`, `.login-card`, `.app-search`
- Cross-reference registry: `{docs/functions/fn/doc}` → resolved URLs

### eXist-db Dashboard ([joewiz/dashboard-next](https://github.com/joewiz/dashboard-next))

Complete rewrite of the administration dashboard, replacing both dashboard and monex.

**Tabs**: Launcher / Collections / Packages / Users / Monitoring / Profiling / Console / Indexes / System

- **Launcher**: App tile grid (public)
- **Collections**: Full database browser with tree navigation, drag-and-drop upload, context menu (rename, delete, copy, move), permissions dialog (public, read-only for guests)
- **Packages**: Install/remove XAR packages
- **Users**: User and group management
- **Monitoring**: Running queries, active locks, cache statistics
- **Profiling**: Query profiling with flame-graph-style analysis
- **Console**: XQuery execution console
- **Indexes**: Index configuration viewer
- **System**: JVM, database, and OS information

**Architecture**: Jinks templates, Roaster for login API and admin CRUD, vanilla JS (no framework).

### eXist-db Documentation ([joewiz/documentation-next](https://github.com/joewiz/documentation-next))

Unified documentation app replacing both doc and fundocs.

**Tabs**: Articles / Functions / Search / Admin

- **Articles**: XDITA (Lightweight DITA) articles rendered via TEI Publisher's ODD processing model. Dynamic indexes (alphabetical and by-keyword) generated from `outputclass` attributes.
- **Functions**: XQDoc-based function reference for all installed modules. Inline documentation articles with YAML front matter. Try-it widgets with live XQuery evaluation.
- **Search**: Lucene full-text across articles and functions with faceted filtering.
- **Admin**: XDITA article editor (JinnTap/jinn-codemirror), XQDoc regeneration, DocBook → XDITA conversion.

**Architecture**: Jinks templates, controller → view.xq → content modules. Two-pass rendering (content template → page wrapper).

### eXist-db Blog ([joewiz/exist-blog](https://github.com/joewiz/exist-blog))

Blog/news app replacing AtomicWiki.

**Tabs**: Posts / Archive / Search / Admin

- **Posts**: Markdown posts with syntax highlighting and executable XQuery cells (jinn-codemirror)
- **Archive**: Browse by year/month, tag filtering
- **Search**: Lucene full-text with shared `.app-search` styling
- **Admin**: Post editor (jinn-codemirror markdown), CRUD for posts
- **Breadcrumbs**: Blog / Posts / {title}, Blog / Archive / {year}, Blog / Tags / {tag}

**Architecture**: Jinks templates, html-templating for data binding, controller → view.xq two-pass rendering.

### eXist-db Notebook ([joewiz/notebook](https://github.com/joewiz/notebook))

Interactive XQuery notebook replacing sandbox.

**Tabs**: Home / Search / Admin

- **Notebooks**: Markdown documents with executable XQuery cells (jinn-codemirror). Cell chaining (results from earlier cells available as `$cell1`, `$cell2`, etc.). Multiple serialization modes (adaptive, XML, JSON, text).
- **Books**: Multi-chapter tutorials with sidebar navigation, breadcrumbs, prev/next links
- **Sharing**: Save and share notebook state via short URLs
- **Search**: Lucene full-text with trigger-based shadow index
- **Admin**: Book/chapter/notebook CRUD (Roaster API + Fore forms)

**Architecture**: Refactored to match blog/docs pattern — controller → view.xq → content.xqm. Public pages go through the Jinks view pipeline; API endpoints (`/api/*`) and admin CRUD go through Roaster. This is a significant architectural improvement over next-v2, where everything went through Roaster.

### Other Bundled Apps

| App | Source | Notes |
|-----|--------|-------|
| eXide | eXist-db/eXide | CodeMirror 6, REx parser, LSP support |
| exist-api | joewiz/exist-api | Unified REST API (uses Roaster) |
| EXPath File | joewiz/exist-file | Coexists with native file module |
| EXPath Binary | joewiz/exist-binary | EXPath Binary module |
| EXPath HTTP Client | joewiz/exist-http-client | HTTP Client module |
| EXPath Crypto | joewiz/exist-crypto | Cryptographic functions |
| FunctX | functx | XQuery function library |
| Public Repo | public-repo | Package repository connector |

---

## App Architecture Patterns

All four main apps (Dashboard, Documentation, Blog, Notebook) follow consistent patterns that reviewers should understand:

### URL Routing

```
controller.xq → view.xq → content module → Jinks template
```

- `controller.xq`: URL routing, login/logout handling, request attribute setting
- `view.xq`: Builds Jinks context (navbar, tabs, breadcrumbs, styles), calls content functions, wraps in page template
- Content module (or html-templating): Generates page HTML
- `page-content.tpl`: Extends `base-page.html` from the exist-site profile

### Login/Logout

All apps use the same pattern:
- **GET /login**: Renders `login.tpl` through the view pipeline (gets navbar)
- **POST /login**: Returns JSON `{"user": "admin", "isAdmin": true}` or 401
- **GET /logout**: Clears `org.exist.login` cookie, invalidates session, redirects to app root
- **Login template**: Shared `.login-card` design with fetch-based JS handler

### Shared CSS Classes

Provided by `exist-site-shell/resources/css/site.css`:
- `.app-tabs` — tab navigation bar (Articles / Functions / Search / Admin)
- `.breadcrumb` + `.breadcrumb-sep` — breadcrumb navigation
- `.login-page` + `.login-card` + `.form-field` + `.login-error` — login form
- `.app-search` — search input + button bar

---

## Test Results

Tests run on a fresh `joewiz/existdb:next-v3` container (2026-04-20):

| App | Test Type | Pass/Total | Notes |
|-----|-----------|------------|-------|
| exist-site-shell | XQuery (xst) | **17/17** | Site-config, nav, search tests |
| Notebook | Node.js | **15/15** | Context chain builder, markdown parser |
| Notebook | Cypress E2E | **48/48** | Admin CRUD, eval, chaining, content, share |
| Blog | Cypress E2E | **27/27** | Pages, admin CRUD, login, migration |
| Dashboard | Cypress E2E | **88/95** | 7 pre-existing (login session timing, system/users element timeouts) |
| Documentation | Cypress E2E | **42/59** | Pre-existing: editor (9 skipped), article/nav/search timing |

### Known Pre-existing Test Issues

- **Dashboard login tests**: Session cookie timing issues in Cypress — the login API works correctly but cookie persistence across Cypress sessions is unreliable
- **Dashboard system/users**: Element timeout failures — DOM rendering takes longer than the 10-15s Cypress timeout on some runs
- **Documentation editor**: 9 tests skipped (editor component loading depends on JinnTap web component timing); 6 failures from slow component initialization
- **Documentation articles/navigation**: 1 failure each — timing-dependent assertions on page load

---

## Differences from next-v2

The `next-v3` branch addresses several issues discovered in next-v2:

1. **Parser foundation**: next-v2 used `feature/new-parser` which had a shaky merge base. next-v3 uses `v2/new-parser` which merges cleanly with the other v2/ branches.

2. **QT4 regressions fixed**: 619 QT4 test regressions in the initial next-v3 build were traced to missing Java files during branch reconstruction. Fixed via commits `9c63fe8d32` and `94765b0c54` (stopgap — to be replaced with proper attribution).

3. **Notebook architecture**: next-v2's notebook routed everything through Roaster. next-v3 refactored to the controller → view.xq → content.xqm pattern used by blog and docs, making the codebase consistent and easier to review.

4. **App consistency**: All apps now share login page design, app-tabs, breadcrumbs, and search styling via exist-site-shell CSS. In next-v2, each app had its own styling.

5. **exist-services module**: Extracted from exist-core per reviewer feedback on the original PRs.

---

## Repos to Transfer to eXist-db Org

These repos on `joewiz` should be transferred to the `eXist-db` GitHub organization:

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
