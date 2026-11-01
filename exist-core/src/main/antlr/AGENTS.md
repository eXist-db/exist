# Parser (ANTLR 2)

eXist uses **ANTLR 2.7.7** for the XQuery parser. The grammar files are:

- `exist-core/src/main/antlr/org/exist/xquery/parser/XQuery.g` — lexer + parser (~3500 lines)
- `exist-core/src/main/antlr/org/exist/xquery/parser/XQueryTree.g` — tree walker
- `exist-core/src/main/antlr/org/exist/xquery/parser/DeclScanner.g` — declaration pre-scanner

## Key ANTLR 2 gotchas

- **testLiterals trap**: NEVER use `"true"` or `"false"` as keyword strings in grammar rules — use a semantic predicate instead. ANTLR 2's `testLiterals` mechanism will intercept them.
- **Syntactic predicates**: `(A B) => ...` cache tokens during lookahead but do NOT rollback lexer state mutations. Flag changes (like `parseStringLiterals`) during token production persist even if the predicate fails.
- **Grammar sections**: Keep rules in labeled sections per feature area to prevent merge conflicts:
  ```
  // === W3C XQuery Update Facility 3.0 ===
  // === Full Text ===
  // === XQuery 4.0 Parser Extensions ===
  ```
- **Expression chain**: The expression precedence chain is: `comparisonExpr` → `ftContainsExpr` → `otherwiseExpr` → `stringConcatExpr` → `rangeExpr`. Do not reorder.

## Generated sources

ANTLR generates `XQueryParser.java`, `XQueryLexer.java`, `XQueryTreeParser.java` into `exist-core/target/generated-sources/antlr/`. These are ~20K lines each and should not be manually edited.
