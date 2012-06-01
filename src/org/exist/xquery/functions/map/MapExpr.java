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

    protected List<Mapping> mappings = new ArrayList(13);

    public MapExpr(XQueryContext context) {
        super(context);
    }

    public void map(PathExpr path) {
        Mapping mapping = new Mapping(path.getExpression(0), path.getExpression(1));
        this.mappings.add(mapping);
    }

    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        for (Mapping mapping : this.mappings) {
            mapping.key.analyze(contextInfo);
            mapping.value.analyze(contextInfo);
        }
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (contextItem != null)
            contextSequence = contextItem.toSequence();
        MapType map = new MapType(this.context);
        for (Mapping mapping : this.mappings) {
            Sequence key = mapping.key.eval(contextSequence);
            if (key.getItemCount() != 1)
                throw new XPathException(MapErrorCode.EXMPDY001, "Expected single value for key, got " + key.getItemCount());
            AtomicValue atomic = key.itemAt(0).atomize();
            Sequence value = mapping.value.eval(contextSequence);
            map.add(atomic, value);
        }
        return map;
    }

    public int returnsType() {
        return Type.MAP;
    }

    public void dump(ExpressionDumper dumper) {
        dumper.display("map {");
        for (Mapping mapping : this.mappings) {
            mapping.key.dump(dumper);
            dumper.display(" := ");
            mapping.value.dump(dumper);
        }
        dumper.display("}");
    }

    private static class Mapping {
        Expression key;
        Expression value;

        public Mapping(Expression key, Expression value) {
            this.key = key;
            this.value = value;
        }
    }
}
