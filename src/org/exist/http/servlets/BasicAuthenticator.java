/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 *  $Id$
 */
package org.exist.http.servlets;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.log4j.Logger;

import org.apache.xmlrpc.Base64;
import org.exist.security.SecurityManager;
import org.exist.security.User;
import org.exist.storage.BrokerPool;

/**
 * @author wolf
 */
public class BasicAuthenticator implements Authenticator {
	
   protected final static Logger LOG = Logger.getLogger(BasicAuthenticator.class);
   
	private BrokerPool pool;
	
	public BasicAuthenticator(BrokerPool pool) {
		this.pool = pool;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.http.servlets.Authenticator#authenticate(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	public User authenticate(HttpServletRequest request,
			HttpServletResponse response) throws IOException {
		String credentials = request.getHeader("Authorization");
		if(credentials == null) {
                       //LOG.debug("Sending BASIC auth challenge.");
			sendChallenge(request, response);
			return null;
		}
		byte[] c = Base64.decode(credentials.substring("Basic ".length()).getBytes());
		String s = new String(c);
                //LOG.debug("BASIC auth credentials: "+s);
		int p = s.indexOf(':');
		String username = p<0 ? s : s.substring(0, p);
		String password = p<0 ? null : s.substring(p + 1);
		
		SecurityManager secman = pool.getSecurityManager();
		User user = secman.getUser(username);
		if(user == null) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return null;
		}
		if (!user.validate(password)) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return null;
		}
		return user;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.http.servlets.Authenticator#sendChallenge(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	public void sendChallenge(HttpServletRequest request,
			HttpServletResponse response) throws IOException {
	    response.setHeader("WWW-Authenticate", "Basic realm=\"exist\"");
		response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
	}
}
