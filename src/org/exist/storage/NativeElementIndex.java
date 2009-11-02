/*
 * eXist Open Source Native XML Database 
 * Copyright (C) 2001-06 The eXist Project
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

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.AttrImpl;
import org.exist.dom.ByDocumentIterator;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtNodeSet;
import org.exist.dom.NewArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.StoredNode;
import org.exist.dom.SymbolTable;
import org.exist.dom.TextImpl;
import org.exist.numbering.DLN;
import org.exist.numbering.NodeId;
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
import org.exist.util.Configuration;
import org.exist.util.FastQSort;
import org.exist.util.LockException;
import org.exist.util.Occurrences;
import org.exist.util.ProgressIndicator;
import org.exist.util.ReadOnlyException;
import org.exist.xquery.Constants;
import org.exist.xquery.DescendantSelector;
import org.exist.xquery.Expression;
import org.exist.xquery.NodeSelector;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.XQueryContext;
import org.w3c.dom.Node;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The indexing occurs in this class. That is, during the loading of a document
 * into the database, the process of associating a long gid with each element,
 * and the subsequent storing of the {@link NodeProxy} on disk.
 */
public class NativeElementIndex extends ElementIndex implements ContentLoadingObserver {
	
	public static final String FILE_NAME = "elements.dbx";
	public static final String  FILE_KEY_IN_CONFIG = "db-connection.elements";
	
    public static final double DEFAULT_STRUCTURAL_CACHE_GROWTH = 1.25;
    public static final double DEFAULT_STRUCTURAL_KEY_THRESHOLD = 0.01;
    public static final double DEFAULT_STRUCTURAL_VALUE_THRESHOLD = 0.04;   
    
	private final static byte ENTRIES_ORDERED = 0;
	private final static byte ENTRIES_UNORDERED = 1;
	
	//TODO : check
	public static int OFFSET_COLLECTION_ID = 0;
	//TODO : check
	public static int OFFSET_TYPE = OFFSET_COLLECTION_ID + Collection.LENGTH_COLLECTION_ID; //4
	public static int OFFSET_SYMBOL = OFFSET_TYPE + ElementValue.LENGTH_TYPE; //5
	public static int OFFSET_NSSYMBOL = OFFSET_SYMBOL + SymbolTable.LENGTH_LOCAL_NAME; //7

    /** The datastore for this node index */
    protected BFile dbNodes;
    
    protected Configuration config;

    /** Work output Stream that should be cleared before every use */
    private VariableByteOutputStream os = new VariableByteOutputStream();
    
    public NativeElementIndex(DBBroker broker, byte id, String dataDir,	Configuration config) throws DBException {
        super(broker);
        this.config = config;       
    	//TODO : read from configuration (key ?)
    	double cacheGrowth = NativeElementIndex.DEFAULT_STRUCTURAL_CACHE_GROWTH;
    	double cacheKeyThresdhold = NativeElementIndex.DEFAULT_STRUCTURAL_KEY_THRESHOLD;
    	double cacheValueThresHold = NativeElementIndex.DEFAULT_STRUCTURAL_VALUE_THRESHOLD;    	       
        BFile nativeFile = (BFile) config.getProperty(getConfigKeyForFile());        
        if (nativeFile == null) {
            File file = new File(dataDir + File.separatorChar + getFileName());
            LOG.debug("Creating '" + file.getName() + "'...");
            nativeFile = new BFile(broker.getBrokerPool(), id, false, 
            		file, broker.getBrokerPool().getCacheManager(), cacheGrowth, cacheKeyThresdhold, cacheValueThresHold);            
            config.setProperty(getConfigKeyForFile(), nativeFile); 
        }        
        this.dbNodes = nativeFile; 
        broker.addContentLoadingObserver(getInstance());
    }
    
    public String getFileName() {
    	return FILE_NAME;      
    }
    
    public String getConfigKeyForFile() {
    	return FILE_KEY_IN_CONFIG;
    }   
    
