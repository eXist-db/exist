/*
 * NOTE: This file is in part based on code from The dbXML Group.
 * The original license statement is also included below.
 *
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * ---------------------------------------------------------------------
 *
 * dbXML License, Version 1.0
 *
 * Copyright (c) 1999-2001 The dbXML Group, L.L.C.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by
 *        The dbXML Group (http://www.dbxml.com/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "dbXML" and "The dbXML Group" must
 *    not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact info@dbxml.com.
 *
 * 5. Products derived from this software may not be called "dbXML",
 *    nor may "dbXML" appear in their name, without prior written
 *    permission of The dbXML Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE DBXML GROUP OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */
package org.exist.storage.btree;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.BrokerPool;
import org.exist.storage.journal.Lsn;
import org.exist.util.ByteConversion;
import org.exist.util.FileUtils;
import org.exist.xquery.Constants;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.NonWritableChannelException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 *  Paged is a paged file foundation that is used by the BTree class and
 *  its subclasses.
 */
public abstract class Paged implements AutoCloseable {

    public static final int LENGTH_VERSION_ID = 2;  //sizeof short
    public static final int LENGTH_HEADER_SIZE = 2;  //sizeof short
    public static final int LENGTH_PAGE_COUNT = 8; //sizeof long
    public static final int LENGTH_PAGE_SIZE = 4; //sizeof int
    public static final int LENGTH_TOTAL_COUNT = 8; //sizeof long
    public static final int LENGTH_FIRST_FREE_PAGE = 8; //sizeof long
    public static final int LENGTH_LAST_FREE_PAGE = 8; //sizeof long
    public static final int LENGTH_PAGE_HEADER_SIZE = 1; //sizeof byte
    public static final int LENGTH_MAX_KEY_SIZE = 2;  //sizeof short
    public static final int LENGTH_RECORD_COUNT = 8; //sizeof long

    public static final int OFFSET_VERSION_ID = 0;
    public static final int OFFSET_HEADER_SIZE = OFFSET_VERSION_ID + LENGTH_VERSION_ID; //2
    public static final int OFFSET_PAGE_SIZE = OFFSET_HEADER_SIZE + LENGTH_HEADER_SIZE; //4
    public static final int OFFSET_PAGE_COUNT = OFFSET_PAGE_SIZE + LENGTH_PAGE_SIZE; //8
    public static final int OFFSET_TOTAL_COUNT = OFFSET_PAGE_COUNT + LENGTH_PAGE_COUNT; //16
    public static final int OFFSET_FIRST_FREE_PAGE = OFFSET_TOTAL_COUNT + LENGTH_TOTAL_COUNT; //24
    public static final int OFFSET_LAST_FREE_PAGE = OFFSET_FIRST_FREE_PAGE + LENGTH_FIRST_FREE_PAGE; //32
    public static final int OFFSET_PAGE_HEADER_SIZE = OFFSET_LAST_FREE_PAGE + LENGTH_LAST_FREE_PAGE; //40
    public static final int OFFSET_MAX_KEY_SIZE = OFFSET_PAGE_HEADER_SIZE + LENGTH_PAGE_HEADER_SIZE; //41
    public static final int OFFSET_RECORD_COUNT = OFFSET_MAX_KEY_SIZE + LENGTH_MAX_KEY_SIZE; //43
    public static final int OFFSET_REMAINDER = OFFSET_RECORD_COUNT + LENGTH_RECORD_COUNT; //51

    protected final static Logger LOG = LogManager.getLogger(Paged.class);

    protected final static byte DELETED = 127;
    protected final static byte OVERFLOW = 126;
    protected final static byte UNUSED = 0;

    protected static int PAGE_SIZE = 4096;

    protected final short fileVersion;
    private final FileHeader fileHeader;
    private final byte[] tempPageData;
    private final byte[] tempHeaderData;

    private RandomAccessFile raf;
    private Path file;
    private boolean readOnly = false;
    private boolean fileIsNew = false;
	
