/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.indexing.lucene;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.facet.index.FacetFields;
import org.apache.lucene.facet.taxonomy.CategoryPath;
import org.apache.lucene.facet.taxonomy.TaxonomyWriter;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.queryparser.flexible.standard.CommonQueryParserConfiguration;
import org.apache.lucene.search.*;
import org.apache.lucene.util.*;
import org.exist.collections.Collection;
import org.exist.dom.*;
import org.exist.indexing.*;
import org.exist.indexing.lucene.PlainTextHighlighter.Offset;
import org.exist.indexing.lucene.PlainTextIndexConfig.PlainTextDoc;
import org.exist.indexing.lucene.PlainTextIndexConfig.PlainTextField;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.numbering.NodeId;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.*;
import org.exist.storage.btree.DBException;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.util.ByteConversion;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.Occurrences;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.NodeValue;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.helpers.AttributesImpl;

import java.io.IOException;
import java.util.*;

/**
 * Class for handling all Lucene operations.
 *
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 * @author Dannes Wessels (dannes@exist-db.org)
 */
public class LuceneIndexWorker implements OrderedValuesIndex, QNamedKeysIndex {

    public static final String OPTION_DEFAULT_OPERATOR = "default-operator";
    public static final String OPTION_PHRASE_SLOP = "phrase-slop";
    public static final String OPTION_LEADING_WILDCARD = "leading-wildcard";
    public static final String OPTION_FILTER_REWRITE = "filter-rewrite";
    public static final String DEFAULT_OPERATOR_OR = "or";

    public static final org.apache.lucene.document.FieldType TYPE_NODE_ID = new org.apache.lucene.document.FieldType();
    static {
        TYPE_NODE_ID.setIndexed(true);
        TYPE_NODE_ID.setStored(false);
        TYPE_NODE_ID.setOmitNorms(true);
        TYPE_NODE_ID.setStoreTermVectors(false);
        TYPE_NODE_ID.setTokenized(true);
    }

    static final Logger LOG = Logger.getLogger(LuceneIndexWorker.class);
    
    protected LuceneIndex index;
    
    private LuceneMatchListener matchListener = null;

    private XMLToQuery queryTranslator;

    private DBBroker broker;

    private DocumentImpl currentDoc = null;
    private int mode = 0;
    
    private LuceneConfig config;
    private Stack<TextExtractor> contentStack = null;
    private Set<NodeId> nodesToRemove = null;
    private List<PendingDoc> nodesToWrite = null;
    private Document pendingDoc = null;
    
    private int cachedNodesSize = 0;

    private int maxCachedNodesSize = 4096 * 1024;
    
    private Analyzer analyzer;

    public static final String FIELD_DOC_ID = "docId";
    public static final String FIELD_DOC_URI = "docUri";

