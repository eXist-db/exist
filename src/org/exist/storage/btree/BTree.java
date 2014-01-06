package org.exist.storage.btree;

/*
*  eXist Open Source Native XML Database
 *  Copyright (C) 2001-05 The eXist Project
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
 *  
 *  This file is in part based on code from the dbXML Group. The original license
 *  statement is included below:
 *  
 *  -------------------------------------------------------------------------------------------------
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
 */

import org.apache.log4j.Logger;

import org.exist.storage.BrokerPool;
import org.exist.storage.BufferStats;
import org.exist.storage.CacheManager;
import org.exist.storage.DefaultCacheManager;
import org.exist.storage.NativeBroker;
import org.exist.storage.cache.*;
import org.exist.storage.journal.Journal;
import org.exist.storage.journal.LogEntryTypes;
import org.exist.storage.journal.LogException;
import org.exist.storage.journal.Loggable;
import org.exist.storage.journal.Lsn;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.Txn;
import org.exist.util.ByteConversion;
import org.exist.util.Lockable;
import org.exist.xquery.TerminatedException;

import java.io.*;
import java.text.NumberFormat;
import java.util.*;

/**
 *  A general purpose B+-tree which stores binary keys as instances of
 *  {@link org.exist.storage.btree.Value}. The actual value data is not
 *  stored in the B+tree itself. Instead, we use long pointers to record the
 *  storage address of the value. This class has no methods to locate or
 *  modify data records. Data handling is in the responsibilty of the 
 *  proper subclasses: {@link org.exist.storage.index.BFile} and
 *  {@link org.exist.storage.dom.DOMFile}.
 *  
 *  Both, branch and leaf nodes are represented by the inner class 
 *  {@link org.exist.storage.btree.BTree.BTreeNode}.
 */
public class BTree extends Paged implements Lockable {

    protected final static Logger LOGSTATS = Logger.getLogger( NativeBroker.EXIST_STATISTICS_LOGGER );
    
    /** Used as return value, if a value was not found */
    public final static long KEY_NOT_FOUND = -1;

    /** Type of BTreeNode/Page */
    protected final static byte LEAF = 1;
    protected final static byte BRANCH = 2;

    protected final static int MIN_SPACE_PER_KEY = 32;

    /** Log entry type for an insert value operation */
    public final static byte LOG_INSERT_VALUE = 0x20;
    /** Log entry type for creation of a new BTree node */
    public final static byte LOG_CREATE_BNODE = 0x21;
    /** Log entry type for a page update resulting from a page split */
    public final static byte LOG_UPDATE_PAGE = 0x22;
    /** Log entry type for a parent page change resulting from a page split */
    public final static byte LOG_SET_PARENT = 0x23;
    /** Log entry type for a value update */
    public final static byte LOG_UPDATE_VALUE = 0x24;
    /** Log entry type for removing a value */
    public final static byte LOG_REMOVE_VALUE = 0x25;
    public final static byte LOG_SET_LINK = 0x26;

    static {
        // register the log entry types used for the BTree
        LogEntryTypes.addEntryType(LOG_INSERT_VALUE, InsertValueLoggable.class);
        LogEntryTypes.addEntryType(LOG_UPDATE_VALUE, UpdateValueLoggable.class);
        LogEntryTypes.addEntryType(LOG_REMOVE_VALUE, RemoveValueLoggable.class);
        LogEntryTypes.addEntryType(LOG_CREATE_BNODE, CreateBTNodeLoggable.class);
        LogEntryTypes.addEntryType(LOG_UPDATE_PAGE, UpdatePageLoggable.class);
        LogEntryTypes.addEntryType(LOG_SET_PARENT, SetParentLoggable.class);
        LogEntryTypes.addEntryType(LOG_SET_LINK, SetPageLinkLoggable.class);
    }

    protected DefaultCacheManager cacheManager;

    /** Cache of BTreeNode(s) */
    protected Cache cache;

    protected double growthThreshold;

    /** Size of BTreeNode cache */
    protected int buffers;

    /** Fileheader of a BTree file */
    private BTreeFileHeader fileHeader;

    /** The LogManager for writing the transaction log */
    protected Journal logManager;

    protected byte fileId;

    protected boolean isTransactional;

    protected BrokerPool pool;

    private double splitFactor = -1;

    protected BTree(BrokerPool pool, byte fileId, boolean transactional,
            DefaultCacheManager cacheManager, double growthThreshold) throws DBException {
        super(pool);
        this.pool = pool;
        this.cacheManager = cacheManager;
        this.buffers = cacheManager.getDefaultInitialSize();
        this.growthThreshold = growthThreshold;
        this.fileId = fileId;
        fileHeader = (BTreeFileHeader) getFileHeader();
        fileHeader.setPageCount(0);
        fileHeader.setTotalCount(0);
        isTransactional = transactional && pool.isTransactional();
        if (isTransactional)
            {logManager = pool.getTransactionManager().getJournal();}
    }

    public BTree(BrokerPool pool, byte fileId, boolean transactional,
            DefaultCacheManager cacheManager, File file, double growthThreshold)
            throws DBException {
        this(pool, fileId, transactional, cacheManager, growthThreshold);
        setFile(file);
    }

    public short getFileVersion() {
        return -1;
    }

    public boolean create(short fixedKeyLen) throws DBException {
        if (super.create()) {
            initCache();
            try {
                createRootNode(null);
            } catch (final IOException e) {
                LOG.warn("Can not create database file " + getFile().getPath(), e);
                return false;
            }
            fileHeader.setFixedKeyLen(fixedKeyLen);
            try {
                fileHeader.write();
            } catch (final IOException e) {
                throw new DBException("Error while writing file header: " + e.getMessage());
            }
        }
        return true;
    }

    public boolean open(short expectedVersion) throws DBException {
        if (super.open(expectedVersion)) {
            initCache();
            return true;
        } else {
            return false;
        }
    }

    public void closeAndRemove() {
        super.closeAndRemove();
        cacheManager.deregisterCache(cache);
    }

    /**
     * Get the active Lock object for this file.
     *
     * @see org.exist.util.Lockable#getLock()
     */
    public Lock getLock() {
        return null;
    }

    protected void initCache() {
        cache = new BTreeCache(cacheManager.getDefaultInitialSize(), 1.5,
            0, CacheManager.BTREE_CACHE);
        cache.setFileName(getFile().getName());
        cacheManager.registerCache(cache);
    }

