/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 the eXist team
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
 * $Id$
 */
package org.exist.xquery.functions.fn;

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
 * Implements the fn:insert-before function.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class FunInsertBefore extends Function {

	protected static final String FUNCTION_DESCRIPTION =

		"Returns a new sequence constructed from the value " +
		"of $target with the value of $inserts inserted at " + 
		"the position specified by the value of $position. " +
		"(The value of $target is not affected by the sequence construction.)\n\n" +
		
		"If $target is the empty sequence, $inserts is returned. If $inserts is the empty sequence, $target is returned.\n\n" +
		
		"The value returned by the function consists of all items " +
		"of $target whose index is less than $position, followed " +
		"by all items of $inserts, followed by the remaining elements " +
		"of $target, in that sequence.\n\n" +
		
		"If $position is less than one (1), the first position, the effective " +
		"value of $position is one (1). If $position is greater than the number " +
		"of items in $target, then the effective value of $position is " +
		"equal to the number of items in $target plus 1.";

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("insert-before", Function.BUILTIN_FUNCTION_NS),
			FUNCTION_DESCRIPTION,
			new SequenceType[] {
					new FunctionParameterSequenceType("target", Type.ITEM, Cardinality.ZERO_OR_MORE, "The target"),
					new FunctionParameterSequenceType("position", Type.INTEGER, Cardinality.ONE, "The position to insert before"),
					new FunctionParameterSequenceType("inserts", Type.ITEM, Cardinality.ZERO_OR_MORE, "The data to insert")
			},
			new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the new sequence"));

	public FunInsertBefore(XQueryContext context) {
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
		Sequence seq1 = getArgument(0).eval(contextSequence, contextItem);
		Sequence seq2 = getArgument(2).eval(contextSequence, contextItem);
		if (seq1.isEmpty())
		    {result = seq2;}
        else if (seq2.isEmpty()) 
            {result = seq1;}
        else {
    		int pos = 
    			((DoubleValue)getArgument(1).eval(contextSequence, contextItem).convertTo(Type.DOUBLE)).getInt();
    		pos--;
    		result = new ValueSequence();
    		if (pos <= 0) {
    			result.addAll(seq2);
    			result.addAll(seq1);
    		} else if (pos >= seq1.getItemCount()) {
    			result.addAll(seq1);
    			result.addAll(seq2);
    		} else {
    			for (int i=0; i<seq1.getItemCount(); i++) {
    				if (i == pos) {result.addAll(seq2);}
    				result.add(seq1.itemAt(i));
    			}
    		}
        }

        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        
        return result;  
        
	}

}
