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
 *  $Id: XFormsFilter.java 4565 2006-10-12 12:42:18 +0000 (Thu, 12 Oct 2006) deliriumsky $
 */

package uk.gov.devonline.www.xforms;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

import org.apache.commons.httpclient.Cookie;

import org.chiba.adapter.ChibaAdapter;
import org.chiba.adapter.ChibaEvent;
import org.chiba.adapter.DefaultChibaEventImpl;
import org.chiba.adapter.servlet.ServletAdapter;
import org.chiba.tools.xslt.StylesheetLoader;
import org.chiba.tools.xslt.UIGenerator;
import org.chiba.tools.xslt.XSLTGenerator;
import org.chiba.xml.xforms.connector.http.AbstractHTTPConnector;
import org.chiba.xml.xforms.exception.XFormsException;

/**
 * A Servlet Filter to provide XForms functionality to existing Servlets
 * 
 * @author Adam Retter
 * @version 1.1
 * @serial 2006-09-18T15:15
 * 
 * 
 * Initialization parameters for the ServletFilter
 * 
 * CHIBA_DEBUG							true or false indicating if Chiba should run in debug mode
 * CHIBA_CONFIG							Path to Chiba's configuration file
 * CHIBA_TEMP_UPLOAD_DESTINATION		Path to upload temporary files to
 * CHIBA_STYLESHEET_PATH				Path to XSLT location
 * CHIBA_STYLESHEET_FILE				XSLT file in XSLT_STYLESHEET_PATH to use for processing
 * CHIBA_CSS							Path and filename of CSS file to use, relevant to servlet url
 */
public class XFormsFilter implements Filter
{
	private static FilterConfig filterConfig = null;
	
	private final static boolean DEFAULT_CHIBA_DEBUG = true;
	private final static String DEFAULT_CHIBA_CONFIG = "/eXist/tools/XFormsFilter/chiba.default.xml";
	private final static String DEFAULT_CHIBA_TEMP_UPLOAD_DESTINATION = "/tmp";
	private final static String DEFAULT_CHIBA_STYLESHEET_PATH = "/exist/tools/XFormsFilter/xslt";
	private final static String DEFAULT_CHIBA_STYLESHEET_FILE = "xhtml.xsl";
	private final static String DEFAULT_CHIBA_CSS = "/exist/servlet/db/xforms-test/xforms.css";
	
	private boolean CHIBA_DEBUG = DEFAULT_CHIBA_DEBUG;
	private String CHIBA_CONFIG = DEFAULT_CHIBA_CONFIG;
	private String CHIBA_TEMP_UPLOAD_DESTINATION = DEFAULT_CHIBA_TEMP_UPLOAD_DESTINATION;
	private String CHIBA_STYLESHEET_PATH = DEFAULT_CHIBA_STYLESHEET_PATH;
	private String CHIBA_STYLESHEET_FILE = DEFAULT_CHIBA_STYLESHEET_FILE;
	private String CHIBA_CSS = DEFAULT_CHIBA_CSS;
	
	private final static String SESSION_CHIBA_ADAPTER = "chiba.adapter";
	private final static String SESSION_CHIBA_UIGENERATOR = "chiba.uiGenerator";
	
	private final static String[] CHIBA_QUERYSTRING_PARAMS = {
		"form",
		"xslt",
		"action_url",
		"JavaScript"
	};
	
	
	
