/*
 * eXist Open Source Native XML Database Copyright (C) 2001-03 Wolfgang M. Meier
 * wolfgang@exist-db.org http://exist.sourceforge.net
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any
 * later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 * $Id$
 */
package org.exist.storage.store;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.dbxml.core.DBException;
import org.dbxml.core.data.Value;
import org.dbxml.core.filer.BTree;
import org.dbxml.core.filer.BTreeCallback;
import org.dbxml.core.filer.BTreeException;
import org.dbxml.core.indexer.IndexQuery;
import org.exist.storage.BufferStats;
import org.exist.storage.CacheManager;
import org.exist.storage.cache.Cache;
import org.exist.storage.cache.Cacheable;
import org.exist.storage.cache.LRUCache;
import org.exist.storage.io.VariableByteArrayInput;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.util.ByteArray;
import org.exist.util.ByteConversion;
import org.exist.util.FastByteBuffer;
import org.exist.util.FixedByteArray;
import org.exist.util.IndexCallback;
import org.exist.util.Lock;
import org.exist.util.LockException;
import org.exist.util.ReadOnlyException;
import org.exist.util.ReentrantReadWriteLock;
import org.exist.xquery.TerminatedException;

/**
 * Data store for variable size values.
 * 
 * This class maps keys to values of variable size. Keys are stored in the
 * b+-tree. B+-tree values are pointers to the logical storage address of the
 * value in the data section. The pointer consists of the page number and a
 * logical tuple identifier.
 * 
 * If a value is larger than the internal page size (4K), it is split into
 * overflow pages. Appending data to a overflow page is very fast. Only the
 * first and the last data page are loaded.
 * 
 * Data pages are buffered.
 * 
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 */
public class BFile extends BTree {
    /**
     * Log4J Logger for this class
     */
    private static final Logger LOG = Logger.getLogger(BFile.class);

    public final static short FILE_FORMAT_VERSION_ID = 3;

    public final static long DATA_SYNC_PERIOD = 15000;
    
    // minimum free space a page should have to be
    // considered for reusing
    public final static int PAGE_MIN_FREE = 64;

    // page signatures
    public final static byte RECORD = 20;

    public final static byte LOB = 21;

    public final static byte FREE_LIST = 22;

    public final static byte MULTI_PAGE = 23;

    protected BFileHeader fileHeader;

    protected int minFree;

    protected Cache dataCache = null;
    
    protected Lock lock = null;

    public int fixedKeyLen = -1;

    protected int maxValueSize;
    
    public BFile(File file, CacheManager cacheManager, double cacheGrowth, int thresholdBTree, int thresholdData) {
        super(cacheManager, file, thresholdBTree);
        fileHeader = (BFileHeader) getFileHeader();
        dataCache = new LRUCache(64, cacheGrowth, thresholdData);
        dataCache.setFileName(getFile().getName());
        cacheManager.registerCache(dataCache);
        minFree = PAGE_MIN_FREE;
        lock = new ReentrantReadWriteLock(file.getName());
        maxValueSize = fileHeader.getWorkSize() / 2;
    }

    /**
     * @return
     */
    public short getFileVersion() {
        return FILE_FORMAT_VERSION_ID;
    }

    /**
     * Returns the Lock object responsible for this BFile.
     * 
     * @return Lock
     */
    public Lock getLock() {
        return lock;
    }

    protected long getDataSyncPeriod() {
        return DATA_SYNC_PERIOD;
    }
    
    /**
     * Append the given data fragment to the value associated
     * with the key. A new entry is created if the key does not
     * yet exist in the database.
     * 
     * @param key
     * @param value
     * @return
     * @throws ReadOnlyException
     * @throws IOException
     */
    public long append(Value key, ByteArray value) throws ReadOnlyException,
            IOException {
        if (key == null) {
            LOG.debug("key is null");
            return -1;
        }
        if (key.getLength() > fileHeader.getWorkSize()) {
            LOG.warn("Key length exceeds page size! Skipping key ...");
            return -1;
        }
        try {
            try {
                // check if key exists already
                long p = findValue(key);
                if (p == KEY_NOT_FOUND) {
                    // key does not exist:
                    p = storeValue(value);
                    addValue(key, p);
                    return p;
                }
                // key exists: get old data
                final long pnum = StorageAddress.pageFromPointer(p);
                final short tid = StorageAddress.tidFromPointer(p);
                final DataPage page = getDataPage(pnum);
                if (page instanceof OverflowPage)
                    ((OverflowPage) page).append(value);
                else {
                    final int valueLen = value.size();
                    final byte[] data = page.getData();
                    final int offset = page.findValuePosition(tid);
                    if (offset < 0)
                            throw new IOException("tid " + tid
                                    + " not found on page " + pnum);
                    final int l = ByteConversion.byteToInt(data, offset);
                    if (offset + 4 > data.length || offset < 0) {
                        LOG.error("wrong pointer (tid: " + tid
                                + page.getPageInfo() + ") in file "
                                + getFile().getName() + "; offset = " + offset);
                        return -1;
                    }
                    if (offset + 4 + l > data.length) {
                        LOG.error("found invalid data record ("
                                + page.getPageInfo() + "): " + "length="
                                + data.length + "; required="
                                + (offset + 4 + l));
                        return -1;
                    }
                    final byte[] newData = new byte[l + valueLen];
                    System.arraycopy(data, offset + 4, newData, 0, l);
                    value.copyTo(newData, l);
                    p = update(p, page, key, new FixedByteArray(newData, 0, newData.length));
                }
                return p;
            } catch (BTreeException bte) {
                // key does not exist:
                long p = storeValue(value);
                addValue(key, p);
                return p;
            }
        } catch (BTreeException bte) {
            LOG.warn("btree exception while appending value", bte);
        }
        return -1;
    }

    /**
     * Close the BFile.
     * 
     * @throws DBException
     * @return always true
     */
    public boolean close() throws DBException {
        super.close();
        return true;
    }

    /**
     * Check, if key is contained in BFile.
     * 
     * @param key key to look for
     * @return true, if key exists
     */
    public boolean containsKey(Value key) {
        try {
            return findValue(key) != KEY_NOT_FOUND;
        } catch (BTreeException bte) {
        } catch (IOException ioe) {
        }
        return false;
    }

    public boolean create() throws DBException {
        if (super.create((short) fixedKeyLen)) {
            return true;
        } else
            return false;
    }

    private SinglePage createDataPage() {
        try {
            SinglePage page = new SinglePage();
            dataCache.add(page, 2);
            return page;
        } catch (IOException ioe) {
            LOG.warn(ioe);
            return null;
        }
    }

    public FileHeader createFileHeader() {
        return new BFileHeader(PAGE_SIZE);
    }

