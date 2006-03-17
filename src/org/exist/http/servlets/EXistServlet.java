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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.Principal;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.xmlrpc.Base64;
import org.exist.EXistException;
import org.exist.http.BadRequestException;
import org.exist.http.Descriptor;
import org.exist.http.NotFoundException;
import org.exist.http.RESTServer;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.User;
import org.exist.security.XmldbPrincipal;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.Constants;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;

/**
 * Implements the REST-style interface if eXist is running within
 * a servlet engine. The real work is done by class 
 * {@link org.exist.http.RESTServer}.
 * 
 * @author wolf
 */
public class EXistServlet extends HttpServlet {

	private String formEncoding = null;
	public final static String DEFAULT_ENCODING = "UTF-8";
	
	private BrokerPool pool = null;
	private String defaultUser = SecurityManager.GUEST_USER;
	private String defaultPass = SecurityManager.GUEST_USER;
	
	private RESTServer server;

	/* (non-Javadoc)
	 * @see javax.servlet.GenericServlet#init(javax.servlet.ServletConfig)
	 */
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		
		// Configure BrokerPool
		try {
			if (BrokerPool.isConfigured()) {
				this.log("Database already started. Skipping configuration ...");
			} else {
				String confFile = config.getInitParameter("configuration");
				String dbHome = config.getInitParameter("basedir");
				String start = config.getInitParameter("start");
	
				if (confFile == null)
					confFile = "conf.xml";
				dbHome = (dbHome == null) ? config.getServletContext().getRealPath(
						".") : config.getServletContext().getRealPath(dbHome);
				this.log("EXistServlet: exist.home=" + dbHome);
				System.setProperty("exist.home", dbHome);
				
				File f = new File(dbHome + File.separator + confFile);
				this.log("reading configuration from " + f.getAbsolutePath());

				if (!f.canRead())
					throw new ServletException("configuration file " + confFile
							+ " not found or not readable");
				Configuration configuration = new Configuration(confFile, dbHome);
				if (start != null && start.equals("true"))
					startup(configuration);
			}
			pool = BrokerPool.getInstance();
			String option = config.getInitParameter("user");
			if (option != null)
				defaultUser = option;
			option = config.getInitParameter("password");
			if (option != null)
				defaultPass = option;
		} catch (EXistException e) {
			throw new ServletException("No database instance available");
		} catch (DatabaseConfigurationException e) {
			throw new ServletException("Unable to configure database instance: " + e.getMessage(), e);
		}

