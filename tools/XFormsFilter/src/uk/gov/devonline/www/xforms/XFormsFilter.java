/*
 *  XFormsFilter
 *  Copyright (C) 2006 Adam Retter, Devon Portal Project <adam.retter@devon.gov.uk>
 *  www.devonline.gov.uk
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

package uk.gov.devonline.www.xforms;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.Integer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.xml.transform.TransformerFactory;

import org.apache.commons.httpclient.Cookie;
import org.apache.log4j.Logger;

import org.chiba.adapter.ChibaAdapter;
import org.chiba.adapter.ChibaEvent;
import org.chiba.adapter.DefaultChibaEventImpl;
import org.chiba.adapter.ui.UIGenerator;
import org.chiba.adapter.ui.XSLTGenerator;
import org.chiba.web.WebAdapter;
import org.chiba.web.servlet.ChibaServlet;
import org.chiba.web.servlet.HttpRequestHandler;
import org.chiba.web.servlet.ServletAdapter;
import org.chiba.web.session.XFormsSession;
import org.chiba.web.session.XFormsSessionManager;
import org.chiba.web.session.impl.DefaultXFormsSessionManagerImpl;
import org.chiba.xml.events.ChibaEventNames;
import org.chiba.xml.events.XMLEvent;
import org.chiba.xml.xforms.config.Config;
import org.chiba.xml.xforms.config.XFormsConfigException;
import org.chiba.xml.xforms.connector.http.AbstractHTTPConnector;
import org.chiba.xml.xforms.exception.XFormsException;
import org.chiba.xml.xslt.TransformerService;
import org.chiba.xml.xslt.impl.CachingTransformerService;
import org.chiba.xml.xslt.impl.FileResourceResolver;

/**
 * A Servlet Filter to provide XForms functionality to existing Servlets
 * 
 * Currently borrows heavily from org.chiba.web.servlet.* in particular ChibaServlet
 * 
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @version 1.3
 * @serial 2007-02-19T13:51
 * 
 * 
 * Initialization parameters for the ServletFilter
 * 
 * chiba.debug							true or false indicating if Chiba should run in debug mode
 * chiba.config							Path to Chiba's configuration file
 * chiba.upload							Path to upload temporary files to
 * chiba.xslt.cache						true or false indicating if Chiba should Cache XSLT
 * chiba.web.xslt.path					Path to XSLT location
 * chiba.web.xslt.default				XSLT file in XSLT_STYLESHEET_PATH to use for processing
 * chiba.CSSPath						Path to xforms.css to use, relevant to servlet context
 * chiba.XFormsSessionChecking			Period in milliseconds that XForms sessions should be checked
 * chiba.XFormsSessionTimeout			Period in milliseconds of XForms session expiry when inactive
 */
public class XFormsFilter implements Filter
{
	protected final static Logger LOG = Logger.getLogger(XFormsFilter.class);
	
	private FilterConfig filterConfig = null;
	
	private final static boolean DEFAULT_CHIBA_DEBUG = true;
	private final static String DEFAULT_CHIBA_CONFIG = "/eXist/tools/XFormsFilter/chiba.default.xml";
	private final static String DEFAULT_CHIBA_TEMP_UPLOAD_DESTINATION = "/tmp";
	private final static boolean DEFAULT_CHIBA_XSLT_CACHE = true;
	private final static String DEFAULT_CHIBA_STYLESHEET_PATH = "/exist/tools/XFormsFilter/xslt";
	private final static String DEFAULT_CHIBA_STYLESHEET_FILE = "xhtml.xsl";
	private final static String DEFAULT_CHIBA_CSS_PATH = "/servlet/db/system/xformsfilter";
	private final static int DEFAULT_CHIBA_XFORMS_SESSION_CHECKING = 300000;
	private final static int DEFAULT_CHIBA_XFORMS_SESSION_TIMEOUT = 1200000;
	
