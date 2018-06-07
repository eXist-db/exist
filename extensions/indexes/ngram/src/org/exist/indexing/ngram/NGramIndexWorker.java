/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
package org.exist.indexing.ngram;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.persistent.AttrImpl;
import org.exist.dom.persistent.AbstractCharacterData;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.ElementImpl;
import org.exist.dom.persistent.ExtArrayNodeSet;
import org.exist.dom.persistent.IStoredNode;
import org.exist.dom.persistent.Match;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.persistent.SymbolTable;
import org.exist.indexing.AbstractMatchListener;
import org.exist.indexing.AbstractStreamListener;
import org.exist.indexing.Index;
import org.exist.indexing.IndexController;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.MatchListener;
import org.exist.indexing.OrderedValuesIndex;
import org.exist.indexing.QNamedKeysIndex;
import org.exist.indexing.StreamListener;
import org.exist.indexing.StreamListener.ReindexMode;
import org.exist.numbering.NodeId;
import org.exist.stax.ExtendedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
import org.exist.storage.IndexSpec;
import org.exist.storage.NodePath;
import org.exist.storage.OccurrenceList;
import org.exist.storage.btree.BTreeCallback;
import org.exist.storage.btree.BTreeException;
import org.exist.storage.btree.IndexQuery;
import org.exist.storage.btree.Value;
import org.exist.storage.index.BFile;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.lock.LockManager;
import org.exist.storage.lock.ManagedLock;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
import org.exist.util.serializer.AttrList;
import org.exist.xquery.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 *
 * Each index entry maps a key (collectionId, ngram) to a list of occurrences, which has the
 * following structure:
 *
 * <pre>[docId : int, nameType: byte, occurrenceCount: int, entrySize: long, [id: NodeId, offset: int, ...]* ]</pre>
 */
public class NGramIndexWorker implements OrderedValuesIndex, QNamedKeysIndex {

    private static final Logger LOG = LogManager.getLogger(NGramIndexWorker.class);

    private static final String INDEX_ELEMENT = "ngram";
    private static final String QNAME_ATTR = "qname";

    private static final byte IDX_QNAME = 0;
    @SuppressWarnings("unused")
    private static final byte IDX_GENERIC = 1;

    private final LockManager lockManager;
    private ReindexMode mode = ReindexMode.STORE;
    private final org.exist.indexing.ngram.NGramIndex index;
    private char[] buf = new char[1024];
    private int currentChar = 0;
    private DocumentImpl currentDoc = null;
    private final DBBroker broker;
    @SuppressWarnings("unused")
	private IndexController controller;
    private final Map<QNameTerm, OccurrenceList> ngrams = new TreeMap<QNameTerm, OccurrenceList>();
    private final VariableByteOutputStream os = new VariableByteOutputStream(7);

    private NGramMatchListener matchListener = null;

    public NGramIndexWorker(DBBroker broker, org.exist.indexing.ngram.NGramIndex index) {
        this.broker = broker;
        this.lockManager = broker.getBrokerPool().getLockManager();
        this.index = index;
        Arrays.fill(buf, ' ');
    }

    @Override
    public String getIndexId() {
        return org.exist.indexing.ngram.NGramIndex.ID;
    }
    
    @Override
    public String getIndexName() {
        return index.getIndexName();
    }

    public Index getIndex() {
        return index;
    }

    public int getN() {
        return index.getN();
    }

    @Override
    public Object configure(IndexController controller, NodeList configNodes, Map<String, String> namespaces) throws DatabaseConfigurationException {
        this.controller = controller;
        // We use a map to store the QNames to be indexed
        Map<QName, NGramIndexConfig> map = new TreeMap<QName, NGramIndexConfig>();
        Node node;
        for(int i = 0; i < configNodes.getLength(); i++) {
            node = configNodes.item(i);
            if(node.getNodeType() == Node.ELEMENT_NODE &&
                    INDEX_ELEMENT.equals(node.getLocalName())) {
                String qname = ((Element)node).getAttribute(QNAME_ATTR);
                if (qname == null || qname.length() == 0)
                    throw new DatabaseConfigurationException("Configuration error: element " + node.getNodeName() +
	                		" must have an attribute " + QNAME_ATTR);
                if (LOG.isTraceEnabled())
                    LOG.trace("NGram index defined on " + qname);
                NGramIndexConfig config = new NGramIndexConfig(namespaces, qname);
                map.put(config.getQName(), config);
            }
        }
        return map;
    }

    @Override
    public void flush() {
        switch (mode) {
            case STORE :
                saveIndex();
                break;
            case REMOVE_ALL_NODES :
            case REMOVE_SOME_NODES :
                dropIndex(mode);
                break;
        }
    }

