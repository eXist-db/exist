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

import java.util.Iterator;

import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.GroupedValueSequence;
import org.exist.xquery.value.GroupedValueSequenceTable;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.OrderedValueSequence;
import org.exist.xquery.value.PreorderedValueSequence;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Implements an XQuery let-expression.
 * 
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 */
public class LetExpr extends BindingExpression {

    public LetExpr(XQueryContext context) {
        super(context);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.BindingExpression#analyze(org.exist.xquery.Expression, int, org.exist.xquery.OrderSpec[])
     */
    public void analyze(AnalyzeContextInfo contextInfo, OrderSpec orderBy[], GroupSpec groupBy[]) throws XPathException {
        // bv : Declare grouping key variable(s) 
        if (groupBy != null) {
            for (int i = 0 ; i < groupBy.length ; i++) {
                final LocalVariable groupKeyVar = new LocalVariable(QName.parse(context, groupBy[i].getKeyVarName(),null));
                groupKeyVar.setSequenceType(sequenceType);
                context.declareVariableBinding(groupKeyVar);
            }
        }
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
            if (whereExpr != null) {
                final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
                newContextInfo.setFlags(contextInfo.getFlags() | IN_PREDICATE | IN_WHERE_CLAUSE);
                whereExpr.analyze(newContextInfo);
            }
            //Reset the context position
            context.setContextSequencePosition(0, null);
            if (returnExpr instanceof BindingExpression) {
                ((BindingExpression)returnExpr).analyze(contextInfo, orderBy,groupBy);
            } else {
                if (orderBy != null) {
                    for (int i = 0; i < orderBy.length; i++) {
                        orderBy[i].analyze(contextInfo);
                    }
                }
                if (groupBy != null) { 
                    for (int i = 0; i < groupBy.length; i++) {
                        groupBy[i].analyze(contextInfo); 
                    }
                }
                returnExpr.analyze(contextInfo);
            }
        } finally {
            // restore the local variable stack
            context.popLocalVariables(mark);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval(Sequence contextSequence, Item contextItem,
            Sequence resultSequence, GroupedValueSequenceTable groupedSequence) 
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
            if (resultSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                    "RESULT SEQUENCE", resultSequence);}
        }
        context.expressionStart(this);
        context.pushDocumentContext();
        try {
            //bv : Declare grouping variables and initiate grouped sequence
            LocalVariable groupKeyVar[] = null;
            if (groupSpecs != null) {
                groupedSequence = new GroupedValueSequenceTable(groupSpecs, varName, context);
                groupKeyVar = new LocalVariable[groupSpecs.length];
                for (int i = 0 ; i < groupSpecs.length ; i++) {
                    groupKeyVar[i] = new LocalVariable(QName.parse(context,
                        groupSpecs[i].getKeyVarName(),null));
                    groupKeyVar[i].setSequenceType(sequenceType);
                    context.declareVariableBinding(groupKeyVar[i]);
                }
            }
            //Save the local variable stack
            LocalVariable mark = context.markLocalVariables(false);
            Sequence in;
            boolean fastOrderBy;
            LocalVariable var;
            try {
                // evaluate input sequence
                in = inputSequence.eval(contextSequence, null);
                clearContext(getExpressionId(), in);
                // Declare the iteration variable
                var = new LocalVariable(QName.parse(context, varName, null));
                var.setSequenceType(sequenceType);
                context.declareVariableBinding(var);
                var.setValue(in);
                if (sequenceType == null)
                    {var.checkType();} //Just because it makes conversions !                	
                var.setContextDocs(inputSequence.getContextDocSet());
                registerUpdateListener(in);
                if (whereExpr != null) {
                    final Sequence filtered = applyWhereExpression(null);
                    // TODO: don't use returnsType here
                    if (filtered.isEmpty()) {
                        if (context.getProfiler().isEnabled())
                            {context.getProfiler().end(this, "", Sequence.EMPTY_SEQUENCE);}
                        return Sequence.EMPTY_SEQUENCE;
                    } else if (filtered.getItemType() == Type.BOOLEAN &&
                               !filtered.effectiveBooleanValue()) {
                        if (context.getProfiler().isEnabled())
                            {context.getProfiler().end(this, "", Sequence.EMPTY_SEQUENCE);}
                        return Sequence.EMPTY_SEQUENCE;
                    }
                }
                //Check if we can speed up the processing of the "order by" clause.
                fastOrderBy = !(in instanceof DeferredFunctionCall) && in.isPersistentSet() && checkOrderSpecs(in);
                //PreorderedValueSequence applies the order specs to all items
                //in one single processing step
                if (fastOrderBy) {
                    in = new PreorderedValueSequence(orderSpecs, in.toNodeSet(), getExpressionId());
                }
                //Otherwise, if there's an order by clause, wrap the result into
                //an OrderedValueSequence. OrderedValueSequence will compute
                //order expressions for every item when it is added to the result sequence.
                if (resultSequence == null) {
                    if(orderSpecs != null && !fastOrderBy)
                        {resultSequence = new OrderedValueSequence(orderSpecs, in.getItemCount());}
                }
                if (groupedSequence==null){
                    if (returnExpr instanceof BindingExpression) {
                      if (resultSequence == null) {
                          resultSequence = new ValueSequence();
                          ((ValueSequence)resultSequence).keepUnOrdered(unordered);
                      }
                      ((BindingExpression)returnExpr).eval(contextSequence, null, resultSequence,null);
                    } else {
                        in = returnExpr.eval(contextSequence);
                        if (resultSequence == null)
                            {resultSequence = in;}
                        else
                            {resultSequence.addAll(in);}
                    }
                }
                else{
                    /* bv : special processing for groupby :
                    if returnExpr is a Binding expression, pass the groupedSequence.
                    Else, add item to groupedSequence and don't evaluate here !
                    */
                    if (returnExpr instanceof BindingExpression) {
                        if (resultSequence == null) {
                            resultSequence = new ValueSequence();
                            ((ValueSequence)resultSequence).keepUnOrdered(unordered);
                        }
                        ((BindingExpression)returnExpr).eval(contextSequence, null, resultSequence, groupedSequence);
                    } else{
                      final Sequence toGroupSequence = context.resolveVariable(groupedSequence.getToGroupVarName()).getValue();
                      groupedSequence.addAll(toGroupSequence);
                    }
                }
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
            //Special processing for groupBy : one return per group in groupedSequence 
            if (groupSpecs!=null) {
                mark = context.markLocalVariables(false);
                context.declareVariableBinding(var);
                for (final Iterator<String> it = groupedSequence.iterate(); it.hasNext();){ 
                    final GroupedValueSequence currentGroup = groupedSequence.get(it.next()); 
                    context.proceed(this);
                    // set binding variable to current group
                    var.setValue(currentGroup);
                    var.checkType();
                    // Set value of grouping keys for the current group
                    for (int i=0 ; i< groupKeyVar.length ; i ++) {
                        groupKeyVar[i].setValue(currentGroup.getGroupKey().itemAt(i).toSequence());
                    }
                    //Evaluate real return expression
                    final Sequence val = groupReturnExpr.eval(null); 
                    resultSequence.addAll(val);
                }
                context.popLocalVariables(mark);
           }
           if (orderSpecs != null && !fastOrderBy)
                {((OrderedValueSequence)resultSequence).sort();}
            clearContext(getExpressionId(), in);
            if (context.getProfiler().isEnabled())
                {context.getProfiler().end(this, "", resultSequence);}
            if (resultSequence == null)
                {return Sequence.EMPTY_SEQUENCE;}
            if (!(resultSequence instanceof DeferredFunctionCall))
                {actualReturnType = resultSequence.getItemType();}
            return resultSequence;
        } finally {
            context.popDocumentContext();
            context.expressionEnd(this);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#returnsType()
     */
    public int returnsType() {
        //TODO: let must return "return expression type"
        if (sequenceType != null)
            {return sequenceType.getPrimaryType();}
        //Type.ITEM by default : this may change *after* evaluation
        return actualReturnType;
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
        if (whereExpr != null) {
            dumper.nl().display("where ");
            whereExpr.dump(dumper);
        }
        if (groupSpecs != null) { 
            dumper.display("group "); 
            dumper.display("$").display(toGroupVarName);
            dumper.display(" as "); 
            dumper.display("$").display(groupVarName);
            dumper.display(" by "); 
            for (int i = 0; i < groupSpecs.length; i++) {
                if (i > 0) 
                    {dumper.display(", ");}
                dumper.display(groupSpecs[i].getGroupExpression().toString());
                dumper.display(" as ");
                dumper.display("$").display(groupSpecs[i].getKeyVarName());
            }
            dumper.nl();
        }
        if (orderSpecs != null) {
            dumper.nl().display("order by ");
            for(int i = 0; i < orderSpecs.length; i++) {
                if(i > 0)
                    {dumper.display(", ");}
                //TODO : toString() or... dump ?
                dumper.display(orderSpecs[i].toString());
            }
        }
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
        if (whereExpr != null) {
            result.append(" where ");
            result.append(whereExpr.toString());
        } 
        if (groupSpecs != null) {
            result.append("group ");
            result.append("$").append(toGroupVarName);
            result.append(" as ");
            result.append("$").append(groupVarName);
            result.append(" by ");
            for (int i = 0; i < groupSpecs.length; i++) {
                if (i > 0)
                    {result.append(", ");}
                result.append(groupSpecs[i].getGroupExpression().toString());
                result.append(" as ");
                result.append("$").append(groupSpecs[i].getKeyVarName());
            }
            result.append(" ");
        }
        if (orderSpecs != null) {
            result.append(" order by ");
            for (int i = 0 ; i < orderSpecs.length ; i++) {
                if (i > 0)
                    {result.append(", ");}
                result.append(orderSpecs[i].toString());
            }
        }
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