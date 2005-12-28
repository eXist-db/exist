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
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.AttrImpl;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ElementImpl;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.SymbolTable;
import org.exist.dom.TextImpl;
import org.exist.dom.XMLUtil;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.btree.BTreeException;
import org.exist.storage.btree.DBException;
import org.exist.storage.btree.IndexQuery;
import org.exist.storage.btree.Value;
import org.exist.storage.index.BFile;
import org.exist.storage.io.VariableByteArrayInput;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.lock.Lock;
import org.exist.util.ByteConversion;
import org.exist.util.FastQSort;
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
 */
public class NativeElementIndex extends ElementIndex implements ContentLoadingObserver {
    
    private static Logger LOG = Logger.getLogger(NativeElementIndex.class.getName());

    /** The datastore for this node index */
    protected BFile dbNodes;

    /** Work output Stream taht should be cleared before every use */
    private VariableByteOutputStream os = new VariableByteOutputStream();

    public NativeElementIndex(DBBroker broker, BFile dbNodes) {
        super(broker);
        this.dbNodes = dbNodes;
    }

    /** Store the given node in the node index.
     * @param qname The node's identity
     * @param proxy The node's proxy
     */
    public void addNode(QName qname, NodeProxy proxy) {       
        //Is this node already pending ?
        ArrayList buf = (ArrayList) pending.get(qname);
        if (buf == null) {
            //Create a node list
            buf = new ArrayList(50);
            pending.put(qname, buf);
        }
        //Add node's proxy to the list
        buf.add(proxy);
    }
    
