/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.indexing.ngram;

import java.io.IOException;
import java.util.*;
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

    private final DBBroker broker;
    private final LockManager lockManager;
    private final org.exist.indexing.ngram.NGramIndex index;

    private ReindexMode mode = ReindexMode.STORE;
    private char[] buf = new char[1024];
    private DocumentImpl currentDoc = null;
    private Map<QName, ?> config;
    private final Deque<XMLString> contentStack = new ArrayDeque<>();

    @SuppressWarnings("unused")
    private IndexController controller;
    private final Map<QNameTerm, OccurrenceList> ngrams = new TreeMap<>();
    private final VariableByteOutputStream os = new VariableByteOutputStream(128);

    private NGramMatchListener matchListener = null;

    public NGramIndexWorker(final DBBroker broker, final org.exist.indexing.ngram.NGramIndex index) {
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
    public Object configure(final IndexController controller, final NodeList configNodes, final Map<String, String> namespaces) throws DatabaseConfigurationException {
        this.controller = controller;
        // We use a map to store the QNames to be indexed
        final Map<QName, NGramIndexConfig> map = new TreeMap<>();
        for (int i = 0; i < configNodes.getLength(); i++) {
            final Node node = configNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE &&
                    INDEX_ELEMENT.equals(node.getLocalName())) {
                final String qname = ((Element) node).getAttribute(QNAME_ATTR);
                if (qname.isEmpty()) {
                    throw new DatabaseConfigurationException("Configuration error: element " + node.getNodeName() +
                            " must have an attribute " + QNAME_ATTR);
                }
                if (LOG.isTraceEnabled()) {
                    LOG.trace("NGram index defined on {}", qname);
                }
                final NGramIndexConfig config = new NGramIndexConfig(namespaces, qname);
                map.put(config.getQName(), config);
            }
        }
        return map;
    }

    @Override
    public void flush() {
        switch (mode) {
            case STORE:
                saveIndex();
                break;
            case REMOVE_ALL_NODES:
            case REMOVE_SOME_NODES:
                dropIndex(mode);
                break;
        }
    }

    private void saveIndex() {
        if (ngrams.isEmpty()) {
            return;
        }

        final VariableByteOutputStream buf = new VariableByteOutputStream();
        for (final Map.Entry<QNameTerm, OccurrenceList> entry : ngrams.entrySet()) {
            final QNameTerm key = entry.getKey();
            final OccurrenceList occurences = entry.getValue();
            occurences.sort();
            os.clear();
            os.writeInt(currentDoc.getDocId());
            os.writeByte(key.qname.getNameType());
            os.writeInt(occurences.getTermCount());

            // write nodeids, freq, and offsets to a `temp` buf
            try {
                NodeId previous = null;
                for (int m = 0; m < occurences.getSize(); ) {
                    previous = occurences.getNode(m).write(previous, buf);

                    final int freq = occurences.getOccurrences(m);
                    buf.writeInt(freq);
                    for (int n = 0; n < freq; n++) {
                        buf.writeInt(occurences.getOffset(m + n));
                    }
                    m += freq;
                }

                final byte[] bufData = buf.toByteArray();

                // clear the buf for the next iteration
                buf.clear();

                // Write length of node IDs + frequency + offsets (bytes)
                os.writeFixedInt(bufData.length);

                // Write the node IDs + frequency + offset
                os.write(bufData);
            } catch (final IOException e) {
                LOG.error("IOException while writing nGram index: {}", e.getMessage(), e);
            }

            final ByteArray data = os.data();
            if (data.size() == 0) {
                continue;
            }

            try (final ManagedLock<ReentrantLock> dbLock = lockManager.acquireBtreeWriteLock(index.db.getLockName())) {
                final NGramQNameKey value = new NGramQNameKey(currentDoc.getCollection().getId(), key.qname,
                        index.getBrokerPool().getSymbols(), key.term);
                index.db.append(value, data);
            } catch (final LockException e) {
                LOG.warn("Failed to acquire lock for file {}", FileUtils.fileName(index.db.getFile()), e);
            } catch (final IOException e) {
                LOG.warn("IO error for file {}", FileUtils.fileName(index.db.getFile()), e);
            } catch (final ReadOnlyException e) {
                LOG.warn("Read-only error for file {}", FileUtils.fileName(index.db.getFile()), e);
            } finally {
                os.clear();
            }
        }
        ngrams.clear();
    }

    private void dropIndex(final ReindexMode mode) {
        if (ngrams.isEmpty()) {
            return;
        }

        final VariableByteOutputStream buf = new VariableByteOutputStream();

        for (final Map.Entry<QNameTerm, OccurrenceList> entry : ngrams.entrySet()) {
            final QNameTerm key = entry.getKey();
            final OccurrenceList occurencesList = entry.getValue();
            occurencesList.sort();
            os.clear();

            try (final ManagedLock<ReentrantLock> dbLock = lockManager.acquireBtreeWriteLock(index.db.getLockName())) {
                final NGramQNameKey value = new NGramQNameKey(currentDoc.getCollection().getId(), key.qname,
                        index.getBrokerPool().getSymbols(), key.term);
                boolean changed = false;
                os.clear();
                final VariableByteInput is = index.db.getAsStream(value);
                if (is == null) {
                    continue;
                }
                while (is.available() > 0) {
                    final int storedDocId = is.readInt();
                    final byte nameType = is.readByte();
                    final int occurrences = is.readInt();
                    //Read (variable) length of node IDs + frequency + offsets
                    final int length = is.readFixedInt();
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

                            final OccurrenceList newOccurrences = new OccurrenceList();
                            NodeId previous = null;
                            for (int m = 0; m < occurrences; m++) {
                                final NodeId nodeId = index.getBrokerPool().getNodeFactory().createFromStream(previous, is);
                                previous = nodeId;
                                final int freq = is.readInt();
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
                            if (newOccurrences.getSize() > 0) {
                                //Don't forget this one
                                newOccurrences.sort();
                                os.writeInt(currentDoc.getDocId());
                                os.writeByte(nameType);
                                os.writeInt(newOccurrences.getTermCount());

                                // write nodeids, freq, and offsets to a `temp` buf
                                previous = null;
                                for (int m = 0; m < newOccurrences.getSize(); ) {
                                    previous = newOccurrences.getNode(m).write(previous, buf);
                                    final int freq = newOccurrences.getOccurrences(m);
                                    buf.writeInt(freq);
                                    for (int n = 0; n < freq; n++) {
                                        buf.writeInt(newOccurrences.getOffset(m + n));
                                    }
                                    m += freq;
                                }

                                final byte[] bufData = buf.toByteArray();

                                // clear the buf for the next iteration
                                buf.clear();

                                // Write length of node IDs + frequency + offsets (bytes)
                                os.writeFixedInt(bufData.length);

                                // Write the node IDs + frequency + offset
                                os.write(bufData);
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
                            LOG.error("Could not put index data for token '{}' in '{}'", key.term, FileUtils.fileName(index.db.getFile()));
                        }
                    }
                }
            } catch (final LockException e) {
                LOG.warn("Failed to acquire lock for file {}", FileUtils.fileName(index.db.getFile()), e);
            } catch (final IOException e) {
                LOG.warn("IO error for file {}", FileUtils.fileName(index.db.getFile()), e);
            } finally {
                os.clear();
            }
        }
        ngrams.clear();
    }

    @Override
    public void removeCollection(final Collection collection, final DBBroker broker, final boolean reindex) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Dropping NGram index for collection {}", collection.getURI());
        }
        try (final ManagedLock<ReentrantLock> dbLock = lockManager.acquireBtreeWriteLock(index.db.getLockName())) {
            // remove generic index
            final Value value = new NGramQNameKey(collection.getId());
            index.db.removeAll(null, new IndexQuery(IndexQuery.TRUNC_RIGHT, value));
        } catch (final LockException e) {
            LOG.warn("Failed to acquire lock for '{}'", FileUtils.fileName(index.db.getFile()), e);
        } catch (final BTreeException | IOException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    public NodeSet search(final int contextId, final DocumentSet docs, final List<QName> qnames, final String query,
            final String ngram, final XQueryContext context, final NodeSet contextSet, final int axis)
            throws XPathException {
        final List<QName> searchQnames;
        if (qnames == null || qnames.isEmpty()) {
            searchQnames = getDefinedIndexes(context.getBroker(), docs);
        } else {
            searchQnames = qnames;
        }

        final NodeSet result = new ExtArrayNodeSet(docs.getDocumentCount(), 250);
        for (final Iterator<Collection> iter = docs.getCollectionIterator(); iter.hasNext(); ) {
            final int collectionId = iter.next().getId();
            for (final QName qname : searchQnames) {
                final NGramQNameKey key = new NGramQNameKey(collectionId, qname, index.getBrokerPool().getSymbols(), query);
                try (final ManagedLock<ReentrantLock> dbLock = lockManager.acquireBtreeReadLock(index.db.getLockName())) {
                    final SearchCallback cb = new SearchCallback(contextId, query, ngram, docs, contextSet, context, result, axis == NodeSet.ANCESTOR);
                    final int op = query.codePointCount(0, query.length()) < getN() ? IndexQuery.TRUNC_RIGHT : IndexQuery.EQ;
                    index.db.query(new IndexQuery(op, key), cb);
                } catch (final LockException e) {
                    LOG.warn("Failed to acquire lock for '{}'", FileUtils.fileName(index.db.getFile()), e);
                } catch (final IOException | BTreeException e) {
                    LOG.error("{} in '{}'", e.getMessage(), FileUtils.fileName(index.db.getFile()), e);
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
     * @param broker the database broker
     * @param docs   documents
     */
    private List<QName> getDefinedIndexes(final DBBroker broker, final DocumentSet docs) {
        final List<QName> indexes = new ArrayList<>(20);
        for (final Iterator<Collection> i = docs.getCollectionIterator(); i.hasNext(); ) {
            final Collection collection = i.next();
            final IndexSpec idxConf = collection.getIndexConfiguration(broker);
            if (idxConf != null) {
                final Map<?, ?> config = (Map<?, ?>) idxConf.getCustomIndexSpec(NGramIndex.ID);
                if (config != null) {
                    for (final Object name : config.keySet()) {
                        indexes.add((QName) name);
                    }
                }
            }
        }
        return indexes;
    }

    @Override
    public boolean checkIndex(final DBBroker broker) {
        return true;
    }

    @Override
    public Occurrences[] scanIndex(final XQueryContext context, final DocumentSet docs, final NodeSet contextSet, final Map hints) {
        List<QName> qnames = hints == null ? null : (List<QName>) hints.get(QNAMES_KEY);

        //Expects a StringValue
        final Object start = hints == null ? null : hints.get(START_VALUE);

        //Expects a StringValue
        final Object end = hints == null ? null : hints.get(END_VALUE);

        if (qnames == null || qnames.isEmpty()) {
            qnames = getDefinedIndexes(context.getBroker(), docs);
        }

        //TODO : use the IndexWorker.VALUE_COUNT hint, if present, to limit the number of returned entries
        final IndexScanCallback cb = new IndexScanCallback(docs, contextSet);
        for (final QName qname : qnames) {
            for (final Iterator<Collection> i = docs.getCollectionIterator(); i.hasNext(); ) {
                final int collectionId = i.next().getId();
                final IndexQuery query;
                if (start == null) {
                    final Value startRef = new NGramQNameKey(collectionId);
                    query = new IndexQuery(IndexQuery.TRUNC_RIGHT, startRef);
                } else if (end == null) {
                    final Value startRef = new NGramQNameKey(collectionId, qname,
                            index.getBrokerPool().getSymbols(), start.toString().toLowerCase());
                    query = new IndexQuery(IndexQuery.TRUNC_RIGHT, startRef);
                } else {
                    final Value startRef = new NGramQNameKey(collectionId, qname,
                            index.getBrokerPool().getSymbols(), start.toString().toLowerCase());
                    final Value endRef = new NGramQNameKey(collectionId, qname,
                            index.getBrokerPool().getSymbols(), end.toString().toLowerCase());
                    query = new IndexQuery(IndexQuery.BW, startRef, endRef);
                }
                try (final ManagedLock<ReentrantLock> dbLock = lockManager.acquireBtreeReadLock(index.db.getLockName())) {
                    index.db.query(query, cb);
                } catch (final LockException e) {
                    LOG.warn("Failed to acquire lock for '{}'", FileUtils.fileName(index.db.getFile()), e);
                } catch (final IOException | BTreeException e) {
                    LOG.error(e.getMessage(), e);
                } catch (final TerminatedException e) {
                    LOG.warn(e.getMessage(), e);
                }
            }
        }
        return cb.map.values().toArray(new Occurrences[0]);
    }

    //This listener is always the same whatever the document and the mode
    //It should thus be declared static
    private final StreamListener listener = new NGramStreamListener();

    @Override
    public StreamListener getListener() {
        return listener;
    }

    @Override
    public MatchListener getMatchListener(final DBBroker broker, final NodeProxy proxy) {
        return getMatchListener(broker, proxy, null);
    }

    public MatchListener getMatchListener(final DBBroker broker, final NodeProxy proxy, final NGramMatchCallback callback) {
        boolean needToFilter = false;
        Match nextMatch = proxy.getMatches();
        while (nextMatch != null) {
            if (nextMatch.getIndexId().equals(org.exist.indexing.ngram.NGramIndex.ID)) {
                needToFilter = true;
                break;
            }
            nextMatch = nextMatch.getNextMatch();
        }
        if (!needToFilter) {
            return null;
        }
        if (matchListener == null) {
            matchListener = new NGramMatchListener(broker, proxy);
        } else {
            matchListener.reset(broker, proxy);
        }
        matchListener.setMatchCallback(callback);
        return matchListener;
    }

    @Override
    public <T extends IStoredNode> IStoredNode getReindexRoot(final IStoredNode<T> node, final NodePath path,
                                                              final boolean insert, final boolean includeSelf) {
        if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
            return null;
        }

        final IndexSpec indexConf = node.getOwnerDocument().getCollection().getIndexConfiguration(broker);
        if (indexConf != null) {
            final Map<?, ?> config = (Map<?, ?>) indexConf.getCustomIndexSpec(NGramIndex.ID);
            if (config == null) {
                return null;
            }

            boolean reindexRequired = false;
            final int len = node.getNodeType() == Node.ELEMENT_NODE && !includeSelf ? path.length() - 1 : path.length();
            for (int i = 0; i < len; i++) {
                final QName qn = path.getComponent(i);
                if (config.get(qn) != null) {
                    reindexRequired = true;
                    break;
                }
            }
            if (reindexRequired) {
                IStoredNode topMost = null;
                IStoredNode<T> currentNode = node;
                while (currentNode != null) {
                    if (config.get(currentNode.getQName()) != null) {
                        topMost = currentNode;
                    }
                    if (currentNode.getOwnerDocument().getCollection().isTempCollection() && currentNode.getNodeId().getTreeLevel() == 2) {
                        break;
                    }
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
     * @param text the text to tokenize
     *
     * @return the tokenized text
     */
    public String[] tokenize(final String text) {
        final int len = text.codePointCount(0, text.length());
        final int gramSize = index.getN();
        final String[] ngrams = new String[len];
        int next = 0;
        int pos = 0;
        final StringBuilder bld = new StringBuilder(gramSize);
        for (int i = 0; i < len; i++) {
            bld.setLength(0);
            int offset = pos;
            for (int count = 0; count < gramSize && offset < text.length(); count++) {
                final int codepoint = Character.toLowerCase(text.codePointAt(offset));
                offset += Character.charCount(codepoint);
                if (count == 0) {
                    pos = offset;   // advance pos to next character
                }
                bld.appendCodePoint(codepoint);
            }
            ngrams[next++] = bld.toString();
        }
        return ngrams;
    }

    private void indexText(final NodeId nodeId, final QName qname, final String text) {
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

    @Override
    public void setDocument(final DocumentImpl document) {
        setDocument(document, ReindexMode.UNKNOWN);
    }

    @Override
    public void setMode(final ReindexMode newMode) {
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
    public void setDocument(final DocumentImpl document, final ReindexMode newMode) {
        currentDoc = document;
        //config = null;
        while (!contentStack.isEmpty()) {
            contentStack.pop().reset();
        }
        final IndexSpec indexConf = document.getCollection().getIndexConfiguration(broker);
        if (indexConf != null) {
            config = (Map<QName, ?>) indexConf.getCustomIndexSpec(org.exist.indexing.ngram.NGramIndex.ID);
        }
        mode = newMode;
    }

    @Override
    public QueryRewriter getQueryRewriter(final XQueryContext context) {
        return null;
    }

    private class NGramStreamListener extends AbstractStreamListener {

        @Override
        public void startElement(final Txn transaction, final ElementImpl element, final NodePath path) {
            if (config != null && config.get(element.getQName()) != null) {
                final XMLString contentBuf = new XMLString();
                contentStack.push(contentBuf);
            }
            super.startElement(transaction, element, path);
        }

        @Override
        public void attribute(final Txn transaction, final AttrImpl attrib, final NodePath path) {
            if (config != null && config.get(attrib.getQName()) != null) {
                indexText(attrib.getNodeId(), attrib.getQName(), attrib.getValue());
            }
            super.attribute(transaction, attrib, path);
        }

        @Override
        public void endElement(final Txn transaction, final ElementImpl element, final NodePath path) {
            if (config != null && config.get(element.getQName()) != null) {
                final XMLString content = contentStack.pop();
                indexText(element.getNodeId(), element.getQName(), content.toString());
                content.reset();
            }
            super.endElement(transaction, element, path);
        }

        @Override
        public void characters(final Txn transaction, final AbstractCharacterData text, final NodePath path) {
            if (contentStack != null && !contentStack.isEmpty()) {
                for (final Iterator<XMLString> it = contentStack.descendingIterator(); it.hasNext(); ) {
                    it.next().append(text.getXMLString());
                }
            }
            super.characters(transaction, text, path);
        }

        @Override
        public IndexWorker getWorker() {
            return NGramIndexWorker.this;
        }
    }

    private static class NGramMatchListener extends AbstractMatchListener {
        private Match match;
        private Deque<NodeOffset> offsetStack = null;
        private NGramMatchCallback callback = null;
        @SuppressWarnings("unused")
        private NodeProxy root;

        private NGramMatchListener(final DBBroker broker, final NodeProxy proxy) {
            reset(broker, proxy);
        }

        void setMatchCallback(final NGramMatchCallback cb) {
            this.callback = cb;
        }

        protected void reset(final DBBroker broker, final NodeProxy proxy) {
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
                    if (ancestors == null) {
                        ancestors = new ExtArrayNodeSet();
                    }
                    ancestors.add(new NodeProxy(null, proxy.getOwnerDocument(), nextMatch.getNodeId()));
                }
                nextMatch = nextMatch.getNextMatch();
            }
            if (ancestors != null && !ancestors.isEmpty()) {
                for (final NodeProxy p : ancestors) {

                    final int thisLevel = p.getNodeId().getTreeLevel();

                    int startOffset = 0;
                    try {
                        final XMLStreamReader reader = broker.getXMLStreamReader(p, false);
                        while (reader.hasNext()) {
                            final int ev = reader.next();

                            final NodeId otherId = (NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID);
                            if (otherId.equals(proxy.getNodeId())) {
                                break;
                            }
                            final int otherLevel = otherId.getTreeLevel();

                            if (ev == XMLStreamConstants.CHARACTERS) {
                                startOffset += reader.getText().length();
                            } else if (ev == XMLStreamConstants.END_ELEMENT && otherLevel == thisLevel) {
                                // finished element...
                                break;  // exit-while
                            }
                        }
                    } catch (final IOException | XMLStreamException e) {
                        LOG.warn("Problem found while serializing XML: {}", e.getMessage(), e);
                    }
                    if (offsetStack == null) {
                        offsetStack = new ArrayDeque<>();
                    }
                    offsetStack.push(new NodeOffset(p.getNodeId(), startOffset));
                }
            }
        }

        @Override
        public void startElement(final QName qname, final AttrList attribs) throws SAXException {
            Match nextMatch = match;
            // check if there are any matches in the current element
            // if yes, push a NodeOffset object to the stack to track
            // the node contents
            while (nextMatch != null) {
                if (nextMatch.getNodeId().equals(getCurrentNode().getNodeId())) {
                    if (offsetStack == null) {
                        offsetStack = new ArrayDeque<>();
                    }
                    offsetStack.push(new NodeOffset(nextMatch.getNodeId()));
                    break;
                }
                nextMatch = nextMatch.getNextMatch();
            }
            super.startElement(qname, attribs);
        }

        @Override
        public void endElement(final QName qname) throws SAXException {
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
        public void characters(final CharSequence seq) throws SAXException {
            List<Match.Offset> offsets = null;    // a list of offsets to process
            if (offsetStack != null) {
                // walk through the stack to find matches which start in
                // the current string of text
                for (final Iterator<NodeOffset> it = offsetStack.descendingIterator(); it.hasNext(); ) {
                    final NodeOffset no = it.next();
                    final int end = no.offset + seq.length();
                    // scan all matches
                    Match next = match;
                    while (next != null) {
                        if (next.getIndexId().equals(NGramIndex.ID) && next.getNodeId().equals(no.nodeId)) {
                            final int freq = next.getFrequency();
                            for (int j = 0; j < freq; j++) {
                                final Match.Offset offset = next.getOffset(j);
                                if (offset.getOffset() < end &&
                                        offset.getOffset() + offset.getLength() > no.offset) {
                                    // add it to the list to be processed
                                    if (offsets == null) {
                                        offsets = new ArrayList<>(4);
                                    }
                                    // adjust the offset and add it to the list
                                    int start = offset.getOffset() - no.offset;
                                    int len = offset.getLength();
                                    if (start < 0) {
                                        len = len - Math.abs(start);
                                        start = 0;
                                    }
                                    if (start + len > seq.length()) {
                                        len = seq.length() - start;
                                    }
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
                final String s = seq.toString();
                int pos = 0;
                for (final Match.Offset offset : offsets) {
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
                                    new NodeProxy(null, getCurrentNode()));
                        } catch (final XPathException e) {
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
        private final NodeId nodeId;
        private int offset;

        private NodeOffset(final NodeId nodeId) {
            this(nodeId, 0);
        }

        private NodeOffset(final NodeId nodeId, final int offset) {
            this.nodeId = nodeId;
            this.offset = offset;
        }
    }

    private record QNameTerm(QName qname, String term) implements Comparable<QNameTerm> {

        @Override
            public int compareTo(final QNameTerm other) {
                final int cmp = qname.compareTo(other.qname);
                if (cmp == 0) {
                    return term.compareTo(other.term);
                }
                return cmp;
            }
        }

    private static class NGramQNameKey extends Value {
        private static final int COLLECTION_ID_OFFSET = 1;
        private static final int NAMETYPE_OFFSET = COLLECTION_ID_OFFSET + Collection.LENGTH_COLLECTION_ID; // 5
        private static final int NAMESPACE_OFFSET = NAMETYPE_OFFSET + ElementValue.LENGTH_TYPE; // 6
        private static final int LOCALNAME_OFFSET = NAMESPACE_OFFSET + SymbolTable.LENGTH_NS_URI; // 8
        private static final int NGRAM_OFFSET = LOCALNAME_OFFSET + SymbolTable.LENGTH_LOCAL_NAME; // 10

        NGramQNameKey(final int collectionId) {
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

        NGramQNameKey(final int collectionId, final QName qname, final SymbolTable symbols, final String ngram) {
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
        @SuppressWarnings("unused")
        private final String query;
        private final String ngram;
        private final DocumentSet docs;
        private final NodeSet contextSet;
        private final XQueryContext context;
        private final NodeSet resultSet;
        private final boolean returnAncestor;

        SearchCallback(final int contextId, final String query, final String ngram, final DocumentSet docs,
                       final NodeSet contextSet, final XQueryContext context, final NodeSet result,
                       final boolean returnAncestor) {
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
        public boolean indexInfo(final Value key, final long pointer) throws TerminatedException {
            final String ngram = new String(key.getData(), NGramQNameKey.NGRAM_OFFSET, key.getLength() - NGramQNameKey.NGRAM_OFFSET, UTF_8);

            try {
                final VariableByteInput is = index.db.getAsStream(pointer);
                //Does the token already has data in the index ?
                if (is == null) {
                    return true;
                }

                while (is.available() > 0) {
                    final int storedDocId = is.readInt();
                    final byte nameType = is.readByte();
                    final int occurrences = is.readInt();
                    //Read (variable) length of node IDs + frequency + offsets
                    final int length = is.readFixedInt();
                    final DocumentImpl storedDocument = docs.getDoc(storedDocId);

                    //Exit if the document is not concerned
                    if (storedDocument == null) {
                        is.skipBytes(length);
                        continue;
                    }

                    NodeId previous = null;
                    for (int m = 0; m < occurrences; m++) {
                        final NodeId nodeId = index.getBrokerPool().getNodeFactory().createFromStream(previous, is);
                        previous = nodeId;
                        final int freq = is.readInt();
                        final NodeProxy nodeProxy = new NodeProxy(null, storedDocument, nodeId, nameTypeToNodeType(nameType));
                        // if a context set is specified, we can directly check if the
                        // matching node is a descendant of one of the nodes
                        // in the context set.
                        if (contextSet != null) {
                            final int sizeHint = contextSet.getSizeHint(storedDocument);
                            if (returnAncestor) {
                                final NodeProxy parentNode = contextSet.parentWithChild(nodeProxy, false, true, NodeProxy.UNKNOWN_NODE_LEVEL);
                                if (parentNode != null) {
                                    readMatches(ngram, is, nodeId, freq, parentNode);
                                    resultSet.add(parentNode, sizeHint);
                                } else {
                                    is.skip(freq);
                                }
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
            } catch (final IOException e) {
                LOG.error(e.getMessage(), e);
                return true;
            }
        }

        private short nameTypeToNodeType(final byte nameType) {
            return switch (nameType) {
                case ElementValue.ELEMENT -> Node.ELEMENT_NODE;
                case ElementValue.ATTRIBUTE -> Node.ATTRIBUTE_NODE;
                default -> NodeProxy.UNKNOWN_NODE_TYPE;
            };
        }

        private void readMatches(final String current, final VariableByteInput is, final NodeId nodeId, final int freq,
                                 final NodeProxy parentNode) throws IOException {
            int diff = 0;
            if (current.length() > ngram.length()) {
                diff = current.lastIndexOf(ngram);
            }
            final Match match = new NGramMatch(contextId, nodeId, ngram, freq);
            for (int n = 0; n < freq; n++) {
                int offset = is.readInt();
                if (diff > 0) {
                    offset += diff;
                }
                match.addOffset(offset, ngram.length());
            }
            parentNode.addMatch(match);
        }
    }

    private final class IndexScanCallback implements BTreeCallback {
        private final DocumentSet docs;
        private NodeSet contextSet;
        private final Map<String, Occurrences> map = new TreeMap<>();

        IndexScanCallback(final DocumentSet docs, final NodeSet contextSet) {
            this.docs = docs;
            this.contextSet = contextSet;
        }

        @Override
        public boolean indexInfo(final Value key, final long pointer) {
            final String term = new String(key.getData(), NGramQNameKey.NGRAM_OFFSET, key.getLength() - NGramQNameKey.NGRAM_OFFSET, UTF_8);

            final VariableByteInput is;
            try {
                is = index.db.getAsStream(pointer);
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);
                return true;
            }

            try {
                while (is.available() > 0) {
                    boolean docAdded = false;
                    final int storedDocId = is.readInt();
                    @SuppressWarnings("unused") final byte nameType = is.readByte();
                    final int occurrences = is.readInt();
                    //Read (variable) length of node IDs + frequency + offsets
                    final int length = is.readFixedInt();
                    final DocumentImpl storedDocument = docs.getDoc(storedDocId);

                    //Exit if the document is not concerned
                    if (storedDocument == null) {
                        is.skipBytes(length);
                        continue;
                    }

                    NodeId previous = null;
                    for (int m = 0; m < occurrences; m++) {
                        final NodeId nodeId = index.getBrokerPool().getNodeFactory().createFromStream(previous, is);
                        previous = nodeId;
                        final int freq = is.readInt();
                        is.skip(freq);
                        boolean include = true;
                        //TODO : revisit
                        if (contextSet != null) {
                            final NodeProxy parentNode = contextSet.parentWithChild(storedDocument, nodeId, false, true);
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
            } catch (final IOException e) {
                LOG.error("{} in '{}'", e.getMessage(), FileUtils.fileName(index.db.getFile()), e);
            }
            return true;
        }
    }
}