    private void saveIndex() {
        if (ngrams.size() == 0)
            return;
        for (Map.Entry<QNameTerm, OccurrenceList> entry : ngrams.entrySet()) {
            QNameTerm key = entry.getKey();
            OccurrenceList occurences = entry.getValue();
            occurences.sort();
            os.clear();
            os.writeInt(currentDoc.getDocId());
            os.writeByte(key.qname.getNameType());
            os.writeInt(occurences.getTermCount());
            //Mark position
            int lenOffset = os.position();
            //Dummy value : actual one will be written below
            os.writeFixedInt(0);
            NodeId previous = null;
            for (int m = 0; m < occurences.getSize(); ) {
                try {
                    previous = occurences.getNode(m).write(previous, os);
                } catch (IOException e) {
                    LOG.error("IOException while writing nGram index: " + e.getMessage(), e);
                }
                int freq = occurences.getOccurrences(m);
                os.writeInt(freq);
                for (int n = 0; n < freq; n++) {
                    os.writeInt(occurences.getOffset(m + n));
                }
                m += freq;
            }
            //Write (variable) length of node IDs + frequency + offsets
            os.writeFixedInt(lenOffset, os.position() - lenOffset - 4);

            ByteArray data = os.data();
            if (data.size() == 0)
                continue;
            try(final ManagedLock<ReentrantLock> dbLock = lockManager.acquireBtreeWriteLock(index.db.getLockName())) {
                NGramQNameKey value = new NGramQNameKey(currentDoc.getCollection().getId(), key.qname,
                        index.getBrokerPool().getSymbols(), key.term);
                index.db.append(value, data);
            } catch (LockException e) {
                LOG.warn("Failed to acquire lock for file " + FileUtils.fileName(index.db.getFile()), e);
            } catch (IOException e) {
                LOG.warn("IO error for file " + FileUtils.fileName(index.db.getFile()), e);
            } catch (ReadOnlyException e) {
                LOG.warn("Read-only error for file " + FileUtils.fileName(index.db.getFile()), e);
            } finally {
                os.clear();
            }
        }
        ngrams.clear();
    }

    private void dropIndex(ReindexMode mode) {
        if (ngrams.size() == 0)
            return;
        for (Map.Entry<QNameTerm, OccurrenceList> entry : ngrams.entrySet()) {
            QNameTerm key = entry.getKey();
            OccurrenceList occurencesList = entry.getValue();
            occurencesList.sort();
            os.clear();

            try(final ManagedLock<ReentrantLock> dbLock = lockManager.acquireBtreeWriteLock(index.db.getLockName())) {
                NGramQNameKey value = new NGramQNameKey(currentDoc.getCollection().getId(), key.qname,
                        index.getBrokerPool().getSymbols(), key.term);
                boolean changed = false;
                os.clear();
                VariableByteInput is = index.db.getAsStream(value);
                if (is == null)
                    continue;
                while (is.available() > 0) {
                    int storedDocId = is.readInt();
                    byte nameType = is.readByte();
                    int occurrences = is.readInt();
                    //Read (variable) length of node IDs + frequency + offsets
                    int length = is.readFixedInt();
                    if (storedDocId != currentDoc.getDocId()) {
                        // data are related to another document:
                        // copy them to any existing data
                        os.writeInt(storedDocId);
                        os.writeByte(nameType);
                        os.writeInt(occurrences);
                        os.writeFixedInt(length);
                        is.copyRaw(os, length);
                    } else {
                        // data are related to our document:
                        if (mode == ReindexMode.REMOVE_ALL_NODES) {
                            // skip them
                            is.skipBytes(length);
                        } else {
                            // removing nodes: need to filter out the node ids to be removed
                            // feed the new list with the GIDs

                            NodeId previous = null;
                            OccurrenceList newOccurrences = new OccurrenceList();
                            for (int m = 0; m < occurrences; m++) {
                                NodeId nodeId = index.getBrokerPool().getNodeFactory().createFromStream(previous, is);
                                previous = nodeId;
                                int freq = is.readInt();
                                // add the node to the new list if it is not
                                // in the list of removed nodes
                                if (!occurencesList.contains(nodeId)) {
                                    for (int n = 0; n < freq; n++) {
                                        newOccurrences.add(nodeId, is.readInt());
                                    }
                                } else {
                                    is.skip(freq);
                                }
                            }
                            // append the data from the new list
                            if(newOccurrences.getSize() > 0) {
                                //Don't forget this one
                                newOccurrences.sort();
                                os.writeInt(currentDoc.getDocId());
                                os.writeByte(nameType);
                                os.writeInt(newOccurrences.getTermCount());
                                //Mark position
                                int lenOffset = os.position();
                                //Dummy value : actual one will be written below
                                os.writeFixedInt(0);
                                previous = null;
                                for (int m = 0; m < newOccurrences.getSize(); ) {
                                    previous = newOccurrences.getNode(m).write(previous, os);
                                    int freq = newOccurrences.getOccurrences(m);
                                    os.writeInt(freq);
                                    for (int n = 0; n < freq; n++) {
                                        os.writeInt(newOccurrences.getOffset(m + n));
                                    }
                                    m += freq;
                                }
                                //Write (variable) length of node IDs + frequency + offsets
                                os.writeFixedInt(lenOffset, os.position() - lenOffset - 4);
                            }
                        }
                        changed = true;
                    }
                }
                //Store new data, if relevant
                if (changed) {
                    //Well, nothing to store : remove the existing data
                    if (os.data().size() == 0) {
                        index.db.remove(value);
                    } else {
                        if (index.db.put(value, os.data()) == BFile.UNKNOWN_ADDRESS) {
                            LOG.error("Could not put index data for token '" +  key.term + "' in '" +
                                    FileUtils.fileName(index.db.getFile()) + "'");
                        }
                    }
                }
            } catch (LockException e) {
                LOG.warn("Failed to acquire lock for file " + FileUtils.fileName(index.db.getFile()), e);
            } catch (IOException e) {
                LOG.warn("IO error for file " + FileUtils.fileName(index.db.getFile()), e);
            } finally {
                os.clear();
            }
        }
        ngrams.clear();
    }

