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
import org.exist.http.servlets.SessionWrapper;
import org.exist.xpath.Cardinality;
import org.exist.xpath.Function;
import org.exist.xpath.FunctionSignature;
import org.exist.xpath.Variable;
import org.exist.xpath.XPathException;
import org.exist.xpath.XPathUtil;
import org.exist.xpath.XQueryContext;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.JavaObjectValue;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;

/**
 * Returns an attribute stored in the current session or an empty sequence
 * if the attribute does not exist.
 * 
 * @author wolf
 */
public class GetSessionAttribute extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("get-session-attribute", REQUEST_FUNCTION_NS, "request"),
			"Returns an attribute stored in the current session object or an empty sequence " +
			"if the attribute cannot be found.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE));
		
	public GetSessionAttribute(XQueryContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		RequestModule myModule = (RequestModule)context.getModule(RequestModule.NAMESPACE_URI);
		
		// session object is read from global variable $session
		Variable var = myModule.resolveVariable(RequestModule.SESSION_VAR);
		if(var.getValue() == null)
			throw new XPathException("Session not set");
		if(var.getValue().getItemType() != Type.JAVA_OBJECT)
			throw new XPathException("Variable $session is not bound to an Java object.");
		JavaObjectValue value = (JavaObjectValue) var.getValue().itemAt(0);
		
		// get attribute name parameter
		String attrib = getArgument(0).eval(contextSequence, contextItem).getStringValue();
		
		if(value.getObject() instanceof SessionWrapper) {
			SessionWrapper session = (SessionWrapper)value.getObject();
			Object o = session.getAttribute(attrib);
			if (o == null)
				return Sequence.EMPTY_SEQUENCE;
			return XPathUtil.javaObjectToXPath(o);
		} else
			throw new XPathException("Type error: variable $session is not bound to a session object");
	}
}
