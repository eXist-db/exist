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
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.NodeSet;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneIndexWorker;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.exist.xquery.FunctionDSL.optManyParam;
import static org.exist.xquery.FunctionDSL.optParam;
import static org.exist.xquery.FunctionDSL.param;
import static org.exist.xquery.FunctionDSL.returnsOptMany;
import static org.exist.xquery.modules.lucene.LuceneModule.functionSignature;

/**
 * ft:query-vector(nodes, vector, k?, options?) — KNN vector search with node context.
 *
 * <p>See {@link AbstractVectorQueryFunction} for the shared {@link Optimizable} machinery and
 * why it's needed.</p>
 */
public class QueryVector extends AbstractVectorQueryFunction {

    private static final FunctionParameterSequenceType FS_PARAM_NODES = optManyParam("nodes", Type.NODE,
            "The node set to search (e.g. collection(...)//article). Document set and qnames are derived from this.");
    private static final FunctionParameterSequenceType FS_PARAM_VECTOR = param("vector", Type.ARRAY_ITEM,
            "Query vector as XQuery array of numbers, e.g. [1.0, 0.0, 0.0, 0.0].");
    private static final FunctionParameterSequenceType FS_PARAM_K = optParam("k", Type.INTEGER,
            "Number of nearest neighbours to return (default 10).");
    private static final FunctionParameterSequenceType FS_PARAM_OPTIONS = optParam("options", Type.ITEM,
            "Optional map with filter-query, filter, facets.");

    private static final String RETURN_DESC =
            "Nodes matching the vector query. The returned sequence follows XQuery node-sequence semantics; "
                    + "to rank by similarity, sort explicitly with ft:score, e.g. "
                    + "'for $h in ft:query-vector(...) order by ft:score($h) descending return $h'.";

    final static FunctionSignature[] signatures = {
            functionSignature("query-vector",
                    "KNN vector search. Returns the k nodes nearest to the query vector. "
                            + "Vector field is resolved from index config.",
                    returnsOptMany(Type.NODE, RETURN_DESC),
                    FS_PARAM_NODES,
                    FS_PARAM_VECTOR),
            functionSignature("query-vector",
                    "KNN vector search with explicit k.",
                    returnsOptMany(Type.NODE, RETURN_DESC),
                    FS_PARAM_NODES,
                    FS_PARAM_VECTOR,
                    FS_PARAM_K),
            functionSignature("query-vector",
                    "KNN vector search with k and options (filter-query, filter, facets).",
                    returnsOptMany(Type.NODE, RETURN_DESC),
                    FS_PARAM_NODES,
                    FS_PARAM_VECTOR,
                    FS_PARAM_K,
                    FS_PARAM_OPTIONS)
    };

    /**
     * True when arg0 ("nodes") is provably a bare self-reference ({@code .}), matching the
     * predicate's own candidate. Set by {@link #analyze(AnalyzeContextInfo)}, consumed by
     * {@link #canOptimizeSequence(Sequence)}.
     */
    private boolean nodesArgIsSelf = false;

    /**
     * Creates a new QueryVector function instance.
     *
     * @param context the XQuery context
     * @param signature the function signature
     */
    public QueryVector(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    /**
     * Detects whether arg0 ("nodes") is a bare self-axis step, mirroring the same check
     * {@link Query#analyze(AnalyzeContextInfo)} performs for exactly the same reason: only in
     * that case is it safe for {@link #preSelect(Sequence, boolean)} to substitute the
     * predicate's own candidate sequence for arg0's value. See
     * {@link #canOptimizeSequence(Sequence)}.
     */
    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        // Pass a clone to super, not contextInfo itself: Function.analyze() mutates it
        // (contextInfo.setParent(this)), and corrupting the caller's shared contextInfo breaks
        // context-id tracking for whatever reads it afterward. Mirrors Query#analyze.
        super.analyze(new AnalyzeContextInfo(contextInfo));
        List<LocationStep> steps = BasicExpressionVisitor.findLocationSteps(getArgument(0));
        if (steps.isEmpty() && getArgument(0) instanceof LocationStep step) {
            steps = List.of(step);
        }
        nodesArgIsSelf = steps.size() == 1 && steps.getFirst() != null
                && steps.getFirst().getAxis() == Constants.SELF_AXIS;
    }

    /**
     * Only claims optimizability when arg0 ("nodes") is provably {@code .} ({@link #nodesArgIsSelf}).
     * {@link #preSelect(Sequence, boolean)} substitutes {@code contextSequence} for arg0's value
     * rather than re-evaluating it — correct precisely because {@code .} evaluated over a bulk
     * {@code contextSequence} returns that same sequence. For any other "nodes" expression (e.g.
     * a variable bound to an unrelated node set), that substitution would search the wrong
     * domain, so optimization must be refused here: {@link org.exist.xquery.pragmas.Optimize#eval}
     * then falls back to {@link AbstractVectorQueryFunction#eval(Sequence, Item)}'s plain path,
     * which correctly re-evaluates arg0 itself via the inherited {@link #eval(Sequence[], Sequence)}.
     */
    @Override
    public Sequence canOptimizeSequence(final Sequence contextSequence) {
        return nodesArgIsSelf ? super.canOptimizeSequence(contextSequence) : Sequence.EMPTY_SEQUENCE;
    }

