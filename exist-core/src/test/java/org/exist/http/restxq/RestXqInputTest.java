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

/**
 * Tests for RESTXQ input annotations controlling how request body content
 * is parsed (JSON, CSV, HTML).
 *
 * <p>Ported from BaseX's RestXqInputTest. These are all BaseX extensions —
 * eXist does not currently support %input:* annotations.</p>
 */
@Category(BaseXExtension.class)
public class RestXqInputTest extends RestXqTestBase {

    // === JSON input ===

    @Test
    public void jsonInputLaxNo() throws Exception {
        post("<A__B/>",
            "declare %rest:POST('{$x}') %rest:path('') %input:json('lax=no') " +
                "function m:f($x) { $x/*/* };",
            "", "{ \"A_B\": \"\" }", "application/json");
    }

    @Test
    public void jsonInputLaxYes() throws Exception {
        post("<A_B/>",
            "declare %rest:POST('{$x}') %rest:path('') %input:json('lax=true') " +
                "function m:f($x) { $x/*/* };",
            "", "{ \"A_B\": \"\" }", "application/json");
    }

    @Test
    public void jsonContentTypeLaxFalse() throws Exception {
        post("<A__B/>",
            "declare %rest:POST('{$x}') %rest:path('') function m:f($x) { $x/*/* };",
            "", "{ \"A_B\": \"\" }", "application/json;lax=false");
    }

    @Test
    public void jsonContentTypeLaxYes() throws Exception {
        post("<A_B/>",
            "declare %rest:POST('{$x}') %rest:path('') function m:f($x) { $x/*/* };",
            "", "{ \"A_B\": \"\" }", "application/json;lax=yes");
    }

    @Test
    public void jsonDefault() throws Exception {
        post("<A__B/>",
            "declare %rest:POST('{$x}') %rest:path('') function m:f($x) { $x/*/* };",
            "", "{ \"A_B\": \"\" }", "application/json");
    }

    // === CSV input ===

    @Test
    public void csvInputNoHeader() throws Exception {
        post("",
            "declare %rest:POST('{$x}') %rest:path('') %input:csv('header=no') " +
                "function m:f($x) { $x//A };",
            "", "A\n1", "text/csv");
    }

    @Test
    public void csvInputHeader() throws Exception {
        post("<A>1</A>",
            "declare %rest:POST('{$x}') %rest:path('') %input:csv('header=yes') " +
                "function m:f($x) { $x//A };",
            "", "A\n1", "text/csv");
    }

    @Test
    public void csvContentTypeNoHeader() throws Exception {
        post("",
            "declare %rest:POST('{$x}') %rest:path('') function m:f($x) { $x//A };",
            "", "A\n1", "text/csv;header=no");
    }

    @Test
    public void csvContentTypeHeader() throws Exception {
        post("<A>1</A>",
            "declare %rest:POST('{$x}') %rest:path('') function m:f($x) { $x//A };",
            "", "A\n1", "text/csv;header=yes");
    }

    @Test
    public void csvDefault() throws Exception {
        post("",
            "declare %rest:POST('{$x}') %rest:path('') function m:f($x) { $x//A };",
            "", "A\n1", "text/csv");
    }
}
