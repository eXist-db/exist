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

import javax.servlet.http.HttpSession;

import org.apache.cocoon.environment.Session;
import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.xpath.Cardinality;
import org.exist.xpath.Function;
import org.exist.xpath.FunctionSignature;
import org.exist.xpath.StaticContext;
import org.exist.xpath.Variable;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.JavaObjectValue;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;

/**
 * @author wolf
 */
public class SetSessionAttribute extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("set-session-attribute", REQUEST_FUNCTION_NS, "request"),
			"Stores a value in the current session using the supplied attribute name.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
			},
			new SequenceType(Type.ITEM, Cardinality.EMPTY));

	public SetSessionAttribute(StaticContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		// session object is read from global variable $session
		Variable var = context.resolveVariable("session");
		if(var.getValue() == null)
			throw new XPathException("Session not set");
		if(var.getValue().getItemType() != Type.JAVA_OBJECT)
			throw new XPathException("Variable $session is not bound to an Java object.");
		JavaObjectValue session = (JavaObjectValue) var.getValue().itemAt(0);
		
		// get attribute name parameter
		String attrib = getArgument(0).eval(contextSequence, contextItem).getStringValue();
		Sequence value = getArgument(1).eval(contextSequence, contextItem);
		if(session.getObject() instanceof Session)
			((Session)session.getObject()).setAttribute(attrib, value);
		else if(session.getObject() instanceof HttpSession)
			((HttpSession)session.getObject()).setAttribute(attrib, value);
		else
			throw new XPathException("Type error: variable $session is not bound to a session object");
		return Sequence.EMPTY_SEQUENCE;
	}
}
