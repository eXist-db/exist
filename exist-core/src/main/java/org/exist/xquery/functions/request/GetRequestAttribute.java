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
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import javax.annotation.Nonnull;
import java.util.Enumeration;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class GetRequestAttribute extends StrictRequestFunction {

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

	public GetRequestAttribute(final XQueryContext context, final FunctionSignature signature) {
		super(context, signature);
	}

	@Override
	public Sequence eval(final Sequence[] args, @Nonnull final RequestWrapper request)
			throws XPathException {

		if (isCalledAs("get-attribute")) {
			final String name = args[0].getStringValue();
			final Object attrib = request.getAttribute(name);
			return attrib == null ? Sequence.EMPTY_SEQUENCE : XPathUtil.javaObjectToXPath(attrib, context);
		} else {
			final Enumeration<String> attributeNames = request.getAttributeNames();
			if (!attributeNames.hasMoreElements()) {
				return Sequence.EMPTY_SEQUENCE;
			}

			final ValueSequence names = new ValueSequence();
			while (attributeNames.hasMoreElements()) {
				names.add(new StringValue(attributeNames.nextElement()));
			}
			return names;
		}
	}
}
