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
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.value.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Implements the XQuery 4.0 "for key", "for value", and "for key/value" clauses.
 *
 * <p>{@code for key $k in map-expr} iterates over the keys of a map.</p>
 * <p>{@code for value $v in map-expr} iterates over the values of a map.</p>
 * <p>{@code for key $k value $v in map-expr} iterates over key-value pairs.</p>
 */
public class ForKeyValueExpr extends BindingExpression {

    private final ClauseType clauseType;
    private QName positionalVariable = null;
    private QName valueVariable = null;
    private SequenceType valueSequenceType = null;

    public ForKeyValueExpr(final XQueryContext context, final ClauseType clauseType) {
        super(context);
        this.clauseType = clauseType;
    }

    public void setPositionalVariable(final QName variable) {
        positionalVariable = variable;
    }

    public void setValueVariable(final QName variable) {
        valueVariable = variable;
    }

    public void setValueSequenceType(final SequenceType type) {
        valueSequenceType = type;
    }

    @Override
    public ClauseType getType() {
        return clauseType;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        final LocalVariable mark = context.markLocalVariables(false);
        try {
            contextInfo.setParent(this);
            final AnalyzeContextInfo varContextInfo = new AnalyzeContextInfo(contextInfo);
            inputSequence.analyze(varContextInfo);
            final LocalVariable inVar = new LocalVariable(varName);
            inVar.setSequenceType(sequenceType);
            inVar.setStaticType(Type.ITEM);
            context.declareVariableBinding(inVar);
            if (valueVariable != null) {
                final LocalVariable valVar = new LocalVariable(valueVariable);
                valVar.setSequenceType(valueSequenceType);
                valVar.setStaticType(Type.ITEM);
                context.declareVariableBinding(valVar);
            }
            if (positionalVariable != null) {
                final LocalVariable posVar = new LocalVariable(positionalVariable);
                posVar.setSequenceType(POSITIONAL_VAR_TYPE);
                posVar.setStaticType(Type.INTEGER);
                context.declareVariableBinding(posVar);
            }

            final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
            newContextInfo.addFlag(SINGLE_STEP_EXECUTION);
            returnExpr.analyze(newContextInfo);
        } finally {
            context.popLocalVariables(mark);
        }
    }

    @Override
    public Sequence eval(Sequence contextSequence, final Item contextItem)
            throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES,
                    "CONTEXT SEQUENCE", contextSequence);
            }
        }
        context.expressionStart(this);

        final LocalVariable mark = context.markLocalVariables(false);
        final Sequence resultSequence = new ValueSequence(unordered);
        try {
            final Sequence in = inputSequence.eval(contextSequence, null);

            if (in.isEmpty()) {
                // Empty map produces no iterations
            } else if (in.getItemCount() != 1 || !(in.itemAt(0) instanceof AbstractMapType)) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                    "for " + clauseLabel() +
                    " expression requires a single map, got " +
                    Type.getTypeName(in.getItemType()));
            } else {
                final AbstractMapType map = (AbstractMapType) in.itemAt(0);
                final LocalVariable var = createVariable(varName);
                var.setSequenceType(sequenceType);
                context.declareVariableBinding(var);

                LocalVariable valVar = null;
                if (valueVariable != null) {
                    valVar = new LocalVariable(valueVariable);
                    valVar.setSequenceType(valueSequenceType);
                    context.declareVariableBinding(valVar);
                }

                LocalVariable at = null;
                if (positionalVariable != null) {
                    at = new LocalVariable(positionalVariable);
                    at.setSequenceType(POSITIONAL_VAR_TYPE);
                    context.declareVariableBinding(at);
                }

                final Sequence keys = map.keys();
                int pos = 0;
                try {
                    for (final SequenceIterator i = keys.iterate(); i.hasNext() && !WhileClause.isTerminated(); ) {
                        context.proceed(this);
                        final AtomicValue key = (AtomicValue) i.nextItem();
                        pos++;

                        final Sequence bindValue;
                        if (clauseType == ClauseType.FOR_VALUE) {
                            bindValue = map.get(key);
                        } else {
                            // FOR_KEY or FOR_KEY_VALUE: primary var is key
                            bindValue = key;
                        }
                        var.setValue(bindValue);

                        if (valVar != null) {
                            valVar.setValue(map.get(key));
                        }

                        if (positionalVariable != null) {
                            at.setValue(new IntegerValue(this, pos));
                        }
                        if (sequenceType != null) {
                            var.checkType();
                        }
                        if (valVar != null && valueSequenceType != null) {
                            valVar.checkType();
                        }

                        final Sequence returnResult;
                        if (returnExpr instanceof OrderByClause) {
                            returnResult = returnExpr.eval(bindValue, null);
                        } else {
                            returnResult = returnExpr.eval(null, null);
                        }
                        resultSequence.addAll(returnResult);
                        var.destroy(context, resultSequence);
                    }
                } catch (final WhileClause.WhileTerminationException e) {
                    // while clause signaled end of iteration
                }
                if (getPreviousClause() == null && WhileClause.isTerminated()) {
                    WhileClause.clearTerminated();
                }
            }
        } finally {
            context.popLocalVariables(mark, resultSequence);
        }

        if (callPostEval()) {
            final Sequence postResult = postEval(resultSequence);
            context.expressionEnd(this);
            if (context.getProfiler().isEnabled()) {
                context.getProfiler().end(this, "", postResult);
            }
            return postResult;
        }

        context.expressionEnd(this);
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", resultSequence);
        }
        return resultSequence;
    }

    private String clauseLabel() {
        switch (clauseType) {
            case FOR_KEY: return "key";
            case FOR_VALUE: return "value";
            case FOR_KEY_VALUE: return "key/value";
            default: return "key";
        }
    }

    private boolean callPostEval() {
        FLWORClause prev = getPreviousClause();
        while (prev != null) {
            switch (prev.getType()) {
                case LET:
                case FOR:
                case FOR_MEMBER:
                case FOR_KEY:
                case FOR_VALUE:
                case FOR_KEY_VALUE:
                    return false;
                case ORDERBY:
                case GROUPBY:
                    return true;
            }
            prev = prev.getPreviousClause();
        }
        return true;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("for " + clauseLabel() + " ", line);
        dumper.startIndent();
        dumper.display("$").display(varName);
        if (valueVariable != null) {
            dumper.display(" value $").display(valueVariable);
        }
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
        result.append("for ").append(clauseLabel()).append(" ");
        result.append("$").append(varName);
        if (valueVariable != null) {
            result.append(" value $").append(valueVariable);
        }
        if (sequenceType != null) {
            result.append(" as ").append(sequenceType);
        }
        result.append(" in ");
        result.append(inputSequence.toString());
        result.append(" ");
        if (returnExpr instanceof LetExpr) {
            result.append(" ");
        } else {
            result.append("return ");
        }
        result.append(returnExpr.toString());
        return result.toString();
    }

    @Override
    public Set<QName> getTupleStreamVariables() {
        final Set<QName> variables = new HashSet<>();
        final QName variable = getVariable();
        if (variable != null) {
            variables.add(variable);
        }
        if (valueVariable != null) {
            variables.add(valueVariable);
        }
        final LocalVariable startVar = getStartVariable();
        if (startVar != null) {
            variables.add(startVar.getQName());
        }
        return variables;
    }
}
