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
package org.exist.http.webdav;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.OutputKeys;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.http.servlets.Authenticator;
import org.exist.http.servlets.BasicAuthenticator;
import org.exist.http.servlets.DigestAuthenticator;
import org.exist.http.webdav.methods.*;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;

/**
 * @author wolf
 */
public class WebDAV {
	
	public final static String DAV_NS = "DAV:";
	
	//	default content types
	public final static String BINARY_CONTENT = "application/octet-stream";
	public final static String XML_CONTENT = "text/xml";
	
	//	default output properties for the XML serialization
	public final static Properties OUTPUT_PROPERTIES = new Properties();
	static {
		OUTPUT_PROPERTIES.setProperty(OutputKeys.INDENT, "yes");
		OUTPUT_PROPERTIES.setProperty(OutputKeys.ENCODING, "UTF-8");
		OUTPUT_PROPERTIES.setProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
		OUTPUT_PROPERTIES.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "no");
		OUTPUT_PROPERTIES.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "no");
	}
	
	// additional response codes
	public final static int SC_MULTI_STATUS = 207;
	
	private final static Logger LOG = Logger.getLogger(WebDAV.class);
	
	private Authenticator digestAuth, basicAuth;
	private Map supportedMethods = new HashMap();
	private BrokerPool pool;
	
	public WebDAV() throws ServletException {
		try {
			pool = BrokerPool.getInstance();
		} catch (EXistException e) {
			throw new ServletException("Error found while initializing database: " + e.getMessage(), e);
		}
		
		supportedMethods.put("OPTIONS", new Options());
		supportedMethods.put("GET", new Get(pool));
		supportedMethods.put("HEAD", new Head());
		supportedMethods.put("PROPFIND", new Propfind(pool));
		supportedMethods.put("PUT", new Put(pool));
		supportedMethods.put("DELETE", new Delete(pool));
		
		digestAuth = new DigestAuthenticator(pool);
		basicAuth = new BasicAuthenticator(pool);
	}
	
	public void process(HttpServletRequest request, HttpServletResponse response)
	throws ServletException, IOException {
		User user = authenticate(request, response);
		if(user == null)
			return;
		
		String path = request.getPathInfo();
		if(path == null || path.length() == 0 || path.equals("/"))
			path = "/db";
		if(path.endsWith("/"))
			path = path.substring(0, path.length() - 1);
		LOG.debug("path = " + path + "; method = " + request.getMethod());
		
		DocumentImpl resource = null;
		Collection collection = null;
		WebDAVMethod method = null;
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			
			collection = broker.getCollection(path);
			if(collection == null) {
				resource = (DocumentImpl)broker.getDocument(path);
				if(resource != null)
					collection = resource.getCollection();
			}
			method = (WebDAVMethod)supportedMethods.get(request.getMethod());
			if(method == null) {
				response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
						"Method is not supported: " + request.getMethod());
				return;
			}
		} catch (EXistException e) {
			throw new ServletException("An error occurred while retrieving resource: " + e.getMessage(), e);
		} catch (PermissionDeniedException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "Not allowed to access resource " + path);
		} finally {
			pool.release(broker);
		}
		method.process(user, request, response, collection, resource);
	}
	
	private User authenticate(HttpServletRequest request, HttpServletResponse response)
	throws IOException {
		String credentials = request.getHeader("Authorization");
		if(credentials == null) {
			digestAuth.sendChallenge(request, response);
			return null;
		}
		if(credentials.toUpperCase().startsWith("DIGEST")) {
			return digestAuth.authenticate(request, response);
		} else {
			LOG.debug("Falling back to basic authentication");
			return basicAuth.authenticate(request, response);
		}
	}
}
