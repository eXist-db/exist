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
import org.dbxml.core.filer.BTreeCallback;
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
import org.exist.dom.XMLUtil;
import org.exist.storage.io.VariableByteArrayInput;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.store.BFile;
import org.exist.util.ByteConversion;
import org.exist.util.FastQSort;
import org.exist.util.Lock;
import org.exist.util.LockException;
import org.exist.util.ReadOnlyException;
import org.exist.xquery.Constants;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.w3c.dom.Attr;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

/**
 * Maintains an index on typed node values.
 * 
 * TODO: Check correct types during validation.
 * 
 * @author wolf
 */
public class NativeValueIndex {

    private final static Logger LOG = Logger.getLogger(NativeValueIndex.class);
    
    private DBBroker broker;
    private BFile db;
    
    private TreeMap pending = new TreeMap();
    
    private DocumentImpl doc;
    
    private VariableByteOutputStream os = new VariableByteOutputStream();
    
    public NativeValueIndex(DBBroker broker, BFile valuesDb) {
        this.broker = broker;
        this.db = valuesDb;
    }
    
    public void storeText(ValueIndexSpec spec, Text node, NodeProxy proxy) {
        AtomicValue atomic = convertToAtomic(spec, node.getData());
        if(atomic == null)
            return;		// skip
        ArrayList buf;
        if (pending.containsKey(atomic))
            buf = (ArrayList) pending.get(atomic);
        else {
            buf = new ArrayList(50);
            pending.put(atomic, buf);
        }
        buf.add(proxy);
    }
    
    public void storeAttribute(ValueIndexSpec spec, Attr node, NodeProxy proxy) {
        AtomicValue atomic = convertToAtomic(spec, node.getValue());
        if(atomic == null)
            return;		// skip
        ArrayList buf;
        if (pending.containsKey(atomic))
            buf = (ArrayList) pending.get(atomic);
        else {
            buf = new ArrayList(50);
            pending.put(atomic, buf);
        }
        buf.add(proxy);
    }
    
    public void setDocument(DocumentImpl document) {
        this.doc = document;
    }
    