    public Paged(final BrokerPool pool, final short fileVersion) {
        this.fileVersion = fileVersion;
        this.fileHeader = createFileHeader(pool.getPageSize());
        this.tempPageData = new byte[fileHeader.pageSize];
        this.tempHeaderData = new byte[fileHeader.pageHeaderSize];
    }

    public final static void setPageSize(final int pageSize) {
        PAGE_SIZE = pageSize;
    }

    public final static int getPageSize() {
        return PAGE_SIZE;
    }

    public final boolean isReadOnly() {
        return readOnly;
    }

    /**
     * Close the underlying files.
     *
     * @throws DBException if an error occurs whilst closing
     */
    @Override
    public void close() throws DBException {
        try {
            raf.close();
        } catch (final IOException e) {
            throw new DBException("An error occurred whilst closing the database file '"
                    + file == null ? "null" : FileUtils.fileName(file) + "': " + e.getMessage());
        }
    }

    /**
     * Completely close down the instance and
     * all underlying resources and caches.
     *
     * @throws DBException if an error occurs whilst closing and removing the file
     */
    public final void closeAndRemove() throws DBException {
        close();
        FileUtils.deleteQuietly(file);
    }

    public boolean create() throws DBException {
        try {
            fileHeader.write();
            return true;
        } catch (final Exception e) {
            e.printStackTrace();
            throw new DBException(0, "Error creating " + FileUtils.fileName(file));
        }
    }

    /**
     * createFileHeader must be implemented by a Paged implementation in order
     * to create an appropriate subclass instance of a FileHeader.
     *
     * @param pageSize the size of the page
     * @return A new file header
     */
    public abstract FileHeader createFileHeader(int pageSize);

    /**
     * createPageHeader must be implemented by a Paged implementation in order
     * to create an appropriate subclass instance of a PageHeader.
     *
     * @return A new page header
     */
    public abstract PageHeader createPageHeader();

    public boolean exists() {
        return !fileIsNew;
    }

    /**
     * Flushes {@link org.exist.storage.btree.Paged#flush()} dirty data to the disk and cleans up the cache.
     * @return <code>true</code> if something has actually been cleaned
     * @throws DBException if an error occurs
     */
    public boolean flush() throws DBException {
        boolean flushed = false;
        try {
            if(fileHeader.isDirty() && !readOnly) {
                fileHeader.write();
                flushed = true;
            }
        } catch (final IOException ioe) {
            LOG.warn("report me");
            //TODO : this exception is *silently* ignored ?
        }
        return flushed;
    }

    /**
     * Backup the entire contents of the underlying file to 
     * an output stream.
     * 
     * @param os the output stream
     * @throws IOException if an I/O error occurs
     */
    public void backupToStream(final OutputStream os) throws IOException {
        raf.seek(0);
        final byte[] buf = new byte[4096];
        int len;
        while ((len = raf.read(buf)) > 0) {
            os.write(buf, 0, len);
        }
    }

    /**
     * getPath returns the file object for this Paged.
     *
     * @return The File
     */
    public final Path getFile() {
        return file;
    }

    /**
     * getFileHeader returns the FileHeader
     *
     * @return The FileHeader
     */
    public FileHeader getFileHeader() {
        return fileHeader;
    }

    protected final Page getFreePage() throws IOException {
        return getFreePage(true);
    }

