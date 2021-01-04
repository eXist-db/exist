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
package org.exist.http.servlets;

import org.exist.security.Subject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Interface for HTTP authentication methods.
 * 
 * @author wolf
 */
public interface Authenticator {
	
    /**
     * Try to authenticate the user specified in the HTTP request.
     * 
     * @param request the http request
     * @param response the http response
	 * @param sendChallenge true if the challenge should be sent, false otherwise
	 *
     * @return The authenticated user or null if the user isn't autenticated
	 *
     * @throws IOException if an I/O error occurs
     */
	Subject authenticate(HttpServletRequest request, HttpServletResponse response, boolean sendChallenge) throws IOException;
	
	/**
	 * Send an WWW-Authenticate header back to client.
	 *
	 * @param request the http request
	 * @param response the http response
	 *
	 * @throws IOException if an I/O error occurs
	 */
	void sendChallenge(HttpServletRequest request, HttpServletResponse response) throws IOException;
}