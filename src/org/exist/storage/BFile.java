/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001 Wolfgang M. Meier
 *  meier@ifs.tu-darmstadt.de
 *  http://exist.sourceforge.net
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
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 *  $Id:
 */
package org.exist.storage;

import it.unimi.dsi.fastUtil.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastUtil.Long2ObjectOpenHashMap;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

import org.apache.log4j.Category;
import org.dbxml.core.DBException;
import org.dbxml.core.data.Value;
import org.dbxml.core.filer.BTree;
import org.dbxml.core.filer.BTreeCallback;
import org.dbxml.core.filer.BTreeException;
import org.dbxml.core.indexer.IndexQuery;
import org.exist.util.ByteConversion;
import org.exist.util.Lock;
import org.exist.util.OrderedLinkedList;
import org.exist.util.ReadOnlyException;
import org.exist.util.SimpleTimeOutLock;

/**
 *  Data store for variable size values.
 * 
 * This class maps keys to values of variable size. Keys are stored
 * in the b+-tree. B+-tree values are pointers to the logical storage address
 * of the value in the data section. The pointer consists of the page number
 * and a logical tuple identifier. 
 *
 * If a value is larger than the internal page size (4K), it is split into
 * overflow pages. Appending data to a overflow page is very fast.
 * Only the first and the last data page are loaded.
 * 
 * Data pages are buffered.
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    25. Mai 2002
 */
public class BFile extends BTree {
	/**  Description of the Field */
	public final static byte FREE_LIST = 22;

	/**  Description of the Field */
	public final static byte LOB = 21;

	private final static Category LOG =
		Category.getInstance(BFile.class.getName());
	/**  Description of the Field */
	public final static byte MULTI_PAGE = 23;

	// minimum free space a page should have to be
	// considered for reusing
	public final static int PAGE_MIN_FREE = 128;

	// page signatures
	public final static byte RECORD = 20;
	protected boolean compressPages = false;

	protected BFileHeader fileHeader;
	protected int minFree;
	protected ClockPageBuffer pages;
	protected Lock lock = new SimpleTimeOutLock();
	public int fixedKeyLen = -1;

	/**  Constructor for the BFile object */
	public BFile() {
		super();
		fileHeader = (BFileHeader) getFileHeader();
		minFree = PAGE_MIN_FREE;
	}

	/**
	 *  Constructor for the BFile object
	 *
	 *@param  file  Description of the Parameter
	 */
	public BFile(File file) {
		super(file);
		fileHeader = (BFileHeader) getFileHeader();
		//		pages = new LRUPageBuffer();
		pages = new ClockPageBuffer();
		minFree = PAGE_MIN_FREE;
	}

	/**
	 *  Constructor for the BFile object
	 *
	 *@param  file     Description of the Parameter
	 *@param  buffers  Description of the Parameter
	 */
	public BFile(File file, int buffers) {
		super(file, buffers);
		fileHeader = (BFileHeader) getFileHeader();
		//		pages = new LRUPageBuffer(buffers / 2);
		pages = new ClockPageBuffer(buffers);
		minFree = PAGE_MIN_FREE;
	}

	/**
	 *  Constructor for the BFile object
	 *
	 *@param  file          Description of the Parameter
	 *@param  btreeBuffers  Description of the Parameter
	 *@param  dataBuffers   Description of the Parameter
	 */
	public BFile(File file, int btreeBuffers, int dataBuffers) {
		super(file, btreeBuffers);
		fileHeader = (BFileHeader) getFileHeader();
		//		pages = new LRUPageBuffer(dataBuffers);
		pages = new ClockPageBuffer(dataBuffers);
		minFree = PAGE_MIN_FREE;
	}

	private final static long createPointer(int page, int offset) {
		long p = (page & 0xffff);
		long o = (offset & 0xffff);
		return page | (o << 32);
	}

	private final static int offsetFromPointer(long pointer) {
		return (int) ((pointer >>> 32) & 0xffff);
	}

	private final static int pageFromPointer(long pointer) {
		return (int) (pointer & 0xffff);
	}

