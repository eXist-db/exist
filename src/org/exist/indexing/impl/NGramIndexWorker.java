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

import org.exist.dom.*;
import org.exist.indexing.*;
import org.exist.util.*;
import org.exist.storage.index.BFile;
import org.exist.storage.txn.Txn;
import org.exist.storage.NodePath;
import org.exist.storage.IndexSpec;
import org.exist.storage.OccurrenceList;
import org.exist.storage.ElementValue;
import org.exist.storage.btree.Value;
import org.exist.storage.btree.IndexQuery;
import org.exist.storage.btree.BTreeException;
import org.exist.storage.btree.BTreeCallback;
import org.exist.storage.lock.Lock;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.io.VariableByteInput;
import org.exist.numbering.NodeId;
import org.exist.collections.*;
import org.exist.collections.Collection;
import org.exist.xquery.Constants;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.TerminatedException;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.regex.Pattern;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

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

    private int mode = 0;

    private NGramIndex index;
    private char[] buf = new char[1024];
    private int currentChar = 0;
    private DocumentImpl currentDoc = null;
    private IndexController controller;
    
    private Map ngrams = new TreeMap();
    private VariableByteOutputStream os = new VariableByteOutputStream(7);

    public NGramIndexWorker(NGramIndex index) {
        this.index = index;
        Arrays.fill(buf, ' ');
    }

    public String getIndexId() {
        return NGramIndex.ID;
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
            case StreamListener.REMOVE :
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

                NGramKey value = new NGramKey(currentDoc.getCollection().getId(), key.term);
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

                NGramKey value = new NGramKey(currentDoc.getCollection().getId(), key.term);
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
                        if (mode == StreamListener.REMOVE) {
                            // skip them
                            is.skipBytes(length);
                        } else {
                            // removing nodes: need to filter out the node ids to be removed
                            // feed the new list with the GIDs

                            NodeId previous = null;
                            OccurrenceList newOccurrences = new OccurrenceList();
                            for (int m = 0; m < occurrences; m++) {
                                NodeId nodeId = index.pool.getNodeFactory().createFromStream(previous, is);
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
            Value value = new NGramKey(collection.getId());
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

    public NodeSet search(DocumentSet docs, String ngram, XQueryContext context, NodeSet contextSet, int axis) throws TerminatedException {
        final NodeSet result = new ExtArrayNodeSet(docs.getLength(), 250);
        for (Iterator iter = docs.getCollectionIterator(); iter.hasNext();) {
            final short collectionId = ((org.exist.collections.Collection) iter.next()).getId();
            NGramKey key = new NGramKey(collectionId, ngram);
            final Lock lock = index.db.getLock();
			try {
				lock.acquire(Lock.READ_LOCK);
                VariableByteInput is = index.db.getAsStream(key);
                //Does the token already has data in the index ?
				if (is == null)
					continue;
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
                        NodeId nodeId = index.pool.getNodeFactory().createFromStream(previous, is);
                        previous = nodeId;
                        int freq = is.readInt();
                        NodeProxy storedNode;
                        switch (nameType) {
                            case ElementValue.ATTRIBUTE :
                                storedNode = new NodeProxy(storedDocument, nodeId, Node.ATTRIBUTE_NODE);
                                break;
                            case ElementValue.ELEMENT :
                                storedNode = new NodeProxy(storedDocument, nodeId, Node.TEXT_NODE);
                                break;
                            default :
                                throw new IllegalArgumentException("Invalid section type in '" + index.db.getFile().getName() + "'");
                        }
                        // if a context set is specified, we can directly check if the
						// matching text node is a descendant of one of the nodes
						// in the context set.
						if (contextSet != null) {
                            NodeProxy parent;
                            switch(nameType) {
                                case ElementValue.ATTRIBUTE :
                                    if (contextSet instanceof VirtualNodeSet) {
                                        parent = contextSet.parentWithChild(storedNode, false, true, NodeProxy.UNKNOWN_NODE_LEVEL);
                                        if (parent != null && !parent.getNodeId().equals(storedNode.getNodeId()))
                                            parent = null;
                                    } else
                                        parent = contextSet.get(storedNode);
                                    break;
                                case ElementValue.ELEMENT :
                                    parent = contextSet.parentWithChild(storedNode, false, true, NodeProxy.UNKNOWN_NODE_LEVEL);
                                    break;
                                default :
                                    throw new IllegalArgumentException("Invalid section type in '" + index.db.getFile().getName() + "'");
                            }
                            if (parent != null) {
                                Match match = new Match(nodeId, ngram, freq);
                                for (int n = 0; n < freq; n++) {
                                    match.addOffset(is.readInt(), ngram.length());
                                }
                                if (axis == NodeSet.ANCESTOR) {
                                    parent.addMatch(match);
                                    int sizeHint = contextSet.getSizeHint(storedDocument);
                                    result.add(parent, sizeHint);
                                } else {
                                    storedNode.addMatch(match);
                                    int sizeHint = contextSet.getSizeHint(storedDocument);
                                    result.add(storedNode, sizeHint);
                                }
                            } else {
							    is.skip(freq);
                            }
						// otherwise, we add all text nodes without check
						} else {
                            Match match = new Match(nodeId, ngram, freq);
                            for (int n = 0; n < freq; n++) {
                                match.addOffset(is.readInt(), ngram.length());
                            }
                            storedNode.addMatch(match);
							result.add(storedNode, Constants.NO_SIZE_HINT);
						}
						context.proceed();
					}
                }
            } catch (LockException e) {
                LOG.warn("Failed to acquire lock for '" + index.db.getFile().getName() + "'", e);
            } catch (IOException e) {
                LOG.error(e.getMessage() + " in '" + index.db.getFile().getName() + "'", e);
            } finally {
                lock.release(Lock.READ_LOCK);
            }
        }
        return result;
    }

    public Occurrences[] scanIndex(DocumentSet docs) {
        final IndexScanCallback cb = new IndexScanCallback(docs);
        final Lock lock = index.db.getLock();
		for (Iterator i = docs.getCollectionIterator(); i.hasNext();) {
            final short collectionId = ((Collection) i.next()).getId();
            Value ref = new NGramKey(collectionId);
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
        this.currentDoc = document;
        this.mode = mode;
        return new NGramStreamListener(document, mode);
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

    private class NGramStreamListener extends AbstractStreamListener {

        private Map config;
        private Stack contentStack = null;
        
        public NGramStreamListener(DocumentImpl document, int mode) {
            setDocument(document, mode);
        }

        public void setDocument(DocumentImpl document, int newMode) {
            currentDoc = document;
            config = null;
            contentStack = null;
            IndexSpec indexConf = document.getCollection().getIndexConfiguration(document.getBroker());
            if (indexConf != null)
                config = (Map) indexConf.getCustomIndexSpec(NGramIndex.ID);
            mode = newMode;
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

    private class NGramKey extends Value {

        public NGramKey(short collectionId) {
            len = 2;
            data = new byte[len];
            ByteConversion.shortToByte(collectionId, data, 0);
        }

        public NGramKey(short collectionId, String ngram) {
            len = UTF8.encoded(ngram) + 2;
            data = new byte[len];
            ByteConversion.shortToByte(collectionId, data, 0);
            UTF8.encode(ngram, data, 2);
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
                term = new String(key.getData(), 2, key.getLength() - 2, "UTF-8");
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
                        NodeId nodeId = index.pool.getNodeFactory().createFromStream(previous, is);
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
}