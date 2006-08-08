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
 *  $Id: GetRequestData.java 2436 2006-01-07 21:47:15 +0000 (Sat, 07 Jan 2006) brihaye $
 */
package org.exist.xquery.functions.request;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.exist.dom.QName;
import org.exist.http.servlets.RequestWrapper;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;


/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class GetData extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName(
				"get-data",
				RequestModule.NAMESPACE_URI,
				RequestModule.PREFIX),
			"Returns the content of a POST request as an XML document or a string representaion. Returns an empty sequence if there is no data.",
			null,
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE));
	
	public final static FunctionSignature deprecated =
		new FunctionSignature(
			new QName(
				"get-request-data",
				RequestModule.NAMESPACE_URI,
				RequestModule.PREFIX),
			"Returns the content of a POST request as an XML document or a string representaion. Returns an empty sequence if there is no data.",
			null,
			new SequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE),
			"Renamed to get-data.");
	
	public GetData(XQueryContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		RequestModule myModule =
			(RequestModule) context.getModule(RequestModule.NAMESPACE_URI);

		// request object is read from global variable $request
		Variable var = myModule.resolveVariable(RequestModule.REQUEST_VAR);
		if(var == null)
			throw new XPathException("No request object found in the current XQuery context.");
		if (var.getValue().getItemType() != Type.JAVA_OBJECT)
			throw new XPathException("Variable $request is not bound to an Java object.");
		JavaObjectValue value = (JavaObjectValue) var.getValue().itemAt(0);
		
		if(value.getObject() instanceof RequestWrapper)
		{
			RequestWrapper request = (RequestWrapper)value.getObject();	
			
			//if the content length is unknown, return
			if(request.getContentLength() == -1)
			{
				return Sequence.EMPTY_SEQUENCE;
			}
			
			//first, get the content of the request
			try
			{
				InputStream is = request.getInputStream();
				ByteArrayOutputStream bos = new ByteArrayOutputStream(request
						.getContentLength());
				byte[] buf = new byte[256];
				int l = 0;
				while ((l = is.read(buf)) > -1) {
					bos.write(buf, 0, l);
				}
				String encoding = request.getCharacterEncoding();
				if(encoding == null)
					encoding = "UTF-8";
				String s = new String(bos.toByteArray(), encoding);
				return new StringValue(s);
			} catch (IOException e) {
				throw new XPathException("An IO exception ocurred: " + e.getMessage(), e);
			}
		} else
			throw new XPathException("Variable $request is not bound to a Request object.");
	}
}