	private boolean CHIBA_DEBUG = DEFAULT_CHIBA_DEBUG;
	private String CHIBA_CONFIG = DEFAULT_CHIBA_CONFIG;
	private String CHIBA_TEMP_UPLOAD_DESTINATION = DEFAULT_CHIBA_TEMP_UPLOAD_DESTINATION;
	private boolean CHIBA_XSLT_CACHE = DEFAULT_CHIBA_XSLT_CACHE;
	private String CHIBA_STYLESHEET_PATH = DEFAULT_CHIBA_STYLESHEET_PATH;
	private String CHIBA_STYLESHEET_FILE = DEFAULT_CHIBA_STYLESHEET_FILE;
	private String CHIBA_CSS_PATH = DEFAULT_CHIBA_CSS_PATH;
	private int CHIBA_XFORMS_SESSION_CHECKING = DEFAULT_CHIBA_XFORMS_SESSION_CHECKING;
	private int CHIBA_XFORMS_SESSION_TIMEOUT = DEFAULT_CHIBA_XFORMS_SESSION_TIMEOUT;
	
	
	private static final String XFORMS_NS = "http://www.w3.org/2002/xforms";
	private static final String HTML_CONTENT_TYPE = "text/html;charset=UTF-8";
	
	private final static String[] CHIBA_QUERYSTRING_PARAMS = {
		"form",
		"xslt",
		"action_url",
		"JavaScript"
	};
	
	
	/**
	 * Filter initialisation
	 * 
	 * @see http://java.sun.com/j2ee/sdk_1.3/techdocs/api/javax/servlet/Filter.html#init(javax.servlet.FilterConfig)
	 */
	public void init(FilterConfig filterConfig) throws ServletException
	{
		this.filterConfig = filterConfig;
		
		setupConfig();
		
		setupTransformerService();
		
		createXFormsSessionManager();
	}
	
	/**
	 * Filter shutdown
	 * 
	 * @see http://java.sun.com/j2ee/sdk_1.3/techdocs/api/javax/servlet/Filter.html#destroy()
	 */
	public void destroy()
	{
		// TODO Auto-generated method stub
	}

	/**
	 * Set's up variables from the filter configuration
	 */
	private void setupConfig()
	{
		if(filterConfig.getInitParameter("chiba.debug") != null)
		{
			CHIBA_DEBUG = filterConfig.getInitParameter("chiba.debug").toLowerCase().equals("true");
		}
		
		if(filterConfig.getInitParameter("chiba.config") != null)
		{
			CHIBA_CONFIG = filterConfig.getInitParameter("chiba.config");
		}
		
		if(filterConfig.getInitParameter("chiba.upload") != null)
		{
			CHIBA_TEMP_UPLOAD_DESTINATION = filterConfig.getInitParameter("chiba.upload");
		}
		
		if(filterConfig.getInitParameter("chiba.xslt.cache") != null)
		{
			CHIBA_XSLT_CACHE = filterConfig.getInitParameter("chiba.xslt.cache").toLowerCase().equals("true");
		}
		
		if(filterConfig.getInitParameter("chiba.web.xslt.path") != null)
		{
			CHIBA_STYLESHEET_PATH = filterConfig.getInitParameter("chiba.web.xslt.path");
		}
		
		if(filterConfig.getInitParameter("chiba.web.xslt.default") != null)
		{
			CHIBA_STYLESHEET_FILE = filterConfig.getInitParameter("chiba.web.xslt.default");
		}
		
		if(filterConfig.getInitParameter("chiba.CSSPath") != null)
		{
			CHIBA_CSS_PATH = filterConfig.getInitParameter("chiba.CSSPath");
		}
		
		if(filterConfig.getInitParameter("chiba.XFormsSessionChecking") != null)
		{
			CHIBA_XFORMS_SESSION_CHECKING = Integer.parseInt(filterConfig.getInitParameter("chiba.XFormsSessionChecking"));
		}
		
		if(filterConfig.getInitParameter("chiba.XFormsSessionTimeout") != null)
		{
			CHIBA_XFORMS_SESSION_TIMEOUT = Integer.parseInt(filterConfig.getInitParameter("chiba.XFormsSessionTimeout"));
		}
	}
	
