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
 * W3C XQFT 3.0 — FTUnaryNot.
 *
 * <pre>FTUnaryNot ::= ("ftnot")? FTPrimaryWithOptions</pre>
 */
public class FTUnaryNot extends FTAbstractExpr {

    private Expression operand;

    public FTUnaryNot(final XQueryContext context) {
        super(context);
    }

    public void setOperand(final Expression operand) {
        this.operand = operand;
    }

    public Expression getOperand() {
        return operand;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        operand.analyze(contextInfo);
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("ftnot ");
        operand.dump(dumper);
    }

    @Override
    public String toString() {
        return "ftnot " + operand.toString();
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        operand.resetState(postOptimization);
    }
}
