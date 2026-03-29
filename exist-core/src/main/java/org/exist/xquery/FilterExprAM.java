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

import io.lacuna.bifurcan.IEntry;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

/**
 * XQuery 4.0 FilterExprAM — the array/map filter expression {@code ?[expr]}.
 *
 * <p>Filters array members or map entries by evaluating a predicate expression
 * with each member/value as the context item. Only items where the predicate's
 * effective boolean value is true are kept in the result.</p>
 *
 * <pre>
 * [1, 2, 3, 4, 5]?[. > 3]         → [4, 5]
 * map{"a":1, "b":2, "c":3}?[. > 1] → map{"b":2, "c":3}
 * </pre>
 *
 * @see <a href="https://qt4cg.org/specifications/xquery-40/xpath-40-xquery-40.html#id-filter-am">
 *      QT4 spec: FilterExprAM</a>
 */
public class FilterExprAM extends AbstractExpression {

    private final Expression target;
    private final Expression predicate;

    public FilterExprAM(final XQueryContext context, final Expression target, final Expression predicate) {
        super(context);
        this.target = target;
        this.predicate = predicate;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        target.analyze(contextInfo);

        // The predicate runs with each member/value as context item
        final AnalyzeContextInfo predInfo = new AnalyzeContextInfo(contextInfo);
        predInfo.setStaticType(Type.ITEM);
        predicate.analyze(predInfo);
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        // Evaluate the target expression
        final Sequence targetSeq = target.eval(contextSequence, contextItem);

        if (targetSeq.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        // Process each item in the target sequence
        final ValueSequence result = new ValueSequence();
        for (final SequenceIterator iter = targetSeq.iterate(); iter.hasNext(); ) {
            final Item item = iter.nextItem();

            if (item.getType() == Type.ARRAY_ITEM) {
                result.add(filterArray((ArrayType) item));
            } else if (Type.subTypeOf(item.getType(), Type.MAP_ITEM)) {
                result.add(filterMap((AbstractMapType) item));
            } else {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "FilterExprAM (?[]) requires an array or map, got " +
                                Type.getTypeName(item.getType()));
            }
        }

        return result;
    }

    private ArrayType filterArray(final ArrayType array) throws XPathException {
        final ArrayType filtered = new ArrayType(context, Sequence.EMPTY_SEQUENCE);
        for (int i = 0; i < array.getSize(); i++) {
            final Sequence member = array.get(i);

            // Evaluate predicate with member as context item
            final Sequence predResult = predicate.eval(member, null);
            if (predResult.effectiveBooleanValue()) {
                filtered.add(member);
            }
        }
        return filtered;
    }

    private AbstractMapType filterMap(final AbstractMapType map) throws XPathException {
        final MapType filtered = new MapType(this, context);
        for (final IEntry<AtomicValue, Sequence> entry : map) {
            final Sequence value = entry.value();

            // Evaluate predicate with value as context item
            final Sequence predResult = predicate.eval(value, null);
            if (predResult.effectiveBooleanValue()) {
                filtered.add(entry.key(), value);
            }
        }
        return filtered;
    }

    @Override
    public int returnsType() {
        return target.returnsType();
    }

    @Override
    public Cardinality getCardinality() {
        return target.getCardinality();
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        target.dump(dumper);
        dumper.display("?[");
        predicate.dump(dumper);
        dumper.display("]");
    }

    @Override
    public String toString() {
        return target.toString() + "?[" + predicate.toString() + "]";
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        target.resetState(postOptimization);
        predicate.resetState(postOptimization);
    }
}