	private void setupTransformerService() throws ServletException
	{
		TransformerService transformerService = new CachingTransformerService(new FileResourceResolver());
		transformerService.setTransformerFactory(TransformerFactory.newInstance());
		
		if(CHIBA_XSLT_CACHE)
		{
			LOG.debug("initializing xslt cache");

            // load default stylesheet
            // todo: extract parameter names
			try
			{
				URI uri = new File(CHIBA_STYLESHEET_PATH).toURI().resolve(new URI(CHIBA_STYLESHEET_FILE));
				transformerService.getTransformer(uri);
			}
			catch(Exception e)
			{
				throw new ServletException(e);
			}
        }

        // store service in servlet context
        // todo: contemplate about transformer service thread-safety
		filterConfig.getServletContext().setAttribute(TransformerService.class.getName(), transformerService);
	}
	
	private void createXFormsSessionManager()
	{
		XFormsSessionManager manager = DefaultXFormsSessionManagerImpl.getInstance();

		manager.setInterval(CHIBA_XFORMS_SESSION_CHECKING);        
		manager.setTimeout(CHIBA_XFORMS_SESSION_TIMEOUT);

        //start running the session cleanup
        manager.init();
	}
	
	/**
	 * The actual filtering method
	 * 
	 * @see http://java.sun.com/j2ee/sdk_1.3/techdocs/api/javax/servlet/Filter.html#doFilter(javax.servlet.ServletRequest,%20javax.servlet.ServletResponse,%20javax.servlet.FilterChain)
	 */
	public void doFilter(ServletRequest srvRequest, ServletResponse srvResponse, FilterChain filterChain) throws IOException, ServletException
	{
		
		/* before servlet request */
		if(isXFormUpdateRequest(srvRequest))
		{
			LOG.info("Start Update XForm");
			updateXForm(srvRequest, srvResponse);
			LOG.info("End Update XForm");
		}
		else
		{
		
			/* do servlet request */
			LOG.info("Passing to Chain");
			BufferedHttpServletResponseWrapper bufResponse = new BufferedHttpServletResponseWrapper((HttpServletResponse)srvResponse);
			filterChain.doFilter(srvRequest, bufResponse);
			LOG.info("Returned from Chain");
			
			/* after servlet request */		
			if(hasXForm(bufResponse))
			{
				bufResponse.getOutputStream().close();
				LOG.info("Start Render XForm");
				
				//remove DOCTYPE PI if it exists, Xerces in Chiba otherwise may try to download the system DTD (can cause latency problems)
				byte[] data = removeDocumentTypePI(bufResponse.getData());
				//correct the <xforms:instance> xmlns="" problem (workaround for namespace problems in eXist)
				data = correctInstanceXMLNS(data);
				
				renderXForm(new ByteArrayInputStream(data), srvRequest, srvResponse);
				LOG.info("End Render XForm");
			}
			else
			{
				srvResponse.getOutputStream().write(bufResponse.getData());
				srvResponse.getOutputStream().close();
			}
		}
	}
	
	/**
	 * Removes the DOCTYPE Processing Instruction from the content if it exists
	 * 
	 * @param content	The HTML page content
	 * 
	 * @return	The content without the DOCTYPE PI 
	 */
	public byte[] removeDocumentTypePI(byte[] content)
	{
		String buf = new String(content);
		
		int iStartDoctype = buf.indexOf("<!DOCTYPE");
		if(iStartDoctype > -1)
		{
			int iEndDoctype = buf.indexOf('>', iStartDoctype);
			
			String newBuf = buf.substring(0, iStartDoctype - 1);
			newBuf += buf.substring(iEndDoctype + 1);
			return newBuf.getBytes();
		}
		return content;
	}
	
	/**
	 * Inserts the attribute xmlns="" on the xforms:instance node if it is missing
	 * 
	 * @param content	The HTML page content
	 * 
	 * @return	The content with the corrected xforms:instance 
	 */
	public byte[] correctInstanceXMLNS(byte[] content)
	{
		String buf = new String(content);
		if(buf.indexOf("<xforms:instance xmlns=\"\">") == -1)
		{
			String newBuf = buf.replace("<xforms:instance>", "<xforms:instance xmlns=\"\">");
			return newBuf.getBytes();
		}
		
		return content;
	}
	