    public NativeElementIndex getInstance() {
    	return this;
    }
 
    /** Store the given node in the node index.
     * @param qname The node's identity
     * @param proxy     The node's proxy
     */
    public void addNode(QName qname, NodeProxy proxy) {      
    	if (doc.getDocId() != proxy.getDocument().getDocId()) {
    		throw new IllegalArgumentException("Document id ('" + doc.getDocId() + "') and proxy id ('" + 
    				proxy.getDocument().getDocId() + "') differ !");
    	}
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
    
    public void storeAttribute(AttrImpl node, NodePath currentPath, int indexingHint, RangeIndexSpec spec, boolean remove) {
        // TODO Auto-generated method stub      
    }

    public void storeText(TextImpl node, NodePath currentPath, int indexingHint) {
        // TODO Auto-generated method stub      
    }

    public void removeNode(StoredNode node, NodePath currentPath, String content) {
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
            lock.release(Lock.WRITE_LOCK);
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
        final int collectionId = this.doc.getCollection().getId(); 
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
            os.writeByte(inUpdateMode ? ENTRIES_UNORDERED : ENTRIES_ORDERED);            
            os.writeInt(gidsCount);
            //TOUNDERSTAND -pb
            int lenOffset = os.position();
            os.writeFixedInt(0);  
            //Compute the GIDs list
            NodeId previous = null;
            for (int j = 0; j < gidsCount; j++) {
                NodeProxy storedNode = (NodeProxy) gids.get(j);
                if (doc.getDocId() != storedNode.getDocument().getDocId()) {
                    throw new IllegalArgumentException("Document id ('" + doc.getDocId() + "') and proxy id ('" +
                            storedNode.getDocument().getDocId() + "') differ !");
                }
                try {
                    previous = storedNode.getNodeId().write(previous, os);
                } catch (IOException e) {
                    LOG.warn("IO error while writing structural index: " + e.getMessage(), e);
                }
                StorageAddress.write(storedNode.getInternalAddress(), os);
            }
            broker.getBrokerPool().getNodeFactory().writeEndOfDocument(os);
            //What does this 4 stand for ?
            os.writeFixedInt(lenOffset, os.position() - lenOffset - 4);
            try {
                lock.acquire(Lock.WRITE_LOCK);
                //Store the data
                final Value key = computeKey(collectionId, qname);
                if (dbNodes.append(key, os.data()) == BFile.UNKNOWN_ADDRESS) {
                    LOG.error("Could not put index data for node '" +  qname + "'");
                    //TODO : throw an exception ?
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
                lock.release(Lock.WRITE_LOCK);
                os.clear();
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
        inUpdateMode = false;
    }    
    
    public void remove() {      
        //TODO : return if doc == null? -pb  
        if (pending.size() == 0) 
            return; 
        final int collectionId = this.doc.getCollection().getId();
        final Lock lock = dbNodes.getLock();
        for (Iterator i = pending.entrySet().iterator(); i.hasNext();) {
            Map.Entry entry = (Map.Entry) i.next();
            List storedGIDList = (ArrayList) entry.getValue();
            QName qname = (QName) entry.getKey();
            final Value key = computeKey(collectionId, qname);     
            List newGIDList = new ArrayList();
            os.clear();             
            try {
                lock.acquire(Lock.WRITE_LOCK);
                Value value = dbNodes.get(key);
                //Does the node already exist in the index ?
                if (value != null) {
                    //Add its data to the new list                    
                    VariableByteArrayInput is = new VariableByteArrayInput(value.getData());
                    try {
                        while (is.available() > 0) {
                            int storedDocId = is.readInt();
                            byte isOrdered = is.readByte();
                            int gidsCount = is.readInt();
                            //TOUNDERSTAND -pb
                            int size = is.readFixedInt();
                            if (storedDocId != this.doc.getDocId()) {
                                // data are related to another document:
                                // append them to any existing data
                                os.writeInt(storedDocId);
                                os.writeByte(isOrdered);
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
                                NodeId previous = null;
                                NodeId nodeId;
                                long address;
                                for (int j = 0; j < gidsCount; j++) {
                                    nodeId = broker.getBrokerPool().getNodeFactory().createFromStream(previous, is);
                                    previous = nodeId;
                                    address = StorageAddress.read(is);
                                    // add the node to the new list if it is not 
                                    // in the list of removed nodes
                                    if (!containsNode(storedGIDList, nodeId)) {
                                        newGIDList.add(new NodeProxy(doc, nodeId, address));
                                    }
                                }
                                broker.getBrokerPool().getNodeFactory().createFromStream(NodeId.ROOT_NODE, is);
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
                        os.writeByte(ENTRIES_ORDERED);
                        os.writeInt(gidsCount);
                        //TOUNDERSTAND -pb
                        int lenOffset = os.position();
                        os.writeFixedInt(0);
                        NodeId previous = null;
                        NodeProxy storedNode;
                        for (int j = 0; j < gidsCount; j++) {
                            storedNode = (NodeProxy) newGIDList.get(j);
                            if (doc.getDocId() != storedNode.getDocument().getDocId()) {
                                throw new IllegalArgumentException("Document id ('" + doc.getDocId() + "') and proxy id ('" + 
                                        storedNode.getDocument().getDocId() + "') differ !");
                            }
                            try {
                                previous = storedNode.getNodeId().write(previous, os);
                            } catch (IOException e) {
                                LOG.warn("IO error while writing structural index: " + e.getMessage(), e);
                                //TODO : throw exception ?
                            }
                            StorageAddress.write(storedNode.getInternalAddress(), os);
                        }
                        broker.getBrokerPool().getNodeFactory().writeEndOfDocument(os);
                        //What does this 4 stand for ?
                        os.writeFixedInt(lenOffset, os.position() - lenOffset - 4);    
                    }
                }                
                //Store the data
                if (value == null) {
                    if (dbNodes.put(key, os.data()) == BFile.UNKNOWN_ADDRESS) {
                        LOG.error("Could not put index data for node '" +  qname + "'"); 
                        //TODO : throw exception ?
                    }                    
                } else {
                    if (dbNodes.update(value.getAddress(), key, os.data()) == BFile.UNKNOWN_ADDRESS) {
                        LOG.error("Could not put index data for node '" +  qname + "'");
                        //TODO : throw exception ?
                    }                    
                }
            } catch (LockException e) {
                LOG.warn("Failed to acquire lock for '" + dbNodes.getFile().getName() + "'", e);                
            } catch (ReadOnlyException e) {
                LOG.warn("Read-only error on '" + dbNodes.getFile().getName() + "'", e);   
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);
            } finally {
                lock.release(Lock.WRITE_LOCK);
                os.clear();
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
            dbNodes.removeAll(null, query);
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock for '" + dbNodes.getFile().getName() + "'", e);
        } catch (BTreeException e) {
            LOG.error(e.getMessage(), e);
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        } finally {
            lock.release(Lock.WRITE_LOCK);
        }
    }    
    
    /* Drop all index entries for the given document.
     * @see org.exist.storage.ContentLoadingObserver#dropIndex(org.exist.dom.DocumentImpl)
     */
    //TODO : note that this is *not* this.doc -pb
    public void dropIndex(DocumentImpl document) throws ReadOnlyException {
        final int collectionId = document.getCollection().getId();
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
                if (is == null)
                    continue;
                os.clear();
                try {
                    while (is.available() > 0) {
                        int storedDocId = is.readInt();
                        byte ordered = is.readByte();
                        int gidsCount = is.readInt();
                        //TOUNDERSTAND -pb
                        int size = is.readFixedInt();
                        if (storedDocId != document.getDocId()) {
                            // data are related to another document:
                            // copy them to any existing data
                            os.writeInt(storedDocId);
                            os.writeByte(ordered);
                            os.writeInt(gidsCount);
                            os.writeFixedInt(size);
                            is.copyRaw(os, size);
                        } else {
                            // data are related to our document:
                            // skip them, they will be processed soon
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
                    if (os.data().size() == 0) {
                        dbNodes.remove(key);
                    } else if (dbNodes.put(key, os.data()) == BFile.UNKNOWN_ADDRESS) {
                        LOG.error("Could not put index data for value '" +  ref + "'");
                        //TODO : thow exception ?
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
            lock.release(Lock.WRITE_LOCK);
            os.clear();
        }
        if (os.size() > 512000)
            // garbage collect the output stream if it is larger than 512k, otherwise reuse it
            os = new VariableByteOutputStream();
    }

    /**
     * Lookup elements or attributes in the index matching a given {@link QName} and
     * {@link NodeSelector}. The NodeSelector argument is optional. If selector is
     * null, all elements or attributes matching qname will be returned.
     * 
     * @param type either {@link ElementValue#ATTRIBUTE}, {@link ElementValue#ELEMENT}.
     * @param docs the set of documents to look up in the index
     * @param qname the QName of the attribute or element
     * @param selector an (optional) NodeSelector
     */
    public NodeSet findElementsByTagName(byte type, DocumentSet docs, QName qname, NodeSelector selector) {
        short nodeType = getIndexType(type);
        final NewArrayNodeSet result = new NewArrayNodeSet(docs.getDocumentCount(), 256);
        final Lock lock = dbNodes.getLock();
        // true if the output document set is the same as the input document set
        boolean sameDocSet = true;
        boolean descendantAxis = selector instanceof DescendantSelector;
        for (Iterator i = docs.getCollectionIterator(); i.hasNext();) {
            //Compute a key for the node
            Collection collection = (Collection) i.next();
            int collectionId = collection.getId();
            final Value key = computeTypedKey(type, collectionId, qname);
            try {
                lock.acquire(Lock.READ_LOCK);
                VariableByteInput is = dbNodes.getAsStream(key); 
                //Does the node already has data in the index ?
                if (is == null) {
                	sameDocSet = false;
                    continue;
                }
                while (is.available() > 0) {
                    int storedDocId = is.readInt();
                    byte ordered = is.readByte();
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
                    NodeId nodeId;
                    NodeId previous = null;
                    for (int k = 0; k < gidsCount; k++) {
                        nodeId = broker.getBrokerPool().getNodeFactory().createFromStream(previous, is);
                        previous = nodeId;
                        if (selector == null) {
                            long address = StorageAddress.read(is);
                            NodeProxy storedNode = new NodeProxy(storedDocument, nodeId, nodeType, address);
                            result.add(storedNode, gidsCount);                        
                        } else {
                            //Filter out the node if requested to do so
                            NodeProxy storedNode = selector.match(storedDocument, nodeId);
                            if (storedNode != null) {
                                long address = StorageAddress.read(is);
                                storedNode.setInternalAddress(address);
                                storedNode.setNodeType(nodeType);
                                result.add(storedNode, gidsCount);
                            } else {
                            	//What does this 3 stand for ?
                                is.skip(3);
                                sameDocSet = false;
                            }
                        }
                    }
                    nodeId = broker.getBrokerPool().getNodeFactory().createFromStream(NodeId.ROOT_NODE, is);
                    result.setSorted(storedDocument, ordered == ENTRIES_ORDERED && !descendantAxis);
                }
            } catch (EOFException e) {
                //EOFExceptions are expected here
            } catch (LockException e) {
                LOG.warn("Failed to acquire lock for '" + dbNodes.getFile().getName() + "'", e);
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);               
                //TODO : return ?
            } finally {
                lock.release(Lock.READ_LOCK);
            }
        }
//        LOG.debug("Found: " + result.getLength() + " for " + qname);
        if (sameDocSet) {
        	result.setDocumentSet(docs);
        }
        return result;
    }

    /**
     * Optimized lookup method which directly implements the ancestor-descendant join. The algorithm
     * does directly operate on the input stream containing the potential descendant nodes. It thus needs
     * less comparisons than {@link #findElementsByTagName(byte, DocumentSet, QName, NodeSelector)}.
     * 
     * @param type either {@link ElementValue#ATTRIBUTE} or {@link ElementValue#ELEMENT}
     * @param docs the set of documents to look up in the index
     * @param contextSet the set of ancestor nodes for which the method will try to find descendants
     * @param contextId id of the current context expression as passed by the query engine
     * @param qname the QName to search for
     */
    public NodeSet findDescendantsByTagName(byte type, QName qname, int axis,
    		DocumentSet docs, ExtNodeSet contextSet,  int contextId) {
//        LOG.debug(contextSet.toString());
        short nodeType = getIndexType(type);
        ByDocumentIterator citer = contextSet.iterateByDocument();
        final NewArrayNodeSet result = new NewArrayNodeSet(docs.getDocumentCount(), 256);
        final Lock lock = dbNodes.getLock();
        // true if the output document set is the same as the input document set
        boolean sameDocSet = true;
        for (Iterator i = docs.getCollectionIterator(); i.hasNext();) {
            //Compute a key for the node
            Collection collection = (Collection) i.next();
            int collectionId = collection.getId();
            final Value key = computeTypedKey(type, collectionId, qname);
            try {
                lock.acquire(Lock.READ_LOCK);
                VariableByteInput is;
                /*
                //TODO : uncomment and implement properly
                //TODO : beware of null NS prefix : it looks to be polysemic (none vs. all)
                //Test for "*" prefix
                if (qname.getPrefix() == null) {
                	try {
	                    final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, key);
	                    ArrayList elements = dbNodes.findKeys(query);	                     
                    } catch (BTreeException e) {
                        LOG.error(e.getMessage(), e);
                        //TODO : throw an exception ? -pb
                    } catch (TerminatedException e) {
                        LOG.warn(e.getMessage(), e);                        
                    }
                    //TODO : iterate over the keys 
                } else */                
                	is = dbNodes.getAsStream(key); 
                //Does the node already has data in the index ?
                if (is == null) {
                	sameDocSet = false;
                    continue;
                }
                int lastDocId = DocumentImpl.UNKNOWN_DOCUMENT_ID;
                NodeProxy ancestor = null;
                
                while (is.available() > 0) {
                    int storedDocId = is.readInt();
                    byte ordered = is.readByte();
                    int gidsCount = is.readInt();
                    //TOUNDERSTAND -pb
                    int size = is.readFixedInt();
                    DocumentImpl storedDocument = docs.getDoc(storedDocId);
                    //Exit if the document is not concerned
                    if (storedDocument == null) {
                        is.skipBytes(size);
                        continue;
                    }
                    // position the context iterator on the next document
                    if (storedDocId != lastDocId || ordered == ENTRIES_UNORDERED) {
                    	citer.nextDocument(storedDocument);
                    	lastDocId = storedDocId;
                    	ancestor = citer.nextNode();
                    }
                    // no ancestor node in the context set, skip the document
                    if (ancestor == null || gidsCount == 0) {
                    	is.skipBytes(size);
                        continue;
                    }

                    NodeId ancestorId = ancestor.getNodeId();
                    long prevPosition = ((BFile.PageInputStream)is).position();
                    long markedPosition = prevPosition;
                    NodeId markedId = null;
                    NodeId previousId = null;
                    NodeProxy lastAncestor = null;

                    // Process the nodes for the current document
                    NodeId nodeId = broker.getBrokerPool().getNodeFactory().createFromStream(previousId, is);
                    previousId = nodeId;
                    long address = StorageAddress.read(is);
 
                    while (true) {
                        int relation = nodeId.computeRelation(ancestorId);
//                        System.out.println(ancestorId + " -> " + nodeId + ": " + relation);
                        if (relation != -1) {
                            // current node is a descendant. walk through the descendants
                            // and add them to the result
                            if (((axis == Constants.CHILD_AXIS || axis == Constants.ATTRIBUTE_AXIS) && relation == NodeId.IS_CHILD) || 
                            		(axis == Constants.DESCENDANT_AXIS && (relation == NodeId.IS_DESCENDANT || relation == NodeId.IS_CHILD)) ||
                            		axis == Constants.DESCENDANT_SELF_AXIS || axis == Constants.DESCENDANT_ATTRIBUTE_AXIS
                        		) {
                                NodeProxy storedNode = new NodeProxy(storedDocument, nodeId, nodeType, address);
                                result.add(storedNode, gidsCount);
                                if (Expression.NO_CONTEXT_ID != contextId) {
                                    storedNode.deepCopyContext(ancestor, contextId);
                                } else
                                    storedNode.copyContext(ancestor);
                                storedNode.addMatches(ancestor);
                            }
                            prevPosition = ((BFile.PageInputStream)is).position();
                            NodeId next = broker.getBrokerPool().getNodeFactory().createFromStream(previousId, is);
                            previousId = next;
                            if (next != DLN.END_OF_DOCUMENT) {
                                // retrieve the next descendant from the stream
                                nodeId = next;
                                address = StorageAddress.read(is);
                            } else {
                                // no more descendants. check if there are more ancestors
                                if (citer.hasNextNode()) {
                                    NodeProxy nextNode = citer.peekNode();
                                    // reached the end of the input stream:
                                    // if the ancestor set has more nodes and the following ancestor
                                    // is a descendant of the previous one, we have to rescan the input stream
                                    // for further matches
                                    if (nextNode.getNodeId().isDescendantOf(ancestorId)) {
                                        prevPosition = markedPosition;
                                        ((BFile.PageInputStream)is).seek(markedPosition);
                                        nodeId = broker.getBrokerPool().getNodeFactory().createFromStream(markedId, is);
                                        previousId = nodeId;
                                        address = StorageAddress.read(is);
                                        ancestor = citer.nextNode();
                                        ancestorId = ancestor.getNodeId();
                                    } else {
//                                        ancestorId = ancestor.getNodeId();
                                        break;
                                    }
                                } else {
                                    break;
                                }
                            }
                        } else {
                            // current node is not a descendant of the ancestor node. Compare the
                            // node ids and proceed with next descendant or ancestor.
                            int cmp = ancestorId.compareTo(nodeId);
                            if (cmp < 0) {
                                // check if we have more ancestors
                                if (citer.hasNextNode()) {
                                    NodeProxy next = citer.nextNode();
                                    // if the ancestor set has more nodes and the following ancestor
                                    // is a descendant of the previous one, we have to rescan the input stream
                                    // for further matches
                                    if (next.getNodeId().isDescendantOf(ancestorId)) {
                                        // rewind the input stream to the position from where we started
                                        // for the previous ancestor node
                                        prevPosition = markedPosition;
                                        ((BFile.PageInputStream)is).seek(markedPosition);
                                        nodeId = broker.getBrokerPool().getNodeFactory().createFromStream(markedId, is);
                                        previousId = nodeId;
                                        address = StorageAddress.read(is);
                                    } else {
                                        // mark the current position in the input stream
                                        markedPosition = prevPosition;
                                        markedId = nodeId;
                                    }
                                    ancestor = next;
                                    ancestorId = ancestor.getNodeId();
                                } else {
                                    // no more ancestors: skip the remaining descendants for this document
                                    while ((previousId = broker.getBrokerPool().getNodeFactory().createFromStream(previousId, is))
                                            != DLN.END_OF_DOCUMENT) {
                                        StorageAddress.read(is);
                                    }
                                    break;
                                }
                            } else {
                                // load the next descendant from the input stream
                                prevPosition = ((BFile.PageInputStream)is).position();
                                NodeId nextId = broker.getBrokerPool().getNodeFactory().createFromStream(previousId, is);
                                previousId = nextId;
                                if (nextId != DLN.END_OF_DOCUMENT) {
                                    nodeId = nextId;
                                    address = StorageAddress.read(is);
                                } else {
                                    // We need to remember the last ancestor in case there are more docs to process.
                                    // Next document should start with this ancestor.
                                    if (lastAncestor == null)
                                        lastAncestor = ancestor;
                                    
                                    // check if we have more ancestors
                                    if (citer.hasNextNode()) {
                                        ancestor = citer.nextNode();
                                        // if the ancestor set has more nodes and the following ancestor
                                        // is a descendant of the previous one, we have to rescan the input stream
                                        // for further matches
                                        if (ancestor.getNodeId().isDescendantOf(ancestorId)) {
                                            // rewind the input stream to the position from where we started
                                            // for the previous ancestor node
                                            prevPosition = markedPosition;
                                            ((BFile.PageInputStream)is).seek(markedPosition);
                                            nodeId = broker.getBrokerPool().getNodeFactory().createFromStream(markedId, is);
                                            previousId = nodeId;
                                            address = StorageAddress.read(is);
                                            ancestorId = ancestor.getNodeId();
                                        } else {
                                            ancestorId = ancestor.getNodeId();
                                            break;
                                        }
                                    } else {
                                        break;
                                    }
                                }
                            }
                        }
                    }
//                    result.setSorted(storedDocument, ordered == ENTRIES_ORDERED);
                    if (lastAncestor != null) {
                        ancestor = lastAncestor;
                        citer.setPosition(ancestor);
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
                lock.release(Lock.READ_LOCK);
            }
        }
//        LOG.debug("Found: " + result.getLength() + " for " + qname);
        if (sameDocSet) {
        	result.setDocumentSet(docs);
        }
        return result;
    }
    
    private short getIndexType(byte type) {
        switch (type) {   
        case ElementValue.ATTRIBUTE :
            return Node.ATTRIBUTE_NODE;            
        case ElementValue.ELEMENT :
            return Node.ELEMENT_NODE;            
        default :
            throw new IllegalArgumentException("Invalid type");
        }
    }
    
    public Occurrences[] scanIndexedElements(Collection collection, boolean inclusive) 
            throws PermissionDeniedException {
        final User user = broker.getUser();
        if (!collection.getPermissions().validate(user, Permission.READ))
            throw new PermissionDeniedException("User '" + user.getName() + 
                    "' has no permission to read collection '" + collection.getURI() + "'");        
        List collections;
        if (inclusive) 
            collections = collection.getDescendants(broker, broker.getUser());
        else
            collections = new ArrayList();
        collections.add(collection);
        final SymbolTable symbols = broker.getBrokerPool().getSymbols();
        final TreeMap map = new TreeMap();        
        final Lock lock = dbNodes.getLock();
        for (Iterator i = collections.iterator(); i.hasNext();) {
            Collection storedCollection = (Collection) i.next();
            int storedCollectionId = storedCollection.getId();
            ElementValue startKey = new ElementValue(ElementValue.ELEMENT, storedCollectionId);
            IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, startKey);
            try {
                lock.acquire(Lock.READ_LOCK);
                //TODO : NativeValueIndex uses LongLinkedLists -pb
                ArrayList values = dbNodes.findEntries(query);
                for (Iterator j = values.iterator(); j.hasNext();) {
                    //TOUNDERSTAND : what's in there ?
                    Value val[] = (Value[]) j.next();
                    short sym = ByteConversion.byteToShort(val[0].getData(), OFFSET_SYMBOL);
                    short nsSymbol = ByteConversion.byteToShort(val[0].getData(), OFFSET_NSSYMBOL);
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
                            is.readByte();
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
                lock.release(Lock.READ_LOCK);
            }
        }
        Occurrences[] result = new Occurrences[map.size()];
        return (Occurrences[]) map.values().toArray(result);
    }   

    //TODO : note that this is *not* this.doc -pb
    public void consistencyCheck(DocumentImpl document) throws EXistException {
        final SymbolTable symbols = broker.getBrokerPool().getSymbols();
        final int collectionId = document.getCollection().getId();
        final Value ref = new ElementValue(collectionId);
        final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
        final StringBuilder msg = new StringBuilder();
        final Lock lock = dbNodes.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            //TODO : NativeValueIndex uses LongLinkedLists -pb
            ArrayList elements = dbNodes.findKeys(query);           
            for (int i = 0; i < elements.size(); i++) {
                Value key = (Value) elements.get(i);
                Value value = dbNodes.get(key);
                short sym = ByteConversion.byteToShort(key.data(), key.start() + OFFSET_SYMBOL);
                String nodeName = symbols.getName(sym);
                msg.setLength(0);
                msg.append("Checking ").append(nodeName).append(": ");                
                VariableByteArrayInput is = new VariableByteArrayInput(value.getData());
                try {
                    while (is.available() > 0) {
                        int storedDocId = is.readInt();
                        is.readByte();
                        int gidsCount = is.readInt();
                        //TOUNDERSTAND -pb
                        is.readFixedInt(); //unused
                        if (storedDocId != document.getDocId()) {
                            // data are related to another document:
                            // ignore them 
                            is.skip(gidsCount * 4);
                        } else {
                            // data are related to our document:
                            // check
                            NodeId previous = null;
                            for (int j = 0; j < gidsCount; j++) {
                            	NodeId nodeId = broker.getBrokerPool().getNodeFactory().createFromStream(previous, is);
                                previous = nodeId;
                                long address = StorageAddress.read(is);
                                Node storedNode = broker.objectWith(new NodeProxy(doc, nodeId, address));
                                if (storedNode == null) {
                                    throw new EXistException("Node " + nodeId + " in document " + document.getFileURI() + " not found.");
                                }
                                if (storedNode.getNodeType() != Node.ELEMENT_NODE && storedNode.getNodeType() != Node.ATTRIBUTE_NODE) {
                                    LOG.error("Node " + nodeId + " in document " +  document.getFileURI() + " is not an element or attribute node.");
                                    LOG.error("Type = " + storedNode.getNodeType() + "; name = " + storedNode.getNodeName() + "; value = " + storedNode.getNodeValue());
                                    throw new EXistException("Node " + nodeId + " in document " + document.getURI() + " is not an element or attribute node.");
                                }
                                if(!storedNode.getLocalName().equals(nodeName)) {
                                    LOG.error("Node name does not correspond to index entry. Expected " + nodeName + "; found " + storedNode.getLocalName());
                                    //TODO : also throw an exception here ?
                                }
                                //TODO : better message (see above) -pb
                                msg.append(StorageAddress.toString(address)).append(" ");
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
            lock.release(Lock.WRITE_LOCK);
        }
    } 
    
    private Value computeKey(int collectionId, QName qname) {
        return computeTypedKey(qname.getNameType(), collectionId, qname);        
    }
   
    private Value computeTypedKey(byte type, int collectionId, QName qname) {
      final SymbolTable symbols = broker.getBrokerPool().getSymbols();
      short sym = symbols.getSymbol(qname.getLocalName());
      //TODO : should we truncate the key ?
      //TODO : beware of the polysemy for getPrefix == null
      //if (qname.getPrefix() == null)
      //    return new ElementValue(type, collectionId, sym); 
      short nsSym = symbols.getNSSymbol(qname.getNamespaceURI());            
      return new ElementValue(type, collectionId, sym, nsSym);
    }
    
    private static boolean containsNode(List list, NodeId nodeId) {
        for (int i = 0; i < list.size(); i++) {
            if (((NodeProxy) list.get(i)).getNodeId().equals(nodeId)) 
                return true;
        }
        return false;
    }    
    
    public void closeAndRemove() {
    	config.setProperty(getConfigKeyForFile(), null);
    	//Do not uncomment yet !
    	//broker.removeContentLoadingObserver(getInstance());
        dbNodes.closeAndRemove();
    }

    public boolean close() throws DBException {
    	config.setProperty(getConfigKeyForFile(), null);
    	//Do not uncomment yet !
    	//broker.removeContentLoadingObserver(getInstance());
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