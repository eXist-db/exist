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
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

/**
 * Implements a count clause inside a FLWOR expressions.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 * @author <a href="mailto:gabriele@strumenta.com">Gabriele Tomassetti</a>
 */
public class CountClause extends AbstractFLWORClause {

    final String varName;

    public CountClause(final XQueryContext context, final String countName) {
        super(context);
        this.varName = countName;
    }

    @Override
    public ClauseType getType() {
        return ClauseType.COUNT;
    }

    public String getVarName() {
        return varName;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {

    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        return null;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("count", this.getLine());
        dumper.startIndent();
        dumper.display(this.varName);
        dumper.endIndent().nl();
    }
}