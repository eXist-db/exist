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
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.dbxml.core.DBException;
import org.dbxml.core.data.Value;
import org.dbxml.core.filer.BTreeCallback;
import org.dbxml.core.filer.BTreeException;
import org.dbxml.core.indexer.IndexQuery;
import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
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
import org.exist.xquery.value.IntegerValue;
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
                try {
                    LOG.debug("Writing " + ((AtomicValue)indexable).getStringValue() + ": " + idList.size());
                } catch (XPathException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
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
                            LOG.debug("Skipping " + len);
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
                    if (db.put(key, os.data()) < 0)
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("removeDocument() - "
                                        + "could not save value index");
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
    
    public NodeSet find(int relation, DocumentSet docs, NodeSet contextSet, Indexable value) 
    throws TerminatedException {
        int idxOp = checkRelationOp(relation);        
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
    
    public static void main(String args[]) {
        byte[] d = ByteConversion.longToByte(56473);
        System.out.print(56473 + " = ");
        for(int i = 0; i < d.length; i++)
            System.out.print(Integer.toHexString(d[i] & 0xFF) + " ");
        System.out.println();
        d = ByteConversion.longToByte(774663);
        System.out.print(774663 + " = ");
        for(int i = 0; i < d.length; i++)
            System.out.print(Integer.toHexString(d[i] & 0xFF) + " ");
        System.out.println();
        Value v1 = new Value(new IntegerValue(56473).serialize((short)2));
        Value v2 = new Value(new IntegerValue(774663).serialize((short)2));
        d = v1.getData();
        System.out.print("v1 = ");
        for(int i = 0; i < d.length; i++)
            System.out.print(Integer.toHexString(d[i] & 0xFF) + " ");
        System.out.println();
        d = v2.getData();
        System.out.print("v2 = ");
        for(int i = 0; i < d.length; i++)
            System.out.print(Integer.toHexString(d[i] & 0xFF) + " ");
        System.out.println();
        System.out.println(v1.compareTo(v2));
    }
}
