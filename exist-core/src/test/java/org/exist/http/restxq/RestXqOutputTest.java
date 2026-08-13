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
import static org.junit.Assert.assertTrue;

/**
 * Tests for RESTXQ serialization output annotations and inline serialization
 * parameters.
 *
 * <p>Ported from BaseX's RestXqOutputTest and expanded.</p>
 */
public class RestXqOutputTest extends RestXqTestBase {

    // === Serialization annotations ===

    @Test
    public void outputMethodText() throws Exception {
        get("9",
            "declare %rest:path('') %output:method('text') function m:f() { '9' };",
            "");
    }

    @Test
    public void outputMethodJson() throws Exception {
        register(
            "declare %rest:path('') %output:method('json') %output:media-type('application/json') " +
                "function m:f() { map { 'key': 'value' } };");
        final HttpResponse response = doGetWithAccept("", "application/json");
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void outputMethodHtml() throws Exception {
        register(
            "declare %rest:path('') %output:method('html') %output:media-type('text/html') " +
                "function m:f() { <html><body>test</body></html> };");
        final HttpResponse response = doGetWithAccept("", "text/html");
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void outputMediaType() throws Exception {
        register(
            "declare %rest:path('') %output:media-type('text/plain') %output:method('text') " +
                "function m:f() { 'hello' };");
        final HttpResponse response = doGetWithAccept("", "text/plain");
        assertEquals(200, response.getStatusLine().getStatusCode());
        final String contentType = response.getEntity().getContentType().getValue();
        assertTrue(contentType.contains("text/plain"));
    }

    // === Inline serialization ===

    @Test
    public void inlineSerializationText() throws Exception {
        get("1",
            "declare %rest:path('') function m:f() { " +
                "<rest:response>" +
                "  <output:serialization-parameters>" +
                "    <output:method value='text'/>" +
                "  </output:serialization-parameters>" +
                "  <http:response status='200'/>" +
                "</rest:response>," +
                "<X>1</X> };",
            "");
    }

    @Test
    public void inlineOverridesAnnotation() throws Exception {
        get("<X>1</X>",
            "declare %rest:path('') %output:method('text') function m:f() { " +
                "<rest:response>" +
                "  <output:serialization-parameters>" +
                "    <output:method value='xml'/>" +
                "  </output:serialization-parameters>" +
                "  <http:response status='200'/>" +
                "</rest:response>," +
                "<X>1</X> };",
            "");
    }

    // === Validation errors ===

    @Test
    public void unknownSerializationParam() throws Exception {
        get(500,
            "declare %rest:path('') %output:xyz('abc') function m:f() { '9' };",
            "");
    }

    @Test
    public void missingValue() throws Exception {
        get(500,
            "declare %rest:path('') %output:method function m:f() { '9' };",
            "");
    }

    @Test
    public void multipleValues() throws Exception {
        get(500,
            "declare %rest:path('') %output:method('xml', 'html') function m:f() { '9' };",
            "");
    }

    @Category(BaseXExtension.class)
    @Test
    public void inlineSerializationError() throws Exception {
        get(500,
            "declare %rest:path('') %output:method('text') function m:f() { " +
                "<rest:response>" +
                "  <output:serialization-parameters>" +
                "    <output:method value='xml'/>" +
                "  </output:serialization-parameters>" +
                "  <http:response status='200'/>" +
                "</rest:response>," +
                "1 + <a/> };",
            "");
    }
}
