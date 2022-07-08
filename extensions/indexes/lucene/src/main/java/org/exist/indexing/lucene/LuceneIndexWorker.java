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
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;
import org.apache.lucene.util.NumericUtils;
import org.exist.collections.Collection;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.persistent.*;
import org.exist.indexing.*;
import org.exist.indexing.StreamListener.ReindexMode;
import org.exist.indexing.lucene.PlainTextHighlighter.Offset;
import org.exist.indexing.lucene.PlainTextIndexConfig.PlainTextField;
import org.exist.numbering.NodeId;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.*;
import org.exist.storage.btree.DBException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;
import org.exist.util.ByteConversion;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.util.Occurrences;
import org.exist.util.pool.NodePool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.modules.lucene.QueryOptions;
import org.exist.xquery.value.*;
import org.w3c.dom.*;
import org.xml.sax.helpers.AttributesImpl;

import javax.annotation.Nullable;
import javax.xml.XMLConstants;
import java.io.IOException;
import java.util.*;


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
        TYPE_NODE_ID.setIndexed(true);
        TYPE_NODE_ID.setStored(false);
        TYPE_NODE_ID.setOmitNorms(true);
        TYPE_NODE_ID.setStoreTermVectors(false);
        TYPE_NODE_ID.setTokenized(true);
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
                write();
                break;
            case REMOVE_ALL_NODES:
                removeDocument(currentDoc.getDocId());
                break;
            case REMOVE_SOME_NODES:
                removeNodes();
                break;
            case REMOVE_BINARY:
            	removePlainTextIndexes();
            	break;
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
                if (nodesToWrite == null)
                    nodesToWrite = new ArrayList<>();
                else
                    nodesToWrite.clear();
                cachedNodesSize = 0;
                break;
            case REMOVE_SOME_NODES:
                nodesToRemove = new TreeSet<>();
                break;
        }
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
    	IndexWriter writer = null;
        try {
            writer = index.getWriter();
            final BytesRefBuilder bytes = new BytesRefBuilder();
            NumericUtils.intToPrefixCoded(docId, 0, bytes);
            Term dt = new Term(FIELD_DOC_ID, bytes.toBytesRef());
            writer.deleteDocuments(dt);
        } catch (IOException e) {
            LOG.warn("Error while removing lucene index: {}", e.getMessage(), e);
        } finally {
            index.releaseWriter(writer);
            mode = ReindexMode.STORE;
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
        IndexWriter writer = null;
        try {
            writer = index.getWriter();
            for (Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext(); ) {
                DocumentImpl doc = i.next();
                final BytesRefBuilder bytes = new BytesRefBuilder();
                NumericUtils.intToPrefixCoded(doc.getDocId(), 0, bytes);
                Term dt = new Term(FIELD_DOC_ID, bytes.toBytesRef());
                writer.deleteDocuments(dt);
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
        IndexWriter writer = null;
        try {
            writer = index.getWriter();

            final BytesRefBuilder bytes = new BytesRefBuilder();
            NumericUtils.intToPrefixCoded(currentDoc.getDocId(), 0, bytes);
            Term dt = new Term(FIELD_DOC_ID, bytes.toBytesRef());
            TermQuery tq = new TermQuery(dt);
            for (NodeId nodeId : nodesToRemove) {
                // store the node id
                int nodeIdLen = nodeId.size();
                byte[] data = new byte[nodeIdLen + 2];
                ByteConversion.shortToByte((short) nodeId.units(), data, 0);
                nodeId.serialize(data, 2);

                Term it = new Term(LuceneUtil.FIELD_NODE_ID, new BytesRef(data));

                TermQuery iq = new TermQuery(it);
                BooleanQuery q = new BooleanQuery();
                q.add(tq, BooleanClause.Occur.MUST);
                q.add(iq, BooleanClause.Occur.MUST);
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
    public NodeSet query(int contextId, DocumentSet docs, NodeSet contextSet,
                         List<QName> qnames, String queryStr, int axis, QueryOptions options)
            throws IOException, ParseException, XPathException {
        return index.withSearcher(searcher -> {
            final List<QName> definedIndexes = getDefinedIndexes(qnames);
            final NodeSet resultSet = new NewArrayNodeSet();
            final boolean returnAncestor = axis == NodeSet.ANCESTOR;
            for (QName qname : definedIndexes) {
                String field = LuceneUtil.encodeQName(qname, index.getBrokerPool().getSymbols());
                LuceneConfig config = getLuceneConfig(broker, docs);
                Analyzer analyzer = getQueryAnalyzer(config,null, qname, options);
                Query query;
                if (queryStr == null) {
                    query = new ConstantScoreQuery(new FieldValueFilter(field));
                } else {
                    QueryParserWrapper parser = getQueryParser(field, analyzer, docs);
                    options.configureParser(parser.getConfiguration());
                    query = parser.parse(queryStr);
                }
                Optional<Map<String, QueryOptions.FacetQuery>> facets = options.getFacets();
                if (facets.isPresent() && config != null) {
                    query = drilldown(facets.get(), query, config);
                }
                searchAndProcess(contextId, qname, docs, contextSet, resultSet,
                        returnAncestor, searcher, query, options.getFields(), config);
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
                Query query = queryRoot == null ? new ConstantScoreQuery(new FieldValueFilter(field)) : queryTranslator.parse(field, queryRoot, analyzer, options);
                Optional<Map<String, QueryOptions.FacetQuery>> facets = options.getFacets();
                if (facets.isPresent() && config != null) {
                    query = drilldown(facets.get(), query, config);
                }
                if (query != null) {
                    searchAndProcess(contextId, qname, docs, contextSet, resultSet,
                            returnAncestor, searcher, query, options.getFields(), config);
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
            final Query query = queryTranslator.parse(field, queryRoot, analyzer, options);
            if (query != null) {
                searchAndProcess(contextId, null, docs, contextSet, resultSet,
                        returnAncestor, searcher, query, null, config);
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

    private void searchAndProcess(int contextId, QName qname, DocumentSet docs,
                                  NodeSet contextSet, NodeSet resultSet, boolean returnAncestor,
                                  SearcherTaxonomyManager.SearcherAndTaxonomy searcher, Query query,
                                  @Nullable Set<String> fields, LuceneConfig config) throws IOException {
        final LuceneFacets facets = new LuceneFacets();
        final FacetsCollector facetsCollector = new FacetsCollector();
        final LuceneHitCollector collector = new LuceneHitCollector(qname, query, docs, contextSet, resultSet, returnAncestor, contextId, facets, facetsCollector, fields);
        searcher.searcher.search(query, collector);

        // compute facets
        facets.compute(searcher.taxonomyReader, config.facetsConfig, facetsCollector);
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
            searchAndProcess(contextId, null, docs, contextSet, resultSet,
                    returnAncestor, searcher, query, null, config);
            return resultSet;
        });
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
    public void indexNonXML(NodeValue descriptor) {
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
        	
	    // Set DocId
	    NumericDocValuesField fDocId = new NumericDocValuesField(FIELD_DOC_ID, currentDoc.getDocId());

            pendingDoc.add(fDocId);

            IntField fDocIdIdx = new IntField(FIELD_DOC_ID, currentDoc.getDocId(), Field.Store.NO);
            pendingDoc.add(fDocIdIdx);

            // For binary documents the doc path needs to be stored
            String uri = currentDoc.getURI().toString();

            Field fDocUri = new Field(FIELD_DOC_URI, uri, Field.Store.YES, Field.Index.NOT_ANALYZED);
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
            Field contentField = new Field(contentFieldName, field.getData().toString(),  store, Field.Index.ANALYZED, Field.TermVector.YES);

            // Extract (document) Boost factor
            if (field.getBoost() > 0) {
                contentField.setBoost(field.getBoost());
            }

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
            // Get analyzer : to be retrieved from configuration
            final Analyzer searchAnalyzer = new StandardAnalyzer(LuceneIndex.LUCENE_VERSION_IN_USE);

            // Setup query Version, default field, analyzer
            final QueryParserWrapper parser = getQueryParser("", searchAnalyzer, null);
            options.configureParser(parser.getConfiguration());
            final Query query = parser.parse(queryText);

            // extract all used fields from query
            final String[] fields;
            if (fieldsToGet == null) {
                fields = LuceneUtil.extractFields(query, searcher.searcher.getIndexReader());
            } else {
                fields = fieldsToGet;
            }

            final PlainTextHighlighter highlighter = new PlainTextHighlighter(query, searcher.searcher.getIndexReader());

            context.pushDocumentContext();
            try {
                final MemTreeBuilder builder = context.getDocumentBuilder();
                builder.startDocument();

                // start root element
                final int nodeNr = builder.startElement("", "results", "results", null);

                // Perform actual search
                final BinarySearchCollector collector = new BinarySearchCollector(toBeMatchedURIs, builder, fields, searchAnalyzer, highlighter);
                searcher.searcher.search(query, collector);

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

    private class BinarySearchCollector extends Collector {
        private final List<String> toBeMatchedURIs;
        private final MemTreeBuilder builder;
        private final String[] fields;
        private final Analyzer searchAnalyzer;
        private final PlainTextHighlighter highlighter;
        private Scorer scorer;
        private AtomicReader reader;

        public BinarySearchCollector(List<String> toBeMatchedURIs, MemTreeBuilder builder, String[] fields, Analyzer searchAnalyzer, PlainTextHighlighter highlighter) {

            this.toBeMatchedURIs = toBeMatchedURIs;
            this.builder = builder;
            this.fields = fields;
            this.searchAnalyzer = searchAnalyzer;
            this.highlighter = highlighter;
        }

        @Override
        public void setScorer(Scorer scorer) throws IOException {
            this.scorer = scorer;
        }

        @Override
        public void collect(int docNum) throws IOException {
            Document doc = reader.document(docNum);

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

        @Override
        public void setNextReader(AtomicReaderContext atomicReaderContext) throws IOException {
            this.reader = atomicReaderContext.reader();
        }

        @Override
        public boolean acceptsDocsOutOfOrder() {
            return true;
        }
    }

    public String getFieldContent(int docId, String field) throws IOException {
        final BytesRefBuilder bytes = new BytesRefBuilder();
        NumericUtils.intToPrefixCoded(docId, 0, bytes);
        Term dt = new Term(FIELD_DOC_ID, bytes.toBytesRef());

        return index.withReader(reader -> {
            List<AtomicReaderContext> leaves = reader.leaves();
            for (AtomicReaderContext context : leaves) {
                AtomicReader atomicReader = context.reader();
                DocsEnum docs = atomicReader.termDocsEnum(dt);
                if (docs != null && docs.nextDoc() != DocsEnum.NO_MORE_DOCS) {
                    Document doc = atomicReader.document(docs.docID());
                    String value = doc.get(field);
                    if (value != null) {
                        return value;
                    }
                }
            }
            return null;
        });
    }

    public boolean hasIndex(int docId) throws IOException {
        final BytesRefBuilder bytes = new BytesRefBuilder();
        NumericUtils.intToPrefixCoded(docId, 0, bytes);
        Term dt = new Term(FIELD_DOC_ID, bytes.toBytesRef());

        return index.withReader(reader -> {
            boolean found = false;
            List<AtomicReaderContext> leaves = reader.leaves();
            for (AtomicReaderContext context : leaves) {
                DocsEnum docs = context.reader().termDocsEnum(dt);
                if (docs != null && docs.nextDoc() != DocsEnum.NO_MORE_DOCS) {
                    found = true;
                    break;
                }
            }
            return found;
        });
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

    private class LuceneHitCollector extends Collector {

        private Scorer scorer;

        private AtomicReader reader;
        private NumericDocValues docIdValues;
        private BinaryDocValues nodeIdValues;
        private final QName qname;
        private final DocumentSet docs;
        private final NodeSet contextSet;
        private final NodeSet resultSet;
        private final boolean returnAncestor;
        private final int contextId;
        private final Query query;
        private final LuceneFacets facets;
        private final FacetsCollector chainedCollector;
        private final Set<String> fields;

        private LuceneHitCollector(QName qname, Query query, DocumentSet docs, NodeSet contextSet, NodeSet resultSet, boolean returnAncestor, int contextId, LuceneFacets facets, FacetsCollector nextCollector, @Nullable Set<String> fields) {
            this.qname = qname;
            this.docs = docs;
            this.contextSet = contextSet;
            this.resultSet = resultSet;
            this.returnAncestor = returnAncestor;
            this.contextId = contextId;
            this.query = query;
            this.facets = facets;
            this.chainedCollector = nextCollector;
            this.fields = fields;
        }

        @Override
        public void setScorer(Scorer scorer) throws IOException {
            this.scorer = scorer;
            chainedCollector.setScorer(scorer);
        }

        @Override
        public void setNextReader(AtomicReaderContext atomicReaderContext) throws IOException {
            this.reader = atomicReaderContext.reader();
            this.docIdValues = this.reader.getNumericDocValues(FIELD_DOC_ID);
            this.nodeIdValues = this.reader.getBinaryDocValues(LuceneUtil.FIELD_NODE_ID);
            chainedCollector.setNextReader(atomicReaderContext);
        }

        @Override
        public boolean acceptsDocsOutOfOrder() {
            return false;
        }

        @Override
        public void collect(int doc) {
            try {
                float score = scorer.score();
                int docId = (int) this.docIdValues.get(doc);
                DocumentImpl storedDocument = docs.getDoc(docId);
                if (storedDocument == null)
                    return;
                final BytesRef ref = this.nodeIdValues.get(doc);
                int units = ByteConversion.byteToShort(ref.bytes, ref.offset);
                NodeId nodeId = index.getBrokerPool().getNodeFactory().createFromData(units, ref.bytes, ref.offset + 2);
                //LOG.info("doc: " + docId + "; node: " + nodeId.toString() + "; units: " + units);

                NodeProxy storedNode = new NodeProxy(null, storedDocument, nodeId);
                if (qname != null)
                    storedNode.setNodeType(qname.getNameType() == ElementValue.ATTRIBUTE ? Node.ATTRIBUTE_NODE : Node.ELEMENT_NODE);
                // if a context set is specified, we can directly check if the
                // matching node is a descendant of one of the nodes
                // in the context set.
                if (contextSet != null) {
                    int sizeHint = contextSet.getSizeHint(storedDocument);
                    if (returnAncestor) {
                        NodeProxy parentNode = contextSet.get(storedNode);
                        // NodeProxy parentNode = contextSet.parentWithChild(storedNode, false, true, NodeProxy.UNKNOWN_NODE_LEVEL);
                        if (parentNode != null) {
                            LuceneMatch match = createMatch(doc, score, nodeId);
                            parentNode.addMatch(match);
                            resultSet.add(parentNode, sizeHint);
                            if (Expression.NO_CONTEXT_ID != contextId) {
                                parentNode.deepCopyContext(storedNode, contextId);
                            } else
                                parentNode.copyContext(storedNode);
                            chainedCollector.collect(doc);
                        }
                    } else {
                        LuceneMatch match = createMatch(doc, score, nodeId);
                        storedNode.addMatch(match);
                        resultSet.add(storedNode, sizeHint);
                        chainedCollector.collect(doc);
                    }
                } else {
                    LuceneMatch match = createMatch(doc, score, nodeId);
                    storedNode.addMatch(match);
                    resultSet.add(storedNode);
                    chainedCollector.collect(doc);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private LuceneMatch createMatch(int docId, float score, NodeId nodeId) throws IOException {
            final LuceneMatch match = new LuceneMatch(contextId, nodeId, query, facets);
            match.setScore(score);
            if (fields != null && !fields.isEmpty()) {
                final Document luceneDoc = reader.document(docId, fields);
                for (String field : fields) {
                    match.addField(field, luceneDoc.getFields(field));
                }
            }
            return match;
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
            return indexes;
        }
        return getDefinedIndexesFor(null, indexes);
    }

    private List<QName> getDefinedIndexesFor(QName qname, final List<QName> indexes) throws IOException {
        return index.withReader(reader -> {
            for (FieldInfo info: MultiFields.getMergedFieldInfos(reader)) {
                if (!FIELD_DOC_ID.equals(info.name)) {
                    QName name = LuceneUtil.decodeQName(info.name, index.getBrokerPool().getSymbols());
                    if (name != null && (qname == null || matchQName(qname, name)))
                        indexes.add(name);
                }
            }
            return indexes;
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
            Analyzer analyzer;
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
    public LuceneConfig getLuceneConfig(DBBroker broker, DocumentSet docs) {
        for (Iterator<Collection> i = docs.getCollectionIterator(); i.hasNext(); ) {
            Collection collection = i.next();
            IndexSpec idxConf = collection.getIndexConfiguration(broker);
            if (idxConf != null) {
                LuceneConfig config = (LuceneConfig) idxConf.getCustomIndexSpec(LuceneIndex.ID);
                if (config != null) {
                    return config;
                }
            }
        }
        return LuceneConfig.DEFAULT_CONFIG;
    }

    protected QueryParserWrapper getQueryParser(String field, Analyzer analyzer, DocumentSet docs) {
        if (docs != null) {
            for (Iterator<Collection> i = docs.getCollectionIterator(); i.hasNext(); ) {
                Collection collection = i.next();
                IndexSpec idxConf = collection.getIndexConfiguration(broker);
                if (idxConf != null) {
                    LuceneConfig config = (LuceneConfig) idxConf.getCustomIndexSpec(LuceneIndex.ID);
                    if (config != null) {
                        QueryParserWrapper parser = config.getQueryParser(field, analyzer);
                        if (parser != null) {
                            return parser;
                        }
                    }
                }
            }
        }
        // not found. return default query parser:
        return new ClassicQueryParserWrapper(field, analyzer);
    }

    public boolean checkIndex(DBBroker broker) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Occurrences[] scanIndex(XQueryContext context, DocumentSet docs, NodeSet nodes, Map<?,?> hints) {
        try {
            List<QName> qnames = hints == null ? null : (List<QName>)hints.get(QNAMES_KEY);
            qnames = getDefinedIndexes(qnames);
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
            return scanIndexByQName(qnames, docs, nodes, start, end, max);
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
        List<AtomicReaderContext> leaves = reader.leaves();
        for (AtomicReaderContext context : leaves) {
            NumericDocValues docIdValues = context.reader().getNumericDocValues(FIELD_DOC_ID);
            BinaryDocValues nodeIdValues = context.reader().getBinaryDocValues(LuceneUtil.FIELD_NODE_ID);
            Bits liveDocs = context.reader().getLiveDocs();
            Terms terms = context.reader().terms(field);
            if (terms != null) {
                TermsEnum termsIter = terms.iterator(null);
                if (termsIter.next() != null) {
                    do {
                        if (map.size() >= max) {
                            break;
                        }
                        BytesRef ref = termsIter.term();
                        String term = ref.utf8ToString();
                        if ((end == null || term.compareTo(end) <= 0) && (start == null || term.startsWith(start))) {
                            DocsEnum docsEnum = termsIter.docs(null, null);
                            while (docsEnum.nextDoc() != DocsEnum.NO_MORE_DOCS) {
                                if (liveDocs != null && !liveDocs.get(docsEnum.docID())) {
                                    continue;
                                }
                                int docId = (int) docIdValues.get(docsEnum.docID());
                                DocumentImpl storedDocument = docs.getDoc(docId);
                                if (storedDocument == null)
                                    continue;
                                if (nodes != null) {
                                    final BytesRef nodeIdRef = nodeIdValues.get(docsEnum.docID());
                                    int units = ByteConversion.byteToShort(nodeIdRef.bytes, nodeIdRef.offset);
                                    NodeId nodeId = index.getBrokerPool().getNodeFactory().createFromData(units, nodeIdRef.bytes, nodeIdRef.offset + 2);
                                    if (nodes.get(storedDocument, nodeId) != null) {
                                        addOccurrence(map, term, docsEnum.freq(), storedDocument);
                                    }
                                } else {
                                    addOccurrence(map, term, docsEnum.freq(), storedDocument);
                                }
                            }
                        }
                    } while (termsIter.next() != null);
                }
            }
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

    private static class PendingDoc {
        private final NodeId nodeId;
        private final QName qname;
        private final NodePath path;
        private final CharSequence text;
        private final float boost;
        private final LuceneIndexConfig idxConf;

        private PendingDoc(final NodeId nodeId, final QName qname, final NodePath path, final CharSequence text,
                final float boost, final LuceneIndexConfig idxConf) {
            this.nodeId = nodeId;
            this.qname = qname;
            this.path = path;
            this.text = text;
            this.idxConf = idxConf;
            this.boost = boost;
        }
    }

    private static class PendingAttr {
	    private final AttrImpl attr;
	    private final LuceneIndexConfig conf;
	    private final NodePath path;

        public PendingAttr(final AttrImpl attr, final NodePath path, final LuceneIndexConfig conf) {
            this.attr = attr;
            this.conf = conf;
            this.path = path;
        }
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
            NumericDocValuesField fDocId = new NumericDocValuesField(FIELD_DOC_ID, 0);
            BinaryDocValuesField fNodeId = new BinaryDocValuesField(LuceneUtil.FIELD_NODE_ID, new BytesRef(8));
            // docId also needs to be indexed
            IntField fDocIdIdx = new IntField(FIELD_DOC_ID, 0, IntField.TYPE_NOT_STORED);

            for (PendingDoc pending : nodesToWrite) {
                final Document doc = new Document();


                List<AbstractFieldConfig> facetConfigs = pending.idxConf.getFacetsAndFields();
                facetConfigs.forEach(config ->
                    config.build(broker, currentDoc, pending.nodeId, doc, pending.text)
                );

                fDocId.setLongValue(currentDoc.getDocId());
                doc.add(fDocId);

                // store the node id
                int nodeIdLen = pending.nodeId.size();
                byte[] data = new byte[nodeIdLen + 2];
                ByteConversion.shortToByte((short) pending.nodeId.units(), data, 0);
                pending.nodeId.serialize(data, 2);
                fNodeId.setBytesValue(data);
                doc.add(fNodeId);

                // add separate index for node id
                BinaryTokenStream bts = new BinaryTokenStream(new BytesRef(data));
                Field fNodeIdIdx = new Field(LuceneUtil.FIELD_NODE_ID, bts, TYPE_NODE_ID);
                doc.add(fNodeIdIdx);

                if (pending.idxConf.doIndex()) {
                    String contentField;
                    // the text content is indexed in a field using either
                    // the qname of the element or attribute or the field
                    // name defined in the configuration
                    if (pending.idxConf.isNamed())
                        contentField = pending.idxConf.getName();
                    else
                        contentField = LuceneUtil.encodeQName(pending.qname, index.getBrokerPool().getSymbols());

                    Field fld = new Field(contentField, pending.text.toString(), Field.Store.NO, Field.Index.ANALYZED, Field.TermVector.YES);
                    if (pending.boost > 0) {
                        fld.setBoost(pending.boost);
                    } else if (config.getBoost() > 0) {
                        fld.setBoost(config.getBoost());
                    }

                    doc.add(fld);
                }

                fDocIdIdx.setIntValue(currentDoc.getDocId());
                doc.add(fDocIdIdx);

                final byte[] docNodeId = LuceneUtil.createId(currentDoc.getDocId(), pending.nodeId);
                final Field fDocNodeId = new StoredField("docNodeId", docNodeId);
                doc.add(fDocNodeId);

                if (pending.idxConf.getAnalyzer() == null) {
                    writer.addDocument(config.facetsConfig.build(index.getTaxonomyWriter(), doc));
                } else {
                    writer.addDocument(config.facetsConfig.build(index.getTaxonomyWriter(), doc), pending.idxConf.getAnalyzer());
		        }
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

