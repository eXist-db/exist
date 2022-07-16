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

/**
 * @author <a href="adam@evolvedbinary.com">Adam Retter</a>
 * @author <a href="gabriele@strumenta.com">Gabriele Tomassetti</a>
 */
public class WindowExpr extends BindingExpression {

    public enum WindowType {
        TUMBLING_WINDOW,
        SLIDING_WINDOW
    }

    private final WindowCondition windowStartCondition;
    private final @Nullable
    WindowCondition windowEndCondition;

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

            // Declare the Window variable
            final LocalVariable windowVar = new LocalVariable(varName);
            windowVar.setSequenceType(sequenceType);
            windowVar.setStaticType(varContextInfo.getStaticReturnType());
            context.declareVariableBinding(windowVar);

            // Declare WindowCondition variables
            declareWindowConditionVariables(context, windowStartCondition);
            declareWindowConditionVariables(context, windowEndCondition);

            final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
//            newContextInfo.addFlag(SINGLE_STEP_EXECUTION);  // TODO(AR) is this correct
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

        final LocalVariable windowVar;
        final Sequence in;

        // Save the local variable stack
        final LocalVariable mark = context.markLocalVariables(false);
        final Sequence resultSequence = new ValueSequence(unordered);
        try {
            in = inputSequence.eval(contextSequence, contextItem);

            // Declare the Window variable
            windowVar = createVariable(varName);
            windowVar.setSequenceType(sequenceType);
            context.declareVariableBinding(windowVar);

            registerUpdateListener(in);

            // Declare WindowCondition variables
            final WindowConditionVariables windowStartConditionVariables = declareWindowConditionVariables(context, windowStartCondition);
            final WindowConditionVariables windowEndConditionVariables = declareWindowConditionVariables(context, windowEndCondition);

            // Save the current context document set to the variable as a hint
            // for path expressions occurring in the "return" clause.
            if (in instanceof NodeSet) {
                windowVar.setContextDocs(in.getDocumentSet());
            } else {
                windowVar.setContextDocs(null);
            }

            //TODO(AR) is this required?
//            //Type.EMPTY is *not* a subtype of other types ;
//            //the tests below would fail without this prior cardinality check
//            if (in.isEmpty() && sequenceType != null &&
//                    !sequenceType.getCardinality().isSuperCardinalityOrEqualOf(Cardinality.EMPTY_SEQUENCE)) {
//                throw new XPathException(this, ErrorCodes.XPTY0004,
//                        "Invalid cardinality for variable $" + varName +
//                                ". Expected " + sequenceType.getCardinality().getHumanDescription() +
//                                ", got " + in.getCardinality().getHumanDescription());
//            }

            // when `window` is not null, we have started
            Sequence window = null;
            int windowStartIdx = -1;

            Sequence startPreviousItem = Sequence.EMPTY_SEQUENCE;
            Sequence startNextItem = Sequence.EMPTY_SEQUENCE;
            Sequence endPreviousItem = Sequence.EMPTY_SEQUENCE;

            for (int i = 0; i < in.getItemCount(); i++) {

                final Item currentItem = in.itemAt(i);

                // if we have NOT started, check if the start-when condition is true
                if (window == null) {

                    // set the start-item
                    if (windowStartConditionVariables.currentItem != null) {
                        windowStartConditionVariables.currentItem.setValue(currentItem.toSequence());
                    }

                    // set the start-position
                    if (windowStartConditionVariables.posVar != null) {
                        windowStartConditionVariables.posVar.setValue(new IntegerValue(i+1));
                    }

                    // set the start-previous-item
                    if (windowStartConditionVariables.previousItem != null) {
                        windowStartConditionVariables.previousItem.setValue(startPreviousItem);
                    }

                    // set the start-next-item
                    if (windowStartConditionVariables.nextItem != null) {
                        windowStartConditionVariables.nextItem.setValue(startNextItem);
                    }

                    // check if the start-when condition is true
                    final Sequence startWhen = windowStartCondition.getWhenExpression().eval(contextSequence, contextItem);
                    if (startWhen.effectiveBooleanValue()) {

                        // signal we have started
                        window = new ValueSequence(false);
                        windowStartIdx = i;

                    } else {
                        // remember the start-previous-item
                        startPreviousItem = currentItem.toSequence();
                    }
                }

                if (window != null) {
                    // if we have started...

                    // add the currentItem to the Window
                    window.add(currentItem);

                    // remember the start-next-item
                    if (window.getItemCount() == 2) {
                        startNextItem = currentItem.toSequence();
                    }

                    if (windowEndConditionVariables != null) {
                        // set the end-item
                        if (windowEndConditionVariables.currentItem != null) {
                            windowEndConditionVariables.currentItem.setValue(currentItem.toSequence());
                        }

                        // set the end-position
                        if (windowEndConditionVariables.posVar != null) {
                            windowEndConditionVariables.posVar.setValue(new IntegerValue(i + 1));
                        }

                        // set the end-previous-item
                        if (windowEndConditionVariables.previousItem != null) {
                            windowEndConditionVariables.previousItem.setValue(endPreviousItem);
                        }
                    }

                    // check if the end-when condition is true
                    final boolean endWhen;
                    if (windowEndCondition != null) {
                        endWhen = windowEndCondition.getWhenExpression().eval(contextSequence, contextItem).effectiveBooleanValue();
                    } else if (i > windowStartIdx + 1) {
                        endWhen = windowStartCondition.getWhenExpression().eval(contextSequence, contextItem).effectiveBooleanValue();
                    } else {
                        endWhen = false;
                    }
                    if (endWhen) {

                        // TODO(AR) end-next-item

                        if (window != null) {
                            // eval the return expression on the window binding
                            returnEvalWindowBinding(windowVar, window,resultSequence);

                            // reset the window
                            window = null;
                            startPreviousItem = Sequence.EMPTY_SEQUENCE;
                            startNextItem = Sequence.EMPTY_SEQUENCE;
                            endPreviousItem = Sequence.EMPTY_SEQUENCE;
                        }
                    } else {
                        // remember the end-previous-item
                        endPreviousItem = currentItem.toSequence();
                    }
                }
            }

            if (window != null && (windowEndCondition == null || !windowEndCondition.isOnly())) {
                // output the remaining binding sequence as a final window

                // eval the return expression on the window binding
                returnEvalWindowBinding(windowVar, window,resultSequence);

                // reset the window
                window = null;
                startPreviousItem = Sequence.EMPTY_SEQUENCE;
                startNextItem = Sequence.EMPTY_SEQUENCE;
                endPreviousItem = Sequence.EMPTY_SEQUENCE;
            }

        } finally {
            // restore the local variable stack
            context.popLocalVariables(mark, resultSequence);
        }

