package org.exist.xquery;

import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

import java.util.Map;

/**
 * Implements the XQuery 3.1 lookup operator on maps and arrays.
 *
 * @author Wolfgang
 */
public class Lookup extends AbstractExpression {

    private Expression contextExpression;
    private Sequence keys = null;
    private Expression keyExpression = null;

    public Lookup(XQueryContext context, Expression ctxExpr) {
        super(context);
        this.contextExpression = ctxExpr;
    }

    public Lookup(XQueryContext context, Expression ctxExpr, String keyString) {
        this(context, ctxExpr);
        this.keys = new StringValue(keyString);
    }

    public Lookup(XQueryContext context, Expression ctxExpr, int position) {
        this(context, ctxExpr);
        this.keys = new IntegerValue(position);
    }

    public Lookup(XQueryContext context, Expression ctxExpr, Expression keyExpression) {
        this(context, ctxExpr);
        this.keyExpression = keyExpression;
    }

    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        final AnalyzeContextInfo contextCopy = new AnalyzeContextInfo(contextInfo);
        if (contextExpression != null) {
            contextExpression.analyze(contextCopy);
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
        if (contextExpression == null) {
            leftSeq = contextSequence;
        } else {
            leftSeq = contextExpression.eval(contextSequence);
        }
        final int contextType = leftSeq.getItemType();

        // Make compatible with baseX and Saxon
        if (leftSeq.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }


        if (!(Type.subTypeOf(contextType, Type.MAP) || Type.subTypeOf(contextType, Type.ARRAY))) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "expression to the left of a lookup operator needs to be a sequence of maps or arrays");
        }
        if (keyExpression != null) {
            keys = keyExpression.eval(contextSequence);
            if (keys.isEmpty()) {
                return Sequence.EMPTY_SEQUENCE;
            }
        }
        try {
            final ValueSequence result = new ValueSequence();
            for (SequenceIterator i = leftSeq.iterate(); i.hasNext(); ) {
                final LookupSupport item = (LookupSupport) i.nextItem();
                if (keys != null) {
                    for (SequenceIterator j = keys.iterate(); j.hasNext(); ) {
                        final AtomicValue key = j.nextItem().atomize();
                        Sequence value = item.get(key);
                        if (value != null) {
                            result.addAll(value);
                        }
                    }
                } else if(item instanceof ArrayType) {
                    result.addAll(item.keys());
                } else if(item instanceof AbstractMapType) {
                    for(final Map.Entry<AtomicValue, Sequence> entry : ((AbstractMapType)item)) {
                        result.addAll(entry.getValue());
                    }
                }
            }
            return result;
        } catch (XPathException e) {
            e.setLocation(getLine(), getColumn(), getSource());
            throw e;
        }
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
        if (contextExpression != null) {
            contextExpression.dump(dumper);
        }
        dumper.display("?");
        if (keyExpression == null && keys != null && keys.getItemCount() > 0) {
            try {
                dumper.display(keys.itemAt(0).getStringValue());
            } catch (XPathException e) {
                // impossible
            }
        } else if (keyExpression != null) {
            keyExpression.dump(dumper);
        }
    }

    @Override
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        if (contextExpression != null) {
            contextExpression.resetState(postOptimization);
        }
        if (keyExpression != null) {
            keyExpression.resetState(postOptimization);
        }
    }

    public interface LookupSupport {

        public Sequence get(AtomicValue key) throws XPathException;

        public Sequence keys() throws XPathException;
    }
}
