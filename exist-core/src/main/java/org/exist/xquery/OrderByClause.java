package org.exist.xquery;

import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.OrderedValueSequence;
import org.exist.xquery.value.Sequence;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Stack;

/**
 * Represents an "order by" clause within a FLWOR expression.
 */
public class OrderByClause extends AbstractFLWORClause {

    protected OrderSpec[] orderSpecs = null;

    /*  OrderByClause needs to keep state between calls to eval and postEval. We thus need
        to track state in a stack to avoid overwrites if we're called recursively. */
    private final Deque<OrderedValueSequence> stack = new ArrayDeque<>();

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
        final OrderedValueSequence orderedResult;
        if (stack.isEmpty()) {
            orderedResult = new OrderedValueSequence(orderSpecs, 100);
        } else {
            orderedResult = stack.pop();
        }
        final Sequence result = getReturnExpression().eval(contextSequence, contextItem);
        if (result != null) {
            orderedResult.addAll(result);
        }
        stack.push(orderedResult);
        return result;
    }

    @Override
    public Sequence postEval(Sequence seq) throws XPathException {
        if (stack.isEmpty()) {
            return seq;
        }
        final OrderedValueSequence orderedResult = stack.pop();
        orderedResult.sort();
        Sequence result = orderedResult;

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
        returnExpr.resetState(postOptimization);
        stack.clear();
    }
}
