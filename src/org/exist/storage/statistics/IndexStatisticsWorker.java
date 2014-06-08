/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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
 */
package org.exist.storage.statistics;

import org.exist.collections.Collection;
import org.exist.dom.BinaryDocument;
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
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.QueryRewriter;
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

    @Override
    public QueryRewriter getQueryRewriter(XQueryContext context) {
        return null;
    }

    public Object configure(IndexController controller, NodeList configNodes, Map<String, String> namespaces) throws DatabaseConfigurationException {
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

    public StoredNode getReindexRoot(StoredNode node, NodePath path, boolean insert, boolean includeSelf) {
        return null;
    }

    public StreamListener getListener() {
        if (mode == StreamListener.STORE)
            {return listener;}
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
        final DocumentCallback cb = new DocumentCallback(broker);
        try {
            broker.getResourcesFailsafe(cb, false);
        } catch (final TerminatedException e) {
            // thrown when the db shuts down. ignore.
        }
        index.updateStats(perDocGuide);
    }

    private void updateDocument(DBBroker broker, DocumentImpl doc) {
        final ElementImpl root = (ElementImpl) doc.getDocumentElement();
        try {
            final NodePath path = new NodePath();
            final Stack<NodeStats> stack = new Stack<NodeStats>();
            QName qname;
            final EmbeddedXMLStreamReader reader = broker.getXMLStreamReader(root, false);
            while (reader.hasNext()) {
                final int status = reader.next();
                switch (status) {
                    case XMLStreamReader.START_ELEMENT:
                        for (int i = 0; i < stack.size(); i++) {
                            final NodeStats next = stack.elementAt(i);
                            next.incDepth();
                        }
                        qname = reader.getQName();
                        path.addComponent(qname);
                        final NodeStats nodeStats = perDocGuide.add(path);
                        stack.push(nodeStats);
                        break;
                    case XMLStreamReader.END_ELEMENT:
                        path.removeLastComponent();
                        final NodeStats stats = stack.pop();
                        stats.updateMaxDepth();
                        break;
                }
            }
        } catch (final IOException e) {
            e.printStackTrace();
        } catch (final XMLStreamException e) {
            e.printStackTrace();
        }
    }

    public void removeCollection(Collection collection, DBBroker broker, boolean reindex) {
    }

    public boolean checkIndex(DBBroker broker) {
        return false;
    }

    public Occurrences[] scanIndex(XQueryContext context, DocumentSet docs, NodeSet contextSet, Map<?,?> hints) {
        return new Occurrences[0];
    }

    private class StatisticsListener extends AbstractStreamListener {

        private Stack<NodeStats> stack = new Stack<NodeStats>();
        
        public void startElement(Txn transaction, ElementImpl element, NodePath path) {
            super.startElement(transaction, element, path);
            if (perDocGuide != null) {
                for (int i = 0; i < stack.size(); i++) {
                    final NodeStats next = stack.elementAt(i);
                    next.incDepth();
                }
                final NodeStats nodeStats = perDocGuide.add(path);
                stack.push(nodeStats);
            }
        }

        public void endElement(Txn transaction, ElementImpl element, NodePath path) {
            super.endElement(transaction, element, path);
            if (perDocGuide != null) {
                final NodeStats stats = (NodeStats) stack.pop();
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
            final CollectionStore store = (CollectionStore) ((NativeBroker)broker).getStorage(NativeBroker.COLLECTIONS_DBX_ID);
            try {
                final byte type = key.data()[key.start() + Collection.LENGTH_COLLECTION_ID + DocumentImpl.LENGTH_DOCUMENT_TYPE];
                final VariableByteInput istream = store.getAsStream(pointer);
                DocumentImpl doc = null;
                if (type == DocumentImpl.XML_FILE) {
                    doc = new DocumentImpl(broker.getBrokerPool());
                    doc.read(istream);
                    updateDocument(broker, doc);
                }
            } catch (final Exception e) {
                IndexStatistics.LOG.warn("An error occurred while regenerating index statistics: " + e.getMessage(), e);
            }
            return true;
        }
    }

	@Override
	public void indexCollection(Collection col) {
	}

    @Override
    public void indexBinary(BinaryDocument doc) {
    }

    @Override
    public void removeIndex(XmldbURI url) {
    }
}