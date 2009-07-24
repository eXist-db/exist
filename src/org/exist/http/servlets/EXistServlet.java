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

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.http.BadRequestException;
import org.exist.http.Descriptor;
import org.exist.http.NotFoundException;
import org.exist.http.RESTServer;
import org.exist.http.SOAPServer;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.User;
import org.exist.security.XmldbPrincipal;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.validation.XmlLibraryChecker;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.Principal;

/**
 * Implements the REST-style interface if eXist is running within
 * a Servlet engine. The real work is done by class 
 * {@link org.exist.http.RESTServer}.
 * 
 * @author wolf
 */
public class EXistServlet extends HttpServlet {

	private String formEncoding = null;
	public final static String DEFAULT_ENCODING = "UTF-8";
	
    protected final static Logger LOG = Logger.getLogger(EXistServlet.class);
	private BrokerPool pool = null;
	private String defaultUsername = SecurityManager.GUEST_USER;
	private String defaultPassword = SecurityManager.GUEST_USER;
	
	private RESTServer srvREST;
	private SOAPServer srvSOAP; 
        
    private Authenticator authenticator;
        
    private User defaultUser;

	/* (non-Javadoc)
	 * @see javax.servlet.GenericServlet#init(javax.servlet.ServletConfig)
	 */
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		
		// Configure BrokerPool
		try {
			if (BrokerPool.isConfigured()) {
				LOG.info("Database already started. Skipping configuration ...");
			} else {
				String confFile = config.getInitParameter("configuration");
				String dbHome = config.getInitParameter("basedir");
				String start = config.getInitParameter("start");
	
				if (confFile == null)
					confFile = "conf.xml";
				dbHome = (dbHome == null) ? config.getServletContext().getRealPath(
						".") : config.getServletContext().getRealPath(dbHome);
                                
				LOG.info("EXistServlet: exist.home=" + dbHome);
				
				File f = new File(dbHome + File.separator + confFile);
				LOG.info("reading configuration from " + f.getAbsolutePath());

				if (!f.canRead())
					throw new ServletException("configuration file " + confFile
							+ " not found or not readable");
				Configuration configuration = new Configuration(confFile, dbHome);
				if (start != null && start.equals("true"))
					startup(configuration);
			}
			pool = BrokerPool.getInstance();
			String option = config.getInitParameter("use-default-user");
                        boolean useDefaultUser = true;
			if (option != null) {
                           useDefaultUser = option.trim().equals("true");
                        }
                        if (useDefaultUser) {
                           option = config.getInitParameter("user");
                           if (option != null)
                                   defaultUsername = option;
                           option = config.getInitParameter("password");
                           if (option != null)
                                   defaultPassword = option;
                           defaultUser = getDefaultUser();
                           if (defaultUser!=null) {
                              LOG.info("Using default user "+defaultUsername+" for all unauthorized requests.");
                           } else {
                              LOG.error("Default user "+defaultUsername+" cannot be found.  A BASIC AUTH challenge will be the default.");
                           }
                        } else {
                           LOG.info("No default user.  All requires must be authorized or will result in a BASIC AUTH challenge.");
                           defaultUser = null;
                        }
                        authenticator = new BasicAuthenticator(pool);
		} catch (EXistException e) {
			throw new ServletException("No database instance available");
		} catch (DatabaseConfigurationException e) {
			throw new ServletException("Unable to configure database instance: " + e.getMessage(), e);
		}
		
		//get form and container encoding's
		formEncoding = config.getInitParameter("form-encoding");
		if(formEncoding == null)
			formEncoding = DEFAULT_ENCODING;
		String containerEncoding = config.getInitParameter("container-encoding");
		if(containerEncoding == null)
			containerEncoding = DEFAULT_ENCODING;
		
		String useDynamicContentType = config.getInitParameter("dynamic-content-type");
		if (useDynamicContentType == null)
			useDynamicContentType = "no";
		
		//Instantiate REST Server
		srvREST = new RESTServer(pool, formEncoding, containerEncoding, 
				useDynamicContentType.equalsIgnoreCase("yes") ||
				useDynamicContentType.equalsIgnoreCase("true"));
                
		//Instantiate SOAP Server
		srvSOAP = new SOAPServer(formEncoding, containerEncoding);
		
		//XML lib checks....
		XmlLibraryChecker.check();
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
		User user = authenticate(request,response);
		if (user == null) {
                        // You now get a challenge if there is no user
			//response.sendError(HttpServletResponse.SC_FORBIDDEN,
			//		"Permission denied: unknown user or password");
			return;
		}
		
