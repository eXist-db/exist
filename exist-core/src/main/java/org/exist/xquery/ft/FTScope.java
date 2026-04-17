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

import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.ExpressionDumper;

/**
 * W3C XQFT 3.0 — FTScope positional filter.
 *
 * <pre>FTScope ::= ("same" | "different") FTBigUnit</pre>
 */
public class FTScope extends FTAbstractExpr {

    public enum ScopeType { SAME, DIFFERENT }
    public enum BigUnit { SENTENCE, PARAGRAPH }

    private ScopeType scopeType;
    private BigUnit bigUnit;

    public FTScope(final XQueryContext context) {
        super(context);
    }

    public void setScopeType(final ScopeType scopeType) {
        this.scopeType = scopeType;
    }

    public ScopeType getScopeType() {
        return scopeType;
    }

    public void setBigUnit(final BigUnit bigUnit) {
        this.bigUnit = bigUnit;
    }

    public BigUnit getBigUnit() {
        return bigUnit;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        // no children to analyze
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display(scopeType.name().toLowerCase());
        dumper.display(' ');
        dumper.display(bigUnit.name().toLowerCase());
    }

    @Override
    public String toString() {
        return scopeType.name().toLowerCase() + " " + bigUnit.name().toLowerCase();
    }
}
