/*
 * eXist Open Source Native XML Database Copyright (C) 2001-06, Wolfgang M.
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
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.NodeSetHelper;
import org.exist.dom.QName;
import org.exist.dom.StoredNode;
import org.exist.dom.SymbolTable;
import org.exist.dom.TextImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.security.xacml.AccessContext;
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
        //Is this qname already pending ?
        ArrayList buf = (ArrayList) pending.get(qname);
        if (buf == null) {
            //Create a node list
            buf = new ArrayList(50);
            pending.put(qname, buf);
        }
        //Add node's proxy to the list
        buf.add(proxy);
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
    
    /* (non-Javadoc)
     * @see org.exist.storage.ContentLoadingObserver#sync()
     */
    public void sync() {
        final Lock lock = dbNodes.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            dbNodes.flush();
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock for '" + dbNodes.getFile().getName() + "'", e);
            //TODO : throw an exception ? -pb
        } catch (DBException e) {
            LOG.error(e.getMessage(), e); 
            //TODO : throw an exception ? -pb
        } finally {
            lock.release();
        }
    }    


    /* (non-Javadoc)
     * @see org.exist.storage.ContentLoadingObserver#flush()
     */
    public void flush() {
        //TODO : return if doc == null? -pb
        if (pending.size() == 0) 
            return;        
        final ProgressIndicator progress = new ProgressIndicator(pending.size(), 5);
        final SymbolTable symbols = broker.getSymbols(); 
        final short collectionId = this.doc.getCollection().getId(); 
        final Lock lock = dbNodes.getLock();   
        int count = 0;
        for (Iterator i = pending.entrySet().iterator(); i.hasNext(); count++) {
            Map.Entry entry = (Map.Entry) i.next();
            QName qname = (QName) entry.getKey();
            //TODO : NativeValueIndex uses LongLinkedLists -pb
            ArrayList gids = (ArrayList) entry.getValue();            
            int gidsCount = gids.size();
            //Don't forget this one
            FastQSort.sort(gids, 0, gidsCount - 1);
            os.clear();
            os.writeInt(this.doc.getDocId());
            os.writeInt(gidsCount);
            //TOUNDERSTAND -pb
            int lenOffset = os.position();
            os.writeFixedInt(0);  
            //Compute the GIDs list
            long previousGID = 0;
            for (int j = 0; j < gidsCount; j++) {
                NodeProxy storedNode = (NodeProxy) gids.get(j);
                long delta = storedNode.getGID() - previousGID;                
                os.writeLong(delta);
                StorageAddress.write(storedNode.getInternalAddress(), os);
                previousGID = storedNode.getGID();
            }            
            os.writeFixedInt(lenOffset, os.position() - lenOffset - 4);
            //Compute a key for the node
            ElementValue ref;
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
                    LOG.error("Could not put index data for node '" +  qname + "'"); 
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
        //TODO : return if doc == null? -pb  
        if (pending.size() == 0) 
            return;
        final SymbolTable symbols = broker.getSymbols(); 
        final short collectionId = this.doc.getCollection().getId();
        final Lock lock = dbNodes.getLock();
        for (Iterator i = pending.entrySet().iterator(); i.hasNext();) {
            Map.Entry entry = (Map.Entry) i.next();
            List storedGIDList = (ArrayList) entry.getValue();
            QName qname = (QName) entry.getKey();
            //Compute a key for the node
            Value searchKey;
            if (qname.getNameType() == ElementValue.ATTRIBUTE_ID) {
                searchKey = new ElementValue(qname.getNameType(), collectionId, qname.getLocalName());                    
            } else {                
                short sym = symbols.getSymbol(qname.getLocalName());
                short nsSym = symbols.getNSSymbol(qname.getNamespaceURI());
                searchKey = new ElementValue(qname.getNameType(), collectionId, sym, nsSym);                    
            }
            List newGIDList = new ArrayList();
            os.clear();             
            try {
                lock.acquire(Lock.WRITE_LOCK);
                Value value = dbNodes.get(searchKey);
                //Does the node already exist in the index ?
                if (value != null) {
                    //Add its data to the new list                    
                    VariableByteArrayInput is = new VariableByteArrayInput(value.getData());
                    try {
                        while (is.available() > 0) {
                            int storedDocId = is.readInt();
                            int gidsCount = is.readInt();
                            //TOUNDERSTAND -pb
                            int size = is.readFixedInt();
                            if (storedDocId != this.doc.getDocId()) {
                                // data are related to another document:
                                // append them to any existing data
                                os.writeInt(storedDocId);
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
                                long previousGID = 0;
                                for (int j = 0; j < gidsCount; j++) {
                                    long delta = is.readLong();
                                    long storedGID = previousGID + delta;                                        
                                    long address = StorageAddress.read(is);
                                    // add the node to the new list if it is not 
                                    // in the list of removed nodes
                                    if (!containsNode(storedGIDList, storedGID)) {
                                        newGIDList.add(new NodeProxy(doc, storedGID, address));
                                    }
                                    previousGID = storedGID;
                                }
                            }
                        }
                    } catch (EOFException e) {
                        //TODO : remove this block if unexpected -pb
                        LOG.warn("REPORT ME " + e.getMessage(), e);
                    }
                    //append the data from the new list
                    if (newGIDList.size() > 0 ) {                        
                        int gidsCount = newGIDList.size();
                        //Don't forget this one
                        FastQSort.sort(newGIDList, 0, gidsCount - 1);                
                        os.writeInt(this.doc.getDocId());
                        os.writeInt(gidsCount);
                        //TOUNDERSTAND -pb
                        int lenOffset = os.position();
                        os.writeFixedInt(0);
                        long previousGID = 0;
                        for (int j = 0; j < gidsCount; j++) {
                            NodeProxy storedNode = (NodeProxy) newGIDList.get(j);
                            long delta = storedNode.getGID() - previousGID;                        
                            os.writeLong(delta);
                            StorageAddress.write(storedNode.getInternalAddress(), os);
                            previousGID = storedNode.getGID();
                        }                
                        os.writeFixedInt(lenOffset, os.position() - lenOffset - 4);    
                    }
                }                
                //Store the data
                if (value == null) {
                    if (dbNodes.put(searchKey, os.data()) == BFile.UNKNOWN_ADDRESS) {
                        LOG.error("Could not put index data for node '" +  qname + "'");  
                    }                    
                } else {
                    if (dbNodes.update(value.getAddress(), searchKey, os.data()) == BFile.UNKNOWN_ADDRESS) {
                        LOG.error("Could not put index data for node '" +  qname + "'");  
                    }                    
                }
            } catch (LockException e) {
                LOG.warn("Failed to acquire lock for '" + dbNodes.getFile().getName() + "'", e);                
            } catch (ReadOnlyException e) {
                LOG.warn("Read-only error on '" + dbNodes.getFile().getName() + "'", e);   
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);
            } finally {
                lock.release();
            }
        }        
        pending.clear();
    } 
    
    /* Drop all index entries for the given collection.
     * @see org.exist.storage.ContentLoadingObserver#dropIndex(org.exist.collections.Collection)
     */
    public void dropIndex(Collection collection) {        
        final Value ref = new ElementValue(collection.getId());
        final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
        final Lock lock = dbNodes.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            //TODO : flush ? -pb
            dbNodes.removeAll(query);
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock for '" + dbNodes.getFile().getName() + "'", e);
        } catch (BTreeException e) {
            LOG.error(e.getMessage(), e);
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        } finally {
            lock.release();
        }
    }    
    
    /* Drop all index entries for the given document.
     * @see org.exist.storage.ContentLoadingObserver#dropIndex(org.exist.dom.DocumentImpl)
     */
    //TODO : note that this is *not* this.doc -pb
    public void dropIndex(DocumentImpl document) throws ReadOnlyException {              
        final short collectionId = document.getCollection().getId();
        final Value ref = new ElementValue(collectionId);
        final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
        final Lock lock = dbNodes.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            ArrayList elements = dbNodes.findKeys(query);  
            for (int i = 0; i < elements.size(); i++) {
                boolean changed = false;
                Value key = (Value) elements.get(i);                
                VariableByteInput is = dbNodes.getAsStream(key);
                os.clear();  
                try {              
                    while (is.available() > 0) {
                        int storedDocId = is.readInt();
                        int gidsCount = is.readInt();
                        //TOUNDERSTAND -pb
                        int size = is.readFixedInt();
                        if (storedDocId != document.getDocId()) {
                            // data are related to another document:
                            // copy them to any existing data
                            os.writeInt(storedDocId);
                            os.writeInt(gidsCount);
                            os.writeFixedInt(size);
                            is.copyRaw(os, size);
                        } else {
                            // data are related to our document:
                            // skip them                           
                            changed = true;                        
                            is.skipBytes(size);
                        }
                    }
                } catch (EOFException e) {
                   //EOF is expected here 
                }                
                if (changed) {  
                    //TODO : no call to dbNodes.remove if no data ? -pb
                    //TODO : why not use the same construct as above :
                    //dbNodes.update(value.getAddress(), ref, os.data()) -pb
                    if (dbNodes.put(key, os.data()) == BFile.UNKNOWN_ADDRESS) {
                        LOG.error("Could not put index data for value '" +  ref + "'");
                    }                    
                }
            }
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock for '" + dbNodes.getFile().getName() + "'", e);
        } catch (TerminatedException e) {
            LOG.warn(e.getMessage(), e);  
        } catch (BTreeException e) {
            LOG.error(e.getMessage(), e);
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        } finally {
            lock.release();
        }
    }


    /* (non-Javadoc)
     * @see org.exist.storage.ContentLoadingObserver#reindex(org.exist.dom.DocumentImpl, org.exist.dom.NodeImpl)
     */
    //TODO : note that this is *not* this.doc -pb
    public void reindex(DocumentImpl document, StoredNode node) {
        if (pending.size() == 0) 
            return;         
        final SymbolTable symbols = broker.getSymbols();
        final short collectionId = document.getCollection().getId();
        final Lock lock = dbNodes.getLock();
        for (Iterator i = pending.entrySet().iterator(); i.hasNext();) {
            try {
                lock.acquire(Lock.WRITE_LOCK);            
                os.clear();
                //TODO : NativeValueIndex uses LongLinkedLists -pb
                List newGIDList =  new ArrayList();
                //Compute a key for the node
                Map.Entry entry = (Map.Entry) i.next();
                //TODO : NativeValueIndex uses LongLinkedLists -pb
                List storedGIDList = (ArrayList) entry.getValue();
                QName qname = (QName) entry.getKey();
                Value ref;
                if (qname.getNameType() == ElementValue.ATTRIBUTE_ID) {
                    ref = new ElementValue(qname.getNameType(), collectionId, qname.getLocalName());                    
                } else {
                    short sym = symbols.getSymbol(qname.getLocalName());
                    short nsSym = symbols.getNSSymbol(qname.getNamespaceURI());
                    ref = new ElementValue(qname.getNameType(), collectionId, sym, nsSym);
                }
                VariableByteInput is = dbNodes.getAsStream(ref);
                //Does the node already exist in the index ?
                if (is != null) {
                    //Add its data to the new list
                    try {
                        while (is.available() > 0) {
                            int storedDocId = is.readInt();
                            int gidsCount = is.readInt();
                            //TOUNDERSTAND -pb
                            int size = is.readFixedInt();
                            if (storedDocId != document.getDocId()) {
                                // data are related to another document:
                                // append them to any existing data                                
                                os.writeInt(storedDocId);
                                os.writeInt(gidsCount);
                                os.writeFixedInt(size);
                                is.copyTo(os, gidsCount * 4);
                            } else {
                                // data are related to our document:
                                // feed the new list with the GIDs
                                long previousGID = 0;
                                for (int j = 0; j < gidsCount; j++) {
                                    long delta = is.readLong();
                                    long storedGID = previousGID + delta;                                        
                                    long address = StorageAddress.read(is);
                                    if (node == null) {
                                        if (document.getTreeLevel(storedGID) < document.getMetadata().reindexRequired()) {
                                            //TOUNDERSTAND : given what is below, why not use newGIDList ? -pb
                                            storedGIDList.add(new NodeProxy(document, storedGID, address));
                                        }
                                    } else {
                                        if (!NodeSetHelper.isDescendant(document, node.getGID(), storedGID)) {
                                            //TOUNDERSTAND : given what is below, why not use storedGIDList ? -pb
                                            newGIDList.add(new NodeProxy(document, storedGID, address));
                                        }
                                    }
                                    previousGID = storedGID;
                                }
                            }
                        }
                    } catch (EOFException e) {
                        //EOFExceptions expected there
                    }
                }  
                // TOUNDERSTAND : given what is above :-), why not rationalize ? -pb
                // append the new list to any existing data
                if (node != null) 
                    storedGIDList.addAll(newGIDList);
                // append the data
                if (storedGIDList.size() > 0) {
                    int gidsCount = storedGIDList.size();
                    //Don't forget this one
                    FastQSort.sort(storedGIDList, 0, gidsCount - 1);               
                    os.writeInt(document.getDocId());
                    os.writeInt(gidsCount);
                    //TOUNDERSTAND -pb       
                    int lenOffset = os.position();
                    os.writeFixedInt(0);
                    long previousGID = 0;
                    for (int j = 0; j < gidsCount; j++) {
                        NodeProxy storedNode = (NodeProxy) storedGIDList.get(j);
                        long delta = storedNode.getGID() - previousGID;                        
                        os.writeLong(delta);
                        StorageAddress.write(storedNode.getInternalAddress(), os);
                        previousGID = storedNode.getGID();
                    }                
                    os.writeFixedInt(lenOffset, os.position() - lenOffset - 4);
                }                
                if (is == null) {
                    //TOUNDERSTAND : Should is be null, what will there be in os.data() ? -pb
                    if (dbNodes.put(ref, os.data()) == BFile.UNKNOWN_ADDRESS) {
                        LOG.error("Could not put index data for node '" +  qname + "'");
                    }
                } else {
                    long address = ((BFile.PageInputStream) is).getAddress();
                    if (dbNodes.update(address, ref, os.data()) == BFile.UNKNOWN_ADDRESS) {
                        LOG.error("Could not update index data for node '" +  qname + "'");
                    }
                }                
            } catch (LockException e) {
                LOG.warn("Failed to acquire lock for '" + dbNodes.getFile().getName() + "'", e);
                return;
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);                
                //TODO : return ?
            } catch (ReadOnlyException e) {
                LOG.warn("database is read only");
                //TODO : return ?
            } finally {
                lock.release(Lock.WRITE_LOCK);
            }
        }
        pending.clear();
    }
    
    public NodeSet getAttributesByName(DocumentSet docs, QName qname, NodeSelector selector) {
        //TODO : should we consider ElementValue.ATTRIBUTE_ID as well ? -pb
        return findElementsByTagName(ElementValue.ATTRIBUTE, docs, qname, selector);       
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
        short nodeType;
        switch (type) {   
            case ElementValue.ATTRIBUTE_ID : //is this correct ? -pb
            case ElementValue.ATTRIBUTE :
                nodeType = Node.ATTRIBUTE_NODE;
                break;            
            case ElementValue.ELEMENT :
                nodeType = Node.ELEMENT_NODE;
                 break;            
            default :
                throw new IllegalArgumentException("Invalid type");
        }        
        final ExtArrayNodeSet result = new ExtArrayNodeSet(docs.getLength(), 256);
        final SymbolTable symbols = broker.getSymbols();
        final Lock lock = dbNodes.getLock();
        // true if the output document set is the same as the input document set
        boolean sameDocSet = true;
        for (Iterator i = docs.getCollectionIterator(); i.hasNext();) {
            //Compute a key for the node
            Collection collection = (Collection) i.next();
            short collectionId = collection.getId();            
            ElementValue ref;
            if (type == ElementValue.ATTRIBUTE_ID) {
                ref = new ElementValue((byte) type, collectionId, qname.getLocalName());
            } else {
                short sym = symbols.getSymbol(qname.getLocalName());
                short nsSym = symbols.getNSSymbol(qname.getNamespaceURI());
                ref = new ElementValue((byte) type, collectionId, sym, nsSym);
            }
            try {
                lock.acquire(Lock.READ_LOCK);
                VariableByteInput is = dbNodes.getAsStream(ref); 
                //Does the node already has data in the index ?
                if (is == null) {
                	sameDocSet = false;
                    continue;
                }
                while (is.available() > 0) {
                    int storedDocId = is.readInt();
                    int gidsCount = is.readInt();
                    //TOUNDERSTAND -pb
                    int size = is.readFixedInt();
                    DocumentImpl storedDocument = docs.getDoc(storedDocId);
                    //Exit if the document is not concerned
                    if (storedDocument == null) {
                        is.skipBytes(size);
                        continue;
                    }               
                    //Process the nodes
                    long previousGID = 0;
                    for (int k = 0; k < gidsCount; k++) {
                        long delta = is.readLong();
                        long storedGID = previousGID + delta; 
                        long address = StorageAddress.read(is);
                        if (selector == null) {
                            NodeProxy storedNode = new NodeProxy(storedDocument, storedGID, nodeType, address);
                            result.add(storedNode, gidsCount);                        
                        } else {
                            //Filter out the node if requested to do so
                            NodeProxy storedNode = selector.match(storedDocument, storedGID);
                            if (storedNode != null) {
                                storedNode.setInternalAddress(address);
                                storedNode.setNodeType(nodeType);
                                result.add(storedNode, gidsCount);
                            } else
                            	sameDocSet = false;
                        }
                        previousGID = storedGID;                        
                    }
                }
            } catch (EOFException e) {
                //EOFExceptions are expected here
            } catch (LockException e) {
                LOG.warn("Failed to acquire lock for '" + dbNodes.getFile().getName() + "'", e);
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);               
                //TODO : return ?
            } finally {
                lock.release();
            }
        }
