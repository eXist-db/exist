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

import org.apache.commons.httpclient.methods.GetMethod;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.io.IOException;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @serial 20070418
 * @version 1.0
 */
public class GETFunction extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("get", HTTPModule.NAMESPACE_URI, HTTPModule.PREFIX),
			"Performs a HTTP GET request. $a is the URL, $b determines if cookies persist for the query lifetime."
			+ " XML data will be returned as a Node, HTML data will be tidied into an XML compatible form, any other content will be returned as xs:base64Binary encoded data.",
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
		
		//setup GET request
		GetMethod get = new GetMethod(url);
		
		try
		{
			//execute the request
			HTTPModule.doRequest(context, get, persistCookies);	
			
			//convert/parse and return the result
			return HTTPModule.httpResponseDataToXQueryDataType(context, get);
		}
		catch(IOException ioe)
		{
			throw new XPathException(ioe);
		}
		finally
		{
			get.releaseConnection();
		}
	}
}
