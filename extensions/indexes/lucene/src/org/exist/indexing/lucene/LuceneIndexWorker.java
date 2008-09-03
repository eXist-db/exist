package org.exist.indexing.lucene;

import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Hits;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ElementImpl;
import org.exist.dom.NewArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.StoredNode;
import org.exist.dom.TextImpl;
import org.exist.dom.QName;
import org.exist.dom.SymbolTable;
import org.exist.dom.Match;
import org.exist.indexing.AbstractStreamListener;
import org.exist.indexing.IndexController;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.MatchListener;
import org.exist.indexing.StreamListener;
import org.exist.indexing.OrderedValuesIndex;
import org.exist.indexing.QNamedKeysIndex;
import org.exist.indexing.ngram.NGramIndex;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.IndexSpec;
import org.exist.storage.NodePath;
import org.exist.storage.txn.Txn;
import org.exist.util.ByteConversion;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.Occurrences;
import org.exist.util.XMLString;
import org.exist.xquery.Expression;
import org.exist.xquery.XQueryContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

public class LuceneIndexWorker implements OrderedValuesIndex, QNamedKeysIndex {

    private static final Logger LOG = Logger.getLogger(LuceneIndexWorker.class);

    private final static String INDEX_ELEMENT = "text";
    private static final String QNAME_ATTR = "qname";
    
    private LuceneIndex index;
    private IndexController controller;

    private DocumentImpl currentDoc = null;
    private int mode = 0;
    
    private Map config;
    private Stack contentStack = null;

    private IndexWriter writer = null;
    
    public LuceneIndexWorker(LuceneIndex parent) {
        this.index = parent;
    }

    public String getIndexId() {
        return LuceneIndex.ID;
    }

    public String getIndexName() {
        return index.getIndexName();
    }

    public Object configure(IndexController controller, NodeList configNodes, Map namespaces) throws DatabaseConfigurationException {
        this.controller = controller;
        LOG.debug("Configuring lucene index");
        // We use a map to store the QNames to be indexed
        Map map = new TreeMap();
        Node node;
        for(int i = 0; i < configNodes.getLength(); i++) {
            node = configNodes.item(i);
            if(node.getNodeType() == Node.ELEMENT_NODE &&
                    INDEX_ELEMENT.equals(node.getLocalName())) {
                String qname = ((Element)node).getAttribute(QNAME_ATTR);
                if (qname == null || qname.length() == 0)
                    throw new DatabaseConfigurationException("Configuration error: element " + node.getNodeName() +
	                		" must have an attribute " + QNAME_ATTR);
                LuceneIndexConfig config = new LuceneIndexConfig(namespaces, qname);
                map.put(config.getQName(), config);
            }
        }
        return map;
    }

    public void flush() {
        try {
            switch (mode) {
                case StreamListener.STORE:
                    if (writer != null) {
                        writer.flush();
                        index.releaseWriter(writer);
                    }
                    writer = null;
                    break;
                case StreamListener.REMOVE_ALL_NODES:
                    removeDocument(currentDoc.getDocId());
                    break;
            }
        } catch (IOException e) {
            LOG.warn("Caught an exception while flushing lucene index: " + e.getMessage(), e);
        }
    }

    public void setDocument(DocumentImpl document) {
        setDocument(document, StreamListener.UNKNOWN);
    }

    public void setDocument(DocumentImpl document, int newMode) {
        currentDoc = document;
        //config = null;
        contentStack = null;
        IndexSpec indexConf = document.getCollection().getIndexConfiguration(document.getBroker());
        if (indexConf != null)
            config = (Map) indexConf.getCustomIndexSpec(LuceneIndex.ID);
        mode = newMode;
    }

    public void setMode(int mode) {
        this.mode = mode;
        try {
            switch (mode) {
                case StreamListener.STORE:
                    writer = index.getWriter();
                    break;
            }
        } catch (IOException e) {
            LOG.warn("Caught exception while preparing lucene index: " + e.getMessage(), e);
        }
    }

    public DocumentImpl getDocument() {
        return currentDoc;
    }

    public int getMode() {
        return this.mode;
    }

    public StoredNode getReindexRoot(StoredNode node, NodePath path, boolean includeSelf) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    private StreamListener listener = new LuceneStreamListener();

    public StreamListener getListener() {
        return listener;
    }

