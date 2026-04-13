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

import com.evolvedbinary.j8fu.function.FunctionE;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.*;
import org.apache.lucene.facet.DrillDownQuery;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.SearcherTaxonomyManager;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.index.*;
import org.apache.lucene.queries.function.FunctionScoreQuery;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.exist.collections.Collection;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.persistent.*;
import org.exist.indexing.*;
import org.exist.indexing.ReindexScope;
import org.exist.indexing.StreamListener.ReindexMode;
import org.exist.indexing.lucene.PlainTextHighlighter.Offset;
import org.exist.indexing.lucene.PlainTextIndexConfig.PlainTextField;
import org.exist.numbering.NodeId;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.*;
import org.exist.storage.btree.DBException;
import org.exist.storage.vector.VectorStore;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
import org.exist.util.pool.NodePool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Expression;
import org.exist.xquery.QueryRewriter;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.lucene.QueryOptions;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.NodeValue;
import org.w3c.dom.*;
import org.xml.sax.helpers.AttributesImpl;

//import org.apache.lucene.search.FieldValueQuery;

import javax.annotation.Nullable;
import javax.xml.XMLConstants;
import java.io.IOException;
import java.util.*;
import java.util.Set;


/**
 * Class for handling all Lucene operations.
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:dannes@exist-db.org">Dannes Wessels</a>
 * @author <a href="mailto:ljo@exist-db.org">Leif-Jöran Olsson</a>
 */
public class LuceneIndexWorker implements OrderedValuesIndex, QNamedKeysIndex {

    public static final org.apache.lucene.document.FieldType TYPE_NODE_ID = new org.apache.lucene.document.FieldType();
    static {
        TYPE_NODE_ID.setTokenized(true);
        TYPE_NODE_ID.setStored(false);
        TYPE_NODE_ID.setOmitNorms(true);
        TYPE_NODE_ID.setStoreTermVectors(false);
        TYPE_NODE_ID.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
    }

    static final Logger LOG = LogManager.getLogger(LuceneIndexWorker.class);
    
    protected LuceneIndex index;
    
    private LuceneMatchListener matchListener = null;

    private XMLToQuery queryTranslator;

    private DBBroker broker;

    private DocumentImpl currentDoc = null;
    private ReindexMode mode = ReindexMode.STORE;
    
    private LuceneConfig config;
    private Deque<TextExtractor> contentStack = null;
    private Set<NodeId> nodesToRemove = null;
    private List<PendingDoc> nodesToWrite = null;
    private Document pendingDoc = null;
    private boolean canFlush = false;

    private int cachedNodesSize = 0;

    private int maxCachedNodesSize = 4096 * 1024;
    
    private Analyzer analyzer;

    public static final String FIELD_DOC_ID = "docId";
    public static final String FIELD_DOC_URI = "docUri";

    private final StreamListener listener = new LuceneStreamListener();

    public LuceneIndexWorker(LuceneIndex parent, DBBroker broker) {
        this.index = parent;
        this.broker = broker;
        this.queryTranslator = new XMLToQuery(index);
    }

    public String getIndexId() {
        return LuceneIndex.ID;
    }

    public String getIndexName() {
        return index.getIndexName();
    }

    public QueryRewriter getQueryRewriter(XQueryContext context) {
        return null;
    }

    public Object configure(IndexController controller, NodeList configNodes, Map<String, String> namespaces) throws DatabaseConfigurationException {
        LOG.debug("Configuring lucene index...");
        config = new LuceneConfig(configNodes, namespaces);
        return config;
    }


    public void flush() {
        switch (mode) {
            case STORE:
                flushStoreMode();
                break;
            case REMOVE_ALL_NODES:
                flushRemoveAllNodesMode();
                break;
            case REMOVE_SOME_NODES:
                flushRemoveSomeNodesMode();
                break;
            case REMOVE_BINARY:
                flushRemoveBinaryMode();
                break;
            case UNKNOWN:
            case REPLACE_DOCUMENT:
                // No-op here: these lifecycle modes are handled by subsequent explicit phases.
                break;
            default:
                LOG.warn("Unhandled ReindexMode in LuceneIndexWorker.flush(): {}", mode);
        }
    }

    @Override
    public void setDocument(DocumentImpl document) {
        setDocument(document, ReindexMode.UNKNOWN);
    }

    @Override
    public void setDocument(DocumentImpl document, ReindexMode newMode) {
        currentDoc = document;
        //config = null;
        contentStack = null;
        IndexSpec indexConf = document.getCollection().getIndexConfiguration(broker);
        if (indexConf != null) {
            config = (LuceneConfig) indexConf.getCustomIndexSpec(LuceneIndex.ID);
            if (config != null)
            	// Create a copy of the original LuceneConfig (there's only one per db instance), 
            	// so we can safely work with it.
            	config = new LuceneConfig(config);
        } else {
            config = LuceneConfig.DEFAULT_CONFIG;
        }
        mode = newMode;
    }

    @Override
    public void setMode(ReindexMode mode) {
        this.mode = mode;
        switch (mode) {
            case STORE:
                prepareStoreMode();
                break;
            case REMOVE_SOME_NODES:
                prepareRemoveSomeNodesMode();
                break;
            case UNKNOWN:
            case REPLACE_DOCUMENT:
            case REMOVE_ALL_NODES:
            case REMOVE_BINARY:
                // No mode-local buffers to initialize.
                break;
            default:
                LOG.warn("Unhandled ReindexMode in LuceneIndexWorker.setMode(): {}", mode);
        }
    }

    private void flushStoreMode() {
        write();
    }

    private void flushRemoveAllNodesMode() {
        removeDocument(currentDoc.getDocId());
    }

    private void flushRemoveSomeNodesMode() {
        removeNodes();
    }

    private void flushRemoveBinaryMode() {
        removePlainTextIndexes();
    }

    private void prepareStoreMode() {
        if (nodesToWrite == null) {
            nodesToWrite = new ArrayList<>();
        } else {
            nodesToWrite.clear();
        }
        cachedNodesSize = 0;
    }

    private void prepareRemoveSomeNodesMode() {
        nodesToRemove = new TreeSet<>();
    }

    @Override
    public DocumentImpl getDocument() {
        return currentDoc;
    }

    @Override
    public ReindexMode getMode() {
        return this.mode;
    }

    @Override
    public <T extends IStoredNode> IStoredNode getReindexRoot(IStoredNode<T> node, NodePath path, boolean insert, boolean includeSelf) {
        if (config == null) {
            return null;
	}
        if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
            // check if sibling attributes or parent element need reindexing
            IStoredNode parentStoredNode = node.getParentStoredNode();

            Iterator<LuceneIndexConfig> configIt = config.getConfig(parentStoredNode.getPath());
            while (configIt.hasNext()) {
                LuceneIndexConfig idxConfig = configIt.next();
                if (idxConfig.shouldReindexOnAttributeChange() && idxConfig.match(path)) {
                    // reindex from attribute parent
                    return parentStoredNode;
                }
            }
            NamedNodeMap attributes = parentStoredNode.getAttributes();
            for (int i = 0; i < attributes.getLength(); ++i) {
                IStoredNode<?> attr = (IStoredNode<?>) attributes.item(i);
                if (attr.getPrefix() != null && XMLConstants.XMLNS_ATTRIBUTE.equals(attr.getPrefix())) {
                    continue;
                }
                configIt = config.getConfig(attr.getPath());
                while (configIt.hasNext()) {
                    LuceneIndexConfig idxConfig = configIt.next();
                    if (idxConfig.shouldReindexOnAttributeChange() && idxConfig.match(path)) {
                        // reindex from attribute parent
                        return parentStoredNode;
                    }
                }
            }
	    // found no reason to reindex
            return null;
        }

        NodePath2 p = new NodePath2((NodePath2)path);
        boolean reindexRequired = false;

