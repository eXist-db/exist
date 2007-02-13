/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2007 The eXist team
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
 *  along with this program; if not, write to the Free Software Foundation
 *  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.storage.dom;

import org.apache.log4j.Logger;
import org.exist.storage.StorageAddress;
import org.exist.storage.btree.Paged;
import org.exist.storage.btree.Value;
import org.exist.storage.btree.BTree;
import org.exist.storage.btree.BTreeException;
import org.exist.storage.lock.Lock;
import org.exist.util.ByteConversion;
import org.exist.util.LockException;
import org.exist.util.sanity.SanityCheck;
import org.exist.dom.StoredNode;
import org.exist.dom.NodeProxy;

import java.io.IOException;

/**
 * An iterator that walks through the raw node data items in a document. The class
 * keeps reading data items from the document's sequence of data pages until it encounters
 * the end of the document. Each returned value contains the data of one node in the
 * document.
 */
public class RawNodeIterator {

    private final static Logger LOG = Logger.getLogger(RawNodeIterator.class);

	private DOMFile db = null;
	private int offset;
	private short lastTID = ItemId.UNKNOWN_ID;
	private DOMFile.DOMPage p = null;
	private long page;
	private Object lockKey;

    /**
     * Construct the iterator. The iterator will be positioned before the specified
     * start node.
     *
     * @param lockKey the owner object used to acquire a lock on the underlying data file (usually a DBBroker)
     * @param db the underlying data file
     * @param node the start node where the iterator will be positioned.
     * @throws IOException
     */
    public RawNodeIterator(Object lockKey, DOMFile db, StoredNode node) throws IOException {
        this.db = db;
		this.lockKey = (lockKey == null ? this : lockKey);
        seek(node);
    }

    /**
     * Construct the iterator. The iterator will be positioned before the specified
     * start node.
     *
     * @param lockKey the owner object used to acquire a lock on the underlying data file (usually a DBBroker)
     * @param db the underlying data file
     * @param proxy the start node where the iterator will be positioned.
     * @throws IOException
     */
    public RawNodeIterator(Object lockKey, DOMFile db, NodeProxy proxy) throws IOException {
        this.db = db;
		this.lockKey = (lockKey == null ? this : lockKey);
        seek(proxy);
    }

    /**
     * Reposition the iterator to the start of the specified node.
     *
     * @param node the start node where the iterator will be positioned.
     * @throws IOException
     */
    public void seek(StoredNode node) throws IOException {
        Lock lock = db.getLock();
        try {
            lock.acquire(Lock.READ_LOCK);
            RecordPos rec = null;
            if (node.getInternalAddress() != StoredNode.UNKNOWN_NODE_IMPL_ADDRESS)
                rec = db.findRecord(node.getInternalAddress());
            if (rec == null) {
                try {
                    long addr = db.findValue(lockKey, new NodeProxy(node));
                    if (addr == BTree.KEY_NOT_FOUND)
                        throw new IOException("Node not found.");
                    rec = db.findRecord(addr);
                } catch (BTreeException e) {
                    throw new IOException("Node not found: " + e.getMessage());
                }
            }
            page = rec.getPage().getPageNum();
            offset = rec.offset - 2;
            p = rec.getPage();
        } catch (LockException e) {
            throw new IOException("Exception while scanning document: " + e.getMessage());
        } finally {
            lock.release(Lock.READ_LOCK);
        }
    }

    /**
     * Reposition the iterator to the start of the specified node.
     *
     * @param proxy the start node where the iterator will be positioned.
     * @throws IOException
     */
    public void seek(NodeProxy proxy) throws IOException {
        Lock lock = db.getLock();
        try {
            lock.acquire(Lock.READ_LOCK);
            RecordPos rec = null;
            if (proxy.getInternalAddress() != StoredNode.UNKNOWN_NODE_IMPL_ADDRESS)
                rec = db.findRecord(proxy.getInternalAddress());
            if (rec == null) {
                try {
                    long addr = db.findValue(lockKey, proxy);
                    if (addr == BTree.KEY_NOT_FOUND)
                        throw new IOException("Node not found.");
                    rec = db.findRecord(addr);
                } catch (BTreeException e) {
                    throw new IOException("Node not found: " + e.getMessage());
                }
            }
            page = rec.getPage().getPageNum();
            offset = rec.offset - 2;
            p = rec.getPage();
        } catch (LockException e) {
            throw new IOException("Exception while scanning document: " + e.getMessage());
        } finally {
            lock.release(Lock.READ_LOCK);
        }
    }