    public MatchListener getMatchListener(DBBroker broker, NodeProxy proxy) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    protected void removeDocument(int docId) {
        IndexReader reader = null;
        try {
            reader = index.getReader();
            Term dt = new Term("docId", Integer.toString(docId));
            reader.deleteDocuments(dt);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            index.releaseReader(reader);
        }
    }

    public void removeCollection(Collection collection, DBBroker broker) {
        if (LOG.isDebugEnabled())
            LOG.debug("Removing collection " + collection.getURI());
        IndexReader reader = null;
        try {
            reader = index.getReader();
            for (Iterator i = collection.iterator(broker); i.hasNext(); ) {
                DocumentImpl doc = (DocumentImpl) i.next();
                Term dt = new Term("docId", Integer.toString(doc.getDocId()));
                reader.deleteDocuments(dt);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            index.releaseReader(reader);
        }
        if (LOG.isDebugEnabled())
            LOG.debug("Collection removed.");
    }

    public NodeSet query(XQueryContext context, int contextId, DocumentSet docs, NodeSet contextSet,
        List qnames, String queryStr, int axis)
        throws IOException, ParseException {
        if (qnames == null || qnames.isEmpty())
            qnames = getDefinedIndexes(context.getBroker(), docs);
        NodeSet resultSet = new NewArrayNodeSet();
        boolean returnAncestor = axis == NodeSet.ANCESTOR;
        IndexSearcher searcher = null;
        try {
            searcher = index.getSearcher();
            for (int i = 0; i < qnames.size(); i++) {
                QName qname = (QName) qnames.get(i);
                String field = encodeQName(qname);
                QueryParser parser = new QueryParser(field, index.getAnalyzer());
                Query query = parser.parse(queryStr);
                Hits hits = searcher.search(query);
                if (LOG.isDebugEnabled())
                    LOG.debug("Found " + hits.length());
                for (int j = 0; j < hits.length(); j++) {
                    Document doc = hits.doc(j);
                    Field fDocId = doc.getField("docId");
                    int docId = Integer.parseInt(fDocId.stringValue());
                    DocumentImpl storedDocument = docs.getDoc(docId);
                    if (storedDocument == null)
                        continue;
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
                                match.setScore(hits.score(j));
                                parentNode.addMatch(match);
                                resultSet.add(parentNode, sizeHint);
                                if (Expression.NO_CONTEXT_ID != contextId) {
                                    parentNode.deepCopyContext(storedNode, contextId);
                                } else
                                    parentNode.copyContext(storedNode);
                            }
                        } else {
                            LuceneMatch match = new LuceneMatch(contextId, nodeId, query);
                            match.setScore(hits.score(j));
                            storedNode.addMatch(match);
                            resultSet.add(storedNode, sizeHint);
                        }
                    } else {
                        LuceneMatch match = new LuceneMatch(contextId, nodeId, query);
                        match.setScore(hits.score(j));
                        storedNode.addMatch(match);
                        resultSet.add(storedNode);
                    }
                }
            }
        } finally {
            index.releaseSearcher(searcher);
        }
        return resultSet;
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
     * @param broker
     * @param docs
     * @return
     */
    private List getDefinedIndexes(DBBroker broker, DocumentSet docs) {
        List indexes = new ArrayList(20);
        for (Iterator i = docs.getCollectionIterator(); i.hasNext(); ) {
            Collection collection = (Collection) i.next();
            IndexSpec idxConf = collection.getIndexConfiguration(broker);
            if (idxConf != null) {
                Map config = (Map) idxConf.getCustomIndexSpec(LuceneIndex.ID);
                if (config != null) {
                    for (Iterator ci = config.keySet().iterator(); ci.hasNext();) {
                        QName qn = (QName) ci.next();
                        indexes.add(qn);
                    }
                }
            }
        }
        return indexes;
    }

    public boolean checkIndex(DBBroker broker) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Occurrences[] scanIndex(XQueryContext context, DocumentSet docs, NodeSet contextSet, Map hints) {
        List qnames = hints == null ? null : (List)hints.get(QNAMES_KEY);
        if (qnames == null || qnames.isEmpty())
            qnames = getDefinedIndexes(context.getBroker(), docs);
        //Expects a StringValue
    	Object start = hints == null ? null : hints.get(START_VALUE);
        Object end = hints == null ? null : hints.get(END_VALUE);
        TreeMap map = new TreeMap();
        IndexReader reader = null;
        try {
            reader = index.getReader();
            for (int i = 0; i < qnames.size(); i++) {
                QName qname = (QName) qnames.get(i);
                String field = encodeQName(qname);
                TermEnum terms;
                if (start == null)
                    terms = reader.terms();
                else
                    terms = reader.terms(new Term(field, start.toString()));
                if (terms == null)
                    continue;
                Term term;
                while((term = terms.term()) != null) {
                    if (term.field().equals(field)) {
                        if (end != null) {
                            if (term.text().compareTo(start.toString()) > 0)
                                break;
                        } else if (!term.text().startsWith(start.toString()))
                            break;
                        TermDocs docsEnum = reader.termDocs(term);
                        while (docsEnum.next()) {
                            Document doc = reader.document(docsEnum.doc());
                            Field fDocId = doc.getField("docId");
                            int docId = Integer.parseInt(fDocId.stringValue());
                            DocumentImpl storedDocument = docs.getDoc(docId);
                            if (storedDocument == null)
                                continue;

                            boolean include = true;
                            if (contextSet != null) {
                                NodeId nodeId = readNodeId(doc);
                                NodeProxy parentNode = contextSet.parentWithChild(storedDocument, nodeId, false, true);
                                include = (parentNode != null);
                            }
                            if (include) {
                                Occurrences oc = (Occurrences) map.get(term);
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
                    terms.next();
                }
                terms.close();
            }
        } catch (IOException e) {
            LOG.warn("Error while scanning lucene index entries: " + e.getMessage(), e);
        } finally {
            index.releaseReader(reader);
        }
        Occurrences[] occur = new Occurrences[map.size()];
        return (Occurrences[]) map.values().toArray(occur);
    }

    protected void indexText(NodeId nodeId, QName qname, XMLString content) {
        if (writer == null)
            return;
        Document doc = new Document();

        // store the node id
        int nodeIdLen = nodeId.size();
        byte[] data = new byte[nodeIdLen + 2];
        ByteConversion.shortToByte((short) nodeId.units(), data, 0);
        nodeId.serialize(data, 2);

        String contentField = encodeQName(qname);

        doc.add(new Field("docId", Integer.toString(currentDoc.getDocId()),
                Field.Store.COMPRESS,  Field.Index.UN_TOKENIZED));
        doc.add(new Field("nodeId", data, Field.Store.YES));
        doc.add(new Field(contentField, content.toString(), Field.Store.NO, Field.Index.TOKENIZED));

        try {
            writer.addDocument(doc);
        } catch (IOException e) {
            LOG.warn("An exception was caught while indexing document: " + e.getMessage(), e);
        }
    }

    private String encodeQName(QName qname) {
        SymbolTable symbols = index.getBrokerPool().getSymbols();
        short namespaceId = symbols.getNSSymbol(qname.getNamespaceURI());
        short localNameId = symbols.getSymbol(qname.getLocalName());
        long nameId = qname.getNameType() | (((int) namespaceId) & 0xFFFF) << 16 | (((long) localNameId) & 0xFFFFFFFFL) << 32;
        return Long.toHexString(nameId);
    }

    private class LuceneStreamListener extends AbstractStreamListener {

        public void startElement(Txn transaction, ElementImpl element, NodePath path) {
            if (mode != REMOVE_ALL_NODES && config != null && config.get(element.getQName()) != null) {
                if (contentStack == null) contentStack = new Stack();
                XMLString contentBuf = new XMLString();
                contentStack.push(contentBuf);
            }
            super.startElement(transaction, element, path);
        }

        public void endElement(Txn transaction, ElementImpl element, NodePath path) {
            if (mode != REMOVE_ALL_NODES && config != null && config.get(element.getQName()) != null) {
                XMLString content = (XMLString) contentStack.pop();
                indexText(element.getNodeId(), element.getQName(), content);
            }
            super.endElement(transaction, element, path);
        }

        public void characters(Txn transaction, TextImpl text, NodePath path) {
            if (contentStack != null && !contentStack.isEmpty()) {
                for (int i = 0; i < contentStack.size(); i++) {
                    XMLString next = (XMLString) contentStack.get(i);
                    next.append(text.getXMLString());
                }
            }
            super.characters(transaction, text, path);
        }

        public IndexWorker getWorker() {
            return LuceneIndexWorker.this;
        }
    }

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
    }
}

