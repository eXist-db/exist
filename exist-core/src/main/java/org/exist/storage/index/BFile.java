/*
 * eXist Open Source Native XML Database Copyright (C) 2001-06 Wolfgang M. Meier
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
package org.exist.storage.index;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.storage.BrokerPool;
import org.exist.storage.BufferStats;
import org.exist.storage.DefaultCacheManager;
import org.exist.storage.NativeBroker;
import org.exist.storage.StorageAddress;
import org.exist.storage.btree.BTree;
import org.exist.storage.btree.BTreeCallback;
import org.exist.storage.btree.BTreeException;
import org.exist.storage.btree.DBException;
import org.exist.storage.btree.IndexQuery;
import org.exist.storage.btree.Value;
import org.exist.storage.cache.Cache;
import org.exist.storage.cache.Cacheable;
import org.exist.storage.cache.LRUCache;
import org.exist.storage.io.VariableByteArrayInput;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.journal.JournalException;
import org.exist.storage.journal.LogEntryTypes;
import org.exist.storage.journal.Loggable;
import org.exist.storage.journal.Lsn;
import org.exist.storage.lock.LockManager;
import org.exist.storage.lock.ManagedLock;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
import org.exist.util.io.FastByteArrayOutputStream;
import org.exist.util.sanity.SanityCheck;
import org.exist.xquery.Constants;
import org.exist.xquery.TerminatedException;

import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

import static java.nio.charset.StandardCharsets.UTF_8;


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
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class BFile extends BTree {

    protected final static Logger LOGSTATS = LogManager.getLogger( NativeBroker.EXIST_STATISTICS_LOGGER );
    
    public final static long UNKNOWN_ADDRESS = -1;

    public final static long DATA_SYNC_PERIOD = 15000;
    
    // minimum free space a page should have to be
    // considered for reusing
    public final static int PAGE_MIN_FREE = 64;

    // page signatures
    public final static byte RECORD = 20;

    public final static byte LOB = 21;

    public final static byte FREE_LIST = 22;

    public final static byte MULTI_PAGE = 23;
    
    public static final int LENGTH_RECORDS_COUNT = 2; //sizeof short
    public static final int LENGTH_NEXT_TID = 2; //sizeof short

    /*
     * Byte ids for the records written to the log file.
     */
    public final static byte LOG_CREATE_PAGE = 0x30;
    public final static byte LOG_STORE_VALUE = 0x31;
    public final static byte LOG_REMOVE_VALUE = 0x32;
    public final static byte LOG_REMOVE_PAGE = 0x33;
    public final static byte LOG_OVERFLOW_APPEND = 0x34;
    public final static byte LOG_OVERFLOW_STORE = 0x35;
    public final static byte LOG_OVERFLOW_CREATE = 0x36;
    public final static byte LOG_OVERFLOW_MODIFIED = 0x37;
    public final static byte LOG_OVERFLOW_CREATE_PAGE = 0x38;
    public final static byte LOG_OVERFLOW_REMOVE = 0x39;

    static {
        // register log entry types for this db file
        LogEntryTypes.addEntryType(LOG_CREATE_PAGE, CreatePageLoggable::new);
        LogEntryTypes.addEntryType(LOG_STORE_VALUE, StoreValueLoggable::new);
        LogEntryTypes.addEntryType(LOG_REMOVE_VALUE, RemoveValueLoggable::new);
        LogEntryTypes.addEntryType(LOG_REMOVE_PAGE, RemoveEmptyPageLoggable::new);
        LogEntryTypes.addEntryType(LOG_OVERFLOW_APPEND, OverflowAppendLoggable::new);
        LogEntryTypes.addEntryType(LOG_OVERFLOW_STORE, OverflowStoreLoggable::new);
        LogEntryTypes.addEntryType(LOG_OVERFLOW_CREATE, OverflowCreateLoggable::new);
        LogEntryTypes.addEntryType(LOG_OVERFLOW_MODIFIED, OverflowModifiedLoggable::new);
        LogEntryTypes.addEntryType(LOG_OVERFLOW_CREATE_PAGE, OverflowCreatePageLoggable::new);
        LogEntryTypes.addEntryType(LOG_OVERFLOW_REMOVE, OverflowRemoveLoggable::new);
    }

    protected final LockManager lockManager;
    protected final BFileHeader fileHeader;
    protected final int minFree;
    protected final Cache<DataPage> dataCache;
    public final int fixedKeyLen = -1;
    protected final int maxValueSize;


    public BFile(final BrokerPool pool, final byte fileId, final short fileVersion, final boolean recoveryEnabled, final Path file, final DefaultCacheManager cacheManager,
            final double cacheGrowth, final double thresholdData) throws DBException {
        super(pool, fileId, fileVersion, recoveryEnabled, cacheManager, file);
        lockManager = pool.getLockManager();
        fileHeader = (BFileHeader) getFileHeader();
        dataCache = new LRUCache<>(FileUtils.fileName(file), 64, cacheGrowth, thresholdData, Cache.CacheType.DATA);
        cacheManager.registerCache(dataCache);
        minFree = PAGE_MIN_FREE;
        maxValueSize = fileHeader.getWorkSize() / 2;
        
        if(exists()) {
            open(fileVersion);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Creating data file: " + FileUtils.fileName(getFile()));
            }
            create();
        }
    }

    /**
     * Returns the Lock object responsible for this BFile.
     * 
     * @return Lock
     */
    @Override
    public String getLockName() {
        return FileUtils.fileName(getFile());
    }

    protected long getDataSyncPeriod() {
        return DATA_SYNC_PERIOD;
    }

    /**
     * Append the given data fragment to the value associated
     * with the key. A new entry is created if the key does not
     * yet exist in the database.
     * 
     * @param key the key
     * @param value the value
     *
     * @return the pointer to the storage address
     *
     * @throws ReadOnlyException if the BFile is read-only
     * @throws IOException if an I/O error occurs whilst writing to the BFile
     */
    public long append(final Value key, final ByteArray value)
            throws ReadOnlyException, IOException {
        return append(null, key, value);
    }

    public long append(final Txn transaction, final Value key, final ByteArray value) throws IOException {
        if (key == null) {
            LOG.debug("key is null");
            return UNKNOWN_ADDRESS;
        }

        if (key.getLength() > fileHeader.getMaxKeySize()) {
            //TODO : throw an exception ? -pb
            LOG.warn("Key length exceeds page size! Skipping key ...");
            return UNKNOWN_ADDRESS;
        }

        try {
            // check if key exists already
            long p = findValue(key);
            if (p == KEY_NOT_FOUND) {
                // key does not exist:
                p = storeValue(transaction, value);
                addValue(transaction, key, p);
                return p;
            }
            // key exists: get old data
            final long pnum = StorageAddress.pageFromPointer(p);
            final short tid = StorageAddress.tidFromPointer(p);
            final DataPage page = getDataPage(pnum);
            if (page instanceof OverflowPage) {
                ((OverflowPage) page).append(transaction, value);
            } else {
                final int valueLen = value.size();
                final byte[] data = page.getData();
                final int offset = page.findValuePosition(tid);
                if (offset < 0) {
                    throw new IOException("tid " + tid + " not found on page " + pnum);
                }
                if (offset + 4 > data.length) {
                    LOG.error("found invalid pointer in file " + FileUtils.fileName(getFile()) +
                            " for page" + page.getPageInfo() + " : " +
                            "tid = " + tid + "; offset = " + offset);
                    return UNKNOWN_ADDRESS;
                }
                final int l = ByteConversion.byteToInt(data, offset);
                //TOUNDERSTAND : unless l can be negative, we should never get there -pb
                if (offset + 4 + l > data.length) {
                    LOG.error("found invalid data record in file " + FileUtils.fileName(getFile()) +
                            " for page" + page.getPageInfo() + " : " +
                            "length = " + data.length + "; required = " + (offset + 4 + l));
                    return UNKNOWN_ADDRESS;
                }
                final byte[] newData = new byte[l + valueLen];
                System.arraycopy(data, offset + 4, newData, 0, l);
                value.copyTo(newData, l);
                p = update(transaction, p, page, key, new FixedByteArray(newData, 0, newData.length));
            }
            return p;
        } catch (final BTreeException bte) {
            LOG.warn("btree exception while appending value", bte);
        }
        return UNKNOWN_ADDRESS;
    }

    /**
     * Check, if key is contained in BFile.
     * 
     * @param key key to look for
     * @return true, if key exists
     */
    public boolean containsKey(final Value key) {
        try {
            return findValue(key) != KEY_NOT_FOUND;
        } catch (final BTreeException | IOException e) {
            LOG.warn(e.getMessage());
        }
        return false;
    }

    @Override
    public boolean create() throws DBException {
        return super.create((short) fixedKeyLen);
    }

    @Override
    public void closeAndRemove() {
        super.closeAndRemove();
        cacheManager.deregisterCache(dataCache);
    }

    private SinglePage createDataPage() {
        try {
            final SinglePage page = new SinglePage();
            dataCache.add(page, 2);
            return page;
        } catch (final IOException ioe) {
            LOG.warn(ioe);
            return null;
        }
    }

    @Override
    public FileHeader createFileHeader(final int pageSize) {
        return new BFileHeader(pageSize);
    }

    @Override
    public PageHeader createPageHeader() {
        return new BFilePageHeader();
    }

    /**
     * Remove all entries matching the given query.
     *
     * @param transaction the database transaction
     * @param query the removal query
     *
     * @throws IOException if an I/O error occurs whilst writing to the BFile
     * @throws BTreeException if an error occurs with the tree
     */
    public void removeAll(final Txn transaction, final IndexQuery query) throws IOException, BTreeException {
        // first collect the values to remove, then sort them by their page number
        // and remove them.
        try {
            final RemoveCallback cb = new RemoveCallback(); 
            remove(transaction, query, cb);
            LOG.debug("Found " + cb.count + " items to remove.");
            if (cb.count == 0) {
                return;
            }
            Arrays.sort(cb.pointers, 0, cb.count - 1);
            for (int i = 0; i < cb.count; i++) {
                remove(transaction, cb.pointers[i]);
            }
        } catch (final TerminatedException e) {
            // Should never happen during remove
            LOG.error("removeAll() - method has been terminated.", e);
        }
    }

    private static class RemoveCallback implements BTreeCallback {
        long[] pointers = new long[128];
        int count = 0;
        
        public boolean indexInfo(final Value value, final long pointer) throws TerminatedException {
            if (count == pointers.length) {
                final long[] np = new long[count * 2];
                System.arraycopy(pointers, 0, np, 0, count);
                pointers = np;
            }
            pointers[count++] = pointer;
            return true;
        }
    }

    public ArrayList<Value> findEntries(final IndexQuery query) throws IOException,
            BTreeException, TerminatedException {
        final FindCallback cb = new FindCallback(FindCallback.BOTH);
        query(query, cb);
        return cb.getValues();
    }

    public ArrayList<Value> findKeys(final IndexQuery query)
        throws IOException, BTreeException, TerminatedException {
        final FindCallback cb = new FindCallback(FindCallback.KEYS);
        query(query, cb);
        return cb.getValues();
    }

    public void find(final IndexQuery query, final IndexCallback callback)
            throws IOException, BTreeException, TerminatedException {
        final FindCallback cb = new FindCallback(callback);
        query(query, cb);
    }

    @Override
    public boolean flush() throws DBException {
        boolean flushed = false;
        //TODO : consider log operation as a flush ?
        if (isRecoveryEnabled()) {
            logManager.get().flush(true, false);
        }
        flushed = flushed | dataCache.flush();
        flushed = flushed | super.flush();
        return flushed;
    }

    public BufferStats getDataBufferStats() {
        if (dataCache == null) {
            return null;
        }
        return new BufferStats(dataCache.getBuffers(), dataCache.getUsedBuffers(), 
            dataCache.getHits(), dataCache.getFails());
    }

    @Override
    public void printStatistics() {
        super.printStatistics();
        final NumberFormat nf = NumberFormat.getPercentInstance();
        final StringBuilder buf = new StringBuilder();
        buf.append(FileUtils.fileName(getFile())).append(" DATA ");
        buf.append("Buffers occupation : ");
        if (dataCache.getBuffers() == 0 && dataCache.getUsedBuffers() == 0) {
            buf.append("N/A");
        } else {
            buf.append(nf.format(dataCache.getUsedBuffers()/(float)dataCache.getBuffers()));
        }
        buf.append(" (").append(dataCache.getUsedBuffers()).append(" out of ").append(dataCache.getBuffers()).append(")");
        //buf.append(dataCache.getBuffers()).append(" / ");
        //buf.append(dataCache.getUsedBuffers()).append(" / ");
        buf.append(" Cache efficiency : ");
        if (dataCache.getHits() == 0 && dataCache.getFails() == 0) {
            buf.append("N/A");
        } else {
            buf.append(nf.format(dataCache.getHits()/(float)(dataCache.getHits() + dataCache.getFails())));
        }
        //buf.append(dataCache.getHits()).append(" / ");
        //buf.append(dataCache.getFails());
        LOGSTATS.info(buf.toString());
    }

    /**
     * Get the value data associated with the specified key
     * or null if the key could not be found.
     * 
     * @param key the key
     *
     * @return the value associated with the key, or null if there is no association.
     */
    public Value get(final Value key) {
        try {
            final long p = findValue(key);
            if (p == KEY_NOT_FOUND) {
                return null;
            }
            final long pnum = StorageAddress.pageFromPointer(p);
            final DataPage page = getDataPage(pnum);
            return get(page, p);
        } catch (final BTreeException e) {
            LOG.error("An exception occurred while trying to retrieve key " + key + ": " + e.getMessage(), e);
        } catch (final IOException e) {
            LOG.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Get the value data for the given key as a variable byte
     * encoded input stream.
     * 
     * @param key the key
     * @return the stream
     * @throws IOException if an I/O error occurs
     */
    public VariableByteInput getAsStream(final Value key) throws IOException {
        try {
            final long p = findValue(key);
            if (p == KEY_NOT_FOUND) {return null;}           
            final long pnum = StorageAddress.pageFromPointer(p);
            final DataPage page = getDataPage(pnum);
            switch (page.getPageHeader().getStatus()) {
                case MULTI_PAGE:
                    return ((OverflowPage) page).getDataStream(p);
                default:
                    return getAsStream(page, p);
            }
        } catch (final BTreeException e) {
        	LOG.error("An exception occurred while trying to retrieve key " + key + ": " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Get the value located at the specified address as a
     * variable byte encoded input stream.
     * 
     * @param pointer the pointer to the value
     * @return the stream
     * @throws IOException if an I/O error occurs
     */
    public VariableByteInput getAsStream(final long pointer) throws IOException {
        final DataPage page = getDataPage(StorageAddress.pageFromPointer(pointer));
        switch (page.getPageHeader().getStatus()) {
            case MULTI_PAGE:
                return ((OverflowPage) page).getDataStream(pointer);
            default:
                return getAsStream(page, pointer);
        }
    }

    private VariableByteInput getAsStream(final DataPage page, final long pointer) throws IOException {
        dataCache.add(page.getFirstPage(), 2);
        final short tid = StorageAddress.tidFromPointer(pointer);
        final int offset = page.findValuePosition(tid);
        if (offset < 0) {
            throw new IOException("no data found at tid " + tid + "; page " + page.getPageNum());
        }
        final byte[] data = page.getData();
        final int l = ByteConversion.byteToInt(data, offset);
        final SimplePageInput input = new SimplePageInput(data, offset + 4, l, pointer);
        return input;
    }

    /**
     * Returns the value located at the specified address.
     * 
     * @param pointer the pointer to the value
     * @return value located at the specified address
     */
    public Value get(final long pointer) {
        try {
            final long pnum = StorageAddress.pageFromPointer(pointer);
            final DataPage page = getDataPage(pnum);
            return get(page, pointer);
        } catch (final IOException e) {
            LOG.error(e);
        }
        return null;
    }

    /**
     * Retrieve value at logical address pointer from page
     *
     * @param page the data page
     * @param pointer the pointer to the value
     *
     * @return the value or null if there is no value
     *
     * @throws IOException if an I/O error occurs
     */
    protected Value get(final DataPage page, final long pointer) throws IOException {
        final short tid = StorageAddress.tidFromPointer(pointer);
        final int offset = page.findValuePosition(tid);
        final byte[] data = page.getData();
        if (offset < 0 || offset > data.length) {
            LOG.error("wrong pointer (tid: " + tid + page.getPageInfo()
                    + ") in file " + FileUtils.fileName(getFile()) + "; offset = "
                    + offset);
            return null;
        }
        final int l = ByteConversion.byteToInt(data, offset);
        if (l + 6 > data.length) {
            LOG.error(FileUtils.fileName(getFile()) + " wrong data length in page "
                    + page.getPageNum() + ": expected=" + (l + 6) + "; found="
                    + data.length);
            return null;
        }
        dataCache.add(page.getFirstPage());
        final Value v = new Value(data, offset + 4, l);
        v.setAddress(pointer);
        return v;
    }

    private DataPage getDataPage(final long pos) throws IOException {
    	return getDataPage(pos, true);
    }

    private DataPage getDataPage(final long pos, final boolean initialize) throws IOException {
        final DataPage wp = (DataPage) dataCache.get(pos);
        if (wp == null) {
            final Page page = getPage(pos);
            if (page == null) {
                LOG.debug("page " + pos + " not found!");
                return null;
            }
            final byte[] data = page.read();
            if (page.getPageHeader().getStatus() == MULTI_PAGE) {
                return new OverflowPage(page, data);
            }
            return new SinglePage(page, data, initialize);
        } else if (wp.getPageHeader().getStatus() == MULTI_PAGE) {
            return new OverflowPage(wp);
        } else {
            return wp;
        }
    }

    private SinglePage getSinglePage(final long pos) throws IOException {
        return getSinglePage(pos, false);
    }

    private SinglePage getSinglePage(final long pos, final boolean initialize) throws IOException {
        final SinglePage wp = (SinglePage) dataCache.get(pos);
        if (wp == null) {
            final Page page = getPage(pos);
            if (page == null) {
                LOG.debug("page " + pos + " not found!");
                return null;
            }
            final byte[] data = page.read();
            return new SinglePage(page, data, initialize);
        }
        return wp;
    }

    public ArrayList<Value> getEntries() throws IOException, BTreeException, TerminatedException {
        final IndexQuery query = new IndexQuery(IndexQuery.ANY, "");
        final FindCallback cb = new FindCallback(FindCallback.BOTH);
        query(query, cb);
        return cb.getValues();
    }

    public ArrayList<Value> getKeys() throws IOException, BTreeException, TerminatedException {
        final IndexQuery query = new IndexQuery(IndexQuery.ANY, "");
        final FindCallback cb = new FindCallback(FindCallback.KEYS);
        query(query, cb);
        return cb.getValues();
    }

    public ArrayList<Value> getValues() throws IOException, BTreeException, TerminatedException {
        final IndexQuery query = new IndexQuery(IndexQuery.ANY, "");
        final FindCallback cb = new FindCallback(FindCallback.VALUES);
        query(query, cb);
        return cb.getValues();
    }

    /**
     * Put data under given key.
     *
     * @param key the key
     * @param data the data (value) to update
     * @param overwrite overwrite if set to true, value will be overwritten if it already exists
     *
     * @return on success the address of the stored value, else UNKNOWN_ADDRESS
     * @throws ReadOnlyException if the BFile is read-only
     */
    public long put(final Value key, final byte[] data, final boolean overwrite) throws ReadOnlyException {
        return put(null, key, data, overwrite);
    }

    public long put(final Txn transaction, final Value key, final byte[] data, final boolean overwrite) {
        SanityCheck.THROW_ASSERT(key.getLength() <= fileHeader.getWorkSize(), "Key length exceeds page size!");
        final FixedByteArray buf = new FixedByteArray(data, 0, data.length);
        return put(transaction, key, buf, overwrite);
    }

    /**
     * Convenience method for {@link BFile#put(Value, byte[], boolean)}, overwrite is true.
     * 
     * @param key with which the data is updated
     * @param value value to update
     *
     * @return on success the address of the stored value, else UNKNOWN_ADDRESS
     */
    public long put(final Value key, final ByteArray value) {
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
     *
     * @return on success the address of the stored value, else UNKNOWN_ADDRESS
     */
    public long put(final Value key, final ByteArray value, final boolean overwrite) {
        return put(null, key, value, overwrite);
    }

    public long put(final Txn transaction, final Value key, final ByteArray value, final boolean overwrite) {
        if (key == null) {
            LOG.debug("key is null");
            return UNKNOWN_ADDRESS;
        }

        if (key.getLength() > fileHeader.getWorkSize()) {
            //TODO : exception ? -pb
            LOG.warn("Key length exceeds page size! Skipping key ...");
            return UNKNOWN_ADDRESS;
        }

        try {
            try {
                // check if key exists already
                //TODO : rely on a KEY_NOT_FOUND (or maybe VALUE_NOT_FOUND) result ! -pb
                long p = findValue(key);
                if (p == KEY_NOT_FOUND) {
                    // key does not exist:
                    p = storeValue(transaction, value);
                    addValue(transaction, key, p);
                    return p;
                }

                // if exists, update value
                if (overwrite) {
                    return update(transaction, p, key, value);
                }

                //TODO : throw an exception ? -pb
                return UNKNOWN_ADDRESS;
            //TODO : why catch an exception here ??? It costs too much ! -pb
            } catch (final BTreeException bte) {
                // key does not exist:
                final long p = storeValue(transaction, value);
                addValue(transaction, key, p);
                return p;
            } catch (final IOException ioe) {
                ioe.printStackTrace();
                LOG.warn(ioe);
                return UNKNOWN_ADDRESS;
            }
        } catch (final BTreeException | IOException e) {
            e.printStackTrace();
            LOG.warn(e);
            return UNKNOWN_ADDRESS;
        }
    }

    public void remove(final Value key) {
        remove(null, key);
    }

    public void remove(final Txn transaction, final Value key) {
        try {
            final long p = findValue(key);
            if (p == KEY_NOT_FOUND) {
                return;
            }
            final long pos = StorageAddress.pageFromPointer(p);
            final DataPage page = getDataPage(pos);
            remove(transaction, page, p);
            removeValue(transaction, key);
        } catch (final BTreeException | IOException e) {
            LOG.error(e);
        }
    }

    public void remove(final Txn transaction, final long p) {
        try {
            final long pos = StorageAddress.pageFromPointer(p);
            final DataPage page = getDataPage(pos);
            remove(transaction, page, p);
        } catch (final IOException e) {
            LOG.error(e);
        }
    }

    private void remove(final Txn transaction, final DataPage page, final long p) throws IOException {
        if (page.getPageHeader().getStatus() == MULTI_PAGE) {
            // overflow page: simply delete the whole page
            ((OverflowPage)page).delete(transaction);
            return;
        }
        final short tid = StorageAddress.tidFromPointer(p);
        final int offset = page.findValuePosition(tid);
        final byte[] data = page.getData();
        if (offset > data.length || offset < 0) {
            LOG.error("wrong pointer (tid: " + tid + ", " + page.getPageInfo() + ")");
            return;
        }
        final int l = ByteConversion.byteToInt(data, offset);
        if (transaction != null && isRecoveryEnabled()) {
            final Loggable loggable = new RemoveValueLoggable(transaction, fileId, page.getPageNum(), tid, data, offset + 4, l);
            writeToLog(loggable, page);
        }
        final BFilePageHeader ph = page.getPageHeader();
        final int end = offset + 4 + l;
        int len = ph.getDataLength();
        // remove old value
        System.arraycopy(data, end, data, offset - 2, len - end);
        ph.setDirty(true);
        ph.decRecordCount();
        len = len - l - 6;
        ph.setDataLength(len);
        page.setDirty(true);
        // if this page is empty, remove it
        if (len == 0) {
            if (transaction != null && isRecoveryEnabled()) {
                final Loggable loggable = new RemoveEmptyPageLoggable(transaction, fileId, page.getPageNum());
                writeToLog(loggable, page);
            }
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
            }
            dataCache.add(page, 2);
        }
    }

    private final void saveFreeSpace(final FreeSpace space, final DataPage page) {
        final int free = fileHeader.getWorkSize() - page.getPageHeader().getDataLength();
        space.setFree(free);
        if(free < minFree) {
            fileHeader.removeFreeSpace(space);
        }
    }

    public long storeValue(final Txn transaction, final ByteArray value) throws IOException {
        final int vlen = value.size();
        // does value fit into a single page?
        if (6 + vlen > maxValueSize) {
            final OverflowPage page = new OverflowPage(transaction);
            final byte[] data = new byte[vlen + 6];
            page.getPageHeader().setDataLength(vlen + 6);
            ByteConversion.shortToByte((short) 1, data, 0);
            ByteConversion.intToByte(vlen, data, 2);
            //System.arraycopy(value, 0, data, 6, vlen);
            value.copyTo(data, 6);
            page.setData(transaction, data);
            page.setDirty(true);
            //dataCache.add(page);
            return StorageAddress.createPointer((int) page.getPageNum(), (short)1);
        }
        DataPage page = null;
        short tid = -1;
        FreeSpace free = null;
        // check for available tid
        while (tid < 0) {
            free = fileHeader.findFreeSpace(vlen + 6);
            if (free == null) {
                page = createDataPage();
                if (transaction != null && isRecoveryEnabled()) {
                    final Loggable loggable = new CreatePageLoggable(transaction, fileId, page.getPageNum());
                    writeToLog(loggable, page);
                }
                page.setData(new byte[fileHeader.getWorkSize()]);
                free = new FreeSpace(page.getPageNum(), 
                        fileHeader.getWorkSize() - page.getPageHeader().getDataLength());
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
                final int realSpace = fileHeader.getWorkSize() - page.getPageHeader().getDataLength();
                if (realSpace < 6 + vlen) {
                    // not correct: adjust and continue
                    LOG.warn("Wrong data length in list of free pages: adjusting to " + realSpace);
                    free.setFree(realSpace);
                    continue;
                }
            }
            tid = page.getNextTID();
            if (tid < 0) {
                LOG.info("removing page " + page.getPageNum() + " from free pages");
                fileHeader.removeFreeSpace(free);
            }
        }
        if (transaction != null && isRecoveryEnabled()) {
            final Loggable loggable = new StoreValueLoggable(transaction, fileId, page.getPageNum(), tid, value);
            writeToLog(loggable, page);
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
        return StorageAddress.createPointer((int) page.getPageNum(), tid);
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
    public long update(final Value key, final ByteArray value) {
        try {
            final long p = findValue(key);
            if (p == KEY_NOT_FOUND) {return UNKNOWN_ADDRESS;}
            return update(p, key, value);
        } catch (final BTreeException | IOException bte) {
            LOG.debug(bte);
        }
        return UNKNOWN_ADDRESS;
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
    public long update(final long p, final Value key, final ByteArray value) {
        return update(null, p, key, value);
    }
    
    public long update(final Txn transaction, final long p, final Value key, final ByteArray value) {
        try {
            return update(transaction, p, getDataPage(StorageAddress.pageFromPointer(p)),
                    key, value);
        } catch (final BTreeException | IOException ioe) {
            LOG.error(ioe.getMessage(), ioe);
            return UNKNOWN_ADDRESS;
        }
    }

    /**
     * Update the key/value pair with logical address p and stored in page.
     *
     * @param transaction the database transaction
     * @param p the pointer address
     * @param page the data page
     * @param key the key
     * @param value the value
     *
     * @return the new pointer
     *
     * @throws BTreeException if an error occurs updating the tree
     * @throws IOException if an I/O error occurs
     */
    protected long update(final Txn transaction, final long p, final DataPage page, final Value key, final ByteArray value)
            throws BTreeException, IOException {
        if (page.getPageHeader().getStatus() == MULTI_PAGE) {
            final int valueLen = value.size();
            // does value fit into a single page?
            if (valueLen + 6 < maxValueSize) {
                // yes: remove the overflow page
                remove(transaction, page, p);
                final long np = storeValue(transaction, value);
                addValue(transaction, key, np);
                return np;
            }
            // this is an overflow page: simply replace the value
            final byte[] data = new byte[valueLen + 6];
            // save tid
            ByteConversion.shortToByte((short) 1, data, 0);
            // save length
            ByteConversion.intToByte(valueLen, data, 2);
            // save data
            value.copyTo(data, 6);
            ((OverflowPage)page).setData(transaction, data);
            return p;
        }
        remove(transaction, page, p);
        final long np = storeValue(transaction, value);
        addValue(transaction, key, np);
        return np;
    }

    public void debugFreeList() {
    	fileHeader.debugFreeList();
    }

    /* ---------------------------------------------------------------------------------
     * Methods used by recovery and transaction management
     * --------------------------------------------------------------------------------- */
    
    /**
     * Write loggable to the journal and update the LSN in the page header.
     *
     * @param loggable the log entry
     * @param page the data page
     */
    private void writeToLog(final Loggable loggable, final DataPage page) {
        if(logManager.isPresent()) {
            try {
                logManager.get().journal(loggable);
                page.getPageHeader().setLsn(loggable.getLsn());
            } catch (final JournalException e) {
                LOG.warn(e.getMessage(), e);
            }
        }
    }

    private SinglePage getSinglePageForRedo(final Loggable loggable, final long pos) throws IOException {
        final SinglePage wp = (SinglePage) dataCache.get(pos);
        if (wp == null) {
            final Page page = getPage(pos);
            final byte[] data = page.read();
            if (page.getPageHeader().getStatus() < RECORD) {
                return null;
            }
            if (loggable != null && isUptodate(page, loggable)) {
                return null;
            }
            return new SinglePage(page, data, true);
        }
        return wp;
    }

    private boolean isUptodate(final Page page, final Loggable loggable) {
        return page.getPageHeader().getLsn().compareTo(loggable.getLsn()) >= 0;
    }

    private boolean requiresRedo(final Loggable loggable, final DataPage page) {
        return loggable.getLsn().compareTo(page.getPageHeader().getLsn()) > 0;
    }

    protected void redoStoreValue(final StoreValueLoggable loggable) {
        try {
            final SinglePage page = getSinglePageForRedo(loggable, loggable.page);
            if (page != null && requiresRedo(loggable, page)) {
                storeValueHelper(loggable, loggable.tid, loggable.value, page);
            }
        } catch (final IOException e) {
            LOG.warn("An IOException occurred during redo: " + e.getMessage());
        }
    }

    protected void undoStoreValue(final StoreValueLoggable loggable) {
        try {
            final SinglePage page = (SinglePage) getDataPage(loggable.page, true);
            removeValueHelper(null, loggable.tid, page);
        } catch (final IOException e) {
            LOG.warn("An IOException occurred during redo: " + e.getMessage(), e);
        }
    }

    protected void redoCreatePage(final CreatePageLoggable loggable) {
        createPageHelper(loggable, loggable.newPage, false);
    }

    protected void undoCreatePage(final CreatePageLoggable loggable) {
        try {
            final SinglePage page = (SinglePage) getDataPage(loggable.newPage);
            fileHeader.removeFreeSpace(fileHeader.getFreeSpace(page.getPageNum()));
            dataCache.remove(page);
            page.delete();
        } catch (final IOException e) {
            LOG.warn("An IOException occurred during redo: " + e.getMessage(), e);
        }
    }

    protected void redoRemoveValue(final RemoveValueLoggable loggable) {
        try {
            SinglePage wp = (SinglePage) dataCache.get(loggable.page);
            if (wp == null) {
                final Page page = getPage(loggable.page);
                if (page == null) {
                    LOG.warn("page " + loggable.page + " not found!");
                    return;
                }
                final byte[] data = page.read();
                if (page.getPageHeader().getStatus() < RECORD || isUptodate(page, loggable)) {
                	// page is obviously deleted later
                	return;
                }
                wp = new SinglePage(page, data, true);
            }
            if (!wp.ph.getLsn().equals(Lsn.LSN_INVALID) && requiresRedo(loggable, wp)) {
                removeValueHelper(loggable, loggable.tid, wp);
            }
        } catch (final IOException e) {
            LOG.warn("An IOException occurred during redo: " + e.getMessage(), e);
        }
    }

    protected void undoRemoveValue(final RemoveValueLoggable loggable) {
        try {
            final SinglePage page = getSinglePage(loggable.page, true);
            final FixedByteArray data = new FixedByteArray(loggable.oldData);
            storeValueHelper(null, loggable.tid, data, page);
        } catch (final IOException e) {
            LOG.warn("An IOException occurred during undo: " + e.getMessage(), e);
        }
    }
    
    protected void redoRemovePage(final RemoveEmptyPageLoggable loggable) {
        try {
            SinglePage wp = (SinglePage) dataCache.get(loggable.page);
            if (wp == null) {
                final Page page = getPage(loggable.page);
                if (page == null) {
                    LOG.warn("page " + loggable.page + " not found!");
                    return;
                }
                final byte[] data = page.read();
                if (page.getPageHeader().getStatus() < RECORD || isUptodate(page, loggable)) {
                    return;
                }
                wp = new SinglePage(page, data, false);
            }
            if (wp.getPageHeader().getLsn().equals(Lsn.LSN_INVALID) || requiresRedo(loggable, wp)) {
                fileHeader.removeFreeSpace(fileHeader.getFreeSpace(wp.getPageNum()));
                dataCache.remove(wp);
                wp.delete();
            }
        } catch (final IOException e) {
            LOG.warn("An IOException occurred during redo: " + e.getMessage(), e);
        }
    }

    protected void undoRemovePage(final RemoveEmptyPageLoggable loggable) {
        createPageHelper(loggable, loggable.page, false);
    }

    protected void redoCreateOverflow(final OverflowCreateLoggable loggable) {
        try {
            DataPage firstPage = (DataPage) dataCache.get(loggable.pageNum);
            if (firstPage == null) {
                final Page page = getPage(loggable.pageNum);
                byte[] data = page.read();
                if (page.getPageHeader().getLsn().equals(Lsn.LSN_INVALID) || requiresRedo(loggable, page)) {
                    dropFreePageList();
                    final BFilePageHeader ph = (BFilePageHeader) page.getPageHeader();
                    ph.setStatus(MULTI_PAGE);
                    ph.setNextInChain(0L);
                    ph.setLastInChain(0L);
                    ph.setDataLength(0);
                    ph.nextTID = 32;
                    data = new byte[fileHeader.getWorkSize()];
                    firstPage = new SinglePage(page, data, true);
                    firstPage.setDirty(true);
                } else {
                    firstPage = new SinglePage(page, data, false);
                }
            }
            if (!firstPage.getPageHeader().getLsn().equals(Lsn.LSN_INVALID) && requiresRedo(loggable, firstPage)) {
                firstPage.getPageHeader().setLsn(loggable.getLsn());
                firstPage.setDirty(true);
            }
            dataCache.add(firstPage);
        } catch (final IOException e) {
            LOG.warn("An IOException occurred during redo: " + e.getMessage(), e);
        }
    }

    protected void undoCreateOverflow(final OverflowCreateLoggable loggable) {
        try {
            final SinglePage page = getSinglePage(loggable.pageNum);
            dataCache.remove(page);
            page.delete();
        } catch (final IOException e) {
            LOG.warn("An IOException occurred during redo: " + e.getMessage(), e);
        }
    }

    protected void redoCreateOverflowPage(final OverflowCreatePageLoggable loggable) {
        createPageHelper(loggable, loggable.newPage, false);
        if (loggable.prevPage != Page.NO_PAGE) {
            try {
                final SinglePage page = getSinglePageForRedo(null, loggable.prevPage);
                SanityCheck.ASSERT(page != null, "Previous page is null");
                page.getPageHeader().setNextInChain(loggable.newPage);
                page.setDirty(true);
                dataCache.add(page);
            } catch (final IOException e) {
                LOG.warn("An IOException occurred during redo: " + e.getMessage(), e);
            }
        }
    }

    protected void undoCreateOverflowPage(final OverflowCreatePageLoggable loggable) {
        try {
            SinglePage page = getSinglePage(loggable.newPage);
            dataCache.remove(page);
            page.delete();
            
            if (loggable.prevPage != Page.NO_PAGE) {
                page = getSinglePage(loggable.prevPage);
                SanityCheck.ASSERT(page != null, "Previous page is null");
                page.getPageHeader().setNextInChain(0);
                page.setDirty(true);
                dataCache.add(page);
            }
        } catch (final IOException e) {
            LOG.warn("An IOException occurred during redo: " + e.getMessage(), e);
        }
    }

    protected void redoAppendOverflow(final OverflowAppendLoggable loggable) {
        try {
            final SinglePage page = getSinglePageForRedo(loggable, loggable.pageNum);
            if (page != null && requiresRedo(loggable, page)) {
                final BFilePageHeader ph = page.getPageHeader();
                loggable.data.copyTo(0, page.getData(), ph.getDataLength(), loggable.chunkSize);
                ph.setDataLength(ph.getDataLength() + loggable.chunkSize);
                ph.setLsn(loggable.getLsn());
                page.setDirty(true);
                dataCache.add(page);
            }
        } catch (final IOException e) {
            LOG.warn("An IOException occurred during redo: " + e.getMessage(), e);
        }
    }

    protected void undoAppendOverflow(final OverflowAppendLoggable loggable) {
        try {
            final SinglePage page = getSinglePage(loggable.pageNum);
            final BFilePageHeader ph = page.getPageHeader();
            ph.setDataLength(ph.getDataLength() - loggable.chunkSize);
            page.setDirty(true);
            dataCache.add(page);
        } catch (final IOException e) {
            LOG.warn("An IOException occurred during redo: " + e.getMessage(), e);
        }
    }

    protected void redoStoreOverflow(final OverflowStoreLoggable loggable) {
        try {
            SinglePage page = getSinglePageForRedo(loggable, loggable.pageNum);
            if (page != null && requiresRedo(loggable, page)) {
                final BFilePageHeader ph = page.getPageHeader();
                try {
                    System.arraycopy(loggable.data, 0, page.getData(), 0, loggable.size);
                } catch (final ArrayIndexOutOfBoundsException e) {
                    LOG.warn(loggable.data.length + "; " + page.getData().length + "; " + ph.getDataLength() + "; " + loggable.size);
                    throw e;
                }
                ph.setDataLength(loggable.size);
                ph.setNextInChain(0);
                ph.setLsn(loggable.getLsn());
                page.setDirty(true);
                dataCache.add(page);
                
                if (loggable.prevPage != Page.NO_PAGE) {
                    page = getSinglePage(loggable.prevPage);
                    SanityCheck.ASSERT(page != null, "Previous page is null");
                    page.getPageHeader().setNextInChain(loggable.pageNum);
                    page.setDirty(true);
                    dataCache.add(page);
                }
            }
        } catch (final IOException e) {
            LOG.warn("An IOException occurred during redo: " + e.getMessage(), e);
        }
    }

    protected void redoModifiedOverflow(final OverflowModifiedLoggable loggable) {
        try {
            final SinglePage page = getSinglePageForRedo(loggable, loggable.pageNum);
            if (page != null && requiresRedo(loggable, page)) {
                final BFilePageHeader ph = page.getPageHeader();
                ph.setDataLength(loggable.length);
                ph.setLastInChain(loggable.lastInChain);
                // adjust length field in first page
                ByteConversion.intToByte(ph.getDataLength() - 6, page.getData(), 2);
                page.setDirty(true);
                // keep the first page in cache
                dataCache.add(page, 2);
            }
        } catch (final IOException e) {
            LOG.warn("An IOException occurred during redo: " + e.getMessage(), e);
        }
    }

    protected void undoModifiedOverflow(final OverflowModifiedLoggable loggable) {
        try {
            final SinglePage page = getSinglePage(loggable.pageNum);
            final BFilePageHeader ph = page.getPageHeader();
            ph.setDataLength(loggable.oldLength);
            // adjust length field in first page
            ByteConversion.intToByte(ph.getDataLength() - 6, page.getData(), 2);
            page.setDirty(true);
            dataCache.add(page);
        } catch (final IOException e) {
            LOG.warn("An IOException occurred during undo: " + e.getMessage(), e);
        }
    }

    protected void redoRemoveOverflow(final OverflowRemoveLoggable loggable) {
        try {
            SinglePage wp = (SinglePage) dataCache.get(loggable.pageNum);
            if (wp == null) {
                final Page page = getPage(loggable.pageNum);
                if (page == null) {
                    LOG.warn("page " + loggable.pageNum + " not found!");
                    return;
                }
                final byte[] data = page.read();
                if (page.getPageHeader().getStatus() < RECORD || isUptodate(page, loggable))
                    {return;}
                wp = new SinglePage(page, data, true);
            }
            if (requiresRedo(loggable, wp)) {
                wp.setDirty(true);
                dataCache.remove(wp);
                wp.delete();
            }
        } catch (final IOException e) {
            LOG.warn("An IOException occurred during redo: " + e.getMessage(), e);
        }
    }

    protected void undoRemoveOverflow(final OverflowRemoveLoggable loggable) {
        final DataPage page = createPageHelper(loggable, loggable.pageNum, false);
        final BFilePageHeader ph = page.getPageHeader();
        ph.setStatus(loggable.status);
        ph.setDataLength(loggable.length);
        ph.setNextInChain(loggable.nextInChain);
        page.setData(loggable.data);
        page.setDirty(true);
        dataCache.add(page);
    }

    private void storeValueHelper(final Loggable loggable, final short tid, final ByteArray value, final SinglePage page) {
        int len = page.ph.getDataLength();
        // save tid
        ByteConversion.shortToByte(tid, page.data, len);
        len += 2;
        page.adjustTID(tid);
        page.setOffset(tid, len);
        // save data length
        ByteConversion.intToByte(value.size(), page.data, len);
        len += 4;
        // save data
        try {
            value.copyTo(page.data, len);
        } catch (final RuntimeException e) {
            LOG.error(FileUtils.fileName(getFile()) + ": storage error in page: " + page.getPageNum() +
                    "; len: " + len + " ; value: " + value.size() + "; max: " + fileHeader.getWorkSize() +
                    "; status: " + page.ph.getStatus());
            LOG.debug(page.printContents());
            throw e;
        }
        len += value.size();
        page.ph.setDataLength(len);
        page.ph.incRecordCount();
        if (loggable != null) {
            page.ph.setLsn(loggable.getLsn());
        }
        FreeSpace free = fileHeader.getFreeSpace(page.getPageNum());
        if (free == null) {
            free = new FreeSpace(page.getPageNum(), fileHeader.getWorkSize() - len);
        }
        saveFreeSpace(free, page);
        page.setDirty(true);
        dataCache.add(page);
    }

    private void removeValueHelper(final Loggable loggable, final short tid, final SinglePage page) throws IOException {
        final int offset = page.findValuePosition(tid);
        if (offset < 0) {
            LOG.warn("TID: " + tid + " not found on page: " + page.getPageNum());
            return;
        }
        final int l = ByteConversion.byteToInt(page.data, offset);
        final int end = offset + 4 + l;
        int len = page.ph.getDataLength();
        // remove old value
        System.arraycopy(page.data, end, page.data, offset - 2, len - end);
        page.ph.setDirty(true);
        page.ph.decRecordCount();
        len = len - l - 6;
        page.ph.setDataLength(len);
        if (loggable != null) {
            page.ph.setLsn(loggable.getLsn());
        }
        page.setDirty(true);
        if (len > 0) {
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
            }
            dataCache.add(page, 2);
        }
    }

    private DataPage createPageHelper(final Loggable loggable, final long newPage, final boolean reuseDeleted) {
        try {
            DataPage dp = (DataPage) dataCache.get(newPage);
            if (dp == null) {
                final Page page = getPage(newPage);
                byte[] data = page.read();
                if (page.getPageHeader().getLsn().equals(Lsn.LSN_INVALID) || (loggable != null && requiresRedo(loggable, page)) ) {
                    if (reuseDeleted) {
                        reuseDeleted(page);
                    } else {
                        dropFreePageList();
                    }
                    final BFilePageHeader ph = (BFilePageHeader) page.getPageHeader();
                    ph.setStatus(RECORD);
                    ph.setDataLength(0);
                    ph.setDataLen(fileHeader.getWorkSize());
                    data = new byte[fileHeader.getWorkSize()];
                    ph.nextTID = 32;
                    dp = new SinglePage(page, data, true);
                } else {
                    dp = new SinglePage(page, data, true);
                }
            }
            if (loggable != null && loggable.getLsn().compareTo(dp.getPageHeader().getLsn()) > 0) {
                dp.getPageHeader().setLsn(loggable.getLsn());
            }
            dp.setDirty(true);
            dataCache.add(dp);
            return dp;
        } catch (final IOException e) {
            LOG.warn("An IOException occurred during redo: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * The file header. Most important, the file header stores the list of
     * data pages containing unused space.
     * 
     * @author wolf
     */
    private final class BFileHeader extends BTreeFileHeader {

        private final FreeList freeList = new FreeList();
        
        //public final static int MAX_FREE_LIST_LEN = 128;

        public BFileHeader(final int pageSize) {
            super(pageSize);
        }

        public void addFreeSpace(final FreeSpace freeSpace) {
            freeList.add(freeSpace);
            setDirty(true);
        }

        public FreeSpace findFreeSpace(final int needed) {
        	return freeList.find(needed);
        }

        public FreeSpace getFreeSpace(final long page) {
            return freeList.retrieve(page);
        }

        public void removeFreeSpace(final FreeSpace space) {
            if (space == null) {
                return;
            }
            freeList.remove(space);
            setDirty(true);
        }

        public void debugFreeList() {
        	LOG.debug(FileUtils.fileName(getFile()) + ": " + freeList.toString());
        }

        @Override
        public int read(final byte[] buf) throws IOException {
            final int offset = super.read(buf);
            return freeList.read(buf, offset);
        }

        @Override
        public int write(final byte[] buf) throws IOException {
            final int offset = super.write(buf);
            return freeList.write(buf, offset);
        }
    }

    private static final class BFilePageHeader extends BTreePageHeader {

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

        @Override
        public int read(final byte[] data, int offset) throws IOException {
            offset = super.read(data, offset);
            records = ByteConversion.byteToShort(data, offset);
            offset += LENGTH_RECORDS_COUNT;
            dataLen = ByteConversion.byteToInt(data, offset);
            offset += 4;
            nextTID = ByteConversion.byteToShort(data, offset);
            offset += LENGTH_NEXT_TID;
            nextInChain = ByteConversion.byteToLong(data, offset);
            offset += 8;
            lastInChain = ByteConversion.byteToLong(data, offset);
            return offset + 8;
        }

        public void setDataLength(final int len) {
            dataLen = len;
        }

        public void setLastInChain(final long p) {
            lastInChain = p;
        }

        public void setNextInChain(final long b) {
            nextInChain = b;
        }

        public void setRecordCount(final short recs) {
            records = recs;
        }

        public void setTID(final short tid) {
            this.nextTID = tid;
        }

        @Override
        public int write(final byte[] data, int offset) throws IOException {
            offset = super.write(data, offset);
            ByteConversion.shortToByte(records, data, offset);
            offset += LENGTH_RECORDS_COUNT;
            ByteConversion.intToByte(dataLen, data, offset);
            offset += 4;
            ByteConversion.shortToByte(nextTID, data, offset);
            offset += LENGTH_NEXT_TID;
            ByteConversion.longToByte(nextInChain, data, offset);
            offset += 8;
            ByteConversion.longToByte(lastInChain, data, offset);
            return offset + 8;
        }
    }

    private abstract class DataPage implements Comparable, Cacheable {
        private int refCount = 0;
        private int timestamp = 0;
        private boolean saved = true;

        public abstract void delete() throws IOException;

        public abstract byte[] getData() throws IOException;

        public abstract BFilePageHeader getPageHeader();

        public abstract String getPageInfo();

        public abstract long getPageNum();

        public abstract int findValuePosition(short tid) throws IOException;

        public abstract short getNextTID();

        public abstract void removeTID(short tid, int length) throws IOException;

        public abstract void setOffset(short tid, int offset);

        @Override
        public long getKey() {
            return getPageNum();
        }

        @Override
        public int getReferenceCount() {
            return refCount;
        }

        @Override
        public int incReferenceCount() {
            if (refCount < Cacheable.MAX_REF) {++refCount;}
            return refCount;
        }

        @Override
        public int decReferenceCount() {
            return refCount > 0 ? --refCount : 0;
        }

        @Override
        public void setReferenceCount(final int count) {
            refCount = count;
        }

        @Override
        public void setTimestamp(final int timestamp) {
            this.timestamp = timestamp;
        }

        @Override
        public int getTimestamp() {
            return timestamp;
        }

        @Override
        public boolean sync(final boolean syncJournal) {
            if (isDirty()) {
                try {
                    write();
                    if (isRecoveryEnabled() && syncJournal && logManager.get().lastWrittenLsn().compareTo(getPageHeader().getLsn()) < 0) {
                        logManager.get().flush(true, false);
                    }
                    return true;
                } catch (final IOException e) {
                    LOG.error("IO exception occurred while saving page "
                            + getPageNum());
                }
            }
            return false;
        }

        @Override
        public boolean isDirty() {
            return !saved;
        }

        @Override
        public boolean allowUnload() {
            return true;
        }

        public abstract void setData(byte[] buf);

        public abstract SinglePage getFirstPage();

        public void setDirty(final boolean dirty) {
            saved = !dirty;
            getPageHeader().setDirty(dirty);
        }

        public abstract void write() throws IOException;

        @Override
        public int compareTo(final Object other) {
            if (getPageNum() == ((DataPage) other).getPageNum()) {
                return Constants.EQUAL;
            } else if (getPageNum() > ((DataPage) other).getPageNum()) {
                return Constants.SUPERIOR;
            } else {
                return Constants.INFERIOR;
            }
        }
    }

    private final class FilterCallback implements BTreeCallback {
        private final BFileCallback callback;

        public FilterCallback(final BFileCallback callback) {
            this.callback = callback;
        }

        @Override
        public boolean indexInfo(final Value value, final long pointer) throws TerminatedException{
            try {
                final long pos = StorageAddress.pageFromPointer(pointer);
                final short tid = StorageAddress.tidFromPointer(pointer);
                final DataPage page = getDataPage(pos);
                final int offset = page.findValuePosition(tid);
                final byte[] data = page.getData();
                final int l = ByteConversion.byteToInt(data, offset);
                final Value v = new Value(data, offset + 4, l);
                callback.info(value, v);
                return true;
            } catch (final IOException e) {
                LOG.error(e.getMessage(), e);
                return true;
            }
        }
    }

    private final class FindCallback implements BTreeCallback {
        public final static int BOTH = 2;
        public final static int KEYS = 1;
        public final static int VALUES = 0;

        private final int mode;
        private final IndexCallback callback;
        private final ArrayList<Value> values;

        public FindCallback(final int mode) {
            this.mode = mode;
            this.callback = null;
            this.values = new ArrayList<>();
        }

        public FindCallback(final IndexCallback callback) {
            this.mode = BOTH;
            this.callback = callback;
            this.values = null;
        }

        public ArrayList<Value> getValues() {
            return values;
        }

        public boolean indexInfo(final Value value, final long pointer) throws TerminatedException {
            final long pos;
            final short tid;
            final DataPage page;
            final int offset;
            final int l;
            final Value v;
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
                        if (callback == null) {
                            values.add(v);
                        } else {
                            return callback.indexInfo(value, v);
                        }
                        return true;

                    case KEYS:
                        value.setAddress(pointer);
                        if (callback == null) {
                            values.add(value);
                        } else {
                            return callback.indexInfo(value, null);
                        }
                        return true;

                    case BOTH:
                        final Value[] entry = new Value[2];
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
                        if (callback == null) {
                            values.add(entry[0]);
                            values.add(entry[1]);
                        } else {
                            return callback.indexInfo(value, v);
                        }

                        return true;
                }
            } catch (final IOException e) {
                LOG.error(e.getMessage(), e);
            }

            return false;
        }
    }

    private final class OverflowPage extends DataPage {
        private final SinglePage firstPage;
        private byte[] data = null;

        public OverflowPage(final Txn transaction) throws IOException {
            firstPage = new SinglePage(false);
            if (transaction != null && isRecoveryEnabled()) {
                final Loggable loggable = new OverflowCreateLoggable(fileId, transaction, firstPage.getPageNum());
                writeToLog(loggable, firstPage);
            }
            final BFilePageHeader ph = firstPage.getPageHeader();
            ph.setStatus(MULTI_PAGE);
            ph.setNextInChain(0L);
            ph.setLastInChain(0L);
            ph.setDataLength(0);
            firstPage.setData(new byte[fileHeader.getWorkSize()]);
            dataCache.add(firstPage, 3);
        }

        public OverflowPage(final DataPage page) {
            firstPage = (SinglePage) page;
        }

        public OverflowPage(final Page p, final byte[] data) throws IOException {
            firstPage = new SinglePage(p, data, false);
            firstPage.getPageHeader().setStatus(MULTI_PAGE);
        }

        /**
         * Append a new chunk of data to the page
         *
         * @param transaction the database transaction
         * @param chunk chunk of data to append
         */
        public void append(final Txn transaction, final ByteArray chunk) throws IOException {
            SinglePage nextPage;
            BFilePageHeader ph = firstPage.getPageHeader();
            final int newLen = ph.getDataLength() + chunk.size();
            // get the last page and fill it
            final long next = ph.getLastInChain();
            DataPage page;
            if (next > 0) {
                page = getDataPage(next, false);
            } else {
                page = firstPage;
            }
            ph = page.getPageHeader();
            int chunkSize = fileHeader.getWorkSize() - ph.getDataLength();
            final int chunkLen = chunk.size();
            if (chunkLen < chunkSize) {chunkSize = chunkLen;}
            // fill last page
            if (transaction != null && isRecoveryEnabled()) {
                final Loggable loggable = 
                    new OverflowAppendLoggable(fileId, transaction, page.getPageNum(), chunk, 0, chunkSize);
                writeToLog(loggable, page);
            }
            chunk.copyTo(0, page.getData(), ph.getDataLength(), chunkSize);
            if(page != firstPage) {
                ph.setDataLength(ph.getDataLength() + chunkSize);
            }
            page.setDirty(true);
            // write the remaining chunks to new pages
            int remaining = chunkLen - chunkSize;
            int current = chunkSize;
            chunkSize = fileHeader.getWorkSize();
            if (remaining > 0) {
                // walk through chain of pages
                while (remaining > 0) {
                    if (remaining < chunkSize) {
                        chunkSize = remaining;
                    }
                    
                    // add a new page to the chain
                    nextPage = createDataPage();
                    if (transaction != null && isRecoveryEnabled()) {
                        Loggable loggable = new OverflowCreatePageLoggable(transaction, fileId, nextPage.getPageNum(),
                                page.getPageNum());
                        writeToLog(loggable, nextPage);
                        
                        loggable = new OverflowAppendLoggable(fileId, transaction, nextPage.getPageNum(),
                                chunk, current, chunkSize);
                        writeToLog(loggable, page);
                    }
                    nextPage.setData(new byte[fileHeader.getWorkSize()]);
                    page.getPageHeader().setNextInChain(nextPage.getPageNum());
                    page.setDirty(true);
                    dataCache.add(page);
                    page = nextPage;
                    // copy next chunk of data to the page
                    chunk.copyTo(current, page.getData(), 0, chunkSize);
                    page.setDirty(true);
                    if (page != firstPage) {
                        page.getPageHeader().setDataLength(chunkSize);
                    }
                    remaining = remaining - chunkSize;
                    current += chunkSize;
                }
            }
            ph = firstPage.getPageHeader();
            if (transaction != null && isRecoveryEnabled()) {
                final Loggable loggable = new OverflowModifiedLoggable(fileId, transaction, firstPage.getPageNum(), 
                        ph.getDataLength() + chunkLen, ph.getDataLength(), page == firstPage ? 0 : page.getPageNum());
                writeToLog(loggable, page);
            }
            if (page != firstPage) {
                // add link to last page
                dataCache.add(page);
                ph.setLastInChain(page.getPageNum());
                
            } else {
                ph.setLastInChain(0L);
            }
            // adjust length field in first page
            ph.setDataLength(newLen);
            ByteConversion.intToByte(firstPage.getPageHeader().getDataLength() - 6, firstPage.getData(), 2);
            firstPage.setDirty(true);
            // keep the first page in cache
            dataCache.add(firstPage, 2);
        }

        @Override
        public void delete() throws IOException {
            delete(null);
        }

        public void delete(final Txn transaction) throws IOException {
            long next = firstPage.getPageNum();
            SinglePage page = firstPage;
            do {
                next = page.ph.getNextInChain();
                if (transaction != null && isRecoveryEnabled()) {
                    int dataLen = page.ph.getDataLength();
                    if (dataLen > fileHeader.getWorkSize()) {
                        dataLen = fileHeader.getWorkSize();
                    }
                    final Loggable loggable = new OverflowRemoveLoggable(fileId, transaction, 
                            page.ph.getStatus(), page.getPageNum(),
                            page.getData(), dataLen, 
                            page.ph.getNextInChain());
                    writeToLog(loggable, page);
                }
                
                page.getPageHeader().setNextInChain(-1L);
                page.setDirty(true);
                dataCache.remove(page);
                page.delete();
                if (next > 0) {
                    page = getSinglePage(next);
                }
            } while (next > 0);
        }

        public VariableByteInput getDataStream(final long pointer) {
            final MultiPageInput input = new MultiPageInput(firstPage, pointer);
            return input;
        }

        @Override
        public byte[] getData() throws IOException {
            if (data != null) {
                return data;
            }

            SinglePage page = firstPage;
            long next;
            byte[] temp;
            int len;

            try(final FastByteArrayOutputStream os = new FastByteArrayOutputStream(page.getPageHeader().getDataLength())) {
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
                if (data.length != firstPage.getPageHeader().getDataLength()) {
                    LOG.warn(FileUtils.fileName(getFile()) + " read=" + data.length
                            + "; expected="
                            + firstPage.getPageHeader().getDataLength());
                }
                return data;
            }
        }

        @Override
        public SinglePage getFirstPage() {
            return firstPage;
        }

        @Override
        public BFilePageHeader getPageHeader() {
            return firstPage.getPageHeader();
        }

        @Override
        public String getPageInfo() {
            return "MULTI_PAGE: " + firstPage.getPageInfo();
        }

        @Override
        public long getPageNum() {
            return firstPage.getPageNum();
        }

        @Override
        public void setData(final byte[] buf) {
            setData(null, buf);
        }

        public void setData(final Txn transaction, final byte[] data) {
            this.data = data;
            try {
                write(transaction);
            } catch (final IOException e) {
                LOG.error(e);
            }
        }

        @Override
        public void write() throws IOException {
            write(null);
        }

        public void write(final Txn transaction) throws IOException {
            if (data == null) {
                return;
            }

            int chunkSize = fileHeader.getWorkSize();
            int remaining = data.length;
            int current = 0;
            long next = 0L;
            SinglePage page = firstPage;
            page.getPageHeader().setDataLength(remaining);
            SinglePage nextPage;
            long prevPageNum = Page.NO_PAGE;
            // walk through chain of pages
            while (remaining > 0) {
                if (remaining < chunkSize) {
                    chunkSize = remaining;
                }
                page.clear();
                // copy next chunk of data to the page
                if (transaction != null && isRecoveryEnabled()) {
                    final Loggable loggable = new OverflowStoreLoggable(fileId, transaction, page.getPageNum(), prevPageNum,
                            data, current, chunkSize);
                    writeToLog(loggable, page);
                }
                System.arraycopy(data, current, page.getData(), 0, chunkSize);
                if (page != firstPage) {
                    page.getPageHeader().setDataLength(chunkSize);
                }
                page.setDirty(true);
                remaining -= chunkSize;
                current += chunkSize;
                next = page.getPageHeader().getNextInChain();
                if (remaining > 0) {
                    if (next > 0) {
                        // load next page in chain
                        nextPage = (SinglePage) getDataPage(next, false);
                        dataCache.add(page);
                        prevPageNum = page.getPageNum();
                        page = nextPage;
                    } else {
                        // add a new page to the chain
                        nextPage = createDataPage();
                        if (transaction != null && isRecoveryEnabled()) {
                            final Loggable loggable = new CreatePageLoggable(transaction, fileId, nextPage.getPageNum());
                            writeToLog(loggable, nextPage);
                        }
                        nextPage.setData(new byte[fileHeader.getWorkSize()]);
                        nextPage.getPageHeader().setNextInChain(0L);
                        page.getPageHeader().setNextInChain(
                                nextPage.getPageNum());
                        dataCache.add(page);
                        prevPageNum = page.getPageNum();
                        page = nextPage;
                    }
                } else {
                    page.getPageHeader().setNextInChain(0L);
                    if (page != firstPage) {
                        page.setDirty(true);
                        dataCache.add(page);
                        firstPage.getPageHeader().setLastInChain(
                                page.getPageNum());
                    } else {
                        firstPage.getPageHeader().setLastInChain(0L);
                    }
                    firstPage.setDirty(true);
                    dataCache.add(firstPage, 3);
                }
            }
            if (next > 0) {
                // there are more pages in the chain:
                // remove them
                while (next > 0) {
                    nextPage = (SinglePage) getDataPage(next, false);
                    
                    next = nextPage.getPageHeader().getNextInChain();
                    
                    if (transaction != null && isRecoveryEnabled()) {
                        final Loggable loggable = new OverflowRemoveLoggable(fileId, transaction, 
                                nextPage.getPageHeader().getStatus(), nextPage.getPageNum(),
                                nextPage.getData(), nextPage.getPageHeader().getDataLength(), 
                                nextPage.getPageHeader().getNextInChain());
                        writeToLog(loggable, nextPage);
                    }
                    
                    nextPage.setDirty(true);
                    nextPage.delete();
                    dataCache.remove(nextPage);
                }
            }
            firstPage.getPageHeader().setDataLength(data.length);
            firstPage.setDirty(true);
            dataCache.add(firstPage, 3);
//            LOG.debug(firstPage.getPageNum() + " data length: " + firstPage.ph.getDataLength());
        }

        @Override
        public int findValuePosition(final short tid) throws IOException {
            return 2;
        }

        @Override
        public short getNextTID() {
            return 1;
        }

        @Override
        public void removeTID(final short tid, final int length) {
            //
        }

        @Override
        public void setOffset(final short tid, final int offset) {
            //
        }
    }

    public interface PageInputStream {
        long getAddress();
        long position();
        void seek(long position) throws IOException;
    }

    /**
     * Variable byte input stream to read data from a single page.
     * 
     * @author wolf
     */
    private final static class SimplePageInput extends VariableByteArrayInput
            implements PageInputStream {

        private final long address;
        
        public SimplePageInput(final byte[] data, final int start, final int len, final long address) {
            super(data, start, len);
            this.address = address;
        }

        @Override
        public long getAddress() {
            return address;
        }

        @Override
        public long position() {
            return position;
        }

        @Override
        public void seek(final long pos) throws IOException {
            this.position = (int) pos;
        }
    }

    /**
     * Variable byte input stream to read a multi-page sequences.
     * 
     * @author wolf
     */
    private final class MultiPageInput implements VariableByteInput, PageInputStream {
        private SinglePage nextPage;
        private int pageLen;
        private short offset = 0;
        private final long address;

        public MultiPageInput(SinglePage first, long address) {
            nextPage = first;
            offset = 6;
            pageLen = first.ph.getDataLength();
            if (pageLen > fileHeader.getWorkSize()) {
                pageLen = fileHeader.getWorkSize();
            }
            dataCache.add(first, 3);
            this.address = address;
        }

        @Override
        public long getAddress() {
            return address;
        }

        @Override
        public final int read() throws IOException {
            if (offset == pageLen) {
                advance();
            }
            return nextPage.data[offset++] & 0xFF;
        }

        @Override
        public final byte readByte() throws IOException {
            if (offset == pageLen) {
                advance();
            }
            return nextPage.data[offset++];
        }

        @Override
        public final short readShort() throws IOException {
            if (offset == pageLen) {
                advance();
            }
            byte b = nextPage.data[offset++];
            short i = (short) (b & 0177);
            for (int shift = 7; (b & 0200) != 0; shift += 7) {
                if (offset == pageLen) {
                    advance();
                }
                b = nextPage.data[offset++];
                i |= (b & 0177) << shift;
            }
            return i;
        }

        @Override
        public final int readInt() throws IOException {
            if (offset == pageLen) {
                advance();
            }
            byte b = nextPage.data[offset++];
            int i = b & 0177;
            for (int shift = 7; (b & 0200) != 0; shift += 7) {
                if (offset == pageLen) {
                    advance();
                }
                b = nextPage.data[offset++];
                i |= (b & 0177) << shift;
            }
            return i;
        }

        @Override
        public int readFixedInt() throws IOException {
            if (offset == pageLen) {
                advance();
            }
            // do we have to read across a page boundary?
            if (offset + 4 < pageLen) {
                return ( nextPage.data[offset++] & 0xff ) |
                    ( (nextPage.data[offset++] & 0xff) << 8 ) |
                    ( (nextPage.data[offset++] & 0xff) << 16 ) |
                    ( (nextPage.data[offset++] & 0xff) << 24 );
            }
            int r = nextPage.data[offset++] & 0xff;
            int shift = 8;
            for (int i = 0; i < 3; i++) {
                if (offset == pageLen) {
                    advance();
                }
                r |= (nextPage.data[offset++] & 0xff) << shift;
                shift += 8;
            }
            return r;
        }

        @Override
        public final long readLong() throws IOException {
            if (offset == pageLen) {
                advance();
            }
            byte b = nextPage.data[offset++];
            long i = b & 0177;
            for (int shift = 7; (b & 0200) != 0; shift += 7) {
                if (offset == pageLen) {
                    advance();
                }
                b = nextPage.data[offset++];
                i |= (b & 0177L) << shift;
            }
            return i;
        }

        @Override
        public final void skip(final int count) throws IOException {
            for (int i = 0; i < count; i++) {
                do {
                    if (offset == pageLen) {
                        advance();
                    }
                } while ((nextPage.data[offset++] & 0200) > 0);
            }
        }

        @Override
        public final void skipBytes(final long count) throws IOException {
            for(long i = 0; i < count; i++) {
                if (offset == pageLen) {
                    advance();
                }
                offset++;
            }
        }

        private final void advance() throws IOException {
            final long next = nextPage.getPageHeader().getNextInChain();
            if (next < 1) {
                pageLen = -1;
                offset = 0;
                throw new EOFException();
            }


            try(final ManagedLock<ReentrantLock> bfileLock = lockManager.acquireBtreeReadLock(getLockName())) {
                nextPage = (SinglePage) getDataPage(next, false);
                pageLen = nextPage.ph.getDataLength();
                offset = 0;
                dataCache.add(nextPage);
            } catch (final LockException e) {
                throw new IOException("failed to acquire a read lock on "
                        + FileUtils.fileName(getFile()));
            }
        }

        @Override
        public final int available() throws IOException {
            if (pageLen < 0) {
                return 0;
            }
            int inPage = pageLen - offset;
            if (inPage == 0) {
                inPage = nextPage.getPageHeader().getNextInChain() > 0 ? 1 : 0;
            }
            return inPage;
        }

        @Override
        public final int read(final byte[] data) throws IOException {
            return read(data, 0, data.length);
        }

        @Override
        public final int read(final byte[] b, final int off, final int len) throws IOException {
            if (pageLen < 0) {
                return -1;
            }

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

        @Override
        public final String readUTF() throws IOException {
            final int len = readInt();
            final byte data[] = new byte[len];

            read(data);

            return new String(data, UTF_8);
        }

        @Override
        public final void copyTo(final VariableByteOutputStream os) throws IOException {
            byte more;
            do {
                if (offset == pageLen) {
                    advance();
                }
                more = nextPage.data[offset++];
                os.writeByte(more);
                more &= 0200;
            } while (more > 0);
        }

        @Override
        public final void copyTo(final VariableByteOutputStream os, final int count) throws IOException {
            byte more;
            for (int i = 0; i < count; i++) {
                do {
                    if (offset == pageLen) {
                        advance();
                    }
                    more = nextPage.data[offset++];
                    os.writeByte(more);
                } while ((more & 0x200) > 0);
            }
        }

        @Override
        public void copyRaw(final VariableByteOutputStream os, final int count) throws IOException {
            for (int i = count; i != 0; ) {
                if (offset == pageLen) {
                    advance();
                }
                int avail = pageLen - offset;
                if (i >= avail) {
                    os.write(nextPage.data, offset, avail);
                    i -= avail;
                    offset = (short) pageLen;
                } else {
                    os.write(nextPage.data, offset, i);
                    offset += i;
                    break;
                }
                //os.writeByte(nextPage.data[offset++]);
            }
        }

        @Override
        public long position() {
            return StorageAddress.createPointer((int) nextPage.getPageNum(), offset);
        }

        @Override
        public void seek(final long position) throws IOException {
            final int newPage = StorageAddress.pageFromPointer(position);
            final short newOffset = StorageAddress.tidFromPointer(position);
            try(final ManagedLock<ReentrantLock> bfileLock =  lockManager.acquireBtreeReadLock(getLockName())) {
                nextPage = getSinglePage(newPage);
                pageLen = nextPage.ph.getDataLength();
                if (pageLen > fileHeader.getWorkSize()) {
                    pageLen = fileHeader.getWorkSize();
                }
                offset = newOffset;
                dataCache.add(nextPage);
            } catch (final LockException e) {
                throw new IOException("Failed to acquire a read lock on " + FileUtils.fileName(getFile()));
            }
        }
    }

    /**
     * Represents a single data page (as opposed to a overflow page).
     * 
     * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
     */
    private final class SinglePage extends DataPage {

        // the raw working data of this page (without page header)
        byte[] data = null;

        // the low-level page
        final Page page;

        // the page header
        final BFilePageHeader ph;

        // table mapping record ids (tids) to offsets
        short[] offsets = null;

        public SinglePage() throws IOException {
            this(true);
        }

        public SinglePage(final boolean compress) throws IOException {
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

        public SinglePage(final Page p, final byte[] data, final boolean initialize) throws IOException {
            if (p == null) {
                throw new IOException("illegal page");
            }

            if (!(p.getPageHeader().getStatus() == RECORD || p.getPageHeader()
                    .getStatus() == MULTI_PAGE)) {
                final IOException e = new IOException("not a data-page: "
                        + p.getPageHeader().getStatus());
                LOG.debug("not a data-page: " + p.getPageInfo(), e);
                throw e;
            }
            this.data = data;
            page = p;
            ph = (BFilePageHeader) page.getPageHeader();
            if(initialize) {
                offsets = new short[ph.nextTID];
                if (ph.getStatus() != MULTI_PAGE) {
                    readOffsets();
                }
            }
        }

        @Override
        public final int findValuePosition(final short tid) throws IOException {
            return offsets[tid];
        }

        private void readOffsets() {
            //if(offsets.length > 256)
                //LOG.warn("TID size: " + ph.nextTID);
            Arrays.fill(offsets, (short)-1);
            final int dlen = ph.getDataLength();
            for(short pos = 0; pos < dlen; ) {
                final short tid = ByteConversion.byteToShort(data, pos);
                if (tid < 0) {
                    LOG.error("Invalid tid found: " + tid + "; ignoring rest of page ...");
                    ph.setDataLength(pos);
                    return;
                }
                if(tid >= offsets.length) {
                    LOG.error("Problematic tid found: " + tid + "; trying to recover ...");
                    final short[] t = new short[tid + 1];
                    Arrays.fill(t, (short)-1);
                    System.arraycopy(offsets, 0, t, 0, offsets.length);
                    offsets = t;
                    ph.nextTID = (short)(tid + 1);
                }
                offsets[tid] = (short)(pos + 2);
                pos += ByteConversion.byteToInt(data, pos + 2) + 6;
            }
        }

        @Override
        public short getNextTID() {
            for(short i = 0; i < offsets.length; i++) {
                if(offsets[i] == -1) {
                    return i;
                }
            }
            final short tid = (short)offsets.length;
            final short next = (short)(ph.nextTID * 2);
            if(next < 0 || next < ph.nextTID) {
                return -1;
            }
            final short[] t = new short[next];
            Arrays.fill(t, (short)-1);
            System.arraycopy(offsets, 0, t, 0, offsets.length);
            offsets = t;
            ph.nextTID = next;
            return tid;
        }

        public void adjustTID(final short tid) {
            if (tid >= ph.nextTID) {
                final short next = (short)(tid * 2);
                final short[] t = new short[next];
                Arrays.fill(t, (short)-1);
                System.arraycopy(offsets, 0, t, 0, offsets.length);
                offsets = t;
                ph.nextTID = next;
            }
        }

        public void clear() {
            Arrays.fill(data, (byte) 0);
        }

        private String printContents() {
            final StringBuilder buf = new StringBuilder();
            for(short i = 0; i < offsets.length; i++) {
                if (offsets[i] > -1) {
                    buf.append('[').append(i).append(", ").append(offsets[i]);
                    final short len = ByteConversion.byteToShort(data, offsets[i]);
                    buf.append(", ").append(len).append(']');
                }
            }
            return buf.toString();
        }

        @Override
        public void setOffset(final short tid, final int offset) {
            if (offsets == null) {
                LOG.warn("page: " + page.getPageNum() + " file: " + FileUtils.fileName(getFile()) + " status: " +
                    getPageHeader().getStatus());
                throw new RuntimeException("page offsets not initialized");
            }
            offsets[tid] = (short)offset;
        }
        
        @Override
        public void removeTID(final short tid, final int length) throws IOException {
            final int offset = offsets[tid] - 2;
            offsets[tid] = -1;
            for(short i = 0; i < offsets.length; i++) {
                if(offsets[i] > offset) {
                    offsets[i] -= length;
                }
            }
            //readOffsets(start);
        }

        @Override
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

        @Override
        public SinglePage getFirstPage() {
            return this;
        }

        @Override
        public byte[] getData() {
            return data;
        }

        @Override
        public BFilePageHeader getPageHeader() {
            return ph;
        }

        @Override
        public String getPageInfo() {
            return page.getPageInfo();
        }

        @Override
        public long getPageNum() {
            return page.getPageNum();
        }

        @Override
        public void setData(final byte[] buf) {
            data = buf;
        }

        @Override
        public void write() throws IOException {
            //LOG.debug(getPath().getName() + " writing page " + getPageNum());
            writeValue(page, new Value(data));
            setDirty(false);
        }
    }
}
