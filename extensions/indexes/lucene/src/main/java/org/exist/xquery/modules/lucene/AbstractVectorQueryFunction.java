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

import org.exist.dom.QName;
import org.exist.dom.persistent.NodeSet;
import org.exist.indexing.lucene.LuceneIndexWorker;
import org.exist.storage.ElementValue;
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

    /**
     * The elements a search can look among for the step whose predicate holds the call, when those are all the step
     * selects from documents: the name of a step on the descendant axis. See {@link #canOptimizeSequence(Sequence)}.
     */
    @Nullable protected QName stepName = null;

    /** Whether the call is in a predicate of a variable, whose value is the nodes the predicate filters. */
    private boolean filtersVariable = false;

    AbstractVectorQueryFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        // Pass a clone to super, not contextInfo itself: Function.analyze() mutates it
        // (contextInfo.setParent(this)), and corrupting the caller's shared contextInfo breaks
        // context-id tracking for whatever reads it afterward. Mirrors Query#analyze.
        super.analyze(new AnalyzeContextInfo(contextInfo));
        stepName = null;
        filtersVariable = false;
        final Expression outerExpr = contextInfo.getContextStep();
        if (outerExpr instanceof final LocationStep step) {
            final NodeTest test = step.getTest();
            if ((step.getAxis() == Constants.DESCENDANT_AXIS || step.getAxis() == Constants.DESCENDANT_SELF_AXIS)
                    && !test.isWildcardTest() && test.getName() != null) {
                stepName = new QName(test.getName(), ElementValue.ELEMENT);
            }
        } else if (outerExpr instanceof final FilteredExpression filtered) {
            filtersVariable = filtered.getExpression() instanceof VariableReference;
        }
    }

    // === Optimizable: single KNN search over the whole candidate step, see class javadoc ===

    /**
     * A vector query finds the k nearest of the nodes the step selects, which evaluating it as written always does.
     * Pre-selecting it searches before the step, and keeps the k nearest of whatever that search looks among, so it
     * gives the same answer only if the search looks among exactly the nodes the step selects. That can be known for
     * two forms, and saves selecting the nodes: a variable's value, and a named descendant step from documents, whose
     * nodes are that name's index entries in those documents. Any other form is evaluated as written.
     */
    @Override
    public Sequence canOptimizeSequence(final Sequence contextSequence) {
        return filtersVariable || (stepName != null && onlyDocuments(contextSequence))
                ? contextSequence : Sequence.EMPTY_SEQUENCE;
    }

    private static boolean onlyDocuments(final Sequence sequence) {
        if (!sequence.isPersistentSet()) {
            return false;
        }
        try {
            return LuceneIndexWorker.onlyDocuments(sequence.toNodeSet());
        } catch (final XPathException e) {
            return false;
        }
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
    public boolean isFilter() {
        // the k nearest of the nodes given: which nodes are kept depends on the others
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
     * True if any argument from {@code fromIndex} onward varies per candidate node because it
     * depends on the context item (e.g. {@code .}, or anything derived from it, such as
     * {@code ft:embed(.)} as a per-candidate "vector" argument). A reference to a variable does
     * not count: a vector bound outside the predicate is the same for every candidate. Where its
     * value changes, as with a {@code for} variable, in a {@code where} clause or between calls of
     * a function, the path is evaluated again, and each evaluation re-runs
     * {@link #preSelect(Sequence, boolean)}, which resets the previous result (GH-2204). The same
     * holds for ft:query, ft:query-field and ngram:contains. If an argument does vary, the expression cannot be bulk-evaluated via
     * {@link #preSelect(Sequence, boolean)}: its
     * value would then vary per candidate, but {@link #evalArgs} evaluates every tail argument
     * exactly once, with {@code contextItem=null} — the same class of bug fixed for arg0 in
     * {@link QueryVector#canOptimizeSequence(Sequence)}, here for the remaining arguments.
     */
    protected boolean anyArgVariesPerCandidate(final int fromIndex) {
        for (int i = fromIndex; i < getArgumentCount(); i++) {
            final Expression arg = getArgument(i);
            if (Dependency.dependsOn(arg, Dependency.CONTEXT_ITEM)) {
                return true;
            }
        }
        return false;
    }
}
