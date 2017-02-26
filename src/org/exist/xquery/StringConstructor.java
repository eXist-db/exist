/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.xquery;

import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

import java.util.ArrayList;
import java.util.List;

/**
 * XQuery 3.1 string constructor.
 */
public class StringConstructor extends AbstractExpression {

    protected List<StringConstructorPart> content = new ArrayList<>(13);

    public StringConstructor(final XQueryContext context) {
        super(context);
    }

    public void addContent(final String str) {
        content.add(new StringConstructorContent(str));
    }

    public void addInterpolation(final Expression expression) {
        content.add(new StringConstructorInterpolation(expression));
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        if(getContext().getXQueryVersion() < 31) {
            throw new XPathException(this, ErrorCodes.EXXQDY0003, "string constructors are not available before " +
                    "XQuery 3.1");
        }
        for (final StringConstructorPart p: content) {
            p.analyze(contextInfo);
        }
    }

    @Override
    public Sequence eval(Sequence contextSequence, final Item contextItem) throws XPathException {
        if (contextItem != null) {
            contextSequence = contextItem.toSequence();
        }

        final StringBuilder out = new StringBuilder();
        for (final StringConstructorPart p: content) {
            out.append(p.eval(contextSequence));
        }
        return new StringValue(out.toString());
    }

    @Override
    public int returnsType() {
        return Type.STRING;
    }

    @Override
    public int getCardinality() {
        return Cardinality.EXACTLY_ONE;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("``[");
        content.forEach(p -> p.dump(dumper));
        dumper.display("``]");
    }

    @Override
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        content.forEach(p -> p.resetState(postOptimization));
    }

    private interface StringConstructorPart {

        void analyze(final AnalyzeContextInfo contextInfo) throws XPathException;

        String eval(final Sequence contextSequence) throws XPathException;

        void dump(final ExpressionDumper dumper);

        void resetState(boolean postOptimization);
    }

    private static class StringConstructorContent implements StringConstructorPart {

        private final String content;

        StringConstructorContent(final String content) {
            this.content = content;
        }

        @Override
        public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
            // nothing to do
        }

        @Override
        public String eval(final Sequence contextSequence) throws XPathException {
            return content;
        }

        @Override
        public void dump(final ExpressionDumper dumper) {
            dumper.display(content);
        }

        @Override
        public void resetState(boolean postOptimization) {
            // nothing to do
        }
    }

    private static class StringConstructorInterpolation implements StringConstructorPart {

        private final Expression expression;

        StringConstructorInterpolation(final Expression expression) {
            this.expression = expression;
        }

        @Override
        public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
            expression.analyze(contextInfo);
        }

        @Override
        public String eval(final Sequence contextSequence) throws XPathException {
            final Sequence result = expression.eval(contextSequence);

            final StringBuilder out = new StringBuilder();
            boolean gotOne = false;
            for(final SequenceIterator i = result.iterate(); i.hasNext(); ) {
                final Item next = i.nextItem();
                if (gotOne) {
                    out.append(' ');
                }
                out.append(next.getStringValue());
                gotOne = true;
            }
            return out.toString();
        }

        @Override
        public void dump(final ExpressionDumper dumper) {
            dumper.display("`{");
            expression.dump(dumper);
            dumper.display("}`");
        }

        @Override
        public void resetState(boolean postOptimization) {
            expression.resetState(postOptimization);
        }
    }
}
