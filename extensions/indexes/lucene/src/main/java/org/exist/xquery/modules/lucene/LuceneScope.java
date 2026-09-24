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
import org.exist.indexing.lucene.LuceneConfig;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneIndexWorker;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.IndexSpec;
import org.exist.storage.DBBroker;
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

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
     * Resolve a sequence of collection or document URIs to the collections they cover, each with its
     * Lucene configuration. A collection URI covers the collection and every sub-collection the caller
     * may read; a document URI covers the collection that owns the document. Configurations are read
     * during the traversal, under the same read lock, so callers do not reopen the collections.
     *
     * @return each collection in scope, in traversal order (parents before children), mapped to its
     *     Lucene configuration, or to null if it has none
     */
    static Map<XmldbURI, LuceneConfig> resolveScopeConfigs(final BasicFunction fn, final Sequence scope) throws XPathException {
        final DBBroker broker = fn.getContext().getBroker();
        final Map<XmldbURI, LuceneConfig> configs = new LinkedHashMap<>();
        for (final SequenceIterator i = scope.iterate(); i.hasNext(); ) {
            final String path = i.nextItem().getStringValue();
            final XmldbURI uri = toUri(fn, path);
            try {
                if (!collectConfigs(broker, uri, configs)) {
                    // not a collection: treat it as a document and cover its owning collection
                    final XmldbURI owner;
                    try (final LockedDocument lockedDoc = broker.getXMLResource(uri, LockMode.READ_LOCK)) {
                        owner = lockedDoc == null ? null : lockedDoc.getDocument().getCollection().getURI();
                    }
                    if (owner != null && !configs.containsKey(owner)) {
                        try (final Collection coll = broker.openCollection(owner, LockMode.READ_LOCK)) {
                            if (coll != null) {
                                configs.put(owner, luceneConfigOf(broker, coll));
                            }
                        }
                    }
                }
            } catch (final PermissionDeniedException e) {
                throw new XPathException(fn, LuceneModule.EXXQDYFT0001, "Permission denied to access '" + path + "'");
            } catch (final LockException e) {
                throw new XPathException(fn, LuceneModule.EXXQDYFT0002, "Lock error while accessing '" + path + "': " + e.getMessage());
            }
        }
        return configs;
    }

    private static @Nullable LuceneConfig luceneConfigOf(final DBBroker broker, final Collection collection) {
        final IndexSpec indexSpec = collection.getIndexConfiguration(broker);
        return indexSpec == null ? null : (LuceneConfig) indexSpec.getCustomIndexSpec(LuceneIndex.ID);
    }

    /**
     * Add {@code uri} and every readable sub-collection to {@code configs}. Returns {@code false}
     * if {@code uri} is not a collection (so the caller can fall back to document resolution). Child
     * URIs are snapshotted under the read lock and recursed into after the lock is released, mirroring
     * {@link org.exist.collections.Collection#allDocs} to avoid self-deadlock.
     */
    private static boolean collectConfigs(final DBBroker broker, final XmldbURI uri, final Map<XmldbURI, LuceneConfig> configs)
            throws PermissionDeniedException, LockException {
        final XmldbURI[] childUris;
        try (final Collection coll = broker.openCollection(uri, LockMode.READ_LOCK)) {
            if (coll == null) {
                return false;
            }
            configs.put(uri, luceneConfigOf(broker, coll));
            final List<XmldbURI> children = new ArrayList<>();
            for (final Iterator<XmldbURI> ci = coll.collectionIterator(broker); ci.hasNext(); ) {
                children.add(uri.append(ci.next()));
            }
            childUris = children.toArray(new XmldbURI[0]);
        }
        for (final XmldbURI child : childUris) {
            try {
                collectConfigs(broker, child, configs);
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
