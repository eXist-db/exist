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

//import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.http.servlets.SessionWrapper;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author Adam Retter (adam.retter@devon.gov.uk)
 * @author Loren Cahlander
 */
public class RemoveAttribute extends Function {

//	private static final Logger logger = LogManager.getLogger(RemoveAttribute.class);
	
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("remove-attribute", SessionModule.NAMESPACE_URI, SessionModule.PREFIX),
			"Removes the attribute with the supplied name from the current session",
			new SequenceType[] {
				new FunctionParameterSequenceType("name", Type.STRING, Cardinality.EXACTLY_ONE, "The attribute name")
			},
			new SequenceType(Type.ITEM, Cardinality.EMPTY));

	public RemoveAttribute(XQueryContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }
        
		final SessionModule myModule = (SessionModule)context.getModule(SessionModule.NAMESPACE_URI);

		// session object is read from global variable $session
		final Variable var = myModule.resolveVariable(SessionModule.SESSION_VAR);
		if(var == null || var.getValue() == null)
			{throw new XPathException(this, "Session not set");}
		if(var.getValue().getItemType() != Type.JAVA_OBJECT)
			{throw new XPathException(this, "Variable $session is not bound to a Java object.");}
		final JavaObjectValue session = (JavaObjectValue) var.getValue().itemAt(0);
		
		// get attribute name parameter
		final String attrib = getArgument(0).eval(contextSequence, contextItem).getStringValue();
		if(session.getObject() instanceof SessionWrapper)
			{((SessionWrapper)session.getObject()).removeAttribute(attrib);}
		else
			{throw new XPathException(this, "Type error: variable $session is not bound to a session object");}

		return Sequence.EMPTY_SEQUENCE;
	}
}
