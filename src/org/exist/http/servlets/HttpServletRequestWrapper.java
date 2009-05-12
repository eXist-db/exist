/*
 * eXist Open Source Native XML Database Copyright (C) 2001-06 Wolfgang M.
 * Meier meier@ifs.tu-darmstadt.de http://exist.sourceforge.net
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 * $Id$
 */

package org.exist.http.servlets;

import org.exist.util.MimeType;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletInputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.security.Principal;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Vector;

/** A wrapper for HttpServletRequest
 * - differentiates between POST parameters in the URL or Content Body
 * - keeps content Body of the POST request, making it available many times 
 * 		through {@link #getContentBodyInputStream()} .
 * 
 * A method of differentiating between POST parameters in the URL or Content Body of the request was needed.
 * The standard javax.servlet.http.HTTPServletRequest does not differentiate between URL or content body parameters,
 * this class does, the type is indicated in RequestParameter.type.
 * 
 * To differentiate manually we need to read the URL (getQueryString()) and the Content body (getInputStream()),
 * this is problematic with the standard javax.servlet.http.HTTPServletRequest as parameter functions (getParameterMap(), getParameterNames(), getParameter(String), getParameterValues(String)) 
 * affect the  input stream functions (getInputStream(), getReader()) and vice versa.
 * 
 * This class solves this by reading the Request Parameters initially from both the URL and the Content Body of the Request
 * and storing them in the private variable params for later use.
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @serial 2006-02-28
 * @version 1.1
 */

//TODO: check loops to make sure they only iterate as few times as needed
//TODO: do we need to do anything with encoding strings manually?


public class HttpServletRequestWrapper implements HttpServletRequest
{
	/** Simple Enumeration implementation for String's, needed for getParameterNames() */
	private class StringEnumeration implements Enumeration
	{
		private String[] strings = null;	//Strings in the Enumeration
		int aryPos = -1;					//Current Position in Enumeration
		
		/**
		 * StringEnumeration Constructor
		 * @param strings 	an array of strings for the Enumeration
		 */
		StringEnumeration(String[] strings)
		{
			if(strings != null)
			{
				if(strings.length > 0)
				{
					//Create a new array
					this.strings = new String[strings.length];

					//Copy the data over
					System.arraycopy(strings, 0, this.strings, 0, strings.length);

					//Set the position to the start of the array
					aryPos = 0;
				}
			}
		}
		
		/**
		 * @see java.util.Enumeration#hasMoreElements
		 */
		public boolean hasMoreElements()
		{
			if(aryPos != -1)
			{
				if(aryPos < strings.length)
				{
					return true;
				}
			}
			return false;
		}
		
		/**
		 * @see java.util.Enumeration#nextElement
		 */
		public Object nextElement()
		{
			if(aryPos != -1)
			{
				if(aryPos < strings.length)
				{
					Object s = (Object)strings[aryPos];
					aryPos++;
					return s;
				}
				else
				{
					throw new NoSuchElementException("No more String's in the Enumeration, End Reached");
				}
			}
			else
			{
				throw new NoSuchElementException("Enumeration is empty");
			}
		}
	}
	
	/** Simple class to hold the value and type of a request parameter */
	private class RequestParamater
	{
		public final static int PARAM_TYPE_URL = 1;		//parameter from the URL of the request
		public final static int PARAM_TYPE_CONTENT = 2;	//parameter from the Content of the request

		private String value = null;	//parameter value
		private int type = 0;			//parameter type, either PARAM_TYPE_URL or PARAM_TYPE_CONTENT
		
		/**
		 * RequestParameter Constructor
		 * @param value 	Value of the Request Parameter
		 * @param type		Type of the Request Parameter, URL (1) or Content (2)
		 */
		RequestParamater(String value, int type)
		{
			this.value = value;
			this.type = type;
		}
		
		/**
		 * Request parameter value accessor
		 * @return		Value of Request parameter 
		 */
		public String getValue()
		{
			return(value);
		}
		
		/**
		 * Request parameter type accessor
		 * @return		Type of Request parameter
		 */
		public int getType()
		{
			return(type);
		}
	}
	
	
	//Members
	private HttpServletRequest request = null;		//The Request
	/** The encoding for the Request */
	private String formEncoding = null;
	