    /* (non-Javadoc)
     * @see org.exist.storage.ContentLoadingObserver#sync()
     */
    public void sync() {
        Lock lock = dbNodes.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            dbNodes.flush();
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock for '" + dbNodes.getFile().getName() + "'", e);
        } catch (DBException e) {
            LOG.warn(e.getMessage(), e);        
        } finally {
            lock.release();
        }
    }    


    /* (non-Javadoc)
     * @see org.exist.storage.ContentLoadingObserver#flush()
     */
    public void flush() {
        if (pending.size() == 0) 
            return;        
        final ProgressIndicator progress = new ProgressIndicator(pending.size(), 5);
        QName qname;   
        NodeProxy currentNode;
        //TODO : NativeValueIndex uses LongLinkedLists -pb
        ArrayList gids;
        int gidsCount;
        long previousGID;
        long delta;        
        int lenOffset;
        final SymbolTable symbols = broker.getSymbols();
        Map.Entry entry; 
        ElementValue ref;
        final short collectionId = doc.getCollection().getId(); 
        final Lock lock = dbNodes.getLock();   
        int count = 0;
        for (Iterator i = pending.entrySet().iterator(); i.hasNext(); count++) {
            entry = (Map.Entry) i.next();
            qname = (QName) entry.getKey();
            gids = (ArrayList) entry.getValue();            
            gidsCount = gids.size();
            //Don't forget this one
            FastQSort.sort(gids, 0, gidsCount - 1);
            os.clear();
            os.writeInt(doc.getDocId());
            os.writeInt(gidsCount);
            lenOffset = os.position();
            os.writeFixedInt(0);  
            //Compute the GIDs list
            previousGID = 0;
            for (int j = 0; j < gidsCount; j++) {
                currentNode = (NodeProxy) gids.get(j);
                delta = currentNode.getGID() - previousGID;                
                os.writeLong(delta);
                StorageAddress.write(currentNode.getInternalAddress(), os);
                previousGID = currentNode.getGID();
            }            
            os.writeFixedInt(lenOffset, os.position() - lenOffset - 4);
            //Compute a key for the node
            if (qname.getNameType() == ElementValue.ATTRIBUTE_ID) {
                ref = new ElementValue(qname.getNameType(), collectionId, qname.getLocalName());                
            } else {
                short sym = symbols.getSymbol(qname.getLocalName());
                short nsSym = symbols.getNSSymbol(qname.getNamespaceURI());
                ref = new ElementValue(qname.getNameType(), collectionId, sym, nsSym);   
            }
            try {
                lock.acquire(Lock.WRITE_LOCK);
                //Store the data
                if (dbNodes.append(ref, os.data()) == BFile.UNKNOWN_ADDRESS) {
                    LOG.warn("Could not put index data for node '" +  qname + "'"); 
                }
            } catch (LockException e) {
                LOG.warn("Failed to acquire lock for '" + dbNodes.getFile().getName() + "'", e);
                //TODO : return ?
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);   
                //TODO : return ?
            } catch (ReadOnlyException e) {
                LOG.warn("Read-only error on '" + dbNodes.getFile().getName() + "'", e);
                //Return without clearing the pending entries
                return;                 
            } finally {
                lock.release();
            }
            progress.setValue(count);
            if (progress.changed()) {
                setChanged();
                notifyObservers(progress);
            }            
        }        
        progress.finish();
        setChanged();
        notifyObservers(progress);
        pending.clear();
    }    
    
    public void remove() {
        //What does remove has to do with the pending entries ??? -pb
        if (pending.size() == 0) 
            return;

        QName qname;   
        NodeProxy currentNode;        
        List currentGIDList;        
        List newGIDList;
        byte[] gids;
        int gidsCount;
        long currentGID;
        long previousGID; 
        long delta;
        Value ref;
        Map.Entry entry;
        Value value;
        VariableByteArrayInput is; 
        //TOUNDERSTAND -pb
        int size;
        int lenOffset;
        int currentDocId;
        final short collectionId = doc.getCollection().getId();
        final Lock lock = dbNodes.getLock();
        for (Iterator i = pending.entrySet().iterator(); i.hasNext();) {
            try {
                lock.acquire(Lock.WRITE_LOCK);
                newGIDList = new ArrayList();
                entry = (Map.Entry) i.next();
                currentGIDList = (ArrayList) entry.getValue();
                qname = (QName) entry.getKey();
                //Compute a key for the node
                if (qname.getNameType() == ElementValue.ATTRIBUTE_ID) {
                    ref = new ElementValue(qname.getNameType(), collectionId, qname.getLocalName());                    
                } else {
                    short sym = broker.getSymbols().getSymbol(qname.getLocalName());
                    short nsSym = broker.getSymbols().getNSSymbol(qname.getNamespaceURI());
                    ref = new ElementValue(qname.getNameType(), collectionId, sym, nsSym);                    
                }
                value = dbNodes.get(ref);
                os.clear();                
                //Does the node already exist in the index ?
                if (value != null) {
                    //Add its data to the new list
                    gids = value.getData();
                    is = new VariableByteArrayInput(gids);
                    try {
                        while (is.available() > 0) {
                            currentDocId = is.readInt();
                            gidsCount = is.readInt();
                            size = is.readFixedInt();
                            if (currentDocId != doc.getDocId()) {
                                // data are related to another document:
                                // append them to any existing data
                                os.writeInt(currentDocId);
                                os.writeInt(gidsCount);
                                os.writeFixedInt(size);
                                try {
                                    is.copyRaw(os, size);
                                } catch(EOFException e) {
                                    LOG.error(e.getMessage(), e);
                                    //TODO : data will be saved although os is probably corrupted ! -pb
                                }
                            } else {
                                // data are related to our document:
                                // feed the new list with the GIDs
                                previousGID = 0;
                                for (int j = 0; j < gidsCount; j++) {
                                    delta = is.readLong();
                                    currentGID = previousGID + delta;                                        
                                    long address = StorageAddress.read(is);
                                    if (!containsNode(currentGIDList, currentGID)) {
                                        newGIDList.add(new NodeProxy(doc, currentGID, address));
                                    }
                                    previousGID = currentGID;
                                }
                            }
                        }
                    } catch (IOException e) {
                        LOG.error(e.getMessage(), e);
                        //TODO : data will be saved although os is probably corrupted ! -pb
                    }
                }
                // append the new list
                gidsCount = newGIDList.size();
                //Don't forget this one
                FastQSort.sort(newGIDList, 0, gidsCount - 1);                
                os.writeInt(doc.getDocId());
                os.writeInt(gidsCount);
                lenOffset = os.position();
                os.writeFixedInt(0);
                previousGID = 0;
                for (int j = 0; j < gidsCount; j++) {
                    currentNode = (NodeProxy) newGIDList.get(j);
                    delta = currentNode.getGID() - previousGID;                        
                    os.writeLong(delta);
                    StorageAddress.write(currentNode.getInternalAddress(), os);
                    previousGID = currentNode.getGID();
                }                
                os.writeFixedInt(lenOffset, os.position() - lenOffset - 4);
                //Store the data
                if (value == null) {
                    if (dbNodes.put(ref, os.data()) == BFile.UNKNOWN_ADDRESS) {
                        LOG.warn("Could not put index data for node '" +  qname + "'");  
                    }                    
                } else {
                    if (dbNodes.update(value.getAddress(), ref, os.data()) == BFile.UNKNOWN_ADDRESS) {
                        LOG.warn("Could not put index data for node '" +  qname + "'");  
                    }                    
                }
            } catch (LockException e) {
                LOG.warn("Failed to acquire lock for '" + dbNodes.getFile().getName() + "'", e);
                //TODO : return ?
            } catch (ReadOnlyException e) {
                LOG.warn("Read-only error on '" + dbNodes.getFile().getName() + "'", e); 
                //TODO : return ?
            } finally {
                lock.release();
            }
        }        
        pending.clear();
    } 
    
    /**
     * Drop all index entries for the given collection.
     * 
     * @param collection
     */
    public void dropIndex(Collection collection) {        
        final Value ref = new ElementValue(collection.getId());
        final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
        final Lock lock = dbNodes.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            dbNodes.removeAll(query);
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock for '" + dbNodes.getFile().getName() + "'", e);
        } catch (BTreeException e) {
            LOG.warn(e.getMessage(), e);
        } catch (IOException e) {
            LOG.warn(e.getMessage(), e);
        } finally {
            lock.release();
        }
    }    
    
    /**
     * Drop all index entries for the given document.
     * 
     * @param doc
     * @throws ReadOnlyException
     */
    public void dropIndex(DocumentImpl doc) throws ReadOnlyException {        
        final short collectionId = doc.getCollection().getId();
        final Value ref = new ElementValue(collectionId);
        final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
        final Lock lock = dbNodes.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            ArrayList elements = dbNodes.findKeys(query);            
            Value key;
            VariableByteInput is;
            int len, size;
            int docId;
            boolean changed;
            for (int i = 0; i < elements.size(); i++) {
                key = (Value) elements.get(i);
                is = dbNodes.getAsStream(key);
                os.clear();
                changed = false;
                try {
                    while (is.available() > 0) {
                        docId = is.readInt();
                        len = is.readInt();
                        size = is.readFixedInt();
                        if (docId != doc.getDocId()) {
                            // copy data to new buffer
                            os.writeInt(docId);
                            os.writeInt(len);
                            os.writeFixedInt(size);
                            is.copyRaw(os, size);
//                            is.copyTo(os, len * 4);
                        } else {
                            changed = true;
                            // skip
                            is.skipBytes(size);
                        }
                    }
                } catch (EOFException e) {
                    // EOF is expected here
                } catch (IOException e) {
                    LOG.warn("removeDocument(String) " + e.getMessage(), e);
                }
                if (changed) {
                    if (dbNodes.put(key, os.data()) == BFile.UNKNOWN_ADDRESS)
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("removeDocument() - "
                                        + "could not save element");
                            }
                }
            }
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock for '" + dbNodes.getFile().getName() + "'", e);
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
        if (pending.size() == 0) return;
        Lock lock = dbNodes.getLock();
        Map.Entry entry;
        QName qname;
        //TODO : NativeValueIndex uses LongLinkedLists -pb
        List oldList = new ArrayList(), idList;
        NodeProxy p;
        VariableByteInput is = null;
        int len, size, lenOffset, docId;
