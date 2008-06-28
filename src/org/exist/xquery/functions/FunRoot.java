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
package org.exist.xquery.functions;

import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.memtree.NodeImpl;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FunRoot extends Function {
    
    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
                new QName("root", Function.BUILTIN_FUNCTION_NS),
                "Returns the root of the tree to which the context node belongs. This will usually, "
                + "but not necessarily, be a document node.",
                new SequenceType[0],
                new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE)
                ),
        new FunctionSignature(
                new QName("root", Function.BUILTIN_FUNCTION_NS),
                "Returns the root of the tree to which $arg belongs. This will usually, "
                + "but not necessarily, be a document node.",
                new SequenceType[] { new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)},
                new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
                )
    };
    
    /**
     * @param context
     * @param signature
     */
    public FunRoot(XQueryContext context, FunctionSignature signature) {
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
        
        
        Sequence seq;
        Sequence result;
        Item item;
 
        if (contextItem != null)
        	contextSequence = contextItem.toSequence();
		if (contextSequence == null || contextSequence.isEmpty()) 
			result = Sequence.EMPTY_SEQUENCE;            
        
		//If we have one argumment, we take it into account
		if (getSignature().getArgumentCount() > 0) 
			seq = getArgument(0).eval(contextSequence, contextItem);
		//Otherwise, we take the context sequence and we iterate over it
		else
			seq = contextSequence; 
		
		if (seq == null)
			throw new XPathException(getASTNode(), "XPDY0002: Undefined context item");

        if (seq.isPersistentSet())
            result = new ExtArrayNodeSet(seq.getItemCount());
        else
            result = new ValueSequence(seq.getItemCount());
        int j = 0;
		for (SequenceIterator i = seq.iterate(); i.hasNext(); j++) {
			item = i.nextItem();
            if (!Type.subTypeOf(item.getType(), Type.NODE))
                throw new XPathException(getASTNode(), "FOTY0011: item is not a node; got '" + item + "'");
            Sequence s = item.toSequence();
            if (s.isPersistentSet()) {
                NodeProxy p = s.toNodeSet().get(0);
                result.add(new NodeProxy(p.getDocument()));
            } else {
                result.add(((NodeImpl)item).getDocument());
            }
		}
    
	    if (context.getProfiler().isEnabled())
	            context.getProfiler().end(this, "", result);    
        
        return result;
        
    }	
  
}
