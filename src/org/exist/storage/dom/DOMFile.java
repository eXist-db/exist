package org.exist.storage.dom;

/*
 * eXist Open Source Native XML Database 
 * Copyright (C) 2001-04, Wolfgang M. Meier
 * (wolfgang@exist-db.org)
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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Stack;

import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.StoredNode;
import org.exist.numbering.DLNBase;
import org.exist.numbering.NodeId;
import org.exist.storage.BrokerPool;
import org.exist.storage.BufferStats;
import org.exist.storage.NativeBroker;
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
import org.exist.storage.journal.LogEntryTypes;
import org.exist.storage.journal.Loggable;
import org.exist.storage.journal.Lsn;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.ReentrantReadWriteLock;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.Txn;
import org.exist.util.ByteConversion;
import org.exist.util.Configuration;
import org.exist.util.Lockable;
import org.exist.util.ReadOnlyException;
import org.exist.util.hashtable.Object2LongIdentityHashMap;
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
 * identified by a unique id, called tuple id (tid). Every node can thus be
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
 *  | tid | length | data |
 * 
 * 3) Relocated record:
 *  | tid | length | address pointer to original location | data |
 * 
 * 2) Forward link:
 *  | tid | address pointer |
 * 
 * tid and length each use two bytes (short), address pointers 8 bytes (long).
 * The upper two bits of the tid are used to indicate the type of the record
 * (see {@link org.exist.storage.dom.ItemId}).
 * 
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 */
public class DOMFile extends BTree implements Lockable {
	
    public static final String FILE_NAME = "dom.dbx";
    public static final String  FILE_KEY_IN_CONFIG = "db-connection.dom";

    public static final int LENGTH_TID = 2; //sizeof short
    public static final int LENGTH_DATA_LENGTH = 2; //sizeof short
    public static final int LENGTH_ORIGINAL_LOCATION = 8; //sizeof long
    public static final int LENGTH_FORWARD_LOCATION = 8; //sizeof long
    public static final int LENGTH_OVERFLOW_LOCATION = 8; //sizeof long

    /*
	 * Byte ids for the records written to the log file.
	 */
	public final static byte LOG_CREATE_PAGE = 0x10;
	public final static byte LOG_ADD_VALUE = 0x11;
	public final static byte LOG_REMOVE_VALUE = 0x12;
	public final static byte LOG_REMOVE_EMPTY_PAGE = 0x13;
	public final static byte LOG_UPDATE_VALUE = 0x14;
	public final static byte LOG_REMOVE_PAGE = 0x15;
	public final static byte LOG_WRITE_OVERFLOW = 0x16;
	public final static byte LOG_REMOVE_OVERFLOW = 0x17;
    public final static byte LOG_INSERT_RECORD = 0x18;
    public final static byte LOG_SPLIT_PAGE = 0x19;
    public final static byte LOG_ADD_LINK = 0x1A;
    public final static byte LOG_ADD_MOVED_REC = 0x1B;
    public final static byte LOG_UPDATE_HEADER = 0x1C;
    public final static byte LOG_UPDATE_LINK = 0x1D;

	static {
		// register log entry types for this db file
		LogEntryTypes.addEntryType(LOG_CREATE_PAGE, CreatePageLoggable.class);
		LogEntryTypes.addEntryType(LOG_ADD_VALUE, AddValueLoggable.class);
		LogEntryTypes.addEntryType(LOG_REMOVE_VALUE, RemoveValueLoggable.class);
		LogEntryTypes.addEntryType(LOG_REMOVE_EMPTY_PAGE, RemoveEmptyPageLoggable.class);
		LogEntryTypes.addEntryType(LOG_UPDATE_VALUE, UpdateValueLoggable.class);
		LogEntryTypes.addEntryType(LOG_REMOVE_PAGE, RemovePageLoggable.class);
		LogEntryTypes.addEntryType(LOG_WRITE_OVERFLOW, WriteOverflowPageLoggable.class);
		LogEntryTypes.addEntryType(LOG_REMOVE_OVERFLOW, RemoveOverflowLoggable.class);
        LogEntryTypes.addEntryType(LOG_INSERT_RECORD, InsertValueLoggable.class);
        LogEntryTypes.addEntryType(LOG_SPLIT_PAGE, SplitPageLoggable.class);
        LogEntryTypes.addEntryType(LOG_ADD_LINK, AddLinkLoggable.class);
        LogEntryTypes.addEntryType(LOG_ADD_MOVED_REC, AddMovedValueLoggable.class);
        LogEntryTypes.addEntryType(LOG_UPDATE_HEADER, UpdateHeaderLoggable.class);
        LogEntryTypes.addEntryType(LOG_UPDATE_LINK, UpdateLinkLoggable.class);
	}

	public final static short FILE_FORMAT_VERSION_ID = 4;

	// page types
	public final static byte LOB = 21;

	public final static byte RECORD = 20;

	public final static short OVERFLOW = 0;

	public final static long DATA_SYNC_PERIOD = 4200;

	private final Cache dataCache;

	private BTreeFileHeader fileHeader;

	private Object owner = null;

	private Lock lock = null;

	private final Object2LongIdentityHashMap pages = new Object2LongIdentityHashMap(
			64);

	private DocumentImpl currentDocument = null;

    private final AddValueLoggable addValueLog = new AddValueLoggable();
    
	public DOMFile(BrokerPool pool, byte id, String dataDir, Configuration config) throws DBException {
		super(pool, id, true, pool.getCacheManager(), 0.01);
		lock = new ReentrantReadWriteLock(getFileName());
		fileHeader = (BTreeFileHeader) getFileHeader();
		fileHeader.setPageCount(0);
		fileHeader.setTotalCount(0);
        dataCache = new LRUCache(256, 0.0, 1.0);
        dataCache.setFileName(getFileName());
        cacheManager.registerCache(dataCache);
		File file = new File(dataDir + File.separatorChar + getFileName());
		setFile(file);
		if (exists()) {
			open();
		} else {
			if (LOG.isDebugEnabled())
				LOG.debug("Creating data file: " + file.getName());
			create();
		}
		config.setProperty(getConfigKeyForFile(), this);	
	}
	
    public static String getFileName() {
    	return FILE_NAME;      
    }
    
    public static String getConfigKeyForFile() {
    	return FILE_KEY_IN_CONFIG;
    }	

	protected final Cache getPageBuffer() {
		return dataCache;
	}

	/**
	 * @return file version.
	 */
	public short getFileVersion() {
		return FILE_FORMAT_VERSION_ID;
	}

