package org.exist.storage;

/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 *  $Id$
 */
import org.dbxml.core.*;
import org.dbxml.core.filer.*;
import org.dbxml.core.indexer.*;
import org.dbxml.core.data.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Iterator;
import java.io.IOException;
import java.io.File;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import org.apache.log4j.Logger;
import gnu.trove.TLongObjectHashMap;
import gnu.trove.TObjectLongHashMap;
import org.exist.util.*;
import org.exist.dom.*;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    25. Mai 2002
 */
public class DOMFile extends BTree implements Lockable {

    // page types
    public final static byte FREE_LIST = 22;
    public final static byte LOB = 21;
    public final static byte RECORD = 20;

    protected static int GROW_BY = 8;

    private static Logger LOG = Logger.getLogger(DOMFile.class);

    private final ClockPageBuffer buffer;
    private DOMFileHeader fileHeader;
    private Object owner = null;

    private Lock lock = new SimpleTimeOutLock();

    private final TObjectLongHashMap pages = new TObjectLongHashMap();

    /**
     *  Constructor for the DOMFile object
     *
     *@param  buffers      Description of the Parameter
     *@param  dataBuffers  Description of the Parameter
     */
    public DOMFile(int buffers, int dataBuffers) {
        super(buffers);
        fileHeader = (DOMFileHeader) getFileHeader();
        fileHeader.setPageCount(0);
        fileHeader.setTotalCount(0);
        buffer = new ClockPageBuffer(dataBuffers);
    }

    /**
     *  Constructor for the DOMFile object
     *
     *@param  file  Description of the Parameter
     */
    public DOMFile(File file) {
        this(256, 256);
        setFile(file);
    }

    /**
     *  Constructor for the DOMFile object
     *
     *@param  file     Description of the Parameter
     *@param  buffers  Description of the Parameter
     */
    public DOMFile(File file, int buffers) {
        this(buffers, 256);
        setFile(file);
    }

    /**
     *  Constructor for the DOMFile object
     *
     *@param  file         Description of the Parameter
     *@param  buffers      Description of the Parameter
     *@param  dataBuffers  Description of the Parameter
     */
    public DOMFile(File file, int buffers, int dataBuffers) {
        this(buffers, dataBuffers);
        setFile(file);
    }

    /**
     *  Constructor for the DOMFile object
     *
     *@param  file     Description of the Parameter
     *@param  buffers  Description of the Parameter
     *@param  keyLen   Description of the Parameter
     */
    public DOMFile(File file, int buffers, short keyLen) {
        this(file, buffers);
        fileHeader.setKeyLen(keyLen);
    }

    /**
     *  Description of the Method
     *
     *@param  page    Description of the Parameter
     *@param  offset  Description of the Parameter
     *@return         Description of the Return Value
     */
    public final static long createPointer(int page, int offset) {
        long p = (page & 0xffff);
        long o = (offset & 0xffff);
        return page | (o << 32);
    }

    /**
     *  Description of the Method
     *
     *@param  pointer  Description of the Parameter
     *@return          Description of the Return Value
     */
    public final static int offsetFromPointer(long pointer) {
        return (int) ((pointer >>> 32) & 0xffff);
    }

    /**
     *  Description of the Method
     *
     *@param  pointer  Description of the Parameter
     *@return          Description of the Return Value
     */
    public final static int pageFromPointer(long pointer) {
        return (int) pointer;
    }

    /**
     *  Description of the Method
     *
     *@param  value  Description of the Parameter
     *@return        Description of the Return Value
     */
    public long add(byte[] value) throws ReadOnlyException {
        if (value == null)
            return -1;
		final int valueLen = value.length;
        // always append data to the end of the file
        DOMPage page = getCurrentPage();
        // does value fit into current data page?
        if (page == null
            || page.len + 2 + valueLen > page.data.length) {
            DOMPage newPage = new DOMPage();
            if (page != null) {
                DOMFilePageHeader ph = page.getPageHeader();
                ph.setNextDataPage(newPage.getPageNum());
                page.setDirty(true);
                //page.write();
            }
            page = newPage;
            setCurrentPage(page);
        }
        // create pointer from pageNum and offset into page
        final long p = createPointer((int) page.getPageNum(), page.len);

        // save data length
        ByteConversion.shortToByte(
            (short) valueLen,
            page.data,
            page.len);
        page.len += 2;
        // save data
        System.arraycopy(
            value,
            0,
            page.data,
            page.len,
            valueLen);
        page.len += valueLen;
        DOMFilePageHeader ph = page.getPageHeader();
        ph.incRecordCount();
        ph.setDataLength(page.len);
        page.setDirty(true);
        buffer.add(page);
        return p;
    }

	public long insertAfter(Value key, byte[] value) {
		try {
			long p = findValue(key);
			return insertAfter(p, value);
		} catch (BTreeException e) {
			LOG.warn("key not found", e);
		} catch (IOException e) {
			LOG.warn("IO error", e);
		}
		return -1;
	}
	