    protected void setSplitFactor(double factor) {
        if (factor > 1.0)
            {throw new IllegalArgumentException("splitFactor should be <= 1 > 0");}
        this.splitFactor = factor;
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


    public void remove(IndexQuery query, BTreeCallback callback) throws IOException,
            BTreeException, TerminatedException {
        remove(null, query, callback);
    }

    /**
     * Search for keys matching the given {@link IndexQuery} and
     * remove them from the node. Every match is reported 
     * to the specified {@link BTreeCallback}.
     * 
     * @param query
     * @param callback
     * @throws IOException
     * @throws BTreeException
     * @throws TerminatedException
     */
    public void remove(Txn transaction, IndexQuery query, BTreeCallback callback)
            throws IOException, BTreeException, TerminatedException {
        if (query != null && query.getOperator() == IndexQuery.TRUNC_RIGHT) {
            final Value val1 = query.getValue(0);
            final byte data1[] = val1.getData();
            final byte data2[] = new byte[data1.length];
            System.arraycopy(data1, 0, data2, 0, data1.length);
            data2[data2.length - 1] += 1;
            query = new IndexQuery(query.getOperator(), val1, new Value(data2));
        }
        getRootNode().remove(transaction, query, callback);
    }

    protected void removeSequential(Txn transaction, BTreeNode page, IndexQuery query,
            BTreeCallback callback) throws TerminatedException {
        long next = page.pageHeader.getNextPage();
        while (next != Page.NO_PAGE) {
            final BTreeNode nextPage = getBTreeNode(next);
            for (int i = 0; i < nextPage.nKeys; i++) {
                boolean test = query.testValue(nextPage.keys[i]);
                if (query.getOperator() != IndexQuery.NEQ && !test)
                    {return;}
                if (test) {
                    if (isTransactional && transaction != null && nextPage.pageHeader.getStatus() == LEAF) {
                        final RemoveValueLoggable log = new RemoveValueLoggable(transaction, 
                            fileId, nextPage.page.getPageNum(), i, nextPage.keys[i], nextPage.ptrs[i]);
                        writeToLog(log, nextPage);
                    }
                    if (callback != null)
                        {callback.indexInfo(nextPage.keys[i], nextPage.ptrs[i]);}
                    nextPage.removeKey(i);
                    nextPage.removePointer(i);
                    nextPage.recalculateDataLen();
                    --i;
                }
            }
            next = nextPage.pageHeader.getNextPage();
        }
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
            final Value val1 = query.getValue(0);
            final byte data1[] = val1.getData();
            final byte data2[] = new byte[data1.length];
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

    protected void scanSequential(BTreeNode page, IndexQuery query, Value keyPrefix, BTreeCallback callback) throws TerminatedException {
        while (page != null) {
            for (int i = 0; i < page.nKeys; i++) {
                if (keyPrefix != null && page.keys[i].comparePrefix(keyPrefix) > 0)
                    {return;}
                boolean test = query.testValue(page.keys[i]);
                if (query.getOperator() != IndexQuery.NEQ && !test)
                    {return;}
                if (test)
                    {callback.indexInfo(page.keys[i], page.ptrs[i]);}
            }
            final long next = page.pageHeader.getNextPage();
            if (next != Page.NO_PAGE) {
                page = getBTreeNode(next);
            } else
                {page = null;}
        }
    }

    /**
     * Create a new node with the given status and parent.
     * 
     * @param transaction
     * @param status
     * @param parent
     * @return The BTree node
     */
    private BTreeNode createBTreeNode(Txn transaction, byte status, BTreeNode parent, 
            boolean reuseDeleted) {
        try {
            final Page page = getFreePage(reuseDeleted);
            final BTreeNode node = new BTreeNode(page, true);
            if (transaction != null && isTransactional && status == LEAF) {
                final Loggable loggable = new CreateBTNodeLoggable(transaction, fileId, 
                    status, page.getPageNum(), parent != null ? parent.page.getPageNum() : Page.NO_PAGE);
                writeToLog(loggable, node);
            }
            node.pageHeader.setStatus(status);
            node.setPointers(new long[0]);
            node.setParent(parent);
            node.write();
            return node;
        } catch (final IOException e) {
            LOG.error("Failed to create a BTree node", e);
            return null;
        }
    }

    /**
     * Read a node from the given page.
     * 
     * @param pageNum
     * @return The BTree node
     */
    private BTreeNode getBTreeNode(long pageNum) {
        try {
            BTreeNode node = (BTreeNode) cache.get(pageNum);
            if (node == null) {
                final Page page = getPage(pageNum);
                node = new BTreeNode(page, false);
                node.read();
            }
            final int increment = node.pageHeader.getStatus() == BRANCH ? 2 : 1;
            cache.add(node, increment);
            return node;
        } catch (final IOException e) {
            LOG.error("Failed to get BTree node on page " + pageNum, e);
            return null;
        }
    }

    /**
     * Set the root node of the tree.
     * 
     * @param rootNode
     * @throws IOException
     */
    protected void setRootNode(BTreeNode rootNode) throws IOException {
        fileHeader.setRootPage(rootNode.page.getPageNum());
        fileHeader.write();
        cache.add(rootNode, 2);
    }

    /**
     * Create the root node.
     * 
     * @param transaction
     * @return The root node
     * @throws IOException
     */
    protected long createRootNode(Txn transaction) throws IOException {
        final BTreeNode root = createBTreeNode(transaction, LEAF, null, true);
        setRootNode(root);
        return root.page.getPageNum();
    }

    /**
     * @return the root node.
     */
    protected BTreeNode getRootNode() {
        try {
            BTreeNode node = (BTreeNode) cache.get(fileHeader.getRootPage());
            if (node == null) {
                final Page page = getPage(fileHeader.getRootPage());
                node = new BTreeNode(page, false);
                node.read();
            }
            cache.add(node, 2);
            return node;
        } catch (final IOException e) {
            LOG.warn("Failed to get root btree node", e);
            return null;
        }
    }

    /**
     * Print a dump of the tree to the given writer. For debug only!
     * @param writer
     * @throws IOException
     * @throws BTreeException
     */
    public void dump(Writer writer) throws IOException, BTreeException {
        final BTreeNode root = getRootNode();
        LOG.debug("ROOT = " + root.page.getPageNum());
        root.dump(writer);
    }

    public TreeMetrics treeStatistics() throws IOException {
        final TreeMetrics metrics = new TreeMetrics(getFile().getName());
        final BTreeNode root = getRootNode();
        root.treeStatistics(metrics);
        return metrics;
    }

    /* Flush the dirty data to the disk and cleans up the cache.
     * @see org.exist.storage.btree.Paged#flush()
     * @return <code>true</code> if something had to be cleaned
     */
    public boolean flush() throws DBException {
        boolean flushed = cache.flush();
        flushed = flushed | super.flush();
        return flushed;
    }

    /*
     * @see org.exist.storage.btree.Paged#close()
     */
	public boolean close() throws DBException {
        if (!isReadOnly()) {
            flush();
        }
        super.close();
        return true;
    }

    protected void dumpValue(Writer writer, Value value, int status) throws IOException {
        final byte[] data = value.getData();
        writer.write('[');
        writer.write(Paged.hexDump(data));
//        for (int i = 0; i < data.length; i++) {
//            writer.write(Integer.toHexString(data[i]));
//        }
        writer.write(']');
    }

    public void rawScan(IndexQuery query, BTreeCallback callback) throws IOException,
            TerminatedException {
        final long pages = getFileHeader().getTotalCount();
        for (int i = 1; i < pages; i++) {
            final Page page = getPage(i);
            page.read();
            if (page.getPageHeader().getStatus() == LEAF) {
                final BTreeNode node = new BTreeNode(page, false);
                node.read();
                node.scanRaw(query, callback);
            }
        }
    }

    protected static class TreeInfo {
        long firstPage;
        int leafPages = 0;

        TreeInfo(long firstPage, int leafs) {
            this.firstPage = firstPage;
            this.leafPages = leafs;
        }
    }

    /**
     * Scan pages by walking through the file sequentially.
     * Optionally remove all inner (branch) pages and return the first leaf page (in order).
     * This method is used to rebuild the btree from the leaf pages.
     *
     * @return
     * @throws IOException
     * @throws TerminatedException
     * @throws DBException
     */
    private TreeInfo scanTree(boolean removeBranches) throws IOException, TerminatedException, DBException {
        final Set<Long> pagePointers = new HashSet<Long>();
        final Set<Long> nextPages = new HashSet<Long>();
        final List<Long> branchPages = new ArrayList<Long>();

        int pageCount = 0;
        final long pages = getFileHeader().getTotalCount();
        for (long i = 0; i < pages; i++) {
            Page page;
            // first check if page is in cache. if yes, use it.
            BTreeNode node = (BTreeNode) cache.get(i);
            if (node != null) {
                page = node.page;
            } else {
                page = getPage(i);
                page.read();
            }
            if (page.getPageHeader().getStatus() == LEAF) {
                pageCount++;
                if (node == null) {
                    node = new BTreeNode(page, false);
                    node.read();
                }
                cache.add(node);
                pagePointers.add(node.page.getPageNum());
                if (node.pageHeader.getNextPage() != Page.NO_PAGE) {
                    nextPages.add(node.pageHeader.getNextPage());
                }
            } else if (page.getPageHeader().getStatus() == BRANCH) {
                branchPages.add(page.getPageNum());
            }
        }
        pagePointers.removeAll(nextPages);
        if (pagePointers.size() > 1) {
            for (long pointer: pagePointers) {
                System.out.println("Start page: " + pointer);
            }
            throw new DBException("More than one start page found for btree");
        }
        if (removeBranches) {
            for (long p : branchPages) {
                Page page = getPage(p);
                page.read();
                BTreeNode node = new BTreeNode(page, false);
                node.read();
                cache.remove(node);
                unlinkPages(page);
                page.getPageHeader().setDirty(true);
            }
        }
        return new TreeInfo(pagePointers.iterator().next(), pageCount);
    }

    public void scanSequential(long pageNum, BTreeCallback callback) throws IOException, TerminatedException {
        while (pageNum != Page.NO_PAGE) {
            System.out.print(pageNum + " ");
            final BTreeNode node = getBTreeNode(pageNum);
            node.scanRaw(null, callback);
            pageNum = node.pageHeader.getNextPage();
        }
        System.out.println();
    }

    public void scanSequential() throws TerminatedException, IOException, DBException {
        TreeInfo info = scanTree(false);
        System.out.println("Sequential scan...");
        scanSequential(info.firstPage, new BTreeCallback() {
            @Override
            public boolean indexInfo(Value value, long pointer) throws TerminatedException {
                return true;
            }
        });
    }

    /**
     * Rebuild the btree: removes all branches and rebuilds the tree by scanning
     * through leaf pages.
     *
     * @throws TerminatedException
     * @throws IOException
     * @throws DBException
     */
    public void rebuild() throws TerminatedException, IOException, DBException {
        TreeInfo info  = scanTree(true);
        if (info.leafPages == 1) {
            BTreeNode root = getBTreeNode(info.firstPage);
            setRootNode(root);
            cache.add(root);
        } else {
            // create a new root node
            final BTreeNode root = createBTreeNode(null, BRANCH, null, false);
            setRootNode(root);
            // insert a pointer to the first page into the root
            BTreeNode node = getBTreeNode(info.firstPage);
            root.insertPointer(info.firstPage, 0);
            cache.add(root);
            node.setParent(root);
            node.saved = false;
            cache.add(node);

            // scan through chain of pages and add them to the tree
            long rightPageNum = node.pageHeader.getNextPage();
            while (rightPageNum != Page.NO_PAGE) {

                node = getBTreeNode(rightPageNum);

                rightPageNum = node.pageHeader.getNextPage();

                // promote first key of page to parent
                if (node.nKeys < 1) {
    //                throw new IOException("No keys found in page " + node.page.getPageNum());
                    continue;
                }
                final Value key = node.keys[0];
                final BTreeNode parent = findParent(key);
                if (parent == null) {
                    throw new IOException("Parent is null for page " + node.page.getPageNum());
                }
                if (parent.pageHeader.getStatus() != BRANCH) {
                    throw new IOException("Not a branch page: " + parent.page.getPageNum());
                }

                parent.promoteValue(null, key, node);
            }
        }
    }

    /**
     * Walk the tree to find the parent page to which key should
     * be promoted.
     *
     * @param key
     * @return
     * @throws IOException
     */
    private BTreeNode findParent(Value key) throws IOException {
        BTreeNode node = getRootNode();
        BTreeNode last = node;
        while (node.pageHeader.getStatus() != LEAF) {
            last = node;
            try {
                int idx = node.searchKey(key);
                idx = idx < 0 ? - (idx + 1) : idx + 1;
                node = node.getChildNode(idx);
            } catch (Exception e) {
                e.printStackTrace();
                throw new IOException("Error while scanning page " + node.page.getPageNum());
            }
        }
        return last;
    }

    /* -------------------------------------------------------------------------
     * Methods used by recovery and transaction management
     * ---------------------------------------------------------------------- */

    private void writeToLog(Loggable loggable, BTreeNode node) {
        try {
            logManager.writeToLog(loggable);
            node.page.getPageHeader().setLsn(loggable.getLsn());
        } catch (final TransactionException e) {
            LOG.warn(e.getMessage(), e);
        }
    }

    protected boolean requiresRedo(Loggable loggable, Page page) {
        return loggable.getLsn() > page.getPageHeader().getLsn();
    }

    protected void redoCreateBTNode(CreateBTNodeLoggable loggable) throws LogException {
        BTreeNode node = (BTreeNode) cache.get(loggable.pageNum);
        if (node == null) {
            // node is not yet loaded. Load it
            try {
                final Page page = getPage(loggable.pageNum);
                page.read();
                if ((page.getPageHeader().getStatus() == BRANCH ||
                        page.getPageHeader().getStatus() == LEAF) &&
                        page.getPageHeader().getLsn() != Lsn.LSN_INVALID &&
                        !requiresRedo(loggable, page)) {
                    // node already found on disk: read it
                    node = new BTreeNode(page, false);
                    node.read();
                    return;
                } else {
                    // create a new node
                    node = new BTreeNode(page, true);
                    node.pageHeader.setStatus(loggable.status);
                    node.setPointers(new long[0]);
                    node.write();
                }
                node.pageHeader.setLsn(loggable.getLsn());
                node.pageHeader.parentPage = loggable.parentNum;
                final int increment = node.pageHeader.getStatus() == BRANCH ? 2 : 1;
                cache.add(node, increment);
            } catch (final IOException e) {
                throw new LogException(e.getMessage(), e);
            }
        }
    }

    protected void redoInsertValue(InsertValueLoggable loggable) throws LogException {
        final BTreeNode node = getBTreeNode(loggable.pageNum);
        if (requiresRedo(loggable, node.page)) {
            node.insertKey(loggable.key, loggable.idx);
            node.insertPointer(loggable.pointer, loggable.pointerIdx);
            node.adjustDataLen(loggable.idx);
            node.pageHeader.setLsn(loggable.getLsn());
        }
    }

    protected void undoInsertValue(InsertValueLoggable loggable) throws LogException {
        try {
            removeValue(null, loggable.key);
        } catch (final BTreeException e) {
            LOG.error("Failed to undo: " + loggable.dump(), e);
        } catch (final IOException e) {
            LOG.error("Failed to undo: " + loggable.dump(), e);
        }
    }

    protected void redoUpdateValue(UpdateValueLoggable loggable) throws LogException {
            final BTreeNode node = getBTreeNode(loggable.pageNum);
        if (node.page.getPageHeader().getLsn() != Page.NO_PAGE && requiresRedo(loggable, node.page)) {
            if (loggable.idx > node.ptrs.length) {
                LOG.warn(node.page.getPageInfo() +
                        "; loggable.idx = " + loggable.idx + "; node.ptrs.length = " + node.ptrs.length);
                final StringWriter writer = new StringWriter();
                try {
                    dump(writer);
                } catch (final Exception e) {
                    LOG.warn(e);
                    e.printStackTrace();
                }
                LOG.warn(writer.toString());
                throw new LogException("Critical error during recovery");
            }
            node.ptrs[loggable.idx] = loggable.pointer;
            node.pageHeader.setLsn(loggable.getLsn());
            node.saved = false;
        }
    }

    protected void undoUpdateValue(UpdateValueLoggable loggable) throws LogException {
        try {
            addValue(null, loggable.key, loggable.oldPointer);
        } catch (final BTreeException e) {
            LOG.error("Failed to undo: " + loggable.dump(), e);
        } catch (final IOException e) {
            LOG.error("Failed to undo: " + loggable.dump(), e);
        }
    }

    protected void redoRemoveValue(RemoveValueLoggable loggable) throws LogException {
        final BTreeNode node = getBTreeNode(loggable.pageNum);
        if (node.page.getPageHeader().getLsn() != Page.NO_PAGE && requiresRedo(loggable, node.page)) {
            node.removeKey(loggable.idx);
            node.removePointer(loggable.idx);
            node.recalculateDataLen();
            node.pageHeader.setLsn(loggable.getLsn());
        }
    }

    protected void undoRemoveValue(RemoveValueLoggable loggable) throws LogException {
        try {
            addValue(null, loggable.oldValue, loggable.oldPointer);
        } catch (final BTreeException e) {
            LOG.error("Failed to undo: " + loggable.dump(), e);
        } catch (final IOException e) {
            LOG.error("Failed to undo: " + loggable.dump(), e);
        }
    }

    protected void redoUpdatePage(UpdatePageLoggable loggable) throws LogException {
        final BTreeNode node = getBTreeNode(loggable.pageNum);
        if (requiresRedo(loggable, node.page)) {
            node.prefix = loggable.prefix;
            node.keys = loggable.values;
            node.nKeys = loggable.values.length;
            node.pageHeader.setValueCount((short) node.nKeys);
            node.setPointers(loggable.pointers);
            node.recalculateDataLen();
            node.pageHeader.setLsn(loggable.getLsn());
        }
    }

    protected void redoSetParent(SetParentLoggable loggable) throws LogException {
            final BTreeNode node = getBTreeNode(loggable.pageNum);
        if (requiresRedo(loggable, node.page)) {
            node.pageHeader.parentPage = loggable.parentNum;
            node.pageHeader.setLsn(loggable.getLsn());
            node.saved = false;
        }
    }

    protected void redoSetPageLink(SetPageLinkLoggable loggable) throws LogException {
        final BTreeNode node = getBTreeNode(loggable.pageNum);
        if (requiresRedo(loggable, node.page)) {
            node.pageHeader.setNextPage(loggable.nextPage);
            node.pageHeader.setLsn(loggable.getLsn());
            node.saved = false;
        }
    }

    /**
     * A node in the B+-tree. Every node is backed by a Page for
     * storing the node's data. Both, branch and leaf nodes are represented
     * by this class. Each node stores its keys as instances of {@link Value}
     * and its values as pointers of type long.
     * 
     *  If the node is a branch, the long pointers point to the child nodes
     *  of the branch. If it is a leaf, the pointers contain the virtual storage
     *  of the data section associated to the key.
     *  
     * @author wolf
     *
     */
    protected final class BTreeNode implements BTreeCacheable {

        /** defines the default size for the keys array */
        private final static int DEFAULT_INITIAL_ENTRIES = 32;
        /** the underlying Page object that stores the node's data */
        private Page page;
        private BTreePageHeader pageHeader;

        /** stores the keys in this page */
        private Value[] keys;

        private Value prefix = Value.EMPTY_VALUE;

        /** the number of keys currently stored */
        private int nKeys = 0;

        /** 
         * stores the page pointers to child nodes (for branches)
         * or the storage address (for leaf nodes).
         */
        private long[] ptrs;

        /** the number of pointers currently used */
        private int nPtrs = 0;

        /** fields used by the Cacheable interface */
        private int refCount = 0;
        private int timestamp = 0;

        /** does this node need to be saved? */
        private boolean saved = true;

        /** the computed raw data size required by this node */
        private int currentDataLen = -1;

        private boolean allowUnload = true;

        public BTreeNode(Page page, boolean newPage) {
            this.page = page;
            pageHeader = (BTreePageHeader) page.getPageHeader();
            if (newPage) {
                keys = new Value[DEFAULT_INITIAL_ENTRIES];
                ptrs = new long[DEFAULT_INITIAL_ENTRIES + 1];
                pageHeader.setValueCount((short) 0);
                saved = false;
            }
        }

        /**
         * Set the link to the parent of this node.
         * 
         * @param parent
         */
        public void setParent(BTreeNode parent) {
            if (parent != null) {
                pageHeader.parentPage = parent.page.getPageNum();
            } else {
                pageHeader.parentPage = Page.NO_PAGE;
            }
            saved = false;
        }
		
        /**
         * @return the parent of this node.
         */
        public BTreeNode getParent() {
            if (pageHeader.parentPage != Page.NO_PAGE) {
                return getBTreeNode(pageHeader.parentPage);
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
                {++refCount;}
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
            return allowUnload;
        }

        /**
         * @see org.exist.storage.cache.Cacheable#getTimestamp()
         */
        public int getTimestamp() {
            return timestamp;
        }

        public boolean isInnerPage() {
            return pageHeader.getStatus() == BRANCH;
        }

        /**
         * @see org.exist.storage.cache.Cacheable#sync(boolean syncJournal)
         */
        public boolean sync(boolean syncJournal) {
            if(isDirty()) {
                try {
                    write();
                    if (isTransactional && syncJournal)
                        {logManager.flushToLog(true);}
                    return true;
                } catch (final IOException e) {
                    LOG.error("IO error while writing page: " + page.getPageNum(), e);
                }
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

        /**
         * Set the keys of this node.
         * 
         * @param vals
         */
        private void setValues(Value[] vals) {
            keys = vals;
            nKeys = vals.length;
            pageHeader.setValueCount((short) nKeys);
            saved = false;
        }

        /**
         * Set the array of pointers of this node.
         * 
         * @param pointers
         */
        private void setPointers(long[] pointers) {
            ptrs = pointers;
            nPtrs = pointers.length;
            saved = false;
        }

        /**
         * Returns the raw data size (in bytes) required by this node.
         * 
         * @return The data length
         */
        private int getDataLen() {
            return currentDataLen < 0 ?
                recalculateDataLen() : currentDataLen;
        }

        /**
         * Recalculates the raw data size (in bytes) required by this node.
         * 
         * @return the data length
         */
        private int recalculateDataLen() {
            currentDataLen = ptrs == null ? 0 : nPtrs * 8;
            if(fileHeader.getFixedKeyLen() < 0) {
                currentDataLen += 2 * nKeys;
            }
            if (pageHeader.getStatus() == BRANCH)
                {currentDataLen += prefix.getLength() + 2;}
            if (pageHeader.getStatus() == LEAF)
                {currentDataLen += nKeys - 1;}
            for (int i = 0; i < nKeys; i++) {
                if (pageHeader.getStatus() == LEAF && i > 0) {
                    // if this is a leaf page, we use prefix compression to store the keys,
                    // so subtract the size of the prefix
                    int prefix = keys[i].commonPrefix(keys[i - 1]);
                    if (prefix < 0 || prefix > Byte.MAX_VALUE)
                        {prefix = 0;}
                    currentDataLen += keys[i].getLength() - prefix;
                } else
                    {currentDataLen += keys[i].getLength();}
            }
            return currentDataLen;
        }

        /**
         * Add the raw data size required to store the value to the internal
         * data size of this node.
         *  
         */
        private void adjustDataLen(int idx) {
            if(currentDataLen < 0) {
                recalculateDataLen();
                return;
            }
            if (pageHeader.getStatus() == LEAF && idx > 0) {
                // if this is a leaf page, we use prefix compression to store the keys,
                // so subtract the size of the prefix
                int prefix;
                if (idx + 1< nKeys) {
                    // recalculate the prefix length for the following value
                    prefix = calculatePrefixLen(idx + 1, idx - 1);
                    currentDataLen -= keys[idx + 1].getLength() - prefix;
                    prefix = calculatePrefixLen(idx + 1, idx);
                    currentDataLen += keys[idx + 1].getLength() - prefix;
                }
                // calculate the prefix length for the new value
                prefix = calculatePrefixLen(idx, idx - 1);
                currentDataLen += keys[idx].getLength() - prefix;
                currentDataLen++; // add one byte for the prefix length
            } else {
                currentDataLen += keys[idx].getLength();
                if (pageHeader.getStatus() == LEAF) {
                    currentDataLen++;
                }
            }
            currentDataLen += 8;
            if(fileHeader.getFixedKeyLen() < 0) {
                currentDataLen += 2;
            }
        }

        private int calculatePrefixLen(int idx0, int idx1) {
            int prefix;
            prefix = keys[idx0].commonPrefix(keys[idx1]);
            if (prefix < 0 || prefix > Byte.MAX_VALUE)
                {prefix = 0;}
            return prefix;
        }

        /**
         * Compute where to split a page: tries to split at half the data size
         *
         * @return
         */
        private int getPivot(int preferred) {
            if (nKeys == 2) {
                return 1;
            }
            final int totalLen = getKeyDataLen();
            int currentLen = 0;
            int pivot = nKeys - 1;
            for (int i = 0; i < nKeys - 1; i++) {
                if (pageHeader.getStatus() == LEAF && i > 0) {
                    // if this is a leaf page, we use prefix compression to store the keys,
                    // so subtract the size of the prefix
                    int prefix = keys[i].commonPrefix(keys[i - 1]);
                    if (prefix < 0 || prefix > Byte.MAX_VALUE)
                        prefix = 0;
                    currentLen += keys[i].getLength() - prefix;
                } else
                    currentLen += keys[i].getLength();
                if (currentLen > totalLen / 2 || i + 1 == preferred) {
                    pivot = currentLen > fileHeader.getWorkSize() ? i : i + 1;
                    break;
                }
            }
            return pivot;
        }

        private int getKeyDataLen() {
            int totalLen = 0;
            for (int i = 0; i < nKeys; i++) {
                if (pageHeader.getStatus() == LEAF && i > 0) {
                    // if this is a leaf page, we use prefix compression to store the keys,
                    // so subtract the size of the prefix
                    int prefix = keys[i].commonPrefix(keys[i - 1]);
                    if (prefix < 0 || prefix > Byte.MAX_VALUE)
                        prefix = 0;
                    totalLen += keys[i].getLength() - prefix;
                } else {
                    totalLen += keys[i].getLength();
                }
            }
            return totalLen;
        }

        private boolean mustSplit() {
            if (pageHeader.getValueCount() != nKeys)
                {throw new RuntimeException("Wrong value count");}
            return getDataLen() > fileHeader.getWorkSize();
        }

        /**
         * Read the node from the underlying page.
         * 
         * @throws IOException
         */
        private void read() throws IOException {
            final byte[] data = page.read();
            final short keyLen = fileHeader.getFixedKeyLen();
            short valSize = keyLen;
            int p = 0;
            // it this is a branch node, read the common prefix
            if (pageHeader.getStatus() == BRANCH) {
                short prefixSize = ByteConversion.byteToShort(data, p);
                p += 2;
                if (prefixSize == 0) {
                    prefix = Value.EMPTY_VALUE;
                } else {
                    prefix = new Value(data, p, prefixSize);
                    p += prefixSize;
                }
            }
            nKeys = pageHeader.getValueCount();
            keys = new Value[(nKeys * 3) / 2 + 1];
            for (int i = 0; i < nKeys; i++) {
                if (keyLen < 0) {
                    valSize = ByteConversion.byteToShort(data, p);
                    p += 2;
                }
                if (pageHeader.getStatus() == LEAF && i > 0) {
                    // for leaf pages, we use prefix compression to increase the number of
                    // keys that can be stored on one page. Each key is stored as follows:
                    // [valSize, prefixLen, value], where prefixLen specifies the number of
                    // leading bytes the key has in common with the previous key.
                    final int prefixLen = (data[p++] & 0xFF);
                    try {
                        final byte[] t = new byte[valSize];
                        if (prefixLen > 0)
                            // copy prefixLen leading bytes from the previous key
                            {System.arraycopy(keys[i - 1].data(), keys[i - 1].start(), t, 0, prefixLen);}
                        // read the remaining bytes
                        System.arraycopy(data, p, t, prefixLen, valSize - prefixLen);
                        p += valSize - prefixLen;
                        keys[i] = new Value(t);
                    } catch (final Exception e) {
                        e.printStackTrace();
                        LOG.error("prefixLen = " + prefixLen + "; i = " + i + "; nKeys = " + nKeys);
                        throw new IOException(e.getMessage());
                    }
                } else {
                    keys[i] = new Value(data, p, valSize);
                    p += valSize;
                }
            }
            //	Read in the pointers
            nPtrs = pageHeader.getPointerCount();
            ptrs = new long[(nPtrs * 3) / 2 + 1];
            for (int i = 0; i < nPtrs; i++) {
                ptrs[i] = ByteConversion.byteToLong(data, p);
                p += 8;
            }
        }

        /**
         * Write the node to the underlying page.
         * 
         * @throws IOException
         */
        private void write() throws IOException {
            final byte[] temp = new byte[fileHeader.getWorkSize()];
            int p = 0;
            byte[] data;
            if (nKeys != pageHeader.getValueCount())
                {throw new RuntimeException("nkeys: " + nKeys + " valueCount: " + pageHeader.getValueCount());}
            // if this is a branch node, write out the common prefix
            if (pageHeader.getStatus() == BRANCH) {
                ByteConversion.shortToByte((short) prefix.getLength(), temp, p);
                p += 2;
                if (prefix.getLength() > 0) {
                    System.arraycopy(prefix.data(), prefix.start(), temp, p, prefix.getLength());
                    p += prefix.getLength();
                }
            }
            final int keyLen = fileHeader.getFixedKeyLen();
            for (int i = 0; i < nKeys; i++) {
                if (keyLen < 0) {
                    ByteConversion.shortToByte((short) keys[i].getLength(), temp, p);
                    p += 2;
                }
                if (pageHeader.getStatus() == LEAF && i > 0) {
                    // for leaf pages, we use prefix compression to increase the number of
                    // keys that can be stored on one page. Each key is stored as follows:
                    // [valSize, prefixLen, value], where prefixLen specifies the number of
                    // leading bytes the key has in common with the previous key.
                    int prefixLen = keys[i].commonPrefix(keys[i - 1]); // determine the common prefix
                    if (prefixLen < 0 || prefixLen > Byte.MAX_VALUE)
                        {prefixLen = 0;}
                    // store the length of the prefix
                    temp[p++] = (byte) prefixLen;
                    // copy the remaining bytes, starting at prefixLen
                    System.arraycopy(keys[i].data(), keys[i].start() + prefixLen, 
                            temp, p, keys[i].getLength() - prefixLen);
                    p += keys[i].getLength() - prefixLen;
                } else {
                    data = keys[i].getData();
                    if(p + data.length > temp.length)
                        {throw new IOException("calculated: " + getDataLen() +
                            "; required: " + (p + data.length));}
                    System.arraycopy(data, 0, temp, p, data.length);
                    p += data.length;
                }
            }
            for (int i = 0; i < nPtrs; i++) {
                ByteConversion.longToByte(ptrs[i], temp, p);
                p += 8;
            }
            writeValue(page, new Value(temp));
            saved = true;
        }

        /**
         * Retrieve the child node at guven index.
         * 
         * @param idx The index
         * @return The BTree node
         * @throws IOException
         */
        private BTreeNode getChildNode(int idx) throws IOException {
            if (pageHeader.getStatus() == BRANCH && idx >= 0 && idx < nPtrs)
                {return getBTreeNode(ptrs[idx]);}
            else
                {return null;}
        }

        /**
         * Remove a key.
         */
        private long removeValue(Txn transaction, Value key) throws IOException, BTreeException {
            int idx = searchKey(key);
            switch (pageHeader.getStatus()) {
            case BRANCH :
                idx = idx < 0 ? - (idx + 1) : idx + 1;
                return getChildNode(idx).removeValue(transaction, key);
            case LEAF :
                if (idx < 0)
                    {return KEY_NOT_FOUND;}
                else {
                    try {
                        allowUnload = false;
                        if (transaction != null && isTransactional) {
                            final RemoveValueLoggable log = new RemoveValueLoggable(transaction, 
                                fileId, page.getPageNum(), idx, keys[idx], ptrs[idx]);
                            writeToLog(log, this);
                        }
                        final long oldPtr = ptrs[idx];
                        removeKey(idx);
                        removePointer(idx);
                        recalculateDataLen();
                        return oldPtr;
                    } finally {
                        allowUnload = true;
                    }
                }
            default :
                throw new BTreeException("Invalid Page Type In removeValue");
            }
        }

        /**
         * Add a key and the corresponding pointer to the node.
         */
        private long addValue(Txn transaction, Value value, long pointer) 
                throws IOException, BTreeException {
            if (value == null)
                {return -1;}
            int idx = searchKey(value);
            switch (pageHeader.getStatus()) {
            case BRANCH :
                idx = idx < 0 ? - (idx + 1) : idx + 1;
                return getChildNode(idx).addValue(transaction, value, pointer);
            case LEAF :
                try {
                    allowUnload = false;
                    if (idx >= 0) {
                        // Value was found... Overwrite
                        final long oldPtr = ptrs[idx];
                        if (transaction != null && isTransactional) {
                            final UpdateValueLoggable loggable = new UpdateValueLoggable(transaction, 
                                fileId, page.getPageNum(), idx, value, pointer, oldPtr);
                                writeToLog(loggable, this);
                        }
                        ptrs[idx] = pointer;
                        saved = false;
                        return oldPtr;
                    } else {
                        // Value was not found
                        idx = - (idx + 1);
                        if (transaction != null && isTransactional) {
                            final InsertValueLoggable loggable = new InsertValueLoggable(transaction, 
                                fileId, page.getPageNum(), idx, value, idx, pointer);
                                writeToLog(loggable, this);
                            }
                            insertKey(value, idx);
                            insertPointer(pointer, idx);
                            adjustDataLen(idx);
                            if (mustSplit()) {
                                // we normally split a node at its median value.
                                // however, if the inserted key is in the upper or lower
                                // section of the node, we split directly at the key. this
                                // has advantages if keys are inserted in ascending order
                                if (splitFactor > 0 && idx > (nKeys * splitFactor) && value.getLength() < fileHeader.getWorkSize() / 4) {
                                    split(transaction, idx == 0 ? 1 : idx);
                                } else {
                                    split(transaction);
                                }
                            }
                    }
                } finally {
                    allowUnload = true;
                }
                return -1;
            default :
                throw new BTreeException("Invalid Page Type In addValue: " + 
                    pageHeader.getStatus() + "; " + page.getPageInfo());
            }
        }

        /**
         * Promote a key to the parent node. Called by {@link #split(Txn)}.
         */
        private void promoteValue(Txn transaction, Value value, BTreeNode rightNode)
                throws IOException, BTreeException {
            int idx = searchKey(value);
            idx = idx < 0 ? -( idx + 1) : idx + 1;
            insertKey(value, idx);
            insertPointer(rightNode.page.getPageNum(), idx + 1);
            rightNode.setParent(this);
            rightNode.saved = false;
            cache.add(rightNode);
            this.saved = false;
            cache.add(this);
            final boolean split = recalculateDataLen() > fileHeader.getWorkSize();
            if (split)
                {split(transaction);}
        }

        private void split(Txn transaction) throws IOException, BTreeException {
            split(transaction, -1);
        }

        /**
         * Split the node.
         *
         * @param transaction the current transaction
         */
        private void split(Txn transaction, int pivot) throws IOException, BTreeException {
            Value[] leftVals;
            Value[] rightVals;
            long[] leftPtrs;
            long[] rightPtrs;
            Value separator;
            final short vc = pageHeader.getValueCount();

            pivot = getPivot(pivot);
            // Split the node into two nodes
            switch (pageHeader.getStatus()) {
                case BRANCH :
                    leftVals = new Value[pivot];
                    leftPtrs = new long[leftVals.length + 1];
                    rightVals = new Value[vc - (pivot + 1)];
                    rightPtrs = new long[rightVals.length + 1];
                    System.arraycopy(keys, 0, leftVals, 0, leftVals.length);
                    System.arraycopy(ptrs, 0, leftPtrs, 0, leftPtrs.length);
                    System.arraycopy(keys, leftVals.length + 1, rightVals, 0, rightVals.length);
                    System.arraycopy(ptrs, leftPtrs.length, rightPtrs, 0, rightPtrs.length);
                    separator = keys[leftVals.length];
                    if (prefix != null && prefix.getLength() > 0) {
                        final byte[] t = new byte[prefix.getLength() + separator.getLength()];
                        System.arraycopy(prefix.data(), prefix.start(), t, 0, prefix.getLength());
                        System.arraycopy(separator.data(), separator.start(), t, prefix.getLength(), separator.getLength());
                        separator = new Value(t);
                    }
                    break;
                case LEAF :
                    leftVals = new Value[pivot];
                    leftPtrs = new long[leftVals.length];
                    rightVals = new Value[vc - pivot];
                    rightPtrs = new long[rightVals.length];
                    System.arraycopy(keys, 0, leftVals, 0, leftVals.length);
                    System.arraycopy(ptrs, 0, leftPtrs, 0, leftPtrs.length);
                    System.arraycopy(keys, leftVals.length, rightVals, 0, rightVals.length);
                    System.arraycopy(ptrs, leftPtrs.length, rightPtrs, 0, rightPtrs.length);
                    separator = keys[leftVals.length];
                    break;
                default :
                    throw new BTreeException("Invalid Page Type In split");
            }
            // Log the update of the current page
            if (transaction != null && isTransactional && pageHeader.getStatus() == LEAF) {
                final Loggable log = new UpdatePageLoggable(transaction, fileId,
                    page.getPageNum(), prefix, leftVals, leftVals.length, leftPtrs, leftPtrs.length);
                writeToLog(log, this);
            }
            setValues(leftVals);
            setPointers(leftPtrs);
            recalculateDataLen();
            // Promote the pivot to the parent branch
            BTreeNode parent = getParent();
            if (parent == null) {
                // This can only happen if this is the root
                parent = createBTreeNode(transaction, BRANCH, null, false);
                // Log change of the parent page
                if (transaction != null && isTransactional && pageHeader.getStatus() == LEAF) {
                    final Loggable log = new SetParentLoggable(transaction, fileId, page.getPageNum(), 
                        parent.page.getPageNum());
                    writeToLog(log, this);
                }
                setParent(parent);
                final BTreeNode rNode = createBTreeNode(transaction, pageHeader.getStatus(), parent, false);
                rNode.setValues(rightVals);
                rNode.setPointers(rightPtrs);
                rNode.setAsParent(transaction);
                if (pageHeader.getStatus() == BRANCH) {
                    rNode.prefix = prefix;
                    rNode.growPrefix();
                } else {
                    if (transaction != null && isTransactional) {
                        final Loggable log = new SetPageLinkLoggable(transaction, 
                            fileId, page.getPageNum(), rNode.page.getPageNum());
                        writeToLog(log, this);
                    }
                    pageHeader.setNextPage(rNode.page.getPageNum());
                }
                // Log update of the right node
                if (isTransactional && transaction != null && pageHeader.getStatus() == LEAF) {
                    final Loggable log = new UpdatePageLoggable(transaction, fileId,
                        rNode.page.getPageNum(), rNode.prefix, rNode.keys, rNode.nKeys, rightPtrs, rightPtrs.length);
                    writeToLog(log, rNode);
                }
                rNode.recalculateDataLen();
                parent.prefix = separator;
                parent.setValues(new Value[] { Value.EMPTY_VALUE });
                parent.setPointers(new long[] { page.getPageNum(), rNode.page.getPageNum()});
                parent.recalculateDataLen();
                cache.add(parent);
                setRootNode(parent);
                if(rNode.mustSplit()) {
                    LOG.debug(getFile().getName() + " right node requires second split: " + rNode.getDataLen());
                    rNode.split(transaction);
                }
                cache.add(rNode);
            } else {
                final BTreeNode rNode = createBTreeNode(transaction, pageHeader.getStatus(), parent, false);
                rNode.setValues(rightVals);
                rNode.setPointers(rightPtrs);
                rNode.setAsParent(transaction);
                if (pageHeader.getStatus() == BRANCH) {
                    rNode.prefix = prefix;
                    rNode.growPrefix();
                } else {
                    if (transaction != null && isTransactional) {
                        Loggable log = new SetPageLinkLoggable(transaction, fileId, 
                            rNode.page.getPageNum(), pageHeader.getNextPage());
                        writeToLog(log, this);
                        log = new SetPageLinkLoggable(transaction, fileId, 
                            page.getPageNum(), rNode.page.getPageNum());
                        writeToLog(log, this);
                    }
                    rNode.pageHeader.setNextPage(pageHeader.getNextPage());
                    pageHeader.setNextPage(rNode.page.getPageNum());
                }
                // Log update of the right node
                if (isTransactional && transaction != null && pageHeader.getStatus() == LEAF) {
                    final Loggable log = new UpdatePageLoggable(transaction, fileId, 
                        rNode.page.getPageNum(), rNode.prefix, rNode.keys,
                        rNode.nKeys, rightPtrs, rightPtrs.length);
                    writeToLog(log, rNode);
                }
                rNode.recalculateDataLen();
                if(rNode.mustSplit()) {
                    LOG.debug(getFile().getName() + " right node requires second split: " + rNode.getDataLen());
                    rNode.split(transaction);
                }
                cache.add(rNode);
                parent.promoteValue(transaction, separator, rNode);
            }
            cache.add(this);
            if (mustSplit()) {
                LOG.debug(getFile().getName() + "left node requires second split: " + getDataLen());
                split(transaction);
            }
        }

        /** Set the parent-link in all child nodes to point to this node */
        private void setAsParent(Txn transaction) {
            if (pageHeader.getStatus() == BRANCH) {
                for (int i = 0; i < nPtrs; i++) {
                    final BTreeNode node = getBTreeNode(ptrs[i]);
                    node.setParent(this);
                    cache.add(node);
                }
            }
        }

        /**
         * Locate the given value in the keys and return the
         * associated pointer.
         */
        private long findValue(Value value) throws IOException, BTreeException {
            int idx = searchKey(value);
            switch (pageHeader.getStatus()) {
            case BRANCH :
                idx = idx < 0 ? - (idx + 1) : idx + 1;
                final BTreeNode child = getChildNode(idx);
                if (child == null)
                    {throw new BTreeException("Unexpected " + idx + ", " + 
                        page.getPageNum() + ": value '" + value.toString() + "' doesn't exist");}
                return child.findValue(value);
            case LEAF :
                if (idx < 0) {
                    return KEY_NOT_FOUND;
                } else {
                    return ptrs[idx];
                }
            default :
                throw new BTreeException("Invalid Page Type In findValue");
            }
        }

        public String toString() {
            final StringWriter writer = new StringWriter();
            try {
                dump(writer);
            } catch (final Exception e) {
                //TODO : add something here ! -pb
            }
            return writer.toString();
        }

        private void treeStatistics(TreeMetrics metrics) throws IOException {
            metrics.addPage(pageHeader.getStatus());
            if (pageHeader.getStatus() == BRANCH) {
                for (int i = 0; i < nPtrs; i++) {
                    final BTreeNode child = getChildNode(i);
                    child.treeStatistics(metrics);
                }
            }
        }

        /**
         * Prints out a debug view of the node to the given writer.
         */
        private void dump(Writer writer) throws IOException, BTreeException {
            //if (pageHeader.getStatus() == LEAF)
            //    return;
            if (page.getPageNum() == fileHeader.getRootPage())
                {writer.write("ROOT: ");}
            writer.write(page.getPageNum() + ": ");
            writer.write(pageHeader.getStatus() == BRANCH ? "BRANCH: " : "LEAF: ");
            writer.write(saved ? "SAVED: " : "DIRTY: ");
            if (pageHeader.getStatus() == BRANCH) {
                writer.write("PREFIX: ");
                dumpValue(writer, prefix, pageHeader.getStatus());
                writer.write(": ");
            }
            writer.write("NEXT: ");
            writer.write(Long.toString(pageHeader.getNextPage()));
            writer.write(": ");
            for (int i = 0; i < nKeys; i++) {
                if (i > 0)
                    {writer.write(' ');}
                dumpValue(writer, keys[i], pageHeader.getStatus());
            }
            writer.write('\n');
            if (pageHeader.getStatus() == BRANCH) {
                writer.write("-----------------------------------------------------------------------------------------\n");
                writer.write(page.getPageNum() + " POINTERS: ");
                for (int i = 0; i < nPtrs; i++) {
                    writer.write(ptrs[i] + " ");
                }
                writer.write('\n');
            }
            writer.write("-----------------------------------------------------------------------------------------\n");
            if (pageHeader.getStatus() == BRANCH) {
                for (int i = 0; i < nPtrs; i++) {
                    final BTreeNode child = getChildNode(i);
                    child.dump(writer);
                }
            }
        }

        /**
         * Search for keys matching the given {@link IndexQuery} and
         * report the to the specified {@link BTreeCallback}.
         * 
         * @param query
         * @param callback
         * @throws IOException
         * @throws BTreeException
         * @throws TerminatedException
         */
        private void query(IndexQuery query, BTreeCallback callback)
                throws IOException, BTreeException, TerminatedException {
            if (query != null
                    && query.getOperator() != IndexQuery.ANY
                    && query.getOperator() != IndexQuery.TRUNC_LEFT) {
                final Value[] qvals = query.getValues();
                int leftIdx = searchKey(qvals[0]);
                int rightIdx = qvals.length > 1 ?
                    searchKey(qvals[qvals.length - 1]) : leftIdx;
                    boolean pos = query.getOperator() >= 0;
                    switch (pageHeader.getStatus()) {
                    case BRANCH :
                        leftIdx = leftIdx < 0 ? - (leftIdx + 1) : leftIdx + 1;
                        rightIdx = rightIdx < 0 ? - (rightIdx + 1) : rightIdx + 1;
                        switch (query.getOperator()) {
                        case IndexQuery.BWX :
                        case IndexQuery.NBWX :
                        case IndexQuery.BW :
                        case IndexQuery.NBW :
                        case IndexQuery.IN :
                        case IndexQuery.NIN :
                        case IndexQuery.TRUNC_RIGHT :
                        case IndexQuery.RANGE :
                            for (int i = 0; i < nPtrs; i++)
                                if ((i >= leftIdx && i <= rightIdx) == pos) {
                                    getChildNode(i).query(query, callback);
                                    if (query.getOperator() == IndexQuery.TRUNC_RIGHT ||
                                        query.getOperator() == IndexQuery.RANGE)
                                        {break;}
                                }
                            break;
                        case IndexQuery.NEQ :
                            getChildNode(0).query(query, callback);
                            break;
                        case IndexQuery.EQ :
                            getChildNode(leftIdx).query(query, callback);
                            break;
                        case IndexQuery.LT :
                            for (int i = 0; i < nPtrs; i++) {
                                if ((pos && (i <= leftIdx)) || (!pos && (i >= leftIdx)))
                                    {getChildNode(i).query(query, callback);}
                            }
                            break;
                        case IndexQuery.GEQ :
                        case IndexQuery.GT :
                            getChildNode(leftIdx).query(query, callback);
                            break;
                        case IndexQuery.LEQ :
                            for (int i = 0; i < nPtrs; i++) {
                                if ((pos && (i >= leftIdx)) || (!pos && (i <= leftIdx)))
                                    {getChildNode(i).query(query, callback);}
                            }
                            break;
                        default :
                            // If it's not implemented, we walk the tree
                            for (int i = 0; i < nPtrs; i++) {
                                getChildNode(i).query(query, callback);
                            }
                            break;
                        }
                        break;
                    case LEAF :
                        switch (query.getOperator()) {
                        case IndexQuery.EQ :
                            if (leftIdx >= 0)
                                {callback.indexInfo(keys[leftIdx], ptrs[leftIdx]);}
                            break;
                        case IndexQuery.NEQ :
                            for (int i = 0; i < nPtrs; i++) {
                                if (i != leftIdx)
                                    {callback.indexInfo(keys[i], ptrs[i]);}
                            }
                            scanNextPage(query, null, callback);
                            break;
                        case IndexQuery.BWX :
                        case IndexQuery.NBWX :
                        case IndexQuery.BW :
                        case IndexQuery.NBW :
                            if (leftIdx < 0)
                                {leftIdx = - (leftIdx + 1);}
                            if (rightIdx < 0)
                                {rightIdx = - (rightIdx + 1);}
                            for (int i = 0; i < nPtrs; i++) {
                                if ((pos && (i >= leftIdx && i <= rightIdx))
                                    || (!pos && (i <= leftIdx || i >= rightIdx))) {
                                    if (query.testValue(keys[i]))
                                        {callback.indexInfo(keys[i], ptrs[i]);}
                                }
                            }
                            break;
                        case IndexQuery.RANGE :
                        case IndexQuery.TRUNC_RIGHT :
                            if (leftIdx < 0)
                                {leftIdx = - (leftIdx + 1);}
                            if (rightIdx < 0)
                                {rightIdx = - (rightIdx + 1);}
                            for (int i = leftIdx; i < rightIdx && i < nPtrs; i++) {
                                if (query.testValue(keys[i]))
                                        {callback.indexInfo(keys[i], ptrs[i]);}
                            }
                            if (rightIdx >= nPtrs)
                                {scanNextPage(query, null, callback);}
                            break;
                        case IndexQuery.IN :
                        case IndexQuery.NIN :
                            if (leftIdx < 0)
                                {leftIdx = - (leftIdx + 1);}
                            if (rightIdx < 0)
                                {rightIdx = - (rightIdx + 1);}
                            for (int i = 0; i < nPtrs; i++) {
                                if (!pos || (i >= leftIdx && i <= rightIdx))
                                    {if (query.testValue(keys[i]))
                                        callback.indexInfo(keys[i], ptrs[i]);}
                            }
                            break;
                        case IndexQuery.LT :
                            if (leftIdx < 0)
                                {leftIdx = - (leftIdx + 1);}
                            for (int i = 0; i < nPtrs; i++) {
                                if ((pos && (i <= leftIdx)) || (!pos && (i >= leftIdx)))
                                    {if (query.testValue(keys[i]))
                                        callback.indexInfo(keys[i], ptrs[i]);}
                            }
                            break;
                        case IndexQuery.GEQ :
                        case IndexQuery.GT :
                            if (leftIdx < 0)
                                {leftIdx = - (leftIdx + 1);}
                            for (int i = leftIdx; i < nPtrs; i++) {
                                if (query.testValue(keys[i]))
                                    {callback.indexInfo(keys[i], ptrs[i]);}
                            }
                            scanNextPage(query, null, callback);
                            break;
                        case IndexQuery.LEQ :
                            if (leftIdx < 0)
                                {leftIdx = - (leftIdx + 1);}
                            for (int i = 0; i < nPtrs; i++) {
                                if ((pos && (i >= leftIdx)) || (!pos && (i <= leftIdx))) {
                                    if (query.testValue(keys[i]))
                                        {callback.indexInfo(keys[i], ptrs[i]);}
                                    else if (query.getOperator() == IndexQuery.TRUNC_RIGHT)
                                        {break;}
                                }
                            }
                            break;
                        default :
                            // If it's not implemented, it falls right through
                            for (int i = 0; i < nPtrs; i++) {
                                if (query.testValue(keys[i]))
                                    {callback.indexInfo(keys[i], ptrs[i]);}
                            }
                            break;
                        }
                        break;
                    default :
                        throw new BTreeException("Invalid Page Type In query");
                    }
            } else {
                // No Query - Just Walk The Tree
                switch (pageHeader.getStatus()) {
                case BRANCH :
                    for (int i = 0; i < nPtrs; i++) {
                        getChildNode(i).query(query, callback);
                    }
                    break;
                case LEAF :
                    for (int i = 0; i < nKeys; i++) {
                        if (query == null || query.getOperator() != IndexQuery.TRUNC_LEFT
                                || query.testValue(keys[i]))
                            {callback.indexInfo(keys[i], ptrs[i]);}
                    }
                    break;
                default :
                    throw new BTreeException("Invalid Page Type In query");
                }
            }
        }

        /**
         * Search for keys matching the given {@link IndexQuery} and
         * report the to the specified {@link BTreeCallback}. This specialized
         * method only considers keys whose value starts with the specified keyPrefix.
         * 
         * @param query
         * @param callback
         * @throws IOException
         * @throws BTreeException
         * @throws TerminatedException
         */
        private void query(IndexQuery query, Value keyPrefix, BTreeCallback callback)
                throws IOException, BTreeException, TerminatedException {
            if (query != null
                    && query.getOperator() != IndexQuery.ANY
                    && query.getOperator() != IndexQuery.TRUNC_LEFT) {
                final Value[] qvals = query.getValues();
                int leftIdx = searchKey(qvals[0]);
                int pfxIdx = searchKey(keyPrefix);
                switch (pageHeader.getStatus()) {
                case BRANCH :
                    leftIdx = leftIdx < 0 ? - (leftIdx + 1) : leftIdx + 1;
                    pfxIdx = pfxIdx < 0 ? - (pfxIdx + 1) : pfxIdx + 1;
                    switch (query.getOperator()) {
                    case IndexQuery.EQ :
                        getChildNode(leftIdx).query(query, keyPrefix, callback);
                        break;
                    case IndexQuery.NEQ :
                        getChildNode(pfxIdx).query(query, keyPrefix, callback);
                        break;
                    case IndexQuery.LT :
                        for (int i = pfxIdx; i <= leftIdx && i < nPtrs; i++) {
                            getChildNode(i).query(query, keyPrefix, callback);
                        }
                        break;
                    case IndexQuery.LEQ :
                        for (int i = pfxIdx; i <= leftIdx && i < nPtrs; i++) {
                            getChildNode(i).query(query, keyPrefix, callback);
                        }
                        break;
                    case IndexQuery.GEQ :
                    case IndexQuery.GT :
                        getChildNode(leftIdx).query(query, keyPrefix, callback);
                        break;
                    }
                    break;
                case LEAF :
                    pfxIdx = pfxIdx < 0 ? - (pfxIdx + 1) : pfxIdx + 1;
                    switch (query.getOperator()) {
                    case IndexQuery.EQ :
                        if (leftIdx >= 0)
                            {callback.indexInfo(keys[leftIdx], ptrs[leftIdx]);}
                        break;
                    case IndexQuery.NEQ :
                        for (int i = pfxIdx; i < nPtrs; i++) {
                            if (keys[i].comparePrefix(keyPrefix) > 0)
                                {break;}
                            if (i != leftIdx)
                                {callback.indexInfo(keys[i], ptrs[i]);}
                        }
                        scanNextPage(query, keyPrefix, callback);
                        break;
                    case IndexQuery.LT :
                        if (leftIdx < 0)
                            {leftIdx = - (leftIdx + 1);}
                        for (int i = pfxIdx; i < leftIdx; i++) {
                            if (query.testValue(keys[i]))
                                {callback.indexInfo(keys[i], ptrs[i]);}
                        }
                        break;
                    case IndexQuery.LEQ :
                        if (leftIdx < 0)
                            {leftIdx = - (leftIdx + 1);}
                        for (int i = pfxIdx; i <= leftIdx && i < nPtrs; i++) {
                            if (query.testValue(keys[i]))
                                {callback.indexInfo(keys[i], ptrs[i]);}
                        }
                        break;
                    case IndexQuery.GT :
                    case IndexQuery.GEQ :
                        if (leftIdx < 0)
                            {leftIdx = - (leftIdx + 1);}
                        for (int i = leftIdx; i < nPtrs; i++) {
                            if (keys[i].comparePrefix(keyPrefix) > 0)
                                {return;}
                            if (query.testValue(keys[i]))
                                {callback.indexInfo(keys[i], ptrs[i]);}
                        }
                        scanNextPage(query, keyPrefix, callback);
                        break;
                    }
                    break;
                default :
                    throw new BTreeException("Invalid Page Type In query");
                }
            } else {
                // No Query - Just Walk The Tree
                switch (pageHeader.getStatus()) {
                case BRANCH :
                    for (int i = 0; i < nPtrs; i++) {
                        getChildNode(i).query(query, callback);
                    }
                    break;
                case LEAF :
                    for (int i = 0; i < nKeys; i++) {
                        if (query.getOperator() != IndexQuery.TRUNC_LEFT || query.testValue(keys[i]))
                            {callback.indexInfo(keys[i], ptrs[i]);}
                    }
                    break;
                default :
                    throw new BTreeException("Invalid Page Type In query");
                }
            }
        }

        protected void scanRaw(IndexQuery query, BTreeCallback callback) throws TerminatedException {
            for (int i = 0; i < nKeys; i++) {
                if (query == null || query.testValue(keys[i]))
                    {callback.indexInfo(keys[i], ptrs[i]);}
            }
        }

        protected void scanNextPage(IndexQuery query, Value keyPrefix, BTreeCallback callback) throws TerminatedException {
            final long next = pageHeader.getNextPage();
            if (next != Page.NO_PAGE) {
                final BTreeNode nextPage = getBTreeNode(next);
                scanSequential(nextPage, query, keyPrefix, callback);
            }
        }

        /**
         * Search for keys matching the given {@link IndexQuery} and
         * remove them from the node. Every match is reported 
         * to the specified {@link BTreeCallback}.
         * 
         * @param query
         * @param callback
         * @throws IOException
         * @throws BTreeException
         * @throws TerminatedException
         */
        private void remove(Txn transaction, IndexQuery query, BTreeCallback callback)
                throws IOException, BTreeException, TerminatedException {
            if (query != null && query.getOperator() != IndexQuery.ANY
                    && query.getOperator() != IndexQuery.TRUNC_LEFT) {
                final Value[] qvals = query.getValues();
                int leftIdx = searchKey(qvals[0]);
                int rightIdx = qvals.length > 1 ? 
                    searchKey(qvals[qvals.length - 1]) : leftIdx;
                boolean pos = query.getOperator() >= 0;
                switch (pageHeader.getStatus()) {
                case BRANCH :
                    leftIdx = leftIdx < 0 ? - (leftIdx + 1) : leftIdx + 1;
                    rightIdx = rightIdx < 0 ? - (rightIdx + 1) : rightIdx + 1;
                    switch (query.getOperator()) {
                    case IndexQuery.BWX :
                    case IndexQuery.NBWX :
                    case IndexQuery.BW :
                    case IndexQuery.NBW :
                    case IndexQuery.IN :
                    case IndexQuery.NIN :
                    case IndexQuery.TRUNC_RIGHT :
                    case IndexQuery.RANGE :
                        for (int i = 0; i < nPtrs; i++) {
                            if ((i >= leftIdx && i <= rightIdx) == pos) {
                                getChildNode(i).remove(transaction, query, callback);
                                if (query.getOperator() == IndexQuery.TRUNC_RIGHT)
                                    {break;}
                            }
                        }
                        break;
                    case IndexQuery.EQ :
                    case IndexQuery.NEQ :
                        for (int i = 0; i < nPtrs; i++) {
                            if (!pos || i == leftIdx)
                                {getChildNode(i).remove(transaction, query, callback);}
                        }
                    case IndexQuery.LT :
                    case IndexQuery.GEQ :
                        for (int i = 0; i < nPtrs; i++){
                            if ((pos && (i <= leftIdx)) || (!pos && (i >= leftIdx)))
                                {getChildNode(i).remove(transaction, query, callback);}
                        }
                        break;
                    case IndexQuery.GT :
                    case IndexQuery.LEQ :
                        for (int i = 0; i < nPtrs; i++) {
                            if ((pos && (i >= leftIdx)) || (!pos && (i <= leftIdx)))
                                {getChildNode(i).remove(transaction, query, callback);}
                        }
                        break;
                    default :
                        // If it's not implemented, we walk the tree
                        for (int i = 0; i < nPtrs; i++) {
                            getChildNode(i).remove(transaction, query, callback);
                        }
                        break;
                    }
                    break;
                case LEAF :
                    try {
                        allowUnload = false;
                        switch (query.getOperator()) {
                        case IndexQuery.EQ :
                            if (leftIdx >= 0) {
                                if (isTransactional && transaction != null) {
                                    final RemoveValueLoggable log =  new RemoveValueLoggable(transaction,
                                        fileId, page.getPageNum(), leftIdx, keys[leftIdx], ptrs[leftIdx]);
                                    writeToLog(log, this);
                                }
                                if (callback != null)
                                    {callback.indexInfo(keys[leftIdx], ptrs[leftIdx]);}
                                removeKey(leftIdx);
                                removePointer(leftIdx);
                                recalculateDataLen();
                            }
                            break;
                        case IndexQuery.NEQ :
                            for (int i = 0; i < nPtrs; i++) {
                                if (i != leftIdx) {
                                    if (isTransactional && transaction != null) {
                                        final RemoveValueLoggable log = new RemoveValueLoggable(transaction,
                                            fileId, page.getPageNum(), i, keys[i], ptrs[i]);
                                        writeToLog(log, this);
                                    }
                                    if (callback != null)
                                        {callback.indexInfo(keys[i], ptrs[i]);}
                                    removeKey(i);
                                    removePointer(i);
                                    recalculateDataLen();
                                }
                            }
                            break;
                        case IndexQuery.BWX :
                        case IndexQuery.NBWX :
                        case IndexQuery.BW :
                        case IndexQuery.NBW :
                        case IndexQuery.RANGE :
                            if (leftIdx < 0)
                                {leftIdx = - (leftIdx + 1);}
                            if (rightIdx < 0)
                                {rightIdx = - (rightIdx + 1);}
                            for (int i = 0; i < nPtrs; i++) {
                                if ((pos && (i >= leftIdx && i <= rightIdx))
                                        || (!pos && (i <= leftIdx || i >= rightIdx))) {
                                    if (query.testValue(keys[i])) {
                                        if (isTransactional && transaction != null) {
                                            final RemoveValueLoggable log = new RemoveValueLoggable(transaction,
                                                fileId, page.getPageNum(), i, keys[i], ptrs[i]);
                                            writeToLog(log, this);
                                        }
                                        if (callback != null)
                                            {callback.indexInfo(keys[i], ptrs[i]);}
                                        removeKey(i);
                                        removePointer(i);
                                        recalculateDataLen();
                                        --i;
                                    }
                                }
                            }
                            break;
                        case IndexQuery.TRUNC_RIGHT :
                            if (leftIdx < 0)
                                {leftIdx = - (leftIdx + 1);}
                            if (rightIdx < 0)
                                {rightIdx = - (rightIdx + 1);}
                                for (int i = leftIdx; i < rightIdx && i < nPtrs; i++) {
                                    if (query.testValue(keys[i])) {
                                        if (isTransactional && transaction != null) {
                                            final RemoveValueLoggable log = new RemoveValueLoggable(transaction, 
                                                fileId, page.getPageNum(), i, keys[i], ptrs[i]);
                                            writeToLog(log, this);
                                        }
                                    if (callback != null)
                                        {callback.indexInfo(keys[i], ptrs[i]);}
                                    removeKey(i);
                                    removePointer(i);
                                    recalculateDataLen();
                                    --i;
                                }
                            }
                            if (rightIdx >= nPtrs)
                                {removeSequential(transaction, this, query, callback);}
                            break;
                        case IndexQuery.IN :
                        case IndexQuery.NIN :
                            if (leftIdx < 0)
                                {leftIdx = - (leftIdx + 1);}
                            if (rightIdx < 0)
                                {rightIdx = - (rightIdx + 1);}
                            for (int i = 0; i < nPtrs; i++) {
                                if (!pos || (i >= leftIdx && i <= rightIdx)) {
                                    if (query.testValue(keys[i])) {
                                        if (isTransactional && transaction != null) {
                                            final RemoveValueLoggable log = new RemoveValueLoggable(transaction,
                                                fileId, page.getPageNum(), i, keys[i], ptrs[i]);
                                                writeToLog(log, this);
                                        }
                                        if (callback != null)
                                            {callback.indexInfo(keys[i], ptrs[i]);}
                                        removeKey(i);
                                        removePointer(i);
                                        recalculateDataLen();
                                        --i;
                                    }
                                }
                            }
                            break;
                        case IndexQuery.LT :
                        case IndexQuery.GEQ :
                            if (leftIdx < 0)
                                {leftIdx = - (leftIdx + 1);}
                            for (int i = 0; i < nPtrs; i++) {
                                if ((pos && (i <= leftIdx)) || (!pos && (i >= leftIdx))) {
                                    if (query.testValue(keys[i])) {
                                        if (isTransactional && transaction != null) {
                                            final RemoveValueLoggable log = new RemoveValueLoggable(transaction,
                                                fileId, page.getPageNum(), i, keys[i], ptrs[i]);
                                                writeToLog(log, this);
                                        }
                                        if (callback != null)
                                            {callback.indexInfo(keys[i], ptrs[i]);}
                                        removeKey(i);
                                        removePointer(i);
                                        recalculateDataLen();
                                        --i;
                                    }
                                }
                            }
                            break;
                        case IndexQuery.GT :
                        case IndexQuery.LEQ :
                            if (leftIdx < 0)
                                {leftIdx = - (leftIdx + 1);}
                            for (int i = 0; i < nPtrs; i++) {
                                if ((pos && (i >= leftIdx)) || (!pos && (i <= leftIdx))) {
                                    if (query.testValue(keys[i])) {
                                        if (isTransactional && transaction != null) {
                                            final RemoveValueLoggable log = new RemoveValueLoggable(transaction,
                                                fileId, page.getPageNum(), i, keys[i], ptrs[i]);
                                            writeToLog(log, this);
                                        }
                                        if (callback != null)
                                            {callback.indexInfo(keys[i], ptrs[i]);}
                                        removeKey(i);
                                        removePointer(i);
                                        recalculateDataLen();
                                        --i;
                                    } else if (query.getOperator() == IndexQuery.TRUNC_RIGHT)
                                        {break;}
                                }
                            }
                            break;
                        default :
                            // If it's not implemented, it falls right through
                            for (int i = 0; i < nPtrs; i++) {
                                if (query.testValue(keys[i])) {
                                    if (isTransactional && transaction != null) {
                                        final RemoveValueLoggable log = new RemoveValueLoggable(transaction,
                                            fileId, page.getPageNum(), i, keys[i], ptrs[i]);
                                            writeToLog(log, this);
                                    }
                                    if (callback != null)
                                        {callback.indexInfo(keys[i], ptrs[i]);}
                                    removeKey(i);
                                    removePointer(i);
                                    recalculateDataLen();
                                    --i;
                                }
                            }
                            break;
                        }
                    } finally {
                        allowUnload = true;
                    }
                    break;
                default :
                    throw new BTreeException("Invalid Page Type In query");
                }
            } else {
                // No Query - Just Walk The Tree
                switch (pageHeader.getStatus()) {
                case BRANCH :
                    for (int i = 0; i < nPtrs; i++) {
                        if (isTransactional && transaction != null) {
                            final RemoveValueLoggable log = 
                                new RemoveValueLoggable(transaction, 
                                    fileId, page.getPageNum(), i, keys[i], ptrs[i]);
                            writeToLog(log, this);
                        }
                        if (callback != null)
                            {callback.indexInfo(keys[i], ptrs[i]);}
                        removeKey(i);
                        removePointer(i);
                        recalculateDataLen();
                        --i;
                    }
                    break;
                case LEAF :
                    for (int i = 0; i < nKeys; i++) {
                        if (query.getOperator() != IndexQuery.TRUNC_LEFT
                                || query.testValue(keys[i])) {
                            if (isTransactional && transaction != null) {
                                final RemoveValueLoggable log = new RemoveValueLoggable(transaction, 
                                    fileId, page.getPageNum(), i, keys[i], ptrs[i]);
                                writeToLog(log, this);
                            }
                            if (callback != null)
                                {callback.indexInfo(keys[i], ptrs[i]);}
                            removeKey(i);
                            removePointer(i);
                            recalculateDataLen();
                            --i;
                        }
                    }
                    break;
                default :
                    throw new BTreeException("Invalid Page Type In query");
                }
            }
        }

        private void growPrefix() {
            if (nKeys == 0)
                {return;}
            if (nKeys == 1) {
                if (keys[0].getLength() > 0) {
                    final byte[] newPrefix = new byte[prefix.getLength() + keys[0].getLength()];
                    System.arraycopy(prefix.data(), prefix.start(), newPrefix, 0 , prefix.getLength());
                    System.arraycopy(keys[0].data(), keys[0].start(), newPrefix, prefix.getLength(),
                        keys[0].getLength());
                    prefix = new Value(newPrefix);
                    keys[0] = Value.EMPTY_VALUE;
                }
                return;
            }
            int idx;
            int max = Integer.MAX_VALUE;
            final Value first = keys[0];
            for (int i = 1; i < nKeys; i++) {
                final Value value = keys[i];
                idx = Math.abs(value.compareTo(first));
                if (idx < max)
                    {max = idx;}
            }
            final int addChars = max - 1;
            if (addChars > 0) {
                // create new prefix with the additional characters
                final byte[] pdata = new byte[prefix.getLength() + addChars];
                System.arraycopy(prefix.data(), prefix.start(), pdata, 0, prefix.getLength());
                System.arraycopy(keys[0].data(), keys[0].start(), pdata, prefix.getLength(), addChars);
                prefix = new Value(pdata);
                // shrink the keys by addChars characters
                Value key;
                for (int i = 0; i < nKeys; i++) {
                    key = keys[i];
                    keys[i] = new Value(key.data(), key.start() + addChars, key.getLength() - addChars);
                }
                recalculateDataLen();
            }
        }

        private void shrinkPrefix(int newLen) {
            final int diff = prefix.getLength() - newLen;
            Value[] nv = new Value[nKeys];
            for (int i = 0; i < nKeys; i++) {
                final Value value = keys[i];
                final byte[] ndata = new byte[value.getLength() + diff];
                System.arraycopy(prefix.data(), prefix.start() + newLen, ndata, 0, diff);
                System.arraycopy(value.data(), value.start(), ndata, diff, value.getLength());
                nv[i] = new Value(ndata);
            }
            keys = nv;
            prefix = new Value(prefix.data(), prefix.start(), newLen);
        }

        /**
         * Insert a key into the array of keys.
         * @param val
         * @param idx
         */
        private void insertKey(Value val, int idx) {
            if (pageHeader.getStatus() == BRANCH) {
                // in a leaf page we might have to adjust the prefix
                if (nKeys == 0) {
                    prefix = val;
                    val = Value.EMPTY_VALUE;
                } else {
                    final int pfxLen = val.checkPrefix(prefix);
                    if (pfxLen < prefix.getLength()) {
                        shrinkPrefix(pfxLen);
                    }
                    val = new Value(val.data(), val.start() + pfxLen, val.getLength() - pfxLen);
                }
            }
            resizeKeys(nKeys + 1);
            System.arraycopy(keys, idx, keys, idx + 1, nKeys - idx);
            keys[idx] = val;
            pageHeader.setValueCount((short) ++nKeys);
            saved = false;
        }

        /**
         * Remove a key from the array of keys.
         * @param idx
         */
        private void removeKey(int idx) {
            try {
                System.arraycopy(keys, idx + 1, keys, idx, nKeys - idx - 1);
            } catch (ArrayIndexOutOfBoundsException e) {
                System.err.println("keys: " + nKeys + " idx: " + idx);
            }
            pageHeader.setValueCount((short) --nKeys);
            saved = false;
        }

        /**
         * Insert a pointer into the array of pointers.
         * 
         * @param ptr
         * @param idx
         */
        private void insertPointer(long ptr, int idx) {
            resizePtrs(nPtrs + 1);
            System.arraycopy(ptrs, idx, ptrs, idx + 1, nPtrs - idx);
            ptrs[idx] = ptr;
            ++nPtrs;
            saved = false;
        }

        /**
         * Remove a pointer from the array of pointers.
         * @param idx
         */
        private void removePointer(int idx) {
            System.arraycopy(ptrs, idx + 1, ptrs, idx, nPtrs - idx - 1);
            --nPtrs;
            saved = false;
        }

        /**
         * Search for the given key in the keys of this node.
         */
        private int searchKey(Value key) {
            if (pageHeader.getStatus() == BRANCH && prefix != null && prefix.getLength() > 0) {
                // if this is a leaf page, check the common prefix first
                if (key.getLength() < prefix.getLength())
                    {return key.compareTo(prefix) <= 0 ? -1 : -(nKeys + 1);}
                final int pfxCmp = key.comparePrefix(prefix);
                if (pfxCmp < 0)
                    {return -1;}
                if (pfxCmp > 0)
                    {return -(nKeys + 1);}
                key = new Value(key.data(), key.start() + prefix.getLength(), 
                    key.getLength() - prefix.getLength());
            }
            int low = 0;
            int high = nKeys - 1;
            while (low <= high) {
                final int mid = (low + high) >> 1;
                final Value  midVal = keys[mid];
                final int cmp = midVal.compareTo(key);
                if (cmp < 0)
                    {low = mid + 1;}
                else if (cmp > 0)
                    {high = mid - 1;}
                else
                    {return mid;} // key found
            }
            return -(low + 1); // key not found.
        }

        private void resizeKeys(int minCapacity) {
            final int oldCapacity = keys.length;
            if (minCapacity > oldCapacity) {
                final Value oldData[] = keys;
                int newCapacity = (oldCapacity * 3)/2 + 1;
                if (newCapacity < minCapacity)
                    {newCapacity = minCapacity;}
                keys = new Value[newCapacity];
                System.arraycopy(oldData, 0, keys, 0, nKeys);
            }
        }
 
        private void resizePtrs(int minCapacity) {
            final int oldCapacity = ptrs.length;
            if (minCapacity > oldCapacity) {
                final long[] oldData = ptrs;
                int newCapacity = (oldCapacity * 3)/2 + 1;
                if (newCapacity < minCapacity)
                    {newCapacity = minCapacity;}
                ptrs = new long[newCapacity];
                System.arraycopy(oldData, 0, ptrs, 0, nPtrs);
            }
        }
    }

    /**
     * @see org.exist.storage.btree.Paged#createFileHeader(int pageSize)
     */
    public FileHeader createFileHeader(int pageSize) {
        return new BTreeFileHeader(pageSize);
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
        final NumberFormat nf = NumberFormat.getPercentInstance();
        final StringBuilder buf = new StringBuilder();
        buf.append(getFile().getName()).append(" INDEX ");
        buf.append("Buffers occupation : ");
        if (cache.getBuffers() == 0 && cache.getUsedBuffers() == 0)
            {buf.append("N/A");}
        else
            {buf.append(nf.format(cache.getUsedBuffers()/(float)cache.getBuffers()));}
        buf.append(" (" + cache.getUsedBuffers() + " out of " + cache.getBuffers() + ")");
        buf.append(" Cache efficiency : ");
        if (cache.getHits() == 0 && cache.getFails() == 0)
            {buf.append("N/A");}
        else
            {buf.append(nf.format(cache.getHits() / (float)(cache.getFails() + cache.getHits())));}
        LOGSTATS.info(buf.toString());
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
            super(1024, pageSize);
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
         *  Set the root page of the storage tree
         *
         *@param  rootPage The new rootPage value
         */
        public final void setRootPage(long rootPage) {
            this.rootPage = rootPage;
            setDirty(true);
        }

        /**
         *  Get the root page of the storage tree
         *
         *@return The rootPage value
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

        public int getMaxKeySize() {
            return (getWorkSize() / 2) - MIN_SPACE_PER_KEY;
        }
    }

    protected static class BTreePageHeader extends PageHeader {

        private short valueCount = 0;
        private long parentPage = Page.NO_PAGE;

        public BTreePageHeader() {
            super();
        }

        public BTreePageHeader(byte[] data, int offset) throws IOException {
            super(data, offset);
        }

        public int read(byte[] data, int offset) throws IOException {
            offset = super.read(data, offset);
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
                {return (short) (valueCount + 1);}
            else
                {return valueCount;}
        }

    }

}
