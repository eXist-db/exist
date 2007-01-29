package org.exist.storage.dom;

import java.io.IOException;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.exist.dom.NodeProxy;
import org.exist.dom.StoredNode;
import org.exist.storage.StorageAddress;
import org.exist.storage.btree.BTree;
import org.exist.storage.btree.BTreeException;
import org.exist.storage.btree.Value;
import org.exist.storage.btree.Paged.Page;
import org.exist.storage.lock.Lock;
import org.exist.util.ByteConversion;
import org.exist.util.LockException;

/**
 * Iterate through all nodes of a document in the DOM storage. Returns the
 * raw data of the node in a {@link org.exist.storage.btree.Value}. Use class 
 * {@link org.exist.storage.dom.NodeIterator} to get node objects instead of
 * raw data.
 * 
 * The DOM file is locked to locate the data and released afterwards. Before
 * working with the returned data, you should get a copy by calling value.getData(). 
 * 
 * @author wolf
 */
public final class DOMFileIterator implements Iterator {
	
		private final static Logger LOG = Logger.getLogger(DOMFileIterator.class);
		
		DOMFile db = null;
		NodeProxy node = null;
		int offset;
		int lastOffset = 0 ;
		short lastTID = -1;
		DOMFile.DOMPage p = null;
		long page;
		long startAddress = StoredNode.UNKNOWN_NODE_IMPL_ADDRESS;
		Object lockKey;

		public DOMFileIterator(Object lock, DOMFile db, NodeProxy node)
			throws BTreeException, IOException {
			this.db = db;
			if (node.getInternalAddress() != StoredNode.UNKNOWN_NODE_IMPL_ADDRESS)
				startAddress = node.getInternalAddress();
			else
				this.node = node;
			lockKey = (lock == null ? this : lock);
		}

		public DOMFileIterator(Object lock, DOMFile db, long address)
			throws BTreeException, IOException {
			this.db = db;
			this.startAddress = address;
			lockKey = (lock == null ? this : lock);
		}

		/**
		 *  Returns the internal virtual address of the node at the iterator's
		 * current position.
		 *
		 *@return    The currentAddress value
		 */
		public long currentAddress() {
			return StorageAddress.createPointer((int) page, lastTID);
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
					lock.acquire();
				} catch (LockException e) {
					LOG.warn(e);
					System.out.println(e);
					return false;
				}
				if(gotoNextPosition()) {
					db.getPageBuffer().add(p);
					final DOMFile.DOMFilePageHeader ph = p.getPageHeader();
					if (offset < ph.getDataLength())
						return true;
					else if (ph.getNextDataPage() == Page.NO_PAGE)
						return false;
					else
						return true;
				} else
				    return false;
			} catch (BTreeException e) {
				LOG.warn(e);
			} catch (IOException e) {
				LOG.warn(e);
			} finally {
			    lock.release();
			}
			return false;
		}

		/**
		 *  Returns the raw data of the next node in the sequence.
		 *
		 *@return    Description of the Return Value
		 */
		public Object next() {
			Lock lock = db.getLock();
			try {
				try {
					lock.acquire();
				} catch (LockException e) {
					LOG.warn(e);
					System.out.println(e);
					return null;
				}
				Value nextVal = null;
				if(gotoNextPosition()) {
				    do {
						DOMFile.DOMFilePageHeader ph = p.getPageHeader();
						// next value larger than length of the current page?
						if (offset >= ph.getDataLength()) {
							// load next page in chain
							final long nextPage = ph.getNextDataPage();
							if (nextPage == Page.NO_PAGE) {
								LOG.debug("bad link to next " + p.page.getPageInfo());
								return null;
							}
							page = nextPage;
							p = db.getCurrentPage(nextPage);
							offset = 0;
							db.addToBuffer(p);
						}
						lastOffset = offset;
						
						// extract tid
						lastTID = ByteConversion.byteToShort(p.data, offset);
						offset += 2;
						//	check if this is just a link to a relocated node
						if(ItemId.isLink(lastTID)) {
							// skip this
							offset += 8;
							continue;
						}
						// read data length
						short l = ByteConversion.byteToShort(p.data, offset);
						offset += 2;
						if(ItemId.isRelocated(lastTID)) {
							// found a relocated node. Skip the next 8 bytes
							offset += 8;
						}
						if(l == DOMFile.OVERFLOW) {
							final long op = ByteConversion.byteToLong(p.data, offset);
							offset += 8;
							final byte[] data = db.getOverflowValue(op);
							nextVal = new Value(data);
						} else {
							nextVal = new Value(p.data, offset, l);
							offset += l;
						}
						nextVal.setAddress(
							StorageAddress.createPointer((int) page, ItemId.getId(lastTID))
						);
				    } while(nextVal == null);
				}
				return nextVal;
			} catch (BTreeException e) {
				LOG.warn(e);
			} catch (IOException e) {
				LOG.warn(e);
			} finally {
			    lock.release();
			}
			return null;
		}

		public void remove() {
			throw new RuntimeException("Remove is not allowed");
		}

		private boolean gotoNextPosition() throws BTreeException, IOException {
		    if (node != null) {
				db.setOwnerObject(lockKey);
				long addr = db.findValue(lockKey, node);
				if (addr == BTree.KEY_NOT_FOUND)
					return false;
				RecordPos rec = db.findRecord(addr);
				if(rec != null) {
					page = rec.getPage().getPageNum();
					p = rec.getPage();
					offset = rec.offset - 2;
					node = null;
				} else
				    return false;
			} else if (startAddress != StoredNode.UNKNOWN_NODE_IMPL_ADDRESS) {
				RecordPos rec = db.findRecord(startAddress);
				if(rec != null) {
					page = rec.getPage().getPageNum();
					offset = rec.offset - 2;
					p = rec.getPage();
					startAddress = StoredNode.UNKNOWN_NODE_IMPL_ADDRESS;
					return true;
				} else
				    return false;
			} else if (page == Page.NO_PAGE)
			    return false;
			p = db.getCurrentPage(page);
			db.addToBuffer(p);
			return true;
		}
		
		/**
		 *  Reposition the iterator at the address of the proxy node.
		 *
		 *@param  node  The new to value
		 */
		public void setTo(NodeProxy node) {
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