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

import org.apache.http.HttpResponse;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests for RESTXQ content negotiation: consumes and produces annotations,
 * and response elements.
 *
 * <p>Ported from BaseX's RestXqFilterTest and expanded.</p>
 */
public class RestXqFilterTest extends RestXqTestBase {

    // === Content negotiation: consumes ===

    @Test
    public void consumesNoContentType() throws Exception {
        get(404,
            "declare %rest:path('') %rest:consumes('text/plain') function m:f() { 1 };",
            "");
    }

    @Test
    public void consumesWildcard() throws Exception {
        get("1",
            "declare %rest:path('') %rest:consumes('*/*') function m:f() { 1 };",
            "");
    }

    @Test
    public void consumesWithParams() throws Exception {
        get(404,
            "declare %rest:path('') %rest:consumes('text/plain;bla=blu') function m:f() { 1 };",
            "");
    }

    @Test
    public void consumesMultiple() throws Exception {
        get("1",
            "declare %rest:path('') %rest:consumes('text/plain', '*/*') function m:f() { 1 };",
            "");
    }

    @Test
    public void consumesSeparateAnnotations() throws Exception {
        get("1",
            "declare %rest:path('') %rest:consumes('text/plain') %rest:consumes('*/*') " +
                "function m:f() { 1 };",
            "");
    }

    @Test
    public void consumesPrecedence() throws Exception {
        get("2",
            "declare %rest:path('') %rest:consumes('text/plain') function m:f() { 1 };\n" +
            "declare %rest:path('') %rest:consumes('*/*') function m:g() { 2 };",
            "");
    }

    @Test
    public void consumesInvalid() throws Exception {
        get(404,
            "declare %rest:path('') %rest:consumes('X') function m:f() { 1 };",
            "");
    }

    // === Content negotiation: produces ===

    @Test
    public void producesTextPlain() throws Exception {
        get("1",
            "declare %rest:path('') %rest:produces('text/plain') function m:f() { 1 };",
            "");
    }

    @Test
    public void producesWildcard() throws Exception {
        get("1",
            "declare %rest:path('') %rest:produces('*/*') function m:f() { 1 };",
            "");
    }

    @Test
    public void producesWithParams() throws Exception {
        get("1",
            "declare %rest:path('') %rest:produces('text/plain;bla=blu') function m:f() { 1 };",
            "");
    }

    @Test
    public void producesMultiple() throws Exception {
        get("1",
            "declare %rest:path('') %rest:produces('text/plain', '*/*') function m:f() { 1 };",
            "");
    }

    @Test
    public void producesSeparateAnnotations() throws Exception {
        get("1",
            "declare %rest:path('') %rest:produces('text/plain') %rest:produces('*/*') " +
                "function m:f() { 1 };",
            "");
    }

    @Category(BaseXExtension.class)
    @Test
    public void producesQuality() throws Exception {
        register(
            "declare %rest:path('') %rest:produces('application/json;qs=1') function m:f() { 'json' };\n" +
            "declare %rest:path('') %rest:produces('text/plain;qs=0.5') function m:g() { 'text' };");
        final HttpResponse response = doGetWithAccept("", "*/*");
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    // === Response elements ===

    @Test
    public void responseElement() throws Exception {
        get("1",
            "declare %rest:path('') function m:f() { <rest:response/>, 1 };",
            "");
    }

    @Test
    public void responseNonRestElement() throws Exception {
        register("declare %rest:path('') function m:f() { <rest:R/> };");
        final String result = doGet("");
        assertNotNull(result);
    }

    @Test
    public void responseStatus200() throws Exception {
        get(200,
            "declare %rest:path('') function m:f() { " +
                "<rest:response><http:response status='200'/></rest:response> };",
            "");
    }

    @Test
    public void responseStatusMessage() throws Exception {
        get("OK",
            "declare %rest:path('') function m:f() { " +
                "<rest:response><http:response status='200' message='OK'/></rest:response>, 'OK' };",
            "");
    }

    // === Erroneous response elements ===

    @Category(BaseXExtension.class)
    @Test
    public void responseInvalidAttribute() throws Exception {
        get(500,
            "declare %rest:path('') function m:f() { <rest:response abc='x'/> };",
            "");
    }

    @Category(BaseXExtension.class)
    @Test
    public void responseTextContent() throws Exception {
        get(500,
            "declare %rest:path('') function m:f() { <rest:response>X</rest:response> };",
            "");
    }

    @Category(BaseXExtension.class)
    @Test
    public void responseNonHttpChild() throws Exception {
        get(500,
            "declare %rest:path('') function m:f() { <rest:response><X/></rest:response> };",
            "");
    }

    @Category(BaseXExtension.class)
    @Test
    public void responseInvalidHttpAttribute() throws Exception {
        get(500,
            "declare %rest:path('') function m:f() { " +
                "<rest:response><http:response stat='200'/></rest:response> };",
            "");
    }

    @Category(BaseXExtension.class)
    @Test
    public void responseHttpTextContent() throws Exception {
        get(500,
            "declare %rest:path('') function m:f() { " +
                "<rest:response><http:response>X</http:response></rest:response> };",
            "");
    }
}