		DBBroker broker = null;
                File tempFile = null;
		try {
           XmldbURI dbpath = XmldbURI.create(path);
           broker = pool.get(user);
           Collection collection = broker.getCollection(dbpath);
           if (collection != null) {
              response.sendError(400,"A PUT request is not allowed against a plain collection path.");
              return;
           }
           //fourth, process the request
           ServletInputStream is = request.getInputStream();
           int len = request.getContentLength();
           // put may send a lot of data, so save it
           // to a temporary file first.
           tempFile = File.createTempFile("existSRV", ".tmp");
           FileOutputStream fos = new FileOutputStream(tempFile);
           BufferedOutputStream os = new BufferedOutputStream(fos);
           byte[] buffer = new byte[4096];
           int count, l = 0;
            if (len > 0) {
                do {
                   count = is.read(buffer);
                   if (count > 0)
                      os.write(buffer, 0, count);
                   l += count;
                } while (l < len);
            }
            os.close();

           srvREST.doPut(broker, tempFile, dbpath, request, response);

		} catch (BadRequestException e) {
            if (response.isCommitted())
                throw new ServletException(e.getMessage(), e);
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());

		} catch (PermissionDeniedException e) {
			//If the current user is the Default User and they do not have permission
			//then send a challenge request to prompt the client for a username/password.
			//Else return a FORBIDDEN Error
			if (user.equals(defaultUser)) {
				authenticator.sendChallenge(request, response);
			} else {
				response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
			}
            
		} catch (EXistException e) {
            if (response.isCommitted())
                throw new ServletException(e.getMessage(), e);
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());

        } catch (Throwable e){
            LOG.error(e);
            throw new ServletException("An unknown error occurred: " + e.getMessage(), e);
            
		} finally {
            if (broker!=null) {
              pool.release(broker);
            }
            if (tempFile!=null) {
              tempFile.delete();
            }
		}
	}

    /**
     * @param request
     * @return
     */
    private String adjustPath(HttpServletRequest request) {
        String path = request.getPathInfo();
        
        if(path==null){
            path="";
        }
        
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
    	if(descriptor != null && !descriptor.requestsFiltered())
    	{
    		//logs the request if specified in the descriptor
    		descriptor.doLogRequestInReplayLog(request);
    		
    		//map's the path if a mapping is specified in the descriptor
    		path = descriptor.mapPath(path);
    	}
		
    	//third, authenticate the user
		User user = authenticate(request,response);
		if (user == null) {
                        // You now get a challenge if there is no user
			//response.sendError(HttpServletResponse.SC_FORBIDDEN,
			//		"Permission denied: unknown user " + "or password");
			return;
		}

		//fourth, process the request
		DBBroker broker = null;
		try
		{
			broker = pool.get(user);
			
			//Route the request
			if(path.indexOf(SOAPServer.WEBSERVICE_MODULE_EXTENSION) > -1)
			{
				//SOAP Server
				srvSOAP.doGet(broker, request, response, path);
			}
			else
			{
				//REST Server
				srvREST.doGet(broker, request, response, path);
			}
		} catch (BadRequestException e) {
            if (response.isCommitted())
                throw new ServletException(e.getMessage());
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());

		} catch (PermissionDeniedException e) {
			//If the current user is the Default User and they do not have permission
			//then send a challenge request to prompt the client for a username/password.
			//Else return a FORBIDDEN Error
			if (user.equals(defaultUser)) {
				authenticator.sendChallenge(request, response);
			} else {
				response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
			}

		} catch (NotFoundException e) {
            if (response.isCommitted())
                throw new ServletException(e.getMessage());
			response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());

		} catch (EXistException e) {
            if (response.isCommitted())
                throw new ServletException(e.getMessage(),e);
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    e.getMessage());

        } catch (Throwable e){
            LOG.error(e.getMessage(), e);
            throw new ServletException("An error occurred: " + e.getMessage(), e);
            
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
    	if(descriptor != null && !descriptor.requestsFiltered())
    	{
    		//logs the request if specified in the descriptor
    		descriptor.doLogRequestInReplayLog(request);
    		
    		//map's the path if a mapping is specified in the descriptor
    		path = descriptor.mapPath(path);
    	}
    	
		//third, authenticate the user
		User user = authenticate(request,response);
		if (user == null) {
                        // You now get a challenge if there is no user
			//response.sendError(HttpServletResponse.SC_FORBIDDEN,
			//		"Permission denied: unknown user " + "or password");
			return;
		}
		
		//fourth, process the request
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			srvREST.doHead(broker, request, response, path);

		} catch (BadRequestException e) {
            if (response.isCommitted())
                throw new ServletException(e.getMessage(), e);
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());

		} catch (PermissionDeniedException e) {
			//If the current user is the Default User and they do not have permission
			//then send a challenge request to prompt the client for a username/password.
			//Else return a FORBIDDEN Error
			if (user.equals(defaultUser)) {
				authenticator.sendChallenge(request, response);
			} else {
				response
						.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
			}

		} catch (NotFoundException e) {
            if (response.isCommitted())
                throw new ServletException(e.getMessage(), e);
			response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());

		} catch (EXistException e) {
            if (response.isCommitted())
                throw new ServletException(e.getMessage(), e);
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    e.getMessage());
            
        } catch (Throwable e){
            LOG.error(e);
            throw new ServletException("An unknown error occurred: " + e.getMessage(), e);

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
		User user = authenticate(request,response);
		if (user == null) {
                        // You now get a challenge if there is no user
			//response.sendError(HttpServletResponse.SC_FORBIDDEN,
			//		"Permission denied: unknown user " + "or password");
			return;
		}

		//fourth, process the request
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			srvREST.doDelete(broker, XmldbURI.create(path), response);

		} catch (PermissionDeniedException e) {
			//If the current user is the Default User and they do not have permission
			//then send a challenge request to prompt the client for a username/password.
			//Else return a FORBIDDEN Error
			if (user.equals(defaultUser)) {
				authenticator.sendChallenge(request, response);
			} else {
				response
				.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
			}

		} catch (NotFoundException e) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());

		} catch (EXistException e) {
            if (response.isCommitted())
                throw new ServletException(e.getMessage(), e);
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            
        } catch (Throwable e){
            LOG.error(e);
            throw new ServletException("An unknown error occurred: " + e.getMessage(), e);

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
		if(descriptor != null)
		{
			if(descriptor.allowRequestLogging())
			{
				request = new HttpServletRequestWrapper(req, formEncoding);
			}
			else
			{
				request = req;
			}
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
    	if(descriptor != null && !descriptor.requestsFiltered())
    	{
    		//logs the request if specified in the descriptor
    		descriptor.doLogRequestInReplayLog(request);
    		
    		//map's the path if a mapping is specified in the descriptor
    		path = descriptor.mapPath(path);
    	}
		
    	//third, authenticate the user
		User user = authenticate(request,response);
		if (user == null) {
                        // You now get a challenge if there is no user
			//response.sendError(HttpServletResponse.SC_FORBIDDEN,
			//		"Permission denied: unknown user " + "or password");
			return;
		}
		
		//fourth, process the request
		DBBroker broker = null;
		try {
			broker = pool.get(user);

			//Route the request
			if(path.indexOf(SOAPServer.WEBSERVICE_MODULE_EXTENSION) > -1)
			{
				//SOAP Server
				srvSOAP.doPost(broker, request, response, path);
			}
			else
			{
				//REST Server
				srvREST.doPost(broker, request, response, path);
			}
            
		} catch (PermissionDeniedException e) {
			//If the current user is the Default User and they do not have permission
			//then send a challenge request to prompt the client for a username/password.
			//Else return a FORBIDDEN Error
			if (user.equals(defaultUser)) {
				authenticator.sendChallenge(request, response);
			} else {
				response
				.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
			}

		} catch (EXistException e) {
            if (response.isCommitted())
                throw new ServletException(e.getMessage(), e);
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e
					.getMessage());

		} catch (BadRequestException e) {
            if (response.isCommitted())
                throw new ServletException(e.getMessage(), e);
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());

		} catch (NotFoundException e) {
            if (response.isCommitted())
                throw new ServletException(e.getMessage(), e);
			response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());

        } catch (Throwable e){
            LOG.error(e);
            throw new ServletException("An unknown error occurred: " + e.getMessage(), e);
            
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
    
	private User authenticate(HttpServletRequest request,HttpServletResponse response)
          throws java.io.IOException
        {
		// First try to validate the principal if passed from the Servlet engine
		Principal principal = request.getUserPrincipal();
		
		if(principal instanceof XmldbPrincipal){
			String username = ((XmldbPrincipal)principal).getName();
			String password = ((XmldbPrincipal)principal).getPassword();
			
			LOG.info("Validating Principle: " + principal.getName());
			User user = pool.getSecurityManager().getUser(username);
			
			if (user != null){
				if (password.equalsIgnoreCase(user.getPassword())){
					LOG.info("Valid User: " + user.getName());
					return user;
				}else{
					LOG.info( "Password invalid for user: " + username );
				}
				LOG.info("User not found: " + principal.getName());
			}	
		}
		
		
		//Secondly try basic authentication
		String auth = request.getHeader("Authorization");
		if(auth == null && defaultUser!=null) {
                   return defaultUser;
		}
                return authenticator.authenticate(request,response);
                /*
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
                 */
	}
	
	private User getDefaultUser() {
		if (defaultUsername != null) {
			User user = pool.getSecurityManager().getUser(defaultUsername);
			if (user != null) {
				if (!user.validate(defaultPassword))
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
		LOG.info("configuring eXist instance");
		try {
			if ( !BrokerPool.isConfigured() )
				BrokerPool.configure( 1, 5, configuration );
		} catch ( EXistException e ) {
			throw new ServletException( e.getMessage(), e );
		} catch (DatabaseConfigurationException e) {
            throw new ServletException( e.getMessage(), e );
        }
        try {
			LOG.info("registering XMLDB driver");
			Class clazz = Class.forName("org.exist.xmldb.DatabaseImpl");
			Database database = (Database)clazz.newInstance();
			DatabaseManager.registerDatabase(database);
		} catch (ClassNotFoundException e) {
			LOG.info("ERROR", e);
		} catch (InstantiationException e) {
			LOG.info("ERROR", e);
		} catch (IllegalAccessException e) {
			LOG.info("ERROR", e);
		} catch (XMLDBException e) {
			LOG.info("ERROR", e);
		}
	}
}
