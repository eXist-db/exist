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

import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

import javax.annotation.Nullable;

/**
 * Base class for implementing an XQuery Pragma expression.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public abstract class AbstractPragma implements Pragma {
    private final QName name;
    private @Nullable final String contents;
    private @Nullable final Expression expression;

    public AbstractPragma(@Nullable final Expression expression, final QName name, @Nullable final String contents) {
        this.expression = expression;
        this.name = name;
        this.contents = contents;
    }

    @Override
    public QName getName() {
        return name;
    }

    public @Nullable Expression getExpression() {
        return expression;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        // no-op by default
    }

    @Override
    public void before(final XQueryContext context, @Nullable final Expression expression, final Sequence contextSequence) throws XPathException {
        // no-op by default
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        // no-op by default
        return null;
    }

    @Override
    public void after(final XQueryContext context, @Nullable final Expression expression) throws XPathException {
        // no-op by default
    }

    protected @Nullable String getContents() {
        return contents;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("(# " + getName().getStringValue());
        if (getContents() != null) {
            dumper.display(' ').display(getContents());
        }
    }

    @Override
    public void resetState(final boolean postOptimization) {
        //no-op by default
    }

    @Override
    public String toString() {
        return "(# " + name + ' ' + contents + "#)";
    }
}
