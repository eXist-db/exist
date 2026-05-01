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
import org.exist.xquery.value.Type;

/**
 * Marker wrapper for an XPath/XQuery 4.0 keyword argument
 * ({@code name := expr}) appearing in a function-call argument list.
 *
 * The keyword name and the underlying value expression are kept together
 * so {@link FunctionFactory} can resolve the name to a positional slot
 * against the called function's signature. After resolution the wrapper
 * is replaced by its underlying expression.
 *
 * Spec: XPath/XQuery 4.0 PR... (Keyword arguments).
 */
public class KeywordArgument extends AbstractExpression {

    private final String name;
    private final Expression value;

    public KeywordArgument(final XQueryContext context, final String name, final Expression value) {
        super(context);
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public Expression getValue() {
        return value;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        // KeywordArgument is a parse-time wrapper; FunctionFactory should have
        // unwrapped it to its underlying value before analyze runs. Reaching
        // analyze means the wrapper survived, which is a programming error
        // (e.g. keyword argument used in a non-function-call position).
        throw new XPathException(this, ErrorCodes.XPST0003,
                "Keyword argument '" + name + ":=' is only allowed in a function-call argument list");
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        throw new XPathException(this, ErrorCodes.XPST0003,
                "Keyword argument '" + name + ":=' is only allowed in a function-call argument list");
    }

    @Override
    public int returnsType() {
        return value != null ? value.returnsType() : Type.ITEM;
    }

    @Override
    public Cardinality getCardinality() {
        return value != null ? value.getCardinality() : Cardinality.ZERO_OR_MORE;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display(name);
        dumper.display(" := ");
        if (value != null) {
            value.dump(dumper);
        }
    }

    @Override
    public String toString() {
        return name + " := " + (value != null ? value.toString() : "()");
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        if (value != null) {
            value.resetState(postOptimization);
        }
    }

    @Override
    public void setContextDocSet(final DocumentSet contextSet) {
        super.setContextDocSet(contextSet);
        if (value != null) {
            value.setContextDocSet(contextSet);
        }
    }
}
