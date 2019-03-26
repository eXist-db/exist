/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.xquery.modules.lucene;

import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetsCollector;
import org.exist.dom.QName;
import org.exist.dom.persistent.Match;
import org.exist.dom.persistent.NodeProxy;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneIndexWorker;
import org.exist.xquery.*;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;

import java.io.IOException;

public class Facets extends BasicFunction {

    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
                new QName("facets", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
                "Return a map of facet labels and counts for the result of a Lucene query. Facets and facet counts apply " +
                        "to the entire sequence returned by ft:query, so the same map will be returned for all nodes in the sequence. " +
                        "It is thus sufficient to specify one node from the sequence as first argument to this function.",
                new SequenceType[] {
                        new FunctionParameterSequenceType("node", Type.NODE, Cardinality.EXACTLY_ONE,
                                "A single node resulting from a call to ft:query for which facet information should be retrieved. " +
                                        "If the node has no facet information attached, an empty sequence will be returned."),
                        new FunctionParameterSequenceType("dimension", Type.STRING, Cardinality.EXACTLY_ONE,
                                "The facet dimension. This should correspond to a dimension defined in the index configuration")
                },
                new FunctionReturnSequenceType(Type.MAP, Cardinality.ZERO_OR_ONE,
                        "A map having the facet label as key and the facet count as value")
        ),
        new FunctionSignature(
            new QName("facets", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
            "Return a map of facet labels and counts for the result of a Lucene query. Facets and facet counts apply " +
                    "to the entire sequence returned by ft:query, so the same map will be returned for all nodes in the sequence. " +
                    "It is thus sufficient to specify one node from the sequence as first argument to this function.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("node", Type.NODE, Cardinality.EXACTLY_ONE,
                            "A single node resulting from a call to ft:query for which facet information should be retrieved. " +
                                    "If the node has no facet information attached, an empty sequence will be returned."),
                    new FunctionParameterSequenceType("dimension", Type.STRING, Cardinality.EXACTLY_ONE,
                            "The facet dimension. This should correspond to a dimension defined in the index configuration"),
                    new FunctionParameterSequenceType("count", Type.INTEGER, Cardinality.EXACTLY_ONE,
                            "The number of facet labels to be returned. Facets with more occurrences in the result will be returned " +
                                    "first.")
            },
            new FunctionReturnSequenceType(Type.MAP, Cardinality.ZERO_OR_ONE,
                    "A map having the facet label as key and the facet count as value")
        )
    };

    public Facets(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        final NodeValue nv = (NodeValue) args[0].itemAt(0);
        if (nv.getImplementationType() == NodeValue.IN_MEMORY_NODE) {
            return Sequence.EMPTY_SEQUENCE;
        }

        int count = Integer.MAX_VALUE;
        if (getArgumentCount() == 3) {
            count = ((IntegerValue) args[2]).getInt();
        }

        final String dimension = args[1].getStringValue();

        final NodeProxy proxy = (NodeProxy) nv;
        try {
            Match match = proxy.getMatches();
            while (match != null) {
                if (match.getIndexId().equals(LuceneIndex.ID)) {
                    final FacetsCollector collector = ((LuceneIndexWorker.LuceneMatch)match).getFacetsCollector();
                    final LuceneIndexWorker index = (LuceneIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(LuceneIndex.ID);
                    final org.apache.lucene.facet.Facets facets = index.getFacets(collector);
                    final FacetResult result = facets.getTopChildren(count, dimension);

                    if (result == null) {
                        return new MapType(context);
                    } else {
                        final MapType map = new MapType(context);
                        for (int i = 0; i < result.labelValues.length; i++) {
                            final String label = result.labelValues[i].label;
                            final Number value = result.labelValues[i].value;
                            map.add(new StringValue(label), new IntegerValue(value.longValue()));
                        }
                        return map;
                    }
                }
                match = match.getNextMatch();
            }
        } catch (IOException e) {
            throw new XPathException(this, LuceneModule.EXXQDYFT0002, e.getMessage());
        }
        return Sequence.EMPTY_SEQUENCE;
    }
}