    /**
     * Returns the first free page it can find, either by reusing a deleted page
     * or by appending a new one to secondary storage.
     *
     * @param reuseDeleted if set to false, the method will not try to reuse a
     * previously deleted page. This is required by btree page split operations to avoid 
     * concurrency conflicts within a transaction.
     *
     * @return a free page
     *
     * @throws IOException if an I/O error occurs
     */
    protected final Page getFreePage(final boolean reuseDeleted) throws IOException {
        final Page page;
        synchronized (fileHeader) {
            long pageNum = fileHeader.firstFreePage;
            if (reuseDeleted && pageNum != Page.NO_PAGE) {
                // Steal a deleted page
                page = new Page(pageNum);
                page.read();
                fileHeader.firstFreePage = page.header.nextPage;
                if (fileHeader.firstFreePage == Page.NO_PAGE) {
                    fileHeader.setLastFreePage(Page.NO_PAGE);
                }
            } else {
                // Grow the file
                pageNum = fileHeader.totalCount;
                if(pageNum == Integer.MAX_VALUE) {
                    throw new IOException("page limit reached: " + pageNum);
                }
                fileHeader.setTotalCount(pageNum + 1);
                page = new Page(pageNum);
                page.read();
            }
        }
        // Cleanly initialize The Page Header
        page.header.setNextPage(Page.NO_PAGE);
        page.header.setStatus(UNUSED);
        fileHeader.setDirty(true);
        // write out the file header
        fileHeader.write();
        return page;
    }

    /**
     * getPage returns the page specified by pageNum.
     *
     * @param pageNum The Page number
     *
     * @return The requested Page
     * @throws IOException if an exception occurs
     */
    protected final Page getPage(final long pageNum) throws IOException {
        return new Page(pageNum);
    }

    /**
     * Gets the opened attribute of the Paged object
     *
     * @return The opened value
     */
    public boolean isOpened() {
        return true;
    }

    /**
     * @param requiredVersion The required version of the file
     * @return true if opened
     * @throws DBException if the paged file cannot be opened
     */
    public boolean open(final short requiredVersion) throws DBException {
        try {
            if (exists()) {
                fileHeader.read();
                if(fileHeader.getVersion() != requiredVersion) {
                    throw new DBException("Database file " +
                        FileUtils.fileName(getFile()) + " has a storage format incompatible with this " +
                        "version of eXist. You need to upgrade your database by creating a backup, " +
                        "cleaning your data directory and restoring the data. In some cases, " +
                        "a reindex may be sufficient. " +
                        "Please follow the instructions for the version you installed. " +
                        "File version is: " + fileHeader.getVersion() +
                        "; db requires version: " + requiredVersion);
                }
                return true;
            } else {
                return false;
            }
        } catch (final Exception e) {
            e.printStackTrace();
            throw new DBException(0, "Error opening " + FileUtils.fileName(file) + ": " + e.getMessage());
        }
    }

    /**
     * Debug.
     *
     * @param out the output print stream
     *
     * @throws IOException Description of the Exception
     */
    public void printFreeSpaceList(final PrintStream out) throws IOException {
        long pageNum = fileHeader.firstFreePage;
        out.println("first free page: " + pageNum);
        Page next;
        out.println("free pages for " + FileUtils.fileName(getFile()));
        while (pageNum != Page.NO_PAGE) {
            next = getPage(pageNum);
            next.read();
            out.print(pageNum + ";");
            pageNum = next.header.nextPage;
        }
        out.println();
    }

    /**
     * setFile sets the file object for this Paged.
     *
     * @param file The File
     *
     * @throws DBException if a database error occurs
     */
    protected final void setFile(final Path file) throws DBException {
        this.file = file;
        fileIsNew = !Files.exists(file);
        try {
            if ((!Files.exists(file)) || Files.isWritable(file)) {
                try {
                    raf = new RandomAccessFile(file.toFile(), "rw");
                    final FileChannel channel = raf.getChannel();   
                    final FileLock lock = channel.tryLock();
                    if (lock == null) {
                        readOnly = true;
                    }
                //TODO : who will release the lock ? -pb
                } catch (final NonWritableChannelException e) {
                    //No way : switch to read-only mode
                    readOnly = true;
                    raf = new RandomAccessFile(file.toFile(), "r");
                    LOG.warn(e);
                }
            } else {
                readOnly = true;
                raf = new RandomAccessFile(file.toFile(), "r");
            }
        } catch (final IOException e) {
            LOG.warn("An exception occurred while opening database file {}: {}", file.toAbsolutePath().toString(), e.getMessage(), e);
        }
    }

