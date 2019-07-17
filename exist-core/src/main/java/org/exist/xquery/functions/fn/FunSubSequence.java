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
import org.exist.xquery.value.*;

import javax.annotation.Nullable;

/**
 * Implements the fn:subsequence function.
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class FunSubSequence extends Function {

    public static final FunctionSignature signatures[] = {
            new FunctionSignature(
                    new QName("subsequence", Function.BUILTIN_FUNCTION_NS),
                    "Returns a subsequence of the items in $source-sequence, "
                            + "items starting at the position, $starting-at, "
                            + "up to the end of the sequence are included.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("source", Type.ITEM, Cardinality.ZERO_OR_MORE, "The source sequence"),
                            new FunctionParameterSequenceType("starting-at", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The starting position in the $source")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the subsequence")),
            new FunctionSignature(
                    new QName("subsequence", Function.BUILTIN_FUNCTION_NS),
                    "Returns a subsequence of the items in $source, "
                            + "starting at the position, $starting-at,  "
                            + "including the number of items indicated by $length.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("source", Type.ITEM, Cardinality.ZERO_OR_MORE, "The source sequence"),
                            new FunctionParameterSequenceType("starting-at", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The starting position in the $source"),
                            new FunctionParameterSequenceType("length", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The length of the subsequence")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the subsequence"))};

    public FunSubSequence(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        // statically check the argument list
        checkArguments();
        // call analyze for each argument
        inPredicate = (contextInfo.getFlags() & IN_PREDICATE) > 0;
        unordered = (contextInfo.getFlags() & UNORDERED) > 0;
        contextId = contextInfo.getContextId();
        contextInfo.setParent(this);

        for (int i = 0; i < getArgumentCount(); i++) {
            final AnalyzeContextInfo argContextInfo = new AnalyzeContextInfo(contextInfo);
            getArgument(i).analyze(argContextInfo);
            if (i == 0) {
                contextInfo.setStaticReturnType(argContextInfo.getStaticReturnType());
            }
        }
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                    "DEPENDENCIES",
                    Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES,
                        "CONTEXT SEQUENCE", contextSequence);
            }
            if (contextItem != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES,
                        "CONTEXT ITEM", contextItem.toSequence());
            }
        }

        final Sequence result;
        final Sequence seq = getArgument(0).eval(contextSequence, contextItem);
        if (seq.isEmpty()) {
            result = Sequence.EMPTY_SEQUENCE;
        } else {
            return subsequence(seq,
                    ((DoubleValue)getArgument(1).eval(contextSequence, contextItem).convertTo(Type.DOUBLE)),
                    getArgumentCount() != 3 ? null : ((DoubleValue)getArgument(2).eval(contextSequence, contextItem).convertTo(Type.DOUBLE))
            );
        }

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }

        return result;
    }

    /**
     * Creates a Subsequence from a sequence
     *
     * @param sequence the input sequence
     * @param startLoc the starting location value as passed to {@code fn:subsequence}
     * @param length the length value as passed to {@code fn:subsequence}, or null for all items
     *
     * @return the subsequence
     */
    public static Sequence subsequence(final Sequence sequence, final DoubleValue startLoc, @Nullable final DoubleValue length) {
        final long startArg = startLoc.getLong();
        final long toExclusive;
        if (length != null) {
                /*
                    From: https://www.w3.org/TR/xpath-functions-31/#func-subsequence

                    $sourceSeq[fn:round($startingLoc) le position()
                            and position() lt fn:round($startingLoc) + fn:round($length)]
                 */
            final long lengthArg = length.getLong();
            toExclusive = startArg + lengthArg;
        } else {
                /*
                    From: https://www.w3.org/TR/xpath-functions-31/#func-subsequence

                    $sourceSeq[fn:round($startingLoc) le position()]
                 */
            toExclusive = Long.MAX_VALUE;   // we can't travel past Long.MAX_VALUE (...at the moment!)
        }

        //TODO(AR) are there shortcuts where we can determine that the result is an empty-sequence from the args

        // we can't start before the first item
        final long fromInclusive;
        if (startArg <= 0) {
            fromInclusive = 1;
        } else {
            fromInclusive = startArg;
        }

        return new SubSequence(fromInclusive, toExclusive, sequence);
    }
}
