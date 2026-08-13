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
import static org.junit.Assert.assertTrue;

/**
 * Tests for eXist-specific RESTXQ authentication annotations:
 * %auth:allow-groups, %auth:allow-users, %auth:deny-groups, %auth:login-required.
 *
 * <p>These annotations are eXist extensions, not part of the RESTXQ spec or BaseX.</p>
 */
@Category(ExistExtension.class)
public class RestXqAuthTest extends RestXqTestBase {

    private static final String AUTH_HEADER =
            "declare namespace auth = 'http://exist-db.org/xquery/restxq/auth';\n";

    // === Group-based auth ===

    @Test
    public void allowGroupDba() throws Exception {
        register(AUTH_HEADER +
            "declare %rest:path('') %auth:allow-groups('dba') function m:f() { 'ok' };");
        final int status = doGetStatus("");
        assertEquals(200, status);
    }

    @Test
    public void allowGroupDenied() throws Exception {
        register(AUTH_HEADER +
            "declare %rest:path('') %auth:allow-groups('dba') function m:f() { 'secret' };");
        final int status = doGetStatusNoAuth("");
        assertTrue("Expected 401 or 403 but got " + status,
            status == 401 || status == 403);
    }

    @Test
    public void allowMultipleGroups() throws Exception {
        register(AUTH_HEADER +
            "declare %rest:path('') %auth:allow-groups('dba', 'admin') function m:f() { 'ok' };");
        final int status = doGetStatus("");
        assertEquals(200, status);
    }

    @Test
    public void noAuthPublic() throws Exception {
        register("declare %rest:path('') function m:f() { 'public' };");
        final int status = doGetStatus("");
        assertEquals(200, status);
    }

    // === User-based auth ===

    @Test
    public void allowUser() throws Exception {
        register(AUTH_HEADER +
            "declare %rest:path('') %auth:allow-users('admin') function m:f() { 'ok' };");
        final int status = doGetStatus("");
        assertEquals(200, status);
    }

    @Test
    public void allowUserDenied() throws Exception {
        register(AUTH_HEADER +
            "declare %rest:path('') %auth:allow-users('admin') function m:f() { 'secret' };");
        final int status = doGetStatusNoAuth("");
        assertTrue("Expected 401 or 403 but got " + status,
            status == 401 || status == 403);
    }

    // === Deny rules ===

    @Test
    public void denyGroup() throws Exception {
        register(AUTH_HEADER +
            "declare %rest:path('') %auth:deny-groups('guest') function m:f() { 'ok' };");
        final int status = doGetStatus("");
        assertEquals(200, status);
    }

    @Test
    public void loginRequired() throws Exception {
        register(AUTH_HEADER +
            "declare %rest:path('') %auth:login-required function m:f() { 'ok' };");
        final int statusNoAuth = doGetStatusNoAuth("");
        assertTrue("Expected 401 or 403 without auth but got " + statusNoAuth,
            statusNoAuth == 401 || statusNoAuth == 403);
    }

    @Test
    public void loginRequiredWithAuth() throws Exception {
        register(AUTH_HEADER +
            "declare %rest:path('') %auth:login-required function m:f() { 'ok' };");
        final int status = doGetStatus("");
        assertEquals(200, status);
    }

    // === Combination ===

    @Test
    public void authPlusPath() throws Exception {
        register(AUTH_HEADER +
            "declare %rest:GET %rest:path('/secure/data') %auth:allow-groups('dba') " +
                "function m:f() { 'secure data' };");
        final int status = doGetStatus("secure/data");
        assertEquals(200, status);
    }

    @Test
    public void authFailureBeforeExecution() throws Exception {
        register(AUTH_HEADER +
            "declare %rest:path('') %auth:allow-groups('nonexistent') function m:f() { 'never' };");
        final int status = doGetStatusNoAuth("");
        assertTrue("Expected 401 or 403 but got " + status,
            status == 401 || status == 403);
    }
}