	public long insertAfter(long address, byte[] value) {
		long pnum = (long)pageFromPointer(address);
		int offset = offsetFromPointer(address);
		DOMPage page = getCurrentPage(pnum);
		if(page == null) {
			LOG.warn("page " + pnum + " not found");
			return -1;
		}
		short l = ByteConversion.byteToShort(page.data, offset);
		offset = offset + l + 2;
		if(offset < page.getPageHeader().getDataLength()) {
			// split the page
			LOG.debug("splitting page " + page.getPageNum() + " at " +
				offset);
			DOMPage splitPage = new DOMPage();
			splitPage.len = page.getPageHeader().getDataLength() - offset;
			System.arraycopy(page.data, offset, 
				splitPage.data, 0, splitPage.len);
			splitPage.getPageHeader().setDataLength(splitPage.len);
			splitPage.getPageHeader().setNextDataPage(
				page.getPageHeader().getNextDataPage()
			);
			buffer.add(splitPage);
			page.getPageHeader().setNextDataPage(splitPage.getPageNum());
			page.len = offset;
			page.setDirty(true);
		}
		ByteConversion.shortToByte((short)value.length, page.data, page.len);
		page.len += 2;
		System.arraycopy(value, 0, page.data, page.len, value.length);
		page.len += value.length;
		buffer.add(page);
		return offset;
	}
	
    /**
     *  Description of the Method
     *
     *@return                  Description of the Return Value
     *@exception  DBException  Description of the Exception
     */
    public boolean close() throws DBException {
        flush();
        super.close();
        return true;
    }

    /**
     *  Description of the Method
     *
     *@return                  Description of the Return Value
     *@exception  DBException  Description of the Exception
     */
    public boolean create() throws DBException {
        if (super.create((short) 12))
            return true;
        else
            return false;
    }

    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public FileHeader createFileHeader() {
        return new DOMFileHeader(1024, PAGE_SIZE);
    }

    /**
     *  Description of the Method
     *
     *@param  read             Description of the Parameter
     *@return                  Description of the Return Value
     *@exception  IOException  Description of the Exception
     */
    public FileHeader createFileHeader(boolean read) throws IOException {
        return new DOMFileHeader(read);
    }

    /**
     *  Description of the Method
     *
     *@param  pageCount  Description of the Parameter
     *@return            Description of the Return Value
     */
    public FileHeader createFileHeader(long pageCount) {
        return new DOMFileHeader(pageCount, PAGE_SIZE);
    }

    /**
     *  Description of the Method
     *
     *@param  pageCount  Description of the Parameter
     *@param  pageSize   Description of the Parameter
     *@return            Description of the Return Value
     */
    public FileHeader createFileHeader(long pageCount, int pageSize) {
        return new DOMFileHeader(pageCount, pageSize);
    }

    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    protected Page createNewPage() {
        try {
            Page page = getFreePage();
            DOMFilePageHeader ph = (DOMFilePageHeader) page.getPageHeader();
            ph.setStatus(RECORD);
            ph.setDirty(true);
            ph.setNextDataPage(-1);
            ph.setDataLength(0);
            ph.setRecordCount((short) 0);
            //page.write();
            fileHeader.setLastDataPage(page.getPageNum());
            return page;
        } catch (IOException ioe) {
            LOG.warn(ioe);
            return null;
        }
    }

    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public PageHeader createPageHeader() {
        return new DOMFilePageHeader();
    }

    /**
     *  Description of the Method
     *
     *@param  query               Description of the Parameter
     *@return                     Description of the Return Value
     *@exception  IOException     Description of the Exception
     *@exception  BTreeException  Description of the Exception
     */
    public ArrayList findKeys(IndexQuery query)
        throws IOException, BTreeException {
        FindCallback cb = new FindCallback(FindCallback.KEYS);
        query(query, cb);
        return cb.getValues();
    }

    private long findNode(NodeImpl node, long target, Iterator iter) {
        if (node.hasChildNodes()) {
            long firstChildId = 
            	XMLUtil.getFirstChildId((DocumentImpl)node.getOwnerDocument(),
            		node.getGID());
            if(firstChildId < 0)
            	return 0;
            long lastChildId = firstChildId + node.getChildCount();
	    //LOG.debug("scanning " + firstChildId + " to " + lastChildId);
            long p;
            for (long gid = firstChildId; gid < lastChildId; gid++) {
                Value value = (Value) iter.next();
                if (gid == target)
                    return ((DOMFileIterator) iter).currentAddress();
                NodeImpl child =
                    NodeImpl.deserialize(
                        value.getData(),
                        (DocumentImpl) node.getOwnerDocument());
                child.setGID(gid);
                if (node.hasChildNodes() && 
                	(p = findNode(child, target, iter)) != 0)
                    return p;
            }
        }
        return 0;
    }

