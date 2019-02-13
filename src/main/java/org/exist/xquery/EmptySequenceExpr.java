package org.exist.xquery;

import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * Represents an empty sequence constructor: ()
 */
public class EmptySequenceExpr extends AbstractExpression {

    public EmptySequenceExpr(XQueryContext context) {
        super(context);
    }

    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    }

    @Override
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        return Sequence.EMPTY_SEQUENCE;
    }

    @Override
    public int returnsType() {
        return Type.EMPTY;
    }

    @Override
    public int getCardinality() {
        return Cardinality.ZERO;
    }

    @Override
    public void dump(ExpressionDumper dumper) {
        dumper.display("()");
    }
}
