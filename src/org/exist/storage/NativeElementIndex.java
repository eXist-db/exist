/*
 * eXist Open Source Native XML Database Copyright (C) 2001-03, Wolfgang M.
 * Meier (wolfgang@exist-db.org)
 * 
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Library General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any
 * later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Library General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * 
 * $Id$
 */
package org.exist.storage;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.dbxml.core.DBException;
import org.dbxml.core.data.Value;
import org.dbxml.core.filer.BTreeException;
import org.dbxml.core.indexer.IndexQuery;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.SymbolTable;
import org.exist.dom.XMLUtil;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.io.VariableByteArrayInput;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.store.BFile;
import org.exist.storage.store.StorageAddress;
import org.exist.util.ByteConversion;
import org.exist.util.Configuration;
import org.exist.util.FastQSort;
import org.exist.util.Lock;
import org.exist.util.LockException;
import org.exist.util.Occurrences;
import org.exist.util.ProgressIndicator;
import org.exist.util.ReadOnlyException;
import org.exist.xquery.NodeSelector;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.XQueryContext;
import org.w3c.dom.Node;

/** The indexing occurs in this class. That is, during the loading of a document
 * into the database, the process of associating a long gid with each element,
 * and the subsequent storing of the {@link NodeProxy} on disk.
 * {@link reIndex} is the main method.
 */
public class NativeElementIndex extends ElementIndex {

    private static Logger LOG = Logger.getLogger(NativeElementIndex.class
            .getName());

    protected BFile dbElement;

    private VariableByteOutputStream os = new VariableByteOutputStream();

    public NativeElementIndex(DBBroker broker, Configuration config,
            BFile dbElement) {
        super(broker, config);
        this.dbElement = dbElement;
    }

    public void addRow(QName qname, NodeProxy proxy) {
        ArrayList buf;
        if (elementIds.containsKey(qname))
            buf = (ArrayList) elementIds.get(qname);
        else {
            buf = new ArrayList(50);
            elementIds.put(qname, buf);
        }
        buf.add(proxy);
    }

    public NodeSet getAttributesByName(DocumentSet docs, QName qname) {
        qname.setLocalName(qname.getLocalName());
        NodeSet result = findElementsByTagName(ElementValue.ATTRIBUTE, docs,
                qname, null);
        return result;
    }

    /**
     * Find elements by their tag name. This method is comparable to the DOM's
     * method call getElementsByTagName. All elements matching tagName and
     * belonging to one of the documents in the DocumentSet docs are returned.
     * 
     * @param docs
     *                  Description of the Parameter
     * @param tagName
     *                  Description of the Parameter
     * @return
     */
    public NodeSet findElementsByTagName(byte type, DocumentSet docs,
            QName qname, NodeSelector selector) {
//    	final long start = System.currentTimeMillis();
        final ExtArrayNodeSet result = new ExtArrayNodeSet(docs.getLength(),
                256);
        final SymbolTable symbols = broker.getSymbols();
        DocumentImpl doc;
        int docId;
        int len;
        short collectionId;
        long gid;
        VariableByteInput is = null;
        ElementValue ref;
        short sym, nsSym;
        Collection collection;
        NodeProxy p;
        final short nodeType = (type == ElementValue.ATTRIBUTE ? Node.ATTRIBUTE_NODE
                : Node.ELEMENT_NODE);
        final Lock lock = dbElement.getLock();
//        StringBuffer debug = new StringBuffer();
//        debug.append(qname.toString()).append(": ");
        for (Iterator i = docs.getCollectionIterator(); i.hasNext();) {
            collection = (Collection) i.next();
            collectionId = collection.getId();
            if (type == ElementValue.ATTRIBUTE_ID) {
                ref = new ElementValue((byte) type, collectionId, qname
                        .getLocalName());
            } else {
                sym = symbols.getSymbol(qname.getLocalName());
                nsSym = symbols.getNSSymbol(qname.getNamespaceURI());
                ref = new ElementValue((byte) type, collectionId, sym, nsSym);
            }
            try {
            	lock.acquire(Lock.READ_LOCK);
            	is = dbElement.getAsStream(ref);
            	
            	if (is == null) continue;
            	while (is.available() > 0) {
            		docId = is.readInt();
            		len = is.readInt();
            		if ((doc = docs.getDoc(docId)) == null) {
            			is.skip(len * 4);
            			continue;
            		}
            		gid = 0;
            		for (int k = 0; k < len; k++) {
            			gid = gid + is.readLong();
            			if(selector == null)
            				p = new NodeProxy(doc, gid, nodeType, StorageAddress.read(is));
            			else {
            				p = selector.match(doc, gid);
            				if(p != null) {
            					p.setInternalAddress(StorageAddress.read(is));
            					p.setNodeType(nodeType);
            				} else
            					is.skip(3);
            			}
            			if(p != null) {
//            				debug.append(StorageAddress.toString(p.getInternalAddress())).append(':');
//            				debug.append(gid).append(' ');
            				result.add(p, len);
            			}
            		}
            	}
            } catch (EOFException e) {
            	//EOFExceptions are expected here
            } catch (LockException e) {
                LOG.warn(
                        "findElementsByTagName(byte, DocumentSet, QName, NodeSelector) - "
                                + "failed to acquire lock", e);
            } catch (IOException e) {
                LOG.warn(
                        "findElementsByTagName(byte, DocumentSet, QName, NodeSelector) - "
                                + "io exception while reading elements for "
                                + qname, e);
            } finally {
                lock.release();
            }
        }
//        LOG.debug(debug.toString());
        //		result.sort();
//        LOG.debug(
//        		"found "
//        		+ qname
//				+ ": "
//				+ result.getLength()
//				+ " in "
//				+ (System.currentTimeMillis() - start)
//				+ "ms.");
        return result;
    }

