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

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author <a href="adam@evolvedbinary.com">Adam Retter</a>
 * @author <a href="gabriele@strumenta.com">Gabriele Tomassetti</a>
 */
public class WindowExpr extends BindingExpression {

    public enum WindowType {
        TUMBLING_WINDOW,
        SLIDING_WINDOW
    }

    private boolean isOuterFor = true;

    private final WindowCondition windowStartCondition;
    private final @Nullable WindowCondition windowEndCondition;
    private final WindowType windowType;

    public WindowExpr(final XQueryContext context, final WindowType type, final WindowCondition windowStartCondition, @Nullable final WindowCondition windowEndCondition) {
        super(context);
        this.windowType = type;
        this.windowStartCondition = windowStartCondition;
        this.windowEndCondition = windowEndCondition;
    }

    @Override
    public ClauseType getType() {
        return ClauseType.WINDOW;
    }

    public WindowType getWindowType() {
        return this.windowType;
    }

    public WindowCondition getWindowStartCondition() {
        return windowStartCondition;
    }

    @Nullable
    public WindowCondition getWindowEndCondition() {
        return windowEndCondition;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        // Save the local variable stack
        final LocalVariable mark = context.markLocalVariables(false);
        try {
            contextInfo.setParent(this);
            final AnalyzeContextInfo varContextInfo = new AnalyzeContextInfo(contextInfo);
            inputSequence.analyze(varContextInfo);

            final SequenceType windowVarType = sequenceType != null ? sequenceType : new SequenceType(Type.ITEM, Cardinality.ONE_OR_MORE);

            // NOTE: we don't know what the Window will select for the WindowVar at this stage, so we can only do a check if the types are explicitly known at this point
            final int inReturnType = inputSequence.returnsType();
            if (!(inReturnType == Type.ITEM && windowVarType.getPrimaryType() != Type.ITEM)
                    && !Type.subTypeOf(inReturnType, windowVarType.getPrimaryType())) {
                throw new XPathException(this, ErrorCodes.XPTY0004, "Window variable expects type: " + windowVarType + ", but window binding sequence is of type: " + Type.getTypeName(inputSequence.returnsType()) + inputSequence.getCardinality().toXQueryCardinalityString());
            }

            // Declare the Window variable
            final LocalVariable windowVar = new LocalVariable(varName);
            windowVar.setSequenceType(windowVarType);
            windowVar.setStaticType(varContextInfo.getStaticReturnType());
            context.declareVariableBinding(windowVar);

            // Declare start WindowCondition variables
            declareWindowConditionVariables(true, windowStartCondition);
            final AnalyzeContextInfo startWhenContextInfo = new AnalyzeContextInfo(contextInfo);
            windowStartCondition.getWhenExpression().analyze(startWhenContextInfo);

            // Declare end WindowCondition variables
            declareWindowConditionVariables(true, windowEndCondition);
            final AnalyzeContextInfo endWhenContextInfo = new AnalyzeContextInfo(contextInfo);
            if (windowEndCondition != null) {
                windowEndCondition.getWhenExpression().analyze(endWhenContextInfo);
            }

            final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
            returnExpr.analyze(newContextInfo);

        } finally {
            // restore the local variable stack
            context.popLocalVariables(mark);
        }
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem)
            throws XPathException {

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

        context.expressionStart(this);

        Sequence resultSequence = new ValueSequence(unordered);
        Sequence in = inputSequence.eval(contextSequence, contextItem);

        registerUpdateListener(in);

        // pre-eval the return expr if it is a FLWORClause and we are the outer for-loop
        if (isOuterFor && returnExpr instanceof FLWORClause flworClause) {
            in = flworClause.preEval(in);
        }

        // when `window` is not null, we have started
        Window window = null;
        int windowStartIdx = -1;

        LocalVariable windowStartMark = null;
        WindowContextVariables windowStartConditionVariables = null;

        LocalVariable windowEndMark = null;
        WindowContextVariables windowEndConditionVariables = null;

        Item previousItem = null;

        final int inCount = in.getItemCount();
        for (int i = 0; i < inCount; i++) {

            final Item currentItem = in.itemAt(i);
            final Item nextItem;
            if (i + 1 <= inCount - 1) {
                nextItem = in.itemAt(i + 1);
            } else {
                nextItem = null;
            }

            // if we have NOT started, check if the start-when condition is true
            if (window == null) {

                // Save the local variable stack
                windowStartMark = context.markLocalVariables(false);

                // Declare Window Start Condition variables
                windowStartConditionVariables = declareWindowConditionVariables(false, windowStartCondition);
                setWindowConditionVariables(windowStartConditionVariables, currentItem, i, previousItem, nextItem);

                // check if the start-when condition is true
                final Sequence startWhen = windowStartCondition.getWhenExpression().eval(contextSequence, contextItem);
                if (startWhen.effectiveBooleanValue()) {

                    // signal we have started
                    window = new Window();
                    window.start(windowStartConditionVariables);
                    windowStartIdx = i;
                }
            }

            if (window != null) {
                // if we have started...

                // if there is no end-when condition and we are after the start-item, check if the start-when condition is true
                if (windowEndCondition == null && i > windowStartIdx) {
                    // Save the local variable stack
                    windowStartMark = context.markLocalVariables(false);

                    // Declare Window Start Condition variables
                    windowStartConditionVariables = declareWindowConditionVariables(false, windowStartCondition);
                    setWindowConditionVariables(windowStartConditionVariables, currentItem, i, previousItem, nextItem);

                    final boolean endWhen = windowStartCondition.getWhenExpression().eval(contextSequence, contextItem).effectiveBooleanValue();
                    if (endWhen) {

                        window.end(windowEndConditionVariables);

                        // eval the return expression on the window binding
                        returnEvalWindowBinding(in, window, resultSequence);

                        // reset the window
                        if (windowEndMark != null) {
                            context.popLocalVariables(windowEndMark, resultSequence);
                            windowEndConditionVariables.destroy(context, resultSequence);
                            windowEndConditionVariables = null;
                            windowEndMark = null;

                        }

                        // signal the start of a new window
                        window = new Window();
                        window.start(windowStartConditionVariables);

                        if (windowStartMark != null) {
                            context.popLocalVariables(windowStartMark, resultSequence);
                            windowStartConditionVariables = null;
                            windowStartMark = null;
                        }

                        windowStartIdx = i;
                    }
                }

                // add the currentItem to the Window
                window.add(currentItem);

                // Declare Window End Condition variables
                if (windowEndCondition != null) {
                    // Save the local variable stack
                    windowEndMark = context.markLocalVariables(false);

                    windowEndConditionVariables = declareWindowConditionVariables(false, windowEndCondition);
                    if (windowEndConditionVariables != null) {
                        setWindowConditionVariables(windowEndConditionVariables, currentItem, i, previousItem, nextItem);
                    }
                }

                // check if the end-when condition is true
                final boolean endWhen;
                if (windowEndCondition != null) {
                    endWhen = windowEndCondition.getWhenExpression().eval(contextSequence, contextItem).effectiveBooleanValue();
                } else {
                    endWhen = false;
                }
                if (endWhen || (windowType == WindowType.SLIDING_WINDOW && (windowEndCondition != null && !windowEndCondition.isOnly()) && i == inCount - 1 && i > windowStartIdx)) {

                    if (window != null) {

                        window.end(windowEndConditionVariables);

                        // eval the return expression on the window binding
                        returnEvalWindowBinding(in, window, resultSequence);

                        // reset the window
                        if (windowEndMark != null) {
                            context.popLocalVariables(windowEndMark, resultSequence);
                            windowEndConditionVariables.destroy(context, resultSequence);
                            windowEndConditionVariables = null;
                            windowEndMark = null;

                        }
                        if (windowStartMark != null) {
                            context.popLocalVariables(windowStartMark, resultSequence);
                            windowStartConditionVariables.destroy(context, resultSequence);
                            windowStartConditionVariables = null;
                            windowStartMark = null;
                        }
                        window = null;

                        if (windowType == WindowType.SLIDING_WINDOW) {
                            // return to the start of the window, so that when we next come around the loop we start examining a new window immediately after the start of the previous window
                            i = windowStartIdx;
                        }
                    }
                }
            }

            if (windowType == WindowType.SLIDING_WINDOW && window == null && windowStartIdx > -1) {
                previousItem = in.itemAt(windowStartIdx);
                windowStartIdx = -1;
            } else {
                previousItem = currentItem;
            }
        }  // end for loop

        if (window != null && (windowEndCondition == null || !windowEndCondition.isOnly())) {
            // output the remaining binding sequence as a final window

            window.end(windowEndConditionVariables);

            // eval the return expression on the window binding
            returnEvalWindowBinding(in, window, resultSequence);

            // reset the window
            if (windowEndMark != null) {
                context.popLocalVariables(windowEndMark, resultSequence);
                windowEndConditionVariables.destroy(context, resultSequence);
                windowEndConditionVariables = null;
                windowEndMark = null;

            }
            if (windowStartMark != null) {
                context.popLocalVariables(windowStartMark, resultSequence);
                windowStartConditionVariables.destroy(context, resultSequence);
                windowStartConditionVariables = null;
                windowStartMark = null;

                window = null;
                windowStartIdx = -1;
            }
        }

        clearContext(getExpressionId(), in);

        setActualReturnType(resultSequence.getItemType());

        if (callPostEval()) {
            resultSequence = postEval(resultSequence);
        }

        context.expressionEnd(this);
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", resultSequence);
        }

