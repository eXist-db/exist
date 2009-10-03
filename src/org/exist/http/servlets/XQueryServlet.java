/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URISyntaxException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.transform.OutputKeys;

import org.apache.log4j.Logger;
import org.exist.http.Descriptor;
import org.exist.source.FileSource;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.xmldb.CollectionImpl;
import org.exist.xmldb.XQueryService;
import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.LocalResourceSet;
import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;
import org.exist.xquery.functions.request.RequestModule;
import org.exist.xquery.functions.response.ResponseModule;
import org.exist.xquery.functions.session.SessionModule;
import org.exist.xquery.util.HTTPUtils;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Item;
import org.exist.debuggee.Debuggee;
import org.exist.dom.XMLUtil;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 * Servlet to generate HTML output from an XQuery file.
 *
 * The servlet responds to an URL pattern as specified in the
 * WEB-INF/web.xml configuration file of the application. It will
 * interpret the path with which it is called as leading to a valid
 * XQuery file. The XQuery file is loaded, compiled and executed.
 * Any output of the script is sent back to the client.
 *
 * The servlet accepts the following initialization parameters in web.xml:
 *
 * <table border="0">
 * 	<tr><td>user</td><td>The user identity with which the script is executed.</td></tr>
 * 	<tr><td>password</td><td>Password for the user.</td></tr>
 * 	<tr><td>uri</td><td>A valid XML:DB URI leading to the root collection used to
 * 	process the request.</td></tr>
 * 	<tr><td>encoding</td><td>The character encoding used for XQuery files.</td></tr>
 * 	<tr><td>container-encoding</td><td>The character encoding used by the servlet
 * 	container.</td></tr>
 * 	<tr><td>form-encoding</td><td>The character encoding used by parameters posted
 * 	from HTML for
 * ms.</td></tr>
 * </table>
 *
 * User identity and password may also be specified through the HTTP session attributes
 * "user" and "password". These attributes will overwrite any other settings.
 *
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class XQueryServlet extends HttpServlet {
    
    private static final Logger LOG = Logger.getLogger(XQueryServlet.class);
    
    public final static String DEFAULT_USER = "guest";
    public final static String DEFAULT_PASS = "guest";
    public final static XmldbURI DEFAULT_URI = XmldbURI.EMBEDDED_SERVER_URI.append(XmldbURI.ROOT_COLLECTION_URI);
    public final static String DEFAULT_ENCODING = "UTF-8";
    public final static String DEFAULT_CONTENT_TYPE = "text/html";
    
    public final static String DRIVER = "org.exist.xmldb.DatabaseImpl";
    
    private String user = null;
    private String password = null;
    private XmldbURI collectionURI = null;
    
    private String containerEncoding = null;
    private String formEncoding = null;
    private String encoding = null;
    private String contentType = null;
    
    /* (non-Javadoc)
     * @see javax.servlet.GenericServlet#init(javax.servlet.ServletConfig)
     */
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        user = config.getInitParameter("user");
        if(user == null)
            user = DEFAULT_USER;
        password = config.getInitParameter("password");
        if(password == null)
            password = DEFAULT_PASS;
        String confCollectionURI = config.getInitParameter("uri");
        if(confCollectionURI == null) {
            collectionURI = DEFAULT_URI;
        } else {
            try {
                collectionURI = XmldbURI.xmldbUriFor(confCollectionURI);
            } catch (URISyntaxException e) {
                throw new ServletException("Invalid XmldbURI for parameter 'uri': "+e.getMessage(),e);
            }
        }
        formEncoding = config.getInitParameter("form-encoding");
        if(formEncoding == null)
            formEncoding = DEFAULT_ENCODING;
        LOG.info("form-encoding = " + formEncoding);
        containerEncoding = config.getInitParameter("container-encoding");
        if(containerEncoding == null)
            containerEncoding = DEFAULT_ENCODING;
        LOG.info("container-encoding = " + containerEncoding);
        encoding = config.getInitParameter("encoding");
        if(encoding == null)
            encoding = DEFAULT_ENCODING;
        LOG.info("encoding = " + encoding);
        contentType = config.getInitParameter("content-type");
        if(contentType == null)
            contentType = DEFAULT_CONTENT_TYPE;
        
        try {
            Class driver = Class.forName(DRIVER);
            Database database = (Database)driver.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
        } catch(Exception e) {
            String errorMessage="Failed to initialize database driver";
            LOG.error(errorMessage,e);
            throw new ServletException(errorMessage+": " + e.getMessage(), e);
        }
    }
    
    /* (non-Javadoc)
     * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        process(request, response);
    }
    
    /* (non-Javadoc)
     * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    protected void doPost(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
        HttpServletRequest request = null;
        
        //For POST request, If we are logging the requests we must wrap HttpServletRequest in HttpServletRequestWrapper
        //otherwise we cannot access the POST parameters from the content body of the request!!! - deliriumsky
        Descriptor descriptor = Descriptor.getDescriptorSingleton();
        if(descriptor != null) {
            if(descriptor.allowRequestLogging()) {
                request = new HttpServletRequestWrapper(req, formEncoding);
            } else {
                request = req;
            }
        } else {
            request = req;
        }
        
        process(request, response);
    }
    
    //-------------------------------
    // doPut and doDelete added by Andrzej Taramina (andrzej@chaeron.com)
    // Date: Sept/05/2007
    //
    // These methods were added so that you can issue an HTTP PUT or DELETE request and have it serviced by an XQuery.
    // NOTE: The XQuery referenced in the target URL of the request will be executed and the PUT/DELETE request will be passed to it
    //
    //-------------------------------
    
     /* (non-Javadoc)
     * @see javax.servlet.http.HttpServlet#doPut(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    protected void doPut(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
        HttpServletRequest request = null;
        
        //For POST request, If we are logging the requests we must wrap HttpServletRequest in HttpServletRequestWrapper
        //otherwise we cannot access the POST parameters from the content body of the request!!! - deliriumsky
        Descriptor descriptor = Descriptor.getDescriptorSingleton();
        if(descriptor != null) {
            if(descriptor.allowRequestLogging()) {
                request = new HttpServletRequestWrapper(req, formEncoding);
            } else {
                request = req;
            }
        } else {
            request = req;
        }
        
        process(request, response);
    }
    
    
    /* (non-Javadoc)
     * @see javax.servlet.http.HttpServlet#doDelete(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        process(request, response);
    }
    
    
    /**
     * Processes incoming HTTP requests for XQuery
     */
    protected void process(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        //first, adjust the path
        String path = request.getPathTranslated();
        if(path == null) {
            path = request.getRequestURI().substring(request.getContextPath().length());
            int p = path.lastIndexOf(';');
            if(p != Constants.STRING_NOT_FOUND)
                path = path.substring(0, p);
            path = getServletContext().getRealPath(path);
        }
        
        //second, perform descriptor actions
        Descriptor descriptor = Descriptor.getDescriptorSingleton();
        if(descriptor != null && !descriptor.requestsFiltered()) {
            //logs the request if specified in the descriptor
            descriptor.doLogRequestInReplayLog(request);
            
            //map's the path if a mapping is specified in the descriptor
            path = descriptor.mapPath(path);
        }
        
        
        if (request.getCharacterEncoding() == null)
            try {
                request.setCharacterEncoding(formEncoding);
            } catch (IllegalStateException e) {
            }
        ServletOutputStream sout = response.getOutputStream();
        PrintWriter output = new PrintWriter(new OutputStreamWriter(sout, formEncoding));
        response.setContentType(contentType + "; charset=" + formEncoding);
        response.addHeader( "pragma", "no-cache" );
        response.addHeader( "Cache-Control", "no-cache" );

        Source source;
        Object sourceAttrib = request.getAttribute("xquery.source");
        if (sourceAttrib != null) {
            String s;
            if (sourceAttrib instanceof Item)
                try {
                    s = ((Item) sourceAttrib).getStringValue();
                } catch (XPathException e) {
                    throw new ServletException("Failed to read XQuery source string from " +
                        "request attribute 'xquery.source': " + e.getMessage(), e);
                }
            else
                s = sourceAttrib.toString();
            source = new StringSource(s);
        } else {
            File f = new File(path);
            if(!f.canRead()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                sendError(output, "Cannot read source file", path);
                return;
            }
            source = new FileSource(f, encoding, true);
        }

        boolean reportErrors = false;
        String errorOpt = (String) request.getAttribute("xquery.report-errors");
        if (errorOpt != null)
            reportErrors = errorOpt.equalsIgnoreCase("YES");
        
        //allow source viewing for GET?
        if(request.getMethod().toUpperCase().equals("GET")) {
            String option;
            boolean allowSource = false;
            if((option = request.getParameter("_source")) != null)
                allowSource = option.equals("yes");
            
            //Should we display the source of the XQuery or execute it
            if(allowSource && descriptor != null) {
                //show the source
                
                //check are we allowed to show the xquery source - descriptor.xml
//                System.out.println("path="+path);
                if(descriptor.allowSource(path)) {
                    //Show the source of the XQuery
                    //writeResourceAs(resource, broker, stylesheet, encoding, "text/plain", outputProperties, response);
                    response.setContentType("text/plain;charset=" + formEncoding);
                    output.write(source.getContent());
                    output.flush();
                    return;
                } else {
                   
                   response.sendError(HttpServletResponse.SC_FORBIDDEN, "Permission to view XQuery source for: " + path + " denied. Must be explicitly defined in descriptor.xml");
                   return;
                }
            }
        }
        
        
        //-------------------------------
        // Added by Igor Abade (igoravl@cosespseguros.com.br)
        // Date: Aug/06/2004
        //-------------------------------
        
        String contentType = this.contentType;
        try {
            contentType = getServletContext().getMimeType(path);
            if (contentType == null)
                contentType = this.contentType;
        } catch (Throwable e) {
            contentType = this.contentType;
        } finally {
            if (contentType.startsWith("text/") || (contentType.endsWith("+xml")))
                contentType += ";charset=" + formEncoding;
            response.setContentType(contentType );
        }
        
        //-------------------------------
        
//        URI baseUri;
//        try {
//            baseUri = new URI(request.getScheme(),
//                    null/*user info?*/, request.getLocalName(), request.getLocalPort(),
//                    request.getRequestURI(), null, null);
//        } catch(URISyntaxException e) {
//            baseUri = null;
//        }
        
        String requestPath = request.getRequestURI();
        int p = requestPath.lastIndexOf("/");
        if(p != Constants.STRING_NOT_FOUND)
            requestPath = requestPath.substring(0, p);
        String moduleLoadPath = getServletContext().getRealPath(requestPath.substring(request.getContextPath().length()));
        String actualUser = null;
        String actualPassword = null;
        HttpSession session = request.getSession( false );
        if(session != null && request.isRequestedSessionIdValid()) {
            actualUser = getSessionAttribute(session, "user");
            actualPassword = getSessionAttribute(session, "password");
        }
        if(actualUser == null) actualUser = user;
        if(actualPassword == null) actualPassword = password;

        String requestAttr = (String) request.getAttribute("xquery.attribute");

        try {
            Collection collection = DatabaseManager.getCollection(collectionURI.toString(), actualUser, actualPassword);
            XQueryService service = (XQueryService)
            collection.getService("XQueryService", "1.0");
            service.setProperty("base-uri", collectionURI.toString());
            service.setModuleLoadPath(moduleLoadPath);
            //service.setNamespace(prefix, RequestModule.NAMESPACE_URI);
            if(!((CollectionImpl)collection).isRemoteCollection()) {
                service.declareVariable(RequestModule.PREFIX + ":request", new HttpRequestWrapper(request, formEncoding, containerEncoding));
                service.declareVariable(ResponseModule.PREFIX + ":response", new HttpResponseWrapper(response));
                service.declareVariable(SessionModule.PREFIX + ":session", ( session != null ? new HttpSessionWrapper( session ) : null ) );
            }

            //if get "start new session" request
    		String xdebug = request.getParameter("XDEBUG_SESSION_START");
    		if (xdebug != null)
    			service.declareVariable(Debuggee.PREFIX+":session",  xdebug);
    		else {
    			//if have session
    			xdebug = request.getParameter("XDEBUG_SESSION");
    			if (xdebug != null) {
    				service.declareVariable(Debuggee.PREFIX+":session",  xdebug);
    			} else {
    				//looking for session in cookies (FF XDebug Helper add-ons)
        			Cookie[] cookies = request.getCookies();
        			if (cookies != null) {
            			for (int i = 0; i < cookies.length; i++) {
            				if (cookies[i].getName().equals("XDEBUG_SESSION")) {
            					//TODO: check for value?? ("eXistDB_XDebug" ? or leave "default") -shabanovd 
                				service.declareVariable(Debuggee.PREFIX+":session",  cookies[i].getValue());
                				break;
            				}
            			}
        			}
    			}
    		}
            

            ResourceSet result = service.execute(source);
            String mediaType = service.getProperty(OutputKeys.MEDIA_TYPE);
            if (mediaType != null) {
                if (!response.isCommitted())
                    response.setContentType(mediaType + "; charset=" + formEncoding);
            }
            if (requestAttr != null && !((CollectionImpl)collection).isRemoteCollection()) {
                request.setAttribute(requestAttr, ((LocalResourceSet)result).toSequence());
            } else {
                for(ResourceIterator i = result.getIterator(); i.hasMoreResources(); ) {
                    Resource res = i.nextResource();
                    output.println(res.getContent().toString());
                }
            }

        } catch (XMLDBException e) {
            LOG.debug(e.getMessage(), e);
            if (reportErrors)
                writeError(output, e);
            else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                sendError(output, e.getMessage(), e);
            }
        } catch (Throwable e){
            LOG.error(e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            sendError(output, "Error", e.getMessage());
        }

        output.flush();
    }
    
    private String getSessionAttribute(HttpSession session, String attribute) {
        Object obj = session.getAttribute(attribute);
        if(obj == null)
            return null;
        if(obj instanceof Sequence)
            try {
                return ((Sequence)obj).getStringValue();
            } catch (XPathException e) {
                return null;
            }
        return obj.toString();
    }
    
    private void sendError(PrintWriter out, String message, XMLDBException e) {
        out.print("<html><head>");
        out.print("<title>XQueryServlet Error</title>");
        out.print("<link rel=\"stylesheet\" type=\"text/css\" href=\"error.css\"></link></head>");
        out.print("<body><div id=\"container\"><h1>Error found</h1>");
        Throwable t = e.getCause();
        if (t instanceof XPathException) {
            XPathException xe = (XPathException) t;
            out.println(xe.getMessageAsHTML());
        } else {
            out.print("<h2>Message:");
            out.print(message);
            out.print("</h2>");
        }
        
        if(t!=null){
            // t can be null
            out.print(HTTPUtils.printStackTraceHTML(t));
        }
        
        
        out.print("</div></body></html>");
    }

    private void writeError(PrintWriter out, XMLDBException e) {
        out.print("<error>");
        Throwable t = e.getCause();
        if (t != null)
            out.print(XMLUtil.encodeAttrMarkup(t.getMessage()));
        else
            out.print(XMLUtil.encodeAttrMarkup(e.getMessage()));
        out.println("</error>");
    }

    private void sendError(PrintWriter out, String message, String description) {
        out.print("<html><head>");
        out.print("<title>XQueryServlet Error</title>");
        out.print("<link rel=\"stylesheet\" type=\"text/css\" href=\"error.css\"></link></head>");
        out.println("<body><h1>Error found</h1>");
        out.print("<div class='message'><b>Message: </b>");
        out.print(message);
        out.print("</div><div class='description'>");
        out.print(description);
        out.print("</div></body></html>");
        out.flush();
    }
    
    // -jmvanel : never used locally
    
//	private static final class CachedQuery {
//
//		long lastModified;
//		String sourcePath;
//		CompiledExpression expression;
//
//		public CachedQuery(File sourceFile, CompiledExpression expression) {
//			this.sourcePath = sourceFile.getAbsolutePath();
//			this.lastModified = sourceFile.lastModified();
//			this.expression = expression;
//		}
//
//		public boolean isValid() {
//			File f = new File(sourcePath);
//			if(f.lastModified() > lastModified)
//				return false;
//			return true;
//		}
//
//		public CompiledExpression getExpression() {
//			return expression;
//		}
//	}
    
}
