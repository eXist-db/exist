package org.exist.storage.btree;

/*
 *  dbXML License, Version 1.0
 *
 *  Copyright (c) 1999-2001 The dbXML Group, L.L.C.
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1. Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the
 *  distribution.
 *
 *  3. The end-user documentation included with the redistribution,
 *  if any, must include the following acknowledgment:
 *  "This product includes software developed by
 *  The dbXML Group (http://www.dbxml.com/)."
 *  Alternately, this acknowledgment may appear in the software
 *  itself, if and wherever such third-party acknowledgments normally
 *  appear.
 *
 *  4. The names "dbXML" and "The dbXML Group" must not be used to
 *  endorse or promote products derived from this software without
 *  prior written permission. For written permission, please contact
 *  info@dbxml.com.
 *
 *  5. Products derived from this software may not be called "dbXML",
 *  nor may "dbXML" appear in their name, without prior written
 *  permission of The dbXML Group.
 *
 *  THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 *  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED.  IN NO EVENT SHALL THE DBXML GROUP OR ITS CONTRIBUTORS
 *  BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY,
 *  OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 *  OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 *  BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *  $Id$
 */
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;

import org.exist.storage.BrokerPool;
import org.exist.storage.BufferStats;
import org.exist.storage.CacheManager;
import org.exist.storage.cache.Cache;
import org.exist.storage.cache.Cacheable;
import org.exist.storage.cache.LRDCache;
import org.exist.storage.log.LogEntryTypes;
import org.exist.storage.log.LogException;
import org.exist.storage.log.LogManager;
import org.exist.storage.log.Loggable;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.Txn;
import org.exist.util.ArrayUtils;
import org.exist.util.ByteConversion;
import org.exist.xquery.TerminatedException;

/**
 *  BTree represents a Variable Magnitude Simple-Prefix B+Tree File. A BTree is
 *  a bit flexible in that it can be used for set or map-based indexing.
 *  HashFiler uses the BTree as a set for producing RecordSet entries. The
 *  Indexers use BTree as a map for indexing entity and attribute values in
 *  Documents. <br>
 *  <br>
 *  For those who don't know how a Simple-Prefix B+Tree works, the primary
 *  distinction is that instead of promoting actual keys to branch pages, when
 *  leaves are split, a shortest-possible separator is generated at the pivot.
 *  That separator is what is promoted to the parent branch (and continuing up
 *  the list). As a result, actual keys and pointers can only be found at the
 *  leaf level. This also affords the index the ability to ignore costly merging
 *  and redistribution of pages when deletions occur. Deletions only affect leaf
 *  pages in this implementation, and so it is entirely possible for a leaf page
 *  to be completely empty after all of its keys have been removed. <br>
 *  <br>
 *  Also, the Variable Magnitude attribute means that the btree attempts to
 *  store as many values and pointers on one page as is possible.
 */
public class BTree extends Paged {

    /** Used as return value, if a value was not found */
    public final static long KEY_NOT_FOUND = -1;

    /** Type of BTreeNode/Page */
	protected final static byte LEAF = 1;
    
    /** Type of BTreeNode/Page */
	protected final static byte BRANCH = 2;

    /** Log entry type for an insert value operation */
    public final static byte LOG_INSERT_VALUE = 0x20;
    
    /** Log entry type for creation of a new btree node */
    public final static byte LOG_CREATE_BNODE = 0x21;
    
    /** Log entry type for a page update resulting from a page split */
    public final static byte LOG_UPDATE_PAGE = 0x22;
    
    /** Log entry type for a parent page change resulting from a page split */
    public final static byte LOG_SET_PARENT = 0x23;
    
    /** Log entry type for a value update */
    public final static byte LOG_UPDATE_VALUE = 0x24;
    
    /** Log entry type for removing a value */
    public final static byte LOG_REMOVE_VALUE = 0x25;
    
	static {
        // register the log entry types used for the btree
		LogEntryTypes.addEntryType(LOG_INSERT_VALUE, InsertValueLoggable.class);
        LogEntryTypes.addEntryType(LOG_UPDATE_VALUE, UpdateValueLoggable.class);
        LogEntryTypes.addEntryType(LOG_REMOVE_VALUE, RemoveValueLoggable.class);
		LogEntryTypes.addEntryType(LOG_CREATE_BNODE, CreateBTNodeLoggable.class);
        LogEntryTypes.addEntryType(LOG_UPDATE_PAGE, UpdatePageLoggable.class);
        LogEntryTypes.addEntryType(LOG_SET_PARENT, SetParentLoggable.class);
	}
	
    protected CacheManager cacheManager;
    
    /** Cache of BTreeNode(s) */
    protected Cache cache;
    
    protected int growthThreshold;
    
    /** Size of BTreeNode cache */
    protected int buffers;

    /** Fileheader of a BTree file */
	private BTreeFileHeader fileHeader;
	
    /** The LogManager for writing the transaction log */
    protected LogManager logManager;
    
    protected byte fileId;
    
    protected boolean isTransactional;
    
	protected BTree(BrokerPool pool, byte fileId, CacheManager cacheManager, int growthThreshold) {
		super();
        this.cacheManager = cacheManager;
        this.buffers = cacheManager.getDefaultInitialSize();
        this.growthThreshold = growthThreshold;
        this.fileId = fileId;
		fileHeader = (BTreeFileHeader) getFileHeader();
		fileHeader.setPageCount(0);
		fileHeader.setTotalCount(0);
        isTransactional = pool.isTransactional();
        if (isTransactional)
            logManager = pool.getTransactionManager().getLogManager();
	}

	public BTree(BrokerPool pool, byte fileId, CacheManager cacheManager, File file, int growthThreshold) {
        this(pool, fileId, cacheManager, growthThreshold);
		setFile(file);
	}

	public boolean open(short expectedVersion) throws DBException {
		if (super.open(expectedVersion)) {
		    initCache();
			return true;
		} else
			return false;
	}
	
	protected boolean create(short fixedKeyLen) throws DBException {
        if (super.create()) {
            initCache();
			try {    
				createRootNode(null);
			} catch (IOException e) {
				LOG.warn("Can not create database file " 
                        + getFile().getPath(), e);
                return false;
			}
            fileHeader.setFixedKeyLen(fixedKeyLen);
            try {
                fileHeader.write();
            } catch (IOException e) {
                throw new DBException("Error while writing file header: " + e.getMessage());
            }
        }
        
		return true;
	}

	private void initCache() {
        cache = new LRDCache(cacheManager.getDefaultInitialSize(), 1.5, growthThreshold);
        cache.setFileName(getFile().getName());
        cacheManager.registerCache(cache);
	}
	
	/**
	 *  addValue adds a Value to the BTree and associates a pointer with it. The
	 *  pointer can be used for referencing any type of data, it just so happens
	 *  that dbXML uses it for referencing pages of associated data in the BTree
	 *  file or other files.
	 *
	 *@param  value               The Value to add
	 *@param  pointer             The pointer to associate with it
	 *@return                     The previous value for the pointer (or -1)
	 *@exception  IOException     Description of the Exception
	 *@exception  BTreeException  Description of the Exception
	 */
    public long addValue(Value value, long pointer) throws IOException, BTreeException {
        return addValue(null, value, pointer);
    }
    
	public long addValue(Txn transaction, Value value, long pointer) throws IOException, BTreeException {
		return getRootNode().addValue(transaction, value, pointer);
	}

	/**
	 *  removeValue removes a Value from the BTree and returns the associated
	 *  pointer for it.
	 *
	 *@param  value               The Value to remove
	 *@return                     The pointer that was associated with it
	 *@exception  IOException     Description of the Exception
	 *@exception  BTreeException  Description of the Exception
	 */
    public long removeValue(Value value) throws IOException, BTreeException {
        return removeValue(null, value);
    }
    
