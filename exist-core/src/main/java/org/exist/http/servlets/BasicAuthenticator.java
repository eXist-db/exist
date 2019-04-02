/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2008 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.http.servlets;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.security.AuthenticationException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.util.Base64Decoder;
import org.exist.xquery.XQueryContext;

/**
 * @author wolf
 */
public class BasicAuthenticator implements Authenticator {

	protected final static Logger LOG = LogManager.getLogger(BasicAuthenticator.class);

	private BrokerPool pool;

	public BasicAuthenticator(BrokerPool pool) {
		this.pool = pool;
	}

	@Override
	public Subject authenticate(
			HttpServletRequest request,
			HttpServletResponse response, 
			boolean sendChallenge) throws IOException {
		
		String credentials = request.getHeader("Authorization");
		String username = null;
		String password = null;
                try {
                    if (credentials != null) {
                            final Base64Decoder dec = new Base64Decoder();
                            dec.translate(credentials.substring("Basic ".length()));
                            final byte[] c = dec.getByteArray();
                            final String s = new String(c);
                            // LOG.debug("BASIC auth credentials: "+s);
                            final int p = s.indexOf(':');
                            username = p < 0 ? s : s.substring(0, p);
                            password = p < 0 ? null : s.substring(p + 1);
                    }
                } catch(final IllegalArgumentException iae) {
                    LOG.warn("Invalid BASIC authentication header received: " + iae.getMessage(), iae);
                    credentials = null;
                }

		// get the user from the session if possible
		final HttpSession session = request.getSession(false);
		Subject user = null;
		if (session != null) {
			user = (Subject) session.getAttribute(XQueryContext.HTTP_SESSIONVAR_XMLDB_USER);
			if (user != null && (username == null || user.getName().equals(username))) {
				return user;
			}
		}

		if (user != null) {
			session.removeAttribute(XQueryContext.HTTP_SESSIONVAR_XMLDB_USER);
		}

		// get the credentials
		if (credentials == null) {
			// prompt for credentials

			// LOG.debug("Sending BASIC auth challenge.");
			if (sendChallenge) {sendChallenge(request, response);}
			return null;
		}

		// authenticate the credentials
		final SecurityManager secman = pool.getSecurityManager();
		try {
			user = secman.authenticate(username, password);
		} catch (final AuthenticationException e) {
			// if authentication failed then send a challenge request again
			if (sendChallenge) {sendChallenge(request, response);}
			return null;
		}

		// store the user in the session
		if (session != null) {
			session.setAttribute(XQueryContext.HTTP_SESSIONVAR_XMLDB_USER, user);
		}

		// return the authenticated user
		return user;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.exist.http.servlets.Authenticator#sendChallenge(javax.servlet.http
	 * .HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	public void sendChallenge(HttpServletRequest request,
			HttpServletResponse response) throws IOException {
		response.setHeader("WWW-Authenticate", "Basic realm=\"exist\"");
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
	}
}
