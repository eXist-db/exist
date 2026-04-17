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
package org.exist.http.restxq;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;

/**
 * Tests for RESTXQ path routing, path variables, regex paths, encoded URIs,
 * path precedence, and validation errors.
 *
 * <p>Ported from BaseX's RestXqPathTest and expanded to one-assertion-per-test
 * for clearer failure diagnosis.</p>
 */
public class RestXqPathTest extends RestXqTestBase {

    // === Basic routing ===

    @Test
    public void rootPathEmpty() throws Exception {
        get("root", "declare %rest:path('') function m:f() { 'root' };", "");
    }

    @Test
    public void rootPathSlash() throws Exception {
        get("root", "declare %rest:path('/') function m:f() { 'root' };", "");
    }

    @Test
    public void simplePath() throws Exception {
        get("ok", "declare %rest:path('/test') function m:f() { 'ok' };", "test");
    }

    @Test
    public void pathNotFound() throws Exception {
        get(404, "declare %rest:path('/a') function m:f() { 1 };", "b");
    }

    @Test
    public void explicitGet() throws Exception {
        get("root", "declare %rest:GET %rest:path('') function m:f() { 'root' };", "");
    }

    @Test
    public void pathNotFoundOnEmpty() throws Exception {
        get(404, "declare %rest:path('') function m:f() { 1 };", "X");
    }

    @Test
    public void pathNotFoundReverse() throws Exception {
        get(404, "declare %rest:path('a') function m:f() { 1 };", "");
    }

    // === Path variables ===

    @Test
    public void singleVariable() throws Exception {
        get("x",
            "declare %rest:path('/var/{$x}') function m:f($x) { $x };",
            "var/x");
    }

    @Test
    public void singleVariableOther() throws Exception {
        get("y",
            "declare %rest:path('/var/{$x}') function m:f($x) { $x };",
            "var/y");
    }

    @Test
    public void multipleVariables() throws Exception {
        get("6",
            "declare %rest:path('{$x}/{$y}') function m:f($x as xs:integer, $y as xs:integer) { $x * $y };",
            "2/3");
    }

    @Test
    public void multipleVariablesTypeError() throws Exception {
        get(500,
            "declare %rest:path('{$x}/{$y}') function m:f($x as xs:integer, $y as xs:integer) { $x * $y };",
            "2/x");
    }

    @Test
    public void typedVariable() throws Exception {
        get("2",
            "declare %rest:path('/{$x}') function m:f($x as xs:int) { $x };",
            "2");
    }

    @Test
    public void typedVariableError() throws Exception {
        get(500,
            "declare %rest:path('/{$x}') function m:f($x as xs:int) { $x };",
            "StRiNg");
    }

    @Test
    public void rootVariable() throws Exception {
        get("x",
            "declare %rest:path('{$x}/a/b/c') function m:f($x) { $x };",
            "x/a/b/c");
    }

    @Test
    public void rootVariableNoMatch() throws Exception {
        get(404,
            "declare %rest:path('{$x}/a/b/c') function m:f($x) { $x };",
            "x/a/b/d");
    }

    @Test
    public void namespacedVariable() throws Exception {
        get("z",
            "declare %rest:path('{$m:x}') function m:f($m:x) { $m:x };",
            "z");
    }

    @Test
    public void defaultElementNamespace() throws Exception {
        get("z",
            "declare default element namespace 'X';\n" +
            "declare %rest:path('{$x}') function m:f($x) { $x };",
            "z");
    }

    // === Regex paths (BaseX extension) ===

    @Category(BaseXExtension.class)
    @Test
    public void regexDotPlus() throws Exception {
        get("a/b/c",
            "declare %rest:path('p/{$x=.+}') function m:f($x) { $x };",
            "p/a/b/c");
    }

    @Category(BaseXExtension.class)
    @Test
    public void regexDigits() throws Exception {
        get("123",
            "declare %rest:path('p/{$x=[0-9]+}') function m:f($x) { $x };",
            "p/123");
    }

    @Category(BaseXExtension.class)
    @Test
    public void regexNoMatch() throws Exception {
        get(404,
            "declare %rest:path('p/{$x=[0-9]+}') function m:f($x) { $x };",
            "p/123a");
    }

    @Category(BaseXExtension.class)
    @Test
    public void regexMultiCapture() throws Exception {
        get("3ab12",
            "declare %rest:path('{$a=\\d+}{$b=\\w+}{$c=\\d}') " +
                "function m:f($a, $b, $c) { $c || $b || $a };",
            "12ab3");
    }

    @Category(BaseXExtension.class)
    @Test
    public void regexMultiCaptureNoMatch() throws Exception {
        get(404,
            "declare %rest:path('{$a=\\d+}{$b=\\w+}{$c=\\d}') " +
                "function m:f($a, $b, $c) { $c || $b || $a };",
            "12ab3x");
    }

