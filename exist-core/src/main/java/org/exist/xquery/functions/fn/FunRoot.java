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

import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.persistent.ExtArrayNodeSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.QName;
import org.exist.dom.memtree.NodeImpl;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class FunRoot extends Function {
	
	protected static final String FUNCTION_DESCRIPTION_0_PARAM = 
		"Returns the root of the tree to which the context item belongs. ";
	protected static final String FUNCTION_DESCRIPTION_1_PARAM = 
		"Returns the root of the tree to which $arg belongs. " +
		"This will usually, but not necessarily, be a document node.\n\n" +
		"If $arg is the empty sequence, the empty sequence is returned.\n\n" +
		"If $arg is a document node, $arg is returned.\n\n" +
		" The behavior of the zero argument version of the function is " +
        "exactly the same as if the context item had been passed in $arg.";
    
    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
                new QName("root", Function.BUILTIN_FUNCTION_NS),
                FUNCTION_DESCRIPTION_0_PARAM,
                new SequenceType[0],
                new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE, "the root node of the tree to which the context node belongs")
                ),
        new FunctionSignature(
                new QName("root", Function.BUILTIN_FUNCTION_NS),
                FUNCTION_DESCRIPTION_1_PARAM,
                new SequenceType[] { new FunctionParameterSequenceType("arg", Type.NODE, Cardinality.ZERO_OR_ONE, "The input node") },
                new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_ONE, "the root node of the tree to which $arg belongs")
                )
    };

    public FunRoot(XQueryContext context, FunctionSignature signature) {
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
        
        
        Sequence seq;
        Sequence result;
        Item item;
 
        if (contextItem != null)
        	{contextSequence = contextItem.toSequence();}
		if (contextSequence == null || contextSequence.isEmpty()) 
			{result = Sequence.EMPTY_SEQUENCE;}            
        
		//If we have one argumment, we take it into account
		if (getSignature().getArgumentCount() > 0) 
			{seq = getArgument(0).eval(contextSequence, contextItem);}
		//Otherwise, we take the context sequence and we iterate over it
		else
			{seq = contextSequence;} 
		
		if (seq == null)
			{throw new XPathException(this, ErrorCodes.XPDY0002, "Undefined context item");}

        if (seq.isPersistentSet())
            {result = new ExtArrayNodeSet(seq.getItemCount());}
        else
            {result = new ValueSequence(seq.getItemCount());}

		for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
			item = i.nextItem();
            if (!Type.subTypeOf(item.getType(), Type.NODE))
                {throw new XPathException(this, ErrorCodes.XPTY0004,  "Item is not a node; got '" + item + "'", seq);}
            final Sequence s = item.toSequence();

            if (s.isPersistentSet()) {
                final NodeProxy p = s.toNodeSet().get(0);
                result.add(new NodeProxy(p.getOwnerDocument()));
            } else {
                if (seq.hasOne() && item.getType() == Type.ATTRIBUTE) {
                	result.add(item);
                } else if(item.getType() == Type.DOCUMENT) {
                    result.add((DocumentImpl)item);
                } else {
                	result.add(((NodeImpl)item).getOwnerDocument());
                }
            }
		}
    
	    if (context.getProfiler().isEnabled())
	            {context.getProfiler().end(this, "", result);}    
        
        return result;
        
    }	
  
}