    public FileHeader createFileHeader(boolean read) throws IOException {
        return new BFileHeader(PAGE_SIZE);
    }

    public FileHeader createFileHeader(long pageCount) {
        return new BFileHeader(PAGE_SIZE);
    }

    public FileHeader createFileHeader(long pageCount, int pageSize) {
        return new BFileHeader(pageSize);
    }

    public PageHeader createPageHeader() {
        return new BFilePageHeader();
    }

    /**
     * Remove all entries matching the given query.
     * 
     * @param query
     * @throws IOException
     * @throws BTreeException
     */
    public void removeAll(IndexQuery query) throws IOException, BTreeException {
        try {
            remove(query, new BTreeCallback() {

                public boolean indexInfo(Value value, long pointer) throws TerminatedException {
                    try {
                        remove(pointer);
                        return true;
                    } catch (ReadOnlyException e) {
                        LOG.debug("file is read-only");
                        return false;
                    }
                }
            });
        } catch (TerminatedException e) {
            // Should never happen during remove

            if (LOG.isDebugEnabled()) {
                LOG.debug("removeAll() - method has been terminated.");
            }
        }
    }

    public ArrayList findEntries(IndexQuery query) throws IOException,
            BTreeException, TerminatedException {
        FindCallback cb = new FindCallback(FindCallback.BOTH);
        query(query, cb);
        return cb.getValues();
    }

    public ArrayList findKeys(IndexQuery query) throws IOException,
            BTreeException, TerminatedException {
        FindCallback cb = new FindCallback(FindCallback.KEYS);
        query(query, cb);
        return cb.getValues();
    }

    public void find(IndexQuery query, IndexCallback callback)
            throws IOException, BTreeException,
            TerminatedException {
        FindCallback cb = new FindCallback(callback);
        query(query, cb);
    }

    public boolean flush() throws DBException {
        dataCache.flush();
        super.flush();
        return true;
    }

    public BufferStats getDataBufferStats() {
        if (dataCache != null)
                return new BufferStats(dataCache.getBuffers(), dataCache
                        .getUsedBuffers(), dataCache.getHits(), dataCache
                        .getFails());
        return null;
    }

    public void printStatistics() {
        super.printStatistics();
        StringBuffer buf = new StringBuffer();
        buf.append(getFile().getName()).append(" DATA ");
        buf.append(dataCache.getBuffers()).append(" / ");
        buf.append(dataCache.getUsedBuffers()).append(" / ");
        buf.append(dataCache.getHits()).append(" / ");
        buf.append(dataCache.getFails());
        LOG.info(buf.toString());
    }

