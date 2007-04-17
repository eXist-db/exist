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
 *  $Id:$
 */
package org.exist.xquery.modules.http;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.methods.GetMethod;

import org.exist.dom.QName;
import org.exist.util.Base64Encoder;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public class GETFunction extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("get", HTTPModule.NAMESPACE_URI, HTTPModule.PREFIX),
			"Performs a HTTP GET request. $a is the URL, $b determines if cookies persist for the query lifetime.",
			new SequenceType[] {
				new SequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE));

	public GETFunction(XQueryContext context)
	{
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		// must be a URL
		if(args[0].isEmpty())
			return Sequence.EMPTY_SEQUENCE;
		
		//get the url
		String url = args[0].itemAt(0).getStringValue();
		//get the persist cookies
		boolean persistCookies = args[1].effectiveBooleanValue();
		
		//setup get request
		GetMethod get = new GetMethod(url);
		
        //use existing cookies
        if(persistCookies)
        {
	        Cookie[] cookies = (Cookie[])context.getXQueryContextVar(HTTPModule.HTTP_MODULE_PERSISTENT_COOKIES);
			if(cookies != null)
			{
				for(int c = 0; c < cookies.length; c++)
				{
					get.setRequestHeader("Cookie", cookies[c].toExternalForm());
				}
			}
        }
        
		//execute the request
		try
		{
			HttpClient http = null;
			try
			{
				http = new HttpClient();
				int result = http.executeMethod(get);
			}
			catch(IOException ioe)
			{
				throw new XPathException(ioe);
			}
			
			//persist cookies
			if(persistCookies)
			{
				HttpState state = http.getState();
				Cookie[] incomingCookies = state.getCookies();
				Cookie[] currentCookies = (Cookie[])context.getXQueryContextVar(HTTPModule.HTTP_MODULE_PERSISTENT_COOKIES);
				context.setXQueryContextVar(HTTPModule.HTTP_MODULE_PERSISTENT_COOKIES, HTTPModule.mergeCookies(currentCookies, incomingCookies));
			}
			
			//try and parse the response as XML
			try
			{
				return ModuleUtils.stringToXML(context, get.getResponseBodyAsString());
			}
			catch(IOException ioe)
			{
				throw new XPathException(ioe);
			}
			catch(SAXException se)
			{
				//could not parse to xml
			}
			
			//response is NOT parseable as XML, determine the type of the response document
			String responseContentType = get.getResponseHeader("Content-Type").getValue();
			int contentTypeEnd = responseContentType.indexOf(";");
			if(contentTypeEnd == -1)
			{
				contentTypeEnd = responseContentType.length();
			}
			String responseMimeType = responseContentType.substring(0, contentTypeEnd);
			MimeTable mimeTable = MimeTable.getInstance();
			MimeType mimeType = mimeTable.getContentType(responseMimeType);
			
			//is it a html document?
			if(mimeType.getName().equals(MimeType.HTML_TYPE.getName()))
			{
				//html document
				try
				{
					//parse html to xml(html)
					return ModuleUtils.htmlToXHtml(context, url, new InputSource(get.getResponseBodyAsStream()));
				}
				catch(IOException ioe)
				{
					throw new XPathException(ioe);
				}
				catch(SAXException se)
				{
					//counld not parse to xml(html)
				}
			}
	
			try
			{
				//other document type, assume binary so base64 encode
				Base64Encoder enc = new Base64Encoder();
				enc.translate(get.getResponseBody());
				return new StringValue(enc.getCharArray().toString());
			}
			catch(IOException ioe)
			{
				throw new XPathException(ioe);
			}
		}	
		finally
		{
			get.releaseConnection();
		}
	}
}