    private final byte[] buf = new byte[1024];

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
            case StreamListener.STORE:
                write();
                break;
            case StreamListener.REMOVE_ALL_NODES:
                removeDocument(currentDoc.getDocId());
                break;
            case StreamListener.REMOVE_SOME_NODES:
                removeNodes();
                break;
            case StreamListener.REMOVE_BINARY:
            	removePlainTextIndexes();
            	break;
        }
    }

    public void setDocument(DocumentImpl document) {
        setDocument(document, StreamListener.UNKNOWN);
    }

    public void setDocument(DocumentImpl document, int newMode) {
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
        }
        mode = newMode;
    }

    public void setMode(int mode) {
        this.mode = mode;
        switch (mode) {
            case StreamListener.STORE:
                if (nodesToWrite == null)
                    nodesToWrite = new ArrayList<PendingDoc>();
                else
                    nodesToWrite.clear();
                cachedNodesSize = 0;
                break;
            case StreamListener.REMOVE_SOME_NODES:
                nodesToRemove = new TreeSet<NodeId>();
                break;
        }
    }

    public DocumentImpl getDocument() {
        return currentDoc;
    }

    public int getMode() {
        return this.mode;
    }

    public StoredNode getReindexRoot(StoredNode node, NodePath path, boolean insert, boolean includeSelf) {
        if (node.getNodeType() == Node.ATTRIBUTE_NODE)
            return null;
        if (config == null)
            return null;
        NodePath p = new NodePath(path);
        boolean reindexRequired = false;
        if (node.getNodeType() == Node.ELEMENT_NODE && !includeSelf)
            p.removeLastComponent();
        for (int i = 0; i < p.length(); i++) {
            if (config.matches(p)) {
                reindexRequired = true;
                break;
            }
            p.removeLastComponent();
        }
        if (reindexRequired) {
            p = new NodePath(path);
            StoredNode topMost = null;
            StoredNode currentNode = node;
            if (currentNode.getNodeType() != Node.ELEMENT_NODE)
                currentNode = currentNode.getParentStoredNode();
            while (currentNode != null) {
                if (config.matches(p))
                    topMost = currentNode;
                currentNode = currentNode.getParentStoredNode();
                p.removeLastComponent();
            }
            return topMost;
        }
        return null;
    }

    private StreamListener listener = new LuceneStreamListener();

    public StreamListener getListener() {
        return listener;
    }

    public MatchListener getMatchListener(DBBroker broker, NodeProxy proxy) {
        boolean needToFilter = false;
        Match nextMatch = proxy.getMatches();
        while (nextMatch != null) {
            if (nextMatch.getIndexId() == LuceneIndex.ID) {
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
            BytesRef bytes = new BytesRef(NumericUtils.BUF_SIZE_INT);
            NumericUtils.intToPrefixCoded(docId, 0, bytes);
            Term dt = new Term(FIELD_DOC_ID, bytes);
            writer.deleteDocuments(dt);
        } catch (IOException e) {
            LOG.warn("Error while removing lucene index: " + e.getMessage(), e);
        } finally {
            index.releaseWriter(writer);
            mode = StreamListener.STORE;
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
            LOG.warn("Error while removing lucene index: " + e.getMessage(), e);
        } finally {
            index.releaseWriter(writer);
            mode = StreamListener.STORE;
        }
    }
    
    public void removeCollection(Collection collection, DBBroker broker, boolean reindex) {
        if (LOG.isDebugEnabled())
            LOG.debug("Removing collection " + collection.getURI());
        IndexWriter writer = null;
        try {
            writer = index.getWriter();
            for (Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext(); ) {
                DocumentImpl doc = i.next();
                BytesRef bytes = new BytesRef(NumericUtils.BUF_SIZE_INT);
                NumericUtils.intToPrefixCoded(doc.getDocId(), 0, bytes);
                Term dt = new Term(FIELD_DOC_ID, bytes);
                writer.deleteDocuments(dt);
            }
        } catch (IOException e) {
            LOG.error("Error while removing lucene index: " + e.getMessage(), e);
        } catch (PermissionDeniedException e) {
            LOG.error("Error while removing lucene index: " + e.getMessage(), e);
        } finally {
            index.releaseWriter(writer);
            if (reindex) {
                try {
                    index.sync();
                } catch (DBException e) {
                    LOG.warn("Exception during reindex: " + e.getMessage(), e);
                }
            }
            mode = StreamListener.STORE;
        }
        if (LOG.isDebugEnabled())
            LOG.debug("Collection removed.");
    }

    /**
     * Remove specific nodes from the index. This method is used for node updates
     * and called from flush() if the worker is in {@link StreamListener#REMOVE_SOME_NODES}
     * mode.
     */
    protected void removeNodes() {
    	if (nodesToRemove == null)
            return;
        IndexWriter writer = null;
        try {
            writer = index.getWriter();

            BytesRef bytes = new BytesRef(NumericUtils.BUF_SIZE_INT);
            NumericUtils.intToPrefixCoded(currentDoc.getDocId(), 0, bytes);
            Term dt = new Term(FIELD_DOC_ID, bytes);
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
            LOG.warn("Error while deleting lucene index entries: " + e.getMessage(), e);
        } finally {
            index.releaseWriter(writer);
            nodesToRemove = null;
        }
    }

    private NodeId readNodeId(int doc, BinaryDocValues nodeIdValues, BrokerPool pool) {
        BytesRef ref = new BytesRef(buf);
        nodeIdValues.get(doc, ref);
        int units = ByteConversion.byteToShort(ref.bytes, ref.offset);
        return pool.getNodeFactory().createFromData(units, ref.bytes, ref.offset + 2);
    }

    /**
     * Query the index. Returns a node set containing all matching nodes. Each node
     * in the node set has a {@link org.exist.indexing.lucene.LuceneIndexWorker.LuceneMatch}
     * element attached, which stores the score and a link to the query which generated it.
     *
     * @param context current XQuery context
     * @param contextId current context id, identify to track the position inside nested XPath predicates
     * @param docs query will be restricted to documents in this set
     * @param contextSet if specified, returned nodes will be descendants of the nodes in this set
     * @param qnames query will be restricted to nodes with the qualified names given here
     * @param queryStr a lucene query string
     * @param axis which node is returned: the node in which a match was found or the corresponding ancestor
     *  from the contextSet
     * @return node set containing all matching nodes
     * 
     * @throws IOException
     * @throws ParseException
     */
    public NodeSet query(XQueryContext context, int contextId, DocumentSet docs, NodeSet contextSet,
        List<QName> qnames, String queryStr, int axis, Properties options)
            throws IOException, ParseException, XPathException {
        qnames = getDefinedIndexes(qnames);
        NodeSet resultSet = new NewArrayNodeSet();
        boolean returnAncestor = axis == NodeSet.ANCESTOR;
        IndexSearcher searcher = null;
        try {
            searcher = index.getSearcher();
            for (QName qname : qnames) {
                String field = LuceneUtil.encodeQName(qname, index.getBrokerPool().getSymbols());
                Analyzer analyzer = getAnalyzer(null, qname, context.getBroker(), docs);
                QueryParserWrapper parser = getQueryParser(field, analyzer, docs);
                setOptions(options, parser.getConfiguration());
                Query query = parser.parse(queryStr);
                searchAndProcess(contextId, qname, docs, contextSet, resultSet,
                    returnAncestor, searcher, query, context.getWatchDog());
            }
        } finally {
            index.releaseSearcher(searcher);
        }
        return resultSet;
    }

    protected void setOptions(Properties options, CommonQueryParserConfiguration parser) throws ParseException {
        if (options == null)
            return;
        String option = options.getProperty(OPTION_DEFAULT_OPERATOR);
        if (option != null && parser instanceof QueryParserBase) {
            if (DEFAULT_OPERATOR_OR.equals(option))
                ((QueryParserBase)parser).setDefaultOperator(QueryParser.OR_OPERATOR);
            else
                ((QueryParserBase)parser).setDefaultOperator(QueryParser.AND_OPERATOR);
        }
        option = options.getProperty(OPTION_LEADING_WILDCARD);
        if (option != null)
            parser.setAllowLeadingWildcard(option.equalsIgnoreCase("yes"));
        option = options.getProperty(OPTION_PHRASE_SLOP);
        if (option != null) {
            try {
                int slop = Integer.parseInt(option);
                parser.setPhraseSlop(slop);
            } catch (NumberFormatException e) {
                throw new ParseException("value for option " + OPTION_PHRASE_SLOP + " needs to be a number");
            }
        }
        option = options.getProperty(OPTION_FILTER_REWRITE);
        if (option != null) {
            if (option.equalsIgnoreCase("yes"))
                parser.setMultiTermRewriteMethod(MultiTermQuery.CONSTANT_SCORE_FILTER_REWRITE);
            else
                parser.setMultiTermRewriteMethod(MultiTermQuery.CONSTANT_SCORE_BOOLEAN_QUERY_REWRITE);
        }
    }

    /**
     * Query the index. Returns a node set containing all matching nodes. Each node
     * in the node set has a {@link org.exist.indexing.lucene.LuceneIndexWorker.LuceneMatch}
     * element attached, which stores the score and a link to the query which generated it.
     *
     * @param context current XQuery context
     * @param contextId current context id, identify to track the position inside nested XPath predicates
     * @param docs query will be restricted to documents in this set
     * @param contextSet if specified, returned nodes will be descendants of the nodes in this set
     * @param qnames query will be restricted to nodes with the qualified names given here
     * @param queryRoot an XML representation of the query, see {@link XMLToQuery}.
     * @param axis which node is returned: the node in which a match was found or the corresponding ancestor
     *  from the contextSet
     * @return node set containing all matching nodes
     *
     * @throws IOException
     * @throws ParseException
     */
    public NodeSet query(XQueryContext context, int contextId, DocumentSet docs, NodeSet contextSet,
                         List<QName> qnames, Element queryRoot, int axis, Properties options)
            throws IOException, ParseException, XPathException {
        qnames = getDefinedIndexes(qnames);
        NodeSet resultSet = new NewArrayNodeSet();
        boolean returnAncestor = axis == NodeSet.ANCESTOR;
        IndexSearcher searcher = null;
        try {
            searcher = index.getSearcher();
            for (QName qname : qnames) {
                String field = LuceneUtil.encodeQName(qname, index.getBrokerPool().getSymbols());
                analyzer = getAnalyzer(null, qname, context.getBroker(), docs);
                Query query = queryTranslator.parse(field, queryRoot, analyzer, options);
                if (query != null) {
	                searchAndProcess(contextId, qname, docs, contextSet, resultSet,
                        returnAncestor, searcher, query, context.getWatchDog());
                }
            }
        } finally {
            index.releaseSearcher(searcher);
        }
        return resultSet;
    }

    public NodeSet queryField(XQueryContext context, int contextId, DocumentSet docs, NodeSet contextSet,
            String field, Element queryRoot, int axis, Properties options)
            throws IOException, XPathException {
        NodeSet resultSet = new NewArrayNodeSet();
        boolean returnAncestor = axis == NodeSet.ANCESTOR;
        IndexSearcher searcher = null;
        try {
            searcher = index.getSearcher();
            analyzer = getAnalyzer(field, null, context.getBroker(), docs);
            Query query = queryTranslator.parse(field, queryRoot, analyzer, options);
            if (query != null) {
                searchAndProcess(contextId, null, docs, contextSet, resultSet,
                    returnAncestor, searcher, query, context.getWatchDog());
            }
        } finally {
            index.releaseSearcher(searcher);
        }
        return resultSet;
    }

    private void searchAndProcess(int contextId, QName qname, DocumentSet docs,
            NodeSet contextSet, NodeSet resultSet, boolean returnAncestor,
            IndexSearcher searcher, Query query, XQueryWatchDog watchDog) throws IOException, TerminatedException {
        LuceneHitCollector collector = new LuceneHitCollector(qname, query, docs, contextSet, resultSet, returnAncestor, contextId, watchDog);
        searcher.search(query, collector);
    }

    public NodeSet queryField(XQueryContext context, int contextId, DocumentSet docs, NodeSet contextSet,
            String field, String queryString, int axis, Properties options)
            throws IOException, ParseException, XPathException {
        NodeSet resultSet = new NewArrayNodeSet();
        boolean returnAncestor = axis == NodeSet.ANCESTOR;
        IndexSearcher searcher = null;
        try {
            searcher = index.getSearcher();
            Analyzer analyzer = getAnalyzer(field, null, context.getBroker(), docs);
            LOG.debug("Using analyzer " + analyzer + " for " + queryString);
            QueryParserWrapper parser = getQueryParser(field, analyzer, docs);
            setOptions(options, parser.getConfiguration());
            Query query = parser.parse(queryString);
            searchAndProcess(contextId, null, docs, contextSet, resultSet,
                returnAncestor, searcher, query, context.getWatchDog());
        } finally {
            index.releaseSearcher(searcher);
        }
        return resultSet;
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
            LOG.error("Expected <doc> got <" + descriptor.getNode().getLocalName() + ">");
            return;
        }

        // Setup parser for SOLR syntax and parse
        PlainTextIndexConfig solrconfParser = new PlainTextIndexConfig();
        solrconfParser.parse(descriptor);
        
        // Get <doc> information
        PlainTextDoc solrDoc = solrconfParser.getDoc();
        
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
            
            Analyzer fieldAnalyzer = (fieldType == null) ? null : fieldType.getAnalyzer();
            
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
            LOG.warn("An exception was caught while indexing document: " + e.getMessage(), e);

        } finally {
            index.releaseWriter(writer);
            pendingDoc = null;
            cachedNodesSize = 0;
        }
    }
    
    /**
     *  SOLR
     * @param context
     * @param toBeMatchedURIs
     * @param queryText
     * @return search report
     */
    public NodeImpl search(final XQueryContext context, final List<String> toBeMatchedURIs, String queryText) throws XPathException {
        
        NodeImpl report = null;
        
        IndexSearcher searcher = null;
        try {
            // Get index searcher
            searcher = index.getSearcher();
            
            // Get analyzer : to be retrieved from configuration
            final Analyzer searchAnalyzer = new StandardAnalyzer(Version.LUCENE_43);

            // Setup query Version, default field, analyzer
            final QueryParserWrapper parser = getQueryParser("", searchAnalyzer, null);
            final Query query = parser.parse(queryText);
                       
            // extract all used fields from query
            final String[] fields = LuceneUtil.extractFields(query, searcher.getIndexReader());

            final PlainTextHighlighter highlighter = new PlainTextHighlighter(query, searcher.getIndexReader());

            final MemTreeBuilder builder = new MemTreeBuilder();
            builder.startDocument();

            // start root element
            final int nodeNr = builder.startElement("", "results", "results", null);

            // Perform actual search
            searcher.search(query, new Collector() {
                private Scorer scorer;
                private AtomicReader reader;

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
                    if(isDocumentMatch(fDocUri, toBeMatchedURIs)){
                        
                        DocumentImpl storedDoc = null;
                        try {
                            // try to read document to check if user is allowed to access it
                            storedDoc = context.getBroker().getXMLResource(XmldbURI.createInternal(fDocUri), Lock.READ_LOCK);
                            if (storedDoc == null) {
                                return;
                            }

                            // setup attributes
                            AttributesImpl attribs = new AttributesImpl();
                            attribs.addAttribute("", "uri", "uri", "CDATA", fDocUri);
                            attribs.addAttribute("", "score", "score", "CDATA", ""+score);
    
                            // write element and attributes
                            builder.startElement("", "search", "search", attribs);
                            for (String field : fields) {
                                String[] fieldContent = doc.getValues(field);
                                attribs.clear();
                                attribs.addAttribute("", "name", "name", "CDATA", field);
                                for (String content : fieldContent) {
                                    List<Offset> offsets = highlighter.getOffsets(content, searchAnalyzer);
                                    if (offsets != null) {
                                        builder.startElement("", "field", "field", attribs);
                                        highlighter.highlight(content, offsets, builder);
                                        builder.endElement();
                                    }
                                }
                            }
                            builder.endElement();
    
                            // clean attributes
                            attribs.clear();
                        } catch (PermissionDeniedException e) {
                            // not allowed to read the document: ignore the match.
                        } finally {
                            if (storedDoc != null) {
                                storedDoc.getUpdateLock().release(Lock.READ_LOCK);
                            }
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
            });

            // finish root element
            builder.endElement();
            
            //System.out.println(builder.getDocument().toString());
            
            // TODO check
            report = ((org.exist.memtree.DocumentImpl) builder.getDocument()).getNode(nodeNr);


        } catch (Exception ex){
            ex.printStackTrace();
            LOG.error(ex);
            throw new XPathException(ex);
        
        } finally {
            index.releaseSearcher(searcher);
        }
        
        
        return report;
    }
    
    public String getFieldContent(int docId, String field) throws IOException {
        BytesRef bytes = new BytesRef(NumericUtils.BUF_SIZE_INT);
        NumericUtils.intToPrefixCoded(docId, 0, bytes);
        Term dt = new Term(FIELD_DOC_ID, bytes);

    	IndexReader reader = null;
        try {
            reader = index.getReader();

            List<AtomicReaderContext> leaves = reader.leaves();
            for (AtomicReaderContext context : leaves) {
                DocsEnum docs = context.reader().termDocsEnum(dt);
                if (docs != null && docs.nextDoc() != DocsEnum.NO_MORE_DOCS) {
                    Document doc = reader.document(docs.docID());
                    return doc.get(field);
                }
            }
        } finally {
            index.releaseReader(reader);
        }
        return null;
    }
    
    public boolean hasIndex(int docId) throws IOException {
        BytesRef bytes = new BytesRef(NumericUtils.BUF_SIZE_INT);
        NumericUtils.intToPrefixCoded(docId, 0, bytes);
        Term dt = new Term(FIELD_DOC_ID, bytes);

        IndexReader reader = null;
        try {
            reader = index.getReader();

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
        } finally {
            index.releaseReader(reader);
        }
    }
    
    /**
     *  Check if Lucene found document matches specified documents or collections.
     * Collections should end with "/".
     * 
     * @param docUri    The uri of the document found by lucene
     * @param toBeMatchedUris     List of document and collection URIs
     * @return TRUE if documenturi is matched or is in collection.
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
        private final byte[] buf = new byte[1024];
        private final QName qname;
        private final DocumentSet docs;
        private final NodeSet contextSet;
        private final NodeSet resultSet;
        private final boolean returnAncestor;
        private final int contextId;
        private final Query query;
        private final XQueryWatchDog watchdog;

        private LuceneHitCollector(QName qname, Query query, DocumentSet docs, NodeSet contextSet, NodeSet resultSet, boolean returnAncestor,
                                   int contextId, XQueryWatchDog watchDog) {
            this.qname = qname;
            this.docs = docs;
            this.contextSet = contextSet;
            this.resultSet = resultSet;
            this.returnAncestor = returnAncestor;
            this.contextId = contextId;
            this.query = query;
            this.watchdog = watchDog;
        }

        @Override
        public void setScorer(Scorer scorer) throws IOException {
            this.scorer = scorer;
        }

        @Override
        public void setNextReader(AtomicReaderContext atomicReaderContext) throws IOException {
            this.reader = atomicReaderContext.reader();
            this.docIdValues = this.reader.getNumericDocValues(FIELD_DOC_ID);
            this.nodeIdValues = this.reader.getBinaryDocValues(LuceneUtil.FIELD_NODE_ID);
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
                BytesRef ref = new BytesRef(buf);
                this.nodeIdValues.get(doc, ref);
                int units = ByteConversion.byteToShort(ref.bytes, ref.offset);
                NodeId nodeId = index.getBrokerPool().getNodeFactory().createFromData(units, ref.bytes, ref.offset + 2);
                //LOG.info("doc: " + docId + "; node: " + nodeId.toString() + "; units: " + units);

                NodeProxy storedNode = new NodeProxy(storedDocument, nodeId);
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
                            LuceneMatch match = new LuceneMatch(contextId, nodeId, query);
                            match.setScore(score);
                            parentNode.addMatch(match);
                            resultSet.add(parentNode, sizeHint);
                            if (Expression.NO_CONTEXT_ID != contextId) {
                                parentNode.deepCopyContext(storedNode, contextId);
                            } else
                                parentNode.copyContext(storedNode);
                        }
                    } else {
                        LuceneMatch match = new LuceneMatch(contextId, nodeId, query);
                        match.setScore(score);
                        storedNode.addMatch(match);
                        resultSet.add(storedNode, sizeHint);
                    }
                } else {
                    LuceneMatch match = new LuceneMatch(contextId, nodeId, query);
                    match.setScore(score);
                    storedNode.addMatch(match);
                    resultSet.add(storedNode);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Check index configurations for all collection in the given DocumentSet and return
     * a list of QNames, which have indexes defined on them.
     *
     * @return List of QName objects on which indexes are defined
     */
    public List<QName> getDefinedIndexes(List<QName> qnames) {
        List<QName> indexes = new ArrayList<QName>(20);
        if (qnames != null && !qnames.isEmpty()) {
            for (QName qname : qnames) {
                if (qname.getLocalName() == null || qname.getNamespaceURI() == null)
                    getDefinedIndexesFor(qname, indexes);
                else
                    indexes.add(qname);
            }
            return indexes;
        }
        return getDefinedIndexesFor(null, indexes);
    }

    private List<QName> getDefinedIndexesFor(QName qname, List<QName> indexes) {
        IndexReader reader = null;
        try {
            reader = index.getReader();
            for (FieldInfo info: MultiFields.getMergedFieldInfos(reader)) {
                if (!FIELD_DOC_ID.equals(info.name)) {
                    QName name = LuceneUtil.decodeQName(info.name, index.getBrokerPool().getSymbols());
                    if (name != null && (qname == null || matchQName(qname, name)))
                        indexes.add(name);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            index.releaseReader(reader);
        }
        return indexes;
    }

    private static boolean matchQName(QName qname, QName candidate) {
        boolean match = true;
        if (qname.getLocalName() != null)
            match = qname.getLocalName().equals(candidate.getLocalName());
        if (match && qname.getNamespaceURI() != null && qname.getNamespaceURI().length() > 0)
            match = qname.getNamespaceURI().equals(candidate.getNamespaceURI());
        return match;
    }

    /**
     * Return the analyzer to be used for the given field or qname. Either field
     * or qname should be specified.
     */
    protected Analyzer getAnalyzer(String field, QName qname, DBBroker broker, DocumentSet docs) {
        for (Iterator<Collection> i = docs.getCollectionIterator(); i.hasNext(); ) {
            Collection collection = i.next();
            IndexSpec idxConf = collection.getIndexConfiguration(broker);
            if (idxConf != null) {
                LuceneConfig config = (LuceneConfig) idxConf.getCustomIndexSpec(LuceneIndex.ID);
                if (config != null) {
                    Analyzer analyzer;
                    if (field == null)
                    	analyzer = config.getAnalyzer(qname);
                    else
                    	analyzer = config.getAnalyzer(field);
                    if (analyzer != null)
                        return analyzer;
                }
            }
        }
        return index.getDefaultAnalyzer();
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
        List<QName> qnames = hints == null ? null : (List<QName>)hints.get(QNAMES_KEY);
        qnames = getDefinedIndexes(qnames);
        //Expects a StringValue
        String start = null, end = null;
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
    }

    private Occurrences[] scanIndexByQName(List<QName> qnames, DocumentSet docs, NodeSet nodes, String start, String end, long max) {
        TreeMap<String, Occurrences> map = new TreeMap<String, Occurrences>();
        IndexReader reader = null;
        try {
            reader = index.getReader();
            for (QName qname : qnames) {
                String field = LuceneUtil.encodeQName(qname, index.getBrokerPool().getSymbols());
                List<AtomicReaderContext> leaves = reader.leaves();
                for (AtomicReaderContext context : leaves) {
                    NumericDocValues docIdValues = context.reader().getNumericDocValues(FIELD_DOC_ID);
                    BinaryDocValues nodeIdValues = context.reader().getBinaryDocValues(LuceneUtil.FIELD_NODE_ID);
                    Bits liveDocs = context.reader().getLiveDocs();
                    Terms terms = context.reader().terms(field);
                    if (terms == null)
                        continue;
                    TermsEnum termsIter = terms.iterator(null);
                    if (termsIter.next() == null) {
                        continue;
                    }
                    do {
                        if (map.size() >= max) {
                            break;
                        }
                        BytesRef ref = termsIter.term();
                        String term = ref.utf8ToString();
                        boolean include = true;
                        if (end != null) {
                            if (term.compareTo(end) > 0)
                                include = false;
                        } else if (start != null && !term.startsWith(start))
                            include = false;
                        if (include) {
                            DocsEnum docsEnum = termsIter.docs(null, null);
                            while (docsEnum.nextDoc() != DocsEnum.NO_MORE_DOCS) {
                                if (liveDocs != null && !liveDocs.get(docsEnum.docID())) {
                                    continue;
                                }
                                int docId = (int) docIdValues.get(docsEnum.docID());
                                DocumentImpl storedDocument = docs.getDoc(docId);
                                if (storedDocument == null)
                                    continue;
                                NodeId nodeId = null;
                                if (nodes != null) {
                                    BytesRef nodeIdRef = new BytesRef(buf);
                                    nodeIdValues.get(docsEnum.docID(), nodeIdRef);
                                    int units = ByteConversion.byteToShort(nodeIdRef.bytes, nodeIdRef.offset);
                                    nodeId = index.getBrokerPool().getNodeFactory().createFromData(units, nodeIdRef.bytes, nodeIdRef.offset + 2);
                                }
                                if (nodeId == null || nodes.get(storedDocument, nodeId) != null) {
                                    Occurrences oc = map.get(term);
                                    if (oc == null) {
                                        oc = new Occurrences(term);
                                        map.put(term, oc);
                                    }
                                    oc.addDocument(storedDocument);
                                    oc.addOccurrences(docsEnum.freq());
                                }
                            }
                        }
                    } while(termsIter.next() != null);
                }
            }
        } catch (IOException e) {
            LOG.warn("Error while scanning lucene index entries: " + e.getMessage(), e);
        } finally {
            index.releaseReader(reader);
        }
        Occurrences[] occur = new Occurrences[map.size()];
        return map.values().toArray(occur);
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
     * @param nodeId
     * @param qname
     * @param content
     */
    protected void indexText(NodeId nodeId, QName qname, NodePath path, LuceneIndexConfig config, CharSequence content) {
        PendingDoc pending = new PendingDoc(nodeId, qname, path, content, config);
        nodesToWrite.add(pending);
        cachedNodesSize += content.length();
        if (cachedNodesSize > maxCachedNodesSize)
            write();
    }

    private class PendingDoc {
        NodeId nodeId;
        CharSequence text;
        QName qname;
        LuceneIndexConfig idxConf;

        private PendingDoc(NodeId nodeId, QName qname, NodePath path, CharSequence text, LuceneIndexConfig idxConf) {
            this.nodeId = nodeId;
            this.qname = qname;
            this.text = text;
            this.idxConf = idxConf;
        }
    }
    
    private void write() {
        if (nodesToWrite == null || nodesToWrite.size() == 0)
            return;
        IndexWriter writer = null;
        try {
            writer = index.getWriter();
            // docId and nodeId are stored as doc value
            NumericDocValuesField fDocId = new NumericDocValuesField(FIELD_DOC_ID, 0);
            BinaryDocValuesField fNodeId = new BinaryDocValuesField(LuceneUtil.FIELD_NODE_ID, new BytesRef(8));
            // docId also needs to be indexed
            IntField fDocIdIdx = new IntField(FIELD_DOC_ID, 0, IntField.TYPE_NOT_STORED);

            final List<Field> metas = new ArrayList<Field>();
            final List<CategoryPath> paths = new ArrayList<CategoryPath>();

            broker.getIndexController().streamMetas(new MetaStreamListener() {
                @Override
                public void metadata(QName key, Object value) {
                    if (value instanceof String) {
                        String name = key.getLocalName();//LuceneUtil.encodeQName(key, index.getBrokerPool().getSymbols());
                        Field fld = new Field(name, value.toString(), Field.Store.NO, Field.Index.ANALYZED, Field.TermVector.YES);
                        metas.add(fld);
                        //System.out.println(" "+name+" = "+value.toString());
                        
                        paths.add(new CategoryPath(name, value.toString()));
                    }
                }
            });
            
            TaxonomyWriter taxoWriter = index.getTaxonomyWriter();
            FacetFields facetFields = new FacetFields(taxoWriter);

            for (PendingDoc pending : nodesToWrite) {
                final Document doc = new Document();
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

                String contentField;
                // the text content is indexed in a field using either
                // the qname of the element or attribute or the field
                // name defined in the configuration
                if (pending.idxConf.isNamed())
                	contentField = pending.idxConf.getName();
                else
                	contentField = LuceneUtil.encodeQName(pending.qname, index.getBrokerPool().getSymbols());

                Field fld = new Field(contentField, pending.text.toString(), Field.Store.NO, Field.Index.ANALYZED, Field.TermVector.YES);
                if (pending.idxConf.getBoost() > 0)
                    fld.setBoost(pending.idxConf.getBoost());

                else if (config.getBoost() > 0)
                    fld.setBoost(config.getBoost());

                doc.add(fld);

                fDocIdIdx.setIntValue(currentDoc.getDocId());
                doc.add(fDocIdIdx);
                
                for (Field meta : metas) {
                    doc.add(meta);
                }
                if (!paths.isEmpty()) {
                    facetFields.addFields(doc, paths);
                }
                
                if (pending.idxConf.getAnalyzer() == null)
                    writer.addDocument(doc);
                else {
                    writer.addDocument(doc, pending.idxConf.getAnalyzer());
                }
            }
        } catch (IOException e) {
            LOG.warn("An exception was caught while indexing document: " + e.getMessage(), e);
        } finally {
            index.releaseWriter(writer);
            nodesToWrite = new ArrayList<PendingDoc>();
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
            LOG.warn("An exception was caught while optimizing the lucene index: " + e.getMessage(), e);
        } finally {
            index.releaseWriter(writer);
        }
    }

    private class LuceneStreamListener extends AbstractStreamListener {

        @Override
        public void startElement(Txn transaction, ElementImpl element, NodePath path) {
            if (mode == STORE && config != null) {
                if (contentStack != null && !contentStack.isEmpty()) {
                    for (TextExtractor extractor : contentStack) {
                        extractor.startElement(element.getQName());
                    }
                }
                Iterator<LuceneIndexConfig> configIter = config.getConfig(path);
                if (configIter != null) {
                    if (contentStack == null) contentStack = new Stack<TextExtractor>();
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
                if (mode == STORE && contentStack != null && !contentStack.isEmpty()) {
                    for (TextExtractor extractor : contentStack) {
                        extractor.endElement(element.getQName());
                    }
                }
                Iterator<LuceneIndexConfig> configIter = config.getConfig(path);
                if (mode != REMOVE_ALL_NODES && configIter != null) {
                    if (mode == REMOVE_SOME_NODES) {
                        nodesToRemove.add(element.getNodeId());
                    } else {
                        while (configIter.hasNext()) {
                            LuceneIndexConfig configuration = configIter.next();
                            if (configuration.match(path)) {
                                TextExtractor extractor = contentStack.pop();
                                indexText(element.getNodeId(), element.getQName(), 
                                    path, extractor.getIndexConfig(), extractor.getText());
                            }
                        }
                    }
                }
            }
            super.endElement(transaction, element, path);
        }

        @Override
        public void attribute(Txn transaction, AttrImpl attrib, NodePath path) {
            path.addComponent(attrib.getQName());
            Iterator<LuceneIndexConfig> configIter = null;
            if (config != null)
                configIter = config.getConfig(path);
            if (mode != REMOVE_ALL_NODES && configIter != null) {
                if (mode == REMOVE_SOME_NODES) {
                    nodesToRemove.add(attrib.getNodeId());
                } else {
                    while (configIter.hasNext()) {
                        LuceneIndexConfig configuration = configIter.next();
                        if (configuration.match(path)) {
                            indexText(attrib.getNodeId(), attrib.getQName(), path,
                                configuration, attrib.getValue());
                        }
                    }
                }
            }
            path.removeLastComponent();
            super.attribute(transaction, attrib, path);
        }

        @Override
        public void characters(Txn transaction, CharacterDataImpl text, NodePath path) {
            if (contentStack != null && !contentStack.isEmpty()) {
                for (TextExtractor extractor : contentStack) {
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
    }

    /**
     * Match class containing the score of a match and a reference to
     * the query that generated it.
     */
    public class LuceneMatch extends Match {

        private float score = 0.0f;
        private Query query;

        public LuceneMatch(int contextId, NodeId nodeId, Query query) {
            super(contextId, nodeId, null);
            this.query = query;
        }

        public LuceneMatch(LuceneMatch copy) {
            super(copy);
            this.score = copy.score;
            this.query = copy.query;
        }

        @Override
        public Match createInstance(int contextId, NodeId nodeId, String matchTerm) {
            return null;
        }

        public Match createInstance(int contextId, NodeId nodeId, Query query) {
            return new LuceneMatch(contextId, nodeId, query);
        }

        @Override
        public Match newCopy() {
            return new LuceneMatch(this);
        }

        @Override
        public String getIndexId() {
            return LuceneIndex.ID;
        }

        public Query getQuery() {
            return query;
        }

        public float getScore() {
            return score;
        }

        protected void setScore(float score) {
            this.score = score;
        }

        @Override
        public boolean equals(Object other) {
            if(!(other instanceof LuceneMatch))
                return false;
            LuceneMatch o = (LuceneMatch) other;
            return (nodeId == o.nodeId || nodeId.equals(o.nodeId))  &&
                query == ((LuceneMatch)other).query;
        }

        @Override
        public boolean matchEquals(Match other) {
            return equals(other);
        }
    }
}

