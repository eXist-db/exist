package org.exist.storage.btree;

/*
 *  dbXML License, Version 1.0
 *
 *
 *  Copyright (c) 1999-2001 The dbXML Group, L.L.C.
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1. Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the
 *  distribution.
 *
 *  3. The end-user documentation included with the redistribution,
 *  if any, must include the following acknowledgment:
 *  "This product includes software developed by
 *  The dbXML Group (http://www.dbxml.com/)."
 *  Alternately, this acknowledgment may appear in the software
 *  itself, if and wherever such third-party acknowledgments normally
 *  appear.
 *
 *  4. The names "dbXML" and "The dbXML Group" must not be used to
 *  endorse or promote products derived from this software without
 *  prior written permission. For written permission, please contact
 *  info@dbxml.com.
 *
 *  5. Products derived from this software may not be called "dbXML",
 *  nor may "dbXML" appear in their name, without prior written
 *  permission of The dbXML Group.
 *
 *  THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 *  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED.  IN NO EVENT SHALL THE DBXML GROUP OR ITS CONTRIBUTORS
 *  BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY,
 *  OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 *  OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 *  BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *  $Id$
 */
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.exist.storage.journal.Lsn;
import org.exist.util.ByteConversion;

/**
 *  Paged is a paged file foundation that is used by both the BTree class and
 *  the HashFiler. It provides flexible paged I/O and page caching
 *  functionality.
 */

public abstract class Paged {
	
	protected final static Logger LOG = Logger.getLogger(Paged.class);
	
	protected final static byte DELETED = 127;
	protected final static byte OVERFLOW = 126;
	protected final static byte UNUSED = 0;
	
	protected static int PAGE_SIZE = 4096;
	
	private RandomAccessFile raf;
	private File file;
	private FileHeader fileHeader;
	private boolean readOnly = false;
	private boolean fileIsNew = false;
	
	private byte[] tempPageData = null;
	private byte[] tempHeaderData = null;
	
	public Paged() {
		fileHeader = createFileHeader();
		tempPageData = new byte[fileHeader.pageSize];
		tempHeaderData = new byte[fileHeader.pageHeaderSize];
	}

	public Paged(File file) {
		this();
		setFile(file);
	}

	public short getFileVersion() {
		return 0;
	}
	
	public final static void setPageSize(int pageSize) {
		PAGE_SIZE = pageSize;
	}

	public final static int getPageSize() {
		return PAGE_SIZE;
	}

	public static Value[] deleteArrayValue(Value[] vals, int idx) {
		Value[] newVals = new Value[vals.length - 1];
		if (idx > 0)
			System.arraycopy(vals, 0, newVals, 0, idx);
		if (idx < newVals.length)
			System.arraycopy(vals, idx + 1, newVals, idx, newVals.length - idx);
		return newVals;
	}

	// These are a bunch of utility methods for subclasses

	public static Value[] insertArrayValue(Value[] vals, Value val, int idx) {
		Value[] newVals = new Value[vals.length + 1];
		if (idx > 0)
			System.arraycopy(vals, 0, newVals, 0, idx);
		newVals[idx] = val;
		if (idx < vals.length)
			System.arraycopy(vals, idx, newVals, idx + 1, vals.length - idx);
		return newVals;
	}

	public final boolean isReadOnly() {
		return readOnly;
	}

	public boolean close() throws DBException {
		try {
			raf.close();
		} catch (IOException e) {
			throw new DBException("an error occurred while closing database file: " + e.getMessage());
		}
		return true;
	}

	public boolean create() throws DBException {
		try {
			fileHeader.write();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			throw new DBException(0, "Error creating " + file.getName());
		}
	}

	/**
	 *  createFileHeader must be implemented by a Paged implementation in order
	 *  to create an appropriate subclass instance of a FileHeader.
	 *
	 *@return    a new FileHeader
	 */
	public abstract FileHeader createFileHeader();

	/**
	 *  createFileHeader must be implemented by a Paged implementation in order
	 *  to create an appropriate subclass instance of a FileHeader.
	 *
	 *@param  read          If true, reads the FileHeader from disk
	 *@return               a new FileHeader
	 *@throws  IOException  if an exception occurs
	 */
	public abstract FileHeader createFileHeader(boolean read) throws IOException;

