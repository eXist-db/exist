/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 *  $Id$
 */
package org.exist.indexing.impl;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.AttrImpl;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ElementImpl;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.Match;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.StoredNode;
import org.exist.dom.SymbolTable;
import org.exist.dom.TextImpl;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
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
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
import org.exist.indexing.*;
import org.exist.util.serializer.AttrList;
import org.exist.xquery.Constants;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.XQueryContext;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamException;

/**
 *
 * Each index entry maps a key (collectionId, ngram) to a list of occurrences, which has the
 * following structure:
 *
 * <pre>[docId : int, nameType: byte, occurrenceCount: int, entrySize: long, [id: NodeId, offset: int, ...]* ]</pre>
 */
public class NGramIndexWorker implements IndexWorker {

    private static final Logger LOG = Logger.getLogger(NGramIndexWorker.class);

    private final static String INDEX_ELEMENT = "ngram";

    private static final String QNAME_ATTR = "qname";

    private static final byte IDX_QNAME = 0;
    private static final byte IDX_GENERIC = 1;

    private int mode = 0;
    private NGramIndex index;
    private char[] buf = new char[1024];
    private int currentChar = 0;
    private DocumentImpl currentDoc = null;

    private IndexController controller;
    private Map ngrams = new TreeMap();
    private VariableByteOutputStream os = new VariableByteOutputStream(7);

    private NGramMatchListener matchListener = null;

    public NGramIndexWorker(NGramIndex index) {
        this.index = index;
        Arrays.fill(buf, ' ');
    }

    public String getIndexId() {
        return NGramIndex.ID;
    }

    public String getIndexName() {
        return index.getIndexName();
    }

    public Index getIndex() {
        return index;
    }

    public int getN() {
        return index.getN();
    }

