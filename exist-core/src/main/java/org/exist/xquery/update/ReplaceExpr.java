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
package org.exist.xquery.update;

import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

/**
 * @author <a href="adam@evolvedbinary.com">Adam Retter</a>
 * @author <a href="gabriele@strumenta.com">Gabriele Tomassetti</a>
 */
public class ReplaceExpr extends ModifyingExpression {
    public enum ReplacementType {
        NODE,
        VALUE
    }

    private final Expression withExpr;
    private final ReplaceExpr.ReplacementType replacementType;

    public ReplaceExpr(final XQueryContext context, final Expression target, final Expression with, final ReplaceExpr.ReplacementType replacementType) {
        super(context, target);
        this.withExpr = with;
        this.replacementType = replacementType;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        return Sequence.EMPTY_SEQUENCE;
    }

    @Override
    public Cardinality getCardinality() {
        return Cardinality.ONE_OR_MORE;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("replace").nl();
        dumper.startIndent();
        targetExpr.dump(dumper);
        dumper.endIndent();
        dumper.display(replacementType).nl();
        dumper.startIndent();
        withExpr.dump(dumper);
        dumper.endIndent();
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("replace ");
        result.append(targetExpr.toString());
        result.append(" ");
        result.append(replacementType.toString());
        result.append(" ");
        result.append(withExpr.toString());
        return result.toString();
    }

    public ReplaceExpr.ReplacementType getReplacementType() {
        return replacementType;
    }

    public Expression getWithExpr() {
        return withExpr;
    }
}
