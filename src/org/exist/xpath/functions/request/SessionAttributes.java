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

import java.util.Enumeration;

import org.exist.dom.QName;
import org.exist.http.servlets.SessionWrapper;
import org.exist.xpath.BasicFunction;
import org.exist.xpath.Cardinality;
import org.exist.xpath.FunctionSignature;
import org.exist.xpath.Variable;
import org.exist.xpath.XPathException;
import org.exist.xpath.XQueryContext;
import org.exist.xpath.value.JavaObjectValue;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.StringValue;
import org.exist.xpath.value.Type;
import org.exist.xpath.value.ValueSequence;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class SessionAttributes extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName(
				"session-attributes",
				RequestModule.NAMESPACE_URI,
				RequestModule.PREFIX),
			"Returns a sequence containing the names of all session attributes defined within the "
				+ "current HTTP session.",
			null,
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE));

	/**
	 * @param context
	 * @param signature
	 */
	public SessionAttributes(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.BasicFunction#eval(org.exist.xpath.value.Sequence[], org.exist.xpath.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
		RequestModule myModule =
			(RequestModule) context.getModule(RequestModule.NAMESPACE_URI);

		// session object is read from global variable $session
		Variable var = myModule.resolveVariable(RequestModule.SESSION_VAR);
		if (var.getValue().getItemType() != Type.JAVA_OBJECT)
			throw new XPathException("Variable $session is not bound to an Java object.");

		JavaObjectValue value = (JavaObjectValue) var.getValue().itemAt(0);
		if (value.getObject() == null)
			throw new XPathException("No session available");
		else if (value.getObject() instanceof SessionWrapper) {
			ValueSequence result = new ValueSequence();
			for (Enumeration enum =
				((SessionWrapper) value.getObject()).getAttributeNames();
				enum.hasMoreElements();
				) {
				String param = (String) enum.nextElement();
				result.add(new StringValue(param));
			}
			return result;
		} else
			throw new XPathException(
				"Variable $session is not bound to a Session object; got: "
					+ value.getObject().getClass().getName());
	}
}
