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
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;

import java.util.ArrayList;
import java.util.List;

import static org.exist.xquery.functions.map.MapType.newLinearMap;

/**
 * Implements the literal syntax for creating maps.
 *
 * <p>In XQuery 4.0, map constructor entries can be either key:value pairs
 * or content expressions. A content expression is a single expression that
 * must evaluate to a map, whose entries are merged into the result.</p>
 */
public class MapExpr extends AbstractExpression {

    private final List<Entry> entries = new ArrayList<>(13);

    public MapExpr(final XQueryContext context) {
        super(context);
    }

    public void map(final PathExpr key, final PathExpr value) {
        this.entries.add(new Mapping(key.simplify(), value.simplify()));
    }

    public void content(final PathExpr expr) {
        this.entries.add(new ContentEntry(expr.simplify()));
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        if (getContext().getXQueryVersion() < 30) {
            throw new XPathException(this, ErrorCodes.EXXQDY0003,
                    "Map is not available before XQuery 3.0");
        }
        contextInfo.setParent(this);
        for (final Entry entry : this.entries) {
            entry.analyze(contextInfo);
        }
    }

    @Override
    public Sequence eval(Sequence contextSequence, final Item contextItem) throws XPathException {
        if (contextItem != null) {
            contextSequence = contextItem.toSequence();
        }

        // Fast path for a single colon-pair literal — skip the linear/forked dance
        // and the duplicate-key check (a single mapping cannot collide with itself).
        if (this.entries.size() == 1 && this.entries.get(0) instanceof Mapping mapping) {
            final Sequence key = mapping.key.eval(contextSequence, null);
            if (key.getItemCount() != 1) {
                throw new XPathException(this, MapErrorCode.EXMPDY001, "Expected single value for key, got " + key.getItemCount());
            }
            final AtomicValue atomic = key.itemAt(0).atomize();
            final Sequence value = mapping.value.eval(contextSequence, null);
            return new MapType(this, context, null, atomic, value);
        }

        final IMap<AtomicValue, Sequence> map = newLinearMap(null);

        boolean firstType = true;
        int prevType = AbstractMapType.UNKNOWN_KEY_TYPE;

        for (final Entry entry : this.entries) {
            if (entry instanceof Mapping mapping) {
                final Sequence key = mapping.key.eval(contextSequence, null);
                if (key.getItemCount() != 1) {
                    throw new XPathException(this, MapErrorCode.EXMPDY001, "Expected single value for key, got " + key.getItemCount());
                }
                final AtomicValue atomic = key.itemAt(0).atomize();
                final Sequence value = mapping.value.eval(contextSequence, null);
                if (map.contains(atomic)) {
                    throw new XPathException(this, ErrorCodes.XQDY0137, "Key \"" + atomic.getStringValue() + "\" already exists in map.");
                }
                map.put(atomic, value);

                final int thisType = atomic.getType();
                if (firstType) {
                    prevType = thisType;
                    firstType = false;
                } else {
                    if (thisType != prevType) {
                        prevType = AbstractMapType.MIXED_KEY_TYPES;
                    }
                }
            } else if (entry instanceof ContentEntry contentEntry) {
                final Sequence result = contentEntry.expr.eval(contextSequence, null);
                // content expression must evaluate to zero or more maps
                for (int i = 0; i < result.getItemCount(); i++) {
                    final Item item = result.itemAt(i);
                    if (item.getType() != Type.MAP_ITEM && !(item instanceof AbstractMapType)) {
                        throw new XPathException(this, ErrorCodes.XPTY0004,
                                "Content expression in map constructor must be a map, got " + Type.getTypeName(item.getType()));
                    }
                    final AbstractMapType contentMap = (AbstractMapType) item;
                    for (final IEntry<AtomicValue, Sequence> mapEntry : contentMap) {
                        final AtomicValue atomic = mapEntry.key();
                        if (map.contains(atomic)) {
                            throw new XPathException(this, ErrorCodes.XQDY0137, "Key \"" + atomic.getStringValue() + "\" already exists in map.");
                        }
                        map.put(atomic, mapEntry.value());

                        final int thisType = atomic.getType();
                        if (firstType) {
                            prevType = thisType;
                            firstType = false;
                        } else {
                            if (thisType != prevType) {
                                prevType = AbstractMapType.MIXED_KEY_TYPES;
                            }
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
        for (final Entry entry : this.entries) {
            entry.accept(visitor);
        }
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("map {");
        for (int i = 0; i < this.entries.size(); i++) {
            if (i > 0) {
                dumper.display(", ");
            }
            this.entries.get(i).dump(dumper);
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

    private interface Entry {
        void analyze(AnalyzeContextInfo contextInfo) throws XPathException;
        void accept(ExpressionVisitor visitor);
        void dump(ExpressionDumper dumper);
        void resetState(boolean postOptimization);
    }

    private static class Mapping implements Entry {
        final Expression key;
        final Expression value;

        Mapping(final Expression key, final Expression value) {
            this.key = key;
            this.value = value;
        }

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

    private static class ContentEntry implements Entry {
        final Expression expr;

        public ContentEntry(final Expression expr) {
            this.expr = expr;
        }

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
