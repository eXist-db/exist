package org.exist.storage.dom;

import org.apache.log4j.Logger;
import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.StoredNode;
import org.exist.storage.DBBroker;
import org.exist.storage.StorageAddress;
import org.exist.storage.btree.BTree;
import org.exist.storage.btree.BTreeException;
import org.exist.storage.btree.Paged.Page;
import org.exist.storage.lock.Lock;
import org.exist.util.ByteConversion;
import org.exist.util.LockException;
import org.exist.util.sanity.SanityCheck;

import java.io.IOException;
import java.util.Iterator;

/**
 * Class NodeIterator is used to iterate over nodes in the DOM storage.
 * This implementation locks the DOM file to read the node and unlocks
 * it afterwards. It is thus safer than DOMFileIterator, since the node's
 * value will not change. 
 * 
 * @author wolf
 */
public final class NodeIterator implements Iterator<StoredNode> {

    private final static Logger LOG = Logger.getLogger(NodeIterator.class);

    private DOMFile db = null;
    private StoredNode node = null;
    private DocumentImpl doc = null;
    private int offset;
    private short lastTupleID = ItemId.UNKNOWN_ID;
    private DOMFile.DOMPage page = null;
    private long pageNum;
    private long startAddress = StoredNode.UNKNOWN_NODE_IMPL_ADDRESS;
    private DBBroker broker;
    private boolean useNodePool = false;

    public NodeIterator(DBBroker broker, DOMFile db, StoredNode node, boolean poolable)
            throws BTreeException, IOException {
        this.db = db;
        this.doc = (DocumentImpl)node.getOwnerDocument();
        this.useNodePool = poolable;
        this.node = node;
        this.broker = broker;
    }

    /**
     *  Returns the internal virtual address of the node at the iterator's
     * current position.
     *
     *@return    The currentAddress value
     */
    public long currentAddress() {
        return StorageAddress.createPointer((int) pageNum, ItemId.getId(lastTupleID));
    }

    /**
     *  Are there more nodes to be read?
     *
     *@return <code>true</code> if there is at least one more node to read
     */
    public boolean hasNext() {
        final Lock lock = db.getLock();
        try {
            try {
                lock.acquire(Lock.READ_LOCK);
            } catch (final LockException e) {
                LOG.warn("Failed to acquire read lock on " + db.getFile().getName());
                //TODO : throw exception here ? -pb
                return false;
            }
            db.setOwnerObject(broker);
            if (gotoNextPosition()) {
                db.getPageBuffer().add(page);
                final DOMFile.DOMFilePageHeader pageHeader = page.getPageHeader();
                if (offset < pageHeader.getDataLength())
                    {return true;}
                else if (pageHeader.getNextDataPage() == Page.NO_PAGE)
                    {return false;}
                else
                    //Mmmmh... strange -pb
                    {return true;}
            }
        } catch (final BTreeException e) {
            LOG.warn(e);
            //TODO : throw exception here ? -pb
        } catch (final IOException e) {
            LOG.warn(e);
            //TODO : throw exception here ? -pb
        } finally {
            lock.release(Lock.READ_LOCK);
        }
        return false;
    }

