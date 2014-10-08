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

import org.exist.dom.persistent.ExtArrayNodeSet;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
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
                    "Returns a subsequence of the items in $source-sequence, "
                    + "items starting at the position, $starting-at, " 
                    + "up to the end of the sequence are included.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("source", Type.ITEM, Cardinality.ZERO_OR_MORE, "The source sequence"),
                            new FunctionParameterSequenceType("starting-at", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The starting position in the $source")
                            },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the subsequence")),
            new FunctionSignature(
                    new QName("subsequence", Function.BUILTIN_FUNCTION_NS),
                    "Returns a subsequence of the items in $source, "
                            + "starting at the position, $starting-at,  "
                            + "including the number of items indicated by $length.",
                    new SequenceType[] {
                        new FunctionParameterSequenceType("source", Type.ITEM, Cardinality.ZERO_OR_MORE, "The source sequence"),
                        new FunctionParameterSequenceType("starting-at", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The starting position in the $source"),
                        new FunctionParameterSequenceType("length", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The length of the subsequence")
                        },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the subsequence")) };

    /**
     * @param context
     */
    public FunSubSequence(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        // statically check the argument list
        checkArguments();
        // call analyze for each argument
    	inPredicate = (contextInfo.getFlags() & IN_PREDICATE) > 0;
        unordered = (contextInfo.getFlags() & UNORDERED) > 0;
    	contextId = contextInfo.getContextId();
    	contextInfo.setParent(this);

        for(int i = 0; i < getArgumentCount(); i++) {
            final AnalyzeContextInfo argContextInfo = new AnalyzeContextInfo(contextInfo);
            getArgument(i).analyze(argContextInfo);
            if (i == 0)
                {contextInfo.setStaticReturnType(argContextInfo.getStaticReturnType());}
        }
    }

    /*
    * (non-Javadoc)
    *
    * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet,
    *      org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
    */
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                    "DEPENDENCIES",
                    Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                        "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                        "CONTEXT ITEM", contextItem.toSequence());}
        }

        Sequence result;
        final Sequence seq = getArgument(0).eval(contextSequence, contextItem);
        if (seq.isEmpty())
            {result = Sequence.EMPTY_SEQUENCE;}
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
            	{--start;}
                        
            Sequence tmp;
            if (seq instanceof NodeSet) {
                tmp = new ExtArrayNodeSet();
                ((ExtArrayNodeSet)tmp).keepUnOrdered(unordered);
            } else {
                tmp = new ValueSequence();
                ((ValueSequence)tmp).keepUnOrdered(unordered);
            }
            
            Item item;
            final SequenceIterator iterator = seq.iterate();
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
            {context.getProfiler().end(this, "", result);}

        return result;

    }

}