	public long removeValue(Txn transaction, Value value) throws IOException, BTreeException {
		return getRootNode().removeValue(transaction, value);
	}

	/**
	 *  findValue finds a Value in the BTree and returns the associated pointer
	 *  for it.
	 *
	 *@param  value               The Value to find
	 *@return                     The pointer that was associated with it
	 *@exception  IOException     Description of the Exception
	 *@exception  BTreeException  Description of the Exception
	 */
	public long findValue(Value value) throws IOException, BTreeException {
		return getRootNode().findValue(value);
	}

	/**
	 *  query performs a query against the BTree and performs callback
	 *  operations to report the search results.
	 *
	 *@param  query               The IndexQuery to use (or null for everything)
	 *@param  callback            The callback instance
	 *@exception  IOException     Description of the Exception
	 *@exception  BTreeException  Description of the Exception
	 */
	public void query(IndexQuery query, BTreeCallback callback)
		throws IOException, BTreeException, TerminatedException {
		if (query != null && query.getOperator() == IndexQuery.TRUNC_RIGHT) {
			Value val1 = query.getValue(0);
			byte data1[] = val1.getData();
			byte data2[] = new byte[data1.length];
			System.arraycopy(data1, 0, data2, 0, data1.length);
			data2[data2.length - 1] += 1;
			query = new IndexQuery(query.getOperator(), val1, new Value(data2));
		}
		getRootNode().query(query, callback);
	}

    /**
     *  Executes a query against the BTree and performs callback
     *  operations to report the search results. This method takes an
     *  additional prefix value. Only BTree keys starting with the specified
     *  prefix are considered. Search through the tree is thus restricted to
     *  a given key range.
     *
     *@param  query The IndexQuery to use (or null for everything)
     *@param prefix a prefix value
     *@param  callback The callback instance
     *@exception  IOException
     *@exception  BTreeException
     */
    public void query(IndexQuery query, Value prefix, BTreeCallback callback)
        throws IOException, BTreeException, TerminatedException {
        getRootNode().query(query, prefix, callback);
    }
    
    public void remove(IndexQuery query, BTreeCallback callback)
    throws IOException, BTreeException, TerminatedException {
        remove(null, query, callback);
    }
    
	public void remove(Txn transaction, IndexQuery query, BTreeCallback callback)
		throws IOException, BTreeException, TerminatedException {
		if (query != null && query.getOperator() == IndexQuery.TRUNC_RIGHT) {
			Value val1 = query.getValue(0);
			byte data1[] = val1.getData();
			byte data2[] = new byte[data1.length];
			System.arraycopy(data1, 0, data2, 0, data1.length);
			data2[data2.length - 1] += 1;
			query = new IndexQuery(query.getOperator(), val1, new Value(data2));
		}
		getRootNode().remove(transaction, query, callback);
	}

	private BTreeNode getBTreeNode(long page, BTreeNode parent) {
		try {
			BTreeNode node = (BTreeNode) cache.get(page);
			if (node == null) {
				Page p = getPage(page);
				node = new BTreeNode(p);
				node.read();
			}
			int increment = node.getStatus() == BRANCH ? 2 : 1;
			cache.add(node, increment);
			return node;
		} catch (IOException e) {
			LOG.warn("Failed to get btree node on page " + page, e);
			return null;
		}
	}

	private BTreeNode createBTreeNode(Txn transaction, byte status, BTreeNode parent) {
		try {
			Page p = getFreePage();
			BTreeNode node = new BTreeNode(p);
            if (transaction != null && isTransactional) {
                Loggable loggable = 
                    new CreateBTNodeLoggable(transaction, fileId, status, p.getPageNum(), 
                            parent != null ? parent.page.getPageNum() : -1);
                writeToLog(loggable, node);
            }
			node.ph.setStatus(status);
			node.setValues(new Value[0]);
			node.setPointers(new long[0]);
			node.setParent(parent);
			node.write();
			//cache.add(node);
			return node;
		} catch (IOException e) {
            LOG.warn("Failed to create a btree node", e);
			return null;
		}
	}

	protected void setRootNode(BTreeNode rootNode) throws IOException {
		fileHeader.setRootPage(rootNode.page.getPageNum());
		fileHeader.write();
		cache.add(rootNode, 2);
	}

	protected long createRootNode(Txn transaction) throws IOException {
		BTreeNode root = createBTreeNode(transaction, LEAF, null);
		setRootNode(root);
		return root.page.getPageNum();
	}

	protected BTreeNode getRootNode() {
		try {
			BTreeNode node = (BTreeNode) cache.get(fileHeader.getRootPage());
			if (node == null) {
				Page p = getPage(fileHeader.getRootPage());
				node = new BTreeNode(p);
				node.read();
			}
			cache.add(node, 2);
			return node;
		} catch (IOException e) {
            LOG.warn("Failed to get root btree node", e);
			return null;
		}
	}

    public void dump(Writer writer) throws IOException, BTreeException {
        BTreeNode root = getRootNode();
        LOG.debug("ROOT = " + root.page.getPageNum());
        root.dump(writer);
    }
    
    /**
     * @see org.exist.storage.btree.Paged#drop()
     */
    public boolean drop() throws DBException {
        return getFile().delete();
    }

    /**
     * @see org.exist.storage.btree.Paged#flush()
     */
	public boolean flush() throws DBException {
		cache.flush();
		super.flush();
		return true;
	}

    /**
     * @see org.exist.storage.btree.Paged#close()
     */
	public boolean close() throws DBException {
		flush();
		super.close();
		return true;
	}

    protected void dumpValue(Writer writer, Value value) throws IOException {
        writer.write(new String(value.getData()));
    }
    
	/* ---------------------------------------------------------------------------------
	 * Methods used by recovery and transaction management
	 * --------------------------------------------------------------------------------- */
    
    private void writeToLog(Loggable loggable, BTreeNode node) {
        try {
            logManager.writeToLog(loggable);
            node.page.getPageHeader().setLsn(loggable.getLsn());
        } catch (TransactionException e) {
            LOG.warn(e.getMessage(), e);
        }
    }
    
    protected boolean requiresRedo(Loggable loggable, Page page) {
        return loggable.getLsn() > page.getPageHeader().getLsn();
    }
    
	protected void redoCreateBTNode(CreateBTNodeLoggable loggable) throws LogException {
		BTreeNode parent = null;
		BTreeNode node = (BTreeNode) cache.get(loggable.pageNum);
		if (node == null) {
			// node is not yet loaded. Load it
			try {
				Page p = getPage(loggable.pageNum);
				if ((p.getPageHeader().getStatus() == BRANCH ||
						p.getPageHeader().getStatus() == LEAF) &&
						requiresRedo(loggable, p)) {
                    // node already found on disk: read it
					node = new BTreeNode(p);
					node.read();
				    return;
				} else {
                    // create a new node
					node = new BTreeNode(p);
					node.ph.setStatus(loggable.status);
					node.setValues(new Value[0]);
					node.setPointers(new long[0]);
					node.write();
				}
                node.ph.setLsn(loggable.getLsn());
                node.ph.parentPage = loggable.parentNum;
				int increment = node.getStatus() == BRANCH ? 2 : 1;
				cache.add(node, increment);
			} catch (IOException e) {
				throw new LogException(e.getMessage(), e);
			}
		}
	}
    
