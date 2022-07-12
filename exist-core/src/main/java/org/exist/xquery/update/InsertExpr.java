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

import org.exist.xquery.*;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * @author <a href="adam@evolvedbinary.com">Adam Retter</a>
 * @author <a href="gabriele@strumenta.com">Gabriele Tomassetti</a>
 */
public class InsertExpr extends AbstractExpression {
    public enum Choice {
        FIRST,
        LAST,
        INTO,
        AFTER,
        BEFORE
    }

    private final Expression source;
    private final Expression target;
    private final Choice choice;

    public InsertExpr(final XQueryContext context, final Expression source, final Expression target, final Choice choice) {
        super(context);
        this.source = source;
        this.target = target;
        this.choice = choice;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        return Sequence.EMPTY_SEQUENCE;
    }

    @Override
    public int returnsType() {
        // placeholder implementation
        return Type.EMPTY;
    }

    public Category getCategory() {
        // placeholder implementation
        return Category.UPDATING;
    }

    @Override
    public Cardinality getCardinality() {
        return Cardinality.ONE_OR_MORE;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("insert").nl();
        dumper.startIndent();
        source.dump(dumper);
        dumper.endIndent();
        dumper.display(choice).nl();
        dumper.startIndent();
        target.dump(dumper);
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("insert ");
        result.append(source.toString());
        result.append(" ");
        result.append(choice.toString());
        result.append(" ");
        result.append(target.toString());
        return result.toString();
    }
}
