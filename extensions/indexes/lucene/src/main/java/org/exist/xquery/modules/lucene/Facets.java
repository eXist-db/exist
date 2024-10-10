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

import io.lacuna.bifurcan.IMap;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.search.Query;
import org.exist.dom.QName;
import org.exist.dom.persistent.Match;
import org.exist.dom.persistent.NodeProxy;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneMatch;
import org.exist.xquery.*;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.exist.xquery.functions.map.MapType.newLinearMap;

public class Facets extends BasicFunction {

    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
                new QName("facets", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
                "Return a map of facet labels and counts for the result of a Lucene query.",
                new SequenceType[] {
                        new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE,
                                "A sequence of nodes for which facet counts should be returned. If the nodes in the sequence " +
                                        "resulted from different Lucene queries, their facet counts will be merged. If no node in the " +
                                        "the sequence has facets attached or the sequence is empty, an empty map is returned."),
                        new FunctionParameterSequenceType("dimension", Type.STRING, Cardinality.EXACTLY_ONE,
                                "The facet dimension. This should correspond to a dimension defined in the index configuration")
                },
                new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.EXACTLY_ONE,
                        "A map having the facet label as key and the facet count as value")
        ),
        new FunctionSignature(
            new QName("facets", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
            "Return a map of facet labels and counts for the result of a Lucene query.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE,
                    "A sequence of nodes for which facet counts should be returned. If the nodes in the sequence " +
                            "resulted from different Lucene queries, their facet counts will be merged. If no node in the " +
                            "the sequence has facets attached or the sequence is empty, an empty map is returned."),
                    new FunctionParameterSequenceType("dimension", Type.STRING, Cardinality.EXACTLY_ONE,
                            "The facet dimension. This should correspond to a dimension defined in the index configuration"),
                    new FunctionParameterSequenceType("count", Type.INTEGER, Cardinality.ZERO_OR_ONE,
                            "The number of facet labels to be returned. Facets with more occurrences in the result will be returned " +
                                    "first.")
            },
            new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.EXACTLY_ONE,
                    "A map having the facet label as key and the facet count as value")
        ),
        new FunctionSignature(
            new QName("facets", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
            "Return a map of facet labels and counts for the result of a Lucene query.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE,
                    "A sequence of nodes for which facet counts should be returned. If the nodes in the sequence " +
                            "resulted from different Lucene queries, their facet counts will be merged. If no node in the " +
                            "the sequence has facets attached or the sequence is empty, an empty map is returned."),
                    new FunctionParameterSequenceType("dimension", Type.STRING, Cardinality.EXACTLY_ONE,
                            "The facet dimension. This should correspond to a dimension defined in the index configuration"),
                    new FunctionParameterSequenceType("count", Type.INTEGER, Cardinality.ZERO_OR_ONE,
                            "The number of facet labels to be returned. Facets with more occurrences in the result will be returned " +
                                    "first."),
                    new FunctionParameterSequenceType("paths", Type.STRING, Cardinality.ONE_OR_MORE,
                            "For hierarchical facets, specify a sequence of paths leading to the position in the hierarchy you" +
                                    "would like to get facet counts for.")
            },
                new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.EXACTLY_ONE,
                        "A map having the facet label as key and the facet count as value")
        )
    };

    public Facets(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        final String dimension = args[1].getStringValue();

        int count = Integer.MAX_VALUE;
        if (getArgumentCount() == 3 && args[2].hasOne()) {
            count = ((IntegerValue) args[2].itemAt(0)).getInt();
        }

        String[] paths = null;
        if (getArgumentCount() == 4 && !args[3].isEmpty()) {
            paths = new String[args[3].getItemCount()];
            int j = 0;
            for (SequenceIterator i = args[3].unorderedIterator(); i.hasNext(); j++) {
                paths[j] = i.nextItem().getStringValue();
            }
        }

        // Find all lucene queries referenced from the input sequence and remember
        // the first match for each. Every query will have its own facets attached,
        // so we have to merge them below.
        final Map<Query, LuceneMatch> luceneQueries = new IdentityHashMap<>();
        for (final SequenceIterator i = args[0].unorderedIterator(); i.hasNext(); ) {
            final NodeValue nv = (NodeValue) i.nextItem();
            if (nv.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                final NodeProxy proxy = (NodeProxy) nv;

                Match match = proxy.getMatches();
                while (match != null) {
                    if (match.getIndexId().equals(LuceneIndex.ID)) {
                        final LuceneMatch luceneMatch = (LuceneMatch) match;
                        luceneQueries.putIfAbsent(luceneMatch.getQuery(), luceneMatch);
                    }
                    match = match.getNextMatch();
                }
            }
        }

        // Iterate the found queries/matches and collect facets for each
        final IMap<AtomicValue, Sequence> map = newLinearMap(null);
        for (LuceneMatch match : luceneQueries.values()) {
            try {
                addFacetsToMap(map, dimension, count, paths, match);
            } catch (IOException e) {
                throw new XPathException(this, LuceneModule.EXXQDYFT0002, e.getMessage());
            }
        }
        return new MapType(this, context, map.forked(), Type.STRING);
    }

    private void addFacetsToMap(final IMap<AtomicValue, Sequence> map, String dimension, int count, String[] paths, LuceneMatch match) throws IOException {
        final org.apache.lucene.facet.Facets facets = match.getFacets();
        final FacetResult result;
        if (paths == null) {
            result = facets.getTopChildren(count, dimension);
        } else {
            result = facets.getTopChildren(count, dimension, paths);
        }

        if (result != null) {
            for (int i = 0; i < result.labelValues.length; i++) {
                final String label = result.labelValues[i].label;
                final Number value = result.labelValues[i].value;
                final AtomicValue key = new StringValue(this, label);

                map.update(key, v -> {
                    if (v == null) {
                        return new IntegerValue(this, value.longValue());
                    } else {
                        return new IntegerValue(this, value.longValue() + ((IntegerValue)v).getLong());
                    }
                });
            }
        }
    }
}
