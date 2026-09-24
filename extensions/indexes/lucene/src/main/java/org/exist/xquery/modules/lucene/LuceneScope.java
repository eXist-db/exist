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
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.w3c.dom.Element;

import java.io.IOException;

/**
 * Shared scope-resolution and index-first query execution for the collection-scoped Lucene functions
 * ({@link QueryScope} and {@link SearchScope}). Both resolve a sequence of collection/document URIs to
 * a {@code DocumentSet} and run the Lucene query directly over it — with a <b>null context set</b>
 * (index-first; no descendant-of constraint) and <b>null qnames</b> (all defined indexes) — so the
 * result is every matching indexed node, of any element type, carrying its score and matches.
 */
final class LuceneScope {

    private LuceneScope() {
    }

    /**
     * Resolve a sequence of collection or document URIs to a document set. Collection URIs are searched
     * recursively (including sub-collections); a URI that is not a collection is tried as a single document.
     */
    static MutableDocumentSet resolveScope(final BasicFunction fn, final Sequence scope) throws XPathException {
        final MutableDocumentSet docs = new DefaultDocumentSet();
        final XQueryContext context = fn.getContext();
        for (final SequenceIterator i = scope.iterate(); i.hasNext(); ) {
            final String path = i.nextItem().getStringValue();
            final XmldbURI uri = toUri(fn, path);
            try (final Collection coll = context.getBroker().openCollection(uri, LockMode.READ_LOCK)) {
                if (coll != null) {
                    // as fn:collection does: a caller that has locked a document set sees that set
                    if (context.inProtectedMode()) {
                        context.getProtectedDocs().getDocsByCollection(coll, docs);
                    } else {
                        coll.allDocs(context.getBroker(), docs, true, context.getProtectedDocs());
                    }
                } else {
                    // not a collection: try it as a single document
                    try (final LockedDocument lockedDoc = fn.getContext().getBroker().getXMLResource(uri, LockMode.READ_LOCK)) {
                        if (lockedDoc != null) {
                            docs.add(lockedDoc.getDocument());
                        }
                    }
                }
            } catch (final PermissionDeniedException e) {
                throw new XPathException(fn, LuceneModule.EXXQDYFT0001, "Permission denied to access '" + path + "'");
            } catch (final LockException e) {
                throw new XPathException(fn, LuceneModule.EXXQDYFT0002, "Lock error while accessing '" + path + "': " + e.getMessage());
            }
        }
        return docs;
    }

    private static XmldbURI toUri(final BasicFunction fn, final String path) throws XPathException {
        try {
            return XmldbURI.create(path);
        } catch (final IllegalArgumentException e) {
            throw new XPathException(fn, ErrorCodes.FODC0004, "Invalid collection or document URI '" + path + "': " + e.getMessage());
        }
    }

    /**
     * Run the index-first query over {@code docs}. {@code queryArg} is either a Lucene query string or an
     * XML query element (an empty query matches all indexed nodes in scope).
     *
     * @return the matching nodes, each carrying its Lucene score and matches.
     */
    static NodeSet query(final BasicFunction fn, final MutableDocumentSet docs,
                         final Sequence queryArg, final QueryOptions options) throws XPathException {
        final LuceneIndexWorker index = (LuceneIndexWorker) fn.getContext().getBroker()
                .getIndexController().getWorkerByIndexId(LuceneIndex.ID);
        try {
            if (!queryArg.isEmpty() && Type.subTypeOf(queryArg.itemAt(0).getType(), Type.ELEMENT)) {
                final Element queryXml = (Element) ((NodeValue) queryArg.itemAt(0)).getNode();
                return index.query(fn.getExpressionId(), docs, null, null, queryXml, NodeSet.DESCENDANT, options);
            } else {
                final String query = queryArg.isEmpty() ? null : queryArg.itemAt(0).getStringValue();
                return index.query(fn.getExpressionId(), docs, null, null, query, NodeSet.DESCENDANT, options);
            }
        } catch (final IOException | ParseException e) {
            throw new XPathException(fn, LuceneModule.EXXQDYFT0002, "Error while querying full text index: " + e.getMessage());
        }
    }
}
