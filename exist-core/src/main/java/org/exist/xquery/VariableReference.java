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
import org.exist.dom.persistent.DocumentSet;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import javax.annotation.Nullable;

/**
 * Represents a reference to an in-scope variable.
 *
 * @author wolf
 */
public class VariableReference extends AbstractExpression {

    private final QName qname;
    private Expression parent;

    public VariableReference(final XQueryContext context, final QName qname) {
        super(context);
        this.qname = qname;
    }

    public QName getName() {
        return qname;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        this.parent = contextInfo.getParent();
        Variable var = null;
        try {
            var = getVariable(contextInfo);
        } catch (final XPathException e) {
            // ignore: variable might not be known yet
            return;
        }
        if (var == null) {
            throw new XPathException(this, ErrorCodes.XPST0008,
                    "Variable '$" + qname + "' is not declared.");
        }
        if (!var.isInitialized()) {
            throw new XPathException(this, ErrorCodes.XQST0054,
                    "variable declaration of '$" + qname + "' cannot " +
                            "be executed because of a circularity.");
        }
        contextInfo.setStaticReturnType(var.getStaticType());
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
        final Variable var = getVariable(new AnalyzeContextInfo(parent, 0));
        if (var == null) {
            throw new XPathException(this, ErrorCodes.XPST0008, "Variable '$" + qname + "' is not declared.");
        }
        final Sequence seq = var.getValue();
        if (seq == null) {
            throw new XPathException(this, ErrorCodes.XPDY0002, "undefined value for variable '$" + qname + "'");
        }
        final Sequence result = seq;
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }
        return result;
    }

    protected Variable getVariable(@Nullable final AnalyzeContextInfo contextInfo) throws XPathException {
        try {
            return context.resolveVariable(contextInfo, qname);
        } catch (final XPathException e) {
            e.setLocation(line, column);
            throw e;
        }
    }

    public DocumentSet preselect(final DocumentSet in_docs) {
        return in_docs;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display('$').display(qname);
    }

    @Override
    public String toString() {
        return "$" + qname;
    }

    @Override
    public int returnsType() {
        try {
            final Variable var = context.resolveVariable(qname);
            if (var != null) {
                if (var.getValue() != null) {
                    final int type = var.getValue().getItemType();
                    return type;
                } else {
                    return var.getType();
                }
            }
        } catch (final XPathException e) {
            //TODO : don't ignore ? -pb
        }
        return Type.ITEM;
    }

    @Override
    public int getDependencies() {
        try {
            final Variable var = context.resolveVariable(qname);
            if (var != null) {
                final int deps = var.getDependencies(context);
                return deps;
            }
        } catch (final XPathException e) {
            //TODO : don't ignore ? -pb
        }
        return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM;
    }

    @Override
    public Cardinality getCardinality() {
        try {
            final Variable var = context.resolveVariable(qname);
            if (var != null && var.getValue() != null) {
                final Cardinality card = var.getValue().getCardinality();
                return card;
            }
        } catch (final XPathException e) {
            //TODO : don't ignore ?
        }
        return Cardinality.ZERO_OR_MORE; // unknown cardinality
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
    }

    @Override
    public void accept(final ExpressionVisitor visitor) {
        visitor.visitVariableReference(this);
    }

    @Override
    public boolean allowMixedNodesInReturn() {
        return true;
    }

    @Override
    public Expression getParent() {
        return parent;
    }
}
