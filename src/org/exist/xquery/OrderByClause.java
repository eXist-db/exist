package org.exist.xquery;

import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.OrderedValueSequence;
import org.exist.xquery.value.Sequence;

import java.util.List;

/**
 * Represents an "order by" clause within a FLWOR expression.
 */
public class OrderByClause extends AbstractFLWORClause {

    protected OrderSpec[] orderSpecs = null;
    protected OrderedValueSequence orderedResult = null;

    public OrderByClause(XQueryContext context, List<OrderSpec> orderSpecs) {
        super(context);
        this.orderSpecs = orderSpecs.toArray(new OrderSpec[orderSpecs.size()]);
    }

    public OrderSpec[] getOrderSpecs() {
        return orderSpecs;
    }

    @Override
    public ClauseType getType() {
        return ClauseType.ORDERBY;
    }

    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        unordered = (contextInfo.getFlags() & UNORDERED) > 0;
        final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
        newContextInfo.addFlag(SINGLE_STEP_EXECUTION);
        if (orderSpecs != null) {
            for (OrderSpec spec: orderSpecs) {
                spec.analyze(newContextInfo);
            }
        }
        returnExpr.analyze(newContextInfo);
    }

    @Override
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (orderedResult == null) {
            orderedResult = new OrderedValueSequence(orderSpecs, 100);
        }
        final Sequence result = getReturnExpression().eval(contextSequence, contextItem);
        if (result != null) {
            orderedResult.addAll(result);
        }
        return result;
    }

    @Override
    public Sequence postEval(Sequence seq) throws XPathException {
        if (orderedResult == null) {
            return seq;
        }
        orderedResult.sort();
        Sequence result = orderedResult;
        // reset to prepare for next iteration of outer loop
        orderedResult = null;

        if (getReturnExpression() instanceof FLWORClause) {
            result = ((FLWORClause) getReturnExpression()).postEval(result);
        }
        return super.postEval(result);
    }

    @Override
    public void dump(ExpressionDumper dumper) {
        dumper.display("order by ");
        for (int i = 0; i < orderSpecs.length; i++) {
            if (i > 0)
            {dumper.display(", ");}
            dumper.display(orderSpecs[i]);
        }
        dumper.nl();
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        visitor.visitOrderByClause(this);
    }

    @Override
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        orderedResult = null;
    }
}
