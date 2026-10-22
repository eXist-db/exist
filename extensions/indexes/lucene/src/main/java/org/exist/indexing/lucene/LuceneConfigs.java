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
package org.exist.indexing.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.storage.DBBroker;
import org.exist.storage.IndexSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The Lucene configurations of a query's documents: each distinct configuration of their collections, with the
 * documents it applies to.
 * <p>
 * One Lucene index serves all collections, but each collection can configure its own analyzers and fields. A query
 * over collections configured differently has to be parsed and searched with each one's configuration, not with the
 * first collection's only: with a stemming analyzer in one sub-collection and a standard one in another, a term
 * analyzed for one of them does not match the other's index.
 */
public final class LuceneConfigs {

    /**
     * A configuration and the documents it applies to.
     *
     * @param config the Lucene configuration
     * @param docs the documents of the collections that have it
     */
    public record Group(LuceneConfig config, DocumentSet docs) {
    }

    private final List<Group> groups;
    /** each group's document filter, built once per query however many qnames it searches */
    private final Map<Group, Query> docsFilters = new IdentityHashMap<>();

    private LuceneConfigs(final List<Group> groups) {
        this.groups = groups;
    }

    /**
     * Groups documents by the Lucene configuration of their collection. When the collections share one
     * configuration, documents of collections without one stay with it, as before; when they have several, those
     * documents form a group of their own, with the default configuration, as they can still be in the index, for
     * example binary documents indexed with ft:index.
     *
     * @param broker the database broker
     * @param docs the query's documents
     * @return at least one group: the default configuration with all the documents if no collection has one, and
     *     all the documents, as given, if their collections share one configuration
     */
    public static LuceneConfigs of(final DBBroker broker, final DocumentSet docs) {
        final Map<Integer, LuceneConfig> byCollection = configsByCollection(broker, docs);
        // each configuration is a distinct instance per configured collection, inherited by its sub-collections
        final Set<LuceneConfig> configured = Collections.newSetFromMap(new IdentityHashMap<>());
        configured.addAll(byCollection.values());
        configured.remove(LuceneConfig.DEFAULT_CONFIG);
        if (configured.size() <= 1) {
            // all the documents, as given, so that a query of one configuration is the same query as before
            final LuceneConfig config = configured.isEmpty() ? LuceneConfig.DEFAULT_CONFIG : configured.iterator().next();
            return new LuceneConfigs(List.of(new Group(config, docs)));
        }
        return new LuceneConfigs(groupByConfig(docs, byCollection));
    }

    private static Map<Integer, LuceneConfig> configsByCollection(final DBBroker broker, final DocumentSet docs) {
        final Map<Integer, LuceneConfig> byCollection = new HashMap<>();
        for (final Iterator<Collection> i = docs.getCollectionIterator(); i.hasNext(); ) {
            try (final Collection collection = i.next()) {
                byCollection.put(collection.getId(), configOf(broker, collection));
            }
        }
        return byCollection;
    }

    private static List<Group> groupByConfig(final DocumentSet docs, final Map<Integer, LuceneConfig> byCollection) {
        final Map<LuceneConfig, DefaultDocumentSet> byConfig = new IdentityHashMap<>();
        final List<LuceneConfig> order = new ArrayList<>();
        for (final Iterator<DocumentImpl> i = docs.getDocumentIterator(); i.hasNext(); ) {
            final DocumentImpl doc = i.next();
            final LuceneConfig config = byCollection.getOrDefault(doc.getCollection().getId(), LuceneConfig.DEFAULT_CONFIG);
            byConfig.computeIfAbsent(config, c -> {
                order.add(c);
                return new DefaultDocumentSet();
            }).add(doc);
        }
        final List<Group> groups = new ArrayList<>(order.size());
        for (final LuceneConfig config : order) {
            groups.add(new Group(config, byConfig.get(config)));
        }
        return groups;
    }

    /**
     * @param group one of the groups
     * @param build builds a query matching the Lucene documents of some documents
     * @return the group's document filter, built once
     */
    public @Nullable Query docsFilter(final Group group, final Function<DocumentSet, Query> build) {
        return docsFilters.computeIfAbsent(group, g -> build.apply(g.docs()));
    }

    /**
     * @param id an analyzer id
     * @return the analyzer of that id in the first configuration that defines it, or null
     */
    public @Nullable Analyzer analyzerById(final String id) {
        for (final Group group : groups) {
            final Analyzer analyzer = group.config().getAnalyzerById(id);
            if (analyzer != null) {
                return analyzer;
            }
        }
        return null;
    }

    private static LuceneConfig configOf(final DBBroker broker, final Collection collection) {
        final IndexSpec idxConf = collection.getIndexConfiguration(broker);
        if (idxConf != null) {
            final LuceneConfig config = (LuceneConfig) idxConf.getCustomIndexSpec(LuceneIndex.ID);
            if (config != null) {
                return config;
            }
        }
        return LuceneConfig.DEFAULT_CONFIG;
    }

    /**
     * @return the configurations, each with its documents, in the order of the documents' collections
     */
    public List<Group> groups() {
        return groups;
    }

    /**
     * @return true if all the documents have the same configuration
     */
    public boolean isUniform() {
        return groups.size() == 1;
    }

    /**
     * @return the configuration of the first group, for what a query has only one of, such as its facets
     */
    public LuceneConfig first() {
        return groups.get(0).config();
    }
}