    /**
     *  Description of the Method
     *
     *@param  first               Description of the Parameter
     *@param  last                Description of the Parameter
     *@return                     Description of the Return Value
     *@exception  IOException     Description of the Exception
     *@exception  BTreeException  Description of the Exception
     */
    public ArrayList findRange(Value first, Value last)
        throws IOException, BTreeException {
        IndexQuery query = new IndexQuery(null, IndexQuery.BW, first, last);
        RangeCallback cb = new RangeCallback();
        query(query, cb);
        return cb.getValues();
    }

    private long findValue(Object lock, NodeProxy node)
        throws IOException, BTreeException {
        DocumentImpl doc = (DocumentImpl) node.getDoc();
        NativeBroker.NodeRef nodeRef =
            new NativeBroker.NodeRef(doc.getDocId(), node.getGID());
        try {
            // first try to find the node in the index
            return findValue(nodeRef);
        } catch (BTreeException e) {
            // node not found in index: try to find the nearest available
            // ancestor and traverse it
            long id = node.getGID();
	    	//LOG.debug("node " + doc.getDocId() + ':' + id + " not found; trying ancestor");
			
            long parentPointer = -1;
            while (parentPointer < 0) {
                if (id < 1)
                    throw new BTreeException(
                        "node " + node.gid + " not found.");
                id = XMLUtil.getParentId(doc, id);
                NativeBroker.NodeRef parentRef =
                    new NativeBroker.NodeRef(doc.getDocId(), id);
                try {
                    parentPointer = findValue(parentRef);
                } catch (BTreeException bte) {
                }
            }
            long firstChildId = XMLUtil.getFirstChildId(doc, id);
            Iterator iter = new DOMFileIterator(lock, doc, this, parentPointer);
            Value value = (Value) iter.next();
            NodeImpl n = NodeImpl.deserialize(value.getData(), doc);
            n.setGID(id);
            long address = findNode(n, node.gid, iter);
            return address == 0 ? -1 : address;
        }
    }

    /**
     *  Description of the Method
     *
     *@param  query               Description of the Parameter
     *@return                     Description of the Return Value
     *@exception  IOException     Description of the Exception
     *@exception  BTreeException  Description of the Exception
     */
    public ArrayList findValues(IndexQuery query)
        throws IOException, BTreeException {
        FindCallback cb = new FindCallback(FindCallback.VALUES);
        query(query, cb);
        return cb.getValues();
    }

    /**
     *  Description of the Method
     *
     *@return                  Description of the Return Value
     *@exception  DBException  Description of the Exception
     */
    public boolean flush() throws DBException {
        buffer.flush();
        pages.remove(owner);
        super.flush();
        try {
        	if( fileHeader.isDirty() )
            	fileHeader.write();
        } catch (IOException ioe) {
            LOG.debug("sync failed", ioe);
        }
        return true;
    }

	public void sync() throws DBException {
		buffer.clear();
		pages.remove(owner);
		super.flush();
		try {
			if( fileHeader.isDirty() )
				fileHeader.write();
		} catch(IOException ioe) {
			LOG.warn("sync failed", ioe);
		}
	}
	
	public void printStatistics() {
		super.printStatistics();
		buffer.printStatistics();
	}
	
