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
package org.exist.xquery.functions.map;

import io.lacuna.bifurcan.IEntry;
import io.lacuna.bifurcan.IMap;
import org.exist.xquery.*;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import java.util.ArrayList;
import java.util.List;

import static org.exist.xquery.functions.map.MapType.newLinearMap;

/**
 * Implements the literal syntax for creating maps.
 *
 * <p>In XQuery 4.0 (PR2094), map constructor entries can be either key:value pairs
 * or merge entries. A merge entry is a single expression that must evaluate to a map,
 * whose entries are merged into the result.</p>
 */
public class MapExpr extends AbstractExpression {

    private final List<Entry> entries = new ArrayList<>(13);

    public MapExpr(final XQueryContext context) {
        super(context);
    }

    public void map(final PathExpr key, final PathExpr value) {
        entries.add(new KeyValueEntry(key.simplify(), value.simplify()));
    }

    public void merge(final PathExpr expr) {
        entries.add(new MergeEntry(expr.simplify()));
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        if (getContext().getXQueryVersion() < 30) {
            throw new XPathException(this, ErrorCodes.EXXQDY0003,
                    "Map is not available before XQuery 3.0");
        }
        contextInfo.setParent(this);
        for (final Entry entry : entries) {
            entry.analyze(contextInfo);
        }
    }

    @Override
    public Sequence eval(Sequence contextSequence, final Item contextItem) throws XPathException {
        if (contextItem != null) {
            contextSequence = contextItem.toSequence();
        }
        final IMap<AtomicValue, Sequence> map = newLinearMap(null);

        boolean firstType = true;
        int prevType = AbstractMapType.UNKNOWN_KEY_TYPE;

        for (final Entry entry : entries) {
            if (entry instanceof KeyValueEntry kv) {
                final Sequence key = kv.key.eval(contextSequence, null);
                if (key.getItemCount() != 1) {
                    throw new XPathException(this, MapErrorCode.EXMPDY001, "Expected single value for key, got " + key.getItemCount());
                }
                final AtomicValue atomic = key.itemAt(0).atomize();
                final Sequence value = kv.value.eval(contextSequence, null);
                if (map.contains(atomic)) {
                    throw new XPathException(this, ErrorCodes.XQDY0137, "Key \"" + atomic.getStringValue() + "\" already exists in map.");
                }
                map.put(atomic, value);

                final int thisType = atomic.getType();
                if (firstType) {
                    prevType = thisType;
                    firstType = false;
                } else if (thisType != prevType) {
                    prevType = AbstractMapType.MIXED_KEY_TYPES;
                }
            } else if (entry instanceof MergeEntry me) {
                final Sequence result = me.expr.eval(contextSequence, null);
                // Each item in the result must be a map
                for (int i = 0; i < result.getItemCount(); i++) {
                    final Item item = result.itemAt(i);
                    if (item.getType() != Type.MAP_ITEM && !Type.subTypeOf(item.getType(), Type.MAP_ITEM)) {
                        throw new XPathException(this, ErrorCodes.XPTY0004,
                                "Merge entry in map constructor must be a map, got " + Type.getTypeName(item.getType()));
                    }
                    final AbstractMapType mergeMap = (AbstractMapType) item;
                    for (final IEntry<AtomicValue, Sequence> mergeEntry : mergeMap) {
                        final AtomicValue mergeKey = mergeEntry.key();
                        if (map.contains(mergeKey)) {
                            throw new XPathException(this, ErrorCodes.XQDY0137,
                                    "Key \"" + mergeKey.getStringValue() + "\" already exists in map.");
                        }
                        map.put(mergeKey, mergeEntry.value());

                        final int thisType = mergeKey.getType();
                        if (firstType) {
                            prevType = thisType;
                            firstType = false;
                        } else if (thisType != prevType) {
                            prevType = AbstractMapType.MIXED_KEY_TYPES;
                        }
                    }
                }
            }
        }

        return new MapType(this, context, map.forked(), prevType);
    }

    @Override
    public int returnsType() {
        return Type.MAP_ITEM;
    }

    @Override
    public void accept(final ExpressionVisitor visitor) {
        super.accept(visitor);
        for (final Entry entry : entries) {
            entry.accept(visitor);
        }
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("map {");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                dumper.display(", ");
            }
            entries.get(i).dump(dumper);
        }
        dumper.display("}");
    }

    @Override
    public String toString() {
        return ExpressionDumper.dump(this);
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        entries.forEach(e -> e.resetState(postOptimization));
    }

    private sealed interface Entry permits KeyValueEntry, MergeEntry {
        void analyze(AnalyzeContextInfo contextInfo) throws XPathException;
        void accept(ExpressionVisitor visitor);
        void dump(ExpressionDumper dumper);
        void resetState(boolean postOptimization);
    }

    private record KeyValueEntry(Expression key, Expression value) implements Entry {
        @Override
        public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
            key.analyze(contextInfo);
            value.analyze(contextInfo);
        }

        @Override
        public void accept(final ExpressionVisitor visitor) {
            key.accept(visitor);
            value.accept(visitor);
        }

        @Override
        public void dump(final ExpressionDumper dumper) {
            key.dump(dumper);
            dumper.display(" : ");
            value.dump(dumper);
        }

        @Override
        public void resetState(final boolean postOptimization) {
            key.resetState(postOptimization);
            value.resetState(postOptimization);
        }
    }

    private record MergeEntry(Expression expr) implements Entry {
        @Override
        public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
            expr.analyze(contextInfo);
        }

        @Override
        public void accept(final ExpressionVisitor visitor) {
            expr.accept(visitor);
        }

        @Override
        public void dump(final ExpressionDumper dumper) {
            expr.dump(dumper);
        }

        @Override
        public void resetState(final boolean postOptimization) {
            expr.resetState(postOptimization);
        }
    }
}
