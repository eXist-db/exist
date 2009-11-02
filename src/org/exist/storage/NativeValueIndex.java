/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.storage;

//import java.io.EOFException;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.*;
import org.exist.indexing.AbstractStreamListener;
import org.exist.indexing.IndexUtils;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.StreamListener;
import org.exist.numbering.NodeId;
import org.exist.storage.btree.BTreeCallback;
import org.exist.storage.btree.BTreeException;
import org.exist.storage.btree.DBException;
import org.exist.storage.btree.IndexQuery;
import org.exist.storage.btree.Value;
import org.exist.storage.index.BFile;
import org.exist.storage.io.VariableByteArrayInput;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.util.ByteConversion;
import org.exist.util.Configuration;
import org.exist.util.FastQSort;
import org.exist.util.LockException;
import org.exist.util.ReadOnlyException;
import org.exist.util.UTF8;
import org.exist.util.ValueOccurrences;
import org.exist.util.XMLString;
import org.exist.xquery.Constants;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.TreeMap;

/**
 * Maintains an index on typed node values.
 * 
 * TODO: Check correct types during validation.
 * 
 * In the BTree single BFile, the keys are :
 * (collectionId, indexType, indexData)
 * and the values are : gid1, gid2-gid1, ...
 * <b></b>
 * <p>Algorithm:</p>
 * When a node is stored, an entry is added or updated in the {@link #pending} map, 
 * with given String content and basic type as key.
 * This way, the index entries are easily put in the persistent BFile storage by 
 * {@link #flush()} .
 * 
 * @author wolf
 */
public class NativeValueIndex implements ContentLoadingObserver {

    private final static Logger LOG = Logger.getLogger(NativeValueIndex.class);
    
    public static final String FILE_NAME = "values.dbx";
    public static final String  FILE_KEY_IN_CONFIG = "db-connection.values";
    
    //TODO : find the real semantics
    public static final int WITH_PATH = 1;
    public static final int WITHOUT_PATH = 2;
    
    public static final double DEFAULT_VALUE_CACHE_GROWTH = 1.25;
    public static final double DEFAULT_VALUE_KEY_THRESHOLD = 0.01;
    public static final double DEFAULT_VALUE_VALUE_THRESHOLD = 0.04;
    
	public static final int LENGTH_VALUE_TYPE = 1; //sizeof byte
	public static final int LENGTH_NODE_IDS = 4; //sizeof int

	public static final int OFFSET_COLLECTION_ID = 0;	
	public static final int OFFSET_VALUE_TYPE = OFFSET_COLLECTION_ID + Collection.LENGTH_COLLECTION_ID; //2
	public static final int OFFSET_DATA = OFFSET_VALUE_TYPE + NativeValueIndex.LENGTH_VALUE_TYPE; //3

    public final static byte IDX_GENERIC = 0;
    public final static byte IDX_QNAME = 1;

    /** The broker that is using this value index */
	DBBroker broker;
	
	/** The datastore for this value index */
    protected BFile dbValues;
    protected Configuration config;  
    
	/** A collection of key-value pairs that pending modifications for this value index.  
     * The keys are {@link org.exist.xquery.value.AtomicValue atomic values}
     * that implement {@link Indexable Indexable}.
	 * The values are {@link org.exist.util.LongLinkedList lists} containing
	 * the nodes GIDs (global identifiers.
     */
    protected Map[] pending = new Map[2];
    
	/** The current document */
    private DocumentImpl doc;
    
	/** Work output Stream taht should be cleared before every use */
    private VariableByteOutputStream os = new VariableByteOutputStream();
    
    //TODO : reconsider this. Case sensitivity have nothing to do with atomic values -pb
    protected boolean caseSensitive = true;
    
    public final static String INDEX_CASE_SENSITIVE_ATTRIBUTE = "caseSensitive";
    public final static String PROPERTY_INDEX_CASE_SENSITIVE = "indexer.case-sensitive";
    
    public NativeValueIndex(DBBroker broker, byte id, String dataDir, Configuration config) throws DBException {
        this.broker = broker;
        this.config = config;
        this.pending[IDX_GENERIC] = new TreeMap();
        this.pending[IDX_QNAME] = new TreeMap();
        //use inheritance if necessary !
    	//TODO : read from configuration (key ?)
    	double cacheGrowth = NativeValueIndex.DEFAULT_VALUE_CACHE_GROWTH;
    	double cacheKeyThresdhold = NativeValueIndex.DEFAULT_VALUE_KEY_THRESHOLD;
    	double cacheValueThresHold = NativeValueIndex.DEFAULT_VALUE_VALUE_THRESHOLD;
        BFile nativeFile = (BFile) config.getProperty(getConfigKeyForFile());        
        if (nativeFile == null) {
        	//use inheritance
            File file = new File(dataDir + File.separatorChar + getFileName());
            LOG.debug("Creating '" + file.getName() + "'...");
            nativeFile = new BFile(broker.getBrokerPool(), id, false, 
            		file, broker.getBrokerPool().getCacheManager(), cacheGrowth, cacheKeyThresdhold, cacheValueThresHold);            
            config.setProperty(getConfigKeyForFile(), nativeFile);          
           
        }        
        dbValues = nativeFile;
        //TODO : reconsider this. Case sensitivity have nothing to do with atomic values -pb
        Boolean caseOpt = (Boolean) config.getProperty(NativeValueIndex.PROPERTY_INDEX_CASE_SENSITIVE);
        if (caseOpt != null)
            caseSensitive = caseOpt.booleanValue();
        broker.addContentLoadingObserver(getInstance());
    }
    
    public String getFileName() {
    	return FILE_NAME;      
    }
    
    public String getConfigKeyForFile() {
    	return FILE_KEY_IN_CONFIG;
    }
    
    public NativeValueIndex getInstance() {
    	return this;
    }    
    
    /* (non-Javadoc)
     * @see org.exist.storage.ContentLoadingObserver#setDocument(org.exist.dom.DocumentImpl)
     */
    public void setDocument(DocumentImpl document) {
        for (byte section = 0; section <= IDX_QNAME; section++) {
            if (pending[section].size() > 0 && this.doc.getDocId() != doc.getDocId()) {
                LOG.error("Document changed but pending had " + pending[section].size(), new Throwable());
                pending[section].clear();
            }
        }
        this.doc = document;
    }    
    
