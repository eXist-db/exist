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

import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.Query;
import org.exist.dom.QName;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.Match;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneIndexWorker;
import org.exist.indexing.lucene.LuceneMatch;
import org.exist.numbering.NodeId;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code ft:search-scope($scope, $query, $options?)} — the detached, map-returning companion to
 * {@link QueryScope}, returning an Elasticsearch {@code _search}-shaped result map. Where
 * {@code ft:query-scope} returns live nodes, this assembles those into plain {@code map}/{@code array}
 * data an API builder wants (no nodes to walk).
 *
 * <p>Result envelope (ES field names in parentheses):</p>
 * <pre>
 * map {
 *   "total":     xs:integer,   (: hits.total.value -- see granularity below :)
 *   "max-score": xs:double,    (: hits.max_score :)
 *   "hits": array {            (: hits.hits[] :)
 *     map {
 *       "uri":     xs:string,  (: the eXist document URI :)
 *       "node-id": xs:string,  (: the indexed element within that document :)
 *       "score":   xs:double,  (: _score :)
 *       "source":  map(*)      (: _source -- requested stored fields :)
 *     }* },
 *   "facets": map(*)           (: aggregations -- requested dimensions, value -> count :)
 * }
 * </pre>
 *
 * <p>{@code $options} (all optional) shapes the result:</p>
 * <ul>
 *   <li>{@code "fields"} (xs:string*) — stored fields to include in each hit's {@code source};</li>
 *   <li>{@code "facets"} (xs:string*) — facet dimensions to aggregate, over the full element-hit set;</li>
 *   <li>{@code "collapse"} (xs:boolean) — group hits to one-per-document (see granularity);</li>
 *   <li>{@code "limit"} (xs:integer) — cap the number of returned hits (the page), not {@code total}.</li>
 * </ul>
 *
 * <p><b>Granularity</b> (the decision ES never had to make): eXist indexes per <em>element occurrence</em>,
 * so one document yields 1..N Lucene documents. By default a hit is an indexed <em>element</em> (honest to
 * the index, sub-document precision). {@code collapse=true()} gives the ES-faithful one-hit-per-document
 * view: group element hits by document, keep the best-scoring element, and report {@code total} as the
 * distinct-document count. Facets are always aggregated over the full element-hit set.</p>
 *
 * <p>This first cut runs the index-first query with default options (the {@code $options} map shapes the
 * <em>result</em>, not the Lucene query). Highlighting and a stored-fields-only fast path (building the map
 * without materializing nodes) are follow-ups.</p>
 */
public class SearchScope extends BasicFunction {