    protected void redoInsertValue(InsertValueLoggable loggable) throws LogException {
        BTreeNode node = getBTreeNode(loggable.pageNum, null);
        if (requiresRedo(loggable, node.page)) {
            node.setValues(insertArrayValue(node.values, loggable.key, loggable.idx));
            node.setPointers(ArrayUtils.insertArrayLong(node.ptrs, loggable.pointer, loggable.idx));
            node.adjustDataLen(loggable.key);
            node.ph.setLsn(loggable.getLsn());
        }
    }
        
    protected void undoInsertValue(InsertValueLoggable loggable) throws LogException {
        try {
            removeValue(null, loggable.key);
        } catch (BTreeException e) {
            LOG.debug("Failed to undo: " + loggable.dump(), e);
        } catch (IOException e) {
            LOG.debug("Failed to undo: " + loggable.dump(), e);
        }
    }
    
    protected void redoUpdateValue(UpdateValueLoggable loggable) throws LogException {
        BTreeNode node = getBTreeNode(loggable.pageNum, null);
        if (requiresRedo(loggable, node.page)) {
            node.ptrs[loggable.idx] = loggable.pointer;
            node.ph.setLsn(loggable.getLsn());
        }
    }
    
    protected void undoUpdateValue(UpdateValueLoggable loggable) throws LogException {
        try {
            addValue(null, loggable.key, loggable.oldPointer);
        } catch (BTreeException e) {
            LOG.debug("Failed to undo: " + loggable.dump(), e);
        } catch (IOException e) {
            LOG.debug("Failed to undo: " + loggable.dump(), e);
        }
    }
    
    protected void redoRemoveValue(RemoveValueLoggable loggable) throws LogException {
        BTreeNode node = getBTreeNode(loggable.pageNum, null);
        if (node.page.getPageHeader().getLsn() > -1 && requiresRedo(loggable, node.page)) {
//            StringWriter out = new StringWriter();
//            out.write("Remove: " + loggable.idx + "; ");
//            out.write("value: ");
//            try {
//                dumpValue(out, loggable.oldValue);
//                out.write("\n");
//                node.dump(out);
//            } catch (BTreeException e) {
//                // TODO Auto-generated catch block
//                e.printStackTrace();
//            } catch (IOException e) {
//                // TODO Auto-generated catch block
//                e.printStackTrace();
//            }
//            LOG.debug(out);
            node.setValues(deleteArrayValue(node.values, loggable.idx));
            node.setPointers(ArrayUtils.deleteArrayLong(node.ptrs, loggable.idx));
            node.recalculateDataLen();
            node.ph.setLsn(loggable.getLsn());
        }
    }
    
    protected void undoRemoveValue(RemoveValueLoggable loggable) throws LogException {
        try {
            addValue(null, loggable.oldValue, loggable.oldPointer);
        } catch (BTreeException e) {
            LOG.debug("Failed to undo: " + loggable.dump(), e);
        } catch (IOException e) {
            LOG.debug("Failed to undo: " + loggable.dump(), e);
        }
    }
    
    protected void redoUpdatePage(UpdatePageLoggable loggable) throws LogException {
        BTreeNode node = getBTreeNode(loggable.pageNum, null);
        if (requiresRedo(loggable, node.page)) {
            node.setValues(loggable.values);
            node.setPointers(loggable.pointers);
            node.recalculateDataLen();
            node.ph.setLsn(loggable.getLsn());
        }
    }
    
    protected void redoSetParent(SetParentLoggable loggable) throws LogException {
        BTreeNode node = getBTreeNode(loggable.pageNum, null);
        if (requiresRedo(loggable, node.page)) {
            node.ph.parentPage = loggable.parentNum;
            node.ph.setLsn(loggable.getLsn());
            node.saved = false;
        }
    }
    
	protected final class BTreeNode implements Cacheable {
		
		private Page page;
		private BTreePageHeader ph;
		
		private Value[] values = null;
		private long[] ptrs = null;
		
		private int refCount = 0;
		private int timestamp = 0;
		
		private boolean saved = true;

		private int currentDataLen = -1;
		
		public BTreeNode(Page page) {
			this.page = page;
			ph = (BTreePageHeader) page.getPageHeader();
		}

		public void setParent(BTreeNode parent) {
			if (parent != null)
				ph.parentPage = parent.page.getPageNum();
            else
                ph.parentPage = -1;
            saved = false;
		}
		
		public BTreeNode getParent() {
			if (-1 < ph.parentPage) {
				return getBTreeNode(ph.parentPage, null);
			} else {
                return null;
            }
		}
		
        /**
         * @see org.exist.storage.cache.Cacheable#getReferenceCount()
         */
		public int getReferenceCount() {
			return refCount;
		}

        /**
         * @see org.exist.storage.cache.Cacheable#incReferenceCount()
         */
		public int incReferenceCount() {
			if (refCount < Cacheable.MAX_REF)
				++refCount;
			return refCount;
		}

        /**
         * @see org.exist.storage.cache.Cacheable#setReferenceCount(int)
         */
		public void setReferenceCount(int count) {
			refCount = count;
		}
		
		/**
		 * @see org.exist.storage.cache.Cacheable#setTimestamp(int)
		 */
		public void setTimestamp(int timestamp) {
			this.timestamp = timestamp;
		}
		
        /**
         * @see org.exist.storage.cache.Cacheable#allowUnload()
         */
        public boolean allowUnload() {
            return true;
        }
        
		/**
		 * @see org.exist.storage.cache.Cacheable#getTimestamp()
		 */
		public int getTimestamp() {
			return timestamp;
		}
        
		/**
         * @see org.exist.storage.cache.Cacheable#sync()
         */
		public boolean sync() {
			if(isDirty())
				try {
					write();
                    if (isTransactional)
                        logManager.flushToLog(true);
					return true;
				} catch (IOException e) {
					LOG.warn("IO error while writing page: " + page.getPageNum(), e);
				}
			return false;
		}
		
        /**
         * @see org.exist.storage.cache.Cacheable#getKey()
         */
		public long getKey() {
			return page.getPageNum();
		}

        /**
         * @see org.exist.storage.cache.Cacheable#decReferenceCount()
         */
		public int decReferenceCount() {
			return refCount > 0 ? --refCount : 0;
		}

        /**
         * @see org.exist.storage.cache.Cacheable#isDirty()
         */
        public boolean isDirty() {
            return !saved;
        }
        
		public int getStatus() {
			return ph.getStatus();
		}

		public void setValues(Value[] values) {
			this.values = values;
			this.saved = false;
			ph.setValueCount((short) values.length);
		}

		public Value[] getValues() {
			return values;
		}

		public void setPointers(long[] ptrs) {
			this.saved = false;
			this.ptrs = ptrs;
		}

		/**
		 * Returns the raw data size (in bytes) required by this node.
		 * 
		 * @return
		 */
		private int getDataLen() {
			return currentDataLen < 0 ? recalculateDataLen() :
					currentDataLen;
		}
		
		/**
		 * Recalculates the raw data size (in bytes) required by this node.
		 * 
		 * @return
		 */
		private int recalculateDataLen() {
			currentDataLen = ptrs == null ? 0 : ptrs.length << 3;
			if(fileHeader.getFixedKeyLen() < 0)
				currentDataLen += 2 * values.length;
			for (int i = 0; i < values.length; i++)
				currentDataLen += values[i].getLength();
			return currentDataLen;
		}
		
		/**
		 * Add the raw data size required to store the value to the internal
		 * data size of this node.
		 *  
		 * @param value
		 */
		private void adjustDataLen(Value value) {
			if(currentDataLen < 0)
				recalculateDataLen();
			currentDataLen += value.getLength() + 8;
			if(fileHeader.getFixedKeyLen() < 0)
				currentDataLen += 2;
		}
		
		private boolean mustSplit() {
			return getDataLen() > fileHeader.getWorkSize();
		}
		