	/**
	 *  createFileHeader must be implemented by a Paged implementation in order
	 *  to create an appropriate subclass instance of a FileHeader.
	 *
	 *@param  pageCount  The number of pages to allocate for primary storage
	 *@return            a new FileHeader
	 */
	public abstract FileHeader createFileHeader(long pageCount);

	/**
	 *  createFileHeader must be implemented by a Paged implementation in order
	 *  to create an appropriate subclass instance of a FileHeader.
	 *
	 *@param  pageCount  The number of pages to allocate for primary storage
	 *@param  pageSize   The size of a Page (should be a multiple of a FS block)
	 *@return            a new FileHeader
	 */
	public abstract FileHeader createFileHeader(long pageCount, int pageSize);

	/**
	 *  createPageHeader must be implemented by a Paged implementation in order
	 *  to create an appropriate subclass instance of a PageHeader.
	 *
	 *@return    a new PageHeader
	 */
	public abstract PageHeader createPageHeader();

	public boolean drop() throws DBException {
		return true;
	}

	public boolean exists() {
		return !fileIsNew;
	}

	public boolean flush() throws DBException {
		try {
			if(fileHeader.isDirty() && !readOnly)
				fileHeader.write();
		} catch (IOException ioe) {
		}
		return true;
	}

	/**
	 *  getFile returns the file object for this Paged.
	 *
	 *@return    The File
	 */
	public final File getFile() {
		return file;
	}
	
	/**
	 *  getFileHeader returns the FileHeader
	 *
	 *@return    The FileHeader
	 */
	public FileHeader getFileHeader() {
		return fileHeader;
	}

	public void closeAndRemove() {
		try {
			raf.close();
		} catch (IOException e) {
			LOG.warn("Failed to close data file: " + file.getAbsolutePath());
		}
		file.delete();
	}
	
	/**
	 *  getFreePage returns the first free Page from secondary storage. If no
	 *  Pages are available, the file is grown as appropriate.
	 *
	 *@return               The next free Page
	 *@throws  IOException  if an Exception occurs
	 */
	protected final Page getFreePage() throws IOException {
		Page p = null;
		synchronized (fileHeader) {
			long pageNum = fileHeader.firstFreePage;
			if (pageNum != -1) {
				// Steal a deleted page
				p = new Page(pageNum);
				p.read();
				fileHeader.firstFreePage = p.header.nextPage;
				if (fileHeader.firstFreePage == -1)
					fileHeader.setLastFreePage(-1);
			} else {
				// Grow the file
				pageNum = fileHeader.totalCount;
//				if(getFile().getName().equals("words.dbx"))
//					System.out.println("growing the file: " + fileHeader.totalCount);
				if(pageNum == Integer.MAX_VALUE) {
					throw new IOException("page limit reached: " + pageNum);
				}
				fileHeader.setTotalCount(pageNum + 1);
				p = new Page(pageNum);
				p.read();
			}
//			if(getFile().getName().equals("dom.dbx"))
//				printFreeSpaceList();
		}
		// Initialize The Page Header (Cleanly)
		p.header.setNextPage(-1);
		p.header.setStatus(UNUSED);
		fileHeader.setDirty(true);
        // write out the file header
        fileHeader.write();
		return p;
	}

	/**
	 *  getPage returns the page specified by pageNum.
	 *
	 *@param  pageNum       The Page number
	 *@return               The requested Page
	 *@throws  IOException  if an Exception occurs
	 */
	protected final Page getPage(long pageNum) throws IOException {
		return new Page(pageNum);
	}

	/**
	 *  Gets the opened attribute of the Paged object
	 *
	 *@return    The opened value
	 */
	public boolean isOpened() {
		return true;
	}

	public boolean open(short expectedVersion) throws DBException {
		try {
			if (exists()) {
				fileHeader.read();
				if(fileHeader.getVersion() != expectedVersion)
					throw new DBException("Database file " +
							getFile().getName() + " has a storage format incompatible with this " +
							"version of eXist. Please do a backup/restore of your data first.");
				return true;
			} else
				return false;
		} catch (Exception e) {
			e.printStackTrace();
			throw new DBException(0, "Error opening " + file.getName());
		}
	}

