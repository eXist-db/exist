package org.exist.storage.store;

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
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;

import org.dbxml.core.DBException;
import org.dbxml.core.data.Value;
import org.dbxml.core.filer.BTree;
import org.dbxml.core.filer.BTreeCallback;
import org.dbxml.core.filer.BTreeException;
import org.dbxml.core.indexer.IndexQuery;
import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeIndexListener;
import org.exist.dom.NodeProxy;
import org.exist.dom.XMLUtil;
import org.exist.storage.BufferStats;
import org.exist.storage.NativeBroker;
import org.exist.storage.Signatures;
import org.exist.storage.cache.Cache;
import org.exist.storage.cache.Cacheable;
import org.exist.storage.cache.LRUCache;
import org.exist.util.ByteConversion;
import org.exist.util.Lock;
import org.exist.util.Lockable;
import org.exist.util.ReadOnlyException;
import org.exist.util.ReentrantReadWriteLock;
import org.exist.util.hashtable.Object2LongIdentityHashMap;
import org.exist.util.sanity.SanityCheck;
import org.exist.xquery.TerminatedException;
import org.w3c.dom.Node;

/**
 * This is the main storage for XML nodes. Nodes are stored in document order.
 * Every document gets its own sequence of pages, which is bound to the
 * writing thread to avoid conflicting writes. The page structure is as follows:
 * 
 * | page header | (tid1 node-data, tid2 node-data, ..., tidn node-data) |
 * 
 * node-data contains the raw binary data of the node as returned by {@link org.exist.dom.NodeImpl#serialize()}.
 * Within a page, a node is identified by a unique id, called tuple id (tid). Every node 
 * can thus be located by a virtual address pointer, which consists of the page id and 
 * the tid. Both components are encoded in a long value (with additional bits used for
 * optional flags). The address pointer is used to reference nodes from the indexes. It should 
 * thus remain unchanged during the life-time of a document.
 * 
 * However, XUpdate requests may insert new nodes in the middle of a page. In these
 * cases, the page will be split and the upper portion of the page is copied to a split page.
 * The record in the original page will be replaced by a forward link, pointing to the new
 * location of the node data in the split page.
 * 
 * As a consequence, the class has to distinguish three different types of data records:
 * 
 * 1) Ordinary record:
 * 
 * | tid | length | data |
 * 
 * 3) Relocated record:
 * 
 * | tid | length | address pointer to original location | data |
 * 
 * 2) Forward link:
 * 
 * | tid | address pointer |
 * 
 * tid and length each use two bytes (short), address pointers 8 bytes (long).
 * The upper two bits of the tid are used to indicate the type of the record 
 * (see {@see org.exist.storage.store.ItemId}).
 * 
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 */
public class DOMFile extends BTree implements Lockable {

    public final static short FILE_FORMAT_VERSION_ID = 2;

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
    
    protected DOMFile(int buffers, int dataBuffers) {
        super(buffers);
        lock = new ReentrantReadWriteLock("dom.dbx");
        fileHeader = (BTreeFileHeader) getFileHeader();
        fileHeader.setPageCount(0);
        fileHeader.setTotalCount(0);
        dataCache = new LRUCache(dataBuffers);
        dataCache.setFileName("dom.dbx");
    }

    public DOMFile(File file, int buffers, int dataBuffers) {
        this(buffers, dataBuffers);
        setFile(file);
    }

    protected final Cache getPageBuffer() {
        return dataCache;
    }

    /**
     * @return
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
     * This method is called when storing a new document. Each writing
     * thread gets its own sequence of pages for writing a document, so all
     * document nodes are stored in sequential order. A new page will be allocated
     * if the current page is full. If the value is larger than the page size, it will 
     * be written to an overflow page.
     * 
     * @param value
     *                   the value to append
     * @return the virtual storage address of the value
     */
    public long add(byte[] value) throws ReadOnlyException {
        if (value == null || value.length == 0) return -1;
        // overflow value?
        if (value.length + 4 > fileHeader.getWorkSize()) {
        	LOG.debug("Creating overflow page");
            OverflowDOMPage overflow = new OverflowDOMPage();
            overflow.write(value);
            byte[] pnum = ByteConversion.longToByte(overflow.getPageNum());
            return add(pnum, true);
        } else
            return add(value, false);
    }

    /**
     * Append a value to the current page. If overflowPage is
     * true, the value will be saved into its own, reserved chain
     * of pages. The current page will just contain a link to the first
     * overflow page.
     * 
     * @param value
     * @param overflowPage
     * @return
     * @throws ReadOnlyException
     */
    private long add(byte[] value, boolean overflowPage)
            throws ReadOnlyException {
        final int valueLen = value.length;
        final Object myOwner = owner;
        // always append data to the end of the file
        DOMPage page = getCurrentPage();
        // does value fit into current data page?
        if (page == null || page.len + 4 + valueLen > page.data.length) {
            DOMPage newPage = new DOMPage();
            if (page != null) {
                DOMFilePageHeader ph = page.getPageHeader();
                ph.setNextDataPage(newPage.getPageNum());
                newPage.getPageHeader().setPrevDataPage(page.getPageNum());
                page.setDirty(true);
                dataCache.add(page);
            }
//            LOG.debug("created new page: " + newPage.getPageNum() + "; prev = " + page.getPageNum() +
//            	"; oldLen = " + page.len + "; valueLen = " + valueLen);
            page = newPage;
            setCurrentPage(newPage);
            SanityCheck.ASSERT(owner == myOwner, "Owner changed during transaction!!!!!!!!!!!!!!!!!");
        }
        // save tuple identifier
        final DOMFilePageHeader ph = page.getPageHeader();
        final short tid = ph.getNextTID();
//        LOG.debug("writing to " + page.getPageNum() + "; " + page.len + "; tid = " + tid + 
//        		"; len = " + valueLen + "; dataLen = " + page.data.length);
        ByteConversion.shortToByte(tid, page.data, page.len);
        page.len += 2;
        // save data length
        // overflow pages have length 0
        ByteConversion.shortToByte(overflowPage ? OVERFLOW : (short) valueLen,
                page.data, page.len);
        page.len += 2;
        // save data
        System.arraycopy(value, 0, page.data, page.len, valueLen);
        page.len += valueLen;
        ph.incRecordCount();
        ph.setDataLength(page.len);
        page.setDirty(true);
        dataCache.add(page, 2);
        // create pointer from pageNum and offset into page
        final long p = StorageAddress.createPointer((int) page.getPageNum(),
                tid);
        return p;
    }

