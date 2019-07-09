/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2011 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id: SwitchExpression.java 13846 2011-02-25 09:52:38Z ellefj $
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
    private List<Case> cases = new ArrayList<Case>(5);
    
    public SwitchExpression(XQueryContext context, Expression operand) {
        super(context);
        this.operand = operand;
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
        final Sequence opSeq = operand.eval(contextSequence);
        Sequence result = null;
        if (opSeq.isEmpty()) {
        	result = defaultClause.returnClause.eval(contextSequence);
        } else {
            if (opSeq.hasMany()) {
                throw new XPathException(this, ErrorCodes.XPTY0004, "Cardinality error in switch operand ", opSeq);
            }
	        final AtomicValue opVal = opSeq.itemAt(0).atomize();
	        final Collator defaultCollator = context.getDefaultCollator();
	        for (final Case next : cases) {
	            for (final Expression caseOperand : next.operands) {
	                final Sequence caseSeq = caseOperand.eval(contextSequence, contextItem);
	                if (caseSeq.hasMany()) {
	                    throw new XPathException(this, ErrorCodes.XPTY0004, "Cardinality error in switch case operand ", caseSeq);
	                }
                    final AtomicValue caseVal = caseSeq.itemAt(0).atomize();
	                if (FunDeepEqual.deepEquals(caseVal, opVal, defaultCollator)) {
	                    return next.returnClause.eval(contextSequence);
	                }
	            }
	        }
        }
        if (result == null) {
            result = defaultClause.returnClause.eval(contextSequence);
        }
        
        return result;
    }

    public int returnsType() {
        return operand.returnsType();
    }

    public int getDependencies() {
        return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM;
    }
    
    public int getCardinality() {
        return Cardinality.ZERO_OR_MORE;
    }
    
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        operand.analyze(contextInfo);
        for (final Case next : cases) {
            next.returnClause.analyze(contextInfo);
        }
        defaultClause.returnClause.analyze(contextInfo);
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