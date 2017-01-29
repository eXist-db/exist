/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009-2011 The eXist Project
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
package org.exist.security.realm.iprange;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.security.DefaultIdentityService;
import org.eclipse.jetty.server.Authentication;
import org.eclipse.jetty.server.UserIdentity;
import org.exist.Database;
import org.exist.EXistException;
import org.exist.config.ConfigurationException;
import org.exist.security.AXSchemaType;
import org.exist.security.AbstractAccount;
import org.exist.security.Account;
import org.exist.security.AbstractRealm;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.security.internal.HttpSessionAuthentication;
import org.exist.security.internal.SubjectAccreditedImpl;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.HTTPUtils;
import org.exist.security.AuthenticationException;

/**
 * IPRange authenticator servlet.
 * 
 * @author <a href="mailto:wshager@gmail.com">Wouter Hager</a>
 * 
 */
public class IPRangeServlet extends HttpServlet {

	private static final long serialVersionUID = -568037449837549034L;

	protected final static Logger LOG = LogManager.getLogger(IPRangeServlet.class);

    public static AbstractRealm realm = null;

	public IPRangeServlet() throws ServletException {
	}

    @Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
	}

    @Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		doPost(req, resp);
	}

    @Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
    	
    	
    	String ip = request.getHeader("X-Forwarded-For");
    	
    	if(ip == null) ip = request.getRemoteAddr();
    	
    	LOG.info("GOT IPRangeServlet "+ip);
    	
    	String json = "{\"fail\":\"IP range not authenticated\"}";
    	
    	try {
    		SecurityManager secman = IPRangeRealm.instance.getSecurityManager();
    		Subject user = secman.authenticate(ip,ip);
    		if(user != null) {
    			LOG.info("IPRangeServlet user " +user.getUsername()+ " found");
	    		final HttpSession session = request.getSession();
	    		// store the user in the session
	    		if (session != null) {
	    			json = "{\"user\":\""+user.getUsername()+"\",\"isAdmin\":\""+user.hasDbaRole()+"\"}";
	    			LOG.info("IPRangeServlet setting session attr "+ XQueryContext.HTTP_SESSIONVAR_XMLDB_USER);
	    			session.setAttribute(XQueryContext.HTTP_SESSIONVAR_XMLDB_USER, user);
	    		} else {
	    			LOG.info("IPRangeServlet session is null");
	    		}
    		} else {
    			LOG.info("IPRangeServlet user not found");
    		}
    	} catch(AuthenticationException e){
    		throw new IOException(e.getMessage());
    	} finally {
    		response.setContentType("application/json");
    		PrintWriter out = response.getWriter();
    		out.print(json);
    		out.flush();
    	}
	}

}