	/**
	 *  Debug
	 *
	 *@exception  IOException  Description of the Exception
	 */
	public void printFreeSpaceList() throws IOException {
		long pageNum = fileHeader.firstFreePage;
		System.out.println("first free page: " + pageNum);
		Page next;
		System.out.println("free pages for " + getFile().getName());
		while (pageNum != -1) {
			next = getPage(pageNum);
			next.read();
			System.out.print(pageNum + ";");
			pageNum = next.header.nextPage;
		}
		System.out.println();
	}

	private boolean isRemovedPage(long pageNum) throws IOException {
		long nextNum = fileHeader.firstFreePage;
		while(nextNum != -1) {
			if(nextNum == pageNum) {
				LOG.error("Page " + pageNum + " has already been removed");
				Thread.dumpStack();
				return true;
			}
			Page next = getPage(nextNum);
			next.read();
			nextNum = next.header.nextPage;
		}
		return false;
	}
	
	/**
	 *  setFile sets the file object for this Paged.
	 *
	 *@param  file  The File
	 */
	protected final void setFile(final File file) {
		this.file = file;
		fileIsNew = !file.exists();
		try {
			if ((!file.exists()) || file.canWrite()) {
				raf = new RandomAccessFile(file, "rw");
			} else {
				readOnly = true;
				raf = new RandomAccessFile(file, "r");
			}
		} catch (Exception e) {
            e.printStackTrace();
		}
	}

	/**
	 *  unlinkPages unlinks a set of pages starting at the specified Page.
	 *
	 *@param  page          The starting Page to unlink
	 *@throws  IOException  if an Exception occurs
	 */
	protected void unlinkPages(Page page) throws IOException {
		if (page != null) {
//			if(isRemovedPage(page.getPageNum()))
//				return;
			// Walk the chain and add it to the unused list
			page.header.setStatus(UNUSED);
            page.header.lsn = Lsn.LSN_INVALID;
			synchronized (fileHeader) {
				if (fileHeader.firstFreePage == -1) {
					fileHeader.setFirstFreePage(page.pageNum);
					page.header.setNextPage(-1);
				} else {
                    long first = fileHeader.firstFreePage;
                    fileHeader.setFirstFreePage(page.pageNum);
                    page.header.setNextPage(first);
				}
				page.remove();
				fileHeader.setDirty(true);
			}
		}
	}

	/**
	 *  unlinkPages unlinks a set of pages starting at the specified page
	 *  number.
	 *
	 *@param  pageNum       Description of the Parameter
	 *@throws  IOException  if an Exception occurs
	 */
	protected final void unlinkPages(long pageNum) throws IOException {
		unlinkPages(getPage(pageNum));
	}

    protected void reuseDeleted(Page page) throws IOException {
        if (page != null && fileHeader.getFirstFreePage() > -1) {
            long next = fileHeader.getFirstFreePage();
            if (next == page.pageNum) {
                fileHeader.setFirstFreePage(page.header.getNextPage());
                fileHeader.write();
                return;
            }
            Page p = getPage(next);
            p.read();
            next = p.header.getNextPage();
            while (next > -1) {
                if (next == page.pageNum) {
                    p.header.setNextPage(page.header.getNextPage());
                    p.header.setDirty(true);
                    p.write(null);
                    return;
                }
                p = getPage(next);
                p.read();
                next = p.header.getNextPage();
            }
        }
    }
    
	/**
	 *  writeValue writes the multi-Paged Value starting at the specified Page.
	 *
	 *@param  page          The starting Page
	 *@param  value         The Value to write
	 *@throws  IOException  if an Exception occurs
	 */
	protected final void writeValue(Page page, Value value) throws IOException {
		byte[] data = value.getData();
		writeValue(page, data);
	}
	
	protected final void writeValue(Page page, byte[] data) throws IOException {
		PageHeader hdr = page.getPageHeader();
		hdr.dataLen = fileHeader.workSize;
		if (data.length < hdr.dataLen) {
			hdr.dataLen = data.length;
		}
		page.write(data);
	}