        return resultSequence;
    }

    private void returnEvalWindowBinding(final Sequence in, final Window window, final Sequence resultSequence) throws XPathException {
        // Save the local variable stack
        final LocalVariable mark = context.markLocalVariables(false);

        try {

            // check that the type of the window binding var can accept the window data
            final SequenceType windowVarType = sequenceType != null ? sequenceType : new SequenceType(Type.ITEM, Cardinality.ONE_OR_MORE);
            final SequenceType windowDataType = new SequenceType(window.items.getItemType(), window.items.getCardinality());
            if (!Type.subTypeOf(windowDataType.getPrimaryType(), windowVarType.getPrimaryType())
                    || !windowDataType.getCardinality().isSubCardinalityOrEqualOf(windowVarType.getCardinality())) {
                throw new XPathException(this, ErrorCodes.XPTY0004, "Window variable expects type: " + windowVarType + ", but window binding sequence is of type: " + windowDataType);
            }

            // Declare the Window variable
            final LocalVariable windowVar = createVariable(varName);
            windowVar.setSequenceType(windowVarType);
            context.declareVariableBinding(windowVar);

            // Save the current context document set to the variable as a hint
            // for path expressions occurring in the "return" clause.
            if (in instanceof NodeSet) {
                windowVar.setContextDocs(in.getDocumentSet());
            } else {
                windowVar.setContextDocs(null);
            }

            // set the binding for the window for the return expression
            windowVar.setValue(window.items);

            // bind the start and end variables for the return expression
            @Nullable final WindowContextVariables windowReturnStartVariables = declareWindowReturnVariableBindings(window.startVariables);
            @Nullable final WindowContextVariables windowReturnEndVariables = declareWindowReturnVariableBindings(window.endVariables);

        // eval the return expression on the window binding
        resultSequence.addAll(returnExpr.eval(null, null));

            // free resources
            if (windowReturnEndVariables != null) {
                windowReturnEndVariables.destroy(context, resultSequence);
            }
            if (windowReturnStartVariables != null) {
                windowReturnStartVariables.destroy(context, resultSequence);
            }
            windowVar.destroy(context, resultSequence);
        } finally {
            // restore the local variable stack
            context.popLocalVariables(mark, resultSequence);
        }
    }

    @Override
    public Sequence preEval(final Sequence seq) throws XPathException {
        // if preEval gets called, we know we're inside another FOR
        isOuterFor = false;
        return super.preEval(seq);
    }

    private boolean callPostEval() {
        FLWORClause prev = getPreviousClause();
        while (prev != null) {
            switch (prev.getType()) {
                case LET, FOR -> {
                    return false;
                }
                case ORDERBY, GROUPBY -> {
                    return true;
                }
            }
            prev = prev.getPreviousClause();
        }
        return true;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display(this.getWindowType() == WindowType.TUMBLING_WINDOW ? "tumbling window " : "sliding window ", line);
        dumper.startIndent();
        dumper.display("$").display(varName);
        if (sequenceType != null) {
            dumper.display(" as ").display(sequenceType);
        }
        dumper.display(" in ");
        inputSequence.dump(dumper);
        dumper.endIndent().nl();
        if (returnExpr instanceof LetExpr) {
            dumper.display(" ", returnExpr.getLine());
        } else {
            dumper.display("return", returnExpr.getLine());
        }
        dumper.startIndent();
        returnExpr.dump(dumper);
        dumper.endIndent().nl();
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append(this.getWindowType() == WindowType.TUMBLING_WINDOW ? "tumbling" : "sliding").append(" window ");
        result.append("$").append(varName);
        if (sequenceType != null) {
            result.append(" as ").append(sequenceType);
        }
        result.append(" in ");
        result.append(inputSequence.toString());
        result.append(" ");
        result.append("start ").append(windowStartCondition.toString());
        if (windowEndCondition != null) {
            result.append(" end ").append(windowEndCondition);
        }
        if (returnExpr instanceof LetExpr) {
            result.append(" ");
        } else {
            result.append(" return ");
        }
        result.append(returnExpr.toString());
        return result.toString();
    }

    @Override
    public void accept(final ExpressionVisitor visitor) {
        visitor.visitWindowExpression(this);
    }

    @Override
    public boolean allowMixedNodesInReturn() {
        return true;
    }

    private @Nullable WindowContextVariables declareWindowConditionVariables(final boolean analyzePhase, @Nullable final WindowCondition windowCondition) throws XPathException {
        if (windowCondition == null) {
            return null;
        }
        return new WindowContextVariables(
                declareWindowConditionVariable(analyzePhase, windowCondition.getCurrentItem(), Type.ITEM, new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE)),
                declareWindowConditionVariable(analyzePhase, windowCondition.getPosVar(), Type.INTEGER, POSITIONAL_VAR_TYPE),
                declareWindowConditionVariable(analyzePhase, windowCondition.getPreviousItem(), Type.ITEM, new SequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE)),
                declareWindowConditionVariable(analyzePhase, windowCondition.getNextItem(), Type.ITEM, new SequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE))
        );
    }

    private @Nullable LocalVariable declareWindowConditionVariable(final boolean analyzePhase, @Nullable final QName variableName, final int staticType, final SequenceType sequenceType) throws XPathException {
        if (variableName == null) {
            return null;
        }

        final LocalVariable windowConditionVariable;
        if (analyzePhase) {
            windowConditionVariable = new LocalVariable(variableName);
        } else {
            windowConditionVariable = createVariable(variableName);
        }
        windowConditionVariable.setSequenceType(sequenceType);
        windowConditionVariable.setStaticType(staticType);
        context.declareVariableBinding(windowConditionVariable);
        return windowConditionVariable;
    }

    private static void setWindowConditionVariables(final WindowContextVariables windowConditionVariables, @Nullable final Item currentItem, final int idx, @Nullable final Item previousItem, @Nullable final Item nextItem) {
        // set the current-item
        if (windowConditionVariables.currentItem != null) {
            windowConditionVariables.currentItem.setValue(currentItem != null ? currentItem.toSequence() : Sequence.EMPTY_SEQUENCE);
        }

        // set the position
        if (windowConditionVariables.posVar != null) {
            windowConditionVariables.posVar.setValue(new IntegerValue(idx + 1L));
        }

        // set the previous-item
        if (windowConditionVariables.previousItem != null) {
            windowConditionVariables.previousItem.setValue(previousItem != null ? previousItem.toSequence() : Sequence.EMPTY_SEQUENCE);
        }

        // set the next-item
        if (windowConditionVariables.nextItem != null) {
            windowConditionVariables.nextItem.setValue(nextItem != null ? nextItem.toSequence() : Sequence.EMPTY_SEQUENCE);
        }
    }

    private @Nullable WindowContextVariables declareWindowReturnVariableBindings(@Nullable final WindowContextVariables windowContextVariables) throws XPathException {
        if (windowContextVariables == null) {
            return null;
        }
        return new WindowContextVariables(
                declareWindowReturnVariableBinding(windowContextVariables.currentItem),
                declareWindowReturnVariableBinding(windowContextVariables.posVar),
                declareWindowReturnVariableBinding(windowContextVariables.previousItem),
                declareWindowReturnVariableBinding(windowContextVariables.nextItem)
        );
    }

    private @Nullable LocalVariable declareWindowReturnVariableBinding(@Nullable final LocalVariable windowContextVariable) throws XPathException {
        if (windowContextVariable == null) {
            return null;
        }

        final LocalVariable windowReturnVariable = createVariable(windowContextVariable.getQName());
        windowReturnVariable.setSequenceType(windowContextVariable.getSequenceType());
        windowReturnVariable.setStaticType(windowContextVariable.getStaticType());

        // bind the value
        windowReturnVariable.setValue(windowContextVariable.getValue());

        // declare it
        context.declareVariableBinding(windowReturnVariable);

        return windowReturnVariable;
    }

    private static class WindowContextVariables {
        final @Nullable
        LocalVariable currentItem;
        final @Nullable
        LocalVariable posVar;
        final @Nullable
        LocalVariable previousItem;
        final @Nullable
        LocalVariable nextItem;

        private WindowContextVariables(@Nullable final LocalVariable currentItem, @Nullable final LocalVariable posVar, @Nullable final LocalVariable previousItem, @Nullable final LocalVariable nextItem) {
            this.currentItem = currentItem;
            this.posVar = posVar;
            this.previousItem = previousItem;
            this.nextItem = nextItem;
        }

        public boolean isDefined() {
            return currentItem != null || posVar != null || previousItem != null || nextItem != null;
        }

        public void destroy(final XQueryContext context, final Sequence contextSequence) {
            if (nextItem != null) {
                nextItem.destroy(context, contextSequence);
            }
            if (previousItem != null) {
                previousItem.destroy(context, contextSequence);
            }
            if (posVar != null) {
                posVar.destroy(context, contextSequence);
            }
            if (currentItem != null) {
                currentItem.destroy(context, contextSequence);
            }
        }
    }

    @Override
    public Set<QName> getTupleStreamVariables() {
        final Set<QName> variables = new HashSet<>();

        addQName(variables, varName);

        addQName(variables, windowStartCondition.getPreviousItem());
        addQName(variables, windowStartCondition.getCurrentItem());
        addQName(variables, windowStartCondition.getNextItem());

        if (windowEndCondition != null) {
            addQName(variables, windowEndCondition.getPreviousItem());
            addQName(variables, windowEndCondition.getCurrentItem());
            addQName(variables, windowEndCondition.getNextItem());
        }

        addQName(variables, getVariable());

        final LocalVariable startVariable = getStartVariable();
        if (startVariable != null) {
            addQName(variables, startVariable.getQName());
        }

        return variables;
    }

    private void addQName(final Collection<QName> qnames, @Nullable final QName qname) {
        if (qname != null) {
            qnames.add(qname);
        }
    }

    private static class Window {
        @Nullable Sequence items;
        @Nullable
        WindowContextVariables startVariables;
        @Nullable
        WindowContextVariables endVariables;
        @Nullable private State state;

        private enum State {
            STARTED,
            ENDED;
        }

        public void start(@Nullable final WindowContextVariables startVariables) {
            if (state != null) {
                throw new IllegalStateException("Window has already been started");
            }

            if (startVariables != null && startVariables.isDefined()) {
                this.startVariables = startVariables;
            }

            state = State.STARTED;
        }

        public void add(final Item item) throws XPathException {
            if (state != State.STARTED) {
                throw new IllegalStateException("Window is not in the started state");
            }

            if (items == null) {
                items = new ValueSequence(false);
            }
            items.add(item);
        }

        public void end(@Nullable final WindowContextVariables endVariables) {
            if (state != State.STARTED) {
                throw new IllegalStateException("Window is not in the started state");
            }

            if (endVariables != null && endVariables.isDefined()) {
                this.endVariables = endVariables;
            }

            state = State.ENDED;
        }
    }

}
