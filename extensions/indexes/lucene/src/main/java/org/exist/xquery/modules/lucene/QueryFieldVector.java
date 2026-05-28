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
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.value.*;

import javax.annotation.Nullable;
import java.io.IOException;

import static org.exist.xquery.FunctionDSL.optParam;
import static org.exist.xquery.FunctionDSL.param;
import static org.exist.xquery.FunctionDSL.returnsOptMany;
import static org.exist.xquery.modules.lucene.LuceneModule.functionSignature;

/**
 * ft:query-field-vector(field, vector, k?, options?) — KNN vector search by field name.
 * Uses context sequence's document set (like ft:query-field).
 */
public class QueryFieldVector extends BasicFunction {

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

        int kValue = 10;
        QueryOptions queryOptions = new QueryOptions();
        if (args.length >= 3 && !args[2].isEmpty()) {
            kValue = args[2].itemAt(0).toJavaObject(Integer.class);
            if (kValue <= 0) {
                kValue = 10;
            }
        }
        if (args.length >= 4 && !args[3].isEmpty()) {
            queryOptions = parseOptions(args[3]);
        }
        final int k = kValue;
        final QueryOptions options = queryOptions;

        DocumentSet docs;
        NodeSet contextSet;
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