	/**
	 *  writeValue writes the multi-Paged Value starting at the specified page
	 *  number.
	 *
	 *@param  page          The starting page number
	 *@param  value         The Value to write
	 *@throws  IOException  if an Exception occurs
	 */
	protected final void writeValue(long page, Value value) throws IOException {
		writeValue(getPage(page), value);
	}
	
	/**
	 *  FileHeader
	 *
	 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
	 */

	public abstract class FileHeader {
		
		private short versionId;
		
		private boolean dirty = false;
		private long firstFreePage = -1;

		private short headerSize;
		private long lastFreePage = -1;
		private short maxKeySize = 256;
		private long pageCount;
		private byte pageHeaderSize = 64;
		private int pageSize;
		private long recordCount;
		private long totalCount;
		private int workSize;

        private byte[] buf;
        
		/**  Constructor for the FileHeader object */
		public FileHeader() {
			this(1024, PAGE_SIZE);
		}

		/**
		 *  Constructor for the FileHeader object
		 *
		 *@param  pageCount  Description of the Parameter
		 */
		public FileHeader(long pageCount) {
			this(pageCount, 4096);
		}

		public FileHeader(long pageCount, int pageSize) {
			this(pageCount, pageSize, (byte) 4);
		}

		public FileHeader(long pageCount, int pageSize, byte blockSize) {
			this.pageSize = pageSize;
			this.pageCount = pageCount;
			this.totalCount = pageCount;
			this.headerSize = (short) pageSize;
			this.versionId = getFileVersion();
            this.buf = new byte[headerSize];
			calculateWorkSize();
		}

		public FileHeader(boolean read) throws IOException {
			if (read)
				read();
		}

		private void calculateWorkSize() {
			workSize = pageSize - pageHeaderSize;
		}

		/**  Decrement the number of records being managed by the file */
		public final synchronized void decRecordCount() {
			recordCount--;
			dirty = true;
		}

		/**
		 *  The first free page in unused secondary space
		 *
		 *@return    The firstFreePage value
		 */
		public final long getFirstFreePage() {
			return firstFreePage;
		}

		/**
		 *  The size of the FileHeader. Usually 1 OS Page
		 *
		 *@return    The headerSize value
		 */
		public final short getHeaderSize() {
			return headerSize;
		}

		/**
		 *  The last free page in unused secondary space
		 *
		 *@return    The lastFreePage value
		 */
		public final long getLastFreePage() {
			return lastFreePage;
		}

		/**
		 *  The maximum number of bytes a key can be. 256 is good
		 *
		 *@return    The maxKeySize value
		 */
		public final short getMaxKeySize() {
			return maxKeySize;
		}

		/**
		 *  The number of pages in primary storage
		 *
		 *@return    The pageCount value
		 */
		public final long getPageCount() {
			return pageCount;
		}

		/**
		 *  The size of a page header. 64 is sufficient
		 *
		 *@return    The pageHeaderSize value
		 */
		public final byte getPageHeaderSize() {
			return pageHeaderSize;
		}

		/**
		 *  The size of a page. Usually a multiple of a FS block
		 *
		 *@return    The pageSize value
		 */
		public final int getPageSize() {
			return pageSize;
		}

		/**
		 *  The number of records being managed by the file (not pages)
		 *
		 *@return    The recordCount value
		 */
		public final long getRecordCount() {
			return recordCount;
		}

		/**
		 *  The number of total pages in the file
		 *
		 *@return    The totalCount value
		 */
		public final long getTotalCount() {
			return totalCount;
		}

		/**
		 *  Gets the workSize attribute of the FileHeader object
		 *
		 *@return    The workSize value
		 */
		public final int getWorkSize() {
			return workSize;
		}

		public final short getVersion() {
			return versionId;
		}
		
		/**  Increment the number of records being managed by the file */
		public final synchronized void incRecordCount() {
			recordCount++;
			dirty = true;
		}

		/**
		 *  Gets the dirty attribute of the FileHeader object
		 *
		 *@return    The dirty value
		 */
		public final boolean isDirty() {
			return dirty;
		}

        public final synchronized void read() throws IOException {
            raf.seek(0);
            raf.read(buf);
            read(buf);
            calculateWorkSize();
            dirty = false;
        }

