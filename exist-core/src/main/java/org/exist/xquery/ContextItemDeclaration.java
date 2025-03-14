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
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.util.Optional;

public class ContextItemDeclaration extends AbstractExpression implements RewritableExpression {

    private static final Logger LOG = LogManager.getLogger(ContextItemDeclaration.class);

    private final Optional<SequenceType> itemType;
    private final boolean external;
    private Optional<Expression> value;

    public ContextItemDeclaration(final XQueryContext context, final SequenceType itemType, final boolean external, final Expression value) {
        super(context);
        this.itemType = Optional.ofNullable(itemType);
        this.external = external;
        this.value = Optional.ofNullable(value);
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);

        if (value.isPresent()) {
            value.get().analyze(contextInfo);
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

        if (external) {

            //TODO(AR): how to set the context item externally? doesn't eXist-db do this by default anyway?

            // is there a default value
            if (value.isPresent()) {
                return value.get().eval(null, null);
            } else {
                return null;
            }
        } else {
            return value.get().eval(null, null);
        }
    }

    @Override
    public int returnsType() {
        return itemType.map(SequenceType::getPrimaryType)
                .orElseGet(() -> value.map(Expression::returnsType).orElse(Type.ITEM));
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.nl().display("declare context item", line);
        itemType.map(it -> dumper.display(" as ").display(it.toString()));
        if(external) {
            dumper.display(" external ");
        }
        if (value.isPresent()) {
            dumper.display(" := ");
            value.get().dump(dumper);
        }
        dumper.nl();
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("declare context item");
        itemType.map(it -> result.append(" as ").append(it));
        if(external) {
            result.append(" external ");
        }
        value.map(v -> result.append(" := ").append(v));
        return result.toString();
    }

    @Override
    public void replace(final Expression oldExpr, final Expression newExpr) {
        if (value.isPresent() && value.get() == oldExpr) {
            this.value = Optional.ofNullable(newExpr);
        }
    }

    @Override
    public void remove(final Expression oldExpr) throws XPathException {
    }

    @Override
    public Expression getPrevious(final Expression current) {
        return null;
    }

    @Override
    public Expression getFirst() {
        return null;
    }
}
