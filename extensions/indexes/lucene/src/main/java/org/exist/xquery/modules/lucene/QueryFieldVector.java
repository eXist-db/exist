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

import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.NodeSet;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneIndexWorker;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import javax.annotation.Nullable;

import static org.exist.xquery.FunctionDSL.optParam;
import static org.exist.xquery.FunctionDSL.param;
import static org.exist.xquery.FunctionDSL.returnsOptMany;
import static org.exist.xquery.modules.lucene.LuceneModule.functionSignature;

/**
 * ft:query-field-vector(field, vector, k?, options?) — KNN vector search by field name.
 * Uses context sequence's document set (like ft:query-field).
 *
 * <p>See {@link AbstractVectorQueryFunction} for the shared {@link Optimizable} machinery and
 * why it's needed. Unlike {@link QueryVector}, arg0 here is always a field name (a string), never
 * a node reference, so {@link #getDependencies()} doesn't need the "does arg0 depend on the
 * context item" check — the whole point of ft:query-field-vector is that it always operates on
 * the full context sequence.</p>
 */
public class QueryFieldVector extends AbstractVectorQueryFunction {

    private static final FunctionParameterSequenceType FS_PARAM_FIELD = param("field", Type.STRING,
            "The vector field name (from vector-field config).");
    private static final FunctionParameterSequenceType FS_PARAM_VECTOR = param("vector", Type.ARRAY_ITEM,
            "Query vector as XQuery array of numbers.");
    private static final FunctionParameterSequenceType FS_PARAM_K = optParam("k", Type.INTEGER,
            "Number of nearest neighbours (default 10).");
    private static final FunctionParameterSequenceType FS_PARAM_OPTIONS = optParam("options", Type.ITEM,
            "Optional map with filter-query, filter, facets.");

    final static FunctionSignature[] signatures = {
            functionSignature("query-field-vector",
                    "KNN vector search by field name. Uses context document set.",
                    returnsOptMany(Type.NODE, "Nodes matching the vector query."),
                    FS_PARAM_FIELD,
                    FS_PARAM_VECTOR),
            functionSignature("query-field-vector",
                    "KNN vector search by field with explicit k.",
                    returnsOptMany(Type.NODE, "Nodes matching the vector query."),
                    FS_PARAM_FIELD,
                    FS_PARAM_VECTOR,
                    FS_PARAM_K),
            functionSignature("query-field-vector",
                    "KNN vector search by field with k and options.",
                    returnsOptMany(Type.NODE, "Nodes matching the vector query."),
                    FS_PARAM_FIELD,
                    FS_PARAM_VECTOR,
                    FS_PARAM_K,
                    FS_PARAM_OPTIONS)
    };

    /**
     * Creates a new QueryFieldVector function instance.
     *
     * @param context the XQuery context
     * @param signature the function signature
     */
    public QueryFieldVector(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, @Nullable final Sequence contextSequence) throws XPathException {
        final String field = args[0].getStringValue();
        final float[] vector = arrayToFloats(args[1]);
        if (vector == null) {
            throw new XPathException(this, "Second argument must be an array of numbers");
        }
        final int k = parseK(args);
        final QueryOptions options = parseOptionsArg(args);

        final DocumentSet docs;
        final NodeSet contextSet;
        if (contextSequence != null && contextSequence.isPersistentSet()) {
            docs = contextSequence.getDocumentSet();
            contextSet = contextSequence.toNodeSet();
        } else {
            docs = context.getStaticallyKnownDocuments();
            contextSet = null;
        }

        final LuceneIndexWorker index = (LuceneIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(LuceneIndex.ID);
        final PerformanceStats.IndexOptimizationLevel optimizationLevel =
                VectorSearchSupport.optimizationLevelForField(index, docs, field);

        return VectorSearchSupport.execute(this, context, index, optimizationLevel,
                () -> index.searchVector(getExpressionId(), docs, contextSet, field, vector, k, options));
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

        final Sequence[] args = evalArgs(contextSequence, 0);
        final String field = args[0].getStringValue();
        final float[] vector = arrayToFloats(args[1]);
        if (vector == null) {
            throw new XPathException(this, "Second argument must be an array of numbers");
        }
        final int k = parseK(args);
        final QueryOptions options = parseOptionsArg(args);

        final DocumentSet docs = contextSequence.getDocumentSet();
        final NodeSet contextSet = useContext ? contextSequence.toNodeSet() : null;

        final LuceneIndexWorker index = (LuceneIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(LuceneIndex.ID);
        final PerformanceStats.IndexOptimizationLevel optimizationLevel =
                VectorSearchSupport.optimizationLevelForField(index, docs, field);

        final Sequence result = VectorSearchSupport.execute(this, context, index, optimizationLevel,
                () -> index.searchVector(getExpressionId(), docs, contextSet, field, vector, k, options));
        preselectResult = result.toNodeSet();
        return preselectResult;
    }

    /**
     * Declares {@link Dependency#CONTEXT_SET} without {@link Dependency#CONTEXT_ITEM} — see class
     * javadoc and {@link QueryVector#getDependencies()} for why this matters: without it,
     * {@link PathExpr#eval} forces one KNN search per candidate document instead of one search
     * over the whole candidate set, silently ignoring {@code k}.
     */
    @Override
    public int getDependencies() {
        if (anyArgDependsOnLocalVar(0)) {
            return Dependency.CONTEXT_SET | Dependency.CONTEXT_ITEM;
        }
        return Dependency.CONTEXT_SET;
    }
}