    /**
	 *  Returns the raw data of the next node in document order.
     * @return the raw data of the node
     */
	public Value next() {
        Value nextValue = null;
        Lock lock = db.getLock();
		try {
			try {
				lock.acquire(Lock.READ_LOCK);
			} catch (LockException e) {
				LOG.warn("Failed to acquire read lock on " + db.getFile().getName());
				return null;
			}
			db.setOwnerObject(lockKey);
            long backLink = 0;
            do {
                DOMFile.DOMFilePageHeader ph = p.getPageHeader();
                // next value larger than length of the current page?
                if (offset >= ph.getDataLength()) {
                    // load next page in chain
                    long nextPage = ph.getNextDataPage();
                    if (nextPage == Paged.Page.NO_PAGE) {
                        SanityCheck.TRACE("bad link to next " + p.page.getPageInfo() + "; previous: " +
                                ph.getPrevDataPage() + "; offset = " + offset + "; lastTID = " + lastTID);
                        return null;
                    }
                    page = nextPage;
                    p = db.getCurrentPage(nextPage);
                    //LOG.debug(" -> " + nextPage + "; len = " + p.len + "; " + p.page.getPageInfo());
                    db.addToBuffer(p);
                    offset = 0;
                }
                // extract the tid
                lastTID = ByteConversion.byteToShort(p.data, offset);
                offset += DOMFile.LENGTH_TID;

                //	check if this is just a link to a relocated node
                if(ItemId.isLink(lastTID)) {
                    // skip this
                    offset += DOMFile.LENGTH_FORWARD_LOCATION;
                    //System.out.println("skipping link on p " + page + " -> " +
                    //StorageAddress.pageFromPointer(link));
                    //continue the iteration
                    continue;
                }

                // read data length
                short vlen = ByteConversion.byteToShort(p.data, offset);
                offset += DOMFile.LENGTH_DATA_LENGTH;
                if (vlen < 0) {
                    LOG.warn("Got negative length" + vlen + " at offset " + offset + "!!!");
                    LOG.debug(db.debugPageContents(p));
                    //TODO : throw an exception right now ?
                }

                if(ItemId.isRelocated(lastTID)) {
                    // found a relocated node. Read the original address
                    backLink = ByteConversion.byteToLong(p.data, offset);
                    offset += DOMFile.LENGTH_ORIGINAL_LOCATION;
                }

                //	overflow page? load the overflow value
                if (vlen == DOMFile.OVERFLOW) {
                    vlen = DOMFile.LENGTH_OVERFLOW_LOCATION;
                    final long overflow = ByteConversion.byteToLong(p.data, offset);
                    offset += DOMFile.LENGTH_OVERFLOW_LOCATION;
                    try {
                        final byte[] odata = db.getOverflowValue(overflow);
                        nextValue = new Value(odata);
                    } catch(Exception e) {
                        LOG.warn("Exception while loading overflow value: " + e.getMessage() +
                                "; originating page: " + p.page.getPageInfo());
                    }
                    // normal node
                } else {
                    try {
                        nextValue = new Value(p.data, offset, vlen);
                        offset += vlen;
                    } catch(Exception e) {
                        LOG.warn("Error while deserializing node: " + e.getMessage(), e);
                        LOG.warn("Reading from offset: " + offset + "; len = " + vlen);
                        LOG.debug(db.debugPageContents(p));
                        throw new RuntimeException(e);
                    }
                }
                if(nextValue == null) {
                    LOG.warn("illegal node on page " + p.getPageNum() + "; tid = " + ItemId.getId(lastTID) +
                            "; next = " + p.getPageHeader().getNextDataPage() + "; prev = " +
                            p.getPageHeader().getPrevDataPage() + "; offset = " + (offset - vlen) +
                            "; len = " + p.getPageHeader().getDataLength());
                    //LOG.debug(db.debugPageContents(p));
                    //LOG.debug(p.dumpPage());
                    return null;
                }
                if(ItemId.isRelocated(lastTID)) {
                    nextValue.setAddress(backLink);
                } else {
                    nextValue.setAddress(
                            StorageAddress.createPointer((int) page, ItemId.getId(lastTID))
                    );
                }
                //YES ! needed because of the continue statement above
            } while (nextValue == null);
			return nextValue;
		} finally {
			lock.release(Lock.READ_LOCK);
		}
	}

    public void closeDocument() {
        db.closeDocument();
    }

    /**
     * Returns the internal virtual storage address of the node at the cursor's current
     * position.
     *
     * @return internal virtual storage address of the node
     */
    public long currentAddress() {
		return StorageAddress.createPointer((int) page, ItemId.getId(lastTID));
	}
}