    /**
     * Store a raw binary resource into the file. The data will always
     * be written into an overflow page.
     * 
     * @param value
     * @return
     */
    public long addBinary(byte[] value) {
        OverflowDOMPage overflow = new OverflowDOMPage();
        overflow.write(value);
        return overflow.getPageNum();
    }

    /**
     * Return binary data stored with {@link #addBinary(byte[])}.
     * 
     * @param pageNum
     * @return
     */
    public byte[] getBinary(long pageNum) {
        return getOverflowValue(pageNum);
    }

    /**
     * Insert a new node after the specified node.
     * 
     * @param key
     * @param value
     * @return
     */
    public long insertAfter(DocumentImpl doc, Value key, byte[] value) {
        try {
            final long p = findValue(key);
            if (p == KEY_NOT_FOUND) return -1;
            return insertAfter(doc, p, value);
        } catch (BTreeException e) {
            LOG.warn("key not found", e);
        } catch (IOException e) {
            LOG.warn("IO error", e);
        }
        return -1;
    }

    /**
     * Insert a new node after the node located at the specified address.
     * 
     * If the previous node is in the middle of a page, the page is split. If the
     * node is appended at the end and the page does not have enough room
     * for the node, a new page is added to the page sequence.
     * 
     * @param doc
     *                   the document to which the new node belongs.
     * @param address
     *                   the storage address of the node after which the new value
     *                   should be inserted.
     * @param value
     *                   the value of the new node.
     * @return
     */
    public long insertAfter(DocumentImpl doc, long address, byte[] value) {
        //check if we need an overflow page
        boolean isOverflow = false;
        if (value.length + 4 > fileHeader.getWorkSize()) {
            OverflowDOMPage overflow = new OverflowDOMPage();
            			LOG.debug("creating overflow page: " + overflow.getPageNum());
            overflow.write(value);
            value = ByteConversion.longToByte(overflow.getPageNum());
            isOverflow = true;
        }
        // locate the node to insert after
        RecordPos rec = findRecord(address);
        if (rec == null) {
            SanityCheck.TRACE("page not found");
            return -1;
        }
        short l = ByteConversion.byteToShort(rec.page.data, rec.offset);
        if(ItemId.isRelocated(rec.tid))
            rec.offset += 8;
        if (l == OVERFLOW)
            rec.offset += 10;
        else
            rec.offset = rec.offset + l + 2;
        int dataLen = rec.page.getPageHeader().getDataLength();
        // insert in the middle of the page?
        if (rec.offset < dataLen) {
            if (dataLen + value.length + 4 < fileHeader.getWorkSize() &&
            		rec.page.getPageHeader().hasRoom()) {
//                LOG.debug("copying data in page " + rec.page.getPageNum()
//                                + "; offset = " + rec.offset + "; dataLen = "
//                                + dataLen + "; valueLen = " + value.length);
                // new value fits into the page
                int end = rec.offset + value.length + 4;
                System.arraycopy(rec.page.data, rec.offset, rec.page.data, end,
                        dataLen - rec.offset);
                rec.page.len = dataLen + value.length + 4;
                rec.page.getPageHeader().setDataLength(rec.page.len);
            } else {
                // doesn't fit: split the page
                rec = splitDataPage(doc, rec);
                if (rec.offset + value.length + 4 > fileHeader.getWorkSize() ||
                		!rec.page.getPageHeader().hasRoom()) {
                    // still not enough free space: create a new page
                    DOMPage newPage = new DOMPage();
                    LOG.debug("creating additional page: " + newPage.getPageNum());
                    newPage.getPageHeader().setNextDataPage(
                            rec.page.getPageHeader().getNextDataPage());
                    newPage.getPageHeader().setPrevDataPage(rec.page.getPageNum());
                    rec.page.getPageHeader().setNextDataPage(
                            newPage.getPageNum());
                    rec.page.setDirty(true);
                    dataCache.add(rec.page);
                    rec.page = newPage;
                    rec.offset = 0;
                    rec.page.len = value.length + 4;
                    rec.page.getPageHeader().setDataLength(rec.page.len);
                    rec.page.getPageHeader().setRecordCount((short)1);
                } else {
                    rec.page.len = rec.offset + value.length + 4;
                    rec.page.getPageHeader().setDataLength(rec.page.len);
                    dataLen = rec.offset;
                }
            }
        } else if (dataLen + value.length + 4 > fileHeader.getWorkSize() ||
        		!rec.page.getPageHeader().hasRoom()) {
            // does value fit into page?
            DOMPage newPage = new DOMPage();
            LOG.debug("creating new page: " + newPage.getPageNum());
            long next = rec.page.getPageHeader().getNextDataPage();
            newPage.getPageHeader().setNextDataPage(
                    next);
            newPage.getPageHeader().setPrevDataPage(rec.page.getPageNum());
            rec.page.getPageHeader().setNextDataPage(newPage.getPageNum());
            if(-1 < next) {
                DOMPage nextPage = getCurrentPage(next);
                nextPage.getPageHeader().setPrevDataPage(newPage.getPageNum());
                nextPage.setDirty(true);
                dataCache.add(nextPage);
            }
            rec.page.setDirty(true);
            dataCache.add(rec.page);
            rec.page = newPage;
            rec.offset = 0;
            rec.page.len = value.length + 4;
            rec.page.getPageHeader().setDataLength(rec.page.len);
        } else {
            rec.page.len = dataLen + value.length + 4;
            rec.page.getPageHeader().setDataLength(rec.page.len);
        }
        // write the data
        short tid = rec.page.getPageHeader().getNextTID();
        // writing tid
        ByteConversion.shortToByte((short) tid, rec.page.data, rec.offset);
        rec.offset += 2;
        // writing value length
        ByteConversion.shortToByte(isOverflow ? 0 : (short) value.length,
                rec.page.data, rec.offset);
        rec.offset += 2;
        // writing data
        System.arraycopy(value, 0, rec.page.data, rec.offset, value.length);
        rec.offset += value.length;
        rec.page.getPageHeader().incRecordCount();
        rec.page.setDirty(true);
        if(rec.page.getPageHeader().getCurrentTID() >= ItemId.DEFRAG_LIMIT &&
        		doc != null)
        	doc.triggerDefrag();
//        LOG.debug(debugPageContents(rec.page));
        dataCache.add(rec.page);
        return StorageAddress.createPointer((int) rec.page.getPageNum(), tid);
    }