    /**
     * Unlinks a set of pages starting at the specified page.
     *
     * @param page The starting Page to unlink
     * @throws IOException If an exception occurs
     */
    protected void unlinkPages(final Page page) throws IOException {
        //Mmmmh... is this null test accurate ? -pb
        if (page != null) {
            // Walk the chain and add it to the unused list
            page.header.setStatus(UNUSED);
            page.header.lsn = Lsn.LSN_INVALID;
            synchronized (fileHeader) {
                if (fileHeader.firstFreePage == Page.NO_PAGE) {
                    fileHeader.setFirstFreePage(page.pageNum);
                    page.header.setNextPage(Page.NO_PAGE);
                } else {
                    final long firstFreePage = fileHeader.firstFreePage;
                    fileHeader.setFirstFreePage(page.pageNum);
                    page.header.setNextPage(firstFreePage);
                }
                page.remove();
                fileHeader.setDirty(true);
            }
        }
    }

    /**
     * Unlinks a set of pages starting at the specified page
     * number.
     *
     * @param pageNum A page number
     * @throws IOException if an exception occurs
     */
    protected final void unlinkPages(final long pageNum) throws IOException {
        unlinkPages(getPage(pageNum));
    }

    /**
     * Clears the {@link FileHeader#firstFreePage} and
     *  {@link FileHeader#lastFreePage}.
     *
     * This is needed in recovery, as the free page list
     * may have become corrupted.
     *
     * Unfortunately this means we loose some space
     * that we will never recover, but it does mean
     * we are more likely to correctly recover.
     *
     * @throws IOException if an exception occurs
     */
    protected void dropFreePageList() throws IOException {
        boolean updated = false;
        if (fileHeader.getFirstFreePage() != Page.NO_PAGE) {
            fileHeader.setFirstFreePage(Page.NO_PAGE);
            updated = true;
        }
        if (fileHeader.getLastFreePage() != Page.NO_PAGE) {
            fileHeader.setLastFreePage(Page.NO_PAGE);
            updated = true;
        }

        if (updated) {
            fileHeader.write();
        }
    }

    protected void reuseDeleted(final Page page) throws IOException {
        if (page != null && fileHeader.getFirstFreePage() != Page.NO_PAGE) {

            long firstFreePageNum = fileHeader.getFirstFreePage();
            if (firstFreePageNum == page.pageNum) {
                fileHeader.setFirstFreePage(page.header.getNextPage());
                fileHeader.write();
                return;
            }

            Page firstFreePage = getPage(firstFreePageNum);
            firstFreePage.read();
            firstFreePageNum = firstFreePage.header.getNextPage();

            while (firstFreePageNum != Page.NO_PAGE) {
                if (firstFreePageNum == page.pageNum) {
                    firstFreePage.header.setNextPage(page.header.getNextPage());
                    firstFreePage.header.setDirty(true);
                    firstFreePage.write(null);
                    return;
                }
                firstFreePage = getPage(firstFreePageNum);
                firstFreePage.read();
                firstFreePageNum = firstFreePage.header.getNextPage();
            }
        }
    }

    /**
     * Writes the multi-paged value starting at the specified Page.
     *
     * @param page The starting Page
     * @param value The value to write
     *
     * @throws IOException if an Exception occurs
     */
    protected final void writeValue(final Page page, final Value value) throws IOException {
        final byte[] data = value.getData();
        writeValue(page, data);
    }

    protected final void writeValue(final Page page, final byte[] data) throws IOException {
        final PageHeader pageHeader = page.getPageHeader();
        pageHeader.dataLen = fileHeader.workSize;
        if (data.length != pageHeader.dataLen) {
            //TODO : where to get this 64 from ?
            if (pageHeader.dataLen != getPageSize() - 64) {
                LOG.warn("ouch: {} != {}", fileHeader.workSize, data.length);
            }
            pageHeader.dataLen = data.length;
        }
        page.write(data);
    }