		public long[] getPointers() {
			return ptrs;
		}

		public void read() throws IOException {
			byte[] data = page.read();
			values = new Value[ph.getValueCount()];
			short keyLen = fileHeader.getFixedKeyLen();
			short valSize = keyLen;
			int p = 0;
			for (int i = 0; i < values.length; i++) {
				if (keyLen < 0) {
					valSize = ByteConversion.byteToShort(data, p);
					p += 2;
				}
				values[i] = new Value(data, p, valSize);
				p += valSize;
			}

			//	Read in the pointers
			final int ptrCount = ph.getPointerCount();
			ptrs = new long[ptrCount];
			for (int i = 0; i < ptrCount; i++) {
				ptrs[i] = ByteConversion.byteToLong(data, p);
				p += 8;
			}
		}

		public void write() throws IOException {
			final byte[] temp = new byte[fileHeader.getWorkSize()];
			int p = 0;
			byte[] data;
			final int keyLen = fileHeader.getFixedKeyLen();
			for (int i = 0; i < values.length; i++) {
				if (keyLen < 0) {
					ByteConversion.shortToByte((short) values[i].getLength(), temp, p);
					p += 2;
				}
				data = values[i].getData();
				if(p + data.length > temp.length)
					throw new IOException("calculated: " + getDataLen() + "; required: " + (p + data.length));
				System.arraycopy(data, 0, temp, p, data.length);
				p += data.length;
			}

			for (int i = 0; i < ptrs.length; i++) {
				ByteConversion.longToByte(ptrs[i], temp, p);
				p += 8;
			}
			writeValue(page, new Value(temp));
			saved = true;
		}

		public BTreeNode getChildNode(int idx) throws IOException {
			if (ph.getStatus() == BRANCH && idx >= 0 && idx < ptrs.length)
				return getBTreeNode(ptrs[idx], this);
			else
				return null;
		}

		public void remove() throws IOException, BTreeException {
			if (ph.getStatus() == BRANCH)
				for (int i = 0; i < ptrs.length; i++)
					getChildNode(i).remove();

			cache.remove(this);
			unlinkPages(page);
		}

		public long removeValue(Txn transaction, Value value) throws IOException, BTreeException {
			int idx = Arrays.binarySearch(values, value);
			switch (ph.getStatus()) {
				case BRANCH :
					if (idx < 0)
						idx = - (idx + 1);
					return getChildNode(idx).removeValue(transaction, value);
				case LEAF :
					if (idx < 0)
						return KEY_NOT_FOUND;
					else {
                        if (transaction != null && isTransactional) {
                            RemoveValueLoggable log = 
                                new RemoveValueLoggable(transaction, fileId, page.getPageNum(), idx,
                                        values[idx], ptrs[idx]);
                            writeToLog(log, this);
                        }
                        
						long oldPtr = ptrs[idx];

						setValues(deleteArrayValue(values, idx));
						setPointers(ArrayUtils.deleteArrayLong(ptrs, idx));
						recalculateDataLen();
						return oldPtr;
					}

				default :
					throw new BTreeException("Invalid Page Type In removeValue");
			}
		}

		public long addValue(Txn transaction, Value value, long pointer) throws IOException, BTreeException {
            if (value == null)
                return -1;
			int idx = Arrays.binarySearch(values, value);

			switch (ph.getStatus()) {
				case BRANCH :
					if (idx < 0)
						idx = - (idx + 1);
					return getChildNode(idx).addValue(transaction, value, pointer);
				case LEAF :
					if (idx >= 0) {
						// Value was found... Overwrite
                        long oldPtr = ptrs[idx];
                        if (transaction != null && isTransactional) {
                            UpdateValueLoggable loggable = new UpdateValueLoggable(transaction, fileId, page.getPageNum(), idx,
                                    value, pointer, oldPtr);
                            writeToLog(loggable, this);
                        }
                        
						ptrs[idx] = pointer;
						setPointers(ptrs);
						//write();
						//cache.add(this);
						return oldPtr;
					} else {
						// Value was not found
						idx = - (idx + 1);

                        if (transaction != null && isTransactional) {
                            InsertValueLoggable loggable = 
                                new InsertValueLoggable(transaction, fileId, page.getPageNum(), idx,
                                    value, pointer);
                            writeToLog(loggable, this);
                        }
						setValues(insertArrayValue(values, value, idx));
						setPointers(ArrayUtils.insertArrayLong(ptrs, pointer, idx));
						adjustDataLen(value);
						//recalculateDataLen();
						if (mustSplit()) {
						    split(transaction);
                        }
					}
					return -1;
				default :
					throw new BTreeException("Invalid Page Type In addValue");
			}
		}

		public void promoteValue(Txn transaction, Value value, long rightPointer)
			throws IOException, BTreeException {
			int idx = Arrays.binarySearch(values, value);
			if (idx < 0)
				idx = - (idx + 1);
            
            Value[] oldVals = new Value[values.length];
            System.arraycopy(values, 0, oldVals, 0, values.length);
            long[] oldPtrs = new long[ptrs.length];
            System.arraycopy(ptrs, 0, oldPtrs, 0, ptrs.length);
            
			setValues(insertArrayValue(values, value, idx));
			setPointers(ArrayUtils.insertArrayLong(ptrs, rightPointer, idx + 1));
            
            if (transaction != null && isTransactional) {
                Loggable log = new UpdatePageLoggable(transaction, fileId, page.getPageNum(), values, ptrs);
                writeToLog(log, this);
            }
            
			boolean split = recalculateDataLen() > fileHeader.getWorkSize();

			if (split)
				split(transaction);
		}

		public Value getSeparator(Value value1, Value value2) {
			int idx = value1.compareTo(value2);
			byte[] b = new byte[Math.abs(idx)];
			System.arraycopy(value2.getData(), 0, b, 0, b.length);
			return new Value(b);
		}

