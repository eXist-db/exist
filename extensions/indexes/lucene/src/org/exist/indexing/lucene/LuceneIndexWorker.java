package org.exist.indexing.lucene;

import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
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
import org.exist.indexing.AbstractStreamListener;
import org.exist.indexing.IndexController;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.MatchListener;
import org.exist.indexing.StreamListener;
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

public class LuceneIndexWorker implements IndexWorker {

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

    public void removeCollection(Collection collection, DBBroker broker) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public NodeSet query(int contextId, DocumentSet docs, NodeSet contextSet, String queryStr, int axis) throws IOException, ParseException {
        NodeSet resultSet = new NewArrayNodeSet();
        boolean returnAncestor = axis == NodeSet.ANCESTOR;
        IndexSearcher searcher = null;
        try {
            searcher = index.getSearcher();
            QueryParser parser = new QueryParser("contents", index.getAnalyzer());
            Query query = parser.parse(queryStr);
            Hits hits = searcher.search(query);
            if (LOG.isDebugEnabled())
                LOG.debug("Found " + hits.length() + " hits.");
            for (int i = 0; i < hits.length(); i++) {
                Document doc = hits.doc(i);
                Field fDocId = doc.getField("docId");
                byte[] temp = fDocId.binaryValue();
                int docId = ByteConversion.byteToInt(temp, 0);
                DocumentImpl storedDocument = docs.getDoc(docId);
                if (storedDocument == null)
                    continue;
                Field fNodeId = doc.getField("nodeId");
                temp = fNodeId.binaryValue();
                int units = ByteConversion.byteToShort(temp, 0);
                NodeId nodeId = index.getBrokerPool().getNodeFactory()
                        .createFromData(units, temp, 2);
                NodeProxy storedNode = new NodeProxy(storedDocument, nodeId);
                // if a context set is specified, we can directly check if the
                // matching node is a descendant of one of the nodes
                // in the context set.
                if (contextSet != null) {
                    int sizeHint = contextSet.getSizeHint(storedDocument);
                    if (returnAncestor) {
                        NodeProxy parentNode = contextSet.parentWithChild(storedNode, false, true, NodeProxy.UNKNOWN_NODE_LEVEL);
                        if (parentNode != null) {
                            resultSet.add(parentNode, sizeHint);
                            if (Expression.NO_CONTEXT_ID != contextId) {
                                parentNode.deepCopyContext(storedNode, contextId);
                            } else
                                parentNode.copyContext(storedNode);
                        }
                    } else {
                        resultSet.add(storedNode, sizeHint);
                    }
                }
            }
        } finally {
            index.releaseSearcher(searcher);
        }
        return resultSet;
    }

    public boolean checkIndex(DBBroker broker) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Occurrences[] scanIndex(XQueryContext context, DocumentSet docs, NodeSet contextSet, Map hints) {
        return new Occurrences[0];  //To change body of implemented methods use File | Settings | File Templates.
    }

    protected void indexText(NodeId nodeId, XMLString content) {
        if (writer == null)
            return;
        Document doc = new Document();
        byte[] docId = new byte[4];
        ByteConversion.intToByte(currentDoc.getDocId(), docId, 0);

        int nodeIdLen = nodeId.size();
        byte[] data = new byte[nodeIdLen + 2];
        ByteConversion.shortToByte((short) nodeId.units(), data, 0);
        nodeId.serialize(data, 2);

        doc.add(new Field("docId", docId, Field.Store.YES));
        doc.add(new Field("nodeId", data, Field.Store.YES));
        doc.add(new Field("contents", content.toString(), Field.Store.NO, Field.Index.TOKENIZED));

        try {
            writer.addDocument(doc);
        } catch (IOException e) {
            LOG.warn("An exception was caught while indexing document: " + e.getMessage(), e);
        }
    }

    private class LuceneStreamListener extends AbstractStreamListener {

        public void startElement(Txn transaction, ElementImpl element, NodePath path) {
            if (config != null && config.get(element.getQName()) != null) {
                if (contentStack == null) contentStack = new Stack();
                XMLString contentBuf = new XMLString();
                contentStack.push(contentBuf);
            }
            super.startElement(transaction, element, path);
        }

        public void endElement(Txn transaction, ElementImpl element, NodePath path) {
            if (config != null && config.get(element.getQName()) != null) {
                XMLString content = (XMLString) contentStack.pop();
                indexText(element.getNodeId(), content);
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
}
