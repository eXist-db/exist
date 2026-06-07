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

import org.apache.lucene.queryparser.classic.ParseException;
import org.exist.collections.Collection;
import org.exist.dom.QName;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.LockedDocument;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.dom.persistent.NodeSet;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneIndexWorker;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.w3c.dom.Element;

import java.io.IOException;

/**
 * {@code ft:query-scope($scope, $query, $options?)} — an <em>index-first</em> Lucene search.
 *
 * Unlike {@code ft:query}, which evaluates relative to an XPath context node set, this function
 * queries the Lucene index directly over the documents in {@code $scope} and returns <em>all</em>
 * matching nodes — of any indexed element type — with their Lucene scores and match highlighting
 * attached, exactly as {@code ft:query} results carry them. Consequently:
 *
 * <ul>
 *   <li>relevance is correct for every hit regardless of how deeply the matched element is nested
 *       (it avoids the {@code //*} descendant-wildcard {@code ft:score}-loss artifact by never
 *       using an XPath node set as the query unit), and</li>
 *   <li>it is element-name independent: no need to enumerate or union the contributing element
 *       types, so content producers stay decoupled from the search aggregator.</li>
 * </ul>
 *
 * The result is an ordinary node set, so {@code ft:score}, {@code ft:facets}, {@code ft:field} and
 * {@code ft:highlight-field-matches} compose on it as usual. This is the focused, native primitive
 * underpinning the "eXlasticSearch" field-first search design; the ES {@code _search}-style result
 * map (hits/fields/facets/highlights/live-node) is assembled in XQuery on top of this node set.
 */
public class QueryScope extends BasicFunction {

    private static final FunctionParameterSequenceType FS_PARAM_SCOPE =
            new FunctionParameterSequenceType("scope", Type.STRING, Cardinality.ZERO_OR_MORE,
                    "Collection (or document) URIs to search. Collection URIs are searched recursively.");
    private static final FunctionParameterSequenceType FS_PARAM_QUERY =
            new FunctionParameterSequenceType("query", Type.ITEM, Cardinality.ZERO_OR_ONE,
                    "The query: a string in Lucene's default query syntax (e.g. \"site-content:(array)\") "
                            + "or an XML query element. An empty query matches all indexed nodes in scope.");
    private static final FunctionParameterSequenceType FS_PARAM_OPTIONS =
            new FunctionParameterSequenceType("options", Type.ITEM, Cardinality.EXACTLY_ONE,
                    "Query options as an XML fragment or an XDM map (same options as ft:query, including facet drill-down).");
    private static final FunctionReturnSequenceType FS_RETURN =
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                    "All indexed nodes in scope matching the query, each carrying its Lucene score "
                            + "(via ft:score) and match information.");

    public static final FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("query-scope", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
                    "Index-first Lucene search over a scope of collections/documents. Returns all matching "
                            + "indexed nodes (any element type) with scores attached, avoiding the XPath node-set "
                            + "scoring artifacts of ft:query over a descendant-axis wildcard.",
                    new SequenceType[]{FS_PARAM_SCOPE, FS_PARAM_QUERY},
                    FS_RETURN),
            new FunctionSignature(
                    new QName("query-scope", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
                    "Index-first Lucene search over a scope of collections/documents, with query options. "
                            + "Returns all matching indexed nodes (any element type) with scores attached.",
                    new SequenceType[]{FS_PARAM_SCOPE, FS_PARAM_QUERY, FS_PARAM_OPTIONS},
                    FS_RETURN)
    };

    public QueryScope(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final MutableDocumentSet docs = resolveScope(args[0]);
        if (docs.getDocumentCount() == 0) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final LuceneIndexWorker index = (LuceneIndexWorker) context.getBroker()
                .getIndexController().getWorkerByIndexId(LuceneIndex.ID);

        // options is the 3rd argument (1-based position 3: scope, query, options), as in ft:query.
        // parseOptions short-circuits to default QueryOptions when getArgumentCount() < 3, so the
        // 2-argument form never dereferences a missing argument.
        final QueryOptions options = Query.parseOptions(this, contextSequence, null, 3);

        try {
            // contextSet == null => index-first (no descendant-of constraint, returns the matched nodes);
            // qnames == null => search across all defined indexes (element-name independent)
            if (!args[1].isEmpty() && Type.subTypeOf(args[1].itemAt(0).getType(), Type.ELEMENT)) {
                final Element queryXml = (Element) ((NodeValue) args[1].itemAt(0)).getNode();
                return index.query(getExpressionId(), docs, null, null, queryXml, NodeSet.DESCENDANT, options);
            } else {
                final String query = args[1].isEmpty() ? null : args[1].itemAt(0).getStringValue();
                return index.query(getExpressionId(), docs, null, null, query, NodeSet.DESCENDANT, options);
            }
        } catch (final IOException | ParseException e) {
            throw new XPathException(this, LuceneModule.EXXQDYFT0002, "Error while querying full text index: " + e.getMessage());
        }
    }

    private MutableDocumentSet resolveScope(final Sequence scope) throws XPathException {
        final MutableDocumentSet docs = new DefaultDocumentSet();
        for (final SequenceIterator i = scope.iterate(); i.hasNext(); ) {
            final String path = i.nextItem().getStringValue();
            final XmldbURI uri = XmldbURI.create(path);
            try (final Collection coll = context.getBroker().openCollection(uri, LockMode.READ_LOCK)) {
                if (coll != null) {
                    coll.allDocs(context.getBroker(), docs, true, context.getProtectedDocs());
                } else {
                    // not a collection: try it as a single document
                    try (final LockedDocument lockedDoc = context.getBroker().getXMLResource(uri, LockMode.READ_LOCK)) {
                        if (lockedDoc != null) {
                            docs.add(lockedDoc.getDocument());
                        }
                    }
                }
            } catch (final PermissionDeniedException e) {
                throw new XPathException(this, LuceneModule.EXXQDYFT0001, "Permission denied to access '" + path + "'");
            } catch (final LockException e) {
                throw new XPathException(this, LuceneModule.EXXQDYFT0002, "Lock error while accessing '" + path + "': " + e.getMessage());
            }
        }
        return docs;
    }
}