	/** The Request Parameters
	 * 
	 * params LinkedHashMap
	 * ====================
	 * params keys are String
	 * params values are Vector of RequestParameter's
	 */
	private LinkedHashMap params = null;			

	/** the content Body of the POST request; 
	 * it must be stored because once the Servlet input stream has been 
	 * consumed, the content Body is no more readable. */
	private String contentBodyAsString;
	byte[] contentBody;

	
	/**
	 * HttpServletRequestWrapper Constructor
	 * @param request		The HttpServletRequest to wrap
	 * @param formEncoding		The encoding to use
	 */
	public HttpServletRequestWrapper(HttpServletRequest request, String formEncoding) throws UnsupportedEncodingException
	{
		this.request = request;
		this.formEncoding = formEncoding;
		params = new LinkedHashMap();
		
		initialiseWrapper();
	}
	
	//Initalises the wrapper, setup encoding and parameter hashtable
	private void initialiseWrapper() throws UnsupportedEncodingException
	{
		//encoding
		if(request.getCharacterEncoding() == null)
		{
			request.setCharacterEncoding(formEncoding);
		}
		else
		{
			formEncoding = getCharacterEncoding();
		}
		
		//Parse out parameters from the URL
		parseURLParameters(this.request.getQueryString());
		
		//If POST request, Parse out parameters from the Content Body
		if(request.getMethod().toUpperCase().equals("POST"))
		{
			//If there is some Content
			if(request.getContentLength() > 0)
			{
				// If a form POST , and not a document POST
				String contentType = request.getContentType().toLowerCase();
                int semicolon = contentType.indexOf(';');
                if (semicolon>0) {
                    contentType = contentType.substring(0,semicolon).trim();
                }
				if( contentType.equals("application/x-www-form-urlencoded")
						&& request.getHeader("ContentType") == null )
				{
					//Parse out parameters from the Content Body
					parseContentBodyParameters();
					
				} else if (contentType.equals(MimeType.XML_TYPE.getName())) {
					// if an XML-RPC
					contentBodyAsString = getContentBody();
				}
			}
		}
	}
	
	//Stores parameters from the QueryString of the request
	private void parseURLParameters(String querystring)
	{
		if(querystring != null)
		{
			//Parse any parameters from the URL
			parseParameters(querystring, RequestParamater.PARAM_TYPE_URL);
		}
	}
	
	/** Stores parameters from the Content Body of the Request */
	private void parseContentBodyParameters()
	{
		String content = getContentBody();
		
		//Parse any parameters from the Content Body
		parseParameters( content, RequestParamater.PARAM_TYPE_CONTENT);
	}

	private String getContentBody() {
		
		//Create a buffer big enough to hold the Content Body		
		contentBody = new byte[ request.getContentLength() ];
		String result = "";
		
		try	{
	        String encoding = request.getCharacterEncoding();
	        if(encoding == null)
	            encoding = "UTF-8";

			//Read the Content Body into the buffer
			InputStream is = request.getInputStream();
			
			int bytes = 0;
			int offset = 0;
			int max = 4096;
		    while (( bytes = is.read( contentBody, offset, max )) != -1) {
				offset += bytes;
		    }
			result = new String(contentBody, encoding);

		}
		catch(IOException ioe) {
			//TODO: handle this properly
			System.err.println( "Error Reading the Content Body into the buffer: " + ioe );
			ioe.printStackTrace();
		}
		return result;
	}
	