        public int read(byte[] buf) throws IOException {
            int offset = 0;
            versionId = ByteConversion.byteToShort(buf, offset);
            offset += 2;
            headerSize = ByteConversion.byteToShort(buf, offset);
            offset += 2;
            pageSize = ByteConversion.byteToInt(buf, offset);
            offset += 4;
            pageCount = ByteConversion.byteToLong(buf, offset);
            offset += 8;
            totalCount = ByteConversion.byteToLong(buf, offset);
            offset += 8;
            firstFreePage = ByteConversion.byteToLong(buf, offset);
            offset += 8;
            lastFreePage = ByteConversion.byteToLong(buf, offset);
            offset += 8;
            pageHeaderSize = buf[offset++];
            maxKeySize = ByteConversion.byteToShort(buf, offset);
            offset += 2;
            recordCount = ByteConversion.byteToLong(buf, offset);
            offset += 8;
            return offset;
        }

		/**
		 *  Sets the dirty attribute of the FileHeader object
		 *
		 *@param  dirty  The new dirty value
		 */
		public final void setDirty(boolean dirty) {
			this.dirty = dirty;
		}

		/**
		 *  The first free page in unused secondary space
		 *
		 *@param  firstFreePage  The new firstFreePage value
		 */
		public final void setFirstFreePage(long firstFreePage) {
			this.firstFreePage = firstFreePage;
			dirty = true;
		}

		/**
		 *  The size of the FileHeader. Usually 1 OS Page
		 *
		 *@param  headerSize  The new headerSize value
		 */
		public final void setHeaderSize(short headerSize) {
			this.headerSize = headerSize;
			dirty = true;
		}

		/**
		 *  The last free page in unused secondary space
		 *
		 *@param  lastFreePage  The new lastFreePage value
		 */
		public final void setLastFreePage(long lastFreePage) {
			this.lastFreePage = lastFreePage;
			dirty = true;
		}

		/**
		 *  The maximum number of bytes a key can be. 256 is good
		 *
		 *@param  maxKeySize  The new maxKeySize value
		 */
		public final void setMaxKeySize(short maxKeySize) {
			this.maxKeySize = maxKeySize;
			dirty = true;
		}

		/**
		 *  The number of pages in primary storage
		 *
		 *@param  pageCount  The new pageCount value
		 */
		public final void setPageCount(long pageCount) {
			this.pageCount = pageCount;
			dirty = true;
		}

		/**
		 *  The size of a page header. 64 is sufficient
		 *
		 *@param  pageHeaderSize  The new pageHeaderSize value
		 */
		public final void setPageHeaderSize(byte pageHeaderSize) {
			this.pageHeaderSize = pageHeaderSize;
			calculateWorkSize();
			dirty = true;
		}

		/**
		 *  The size of a page. Usually a multiple of a FS block
		 *
		 *@param  pageSize  The new pageSize value
		 */
		public final void setPageSize(int pageSize) {
			this.pageSize = pageSize;
			calculateWorkSize();
			dirty = true;
		}

		/**
		 *  The number of records being managed by the file (not pages)
		 *
		 *@param  recordCount  The new recordCount value
		 */
		public final void setRecordCount(long recordCount) {
			this.recordCount = recordCount;
			dirty = true;
		}

		/**
		 *  The number of total pages in the file
		 *
		 *@param  totalCount  The new totalCount value
		 */
		public final void setTotalCount(long totalCount) {
			this.totalCount = totalCount;
			dirty = true;
		}

        public final synchronized void write() throws IOException {
            raf.seek(0);
            write(buf);
            raf.write(buf);
            dirty = false;
        }
        
        public int write(byte[] buf) throws IOException {
            int offset = 0;
            ByteConversion.shortToByte(versionId, buf, offset);
            offset += 2;
            ByteConversion.shortToByte(headerSize, buf, offset);
            offset += 2;
            ByteConversion.intToByte(pageSize, buf, offset);
            offset += 4;
            ByteConversion.longToByte(pageCount, buf, offset);
            offset += 8;
            ByteConversion.longToByte(totalCount, buf, offset);
            offset += 8;
            ByteConversion.longToByte(firstFreePage, buf, offset);
            offset += 8;
            ByteConversion.longToByte(lastFreePage, buf, offset);
            offset += 8;
            buf[offset++] = pageHeaderSize;
            ByteConversion.shortToByte(maxKeySize, buf, offset);
            offset += 2;
            ByteConversion.longToByte(recordCount, buf, offset);
            offset += 8;
            return offset;
        }
	}

