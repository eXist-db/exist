# XQuery Dynamic-Invocation Coverage

## Corrects test-gaps.csv's blind spot for builtin XQuery functions

`test-gaps.csv` (from Moderne's official Prethink recipe) systematically misreports
`BasicFunction#eval()` overrides — i.e. essentially every builtin XQuery function implementation
in `org.exist.xquery.functions.*` — as having "no test coverage." This is a static-analysis
blind spot, not a real gap: these functions are dispatched by QName through the query engine at
runtime (a test invokes them via an XQuery string like `"fn:environment-variable('PATH')"`), so
there is no direct Java call edge for a call-graph analyzer to see between the test and `eval()`.

This context is produced by a custom eXist-db-specific recipe,
`org.exist.moderne.XQueryDynamicInvocationCoverage` (source and docs:
[eXist-db/exist-moderne-recipes](https://github.com/eXist-db/exist-moderne-recipes)), run as an
extra step in [`.github/workflows/prethink.yml`](../../.github/workflows/prethink.yml) alongside
the official Prethink recipe. It cross-references each `BasicFunction` subclass's QName local
name (from `new QName(...)` calls, or the `FunctionDSL.functionSignature(name, ...)` helper
pattern, including through a same-class `static final String` constant) against string literals
in Java test sources and raw text in test resources (XQSuite `.xql`/`.xqm` modules, XML
fixtures).

**Before trusting a `test-gaps.csv` row for a `BasicFunction` subclass, cross-check it against
this file first.**

## Data Tables

### XQuery dynamic-invocation coverage

**File:** [`xquery-dynamic-invocation-coverage.csv`](xquery-dynamic-invocation-coverage.csv)

One row per (implementing class, QName local name) pair.

| Column | Description |
|--------|-------------|
| Source path | The path to the Java source file implementing the XQuery function. |
| Class name | The fully qualified name of the `BasicFunction` subclass. |
| QName local name | The XQuery function's local name, as passed to `new QName(...)` in the function's signature. |
| Dynamically invoked in tests | `true` if a test source or resource contains a string matching `"<localName>("`, indicating the function is likely exercised via XQuery string dispatch even though no direct Java call exists. |
| Matching test paths | Semicolon-separated paths of the test sources/resources whose text matched this function's local name, or empty if none matched. |

## Limitations

This is a best-effort heuristic, not a proof: it matches on local name only (not the full
namespace-qualified QName), so it can be fooled by two different modules coincidentally sharing
a local name — most likely for short/generic names (e.g. `log`). In this codebase's convention
of long, hyphenated, function-specific names, that risk is low. Treat a `false` row as a real
candidate gap; treat a `true` row as reasonably trustworthy.
