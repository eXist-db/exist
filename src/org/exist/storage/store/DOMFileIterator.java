package org.exist.storage.store;

import java.io.IOException;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.dbxml.core.data.Value;
import org.dbxml.core.filer.BTree;
import org.dbxml.core.filer.BTreeException;
import org.exist.dom.NodeProxy;
import org.exist.storage.store.DOMFile.DOMFilePageHeader;
import org.exist.storage.store.DOMFile.DOMPage;
import org.exist.util.ByteConversion;
import org.exist.util.Lock;
import org.exist.util.LockException;

/**
 * Iterate through all nodes of a document in the DOM storage. Returns the
 * raw data of the node in a {@link org.dbxml.core.data.Value}. Use class 
 * {@link org.exist.storage.store.NodeIterator} to get node objects instead of
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
		DOMPage p = null;
		long page;
		long startAddress = -1;
		Object lockKey;

		public DOMFileIterator(Object lock, DOMFile db, NodeProxy node)
			throws BTreeException, IOException {
			this.db = db;
			if (-1 < node.getInternalAddress())
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
					return false;
				}
				if(gotoNextPosition()) {
					db.getPageBuffer().add(p);
					final DOMFilePageHeader ph = p.getPageHeader();
					if (offset < ph.getDataLength())
						return true;
					else if (ph.getNextDataPage() < 0)
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
					return null;
				}
				Value nextVal = null;
				if(gotoNextPosition()) {
				    do {
						DOMFilePageHeader ph = p.getPageHeader();
						// next value larger than length of the current page?
						if (offset >= ph.getDataLength()) {
							// load next page in chain
							final long nextPage = ph.getNextDataPage();
							if (nextPage < 0) {
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

		/**
		 * Remove the current node. This implementation just
		 * decrements the node count. It does not actually remove
		 * the node's value, but removes a page if
		 * node count == 0. Use this method only if you want to
		 * delete an entire document, not to remove a single node.
		 */
		public void remove() {
			Lock lock = db.getLock();
			try {
				lock.acquire(Lock.WRITE_LOCK);
				DOMPage p = db.getCurrentPage(page);
				short itemId = ByteConversion.byteToShort(p.data, lastOffset);
				lastOffset += 2;
				short l = ByteConversion.byteToShort(p.data, lastOffset);
				lastOffset += 2;
				if(ItemId.isRelocated(itemId)) {
				    long backLink = ByteConversion.byteToLong(p.data, lastOffset);
				    lastOffset += 8;
				    DOMFile.RecordPos origRec = db.findRecord(backLink, false);
				    DOMFilePageHeader ph = origRec.page.getPageHeader();
				    ph.decRecordCount();
				    //LOG.debug("Removing link from " + StorageAddress.pageFromPointer(backLink) + "; count=" + ph.getRecordCount());
				    origRec.page.setDirty(true);
				    if(ph.getRecordCount() == 0) {
				        //LOG.debug("Removing linked page: " + origRec.page.getPageNum());
				        db.removePage(origRec.page);
				    }
				}
				// if this is an overflow value, remove it 
				if(l == DOMFile.OVERFLOW) {
					final long op = ByteConversion.byteToLong(p.data, lastOffset);
					lastOffset += 8;
					db.removeOverflowValue(op);
				}
				
				// decrement record count
				DOMFilePageHeader ph = p.getPageHeader();
				ph.decRecordCount();
				//LOG.debug("removed value from " + p.getPageNum() + "; size=" + ph.getRecordCount());
				p.setDirty(true);
				// if record count == 0, remove the page
				if (ph.getRecordCount() == 0) {
					long np = ph.getNextDataPage();
					db.removePage(p);
					page = np;
					offset = 0;
				} else
				    db.getPageBuffer().add(p);
			} catch (LockException e) {
				LOG.warn(e);
			} finally {
				lock.release();
			}
		}

		private boolean gotoNextPosition() throws BTreeException, IOException {
		    if (node != null) {
				db.setOwnerObject(lockKey);
				long addr = db.findValue(lockKey, node);
				if (addr == BTree.KEY_NOT_FOUND)
					return false;
				DOMFile.RecordPos rec = db.findRecord(addr);
				if(rec != null) {
					page = rec.page.getPageNum();
					p = rec.page;
					offset = rec.offset - 2;
					node = null;
				} else
				    return false;
			} else if (-1 < startAddress) {
				DOMFile.RecordPos rec = db.findRecord(startAddress);
				if(rec != null) {
					page = rec.page.getPageNum();
					offset = rec.offset - 2;
					p = rec.page;
					startAddress = -1;
					return true;
				} else
				    return false;
			} else if (page == -1)
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
			if (-1 < node.getInternalAddress()) {
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