	/**
	 *  Page
	 */

	public final class Page implements Comparable {

		/**  The Header for this Page */
		private PageHeader header;

		/**  The offset into the file that this page starts */
		private long offset;
		/**  This page number */
		private long pageNum;

		private int refCount = 0;
        
		/**  Constructor for the Page object */
		public Page() {
			header = createPageHeader();
		}

		/**
		 *  Constructor for the Page object
		 *
		 *@param  pageNum          Description of the Parameter
		 *@exception  IOException  Description of the Exception
		 */
		public Page(long pageNum) throws IOException {
			this();
			if(pageNum < 0)
				throw new IOException("Illegal page num: " + pageNum);
			setPageNum(pageNum);
		}
        
		public void decRefCount() {
			refCount--;
		}

		/**
		 *  Gets the offset attribute of the Page object
		 *
		 *@return    The offset value
		 */
		public long getOffset() {
			return offset;
		}

		/**
		 *  Gets the pageHeader attribute of the Page object
		 *
		 *@return    The pageHeader value
		 */
		public PageHeader getPageHeader() {
			return header;
		}

		/**
		 *  Gets the pageInfo attribute of the Page object
		 *
		 *@return    The pageInfo value
		 */
		public String getPageInfo() {
			return "page: "
				+ pageNum
				+ "; file = "
				+ getFile().getName()
				+ "; address = "
				+ Long.toHexString(offset)
				+ "; page header = "
				+ fileHeader.getPageHeaderSize()
				+ "; data start = "
				+ Long.toHexString(offset + fileHeader.getPageHeaderSize());
		}

		public long getPageNum() {
			return pageNum;
		}

		public int getRefCount() {
			return refCount;
		}

		public int getDataPos() {
			return fileHeader.pageHeaderSize;
		}

		public void incRefCount() {
			refCount++;
		}

		public byte[] read() throws IOException {
			try {
//				dumpPage();
				if (raf.getFilePointer() != offset) {
					raf.seek(offset);
				}

				Arrays.fill(tempHeaderData, (byte)0);
				raf.read(tempHeaderData);
				
				// Read in the header
				header.read(tempHeaderData, 0);
				
				// Read the working data
				final byte[] workData = new byte[header.dataLen];
				raf.read(workData);
				return workData;
			} catch(Exception e) {
				LOG.debug("error while reading page: " + getPageInfo(), e);
				throw new IOException(e.getMessage());
			}
		}
		
		public void setPageNum(long pageNum) {
			this.pageNum = pageNum;
			offset = fileHeader.headerSize + (pageNum * fileHeader.pageSize);
		}

		public void remove() throws IOException {
			write(null);
		}
		
		private final void write(byte[] data) throws IOException {
			
			if(data == null) {
				// Removed page: fill with 0
				Arrays.fill(tempPageData, (byte)0);
                header.setLsn(Lsn.LSN_INVALID);
            }

			// Write out the header
			header.write(tempPageData, 0);
			header.dirty = false;

			if(data != null) {
				if(data.length > fileHeader.workSize)
					throw new IOException("page: " + getPageInfo() + ": data length too large: " + data.length);
				else
					System.arraycopy(data, 0, tempPageData, fileHeader.pageHeaderSize, data.length);
			}
			if (raf.getFilePointer() != offset)
				raf.seek(offset);
			//raf.write(data);
			raf.write(tempPageData);
		}
		
