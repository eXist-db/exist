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
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Implements the fn:subsequence function.
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FunSubSequence extends Function {

    public final static FunctionSignature signatures[] = {
            new FunctionSignature(
                    new QName("subsequence", Function.BUILTIN_FUNCTION_NS),
                    "Returns a subsequence of the values in the first argument sequence, "
                            + "starting at the position indicated by the value of the second argument and "
                            + "including the number of items indicated by the value of the optional third"
                            + "argument. If the third argument is missing, all items up to the end of the "
                            + "sequence are included.", new SequenceType[] {
                            new SequenceType(Type.ITEM,
                                    Cardinality.ZERO_OR_MORE),
                            new SequenceType(Type.DOUBLE,
                                    Cardinality.EXACTLY_ONE) },
                    new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)),
            new FunctionSignature(
                    new QName("subsequence", Function.BUILTIN_FUNCTION_NS),
                    "Returns a subsequence of the values in the first argument sequence, "
                            + "starting at the position indicated by the value of the second argument and "
                            + "including the number of items indicated by the value of the optional third"
                            + "argument. If the third argument is missing, all items up to the end of the "
                            + "sequence are included.", new SequenceType[] {
                            new SequenceType(Type.ITEM,
                                    Cardinality.ZERO_OR_MORE),
                            new SequenceType(Type.DOUBLE,
                                    Cardinality.EXACTLY_ONE),
                            new SequenceType(Type.DOUBLE,
                                    Cardinality.EXACTLY_ONE) },
                    new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)) };

    /**
     * @param context
     */
    public FunSubSequence(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet,
     *      org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                    "DEPENDENCIES",
                    Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES,
                        "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES,
                        "CONTEXT ITEM", contextItem.toSequence());
        }

        Sequence result;
        Sequence seq = getArgument(0).eval(contextSequence, contextItem);
        if (seq.isEmpty())
            result = Sequence.EMPTY_SEQUENCE;
        else {
            int start = ((DoubleValue) getArgument(1).eval(contextSequence,
                    contextItem).convertTo(Type.DOUBLE)).getInt();

            int length = Integer.MAX_VALUE;
            if (getSignature().getArgumentCount() == 3) {
                length = ((DoubleValue) getArgument(2).eval(
                        contextSequence, contextItem)
                        .convertTo(Type.DOUBLE)).getInt();
            }

            // TODO : exception? -pb
            if (start < 0) {
                length = length + start - 1;
                start = 0;
            } else if (start == 0) {
            	--length;
                --start;
            } else
            	--start;
                        
            Sequence tmp;
            if (seq instanceof NodeSet)
                tmp = new ExtArrayNodeSet();
            else
                tmp = new ValueSequence();
            
            Item item;
            SequenceIterator iterator = seq.iterate();
            for(int i = 0; i < start; i++) {
                item = iterator.nextItem();
            } 
            int i=0;
            while (iterator.hasNext() && i < length) {
                item = iterator.nextItem();
                tmp.add(item);
                i++;
            }
            
            result = i>0?tmp:Sequence.EMPTY_SEQUENCE;
        }

        if (context.getProfiler().isEnabled())
            context.getProfiler().end(this, "", result);

        return result;

    }

}