    public void flush() {
        if (pending.size() == 0) return;
        NodeProxy proxy;
        Indexable indexable;
        ArrayList idList;
        int len;
        Value ref;
        Map.Entry entry;
        // get collection id for this collection
        long prevId;
        long cid;
        short collectionId = doc.getCollection().getId();
        Lock lock = db.getLock();
        try {
            for (Iterator i = pending.entrySet().iterator(); i.hasNext();) {
                entry = (Map.Entry) i.next();
                indexable = (Indexable) entry.getKey();
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
                }
                ref = new Value(indexable.serialize(collectionId));
                try {
                    lock.acquire(Lock.WRITE_LOCK);
                    if (db.append(ref, os.data()) < 0) {
                        LOG.warn("could not save index for value");
                        continue;
                    }
                } catch (LockException e) {
                    LOG.error("could not acquire lock on values.dbx", e);
                } catch (IOException e) {
                    LOG.error("io error while writing value index.", e);
                } finally {
                    lock.release();
                }
            }
        } catch (ReadOnlyException e) {
            LOG.warn("database is read-only");
            return;
        }
        pending.clear();
    }
    
    public void sync() {
        Lock lock = db.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            try {
                db.flush();
            } catch (DBException dbe) {
                LOG.warn(dbe);
            }
        } catch (LockException e) {
            LOG.warn("could not acquire lock for values.dbx", e);
        } finally {
            lock.release();
        }
    }
    
    /**
     * Drop all index entries for the given collection.
     * 
     * @param collection
     */
    public void dropIndex(Collection collection) {
        LOG.debug("removing elements ...");
        Value ref = new ElementValue(collection.getId());
        IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
        Lock lock = db.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            db.removeAll(query);
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
    
    /**
     * Drop all index entries for the given document.
     * 
     * @param doc
     * @throws ReadOnlyException
     */
    public void dropIndex(DocumentImpl doc) throws ReadOnlyException {
        //	  drop element-index
        short collectionId = doc.getCollection().getId();
        Value ref = new ElementValue(collectionId);
        IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
        Lock lock = db.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            ArrayList elements = db.findKeys(query);
            if (LOG.isDebugEnabled()) {
                LOG.debug("removeDocument() - " + "found " + elements.size()
                        + " elements.");
            }

            Value key;
            Value value;
            byte[] data;
            VariableByteArrayInput is;
            VariableByteOutputStream os;
            int len;
            int docId;
            long delta;
            long address;
            boolean changed;
            for (int i = 0; i < elements.size(); i++) {
                key = (Value) elements.get(i);
                value = db.get(key);
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
                            LOG.debug("Copying " + len);
                            for (int j = 0; j < len; j++) {
                                delta = is.readLong();
                                os.writeLong(delta);
                            }
                        } else {
                            changed = true;
                            // skip
                            is.skip(len);
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
                    if (os.data().size() == 0) {
                        db.remove(key);
                    } else {
                        if (db.put(key, os.data()) < 0)
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("removeDocument() - "
                                        + "could not save value index");
                            }
                    }
                }
            }
        } catch (LockException e) {
            LOG.warn("removeDocument(String) - "
                    + "could not acquire lock on values.dbx", e);
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
    
    public void reindex(DocumentImpl oldDoc, NodeImpl node) {
        if (pending.size() == 0) return;
        Lock lock = db.getLock();
        Map.Entry entry;
        Indexable indexable;
        List oldList = new ArrayList(), idList;
        NodeProxy p;
        VariableByteInput is = null;
        int len, docId;
        byte[] data;
        Value ref;
        Value val;
        short sym, nsSym;
        short collectionId = oldDoc.getCollection().getId();
        long gid, prevId, cid, address;
        try {
            // iterate through elements
            for (Iterator i = pending.entrySet().iterator(); i.hasNext();) {
                entry = (Map.Entry) i.next();
                indexable = (Indexable) entry.getKey();
                idList = (ArrayList) entry.getValue();
                ref = new Value(indexable.serialize(collectionId));
                
                // try to retrieve old index entry for the element
                try {
                    lock.acquire(Lock.WRITE_LOCK);
                    is = db.getAsStream(ref);
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
                                    is.copyTo(os, len);
                                } else {
                                    // copy nodes to new list
                                    gid = 0;
                                    for (int j = 0; j < len; j++) {
                                        gid += is.readLong();
                                        if (node == null
                                                && oldDoc.getTreeLevel(gid) < oldDoc
                                                .reindexRequired()) {
                                            idList.add(new NodeProxy(oldDoc, gid));
                                        } else if (node != null
                                                && (!XMLUtil.isDescendant(oldDoc,
                                                        node.getGID(), gid))) {
                                            oldList.add(new NodeProxy(oldDoc, gid));
                                        }
                                    }
                                }
                            }
                        } catch (EOFException e) {
                        } catch (IOException e) {
                            LOG.error("io-error while updating index for value", e);
                        }
                    }
                    if (node != null) idList.addAll(oldList);
                    // write out the updated list
                    FastQSort.sort(idList, 0, idList.size() - 1);
                    len = idList.size();
                    os.writeInt(doc.getDocId());
                    os.writeInt(len);
                    prevId = 0;
                    for (int j = 0; j < len; j++) {
                        p = (NodeProxy) idList.get(j);
                        cid = p.gid - prevId;
                        prevId = p.gid;
                        os.writeLong(cid);
                    }
                    if (is == null)
                        db.put(ref, os.data());
                    else {
                        address = ((BFile.PageInputStream) is).getAddress();
                        db.update(address, ref, os.data());
                    }
                } catch (LockException e) {
                    LOG.error("could not acquire lock for value index", e);
                    return;
                } catch (IOException e) {
                    LOG.error("io error while reindexing", e);
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
    
    public void remove() {
        if (pending.size() == 0) return;
        Lock lock = db.getLock();
        Map.Entry entry;
        Indexable indexable;
        List newList = new ArrayList(), idList;
        NodeProxy p;
        VariableByteArrayInput is;
        int len, docId;
        byte[] data;
        Value ref;
        Value val;
        short sym, nsSym;
        short collectionId = doc.getCollection().getId();
        long delta, last, gid;
        try {
            // iterate through elements
            for (Iterator i = pending.entrySet().iterator(); i.hasNext();) {
                try {
                    lock.acquire(Lock.WRITE_LOCK);
                    entry = (Map.Entry) i.next();
                    indexable = (Indexable) entry.getKey();
                    idList = (ArrayList) entry.getValue();
                    ref = new Value(indexable.serialize(collectionId));
                    
                    val = db.get(ref);
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
                                        is.copyTo(os, len);
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
                                        if (!containsNode(idList, gid)) {
                                            newList.add(new NodeProxy(doc, gid));
                                        }
                                    }
                                }
                            }
                        } catch (EOFException e) {
                            LOG
                            .error("end-of-file while updating value index", e);
                        } catch (IOException e) {
                            LOG.error("io-error while updating value index", e);
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
                    }
                    if (val == null) {
                    	db.put(ref, os.data());
                    } else {
                    	db.update(val.getAddress(), ref, os.data());
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
        pending.clear();
    }
    
    private final static boolean containsNode(List list, long gid) {
        for (int i = 0; i < list.size(); i++)
            if (((NodeProxy) list.get(i)).gid == gid) return true;
        return false;
    }
    
    public NodeSet find(int relation, DocumentSet docs, NodeSet contextSet, Indexable value) 
    throws TerminatedException {
        int idxOp =  checkRelationOp(relation);
        NodeSet result = new ExtArrayNodeSet();
        SearchCallback callback = new SearchCallback(docs, contextSet, result);
        Lock lock = db.getLock();
        for (Iterator iter = docs.getCollectionIterator(); iter.hasNext();) {
			Collection collection = (Collection) iter.next();
			short collectionId = collection.getId();
			byte[] key = value.serialize(collectionId);
			IndexQuery query = new IndexQuery(idxOp, new Value(key));
			try {
				lock.acquire();
				try {
					db.query(query, callback);
				} catch (IOException ioe) {
					LOG.debug(ioe);
				} catch (BTreeException bte) {
					LOG.debug(bte);
				}
			} catch (LockException e) {
				LOG.debug(e);
			} finally {
				lock.release();
			}
        }
        return result;
    }
    
    public NodeSet match(DocumentSet docs, NodeSet contextSet, String expr, int type)
    throws TerminatedException, EXistException {
    	// if the regexp starts with a char sequence, we restrict the index scan to entries starting with
    	// the same sequence. Otherwise, we have to scan the whole index.
    	StringBuffer term = new StringBuffer();
		for (int j = 0; j < expr.length(); j++)
			if (Character.isLetterOrDigit(expr.charAt(j)))
				term.append(expr.charAt(j));
			else
				break;
		StringValue startTerm = null;
		if(term.length() > 0) {
			startTerm = new StringValue(term.toString());
		}
		
		TermMatcher comparator = new RegexMatcher(expr, type);
        NodeSet result = new ExtArrayNodeSet();
        RegexCallback callback = new RegexCallback(docs, contextSet, result, comparator);
        Lock lock = db.getLock();
        for (Iterator iter = docs.getCollectionIterator(); iter.hasNext();) {
			Collection collection = (Collection) iter.next();
			short collectionId = collection.getId();
			byte[] key;
			if(startTerm != null)
				key = startTerm.serialize(collectionId);
			else {
				key = new byte[3];
				ByteConversion.shortToByte(collectionId, key, 0);
				key[2] = (byte) Type.STRING;
			}
			IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, new Value(key));
			try {
				lock.acquire();
				try {
					db.query(query, callback);
				} catch (IOException ioe) {
					LOG.debug(ioe);
				} catch (BTreeException bte) {
					LOG.debug(bte);
				}
			} catch (LockException e) {
				LOG.debug(e);
			} finally {
				lock.release();
			}
        }
        return result;
    }
    
    private int checkRelationOp(int relation) {
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
    
    private AtomicValue convertToAtomic(ValueIndexSpec spec, String value) {
        final StringValue str = new StringValue(value);
        AtomicValue atomic = null;
        if(Type.subTypeOf(spec.getType(), Type.STRING))
            atomic = str;
        else {
            try {
                atomic = str.convertTo(spec.getType());
            } catch (XPathException e) {
                LOG.warn("Node value: '" + value + "' cannot be converted to type " + 
                        Type.getTypeName(spec.getType()));
            }
        }
        if(!(atomic instanceof Indexable)) {
            LOG.warn("The specified type: " + Type.getTypeName(spec.getType()) +
                    " cannot be used as index key. It does not implement interface Indexable.");
            atomic = null;
        }
        return atomic;        
    }
    
    private class SearchCallback implements BTreeCallback {
        
        DocumentSet docs;
        NodeSet contextSet;
        NodeSet result;
        
        public SearchCallback(DocumentSet docs, NodeSet contextSet, NodeSet result) {
            this.docs = docs;
            this.contextSet = contextSet;
            this.result = result;
        }
        
        /* (non-Javadoc)
         * @see org.dbxml.core.filer.BTreeCallback#indexInfo(org.dbxml.core.data.Value, long)
         */
        public boolean indexInfo(Value value, long pointer)
                throws TerminatedException {
            VariableByteInput is = null;
			try {
				is = db.getAsStream(pointer);
			} catch (IOException ioe) {
				LOG.warn(ioe.getMessage(), ioe);
			}
			if (is == null)
				return true;
			try {
                int sizeHint = -1;
                while (is.available() > 0) {
                	int docId = is.readInt();
                	int len = is.readInt();
                	if ((doc = docs.getDoc(docId)) == null
                			|| (contextSet != null && !contextSet.containsDoc(doc))) {
                		is.skip(len);
                		continue;
                	}
                	if (contextSet != null)
                		sizeHint = contextSet.getSizeHint(doc);
                	long gid = 0;
                	NodeProxy current, parent;
                	for (int j = 0; j < len; j++) {
                		gid = gid + is.readLong();
                		current = new NodeProxy(doc, gid, Node.TEXT_NODE);
                		
                		// if a context set is specified, we can directly check if the
                		// matching text node is a descendant of one of the nodes
                		// in the context set.
                		if (contextSet != null) {
                			parent = contextSet.parentWithChild(current, false, true, -1);
                			if (parent != null) {
                				result.add(parent, sizeHint);
                			}
                		// otherwise, we add all text nodes without check
                		} else {
                			result.add(current, sizeHint);
                		}
                	}
                }
			} catch (EOFException e) {
			    // EOF is expected here
            } catch (IOException e) {
                LOG.warn("io error while reading index", e);
            }
            return false;
        }
    }
    
    private class RegexCallback extends SearchCallback {
    	
    	private TermMatcher matcher;
    	
    	public RegexCallback(DocumentSet docs, NodeSet contextSet, NodeSet result, TermMatcher matcher) {
    		super(docs, contextSet, result);
    		this.matcher = matcher;
    	}
    	
    	/* (non-Javadoc)
		 * @see org.exist.storage.NativeValueIndex.SearchCallback#indexInfo(org.dbxml.core.data.Value, long)
		 */
		public boolean indexInfo(Value value, long pointer)
				throws TerminatedException {
			StringValue val = new StringValue(null);
			val.deserialize(value.getData());
			if(matcher.matches(val.getStringValue())) {
				super.indexInfo(value, pointer);
			}
			return true;
		}
    }
}