        if (node.getNodeType() == Node.ELEMENT_NODE && !includeSelf)
            p.removeLastNode();
        for (int i = 0; i < p.length(); i++) {
            if (config.matches(p)) {
                reindexRequired = true;
                break;
            }
            p.removeLastNode();
        }
        if (reindexRequired) {
            p = new NodePath2((NodePath2)path);
            IStoredNode topMost = null;
            IStoredNode currentNode = node;
            if (currentNode.getNodeType() != Node.ELEMENT_NODE)
                currentNode = currentNode.getParentStoredNode();
            while (currentNode != null) {
                if (config.matches(p))
                    topMost = currentNode;
                currentNode = currentNode.getParentStoredNode();
                p.removeLastNode();
            }
            return topMost;
        }
        return null;
    }

    @Override
    public StreamListener getListener() {
        return listener;
    }

    @Override
    public MatchListener getMatchListener(DBBroker broker, NodeProxy proxy) {
        boolean needToFilter = false;
        Match nextMatch = proxy.getMatches();
        while (nextMatch != null) {
            if (nextMatch.getIndexId().equals(LuceneIndex.ID)) {
                needToFilter = true;
                break;
            }
            nextMatch = nextMatch.getNextMatch();
        }
        if (!needToFilter)
            return null;
        if (matchListener == null)
            matchListener = new LuceneMatchListener(index, broker, proxy);
        else
            matchListener.reset(broker, proxy);
        return matchListener;
    }

    protected void removeDocument(int docId) {
        removeVectorStoreForDocument(currentDoc.getURI().toString());
    	IndexWriter writer = null;
        try {
            writer = index.getWriter();
            final Query docIdQuery = IntField.newExactQuery(FIELD_DOC_ID, docId);
            writer.deleteDocuments(docIdQuery);
        } catch (IOException e) {
            LOG.warn("Error while removing lucene index: {}", e.getMessage(), e);
        } finally {
            index.releaseWriter(writer);
            mode = ReindexMode.STORE;
        }
    }

    private void removeVectorStoreForDocument(String docPath) {
        final VectorStore vectorStore = broker.getBrokerPool().getVectorStore();
        if (vectorStore == null) {
            return;
        }
        final Txn txn = broker.getCurrentTransaction();
        if (txn == null) {
            return;
        }
        try {
            vectorStore.removeByDocument(txn, docPath);
        } catch (IOException e) {
            LOG.debug("Failed to remove vectors for document {}: {}", docPath, e.getMessage());
        }
    }

    private void removeVectorStoreForNodes(String docPath, Set<NodeId> nodeIds) {
        final VectorStore vectorStore = broker.getBrokerPool().getVectorStore();
        if (vectorStore == null || nodeIds == null || nodeIds.isEmpty()) {
            return;
        }
        final Txn txn = broker.getCurrentTransaction();
        if (txn == null) {
            return;
        }
        try {
            for (NodeId nodeId : nodeIds) {
                vectorStore.remove(txn, docPath, nodeId);
            }
        } catch (IOException e) {
            LOG.debug("Failed to remove vectors for nodes: {}", e.getMessage());
        }
    }

    protected void removePlainTextIndexes() {
    	IndexWriter writer = null;
        try {
            writer = index.getWriter();
            String uri = currentDoc.getURI().toString();
            Term dt = new Term(FIELD_DOC_URI, uri);
            writer.deleteDocuments(dt);
        } catch (IOException e) {
            LOG.warn("Error while removing lucene index: {}", e.getMessage(), e);
        } finally {
            index.releaseWriter(writer);
            mode = ReindexMode.STORE;
        }
    }

    @Override
    public void removeCollection(Collection collection, DBBroker broker, boolean reindex) {
        if (LOG.isDebugEnabled())
            LOG.debug("Removing collection {}", collection.getURI());
        final VectorStore vectorStore = broker.getBrokerPool().getVectorStore();
        final Txn txn = broker.getCurrentTransaction();
        if (vectorStore != null && txn != null) {
            try {
                for (Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext(); ) {
                    DocumentImpl doc = i.next();
                    vectorStore.removeByDocument(txn, doc.getURI().toString());
                }
            } catch (IOException | PermissionDeniedException | LockException e) {
                LOG.debug("Failed to remove vectors for collection: {}", e.getMessage());
            }
        }
        IndexWriter writer = null;
        try {
            writer = index.getWriter();
            for (Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext(); ) {
                DocumentImpl doc = i.next();
                writer.deleteDocuments(IntField.newExactQuery(FIELD_DOC_ID, doc.getDocId()));
            }
        } catch (IOException | PermissionDeniedException | LockException e) {
            LOG.error("Error while removing lucene index: {}", e.getMessage(), e);
        } finally {
            index.releaseWriter(writer);
            if (reindex) {
                try {
                    index.sync();
                } catch (DBException e) {
                    LOG.warn("Exception during reindex: {}", e.getMessage(), e);
                }
            }
            mode = ReindexMode.STORE;
        }
        if (LOG.isDebugEnabled())
            LOG.debug("Collection removed.");
    }

    /**
     * Remove specific nodes from the index. This method is used for node updates
     * and called from flush() if the worker is in {@link ReindexMode#REMOVE_SOME_NODES}
     * mode.
     */
    protected void removeNodes() {
    	if (nodesToRemove == null)
            return;
        removeVectorStoreForNodes(currentDoc.getURI().toString(), nodesToRemove);
        IndexWriter writer = null;
        try {
            writer = index.getWriter();

            final Query docIdQuery = IntField.newExactQuery(FIELD_DOC_ID, currentDoc.getDocId());
            for (NodeId nodeId : nodesToRemove) {
                // store the node id
                int nodeIdLen = nodeId.size();
                byte[] data = new byte[nodeIdLen + 2];
                ByteConversion.shortToByteH((short) nodeId.units(), data, 0);
                nodeId.serialize(data, 2);

                Term it = new Term(LuceneUtil.FIELD_NODE_ID, new BytesRef(data));

                TermQuery iq = new TermQuery(it);
                BooleanQuery q = new BooleanQuery.Builder()
                    .add(docIdQuery, BooleanClause.Occur.MUST)
                    .add(iq, BooleanClause.Occur.MUST)
                    .build();
                writer.deleteDocuments(q);
            }
        } catch (IOException e) {
            LOG.warn("Error while deleting lucene index entries: {}", e.getMessage(), e);
        } finally {
            index.releaseWriter(writer);
            nodesToRemove = null;
        }
    }

    /**
     * Query the index. Returns a node set containing all matching nodes. Each node
     * in the node set has a {@link LuceneMatch}
     * element attached, which stores the score and a link to the query which generated it.
     *
     * @param contextId current context id, identify to track the position inside nested XPath predicates
     * @param docs query will be restricted to documents in this set
     * @param contextSet if specified, returned nodes will be descendants of the nodes in this set
     * @param qnames query will be restricted to nodes with the qualified names given here
     * @param queryStr a lucene query string
     * @param axis which node is returned: the node in which a match was found or the corresponding ancestor
     *  from the contextSet
     * @param options the query options
     *
     * @return node set containing all matching nodes
     * 
     * @throws IOException if an I/O error occurs
     * @throws ParseException if the query cannot be parsed
     * @throws XPathException if an error occurs executing the query
     */
    public NodeSet query(final int contextId, final DocumentSet docs, @Nullable final NodeSet contextSet,
                         final List<QName> qnames, final String queryStr, final int axis, final QueryOptions options)
            throws IOException, ParseException, XPathException {
        return index.withSearcher(searcher -> {
            final List<QName> definedIndexes = getDefinedIndexes(qnames);
            final NodeSet resultSet = new NewArrayNodeSet();
            final boolean returnAncestor = axis == NodeSet.ANCESTOR;
            for (final QName qname : definedIndexes) {
                final String field = LuceneUtil.encodeQName(qname, index.getBrokerPool().getSymbols());
                final LuceneConfig config = getLuceneConfig(broker, docs);
                final Analyzer analyzer = getQueryAnalyzer(config,null, qname, options);
                Query query;
                if (queryStr == null) {
                    query = new MatchAllDocsQuery();
                } else {
                    final QueryParserWrapper parser = getQueryParser(field, analyzer, docs, qname, config, queryStr);
                    options.configureParser(parser.getConfiguration());
                    query = parser.parse(queryStr);
                    query = AnalyzingQueryRewriter.rewrite(query, analyzer);
                }
                query = filterByIndexType(query, field);
                final Optional<Map<String, QueryOptions.FacetQuery>> facets = options.getFacets();
                if (facets.isPresent() && config != null) {
                    query = drilldown(facets.get(), query, config);
                }
                if (config != null && config.hasBoostConfig()) {
                    query = FunctionScoreQuery.boostByValue(query, DoubleValuesSource.fromFloatField(LuceneUtil.FIELD_BOOST));
                }
                searchAndProcess(contextId, qname, docs, contextSet, resultSet,
                        returnAncestor, searcher, query, config);
            }
            return resultSet;
        });
    }

    /**
     * Query the index. Returns a node set containing all matching nodes. Each node
     * in the node set has a {@link LuceneMatch}
     * element attached, which stores the score and a link to the query which generated it.
     *
     * @param contextId current context id, identify to track the position inside nested XPath predicates
     * @param docs query will be restricted to documents in this set
     * @param contextSet if specified, returned nodes will be descendants of the nodes in this set
     * @param qnames query will be restricted to nodes with the qualified names given here
     * @param queryRoot an XML representation of the query, see {@link XMLToQuery}.
     * @param axis which node is returned: the node in which a match was found or the corresponding ancestor
     *  from the contextSet
     * @param options the query options
     *
     * @return node set containing all matching nodes
     *
     * @throws IOException if an I/O error occurs
     * @throws ParseException if the query cannot be parsed
     * @throws XPathException if an error occurs executing the query
     */
    public NodeSet query(int contextId, DocumentSet docs, NodeSet contextSet,
                         List<QName> qnames, Element queryRoot, int axis, QueryOptions options)
            throws IOException, ParseException, XPathException {
        return index.withSearcher(searcher -> {
            final List<QName> definedIndexes = getDefinedIndexes(qnames);
            final NodeSet resultSet = new NewArrayNodeSet();
            final boolean returnAncestor = axis == NodeSet.ANCESTOR;
            for (QName qname : definedIndexes) {
                String field = LuceneUtil.encodeQName(qname, index.getBrokerPool().getSymbols());
                LuceneConfig config = getLuceneConfig(broker, docs);
                analyzer = getQueryAnalyzer(config, null, qname, options);
                Query query = queryRoot == null ? new MatchAllDocsQuery() : queryTranslator.parse(field, queryRoot, analyzer, options);
                if (query != null) {
                    query = filterByIndexType(query, field);
                    Optional<Map<String, QueryOptions.FacetQuery>> facets = options.getFacets();
                    if (facets.isPresent() && config != null) {
                        query = drilldown(facets.get(), query, config);
                    }
                    if (config != null && config.hasBoostConfig()) {
                        query = FunctionScoreQuery.boostByValue(query, DoubleValuesSource.fromFloatField(LuceneUtil.FIELD_BOOST));
                    }
                    searchAndProcess(contextId, qname, docs, contextSet, resultSet,
                            returnAncestor, searcher, query, config);
                }
            }
            return resultSet;
        });
    }

    public NodeSet queryField(int contextId, DocumentSet docs, NodeSet contextSet,
                              String field, Element queryRoot, int axis, QueryOptions options)
            throws IOException, XPathException {
        return index.withSearcher(searcher -> {
            final NodeSet resultSet = new NewArrayNodeSet();
            final boolean returnAncestor = axis == NodeSet.ANCESTOR;
            final LuceneConfig config = getLuceneConfig(broker, docs);
            analyzer = getQueryAnalyzer(config, field, null, options);
            Query query = queryTranslator.parse(field, queryRoot, analyzer, options);
            if (query != null) {
                if (config != null && config.hasBoostConfig()) {
                    query = FunctionScoreQuery.boostByValue(query, DoubleValuesSource.fromFloatField(LuceneUtil.FIELD_BOOST));
                }
                searchAndProcess(contextId, null, docs, contextSet, resultSet,
                        returnAncestor, searcher, query, config);
            }
            return resultSet;
        });
    }

    private Query drilldown(Map<String, QueryOptions.FacetQuery> facets, Query baseQuery, LuceneConfig config) {
        final DrillDownQuery drillDownQuery = new DrillDownQuery(config.facetsConfig, baseQuery);
        for (final Map.Entry<String, QueryOptions.FacetQuery> facet : facets.entrySet()) {
            final FacetsConfig.DimConfig dimConfig = config.facetsConfig.getDimConfig(facet.getKey());
            facet.getValue().toQuery(facet.getKey(), drillDownQuery, dimConfig.hierarchical);
        }
        return drillDownQuery;
    }

    /**
     * Wraps the query in a BooleanQuery that filters by {@link LuceneUtil#FIELD_INDEX_TYPE}
     * when querying by qname, so we only match Lucene docs from that index config.
     * Required when multiple indexes (e.g. document vs abstract) share a collection.
     */
    private Query filterByIndexType(Query query, String indexField) {
        return new BooleanQuery.Builder()
                .add(query, BooleanClause.Occur.MUST)
                .add(new TermQuery(new Term(LuceneUtil.FIELD_INDEX_TYPE, indexField)), BooleanClause.Occur.FILTER)
                .build();
    }

    private void searchAndProcess(final int contextId, final QName qname, final DocumentSet docs,
                                  @Nullable final NodeSet contextSet, final NodeSet resultSet, final boolean returnAncestor,
                                  final SearcherTaxonomyManager.SearcherAndTaxonomy searcher, final Query query,
                                  final LuceneConfig config) throws IOException {
        final LuceneFacets facets = new LuceneFacets();
        final FacetsCollector facetsCollector = new FacetsCollector();
        final LuceneHitCollector collector = new LuceneHitCollector(qname, query, docs, contextSet, resultSet, returnAncestor, contextId, facets, facetsCollector);
        searcher.searcher().search(query, collector);

        // compute facets (skip if config or taxonomy missing)
        if (config != null && config.facetsConfig != null && searcher.taxonomyReader() != null) {
            facets.compute(searcher.taxonomyReader(), config.facetsConfig, facetsCollector);
        }
    }

    /**
     * Wrapper around Lucene {@link Facets}, which are computed after the search has finished.
     */
    protected static class LuceneFacets {

        private Facets facets;

        public Facets getFacets() {
            return facets;
        }

        /**
         * Compute facets based on the given {@link FacetsCollector}.
         *
         * @param reader the taxonomy reader
         * @param config the facets configuration
         * @param collector the facets collector
         *
         * @throws IOException if an I/O error occurs
         */
        public void compute(DirectoryTaxonomyReader reader, FacetsConfig config, FacetsCollector collector)
                throws IOException {
            this.facets = new FastTaxonomyFacetCounts(reader, config, collector);
        }
    }

    /**
     * Calls {@link LuceneUtil#extractTerms(Query, Map, IndexReader, boolean)}  to extract
     * the terms which would be matched by the given query.
     *
     * @param query to extract terms for
     * @return the map returned by {@link LuceneUtil#extractTerms(Query, Map, IndexReader, boolean)}
     * @throws IOException in case of Lucene IO error
     */
    public Map<Object, Query> getTerms(final Query query) throws IOException {
        return index.withReader(reader -> {
            final Map<Object, Query> termMap = new TreeMap<>();
            LuceneUtil.extractTerms(query, termMap, reader, false);
            return termMap;
        });
    }

    public NodeSet queryField(XQueryContext context, int contextId, DocumentSet docs, NodeSet contextSet,
            String field, String queryString, int axis, QueryOptions options)
            throws IOException, XPathException {
        return index.withSearcher(searcher -> {
            NodeSet resultSet = new NewArrayNodeSet();
            boolean returnAncestor = axis == NodeSet.ANCESTOR;
            LuceneConfig config = getLuceneConfig(context.getBroker(), docs);
            Analyzer analyzer = getQueryAnalyzer(config, field, null, options);
            LOG.debug("Using analyzer {} for {}", analyzer, queryString);
            QueryParserWrapper parser = getQueryParser(field, analyzer, docs);
            options.configureParser(parser.getConfiguration());
            Query query = parser.parse(queryString);
            query = AnalyzingQueryRewriter.rewrite(query, analyzer);
            searchAndProcess(contextId, null, docs, contextSet, resultSet,
                    returnAncestor, searcher, query, config);
            return resultSet;
        });
    }

    /**
     * Vector (KNN) search by field name. Returns k nearest nodes.
     *
     * @param contextId current context id
     * @param docs documents to search
     * @param contextSet optional context set for ancestor resolution
     * @param field vector field name (from vector-field config)
     * @param vector query vector
     * @param k number of nearest neighbours
     * @param options filter options (filter-query, filter, filter-facets)
     * @return node set of k nearest matches
     */
    public NodeSet searchVector(final int contextId, final DocumentSet docs, @Nullable final NodeSet contextSet,
            final String field, final float[] vector, final int k, final QueryOptions options)
            throws IOException, XPathException {
        return index.withSearcher(searcher -> {
            final NodeSet resultSet = new NewArrayNodeSet();
            final boolean returnAncestor = true;
            final LuceneConfig config = getLuceneConfig(broker, docs);
            Query filterQuery = buildVectorFilterQuery(options, config, docs);
            Query baseQuery = filterQuery != null
                    ? new KnnFloatVectorQuery(field, vector, k, filterQuery)
                    : new KnnFloatVectorQuery(field, vector, k);
            searchAndProcess(contextId, null, docs, contextSet, resultSet,
                    returnAncestor, searcher, baseQuery, config);
            return resultSet;
        });
    }

    /**
     * Vector (KNN) search by qnames. Resolves vector field from index config.
     *
     * @param contextId current context id
     * @param docs documents to search
     * @param contextSet optional context set for ancestor resolution
     * @param qnames index qnames (e.g. article)
     * @param vector query vector
     * @param k number of nearest neighbours
     * @param options filter options
     * @return node set of k nearest matches
     */
    public NodeSet searchVector(final int contextId, final DocumentSet docs, @Nullable final NodeSet contextSet,
            final List<QName> qnames, final float[] vector, final int k, final QueryOptions options)
            throws IOException, XPathException {
        return index.withSearcher(searcher -> {
            final List<QName> definedIndexes = getDefinedIndexes(qnames);
            final NodeSet resultSet = new NewArrayNodeSet();
            final boolean returnAncestor = true;
            final LuceneConfig config = getLuceneConfig(broker, docs);
            for (final QName qname : definedIndexes) {
                final LuceneIndexConfig idxConf = config != null ? config.getIndexConfigForQName(qname) : null;
                final LuceneVectorFieldConfig vecConf = getFirstVectorField(idxConf);
                if (vecConf == null) {
                    continue;
                }
                final String field = vecConf.getName();
                final String indexField = LuceneUtil.encodeQName(qname, index.getBrokerPool().getSymbols());
                Query filterQuery = buildVectorFilterQuery(options, config, docs);
                Query vectorQuery = filterQuery != null
                        ? new KnnFloatVectorQuery(field, vector, k, filterQuery)
                        : new KnnFloatVectorQuery(field, vector, k);
                Query baseQuery = filterByIndexType(vectorQuery, indexField);
                searchAndProcess(contextId, qname, docs, contextSet, resultSet,
                        returnAncestor, searcher, baseQuery, config);
            }
            return resultSet;
        });
    }

    @Nullable
    private static LuceneVectorFieldConfig getFirstVectorField(@Nullable final LuceneIndexConfig idxConf) {
        if (idxConf == null) {
            return null;
        }
        for (final AbstractFieldConfig fc : idxConf.getFacetsAndFields()) {
            if (fc instanceof LuceneVectorFieldConfig vfc) {
                return vfc;
            }
        }
        return null;
    }

    /** Build filter to restrict KNN to docs in the document set (index is shared across collections). */
    private Query buildDocsFilterQuery(final DocumentSet docs) {
        if (docs == null || docs.getDocumentCount() == 0) {
            return null;
        }
        final List<Query> docIdQueries = new ArrayList<>();
        for (final Iterator<DocumentImpl> it = docs.getDocumentIterator(); it.hasNext(); ) {
            docIdQueries.add(IntField.newExactQuery(FIELD_DOC_ID, it.next().getDocId()));
        }
        if (docIdQueries.size() == 1) {
            return docIdQueries.get(0);
        }
        final BooleanQuery.Builder b = new BooleanQuery.Builder();
        for (final Query q : docIdQueries) {
            b.add(q, BooleanClause.Occur.SHOULD);
        }
        return b.build();
    }

    @Nullable
    private Query buildVectorFilterQuery(final QueryOptions options, @Nullable final LuceneConfig config,
            final DocumentSet docs) throws XPathException {
        final List<Query> filters = new ArrayList<>(4);
        addIfNotNull(filters, buildDocsFilterQuery(docs));
        addIfNotNull(filters, buildKeywordFilter(options, config, docs));
        addIfNotNull(filters, buildRangeFilter(options));
        addIfNotNull(filters, buildFacetFilter(options, config));
        return combineFilters(filters);
    }

    private static void addIfNotNull(final List<Query> list, @Nullable final Query q) {
        if (q != null) {
            list.add(q);
        }
    }

    @Nullable
    private Query buildKeywordFilter(final QueryOptions options, @Nullable final LuceneConfig config,
            final DocumentSet docs) {
        if (options == null) {
            return null;
        }
        final String filterStr = options.getFilterQuery();
        if (filterStr == null || filterStr.isEmpty()) {
            return null;
        }
        final QueryParserWrapper parser = resolveFilterQueryParser(config, docs);
        if (parser == null) {
            return null;
        }
        try {
            return parser.parse(filterStr);
        } catch (XPathException e) {
            LOG.warn("Failed to parse filter-query '{}': {}", filterStr, e.getMessage());
            return null;
        }
    }

    @Nullable
    private QueryParserWrapper resolveFilterQueryParser(@Nullable final LuceneConfig config, final DocumentSet docs) {
        final LuceneConfig cfg = config != null ? config : getLuceneConfig(broker, docs);
        if (cfg == null) {
            return null;
        }
        final String[] fields = getSearchableFieldsForFilter(cfg, docs);
        if (fields.length == 0) {
            return null;
        }
        final Analyzer a = cfg.getAnalyzer((QName) null);
        if (a == null) {
            return null;
        }
        return fields.length == 1
                ? new ClassicQueryParserWrapper(fields[0], a)
                : new MultiFieldQueryParserWrapper(fields, a);
    }

    @Nullable
    private static Query buildRangeFilter(final QueryOptions options) {
        if (options == null) {
            return null;
        }
        final String field = options.getFilterField();
        final Object val = options.getFilterValue();
        if (field == null || val == null) {
            return null;
        }
        return val instanceof Number n
                ? LongField.newExactQuery(field, n.longValue())
                : new TermQuery(new Term(field, val.toString()));
    }

    @Nullable
    private Query buildFacetFilter(final QueryOptions options, @Nullable final LuceneConfig config) {
        if (options == null || config == null || config.facetsConfig == null) {
            return null;
        }
        final Optional<Map<String, QueryOptions.FacetQuery>> facets = options.getFacets();
        if (facets.isEmpty()) {
            return null;
        }
        return drilldown(facets.get(), new MatchAllDocsQuery(), config);
    }

    @Nullable
    private static Query combineFilters(final List<Query> filters) {
        if (filters.isEmpty()) {
            return null;
        }
        if (filters.size() == 1) {
            return filters.getFirst();
        }
        final BooleanQuery.Builder b = new BooleanQuery.Builder();
        for (final Query q : filters) {
            b.add(q, BooleanClause.Occur.MUST);
        }
        return b.build();
    }

    private String[] getSearchableFieldsForFilter(@Nullable final LuceneConfig config, final DocumentSet docs) {
        if (config == null || docs == null) {
            return new String[0];
        }
        for (final Iterator<Collection> i = docs.getCollectionIterator(); i.hasNext(); ) {
            final Collection col = i.next();
            final IndexSpec idxSpec = col.getIndexConfiguration(broker);
            if (idxSpec != null) {
                final LuceneConfig lc = (LuceneConfig) idxSpec.getCustomIndexSpec(LuceneIndex.ID);
                if (lc != null) {
                    for (final LuceneIndexConfig idx : lc.getIndexConfigurations()) {
                        final String[] names = idx.getSearchableFieldNames();
                        if (names.length > 0) {
                            return names;
                        }
                    }
                }
            }
        }
        return new String[0];
    }

    /**
     * Add SOLR formatted data to lucene index.
     * 
     * <pre>
     * {@code
     * <doc>
     *   <field name="name1" boost="value1">data1</field>
     *  <field name="name2">data2</field>
     * </doc>
     * }
     * </pre>
     * 
     * @param descriptor SOLR styled data 
     */
    public void indexNonXML(NodeValue descriptor) throws DatabaseConfigurationException {
        // Verify input
        if (!descriptor.getNode().getLocalName().contentEquals("doc")) {
            // throw exception
            LOG.error("Expected <doc> got <{}>", descriptor.getNode().getLocalName());
            return;
        }

        // Setup parser for SOLR syntax and parse
        PlainTextIndexConfig solrconfParser = new PlainTextIndexConfig();
        solrconfParser.parse(descriptor);
        
        if (pendingDoc == null) {
            // create Lucene document
            pendingDoc = new Document();

            // Set DocId. IntField (Points) for querying; SortedNumericDocValuesField for collectors (Lucene 10 consistency).
            final IntField fDocIdIdx = new IntField(FIELD_DOC_ID, currentDoc.getDocId(), Field.Store.NO);
            pendingDoc.add(fDocIdIdx);
            pendingDoc.add(new SortedNumericDocValuesField(FIELD_DOC_ID, currentDoc.getDocId()));
            pendingDoc.add(new FloatDocValuesField(LuceneUtil.FIELD_BOOST, 1.0f));

            // For binary documents the doc path needs to be stored
            final String uri = currentDoc.getURI().toString();

            Field fDocUri = new StringField(FIELD_DOC_URI, uri, Field.Store.YES);
            pendingDoc.add(fDocUri);
        }
        
        // Iterate over all found fields and write the data.
        for (PlainTextField field : solrconfParser.getFields()) {
            
            // Get field type configuration
            FieldType fieldType = config == null ? null : config.getFieldType(field.getName());
            
            Field.Store store = null;
            if (fieldType != null)
            	store = fieldType.getStore();
            if (store == null)
            	store = field.getStore();
            
            // Get name from SOLR field
            String contentFieldName = field.getName();

            // Actual field content ; Store flag can be set in solrField
            Field contentField = new TextField(contentFieldName, field.getData().toString(), store);

            pendingDoc.add(contentField);
        }
    }
    
    public void writeNonXML() {
    	IndexWriter writer = null;
        try {
            writer = index.getWriter();
            
            writer.addDocument(pendingDoc);
        } catch (IOException e) {
            LOG.warn("An exception was caught while indexing document: {}", e.getMessage(), e);

        } finally {
            index.releaseWriter(writer);
            pendingDoc = null;
            cachedNodesSize = 0;
        }
    }

    /**
     * SOLR
     *
     * @param context the xquery context
     * @param toBeMatchedURIs the URIs to match
     * @param queryText the query
     * @param fieldsToGet the fields to get
     * @param options the search options
     *
     * @return search report
     *
     * @throws XPathException if an error occurs executing the query
     * @throws IOException if an I/O error occurs
     */
    public NodeImpl search(final XQueryContext context, final List<String> toBeMatchedURIs, String queryText, String[] fieldsToGet, QueryOptions options) throws XPathException, IOException {

        return index.withSearcher(searcher -> {
            final Analyzer searchAnalyzer = index.getDefaultAnalyzer();

            // Setup query Version, default field, analyzer
            final QueryParserWrapper parser = getQueryParser("", searchAnalyzer, null);
            options.configureParser(parser.getConfiguration());
            Query query = parser.parse(queryText);
            query = AnalyzingQueryRewriter.rewrite(query, searchAnalyzer);

            // extract all used fields from query
            final String[] fields;
            if (fieldsToGet == null) {
                fields = LuceneUtil.extractFields(query, searcher.searcher().getIndexReader());
            } else {
                fields = fieldsToGet;
            }

            final PlainTextHighlighter highlighter = new PlainTextHighlighter(query, searcher.searcher().getIndexReader());

            context.pushDocumentContext();
            try {
                final MemTreeBuilder builder = context.getDocumentBuilder();
                builder.startDocument();

                // start root element
                final int nodeNr = builder.startElement("", "results", "results", null);

                // Perform actual search
                final BinarySearchCollector collector = new BinarySearchCollector(toBeMatchedURIs, builder, fields, searchAnalyzer, highlighter);
                searcher.searcher().search(query, collector);

                // finish root element
                builder.endElement();

                //System.out.println(builder.getDocument().toString());

                // TODO check
                return builder.getDocument().getNode(nodeNr);
            } finally {
                context.popDocumentContext();
            }

        });
    }

    private class BinarySearchCollector implements Collector {
        private final List<String> toBeMatchedURIs;
        private final MemTreeBuilder builder;
        private final String[] fields;
        private final Analyzer searchAnalyzer;
        private final PlainTextHighlighter highlighter;

        public BinarySearchCollector(List<String> toBeMatchedURIs, MemTreeBuilder builder, String[] fields, Analyzer searchAnalyzer, PlainTextHighlighter highlighter) {

            this.toBeMatchedURIs = toBeMatchedURIs;
            this.builder = builder;
            this.fields = fields;
            this.searchAnalyzer = searchAnalyzer;
            this.highlighter = highlighter;
        }

        @Override
        public LeafCollector getLeafCollector(LeafReaderContext context) throws IOException {
            return new BinarySearchLeafCollector(context.reader());
        }

        @Override
        public ScoreMode scoreMode() {
            return ScoreMode.COMPLETE;
        }

        private class BinarySearchLeafCollector implements LeafCollector {
            private final LeafReader reader;
            private Scorable scorer;

            public BinarySearchLeafCollector(LeafReader reader) {
                this.reader = reader;
            }

            @Override
            public void setScorer(Scorable scorer) throws IOException {
                this.scorer = scorer;
            }

            @Override
            public void collect(int docNum) throws IOException {
                Document doc = reader.storedFields().document(docNum);

                // Get URI field of document
                String fDocUri = doc.get(FIELD_DOC_URI);

                // Get score
                float score = scorer.score();

                // Check if document URI has a full match or if a
                // document is in a collection
                if (isDocumentMatch(fDocUri, toBeMatchedURIs)) {

                    try(final LockedDocument lockedStoredDoc = broker.getXMLResource(XmldbURI.createInternal(fDocUri), LockMode.READ_LOCK)) {
                        // try to read document to check if user is allowed to access it
                        if (lockedStoredDoc == null) {
                            return;
                        }

                        // setup attributes
                        AttributesImpl attribs = new AttributesImpl();
                        attribs.addAttribute("", "uri", "uri", "CDATA", fDocUri);
                        attribs.addAttribute("", "score", "score", "CDATA", "" + score);

                        // write element and attributes
                        builder.startElement("", "search", "search", attribs);
                        for (String field : fields) {
                            String[] fieldContent = doc.getValues(field);
                            attribs.clear();
                            attribs.addAttribute("", "name", "name", "CDATA", field);
                            for (String content : fieldContent) {
                                List<Offset> offsets = highlighter.getOffsets(content, searchAnalyzer);
                                builder.startElement("", "field", "field", attribs);
                                if (offsets != null) {
                                    highlighter.highlight(content, offsets, builder);
                                } else {
                                    builder.characters(content);
                                }
                                builder.endElement();
                            }
                        }
                        builder.endElement();

                        // clean attributes
                        attribs.clear();
                    } catch (PermissionDeniedException e) {
                        // not allowed to read the document: ignore the match.
                    }
                }
            }
        }
    }

    public String getFieldContent(int docId, String field) throws IOException {
        try {
            return index.withSearcher(searcher -> {
                final Query docIdQuery = IntField.newExactQuery(FIELD_DOC_ID, docId);
                final TopDocs topDocs = searcher.searcher().search(docIdQuery, 1);
                if (topDocs.totalHits.value() == 0) {
                    return null;
                }
                final Document doc = searcher.searcher().storedFields().document(topDocs.scoreDocs[0].doc);
                return doc.get(field);
            });
        } catch (XPathException e) {
            throw new IOException("Unexpected XPath error in getFieldContent", e);
        }
    }

    /**
     * Resolve field values by eXist document ID and node ID. Uses query-by-FIELD_DOC_ID
     * and FIELD_NODE_ID so the lookup is valid across reader refreshes (avoids volatile Lucene docID).
     * Multiple indexed nodes per document each have the same docId but different nodeId.
     */
    public IndexableField[] getFieldByExistDocId(final int existDocId, final NodeId nodeId, final String field) throws IOException {
        try {
            return index.withSearcher(searcher -> {
                final Query q = docIdAndNodeIdQuery(existDocId, nodeId);
                final TopDocs topDocs = searcher.searcher().search(q, 1);
                if (topDocs.totalHits.value() == 0) {
                    return new IndexableField[0];
                }
                final int luceneDocId = topDocs.scoreDocs[0].doc;
                final Set<String> fields = Collections.singleton(field);
                final Document doc = searcher.searcher().storedFields().document(luceneDocId, fields);
                final Object fieldsObj = doc.getFields(field);
                if (fieldsObj instanceof List) {
                    final List<IndexableField> fieldList = (List<IndexableField>) fieldsObj;
                    final IndexableField[] result = new IndexableField[fieldList.size()];
                    int i = 0;
                    for (final IndexableField f : fieldList) {
                        result[i++] = f;
                    }
                    return result;
                } else if (fieldsObj instanceof IndexableField[]) {
                    return (IndexableField[]) fieldsObj;
                }
                return new IndexableField[0];
            });
        } catch (XPathException e) {
            throw new IOException("Unexpected XPath error in getFieldByExistDocId", e);
        }
    }

    /**
     * Resolve binary field by eXist document ID and node ID. Uses query-by-FIELD_DOC_ID
     * and FIELD_NODE_ID so the lookup is valid across reader refreshes (avoids volatile Lucene docID).
     */
    public @Nullable BytesRef getBinaryFieldByExistDocId(final int existDocId, final NodeId nodeId, final String field) throws IOException {
        try {
            return index.withSearcher(searcher -> {
                final Query q = docIdAndNodeIdQuery(existDocId, nodeId);
                final TopDocs topDocs = searcher.searcher().search(q, 1);
                if (topDocs.totalHits.value() == 0) {
                    return null;
                }
                final int luceneDocId = topDocs.scoreDocs[0].doc;
                return getBinaryFieldForLuceneDocId(luceneDocId, field);
            });
        } catch (XPathException e) {
            throw new IOException("Unexpected XPath error in getBinaryFieldByExistDocId", e);
        }
    }

    private @Nullable BytesRef getBinaryFieldForLuceneDocId(final int luceneDocId, final String field) throws IOException {
        return index.withReader(reader -> {
            final List<LeafReaderContext> leaves = reader.leaves();
            for (final LeafReaderContext context : leaves) {
                final int id = luceneDocId - context.docBase;
                if (id >= 0 && id < context.reader().numDocs()) {
                    final BinaryDocValues values = context.reader().getBinaryDocValues(field);
                    if (values != null && values.advanceExact(id)) {
                        final BytesRef bytes = values.binaryValue();
                        if (bytes != null && bytes.length > 0) {
                            return bytes;
                        }
                    }
                }
            }
            return null;
        });
    }

    private static Query docIdAndNodeIdQuery(final int existDocId, final NodeId nodeId) {
        final int nodeIdLen = nodeId.size();
        final byte[] data = new byte[nodeIdLen + 2];
        ByteConversion.shortToByteH((short) nodeId.units(), data, 0);
        nodeId.serialize(data, 2);
        final Term nodeTerm = new Term(LuceneUtil.FIELD_NODE_ID, new BytesRef(data));
        return new BooleanQuery.Builder()
            .add(IntField.newExactQuery(LuceneIndexWorker.FIELD_DOC_ID, existDocId), BooleanClause.Occur.MUST)
            .add(new TermQuery(nodeTerm), BooleanClause.Occur.MUST)
            .build();
    }

    public boolean hasIndex(int docId) throws IOException {
        try {
            return index.withSearcher(searcher -> {
                final Query docIdQuery = IntField.newExactQuery(FIELD_DOC_ID, docId);
                final TopDocs topDocs = searcher.searcher().search(docIdQuery, 1);
                return topDocs.totalHits.value() > 0;
            });
        } catch (XPathException e) {
            throw new IOException("Unexpected XPath error in hasIndex", e);
        }
    }
    
    /**
     * Check if Lucene found document matches specified documents or collections.
     * Collections should end with "/".
     * 
     * @param docUri The uri of the document found by lucene
     * @param toBeMatchedUris List of document and collection URIs
     *
     * @return true if {@code docUri} is matched or is in collection, false otherwise
     */
    private boolean isDocumentMatch(String docUri, List<String> toBeMatchedUris){
        
        if(docUri==null){
            LOG.error("docUri is null.");
            return false;
        }
        
        if(toBeMatchedUris==null){
            LOG.error("match is null.");
            return false;
        }
        
        for(String doc : toBeMatchedUris){
            if( docUri.startsWith(doc) ){
                return true;
            }       
        }
        return false;
    }

    private class LuceneHitCollector implements Collector {

        private final QName qname;
        private final DocumentSet docs;
        private @Nullable final NodeSet contextSet;
        private final NodeSet resultSet;
        private final boolean returnAncestor;
        private final int contextId;
        private final Query query;
        private final LuceneFacets facets;
        private final FacetsCollector chainedCollector;

        private LuceneHitCollector(final QName qname, final Query query, final DocumentSet docs, @Nullable final NodeSet contextSet, final NodeSet resultSet, final boolean returnAncestor, final int contextId, final LuceneFacets facets, final FacetsCollector nextCollector) {
            this.qname = qname;
            this.docs = docs;
            this.contextSet = contextSet;
            this.resultSet = resultSet;
            this.returnAncestor = returnAncestor;
            this.contextId = contextId;
            this.query = query;
            this.facets = facets;
            this.chainedCollector = nextCollector;
        }

        @Override
        public LeafCollector getLeafCollector(LeafReaderContext context) throws IOException {
            final LeafCollector chainedLeafCollector;
            try {
                chainedLeafCollector = chainedCollector.getLeafCollector(context);
            } catch (AssertionError e) {
                // If the segment doesn't contain any facet-relevant data, FacetsCollector.getLeafCollector()
                // might throw an AssertionError (specifically in doSetNextReader).
                // We still want to collect hits for the main query.
                return new LuceneHitLeafCollector(context, null);
            }
            return new LuceneHitLeafCollector(context, chainedLeafCollector);
        }

        @Override
        public ScoreMode scoreMode() {
            return ScoreMode.COMPLETE;
        }

        private class LuceneHitLeafCollector implements LeafCollector {
            private final LeafReader reader;
            private final int docBase;
            private final LeafCollector chainedLeafCollector;
            private Scorable scorer;
            private SortedNumericDocValues docIdValues;
            private BinaryDocValues nodeIdValues;

            public LuceneHitLeafCollector(LeafReaderContext context, LeafCollector chainedLeafCollector) throws IOException {
                this.reader = context.reader();
                this.docBase = context.docBase;
                this.chainedLeafCollector = chainedLeafCollector;
                this.docIdValues = reader.getSortedNumericDocValues(FIELD_DOC_ID);
                this.nodeIdValues = reader.getBinaryDocValues(LuceneUtil.FIELD_NODE_ID_DV);
            }

            @Override
            public void setScorer(Scorable scorer) throws IOException {
                this.scorer = scorer;
                if (chainedLeafCollector != null) {
                    chainedLeafCollector.setScorer(scorer);
                }
            }

            @Override
            public void finish() throws IOException {
                if (chainedLeafCollector != null) {
                    chainedLeafCollector.finish();
                }
            }

            @Override
            public void collect(int doc) throws IOException {
                float score = scorer.score();
                int docId;
                if (docIdValues != null && docIdValues.advanceExact(doc)) {
                    docId = (int) docIdValues.nextValue();
                } else {
                    docId = reader.storedFields().document(doc).getField(FIELD_DOC_ID).numericValue().intValue();
                }
                DocumentImpl storedDocument = docs.getDoc(docId);
                if (storedDocument == null) {
                    return;
                }
                if (nodeIdValues != null && nodeIdValues.advanceExact(doc)) {
                    final BytesRef ref = nodeIdValues.binaryValue();
                    int units = ByteConversion.byteToShortH(ref.bytes, ref.offset);
                    NodeId nodeId = index.getBrokerPool().getNodeFactory().createFromData(units, ref.bytes, ref.offset + 2);

                    NodeProxy storedNode = new NodeProxy(null, storedDocument, nodeId);
                    if (qname != null) {
                        storedNode.setNodeType(qname.getNameType() == ElementValue.ATTRIBUTE ? Node.ATTRIBUTE_NODE : Node.ELEMENT_NODE);
                    }
                    LuceneMatch match = createMatch(doc, score, nodeId, docBase);
                    processHit(doc, storedDocument, storedNode, match);
                }
            }

            private void processHit(int doc, DocumentImpl storedDocument, NodeProxy storedNode, LuceneMatch match) throws IOException {
                if (contextSet != null) {
                    int sizeHint = contextSet.getSizeHint(storedDocument);
                    if (returnAncestor) {
                        processAncestorHit(doc, storedNode, match, sizeHint);
                    } else {
                        processDirectHit(doc, storedNode, match, sizeHint);
                    }
                } else {
                    storedNode.addMatch(match);
                    resultSet.add(storedNode);
                    chainCollect(doc);
                }
            }

            private void processAncestorHit(int doc, NodeProxy storedNode, LuceneMatch match, int sizeHint) throws IOException {
                NodeProxy parentNode = contextSet.parentWithChild(storedNode, true, true, NodeProxy.UNKNOWN_NODE_LEVEL);
                if (parentNode != null) {
                    parentNode.addMatch(match);
                    resultSet.add(parentNode, sizeHint);
                    if (Expression.NO_CONTEXT_ID != contextId) {
                        parentNode.deepCopyContext(storedNode, contextId);
                    } else {
                        parentNode.copyContext(storedNode);
                    }
                    chainCollect(doc);
                }
            }

            private void processDirectHit(int doc, NodeProxy storedNode, LuceneMatch match, int sizeHint) throws IOException {
                storedNode.addMatch(match);
                NodeProxy fromContext = contextSet.get(storedNode);
                if (fromContext == null) {
                    fromContext = contextSet.parentWithChild(storedNode, true, true, NodeProxy.UNKNOWN_NODE_LEVEL);
                }
                if (fromContext != null) {
                    if (Expression.NO_CONTEXT_ID != contextId) {
                        storedNode.deepCopyContext(fromContext, contextId);
                    } else {
                        storedNode.copyContext(fromContext);
                    }
                }
                resultSet.add(storedNode, sizeHint);
                chainCollect(doc);
            }

            private void chainCollect(int doc) throws IOException {
                if (chainedLeafCollector != null) {
                    chainedLeafCollector.collect(doc);
                }
            }

            private LuceneMatch createMatch(final int docId, final float score, final NodeId nodeId, final int docBase) {
                final LuceneMatch match = new LuceneMatch(contextId, docId + docBase, nodeId, query, facets);
                match.setScore(score);
                return match;
            }
        }
    }

    /**
     * Check index configurations for all collection in the given DocumentSet and return
     * a list of QNames, which have indexes defined on them.
     *
     * @param qnames the qnames to find the findexes for
     *
     * @return List of QName objects on which indexes are defined
     *
     * @throws IOException if an I/O error occurs
     */
    public List<QName> getDefinedIndexes(final List<QName> qnames) throws IOException {
        final List<QName> indexes = new ArrayList<>(20);
        if (qnames != null && !qnames.isEmpty()) {
            for (final QName qname : qnames) {
                if (qname.getLocalPart() == null || qname.getLocalPart().equals(QName.WILDCARD)
                        || qname.getNamespaceURI() == null || qname.getNamespaceURI().equals(QName.WILDCARD)) {
                    getDefinedIndexesFor(qname, indexes);
                } else {
                    indexes.add(qname);
                }
            }
        } else {
            getDefinedIndexesFor(null, indexes);
        }
        return indexes;
    }

    private void getDefinedIndexesFor(final QName qname, final List<QName> indexes) throws IOException {
        index.<Void>withReader(reader -> {
            for (final LeafReaderContext context : reader.leaves()) {
                for (final FieldInfo info : context.reader().getFieldInfos()) {
                    if (!FIELD_DOC_ID.equals(info.name)) {
                        final QName name = LuceneUtil.decodeQName(info.name, index.getBrokerPool().getSymbols());
                        if (name != null && name.getLocalPart() != null && !name.getLocalPart().isEmpty()
                                && (qname == null || matchQName(qname, name))
                                && !indexes.contains(name)) {
                            indexes.add(name);
                        }
                    }
                }
            }
            return null;
        });
    }

    private static boolean matchQName(QName qname, QName candidate) {
        boolean match = true;
        if (qname.getLocalPart() != null && (!qname.getLocalPart().equals(QName.WILDCARD))) {
            match = qname.getLocalPart().equals(candidate.getLocalPart());
        }
        if (match && qname.getNamespaceURI() != null && (!qname.getNamespaceURI().equals(QName.WILDCARD)) && !qname.getNamespaceURI().isEmpty()) {
            match = qname.getNamespaceURI().equals(candidate.getNamespaceURI());
        }
        return match;
    }

    /**
     * Derive unique QNames from a node set. Used when util:index-keys is called
     * with nodes but no QNAMES_KEY hint, so we restrict scanning to the fields
     * corresponding to those nodes (avoids double-counting when multiple indexes
     * share a collection, e.g. path-based and qname-based).
     *
     * @param nodes the node set (elements or attributes)
     * @return list of unique QNames, or empty list if nodes is empty
     */
    private static List<QName> getQNamesFromNodes(NodeSet nodes) {
        final Set<QName> seen = new ObjectArraySet<>();
        for (NodeProxy proxy : nodes) {
            final QName qname = proxy.getQName();
            if (qname != null) {
                seen.add(qname);
            }
        }
        return new ArrayList<>(seen);
    }

    /**
     * Return the analyzer to be used for the given field or qname. Either field
     * or qname should be specified.
     *
     * @param config the lucene config
     * @param field the analyzer field
     * @param qname the analyzer qname
     * @param opts the query options
     *
     * @return the analyzer or null
     */
    @Nullable protected Analyzer getQueryAnalyzer(LuceneConfig config, String field, QName qname, QueryOptions opts) {
        if (config != null) {
            final Analyzer analyzer;
            if (opts.getQueryAnalyzerId() != null) {
                analyzer = config.getAnalyzerById(opts.getQueryAnalyzerId());
                if (analyzer == null) {
                    String msg = String.format("getAnalyzerById('%s') returned null!", opts.getQueryAnalyzerId());
                    LOG.error(msg);
                }
            }
            else if (field == null) {
                analyzer = config.getAnalyzer(qname);
            } else {
                analyzer = config.getAnalyzer(field);
            }
            if (analyzer != null) {
                return analyzer;
            }
        }
        return index.getDefaultAnalyzer();
    }

    /**
     * Return the first configuration found for documents in the document set.
     *
     * @param broker the database broker
     * @param docs the document set
     *
     * @return the lucene config or null
     */
    public static LuceneConfig getLuceneConfig(DBBroker broker, DocumentSet docs) {
        for (Iterator<Collection> i = docs.getCollectionIterator(); i.hasNext(); ) {
            try (Collection collection = i.next();) {
                IndexSpec idxConf = collection.getIndexConfiguration(broker);
                if (idxConf != null) {
                    LuceneConfig config = (LuceneConfig) idxConf.getCustomIndexSpec(LuceneIndex.ID);
                    if (config != null) {
                        return config;
                    }
                }
            }
        }
        return LuceneConfig.DEFAULT_CONFIG;
    }

    protected QueryParserWrapper getQueryParser(String field, Analyzer analyzer, DocumentSet docs) {
        return getQueryParser(field, analyzer, docs, null, null, null);
    }

    /**
     * Get a query parser for the given field(s). When the index has nested Lucene
     * fields (e.g. lemma, pos) and the query contains explicit field refs (field:value),
     * uses MultiFieldQueryParser so that regex and other syntax work correctly in all
     * fields. Fixes GitHub #4389. When index="no" and the query has no field refs,
     * keeps single-field parser so unqualified terms match nothing.
     *
     * <p>Heuristic: queryStr.contains(":") detects field refs. A bare colon can appear
     * inside regex ({@code field:/a:b/}) or phrases ({@code field:"x:y"}), which may
     * cause MultiFieldQueryParser to be used even when the user did not intend a
     * field-qualified term; this is conservative and generally acceptable.
     */
    protected QueryParserWrapper getQueryParser(String field, Analyzer analyzer, DocumentSet docs,
            QName qname, LuceneConfig luceneConfig, String queryStr) {
        final String safeField = field != null ? field : "";
        /* Use MultiFieldQueryParser only for index="no" with nested fields. Fixes #4389
         * (regex in multiple fields). For index="yes", keep single-field parser to avoid
         * regressions (e.g. facets query-field-no-expression). */
        if (luceneConfig != null && qname != null && queryStr != null && queryStr.contains(":")) {
            LuceneIndexConfig idxConfig = luceneConfig.getIndexConfigForQName(qname);
            if (idxConfig != null && !idxConfig.doIndex()) {
                String[] searchableFields = idxConfig.getSearchableFieldNames();
                if (searchableFields.length > 0) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Using MultiFieldQueryParser for index=\"no\" qname={} fields={}",
                                qname, java.util.Arrays.toString(searchableFields));
                    }
                    return new MultiFieldQueryParserWrapper(searchableFields, analyzer);
                }
            }
        }
        if (docs != null) {
            for (Iterator<Collection> i = docs.getCollectionIterator(); i.hasNext(); ) {
                Collection collection = i.next();
                IndexSpec idxConf = collection.getIndexConfiguration(broker);
                if (idxConf != null) {
                    LuceneConfig config = (LuceneConfig) idxConf.getCustomIndexSpec(LuceneIndex.ID);
                    if (config != null) {
                        QueryParserWrapper parser = config.getQueryParser(safeField, analyzer);
                        if (parser != null) {
                            return parser;
                        }
                    }
                }
            }
        }
        return new ClassicQueryParserWrapper(safeField, analyzer);
    }

    public boolean checkIndex(DBBroker broker) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Occurrences[] scanIndex(XQueryContext context, DocumentSet docs, NodeSet nodes, Map<?,?> hints) {
        try {
            List<QName> qnames = hints == null ? null : (List<QName>)hints.get(QNAMES_KEY);
            if (LOG.isDebugEnabled()) {
                LOG.debug("scanIndex: qnamesFromHints={}, nodes={}, docCount={}",
                    qnames, nodes != null ? nodes.getItemCount() : 0, docs != null ? docs.getDocumentCount() : 0);
            }
            if (qnames == null && nodes != null && !nodes.isEmpty()) {
                qnames = getQNamesFromNodes(nodes);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("scanIndex: qnamesFromNodes={}", qnames);
                }
            }
            qnames = getDefinedIndexes(qnames);
            if (LOG.isDebugEnabled()) {
                LOG.debug("scanIndex: qnamesAfterGetDefined={}", qnames);
            }
            //Expects a StringValue
            String start = null;
            String end = null;
            long max = Long.MAX_VALUE;
            if (hints != null) {
                Object vstart = hints.get(START_VALUE);
                Object vend = hints.get(END_VALUE);
                start = vstart == null ? null : vstart.toString();
                end = vend == null ? null : vend.toString();
                IntegerValue vmax = (IntegerValue) hints.get(VALUE_COUNT);
                max = vmax == null ? Long.MAX_VALUE : vmax.getValue();
            }
            Occurrences[] result = scanIndexByQName(qnames, docs, nodes, start, end, max);
            if (LOG.isDebugEnabled()) {
                LOG.debug("scanIndex: resultCount={}", result != null ? result.length : 0);
            }
            return result;
        } catch (IOException e) {
            LOG.warn("Failed to scan index occurrences: {}", e.getMessage(), e);
            return new Occurrences[0];
        }
    }

    public Occurrences[] scanIndexByField(String field, DocumentSet docs, Map<?,?> hints) {
        try {
            //Expects a StringValue
            String start = null;
            String end = null;
            long max = Long.MAX_VALUE;
            if (hints != null) {
                Object vstart = hints.get(START_VALUE);
                Object vend = hints.get(END_VALUE);
                start = vstart == null ? null : vstart.toString();
                end = vend == null ? null : vend.toString();
                IntegerValue vmax = (IntegerValue) hints.get(VALUE_COUNT);
                max = vmax == null ? Long.MAX_VALUE : vmax.getValue();
            }
            return scanIndexByField(field, docs, null, start, end, max);
        } catch (IOException e) {
            LOG.warn("Failed to scan index occurrences: {}", e.getMessage(), e);
            return new Occurrences[0];
        }
    }

    private Occurrences[] scanIndexByQName(List<QName> qnames, DocumentSet docs, NodeSet nodes, String start, String end, long max) throws IOException {
        final TreeMap<String, Occurrences> map = new TreeMap<>();
        index.withReader(reader -> {
            for (QName qname : qnames) {
                String field = LuceneUtil.encodeQName(qname, index.getBrokerPool().getSymbols());
                if (LOG.isDebugEnabled()) {
                    LOG.debug("scanIndexByQName: qname={} -> field={}", qname, field);
                }
                doScanIndex(docs, nodes, start, end, max, map, reader, field);
            }
            return null;
        });
        Occurrences[] occur = new Occurrences[map.size()];
        return map.values().toArray(occur);
    }

    private Occurrences[] scanIndexByField(String field, DocumentSet docs, NodeSet nodes, String start, String end, long max) throws IOException {
        final TreeMap<String, Occurrences> map = new TreeMap<>();
        index.withReader(reader -> {
            doScanIndex(docs, nodes, start, end, max, map, reader, field);
            return null;
        });
        Occurrences[] occur = new Occurrences[map.size()];
        return map.values().toArray(occur);
    }

    private void doScanIndex(DocumentSet docs, NodeSet nodes, String start, String end, long max, TreeMap<String, Occurrences> map, IndexReader reader, String field) throws IOException {
        List<LeafReaderContext> leaves = reader.leaves();
        for (LeafReaderContext context : leaves) {
            LeafReader leafReader = context.reader();
            // FIXME: docidvalues is null and likely should not be
            SortedNumericDocValues docIdValues = leafReader.getSortedNumericDocValues(FIELD_DOC_ID);
            BinaryDocValues nodeIdValues = leafReader.getBinaryDocValues(LuceneUtil.FIELD_NODE_ID_DV);
            Bits liveDocs = leafReader.getLiveDocs();
            Terms terms = leafReader.terms(field);
            if (LOG.isDebugEnabled() && terms == null) {
                LOG.debug("doScanIndex: field={} terms=null (field not in segment)", field);
            }
            if (terms == null) {
                continue;
            }
            TermsEnum termsIter = terms.iterator();

            if (termsIter.next() == null) {
                continue;
            }
            do {
                if (map.size() >= max) {
                    break;
                }
                BytesRef ref = termsIter.term();
                String term = ref.utf8ToString();
                if ((end != null && term.compareTo(end) > 0) || (start != null && !term.toLowerCase().startsWith(start.toLowerCase()))) {
                    continue;
                }
                PostingsEnum postings = termsIter.postings(null, PostingsEnum.NONE);
                while (postings.nextDoc() != PostingsEnum.NO_MORE_DOCS) {
                    if (liveDocs != null && !liveDocs.get(postings.docID())) {
                        continue;
                    }
                    int docId;
                    if (docIdValues != null && docIdValues.advanceExact(postings.docID())) {
                        docId = (int) docIdValues.nextValue();
                    } else {
                        docId = leafReader.storedFields().document(postings.docID()).getField(FIELD_DOC_ID).numericValue().intValue();
                    }
                    DocumentImpl storedDocument = docs.getDoc(docId);
                    if (storedDocument == null) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("doScanIndex: docId={} not in docs (getDoc=null)", docId);
                        }
                        continue;
                    }
                    if (nodes == null) {
                        addOccurrence(map, term, postings.freq(), storedDocument);
                    } else {
                        if (nodeIdValues == null || !nodeIdValues.advanceExact(postings.docID())) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("doScanIndex: nodeIdValues null={} (skipping term={} doc={})",
                                    nodeIdValues == null, term, postings.docID());
                            }
                            continue;
                        }
                        final BytesRef nodeIdRef = nodeIdValues.binaryValue();
                        int units = ByteConversion.byteToShortH(nodeIdRef.bytes, nodeIdRef.offset);
                        NodeId nodeId = index.getBrokerPool().getNodeFactory().createFromData(units, nodeIdRef.bytes, nodeIdRef.offset + 2);
                        if (nodes.get(storedDocument, nodeId) != null) {
                            addOccurrence(map, term, postings.freq(), storedDocument);
                        } else if (LOG.isDebugEnabled()) {
                            LOG.debug("doScanIndex: term={} docId={} nodeId not in nodes", term, docId);
                        }
                    }
                }

            } while (termsIter.next() != null);
        }
    }

    private void addOccurrence(TreeMap<String, Occurrences> map, String term, int frequency, DocumentImpl storedDocument) throws IOException {
        Occurrences oc = map.get(term);
        if (oc == null) {
            oc = new Occurrences(term);
            map.put(term, oc);
        }
        oc.addDocument(storedDocument);
        oc.addOccurrences(frequency);
    }

    /**
     * Adds the passed character sequence to the lucene index. We
     * create one lucene document per XML node, using 2 fields to identify
     * the node:
     *
     * <ul>
     *  <li>docId: eXist-internal document id of the node, stored as string.</li>
     *  <li>nodeId: the id of the node, stored in binary compressed form.</li>
     * </ul>
     *
     * The text is indexed into a field whose name encodes the qualified name of
     * the node. The qualified name is stored as a hex sequence pointing into the
     * global symbol table.
     *
     * @param nodeId the node if
     * @param qname the qname of the node
     * @param path the node path
     * @param config the lucene index config
     * @param content the content of the node
     */
    protected void indexText(final NodeId nodeId, final QName qname, final NodePath path, final LuceneIndexConfig config, final CharSequence content) {
        final PendingDoc pending = new PendingDoc(nodeId, qname, path, content, config.getBoost(), config);
        addPending(pending);
    }

    /**
     * Adds the passed character sequence to the lucene index.
     * This version uses the AttrImpl for node specific attribute match boosting.
     *
     * @param attribs the attributes
     * @param nodeId the node id
     * @param qname the qname of the node
     * @param path the path of the node
     * @param config the lucene index config
     * @param content the content of the node
     */
    protected void indexText(final java.util.Collection<AttrImpl> attribs, final NodeId nodeId, final QName qname, final NodePath path, final LuceneIndexConfig config, final CharSequence content) {
        final PendingDoc pending = new PendingDoc(nodeId, qname, path, content, config.getAttrBoost(attribs), config);
        addPending(pending);
    }
    
    private void addPending(final PendingDoc pending) {
        nodesToWrite.add(pending);
        cachedNodesSize += pending.text.length();
        if (cachedNodesSize > maxCachedNodesSize) {
            write();
	    }
    }

    private record PendingDoc(NodeId nodeId, QName qname, NodePath path, CharSequence text, float boost,
                              LuceneIndexConfig idxConf) {
    }

    private record PendingAttr(AttrImpl attr, NodePath path, LuceneIndexConfig conf) {
    }
    
    private void write() {
        if (nodesToWrite == null || nodesToWrite.isEmpty() || !canFlush) {
            return;
	    }

        if (broker.getIndexController().isReindexing()) {
            // remove old indexed nodes
            nodesToRemove = new TreeSet<>();
            for (PendingDoc p : nodesToWrite) {
                nodesToRemove.add(p.nodeId);
            }
            removeNodes();
        }

        IndexWriter writer = null;
        try {
            writer = index.getWriter();
            // docId and nodeId are stored as doc value
            // docId also needs to be indexed. IntField in Lucene 10+ also provides doc values.
            // Create new field instances per doc to avoid shared mutable state (fixes vector search returning wrong nodeIds).

            for (final PendingDoc pending : nodesToWrite) {
                final Document doc = new Document();


                List<AbstractFieldConfig> facetConfigs = pending.idxConf.getFacetsAndFields();
                final ReindexScope scope = broker.getIndexController().getReindexScope();
                facetConfigs.forEach(config -> {
                    if (scope == ReindexScope.VECTOR && !(config instanceof LuceneVectorFieldConfig)) {
                        return; // Vector-only: skip fulltext fields and facets
                    }
                    config.build(broker, currentDoc, pending.nodeId, doc, pending.text);
                });
                // register field analyzers so indexing uses the same analyzer as querying
                final LuceneConfig luceneConfig = pending.idxConf.getParent();
                for (AbstractFieldConfig config : facetConfigs) {
                    if (config instanceof LuceneFieldConfig lfc) {
                        Analyzer a = lfc.getAnalyzer();
                        if (a == null) {
                            a = luceneConfig.getFieldAnalyzer(lfc.getName());
                            if (a == null) {
                                a = pending.idxConf.getAnalyzer();
                            }
                        }
                        if (a != null) {
                            index.addFieldAnalyzer(lfc.getName(), a);
                        }
                    }
                }

                // store the node id (new field per doc to avoid shared mutable state)
                int nodeIdLen = pending.nodeId.size();
                byte[] data = new byte[nodeIdLen + 2];
                ByteConversion.shortToByteH((short) pending.nodeId.units(), data, 0);
                pending.nodeId.serialize(data, 2);
                doc.add(new BinaryDocValuesField(LuceneUtil.FIELD_NODE_ID_DV, new BytesRef(data)));

                // add separate index for node id (FIELD_NODE_ID for Term queries)
                BinaryTokenStream bts = new BinaryTokenStream(new BytesRef(data));
                Field fNodeIdIdx = new Field(LuceneUtil.FIELD_NODE_ID, bts, TYPE_NODE_ID);
                doc.add(fNodeIdIdx);

                String contentField = null;
                final boolean indexFulltext = scope != ReindexScope.VECTOR && pending.idxConf.doIndex();
                if (indexFulltext) {
                    // the text content is indexed in a field using either
                    // the qname of the element or attribute or the field
                    // name defined in the configuration
                    contentField = pending.idxConf.isNamed()
                        ? pending.idxConf.getName()
                        : LuceneUtil.encodeQName(pending.qname, index.getBrokerPool().getSymbols());

                    Field fld = new TextField(contentField, pending.text.toString(), Field.Store.NO);

                    doc.add(fld);
                } else {
                    contentField = pending.idxConf.isNamed()
                        ? pending.idxConf.getName()
                        : LuceneUtil.encodeQName(pending.qname, index.getBrokerPool().getSymbols());
                }
                doc.add(new StringField(LuceneUtil.FIELD_INDEX_TYPE, contentField, Field.Store.NO));

                doc.add(new IntField(FIELD_DOC_ID, currentDoc.getDocId(), Field.Store.NO));
                doc.add(new SortedNumericDocValuesField(FIELD_DOC_ID, currentDoc.getDocId()));
                doc.add(new StoredField(FIELD_DOC_ID, currentDoc.getDocId()));

                final float boostVal = pending.boost > 0 ? pending.boost : 1.0f;
                doc.add(new FloatDocValuesField(LuceneUtil.FIELD_BOOST, boostVal));

                final Analyzer customAnalyzer = pending.idxConf.getAnalyzer();
                if (customAnalyzer != null && contentField != null) {
                    index.addFieldAnalyzer(contentField, customAnalyzer);
                }
                writer.addDocument(pending.idxConf.getParent().facetsConfig.build(index.getTaxonomyWriter(), doc));
	        }
        } catch (final IOException e) {
            LOG.warn("An exception was caught while indexing document: {}", e.getMessage(), e);
        } finally {
            index.releaseWriter(writer);
            nodesToWrite = new ArrayList<>();
            cachedNodesSize = 0;
        }
    }

    /**
     * Optimize the Lucene index by merging all segments into a single one. This
     * may take a while and write operations will be blocked during the optimize.
     */
    public void optimize() {
        IndexWriter writer = null;
        try {
            writer = index.getWriter(true);
            writer.forceMerge(1, true);
            writer.commit();
        } catch (IOException e) {
            LOG.warn("An exception was caught while optimizing the lucene index: {}", e.getMessage(), e);
        } finally {
            index.releaseWriter(writer);
        }
    }

    private class LuceneStreamListener extends AbstractStreamListener {
        private ArrayList<PendingAttr> pendingAttrs = new ArrayList<>();
	private ArrayList<AttrImpl> attributes = new ArrayList<>(10);
        private ElementImpl currentElement;

        @Override
        public void startReplaceDocument(Txn transaction) {
            // if config has fields or facets, we cannot flush until the document is completely stored
            canFlush = config == null || !config.hasFieldsOrFacets();
        }

        @Override
        public void endReplaceDocument(Txn transaction) {
            canFlush = true;
        }

        @Override
        public void startIndexDocument(Txn transaction) {
            // if config has fields or facets, we cannot flush until the document is completely stored
            canFlush = config == null || !config.hasFieldsOrFacets();
        }

        @Override
        public void endIndexDocument(Txn transaction) {
            canFlush = true;
        }

        @Override
        public void startElement(Txn transaction, ElementImpl element, NodePath path) {
            if (currentElement != null) {
                indexPendingAttrs();
            }
            currentElement = element;

            if (mode == ReindexMode.STORE && config != null) {
                if (contentStack != null) {
                    for (final TextExtractor extractor : contentStack) {
                        extractor.startElement(element.getQName());
                    }
                }

                Iterator<LuceneIndexConfig> configIter = config.getConfig(path);
                if (configIter != null) {
                    if (contentStack == null) {
                        contentStack = new ArrayDeque<>();
                    }
                    while (configIter.hasNext()) {
                        LuceneIndexConfig configuration = configIter.next();
                        if (configuration.match(path)) {
                            TextExtractor extractor = new DefaultTextExtractor();
                            extractor.configure(config, configuration);
                            contentStack.push(extractor);
                        }
                    }
                }
            }
            super.startElement(transaction, element, path);
        }

        @Override
        public void endElement(Txn transaction, ElementImpl element, NodePath path) {
            if (config != null) {
                if (mode == ReindexMode.STORE && contentStack != null) {
                    for (final TextExtractor extractor : contentStack) {
                        extractor.endElement(element.getQName());
                    }
                }
                Iterator<LuceneIndexConfig> configIter = config.getConfig(path);
                if (mode != ReindexMode.REMOVE_ALL_NODES && configIter != null) {
                    if (mode == ReindexMode.REMOVE_SOME_NODES) {
                        nodesToRemove.add(element.getNodeId());
                    } else {
                        while (configIter.hasNext()) {
                            LuceneIndexConfig configuration = configIter.next();
                            if (configuration.match(path)) {
                                TextExtractor extractor = contentStack.pop();

                                if (configuration.shouldReindexOnAttributeChange()) {
                                    // if we still have the attributes cached
				    // i e this element had no child elements,
				    // use them to save some time
                                    // otherwise we fetch the attributes again
                                    boolean wasEmpty = false;
                                    if (attributes.isEmpty()) {
                                        wasEmpty = true;
                                        NamedNodeMap attributes1 = element.getAttributes();
                                        for (int i = 0; i < attributes1.getLength(); i++) {
                                            attributes.add((AttrImpl) attributes1.item(i));
                                        }
                                    }
                                    indexText(attributes, element.getNodeId(), element.getQName(), path, extractor.getIndexConfig(), extractor.getText());
                                    if (wasEmpty) {
                                        attributes.clear();
                                    }
                                } else {
                                    // no attribute matching, index normally
                                    indexText(element.getNodeId(), element.getQName(), path, extractor.getIndexConfig(), extractor.getText());
                                }
                            }
                        }
                    }
                }
            }

            indexPendingAttrs();
            currentElement = null;

            super.endElement(transaction, element, path);
        }

        @Override
        public void attribute(Txn transaction, AttrImpl attrib, NodePath path) {
            path.addComponent(attrib.getQName());

            AttrImpl attribCopy = null;
            if (mode == ReindexMode.STORE && currentElement != null) {
                attribCopy = (AttrImpl) NodePool.getInstance().borrowNode(Node.ATTRIBUTE_NODE);
                attribCopy.setValue(attrib.getValue());
                attribCopy.setNodeId(attrib.getNodeId());
                attribCopy.setQName(attrib.getQName());
                attributes.add(attribCopy);
            }

            Iterator<LuceneIndexConfig> configIter = null;
            if (config != null)
                configIter = config.getConfig(path);
            if (mode != ReindexMode.REMOVE_ALL_NODES && configIter != null) {
                if (mode == ReindexMode.REMOVE_SOME_NODES) {
                    nodesToRemove.add(attrib.getNodeId());
                } else {
                    while (configIter.hasNext()) {
                        LuceneIndexConfig configuration = configIter.next();
                        if (configuration.match(path)) {
			    if (configuration.shouldReindexOnAttributeChange()) {
				appendAttrToBeIndexedLater(attribCopy, new NodePath(path), configuration);
			    } else {
				indexText(attrib.getNodeId(), attrib.getQName(), path, configuration, attrib.getValue());
			    }
                        }
                    }
                }
            }

            path.removeLastComponent();
            super.attribute(transaction, attrib, path);
        }

        @Override
        public void characters(Txn transaction, AbstractCharacterData text, NodePath path) {
            if (contentStack != null) {
                for (final TextExtractor extractor : contentStack) {
                	extractor.beforeCharacters();
                    extractor.characters(text.getXMLString());
                }
            }
            super.characters(transaction, text, path);
        }

        @Override
        public IndexWorker getWorker() {
            return LuceneIndexWorker.this;
        }

        /**
         * Delay indexing of attributes until we have them all to calculate boost
         *
         * @param attr the attribute to be indexed
         * @param path the node path
         * @param conf the index config
         */
        private void appendAttrToBeIndexedLater(AttrImpl attr, NodePath path, LuceneIndexConfig conf) {
            if (currentElement == null){
                LOG.error("currentElement == null");
            } else {
                pendingAttrs.add(new PendingAttr(attr, path, conf));
	        }
        }

        /**
         * Put pending attribute nodes in indexing cache
         * and then clear pending attributes
         */
        private void indexPendingAttrs() {
                try {
                    if (mode == ReindexMode.STORE && config != null) {
                        for (PendingAttr pending : pendingAttrs) {
                            AttrImpl attr = pending.attr;
                            indexText(attributes, attr.getNodeId(), attr.getQName(), pending.path, pending.conf, attr.getValue());
                        }
                    }
                } finally {
                    pendingAttrs.clear();
                    releaseAttributes();
                }
            }

            private void releaseAttributes() {
                try {
                    for (Attr attr : attributes) {
                        NodePool.getInstance().returnNode((AttrImpl) attr);
                    }
                } finally {
                    attributes.clear();
                }
            }
        }
    }