    public Occurrences[] scanIndexedElements(Collection collection,
            boolean inclusive) throws PermissionDeniedException {
        if (!collection.getPermissions().validate(broker.getUser(),
                Permission.READ))
                throw new PermissionDeniedException(
                        "you don't have the permission"
                                + " to read collection " + collection.getName());
        List collections = inclusive ? collection.getDescendants(broker, broker
                .getUser()) : new ArrayList();
        collections.add(collection);
        TreeMap map = new TreeMap();
        VariableByteArrayInput is;
        int docId;
        int len;
        // required for namespace lookups
        XQueryContext context = new XQueryContext(broker);
        final Lock lock = dbElement.getLock();
        for (Iterator i = collections.iterator(); i.hasNext();) {
            Collection current = (Collection) i.next();
            short collectionId = current.getId();

            ElementValue ref = new ElementValue(ElementValue.ELEMENT,
                    collectionId);
            IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
            try {
                lock.acquire();
                ArrayList values = dbElement.findEntries(query);
                for (Iterator j = values.iterator(); j.hasNext();) {
                    Value val[] = (Value[]) j.next();
                    short elementId = ByteConversion.byteToShort(val[0]
                            .getData(), 3);
                    short nsSymbol = ByteConversion.byteToShort(val[0]
                            .getData(), 5);

                    String name = broker.getSymbols().getName(elementId);
                    String namespace = nsSymbol == 0 ? "" : broker.getSymbols()
                            .getNamespace(nsSymbol);
                    QName qname = new QName(name, namespace);
                    Occurrences oc = (Occurrences) map.get(qname);
                    if (oc == null) {
                        qname.setPrefix(context.getPrefixForURI(namespace));
                        oc = new Occurrences(qname);
                        map.put(qname, oc);
                    }

                    is = new VariableByteArrayInput(val[1].data(), val[1]
                            .start(), val[1].getLength());
                    try {
                        while (is.available() > 0) {
                            docId = is.readInt();
                            len = is.readInt();
                            oc.addOccurrences(len);
                            is.skip(len * 4);
                        }
                    } catch (EOFException e) {
                    } catch (IOException e) {
                        LOG.warn("unexpected exception", e);
                    }
                }
            } catch (BTreeException e) {
                LOG.warn("exception while reading element index", e);
            } catch (IOException e) {
                LOG.warn("exception while reading element index", e);
            } catch (LockException e) {
                LOG.warn("failed to acquire lock", e);
            } catch (TerminatedException e) {
                LOG.warn("Method terminated", e);
            } finally {
                lock.release();
            }
        }
        Occurrences[] result = new Occurrences[map.size()];
        return (Occurrences[]) map.values().toArray(result);
    }

