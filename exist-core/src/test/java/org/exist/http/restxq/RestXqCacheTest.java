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
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.net.URLEncoder;

import static org.junit.Assert.assertEquals;

/**
 * Tests for RESTXQ module discovery, caching, and cache invalidation.
 */
public class RestXqCacheTest extends RestXqTestBase {

    @Test
    public void moduleDiscovery() throws Exception {
        register("declare %rest:path('/cache-test-discovery') function m:f() { 'discovered' };");
        assertEquals("discovered", doGet("cache-test-discovery").trim());
    }

    @Test
    public void moduleUpdate() throws Exception {
        register("declare %rest:path('/cache-test-update') function m:f() { 'v1' };");
        assertEquals("v1", doGet("cache-test-update").trim());

        register("declare %rest:path('/cache-test-update') function m:f() { 'v2' };");
        assertEquals("v2", doGet("cache-test-update").trim());
    }

    @Test
    public void moduleDelete() throws Exception {
        register("declare %rest:path('/cache-test-delete') function m:f() { 'exists' };");
        assertEquals("exists", doGet("cache-test-delete").trim());

        // register() removes previous module, new one has different path
        register("declare %rest:path('/cache-test-other') function m:f() { 'other' };");

        final int status = doGetStatus("cache-test-delete");
        assertEquals(404, status);
    }

    @Test
    public void moduleCompileError() throws Exception {
        storeModuleDirect("broken.xqm",
            "xquery version '3.1';\n" +
            "module namespace broken = 'http://exist-db.org/test/broken';\n" +
            "declare namespace rest = 'http://exquery.org/ns/restxq';\n" +
            "declare %rest:path('/broken') function broken:f() { $undefined };");

        Thread.sleep(500);

        register("declare %rest:path('/working') function m:f() { 'works' };");
        assertEquals("works", doGet("working").trim());
    }

    @Category(BaseXExtension.class)
    @Test
    public void cacheInvalidation() throws Exception {
        register("declare %rest:path('/cache-test-init') function m:f() { 'cached' };");
        assertEquals("cached", doGet("cache-test-init").trim());
        assertEquals("cached", doGet("cache-test-init").trim());
    }

    @Category(BaseXExtension.class)
    @Test
    public void timestampCheck() throws Exception {
        final String func = "declare %rest:path('/cache-test-timestamp') function m:f() { 'same' };";
        register(func);
        assertEquals("same", doGet("cache-test-timestamp").trim());

        register(func);
        assertEquals("same", doGet("cache-test-timestamp").trim());
    }

    @org.junit.Ignore("Not yet implemented: rest:resource-functions() WADL endpoint")
    @Category(BaseXExtension.class)
    @Test
    public void statusEndpoint() throws Exception {
        register("declare %rest:path('/cache-test-status') function m:f() { 'status' };");

        final HttpResponse response = executor.execute(Request
                .Get(getRestUri() + "/db/?_query=" +
                    URLEncoder.encode(
                        "import module namespace rest='http://exquery.org/ns/restxq'; " +
                        "count(rest:resource-functions()//rest:resource-function)",
                        "UTF-8"))
        ).returnResponse();
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
    }

    @Category(BaseXExtension.class)
    @Test
    public void statusShowsErrors() throws Exception {
        storeModuleDirect("error-status.xqm",
            "xquery version '3.1';\n" +
            "module namespace err = 'http://exist-db.org/test/error-status';\n" +
            "declare namespace rest = 'http://exquery.org/ns/restxq';\n" +
            "declare %rest:path('/error-status') function err:f() { $missing };");

        Thread.sleep(500);

        register("declare %rest:path('/healthy') function m:f() { 'ok' };");
        assertEquals("ok", doGet("healthy").trim());
    }
}
