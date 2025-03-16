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
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * A global variable declaration (with: declare variable). Variable bindings within
 * for and let expressions are handled by {@link org.exist.xquery.ForExpr} and
 * {@link org.exist.xquery.LetExpr}.
 *
 * @author wolf
 */
public class VariableDeclaration extends AbstractExpression implements RewritableExpression {

    final QName qname;
    Optional<Expression> expression;
    SequenceType sequenceType = null;
    boolean analyzeDone = false;

    public VariableDeclaration(final XQueryContext context, final QName qname, final Expression expr) {
        super(context);
        this.qname = qname;
        this.expression = Optional.ofNullable(expr);
    }

    public QName getName() {
        return qname;
    }

    /**
     * Set the sequence type of the variable.
     *
     * @param type the sequence type
     */
    public void setSequenceType(final SequenceType type) {
        this.sequenceType = type;
    }

    public SequenceType getSequenceType() {
        return sequenceType;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        final Variable var = new VariableImpl(qname);
        var.setIsInitialized(false);

        if (!analyzeDone) {
            final Module[] modules = context.getModules(qname.getNamespaceURI());

            // can we find a module which declared this variable
            final Module myModule = findDeclaringModule(modules);

            if (myModule != null) {
                // NOTE: duplicate var declaration is handled in the XQuery tree parser, and may throw XQST0049
                myModule.declareVariable(var);
            } else {
                // NOTE: duplicate var declaration is handled in the XQuery tree parser, and may throw XQST0049
                context.declareGlobalVariable(var);
            }
            analyzeDone = true;
        }
        analyzeExpression(contextInfo);
        var.setIsInitialized(true);
    }

    private @Nullable Module findDeclaringModule(@Nullable final Module[] modules) {
        if (modules != null && modules.length > 0) {
            for (final Module module : modules) {
                if (module instanceof ExternalModule) {
                    if (((ExternalModuleImpl)module).getSource().equals(context.getSource())) {
                        return module;
                    }
                } else if (module instanceof InternalModule) {
                    //TODO(AR) implement
                    throw new UnsupportedOperationException("TODO(AR) implement");
                    //context.getSource().pathOrContentOrShortIdentifier().equals(module.loc)
                }
            }
        }

        return null;
    }

    /**
     * Analyze just the expression. For dynamically imported modules this needs to be done one time
     * after import.
     *
     * @param contextInfo context information
     * @throws XPathException in case of static error
     */
    public void analyzeExpression(final AnalyzeContextInfo contextInfo) throws XPathException {
        if (expression.isPresent()) {
            expression.get().analyze(contextInfo);
        }
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            }
        }

        context.pushInScopeNamespaces(false);
        try {
            final Module myModule = findDeclaringModule(context.getRootModules(qname.getNamespaceURI()));

            context.pushDocumentContext();
            try {
                context.prologEnter(this);
                if (expression.isPresent()) {
                    // normal variable declaration or external var with default value
                    final Sequence seq = expression.get().eval(contextSequence, null);
                    final Variable var;
                    if (myModule != null) {
                        var = myModule.declareVariable(qname, seq);
                        var.setSequenceType(sequenceType);
                        var.checkType();
                    } else {
                        var = new VariableImpl(qname);
                        var.setValue(seq);
                        var.setSequenceType(sequenceType);
                        var.checkType();
                        context.declareGlobalVariable(var);
                    }

                    if (context.getProfiler().isEnabled()) {
                        //Note : that we use seq but we return Sequence.EMPTY_SEQUENCE
                        context.getProfiler().end(this, "", seq);
                    }
                } else {
                    // external variable without default
                    final Variable external = context.resolveGlobalVariable(qname);
                    if (external == null) {
                        // If no value is provided for the variable by the external environment, and VarDefaultValue
                        // is not specified, then a dynamic error is raised [err:XPDY0002]
                        throw new XPathException(this, ErrorCodes.XPDY0002, "no value specified for external variable " +
                                qname);
                    }
                    external.setSequenceType(sequenceType);

                    if (myModule != null) {
                        // declare on module
                        myModule.declareVariable(external);
                    }
                }
            } finally {
                context.popDocumentContext();
            }
        } finally {
            context.popInScopeNamespaces();
        }

        return null;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.nl().display("declare variable $").display(qname.toString(), line);
        if (sequenceType != null) {
            dumper.display(" as ").display(sequenceType.toString());
        }
        dumper.display("{");
        dumper.startIndent();
        expression.ifPresent(e -> e.dump(dumper));
        dumper.endIndent();
        dumper.nl().display("}").nl();
    }

    public
    @Override String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("declare variable $").append(qname);
        if (sequenceType != null) {
            result.append(" as ").append(sequenceType);
        }
        if (expression.isPresent()) {
            result.append("{");
            result.append(expression);
            result.append("}");
        }
        return result.toString();
    }

    @Override
    public int returnsType() {
        return expression.map(Expression::returnsType).orElse(Type.ITEM);
    }

    @Override
    public int getDependencies() {
        return Dependency.CONTEXT_SET;
    }

    @Override
    public Cardinality getCardinality() {
        return expression.isPresent() ? expression.get().getCardinality() : Cardinality.ONE_OR_MORE;
    }

    public Optional<Expression> getExpression() {
        return expression;
    }

    /* RewritableExpression API */

    @Override
    public void replace(final Expression oldExpr, final Expression newExpr) {
        if (expression.isPresent() && expression.get() == oldExpr) {
            this.expression = Optional.ofNullable(newExpr);
        }
    }

    @Override
    public Expression getPrevious(final Expression current) {
        return null;
    }

    @Override
    public Expression getFirst() {
        return null;
    }

    @Override
    public void remove(final Expression oldExpr) throws XPathException {
    }

    /* END RewritableExpression API */

    @Override
    public boolean allowMixedNodesInReturn() {
        return true;
    }

    @Override
    public void accept(final ExpressionVisitor visitor) {
        visitor.visitVariableDeclaration(this);
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        expression.ifPresent(e -> e.resetState(postOptimization));
        if (!postOptimization) {
            analyzeDone = false;
        }
    }
}
