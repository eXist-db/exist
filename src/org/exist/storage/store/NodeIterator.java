package org.exist.storage.store;

import java.io.IOException;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.dbxml.core.filer.BTree;
import org.dbxml.core.filer.BTreeException;
import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeProxy;
import org.exist.util.ByteConversion;
import org.exist.util.Lock;
import org.exist.util.LockException;
import org.exist.util.StorageAddress;

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

	DOMFile db = null;
	NodeProxy node = null;
	DocumentImpl doc = null;
	int offset;
	short lastTID = -1;
	DOMFile.DOMPage p = null;
	long page;
	long startAddress = -1;
	Object lockKey;

	public NodeIterator(Object lock, DOMFile db, NodeProxy node)
		throws BTreeException, IOException {
		this.db = db;
		this.doc = node.doc;
		if (-1 < node.getInternalAddress())
			startAddress = node.getInternalAddress();
		else
			this.node = node;
		lockKey = (lock == null ? this : lock);
	}

	public NodeIterator(Object lock, DOMFile db, DocumentImpl doc, long address) {
		this.db = db;
		this.doc = doc;
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
				final DOMFile.DOMFilePageHeader ph = p.getPageHeader();
				if (offset < ph.getDataLength())
					return true;
				else if (ph.getNextDataPage() < 0)
					return false;
				else
					return true;
			}
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
		NodeImpl nextNode = null;
		try {
			try {
				lock.acquire(Lock.READ_LOCK);
			} catch (LockException e) {
				return null;
			}
			if(gotoNextPosition()) {
				final DOMFile.DOMFilePageHeader ph = p.getPageHeader();
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
					db.addToBuffer(p);
					offset = 0;
				}
				// extract the value
				lastTID = ByteConversion.byteToShort(p.data, offset);
				short l = ByteConversion.byteToShort(p.data, offset + 2);
				if(l == DOMFile.OVERFLOW) {
					// overflow page: load the overflow value
					l = 8;
					final long overflow = ByteConversion.byteToLong(p.data, offset + 4);
					final byte[] odata = db.getOverflowValue(overflow);
					nextNode = NodeImpl.deserialize(odata, 0, odata.length, doc);
				} else {
					nextNode = NodeImpl.deserialize(p.data, offset + 4, l, doc);
				}
				nextNode.setInternalAddress(StorageAddress.createPointer((int) page, lastTID));
				nextNode.setOwnerDocument(doc);
				offset = offset + 4 + l;
			}
			return nextNode;
		} catch (BTreeException e) {
			LOG.warn(e);
		} catch (IOException e) {
			LOG.warn(e);
		} finally {
			lock.release();
		}
		return null;
	}

	private boolean gotoNextPosition() throws BTreeException, IOException {
		//	position the iterator at the start of the first value
		if (node != null) {
			db.setOwnerObject(lockKey);
			final long addr = db.findValue(lockKey, node);
			if (addr == BTree.KEY_NOT_FOUND)
				return false;
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
		else
			return false;
		return true;
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