    /**
     * Split a data page at the position indicated by the rec parameter.
     * 
     * The portion of the page starting at rec.offset is moved into a new page.
     * Every moved record is marked as relocated and a link is stored into
     * the original page to point to the new record position.  
     * 
     * @param doc
     * @param rec
     */
    private RecordPos splitDataPage(DocumentImpl doc, RecordPos rec) {
        if(currentDocument != null)
            currentDocument.incSplitCount();
        // check if a split is really required. A split is not required if all records
        // following the split point are already links to other pages. In this case,
        // the new record is just appended to a new page linked to the old one.
        boolean requireSplit = false;
        for (int pos = rec.offset; pos < rec.page.len;) {
            short currentId = ByteConversion.byteToShort(rec.page.data, pos);
            if(!ItemId.isLink(currentId)) {
                requireSplit = true;
                break;
            }
            pos += 10;
        }
        if(!requireSplit) {
            LOG.debug("page " + rec.page.getPageNum() + ": no split required");
            rec.offset = rec.page.len;
            return rec;
        }
        NodeIndexListener idx = doc.getIndexListener();
        
        // copy the old data up to the split point into a new array
        int oldDataLen = rec.page.getPageHeader().getDataLength();
        byte[] oldData = rec.page.data;
        long oldPageNum = rec.page.getPageNum();
        rec.page.data = new byte[fileHeader.getWorkSize()];
        System.arraycopy(oldData, 0, rec.page.data, 0, rec.offset);
        
        // the old rec.page now contains a copy of the data up to the split point
        rec.page.len = rec.offset;
        rec.page.setDirty(true);
        
        // create a first split page
        DOMPage firstSplitPage = new DOMPage();
        DOMPage nextSplitPage = firstSplitPage;
        nextSplitPage.getPageHeader().setNextTID((short)(rec.page.getPageHeader().getCurrentTID()));
        short tid, currentId, currentLen, realLen;
        long backLink;
        short splitRecordCount = 0;
        LOG.debug("splitting " + rec.page.getPageNum() + " at " + rec.offset + ": new: "
                + nextSplitPage.getPageNum() + "; next: " + 
                rec.page.getPageHeader().getNextDataPage());
        
        // start copying records from rec.offset to the new split pages
        for (int pos = rec.offset; pos < oldDataLen; splitRecordCount++) {
            // read the current id
            currentId = ByteConversion.byteToShort(oldData, pos);
            tid = ItemId.getId(currentId);
            pos += 2;
            if(ItemId.isLink(currentId)) {
                /* This is already a link, so we just copy it */
                ByteConversion.shortToByte(currentId,
                        rec.page.data, rec.page.len);
                rec.page.len += 2;
                System.arraycopy(oldData, pos, rec.page.data, rec.page.len, 8);
                rec.page.len += 8;
                pos += 8;
                continue;
            }
            // read data length
            currentLen = ByteConversion.byteToShort(oldData, pos);
            pos += 2;
            // if this is an overflow page, the real data length is always 8 byte
            // for the page number of the overflow page
            realLen = (currentLen == OVERFLOW ? 8 : currentLen);
            
            // check if we have room in the current split page
            if(nextSplitPage.len + realLen + 12 > fileHeader.getWorkSize()) {
                // not enough room in the split page: append a new page
                DOMPage newPage = new DOMPage();
                newPage.getPageHeader().setNextTID((short)(rec.page.getPageHeader().getNextTID() - 1));
                newPage.getPageHeader().setPrevDataPage(nextSplitPage.getPageNum());
                LOG.debug("creating new split page: " + newPage.getPageNum());
                nextSplitPage.getPageHeader().setNextDataPage(newPage.getPageNum());
                nextSplitPage.getPageHeader().setDataLength(nextSplitPage.len);
                nextSplitPage.getPageHeader().setRecordCount(splitRecordCount);
                nextSplitPage.setDirty(true);
                dataCache.add(nextSplitPage);
                dataCache.add(newPage);
                nextSplitPage = newPage;
                splitRecordCount = 0;
            }
            
            /* if the record has already been relocated, read the original storage
             * address and update the link there.
             */
            if(ItemId.isRelocated(currentId)) {
                backLink = ByteConversion.byteToLong(oldData, pos);
                pos += 8;
                RecordPos origRec = findRecord(backLink, false);
                long forwardLink = StorageAddress.createPointer((int) nextSplitPage
                        .getPageNum(), tid);
                ByteConversion.longToByte(forwardLink, origRec.page.data, origRec.offset);
                origRec.page.setDirty(true);
                dataCache.add(origRec.page);
            } else
                backLink = StorageAddress.createPointer(
                        (int) rec.page.getPageNum(), tid);
            
            // save the record to the split page:
            
            // set the relocated flag and save the item id
            ByteConversion.shortToByte(ItemId.setIsRelocated(currentId),
                    nextSplitPage.data, nextSplitPage.len);
            nextSplitPage.len += 2;
            //	save length field
            ByteConversion.shortToByte(currentLen, nextSplitPage.data,
                    nextSplitPage.len);
            nextSplitPage.len += 2;
            // save link to the original page
            ByteConversion.longToByte(backLink, nextSplitPage.data,
                    nextSplitPage.len);
            nextSplitPage.len += 8;
            
            // now save the data
            try { 
                System.arraycopy(oldData, pos, nextSplitPage.data,
                        nextSplitPage.len, realLen);
            } catch (ArrayIndexOutOfBoundsException e) {
                SanityCheck.TRACE("pos = " + pos + "; len = " + nextSplitPage.len + "; currentLen = " + realLen +
                        "; tid = " + currentId + "; page = " + rec.page.getPageNum());
                throw e;
            }
            nextSplitPage.len += realLen;
            pos += realLen;

            // report the split to the index listener. Pass it the old and the new storage address.
            if(idx != null) {
	            idx.nodeChanged(StorageAddress.createPointer((int) oldPageNum, tid), StorageAddress.createPointer(
	                    (int) nextSplitPage.getPageNum(), tid));
            }
            
            // save a link pointer in the original page if the record has not been
            // relocated before.
            if(!ItemId.isRelocated(currentId)) {
                if(rec.page.len + 10 > fileHeader.getWorkSize()) {
                    // the link doesn't fit into the old page. Append a new page
                    DOMPage newPage = new DOMPage();
                    newPage.getPageHeader().setNextTID((short)(rec.page.getPageHeader().getNextTID() - 1));
                    newPage.getPageHeader().setPrevDataPage(rec.page.getPageNum());
                    newPage.getPageHeader().setNextDataPage(rec.page.getPageHeader().getNextDataPage());
                    LOG.debug("creating new page after split: " + newPage.getPageNum());
                    rec.page.getPageHeader().setNextDataPage(newPage.getPageNum());
                    rec.page.getPageHeader().setDataLength(rec.page.len);
                    rec.page.getPageHeader().setRecordCount(countRecordsInPage(rec.page));
                    rec.page.setDirty(true);
                    dataCache.add(rec.page);
                    dataCache.add(newPage);
                    rec.page = newPage;
                    rec.page.len = 0;
                }
                ByteConversion.shortToByte(ItemId.setIsLink(currentId),
                    rec.page.data, rec.page.len);
                rec.page.len += 2;
                long forwardLink = StorageAddress.createPointer((int) nextSplitPage
                    .getPageNum(), tid);
                ByteConversion.longToByte(forwardLink, rec.page.data, rec.page.len);
                rec.page.len += 8;
            }
        } // finished copying data
        
        // link the split pages to the original page
        
        if(nextSplitPage.len == 0) {
            LOG.warn("page " + nextSplitPage.getPageNum() + " is empty. Remove it");
            // if nothing has been copied to the last split page,
            // remove it
            dataCache.remove(nextSplitPage);
            if(nextSplitPage == firstSplitPage)
                firstSplitPage = null;
            try {
                unlinkPages(nextSplitPage.page);
            } catch (IOException e) {
                LOG.warn("Failed to remove empty split page: " + e.getMessage(), e);
            }
            nextSplitPage = null;
        } else {
	        nextSplitPage.getPageHeader().setDataLength(nextSplitPage.len);
	        nextSplitPage.getPageHeader().setNextDataPage(
	                rec.page.getPageHeader().getNextDataPage());
	        nextSplitPage.getPageHeader().setRecordCount(splitRecordCount);
	        nextSplitPage.setDirty(true);
	        dataCache.add(nextSplitPage);
	        firstSplitPage.getPageHeader().setPrevDataPage(rec.page.getPageNum());
	        if(nextSplitPage != firstSplitPage) {
	            firstSplitPage.setDirty(true);
	            dataCache.add(firstSplitPage);
	        }
        }
        long next = rec.page.getPageHeader().getNextDataPage();
        if(-1 < next) {
            DOMPage nextPage = getCurrentPage(next);
            nextPage.getPageHeader().setPrevDataPage(nextSplitPage.getPageNum());
            nextPage.setDirty(true);
            dataCache.add(nextPage);
        }
        rec.page = getCurrentPage(rec.page.getPageNum());
        if(firstSplitPage != null) {
            rec.page.getPageHeader().setNextDataPage(firstSplitPage.getPageNum());
        }
        rec.page.getPageHeader().setDataLength(rec.page.len);
        rec.page.getPageHeader().setRecordCount(countRecordsInPage(rec.page));
        rec.offset = rec.page.len;
        return rec;
    }

