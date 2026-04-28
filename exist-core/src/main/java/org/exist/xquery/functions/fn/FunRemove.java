/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
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
 * Implements the fn:remove function.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class FunRemove extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("remove", Function.BUILTIN_FUNCTION_NS),
			"Returns a new sequence constructed from the value of $input with the items " +
			"at the supplied $positions removed. " +
			"In XQuery 4.0, $positions may be a sequence of integers; positions outside " +
			"the bounds of $input or duplicate positions are ignored. If $positions is " +
			"the empty sequence, $input is returned unchanged.",
			new SequenceType[] {
					new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
					new FunctionParameterSequenceType("positions", Type.INTEGER, Cardinality.ZERO_OR_MORE, "Positions of the items to remove")
			},
			new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the new sequence with the items at the specified positions removed."));



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
            // XQuery 4.0: $positions may be a sequence of zero or more xs:integer
            final Sequence positions = getArgument(1).eval(contextSequence, contextItem);
            if (positions.isEmpty()) {
                result = seq;
            } else {
                // Build a set of 0-based positions to remove (ignore out-of-range and duplicates).
                final java.util.Set<Integer> remove = new java.util.HashSet<>(positions.getItemCount());
                final org.exist.xquery.value.SequenceIterator it = positions.iterate();
                while (it.hasNext()) {
                    final int pos = ((DoubleValue) it.nextItem().convertTo(Type.DOUBLE)).getInt();
                    if (pos >= 1 && pos <= seq.getItemCount()) {
                        remove.add(pos - 1);
                    }
                }
                if (remove.isEmpty()) {
                    result = seq;
                } else {
                    // Use the generic ValueSequence path even for node sets — multi-position
                    // removal does not benefit from NodeSet.except, which expects one node at a time.
                    final ValueSequence vs = new ValueSequence();
                    for (int i = 0; i < seq.getItemCount(); i++) {
                        if (!remove.contains(i)) { vs.add(seq.itemAt(i)); }
                    }
                    result = vs;
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