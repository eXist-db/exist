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
package org.exist.xquery.modules.lucene;

import org.exist.dom.persistent.NodeSet;
import org.exist.xquery.*;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.value.*;

import javax.annotation.Nullable;

/**
 * Shared machinery for {@link QueryVector} (ft:query-vector) and {@link QueryFieldVector}
 * (ft:query-field-vector): KNN vector-search functions that take {@code (..., vector, k?,
 * options?)}, with {@code vector} and {@code k} always the last-but-one/two arguments and
 * {@code options} always last.
 *
 * <p>Implements {@link Optimizable} so that, when used as a path predicate (e.g.
 * {@code collection(...)//article[ft:query-vector(., $vec, $k)]} or
 * {@code article[ft:query-field-vector("embedding", $vec, $k)]}), the query engine wraps the
 * enclosing step in {@code (#exist:optimize#)} and calls {@code preSelect(Sequence, boolean)}
 * <em>once</em> for the whole candidate node set, rather than evaluating the predicate — and
 * re-running the KNN search — separately for every candidate node. Without this, {@code k} is
 * only honored by the direct-call form: a per-node evaluation restricts each search to that
 * single node's own document, and when every candidate is its own document, "the k nearest
 * neighbours of a 1-node set" is trivially that node itself, so the predicate is always true.
 * See https://github.com/eXist-db/exist/issues/6738</p>
 */
abstract class AbstractVectorQueryFunction extends BasicFunction implements Optimizable {

    /** Cached result of {@code preSelect(Sequence, boolean)}, consumed by {@link #eval(Sequence, Item)}. */
    @Nullable protected NodeSet preselectResult = null;

    AbstractVectorQueryFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    // === Optimizable: single KNN search over the whole candidate step, see class javadoc ===

    /**
     * Default: always optimizable — correct for {@link QueryFieldVector}, whose "field" argument
     * is a plain string with nothing that could diverge from {@code contextSequence}, and whose
     * search domain (any node in the candidate sequence is eligible) is resolved dynamically
     * inside {@code preSelect()} rather than restricted by an AST-visible qname, unlike ft:query.
     * {@link QueryVector} overrides this: its "nodes" argument, if it isn't provably {@code .},
     * must not have {@code contextSequence} silently substituted for its real value.
     */
    @Override
    public Sequence canOptimizeSequence(final Sequence contextSequence) {
        return contextSequence;
    }

    @Override
    public boolean optimizeOnSelf() {
        return true;
    }

    @Override
    public boolean optimizeOnChild() {
        return false;
    }

    @Override
    public int getOptimizeAxis() {
        return Constants.DESCENDANT_SELF_AXIS;
    }

    @Override
    public Sequence eval(final Sequence contextSequence, @Nullable final Item contextItem) throws XPathException {
        final Sequence effectiveContextSequence = contextItem != null ? contextItem.toSequence() : contextSequence;
        if (preselectResult != null) {
            if (effectiveContextSequence == null) {
                return Sequence.EMPTY_SEQUENCE;
            }
            return preselectResult.selectAncestorDescendant(effectiveContextSequence.toNodeSet(), NodeSet.DESCENDANT,
                    true, getContextId(), true);
        }
        return super.eval(effectiveContextSequence, contextItem);
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        if (!postOptimization) {
            preselectResult = null;
        }
    }

    /**
     * Evaluates arguments {@code [fromIndex, argCount)} against a bulk {@code contextSequence},
     * for use by {@code preSelect()}. Arguments below {@code fromIndex} are left {@code null} in
     * the result: preSelect derives the candidate set (and, for {@link QueryVector}, the "nodes"
     * argument's value) from {@code contextSequence} itself — the step's full candidate set,
     * before the predicate applies — not by re-evaluating an argument that is typically just
     * {@code .}.
     */
    protected Sequence[] evalArgs(final Sequence contextSequence, final int fromIndex) throws XPathException {
        final int argCount = getArgumentCount();
        final Sequence[] out = new Sequence[argCount];
        for (int i = fromIndex; i < argCount; i++) {
            out[i] = getArgument(i).eval(contextSequence, null);
        }
        return out;
    }

    /** Parses the {@code k} argument (index 2, optional) from an args array shaped {@code (nodes|field, vector, k?, options?)}. */
    protected static int parseK(final Sequence[] args) throws XPathException {
        if (args.length >= 3 && !args[2].isEmpty()) {
            final int k = args[2].itemAt(0).toJavaObject(Integer.class);
            return k > 0 ? k : 10;
        }
        return 10;
    }

    /**
     * Parses the {@code options} argument (index 3, optional) from an args array shaped
     * {@code (nodes|field, vector, k?, options?)}. Unlike {@link Query#parseOptions}, an argument
     * that was provided but evaluated to {@code ()} is treated the same as one that wasn't
     * provided at all (defaults) rather than an error — pass {@code null} to
     * {@link QueryOptions#fromSequence}, not the empty sequence itself, which
     * {@code fromSequence} would instead reject as a wrongly-shaped options value.
     */
    protected QueryOptions parseOptionsArg(final Sequence[] args) throws XPathException {
        final Sequence optSeq = args.length >= 4 ? args[3] : null;
        return QueryOptions.fromSequence(context, this, optSeq == null || optSeq.isEmpty() ? null : optSeq);
    }

    protected static float[] arrayToFloats(final Sequence seq) throws XPathException {
        if (seq == null || seq.isEmpty() || seq.getItemType() != Type.ARRAY_ITEM) {
            return null;
        }
        final ArrayType arr = (ArrayType) seq.itemAt(0);
        final int n = arr.getSize();
        final float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            final Sequence item = arr.get(i);
            if (item.isEmpty()) {
                return null;
            }
            final Item it = item.itemAt(0);
            if (it instanceof NumericValue nv) {
                out[i] = (float) nv.getDouble();
            } else {
                out[i] = (float) Double.parseDouble(it.getStringValue());
            }
        }
        return out;
    }

    /**
     * True if any argument from {@code fromIndex} onward varies per candidate node — either
     * because it depends on a local iteration variable (e.g. a {@code for}-bound variable —
     * GH-2204; CONTEXT_VARS from an outer scope are static relative to the current iteration and
     * don't count), or because it depends on the context item itself (e.g. {@code .}, or anything
     * derived from it, such as {@code ft:embed(.)} as a per-candidate "vector" argument). Either
     * way the expression cannot be bulk-evaluated via {@link #preSelect(Sequence, boolean)}: its
     * value would then vary per candidate, but {@link #evalArgs} evaluates every tail argument
     * exactly once, with {@code contextItem=null} — the same class of bug fixed for arg0 in
     * {@link QueryVector#canOptimizeSequence(Sequence)}, here for the remaining arguments.
     */
    protected boolean anyArgVariesPerCandidate(final int fromIndex) {
        for (int i = fromIndex; i < getArgumentCount(); i++) {
            final Expression arg = getArgument(i);
            if (Dependency.dependsOnLocalVar(arg) || Dependency.dependsOn(arg, Dependency.CONTEXT_ITEM)) {
                return true;
            }
        }
        return false;
    }
}
