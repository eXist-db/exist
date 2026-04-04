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
package org.exist.xquery.ft;

import org.exist.xquery.AbstractExpression;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * W3C XQFT 3.0 — FTSelection.
 *
 * <pre>FTSelection ::= FTOr FTPosFilter*</pre>
 *
 * Wraps an FTOr expression with optional positional filters.
 */
public class FTSelection extends AbstractExpression {

    private Expression ftOr;
    private final List<Expression> posFilters = new ArrayList<>();

    public FTSelection(final XQueryContext context) {
        super(context);
    }

    public void setFTOr(final Expression ftOr) {
        this.ftOr = ftOr;
    }

    public Expression getFTOr() {
        return ftOr;
    }

    public void addPosFilter(final Expression filter) {
        posFilters.add(filter);
    }

    public List<Expression> getPosFilters() {
        return Collections.unmodifiableList(posFilters);
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        ftOr.analyze(contextInfo);
        for (final Expression filter : posFilters) {
            filter.analyze(contextInfo);
        }
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        throw new XPathException(this, "FTSelection cannot be evaluated directly");
    }

    @Override
    public int returnsType() {
        return Type.ITEM;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        ftOr.dump(dumper);
        for (final Expression filter : posFilters) {
            dumper.display(' ');
            filter.dump(dumper);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(ftOr.toString());
        for (final Expression filter : posFilters) {
            sb.append(' ').append(filter.toString());
        }
        return sb.toString();
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        ftOr.resetState(postOptimization);
        for (final Expression filter : posFilters) {
            filter.resetState(postOptimization);
        }
    }
}
