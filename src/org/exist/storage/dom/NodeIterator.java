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
public final class NodeIterator implements Iterator {

	private final static Logger LOG = Logger.getLogger(NodeIterator.class);

	private DOMFile db = null;
	private StoredNode node = null;
	private DocumentImpl doc = null;
	private int offset;
	private short lastTID = ItemId.UNKNOWN_ID;
	private DOMFile.DOMPage p = null;
	private long page;
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
		return StorageAddress.createPointer((int) page, ItemId.getId(lastTID));
	}

	/**
	 *  Are there more nodes to be read?
	 *
	 *@return    Description of the Return Value
	 */
	public boolean hasNext() {
		Lock lock = db.getLock();
		try {
			try {
				lock.acquire(Lock.READ_LOCK);
			} catch (LockException e) {
				LOG.warn("Failed to acquire read lock on " + db.getFile().getName());
				return false;
			}
			db.setOwnerObject(broker);
			if(gotoNextPosition()) {
				db.getPageBuffer().add(p);
				final DOMFile.DOMFilePageHeader ph = p.getPageHeader();
				if (offset < ph.getDataLength())
					return true;
				else if (ph.getNextDataPage() == Page.NO_PAGE)
					return false;
				else
					return true;
			}
		} catch (BTreeException e) {
			LOG.warn(e);
		} catch (IOException e) {
			LOG.warn(e);
		} finally {
			lock.release(Lock.READ_LOCK);
		}
		return false;
	}

	/**
	 *  Returns the next node in document order. 
	 */
	public Object next() {
		Lock lock = db.getLock();
		try {
			try {
				lock.acquire(Lock.READ_LOCK);
			} catch (LockException e) {
				LOG.warn("Failed to acquire read lock on " + db.getFile().getName());
				return null;
			}
			db.setOwnerObject(broker);
			StoredNode nextNode = null;
			if(gotoNextPosition()) {
				long backLink = 0;
			    do {
					DOMFile.DOMFilePageHeader ph = p.getPageHeader();
					// next value larger than length of the current page?
					if (offset >= ph.getDataLength()) {
						// load next page in chain
						long nextPage = ph.getNextDataPage();
						if (nextPage == Page.NO_PAGE) {
                            SanityCheck.TRACE("bad link to next " + p.page.getPageInfo() + "; previous: " +
									ph.getPrevDataPage() + "; offset = " + offset + "; lastTID = " + lastTID);
    					    System.out.println(db.debugPageContents(p));
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
					if(vlen == DOMFile.OVERFLOW) {
						vlen = DOMFile.LENGTH_OVERFLOW_LOCATION;
						final long overflow = ByteConversion.byteToLong(p.data, offset);
						offset += DOMFile.LENGTH_OVERFLOW_LOCATION;
						try {
							final byte[] odata = db.getOverflowValue(overflow);
							nextNode = StoredNode.deserialize(odata, 0, odata.length, doc, useNodePool);
						} catch(Exception e) {
							LOG.warn("Exception while loading overflow value: " + e.getMessage() +
									"; originating page: " + p.page.getPageInfo());
						}
					// normal node
					} else {
                        try {
    						nextNode = StoredNode.deserialize(p.data, offset, vlen, doc, useNodePool);
    						offset += vlen;
                        } catch(Exception e) {
                            LOG.warn("Error while deserializing node: " + e.getMessage(), e);
                            LOG.warn("Reading from offset: " + offset + "; len = " + vlen);
                            LOG.debug(db.debugPageContents(p));
    					    System.out.println(db.debugPageContents(p));
                            throw new RuntimeException(e);
                        }
					}
					if(nextNode == null) {
					    LOG.warn("illegal node on page " + p.getPageNum() + "; tid = " + ItemId.getId(lastTID) +
					            "; next = " + p.getPageHeader().getNextDataPage() + "; prev = " + 
					            p.getPageHeader().getPrevDataPage() + "; offset = " + (offset - vlen) +
					            "; len = " + p.getPageHeader().getDataLength());
					    System.out.println(db.debugPageContents(p));					    
					    return null;
					}
					if(ItemId.isRelocated(lastTID)) {
						nextNode.setInternalAddress(backLink);
					} else {
						nextNode.setInternalAddress(
							StorageAddress.createPointer((int) page, ItemId.getId(lastTID))
						);
					}
					nextNode.setOwnerDocument(doc);
				//YES ! needed because of the continue statement above
			    } while (nextNode == null);
			}
			return nextNode;
		} catch (BTreeException e) {
			LOG.warn(e.getMessage(), e);
		} catch (IOException e) {
			LOG.warn(e.getMessage(), e);
		} finally {
			lock.release(Lock.READ_LOCK);
		}
		return null;
	}

	private boolean gotoNextPosition() throws BTreeException, IOException {
		//	position the iterator at the start of the first value
		if (node != null) {
            RecordPos rec = null;
            if (node.getInternalAddress() != StoredNode.UNKNOWN_NODE_IMPL_ADDRESS)
                rec = db.findRecord(node.getInternalAddress());
            if (rec == null) {
    			long addr = db.findValue(broker, new NodeProxy(node));
    			if (addr == BTree.KEY_NOT_FOUND)
    				return false;
    			rec = db.findRecord(addr);
            }
			page = rec.getPage().getPageNum();
			p = rec.getPage();
			//Position the stream at the very beginning of the record
			offset = rec.offset - DOMFile.LENGTH_TID;
			node = null;
			return true;
		} else if (startAddress != StoredNode.UNKNOWN_NODE_IMPL_ADDRESS) {
			final RecordPos rec = db.findRecord(startAddress);
			if(rec == null)
				throw new IOException("Node not found at specified address.");
			page = rec.getPage().getPageNum();
			//Position the stream at the very beginning of the record
			offset = rec.offset - DOMFile.LENGTH_TID;
			p = rec.getPage();
			startAddress = StoredNode.UNKNOWN_NODE_IMPL_ADDRESS;
			return true;
		} else if (page != Page.NO_PAGE) {
			p = db.getCurrentPage(page);
			db.addToBuffer(p);
//			LOG.debug("reading " + p.page.getPageNum() + "; " + p.page.hashCode());
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
		if (node.getInternalAddress() != StoredNode.UNKNOWN_NODE_IMPL_ADDRESS) {
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