	/**
	 * Checks if the request is to update an XForm
	 * 
	 * @param srvRequest	The request
	 * 
	 * @return true if the request is to update an XForm, false otherwise 
	 */
	public boolean isXFormUpdateRequest(ServletRequest srvRequest)
	{
		//get the http request object
		HttpServletRequest request = (HttpServletRequest)srvRequest;
	
		//must be a POST request
		if(!request.getMethod().equals("POST"))
			return false;
		
		//get the Chiba Adapter from the session
		String key = request.getParameter("sessionKey"); 
		if(key == null)
			return false;
		
		XFormsSessionManager manager = (XFormsSessionManager)request.getSession().getAttribute(XFormsSessionManager.XFORMS_SESSION_MANAGER);
		XFormsSession xFormsSession = manager.getXFormsSession(key);
		if(xFormsSession == null)
			return false;
		
		//ChibaAdapter adapter = (ChibaAdapter)request.getSession().getAttribute(SESSION_CHIBA_ADAPTER);
		WebAdapter adapter = xFormsSession.getAdapter();
		if(adapter == null)
			return false;
		
		String actionURL;
    	if(request.getQueryString() != null)
    	{
    		actionURL = request.getRequestURL() + "?" + request.getQueryString(); 
    	}
    	else
    	{
    		actionURL = request.getRequestURL().toString();
    	}
    	
    	//remove the sessionKey (if any) before comparing the action URL
    	int posSessionKey = actionURL.indexOf("sessionKey");
    	if(posSessionKey > -1)
    	{
    		char preSep = actionURL.charAt(posSessionKey - 1);
    		if(preSep == '?')
    		{
    			if(actionURL.indexOf('&') > -1)
    			{
    				actionURL = actionURL.substring(0, posSessionKey) + actionURL.substring(actionURL.indexOf('&') + 1);
    			}
    			else
    			{
    				actionURL = actionURL.substring(0, posSessionKey - 1);
    			}
    		}
    		else if(preSep == '&')
    		{
    			actionURL = actionURL.substring(0, posSessionKey - 1);
    		}
    	}
    	
    	//if the action-url in the adapters context param is the same as that of the action url then we know we are updating
    	return(adapter.getContextParam("action-url").equals(actionURL));
	}
	