	/** Parses Parameters into param objects and stores them in a vector in params */
	private void parseParameters(String parameters, int type)
	{
		//Split parameters into an array
		String[] nameValuePairs = parameters.split("&");
		
		for (int k = 0; k < nameValuePairs.length; k++)
		{
			//Split parameter into name and value
			String[] thePair = nameValuePairs[k].split("=");
			
			try
			{
				//URL Decode the parameter name and value
				thePair[0] = URLDecoder.decode(thePair[0], formEncoding);
				if(thePair.length == 2)
				{
					thePair[1] = URLDecoder.decode(thePair[1], formEncoding);
				}
			}
			catch(UnsupportedEncodingException uee)
			{
				//TODO: handle this properly
				uee.printStackTrace();
			}
			
			//Have we encountered a parameter with this name?
			if(params.containsKey(thePair[0]))
			{
				//key exists in hash map, add value and type to vector
				Vector vecValues = (Vector)params.get(thePair[0]);
				vecValues.add(new RequestParamater((thePair.length == 2 ? thePair[1] : new String()), type));
				params.put(thePair[0], vecValues);
			}
			else
			{
				//not in hash map so add a vector with the initial value
				Vector vecValues = new Vector();
				vecValues.add(new RequestParamater((thePair.length == 2 ? thePair[1] : new String()), type));
				params.put(thePair[0], vecValues);
			}
		}
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getAuthType
	 */
	public String getAuthType()
	{
		return request.getAuthType();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getCookies
	 */
	public Cookie[] getCookies()
	{
		return request.getCookies();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getDateHeader
	 */
	public long getDateHeader(String name)
	{
		return request.getDateHeader(name);
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getHeader
	 */
	public String getHeader(String name)
	{
		return request.getHeader(name);
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getHeaders
	 */
	public Enumeration getHeaders(String name)
	{
		return request.getHeaders(name);
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getHeaderNames
	 */
	public Enumeration getHeaderNames()
	{
		return request.getHeaderNames();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getIntHeader
	 */
	public int getIntHeader(String name)
	{ 
		return request.getIntHeader(name);
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getMethod
	 */
	public String getMethod()
	{
		return request.getMethod();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getPathInfo
	 */
	public String getPathInfo()
	{
		return request.getPathInfo();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getPathTranslated
	 */
	public String getPathTranslated()
	{
		return request.getPathInfo();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getContextPath
	 */
	public String getContextPath()
	{
		return request.getContextPath();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getQueryString
	 */
	public String getQueryString()
	{
		return request.getQueryString();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getRemoteUser
	 */
	public String getRemoteUser()
	{
		return request.getRemoteUser();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#isUserInRole
	 */
	public boolean isUserInRole(String name)
	{
		return request.isUserInRole(name);
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getUserPrincipal
	 */
	public Principal getUserPrincipal()
	{
		return request.getUserPrincipal();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getRequestedSessionId
	 */
	public String getRequestedSessionId()
	{
		return request.getRequestedSessionId();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getRequestURI
	 */
	public String getRequestURI()
	{
		return request.getRequestURI();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getRequestURL
	 */
	public StringBuffer getRequestURL()
	{	
		return request.getRequestURL();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getServletPath
	 */
	public String getServletPath()
	{
		return request.getServletPath();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getSession
	 */
	public HttpSession getSession(boolean create)
	{
		return request.getSession(create);
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getSession
	 */
	public HttpSession getSession()
	{
		return request.getSession();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdValid
	 */
	public boolean isRequestedSessionIdValid()
	{
		return request.isRequestedSessionIdValid();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdFromCookie
	 */
	public boolean isRequestedSessionIdFromCookie()
	{
		return request.isRequestedSessionIdFromCookie();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdFromURL
	 */
	public boolean isRequestedSessionIdFromURL()
	{
		return request.isRequestedSessionIdFromURL();
	}

	/**
	 * @deprecated use isRequestedSessionIdFromURL() instead.
     * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdFromUrl
	 */
	public boolean isRequestedSessionIdFromUrl()
	{	
		return request.isRequestedSessionIdFromUrl();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getAttribute
	 */
	public Object getAttribute(String name)
	{
		return request.getAttribute(name);
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getAttributeNames
	 */
	public Enumeration getAttributeNames()
	{
		return request.getAttributeNames();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getCharacterEncoding
	 */
	public String getCharacterEncoding()
	{
		return request.getCharacterEncoding();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#setCharacterEncoding
	 */
	public void setCharacterEncoding(String env) throws UnsupportedEncodingException
	{
		request.setCharacterEncoding(env);
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getContentLength
	 */
	public int getContentLength()
	{
		return request.getContentLength();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getContentType
	 */
	public String getContentType()
	{	
		return request.getContentType();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getInputStream
	 */
	public ServletInputStream getInputStream() throws IOException
	{
        if (contentBodyRecorded())
            return new CachingServletInputStream();
        else
            return request.getInputStream();
    }

//	public InputStream getStringBufferInputStream() throws IOException {
//		return new StringBufferInputStream( contentBodyAsString );
//	}

	/** making the content Body of the POST request available many times, for processing by , e.g. Rpc processor . */
	public InputStream getContentBodyInputStream() throws IOException {
		return new ByteArrayInputStream( contentBody );
	}
	
	/**
	 * get the value of a Request parameter by its name from the local parameter store
	 * @param name		The name of the Request parameter to get the value for
	 * @return		The value of the Request parameter with the specified name
	 */
	public String getParameter(String name)
	{
		//Does the parameter exist?
		if(params.containsKey(name))
		{
			//Get the parameters vector of values
			Vector vecParameterValues = (Vector)params.get(name);

			//return the first value in the vector
			return ((RequestParamater)vecParameterValues.get(0)).getValue();
		}
		else
		{
			return null;
		}
	}

	/**
	 * get the names of the Request parameters from the local parameter store
	 * @return		An enumeration of string values representing the Request parameters names
	 */
	public Enumeration getParameterNames()
	{	
		//get the key set as an array
		Object[] keySet = params.keySet().toArray();
		
		//create a new string array, the same size as the ket set array 
		String[] strKeySet = new String[keySet.length];
		
		//copy the data from the key set to the string key set
		System.arraycopy(keySet, 0, strKeySet, 0, keySet.length);
		
		//return an enumeration of strings of the keys
		return new StringEnumeration(strKeySet);
	}

	/**
	 * get the values of the Request parameter indicated by name from the local parameter store
	 * @param name		The name of the Request parameter to get the values for
	 * @return		The String array of the Request parameter's values
	 */
	public String[] getParameterValues(String name)
	{
		//Does the parameter exist?
		if(params.containsKey(name))
		{
			//Get the parameters vector of values
			Vector vecParameterValues = (Vector)params.get(name);
			
			//Create a string array to hold the values
			String[] values = new String[vecParameterValues.size()];
			
			//Copy each value into the string array
			for(int i = 0; i < vecParameterValues.size(); i++)
			{
				values[i] = ((RequestParamater)vecParameterValues.get(i)).getValue();
			}
			
			//return the string array of values
			return(values);
		}
		else
		{
			return null;
		}
	}

	/**
	 * get a Map of Request parameters (keys and values) from the local parameter store
	 * @return		Map of Request Parameters. Key is of type String and Value is of type String[].
	 */
	public Map getParameterMap()
	{
		//Map to hold the parameters
		LinkedHashMap mapParameters = new LinkedHashMap();
		
		Set setParams = params.entrySet();
		
		//iterate through the Request Parameters
		for(Iterator itParams = setParams.iterator(); itParams.hasNext(); )
		{
			//Get the parameter
			Map.Entry me = (Map.Entry)itParams.next();
			
			//Get the parameters values
			Vector vecParamValues = (Vector)me.getValue();
			
			//Create a string array to hold the parameter values
			String[] values = new String[vecParamValues.size()];
			
			//Copy the parameter values into a string array
			int i = 0;
			for(Iterator itParamValues = vecParamValues.iterator(); itParamValues.hasNext(); i++)
			{
				values[i] = ((RequestParamater)itParamValues.next()).getValue();
			}
			mapParameters.put(me.getKey(), values); //Store the parameter in a map
		}
		return mapParameters; //return the Map of parameters
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getProtocol
	 */
	public String getProtocol()
	{
		return request.getProtocol();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getScheme
	 */
	public String getScheme()
	{
		return request.getScheme();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getServerName
	 */
	public String getServerName()
	{
		return request.getServerName();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getServerPort
	 */
	public int getServerPort()
	{
		return request.getServerPort();
	}

	public BufferedReader getReader() throws IOException
	{
        if (contentBodyRecorded())
            return new BufferedReader(new InputStreamReader(getContentBodyInputStream(),
                    request.getCharacterEncoding()));
        else
		    return request.getReader();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getRemoteAddr
	 */
	public String getRemoteAddr()
	{
		return request.getRemoteAddr();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getRemoteHost
	 */
	public String getRemoteHost()
	{
		return request.getRemoteHost();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#setAttribute
	 */
	public void setAttribute(String name, Object o)
	{
		request.setAttribute(name, o);
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#removeAttribute
	 */
	public void removeAttribute(String name)
	{
		request.removeAttribute(name);
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getLocale
	 */
	public Locale getLocale()
	{
		return request.getLocale();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getLocales
	 */
	public Enumeration getLocales()
	{
		return request.getLocales();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#isSecure
	 */
	public boolean isSecure()
	{
		return request.isSecure();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getRequestDispatcher
	 */
	public RequestDispatcher getRequestDispatcher(String name)
	{
		return request.getRequestDispatcher(name);
	}

	/**
     * @deprecated use use ServletContext#getRealPath(java.lang.String) instead.
	 * @see javax.servlet.http.HttpServletRequest#getRealPath
	 */
	public String getRealPath(String path)
	{
		return request.getRealPath(path);
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getRemotePort
	 */
	public int getRemotePort()
	{
		return request.getRemotePort();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getLocalName
	 */
	public String getLocalName()
	{	
		return request.getLocalName();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getLocalAddr
	 */
	public String getLocalAddr()
	{
		return request.getLocalAddr();
	}

	/**
	 * @see javax.servlet.http.HttpServletRequest#getLocalPort
	 */
	public int getLocalPort()
	{	
		return request.getLocalPort();
	}
	
	/**
	 * Similar to javax.servlet.http.HttpServletRequest.toString() ,
	 * except it includes output of the Request parameters from the Request's Content Body
	 * @return		String representation of HttpServletRequestWrapper
	 */
	public String toString() {
		// If POST request AND there is some content AND its not a file upload
		// 	AND content Body has not been recorded
		if (	    request.getMethod().toUpperCase().equals("POST")
				&&  request.getContentLength() > 0
				&& !request.getContentType().toUpperCase().startsWith(
						"MULTIPART/")
				&& ! contentBodyRecorded() ) {
			
			// Also return the content parameters, these are not part 
			// of the standard HttpServletRequest.toString() output
			StringBuffer buf = new StringBuffer( request.toString());

			Set setParams = params.entrySet();

			for (Iterator itParams = setParams.iterator(); itParams.hasNext();) {
				Map.Entry me = (Map.Entry) itParams.next();
				Vector vecParamValues = (Vector) me.getValue();

				for (Iterator itParamValues = vecParamValues.iterator(); itParamValues
						.hasNext();) {
					RequestParamater p = (RequestParamater) itParamValues
							.next();

					if (p.type == RequestParamater.PARAM_TYPE_CONTENT) {
						if (buf.charAt(buf.length() - 1) != '\n')
							buf.append("&");
						buf.append((String) me.getKey());
						buf.append("=");
						buf.append(p.getValue());
					}
				}
			}

			buf.append(	System.getProperty("line.separator") +
						System.getProperty("line.separator") );

			return buf.toString();

		} else if ( contentBodyRecorded() ) {
			
			// XML-RPC request or plain XML REST POST
			StringBuffer buf = new StringBuffer( request.toString() );
			buf.append(contentBodyAsString);
			
			buf.append(	System.getProperty("line.separator") +
						System.getProperty("line.separator") );
			return buf.toString();

		} else {
			//Return standard HttpServletRequest.toString() output
			return request.toString();
		}
	}

	private boolean contentBodyRecorded() {
		return contentBody != null 
			&& contentBody.length > 0;
	}

    private class CachingServletInputStream extends ServletInputStream {

        private ByteArrayInputStream istream;

        public CachingServletInputStream() {
            if (contentBody == null)
                istream = new ByteArrayInputStream(new byte[0]);
            else
                istream = new ByteArrayInputStream(contentBody);
        }
        
        public int read() throws IOException {
           return istream.read();
        }

        public int read(byte b[]) throws IOException {
            return istream.read(b);
        }

        public int read(byte b[], int off, int len) throws IOException {
            return istream.read(b, off, len);
        }

        public int available() throws IOException {
            return istream.available();
        }
    }
}