    @Override
    public Sequence eval(final Sequence[] args, @Nullable final Sequence contextSequence) throws XPathException {
        final Sequence nodesSeq = args[0];
        if (nodesSeq == null || nodesSeq.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final float[] vector = arrayToFloats(args[1]);
        if (vector == null) {
            throw new XPathException(this, "Second argument must be an array of numbers");
        }

        final int k = parseK(args);
        final QueryOptions options = parseOptionsArg(args);

        final NodeSet nodes = nodesSeq.toNodeSet();
        return runSearch(nodes, nodes.getDocumentSet(), nodes, vector, k, options);
    }

    @Override
    public NodeSet preSelect(final Sequence contextSequence, final boolean useContext) throws XPathException {
        // the expression can be called multiple times, so we need to clear the previous preselectResult
        preselectResult = null;
        if (contextSequence == null || !contextSequence.isPersistentSet()) {
            // in-memory node sets won't have an index
            preselectResult = NodeSet.EMPTY_SET;
            return preselectResult;
        }

        final NodeSet nodes = contextSequence.toNodeSet();
        if (nodes == null || nodes.isEmpty()) {
            preselectResult = NodeSet.EMPTY_SET;
            return preselectResult;
        }

        // arg0 ("nodes") is deliberately not re-evaluated: canOptimizeSequence() only reaches
        // this point when arg0 is provably `.`, so the candidate set is contextSequence itself
        // (the step's full candidate set, before the predicate applies) — see
        // AbstractVectorQueryFunction#evalArgs and canOptimizeSequence() above.
        final Sequence[] tailArgs = evalArgs(contextSequence, 1);
        final float[] vector = arrayToFloats(tailArgs[1]);
        if (vector == null) {
            throw new XPathException(this, "Second argument must be an array of numbers");
        }
        final int k = parseK(tailArgs);
        final QueryOptions options = parseOptionsArg(tailArgs);

        final NodeSet contextSet = useContext ? nodes : null;
        final Sequence result = runSearch(nodes, contextSequence.getDocumentSet(), contextSet, vector, k, options);
        preselectResult = result.toNodeSet();
        return preselectResult;
    }

    /** The tail shared by {@link #eval(Sequence[], Sequence)} and {@link #preSelect(Sequence, boolean)}: resolve the index/qnames and run the KNN search. */
    private Sequence runSearch(final NodeSet nodes, final DocumentSet docs, @Nullable final NodeSet contextSet,
            final float[] vector, final int k, final QueryOptions options) throws XPathException {
        final LuceneIndexWorker index = (LuceneIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(LuceneIndex.ID);
        final List<QName> qnames = index != null ? resolveQNames(nodes, index) : getQNamesFromNodes(nodes);
        final PerformanceStats.IndexOptimizationLevel optimizationLevel =
                VectorSearchSupport.optimizationLevelForQNames(this, index, docs, qnames);
        return VectorSearchSupport.execute(this, context, index, optimizationLevel,
                () -> index.searchVector(getExpressionId(), docs, contextSet, qnames, vector, k, options));
    }

    /**
     * Declares {@link Dependency#CONTEXT_SET} without {@link Dependency#CONTEXT_ITEM} for the
     * common case (arg0 "nodes" is a bare node reference, typically {@code .}, that doesn't
     * itself depend on the context item). This is essential, not cosmetic: {@link PathExpr#eval}
     * chooses between evaluating a step once in bulk against the whole candidate sequence, or
     * iterating it once per context item, based on whether its dependencies include
     * {@code CONTEXT_ITEM}. Declaring {@code CONTEXT_ITEM} (the {@link Function} default) forces
     * per-item iteration — one KNN search per candidate document — which is exactly the bug this
     * class exists to fix: a per-document top-k search is never equivalent to a top-k search over
     * the whole candidate set. Mirrors {@link Query#getDependencies()}.
     */
    @Override
    public int getDependencies() {
        final Expression nodesArg = getArgument(0);
        if (Type.subTypeOf(nodesArg.returnsType(), Type.NODE)
                && !Dependency.dependsOn(nodesArg, Dependency.CONTEXT_ITEM)) {
            if (anyArgVariesPerCandidate(1)) {
                return Dependency.CONTEXT_SET | Dependency.CONTEXT_ITEM;
            }
            return Dependency.CONTEXT_SET;
        }
        return Dependency.CONTEXT_SET | Dependency.CONTEXT_ITEM;
    }

    private List<QName> resolveQNames(final NodeSet nodes, final LuceneIndexWorker index) throws XPathException {
        final List<QName> qnames = getQNamesFromNodes(nodes);
        if (!qnames.isEmpty()) {
            return qnames;
        }
        try {
            return index.getDefinedIndexes(null);
        } catch (IOException e) {
            throw new XPathException(this, "Failed to get index config: " + e.getMessage(), e);
        }
    }

    private static List<QName> getQNamesFromNodes(final NodeSet nodes) {
        final Set<QName> seen = new LinkedHashSet<>();
        for (int i = 0; i < nodes.getItemCount(); i++) {
            final org.exist.dom.persistent.NodeProxy np = nodes.get(i);
            if (np != null) {
                final QName qn = np.getQName();
                if (qn != null) {
                    seen.add(qn);
                }
            }
        }
        return new ArrayList<>(seen);
    }
}