    public void dropIndex(Collection collection) {
        LOG.debug("removing elements ...");
        Value ref = new ElementValue(collection.getId());
        IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
        Lock lock = dbElement.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            dbElement.removeAll(query);
        } catch (LockException e) {
            LOG.error("could not acquire lock on elements index", e);
        } catch (BTreeException e) {
            LOG.warn(e.getMessage(), e);
        } catch (IOException e) {
            LOG.warn(e.getMessage(), e);
        } finally {
            lock.release();
        }
    }

    public void consistencyCheck(DocumentImpl doc) throws EXistException {
    	short collectionId = doc.getCollection().getId();
        Value ref = new ElementValue(collectionId);
        IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
        Lock lock = dbElement.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            ArrayList elements = dbElement.findKeys(query);
            
            Value key;
            Value value;
            byte[] data;
            // byte[] ndata;
            VariableByteArrayInput is;
            int len;
            int docId;
            long delta;
            long address;
            long gid;
            long last = 0;
            Node node;
            short symbol;
            String nodeName;
            StringBuffer msg = new StringBuffer();
            for (int i = 0; i < elements.size(); i++) {
                key = (Value) elements.get(i);
                value = dbElement.get(key);
                data = value.getData();
                symbol = ByteConversion.byteToShort(key.data(), key.start() + 3);
                nodeName = broker.getSymbols().getName(symbol);
                msg.setLength(0);
                msg.append("Checking ").append(nodeName).append(": ");
                
                is = new VariableByteArrayInput(data);
                try {
                    while (is.available() > 0) {
                        docId = is.readInt();
                        len = is.readInt();
                        if (docId == doc.getDocId()) {
                            for (int j = 0; j < len; j++) {
                                delta = is.readLong();
                                gid = last + delta;
                                last = gid;
                                address = StorageAddress.read(is);
                                node = broker.objectWith(new NodeProxy(doc, gid, address));
                                if(node == null) {
                                	throw new EXistException("Node " + gid + " in document " + doc.getFileName() + " not found.");
                                }
                                if(node.getNodeType() != Node.ELEMENT_NODE && node.getNodeType() != Node.ATTRIBUTE_NODE) {
                                	LOG.warn("Node " + gid + " in document " + 
                                			doc.getFileName() + " is not an element or attribute node.");
                                	LOG.debug("Type = " + node.getNodeType() + "; name = " + node.getNodeName() +
                                			"; value = " + node.getNodeValue());
                                	throw new EXistException("Node " + gid + " in document " + 
                                			doc.getFileName() + " is not an element or attribute node.");
                                }
                                if(!node.getLocalName().equals(nodeName)) {
                                	LOG.warn("Node name does not correspond to index entry. Expected " + nodeName +
                                			"; found " + node.getLocalName());
                                }
                                msg.append(StorageAddress.toString(address)).append(' ');
                            }
                        } else
                        	is.skip(len * 4);
                    }
                } catch (EOFException e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("removeDocument(String) - eof", e);
                    }
                } catch (IOException e) {
                    LOG.warn("removeDocument(String) " + e.getMessage(), e);
                }
                LOG.debug(msg.toString());
            }
        } catch (LockException e) {
            LOG.warn("removeDocument(String) - "
                    + "could not acquire lock on elements", e);
        } catch (TerminatedException e) {
            LOG.warn("method terminated", e);
        } catch (BTreeException e) {
            LOG.warn(e.getMessage(), e);
        } catch (IOException e) {
            LOG.warn(e.getMessage(), e);
        } finally {
            lock.release();
        }
    }
    
    public void dropIndex(DocumentImpl doc) throws ReadOnlyException {
        //	  drop element-index
        short collectionId = doc.getCollection().getId();
        Value ref = new ElementValue(collectionId);
        IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
        Lock lock = dbElement.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            ArrayList elements = dbElement.findKeys(query);
            if (LOG.isDebugEnabled()) {
                LOG.debug("removeDocument() - " + "found " + elements.size()
                        + " elements.");
            }

            Value key;
            Value value;
            byte[] data;
            // byte[] ndata;
            VariableByteArrayInput is;
            VariableByteOutputStream os;
            int len;
            int docId;
            long delta;
            long address;
            boolean changed;
            for (int i = 0; i < elements.size(); i++) {
                key = (Value) elements.get(i);
                value = dbElement.get(key);
                data = value.getData();
                is = new VariableByteArrayInput(data);
                os = new VariableByteOutputStream();
                changed = false;
                try {
                    while (is.available() > 0) {
                        docId = is.readInt();
                        len = is.readInt();
                        if (docId != doc.getDocId()) {
                            // copy data to new buffer
                            os.writeInt(docId);
                            os.writeInt(len);
                            for (int j = 0; j < len; j++) {
                                delta = is.readLong();
                                address = StorageAddress.read(is);
                                os.writeLong(delta);
                                StorageAddress.write(address, os);
                            }
                        } else {
                            changed = true;
                            // skip
                            is.skip(len * 4);
                        }
                    }
                } catch (EOFException e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("removeDocument(String) - eof", e);
                    }
                } catch (IOException e) {
                    LOG.warn("removeDocument(String) " + e.getMessage(), e);
                }
                if (changed) {
                    if (dbElement.put(key, os.data()) < 0)
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("removeDocument() - "
                                        + "could not save element");
                            }
                }
            }
        } catch (LockException e) {
            LOG.warn("removeDocument(String) - "
                    + "could not acquire lock on elements", e);
        } catch (TerminatedException e) {
            LOG.warn("method terminated", e);
        } catch (BTreeException e) {
            LOG.warn(e.getMessage(), e);
        } catch (IOException e) {
            LOG.warn(e.getMessage(), e);
        } finally {
            lock.release();
        }
    }

    /** Called by {@link NativeBroker.reIndex} */
    public void reindex(DocumentImpl oldDoc, NodeImpl node) {
        if (elementIds.size() == 0) return;
        Lock lock = dbElement.getLock();
        Map.Entry entry;
        QName qname;
        List oldList = new ArrayList(), idList;
        NodeProxy p;
        VariableByteInput is = null;
        int len, docId;
        byte[] data;
        Value ref;
        Value val;
        short sym, nsSym;
        short collectionId = oldDoc.getCollection().getId();
        long delta, last, gid, address;
        try {
            // iterate through elements
            for (Iterator i = elementIds.entrySet().iterator(); i.hasNext();) {
                entry = (Map.Entry) i.next();
                idList = (ArrayList) entry.getValue();
                qname = (QName) entry.getKey();
                if (qname.getNameType() != ElementValue.ATTRIBUTE_ID) {
                    sym = broker.getSymbols().getSymbol(qname.getLocalName());
                    nsSym = broker.getSymbols().getNSSymbol(
                            qname.getNamespaceURI());
                    ref = new ElementValue(qname.getNameType(), collectionId,
                            sym, nsSym);
                } else
                    ref = new ElementValue(qname.getNameType(), collectionId,
                            qname.getLocalName());
                // try to retrieve old index entry for the element
                try {
                    lock.acquire(Lock.WRITE_LOCK);
                    is = dbElement.getAsStream(ref);
                    os.clear();
                    oldList.clear();
                    if (is != null) {
                        // add old entries to the new list
                        try {
                            while (is.available() > 0) {
                                docId = is.readInt();
                                len = is.readInt();
                                if (docId != oldDoc.getDocId()) {
                                    // section belongs to another document:
                                    // copy data to new buffer
                                    os.writeInt(docId);
                                    os.writeInt(len);
                                    is.copyTo(os, len * 4);
                                } else {
                                    // copy nodes to new list
                                    last = 0;
                                    for (int j = 0; j < len; j++) {
                                        delta = is.readLong();
                                        gid = last + delta;
                                        last = gid;
                                        address = StorageAddress.read(is);
                                        if (node == null
                                                && oldDoc.getTreeLevel(gid) < oldDoc
                                                .reindexRequired()) {
                                            idList.add(new NodeProxy(oldDoc, gid,
                                                    address));
                                        } else if (node != null
                                                && (!XMLUtil.isDescendant(oldDoc,
                                                        node.getGID(), gid))) {
                                            oldList.add(new NodeProxy(oldDoc, gid,
                                                    address));
                                        }
                                    }
                                }
                            }
                        } catch (EOFException e) {
                        } catch (IOException e) {
                            LOG.error("io-error while updating index for element "
                                    + qname);
                        }
                    }
                    if (node != null) idList.addAll(oldList);
                    // write out the updated list
                    FastQSort.sort(idList, 0, idList.size() - 1);
                    len = idList.size();
                    os.writeInt(doc.getDocId());
                    os.writeInt(len);
                    last = 0;
                    for (int j = 0; j < len; j++) {
                        p = (NodeProxy) idList.get(j);
                        delta = p.gid - last;
                        last = p.gid;
                        os.writeLong(delta);
                        StorageAddress.write(p.getInternalAddress(), os);
                    }
                    //data = os.toByteArray();
                    if (is == null)
                        dbElement.put(ref, os.data());
                    else {
                        address = ((BFile.PageInputStream) is).getAddress();
                        dbElement.update(address, ref, os.data());
                        //dbElement.update(val.getAddress(), ref, data);
                    }
                } catch (LockException e) {
                    LOG.error("could not acquire lock for index on " + qname);
                    return;
                } catch (IOException e) {
                    LOG.error("io error while reindexing " + qname, e);
                    is = null;
                } finally {
                    lock.release(Lock.WRITE_LOCK);
                }
            }
        } catch (ReadOnlyException e) {
            LOG.warn("database is read only");
        }
        elementIds.clear();
    }

    public void remove() {
        if (elementIds.size() == 0) return;
        Lock lock = dbElement.getLock();
        Map.Entry entry;
        QName qname;
        List newList = new ArrayList(), idList;
        NodeProxy p;
        VariableByteArrayInput is;
        int len, docId;
        byte[] data;
        Value ref;
        Value val;
        short sym, nsSym;
        short collectionId = doc.getCollection().getId();
        long delta, last, gid, address;
        try {
            // iterate through elements
            for (Iterator i = elementIds.entrySet().iterator(); i.hasNext();) {
                try {
                    lock.acquire(Lock.WRITE_LOCK);
                    entry = (Map.Entry) i.next();
                    idList = (ArrayList) entry.getValue();
                    qname = (QName) entry.getKey();
                    if (qname.getNameType() != ElementValue.ATTRIBUTE_ID) {
                        sym = broker.getSymbols().getSymbol(qname.getLocalName());
                        nsSym = broker.getSymbols().getNSSymbol(
                                qname.getNamespaceURI());
                        ref = new ElementValue(qname.getNameType(), collectionId,
                                sym, nsSym);
                    } else {
                        ref = new ElementValue(qname.getNameType(), collectionId,
                                qname.getLocalName());
                    }
                    val = dbElement.get(ref);
                    os.clear();
                    newList.clear();
                    if (val != null) {
                        // add old entries to the new list
                        data = val.getData();
                        is = new VariableByteArrayInput(data);
                        try {
                            while (is.available() > 0) {
                                docId = is.readInt();
                                len = is.readInt();
                                if (docId != doc.getDocId()) {
                                    // section belongs to another document:
                                    // copy data to new buffer
                                    os.writeInt(docId);
                                    os.writeInt(len);
                                    try {
                                        is.copyTo(os, len * 4);
                                    } catch(EOFException e) {
                                        LOG.error("EOF while copying: expected: " + len);
                                    }
                                } else {
                                    // copy nodes to new list
                                    last = 0;
                                    for (int j = 0; j < len; j++) {
                                        delta = is.readLong();
                                        gid = last + delta;
                                        last = gid;
                                        address = StorageAddress.read(is);
                                        if (!containsNode(idList, gid)) {
                                            newList.add(new NodeProxy(doc, gid,
                                                    address));
                                        }
                                    }
                                }
                            }
                        } catch (EOFException e) {
                            LOG
                            .error("end-of-file while updating index for element "
                                    + qname);
                        } catch (IOException e) {
                            LOG.error("io-error while updating index for element "
                                    + qname);
                        }
                    }
                    // write out the updated list
                    FastQSort.sort(newList, 0, newList.size() - 1);
                    len = newList.size();
                    os.writeInt(doc.getDocId());
                    os.writeInt(len);
                    last = 0;
                    for (int j = 0; j < len; j++) {
                        p = (NodeProxy) newList.get(j);
                        delta = p.gid - last;
                        last = p.gid;
                        os.writeLong(delta);
                        StorageAddress.write(p.getInternalAddress(), os);
                    }
                    if (val == null) {
                    	dbElement.put(ref, os.data());
                    } else {
                    	dbElement.update(val.getAddress(), ref, os.data());
                    }
                } catch (LockException e) {
                    LOG.error("could not acquire lock on elements", e);
                } finally {
                    lock.release();
                }
            }
        } catch (ReadOnlyException e) {
            LOG.warn("database is read only");
        }
        elementIds.clear();
    }

    private final static boolean containsNode(List list, long gid) {
        for (int i = 0; i < list.size(); i++)
            if (((NodeProxy) list.get(i)).gid == gid) return true;
        return false;
    }

    public void flush() {
        if (elementIds.size() == 0) return;
        final ProgressIndicator progress = new ProgressIndicator(elementIds
                .size(), 5);

        NodeProxy proxy;
        QName qname;
        ArrayList idList;
        int count = 1, len;
        byte[] data;
        String name;
        ElementValue ref;
        Map.Entry entry;
        // get collection id for this collection
        long prevId;
        long cid;
        long addr;
        short collectionId = doc.getCollection().getId();
        Lock lock = dbElement.getLock();
        try {
            for (Iterator i = elementIds.entrySet().iterator(); i.hasNext();) {
                entry = (Map.Entry) i.next();
                qname = (QName) entry.getKey();
                idList = (ArrayList) entry.getValue();
                os.clear();
                FastQSort.sort(idList, 0, idList.size() - 1);
                len = idList.size();
                os.writeInt(doc.getDocId());
                os.writeInt(len);
                prevId = 0;
                for (int j = 0; j < len; j++) {
                    proxy = (NodeProxy) idList.get(j);
                    cid = proxy.gid - prevId;
                    prevId = proxy.gid;
                    os.writeLong(cid);
                    StorageAddress.write(proxy.getInternalAddress(), os);
                }
                if (qname.getNameType() != ElementValue.ATTRIBUTE_ID) {
                    short sym = broker.getSymbols().getSymbol(
                            qname.getLocalName());
                    short nsSym = broker.getSymbols().getNSSymbol(
                            qname.getNamespaceURI());
                    ref = new ElementValue(qname.getNameType(), collectionId,
                            sym, nsSym);
                } else {
                    ref = new ElementValue(qname.getNameType(), collectionId,
                            qname.getLocalName());
                }
                try {
                    lock.acquire(Lock.WRITE_LOCK);
                    if (dbElement.append(ref, os.data()) < 0) {
                        LOG.warn("could not save index for element " + qname);
                        continue;
                    }
                } catch (LockException e) {
                    LOG.error("could not acquire lock on elements", e);
                } catch (IOException e) {
                    LOG.error("io error while writing element " + qname, e);
                } finally {
                    lock.release();
                }
                progress.setValue(count);
                if (progress.changed()) {
                    setChanged();
                    notifyObservers(progress);
                }
                count++;
            }
        } catch (ReadOnlyException e) {
            LOG.warn("database is read-only");
            return;
        }
        progress.finish();
        setChanged();
        notifyObservers(progress);
        elementIds.clear();
    }

    public void sync() {
        Lock lock = dbElement.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            try {
                dbElement.flush();
            } catch (DBException dbe) {
                LOG.warn(dbe);
            }
        } catch (LockException e) {
            LOG.warn("could not acquire lock for elements", e);
        } finally {
            lock.release();
        }
    }
}