    public Object configure(IndexController controller, NodeList configNodes, Map namespaces) throws DatabaseConfigurationException {
        this.controller = controller;
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
                NGramIndexConfig config = new NGramIndexConfig(namespaces, qname);
                map.put(config.getQName(), config);
            }
        }
        return map;
    }

    public void flush() {
        switch (mode) {
            case StreamListener.STORE :
                saveIndex();
                break;
            case StreamListener.REMOVE_ALL_NODES :
            case StreamListener.REMOVE_NODES :
                dropIndex(mode);
                break;
        }
    }

    private void saveIndex() {
        if (ngrams.size() == 0)
            return;
        for (Iterator iterator = ngrams.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            QNameTerm key = (QNameTerm) entry.getKey();
            OccurrenceList occurences = (OccurrenceList) entry.getValue();
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
                    LOG.error("IOException while writing fulltext index: " + e.getMessage(), e);
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
            Lock lock = index.db.getLock();
            try {
                lock.acquire(Lock.WRITE_LOCK);

                NGramQNameKey value = new NGramQNameKey(currentDoc.getCollection().getId(), key.qname,
                        currentDoc.getBroker().getSymbols(), key.term);
                index.db.append(value, data);
            } catch (LockException e) {
                LOG.warn("Failed to acquire lock for file " + index.db.getFile().getName(), e);
            } catch (IOException e) {
                LOG.warn("IO error for file " + index.db.getFile().getName(), e);
            } catch (ReadOnlyException e) {
                LOG.warn("Read-only error for file " + index.db.getFile().getName(), e);
            } finally {
                lock.release(Lock.WRITE_LOCK);
                os.clear();
            }
        }
        ngrams.clear();
    }

    private void dropIndex(int mode) {
        if (ngrams.size() == 0)
            return;
        for (Iterator iterator = ngrams.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            QNameTerm key = (QNameTerm) entry.getKey();
            OccurrenceList occurencesList = (OccurrenceList) entry.getValue();
            occurencesList.sort();
            os.clear();

            Lock lock = index.db.getLock();
            try {
                lock.acquire(Lock.WRITE_LOCK);

                NGramQNameKey value = new NGramQNameKey(currentDoc.getCollection().getId(), key.qname,
                        currentDoc.getBroker().getSymbols(), key.term);
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
                        if (mode == StreamListener.REMOVE_ALL_NODES) {
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
                                    index.db.getFile().getName() + "'");
                        }
                    }
                }
            } catch (LockException e) {
                LOG.warn("Failed to acquire lock for file " + index.db.getFile().getName(), e);
            } catch (IOException e) {
                LOG.warn("IO error for file " + index.db.getFile().getName(), e);
            } catch (ReadOnlyException e) {
                LOG.warn("Read-only error for file " + index.db.getFile().getName(), e);
            } finally {
                lock.release(Lock.WRITE_LOCK);
                os.clear();
            }
        }
        ngrams.clear();
    }

    public void removeCollection(Collection collection) {
        if (LOG.isDebugEnabled())
            LOG.debug("Dropping NGram index for collection " + collection.getURI());
        final Lock lock = index.db.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            // remove generic index
            Value value = new NGramQNameKey(collection.getId());
            index.db.removeAll(null, new IndexQuery(IndexQuery.TRUNC_RIGHT, value));
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock for '" + index.db.getFile().getName() + "'", e);
        } catch (BTreeException e) {
            LOG.error(e.getMessage(), e);
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        } finally {
            lock.release(Lock.WRITE_LOCK);
        }
    }

    public NodeSet search(int contextId, DocumentSet docs, List qnames, String ngram, XQueryContext context, NodeSet contextSet, int axis)
        throws TerminatedException {
        if (qnames == null || qnames.isEmpty())
            qnames = getDefinedIndexes(context.getBroker(), docs);
        final NodeSet result = new ExtArrayNodeSet(docs.getLength(), 250);
        for (Iterator iter = docs.getCollectionIterator(); iter.hasNext();) {
            final short collectionId = ((org.exist.collections.Collection) iter.next()).getId();
            for (int i = 0; i < qnames.size(); i++) {
                QName qname = (QName) qnames.get(i);
                NGramQNameKey key = new NGramQNameKey(collectionId, qname, context.getBroker().getSymbols(), ngram);
                final Lock lock = index.db.getLock();
                try {
                    lock.acquire(Lock.READ_LOCK);
                    SearchCallback cb = new SearchCallback(contextId, ngram, docs, contextSet, context, result, axis == NodeSet.ANCESTOR);
                    int op = ngram.length() < getN() ? IndexQuery.TRUNC_RIGHT : IndexQuery.EQ;
                    final IndexQuery query = new IndexQuery(op, key);
                    index.db.query(query, cb);
                } catch (LockException e) {
                    LOG.warn("Failed to acquire lock for '" + index.db.getFile().getName() + "'", e);
                } catch (IOException e) {
                    LOG.error(e.getMessage() + " in '" + index.db.getFile().getName() + "'", e);
                } catch (BTreeException e) {
                    LOG.error(e.getMessage() + " in '" + index.db.getFile().getName() + "'", e);
                } finally {
                    lock.release(Lock.READ_LOCK);
                }
            }
        }
        return result;
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
                Map config = (Map) idxConf.getCustomIndexSpec(NGramIndex.ID);
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

    public Occurrences[] scanIndex(DocumentSet docs) {
        final IndexScanCallback cb = new IndexScanCallback(docs);
        final Lock lock = index.db.getLock();
		for (Iterator i = docs.getCollectionIterator(); i.hasNext();) {
            final short collectionId = ((Collection) i.next()).getId();
            Value ref = new NGramQNameKey(collectionId);
            final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
			try {
				lock.acquire(Lock.READ_LOCK);
				index.db.query(query, cb);
			} catch (LockException e) {
                LOG.warn("Failed to acquire lock for '" + index.db.getFile().getName() + "'", e);
			} catch (IOException e) {
                LOG.error(e.getMessage(), e);
			} catch (BTreeException e) {
                LOG.error(e.getMessage(), e);
			} catch (TerminatedException e) {
                LOG.warn(e.getMessage(), e);
            } finally {
				lock.release(Lock.READ_LOCK);
			}
		}
		Occurrences[] result = new Occurrences[cb.map.size()];
		return (Occurrences[]) cb.map.values().toArray(result);
    }

    public StreamListener getListener(int mode, DocumentImpl document) {
        setDocument(document, mode);
        return new NGramStreamListener();
    }

    public MatchListener getMatchListener(NodeProxy proxy) {
        boolean needToFilter = false;
        Match nextMatch = proxy.getMatches();
        while (nextMatch != null) {
            if (nextMatch.getIndexId() == NGramIndex.ID) {
                needToFilter = true;
                break;
            }
            nextMatch = nextMatch.getNextMatch();
        }
        if (!needToFilter)
            return null;
        if (matchListener == null)
            matchListener = new NGramMatchListener(proxy);
        else
            matchListener.reset(proxy);
        return matchListener;
    }

    public StoredNode getReindexRoot(StoredNode node, NodePath path, boolean includeSelf) {
        if (node.getNodeType() == Node.ATTRIBUTE_NODE)
            return null;
        IndexSpec indexConf = node.getDocument().getCollection().getIndexConfiguration(node.getDocument().getBroker());
        if (indexConf != null) {
            Map config = (Map) indexConf.getCustomIndexSpec(NGramIndex.ID);
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
                StoredNode top = null;
                StoredNode next = node;
                while (next != null) {
                    if (config.get(next.getQName()) != null)
                        top = next;
                    next = (StoredNode) next.getParentNode();
                }
                return top;
            }
        }
        return null;
    }

    public String[] tokenize(CharSequence text) {
        int len = text.length();
        int gramSize = index.getN();
        String ngrams[] = new String[len];
        int next = 0;
        for (int i = 0; i < len; i++) {
            checkBuffer();
            for (int j = 0; j < gramSize && i + j < len; j++) {
                // TODO: case sensitivity should be configurable
                buf[currentChar + j] = Character.toLowerCase(text.charAt(i + j));
            }
            ngrams[next++] = new String(buf, currentChar, gramSize);
            currentChar += gramSize;
        }
        return ngrams;
    }

    public String[] getDistinctNGrams(CharSequence text) {
        int ngramSize = index.getN();
        int count = text.length() / ngramSize;
        int remainder = text.length() % ngramSize;
        String[] n = new String[(remainder > 0 ? count + 1 : count)];
        int pos = 0;
        for (int i = 0; i < count; i++) {
            char ch[] = new char[ngramSize];
            for (int j = 0; j < ngramSize; j++) {
                ch[j] = text.charAt(pos++);
            }
            n[i] = new String(ch);
        }
        if (remainder > 0) {
            char ch[] = new char[remainder];
            for (int i = 0; i < remainder; i++)
                ch[i] = text.charAt(pos++);
            n[count] = new String(ch);
        }
        return n;
    }

    private void indexText(NodeId nodeId, QName qname, CharSequence text) {
        String ngram[] = tokenize(text);
        int len = ngram.length;
        for (int i = 0; i < len; i++) {
            QNameTerm key = new QNameTerm(qname, ngram[i]);
            OccurrenceList list = (OccurrenceList) ngrams.get(key);
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

    private Map config;
    private Stack contentStack = null;

    public void setDocument(DocumentImpl document, int newMode) {
        currentDoc = document;
        //config = null;
        contentStack = null;
        IndexSpec indexConf = document.getCollection().getIndexConfiguration(document.getBroker());
        if (indexConf != null)
            config = (Map) indexConf.getCustomIndexSpec(NGramIndex.ID);
        mode = newMode;
    }

    private class NGramStreamListener extends AbstractStreamListener {

        public NGramStreamListener() {
        }

        public void startElement(Txn transaction, ElementImpl element, NodePath path) {
            if (config != null && config.get(element.getQName()) != null) {
                if (contentStack == null) contentStack = new Stack();
                XMLString contentBuf = new XMLString();
                contentStack.push(contentBuf);
            }
            super.startElement(transaction, element, path);
        }

        public void attribute(Txn transaction, AttrImpl attrib, NodePath path) {
            if (config != null && config.get(attrib.getQName()) != null) {
                indexText(attrib.getNodeId(), attrib.getQName(), attrib.getValue());
            }
            super.attribute(transaction, attrib, path);
        }

        public void endElement(Txn transaction, ElementImpl element, NodePath path) {
            if (config != null && config.get(element.getQName()) != null) {
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
        	return NGramIndexWorker.this;
        }
    }

    private class NGramMatchListener extends AbstractMatchListener {

        private Match match;
        private Stack offsetStack = null;

        public NGramMatchListener(NodeProxy proxy) {
            reset(proxy);
        }
        
        protected void reset(NodeProxy proxy) {
            this.match = proxy.getMatches();

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
                    ancestors.add(new NodeProxy(proxy.getDocument(), nextMatch.getNodeId()));
                }
                nextMatch = nextMatch.getNextMatch();
            }
            if (ancestors != null && !ancestors.isEmpty()) {
                for (Iterator i = ancestors.iterator(); i.hasNext(); ) {
                    NodeProxy p = (NodeProxy) i.next();
                    int startOffset = 0;
                    try {
                        XMLStreamReader reader = proxy.getDocument().getBroker().getXMLStreamReader(p, false);
                        while (reader.hasNext()) {
                            int ev = reader.next();
                            NodeId nodeId = (NodeId) reader.getProperty(EmbeddedXMLStreamReader.PROPERTY_NODE_ID);
                            if (nodeId.equals(proxy.getNodeId()))
                                break;
                            if (ev == XMLStreamReader.CHARACTERS)
                                startOffset += reader.getText().length();
                        }
                    } catch (IOException e) {
                        LOG.warn("Problem found while serializing XML: " + e.getMessage(), e);
                    } catch (XMLStreamException e) {
                        LOG.warn("Problem found while serializing XML: " + e.getMessage(), e);
                    }
                    if (offsetStack == null)
                        offsetStack = new Stack();
                    offsetStack.push(new NodeOffset(p.getNodeId(), startOffset));
                }
            }
        }

        public void startElement(QName qname, AttrList attribs) throws SAXException {
            Match nextMatch = match;
            // check if there are any matches in the current element
            // if yes, push a NodeOffset object to the stack to track
            // the node contents
            while (nextMatch != null) {
                if (nextMatch.getNodeId().equals(getCurrentNode().getNodeId())) {
                    if (offsetStack == null)
                        offsetStack = new Stack();
                    offsetStack.push(new NodeOffset(nextMatch.getNodeId()));
                    break;
                }
                nextMatch = nextMatch.getNextMatch();
            }
            super.startElement(qname, attribs);
        }

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

        public void characters(CharSequence seq) throws SAXException {
            List offsets = null;    // a list of offsets to process
            if (offsetStack != null) {
                // walk through the stack to find matches which start in
                // the current string of text
                for (int i = 0; i < offsetStack.size(); i++) {
                    NodeOffset no = (NodeOffset) offsetStack.get(i);
                    int end = no.offset + seq.length();
                    // scan all matches
                    Match next = match;
                    while (next != null) {
                        if (next.getNodeId().equals(no.nodeId)) {
                            int freq = next.getFrequency();
                            for (int j = 0; j < freq; j++) {
                                Match.Offset offset = next.getOffset(j);
                                if (offset.getOffset() < end &&
                                    offset.getOffset() + offset.getLength() > no.offset) {
                                    // add it to the list to be processed
                                    if (offsets == null) {
                                        offsets = new ArrayList(4);
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
                for (int i = 0; i < offsets.size(); i++) {
                    Match.Offset offset = (Match.Offset) offsets.get(i);
                    if (offset.getOffset() > pos) {
                        super.characters(s.substring(pos, pos + (offset.getOffset() - pos)));
                    }
                    super.startElement(MATCH_ELEMENT, null); 
                    super.characters(s.substring(offset.getOffset(), offset.getOffset() + offset.getLength()));
                    super.endElement(MATCH_ELEMENT);
                    pos = offset.getOffset() + offset.getLength();
                }
                if (pos < s.length()) {
                    super.characters(s.substring(pos));
                }
            } else
                super.characters(seq);
        }

    }

    private class NodeOffset {
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
    
    private class QNameTerm implements Comparable {

        QName qname;
        String term;

        public QNameTerm(QName qname, String term) {
            this.qname = qname;
            this.term = term;
        }

        public int compareTo(Object o) {
            QNameTerm other = (QNameTerm) o;
            int cmp = qname.compareTo(other.qname);
            if (cmp == 0)
                return term.compareTo(other.term);
            else
                return cmp;
        }
    }

    private class NGramQNameKey extends Value {

        public NGramQNameKey(short collectionId) {
            len = 3;
            data = new byte[len];
            data[0] = IDX_QNAME;
            ByteConversion.shortToByte(collectionId, data, 1);
        }

        public NGramQNameKey(short collectionId, QName qname, SymbolTable symbols) {
            len = 8;
            data = new byte[len];
            data[0] = IDX_QNAME;
            ByteConversion.shortToByte(collectionId, data, 1);
            final short namespaceId = symbols.getNSSymbol(qname.getNamespaceURI());
			final short localNameId = symbols.getSymbol(qname.getLocalName());
            data[3] = qname.getNameType();
            ByteConversion.shortToByte(namespaceId, data, 4);
			ByteConversion.shortToByte(localNameId, data, 6);
        }

        public NGramQNameKey(short collectionId, QName qname, SymbolTable symbols, String ngram) {
            len = UTF8.encoded(ngram) + 8;
            data = new byte[len];
            data[0] = IDX_QNAME;
            ByteConversion.shortToByte(collectionId, data, 1);
            final short namespaceId = symbols.getNSSymbol(qname.getNamespaceURI());
			final short localNameId = symbols.getSymbol(qname.getLocalName());
            data[3] = qname.getNameType();
            ByteConversion.shortToByte(namespaceId, data, 4);
			ByteConversion.shortToByte(localNameId, data, 6);

            UTF8.encode(ngram, data, 8);
        }
    }

    private final class SearchCallback implements BTreeCallback {

        private int contextId;
        private String query;
        private DocumentSet docs;
        private NodeSet contextSet;
        private XQueryContext context;
        private NodeSet resultSet;
        private boolean returnAncestor;

        public SearchCallback(int contextId, String query, DocumentSet docs, NodeSet contextSet, XQueryContext context, NodeSet result, boolean returnAncestor) {
            this.contextId = contextId;
            this.query = query;
            this.docs = docs;
            this.context = context;
            this.contextSet = contextSet;
            this.resultSet = result;
            this.returnAncestor = returnAncestor;
        }

        public boolean indexInfo(Value key, long pointer) throws TerminatedException {
            String ngram;
            try {
                ngram = new String(key.getData(), 8, key.getLength() - 8, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                LOG.error(e.getMessage(), e);
                return true;
            }
            VariableByteInput is = null;
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
                        NodeProxy storedNode = new NodeProxy(storedDocument, nodeId);

                        // if a context set is specified, we can directly check if the
                        // matching node is a descendant of one of the nodes
                        // in the context set.
                        if (contextSet != null) {
                            int sizeHint = contextSet.getSizeHint(storedDocument);
                            if (returnAncestor) {
                                NodeProxy parentNode = contextSet.parentWithChild(storedNode, false, true, NodeProxy.UNKNOWN_NODE_LEVEL);
                                Match match = new NGramMatch(contextId, nodeId, ngram, freq);
                                for (int n = 0; n < freq; n++) {
                                    match.addOffset(is.readInt(), query.length());
                                }
                                if (parentNode != null) {
                                    parentNode.addMatch(match);
                                    resultSet.add(parentNode, sizeHint);
                                } else
                                    is.skip(freq);
                            } else {
                                Match match = new NGramMatch(contextId, nodeId, ngram, freq);
                                for (int n = 0; n < freq; n++) {
                                    match.addOffset(is.readInt(), ngram.length());
                                }
                                storedNode.addMatch(match);
                                resultSet.add(storedNode, sizeHint);
                            }
                            // otherwise, we add all text nodes without check
                        } else {
                            Match match = new NGramMatch(contextId, nodeId, ngram, freq);
                            for (int n = 0; n < freq; n++) {
                                match.addOffset(is.readInt(), ngram.length());
                            }
                            storedNode.addMatch(match);
                            resultSet.add(storedNode, Constants.NO_SIZE_HINT);
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
    }

    private final class IndexScanCallback implements BTreeCallback {

		private DocumentSet docs;
		private Map map = new TreeMap();

		IndexScanCallback(DocumentSet docs) {
			this.docs = docs;
		}

		/* (non-Javadoc)
		 * @see org.dbxml.core.filer.BTreeCallback#indexInfo(org.dbxml.core.data.Value, long)
		 */
		public boolean indexInfo(Value key, long pointer) throws TerminatedException {
            String term;
            try {
                term = new String(key.getData(), 8, key.getLength() - 8, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                LOG.error(e.getMessage(), e);
                return true;
            }
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
                        Occurrences oc = (Occurrences) map.get(term);
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
			} catch(IOException e) {
                LOG.error(e.getMessage() + " in '" + index.db.getFile().getName() + "'", e);
			}
			return true;
		}
	}

    public class NGramMatch extends Match {

        public NGramMatch(int contextId, NodeId nodeId, String matchTerm) {
            super(contextId, nodeId, matchTerm);
        }

        public NGramMatch(int contextId, NodeId nodeId, String matchTerm, int frequency) {
            super(contextId, nodeId, matchTerm, frequency);
        }

        public NGramMatch(Match match) {
            super(match);
        }

        public Match createInstance(int contextId, NodeId nodeId, String matchTerm) {
            return new NGramMatch(contextId, nodeId, matchTerm);
        }

        public Match newCopy() {
            return new NGramMatch(this);
        }

        public String getIndexId() {
            return NGramIndex.ID;
        }
    }
}