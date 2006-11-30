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
package org.exist.xquery.functions.response;

import org.exist.dom.QName;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.request.RequestModule;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Set's a HTTP header on the HTTP Response
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * 
 * @see org.exist.xquery.Function
 */
public class SetHeader extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("set-header", ResponseModule.NAMESPACE_URI, ResponseModule.PREFIX),
			"Set's a HTTP Header on the HTTP Response. $a is the header name, $b is the header value.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.ITEM, Cardinality.EMPTY));

	public final static FunctionSignature deprecated =
		new FunctionSignature(
			new QName("set-response-header", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
			"Set's a HTTP Header on the HTTP Response. $a is the header name, $b is the header value.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.ITEM, Cardinality.EMPTY),
			"Moved to response module and renamed to response:set-header.");
	
	public SetHeader(XQueryContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
        
		ResponseModule myModule = (ResponseModule)context.getModule(ResponseModule.NAMESPACE_URI);

		// response object is read from global variable $response
		Variable var = myModule.resolveVariable(ResponseModule.RESPONSE_VAR);
		if(var == null || var.getValue() == null)
			throw new XPathException("Response not set");
		if(var.getValue().getItemType() != Type.JAVA_OBJECT)
			throw new XPathException("Variable $response is not bound to a Java object.");
		JavaObjectValue response = (JavaObjectValue)var.getValue().itemAt(0);
		
		//get parameters
		String name = getArgument(0).eval(contextSequence, contextItem).getStringValue();
		String value = getArgument(1).eval(contextSequence, contextItem).getStringValue();
		
		//set response header
		if(response.getObject() instanceof ResponseWrapper)
			((ResponseWrapper)response.getObject()).setHeader(name, value);
		else
			throw new XPathException("Type error: variable $response is not bound to a response object");
			
		return Sequence.EMPTY_SEQUENCE;
	}
}
