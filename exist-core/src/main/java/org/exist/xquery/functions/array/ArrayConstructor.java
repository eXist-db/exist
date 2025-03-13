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
    private List<Expression> arguments = new ArrayList<>();

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
                final List<Sequence> items = new ArrayList<>(arguments.size());
                for (Expression arg: arguments) {
                    final Sequence result = arg.eval(contextSequence, contextItem);
                    if (result != null) {
                        items.add(result);
                    }
                }
                return new ArrayType(this, context, items);
            default:
                final Sequence result =  arguments.isEmpty() ? Sequence.EMPTY_SEQUENCE : arguments.getFirst().eval(contextSequence, contextItem);
                return new ArrayType(this, context, result);
        }
    }

    @Override
    public int returnsType() {
        return Type.ARRAY_ITEM;
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