//        byte[] data;
        Value ref;
//        Value val;
        short sym, nsSym;
        short collectionId = oldDoc.getCollection().getId();
        long delta, last, gid, address;
        try {
            // iterate through elements
            for (Iterator i = pending.entrySet().iterator(); i.hasNext();) {
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
                    is = dbNodes.getAsStream(ref);
                    os.clear();
                    oldList.clear();
                    if (is != null) {
                        // add old entries to the new list
                        try {
                            while (is.available() > 0) {
                                docId = is.readInt();
                                len = is.readInt();
                                size = is.readFixedInt();
                                if (docId != oldDoc.getDocId()) {
                                    // section belongs to another document:
                                    // copy data to new buffer
                                    os.writeInt(docId);
                                    os.writeInt(len);
                                    os.writeFixedInt(size);
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
                    lenOffset = os.position();
                    os.writeFixedInt(0);
                    last = 0;
                    for (int j = 0; j < len; j++) {
                        p = (NodeProxy) idList.get(j);
                        delta = p.getGID() - last;
                        last = p.getGID();
                        os.writeLong(delta);
                        StorageAddress.write(p.getInternalAddress(), os);
                    }
                    
                    os.writeFixedInt(lenOffset, os.position() - lenOffset - 4);
                    
                    if (is == null)
                        dbNodes.put(ref, os.data());
                    else {
                        address = ((BFile.PageInputStream) is).getAddress();
                        dbNodes.update(address, ref, os.data());
                    }
                } catch (LockException e) {
                    LOG.warn("Failed to acquire lock for '" + dbNodes.getFile().getName() + "'", e);
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
        pending.clear();
    }
    
    public NodeSet getAttributesByName(DocumentSet docs, QName qname, NodeSelector selector) {
        NodeSet result = findElementsByTagName(ElementValue.ATTRIBUTE, docs,
                qname, selector);
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
    public NodeSet findElementsByTagName(byte type, DocumentSet docs, QName qname, NodeSelector selector) {
        final ExtArrayNodeSet result = new ExtArrayNodeSet(docs.getLength(), 256);
        final SymbolTable symbols = broker.getSymbols();
        DocumentImpl doc;
        int docId;
        int len, size;
        short collectionId;
        long gid;
        VariableByteInput is = null;
        ElementValue ref;
        short sym, nsSym;
        Collection collection;
        NodeProxy p;
        final short nodeType = (type == ElementValue.ATTRIBUTE ? Node.ATTRIBUTE_NODE
                : Node.ELEMENT_NODE);
        final Lock lock = dbNodes.getLock();
        for (Iterator i = docs.getCollectionIterator(); i.hasNext();) {
            collection = (Collection) i.next();
            collectionId = collection.getId();
            if (type == ElementValue.ATTRIBUTE_ID) {
                ref = new ElementValue((byte) type, collectionId, qname.getLocalName());
            } else {
                sym = symbols.getSymbol(qname.getLocalName());
                nsSym = symbols.getNSSymbol(qname.getNamespaceURI());
                ref = new ElementValue((byte) type, collectionId, sym, nsSym);
            }
            try {
                lock.acquire(Lock.READ_LOCK);
                is = dbNodes.getAsStream(ref);
                
                if (is == null) 
                    continue;
                
                while (is.available() > 0) {
                    docId = is.readInt();
                    len = is.readInt();
                    size = is.readFixedInt();
                    doc = docs.getDoc(docId);
                    if (doc == null) {
                        is.skipBytes(size);
                        continue;
                    }
                    
                    gid = 0;
                    for (int k = 0; k < len; k++) {
                        gid = gid + is.readLong();
                        if (selector == null)
                            p = new NodeProxy(doc, gid, nodeType, StorageAddress.read(is));
                        else {
                            p = selector.match(doc, gid);
                            if (p == null) {
                                is.skip(3);
                            } else {
                                p.setInternalAddress(StorageAddress.read(is));
                                p.setNodeType(nodeType);                                
                            }                                
                        }
                        if (p != null) {
                            result.add(p, len);
                        }
                    }
                }
            } catch (EOFException e) {
                //EOFExceptions are expected here
            } catch (LockException e) {
                LOG.warn("Failed to acquire lock for '" + dbNodes.getFile().getName() + "'", e);
            } catch (IOException e) {
                LOG.warn(
                        "findElementsByTagName(byte, DocumentSet, QName, NodeSelector) - "
                                + "io exception while reading elements for "
                                + qname, e);
            } finally {
                lock.release();
            }
        }
        return result;
    }

    public Occurrences[] scanIndexedElements(Collection collection, boolean inclusive) 
            throws PermissionDeniedException {
        if (!collection.getPermissions().validate(broker.getUser(), Permission.READ))
                throw new PermissionDeniedException(
                        "you don't have the permission"
                                + " to read collection " + collection.getName());
        List collections = inclusive ? collection.getDescendants(broker, broker
                .getUser()) : new ArrayList();
        collections.add(collection);
        TreeMap map = new TreeMap();
        VariableByteArrayInput is;
        int len, size;
        // required for namespace lookups
        XQueryContext context = new XQueryContext(broker);
        final Lock lock = dbNodes.getLock();
        for (Iterator i = collections.iterator(); i.hasNext();) {
            Collection current = (Collection) i.next();
            short collectionId = current.getId();

            ElementValue ref = new ElementValue(ElementValue.ELEMENT,
                    collectionId);
            IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
            try {
                lock.acquire();
                //TODO : NativeValueIndex uses LongLinkedLists -pb
                ArrayList values = dbNodes.findEntries(query);
                for (Iterator j = values.iterator(); j.hasNext();) {
                    Value val[] = (Value[]) j.next();
                    short elementId = ByteConversion.byteToShort(val[0].getData(), 3);
                    short nsSymbol = ByteConversion.byteToShort(val[0].getData(), 5);
                    String name = broker.getSymbols().getName(elementId);
                    String namespace = nsSymbol == 0 ? "" : broker.getSymbols().getNamespace(nsSymbol);
                    QName qname = new QName(name, namespace);
                    Occurrences oc = (Occurrences) map.get(qname);
                    if (oc == null) {
                        qname.setPrefix(context.getPrefixForURI(namespace));
                        oc = new Occurrences(qname);
                        map.put(qname, oc);
                    }

                    is = new VariableByteArrayInput(val[1].data(), val[1].start(), val[1].getLength());
                    try {
                        while (is.available() > 0) { 
                            is.readInt();
                            len = is.readInt();
                            size = is.readFixedInt();
                            oc.addOccurrences(len);
                            is.skipBytes(size);
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
                LOG.warn("Failed to acquire lock for '" + dbNodes.getFile().getName() + "'", e);
            } catch (TerminatedException e) {
                LOG.warn("Method terminated", e);
            } finally {
                lock.release();
            }
        }
        Occurrences[] result = new Occurrences[map.size()];
        return (Occurrences[]) map.values().toArray(result);
    }    

    private final static boolean containsNode(List list, long gid) {
        for (int i = 0; i < list.size(); i++) {
            if (((NodeProxy) list.get(i)).getGID() == gid) 
                return true;
        }
        return false;
    }
    
    public void consistencyCheck(DocumentImpl doc) throws EXistException {
        final short collectionId = doc.getCollection().getId();
        final Value ref = new ElementValue(collectionId);
        final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
        final Lock lock = dbNodes.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            //TODO : NativeValueIndex uses LongLinkedLists -pb
            ArrayList elements = dbNodes.findKeys(query);
            
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
                value = dbNodes.get(key);
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
                        is.readFixedInt();
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
            LOG.warn("Failed to acquire lock for '" + dbNodes.getFile().getName() + "'", e);
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
    
    public boolean close() throws DBException {
        return dbNodes.close();
    }
    
    public void printStatistics() {
        dbNodes.printStatistics();
    }

	public void storeAttribute(AttrImpl node, NodePath currentPath, boolean fullTextIndexSwitch) {
		// TODO Auto-generated method stub		
	}

	public void storeText(TextImpl node, NodePath currentPath, boolean fullTextIndexSwitch) {
		// TODO Auto-generated method stub		
	}

	public void startElement(ElementImpl impl, NodePath currentPath, boolean index) {
		// TODO Auto-generated method stub		
	}

	public void endElement(int xpathType, ElementImpl node, String content) {
		// TODO Auto-generated method stub		
	}

	public void removeElement(ElementImpl node, NodePath currentPath, String content) {
		// TODO Auto-generated method stub		
	}
    
    public String toString() {
        return this.getClass().getName() + " at "+ dbNodes.getFile().getName() +
        " owned by " + broker.toString();
    }    
    


}