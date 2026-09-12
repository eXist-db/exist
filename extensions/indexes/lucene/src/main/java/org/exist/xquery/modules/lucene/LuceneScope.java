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
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.w3c.dom.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
        for (final SequenceIterator i = scope.iterate(); i.hasNext(); ) {
            final String path = i.nextItem().getStringValue();
            final XmldbURI uri = XmldbURI.create(path);
            try (final Collection coll = fn.getContext().getBroker().openCollection(uri, LockMode.READ_LOCK)) {
                if (coll != null) {
                    coll.allDocs(fn.getContext().getBroker(), docs, true, fn.getContext().getProtectedDocs());
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

    /**
     * Resolve a sequence of collection or document URIs to the set of collections in scope, recursively
     * (sub-collections included) and permission-checked at collection-read granularity. Unlike
     * {@link #resolveScope}, this does not enumerate documents: configuration introspection
     * ({@link Fields}) needs only the collection hierarchy, so the cost is bounded by the number of
     * collections in scope, not the number of documents.
     *
     * <p>A URI that resolves to a collection contributes that collection and, recursively, every
     * sub-collection the caller can read (unreadable sub-collections are skipped, as in
     * {@link org.exist.collections.Collection#allDocs}). A URI that is not a collection is treated as a
     * document and contributes its owning collection. Insertion order is preserved (parents before
     * children) so a configuration inherited from a parent is attributed to that parent.</p>
     */
    static Set<XmldbURI> resolveScopeCollections(final BasicFunction fn, final Sequence scope) throws XPathException {
        final DBBroker broker = fn.getContext().getBroker();
        final Set<XmldbURI> collections = new LinkedHashSet<>();
        for (final SequenceIterator i = scope.iterate(); i.hasNext(); ) {
            final String path = i.nextItem().getStringValue();
            final XmldbURI uri = XmldbURI.create(path);
            try {
                if (!collectCollections(broker, uri, collections)) {
                    // not a collection: treat it as a document and add its owning collection
                    try (final LockedDocument lockedDoc = broker.getXMLResource(uri, LockMode.READ_LOCK)) {
                        if (lockedDoc != null) {
                            collections.add(lockedDoc.getDocument().getCollection().getURI());
                        }
                    }
                }
            } catch (final PermissionDeniedException e) {
                throw new XPathException(fn, LuceneModule.EXXQDYFT0001, "Permission denied to access '" + path + "'");
            } catch (final LockException e) {
                throw new XPathException(fn, LuceneModule.EXXQDYFT0002, "Lock error while accessing '" + path + "': " + e.getMessage());
            }
        }
        return collections;
    }

    /**
     * Add {@code uri} and every readable sub-collection to {@code collections}. Returns {@code false}
     * if {@code uri} is not a collection (so the caller can fall back to document resolution). Child
     * URIs are snapshotted under the read lock and recursed into after the lock is released, mirroring
     * {@link org.exist.collections.Collection#allDocs} to avoid self-deadlock.
     */
    private static boolean collectCollections(final DBBroker broker, final XmldbURI uri, final Set<XmldbURI> collections)
            throws PermissionDeniedException, LockException {
        final XmldbURI[] childUris;
        try (final Collection coll = broker.openCollection(uri, LockMode.READ_LOCK)) {
            if (coll == null) {
                return false;
            }
            collections.add(uri);
            final List<XmldbURI> children = new ArrayList<>();
            for (final Iterator<XmldbURI> ci = coll.collectionIterator(broker); ci.hasNext(); ) {
                children.add(uri.append(ci.next()));
            }
            childUris = children.toArray(new XmldbURI[0]);
        }
        for (final XmldbURI child : childUris) {
            try {
                collectCollections(broker, child, collections);
            } catch (final PermissionDeniedException pde) {
                // skip sub-collections the caller cannot read (matches Collection.allDocs)
            }
        }
        return true;
    }

    /**
     * Run the index-first query over {@code docs}. {@code queryArg} is either a Lucene query string or an
     * XML query element (an empty query matches all indexed nodes in scope).
     *
     * @return the matching nodes, each carrying its Lucene score and matches.
     */
    static NodeSet query(final BasicFunction fn, final Sequence contextSequence, final MutableDocumentSet docs,
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
