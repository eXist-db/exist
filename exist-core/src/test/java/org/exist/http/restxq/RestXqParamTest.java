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
import org.apache.http.client.fluent.Request;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;

/**
 * Tests for RESTXQ parameter annotations: query-param, form-param,
 * header-param, and cookie-param.
 *
 * <p>Ported from BaseX's RestXqParamTest and expanded with form, header,
 * and cookie parameter tests.</p>
 */
public class RestXqParamTest extends RestXqTestBase {

    // === Query parameters ===

    @Test
    public void queryParam() throws Exception {
        get("1",
            "declare %rest:path('') %rest:query-param('a', '{$v}') " +
                "function m:f($v) { $v };",
            "?a=1");
    }

    @Test
    public void queryParamArithmetic() throws Exception {
        get("2",
            "declare %rest:path('') %rest:query-param('a', '{$a}') " +
                "function m:f($a) { $a * 2 };",
            "?a=1");
    }

    @Test
    public void queryParamMultiple() throws Exception {
        get("2",
            "declare %rest:path('') %rest:query-param('a', '{$a}') " +
                "function m:f($a as xs:integer*) { count($a) };",
            "?a=4&a=8");
    }

    @Test
    public void queryParamDefault() throws Exception {
        get("3",
            "declare %rest:path('') %rest:query-param('a', '{$v}', 3) " +
                "function m:f($v) { $v };",
            "");
    }

    @Test
    public void queryParamDefaultMulti() throws Exception {
        get("2",
            "declare %rest:path('') %rest:query-param('a', '{$v}', 4, 8) " +
                "function m:f($v) { count($v) };",
            "");
    }

    @Test
    public void queryParamTwo() throws Exception {
        get("6",
            "declare %rest:path('') %rest:query-param('a', '{$a}') %rest:query-param('b', '{$b}') " +
                "function m:f($a, $b) { $a * $b };",
            "?a=2&b=3");
    }

    @Test
    public void queryParamMissing() throws Exception {
        get("0",
            "declare %rest:path('') %rest:query-param('a', '{$v}') " +
                "function m:f($v) { count($v) };",
            "");
    }

    // === Query parameter errors ===

    @Test
    public void queryParamNoVar() throws Exception {
        get(500,
            "declare %rest:path('') %rest:query-param('a', '{$a}') function m:f() { 1 };",
            "?a=2");
    }

    @Test
    public void queryParamDuplicate() throws Exception {
        get(500,
            "declare %rest:path('') %rest:query-param('a', '{$a}') %rest:query-param('a', '{$a}') " +
                "function m:f($a) { $a };",
            "?a=2");
    }

    @Test
    public void queryParamNotString() throws Exception {
        get(500,
            "declare %rest:path('') %rest:query-param(1, '{$a}') function m:f($a) { $a };",
            "?a=2");
    }

    @Test
    public void queryParamBadTemplate() throws Exception {
        get(500,
            "declare %rest:path('') %rest:query-param('a', '$a') function m:f($a) { $a };",
            "?a=2");
    }

    @Test
    public void queryParamCardError() throws Exception {
        get(500,
            "declare %rest:path('') %rest:query-param('a', '{$a}') " +
                "function m:f($a as item()) { () };",
            "?a=4&a=8");
    }

    // === Form parameters ===

    @Category(BaseXExtension.class)
    @Test
    public void formParam() throws Exception {
        register(
            "declare %rest:POST %rest:path('') %rest:form-param('field', '{$v}') " +
                "function m:f($v) { $v };");
        final String result = doPost("", "field=hello", "application/x-www-form-urlencoded");
        assertEquals("hello", result.trim());
    }

    @Category(BaseXExtension.class)
    @Test
    public void formParamDefault() throws Exception {
        register(
            "declare %rest:POST %rest:path('') %rest:form-param('field', '{$v}', 'default') " +
                "function m:f($v) { $v };");
        final String result = doPost("", "", "application/x-www-form-urlencoded");
        assertEquals("default", result.trim());
    }

    // === Header parameters ===

    @Category(BaseXExtension.class)
    @Test
    public void headerParam() throws Exception {
        register(
            "declare %rest:path('') %rest:header-param('X-Custom', '{$v}') " +
                "function m:f($v) { $v };");
        final HttpResponse response = executor.execute(
            Request.Get(getRestXqUri() + "/")
                .addHeader("X-Custom", "test-value")
        ).returnResponse();
        final String body = asString(response.getEntity().getContent());
        assertEquals("test-value", body.trim());
    }

    @Category(BaseXExtension.class)
    @Test
    public void headerParamDefault() throws Exception {
        register(
            "declare %rest:path('') %rest:header-param('X-Missing', '{$v}', 'fallback') " +
                "function m:f($v) { $v };");
        final String result = doGet("");
        assertEquals("fallback", result.trim());
    }

    // === Cookie parameters ===

    @Category(BaseXExtension.class)
    @Test
    public void cookieParam() throws Exception {
        register(
            "declare %rest:path('') %rest:cookie-param('session', '{$v}') " +
                "function m:f($v) { $v };");
        final HttpResponse response = executor.execute(
            Request.Get(getRestXqUri() + "/")
                .addHeader("Cookie", "session=abc123")
        ).returnResponse();
        final String body = asString(response.getEntity().getContent());
        assertEquals("abc123", body.trim());
    }

    @Category(BaseXExtension.class)
    @Test
    public void cookieParamDefault() throws Exception {
        register(
            "declare %rest:path('') %rest:cookie-param('missing', '{$v}', 'none') " +
                "function m:f($v) { $v };");
        final String result = doGet("");
        assertEquals("none", result.trim());
    }
}