	/**
	 * Checks if the response contains an XForm
	 * 
	 * @param bufResponse	The buffered response
	 * 
	 * @return true if the response contains an XForm, false otherwise 
	 */
	public boolean hasXForm(BufferedHttpServletResponseWrapper bufResponse)
	{
		String strResponse = bufResponse.getDataAsString(); 
		
		//find the xforms namespace local name
		int xfNSDeclEnd = strResponse.indexOf("=\"" + XFORMS_NS + "\"");
		if(xfNSDeclEnd != -1)
		{
			String temp = strResponse.substring(0, xfNSDeclEnd);
			int xfNSDeclStart = temp.lastIndexOf(':') + 1;
			String xfNSLocal = temp.substring(xfNSDeclStart);
			
			//check for xforms model and instance elements
			if(strResponse.contains('<' + xfNSLocal + ":model") && strResponse.contains('<' + xfNSLocal + ":instance"))
			{
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Renders an XForm described in bufServletResponseWrapper using the Chiba XForms Engine
	 * 
	 *  @param srvRequest	The Servlet request object
	 *  @param bufServletResponseWrapper	The response from the Servlet
	 */
	public void renderXForm(InputStream isXForm, ServletRequest srvRequest, ServletResponse srvResponse)
	{
		HttpServletRequest request = (HttpServletRequest)srvRequest;
		HttpServletResponse response = (HttpServletResponse)srvResponse;
		HttpSession session = request.getSession(true);
		WebAdapter adapter = null;
		
		/*
        the XFormsSessionManager is kept in the http-session though it is accessible as singleton. Subsequent
        servlets should access the manager through the http-session attribute as below to ensure the http-session
        is refreshed.
        */
		XFormsSessionManager sessionManager = getXFormsSessionManager();
        XFormsSession xFormsSession = sessionManager.createXFormsSession();
        
        session.setAttribute(XFormsSessionManager.XFORMS_SESSION_MANAGER, sessionManager);
        LOG.debug("Created XFormsSession with key: " + xFormsSession.getKey());
		
        //Dont set in filter -> is set by servlet!
        /*
        request.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control","private, no-store,  no-cache, must-revalidate");
        response.setHeader("Pragma","no-cache");
        response.setDateHeader("Expires",-1);
        */
        
    	try
    	{
    		//get the base URI
    		String baseURI = request.getRequestURL().toString();
    		
    		//get the action URL
    		String actionURL;
    		if(request.getQueryString() != null)
    		{
    			actionURL = request.getRequestURL() + "?" + request.getQueryString();
    		}
    		else
    		{
    			actionURL = request.getRequestURL().toString();
    		}
    		
    		//new chiba adapter (ServletAdapter for HTML/XForms | FluxAdapter for AJAX/XForms)
    		adapter = new ServletAdapter();
    		adapter = setupAdapter(adapter, xFormsSession, baseURI, isXForm);
    		setContextParams(request, adapter);
    		storeCookies(request, adapter);
    		storeAcceptLanguage(request, adapter);
    		
    		//set some parameters for checking on POST
    		adapter.setContextParam("action-url", actionURL);
            
            //initialise the adapter
            adapter.init();
            
            //handle exit event
            XMLEvent exitEvent = adapter.checkForExitEvent();
            if(exitEvent != null)
            {
            	handleExit(exitEvent, xFormsSession, session, request, response);
            }
            else
            {
            	//TODO: needed?
            	//response.setContentType(HTML_CONTENT_TYPE);
            	
            	UIGenerator uiGenerator = createUIGenerator(request, xFormsSession, actionURL, null);
            		            
	            //Generate the UI
	            uiGenerator.setInput(adapter.getXForms());
	            uiGenerator.setOutput(response.getOutputStream());
	            uiGenerator.generate();
	            
                //store WebAdapter in XFormsSession
                xFormsSession.setAdapter(adapter);
                //store UIGenerator in XFormsSession as property
                xFormsSession.setProperty(XFormsSession.UIGENERATOR, uiGenerator);
                //store queryString as 'referer' in XFormsSession
                xFormsSession.setProperty(XFormsSession.REFERER, request.getQueryString());
                //actually add the XFormsSession ot the manager
                sessionManager.addXFormsSession(xFormsSession);
            }
		}
		catch(Exception e)
		{
			LOG.error(e);
			e.printStackTrace();
			
			shutdown(adapter, session, e, response, request, xFormsSession.getKey());
		}
	}
    
	/**
	 * Updates the state of an XForm
	 * 
	 *  @param srvRequest	The Servlet request object to read the update from
	 *  @param srvResponse	The Servlet response to write the result to
	 */
	private void updateXForm(ServletRequest srvRequest, ServletResponse srvResponse)
    {
		HttpServletRequest request = (HttpServletRequest)srvRequest;
		HttpServletResponse response = (HttpServletResponse)srvResponse;
		HttpSession session = request.getSession();
		WebAdapter adapter = null;
		String key = request.getParameter("sessionKey");
		
		/*
		 * (ChibaAdapter)session.getAttribute(SESSION_CHIBA_ADAPTER);
		*/
		
		try
		{
			XFormsSessionManager manager = (XFormsSessionManager)session.getAttribute(XFormsSessionManager.XFORMS_SESSION_MANAGER);
			XFormsSession xFormsSession = manager.getXFormsSession(key);
			
			String referer = (String)xFormsSession.getProperty(XFormsSession.REFERER);
			LOG.debug("Referer: " + referer);
			
			adapter = xFormsSession.getAdapter();
			if(adapter == null)
			{
				LOG.error("No chiba adapter in session!");
				return;
			}
			
			ChibaEvent chibaEvent = new DefaultChibaEventImpl();
			chibaEvent.initEvent("http-request", null, request);
			adapter.dispatch(chibaEvent);
		
			//handle exit event
			XMLEvent exitEvent = adapter.checkForExitEvent();
			if(exitEvent != null)
			{
				handleExit(exitEvent, xFormsSession,  session, request, response);
			}
			else
			{
            	//TODO: needed?
            	//response.setContentType(HTML_CONTENT_TYPE);
			
		        //Dont set in filter -> is set by servlet!
		        /*
		        request.setCharacterEncoding("UTF-8");
		        response.setHeader("Cache-Control","private, no-store,  no-cache, must-revalidate");
		        response.setHeader("Pragma","no-cache");
		        response.setDateHeader("Expires",-1);
		        */
				
				UIGenerator uiGenerator = (UIGenerator)xFormsSession.getProperty(XFormsSession.UIGENERATOR);
				
				//TODO: needed?
				response.setCharacterEncoding("UTF-8");
				
				uiGenerator.setInput(adapter.getXForms());
				uiGenerator.setOutput(response.getOutputStream());
				uiGenerator.generate();
				response.getOutputStream().close();
			}
		}
		catch(Exception e)
		{
			LOG.error(e);
			e.printStackTrace();
			
			shutdown(adapter, session, e, response, request, key);
		}
    }
	
	/**
     * configures the an Adapter for interacting with the XForms processor (ChibaBean). The Adapter itself
     * will create the XFormsProcessor (ChibaBean) and configure it for processing.
     * <p/>
     * If you'd like to use a different source of XForms documents e.g. DOM you should extend this class and
     * overwrite this method. Please take care to also set the baseURI of the processor to a reasonable value
     * cause this will be the fundament for all URI resolutions taking place.
     *
     * @param adapter  the WebAdapter implementation to setup
     * @param XFormsSession	The XFormsSession for this adapter 
     * @param baseURI	The URI to use as a base for resolving URI's
     * @param isXForm	The input stream for the XForm
     * 
     * @return ServletAdapter
     */
    protected WebAdapter setupAdapter(WebAdapter adapter, XFormsSession xFormsSession, String baseURI, InputStream isXForm) throws XFormsException, URISyntaxException, IOException
    {
    	//set the adapters xforms session
		adapter.setXFormsSession(xFormsSession);
		
		//set the config file
		adapter.setConfigPath(CHIBA_CONFIG);
    	
		//set the xform, then close the input stream
		adapter.setXForms(isXForm);
		isXForm.close();
		
		//set the base URI
		adapter.setBaseURI(baseURI);
		
		//set the temporary file upload location
		adapter.setUploadDestination(CHIBA_TEMP_UPLOAD_DESTINATION);

        Map servletMap = new HashMap();
        servletMap.put(WebAdapter.SESSION_ID, xFormsSession.getKey());
        adapter.setContextParam(ChibaAdapter.SUBMISSION_RESPONSE, servletMap);

        return adapter;
    }
	
    /**
     * this method is responsible for passing all context information needed by the Adapter and Processor from
     * ServletRequest to ChibaContext. Will be called only once when the form-session is inited (GET).
     *
     * @param request    the ServletRequest
     * @param adapter the WebAdapter to use
     */
    protected void setContextParams(HttpServletRequest request, WebAdapter adapter)
    {
        //[1] pass user-agent to Adapter for UI-building
        adapter.setContextParam(WebAdapter.USERAGENT, request.getHeader("User-Agent"));
        adapter.setContextParam(WebAdapter.REQUEST_URI, getRequestURI(request));

        //[2] read any request params that are *not* Chiba params and pass them into the context map
        Enumeration params = request.getParameterNames();
        String paramName;
        while(params.hasMoreElements())
        {
            paramName = (String) params.nextElement();
            
            boolean isChibaParam = false;
            for(int i = 0; i < CHIBA_QUERYSTRING_PARAMS.length; i++)
            {
            	if(paramName.equals(CHIBA_QUERYSTRING_PARAMS[i]))
            	{
            		isChibaParam = true;
            	}
            }
            
            if(!isChibaParam)
            {
                String paramValue = request.getParameter(paramName);
                adapter.setContextParam(paramName, paramValue);
                LOG.debug("Request param: " + paramName + " added to the context");
            }
        }
    }
    
    /**
     * stores cookies that may exist in request and passes them on to processor for usage in
     * HTTPConnectors. Instance loading and submission then uses these cookies. Important for
     * applications using auth.
     *
     * @param request the servlet request
     * @param adapter the WebAdapter instance
     */
    protected void storeCookies(HttpServletRequest request, WebAdapter adapter)
    {
    	javax.servlet.http.Cookie[] cookiesIn = request.getCookies();
        if(cookiesIn != null)
        {
            Cookie[] commonsCookies = new Cookie[cookiesIn.length];
            for(int i = 0; i < cookiesIn.length; i += 1)
            {
                javax.servlet.http.Cookie c = cookiesIn[i];
                
                String domain = c.getDomain();
                if(domain == null)
                {
                	domain = "";
                }
                String path = c.getPath();
                if(path == null)
                {
                	path = "/";
                }
                
                commonsCookies[i] = new Cookie(domain, c.getName(), c.getValue(), path, c.getMaxAge(), c.getSecure());
            }
            adapter.setContextParam(AbstractHTTPConnector.REQUEST_COOKIE, commonsCookies);
        }
    }
    
    protected void storeAcceptLanguage(HttpServletRequest request, WebAdapter adapter)
    {
        String acceptLanguage = request.getHeader("accept-language");
        if((acceptLanguage != null) && (acceptLanguage.length() > 0))
        {
            adapter.setContextParam(AbstractHTTPConnector.ACCEPT_LANGUAGE, acceptLanguage);
        }
    }
    
    protected UIGenerator createUIGenerator(HttpServletRequest request, XFormsSession xFormsSession, String actionURL, String js) throws URISyntaxException, XFormsConfigException
    {
        TransformerService transformerService = (TransformerService) filterConfig.getServletContext().getAttribute(TransformerService.class.getName());
        URI uri = new File(CHIBA_STYLESHEET_PATH).toURI().resolve(new URI(CHIBA_STYLESHEET_FILE));
        
        XSLTGenerator generator = new XSLTGenerator();
        generator.setTransformerService(transformerService);
        generator.setStylesheetURI(uri);

        // todo: unify and extract parameter names
        generator.setParameter("contextroot", request.getContextPath());
        generator.setParameter("sessionKey", xFormsSession.getKey());
        if(xFormsSession.getProperty(XFormsSession.KEEPALIVE_PULSE) != null)
        {
            generator.setParameter("keepalive-pulse", xFormsSession.getProperty(XFormsSession.KEEPALIVE_PULSE));
        }
        generator.setParameter("action-url", actionURL);
        generator.setParameter("debug-enabled", CHIBA_DEBUG);
        String selectorPrefix = Config.getInstance().getProperty(HttpRequestHandler.SELECTOR_PREFIX_PROPERTY, HttpRequestHandler.SELECTOR_PREFIX_DEFAULT);
        generator.setParameter("selector-prefix", selectorPrefix);
        String removeUploadPrefix = Config.getInstance().getProperty(HttpRequestHandler.REMOVE_UPLOAD_PREFIX_PROPERTY, HttpRequestHandler.REMOVE_UPLOAD_PREFIX_DEFAULT);
        generator.setParameter("remove-upload-prefix", removeUploadPrefix);
        String dataPrefix = Config.getInstance().getProperty("chiba.web.dataPrefix");
        generator.setParameter("data-prefix", dataPrefix);
        String triggerPrefix = Config.getInstance().getProperty("chiba.web.triggerPrefix");
        generator.setParameter("trigger-prefix", triggerPrefix);
        generator.setParameter("user-agent", request.getHeader("User-Agent"));
        generator.setParameter("scripted", String.valueOf(js != null));
        
        //TODO: enable for AJAX/XForms
        /*if(scriptPath != null)
        {
            generator.setParameter("scriptPath", scriptPath);
        }*/
        generator.setParameter("CSSPath", CHIBA_CSS_PATH + '/');

        return generator;
    }
    
    private String getRequestURI(HttpServletRequest request)
    {
        StringBuffer buffer = new StringBuffer(request.getScheme());
        buffer.append("://");
        buffer.append(request.getServerName());
        buffer.append(":");
        buffer.append(request.getServerPort());
        buffer.append(request.getContextPath());
        return buffer.toString();
    }
    
    /**
     * returns a specific implementation of XFormsSessionManager. Plugin your own implementations here if needed.
     *
     * @return a specific implementation of XFormsSessionManager (defaults to DefaultXFormsSessionManagerImpl)
     */
    protected XFormsSessionManager getXFormsSessionManager()
    {
       return DefaultXFormsSessionManagerImpl.getInstance();
    }
	
    protected void handleExit(XMLEvent exitEvent, XFormsSession xFormsSession, HttpSession session, HttpServletRequest request, HttpServletResponse response) throws IOException
    {
    	if(ChibaEventNames.REPLACE_ALL.equals(exitEvent.getType()))
    	{
    		submissionResponse(xFormsSession, request, response);
    		//response.sendRedirect(response.encodeRedirectURL(request.getContextPath() + "/SubmissionResponse?sessionKey=" + xFormsSession.getKey()));
    	}
    	else if(ChibaEventNames.LOAD_URI.equals(exitEvent.getType()))
    	{	
    		if(exitEvent.getContextInfo("show") != null)
    		{
    			String loadURI = (String) exitEvent.getContextInfo("uri");

    			//kill XFormsSession
    			xFormsSession.getManager().deleteXFormsSession(xFormsSession.getKey());

    			response.sendRedirect(response.encodeRedirectURL(loadURI));
    		}
    	}
    	LOG.debug("EXITED DURING XFORMS MODEL INIT!");
    }
	
	private void submissionResponse(XFormsSession xFormsSession, HttpServletRequest request, HttpServletResponse response) throws IOException
	{
		// lookup attribute containing submission response map
		Map submissionResponse = (Map)xFormsSession.getProperty(ChibaServlet.CHIBA_SUBMISSION_RESPONSE);
		if(submissionResponse != null)
		{
			LOG.debug("Handling submission/@replace='all'");

            // copy header fields
            Map headerMap = (Map)submissionResponse.get("header");
            String name;
            String value;
            Iterator iterator = headerMap.keySet().iterator();
            while(iterator.hasNext())
            {
                name = (String) iterator.next();
                if(name.equalsIgnoreCase("Transfer-Encoding"))
                {
                    // Some servers (e.g. WebSphere) may set a "Transfer-Encoding"
                    // with the value "chunked". This may confuse the client since
                    // ChibaServlet output is not encoded as "chunked", so this
                    // header is ignored.
                    continue;
                }

                value = (String) headerMap.get(name);
                
                response.setHeader(name, value);
                
                LOG.debug("Added header: " + name + "=" + value);
            }

            // copy body stream
            InputStream bodyStream = (InputStream) submissionResponse.get("body");
            OutputStream outputStream = new BufferedOutputStream(response.getOutputStream());
            for(int b = bodyStream.read(); b > -1; b = bodyStream.read())
            {
                outputStream.write(b);
            }

            // close streams
            bodyStream.close();
            outputStream.close();

            //kill XFormsSession
            XFormsSessionManager sessionManager = xFormsSession.getManager();
            sessionManager.deleteXFormsSession(xFormsSession.getKey());

            return;
        }

        response.sendError(HttpServletResponse.SC_FORBIDDEN, "no submission response available");	
	}
	
    protected void shutdown(WebAdapter adapter, HttpSession session, Exception e, HttpServletResponse response, HttpServletRequest request, String key)
    {
    	// attempt to shutdown processor
    	if(adapter != null)
    	{
    		try
    		{
    			adapter.shutdown();
    		}
    		catch(XFormsException xfe)
    		{
    			LOG.error(xfe);
    			xfe.printStackTrace();
    		}
    	}

    	// store exception
		//todo: move exceptions to XFormsSession
		session.setAttribute("chiba.exception", e);
		//remove xformssession from httpsession
		session.removeAttribute(key);

		//TODO: should we pass the error upto the servlet somehow?
		// redirect to error page (after encoding session id if required)
		//response.sendRedirect(response.encodeRedirectURL(request.getContextPath() + "/" + request.getSession().getServletContext().getInitParameter("error.page")));
    }
}
