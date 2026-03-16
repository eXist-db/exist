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
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.value.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Implements the XQuery 4.0 "for member" clause in FLWOR expressions.
 *
 * <p>{@code for member $m in $array-expr} iterates over the members of an array,
 * binding each member (which is a sequence) to the variable.</p>
 */
public class ForMemberExpr extends BindingExpression {

    private QName positionalVariable = null;

    public ForMemberExpr(final XQueryContext context) {
        super(context);
    }

    public void setPositionalVariable(final QName variable) {
        positionalVariable = variable;
    }

    @Override
    public ClauseType getType() {
        return ClauseType.FOR_MEMBER;
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

            if (!(in instanceof ArrayType)) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                    "for member expression requires an array, got " +
                    Type.getTypeName(in.getItemType()));
            }

            final ArrayType array = (ArrayType) in;
            final LocalVariable var = createVariable(varName);
            var.setSequenceType(sequenceType);
            context.declareVariableBinding(var);

            LocalVariable at = null;
            if (positionalVariable != null) {
                at = new LocalVariable(positionalVariable);
                at.setSequenceType(POSITIONAL_VAR_TYPE);
                context.declareVariableBinding(at);
            }

            try {
                for (int i = 0; i < array.getSize() && !WhileClause.isTerminated(); i++) {
                    context.proceed(this);
                    final Sequence member = array.get(i);
                    var.setValue(member);
                    if (positionalVariable != null) {
                        at.setValue(new IntegerValue(this, i + 1));
                    }
                    if (sequenceType == null) {
                        var.checkType();
                    }

                    final Sequence returnResult;
                    if (returnExpr instanceof OrderByClause) {
                        returnResult = returnExpr.eval(member, null);
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

    private boolean callPostEval() {
        FLWORClause prev = getPreviousClause();
        while (prev != null) {
            switch (prev.getType()) {
                case LET:
                case FOR:
                case FOR_MEMBER:
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
        dumper.display("for member ", line);
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
        result.append("for member ");
        result.append("$").append(varName);
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
        final LocalVariable startVar = getStartVariable();
        if (startVar != null) {
            variables.add(startVar.getQName());
        }
        return variables;
    }
}
