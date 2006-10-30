/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist-db.org
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
 *  $Id: InvalidateSession.java 325 2004-05-13 13:33:08 +0100 (Thu, 13 May 2004) wolfgang_m $
 */
package org.exist.xquery.functions.session;

import org.exist.dom.QName;
import org.exist.http.servlets.SessionWrapper;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.request.RequestModule;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;


/**
 * @author wolf
 */
public class Invalidate extends BasicFunction {

    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("invalidate", SessionModule.NAMESPACE_URI, SessionModule.PREFIX),
			"Invalidate (remove) the current HTTP session if present",
			null,
			new SequenceType(Type.ITEM, Cardinality.EMPTY));
    
    public final static FunctionSignature deprecated =
		new FunctionSignature(
			new QName("invalidate-session", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
			"Invalidate (remove) the current HTTP session if present",
			null,
			new SequenceType(Type.ITEM, Cardinality.EMPTY),
			"Moved to 'session' module and renamed to session:invalidate");
    
    /**
     * @param context
     */
    public Invalidate(XQueryContext context) {
        super(context, signature);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {
        SessionModule myModule = (SessionModule)context.getModule(SessionModule.NAMESPACE_URI);
        // session object is read from global variable $session
		Variable var = myModule.resolveVariable(SessionModule.SESSION_VAR);
		if(var == null || var.getValue() == null)
			throw new XPathException("Session not set");
		if(var.getValue().getItemType() != Type.JAVA_OBJECT)
			throw new XPathException("Variable $session is not bound to an Java object.");
		JavaObjectValue value = (JavaObjectValue) var.getValue().itemAt(0);
		if(value.getObject() instanceof SessionWrapper) {
			SessionWrapper session = (SessionWrapper)value.getObject();
			session.invalidate();
			return Sequence.EMPTY_SEQUENCE;
		} else
			throw new XPathException("Type error: variable $session is not bound to a session object");
    }

}
