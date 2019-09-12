/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2018 The eXist Project
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
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.ElementImpl;
import org.exist.dom.persistent.IStoredNode;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.indexing.AbstractStreamListener;
import org.exist.indexing.IndexController;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.MatchListener;
import org.exist.indexing.StreamListener;
import org.exist.indexing.StreamListener.ReindexMode;
import org.exist.numbering.NodeId;
import org.exist.stax.ExtendedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.NodePath;
import org.exist.storage.btree.BTreeCallback;
import org.exist.storage.btree.Value;
import org.exist.storage.index.CollectionStore;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.txn.Txn;
import org.exist.util.Occurrences;
import org.exist.xquery.QueryRewriter;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.XQueryContext;
import org.w3c.dom.NodeList;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 */
public class IndexStatisticsWorker implements IndexWorker {
    private final IndexStatistics index;
    private final StatisticsListener listener = new StatisticsListener();

    private DataGuide perDocGuide = null;
    private ReindexMode mode = ReindexMode.STORE;
    private DocumentImpl currentDoc = null;

    public IndexStatisticsWorker(final IndexStatistics index) {
        this.index = index;
    }

    @Override
    public String getIndexId() {
        return index.getIndexId();
    }

    @Override
    public String getIndexName() {
        return index.getIndexName();
    }

    @Override
    public QueryRewriter getQueryRewriter(final XQueryContext context) {
        return null;
    }

    @Override
    public Object configure(final IndexController controller, final NodeList configNodes,
                            final Map<String, String> namespaces) {
        return null;
    }

    @Override
    public void setDocument(final DocumentImpl doc) {
        setDocument(doc, ReindexMode.UNKNOWN);
    }

    @Override
    public void setDocument(final DocumentImpl doc, final ReindexMode mode) {
        this.perDocGuide = new DataGuide();
        this.currentDoc = doc;
        this.mode = mode;
    }

    @Override
    public void setMode(final ReindexMode mode) {
        perDocGuide = new DataGuide();
        this.mode = mode;
    }

    @Override
    public DocumentImpl getDocument() {
        return currentDoc;
    }

    @Override
    public ReindexMode getMode() {
        return mode;
    }

    @Override
    public <T extends IStoredNode> IStoredNode getReindexRoot(final IStoredNode<T> node, final NodePath path,
                                                              final boolean insert, final boolean includeSelf) {
        return null;
    }

    @Override
    public StreamListener getListener() {
        if (mode == ReindexMode.STORE) {
            return listener;
        }
        return null;
    }

    @Override
    public MatchListener getMatchListener(final DBBroker broker, final NodeProxy proxy) {
        return null;
    }

    @Override
    public void flush() {
        if (perDocGuide != null) {
            index.mergeStats(perDocGuide);
//            System.out.println(index.toString());
        }
        perDocGuide = new DataGuide();
    }

    public void updateIndex(final DBBroker broker, final Txn transaction) {
        perDocGuide = new DataGuide();
        final DocumentCallback cb = new DocumentCallback(broker);
        try {
            broker.getResourcesFailsafe(transaction, cb, false);
        } catch (final TerminatedException e) {
            // thrown when the db shuts down. ignore.
        }
        index.updateStats(perDocGuide);
    }

    private void updateDocument(final DBBroker broker, final DocumentImpl doc) {
        final ElementImpl root = (ElementImpl) doc.getDocumentElement();
        final int rootLevel = root.getNodeId().getTreeLevel();
        try {
            final NodePath path = new NodePath();
            final Deque<NodeStats> stack = new ArrayDeque<>();
            final ExtendedXMLStreamReader reader = broker.getXMLStreamReader(root, false);
            while (reader.hasNext()) {
                final int status = reader.next();

                switch (status) {

                    case XMLStreamReader.START_ELEMENT:
                        for (final NodeStats next : stack) {
                            next.incDepth();
                        }
                        final QName qname = reader.getQName();
                        path.addComponent(qname);
                        final NodeStats nodeStats = perDocGuide.add(path);
                        stack.push(nodeStats);
                        break;

                    case XMLStreamReader.END_ELEMENT:
                        path.removeLastComponent();
                        final NodeStats stats = stack.pop();
                        stats.updateMaxDepth();

                        final NodeId otherId = (NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID);
                        final int otherLevel = otherId.getTreeLevel();
                        if (otherLevel == rootLevel) {
                            // finished `root element...
                            break;  // exit-while
                        }
                        break;
                }
            }
        } catch (final IOException | XMLStreamException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void removeCollection(final Collection collection, final DBBroker broker, final boolean reindex) {
        //no-op
    }

    @Override
    public boolean checkIndex(final DBBroker broker) {
        return false;
    }

    @Override
    public Occurrences[] scanIndex(final XQueryContext context, final DocumentSet docs, final NodeSet contextSet,
                                   final Map hints) {
        return new Occurrences[0];
    }

    private class StatisticsListener extends AbstractStreamListener {
        private final Deque<NodeStats> stack = new ArrayDeque<>();

        @Override
        public void startElement(final Txn transaction, final ElementImpl element, final NodePath path) {
            super.startElement(transaction, element, path);
            if (perDocGuide != null) {
                for (final NodeStats next : stack) {
                    next.incDepth();
                }
                final NodeStats nodeStats = perDocGuide.add(path);
                stack.push(nodeStats);
            }
        }

        @Override
        public void endElement(final Txn transaction, final ElementImpl element, final NodePath path) {
            super.endElement(transaction, element, path);
            if (perDocGuide != null) {
                final NodeStats stats = stack.pop();
                stats.updateMaxDepth();
            }
        }

        @Override
        public IndexWorker getWorker() {
            return IndexStatisticsWorker.this;
        }
    }

    private class DocumentCallback implements BTreeCallback {
        private final DBBroker broker;

        private DocumentCallback(final DBBroker broker) {
            this.broker = broker;
        }

        @Override
        public boolean indexInfo(final Value key, final long pointer) throws TerminatedException {
            final CollectionStore store = (CollectionStore) ((NativeBroker) broker).getStorage(NativeBroker.COLLECTIONS_DBX_ID);
            try {
                final byte type = key.data()[key.start() + Collection.LENGTH_COLLECTION_ID + DocumentImpl.LENGTH_DOCUMENT_TYPE];
                final VariableByteInput istream = store.getAsStream(pointer);
                if (type == DocumentImpl.XML_FILE) {
                    final DocumentImpl doc = DocumentImpl.read(broker.getBrokerPool(), istream);
                    updateDocument(broker, doc);
                }
            } catch (final Exception e) {
                IndexStatistics.LOG.warn("An error occurred while regenerating index statistics: " + e.getMessage(), e);
            }
            return true;
        }
    }
}