    /**
     * Returns the number of records stored in a page.
     * 
     * @param page
     * @return
     */
    private short countRecordsInPage(DOMPage page) {
        short count = 0;
        short currentId, vlen;
        int dlen = page.getPageHeader().getDataLength();
        for (int pos = 0; pos < dlen; count++) {
            currentId = ByteConversion.byteToShort(page.data, pos);
            if (ItemId.isLink(currentId)) {
                    pos += 10;
            } else {
                vlen = ByteConversion.byteToShort(page.data, pos + 2);
                if (ItemId.isRelocated(currentId)) {
                    pos += vlen == OVERFLOW ? 20 : vlen + 12;
                } else
                    pos += vlen == OVERFLOW ? 12 : vlen + 4;
            }
        }
//        LOG.debug("page " + page.getPageNum() + " has " + count + " records.");
        return count;
    }
    
    public String debugPageContents(DOMPage page) {
    	StringBuffer buf = new StringBuffer();
    	buf.append("Page " + page.getPageNum() + ": ");
    	short count = 0;
    	short currentId, vlen;
    	int dlen = page.getPageHeader().getDataLength();
    	for (int pos = 0; pos < dlen; count++) {
    		currentId = ByteConversion.byteToShort(page.data, pos);
    		buf.append(ItemId.getId(currentId) + "[" + pos);
    		if (ItemId.isLink(currentId)) {
    			buf.append(':').append(10).append("] ");
    			pos += 10;
    		} else {
    			vlen = ByteConversion.byteToShort(page.data, pos + 2);
    			if(vlen < 0) {
    				LOG.warn("Illegal length: " + vlen);
    				return buf.toString();
    			}
    			buf.append(':').append(vlen).append("] ");
    			if (ItemId.isRelocated(currentId)) {
    				pos += vlen == OVERFLOW ? 20 : vlen + 12;
    			} else
    				pos += vlen == OVERFLOW ? 12 : vlen + 4;
    		}
    	}
    	buf.append("; records in page: " + count);
    	buf.append("; nextTID: " + page.getPageHeader().getCurrentTID());
    	return buf.toString();
    }
    
    public boolean close() throws DBException {
        flush();
        super.close();
        return true;
    }

    public boolean create() throws DBException {
        if (super.create((short) 12))
            return true;
        else
            return false;
    }

    public FileHeader createFileHeader() {
        return new BTreeFileHeader(1024, PAGE_SIZE);
    }

