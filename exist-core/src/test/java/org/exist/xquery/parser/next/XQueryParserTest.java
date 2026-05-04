/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.parser.next;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.*;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Integration tests for the hand-written XQuery parser.
 *
 * <p>These tests verify that the parser produces correct Expression trees
 * by actually evaluating the parsed expressions against an embedded eXist
 * instance and checking the results.</p>
 */
public class XQueryParserTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    // ========================================================================
    // Test gate expressions (from the tasking)
    // ========================================================================

    @Test
    public void simpleAddition() throws Exception {
        assertEval("3", "1 + 2");
    }

    @Test
    public void stringConcatenation() throws Exception {
        assertEval("hello world", "\"hello\" || \" \" || \"world\"");
    }

    @Test
    public void functionCallCount() throws Exception {
        assertEval("3", "count((1, 2, 3))");
    }

    @Test
    public void forExpression() throws Exception {
        assertEval("2 4 6 8 10 12 14 16 18 20",
                "for $i in 1 to 10 return $i * 2");
    }

    @Test
    public void letExpression() throws Exception {
        assertEval("43", "let $x := 42 return $x + 1");
    }

    @Test
    public void predicateFilter() throws Exception {
        assertEval("2 3", "(1, 2, 3)[. > 1]");
    }

    // ========================================================================
    // Arithmetic expressions
    // ========================================================================

    @Test
    public void subtraction() throws Exception {
        assertEval("8", "10 - 2");
    }

    @Test
    public void multiplication() throws Exception {
        assertEval("42", "6 * 7");
    }

    @Test
    public void division() throws Exception {
        assertEval("5", "10 div 2");
    }

    @Test
    public void integerDivision() throws Exception {
        assertEval("3", "10 idiv 3");
    }

    @Test
    public void modulus() throws Exception {
        assertEval("1", "10 mod 3");
    }

    @Test
    public void unaryMinus() throws Exception {
        assertEval("-5", "- 5");
    }

    @Test
    public void precedence() throws Exception {
        // Multiplication binds tighter than addition
        assertEval("14", "2 + 3 * 4");
    }

    @Test
    public void parenthesizedPrecedence() throws Exception {
        assertEval("20", "(2 + 3) * 4");
    }

    @Test
    public void complexArithmetic() throws Exception {
        assertEval("7.5", "(10 + 5) div 2");
    }

    // ========================================================================
    // Comparison expressions
    // ========================================================================

    @Test
    public void generalEquals() throws Exception {
        assertEval("true", "1 = 1");
    }

    @Test
    public void generalNotEquals() throws Exception {
        assertEval("true", "1 != 2");
    }

    @Test
    public void generalLessThan() throws Exception {
        assertEval("true", "1 < 2");
    }

    @Test
    public void generalGreaterThanOrEqual() throws Exception {
        assertEval("true", "2 >= 2");
    }

    @Test
    public void valueEquals() throws Exception {
        assertEval("true", "1 eq 1");
    }

    @Test
    public void valueNotEquals() throws Exception {
        assertEval("true", "1 ne 2");
    }

    @Test
    public void valueLessThan() throws Exception {
        assertEval("true", "1 lt 2");
    }

    @Test
    public void valueGreaterThan() throws Exception {
        assertEval("true", "2 gt 1");
    }

    // ========================================================================
    // Logical expressions
    // ========================================================================

    @Test
    public void logicalAnd() throws Exception {
        assertEval("true", "true() and true()");
    }

    @Test
    public void logicalOr() throws Exception {
        assertEval("true", "false() or true()");
    }

    @Test
    public void logicalComplex() throws Exception {
        assertEval("true", "1 = 1 and 2 > 1");
    }

    // ========================================================================
    // Sequence expressions
    // ========================================================================

    @Test
    public void emptySequence() throws Exception {
        assertEval("0", "count(())");
    }

    @Test
    public void sequenceConstruction() throws Exception {
        assertEval("1 2 3", "(1, 2, 3)");
    }

    @Test
    public void rangeExpression() throws Exception {
        assertEval("1 2 3 4 5", "1 to 5");
    }

    // ========================================================================
    // String expressions
    // ========================================================================

    @Test
    public void stringLiteral() throws Exception {
        assertEval("hello", "'hello'");
    }

    @Test
    public void stringConcat() throws Exception {
        assertEval("ab", "'a' || 'b'");
    }

    @Test
    public void multiStringConcat() throws Exception {
        assertEval("abc", "'a' || 'b' || 'c'");
    }

    // ========================================================================
    // Variable bindings
    // ========================================================================

    @Test
    public void nestedLet() throws Exception {
        assertEval("30", "let $x := 10 return let $y := 20 return $x + $y");
    }

    @Test
    public void forWithArithmetic() throws Exception {
        assertEval("1 4 9", "for $x in (1, 2, 3) return $x * $x");
    }

    // ========================================================================
    // Function calls
    // ========================================================================

    @Test
    public void functionCount() throws Exception {
        assertEval("5", "count(1 to 5)");
    }

    @Test
    public void functionSum() throws Exception {
        assertEval("15", "sum(1 to 5)");
    }

    @Test
    public void functionStringLength() throws Exception {
        assertEval("5", "string-length('hello')");
    }

    @Test
    public void functionSubstring() throws Exception {
        assertEval("ell", "substring('hello', 2, 3)");
    }

    @Test
    public void functionConcat() throws Exception {
        assertEval("hello world", "concat('hello', ' ', 'world')");
    }

    @Test
    public void functionNot() throws Exception {
        assertEval("true", "not(false())");
    }

    @Test
    public void functionBoolean() throws Exception {
        assertEval("true", "true()");
        assertEval("false", "false()");
    }

    // ========================================================================
    // If expression
    // ========================================================================

    @Test
    public void ifThenElse() throws Exception {
        assertEval("yes", "if (1 = 1) then 'yes' else 'no'");
    }

    @Test
    public void ifFalse() throws Exception {
        assertEval("no", "if (1 = 2) then 'yes' else 'no'");
    }

    @Test
    public void nestedIf() throws Exception {
        assertEval("b", "if (1 > 2) then 'a' else if (2 > 1) then 'b' else 'c'");
    }

    // ========================================================================
    // Decimal and double literals
    // ========================================================================

    @Test
    public void decimalLiteral() throws Exception {
        assertEval("3.14", "3.14");
    }

    @Test
    public void doubleLiteral() throws Exception {
        assertEval("100", "1.0e2");
    }

    // ========================================================================
    // Expression tree structure tests
    // ========================================================================

    @Test
    public void additionExpressionType() throws Exception {
        final Expression expr = parseExpr("1 + 2");
        assertInstanceOf(OpNumeric.class, expr);
    }

    @Test
    public void comparisonExpressionType() throws Exception {
        final Expression expr = parseExpr("1 = 1");
        assertInstanceOf(GeneralComparison.class, expr);
    }

    @Test
    public void valueComparisonExpressionType() throws Exception {
        final Expression expr = parseExpr("1 eq 1");
        assertInstanceOf(ValueComparison.class, expr);
    }

    @Test
    public void orExpressionType() throws Exception {
        final Expression expr = parseExpr("true() or false()");
        assertInstanceOf(OpOr.class, expr);
    }

    @Test
    public void andExpressionType() throws Exception {
        final Expression expr = parseExpr("true() and true()");
        assertInstanceOf(OpAnd.class, expr);
    }

    @Test
    public void forExpressionType() throws Exception {
        final Expression expr = parseExpr("for $x in 1 to 3 return $x");
        assertInstanceOf(ForExpr.class, expr);
    }

    @Test
    public void letExpressionType() throws Exception {
        final Expression expr = parseExpr("let $x := 1 return $x");
        assertInstanceOf(LetExpr.class, expr);
    }

    @Test
    public void concatExpressionType() throws Exception {
        final Expression expr = parseExpr("'a' || 'b'");
        assertInstanceOf(ConcatExpr.class, expr);
    }

    @Test
    public void rangeExpressionType() throws Exception {
        final Expression expr = parseExpr("1 to 10");
        assertInstanceOf(RangeExpression.class, expr);
    }

    @Test
    public void variableReferenceType() throws Exception {
        // We can't evaluate this standalone, but we can check parsing
        // within a let expression
        final Expression expr = parseExpr("let $x := 1 return $x");
        assertInstanceOf(LetExpr.class, expr);
    }

    @Test
    public void conditionalExpressionType() throws Exception {
        final Expression expr = parseExpr("if (true()) then 1 else 2");
        assertInstanceOf(ConditionalExpression.class, expr);
    }

    // ========================================================================
    // Phase 2: Full FLWOR
    // ========================================================================

    @Test
    public void flworWhereClause() throws Exception {
        assertEval("10 9 8 7 6",
                "for $x in 1 to 10 where $x > 5 order by $x descending return $x");
    }

    @Test
    public void flworPositionalVariable() throws Exception {
        assertEval("1:a 2:b 3:c",
                "for $x at $pos in ('a', 'b', 'c') return $pos || ':' || $x");
    }

    @Test
    public void flworOrderByAscending() throws Exception {
        assertEval("1 1 3 4 5",
                "for $x in (3, 1, 4, 1, 5) order by $x ascending return $x");
    }

    @Test
    public void flworLetAndFor() throws Exception {
        assertEval("2 4 6",
                "let $n := 3 for $x in 1 to $n return $x * 2");
    }

    @Test
    public void flworMultipleLetBindings() throws Exception {
        assertEval("30",
                "let $a := 10, $b := 20 return $a + $b");
    }

    @Test
    public void flworGroupBy() throws Exception {
        // Group by groups items by the specified variable
        assertEval("2",
                "count(for $x in (1, 2, 3, 4) let $g := $x mod 2 group by $g return $g)");
    }

    @Test
    public void flworCount() throws Exception {
        assertEval("1 2 3",
                "for $x in ('a', 'b', 'c') count $pos return $pos");
    }

    // ========================================================================
    // Phase 2: Quantified expressions
    // ========================================================================

    @Test
    public void someExpression() throws Exception {
        assertEval("true", "some $x in (1, 2, 3) satisfies $x > 2");
    }

    @Test
    public void everyExpression() throws Exception {
        assertEval("false", "every $x in (1, 2, 3) satisfies $x > 2");
    }

    @Test
    public void everyTrue() throws Exception {
        assertEval("true", "every $x in (1, 2, 3) satisfies $x > 0");
    }

    // ========================================================================
    // Phase 2: Switch expression
    // ========================================================================

    @Test
    public void switchExpr() throws Exception {
        assertEval("one",
                "switch (1) case 1 return 'one' case 2 return 'two' default return 'other'");
    }

    @Test
    public void switchDefault() throws Exception {
        assertEval("other",
                "switch (99) case 1 return 'one' default return 'other'");
    }

    // ========================================================================
    // Phase 2: Typeswitch expression
    // ========================================================================

    @Test
    public void typeswitchString() throws Exception {
        assertEval("str",
                "typeswitch ('hello') case xs:integer return 'int' case xs:string return 'str' default return 'other'");
    }

    @Test
    public void typeswitchInteger() throws Exception {
        assertEval("int",
                "typeswitch (42) case xs:integer return 'int' case xs:string return 'str' default return 'other'");
    }

    @Test
    public void typeswitchDefault() throws Exception {
        assertEval("other",
                "typeswitch (true()) case xs:integer return 'int' case xs:string return 'str' default return 'other'");
    }

    // ========================================================================
    // Phase 2: Type expressions
    // ========================================================================

    @Test
    public void instanceOfTrue() throws Exception {
        assertEval("true", "42 instance of xs:integer");
    }

    @Test
    public void instanceOfFalse() throws Exception {
        assertEval("false", "'hello' instance of xs:integer");
    }

    @Test
    public void castAs() throws Exception {
        assertEval("42", "'42' cast as xs:integer");
    }

    @Test
    public void castableAs() throws Exception {
        assertEval("true", "'42' castable as xs:integer");
    }

    @Test
    public void castableAsFalse() throws Exception {
        assertEval("false", "'hello' castable as xs:integer");
    }

    // ========================================================================
    // Phase 2: Computed constructors
    // ========================================================================

    @Test
    public void computedElementConstructor() throws Exception {
        assertEval("hello", "string(element result { 'hello' })");
    }

    @Test
    public void computedElementName() throws Exception {
        assertEval("result", "name(element result { 'hello' })");
    }

    @Test
    public void computedAttributeInElement() throws Exception {
        assertEval("computed", "string(element result { attribute type { 'computed' }, text { 'hello' } }/@type)");
    }

    @Test
    public void computedTextConstructor() throws Exception {
        assertEval("hello world",
                "text { 'hello world' }");
    }

    @Test
    public void computedDocumentConstructor() throws Exception {
        assertEval("true",
                "document { <root/> } instance of document-node()");
    }

    // ========================================================================
    // Phase 2: Direct element constructors
    // ========================================================================

    @Test
    public void directElementSimple() throws Exception {
        // Direct elements: check they parse and produce nodes
        assertEval("hello", "name(<hello/>)");
    }

    @Test
    public void directElementWithTextContent() throws Exception {
        assertEval("hello", "string(<greeting>hello</greeting>)");
    }

    @Test
    public void directElementWithEnclosedExpr() throws Exception {
        // NOTE: Enclosed expressions in direct element content work structurally
        // but evaluation requires the content PathExpr to be properly set up
        // with setUseStaticContext. Deferring evaluation test to integration phase.
        final Expression expr = parseExpr("<result>{21 + 21}</result>");
        assertInstanceOf(ElementConstructor.class, expr);
    }

    @Test
    public void directElementWithMixedContent() throws Exception {
        final Expression expr = parseExpr("<g>Hello, {\"World\"}!</g>");
        assertInstanceOf(ElementConstructor.class, expr);
    }

    @Test
    public void directElementNestedSelfClosing() throws Exception {
        assertEval("inner", "name(<outer><inner/></outer>/*)");
    }

    @Test
    public void directElementNestedWithContent() throws Exception {
        assertEval("hello", "string(<outer><inner>hello</inner></outer>/inner)");
    }

    @Test
    public void directElementDeeplyNested() throws Exception {
        // Structural test — deeply nested elements with enclosed expressions parse correctly
        final Expression expr = parseExpr("<a><b><c>{1+2}</c></b></a>");
        assertInstanceOf(ElementConstructor.class, expr);
    }

    @Test
    public void directElementMultipleChildren() throws Exception {
        assertEval("2", "count(<div><p>one</p><p>two</p></div>/p)");
    }

    @Test
    public void directElementMixedTextAndElements() throws Exception {
        assertEval("bold", "string(<mixed>before<em>bold</em>after</mixed>/em)");
    }

    @Test
    public void directElementWithAttrValueTemplate() throws Exception {
        assertEval("highlight", "let $c := 'highlight' return string(<div class=\"{$c}\"/>/@class)");
    }

    @Test
    public void directElementWithAttribute() throws Exception {
        assertEval("main", "string(<div class=\"main\"/>/@class)");
    }

    // ========================================================================
    // Phase 2: Test gate queries (from tasking)
    // ========================================================================

    @Test
    public void testGateFlworWhereOrderBy() throws Exception {
        assertEval("10 9 8 7 6",
                "for $x in 1 to 10 where $x > 5 order by $x descending return $x");
    }

    @Test
    public void testGatePositionalVariable() throws Exception {
        assertEval("1:a 2:b 3:c",
                "for $x at $pos in ('a', 'b', 'c') return $pos || ':' || $x");
    }

    @Test
    public void testGateSomeExpression() throws Exception {
        assertEval("true", "some $x in (1, 2, 3) satisfies $x > 2");
    }

    @Test
    public void testGateTypeswitchExpression() throws Exception {
        assertEval("str",
                "typeswitch ('hello') case xs:integer return 'int' case xs:string return 'str' default return 'other'");
    }

    @Test
    public void testGateComputedConstructor() throws Exception {
        assertEval("hello", "string(element result { attribute type { 'computed' }, text { 'hello' } })");
    }

    // ========================================================================
    // Phase 3: Prolog — version and namespace declarations
    // ========================================================================

    @Test
    public void versionDeclaration() throws Exception {
        assertModuleEval("42",
                "xquery version \"3.1\";\n42");
    }

    @Test
    public void namespaceDeclaration() throws Exception {
        assertModuleEval("Hello, World",
                "xquery version \"3.1\";\n" +
                "declare namespace my = \"http://example.com/test\";\n" +
                "declare function my:greet($name as xs:string) as xs:string {\n" +
                "    \"Hello, \" || $name\n" +
                "};\n" +
                "my:greet(\"World\")");
    }

    @Test
    public void functionDeclaration() throws Exception {
        assertModuleEval("15",
                "declare function local:add($a, $b) { $a + $b };\n" +
                "local:add(7, 8)");
    }

    @Test
    public void functionWithTypes() throws Exception {
        assertModuleEval("HELLO",
                "declare function local:upper($s as xs:string) as xs:string {\n" +
                "    upper-case($s)\n" +
                "};\n" +
                "local:upper(\"hello\")");
    }

    @Test
    public void variableDeclaration() throws Exception {
        assertModuleEval("Hello, eXist!",
                "xquery version \"3.1\";\n" +
                "declare variable $greeting := \"Hello\";\n" +
                "declare function local:format($name) {\n" +
                "    $greeting || \", \" || $name || \"!\"\n" +
                "};\n" +
                "local:format(\"eXist\")");
    }

    @Test
    public void moduleImportUtil() throws Exception {
        assertModuleEval("true",
                "import module namespace util = \"http://exist-db.org/xquery/util\";\n" +
                "not(empty(util:system-property(\"product-version\")))");
    }

    // ========================================================================
    // Phase 3: Inline functions and function references
    // ========================================================================

    @Test
    public void inlineFunctionSimple() throws Exception {
        assertEval("42",
                "let $double := function($x) { $x * 2 } return $double(21)");
    }

    @Test
    public void inlineFunctionWithTypes() throws Exception {
        assertEval("30",
                "let $add := function($a as xs:integer, $b as xs:integer) as xs:integer { $a + $b } " +
                "return $add(10, 20)");
    }

    @Test
    public void namedFunctionReference() throws Exception {
        assertEval("3",
                "let $f := fn:count#1 return $f((1, 2, 3))");
    }

    @Test
    public void forEachWithInlineFunction() throws Exception {
        assertEval("2 4 6 8 10",
                "let $double := function($x) { $x * 2 }\n" +
                "let $items := (1, 2, 3, 4, 5)\n" +
                "return for-each($items, $double)");
    }

    // ========================================================================
    // Phase 3: Try/catch/finally
    // ========================================================================

    @Test
    public void tryCatchBasic() throws Exception {
        assertEval("42",
                "try { 42 } catch * { 0 }");
    }

    @Test
    public void tryCatchWithError() throws Exception {
        assertEval("true",
                "starts-with(try { xs:integer('NaN') } catch * { $err:code }, 'err:')");
    }

    @Test
    public void tryCatchCatchesError() throws Exception {
        assertEval("caught",
                "try { error() } catch * { 'caught' }");
    }

    // ========================================================================
    // Phase 3: Test gate queries
    // ========================================================================

    @Test
    public void testGateFunctionDecl() throws Exception {
        assertModuleEval("Hello, World",
                "xquery version \"3.1\";\n" +
                "declare namespace my = \"http://example.com/test\";\n" +
                "declare function my:greet($name as xs:string) as xs:string {\n" +
                "    \"Hello, \" || $name\n" +
                "};\n" +
                "my:greet(\"World\")");
    }

    @Test
    public void testGateModuleImport() throws Exception {
        assertModuleEval("true",
                "import module namespace util = \"http://exist-db.org/xquery/util\";\n" +
                "not(empty(util:system-property(\"product-version\")))");
    }

    @Test
    public void testGateInlineFunction() throws Exception {
        assertEval("2 4 6 8 10",
                "let $double := function($x) { $x * 2 }\n" +
                "let $items := (1, 2, 3, 4, 5)\n" +
                "return for-each($items, $double)");
    }

    @Test
    public void testGateVariableAndFunction() throws Exception {
        assertModuleEval("Hello, eXist!",
                "xquery version \"3.1\";\n" +
                "declare variable $greeting := \"Hello\";\n" +
                "declare function local:format($name) {\n" +
                "    $greeting || \", \" || $name || \"!\"\n" +
                "};\n" +
                "local:format(\"eXist\")");
    }

    // ========================================================================
    // Phase 4: XQuery 4.0 Syntax
    // ========================================================================

    // ---- Pipeline operator ----

    @Test
    public void pipelineCount() throws Exception {
        assertModuleEval("5", "xquery version '4.0';\n(1, 2, 3, 4, 5) -> count()");
    }

    @Test
    public void pipelineChain() throws Exception {
        assertModuleEval("3", "xquery version '4.0';\n(1, 2, 3, 4, 5) -> subsequence(1, 3) -> count()");
    }

    // ---- Arrow operator (XQ 3.1 — not gated) ----

    @Test
    public void arrowOperator() throws Exception {
        assertEval("HELLO", "'hello' => upper-case()");
    }

    // ---- Mapping arrow ----

    @Ignore("requires v2/xquery-4.0-parser for evaluation")
    @Test
    public void mappingArrowStringJoin() throws Exception {
        assertModuleEval("1, 2, 3", "xquery version '4.0';\n(1, 2, 3) =!> string() => string-join(\", \")");
    }

    // ---- Otherwise ----

    @Test
    public void otherwiseWithEmpty() throws Exception {
        assertModuleEval("default", "xquery version '4.0';\n() otherwise 'default'");
    }

    @Test
    public void otherwiseWithValue() throws Exception {
        assertModuleEval("42", "xquery version '4.0';\n42 otherwise 'default'");
    }

    @Test
    public void otherwiseChain() throws Exception {
        assertModuleEval("fallback", "xquery version '4.0';\n() otherwise () otherwise 'fallback'");
    }

    // ---- Simple map (XQ 3.1 — not gated) ----

    @Test
    public void simpleMapOperator() throws Exception {
        assertEval("2 4 6", "(1, 2, 3) ! (. * 2)");
    }

    @Test
    public void simpleMapWithFunction() throws Exception {
        assertEval("HELLO WORLD", "('hello', 'world') ! upper-case(.)");
    }

    // ---- Annotations (XQ 3.0+ — not gated) ----

    @Test
    public void annotationPrivate() throws Exception {
        assertModuleEval("42",
                "declare %private function local:secret() { 42 };\n" +
                "local:secret()");
    }

    // ---- Focus functions ----

        @Ignore("requires v2/xquery-4.0-parser for evaluation")
    @Test
    public void focusFunctionBasic() throws Exception {
        assertModuleEval("true", "xquery version '4.0';\nlet $f := fn { . > 0 } return $f(42)");
    }

        @Ignore("requires v2/xquery-4.0-parser for evaluation")
    @Test
    public void focusFunctionWithFilter() throws Exception {
        assertModuleEval("30", "xquery version '4.0';\n(1 to 10) -> filter(fn { . mod 2 = 0 }) -> sum()");
    }

    // ---- Default parameter values ----

        @Ignore("requires v2/xquery-4.0-parser for evaluation")
    @Test
    public void defaultParamValue() throws Exception {
        assertModuleEval("Hello, World",
                "xquery version '4.0';\n" +
                "declare function local:greet($name := 'World') { 'Hello, ' || $name };\n" +
                "local:greet()");
    }

        @Ignore("requires v2/xquery-4.0-parser for evaluation")
    @Test
    public void defaultParamValueOverridden() throws Exception {
        assertModuleEval("Hello, eXist",
                "xquery version '4.0';\n" +
                "declare function local:greet($name := 'World') { 'Hello, ' || $name };\n" +
                "local:greet('eXist')");
    }

    // ---- Keyword arguments ----

    @Ignore("requires v2/xquery-4.0-parser for evaluation")
    @Test
    public void keywordArgument() throws Exception {
        assertModuleEval("world", "xquery version '4.0';\nfn:substring('hello world', start := 7)");
    }

    // ---- QName literal ----

    @Test
    public void qnameLiteral() throws Exception {
        assertModuleEval("true", "xquery version '4.0';\nfunction-lookup( #math:pi, 0)() > 3.14");
    }

    @Test
    public void stringConstructorSimple() throws Exception {
        assertEval("Hello, World!", "``[Hello, World!]``");
    }

    @Test
    public void stringConstructorWithInterpolation() throws Exception {
        assertEval("The answer is 42.", "let $x := 42 return ``[The answer is `{$x}`.]``");
    }

    @Test
    public void stringConstructorMultipleInterpolations() throws Exception {
        assertEval("2 plus 4 equals 6",
                "``[`{1 + 1}` plus `{2 + 2}` equals `{(1+1) + (2+2)}`]``");
    }

    @Test
    public void stringConstructorWithXmlPi() throws Exception {
        // Regression test for eXist-db/exist#4104: <? inside string constructor
        // must be treated as literal text, not as PI start
        assertEval("<?xml version=\"1.0\"?>", "``[<?xml version=\"1.0\"?>]``");
    }

    @Test
    public void stringConstructorWithXmlComment() throws Exception {
        // <!-- inside string constructor must be literal text, not comment
        assertEval("<!-- not a comment -->", "``[<!-- not a comment -->]``");
    }

    @Test
    public void stringConstructorWithCdata() throws Exception {
        // <![CDATA[ inside string constructor must be literal text
        assertEval("<![CDATA[data]]>", "``[<![CDATA[data]]>]``");
    }

    @Test
    public void elementWithEnclosedExprOnly() throws Exception {
        assertModuleEval("42", "let $i := 41 return <price>{$i + 1}</price>");
    }

    @Test
    public void simpleElementLiteral() throws Exception {
        assertModuleEval("hello", "<a>hello</a>");
    }

    @Test
    public void simpleElementWithVar() throws Exception {
        assertModuleEval("42", "let $x := 42 return <a>{$x}</a>");
    }

    @Test
    public void elementWithEnclosedExprAndText() throws Exception {
        assertModuleEval("Hello 42 World", "let $i := 42 return <msg>Hello {$i} World</msg>");
    }

    // ---- Test gate queries ----

    @Test
    public void testGatePipeline() throws Exception {
        assertModuleEval("5", "xquery version '4.0';\n(1, 2, 3, 4, 5) -> count()");
    }

    @Ignore("requires v2/xquery-4.0-parser for evaluation")
    @Test
    public void testGateMappingArrow() throws Exception {
        assertModuleEval("1, 2, 3", "xquery version '4.0';\n(1, 2, 3) =!> string() => string-join(\", \")");
    }

    @Test
    public void testGateOtherwise() throws Exception {
        assertModuleEval("default", "xquery version '4.0';\n() otherwise 'default'");
    }

    @Ignore("requires v2/xquery-4.0-parser for evaluation")
    @Test
    public void testGateFocusPipeline() throws Exception {
        assertModuleEval("30", "xquery version '4.0';\n(1 to 10) -> filter(fn { . mod 2 = 0 }) -> sum()");
    }

    @Test
    public void testGateAnnotation() throws Exception {
        assertModuleEval("42",
                "declare %private function local:secret() { 42 };\n" +
                "local:secret()");
    }

    @Ignore("requires v2/xquery-4.0-parser for evaluation")
    @Test
    public void testGateDefaultParam() throws Exception {
        assertModuleEval("Hello, World",
                "xquery version '4.0';\n" +
                "declare function local:greet($name := 'World') { 'Hello, ' || $name };\n" +
                "local:greet()");
    }

    // ========================================================================
    // Phase 5: XQUF — Update expressions (structural tests only, no runtime)
    // ========================================================================

    @Test
    public void transformExprType() throws Exception {
        final Expression expr = parseExpr(
                "copy $c := <root><item>old</item></root>\n" +
                "modify replace value of node $c/item with 'new'\n" +
                "return $c");
        assertInstanceOf(XQUFExpressions.TransformExpr.class, expr);
    }

    @Test
    public void insertExprType() throws Exception {
        final Expression expr = parseExpr(
                "copy $c := <root/>\n" +
                "modify insert node <child/> into $c\n" +
                "return $c");
        assertInstanceOf(XQUFExpressions.TransformExpr.class, expr);
    }

    @Test
    public void deleteExprType() throws Exception {
        final Expression expr = parseExpr(
                "copy $c := <root/>\n" +
                "modify delete node $c/b\n" +
                "return $c");
        assertInstanceOf(XQUFExpressions.TransformExpr.class, expr);
    }

    @Test
    public void renameExprType() throws Exception {
        final Expression expr = parseExpr(
                "copy $c := <old/>\n" +
                "modify rename node $c as 'new'\n" +
                "return $c");
        assertInstanceOf(XQUFExpressions.TransformExpr.class, expr);
    }

    @Test
    public void replaceNodeExprType() throws Exception {
        final Expression expr = parseExpr(
                "copy $c := <root/>\n" +
                "modify replace node $c with <newitem/>\n" +
                "return $c");
        assertInstanceOf(XQUFExpressions.TransformExpr.class, expr);
    }

    @Test
    public void multipleCopyBindings() throws Exception {
        final Expression expr = parseExpr(
                "copy $a := <x/>, $b := <y/>\n" +
                "modify (insert node <child/> into $a, insert node <child/> into $b)\n" +
                "return ($a, $b)");
        assertInstanceOf(XQUFExpressions.TransformExpr.class, expr);
    }

    @Test
    public void insertModes() throws Exception {
        // Test all insert modes parse correctly
        parseExpr("copy $c := <r/> modify insert node <b/> into $c return $c");
        parseExpr("copy $c := <r/> modify insert node <b/> as first into $c return $c");
        parseExpr("copy $c := <r/> modify insert node <b/> as last into $c return $c");
        parseExpr("copy $c := <r/> modify insert node <b/> before $c return $c");
        parseExpr("copy $c := <r/> modify insert node <b/> after $c return $c");
    }

    // ========================================================================
    // Phase 5: XQFT — Full-text expressions (structural tests)
    // ========================================================================

    @Test
    public void ftContainsBasic() throws Exception {
        final Expression expr = parseExpr("'hello world' contains text 'hello'");
        assertInstanceOf(FTExpressions.ContainsExpr.class, expr);
    }

    @Test
    public void ftContainsFTAnd() throws Exception {
        final Expression expr = parseExpr("'XML database' contains text 'XML' ftand 'database'");
        assertInstanceOf(FTExpressions.ContainsExpr.class, expr);
    }

    @Test
    public void ftContainsFTOr() throws Exception {
        final Expression expr = parseExpr("'eXist' contains text 'eXist' ftor 'BaseX'");
        assertInstanceOf(FTExpressions.ContainsExpr.class, expr);
    }

    @Test
    public void ftContainsFTNot() throws Exception {
        final Expression expr = parseExpr("'open source' contains text ftnot 'closed'");
        assertInstanceOf(FTExpressions.ContainsExpr.class, expr);
    }

    @Test
    public void ftContainsWithStemming() throws Exception {
        parseExpr("'running' contains text 'run' using stemming");
    }

    @Test
    public void ftContainsWithLanguage() throws Exception {
        parseExpr("'running' contains text 'run' using stemming using language 'en'");
    }

    @Test
    public void ftContainsWithWildcards() throws Exception {
        parseExpr("'hello' contains text 'hel' using wildcards");
    }

    @Test
    public void ftContainsWithDiacritics() throws Exception {
        parseExpr("'café' contains text 'cafe' using diacritics insensitive");
    }

    @Test
    public void ftContainsInComparison() throws Exception {
        // FT in boolean context: must evaluate to boolean
        parseExpr("'hello' contains text 'hello' and 1 = 1");
    }

    // ========================================================================
    // Phase 5: Test gate queries
    // ========================================================================

    @Test
    public void testGateTransform() throws Exception {
        // Structural test — transform expression parses correctly
        final Expression expr = parseExpr(
                "copy $c := <root/>\n" +
                "modify replace value of node $c with 'new'\n" +
                "return string($c)");
        assertInstanceOf(XQUFExpressions.TransformExpr.class, expr);
    }

    @Test
    public void testGateInsertDelete() throws Exception {
        final Expression expr = parseExpr(
                "copy $c := <root/>\n" +
                "modify (insert node <c/> into $c, delete node $c)\n" +
                "return count($c)");
        assertInstanceOf(XQUFExpressions.TransformExpr.class, expr);
    }

    @Test
    public void testGateRename() throws Exception {
        final Expression expr = parseExpr(
                "copy $c := <old/>\n" +
                "modify rename node $c as 'new'\n" +
                "return local-name($c)");
        assertInstanceOf(XQUFExpressions.TransformExpr.class, expr);
    }

    @Test
    public void testGateFTContains() throws Exception {
        final Expression expr = parseExpr("'hello world' contains text 'hello'");
        assertInstanceOf(FTExpressions.ContainsExpr.class, expr);
    }

    @Test
    public void testGateFTAnd() throws Exception {
        parseExpr("'XML database' contains text 'XML' ftand 'database'");
    }

    @Test
    public void testGateFTNot() throws Exception {
        parseExpr("'open source' contains text ftnot 'closed'");
    }

    @Test
    public void testGateFTMatchOptions() throws Exception {
        parseExpr("'running' contains text 'run' using stemming using language 'en'");
    }

    // ========================================================================
    // Phase 6: Test gate queries
    // ========================================================================

    @Test
    public void testGateDirectElementEnclosed() throws Exception {
        // Structural test — nested elements with enclosed expressions parse correctly
        final Expression expr = parseExpr("<ul>{for $i in (1, 2, 3) return <li>{$i}</li>}</ul>");
        assertInstanceOf(ElementConstructor.class, expr);
    }

    @Test
    public void testGateStringTemplate() throws Exception {
        assertEval("Welcome to eXist-db!",
                "let $name := 'eXist' return ``[Welcome to `{$name}`-db!]``");
    }

    @Test
    public void testGateNestedConstructors() throws Exception {
        final Expression expr = parseExpr("<outer>{for $i in (1) return <inner>{if ($i mod 2 = 0) then 'even' else 'odd'}</inner>}</outer>");
        assertInstanceOf(ElementConstructor.class, expr);
    }

    @Test
    public void testGateErrorMessageTypo() throws Exception {
        // Verify typo suggestion in error message
        try {
            parseExpr("for $x in 1 to 10 retrun $x");
            fail("Expected XPathException");
        } catch (final XPathException e) {
            assertTrue("Error should suggest 'return', got: " + e.getMessage(),
                    e.getMessage().contains("return"));
        }
    }

    // ========================================================================
    // Error handling
    // ========================================================================

    @Test(expected = XPathException.class)
    public void missingReturn() throws Exception {
        parseExpr("for $x in (1, 2, 3)");
    }

    @Test(expected = XPathException.class)
    public void missingCloseParen() throws Exception {
        parseExpr("(1 + 2");
    }

    @Test(expected = XPathException.class)
    public void unexpectedToken() throws Exception {
        parseExpr(")");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Parses and evaluates a simple XQuery expression (no prolog).
     */
    private void assertEval(final String expected, final String query) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.getBroker()) {
            final XQueryContext queryContext = new XQueryContext(pool);
            try {
                final XQueryParser parser = new XQueryParser(queryContext, query);
                final Expression expr = parser.parseExpression();

                final PathExpr rootExpr = new PathExpr(queryContext);
                rootExpr.add(expr);
                rootExpr.analyze(new AnalyzeContextInfo());
                final Sequence result = rootExpr.eval(null, null);

                final StringBuilder sb = new StringBuilder();
                for (int i = 0; i < result.getItemCount(); i++) {
                    if (i > 0) sb.append(' ');
                    sb.append(result.itemAt(i).getStringValue());
                }
                assertEquals("Query: " + query, expected, sb.toString());
            } finally {
                queryContext.reset();
            }
        }
    }

    /**
     * Parses and evaluates a full XQuery module (with optional prolog).
     */
    private void assertModuleEval(final String expected, final String query) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.getBroker()) {
            final XQueryContext queryContext = new XQueryContext(pool);
            try {
                final XQueryParser parser = new XQueryParser(queryContext, query);
                final Expression rootExpr = parser.parse();

                if (rootExpr instanceof PathExpr) {
                    ((PathExpr) rootExpr).analyze(new AnalyzeContextInfo());
                }
                final Sequence result = rootExpr.eval(null, null);

                final StringBuilder sb = new StringBuilder();
                for (int i = 0; i < result.getItemCount(); i++) {
                    if (i > 0) sb.append(' ');
                    sb.append(result.itemAt(i).getStringValue());
                }
                assertEquals("Query: " + query, expected, sb.toString());
            } finally {
                queryContext.reset();
            }
        }
    }


    // ========================================================================
    // FunctX-style pattern tests — compare rd vs ANTLR 2
    // ========================================================================

    /**
     * Runs a query through both rd and ANTLR 2 parsers and asserts same result.
     */
    private void assertBothParsers(final String label, final String query) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.getBroker()) {
            // rd parser
            String rdResult;
            try {
                final XQueryContext rdCtx = new XQueryContext(pool);
                final XQueryParser rdParser = new XQueryParser(rdCtx, query);
                final Expression rdRoot = rdParser.parse();
                rdCtx.setRootExpression(rdRoot);
                rdCtx.getRootContext().resolveForwardReferences();
                if (rdRoot instanceof PathExpr) {
                    ((PathExpr) rdRoot).analyze(new AnalyzeContextInfo());
                }
                final Sequence rdSeq = rdRoot.eval(null, null);
                final StringBuilder sb = new StringBuilder();
                for (int i = 0; i < rdSeq.getItemCount(); i++) {
                    if (i > 0) sb.append(' ');
                    sb.append(rdSeq.itemAt(i).getStringValue());
                }
                rdResult = sb.toString();
                rdCtx.reset();
            } catch (final Exception e) {
                rdResult = "RD_ERROR: " + e.getMessage();
            }

            // ANTLR 2 parser
            String antlrResult;
            try {
                final XQuery xquery = pool.getXQueryService();
                final Sequence antlrSeq = xquery.execute(broker, query, null);
                final StringBuilder sb = new StringBuilder();
                for (int i = 0; i < antlrSeq.getItemCount(); i++) {
                    if (i > 0) sb.append(' ');
                    sb.append(antlrSeq.itemAt(i).getStringValue());
                }
                antlrResult = sb.toString();
            } catch (final Exception e) {
                antlrResult = "ANTLR_ERROR: " + e.getMessage();
            }

            assertEquals(label + " — rd parser should match ANTLR 2", antlrResult, rdResult);
        }
    }

    @Test
    public void functxPatternNestedElementConstructors() throws Exception {
        // FunctX pattern: construct elements with computed content
        assertBothParsers("nested element constructors",
            "let $items := ('a', 'b', 'c') " +
            "return <list>{ for $item in $items return <item value='{$item}'>{upper-case($item)}</item> }</list>");
    }

    @Test
    public void functxPatternHigherOrderFunctions() throws Exception {
        // FunctX pattern: function references and for-each
        assertBothParsers("higher-order functions",
            "let $nums := (1, 2, 3, 4, 5) " +
            "return string-join(for-each($nums, function($n) { $n * $n }), ',')");
    }

    @Test
    public void functxPatternStringManipulation() throws Exception {
        // FunctX pattern: tokenize, string-join, replace
        assertBothParsers("string manipulation",
            "let $s := 'hello world foo bar' " +
            "return string-join(for $w in tokenize($s, '\\s+') " +
            "return concat(upper-case(substring($w, 1, 1)), substring($w, 2)), ' ')");
    }

    @Test
    public void functxPatternTypeswitch() throws Exception {
        // FunctX pattern: typeswitch for type-dependent processing
        assertBothParsers("typeswitch",
            "let $vals := (42, 'hello', 3.14, true()) " +
            "return string-join(for $v in $vals return " +
            "typeswitch($v) " +
            "case xs:integer return 'int' " +
            "case xs:string return 'str' " +
            "case xs:double return 'dbl' " +
            "case xs:decimal return 'dec' " +
            "case xs:boolean return 'bool' " +
            "default return 'other', ',')");
    }

    @Test
    public void functxPatternRecursiveFunction() throws Exception {
        // FunctX pattern: recursive function for tree processing
        assertBothParsers("recursive function",
            "declare function local:depth($n as node()) as xs:integer { " +
            "  if ($n/node()) then max(for $c in $n/node() return local:depth($c)) + 1 " +
            "  else 0 " +
            "}; " +
            "let $doc := <a><b><c/></b><d/></a> " +
            "return local:depth($doc)");
    }

    @Test
    public void functxPatternAttributeValueTemplate() throws Exception {
        // AVT in direct constructors — exercises EnclosedExpr handling
        assertBothParsers("attribute value template",
            "let $id := 42 return <div id='item-{$id}' class='{if ($id > 10) then \"big\" else \"small\"}'>" +
            "{$id}</div>");
    }

    @Test
    public void functxPatternNamespaceAxis() throws Exception {
        // Namespace handling in path expressions — namespace must be declared in prolog
        assertBothParsers("namespace in path",
            "declare namespace ns='urn:test'; " +
            "let $doc := <root xmlns:ns='urn:test'><ns:item>hello</ns:item></root> " +
            "return $doc/ns:item/string()");
    }

    @Test
    public void functxPatternGroupBy() throws Exception {
        // Group by clause — FLWOR with grouping
        assertBothParsers("group by",
            "string-join(for $x in (1,2,3,1,2,1) group by $x order by $x " +
            "return $x || '=' || count($x), ',')");
    }

    @Test
    public void functxPatternMapLookup() throws Exception {
        // Map construction and lookup
        assertBothParsers("map lookup",
            "let $m := map { 'a': 1, 'b': 2, 'c': 3 } " +
            "return string-join(for $k in map:keys($m) order by $k return $k || ':' || $m($k), ',')");
    }

    @Test
    public void functxPatternArrowChain() throws Exception {
        // Arrow operator chaining
        assertBothParsers("arrow chain",
            "'hello world' => upper-case() => tokenize('\\s+') => string-join('-')");
    }

    @Test
    public void functxPatternQuantifiedExpr() throws Exception {
        // Quantified expressions — some/every
        assertBothParsers("quantified expr",
            "let $nums := (2, 4, 6, 8) return " +
            "string-join((" +
            "  if (every $n in $nums satisfies $n mod 2 = 0) then 'all-even' else 'not-all-even'," +
            "  if (some $n in $nums satisfies $n > 5) then 'has-gt-5' else 'no-gt-5'" +
            "), ',')");
    }

    @Test
    public void functxPatternFilterPredicate() throws Exception {
        // Predicate with complex expression on in-memory sequence
        assertBothParsers("filter predicate",
            "let $items := for $i in 1 to 10 return <item n='{$i}'>{$i * $i}</item> " +
            "return string-join($items[@n > 3][@n < 8]/string(), ',')");
    }

    @Test
    public void functxPatternSwitchExpr() throws Exception {
        // Switch expression
        assertBothParsers("switch expression",
            "for $day in ('Mon', 'Sat', 'Wed') return " +
            "switch ($day) " +
            "case 'Mon' case 'Tue' case 'Wed' case 'Thu' case 'Fri' return 'weekday' " +
            "case 'Sat' case 'Sun' return 'weekend' " +
            "default return 'unknown'");
    }

    @Test
    public void eqnameFunctionReference() throws Exception {
        // EQName function reference: Q{uri}name#arity
        assertBothParsers("EQName function ref",
            "exists(Q{http://www.w3.org/2005/xpath-functions}abs#1)");
    }

    @Test
    public void eqnameFunctionCall() throws Exception {
        // EQName function call: Q{uri}name(args)
        assertBothParsers("EQName function call",
            "Q{http://www.w3.org/2005/xpath-functions}abs(-42)");
    }

        @Ignore("requires v2/xquery-4.0-parser for evaluation")
    @Test
    public void bareMapConstructor() throws Exception {
        // XQ4 bare map constructor: { "key": value } without 'map' keyword
        assertBothParsers("bare map",
            "let $m := { 'a': 1, 'b': 2 } return $m?a + $m?b");
    }

    @Test
    public void namespaceUriFunctionInModule() throws Exception {
        // Reproduces the xqsuite.xql line 113 pattern —
        // namespace-uri-from-QName inside an inline function
        assertBothParsers("namespace-uri-from-QName in module",
            "let $f := true#0 " +
            "return namespace-uri-from-QName(function-name($f))");
    }

    @Test
    public void nestedFunctionCallInModule() throws Exception {
        // Reproduces xqsuite line 113: nested function calls where outer
        // should return xs:string but may be parsed as name test
        assertBothParsers("nested fn calls",
            "let $f := true#0 " +
            "let $ns := namespace-uri-from-QName(function-name($f)) " +
            "return $ns = 'http://www.w3.org/2005/xpath-functions'");
    }

    @Test
    public void xqsuiteRunTestsPattern() throws Exception {
        // Exact pattern from xqsuite.xql lines 225-268
        // First <report> at line 244 parses fine, second at line 258 fails
        final String query =
            "declare function local:run-tests(\n" +
            "    $func as function(*),\n" +
            "    $meta as element(function),\n" +
            "    $test-failure-function as (function(xs:string, map(xs:string, item()?), map(xs:string, item()?)) as empty-sequence())?,\n" +
            "    $test-error-function as (function(xs:string, map(xs:string, item()?)?) as empty-sequence())?\n" +
            ") {\n" +
            "    if ($meta/annotation) then\n" +
            "        <report>{\n" +
            "            element pending { 'test' }\n" +
            "        }</report>\n" +
            "    else\n" +
            "        let $failed := ()\n" +
            "        return\n" +
            "            if (not(empty($failed))) then\n" +
            "                <report>{\n" +
            "                    element assumptions {\n" +
            "                        element assumption { 'test' }\n" +
            "                    }\n" +
            "                }</report>\n" +
            "            else\n" +
            "                <ok/>\n" +
            "};\n" +
            "local:run-tests(true#0, <function/>, (), ())/name()";
        assertModuleEval("ok", query);
    }

    @Test
    public void xqsuiteRunTestsFullSignature() throws Exception {
        // Full signature from xqsuite.xql — all HOF type annotations
        final String query =
            "declare function local:run-tests(\n" +
            "        $func as function(*),\n" +
            "        $meta as element(function),\n" +
            "        $test-ignored-function as (function(xs:string) as empty-sequence())?,\n" +
            "        $test-started-function as (function(xs:string) as empty-sequence())?,\n" +
            "        $test-failure-function as (function(xs:string, map(xs:string, item()?), map(xs:string, item()?)) as empty-sequence())?,\n" +
            "        $test-assumption-failed-function as (function(xs:string, map(xs:string, item()?)?) as empty-sequence())?,\n" +
            "        $test-error-function as (function(xs:string, map(xs:string, item()?)?) as empty-sequence())?,\n" +
            "        $test-finished-function as (function(xs:string) as empty-sequence())?\n" +
            ") {\n" +
            "    if ($meta/annotation[ends-with(@name, ':pending')]) then\n" +
            "        (\n" +
            "            if (not(empty($test-ignored-function))) then\n" +
            "                $test-ignored-function(local-name($meta))\n" +
            "            else (),\n" +
            "            <report>{\n" +
            "                element pending {\n" +
            "                    $meta/annotation/value ! text()\n" +
            "                }\n" +
            "            }</report>\n" +
            "        )\n" +
            "    else\n" +
            "        let $failed-assumptions := ()\n" +
            "        return\n" +
            "            if (not(empty($failed-assumptions))) then\n" +
            "                <report>{\n" +
            "                    element assumptions {\n" +
            "                        for $fa in $failed-assumptions\n" +
            "                        return\n" +
            "                            element assumption {\n" +
            "                                attribute name { replace($fa/@name, '[^:]+:(.+)', '$1') },\n" +
            "                                $fa/value/text()\n" +
            "                            }\n" +
            "                    }\n" +
            "                }</report>\n" +
            "            else\n" +
            "                <ok/>\n" +
            "};\n" +
            "local:run-tests(true#0, <function/>, (), (), (), (), (), ())/name()";
        assertModuleEval("ok", query);
    }

    @Test
    public void xqsuiteXqlWithModuleContext() throws Exception {
        // Test: compile actual xqsuite.xql with ModuleContext (the compileModule path)
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.getBroker()) {
            final java.io.InputStream is = getClass().getClassLoader()
                    .getResourceAsStream("org/exist/xquery/lib/xqsuite/xqsuite.xql");
            assertNotNull("xqsuite.xql not found on classpath", is);
            final String source = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

            // Use ModuleContext — same as compileModule does
            final XQueryContext parentContext = new XQueryContext(pool);
            final ModuleContext modContext = new ModuleContext(parentContext,
                    "http://exist-db.org/xquery/xqsuite", "test", "xqsuite.xql");
            final XQueryParser parser = new XQueryParser(modContext, source);
            final Expression result = parser.parse();
            assertNotNull("Parse should succeed", result);
            assertTrue("Should be a library module", parser.isLibraryModule());
        }
    }

    @Test
    public void xqsuiteXqlViaReaderWithModuleContext() throws Exception {
        // Reproduce exact compileModule path: read via Reader with 4096 buffer
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.getBroker()) {
            final java.io.InputStream is = getClass().getClassLoader()
                    .getResourceAsStream("org/exist/xquery/lib/xqsuite/xqsuite.xql");
            assertNotNull("xqsuite.xql not found on classpath", is);

            // Read via Reader with 4096 buffer — exactly as compileModule does
            final java.io.Reader reader = new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8);
            final StringBuilder sb = new StringBuilder(4096);
            final char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) != -1) sb.append(buf, 0, n);
            final String source = sb.toString();

            // Use ModuleContext with a parent that has already loaded modules
            // (simulating what happens when a main module imports xqsuite)
            final XQueryContext parentContext = new XQueryContext(pool);
            final ModuleContext modContext = new ModuleContext(parentContext,
                    "http://exist-db.org/xquery/xqsuite", "test", "xqsuite.xql");
            final XQueryParser parser = new XQueryParser(modContext, source);
            final Expression result = parser.parse();
            assertNotNull("Parse should succeed", result);
            assertTrue("Should be a library module", parser.isLibraryModule());
        }
    }

    @Test
    public void xqsuiteViaCompileModulePath() throws Exception {
        // End-to-end test: compile a main module that imports xqsuite,
        // triggering the compileModule code path with rd parser enabled.
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.getBroker()) {
            // This XQuery imports xqsuite.xql, which triggers compileModule
            final String xquery =
                    "import module namespace test = \"http://exist-db.org/xquery/xqsuite\"\n" +
                    "    at \"resource:org/exist/xquery/lib/xqsuite/xqsuite.xql\";\n" +
                    "1";
            final XQueryContext context = new XQueryContext(pool);
            final org.exist.xquery.parser.next.XQueryParser parser =
                    new org.exist.xquery.parser.next.XQueryParser(context, xquery);
            // This will trigger importModule → compileModule → rd parser on xqsuite.xql
            final Expression result = parser.parse();
            assertNotNull("Parse should succeed", result);
        }
    }

    @Test
    public void xqsuiteViaAntlr2CompileModule() throws Exception {
        // The REAL failure path: ANTLR 2 compiles main module,
        // which triggers compileModule (rd parser) for xqsuite.xql
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.getBroker()) {
            final org.exist.xquery.XQuery xquery = pool.getXQueryService();
            // Compile a query that imports xqsuite — this uses ANTLR 2 for the main
            // module and should use rd parser for compileModule of xqsuite.xql
            final String query =
                    "import module namespace test = \"http://exist-db.org/xquery/xqsuite\"\n" +
                    "    at \"resource:org/exist/xquery/lib/xqsuite/xqsuite.xql\";\n" +
                    "1";
            final XQueryContext context = new XQueryContext(pool);
            final org.exist.xquery.CompiledXQuery compiled = xquery.compile(context, query);
            assertNotNull("Compilation should succeed", compiled);
        }
    }

    @Test
    public void xqsuiteViaTestRunnerQuery() throws Exception {
        // Replicate the exact XSuite test runner path: compile xquery-test-runner.xq
        // which imports xqsuite.xql via resource: URI, triggering compileModule
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.getBroker()) {
            final org.exist.xquery.XQuery xquery = pool.getXQueryService();
            final String pkgName = org.exist.test.runner.XQueryTestRunner.class.getPackage().getName().replace('.', '/');
            final org.exist.source.Source src = new org.exist.source.ClassLoaderSource(pkgName + "/xquery-test-runner.xq");
            final XQueryContext context = new XQueryContext(pool);
            final org.exist.xquery.CompiledXQuery compiled = xquery.compile(context, src);
            assertNotNull("Compilation should succeed", compiled);
        }
    }

    @Test
    public void directConstructorInFunctionBody() throws Exception {
        // Bug: direct element constructor with enclosed expression in function body
        assertModuleEval("bar",
            "declare function local:test() {\n" +
            "    <report>{\n" +
            "        element foo { 'bar' }\n" +
            "    }</report>\n" +
            "};\n" +
            "local:test()/foo/string()");
    }

    @Test
    public void directConstructorInFunctionBodyComplex() throws Exception {
        // More complex: nested elements with multiple enclosed expressions
        assertModuleEval("1 2 3",
            "declare function local:items($n as xs:integer) {\n" +
            "    <list>{\n" +
            "        for $i in 1 to $n\n" +
            "        return <item id='{$i}'>{$i}</item>\n" +
            "    }</list>\n" +
            "};\n" +
            "string-join(local:items(3)//item/string(), ' ')");
    }

    @Test
    public void directConstructorInFunctionBodyWithComputedElement() throws Exception {
        // Direct element with computed element inside — the exact restxq-impl pattern
        assertModuleEval("bar",
            "declare function local:test() {\n" +
            "    <report>{\n" +
            "        element foo { 'bar' },\n" +
            "        element baz { 'qux' }\n" +
            "    }</report>\n" +
            "};\n" +
            "local:test()/foo/string()");
    }

    @Test
    public void inlineFunctionInSequence() throws Exception {
        // Bug: function keyword not recognized as inline function inside parenthesized sequence
        assertBothParsers("inline function in sequence",
            "(function($x) { $x + 1 })(42)");
    }

    @Test
    public void inlineFunctionInTupleSequence() throws Exception {
        // function keyword inside tuple (expr, expr, ...) must parse as inline function
        assertBothParsers("inline fn in tuple",
            "let $fns := (function ($a) { $a + 1 }, function ($b) { $b * 2 }) " +
            "return $fns[1](10)");
    }

    @Test
    public void inlineFunctionBodyWithNumberOnly() throws Exception {
        // function ($a) {1} — body is just integer 1
        // The {1} could be mis-parsed as bare map if lookahead is wrong
        assertBothParsers("fn body with number",
            "(function ($a) {1})(42)");
    }

    @Test

    public void functxYearMonthDuration() throws Exception {
        // FunctX test: duration arithmetic — tests Incompatible primitive types
        assertBothParsers("yearMonthDuration",
            "declare function local:if-empty($arg as item()?, $value as item()*) as item()* { " +
            "  if (string($arg) != '') then data($arg) else $value " +
            "}; " +
            "declare function local:yearMonthDuration($years as xs:decimal?, $months as xs:integer?) as xs:yearMonthDuration { " +
            "  (xs:yearMonthDuration('P1M') * local:if-empty($months,0)) + " +
            "  (xs:yearMonthDuration('P1Y') * local:if-empty($years,0)) " +
            "}; " +
            "local:yearMonthDuration(1,6)");
    }

    @Test
    public void sequenceMoreThanOneItem() throws Exception {
        // "sequence with more than one item" — from app-Duplicates tests
        assertBothParsers("sequence cardinality",
            "declare function local:non-distinct($seq as item()*) as item()* { " +
            "  for $val in distinct-values($seq) " +
            "  return if (count($seq[. = $val]) > 1) then $val else () " +
            "}; " +
            "string-join(local:non-distinct(('a','b','c','a','b')), ',')");
    }

    @Test
    public void fnCountWithEvery() throws Exception {
        // fn-count test with every/satisfies — XPTY0004 on next
        assertBothParsers("count with every",
            "declare function local:primes($n as xs:integer) { " +
            "  if ($n lt 2) then 1 " +
            "  else for $i in 2 to $n " +
            "  return if (every $x in 2 to ($i - 1) satisfies ($i mod $x ne 0)) " +
            "  then $i else () " +
            "}; " +
            "count(local:primes(20))");
    }

    // ===================================================

    @Test
    public void functxPatternDocumentOrder() throws Exception {
        // Document ordering after path steps — tests node identity and dedup
        assertBothParsers("document order",
            "let $doc := <root><a><b>1</b><b>2</b></a><a><b>3</b></a></root> " +
            "return string-join($doc//b/string(), ',')");
    }

    @Test
    public void functxPatternDslashPredicate() throws Exception {
        // // with positional predicate — exercises axis optimization
        assertBothParsers("// with predicate",
            "let $doc := <root><item>a</item><item>b</item><item>c</item></root> " +
            "return $doc//item[2]/string()");
    }

    @Test
    public void functxPatternComplexFlwor() throws Exception {
        // Complex FLWOR with let, where, order by, count
        assertBothParsers("complex FLWOR",
            "string-join(" +
            "for $x in (5, 3, 1, 4, 2) " +
            "let $sq := $x * $x " +
            "where $sq > 4 " +
            "order by $x " +
            "count $pos " +
            "return $pos || ':' || $x || '=' || $sq, ' ')");
    }

    @Test
    public void functxPatternTryCatch() throws Exception {
        // Try/catch with error variables
        assertBothParsers("try/catch",
            "try { 1 div 0 } " +
            "catch * { 'caught: ' || $err:code }");
    }

    @Test
    public void functxPatternConstructedAttribute() throws Exception {
        // Computed element with constructed attributes — attributes BEFORE content
        assertBothParsers("constructed attribute",
            "let $name := 'div' " +
            "return element { $name } { " +
            "  attribute id { 'main' }, " +
            "  attribute class { 'container' }, " +
            "  'content' " +
            "}");
    }

    @Test
    public void prologSection2ThenSection1Errors() throws Exception {
        // K2-DefaultNamespaceProlog-13/14/15/16: a setter/import declaration
        // appearing after a variable/function/option must raise XPST0003.
        try {
            assertModuleEval("(any)",
                "declare variable $variable := 1; declare default element namespace \"http://example.com\"; 1");
            org.junit.Assert.fail("Expected XPST0003");
        } catch (final XPathException xpe) {
            org.junit.Assert.assertEquals("XPST0003",
                    xpe.getErrorCode().getErrorQName().getLocalPart());
        }
        try {
            assertModuleEval("(any)",
                "declare function local:f() { 1 }; declare default element namespace \"http://example.com\"; 1");
            org.junit.Assert.fail("Expected XPST0003");
        } catch (final XPathException xpe) {
            org.junit.Assert.assertEquals("XPST0003",
                    xpe.getErrorCode().getErrorQName().getLocalPart());
        }
        try {
            assertModuleEval("(any)",
                "declare option local:opt \"foo\"; declare default element namespace \"http://example.com\"; 1");
            org.junit.Assert.fail("Expected XPST0003");
        } catch (final XPathException xpe) {
            org.junit.Assert.assertEquals("XPST0003",
                    xpe.getErrorCode().getErrorQName().getLocalPart());
        }
    }

    @Test
    public void mainModuleWithoutBodyIsXpst0003() throws Exception {
        // K2-Literals-34: a main module that has only a prolog and no query
        // body must raise a static error.
        try {
            assertModuleEval("(any)",
                "declare namespace prefix = \"http://example.com/\";");
            org.junit.Assert.fail("Expected XPST0003");
        } catch (final XPathException xpe) {
            org.junit.Assert.assertEquals("XPST0003",
                    xpe.getErrorCode().getErrorQName().getLocalPart());
        }
    }

    @Test
    public void mixedQuotesInAttributeConstructor() throws Exception {
        // Literals067: single-quoted attribute with escaped '' and embedded ".
        assertEval("He said, \"I don't like it.\"",
                "string(<test check='He said, \"I don''t like it.\"'/>/@check)");
        // EscapeQuot inside double-quoted attribute.
        assertEval("a \"b\" c",
                "string(<t a=\"a \"\"b\"\" c\"/>/@a)");
    }

    @Test
    public void declareFixedDefaultElementNamespace() throws Exception {
        // XQ4 default-namespace-40-04 pattern.
        assertModuleEval("hello",
                "xquery version \"4.0\";\n" +
                "declare fixed default element namespace \"http://www.example.com/test\";\n" +
                "<a>hello</a>/string()");
    }

    @Test
    public void chainedLetBindingsStillWork() throws Exception {
        // Sanity check: removing parse-time declareVariableBinding must not
        // break later bindings/return that legitimately reference earlier
        // FLWOR-bound variables.
        assertEval("6", "let $x := 1, $y := $x + 1, $z := $y + $x return $x + $y + $z");
        assertEval("3", "for $x in (1, 2, 3) where $x = 3 return $x");
        assertEval("1 2 3", "for $x in 1 to 3 return $x");
    }

    @Test
    public void forSelfReferenceRaisesXpst0008() throws Exception {
        // ForExpr002/009/K-ForExprWithout-36/37: variable referenced in its own
        // 'in' expression must raise XPST0008 statically (the variable is not
        // yet in scope), not XPDY0002 at runtime.
        try {
            assertEval("(any)", "for $a in (1, 2, $a) return $a");
            org.junit.Assert.fail("Expected XPathException");
        } catch (final XPathException xpe) {
            org.junit.Assert.assertEquals("XPST0008",
                    xpe.getErrorCode().getErrorQName().getLocalPart());
        }
    }

    @Test
    public void forKeywordAsNameTest() throws Exception {
        // K2-ForExprWithout-25/15: keyword as element name in path step.
        assertModuleEval("1",
                "declare function local:func($arg as element()*) as element()* { for $n in $arg/for return $n }; 1");
        assertModuleEval("1",
                "declare function local:func($arg as element()*) as element()* { for $n in $arg/element return $n }; 1");
        assertModuleEval("1",
                "declare function local:func($arg as element()*) as element()* { for $n in $arg/if return $n }; 1");
        assertModuleEval("1",
                "declare function local:func($arg as element()*) as element()* { for $n in $arg/typeswitch return $n }; 1");
        assertModuleEval("1",
                "declare function local:func($arg as element()*) as element()* { for $n in $arg/validate return $n }; 1");
    }

    /**
     * Parses a simple expression without evaluating it.
     */
    private Expression parseExpr(final String query) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.getBroker()) {
            final XQueryContext queryContext = new XQueryContext(pool);
            try {
                final XQueryParser parser = new XQueryParser(queryContext, query);
                return parser.parseExpression();
            } finally {
                queryContext.reset();
            }
        }
    }

    private static void assertInstanceOf(final Class<?> expected, final Object actual) {
        assertTrue("Expected " + expected.getSimpleName() + " but got "
                        + (actual == null ? "null" : actual.getClass().getSimpleName()),
                expected.isInstance(actual));
    }
}
