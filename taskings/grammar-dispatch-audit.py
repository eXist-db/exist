#!/usr/bin/env python3
"""
Grammar Dispatch Audit: Cross-references EBNF productions against
the rd parser's parse* method calls to find Expr/ExprSingle mismatches.

Usage:
    python3 taskings/grammar-dispatch-audit.py

Reads:
    ~/workspace/eXide/grammars/XQuery-40-Family-XQUFEL.ebnf
    ~/workspace/exist/.claude/worktrees/feature-new-parser/exist-core/src/main/java/org/exist/xquery/parser/next/XQueryParser.java

Produces: a report of all productions, their EBNF references, parser calls,
and any Expr/ExprSingle mismatches.
"""

import re
import sys
from pathlib import Path
from collections import defaultdict

EBNF_PATH = Path.home() / "workspace/eXide/grammars/XQuery-40-Family-XQUFEL.ebnf"
PARSER_PATH = Path.home() / "workspace/exist/.claude/worktrees/feature-new-parser/exist-core/src/main/java/org/exist/xquery/parser/next/XQueryParser.java"


def parse_ebnf(path):
    """Extract productions and their references from EBNF."""
    content = path.read_text()

    # Parse productions: Name ::= RHS
    # Handle multi-line productions (continuation lines start with whitespace or |)
    productions = {}
    current_name = None
    current_rhs = []

    for line in content.split('\n'):
        # Skip comments
        stripped = line.strip()
        if stripped.startswith('/*') or stripped.startswith('*') or stripped.startswith('//'):
            continue

        # New production: starts with letter at column 0
        m = re.match(r'^([A-Z]\w*)\s*$', line)
        if m:
            # Name on its own line, ::= on next
            if current_name and current_rhs:
                productions[current_name] = ' '.join(current_rhs)
            current_name = m.group(1)
            current_rhs = []
            continue

        m = re.match(r'^([A-Z]\w*)\s+::=\s*(.*)', line)
        if m:
            if current_name and current_rhs:
                productions[current_name] = ' '.join(current_rhs)
            current_name = m.group(1)
            current_rhs = [m.group(2)]
            continue

        # Continuation: ::= on its own or continuation of RHS
        if current_name:
            m = re.match(r'^\s+::=\s*(.*)', line)
            if m:
                current_rhs.append(m.group(1))
                continue
            if line.startswith(' ') or line.startswith('\t'):
                current_rhs.append(stripped)
                continue
            elif stripped == '':
                continue
            else:
                # End of current production
                if current_rhs:
                    productions[current_name] = ' '.join(current_rhs)
                current_name = None
                current_rhs = []

    if current_name and current_rhs:
        productions[current_name] = ' '.join(current_rhs)

    # Extract references from each production's RHS
    production_refs = {}
    for name, rhs in productions.items():
        # Find all PascalCase references (production names)
        refs = re.findall(r'\b([A-Z][A-Za-z0-9]+)\b', rhs)
        # Filter out string literals and common non-productions
        refs = [r for r in refs if r not in ('CDATA', 'EOF', 'NOT', 'AND', 'OR', 'IN')]
        production_refs[name] = refs

    return productions, production_refs


def parse_parser(path):
    """Extract parse methods and their calls from the rd parser."""
    content = path.read_text()
    lines = content.split('\n')

    # Find all method definitions and their bodies
    methods = {}
    current_method = None
    current_body = []
    brace_depth = 0

    for i, line in enumerate(lines):
        # Match method declarations
        m = re.match(r'\s+(?:private|public|protected)?\s*(?:static\s+)?(?:\w+\s+)?(parse\w+)\s*\(', line)
        if m and '{' not in line[:line.index('parse')]:
            m = re.match(r'\s+.*\b(parse\w+)\s*\(', line)
        if m:
            method_name = m.group(1)
            if current_method and current_body:
                methods[current_method] = '\n'.join(current_body)
            current_method = method_name
            current_body = [line]
            brace_depth = line.count('{') - line.count('}')
            continue

        if current_method:
            current_body.append(line)
            brace_depth += line.count('{') - line.count('}')
            if brace_depth <= 0 and len(current_body) > 2:
                methods[current_method] = '\n'.join(current_body)
                current_method = None
                current_body = []
                brace_depth = 0

    if current_method and current_body:
        methods[current_method] = '\n'.join(current_body)

    # For each method, extract calls to other parse* methods
    method_calls = {}
    for name, body in methods.items():
        # Find all parseXxx() calls
        calls = re.findall(r'\b(parse\w+)\s*\(', body)
        # Remove self-references
        calls = [c for c in calls if c != name]
        method_calls[name] = calls

    return methods, method_calls