	//TODO: could create a single UIGenerator, start it up in init() and close it down in destroy()???
	
	
	/**
	 * Filter initialisation
	 * 
	 * @see http://java.sun.com/j2ee/sdk_1.3/techdocs/api/javax/servlet/Filter.html#init(javax.servlet.FilterConfig)
	 */
	public void init(FilterConfig filterConfig) throws ServletException
	{
		this.filterConfig = filterConfig;
		
		setupConfig();
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
		if(filterConfig.getInitParameter("CHIBA_DEBUG") != null)
		{
			CHIBA_DEBUG = filterConfig.getInitParameter("CHIBA_DEBUG").toLowerCase().equals("true");
		}
		
		if(filterConfig.getInitParameter("CHIBA_CONFIG") != null)
		{
			CHIBA_CONFIG = filterConfig.getInitParameter("CHIBA_CONFIG");
		}
		
		if(filterConfig.getInitParameter("CHIBA_TEMP_UPLOAD_DESTINATION") != null)
		{
			CHIBA_TEMP_UPLOAD_DESTINATION = filterConfig.getInitParameter("CHIBA_TEMP_UPLOAD_DESTINATION");
		}
		
		if(filterConfig.getInitParameter("CHIBA_STYLESHEET_PATH") != null)
		{
			CHIBA_STYLESHEET_PATH = filterConfig.getInitParameter("CHIBA_STYLESHEET_PATH");
		}
		
		if(filterConfig.getInitParameter("CHIBA_STYLESHEET_FILE") != null)
		{
			CHIBA_STYLESHEET_FILE = filterConfig.getInitParameter("CHIBA_STYLESHEET_FILE");
		}
		
		if(filterConfig.getInitParameter("CHIBA_CSS") != null)
		{
			CHIBA_CSS = filterConfig.getInitParameter("CHIBA_CSS");
		}
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
			log("Start Update XForm", false);
			updateXForm(srvRequest, srvResponse);
			log("End Update XForm", false);
		}
		else
		{
		
			/* do servlet request */
			log("Passing to Chain", false);
			BufferedHttpServletResponseWrapper bufResponse = new BufferedHttpServletResponseWrapper((HttpServletResponse)srvResponse);
			filterChain.doFilter(srvRequest, bufResponse);
			log("Returned from Chain", false);
			
			/* after servlet request */		
			if(hasXForm(bufResponse))
			{
				bufResponse.getOutputStream().close();
				log("Start Render XForm", false);
				
				//remove DOCTYPE PI if it exists, Xerces in Chiba otherwise may try to download the system DTD (can cause latency problems)
				byte[] data = removeDocumentTypePI(bufResponse.getData());
				//correct the <xforms:instance> xmlns="" problem (workaround for namespace problems in eXist)
				data = correctInstanceXMLNS(data);
				
				renderXForm(new ByteArrayInputStream(data), srvRequest, srvResponse);
				log("End Render XForm", false);
			}
			else
			{
				srvResponse.getOutputStream().write(bufResponse.getData());
				srvResponse.getOutputStream().close();
			}
		}
	}	
	
	/**
	 * Log's a message
	 * 
	 * @param message	The message to log
	 * @param error		If the message is an error message
	 */
	private void log(String message, boolean error)
	{
		if(error)
		{
			filterConfig.getServletContext().log("XFormsFilter ERROR: " + message);
		}
		else
		{
			filterConfig.getServletContext().log("XFormsFilter: " + message);
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
		if(request.getMethod().equals("POST"))
		{
			//get the Chiba Adapter from the session
			ChibaAdapter adapter = (ChibaAdapter)request.getSession().getAttribute(SESSION_CHIBA_ADAPTER);
		
	    	if(adapter != null)
	    	{
	    		String actionURL;
	        	if(request.getQueryString() != null)
	        	{
	        		actionURL = request.getRequestURL() + "?" + request.getQueryString(); 
	        	}
	        	else
	        	{
	        		actionURL = request.getRequestURL().toString();
	        	}
	        	
	        	
	        	if(actionURL.indexOf("&sessionKey=") > -1)
	        	{
	        		actionURL = actionURL.substring(0, actionURL.indexOf("&sessionKey="));
	        	}
	        	
	        	//if the action-url in the adapters context param is the same as that of the action url then we know we are updating
	        	if(adapter.getContextParam("action-url").equals(actionURL))
	        	{
	        		return true;
	        	}
	    	}
		}
		
    	return false;
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
		
		if(strResponse.contains(new String("<xforms:model")) && strResponse.contains(new String("<xforms:instance")))
		{
			return true;
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
    	
    	try
    	{
    		String baseURI = request.getRequestURL().toString();
    		String actionURL;
    		if(request.getQueryString() != null)
    		{
    			actionURL = request.getRequestURL() + "?" + request.getQueryString();
    		}
    		else
    		{
    			actionURL = request.getRequestURL().toString();
    		}
    		
    		//new chiba adapter
    		ChibaAdapter adapter = new ServletAdapter();
    		
    		//Create a Chiba Bean
    		//adapter.createXFormsProcessor();	//no longer needed after Chiba 2.0.0RC1
    		
    		//set the config file
    		adapter.setConfigPath(CHIBA_CONFIG);
    		
    		//set some parameters for checking on POST
    		adapter.setContextParam("action-url", actionURL);
    		
            //set the XForm
            adapter.setXForms(isXForm);
            isXForm.close();
            
            adapter.setBaseURI(baseURI);
            adapter.setUploadDestination(CHIBA_TEMP_UPLOAD_DESTINATION);
            
            Map servletMap = new HashMap();            
            servletMap.put(ChibaAdapter.SESSION_ID, session.getId());
            adapter.setContextParam(ChibaAdapter.SUBMISSION_RESPONSE, servletMap);
            
            //pass user-agent to Adapter for UI-building
            adapter.setContextParam("chiba.useragent", request.getHeader("User-Agent"));
            
            //copy all request querystring parameters that
            //arent chiba related into the context
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
                }
            }
            
            //store cookies that may exist in request and passes them on to processor for usage in
            //HTTPConnectors. Instance loading and submission then uses these cookies. Important for
            //applications using auth
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
            
            //initialise the adapter
            adapter.init();
            
            if(chibaLoad(adapter, response))
            {
            	return;
            }
            	
            if(chibaReplaceAll(adapter, response))
            {
            	return;
            }
            
            //create the UI Generator
            StylesheetLoader stylesheetLoader = new StylesheetLoader(CHIBA_STYLESHEET_PATH);
            stylesheetLoader.setStylesheetFile(CHIBA_STYLESHEET_FILE);
            
            UIGenerator uiGenerator = new XSLTGenerator(stylesheetLoader);
            
            /* START set parameters */
            uiGenerator.setParameter("contextroot",request.getContextPath());
            uiGenerator.setParameter("sessionKey", session.getId());
            uiGenerator.setParameter("action-url", actionURL);
            uiGenerator.setParameter("debug-enabled", new Boolean(CHIBA_DEBUG).toString());
            uiGenerator.setParameter("selector-prefix", "s_");
            uiGenerator.setParameter("remove-upload-prefix", "ru_");
            uiGenerator.setParameter("data-prefix", "d_");
            uiGenerator.setParameter("trigger-prefix", "t_");
            uiGenerator.setParameter("user-agent", request.getHeader("User-Agent"));
            uiGenerator.setParameter("css-file", CHIBA_CSS);
            /* END set parameters */
            
            //Generate the UI
            uiGenerator.setInputNode(adapter.getXForms());
            uiGenerator.setOutput(response.getWriter());
            uiGenerator.generate();
            
            //store adapter and uigenerator in session
            session.setAttribute(SESSION_CHIBA_ADAPTER, adapter);
            session.setAttribute(SESSION_CHIBA_UIGENERATOR, uiGenerator);
		}
		catch(XFormsException xfe)
		{
			System.err.println(xfe.getMessage());
			xfe.printStackTrace();
		}
		catch(IOException ioe)
		{
			System.err.println(ioe.getMessage());
			ioe.printStackTrace();
		}
		catch(Exception e)
		{
			System.err.println(e.getMessage());
			e.printStackTrace();
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
		HttpSession session = request.getSession();
		ChibaAdapter adapter = (ChibaAdapter)session.getAttribute(SESSION_CHIBA_ADAPTER);
    	
    	if(adapter == null)
    	{
			log("No chiba adapter in session", true);
			return;
		}
    	
    	ChibaEvent chibaEvent = new DefaultChibaEventImpl();
		chibaEvent.initEvent("http-request", null, request);
		
		try
		{
			adapter.dispatch(chibaEvent);
			
			if (chibaLoad(adapter, srvResponse))
			{
				//clear session
				session.removeAttribute(SESSION_CHIBA_ADAPTER);
				session.removeAttribute(SESSION_CHIBA_UIGENERATOR);
				
				return;
			}
			
			if (chibaReplaceAll(adapter, srvResponse))
			{
				//clear session
				session.removeAttribute(SESSION_CHIBA_ADAPTER);
				session.removeAttribute(SESSION_CHIBA_UIGENERATOR);
				
				return;
			}
			
			UIGenerator uiGenerator = (UIGenerator)request.getSession().getAttribute(SESSION_CHIBA_UIGENERATOR);
			
			srvResponse.setCharacterEncoding("UTF-8");
			
			uiGenerator.setInputNode(adapter.getXForms());
			uiGenerator.setOutput(srvResponse.getWriter());
			uiGenerator.generate();
			srvResponse.getWriter().close();
		}
		catch(XFormsException xfe)
		{
			System.err.println(xfe.getMessage());
			xfe.printStackTrace();
		}
		catch(IOException ioe)
		{
			System.err.println(ioe.getMessage());
			ioe.printStackTrace();
		}
    }
	
	/**
	 * should be re-implemented using chiba events on adapter
	 */
	protected boolean chibaLoad(ChibaAdapter adapter, ServletResponse srvResponse) throws XFormsException, IOException
	{
		HttpServletResponse response = (HttpServletResponse)srvResponse;
		
		if(adapter.getContextParam(ChibaAdapter.LOAD_URI) != null)
		{
			String redirectTo = (String) adapter.removeContextParam(ChibaAdapter.LOAD_URI);
			adapter.shutdown();
			response.sendRedirect(response.encodeRedirectURL(redirectTo));
			return true;
		}
		return false;
	}

	/**
	 * should be re-implemented using chiba events on adapter
	 */
	protected boolean chibaReplaceAll(ChibaAdapter chibaAdapter, ServletResponse srvResponse) throws XFormsException, IOException
	{
		if(chibaAdapter.getContextParam(ChibaAdapter.SUBMISSION_RESPONSE) != null)
		{
			Map forwardMap = (Map) chibaAdapter.removeContextParam(ChibaAdapter.SUBMISSION_RESPONSE);
			if (forwardMap.containsKey(ChibaAdapter.SUBMISSION_RESPONSE_STREAM))
			{
				chibaForwardResponse(forwardMap, srvResponse);
				chibaAdapter.shutdown();
				return true;
			}
		}
		return false;
	}

	private void chibaForwardResponse(Map forwardMap, ServletResponse srvResponse) throws IOException
	{
		HttpServletResponse response = (HttpServletResponse)srvResponse;
		
		// fetch response stream
		InputStream responseStream = (InputStream) forwardMap.remove(ChibaAdapter.SUBMISSION_RESPONSE_STREAM);
	
		// copy header information
		Iterator iterator = forwardMap.keySet().iterator();
		while (iterator.hasNext())
		{
			String name = (String) iterator.next();
		
			if ("Transfer-Encoding".equalsIgnoreCase(name))
			{
				// Some servers (e.g. WebSphere) may set a "Transfer-Encoding"
				// with the value "chunked". This may confuse the client since
				// ChibaServlet output is not encoded as "chunked", so this
				// header is ignored.
				continue;
			}
		
			String value = (String) forwardMap.get(name);
			response.setHeader(name, value);
		}
		
		// copy stream content
		OutputStream outputStream = new BufferedOutputStream(response.getOutputStream());
		for(int b = responseStream.read(); b > -1; b = responseStream.read())
		{
			outputStream.write(b);
		}
		
		// close streams
		responseStream.close();
		outputStream.close();
	}

}