    private static final FunctionParameterSequenceType FS_PARAM_SCOPE =
            new FunctionParameterSequenceType("scope", Type.STRING, Cardinality.ZERO_OR_MORE,
                    "Collection (or document) URIs to search. Collection URIs are searched recursively.");
    private static final FunctionParameterSequenceType FS_PARAM_QUERY =
            new FunctionParameterSequenceType("query", Type.ITEM, Cardinality.ZERO_OR_ONE,
                    "The query: a string in Lucene's default query syntax or an XML query element. "
                            + "An empty query matches all indexed nodes in scope.");
    private static final FunctionParameterSequenceType FS_PARAM_OPTIONS =
            new FunctionParameterSequenceType("options", Type.MAP_ITEM, Cardinality.EXACTLY_ONE,
                    "Result-shaping options: 'fields' (xs:string*), 'facets' (xs:string*), "
                            + "'collapse' (xs:boolean), 'limit' (xs:integer).");
    private static final FunctionReturnSequenceType FS_RETURN =
            new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.EXACTLY_ONE,
                    "An Elasticsearch _search-shaped result map: total, max-score, hits[], facets.");

    public static final FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("search-scope", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
                    "Index-first Lucene search over a scope of collections/documents, returning an "
                            + "Elasticsearch _search-shaped result map (total, max-score, hits, facets).",
                    new SequenceType[]{FS_PARAM_SCOPE, FS_PARAM_QUERY},
                    FS_RETURN),
            new FunctionSignature(
                    new QName("search-scope", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
                    "Index-first Lucene search over a scope of collections/documents, returning an "
                            + "Elasticsearch _search-shaped result map, shaped by the options map.",
                    new SequenceType[]{FS_PARAM_SCOPE, FS_PARAM_QUERY, FS_PARAM_OPTIONS},
                    FS_RETURN)
    };

    /** A single element hit and its computed Lucene score. */
    private record Hit(NodeProxy proxy, double score) {
    }

    public SearchScope(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final ResultSpec spec = parseSpec(args);

        if (args[0].isEmpty()) {
            return emptyResult(spec);
        }
        final MutableDocumentSet docs = LuceneScope.resolveScope(this, args[0]);
        if (docs.getDocumentCount() == 0) {
            return emptyResult(spec);
        }

        final NodeSet hits = LuceneScope.query(this, contextSequence, docs, args[1], new QueryOptions());
        return buildResult(hits, spec);
    }

    private Sequence buildResult(final NodeSet hits, final ResultSpec spec) throws XPathException {
        final LuceneIndexWorker index = (LuceneIndexWorker) context.getBroker()
                .getIndexController().getWorkerByIndexId(LuceneIndex.ID);

        // every element hit with its score
        final List<Hit> all = new ArrayList<>();
        for (final SequenceIterator i = hits.iterate(); i.hasNext(); ) {
            final NodeProxy proxy = (NodeProxy) i.nextItem();
            all.add(new Hit(proxy, scoreOf(proxy)));
        }

        final double maxScore = all.stream().mapToDouble(Hit::score).max().orElse(0.0);

        // ranked hit list + total, at the chosen granularity
        final List<Hit> ranked;
        final long total;
        if (spec.collapse()) {
            final Map<Integer, Hit> bestPerDoc = new LinkedHashMap<>();
            for (final Hit h : all) {
                bestPerDoc.merge(h.proxy().getOwnerDocument().getDocId(), h,
                        (a, b) -> a.score() >= b.score() ? a : b);
            }
            ranked = new ArrayList<>(bestPerDoc.values());
            total = ranked.size();
        } else {
            ranked = new ArrayList<>(all);
            total = all.size();
        }
        ranked.sort((a, b) -> Double.compare(b.score(), a.score()));

        final List<Hit> page = (spec.limit() >= 0 && spec.limit() < ranked.size())
                ? ranked.subList(0, spec.limit()) : ranked;

        final List<Sequence> hitMaps = new ArrayList<>(page.size());
        for (final Hit h : page) {
            hitMaps.add(buildHit(h, spec.fields(), index));
        }

        final MapType facets = new MapType(this, context);
        for (final String dimension : spec.facets()) {
            facets.add(new StringValue(this, dimension), buildFacet(all, dimension));
        }

        final MapType result = new MapType(this, context);
        result.add(new StringValue(this, "total"), new IntegerValue(this, total));
        result.add(new StringValue(this, "max-score"), new DoubleValue(this, maxScore));
        result.add(new StringValue(this, "hits"), new ArrayType(this, context, hitMaps));
        result.add(new StringValue(this, "facets"), facets);
        return result;
    }

    private MapType buildHit(final Hit hit, final List<String> fields, final LuceneIndexWorker index)
            throws XPathException {
        final NodeProxy proxy = hit.proxy();
        final DocumentImpl doc = proxy.getOwnerDocument();
        final MapType hitMap = new MapType(this, context);
        hitMap.add(new StringValue(this, "uri"), new StringValue(this, doc.getURI().toString()));
        hitMap.add(new StringValue(this, "node-id"), new StringValue(this, proxy.getNodeId().toString()));
        hitMap.add(new StringValue(this, "score"), new DoubleValue(this, hit.score()));

        final MapType source = new MapType(this, context);
        for (final String field : fields) {
            source.add(new StringValue(this, field), fieldValues(index, doc.getDocId(), proxy.getNodeId(), field));
        }
        hitMap.add(new StringValue(this, "source"), source);
        return hitMap;
    }

    private Sequence fieldValues(final LuceneIndexWorker index, final int docId, final NodeId nodeId,
                                 final String field) throws XPathException {
        try {
            final IndexableField[] indexed = index.getFieldByExistDocId(docId, nodeId, field);
            final ValueSequence values = new ValueSequence(indexed.length);
            for (final IndexableField f : indexed) {
                final String s = f.stringValue();
                values.add(new StringValue(this, s != null ? s
                        : (f.numericValue() != null ? f.numericValue().toString() : "")));
            }
            return values;
        } catch (final IOException e) {
            throw new XPathException(this, LuceneModule.EXXQDYFT0002, "Error retrieving field '" + field + "': " + e.getMessage());
        }
    }

    /** Aggregate one facet dimension over the full element-hit set (value -> count). */
    private MapType buildFacet(final List<Hit> all, final String dimension) throws XPathException {
        // each distinct Lucene query carries its own facets; collect one match per query, then merge
        final Map<Query, LuceneMatch> perQuery = new IdentityHashMap<>();
        for (final Hit h : all) {
            Match match = h.proxy().getMatches();
            while (match != null) {
                if (match.getIndexId().equals(LuceneIndex.ID)) {
                    final LuceneMatch luceneMatch = (LuceneMatch) match;
                    perQuery.putIfAbsent(luceneMatch.getQuery(), luceneMatch);
                }
                match = match.getNextMatch();
            }
        }

        final Map<String, Long> counts = new LinkedHashMap<>();
        for (final LuceneMatch match : perQuery.values()) {
            final org.apache.lucene.facet.Facets facets = match.getFacets();
            if (facets == null) {
                continue;
            }
            try {
                final FacetResult fr = facets.getTopChildren(Integer.MAX_VALUE, dimension);
                if (fr != null) {
                    for (final LabelAndValue lv : fr.labelValues) {
                        counts.merge(lv.label, lv.value.longValue(), Long::sum);
                    }
                }
            } catch (final IOException e) {
                throw new XPathException(this, LuceneModule.EXXQDYFT0002, "Error reading facet '" + dimension + "': " + e.getMessage());
            } catch (final IllegalArgumentException e) {
                // dimension not configured for this query's facets: contributes nothing
            }
        }

        final MapType dimMap = new MapType(this, context);
        for (final Map.Entry<String, Long> entry : counts.entrySet()) {
            dimMap.add(new StringValue(this, entry.getKey()), new IntegerValue(this, entry.getValue()));
        }
        return dimMap;
    }

    private Sequence emptyResult(final ResultSpec spec) throws XPathException {
        final MapType result = new MapType(this, context);
        result.add(new StringValue(this, "total"), new IntegerValue(this, 0));
        result.add(new StringValue(this, "max-score"), new DoubleValue(this, 0.0));
        result.add(new StringValue(this, "hits"), new ArrayType(this, context, List.of()));
        final MapType facets = new MapType(this, context);
        for (final String dimension : spec.facets()) {
            facets.add(new StringValue(this, dimension), new MapType(this, context));
        }
        result.add(new StringValue(this, "facets"), facets);
        return result;
    }

    private static double scoreOf(final NodeProxy proxy) {
        double score = 0.0;
        Match match = proxy.getMatches();
        while (match != null) {
            if (match.getIndexId().equals(LuceneIndex.ID)) {
                score += ((LuceneMatch) match).getScore();
            }
            match = match.getNextMatch();
        }
        return score;
    }

    // ---- options ----

    /** The parsed result-shaping options. */
    private record ResultSpec(List<String> fields, List<String> facets, boolean collapse, int limit) {
    }

    private ResultSpec parseSpec(final Sequence[] args) throws XPathException {
        if (getArgumentCount() < 3 || args[2].isEmpty()
                || !Type.subTypeOf(args[2].itemAt(0).getType(), Type.MAP_ITEM)) {
            return new ResultSpec(List.of(), List.of(), false, -1);
        }
        final AbstractMapType options = (AbstractMapType) args[2].itemAt(0);
        return new ResultSpec(
                stringList(options, "fields"),
                stringList(options, "facets"),
                booleanOption(options, "collapse"),
                intOption(options, "limit"));
    }

    private List<String> stringList(final AbstractMapType options, final String key) throws XPathException {
        final Sequence value = options.get(new StringValue(this, key));
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        final List<String> result = new ArrayList<>(value.getItemCount());
        for (final SequenceIterator i = value.iterate(); i.hasNext(); ) {
            result.add(i.nextItem().getStringValue());
        }
        return result;
    }

    private boolean booleanOption(final AbstractMapType options, final String key) throws XPathException {
        final Sequence value = options.get(new StringValue(this, key));
        return value != null && !value.isEmpty() && value.effectiveBooleanValue();
    }

    private int intOption(final AbstractMapType options, final String key) throws XPathException {
        final Sequence value = options.get(new StringValue(this, key));
        if (value == null || value.isEmpty()) {
            return -1;
        }
        return ((AtomicValue) value.itemAt(0)).toJavaObject(Integer.class);
    }
}