    /**
     * Writes the multi-Paged Value starting at the specified page number.
     *
     * @param page The starting page number
     * @param value The Value to write
     * @throws IOException if an Exception occurs
     */
    protected final void writeValue(final long page, final Value value) throws IOException {
        writeValue(getPage(page), value);
    }

    /**
     * FileHeader
     *
     * @author <a href="mailto:meier@ifs.tu-darmstadt.de">Wolfgang Meier</a>
     */
    public abstract class FileHeader {
        private short version;

        private boolean dirty = false;
        private long firstFreePage = Page.NO_PAGE;

        private short headerSize;
        private long lastFreePage = Page.NO_PAGE;
        private short maxKeySize = 256;
        private long pageCount;
        private byte pageHeaderSize = 64;
        private int pageSize;
        private long recordCount;
        private long totalCount;
        private int workSize;

        private final byte[] buf;

        public FileHeader(final long pageCount, final int pageSize) {
            this.pageSize = pageSize;
            this.pageCount = pageCount;
            this.totalCount = pageCount;
            this.headerSize = (short) pageSize;
            this.version = fileVersion;
            this.buf = new byte[headerSize];
            calculateWorkSize();
        }

        private void calculateWorkSize() {
            workSize = pageSize - pageHeaderSize;
        }

        /**
         * Decrement the number of records being managed by the file
         */
        public final synchronized void decRecordCount() {
            recordCount--;
            dirty = true;
        }

        /**
         * The first free page in unused secondary space
         *
         * @return The firstFreePage value
         */
        public final long getFirstFreePage() {
            return firstFreePage;
        }

        /**
         * The size of the FileHeader. Usually 1 OS Page
         *
         * @return The size value
         */
        public final short getHeaderSize() {
            return headerSize;
        }

        /**
         * The last free page in unused secondary space
         *
         * @return The lastFreePage value
         */
        public final long getLastFreePage() {
            return lastFreePage;
        }

        /**
         * The maximum number of bytes a key can be. 256 is good
         *
         * @return The maxKeySize value
         */
        public int getMaxKeySize() {
            return maxKeySize;
        }

        /**
         * The number of pages in primary storage
         *
         * @return The pageCount value
         */
        public final long getPageCount() {
            return pageCount;
        }

        /**
         * The size of a page header. 64 is sufficient
         *
         *@return The pageHeaderSize value
         */
        public final byte getPageHeaderSize() {
            return pageHeaderSize;
        }

        /**
         * The size of a page. Usually a multiple of a FS block
         *
         * @return The page size
         */
        public final int getPageSize() {
            return pageSize;
        }

        /**
         *  The number of records being managed by the file (not pages)
         *
         *@return    The number of records
         */
        public final long getRecordCount() {
            return recordCount;
        }

        /**
         * The total number of pages in the file
         *
         * @return The total number of pages
         */
        public final long getTotalCount() {
            return totalCount;
        }

        /**
         * Gets the workSize attribute of the FileHeader object
         *
         * @return The workSize value
         */
        public final int getWorkSize() {
            return workSize;
        }
        
        public final short getVersion() {
            return version;
        }
        
        /**
         * Increment the number of records being managed by the file
         */
        public final synchronized void incRecordCount() {
            recordCount++;
            dirty = true;
        }

