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
import org.exist.storage.vector.VectorOperationMetrics;
import org.exist.xquery.*;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.AbstractMapType;
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
 */
public class QueryVector extends BasicFunction {

    private static final FunctionParameterSequenceType FS_PARAM_NODES = optManyParam("nodes", Type.NODE,
            "The node set to search (e.g. collection(...)//article). Document set and qnames are derived from this.");
    private static final FunctionParameterSequenceType FS_PARAM_VECTOR = param("vector", Type.ARRAY_ITEM,
            "Query vector as XQuery array of numbers, e.g. [1.0, 0.0, 0.0, 0.0].");
    private static final FunctionParameterSequenceType FS_PARAM_K = optParam("k", Type.INTEGER,
            "Number of nearest neighbours to return (default 10).");
    private static final FunctionParameterSequenceType FS_PARAM_OPTIONS = optParam("options", Type.ITEM,
            "Optional map with filter-query, filter, facets.");

    final static FunctionSignature[] signatures = {
            functionSignature("query-vector",
                    "KNN vector search. Returns k nearest nodes. Vector field is resolved from index config.",
                    returnsOptMany(Type.NODE, "Nodes matching the vector query, ordered by similarity."),
                    FS_PARAM_NODES,
                    FS_PARAM_VECTOR),
            functionSignature("query-vector",
                    "KNN vector search with explicit k.",
                    returnsOptMany(Type.NODE, "Nodes matching the vector query, ordered by similarity."),
                    FS_PARAM_NODES,
                    FS_PARAM_VECTOR,
                    FS_PARAM_K),
            functionSignature("query-vector",
                    "KNN vector search with k and options (filter-query, filter, facets).",
                    returnsOptMany(Type.NODE, "Nodes matching the vector query, ordered by similarity."),
                    FS_PARAM_NODES,
                    FS_PARAM_VECTOR,
                    FS_PARAM_K,
                    FS_PARAM_OPTIONS)
    };

    /**
     * Creates a new QueryVector function instance.
     *
     * @param context the XQuery context
     * @param signature the function signature
     */
    public QueryVector(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
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
        final QueryOptions options = args.length >= 4 && !args[3].isEmpty()
                ? parseOptions(args[3]) : new QueryOptions();

        final NodeSet nodes = nodesSeq.toNodeSet();
        final DocumentSet docs = nodes.getDocumentSet();
        final LuceneIndexWorker index = (LuceneIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(LuceneIndex.ID);
        final List<QName> qnames = resolveQNames(nodes, index);

        final PerformanceStats.IndexOptimizationLevel optimizationLevel;
        try {
            optimizationLevel = index != null && index.hasVectorIndexForQNames(docs, qnames)
                    ? PerformanceStats.IndexOptimizationLevel.OPTIMIZED
                    : PerformanceStats.IndexOptimizationLevel.NONE;
        } catch (IOException e) {
            throw new XPathException(this, "Failed to check vector index config: " + e.getMessage(), e);
        }

        final long start = System.currentTimeMillis();
        try {
            final Sequence result = index.searchVector(getExpressionId(), docs, nodes, qnames, vector, k, options);
            final long duration = System.currentTimeMillis() - start;
            if (optimizationLevel == PerformanceStats.IndexOptimizationLevel.OPTIMIZED) {
                VectorOperationMetrics.recordKnn(duration * 1_000_000L);
            }
            if (context.getProfiler().traceFunctions()) {
                context.getProfiler().traceIndexUsage(context, "lucene-vector", this, optimizationLevel, duration);
            }
            return result;
        } catch (IOException e) {
            throw new XPathException(this, "Vector search failed: " + e.getMessage(), e);
        }
    }

    private static int parseK(final Sequence[] args) throws XPathException {
        if (args.length >= 3 && !args[2].isEmpty()) {
            final int k = args[2].itemAt(0).toJavaObject(Integer.class);
            return k > 0 ? k : 10;
        }
        return 10;
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

    private static float[] arrayToFloats(final Sequence seq) throws XPathException {
        if (seq.isEmpty() || seq.getItemType() != Type.ARRAY_ITEM) {
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

    private QueryOptions parseOptions(final Sequence optSeq) throws XPathException {
        if (optSeq.isEmpty()) {
            return new QueryOptions();
        }
        final Item item = optSeq.itemAt(0);
        if (Type.subTypeOf(item.getType(), Type.MAP_ITEM)) {
            return new QueryOptions((AbstractMapType) item);
        }
        if (Type.subTypeOf(item.getType(), Type.NODE)) {
            return new QueryOptions(context, (NodeValue) item);
        }
        throw new XPathException(this, LuceneModule.EXXQDYFT0004, "Options must be a map or XML element");
    }
}
