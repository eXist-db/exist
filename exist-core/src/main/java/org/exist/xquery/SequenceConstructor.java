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

import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * An XQuery sequence constructor ",". For example, ($a, $b) constructs a new
 * sequence containing items $a and $b.
 * 
 * @author wolf
 */
public class SequenceConstructor extends PathExpr {

    public SequenceConstructor(final XQueryContext context) {
        super(context);
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        inPredicate = (contextInfo.getFlags() & IN_PREDICATE) > 0;
        unordered = (contextInfo.getFlags() & UNORDERED) > 0;
        contextId = contextInfo.getContextId();
        int staticType = Type.ANY_TYPE;
        for (final Expression expr : steps) {
            //Create a new context info because each sequence expression could modify it (add/remove flags...)
            final AnalyzeContextInfo info = new AnalyzeContextInfo(contextInfo);
            expr.analyze(info);
            if (staticType == Type.ANY_TYPE) {
                staticType = info.getStaticReturnType();
            } else if (staticType != Type.ITEM && staticType != info.getStaticReturnType()) {
                staticType = Type.ITEM;
            }
        }
        contextInfo.setStaticReturnType(staticType);
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
        final ValueSequence result = new ValueSequence();
        result.keepUnOrdered(unordered);
        for(final Expression step : steps) {
            context.pushDocumentContext();
            try {
                final Sequence temp = step.eval(contextSequence, contextItem);
                if (temp != null && !temp.isEmpty()) {
                    result.addAll(temp);
                }
            } finally {
                context.popDocumentContext();
            }
        }
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }
        return result;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("(");
        dumper.startIndent();
        boolean moreThanOne = false;
        for(final Expression step : steps) {
            if (moreThanOne) {
                dumper.display(", ");
            }
            moreThanOne = true;
            step.dump(dumper);
        }
        dumper.endIndent();
        dumper.nl().display(")");
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("( ");
        boolean moreThanOne = false;
        for (final Expression step : steps) {
            if (moreThanOne) {
                result.append(", ");
            }
            moreThanOne = true;
            result.append(step.toString());
        }
        result.append(" )");
        return result.toString();
    }

    /**
     * Add another PathExpr to this object's expression list.
     * Performs a static check, if return type is a function type other than array.
     * see #id-content section 1.e.i and 1.e.ii
     *
     * @throws XPathException when Path returns a function type
     * @param path A path to add to this path
     */
    public void addPathIfNotFunction(final PathExpr path) throws XPathException {
        final int retType = path.returnsType();
        if (Type.subTypeOf(retType, Type.FUNCTION) && retType != Type.ARRAY_ITEM) {
            throw new XPathException(path, ErrorCodes.XQTY0105, "Function types are not allowed in element content. Got " + Type.getTypeName(retType));
        }
        super.addPath(path);
    }

    @Override
    public int returnsType() {
        return Type.ITEM;
    }

    @Override
    public Cardinality getCardinality() {
        return Cardinality.ZERO_OR_MORE;
    }

    @Override
    public boolean allowMixedNodesInReturn() {
        return true;
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
    }
}
