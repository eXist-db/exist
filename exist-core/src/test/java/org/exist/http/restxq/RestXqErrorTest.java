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

import static org.junit.Assert.assertNotNull;

/**
 * Tests for RESTXQ error handling: error annotations, wildcards, precedence,
 * error-param bindings, and validation.
 *
 * <p>Ported from BaseX's RestXqErrorTest and expanded.</p>
 */
@Category(BaseXExtension.class)
public class RestXqErrorTest extends RestXqTestBase {

    // === Error catching ===

    @Test
    public void catchAll() throws Exception {
        get("F",
            "declare %rest:path('') function m:a() { error() };\n" +
            "declare %rest:error('*') function m:b() { 'F' };",
            "");
    }

    @Test
    public void catchQName() throws Exception {
        get("F",
            "declare %rest:path('') function m:a() { error(xs:QName('x')) };\n" +
            "declare %rest:error('x') function m:b() { 'F' };",
            "");
    }

    @Test
    public void catchLocalWild() throws Exception {
        get("F",
            "declare %rest:path('') function m:a() { 1 + <a/> };\n" +
            "declare %rest:error('*:FORG0001') function m:b() { 'F' };",
            "");
    }

    @Test
    public void catchPrefixWild() throws Exception {
        get("F",
            "declare %rest:path('') function m:a() { 1 + <a/> };\n" +
            "declare %rest:error('err:*') function m:b() { 'F' };",
            "");
    }

    @Test
    public void catchExactQName() throws Exception {
        get("F",
            "declare %rest:path('') function m:a() { 1 + <a/> };\n" +
            "declare %rest:error('err:FORG0001') function m:b() { 'F' };",
            "");
    }

    @Test
    public void catchURIQualified() throws Exception {
        get("F",
            "declare %rest:path('') function m:a() { 1 + <a/> };\n" +
            "declare %rest:error('Q{http://www.w3.org/2005/xqt-errors}FORG0001') " +
                "function m:b() { 'F' };",
            "");
    }

    @Test
    public void catchURIWild() throws Exception {
        get("F",
            "declare %rest:path('') function m:a() { 1 + <a/> };\n" +
            "declare %rest:error('Q{http://www.w3.org/2005/xqt-errors}*') " +
                "function m:b() { 'F' };",
            "");
    }

    // === Error precedence ===

    @Test
    public void precedenceQNameFirst() throws Exception {
        get("1",
            "declare %rest:path('') function m:a() { 1 + <a/> };\n" +
            "declare %rest:error('err:FORG0001') function m:b() { '1' };\n" +
            "declare %rest:error('err:*') function m:d() { '2' };\n" +
            "declare %rest:error('*:FORG0001') function m:c() { '3' };\n" +
            "declare %rest:error('*') function m:e() { '4' };",
            "");
    }

    @Test
    public void precedencePrefixWild() throws Exception {
        get("2",
            "declare %rest:path('') function m:a() { 1 + <a/> };\n" +
            "declare %rest:error('err:*') function m:d() { '2' };\n" +
            "declare %rest:error('*:FORG0001') function m:c() { '3' };\n" +
            "declare %rest:error('*') function m:e() { '4' };",
            "");
    }

    @Test
    public void precedenceNamespaceWild() throws Exception {
        get("3",
            "declare %rest:path('') function m:a() { 1 + <a/> };\n" +
            "declare %rest:error('err:*') function m:d() { '3' };\n" +
            "declare %rest:error('*') function m:e() { '4' };",
            "");
    }

    // === Error parameters ===

    @Test
    public void errorParamCode() throws Exception {
        get("#err:FOER0000",
            "declare %rest:path('') function m:a() { error() };\n" +
            "declare %rest:error('*') %rest:error-param('code', '{$x}') " +
                "function m:b($x) { $x };",
            "");
    }

    @Test
    public void errorParamCodeQName() throws Exception {
        get("#x",
            "declare %rest:path('') function m:a() { error(xs:QName('x')) };\n" +
            "declare %rest:error('*') %rest:error-param('code', '{$x}') " +
                "function m:b($x) { $x };",
            "");
    }

    @Test
    public void errorParamDescription() throws Exception {
        get("!!!",
            "declare %rest:path('') function m:a() { error(xs:QName('x'), '!!!') };\n" +
            "declare %rest:error('*') %rest:error-param('description', '{$x}') " +
                "function m:b($x) { $x };",
            "");
    }

    @Test
    public void errorParamValue() throws Exception {
        register(
            "declare %rest:path('') function m:a() { error(xs:QName('x'), 'desc', <val/>) };\n" +
            "declare %rest:error('*') %rest:error-param('value', '{$x}') " +
                "function m:b($x) { name($x) };");
        final String result = doGet("");
        assertNotNull(result);
    }

    @Test
    public void errorParamModule() throws Exception {
        register(
            "declare %rest:path('') function m:a() { error() };\n" +
            "declare %rest:error('*') %rest:error-param('module', '{$x}') " +
                "function m:b($x) { $x };");
        final String result = doGet("");
        assertNotNull(result);
    }

    @Test
    public void errorParamLineNumber() throws Exception {
        register(
            "declare %rest:path('') function m:a() { error() };\n" +
            "declare %rest:error('*') %rest:error-param('line-number', '{$x}') " +
                "function m:b($x) { $x };");
        final String result = doGet("");
        assertNotNull(result);
    }

