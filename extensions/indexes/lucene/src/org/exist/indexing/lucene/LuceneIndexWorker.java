package org.exist.indexing.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.document.FieldSelectorResult;
import org.apache.lucene.document.NumericField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.NumericUtils;
import org.apache.lucene.util.OpenBitSet;
import org.exist.collections.Collection;
import org.exist.dom.AttrImpl;
import org.exist.dom.CharacterDataImpl;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ElementImpl;
import org.exist.dom.Match;
import org.exist.dom.NewArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.StoredNode;
import org.exist.dom.SymbolTable;
import org.exist.indexing.AbstractStreamListener;
import org.exist.indexing.IndexController;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.MatchListener;
import org.exist.indexing.OrderedValuesIndex;
import org.exist.indexing.QNamedKeysIndex;
import org.exist.indexing.StreamListener;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
import org.exist.storage.IndexSpec;
import org.exist.storage.NodePath;
import org.exist.storage.txn.Txn;
import org.exist.util.ByteConversion;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.Occurrences;
import org.exist.xquery.*;
import org.exist.xquery.value.IntegerValue;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class LuceneIndexWorker implements OrderedValuesIndex, QNamedKeysIndex {

    public static final String OPTION_DEFAULT_OPERATOR = "default-operator";
    public static final String OPTION_PHRASE_SLOP = "phrase-slop";
    public static final String OPTION_LEADING_WILDCARD = "leading-wildcard";
    public static final String OPTION_FILTER_REWRITE = "filter-rewrite";
    public static final String DEFAULT_OPERATOR_OR = "or";
    
    private static final Logger LOG = Logger.getLogger(LuceneIndexWorker.class);

    private static final FieldSelector NODE_FIELD_SELECTOR = new NodeFieldSelector();

    private LuceneIndex index;
    @SuppressWarnings("unused")
	private IndexController controller;

    private LuceneMatchListener matchListener = null;

    private XMLToQuery queryTranslator;

    private DBBroker broker;

    private DocumentImpl currentDoc = null;
    private int mode = 0;
    
    private LuceneConfig config;
    private Stack<TextExtractor> contentStack = null;
    private Set<NodeId> nodesToRemove = null;
    private List<PendingDoc> nodesToWrite = null;
    private int cachedNodesSize = 0;

    private int maxCachedNodesSize = 4096 * 1024;
    private Analyzer analyzer;

    public static final String FIELD_NODE_ID = "nodeId";
    public static final String FIELD_DOC_ID = "docId";

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

    public Object configure(IndexController controller, NodeList configNodes, Map namespaces) throws DatabaseConfigurationException {
        this.controller = controller;
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

    public StoredNode getReindexRoot(StoredNode node, NodePath path, boolean includeSelf) {
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
        IndexReader reader = null;
        try {
            reader = index.getWritingReader();
            Term dt = new Term(FIELD_DOC_ID, NumericUtils.intToPrefixCoded(docId));
            reader.deleteDocuments(dt);
            reader.flush();
        } catch (IOException e) {
            LOG.warn("Error while removing lucene index: " + e.getMessage(), e);
        } finally {
            index.releaseWritingReader(reader);
            mode = StreamListener.STORE;
        }
    }

    public void removeCollection(Collection collection, DBBroker broker) {
        if (LOG.isDebugEnabled())
            LOG.debug("Removing collection " + collection.getURI());
        IndexReader reader = null;
        try {
            reader = index.getWritingReader();
            for (Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext(); ) {
                DocumentImpl doc = i.next();
                Term dt = new Term(FIELD_DOC_ID, NumericUtils.intToPrefixCoded(doc.getDocId()));
                TermDocs td = reader.termDocs(dt);
                while (td.next()) {
                    reader.deleteDocument(td.doc());
                }
            }
            reader.flush();
        } catch (IOException e) {
            LOG.warn("Error while removing lucene index: " + e.getMessage(), e);
        } finally {
            index.releaseWritingReader(reader);
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
        IndexReader reader = null;
        try {
            reader = index.getWritingReader();
            Term dt = new Term(FIELD_DOC_ID, NumericUtils.intToPrefixCoded(currentDoc.getDocId()));
            TermDocs docsEnum = reader.termDocs(dt);
            while (docsEnum.next()) {
                Document doc = reader.document(docsEnum.doc());
                NodeId nodeId = readNodeId(doc);
                if (nodesToRemove.contains(nodeId)) {
                    reader.deleteDocument(docsEnum.doc());
                }
            }
            nodesToRemove = null;
            reader.flush();
        } catch (IOException e) {
            LOG.warn("Error while deleting lucene index entries: " + e.getMessage(), e);
        } finally {
            index.releaseWritingReader(reader);
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
            throws IOException, ParseException, TerminatedException {
        qnames = getDefinedIndexes(qnames);
        NodeSet resultSet = new NewArrayNodeSet();
        boolean returnAncestor = axis == NodeSet.ANCESTOR;
        IndexSearcher searcher = null;
        try {
            searcher = index.getSearcher();
            for (QName qname : qnames) {
                String field = encodeQName(qname);
                Analyzer analyzer = getAnalyzer(null, qname, context.getBroker(), docs);
                QueryParser parser = new QueryParser(field, analyzer);
                setOptions(options, parser);
                Query query = parser.parse(queryStr);
                searchAndProcess(contextId, qname, docs, contextSet, resultSet,
                    returnAncestor, searcher, query, context.getWatchDog());
            }
        } finally {
            index.releaseSearcher(searcher);
        }
        return resultSet;
    }

    private void setOptions(Properties options, QueryParser parser) throws ParseException {
        if (options == null)
            return;
        String option = options.getProperty(OPTION_DEFAULT_OPERATOR);
        if (option != null) {
            if (DEFAULT_OPERATOR_OR.equals(option))
                parser.setDefaultOperator(QueryParser.OR_OPERATOR);
            else
                parser.setDefaultOperator(QueryParser.AND_OPERATOR);
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
                String field = encodeQName(qname);
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
        LuceneHitCollector collector = new LuceneHitCollector();
        searcher.search(query, collector);
        processHits(collector.getDocs(), searcher, contextId, qname, docs, contextSet, resultSet, returnAncestor, query, watchDog);
    }

    public NodeSet queryField(XQueryContext context, int contextId, DocumentSet docs, NodeSet contextSet,
            String field, String queryString, int axis, Properties options)
            throws IOException, ParseException, TerminatedException {
        NodeSet resultSet = new NewArrayNodeSet();
        boolean returnAncestor = axis == NodeSet.ANCESTOR;
        IndexSearcher searcher = null;
        try {
            searcher = index.getSearcher();
            Analyzer analyzer = getAnalyzer(field, null, context.getBroker(), docs);
            LOG.debug("Using analyzer " + analyzer + " for " + queryString);
            QueryParser parser = new QueryParser(field, analyzer);
            setOptions(options, parser);
            Query query = parser.parse(queryString);
            searchAndProcess(contextId, null, docs, contextSet, resultSet,
                returnAncestor, searcher, query, context.getWatchDog());
        } finally {
            index.releaseSearcher(searcher);
        }
        return resultSet;
    }
    
    /**
     * Process the query results collected from the Lucene index and
     * map them to the corresponding XML nodes in eXist.
     */
    private void processHits(List<ScoreDoc> hits, IndexSearcher searcher, int contextId, QName qname, DocumentSet docs, NodeSet contextSet,
                             NodeSet resultSet, boolean returnAncestor, Query query, XQueryWatchDog watchDog) throws TerminatedException {
        for (ScoreDoc scoreDoc : hits) {
            watchDog.proceed(null);
            try {
                Document doc = searcher.doc(scoreDoc.doc, NODE_FIELD_SELECTOR);
                String fDocId = doc.get(FIELD_DOC_ID);
                int docId = Integer.parseInt(fDocId);
                DocumentImpl storedDocument = docs.getDoc(docId);
                if (storedDocument == null)
                    continue;
                NodeId nodeId = readNodeId(doc);
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
                            match.setScore(scoreDoc.score);
                            parentNode.addMatch(match);
                            resultSet.add(parentNode, sizeHint);
                            if (Expression.NO_CONTEXT_ID != contextId) {
                                parentNode.deepCopyContext(storedNode, contextId);
                            } else
                                parentNode.copyContext(storedNode);
                        }
                    } else {
                        LuceneMatch match = new LuceneMatch(contextId, nodeId, query);
                        match.setScore(scoreDoc.score);
                        storedNode.addMatch(match);
                        resultSet.add(storedNode, sizeHint);
                    }
                } else {
                    LuceneMatch match = new LuceneMatch(contextId, nodeId, query);
                    match.setScore(scoreDoc.score);
                    storedNode.addMatch(match);
                    resultSet.add(storedNode);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static class LuceneHitCollector extends Collector {

        private List<ScoreDoc> docs = new ArrayList<ScoreDoc>();
        private int docBase;
        private Scorer scorer;

        private LuceneHitCollector() {
            //Nothing special to do
        }

        public List<ScoreDoc> getDocs() {
            Collections.sort(docs, new Comparator<ScoreDoc>() {

                public int compare(ScoreDoc scoreDoc, ScoreDoc scoreDoc1) {
                    if (scoreDoc.doc == scoreDoc1.doc)
                        return 0;
                    else if (scoreDoc.doc < scoreDoc1.doc)
                        return -1;
                    return 1;
                }
            });
            return docs;
        }
        
        @Override
        public void setScorer(Scorer scorer) throws IOException {
            this.scorer = scorer;
        }

        @Override
        public void setNextReader(IndexReader indexReader, int docBase) throws IOException {
            this.docBase = docBase;
        }

        @Override
        public boolean acceptsDocsOutOfOrder() {
            return false;
        }

        @Override
        public void collect(int doc) {
            try {
                float score = scorer.score();
                docs.add(new ScoreDoc(doc + docBase, score));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private NodeId readNodeId(Document doc) {
        byte[] temp = doc.getBinaryValue(FIELD_NODE_ID);
        int units = ByteConversion.byteToShort(temp, 0);
        return index.getBrokerPool().getNodeFactory()
                .createFromData(units, temp, 2);
    }

    /**
     * Check index configurations for all collection in the given DocumentSet and return
     * a list of QNames, which have indexes defined on them.
     *
     * @return List of QName objects on which indexes are defined
     */
    private List<QName> getDefinedIndexes(List<QName> qnames) {
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
            java.util.Collection<String> fields = reader.getFieldNames(IndexReader.FieldOption.INDEXED);
            for (String field: fields) {
                if (!FIELD_DOC_ID.equals(field)) {
                    QName name = decodeQName(field);
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
    private Analyzer getAnalyzer(String field, QName qname, DBBroker broker, DocumentSet docs) {
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

    public boolean checkIndex(DBBroker broker) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Occurrences[] scanIndex(XQueryContext context, DocumentSet docs, NodeSet nodes, Map hints) {
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
        if (nodes == null || max < Long.MAX_VALUE)
            return scanIndexByQName(qnames, docs, nodes, start, end, max);
        return scanIndexByNodes(qnames, docs, nodes, start, end, max);
    }

    private Occurrences[] scanIndexByQName(List<QName> qnames, DocumentSet docs, NodeSet nodes, String start, String end, long max) {
        TreeMap<String, Occurrences> map = new TreeMap<String, Occurrences>();
        IndexReader reader = null;
        try {
            reader = index.getReader();
            for (QName qname : qnames) {
                String field = encodeQName(qname);
                TermEnum terms;
                if (start == null)
                    terms = reader.terms(new Term(field, ""));
                else
                    terms = reader.terms(new Term(field, start));
                if (terms == null)
                    continue;
                Term term;
                TermDocs termDocs = reader.termDocs();
                do {
                    term = terms.term();
                    if (term != null && term.field().equals(field)) {
                        boolean include = true;
                        if (end != null) {
                            if (term.text().compareTo(start) > 0)
                                include = false;
                        } else if (start != null && !term.text().startsWith(start))
                            include = false;
                        if (include) {
                            termDocs.seek(term);
                            while (termDocs.next()) {
                                if (reader.isDeleted(termDocs.doc()))
                                    continue;
                                Document doc = reader.document(termDocs.doc());
                                String fDocId = doc.get(FIELD_DOC_ID);
                                int docId = Integer.parseInt(fDocId);
                                DocumentImpl storedDocument = docs.getDoc(docId);
                                if (storedDocument == null)
                                    continue;
                                NodeId nodeId = null;
                                if (nodes != null) {
                                    // load document to check if the current node is in the passed context set, if any
                                    nodeId = readNodeId(doc);
                                }
                                if (nodeId == null || nodes.get(storedDocument, nodeId) != null) {
                                    Occurrences oc = map.get(term.text());
                                    if (oc == null) {
                                        oc = new Occurrences(term.text());
                                        map.put(term.text(), oc);
                                    }
                                    oc.addDocument(storedDocument);
                                    oc.addOccurrences(termDocs.freq());
                                }
                            }
                            termDocs.close();
                        }
                    }
                    if (map.size() >= max)
                        break;
                } while (terms.next());
                termDocs.close();
                terms.close();
            }
        } catch (IOException e) {
            LOG.warn("Error while scanning lucene index entries: " + e.getMessage(), e);
        } finally {
            index.releaseReader(reader);
        }
        Occurrences[] occur = new Occurrences[map.size()];
        return map.values().toArray(occur);
    }

    private Occurrences[] scanIndexByNodes(List<QName> qnames, DocumentSet docs, NodeSet nodes, String start, String end, long max) {
        TreeMap<String, Occurrences> map = new TreeMap<String, Occurrences>();

        FieldSelector selector = new FieldSelector() {
            private static final long serialVersionUID = 3270211696620175721L;
            public FieldSelectorResult accept(String field) {
                if (field.equals(FIELD_NODE_ID))
                    return FieldSelectorResult.LOAD_AND_BREAK;
                return FieldSelectorResult.NO_LOAD;
            }
        };
        IndexSearcher searcher = null;
        try {
            searcher = index.getSearcher();
            IndexReader reader = searcher.getIndexReader();
            for (Iterator<DocumentImpl> i = docs.getDocumentIterator(); i.hasNext(); ) {
                DocumentImpl doc = i.next();
                Query query = new TermQuery(new Term(FIELD_DOC_ID, NumericUtils.intToPrefixCoded(doc.getDocId())));
                DocumentCollector collector = new DocumentCollector(searcher.maxDoc());
                searcher.search(query, collector);

                DocIdSetIterator iter = collector.docs.iterator();
                int next;
                while ((next = iter.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
                    NodeId nodeId = null;
                    if (nodes != null) {
                        // load document to check if the current node is in the passed context set, if any
                        Document luceneDoc = searcher.doc(next, selector);
                        nodeId = readNodeId(luceneDoc);
                    }
                    if (nodeId == null || nodes.get(doc, nodeId) != null) {
                        for (QName qname : qnames) {
                            String field = encodeQName(qname);
                            TermFreqVector tfv = reader.getTermFreqVector(next, field);
                            if (tfv != null) {
                                String[] terms = tfv.getTerms();
                                int[] freq = tfv.getTermFrequencies();
                                for (int j = 0; j < terms.length; j++) {
                                    boolean include = true;
                                    if (end != null) {
                                        if (terms[j].compareTo(start) > 0)
                                            include = false;
                                    } else if (start != null && !terms[j].startsWith(start))
                                        include = false;
                                    if (include) {
                                        Occurrences oc = map.get(terms[j]);
                                        if (oc == null) {
                                            oc = new Occurrences(terms[j]);
                                            map.put(terms[j], oc);
                                        }
                                        oc.addDocument(doc);
                                        oc.addOccurrences(freq[j]);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOG.warn("Error while scanning lucene index entries: " + e.getMessage(), e);
        } finally {
            index.releaseSearcher(searcher);
        }
        return occurrencesToArray(map);
    }

    private Occurrences[] occurrencesToArray(TreeMap<String, Occurrences> map) {
        Occurrences[] occur = new Occurrences[map.size()];
        return map.values().toArray(occur);
    }

    private static class DocumentCollector extends Collector {

        OpenBitSet docs;
        int base = 0;

        private DocumentCollector(int size) {
            docs = new OpenBitSet(size);
        }

        @Override
        public void setScorer(Scorer scorer) throws IOException {
            //What to do there ?
        }

        @Override
        public void collect(int doc) throws IOException {
            docs.set(base + doc);
        }

        @Override
        public void setNextReader(IndexReader indexReader, int base) throws IOException {
            this.base = base;
        }

        @Override
        public boolean acceptsDocsOutOfOrder() {
            return true;
        }
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
            // by default, Lucene only indexes the first 10,000 terms in a field
            writer.setMaxFieldLength(Integer.MAX_VALUE);
            NumericField fDocId = new NumericField(FIELD_DOC_ID, Field.Store.YES, true);
            Field fNodeId = new Field(FIELD_NODE_ID, new byte [] { 0 }, Field.Store.YES);
            for (PendingDoc pending : nodesToWrite) {
                Document doc = new Document();
                if (pending.idxConf.getBoost() > 0)
                    doc.setBoost(pending.idxConf.getBoost());
                else if (config.getBoost() > 0)
                    doc.setBoost(config.getBoost());

                // store the node id
                int nodeIdLen = pending.nodeId.size();
                byte[] data = new byte[nodeIdLen + 2];
                ByteConversion.shortToByte((short) pending.nodeId.units(), data, 0);
                pending.nodeId.serialize(data, 2);

                String contentField;
                // the text content is indexed in a field using either
                // the qname of the element or attribute or the field
                // name defined in the configuration
                if (pending.idxConf.isNamed())
                	contentField = pending.idxConf.getName();
                else
                	contentField = encodeQName(pending.qname);
                fDocId.setIntValue(currentDoc.getDocId());
                fNodeId.setValue(data);

                doc.add(fDocId);
                doc.add(fNodeId);
                doc.add(new Field(contentField, pending.text.toString(), Field.Store.NO, Field.Index.ANALYZED,
                    Field.TermVector.YES));

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
     *
     * @see http://lucene.apache.org/java/3_0_1/api/all/org/apache/lucene/index/IndexWriter.html#optimize()
     */
    public void optimize() {
        IndexWriter writer = null;
        try {
            writer = index.getWriter();
            writer.optimize(true);
        } catch (IOException e) {
            LOG.warn("An exception was caught while optimizing the lucene index: " + e.getMessage(), e);
        } finally {
            index.releaseWriter(writer);
        }
    }

    /**
     * Encode an element or attribute qname into a lucene field name using the
     * internal ids for namespace and local name.
     * 
     * @param qname
     * @return encoded qname
     */
    private String encodeQName(QName qname) {
        SymbolTable symbols = index.getBrokerPool().getSymbols();
        short namespaceId = symbols.getNSSymbol(qname.getNamespaceURI());
        short localNameId = symbols.getSymbol(qname.getLocalName());
        long nameId = qname.getNameType() | (namespaceId & 0xFFFF) << 16 | (localNameId & 0xFFFFFFFFL) << 32;
        return Long.toHexString(nameId);
    }

    /**
     * Decode the lucene field name into an element or attribute qname.
     * 
     * @param s
     * @return the qname
     */
    private QName decodeQName(String s) {
        SymbolTable symbols = index.getBrokerPool().getSymbols();
        try {
            long l = Long.parseLong(s, 16);
            short namespaceId = (short) ((l >>> 16) & 0xFFFFL);
            short localNameId = (short) ((l >>> 32) & 0xFFFFL);
            byte type = (byte) (l & 0xFFL);
            if (namespaceId < 0 || localNameId < 0)
            	return null;
            String namespaceURI = symbols.getNamespace(namespaceId);
            String localName = symbols.getName(localNameId);
            if (namespaceURI == null || localName == null)
            	return null;
            QName qname = new QName(localName, namespaceURI, "");
            qname.setNameType(type);
            return qname;
        } catch (NumberFormatException e) {
            return null;
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

        private void setScore(float score) {
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

    private static class NodeFieldSelector implements FieldSelector {

        private static final long serialVersionUID = -4899170629980829109L;

        public FieldSelectorResult accept(String fieldName) {
            if (FIELD_DOC_ID.equals(fieldName))
                return FieldSelectorResult.LOAD;
            if (FIELD_NODE_ID.equals(fieldName))
                return FieldSelectorResult.LOAD_AND_BREAK;
            return FieldSelectorResult.NO_LOAD;
        }
    }
}

