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
import static org.junit.Assert.assertFalse;

/**
 * Tests for RESTXQ redirect and forward functionality.
 *
 * <p>Ported from BaseX's RestXqRedirectTest. The web:redirect and web:forward
 * functions are BaseX extensions.</p>
 */
@Category(BaseXExtension.class)
public class RestXqRedirectTest extends RestXqTestBase {

    @Test
    public void redirect() throws Exception {
        get("R",
            "declare %rest:path('') function m:a() { web:redirect('a') };\n" +
            "declare %rest:path('a') function m:b() { 'R' };",
            "");
    }

    @Test
    public void forward() throws Exception {
        get("F",
            "declare %rest:path('') function m:a() { web:forward('a') };\n" +
            "declare %rest:path('a') function m:b() { 'F' };",
            "");
    }

    @Test
    public void forwardPreservesBody() throws Exception {
        register(
            "declare %rest:POST('{$x}') %rest:path('') function m:a($x) { web:forward('b') };\n" +
            "declare %rest:POST('{$x}') %rest:path('b') function m:b($x) { $x };");
        final String result = doPost("", "data", "text/plain");
        assertEquals("data", result.trim());
    }

    @Test
    public void webError() throws Exception {
        register("declare %rest:path('') function m:f() { web:error(418, 'teapot') };");
        final int status = doGetStatus("");
        assertEquals(418, status);
    }

    @Test
    public void webErrorNoTrace() throws Exception {
        register("declare %rest:path('') function m:f() { web:error(500, 'oops') };");
        final String body = doGet("");
        assertFalse(body.contains("at org.exist"));
    }
}
