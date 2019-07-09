/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
 *  http://exist-db.org
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery;

import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements the XQuery typeswitch construct.
 * 
 * @author wolf
 *
 */
public class TypeswitchExpression extends AbstractExpression {

    /**
     * Internal class used to hold a single case clause.
     */
    private static class Case {
        SequenceType[] types;
        Expression returnClause;
        QName variable;
        
        public Case(SequenceType[] types, QName variable, Expression caseClause) {
            this.types = types;
            this.variable = variable;
            this.returnClause = caseClause;
        }
    }
    
    private Expression operand;
    private Case defaultClause = null;
    private List<Case> cases = new ArrayList<Case>(5);
    
    public TypeswitchExpression(XQueryContext context, Expression operand) {
        super(context);
        this.operand = operand;
    }
    
    /**
     * Add a case clause with a sequence type and an optional variable declaration.
     *
     * @param types the sequence types to match
     * @param var name of the variable to bind the current item to
     * @param caseClause the expression to evaluate
     */
    public void addCase(SequenceType[] types, QName var, Expression caseClause) {
        cases.add(new Case(types, var, caseClause));
    }
    
    /**
     * Set the default clause with an optional variable declaration.
     *
     * @param var the name of the variable to bind the current item to
     * @param defaultClause the expression to evaluate
     */
    public void setDefault(QName var, Expression defaultClause) {
        this.defaultClause = new Case(null, var, defaultClause);
    }
    
    public Sequence eval(Sequence contextSequence, Item contextItem)
            throws XPathException {
        if (contextItem != null)
            {contextSequence = contextItem.toSequence();}
        final Sequence opSeq = operand.eval(contextSequence);
        Sequence result = null;
        
        final LocalVariable mark = context.markLocalVariables(false);
        try {
        	for (int i = 0; i < cases.size() && result == null; i++) {
        		final Case next = cases.get(i);
                for (SequenceType type: next.types) {
                    if (checkType(type, opSeq)) {
                        if (next.variable != null) {
                            final LocalVariable var = new LocalVariable(next.variable);
                            var.setSequenceType(type);
                            var.setValue(opSeq);
                            var.setContextDocs(operand.getContextDocSet());
                            var.checkType();
                            context.declareVariableBinding(var);
                        }

                        result = next.returnClause.eval(contextSequence);
                        break;
                    }
                }
        	}
        	
        	if (result == null) {
        		if (defaultClause.variable != null) {
        			final LocalVariable var = new LocalVariable(defaultClause.variable);
        			var.setValue(opSeq);
        			var.setContextDocs(operand.getContextDocSet());
        			context.declareVariableBinding(var);
        		}
        		
        		result = defaultClause.returnClause.eval(contextSequence);
        	}
        } finally {
        	context.popLocalVariables(mark, result);
        }
        
        return result;
    }

    private boolean checkType(SequenceType type, Sequence seq) throws XPathException {
        final int requiredCardinality = type.getCardinality();
        int actualCardinality;
        if (seq.isEmpty()) {actualCardinality = Cardinality.EMPTY;}
        else if (seq.hasMany()) {actualCardinality = Cardinality.MANY;}
        else {actualCardinality = Cardinality.ONE;}
        
        if (!Cardinality.checkCardinality(requiredCardinality, actualCardinality))
            {return false;}
        for(final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
            final Item next = i.nextItem();
            if(!type.checkType(next)) {
                return false;
            }
        }
        return true;
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
        
        final LocalVariable mark0 = context.markLocalVariables(false);
        
        try {
        	for (final Case next : cases) {
        		final LocalVariable mark1 = context.markLocalVariables(false);
        		try {
        			if (next.variable != null) {
        				final LocalVariable var = new LocalVariable(next.variable);
                        if (next.types.length == 1) {
                            var.setSequenceType(next.types[0]);
                        }
        				context.declareVariableBinding(var);
        			}
        			next.returnClause.analyze(contextInfo);
        		} finally {
        			context.popLocalVariables(mark1);
        		}
        	}
        	if (defaultClause.variable != null) {
        		final LocalVariable var = new LocalVariable(defaultClause.variable);
        		context.declareVariableBinding(var);
        	}
        	defaultClause.returnClause.analyze(contextInfo);
        } finally {
        	context.popLocalVariables(mark0);
        }
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        operand.accept(visitor);
        for (final Case next : cases) {
            next.returnClause.accept(visitor);
        }
        defaultClause.returnClause.accept(visitor);
    }

    public void setContextDocSet(DocumentSet contextSet) {
        super.setContextDocSet(contextSet);
        operand.setContextDocSet(contextSet);
    }
    
    public void dump(ExpressionDumper dumper) {
        dumper.display("typeswitch(", line);
        operand.dump(dumper);
        dumper.display(')');
        dumper.startIndent();
        for (int i = 0; i < cases.size(); i++) {
            final Case caseClause = (Case) cases.get(i);
            dumper.display("case ");
            if (caseClause.variable != null) {
                dumper.display('$');
                dumper.display(caseClause.variable.getStringValue());
                dumper.display(" as ");
            }
            for (int j = 0; j < caseClause.types.length; j++) {
                if (j > 0) {
                    dumper.display(" | ");
                }
                dumper.display(caseClause.types[j]);
            }
            dumper.display(" return ");
            dumper.display(caseClause.returnClause).nl();
        }
        dumper.display("default ");
        if (defaultClause.variable != null) {
            dumper.display('$');
            dumper.display(defaultClause.variable.getStringValue());
            dumper.display(' ');
        }
        defaultClause.returnClause.dump(dumper);
        dumper.endIndent();
    }

    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        
        operand.resetState(postOptimization);
        defaultClause.returnClause.resetState(postOptimization);
        for (int i = 0; i < cases.size(); i++) {
            final Case caseClause = (Case) cases.get(i);
            caseClause.returnClause.resetState(postOptimization);
        }
    }
}