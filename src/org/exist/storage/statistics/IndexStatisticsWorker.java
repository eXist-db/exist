package org.exist.storage.statistics;

import org.exist.collections.Collection;
import org.exist.dom.*;
import org.exist.indexing.*;
import org.exist.storage.DBBroker;
import org.exist.storage.NodePath;
import org.exist.storage.txn.Txn;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.Occurrences;
import org.exist.xquery.XQueryContext;
import org.w3c.dom.NodeList;

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

    public MatchListener getMatchListener(NodeProxy proxy) {
        return null;
    }

    public void flush() {
        if (perDocGuide != null) {
            index.mergeStats(perDocGuide);
//            System.out.println(index.toString());
        }
        perDocGuide = new DataGuide();
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
}