    /**
     *  Returns the next node in document order. 
     */
    public StoredNode next() {
        final Lock lock = db.getLock();
        try {
            try {
                lock.acquire(Lock.READ_LOCK);
            } catch (final LockException e) {
                LOG.warn("Failed to acquire read lock on " + db.getFile().getName());
                //TODO : throw exception here ? -pb
                return null;
            }
            db.setOwnerObject(broker);
            StoredNode nextNode = null;
            if (gotoNextPosition()) {
                long backLink = 0;
                do {
                    final DOMFile.DOMFilePageHeader pageHeader = page.getPageHeader();
                    //Next value larger than length of the current page?
                    if (offset >= pageHeader.getDataLength()) {
                        //Load next page in chain
                        long nextPageNum = pageHeader.getNextDataPage();
                        if (nextPageNum == Page.NO_PAGE) {
                            SanityCheck.TRACE("bad link to next " + page.page.getPageInfo() +
                                "; previous: " + pageHeader.getPreviousDataPage() +
                                "; offset = " + offset + "; lastTupleID = " + lastTupleID);
                            System.out.println(db.debugPageContents(page));
                            //TODO : throw exception here ? -pb
                            return null;
                        }
                        pageNum = nextPageNum;
                        page = db.getDOMPage(nextPageNum);
                        db.addToBuffer(page);
                        offset = 0;
                    }
                    //Extract the tuple ID
                    lastTupleID = ByteConversion.byteToShort(page.data, offset);
                    offset += DOMFile.LENGTH_TID;
                    //Check if this is just a link to a relocated node
                    if(ItemId.isLink(lastTupleID)) {
                        //Skip this
                        offset += DOMFile.LENGTH_FORWARD_LOCATION;
                        //Continue the iteration
                        continue;
                    }
                    //Read data length
                    short vlen = ByteConversion.byteToShort(page.data, offset);
                    offset += DOMFile.LENGTH_DATA_LENGTH;
                    if (vlen < 0) {
                        LOG.error("Got negative length" + vlen + " at offset " + offset + "!!!");
                        LOG.debug(db.debugPageContents(page));
                        //TODO : throw an exception right now ?
                    }
                    if(ItemId.isRelocated(lastTupleID)) {
                        //Found a relocated node. Read the original address
                        backLink = ByteConversion.byteToLong(page.data, offset);
                        offset += DOMFile.LENGTH_ORIGINAL_LOCATION;
                    }
                    //Overflow page? Load the overflow value
                    if (vlen == DOMFile.OVERFLOW) {
                        vlen = DOMFile.LENGTH_OVERFLOW_LOCATION;
                        final long overflow = ByteConversion.byteToLong(page.data, offset);
                        offset += DOMFile.LENGTH_OVERFLOW_LOCATION;
                        try {
                            final byte[] overflowValue = db.getOverflowValue(overflow);
                            nextNode = StoredNode.deserialize(overflowValue, 0, overflowValue.length,
                                doc, useNodePool);
                        } catch(final Exception e) {
                            LOG.warn("Exception while loading overflow value: " + e.getMessage() +
                                "; originating page: " + page.page.getPageInfo());
                            //TODO : rethrow exception ? -pb
                        }
                    //Normal node
                    } else {
                        try {
                            nextNode = StoredNode.deserialize(page.data, offset, vlen, doc, useNodePool);
                            offset += vlen;
                        } catch(final Exception e) {
                            LOG.error("Error while deserializing node: " + e.getMessage(), e);
                            LOG.error("Reading from offset: " + offset + "; len = " + vlen);
                            LOG.debug(db.debugPageContents(page));
                            System.out.println(db.debugPageContents(page));
                            throw new RuntimeException(e);
                        }
                    }
                    if (nextNode == null) {
                        LOG.error("illegal node on page " + page.getPageNum() +
                            "; tid = " + ItemId.getId(lastTupleID) +
                            "; next = " + page.getPageHeader().getNextDataPage() +
                            "; prev = " + page.getPageHeader().getPreviousDataPage() +
                            "; offset = " + (offset - vlen) +
                            "; len = " + page.getPageHeader().getDataLength());
                        System.out.println(db.debugPageContents(page));
                        //TODO : throw an exception here ? -pb
                        return null;
                    }
                    if (ItemId.isRelocated(lastTupleID)) {
                        nextNode.setInternalAddress(backLink);
                    } else {
                        nextNode.setInternalAddress(StorageAddress.createPointer((int) pageNum, 
                            ItemId.getId(lastTupleID)));
                    }
                    nextNode.setOwnerDocument(doc);
                } while (nextNode == null);
            }
            return nextNode;
        } catch (final BTreeException e) {
            LOG.error(e.getMessage(), e);
            //TODO : re-throw exception ? -pb
        } catch (final IOException e) {
            LOG.error(e.getMessage(), e);
            //TODO : re-throw exception ? -pb
        } finally {
            lock.release(Lock.READ_LOCK);
        }
        return null;
    }

    private boolean gotoNextPosition() throws BTreeException, IOException {
        //Position the iterator at the start of the first value
        if (node != null) {
            RecordPos rec = null;
            if (StorageAddress.hasAddress(node.getInternalAddress()))
                {rec = db.findRecord(node.getInternalAddress());}
            if (rec == null) {
                final long addr = db.findValue(broker, new NodeProxy(node));
                if (addr == BTree.KEY_NOT_FOUND)
                    {return false;}
                rec = db.findRecord(addr);
            }
            pageNum = rec.getPage().getPageNum();
            page = rec.getPage();
            //Position the stream at the very beginning of the record
            offset = rec.offset - DOMFile.LENGTH_TID;
            node = null;
            return true;
        } else if (StorageAddress.hasAddress(startAddress)) {
            final RecordPos rec = db.findRecord(startAddress);
            if(rec == null)
                {throw new IOException("Node not found at specified address.");}
            pageNum = rec.getPage().getPageNum();
            //Position the stream at the very beginning of the record
            offset = rec.offset - DOMFile.LENGTH_TID;
            page = rec.getPage();
            startAddress = StoredNode.UNKNOWN_NODE_IMPL_ADDRESS;
            return true;
        } else if (pageNum != Page.NO_PAGE) {
            page = db.getDOMPage(pageNum);
            db.addToBuffer(page);
            return true;
        }
        return false;
    }

    /**
     * Remove the current node. This implementation just
     * decrements the node count. It does not actually remove
     * the node's value, but removes a page if
     * node count == 0. Use this method only if you want to
     * delete an entire document, not to remove a single node.
     */
    public void remove() {
        throw new RuntimeException("remove() method not implemented");
    }

    /**
     *  Reposition the iterator at the address of the proxy node.
     *
     *@param  node  The new to value
     */
    public void setTo(StoredNode node) {
        if (StorageAddress.hasAddress(node.getInternalAddress())) {
            startAddress = node.getInternalAddress();
        } else {
            this.node = node;
        }
    }

    /**
     *  Reposition the iterate at a given address.
     *
     *@param  address  The new to value
     */
    public void setTo(long address) {
        this.startAddress = address;
    }
}