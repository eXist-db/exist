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
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implements a count clause inside a FLWOR expressions.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 * @author <a href="mailto:gabriele@strumenta.com">Gabriele Tomassetti</a>
 */
public class CountClause extends AbstractFLWORClause {

    private static final SequenceType countVarType = new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE);

    final QName varName;

    // the count itself
    private long count = 0;
    private int step = 1;

    public CountClause(final XQueryContext context, final QName varName) {
        super(context);
        this.varName = varName;
    }

    @Override
    public ClauseType getType() {
        return ClauseType.COUNT;
    }

    public QName getVarName() {
        return varName;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        unordered = (contextInfo.getFlags() & UNORDERED) > 0;

        // Save the local variable stack
        final LocalVariable mark = context.markLocalVariables(false);
        try {
            final AnalyzeContextInfo varContextInfo = new AnalyzeContextInfo(contextInfo);

            // Declare the count variable
            final LocalVariable countVar = new LocalVariable(varName);
            countVar.setSequenceType(countVarType);
            countVar.setStaticType(varContextInfo.getStaticReturnType());
            context.declareVariableBinding(countVar);

            // analyze the return expression
            final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
            returnExpr.analyze(newContextInfo);

        } finally {
            // restore the local variable stack
            context.popLocalVariables(mark);
        }
    }

    @Override
    public Sequence preEval(final Sequence seq) throws XPathException {
        // determine whether to count down or up
        this.step = hasPreviousOrderByDescending() ? -1 : 1;

        // get the count start position
        if (this.step == 1) {
            this.count = 0;
        } else {
            this.count = seq.getItemCountLong() + 1;
        }

        return super.preEval(seq);
    }

    private boolean hasPreviousOrderByDescending() {
        FLWORClause prev = getPreviousClause();
        while (prev != null) {
            switch (prev.getType()) {
                case LET, GROUPBY, FOR -> {
                    return false;
                }
                case ORDERBY -> {
                    return isDescending(((OrderByClause) prev).getOrderSpecs());
                }
                default -> prev = prev.getPreviousClause();
            }
        }
        return true;
    }
    private boolean isDescending(final List<OrderSpec> orderSpecs) {
        for (final OrderSpec orderSpec : orderSpecs) {
            if ((orderSpec.getModifiers() & OrderSpec.DESCENDING_ORDER) == OrderSpec.DESCENDING_ORDER) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
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

        final Sequence resultSequence = new ValueSequence(unordered);

        // update the count
        count = count + step;

        // Save the local variable stack
        final LocalVariable mark = context.markLocalVariables(false);
        try {

            // Declare the count variable
            final LocalVariable countVar = createVariable(varName);
            countVar.setSequenceType(countVarType);
            context.declareVariableBinding(countVar);

            // set the binding for the count
            countVar.setValue(new IntegerValue(count));

            // eval the return expression on the window binding
            resultSequence.addAll(returnExpr.eval(null, null));

            // free resources
            countVar.destroy(context, resultSequence);

        } finally {
            // restore the local variable stack
            context.popLocalVariables(mark, resultSequence);
        }

        setActualReturnType(resultSequence.getItemType());

        context.expressionEnd(this);
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", resultSequence);
        }

        return resultSequence;
    }

    @Override
    public Sequence postEval(Sequence seq) throws XPathException {
        if (returnExpr instanceof FLWORClause flworClause) {
            seq = flworClause.postEval(seq);
        }
        return super.postEval(seq);
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("count", this.getLine());
        dumper.startIndent();
        dumper.display(this.varName);
        dumper.endIndent().nl();
    }

    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("count ");
        result.append("$").append(this.varName);
        return result.toString();
    }

    @Override
    public void accept(final ExpressionVisitor visitor) {
        visitor.visitCountClause(this);
    }

    @Override
    public Set<QName> getTupleStreamVariables() {
        final Set<QName> variables = new HashSet<>();

        final QName variable = getVarName();
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
