/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
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
package org.exist.xquery.functions.session;

//import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.response.ResponseModule;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author Wolfgang Meier
 * @author Loren Cahlander
 */
public class EncodeURL extends BasicFunction {

//	private static final Logger logger = Logger.getLogger(EncodeURL.class);
	
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("encode-url", SessionModule.NAMESPACE_URI, SessionModule.PREFIX),
			"Encodes the specified URL with the current HTTP session-id.",
			new SequenceType[] {
				new FunctionParameterSequenceType("url", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The URL to encode")
			},
			new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE, "the encoded URL"));
	
	public EncodeURL(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Function#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(
		Sequence args[],
		Sequence contextSequence)
		throws XPathException {
		
		final ResponseModule myModule = (ResponseModule)context.getModule(ResponseModule.NAMESPACE_URI);
			
		// request object is read from global variable $response
		final Variable var = myModule.resolveVariable(ResponseModule.RESPONSE_VAR);
		if(var == null || var.getValue() == null) {
			throw new XPathException(this, "No request object found in the current XQuery context.");
		}
		if(var.getValue().getItemType() != Type.JAVA_OBJECT) {
			throw new XPathException(this, "Variable $response is not bound to an Java object.");
		}
		
		// get parameters
		final String url = args[0].getStringValue();
		
		final JavaObjectValue value = (JavaObjectValue)
			var.getValue().itemAt(0);
		if(value.getObject() instanceof ResponseWrapper) {
			return new AnyURIValue(((ResponseWrapper)value.getObject()).encodeURL(url));
		} else {
			throw new XPathException(this, "Variable $response is not bound to a Response object.");
		}
	}
}