def find_expr_mismatches(productions, production_refs, methods, method_calls):
    """Find cases where EBNF says ExprSingle but parser uses parseExpr or vice versa."""

    # Key EBNF rules that reference Expr vs ExprSingle
    # ExprSingle is used in: return clauses, function args, predicates, etc.
    # Expr (comma-separated) is used in: function body, parenthesized expressions, element content

    mismatches = []

    # Map EBNF productions to likely parser methods
    ebnf_to_parser = {
        'ReturnClause': 'parseFLWOR',
        'LetClause': 'parseLetBinding',
        'ForClause': 'parseForBinding',
        'WhereClause': 'parseWhereClause',
        'OrderSpec': 'parseOrderByClause',
        'QuantifiedExpr': 'parseQuantified',
        'IfExpr': 'parseIfExpr',
        'SwitchExpr': 'parseSwitchExpr',
        'TypeswitchExpr': 'parseTypeswitchExpr',
        'TryCatchExpr': 'parseTryCatchExpr',
        'FunctionBody': 'parseFunctionDecl',  # also parseInlineFunction
        'EnclosedExpr': 'scanEnclosedExpr',
        'Argument': 'parseFunctionArg',
        'CompElemConstructor': 'parseComputedElementConstructor',
        'CompAttrConstructor': 'parseComputedAttributeConstructor',
        'CompTextConstructor': 'parseComputedTextConstructor',
        'CompCommentConstructor': 'parseComputedCommentConstructor',
        'CompDocConstructor': 'parseComputedDocumentConstructor',
        'CompPIConstructor': 'parseComputedPIConstructor',
        'CompNamespaceConstructor': 'parseComputedNamespaceConstructor',
        'Predicate': 'parsePredicate',
        'ParenthesizedExpr': 'parseParenthesized',
        'CastExpr': 'parseCastExpr',
        'CastableExpr': 'parseCastableExpr',
        'TreatAsExpr': 'parseTreatExpr',
        'InstanceofExpr': 'parseInstanceOfExpr',
        'InsertExpr': 'parseInsertExpr',
        'DeleteExpr': 'parseDeleteExpr',
        'ReplaceExpr': 'parseReplaceExpr',
        'RenameExpr': 'parseRenameExpr',
        'TransformExpr': 'parseTransformExpr',
        'WindowClause': 'parseWindowClause',
        'WhileClause': 'parseWhileClause',
        'CountClause': 'parseCountClause',
        'GroupByClause': 'parseGroupByClause',
    }

    # Productions that should use ExprSingle (not Expr) per EBNF
    expr_single_productions = set()
    expr_productions = set()
    for name, rhs in productions.items():
        if 'ExprSingle' in rhs:
            expr_single_productions.add(name)
        if re.search(r'\bExpr\b', rhs) and 'ExprSingle' not in rhs:
            expr_productions.add(name)

    # Check each mapped production
    for ebnf_name, parser_method in ebnf_to_parser.items():
        if parser_method not in method_calls:
            continue

        calls = method_calls[parser_method]
        method_body = methods.get(parser_method, '')

        # Check: does the EBNF say ExprSingle but parser calls parseExpr?
        if ebnf_name in expr_single_productions:
            expr_calls = [c for c in calls if c == 'parseExpr']
            if expr_calls:
                # Verify it's not inside a sub-block (like function body)
                # Simple heuristic: check if parseExpr appears in context
                mismatches.append({
                    'production': ebnf_name,
                    'parser_method': parser_method,
                    'issue': 'EBNF says ExprSingle but parser calls parseExpr()',
                    'severity': 'BUG',
                    'detail': f'Found {len(expr_calls)} parseExpr() calls where ExprSingle expected'
                })

        # Check: does the EBNF say Expr but parser calls parseExprSingle?
        if ebnf_name in expr_productions:
            if 'parseExprSingle' in calls and 'parseExpr' not in calls:
                mismatches.append({
                    'production': ebnf_name,
                    'parser_method': parser_method,
                    'issue': 'EBNF says Expr but parser only calls parseExprSingle()',
                    'severity': 'BUG',
                    'detail': 'Too restrictive — should allow comma-separated sequences'
                })

    # Specific checks: (EBNF_name, parser_method, expected_level, expected_call)
    # These verify that each parse* method uses the correct Expr vs ExprSingle
    specific_checks = [
        # === ExprSingle contexts (MUST NOT use parseExpr) ===
        # ReturnClause ::= "return" ExprSingle
        ('ReturnClause', 'parseFLWOR', 'ExprSingle', 'parseExprSingle'),
        # Argument ::= ExprSingle | ArgumentPlaceholder
        ('Argument', 'parseFunctionArg', 'ExprSingle', 'parseExprSingle'),
        # WhereClause ::= "where" ExprSingle
        ('WhereClause', 'parseWhereClause', 'ExprSingle', 'parseExprSingle'),
        # OrderSpec key ::= ExprSingle
        ('OrderSpec', 'parseOrderByClause', 'ExprSingle', 'parseExprSingle'),
        # QuantifierBinding ::= "$" VarName "in" ExprSingle
        ('QuantifierBinding', 'parseQuantified', 'ExprSingle', 'parseExprSingle'),
        # WindowCondition when ::= ExprSingle
        ('WindowCondition', 'parseWindowCondition', 'ExprSingle', 'parseExprSingle'),
        # WhileClause ::= "while" ExprSingle
        ('WhileClause', 'parseWhileClause', 'ExprSingle', 'parseExprSingle'),
        # MapConstructorEntry ::= ExprSingle ":" ExprSingle
        ('MapConstructorEntry', 'parseMapEntry', 'ExprSingle', 'parseExprSingle'),
        # ForBinding in ::= ExprSingle
        ('ForBinding', 'parseForBinding', 'ExprSingle', 'parseExprSingle'),
        # LetBinding ::= ExprSingle
        ('LetBinding', 'parseLetBinding', 'ExprSingle', 'parseExprSingle'),

        # === Expr contexts (MAY use parseExpr) ===
        # Predicate ::= "[" Expr "]"
        ('Predicate', 'parsePredicate', 'Expr', 'parseExpr'),
        # ParenthesizedExpr ::= "(" Expr? ")"
        ('ParenthesizedExpr', 'parseParenthesized', 'Expr', 'parseExpr'),
        # FunctionBody ::= EnclosedExpr ::= "{" Expr "}"
        ('FunctionBody', 'parseFunctionDecl', 'Expr', 'parseExpr'),
        # EnclosedExpr inside XML ::= "{" Expr "}"
        ('EnclosedExpr', 'scanEnclosedExpr', 'Expr', 'parseExpr'),
        # CompDocConstructor ::= "document" "{" Expr "}"
        ('CompDocConstructor', 'parseComputedDocumentConstructor', 'Expr', 'parseExpr'),
        # TryClause ::= "try" "{" Expr "}"
        ('TryClause', 'parseTryCatchExpr', 'Expr', 'parseExpr'),
    ]

    for ebnf_name, parser_method, expected_level, expected_call in specific_checks:
        if parser_method not in methods:
            continue
        body = methods[parser_method]

        # Find actual calls in the method body
        actual_expr_calls = list(re.finditer(r'\b(parseExpr|parseExprSingle)\s*\(', body))

        for match in actual_expr_calls:
            actual_call = match.group(1)
            if actual_call != expected_call:
                # Get line number context
                pos = match.start()
                line_num = body[:pos].count('\n') + 1
                context_line = body.split('\n')[line_num - 1].strip()[:80]

                mismatches.append({
                    'production': ebnf_name,
                    'parser_method': parser_method,
                    'issue': f'EBNF says {expected_level} but parser calls {actual_call}()',
                    'severity': 'BUG' if expected_level == 'ExprSingle' and actual_call == 'parseExpr' else 'WARN',
                    'detail': f'Line ~{line_num}: {context_line}'
                })

    return mismatches


