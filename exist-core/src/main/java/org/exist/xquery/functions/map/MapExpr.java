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

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        if (getContext().getXQueryVersion() < 30) {
            throw new XPathException(this, ErrorCodes.EXXQDY0003,
                    "Map is not available before XQuery 3.0");
        }
        contextInfo.setParent(this);
        for (final Mapping mapping : this.mappings) {
            mapping.key.analyze(contextInfo);
            mapping.value.analyze(contextInfo);
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

        for (final Mapping mapping : this.mappings) {
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
        for (final Mapping mapping : this.mappings) {
            mapping.key.accept(visitor);
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
            mapping.key.dump(dumper);
            dumper.display(" : ");
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
            key.resetState(postOptimization);
            value.resetState(postOptimization);
        }
    }
}
