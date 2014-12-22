package org.exist.xquery.functions.map;

import org.exist.xquery.*;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

/**
 * Created by wolf on 29/08/14.
 */
public class MapLookup extends AbstractExpression {

    private Expression mapExpression;
    private AtomicValue keyString = null;
    private Expression keyExpression = null;

    public MapLookup(XQueryContext context, Expression mapExpr, String keyString) {

        super(context);
        this.mapExpression = mapExpr;
        this.keyString = new StringValue(keyString);
    }

    public MapLookup(XQueryContext context, Expression mapExpr, Expression keyExpression) {
        super(context);
        this.mapExpression = mapExpr;
        this.keyExpression = keyExpression;
    }

    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        final AnalyzeContextInfo contextCopy = new AnalyzeContextInfo(contextInfo);
        if (mapExpression != null) {
            mapExpression.analyze(contextCopy);
        }
        if (keyExpression != null) {
            keyExpression.analyze(contextCopy);
        }
    }

    @Override
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (contextItem != null) {
            contextSequence = contextItem.toSequence();
        }
        Sequence leftSeq;
        if (mapExpression == null) {
            leftSeq = contextSequence;
        } else {
            leftSeq = mapExpression.eval(contextSequence);
        }
        if (!Type.subTypeOf(leftSeq.getItemType(), Type.MAP)) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "expression to the left of a map lookup operator needs to be a sequence of maps");
        }
        if (keyExpression != null) {
            final Sequence keySeq = keyExpression.eval(contextSequence);
            if (keySeq.isEmpty()) {
                return Sequence.EMPTY_SEQUENCE;
            }
            keyString = keySeq.itemAt(0).atomize();
        }
        ValueSequence result = new ValueSequence();
        for (SequenceIterator i = leftSeq.iterate(); i.hasNext(); ) {
            final AbstractMapType map = (AbstractMapType) i.nextItem();
            Sequence value = map.get(keyString);
            if (value != null) {
                result.addAll(value);
            }
        }
        return result;
    }

    @Override
    public int returnsType() {
        return Type.ITEM;
    }

    @Override
    public int getCardinality() {
        return Cardinality.ZERO_OR_MORE;
    }

    @Override
    public void dump(ExpressionDumper dumper) {
        if (mapExpression != null) {
            mapExpression.dump(dumper);
        }
        dumper.display("!");
        if (keyExpression == null) {
            try {
                dumper.display(keyString.getStringValue());
            } catch (XPathException e) {
                // impossible
            }
        } else {
            keyExpression.dump(dumper);
        }
    }

    @Override
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        if (mapExpression != null) {
            mapExpression.resetState(postOptimization);
        }
        if (keyExpression != null) {
            keyExpression.resetState(postOptimization);
        }
    }
}