		public void split(Txn transaction) throws IOException, BTreeException {
			Value[] leftVals;
			Value[] rightVals;
			long[] leftPtrs;
			long[] rightPtrs;
			Value separator;
            
			final short vc = ph.getValueCount();
			final int pivot = vc / 2;
			// Split the node into two nodes
			switch (ph.getStatus()) {
				case BRANCH :
					leftVals = new Value[pivot];
					leftPtrs = new long[leftVals.length + 1];
					rightVals = new Value[vc - (pivot + 1)];
					rightPtrs = new long[rightVals.length + 1];

					System.arraycopy(values, 0, leftVals, 0, leftVals.length);
					System.arraycopy(ptrs, 0, leftPtrs, 0, leftPtrs.length);
					System.arraycopy(values, leftVals.length + 1, rightVals, 0, rightVals.length);
					System.arraycopy(ptrs, leftPtrs.length, rightPtrs, 0, rightPtrs.length);

					separator = values[leftVals.length];
					break;
				case LEAF :
					leftVals = new Value[pivot];
					leftPtrs = new long[leftVals.length];
					rightVals = new Value[vc - pivot];
					rightPtrs = new long[rightVals.length];

					System.arraycopy(values, 0, leftVals, 0, leftVals.length);
					System.arraycopy(ptrs, 0, leftPtrs, 0, leftPtrs.length);
					System.arraycopy(values, leftVals.length, rightVals, 0, rightVals.length);
					System.arraycopy(ptrs, leftPtrs.length, rightPtrs, 0, rightPtrs.length);

					separator = leftVals[leftVals.length - 1];
					break;
				default :
					throw new BTreeException("Invalid Page Type In split");
			}

            // Log the update of the current page
            if (transaction != null && isTransactional) {
                Loggable log = new UpdatePageLoggable(transaction, fileId, page.getPageNum(), leftVals, leftPtrs);
                writeToLog(log, this);
            }
            
			setValues(leftVals);
			setPointers(leftPtrs);
			recalculateDataLen();

			// Promote the pivot to the parent branch
            BTreeNode parent = getParent();
			if (parent == null) {
				// This can only happen if this is the root
				parent = createBTreeNode(transaction, BRANCH, null);
                
                // Log change of the parent page
                if (transaction != null && isTransactional) {
                    Loggable log = new SetParentLoggable(transaction, fileId, page.getPageNum(), 
                            parent.page.getPageNum());
                    writeToLog(log, this);
                }
                
                setParent(parent);

				final BTreeNode rNode = createBTreeNode(transaction, ph.getStatus(), parent);
                
                // Log update of the right node
                if (isTransactional && transaction != null) {
                    Loggable log = 
                        new UpdatePageLoggable(transaction, fileId, rNode.page.getPageNum(), rightVals, rightPtrs);
                    writeToLog(log, rNode);
                }
                
				rNode.setValues(rightVals);
				rNode.setPointers(rightPtrs);
				rNode.recalculateDataLen();
				cache.add(rNode);
                
				parent.setValues(new Value[] { separator });
				parent.setPointers(new long[] { page.getPageNum(), rNode.page.getPageNum()});
                
                // Log update of the parent node
                if (isTransactional && transaction != null) {
                    Loggable log =
                        new UpdatePageLoggable(transaction, fileId, parent.page.getPageNum(), parent.values, parent.ptrs);
                    writeToLog(log, parent);
                }
                
				parent.recalculateDataLen();
				cache.add(parent);
				setRootNode(parent);
				if(rNode.mustSplit()) {
					LOG.debug(getFile().getName() + " right node requires second split: " + rNode.getDataLen());
					rNode.split(transaction);
				}
			} else {
				final BTreeNode rNode = createBTreeNode(transaction, ph.getStatus(), parent);
                
                // Log update of the right node
                if (isTransactional && transaction != null) {
                    Loggable log = 
                        new UpdatePageLoggable(transaction, fileId, rNode.page.getPageNum(), rightVals, rightPtrs);
                    writeToLog(log, rNode);
                }
                
				rNode.setValues(rightVals);
				rNode.setPointers(rightPtrs);
				rNode.recalculateDataLen();
				cache.add(rNode);
				parent.promoteValue(transaction, separator, rNode.page.getPageNum());
				if(rNode.mustSplit()) {
					LOG.debug(getFile().getName() + " right node requires second split: " + rNode.getDataLen());
					rNode.split(transaction);
				}
			}
            cache.add(this);
			if(mustSplit()) {
				LOG.debug(getFile().getName() + "left node requires second split: " + getDataLen());
				split(transaction);
			}
		}

		/////////////////////////////////////////////////////////////////

		public long findValue(Value value) throws IOException, BTreeException {
			int idx = Arrays.binarySearch(values, value);
			switch (ph.getStatus()) {
				case BRANCH :
					if (idx < 0)
						idx = - (idx + 1);
					BTreeNode child = getChildNode(idx);
					if (child == null)
						throw new BTreeException(
							"unexpected "
								+ idx
								+ ", "
								+ page.getPageNum()
								+ ": value '"
								+ value.toString()
								+ "' doesn't exist");
					return child.findValue(value);
				case LEAF :
					if (idx < 0) {
						return KEY_NOT_FOUND;
						//throw new BTreeException("Value doesn't exist");
					} else
						return ptrs[idx];
				default :
					throw new BTreeException("Invalid Page Type In findValue");
			}
		}

        public void dump(Writer writer) throws IOException, BTreeException {
            if (page.getPageNum() == fileHeader.getRootPage())
                writer.write("ROOT: ");
            writer.write(page.getPageNum() + ": ");
            writer.write(ph.getStatus() == BRANCH ? "BRANCH: " : "LEAF: ");
            for (int i = 0; i < values.length; i++) {
                if (i > 0)
                    writer.write(' ');
                dumpValue(writer, values[i]);
            }
            writer.write('\n');
            writer.write("-----------------------------------------------------------------------------------------\n");
            if (ph.getStatus() == BRANCH) {
                for (int i = 0; i < ptrs.length; i++) {
                    BTreeNode child = getChildNode(i);
                    child.dump(writer);
                }
            }
        }
        
		// query is a BEAST of a method
		// --- added support for right truncated search terms (wolf)
		public void query(IndexQuery query, BTreeCallback callback)
			throws IOException, BTreeException, TerminatedException {
			if (query != null
				&& query.getOperator() != IndexQuery.ANY
				&& query.getOperator() != IndexQuery.TRUNC_LEFT) {
				Value[] qvals = query.getValues();
				int leftIdx = Arrays.binarySearch(values, qvals[0]);
				int rightIdx =
					qvals.length > 1
						? Arrays.binarySearch(values, qvals[qvals.length - 1])
						: leftIdx;
				boolean pos = query.getOperator() >= 0;
				switch (ph.getStatus()) {

					case BRANCH :
						if (leftIdx < 0)
							leftIdx = - (leftIdx + 1);
						if (rightIdx < 0)
							rightIdx = - (rightIdx + 1);

						switch (query.getOperator()) {
							case IndexQuery.BWX :
							case IndexQuery.NBWX :
							case IndexQuery.BW :
							case IndexQuery.NBW :
							case IndexQuery.IN :
							case IndexQuery.NIN :
							case IndexQuery.TRUNC_RIGHT :
								for (int i = 0; i < ptrs.length; i++)
									if ((i >= leftIdx && i <= rightIdx) == pos)
										getChildNode(i).query(query, callback);
								break;
							case IndexQuery.EQ :
							case IndexQuery.NEQ :
								for (int i = 0; i < ptrs.length; i++)
									if (!pos || i == leftIdx)
										getChildNode(i).query(query, callback);

							case IndexQuery.LT :
							case IndexQuery.GEQ :
								for (int i = 0; i < ptrs.length; i++)
									if ((pos && (i <= leftIdx)) || (!pos && (i >= leftIdx)))
										getChildNode(i).query(query, callback);
								break;
							case IndexQuery.GT :
							case IndexQuery.LEQ :
								for (int i = 0; i < ptrs.length; i++)
									if ((pos && (i >= leftIdx)) || (!pos && (i <= leftIdx)))
										getChildNode(i).query(query, callback);
								break;
							default :
								// If it's not implemented, we walk the tree

								for (int i = 0; i < ptrs.length; i++)
									getChildNode(i).query(query, callback);
								break;
						}
						break;
					case LEAF :
						switch (query.getOperator()) {
							case IndexQuery.EQ :
								if (leftIdx >= 0)
									callback.indexInfo(values[leftIdx], ptrs[leftIdx]);
								break;
							case IndexQuery.NEQ :
								for (int i = 0; i < ptrs.length; i++)
									if (i != leftIdx)
										callback.indexInfo(values[i], ptrs[i]);
								break;
							case IndexQuery.BWX :
							case IndexQuery.NBWX :
							case IndexQuery.BW :
							case IndexQuery.NBW :
								if (leftIdx < 0)
									leftIdx = - (leftIdx + 1);
								if (rightIdx < 0)
									rightIdx = - (rightIdx + 1);
								for (int i = 0; i < ptrs.length; i++)
									if ((pos && (i >= leftIdx && i <= rightIdx))
										|| (!pos && (i <= leftIdx || i >= rightIdx))) {
										if (query.testValue(values[i]))
											callback.indexInfo(values[i], ptrs[i]);
									}
								break;
							case IndexQuery.TRUNC_RIGHT :
								if (leftIdx < 0)
									leftIdx = - (leftIdx + 1);
								if (rightIdx < 0)
									rightIdx = - (rightIdx + 1);
								for (int i = 0; i < ptrs.length; i++)
									if ((pos && (i >= leftIdx && i < rightIdx))
										|| (!pos && (i <= leftIdx || i >= rightIdx))) {
										if (query.testValue(values[i]))
											callback.indexInfo(values[i], ptrs[i]);
									}
								break;
							case IndexQuery.IN :
							case IndexQuery.NIN :
								if (leftIdx < 0)
									leftIdx = - (leftIdx + 1);
								if (rightIdx < 0)
									rightIdx = - (rightIdx + 1);
								for (int i = 0; i < ptrs.length; i++)
									if (!pos || (i >= leftIdx && i <= rightIdx))
										if (query.testValue(values[i]))
											callback.indexInfo(values[i], ptrs[i]);
								break;
							case IndexQuery.LT :
							case IndexQuery.GEQ :
								if (leftIdx < 0)
									leftIdx = - (leftIdx + 1);
								for (int i = 0; i < ptrs.length; i++)
									if ((pos && (i <= leftIdx)) || (!pos && (i >= leftIdx)))
										if (query.testValue(values[i]))
											callback.indexInfo(values[i], ptrs[i]);
								break;
							case IndexQuery.GT :
							case IndexQuery.LEQ :
								if (leftIdx < 0)
									leftIdx = - (leftIdx + 1);
								for (int i = 0; i < ptrs.length; i++)
									if ((pos && (i >= leftIdx)) || (!pos && (i <= leftIdx))) {
										if (query.testValue(values[i]))
											callback.indexInfo(values[i], ptrs[i]);
										else if (query.getOperator() == IndexQuery.TRUNC_RIGHT)
											break;
									}
								break;
							default :
								// If it's not implemented, it falls right through

								for (int i = 0; i < ptrs.length; i++)
									if (query.testValue(values[i]))
										callback.indexInfo(values[i], ptrs[i]);
								break;
						}
						break;
					default :
						throw new BTreeException("Invalid Page Type In query");
				}

			} else
				// No Query - Just Walk The Tree
				switch (ph.getStatus()) {
					case BRANCH :
						for (int i = 0; i < ptrs.length; i++)
							getChildNode(i).query(query, callback);
						break;
					case LEAF :
						for (int i = 0; i < values.length; i++)
							if (query.getOperator() != IndexQuery.TRUNC_LEFT
								|| query.testValue(values[i]))
								callback.indexInfo(values[i], ptrs[i]);
						break;
					default :
						throw new BTreeException("Invalid Page Type In query");
				}

		}
		