    /**
     * Get the value data associated with the specified key
     * or null if the key could not be found.
     * 
     * @param key
     * @return
     */
    public Value get(Value key) {
        try {
            final long p = findValue(key);
            if (p == KEY_NOT_FOUND) return null;
            final long pnum = StorageAddress.pageFromPointer(p);
            final DataPage page = getDataPage(pnum);
            return get(page, p);
        } catch (BTreeException b) {
            LOG.debug("key " + key + " not found");
        } catch (IOException e) {
            LOG.debug(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Get the value data for the given key as a variable byte
     * encoded input stream.
     * 
     * @param key
     * @return
     * @throws IOException
     */
    public VariableByteInput getAsStream(Value key) throws IOException {
        try {
            final long p = findValue(key);
            if (p == KEY_NOT_FOUND) {
                return null;
            }
            final long pnum = StorageAddress.pageFromPointer(p);
            final DataPage page = getDataPage(pnum);
            switch (page.getPageHeader().getStatus()) {
                case MULTI_PAGE:
                    return ((OverflowPage) page).getDataStream(p);
                default:
                    return getAsStream(page, p);
            }
        } catch (BTreeException b) {
            LOG.debug("key " + key + " not found");
        }
        return null;
    }

    /**
     * Get the value located at the specified address as a
     * variable byte encoded input stream.
     * 
     * @param pointer
     * @return
     * @throws IOException
     */
    public VariableByteInput getAsStream(long pointer) throws IOException {
        final DataPage page = getDataPage(StorageAddress
                .pageFromPointer(pointer));
        switch (page.getPageHeader().getStatus()) {
            case MULTI_PAGE:
                return ((OverflowPage) page).getDataStream(pointer);
            default:
                return getAsStream(page, pointer);
        }
    }

    private VariableByteInput getAsStream(DataPage page, long pointer)
            throws IOException {
        dataCache.add(page.getFirstPage(), 2);
        final short tid = (short) StorageAddress.tidFromPointer(pointer);
        final int offset = page.findValuePosition(tid);
        if (offset < 0) { throw new IOException("no data found at tid " + tid
                + "; page " + page.getPageNum()); }
        final byte[] data = page.getData();
        final int l = ByteConversion.byteToInt(data, offset);
        SimplePageInput input = new SimplePageInput(data, offset + 4, l,
                pointer);
        return input;
    }

    /**
     * Returns the value located at the specified address.
     * 
     * @param p
     * @return
     */
    public Value get(long p) {
        try {
            long pnum = StorageAddress.pageFromPointer(p);
            DataPage page = getDataPage(pnum);
            return get(page, p);
        } catch (BTreeException b) {
        } catch (IOException e) {
            LOG.debug(e);
        }
        return null;
    }

    /**
     * Retrieve value at logical address p from page
     */
    protected Value get(DataPage page, long p) throws BTreeException,
            IOException {
        final short tid = StorageAddress.tidFromPointer(p);
        final int offset = page.findValuePosition(tid);
        final byte[] data = page.getData();
        if (offset > data.length || offset < 0) {
            LOG.error("wrong pointer (tid: " + tid + page.getPageInfo()
                    + ") in file " + getFile().getName() + "; offset = "
                    + offset);
            return null;
        }
        final int l = ByteConversion.byteToInt(data, offset);
        if (l + 6 > data.length) {
            LOG.error(getFile().getName() + " wrong data length in page "
                    + page.getPageNum() + ": expected=" + (l + 6) + "; found="
                    + data.length);
            return null;
        }
        dataCache.add(page.getFirstPage());
        final Value v = new Value(data, offset + 4, l);
        v.setAddress(p);
        return v;
    }

    private DataPage getDataPage(long pos) throws IOException {
    	return getDataPage(pos, true);
    }
    
    private DataPage getDataPage(long pos, boolean initialize) throws IOException {
        DataPage wp = (DataPage) dataCache.get(pos);
        if (wp == null) {
            final Page page = getPage(pos);
            if (page == null) {
                LOG.debug("page " + pos + " not found!");
                return null;
            }
            final byte[] data = page.read();
            if (page.getPageHeader().getStatus() == MULTI_PAGE)
                return new OverflowPage(page, data);
            else
                return new SinglePage(page, data, initialize);
        } else if (wp.getPageHeader().getStatus() == MULTI_PAGE)
            return new OverflowPage(wp);
        else
            return wp;
    }

    public ArrayList getEntries() throws IOException, BTreeException,
    TerminatedException {
        IndexQuery query = new IndexQuery(IndexQuery.ANY, "");
        FindCallback cb = new FindCallback(FindCallback.BOTH);
        query(query, cb);
        return cb.getValues();
    }

    public ArrayList getKeys() throws IOException, BTreeException,
    TerminatedException {
        IndexQuery query = new IndexQuery(IndexQuery.ANY, "");
        FindCallback cb = new FindCallback(FindCallback.KEYS);
        query(query, cb);
        return cb.getValues();
    }

    public ArrayList getValues() 
    throws IOException, BTreeException, TerminatedException {
        IndexQuery query = new IndexQuery(IndexQuery.ANY, "");
        FindCallback cb = new FindCallback(FindCallback.VALUES);
        query(query, cb);
        return cb.getValues();
    }

    public boolean open() throws DBException {
        return super.open(FILE_FORMAT_VERSION_ID);
    }

    /**
     * Put data under given key.
     * 
     * @see {@link BFile#put(Value, ByteArray, boolean)}
     * @param with which the data is updated
     * @param data the data (value) to update
     * @param overwrite overwrite if set to true, value will be overwritten if it already exists
     * @return on success the address of the stored value, else -1
     * @throws ReadOnlyException
     */
    public long put(Value key, byte[] data, boolean overwrite)
            throws ReadOnlyException {
        if (key.getLength() > fileHeader.getWorkSize()) {
            LOG.warn("Key length exceeds page size! Skipping key ...");
            return -1;
        }
        FastByteBuffer buf = new FastByteBuffer(5);
        buf.append(data);
        return put(key, buf, overwrite);
    }

    /**
     * Convinience method for {@link BFile#put(Value, byte[], true)}.
     * 
     * @param key with which the data is updated
     * @param value value to update
     * @return on success the address of the stored value, else -1
     * @throws ReadOnlyException
     */
    public long put(Value key, ByteArray value) throws ReadOnlyException {
        return put(key, value, true);
    }

    /**
     * Put a value under given key. The difference of this
     * method and {@link BFile#append(Value, ByteArray)} is,
     * that the value gets updated and not stored.
     * 
     * @param key with which the data is updated
     * @param value value to update
     * @param overwrite if set to true, value will be overwritten if it already exists
     * @return on success the address of the stored value, else -1
     * @throws ReadOnlyException
     */
    public long put(Value key, ByteArray value, boolean overwrite)
            throws ReadOnlyException {
        if (key == null) {
            LOG.debug("key is null");
            return -1;
        }
        if (key.getLength() > fileHeader.getWorkSize()) {
            LOG.warn("Key length exceeds page size! Skipping key ...");
            return -1;
        }
        try {
            try {
                // check if key exists already
                long p = findValue(key);
                if (p == KEY_NOT_FOUND) {
                    // key does not exist:
                    p = storeValue(value);
                    addValue(key, p);
                    return p;
                }
                // if exists, update value
                if (overwrite) {
                    return update(p, key, value);
                } else
                    return -1;
            } catch (BTreeException bte) {
                // key does not exist:
                long p = storeValue(value);
                addValue(key, p);
                return p;
            } catch (IOException ioe) {
                ioe.printStackTrace();
                return -1;
            }
        } catch (IOException e) {
            e.printStackTrace();
            LOG.warn(e);
            return -1;
        } catch (BTreeException bte) {
            bte.printStackTrace();
            LOG.warn(bte);
            return -1;
        }
    }

    public void remove(Value key) throws ReadOnlyException {
        try {
            long p = findValue(key);
            if (p == KEY_NOT_FOUND) return;
            long pos = StorageAddress.pageFromPointer(p);
            DataPage page = getDataPage(pos);
            remove(page, p);

            removeValue(key);
        } catch (BTreeException bte) {
            LOG.debug(bte);
        } catch (IOException ioe) {
            LOG.debug(ioe);
        }
    }

    public void remove(long p) throws ReadOnlyException {
        try {
            long pos = StorageAddress.pageFromPointer(p);
            DataPage page = getDataPage(pos);
            remove(page, p);
        } catch (BTreeException e) {
            LOG.debug("btree problem", e);
        } catch (IOException e) {
            LOG.debug("io problem", e);
        }
    }

    private void remove(DataPage page, long p) throws BTreeException,
            IOException, ReadOnlyException {
        if (page.getPageHeader().getStatus() == MULTI_PAGE) {
            // overflow page: simply delete the whole page
            page.delete();
            return;
        }
        short tid = StorageAddress.tidFromPointer(p);
        int offset = page.findValuePosition(tid);
        byte[] data = page.getData();
        if (offset > data.length || offset < 0) {
            LOG.error("wrong pointer (tid: " + tid + ", " + page.getPageInfo()
                    + ")");
            return;
        }
        int l = ByteConversion.byteToInt(data, offset);
        int end = offset + 4 + l;
        int len = page.getPageHeader().getDataLength();
        // remove old value
        System.arraycopy(data, end, data, offset - 2, len - end);
        page.getPageHeader().setDirty(true);
        page.getPageHeader().decRecordCount();
        len = len - l - 6;
        page.getPageHeader().setDataLength(len);
        page.setDirty(true);
        // if this page is empty, remove it
        if (len == 0) {
            fileHeader.removeFreeSpace(fileHeader.getFreeSpace(page.getPageNum()));
            dataCache.remove(page);
            page.delete();
        } else {
        	page.removeTID(tid, l + 6);
            // adjust free space data
            final int newFree = fileHeader.getWorkSize() - len;
            if (newFree > minFree) {
                FreeSpace free = fileHeader.getFreeSpace(page.getPageNum());
                if (free == null) {
                    free = new FreeSpace(page.getPageNum(), newFree);
                    fileHeader.addFreeSpace(free);
                } else {
                    free.setFree(newFree);
                }
//                if(page.getPageHeader().getCurrentTID() < 0)
//                    fileHeader.removeFreeSpace(free);
            }
            dataCache.add(page, 2);
        }
    }

    private final void saveFreeSpace(FreeSpace space, DataPage page) {
        int free = fileHeader.getWorkSize()
                - page.getPageHeader().getDataLength();
        space.setFree(free);
        if(free < minFree)
        	fileHeader.removeFreeSpace(space);
    }

    public void setLocation(String location) {
        setFile(new File(location + ".dbx"));
    }

    private long storeValue(ByteArray value) throws IOException,
            ReadOnlyException {
        final int vlen = value.size();
        // does value fit into a single page?
        if (6 + vlen > maxValueSize) {
            OverflowPage page = new OverflowPage();
            byte[] data = new byte[vlen + 6];
            page.getPageHeader().setDataLength(vlen + 6);
            ByteConversion.shortToByte((short) 1, data, 0);
            ByteConversion.intToByte(vlen, data, 2);
            //System.arraycopy(value, 0, data, 6, vlen);
            value.copyTo(data, 6);
            page.setData(data);
            page.setDirty(true);
//            dataCache.add(page);
            return StorageAddress.createPointer((int) page.getPageNum(),
                    (short) 1);
        }
        DataPage page = null;
        short tid = -1;
        FreeSpace free = null;
        int realSpace = 0;
        // check for available tid
        while (tid < 0) {
            free = fileHeader.findFreeSpace(vlen + 6);
            if (free == null) {
                page = createDataPage();
                page.setData(new byte[fileHeader.getWorkSize()]);
                free = new FreeSpace(page.getPageNum(), fileHeader
                        .getWorkSize()
                        - page.getPageHeader().getDataLength());
                fileHeader.addFreeSpace(free);
            } else {
                page = getDataPage(free.getPage());
                // check if this is really a data page
                if (page.getPageHeader().getStatus() != BFile.RECORD) {
                    LOG.warn("page " + page.getPageNum()
                            + " is not a data page; removing it");
                    fileHeader.removeFreeSpace(free);
                    continue;
                }
                // check if the information about free space is really correct
                realSpace = fileHeader.getWorkSize()
                        - page.getPageHeader().getDataLength();
                if (realSpace < 6 + vlen) {
                    // not correct: adjust and continue
                    LOG
                            .warn("wrong data length in list of free pages: adjusting to "
                                    + realSpace);
                    free.setFree(realSpace);
                    continue;
                }
            }
            tid = page.getNextTID();
            if (tid < 0) {
                LOG.info("removing page " + page.getPageNum()
                        + " from free pages");
                fileHeader.removeFreeSpace(free);
            }
        }
        int len = page.getPageHeader().getDataLength();
        final byte[] data = page.getData();

        // save tid
        ByteConversion.shortToByte(tid, data, len);
        len += 2;
        page.setOffset(tid, len);
        // save data length
        ByteConversion.intToByte(vlen, data, len);
        len += 4;
        // save data
        value.copyTo(data, len);
        len += vlen;
        page.getPageHeader().setDataLength(len);
        page.getPageHeader().incRecordCount();
        saveFreeSpace(free, page);
        page.setDirty(true);
        dataCache.add(page);
        // return pointer from pageNum and offset into page
        return StorageAddress.createPointer((int) page.getPageNum(),
                (short) tid);
    }

    /**
     * Update a key/value pair.
     * 
     * @param key
     *                   Description of the Parameter
     * @param value
     *                   Description of the Parameter
     * @return Description of the Return Value
     */
    public long update(Value key, ByteArray value) throws ReadOnlyException {
        try {
            long p = findValue(key);
            if (p == KEY_NOT_FOUND) return -1;
            return update(p, key, value);
        } catch (BTreeException bte) {
            LOG.debug(bte);
        } catch (IOException ioe) {
            LOG.debug(ioe);
        }
        return -1;
    }

    /**
     * Update the key/value pair found at the logical address p.
     * 
     * @param p
     *                   Description of the Parameter
     * @param key
     *                   Description of the Parameter
     * @param value
     *                   Description of the Parameter
     * @return Description of the Return Value
     */
    public long update(long p, Value key, ByteArray value)
            throws ReadOnlyException {
        try {
            return update(p, getDataPage(StorageAddress.pageFromPointer(p)),
                    key, value);
        } catch (BTreeException bte) {
            LOG.debug(bte);
            return -1;
        } catch (IOException ioe) {
            LOG.warn(ioe.getMessage(), ioe);
            return -1;
        }
    }

    /**
     * Update the key/value pair with logical address p and stored in page.
     * 
     * @param p
     *                   Description of the Parameter
     * @param page
     *                   Description of the Parameter
     * @param key
     *                   Description of the Parameter
     * @param value
     *                   Description of the Parameter
     * @exception BTreeException
     *                        Description of the Exception
     * @exception IOException
     *                        Description of the Exception
     */
    protected long update(long p, DataPage page, Value key, ByteArray value)
            throws BTreeException, IOException, ReadOnlyException {
        if (page.getPageHeader().getStatus() == MULTI_PAGE) {
            final int valueLen = value.size();
            // does value fit into a single page?
            if (valueLen + 6 < maxValueSize) {
                // yes: remove the overflow page
                remove(page, p);
                final long np = storeValue(value);
                addValue(key, np);
                return np;
            } else {
//                LOG.debug("Replacing overflow value on page: " + page.getPageNum() + ": " + value.size());
                // this is an overflow page: simply replace the value
                final byte[] data = new byte[valueLen + 6];
                // save tid
                ByteConversion.shortToByte((short) 1, data, 0);

                // save length
                ByteConversion.intToByte(valueLen, data, 2);
                // save data
                value.copyTo(data, 6);

                page.setData(data);
                return p;
            }
        } else {
            remove(page, p);
            final long np = storeValue(value);
            addValue(key, np);
            return np;
        }
    }

    public void debugFreeList() {
    	fileHeader.debugFreeList();
    }
    
    /**
     * The file header. Most important, the file header stores the list of
     * data pages containing unused space.
     * 
     * @author wolf
     */
    private final class BFileHeader extends BTreeFileHeader {

        private FreeList freeList = new FreeList();
        
        public final static int MAX_FREE_LIST_LEN = 128;

        public BFileHeader(int pageSize) {
            super();
        }

        public void addFreeSpace(FreeSpace freeSpace) {
            freeList.add(freeSpace);
            setDirty(true);
        }

        public FreeSpace findFreeSpace(int needed) {
        	return freeList.find(needed);
        }

        public FreeSpace getFreeSpace(long page) {
            return freeList.retrieve(page);
        }
        
        public void removeFreeSpace(FreeSpace space) {
            if (space == null) return;
            freeList.remove(space);
            setDirty(true);
        }
        
        public void debugFreeList() {
        	LOG.debug(getFile().getName() + ": " + freeList.toString());
        }
        
        public int read(byte[] buf) throws IOException {
            int offset = super.read(buf);
            return freeList.read(buf, offset);
        }

        public int write(byte[] buf) throws IOException {
            int offset = super.write(buf);
            return freeList.write(buf, offset);
        }
    }

    private final class BFilePageHeader extends BTreePageHeader {

        private int dataLen = 0;

        private long lastInChain = -1L;

        private long nextInChain = -1L;

        // tuple identifier: used to identify distinct
        // values inside a page
        private short nextTID = -1;

        private short records = 0;

        public BFilePageHeader() {
            super();
        }

        public BFilePageHeader(byte[] data, int offset) throws IOException {
            super(data, offset);
        }

        public void decRecordCount() {
            records--;
        }

        public int getDataLength() {
            return dataLen;
        }

        public long getLastInChain() {
            return lastInChain;
        }

        public long getNextInChain() {
            return nextInChain;
        }

        public short getNextTID() {
            if (nextTID == Short.MAX_VALUE) {
                LOG.warn("tid limit reached");
                return -1;
            }
            return ++nextTID;
        }

        public short getCurrentTID() {
            if(nextTID == Short.MAX_VALUE) {
                return -1;
            }
            return nextTID;
        }
        
        public short getRecordCount() {
            return records;
        }

        public void incRecordCount() {
            records++;
        }

        public int read(byte[] data, int offset) throws IOException {
            offset = super.read(data, offset);
            records = ByteConversion.byteToShort(data, offset);
            offset += 2;
            dataLen = ByteConversion.byteToInt(data, offset);
            offset += 4;
            nextTID = ByteConversion.byteToShort(data, offset);
            offset += 2;
            nextInChain = ByteConversion.byteToLong(data, offset);
            offset += 8;
            lastInChain = ByteConversion.byteToLong(data, offset);
            return offset + 8;
        }

        public void setDataLength(int len) {
            dataLen = len;
        }

        public void setLastInChain(long p) {
            lastInChain = p;
        }

        public void setNextInChain(long b) {
            nextInChain = b;
        }

        public void setRecordCount(short recs) {
            records = recs;
        }

        public void setTID(short tid) {
            this.nextTID = tid;
        }

        public int write(byte[] data, int offset) throws IOException {
            offset = super.write(data, offset);
            ByteConversion.shortToByte(records, data, offset);
            offset += 2;
            ByteConversion.intToByte(dataLen, data, offset);
            offset += 4;
            ByteConversion.shortToByte(nextTID, data, offset);
            offset += 2;
            ByteConversion.longToByte(nextInChain, data, offset);
            offset += 8;
            ByteConversion.longToByte(lastInChain, data, offset);
            return offset + 8;
        }
    }

    private abstract class DataPage implements Comparable, Cacheable {

        int refCount = 0;

        int timestamp = 0;

        boolean saved = true;

        public abstract void delete() throws IOException;

        public abstract byte[] getData() throws IOException;

        public abstract BFilePageHeader getPageHeader();

        public abstract String getPageInfo();

        public abstract long getPageNum();

        public abstract int findValuePosition(short tid)
        throws IOException;
        
        public abstract short getNextTID();
        
        public abstract void removeTID(short tid, int length) throws IOException;
        
        public abstract void setOffset(short tid, int offset);
        
        public long getKey() {
            return getPageNum();
        }

        public int getReferenceCount() {
            return refCount;
        }

        public int incReferenceCount() {
            if (refCount < Cacheable.MAX_REF) ++refCount;
            return refCount;
        }

        public int decReferenceCount() {
            return refCount > 0 ? --refCount : 0;
        }

        public void setReferenceCount(int count) {
            refCount = count;
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.exist.storage.cache.Cacheable#setTimestamp(int)
         */
        public void setTimestamp(int timestamp) {
            this.timestamp = timestamp;
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.exist.storage.cache.Cacheable#getTimestamp()
         */
        public int getTimestamp() {
            return timestamp;
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.exist.storage.cache.Cacheable#release()
         */
        public boolean sync() {
            if (isDirty()) {
                try {
                    write();
                    return true;
                } catch (IOException e) {
                    LOG.error("IO exception occurred while saving page "
                            + getPageNum());
                }
            }
            return false;
        }

        public boolean isDirty() {
            return !saved;
        }

        public boolean allowUnload() {
            return true;
        }

        public abstract void setData(byte[] buf);

        public abstract SinglePage getFirstPage();

        public void setDirty(boolean dirty) {
            saved = !dirty;
            getPageHeader().setDirty(dirty);
        }

        public abstract void write() throws IOException;

        public int compareTo(Object other) {
            if (getPageNum() == ((DataPage) other).getPageNum())
                return 0;
            else if (getPageNum() > ((DataPage) other).getPageNum())
                return 1;
            else
                return -1;
        }
    }

    private final class FilterCallback implements BTreeCallback {

        BFileCallback callback;

        public FilterCallback(BFileCallback callback) {
            this.callback = callback;
        }

        public boolean indexInfo(Value value, long pointer) throws TerminatedException{
            try {
                long pos;
                short tid;
                DataPage page;
                int offset;
                int l;
                Value v;
                pos = StorageAddress.pageFromPointer(pointer);
                tid = StorageAddress.tidFromPointer(pointer);
                page = getDataPage(pos);
                offset = page.findValuePosition(tid);
                byte[] data = page.getData();
                l = ByteConversion.byteToInt(data, offset);
                v = new Value(data, offset + 4, l);
                callback.info(value, v);
                return true;
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);
                return true;
            }
        }
    }

    private final class FindCallback implements BTreeCallback {

        public final static int BOTH = 2;

        public final static int KEYS = 1;

        public final static int VALUES = 0;

        private int mode = VALUES;

        private IndexCallback callback = null;

        private ArrayList values = null;

        public FindCallback(int mode) {
            this.mode = mode;
            values = new ArrayList();
        }

        public FindCallback(IndexCallback callback) {
            this.mode = BOTH;
            this.callback = callback;
        }

        public ArrayList getValues() {
            return values;
        }

        public boolean indexInfo(Value value, long pointer) throws TerminatedException {
            long pos;
            short tid;
            DataPage page;
            int offset;
            int l;
            Value v;
            byte[] data;
            try {
                switch (mode) {
                    case VALUES:
                        pos = StorageAddress.pageFromPointer(pointer);
                        tid = StorageAddress.tidFromPointer(pointer);
                        page = getDataPage(pos);
                        dataCache.add(page.getFirstPage());
                        offset = page.findValuePosition(tid);
                        data = page.getData();
                        l = ByteConversion.byteToInt(data, offset);
                        v = new Value(data, offset + 4, l);
                        v.setAddress(pointer);
                        if (callback == null)
                            values.add(v);
                        else
                            return callback.indexInfo(value, v);
                        return true;
                    case KEYS:
                        value.setAddress(pointer);
                        if (callback == null)
                            values.add(value);
                        else
                            return callback.indexInfo(value, null);
                        return true;
                    case BOTH:
                        Value[] entry = new Value[2];
                        entry[0] = value;
                        pos = StorageAddress.pageFromPointer(pointer);
                        tid = StorageAddress.tidFromPointer(pointer);
                        page = getDataPage(pos);
                        if (page.getPageHeader().getStatus() == MULTI_PAGE) {
                            data = page.getData();
                            
                        }
                        dataCache.add(page.getFirstPage());
                        offset = page.findValuePosition(tid);
                        data = page.getData();
                        l = ByteConversion.byteToInt(data, offset);
                        v = new Value(data, offset + 4, l);
                        v.setAddress(pointer);
                        entry[1] = v;
                        if (callback == null)
                            values.add(entry);
                        else
                            return callback.indexInfo(value, v);
                        return true;
                }
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);
            }
            return false;
        }
    }

    private final class OverflowPage extends DataPage {
        
        byte[] data = null;

        SinglePage firstPage;

        public OverflowPage() throws IOException {
            firstPage = new SinglePage(false);
            final BFilePageHeader ph = firstPage.getPageHeader();
            ph.setStatus(MULTI_PAGE);
            ph.setNextInChain(0L);
            ph.setLastInChain(0L);
            ph.setDataLength(0);
            firstPage.setData(new byte[fileHeader.getWorkSize()]);
            dataCache.add(firstPage, 3);
        }

        public OverflowPage(DataPage page) {
        	firstPage = (SinglePage) page;
        }

        public OverflowPage(Page p, byte[] data) throws IOException {
            firstPage = new SinglePage(p, data, false);
            firstPage.getPageHeader().setStatus(MULTI_PAGE);
        }

        /**
         * Append a new chunk of data to the page
         * 
         * @param chunk
         *                   chunk of data to append
         */
        public void append(ByteArray chunk) throws IOException {
            SinglePage nextPage;
            BFilePageHeader ph = firstPage.getPageHeader();
            final int newLen = ph.getDataLength() + chunk.size();
            // get the last page and fill it
            long next = ph.getLastInChain();
            DataPage page;
            if (next > 0)
                page = (DataPage) getDataPage(next, false);
            else
                page = firstPage;
            ph = page.getPageHeader();
            int chunkSize = fileHeader.getWorkSize() - ph.getDataLength();
            final int chunkLen = chunk.size();
            if (chunkLen < chunkSize) chunkSize = chunkLen;
            // fill last page
            chunk.copyTo(0, page.getData(), ph.getDataLength(), chunkSize);
//            LOG.debug("Appending to " + firstPage.getPageNum() + "; " + firstPage.ph.getDataLength() + ": " + chunkLen +
//                    "; chunk: " + chunkSize);
            if (page != firstPage)
                ph.setDataLength(ph.getDataLength() + chunkSize);
            page.setDirty(true);
            // write the remaining chunks to new pages
            int remaining = chunkLen - chunkSize;
            int current = chunkSize;
            chunkSize = fileHeader.getWorkSize();
            if (remaining > 0) {
                // walk through chain of pages
                while (remaining > 0) {
                    // add a new page to the chain
                    nextPage = (SinglePage) createDataPage();
//                    LOG.debug("nextPage: " + nextPage.getPageNum() + "; remaining = " + remaining);
                    nextPage.setData(new byte[fileHeader.getWorkSize()]);
                    page.getPageHeader().setNextInChain(nextPage.getPageNum());
                    page.setDirty(true);

                    dataCache.add(page);
                    page = nextPage;
                    if (remaining < chunkSize) chunkSize = remaining;
                    // copy next chunk of data to the page
                    chunk.copyTo(current, page.getData(), 0, chunkSize);
                    page.setDirty(true);
                    if (page != firstPage)
                            page.getPageHeader().setDataLength(chunkSize);
                    remaining = remaining - chunkSize;
                    current += chunkSize;
                }
            }
            ph = firstPage.getPageHeader();
            if (page != firstPage) {
                // add link to last page
                dataCache.add(page);
                ph.setLastInChain(page.getPageNum());
            } else
                ph.setLastInChain(0L);
            // adjust length field in first page
            ph.setDataLength(newLen);
            ByteConversion.intToByte(newLen - 6, firstPage.getData(), 2);
//            LOG.debug("First page " + firstPage.getPageNum() + " len: " + (newLen - 6) + "; ph: " + firstPage.ph.getDataLength());
            firstPage.setDirty(true);
            // keep the first page in cache
            dataCache.add(firstPage, 2);
        }

        public void delete() throws IOException {
            long next = firstPage.getPageNum();
            SinglePage page = firstPage;
            do {
                next = page.getPageHeader().getNextInChain();
                page.getPageHeader().setNextInChain(-1L);
                page.setDirty(true);
                dataCache.remove(page);
                page.delete();
                if (next > 0) page = (SinglePage) getDataPage(next, false);
            } while (next > 0);
        }

        public VariableByteInput getDataStream(long pointer) {
            MultiPageInput input = new MultiPageInput(firstPage, pointer);
            return input;
        }

        public byte[] getData() throws IOException {
            if (data != null) return data;
            SinglePage page = firstPage;
            Value v;
            long pnum;
            long next;
            byte[] temp;
            int len;
            ByteArrayOutputStream os = new ByteArrayOutputStream(page
                    .getPageHeader().getDataLength());
            do {
                temp = page.getData();
                next = page.getPageHeader().getNextInChain();
                len = next > 0 ? fileHeader.getWorkSize() : page
                        .getPageHeader().getDataLength();
                os.write(temp, 0, len);

                if (next > 0) {
                    page = (SinglePage) getDataPage(next, false);
                    dataCache.add(page);
                }
            } while (next > 0);
            data = os.toByteArray();
//            LOG.debug("Read data from page " + firstPage.getPageNum() + "; len = " + data.length);
            if (data.length != firstPage.getPageHeader().getDataLength()) {
                LOG.warn(getFile().getName() + " read=" + data.length
                        + "; expected="
                        + firstPage.getPageHeader().getDataLength());
            }
            return data;
        }

        public SinglePage getFirstPage() {
            return firstPage;
        }

        public BFilePageHeader getPageHeader() {
            return firstPage.getPageHeader();
        }

        public String getPageInfo() {
            return "MULTI_PAGE: " + firstPage.getPageInfo();
        }

        public long getPageNum() {
            return firstPage.getPageNum();
        }

        public void setData(byte[] data) {
            this.data = data;
            try {
                write();
            } catch (IOException e) {
                LOG.warn(e);
            }
        }

        public void write() throws IOException {
            if (data == null) return;
            int chunkSize = fileHeader.getWorkSize();
            int remaining = data.length;
            int current = 0;
            long next = 0L;
            SinglePage page = firstPage;
            SinglePage nextPage;
            // walk through chain of pages
            while (remaining > 0) {
                if (remaining < chunkSize) chunkSize = remaining;
                page.clear();
                // copy next chunk of data to the page
                System.arraycopy(data, current, page.getData(), 0, chunkSize);
                if (page != firstPage)
                    page.getPageHeader().setDataLength(chunkSize);
                page.setDirty(true);
                remaining -= chunkSize;
                current += chunkSize;
                next = page.getPageHeader().getNextInChain();
                if (remaining > 0) {
                    if (next > 0) {
                        // load next page in chain
                        nextPage = (SinglePage) getDataPage(next, false);
                    } else {
                        // add a new page to the chain
                        nextPage = (SinglePage) createDataPage();
//                        LOG.debug("New page: " + nextPage.getPageNum() + "; write: " + remaining);
                        nextPage.setData(new byte[fileHeader.getWorkSize()]);
                        nextPage.getPageHeader().setNextInChain(0L);
                        page.getPageHeader().setNextInChain(
                                nextPage.getPageNum());
                    }
                    dataCache.add(page);
                    page = nextPage;
                } else {
                    page.getPageHeader().setNextInChain(0L);
                    if (page != firstPage) {
                        page.setDirty(true);
                        dataCache.add(page);
                        firstPage.getPageHeader().setLastInChain(
                                page.getPageNum());
                    } else
                        firstPage.getPageHeader().setLastInChain(0L);
                    // adjust length field in first page
//                    ByteConversion.intToByte(data.length - 6, firstPage.getData(), 2);
                }
            }
            if (next > 0) {
                // there are more pages in the chain:
                // remove them
                while (next > 0) {
                    nextPage = (SinglePage) getDataPage(next, false);
                    next = nextPage.getPageHeader().getNextInChain();
                    nextPage.setDirty(true);
                    nextPage.delete();
//                    LOG.debug("Removing " + nextPage.getPageNum());
                    dataCache.remove(nextPage);
                }
            }
            firstPage.getPageHeader().setDataLength(data.length);
            firstPage.setDirty(true);
            dataCache.add(firstPage, 3);
//            LOG.debug(firstPage.getPageNum() + " data length: " + firstPage.ph.getDataLength());
        }
        
        /* (non-Javadoc)
		 * @see org.exist.storage.store.BFile.DataPage#findValuePosition(short)
		 */
		public int findValuePosition(short tid) throws IOException {
			return 2;
		}

		/* (non-Javadoc)
		 * @see org.exist.storage.store.BFile.DataPage#getNextTID()
		 */
		public short getNextTID() {
			return 1;
		}

		/* (non-Javadoc)
		 * @see org.exist.storage.store.BFile.DataPage#removeTID(short)
		 */
		public void removeTID(short tid, int length) {
		}

		/* (non-Javadoc)
		 * @see org.exist.storage.store.BFile.DataPage#setOffset(short, int)
		 */
		public void setOffset(short tid, int offset) {
		}
    }

    public interface PageInputStream {
        public long getAddress();
    }

    /**
     * Variable byte input stream to read data from a single page.
     * 
     * @author wolf
     */
    private final class SimplePageInput extends VariableByteArrayInput
            implements PageInputStream {

        private long address = 0L;

        public SimplePageInput() {
        }

        public SimplePageInput(byte[] data, int start, int len, long address) {
            super(data, start, len);
            this.address = address;
        }

        public long getAddress() {
            return address;
        }
    }

    /**
     * Variable byte input stream to read a multi-page sequences.
     * 
     * @author wolf
     */
    private final class MultiPageInput implements VariableByteInput,
            PageInputStream {

        private SinglePage nextPage;

        private int pageLen;

        private int offset = 0;

        private long address = 0L;

        public MultiPageInput() {
        }

        public MultiPageInput(SinglePage first, long address) {
            nextPage = first;
            offset = 6;
            pageLen = first.ph.getDataLength();
            if (pageLen > fileHeader.getWorkSize())
            	pageLen = fileHeader.getWorkSize();
            dataCache.add(first, 3);
            this.address = address;
        }

        public long getAddress() {
            return address;
        }
        
        /*
         * (non-Javadoc)
         * 
         * @see java.io.InputStream#read()
         */
        public final int read() throws IOException {
            if (offset == pageLen) {
                advance();
            }
            return (int) (nextPage.data[offset++] & 0xFF);
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.exist.util.VariableInputStream#readByte()
         */
        public final byte readByte() throws IOException {
            if (offset == pageLen) advance();
            return (nextPage.data[offset++]);
        }

        public final short readShort() throws IOException {
            if (offset == pageLen) advance();
            byte b = nextPage.data[offset++];
            short i = (short) (b & 0177);
            for (int shift = 7; (b & 0200) != 0; shift += 7) {
                if (offset == pageLen) advance();
                b = nextPage.data[offset++];
                i |= (b & 0177) << shift;
            }
            return i;
        }

        public final int readInt() throws IOException {
            if (offset == pageLen) advance();
            byte b = nextPage.data[offset++];
            int i = b & 0177;
            for (int shift = 7; (b & 0200) != 0; shift += 7) {
                if (offset == pageLen) advance();
                b = nextPage.data[offset++];
                i |= (b & 0177) << shift;
            }
            return i;
        }

        public final long readLong() throws IOException {
            if (offset == pageLen) advance();
            byte b = nextPage.data[offset++];
            long i = b & 0177;
            for (int shift = 7; (b & 0200) != 0; shift += 7) {
                if (offset == pageLen) advance();
                b = nextPage.data[offset++];
                i |= (b & 0177L) << shift;
            }
            return i;
        }

        public final void skip(int count) throws IOException {
            for (int i = 0; i < count; i++) {
                do {
                    if (offset == pageLen) advance();
                } while ((nextPage.data[offset++] & 0200) > 0);
            }
        }

        public final void skipBytes(long count) throws IOException {
            for(long i = 0; i < count; i++) {
                if(offset++ == pageLen) advance();
            }
        }
        
        private final void advance() throws IOException {
            long next = nextPage.getPageHeader().getNextInChain();
            if (next < 1) {
                pageLen = -1;
                offset = 0;
                throw new EOFException();
            } else {
                try {
                    lock.acquire(Lock.READ_LOCK);
                    nextPage = (SinglePage) getDataPage(next, false);
                    pageLen = nextPage.ph.getDataLength();
                    offset = 0;
                    dataCache.add(nextPage);
                } catch (LockException e) {
                    throw new IOException("failed to acquire a read lock on "
                            + getFile().getName());
                } finally {
                    lock.release();
                }
            }
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.io.InputStream#available()
         */
        public final int available() throws IOException {
            return pageLen < 0 ? 0 : pageLen;
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.exist.storage.io.VariableByteInput#read(byte[])
         */
        public final int read(byte[] data) throws IOException {
            return read(data, 0, data.length);
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.io.InputStream#read(byte[], int, int)
         */
        public final int read(byte[] b, int off, int len) throws IOException {
            if (pageLen < 0) return -1;
            for (int i = 0; i < len; i++) {
                if (offset == pageLen) {
                    final long next = nextPage.getPageHeader().getNextInChain();
                    if (next < 1) {
                        pageLen = -1;
                        offset = 0;
                        return i;
                    }
                    nextPage = (SinglePage) getDataPage(next, false);
                    pageLen = nextPage.ph.getDataLength();
                    offset = 0;
                    dataCache.add(nextPage);
                }
                b[off + i] = nextPage.data[offset++];
            }
            return len;
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.exist.storage.io.VariableByteInput#readUTF()
         */
        public final String readUTF() throws IOException, EOFException {
            int len = readInt();
            byte data[] = new byte[len];
            read(data);
            String s;
            try {
                s = new String(data, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                s = new String(data);
            }
            return s;
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.exist.storage.io.VariableByteInput#copyTo(org.exist.storage.io.VariableByteOutputStream)
         */
        public final void copyTo(VariableByteOutputStream os)
                throws IOException {
            byte more;
            do {
                if (offset == pageLen) advance();
                more = nextPage.data[offset++];
                os.writeByte(more);
                more &= 0200;
            } while (more > 0);
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.exist.storage.io.VariableByteInput#copyTo(org.exist.storage.io.VariableByteOutputStream,
         *           int)
         */
        public final void copyTo(VariableByteOutputStream os, int count)
                throws IOException {
            byte more;
            for (int i = 0; i < count; i++) {
                do {
                    if (offset == pageLen) advance();
                    more = nextPage.data[offset++];
                    os.writeByte(more);
                } while ((more & 0x200) > 0);
            }
        }
    }

    /**
     * Represents a single data page (as opposed to a overflow page).
     * 
     * @author Wolfgang Meier <wolfgang@exist-db.org>
     */
    private final class SinglePage extends DataPage {

    	// the raw working data of this page (without page header)
        byte[] data = null;
        
        // the low-level page
        Page page;
        
        // the page header
        BFilePageHeader ph;
        
        // table mapping record ids (tids) to offsets
        short[] offsets = null;

        public SinglePage() throws IOException {
            this(true);
        }

        public SinglePage(boolean compress) throws IOException {
            page = getFreePage();
            ph = (BFilePageHeader) page.getPageHeader();
            ph.setStatus(RECORD);
            ph.setDirty(true);
            ph.setDataLength(0);
            //ph.setNextChunk( -1 );
            data = new byte[fileHeader.getWorkSize()];
            offsets = new short[32];
            ph.nextTID = 32;
            Arrays.fill(offsets, (short)-1);
        }

        public SinglePage(Page p, byte[] data, boolean initialize) throws IOException {
            if (p == null) throw new IOException("illegal page");
            if (!(p.getPageHeader().getStatus() == RECORD || p.getPageHeader()
                    .getStatus() == MULTI_PAGE)) {
                LOG.debug("not a data-page: " + p.getPageInfo());
                throw new IOException("not a data-page: "
                        + p.getPageHeader().getStatus());
            }
            this.data = data;
            page = p;
            ph = (BFilePageHeader) page.getPageHeader();
            if(initialize) {
            	offsets = new short[ph.nextTID];
            	readOffsets();
            }
        }

        public final int findValuePosition(short tid)
        throws IOException {
        	return offsets[tid];
//		    final int dlen = ph.getDataLength();
//		    for(int pos = 0; pos < dlen; ) {
//		        if (ByteConversion.byteToShort(data, pos) == tid) return pos + 2;
//		        pos += ByteConversion.byteToInt(data, pos + 2) + 6;
//		    }
//		    LOG.warn("tid " + tid + " not found. " + getPageInfo());
//		    return -1;
        }
        
        private void readOffsets() throws IOException {
//        	if(offsets.length > 256)
//        		LOG.warn("TID size: " + ph.nextTID);
        	Arrays.fill(offsets, (short)-1);
        	final int dlen = ph.getDataLength();
		    for(short pos = 0; pos < dlen; ) {
		    	short tid = ByteConversion.byteToShort(data, pos);
		    	if(tid >= offsets.length) {
		    		LOG.error("Problematic tid found: " + tid + "; trying to recover ...");
		    		short[] t = new short[tid + 1];
		    		Arrays.fill(t, (short)-1);
		        	System.arraycopy(offsets, 0, t, 0, offsets.length);
		        	offsets = t;
		        	ph.nextTID = (short)(tid + 1);
		    	}
		    	offsets[tid] = (short)(pos + 2);
		    	pos += ByteConversion.byteToInt(data, pos + 2) + 6;
		    }
        }
        
        public short getNextTID() {
        	for(short i = 0; i < offsets.length; i++) {
        		if(offsets[i] == -1) {
        			return i;
        		}
        	}
        	short tid = (short)offsets.length;
        	short next = (short)(ph.nextTID * 2);
        	if(next < 0 || next < ph.nextTID) {
        		return -1;
        	}
        	short[] t = new short[next];
        	Arrays.fill(t, (short)-1);
        	System.arraycopy(offsets, 0, t, 0, offsets.length);
        	offsets = t;
        	ph.nextTID = next;
//        	if(ph.nextTID > 256)
//        		LOG.warn("TID size: " + ph.nextTID);
        	return tid;
        }

        public void clear() {
            Arrays.fill(data, (byte) 0);
        }
        
        private String printContents() {
        	StringBuffer buf = new StringBuffer();
        	for(short i = 0; i < offsets.length; i++) {
        		buf.append('[').append(i).append(", ").append(offsets[i]).append(']');
        	}
        	return buf.toString();
        }
        
        public void setOffset(short tid, int offset) {
        	offsets[tid] = (short)offset;
        }
        
        public void removeTID(short tid, int length) throws IOException {
        	int offset = offsets[tid] - 2;
        	offsets[tid] = -1;
        	for(short i = 0; i < offsets.length; i++) {
        		if(offsets[i] > offset)
        			offsets[i] -= length;
        	}
//        	readOffsets(start);
        }
        
        public void delete() throws IOException {
            // reset page header fields
            ph.setDataLength(0);
            ph.setNextInChain(-1L);
            ph.setLastInChain(-1L);
            ph.setTID((short) -1);
            ph.setRecordCount((short) 0);
            setReferenceCount(0);
            ph.setDirty(true);
            unlinkPages(page);
        }

        public SinglePage getFirstPage() {
            return this;
        }

        public byte[] getData() {
            return data;
        }

        public BFilePageHeader getPageHeader() {
            return ph;
        }

        public String getPageInfo() {
            return page.getPageInfo();
        }

        public long getPageNum() {
            return page.getPageNum();
        }

        public void setData(byte[] buf) {
            data = buf;
        }

        public void write() throws IOException {
            //LOG.debug(getFile().getName() + " writing page " + getPageNum());
            writeValue(page, new Value(data));
            setDirty(false);
        }
    }
}