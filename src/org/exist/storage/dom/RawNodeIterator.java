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
import org.exist.dom.NodeHandle;
import org.exist.dom.NodeProxy;
import org.exist.storage.DBBroker;
import org.exist.storage.StorageAddress;
import org.exist.storage.btree.BTree;
import org.exist.storage.btree.BTreeException;
import org.exist.storage.btree.Paged;
import org.exist.storage.btree.Value;
import org.exist.storage.lock.Lock;
import org.exist.util.ByteConversion;
import org.exist.util.LockException;
import org.exist.util.sanity.SanityCheck;

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
    private short lastTupleID = ItemId.UNKNOWN_ID;
    private DOMFile.DOMPage page = null;
    private long pageNum;
    private DBBroker broker;

    /**
     * Construct the iterator. The iterator will be positioned before the specified
     * start node.
     *
     * @param broker the owner object used to acquire a lock on the underlying data file (usually a DBBroker)
     * @param db the underlying data file
     * @param node the start node where the iterator will be positioned.
     * @throws IOException
     */
    public RawNodeIterator(DBBroker broker, DOMFile db, NodeHandle node) throws IOException {
        this.db = db;
        this.broker = broker;
        seek(node);
    }

    /**
     * Reposition the iterator to the start of the specified node.
     *
     * @param node the start node where the iterator will be positioned.
     * @throws IOException
     */
    public void seek(NodeHandle node) throws IOException {
        final Lock lock = db.getLock();
        try {
            lock.acquire(Lock.READ_LOCK);
            RecordPos rec = null;
            if (StorageAddress.hasAddress(node.getInternalAddress()))
                {rec = db.findRecord(node.getInternalAddress());}
            if (rec == null) {
                try {
                    final long address = db.findValue(broker, new NodeProxy(node));
                    if (address == BTree.KEY_NOT_FOUND)
                        {throw new IOException("Node not found.");}
                    rec = db.findRecord(address);
                } catch (final BTreeException e) {
                    throw new IOException("Node not found: " + e.getMessage());
                }
            }
            pageNum = rec.getPage().getPageNum();
            //Position the stream at the very beginning of the record
            offset = rec.offset - DOMFile.LENGTH_TID;
            page = rec.getPage();
        } catch (final LockException e) {
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
        final Lock lock = db.getLock();
        try {
            try {
                lock.acquire(Lock.READ_LOCK);
            } catch (final LockException e) {
                LOG.error("Failed to acquire read lock on " + db.getFile().getName());
                //TODO : throw exception here ? -pb
                return null;
            }
            db.setOwnerObject(broker);
            long backLink = 0;
            do {
                final DOMFile.DOMFilePageHeader pageHeader = page.getPageHeader();
                //Next value larger than length of the current page?
                if (offset >= pageHeader.getDataLength()) {
                    //Load next page in chain
                    long nextPage = pageHeader.getNextDataPage();
                    if (nextPage == Paged.Page.NO_PAGE) {
                        SanityCheck.TRACE("Bad link to next page " + page.page.getPageInfo() +
                            "; previous: " + pageHeader.getPreviousDataPage() +
                            "; offset = " + offset + "; lastTupleID = " + lastTupleID);
                        //TODO : throw exception here ? -pb
                        return null;
                    }
                    pageNum = nextPage;
                    page = db.getDOMPage(nextPage);
                    db.addToBuffer(page);
                    offset = 0;
                }
                //Extract the tuple id
                lastTupleID = ByteConversion.byteToShort(page.data, offset);
                offset += DOMFile.LENGTH_TID;
                //Check if this is just a link to a relocated node
                if(ItemId.isLink(lastTupleID)) {
                    //Skip this
                    offset += DOMFile.LENGTH_FORWARD_LOCATION;
                    continue;
                }
                //Read data length
                short valueLength = ByteConversion.byteToShort(page.data, offset);
                offset += DOMFile.LENGTH_DATA_LENGTH;
                if (valueLength < 0) {
                    LOG.error("Got negative length" + valueLength + " at offset " + offset + "!!!");
                    LOG.debug(db.debugPageContents(page));
                    //TODO : throw an exception right now ?
                }
                if (ItemId.isRelocated(lastTupleID)) {
                    // found a relocated node. Read the original address
                    backLink = ByteConversion.byteToLong(page.data, offset);
                    offset += DOMFile.LENGTH_ORIGINAL_LOCATION;
                }
                //Overflow page? load the overflow value
                if (valueLength == DOMFile.OVERFLOW) {
                    valueLength = DOMFile.LENGTH_OVERFLOW_LOCATION;
                    final long overflow = ByteConversion.byteToLong(page.data, offset);
                    offset += DOMFile.LENGTH_OVERFLOW_LOCATION;
                    try {
                        final byte[] odata = db.getOverflowValue(overflow);
                        nextValue = new Value(odata);
                    } catch(final Exception e) {
                        LOG.error("Exception while loading overflow value: " + e.getMessage() +
                            "; originating page: " + page.page.getPageInfo());
                    }
                    // normal node
                } else {
                    try {
                        nextValue = new Value(page.data, offset, valueLength);
                        offset += valueLength;
                    } catch(final Exception e) {
                        LOG.error("Error while deserializing node: " + e.getMessage(), e);
                        LOG.error("Reading from offset: " + offset + "; len = " + valueLength);
                        LOG.debug(db.debugPageContents(page));
                        throw new RuntimeException(e);
                    }
                }
                if (nextValue == null) {
                    LOG.error("illegal node on page " + page.getPageNum() +
                        "; tupleID = " + ItemId.getId(lastTupleID) +
                        "; next = " + page.getPageHeader().getNextDataPage() +
                        "; prev = " + page.getPageHeader().getPreviousDataPage() +
                        "; offset = " + (offset - valueLength) +
                        "; len = " + page.getPageHeader().getDataLength());
                    //TODO : throw exception here ? -pb
                    return null;
                }
                if (ItemId.isRelocated(lastTupleID)) {
                    nextValue.setAddress(backLink);
                } else {
                    nextValue.setAddress(StorageAddress.createPointer((int) pageNum, 
                        ItemId.getId(lastTupleID))
                    );
                }
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
        return StorageAddress.createPointer((int) pageNum, ItemId.getId(lastTupleID));
    }
}
