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
 * W3C XQFT 3.0 — FTRange.
 *
 * <pre>
 * FTRange ::= ("exactly" AdditiveExpr)
 *           | ("at" "least" AdditiveExpr)
 *           | ("at" "most" AdditiveExpr)
 *           | ("from" AdditiveExpr "to" AdditiveExpr)
 * </pre>
 */
public class FTRange extends FTAbstractExpr {

    public enum RangeMode {
        EXACTLY, AT_LEAST, AT_MOST, FROM_TO
    }

    private RangeMode mode;
    private Expression expr1;
    private Expression expr2;  // only for FROM_TO

    public FTRange(final XQueryContext context) {
        super(context);
    }

    public void setMode(final RangeMode mode) {
        this.mode = mode;
    }

    public RangeMode getMode() {
        return mode;
    }

    public void setExpr1(final Expression expr1) {
        this.expr1 = expr1;
    }

    public Expression getExpr1() {
        return expr1;
    }

    public void setExpr2(final Expression expr2) {
        this.expr2 = expr2;
    }

    public Expression getExpr2() {
        return expr2;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        expr1.analyze(contextInfo);
        if (expr2 != null) {
            expr2.analyze(contextInfo);
        }
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        switch (mode) {
            case EXACTLY:
                dumper.display("exactly ");
                expr1.dump(dumper);
                break;
            case AT_LEAST:
                dumper.display("at least ");
                expr1.dump(dumper);
                break;
            case AT_MOST:
                dumper.display("at most ");
                expr1.dump(dumper);
                break;
            case FROM_TO:
                dumper.display("from ");
                expr1.dump(dumper);
                dumper.display(" to ");
                expr2.dump(dumper);
                break;
        }
    }

    @Override
    public String toString() {
        switch (mode) {
            case EXACTLY: return "exactly " + expr1.toString();
            case AT_LEAST: return "at least " + expr1.toString();
            case AT_MOST: return "at most " + expr1.toString();
            case FROM_TO: return "from " + expr1.toString() + " to " + expr2.toString();
            default: return "";
        }
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        expr1.resetState(postOptimization);
        if (expr2 != null) {
            expr2.resetState(postOptimization);
        }
    }
}
