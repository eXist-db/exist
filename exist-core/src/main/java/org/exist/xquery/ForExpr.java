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

import org.exist.dom.QName;
import org.exist.dom.persistent.NodeSet;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents an XQuery "for" expression.
 * 
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class ForExpr extends BindingExpression {

    private QName positionalVariable = null;
    private QName scoreVariable = null;
    private boolean allowEmpty = false;
    private boolean isOuterFor = true;

    public ForExpr(XQueryContext context, boolean allowingEmpty) {
        super(context);
        this.allowEmpty = allowingEmpty;
    }

    @Override
    public ClauseType getType() {
        return ClauseType.FOR;
    }

    /**
     * FLWOR loop-invariant input hoisting via rewrite-into-let.
     *
     * If this {@code for}'s {@code in} expression is loop-invariant relative
     * to all enclosing FLWOR-bound variables, rewrite it as a reference to a
     * new {@code let} binding inserted before the outermost enclosing FLWOR
     * head. The hoisted expression is evaluated once instead of once per
     * outer iteration, turning O(N×M) nested-loop joins into O(N+M) for the
     * input materialization.
     *
     * Order matters: this clause's own variables are added to the current
     * scope AFTER recursing into the input (the binding is not in scope for
     * its own initializer) and BEFORE recursing into the return expression
     * (the rest of the chain).
     */
    @Override
    public Expression optimize(final CompileContext cc) throws XPathException {
        final boolean enteredScope = getPreviousClause() == null;
        if (enteredScope) {
            cc.enterFlworChain();
        }

        // Recurse input first — gives any inner FLWORs a chance to hoist
        // themselves out of US (their hoists target our scope's outermost).
        if (inputSequence != null) {
            inputSequence = inputSequence.optimize(cc);
        }

        // Hoist OUR input, if invariant against outer chain scopes.
        tryHoistInputSequence(cc);

        // Record THIS for-clause as a potential hoist insertion point on the
        // current scope (the first such call wins). Must precede addVisibleVar
        // so subsequent vars are tagged loop-body, not let-prefix.
        cc.recordForClause(this);

        // Now this clause's vars become visible to the rest of the chain.
        cc.addVisibleFlworVar(varName);
        if (positionalVariable != null) {
            cc.addVisibleFlworVar(positionalVariable);
        }
        if (scoreVariable != null) {
            cc.addVisibleFlworVar(scoreVariable);
        }

        if (returnExpr != null) {
            returnExpr = returnExpr.optimize(cc);
        }

        Expression result = this;
        if (enteredScope) {
            result = cc.applyHoistsAndExitChain(result);
        }
        return result;
    }

    /**
     * Decide whether this for-clause's input is loop-invariant relative to
     * outer FLWOR scopes and, if so, queue a hoist on the outermost scope
     * and replace the input with a reference to the synthesized variable.
     *
     * Conservative gates:
     * <ul>
     *   <li>requires at least one outer FLWOR scope (otherwise nothing to
     *       hoist over);</li>
     *   <li>skips trivial inputs (literals, bare variable references) where
     *       hoisting yields no benefit;</li>
     *   <li>skips updating expressions (W3C XQUF — must not move side
     *       effects);</li>
     *   <li>refuses to hoist when reference-collection encountered an
     *       expression shape the walker cannot prove side-effect-free of
     *       outer-var references.</li>
     * </ul>
     */
    private void tryHoistInputSequence(final CompileContext cc) {
        if (inputSequence == null
                || inputSequence instanceof VariableReference
                || inputSequence instanceof LiteralValue
                || cc.flworChainDepth() < 2) {
            return;
        }
        if (inputSequence.isUpdating()) {
            return;
        }

        final Set<QName> outerVars = cc.getOuterLoopBodyVars();
        if (outerVars.isEmpty()) {
            return;
        }

        final RefCollector refs = new RefCollector();
        refs.collect(inputSequence);
        if (refs.aborted) {
            return;
        }
        for (final QName name : refs.referenced) {
            if (outerVars.contains(name)) {
                return;
            }
        }

        final QName hoistedName = cc.generateHoistedVarName();
        cc.addPendingHoistToOutermost(hoistedName, inputSequence);

        final VariableReference ref = new VariableReference(context, hoistedName);
        ref.setLocation(line, column);
        inputSequence = ref;
    }

    /**
     * Walks an expression subtree to collect the QNames of in-scope variables
     * it references. Sets {@link #aborted} to true if it encounters a class
     * shape it cannot reliably traverse — callers must treat that as
     * "may reference any var" and refuse to hoist.
     *
     * The subtree walk explicitly handles classes whose children are NOT
     * exposed via {@code getSubExpression}: {@link BindingExpression}'s
     * {@code inputSequence} / {@code returnExpr}, {@link FilteredExpression}'s
     * expression and predicates, {@link LocationStep}'s predicates, and the
     * {@link AbstractFLWORClause} chain. For everything else it falls back to
     * {@code getSubExpression}; an unrecognized class with no advertised
     * children aborts the walk.
     */
    private static final class RefCollector {
        final Set<QName> referenced = new HashSet<>();
        boolean aborted = false;

        void collect(final Expression expr) {
            if (expr == null || aborted) {
                return;
            }
            if (expr instanceof VariableReference vr) {
                final QName name = vr.getName();
                if (name != null) {
                    referenced.add(name);
                }
                return;
            }
            if (expr instanceof LiteralValue) {
                return;
            }
            if (expr instanceof BindingExpression be) {
                collect(be.getInputSequence());
                if (be instanceof AbstractFLWORClause flwor) {
                    collect(flwor.getReturnExpression());
                }
                return;
            }
            if (expr instanceof FilteredExpression fe) {
                collect(fe.getExpression());
                for (final Predicate p : fe.getPredicates()) {
                    collect(p);
                }
                return;
            }
            if (expr instanceof LocationStep ls) {
                final Predicate[] preds = ls.getPredicates();
                if (preds != null) {
                    for (final Predicate p : preds) {
                        collect(p);
                    }
                }
                return;
            }
            if (expr instanceof WhereClause wc) {
                collect(wc.getWhereExpr());
                collect(wc.getReturnExpression());
                return;
            }
            if (expr instanceof AbstractFLWORClause flwor) {
                // OrderByClause, GroupByClause, CountClause, ReturnClause-like.
                // Their non-returnExpr children aren't uniformly accessible;
                // bail conservatively.
                aborted = true;
                return;
            }
            final int count = expr.getSubExpressionCount();
            if (count == 0) {
                // Unknown leaf with no advertised children: cannot prove
                // it has no var references. Conservative bail.
                if (!isKnownSafeLeaf(expr)) {
                    aborted = true;
                }
                return;
            }
            for (int i = 0; i < count; i++) {
                collect(expr.getSubExpression(i));
                if (aborted) {
                    return;
                }
            }
        }

        private static boolean isKnownSafeLeaf(final Expression expr) {
            return expr instanceof LiteralValue;
        }
    }

    /**
     * A "for" expression may have an optional positional variable whose
     * QName can be set via this method.
     * 
     * @param variable the name of the variable to set
     */
    public void setPositionalVariable(final QName variable) {
        positionalVariable = variable;
    }

    /**
     * XQFT 3.0 §2.3: A "for" expression may have an optional score variable
     * whose QName can be set via this method. The score variable is bound to
     * an xs:double value representing the relevance score for each item.
     *
     * @param variable the name of the score variable
     */
    public void setScoreVariable(final QName variable) {
        scoreVariable = variable;
    }

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.Expression)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        // Save the local variable stack
        final LocalVariable mark = context.markLocalVariables(false);
        try {
            contextInfo.setParent(this);
            final AnalyzeContextInfo varContextInfo = new AnalyzeContextInfo(contextInfo);
            varContextInfo.addFlag(NON_UPDATING_CONTEXT);
            inputSequence.analyze(varContextInfo);
            // Declare the iteration variable
            final LocalVariable inVar = new LocalVariable(varName);
            inVar.setSequenceType(sequenceType);
            inVar.setStaticType(varContextInfo.getStaticReturnType());
            context.declareVariableBinding(inVar);
            // Declare positional variable
            if (positionalVariable != null) {
                final LocalVariable posVar = new LocalVariable(positionalVariable);
                posVar.setSequenceType(POSITIONAL_VAR_TYPE);
                posVar.setStaticType(Type.INTEGER);
                context.declareVariableBinding(posVar);
            }
            // Declare score variable (XQFT 3.0 §2.3)
            if (scoreVariable != null) {
                final LocalVariable scoreVar = new LocalVariable(scoreVariable);
                scoreVar.setSequenceType(new SequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE));
                scoreVar.setStaticType(Type.DOUBLE);
                context.declareVariableBinding(scoreVar);
            }

            final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
            newContextInfo.addFlag(SINGLE_STEP_EXECUTION);
            returnExpr.analyze(newContextInfo);
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
    public Sequence eval(Sequence contextSequence, Item contextItem)
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
        }
        context.expressionStart(this);
        LocalVariable var;
        Sequence in;
        // Save the local variable stack
        LocalVariable mark = context.markLocalVariables(false);
        Sequence resultSequence = new ValueSequence(unordered);
        try {
            // Evaluate the "in" expression
            in = inputSequence.eval(contextSequence, null);
            clearContext(getExpressionId(), in);
            // Declare the iteration variable
            var = createVariable(varName);
            var.setSequenceType(sequenceType);
            context.declareVariableBinding(var);
            registerUpdateListener(in);
            // Declare positional variable
            LocalVariable at = null;
            if (positionalVariable != null) {
                at = new LocalVariable(positionalVariable);
                at.setSequenceType(POSITIONAL_VAR_TYPE);
                context.declareVariableBinding(at);
            }
            // Declare score variable (XQFT 3.0 §2.3)
            LocalVariable score = null;
            if (scoreVariable != null) {
                score = new LocalVariable(scoreVariable);
                score.setSequenceType(new SequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE));
                context.declareVariableBinding(score);
                // Naive implementation: always bind score to 1.0
                score.setValue(new DoubleValue(this, 1.0));
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
            if (isOuterFor) {
                if (returnExpr instanceof WhereClause) {
                    if (at == null) {
                        in = ((WhereClause) returnExpr).preEval(in);
                    }
                } else if (returnExpr instanceof FLWORClause) {
                    in = ((FLWORClause) returnExpr).preEval(in);
                }
            }

            final IntegerValue atVal = new IntegerValue(this, 1);
            if (positionalVariable != null) {
                at.setValue(atVal);
            }
            //Type.EMPTY is *not* a subtype of other types ;
            //the tests below would fail without this prior cardinality check
            if (in.isEmpty() && sequenceType != null &&
                    !sequenceType.getCardinality().isSuperCardinalityOrEqualOf(Cardinality.EMPTY_SEQUENCE)) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Invalid cardinality for variable $" + varName +
                                ". Expected " + sequenceType.getCardinality().getHumanDescription() +
                                ", got " + in.getCardinality().getHumanDescription());
            }

            // Loop through each variable binding
            int p = 0;
            try {
                if (in.isEmpty() && allowEmpty) {
                    processItem(var, AtomicValue.EMPTY_VALUE, Sequence.EMPTY_SEQUENCE, resultSequence, at, p);
                } else {
                    for (final SequenceIterator i = in.iterate(); i.hasNext() && !WhileClause.isTerminated(); p++) {
                        processItem(var, i.nextItem(), in, resultSequence, at, p);
                    }
                }
            } catch (final WhileClause.WhileTerminationException e) {
                // while clause signaled end of iteration for this for loop
            }
            // clear terminated flag if this is the outermost for
            if (isOuterFor && WhileClause.isTerminated()) {
                WhileClause.clearTerminated();
            }
        } finally {
            // restore the local variable stack
            context.popLocalVariables(mark, resultSequence);
        }

        clearContext(getExpressionId(), in);
        setActualReturnType(resultSequence.getItemType());

        if (callPostEval()) {
            resultSequence = postEval(resultSequence);
        }

        context.expressionEnd(this);
        if (context.getProfiler().isEnabled())
            {context.getProfiler().end(this, "", resultSequence);}
        return resultSequence;
    }

    private void processItem(LocalVariable var, Item contextItem, Sequence in, Sequence resultSequence, LocalVariable
            at, int p) throws XPathException {
        context.proceed(this);
        context.setContextSequencePosition(p, in);
        if (positionalVariable != null) {
            final int position = contextItem == AtomicValue.EMPTY_VALUE ? 0 : p + 1;
            at.setValue(new IntegerValue(this, position));
        }
        final Sequence contextSequence = contextItem.toSequence();
        // set variable value to current item
        var.setValue(contextSequence);
        var.checkType();
        //Reset the context position
        context.setContextSequencePosition(0, null);

        final Sequence returnExprResult;
        if (returnExpr instanceof OrderByClause) {
            returnExprResult = returnExpr.eval(contextSequence, null);
        } else {
            returnExprResult = returnExpr.eval(null, null);
        }
        resultSequence.addAll(returnExprResult);

        // free resources
        var.destroy(context, resultSequence);
    }

    private boolean callPostEval() {
        FLWORClause prev = getPreviousClause();
        while (prev != null) {
            switch (prev.getType()) {
                case LET:
                case FOR:
                    return false;
                case ORDERBY:
                case GROUPBY:
                    return true;
                default:
                    break;
            }
            prev = prev.getPreviousClause();
        }
        return true;
    }

    @Override
    public Sequence preEval(Sequence seq) throws XPathException {
        // if preEval gets called, we know we're inside another FOR
        isOuterFor = false;
        return super.preEval(seq);
    }

    @Override
    public void dump(ExpressionDumper dumper) {
        dumper.display("for ", line);
        dumper.startIndent();
        dumper.display("$").display(varName);
        if (sequenceType != null) {
            dumper.display(" as ").display(sequenceType);
        }
        if (allowEmpty) {
            dumper.display(" allowing empty ");
        }
        if (positionalVariable != null)
            {dumper.display(" at ").display(positionalVariable);}
        if (scoreVariable != null)
            {dumper.display(" score ").display(scoreVariable);}
        dumper.display(" in ");
        inputSequence.dump(dumper);
        dumper.endIndent().nl();
        //TODO : QuantifiedExpr
        if (returnExpr instanceof LetExpr)
            {dumper.display(" ", returnExpr.getLine());}
        else
            {dumper.display("return", returnExpr.getLine());} 
        dumper.startIndent();
        returnExpr.dump(dumper);
        dumper.endIndent().nl();
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("for ");
        result.append("$").append(varName);
        if (sequenceType != null)
            {result.append(" as ").append(sequenceType);}
        if (allowEmpty) {
            result.append(" allowing empty ");
        }
        if (positionalVariable != null) {
            result.append(" at ").append(positionalVariable);
        }
        if (scoreVariable != null) {
            result.append(" score ").append(scoreVariable);
        }
        result.append(" in ");
        result.append(inputSequence.toString());
        result.append(" ");
        //TODO : QuantifiedExpr
        if (returnExpr instanceof LetExpr)
            {result.append(" ");}
        else
            {result.append("return ");}
        result.append(returnExpr.toString());
        return result.toString();
    }

    @Override
    public void accept(final ExpressionVisitor visitor) {
        visitor.visitForExpression(this);
    }

    @Override
    public Set<QName> getTupleStreamVariables() {
        final Set<QName> variables = new HashSet<>();
        if (positionalVariable != null) {
            variables.add(positionalVariable);
        }
        if (scoreVariable != null) {
            variables.add(scoreVariable);
        }

        final QName variable = getVariable();
        if (variable != null) {
            variables.add(variable);
        }

        final LocalVariable startVar = getStartVariable();
        if (startVar != null) {
            variables.add(startVar.getQName());
        }

        return variables;
    }
}