    @Category(BaseXExtension.class)
    @Test
    public void regexPrecedenceSpecific() throws Exception {
        get("2",
            "declare %rest:path('{$p=.+}') function m:f1($p) { 1 };\n" +
            "declare %rest:path('{$p=.+}/x') function m:f2($p) { 2 };",
            "1/x");
    }

    @Category(BaseXExtension.class)
    @Test
    public void regexPrecedenceGeneral() throws Exception {
        get("1",
            "declare %rest:path('{$p=.+}') function m:f1($p) { 1 };\n" +
            "declare %rest:path('{$p=.+}/x') function m:f2($p) { 2 };",
            "1");
    }

    // === Encoded URIs ===

    @Test
    public void encodedBraceLower() throws Exception {
        get("1", "declare %rest:path('%7b') function m:f() { 1 };", "%7b");
    }

    @Test
    public void encodedBraceUpper() throws Exception {
        get("1", "declare %rest:path('%7b') function m:f() { 1 };", "%7B");
    }

    @Test
    public void encodedBraceReverse() throws Exception {
        get("1", "declare %rest:path('%7B') function m:f() { 1 };", "%7b");
    }

    @Test
    public void encodedPipe() throws Exception {
        get("1", "declare %rest:path('%7C') function m:f() { 1 };", "%7C");
    }

    @Test
    public void encodedSpaceAsPlus() throws Exception {
        get("1", "declare %rest:path('+') function m:f() { 1 };", "%20");
    }

    @Test
    public void encodedSpaceLiteral() throws Exception {
        get("1", "declare %rest:path(' ') function m:f() { 1 };", "%20");
    }

    @Test
    public void encodedPlusSign() throws Exception {
        get("1", "declare %rest:path('%2b') function m:f() { 1 };", "+");
    }

    @Test
    public void encodedSpacePercent() throws Exception {
        get("1", "declare %rest:path('%20') function m:f() { 1 };", "%20");
    }

    @Test
    public void invalidEncodingPercent() throws Exception {
        get(500, "declare %rest:path('%F') function m:f() { 1 };", "");
    }

    @Test
    public void invalidEncodingPercentAlone() throws Exception {
        get(500, "declare %rest:path('%') function m:f() { 1 };", "");
    }

    // === Path precedence ===

    @Test
    public void specificBeatsGeneral() throws Exception {
        register(
            "declare %rest:path('/a/b/c') function m:f1() { 'specific' };\n" +
            "declare %rest:path('/{$x}') function m:f2($x) { 'general' };");
        assertEquals("specific", doGet("a/b/c").trim());
    }

    // === Validation errors ===

    @Test
    public void noPathAnnotation() throws Exception {
        get(500, "declare %rest:GET function m:f() { () };", "");
    }

    @Test
    public void noPathArgument() throws Exception {
        get(500, "declare %rest:path function m:f() { () };", "");
    }

    @Test
    public void emptyPathArgument() throws Exception {
        get(500, "declare %rest:path() function m:f() { () };", "");
    }

    @Test
    public void twoPathArguments() throws Exception {
        get(500, "declare %rest:path('a', 'b') function m:f() { () };", "a");
    }

    @Test
    public void duplicatePaths() throws Exception {
        get(500, "declare %rest:path('a') %rest:path('b') function m:f() { () };", "a");
    }

    @Test
    public void duplicateMethod() throws Exception {
        get(500, "declare %rest:GET %rest:GET %rest:path('') function m:f() { 'root' };", "");
    }

    @Test
    public void invalidVariableNoBraces() throws Exception {
        get(500, "declare %rest:path('{a}') function m:f() { () };", "a");
    }

    @Test
    public void invalidVariableSpaces() throws Exception {
        get(500, "declare %rest:path('{ $a }') function m:f() { () };", "a");
    }

    @Test
    public void invalidVariableName() throws Exception {
        get(500, "declare %rest:path('{$x::x}') function m:f() { () };", "a");
    }

    @Test
    public void invalidVariableNameSpace() throws Exception {
        get(500, "declare %rest:path('{$x x}') function m:f() { () };", "a");
    }

    @Test
    public void missingFunctionArgument() throws Exception {
        get(500, "declare %rest:path('{$x}') function m:f() { () };", "a");
    }

    @Test
    public void duplicateVariable() throws Exception {
        get(500, "declare %rest:path('{$x}/{$x}') function m:f($x) { () };", "a");
    }

    @Test
    public void missingTemplateVariable() throws Exception {
        get(500, "declare %rest:path('') function m:f($x) { () };", "");
    }

    @Test
    public void variableMustBeAtomic() throws Exception {
        get(500, "declare %rest:path('{$x}') function m:f($x as node()) { $x };", "1");
    }

    @Test
    public void unknownFunction() throws Exception {
        get(500, "declare %rest:path('') function m:f() { m:foo() };", "");
    }

    @Test
    public void invalidAnnotation() throws Exception {
        get(500, "declare %rest:path('') %rest:xyz function m:f() { 'x' };", "");
    }
}