    /** Store the given element's value in the value index.
     * @param xpathType The value type
     * @param node The element
     * @param content The string representation of the value
     */
    public void storeElement(ElementImpl node, String content, int xpathType, byte indexType, boolean remove) {
    	if (doc.getDocId() != node.getDocId()) {
    		throw new IllegalArgumentException("Document id ('" + doc.getDocId() + "') and proxy id ('" + 
    				node.getDocId() + "') differ !");
    	}
        AtomicValue atomic = convertToAtomic(xpathType, content);
        //Ignore if the value can't be successfully atomized
        //(this is logged elsewhere)
        if (atomic == null)
            return;
        Object key;
        if (indexType == IDX_QNAME) {
            key = new QNameKey(node.getQName(), atomic);
        } else
            key = atomic;

        if (!remove) {
            ArrayList buf;
            //Is this indexable value already pending ?
            if (pending[indexType].containsKey(key))
                buf = (ArrayList) pending[indexType].get(key);
            else {
                //Create a NodeId list
                buf = new ArrayList(8);
                pending[indexType].put(key, buf);
            }
            //Add node's NodeId to the list
            buf.add(node.getNodeId());
        } else {
            if (!pending[indexType].containsKey(key))
                pending[indexType].put(key, null);
        }
    }
    

    /** Store the given attribute's value in the value index.
     * @param spec The index specification
     * @param node The attribute
     */
    public void storeAttribute(AttrImpl node, NodePath currentPath, int indexingHint, RangeIndexSpec spec, boolean remove) {
   	 storeAttribute(node, node.getValue(), currentPath, indexingHint, spec.getType(), spec.getQName() == null ? IDX_GENERIC : IDX_QNAME, remove);
    }
    
    public void storeAttribute(AttrImpl node, String value, NodePath currentPath, int indexingHint, int xpathType, byte indexType, boolean remove) {
        //Return early
    	if (indexingHint != WITHOUT_PATH)
    		return;
        if (doc != null && doc.getDocId() != node.getDocId()) {
    		throw new IllegalArgumentException("Document id ('" + doc.getDocId() + "') and proxy id ('" + 
    				node.getDocId() + "') differ !");
    	}
    	
        AtomicValue atomic = convertToAtomic(xpathType, value);
        //Ignore if the value can't be successfully atomized
        //(this is logged elsewhere)
        if(atomic == null)
            return;
        Object key;
        if (indexType == IDX_QNAME) {
            key = new QNameKey(node.getQName(), atomic);
        } else
            key = atomic;
        if (!remove) {
            ArrayList buf;
            //Is this indexable value already pending ?
            if (pending[indexType].containsKey(key))
                //Reuse the existing NodeId list
                buf = (ArrayList) pending[indexType].get(key);
            else {
                //Create a NodeId list
                buf = new ArrayList(8);
                pending[indexType].put(key, buf);
            }
            //Add node's GID to the list
            buf.add(node.getNodeId());
        } else {
            if (!pending[indexType].containsKey(key))
                pending[indexType].put(key, null);
        }
    }

    public StoredNode getReindexRoot(StoredNode node, NodePath nodePath) {
        doc = node.getDocument();
        NodePath path = new NodePath(nodePath);
        StoredNode root = null;
        StoredNode currentNode = ((node.getNodeType() == Node.ELEMENT_NODE || node.getNodeType() == Node.ATTRIBUTE_NODE)
                        ? node : node.getParentStoredNode());
        while (currentNode != null) {
            GeneralRangeIndexSpec rSpec = doc.getCollection().getIndexByPathConfiguration(broker, path);
            QNameRangeIndexSpec qSpec = doc.getCollection().getIndexByQNameConfiguration(broker, currentNode.getQName());
            if (rSpec != null || qSpec != null)
                root = currentNode;
            if (doc.getCollection().isTempCollection() && currentNode.getNodeId().getTreeLevel() == 2)
                break;
            currentNode = currentNode.getParentStoredNode();
            path.removeLastComponent();
        }
        return root;
    }

    public void reindex(StoredNode node) {
        if (node == null)
            return;
        StreamListener listener = new ValueIndexStreamListener();
        IndexUtils.scanNode(broker, null, node, listener);
    }

    public void storeText(TextImpl node, NodePath currentPath, int indexingHint) {
        // TODO Auto-generated method stub      
    }
    
    public void removeNode(StoredNode node, NodePath currentPath, String content) {
        // TODO Auto-generated method stub      
    }    
    