        public void query(IndexQuery query, Value prefix, BTreeCallback callback)
        throws IOException, BTreeException, TerminatedException {
            if (query != null
                    && query.getOperator() != IndexQuery.ANY
                    && query.getOperator() != IndexQuery.TRUNC_LEFT) {
                Value[] qvals = query.getValues();
                int leftIdx = Arrays.binarySearch(values, qvals[0]);
                int pfxIdx = Arrays.binarySearch(values, prefix);
                
                    boolean pos = query.getOperator() >= 0;
                    switch (ph.getStatus()) {
                        case BRANCH :
                            if (leftIdx < 0)
                                leftIdx = - (leftIdx + 1);
                            if (pfxIdx < 0)
                                pfxIdx = - (pfxIdx + 1);
                            
                            switch (query.getOperator()) {
                                case IndexQuery.EQ :
                                    getChildNode(leftIdx).query(query, prefix, callback);
                                    break;
                                case IndexQuery.NEQ :
                                    for (int i = 0; i < ptrs.length; i++)
                                        if (i != leftIdx && values[i].startsWith(prefix))
                                            getChildNode(i).query(query, prefix, callback);
                                    break;
                                case IndexQuery.LT :
                                    for (int i = pfxIdx; i <= leftIdx && i < ptrs.length; i++) {
                                        getChildNode(i).query(query, prefix, callback);
                                    }
                                    break;
                                case IndexQuery.LEQ :
                                    for (int i = pfxIdx; i <= leftIdx && i < ptrs.length; i++) {
                                        getChildNode(i).query(query, prefix, callback);
                                    }
                                    break;
                                case IndexQuery.GT :
                                    for (int i = leftIdx; i < ptrs.length; i++) {
                                        if (i < values.length && values[i].comparePrefix(prefix) > 0)
                                            break;
                                        getChildNode(i).query(query, prefix, callback);
                                    }
                                    break;
                                case IndexQuery.GEQ :
                                    for (int i = leftIdx; i < ptrs.length; i++) {
                                        if (i < values.length && values[i].comparePrefix(prefix) > 0)
                                            break;
                                        getChildNode(i).query(query, prefix, callback);
                                    }
                                    break;
                            }
                            break;
                        case LEAF :
                            if (pfxIdx < 0)
                                pfxIdx = - (pfxIdx + 1);
                            switch (query.getOperator()) {
                                case IndexQuery.EQ :
                                    if (leftIdx >= 0)
                                        callback.indexInfo(values[leftIdx], ptrs[leftIdx]);
                                    break;
                                case IndexQuery.NEQ :
                                    for (int i = 0; i < ptrs.length; i++)
                                        if (i != leftIdx)
                                            callback.indexInfo(values[i], ptrs[i]);
                                    break;
                                case IndexQuery.LT :
                                    if (leftIdx < 0)
                                        leftIdx = - (leftIdx + 1);
                                    for (int i = pfxIdx; i < leftIdx; i++) {
                                        if (query.testValue(values[i]))
                                            callback.indexInfo(values[i], ptrs[i]);
                                    }
                                    break;
                                case IndexQuery.LEQ :
                                    if (leftIdx < 0)
                                        leftIdx = - (leftIdx + 1);
                                    for (int i = pfxIdx; i <= leftIdx && i < ptrs.length; i++) {
                                        if (query.testValue(values[i]))
                                            callback.indexInfo(values[i], ptrs[i]);
                                    }
                                    break;
                                case IndexQuery.GT :
                                    if (leftIdx < 0)
                                        leftIdx = - (leftIdx + 1);
                                    for (int i = leftIdx; i < ptrs.length; i++) {
                                        if (values[i].comparePrefix(prefix) > 0)
                                            break;
                                        if (query.testValue(values[i]))
                                            callback.indexInfo(values[i], ptrs[i]);
                                    }
                                    break;
                                case IndexQuery.GEQ :
                                    if (leftIdx < 0)
                                        leftIdx = - (leftIdx + 1);
                                    for (int i = leftIdx; i < ptrs.length; i++) {
                                        if (values[i].comparePrefix(prefix) > 0)
                                            break;
                                        if (query.testValue(values[i]))
                                            callback.indexInfo(values[i], ptrs[i]);
                                    }
                                    break;
                            }
                            break;
                        default :
                            throw new BTreeException("Invalid Page Type In query");
                    }
            } else
                // No Query - Just Walk The Tree
                switch (ph.getStatus()) {
                    case BRANCH :
                        for (int i = 0; i < ptrs.length; i++)
                            getChildNode(i).query(query, callback);
                        break;
                    case LEAF :
                        for (int i = 0; i < values.length; i++)
                            if (query.getOperator() != IndexQuery.TRUNC_LEFT
                                    || query.testValue(values[i]))
                                callback.indexInfo(values[i], ptrs[i]);
                        break;
                    default :
                        throw new BTreeException("Invalid Page Type In query");
                }
        }
        
