package org.exist.xquery.functions.array;

import org.exist.xquery.*;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * A literal array constructor (XQuery 3.1)
 */
public class ArrayConstructor extends AbstractExpression {

    public enum ConstructorType { SQUARE_ARRAY, CURLY_ARRAY }

    private ConstructorType type;
    private List<Expression> arguments = new ArrayList<Expression>();

    public ArrayConstructor(XQueryContext context, ConstructorType type) {
        super(context);
        this.type = type;
    }

    public void addArgument(Expression expression) {
        arguments.add(expression);
    }

    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        for (Expression expr: arguments) {
            expr.analyze(contextInfo);
        }
    }

    @Override
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getXQueryVersion() < 31) {
            throw new XPathException(this, ErrorCodes.EXXQDY0004, "arrays are only available in XQuery 3.1, but version declaration states " +
                context.getXQueryVersion());
        }
        switch(type) {
            case SQUARE_ARRAY:
                final List<Sequence> items = new ArrayList<Sequence>(arguments.size());
                for (Expression arg: arguments) {
                    final Sequence result = arg.eval(contextSequence, contextItem);
                    if (result != null) {
                        items.add(result);
                    }
                }
                return new ArrayType(context, items);
            default:
                final Sequence result = arguments.get(0).eval(contextSequence, contextItem);
                return new ArrayType(context, result);
        }
    }

    @Override
    public int returnsType() {
        return Type.ARRAY;
    }

    @Override
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        for (Expression expr: arguments) {
            expr.resetState(postOptimization);
        }
    }

    @Override
    public void dump(ExpressionDumper dumper) {
        dumper.display("array {");
        dumper.display('}');
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        for (Expression expr: arguments) {
            expr.accept(visitor);
        }
    }
}
