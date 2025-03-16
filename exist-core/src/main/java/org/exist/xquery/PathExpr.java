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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.VirtualNodeSet;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;
import org.xmldb.api.base.CompiledExpression;

import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * PathExpr is just a sequence of XQuery/XPath expressions, which will be called
 * step by step.
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author perig
 * @author ljo
 */
public class PathExpr extends AbstractExpression implements CompiledXQuery,
        CompiledExpression, RewritableExpression {

    protected final static Logger LOG = LogManager.getLogger(PathExpr.class);

    protected final List<Expression> steps = new ArrayList<>();

    private boolean staticContext = false;

    protected boolean inPredicate = false;

    protected Expression parent;

    public PathExpr(final XQueryContext context) {
        super(context);
    }

    /**
     * Add an arbitrary expression to this object's list of child-expressions.
     *
     * @param expression An expression to add to this path
     */
    public void add(final Expression expression) {
        steps.add(expression);
    }

    /**
     * Add all the child-expressions from another PathExpr to this object's
     * child-expressions.
     *
     * @param path A path to concatenate with this path
     */
    public void add(final PathExpr path) {
        steps.addAll(path.steps);
    }

    /**
     * Add another PathExpr to this object's expression list.
     *
     * @param path A path to add to this path
     */
    public void addPath(final PathExpr path) {
        steps.add(path);
    }

    /**
     * Add a predicate expression to the list of expressions. The predicate is
     * added to the last expression in the list.
     *
     * @param predicate A predicate to add to the path as the last expression
     */
    public void addPredicate(final Predicate predicate) {
        if (!steps.isEmpty()) {
            final Expression e = steps.getLast();
            if (e instanceof Step) {
                ((Step) e).addPredicate(predicate);
            }
        }
    }

    /* RewritableExpression API */

    /**
     * Replace the given expression by a new expression.
     *
     * @param oldExpr the old expression
     * @param newExpr the new expression to replace the old
     */
    @Override
    public void replace(final Expression oldExpr, final Expression newExpr) {
        final int idx = steps.indexOf(oldExpr);
        if (idx < 0) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Expression not found when trying to replace: {}; in: {}", ExpressionDumper.dump(oldExpr), ExpressionDumper.dump(this));
            }
            return;
        }
        steps.set(idx, newExpr);
    }

    @Override
    public Expression getPrevious(final Expression current) {
        final int idx = steps.indexOf(current);
        if (idx > 1) {
            return steps.get(idx - 1);
        }
        return null;
    }

    @Override
    public Expression getFirst() {
        return steps.isEmpty() ? null : steps.getFirst();
    }

    @Override
    public void remove(final Expression oldExpr) {
        final int idx = steps.indexOf(oldExpr);
        if (idx < 0) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Expression not found when trying to remove: {}; in: {}", ExpressionDumper.dump(oldExpr), ExpressionDumper.dump(this));
            }
            return;
        }
        steps.remove(idx);
    }

    /* END RewritableExpression API */

    @Override
    public Expression getParent() {
        return this.parent;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        this.parent = contextInfo.getParent();
        inPredicate = (contextInfo.getFlags() & IN_PREDICATE) > 0;
        unordered = (contextInfo.getFlags() & UNORDERED) > 0;
        contextId = contextInfo.getContextId();


        for (int i = 0; i < steps.size(); i++) {
            // if this is a sequence of steps, the IN_PREDICATE flag
            // is only passed to the first step, so it has to be removed
            // for subsequent steps
            final Expression expr = steps.get(i);
            if ((contextInfo.getFlags() & IN_PREDICATE) > 0) {
                if (i == 1) {
                    //take care : predicates in predicates are not marked as such ! -pb
                    contextInfo.setFlags(contextInfo.getFlags() & (~IN_PREDICATE));
                    //Where clauses should be identified. TODO : pass bound variable's inputSequence ? -pb
                    if ((contextInfo.getFlags() & IN_WHERE_CLAUSE) == 0) {
                        contextInfo.setContextId(Expression.NO_CONTEXT_ID);
                    }
                }
            }

            if (i > 1) {
                contextInfo.setContextStep(steps.get(i - 1));
            }
            contextInfo.setParent(this);
            expr.analyze(contextInfo);
        }
    }

    @Override
    public Sequence eval(Sequence contextSequence, final Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                    "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES,
                        "CONTEXT SEQUENCE", contextSequence);
            }
            if (contextItem != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES,
                        "CONTEXT ITEM", contextItem.toSequence());
            }
        }

        if (contextItem != null) {
            contextSequence = contextItem.toSequence();
        }

        Sequence result = null;
        if (steps.isEmpty()) {
            result = Sequence.EMPTY_SEQUENCE;
        } else {
            //we will filter out nodes from the contextSequence
            result = contextSequence;
            Sequence currentContext = contextSequence;
            DocumentSet contextDocs = null;
            Expression expr = steps.getFirst();
            if (expr instanceof VariableReference) {
                final Variable var = ((VariableReference) expr).getVariable(new AnalyzeContextInfo(parent, 0));
                //TOUNDERSTAND : how null could be possible here ? -pb
                if (var != null) {
                    contextDocs = var.getContextDocs();
                }
            }
            //contextDocs == null *is* significant
            setContextDocSet(contextDocs);
            //To prevent processing nodes after atomic values...
            //TODO : let the parser do it ? -pb
            boolean gotAtomicResult = false;
            Expression prev = null;
            for (int stepIdx = 0; stepIdx < steps.size(); stepIdx++) {
                final Expression step = steps.get(stepIdx);
                prev = expr;
                expr = step;
                context.getWatchDog().proceed(expr);
                //TODO : maybe this could be detected by the parser ? -pb
                if (gotAtomicResult && !Type.subTypeOf(expr.returnsType(), Type.NODE)
                        //Ugly workaround to allow preceding *text* nodes.
                        && !(expr instanceof EnclosedExpr)) {
                    throw new XPathException(this, ErrorCodes.XPTY0019,
                            "left operand of '/' must be a node. Got '" +
                                    Type.getTypeName(result.getItemType()) + " " +
                                    result.getCardinality().getHumanDescription() + "'");
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
                                !(this instanceof Predicate && Type.subTypeOfUnion(this.returnsType(), Type.NUMERIC)) &&
                                currentContext != null && !currentContext.isEmpty())) {
                    Sequence exprResult = new ValueSequence(Type.subTypeOf(expr.returnsType(), Type.NODE));
                    ((ValueSequence) exprResult).keepUnOrdered(unordered);
                    //Restore a position which may have been modified by inner expressions 
                    int p = context.getContextPosition();
                    final Sequence seq = context.getContextSequence();
                    for (final SequenceIterator iterInner = currentContext.iterate(); iterInner.hasNext(); p++) {
                        context.setContextSequencePosition(p, seq);
                        context.getWatchDog().proceed(expr);
                        final Item current = iterInner.nextItem();
                        //0 or 1 item
                        if (!currentContext.hasMany()) {
                            exprResult = expr.eval(currentContext, current);
                        } else {
                            exprResult.addAll(expr.eval(currentContext, current));
                        }
                    }
                    result = exprResult;
                } else {
                    try {
                        result = expr.eval(currentContext, null);
                    } catch (XPathException ex){
                        // enrich exception when information is available
                        if (ex.getLine() < 1 || ex.getColumn() < 1) {
                            ex.setLocation(expr.getLine(), expr.getColumn());
                        }
                        if (ex.getLine() < 1 || ex.getColumn() < 1) {
                            ex.setLocation(getLine(), getColumn());
                        }
                        throw ex;
                    }
                }
                //TOUNDERSTAND : why did I have to write this test :-) ? -pb
                //it looks like an empty sequence could be considered as a sub-type of Type.NODE
                //well, no so stupid I think...
                if (result != null) {
                    if (steps.size() > 1 && !(result instanceof VirtualNodeSet) &&
                            !(expr instanceof EnclosedExpr) && !result.isEmpty() &&
                            !Type.subTypeOf(result.getItemType(), Type.NODE)) {
                        gotAtomicResult = true;
                    }
                    if (steps.size() > 1 && getLastExpression() instanceof Step) {
                        // remove duplicate nodes if this is a path
                        // expression with more than one step
                        result.removeDuplicates();
                    }

                    /**
                     * If the result is an Empty Sequence, we don't need to process
                     * any more steps as there is no Context Sequence for the next step!
                     *
                     * There is one exception to this rule, which is a TextConstructor
                     * expression that only contains whitespace but has been configured
                     * to strip-whitespace. In this instance the TextConstructor will
                     * return true when {@link Expression#evalNextExpressionOnEmptyContextSequence()}
                     * is called to indicate that.
                     */
                    if (result.isEmpty() && stepIdx < steps.size() - 1 && !step.evalNextExpressionOnEmptyContextSequence()) {
                        break;
                    }
                }

                if (!staticContext && (!(step instanceof VariableDeclaration))) {
                    currentContext = result;
                }
            }

            final boolean allowMixedNodesInReturn;
            if(prev != null) {
                allowMixedNodesInReturn = prev.allowMixedNodesInReturn() | expr.allowMixedNodesInReturn();
            } else {
                allowMixedNodesInReturn = expr.allowMixedNodesInReturn();
            }

            if (gotAtomicResult && result != null && !allowMixedNodesInReturn &&
                    !Type.subTypeOf(result.getItemType(), Type.ANY_ATOMIC_TYPE)) {
                throw new XPathException(this, ErrorCodes.XPTY0018,
                        "Cannot mix nodes and atomic values in the result of a path expression.");
            }
        }
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }
        return result;
    }

    @Override
    public XQueryContext getContext() {
        return context;
    }

    public DocumentSet getDocumentSet() {
        return null;
    }

    /**
     * Get the expression.
     *
     * @param pos the position.
     *
     * @return the expression.
     *
     * @deprecated use {@link #getSubExpression(int)}
     */
    @Deprecated
    public Expression getExpression(final int pos) {
        return steps.isEmpty() ? null : steps.get(pos);
    }

    @Override
    public Expression getSubExpression(final int pos) {
        return steps.isEmpty() ? null : steps.get(pos);
    }

    public Expression getLastExpression() {
        return steps.isEmpty() ? null : steps.getLast();
    }

    /**
     * Get the length.
     *
     * @return the length of the path expression.
     *
     * @deprecated use {@link #getSubExpressionCount()}
     */
    @Deprecated
    public int getLength() {
        return steps.size();
    }

    @Override
    public int getSubExpressionCount() {
        return steps.size();
    }

    @Override
    public boolean allowMixedNodesInReturn() {
        if (steps.size() == 1) {
            return steps.getFirst().allowMixedNodesInReturn();
        }
        return super.allowMixedNodesInReturn();
    }

    public void setUseStaticContext(final boolean staticContext) {
        this.staticContext = staticContext;
    }

    @Override
    public void accept(final ExpressionVisitor visitor) {
        visitor.visitPathExpr(this);
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        Expression next = null;
        int count = 0;
        for (final Iterator<Expression> iter = steps.iterator(); iter.hasNext(); count++) {
            next = iter.next();
            //Open a first parenthesis
            if (next instanceof LogicalOp) {
                dumper.display('(');
            }
            if (count > 0) {
                if (next instanceof Step) {
                    dumper.display("/");
                } else {
                    dumper.nl();
                }
            }
            next.dump(dumper);
        }
        //Close the last parenthesis
        if (next instanceof LogicalOp) {
            dumper.display(')');
        }
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        Expression next = null;
        if (steps.isEmpty()) {
            result.append("()");
        } else {
            int count = 0;
            for (final Iterator<Expression> iter = steps.iterator(); iter.hasNext(); count++) {
                next = iter.next();
                // Open a first parenthesis
                if (next instanceof LogicalOp) {
                    result.append('(');
                }
                if (count > 0) {
                    if (next instanceof Step) {
                        result.append("/");
                    } else {
                        result.append(' ');
                    }
                }
                result.append(next.toString());
            }
            // Close the last parenthesis
            if (next instanceof LogicalOp) {
                result.append(')');
            }
        }
        return result.toString();
    }

    @Override
    public int returnsType() {
        if (steps.isEmpty()) {
            //Not so simple. ITEM should be re-tuned in some circumstances that have to be determined
            return Type.NODE;
        }
        return steps.getLast().returnsType();
    }

    @Override
    public Cardinality getCardinality() {
        if (steps.isEmpty()) {
            return Cardinality.EMPTY_SEQUENCE;
        }
        return (steps.getLast()).getCardinality();
    }

    @Override
    public int getDependencies() {
        int deps = 0;
        for (final Expression step : steps) {
            deps = deps | step.getDependencies();
        }
        return deps;
    }

    public void replaceLastExpression(final Expression s) {
        if (steps.isEmpty()) {
            return;
        }
        steps.set(steps.size() - 1, s);
    }

    public String getLiteralValue() {
        if (steps.isEmpty()) {
            return "";
        }
        final Expression next = steps.getFirst();
        if (next instanceof LiteralValue) {
            try {
                return ((LiteralValue) next).getValue().getStringValue();
            } catch (final XPathException e) {
                //TODO : is there anything to do here ?
            }
        }
        if (next instanceof PathExpr) {
            return ((PathExpr) next).getLiteralValue();
        }
        return "";
    }

    @Override
    public int getLine() {
        if (line <= 0 && !steps.isEmpty()) {
            return steps.getFirst().getLine();
        }
        return line;
    }

    @Override
    public int getColumn() {
        if (column <= 0 && !steps.isEmpty()) {
            return steps.getFirst().getColumn();
        }
        return column;
    }

    @Override
    public void setPrimaryAxis(final int axis) {
        if (!steps.isEmpty()) {
            steps.getFirst().setPrimaryAxis(axis);
        }
    }

    @Override
    public int getPrimaryAxis() {
        if (!steps.isEmpty()) {
            return steps.getFirst().getPrimaryAxis();
        }
        return Constants.UNKNOWN_AXIS;
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        for (Expression step : steps) {
            step.resetState(postOptimization);
        }
    }

    @Override
    public void reset() {
        resetState(false);
    }

    @Override
    public boolean isValid() {
        return context.checkModulesValid();
    }

    @Override
    public void dump(final Writer writer) {
        final ExpressionDumper dumper = new ExpressionDumper(writer);
        dump(dumper);
    }

    @Override
    public void setContext(final XQueryContext context) {
        this.context = context;
    }

    @Override
    public Expression simplify() {
        if (steps.size() == 1) {
            return steps.getFirst().simplify();
        }
        return this;
    }
}
