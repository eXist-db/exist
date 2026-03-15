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
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.ExpressionDumper;

/**
 * W3C XQFT 3.0 — FTWindow positional filter.
 *
 * <pre>FTWindow ::= "window" AdditiveExpr FTUnit</pre>
 */
public class FTWindow extends FTAbstractExpr {

    private Expression windowExpr;
    private FTUnit unit;

    public FTWindow(final XQueryContext context) {
        super(context);
    }

    public void setWindowExpr(final Expression windowExpr) {
        this.windowExpr = windowExpr;
    }

    public Expression getWindowExpr() {
        return windowExpr;
    }

    public void setUnit(final FTUnit unit) {
        this.unit = unit;
    }

    public FTUnit getUnit() {
        return unit;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        windowExpr.analyze(contextInfo);
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("window ");
        windowExpr.dump(dumper);
        dumper.display(' ').display(unit.toString());
    }

    @Override
    public String toString() {
        return "window " + windowExpr.toString() + " " + unit.toString();
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        windowExpr.resetState(postOptimization);
    }
}
