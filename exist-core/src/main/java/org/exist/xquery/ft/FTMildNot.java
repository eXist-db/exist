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
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.ExpressionDumper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * W3C XQFT 3.0 — FTMildNot.
 *
 * <pre>FTMildNot ::= FTUnaryNot ( "not" "in" FTUnaryNot )*</pre>
 */
public class FTMildNot extends FTAbstractExpr {

    private final List<Expression> operands = new ArrayList<>();

    public FTMildNot(final XQueryContext context) {
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
        // XQFT 3.0 §3.3: operands of "not in" (mild not) must not contain
        // ftnot (FTUnaryNot) or "occurs" (FTTimes). Raise FTST0001 if found.
        if (operands.size() > 1) {
            for (final Expression operand : operands) {
                validateMildNotOperand(operand);
            }
        }
    }

    /**
     * Recursively check that an expression tree does not contain FTUnaryNot or FTTimes.
     */
    private void validateMildNotOperand(final Expression expr) throws XPathException {
        if (expr instanceof FTUnaryNot) {
            throw new XPathException(this, ErrorCodes.FTST0001,
                    "ftnot is not allowed as operand of 'not in' (mild not)");
        }
        if (expr instanceof FTWords) {
            final FTWords ftWords = (FTWords) expr;
            if (ftWords.getFTTimes() != null) {
                throw new XPathException(this, ErrorCodes.FTST0001,
                        "'occurs' is not allowed as operand of 'not in' (mild not)");
            }
        }
        // Recurse into sub-expressions
        if (expr instanceof FTAnd) {
            for (final Expression child : ((FTAnd) expr).getOperands()) {
                validateMildNotOperand(child);
            }
        } else if (expr instanceof FTOr) {
            for (final Expression child : ((FTOr) expr).getOperands()) {
                validateMildNotOperand(child);
            }
        } else if (expr instanceof FTMildNot) {
            for (final Expression child : ((FTMildNot) expr).getOperands()) {
                validateMildNotOperand(child);
            }
        } else if (expr instanceof FTPrimaryWithOptions) {
            validateMildNotOperand(((FTPrimaryWithOptions) expr).getPrimary());
        } else if (expr instanceof FTSelection) {
            validateMildNotOperand(((FTSelection) expr).getFTOr());
        }
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        for (int i = 0; i < operands.size(); i++) {
            if (i > 0) {
                dumper.display(" not in ");
            }
            operands.get(i).dump(dumper);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < operands.size(); i++) {
            if (i > 0) {
                sb.append(" not in ");
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
