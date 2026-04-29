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
 */
public class MapExpr extends AbstractExpression {

    private final List<Mapping> mappings = new ArrayList<>(13);

    public MapExpr(final XQueryContext context) {
        super(context);
    }

    public void map(final PathExpr key, final PathExpr value) {
        final Mapping mapping = new Mapping(key.simplify(), value.simplify());
        this.mappings.add(mapping);
    }

    /**
     * Add a bare expression entry (XQ4 PR2094). At evaluation time the
     * expression must yield a map, whose entries are merged into the
     * surrounding map. Used for {@code { $m1, $m2, "k": v }} and similar
     * conditional/spread patterns introduced in XPath/XQuery 4.0.
     */
    public void merge(final PathExpr expr) {
        this.mappings.add(new Mapping(null, expr.simplify()));
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        if (getContext().getXQueryVersion() < 30) {
            throw new XPathException(this, ErrorCodes.EXXQDY0003,
                    "Map is not available before XQuery 3.0");
        }
        // XQ4 PR2094: bare expression entries (no key) are an XQuery 4.0 feature.
        // In earlier versions the spec requires a parse-level XPST0003 error.
        if (getContext().getXQueryVersion() < 40) {
            for (final Mapping mapping : this.mappings) {
                if (mapping.key == null) {
                    throw new XPathException(this, ErrorCodes.XPST0003,
                            "Bare expression entries in a map constructor require XQuery 4.0; expected key:value pair");
                }
            }
        }
        contextInfo.setParent(this);
        for (final Mapping mapping : this.mappings) {
            if (mapping.key != null) {
                mapping.key.analyze(contextInfo);
            }
            mapping.value.analyze(contextInfo);
        }
    }

    @Override
    public Sequence eval(Sequence contextSequence, final Item contextItem) throws XPathException {
        if (contextItem != null) {
            contextSequence = contextItem.toSequence();
        }

        // Fast path for a single colon-pair literal — skip the linear/forked dance
        // and the duplicate-key check (a single mapping cannot collide with itself).
        // Bare-content mappings still need the full merge path below.
        if (this.mappings.size() == 1 && this.mappings.get(0).key != null) {
            final Mapping mapping = this.mappings.get(0);
            final Sequence key = mapping.key.eval(contextSequence, null);
            if (key.getItemCount() != 1) {
                throw new XPathException(this, MapErrorCode.EXMPDY001, "Expected single value for key, got " + key.getItemCount());
            }
            final AtomicValue atomic = key.itemAt(0).atomize();
            final Sequence value = mapping.value.eval(contextSequence, null);
            return new MapType(this, context, null, atomic, value);
        }

        final IMap<AtomicValue, Sequence> map = newLinearMap(null);
        final java.util.ArrayList<AtomicValue> insertionOrder = new java.util.ArrayList<>(this.mappings.size());

        boolean firstType = true;
        int prevType = AbstractMapType.UNKNOWN_KEY_TYPE;

        for (final Mapping mapping : this.mappings) {
            if (mapping.key == null) {
                // XQ4 PR2094 bare-content entry: evaluate, must be a map, merge entries.
                final Sequence merged = mapping.value.eval(contextSequence, null);
                for (final SequenceIterator i = merged.iterate(); i.hasNext(); ) {
                    final Item item = i.nextItem();
                    if (!(item instanceof AbstractMapType subMap)) {
                        throw new XPathException(this, ErrorCodes.XPTY0004,
                                "Bare map content expression must evaluate to a sequence of maps; got " +
                                        Type.getTypeName(item.getType()));
                    }
                    for (final IEntry<AtomicValue, Sequence> entry : subMap) {
                        final AtomicValue ek = entry.key();
                        if (map.contains(ek)) {
                            throw new XPathException(this, ErrorCodes.XQDY0137,
                                    "Key \"" + ek.getStringValue() + "\" already exists in map.");
                        }
                        map.put(ek, entry.value());
                        insertionOrder.add(ek);
                        final int et = ek.getType();
                        if (firstType) {
                            prevType = et;
                            firstType = false;
                        } else if (et != prevType) {
                            prevType = AbstractMapType.MIXED_KEY_TYPES;
                        }
                    }
                }
                continue;
            }
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
            insertionOrder.add(atomic);

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

        return new MapType(this, context, map.forked(), prevType, insertionOrder);
    }

    @Override
    public int returnsType() {
        return Type.MAP_ITEM;
    }

    @Override
    public void accept(final ExpressionVisitor visitor) {
        super.accept(visitor);
        for (final Mapping mapping : this.mappings) {
            if (mapping.key != null) {
                mapping.key.accept(visitor);
            }
            mapping.value.accept(visitor);
        }
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("map {");
        for (int i = 0; i < this.mappings.size(); i++) {
            final Mapping mapping = this.mappings.get(i);
            if (i > 0) {
                dumper.display(", ");
            }
            if (mapping.key != null) {
                mapping.key.dump(dumper);
                dumper.display(" : ");
            }
            mapping.value.dump(dumper);
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
        mappings.forEach(m -> m.resetState(postOptimization));
    }

    private static class Mapping {
        final Expression key;
        final Expression value;

        public Mapping(final Expression key, final Expression value) {
            this.key = key;
            this.value = value;
        }

        private void resetState(final boolean postOptimization) {
            if (key != null) {
                key.resetState(postOptimization);
            }
            value.resetState(postOptimization);
        }
    }
}
