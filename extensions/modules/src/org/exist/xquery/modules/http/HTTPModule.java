/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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
 *  $Id: ExampleModule.java 1173 2005-04-20 11:15:18Z wolfgang_m $
 */
package org.exist.xquery.modules.http;

import java.io.IOException;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.URIException;

import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.Base64Binary;
import org.exist.xquery.value.Sequence;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk
 * @serial 20070428
 * @version 1.0
 */
public class HTTPModule extends AbstractInternalModule
{
	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/http";
	
	public final static String PREFIX = "http";
	
	public final static String HTTP_MODULE_PERSISTENT_COOKIES = "_eXist_http_module_cookies";
	
	private final static FunctionDef[] functions = {
		new FunctionDef(GETFunction.signature, GETFunction.class),
		new FunctionDef(POSTFunction.signature, POSTFunction.class),
		new FunctionDef(ClearPersistentCookiesFunction.signature, ClearPersistentCookiesFunction.class)
	};
	
	public HTTPModule() {
		super(functions);
	}

	public String getNamespaceURI() {
		return NAMESPACE_URI;
	}

	public String getDefaultPrefix() {
		return PREFIX;
	}

	public String getDescription() {
		return "A module for performing HTTP requests";
	}
	
	
	/**
	 * Performs a HTTP Request
	 * 
	 * @param context	The context of the calling XQuery
	 * @param method	The HTTP methor for the request
	 * @param persistCookies	If true existing cookies are re-used and any issued cookies are persisted for future HTTP Requests 
	 */
	protected static void doRequest(XQueryContext context, HttpMethod method, boolean persistCookies) throws IOException
	{
		//use existing cookies?
        if(persistCookies)
        {
        	//set existing cookies
	        Cookie[] cookies = (Cookie[])context.getXQueryContextVar(HTTPModule.HTTP_MODULE_PERSISTENT_COOKIES);
			if(cookies != null)
			{
				for(int c = 0; c < cookies.length; c++)
				{
					method.setRequestHeader("Cookie", cookies[c].toExternalForm());
				}
			}
        }
        
		//execute the request
		HttpClient http = new HttpClient();
		int result = http.executeMethod(method);
		
		//persist cookies?
		if(persistCookies)
		{
			//store/update cookies
			HttpState state = http.getState();
			Cookie[] incomingCookies = state.getCookies();
			Cookie[] currentCookies = (Cookie[])context.getXQueryContextVar(HTTPModule.HTTP_MODULE_PERSISTENT_COOKIES);
			context.setXQueryContextVar(HTTPModule.HTTP_MODULE_PERSISTENT_COOKIES, HTTPModule.mergeCookies(currentCookies, incomingCookies));
		}
	}
	
	/**
	 * Takes the HTTP Response Body from the HTTP Method and attempts to convert it to a
	 * suiatble datatype for XQuery.
	 * 
	 * Conversion Preference -
	 * 1) Try and parse as XML, if successful returns a Node
	 * 2) Try and parse as HTML returning as XML compatible HTML, if successful returns a Node
	 * 3) Return as base64Binary encoded data
	 * 
	 * @param context	The context of the calling XQuery
	 * @param method	The HTTP Request Method
	 * 
	 * @return The data in an suitable XQuery datatype value
	 */
	protected static Sequence httpResponseDataToXQueryDataType(XQueryContext context, HttpMethod method) throws IOException, XPathException
	{
		//try and parse the response as XML
		try
		{
			//TODO: replace getResponseBodyAsString() with getResponseBodyAsStream()
			return ModuleUtils.stringToXML(context, method.getResponseBodyAsString());
		}
		catch(SAXException se)
		{
			//could not parse to xml
		}
		
		//response is NOT parseable as XML, determine the type of the response document
		MimeType responseMimeType = HTTPModule.getResponseMimeType(method.getResponseHeader("Content-Type"));
		
		//is it a html document?
		if(responseMimeType.getName().equals(MimeType.HTML_TYPE.getName()))
		{
			//html document
			try
			{
				//parse html to xml(html)
				return ModuleUtils.htmlToXHtml(context, method.getURI().toString(), new InputSource(method.getResponseBodyAsStream()));
			}
			catch(URIException ue)
			{
				throw new XPathException(ue);
			}
			catch(SAXException se)
			{
				//counld not parse to xml(html)
			}
		}

		//other document type, assume binary so base64 encode
		return new Base64Binary(method.getResponseBody());
	}
	
	/**
	 * Given the Response Header for Content-Type this function returns an appropriate eXist MimeType
	 * 
	 * @param responseHeaderContentType	The HTTP Response Header containing the Content-Type of the Response.
	 * @return The corresponding eXist MimeType
	 */
	protected static MimeType getResponseMimeType(Header responseHeaderContentType)
	{
		if(responseHeaderContentType != null)
		{
			if(responseHeaderContentType.getName().equals("Content-Type"))
			{
				String responseContentType = responseHeaderContentType.getValue();
				int contentTypeEnd = responseContentType.indexOf(";");
				if(contentTypeEnd == -1)
				{
					contentTypeEnd = responseContentType.length();
				}
				String responseMimeType = responseContentType.substring(0, contentTypeEnd);
				MimeTable mimeTable = MimeTable.getInstance();
				MimeType mimeType = mimeTable.getContentType(responseMimeType);
				if(mimeType != null)
				{
					return mimeType;
				}
			}
		}
		
		return MimeType.BINARY_TYPE;
	}
	
	/**
	 * Merges two cookie arrays together
	 * 
	 * If cookies are equal (same name, path and comain) then the incoming cookie is favoured over the current cookie
	 * 
	 * @param current	The cookies already known
	 * @param incoming	The new cookies
	 * 
	 * 
	 */
	protected static Cookie[] mergeCookies(Cookie[] current, Cookie[] incoming)
	{
		if(current == null)
		{
			if(incoming == null)
				return null;
			else if(incoming.length == 0)
				return null;
			else
				return incoming;
		}
		
		if(incoming == null)
			return current;
		
		
		java.util.HashMap replacements = new java.util.HashMap();
		java.util.Vector additions = new java.util.Vector();
		
		for(int i = 0; i < incoming.length; i++)
		{
			boolean cookieExists = false;
			
			for(int c = 0; c < current.length; i++)
			{
				if(current[c].equals(incoming[i]))
				{
					//replacement				
					replacements.put(new Integer(c), incoming[i]);
					cookieExists = true;
					break;
				}
			}
			
			if(!cookieExists)
			{
				//add
				additions.add(incoming[i]);
			}
		}
		
		Cookie[] merged = new Cookie[current.length + additions.size()];
		//resolve replacements/copies
		for(int c = 0; c < current.length; c++)
		{
			if(replacements.containsKey(new Integer(c)))
			{
				//replace
				merged[c] = (Cookie)replacements.get(new Integer(c));
			}
			else
			{
				//copy
				merged[c] = current[c];
			}
		}
		//resolve additions
		for(int a = 0; a < additions.size(); a++)
		{
			int offset = current.length + a;
			merged[offset] = (Cookie)additions.get(a);
		}
		
		return merged;
	}
}
