package org.exist.xquery.functions.map;

import org.exist.xquery.*;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import java.util.ArrayList;
import java.util.List;

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
        final MapType map = new MapType(this.context);
        for (final Mapping mapping : this.mappings) {
            final Sequence key = mapping.key.eval(contextSequence);
            if (key.getItemCount() != 1) {
                throw new XPathException(this, MapErrorCode.EXMPDY001, "Expected single value for key, got " + key.getItemCount());
            }
            final AtomicValue atomic = key.itemAt(0).atomize();
            final Sequence value = mapping.value.eval(contextSequence);
            if (map.contains(atomic)) {
                throw new XPathException(this, ErrorCodes.XQDY0137, "Key \"" + atomic.getStringValue() + "\" already exists in map.");
            }
            map.add(atomic, value);
        }
        return map;
    }

    @Override
    public int returnsType() {
        return Type.MAP;
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
        for (final Mapping mapping : this.mappings) {
            mapping.key.dump(dumper);
            dumper.display(" := ");
            mapping.value.dump(dumper);
        }
        dumper.display("}");
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
