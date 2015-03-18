/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
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
 *  $Id$
 */
package org.exist.xquery;

import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.VirtualNodeSet;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.xmldb.api.base.CompiledExpression;

/**
 * PathExpr is just a sequence of XQuery/XPath expressions, which will be called
 * step by step.
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 * @author perig
 * @author ljo
 */
public class PathExpr extends AbstractExpression implements CompiledXQuery,
        CompiledExpression, RewritableExpression {

    protected final static Logger LOG = LogManager.getLogger(PathExpr.class);

    protected boolean keepVirtual = false;

    protected List<Expression> steps = new ArrayList<Expression>();

    protected boolean staticContext = false;

    protected boolean inPredicate = false;

    protected Expression parent;

    public PathExpr(XQueryContext context) {
        super(context);
    }

    /**
     * Add an arbitrary expression to this object's list of child-expressions.
     * 
     * @param s
     */
    public void add(Expression s) {
        steps.add(s);
    }

    /**
     * Add all the child-expressions from another PathExpr to this object's
     * child-expressions.
     * 
     * @param path
     */
    public void add(PathExpr path) {
        Expression expr;
        for (final Iterator<Expression> i = path.steps.iterator(); i.hasNext();) {
            expr = (Expression) i.next();
            add(expr);
        }
    }

    /**
     * Add another PathExpr to this object's expression list.
     * 
     * @param path
     */
    public void addPath(PathExpr path) {
        steps.add(path);
    }

    /**
     * Add a predicate expression to the list of expressions. The predicate is
     * added to the last expression in the list.
     * 
     * @param pred
     */
    public void addPredicate(Predicate pred) {
        final Expression e = (Expression) steps.get(steps.size() - 1);
        if (e instanceof Step) {((Step) e).addPredicate(pred);}
    }

    /* RewritableExpression API */
    
    /**
     * Replace the given expression by a new expression.
     *
     * @param oldExpr the old expression
     * @param newExpr the new expression to replace the old
     */
    public void replace(Expression oldExpr, Expression newExpr) {
        final int idx = steps.indexOf(oldExpr);
        if (idx < 0) {
            LOG.warn("Expression not found: " + ExpressionDumper.dump(oldExpr) + "; in: " + ExpressionDumper.dump(this));
            return;
        }
        steps.set(idx, newExpr);
    }

    @Override
    public Expression getPrevious(Expression current) {
        final int idx = steps.indexOf(current);
        if (idx > 1)
            {return steps.get(idx - 1);}
        return null;
    }

    @Override
    public Expression getFirst() {
        return steps.get(0);
    }

    @Override
    public void remove(Expression oldExpr) {
        final int idx = steps.indexOf(oldExpr);
        if (idx < 0) {
            LOG.warn("Expression to remove not found: " + ExpressionDumper.dump(oldExpr) + "; in: " + ExpressionDumper.dump(this));
            return;
        }
        steps.remove(idx);
    }

    /* END RewritableExpression API */

    public Expression getParent() {
        return this.parent;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.Expression)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        this.parent = contextInfo.getParent();
        inPredicate = (contextInfo.getFlags() & IN_PREDICATE) > 0;
        unordered = (contextInfo.getFlags() & UNORDERED) > 0;
        contextId = contextInfo.getContextId();
        for (int i = 0; i < steps.size(); i++) {
            // if this is a sequence of steps, the IN_PREDICATE flag
            // is only passed to the first step, so it has to be removed
            // for subsequent steps
            final Expression expr = steps.get(i);
            if ((contextInfo.getFlags() & IN_PREDICATE) > 0 ) {
                if (i == 1) {
                    //take care : predicates in predicates are not marked as such ! -pb
                    contextInfo.setFlags(contextInfo.getFlags() & (~IN_PREDICATE));
                    //Where clauses should be identified. TODO : pass bound variable's inputSequence ? -pb
                    if ((contextInfo.getFlags() & IN_WHERE_CLAUSE) == 0)
                        {contextInfo.setContextId(Expression.NO_CONTEXT_ID);}
                }
            }
            if (i > 1)
                {contextInfo.setContextStep((Expression) steps.get(i - 1));}
            contextInfo.setParent(this);
            expr.analyze(contextInfo);
        }
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
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
        }
        if (contextItem != null)
            {contextSequence = contextItem.toSequence();}
        Sequence result = null;
        if (steps.size() == 0) {
            result = Sequence.EMPTY_SEQUENCE;
        } else {
            //we will filter out nodes from the contextSequence
            result = contextSequence;
            Sequence currentContext = contextSequence;
            DocumentSet contextDocs = null;
            Expression expr = steps.get(0);
            if (expr instanceof VariableReference) {
                final Variable var = ((VariableReference) expr).getVariable();
                //TOUNDERSTAND : how null could be possible here ? -pb
                if (var != null) 
                    {contextDocs = var.getContextDocs();}
            }
            //contextDocs == null *is* significant
            setContextDocSet(contextDocs);
            //To prevent processing nodes after atomic values...
            //TODO : let the parser do it ? -pb
            boolean gotAtomicResult = false;
            for (final Iterator<Expression> iter = steps.iterator(); iter.hasNext();) {
                expr = iter.next();
                context.getWatchDog().proceed(expr);
                //TODO : maybe this could be detected by the parser ? -pb
                if (gotAtomicResult && !Type.subTypeOf(expr.returnsType(), Type.NODE)
                        //Ugly workaround to allow preceding *text* nodes.
                        && !(expr instanceof EnclosedExpr)) {
                    throw new XPathException(this, ErrorCodes.XPTY0019,
                        "left operand of '/' must be a node. Got '" + 
                        Type.getTypeName(result.getItemType()) +
                        Cardinality.toString(result.getCardinality()) + "'");
                }
                //contextDocs == null *is* significant
                expr.setContextDocSet(contextDocs);
                // switch into single step mode if we are processing in-memory nodes only
                final boolean inMemProcessing = currentContext != null &&
                        Type.subTypeOf(currentContext.getItemType(), Type.NODE) &&
                        !currentContext.isPersistentSet();
                //DESIGN : first test the dependency then the result
                final int exprDeps = expr.getDependencies();
                if (inMemProcessing ||
                        ((Dependency.dependsOn(exprDeps, Dependency.CONTEXT_ITEM) ||
                        Dependency.dependsOn(exprDeps, Dependency.CONTEXT_POSITION)) &&
                        //A positional predicate will be evaluated one time
                        //TODO : reconsider since that may be expensive (type evaluation)
                        !(this instanceof Predicate && Type.subTypeOf(this.returnsType(), Type.NUMBER)) &&
                        currentContext != null && !currentContext.isEmpty())) {
                    Sequence exprResult = new ValueSequence(Type.subTypeOf(expr.returnsType(), Type.NODE));
                    ((ValueSequence)exprResult).keepUnOrdered(unordered);
                    //Restore a position which may have been modified by inner expressions 
                    int p = context.getContextPosition();
                    final Sequence seq = context.getContextSequence();
                    for (final SequenceIterator iterInner = currentContext.iterate(); iterInner.hasNext(); p++) {
                        context.setContextSequencePosition(p, seq);
                        context.getWatchDog().proceed(expr);
                        final Item current = iterInner.nextItem();
                        //0 or 1 item
                        if (!currentContext.hasMany())
                            {exprResult = expr.eval(currentContext, current);}
                        else {
                            exprResult.addAll(expr.eval(currentContext, current));
                        }
                    }
                    result = exprResult;
                } else {
                    result = expr.eval(currentContext);
                }
                //TOUNDERSTAND : why did I have to write this test :-) ? -pb
                //it looks like an empty sequence could be considered as a sub-type of Type.NODE
                //well, no so stupid I think...    
                if (steps.size() > 1 && !(result instanceof VirtualNodeSet) &&
                        !(expr instanceof EnclosedExpr) && !result.isEmpty() &&
                        !Type.subTypeOf(result.getItemType(), Type.NODE))
                    {gotAtomicResult = true;}
                if(steps.size() > 1 && getLastExpression() instanceof Step)
                    // remove duplicate nodes if this is a path 
                    // expression with more than one step
                    {result.removeDuplicates();}
                if (!staticContext)
                    {currentContext = result;}
            }
            if (gotAtomicResult && !expr.allowMixedNodesInReturn() &&
                    !Type.subTypeOf(result.getItemType(), Type.ATOMIC)) {
                throw new XPathException(this, ErrorCodes.XPTY0018,
                    "Cannot mix nodes and atomic values in the result of a path expression.");
            }
        }
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);}
        return result;
    }

    public XQueryContext getContext() {
        return context;
    }

    public DocumentSet getDocumentSet() {
        return null;
    }

    //TODO: @Deprecated //use getSubExpression
    public Expression getExpression(int pos) {
        return steps.get(pos);
    }

    public Expression getSubExpression(int pos) {
        return steps.get(pos);
    }

    public Expression getLastExpression() {
        if (steps.size() == 0) 
            {return null;}
        return steps.get(steps.size() - 1);
    }

    //TODO: @Deprecated //use getSubExpressionCount
    public int getLength() {
        return steps.size();
    }

    public int getSubExpressionCount() {
    	return steps.size();
    }

    @Override
    public boolean allowMixedNodesInReturn() {
        if (steps.size() == 1)
            {return steps.get(0).allowMixedNodesInReturn();}
        return super.allowMixedNodesInReturn();
    }

    public void setUseStaticContext(boolean staticContext) {
        this.staticContext = staticContext;
    }

    public void accept(ExpressionVisitor visitor) {
    	visitor.visitPathExpr(this);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        Expression next = null;
        int count = 0;
        for (final Iterator<Expression> iter = steps.iterator(); iter.hasNext(); count++) {
            next = iter.next(); 
            //Open a first parenthesis
            if (next instanceof LogicalOp)
                {dumper.display('(');}
            if (count > 0) {
                if (next instanceof Step)
                    {dumper.display("/");}
                else
                    {dumper.nl();}
            }
            next.dump(dumper);
        }
        //Close the last parenthesis
        if (next instanceof LogicalOp)
            {dumper.display(')');}
    }

    public String toString() { 
        final StringBuilder result = new StringBuilder();
        Expression next = null;
        if (steps.size() == 0)
            {result.append("()");}
        else {
            int count = 0;
            for (final Iterator<Expression> iter = steps.iterator(); iter.hasNext(); count++) {
                next = iter.next(); 
                // Open a first parenthesis
                if (next instanceof LogicalOp)
                    {result.append('(');}
                if (count > 0) {
                    if (next instanceof Step)
                        {result.append("/");}
                    else
                        {result.append(' ');}
                }
                result.append(next.toString());
            }
            // Close the last parenthesis
            if (next instanceof LogicalOp)
                {result.append(')');}
        }
        return result.toString();
    }

    public int returnsType() {
        if (steps.size() == 0) 
        	//Not so simple. ITEM should be re-tuned in some circumstances that have to be determined
            {return Type.NODE;}
        return steps.get(steps.size() - 1).returnsType();
    }

    public int getCardinality() {
        if (steps.size() == 0) {return Cardinality.ZERO;}
        return ((Expression) steps.get(steps.size() -1)).getCardinality();
    }
 
    /*
     * (non-Javadoc)
     * 
     * @see org.exist.xquery.AbstractExpression#getDependencies()
     */
    public int getDependencies() {
        Expression next;
        int deps = 0;
        for (final Iterator<Expression> i = steps.iterator(); i.hasNext();) {
            next = i.next();
            deps = deps | next.getDependencies();
        }
        return deps;
    }

    public void replaceLastExpression(Expression s) {
        if (steps.size() == 0)
            {return;}
        steps.set(steps.size() - 1, s);
    }

    public String getLiteralValue() {
        if (steps.size() == 0) 
            {return "";}
        final Expression next = steps.get(0);
        if (next instanceof LiteralValue) {
            try {
                return ((LiteralValue) next).getValue().getStringValue();
            } catch (final XPathException e) {
                //TODO : is there anything to do here ?
            }
        }
        if (next instanceof PathExpr)
            {return ((PathExpr)next).getLiteralValue();}
        return "";
    }

    public int getLine() {
        if (line < 0 && steps.size() > 0)
            {return steps.get(0).getLine();}
        return line;
    }

    public int getColumn() {
        if (column < 0 && steps.size() > 0)
            {return steps.get(0).getColumn();}
        return column;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.xquery.AbstractExpression#setPrimaryAxis(int)
     */
    public void setPrimaryAxis(int axis) {
        if (steps.size() > 0) 
            {steps.get(0).setPrimaryAxis(axis);}
    }

    public int getPrimaryAxis() {
        if (steps.size() > 0)
            {return steps.get(0).getPrimaryAxis();}
        return Constants.UNKNOWN_AXIS;
    }

    /*
    * (non-Javadoc)
    *
    * @see org.exist.xquery.AbstractExpression#resetState()
    */
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        for (int i = 0; i < steps.size(); i++) {
            steps.get(i).resetState(postOptimization);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.xmldb.CompiledExpression#reset()
     */
    public void reset() {
        resetState(false);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.CompiledXQuery#isValid()
     */
    public boolean isValid() {
        return context.checkModulesValid();
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.CompiledXQuery#dump(java.io.Writer)
     */
    public void dump(Writer writer) {
        final ExpressionDumper dumper = new ExpressionDumper(writer);
        dump(dumper);
    }

    public void setContext(XQueryContext context) {
        this.context = context;
    }

    public Expression simplify() {
        if (steps.size() == 1) {
            return steps.get(0).simplify();
        }
        return this;
    }
}