def main():
    print("=" * 80)
    print("Grammar Dispatch Audit: EBNF vs rd Parser")
    print("=" * 80)
    print()

    # Parse EBNF
    if not EBNF_PATH.exists():
        print(f"ERROR: EBNF file not found: {EBNF_PATH}")
        sys.exit(1)
    productions, production_refs = parse_ebnf(EBNF_PATH)
    print(f"EBNF: {len(productions)} productions parsed")

    # Parse rd parser
    if not PARSER_PATH.exists():
        print(f"ERROR: Parser file not found: {PARSER_PATH}")
        sys.exit(1)
    methods, method_calls = parse_parser(PARSER_PATH)
    print(f"Parser: {len(methods)} parse methods found")
    print()

    # Find mismatches
    mismatches = find_expr_mismatches(productions, production_refs, methods, method_calls)

    # Report
    bugs = [m for m in mismatches if m['severity'] == 'BUG']
    warns = [m for m in mismatches if m['severity'] == 'WARN']

    print(f"{'='*80}")
    print(f"MISMATCHES FOUND: {len(bugs)} bugs, {len(warns)} warnings")
    print(f"{'='*80}")
    print()

    if bugs:
        print("=== BUGS (Expr/ExprSingle mismatch) ===")
        for m in bugs:
            print(f"  [{m['severity']}] {m['production']} ({m['parser_method']})")
            print(f"        {m['issue']}")
            print(f"        {m['detail']}")
            print()

    if warns:
        print("=== WARNINGS ===")
        for m in warns:
            print(f"  [{m['severity']}] {m['production']} ({m['parser_method']})")
            print(f"        {m['issue']}")
            print(f"        {m['detail']}")
            print()

    # Summary
    print(f"{'='*80}")
    print(f"SUMMARY")
    print(f"{'='*80}")
    print(f"  EBNF productions: {len(productions)}")
    print(f"  Parser methods: {len(methods)}")
    print(f"  Bugs found: {len(bugs)}")
    print(f"  Warnings: {len(warns)}")

    if bugs:
        print()
        print("  FIX NEEDED:")
        for m in bugs:
            print(f"    - {m['parser_method']}: change parseExpr() to parseExprSingle()")
        sys.exit(1)
    else:
        print()
        print("  ALL CLEAR: No Expr/ExprSingle mismatches found!")
        sys.exit(0)


if __name__ == '__main__':
    main()