    /* (non-Javadoc)
     * @see org.exist.storage.IndexGenerator#sync()
     */
    public void sync() {
        final Lock lock = dbValues.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            dbValues.flush();            
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock for '" + dbValues.getFile().getName() + "'", e);
            //TODO : throw an exception ? -pb
        } catch (DBException e) {
            LOG.error(e.getMessage(), e); 
            //TODO : throw an exception ? -pb
        } finally {
            lock.release(Lock.WRITE_LOCK);
        }
    }    
    
	/* (non-Javadoc)
	 * @see org.exist.storage.IndexGenerator#flush()
	 */
    public void flush() {
        //TODO : return if doc == null? -pb
        int keyCount = pending[IDX_GENERIC].size() + pending[IDX_QNAME].size();
        if (keyCount == 0)
            return;
        final int collectionId = this.doc.getCollection().getId();
        final Lock lock = dbValues.getLock();
        for (byte section = 0; section <= IDX_QNAME; section++) {
            for (Iterator i = pending[section].entrySet().iterator(); i.hasNext();) {
                Map.Entry entry = (Map.Entry) i.next();
                Object key = entry.getKey();
                //TODO : NativeElementIndex uses ArrayLists -pb
                ArrayList gids = (ArrayList) entry.getValue();
                int gidsCount = gids.size();
                //Don't forget this one
                FastQSort.sort(gids, 0, gidsCount - 1);
                os.clear();
                os.writeInt(this.doc.getDocId());
                os.writeInt(gidsCount);
                //Mark position
                int nodeIDsLength = os.position();
                //Dummy value : actual one will be written below
                os.writeFixedInt(0);
                //Compute the GID list
                NodeId previous = null;
                for (int j = 0; j < gidsCount; j++) {
                	NodeId nodeId = (NodeId) gids.get(j);
                    try {
                        previous = nodeId.write(previous, os);
//                        nodeId.write(os);
                    } catch (IOException e) {
                        LOG.warn("IO error while writing range index: " + e.getMessage(), e);
                        //TODO : throw exception?
                    }
                }
                //Write (variable) length of node IDs
                os.writeFixedInt(nodeIDsLength, os.position() - nodeIDsLength - LENGTH_NODE_IDS);
                try {
                    lock.acquire(Lock.WRITE_LOCK);
                    Value v;
                    if (section == IDX_GENERIC)
                        v = new SimpleValue(collectionId, (Indexable) key);
                    else {
                        QNameKey qnk = (QNameKey) key;
                        v = new QNameValue(collectionId, qnk.qname, qnk.value,
                                broker.getBrokerPool().getSymbols());
                    }
                    if (dbValues.append(v, os.data()) == BFile.UNKNOWN_ADDRESS) {
                        LOG.warn("Could not append index data for key '" +  key + "'");
                        //TODO : throw exception ?
                    }
                } catch (EXistException e) {
                    LOG.error(e.getMessage(), e);
                } catch (LockException e) {
                    LOG.warn("Failed to acquire lock for '" + dbValues.getFile().getName() + "'", e);
                   //TODO : return ?
                } catch (IOException e) {
                    LOG.error(e.getMessage(), e);
                    //TODO : return ?
                } catch (ReadOnlyException e) {
                    LOG.warn(e.getMessage(), e);
                    //Return without clearing the pending entries
                    return;
                } finally {
                    lock.release(Lock.WRITE_LOCK);
                    os.clear();
                }
            }
            pending[section].clear();
        }
    }
    
    /* (non-Javadoc)
     * @see org.exist.storage.IndexGenerator#remove()
     */
    public void remove() { 
        //TODO : return if doc == null? -pb
        int keyCount = pending[IDX_GENERIC].size() + pending[IDX_QNAME].size();
        if (keyCount == 0)
            return;
        final int collectionId = this.doc.getCollection().getId();
        final Lock lock = dbValues.getLock();
        for (byte section = 0; section <= IDX_QNAME; section++) {
            for (Iterator i = pending[section].entrySet().iterator(); i.hasNext();) {
                Map.Entry entry = (Map.Entry) i.next();
                Object key = entry.getKey();
                ArrayList storedGIDList = (ArrayList) entry.getValue();
                ArrayList newGIDList = new ArrayList();
                os.clear();
                try {
                    lock.acquire(Lock.WRITE_LOCK);
                    //Compute a key for the value
                    Value searchKey;
                    if (section == IDX_GENERIC)
                        searchKey = new SimpleValue(collectionId, (Indexable) key);
                    else {
                        QNameKey qnk = (QNameKey) key;
                        searchKey = new QNameValue(collectionId, qnk.qname, qnk.value, broker.getBrokerPool().getSymbols());
                    }
                    Value value = dbValues.get(searchKey);
                    //Does the value already has data in the index ?
                    if (value != null) {
                        //Add its data to the new list
                        VariableByteArrayInput is = new VariableByteArrayInput(value.getData());
                        while (is.available() > 0) {
                            int storedDocId = is.readInt();
                            int gidsCount = is.readInt();
                            int size = is.readFixedInt();
                            if (storedDocId != this.doc.getDocId()) {
                                // data are related to another document:
                                // append them to any existing data
                                os.writeInt(storedDocId);
                                os.writeInt(gidsCount);
                                os.writeFixedInt(size);
                                is.copyRaw(os, size);
                            } else {
                                // data are related to our document:
                                // feed the new list with the GIDs
                                NodeId previous = null;
                                for (int j = 0; j < gidsCount; j++) {
                                    NodeId nodeId = broker.getBrokerPool().getNodeFactory().createFromStream(previous, is);
                                    previous = nodeId;
                                    // add the node to the new list if it is not
                                    // in the list of removed nodes
                                    if (!containsNode(storedGIDList, nodeId)) {
                                        newGIDList.add(nodeId);
                                    }
                                }
                            }
                        }
                        //append the data from the new list
                        if (newGIDList.size() > 0) {
                            int gidsCount = newGIDList.size();
                            //Don't forget this one
                            FastQSort.sort(newGIDList, 0, gidsCount - 1);
                            os.writeInt(this.doc.getDocId());
                            os.writeInt(gidsCount);
                            //Mark position
                            int nodeIDsLength = os.position();
                            //Dummy value : actual one will be written below
                            os.writeFixedInt(0);
                            NodeId previous = null;
                            for (int j = 0; j < gidsCount; j++) {
                                NodeId nodeId = (NodeId) newGIDList.get(j);
                                try {
                                    previous = nodeId.write(previous, os);
                                } catch (IOException e) {
                                    LOG.warn("IO error while writing range index: " + e.getMessage(), e);
                                    //TODO : throw exception ?
                                }
                            }
                            //Write (variable) length of node IDs
                            os.writeFixedInt(nodeIDsLength, os.position() - nodeIDsLength - LENGTH_NODE_IDS);
                        }
//                        if(os.data().size() == 0)
//                            dbValues.remove(value);
                        if (dbValues.update(value.getAddress(), searchKey, os.data()) == BFile.UNKNOWN_ADDRESS) {
                            LOG.error("Could not update index data for value '" +  searchKey + "'");
                            //TODO: throw exception ?
                        }
                    } else {
                        if (dbValues.put(searchKey, os.data()) == BFile.UNKNOWN_ADDRESS) {
                            LOG.error("Could not put index data for value '" +  searchKey + "'");
                            //TODO : throw exception ?
                        }
                    }
                } catch (EXistException e) {
                    LOG.error(e.getMessage(), e);
                } catch (LockException e) {
                    LOG.warn("Failed to acquire lock for '" + dbValues.getFile().getName() + "'", e);
                    //TODO : return ?
                } catch (ReadOnlyException e) {
                    LOG.warn("Read-only error on '" + dbValues.getFile().getName() + "'", e);
                } catch (IOException e) {
                    LOG.error(e.getMessage(), e);
                } finally {
                    lock.release(Lock.WRITE_LOCK);
                    os.clear();
                }
            }
            pending[section].clear();
        }
    }    
    
    private static boolean containsNode(List list, NodeId nodeId) {
        for (int i = 0; i < list.size(); i++) {
            if (((NodeId) list.get(i)).equals(nodeId)) 
                return true;
        }
        return false;
    }
    
    /* Drop all index entries for the given collection.
	 * @see org.exist.storage.IndexGenerator#dropIndex(org.exist.collections.Collection)
	 */
    public void dropIndex(Collection collection) {
        final Lock lock = dbValues.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            //TODO : flush ? -pb
            // remove generic index
            Value ref = new SimpleValue(collection.getId());
            dbValues.removeAll(null, new IndexQuery(IndexQuery.TRUNC_RIGHT, ref));
            // remove QName index
            ref = new QNameValue(collection.getId());
            dbValues.removeAll(null, new IndexQuery(IndexQuery.TRUNC_RIGHT, ref));
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock for '" + dbValues.getFile().getName() + "'", e);
        } catch (BTreeException e) {
            LOG.error(e.getMessage(), e);
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        } finally {
            lock.release(Lock.WRITE_LOCK);
        }
    }
    
    /* Drop all index entries for the given document.
	 * @see org.exist.storage.IndexGenerator#dropIndex(org.exist.dom.DocumentImpl)
	 */
    //TODO : note that this is *not* this.doc -pb
    public void dropIndex(DocumentImpl document) throws ReadOnlyException {
        final int collectionId = document.getCollection().getId();
        final Lock lock = dbValues.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            for (int section = 0; section <= IDX_QNAME; section++) {
                for (Iterator i = pending[section].entrySet().iterator(); i.hasNext();) {
                    Map.Entry entry = (Map.Entry) i.next();
                    Object key = entry.getKey();
                    //Compute a key for the indexed value in the collection
                    Value v;
                    if (section == IDX_GENERIC)
                        v = new SimpleValue(collectionId, (Indexable) key);
                    else {
                        QNameKey qnk = (QNameKey) key;
                        v = new QNameValue(collectionId, qnk.qname, qnk.value,
                                broker.getBrokerPool().getSymbols());
                    }
                    Value value = dbValues.get(v);
                    if (value == null)
                        continue;
                    VariableByteArrayInput is = new VariableByteArrayInput(value.getData());
                    boolean changed = false;
                    os.clear();
                    while (is.available() > 0) {
                        int storedDocId = is.readInt();
                        int gidsCount = is.readInt();
                        int size = is.readFixedInt();
                        if (storedDocId != document.getDocId()) {
                            // data are related to another document:
                            // copy them (keep them)
                            os.writeInt(storedDocId);
                            os.writeInt(gidsCount);
                            os.writeFixedInt(size);
                            is.copyRaw(os, size);
                        } else {
                            // data are related to our document:
                            // skip them (remove them)
                            is.skipBytes(size);
                            changed = true;
                        }
                    }
                    //Store new data, if relevant
                    if (changed) {
                        if (os.data().size() == 0) {
                            // nothing to store:
                            // remove the existing key/value pair
                            dbValues.remove(v);
                        } else {
                            // still something to store:
                            // modify the existing value for the key
                            if (dbValues.put(v, os.data()) == BFile.UNKNOWN_ADDRESS) {
                                LOG.error("Could not put index data for key '" +  v + "'");
                                //TODO : throw exception ?
                            }
                        }
                    }
                }
                pending[section].clear();
            }
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock for '" + dbValues.getFile().getName() + "'", e);
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);    
        } catch (EXistException e) {
            LOG.warn("Exception while removing range index: " + e.getMessage(), e);
        } finally {
            os.clear();
            lock.release(Lock.WRITE_LOCK);
        }
    }

    public NodeSet find(int relation, DocumentSet docs, NodeSet contextSet, int axis, QName qname, Indexable value)
            throws TerminatedException {
        final NodeSet result = new NewArrayNodeSet();
        if (qname == null)
            findAll(relation, docs, contextSet, axis, null, value, result);
        else {
            List qnames = new LinkedList();
            qnames.add(qname);
            return findAll(relation, docs, contextSet, axis, qnames, value, result);
        }
        return result;
    }

    public NodeSet findAll(int relation, DocumentSet docs, NodeSet contextSet, int axis, Indexable value) throws TerminatedException {
        final NodeSet result = new NewArrayNodeSet();
        findAll(relation, docs, contextSet, axis, getDefinedIndexes(docs), value, result);
        findAll(relation, docs, contextSet, axis, null, value, result);
        return result;
    }

    /** find
	 * @param relation binary operator used for the comparison
	 * @param value right hand comparison value */
    private NodeSet findAll(int relation, DocumentSet docs, NodeSet contextSet, int axis, List qnames,
                            Indexable value, NodeSet result)
            throws TerminatedException {
        final SearchCallback cb = new SearchCallback(docs, contextSet, result, axis == NodeSet.ANCESTOR);
        final Lock lock = dbValues.getLock();
        for (Iterator iter = docs.getCollectionIterator(); iter.hasNext();) {
            final int collectionId = ((Collection) iter.next()).getId();
            final int idxOp = checkRelationOp(relation);
            Value searchKey, prefixKey;
            if (qnames == null) {
                try {
                    lock.acquire(Lock.READ_LOCK);
                    searchKey = new SimpleValue(collectionId, value);
                    prefixKey = new SimplePrefixValue(collectionId, value.getType());
                    final IndexQuery query = new IndexQuery(idxOp, searchKey);
                    if (idxOp == IndexQuery.EQ)
                        dbValues.query(query, cb);
                    else
                        dbValues.query(query, prefixKey, cb);
                } catch (EXistException e) {
                    LOG.error(e.getMessage(), e);
                } catch (LockException e) {
                    LOG.warn("Failed to acquire lock for '" + dbValues.getFile().getName() + "'", e);
                } catch (IOException e) {
                    LOG.error(e.getMessage(), e);
                } catch (BTreeException e) {
                    LOG.error(e.getMessage(), e);
                } finally {
                    lock.release(Lock.READ_LOCK);
                }
            } else {
                for (int i = 0; i < qnames.size(); i++) {
                    QName qname = (QName) qnames.get(i);
                    try {
                        lock.acquire(Lock.READ_LOCK);

                        //Compute a key for the value in the collection
                        searchKey = new QNameValue(collectionId, qname, value,
                                broker.getBrokerPool().getSymbols());
                        prefixKey = new QNamePrefixValue(collectionId, qname, value.getType(),
                                broker.getBrokerPool().getSymbols());

                        final IndexQuery query = new IndexQuery(idxOp, searchKey);
                        if (idxOp == IndexQuery.EQ)
                            dbValues.query(query, cb);
                        else
                            dbValues.query(query, prefixKey, cb);
                    } catch (EXistException e) {
                        LOG.error(e.getMessage(), e);
                    } catch (LockException e) {
                        LOG.warn("Failed to acquire lock for '" + dbValues.getFile().getName() + "'", e);
                    } catch (IOException e) {
                        LOG.error(e.getMessage(), e);
                    } catch (BTreeException e) {
                        LOG.error(e.getMessage(), e);
                    } finally {
                        lock.release(Lock.READ_LOCK);
                    }
                }
            }
        }
        return result;
    }

    public NodeSet match(DocumentSet docs, NodeSet contextSet, int axis, String expr, QName qname, int type)
            throws TerminatedException, EXistException {
        return match(docs, contextSet, axis, expr, qname, type, 0, true);
    }

    public NodeSet match(DocumentSet docs, NodeSet contextSet, int axis, String expr, QName qname, int type,
                         int flags, boolean caseSensitiveQuery)
            throws TerminatedException, EXistException {
        final NodeSet result = new NewArrayNodeSet();
        if (qname == null)
            matchAll(docs, contextSet, axis, expr, null, type, flags, caseSensitiveQuery, result);
        else {
            List qnames = new LinkedList();
            qnames.add(qname);
            matchAll(docs, contextSet, axis, expr, qnames, type, flags, caseSensitiveQuery, result);
        }
        return result;
    }

    public NodeSet matchAll(DocumentSet docs, NodeSet contextSet, int axis, String expr, int type, int flags,
                            boolean caseSensitiveQuery)
            throws TerminatedException, EXistException {
        final NodeSet result = new NewArrayNodeSet();
        matchAll(docs, contextSet, axis, expr, getDefinedIndexes(docs), type, flags, caseSensitiveQuery, result);
        matchAll(docs, contextSet, axis, expr, null, type, flags, caseSensitiveQuery, result);
        return result;
    }

    /** Regular expression search
	 * @param type  like type argument for {@link org.exist.storage.RegexMatcher} constructor
	 * @param flags like flags argument for {@link org.exist.storage.RegexMatcher} constructor
	 *  */
    public NodeSet matchAll(DocumentSet docs, NodeSet contextSet, int axis, String expr, List qnames, int type, int flags,
                            boolean caseSensitiveQuery, NodeSet result)
        throws TerminatedException, EXistException {        
    	// if the regexp starts with a char sequence, we restrict the index scan to entries starting with
    	// the same sequence. Otherwise, we have to scan the whole index.
        StringValue startTerm = null;
        if (expr.startsWith("^") && caseSensitiveQuery == caseSensitive) {
        	StringBuilder term = new StringBuilder();
    		for (int j = 1; j < expr.length(); j++)
    			if (Character.isLetterOrDigit(expr.charAt(j)))
    				term.append(expr.charAt(j));
    			else
    				break;
    		if(term.length() > 0) {
                startTerm = new StringValue(term.toString());
                LOG.debug("Match will begin index scan at '" + startTerm + "'");
    		}
        }
		final TermMatcher comparator = new RegexMatcher(expr, type, flags);
        final RegexCallback cb = new RegexCallback(docs, contextSet, result, comparator, axis == NodeSet.ANCESTOR);
        final Lock lock = dbValues.getLock();
        for (Iterator iter = docs.getCollectionIterator(); iter.hasNext();) {
            final int collectionId = ((Collection) iter.next()).getId();
            Value searchKey;
            if (qnames == null) {
                try {
                    lock.acquire(Lock.READ_LOCK);

                    if (startTerm != null) {
                        //Compute a key for the start term in the collection
                        searchKey = new SimpleValue(collectionId, startTerm);
                    } else {
                        //Compute a key for an arbitrary string in the collection
                        searchKey = new SimplePrefixValue(collectionId, Type.STRING);
                    }
                    final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, searchKey);
                    dbValues.query(query, cb);
                } catch (LockException e) {
                    LOG.warn("Failed to acquire lock for '" + dbValues.getFile().getName() + "'", e);
                } catch (IOException e) {
                    LOG.error(e.getMessage(), e);
                } catch (BTreeException e) {
                    LOG.error(e.getMessage(), e);
                } finally {
                    lock.release(Lock.READ_LOCK);
                }
            } else {
                for (int i = 0; i < qnames.size(); i++) {
                    QName qname = (QName) qnames.get(i);
                    try {
                        lock.acquire(Lock.READ_LOCK);
                        if (startTerm != null) {
                            searchKey = new QNameValue(collectionId, qname, startTerm,
                                    broker.getBrokerPool().getSymbols());
                        } else {
                            LOG.debug("Searching with QName prefix");
                            searchKey = new QNamePrefixValue(collectionId, qname, Type.STRING,
                                    broker.getBrokerPool().getSymbols());
                        }
                        final IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, searchKey);
                        dbValues.query(query, cb);
                    } catch (LockException e) {
                        LOG.warn("Failed to acquire lock for '" + dbValues.getFile().getName() + "'", e);
                    } catch (IOException e) {
                        LOG.error(e.getMessage(), e);
                    } catch (BTreeException e) {
                        LOG.error(e.getMessage(), e);
                    } finally {
                        lock.release(Lock.READ_LOCK);
                    }
                }
            }
        }
        return result;
    }
    
    public ValueOccurrences[] scanIndexKeys(DocumentSet docs, NodeSet contextSet, Indexable start) {
        final int type = start.getType();
        final boolean stringType = Type.subTypeOf(type, Type.STRING);
        final IndexScanCallback cb = new IndexScanCallback(docs, contextSet, type, false);
        final Lock lock = dbValues.getLock();
        for (Iterator i = docs.getCollectionIterator(); i.hasNext();) {
            try {
                lock.acquire(Lock.READ_LOCK);
                final Collection c = (Collection) i.next();
                final  int collectionId = c.getId();
                //Compute a key for the start value in the collection
                if (stringType) {
                    final Value startKey = new SimpleValue(collectionId, start);
                	IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, startKey);
                    dbValues.query(query, cb);
                } else {
                    final Value startKey = new SimpleValue(collectionId, start);
                    final Value prefixKey = new SimplePrefixValue(collectionId, start.getType());
                    final IndexQuery query = new IndexQuery(IndexQuery.GEQ, startKey);
                	dbValues.query(query, prefixKey, cb);
                }
			} catch (EXistException e) {
                LOG.error(e.getMessage(), e);                
            } catch (LockException e) {
                LOG.warn("Failed to acquire lock for '" + dbValues.getFile().getName() + "'", e);
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
        Map map = cb.map;
        ValueOccurrences[] result = new ValueOccurrences[map.size()];        
        return (ValueOccurrences[]) map.values().toArray(result);
    }

    /**
     * Scan all index keys indexed by the given QName. Return {@link org.exist.util.ValueOccurrences}
     * for those index entries pointing to descendants of the specified context set. The first argument specifies
     * the set of documents to include in the scan. Nodes which are not in this document set will be ignored.
     *
     * @param docs set of documents to scan
     * @param contextSet if != null, return only index entries pointing to nodes which are descendants of nodes in the context set
     * @param qnames an array of QNames: defines the index entries to be scanned.
     * @param start an optional start value: only index keys starting with or being greater than this start value
     *  (depends on the type of the index key) will be scanned
     * @return a list of ValueOccurrences
     */
    public ValueOccurrences[] scanIndexKeys(DocumentSet docs, NodeSet contextSet, QName[] qnames, Indexable start) {
        if (qnames == null) {
            List qnlist = getDefinedIndexes(docs);
            qnames = new QName[qnlist.size()];
            qnames = (QName[]) qnlist.toArray(qnames);
        }
        final int type = start.getType();
        final boolean stringType = Type.subTypeOf(type, Type.STRING);
        final IndexScanCallback cb = new IndexScanCallback(docs, contextSet, type, true);
        final Lock lock = dbValues.getLock();
        for (int j = 0; j < qnames.length; j++) {
            for (Iterator i = docs.getCollectionIterator(); i.hasNext();) {
                try {
                    lock.acquire(Lock.READ_LOCK);
                    final  int collectionId = ((Collection) i.next()).getId();
                    //Compute a key for the start value in the collection
                    if (stringType) {
                        final Value startKey = new QNameValue(collectionId, qnames[j], start, broker.getBrokerPool().getSymbols());
                        IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, startKey);
                        dbValues.query(query, cb);
                    } else {
                        final Value startKey = new QNameValue(collectionId, qnames[j], start, broker.getBrokerPool().getSymbols());
                        final Value prefixKey = new QNamePrefixValue(collectionId, qnames[j], start.getType(), broker.getBrokerPool().getSymbols());
                        final IndexQuery query = new IndexQuery(IndexQuery.GEQ, startKey);
                        dbValues.query(query, prefixKey, cb);
                    }
                } catch (EXistException e) {
                    LOG.error(e.getMessage(), e);
                } catch (LockException e) {
                    LOG.warn("Failed to acquire lock for '" + dbValues.getFile().getName() + "'", e);
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
        }
        Map map = cb.map;
        ValueOccurrences[] result = new ValueOccurrences[map.size()];
        return (ValueOccurrences[]) map.values().toArray(result);
    }

    protected List getDefinedIndexes(DocumentSet docs) {
        List qnames = new ArrayList();
        for (Iterator i = docs.getCollectionIterator(); i.hasNext(); ) {
            Collection collection = (Collection) i.next();
            IndexSpec idxConf = collection.getIndexConfiguration(broker);
            if (idxConf != null)
                qnames.addAll(idxConf.getIndexedQNames());
        }
        return qnames;
    }

    protected int checkRelationOp(int relation) {
        int indexOp;
        switch(relation) {
        	case Constants.LT:
        	    indexOp = IndexQuery.LT;
        		break;
        	case Constants.LTEQ:
        	    indexOp = IndexQuery.LEQ;
        		break;
        	case Constants.GT:
        	    indexOp = IndexQuery.GT;
        		break;
        	case Constants.GTEQ:
        	    indexOp = IndexQuery.GEQ;
        		break;
        	case Constants.NEQ:
        	    indexOp = IndexQuery.NEQ;
        		break;
        	case Constants.EQ:
        	default:
        	    indexOp = IndexQuery.EQ;
        		break;
        }
        return indexOp;
    }    
    
    /**
     * @param xpathType
     * @param value
     * @return <code>null</null> if atomization fails or if the atomic value is not indexable.
     * Should we throw an exception instead ? -pb
    * @throws XPathException 
     */
    private AtomicValue convertToAtomic(int xpathType, String value) {
        AtomicValue atomic;
        if (Type.subTypeOf(xpathType, Type.STRING)) {
            try {
					atomic = new StringValue(value, xpathType);
				} catch (XPathException e) {
					return null;
				}
        } else {
            try {
                atomic = new StringValue(value).convertTo(xpathType);
            } catch (XPathException e) {
                LOG.warn("Node value '" + value + "' cannot be converted to " + 
                        Type.getTypeName(xpathType));
                return null;
            }
        }
        if (atomic == null) {
            LOG.warn("Node value '" + Type.getTypeName(xpathType) + "(" + value + ")'" +
            " cannot be used as index key. It is null.");
            return null;
        }            
        if (!(atomic instanceof Indexable)) {
            LOG.warn("Node value '" + Type.getTypeName(xpathType) + "(" + value + ")'" +
            " cannot be used as index key. It does not implement " + Indexable.class.getName());
            return null;
        }
        return atomic;        
    }

    public void closeAndRemove() {
    	//Use inheritance if necessary ;-)
    	config.setProperty(getConfigKeyForFile(), null);       	
    	dbValues.closeAndRemove();
    }
    
    public boolean close() throws DBException {
    	//Use inheritance if necessary ;-)
    	config.setProperty(getConfigKeyForFile(), null);  
        return dbValues.close();        
    }
    
    public void printStatistics() {
        dbValues.printStatistics();
    }
    
    public String toString() {
        return this.getClass().getName() + " at "+ dbValues.getFile().getName() +
        " owned by " + broker.toString() + " (case sensitive = " + caseSensitive + ")";
    }
    
	/** TODO document */
    class SearchCallback implements BTreeCallback {
        
        DocumentSet docs;
        NodeSet contextSet;
        NodeSet result;
        boolean returnAncestor;
        
        public SearchCallback(DocumentSet docs, NodeSet contextSet, NodeSet result, boolean returnAncestor) {
            this.docs = docs;
            this.contextSet = contextSet;
            this.result = result;
            this.returnAncestor = returnAncestor;
        }
        
        /* (non-Javadoc)
         * @see org.dbxml.core.filer.BTreeCallback#indexInfo(org.dbxml.core.data.Value, long)
         */
        public boolean indexInfo(Value value, long pointer) throws TerminatedException {
            VariableByteInput is;
			try {
				is = dbValues.getAsStream(pointer);
			} catch (IOException e) {
				LOG.error(e.getMessage(), e);
                return true;
			}         
			try {                
                while (is.available() > 0) {
                    int storedDocId = is.readInt();
                    int gidsCount = is.readInt();
                    int size = is.readFixedInt();
                    DocumentImpl storedDocument = docs.getDoc(storedDocId);
                    //Exit if the document is not concerned
                	if (storedDocument == null) {
                        is.skipBytes(size);
                        continue;                        
                    }
                	//Process the nodes
                    NodeId previous = null;
                    NodeId nodeId;
                    NodeProxy storedNode;
                    for (int j = 0; j < gidsCount; j++) {
                        nodeId = broker.getBrokerPool().getNodeFactory().createFromStream(previous, is);
                        previous = nodeId;
                        storedNode = new NodeProxy(storedDocument, nodeId);
                        // if a context set is specified, we can directly check if the
                		// matching node is a descendant of one of the nodes
                		// in the context set.
                		if (contextSet != null) {
                            int sizeHint = contextSet.getSizeHint(storedDocument);
                            if (returnAncestor) {
                                NodeProxy parentNode = contextSet.parentWithChild(storedNode, false, true, NodeProxy.UNKNOWN_NODE_LEVEL);
                                if (parentNode != null) 
                                    result.add(parentNode, sizeHint);
                			} else
                                result.add(storedNode, sizeHint);
                		// otherwise, we add all nodes without check
                		} else {
                			result.add(storedNode, Constants.NO_SIZE_HINT);
                		}
                	}
                }
            } catch (IOException e) {                
                LOG.error(e.getMessage(), e);
            }
            return false;
        }
    }
    
	/** TODO document */
    private class RegexCallback extends SearchCallback {
    	
    	private TermMatcher matcher;
    	private XMLString key = new XMLString(128);
        
    	public RegexCallback(DocumentSet docs, NodeSet contextSet, NodeSet result, TermMatcher matcher, boolean returnAncestor) {
    		super(docs, contextSet, result, returnAncestor);
    		this.matcher = matcher;
    	}

		public boolean indexInfo(Value value, long pointer) throws TerminatedException {
            int offset;
            if (value.data()[value.start()] == IDX_GENERIC)
                offset = SimpleValue.OFFSET_VALUE + NativeValueIndex.LENGTH_VALUE_TYPE;
            else
                offset = QNameValue.OFFSET_VALUE + NativeValueIndex.LENGTH_VALUE_TYPE;
            key.reuse();
            UTF8.decode(value.data(), value.start() + offset,
            		value.getLength() - offset, key);
			if(matcher.matches(key)) {
				super.indexInfo(value, pointer);
			}
			return true;
		}
    }
    
    private final class IndexScanCallback implements BTreeCallback{
        
        private DocumentSet docs;
        private NodeSet contextSet;
        private Map map = new TreeMap();
        private int type;
        private boolean byQName;

        IndexScanCallback(DocumentSet docs, NodeSet contextSet, int type, boolean byQName) {
            this.docs = docs;
            this.contextSet = contextSet;
            this.type = type;
            this.byQName = byQName;
        }
        
        /* (non-Javadoc)
         * @see org.dbxml.core.filer.BTreeCallback#indexInfo(org.dbxml.core.data.Value, long)
         */
        public boolean indexInfo(Value key, long pointer) throws TerminatedException {            
            AtomicValue atomic;
            try {
                if (byQName)
                    atomic = (AtomicValue) QNameValue.deserialize(key.data(), key.start(), key.getLength());
                else
                    atomic = (AtomicValue) SimpleValue.deserialize(key.data(), key.start(), key.getLength());
                if (atomic.getType() != type)
                    return false;
            } catch (EXistException e) {
                LOG.error(e.getMessage(), e);
                return true;
            }
            VariableByteInput is;
            try {
                is = dbValues.getAsStream(pointer);
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);
                return true;
            }
            
            ValueOccurrences oc = (ValueOccurrences) map.get(atomic);                        
            try {
                while (is.available() > 0) {
                    boolean docAdded = false;
                    int storedDocId = is.readInt();
                    int gidsCount = is.readInt();
                    int size = is.readFixedInt();
                    DocumentImpl storedDocument = docs.getDoc(storedDocId); 
                    //Exit if the document is not concerned
                    if (storedDocument == null) {
                        is.skipBytes(size);
                        continue;
                    }                    
                    NodeId lastParentId = null;
                    NodeId previous = null;
                    NodeId nodeId;
                    NodeProxy parentNode;
                    for (int j = 0; j < gidsCount; j++) {
                        nodeId = broker.getBrokerPool().getNodeFactory().createFromStream(previous, is);
                        previous = nodeId;
                        if (contextSet != null)
                            parentNode = contextSet.parentWithChild(storedDocument, nodeId, false, true);
                        else
                            parentNode = new NodeProxy(storedDocument, nodeId);

                        if (parentNode != null) {
                            if (oc == null) {
                                oc = new ValueOccurrences(atomic);
                                map.put(atomic, oc);
                            }
                            //Handle this very special case : /item[foo = "bar"] vs. /item[@foo = "bar"]
                            //Same value, same parent but different nodes !
                            //Not sure if we should track the contextSet's parentId... (just like we do)
                            //... or the way the contextSet is created (thus keeping track of the NodeTest)
                            if (lastParentId == null || !lastParentId.equals(parentNode.getNodeId()))
                                oc.addOccurrences(1);
                            if (!docAdded) {
                                oc.addDocument(storedDocument);
                                docAdded = true;
                            }
                            lastParentId = parentNode.getNodeId();
                        }
                        //TODO : what if contextSet == null ? -pb
                        //See above where we have this behaviour :
                        //otherwise, we add all nodes without check    
                    }
                }
            } catch(IOException e) {
                LOG.error(e.getMessage(), e);
            }
            return true;
        }
    }

    private static class QNameKey implements Comparable {

        private QName qname;
        private AtomicValue value;

        public QNameKey(QName qname, AtomicValue atomic) {
            this.qname = qname;
            this.value = atomic;
        }

        public int compareTo(Object o) {
            QNameKey other = (QNameKey) o;
            int cmp = qname.compareTo(other.qname);
            if (cmp == 0)
                return value.compareTo(other.value);
            else
                return cmp;
        }
    }

    private static class SimpleValue extends Value {

        public static int OFFSET_IDX_TYPE = 0;
        public static int LENGTH_IDX_TYPE = 1; //sizeof byte
        public static int OFFSET_COLLECTION_ID = OFFSET_IDX_TYPE + LENGTH_IDX_TYPE; //1
        public static int OFFSET_VALUE = OFFSET_COLLECTION_ID + Collection.LENGTH_COLLECTION_ID; // 3

        public SimpleValue(int collectionId) {
            len = LENGTH_IDX_TYPE + Collection.LENGTH_COLLECTION_ID;
            data = new byte[len];
            data[OFFSET_IDX_TYPE] = IDX_GENERIC;
            ByteConversion.intToByte(collectionId, data, OFFSET_COLLECTION_ID);
            pos = OFFSET_IDX_TYPE;
        }

        public SimpleValue(int collectionId, Indexable atomic) throws EXistException {
            data = atomic.serializeValue(OFFSET_VALUE);
            len = data.length;
            pos = OFFSET_IDX_TYPE;
            data[OFFSET_IDX_TYPE] = IDX_GENERIC;
            ByteConversion.intToByte(collectionId, data, OFFSET_COLLECTION_ID);
        }

        public static Indexable deserialize(byte[] data, int start, int len) throws EXistException {
            return ValueIndexFactory.deserialize(data, start + OFFSET_VALUE, len - OFFSET_VALUE);
        }
    }

    private static class SimplePrefixValue extends Value {

        public static int LENGTH_VALUE_TYPE = 1; //sizeof byte

        public SimplePrefixValue(int collectionId, int type) {
            len = SimpleValue.LENGTH_IDX_TYPE + Collection.LENGTH_COLLECTION_ID + LENGTH_VALUE_TYPE;
            data = new byte[len];
            data[SimpleValue.OFFSET_IDX_TYPE] = IDX_GENERIC;
            ByteConversion.intToByte(collectionId, data, SimpleValue.OFFSET_COLLECTION_ID);
            data[SimpleValue.OFFSET_VALUE] = (byte) type;
            pos = SimpleValue.OFFSET_IDX_TYPE;
        }
    }

    private static class QNameValue extends Value {

    	public static int LENGTH_IDX_TYPE = 1; //sizeof byte
    	public static int LENGTH_QNAME_TYPE = 1; //sizeof byte
    	
    	public static int OFFSET_IDX_TYPE = 0;		
		public static int OFFSET_COLLECTION_ID = OFFSET_IDX_TYPE + LENGTH_IDX_TYPE; //1
		public static int OFFSET_QNAME_TYPE = OFFSET_COLLECTION_ID + Collection.LENGTH_COLLECTION_ID; //3        
		public static int OFFSET_NS_URI = OFFSET_QNAME_TYPE + LENGTH_QNAME_TYPE; //4
		public static int OFFSET_LOCAL_NAME = OFFSET_NS_URI + SymbolTable.LENGTH_NS_URI; //6
		public static int OFFSET_VALUE = OFFSET_LOCAL_NAME + SymbolTable.LENGTH_LOCAL_NAME; //8

        public QNameValue(int collectionId) {
            len = LENGTH_IDX_TYPE + Collection.LENGTH_COLLECTION_ID;
            data = new byte[len];
            data[OFFSET_IDX_TYPE] = IDX_QNAME;
            ByteConversion.intToByte(collectionId, data, OFFSET_COLLECTION_ID);
            pos = OFFSET_IDX_TYPE;
        }

        public QNameValue(int collectionId, QName qname, Indexable atomic, SymbolTable symbols) throws EXistException {
            data = atomic.serializeValue(OFFSET_VALUE);
            len = data.length;
            pos = OFFSET_IDX_TYPE;
            final short namespaceId = symbols.getNSSymbol(qname.getNamespaceURI());
            final short localNameId = symbols.getSymbol(qname.getLocalName());
            data[OFFSET_IDX_TYPE] = IDX_QNAME;
            ByteConversion.intToByte(collectionId, data, OFFSET_COLLECTION_ID);
            data[OFFSET_QNAME_TYPE] = qname.getNameType();
            ByteConversion.shortToByte(namespaceId, data, OFFSET_NS_URI);
            ByteConversion.shortToByte(localNameId, data, OFFSET_LOCAL_NAME);
        }

        public static Indexable deserialize(byte[] data, int start, int len) throws EXistException {
            return ValueIndexFactory.deserialize(data, start + OFFSET_VALUE, len - OFFSET_VALUE);
        }

        public static byte getType(byte[] data, int start) {
            return data[start + OFFSET_QNAME_TYPE];
        }
    }

    private static class QNamePrefixValue extends Value {

        public static int LENGTH_VALUE_TYPE = 1; //sizeof byte

        public QNamePrefixValue(int collectionId, QName qname, int type, SymbolTable symbols) {
            len = QNameValue.OFFSET_VALUE + LENGTH_VALUE_TYPE;
            data = new byte[len];
            data[QNameValue.OFFSET_IDX_TYPE] = IDX_QNAME;
            ByteConversion.intToByte(collectionId, data, QNameValue.OFFSET_COLLECTION_ID);
            final short namespaceId = symbols.getNSSymbol(qname.getNamespaceURI());
            final short localNameId = symbols.getSymbol(qname.getLocalName());
            data[QNameValue.OFFSET_QNAME_TYPE] = qname.getNameType();
            ByteConversion.shortToByte(namespaceId, data, QNameValue.OFFSET_NS_URI);
            ByteConversion.shortToByte(localNameId, data, QNameValue.OFFSET_LOCAL_NAME);
            data[QNameValue.OFFSET_VALUE] = (byte) type;
            pos = QNameValue.OFFSET_IDX_TYPE;
        }
    }

    private class ValueIndexStreamListener extends AbstractStreamListener {

        private Stack contentStack = null;

        public ValueIndexStreamListener() {
            super();
        }

        public void startElement(Txn transaction, ElementImpl element, NodePath path) {
            GeneralRangeIndexSpec rSpec = doc.getCollection().getIndexByPathConfiguration(broker, path);
            QNameRangeIndexSpec qSpec = doc.getCollection().getIndexByQNameConfiguration(broker, element.getQName());
            if (rSpec != null || qSpec != null) {
                if (contentStack == null) contentStack = new Stack();
                XMLString contentBuf = new XMLString();
                contentStack.push(contentBuf);
            }
            super.startElement(transaction, element, path);
        }

        public void attribute(Txn transaction, AttrImpl attrib, NodePath path) {
            GeneralRangeIndexSpec rSpec = doc.getCollection().getIndexByPathConfiguration(broker, path);
            QNameRangeIndexSpec qSpec = doc.getCollection().getIndexByQNameConfiguration(broker, attrib.getQName());
            if (rSpec != null)
                storeAttribute(attrib, path, NativeValueIndex.WITHOUT_PATH, rSpec, false);
            if (qSpec != null)
                storeAttribute(attrib, path, NativeValueIndex.WITHOUT_PATH, qSpec, false);

				 switch(attrib.getType()) {
					 case AttrImpl.ID:
						 storeAttribute(attrib, attrib.getValue(), path, NativeValueIndex.WITHOUT_PATH, Type.ID, NativeValueIndex.IDX_GENERIC, false);
						 break;
					 case AttrImpl.IDREF:
						 storeAttribute(attrib, attrib.getValue(), path, NativeValueIndex.WITHOUT_PATH, Type.IDREF, NativeValueIndex.IDX_GENERIC, false);
						 break;
					 case AttrImpl.IDREFS:
						 StringTokenizer tokenizer = new StringTokenizer(attrib.getValue(), " ");
						 while (tokenizer.hasMoreTokens()) {
							 storeAttribute(attrib, tokenizer.nextToken(), path, NativeValueIndex.WITHOUT_PATH, Type.IDREF, NativeValueIndex.IDX_GENERIC, false);
						 }
						 break;
					 default:
						 // do nothing special
				 }
            super.attribute(transaction, attrib, path);
        }

        public void endElement(Txn transaction, ElementImpl element, NodePath path) {
            GeneralRangeIndexSpec rSpec = doc.getCollection().getIndexByPathConfiguration(broker, path);
            QNameRangeIndexSpec qSpec = doc.getCollection().getIndexByQNameConfiguration(broker, element.getQName());
            if (rSpec != null || qSpec != null) {
                XMLString content = (XMLString) contentStack.pop();
                if (rSpec != null)
                    storeElement(element, content.toString(), RangeIndexSpec.indexTypeToXPath(rSpec.getIndexType()), NativeValueIndex.IDX_GENERIC, false);
                if (qSpec != null)
                    storeElement(element, content.toString(), RangeIndexSpec.indexTypeToXPath(qSpec.getIndexType()), NativeValueIndex.IDX_QNAME, false);
            }
            super.endElement(transaction, element, path);
        }

        public void characters(Txn transaction, CharacterDataImpl text, NodePath path) {
            if (contentStack != null && !contentStack.isEmpty()) {
                for (int i = 0; i < contentStack.size(); i++) {
                    XMLString next = (XMLString) contentStack.get(i);
                    next.append(text.getXMLString());
                }
            }
            super.characters(transaction, text, path);
        }

        public IndexWorker getWorker() {
            return null;
        }
    }
}
