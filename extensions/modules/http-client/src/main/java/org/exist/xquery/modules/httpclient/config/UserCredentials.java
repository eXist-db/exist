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
package org.exist.xquery.modules.httpclient.config;

/**
 * Immutable user credentials parsed from an {@code <http:request>} element.
 *
 * <p>Holds the authentication-related attributes ({@code username}, {@code password},
 * {@code auth-method}, {@code send-authorization}) as defined by the EXPath HTTP Client
 * specification.</p>
 *
 * @param username          the username for authentication
 * @param password          the password for authentication
 * @param authMethod        the authentication method (e.g., "Basic", "Digest")
 * @param sendAuthorization whether to send the Authorization header preemptively
 */
public record UserCredentials(
        String username,
        String password,
        String authMethod,
        boolean sendAuthorization
) {
    /**
     * Default credentials: no authentication.
     */
    public static final UserCredentials DEFAULTS = new UserCredentials(null, null, null, false);
}
