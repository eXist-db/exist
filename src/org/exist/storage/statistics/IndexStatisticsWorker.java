package org.exist.storage.statistics;

import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ElementImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.StoredNode;
import org.exist.indexing.AbstractStreamListener;
import org.exist.indexing.IndexController;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.MatchListener;
import org.exist.indexing.StreamListener;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.NodePath;
import org.exist.storage.btree.BTreeCallback;
import org.exist.storage.btree.Value;
import org.exist.storage.index.CollectionStore;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.txn.Txn;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.Occurrences;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.XQueryContext;
import org.w3c.dom.NodeList;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.util.Map;
import java.util.Stack;

/**
 */
public class IndexStatisticsWorker implements IndexWorker {

    private IndexStatistics index;

    private DataGuide perDocGuide = null;

    private int mode = 0;
    private DocumentImpl currentDoc = null;

    private StatisticsListener listener = new StatisticsListener();
    
    public IndexStatisticsWorker(IndexStatistics index) {
        this.index = index;
    }

    public String getIndexId() {
        return index.getIndexId();
    }

    public String getIndexName() {
        return index.getIndexName();
    }

    public Object configure(IndexController controller, NodeList configNodes, Map namespaces) throws DatabaseConfigurationException {
        return null;
    }

    public void setDocument(DocumentImpl doc) {
        setDocument(doc, StreamListener.UNKNOWN);
    }

    public void setDocument(DocumentImpl doc, int mode) {
        perDocGuide = new DataGuide();
        this.currentDoc = doc;
        this.mode = mode;
    }

    public void setMode(int mode) {
        perDocGuide = new DataGuide();
        this.mode = mode;
    }

    public DocumentImpl getDocument() {
        return currentDoc;
    }

    public int getMode() {
        return mode;
    }

    public StoredNode getReindexRoot(StoredNode node, NodePath path, boolean includeSelf) {
        return null;
    }

    public StreamListener getListener() {
        if (mode == StreamListener.STORE)
            return listener;
        return null;
    }

    public MatchListener getMatchListener(DBBroker broker, NodeProxy proxy) {
        return null;
    }

    public void flush() {
        if (perDocGuide != null) {
            index.mergeStats(perDocGuide);
//            System.out.println(index.toString());
        }
        perDocGuide = new DataGuide();
    }

    public void updateIndex(DBBroker broker) {
        perDocGuide = new DataGuide();
        DocumentCallback cb = new DocumentCallback(broker);
        broker.getResourcesFailsafe(cb, false);
        index.updateStats(perDocGuide);
    }

    private void updateDocument(DBBroker broker, DocumentImpl doc) {
        ElementImpl root = (ElementImpl) doc.getDocumentElement();
        try {
            NodePath path = new NodePath();
            Stack stack = new Stack();
            QName qname;
            EmbeddedXMLStreamReader reader = broker.getXMLStreamReader(root, false);
            while (reader.hasNext()) {
                int status = reader.next();
                switch (status) {
                    case XMLStreamReader.START_ELEMENT:
                        for (int i = 0; i < stack.size(); i++) {
                            NodeStats next = (NodeStats) stack.elementAt(i);
                            next.incDepth();
                        }
                        qname = reader.getQName();
                        path.addComponent(qname);
                        NodeStats nodeStats = perDocGuide.add(path);
                        stack.push(nodeStats);
                        break;
                    case XMLStreamReader.END_ELEMENT:
                        path.removeLastComponent();
                        NodeStats stats = (NodeStats) stack.pop();
                        stats.updateMaxDepth();
                        break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (XMLStreamException e) {
            e.printStackTrace();
        }
    }

    public void removeCollection(Collection collection, DBBroker broker) {
    }

    public boolean checkIndex(DBBroker broker) {
        return false;
    }

    public Occurrences[] scanIndex(XQueryContext context, DocumentSet docs, NodeSet contextSet, Map hints) {
        return new Occurrences[0];
    }

    private class StatisticsListener extends AbstractStreamListener {

        private Stack stack = new Stack();
        
        public void startElement(Txn transaction, ElementImpl element, NodePath path) {
            super.startElement(transaction, element, path);
            if (perDocGuide != null) {
                for (int i = 0; i < stack.size(); i++) {
                    NodeStats next = (NodeStats) stack.elementAt(i);
                    next.incDepth();
                }
                NodeStats nodeStats = perDocGuide.add(path);
                stack.push(nodeStats);
            }
        }

        public void endElement(Txn transaction, ElementImpl element, NodePath path) {
            super.endElement(transaction, element, path);
            if (perDocGuide != null) {
                NodeStats stats = (NodeStats) stack.pop();
                stats.updateMaxDepth();
            }
        }

        public IndexWorker getWorker() {
            return IndexStatisticsWorker.this;
        }
    }

    private class DocumentCallback implements BTreeCallback {

        private DBBroker broker;

        private DocumentCallback(DBBroker broker) {
            this.broker = broker;
        }

        public boolean indexInfo(Value key, long pointer) throws TerminatedException {
            CollectionStore store = (CollectionStore) ((NativeBroker)broker).getStorage(NativeBroker.COLLECTIONS_DBX_ID);
            try {
                byte type = key.data()[key.start() + Collection.LENGTH_COLLECTION_ID + DocumentImpl.LENGTH_DOCUMENT_TYPE];
                VariableByteInput istream = store.getAsStream(pointer);
                DocumentImpl doc = null;
                if (type == DocumentImpl.XML_FILE) {
                    doc = new DocumentImpl(broker.getBrokerPool());
                    doc.read(istream);
                    updateDocument(broker, doc);
                }
            } catch (Exception e) {
                IndexStatistics.LOG.warn("An error occurred while regenerating index statistics: " + e.getMessage(), e);
            }
            return true;
        }
    }
}