		// Instantiate REST server
		formEncoding = config.getInitParameter("form-encoding");
		if(formEncoding == null)
			formEncoding = DEFAULT_ENCODING;
		String containerEncoding = config.getInitParameter("container-encoding");
		if(containerEncoding == null)
			containerEncoding = DEFAULT_ENCODING;
		server = new RESTServer(formEncoding, containerEncoding);
	}

	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doPut(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		//first, adjust the path
		String path = adjustPath(request);
		
		//second, perform descriptor actions
		Descriptor descriptor = Descriptor.getDescriptorSingleton();
    	if(descriptor != null)
    	{
    		//TODO: figure out a way to log PUT requests with HttpServletRequestWrapper and Descriptor.doLogRequestInReplayLog() 
    		
    		//map's the path if a mapping is specified in the descriptor
    		path = descriptor.mapPath(path);
    	}
		
		//third, authenticate the user
		User user = authenticate(request);
		if (user == null) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN,
					"Permission denied: unknown user or password");
			return;
		}
		
		//fourth, process the request
		ServletInputStream is = request.getInputStream();
		int len = request.getContentLength();
		// put may send a lot of data, so save it
		// to a temporary file first.
		File tempFile = File.createTempFile("existSRV", ".tmp");
		OutputStream os = new FileOutputStream(tempFile);
		byte[] buffer = new byte[4096];
		int count, l = 0;
		do {
			count = is.read(buffer);
			if (count > 0)
				os.write(buffer, 0, count);
			l += count;
		} while (l < len);
		os.close();
		
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			server.doPut(broker, tempFile, path, request, response);
		} catch (BadRequestException e) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
		} catch (PermissionDeniedException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
		} catch (EXistException e) {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		} finally {
			pool.release(broker);
		}
        tempFile.delete();
	}

    /**
     * @param request
     * @return
     */
    private String adjustPath(HttpServletRequest request) {
        String path = request.getPathInfo();
        int p = path.lastIndexOf(';');
        if (p != Constants.STRING_NOT_FOUND)
			path = path.substring(0, p);
        return path;
    }
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		
		//first, adjust the path
		String path = adjustPath(request);
		
		//second, perform descriptor actions
		Descriptor descriptor = Descriptor.getDescriptorSingleton();
    	if(descriptor != null)
    	{
    		//logs the request if specified in the descriptor
    		descriptor.doLogRequestInReplayLog(request);
    		
    		//map's the path if a mapping is specified in the descriptor
    		path = descriptor.mapPath(path);
    	}
		
    	//third, authenticate the user
		User user = authenticate(request);
		if (user == null) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN,
					"Permission denied: unknown user " + "or password");
			return;
		}

		//fouth, process the request
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			server.doGet(broker, request, response, path);
		} catch (BadRequestException e) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, e
					.getMessage());
		} catch (PermissionDeniedException e) {
			response
					.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
		} catch (NotFoundException e) {
			response
					.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
		} catch (EXistException e) {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e
					.getMessage());
		} finally {
			pool.release(broker);
		}
	}

	protected void doHead(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		//first, adjust the path
		String path = adjustPath(request);
		
		//second, perform descriptor actions
		Descriptor descriptor = Descriptor.getDescriptorSingleton();
    	if(descriptor != null)
    	{
    		//logs the request if specified in the descriptor
    		descriptor.doLogRequestInReplayLog(request);
    		
    		//map's the path if a mapping is specified in the descriptor
    		path = descriptor.mapPath(path);
    	}
    	
		//third, authenticate the user
		User user = authenticate(request);
		if (user == null) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN,
					"Permission denied: unknown user " + "or password");
			return;
		}
		
		//fourth, process the request
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			server.doHead(broker, request, response, path);
		} catch (BadRequestException e) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, e
					.getMessage());
		} catch (PermissionDeniedException e) {
			response
					.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
		} catch (NotFoundException e) {
			response
					.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
		} catch (EXistException e) {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e
					.getMessage());
		} finally {
			pool.release(broker);
		}
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doDelete(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		//first, adjust the path
		String path = adjustPath(request);
		
		//second, perform descriptor actions
		Descriptor descriptor = Descriptor.getDescriptorSingleton();
		if(descriptor != null)
    	{
			//map's the path if a mapping is specified in the descriptor
    		path = descriptor.mapPath(path);
    	}
		
		//third, authenticate the user
		User user = authenticate(request);
		if (user == null) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN,
					"Permission denied: unknown user " + "or password");
			return;
		}

		//fourth, process the request
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			server.doDelete(broker, path, response);
		} catch (PermissionDeniedException e) {
			response
			.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
		} catch (NotFoundException e) {
			response
			.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
		} catch (EXistException e) {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e
					.getMessage());
		} finally {
			pool.release(broker);
		}
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doPost(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException
	{	
		HttpServletRequest request = null;

		//For POST request, If we are logging the requests we must wrap HttpServletRequest in HttpServletRequestWrapper
		//otherwise we cannot access the POST parameters from the content body of the request!!! - deliriumsky
		Descriptor descriptor = Descriptor.getDescriptorSingleton();
		if(descriptor.allowRequestLogging())
		{
			request = new HttpServletRequestWrapper(req, formEncoding);
		}
		else
		{
			request = req;
		}
		
		//first, adjust the path
		String path = request.getPathInfo();
		if(path == null)
		{
			path = "";
		}
		else
		{
            path = adjustPath(request);
		}
		
		//second, perform descriptor actions
    	if(descriptor != null)
    	{
    		//logs the request if specified in the descriptor
    		descriptor.doLogRequestInReplayLog(request);
    		
    		//map's the path if a mapping is specified in the descriptor
    		path = descriptor.mapPath(path);
    	}
		
    	//third, authenticate the user
		User user = authenticate(request);
		if (user == null) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN,
					"Permission denied: unknown user " + "or password");
			return;
		}
		
		//fouth, process the request
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			server.doPost(broker, request, response, path);
		} catch (PermissionDeniedException e) {
			response
			.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
		} catch (EXistException e) {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e
					.getMessage());
		} catch (BadRequestException e) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
		} finally {
			pool.release(broker);
		}
	}
	
    /* (non-Javadoc)
     * @see javax.servlet.GenericServlet#destroy()
     */
    public void destroy() {
        super.destroy();
        BrokerPool.stopAll(false);
    }
    
	private User authenticate(HttpServletRequest request) {
		// First try to validate the principial if passed from the servlet engine
		Principal principal = request.getUserPrincipal();
		
		if(principal instanceof XmldbPrincipal){
			String username = ((XmldbPrincipal)principal).getName();
			String password = ((XmldbPrincipal)principal).getPassword();
			
			this.log("Validating Principle: " + principal.getName());
			User user = pool.getSecurityManager().getUser(username);
			
			if (user != null){
				if (password.equalsIgnoreCase(user.getPassword())){
					this.log("Valid User: " + user.getName());
					return user;
				}else{
					this.log( "Password invalid for user: " + username );
				}
				this.log("User not found: " + principal.getName());
			}	
		}
		
		String auth = request.getHeader("Authorization");
		if(auth == null) {
			return getDefaultUser();
		}
		byte[] c = Base64.decode(auth.substring(6).getBytes());
		String s = new String(c);
		int p = s.indexOf(':');
		if (p == Constants.STRING_NOT_FOUND) {
			 return null;
			 }
		String username = s.substring(0, p);
		String password = s.substring(p + 1);
		
		User user = pool.getSecurityManager().getUser(username);
		if (user == null)
			return null;
		if (!user.validate(password))
			return null;
		return user;
	}
	
	private User getDefaultUser() {
		if (defaultUser != null) {
			User user = pool.getSecurityManager().getUser(defaultUser);
			if (user != null) {
				if (!user.validate(defaultPass))
					return null;
			}
			return user;
		}
		return null;
	}
	
	private void startup(Configuration configuration) throws ServletException {
		if ( configuration == null )
			throw new ServletException( "database has not been " +
			"configured" );
		this.log("configuring eXist instance");
		try {
			if ( !BrokerPool.isConfigured() )
				BrokerPool.configure( 1, 5, configuration );
		} catch ( EXistException e ) {
			throw new ServletException( e.getMessage() );
		}
		try {
			this.log("registering XMLDB driver");
			Class clazz = Class.forName("org.exist.xmldb.DatabaseImpl");
			Database database = (Database)clazz.newInstance();
			DatabaseManager.registerDatabase(database);
		} catch (ClassNotFoundException e) {
			this.log("ERROR", e);
		} catch (InstantiationException e) {
			this.log("ERROR", e);
		} catch (IllegalAccessException e) {
			this.log("ERROR", e);
		} catch (XMLDBException e) {
			this.log("ERROR", e);
		}
	}
}
