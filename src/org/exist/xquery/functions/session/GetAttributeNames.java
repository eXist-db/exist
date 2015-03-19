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

import java.util.Enumeration;

//import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.http.servlets.SessionWrapper;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 * @author Loren Cahlander
 */
public class GetAttributeNames extends BasicFunction {
	
//	private static final Logger logger = LogManager.getLogger(GetAttributeNames.class);

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("get-attribute-names", SessionModule.NAMESPACE_URI, SessionModule.PREFIX),
			"Returns a sequence containing the names of all session attributes defined within the "
				+ "current HTTP session.",
			null,
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "the list of attribute names"));
	
	/**
	 * @param context
	 */
	public GetAttributeNames(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
		
		final SessionModule myModule = (SessionModule) context.getModule(SessionModule.NAMESPACE_URI);

		// session object is read from global variable $session
		final Variable var = myModule.resolveVariable(SessionModule.SESSION_VAR);
		if(var == null || var.getValue() == null)
			{throw new XPathException(this, "Session not set");}
		if (var.getValue().getItemType() != Type.JAVA_OBJECT)
			{throw new XPathException(this, "Variable $session is not bound to an Java object.");}
		final JavaObjectValue session = (JavaObjectValue) var.getValue().itemAt(0);
		
		if(session.getObject() instanceof SessionWrapper)
		{
			final ValueSequence result = new ValueSequence();
			final SessionWrapper sessionWrapper = (SessionWrapper)session.getObject();
			for(final Enumeration<String> e = sessionWrapper.getAttributeNames(); e.hasMoreElements();)
			{
				final String attribName = e.nextElement();
				result.add(new StringValue(attribName));
			}
			return result;
		}
		else
			{throw new XPathException(this, "Type error: variable $session is not bound to a session object");}
	}
}
