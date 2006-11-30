/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2006 The eXist team
 *  http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
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
import org.exist.xquery.value.Item;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.IntegerValue;

/**
 * Set's a HTTP Cookie on the HTTP Response
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * 
 * @see org.exist.xquery.Function
 */
public class SetCookie extends Function {

    public final static FunctionSignature signatures[] = {
	new FunctionSignature(
			      new QName("set-cookie", ResponseModule.NAMESPACE_URI, ResponseModule.PREFIX),
			      "Set's a HTTP Cookie on the HTTP Response. $a is the cookie name, $b is the cookie value.",
			      new SequenceType[] {
				  new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				  new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			      },
			      new SequenceType(Type.ITEM, Cardinality.EMPTY)),
	new FunctionSignature(
			      new QName("set-cookie", ResponseModule.NAMESPACE_URI, ResponseModule.PREFIX),
			      "Set's a HTTP Cookie on the HTTP Response. $a is the cookie name, $b is the cookie value, and $c is the maxAge of the cookie.",
			      new SequenceType[] {
				  new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				  new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				  new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE)
			      },
			      new SequenceType(Type.ITEM, Cardinality.EMPTY)) 
    };

	public SetCookie(XQueryContext context, FunctionSignature signature) {
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
		Sequence ageSeq = getArgument(2).eval(contextSequence, contextItem);
		//set response header
		if(response.getObject() instanceof ResponseWrapper) {
		    if (ageSeq.isEmpty()) {
			((ResponseWrapper)response.getObject()).addCookie(name, value);
		    } else {
			int maxAge = ((IntegerValue) ageSeq.convertTo(Type.INTEGER)).getInt();
			((ResponseWrapper)response.getObject()).addCookie(name, value, maxAge);
		    }
		} else {
		    throw new XPathException("Type error: variable $response is not bound to a response object");
		}
		return Sequence.EMPTY_SEQUENCE;
	}
}