        clearContext(getExpressionId(), in);

        //TODO(AR) is this needed - borrowed from ForExpr.java eval
//        if (sequenceType != null) {
//            //Type.EMPTY is *not* a subtype of other types ; checking cardinality first
//            //only a check on empty sequence is accurate here
//            if (resultSequence.isEmpty() &&
//                    !sequenceType.getCardinality().isSuperCardinalityOrEqualOf(Cardinality.EMPTY_SEQUENCE)) {
//                throw new XPathException(this, ErrorCodes.XPTY0004,
//                    "Invalid cardinality for variable $" + varName + ". Expected " +
//                            sequenceType.getCardinality().getHumanDescription() +
//                            ", got " + Cardinality.EMPTY_SEQUENCE.getHumanDescription());
//            }
//            //TODO : ignore nodes right now ; they are returned as xs:untypedAtomicType
//            if (!Type.subTypeOf(sequenceType.getPrimaryType(), Type.NODE)) {
//                if (!resultSequence.isEmpty() &&
//                        !Type.subTypeOf(resultSequence.getItemType(),
//                                sequenceType.getPrimaryType())){
//                    throw new XPathException(this, ErrorCodes.XPTY0004,
//                        "Invalid type for variable $" + varName +
//                                ". Expected " + Type.getTypeName(sequenceType.getPrimaryType()) +
//                                ", got " +Type.getTypeName(resultSequence.getItemType()));
//                }
//                //trigger the old behaviour
//            } else {
//                var.checkType();
//            }
//        }
        setActualReturnType(resultSequence.getItemType());

        context.expressionEnd(this);
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", resultSequence);
        }

        return resultSequence;
    }

    private void returnEvalWindowBinding(final LocalVariable windowVar, final Sequence window, final Sequence resultSequence) throws XPathException {
        // set the binding for the window
        windowVar.setValue(new ValueSequence(window, false));

        // eval the return expression on the window binding
        resultSequence.addAll(returnExpr.eval(null, null));

        // free resources
        windowVar.destroy(context, resultSequence); // TODO(AR) is this in the correct place?
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
        //TODO : QuantifiedExpr
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
        result.append(this.getWindowType() == WindowType.TUMBLING_WINDOW ? "tumbling window " : "sliding window ");
        result.append("$").append(varName);
        if (sequenceType != null) {
            result.append(" as ").append(sequenceType);
        }
        result.append(" in ");
        result.append(inputSequence.toString());
        result.append(" ");
        result.append("start ").append(windowStartCondition.toString());
        if (windowEndCondition != null) {
            result.append(" end ").append(windowEndCondition.toString());
        }
        //TODO : QuantifiedExpr
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

    private static @Nullable
    WindowConditionVariables declareWindowConditionVariables(final XQueryContext context, @Nullable final WindowCondition windowCondition) throws XPathException {
        if (windowCondition == null) {
            return null;
        }
        return new WindowConditionVariables(
                declareWindowConditionVariable(context, windowCondition.getCurrentItem(), Type.ITEM, new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE)),
                declareWindowConditionVariable(context, windowCondition.getPosVar(), Type.INTEGER, POSITIONAL_VAR_TYPE),
                declareWindowConditionVariable(context, windowCondition.getPreviousItem(), Type.ITEM, new SequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE)),
                declareWindowConditionVariable(context, windowCondition.getNextItem(), Type.ITEM, new SequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE))
        );
    }

    private static @Nullable
    LocalVariable declareWindowConditionVariable(final XQueryContext context, @Nullable final QName variableName, final int staticType, final SequenceType sequenceType) throws XPathException {
        if (variableName == null) {
            return null;
        }
        final LocalVariable windowConditionVariable = new LocalVariable(variableName);
        windowConditionVariable.setSequenceType(sequenceType);
        windowConditionVariable.setStaticType(staticType);
        context.declareVariableBinding(windowConditionVariable);
        return windowConditionVariable;
    }

    private static class WindowConditionVariables {
        final @Nullable
        LocalVariable currentItem;
        final @Nullable
        LocalVariable posVar;
        final @Nullable
        LocalVariable previousItem;
        final @Nullable
        LocalVariable nextItem;

        private WindowConditionVariables(@Nullable final LocalVariable currentItem, @Nullable final LocalVariable posVar, @Nullable final LocalVariable previousItem, @Nullable final LocalVariable nextItem) {
            this.currentItem = currentItem;
            this.posVar = posVar;
            this.previousItem = previousItem;
            this.nextItem = nextItem;
        }
    }
}