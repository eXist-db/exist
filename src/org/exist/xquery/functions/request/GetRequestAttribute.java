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
package org.exist.xquery.functions.request;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.http.servlets.RequestWrapper;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XPathUtil;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;

import java.util.Enumeration;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class GetRequestAttribute extends BasicFunction {

	protected static final Logger logger = LogManager.getLogger(GetRequestAttribute.class);

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("get-attribute", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
			"Returns the string value of the request attribute specified in the argument or the empty " +
            "sequence if no such attribute exists. The attribute value should be a string.",
			new SequenceType[] {
                    new FunctionParameterSequenceType("attribute-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the attribute")
            },
			new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the string value of the requested attribute")),
        new FunctionSignature(
			new QName("attribute-names", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
			"Returns the names of all request attributes in the current request.",
			null,
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "the names of all attributes attached to the " +
                "current request")
        )
    };

	/**
	 * @param context
	 */
	public GetRequestAttribute(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {

        final RequestModule myModule = (RequestModule)context.getModule(RequestModule.NAMESPACE_URI);
		
		// request object is read from global variable $request
		final Variable var = myModule.resolveVariable(RequestModule.REQUEST_VAR);
		if(var == null || var.getValue() == null)
			{throw new XPathException(this, "No request object found in the current XQuery context.");}
		if (var.getValue().getItemType() != Type.JAVA_OBJECT)
			{throw new XPathException(this, "Variable $request is not bound to an Java object.");}

		final JavaObjectValue value = (JavaObjectValue) var.getValue().itemAt(0);
		if (value.getObject() instanceof RequestWrapper) {
            if (isCalledAs("get-attribute")) {
                final String name = args[0].getStringValue();
                final Object attrib = ((RequestWrapper) value.getObject()).getAttribute(name);
                return attrib == null ? Sequence.EMPTY_SEQUENCE : XPathUtil.javaObjectToXPath(attrib, context);
            } else {
                final ValueSequence names = new ValueSequence();
                for (final Enumeration<String> e = ((RequestWrapper) value.getObject()).getAttributeNames(); e.hasMoreElements(); ) {
                    names.add(new StringValue(e.nextElement().toString()));
                }
                return names;
            }
        } else
			{throw new XPathException(this, "Variable $request is not bound to a Request object.");}
	}
	
}
