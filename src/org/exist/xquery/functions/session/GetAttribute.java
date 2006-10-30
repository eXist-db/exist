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
 *  $Id: GetSessionAttribute.java 2980 2006-03-26 22:18:09 +0100 (Sun, 26 Mar 2006) deliriumsky $
 */
package org.exist.xquery.functions.session;

import org.exist.dom.QName;
import org.exist.http.servlets.SessionWrapper;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XPathUtil;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.request.RequestModule;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Returns an attribute stored in the current session or an empty sequence
 * if the attribute does not exist.
 * 
 * @author wolf
 */
public class GetAttribute extends Function {
	
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("get-attribute", SessionModule.NAMESPACE_URI, SessionModule.PREFIX),
			"Returns an attribute stored in the current session object or an empty sequence " +
			"if the attribute cannot be found.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE));
	
	public final static FunctionSignature deprecated =
		new FunctionSignature(
			new QName("get-session-attribute", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
			"Returns an attribute stored in the current session object or an empty sequence " +
			"if the attribute cannot be found.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE),
			"Moved to 'session' module. Renamed to session:get-attribute");
		
	public GetAttribute(XQueryContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		
		SessionModule myModule = (SessionModule)context.getModule(SessionModule.NAMESPACE_URI);
		
		// session object is read from global variable $session
		Variable var = myModule.resolveVariable(SessionModule.SESSION_VAR);
		if(var == null || var.getValue() == null)
			throw new XPathException("Session not set");
		if(var.getValue().getItemType() != Type.JAVA_OBJECT)
			throw new XPathException("Variable $session is not bound to a Java object.");
		JavaObjectValue session = (JavaObjectValue) var.getValue().itemAt(0);
		
		// get attribute name parameter
		String attribName = getArgument(0).eval(contextSequence, contextItem).getStringValue();
		
		if(session.getObject() instanceof SessionWrapper)
		{
			try
			{
				Object o = ((SessionWrapper)session.getObject()).getAttribute(attribName);
				if (o == null)
					return Sequence.EMPTY_SEQUENCE;
				return XPathUtil.javaObjectToXPath(o, context);
			}
			catch(IllegalStateException ise)
			{
				//TODO: if we throw an exception here it means that getAttribute()
				//cannot be called after invalidate() on the session object. This is the 
				//way that it works in Java, however this isnt the way it works in xquery currently
				//we can change this but we need to be aware of the consequences, the eXist admin webapp is a
				//good example of what happens if you change this - try logging out of the webapp ;-)
				// - deliriumsky
				
				//log.error(ise.getStackTrace());	
				//throw new XPathException(getASTNode(), "Session has an IllegalStateException for getAttribute() - " + ise.getStackTrace() + System.getProperty("line.separator") + System.getProperty("line.separator") + "Did you perhaps call session:invalidate() previously?");

				return Sequence.EMPTY_SEQUENCE;
			}
		}
		else
			throw new XPathException("Type error: variable $session is not bound to a session object");
	}
}
