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
 *  $Id$
 */
package org.exist.xpath.functions.request;

import org.exist.dom.QName;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.xpath.BasicFunction;
import org.exist.xpath.Cardinality;
import org.exist.xpath.FunctionSignature;
import org.exist.xpath.Variable;
import org.exist.xpath.XPathException;
import org.exist.xpath.XQueryContext;
import org.exist.xpath.value.AnyURIValue;
import org.exist.xpath.value.JavaObjectValue;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;

/**
 * @author wolf
 */
public class EncodeURL extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("encode-url", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
			"Encodes the specified URL with the current HTTP session-id.",
			new SequenceType[] {
				new SequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE));
	
	public EncodeURL(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Function#eval(org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(
		Sequence args[],
		Sequence contextSequence)
		throws XPathException {
		
		RequestModule myModule = (RequestModule)context.getModule(RequestModule.NAMESPACE_URI);
			
		// request object is read from global variable $request
		Variable var = myModule.resolveVariable(RequestModule.RESPONSE_VAR);
		if(var.getValue().getItemType() != Type.JAVA_OBJECT)
			throw new XPathException("Variable $response is not bound to an Java object.");
		
		// get parameters
		String url = args[0].getStringValue();
		
		JavaObjectValue value = (JavaObjectValue)
			var.getValue().itemAt(0);
		if(value.getObject() instanceof ResponseWrapper)
			return new AnyURIValue(((ResponseWrapper)value.getObject()).encodeURL(url));
		else
			throw new XPathException("Variable $response is not bound to a Response object.");
	}
}
