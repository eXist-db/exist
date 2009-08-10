/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
 * http://exist-db.org
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
package org.exist.xquery.functions;

import org.apache.log4j.Logger;

import java.util.Iterator;
import java.util.List;

import org.exist.dom.QName;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Atomize;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.DynamicCardinalityCheck;
import org.exist.xquery.Expression;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.Error;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * Implements the library function fn:concat().
 *
 */
public class FunConcat extends Function {
		protected static final Logger logger = Logger.getLogger(FunConcat.class);
	public final static FunctionSignature signature =
			new FunctionSignature(
				new QName("concat", Function.BUILTIN_FUNCTION_NS),
				"Accepts two or more xdt:anyAtomicType arguments, $atomizable-values, and converts them " +
				"to xs:string. Returns the xs:string that is the concatenation of the values " +
				"of its arguments after conversion. If any of the arguments is the empty sequence, " +
				"the argument is treated as the zero-length string.",
				new SequenceType[] {
                    //More complicated : see below
				    new FunctionParameterSequenceType("atomizable-values", Type.ATOMIC, Cardinality.ZERO_OR_ONE, "The atomizable values")
				},
				new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the concatenated values"),
				true
			);
			
	public FunConcat(XQueryContext context) {
		super(context, signature);
	}

	//Why a specific method here ?
	public int returnsType() {
		return Type.STRING;
	}
	
	/**
	 * Overloaded function: no static type checking.
	 * 
	 * @see org.exist.xquery.Function#setArguments(java.util.List)
	 */
	public void setArguments(List arguments) throws XPathException {
		for(Iterator i = arguments.iterator(); i.hasNext(); ) {
			Expression next = (Expression) i.next();
            next = new DynamicCardinalityCheck(context, Cardinality.ZERO_OR_ONE, next,
                    new Error(Error.FUNC_PARAM_CARDINALITY, "1", mySignature));                
            if (!Type.subTypeOf(next.returnsType(), Type.ATOMIC))
                next = new Atomize(context, next);
			steps.add(next);
		}
	}
    
	//Why a specific method here ?
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    	contextInfo.setParent(this);
        // call analyze for each argument
        inPredicate = (contextInfo.getFlags() & IN_PREDICATE) > 0;
        for(int i = 0; i < getArgumentCount(); i++) {
            getArgument(i).analyze(contextInfo);
        }        
    }
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
       if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
           
		if(getArgumentCount() < 2) {
            logger.error("fn:concat() requires at least two arguments");
			throw new XPathException (this, "concat requires at least two arguments");
		}
        
		StringBuilder concat = new StringBuilder();     
		for (int i = 0; i < getArgumentCount(); i++) {
            concat.append(getArgument(i).eval(contextSequence, contextItem).getStringValue());
		}
		Sequence result = new StringValue(concat.toString());

        if (context.getProfiler().isEnabled())
            context.getProfiler().end(this, "", result);
        
        return result;  
	}
}
