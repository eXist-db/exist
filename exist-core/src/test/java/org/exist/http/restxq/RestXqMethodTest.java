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
 * Tests for RESTXQ HTTP methods: GET, POST, PUT, DELETE, HEAD, OPTIONS,
 * PATCH, and custom methods.
 *
 * <p>Ported from BaseX's RestXqMethodTest and expanded.</p>
 */
public class RestXqMethodTest extends RestXqTestBase {

    // === Standard methods ===

    @Test
    public void getMethod() throws Exception {
        get("x", "declare %rest:GET %rest:path('') function m:f() { 'x' };", "");
    }

    @Test
    public void postTextBody() throws Exception {
        post("12",
            "declare %rest:POST('{$x}') %rest:path('') function m:f($x) { $x };",
            "", "12", "text/plain");
    }

    @Test
    public void postXmlBody() throws Exception {
        post("<x>A</x>",
            "declare %rest:POST('{$x}') %rest:path('') function m:f($x) { $x };",
            "", "<x>A</x>", "application/xml");
    }

    @Test
    public void postJsonBody() throws Exception {
        register("declare %rest:POST('{$x}') %rest:path('') function m:f($x) { $x };");
        final int status = doPostStatus("", "{ \"A\": \"B\" }", "application/json");
        assertEquals(200, status);
    }

    @Test
    public void postBinaryBody() throws Exception {
        post("AAA",
            "declare %rest:POST('{$x}') %rest:path('') function m:f($x) { $x };",
            "", "AAA", "application/octet-stream");
    }

    @Test
    public void putBody() throws Exception {
        register("declare %rest:PUT('{$x}') %rest:path('') function m:f($x) { $x };");
        final int status = doPutStatus("", "data", "text/plain");
        assertEquals(200, status);
    }

    @Test
    public void deleteMethod() throws Exception {
        register("declare %rest:DELETE %rest:path('') function m:f() { 'deleted' };");
        final int status = doDeleteStatus("");
        assertEquals(200, status);
    }

    @Category(BaseXExtension.class)
    @Test
    public void patchMethod() throws Exception {
        register("declare %rest:PATCH('{$x}') %rest:path('') function m:f($x) { $x };");
        final int status = doPatchStatus("", "patch-data", "text/plain");
        assertEquals(200, status);
    }

    // === Custom methods (BaseX extension) ===

    @Category(BaseXExtension.class)
    @Test
    public void customMethodGet() throws Exception {
        get("x", "declare %rest:method('GET') %rest:path('') function m:f() { 'x' };", "");
    }

    @Category(BaseXExtension.class)
    @Test
    public void customMethodCaseInsensitive() throws Exception {
        get("x", "declare %rest:method('get') %rest:path('') function m:f() { 'x' };", "");
    }

    @Category(BaseXExtension.class)
    @Test
    public void customMethodRetrieve() throws Exception {
        register("declare %rest:method('RETRIEVE') %rest:path('') function m:f() { 'x' };");
        final int status = doCustomMethodStatus("RETRIEVE", "");
        assertEquals(200, status);
    }

    @Category(BaseXExtension.class)
    @Test
    public void customMethodRetrieveWithBody() throws Exception {
        register("declare %rest:method('RETRIEVE', '{$b}') %rest:path('') function m:f($b) { $b };");
        final String result = doCustomMethodBody("RETRIEVE", "", "12", "text/plain");
        assertEquals("12", result.trim());
    }

    @Category(BaseXExtension.class)
    @Test
    public void customMethodDuplicate() throws Exception {
        register("declare %rest:method('RETRIEVE') %rest:method('RETRIEVE') %rest:path('') function m:f() { 'x' };");
        final int status = doCustomMethodStatus("RETRIEVE", "");
        assertEquals(500, status);
    }

    @Category(BaseXExtension.class)
    @Test
    public void methodConflict() throws Exception {
        get(500,
            "declare %rest:method('GET') %rest:GET %rest:path('') function m:f() { 'x' };",
            "");
    }

    @Category(BaseXExtension.class)
    @Test
    public void methodConflictCaseInsensitive() throws Exception {
        get(500,
            "declare %rest:method('get') %rest:method('GET') %rest:path('') function m:f() { 'x' };",
            "");
    }

    @Category(BaseXExtension.class)
    @Test
    public void customMethodPostWithBody() throws Exception {
        post("12",
            "declare %rest:method('POST', '{$b}') %rest:path('') function m:f($b) { $b };",
            "", "12", "text/plain");
    }

    @Category(BaseXExtension.class)
    @Test
    public void customMethodGetWithBodyError() throws Exception {
        get(500,
            "declare %rest:method('GET', '{$b}') %rest:path('') function m:f($b) { $b };",
            "");
    }

    // === HEAD ===

    @Category(BaseXExtension.class)
    @Test
    public void headExplicitResponse() throws Exception {
        register("declare %rest:HEAD %rest:path('') function m:f() { <rest:response/> };");
        assertEquals(200, doHeadStatus(""));
    }

    @Category(BaseXExtension.class)
    @Test
    public void headExplicitTyped() throws Exception {
        register("declare %rest:HEAD %rest:path('') function m:f() as element(rest:response) { <rest:response/> };");
        assertEquals(200, doHeadStatus(""));
    }

    @Category(BaseXExtension.class)
    @Test
    public void headWrongReturnEmpty() throws Exception {
        register("declare %rest:HEAD %rest:path('') function m:f() {};");
        assertEquals(500, doHeadStatus(""));
    }

    @Category(BaseXExtension.class)
    @Test
    public void headWrongReturnElement() throws Exception {
        register("declare %rest:HEAD %rest:path('') function m:f() { <response/> };");
        assertEquals(500, doHeadStatus(""));
    }

    @Test
    public void headAutoFromGet() throws Exception {
        register("declare %rest:GET %rest:path('') function m:f() { () };");
        assertEquals(200, doHeadStatus(""));
    }

    @Test
    public void headAutoFromGetWithContent() throws Exception {
        register("declare %rest:GET %rest:path('') function m:f() { 1 to 5 };");
        assertEquals(200, doHeadStatus(""));
    }

    // === OPTIONS ===

    @Category(BaseXExtension.class)
    @Test
    public void optionsExplicit() throws Exception {
        register("declare %rest:OPTIONS %rest:path('') function m:f() { 1 };");
        assertEquals("1", doOptions("").trim());
    }

    @Category(BaseXExtension.class)
    @Test
    public void optionsExplicitEmpty() throws Exception {
        register("declare %rest:OPTIONS %rest:path('') function m:f() {};");
        assertEquals("", doOptions("").trim());
    }

    @Test
    public void optionsAutoFromGet() throws Exception {
        register("declare %rest:GET %rest:path('') function m:f() { <rest:response/> };");
        final int status = doOptionsStatus("");
        assertEquals(200, status);
    }

    // === Duplicate methods ===

    @Test
    public void duplicateGetAnnotation() throws Exception {
        get(500, "declare %rest:GET %rest:GET %rest:path('') function m:f() { 'x' };", "");
    }
}