	/**
	 * Returns the Lock object responsible for this BFile.
	 * 
	 * @return Lock
	 */
	public Lock getLock() {
		return lock;
	}
	public boolean append(Value key, byte[] value) throws ReadOnlyException {
		if (key == null) {
			LOG.debug("key is null");
			return false;
		}
		try {
			try {
				// check if key exists already
				final long p = findValue(key);
				// key exists: get old data
				final long pnum = (long) pageFromPointer(p);
				final short tid = (short) offsetFromPointer(p);
				final DataPage page = getDataPage(pnum);
				if (page instanceof OverflowPage)
					 ((OverflowPage) page).append(value);
				else {
					final byte[] data = page.getData();
					final int offset = findValuePosition(page, tid);
					final int l = ByteConversion.byteToInt(data, offset);
					if (offset + 4 > data.length || offset < 0) {
						LOG.error(
							"wrong pointer (tid: "
								+ tid
								+ page.getPageInfo()
								+ ") in file "
								+ getFile().getName()
								+ "; offset = "
								+ offset);
						return false;
					}
					if (offset + 4 + l > data.length) {
						LOG.error(
							"found invalid data record ("
								+ page.getPageInfo()
								+ "): "
								+ "length="
								+ data.length
								+ "; required="
								+ (offset + 4 + l));
						return false;
					}
					final byte[] newData = new byte[l + value.length];
					System.arraycopy(data, offset + 4, newData, 0, l);
					System.arraycopy(value, 0, newData, l, value.length);
					update(p, page, key, newData);
				}
				return true;
			} catch (BTreeException bte) {
				// key does not exist:
				long p = storeValue(value);
				addValue(key, p);
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		} catch (IOException e) {
			e.printStackTrace();
			LOG.warn(e);
			return false;
		} catch (BTreeException bte) {
			bte.printStackTrace();
			LOG.warn(bte);
			return false;
		}
		return true;
	}

	/**
	 *  Description of the Method
	 *
	 *@return                  Description of the Return Value
	 *@exception  DBException  Description of the Exception
	 */
	public boolean close() throws DBException {
		super.close();
		return true;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  key  Description of the Parameter
	 *@return      Description of the Return Value
	 */
	public boolean containsKey(Value key) {
		try {
			findValue(key);
			return true;
		} catch (BTreeException bte) {
		} catch (IOException ioe) {
		}
		return false;
	}

	/**
	 *  Description of the Method
	 *
	 *@return                  Description of the Return Value
	 *@exception  DBException  Description of the Exception
	 */
	public boolean create() throws DBException {
		if (super.create((short) fixedKeyLen)) {
			fileHeader.setLastDataPage(-1);
			return true;
		} else
			return false;
	}

	private SinglePage createDataPage() {
		try {
			SinglePage page = new SinglePage(compressPages);
			pages.add(page);
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
	public FileHeader createFileHeader() {
		return new BFileHeader(PAGE_SIZE);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  read             Description of the Parameter
	 *@return                  Description of the Return Value
	 *@exception  IOException  Description of the Exception
	 */
	public FileHeader createFileHeader(boolean read) throws IOException {
		return new BFileHeader(PAGE_SIZE);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  pageCount  Description of the Parameter
	 *@return            Description of the Return Value
	 */
	public FileHeader createFileHeader(long pageCount) {
		return new BFileHeader(PAGE_SIZE);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  pageCount  Description of the Parameter
	 *@param  pageSize   Description of the Parameter
	 *@return            Description of the Return Value
	 */
	public FileHeader createFileHeader(long pageCount, int pageSize) {
		return new BFileHeader(pageSize);
	}

	/**
	 *  Description of the Method
	 *
	 *@return    Description of the Return Value
	 */
	public PageHeader createPageHeader() {
		return new BFilePageHeader();
	}

	/**
	 *  Description of the Method
	 *
	 *@param  query               Description of the Parameter
	 *@param  callback            Description of the Parameter
	 *@exception  IOException     Description of the Exception
	 *@exception  BTreeException  Description of the Exception
	 */
	public void filter(IndexQuery query, BFileCallback callback)
		throws IOException, BTreeException {
		FilterCallback cb = new FilterCallback(callback);
		query(query, cb);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  query               Description of the Parameter
	 *@return                     Description of the Return Value
	 *@exception  IOException     Description of the Exception
	 *@exception  BTreeException  Description of the Exception
	 */
	public ArrayList findEntries(IndexQuery query)
		throws IOException, BTreeException {
		FindCallback cb = new FindCallback(FindCallback.BOTH);
		query(query, cb);
		return cb.getValues();
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

	private final int findValuePosition(DataPage page, short tid) {
		int pos = 0;
		int l;
		short current = -1;
		byte[] data = page.getData();
		while (pos < page.getPageHeader().getDataLength()) {
			current = ByteConversion.byteToShort(data, pos);
			if (current == tid)
				return pos + 2;
			l = ByteConversion.byteToInt(data, pos + 2);
			pos = pos + l + 6;
		}
		LOG.debug(
			"tid "
				+ tid
				+ " not found. "
				+ page.getPageInfo()
				+ "; pos: "
				+ pos
				+ "; last-tid: "
				+ current);
		return -1;
	}

	public ArrayList findValues(IndexQuery query)
		throws IOException, BTreeException {
		FindCallback cb = new FindCallback(FindCallback.VALUES);
		query(query, cb);
		return cb.getValues();
	}

	public boolean flush() throws DBException {
		pages.flush();
		super.flush();
		return true;
	}

	public void printStatistics() {
		super.printStatistics();
		StringBuffer buf = new StringBuffer();
		buf.append(getFile().getName()).append(" DATA ");
		buf.append(pages.getBuffers()).append(" / ");
		buf.append(pages.getUsedBuffers()).append(" / ");
		buf.append(pages.getHits()).append(" / ");
		buf.append(pages.getFails());
		LOG.info(buf.toString());
	}

	public Value get(Value key) {
		try {
			long p = findValue(key);
			long pnum = (long) pageFromPointer(p);
			DataPage page = getDataPage(pnum);
			return get(page, p);
		} catch (BTreeException b) {
		} catch (IOException e) {
			LOG.debug(e);
		}
		return null;
	}

	public int getValueSize(Value key) {
		try {
			long p = findValue(key);
			long pnum = (long) pageFromPointer(p);
			DataPage page = getDataPage(pnum);
			if (page.getPageHeader().getStatus() == MULTI_PAGE)
				return page.getPageHeader().getDataLength();
			else {
				short tid = (short) offsetFromPointer(p);
				int offset = findValuePosition(page, tid);
				byte[] data = page.getData();
				return ByteConversion.byteToInt(data, offset);
			}
		} catch (BTreeException b) {
		} catch (IOException e) {
			LOG.debug(e);
		}
		return -1;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  p  Description of the Parameter
	 *@return    Description of the Return Value
	 */
	public Value get(long p) {
		try {
			long pnum = (long) pageFromPointer(p);
			DataPage page = getDataPage(pnum);
			return get(page, p);
		} catch (BTreeException b) {
		} catch (IOException e) {
			LOG.debug(e);
		}
		return null;
	}

	/**
	 *  Retrieve value at logical address p from page
	 *
	 *@param  page                Description of the Parameter
	 *@param  p                   Description of the Parameter
	 *@return                     Description of the Return Value
	 *@exception  BTreeException  Description of the Exception
	 *@exception  IOException     Description of the Exception
	 */
	protected Value get(DataPage page, long p)
		throws BTreeException, IOException {
		short tid;
		int offset;
		byte[] data;
		int l;
		tid = (short) offsetFromPointer(p);
		offset = findValuePosition(page, tid);
		data = page.getData();
		if (offset > data.length || offset < 0) {
			LOG.error(
				"wrong pointer (tid: "
					+ tid
					+ page.getPageInfo()
					+ ") in file "
					+ getFile().getName()
					+ "; offset = "
					+ offset);
			return null;
		}
		l = ByteConversion.byteToInt(data, offset);
		if (l + 6 > data.length) {
			LOG.error(
				getFile().getName()
					+ " wrong data length in page "
					+ page.getPageNum()
					+ ": expected="
					+ (l + 6)
					+ "; found="
					+ data.length);
			return null;
		}
		pages.add(page);
		return new Value(data, offset + 4, l);
	}

	private DataPage getDataPage(long pos) {
		try {
			DataPage wp;
			if ((wp = pages.get(pos)) == null) {
				final Page page = getPage(pos);
				final byte[] data = page.read();
				if (page == null) {
					LOG.debug("page " + pos + " not found!");
					return null;
				}
				if (page.getPageHeader().getStatus() == MULTI_PAGE)
					return new OverflowPage(page, data, compressPages);
				else
					return new SinglePage(page, data, compressPages);
			} else if (wp.getPageHeader().getStatus() == MULTI_PAGE)
				return new OverflowPage(wp);
			else
				return wp;
		} catch (IOException ioe) {
			LOG.debug("cannot read page: " + pos + ": " + ioe);
			ioe.printStackTrace();
			return null;
		}
	}

	/**
	 *  Gets the entries attribute of the BFile object
	 *
	 *@return                     The entries value
	 *@exception  IOException     Description of the Exception
	 *@exception  BTreeException  Description of the Exception
	 */
	public ArrayList getEntries() throws IOException, BTreeException {
		IndexQuery query = new IndexQuery(null, IndexQuery.ANY, "");
		FindCallback cb = new FindCallback(FindCallback.BOTH);
		query(query, cb);
		return cb.getValues();
	}

	/**
	 *  Gets the keys attribute of the BFile object
	 *
	 *@return                     The keys value
	 *@exception  IOException     Description of the Exception
	 *@exception  BTreeException  Description of the Exception
	 */
	public ArrayList getKeys() throws IOException, BTreeException {
		IndexQuery query = new IndexQuery(null, IndexQuery.ANY, "");
		FindCallback cb = new FindCallback(FindCallback.KEYS);
		query(query, cb);
		return cb.getValues();
	}

	/**
	 *  Gets the values attribute of the BFile object
	 *
	 *@return                     The values value
	 *@exception  IOException     Description of the Exception
	 *@exception  BTreeException  Description of the Exception
	 */
	public ArrayList getValues() throws IOException, BTreeException {
		IndexQuery query = new IndexQuery(null, IndexQuery.ANY, "");
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
	public boolean open() throws DBException {
		return super.open();
	}

	/**
	 *  Description of the Method
	 *
	 *@param  key    Description of the Parameter
	 *@param  value  Description of the Parameter
	 *@return        Description of the Return Value
	 */
	public boolean put(Value key, byte[] value) throws ReadOnlyException {
		return put(key, value, true);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  key        Description of the Parameter
	 *@param  value      Description of the Parameter
	 *@param  overwrite  Description of the Parameter
	 *@return            Description of the Return Value
	 */
	public boolean put(Value key, byte[] value, boolean overwrite)
		throws ReadOnlyException {
		if (key == null) {
			LOG.debug("key is null");
			return false;
		}
		try {
			try {
				// check if key exists already
				long p = findValue(key);
				// if exists, update value
				if (overwrite) {
					return update(p, key, value);
				} else
					return false;
			} catch (BTreeException bte) {
				// key does not exist:
				long p = storeValue(value);
				addValue(key, p);
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		} catch (IOException e) {
			e.printStackTrace();
			LOG.warn(e);
			return false;
		} catch (BTreeException bte) {
			bte.printStackTrace();
			LOG.warn(bte);
			return false;
		}
		return true;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  key  Description of the Parameter
	 */
	public void remove(Value key) throws ReadOnlyException {
		try {
			long p = findValue(key);
			long pos = (long) pageFromPointer(p);
			DataPage page = getDataPage(pos);
			remove(page, p);

			removeValue(key);
		} catch (BTreeException bte) {
			LOG.debug(bte);
		} catch (IOException ioe) {
			LOG.debug(ioe);
		}
	}

	public void remove(long p) throws ReadOnlyException {
		try {
			long pos = (long) pageFromPointer(p);
			DataPage page = getDataPage(pos);
			remove(page, p);
		} catch (BTreeException e) {
			LOG.debug("btree problem", e);
		} catch (IOException e) {
			LOG.debug("io problem", e);
		}
	}

	/**
	 *  Description of the Method
	 *
	 *@param  page                Description of the Parameter
	 *@param  p                   Description of the Parameter
	 *@exception  BTreeException  Description of the Exception
	 *@exception  IOException     Description of the Exception
	 */
	protected void remove(DataPage page, long p)
		throws BTreeException, IOException, ReadOnlyException {
		if (page.getPageHeader().getStatus() == MULTI_PAGE) {
			// overflow page: simply delete the whole page
			page.delete();
			pages.remove(page);
			return;
		}
		short tid = (short) offsetFromPointer(p);
		int offset = findValuePosition(page, tid);
		byte[] data = page.getData();
		if (offset > data.length || offset < 0) {
			LOG.error(
				"wrong pointer (tid: " + tid + ", " + page.getPageInfo() + ")");
			return;
		}
		int l = ByteConversion.byteToInt(data, offset);
		int end = offset + 4 + l;
		int len = page.getPageHeader().getDataLength();
		// remove old value
		System.arraycopy(data, end, data, offset - 2, len - end);
		page.getPageHeader().setDirty(true);
		page.getPageHeader().decRecordCount();
		len = len - l - 6;
		page.getPageHeader().setDataLength(len);
		page.setDirty(true);

		// adjust free space data
		FreeSpace free = fileHeader.getFreeSpace(page.getPageNum());
		if (free == null)
			free =
				new FreeSpace(
					page.getPageNum(),
					fileHeader.getWorkSize() - len);
		free.setFree(fileHeader.getWorkSize() - len);
		fileHeader.removeFreeSpace(free);
		if (len == 0) {
			// if this page is empty, remove it
			pages.remove(page);
			page.delete();
		} else {
			fileHeader.addFreeSpace(free);
			pages.add(page);
		}
	}

	private final void saveFreeSpace(FreeSpace space, DataPage page) {
		int free =
			fileHeader.getWorkSize() - page.getPageHeader().getDataLength();
		space.setFree(free);
		fileHeader.removeFreeSpace(space);
		if (free > minFree)
			fileHeader.addFreeSpace(space);
	}

	/**
	 *  Sets the compression attribute of the BFile object
	 *
	 *@param  compress  The new compression value
	 */
	public void setCompression(boolean compress) {
		compressPages = compress;
	}

	/**
	 *  Sets the location attribute of the BFile object
	 *
	 *@param  location  The new location value
	 */
	public void setLocation(String location) {
		setFile(new File(location + ".dbx"));
	}

	private long storeValue(byte[] value)
		throws IOException, ReadOnlyException {
		// does value fit into a single page?
		if (6 + value.length > fileHeader.getWorkSize()) {
			OverflowPage page = new OverflowPage();
			byte[] data = new byte[value.length + 6];
			page.getPageHeader().setDataLength(value.length + 6);
			ByteConversion.shortToByte((short) 1, data, 0);
			ByteConversion.intToByte(value.length, data, 2);
			System.arraycopy(value, 0, data, 6, value.length);
			page.setData(data);
			page.getPageHeader().setDirty(true);
			page.setDirty(true);
			//pages.add( page );
			return createPointer((int) page.getPageNum(), 1);
		}
		DataPage page = null;
		short tid = -1;
		FreeSpace free = null;
		int realSpace = 0;
		// check for available tid
		while (tid < 0) {
			free = fileHeader.findFreeSpace(value.length + 6);
			if (free == null) {
				page = createDataPage();
				page.setData(new byte[fileHeader.getWorkSize()]);
				free =
					new FreeSpace(
						page.getPageNum(),
						fileHeader.getWorkSize()
							- page.getPageHeader().getDataLength());
			} else {
				page = getDataPage(free.getPage());
				// check if the information about free space is really correct
				realSpace =
					fileHeader.getWorkSize()
						- page.getPageHeader().getDataLength();
				if (realSpace < 6 + value.length) {
					// not correct: adjust and continue
					LOG.debug(
						"wrong data length in list of free pages: adjusting ...");
					free.setFree(realSpace);
					fileHeader.removeFreeSpace(free);
					fileHeader.addFreeSpace(free);
					continue;
				}
			}
			tid = page.getPageHeader().getNextTID();
			if (tid < 0)
				fileHeader.removeFreeSpace(free);
		}
		// create pointer from pageNum and offset into page
		long p = createPointer((int) page.getPageNum(), (int) tid);
		int len = page.getPageHeader().getDataLength();
		byte[] data = page.getData();

		// save tid
		ByteConversion.shortToByte(tid, data, len);
		len += 2;
		// save data length
		ByteConversion.intToByte(value.length, data, len);
		len += 4;
		// save data
		System.arraycopy(value, 0, data, len, value.length);
		len += value.length;
		page.getPageHeader().setDataLength(len);
		page.getPageHeader().incRecordCount();
		saveFreeSpace(free, page);
		page.setDirty(true);
		pages.add(page, 2);
		return p;
	}

	/**
	 *  Update a key/value pair.
	 *
	 *@param  key    Description of the Parameter
	 *@param  value  Description of the Parameter
	 *@return        Description of the Return Value
	 */
	public boolean update(Value key, byte[] value) throws ReadOnlyException {
		try {
			long p = findValue(key);
			return update(p, key, value);
		} catch (BTreeException bte) {
			LOG.debug(bte);
		} catch (IOException ioe) {
			LOG.debug(ioe);
		}
		return false;
	}

	/**
	 *  Update the key/value pair found at the logical address p.
	 *
	 *@param  p      Description of the Parameter
	 *@param  key    Description of the Parameter
	 *@param  value  Description of the Parameter
	 *@return        Description of the Return Value
	 */
	protected boolean update(long p, Value key, byte[] value)
		throws ReadOnlyException {
		try {
			long pos = (long) pageFromPointer(p);
			DataPage page = getDataPage(pos);
			update(p, page, key, value);
			return true;
		} catch (BTreeException bte) {
			LOG.debug(bte);
			return false;
		} catch (IOException ioe) {
			LOG.debug(ioe);
			return false;
		}
	}

	/**
	 *  Update the key/value pair with logical address p and
	 *  stored in page.
	 *
	 *@param  p                   Description of the Parameter
	 *@param  page                Description of the Parameter
	 *@param  key                 Description of the Parameter
	 *@param  value               Description of the Parameter
	 *@exception  BTreeException  Description of the Exception
	 *@exception  IOException     Description of the Exception
	 */
	protected void update(long p, DataPage page, Value key, byte[] value)
		throws BTreeException, IOException, ReadOnlyException {
		if (page.getPageHeader().getStatus() == MULTI_PAGE) {
			// does value fit into a single page?
			if (value.length + 6 < fileHeader.getWorkSize()) {
				// yes: remove the overflow page
				remove(page, p);
				long np = storeValue(value);
				addValue(key, np);
			} else {
				// this is an overflow page: simply replace the value
				byte[] data = new byte[value.length + 6];
				// save tid
				ByteConversion.shortToByte((short) 1, data, 0);

				// save length
				ByteConversion.intToByte(value.length, data, 2);
				// save data
				System.arraycopy(value, 0, data, 6, value.length);
				page.setData(data);
			}
		} else {
			remove(page, p);
			long np = storeValue(value);
			addValue(key, np);
		}
	}

	/**
	 *  Description of the Class
	 *
	 *@author     wolf
	 *@created    28. Mai 2002
	 */
	private final static class FreeSpace implements Comparable {
		private int free = 0;

		private long page = -1;

		/**
		 *  Constructor for the FreeSpace object
		 *
		 *@param  pageNum  Description of the Parameter
		 *@param  space    Description of the Parameter
		 */
		public FreeSpace(long pageNum, int space) {
			page = pageNum;
			free = space;
		}

		/**
		 *  Description of the Method
		 *
		 *@param  obj  Description of the Parameter
		 *@return      Description of the Return Value
		 */
		public int compareTo(Object obj) {
			FreeSpace other = (FreeSpace) obj;
			if (free < other.free)
				return -1;
			else if (free > other.free)
				return 1;
			else
				return 0;
		}

		/**
		 *  Gets the free attribute of the FreeSpace object
		 *
		 *@return    The free value
		 */
		public int getFree() {
			return free;
		}

		/**
		 *  Gets the page attribute of the FreeSpace object
		 *
		 *@return    The page value
		 */
		public long getPage() {
			return page;
		}

		/**
		 *  Sets the free attribute of the FreeSpace object
		 *
		 *@param  space  The new free value
		 */
		public void setFree(int space) {
			free = space;
		}

		/**
		 *  Description of the Method
		 *
		 *@return    Description of the Return Value
		 */
		public String toString() {
			return Integer.toString(free);
		}
	}

	/**
	 *  Description of the Class
	 *
	 *@author     wolf
	 *@created    28. Mai 2002
	 */
	private final class BFileHeader extends BTreeFileHeader {
		private OrderedLinkedList freeList = new OrderedLinkedList();
		private long freeSpacePage = -1;
		private long lastDataPage = -1;
        
        public final static int MAX_FREE_LIST_LEN = 128;

		/**  Constructor for the BFileHeader object */
		public BFileHeader(int pageSize) {
			super();
		}

		/**
		 *  Adds a feature to the FreeSpace attribute of the BFileHeader object
		 *
		 *@param  freeSpace  The feature to be added to the FreeSpace attribute
		 */
		public void addFreeSpace(FreeSpace freeSpace) {
			freeList.add(freeSpace);
		}

		/**
		 *  Description of the Method
		 *
		 *@param  needed  Description of the Parameter
		 *@return         Description of the Return Value
		 */
		public FreeSpace findFreeSpace(int needed) {
			FreeSpace freeSpace;
			for (Iterator i = freeList.iterator(); i.hasNext();) {
				freeSpace = (FreeSpace) i.next();
				if (freeSpace.getFree() >= needed)
					return freeSpace;
			}
			return null;
		}

		/**
		 *  Gets the freeSpace attribute of the BFileHeader object
		 *
		 *@param  page  Description of the Parameter
		 *@return       The freeSpace value
		 */
		public FreeSpace getFreeSpace(long page) {
			FreeSpace freeSpace;
			for (Iterator i = freeList.iterator(); i.hasNext();) {
				freeSpace = (FreeSpace) i.next();
				if (freeSpace.getPage() == page)
					return freeSpace;
			}
			return null;
		}

		/**
		 *  Gets the lastDataPage attribute of the BFileHeader object
		 *
		 *@return    The lastDataPage value
		 */
		public long getLastDataPage() {
			return lastDataPage;
		}

		/**
		 *  Gets the maxFreeSpace attribute of the BFileHeader object
		 *
		 *@return    The maxFreeSpace value
		 */
		public FreeSpace getMaxFreeSpace() {
			FreeSpace space;
			FreeSpace max = null;
			for (Iterator i = freeList.iterator(); i.hasNext();) {
				space = (FreeSpace) i.next();
				if (max == null || max.getFree() < space.getFree())
					max = space;
			}
			return max;
		}

		/**  Description of the Method */
		public void printFreeSpace() {
			System.out.print(getFile().getName() + ": ");
			FreeSpace freeSpace;
			for (Iterator i = freeList.iterator(); i.hasNext();) {
				freeSpace = (FreeSpace) i.next();
				System.out.print(
					"["
						+ freeSpace.getPage()
						+ ", "
						+ freeSpace.getFree()
						+ "] ");
			}
			System.out.println();
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
			final int fsize = raf.readInt();
			long page;
			int space;
			for(int i = 0; i < fsize; i++) { 
				page = raf.readLong();
				space = raf.readInt();
				freeList.add(new FreeSpace(page, space));
			}
		}

		/**
		 *  Description of the Method
		 *
		 *@param  space  Description of the Parameter
		 */
		public void removeFreeSpace(FreeSpace space) {
			freeList.remove(space);
		}

		/**
		 *  Sets the lastDataPage attribute of the BFileHeader object
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
            // does the free-space list fit into the file header?
            if(freeList.size() > MAX_FREE_LIST_LEN) {
                // no: remove some smaller entries to make it fit
                for(int i = 0; i < freeList.size() - MAX_FREE_LIST_LEN; i++)
                    freeList.removeFirst();
            }
            super.write(raf);
			raf.writeLong(lastDataPage);
			raf.writeInt(freeList.size());
			FreeSpace freeSpace;
			for (Iterator i = freeList.iterator(); i.hasNext();) {
				freeSpace = (FreeSpace) i.next();
				raf.writeLong(freeSpace.getPage());
				raf.writeInt(freeSpace.getFree());
			}
		}
	}

	/**
	 *  Description of the Class
	 *
	 *@author     wolf
	 *@created    28. Mai 2002
	 */
	private final class BFilePageHeader extends BTreePageHeader {
		private int dataLen = 0;
		private long lastInChain = -1L;
		private long nextInChain = -1L;

		// tuple identifier: used to identify distinct
		// values inside a page
		private short nextTID = -1;

		private short records = 0;

		/**  Constructor for the BFilePageHeader object */
		public BFilePageHeader() {
			super();
		}

		/**
		 *  Constructor for the BFilePageHeader object
		 *
		 *@param  dis              Description of the Parameter
		 *@exception  IOException  Description of the Exception
		 */
		public BFilePageHeader(DataInputStream dis) throws IOException {
			super(dis);
		}

		/**  Description of the Method */
		public void decRecordCount() {
			records--;
		}

		/**
		 *  Gets the dataLength attribute of the BFilePageHeader object
		 *
		 *@return    The dataLength value
		 */
		public int getDataLength() {
			return dataLen;
		}

		/**
		 *  Gets the lastChunk attribute of the BFilePageHeader object
		 *
		 *@return    The lastChunk value
		 */
		public long getLastInChain() {
			return lastInChain;
		}

		/**
		 *  Gets the nextPageBlock attribute of the BFilePageHeader object
		 *
		 *@return    The nextPageBlock value
		 */
		public long getNextInChain() {
			return nextInChain;
		}

		/**
		 *  Gets the nextTID attribute of the BFilePageHeader object
		 *
		 *@return    The nextTID value
		 */
		public short getNextTID() {
			++nextTID;
			if (nextTID > Short.MAX_VALUE) {
				LOG.debug("tid limit reached");
				return -1;
			}
			return nextTID;
		}

		/**
		 *  Gets the recordCount attribute of the BFilePageHeader object
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
			nextTID = dis.readShort();
			nextInChain = dis.readLong();
			lastInChain = dis.readLong();
		}

		/**
		 *  Sets the dataLength attribute of the BFilePageHeader object
		 *
		 *@param  len  The new dataLength value
		 */
		public void setDataLength(int len) {
			dataLen = len;
		}

		/**
		 *  Sets the lastChunk attribute of the BFilePageHeader object
		 *
		 *@param  p  The new lastChunk value
		 */
		public void setLastInChain(long p) {
			lastInChain = p;
		}

		/**
		 *  Sets the nextPageBlock attribute of the BFilePageHeader object
		 *
		 *@param  b  The new nextPageBlock value
		 */
		public void setNextInChain(long b) {
			nextInChain = b;
		}

		/**
		 *  Sets the recordCount attribute of the BFilePageHeader object
		 *
		 *@param  recs  The new recordCount value
		 */
		public void setRecordCount(short recs) {
			records = recs;
		}

		/**
		 *  Sets the tID attribute of the BFilePageHeader object
		 *
		 *@param  tid  The new tID value
		 */
		public void setTID(short tid) {
			this.nextTID = tid;
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
			dos.writeShort(nextTID);
			dos.writeLong(nextInChain);
			dos.writeLong(lastInChain);
		}
	}

	private abstract class DataPage implements Comparable {
		int refCount = 0;
		boolean saved = true;

		/**  Description of the Method */
		public void decRefCount() {
			refCount--;
		}

		/**
		 *  Description of the Method
		 *
		 *@exception  IOException  Description of the Exception
		 */
		public abstract void delete() throws IOException;

		/**
		 *  Gets the data attribute of the DataPage object
		 *
		 *@return    The data value
		 */
		public abstract byte[] getData();

		/**
		 *  Gets the pageHeader attribute of the DataPage object
		 *
		 *@return    The pageHeader value
		 */
		public abstract BFilePageHeader getPageHeader();

		/**
		 *  Gets the pageInfo attribute of the DataPage object
		 *
		 *@return    The pageInfo value
		 */
		public abstract String getPageInfo();

		/**
		 *  Gets the pageNum attribute of the DataPage object
		 *
		 *@return    The pageNum value
		 */
		public abstract long getPageNum();

		/**
		 *  Gets the refCount attribute of the DataPage object
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

		public void setRefCount(int count) {
			refCount = count;
		}

		/**
		 *  Gets the dirty attribute of the DataPage object
		 *
		 *@return    The dirty value
		 */
		public boolean isDirty() {
			return !saved;
		}

		/**
		 *  Sets the data attribute of the DataPage object
		 *
		 *@param  buf  The new data value
		 */
		public abstract void setData(byte[] buf);

		/**
		 *  Sets the dirty attribute of the DataPage object
		 *
		 *@param  dirty  The new dirty value
		 */
		public void setDirty(boolean dirty) {
			saved = !dirty;
		}

		/**
		 *  Description of the Method
		 *
		 *@exception  IOException  Description of the Exception
		 */
		public abstract void write() throws IOException;

		public int compareTo(Object other) {
			if (getPageNum() == ((DataPage) other).getPageNum())
				return 0;
			else if (getPageNum() > ((DataPage) other).getPageNum())
				return 1;
			else
				return -1;
		}
	}

	/**
	 *  Description of the Class
	 *
	 *@author     wolf
	 *@created    28. Mai 2002
	 */
	private final class FilterCallback implements BTreeCallback {

		BFileCallback callback;

		/**
		 *  Constructor for the FilterCallback object
		 *
		 *@param  callback  Description of the Parameter
		 */
		public FilterCallback(BFileCallback callback) {
			this.callback = callback;
		}

		/**
		 *  Description of the Method
		 *
		 *@param  value    Description of the Parameter
		 *@param  pointer  Description of the Parameter
		 *@return          Description of the Return Value
		 */
		public boolean indexInfo(Value value, long pointer) {
			long pos;
			short tid;
			DataPage page;
			int offset;
			int l;
			Value v;
			pos = (long) pageFromPointer(pointer);
			tid = (short) offsetFromPointer(pointer);
			page = getDataPage(pos);
			offset = findValuePosition(page, tid);
			byte[] data = page.getData();
			l = ByteConversion.byteToInt(data, offset);
			v = new Value(data, offset + 4, l);
			callback.info(value, v);
			return true;
		}
	}

	/**
	 *  Description of the Class
	 *
	 *@author     wolf
	 *@created    28. Mai 2002
	 */
	private final class FindCallback implements BTreeCallback {
		/**  Description of the Field */
		public final static int BOTH = 2;
		/**  Description of the Field */
		public final static int KEYS = 1;

		/**  Description of the Field */
		public final static int VALUES = 0;
		int mode = VALUES;

		private ArrayList values = new ArrayList();

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
			long pos;
			short tid;
			DataPage page;
			int offset;
			int l;
			Value v;
			byte[] data;
			switch (mode) {
				case VALUES :
					pos = (long) pageFromPointer(pointer);
					tid = (short) offsetFromPointer(pointer);
					page = getDataPage(pos);
					pages.add(page);
					offset = findValuePosition(page, tid);
					data = page.getData();
					l = ByteConversion.byteToInt(data, offset);
					v = new Value(data, offset + 4, l);
					v.setAddress(pointer);
					values.add(v);
					return true;
				case KEYS :
					value.setAddress(pointer);
					values.add(value);
					return true;
				case BOTH :
					Value[] entry = new Value[2];
					entry[0] = value;
					pos = (long) pageFromPointer(pointer);
					tid = (short) offsetFromPointer(pointer);
					page = getDataPage(pos);
					pages.add(page);
					offset = findValuePosition(page, tid);
					data = page.getData();
					l = ByteConversion.byteToInt(data, offset);
					v = new Value(data, offset + 4, l);
					v.setAddress(pointer);
					entry[1] = v;
					values.add(entry);
					return true;
			}
			return false;
		}
	}

	private final class OverflowPage extends DataPage {
		byte[] data = null;
		SinglePage firstPage;

		/**
		 *  Constructor for the SinglePage object
		 *
		 *@exception  IOException  Description of the Exception
		 */
		public OverflowPage() throws IOException {
			firstPage = new SinglePage(false);
			BFilePageHeader ph = firstPage.getPageHeader();
			ph.setStatus(MULTI_PAGE);
			ph.setNextInChain(0L);
			ph.setLastInChain(0L);
			ph.setDataLength(0);
			firstPage.setData(new byte[fileHeader.getWorkSize()]);
			pages.add(firstPage, 3);
		}

		/**
		 *  Constructor for the OverflowPage object
		 *
		 *@param  page  Description of the Parameter
		 */
		public OverflowPage(DataPage page) {
			firstPage = (SinglePage) page;
		}

		/**
		 *  Constructor for the SinglePage object
		 *
		 *@param  p                Description of the Parameter
		 *@param  compress         Description of the Parameter
		 *@exception  IOException  Description of the Exception
		 */
		public OverflowPage(Page p, byte[] data, boolean compress)
			throws IOException {
			firstPage = new SinglePage(p, data, compress);
			firstPage.getPageHeader().setStatus(MULTI_PAGE);
		}

		/**
		 *  Append a new chunk of data to the page
		 *
		 *@param  chunk  chunk of data to append
		 */
		public void append(byte[] chunk) {
			SinglePage nextPage;
			BFilePageHeader ph = firstPage.getPageHeader();
			// get the last page and fill it
			long next = ph.getLastInChain();
			DataPage page;
			if (next > 0)
				page = (DataPage) getDataPage(next);
			else
				page = firstPage;
			ph = page.getPageHeader();
			int chunkSize = fileHeader.getWorkSize() - ph.getDataLength();
			if (chunk.length < chunkSize)
				chunkSize = chunk.length;
			// fill last page
			System.arraycopy(
				chunk,
				0,
				page.getData(),
				ph.getDataLength(),
				chunkSize);
			ph.setDataLength(ph.getDataLength() + chunkSize);
			page.setDirty(true);
			// write the remaining chunks to new pages
			int remaining = chunk.length - chunkSize;
			int current = chunkSize;
			chunkSize = fileHeader.getWorkSize();
			if (remaining > 0) {
				// walk through chain of pages
				while (remaining > 0) {
					// add a new page to the chain
					nextPage = (SinglePage) createDataPage();
					nextPage.setData(new byte[fileHeader.getWorkSize()]);
					page.getPageHeader().setNextInChain(nextPage.getPageNum());
					page.setDirty(true);

					pages.add(page);
					page = nextPage;
					if (remaining < chunkSize)
						chunkSize = remaining;
					// copy next chunk of data to the page
					System.arraycopy(
						chunk,
						current,
						page.getData(),
						0,
						chunkSize);
					page.setDirty(true);
					if (page != firstPage)
						page.getPageHeader().setDataLength(chunkSize);
					remaining = remaining - chunkSize;
					current += chunkSize;
				}
			}
			ph = firstPage.getPageHeader();
			if (page != firstPage) {
				// add link to last page
				pages.add(page, 2);
				ph.setLastInChain(page.getPageNum());
				ph.setDataLength(ph.getDataLength() + chunk.length);
			} else
				ph.setLastInChain(0L);
			// adjust length field in first page
			ByteConversion.intToByte(
				firstPage.getPageHeader().getDataLength() - 6,
				firstPage.getData(),
				2);
			firstPage.setDirty(true);
			pages.add(firstPage, 3);
		}

		/**
		 *  Description of the Method
		 *
		 *@exception  IOException  Description of the Exception
		 */
		public void delete() throws IOException {
			long next = firstPage.getPageNum();
			SinglePage page = firstPage;
			do {
				next = page.getPageHeader().getNextInChain();
				page.getPageHeader().setNextInChain(-1L);
				page.setDirty(true);
				pages.remove(page);
				page.delete();
				if (next > 0)
					page = (SinglePage) getDataPage(next);
			} while (next > 0);
		}

		/**
		 *  Gets the data attribute of the OverflowPage object
		 *
		 *@return    The data value
		 */
		public byte[] getData() {
			if (data != null)
				return data;
			SinglePage page = firstPage;
			Value v;
			long pnum;
			long next;
			byte[] temp;
			int len;
			ByteArrayOutputStream os =
				new ByteArrayOutputStream(page.getPageHeader().getDataLength());
			do {
				temp = page.getData();
				next = page.getPageHeader().getNextInChain();
				len =
					next > 0
						? fileHeader.getWorkSize()
						: page.getPageHeader().getDataLength();
				os.write(temp, 0, len);

				if (next > 0) {
					page = (SinglePage) getDataPage(next);
					pages.add(page);
				}
			} while (next > 0);
			data = os.toByteArray();
			if (data.length != firstPage.getPageHeader().getDataLength()) {
				LOG.warn(
					getFile().getName()
						+ " read="
						+ data.length
						+ "; expected="
						+ firstPage.getPageHeader().getDataLength());
				//                firstPage.getPageHeader().setDataLength( data.length );
				//                firstPage.setDirty(true);
				//                throw new RuntimeException(getFile().getName() + " data page corrupt!");
			}
			return data;
		}

		/**
		 *  Gets the firstPage attribute of the OverflowPage object
		 *
		 *@return    The firstPage value
		 */
		public SinglePage getFirstPage() {
			return firstPage;
		}

		/**
		 *  Gets the pageHeader attribute of the OverflowPage object
		 *
		 *@return    The pageHeader value
		 */
		public BFilePageHeader getPageHeader() {
			return firstPage.getPageHeader();
		}

		/**
		 *  Gets the pageInfo attribute of the OverflowPage object
		 *
		 *@return    The pageInfo value
		 */
		public String getPageInfo() {
			return "MULTI_PAGE: " + firstPage.getPageInfo();
		}

		/**
		 *  Gets the pageNum attribute of the SinglePage object
		 *
		 *@return    The pageNum value
		 */
		public long getPageNum() {
			return firstPage.getPageNum();
		}

		/**
		 *  Sets the data attribute of the OverflowPage object
		 *
		 *@param  data  The new data value
		 */
		public void setData(byte[] data) {
			this.data = data;
			try {
				write();
			} catch (IOException e) {
				LOG.warn(e);
			}
		}

		/**
		 *  Description of the Method
		 *
		 *@exception  IOException  Description of the Exception
		 */
		public void write() throws IOException {
			if (data == null)
				return;
			int chunkSize = fileHeader.getWorkSize();
			int remaining = data.length;
			int current = 0;
			long next = 0L;
			byte[] chunk;
			SinglePage page = firstPage;
			page.getPageHeader().setDataLength(remaining);
			SinglePage nextPage;
			// walk through chain of pages
			while (remaining > 0) {
				if (remaining < chunkSize)
					chunkSize = remaining;
				// copy next chunk of data to the page
				//if(!Arrays.equals(data, current, page.getData(), 0, chunkSize)) {
				System.arraycopy(data, current, page.getData(), 0, chunkSize);
				if (page != firstPage)
					page.getPageHeader().setDataLength(chunkSize);
				page.setDirty(true);
				//}
				remaining -= chunkSize;
				current += chunkSize;
				next = page.getPageHeader().getNextInChain();
				if (remaining > 0) {
					if (next > 0) {
						// load next page in chain
						nextPage = (SinglePage) getDataPage(next);
						pages.add(page);
						page = nextPage;
					} else {
						// add a new page to the chain
						nextPage = (SinglePage) createDataPage();
						nextPage.setData(new byte[fileHeader.getWorkSize()]);
						nextPage.getPageHeader().setNextInChain(0L);
						page.getPageHeader().setNextInChain(
							nextPage.getPageNum());
						pages.add(page);
						page = nextPage;
					}
				} else {
					page.getPageHeader().setNextInChain(0L);
					if (page != firstPage) {
						page.setDirty(true);
						pages.add(page);
						firstPage.getPageHeader().setLastInChain(
							page.getPageNum());
					} else
						firstPage.getPageHeader().setLastInChain(0L);
					firstPage.setDirty(true);
					pages.add(firstPage, 3);
				}
			}
			if (next > 0) {
				// there are more pages in the chain:
				// remove them
				while (next > 0) {
					nextPage = (SinglePage) getDataPage(next);
					next = nextPage.getPageHeader().getNextInChain();
					nextPage.setDirty(true);
					nextPage.delete();
					pages.remove(nextPage);
				}
			}
		}
	}

	protected class ClockPageBuffer {

		/**  Description of the Field */
		public final static int PAGE_BUFFER_SIZE = 32;
		protected int blockBuffers;

		protected int fails = 0;
		protected int hits = 0;
		//protected TLongObjectHashMap map;
        protected Long2ObjectLinkedOpenHashMap map;

		//protected LinkedList queue = new LinkedList();

		/**
		 *  Constructor for the PageBuffer object
		 *
		 *@param  blockBuffers  Description of the Parameter
		 */
		public ClockPageBuffer(int blockBuffers) {
			this.blockBuffers = blockBuffers;
			//map = new TLongObjectHashMap(blockBuffers);
            map = new Long2ObjectLinkedOpenHashMap(blockBuffers);
		}

		/**  Constructor for the PageBuffer object */
		public ClockPageBuffer() {
			this(PAGE_BUFFER_SIZE);
		}

		/**
		 *  Description of the Method
		 *
		 *@param  page  Description of the Parameter
		 */
		public void add(DataPage page) {
			add(page, 1);
		}

		public void add(DataPage page, int initialRefCount) {
			if (page instanceof OverflowPage) {
				page = ((OverflowPage) page).getFirstPage();
			}
			if (map.containsKey(page.getPageNum())) {
				page.incRefCount();
				return;
			}
			while (map.size() >= blockBuffers)
				removeOne(page);
			page.setRefCount(initialRefCount);
			//queue.addLast(page);
			map.put(page.getPageNum(), page);
		}

		public void flush() {
			DataPage page;
			for (Iterator i = map.values().iterator(); i.hasNext();) {
				page = (DataPage) i.next();
				if (page.isDirty())
					try {
						page.write();
						//fileHeader.write();
					} catch (IOException ioe) {
						ioe.printStackTrace();
					}
			}
		}

		public DataPage get(Page page) {
			return get(page.getPageNum());
		}

		public DataPage get(long pnum) {
			final DataPage page = (DataPage) map.get(pnum);
			if (page == null)
				fails++;
			else
				hits++;
			return page;
		}

		public void remove(DataPage page) {
//			final int idx = queue.indexOf(page);
//			if (idx > -1)
//				queue.remove(idx);
			map.remove(page.getPageNum());
			if (page.isDirty())
				try {
					page.write();
					//fileHeader.write();
				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
		}

		private final void removeOne(DataPage page) {
			DataPage old;
			boolean removed = false;
			long oldNum, pNum;
			while (!removed) {
				for (Iterator i = map.values().iterator(); i.hasNext();) {
					old = (DataPage) i.next();
					oldNum = old.getPageNum();
					pNum = page.getPageNum();
					// don't replace the page we are trying to store
					// and don't replace consecutive pages
					if (oldNum == pNum || oldNum == pNum + 1)
						continue;
					old.decRefCount();
					// replace old page if it has reference count < 1,
					if (old.getRefCount() < 1) {
						i.remove();
						//map.remove(oldNum);
						removed = true;
						if (old.isDirty())
							try {
								old.write();
							} catch (IOException e) {
								LOG.warn(
									"error while writing page: "
										+ old.getPageInfo()
										+ ": "
										+ e.getMessage());
							}
						old = null;
						return;
					}
				}
			}
		}

		public int getBuffers() {
			return blockBuffers;
		}
		public int getUsedBuffers() {
			return map.size();
		}
		public int getSize() {
			return map.size();
		}
		public int getFails() {
			return fails;
		}
		public int getHits() {
			return hits;
		}
	}

	/**
	 *  wrapper class around a page of data.
	 *
	 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
	 *@created    25. Mai 2002
	 */
	private final class SinglePage extends DataPage {

		boolean compress = false;
		byte[] data = null;
		Page page;
		BFilePageHeader ph;

		/**
		 *  Constructor for the DataPage object
		 *
		 *@exception  IOException  Description of the Exception
		 */
		public SinglePage() throws IOException {
			this(true);
		}

		/**
		 *  Constructor for the DataPage object
		 *
		 *@param  compress         Description of the Parameter
		 *@exception  IOException  Description of the Exception
		 */
		public SinglePage(boolean compress) throws IOException {
			this.compress = compress;
			page = getFreePage();
			ph = (BFilePageHeader) page.getPageHeader();
			ph.setStatus(RECORD);
			ph.setDirty(true);
			ph.setDataLength(0);
			//ph.setNextChunk( -1 );
			fileHeader.setLastDataPage(page.getPageNum());
			data = new byte[fileHeader.getWorkSize()];
		}

		/**
		 *  Constructor for the DataPage object
		 *
		 *@param  p                Description of the Parameter
		 *@exception  IOException  Description of the Exception
		 */
		public SinglePage(Page p, byte[] data) throws IOException {
			this(p, data, true);
		}

		/**
		 *  Constructor for the DataPage object
		 *
		 *@param  p                Description of the Parameter
		 *@param  compress         Description of the Parameter
		 *@exception  IOException  Description of the Exception
		 */
		public SinglePage(Page p, byte[] data, boolean compress)
			throws IOException {
			if (p == null)
				throw new IOException("illegal page");
			if (!(p.getPageHeader().getStatus() == RECORD
				|| p.getPageHeader().getStatus() == MULTI_PAGE)) {
				LOG.debug("not a data-page: " + p.getPageInfo());
				throw new IOException("not a data-page");
			}
			this.data = data;
			this.compress = compress;
			page = p;
			ph = (BFilePageHeader) page.getPageHeader();
		}

		/**
		 *  Description of the Method
		 *
		 *@exception  IOException  Description of the Exception
		 */
		public void delete() throws IOException {
			// reset page header fields
			ph.setDataLength(0);
			ph.setNextInChain(-1L);
			ph.setLastInChain(-1L);
			ph.setTID((short) - 1);
			ph.setRecordCount((short) 0);
			setRefCount(0);
			ph.setDirty(true);
			unlinkPages(page);
		}

		/**
		 *  Gets the data attribute of the DataPage object
		 *
		 *@return    The data value
		 */
		public byte[] getData() {
			return data;
		}

		/**
		 *  Gets the pageHeader attribute of the DataPage object
		 *
		 *@return    The pageHeader value
		 */
		public BFilePageHeader getPageHeader() {
			return ph;
		}

		/**
		 *  Gets the pageInfo attribute of the SinglePage object
		 *
		 *@return    The pageInfo value
		 */
		public String getPageInfo() {
			return page.getPageInfo();
		}

		/**
		 *  Gets the pageNum attribute of the SinglePage object
		 *
		 *@return    The pageNum value
		 */
		public long getPageNum() {
			return page.getPageNum();
		}

		/**
		 *  Sets the data attribute of the DataPage object
		 *
		 *@param  buf  The new data value
		 */
		public void setData(byte[] buf) {
			data = buf;
		}

		/**
		 *  Sets the dirty attribute of the SinglePage object
		 *
		 *@param  dirty  The new dirty value
		 */
		public void setDirty(boolean dirty) {
			super.setDirty(dirty);
			ph.setDirty(true);
			saved = false;
		}

		/**
		 *  Description of the Method
		 *
		 *@exception  IOException  Description of the Exception
		 */
		public void write() throws IOException {
			ph.setDirty(true);
			Value value = new Value(data);
			writeValue(page, value);
			saved = true;
		}
	}
}
