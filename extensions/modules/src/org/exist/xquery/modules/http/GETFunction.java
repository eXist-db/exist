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
import org.apache.commons.httpclient.Header;
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
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

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
		
		//setup get content
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
		int result = -1;
		try
		{
			HttpClient http = new HttpClient();
			result = http.executeMethod(get);
			
			//persist cookies
			if(persistCookies)
			{
				HttpState state = http.getState();
				Cookie[] incomingCookies = state.getCookies();
				Cookie[] currentCookies = (Cookie[])context.getXQueryContextVar(HTTPModule.HTTP_MODULE_PERSISTENT_COOKIES);
				context.setXQueryContextVar(HTTPModule.HTTP_MODULE_PERSISTENT_COOKIES, HTTPModule.mergeCookies(currentCookies, incomingCookies));
			}
			
			//determine the type of the response document
			Header responseContentType = get.getResponseHeader("Content-Type");
			String responseMimeType = responseContentType.getValue().substring(0, responseContentType.getValue().indexOf(';'));
			MimeTable mimeTable = MimeTable.getInstance();
			MimeType mimeType = mimeTable.getContentType(responseMimeType);
			
			//return the data
			if(mimeType.isXMLType())
			{
				// xml response
				return ModuleUtils.stringToXML(context, get.getResponseBodyAsString());
			}
			else
			{
				if(mimeType.getName().equals(MimeType.HTML_TYPE.getName()))
				{
					// html response
					/*if(isValidXHTML)
					{
						return xhtml;
					}
					else
					{
						//tidy up the html
					}*/
					
					return Sequence.EMPTY_SEQUENCE;
				}
				else
				{
					// assume binary response, encode as base64
					Base64Encoder enc = new Base64Encoder();
					enc.translate(get.getResponseBody());
					return new StringValue(enc.getCharArray().toString());
				}
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			get.releaseConnection();
		}
		
		return Sequence.EMPTY_SEQUENCE;
	}
}