    // === Multiple error codes ===

    @Test
    public void multipleErrorCodes() throws Exception {
        get("F",
            "declare %rest:path('') function m:a() { 1 + <a/> };\n" +
            "declare %rest:error('err:FORG0001', 'err:FORG0002') function m:b() { 'F' };",
            "");
    }

    @Test
    public void multiplePrefixWild() throws Exception {
        get("F",
            "declare %rest:path('') function m:a() { 1 + <a/> };\n" +
            "declare %rest:error('err:*', 'unit:*') function m:b() { 'F' };",
            "");
    }

    @Test
    public void multipleSeparateAnnotations() throws Exception {
        get("F",
            "declare %rest:path('') function m:a() { 1 + <a/> };\n" +
            "declare %rest:error('unit:*') %rest:error('err:*') function m:b() { 'F' };",
            "");
    }

    // === Error validation ===

    @Test
    public void noMatchingHandler() throws Exception {
        get(500,
            "declare %rest:path('') function m:a() { error(xs:QName('x')) };\n" +
            "declare %rest:error('y') function m:b() { 'F' };",
            "");
    }

    @Test
    public void unknownPrefix() throws Exception {
        get(500,
            "declare %rest:path('') function m:a() { () };\n" +
            "declare %rest:error('unknown:*') function m:b() { 'F' };",
            "");
    }

    @Test
    public void invalidLocalName() throws Exception {
        get(500,
            "declare %rest:path('') function m:a() { () };\n" +
            "declare %rest:error('*:In Valid') function m:b() { 'F' };",
            "");
    }

    @Test
    public void invalidName() throws Exception {
        get(500,
            "declare %rest:path('') function m:a() { () };\n" +
            "declare %rest:error('In Valid') function m:b() { 'F' };",
            "");
    }

    @Test
    public void invalidURIQualified() throws Exception {
        get(500,
            "declare %rest:path('') function m:a() { () };\n" +
            "declare %rest:error('Q{http://www.w3.org/2005/xqt-errors}') function m:b() { 'F' };",
            "");
    }

    @Test
    public void duplicateHandlersStar() throws Exception {
        get(500,
            "declare %rest:path('') function m:a() { () };\n" +
            "declare %rest:error('*') function m:b() { 'F' };\n" +
            "declare %rest:error('*') function m:b() { 'F' };",
            "");
    }

    @Test
    public void duplicateHandlersLocalWild() throws Exception {
        get(500,
            "declare %rest:path('') function m:a() { () };\n" +
            "declare %rest:error('*:FORG0001') function m:b() { 'F' };\n" +
            "declare %rest:error('*:FORG0001') function m:b() { 'F' };",
            "");
    }

    @Test
    public void duplicateHandlersPrefixWild() throws Exception {
        get(500,
            "declare %rest:path('') function m:a() { () };\n" +
            "declare %rest:error('err:*') function m:b() { 'F' };\n" +
            "declare %rest:error('err:*') function m:b() { 'F' };",
            "");
    }

    @Test
    public void duplicateHandlersExact() throws Exception {
        get(500,
            "declare %rest:path('') function m:a() { () };\n" +
            "declare %rest:error('error') function m:b() { 'F' };\n" +
            "declare %rest:error('error') function m:b() { 'F' };",
            "");
    }

    @Test
    public void duplicateHandlersQNameVsURI() throws Exception {
        get(500,
            "declare %rest:path('') function m:a() { () };\n" +
            "declare %rest:error('err:FORG0001') function m:b() { 'F' };\n" +
            "declare %rest:error('Q{http://www.w3.org/2005/xqt-errors}FORG0001') " +
                "function m:b() { 'F' };",
            "");
    }

    @Test
    public void duplicateErrorSameAnnotation() throws Exception {
        get(500,
            "declare %rest:path('') function m:a() { () };\n" +
            "declare %rest:error('*') %rest:error('*') function m:b() { 'F' };",
            "");
    }

    @Test
    public void duplicateCodeInListStar() throws Exception {
        get(500,
            "declare %rest:path('') function m:a() { () };\n" +
            "declare %rest:error('*', '*') function m:b() { 'F' };",
            "");
    }

    @Test
    public void duplicateCodeInListName() throws Exception {
        get(500,
            "declare %rest:path('') function m:a() { () };\n" +
            "declare %rest:error('x', 'x') function m:b() { 'F' };",
            "");
    }

    @Test
    public void duplicateCodeInListPrefixed() throws Exception {
        get(500,
            "declare %rest:path('') function m:a() { () };\n" +
            "declare %rest:error('err:x', 'err:x') function m:b() { 'F' };",
            "");
    }

    @Test
    public void duplicateCodeInListLocalWild() throws Exception {
        get(500,
            "declare %rest:path('') function m:a() { () };\n" +
            "declare %rest:error('*:x', '*:x') function m:b() { 'F' };",
            "");
    }

    @Test
    public void duplicateCodeInListPrefixWild() throws Exception {
        get(500,
            "declare %rest:path('') function m:a() { () };\n" +
            "declare %rest:error('x:*', 'x:*') function m:b() { 'F' };",
            "");
    }
}