        /**
         * Returns whether this page has been modified or not.
         *
         * @return <code>true</code> if this page has been modified
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

        public int read(final byte[] buf) throws IOException {
            version = ByteConversion.byteToShort(buf, OFFSET_VERSION_ID);
            headerSize = ByteConversion.byteToShort(buf, OFFSET_HEADER_SIZE);
            pageSize = ByteConversion.byteToInt(buf, OFFSET_PAGE_SIZE);
            pageCount = ByteConversion.byteToLong(buf, OFFSET_PAGE_COUNT);
            totalCount = ByteConversion.byteToLong(buf, OFFSET_TOTAL_COUNT);
            firstFreePage = ByteConversion.byteToLong(buf, OFFSET_FIRST_FREE_PAGE);
            lastFreePage = ByteConversion.byteToLong(buf, OFFSET_LAST_FREE_PAGE);
            pageHeaderSize = buf[OFFSET_PAGE_HEADER_SIZE];
            maxKeySize = ByteConversion.byteToShort(buf, OFFSET_MAX_KEY_SIZE);
            recordCount = ByteConversion.byteToLong(buf, OFFSET_RECORD_COUNT);
            return OFFSET_REMAINDER;
        }

        public int write(final byte[] buf) throws IOException {
            ByteConversion.shortToByte(version, buf, OFFSET_VERSION_ID);
            ByteConversion.shortToByte(headerSize, buf, OFFSET_HEADER_SIZE);
            ByteConversion.intToByte(pageSize, buf, OFFSET_PAGE_SIZE);
            ByteConversion.longToByte(pageCount, buf, OFFSET_PAGE_COUNT);
            ByteConversion.longToByte(totalCount, buf, OFFSET_TOTAL_COUNT);
            ByteConversion.longToByte(firstFreePage, buf, OFFSET_FIRST_FREE_PAGE);
            ByteConversion.longToByte(lastFreePage, buf, OFFSET_LAST_FREE_PAGE);
            buf[OFFSET_PAGE_HEADER_SIZE] = pageHeaderSize;
            ByteConversion.shortToByte(maxKeySize, buf, OFFSET_MAX_KEY_SIZE);
            ByteConversion.longToByte(recordCount, buf, OFFSET_RECORD_COUNT);
            return OFFSET_REMAINDER;
        }

        /**
         * Sets the dirty attribute of the FileHeader object
         *
         * @param dirty The new dirty value
         */
        public final void setDirty(final boolean dirty) {
        	this.dirty = dirty;
        }

        /**
         * The first free page in unused secondary space
         *
         * @param firstFreePage The new first free page number
         */
        public final void setFirstFreePage(final long firstFreePage) {
            this.firstFreePage = firstFreePage;
            dirty = true;
        }

        /**
         * The size of the FileHeader. Usually 1 OS Page
         *
         * @param headerSize The new headerSize value
         */
        public final void setHeaderSize(final short headerSize) {
            this.headerSize = headerSize;
            dirty = true;
        }

        /**
         * The last free page in unused secondary space
         *
         * @param lastFreePage The new lastFreePage value
         */
        public final void setLastFreePage(final long lastFreePage) {
            this.lastFreePage = lastFreePage;
            dirty = true;
        }

        /**
         * The maximum number of bytes a key can be. 256 is good
         *
         * @param maxKeySize The new maximum size for a key
         */
        public final void setMaxKeySize(final short maxKeySize) {
            this.maxKeySize = maxKeySize;
            dirty = true;
        }

        /**
         * The number of pages in primary storage
         *
         * @param  pageCount  The new pageCount value
         */
        public final void setPageCount(final long pageCount) {
            this.pageCount = pageCount;
            dirty = true;
        }

        /**
         * The size of a page header. 64 is sufficient
         *
         * @param pageHeaderSize The new pageHeaderSize value
         */
        public final void setPageHeaderSize(final byte pageHeaderSize) {
            this.pageHeaderSize = pageHeaderSize;
            calculateWorkSize();
            dirty = true;
        }

        /**
         * The size of a page. Usually a multiple of a FS block
         *
         * @param pageSize The new pageSize value
         */
        public final void setPageSize(final int pageSize) {
            this.pageSize = pageSize;
            calculateWorkSize();
            dirty = true;
        }

        /**
         * The number of records being managed by the file (not pages)
         *
         * @param recordCount The new recordCount value
         */
        public final void setRecordCount(final long recordCount) {
            this.recordCount = recordCount;
            dirty = true;
        }

