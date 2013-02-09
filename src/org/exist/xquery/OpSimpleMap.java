package org.exist.xquery;

import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.ValueSequence;

/**
 * Implements the XQuery 3.0 simple map operator "!".
 */
public class OpSimpleMap extends AbstractExpression {

    private Expression left;
    private PathExpr right;

    public OpSimpleMap(XQueryContext context, PathExpr left, PathExpr right) {
        super(context);
        this.left = left;
        this.right = right;
    }

    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        final AnalyzeContextInfo contextCopy = new AnalyzeContextInfo(contextInfo);
        left.analyze(contextCopy);
        right.analyze(contextCopy);
    }

    @Override
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (contextItem != null)
            {contextSequence = contextItem.toSequence();}
        final Sequence leftSeq = left.eval(contextSequence);
        if (leftSeq.isEmpty())
            {return Sequence.EMPTY_SEQUENCE;}

        final ValueSequence result = new ValueSequence();
        int pos = 0;
        for (final SequenceIterator i = leftSeq.iterate(); i.hasNext(); pos++) {
            context.setContextSequencePosition(pos, leftSeq);
            final Sequence rightSeq = right.eval(i.nextItem().toSequence());
            result.addAll(rightSeq);
        }
        return result;
    }

    @Override
    public int returnsType() {
        return right.returnsType();
    }

    @Override
    public int getCardinality() {
        return Cardinality.ZERO_OR_MORE;
    }

    @Override
    public void dump(ExpressionDumper dumper) {
        left.dump(dumper);
        dumper.display(" ! ");
        right.dump(dumper);
    }
}