		public void remove(Txn transaction, IndexQuery query, BTreeCallback callback)
			throws IOException, BTreeException, TerminatedException {
			if (query != null
				&& query.getOperator() != IndexQuery.ANY
				&& query.getOperator() != IndexQuery.TRUNC_LEFT) {
				Value[] qvals = query.getValues();
				int leftIdx = Arrays.binarySearch(values, qvals[0]);
				int rightIdx =
					qvals.length > 1
						? Arrays.binarySearch(values, qvals[qvals.length - 1])
						: leftIdx;
				boolean pos = query.getOperator() >= 0;
				switch (ph.getStatus()) {

					case BRANCH :
						if (leftIdx < 0)
							leftIdx = - (leftIdx + 1);
						if (rightIdx < 0)
							rightIdx = - (rightIdx + 1);

						switch (query.getOperator()) {
							case IndexQuery.BWX :
							case IndexQuery.NBWX :
							case IndexQuery.BW :
							case IndexQuery.NBW :
							case IndexQuery.IN :
							case IndexQuery.NIN :
							case IndexQuery.TRUNC_RIGHT :
								for (int i = 0; i < ptrs.length; i++)
									if ((i >= leftIdx && i <= rightIdx) == pos)
										getChildNode(i).remove(transaction, query, callback);
								break;
							case IndexQuery.EQ :
							case IndexQuery.NEQ :
								for (int i = 0; i < ptrs.length; i++)
									if (!pos || i == leftIdx)
										getChildNode(i).remove(transaction, query, callback);

							case IndexQuery.LT :
							case IndexQuery.GEQ :
								for (int i = 0; i < ptrs.length; i++)
									if ((pos && (i <= leftIdx)) || (!pos && (i >= leftIdx)))
										getChildNode(i).remove(transaction, query, callback);
								break;
							case IndexQuery.GT :
							case IndexQuery.LEQ :
								for (int i = 0; i < ptrs.length; i++)
									if ((pos && (i >= leftIdx)) || (!pos && (i <= leftIdx)))
										getChildNode(i).remove(transaction, query, callback);
								break;
							default :
								// If it's not implemented, we walk the tree

								for (int i = 0; i < ptrs.length; i++)
									getChildNode(i).remove(transaction, query, callback);
								break;
						}
						break;
					case LEAF :
						switch (query.getOperator()) {
							case IndexQuery.EQ :
								if (leftIdx >= 0) {
                                    if (isTransactional && transaction != null) {
                                        RemoveValueLoggable log = 
                                            new RemoveValueLoggable(transaction, fileId, page.getPageNum(), leftIdx,
                                                    values[leftIdx], ptrs[leftIdx]);
                                        writeToLog(log, this);
                                    }
                                    
									if (callback != null)
										callback.indexInfo(values[leftIdx], ptrs[leftIdx]);
									setValues(deleteArrayValue(values, leftIdx));
									setPointers(ArrayUtils.deleteArrayLong(ptrs, leftIdx));
									recalculateDataLen();
								}
								break;
							case IndexQuery.NEQ :
								for (int i = 0; i < ptrs.length; i++)
									if (i != leftIdx) {
                                        if (isTransactional && transaction != null) {
                                            RemoveValueLoggable log = 
                                                new RemoveValueLoggable(transaction, fileId, page.getPageNum(), i,
                                                        values[i], ptrs[i]);
                                            writeToLog(log, this);
                                        }
                                        
										if (callback != null)
											callback.indexInfo(values[i], ptrs[i]);
										setValues(deleteArrayValue(values, i));
										setPointers(ArrayUtils.deleteArrayLong(ptrs, i));
										recalculateDataLen();
									}
								break;
							case IndexQuery.BWX :
							case IndexQuery.NBWX :
							case IndexQuery.BW :
							case IndexQuery.NBW :
								if (leftIdx < 0)
									leftIdx = - (leftIdx + 1);
								if (rightIdx < 0)
									rightIdx = - (rightIdx + 1);
								for (int i = 0; i < ptrs.length; i++)
									if ((pos && (i >= leftIdx && i <= rightIdx))
										|| (!pos && (i <= leftIdx || i >= rightIdx))) {
										if (query.testValue(values[i])) {
                                            if (isTransactional && transaction != null) {
                                                RemoveValueLoggable log = 
                                                    new RemoveValueLoggable(transaction, fileId, page.getPageNum(), i,
                                                            values[i], ptrs[i]);
                                                writeToLog(log, this);
                                            }
                                            
											if (callback != null)
												callback.indexInfo(values[i], ptrs[i]);
											setValues(deleteArrayValue(values, i));
											setPointers(ArrayUtils.deleteArrayLong(ptrs, i));
											--i;
											recalculateDataLen();
										}
									}
								break;
							case IndexQuery.TRUNC_RIGHT :
								if (leftIdx < 0)
									leftIdx = - (leftIdx + 1);
								if (rightIdx < 0)
									rightIdx = - (rightIdx + 1);
								for (int i = 0; i < ptrs.length; i++)
									if ((pos && (i >= leftIdx && i < rightIdx))
										|| (!pos && (i <= leftIdx || i >= rightIdx))) {
										if (query.testValue(values[i])) {
                                            if (isTransactional && transaction != null) {
                                                RemoveValueLoggable log = 
                                                    new RemoveValueLoggable(transaction, fileId, page.getPageNum(), i,
                                                            values[i], ptrs[i]);
                                                writeToLog(log, this);
                                            }
                                            
											if (callback != null)
												callback.indexInfo(values[i], ptrs[i]);
											setValues(deleteArrayValue(values, i));
											setPointers(ArrayUtils.deleteArrayLong(ptrs, i));
											--i;
											recalculateDataLen();
										}
									}
								break;
							case IndexQuery.IN :
							case IndexQuery.NIN :
								if (leftIdx < 0)
									leftIdx = - (leftIdx + 1);
								if (rightIdx < 0)
									rightIdx = - (rightIdx + 1);
								for (int i = 0; i < ptrs.length; i++)
									if (!pos || (i >= leftIdx && i <= rightIdx))
										if (query.testValue(values[i])) {
                                            if (isTransactional && transaction != null) {
                                                RemoveValueLoggable log = 
                                                    new RemoveValueLoggable(transaction, fileId, page.getPageNum(), i,
                                                            values[i], ptrs[i]);
                                                writeToLog(log, this);
                                            }
                                            
											if (callback != null)
												callback.indexInfo(values[i], ptrs[i]);
											setValues(deleteArrayValue(values, i));
											setPointers(ArrayUtils.deleteArrayLong(ptrs, i));
											--i;
											recalculateDataLen();
										}
								break;
							case IndexQuery.LT :
							case IndexQuery.GEQ :
								if (leftIdx < 0)
									leftIdx = - (leftIdx + 1);
								for (int i = 0; i < ptrs.length; i++)
									if ((pos && (i <= leftIdx)) || (!pos && (i >= leftIdx)))
										if (query.testValue(values[i])) {
                                            if (isTransactional && transaction != null) {
                                                RemoveValueLoggable log = 
                                                    new RemoveValueLoggable(transaction, fileId, page.getPageNum(), i,
                                                            values[i], ptrs[i]);
                                                writeToLog(log, this);
                                            }
                                            
											if (callback != null)
												callback.indexInfo(values[i], ptrs[i]);
											setValues(deleteArrayValue(values, i));
											setPointers(ArrayUtils.deleteArrayLong(ptrs, i));
											--i;
											recalculateDataLen();
										}
								break;
							case IndexQuery.GT :
							case IndexQuery.LEQ :
								if (leftIdx < 0)
									leftIdx = - (leftIdx + 1);
								for (int i = 0; i < ptrs.length; i++)
									if ((pos && (i >= leftIdx)) || (!pos && (i <= leftIdx))) {
										if (query.testValue(values[i])) {
                                            if (isTransactional && transaction != null) {
                                                RemoveValueLoggable log = 
                                                    new RemoveValueLoggable(transaction, fileId, page.getPageNum(), i,
                                                            values[i], ptrs[i]);
                                                writeToLog(log, this);
                                            }
                                            
											if (callback != null)
												callback.indexInfo(values[i], ptrs[i]);
											setValues(deleteArrayValue(values, i));
											setPointers(ArrayUtils.deleteArrayLong(ptrs, i));
											--i;
											recalculateDataLen();
										} else if (query.getOperator() == IndexQuery.TRUNC_RIGHT)
											break;
									}
								break;
							default :
								// If it's not implemented, it falls right through

								for (int i = 0; i < ptrs.length; i++)
									if (query.testValue(values[i])) {
                                        if (isTransactional && transaction != null) {
                                            RemoveValueLoggable log = 
                                                new RemoveValueLoggable(transaction, fileId, page.getPageNum(), i,
                                                        values[i], ptrs[i]);
                                            writeToLog(log, this);
                                        }
                                        
										if (callback != null)
											callback.indexInfo(values[i], ptrs[i]);
										setValues(deleteArrayValue(values, i));
										setPointers(ArrayUtils.deleteArrayLong(ptrs, i));
										--i;
										recalculateDataLen();
									}

								break;
						}
						break;
					default :
						throw new BTreeException("Invalid Page Type In query");
				}

			} else
				// No Query - Just Walk The Tree
				switch (ph.getStatus()) {
					case BRANCH :
						for (int i = 0; i < ptrs.length; i++) {
                            if (isTransactional && transaction != null) {
                                RemoveValueLoggable log = 
                                    new RemoveValueLoggable(transaction, fileId, page.getPageNum(), i,
                                            values[i], ptrs[i]);
                                writeToLog(log, this);
                            }
                            
							if (callback != null)
								callback.indexInfo(values[i], ptrs[i]);
							setValues(deleteArrayValue(values, i));
							setPointers(ArrayUtils.deleteArrayLong(ptrs, i));
							--i;
							recalculateDataLen();
						}
						break;
					case LEAF :
						for (int i = 0; i < values.length; i++)
							if (query.getOperator() != IndexQuery.TRUNC_LEFT
								|| query.testValue(values[i])) {
                                if (isTransactional && transaction != null) {
                                    RemoveValueLoggable log = 
                                        new RemoveValueLoggable(transaction, fileId, page.getPageNum(), i,
                                                values[i], ptrs[i]);
                                    writeToLog(log, this);
                                }
                                
								if (callback != null)
									callback.indexInfo(values[i], ptrs[i]);
								setValues(deleteArrayValue(values, i));
								setPointers(ArrayUtils.deleteArrayLong(ptrs, i));
								--i;
								recalculateDataLen();
							}
						break;
					default :
						throw new BTreeException("Invalid Page Type In query");
				}
		}
	}