//        LOG.debug("Found: " + result.getLength() + " for " + qname);
        if (sameDocSet) {
        	result.setDocumentSet(docs);
        }
        return result;
    }

    public Occurrences[] scanIndexedElements(Collection collection, boolean inclusive) 
            throws PermissionDeniedException {
        final User user = broker.getUser();
        if (!collection.getPermissions().validate(user, Permission.READ))
            throw new PermissionDeniedException("User '" + user.getName() + 
                    "' has no permission to read collection '" + collection.getName() + "'");        
        List collections;
        if (inclusive) 
            collections = collection.getDescendants(broker, broker.getUser());
        else
            collections = new ArrayList();
        collections.add(collection);
        final SymbolTable symbols = broker.getSymbols();
        final TreeMap map = new TreeMap();        
        final Lock lock = dbNodes.getLock();
        for (Iterator i = collections.iterator(); i.hasNext();) {
            Collection storedCollection = (Collection) i.next();
            short storedCollectionId = storedCollection.getId();
            ElementValue startKey = new ElementValue(ElementValue.ELEMENT, storedCollectionId);
            IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, startKey);
            try {
                lock.acquire();
                //TODO : NativeValueIndex uses LongLinkedLists -pb
                ArrayList values = dbNodes.findEntries(query);
                for (Iterator j = values.iterator(); j.hasNext();) {
                    //TOUNDERSTAND : what's in there ?
                    Value val[] = (Value[]) j.next();
                    short sym = ByteConversion.byteToShort(val[0].getData(), 3);
                    short nsSymbol = ByteConversion.byteToShort(val[0].getData(), 5);
                    String name = symbols.getName(sym);
                    String namespace;
                    if (nsSymbol == 0) {
                        namespace = "";
                    } else {
                        namespace = symbols.getNamespace(nsSymbol);
                    }                    
                    QName qname = new QName(name, namespace);
                    Occurrences oc = (Occurrences) map.get(qname);
                    if (oc == null) {
                        // required for namespace lookups
                        final XQueryContext context = new XQueryContext(broker, AccessContext.INTERNAL_PREFIX_LOOKUP);                        
                        qname.setPrefix(context.getPrefixForURI(namespace));
                        oc = new Occurrences(qname);
                        map.put(qname, oc);
                    }
                    VariableByteArrayInput is = new VariableByteArrayInput(val[1].data(), val[1].start(), val[1].getLength());
                    try {
                        while (is.available() > 0) { 
                            is.readInt();
                            int gidsCount = is.readInt();
                            //TOUNDERSTAND -pb
                            int size = is.readFixedInt();                            
                            is.skipBytes(size);
                            oc.addOccurrences(gidsCount);
                        }                    
                    } catch (EOFException e) {
                        //TODO : remove this block if unexpected -pb
                        LOG.warn("REPORT ME " + e.getMessage(), e);                    
                    }
                }
            } catch (LockException e) {
                LOG.warn("Failed to acquire lock for '" + dbNodes.getFile().getName() + "'", e);                
            } catch (BTreeException e) {
                LOG.error(e.getMessage(), e);
                //TODO : return ?
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);
                //TODO : return ?           
            } catch (TerminatedException e) {
                LOG.warn(e.getMessage(), e);
            } finally {
                lock.release();
            }
        }
        Occurrences[] result = new Occurrences[map.size()];
        return (Occurrences[]) map.values().toArray(result);
    }   

    //TODO : note that this is *not* this.doc -pb
    public void consistencyCheck(DocumentImpl document) throws EXistException {
        final SymbolTable symbols = broker.getSymbols();
        final short collectionId = document.getCollection().getId();
        final Value ref = new ElementValue(collectionId);
        final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
        final StringBuffer msg = new StringBuffer();    
        final Lock lock = dbNodes.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            //TODO : NativeValueIndex uses LongLinkedLists -pb
            ArrayList elements = dbNodes.findKeys(query);           
            for (int i = 0; i < elements.size(); i++) {
                Value key = (Value) elements.get(i);
                Value value = dbNodes.get(key);
                short sym = ByteConversion.byteToShort(key.data(), key.start() + 3);
                String nodeName = symbols.getName(sym);
                msg.setLength(0);
                msg.append("Checking ").append(nodeName).append(": ");                
                VariableByteArrayInput is = new VariableByteArrayInput(value.getData());
                try {
                    while (is.available() > 0) {
                        int storedDocId = is.readInt();
                        int gidsCount = is.readInt();
                        //TOUNDERSTAND -pb
                        int size = is.readFixedInt(); //unused                       
                        if (storedDocId != document.getDocId()) {
                            // data are related to another document:
                            // ignore them 
                            is.skip(gidsCount * 4);
                        } else {
                            // data are related to our document:
                            // check   
                            long previousGID = 0;
                            for (int j = 0; j < gidsCount; j++) {
                                long delta = is.readLong();
                                long storedGID = previousGID + delta;                                
                                long address = StorageAddress.read(is);
                                Node storedNode = broker.objectWith(new NodeProxy(doc, storedGID, address));
                                if (storedNode == null) {
                                    throw new EXistException("Node " + storedGID + " in document " + document.getFileName() + " not found.");
                                }
                                if (storedNode.getNodeType() != Node.ELEMENT_NODE && storedNode.getNodeType() != Node.ATTRIBUTE_NODE) {
                                    LOG.error("Node " + storedGID + " in document " +  document.getFileName() + " is not an element or attribute node.");
                                    LOG.error("Type = " + storedNode.getNodeType() + "; name = " + storedNode.getNodeName() + "; value = " + storedNode.getNodeValue());
                                    throw new EXistException("Node " + storedGID + " in document " + document.getFileName() + " is not an element or attribute node.");
                                }
                                if(!storedNode.getLocalName().equals(nodeName)) {
                                    LOG.error("Node name does not correspond to index entry. Expected " + nodeName + "; found " + storedNode.getLocalName());
                                    //TODO : also throw an exception here ?
                                }
                                //TODO : better message (see above) -pb
                                msg.append(StorageAddress.toString(address)).append(" ");
                                previousGID = storedGID;
                            }
                        }                            
                    }                
                } catch (EOFException e) {
                    //TODO : remove this block if unexpected -pb
                    LOG.warn("REPORT ME " + e.getMessage(), e);
                }
                LOG.debug(msg.toString());
            }
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock for '" + dbNodes.getFile().getName() + "'", e);   
            //TODO : throw an exception ? -pb
        } catch (BTreeException e) {
            LOG.error(e.getMessage(), e);
            //TODO : throw an exception ? -pb
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
            //TODO : throw an exception ? -pb
        } catch (TerminatedException e) {
            LOG.warn(e.getMessage(), e);
            //TODO : throw an exception ? -pb
        } finally {
            lock.release();
        }
    } 
    
    private final static boolean containsNode(List list, long gid) {
        for (int i = 0; i < list.size(); i++) {
            if (((NodeProxy) list.get(i)).getGID() == gid) 
                return true;
        }
        return false;
    }    
    
    public boolean close() throws DBException {
        return dbNodes.close();
    }
    
    public void printStatistics() {
        dbNodes.printStatistics();
    }
    
    public String toString() {
        return this.getClass().getName() + " at "+ dbNodes.getFile().getName() +
        " owned by " + broker.toString();
    }

}