        /**
         * The number of total pages in the file
         *
         * @param totalCount The new totalCount value
         */
        public final void setTotalCount(final long totalCount) {
            this.totalCount = totalCount;
            dirty = true;
        }

        public final synchronized void write() throws IOException {
            raf.seek(0);
            write(buf);
            raf.write(buf);
            dirty = false;
        }
    }

    /**
     * Page
     */
    public final class Page implements Comparable<Page> {

        public static final long NO_PAGE = -1;

        /**  The Header for this Page */
        private final PageHeader header;

        /**  The offset into the file that this page starts */
        private long offset;
        /**  This page number */
        private long pageNum;

        private int refCount = 0;

        public Page() {
            this.header = createPageHeader();
        }

        /**
         * Constructor for the Page object
         *
         * @param pageNum Description of the Parameter
         *
         * @throws IOException Description of the Exception
         */
        public Page(final long pageNum) throws IOException {
            this();
            if(pageNum == Page.NO_PAGE) {
                throw new IOException("Illegal page num: " + pageNum);
            }
            setPageNum(pageNum);
        }

        public void decRefCount() {
            refCount--;
        }

        /**
         * Gets the offset attribute of the Page object
         *
         * @return The offset value
         */
        public long getOffset() {
            return offset;
        }

        /**
         * Gets the pageHeader attribute of the Page object
         *
         * @return The pageHeader value
         */
        public PageHeader getPageHeader() {
            return header;
        }

        /**
         * Gets the pageInfo attribute of the Page object
         *
         * @return The pageInfo value
         */
        public String getPageInfo() {
            return "page: " + pageNum +
                "; file = " + FileUtils.fileName(getFile()) +
                "; address = " + Long.toHexString(offset) +
                "; page header = " + fileHeader.getPageHeaderSize() +
                "; data start = " + Long.toHexString(offset + fileHeader.getPageHeaderSize());
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
            } catch(final Exception e) {
                LOG.warn("error while reading page: {}", getPageInfo(), e);
                throw new IOException(e.getMessage());
            }
        }

        public void setPageNum(final long pageNum) {
            this.pageNum = pageNum;
            offset = fileHeader.headerSize + (pageNum * fileHeader.pageSize);
        }

        public void remove() throws IOException {
            write(null);
        }

        private final void write(final byte[] data) throws IOException {
            if(data == null) {
                // Removed page: fill with 0
                Arrays.fill(tempPageData, (byte)0);
                header.setLsn(Lsn.LSN_INVALID);
            }
            // Write out the header
            header.write(tempPageData, 0);
            header.dirty = false;
            if (data != null) {
                if (data.length > fileHeader.workSize) {
                    throw new IOException("page: " + getPageInfo() + ": data length too large: " + data.length);
                } else {
                    System.arraycopy(data, 0, tempPageData, fileHeader.pageHeaderSize, data.length);
                }
            }
            if (raf.getFilePointer() != offset) {
                raf.seek(offset);
            }
            raf.write(tempPageData);
        }

        @Override
        public boolean equals(final Object obj) {
            return ((Page)obj).pageNum == pageNum;
        }

        @Override
        public int compareTo(final Page other) {
            if (pageNum == other.pageNum) {
                return Constants.EQUAL;
            } else if(pageNum > other.pageNum) {
                return Constants.SUPERIOR;
            } else {
                return Constants.INFERIOR;
            }
        }

