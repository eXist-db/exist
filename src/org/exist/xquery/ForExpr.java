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

import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.GroupedValueSequence;
import org.exist.xquery.value.GroupedValueSequenceTable;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.OrderedValueSequence;
import org.exist.xquery.value.PreorderedValueSequence;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import java.util.Iterator;

/**
 * Represents an XQuery "for" expression.
 * 
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 */
public class ForExpr extends BindingExpression {

    private String positionalVariable = null;

    public ForExpr(XQueryContext context) {
        super(context);
    }

    /**
     * A "for" expression may have an optional positional variable whose
     * QName can be set via this method.
     * 
     * @param var
     */
    public void setPositionalVariable(String var) {
        positionalVariable = var;
    }

    public void analyze(AnalyzeContextInfo contextInfo, OrderSpec orderBy[]) throws XPathException { 
        analyze(contextInfo, orderBy, null); 
    } 

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.Expression)
     */
    public void analyze(AnalyzeContextInfo contextInfo, OrderSpec orderBy[],
            GroupSpec groupBy[]) throws XPathException { 
//        // bv : Declare the grouping variable
//        if(groupVarName != null){
//            LocalVariable groupVar = new LocalVariable(QName.parse(context, groupVarName, null));
//            groupVar.setSequenceType(sequenceType);
//            context.declareVariableBinding(groupVar);
//        }
        // bv : Declare grouping key variable(s) 
//        if (groupBy!= null){
//            for (int i=0;i<groupBy.length;i++){
//                LocalVariable groupKeyVar = new LocalVariable(QName.parse(context,
//                    groupBy[i].getKeyVarName(),null));
//                groupKeyVar.setSequenceType(sequenceType);
//                context.declareVariableBinding(groupKeyVar);
//            }
//        }
        // Save the local variable stack
        final LocalVariable mark = context.markLocalVariables(false);
        if (groupSpecs != null) {
            for (final GroupSpec spec : groupSpecs) {
                final LocalVariable groupKeyVar = new LocalVariable(QName.parse(context, spec.getKeyVarName()));
                groupKeyVar.setSequenceType(sequenceType);
                context.declareVariableBinding(groupKeyVar);
            }
        }
        try {
            contextInfo.setParent(this);
            final AnalyzeContextInfo varContextInfo = new AnalyzeContextInfo(contextInfo);
            inputSequence.analyze(varContextInfo);
            // Declare the iteration variable
            final LocalVariable inVar = new LocalVariable(QName.parse(context, varName, null));
            inVar.setSequenceType(sequenceType);
            inVar.setStaticType(varContextInfo.getStaticReturnType());
            context.declareVariableBinding(inVar);
            // Declare positional variable
            if (positionalVariable != null) {
                //could probably be detected by the parser
                if (varName.equals(positionalVariable))
                    {throw new XPathException(this, ErrorCodes.XQST0089,
                        "bound variable and positional variable have the same name");}
                final LocalVariable posVar = new LocalVariable(QName.parse(context, positionalVariable, null));
                posVar.setSequenceType(POSITIONAL_VAR_TYPE);
                posVar.setStaticType(Type.INTEGER);
                context.declareVariableBinding(posVar);
            }
            if (whereExpr != null) {
                final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
                newContextInfo.setFlags(contextInfo.getFlags() | IN_PREDICATE | IN_WHERE_CLAUSE);
                newContextInfo.setContextId(getExpressionId());
                whereExpr.analyze(newContextInfo);
            }
            // the order by specs should be analyzed by the last binding expression
            // in the chain to have access to all variables. So if the return expression
            // is another binding expression, we just forward the order specs.
            if (returnExpr instanceof BindingExpression) {
                final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
                newContextInfo.addFlag(SINGLE_STEP_EXECUTION);
                ((BindingExpression)returnExpr).analyze(newContextInfo, orderBy, groupBy); 
            } else {
                final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
                newContextInfo.addFlag(SINGLE_STEP_EXECUTION);
                //analyze the order specs and the group specs 
                if (orderBy != null) {
                    for(int i = 0; i < orderBy.length; i++)
                        orderBy[i].analyze(newContextInfo);
                }
                if (groupSpecs != null) {
                    for (final GroupSpec spec : groupSpecs) {
                        spec.analyze(newContextInfo);
                    }
                }
                returnExpr.analyze(newContextInfo);
            }
        } finally {
            // restore the local variable stack
            context.popLocalVariables(mark);
        }
    }

    /**
     * This implementation tries to process the "where" clause in advance, i.e. in one single
     * step. This is possible if the input sequence is a node set and the where expression
     * has no dependencies on other variables than those declared in this "for" statement.
     * 
     * @see org.exist.xquery.Expression#eval(Sequence, Item)
     */
    public Sequence eval(Sequence contextSequence, Item contextItem,
            Sequence resultSequence, GroupedValueSequenceTable groupedSequence) 
            throws XPathException {
        if (context.getProfiler().isEnabled()) {
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
        // bv - Declare grouping variables and initiate grouped sequence
        LocalVariable groupKeyVar[] = null; 
        if (groupSpecs != null){ 
            groupedSequence = new GroupedValueSequenceTable(groupSpecs, varName, context);
            groupKeyVar = new LocalVariable[groupSpecs.length]; 
            for (int i=0 ; i<groupSpecs.length ; i++){ 
                groupKeyVar[i] = new LocalVariable(QName.parse(context,
                    groupSpecs[i].getKeyVarName(),null)); 
                groupKeyVar[i].setSequenceType(sequenceType); 
                context.declareVariableBinding(groupKeyVar[i]); 
            }
        }
        // Check if we can speed up the processing of the "order by" clause.
        boolean fastOrderBy = false;
        LocalVariable var;
        Sequence in;
        // Save the local variable stack
        LocalVariable mark = context.markLocalVariables(false);
        try {
            // Evaluate the "in" expression
            in = inputSequence.eval(contextSequence, null);
            clearContext(getExpressionId(), in);
            // Declare the iteration variable
            var = new LocalVariable(QName.parse(context, varName, null));
            var.setSequenceType(sequenceType);
            context.declareVariableBinding(var);
            registerUpdateListener(in);
            // Declare positional variable
            LocalVariable at = null;
            if (positionalVariable != null) {
                at = new LocalVariable(QName.parse(context, positionalVariable, null));
                at.setSequenceType(POSITIONAL_VAR_TYPE);
                context.declareVariableBinding(at);
            }
            // Assign the whole input sequence to the bound variable.
            // This is required if we process the "where" or "order by" clause
            // in one step.
            var.setValue(in);
            // Save the current context document set to the variable as a hint
            // for path expressions occurring in the "return" clause.
            if (in instanceof NodeSet) {
                var.setContextDocs(in.getDocumentSet());
            } else {
                var.setContextDocs(null);
            }
            // See if we can process the "where" clause in a single step (instead of
            // calling the where expression for each item in the input sequence)
            // This is possible if the input sequence is a node set and has no
            // dependencies on the current context item.
            boolean fastExec = 
                whereExpr != null && at == null &&
                !Dependency.dependsOn(whereExpr, Dependency.CONTEXT_ITEM) &&
                in.isPersistentSet() &&
                Type.subTypeOf(in.getItemType(), Type.NODE);
            // If possible, apply the where expression ahead of the iteration
            if (fastExec) {
                if (!in.isCached()) {
                    setContext(getExpressionId(), in);
                    if (whereExpr != null)
                        {whereExpr.setContextId(getExpressionId());}
                }
                in = applyWhereExpression(in);
                if (!in.isCached())
                    {clearContext(getExpressionId(), in);}
            }
            // PreorderedValueSequence applies the order specs to all items
            // in one single processing step
            if (fastOrderBy) {
                in = new PreorderedValueSequence(orderSpecs, in, getExpressionId());
            }
            // Otherwise, if there's an order by clause, wrap the result into
            // an OrderedValueSequence. OrderedValueSequence will compute
            // order expressions for every item when it is added to the result sequence.
            if (resultSequence == null) {
                if (orderSpecs != null && !fastOrderBy) {
                    resultSequence = new OrderedValueSequence(orderSpecs, in.getItemCount());
                } else {
                    resultSequence = new ValueSequence();
                    ((ValueSequence)resultSequence).keepUnOrdered(unordered);
                }
            }
            Sequence val = null;
            int p = 1;
            final IntegerValue atVal = new IntegerValue(1);
            if(positionalVariable != null)
                {at.setValue(atVal);}
            //Type.EMPTY is *not* a subtype of other types ;
            //the tests below would fail without this prior cardinality check
            if (in.isEmpty() && sequenceType != null &&
                    !Cardinality.checkCardinality(sequenceType.getCardinality(),
                    Cardinality.EMPTY)) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Invalid cardinality for variable $" + varName +
                    ". Expected " + Cardinality.getDescription(sequenceType.getCardinality()) + 
                    ", got " + Cardinality.getDescription(in.getCardinality()));
            }
            // Loop through each variable binding
            p = 0;
            for (final SequenceIterator i = in.iterate(); i.hasNext(); p++) {
                context.proceed(this);
                contextItem = i.nextItem();
                context.setContextSequencePosition(p, in);
                if (positionalVariable != null)
                    {at.setValue(new IntegerValue(p + 1));}
                contextSequence = contextItem.toSequence();
                // set variable value to current item
                var.setValue(contextSequence);
                if (sequenceType == null)
                    {var.checkType();} //because it makes some conversions ! 
                val = contextSequence;
                // check optional where clause
                if (whereExpr != null && (!fastExec)) {
                    if (contextItem instanceof NodeProxy)
                        {((NodeProxy)contextItem).addContextNode(getExpressionId(), (NodeProxy)contextItem);}
                    final Sequence bool = applyWhereExpression(null);
                    if (contextItem instanceof NodeProxy)
                        {((NodeProxy)contextItem).clearContext(getExpressionId());}
                    // if where returned false, continue
                    if (!bool.effectiveBooleanValue())
                        {continue;}
                } else {
                    val = contextItem.toSequence();
                }
                //Reset the context position
                context.setContextSequencePosition(0, null);
                if (groupedSequence==null) {
                    if (returnExpr instanceof BindingExpression) {
                        ((BindingExpression)returnExpr).eval(null, null, resultSequence, null);
                    // otherwise call the return expression and add results to resultSequence 
                    } else {
                        val = returnExpr.eval(null);
                        resultSequence.addAll(val);
                    } 
                } else {
                    /* bv : special processing for groupby :
                    if returnExpr is a Binding expression, pass the groupedSequence.  
                    Else, add item to groupedSequence and don't evaluate here !  
                     */
                    if (returnExpr instanceof BindingExpression){ 
                        ((BindingExpression)returnExpr).eval(null, null, resultSequence, groupedSequence);
                    } else {
                        final Sequence toGroupSequence = context.resolveVariable(groupedSequence.getToGroupVarName()).getValue();
                        groupedSequence.addAll(toGroupSequence);
                    }
                }
                // free resources
                var.destroy(context, resultSequence);
            }
        } finally {
            // restore the local variable stack 
            context.popLocalVariables(mark, resultSequence);
        }
        // bv : Special processing for groupBy : one return per group in groupedSequence
        if (groupSpecs!=null) {
            mark = context.markLocalVariables(false);
            context.declareVariableBinding(var);

            // Declare positional variable if required
            LocalVariable at = null;
            if (positionalVariable != null) {
                at = new LocalVariable(QName.parse(context, positionalVariable, null));
                at.setSequenceType(POSITIONAL_VAR_TYPE);
                context.declareVariableBinding(at);
            }
            final IntegerValue atVal = new IntegerValue(1);
            if(positionalVariable != null) {
                at.setValue(atVal);
            }

            int p = 0;
            for (final Iterator<String> it = groupedSequence.iterate(); it.hasNext(); ) {
                final GroupedValueSequence currentGroup = groupedSequence.get(it.next());
                context.proceed(this);
                // set binding variable to current group
                var.setValue(currentGroup);
                var.checkType();
                //set value of grouping keys for the current group 
                for (int i=0; i< groupKeyVar.length ; i ++) {
                    groupKeyVar[i].setValue(currentGroup.getGroupKey().itemAt(i).toSequence());
                }
                if (positionalVariable != null) {
                    final ValueSequence ps = new ValueSequence();
                    for (int i = 0; i < currentGroup.getItemCount(); i++) {
                        ps.add(new IntegerValue(p + i + 1));
                    }
                    at.setValue(ps);
                }
                //evaluate real return expression 
                final Sequence val = groupReturnExpr.eval(null); 
                resultSequence.addAll(val);

                p += currentGroup.getItemCount();
            }
            //Reset the context position
            context.setContextSequencePosition(0, null);
            context.popLocalVariables(mark);
        }
        if (orderSpecs != null && !fastOrderBy)
            {((OrderedValueSequence)resultSequence).sort();}
        clearContext(getExpressionId(), in);
        if (sequenceType != null) {
            //Type.EMPTY is *not* a subtype of other types ; checking cardinality first
            //only a check on empty sequence is accurate here
            if (resultSequence.isEmpty() &&
                    !Cardinality.checkCardinality(sequenceType.getCardinality(),
                    Cardinality.EMPTY))
                {throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Invalid cardinality for variable $" + varName + ". Expected " +
                    Cardinality.getDescription(sequenceType.getCardinality()) +
                    ", got " + Cardinality.getDescription(Cardinality.EMPTY));}
            //TODO : ignore nodes right now ; they are returned as xs:untypedAtomicType
            if (!Type.subTypeOf(sequenceType.getPrimaryType(), Type.NODE)) {
                if (!resultSequence.isEmpty() &&
                        !Type.subTypeOf(resultSequence.getItemType(),
                        sequenceType.getPrimaryType()))
                    {throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Invalid type for variable $" + varName +
                        ". Expected " + Type.getTypeName(sequenceType.getPrimaryType()) +
                        ", got " +Type.getTypeName(resultSequence.getItemType()));}
            //trigger the old behaviour
            } else {
                var.checkType();
            }
        }
        actualReturnType = resultSequence.getItemType();
        context.expressionEnd(this);
        if (context.getProfiler().isEnabled())
            {context.getProfiler().end(this, "", resultSequence);}
        return resultSequence;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#returnsType()
     */
    public int returnsType() {
        if (sequenceType != null)
            {return sequenceType.getPrimaryType();}
        //Type.ITEM by default : this may change *after* evaluation
        return actualReturnType;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("for ", line);
        dumper.startIndent();
        dumper.display("$").display(varName);
        if (positionalVariable != null)
            {dumper.display(" at ").display(positionalVariable);}
        if (sequenceType != null)
            {dumper.display(" as ").display(sequenceType);}
        dumper.display(" in ");
        inputSequence.dump(dumper);
        dumper.endIndent().nl();
        if (whereExpr != null) {
            dumper.display("where", whereExpr.getLine());
            dumper.startIndent();
            whereExpr.dump(dumper);
            dumper.endIndent().nl();
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
            dumper.display("order by ");
            for (int i = 0; i < orderSpecs.length; i++) {
                if (i > 0)
                    {dumper.display(", ");}
                dumper.display(orderSpecs[i]);
            }
            dumper.nl();
        }
        //TODO : QuantifiedExpr
        if (returnExpr instanceof LetExpr)
            {dumper.display(" ", returnExpr.getLine());}
        else
            {dumper.display("return", returnExpr.getLine());} 
        dumper.startIndent();
        returnExpr.dump(dumper);
        dumper.endIndent().nl();
    }

    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("for ");
        result.append("$").append(varName);
        if (positionalVariable != null)
            {result.append(" at ").append(positionalVariable);}
        if (sequenceType != null)
            {result.append(" as ").append(sequenceType);}
        result.append(" in ");
        result.append(inputSequence.toString());
        result.append(" ");
        if (whereExpr != null) {
            result.append("where");
            result.append(" ");
            result.append(whereExpr.toString());
            result.append(" ");
        }
        if (groupSpecs != null) {
            result.append("group ");
            result.append("$").append(toGroupVarName);
            result.append(" as ");
            result.append("$").append(groupVarName);
            result.append(" by ");
            for(int i = 0; i < groupSpecs.length; i++) {
                if (i > 0)
                    {result.append(", ");}
                result.append(groupSpecs[i].getGroupExpression().toString());
                result.append(" as ");
                result.append("$").append(groupSpecs[i].getKeyVarName());
            }
            result.append(" ");
        }
        if (orderSpecs != null) {
            result.append("order by ");
            for (int i = 0; i < orderSpecs.length; i++) {
                if (i > 0)
                    {result.append(", ");}
                result.append(orderSpecs[i].toString());
            }
            result.append(" ");
        }
        //TODO : QuantifiedExpr
        if (returnExpr instanceof LetExpr)
            {result.append(" ");}
        else
            {result.append("return ");}
        result.append(returnExpr.toString());
        return result.toString();
    }

    /* (non-Javadoc)
    * @see org.exist.xquery.AbstractExpression#resetState()
    */
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
    }

    public void accept(ExpressionVisitor visitor) {
        visitor.visitForExpression(this);
    }
}