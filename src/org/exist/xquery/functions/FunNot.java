/* eXist Native XML Database
 * Copyright (C) 2001-06,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */

package org.exist.xquery.functions;

import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
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
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class FunNot extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("not", Function.BUILTIN_FUNCTION_NS),
			new SequenceType[] {
				 new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)},
			new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE));

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
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
           
		if(contextItem != null)
			contextSequence = contextItem.toSequence();
		
        Sequence result;
        Expression arg = getArgument(0);		
		// case 1: if the argument expression returns a node set,
		// subtract the set from the context node set and return
		// the remaining set
		if (Type.subTypeOf(arg.returnsType(), Type.NODE) &&
			(arg.getDependencies() & Dependency.CONTEXT_ITEM) == 0) {
			if (contextSequence == null || contextSequence.isEmpty()) {
				// TODO: special treatment if the context sequence is empty:
				// within a predicate, we just return the empty sequence
				// otherwise evaluate the argument and return a boolean result			    
				if (inPredicate && !inWhereClause)
                    result = Sequence.EMPTY_SEQUENCE;
				else
                    result = evalBoolean(contextSequence, contextItem, arg);
			} else {            
    			result = new ExtArrayNodeSet();
    			if(!contextSequence.isEmpty())
    				result.addAll(contextSequence);
    			
    			NodeProxy current;
    			if (inPredicate) {
    				for (SequenceIterator i = result.iterate(); i.hasNext();) {
    					current = (NodeProxy) i.nextItem();
    					if (contextId != Expression.NO_CONTEXT_ID)
                            current.addContextNode(contextId, current);
    					current.addContextNode(getExpressionId(), current);
//    					LOG.debug("Context: " + current.debugContext());
    				}
    			}

    			// evaluate argument expression
    			Sequence argSeq = arg.eval(result);
    			NodeSet argSet = argSeq.toNodeSet().getContextNodes(getExpressionId());
    			result = ((NodeSet)result).except(argSet);
            }
			
		// case 2: simply invert the boolean value
		} else {
			return evalBoolean(contextSequence, contextItem, arg);
		}
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;           
	}

	/**
	 * @param contextSequence
	 * @param contextItem
	 * @param arg
	 * @return
	 * @throws XPathException
	 */
	private Sequence evalBoolean(Sequence contextSequence, Item contextItem, Expression arg) throws XPathException {
        Sequence seq = arg.eval(contextSequence, contextItem);
		return seq.effectiveBooleanValue() ? BooleanValue.FALSE : BooleanValue.TRUE;
	}
}
