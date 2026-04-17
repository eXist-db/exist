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

import java.util.ArrayList;
import java.util.List;

import com.ibm.icu.text.Collator;
import org.exist.dom.persistent.DocumentSet;
import org.exist.xquery.functions.fn.FunDeepEqual;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

/**
 * Implements the XQuery 3 switch expression.
 * 
 * @author ljo
 *
 */
public class SwitchExpression extends AbstractExpression {

    /**
     * Internal class used to hold a single case clause.
     */
    private static class Case {
        List<Expression> operands;
        Expression returnClause;
       
        public Case(List<Expression> caseOperands, Expression caseClause) {
            this.operands = caseOperands;
            this.returnClause = caseClause;
        }
    }
    
    private Expression operand;
    private Case defaultClause = null;
    private List<Case> cases = new ArrayList<>(5);
    private boolean booleanMode = false;

    public SwitchExpression(XQueryContext context, Expression operand) {
        super(context);
        this.operand = operand;
    }

    /**
     * Set boolean mode for XQ4 omitted comparand: switch () { case boolExpr return ... }
     * In boolean mode, each case operand is evaluated and its effective boolean value determines the match.
     */
    public void setBooleanMode(boolean booleanMode) {
        this.booleanMode = booleanMode;
    }
    
    /**
     * Add case clause(s) with a return.
     *
     * @param caseOperands list of operands
     * @param returnClause the return clause
     */
    public void addCase(List<Expression> caseOperands, Expression returnClause) {
        cases.add(new Case(caseOperands, returnClause));
    }
    
    /**
     * Set the default clause.
     *
     * @param defaultClause the default clause
     */
    public void setDefault(Expression defaultClause) {
        this.defaultClause = new Case(null, defaultClause);
    }
    
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if(getContext().getXQueryVersion() < 30){
            throw new XPathException(this, ErrorCodes.EXXQDY0003, "switch expression is not available before XQuery 3.0", contextSequence);
        }

        if (contextItem != null)
            {contextSequence = contextItem.toSequence();}

        if (booleanMode) {
            // XQ4 omitted comparand: evaluate each case operand as boolean
            return evalBooleanMode(contextSequence, contextItem);
        }

        final Sequence opSeq = operand.eval(contextSequence, null);
        if (opSeq.hasMany()) {
            throw new XPathException(this, ErrorCodes.XPTY0004, "Cardinality error in switch operand ", opSeq);
        }
        final Collator defaultCollator = context.getDefaultCollator();
        if (opSeq.isEmpty()) {
            // XQ4: empty comparand can match case () (empty case operand)
            for (final Case next : cases) {
                for (final Expression caseOperand : next.operands) {
                    final Sequence caseSeq = caseOperand.eval(contextSequence, contextItem);
                    if (caseSeq.isEmpty()) {
                        return next.returnClause.eval(contextSequence, null);
                    }
                }
            }
        } else {
            final AtomicValue opVal = opSeq.itemAt(0).atomize();
            for (final Case next : cases) {
                for (final Expression caseOperand : next.operands) {
                    final Sequence caseSeq = caseOperand.eval(contextSequence, contextItem);
                    if (context.getXQueryVersion() <= 30 && caseSeq.hasMany()) {
                        throw new XPathException(this, ErrorCodes.XPTY0004, "Cardinality error in switch case operand ", caseSeq);
                    }
                    // XQ4: case operand may be a sequence; match if any item equals the comparand
                    for (int i = 0; i < caseSeq.getItemCount(); i++) {
                        final AtomicValue caseVal = caseSeq.itemAt(i).atomize();
                        if (FunDeepEqual.deepEquals(caseVal, opVal, defaultCollator)) {
                            return next.returnClause.eval(contextSequence, null);
                        }
                    }
                }
            }
        }
        return defaultClause.returnClause.eval(contextSequence, null);
    }

    private Sequence evalBooleanMode(Sequence contextSequence, Item contextItem) throws XPathException {
        for (final Case next : cases) {
            for (final Expression caseOperand : next.operands) {
                final Sequence caseSeq = caseOperand.eval(contextSequence, contextItem);
                if (caseSeq.effectiveBooleanValue()) {
                    return next.returnClause.eval(contextSequence, null);
                }
            }
        }
        return defaultClause.returnClause.eval(contextSequence, null);
    }

    public int returnsType() {
        return operand.returnsType();
    }

    public int getDependencies() {
        return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM;
    }

    @Override
    public Cardinality getCardinality() {
        return Cardinality.ZERO_OR_MORE;
    }
    
    @Override
    public boolean isUpdating() {
        for (final Case c : cases) {
            if (c.returnClause.isUpdating()) {
                return true;
            }
        }
        return defaultClause != null && defaultClause.returnClause.isUpdating();
    }

    @Override
    public boolean isVacuous() {
        for (final Case c : cases) {
            if (!c.returnClause.isVacuous()) {
                return false;
            }
        }
        return defaultClause == null || defaultClause.returnClause.isVacuous();
    }

    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        final AnalyzeContextInfo myContextInfo = new AnalyzeContextInfo(contextInfo);
        myContextInfo.setParent(this);

        // Operand and case operands are non-updating contexts
        final AnalyzeContextInfo operandInfo = new AnalyzeContextInfo(myContextInfo);
        operandInfo.addFlag(NON_UPDATING_CONTEXT);
        operand.analyze(operandInfo);
        for (final Case next : cases) {
            for (final Expression caseOperand : next.operands) {
                final AnalyzeContextInfo caseOpInfo = new AnalyzeContextInfo(myContextInfo);
                caseOpInfo.addFlag(NON_UPDATING_CONTEXT);
                caseOperand.analyze(caseOpInfo);
            }
            myContextInfo.setParent(this);
            next.returnClause.analyze(myContextInfo);
        }
        myContextInfo.setParent(this);
        defaultClause.returnClause.analyze(myContextInfo);

        // XUST0001: check branch compatibility
        boolean hasUpdating = false;
        boolean hasNonUpdating = false;
        for (final Case c : cases) {
            if (c.returnClause.isUpdating()) {
                hasUpdating = true;
            } else if (!c.returnClause.isVacuous()) {
                hasNonUpdating = true;
            }
        }
        if (defaultClause != null) {
            if (defaultClause.returnClause.isUpdating()) {
                hasUpdating = true;
            } else if (!defaultClause.returnClause.isVacuous()) {
                hasNonUpdating = true;
            }
        }
        if (hasUpdating && hasNonUpdating) {
            throw new XPathException(this, ErrorCodes.XUST0001,
                    "switch branches mix updating and non-updating expressions");
        }
    }

    public void setContextDocSet(DocumentSet contextSet) {
        super.setContextDocSet(contextSet);
        operand.setContextDocSet(contextSet);
    }
    
    public void dump(ExpressionDumper dumper) {
        dumper.display("switch(", line);
        operand.dump(dumper);
        dumper.display(')');
        dumper.startIndent();
        for (final Case next : cases) {
            for (final Expression caseOperand : next.operands) {
                dumper.display("case ");
                dumper.display(caseOperand);
            }
            dumper.display(" return ");
            dumper.display(next.returnClause).nl();
        }
        dumper.display("default ");
        defaultClause.returnClause.dump(dumper);
        dumper.endIndent();
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        operand.accept(visitor);
        for (final Case next : cases) {
            next.returnClause.accept(visitor);
        }
        defaultClause.returnClause.accept(visitor);
    }

    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        
        operand.resetState(postOptimization);
        defaultClause.returnClause.resetState(postOptimization);
        for (final Case next : cases) {
            next.returnClause.resetState(postOptimization);
        }
    }
}