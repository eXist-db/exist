## Summary

Implements XQuery 4.0 parser and runtime support for eXist-db, covering the majority of the QT4CG specification draft syntax, 50+ new standard functions, and enhanced existing functions. This brings eXist-db in line with the evolving XQuery 4.0 standard alongside BaseX and Saxon.

This PR is part of the [XQuery 4.0 master plan](https://github.com/eXist-db/exist/issues/XXXX) and covers:
- **Parser**: All major XQ4 syntax additions via ANTLR 2 grammar extensions
- **Functions**: 50+ new `fn:` functions and enhancements to existing functions
- **Map/Array modules**: Ordered maps, 6 new map functions, 4 new array functions
- **Error codes**: Spec-compliant error code alignment across type checking
- **Parameter names**: W3C catalog alignment for keyword argument support

## What Changed

### Grammar changes (XQuery.g + XQueryTree.g)

| Feature | Spec Reference | Status |
|---------|---------------|--------|
| Focus functions: `fn { expr }` | PR2200 | Complete |
| Keyword arguments: `name := expr` | PR197 | Complete |
| Default parameter values: `$param := default` | PR197 | Complete |
| String templates: `` `Hello {$name}` `` | PR254 | Complete |
| Pipeline operator: `expr => func` | PR510 | Complete |
| Mapping arrow: `expr =!> func` | PR510 | Complete |
| `for member` clause | PR1172 | Complete |
| `otherwise` expression | PR795 | Complete |
| Braced if: `if (cond) { expr }` | — | Complete |
| `while` clause in FLWOR | — | Complete |
| `try`/`catch`/`finally` | — | Complete |
| Ternary conditional: `?? !!` | — | Complete |
| QName literals: `#name` | — | Complete |
| Hex/binary integer literals | — | Complete |
| Numeric underscores: `1_000_000` | — | Complete |
| Array/map filter: `?[predicate]` | — | Complete |
| Choice/union item types | — | Complete |
| Enumeration types: `enum("a","b")` | — | Complete |
| Method call operator: `=?>` | — | Complete |
| Let destructuring | — | Complete |
| `fn(...)` type shorthand | — | Complete |
| `declare context value` | — | Complete |
| `xquery version "4.0"` | — | Complete |
| Braced switch/typeswitch | — | Complete |
| Unicode `×` multiplication sign | — | Complete |
| `reservedKeywords` sub-rule refactoring | — | Complete |

### Expression classes (30 files)

New expression classes for XQ4 runtime semantics:

| Class | Purpose |
|-------|---------|
| `FocusFunction` | `fn { expr }` with implicit context item binding |
| `KeywordArgumentExpression` | `name := expr` argument passing |
| `MappingArrowOperator` | `=!>` with sequence mapping semantics |
| `MethodCallOperator` | `=?>` method dispatch |
| `PipelineExpression` | `=>` left-to-right function chaining |
| `OtherwiseExpression` | Fallback when left side is empty |
| `WhileClause` | FLWOR `while (condition)` iteration |
| `ForMemberExpr` / `ForKeyValueExpr` | Array/map iteration |
| `LetDestructureExpr` | `let ($a, $b) := sequence` |
| `FilterExprAM` | `?[predicate]` array/map filtering |
| `ChoiceCastExpression` / `ChoiceCastableExpression` | Union type casting |
| `EnumCastExpression` | `enum("a","b")` validation |
| `FunctionParameterFunctionSequenceType` | HOF parameter type with arity checking |

Modified classes include `Function` (keyword arg resolution), `FunctionSignature` (default params), `UserDefinedFunction` (default param binding), `TryCatchExpression` (finally clause), `SwitchExpression` (XQ4 version gating), `StringConstructor` (atomization fixes), and `XQueryContext` (version 4.0 recognition).

### XQ4 functions (50+ new, 18 enhanced)

**New function implementations:**

| Category | Functions |
|----------|----------|
| Sequence | `fn:characters`, `fn:foot`, `fn:trunk`, `fn:items-at`, `fn:slice`, `fn:replicate`, `fn:insert-separator` |
| Comparison | `fn:all-equal`, `fn:all-different`, `fn:duplicate-values`, `fn:atomic-equal`, `fn:highest`, `fn:lowest` |
| Higher-order | `fn:every`, `fn:some`, `fn:partition`, `fn:scan-left`, `fn:scan-right`, `fn:op`, `fn:partial-apply` |
| Subsequence | `fn:contains-subsequence`, `fn:starts-with-subsequence`, `fn:ends-with-subsequence`, `fn:subsequence-where` |
| URI/String | `fn:parse-uri`, `fn:build-uri`, `fn:decode-from-uri`, `fn:char`, `fn:characters` |
| Type/Reflection | `fn:type-of`, `fn:atomic-type-annotation`, `fn:node-type-annotation`, `fn:function-annotations`, `fn:function-identity`, `fn:is-NaN`, `fn:identity`, `fn:void` |
| Date/Time | `fn:civil-timezone`, `fn:seconds`, `fn:unix-dateTime` |
| Hash | `fn:hash` (MD5, SHA-1, SHA-256, SHA-384, SHA-512, BLAKE3) |
| CSV | `fn:csv`, `fn:parse-csv`, `fn:csv-to-arrays` |
| Names | `fn:parse-QName`, `fn:expanded-QName`, `fn:parse-integer` |
| Navigation | `fn:transitive-closure`, `fn:element-to-map`, `fn:distinct-ordered-nodes`, `fn:siblings`, `fn:in-scope-namespaces` |
| Misc | `fn:sort-by`, `fn:divide-decimals`, `fn:message`, `fn:deep-equal` (options map) |

**Enhanced existing functions:**

| Function | Enhancement |
|----------|-------------|
| `fn:compare` | XQ4 `anyAtomicType`, numeric total order, duration/datetime ordering |
| `fn:min`/`fn:max` | Comparison function parameter |
| `fn:deep-equal` | Options map (debug, flags, collation) |
| `fn:matches`/`fn:tokenize` | XQ4 regex flags (`!` for XPath, unnamed capture groups) |
| `fn:replace` | `c` flag, empty match handling, function replacement parameter |
| `fn:round` | 3-argument `$mode` overload (half-up, half-down, etc.) |
| Collations | Fixed supplementary codepoint comparison; ASCII case-insensitive collator |

### Map module enhancements (6 files)

- **Ordered maps**: Maps preserve insertion order (backed by `LinkedHashMap`)
- **New functions**: `map:keys-where`, `map:filter`, `map:build`, `map:pair`, `map:of-pairs`, `map:values-of`, `map:index`
- **Cross-type numeric key equality**: `map { 1: "a" }?1.0` works correctly

### Array module enhancements

- `array:index-where`, `array:slice`, `array:sort-by`, `array:sort-with`

### Error code alignment (26 files)

Aligned error codes with the W3C specification across type casting, cardinality checks, and treat-as expressions:

| Component | Change | Impact |
|-----------|--------|--------|
| `convertTo()` in 20 atomic types | `FORG0001` → `XPTY0004` for type-incompatible casts | +510 tests |
| `DoubleValue` | NaN/INF → integer/decimal: `FOCA0002` | +48 tests |
| `DynamicCardinalityCheck` | Generic `ERROR` → `XPTY0004` (or `XPDY0050` for treat-as) | +5 tests |
| `DynamicTypeCheck` | `FOCH0002` → `XPTY0004` (overridable for treat-as) | +1 test |
| `TreatAsExpression` | Passes `XPDY0050` to type/cardinality checks | +17 tests |

### Parameter name alignment (59 files)

Renamed function parameter names across 59 `fn:` module files to match the W3C XQuery 4.0 Functions and Operators catalog. This enables keyword argument support (`name := value`) with the standard parameter names. Primary renames: `$arg` → `$value`, `$arg` → `$input`, etc.

### Tests

- **`fnXQuery40.xql`**: Comprehensive XQSuite test file covering all XQ4 features (2491 lines)
- Updated `fnHigherOrderFunctions.xql`, `replace.xqm`, `fnLanguage.xqm`, `InspectModuleTest.java`
- New `deep-equal-options-test.xq` for XQ4 deep-equal options map

## Spec References

- [QT4CG XQuery 4.0 Draft](https://qt4cg.org/specifications/xquery-40/)
- [QT4CG XPath/XQuery Functions 4.0](https://qt4cg.org/specifications/xpath-functions-40/)
- Key proposals: PR197 (keyword args), PR254 (string templates), PR510 (pipeline/mapping arrow), PR795 (otherwise), PR1172 (for member), PR2200 (fn keyword/focus functions)

## XQTS Results

QT4 XQTS test sets, run against the consolidated branch (2026-03-14):

| Test Set | Tests | Passed | Failed | Errors | Pass Rate |
|----------|-------|--------|--------|--------|-----------|
| misc-BuiltInKeywords | 297 | 215 | 79 | 3 | 72.4% |
| prod-ArrowExpr | 70 | 67 | 3 | 0 | 95.7% |
| prod-CastExpr | 2803 | 2613 | 187 | 3 | 93.2% |
| prod-CountClause | 13 | 12 | 1 | 0 | 92.3% |
| prod-DynamicFunctionCall | 88 | 33 | 54 | 1 | 37.5% |
| prod-FLWORExpr | 21 | 21 | 0 | 0 | 100.0% |
| prod-FunctionDecl | 228 | 175 | 53 | 0 | 76.8% |
| prod-GroupByClause | 40 | 36 | 2 | 2 | 90.0% |
| prod-IfExpr | 43 | 42 | 1 | 0 | 97.7% |
| prod-InlineFunctionExpr | 46 | 37 | 7 | 2 | 80.4% |
| prod-InstanceofExpr | 319 | 310 | 9 | 0 | 97.2% |
| prod-Lookup | 131 | 116 | 13 | 2 | 88.5% |
| prod-NamedFunctionRef | 564 | 520 | 42 | 2 | 92.2% |
| prod-OrderByClause | 206 | 204 | 1 | 1 | 99.0% |
| prod-QuantifiedExpr | 215 | 204 | 11 | 0 | 94.9% |
| prod-StringTemplate | 53 | 52 | 1 | 0 | 98.1% |
| prod-SwitchExpr | 38 | 38 | 0 | 0 | 100.0% |
| prod-TreatExpr | 73 | 72 | 1 | 0 | 98.6% |
| prod-TryCatchExpr | 193 | 163 | 30 | 0 | 84.5% |
| prod-TypeswitchExpr | 74 | 72 | 2 | 0 | 97.3% |
| prod-UnaryLookup | 37 | 31 | 4 | 2 | 83.8% |
| prod-WhereClause | 85 | 78 | 7 | 0 | 91.8% |
| prod-WindowClause | 158 | 125 | 33 | 0 | 79.1% |
| **Total** | **5795** | **5236** | **541** | **18** | **90.4%** |

**Test sets at 100%:** prod-FLWORExpr, prod-SwitchExpr

**XQSuite:** 1316 tests, 0 failures, 9 skipped

### Failure analysis

The remaining failures are primarily:

| Category | Count | Notes |
|----------|-------|-------|
| Record types / type infrastructure | ~120 | Requires XQ4 record type system (not yet implemented) |
| Unimplemented functions | ~80 | Functions not yet available in eXist-db |
| Error code mismatches | ~80 | Generic `ERROR` vs specific codes in validation routines |
| XQ4 no-namespace functions | ~40 | PR2200 allows overriding `fn:` namespace (architectural change) |
| Parser type syntax | ~30 | Record/union types in function signatures |
| Pre-existing issues | ~20 | Failures also present on develop |
| Window clause | ~30 | XQ4 window clause extensions |
| Other | ~30 | Various edge cases |

## Limitations

The following XQuery 4.0 features are **not** implemented in this PR:

- **Record types** (`record(name as xs:string, age as xs:integer)`) — requires new type infrastructure
- **Union types in type declarations** — parser accepts but runtime support is limited
- **JNode / JSON node types** — requires new data model layer
- **`declare context value`** — parsed as synonym but not fully enforced
- **Method calls (`=?>`)** — parsed but limited to simple dispatch
- **No-namespace function overriding** (PR2200) — `fn:` namespace functions cannot yet be overridden by unprefixed declarations
- **Version gating** — XQ4 features are available regardless of `xquery version` declaration; no XQ3.1-only mode
- **XML Schema revalidation** — not applicable to eXist-db

## Test Plan

- [x] XQSuite: 1316 tests, 0 failures
- [x] QT4 XQTS: 5236/5795 (90.4%) across 23 parser-related test sets
- [ ] Full `mvn test` on CI
- [ ] XQTS comparison against develop baseline
- [ ] Review by @duncdrum

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
