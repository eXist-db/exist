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
package org.exist.xquery.functions.fn;

import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Expression;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class FunNot extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("not", Function.BUILTIN_FUNCTION_NS),
			" Returns true if the effective boolean " +
			"value is false, and false if the effective boolean value is true. \n\n $arg is reduced to an effective boolean value by applying " +
			"the fn:boolean() function.",
			new SequenceType[] {
				 new FunctionParameterSequenceType("arg", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input items")},
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "the negated effective boolean value (ebv) of $arg"));

	@SuppressWarnings("unused")
	private boolean inWhereClause = false;
	
	public FunNot(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Function#analyze(org.exist.xquery.Expression, int)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        inWhereClause = (contextInfo.getFlags() & IN_WHERE_CLAUSE) != 0;
    }
    
	public int returnsType() {
		//TODO: test for possible performance lost
		//return Type.BOOLEAN;
		return Type.subTypeOf(getArgument(0).returnsType(), Type.NODE)
			? Type.NODE
			: Type.BOOLEAN;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.functions.Function#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_SET | getArgument(0).getDependencies();
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
       if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }
           
		if(contextItem != null)
			{contextSequence = contextItem.toSequence();}
		
        Sequence result;
        final Expression arg = getArgument(0);		
		// case 1: if the argument expression returns a node set,
		// subtract the set from the context node set and return
		// the remaining set
		if (Type.subTypeOf(arg.returnsType(), Type.NODE) &&
            (contextSequence == null || contextSequence.isPersistentSet()) &&
            !Dependency.dependsOn(arg, Dependency.CONTEXT_ITEM)) {
			if (contextSequence == null || contextSequence.isEmpty()) {
				// TODO: special treatment if the context sequence is empty:
				// within a predicate, we just return the empty sequence
				// otherwise evaluate the argument and return a boolean result			    
//				if (inPredicate && !inWhereClause)
//                    result = Sequence.EMPTY_SEQUENCE;
//				else
                    result = evalBoolean(contextSequence, contextItem, arg);
			} else {
    			result = contextSequence.toNodeSet().copy();

    			if (inPredicate) {
    				for (final SequenceIterator i = result.iterate(); i.hasNext();) {
    					final NodeProxy item = (NodeProxy) i.nextItem();
//    					item.addContextNode(getExpressionId(), item);
    					if (contextId != Expression.NO_CONTEXT_ID)
                            {item.addContextNode(contextId, item);}
    					else
    						{item.addContextNode(getExpressionId(), item);}
    				}
    			}

    			// evaluate argument expression
    			final Sequence argSeq = arg.eval(result);
    			NodeSet argSet;
    			if (contextId != Expression.NO_CONTEXT_ID) {
	    			argSet = argSeq.toNodeSet().getContextNodes(contextId);
    			} else {
		    		argSet = argSeq.toNodeSet().getContextNodes(getExpressionId());
    			}
    			result = ((NodeSet)result).except(argSet);
            }
			
		// case 2: simply invert the boolean value
		} else {
			return evalBoolean(contextSequence, contextItem, arg);
		}
        
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        
        return result;           
	}

	/**
	 * @param contextSequence
	 * @param contextItem
	 * @param arg
	 * @throws XPathException
	 */
	private Sequence evalBoolean(Sequence contextSequence, Item contextItem, Expression arg) throws XPathException {
        final Sequence seq = arg.eval(contextSequence, contextItem);
		return seq.effectiveBooleanValue() ? BooleanValue.FALSE : BooleanValue.TRUE;
	}
}
