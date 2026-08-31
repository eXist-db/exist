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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * W3C XQFT 3.0 — FTAnd.
 *
 * <pre>FTAnd ::= FTMildNot ( "ftand" FTMildNot )*</pre>
 */
public class FTAnd extends FTAbstractExpr {

    private final List<Expression> operands = new ArrayList<>();

    public FTAnd(final XQueryContext context) {
        super(context);
    }

    public void addOperand(final Expression operand) {
        operands.add(operand);
    }

    public List<Expression> getOperands() {
        return Collections.unmodifiableList(operands);
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        for (final Expression operand : operands) {
            operand.analyze(contextInfo);
        }
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        for (int i = 0; i < operands.size(); i++) {
            if (i > 0) {
                dumper.display(" ftand ");
            }
            operands.get(i).dump(dumper);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < operands.size(); i++) {
            if (i > 0) {
                sb.append(" ftand ");
            }
            sb.append(operands.get(i).toString());
        }
        return sb.toString();
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        for (final Expression operand : operands) {
            operand.resetState(postOptimization);
        }
    }
}