		/* (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		public boolean equals(Object obj) {
			return ((Page)obj).pageNum == pageNum;
		}
		
		/* (non-Javadoc)
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		public int compareTo(Object o) {
			final Page other = (Page)o;
			if(pageNum == other.pageNum)
				return 0;
			else if(pageNum > other.pageNum)
				return 1;
			else
				return -1;
		}
		
		public void dumpPage() throws IOException {
			if (raf.getFilePointer() != offset)
				raf.seek(offset);
			byte[] data = new byte[fileHeader.pageSize];
			raf.read(data);
			LOG.debug("Contents of page " + pageNum + ": " + hexDump(data));
		}

	}

	public static abstract class PageHeader {
		
		private int dataLen = 0;
		private boolean dirty = false;
		private long nextPage = -1;
        
		private byte status = UNUSED;

        private long lsn = Lsn.LSN_INVALID;
        
		public PageHeader() {
		}

		public PageHeader(byte[] data, int offset) throws IOException {
			read(data, offset);
		}

		/**
		 *  The length of the Data
		 *
		 *@return    The dataLen value
		 */
		public final int getDataLen() {
			return dataLen;
		}

		/**
		 *  The next page for this Record (if overflowed)
		 *
		 *@return    The nextPage value
		 */
		public final long getNextPage() {
			return nextPage;
		}

		/**
		 *  The status of this page (UNUSED, RECORD, DELETED, etc...)
		 *
		 *@return    The status value
		 */
		public final byte getStatus() {
			return status;
		}

		/**
		 *  Gets the dirty attribute of the PageHeader object
		 *
		 *@return    The dirty value
		 */
		public final boolean isDirty() {
			return dirty;
		}

        /**
         * Returns the LSN, i.e. the log sequence number, of the last
         * operation that modified this page. This information is used
         * during recovery: if the log sequence number of a log record
         * is smaller or equal to the LSN stored in this page header, then
         * the page has already been written to disk before the database
         * failure. Otherwise, the modification is not yet reflected in the page
         * and the operation needs to be redone.
         * 
         * @return
         */
        public final long getLsn() {
            return lsn;
        }
        
        public final void setLsn(long lsn) {
            this.lsn = lsn;
        }
        
		public int read(byte[] data, int offset) throws IOException {
			status = data[offset++];
			dataLen = ByteConversion.byteToInt(data, offset);
			offset += 4;
			nextPage = ByteConversion.byteToLong(data, offset);
			offset += 8;
            lsn = ByteConversion.byteToLong(data, offset);
            offset += 8;
			return offset;
		}

		public int write(byte[] data, int offset) throws IOException {
			data[offset++] = status;
			ByteConversion.intToByte(dataLen, data, offset);
			offset += 4;
			ByteConversion.longToByte(nextPage, data, offset);
			offset += 8;
            ByteConversion.longToByte(lsn, data, offset);
            offset += 8;
			dirty = false;
			return offset;
		}
		
		/**
		 *  The length of the Data
		 *
		 *@param  dataLen  The new dataLen value
		 */
		public final void setDataLen(int dataLen) {
			this.dataLen = dataLen;
			dirty = true;
		}

		public final void setDirty(boolean dirty) {
			this.dirty = dirty;
		}

		/**
		 *  The next page for this Record (if overflowed)
		 *
		 *@param  nextPage  The new nextPage value
		 */
		public final void setNextPage(long nextPage) {
			this.nextPage = nextPage;
			dirty = true;
		}

		/**
		 *  The status of this page (UNUSED, RECORD, DELETED, etc...)
		 *
		 *@param  status  The new status value
		 */
		public final void setStatus(byte status) {
			this.status = status;
			dirty = true;
		}
		
	}
	
	private static String[] hex = {"0", "1", "2", "3", "4", "5", "6", "7",
            "8", "9", "a", "b", "c", "d", "e", "f"};
	
	public static String hexDump(byte[] data) {
		StringBuffer buf = new StringBuffer();
		buf.append("\r\n");
		int columns = 0;
		for(int i = 0; i < data.length; i++, columns++) {
			byteToHex(buf, data[i]);
			if(columns == 16) {
				buf.append("\r\n");
				columns = 0;
			} else
				buf.append(' ');
		}
		return buf.toString();
	}
	
	private static void byteToHex( StringBuffer buf, byte b ) {
        int n = b;
        if ( n < 0 ) {
            n = 256 + n;
        }
        int d1 = n / 16;
        int d2 = n % 16;
        buf.append( hex[d1] );
        buf.append( hex[d2] );
    }
	
}