	public void setCurrentDocument(DocumentImpl doc) {
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
	 * @param value the value to append
	 * @return the virtual storage address of the value
	 */
	public long add(Txn transact, byte[] value) throws ReadOnlyException {
		if (value == null || value.length == 0)
			return KEY_NOT_FOUND;
		// overflow value?
		if (value.length + 2 + 2 > fileHeader.getWorkSize()) {
			LOG.debug("Creating overflow page");
			OverflowDOMPage overflow = new OverflowDOMPage(transact);
			overflow.write(transact, value);
			byte[] pnum = ByteConversion.longToByte(overflow.getPageNum());
			return add(transact, pnum, true);
		} else
			return add(transact, value, false);
	}

	/**
	 * Append a value to the current page. If overflowPage is true, the value
	 * will be saved into its own, reserved chain of pages. The current page
	 * will just contain a link to the first overflow page.
	 * 
	 * @param value
	 * @param overflowPage
	 * @return the virtual storage address of the value
	 * @throws ReadOnlyException
	 */
	private long add(Txn transaction, byte[] value, boolean overflowPage) throws ReadOnlyException {
		final int vlen = value.length;
		// always append data to the end of the file
		DOMPage page = getCurrentPage(transaction);
		// does value fit into current data page?
		if (page == null || page.len + LENGTH_TID + LENGTH_DATA_LENGTH + vlen > page.data.length) {
			DOMPage newPage = new DOMPage();
			if (page != null) {
				DOMFilePageHeader ph = page.getPageHeader();
                
                if (isTransactional && transaction != null) {
                    UpdateHeaderLoggable loggable = new UpdateHeaderLoggable(
                            transaction, ph.getPrevDataPage(), page.getPageNum(),
                            newPage.getPageNum(), ph.getPrevDataPage(), ph.getNextDataPage()
                    );
                    writeToLog(loggable, page.page);
                }
				ph.setNextDataPage(newPage.getPageNum());
				newPage.getPageHeader().setPrevDataPage(page.getPageNum());
				page.setDirty(true);
				dataCache.add(page);
			}
			if (isTransactional && transaction != null) {
				CreatePageLoggable loggable = new CreatePageLoggable(
						transaction, page == null ? Page.NO_PAGE : page.getPageNum(),
						newPage.getPageNum(), Page.NO_PAGE);
				writeToLog(loggable, newPage.page);

			}
			page = newPage;
			setCurrentPage(newPage);
		}
		// save tuple identifier
		final DOMFilePageHeader ph = page.getPageHeader();
		final short tid = ph.getNextTID();
		// LOG.debug("writing to " + page.getPageNum() + "; " + page.len + ";
		// tid = " + tid +
		// "; len = " + valueLen + "; dataLen = " + page.data.length);

		if (isTransactional && transaction != null) {
            addValueLog.clear(transaction, page.getPageNum(), tid, value);
			writeToLog(addValueLog, page.page);
		}

		// save TID
		ByteConversion.shortToByte(tid, page.data, page.len);
		page.len += LENGTH_TID;
		// save data length
		// overflow pages have length 0
		ByteConversion.shortToByte(overflowPage ? OVERFLOW : (short) vlen, page.data, page.len);
		page.len += LENGTH_DATA_LENGTH;
		// save data
		System.arraycopy(value, 0, page.data, page.len, vlen);
		page.len += vlen;
		ph.incRecordCount();
		ph.setDataLength(page.len);
		page.setDirty(true);
		dataCache.add(page, 2);
		// create pointer from pageNum and offset into page
		final long p = StorageAddress.createPointer((int) page.getPageNum(),
				tid);
		return p;
	}

	private void writeToLog(Loggable loggable, Page page) {
		try {
			logManager.writeToLog(loggable);
			page.getPageHeader().setLsn(loggable.getLsn());
		} catch (TransactionException e) {
			LOG.warn(e.getMessage(), e);
		}
	}

	/**
	 * Store a raw binary resource into the file. The data will always be
	 * written into an overflow page.
	 * 
	 * @param value     Binary resource as byte array
	 */
        public long addBinary(Txn transaction, DocumentImpl doc, byte[] value) {
            OverflowDOMPage overflow = new OverflowDOMPage(transaction);
            int pagesCount = overflow.write(transaction, value);
            doc.getMetadata().setPageCount(pagesCount);
            return overflow.getPageNum();
        }
        
        
        /**
         * Store a raw binary resource into the file. The data will always be
         * written into an overflow page.
         * 
         * @param is   Binary resource as stream.
         */
        public long addBinary(Txn transaction, DocumentImpl doc, InputStream is) {
            OverflowDOMPage overflow = new OverflowDOMPage(transaction);
            int pagesCount = overflow.write(transaction, is);
            doc.getMetadata().setPageCount(pagesCount);
            return overflow.getPageNum();
        }

	/**
	 * Return binary data stored with {@link #addBinary(Txn, DocumentImpl, byte[])}.
	 * 
	 * @param pageNum
	 */
	public byte[] getBinary(long pageNum) {
		return getOverflowValue(pageNum);
	}

	public void readBinary(long pageNum, OutputStream os) {
		try {
			OverflowDOMPage overflow = new OverflowDOMPage(pageNum);
			overflow.streamTo(os);
		} catch (IOException e) {
			LOG.error("io error while loading overflow value", e);
		}
	}
	
	/**
	 * Insert a new node after the specified node.
	 * 
	 * @param key
	 * @param value
	 */
	public long insertAfter(Txn transaction, DocumentImpl doc, Value key, byte[] value) {
		try {
			final long p = findValue(key);
			if (p == KEY_NOT_FOUND)
				return p;
			return insertAfter(transaction, doc, p, value);
		} catch (BTreeException e) {
			LOG.warn("key not found", e);
		} catch (IOException e) {
			LOG.warn("IO error", e);
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
	 * @param doc
	 *                     the document to which the new node belongs.
	 * @param address
	 *                     the storage address of the node after which the new value
	 *                     should be inserted.
	 * @param value
	 *                     the value of the new node.
	 */
	public long insertAfter(Txn transaction, DocumentImpl doc, long address, byte[] value) {
		// check if we need an overflow page
		boolean isOverflow = false;
		if (value.length + 2 + 2 > fileHeader.getWorkSize()) {
			OverflowDOMPage overflow = new OverflowDOMPage(transaction);
			LOG.debug("creating overflow page: " + overflow.getPageNum());
			overflow.write(transaction, value);
			value = ByteConversion.longToByte(overflow.getPageNum());
			isOverflow = true;
		}
		// locate the node to insert after
		RecordPos rec = findRecord(address);
		if (rec == null) {
			SanityCheck.TRACE("page not found");
			return KEY_NOT_FOUND;
		}
		short len = ByteConversion.byteToShort(rec.getPage().data, rec.offset);
		rec.offset += LENGTH_DATA_LENGTH;
		if (ItemId.isRelocated(rec.getTID()))
			rec.offset += LENGTH_ORIGINAL_LOCATION;
		if (len == OVERFLOW)
			rec.offset += LENGTH_OVERFLOW_LOCATION;
		else
			rec.offset += len;
        
		int dlen = rec.getPage().getPageHeader().getDataLength();
		// insert in the middle of the page?
		if (rec.offset < dlen) {
			if (dlen + value.length + 2 + 2 < fileHeader.getWorkSize()
					&& rec.getPage().getPageHeader().hasRoom()) {
//				 LOG.debug("copying data in page " + rec.getPage().getPageNum()
//				 + "; offset = " + rec.offset + "; dataLen = "
//				 + dataLen + "; valueLen = " + value.length);
				// new value fits into the page
				int end = rec.offset + value.length + 2 + 2;
				System.arraycopy(rec.getPage().data, rec.offset, rec.getPage().data, end,
						dlen - rec.offset);
				rec.getPage().len = dlen + value.length + 2 + 2;
				rec.getPage().getPageHeader().setDataLength(rec.getPage().len);
			} else {
				// doesn't fit: split the page
				rec = splitDataPage(transaction, doc, rec);
				if (rec.offset + value.length + 2 + 2 > fileHeader.getWorkSize()
						|| !rec.getPage().getPageHeader().hasRoom()) {
					// still not enough free space: create a new page
					DOMPage newPage = new DOMPage();
					LOG.debug("creating additional page: "
							+ newPage.getPageNum() + "; prev = " + rec.getPage().getPageNum() + "; next = " +
                            rec.getPage().ph.getNextDataPage());
                    
                    if (isTransactional && transaction != null) {
                        CreatePageLoggable loggable = new CreatePageLoggable(
                                transaction, rec.getPage().getPageNum(),
                                newPage.getPageNum(), rec.getPage().ph.getNextDataPage());
                        writeToLog(loggable, newPage.page);
                    }
                    
                    // adjust page links
					newPage.getPageHeader().setNextDataPage(
							rec.getPage().getPageHeader().getNextDataPage());
					newPage.getPageHeader().setPrevDataPage(
							rec.getPage().getPageNum());
                    if (isTransactional && transaction != null) {
                        UpdateHeaderLoggable loggable = new UpdateHeaderLoggable(
                                transaction, rec.getPage().ph.getPrevDataPage(), rec.getPage().getPageNum(),
                                newPage.getPageNum(), rec.getPage().ph.getPrevDataPage(), rec.getPage().ph.getNextDataPage()
                        );
                        writeToLog(loggable, rec.getPage().page);
                    }
                    rec.getPage().getPageHeader().setNextDataPage(
                            newPage.getPageNum());
                    if (newPage.ph.getNextDataPage() != Page.NO_PAGE) {
                        // link the next page in the chain back to the new page inserted 
                        DOMPage nextInChain = getCurrentPage(newPage.ph.getNextDataPage());
                        
                        if (isTransactional && transaction != null) {
                            UpdateHeaderLoggable loggable = 
                                new UpdateHeaderLoggable(transaction, newPage.getPageNum(), nextInChain.getPageNum(), 
                                        nextInChain.ph.getNextDataPage(), nextInChain.ph.getPrevDataPage(), 
                                        nextInChain.ph.getNextDataPage());
                            writeToLog(loggable, nextInChain.page);
                        }
                        
                        nextInChain.ph.setPrevDataPage(newPage.getPageNum());
                        nextInChain.setDirty(true);
                        dataCache.add(nextInChain);
                    }
                    
					rec.getPage().setDirty(true);
					dataCache.add(rec.getPage());
					rec.setPage(newPage);
					rec.offset = 0;
					rec.getPage().len = value.length + 2 + 2;
					rec.getPage().getPageHeader().setDataLength(rec.getPage().len);
					rec.getPage().getPageHeader().setRecordCount((short) 1);
				} else {
					rec.getPage().len = rec.offset + value.length + 2 + 2;
					rec.getPage().getPageHeader().setDataLength(rec.getPage().len);
					dlen = rec.offset;
				}
			}
		} else if (dlen + value.length + 2 + 2 > fileHeader.getWorkSize()
				|| !rec.getPage().getPageHeader().hasRoom()) {
			// does value fit into page?
			DOMPage newPage = new DOMPage();
			LOG.debug("creating new page: " + newPage.getPageNum());
            if (isTransactional && transaction != null) {
                CreatePageLoggable loggable = new CreatePageLoggable(
                        transaction, rec.getPage().getPageNum(),
                        newPage.getPageNum(), rec.getPage().ph.getNextDataPage());
                writeToLog(loggable, newPage.page);

            }
            
			long nextPageNr = rec.getPage().getPageHeader().getNextDataPage();
			newPage.getPageHeader().setNextDataPage(nextPageNr);
			newPage.getPageHeader().setPrevDataPage(rec.getPage().getPageNum());
            
            if (isTransactional && transaction != null) {
                UpdateHeaderLoggable loggable = 
                    new UpdateHeaderLoggable(transaction, rec.getPage().ph.getPrevDataPage(), rec.getPage().getPageNum(), 
                            newPage.getPageNum(), rec.getPage().ph.getPrevDataPage(), 
                            rec.getPage().ph.getNextDataPage());
                writeToLog(loggable, rec.getPage().page);
            }
            rec.getPage().getPageHeader().setNextDataPage(newPage.getPageNum());
            
			if (Page.NO_PAGE != nextPageNr) {
				DOMPage nextPage = getCurrentPage(nextPageNr);
				
                if (isTransactional && transaction != null) {
                    UpdateHeaderLoggable loggable = 
                        new UpdateHeaderLoggable(transaction, newPage.getPageNum(), nextPage.getPageNum(), 
                                nextPage.ph.getNextDataPage(), nextPage.ph.getPrevDataPage(), 
                                nextPage.ph.getNextDataPage());
                    writeToLog(loggable, nextPage.page);
                }
                
				nextPage.getPageHeader().setPrevDataPage(newPage.getPageNum());
				nextPage.setDirty(true);
				dataCache.add(nextPage);
			}
			rec.getPage().setDirty(true);
			dataCache.add(rec.getPage());
			rec.setPage(newPage);
			rec.offset = 0;
			rec.getPage().len = value.length + 2+ 2;
			rec.getPage().getPageHeader().setDataLength(rec.getPage().len);
		} else {
			rec.getPage().len = dlen + value.length + 2 + 2;
			rec.getPage().getPageHeader().setDataLength(rec.getPage().len);
		}
		// write the data
		short tid = rec.getPage().getPageHeader().getNextTID();
        
        if (isTransactional && transaction != null) {
            Loggable loggable = 
                new InsertValueLoggable(transaction, rec.getPage().getPageNum(), isOverflow, tid, value, rec.offset);
            writeToLog(loggable, rec.getPage().page);
        }
        
		// writing tid
		ByteConversion.shortToByte((short) tid, rec.getPage().data, rec.offset);
		rec.offset += LENGTH_TID;
		// writing value length
		ByteConversion.shortToByte(isOverflow ? OVERFLOW : (short) value.length,
				rec.getPage().data, rec.offset);
		rec.offset += LENGTH_DATA_LENGTH;
		// writing data
		System.arraycopy(value, 0, rec.getPage().data, rec.offset, value.length);
		rec.offset += value.length;
		rec.getPage().getPageHeader().incRecordCount();
		rec.getPage().setDirty(true);
		if (doc != null && rec.getPage().getPageHeader().getCurrentTID() >= ItemId.DEFRAG_LIMIT)
			doc.triggerDefrag();
		// LOG.debug(debugPageContents(rec.page));
		dataCache.add(rec.getPage());
//        LOG.debug(debugPages(doc));
		return StorageAddress.createPointer((int) rec.getPage().getPageNum(), tid);
	}

	/**
	 * Split a data page at the position indicated by the rec parameter.
	 * 
	 * The portion of the page starting at rec.offset is moved into a new page.
	 * Every moved record is marked as relocated and a link is stored into the
	 * original page to point to the new record position.
	 * 
	 * @param doc
	 * @param rec
	 */
	private RecordPos splitDataPage(Txn transaction, DocumentImpl doc, RecordPos rec) {
		if (currentDocument != null)
			currentDocument.getMetadata().incSplitCount();
		// check if a split is really required. A split is not required if all
		// records following the split point are already links to other pages. In this
		// case, the new record is just appended to a new page linked to the old one.
		boolean requireSplit = false;
		for (int pos = rec.offset; pos < rec.getPage().len;) {
			short tid = ByteConversion.byteToShort(rec.getPage().data, pos);
			pos += LENGTH_TID;
			if (!ItemId.isLink(tid)) {
				requireSplit = true;
				break;
			}
			pos += LENGTH_FORWARD_LOCATION;
		}
		if (!requireSplit) {
			LOG.debug("page " + rec.getPage().getPageNum() + ": no split required");
			rec.offset = rec.getPage().len;
			return rec;
		}

		// copy the old data up to the split point into a new array
        int oldDataLen = rec.getPage().getPageHeader().getDataLength();
        byte[] oldData = rec.getPage().data;
        
        if (isTransactional && transaction != null) {
            Loggable loggable = new SplitPageLoggable(transaction, rec.getPage().getPageNum(), rec.offset,
                    oldData, oldDataLen);
            writeToLog(loggable, rec.getPage().page);
        }
        
		rec.getPage().data = new byte[fileHeader.getWorkSize()];
		System.arraycopy(oldData, 0, rec.getPage().data, 0, rec.offset);

		// the old rec.page now contains a copy of the data up to the split
		// point
		rec.getPage().len = rec.offset;
		rec.getPage().setDirty(true);
        
		// create a first split page
		DOMPage firstSplitPage = new DOMPage();
        
        if (isTransactional && transaction != null) {
            Loggable loggable = new CreatePageLoggable(
                    transaction, rec.getPage().getPageNum(), firstSplitPage.getPageNum(), Page.NO_PAGE,
                    rec.getPage().getPageHeader().getCurrentTID());
            writeToLog(loggable, firstSplitPage.page);
        }
        
		DOMPage nextSplitPage = firstSplitPage;
		nextSplitPage.getPageHeader().setNextTID(
				(short) (rec.getPage().getPageHeader().getCurrentTID()));
		long backLink;
		short splitRecordCount = 0;
		LOG.debug("splitting " + rec.getPage().getPageNum() + " at " + rec.offset
				+ ": new: " + nextSplitPage.getPageNum() + "; next: "
				+ rec.getPage().getPageHeader().getNextDataPage());

		// start copying records from rec.offset to the new split pages
		for (int pos = rec.offset; pos < oldDataLen; splitRecordCount++) {
			// read the current id
			short tid = ByteConversion.byteToShort(oldData, pos);
			pos += LENGTH_TID;
			if (ItemId.isLink(tid)) {
				/* This is already a link, so we just copy it */
			    if (rec.getPage().len + LENGTH_TID + LENGTH_FORWARD_LOCATION > fileHeader.getWorkSize()) {
                    /* no room in the old page, append a new one */
                    DOMPage newPage = new DOMPage();
                    
                    if (isTransactional && transaction != null) {
                        Loggable loggable = new CreatePageLoggable(
                                transaction, rec.getPage().getPageNum(), newPage.getPageNum(),
                                rec.getPage().getPageHeader().getNextDataPage(),
                                rec.getPage().getPageHeader().getCurrentTID());
                        writeToLog(loggable, firstSplitPage.page);
                        
                        loggable = new UpdateHeaderLoggable(transaction, rec.getPage().ph.getPrevDataPage(), 
                                rec.getPage().getPageNum(), newPage.getPageNum(), rec.getPage().ph.getPrevDataPage(),
                                rec.getPage().ph.getNextDataPage());
                        writeToLog(loggable, nextSplitPage.page);
                    }
                    
                    newPage.getPageHeader().setNextTID((short)(rec.getPage().getPageHeader().getCurrentTID()));
                    newPage.getPageHeader().setPrevDataPage(rec.getPage().getPageNum());
                    newPage.getPageHeader().setNextDataPage(rec.getPage().getPageHeader().getNextDataPage());
                    LOG.debug("appending page after split: " + newPage.getPageNum());
                    rec.getPage().getPageHeader().setNextDataPage(newPage.getPageNum());
                    rec.getPage().getPageHeader().setDataLength(rec.getPage().len);
                    rec.getPage().getPageHeader().setRecordCount(countRecordsInPage(rec.getPage()));
                    rec.getPage().setDirty(true);
                    dataCache.add(rec.getPage());
                    dataCache.add(newPage);
                    rec.setPage(newPage);
                    rec.getPage().len = 0;
                }

                if (isTransactional && transaction != null) {
                    long oldLink = ByteConversion.byteToLong(oldData, pos);
                    Loggable loggable = new AddLinkLoggable(transaction, rec.getPage().getPageNum(), ItemId.getId(tid), oldLink);
                    writeToLog(loggable, rec.getPage().page);
                }
                
				ByteConversion.shortToByte(tid, rec.getPage().data, rec.getPage().len);
				rec.getPage().len += LENGTH_TID;
				System.arraycopy(oldData, pos, rec.getPage().data, rec.getPage().len, LENGTH_FORWARD_LOCATION);
				rec.getPage().len += LENGTH_FORWARD_LOCATION;
				pos += LENGTH_FORWARD_LOCATION;
				continue;
			}
			// read data length
			short storedLen = ByteConversion.byteToShort(oldData, pos);
			pos += LENGTH_DATA_LENGTH;
			// if this is an overflow page, the real data length is always 8
			// byte for the page number of the overflow page
			short realLen = (storedLen == OVERFLOW ? LENGTH_OVERFLOW_LOCATION : storedLen);

			// check if we have room in the current split page
			if (nextSplitPage.len + LENGTH_TID + LENGTH_DATA_LENGTH + LENGTH_ORIGINAL_LOCATION + realLen > fileHeader.getWorkSize()) {
				// not enough room in the split page: append a new page
				DOMPage newPage = new DOMPage();
                
                if (isTransactional && transaction != null) {
                    Loggable loggable = new CreatePageLoggable(
                            transaction, nextSplitPage.getPageNum(), newPage.getPageNum(), Page.NO_PAGE,
                            rec.getPage().getPageHeader().getCurrentTID());
                    writeToLog(loggable, firstSplitPage.page);
                    
                    loggable = new UpdateHeaderLoggable(transaction, nextSplitPage.ph.getPrevDataPage(), 
                            nextSplitPage.getPageNum(), newPage.getPageNum(),
                            nextSplitPage.ph.getPrevDataPage(), nextSplitPage.ph.getNextDataPage());
                    writeToLog(loggable, nextSplitPage.page);
                }
                
				newPage.getPageHeader().setNextTID(rec.getPage().getPageHeader().getCurrentTID());
				newPage.getPageHeader().setPrevDataPage(
						nextSplitPage.getPageNum());
				LOG.debug("creating new split page: " + newPage.getPageNum());
				nextSplitPage.getPageHeader().setNextDataPage(
						newPage.getPageNum());
				nextSplitPage.getPageHeader().setDataLength(nextSplitPage.len);
				nextSplitPage.getPageHeader().setRecordCount(splitRecordCount);
				nextSplitPage.setDirty(true);
				dataCache.add(nextSplitPage);
				dataCache.add(newPage);
				nextSplitPage = newPage;
				splitRecordCount = 0;
			}

			/*
			 * if the record has already been relocated, read the original
			 * storage address and update the link there.
			 */
			if (ItemId.isRelocated(tid)) {
				backLink = ByteConversion.byteToLong(oldData, pos);
				pos += LENGTH_ORIGINAL_LOCATION;
				RecordPos origRec = findRecord(backLink, false);
                long oldLink = ByteConversion.byteToLong(origRec.getPage().data, origRec.offset);                
				long forwardLink = StorageAddress.createPointer(
						(int) nextSplitPage.getPageNum(), ItemId.getId(tid));
                
                if (isTransactional && transaction != null) {
                    Loggable loggable = new UpdateLinkLoggable(transaction, origRec.getPage().getPageNum(), origRec.offset, 
                            forwardLink, oldLink);
                    writeToLog(loggable, origRec.getPage().page);
                }
                
				ByteConversion.longToByte(forwardLink, origRec.getPage().data, origRec.offset);
				origRec.getPage().setDirty(true);
				dataCache.add(origRec.getPage());
			} else
				backLink = StorageAddress.createPointer((int) rec.getPage().getPageNum(), ItemId.getId(tid));

			/*
             * save the record to the split page:
			 */

            if (isTransactional && transaction != null) {
                byte[] logData = new byte[realLen];
                System.arraycopy(oldData, pos, logData, 0, realLen);
                Loggable loggable = new AddMovedValueLoggable(transaction, nextSplitPage.getPageNum(),
                        tid, logData, backLink);
                writeToLog(loggable, nextSplitPage.page);
            }
            
			// set the relocated flag and save the item id
			ByteConversion.shortToByte(ItemId.setIsRelocated(tid), nextSplitPage.data, nextSplitPage.len);
			nextSplitPage.len += LENGTH_TID;
			// save length field
			ByteConversion.shortToByte(storedLen, nextSplitPage.data, nextSplitPage.len);
			nextSplitPage.len += LENGTH_DATA_LENGTH;
			// save link to the original page
			ByteConversion.longToByte(backLink, nextSplitPage.data,	nextSplitPage.len);
			nextSplitPage.len += LENGTH_ORIGINAL_LOCATION;

			// now save the data
			try {
				System.arraycopy(oldData, pos, nextSplitPage.data, nextSplitPage.len, realLen);
			} catch (ArrayIndexOutOfBoundsException e) {
				SanityCheck.TRACE("pos = " + pos + "; len = "
						+ nextSplitPage.len + "; currentLen = " + realLen
						+ "; tid = " + tid + "; page = "
						+ rec.getPage().getPageNum());
				throw e;
			}
			nextSplitPage.len += realLen;
			pos += realLen;

			// save a link pointer in the original page if the record has not
			// been relocated before.
			if (!ItemId.isRelocated(tid)) {
				if (rec.getPage().len + LENGTH_TID + LENGTH_FORWARD_LOCATION > fileHeader.getWorkSize()) {
					// the link doesn't fit into the old page. Append a new page
					DOMPage newPage = new DOMPage();
                    
                    if (isTransactional && transaction != null) {
                        Loggable loggable = new CreatePageLoggable(
                                transaction, rec.getPage().getPageNum(), newPage.getPageNum(),
                                rec.getPage().getPageHeader().getNextDataPage(),
                                rec.getPage().getPageHeader().getCurrentTID());
                        writeToLog(loggable, firstSplitPage.page);
                        
                        loggable = new UpdateHeaderLoggable(transaction, rec.getPage().ph.getPrevDataPage(), 
                                rec.getPage().getPageNum(), newPage.getPageNum(), rec.getPage().ph.getPrevDataPage(), 
                                rec.getPage().ph.getNextDataPage());
                        writeToLog(loggable, nextSplitPage.page);
                    }
                    
					newPage.getPageHeader().setNextTID(rec.getPage().ph.getCurrentTID());
					newPage.getPageHeader().setPrevDataPage(rec.getPage().getPageNum());
					newPage.getPageHeader().setNextDataPage(rec.getPage().getPageHeader().getNextDataPage());
					LOG.debug("creating new page after split: "	+ newPage.getPageNum());
					rec.getPage().getPageHeader().setNextDataPage(newPage.getPageNum());
					rec.getPage().getPageHeader().setDataLength(rec.getPage().len);
					rec.getPage().getPageHeader().setRecordCount(countRecordsInPage(rec.getPage()));
					rec.getPage().setDirty(true);
					dataCache.add(rec.getPage());
					dataCache.add(newPage);
					rec.setPage(newPage);
					rec.getPage().len = 0;
				}
                
                long forwardLink = StorageAddress.createPointer(
                        (int) nextSplitPage.getPageNum(), ItemId.getId(tid));
                
                if (isTransactional && transaction != null) {
                    Loggable loggable = new AddLinkLoggable(transaction, rec.getPage().getPageNum(), tid, 
                            forwardLink);
                    writeToLog(loggable, rec.getPage().page);
                }
                
				ByteConversion.shortToByte(ItemId.setIsLink(tid), rec.getPage().data, rec.getPage().len);
				rec.getPage().len += LENGTH_TID;
				ByteConversion.longToByte(forwardLink, rec.getPage().data, rec.getPage().len);
				rec.getPage().len += LENGTH_FORWARD_LOCATION;
			}
		} // end of for loop: finished copying data

		// link the split pages to the original page

		if (nextSplitPage.len == 0) {
			LOG.warn("page " + nextSplitPage.getPageNum()
					+ " is empty. Remove it");
			// if nothing has been copied to the last split page,
			// remove it
			dataCache.remove(nextSplitPage);
			if (nextSplitPage == firstSplitPage)
				firstSplitPage = null;
			try {
				unlinkPages(nextSplitPage.page);
			} catch (IOException e) {
				LOG.warn("Failed to remove empty split page: " + e.getMessage(), e);
			}
			nextSplitPage = null;
		} else {
            if (isTransactional && transaction != null) {
                Loggable loggable = new UpdateHeaderLoggable(transaction, nextSplitPage.ph.getPrevDataPage(),
                        nextSplitPage.getPageNum(), rec.getPage().ph.getNextDataPage(),
                        nextSplitPage.ph.getPrevDataPage(), nextSplitPage.ph.getNextDataPage());
                writeToLog(loggable, nextSplitPage.page);
            }
            
			nextSplitPage.getPageHeader().setDataLength(nextSplitPage.len);
			nextSplitPage.getPageHeader().setNextDataPage(
					rec.getPage().getPageHeader().getNextDataPage());
			nextSplitPage.getPageHeader().setRecordCount(splitRecordCount);
			nextSplitPage.setDirty(true);
			dataCache.add(nextSplitPage);
            
			if (isTransactional && transaction != null) {
                Loggable loggable = new UpdateHeaderLoggable(transaction, rec.getPage().getPageNum(),
                        firstSplitPage.getPageNum(), firstSplitPage.ph.getNextDataPage(), firstSplitPage.ph.getPrevDataPage(), 
                        firstSplitPage.ph.getNextDataPage());
                writeToLog(loggable, nextSplitPage.page);
            }

			firstSplitPage.getPageHeader().setPrevDataPage(rec.getPage().getPageNum());
			if (nextSplitPage != firstSplitPage) {
				firstSplitPage.setDirty(true);
				dataCache.add(firstSplitPage);
			}
		}
		long nextPageNr = rec.getPage().getPageHeader().getNextDataPage();
		if (Page.NO_PAGE != nextPageNr) {
			DOMPage nextPage = getCurrentPage(nextPageNr);
            if (isTransactional && transaction != null) {
                Loggable loggable = new UpdateHeaderLoggable(transaction, nextSplitPage.getPageNum(),
                        nextPage.getPageNum(), Page.NO_PAGE, nextPage.ph.getPrevDataPage(), nextPage.ph.getNextDataPage());
                writeToLog(loggable, nextPage.page);
            }
			nextPage.getPageHeader().setPrevDataPage(nextSplitPage.getPageNum());
			nextPage.setDirty(true);
			dataCache.add(nextPage);
		}
		rec.setPage(getCurrentPage(rec.getPage().getPageNum()));
		if (firstSplitPage != null) {
            if (isTransactional && transaction != null) {
                Loggable loggable = new UpdateHeaderLoggable(transaction, rec.getPage().ph.getPrevDataPage(),
                        rec.getPage().getPageNum(), firstSplitPage.getPageNum(),
                        rec.getPage().ph.getPrevDataPage(), rec.getPage().ph.getNextDataPage());
                writeToLog(loggable, rec.getPage().page);
            }
			rec.getPage().getPageHeader().setNextDataPage(firstSplitPage.getPageNum());
		}
		rec.getPage().getPageHeader().setDataLength(rec.getPage().len);
		rec.getPage().getPageHeader().setRecordCount(countRecordsInPage(rec.getPage()));
		rec.offset = rec.getPage().len;
		return rec;
	}

	/**
	 * Returns the number of records stored in a page.
	 * 
	 * @param page
	 * @return The number of records
	 */
	private short countRecordsInPage(DOMPage page) {
		short count = 0;
		final int dlen = page.getPageHeader().getDataLength();
		for (int pos = 0; pos < dlen; count++) {
			short tid = ByteConversion.byteToShort(page.data, pos);
			pos += LENGTH_TID;
			if (ItemId.isLink(tid)) {
				pos += LENGTH_FORWARD_LOCATION;
			} else {
				short vlen = ByteConversion.byteToShort(page.data, pos);
				pos += LENGTH_DATA_LENGTH;
				if (ItemId.isRelocated(tid)) {
					pos += vlen == OVERFLOW ? LENGTH_ORIGINAL_LOCATION + LENGTH_OVERFLOW_LOCATION : LENGTH_ORIGINAL_LOCATION + vlen;
				} else
					pos += vlen == OVERFLOW ? LENGTH_OVERFLOW_LOCATION : vlen;
			}
		}
		// LOG.debug("page " + page.getPageNum() + " has " + count + "
		// records.");
		return count;
	}

	public String debugPageContents(DOMPage page) {
		StringBuffer buf = new StringBuffer();
		buf.append("Page " + page.getPageNum() + ": ");
		short count = 0;
		short vlen;
		int dlen = page.getPageHeader().getDataLength();
		for (int pos = 0; pos < dlen; count++) {
			short tid = ByteConversion.byteToShort(page.data, pos);
			pos += LENGTH_TID;
            if (ItemId.isLink(tid))
                buf.append('L');
            else if (ItemId.isRelocated(tid))
                buf.append('R');
			buf.append(ItemId.getId(tid) + "[" + pos);
			if (ItemId.isLink(tid)) {
				buf.append(':').append(LENGTH_FORWARD_LOCATION).append("] ");
				pos += LENGTH_FORWARD_LOCATION;
			} else {
				vlen = ByteConversion.byteToShort(page.data, pos);
                pos += LENGTH_DATA_LENGTH;
				if (vlen < 0) {
					LOG.warn("Illegal length: " + vlen);
					return buf.toString();
				}
				buf.append(':').append(vlen).append("]");
                if (ItemId.isRelocated(tid))
                    pos += LENGTH_ORIGINAL_LOCATION;
                buf.append(':').append(Signatures.getType(page.data[pos])).append(' ');
                pos += vlen;
			}
		}
		buf.append("; records in page: " + count);
		buf.append("; nextTID: " + page.getPageHeader().getCurrentTID());
        buf.append("; length: " + page.getPageHeader().getDataLength());
		return buf.toString();
	}

	public boolean close() throws DBException {
        if (!isReadOnly())
            flush();
		super.close();
		return true;
	}

    public void closeAndRemove() {
    	super.closeAndRemove();
    	cacheManager.deregisterCache(dataCache);
    }

    public boolean create() throws DBException {
		if (super.create((short) -1))
			return true;
		else
			return false;
	}

	public FileHeader createFileHeader() {
		return new BTreeFileHeader(1024, PAGE_SIZE);
	}

	protected void unlinkPages(Page page) throws IOException {
		super.unlinkPages(page);
	}

	public PageHeader createPageHeader() {
		return new DOMFilePageHeader();
	}

	public ArrayList findKeys(IndexQuery query) throws IOException,
			BTreeException {
		final FindCallback cb = new FindCallback(FindCallback.KEYS);
		try {
			query(query, cb);
		} catch (TerminatedException e) {
			// Should never happen here
			LOG.warn("Method terminated");
		}
		return cb.getValues();
	}
	
	private final static class ChildNode {
		StoredNode node;
		int index = 0;
		
		public ChildNode(StoredNode node) {
			this.node = node;
		}
	}
	
	/* TODO: Non-recursive implementation. Should be faster, but no measurable 
	 * difference observed so far. Keep as reference for future uses. */
	private long findNode2(StoredNode node, NodeId target, Iterator iter) {
		Stack stack = new Stack();
		stack.push(new ChildNode(node));
		while(!stack.isEmpty()) {
			StoredNode temp = ((ChildNode) stack.peek()).node;
			int index = ((ChildNode) stack.peek()).index;
			if (index < temp.getChildCount()) {
				((ChildNode) stack.peek()).index++;
				temp = (StoredNode) iter.next();
				if (target.equals(temp.getNodeId()))
					return ((NodeIterator) iter).currentAddress();
				if (temp.hasChildNodes()) {
					stack.push(new ChildNode(temp));
					index = 0;
				}
			}
			while (index == temp.getChildCount()) {
				stack.pop();
				if (!stack.isEmpty()) {
					index = ((ChildNode) stack.peek()).index;
					temp = ((ChildNode) stack.peek()).node;
				} else
					break;
			}
		}
		return KEY_NOT_FOUND;
	}
	
	private long findNode(StoredNode node, NodeId target, Iterator iter) {
		if (node.hasChildNodes()) {			
			for (int i = 0; i < node.getChildCount(); i++) {
				StoredNode child = (StoredNode) iter.next();

				SanityCheck.ASSERT(child != null, "Next node missing.");

				if (target.equals(child.getNodeId())) {
					return ((NodeIterator) iter).currentAddress();
				}
				long p;
				if ((p = findNode(child, target, iter)) != KEY_NOT_FOUND)
					return p;
			}
		}
		return KEY_NOT_FOUND;
	}

	/**
	 * Find a node by searching for a known ancestor in the index. If an
	 * ancestor is found, it is traversed to locate the specified descendant
	 * node.
	 * 
	 * @param lock
	 * @param node
	 * @return The node's adress or <code>KEY_NOT_FOUND</code> if the node can not be found.
	 * @throws IOException
	 * @throws BTreeException
	 */
	protected long findValue(Object lock, NodeProxy node) throws IOException,
			BTreeException {
		final DocumentImpl doc = (DocumentImpl) node.getDocument();
		final NativeBroker.NodeRef nodeRef = new NativeBroker.NodeRef(doc.getDocId(), node.getNodeId());
		// first try to find the node in the index
		final long p = findValue(nodeRef);
		if (p == KEY_NOT_FOUND) {
			// node not found in index: try to find the nearest available
			// ancestor and traverse it
			NodeId id = node.getNodeId();
			long parentPointer = KEY_NOT_FOUND;
			do {
				id = id.getParentId();
				if (id == NodeId.DOCUMENT_NODE) {
					SanityCheck.TRACE("Node " + node.getDocument().getDocId() + ":" + node.getNodeId() + " not found.");
					throw new BTreeException("node " + node.getNodeId() + " not found.");
				}
				NativeBroker.NodeRef parentRef = new NativeBroker.NodeRef(doc.getDocId(), id);
				try {
					parentPointer = findValue(parentRef);
				} catch (BTreeException bte) {
				}
			} while (parentPointer == KEY_NOT_FOUND);

			final Iterator iter = new NodeIterator(lock, this, node.getDocument(), parentPointer);
			final StoredNode n = (StoredNode) iter.next();
			final long address = findNode(n, node.getNodeId(), iter);
			if (address == KEY_NOT_FOUND) {
				// if(LOG.isDebugEnabled())
				// LOG.debug("Node data location not found for node " +
				// node.gid);
				return KEY_NOT_FOUND;
			} else
				return address;
		} else
			return p;
	}

	/**
	 * Find matching nodes for the given query.
	 * 
	 * @param query
	 *                     Description of the Parameter
	 * @return Description of the Return Value
	 * @exception IOException
	 *                           Description of the Exception
	 * @exception BTreeException
	 *                           Description of the Exception
	 */
	public ArrayList findValues(IndexQuery query) throws IOException,
			BTreeException {
		FindCallback cb = new FindCallback(FindCallback.VALUES);
		try {
			query(query, cb);
		} catch (TerminatedException e) {
			// Should never happen
			LOG.warn("Method terminated");
		}
		return cb.getValues();
	}

	/**
	 * Flush all buffers to disk.
	 * 
	 * @return Description of the Return Value
	 * @exception DBException
	 *                           Description of the Exception
	 */
	public boolean flush() throws DBException {
		boolean flushed = false;
		//TODO : record transaction as a valuable flush ?
        if (isTransactional)
            logManager.flushToLog(true);
		if (!BrokerPool.FORCE_CORRUPTION) {
			flushed = flushed | super.flush();
			flushed = flushed | dataCache.flush();
		}
		// closeDocument();
		return flushed;
	}

	public void printStatistics() {
		super.printStatistics();
		NumberFormat nf = NumberFormat.getPercentInstance();
		NumberFormat nf2 = NumberFormat.getInstance();
		StringBuffer buf = new StringBuffer();
		buf.append(getFile().getName()).append(" DATA ");
        buf.append("Buffers occupation : ");
        if (dataCache.getBuffers() == 0 && dataCache.getUsedBuffers() == 0)
        	buf.append("N/A");
        else
        	buf.append(nf.format(dataCache.getUsedBuffers()/(float)dataCache.getBuffers()));
        buf.append(" (" + nf2.format(dataCache.getUsedBuffers()) + " out of " + nf2.format(dataCache.getBuffers()) + ")");		
		//buf.append(dataCache.getBuffers()).append(" / ");
		//buf.append(dataCache.getUsedBuffers()).append(" / ");
        buf.append(" Cache efficiency : ");
        if (dataCache.getHits() == 0 && dataCache.getFails() == 0)
        	buf.append("N/A");
        else
        	buf.append(nf.format(dataCache.getHits()/(float)(dataCache.getFails() + dataCache.getHits())));        
		//buf.append(dataCache.getHits()).append(" / ");
		//buf.append(dataCache.getFails());
		LOG.info(buf.toString());
	}

	public BufferStats getDataBufferStats() {
		return new BufferStats(dataCache.getBuffers(), dataCache
				.getUsedBuffers(), dataCache.getHits(), dataCache.getFails());
	}

	/**
	 * Retrieve a node by key
	 * 
	 * @param key
	 * @return Description of the Return Value
	 */
	public Value get(Value key) {
		try {
			long p = findValue(key);
			if (p == KEY_NOT_FOUND)
				return null;
			return get(p);
		} catch (BTreeException bte) {
			return null;
			// key not found
		} catch (IOException ioe) {
			LOG.debug(ioe);
			return null;
		}
	}

	/**
	 * Retrieve a node described by the given NodeProxy.
	 * 
	 * @param node
	 *                     Description of the Parameter
	 * @return Description of the Return Value
	 */
	public Value get(NodeProxy node) {
		try {
			long p = findValue(owner, node);
			if (p == KEY_NOT_FOUND)
				return null;
			return get(p);
		} catch (BTreeException bte) {
			return null;
		} catch (IOException ioe) {
			LOG.debug(ioe);
			return null;
		}
	}

	/**
	 * Retrieve node at virtual address p.
	 * 
	 * @param p
	 *                     Description of the Parameter
	 * @return Description of the Return Value
	 */
	public Value get(long p) {
		RecordPos rec = findRecord(p);
		if (rec == null) {
//			SanityCheck.TRACE("object at " + StorageAddress.toString(p)
//					+ " not found.");
			return null;
		}
		short storedLen = ByteConversion.byteToShort(rec.getPage().data, rec.offset);
		rec.offset += LENGTH_DATA_LENGTH;
		if (ItemId.isRelocated(rec.getTID()))
			rec.offset += LENGTH_ORIGINAL_LOCATION;
		Value v;
		if (storedLen == OVERFLOW) {
			long pnum = ByteConversion.byteToLong(rec.getPage().data, rec.offset);
			byte[] data = getOverflowValue(pnum);
			v = new Value(data);
		} else
			v = new Value(rec.getPage().data, rec.offset, storedLen);
		v.setAddress(p);
		return v;
	}

	protected byte[] getOverflowValue(long pnum) {
		try {
			OverflowDOMPage overflow = new OverflowDOMPage(pnum);
			return overflow.read();
		} catch (IOException e) {
			LOG.error("io error while loading overflow value", e);
			return null;
		}
	}
	
	public void removeOverflowValue(Txn transaction, long pnum) {
		try {
			OverflowDOMPage overflow = new OverflowDOMPage(pnum);
			overflow.delete(transaction);
		} catch (IOException e) {
			LOG.error("io error while removing overflow value", e);
		}
	}

	/**
	 * Retrieve the last page in the current sequence.
	 * 
	 * @return The currentPage value
	 */
	private final synchronized DOMPage getCurrentPage(Txn transaction) {
		long pnum = pages.get(owner);
		if (pnum == Page.NO_PAGE) {
			final DOMPage page = new DOMPage();
			pages.put(owner, page.page.getPageNum());
			// LOG.debug("new page created: " + page.getPage().getPageNum() + " by "
			// + owner +
			// "; thread: " + Thread.currentThread().getName());
			dataCache.add(page);
			if (isTransactional && transaction != null) {
				CreatePageLoggable loggable = new CreatePageLoggable(
						transaction, Page.NO_PAGE, page.getPageNum(), Page.NO_PAGE);
				writeToLog(loggable, page.page);
			}
			return page;
		} else
			return getCurrentPage(pnum);
	}

	/**
	 * Retrieve the page with page number p
	 * 
	 * @param p
	 *                     Description of the Parameter
	 * @return The currentPage value
	 */
	protected final DOMPage getCurrentPage(long p) {
		DOMPage page = (DOMPage) dataCache.get(p);
		if (page == null) {
			// LOG.debug("Loading page " + p + " from file");
			page = new DOMPage(p);
		}
		return page;
	}

	public void closeDocument() {
		pages.remove(owner);
		// SanityCheck.TRACE("current doc closed by: " + owner +
		// "; thread: " + Thread.currentThread().getName());
	}

	/**
	 * Open the file.
	 * 
	 * @return Description of the Return Value
	 * @exception DBException
	 *                           Description of the Exception
	 */
	public boolean open() throws DBException {
		return super.open(FILE_FORMAT_VERSION_ID);
	}

	/**
	 * Put a new key/value pair.
	 * 
	 * @param key
	 *                     Description of the Parameter
	 * @param value
	 *                     Description of the Parameter
	 * @return Description of the Return Value
	 */
	public long put(Txn transaction, Value key, byte[] value)
			throws ReadOnlyException {
		long p = add(transaction, value);
		try {
			addValue(transaction, key, p);
		} catch (IOException ioe) {
			LOG.debug(ioe);
			return KEY_NOT_FOUND;
		} catch (BTreeException bte) {
			LOG.debug(bte);
			return KEY_NOT_FOUND;
		}
		return p;
	}

	/**
	 * Physically remove a node. The data of the node will be removed from the
	 * page and the occupied space is freed.
	 */
	public void remove(Value key) {
		remove(null, key);
	}

	public void remove(Txn transaction, Value key) {
		try {
			long p = findValue(key);
			if (p == KEY_NOT_FOUND)
				return;
			remove(transaction, key, p);
		} catch (BTreeException bte) {
			LOG.debug(bte);
		} catch (IOException ioe) {
			LOG.debug(ioe);
		}
	}

	/**
	 * Remove the link at the specified position from the file.
	 * 
	 * @param p
	 */
	private void removeLink(Txn transaction, long p) {
		RecordPos rec = findRecord(p, false);
		DOMFilePageHeader ph = rec.getPage().getPageHeader();
		if (isTransactional && transaction != null) {
            byte[] data = new byte[8];
            System.arraycopy(rec.getPage().data, rec.offset, data, 0, 8);
            RemoveValueLoggable loggable = new RemoveValueLoggable(transaction,
                    rec.getPage().getPageNum(), rec.getTID(), rec.offset - 2, data, false, 0);
            writeToLog(loggable, rec.getPage().page);
        }
		int end = rec.offset + 8;
		System.arraycopy(rec.getPage().data, end, rec.getPage().data,
				rec.offset - 2, rec.getPage().len - end);
		rec.getPage().len = rec.getPage().len - (2 + 8);
		ph.setDataLength(rec.getPage().len);
		rec.getPage().setDirty(true);
		ph.decRecordCount();
		// LOG.debug("size = " + ph.getRecordCount());
		if (rec.getPage().len == 0) {
		    if (isTransactional && transaction != null) {
                RemoveEmptyPageLoggable loggable = new RemoveEmptyPageLoggable(
                        transaction, rec.getPage().getPageNum(), rec.getPage().ph.getPrevDataPage(), rec.getPage().ph.getNextDataPage());
                writeToLog(loggable, rec.getPage().page);
            }
			removePage(rec.getPage());
			rec.setPage(null);
		} else {
			dataCache.add(rec.getPage());
			// printPageContents(rec.page);
		}
	}

	/**
	 * Physically remove a node. The data of the node will be removed from the
	 * page and the occupied space is freed.
	 * 
	 * @param p
	 */
	public void removeNode(long p) {
		removeNode(null, p);
	}

	public void removeNode(Txn transaction, long p) {
		RecordPos rec = findRecord(p);
		final int startOffset = rec.offset - 2;
		final DOMFilePageHeader ph = rec.getPage().getPageHeader();
		short vlen = ByteConversion.byteToShort(rec.getPage().data, rec.offset);
		rec.offset += LENGTH_DATA_LENGTH;
		short l = vlen;		
		if (ItemId.isLink(rec.getTID())) {
			throw new RuntimeException("Cannot remove link ...");
		}
        boolean isOverflow = false;
        long backLink = 0;
		if (ItemId.isRelocated(rec.getTID())) {
			backLink = ByteConversion.byteToLong(rec.getPage().data, rec.offset);
			rec.offset += LENGTH_ORIGINAL_LOCATION;
			l += LENGTH_ORIGINAL_LOCATION;
			removeLink(transaction, backLink);
		}
		if (l == OVERFLOW) {
			// remove overflow value
            isOverflow = true;
			long overflowLink = ByteConversion.byteToLong(rec.getPage().data, rec.offset);
			rec.offset += LENGTH_OVERFLOW_LOCATION;
			try {
				OverflowDOMPage overflow = new OverflowDOMPage(overflowLink);
				overflow.delete(transaction);
			} catch (IOException e) {
				LOG.error("io error while removing overflow page", e);
			}
			l += LENGTH_OVERFLOW_LOCATION;
            vlen = LENGTH_OVERFLOW_LOCATION;
		}
		if (isTransactional && transaction != null) {
			byte[] data = new byte[vlen];
			System.arraycopy(rec.getPage().data, rec.offset, data, 0, vlen);
			RemoveValueLoggable loggable = new RemoveValueLoggable(transaction,
					rec.getPage().getPageNum(), rec.getTID(), startOffset, data, isOverflow, backLink);
			writeToLog(loggable, rec.getPage().page);
		}
		int end = startOffset + 2 + 2 + l;
		int len = ph.getDataLength();
		// remove old value
		System.arraycopy(rec.getPage().data, end, rec.getPage().data, startOffset, len - end);
		rec.getPage().setDirty(true);
		rec.getPage().len = len - (2 + 2 + l);
		rec.getPage().setDirty(true);
		ph.setDataLength(rec.getPage().len);
		ph.decRecordCount();
		if (rec.getPage().len == 0) {
			LOG.debug("removing page " + rec.getPage().getPageNum());
			if (isTransactional && transaction != null) {
				RemoveEmptyPageLoggable loggable = new RemoveEmptyPageLoggable(
						transaction, rec.getPage().getPageNum(), rec.getPage().ph
								.getPrevDataPage(), rec.getPage().ph
								.getNextDataPage());
				writeToLog(loggable, rec.getPage().page);
			}
			removePage(rec.getPage());
			rec.setPage(null);
		} else {
			rec.getPage().cleanUp();
			dataCache.add(rec.getPage());
		}
	}

	/**
	 * Physically remove a node. The data of the node will be removed from the
	 * page and the occupied space is freed.
	 */
	public void remove(Value key, long p) {
		remove(null, key, p);
	}

	public void remove(Txn transaction, Value key, long p) {
		removeNode(transaction, p);
		try {
			removeValue(transaction, key);
		} catch (BTreeException e) {
			LOG.warn("btree error while removing node", e);
		} catch (IOException e) {
			LOG.warn("io error while removing node", e);
		}
	}

	/**
	 * Remove the specified page. The page is added to the list of free pages.
	 * 
	 * @param page
	 */
	public void removePage(DOMPage page) {
		dataCache.remove(page);
		DOMFilePageHeader ph = page.getPageHeader();
		if (ph.getNextDataPage() != Page.NO_PAGE) {
			DOMPage next = getCurrentPage(ph.getNextDataPage());
			next.getPageHeader().setPrevDataPage(ph.getPrevDataPage());
//			 LOG.debug(next.getPageNum() + ".prev = " + ph.getPrevDataPage());
			next.setDirty(true);
			dataCache.add(next);
            
		}

		if (ph.getPrevDataPage() != Page.NO_PAGE) {
			DOMPage prev = getCurrentPage(ph.getPrevDataPage());
			prev.getPageHeader().setNextDataPage(ph.getNextDataPage());
//			 LOG.debug(prev.getPageNum() + ".next = " + ph.getNextDataPage());
			prev.setDirty(true);
			dataCache.add(prev);
		}

		try {
			ph.setNextDataPage(Page.NO_PAGE);
			ph.setPrevDataPage(Page.NO_PAGE);
			ph.setDataLength(0);
			ph.setNextTID(ItemId.UNKNOWN_ID);
			ph.setRecordCount((short) 0);
			unlinkPages(page.page);
		} catch (IOException ioe) {
			LOG.warn(ioe);
		}
		if (currentDocument != null)
			currentDocument.getMetadata().decPageCount();
	}

	/**
	 * Remove a sequence of pages, starting with the page denoted by the passed
	 * address pointer p.
	 * 
	 * @param transaction
	 * @param p
	 */
	public void removeAll(Txn transaction, long p) {
//		 StringBuffer debug = new StringBuffer();
//		 debug.append("Removed pages: ");
		long pnum = StorageAddress.pageFromPointer(p);
		while (Page.NO_PAGE != pnum) {
			DOMPage page = getCurrentPage(pnum);

			if (isTransactional && transaction != null) {
				RemovePageLoggable loggable = new RemovePageLoggable(
						transaction, pnum, 
						page.ph.getPrevDataPage(), page.ph.getNextDataPage(), 
						page.data, page.len,
						page.ph.getCurrentTID(), page.ph.getRecordCount());
				writeToLog(loggable, page.page);
			}

			pnum = page.getPageHeader().getNextDataPage();
			dataCache.remove(page);
			try {
				DOMFilePageHeader ph = page.getPageHeader();
				ph.setNextDataPage(Page.NO_PAGE);
				ph.setPrevDataPage(Page.NO_PAGE);
				ph.setDataLength(0);
				ph.setNextTID(ItemId.UNKNOWN_ID);
				ph.setRecordCount((short) 0);
				page.len = 0;
				unlinkPages(page.page);
			} catch (IOException e) {
				LOG.warn("Error while removing page: " + e.getMessage(), e);
			}
		}
//		 LOG.debug(debug.toString());
	}

	public String debugPages(DocumentImpl doc, boolean showPageContents) {
		StringBuffer buf = new StringBuffer();
		buf.append("Pages used by ").append(doc.getURI());
		buf.append("; docId ").append(doc.getDocId()).append(':');
		long pnum = StorageAddress.pageFromPointer(((StoredNode) doc
				.getFirstChild()).getInternalAddress());
		while (Page.NO_PAGE != pnum) {
			DOMPage page = getCurrentPage(pnum);
			dataCache.add(page);
			buf.append(' ').append(pnum);
			pnum = page.getPageHeader().getNextDataPage();
            if (showPageContents)
                LOG.debug(debugPageContents(page));
		}
        //Commented out since DocmentImpl has no more internal address
		//buf.append("; Document metadata at "
		//		+ StorageAddress.toString(doc.getInternalAddress()));
		return buf.toString();
	}

	/**
	 * Set the last page in the sequence to which nodes are currently appended.
	 * 
	 * @param page
	 *                     The new currentPage value
	 */
	private final void setCurrentPage(DOMPage page) {
		long pnum = pages.get(owner);
		if (pnum == page.page.getPageNum())
			return;
		// pages.remove(owner);
		// LOG.debug("current page set: " + page.getPage().getPageNum() + " by " +
		// owner.hashCode() +
		// "; thread: " + Thread.currentThread().getName());
		pages.put(owner, page.page.getPageNum());
	}

	/**
	 * Get the active Lock object for this file.
	 * 
	 * @see org.exist.util.Lockable#getLock()
	 */
	public final Lock getLock() {
		return lock;
	}

	/**
	 * The current object owning this file.
	 * 
	 * @param obj
	 *                     The new ownerObject value
	 */
	public final void setOwnerObject(Object obj) {
		// if(owner != obj && obj != null)
		// LOG.debug("owner set -> " + obj.hashCode());
		owner = obj;
	}

	/**
	 * Update the key/value pair.
	 * 
	 * @param key
	 *                     Description of the Parameter
	 * @param value
	 *                     Description of the Parameter
	 * @return Description of the Return Value
	 */
	public boolean update(Txn transaction, Value key, byte[] value)
			throws ReadOnlyException {
		try {
			long p = findValue(key);
			if (p == KEY_NOT_FOUND)
				return false;
			update(transaction, p, value);
		} catch (BTreeException bte) {
			LOG.debug(bte);
			bte.printStackTrace();
			return false;
		} catch (IOException ioe) {
			LOG.debug(ioe);
			return false;
		}
		return true;
	}

    /**
     * Update the key/value pair where the value is found at address p.
     *
     * @param transaction 
     * @param p 
     * @param value 
     * @throws org.exist.util.ReadOnlyException 
     */
	public void update(Txn transaction, long p, byte[] value)
			throws ReadOnlyException {
		RecordPos rec = findRecord(p);
        
		short vlen = ByteConversion.byteToShort(rec.getPage().data, rec.offset);
		rec.offset += LENGTH_DATA_LENGTH;
		if (ItemId.isRelocated(rec.getTID()))
			rec.offset += LENGTH_ORIGINAL_LOCATION;
		if (value.length < vlen) {
			// value is smaller than before
			throw new IllegalStateException("shrinked");
		} else if (value.length > vlen) {
			throw new IllegalStateException("value too long: expected: "
					+ value.length + "; got: " + vlen);
		} else {
            if (isTransactional && transaction != null) {
                Loggable loggable = new UpdateValueLoggable(transaction, rec.getPage().getPageNum(), 
                		rec.getTID(), value, rec.getPage().data, rec.offset);
                writeToLog(loggable, rec.getPage().page);
            }
            
			// value length unchanged
			System.arraycopy(value, 0, rec.getPage().data, rec.offset, value.length);
		}
		rec.getPage().setDirty(true);
	}

	/**
	 * Retrieve the string value of the specified node. This is an optimized low-level method
	 * which will directly traverse the stored DOM nodes and collect the string values of
	 * the specified root node and all its descendants. By directly scanning the stored
	 * node data, we do not need to create a potentially large amount of node objects
	 * and thus save memory and time for garbage collection. 
	 * 
	 * @param node
	 * @return string value of the specified node
	 */
	public String getNodeValue(StoredNode node, boolean addWhitespace) {
		try {
			long address = node.getInternalAddress();
            RecordPos rec = null;
            // try to directly locate the root node through its storage address
            if (address != StoredNode.UNKNOWN_NODE_IMPL_ADDRESS)
                rec = findRecord(address);
            if (rec == null) {
				// fallback to a btree lookup if the node could not be found
				// by its storage address
				address = findValue(this, new NodeProxy(node));
    			if (address == BTree.KEY_NOT_FOUND)
    				return null;
                rec = findRecord(address);
    			SanityCheck.THROW_ASSERT(rec != null, "Node data could not be found!");
            }
			// we collect the string values in binary format and append to a ByteArrayOutputStream
			final ByteArrayOutputStream os = new ByteArrayOutputStream();
			// now traverse the tree
			getNodeValue((DocumentImpl)node.getOwnerDocument(), os, rec, true, addWhitespace);
			final byte[] data = os.toByteArray();
			String value;
			try {
				value = new String(data, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				LOG.warn("UTF-8 error while reading node value", e);
				//TODO : why not store another string like "OOOPS !" ?
				//then return null
				value = new String(data);
			}
			return value;
		} catch (BTreeException e) {
			LOG.warn("btree error while reading node value", e);
		} catch (IOException e) {
			LOG.warn("io error while reading node value", e);
		}
		return null;
	}

	/**
	 * Recursive method to retrieve the string values of the root node
	 * and all its descendants.
	 */
	private void getNodeValue(DocumentImpl doc, ByteArrayOutputStream os, RecordPos rec,
			boolean firstCall, boolean addWhitespace) {
		// locate the next real node, skipping relocated nodes
		boolean foundNext = false;
		do {
			if (rec.offset > rec.getPage().getPageHeader().getDataLength()) {
				// end of page reached, proceed to the next page
				final long nextPage = rec.getPage().getPageHeader().getNextDataPage();
				if (nextPage == Page.NO_PAGE) {
					SanityCheck.TRACE("bad link to next page! offset: " + rec.offset + "; len: " + 
							rec.getPage().getPageHeader().getDataLength() +
							": " + rec.getPage().page.getPageInfo());
					return;
				}
				rec.setPage(getCurrentPage(nextPage));
				dataCache.add(rec.getPage());
				rec.offset = 2;
			}
			//TODO : understand this ! -pb
			short tid = ByteConversion.byteToShort(rec.getPage().data, rec.offset - 2); 
			rec.setTID(tid);
			if (ItemId.isLink(rec.getTID())) {
				// this is a link: skip it
				rec.offset += (LENGTH_TID + LENGTH_FORWARD_LOCATION);
			} else
				// ok: node found
				foundNext = true;
		} while (!foundNext);
		// read the page len
		int len = ByteConversion.byteToShort(rec.getPage().data, rec.offset);
        rec.offset += LENGTH_DATA_LENGTH;
		// check if the node was relocated
		if (ItemId.isRelocated(rec.getTID()))
			rec.offset += LENGTH_ORIGINAL_LOCATION;
		byte[] data = rec.getPage().data;
		int readOffset = rec.offset;
		boolean inOverflow = false;
		if (len == OVERFLOW) {
			// if we have an overflow value, load it from the overflow page
			final long op = ByteConversion.byteToLong(data, rec.offset);
			data = getOverflowValue(op);
			rec.offset += 2 + 8;
			len = data.length;
			readOffset = 0;
			inOverflow = true;
		}

		// check the type of the node
		final short type = Signatures.getType(data[readOffset++]);
		// switch on the node type
		switch (type) {
		case Node.ELEMENT_NODE:
			final int children = ByteConversion.byteToInt(data, readOffset);
            readOffset += 4;
            int dlnLen = ByteConversion.byteToShort(data, readOffset);
            readOffset += 2;
            readOffset +=
                doc.getBroker().getBrokerPool().getNodeFactory().lengthInBytes(dlnLen, data, readOffset);
            final short attributes = ByteConversion.byteToShort(data, readOffset);
			final boolean extraWhitespace = addWhitespace
					&& children - attributes > 1;
			rec.offset += len + 2;
			for (int i = 0; i < children; i++) {
				//recursive call
				getNodeValue(doc, os, rec, false, addWhitespace);
				if (extraWhitespace)
					os.write((byte) ' ');
			}
			return;
		case Node.TEXT_NODE:
        case Node.CDATA_SECTION_NODE:
            dlnLen = ByteConversion.byteToShort(data, readOffset);
            readOffset += 2;
            int nodeIdLen =
                doc.getBroker().getBrokerPool().getNodeFactory().lengthInBytes(dlnLen, data, readOffset);
            readOffset += nodeIdLen;
            os.write(data, readOffset, len - nodeIdLen - (2 + 1));
			break;
		case Node.ATTRIBUTE_NODE:
			// use attribute value if the context node is an attribute, i.e.
			// if this is the first call to the method
			if (firstCall) {
                int start = readOffset - 1;
                final byte idSizeType = (byte) (data[start] & 0x3);
				final boolean hasNamespace = (data[start] & 0x10) == 0x10;
                dlnLen = ByteConversion.byteToShort(data, readOffset);
                readOffset += 2;
                nodeIdLen  =
                    doc.getBroker().getBrokerPool().getNodeFactory().lengthInBytes(dlnLen, data, readOffset);
                readOffset += nodeIdLen + Signatures.getLength(idSizeType);

                if (hasNamespace) {
					readOffset += 2; // skip namespace id
					final short prefixLen = ByteConversion.byteToShort(data, readOffset);
					readOffset += prefixLen + 2; // skip prefix
				}
                os.write(rec.getPage().data, readOffset, len - (readOffset - start));
			}
			break;
		}
		if (!inOverflow)
			// if it isn't an overflow value, add the value length to the current offset
			rec.offset += len + 2;
	}

	protected RecordPos findRecord(long p) {
		return findRecord(p, true);
	}

	/**
	 * Find a record within the page or the pages linked to it.
	 * 
	 * @param p
	 * @return The record position in the page
	 */
	protected RecordPos findRecord(long p, boolean skipLinks) {
		long pageNr = StorageAddress.pageFromPointer(p);
		short tid = StorageAddress.tidFromPointer(p);
		while (pageNr != Page.NO_PAGE) {
			DOMPage page = getCurrentPage(pageNr);
			dataCache.add(page);
			RecordPos rec = page.findRecord(tid);
			if (rec == null) {
				pageNr = page.getPageHeader().getNextDataPage();
				if (pageNr == page.getPageNum()) {
					SanityCheck.TRACE("circular link to next page on " + pageNr);
					return null;
				}
			} else if (rec.isLink()) {
				if (!skipLinks)
					return rec;
				long forwardLink = ByteConversion.byteToLong(page.data, rec.offset);
//				 LOG.debug("following link on " + pageNr +
//				 " to page "
//				 + StorageAddress.pageFromPointer(forwardLink)
//				 + "; tid="
//				 + StorageAddress.tidFromPointer(forwardLink));
				// load the link page
				pageNr = StorageAddress.pageFromPointer(forwardLink);
				tid = StorageAddress.tidFromPointer(forwardLink);
			} else {
				return rec;
			}
		}
		return null;
	}

	/*
	 * ---------------------------------------------------------------------------------
	 * Methods used by recovery and transaction management
	 * ---------------------------------------------------------------------------------
	 */

	private boolean requiresRedo(Loggable loggable, DOMPage page) {
		return loggable.getLsn() > page.getPageHeader().getLsn();
	}

	protected void redoCreatePage(CreatePageLoggable loggable) {
		DOMPage newPage = getCurrentPage(loggable.newPage);
		DOMFilePageHeader ph = newPage.getPageHeader();
		if (ph.getLsn() == Lsn.LSN_INVALID || requiresRedo(loggable, newPage)) {
			try {
				reuseDeleted(newPage.page);
				ph.setStatus(RECORD);
				ph.setDataLength(0);
				ph.setNextTID(ItemId.UNKNOWN_ID);
				ph.setRecordCount((short) 0);
				newPage.len = 0;
				newPage.data = new byte[fileHeader.getWorkSize()];
                ph.setPrevDataPage(Page.NO_PAGE);
                if (loggable.nextTID != ItemId.UNKNOWN_ID)
                    ph.setNextTID(loggable.nextTID);
                ph.setLsn(loggable.getLsn());
				newPage.setDirty(true);
                
                if (loggable.nextPage == Page.NO_PAGE)
                	ph.setNextDataPage(Page.NO_PAGE);
                else
                	ph.setNextDataPage(loggable.nextPage);
                if (loggable.prevPage == Page.NO_PAGE)
                	ph.setPrevDataPage(Page.NO_PAGE);
                else
                	ph.setPrevDataPage(loggable.prevPage);
			} catch (IOException e) {
				LOG.warn("Failed to redo " + loggable.dump() + ": "
						+ e.getMessage(), e);
			}
		}
        dataCache.add(newPage);
	}

	protected void undoCreatePage(CreatePageLoggable loggable) {
		DOMPage page = getCurrentPage(loggable.newPage);
		DOMFilePageHeader ph = page.getPageHeader();
		dataCache.remove(page);
		try {
			ph.setNextDataPage(Page.NO_PAGE);
			ph.setPrevDataPage(Page.NO_PAGE);
			ph.setDataLength(0);
			ph.setNextTID(ItemId.UNKNOWN_ID);
			ph.setRecordCount((short) 0);
			page.len = 0;
			unlinkPages(page.page);
		} catch (IOException e) {
			LOG.warn("Error while removing page: " + e.getMessage(), e);
		}
	}

	protected void redoAddValue(AddValueLoggable loggable) {
		DOMPage page = getCurrentPage(loggable.pageNum);
		DOMFilePageHeader ph = page.getPageHeader();
		if (ph.getLsn() != Lsn.LSN_INVALID && requiresRedo(loggable, page)) {
			try {
				ByteConversion.shortToByte(loggable.tid, page.data, page.len);
				page.len += LENGTH_TID;
				// save data length
				// overflow pages have length 0
				short vlen = (short) loggable.value.length;
				ByteConversion.shortToByte(vlen, page.data, page.len);
				page.len += LENGTH_DATA_LENGTH;
				// save data
				System.arraycopy(loggable.value, 0, page.data, page.len, vlen);
				page.len += vlen;
				ph.incRecordCount();
				ph.setDataLength(page.len);
				page.setDirty(true);
				ph.setNextTID(loggable.tid);
                ph.setLsn(loggable.getLsn());
				dataCache.add(page, 2);
			} catch (ArrayIndexOutOfBoundsException e) {
				LOG.warn("page: " + page.getPageNum() + "; len = " + page.len
						+ "; value = " + loggable.value.length);
				throw e;
			}
		}
	}

	protected void undoAddValue(AddValueLoggable loggable) {
		DOMPage page = getCurrentPage(loggable.pageNum);
		DOMFilePageHeader ph = page.getPageHeader();
		RecordPos pos = page.findRecord(ItemId.getId(loggable.tid));
		SanityCheck.ASSERT(pos != null, "Record not found!");
		int startOffset = pos.offset - 2;
		// get the record length
		short l = ByteConversion.byteToShort(page.data, pos.offset);
		// end offset
		int end = startOffset + LENGTH_TID + LENGTH_DATA_LENGTH + l;
		int len = ph.getDataLength();
		// remove old value
		System.arraycopy(page.data, end, page.data, startOffset, len - end);
		page.setDirty(true);
		page.len = len - (LENGTH_TID + LENGTH_DATA_LENGTH + l);
		ph.setDataLength(page.len);
		page.setDirty(true);
		ph.decRecordCount();
	}

	protected void redoUpdateValue(UpdateValueLoggable loggable) {
		DOMPage page = getCurrentPage(loggable.pageNum);
		DOMFilePageHeader ph = page.getPageHeader();
		if (ph.getLsn() != Lsn.LSN_INVALID && requiresRedo(loggable, page)) {
			RecordPos rec = page.findRecord(ItemId.getId(loggable.tid));
            SanityCheck.THROW_ASSERT(rec != null, "tid " + ItemId.getId(loggable.tid) + " not found on page " + page.getPageNum() +
                    "; contents: " + debugPageContents(page));
			ByteConversion.byteToShort(rec.getPage().data, rec.offset);
			rec.offset += LENGTH_DATA_LENGTH;
			if (ItemId.isRelocated(rec.getTID()))
				rec.offset += LENGTH_ORIGINAL_LOCATION;
			System.arraycopy(loggable.value, 0, rec.getPage().data, rec.offset,
					loggable.value.length);
            rec.getPage().ph.setLsn(loggable.getLsn());
			rec.getPage().setDirty(true);
            dataCache.add(rec.getPage());
		}
	}

	protected void undoUpdateValue(UpdateValueLoggable loggable) {
        DOMPage page = getCurrentPage(loggable.pageNum);
        RecordPos rec = page.findRecord(ItemId.getId(loggable.tid));
        SanityCheck.THROW_ASSERT(rec != null, "tid " + ItemId.getId(loggable.tid) + " not found on page " + page.getPageNum() +
                "; contents: " + debugPageContents(page));
        short len = ByteConversion.byteToShort(rec.getPage().data, rec.offset);
        SanityCheck.THROW_ASSERT(len == loggable.oldValue.length);
        rec.offset += LENGTH_DATA_LENGTH;
        if (ItemId.isRelocated(rec.getTID()))
            rec.offset += LENGTH_ORIGINAL_LOCATION;
        System.arraycopy(loggable.oldValue, 0, page.data, rec.offset, loggable.oldValue.length);
        page.ph.setLsn(loggable.getLsn());
        page.setDirty(true);
        dataCache.add(page);
	}

	protected void redoRemoveValue(RemoveValueLoggable loggable) {
		DOMPage page = getCurrentPage(loggable.pageNum);
//        LOG.debug(debugPageContents(page));
		DOMFilePageHeader ph = page.getPageHeader();
		if (ph.getLsn() != Lsn.LSN_INVALID && requiresRedo(loggable, page)) {
			RecordPos pos = page.findRecord(ItemId.getId(loggable.tid));
			SanityCheck.ASSERT(pos != null, "Record not found: " + ItemId.getId(loggable.tid) + ": "
					+ page.page.getPageInfo() + "\n" + debugPageContents(page));
            int startOffset = pos.offset - 2;
            if (ItemId.isLink(loggable.tid)) {
                int end = pos.offset + LENGTH_FORWARD_LOCATION;
                System.arraycopy(page.data, end, page.data, pos.offset - 2, page.len - end);
                page.len = page.len - (LENGTH_DATA_LENGTH + LENGTH_FORWARD_LOCATION);
            } else {
    			// get the record length
                short l = ByteConversion.byteToShort(page.data, pos.offset);
                if (ItemId.isRelocated(loggable.tid)) {
                    pos.offset += LENGTH_ORIGINAL_LOCATION;
                    l += LENGTH_ORIGINAL_LOCATION;
                }
                if (l == OVERFLOW)
                    l += LENGTH_OVERFLOW_LOCATION;
    			// end offset
    			int end = startOffset + LENGTH_TID + LENGTH_DATA_LENGTH + l;
    			int len = ph.getDataLength();
    			// remove old value
    			System.arraycopy(page.data, end, page.data, startOffset, len - end);
    			page.setDirty(true);
    			page.len = len - (LENGTH_TID + LENGTH_DATA_LENGTH + l);
            }
            ph.setDataLength(page.len);
            page.setDirty(true);
			ph.decRecordCount();
            ph.setLsn(loggable.getLsn());
            page.cleanUp();
            dataCache.add(page);
		}
//		LOG.debug(debugPageContents(page));
	}

	protected void undoRemoveValue(RemoveValueLoggable loggable) {
		DOMPage page = getCurrentPage(loggable.pageNum);
//		LOG.debug(debugPageContents(page));
		DOMFilePageHeader ph = page.getPageHeader();
        int offset = loggable.offset;
        short vlen = (short) loggable.oldData.length;
        if (offset < page.ph.getDataLength()) {
            // make room for the removed value
            int required;
            if (ItemId.isLink(loggable.tid))
                required = LENGTH_TID + LENGTH_FORWARD_LOCATION;
            else
                required = LENGTH_TID + LENGTH_DATA_LENGTH + vlen;
            if (ItemId.isRelocated(loggable.tid))
                required += LENGTH_ORIGINAL_LOCATION;
            int end = offset + required;
            try {
            	System.arraycopy(page.data, offset, page.data, end, page.ph.getDataLength() - offset);
            } catch(ArrayIndexOutOfBoundsException e) {
                SanityCheck.TRACE("Error while copying data on page " + page.getPageNum() +
                        "; tid: " + ItemId.getId(loggable.tid) + "; required: " + required +
                        "; offset: " + offset + "; end: " + end + "; len: " + (page.ph.getDataLength() - offset) +
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
		ph.incRecordCount();
		ph.setDataLength(page.len);
		page.setDirty(true);
		page.cleanUp();
		dataCache.add(page, 2);
//        LOG.debug(debugPageContents(page));
	}

	protected void redoRemoveEmptyPage(RemoveEmptyPageLoggable loggable) {
		DOMPage page = getCurrentPage(loggable.pageNum);
		DOMFilePageHeader ph = page.getPageHeader();
		if (ph.getLsn() != Lsn.LSN_INVALID && requiresRedo(loggable, page)) {
			removePage(page);
        }
	}

	protected void undoRemoveEmptyPage(RemoveEmptyPageLoggable loggable) {
		try {
            DOMPage newPage = getCurrentPage(loggable.pageNum);
            reuseDeleted(newPage.page);
            if (loggable.prevPage == Page.NO_PAGE) 
            	newPage.getPageHeader().setPrevDataPage(Page.NO_PAGE);
            else {
            	DOMPage oldPage = getCurrentPage(loggable.prevPage);
            	oldPage.getPageHeader().setNextDataPage(newPage.getPageNum());
            	oldPage.setDirty(true);
            	dataCache.add(oldPage);
            	newPage.getPageHeader().setPrevDataPage(oldPage.getPageNum());
            }	
            if (loggable.nextPage == Page.NO_PAGE) 
            	newPage.getPageHeader().setNextDataPage(Page.NO_PAGE);
            else {
            	DOMPage oldPage = getCurrentPage(loggable.nextPage);
            	oldPage.getPageHeader().setPrevDataPage(newPage.getPageNum());
                newPage.getPageHeader().setNextDataPage(loggable.nextPage);
            	oldPage.setDirty(true);
            	dataCache.add(oldPage);
            }	
            newPage.ph.setNextTID(ItemId.UNKNOWN_ID);
            newPage.setDirty(true);
            dataCache.add(newPage);
        } catch (IOException e) {
            LOG.warn("Error during undo: " + e.getMessage(), e);
        }
	}

	protected void redoRemovePage(RemovePageLoggable loggable) {
		DOMPage page = getCurrentPage(loggable.pageNum);
		DOMFilePageHeader ph = page.getPageHeader();
		if (ph.getLsn() != Lsn.LSN_INVALID && requiresRedo(loggable, page)) {
			dataCache.remove(page);
			try {
				ph.setNextDataPage(Page.NO_PAGE);
				ph.setPrevDataPage(Page.NO_PAGE);
                ph.setDataLen(fileHeader.getWorkSize());
				ph.setDataLength(0);
				ph.setNextTID(ItemId.UNKNOWN_ID);
				ph.setRecordCount((short) 0);
				page.len = 0;
				unlinkPages(page.page);
			} catch (IOException e) {
				LOG.warn("Error while removing page: " + e.getMessage(), e);
			}
		}
	}

	protected void undoRemovePage(RemovePageLoggable loggable) {
		try {
			DOMPage page = getCurrentPage(loggable.pageNum);
			DOMFilePageHeader ph = page.getPageHeader();
			reuseDeleted(page.page);
			ph.setStatus(RECORD);
			ph.setNextDataPage(loggable.nextPage);
			ph.setPrevDataPage(loggable.prevPage);
			ph.setNextTID(loggable.oldTid);
			ph.setRecordCount(loggable.oldRecCnt);
			ph.setDataLength(loggable.oldLen);
			System.arraycopy(loggable.oldData, 0, page.data, 0, loggable.oldLen);
			page.len = loggable.oldLen;
			page.setDirty(true);
			dataCache.add(page);
		} catch (IOException e) {
			LOG.warn("Failed to undo " + loggable.dump() + ": "
					+ e.getMessage(), e);
		}
	}

	protected void redoWriteOverflow(WriteOverflowPageLoggable loggable) {
		try {
			Page page = getPage(loggable.pageNum);
            page.read();
			PageHeader ph = page.getPageHeader();
			reuseDeleted(page);
			ph.setStatus(RECORD);

			if (ph.getLsn() != Lsn.LSN_INVALID && requiresRedo(loggable, page)) {
				if (loggable.nextPage == Page.NO_PAGE) {
					ph.setNextPage(Page.NO_PAGE);
				} else {
					ph.setNextPage(loggable.nextPage);					
				}
                ph.setLsn(loggable.getLsn());
				writeValue(page, loggable.value);
			}
		} catch (IOException e) {
			LOG.warn("Failed to redo " + loggable.dump() + ": "
					+ e.getMessage(), e);
		}
	}

	protected void undoWriteOverflow(WriteOverflowPageLoggable loggable) {
		try {
			Page page = getPage(loggable.pageNum);
            page.read();
			unlinkPages(page);
		} catch (IOException e) {
			LOG.warn("Failed to undo " + loggable.dump() + ": "
					+ e.getMessage(), e);
		}
	}

	protected void redoRemoveOverflow(RemoveOverflowLoggable loggable) {
		try {
			Page page = getPage(loggable.pageNum);
            page.read();
			PageHeader ph = page.getPageHeader();
			if (ph.getLsn() != Lsn.LSN_INVALID && requiresRedo(loggable, page)) {
				unlinkPages(page);
			}
		} catch (IOException e) {
			LOG.warn("Failed to undo " + loggable.dump() + ": "
					+ e.getMessage(), e);
		}
	}

	protected void undoRemoveOverflow(RemoveOverflowLoggable loggable) {
		try {
			Page page = getPage(loggable.pageNum);
            page.read();
			PageHeader ph = page.getPageHeader();
			reuseDeleted(page);
			ph.setStatus(RECORD);
			if (loggable.nextPage == Page.NO_PAGE) {
				ph.setNextPage(Page.NO_PAGE);
			} else {
				ph.setNextPage(loggable.nextPage);				
			}
			writeValue(page, loggable.oldData);
		} catch (IOException e) {
			LOG.warn("Failed to redo " + loggable.dump() + ": "	+ e.getMessage(), e);
		}
	}

    protected void redoInsertValue(InsertValueLoggable loggable) {
        DOMPage page = getCurrentPage(loggable.pageNum);
        DOMFilePageHeader ph = page.getPageHeader();
        if (ph.getLsn() != Lsn.LSN_INVALID && requiresRedo(loggable, page)) {
            int dlen = page.getPageHeader().getDataLength();
            int offset = loggable.offset;
            // insert in the middle of the page?
            if (offset < dlen) {
                int end = offset + loggable.value.length + 2 + 2;
                try {
                	System.arraycopy(page.data, offset, page.data, end, dlen - offset);
                } catch(ArrayIndexOutOfBoundsException e) {
                    SanityCheck.TRACE("Error while copying data on page " + page.getPageNum() +
                            "; tid: " + loggable.tid +
                            "; offset: " + offset + "; end: " + end + "; len: " + (dlen - offset));
                }
            }
            // writing tid
            ByteConversion.shortToByte((short) loggable.tid, page.data, offset);
            offset += LENGTH_TID;
            page.len += LENGTH_TID;
            // writing value length
            ByteConversion.shortToByte(loggable.isOverflow() ? OVERFLOW : (short) loggable.value.length,
                    page.data, offset);
            offset += LENGTH_DATA_LENGTH;
            page.len += LENGTH_DATA_LENGTH;
            // writing data
            System.arraycopy(loggable.value, 0, page.data, offset, loggable.value.length);
            offset += loggable.value.length;
            page.len += loggable.value.length;
            page.getPageHeader().incRecordCount();            
            ph.setDataLength(page.len);
            ph.setNextTID(loggable.tid);
            page.setDirty(true);
            dataCache.add(page);
        }
    }
    
    protected void undoInsertValue(InsertValueLoggable loggable) {
        DOMPage page = getCurrentPage(loggable.pageNum);
        DOMFilePageHeader ph = page.getPageHeader();
        if (ItemId.isLink(loggable.tid)) {
            int end = loggable.offset + LENGTH_FORWARD_LOCATION;
            System.arraycopy(page.data, end, page.data, loggable.offset - 2, page.len - end);
            page.len = page.len - (LENGTH_DATA_LENGTH + LENGTH_FORWARD_LOCATION);
        } else {
            // get the record length
            int offset = loggable.offset + 2;
            short l = ByteConversion.byteToShort(page.data, offset);
            if (ItemId.isRelocated(loggable.tid)) {
                offset += LENGTH_ORIGINAL_LOCATION;
                l += LENGTH_ORIGINAL_LOCATION;
            }
            if (l == OVERFLOW)
                l += LENGTH_OVERFLOW_LOCATION;
            // end offset
            int end = loggable.offset + (2 + LENGTH_DATA_LENGTH + l);
            int len = ph.getDataLength();
            // remove value
            try {
                System.arraycopy(page.data, end, page.data, loggable.offset, len - end);
            } catch (ArrayIndexOutOfBoundsException e) {
                SanityCheck.TRACE("Error while copying data on page " + page.getPageNum() +
                        "; tid: " + loggable.tid +
                        "; offset: " + loggable.offset + "; end: " + end + "; len: " + (len - end) +
                        "; dataLength: " + len);
            }
            page.len = len - (LENGTH_TID + LENGTH_DATA_LENGTH + l);
        }
        ph.setDataLength(page.len);
        page.setDirty(true);
        ph.decRecordCount();
        ph.setLsn(loggable.getLsn());
        page.cleanUp();
        dataCache.add(page);
    }
    
    protected void redoSplitPage(SplitPageLoggable loggable) {
        DOMPage page = getCurrentPage(loggable.pageNum);
        DOMFilePageHeader ph = page.getPageHeader();
        if (ph.getLsn() != Lsn.LSN_INVALID && requiresRedo(loggable, page)) {
            byte[] oldData = page.data;
            page.data = new byte[fileHeader.getWorkSize()];
            System.arraycopy(oldData, 0, page.data, 0, loggable.splitOffset);
            page.len = loggable.splitOffset;
            ph.setDataLength(page.len);
            ph.setRecordCount(countRecordsInPage(page));
            page.setDirty(true);
            dataCache.add(page);
        }
    }
    
    protected void undoSplitPage(SplitPageLoggable loggable) {
        DOMPage page = getCurrentPage(loggable.pageNum);
        DOMFilePageHeader ph = page.getPageHeader();
        page.data = loggable.oldData;
        page.len = loggable.oldLen;
        ph.setDataLength(page.len);
        page.setDirty(true);
        ph.setLsn(loggable.getLsn());
        page.cleanUp();
        dataCache.add(page);
    }
    
    protected void redoAddLink(AddLinkLoggable loggable) {
        DOMPage page = getCurrentPage(loggable.pageNum);
        DOMFilePageHeader ph = page.getPageHeader();
        if (ph.getLsn() != Lsn.LSN_INVALID && requiresRedo(loggable, page)) {
            ByteConversion.shortToByte(ItemId.setIsLink(loggable.tid), page.data, page.len);
            page.len += LENGTH_TID;
            ByteConversion.longToByte(loggable.link, page.data, page.len);
            page.len += LENGTH_FORWARD_LOCATION;
            page.setDirty(true);
            ph.setNextTID(ItemId.getId(loggable.tid));
            ph.setDataLength(page.len);
            ph.setLsn(loggable.getLsn());
            ph.incRecordCount();
            dataCache.add(page);
        }
    }
    
    protected void undoAddLink(AddLinkLoggable loggable) {
        DOMPage page = getCurrentPage(loggable.pageNum);
        DOMFilePageHeader ph = page.getPageHeader();
        RecordPos rec = page.findRecord(loggable.tid);
        int end = rec.offset + LENGTH_FORWARD_LOCATION;
        System.arraycopy(page.data, end, page.data, rec.offset - 2, page.len - end);
        page.len = page.len - (LENGTH_TID + LENGTH_FORWARD_LOCATION);
        ph.setDataLength(page.len);
        page.setDirty(true);
        ph.decRecordCount();
        ph.setLsn(loggable.getLsn());
        page.cleanUp();
        dataCache.add(page);
    }
    
    protected void redoUpdateLink(UpdateLinkLoggable loggable) {
        DOMPage page = getCurrentPage(loggable.pageNum);
        DOMFilePageHeader ph = page.getPageHeader();
        if (ph.getLsn() != Lsn.LSN_INVALID && requiresRedo(loggable, page)) {
            ByteConversion.longToByte(loggable.link, page.data, loggable.offset);
            page.setDirty(true);
            ph.setLsn(loggable.getLsn());
            dataCache.add(page);
        }
    }
    
    protected void undoUpdateLink(UpdateLinkLoggable loggable) {
        DOMPage page = getCurrentPage(loggable.pageNum);
        DOMFilePageHeader ph = page.getPageHeader();
        ByteConversion.longToByte(loggable.oldLink, page.data, loggable.offset);
        page.setDirty(true);
        ph.setLsn(loggable.getLsn());
        dataCache.add(page);
    }
    
    protected void redoAddMovedValue(AddMovedValueLoggable loggable) {
        DOMPage page = getCurrentPage(loggable.pageNum);
        DOMFilePageHeader ph = page.getPageHeader();
        if (ph.getLsn() != Lsn.LSN_INVALID && requiresRedo(loggable, page)) {
            try {
                ByteConversion.shortToByte(ItemId.setIsRelocated(loggable.tid), page.data, page.len);
                page.len += LENGTH_TID;
                short vlen = (short) loggable.value.length;
                // save data length
                // overflow pages have length 0
                ByteConversion.shortToByte(vlen, page.data, page.len);
                page.len += LENGTH_DATA_LENGTH;
                ByteConversion.longToByte(loggable.backLink, page.data, page.len);
                page.len += LENGTH_FORWARD_LOCATION;
                // save data
                System.arraycopy(loggable.value, 0, page.data, page.len, vlen);
                page.len += vlen;
                ph.incRecordCount();
                ph.setDataLength(page.len);
                page.setDirty(true);
                ph.setNextTID(ItemId.getId(loggable.tid));
                ph.incRecordCount();
                ph.setLsn(loggable.getLsn());
                dataCache.add(page, 2);
            } catch (ArrayIndexOutOfBoundsException e) {
                LOG.warn("page: " + page.getPageNum() + "; len = " + page.len
                        + "; value = " + loggable.value.length);
                throw e;
            }
        }
    }
    
    protected void undoAddMovedValue(AddMovedValueLoggable loggable) {
        DOMPage page = getCurrentPage(loggable.pageNum);
        DOMFilePageHeader ph = page.getPageHeader();
        RecordPos rec = page.findRecord(ItemId.getId(loggable.tid));
        SanityCheck.ASSERT(rec != null, "Record with tid " + ItemId.getId(loggable.tid) + " not found: "
                + debugPageContents(page));
        // get the record length
        short l = ByteConversion.byteToShort(page.data, rec.offset); 
        // end offset
        int end = rec.offset + 2 + 8 + l;
        int len = ph.getDataLength();
        // remove value
        try {
            System.arraycopy(page.data, end, page.data, rec.offset - 2, len - end);
        } catch (ArrayIndexOutOfBoundsException e) {
            SanityCheck.TRACE("Error while copying data on page " + page.getPageNum() +
                    "; tid: " + loggable.tid +
                    "; offset: " + (rec.offset - 2) + "; end: " + end + "; len: " + (len - end));
        }
        page.setDirty(true);
        page.len = len - (LENGTH_TID + LENGTH_DATA_LENGTH + 8 + l);
        ph.setDataLength(page.len);
        ph.decRecordCount();
        ph.setLsn(loggable.getLsn());
        page.cleanUp();
        dataCache.add(page);
    }
    
    protected void redoUpdateHeader(UpdateHeaderLoggable loggable) {
        DOMPage page = getCurrentPage(loggable.pageNum);
        DOMFilePageHeader ph = page.getPageHeader();
        if (ph.getLsn() != Lsn.LSN_INVALID && requiresRedo(loggable, page)) {
            if (loggable.nextPage != Page.NO_PAGE)
                ph.setNextDataPage(loggable.nextPage);
            if (loggable.prevPage != Page.NO_PAGE)
                ph.setPrevDataPage(loggable.prevPage);
            page.setDirty(true);
            ph.setLsn(loggable.getLsn());
            dataCache.add(page, 2);
        }
    }
    
    protected void undoUpdateHeader(UpdateHeaderLoggable loggable) {
        DOMPage page = getCurrentPage(loggable.pageNum);
        DOMFilePageHeader ph = page.getPageHeader();
        if (loggable.oldPrev != Page.NO_PAGE)
            ph.setPrevDataPage(loggable.oldPrev);
        if (loggable.oldNext != Page.NO_PAGE)
            ph.setNextDataPage(loggable.oldNext);
        page.setDirty(true);
        ph.setLsn(loggable.getLsn());
        dataCache.add(page, 2);
    }
    
	protected void dumpValue(Writer writer, Value key) throws IOException {
		writer.write(Integer.toString(ByteConversion.byteToInt(key.data(), key.start())));
		writer.write(':');
		try {
            int bytes = key.getLength() - 4;
            byte[] data = key.data();
            for (int i = 0; i < bytes; i++) {
                writer.write(DLNBase.toBitString(data[key.start() + 4 + i]));
            }
		} catch (Exception e) {
            e.printStackTrace();
			System.out.println(e.getMessage() + ": doc: " + Integer.toString(ByteConversion.byteToInt(key.data(), key.start())));
		}
	}

	protected final static class DOMFilePageHeader extends BTreePageHeader {

		protected int dataLen = 0;

		protected long nextDataPage = Page.NO_PAGE;

		protected long prevDataPage = Page.NO_PAGE;

		protected short tid = ItemId.UNKNOWN_ID;

		protected short records = 0;

		public DOMFilePageHeader() {
			super();
		}

		public DOMFilePageHeader(byte[] data, int offset) throws IOException {
			super(data, offset);
		}

		public void decRecordCount() {
			--records;
		}

		public short getCurrentTID() {
			return tid;
		}

		public short getNextTID() {
			if (++tid == ItemId.ID_MASK)
				throw new RuntimeException("no spare ids on page");
			return tid;
		}

		public boolean hasRoom() {
			return tid < ItemId.MAX_ID;
		}

		public void setNextTID(short tid) {
			if (tid > ItemId.MAX_ID)
				throw new RuntimeException("TID overflow! TID = " + tid);
			this.tid = tid;
		}

		public int getDataLength() {
			return dataLen;
		}

		public long getNextDataPage() {
			return nextDataPage;
		}

		public long getPrevDataPage() {
			return prevDataPage;
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
			nextDataPage = ByteConversion.byteToLong(data, offset);
			offset += 8;
			prevDataPage = ByteConversion.byteToLong(data, offset);
			offset += 8;
			tid = ByteConversion.byteToShort(data, offset);
			return offset + 2;
		}

		public int write(byte[] data, int offset) throws IOException {
			offset = super.write(data, offset);
			ByteConversion.shortToByte(records, data, offset);
			offset += 2;
			ByteConversion.intToByte(dataLen, data, offset);
			offset += 4;
			ByteConversion.longToByte(nextDataPage, data, offset);
			offset += 8;
			ByteConversion.longToByte(prevDataPage, data, offset);
			offset += 8;
			ByteConversion.shortToByte(tid, data, offset);
			return offset + 2;
		}

		public void setDataLength(int len) {
			dataLen = len;
		}

		public void setNextDataPage(long page) {
			nextDataPage = page;
		}

		public void setPrevDataPage(long page) {
			prevDataPage = page;
		}

		public void setRecordCount(short recs) {
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

		DOMFilePageHeader ph;

		// fields required by Cacheable
		int refCount = 0;

		int timestamp = 0;

		// has the page been saved or is it dirty?
		boolean saved = true;

		// set to true if the page has been removed from the cache
		boolean invalidated = false;

		public DOMPage() {
			page = createNewPage();
			ph = (DOMFilePageHeader) page.getPageHeader();
			// LOG.debug("Created new page: " + page.getPageNum());
			data = new byte[fileHeader.getWorkSize()];
			len = 0;
		}

		public DOMPage(long pos) {
			try {
				page = getPage(pos);
				load(page);
			} catch (IOException ioe) {
				LOG.debug(ioe);
				ioe.printStackTrace();
			}
		}

		public DOMPage(Page page) {
			this.page = page;
			load(page);
		}

		protected Page createNewPage() {
			try {
				Page page = getFreePage();
				DOMFilePageHeader ph = (DOMFilePageHeader) page.getPageHeader();
				ph.setStatus(RECORD);
				ph.setDirty(true);
				ph.setNextDataPage(Page.NO_PAGE);
				ph.setPrevDataPage(Page.NO_PAGE);
				ph.setNextPage(Page.NO_PAGE);
				ph.setNextTID(ItemId.UNKNOWN_ID);
				ph.setDataLength(0);
				ph.setRecordCount((short) 0);
				if (currentDocument != null)
					currentDocument.getMetadata().incPageCount();
//	            LOG.debug("New page: " + page.getPageNum() + "; " + page.getPageInfo());
				return page;
			} catch (IOException ioe) {
				LOG.warn(ioe);
				return null;
			}
		}

		public RecordPos findRecord(short targetId) {
			final int dlen = ph.getDataLength();
			RecordPos rec = null;
			for (int pos = 0; pos < dlen;) {
				short tid = ByteConversion.byteToShort(data, pos);
				pos += LENGTH_TID;
				if (ItemId.matches(tid, targetId)) {
					if (ItemId.isLink(tid)) {
						rec = new RecordPos(pos, this, tid, true);
					} else {
						rec = new RecordPos(pos, this, tid);
					}
					break;
				} else if (ItemId.isLink(tid)) {
					pos += LENGTH_FORWARD_LOCATION;
				} else {
					short vlen = ByteConversion.byteToShort(data, pos);
					pos += LENGTH_DATA_LENGTH;
					if (vlen < 0) {
						LOG.warn("page = " + page.getPageNum() + "; pos = "
								+ pos + "; vlen = " + vlen + "; tid = "
								+ tid + "; target = " + targetId);
					}
					if (ItemId.isRelocated(tid)) {
						pos += LENGTH_ORIGINAL_LOCATION + vlen;
					} else {
						pos += vlen;
					}
					if (vlen == OVERFLOW)
						pos += LENGTH_OVERFLOW_LOCATION;
				}
			}
			return rec;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.exist.storage.cache.Cacheable#getKey()
		 */
		public long getKey() {
			return page.getPageNum();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.exist.storage.cache.Cacheable#getReferenceCount()
		 */
		public int getReferenceCount() {
			return refCount;
		}

		public int decReferenceCount() {
			return refCount > 0 ? --refCount : 0;
		}

		public int incReferenceCount() {
			if (refCount < Cacheable.MAX_REF)
				++refCount;
			return refCount;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.exist.storage.cache.Cacheable#setReferenceCount(int)
		 */
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

		public DOMFilePageHeader getPageHeader() {
			return ph;
		}

		public long getPageNum() {
			return page.getPageNum();
		}

		public boolean isDirty() {
			return !saved;
		}

		public void setDirty(boolean dirty) {
			saved = !dirty;
			page.getPageHeader().setDirty(dirty);
		}

		private void load(Page page) {
			try {
				data = page.read();
				ph = (DOMFilePageHeader) page.getPageHeader();
				len = ph.getDataLength();
				if (data.length == 0) {
					data = new byte[fileHeader.getWorkSize()];
					len = 0;
					return;
				}
			} catch (IOException ioe) {
				LOG.debug(ioe);
				ioe.printStackTrace();
			}
			saved = true;
		}

		public void write() {
			if (page == null)
				return;
			try {
				if (!ph.isDirty())
					return;
				ph.setDataLength(len);
				writeValue(page, data);
				setDirty(false);
			} catch (IOException ioe) {
				LOG.error(ioe);
			}
		}

		public String dumpPage() {
			return "Contents of page " + page.getPageNum() + ": "
					+ hexDump(data);
		}

		public boolean sync(boolean syncJournal) {
			if (isDirty()) {
				write();
                if (isTransactional && syncJournal && logManager.lastWrittenLsn() < ph.getLsn())
                    logManager.flushToLog(true);
				return true;
			}
			return false;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.exist.storage.cache.Cacheable#allowUnload()
		 */
		public boolean allowUnload() {
			return true;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		public boolean equals(Object obj) {
			DOMPage other = (DOMPage) obj;
			return page.equals(other.page);
		}

		public void invalidate() {
			invalidated = true;
		}

		public boolean isInvalidated() {
			return invalidated;
		}

		/**
		 * Walk through the page after records have been removed. Set the tid
		 * counter to the next spare id that can be used for following
		 * insertions.
		 */
		public void cleanUp() {
			final int dlen = ph.getDataLength();
			short maxTID = 0;
			for (int pos = 0; pos < dlen;) {
				short tid = ByteConversion.byteToShort(data, pos);
				pos += LENGTH_TID;
				if (ItemId.getId(tid) > ItemId.MAX_ID) {
					LOG.debug(debugPageContents(this));
					throw new RuntimeException("TID overflow in page "
							+ getPageNum());
				}
				if (ItemId.getId(tid) > maxTID)
					maxTID = ItemId.getId(tid);
				if (ItemId.isLink(tid)) {
					pos += LENGTH_FORWARD_LOCATION;
				} else {
					short vlen = ByteConversion.byteToShort(data, pos);
					pos += LENGTH_DATA_LENGTH;
					if (ItemId.isRelocated(tid)) {
						pos += vlen == OVERFLOW ? LENGTH_ORIGINAL_LOCATION + LENGTH_OVERFLOW_LOCATION : LENGTH_ORIGINAL_LOCATION + vlen;
					} else
						pos += vlen == OVERFLOW ? LENGTH_OVERFLOW_LOCATION : vlen;
				}
			}
			ph.setNextTID(maxTID);
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
	protected final class OverflowDOMPage {

		Page firstPage = null;

		public OverflowDOMPage(Txn transaction) {
			firstPage = createNewPage();
			LOG.debug("Creating overflow page: " + firstPage.getPageNum());
		}

		public OverflowDOMPage(long first) throws IOException {
			firstPage = getPage(first);
		}

		protected Page createNewPage() {
			try {
				Page page = getFreePage();
				DOMFilePageHeader ph = (DOMFilePageHeader) page.getPageHeader();
				ph.setStatus(RECORD);
				ph.setDirty(true);
				ph.setNextDataPage(Page.NO_PAGE);
				ph.setPrevDataPage(Page.NO_PAGE);
				ph.setNextPage(Page.NO_PAGE);
				ph.setNextTID(ItemId.UNKNOWN_ID);
				ph.setDataLength(0);
				ph.setRecordCount((short) 0);
				if (currentDocument != null)
					currentDocument.getMetadata().incPageCount();
//	            LOG.debug("New page: " + page.getPageNum() + "; " + page.getPageInfo());
				return page;
			} catch (IOException ioe) {
				LOG.warn(ioe);
				return null;
			}
		}

		// Write binary resource from inputstream
		public int write(Txn transaction, InputStream is) {
		
		    int pageCount = 0;		    
		    Page currentPage = firstPage;
		
		    try {
		        // Transfer bytes from inputstream to db
		    	final int chunkSize = fileHeader.getWorkSize();
		        byte[] buf = new byte[chunkSize];
		        int len = is.read(buf);
		        
		        // Check if stream does contain any data
		        if (len < 1){
		        	currentPage.setPageNum(Page.NO_PAGE);
		        	currentPage.getPageHeader().setNextPage(Page.NO_PAGE);
		        	//Shouldn't we return 0 here ?
		            return -1;
		        }
		        
		        // Read remaining stream
		        while ( len > -1 ) {
		            // If there are bytes in stream, read
		            if (len > 0){
		                Value value = new Value(buf, 0, len);
			        	Page nextPage;
		                if (len == chunkSize) {
		                	nextPage = createNewPage();
		                	currentPage.getPageHeader().setNextPage(nextPage.getPageNum());		                    
		                } else {
				        	nextPage = null;
		                	currentPage.getPageHeader().setNextPage(Page.NO_PAGE);
		                }
		                
		                // If no data is in input stream left, don't write
		                if (isTransactional && transaction != null) {
		                    long nextPageNum = (nextPage == null) ? 
		                    		Page.NO_PAGE :
		                    		nextPage.getPageNum();		                    
		                    Loggable loggable = new WriteOverflowPageLoggable(
		                            transaction, currentPage.getPageNum(),
		                            nextPageNum , value);
		                    writeToLog(loggable, currentPage);
		                }
		                
		                writeValue(currentPage, value);
		                pageCount++;
		                currentPage = nextPage;
		            }
		            len = is.read(buf);
		        }
		        // TODO what if remaining length=0?
		
		    } catch (IOException ex) {
		        LOG.error("io error while writing overflow page", ex);
		    }
		
		    return pageCount;
		}
    
    
		public int write(Txn transaction, byte[] data) {
            int pageCount = 0;
            try {
                int remaining = data.length;
                Page currentPage = firstPage;
                int pos = 0;
                while (remaining > 0) {
                    final int chunkSize = remaining > fileHeader.getWorkSize() ? 
                    		fileHeader.getWorkSize() : remaining;
                    remaining -= chunkSize;
                    Value value = new Value(data, pos, chunkSize);
                    Page nextPage;
                    if (remaining > 0) {
                    	nextPage = createNewPage();                        
                        currentPage.getPageHeader().setNextPage(nextPage.getPageNum());
                    } else {
                    	nextPage = null;
                    	currentPage.getPageHeader().setNextPage(Page.NO_PAGE);
                    }
                    
                    if (isTransactional && transaction != null) {
                        Loggable loggable = new WriteOverflowPageLoggable(
                                transaction, currentPage.getPageNum(),
                                remaining > 0 ? nextPage.getPageNum() : Page.NO_PAGE, value);
                        writeToLog(loggable, currentPage);
                    }
                    
                    writeValue(currentPage, value);
                    pos += chunkSize;
                    currentPage = nextPage;
                    ++pageCount;
                }
            } catch (IOException e) {
                LOG.error("io error while writing overflow page", e);
            }
            return pageCount;
		}

		public byte[] read() {
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			streamTo(os);
			return os.toByteArray();
		}

		public void streamTo(OutputStream os) {
			Page page = firstPage;
			int count = 0;
			while (page != null) {
				try {
					byte[] chunk = page.read();
					os.write(chunk);
					long nextPageNumber = page.getPageHeader().getNextPage();
					page = (nextPageNumber == Page.NO_PAGE) ? null : getPage(nextPageNumber);
				} catch (IOException e) {
					LOG.error("io error while loading overflow page "
							+ firstPage.getPageNum() + "; read: " + count, e);
					break;
				}
				++count;
			}
		}
		
		public void delete(Txn transaction) throws IOException {
			Page page = firstPage;
			while (page != null) {
				LOG.debug("removing overflow page " + page.getPageNum());
				long nextPageNumber = page.getPageHeader().getNextPage();
				if (isTransactional && transaction != null) {
					byte[] chunk = page.read();
					Loggable loggable = new RemoveOverflowLoggable(transaction,
							page.getPageNum(), nextPageNumber, chunk);
					writeToLog(loggable, page);
				}
				unlinkPages(page);
				page = (nextPageNumber == Page.NO_PAGE) ? null : getPage(nextPageNumber);
			}
		}

		public long getPageNum() {
			return firstPage.getPageNum();
		}
	}

	public final void addToBuffer(DOMPage page) {
		dataCache.add(page);
	}

	private final class FindCallback implements BTreeCallback {

		public final static int KEYS = 1;
		public final static int VALUES = 0;

		int mode;

		ArrayList values = new ArrayList();

		public FindCallback(int mode) {
			this.mode = mode;
		}

		public ArrayList getValues() {
			return values;
		}

		public boolean indexInfo(Value value, long pointer) {
			switch (mode) {
			case VALUES:
				RecordPos rec = findRecord(pointer);
				short len = ByteConversion.byteToShort(rec.getPage().data, rec.offset);
				values.add(new Value(rec.getPage().data, rec.offset + LENGTH_DATA_LENGTH, len));
				return true;
			case KEYS:
				values.add(value);
				return true;
			}
			return false;
		}
	}

}
