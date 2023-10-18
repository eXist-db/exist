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

import org.exist.dom.persistent.DocumentSet;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * Implements an XQuery extension expression. An extension expression starts with
 * a one or more pragmas, followed by an expression enclosed in curly braces. For evaluation
 * details check {@link #eval(Sequence, Item)}.
 *
 * @author wolf
 */
public class ExtensionExpression extends AbstractExpression {

    @Nullable private Pragma[] pragmas = null;
    private Expression innerExpression;

    public ExtensionExpression(final XQueryContext context) {
        super(context);
    }

    public void setExpression(final Expression inner) {
        this.innerExpression = inner;
    }

    public void addPragma(final Pragma pragma) {
        if (pragmas == null) {
            pragmas = new Pragma[1];
        } else {
            pragmas = Arrays.copyOf(pragmas, pragmas.length + 1);
        }
        pragmas[pragmas.length - 1] = pragma;
    }

    /**
     * For every pragma in the list, calls {@link Pragma#before(XQueryContext, Expression, Sequence)} before evaluation.
     * The method then tries to call {@link Pragma#eval(Sequence, Item)} on every pragma.
     * If a pragma does not return null for this call, the returned Sequence will become the result
     * of the extension expression. If more than one pragma returns something for eval, an exception
     * will be thrown. If all pragmas return null, we call eval on the original expression and return
     * that.
     */
    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        callBefore(contextSequence);
        @Nullable Sequence result = null;
        if (pragmas != null) {
            for (final Pragma pragma : pragmas) {
                final Sequence temp = pragma.eval(contextSequence, contextItem);
                if (temp != null) {
                    result = temp;
                    break;
                }
            }
        }
        if (result == null) {
            result = innerExpression.eval(contextSequence, contextItem);
        }
        callAfter();
        return result;
    }

    private void callAfter() throws XPathException {
        if (pragmas == null) {
            return;
        }

        for (final Pragma pragma : pragmas) {
            pragma.after(context, innerExpression);
        }
    }

    private void callBefore(final Sequence contextSequence) throws XPathException {
        if (pragmas == null) {
            return;
        }

        for (final Pragma pragma : pragmas) {
            pragma.before(context, innerExpression, contextSequence);
        }
    }

    @Override
    public int returnsType() {
        return innerExpression.returnsType();
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        final AnalyzeContextInfo newContext = new AnalyzeContextInfo(contextInfo);
        if (pragmas != null) {
            for (final Pragma pragma : pragmas) {
                pragma.analyze(newContext);
            }
        }
        innerExpression.analyze(newContext);
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        if (pragmas != null) {
            for (final Pragma pragma : pragmas) {
                pragma.dump(dumper);
                dumper.nl();
            }
        }
        dumper.display('{');
        dumper.startIndent();
        innerExpression.dump(dumper);
        dumper.endIndent();
        dumper.nl().display("}", line).nl();
    }

    @Override
    public int getDependencies() {
        return innerExpression.getDependencies();
    }

    @Override
    public Cardinality getCardinality() {
        return innerExpression.getCardinality();
    }

    @Override
    public void setContextDocSet(final DocumentSet contextSet) {
        super.setContextDocSet(contextSet);
        innerExpression.setContextDocSet(contextSet);
    }

    @Override
    public void setPrimaryAxis(final int axis) {
        innerExpression.setPrimaryAxis(axis);
    }

    @Override
    public int getPrimaryAxis() {
        return innerExpression.getPrimaryAxis();
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        innerExpression.resetState(postOptimization);
        if (pragmas != null) {
            for (final Pragma pragma : pragmas) {
                pragma.resetState(postOptimization);
            }
        }
    }

    @Override
    public void accept(final ExpressionVisitor visitor) {
        visitor.visit(innerExpression);
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();

        if (pragmas != null) {
            for (final Pragma pragma : pragmas) {
                result.append(pragma.toString());
            }
        }

        result.append("{ ");
        result.append(innerExpression.toString());
        result.append(" }");

        return result.toString();
    }
}