        public void dumpPage() throws IOException {
            if (raf.getFilePointer() != offset) {
                raf.seek(offset);
            }
            final byte[] data = new byte[fileHeader.pageSize];
            raf.read(data);
            LOG.debug("Contents of page {}: {}", pageNum, hexDump(data));
        }
    }

    public static abstract class PageHeader {

        public static final int LENGTH_PAGE_STATUS = 1; //sizeof byte
        public static final int LENGTH_PAGE_DATA_LENGTH = 4; //sizeof int
        public static final int LENGTH_PAGE_NEXT_PAGE = 8; //sizeof long
        public static final int LENGTH_PAGE_LSN = Lsn.RAW_LENGTH;

        private int dataLen = 0;
        private boolean dirty = false;
        private long nextPage = Page.NO_PAGE;

        private byte status = UNUSED;

        private Lsn lsn = Lsn.LSN_INVALID;
        
        public PageHeader() {
        }

        public PageHeader(final byte[] data, final int offset) throws IOException {
            read(data, offset);
        }

        /**
         * The length of the Data
         *
         * @return The dataLen value
         */
        public final int getDataLen() {
            return dataLen;
        }

        /**
         * The next page for this Record (if overflowed)
         *
         * @return The nextPage value
         */
        public final long getNextPage() {
            return nextPage;
        }

        /**
         * The status of this page (UNUSED, RECORD, DELETED, etc...)
         * - jmv - DESIGN_NOTE : 44 calls to this functions, mostly with switch;
         * the "state" design pattern is appropriate to eliminate these non - object oriented switches,
         * and put together all the behavior related to one state. 
         * 
         * @return    The status value
         */
        public final byte getStatus() {
            return status;
        }

        /**
         * Gets the dirty attribute of the PageHeader object
         *
         * @return    The dirty value
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
         * @return log sequence number of the last operation that modified this page.
         */
        public final Lsn getLsn() {
            return lsn;
        }

        public final void setLsn(final Lsn lsn) {
            this.lsn = lsn;
        }

        public int read(final byte[] data, int offset) throws IOException {
            status = data[offset];
            offset += LENGTH_PAGE_STATUS;
            dataLen = ByteConversion.byteToInt(data, offset);
            offset += LENGTH_PAGE_DATA_LENGTH;
            nextPage = ByteConversion.byteToLong(data, offset);
            offset += LENGTH_PAGE_NEXT_PAGE;
            lsn = Lsn.read(data, offset);
            offset += LENGTH_PAGE_LSN;
        	return offset;
        }

        public int write(final byte[] data, int offset) throws IOException {
            data[offset] = status;
            offset += LENGTH_PAGE_STATUS;
            ByteConversion.intToByte(dataLen, data, offset);
            offset += LENGTH_PAGE_DATA_LENGTH;
            ByteConversion.longToByte(nextPage, data, offset);
            offset += LENGTH_PAGE_NEXT_PAGE;
            lsn.write(data, offset);
            offset += LENGTH_PAGE_LSN;
            dirty = false;
            return offset;
        }

        /**
         * The length of the Data
         *
         * @param dataLen The new dataLen value
         */
        public final void setDataLen(final int dataLen) {
            this.dataLen = dataLen;
            dirty = true;
        }

        public final void setDirty(final boolean dirty) {
            this.dirty = dirty;
        }

        /**
         * The next page for this Record (if overflowed)
         *
         * @param nextPage The new nextPage value
         */
        public final void setNextPage(final long nextPage) {
            this.nextPage = nextPage;
            dirty = true;
        }

        /**
         * The status of this page (UNUSED, RECORD, DELETED, etc...)
         *
         * @param status The new status value
         */
        public final void setStatus(final byte status) {
            this.status = status;
            dirty = true;
        }
    }

    private static String[] hex = {"0", "1", "2", "3", "4", "5", "6", "7",
        "8", "9", "a", "b", "c", "d", "e", "f"};

    public static String hexDump(final byte[] data) {
        final StringBuilder buf = new StringBuilder();
        int columns = 0;
        for (int i = 0; i < data.length; i++, columns++) {
            byteToHex(buf, data[i]);
            if(columns == 16) {
                columns = 0;
            } else {
                buf.append(' ');
            }
        }
        return buf.toString();
    }

    private static void byteToHex(final StringBuilder buf, final byte b) {
        int n = b;
        if ( n < 0 ) {
            n = 256 + n;
        }
        final int d1 = n / 16;
        final int d2 = n % 16;
        buf.append(hex[d1]);
        buf.append(hex[d2]);
    }

}
