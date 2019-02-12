/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2004-2009 The eXist Project
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

import org.exist.dom.persistent.ExtArrayNodeSet;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Implements the fn:remove function.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class FunRemove extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("remove", Function.BUILTIN_FUNCTION_NS),
			"Returns a new sequence constructed from the value of $target with the item " +
			"at $position removed.\n\nIf $position " +
			"is less than 1 or greater than the number of items in $target, $target is returned. " +
			"Otherwise, the value returned by the function consists of all items of $target " +
			"whose index is less than $position, followed by all items of $target whose index " +
			"is greater than $position. If $target is the empty sequence, the empty sequence " +
			"is returned.",
			new SequenceType[] {
					new FunctionParameterSequenceType("target", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
					new FunctionParameterSequenceType("position", Type.INTEGER, Cardinality.ONE, "The position of the value to be removed")
			},
			new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the new sequence with the item at the position specified by the value of $position removed."));



	public FunRemove(XQueryContext context) {
		super(context, signature);
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
        
        Sequence result;
        Sequence seq = getArgument(0).eval(contextSequence, contextItem);
		if (seq.isEmpty()) 
            {result = Sequence.EMPTY_SEQUENCE;}
        else {            
            //TODO : explain this Double conversion -pb
    		int pos = ((DoubleValue)getArgument(1).eval(contextSequence, contextItem).convertTo(Type.DOUBLE)).getInt();
    		if (pos < 1 || pos > seq.getItemCount()) 
                {result= seq;}
            else {
        		pos--;
        		if (seq instanceof NodeSet) {
        			result = new ExtArrayNodeSet();
        			result.addAll((NodeSet) seq);
        			result = ((NodeSet)result).except((NodeSet) seq.itemAt(pos));
        		} else {
        			result = new ValueSequence();
        			for (int i = 0; i < seq.getItemCount(); i++) {
        				if (i != pos) {result.add(seq.itemAt(i));}
        			}        			
        		}
            }
        }
        
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        
        return result;         
	}

    @Override
    public int getDependencies() {
        return Dependency.NO_DEPENDENCY;
    }
}