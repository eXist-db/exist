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
package org.exist.xquery;

import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implements the XQuery 4.0 array/map filter expression ({@code ?[predicate]}).
 *
 * <p>For arrays, iterates over members and keeps those where the predicate
 * evaluates to true with the context item set to each member.
 * Numeric predicates select by position (1-based).</p>
 *
 * <p>For maps, iterates over entries and keeps those where the predicate
 * evaluates to true with the context item set to
 * {@code map { "key": key, "value": value }} for each entry.
 * Numeric predicates select by position in insertion order.</p>
 */
public class FilterExprAM extends AbstractExpression {

    private Expression contextExpr;
    private Expression predicate;

    public FilterExprAM(final XQueryContext context, final Expression contextExpr, final Expression predicate) {
        super(context);
        this.contextExpr = contextExpr;
        this.predicate = predicate;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        contextExpr.analyze(contextInfo);
        final AnalyzeContextInfo predicateInfo = new AnalyzeContextInfo(contextInfo);
        predicate.analyze(predicateInfo);
    }

    @Override
    public Sequence eval(Sequence contextSequence, final Item contextItem) throws XPathException {
        if (contextItem != null) {
            contextSequence = contextItem.toSequence();
        }
        final Sequence input = contextExpr.eval(contextSequence, null);

        if (input.isEmpty()) {
            return input;
        }

        final Item item = input.itemAt(0);
        if (Type.subTypeOf(item.getType(), Type.ARRAY_ITEM)) {
            return filterArray((ArrayType) item);
        } else if (Type.subTypeOf(item.getType(), Type.MAP_ITEM)) {
            return filterMap((AbstractMapType) item);
        } else {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "?[] filter requires an array or map, got " + Type.getTypeName(item.getType()));
        }
    }

    private ArrayType filterArray(final ArrayType array) throws XPathException {
        final int size = array.getSize();

        // Build a context sequence of all member items for position()/last()
        final ValueSequence contextSeq = new ValueSequence(size);
        final List<Sequence> members = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            final Sequence member = array.get(i);
            members.add(member);
            // For context sequence, we need each member as an item.
            // If a member is a sequence, wrap it — but for position/last to work
            // we need exactly `size` items in the context sequence.
            if (member.isEmpty()) {
                // Empty sequence member: use empty sequence as placeholder
                contextSeq.add(AtomicValue.EMPTY_VALUE);
            } else if (member.getItemCount() == 1) {
                contextSeq.add(member.itemAt(0));
            } else {
                // Multi-item member: use first item as representative for context
                contextSeq.add(member.itemAt(0));
            }
        }

        final int savedPos = context.getContextPosition();
        final Sequence savedSeq = context.getContextSequence();
        try {
            final ArrayType result = new ArrayType(context, new ArrayList<>());
            for (int i = 0; i < size; i++) {
                final Sequence member = members.get(i);
                context.setContextSequencePosition(i, contextSeq);

                final Sequence predResult = predicate.eval(member, null);
                if (isSelected(predResult, i + 1)) {
                    result.add(member);
                }
            }
            return result;
        } finally {
            context.setContextSequencePosition(savedPos, savedSeq);
        }
    }

    private AbstractMapType filterMap(final AbstractMapType map) throws XPathException {
        final Sequence keys = map.keys();
        final int size = keys.getItemCount();

        // Build entry maps and context sequence for position/last
        final ValueSequence contextSeq = new ValueSequence(size);
        final List<AtomicValue> keyList = new ArrayList<>(size);
        final List<AbstractMapType> entryMaps = new ArrayList<>(size);

        for (final SequenceIterator i = keys.iterate(); i.hasNext(); ) {
            final AtomicValue key = (AtomicValue) i.nextItem();
            keyList.add(key);
            final Sequence value = map.get(key);

            final MapType entryMap = new MapType(context, null);
            entryMap.add(new StringValue(this, "key"), key.toSequence());
            entryMap.add(new StringValue(this, "value"), value);
            entryMaps.add(entryMap);
            contextSeq.add(entryMap);
        }

        final int savedPos = context.getContextPosition();
        final Sequence savedSeq = context.getContextSequence();
        try {
            final MapType result = new MapType(context, null);
            for (int i = 0; i < size; i++) {
                context.setContextSequencePosition(i, contextSeq);
                final AbstractMapType entryMap = entryMaps.get(i);

                final Sequence predResult = predicate.eval(entryMap.toSequence(), null);
                if (isSelected(predResult, i + 1)) {
                    result.add(keyList.get(i), map.get(keyList.get(i)));
                }
            }
            return result;
        } finally {
            context.setContextSequencePosition(savedPos, savedSeq);
        }
    }

    /**
     * Determines whether a member/entry at the given 1-based position is selected
     * by the predicate result, following XQ4 array/map filter semantics:
     * - If the result is a single numeric value, select if it equals the position.
     * - If the result is a multi-item all-numeric sequence, select if any value
     *   equals the position (XQ4 extension for ?[] filters).
     * - If the result is a multi-item sequence mixing numeric and non-numeric,
     *   raise FORG0006.
     * - Otherwise, evaluate effective boolean value.
     */
    private boolean isSelected(final Sequence predResult, final int position) throws XPathException {
        if (predResult.isEmpty()) {
            return false;
        }

        // Single numeric value: positional predicate
        if (predResult.hasOne() && Type.subTypeOfUnion(predResult.itemAt(0).getType(), Type.NUMERIC)) {
            final double pos = ((NumericValue) predResult.itemAt(0)).getDouble();
            return pos == position;
        }

        // Multi-item sequence starting with numeric: check all items are numeric
        if (predResult.getItemCount() > 1 &&
                Type.subTypeOfUnion(predResult.itemAt(0).getType(), Type.NUMERIC)) {
            for (final SequenceIterator i = predResult.iterate(); i.hasNext(); ) {
                final Item item = i.nextItem();
                if (!Type.subTypeOfUnion(item.getType(), Type.NUMERIC)) {
                    throw new XPathException((Expression) null, ErrorCodes.FORG0006,
                            "Mixed numeric and non-numeric values in filter predicate");
                }
                final double pos = ((NumericValue) item).getDouble();
                if (pos == position) {
                    return true;
                }
            }
            return false;
        }

        // Boolean predicate
        return predResult.effectiveBooleanValue();
    }

    @Override
    public int returnsType() {
        return Type.ITEM;
    }

    @Override
    public Cardinality getCardinality() {
        return Cardinality.EXACTLY_ONE;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        contextExpr.dump(dumper);
        dumper.display("?[");
        predicate.dump(dumper);
        dumper.display("]");
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        contextExpr.resetState(postOptimization);
        predicate.resetState(postOptimization);
    }
}
