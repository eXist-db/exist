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
package org.exist.xquery;

import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

/**
 * @author <a href="adam@evolvedbinary.com">Adam Retter</a>
 * @author <a href="gabriele@strumenta.com">Gabriele Tomassetti</a>
 */
public class WindowExpr extends BindingExpression {

    public enum WindowType {
        TUMBLING_WINDOW,
        SLIDING_WINDOW
    }

    //private Expression inputSequence = null;
    private final WindowCondition windowStartCondition;
    private final WindowCondition windowEndCondition;

    private final WindowType windowType;
    public WindowExpr(final XQueryContext context, final WindowType type, final WindowCondition windowStartCondition, final WindowCondition windowEndCondition) {
        super(context);
        //this.inputSequence = inputSequence;
        this.windowType = type;
        this.windowStartCondition = windowStartCondition;
        this.windowEndCondition = windowEndCondition;
    }

    @Override
    public ClauseType getType() {
        return ClauseType.WINDOW;
    }

    public WindowType getWindowType() {
        return this.windowType;
    }

    public WindowCondition getWindowStartCondition() {
        return windowStartCondition;
    }

    public WindowCondition getWindowEndCondition() {
        return windowEndCondition;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        // TODO
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem)
            throws XPathException {
        // TODO

        Sequence resultSequence = null;
        return resultSequence;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display(this.getWindowType() == WindowType.TUMBLING_WINDOW ? "tumbling window " : "sliding window ", line);
        dumper.startIndent();
        dumper.display("$").display(varName);
        if (sequenceType != null) {
            dumper.display(" as ").display(sequenceType);
        }
        dumper.display(" in ");
        inputSequence.dump(dumper);
        dumper.endIndent().nl();
        //TODO : QuantifiedExpr
        if (returnExpr instanceof LetExpr){
            dumper.display(" ", returnExpr.getLine());
        } else {
            dumper.display("return", returnExpr.getLine());
        }
        dumper.startIndent();
        returnExpr.dump(dumper);
        dumper.endIndent().nl();
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append(this.getWindowType() == WindowType.TUMBLING_WINDOW ? "tumbling window " : "sliding window ");
        result.append("$").append(varName);
        if (sequenceType != null) {
            result.append(" as ").append(sequenceType);
        }
        result.append(" in ");
        result.append(inputSequence.toString());
        result.append(" ");
        result.append("start ").append(windowStartCondition.toString());
        result.append(" end ").append(windowEndCondition.toString());
        //TODO : QuantifiedExpr
        if (returnExpr instanceof LetExpr) {
            result.append(" ");
        } else {
            result.append("return ");
        }
        result.append(returnExpr.toString());
        return result.toString();
    }

    @Override
    public void accept(final ExpressionVisitor visitor) {
        visitor.visitWindowExpression(this);
    }

    @Override
    public boolean allowMixedNodesInReturn() {
        return true;
    }
}