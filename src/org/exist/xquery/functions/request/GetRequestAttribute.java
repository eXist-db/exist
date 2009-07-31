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
 *  $Id$
 */
package org.exist.xquery.functions.request;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.http.servlets.RequestWrapper;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XPathUtil;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class GetRequestAttribute extends BasicFunction {

	protected static final Logger logger = Logger.getLogger(GetRequestAttribute.class);

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("get-attribute", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
			"Returns the string value of the request attribute specified in the argument or the empty " +
            "sequence if no such attribute exists. The attribute value should be a string.",
			new SequenceType[] {
                    new FunctionParameterSequenceType("attribute-name", Type.STRING, Cardinality.EXACTLY_ONE, "Name of the attribute")
            },
			new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the string value of the requested attribute"));

	/**
	 * @param context
	 */
	public GetRequestAttribute(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
		
        String name = args[0].getStringValue();
        RequestModule myModule = (RequestModule)context.getModule(RequestModule.NAMESPACE_URI);
		
		// request object is read from global variable $request
		Variable var = myModule.resolveVariable(RequestModule.REQUEST_VAR);
		if(var == null || var.getValue() == null)
			throw new XPathException(this, "No request object found in the current XQuery context.");
		if (var.getValue().getItemType() != Type.JAVA_OBJECT)
			throw new XPathException(this, "Variable $request is not bound to an Java object.");

		JavaObjectValue value = (JavaObjectValue) var.getValue().itemAt(0);
		if (value.getObject() instanceof RequestWrapper) {
			Object attrib = ((RequestWrapper) value.getObject()).getAttribute(name);
            return attrib == null ? Sequence.EMPTY_SEQUENCE : XPathUtil.javaObjectToXPath(attrib, context);
        } else
			throw new XPathException(this, "Variable $request is not bound to a Request object.");
	}
	
}