    protected Page createNewPage() {
        try {
            Page page = getFreePage();
            DOMFilePageHeader ph = (DOMFilePageHeader) page.getPageHeader();
            ph.setStatus(RECORD);
            ph.setDirty(true);
            ph.setNextDataPage(-1);
            ph.setPrevDataPage(-1);
            ph.setNextTID((short) -1);
            ph.setDataLength(0);
            ph.setRecordCount((short) 0);
            if(currentDocument != null)
                currentDocument.incPageCount();
//            SanityCheck.TRACE("New page: " + page.getPageNum());
            return page;
        } catch (IOException ioe) {
            LOG.warn(ioe);
            return null;
        }
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

    private long findNode(NodeImpl node, long target, Iterator iter) {
        if (node.hasChildNodes()) {
            final long firstChildId = XMLUtil.getFirstChildId(
                    (DocumentImpl) node.getOwnerDocument(), node.getGID());
            if (firstChildId < 0) {
                LOG.debug("first child not found: " + node.getGID());
                return 0;
            }
            final long lastChildId = firstChildId + node.getChildCount();
            long p;
            for (long gid = firstChildId; gid < lastChildId; gid++) {
                NodeImpl child = (NodeImpl) iter.next();
                
                SanityCheck.ASSERT(child != null, "Next node missing. gid = " + gid + "; last = " + lastChildId + 
                        "; parent= " + node.getNodeName() + "; count = " + node.getChildCount());
                
                if (gid == target) {
                        return ((NodeIterator) iter).currentAddress();
                }
                child.setGID(gid);
                if ((p= findNode(child, target, iter)) != 0) return p;
            }
        }
        return 0;
    }

    /**
     * Find a node by searching for a known ancestor in the index. If an
     * ancestor is found, it is traversed to locate the specified descendant
     * node.
     * 
     * @param lock
     * @param node
     * @return @throws
     *              IOException
     * @throws BTreeException
     */
    protected long findValue(Object lock, NodeProxy node) throws IOException,
            BTreeException {
        final DocumentImpl doc = (DocumentImpl) node.getDocument();
        final NativeBroker.NodeRef nodeRef = new NativeBroker.NodeRef(doc
                .getDocId(), node.getGID());
        // first try to find the node in the index
        final long p = findValue(nodeRef);
        if (p == KEY_NOT_FOUND) {
            // node not found in index: try to find the nearest available
            // ancestor and traverse it
            long id = node.getGID();
            long parentPointer = -1;
            do {
                id = XMLUtil.getParentId(doc, id);
                if (id < 1) {
                    SanityCheck.TRACE("Node " + node.gid + " not found.");
                    throw new BTreeException("node " + node.gid + " not found.");
                }
                NativeBroker.NodeRef parentRef = new NativeBroker.NodeRef(doc
                        .getDocId(), id);
                try {
                    parentPointer = findValue(parentRef);
                } catch (BTreeException bte) {
                }
            } while (parentPointer == KEY_NOT_FOUND);
            	
            final long firstChildId = XMLUtil.getFirstChildId(doc, id);
            final Iterator iter = new NodeIterator(lock, this, node.getDocument(),
                    parentPointer);
            final NodeImpl n = (NodeImpl) iter.next();
            n.setGID(id);
            final long address = findNode(n, node.gid, iter);
            if(address == 0) {
//            	if(LOG.isDebugEnabled())
//            		LOG.debug("Node data location not found for node " + node.gid);
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
     *                   Description of the Parameter
     * @return Description of the Return Value
     * @exception IOException
     *                        Description of the Exception
     * @exception BTreeException
     *                        Description of the Exception
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
     *                        Description of the Exception
     */
    public boolean flush() throws DBException {
        super.flush();
        dataCache.flush();
//        closeDocument();
        return true;
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
            if (p == KEY_NOT_FOUND) return null;
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
     *                   Description of the Parameter
     * @return Description of the Return Value
     */
    public Value get(NodeProxy node) {
        try {
            long p = findValue(owner, node);
            if (p == KEY_NOT_FOUND) return null;
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
     *                   Description of the Parameter
     * @return Description of the Return Value
     */
    public Value get(long p) {
        RecordPos rec = findRecord(p);
        if (rec == null) {
            SanityCheck.TRACE("object at " + StorageAddress.toString(p) + " not found.");
            return null;
        }
        short l = ByteConversion.byteToShort(rec.page.data, rec.offset);
        rec.offset += 2;
        if (ItemId.isRelocated(rec.tid)) rec.offset += 8;
        Value v;
        if (l == OVERFLOW) {
            long pnum = ByteConversion.byteToLong(rec.page.data, rec.offset);
            byte[] data = getOverflowValue(pnum);
            v = new Value(data);
        } else
            v = new Value(rec.page.data, rec.offset, l);
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

    public void removeOverflowValue(long pnum) {
        try {
            OverflowDOMPage overflow = new OverflowDOMPage(pnum);
            overflow.delete();
        } catch (IOException e) {
            LOG.error("io error while removing overflow value", e);
        }
    }

    /**
     * Retrieve the last page in the current sequence.
     * 
     * @return The currentPage value
     */
    private final DOMPage getCurrentPage() {
        long pnum = pages.get(owner);
        if (pnum < 0) {
            final DOMPage page = new DOMPage();
            pages.put(owner, page.page.getPageNum());
//            LOG.debug("new page created: " + page.page.getPageNum() + " by " + owner +
//            		"; thread: " + Thread.currentThread().getName());
            dataCache.add(page);
            return page;
        } else
            return getCurrentPage(pnum);
    }

    /**
     * Retrieve the page with page number p
     * 
     * @param p
     *                   Description of the Parameter
     * @return The currentPage value
     */
    protected final DOMPage getCurrentPage(long p) {
        DOMPage page = (DOMPage) dataCache.get(p);
        if (page == null) {
//        	LOG.debug("Loading page " + p + " from file");
            page = new DOMPage(p);
        }
        return page;
    }

    public void closeDocument() {
        pages.remove(owner);
//        SanityCheck.TRACE("current doc closed by: " + owner +
//        		"; thread: " + Thread.currentThread().getName());
    }

    /**
     * Open the file.
     * 
     * @return Description of the Return Value
     * @exception DBException
     *                        Description of the Exception
     */
    public boolean open() throws DBException {
        return super.open(FILE_FORMAT_VERSION_ID);
    }

    /**
     * Put a new key/value pair.
     * 
     * @param key
     *                   Description of the Parameter
     * @param value
     *                   Description of the Parameter
     * @return Description of the Return Value
     */
    public long put(Value key, byte[] value) throws ReadOnlyException {
        long p = add(value);
        try {
            addValue(key, p);
        } catch (IOException ioe) {
            LOG.debug(ioe);
            return -1;
        } catch (BTreeException bte) {
            LOG.debug(bte);
            return -1;
        }
        return p;
    }

    /**
     * Physically remove a node. The data of the node will be removed from
     * the page and the occupied space is freed.
     */
    public void remove(Value key) {
        try {
            long p = findValue(key);
            if (p == KEY_NOT_FOUND) return;
            remove(key, p);
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
    private void removeLink(long p) {
        RecordPos rec = findRecord(p, false);
        DOMFilePageHeader ph = rec.page.getPageHeader();
        int end = rec.offset + 8;
        System.arraycopy(rec.page.data, rec.offset + 8, rec.page.data, rec.offset - 2, rec.page.len - end);
        rec.page.len = rec.page.len - 10;
        ph.setDataLength(rec.page.len);
        rec.page.setDirty(true);
        ph.decRecordCount();
//        LOG.debug("size = " + ph.getRecordCount());
        if (rec.page.len == 0) {
            removePage(rec.page);
            rec.page = null;
        } else {
            dataCache.add(rec.page);
            //printPageContents(rec.page);
        }
    }
    
    /**
     * Physically remove a node. The data of the node will be removed from
     * the page and the occupied space is freed.
     * 
     * @param p
     */
    public void remove(long p) {
        RecordPos rec = findRecord(p);
//        LOG.debug("removing value " + rec.tid + " from " + rec.page.getPageNum());
        int startOffset = rec.offset - 2;
        DOMFilePageHeader ph = rec.page.getPageHeader();
        short l = ByteConversion.byteToShort(rec.page.data, rec.offset);
        rec.offset += 2;
        if(ItemId.isLink(rec.tid)) {
        	throw new RuntimeException("Cannot remove link ...");
        }
        if(ItemId.isRelocated(rec.tid)) {
            long backLink = ByteConversion.byteToLong(rec.page.data, rec.offset);
            removeLink(backLink);
            rec.offset += 8;
            l += 8;
        }
        if (l == OVERFLOW) {
            // remove overflow value
            long pnum = ByteConversion
                    .byteToLong(rec.page.data, rec.offset);
            rec.offset += 8;
            try {
                OverflowDOMPage overflow = new OverflowDOMPage(pnum);
                overflow.delete();
            } catch (IOException e) {
                LOG.error("io error while removing overflow page", e);
            }
            l += 8;
        }
        int end = startOffset + 4 + l;
        int len = ph.getDataLength();
        // remove old value
        System.arraycopy(rec.page.data, end, rec.page.data, startOffset, len
                - end);
        rec.page.setDirty(true);
        len = len - l - 4;
        ph.setDataLength(len);
        rec.page.len = len;
        rec.page.setDirty(true);
        ph.decRecordCount();
        if (rec.page.len == 0) {
            LOG.debug("removing page " + rec.page.getPageNum());
            removePage(rec.page);
            rec.page = null;
        } else {
        	rec.page.cleanUp();
//        	LOG.debug(debugPageContents(rec.page));
            dataCache.add(rec.page);
        }
    }
 
    /**
     * Physically remove a node. The data of the node will be removed from
     * the page and the occupied space is freed. 
     */
    public void remove(Value key, long p) {
        remove(p);
        try {
            removeValue(key);
        } catch (BTreeException e) {
            LOG.warn("btree error while removing node", e);
        } catch (IOException e) {
            LOG.warn("io error while removing node", e);
        }
    }

    /**
     * Remove the specified page. The page is added
     * to the list of free pages.
     * 
     * @param page
     */
    public void removePage(DOMPage page) {
        dataCache.remove(page);
        DOMFilePageHeader ph = page.getPageHeader();
        if (ph.getNextDataPage() > -1) {
			DOMPage next = getCurrentPage(ph.getNextDataPage());
			next.getPageHeader().setPrevDataPage(ph.getPrevDataPage());
//			LOG.debug(next.getPageNum() + ".prev = " + ph.getPrevDataPage());
			next.setDirty(true);
			dataCache.add(next);
        }
        
        if(ph.getPrevDataPage() > -1) {
            DOMPage prev = getCurrentPage(ph.getPrevDataPage());
        	prev.getPageHeader().setNextDataPage(ph.getNextDataPage());
//        	LOG.debug(prev.getPageNum() + ".next = " + ph.getNextDataPage());
			prev.setDirty(true);
			dataCache.add(prev);
        }
        
        try {
            ph.setNextDataPage(-1);
            ph.setPrevDataPage(-1);
            ph.setDataLength(0);
            ph.setNextTID((short) -1);
            ph.setRecordCount((short) 0);
            unlinkPages(page.page);
        } catch (IOException ioe) {
            LOG.warn(ioe);
        }
        if(currentDocument != null)
            currentDocument.decPageCount();
    }
    
    public void removeAll(long p) {
//    	StringBuffer debug = new StringBuffer();
//    	debug.append("Removed pages: ");
        long pnum = StorageAddress.pageFromPointer(p);
        while(-1 < pnum) {
//        	debug.append(' ').append(pnum);
            DOMPage page = getCurrentPage(pnum);
            pnum = page.getPageHeader().getNextDataPage();
            dataCache.remove(page);
            try {
            	DOMFilePageHeader ph = page.getPageHeader();
                ph.setNextDataPage(-1);
                ph.setPrevDataPage(-1);
                ph.setDataLength(0);
                ph.setNextTID((short) -1);
                ph.setRecordCount((short) 0);
                page.len = 0;
                unlinkPages(page.page);
            } catch (IOException e) {
                LOG.warn("Error while removing page: " + e.getMessage(), e);
            }
        }
//        LOG.debug(debug.toString());
    }
    
    public String debugPages(DocumentImpl doc) {
    	StringBuffer buf = new StringBuffer();
    	buf.append("Pages used by ").append(doc.getName());
    	buf.append("; docId ").append(doc.getDocId()).append(':');
    	long pnum = StorageAddress.pageFromPointer(((NodeImpl)doc.getFirstChild()).getInternalAddress());
        while(-1 < pnum) {
            DOMPage page = getCurrentPage(pnum);
            dataCache.add(page);
            buf.append(' ').append(pnum);
            pnum = page.getPageHeader().getNextDataPage();
        }
        buf.append("; Document metadata at " +StorageAddress.toString(doc.getAddress()));
        return buf.toString();
    }
    
    /**
     * Set the last page in the sequence to which nodes are currently appended.
     * 
     * @param page
     *                   The new currentPage value
     */
    private final void setCurrentPage(DOMPage page) {
        long pnum = pages.get(owner);
        if (pnum == page.page.getPageNum()) return;
//        pages.remove(owner);
//        LOG.debug("current page set: " + page.page.getPageNum() + " by " + owner.hashCode() +
//        		"; thread: " + Thread.currentThread().getName());
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
     *                   The new ownerObject value
     */
    public final void setOwnerObject(Object obj) {
//    	if(owner != obj && obj != null)
//    		LOG.debug("owner set -> " + obj.hashCode());
        owner = obj;
    }

    /**
     * Update the key/value pair.
     * 
     * @param key
     *                   Description of the Parameter
     * @param value
     *                   Description of the Parameter
     * @return Description of the Return Value
     */
    public boolean update(Value key, byte[] value) throws ReadOnlyException {
        try {
            long p = findValue(key);
            if (p == KEY_NOT_FOUND) return false;
            update(p, value);
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
     * @param key
     *                   Description of the Parameter
     * @param p
     *                   Description of the Parameter
     * @param value
     *                   Description of the Parameter
     */
    public void update(long p, byte[] value) throws ReadOnlyException {
        RecordPos rec = findRecord(p);
        short l = ByteConversion.byteToShort(rec.page.data, rec.offset);
        rec.offset += 2;
        if(ItemId.isRelocated(rec.tid))
        	rec.offset += 8;
        if (value.length < l) {
            // value is smaller than before
            throw new IllegalStateException("shrinked");
        } else if (value.length > l) {
            throw new IllegalStateException("value too long: expected: "
                    + value.length + "; got: " + l);
        } else {
            // value length unchanged
            System.arraycopy(value, 0, rec.page.data, rec.offset,
                    value.length);
        }
        rec.page.setDirty(true);
    }

    /**
     * Retrieve the string value of the specified node.
     * 
     * @param proxy
     * @return
     */
    public String getNodeValue(NodeProxy proxy, boolean addWhitespace) {
        try {
            long address = proxy.getInternalAddress();
            if (address < 0) address = findValue(this, proxy);
            if (address == BTree.KEY_NOT_FOUND) return null;
            final RecordPos rec = findRecord(address);
            SanityCheck.THROW_ASSERT(rec != null, "Node data could not be found! Page: " + 
                    StorageAddress.pageFromPointer(address) +
                    "; tid: " + StorageAddress.tidFromPointer(address));
            final ByteArrayOutputStream os = new ByteArrayOutputStream();
            getNodeValue(os, rec, true, addWhitespace);
            final byte[] data = os.toByteArray();
            String value;
            try {
                value = new String(data, "UTF-8");
            } catch (UnsupportedEncodingException e) {
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

    private void getNodeValue(ByteArrayOutputStream os, RecordPos rec,
            boolean firstCall, boolean addWhitespace) {
    	boolean foundNext = false;
    	do {
	        if (rec.offset > rec.page.getPageHeader().getDataLength()) {
	            final long nextPage = rec.page.getPageHeader().getNextDataPage();
	            if (nextPage < 0) {
	                SanityCheck.TRACE("bad link to next page");
	                return;
	            }
	            rec.page = getCurrentPage(nextPage);
	            dataCache.add(rec.page);
	            rec.offset = 2;
	        }
	        rec.tid = ByteConversion.byteToShort(rec.page.data, rec.offset - 2);
	        if(ItemId.isLink(rec.tid)) {
	        	rec.offset += 10;
	        } else
	        	foundNext = true;
    	} while(!foundNext);
        int len = ByteConversion.byteToShort(rec.page.data, rec.offset);
        rec.offset += 2;
        if(ItemId.isRelocated(rec.tid))
            rec.offset += 8;
        byte[] data = rec.page.data;
        int readOffset = rec.offset;
        if (len == OVERFLOW) {
            final long op = ByteConversion.byteToLong(data,
                    rec.offset);
            data = getOverflowValue(op);
            len = data.length;
            readOffset = 0;
            rec.offset += 8;
        }
        final short type = Signatures.getType(data[readOffset]);
        switch (type) {
        case Node.ELEMENT_NODE:
        	final int children = ByteConversion.byteToInt(data, readOffset + 1);
	        final byte attrSizeType = (byte) ((data[readOffset] & 0x0C) >> 0x2);
	        final short attributes = (short) Signatures.read(attrSizeType,
	        		data, readOffset + 5);
            final boolean extraWhitespace = addWhitespace && children - attributes > 1;
	        rec.offset += len + 2;
	        for (int i = 0; i < children; i++) {
	        	getNodeValue(os, rec, false, addWhitespace);
	        	if (extraWhitespace)
	        		os.write((byte) ' ');
	        }
	        return;
        case Node.TEXT_NODE:
            os.write(data, readOffset + 1, len - 1);
            break;
        case Node.ATTRIBUTE_NODE:
            // use attribute value if the context node is an attribute, i.e.
            // if this is the first call to the method
            if (firstCall) {
                final byte idSizeType = (byte) (data[readOffset] & 0x3);
                final boolean hasNamespace = (data[readOffset] & 0x10) == 0x10;
                int next = Signatures.getLength(idSizeType) + 1;
                if (hasNamespace) {
                    next += 2; // skip namespace id
                    final short prefixLen = ByteConversion.byteToShort(data,
                            readOffset + next);
                    next += prefixLen + 2; // skip prefix
                }
                os.write(rec.page.data, readOffset + next, len - next);
            }
            break;
        }
        if(len != OVERFLOW)
        	rec.offset += len + 2;
    }

    protected RecordPos findRecord(long p) {
        return findRecord(p, true);
    }
    
        /**
         * Find a record within the page or the pages linked to it.
         * 
         * @param p
         * @return
         */
        protected RecordPos findRecord(long p, boolean skipLinks) {
            long pageNr = StorageAddress.pageFromPointer(p);
            short targetId = StorageAddress.tidFromPointer(p);
            DOMPage page;
            int pos;
            short currentId, vlen;
            RecordPos rec;
            while (pageNr > -1) {
                page = getCurrentPage(pageNr);
                dataCache.add(page);
                rec = page.findRecord(targetId);
                if(rec == null) {
                	pageNr = page.getPageHeader().getNextDataPage();
                	if (pageNr == page.getPageNum()) {
                		SanityCheck.TRACE("circular link to next page on " + pageNr);
                		return null;
                	}
                	SanityCheck.TRACE(
                		owner.toString()
						+ ": tid "
						+ targetId
						+ " not found on "
						+ page.page.getPageInfo()
						+ ". Loading "
						+ pageNr + "; contents: " + debugPageContents(page));
                } else if(rec.isLink) {
                	if(!skipLinks)
                		return rec;
                	long forwardLink = ByteConversion.byteToLong(page.data,
                			rec.offset);
//                	LOG.debug("following link on " + pageNr +
//                			" to page "
//                			+ StorageAddress.pageFromPointer(forwardLink)
//							+ "; tid="
//							+ StorageAddress.tidFromPointer(forwardLink));
                	// load the link page
                	pageNr = StorageAddress.pageFromPointer(forwardLink);
                	targetId = StorageAddress.tidFromPointer(forwardLink);
                } else {
                	return rec;
                }
            }
            return null;
        }

    protected final static class DOMFilePageHeader extends BTreePageHeader {

        protected int dataLen = 0;

        protected long nextDataPage = -1;

        protected long prevDataPage = -1;

        protected short tid = -1;

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
        	if(tid > ItemId.MAX_ID)
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
            ph = (DOMFilePageHeader)page.getPageHeader();
//         LOG.debug("Created new page: " + page.getPageNum());
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

        public RecordPos findRecord(short targetId) {
        	final int dlen = ph.getDataLength();
        	short currentId;
        	short vlen;
        	RecordPos rec = null;
        	byte flags;
            for (int pos = 0; pos < dlen;) {
//            	currentId = (short) ( ( data[pos] & 0xff ) + ( ( data[pos + 1] & 0xff ) << 8 ) );
                currentId = ByteConversion.byteToShort(data, pos);
                flags = ItemId.getFlags(currentId);
                if (ItemId.matches(currentId, targetId)) {
                	if ((flags & ItemId.LINK_FLAG) != 0) {
                    	rec = new RecordPos(pos + 2, this, currentId);
                    	rec.isLink = true;
                    } else {
                    	rec = new RecordPos(pos + 2, this, currentId);
                    }
                	break;
                } else if ((flags & ItemId.LINK_FLAG) != 0){
                	pos += 10;
                } else {
//                	vlen = (short) ( ( data[pos + 2] & 0xff ) + ( ( data[pos + 3] & 0xff ) << 8 ) );
                    vlen = ByteConversion.byteToShort(data, pos + 2);
                    if(vlen < 0) {
                    	LOG.warn("page = " + page.getPageNum() + "; pos = " + pos + "; vlen = " + vlen + "; tid = " + currentId +
                    			"; target = " + targetId);
                    }
                    if ((flags & ItemId.RELOCATED_FLAG) != 0) {
                    	pos += vlen + 12;
                    } else {
                    	pos += vlen + 4;
                    }
                    if(vlen == OVERFLOW)
                    	pos += 8;
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
            if (refCount < Cacheable.MAX_REF) ++refCount;
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
                    LOG
                            .debug("page " + page.getPageNum()
                                    + " data length == 0");
                    return;
                }
            } catch (IOException ioe) {
                LOG.debug(ioe);
                ioe.printStackTrace();
            }
            saved = true;
        }
        
        public void write() {
            if (page == null) return;
            try {
                if (!ph.isDirty()) return;
                ph.setDataLength(len);
                writeValue(page, data);
                setDirty(false);
            } catch (IOException ioe) {
                LOG.error(ioe);
            }
        }

        public String dumpPage() {
        	return "Contents of page " + page.getPageNum() + ": " +
				hexDump(data);
        }
        
        public boolean sync() {
            if (isDirty()) {
                write();
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
         * Walk through the page after records have been removed.
         * Set the tid counter to the next spare id that can be used for following
         * insertions. 
         */
        public void cleanUp() {
        	final int dlen = ph.getDataLength();
        	short currentId, vlen, tid;
        	short maxTID = 0;
        	for (int pos = 0; pos < dlen; ) {
        		currentId = ByteConversion.byteToShort(data, pos);
        		tid = ItemId.getId(currentId);
        		if(tid > ItemId.MAX_ID) {
        			LOG.debug(debugPageContents(this));
        			throw new RuntimeException("TID overflow in page " + getPageNum());
        		}
        		if(tid > maxTID)
        			maxTID = tid;
        		if (ItemId.isLink(currentId)) {
        			pos += 10;
        		} else {
        			vlen = ByteConversion.byteToShort(data, pos + 2);
        			if (ItemId.isRelocated(currentId)) {
        				pos += vlen == OVERFLOW ? 20 : vlen + 12;
        			} else
        				pos += vlen == OVERFLOW ? 12 : vlen + 4;
        		}
        	}
        	ph.setNextTID(maxTID);
        }
    }

    /**
     * This represents an overflow page. Overflow pages are created if
     * the node data exceeds the size of one page. An overflow page is a
     * sequence of DOMPages.
     *  
     * @author wolf
     *
     */
    protected final class OverflowDOMPage {

        Page firstPage = null;

        public OverflowDOMPage() {
        	LOG.debug("Creating overflow page");
            firstPage = createNewPage();
        }

        public OverflowDOMPage(long first) throws IOException {
            firstPage = getPage(first);
        }

        public void write(byte[] data) {
            try {
                int remaining = data.length;
                int chunkSize = fileHeader.getWorkSize();
                Page page = firstPage, next = null;
                int chunk, pos = 0;
                Value value;
                while (remaining > 0) {
                    chunkSize = remaining > fileHeader.getWorkSize() ? fileHeader
                            .getWorkSize()
                            : remaining;
                    value = new Value(data, pos, chunkSize);
                    remaining -= chunkSize;
                    if (remaining > 0) {
                        next = createNewPage();
                        page.getPageHeader().setNextPage(next.getPageNum());
                    } else
                        page.getPageHeader().setNextPage(-1);
                    writeValue(page, value);
                    pos += chunkSize;
                    page = next;
                    next = null;
                }
            } catch (IOException e) {
                LOG.error("io error while writing overflow page", e);
            }
        }

        public byte[] read() {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            Page page = firstPage;
            byte[] chunk;
            long np;
            int count = 0;
            while (page != null) {
                try {
                    chunk = page.read();
                    os.write(chunk);
                    np = page.getPageHeader().getNextPage();
                    page = np > -1 ? getPage(np) : null;
                } catch (IOException e) {
                    LOG.error("io error while loading overflow page "
                            + firstPage.getPageNum() + "; read: " + count, e);
                    break;
                }
                ++count;
            }
            return os.toByteArray();
        }

        public void delete() throws IOException {
            Page page = firstPage;
            long np;
            while (page != null) {
                page.read();
                LOG.debug("removing overflow page " + page.getPageNum());
                np = page.getPageHeader().getNextPage();
                unlinkPages(page);
                page = np > -1 ? getPage(np) : null;
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

        int mode = VALUES;

        ArrayList values = new ArrayList();

        public FindCallback(int mode) {
            this.mode = mode;
        }

        public ArrayList getValues() {
            return values;
        }

        public boolean indexInfo(Value value, long pointer) 
        {
            switch (mode) {
            case VALUES:
                RecordPos rec = findRecord(pointer);
                short l = ByteConversion.byteToShort(rec.page.data, rec.offset);
                int dataStart = rec.offset + 2;
                //int l = (int) VariableByteCoding.decode( page.data,
                // offset );
                //int dataStart = VariableByteCoding.getSize( l );
                values.add(new Value(rec.page.data, dataStart, l));
                return true;
            case KEYS:
                values.add(value);
                return true;
            }
            return false;
        }
    }

    private final class RangeCallback implements BTreeCallback {

        ArrayList values = new ArrayList();

        public RangeCallback() {
        }

        public ArrayList getValues() {
            return values;
        }

        public boolean indexInfo(Value value, long pointer) 
        throws TerminatedException {
            RecordPos rec = findRecord(pointer);
            short l = ByteConversion.byteToShort(rec.page.data, rec.offset);
            int dataStart = rec.offset + 2;
            //int l = (int) VariableByteCoding.decode( page.data, offset );
            //int dataStart = VariableByteCoding.getSize( l ) + offset;
            values.add(new Value(rec.page.data, dataStart, l));
            return true;
        }
    }

    protected final static class RecordPos {

    	DOMPage page = null;
        int offset = -1;
        short tid = 0;
        boolean isLink = false;

        public RecordPos(int offset, DOMPage page, short tid) {
            this.offset = offset;
            this.page = page;
            this.tid = tid;
        }
    }
}