    /**
     *  Description of the Method
     *
     *@param  key  Description of the Parameter
     *@return      Description of the Return Value
     */
    public Value get(Value key) {
        try {
            long p = findValue(key);
            if (p < 0)
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
     *  Description of the Method
     *
     *@param  node  Description of the Parameter
     *@return       Description of the Return Value
     */
    public Value get(NodeProxy node) {
        try {
            long p = findValue(owner, node);
            if (p < 0)
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
     *  Description of the Method
     *
     *@param  p  Description of the Parameter
     *@return    Description of the Return Value
     */
    public Value get(long p) {
        long pos = (long) pageFromPointer(p);
        int offset = (int) offsetFromPointer(p);
        DOMPage page = getCurrentPage(pos);
        buffer.add( page );
        short l = ByteConversion.byteToShort(page.data, offset);
        int dataStart = offset + 2;
        return new Value(page.data, dataStart, l);
    }

    /**
     *  Gets the currentPage attribute of the DOMFile object
     *
     *@return    The currentPage value
     */
    private final DOMPage getCurrentPage() {
        if (!pages.containsKey(owner)) {
            final DOMPage page = new DOMPage();
            pages.put(owner, page.page.getPageNum());
			return page;
        } else {
            return getCurrentPage(pages.get(owner));
        }
    }

    /**
     *  Gets the currentPage attribute of the DOMFile object
     *
     *@param  p  Description of the Parameter
     *@return    The currentPage value
     */
    private final DOMPage getCurrentPage(long p) {
        DOMPage page = (DOMPage) buffer.get(p);
        if (page == null)
        	page = new DOMPage(p);
        return page;
    }

    /**
     *@return    The rootNode value
     */
//    protected BTreeNode getRootNode() {
//        try {
//            if (currentDoc.getRootPage() < 0) {
//                long rootPage = createRootNode();
//                currentDoc.setRootPage(rootPage);
//            }
//            BTreeNode node = (BTreeNode) cache.get(currentDoc.getRootPage());
//            if (node == null) {
//            	LOG.debug("reading root: " + currentDoc.getRootPage());
//                Page p = getPage(currentDoc.getRootPage());
//                node = new BTreeNode(p);
//                node.read();
//            }
//            cache.add(node);
//            return node;
//        } catch (Exception e) {
//            System.err.println(e);
//            e.printStackTrace();
//            return null;
//        }
//    }

    /**
     *  Description of the Method
     *
     *@param  doc   Description of the Parameter
     *@param  node  Description of the Parameter
     *@return       Description of the Return Value
     */
    public Iterator iterator(DocumentImpl doc, NodeProxy node) {
        try {
            return new DOMFileIterator(owner, doc, this, node);
        } catch (IOException ioe) {
            LOG.warn(ioe);
        } catch (BTreeException bte) {
            LOG.warn(bte);
        }
        return null;
    }

    /**
     *  Description of the Method
     *
     *@param  doc      Description of the Parameter
     *@param  address  Description of the Parameter
     *@return          Description of the Return Value
     */
    public Iterator iterator(DocumentImpl doc, long address) {
        try {
            return new DOMFileIterator(owner, doc, this, address);
        } catch (IOException ioe) {
            LOG.warn(ioe);
        } catch (BTreeException bte) {
            LOG.warn(bte);
        }
        return null;
    }

    /**
     *  Description of the Method
     *
     *@return                  Description of the Return Value
     *@exception  DBException  Description of the Exception
     */
    public boolean open() throws DBException {
        if (super.open())
            return true;
        else
            return false;
    }

    /**
     *  Description of the Method
     *
     *@param  key    Description of the Parameter
     *@param  value  Description of the Parameter
     *@return        Description of the Return Value
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
     *  Description of the Method
     *
     *@param  key  Description of the Parameter
     */
    public void remove(Value key) {
        try {
            long p = findValue(key);
            remove(p);
            removeValue(key);
        } catch (BTreeException bte) {
            LOG.debug(bte);
        } catch (IOException ioe) {
            LOG.debug(ioe);
        }
    }

    /**
     *  Description of the Method
     *
     *@param  p  Description of the Parameter
     */
    public void remove(long p) {
        long pos = (long) pageFromPointer(p);
        int offset = (int) offsetFromPointer(p);
        DOMPage page = getCurrentPage(pos);
        DOMFilePageHeader ph = page.getPageHeader();
        ph.decRecordCount();
        if (ph.getRecordCount() == 0) {
            buffer.remove(page);
            long np = ph.getNextDataPage();
            try {
                if (fileHeader.getLastDataPage() == page.getPageNum())
                    fileHeader.setLastDataPage(-1);
                ph.setNextDataPage(-1);
                ph.setDataLength(0);
                ph.setRecordCount((short) 0);
                unlinkPages(page.page);
                fileHeader.write();
            } catch (IOException ioe) {
                LOG.warn(ioe);
            }
            page = null;
        } else
            buffer.add(page);
    }

    /**
     *  Sets the currentPage attribute of the DOMFile object
     *
     *@param  page  The new currentPage value
     */
    private final void setCurrentPage(DOMPage page) {
        final long pnum = pages.get(owner);
        if (pnum == page.page.getPageNum())
            return;
        pages.remove(owner);
        pages.put(owner, page.page.getPageNum());
    }

    public final Lock getLock() {
        return lock;
    }

    /**
     *  Sets the location attribute of the DOMFile object
     *
     *@param  location  The new location value
     */
    public void setLocation(String location) {
        setFile(new File(location + ".dbx"));
    }

    /**
     *  Sets the ownerObject attribute of the DOMFile object
     *
     *@param  obj  The new ownerObject value
     */
    public final void setOwnerObject(Object obj) {
        owner = obj;
    }

	public final void releaseOwner(Object obj) {
		//pages.remove(obj);
	}

    /**
     *  Set the rootNode of the B+-tree
     *
     *@param  rootNode         The new rootNode value
     *@exception  IOException  Description of the Exception
     */
//    protected void setRootNode(BTreeNode rootNode) throws IOException {
//        if (currentDoc != null)
//            currentDoc.setRootPage(rootNode.page.getPageNum());
//        cache.add(rootNode);
//    }

    /**
     *  Description of the Method
     *
     *@param  key    Description of the Parameter
     *@param  value  Description of the Parameter
     *@return        Description of the Return Value
     */
    public boolean update(Value key, byte[] value) throws ReadOnlyException {
        try {
            long p = findValue(key);
            if (p < 0)
                return false;
            // key not found
            update(key, p, value);
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
     *  Description of the Method
     *
     *@param  key    Description of the Parameter
     *@param  p      Description of the Parameter
     *@param  value  Description of the Parameter
     */
    public void update(Value key, long p, byte[] value) 
    throws ReadOnlyException {
        long pos = (long) pageFromPointer(p);
        int offset = (int) offsetFromPointer(p);
        DOMPage page = getCurrentPage(pos);
        buffer.add(page);
        //long l = VariableByteCoding.decode( page.data, offset );
        //int dataStart = offset + VariableByteCoding.getSize( l );
        short l = ByteConversion.byteToShort(page.data, offset);
        int dataStart = offset + 2;
        if (value.length > l) {
            LOG.debug("key updated");
            remove(key);
            put(key, value);
        } else {
            System.arraycopy(
                value,
                0,
                page.data,
                dataStart,
                value.length);
            page.setDirty(true);
        }
    }
	
    private final static class DOMFileIterator implements Iterator {
        DOMFile db = null;
        DocumentImpl doc = null;
        Value nextVal = null;
        NodeProxy node = null;
        int offset, lastOffset;
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
        public DOMFileIterator(
            Object lock,
            DocumentImpl doc,
            DOMFile db,
            NodeProxy node)
            throws BTreeException, IOException {
            this.db = db;
            this.node = node;
            this.doc = doc;
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
        public DOMFileIterator(
            Object lock,
            DocumentImpl doc,
            DOMFile db,
            long address)
            throws BTreeException, IOException {
            this.db = db;
            this.startAddress = address;
            this.doc = doc;
            lockKey = (lock == null ? this : lock);
        }

        /**
         *  Gets the currentAddress attribute of the DOMFileIterator object
         *
         *@return    The currentAddress value
         */
        public long currentAddress() {
            return createPointer((int) page, lastOffset);
        }

        /**
         *  Description of the Method
         *
         *@return    Description of the Return Value
         */
        public boolean hasNext() {
            Lock lock = db.getLock();
            try {
                try {
                    lock.acquire(lockKey);
                    lock.enter(lockKey);
                } catch (LockException e) {
                    return false;
                }
                if (node != null) {
                    db.setOwnerObject(lockKey);
                    long p = db.findValue(lockKey, node);
                    page = (long) DOMFile.pageFromPointer(p);
                    offset = DOMFile.offsetFromPointer(p);
                    node = null;
                } else if (-1 < startAddress) {
                    page = (long) DOMFile.pageFromPointer(startAddress);
                    offset = DOMFile.offsetFromPointer(startAddress);
                    startAddress = -1;
                }
                if (page < 0) {
                    lock.release(lockKey);
                    return false;
                }
                p = db.getCurrentPage(page);
                DOMFilePageHeader ph = p.getPageHeader();
                lock.release(lockKey);
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
            lock.release(lockKey);
            return false;
        }

        /**
         *  Description of the Method
         *
         *@return    Description of the Return Value
         */
        public Object next() {
            Lock lock = db.getLock();
            try {
                try {
                    lock.acquire(lockKey);
                    lock.enter(lockKey);
                } catch (LockException e) {
                    return null;
                }
                if (node != null) {
                    db.setOwnerObject(lockKey);
                    long p = db.findValue(lockKey, node);
                    page = (long) DOMFile.pageFromPointer(p);
                    offset = DOMFile.offsetFromPointer(p);
                    node = null;
                } else if (-1 < startAddress) {
                    page = (long) DOMFile.pageFromPointer(startAddress);
                    offset = DOMFile.offsetFromPointer(startAddress);
                    startAddress = -1;
                }

                if (page < 0) {
                    lock.release(lockKey);
                    return null;
                }
                DOMPage p = 
                	db.getCurrentPage(page);
                db.buffer.add(p);
                DOMFilePageHeader ph = p.getPageHeader();
                if (offset >= ph.getDataLength()) {
                    long nextPage = ph.getNextDataPage();
                    if (nextPage < 0) {
                        LOG.debug(
                            "offset out of range " + p.page.getPageInfo());
                        lock.release(lockKey);
                        return null;
                    }
                    page = nextPage;
                    p = db.getCurrentPage(nextPage);
                    offset = 0;
                }
                short l = ByteConversion.byteToShort(p.data, offset);
                int dataStart = offset + 2;
                Value nextVal = new Value(p.data, dataStart, l);
                nextVal.setAddress(createPointer((int) page, offset));
                lastOffset = offset;
                offset = dataStart + l;
                lock.release(lockKey);
                return nextVal;
            } catch (BTreeException e) {
                LOG.warn(e);
            } catch (IOException e) {
                LOG.warn(e);
            }
            lock.release(lockKey);
            return null;
        }

        /**  Description of the Method */
        public void remove() {
            Lock lock = db.getLock();
            try {
                lock.acquire(lockKey, Lock.WRITE_LOCK);
                lock.enter(lockKey);
                DOMPage p = null;
                p = db.getCurrentPage(page);
                DOMFilePageHeader ph = p.getPageHeader();
                ph.decRecordCount();
                if (ph.getRecordCount() == 0) {
                    long np = ph.getNextDataPage();
                    try {
                        if (db.fileHeader.getLastDataPage() == p.getPageNum())
                            db.fileHeader.setLastDataPage(-1);
                        ph.setNextDataPage(-1);
                        ph.setDataLength(0);
                        ph.setRecordCount((short) 0);
                        p.setDirty(true);
                        db.buffer.remove( p );
                        db.unlinkPages(p.page);
                        db.fileHeader.write();
                    } catch (IOException ioe) {
                        LOG.warn(ioe);
                    }
                    page = np;
                    offset = 0;
                }
            } catch (LockException e) {
                LOG.warn(e);
            } finally {
                lock.release(lockKey);
            }
        }

        /**
         *  Sets the to attribute of the DOMFileIterator object
         *
         *@param  node  The new to value
         */
        public void setTo(NodeProxy node) {
            this.node = node;
        }

        /**
         *  Sets the to attribute of the DOMFileIterator object
         *
         *@param  address  The new to value
         */
        public void setTo(long address) {
            this.startAddress = address;
        }
    }

    /**
     *  Description of the Class
     *
     *@author     wolf
     *@created    3. Juni 2002
     */
    private final class DOMFileHeader extends BTreeFileHeader {

        protected long lastDataPage = -1;
        protected LinkedList reserved = new LinkedList();

        /**  Constructor for the DOMFileHeader object */
        public DOMFileHeader() {
        }

        /**
         *  Constructor for the DOMFileHeader object
         *
         *@param  pageCount  Description of the Parameter
         */
        public DOMFileHeader(long pageCount) {
            super(pageCount);
        }

        /**
         *  Constructor for the DOMFileHeader object
         *
         *@param  pageCount  Description of the Parameter
         *@param  pageSize   Description of the Parameter
         */
        public DOMFileHeader(long pageCount, int pageSize) {
            super(pageCount, pageSize);
        }

        /**
         *  Constructor for the DOMFileHeader object
         *
         *@param  pageCount  Description of the Parameter
         *@param  pageSize   Description of the Parameter
         *@param  blockSize  Description of the Parameter
         */
        public DOMFileHeader(long pageCount, int pageSize, byte blockSize) {
            super(pageCount, pageSize, blockSize);
        }

        /**
         *  Constructor for the DOMFileHeader object
         *
         *@param  read             Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public DOMFileHeader(boolean read) throws IOException {
            super(read);
        }

        /**
         *  Adds a feature to the ReservedPage attribute of the DOMFileHeader
         *  object
         *
         *@param  page  The feature to be added to the ReservedPage attribute
         */
        public void addReservedPage(long page) {
            reserved.addFirst(new Long(page));
        }

        /**
         *  Gets the lastDataPage attribute of the DOMFileHeader object
         *
         *@return    The lastDataPage value
         */
        public long getLastDataPage() {
            return lastDataPage;
        }

        /**
         *  Gets the reservedPage attribute of the DOMFileHeader object
         *
         *@return    The reservedPage value
         */
        public long getReservedPage() {
            if (reserved.size() == 0)
                return -1;
            return ((Long) reserved.removeLast()).longValue();
        }

        /**
         *  Description of the Method
         *
         *@param  raf              Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public void read(java.io.RandomAccessFile raf) throws IOException {
            super.read(raf);
            lastDataPage = raf.readLong();
            int rp = raf.readInt();
            long l;
            for (int i = 0; i < rp; i++) {
                l = raf.readLong();
                reserved.addFirst(new Long(l));
            }
        }

        /**
         *  Sets the lastDataPage attribute of the DOMFileHeader object
         *
         *@param  last  The new lastDataPage value
         */
        public void setLastDataPage(long last) {
            lastDataPage = last;
        }

        /**
         *  Description of the Method
         *
         *@param  raf              Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public void write(java.io.RandomAccessFile raf) throws IOException {
            super.write(raf);
            raf.writeLong(lastDataPage);
            raf.writeInt(reserved.size());
            Long l;
            for (Iterator i = reserved.iterator(); i.hasNext();) {
                l = (Long) i.next();
                raf.writeLong(l.longValue());
            }
        }
    }

    /**
     *  Description of the Class
     *
     *@author     wolf
     *@created    3. Juni 2002
     */
    private final class DOMFilePageHeader extends BTreePageHeader {
        protected int dataLen = 0;
        protected long nextDataPage = -1;

        protected short records = 0;

        /**  Constructor for the DOMFilePageHeader object */
        public DOMFilePageHeader() {
            super();
        }

        /**
         *  Constructor for the DOMFilePageHeader object
         *
         *@param  dis              Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public DOMFilePageHeader(DataInputStream dis) throws IOException {
            super(dis);
        }

        /**  Description of the Method */
        public void decRecordCount() {
            records--;
        }

        /**
         *  Gets the dataLength attribute of the DOMFilePageHeader object
         *
         *@return    The dataLength value
         */
        public int getDataLength() {
            return dataLen;
        }

        /**
         *  Gets the nextDataPage attribute of the DOMFilePageHeader object
         *
         *@return    The nextDataPage value
         */
        public long getNextDataPage() {
            return nextDataPage;
        }

        /**
         *  Gets the recordCount attribute of the DOMFilePageHeader object
         *
         *@return    The recordCount value
         */
        public short getRecordCount() {
            return records;
        }

        /**  Description of the Method */
        public void incRecordCount() {
            records++;
        }

        /**
         *  Description of the Method
         *
         *@param  dis              Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public void read(DataInputStream dis) throws IOException {
            super.read(dis);
            records = dis.readShort();
            dataLen = dis.readInt();
            nextDataPage = dis.readLong();
        }

        /**
         *  Sets the dataLength attribute of the DOMFilePageHeader object
         *
         *@param  len  The new dataLength value
         */
        public void setDataLength(int len) {
            dataLen = len;
        }

        /**
         *  Sets the nextDataPage attribute of the DOMFilePageHeader object
         *
         *@param  page  The new nextDataPage value
         */
        public void setNextDataPage(long page) {
            nextDataPage = page;
        }

        /**
         *  Sets the recordCount attribute of the DOMFilePageHeader object
         *
         *@param  recs  The new recordCount value
         */
        public void setRecordCount(short recs) {
            records = recs;
        }

        /**
         *  Description of the Method
         *
         *@param  dos              Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public void write(DataOutputStream dos) throws IOException {
            super.write(dos);
            dos.writeShort(records);
            dos.writeInt(dataLen);
            dos.writeLong(nextDataPage);
        }
    }

    /**
     *  Description of the Class
     *
     *@author     wolf
     *@created    3. Juni 2002
     */
    private final class DOMPage {

        byte[] data;
        int len = 0;
        Page page;
        int refCount = 0;
        boolean saved = true;

        /**  Constructor for the DOMPage object */
        public DOMPage() {
            page = createNewPage();
            data = new byte[fileHeader.getWorkSize()];
            len = 0;
        }

        /**
         *  Constructor for the DOMPage object
         *
         *@param  pos  Description of the Parameter
         */
        public DOMPage(long pos) {
            try {
                page = getPage(pos);
                load(page);
            } catch (IOException ioe) {
                LOG.debug(ioe);
                ioe.printStackTrace();
            }
        }

        /**
         *  Constructor for the DOMPage object
         *
         *@param  page  Description of the Parameter
         */
        public DOMPage(Page page) {
            this.page = page;
            load(page);
        }

        /**  Description of the Method */
        public void decRefCount() {
            refCount--;
        }

        /**
         *  Gets the pageHeader attribute of the DOMPage object
         *
         *@return    The pageHeader value
         */
        public DOMFilePageHeader getPageHeader() {
            return (DOMFilePageHeader) page.getPageHeader();
        }

        /**
         *  Gets the pageNum attribute of the DOMPage object
         *
         *@return    The pageNum value
         */
        public long getPageNum() {
            return page.getPageNum();
        }

        /**
         *  Gets the refCount attribute of the DOMPage object
         *
         *@return    The refCount value
         */
        public int getRefCount() {
            return refCount;
        }

        /**  Description of the Method */
        public void incRefCount() {
            refCount++;
        }

        /**
         *  Gets the dirty attribute of the DOMPage object
         *
         *@return    The dirty value
         */
        public boolean isDirty() {
            return !saved;
        }
        
        public void setDirty(boolean dirty) {
        	saved = !dirty;
        	page.getPageHeader().setDirty(dirty);
        }

        private void load(Page page) {
            try {
                DOMFilePageHeader ph = (DOMFilePageHeader) page.getPageHeader();
                len = ph.getDataLength();
                Value v = readValue(page);
                if (v.getLength() == 0) {
                    LOG.debug("data length == 0");
                    return;
                }
                data = v.getData();
            } catch (IOException ioe) {
                LOG.debug(ioe);
                ioe.printStackTrace();
            }
            saved = true;
        }

        /**  Description of the Method */
        public void write() {
            if (page == null)
                return;
            try {
                DOMFilePageHeader ph = (DOMFilePageHeader) page.getPageHeader();
                if (!ph.isDirty())
                    return;
                ph.setDataLength(len);
                ph.setRecordLen(len);
                Value value = new Value(data);
                writeValue(page, value);
                //page.write();
            } catch (IOException ioe) {
                LOG.error(ioe);
            }
        }
    }

    /**
     *  Cache for data pages. Pages are put on top of a stack. If the stack size
     *  exceeds blockBuffers, the last page in the stack will be removed and
     *  saved to disk. When a page is removed, it's dirty flag is check to
     *  determine if the page needs to be saved. If the page is dirty, the page
     *  is saved.
     *
     *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
     *@created    25. Mai 2002
     */
    protected class ClockPageBuffer {
        protected int blockBuffers;
        protected int hits = 0;
        protected TLongObjectHashMap map;
        protected int misses = 0;

        protected LinkedList queue = new LinkedList();

        /**
         *  Constructor for the PageBuffer object
         *
         *@param  blockBuffers  Description of the Parameter
         */
        public ClockPageBuffer(int blockBuffers) {
            this.blockBuffers = blockBuffers;
            map = new TLongObjectHashMap(blockBuffers);
        }

        /**  Constructor for the PageBuffer object */
        public ClockPageBuffer() {
            this(64);
        }

        /**
         *  Description of the Method
         *
         *@param  page  Description of the Parameter
         */
        public void add(DOMPage page) {
            if (map.containsKey(page.page.getPageNum())) {
                page.incRefCount();
                return;
            }
            while (queue.size() > blockBuffers) {
                boolean removed = false;
                while (!removed) {
                    for (Iterator i = queue.iterator(); i.hasNext();) {
                        DOMPage old = (DOMPage) i.next();
                        old.decRefCount();
                        if (old.getRefCount() < 1 &&
                            old.getPageNum() != page.getPageNum()) {
                            i.remove();
                            map.remove(old.page.getPageNum());
                            if (old.isDirty())
                                old.write();
                            removed = true;
                            break;
                        }
                    }
                }
            }
            queue.add(page);
            map.put(page.page.getPageNum(), page);
        }

        /**  Description of the Method */
        public void flush() {
            DOMPage page;
            for (Iterator i = queue.iterator(); i.hasNext();) {
                page = (DOMPage) i.next();
                if (page.isDirty())
                    page.write();
            }
        }

		public void clear() {
			flush();
			queue.clear();
			map.clear();
		}

        /**
         *  Description of the Method
         *
         *@param  page  Description of the Parameter
         *@return       Description of the Return Value
         */
        public DOMPage get(Page page) {
            return get(page.getPageNum());
        }

        /**
         *  Description of the Method
         *
         *@param  pnum  Description of the Parameter
         *@return       Description of the Return Value
         */
        public DOMPage get(long pnum) {
            DOMPage page = (DOMPage) map.get(pnum);
            if (page == null)
                misses++;
            else
                hits++;
            return page;
        }

        /**
         *  Description of the Method
         *
         *@param  page  Description of the Parameter
         */
        public void remove(DOMPage page) {
            int idx;
            while ((idx = queue.indexOf(page)) > -1)
                queue.remove(idx);
            if (map.remove(page.page.getPageNum()) == null)
                LOG.debug("could not remove page " + page.page.getPageNum());
        }
        
        public void printStatistics() {
			StringBuffer buf = new StringBuffer();
			buf.append("dom.dbx DATA ").append(blockBuffers);
			buf.append(" / ").append(hits);
			buf.append(" / ").append(misses);
			LOG.info(buf.toString());
        }
    }

    /**
     *  Description of the Class
     *
     *@author     wolf
     *@created    3. Juni 2002
     */
    private final class FindCallback implements BTreeCallback {
        /**  Description of the Field */
        public final static int KEYS = 1;

        /**  Description of the Field */
        public final static int VALUES = 0;
        int mode = VALUES;

        ArrayList values = new ArrayList();

        /**
         *  Constructor for the FindCallback object
         *
         *@param  mode  Description of the Parameter
         */
        public FindCallback(int mode) {
            this.mode = mode;
        }

        /**
         *  Gets the values attribute of the FindCallback object
         *
         *@return    The values value
         */
        public ArrayList getValues() {
            return values;
        }

        /**
         *  Description of the Method
         *
         *@param  value    Description of the Parameter
         *@param  pointer  Description of the Parameter
         *@return          Description of the Return Value
         */
        public boolean indexInfo(Value value, long pointer) {
            switch (mode) {
                case VALUES :
                    long pos = (long) pageFromPointer(pointer);
                    int offset = (int) offsetFromPointer(pointer);
                    DOMPage page = getCurrentPage(pos);
                    short l = ByteConversion.byteToShort(page.data, offset);
                    int dataStart = offset + 2;
                    //int l = (int) VariableByteCoding.decode( page.data, offset );
                    //int dataStart = VariableByteCoding.getSize( l );
                    values.add(new Value(page.data, dataStart, l));
                    return true;
                case KEYS :
                    values.add(value);
                    return true;
            }
            return false;
        }
    }

    /**
     *  Description of the Class
     *
     *@author     wolf
     *@created    3. Juni 2002
     */
    private final class RangeCallback implements BTreeCallback {

        ArrayList values = new ArrayList();

        /**  Constructor for the RangeCallback object */
        public RangeCallback() {
        }

        /**
         *  Gets the values attribute of the RangeCallback object
         *
         *@return    The values value
         */
        public ArrayList getValues() {
            return values;
        }

        /**
         *  Description of the Method
         *
         *@param  value    Description of the Parameter
         *@param  pointer  Description of the Parameter
         *@return          Description of the Return Value
         */
        public boolean indexInfo(Value value, long pointer) {
            long pos = (long) pageFromPointer(pointer);
            int offset = (int) offsetFromPointer(pointer);
            DOMPage page = getCurrentPage(pos);
            short l = ByteConversion.byteToShort(page.data, offset);
            int dataStart = offset + 2;
            //int l = (int) VariableByteCoding.decode( page.data, offset );
            //int dataStart = VariableByteCoding.getSize( l ) + offset;
            values.add(new Value(page.data, dataStart, l));
            return true;
        }
    }
}
