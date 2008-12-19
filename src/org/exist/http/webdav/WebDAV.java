/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.http.webdav;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.OutputKeys;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.http.servlets.Authenticator;
import org.exist.http.servlets.BasicAuthenticator;
import org.exist.http.servlets.DigestAuthenticator;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;

/**
 * The main class for processing WebDAV requests.
 *
 * @author wolf
 */
public class WebDAV {
    
    public final static String DAV_NS = "DAV:";
    
    // authentication methods
    public final static int BASIC_AUTH = 0;
    public final static int DIGEST_AUTH = 1;
    
    //	default content types
    public final static String BINARY_CONTENT = MimeType.BINARY_TYPE.getName();
    public final static String XML_CONTENT = MimeType.XML_TYPE.getName();
    /** id of the database registred against the BrokerPool */
    protected String databaseid = BrokerPool.DEFAULT_INSTANCE_NAME;
    
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

    private WebDAVMethodFactory factory;
    private int defaultAuthMethod;
    private Authenticator digestAuth;
    private Authenticator basicAuth;
    private BrokerPool pool;
    
    public WebDAV(int authenticationMethod, String id) throws ServletException {
       this(authenticationMethod,id,WebDAVMethodFactory.getInstance());
    }
    public WebDAV(int authenticationMethod, String id,WebDAVMethodFactory factory) throws ServletException {
       this.factory = factory;
        if (id != null && !"".equals(id)) this.databaseid=id;
        try {
            pool = BrokerPool.getInstance(this.databaseid);
        } catch (EXistException e) {
            throw new ServletException("Error found while initializing "
                    + "database: " + e.getMessage(), e);
        }
        defaultAuthMethod = authenticationMethod;
        digestAuth = new DigestAuthenticator(pool);
        basicAuth = new BasicAuthenticator(pool);
    }
    
    /**
     * Process a WebDAV request. The request is delegated to the corresponding
     * {@link WebDAVMethod} after authenticating the user.
     *
     * @param request           an HttpServletRequest object that contains
     *                          the request the client has made of the servlet
     * @param response          an HttpServletResponse object that contains the
     *                          response the servlet sends to the client
     * @throws ServletException if the request could not be handled
     * @throws IOException      if an input or output error is detected when
     *                          the servlet handles the request
     */
    public void process(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        User user = authenticate(request, response);
        if(user == null){
            // TODO Return error code ?
//            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, 
//                        "Please Supply credentials");
            return;
        }
            
        
        String path = request.getPathInfo();
        if(path == null || path.length() == 0 || path.equals("/")) {
            response.sendRedirect(request.getRequestURI() + DBBroker.ROOT_COLLECTION);
            return;
        }
        
        if(path.endsWith("/"))
            path = path.substring(0, path.length() - 1);
        
        if(LOG.isDebugEnabled()){
            LOG.debug("method='" + request.getMethod() + "'; path='" + path 
                    + "'; user='"+user.getName()
                    + "'; Lock-Token='" + request.getHeader("Lock-Token")
                    + "'; If='"+request.getHeader("If")+"'");
        }
        
        // for debugging webdav
        long start=System.currentTimeMillis();
                
        WebDAVMethod method = factory.create(request.getMethod(), pool);
        if(method == null) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    "Method is not supported: " + request.getMethod());
            return;
        }
        
        try {
        	method.process(user, request, response, XmldbURI.xmldbUriFor(path));
            
        } catch (URISyntaxException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());

        } catch (Throwable e){
            LOG.error(e);
            throw new ServletException("An error occurred: " + e.getMessage(), e);
            
        } finally {
            // for debugging webdav
            if(LOG.isDebugEnabled()){
                LOG.debug("Completed in "+(System.currentTimeMillis()-start)+" msecs.");
            }
        }
        	
    }
    
    private User authenticate(HttpServletRequest request, HttpServletResponse response)
    throws IOException {
        String credentials = request.getHeader("Authorization");
        if(credentials == null) {
            if(defaultAuthMethod == BASIC_AUTH)
                basicAuth.sendChallenge(request, response);
            else
                digestAuth.sendChallenge(request, response);
            return null;
        }
        
        if(credentials.toUpperCase().startsWith("DIGEST")) {
            return digestAuth.authenticate(request, response);
        } else {
            return basicAuth.authenticate(request, response);
        }
    }
}
