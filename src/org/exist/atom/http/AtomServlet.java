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
 */
package org.exist.atom.http;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.atom.AtomModule;
import org.exist.atom.modules.AtomFeeds;
import org.exist.atom.modules.AtomProtocol;
import org.exist.atom.modules.Introspect;
import org.exist.atom.modules.Query;
import org.exist.atom.modules.Topics;
import org.exist.http.BadRequestException;
import org.exist.http.NotFoundException;
import org.exist.http.servlets.Authenticator;
import org.exist.http.servlets.BasicAuthenticator;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.User;
import org.exist.security.XmldbPrincipal;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.validation.XmlLibraryChecker;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;

/**
 * Implements a rest interface for exist collections as atom feeds
 * 
 * @author Alex Milowski
 */
public class AtomServlet extends HttpServlet {

   public final static String DEFAULT_ENCODING = "UTF-8";

   protected final static Logger LOG = Logger.getLogger(AtomServlet.class);
   
   class ModuleContext implements AtomModule.Context {
      ServletConfig config;
      ModuleContext(ServletConfig config,String subpath) {
         this.config = config;
      }
      
      public String getDefaultCharset() {
         return formEncoding;
      }
      
      public String getParameter(String name) {
         return config.getInitParameter(name);
      }
      
      public String getContextPath() {
         // TODO: finish
         return null;
      }
      
      public URL getContextURL() {
         // TODO: finish
         return null;
      }
   }
   
   // What I want...
   //private Map<String,AtomModule> modules;
   private Map modules;
   
   private String formEncoding = null;
   private BrokerPool pool = null;
   private String defaultUsername = SecurityManager.GUEST_USER;
   private String defaultPassword = SecurityManager.GUEST_USER;
	
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
             
             if (confFile == null) {
                confFile = "conf.xml";
             }
             dbHome = (dbHome == null) ? config.getServletContext().getRealPath(".") : 
                                         config.getServletContext().getRealPath(dbHome);
             LOG.info("AtomServlet: exist.home=" + dbHome);
             System.setProperty("exist.home", dbHome);
             
             File f = new File(dbHome + File.separator + confFile);
             LOG.info("reading configuration from " + f.getAbsolutePath());
             
             if (!f.canRead()) {
                throw new ServletException("configuration file " + confFile
                        + " not found or not readable");
             }
             Configuration configuration = new Configuration(confFile, dbHome);
             if (start != null && start.equals("true")) {
                startup(configuration);
             }
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
       if (formEncoding == null) {
          formEncoding = DEFAULT_ENCODING;
       }
       String containerEncoding = config.getInitParameter("container-encoding");
       if (containerEncoding == null) {
          containerEncoding = DEFAULT_ENCODING;
       }


       //modules = new HashMap<String,AtomModule>();
       modules = new HashMap();
       AtomProtocol protocol = new AtomProtocol();
       modules.put("edit",protocol);
       protocol.init(new ModuleContext(config,"edit"));
       AtomFeeds feeds = new AtomFeeds();
       modules.put("content",feeds);
       feeds.init(new ModuleContext(config,"content"));
       Topics topics = new Topics();
       modules.put("topic",topics);
       topics.init(new ModuleContext(config,"topic"));
       Query query = new Query();
       modules.put("query",query);
       query.init(new ModuleContext(config,"query"));
       Introspect introspect = new Introspect();
       modules.put("introspect",introspect);
       introspect.init(new ModuleContext(config,"introspect"));
       
       
       // XML lib checks....
       if( XmlLibraryChecker.isXercesVersionOK() ){
          LOG.info("Detected "+ XmlLibraryChecker.XERCESVERSION + ", OK.");
          
       } else {
          LOG.warn("eXist requires '"+ XmlLibraryChecker.XERCESVERSION
                  + "' but detected '"+ XmlLibraryChecker.getXercesVersion()
                  +"'. Please add the correct version to the "
                  +"class-path, e.g. in the 'endorsed' folder of "
                  +"the servlet container or in the 'endorsed' folder "
                  +"of the JRE.");
       }
       