    @Override
    public void removeCollection(Collection collection, DBBroker broker, boolean reindex) {
        if (LOG.isDebugEnabled())
            LOG.debug("Dropping NGram index for collection " + collection.getURI());
        try(final ManagedLock<ReentrantLock> dbLock = lockManager.acquireBtreeWriteLock(index.db.getLockName())) {
            // remove generic index
            Value value = new NGramQNameKey(collection.getId());
            index.db.removeAll(null, new IndexQuery(IndexQuery.TRUNC_RIGHT, value));
        } catch (final LockException e) {
            LOG.warn("Failed to acquire lock for '" + FileUtils.fileName(index.db.getFile()) + "'", e);
        } catch (final BTreeException | IOException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    public NodeSet search(int contextId, DocumentSet docs, List<QName> qnames, String query, String ngram, XQueryContext context, NodeSet contextSet, int axis)
 throws XPathException {
        if (qnames == null || qnames.isEmpty())
            qnames = getDefinedIndexes(context.getBroker(), docs);
        final NodeSet result = new ExtArrayNodeSet(docs.getDocumentCount(), 250);
        for (Iterator<org.exist.collections.Collection> iter = docs.getCollectionIterator(); iter.hasNext();) {
            final int collectionId = iter.next().getId();
            for (int i = 0; i < qnames.size(); i++) {
                QName qname = qnames.get(i);
                NGramQNameKey key = new NGramQNameKey(collectionId, qname, index.getBrokerPool().getSymbols(), query);
                try(final ManagedLock<ReentrantLock> dbLock = lockManager.acquireBtreeReadLock(index.db.getLockName())) {
                    SearchCallback cb = new SearchCallback(contextId, query, ngram, docs, contextSet, context, result, axis == NodeSet.ANCESTOR);
                    int op = query.codePointCount(0, query.length()) < getN() ? IndexQuery.TRUNC_RIGHT : IndexQuery.EQ;
                    index.db.query(new IndexQuery(op, key), cb);
                } catch (LockException e) {
                    LOG.warn("Failed to acquire lock for '" + FileUtils.fileName(index.db.getFile()) + "'", e);
                } catch (final IOException | BTreeException e) {
                    LOG.error(e.getMessage() + " in '" + FileUtils.fileName(index.db.getFile()) + "'", e);
                }
            }
        }

        result.iterate(); // ensure result is ready to use

        return result;
    }

    /**
     * Check index configurations for all collection in the given DocumentSet and return
     * a list of QNames, which have indexes defined on them.
     *
     * @param broker
     * @param docs
     * 
     */
    private List<QName> getDefinedIndexes(DBBroker broker, DocumentSet docs) {
        List<QName> indexes = new ArrayList<QName>(20);
        for (Iterator<Collection> i = docs.getCollectionIterator(); i.hasNext(); ) {
            Collection collection = i.next();
            IndexSpec idxConf = collection.getIndexConfiguration(broker);
            if (idxConf != null) {
                Map<?,?> config = (Map<?,?>) idxConf.getCustomIndexSpec(NGramIndex.ID);
                if (config != null) {
                    for (Object name : config.keySet()) {
                        QName qn = (QName) name;
                        indexes.add(qn);
                    }
                }
            }
        }
        return indexes;
    }
    
    @Override
    public boolean checkIndex(DBBroker broker) {
    	return true;
    }

    @Override
    public Occurrences[] scanIndex(XQueryContext context, DocumentSet docs, NodeSet contextSet, Map hints) {
        List<QName> qnames = hints == null ? null : (List<QName>)hints.get(QNAMES_KEY);
        //Expects a StringValue
        Object start = hints == null ? null : hints.get(START_VALUE);
        //Expects a StringValue
        Object end = hints == null ? null : hints.get(END_VALUE); 
        if (qnames == null || qnames.isEmpty())
            qnames = getDefinedIndexes(context.getBroker(), docs);
        //TODO : use the IndexWorker.VALUE_COUNT hint, if present, to limit the number of returned entries
        final IndexScanCallback cb = new IndexScanCallback(docs, contextSet);
        for (int q = 0; q < qnames.size(); q++) {
            for (Iterator<Collection> i = docs.getCollectionIterator(); i.hasNext();) {
                final int collectionId = i.next().getId();
                final IndexQuery query;
                if (start == null) {
                    Value startRef = new NGramQNameKey(collectionId);
                    query = new IndexQuery(IndexQuery.TRUNC_RIGHT, startRef);
                } else if (end == null) {
                    Value startRef = new NGramQNameKey(collectionId, qnames.get(q),
                    		index.getBrokerPool().getSymbols(), start.toString().toLowerCase());
                    query = new IndexQuery(IndexQuery.TRUNC_RIGHT, startRef);
                } else {
                    Value startRef = new NGramQNameKey(collectionId, qnames.get(q), 
                    	index.getBrokerPool().getSymbols(), start.toString().toLowerCase());
                    Value endRef = new NGramQNameKey(collectionId, qnames.get(q),
                    		index.getBrokerPool().getSymbols(), end.toString().toLowerCase());
                    query = new IndexQuery(IndexQuery.BW, startRef, endRef);
                }
                try(final ManagedLock<ReentrantLock> dbLock = lockManager.acquireBtreeReadLock(index.db.getLockName())) {
                    index.db.query(query, cb);
                } catch (final LockException e) {
                    LOG.warn("Failed to acquire lock for '" + FileUtils.fileName(index.db.getFile()) + "'", e);
                } catch (final IOException | BTreeException e) {
                    LOG.error(e.getMessage(), e);
                } catch (final TerminatedException e) {
                    LOG.warn(e.getMessage(), e);
                }
            }
        }
        Occurrences[] result = new Occurrences[cb.map.size()];
        return cb.map.values().toArray(result);
    }

    //This listener is always the same whatever the document and the mode
    //It should thus be declared static
    private final StreamListener listener = new NGramStreamListener();

    @Override
    public StreamListener getListener() {
        return listener;
    }

    @Override
    public MatchListener getMatchListener(DBBroker broker, NodeProxy proxy) {
        return getMatchListener(broker, proxy, null);
    }

    public MatchListener getMatchListener(DBBroker broker, NodeProxy proxy, NGramMatchCallback callback) {
        boolean needToFilter = false;
        Match nextMatch = proxy.getMatches();
        while (nextMatch != null) {
            if (nextMatch.getIndexId() == org.exist.indexing.ngram.NGramIndex.ID) {
                needToFilter = true;
                break;
            }
            nextMatch = nextMatch.getNextMatch();
        }
        if (!needToFilter)
            return null;
        if (matchListener == null)
            matchListener = new NGramMatchListener(broker, proxy);
        else
            matchListener.reset(broker, proxy);
        matchListener.setMatchCallback(callback);
        return matchListener;
    }

    @Override
    public <T extends IStoredNode> IStoredNode getReindexRoot(IStoredNode<T> node, NodePath path, boolean insert, boolean includeSelf) {
        if (node.getNodeType() == Node.ATTRIBUTE_NODE)
            return null;
        IndexSpec indexConf = node.getOwnerDocument().getCollection().getIndexConfiguration(broker);
        if (indexConf != null) {
            Map<?,?> config = (Map<?,?>) indexConf.getCustomIndexSpec(NGramIndex.ID);
            if (config == null)
                return null;
            boolean reindexRequired = false;
            int len = node.getNodeType() == Node.ELEMENT_NODE && !includeSelf ? path.length() - 1 : path.length();
            for (int i = 0; i < len; i++) {
                QName qn = path.getComponent(i);
                if (config.get(qn) != null) {
                    reindexRequired = true;
                    break;
                }
            }
            if (reindexRequired) {
                IStoredNode topMost = null;
                IStoredNode<T> currentNode = node;
                while (currentNode != null) {
                    if (config.get(currentNode.getQName()) != null)
                    	topMost = currentNode;
                    if (currentNode.getOwnerDocument().getCollection().isTempCollection() && currentNode.getNodeId().getTreeLevel() == 2)
                        break;
                    //currentNode = (StoredNode) currentNode.getParentNode();
                    currentNode = currentNode.getParentStoredNode();
                }
                return topMost;
            }
        }
        return null;
    }

    /**
     * Split the given text string into ngrams. The size of an ngram is determined
     * by counting the codepoints, not the characters. The resulting strings may
     * thus be longer than the ngram size.
     *
     * @param text
     * 
     */
    public String[] tokenize(String text) {
        int len = text.codePointCount(0, text.length());
        int gramSize = index.getN();
        String[] ngrams = new String[len];
        int next = 0;
        int pos = 0;
        StringBuilder bld = new StringBuilder(gramSize);
        for (int i = 0; i < len; i++) {
            bld.setLength(0);
            int offset = pos;
            for (int count = 0; count < gramSize && offset < text.length(); count++) {
                int codepoint = Character.toLowerCase(text.codePointAt(offset));
                offset += Character.charCount(codepoint);
                if (count == 0)
                    pos = offset;   // advance pos to next character
                bld.appendCodePoint(codepoint);
            }
            ngrams[next++] = bld.toString();
        }
        return ngrams;
    }

    private void indexText(NodeId nodeId, QName qname, String text) {
        final String[] ngram = tokenize(text);
        final int len = text.length();
        for (int i = 0, j = 0, cp; i < len; i += Character.charCount(cp), j++) {
            cp = text.codePointAt(i);
            final QNameTerm key = new QNameTerm(qname, ngram[j]);
            OccurrenceList list = ngrams.get(key);
            if (list == null) {
                list = new OccurrenceList();
                list.add(nodeId, i);
                ngrams.put(key, list);
            } else {
                list.add(nodeId, i);
            }
        }
    }

    private void checkBuffer() {
        if (currentChar + index.getN() > buf.length) {
            buf = new char[1024];
            Arrays.fill(buf, ' ');
            currentChar = 0;
        }
    }

    private Map<QName, ?> config;
    private Stack<XMLString> contentStack = null;

    @Override
    public void setDocument(DocumentImpl document) {
    	setDocument(document, ReindexMode.UNKNOWN);
    }

    @Override
    public void setMode(ReindexMode newMode) {
        // wolf: unnecessary call to setDocument?
//    	setDocument(currentDoc, newMode);
        mode = newMode;
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
    public void setDocument(DocumentImpl document, ReindexMode newMode) {
    	currentDoc = document;
        //config = null;
        contentStack = null;
        IndexSpec indexConf = document.getCollection().getIndexConfiguration(broker);
        if (indexConf != null)
            config = (Map<QName, ?>) indexConf.getCustomIndexSpec(org.exist.indexing.ngram.NGramIndex.ID);
        mode = newMode;
    }

    @Override
    public QueryRewriter getQueryRewriter(XQueryContext context) {
        return null;
    }

    private class NGramStreamListener extends AbstractStreamListener {

        public NGramStreamListener() {
            //Nothing to do
        }

        @Override
        public void startElement(Txn transaction, ElementImpl element, NodePath path) {
            if (config != null && config.get(element.getQName()) != null) {
                if (contentStack == null) contentStack = new Stack<XMLString>();
                XMLString contentBuf = new XMLString();
                contentStack.push(contentBuf);
            }
            super.startElement(transaction, element, path);
        }

        @Override
        public void attribute(Txn transaction, AttrImpl attrib, NodePath path) {
            if (config != null && config.get(attrib.getQName()) != null) {
                indexText(attrib.getNodeId(), attrib.getQName(), attrib.getValue());
            }
            super.attribute(transaction, attrib, path);
        }

        @Override
        public void endElement(Txn transaction, ElementImpl element, NodePath path) {
            if (config != null && config.get(element.getQName()) != null) {
                XMLString content = contentStack.pop();
                indexText(element.getNodeId(), element.getQName(), content.toString());
            }
            super.endElement(transaction, element, path);
        }

        @Override
        public void characters(Txn transaction, AbstractCharacterData text, NodePath path) {
            if (contentStack != null && !contentStack.isEmpty()) {
                for (XMLString next : contentStack) {
                    next.append(text.getXMLString());
                }
            }
            super.characters(transaction, text, path);
        }

        @Override
        public IndexWorker getWorker() {
        	return NGramIndexWorker.this;
        }
    }

    private class NGramMatchListener extends AbstractMatchListener {

        private Match match;
        private Stack<NodeOffset> offsetStack = null;
        private NGramMatchCallback callback = null;
        @SuppressWarnings("unused")
		private NodeProxy root;

        public NGramMatchListener(DBBroker broker, NodeProxy proxy) {
            reset(broker, proxy);
        }

        protected void setMatchCallback(NGramMatchCallback cb) {
            this.callback = cb;
        }

        protected void reset(DBBroker broker, NodeProxy proxy) {
            this.root = proxy;
            this.match = proxy.getMatches();
            setNextInChain(null);
            /* Check if an index is defined on an ancestor of the current node.
             * If yes, scan the ancestor to get the offset of the first character
             * in the current node. For example, if the indexed node is &lt;a>abc&lt;b>de&lt;/b></a>
             * and we query for //a[text:ngram-contains(., 'de')]/b, proxy will be a &lt;b> node, but
             * the offsets of the matches are relative to the start of &lt;a>.
             */
            NodeSet ancestors = null;
            Match nextMatch = this.match;
            while (nextMatch != null) {
                if (proxy.getNodeId().isDescendantOf(nextMatch.getNodeId())) {
                    if (ancestors == null)
                        ancestors = new ExtArrayNodeSet();
                    ancestors.add(new NodeProxy(proxy.getOwnerDocument(), nextMatch.getNodeId()));
                }
                nextMatch = nextMatch.getNextMatch();
            }
            if (ancestors != null && !ancestors.isEmpty()) {
                for (NodeProxy p : ancestors) {
                    int startOffset = 0;
                    try {
                        XMLStreamReader reader = broker.getXMLStreamReader(p, false);
                        while (reader.hasNext()) {
                            int ev = reader.next();
                            NodeId nodeId = (NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID);
                            if (nodeId.equals(proxy.getNodeId()))
                                break;
                            if (ev == XMLStreamConstants.CHARACTERS)
                                startOffset += reader.getText().length();
                        }
                    } catch (IOException e) {
                        LOG.warn("Problem found while serializing XML: " + e.getMessage(), e);
                    } catch (XMLStreamException e) {
                        LOG.warn("Problem found while serializing XML: " + e.getMessage(), e);
                    }
                    if (offsetStack == null)
                        offsetStack = new Stack<NodeOffset>();
                    offsetStack.push(new NodeOffset(p.getNodeId(), startOffset));
                }
            }
        }

        @Override
        public void startElement(QName qname, AttrList attribs) throws SAXException {
            Match nextMatch = match;
            // check if there are any matches in the current element
            // if yes, push a NodeOffset object to the stack to track
            // the node contents
            while (nextMatch != null) {
                if (nextMatch.getNodeId().equals(getCurrentNode().getNodeId())) {
                    if (offsetStack == null)
                        offsetStack = new Stack<NodeOffset>();
                    offsetStack.push(new NodeOffset(nextMatch.getNodeId()));
                    break;
                }
                nextMatch = nextMatch.getNextMatch();
            }
            super.startElement(qname, attribs);
        }

        @Override
        public void endElement(QName qname) throws SAXException {
            Match nextMatch = match;
            // check if we need to pop the stack
            while (nextMatch != null) {
                if (nextMatch.getNodeId().equals(getCurrentNode().getNodeId())) {
                    offsetStack.pop();
                    break;
                }
                nextMatch = nextMatch.getNextMatch();
            }
            super.endElement(qname);
        }

        @Override
        public void characters(CharSequence seq) throws SAXException {
            List<Match.Offset> offsets = null;    // a list of offsets to process
            if (offsetStack != null) {
                // walk through the stack to find matches which start in
                // the current string of text
                for (int i = 0; i < offsetStack.size(); i++) {
                    NodeOffset no = offsetStack.get(i);
                    int end = no.offset + seq.length();
                    // scan all matches
                    Match next = match;
                    while (next != null) {
                        if (next.getIndexId() == NGramIndex.ID && next.getNodeId().equals(no.nodeId)) {
                            int freq = next.getFrequency();
                            for (int j = 0; j < freq; j++) {
                                Match.Offset offset = next.getOffset(j);
                                if (offset.getOffset() < end &&
                                    offset.getOffset() + offset.getLength() > no.offset) {
                                    // add it to the list to be processed
                                    if (offsets == null) {
                                        offsets = new ArrayList<Match.Offset>(4);
                                    }
                                    // adjust the offset and add it to the list
                                    int start = offset.getOffset() - no.offset;
                                    int len = offset.getLength();
                                    if (start < 0) {
                                        len = len - Math.abs(start);
                                        start = 0;
                                    }
                                    if (start + len > seq.length())
                                        len = seq.length() - start;
                                    offsets.add(new Match.Offset(start, len));
                                }
                            }
                        }
                        next = next.getNextMatch();
                    }
                    // add the length of the current text to the element content length
                    no.offset = end;
                }
            }
            // now print out the text, marking all matches with a match element
            if (offsets != null) {
                FastQSort.sort(offsets, 0, offsets.size() - 1);
                String s = seq.toString();
                int pos = 0;
                for (Match.Offset offset : offsets) {
                    if (offset.getOffset() > pos) {
                        super.characters(s.substring(pos, pos + (offset.getOffset() - pos)));
                    }
                    if (callback == null) {
                        super.startElement(MATCH_ELEMENT, null);
                        super.characters(s.substring(offset.getOffset(), offset.getOffset() + offset.getLength()));
                        super.endElement(MATCH_ELEMENT);
                    } else {
                        try {
                            callback.match(nextListener, s.substring(offset.getOffset(), offset.getOffset() + offset.getLength()),
                                    new NodeProxy(getCurrentNode()));
                        } catch (XPathException e) {
                            throw new SAXException("An error occurred while calling match callback: " + e.getMessage(), e);
                        }
                    }
                    pos = offset.getOffset() + offset.getLength();
                }
                if (pos < s.length()) {
                    super.characters(s.substring(pos));
                }
            } else
                super.characters(seq);
        }
    }

    private static class NodeOffset {
        NodeId nodeId;
        int offset = 0;

        public NodeOffset(NodeId nodeId) {
            this.nodeId = nodeId;
        }

        public NodeOffset(NodeId nodeId, int offset) {
            this.nodeId = nodeId;
            this.offset = offset;
        }
    }
    
    private static class QNameTerm implements Comparable<QNameTerm> {

        QName qname;
        String term;

        public QNameTerm(QName qname, String term) {
            this.qname = qname;
            this.term = term;
        }

        @Override
        public int compareTo(QNameTerm other) {
            int cmp = qname.compareTo(other.qname);
            if (cmp == 0)
                return term.compareTo(other.term);
            return cmp;
        }
    }

    private static class NGramQNameKey extends Value {

        private static final int COLLECTION_ID_OFFSET = 1;
        private static final int NAMETYPE_OFFSET = COLLECTION_ID_OFFSET + Collection.LENGTH_COLLECTION_ID; // 5
        private static final int NAMESPACE_OFFSET = NAMETYPE_OFFSET + ElementValue.LENGTH_TYPE; // 6
        private static final int LOCALNAME_OFFSET = NAMESPACE_OFFSET + SymbolTable.LENGTH_NS_URI; // 8
        private static final int NGRAM_OFFSET = LOCALNAME_OFFSET + SymbolTable.LENGTH_LOCAL_NAME; // 10

        public NGramQNameKey(int collectionId) {
            len = Collection.LENGTH_COLLECTION_ID + 1;
            data = new byte[len];
            data[0] = IDX_QNAME;
            ByteConversion.intToByte(collectionId, data, COLLECTION_ID_OFFSET);
        }

        /*
        public NGramQNameKey(int collectionId, QName qname, SymbolTable symbols) {
            len = NGRAM_OFFSET;
            data = new byte[len];
            data[0] = IDX_QNAME;
            ByteConversion.intToByte(collectionId, data, COLLECTION_ID_OFFSET);
            final short namespaceId = symbols.getNSSymbol(qname.getNamespaceURI());
            final short localNameId = symbols.getSymbol(qname.getLocalPart());
            data[NAMETYPE_OFFSET] = qname.getNameType();
            ByteConversion.shortToByte(namespaceId, data, NAMESPACE_OFFSET);
            ByteConversion.shortToByte(localNameId, data, LOCALNAME_OFFSET);
        }
        */

        public NGramQNameKey(int collectionId, QName qname, SymbolTable symbols, String ngram) {
            len = UTF8.encoded(ngram) + NGRAM_OFFSET;
            data = new byte[len];
            data[0] = IDX_QNAME;
            ByteConversion.intToByte(collectionId, data, COLLECTION_ID_OFFSET);
            final short namespaceId = symbols.getNSSymbol(qname.getNamespaceURI());
            final short localNameId = symbols.getSymbol(qname.getLocalPart());
            data[NAMETYPE_OFFSET] = qname.getNameType();
            ByteConversion.shortToByte(namespaceId, data, NAMESPACE_OFFSET);
            ByteConversion.shortToByte(localNameId, data, LOCALNAME_OFFSET);
            UTF8.encode(ngram, data, NGRAM_OFFSET);
        }
    }

    private final class SearchCallback implements BTreeCallback {

        private final int contextId;
        private final String query;
        private final String ngram;
        private final DocumentSet docs;
        private final NodeSet contextSet;
        private final XQueryContext context;
        private final NodeSet resultSet;
        private final boolean returnAncestor;

        public SearchCallback(int contextId, String query, String ngram, DocumentSet docs, NodeSet contextSet,
                  XQueryContext context, NodeSet result, boolean returnAncestor) {
            this.contextId = contextId;
            this.query = query;
            this.ngram = ngram;
            this.docs = docs;
            this.context = context;
            this.contextSet = contextSet;
            this.resultSet = result;
            this.returnAncestor = returnAncestor;
        }

        @Override
        public boolean indexInfo(Value key, long pointer) throws TerminatedException {
            String ngram = new String(key.getData(), NGramQNameKey.NGRAM_OFFSET, key.getLength() - NGramQNameKey.NGRAM_OFFSET, UTF_8);

            VariableByteInput is;
            try {
                is = index.db.getAsStream(pointer);
                //Does the token already has data in the index ?
                if (is == null)
                    return true;
                while (is.available() > 0) {
                    int storedDocId = is.readInt();
                    byte nameType = is.readByte();
                    int occurrences = is.readInt();
                    //Read (variable) length of node IDs + frequency + offsets
                    int length = is.readFixedInt();
                    DocumentImpl storedDocument = docs.getDoc(storedDocId);
                    //Exit if the document is not concerned
                    if (storedDocument == null) {
                        is.skipBytes(length);
                        continue;
                    }
                    NodeId previous = null;
                    for (int m = 0; m < occurrences; m++) {
                        NodeId nodeId = index.getBrokerPool().getNodeFactory().createFromStream(previous, is);
                        previous = nodeId;
                        int freq = is.readInt();
                        NodeProxy nodeProxy = new NodeProxy(storedDocument, nodeId, nameTypeToNodeType(nameType));
                        // if a context set is specified, we can directly check if the
                        // matching node is a descendant of one of the nodes
                        // in the context set.
                        if (contextSet != null) {
                            int sizeHint = contextSet.getSizeHint(storedDocument);
                            if (returnAncestor) {
                                NodeProxy parentNode = contextSet.parentWithChild(nodeProxy, false, true, NodeProxy.UNKNOWN_NODE_LEVEL);
                                if (parentNode != null) {
                                    readMatches(ngram, is, nodeId, freq, parentNode);
                                    resultSet.add(parentNode, sizeHint);
                                } else
                                    is.skip(freq);
                            } else {
                                readMatches(ngram, is, nodeId, freq, nodeProxy);
                                resultSet.add(nodeProxy, sizeHint);
                            }
                            // otherwise, we add all text nodes without check
                        } else {
                            readMatches(ngram, is, nodeId, freq, nodeProxy);
                            resultSet.add(nodeProxy, Constants.NO_SIZE_HINT);
                        }
                        context.proceed();
                    }
                }
                return false;
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);
                return true;
            }
        }

        private short nameTypeToNodeType(final byte nameType) {
            switch(nameType) {
                case ElementValue.ELEMENT:
                    return Node.ELEMENT_NODE;

                case ElementValue.ATTRIBUTE:
                    return Node.ATTRIBUTE_NODE;

                case ElementValue.UNKNOWN:
                default:
                    return NodeProxy.UNKNOWN_NODE_TYPE;
            }
        }

        private void readMatches(String current, VariableByteInput is, NodeId nodeId, int freq, NodeProxy parentNode) throws IOException {
            int diff = 0;
            if (current.length() > ngram.length())
                diff = current.lastIndexOf(ngram);
            Match match = new NGramMatch(contextId, nodeId, ngram, freq);
            for (int n = 0; n < freq; n++) {
                int offset = is.readInt();
                if (diff > 0)
                    offset += diff;
                match.addOffset(offset, ngram.length());
            }
            parentNode.addMatch(match);
        }
    }

    private final class IndexScanCallback implements BTreeCallback {

        private final DocumentSet docs;
        private NodeSet contextSet;
        private final Map<String, Occurrences> map = new TreeMap<String, Occurrences>();

        //IndexScanCallback(DocumentSet docs) {
            //this.docs = docs;
        //}

        IndexScanCallback(DocumentSet docs, NodeSet contextSet) {
            this.docs = docs;
            this.contextSet = contextSet;
        }

        /* (non-Javadoc)
         * @see org.dbxml.core.filer.BTreeCallback#indexInfo(org.dbxml.core.data.Value, long)
         */
        @Override
        public boolean indexInfo(Value key, long pointer) throws TerminatedException {
            String term = new String(key.getData(), NGramQNameKey.NGRAM_OFFSET, key.getLength() - NGramQNameKey.NGRAM_OFFSET, UTF_8);

            VariableByteInput is;
            try {
                is = index.db.getAsStream(pointer);
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);
                return true;
            }
            try {
                while (is.available() > 0) {
                    boolean docAdded = false;
                    int storedDocId = is.readInt();
                    byte nameType = is.readByte();
                    int occurrences = is.readInt();
                    //Read (variable) length of node IDs + frequency + offsets
                    int length = is.readFixedInt();
                    DocumentImpl storedDocument = docs.getDoc(storedDocId);
                    //Exit if the document is not concerned
                    if (storedDocument == null) {
                        is.skipBytes(length);
                        continue;
                    }
                    NodeId previous = null;
                    for (int m = 0; m < occurrences; m++) {
                        NodeId nodeId = index.getBrokerPool().getNodeFactory().createFromStream(previous, is);
                        previous = nodeId;
                        int freq = is.readInt();
                        is.skip(freq);
                        boolean include = true;
                        //TODO : revisit
                        if (contextSet != null) {
                            NodeProxy parentNode = contextSet.parentWithChild(storedDocument, nodeId, false, true);
                            include = (parentNode != null);
                        }
                        if (include) {
                            Occurrences oc = map.get(term);
                            if (oc == null) {
                                oc = new Occurrences(term);
                                map.put(term, oc);
                            }
                            if (!docAdded) {
                                oc.addDocument(storedDocument);
                                docAdded = true;
                            }
                            oc.addOccurrences(freq);
                        }
                    }
                }
            } catch(IOException e) {
                LOG.error(e.getMessage() + " in '" + FileUtils.fileName(index.db.getFile()) + "'", e);
            }
            return true;
        }
    }

}
