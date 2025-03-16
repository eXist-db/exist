/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage.dom;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.storage.btree.Paged.Page.NO_PAGE;

import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.AttrImpl;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.ElementImpl;
import org.exist.dom.persistent.IStoredNode;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.StoredNode;
import org.exist.numbering.DLNBase;
import org.exist.numbering.NodeId;
import org.exist.stax.ExtendedXMLStreamReader;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.storage.BrokerPool;
import org.exist.storage.BufferStats;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.NativeBroker.NodeRef;
import org.exist.storage.Signatures;
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
import org.exist.storage.journal.JournalException;
import org.exist.storage.journal.LogEntryTypes;
import org.exist.storage.journal.Loggable;
import org.exist.storage.journal.Lsn;
import org.exist.storage.lock.LockManager;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.util.sanity.SanityCheck;
import org.exist.xquery.TerminatedException;
import org.w3c.dom.Node;

/**
 * This is the main storage for XML nodes. Nodes are stored in document order.
 * Every document gets its own sequence of pages, which is bound to the writing
 * thread to avoid conflicting writes. The page structure is as follows:
 *  | page header | (tid1 node-data, tid2 node-data, ..., tidn node-data) |
 * 
 * node-data contains the raw binary data of the node. Within a page, a node is
 * identified by a unique id, called tuple id (tuple id). Every node can thus be
 * located by a virtual address pointer, which consists of the page id and the
 * tid. Both components are encoded in a long value (with additional bits used
 * for optional flags). The address pointer is used to reference nodes from the
 * indexes. It should thus remain unchanged during the life-time of a document.
 * 
 * However, XUpdate requests may insert new nodes in the middle of a page. In
 * these cases, the page will be split and the upper portion of the page is
 * copied to a split page. The record in the original page will be replaced by a
 * forward link, pointing to the new location of the node data in the split
 * page.
 * 
 * As a consequence, the class has to distinguish three different types of data
 * records:
 * 
 * 1) Ordinary record:
 *  | tuple id | length | data |
 * 
 * 3) Relocated record:
 *  | tuple id | length | address pointer to original location | data |
 * 
 * 2) Forward link:
 *  | tuple id | address pointer |
 * 
 * tuple id and length each use two bytes (short), address pointers 8 bytes (long).
 * The upper two bits of the tuple id are used to indicate the type of the record
 * (see {@link org.exist.storage.dom.ItemId}).
 * 
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class DOMFile extends BTree implements Lockable {

    private static final Logger LOGSTATS = LogManager.getLogger(NativeBroker.EXIST_STATISTICS_LOGGER);

    public static final String FILE_NAME = "dom.dbx";
    public static final String CONFIG_KEY_FOR_FILE = "db-connection.dom";

    static final int LENGTH_TID = 2; //sizeof short
    static final int LENGTH_DATA_LENGTH = 2; //sizeof short
    static final int LENGTH_LINK = 8; //sizeof long
    static final int LENGTH_ORIGINAL_LOCATION = LENGTH_LINK;
    static final int LENGTH_FORWARD_LOCATION = LENGTH_LINK;
    static final int LENGTH_OVERFLOW_LOCATION = LENGTH_LINK;

    /*
     * Byte ids for the records written to the log file.
     */
    static final byte LOG_CREATE_PAGE = 0x10;
    static final byte LOG_ADD_VALUE = 0x11;
    static final byte LOG_REMOVE_VALUE = 0x12;
    static final byte LOG_REMOVE_EMPTY_PAGE = 0x13;
    static final byte LOG_UPDATE_VALUE = 0x14;
    static final byte LOG_REMOVE_PAGE = 0x15;
    static final byte LOG_WRITE_OVERFLOW = 0x16;
    static final byte LOG_REMOVE_OVERFLOW = 0x17;
    static final byte LOG_INSERT_RECORD = 0x18;
    static final byte LOG_SPLIT_PAGE = 0x19;
    static final byte LOG_ADD_LINK = 0x1A;
    static final byte LOG_ADD_MOVED_REC = 0x1B;
    static final byte LOG_UPDATE_HEADER = 0x1C;
    static final byte LOG_UPDATE_LINK = 0x1D;

    static {
        // register log entry types for this db file
        LogEntryTypes.addEntryType(LOG_CREATE_PAGE, CreatePageLoggable::new);
        LogEntryTypes.addEntryType(LOG_ADD_VALUE, AddValueLoggable::new);
        LogEntryTypes.addEntryType(LOG_REMOVE_VALUE, RemoveValueLoggable::new);
        LogEntryTypes.addEntryType(LOG_REMOVE_EMPTY_PAGE, RemoveEmptyPageLoggable::new);
        LogEntryTypes.addEntryType(LOG_UPDATE_VALUE, UpdateValueLoggable::new);
        LogEntryTypes.addEntryType(LOG_REMOVE_PAGE, RemovePageLoggable::new);
        LogEntryTypes.addEntryType(LOG_WRITE_OVERFLOW, WriteOverflowPageLoggable::new);
        LogEntryTypes.addEntryType(LOG_REMOVE_OVERFLOW, RemoveOverflowLoggable::new);
        LogEntryTypes.addEntryType(LOG_INSERT_RECORD, InsertValueLoggable::new);
        LogEntryTypes.addEntryType(LOG_SPLIT_PAGE, SplitPageLoggable::new);
        LogEntryTypes.addEntryType(LOG_ADD_LINK, AddLinkLoggable::new);
        LogEntryTypes.addEntryType(LOG_ADD_MOVED_REC, AddMovedValueLoggable::new);
        LogEntryTypes.addEntryType(LOG_UPDATE_HEADER, UpdateHeaderLoggable::new);
        LogEntryTypes.addEntryType(LOG_UPDATE_LINK, UpdateLinkLoggable::new);
    }

    public static final short FILE_FORMAT_VERSION_ID = 10;

    private final LockManager lockManager;

    //Page types
    static final byte LOB = 21;
    static final byte RECORD = 20;
    //Data length for overflow pages
    static final short OVERFLOW = 0;

    static final long DATA_SYNC_PERIOD = 4200;

    private final Cache<DOMPage> dataCache;

    private final BTreeFileHeader fileHeader;

    private Object owner = null;

    private final Reference2LongMap<Object> pages;

    private DocumentImpl currentDocument = null;

    private final AddValueLoggable addValueLog = new AddValueLoggable();

    public DOMFile(final BrokerPool pool, final byte id, final Path dataDir, final Configuration config) throws DBException {
        super(pool, id, FILE_FORMAT_VERSION_ID, true, pool.getCacheManager());
        this.lockManager = pool.getLockManager();
        this.pages = new Reference2LongOpenHashMap<>(64);
        this.pages.defaultReturnValue(NO_PAGE);
        fileHeader = (BTreeFileHeader)getFileHeader();
        fileHeader.setPageCount(0);
        fileHeader.setTotalCount(0);
        dataCache = new LRUCache<>(getFileName(), 256, 0.0, 1.0, Cache.CacheType.DATA);
        cacheManager.registerCache(dataCache);
        final Path file = dataDir.resolve(getFileName());
        setFile(file);
        if (exists()) {
            open();
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Creating data file: {}", FileUtils.fileName(file));
            }
            create();
        }
        config.setProperty(getConfigKeyForFile(), this);
    }

    /**
     * Set the current page.
     * 
     * @param page The new page
     */
    private void setCurrentPage(final DOMPage page) {
        final long pageNum = pages.getLong(owner);
        if (pageNum == page.page.getPageNum()) {
            return;
        }
        pages.put(owner, page.page.getPageNum());
    }

    /**
     * Retrieve the last page in the current sequence.
     * 
     * @return The current page
     */
    private DOMPage getCurrentPage(final Txn transaction) {
        final long pageNum = pages.getLong(owner);
        if (pageNum == NO_PAGE) {
            final DOMPage page = new DOMPage();
            pages.put(owner, page.page.getPageNum());
            dataCache.add(page);
            if (transaction != null && isRecoveryEnabled()) {
                final CreatePageLoggable loggable = new CreatePageLoggable(
                    transaction, NO_PAGE, page.getPageNum(), NO_PAGE);
                writeToLog(loggable, page.page);
            }
            return page;
        } else {
            return getDOMPage(pageNum);
        }
    }

    /**
     * Retrieve the current page
     * 
     * @param pointer Description of the Parameter
     * @return The current page
     */
    DOMPage getDOMPage(final long pointer) {
        DOMPage page = dataCache.get(pointer);
        if (page == null) {
            page = new DOMPage(pointer);
        }
        return page;
    }

    /**
     * Open the file.
     * 
     * @return Description of the Return Value
     * @throws DBException   Description of the Exception
     */
    private boolean open() throws DBException {
        return super.open(FILE_FORMAT_VERSION_ID);
    }

    public void closeDocument() {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLocked(getLockName())) {
            LOG.debug("The file doesn't own a lock");
        }
        pages.removeLong(owner);
    }

    public static String getFileName() {
        return FILE_NAME;
    }

    public static String getConfigKeyForFile() {
        return CONFIG_KEY_FOR_FILE;
    }

    void addToBuffer(final DOMPage page) {
        if (LOG.isDebugEnabled() && !lockManager.isBtreeLocked(getLockName())) {
            LOG.debug("The file doesn't own a lock");
        }
        dataCache.add(page);
    }

    @Override
    public boolean create() throws DBException {
        return super.create((short) -1);
    }

    @Override
    public void close() throws DBException {
        if (!isReadOnly()) {
            flush();
        }
        if (LOG.isDebugEnabled() && !lockManager.isBtreeLockedForWrite(getLockName())) {
            LOG.debug("The file doesn't own a write lock");
        }
        super.close();
        cacheManager.deregisterCache(dataCache);
    }

    void setCurrentDocument(final DocumentImpl doc) {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLocked(getLockName())) {
            LOG.debug("The file doesn't own a lock");
        }
        this.currentDocument = doc;
    }

    /**
     * Append a value to the current page.
     * 
     * This method is called when storing a new document. Each writing thread
     * gets its own sequence of pages for writing a document, so all document
     * nodes are stored in sequential order. A new page will be allocated if the
     * current page is full. If the value is larger than the page size, it will
     * be written to an overflow page.
     *
     * @param transaction the database transaction
     * @param value the value to append
     * @return the virtual storage address of the value
     *
     * @throws ReadOnlyException if the DOM file is read-only
     */
    public long add(final Txn transaction, final byte[] value) throws ReadOnlyException {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLockedForWrite(getLockName())) {
            LOG.debug("The file doesn't own a write lock");
        }

        if (value == null || value.length == 0) {
            return KEY_NOT_FOUND;
        }

        // overflow value?
        if (value.length + LENGTH_TID + LENGTH_DATA_LENGTH > fileHeader.getWorkSize()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Creating overflow page");
            }
            final OverflowDOMPage overflowPage = new OverflowDOMPage();
            overflowPage.write(transaction, value);
            final byte[] pageNum = ByteConversion.longToByte(overflowPage.getPageNum());
            return add(transaction, pageNum, true);
        } else {
            return add(transaction, value, false);
        }
    }

    /**
     * Append a value to the current page. If overflowPage is true, the value
     * will be saved into its own, reserved chain of pages. The current page
     * will just contain a link to the first overflow page.
     * 
     * @param value the value
     * @param overflowPage the overflow page
     * @return the virtual storage address of the value
     * @throws ReadOnlyException if the DOMFile is read-only
     */
    private long add(final Txn transaction, final byte[] value, final boolean overflowPage) throws ReadOnlyException {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLockedForWrite(getLockName())) {
            LOG.debug("The file doesn't own a write lock");
        }
        final int valueLength = value.length;
        //Always append data to the end of the file
        DOMPage currentPage = getCurrentPage(transaction);
        //Does the value fit into current data page?
        if (currentPage.len + LENGTH_TID + LENGTH_DATA_LENGTH + valueLength > currentPage.data.length) {
            final DOMPage newPage = new DOMPage();
            final DOMFilePageHeader currentPageHeader = currentPage.getPageHeader();
            if (transaction != null && isRecoveryEnabled()) {
                final UpdateHeaderLoggable loggable = new UpdateHeaderLoggable(
                    transaction, currentPageHeader.getPreviousDataPage(), currentPage.getPageNum(),
                    newPage.getPageNum(), currentPageHeader.getPreviousDataPage(), 
                    currentPageHeader.getNextDataPage());
                writeToLog(loggable, currentPage.page);
            }
            currentPageHeader.setNextDataPage(newPage.getPageNum());
            newPage.getPageHeader().setPrevDataPage(currentPage.getPageNum());
            currentPage.setDirty(true);
            dataCache.add(currentPage);
            if (transaction != null && isRecoveryEnabled()) {
                final CreatePageLoggable loggable = new CreatePageLoggable(
                    transaction, currentPage.getPageNum(),
                    newPage.getPageNum(), NO_PAGE);
                writeToLog(loggable, newPage.page);
            }
            currentPage = newPage;
            setCurrentPage(newPage);
        }
        final DOMFilePageHeader currentPageHeader = currentPage.getPageHeader();
        final short tupleID = currentPageHeader.getNextTupleID();
        if (transaction != null && isRecoveryEnabled()) {
                addValueLog.clear(transaction, currentPage.getPageNum(), tupleID, value, overflowPage);
            writeToLog(addValueLog, currentPage.page);
        }
        //Save tuple identifier
        ByteConversion.shortToByte(tupleID, currentPage.data, currentPage.len);
        currentPage.len += LENGTH_TID;
        //Save data length
        ByteConversion.shortToByte(overflowPage ? OVERFLOW : (short) valueLength,
            currentPage.data, currentPage.len);
        currentPage.len += LENGTH_DATA_LENGTH;
        //Save data
        System.arraycopy(value, 0, currentPage.data, currentPage.len, valueLength);
        currentPage.len += valueLength;
        currentPageHeader.incRecordCount();
        currentPageHeader.setDataLength(currentPage.len);
        currentPage.setDirty(true);
        dataCache.add(currentPage, 2);
        // return pointer from pageNum and offset into page
        return StorageAddress.createPointer((int)currentPage.getPageNum(), tupleID);
    }

    private void writeToLog(final Loggable loggable, final Page page) {
        if(logManager.isPresent()) {
            try {
                logManager.get().journal(loggable);
                page.getPageHeader().setLsn(loggable.getLsn());
            } catch (final JournalException e) {
                LOG.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Store a raw binary resource into the file. The data will always be
     * written into an overflow page.
     *
     * @param transaction the database transaction
     * @param doc the document to add
     * @param value Binary resource as byte array
     *
     * @return the page number
     */
    public long addBinary(final Txn transaction, final DocumentImpl doc, final byte[] value) {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLockedForWrite(getLockName())) {
            LOG.debug("The file doesn't own a write lock");
        }
        final OverflowDOMPage overflowPage = new OverflowDOMPage();
        final int pagesCount = overflowPage.write(transaction, value);
        doc.setPageCount(pagesCount);
        return overflowPage.getPageNum();
    }

    /**
     * Store a raw binary resource into the file. The data will always be
     * written into an overflow page.
     *
     * @param transaction the transaction
     * @param doc the document to add
     * @param is Binary resource as stream.
     *
     * @return the page number
     */
    public long addBinary(final Txn transaction, final DocumentImpl doc, final InputStream is) {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLockedForWrite(getLockName())) {
            LOG.debug("The file doesn't own a write lock");
        }
        final OverflowDOMPage overflowPage = new OverflowDOMPage();
        final int pagesCount = overflowPage.write(transaction, is);
        doc.setPageCount(pagesCount);
        return overflowPage.getPageNum();
    }

    /**
     * Return binary data stored with {@link #addBinary(Txn, DocumentImpl, byte[])}.
     * 
     * @param pageNum the page number
     * @return binary data stored
     */
    public byte[] getBinary(final long pageNum) {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLocked(getLockName())) {
            LOG.debug("The file doesn't own a lock");
        }
        return getOverflowValue(pageNum);
    }

    public void readBinary(final long pageNum, final OutputStream os) {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLocked(getLockName())) {
            LOG.debug("The file doesn't own a lock");
        }
        try {
            final OverflowDOMPage overflowPage = new OverflowDOMPage(pageNum);
            overflowPage.streamTo(os);
        } catch (final IOException e) {
            LOG.error("IO error while loading overflow value", e);
        }
    }

    /**
     * Insert a new node after the specified node.
     *
     * @param transaction the database transaction
     * @param doc the document
     * @param key the key
     * @param value the value
     *
     * @return the storage address pointer
     */
    public long insertAfter(final Txn transaction, final DocumentImpl doc, final Value key, final byte[] value) {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLockedForWrite(getLockName())) {
            LOG.debug("The file doesn't own a write lock");
        }
        try {
            final long address = findValue(key);
            if (address == KEY_NOT_FOUND) {
                LOG.warn("Couldn't find the value");
                return KEY_NOT_FOUND;
            }
            return insertAfter(transaction, doc, address, value);
        } catch (final BTreeException e) {
            LOG.warn("key not found", e);
        } catch (final IOException e) {
            LOG.error("IO error", e);
        }
        return KEY_NOT_FOUND;
    }

    /**
     * Insert a new node after the node located at the specified address.
     * 
     * If the previous node is in the middle of a page, the page is split. If
     * the node is appended at the end and the page does not have enough room
     * for the node, a new page is added to the page sequence.
     *
     * @param transaction the database transaction
     * @param doc       the document to which the new node belongs.
     * @param address   the storage address of the node after which the 
     *                  new value should be inserted.
     * @param value     the value of the new node.
     *
     * @return the storage address pointer
     */
    public long insertAfter(final Txn transaction, final DocumentImpl doc, final long address, byte[] value) {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLockedForWrite(getLockName())) {
            LOG.debug("The file doesn't own a write lock");
        }
        // check if we need an overflow page
        boolean isOverflow = false;
        if (LENGTH_TID + LENGTH_DATA_LENGTH + value.length > fileHeader.getWorkSize()) {
            final OverflowDOMPage overflowPage = new OverflowDOMPage();
            LOG.debug("Creating overflow page: {}", overflowPage.getPageNum());
            overflowPage.write(transaction, value);
            value = ByteConversion.longToByte(overflowPage.getPageNum());
            isOverflow = true;
        }
        // locate the node to insert after
        RecordPos rec = findRecord(address);
        if (rec == null) {
            SanityCheck.TRACE("Page not found");
            return KEY_NOT_FOUND;
        }
        final short vlen = ByteConversion.byteToShort(rec.getPage().data, rec.offset);
        rec.offset += LENGTH_DATA_LENGTH;
        if (ItemId.isRelocated(rec.getTupleID())) {
            rec.offset += LENGTH_ORIGINAL_LOCATION;
        }
        if (vlen == OVERFLOW) {
            rec.offset += LENGTH_OVERFLOW_LOCATION;
        } else {
            rec.offset += vlen;
        }
        //OK : we now have an offset for the new node
        final int dataLength = rec.getPage().getPageHeader().getDataLength();
        //Can we insert in the middle of the page?
        if (rec.offset < dataLength) {
            //New value fits into the page
            if (dataLength + LENGTH_TID + LENGTH_DATA_LENGTH + value.length <= fileHeader.getWorkSize()
                && rec.getPage().getPageHeader().hasRoom()) {
                final int end = rec.offset + LENGTH_TID + LENGTH_DATA_LENGTH + value.length;
                System.arraycopy(rec.getPage().data, rec.offset, rec.getPage().data, end,
                    dataLength - rec.offset);
                rec.getPage().len = dataLength + LENGTH_TID + LENGTH_DATA_LENGTH + value.length;
                rec.getPage().getPageHeader().setDataLength(rec.getPage().len);
            //Doesn't fit: split the page
            } else {
                rec = splitDataPage(transaction, rec);
                //Still not enough free space: create a new page
                if (rec.offset + LENGTH_TID + LENGTH_DATA_LENGTH + 
                        value.length > fileHeader.getWorkSize() ||
                        !rec.getPage().getPageHeader().hasRoom()) {
                    final DOMPage newPage = new DOMPage();
                    final DOMFilePageHeader newPageHeader = newPage.getPageHeader();
                    LOG.debug("creating additional page: {}; prev = {}; next = {}", newPage.getPageNum(), rec.getPage().getPageNum(), rec.getPage().getPageHeader().getNextDataPage());
                    if (transaction != null && isRecoveryEnabled()) {
                        final CreatePageLoggable loggable = new CreatePageLoggable(
                            transaction, rec.getPage().getPageNum(),
                            newPage.getPageNum(), rec.getPage().getPageHeader().getNextDataPage());
                        writeToLog(loggable, newPage.page);
                    }
                    //Adjust page links
                    newPageHeader.setNextDataPage(rec.getPage().getPageHeader().getNextDataPage());
                    newPageHeader.setPrevDataPage(rec.getPage().getPageNum());
                    if (transaction != null && isRecoveryEnabled()) {
                        final UpdateHeaderLoggable loggable = new UpdateHeaderLoggable(
                            transaction, rec.getPage().getPageHeader().getPreviousDataPage(), 
                            rec.getPage().getPageNum(), newPage.getPageNum(), 
                            rec.getPage().getPageHeader().getPreviousDataPage(), 
                            rec.getPage().getPageHeader().getNextDataPage());
                        writeToLog(loggable, rec.getPage().page);
                    }
                    rec.getPage().getPageHeader().setNextDataPage(newPage.getPageNum());
                    if (newPageHeader.getNextDataPage() != NO_PAGE) {
                        //Link the next page in the chain back to the new page inserted 
                        final DOMPage nextPage = getDOMPage(newPageHeader.getNextDataPage());
                        final DOMFilePageHeader nextPageHeader = nextPage.getPageHeader();
                        if (transaction != null && isRecoveryEnabled()) {
                            final UpdateHeaderLoggable loggable = new UpdateHeaderLoggable(
                                transaction, newPage.getPageNum(), nextPage.getPageNum(), 
                                nextPageHeader.getNextDataPage(), nextPageHeader.getPreviousDataPage(), 
                                nextPageHeader.getNextDataPage());
                            writeToLog(loggable, nextPage.page);
                        }
                        nextPageHeader.setPrevDataPage(newPage.getPageNum());
                        nextPage.setDirty(true);
                        dataCache.add(nextPage);
                    }
                    rec.getPage().setDirty(true);
                    dataCache.add(rec.getPage());
                    //Switch record to new page...
                    rec.setPage(newPage);
                    rec.offset = 0;
                    rec.getPage().len = LENGTH_TID + LENGTH_DATA_LENGTH + value.length;
                    rec.getPage().getPageHeader().setDataLength(rec.getPage().len);
                //enough space in split page
                } else {
                    rec.getPage().len = rec.offset + LENGTH_TID + LENGTH_DATA_LENGTH + value.length;
                    rec.getPage().getPageHeader().setDataLength(rec.getPage().len);
                }
            }
        //The value doesn't fit into page : create new page
        } else if (dataLength + LENGTH_TID + LENGTH_DATA_LENGTH + value.length > 
            fileHeader.getWorkSize() || !rec.getPage().getPageHeader().hasRoom()) {
            final DOMPage newPage = new DOMPage();
            final DOMFilePageHeader newPageHeader = newPage.getPageHeader();
            LOG.debug("Creating new page: {}", newPage.getPageNum());
            if (transaction != null && isRecoveryEnabled()) {
                final CreatePageLoggable loggable = new CreatePageLoggable(
                    transaction, rec.getPage().getPageNum(),
                    newPage.getPageNum(), rec.getPage().getPageHeader().getNextDataPage());
                writeToLog(loggable, newPage.page);
            }
            final long nextPageNum = rec.getPage().getPageHeader().getNextDataPage();
            newPageHeader.setNextDataPage(nextPageNum);
            newPageHeader.setPrevDataPage(rec.getPage().getPageNum());
            if (transaction != null && isRecoveryEnabled()) {
                final DOMFilePageHeader pageHeader = rec.getPage().getPageHeader();
                final UpdateHeaderLoggable loggable = 
                    new UpdateHeaderLoggable(transaction, pageHeader.getPreviousDataPage(), 
                        rec.getPage().getPageNum(), newPage.getPageNum(), 
                        pageHeader.getPreviousDataPage(), pageHeader.getNextDataPage());
                writeToLog(loggable, rec.getPage().page);
            }
            rec.getPage().getPageHeader().setNextDataPage(newPage.getPageNum());
            if (nextPageNum != NO_PAGE) {
                final DOMPage nextPage = getDOMPage(nextPageNum);
                final DOMFilePageHeader nextPageHeader = nextPage.getPageHeader();
                if (transaction != null && isRecoveryEnabled()) {
                    final UpdateHeaderLoggable loggable = 
                        new UpdateHeaderLoggable(transaction, newPage.getPageNum(), 
                            nextPage.getPageNum(), nextPageHeader.getNextDataPage(), 
                            nextPageHeader.getPreviousDataPage(), nextPageHeader.getNextDataPage());
                    writeToLog(loggable, nextPage.page);
                }
                nextPageHeader.setPrevDataPage(newPage.getPageNum());
                nextPage.setDirty(true);
                dataCache.add(nextPage);
            }
            rec.getPage().setDirty(true);
            dataCache.add(rec.getPage());
            //Switch record to new page
            rec.setPage(newPage);
            rec.offset = 0;
            rec.getPage().len = LENGTH_TID + LENGTH_DATA_LENGTH + value.length;
            rec.getPage().getPageHeader().setDataLength(rec.getPage().len);
        //Append the value
        } else {
            rec.getPage().len = dataLength + LENGTH_TID + LENGTH_DATA_LENGTH + value.length;
            rec.getPage().getPageHeader().setDataLength(rec.getPage().len);
        }
        final short tupleID = rec.getPage().getPageHeader().getNextTupleID();
        if (transaction != null && isRecoveryEnabled()) {
            final Loggable loggable = new InsertValueLoggable(transaction, rec.getPage().getPageNum(), isOverflow, tupleID, value, rec.offset);
            writeToLog(loggable, rec.getPage().page);
        }
        //Write tid
        ByteConversion.shortToByte(tupleID, rec.getPage().data, rec.offset);
        rec.offset += LENGTH_TID;
        //Write value length
        ByteConversion.shortToByte(isOverflow ? OVERFLOW : (short) value.length,
            rec.getPage().data, rec.offset);
        rec.offset += LENGTH_DATA_LENGTH;
        //Write data
        System.arraycopy(value, 0, rec.getPage().data, rec.offset, value.length);
        rec.offset += value.length;
        rec.getPage().getPageHeader().incRecordCount();
        if (doc != null && rec.getPage().getPageHeader().getCurrentTupleID() >=
            ItemId.DEFRAG_LIMIT) {
            doc.triggerDefrag();
        }
        rec.getPage().setDirty(true);
        dataCache.add(rec.getPage());
        return StorageAddress.createPointer((int)rec.getPage().getPageNum(), tupleID);
    }

    /**
     * Split a data page at the position indicated by the rec parameter.
     * 
     * The portion of the page starting at rec.offset is moved into a new page.
     * Every moved record is marked as relocated and a link is stored into the
     * original page to point to the new record position.
     *
     * @param transaction the database transaction
     * @param rec the record position
     *
     * @return the updated record position
     */
    private RecordPos splitDataPage(final Txn transaction, final RecordPos rec) {
        if (currentDocument != null) {
            currentDocument.incSplitCount();
        }
        //Check if a split is really required. A split is not required if
        //all records following the split point are already links to other pages.
        //In this case, the new record is just appended to a new page linked to the old one.
        boolean requireSplit = false;
        for (int pos = rec.offset; pos < rec.getPage().len;) {
            final short tupleID = ByteConversion.byteToShort(rec.getPage().data, pos);
            pos += LENGTH_TID;
            if (!ItemId.isLink(tupleID)) {
                requireSplit = true;
                break;
            }
            pos += LENGTH_FORWARD_LOCATION;
        }
        if (!requireSplit) {
            LOG.debug("page: {}: no split required. Next page:{} Previous page:{}", rec.getPage().getPageNum(), rec.getPage().getPageHeader().getNextDataPage(), rec.getPage().getPageHeader().getPreviousDataPage());
            rec.offset = rec.getPage().len;
            return rec;
        }
        final DOMFilePageHeader pageHeader = rec.getPage().getPageHeader();
        //Copy the old data up to the split point into a new array
        final int oldDataLen = pageHeader.getDataLength();
        final byte[] oldData = rec.getPage().data;
        if (transaction != null && isRecoveryEnabled()) {
            final Loggable loggable = new SplitPageLoggable(transaction, 
                rec.getPage().getPageNum(), rec.offset, oldData, oldDataLen);
            writeToLog(loggable, rec.getPage().page);
        }
        rec.getPage().data = new byte[fileHeader.getWorkSize()];
        System.arraycopy(oldData, 0, rec.getPage().data, 0, rec.offset);
        //The old rec.page now contains a copy of the data up to the split point
        rec.getPage().len = rec.offset;
        pageHeader.setDataLength(rec.getPage().len);
        rec.getPage().setDirty(true);
        //Create a first split page
        DOMPage firstSplitPage = new DOMPage();
        if (transaction != null && isRecoveryEnabled()) {
            final Loggable loggable = new CreatePageLoggable(transaction,
                rec.getPage().getPageNum(), firstSplitPage.getPageNum(),
                NO_PAGE, pageHeader.getCurrentTupleID());
            writeToLog(loggable, firstSplitPage.page);
        }
        DOMPage nextSplitPage = firstSplitPage;
        nextSplitPage.getPageHeader().setNextTupleID(pageHeader.getCurrentTupleID());
        long backLink;
        short splitRecordCount = 0;
        LOG.debug("Splitting {} at {}: New page: {}; Next page: {}", rec.getPage().getPageNum(), rec.offset, nextSplitPage.getPageNum(), pageHeader.getNextDataPage());
        //Start copying records from rec.offset to the new split pages
        for (int pos = rec.offset; pos < oldDataLen; splitRecordCount++) {
            //Read the current id
            final short tupleID = ByteConversion.byteToShort(oldData, pos);
            pos += LENGTH_TID;
            //This is already a link, so we just copy it
            if (ItemId.isLink(tupleID)) {
                /* No room in the old page, append a new one */
                if (rec.getPage().len + LENGTH_TID + LENGTH_FORWARD_LOCATION > fileHeader.getWorkSize()) {
                    final DOMPage newPage = new DOMPage();
                    final DOMFilePageHeader newPageHeader = newPage.getPageHeader();
                    if (transaction != null && isRecoveryEnabled()) {
                        Loggable loggable = new CreatePageLoggable(transaction,
                            rec.getPage().getPageNum(), newPage.getPageNum(),
                            pageHeader.getNextDataPage(), pageHeader.getCurrentTupleID());
                        writeToLog(loggable, firstSplitPage.page);
                        loggable = new UpdateHeaderLoggable(transaction,
                            pageHeader.getPreviousDataPage(), rec.getPage().getPageNum(), 
                            newPage.getPageNum(), pageHeader.getPreviousDataPage(),
                            pageHeader.getNextDataPage());
                        writeToLog(loggable, nextSplitPage.page);
                    }
                    newPageHeader.setNextTupleID(pageHeader.getCurrentTupleID());
                    newPageHeader.setPrevDataPage(rec.getPage().getPageNum());
                    newPageHeader.setNextDataPage(pageHeader.getNextDataPage());
                    LOG.debug("Appending page after split: {}", newPage.getPageNum());
                    pageHeader.setNextDataPage(newPage.getPageNum());
                    pageHeader.setDataLength(rec.getPage().len);
                    pageHeader.setRecordCount(countRecordsInPage(rec.getPage()));
                    rec.getPage().cleanUp();
                    rec.getPage().setDirty(true);
                    dataCache.add(rec.getPage());
                    //Switch record to new page...
                    rec.setPage(newPage);
                    rec.getPage().len = 0;
                    dataCache.add(newPage);
                }
                if (transaction != null && isRecoveryEnabled()) {
                    final long oldLink = ByteConversion.byteToLong(oldData, pos);
                    final Loggable loggable = new AddLinkLoggable(transaction, 
                        rec.getPage().getPageNum(), ItemId.getId(tupleID), oldLink);
                    writeToLog(loggable, rec.getPage().page);
                }
                ByteConversion.shortToByte(tupleID, rec.getPage().data, rec.getPage().len);
                rec.getPage().len += LENGTH_TID;
                System.arraycopy(oldData, pos, rec.getPage().data, rec.getPage().len,
                    LENGTH_FORWARD_LOCATION);
                rec.getPage().len += LENGTH_FORWARD_LOCATION;
                pos += LENGTH_FORWARD_LOCATION;
                continue;
            }
            //Read data length
            final short vlen = ByteConversion.byteToShort(oldData, pos);
            pos += LENGTH_DATA_LENGTH;
            //If this is an overflow page, the real data length is always
            //LENGTH_LINK byte for the page number of the overflow page
            final short realLen = (vlen == OVERFLOW ? LENGTH_OVERFLOW_LOCATION : vlen);
            //Check if we have room in the current split page
            if (nextSplitPage.len + LENGTH_TID + LENGTH_DATA_LENGTH +
                    LENGTH_ORIGINAL_LOCATION + realLen > fileHeader.getWorkSize()) {
                //Not enough room in the split page: append a new page
                final DOMPage newPage = new DOMPage();
                final DOMFilePageHeader newPageHeader = newPage.getPageHeader();
                if (transaction != null && isRecoveryEnabled()) {
                    Loggable loggable = new CreatePageLoggable(transaction,
                        nextSplitPage.getPageNum(), newPage.getPageNum(), 
                        NO_PAGE, pageHeader.getCurrentTupleID());
                    writeToLog(loggable, firstSplitPage.page);
                    loggable = new UpdateHeaderLoggable(transaction,
                        nextSplitPage.getPageHeader().getPreviousDataPage(),
                        nextSplitPage.getPageNum(), newPage.getPageNum(),
                        nextSplitPage.getPageHeader().getPreviousDataPage(),
                        nextSplitPage.getPageHeader().getNextDataPage());
                    writeToLog(loggable, nextSplitPage.page);
                }
                newPageHeader.setNextTupleID(pageHeader.getCurrentTupleID());
                newPageHeader.setPrevDataPage(nextSplitPage.getPageNum());
                //No next page ? Well... we might want to enforce the value -pb
                LOG.debug("Creating new split page: {}", newPage.getPageNum());
                nextSplitPage.getPageHeader().setNextDataPage(newPage.getPageNum());
                nextSplitPage.getPageHeader().setDataLength(nextSplitPage.len);
                nextSplitPage.getPageHeader().setRecordCount(splitRecordCount);
                nextSplitPage.cleanUp();
                nextSplitPage.setDirty(true);
                dataCache.add(nextSplitPage);
                dataCache.add(newPage);
                nextSplitPage = newPage;
                splitRecordCount = 0;
            }
            /*
             * If the record has already been relocated, 
             * read the original storage address and update the link there.
             */
            if (ItemId.isRelocated(tupleID)) {
                backLink = ByteConversion.byteToLong(oldData, pos);
                pos += LENGTH_ORIGINAL_LOCATION;
                final RecordPos originalRecordPos = findRecord(backLink, false);
                final long oldLink = ByteConversion.byteToLong(originalRecordPos.getPage().data, 
                        originalRecordPos.offset);
                final long forwardLink = StorageAddress.createPointer((int) 
                    nextSplitPage.getPageNum(), ItemId.getId(tupleID));
                if (transaction != null && isRecoveryEnabled()) {
                    final Loggable loggable = new UpdateLinkLoggable(transaction, 
                        originalRecordPos.getPage().getPageNum(), originalRecordPos.offset,
                        forwardLink, oldLink);
                    writeToLog(loggable, originalRecordPos.getPage().page);
                }
                ByteConversion.longToByte(forwardLink, originalRecordPos.getPage().data,
                    originalRecordPos.offset);
                originalRecordPos.getPage().setDirty(true);
                dataCache.add(originalRecordPos.getPage());
            } else {
                backLink = StorageAddress.createPointer((int) rec.getPage().getPageNum(),
                    ItemId.getId(tupleID));
            }
            /*
             * Save the record to the split page:
            */
            if (transaction != null && isRecoveryEnabled()) {
                //What does this "log" mean really ? Original ? -pb
                final byte[] logData = new byte[realLen];
                System.arraycopy(oldData, pos, logData, 0, realLen);
                final Loggable loggable = new AddMovedValueLoggable(transaction, 
                    nextSplitPage.getPageNum(), tupleID, logData, backLink);
                writeToLog(loggable, nextSplitPage.page);
            }
            //Set the relocated flag and save the item id
            ByteConversion.shortToByte(ItemId.setIsRelocated(tupleID), nextSplitPage.data, 
                nextSplitPage.len);
            nextSplitPage.len += LENGTH_TID;
            //Save length field
            ByteConversion.shortToByte(vlen, nextSplitPage.data, nextSplitPage.len);
            nextSplitPage.len += LENGTH_DATA_LENGTH;
            //Save link to the original page
            ByteConversion.longToByte(backLink, nextSplitPage.data,	nextSplitPage.len);
            nextSplitPage.len += LENGTH_ORIGINAL_LOCATION;
            //Now save the data
            try {
                System.arraycopy(oldData, pos, nextSplitPage.data, nextSplitPage.len, realLen);
            } catch (final ArrayIndexOutOfBoundsException e) {
                SanityCheck.TRACE("pos = " + pos + "; len = " + nextSplitPage.len +
                    "; currentLen = " + realLen + "; tupleID = " + tupleID +
                    "; page = " + rec.getPage().getPageNum());
                throw e;
            }
            nextSplitPage.len += realLen;
            pos += realLen;
            // save a link pointer in the original page if the record has not
            // been relocated before.
            if (!ItemId.isRelocated(tupleID)) {
                // the link doesn't fit into the old page. Append a new page
                if (rec.getPage().len + LENGTH_TID + LENGTH_FORWARD_LOCATION > fileHeader.getWorkSize()) {
                    final DOMPage newPage = new DOMPage();
                    final DOMFilePageHeader newPageHeader = newPage.getPageHeader();
                    if (transaction != null && isRecoveryEnabled()) {
                        Loggable loggable = new CreatePageLoggable(transaction, 
                            rec.getPage().getPageNum(), newPage.getPageNum(),
                            pageHeader.getNextDataPage(), pageHeader.getCurrentTupleID());
                        writeToLog(loggable, firstSplitPage.page);
                        loggable = new UpdateHeaderLoggable(transaction, 
                            pageHeader.getPreviousDataPage(), 
                            rec.getPage().getPageNum(), newPage.getPageNum(), 
                            pageHeader.getPreviousDataPage(), pageHeader.getNextDataPage());
                        writeToLog(loggable, nextSplitPage.page);
                    }
                    newPageHeader.setNextTupleID(pageHeader.getCurrentTupleID());
                    newPageHeader.setPrevDataPage(rec.getPage().getPageNum());
                    newPageHeader.setNextDataPage(pageHeader.getNextDataPage());
                    LOG.debug("Creating new page after split: {}", newPage.getPageNum());
                    pageHeader.setNextDataPage(newPage.getPageNum());
                    pageHeader.setDataLength(rec.getPage().len);
                    pageHeader.setRecordCount(countRecordsInPage(rec.getPage()));
                    rec.getPage().cleanUp();
                    rec.getPage().setDirty(true);
                    dataCache.add(rec.getPage());
                    //switch record to new page...
                    rec.setPage(newPage);
                    rec.getPage().len = 0;
                    dataCache.add(newPage);
                }
                final long forwardLink = StorageAddress.createPointer(
                    (int) nextSplitPage.getPageNum(), ItemId.getId(tupleID));
                if (transaction != null && isRecoveryEnabled()) {
                    final Loggable loggable = new AddLinkLoggable(transaction, 
                        rec.getPage().getPageNum(), tupleID, forwardLink);
                    writeToLog(loggable, rec.getPage().page);
                }
                ByteConversion.shortToByte(ItemId.setIsLink(tupleID), rec.getPage().data, rec.getPage().len);
                rec.getPage().len += LENGTH_TID;
                ByteConversion.longToByte(forwardLink, rec.getPage().data, rec.getPage().len);
                rec.getPage().len += LENGTH_FORWARD_LOCATION;
            }
        } //End of for loop: finished copying data
        //Link the split pages to the original page
        if (nextSplitPage.len == 0) {
            LOG.warn("Page {} is empty. Remove it", nextSplitPage.getPageNum());
            //If nothing has been copied to the last split page, remove it
            if (nextSplitPage == firstSplitPage) {
                firstSplitPage = null;
            }
            try {
                unlinkPages(nextSplitPage.page);
            } catch (final IOException e) {
                LOG.warn("Failed to remove empty split page: {}", e.getMessage(), e);
            }
            nextSplitPage.setDirty(true);
            dataCache.remove(nextSplitPage);
            nextSplitPage = null;
        } else {
            if (transaction != null && isRecoveryEnabled()) {
                final Loggable loggable = new UpdateHeaderLoggable(transaction, 
                    nextSplitPage.getPageHeader().getPreviousDataPage(), nextSplitPage.getPageNum(),
                    pageHeader.getNextDataPage(), nextSplitPage.getPageHeader().getPreviousDataPage(),
                    nextSplitPage.getPageHeader().getNextDataPage());
                writeToLog(loggable, nextSplitPage.page);
            }
            nextSplitPage.getPageHeader().setDataLength(nextSplitPage.len);
            nextSplitPage.getPageHeader().setNextDataPage(pageHeader.getNextDataPage());
            nextSplitPage.getPageHeader().setRecordCount(splitRecordCount);
            nextSplitPage.cleanUp();
            nextSplitPage.setDirty(true);
            dataCache.add(nextSplitPage);
            if (transaction != null && isRecoveryEnabled()) {
                final DOMFilePageHeader firstPageHeader = firstSplitPage.getPageHeader();
                final Loggable loggable = new UpdateHeaderLoggable(transaction, 
                    rec.getPage().getPageNum(), firstSplitPage.getPageNum(),
                    firstPageHeader.getNextDataPage(), firstPageHeader.getPreviousDataPage(),
                    firstPageHeader.getNextDataPage());
                writeToLog(loggable, nextSplitPage.page);
            }
            firstSplitPage.getPageHeader().setPrevDataPage(rec.getPage().getPageNum());
            if (nextSplitPage != firstSplitPage) {
                firstSplitPage.setDirty(true);
                dataCache.add(firstSplitPage);
            }
        }
        final long nextPageNum = pageHeader.getNextDataPage();
        if (NO_PAGE != nextPageNum) {
            final DOMPage nextPage = getDOMPage(nextPageNum);
            if (transaction != null && isRecoveryEnabled()) {
                final Loggable loggable = new UpdateHeaderLoggable(transaction, 
                    nextSplitPage.getPageNum(), nextPage.getPageNum(), 
                    NO_PAGE, nextPage.getPageHeader().getPreviousDataPage(),
                    nextPage.getPageHeader().getNextDataPage());
                writeToLog(loggable, nextPage.page);
            }
            nextPage.getPageHeader().setPrevDataPage(nextSplitPage.getPageNum());
            nextPage.setDirty(true);
            dataCache.add(nextPage);
        }
        rec.setPage(getDOMPage(rec.getPage().getPageNum()));
        if (firstSplitPage != null) {
            if (transaction != null && isRecoveryEnabled()) {
                final Loggable loggable = new UpdateHeaderLoggable(transaction, 
                    pageHeader.getPreviousDataPage(), rec.getPage().getPageNum(), 
                    firstSplitPage.getPageNum(), pageHeader.getPreviousDataPage(), 
                    pageHeader.getNextDataPage());
                writeToLog(loggable, rec.getPage().page);
            }
            pageHeader.setNextDataPage(firstSplitPage.getPageNum());
        }
        pageHeader.setDataLength(rec.getPage().len);
        pageHeader.setRecordCount(countRecordsInPage(rec.getPage()));
        rec.getPage().cleanUp();
        rec.offset = rec.getPage().len;
        return rec;
    }

    /**
     * Returns the number of records stored in a page.
     * 
     * @param page the page
     * @return The number of records
     */
    private short countRecordsInPage(final DOMPage page) {
        short count = 0;
        final int dataLength = page.getPageHeader().getDataLength();
        for (int pos = 0; pos < dataLength; count++) {
            final short tupleID = ByteConversion.byteToShort(page.data, pos);
            pos += LENGTH_TID;
            if (ItemId.isLink(tupleID)) {
                pos += LENGTH_FORWARD_LOCATION;
            } else {
                final short vlen = ByteConversion.byteToShort(page.data, pos);
                pos += LENGTH_DATA_LENGTH;
                if (ItemId.isRelocated(tupleID)) {
                    pos += vlen == OVERFLOW ? 
                        LENGTH_ORIGINAL_LOCATION + LENGTH_OVERFLOW_LOCATION :
                        LENGTH_ORIGINAL_LOCATION + vlen;
                } else {
                    pos += vlen == OVERFLOW ? LENGTH_OVERFLOW_LOCATION : vlen;
                }
            }
        }
        return count;
    }

    String debugPageContents(final DOMPage page) {
        final StringBuilder buf = new StringBuilder();
        buf.append("Page ").append(page.getPageNum()).append(": ");
        short count = 0;
        final int dataLength = page.getPageHeader().getDataLength();
        for (int pos = 0; pos < dataLength; count++) {
            buf.append(pos).append("/");
            final short tupleID = ByteConversion.byteToShort(page.data, pos);
            pos += LENGTH_TID;
            buf.append(ItemId.getId(tupleID));
            if (ItemId.isLink(tupleID)) {
                buf.append("L");
            } else if (ItemId.isRelocated(tupleID)) {
                buf.append("R");
            }
            if (ItemId.isLink(tupleID)) {
                final long forwardLink = ByteConversion.byteToLong(page.data, pos);
                buf.append(':').append(forwardLink).append(" ");
                pos += LENGTH_FORWARD_LOCATION;
            } else {
                final short valueLength = ByteConversion.byteToShort(page.data, pos);
                pos += LENGTH_DATA_LENGTH;
                if (valueLength < 0) {
                    LOG.warn("Illegal length: {}", valueLength);
                    return buf.append("[Illegal length : ").append(valueLength).append("] ").toString();
                    //Probably unable to continue...
                } else if (ItemId.isRelocated(tupleID)) {
                    //TODO : output to buffer ?
                    pos += LENGTH_ORIGINAL_LOCATION;
                } else {
                    buf.append("[");
                    switch (Signatures.getType(page.data[pos])) {
                    case Node.ELEMENT_NODE :
                    {
                        buf.append("element ");
                        int readOffset = pos;
                        readOffset += 1;
                        final int children = ByteConversion.byteToInt(page.data, readOffset);
                        readOffset += ElementImpl.LENGTH_ELEMENT_CHILD_COUNT;
                        final int dlnLen = ByteConversion.byteToShort(page.data, readOffset);
                        readOffset += NodeId.LENGTH_NODE_ID_UNITS;
                        //That might happen during recovery runs : TODO, investigate
                        if (owner == null) {
                            buf.append("(Can't read data, owner is null)");
                        } else {
                            try {
                                final NodeId nodeId = ((NativeBroker)owner).getBrokerPool()
                                    .getNodeFactory().createFromData(dlnLen, page.data, readOffset);
                                readOffset += nodeId.size();
                                buf.append("(").append(nodeId).append(")");
                                final short attributes = ByteConversion.byteToShort(page.data, readOffset);
                                buf.append(" children: ").append(children);
                                buf.append(" attributes: ").append(attributes);
                            } catch (final Exception e) {
                                //TODO : more friendly message. Provide the array of bytes ?
                                buf.append("(Unable to read the node ID at: ").append(readOffset);
                                buf.append(" children : ").append(children);
                                //Probably a wrong offset so... don't read it
                                buf.append(" attributes : unknown");
                            }
                        }
                        break;
                    }
                    case Node.TEXT_NODE:
                    case Node.CDATA_SECTION_NODE:
                    {
                        if (Signatures.getType(page.data[pos]) == Node.TEXT_NODE) {
                            buf.append("text ");
                        } else {
                            buf.append("CDATA ");
                        }
                        int readOffset = pos;
                        readOffset += 1;
                        final int dlnLen = ByteConversion.byteToShort(page.data, readOffset);
                        readOffset += NodeId.LENGTH_NODE_ID_UNITS;
                        //That might happen during recovery runs : TODO, investigate
                        if (owner == null) {
                            buf.append("(Can't read data, owner is null)");
                        } else {
                            try {
                                final NodeId nodeId = ((NativeBroker)owner).getBrokerPool()
                                    .getNodeFactory().createFromData(dlnLen, page.data, readOffset);
                                readOffset += nodeId.size();
                                buf.append("(").append(nodeId).append(")");
                                String value = new String(page.data, readOffset, valueLength - (readOffset - pos), UTF_8);
                                if (value.length() > 15) {
                                    value = value.substring(0,8) + "..." + value.substring(value.length() - 8);
                                }

                                buf.append(":'").append(value).append("'");
                            } catch (final Exception e) {
                                //TODO : more friendly message. Provide the array of bytes ?
                                buf.append("(unable to read the node ID at : ").append(readOffset);
                            }
                        }
                        break;
                    }
                    case Node.ATTRIBUTE_NODE:
                    {
                        buf.append("[");
                        buf.append("attribute ");
                        int readOffset = pos;
                        final byte idSizeType = (byte) (page.data[readOffset] & 0x3);
                        final boolean hasNamespace = (page.data[readOffset] & 0x10) == 0x10;
                        readOffset += 1;
                        final int dlnLen = ByteConversion.byteToShort(page.data, readOffset);
                        readOffset += NodeId.LENGTH_NODE_ID_UNITS;
                        //That might happen during recovery runs : TODO, investigate
                        if (owner == null) {
                            buf.append("(can't read data, owner is null)");
                        } else {
                            try {
                                final NodeId nodeId = ((NativeBroker)owner).getBrokerPool()
                                    .getNodeFactory().createFromData(dlnLen, page.data, readOffset);
                                readOffset += nodeId.size();
                                buf.append("(").append(nodeId).append(")");
                                readOffset += Signatures.getLength(idSizeType);
                                if (hasNamespace) {
                                    //Untested
                                    final short NSId = ByteConversion.byteToShort(page.data, readOffset);
                                    readOffset += AttrImpl.LENGTH_NS_ID;
                                    final short prefixLen = ByteConversion.byteToShort(page.data, readOffset);
                                    readOffset += AttrImpl.LENGTH_PREFIX_LENGTH + prefixLen;
                                    final String prefix = new String(page.data, readOffset, valueLength - (readOffset - prefixLen), UTF_8);

                                    final String NsURI = ((NativeBroker)owner).getBrokerPool()
                                        .getSymbols().getNamespace(NSId);
                                    buf.append(prefix).append("{").append(NsURI).append("}");
                                }
                                    String value = new String(page.data, readOffset, valueLength - (readOffset - pos), UTF_8);
                                    if (value.length() > 15) {
                                        value = value.substring(0, 8) + "..." + value.substring(value.length() - 8);
                                    }

                                    buf.append(":'").append(value).append("'");
                            } catch (final Exception e) {
                                //TODO : more friendly message. Provide the array of bytes ?
                                buf.append("(unable to read the node ID at : ").append(readOffset);
                            }
                        }
                            buf.append("] ");
                            break;
                        }
                    default:
                        buf.append("Unknown node type !");
                    }
                    buf.append( "] ");
                }
                pos += valueLength;
            }
        }
        buf.append("; records in page: ").append(count).append(" (header says: ").append(page.getPageHeader().getRecordCount()).append(")");
        buf.append("; currentTupleID: ").append(page.getPageHeader().getCurrentTupleID());
        buf.append("; data length: ").append(page.getPageHeader().getDataLength());
        for (int i = page.data.length ; i > 0 ; i--) {
            if (page.data[i - 1] != 0) {
                buf.append(" (last non-zero byte: ").append(i).append(")");
                break;
            }
        }
        return buf.toString();
    }

    @Override
    public FileHeader createFileHeader(final int pageSize) {
        return new BTreeFileHeader(1024, pageSize);
    }

    @Override
    protected void unlinkPages(final Page page) throws IOException {
        super.unlinkPages(page);
    }

    @Override
    public PageHeader createPageHeader() {
        return new DOMFilePageHeader();
    }

    public List<Value> findKeys(final IndexQuery query)
            throws IOException, BTreeException {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLocked(getLockName())) {
            LOG.debug("The file doesn't own a lock");
        }
        final FindCallback callBack = new FindCallback(FindCallback.KEYS);
        try {
            query(query, callBack);
        } catch (final TerminatedException e) {
            // Should never happen here
            LOG.error("Method terminated");
        }
        return callBack.getValues();
    }

    /**
     * Retrieve node at virtual address.
     *
     * @param broker the database broker
     * @param node The virtual address
     * @return The reference of the node
     * @throws IOException if an I/O error occurs
     * @throws BTreeException if an error occurs reading the tree
     */
    protected long findValue(final DBBroker broker, final NodeProxy node)
            throws IOException, BTreeException {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLocked(getLockName())) {
            LOG.debug("The file doesn't own a lock");
        }
        final DocumentImpl doc = node.getOwnerDocument();
        final NodeRef nodeRef = new NativeBroker.NodeRef(doc.getDocId(), node.getNodeId());
        // first try to find the node in the index
        final long pointer = findValue(nodeRef);
        if (pointer == KEY_NOT_FOUND) {
            // node not found in index: try to find the nearest available
            // ancestor and traverse it
            NodeId nodeID = node.getNodeId();
            long parentPointer = KEY_NOT_FOUND;
            do {
                nodeID = nodeID.getParentId();
                if (nodeID == null) {
                    SanityCheck.TRACE("Node " + node.getOwnerDocument().getDocId() + ":<null nodeID> not found.");
                    throw new BTreeException("Node not found.");
                }
                if (nodeID == NodeId.DOCUMENT_NODE) {
                    SanityCheck.TRACE("Node " + node.getOwnerDocument().getDocId() + ":" + nodeID + " not found.");
                    return KEY_NOT_FOUND;
                }
                final NativeBroker.NodeRef parentRef = new NativeBroker.NodeRef(doc.getDocId(), nodeID);
                try {
                    parentPointer = findValue(parentRef);
                } catch (final BTreeException bte) {
                    LOG.error("report me", bte);
                }
            } while (parentPointer == KEY_NOT_FOUND);
            try {

                final int thisLevel = nodeID.getTreeLevel();
                Integer childLevel = null; // lazily initialized below

                final NodeProxy parent = new NodeProxy(null, doc, nodeID, parentPointer);
                final EmbeddedXMLStreamReader reader = (EmbeddedXMLStreamReader)broker.getXMLStreamReader(parent, true);

                while (reader.hasNext()) {
                    final int status = reader.next();

                    if (status != XMLStreamReader.END_ELEMENT) {
                        if (childLevel == null) {
                            childLevel = reader.getNode().getNodeType() == Node.ELEMENT_NODE ? thisLevel + 1 : thisLevel;
                        }

                        final NodeId otherId = (NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID);
                        if (otherId.equals(node.getNodeId())) {
                            return reader.getCurrentPosition();
                        }
                    }

                    if (status == XMLStreamConstants.END_ELEMENT) {
                        final NodeId otherId = (NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID);
                        final int otherLevel = otherId.getTreeLevel();
                        if (childLevel != null && childLevel != otherLevel && otherLevel == thisLevel) {
                            // finished `this` element...
                            break;  // exit-while
                        }
                    }
                }

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Node {} could not be found. Giving up. This is usually not an error.", node.getNodeId());
                }
                return KEY_NOT_FOUND;
            } catch (final XMLStreamException e) {
                SanityCheck.TRACE("Node " + node.getOwnerDocument().getDocId() + ":" + node.getNodeId() + " not found.");
                throw new BTreeException("Node " + node.getNodeId() + " not found.");
            }
        } else {
            return pointer;
        }
    }

    /**
     * Find matching nodes for the given query.
     * 
     * @param query Description of the Parameter
     * @return Description of the Return Value
     * @throws IOException Description of the Exception
     * @throws BTreeException Description of the Exception
     */
    public List<Value> findValues(final IndexQuery query) throws IOException,
            BTreeException {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLocked(getLockName())) {
            LOG.debug("The file doesn't own a lock");
        }
        final FindCallback callBack = new FindCallback(FindCallback.VALUES);
        try {
            query(query, callBack);
        } catch (final TerminatedException e) {
            // Should never happen
            LOG.warn("Method terminated");
        }
        return callBack.getValues();
    }

    /**
     * Flush all buffers to disk.
     *
     * @return true if the buffers were flushed
     * @throws DBException if an error occurs
     */
    @Override
    public boolean flush() throws DBException {
        boolean flushed = false;
        //TODO : record transaction as a valuable flush ?
        if (isRecoveryEnabled()) {
            logManager.ifPresent(l -> l.flush(true, false));
        }
        if (!BrokerPool.FORCE_CORRUPTION) {
            flushed = flushed | super.flush();
            flushed = flushed | dataCache.flush();
        }
        return flushed;
    }

    @Override
    public void printStatistics() {
        super.printStatistics();
        final NumberFormat nf1 = NumberFormat.getPercentInstance();
        final NumberFormat nf2 = NumberFormat.getInstance();
        final StringBuilder buf = new StringBuilder();
        buf.append(FileUtils.fileName(getFile())).append(" DATA ");
        buf.append("Buffers occupation : ");
        if (dataCache.getBuffers() == 0 && dataCache.getUsedBuffers() == 0) {
            buf.append("N/A");
        } else {
            buf.append(nf1.format(dataCache.getUsedBuffers()/(float)dataCache.getBuffers()));
        }
        buf.append(" (").append(nf2.format(dataCache.getUsedBuffers())).append(" out of ").append(nf2.format(dataCache.getBuffers())).append(")");
        buf.append(" Cache efficiency : ");
        if (dataCache.getHits() == 0 && dataCache.getFails() == 0) {
            buf.append("N/A");
        } else {
            buf.append(nf1.format(dataCache.getHits()/(float)(dataCache.getFails() + dataCache.getHits())));
        }
        LOGSTATS.info(buf.toString());
    }

    public BufferStats getDataBufferStats() {
        return new BufferStats(dataCache.getBuffers(), dataCache.getUsedBuffers(), 
            dataCache.getHits(), dataCache.getFails());
    }


    /**
     * Retrieve a node by key
     * 
     * @param key the key
     * @return the value, or null
     */
    public Value get(final Value key) {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLocked(getLockName())) {
            LOG.debug("The file doesn't own a lock");
        }
        try {
            final long pointer = findValue(key);
            if (pointer == KEY_NOT_FOUND) {
                LOG.warn("Value not found : {}", key);
                return null;
            }
            return get(pointer);
        } catch (final BTreeException | IOException e) {
            LOG.error(e);
            return null;
        }
    }

    public Value get(final DBBroker broker, final NodeProxy node) {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLocked(getLockName())) {
            LOG.debug("The file doesn't own a lock");
        }
        try {
            final long pointer = findValue(broker, node);
            if (pointer == KEY_NOT_FOUND) {
                return null;
            }
            return get(pointer);
        } catch (final BTreeException | IOException e) {
            LOG.warn(e);
            return null;
        }
    }

    /**
     * Retrieve node at virtual address.
     * 
     * @param pointer The virtual address
     * @return The node
     */
    public Value get(final long pointer) {
        return get(pointer, true);
    }

    /**
     * Retrieve node at virtual address.
     * 
     * @param pointer The virtual address
     * @param warnIfMissing Whether or not a warning should be output 
     * if the node can not be found 
     * @return The node
     */
    public Value get(final long pointer, final boolean warnIfMissing) {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLocked(getLockName())) {
            LOG.debug("The file doesn't own a lock");
        }
        final RecordPos rec = findRecord(pointer);
        if (rec == null) {
            if (warnIfMissing) {
                SanityCheck.TRACE("Object at " + StorageAddress.toString(pointer) + " not found.");
            }
            //TODO : throw exception ?
            return null;
        }
        final short vlen = ByteConversion.byteToShort(rec.getPage().data, rec.offset);
        rec.offset += LENGTH_DATA_LENGTH;
        if (ItemId.isRelocated(rec.getTupleID())) {
            rec.offset += LENGTH_ORIGINAL_LOCATION;
        }
        final Value value;
        if (vlen == OVERFLOW) {
            final long pageNo = ByteConversion.byteToLong(rec.getPage().data, rec.offset);
            final byte[] data = getOverflowValue(pageNo);
            value = new Value(data);
        } else {
            value = new Value(rec.getPage().data, rec.offset, vlen);
        }
        value.setAddress(pointer);
        return value;
    }

    @Override
    protected void dumpValue(final Writer writer, final Value key, final int status) throws IOException {
        if (status == BRANCH) {
            super.dumpValue(writer, key, status);
            return;
        }
        if (key.getLength() == 0) {
            return;
        }
        writer.write(Integer.toString(ByteConversion.byteToInt(key.data(), key.start())));
        writer.write(':');
        try {
            final int bytes = key.getLength() - 4;
            final byte[] data = key.data();
            for (int i = 0; i < bytes; i++) {
                writer.write(DLNBase.toBitString(data[key.start() + 4 + i]));
            }
        } catch (final Exception e) {
            LOG.error("{}: doc: {}", e.getMessage(), ByteConversion.byteToInt(key.data(), key.start()), e);
        }
    }

    /**
     * Put a new key/value pair.
     *
     * @param transaction the database transaction
     * @param key the key
     * @param value the value
     * @return pointer to the address
     *
     * @throws ReadOnlyException if the DOM file is read-only
     */
    public long put(final Txn transaction, final Value key, final byte[] value)
            throws ReadOnlyException {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLockedForWrite(getLockName())) {
            LOG.debug("The file doesn't own a write lock");
        }
        final long pointer = add(transaction, value);
        try {
            addValue(transaction, key, pointer);
        } catch (final BTreeException | IOException e) {
            //TODO : throw exception ?
            LOG.error(e);
            return KEY_NOT_FOUND;
        }
        return pointer;
    }

    /**
     * Physically remove a node. The data of the node will be removed from the
     * page and the occupied space is freed.
     *
     * @param transaction the database transaction
     * @param key the key
     */

    public void remove(final Txn transaction, final Value key) {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLockedForWrite(getLockName())) {
            LOG.debug("The file doesn't own a write lock");
        }
        try {
            final long pointer = findValue(key);
            if (pointer == KEY_NOT_FOUND) {
                //TODO : throw exception ?
                LOG.error("Value not found: {}", key);
                return;
            }
            remove(transaction, key, pointer);
        } catch (final BTreeException | IOException e) {
            //TODO : throw exception ?
            LOG.warn(e);
        }
    }


    byte[] getOverflowValue(final long pointer) {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLocked(getLockName())) {
            LOG.debug("The file doesn't own a lock");
        }
        try {
            final OverflowDOMPage overflow = new OverflowDOMPage(pointer);
            return overflow.read();
        } catch (final IOException e) {
            LOG.warn("IO error while loading overflow value", e);
            //TODO : throw exception ?
            return null;
        }
    }

    /**
     * Remove the overflow value.
     * 
     * @param transaction The current transaction
     * @param pointer The pointer to the value
     */
    public void removeOverflowValue(final Txn transaction, final long pointer) {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLockedForWrite(getLockName())) {
            LOG.debug("The file doesn't own a write lock");
        }
        try {
            final OverflowDOMPage overflow = new OverflowDOMPage(pointer);
            overflow.delete(transaction);
        } catch (final IOException e) {
            LOG.error("IO error while removing overflow value", e);
        }
    }

    /**
     * Remove the link at the specified position from the file.
     *
     * @param transaction the current transaction
     * @param pointer The pointer to the value
     */
    private void removeLink(final Txn transaction, final long pointer) {
        final RecordPos rec = findRecord(pointer, false);
        final DOMFilePageHeader pageHeader = rec.getPage().getPageHeader();
        if (transaction != null && isRecoveryEnabled()) {
            final byte[] data = new byte[LENGTH_LINK];
            System.arraycopy(rec.getPage().data, rec.offset, data, 0, LENGTH_LINK);
            //Position the stream at the very beginning of the record
            final RemoveValueLoggable loggable = new RemoveValueLoggable(transaction,
                rec.getPage().getPageNum(), rec.getTupleID(), rec.offset - LENGTH_TID, data, false, 0);
            writeToLog(loggable, rec.getPage().page);
        }
        final int end = rec.offset + LENGTH_LINK;
        //Position the stream at the very beginning of the record
        System.arraycopy(rec.getPage().data, end, rec.getPage().data,
             rec.offset - LENGTH_TID, rec.getPage().len - end);
        rec.getPage().len = rec.getPage().len - (LENGTH_TID + LENGTH_LINK);
        if (rec.getPage().len < 0) {
            LOG.warn("Page length < 0");
        }
        pageHeader.setDataLength(rec.getPage().len);
        pageHeader.decRecordCount();
        if (rec.getPage().len == 0) {
            if (pageHeader.getRecordCount() > 0) {
                LOG.warn("Empty page seems to have record!");
            }
            if (transaction != null && isRecoveryEnabled()) {
                final RemoveEmptyPageLoggable loggable = new RemoveEmptyPageLoggable(
                   transaction, rec.getPage().getPageNum(), 
                   pageHeader.getPreviousDataPage(), pageHeader.getNextDataPage());
                    writeToLog(loggable, rec.getPage().page);
            }
            removePage(rec.getPage());
            rec.setPage(null);
        } else {
            rec.getPage().setDirty(true);
            dataCache.add(rec.getPage());
        }
    }

    /**
     * Physically remove a node. The data of the node will be removed from the
     * page and the occupied space is freed.
     *
     * @param transaction the database transaction
     * @param pointer pointer to the node
     */
    public void removeNode(final Txn transaction, final long pointer) {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLockedForWrite(getLockName())) {
            LOG.debug("The file doesn't own a write lock");
        }
        final RecordPos rec = findRecord(pointer);
        //Position the stream at the very beginning of the record
        final int startOffset = rec.offset - LENGTH_TID;
        final DOMFilePageHeader pageHeader = rec.getPage().getPageHeader();
        final short vlen = ByteConversion.byteToShort(rec.getPage().data, rec.offset);
        rec.offset += LENGTH_DATA_LENGTH;
        short realLen = vlen;
        if (ItemId.isLink(rec.getTupleID())) {
            throw new RuntimeException("Cannot remove link ...");
        }
        boolean isOverflow = false;
        long backLink = 0;
        if (ItemId.isRelocated(rec.getTupleID())) {
            backLink = ByteConversion.byteToLong(rec.getPage().data, rec.offset);
            rec.offset += LENGTH_ORIGINAL_LOCATION;
            realLen += LENGTH_ORIGINAL_LOCATION;
            removeLink(transaction, backLink);
        }
        if (vlen == OVERFLOW) {
            // remove overflow value
            isOverflow = true;
            final long overflowLink = ByteConversion.byteToLong(rec.getPage().data, rec.offset);
            rec.offset += LENGTH_OVERFLOW_LOCATION;
            try {
                final OverflowDOMPage overflow = new OverflowDOMPage(overflowLink);
                overflow.delete(transaction);
            } catch (final IOException e) {
                LOG.warn("IO error while removing overflow page", e);
                //TODO : rethrow exception ? -pb
            }
            realLen += LENGTH_OVERFLOW_LOCATION;
        }
        if (transaction != null && isRecoveryEnabled()) {
            final byte[] data = new byte[vlen == OVERFLOW ? LENGTH_OVERFLOW_LOCATION : vlen];
            System.arraycopy(rec.getPage().data, rec.offset, data, 0,
                vlen == OVERFLOW ? LENGTH_OVERFLOW_LOCATION : vlen);
            final RemoveValueLoggable loggable = new RemoveValueLoggable(transaction,
               rec.getPage().getPageNum(), rec.getTupleID(), startOffset, data, isOverflow, backLink);
            writeToLog(loggable, rec.getPage().page);
        }
        final int dataLength = pageHeader.getDataLength();
        final int end = startOffset + LENGTH_TID + LENGTH_DATA_LENGTH + realLen;
        // remove old value
        System.arraycopy(rec.getPage().data, end, rec.getPage().data, startOffset, dataLength - end);
        rec.getPage().setDirty(true);
        rec.getPage().len = dataLength - (LENGTH_TID + LENGTH_DATA_LENGTH + realLen);
        if (rec.getPage().len < 0) {
            LOG.error("Page length < 0");
            //TODO : throw exception ? -pb
        }
        rec.getPage().setDirty(true);
        pageHeader.setDataLength(rec.getPage().len);
        pageHeader.decRecordCount();
        if (rec.getPage().len == 0) {
            LOG.debug("Removing page {}", rec.getPage().getPageNum());
            if (pageHeader.getRecordCount() > 0) {
                LOG.warn("Empty page seems to have record !");
            }
            if (transaction != null && isRecoveryEnabled()) {
                final RemoveEmptyPageLoggable loggable = new RemoveEmptyPageLoggable(
                   transaction, rec.getPage().getPageNum(),
                   rec.getPage().pageHeader.getPreviousDataPage(),
                   rec.getPage().pageHeader.getNextDataPage());
                writeToLog(loggable, rec.getPage().page);
            }
            removePage(rec.getPage());
            rec.setPage(null);
        } else {
            rec.getPage().setDirty(true);
            dataCache.add(rec.getPage());
        }
    }

    /**
     * Physically remove a node. The data of the node will be removed from the
     * page and the occupied space is freed.
     *
     * @param transaction the database transaction
     * @param key the key
     * @param pointer pointer to the value
     */
    public void remove(final Txn transaction, final Value key, final long pointer) {
        removeNode(transaction, pointer);
        try {
            removeValue(transaction, key);
        } catch (final BTreeException | IOException e) {
            LOG.error("BTree error while removing node", e);
            //TODO : rethrow exception ? -pb
        }
    }

    /**
     * Remove the specified page. The page is added to the list of free pages.
     * 
     * @param page the DOM page
     */
    private void removePage(final DOMPage page) {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLockedForWrite(getLockName())) {
            LOG.debug("The file doesn't own a write lock");
        }
        final DOMFilePageHeader pageHeader = page.getPageHeader();
        if (pageHeader.getNextDataPage() != NO_PAGE) {
            final DOMPage nextPage = getDOMPage(pageHeader.getNextDataPage());
            nextPage.getPageHeader().setPrevDataPage(pageHeader.getPreviousDataPage());
            nextPage.setDirty(true);
            dataCache.add(nextPage);
        }
        if (pageHeader.getPreviousDataPage() != NO_PAGE) {
            final DOMPage previousPage = getDOMPage(pageHeader.getPreviousDataPage());
            previousPage.getPageHeader().setNextDataPage(pageHeader.getNextDataPage());
            previousPage.setDirty(true);
            dataCache.add(previousPage);
        }
        try {
            pageHeader.setNextDataPage(NO_PAGE);
            pageHeader.setPrevDataPage(NO_PAGE);
            pageHeader.setDataLength(0);
            pageHeader.setNextTupleID(ItemId.UNKNOWN_ID);
            pageHeader.setRecordCount((short) 0);
            unlinkPages(page.page);
            page.setDirty(true);
            dataCache.remove(page);
        } catch (final IOException ioe) {
            LOG.error(ioe);
            //TODO : rethrow exception ? -pb
        }
        if (currentDocument != null) {
            currentDocument.decPageCount();
        }
    }

    /**
     * Remove a sequence of pages, starting with the page denoted by the passed
     * address pointer p.
     *
     * @param transaction the database transaction
     * @param pointer the pointer to the first page
     */
    public void removeAll(final Txn transaction, final long pointer) {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLockedForWrite(getLockName())) {
            LOG.debug("The file doesn't own a write lock");
        }
        long pageNum = StorageAddress.pageFromPointer(pointer);
        if (pageNum == NO_PAGE) {
            LOG.error("Tried to remove unknown page");
            //TODO : throw exception ? -pb
        }
        while (pageNum != NO_PAGE) {
            final DOMPage currentPage = getDOMPage(pageNum);
            final DOMFilePageHeader currentPageHeader = currentPage.getPageHeader();
            if (transaction != null && isRecoveryEnabled()) {
                final RemovePageLoggable loggable = new RemovePageLoggable(transaction, pageNum, 
                    currentPageHeader.getPreviousDataPage(), currentPageHeader.getNextDataPage(), 
                    currentPage.data, currentPage.len,
                    currentPageHeader.getCurrentTupleID(), currentPageHeader.getRecordCount());
                writeToLog(loggable, currentPage.page);
            }
            pageNum = currentPageHeader.getNextDataPage();
            try {
                currentPageHeader.setNextDataPage(NO_PAGE);
                currentPageHeader.setPrevDataPage(NO_PAGE);
                currentPageHeader.setDataLength(0);
                currentPageHeader.setNextTupleID(ItemId.UNKNOWN_ID);
                currentPageHeader.setRecordCount((short) 0);
                currentPage.len = 0;
                unlinkPages(currentPage.page);
                currentPage.setDirty(true);
                dataCache.remove(currentPage);
            } catch (final IOException e) {
                LOG.error("Error while removing page: {}", e.getMessage(), e);
                //TODO : rethrow the exception ? -pb
            }
        }
    }

    public String debugPages(final DocumentImpl doc, boolean showPageContents) {
        final StringBuilder buf = new StringBuilder();
        buf.append("Pages used by ").append(doc.getURI());
        buf.append("; (docId: ").append(doc.getDocId()).append("): ");
        long pageNum = StorageAddress.pageFromPointer((
            (IStoredNode<?>) doc.getFirstChild()).getInternalAddress());
        while (pageNum != NO_PAGE) {
            final DOMPage page = getDOMPage(pageNum);
            final DOMFilePageHeader pageHeader = page.getPageHeader();
            dataCache.add(page);
            buf.append(' ').append(pageNum);
            pageNum = pageHeader.getNextDataPage();
            if (showPageContents) {
                LOG.debug(debugPageContents(page));
            }
        }
        return buf.toString();
    }

    /**
     * Update the key/value pair.
     *
     * @param transaction the database transaction
     * @param key the key
     * @param value the value
     *
     * @return true if the value was updated, false otherwise
     *
     * @throws ReadOnlyException if the DOM file is read-only
     */
    public boolean update(final Txn transaction, final Value key, final byte[] value)
            throws ReadOnlyException {
        try {
            final long pointer = findValue(key);
            if (pointer == KEY_NOT_FOUND) {
                //TODO : transform to error ? -pb
                LOG.warn("Node value not found : {}", key);
                return false;
            }
            update(transaction, pointer, value);
            return true;
        } catch (final BTreeException | IOException e) {
            //TODO : rethrow exception ? -pb
            LOG.error(e);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Update the key/value pair where the value is found at address p.
     *
     * @param transaction the database transaction
     * @param pointer pointer to the existing value
     * @param value the new value
     *
     * @throws ReadOnlyException if the DOM file is read-only
     */
    public void update(final Txn transaction, final long pointer, final byte[] value) throws ReadOnlyException {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLockedForWrite(getLockName())) {
            LOG.debug("The file doesn't own a write lock");
        }
        final RecordPos recordPos = findRecord(pointer);
        final short valueLength = ByteConversion.byteToShort(recordPos.getPage().data, recordPos.offset);
        recordPos.offset += LENGTH_DATA_LENGTH;
        if (ItemId.isRelocated(recordPos.getTupleID())) {
            recordPos.offset += LENGTH_ORIGINAL_LOCATION;
        }
        if (value.length < valueLength) {
            // value is smaller than before
            throw new IllegalStateException("Value too short. Expected: "
                    + value.length + "; got: " + valueLength);
        } else if (value.length > valueLength) {
            throw new IllegalStateException("Value too long. Expected: "
                    + value.length + "; got: " + valueLength);
        } else {
            if (transaction != null && isRecoveryEnabled()) {
                if (ItemId.getId(recordPos.getTupleID()) < 0) {
                    LOG.error("Tuple ID < 0");
                    //TODO : throw exception ? -pb
                }
                final Loggable loggable = new UpdateValueLoggable(transaction, 
                    recordPos.getPage().getPageNum(), recordPos.getTupleID(),
                    value, recordPos.getPage().data, recordPos.offset);
                writeToLog(loggable, recordPos.getPage().page);
            }
            // value length unchanged
            System.arraycopy(value, 0, recordPos.getPage().data, recordPos.offset, value.length);
        }
        recordPos.getPage().setDirty(true);
    }

    /**
     * Retrieve the string value of the specified node. This is an optimized low-level method
     * which will directly traverse the stored DOM nodes and collect the string values of
     * the specified root node and all its descendants. By directly scanning the stored
     * node data, we do not need to create a potentially large amount of node objects
     * and thus save memory and time for garbage collection. 
     *
     * @param broker the database broker
     * @param node the node
     * @param addWhitespace true if whitespace should be added to the node value
     * @return string value of the specified node
     */
    public String getNodeValue(final DBBroker broker, final IStoredNode node, final boolean addWhitespace) {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLocked(getLockName())) {
            LOG.debug("The file doesn't own a lock");
        }
        try {
            long address = node.getInternalAddress();
            RecordPos recordPos = null;
            // try to directly locate the root node through its storage address
            if (StorageAddress.hasAddress(address)) {
                recordPos = findRecord(address);
            }
            if (recordPos == null) {
                // fallback to a BTree lookup if the node could not be found
                // by its storage address
                address = findValue(broker, new NodeProxy(null, node));
                if (address == BTree.KEY_NOT_FOUND) {
                    LOG.error("Node value not found: {}", node);
                    //TODO : throw exception ? -pb
                    return null;
                }
                recordPos = findRecord(address);
                SanityCheck.THROW_ASSERT(recordPos != null, "Node data could not be found!");
                //TODO : throw exception ? -pb
            }
            // we collect the string values in binary format and append them to a ByteArrayOutputStream
            try(final UnsynchronizedByteArrayOutputStream os = new UnsynchronizedByteArrayOutputStream(32)) {
                // now traverse the tree
                getNodeValue(broker.getBrokerPool(), os, recordPos, true, addWhitespace);
                final byte[] data = os.toByteArray();

                final XMLString str = UTF8.decode(data);
                if (str != null) {
                    return str.toString();
                } else {
                    return "";
                }
            }
        } catch (final BTreeException e) {
            LOG.error("BTree error while reading node value", e);
          //TODO : rethrow exception ? -pb
        } catch (final Exception e) {
            LOG.error("IO error while reading node value", e);
          //TODO : rethrow exception ? -pb
        }
        //TODO : remove if exceptions thrown...
        return null;
    }

    /**
     * Recursive method to retrieve the string values of the root node
     * and all its descendants.
     *
     * @param pool the broker pool
     * @param os the output stream to receive the value
     * @param rec the record position
     * @param isTopNode true if this is the top node, false otherwise
     * @param addWhitespace true if whitespace should be added to the node value
     */
    private void getNodeValue(final BrokerPool pool,
                              final UnsynchronizedByteArrayOutputStream os,
                              final RecordPos rec, final boolean isTopNode,
                              final boolean addWhitespace) {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLocked(getLockName())) {
            LOG.debug("The file doesn't own a lock");
        }
        //Locate the next real node, skipping relocated nodes
        boolean foundNext = false;
        do {
            final DOMFilePageHeader pageHeader = rec.getPage().getPageHeader();
            if (rec.offset > pageHeader.getDataLength()) {
                // end of page reached, proceed to the next page
                final long nextPage = pageHeader.getNextDataPage();
                if (nextPage == NO_PAGE) {
                    SanityCheck.TRACE("Bad link to next page! " +
                        "Offset: " + rec.offset + 
                        ", Len: " + pageHeader.getDataLength() +
                        ", Page info : " + rec.getPage().page.getPageInfo());
                    //TODO : throw exception ? -pb
                    return;
                }
                rec.setPage(getDOMPage(nextPage));
                dataCache.add(rec.getPage());
                rec.offset = LENGTH_TID;
            }
            //Position the stream at the very beginning of the record
            final short tupleID = ByteConversion.byteToShort(rec.getPage().data, rec.offset - LENGTH_TID);
            rec.setTupleID(tupleID);
            if (ItemId.isLink(rec.getTupleID())) {
                //This is a link: skip it
                //We position the offset *after* the next TupleID
                rec.offset += (LENGTH_FORWARD_LOCATION + LENGTH_TID);
            } else {
                //OK: node found
                foundNext = true;
            }
        } while (!foundNext);
        final short valueLength = ByteConversion.byteToShort(rec.getPage().data, rec.offset);
        int realLen = valueLength;
        rec.offset += LENGTH_DATA_LENGTH;
        //Check if the node was relocated
        if (ItemId.isRelocated(rec.getTupleID())) {
            rec.offset += LENGTH_ORIGINAL_LOCATION;
        }
        byte[] data = rec.getPage().data;
        int readOffset = rec.offset;
        boolean inOverflow = false;
        if (valueLength == OVERFLOW) {
            //If we have an overflow value, load it from the overflow page
            final long p = ByteConversion.byteToLong(data, rec.offset);
            data = getOverflowValue(p);
            //We position the offset *after* the next TID
            rec.offset += LENGTH_OVERFLOW_LOCATION + LENGTH_TID;
            realLen = data.length;
            readOffset = 0;
            inOverflow = true;
        }
        // check the type of the node
        final short type = Signatures.getType(data[readOffset]);
        readOffset += StoredNode.LENGTH_SIGNATURE_LENGTH;
        //Switch on the node type
        switch (type) {
            case Node.ELEMENT_NODE: {
                final int children = ByteConversion.byteToInt(data, readOffset);
                readOffset += ElementImpl.LENGTH_ELEMENT_CHILD_COUNT;
                final int dlnLen = ByteConversion.byteToShort(data, readOffset);
                readOffset += NodeId.LENGTH_NODE_ID_UNITS;
                final int nodeIdLen = pool.getNodeFactory().lengthInBytes(dlnLen, data, readOffset);
                readOffset += nodeIdLen;
                final short attributes = ByteConversion.byteToShort(data, readOffset);
                //Ignore the following NS data which are of no use
                //We position the offset *after* the next TID
                rec.offset += realLen + LENGTH_TID;
                final boolean extraWhitespace = addWhitespace && (children - attributes) > 1;
                for (int i = 0; i < children; i++) {
                    //recursive call : we ignore attributes children
                    getNodeValue(pool, os, rec, false, addWhitespace);
                    if (extraWhitespace) {
                        os.write((byte) ' ');
                    }
                }
                return;
            }
            case Node.TEXT_NODE:
            case Node.CDATA_SECTION_NODE: {
                final int dlnLen = ByteConversion.byteToShort(data, readOffset);
                readOffset += NodeId.LENGTH_NODE_ID_UNITS;
                final int nodeIdLen = pool.getNodeFactory().lengthInBytes(dlnLen, data, readOffset);
                readOffset += nodeIdLen;
                os.write(data, readOffset, realLen -
                    (StoredNode.LENGTH_SIGNATURE_LENGTH + NodeId.LENGTH_NODE_ID_UNITS + nodeIdLen));
                break;
            }
            case Node.PROCESSING_INSTRUCTION_NODE: {
	            final int dlnLen = ByteConversion.byteToShort(data, readOffset);
	            readOffset += NodeId.LENGTH_NODE_ID_UNITS;
	            final int nodeIdLen = pool.getNodeFactory().lengthInBytes(dlnLen, data, readOffset);
	            readOffset += nodeIdLen;
	            final int targetLen = ByteConversion.byteToInt(data, readOffset);
	            readOffset += 4 + targetLen;
	            os.write(
            		data, 
            		readOffset, 
            		realLen - (StoredNode.LENGTH_SIGNATURE_LENGTH + NodeId.LENGTH_NODE_ID_UNITS + nodeIdLen + targetLen + 4));
	            break;
            }
            case Node.ATTRIBUTE_NODE: {
                if (isTopNode) {
                    final int start = readOffset - StoredNode.LENGTH_SIGNATURE_LENGTH;
                    final byte idSizeType = (byte) (data[start] & 0x3);
                    final boolean hasNamespace = (data[start] & 0x10) == 0x10;
                    final int dlnLen = ByteConversion.byteToShort(data, readOffset);
                    readOffset += NodeId.LENGTH_NODE_ID_UNITS;
                    final int nodeIdLen = pool.getNodeFactory().lengthInBytes(dlnLen, data, readOffset);
                    readOffset += nodeIdLen;
                    readOffset += Signatures.getLength(idSizeType);
                    if (hasNamespace) {
                        readOffset += AttrImpl.LENGTH_NS_ID; // skip namespace id
                        final short prefixLen = ByteConversion.byteToShort(data, readOffset);
                        readOffset += AttrImpl.LENGTH_PREFIX_LENGTH;
                        readOffset += prefixLen; // skip prefix
                    }
                    os.write(data, readOffset, realLen - (readOffset - start));
                }
                break;
            }
            case Node.COMMENT_NODE:
            {
                if (isTopNode) {
                    final int dlnLen = ByteConversion.byteToShort(data, readOffset);
                    readOffset += NodeId.LENGTH_NODE_ID_UNITS;
                    final int nodeIdLen = pool.getNodeFactory().lengthInBytes(dlnLen, data, readOffset);
                    readOffset += nodeIdLen;
                    os.write(data, readOffset, realLen - (StoredNode.LENGTH_SIGNATURE_LENGTH + NodeId.LENGTH_NODE_ID_UNITS + nodeIdLen));
                }
                break;
            }
        }
        if (!inOverflow) {
            //If it isn't an overflow value, add the value length to the current offset
            //We position the offset *after* the next TID
            rec.offset += realLen + LENGTH_TID;
        }
    }

    RecordPos findRecord(final long pointer) {
        return findRecord(pointer, true);
    }

    /**
     * Find a record within the page or the pages linked to it.
     * 
     * @param pointer the pointer to the page
     * @param skipLinks true if links should be skipped, false otherwise
     * @return The record position in the page
     */
    private RecordPos findRecord(final long pointer, final boolean skipLinks) {
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLocked(getLockName())) {
            LOG.debug("The file doesn't own a lock");
        }
        long pageNum = StorageAddress.pageFromPointer(pointer);
        short tupleID = StorageAddress.tidFromPointer(pointer);
        while (pageNum != NO_PAGE) {
            final DOMPage page = getDOMPage(pageNum);
            dataCache.add(page);
            final RecordPos rec = page.findRecord(tupleID);
            if (rec == null) {
                pageNum = page.getPageHeader().getNextDataPage();
                if (pageNum == page.getPageNum()) {
                    SanityCheck.TRACE("Circular link to next page on " + pageNum);
                    //TODO : throw exception ?
                    return null;
                }
            } else if (rec.isLink()) {
                if (!skipLinks)
                    {return rec;}
                final long forwardLink = ByteConversion.byteToLong(page.data, rec.offset);
                // load the link page
                pageNum = StorageAddress.pageFromPointer(forwardLink);
                tupleID = StorageAddress.tidFromPointer(forwardLink);
            } else {
                return rec;
            }
        }
        //TODO : throw exception ? -pb
        return null;
    }

    @Override
    public String getLockName() {
        return getFileName();
    }

    /**
     * The current object owning this file.
     * 
     * @param ownerObject The new ownerObject value
     */
    public final void setOwnerObject(final Object ownerObject) {
        if (ownerObject == null) {
            LOG.error("setOwnerObject(null)");
        }
        if(LOG.isDebugEnabled() && !lockManager.isBtreeLocked(getLockName())) {
            LOG.debug("The file doesn't own a lock");
        }

        owner = ownerObject;
    }

    /*
     * ---------------------------------------------------------------------------------
     * Methods used by recovery and transaction management
     * ---------------------------------------------------------------------------------
     */

    private boolean requiresRedo(final Loggable loggable, final DOMPage page) {
        return loggable.getLsn().compareTo(page.getPageHeader().getLsn()) > 0;
    }

    void redoCreatePage(final CreatePageLoggable loggable) {
        final DOMPage newPage = getDOMPage(loggable.newPage);
        final DOMFilePageHeader newPageHeader = newPage.getPageHeader();
        if (newPageHeader.getLsn().equals(Lsn.LSN_INVALID) || requiresRedo(loggable, newPage)) {
            try {
                dropFreePageList();
                newPageHeader.setStatus(RECORD);
                newPageHeader.setDataLength(0);
                newPageHeader.setNextTupleID(ItemId.UNKNOWN_ID);
                newPageHeader.setRecordCount((short) 0);
                newPage.len = 0;
                newPage.data = new byte[fileHeader.getWorkSize()];
                newPageHeader.setPrevDataPage(NO_PAGE);
                if (loggable.nextTID != ItemId.UNKNOWN_ID) {
                    newPageHeader.setNextTupleID(loggable.nextTID);
                }
                newPageHeader.setLsn(loggable.getLsn());
                newPage.setDirty(true);
                if (loggable.nextPage == NO_PAGE) {
                    newPageHeader.setNextDataPage(NO_PAGE);
                } else {
                    newPageHeader.setNextDataPage(loggable.nextPage);
                }
                if (loggable.prevPage == NO_PAGE) {
                    newPageHeader.setPrevDataPage(NO_PAGE);
                } else {
                    newPageHeader.setPrevDataPage(loggable.prevPage);
                }
            } catch (final IOException e) {
                LOG.error("Failed to redo {}: {}", loggable.dump(), e.getMessage(), e);
                //TODO : throw exception ?
            }
        }
        dataCache.add(newPage);
    }

    void undoCreatePage(final CreatePageLoggable loggable) {
        final DOMPage page = getDOMPage(loggable.newPage);
        final DOMFilePageHeader pageHeader = page.getPageHeader();
        try {
            pageHeader.setNextDataPage(NO_PAGE);
            pageHeader.setPrevDataPage(NO_PAGE);
            pageHeader.setDataLength(0);
            pageHeader.setNextTupleID(ItemId.UNKNOWN_ID);
            pageHeader.setRecordCount((short) 0);
            page.len = 0;
            unlinkPages(page.page);
            page.setDirty(true);
            dataCache.remove(page);
        } catch (final IOException e) {
            LOG.warn("Error while removing page: {}", e.getMessage(), e);
            //TODO : exception ?
        }
    }

    void redoAddValue(final AddValueLoggable loggable) {
        final DOMPage page = getDOMPage(loggable.pageNum);
        final DOMFilePageHeader pageHeader = page.getPageHeader();
        if ((!pageHeader.getLsn().equals(Lsn.LSN_INVALID)) && requiresRedo(loggable, page)) {
            try {
                ByteConversion.shortToByte(loggable.tid, page.data, page.len);
                page.len += LENGTH_TID;
                // save data length
                // overflow pages have length 0
                final short vlen = (short) loggable.value.length;
                ByteConversion.shortToByte(loggable.isOverflow ? OVERFLOW : vlen, page.data, page.len);
                page.len += LENGTH_DATA_LENGTH;
                // save data
                System.arraycopy(loggable.value, 0, page.data, page.len, vlen);
                page.len += vlen;
                pageHeader.incRecordCount();
                pageHeader.setDataLength(page.len);
                page.setDirty(true);
                pageHeader.setNextTupleID(loggable.tid);
                pageHeader.setLsn(loggable.getLsn());
                dataCache.add(page, 2);
            } catch (final ArrayIndexOutOfBoundsException e) {
                LOG.warn("page: {}; len = {}; value = {}", page.getPageNum(), page.len, loggable.value.length);
                throw e;
            }
        }
    }

    void undoAddValue(final AddValueLoggable loggable) {
        final DOMPage page = getDOMPage(loggable.pageNum);
        final DOMFilePageHeader pageHeader = page.getPageHeader();

        // is there anything to undo?
        if (pageHeader.getLsn().equals(Lsn.LSN_INVALID) || pageHeader.getStatus() == UNUSED) {
            LOG.warn("Nothing to undo, but received: AddValueLoggable(txnId={}, lsn={}, pageNum={}, isOverflow={})", loggable.getTransactionId(), loggable.getLsn(), loggable.pageNum, loggable.isOverflow);
            return;
        }

        final RecordPos pos = page.findRecord(ItemId.getId(loggable.tid));
        SanityCheck.ASSERT(pos != null, "Record not found! isOverflow: " + loggable.isOverflow);
        //TODO : throw exception ? -pb
        //Position the stream at the very beginning of the record
        final int startOffset = pos.offset - LENGTH_TID;
        //Get the record length
        final short vlen = loggable.isOverflow ? 8 : ByteConversion.byteToShort(page.data, pos.offset);
        //End offset
        final int end = startOffset + LENGTH_TID + LENGTH_DATA_LENGTH + vlen;
        final int dlen = pageHeader.getDataLength();
        //Remove old value
        System.arraycopy(page.data, end, page.data, startOffset, dlen - end);
        page.len = dlen - (LENGTH_TID + LENGTH_DATA_LENGTH + vlen);
        if (page.len < 0) {
            LOG.error("page length < 0");
            //TODO : exception ?
        }
        pageHeader.setDataLength(page.len);
        pageHeader.decRecordCount();
        page.setDirty(true);
    }

    void redoUpdateValue(final UpdateValueLoggable loggable) {
        final DOMPage page = getDOMPage(loggable.pageNum);
        final DOMFilePageHeader ph = page.getPageHeader();
        if ((!ph.getLsn().equals(Lsn.LSN_INVALID)) && requiresRedo(loggable, page)) {
            final RecordPos rec = page.findRecord(ItemId.getId(loggable.tid));
            SanityCheck.THROW_ASSERT(rec != null, 
                "tid " + ItemId.getId(loggable.tid) +
                " not found on page " + page.getPageNum() +
                "; contents: " + debugPageContents(page));
//            ByteConversion.byteToShort(rec.getPage().data, rec.offset);
            rec.offset += LENGTH_DATA_LENGTH;
            if (ItemId.isRelocated(rec.getTupleID())) {
                rec.offset += LENGTH_ORIGINAL_LOCATION;
            }
            System.arraycopy(loggable.value, 0, rec.getPage().data, rec.offset, loggable.value.length);
            rec.getPage().getPageHeader().setLsn(loggable.getLsn());
            rec.getPage().setDirty(true);
            dataCache.add(rec.getPage());
        }
    }

    void undoUpdateValue(final UpdateValueLoggable loggable) {
        final DOMPage page = getDOMPage(loggable.pageNum);
        final RecordPos rec = page.findRecord(ItemId.getId(loggable.tid));
        SanityCheck.THROW_ASSERT(rec != null,
            "tid " + ItemId.getId(loggable.tid) +
            " not found on page " + page.getPageNum() +
            "; contents: " + debugPageContents(page));
        final short vlen = ByteConversion.byteToShort(rec.getPage().data, rec.offset);
        SanityCheck.THROW_ASSERT(vlen == loggable.oldValue.length);
        rec.offset += LENGTH_DATA_LENGTH;
        if (ItemId.isRelocated(rec.getTupleID())) {
            rec.offset += LENGTH_ORIGINAL_LOCATION;
        }
        System.arraycopy(loggable.oldValue, 0, page.data, rec.offset, loggable.oldValue.length);
        page.getPageHeader().setLsn(loggable.getLsn());
        page.setDirty(true);
        dataCache.add(page);
    }

    void redoRemoveValue(final RemoveValueLoggable loggable) {
        final DOMPage page = getDOMPage(loggable.pageNum);
        final DOMFilePageHeader pageHeader = page.getPageHeader();
        if ((!pageHeader.getLsn().equals(Lsn.LSN_INVALID)) && requiresRedo(loggable, page)) {
            final RecordPos pos = page.findRecord(ItemId.getId(loggable.tid));
            SanityCheck.ASSERT(pos != null, 
                "Record not found: " + ItemId.getId(loggable.tid) + ": " + 
                page.page.getPageInfo() + "\n" + 
                debugPageContents(page));
            //Position the stream at the very beginning of the record
            final int startOffset = pos.offset - LENGTH_TID;
            if (ItemId.isLink(loggable.tid)) {
                final int end = pos.offset + LENGTH_FORWARD_LOCATION;
                System.arraycopy(page.data, end, page.data, startOffset, page.len - end);
                page.len = page.len - (LENGTH_DATA_LENGTH + LENGTH_FORWARD_LOCATION);
            } else {
                // get the record length
                short l = ByteConversion.byteToShort(page.data, pos.offset);
                if (ItemId.isRelocated(loggable.tid)) {
                    pos.offset += LENGTH_ORIGINAL_LOCATION;
                    l += LENGTH_ORIGINAL_LOCATION;
                }
                if (l == OVERFLOW) {
                    l += LENGTH_OVERFLOW_LOCATION;
                }
                // end offset
                final int end = startOffset + LENGTH_TID + LENGTH_DATA_LENGTH + l;
                final int dlen = pageHeader.getDataLength();
                // remove old value
                System.arraycopy(page.data, end, page.data, startOffset, dlen - end);
                page.setDirty(true);
                page.len = dlen - (LENGTH_TID + LENGTH_DATA_LENGTH + l);
            }
            if (page.len < 0) {
                LOG.error("page length < 0");
                //TODO : throw exception ? -pb
            }
            pageHeader.setDataLength(page.len);
            pageHeader.decRecordCount();
            pageHeader.setLsn(loggable.getLsn());
            page.setDirty(true);
            dataCache.add(page);
        }
    }

    void undoRemoveValue(final RemoveValueLoggable loggable) {
    	final DOMPage page = getDOMPage(loggable.pageNum);
    	final DOMFilePageHeader pageHeader = page.getPageHeader();
        int offset = loggable.offset;
        final short vlen = (short) loggable.oldData.length;
        if (offset < pageHeader.getDataLength()) {
            // make room for the removed value
            int required;
            if (ItemId.isLink(loggable.tid)) {
                required = LENGTH_TID + LENGTH_FORWARD_LOCATION;
            } else {
                required = LENGTH_TID + LENGTH_DATA_LENGTH + vlen;
            }
            if (ItemId.isRelocated(loggable.tid)) {
                required += LENGTH_ORIGINAL_LOCATION;
            }
            final int end = offset + required;
            try {
            	System.arraycopy(page.data, offset, page.data, end, pageHeader.getDataLength() - offset);
            } catch(final ArrayIndexOutOfBoundsException e) {
            	LOG.error(e);
                SanityCheck.TRACE("Error while copying data on page " + page.getPageNum() +
                    "; tid: " + ItemId.getId(loggable.tid) + "; required: " + required +
                    "; offset: " + offset + "; end: " + end +
                    "; len: " + (pageHeader.getDataLength() - offset) +
                    "; avail: " + page.data.length + "; work: " + fileHeader.getWorkSize());
            }
        }
        //save TID
        ByteConversion.shortToByte(loggable.tid, page.data, offset);
        offset += LENGTH_TID;
        if (ItemId.isLink(loggable.tid)) {
            System.arraycopy(loggable.oldData, 0, page.data, offset, LENGTH_FORWARD_LOCATION);
            page.len += (LENGTH_TID + LENGTH_FORWARD_LOCATION);
        } else {
            // save data length
            // overflow pages have length 0
            if (loggable.isOverflow) {
                ByteConversion.shortToByte(OVERFLOW, page.data, offset);
            } else {
                ByteConversion.shortToByte(vlen, page.data, offset);
            }
            offset += LENGTH_DATA_LENGTH;
            if (ItemId.isRelocated(loggable.tid)) {
                ByteConversion.longToByte(loggable.backLink, page.data, offset);
                offset += LENGTH_ORIGINAL_LOCATION;
                page.len += LENGTH_ORIGINAL_LOCATION;
            }
            // save data
            System.arraycopy(loggable.oldData, 0, page.data, offset, vlen);
            page.len += (LENGTH_TID + LENGTH_DATA_LENGTH + vlen);
        }
        pageHeader.incRecordCount();
        pageHeader.setDataLength(page.len);
        page.setDirty(true);
        dataCache.add(page, 2);
    }

    void redoRemoveEmptyPage(final RemoveEmptyPageLoggable loggable) {
        final DOMPage page = getDOMPage(loggable.pageNum);
        final DOMFilePageHeader pageHeader = page.getPageHeader();
        if ((!pageHeader.getLsn().equals(Lsn.LSN_INVALID)) && requiresRedo(loggable, page)) {
            removePage(page);
        }
    }

    void undoRemoveEmptyPage(final RemoveEmptyPageLoggable loggable) {
        try {
            final DOMPage newPage = getDOMPage(loggable.pageNum);
            final DOMFilePageHeader newPageHeader = newPage.getPageHeader();
            dropFreePageList();
            if (loggable.prevPage == NO_PAGE) {
                newPageHeader.setPrevDataPage(NO_PAGE);
            } else {
                final DOMPage oldPage = getDOMPage(loggable.prevPage);
                final DOMFilePageHeader oldPageHeader = oldPage.getPageHeader();
                newPageHeader.setPrevDataPage(oldPage.getPageNum());
                oldPageHeader.setNextDataPage(newPage.getPageNum());
                oldPage.setDirty(true);
                dataCache.add(oldPage);
            }
            if (loggable.nextPage == NO_PAGE) {
                newPageHeader.setNextDataPage(NO_PAGE);
            } else {
                final DOMPage oldPage = getDOMPage(loggable.nextPage);
                final DOMFilePageHeader oldPageHeader = oldPage.getPageHeader();
                oldPageHeader.setPrevDataPage(newPage.getPageNum());
                newPageHeader.setNextDataPage(loggable.nextPage);
                oldPage.setDirty(true);
                dataCache.add(oldPage);
            }
            newPageHeader.setNextTupleID(ItemId.UNKNOWN_ID);
            newPage.setDirty(true);
            dataCache.add(newPage);
        } catch (final IOException e) {
            LOG.error("Error during undo: {}", e.getMessage(), e);
            //TODO : throw exception ? -pb
        }
    }

    void redoRemovePage(final RemovePageLoggable loggable) {
        final DOMPage page = getDOMPage(loggable.pageNum);
        final DOMFilePageHeader pageHeader = page.getPageHeader();
        if ((!pageHeader.getLsn().equals(Lsn.LSN_INVALID)) && requiresRedo(loggable, page)) {
            try {
                pageHeader.setNextDataPage(NO_PAGE);
                pageHeader.setPrevDataPage(NO_PAGE);
                pageHeader.setDataLen(fileHeader.getWorkSize());
                pageHeader.setDataLength(0);
                pageHeader.setNextTupleID(ItemId.UNKNOWN_ID);
                pageHeader.setRecordCount((short) 0);
                page.len = 0;
                unlinkPages(page.page);
                page.setDirty(true);
                dataCache.remove(page);
            } catch (final IOException e) {
                LOG.warn("Error while removing page: {}", e.getMessage(), e);
                //TODO : throw exception ? -pb
            }
        }
    }

    void undoRemovePage(final RemovePageLoggable loggable) {
        try {
            final DOMPage page = getDOMPage(loggable.pageNum);
            final DOMFilePageHeader pageHeader = page.getPageHeader();
            dropFreePageList();
            pageHeader.setStatus(RECORD);
            pageHeader.setNextDataPage(loggable.nextPage);
            pageHeader.setPrevDataPage(loggable.prevPage);
            pageHeader.setNextTupleID(ItemId.getId(loggable.oldTid));
            pageHeader.setRecordCount(loggable.oldRecCnt);
            pageHeader.setDataLength(loggable.oldLen);
            System.arraycopy(loggable.oldData, 0, page.data, 0, loggable.oldLen);
            page.len = loggable.oldLen;
            page.setDirty(true);
            dataCache.add(page);
        } catch (final IOException e) {
            LOG.warn("Failed to undo {}: {}", loggable.dump(), e.getMessage(), e);
          //TODO : throw exception ? -pb
        }
    }

    void redoWriteOverflow(final WriteOverflowPageLoggable loggable) {
        try {
            final Page page = getPage(loggable.pageNum);
            final PageHeader pageHeader = page.getPageHeader();
            if ((!pageHeader.getLsn().equals(Lsn.LSN_INVALID)) && requiresRedo(loggable, page)) {

                dropFreePageList();
                pageHeader.setStatus(RECORD);
                if (loggable.nextPage == NO_PAGE) {
                    pageHeader.setNextPage(NO_PAGE);
                } else {
                    pageHeader.setNextPage(loggable.nextPage);
                }
                pageHeader.setLsn(loggable.getLsn());
                writeValue(page, loggable.value);
            }

        } catch (final IOException e) {
            LOG.warn("Failed to redo {}: {}", loggable.dump(), e.getMessage(), e);
            //TODO : throw exception ? -pb
        }
    }

    void undoWriteOverflow(final WriteOverflowPageLoggable loggable) {
        try {
            final Page page = getPage(loggable.pageNum);
            page.read();
            unlinkPages(page);
        } catch (final IOException e) {
            LOG.warn("Failed to undo {}: {}", loggable.dump(), e.getMessage(), e);
          //TODO : throw exception ? -pb
        }
    }

    void redoRemoveOverflow(final RemoveOverflowLoggable loggable) {
        try {
            final Page page = getPage(loggable.pageNum);
            page.read();
            final PageHeader pageHeader = page.getPageHeader();
            if ((!pageHeader.getLsn().equals(Lsn.LSN_INVALID)) && requiresRedo(loggable, page)) {
                unlinkPages(page);
            }
        } catch (final IOException e) {
            LOG.warn("Failed to undo {}: {}", loggable.dump(), e.getMessage(), e);
          //TODO : throw exception ? -pb
        }
    }

    void undoRemoveOverflow(final RemoveOverflowLoggable loggable) {
        try {
            final Page page = getPage(loggable.pageNum);
            page.read();
            final PageHeader pageHeader = page.getPageHeader();
            dropFreePageList();
            pageHeader.setStatus(RECORD);
            if (loggable.nextPage == NO_PAGE) {
                pageHeader.setNextPage(NO_PAGE);
            } else {
                pageHeader.setNextPage(loggable.nextPage);
            }
            writeValue(page, loggable.oldData);
        } catch (final IOException e) {
            LOG.warn("Failed to redo {}: {}", loggable.dump(), e.getMessage(), e);
          //TODO : throw exception ? -pb
        }
    }

    void redoInsertValue(final InsertValueLoggable loggable) {
        final DOMPage page = getDOMPage(loggable.pageNum);
        final DOMFilePageHeader pageHeader = page.getPageHeader();
        if ((!pageHeader.getLsn().equals(Lsn.LSN_INVALID)) && requiresRedo(loggable, page)) {
            final int dlen = pageHeader.getDataLength();
            int offset = loggable.offset;
            // insert in the middle of the page?
            if (offset < dlen) {
                final int end = offset + LENGTH_TID + LENGTH_DATA_LENGTH + loggable.value.length;
                try {
                    System.arraycopy(page.data, offset, page.data, end, dlen - offset);
                } catch(final ArrayIndexOutOfBoundsException e) {
                    LOG.error(e);
                    SanityCheck.TRACE("Error while copying data on page " + page.getPageNum() +
                        "; tid: " + loggable.tid +
                        "; offset: " + offset +
                        "; end: " + end +
                        "; len: " + (dlen - offset));
                }
            }
            // writing tid
            ByteConversion.shortToByte(loggable.tid, page.data, offset);
            offset += LENGTH_TID;
            page.len += LENGTH_TID;
            // writing value length
            ByteConversion.shortToByte(loggable.isOverflow() ?
                OVERFLOW : (short) loggable.value.length, page.data, offset);
            offset += LENGTH_DATA_LENGTH;
            page.len += LENGTH_DATA_LENGTH;
            // writing data
            System.arraycopy(loggable.value, 0, page.data, offset, loggable.value.length);
            offset += loggable.value.length;
            page.len += loggable.value.length;
            pageHeader.incRecordCount();
            pageHeader.setDataLength(page.len);
            pageHeader.setNextTupleID(ItemId.getId(loggable.tid));
            page.setDirty(true);
            dataCache.add(page);
        }
    }

    void undoInsertValue(final InsertValueLoggable loggable) {
        final DOMPage page = getDOMPage(loggable.pageNum);
        final DOMFilePageHeader pageHeader = page.getPageHeader();
        if (ItemId.isLink(loggable.tid)) {
            final int end = loggable.offset + LENGTH_FORWARD_LOCATION;
            //Position the stream at the very beginning of the record
            System.arraycopy(page.data, end, page.data, loggable.offset - LENGTH_TID, page.len - end);
            page.len = page.len - (LENGTH_DATA_LENGTH + LENGTH_FORWARD_LOCATION);
        } else {
            // get the record length
            final int offset = loggable.offset + LENGTH_TID;
            //TODO Strange : in the lines above, the offset seems to be positioned *after* the TID
            short l = ByteConversion.byteToShort(page.data, offset);
            if (ItemId.isRelocated(loggable.tid)) {
                l += LENGTH_ORIGINAL_LOCATION;
            }
            if (l == OVERFLOW) {
                l += LENGTH_OVERFLOW_LOCATION;
            }
            // end offset
            final int end = loggable.offset + (LENGTH_TID + LENGTH_DATA_LENGTH + l);
            final int dlen = pageHeader.getDataLength();
            // remove value
            try {
                System.arraycopy(page.data, end, page.data, loggable.offset, dlen - end);
            } catch (final ArrayIndexOutOfBoundsException e) {
                LOG.error(e);
                SanityCheck.TRACE("Error while copying data on page " + page.getPageNum() +
                    "; tid: " + loggable.tid +
                    "; offset: " + loggable.offset + 
                    "; end: " + end +
                    "; len: " + (dlen - end) +
                    "; dataLength: " + dlen);
            }
            page.len = dlen - (LENGTH_TID + LENGTH_DATA_LENGTH + l);
        }
        if (page.len < 0) {
            LOG.warn("page length < 0");
        }
        pageHeader.setDataLength(page.len);
        pageHeader.decRecordCount();
        pageHeader.setLsn(loggable.getLsn());
        page.setDirty(true);
        dataCache.add(page);
    }

    void redoSplitPage(final SplitPageLoggable loggable) {
        final DOMPage page = getDOMPage(loggable.pageNum);
        final DOMFilePageHeader pageHeader = page.getPageHeader();
        if ((!pageHeader.getLsn().equals(Lsn.LSN_INVALID)) && requiresRedo(loggable, page)) {
            final byte[] oldData = page.data;
            page.data = new byte[fileHeader.getWorkSize()];
            System.arraycopy(oldData, 0, page.data, 0, loggable.splitOffset);
            page.len = loggable.splitOffset;
            if (page.len < 0) {
                LOG.error("page length < 0");
            }
            pageHeader.setDataLength(page.len);
            pageHeader.setRecordCount(countRecordsInPage(page));
            page.setDirty(true);
            dataCache.add(page);
        }
    }

    void undoSplitPage(final SplitPageLoggable loggable) {
        final DOMPage page = getDOMPage(loggable.pageNum);
        final DOMFilePageHeader pageHeader = page.getPageHeader();
        page.data = loggable.oldData;
        page.len = loggable.oldLen;
        if (page.len < 0) {
            LOG.error("page length < 0");
        }
        pageHeader.setDataLength(page.len);
        pageHeader.setLsn(loggable.getLsn());
        page.setDirty(true);
        dataCache.add(page);
    }

    void redoAddLink(final AddLinkLoggable loggable) {
        final DOMPage page = getDOMPage(loggable.pageNum);
        final DOMFilePageHeader pageHeader = page.getPageHeader();
        if ((!pageHeader.getLsn().equals(Lsn.LSN_INVALID)) && requiresRedo(loggable, page)) {
            ByteConversion.shortToByte(ItemId.setIsLink(loggable.tid), page.data, page.len);
            page.len += LENGTH_TID;
            ByteConversion.longToByte(loggable.link, page.data, page.len);
            page.len += LENGTH_FORWARD_LOCATION;
            pageHeader.setNextTupleID(ItemId.getId(loggable.tid));
            pageHeader.setDataLength(page.len);
            pageHeader.setLsn(loggable.getLsn());
            pageHeader.incRecordCount();
            page.setDirty(true);
            dataCache.add(page);
        }
    }

    void undoAddLink(final AddLinkLoggable loggable) {
        final DOMPage page = getDOMPage(loggable.pageNum);
        final DOMFilePageHeader pageHeader = page.getPageHeader();
        final RecordPos rec = page.findRecord(loggable.tid);
        final int end = rec.offset + LENGTH_FORWARD_LOCATION;
        //Position the stream at the very beginning of the record
        System.arraycopy(page.data, end, page.data, rec.offset - LENGTH_TID, page.len - end);
        page.len = page.len - (LENGTH_TID + LENGTH_FORWARD_LOCATION);
        if (page.len < 0) {
            LOG.error("page length < 0");
        }
        pageHeader.setDataLength(page.len);
        pageHeader.decRecordCount();
        pageHeader.setLsn(loggable.getLsn());
        page.setDirty(true);
        dataCache.add(page);
    }

    
    void redoUpdateLink(final UpdateLinkLoggable loggable) {
        final DOMPage page = getDOMPage(loggable.pageNum);
        final DOMFilePageHeader pageHeader = page.getPageHeader();
        if ((!pageHeader.getLsn().equals(Lsn.LSN_INVALID)) && requiresRedo(loggable, page)) {
            ByteConversion.longToByte(loggable.link, page.data, loggable.offset);
            pageHeader.setLsn(loggable.getLsn());
            page.setDirty(true);
            dataCache.add(page);
        }
    }

    void undoUpdateLink(final UpdateLinkLoggable loggable) {
        final DOMPage page = getDOMPage(loggable.pageNum);
        final DOMFilePageHeader pageHeader = page.getPageHeader();
        ByteConversion.longToByte(loggable.oldLink, page.data, loggable.offset);
        pageHeader.setLsn(loggable.getLsn());
        page.setDirty(true);
        dataCache.add(page);
    }

    void redoAddMovedValue(final AddMovedValueLoggable loggable) {
        final DOMPage page = getDOMPage(loggable.pageNum);
        final DOMFilePageHeader pageHeader = page.getPageHeader();
        if ((!pageHeader.getLsn().equals(Lsn.LSN_INVALID)) && requiresRedo(loggable, page)) {
            try {
                ByteConversion.shortToByte(ItemId.setIsRelocated(loggable.tid), page.data, page.len);
                page.len += LENGTH_TID;
                final short vlen = (short) loggable.value.length;
                // save data length
                // overflow pages have length 0
                ByteConversion.shortToByte(vlen, page.data, page.len);
                page.len += LENGTH_DATA_LENGTH;
                ByteConversion.longToByte(loggable.backLink, page.data, page.len);
                page.len += LENGTH_FORWARD_LOCATION;
                // save data
                System.arraycopy(loggable.value, 0, page.data, page.len, vlen);
                page.len += vlen;
                //TODO understand why 2 occurrences of ph.incRecordCount(); ?
                pageHeader.incRecordCount();
                pageHeader.setDataLength(page.len);
                pageHeader.setNextTupleID(ItemId.getId(loggable.tid));
                pageHeader.incRecordCount();
                pageHeader.setLsn(loggable.getLsn());
                page.setDirty(true);
                dataCache.add(page, 2);
            } catch (final ArrayIndexOutOfBoundsException e) {
                LOG.error("page: {}; len = {}; value = {}", page.getPageNum(), page.len, loggable.value.length);
                throw e;
            }
        }
    }

    void undoAddMovedValue(final AddMovedValueLoggable loggable) {
        final DOMPage page = getDOMPage(loggable.pageNum);
        final DOMFilePageHeader pageHeader = page.getPageHeader();
        final RecordPos rec = page.findRecord(ItemId.getId(loggable.tid));
        SanityCheck.ASSERT(rec != null,
            "Record with tid " + ItemId.getId(loggable.tid) + " not found: " +
            debugPageContents(page));
        // get the record's length
        final short vlen = ByteConversion.byteToShort(page.data, rec.offset);
        final int end = rec.offset + LENGTH_DATA_LENGTH + LENGTH_ORIGINAL_LOCATION + vlen;
        final int dlen = pageHeader.getDataLength();
        // remove value
        try {
            //Position the stream at the very beginning of the record
            System.arraycopy(page.data, end, page.data, rec.offset - LENGTH_TID, dlen - end);
        } catch (final ArrayIndexOutOfBoundsException e) {
        	LOG.error(e);
            SanityCheck.TRACE("Error while copying data on page " + page.getPageNum() +
                  "; tid: " + loggable.tid +
                  "; offset: " + (rec.offset - LENGTH_TID) +
                  "; end: " + end + "; len: " + (dlen - end));
        }
        page.len = dlen - (LENGTH_TID + LENGTH_DATA_LENGTH + LENGTH_ORIGINAL_LOCATION + vlen);
        if (page.len < 0) {
            LOG.error("page length < 0");
            //TODO : throw exception ? -pb
        }
        pageHeader.setDataLength(page.len);
        pageHeader.decRecordCount();
        pageHeader.setLsn(loggable.getLsn());
        page.setDirty(true);
        dataCache.add(page);
    }

    void redoUpdateHeader(final UpdateHeaderLoggable loggable) {
        final DOMPage page = getDOMPage(loggable.pageNum);
        final DOMFilePageHeader pageHeader = page.getPageHeader();
        if ((!pageHeader.getLsn().equals(Lsn.LSN_INVALID)) && requiresRedo(loggable, page)) {
            if (loggable.nextPage != NO_PAGE) {
                pageHeader.setNextDataPage(loggable.nextPage);
            }
            if (loggable.prevPage != NO_PAGE) {
                pageHeader.setPrevDataPage(loggable.prevPage);
            }
            pageHeader.setLsn(loggable.getLsn());
            page.setDirty(true);
            dataCache.add(page, 2);
        }
    }

    void undoUpdateHeader(final UpdateHeaderLoggable loggable) {
        final DOMPage page = getDOMPage(loggable.pageNum);
        final DOMFilePageHeader pageHeader = page.getPageHeader();
        pageHeader.setPrevDataPage(loggable.oldPrev);
        pageHeader.setNextDataPage(loggable.oldNext);
        pageHeader.setLsn(loggable.getLsn());
        page.setDirty(true);
        dataCache.add(page, 2);
    }

    protected final class DOMFilePageHeader extends BTreePageHeader {

        int dataLength = 0;
        long nextDataPage = NO_PAGE;
        long previousDataPage = NO_PAGE;
        short tupleID = ItemId.UNKNOWN_ID;
        private short records = 0;

        static final short LENGTH_RECORDS_COUNT = 2; //sizeof short
        static final int LENGTH_DATA_LENGTH = 4; //sizeof int
        static final long LENGTH_NEXT_PAGE_POINTER = 8; //sizeof long
        static final long LENGTH_PREV_PAGE_POINTER = 8; //sizeof long
        static final short LENGTH_CURRENT_TID = 2; //sizeof short

        DOMFilePageHeader() {
            super();
        }

        DOMFilePageHeader(final byte[] data, final int offset) throws IOException {
            super(data, offset);
        }

        void decRecordCount() {
            //TODO : check negative value ? -pb
            records--;
        }

        short getCurrentTupleID() {
            //TODO : overflow check ? -pb
            return tupleID;
        }

        short getNextTupleID() {
            if (++tupleID == ItemId.ID_MASK) {
                throw new RuntimeException("No spare ids on page");
            }
            return tupleID;
        }

        boolean hasRoom() {
            return tupleID < ItemId.MAX_ID;
        }

        void setNextTupleID(final short tupleID) {
            if (tupleID > ItemId.MAX_ID) {
                throw new RuntimeException("TupleID overflow! TupleID = " + tupleID);
            }
            this.tupleID = tupleID;
        }

        int getDataLength() {
            return dataLength;
        }

        long getNextDataPage() {
            return nextDataPage;
        }

        long getPreviousDataPage() {
            return previousDataPage;
        }

        short getRecordCount() {
            return records;
        }

        void incRecordCount() {
            records++;
        }

        @Override
        public int read(final byte[] data, int offset) throws IOException {
            offset = super.read(data, offset);
            records = ByteConversion.byteToShort(data, offset);
            offset += LENGTH_RECORDS_COUNT;
            dataLength = ByteConversion.byteToInt(data, offset);
            offset += LENGTH_DATA_LENGTH;
            nextDataPage = ByteConversion.byteToLong(data, offset);
            offset += LENGTH_NEXT_PAGE_POINTER;
            previousDataPage = ByteConversion.byteToLong(data, offset);
            offset += LENGTH_PREV_PAGE_POINTER;
            tupleID = ByteConversion.byteToShort(data, offset);
            return offset + LENGTH_CURRENT_TID;
        }

        @Override
        public int write(final byte[] data, int offset) throws IOException {
            offset = super.write(data, offset);
            ByteConversion.shortToByte(records, data, offset);
            offset += LENGTH_RECORDS_COUNT;
            ByteConversion.intToByte(dataLength, data, offset);
            offset += LENGTH_DATA_LENGTH;
            ByteConversion.longToByte(nextDataPage, data, offset);
            offset += LENGTH_NEXT_PAGE_POINTER;
            ByteConversion.longToByte(previousDataPage, data, offset);
            offset += LENGTH_PREV_PAGE_POINTER;
            ByteConversion.shortToByte(tupleID, data, offset);
            return offset + LENGTH_CURRENT_TID;
        }

        void setDataLength(final int dataLength) {
            if (dataLength > fileHeader.getWorkSize()) {
                LOG.error("data too long for file header !");
                //TODO  :throw exception ? -pb
            }
            this.dataLength = dataLength;
        }

        void setNextDataPage(final long page) {
            nextDataPage = page;
        }

        void setPrevDataPage(final long page) {
            previousDataPage = page;
        }

        void setRecordCount(final short recs) {
            records = recs;
        }
    }

    protected final class DOMPage implements Cacheable {

        // the raw working data (without page header) of this page
        byte[] data;

        // the current size of the used data
        int len = 0;

        // the low-level page
        Page page;

        DOMFilePageHeader pageHeader;

        // fields required by Cacheable
        int refCount = 0;

        int timestamp = 0;

        // has the page been saved or is it dirty?
        boolean saved = true;

        // set to true if the page has been removed from the cache
        boolean invalidated = false;

        DOMPage() {
            this.page = createNewPage();
            this.pageHeader = (DOMFilePageHeader) page.getPageHeader();
            this.data = new byte[fileHeader.getWorkSize()];
            this.len = 0;
        }

        DOMPage(final long pos) {
            try {
                this.page = getPage(pos);
                load(page);
            } catch (final IOException ioe) {
                LOG.error(ioe);
                ioe.printStackTrace();
                //TODO  :throw exception ? -pb
            }
        }
        
        DOMPage(final Page page) {
            this.page = page;
            load(page);
        }

        Page createNewPage() {
            try {
                final Page page = getFreePage();
                final DOMFilePageHeader pageHeader = (DOMFilePageHeader) page.getPageHeader();
                pageHeader.setStatus(RECORD);
                pageHeader.setDirty(true);
                pageHeader.setNextDataPage(NO_PAGE);
                pageHeader.setPrevDataPage(NO_PAGE);
                pageHeader.setNextPage(NO_PAGE);
                pageHeader.setNextTupleID(ItemId.UNKNOWN_ID);
                pageHeader.setDataLength(0);
                pageHeader.setRecordCount((short) 0);
                if (currentDocument != null) {
                    currentDocument.incPageCount();
                }
                return page;
            } catch (final IOException ioe) {
                LOG.error(ioe);
                return null;
            }
        }

        RecordPos findRecord(final short targetId) {
            final int dlen = pageHeader.getDataLength();
            RecordPos rec = null;
            for (int pos = 0; pos < dlen;) {
                final short tupleID = ByteConversion.byteToShort(data, pos);
                pos += LENGTH_TID;
                if (ItemId.matches(tupleID, targetId)) {
                    if (ItemId.isLink(tupleID)) {
                        rec = new RecordPos(pos, this, tupleID, true);
                    } else {
                        rec = new RecordPos(pos, this, tupleID);
                    }
                    break;
                } else if (ItemId.isLink(tupleID)) {
                    pos += LENGTH_FORWARD_LOCATION;
                } else {
                    final short vlen = ByteConversion.byteToShort(data, pos);
                    pos += LENGTH_DATA_LENGTH;
                    if (vlen < 0) {
                        LOG.error("page = {}; pos = {}; vlen = {}; tupleID = {}; target = {}", page.getPageNum(), pos, vlen, tupleID, targetId);
                    }
                    if (ItemId.isRelocated(tupleID)) {
                        pos += LENGTH_ORIGINAL_LOCATION + vlen;
                    } else {
                        pos += vlen;
                    }
                    if (vlen == OVERFLOW) {
                        pos += LENGTH_OVERFLOW_LOCATION;
                    }
                }
            }
            return rec;
        }

        @Override
        public long getKey() {
            return page.getPageNum();
        }

        @Override
        public int getReferenceCount() {
            return refCount;
        }

        @Override
        public int decReferenceCount() {
            //TODO : check if the decrementation is allowed ? -pb
            return refCount > 0 ? --refCount : 0;
        }

        @Override
        public int incReferenceCount() {
            //TODO : check uf the incrementation is allowed ? -pb 
            if (refCount < Cacheable.MAX_REF) {
                refCount++;
            }
            return refCount;
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

        DOMFilePageHeader getPageHeader() {
            return pageHeader;
        }

        long getPageNum() {
            return page.getPageNum();
        }

        @Override
        public boolean isDirty() {
            return !saved;
        }

        void setDirty(final boolean dirty) {
            saved = !dirty;
            page.getPageHeader().setDirty(dirty);
        }

        private void load(final Page page) {
            try {
                data = page.read();
                pageHeader = (DOMFilePageHeader) page.getPageHeader();
                len = pageHeader.getDataLength();
                if (data.length == 0) {
                    data = new byte[fileHeader.getWorkSize()];
                    len = 0;
                    return;
                }
            } catch (final IOException ioe) {
                LOG.error(ioe);
                ioe.printStackTrace();
            }
            saved = true;
        }

        private void write() {
            if (page == null) {
                return;
            }

            try {
                if (!pageHeader.isDirty()) {
                    return;
                }
                pageHeader.setDataLength(len);
                writeValue(page, data);
                setDirty(false);
            } catch (final IOException ioe) {
                LOG.error(ioe);
                //TODO : throw exception ? -pb
            }
        }

        String dumpPage() {
            return "Contents of page " + page.getPageNum() + ": " + hexDump(data);
        }

        @Override
        public boolean sync(final boolean syncJournal) {
            if (isDirty()) {
                write();
                if (isRecoveryEnabled() && syncJournal && logManager.isPresent() && logManager.get().lastWrittenLsn().compareTo(pageHeader.getLsn()) < 0) {
                    logManager.ifPresent(l -> l.flush(true, false));
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean allowUnload() {
            return true;
        }

        @Override
        public boolean equals(final Object obj) {
            if(!(obj instanceof DOMPage other)) {
                return false;
            }

            return page.equals(other.page);
        }

        void invalidate() {
            invalidated = true;
        }

        boolean isInvalidated() {
            return invalidated;
        }

        /**
         * Walk through the page after records have been removed. Set the tid
         * counter to the next spare id that can be used for following
         * insertions.
         */
        void cleanUp() {
            final int dlen = pageHeader.getDataLength();
            short maxTupleID = 0;
            short recordCount = 0;
            for (int pos = 0; pos < dlen; recordCount++) {
                final short tupleID = ByteConversion.byteToShort(data, pos);
                pos += LENGTH_TID;
                if (ItemId.getId(tupleID) > ItemId.MAX_ID) {
                    LOG.error(debugPageContents(this));
                    throw new RuntimeException("TupleID overflow in page " + getPageNum());
                }
                if (ItemId.getId(tupleID) > maxTupleID) {
                    maxTupleID = ItemId.getId(tupleID);
                }
                if (ItemId.isLink(tupleID)) {
                    pos += LENGTH_FORWARD_LOCATION;
                } else {
                    final short vlen = ByteConversion.byteToShort(data, pos);
                    pos += LENGTH_DATA_LENGTH;
                    if (ItemId.isRelocated(tupleID)) {
                        pos += vlen == OVERFLOW ?
                            LENGTH_ORIGINAL_LOCATION + LENGTH_OVERFLOW_LOCATION : 
                            LENGTH_ORIGINAL_LOCATION + vlen;
                    } else {
                        pos += vlen == OVERFLOW ? LENGTH_OVERFLOW_LOCATION : vlen;
                    }
                }
            }
            pageHeader.setNextTupleID(maxTupleID);
        }
    }

    /**
     * This represents an overflow page. Overflow pages are created if the node
     * data exceeds the size of one page. An overflow page is a sequence of
     * DOMPages.
     * 
     * @author wolf
     * 
     */
    final class OverflowDOMPage {

        final Page firstPage;

        OverflowDOMPage() {
            firstPage = createNewPage();
            LOG.debug("Creating overflow page: {}", firstPage.getPageNum());
        }

        OverflowDOMPage(final long first) throws IOException {
            firstPage = getPage(first);
        }

        Page createNewPage() {
            try {
                final Page page = getFreePage();
                final DOMFilePageHeader pageHeader = (DOMFilePageHeader) page.getPageHeader();
                pageHeader.setStatus(RECORD);
                pageHeader.setDirty(true);
                pageHeader.setNextDataPage(NO_PAGE);
                pageHeader.setPrevDataPage(NO_PAGE);
                pageHeader.setNextPage(NO_PAGE);
                pageHeader.setNextTupleID(ItemId.UNKNOWN_ID);
                pageHeader.setDataLength(0);
                pageHeader.setRecordCount((short) 0);
                if (currentDocument != null) {
                    currentDocument.incPageCount();
                }
                return page;
            } catch (final IOException ioe) {
                LOG.error(ioe);
                return null;
            }
        }

        // Write binary resource from InputStream
        int write(final Txn transaction, final InputStream is) {
            int pageCount = 0;
            Page currentPage = firstPage;
            try {
                // Transfer bytes from InputStream to db
                final int chunkSize = fileHeader.getWorkSize();
                final byte[] buf = new byte[chunkSize];
                final byte[] altbuf = new byte[chunkSize];
                byte[] currbuf = buf;
                byte[] fullbuf = null;
                boolean isaltbuf = false;
                int len;
                int basebuf = 0;
                int basemax = chunkSize;
                boolean emptyPage = true;
                while((len = is.read(currbuf, basebuf, basemax))!=-1) {
                    emptyPage=false;
                    // We are going to use a buffer swapping technique
                    if(fullbuf != null) {
                        final Value value = new Value(fullbuf, 0, chunkSize);
                        final Page nextPage = createNewPage();
                        currentPage.getPageHeader().setNextPage(nextPage.getPageNum());
                        if (transaction != null && isRecoveryEnabled()) {
                            final long nextPageNum = nextPage.getPageNum();
                            final Loggable loggable = new WriteOverflowPageLoggable(
                                transaction, currentPage.getPageNum(),
                                nextPageNum , value);
                            writeToLog(loggable, currentPage);
                        }
                        writeValue(currentPage, value);
                        pageCount++;
                        currentPage = nextPage;
                        fullbuf=null;
                    }
                    // Let's swap the buffer
                    basebuf += len;
                    if(basebuf == chunkSize) {
                        fullbuf = currbuf;
                        currbuf = (isaltbuf)? buf : altbuf;
                        isaltbuf = !isaltbuf;
                        basebuf = 0;
                        basemax = chunkSize;
                    } else {
                        basemax -= len;
                    }
                }
                // Detecting a zero byte stream
                if(emptyPage) {
                    currentPage.setPageNum(NO_PAGE);
                    currentPage.getPageHeader().setNextPage(NO_PAGE);
                } else {
                    // Just in the limit of a page
                    if (fullbuf != null) {
                        basebuf = chunkSize;
                        currbuf = fullbuf;
                    }
                    final Value value = new Value(currbuf, 0, basebuf);
                    currentPage.getPageHeader().setNextPage(NO_PAGE);
                    if (transaction != null && isRecoveryEnabled()) {
                        final Loggable loggable = new WriteOverflowPageLoggable(
                            transaction, currentPage.getPageNum(), NO_PAGE, value);
                        writeToLog(loggable, currentPage);
                    }
                    writeValue(currentPage, value);
                    pageCount++;
                }
                // TODO what if remaining length == 0 ?
            } catch (final IOException ex) {
                LOG.error("IO error while writing overflow page", ex);
              //TODO : throw exception ? -pb
            }
            return pageCount;
        }

        int write(final Txn transaction, final byte[] data) {
            int pageCount = 0;
            try {
                Page currentPage = firstPage;
                int remaining = data.length;
                int pos = 0;
                while (remaining > 0) {
                    final int chunkSize = remaining > fileHeader.getWorkSize() ? 
                        fileHeader.getWorkSize() : remaining;
                    remaining -= chunkSize;
                    final Value value = new Value(data, pos, chunkSize);
                    final Page nextPage;
                    if (remaining > 0) {
                        nextPage = createNewPage();
                        currentPage.getPageHeader().setNextPage(nextPage.getPageNum());
                    } else {
                        nextPage = null;
                        currentPage.getPageHeader().setNextPage(NO_PAGE);
                    }
                    if (transaction != null && isRecoveryEnabled()) {
                        final Loggable loggable = new WriteOverflowPageLoggable(
                            transaction, currentPage.getPageNum(),
                            remaining > 0 ? nextPage.getPageNum() : NO_PAGE, value);
                        writeToLog(loggable, currentPage);
                    }
                    writeValue(currentPage, value);
                    pos += chunkSize;
                    currentPage = nextPage;
                    ++pageCount;
                }
            } catch (final IOException e) {
                LOG.warn("IO error while writing overflow page", e);
                //TODO : throw exception ? -pb
            }
            return pageCount;
        }

        byte[] read() {
            try(final UnsynchronizedByteArrayOutputStream os = new UnsynchronizedByteArrayOutputStream(32)) {
                streamTo(os);
                return os.toByteArray();
            } catch(final IOException ioe) {
                LOG.error(ioe);
                return null;
            }
        }

        void streamTo(final OutputStream os) {
            Page page = firstPage;
            int count = 0;
            while (page != null) {
                try {
                    final byte[] chunk = page.read();
                    os.write(chunk);
                    final long nextPageNumber = page.getPageHeader().getNextPage();
                    page = (nextPageNumber == NO_PAGE) ? null : getPage(nextPageNumber);
                } catch (final IOException e) {
                    LOG.error("IO error while loading overflow page {}; read: {}", firstPage.getPageNum(), count, e);
                    //TODO : too soft ? throw the exception ?
                    break;
                }
                count++;
            }
        }

        void delete(final Txn transaction) throws IOException {
            Page page = firstPage;
            while (page != null) {
                LOG.debug("Removing overflow page {}", page.getPageNum());
                final long nextPageNumber = page.getPageHeader().getNextPage();
                if (transaction != null && isRecoveryEnabled()) {
                    final byte[] chunk = page.read();
                    final Loggable loggable = new RemoveOverflowLoggable(transaction,
                        page.getPageNum(), nextPageNumber, chunk);
                    writeToLog(loggable, page);
                }
                unlinkPages(page);
                page = (nextPageNumber == NO_PAGE) ? null : getPage(nextPageNumber);
            }
        }

        long getPageNum() {
            return firstPage.getPageNum();
        }
    }

    private final class FindCallback implements BTreeCallback {
        static final int KEYS = 1;
        static final int VALUES = 0;

        private final int mode;
        private final List<Value> values = new ArrayList<>();

        FindCallback(final int mode) {
            this.mode = mode;
        }

        List<Value> getValues() {
            return values;
        }

        @Override
        public boolean indexInfo(final Value value, final long pointer) {
            switch (mode) {
                case VALUES:
                    final RecordPos rec = findRecord(pointer);
                    final short vlen = ByteConversion.byteToShort(rec.getPage().data, rec.offset);
                    values.add(new Value(rec.getPage().data, rec.offset + LENGTH_DATA_LENGTH, vlen));
                    return true;
                case KEYS:
                    values.add(value);
                    return true;
            }
            return false;
        }
    }

}
