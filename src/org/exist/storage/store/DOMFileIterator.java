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
import org.exist.util.StorageAddress;

/**
 * Class DOMFileIterator is used to iterate over nodes in the DOM storage.
 * This implementation returns the raw value of the node. You have to call
 * Node.deserialize() to read the node from the value data.
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
		int offset, lastOffset = 0;
		short lastTID = -1;
		DOMPage p = null;
		long page;
		long startAddress = -1;
		Object lockKey;

		/**
		 *  Constructor for the DOMFileIterator object
		 *
		 *@param  doc                 Description of the Parameter
		 *@param  db                  Description of the Parameter
		 *@param  node                Description of the Parameter
		 *@exception  BTreeException  Description of the Exception
		 *@exception  IOException     Description of the Exception
		 */
		public DOMFileIterator(Object lock, DOMFile db, NodeProxy node)
			throws BTreeException, IOException {
			this.db = db;
			if (-1 < node.internalAddress)
				startAddress = node.internalAddress;
			else
				this.node = node;
			lockKey = (lock == null ? this : lock);
		}

		/**
		 *  Constructor for the DOMFileIterator object
		 *
		 *@param  doc                 Description of the Parameter
		 *@param  db                  Description of the Parameter
		 *@param  address             Description of the Parameter
		 *@exception  BTreeException  Description of the Exception
		 *@exception  IOException     Description of the Exception
		 */
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
				if (node != null) {
					db.setOwnerObject(lockKey);
					long addr = db.findValue(lockKey, node);
					if (addr == BTree.KEY_NOT_FOUND)
						return false;
					final DOMFile.RecordPos rec = db.findValuePosition(addr);
					page = rec.page.getPageNum();
					p = rec.page;
					offset = rec.offset - 2;
					node = null;
				} else if (-1 < startAddress) {
					final DOMFile.RecordPos rec = db.findValuePosition(startAddress);
					page = rec.page.getPageNum();
					offset = rec.offset - 2;
					p = rec.page;
				} else if (page > -1)
					p = db.getCurrentPage(page);
				else {
					lock.release();
					return false;
				}
				db.getPageBuffer().add(p);
				final DOMFilePageHeader ph = p.getPageHeader();
				lock.release();
				if (offset < ph.getDataLength())
					return true;
				else if (ph.getNextDataPage() < 0)
					return false;
				else
					return true;
			} catch (BTreeException e) {
				LOG.warn(e);
			} catch (IOException e) {
				LOG.warn(e);
			}
			lock.release();
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
				// position the iterator at the start of the first value
				if (node != null) {
					db.setOwnerObject(lockKey);
					final long addr = db.findValue(lockKey, node);
					if (addr == BTree.KEY_NOT_FOUND)
						return null;
					DOMFile.RecordPos rec = db.findValuePosition(addr);
					page = rec.page.getPageNum();
					p = rec.page;
					offset = rec.offset - 2;
					node = null;
				} else if (-1 < startAddress) {
					final DOMFile.RecordPos rec = db.findValuePosition(startAddress);
					page = rec.page.getPageNum();
					offset = rec.offset - 2;
					p = rec.page;
					startAddress = -1;
				} else if (page > -1)
					p = db.getCurrentPage(page);
				else {
					lock.release();
					return null;
				}
				final DOMFilePageHeader ph = p.getPageHeader();
				// next value larger than length of the current page?
				if (offset >= ph.getDataLength()) {
					// load next page in chain
					final long nextPage = ph.getNextDataPage();
					if (nextPage < 0) {
						LOG.debug("bad link to next " + p.page.getPageInfo());
						lock.release();
						return null;
					}
					page = nextPage;
					p = db.getCurrentPage(nextPage);
					offset = 0;
				}
				// extract the value
				lastTID = ByteConversion.byteToShort(p.data, offset);
				short l = ByteConversion.byteToShort(p.data, offset + 2);
				Value nextVal;
				if(l == DOMFile.OVERFLOW) {
					final long op = ByteConversion.byteToLong(p.data, offset + 4);
					final byte[] data = db.getOverflowValue(op);
					nextVal = new Value(data);
					l = 8;
				} else
					nextVal = new Value(p.data, offset + 4, l);
				nextVal.setAddress(StorageAddress.createPointer((int) page, lastTID));
				lastOffset = offset;
				offset = offset + 4 + l;
				lock.release();
				return nextVal;
			} catch (BTreeException e) {
				LOG.warn(e);
			} catch (IOException e) {
				LOG.warn(e);
			}
			lock.release();
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
				short l = ByteConversion.byteToShort(p.data, lastOffset + 2);
				// if this is an overflow value, remove it 
				if(l == DOMFile.OVERFLOW) {
					final long op = ByteConversion.byteToLong(p.data, lastOffset + 4);
					db.removeOverflowValue(op);
				}
				
				// decrement record count
				DOMFilePageHeader ph = p.getPageHeader();
				ph.decRecordCount();
				p.setDirty(true);
				// if record count == 0, remove the page
				if (ph.getRecordCount() == 0) {
					long np = ph.getNextDataPage();
					try {
						if (np > -1) {
							DOMPage next = db.getCurrentPage(np);
							next.getPageHeader().prevDataPage = -1;
							db.getPageBuffer().add(next);
						}
						ph.setNextDataPage(-1);
						ph.setPrevDataPage(-1);
						ph.setDataLength(0);
						//ph.setNextTID((short)0);
						ph.setRecordCount((short) 0);
						p.setDirty(true);
						db.getPageBuffer().remove(p);
						db.unlinkPages(p.page);
					} catch (IOException ioe) {
						LOG.warn(ioe);
					}
					page = np;
					offset = 0;
				}
			} catch (LockException e) {
				LOG.warn(e);
			} finally {
				lock.release();
			}
		}

		/**
		 *  Reposition the iterator at the address of the proxy node.
		 *
		 *@param  node  The new to value
		 */
		public void setTo(NodeProxy node) {
			if (-1 < node.internalAddress) {
				startAddress = node.internalAddress;
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