       if( XmlLibraryChecker.isXalanVersionOK() ){
          LOG.info("Detected "+ XmlLibraryChecker.XALANVERSION + ", OK.");
          
       } else {
          LOG.warn("eXist requires '"+ XmlLibraryChecker.XALANVERSION
                  + "' but detected '"+ XmlLibraryChecker.getXalanVersion()
                  +"'. Please add the correct version to the "
                  +"class-path, e.g. in the 'endorsed' folder of "
                  +"the servlet container or in the 'endorsed' folder "
                  +"of the JRE.");
       }
    }
    
   protected void service(HttpServletRequest request, HttpServletResponse response) 
      throws ServletException
   {
      try {
         // Get the path
         String path = request.getPathInfo();

         // Authenticate
         User user = authenticate(request,response);
         if (user == null) {
            // You now get a challenge if there is no user
            return;
         }

         int firstSlash = path.indexOf('/',1);
         if (firstSlash<0 && path.length()==1) {
            response.sendError(400,"Module not specified.");
            return;
         }
         String moduleName = firstSlash<0 ? path.substring(1) : path.substring(1,firstSlash);
         path = firstSlash<0 ? "" : path.substring(firstSlash);

         AtomModule module = (AtomModule)modules.get(moduleName);
         if (module==null) {
            response.sendError(400,"Module "+moduleName+" not found.");
            return;
         }
         // Handle the resource
         DBBroker broker = null;
         try {
            broker = pool.get(user);
            module.process(broker,new HttpRequestMessage(request,path,'/'+moduleName),new HttpResponseMessage(response));
         } catch (NotFoundException ex) {
            LOG.info("Resource "+path+" not found by "+moduleName,ex);
            response.sendError(404,ex.getMessage());
         } catch (PermissionDeniedException ex) {
            LOG.info("Permission denied to "+path+" by "+moduleName+" for "+user.getName(),ex);
            response.sendError(401,ex.getMessage());
         } catch (BadRequestException ex) {
            LOG.info("Bad request throw from module "+moduleName,ex);
            response.sendError(400,ex.getMessage());
         } catch (EXistException ex) {
            LOG.fatal("Exception getting broker from pool for user "+user.getName(),ex);
            response.sendError(500,"Service is not available.");
         } finally {
            pool.release(broker);
         }
      } catch (IOException ex) {
         LOG.fatal("I/O exception on request.",ex);
         try {
            response.sendError(500,"Service is not available.");
         } catch (IOException finalEx) {
            LOG.fatal("Cannot return 500 on exception.",ex);
         }
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
      // First try to validate the principial if passed from the servlet engine
      Principal principal = request.getUserPrincipal();
      
      if (principal instanceof XmldbPrincipal){
         String username = ((XmldbPrincipal)principal).getName();
         String password = ((XmldbPrincipal)principal).getPassword();
         
         LOG.info("Validating Principle: " + principal.getName());
         User user = pool.getSecurityManager().getUser(username);
         
         if (user != null){
            if (password.equalsIgnoreCase(user.getPassword())){
               LOG.info("Valid User: " + user.getName());
               return user;
            } else {
               LOG.info( "Password invalid for user: " + username );
            }
            LOG.info("User not found: " + principal.getName());
         }
      }
      
      String auth = request.getHeader("Authorization");
      if (auth == null && defaultUser!=null) {
         return defaultUser;
      }
      return authenticator.authenticate(request,response);
   }
	
   private User getDefaultUser() {
      if (defaultUsername != null) {
         User user = pool.getSecurityManager().getUser(defaultUsername);
         if (user != null) {
            if (!user.validate(defaultPassword)) {
               return null;
            }
         }
         return user;
      }
      return null;
   }
	
   private void startup(Configuration configuration) 
      throws ServletException 
   {
      if ( configuration == null ) {
         throw new ServletException( "database has not been configured" );
      }
      LOG.info("configuring eXist instance");
      try {
         if ( !BrokerPool.isConfigured() ) {
            BrokerPool.configure( 1, 5, configuration );
         }
      } catch ( EXistException e ) {
         throw new ServletException( e.getMessage() );
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
