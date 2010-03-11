package org.exist.indexing.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.*;
import org.exist.collections.Collection;
import org.exist.dom.*;
import org.exist.indexing.AbstractStreamListener;
import org.exist.indexing.IndexController;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.MatchListener;
import org.exist.indexing.OrderedValuesIndex;
import org.exist.indexing.QNamedKeysIndex;
import org.exist.indexing.StreamListener;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.IndexSpec;
import org.exist.storage.NodePath;
import org.exist.storage.txn.Txn;
import org.exist.util.ByteConversion;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.Occurrences;
import org.exist.xquery.Expression;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.IntegerValue;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;

public class LuceneIndexWorker implements OrderedValuesIndex, QNamedKeysIndex {

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

    public Object configure(IndexController controller, NodeList configNodes, Map<String, String> namespaces) throws DatabaseConfigurationException {
        this.controller = controller;
        LOG.debug("Configuring lucene index");
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
        if (indexConf != null)
            config = (LuceneConfig) indexConf.getCustomIndexSpec(LuceneIndex.ID);
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
            Term dt = new Term("docId", Integer.toString(docId));
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
                Term dt = new Term("docId", Integer.toString(doc.getDocId()));
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
            Term dt = new Term("docId", Integer.toString(currentDoc.getDocId()));
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
        List<QName> qnames, String queryStr, int axis)
        throws IOException, ParseException {
        if (qnames == null || qnames.isEmpty())
            qnames = getDefinedIndexes();
        NodeSet resultSet = new NewArrayNodeSet();
        boolean returnAncestor = axis == NodeSet.ANCESTOR;
        IndexSearcher searcher = null;
        try {
            searcher = index.getSearcher();
            for (QName qname : qnames) {
                String field = encodeQName(qname);
                Analyzer analyzer = getAnalyzer(qname, context.getBroker(), docs);
                QueryParser parser = new QueryParser(field, analyzer);
                Query query = parser.parse(queryStr);
                LuceneHitCollector collector = new LuceneHitCollector();
                searcher.search(query, collector);
                processHits(collector.getDocs(), searcher, contextId, docs, contextSet, resultSet, returnAncestor, query);
            }
        } finally {
            index.releaseSearcher(searcher);
        }
        return resultSet;
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
                         List<QName> qnames, Element queryRoot, int axis)
            throws IOException, ParseException, XPathException {
        if (qnames == null || qnames.isEmpty())
            qnames = getDefinedIndexes();
        NodeSet resultSet = new NewArrayNodeSet();
        boolean returnAncestor = axis == NodeSet.ANCESTOR;
        IndexSearcher searcher = null;
        try {
            searcher = index.getSearcher();
            for (QName qname : qnames) {
                String field = encodeQName(qname);
                analyzer = getAnalyzer(qname, context.getBroker(), docs);
                Query query = queryTranslator.parse(field, queryRoot, analyzer);
                LuceneHitCollector collector = new LuceneHitCollector();
                searcher.search(query, collector);
                processHits(collector.getDocs(), searcher, contextId, docs, contextSet, resultSet, returnAncestor, query);
            }
        } finally {
            index.releaseSearcher(searcher);
        }
        return resultSet;
    }