	////////////////////////////////////////////////////////////////////

    /**
     * @see org.exist.storage.btree.Paged#createFileHeader()
     */
	public FileHeader createFileHeader() {
		return new BTreeFileHeader(PAGE_SIZE);
	}

    /**
     * @see org.exist.storage.btree.Paged#createFileHeader(boolean)
     */
	public FileHeader createFileHeader(boolean read) throws IOException {
		return new BTreeFileHeader(read);
	}

    /**
     * @see org.exist.storage.btree.Paged#createFileHeader(long)
     */
	public FileHeader createFileHeader(long pageCount) {
		return new BTreeFileHeader(pageCount, PAGE_SIZE);
	}

    /**
     * @see org.exist.storage.btree.Paged#createFileHeader(long, int)
     */
	public FileHeader createFileHeader(long pageCount, int pageSize) {
		return new BTreeFileHeader(pageCount, pageSize);
	}

    /**
     * @see org.exist.storage.btree.Paged#createPageHeader()
     */
	public PageHeader createPageHeader() {
		return new BTreePageHeader();
	}

	public BufferStats getIndexBufferStats() {
		return new BufferStats(
			cache.getBuffers(),
			cache.getUsedBuffers(),
			cache.getHits(),
			cache.getFails());
	}

	public void printStatistics() {
		StringBuffer buf = new StringBuffer();
		buf.append(getFile().getName()).append(" INDEX ");
		buf.append(cache.getBuffers()).append(" / ");
		buf.append(cache.getUsedBuffers()).append(" / ");
		buf.append(cache.getHits()).append(" / ");
		buf.append(cache.getFails());
		LOG.info(buf.toString());
	}

	protected class BTreeFileHeader extends FileHeader {
		private long rootPage = 0;
		private short fixedLen = -1;

		public BTreeFileHeader() {
			super();
		}

		public BTreeFileHeader(long pageCount) {
			super(pageCount);
		}

		public BTreeFileHeader(long pageCount, int pageSize) {
			super(pageCount, pageSize);
		}

		public BTreeFileHeader(long pageCount, int pageSize, byte blockSize) {
			super(pageCount, pageSize, blockSize);
		}

		public BTreeFileHeader(int pageSize) {
			super(pageSize);
		}

		public BTreeFileHeader(boolean read) throws IOException {
			super(read);
		}

        public int read(byte[] buf) throws IOException {
            int offset = super.read(buf);
            rootPage = ByteConversion.byteToLong(buf, offset);
            offset += 8;
            fixedLen = ByteConversion.byteToShort(buf, offset);
            offset += 2;
            return offset;
        }

        public int write(byte[] buf) throws IOException {
            int offset = super.write(buf);
            ByteConversion.longToByte(rootPage, buf, offset);
            offset += 8;
            ByteConversion.shortToByte(fixedLen, buf, offset);
            offset += 2;
            return offset;
        }

		/**
		 *  The root page of the storage tree
		 *
		 *@param  rootPage  The new rootPage value
		 */
		public final void setRootPage(long rootPage) {
			this.rootPage = rootPage;
			setDirty(true);
		}

		/**
		 *  The root page of the storage tree
		 *
		 *@return    The rootPage value
		 */
		public final long getRootPage() {
			return rootPage;
		}

		public short getFixedKeyLen() {
			return fixedLen;
		}

		public void setFixedKeyLen(short keyLen) {
			this.fixedLen = keyLen;
		}
	}

	protected static class BTreePageHeader extends PageHeader {
		private short valueCount = 0;
		private long parentPage = -1;
		
		public BTreePageHeader() {
			super();
		}

		public BTreePageHeader(byte[] data, int offset) throws IOException {
			super(data, offset);
		}

		public int read(byte[] data, int offset) throws IOException {
			offset = super.read(data, offset);

			//if (getStatus() == UNUSED)
			//	return;
			parentPage = ByteConversion.byteToLong(data, offset);
			offset += 8;
			valueCount = ByteConversion.byteToShort(data, offset);
			return offset + 2;
		}

		public int write(byte[] data, int offset) throws IOException {
			offset = super.write(data, offset);
			ByteConversion.longToByte(parentPage, data, offset);
			offset += 8;
			ByteConversion.shortToByte(valueCount, data, offset);
			return offset + 2;
		}

		public final void setValueCount(short valueCount) {
			this.valueCount = valueCount;
			setDirty(true);
		}

		/**
		 *  The number of values stored by this page
		 *
		 *@return    The valueCount value
		 */
		public final short getValueCount() {
			return valueCount;
		}

		/**
		 *  The number of pointers stored by this page
		 *
		 *@return    The pointerCount value
		 */
		public final short getPointerCount() {
			if (getStatus() == BRANCH)
				return (short) (valueCount + 1);
			else
				return valueCount;
		}
	}
}
