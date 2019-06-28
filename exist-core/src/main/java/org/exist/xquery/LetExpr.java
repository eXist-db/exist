/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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

import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * Implements an XQuery let-expression.
 * 
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class LetExpr extends BindingExpression {

    public LetExpr(XQueryContext context) {
        super(context);
    }

    @Override
    public ClauseType getType() {
        return ClauseType.LET;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        //Save the local variable stack
        final LocalVariable mark = context.markLocalVariables(false);
        try {
            contextInfo.setParent(this);
            final AnalyzeContextInfo varContextInfo = new AnalyzeContextInfo(contextInfo);
            inputSequence.analyze(varContextInfo);
            //Declare the iteration variable
            final LocalVariable inVar = new LocalVariable(QName.parse(context, varName, null));
            inVar.setSequenceType(sequenceType);
            inVar.setStaticType(varContextInfo.getStaticReturnType());
            context.declareVariableBinding(inVar);
            //Reset the context position
            context.setContextSequencePosition(0, null);

            returnExpr.analyze(contextInfo);
        } catch (final QName.IllegalQNameException e) {
            throw new XPathException(ErrorCodes.XPST0081, "No namespace defined for prefix " + varName);
        } finally {
            // restore the local variable stack
            context.popLocalVariables(mark);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval(Sequence contextSequence, Item contextItem)
            throws XPathException {
        if (context.getProfiler().isEnabled()){
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                    "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                    "CONTEXT ITEM", contextItem.toSequence());}
        }
        context.expressionStart(this);
        context.pushDocumentContext();
        try {
            //Save the local variable stack
            LocalVariable mark = context.markLocalVariables(false);
            Sequence in;
            LocalVariable var;
            Sequence resultSequence = null;
            try {
                // evaluate input sequence
                in = inputSequence.eval(contextSequence, null);
                clearContext(getExpressionId(), in);
                // Declare the iteration variable
                var = createVariable(varName);
                var.setSequenceType(sequenceType);
                context.declareVariableBinding(var);
                var.setValue(in);
                if (sequenceType == null)
                    {var.checkType();} //Just because it makes conversions !                	
                var.setContextDocs(inputSequence.getContextDocSet());
                registerUpdateListener(in);

                resultSequence = returnExpr.eval(contextSequence, null);

                if (sequenceType != null) {
                    int actualCardinality;
                    if (var.getValue().isEmpty()) {actualCardinality = Cardinality.EMPTY;}
                    else if (var.getValue().hasMany()) {actualCardinality = Cardinality.MANY;}
                    else {actualCardinality = Cardinality.ONE;}
                    //Type.EMPTY is *not* a subtype of other types ; checking cardinality first
                    if (!Cardinality.checkCardinality(sequenceType.getCardinality(), actualCardinality))
                        {throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Invalid cardinality for variable $" + varName +
                            ". Expected " +
                            Cardinality.getDescription(sequenceType.getCardinality()) +
                            ", got " + Cardinality.getDescription(actualCardinality), in);}
                    //TODO : ignore nodes right now ; they are returned as xs:untypedAtomicType
                    if (!Type.subTypeOf(sequenceType.getPrimaryType(), Type.NODE)) {
                        if (!var.getValue().isEmpty() && !Type.subTypeOf(var.getValue()
                                .getItemType(), sequenceType.getPrimaryType())) {
                            throw new XPathException(this, ErrorCodes.XPTY0004,
                                "Invalid type for variable $" + varName +
                                ". Expected " + Type.getTypeName(sequenceType.getPrimaryType()) +
                                ", got " +Type.getTypeName(var.getValue().getItemType()), in);
                        }
                    //Here is an attempt to process the nodes correctly
                    } else {
                        //Same as above : we probably may factorize 
                        if (!var.getValue().isEmpty() && !Type.subTypeOf(var.getValue().getItemType(), sequenceType.getPrimaryType()))
                            {throw new XPathException(this, ErrorCodes.XPTY0004,
                                "Invalid type for variable $" + varName + ". Expected " +
                                Type.getTypeName(sequenceType.getPrimaryType()) + ", got " +
                                Type.getTypeName(var.getValue().getItemType()), in);}
                    }
                }
            } finally {
                // Restore the local variable stack
                context.popLocalVariables(mark, resultSequence);
            }
            clearContext(getExpressionId(), in);
            if (context.getProfiler().isEnabled())
                {context.getProfiler().end(this, "", resultSequence);}
            if (resultSequence == null)
                {return Sequence.EMPTY_SEQUENCE;}
            if (!(resultSequence instanceof DeferredFunctionCall)) {
                setActualReturnType(resultSequence.getItemType());
            }
            if (getPreviousClause() == null) {
                resultSequence = postEval(resultSequence);
            }
            return resultSequence;
        } finally {
            context.popDocumentContext();
            context.expressionEnd(this);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("let ", line);
        dumper.startIndent();
        dumper.display("$").display(varName);
        dumper.display(" := ");
        inputSequence.dump(dumper);
        dumper.endIndent();
        //TODO : QuantifiedExpr
        if (returnExpr instanceof LetExpr)
            {dumper.display(", ");}
        else
            {dumper.nl().display("return ");}
        dumper.startIndent();
        returnExpr.dump(dumper);
        dumper.endIndent();
    }

    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("let ");
        result.append("$").append(varName);
        result.append(" := ");
        result.append(inputSequence.toString());
        result.append(" ");
        //TODO : QuantifiedExpr
        if (returnExpr instanceof LetExpr)
            {result.append(", ");}
        else
            {result.append("return ");}
        result.append(returnExpr.toString());
        return result.toString();
    }

    public void accept(ExpressionVisitor visitor) {
        visitor.visitLetExpression(this);
    }

    @Override
    public boolean allowMixedNodesInReturn() {
        return true;
    }
}