    /**
     * Process the query results collected from the Lucene index and
     * map them to the corresponding XML nodes in eXist.
     */
    private void processHits(List<ScoreDoc> hits, IndexSearcher searcher, int contextId, DocumentSet docs, NodeSet contextSet,
                             NodeSet resultSet, boolean returnAncestor, Query query) {
        for (ScoreDoc scoreDoc : hits) {
            try {
                Document doc = searcher.doc(scoreDoc.doc, NODE_FIELD_SELECTOR);
                Field fDocId = doc.getField("docId");
                int docId = Integer.parseInt(fDocId.stringValue());
                DocumentImpl storedDocument = docs.getDoc(docId);
                if (storedDocument == null)
                    return;
                NodeId nodeId = readNodeId(doc);
                NodeProxy storedNode = new NodeProxy(storedDocument, nodeId);
                // if a context set is specified, we can directly check if the
                // matching node is a descendant of one of the nodes
                // in the context set.
                if (contextSet != null) {
                    int sizeHint = contextSet.getSizeHint(storedDocument);
                    if (returnAncestor) {
                        NodeProxy parentNode = contextSet.parentWithChild(storedNode, false, true, NodeProxy.UNKNOWN_NODE_LEVEL);
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

        private LuceneHitCollector() throws IOException {
        }

        public List<ScoreDoc> getDocs() {
            return docs;
        }
        
        public void setScorer(Scorer scorer) throws IOException {
            this.scorer = scorer;
        }

        public void setNextReader(IndexReader indexReader, int docBase) throws IOException {
            this.docBase = docBase;
        }

        public boolean acceptsDocsOutOfOrder() {
            return false;
        }

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
        byte[] temp;
        Field fNodeId = doc.getField("nodeId");
        temp = fNodeId.binaryValue();
        int units = ByteConversion.byteToShort(temp, 0);
        NodeId nodeId = index.getBrokerPool().getNodeFactory()
                .createFromData(units, temp, 2);
        return nodeId;
    }

    /**
     * Check index configurations for all collection in the given DocumentSet and return
     * a list of QNames, which have indexes defined on them.
     *
     * @return List of QName objects on which indexes are defined
     */
    private List<QName> getDefinedIndexes() {
        List<QName> indexes = new ArrayList<QName>(20);
        IndexReader reader = null;
        try {
            reader = index.getReader();
            java.util.Collection<?> fields = reader.getFieldNames(IndexReader.FieldOption.INDEXED);
            for (Iterator<?> i = fields.iterator(); i.hasNext(); ) {
                String field = (String) i.next();
                if (!"docId".equals(field))
                    indexes.add(decodeQName(field));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            index.releaseReader(reader);
        }
        return indexes;
    }

    private Analyzer getAnalyzer(QName qname, DBBroker broker, DocumentSet docs) {
        for (Iterator<Collection> i = docs.getCollectionIterator(); i.hasNext(); ) {
            Collection collection = i.next();
            IndexSpec idxConf = collection.getIndexConfiguration(broker);
            if (idxConf != null) {
                LuceneConfig config = (LuceneConfig) idxConf.getCustomIndexSpec(LuceneIndex.ID);
                if (config != null) {
                    Analyzer analyzer = config.getAnalyzer(qname);
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

    public Occurrences[] scanIndex(XQueryContext context, DocumentSet docs, NodeSet contextSet, Map hints) {
        List<QName> qnames = hints == null ? null : (List<QName>)hints.get(QNAMES_KEY);
        if (qnames == null || qnames.isEmpty())
            qnames = getDefinedIndexes();
        //Expects a StringValue
    	Object start = hints == null ? null : hints.get(START_VALUE);
        Object end = hints == null ? null : hints.get(END_VALUE);
        IntegerValue max = (IntegerValue) hints.get(VALUE_COUNT);

        TreeMap<Term, Occurrences> map = new TreeMap<Term, Occurrences>();
        IndexReader reader = null;
        try {
            reader = index.getReader();
            for (QName qname : qnames) {
                String field = encodeQName(qname);
                TermEnum terms;
                if (start == null)
                    terms = reader.terms(new Term(field, ""));
                else
                    terms = reader.terms(new Term(field, start.toString()));
                if (terms == null)
                    continue;
                Term term;
                do {
                    term = terms.term();
                    if (term != null && term.field().equals(field)) {
                        boolean include = true;
                        if (end != null) {
                            if (term.text().compareTo(start.toString()) > 0)
                                include = false;
                        } else if (start != null && !term.text().startsWith(start.toString()))
                            include = false;
                        if (include) {
                            TermDocs docsEnum = reader.termDocs(term);
                            while (docsEnum.next()) {
                                if (reader.isDeleted(docsEnum.doc()))
                                    continue;
                                Document doc = reader.document(docsEnum.doc());
                                Field fDocId = doc.getField("docId");
                                int docId = Integer.parseInt(fDocId.stringValue());
                                DocumentImpl storedDocument = docs.getDoc(docId);
                                if (storedDocument == null)
                                    continue;

                                if (contextSet != null) {
                                    NodeId nodeId = readNodeId(doc);
                                    NodeProxy parentNode = contextSet.parentWithChild(storedDocument, nodeId, false, true);
                                    include = (parentNode != null);
                                }
                                if (include) {
                                    Occurrences oc = map.get(term);
                                    if (oc == null) {
                                        oc = new Occurrences(term.text());
                                        map.put(term, oc);
                                    }
                                    oc.addDocument(storedDocument);
                                    oc.addOccurrences(docsEnum.freq());
                                }
                            }
                            docsEnum.close();
                        }
                    }
                    if (max != null && map.size() >= max.getValue())
                        break;
                } while (terms.next());
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
    protected void indexText(NodeId nodeId, QName qname, NodePath path, CharSequence content, float boost) {
        if (path.length() == 0)
            throw new RuntimeException();
        PendingDoc pending = new PendingDoc(nodeId, content, path, qname, boost);
        nodesToWrite.add(pending);
        cachedNodesSize += content.length();
        if (cachedNodesSize > maxCachedNodesSize)
            write();
    }

    private class PendingDoc {
        NodeId nodeId;
        CharSequence text;
        QName qname;
        Analyzer analyzer;
        float boost;

        private PendingDoc(NodeId nodeId, CharSequence text, NodePath path, QName qname, float boost) {
            this.nodeId = nodeId;
            this.text = text;
            this.qname = qname;
            this.analyzer = config.getAnalyzer(path);
            this.boost = boost;
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
            for (PendingDoc pending : nodesToWrite) {
                Document doc = new Document();
                if (pending.boost > 0)
                    doc.setBoost(pending.boost);
                else if (config.getBoost() > 0)
                    doc.setBoost(config.getBoost());

                // store the node id
                int nodeIdLen = pending.nodeId.size();
                byte[] data = new byte[nodeIdLen + 2];
                ByteConversion.shortToByte((short) pending.nodeId.units(), data, 0);
                pending.nodeId.serialize(data, 2);

                String contentField = encodeQName(pending.qname);

                doc.add(new Field("docId", Integer.toString(currentDoc.getDocId()),
                        Field.Store.YES,  Field.Index.NOT_ANALYZED));
                doc.add(new Field("nodeId", data, Field.Store.YES));
                doc.add(new Field(contentField, pending.text.toString(), Field.Store.NO, Field.Index.ANALYZED));

                if (pending.analyzer == null)
                    writer.addDocument(doc);
                else
                    writer.addDocument(doc, pending.analyzer);
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

    private String encodeQName(QName qname) {
        SymbolTable symbols = index.getBrokerPool().getSymbols();
        short namespaceId = symbols.getNSSymbol(qname.getNamespaceURI());
        short localNameId = symbols.getSymbol(qname.getLocalName());
        long nameId = qname.getNameType() | (((int) namespaceId) & 0xFFFF) << 16 | (((long) localNameId) & 0xFFFFFFFFL) << 32;
        return Long.toHexString(nameId);
    }

    private QName decodeQName(String s) {
        SymbolTable symbols = index.getBrokerPool().getSymbols();
        long l = Long.parseLong(s, 16);
        short namespaceId = (short) ((l >>> 16) & 0xFFFFL);
        short localNameId = (short) ((l >>> 32) & 0xFFFFL);
        byte type = (byte) (l & 0xFFL);
        String namespaceURI = symbols.getNamespace(namespaceId);
        String localName = symbols.getName(localNameId);
        QName qname = new QName(localName, namespaceURI, "");
        qname.setNameType(type);
        return qname;
    }

    private class LuceneStreamListener extends AbstractStreamListener {

        public void startElement(Txn transaction, ElementImpl element, NodePath path) {
            if (mode == STORE && config != null) {
                if (contentStack != null && !contentStack.isEmpty()) {
                    for (TextExtractor extractor : contentStack) {
                        extractor.startElement(element.getQName());
                    }
                }
                LuceneIndexConfig idxConf = config.getConfig(path);
                if (idxConf != null) {
                    if (contentStack == null) contentStack = new Stack<TextExtractor>();
                    TextExtractor extractor = new DefaultTextExtractor();
                    extractor.configure(config, idxConf);
                    contentStack.push(extractor);
                }
            }
            super.startElement(transaction, element, path);
        }

        public void endElement(Txn transaction, ElementImpl element, NodePath path) {
            if (config != null) {
                if (mode == STORE && contentStack != null && !contentStack.isEmpty()) {
                    for (TextExtractor extractor : contentStack) {
                        extractor.endElement(element.getQName());
                    }
                }
                LuceneIndexConfig idxConf = config.getConfig(path);
                if (mode != REMOVE_ALL_NODES && idxConf != null) {
                    if (mode == REMOVE_SOME_NODES) {
                        nodesToRemove.add(element.getNodeId());
                    } else {
                        TextExtractor extractor = contentStack.pop();
                        indexText(element.getNodeId(), element.getQName(), path,
                                extractor.getText(), idxConf.getBoost());
                    }
                }
            }
            super.endElement(transaction, element, path);
        }

        public void attribute(Txn transaction, AttrImpl attrib, NodePath path) {
            path.addComponent(attrib.getQName());
            if (mode != REMOVE_ALL_NODES && config != null && config.matches(path)) {
                if (mode == REMOVE_SOME_NODES) {
                    nodesToRemove.add(attrib.getNodeId());
                } else {
                    indexText(attrib.getNodeId(), attrib.getQName(), path, attrib.getValue(),
                            config.getBoost());
                }
            }
            path.removeLastComponent();
            super.attribute(transaction, attrib, path);
        }

        public void characters(Txn transaction, CharacterDataImpl text, NodePath path) {
            if (contentStack != null && !contentStack.isEmpty()) {
                for (TextExtractor extractor : contentStack) {
                    extractor.characters(text.getXMLString());
                }
            }
            super.characters(transaction, text, path);
        }

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

        public Match createInstance(int contextId, NodeId nodeId, String matchTerm) {
            return null;
        }

        public Match createInstance(int contextId, NodeId nodeId, Query query) {
            return new LuceneMatch(contextId, nodeId, query);
        }

        public Match newCopy() {
            return new LuceneMatch(this);
        }

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

        public boolean equals(Object other) {
            if(!(other instanceof LuceneMatch))
                return false;
            LuceneMatch o = (LuceneMatch) other;
            return (nodeId == o.nodeId || nodeId.equals(o.nodeId))  &&
                query == ((LuceneMatch)other).query;
        }

        public boolean matchEquals(Match other) {
            return equals(other);
        }
    }

    private static class NodeFieldSelector implements FieldSelector {

		private static final long serialVersionUID = -4899170629980829109L;

		public FieldSelectorResult accept(String fieldName) {
            if ("nodeId".equals(fieldName) || "docId".equals(fieldName))
                return FieldSelectorResult.LOAD;
            return FieldSelectorResult.NO_LOAD;
        }
    }
}

