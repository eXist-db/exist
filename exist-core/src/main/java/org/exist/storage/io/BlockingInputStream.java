/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id: BlockingInputStream.java 223 2007-04-21 22:13:05Z dizzzz $
 */

package org.exist.storage.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * <code>BlockingInputStream</code> is a combination of an input stream and
 * an output stream, connected through a (circular) buffer in memory.
 * It is intended for coupling producer threads to consumer threads via a
 * (byte) stream.
 * When the buffer is full producer threads will be blocked until the buffer
 * has some free space again. When the buffer is empty the consumer threads will
 * be blocked until some bytes are available again.
 * Closing of the output stream will block until the inputstream is closed.
 * A special version of the close function enables the consumer threads to
 * specify that an exception has occurred. This will cause producer calls to
 * be unblocked and throw an IOException containing this exception as cause.
 *
 * @author Chris Offerman
 */
public class BlockingInputStream extends InputStream {

    private final static int EOS      = -1;
    private static final int CAPACITY = 8192;
    private static final int SIZE     = CAPACITY + 1;

    private byte[] buffer = new byte[SIZE];  // Circular queue.
    private int head;  // First full buffer position.
    private int tail;  // First empty buffer position.
    private boolean    inClosed;     // Is the input  stream closed?
    private boolean   outClosed;     // Is the output stream closed?
    private Exception  inException;  // Specified when closing input.
    private Exception outException;  // Specified when closing output.

    private BlockingOutputStream bos = new BlockingOutputStream(this);

    /**
     * @return BlockingOutputStream adapter for this BlockingInputStream.
     *
     */
    public BlockingOutputStream getOutputStream() {
        return bos;
    }

    /** Is a stream closed? */
    private boolean closed() {
        return inClosed || outClosed;
    }

    /* InputStream methods */

    /**
     * Reads the next byte of data from the input stream. The value byte is
     * returned as an <code>int</code> in the range <code>0</code> to
     * <code>255</code>. If no byte is available because the end of the stream
     * has been reached, the value <code>-1</code> is returned. This method
     * blocks until input data is available, the end of the stream is detected,
     * or an exception is thrown.
     * 
     * 
     * @return the next byte of data, or <code>-1</code> if the end of the
     *             stream is reached.
     * @throws IOException  if an I/O error occurs.
     */
    @Override
    public synchronized int read() throws IOException {
        final byte bb[] = new byte[1];
        return (read(bb, 0, 1) == EOS) ? EOS : bb[0];
    }

    /**
     * Reads up to <code>len</code> bytes of data from the input stream into
     * an array of bytes.  An attempt is made to read as many as
     * <code>len</code> bytes, but a smaller number may be read.
     * The number of bytes actually read is returned as an integer.
     * 
     *  This method blocks until input data is available, end of file is
     * detected, or an exception is thrown.
     * 
     * 
     * @param b     the buffer into which the data is read.
     * @param off   the start offset in array <code>b</code>
     *                   at which the data is written.
     * @param len   the maximum number of bytes to read.
     * @return the total number of bytes read into the buffer, or
     *             <code>-1</code> if there is no more data because the end of
     *             the stream has been reached.
     * @throws IOException  if an I/O error occurs.
     * @throws NullPointerException  if <code>b</code> is <code>null</code>.
     */
    @Override
    public synchronized int read(byte b[], int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if ((off < 0) || (off > b.length) || (len < 0) ||
                ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }
        int count = EOS;
        try {
            while (empty() && !closed()) wait();
            if (outException != null) {throw new IOException(
              "BlockingOutputStream closed with an exception.", outException);}
            else if (!closed()) {
                count = Math.min(len, available());
                final int count1 = Math.min(count, availablePart1());
                System.arraycopy(buffer, head, b, off, count1);
                final int count2 = count - count1;
                if (count2 > 0) {
                    System.arraycopy(buffer, 0, b, off + count1, count2);
                }
                head = next(head, count);
                if (empty()) {head = tail = 0;} // Reset to optimal situation.
            }
        } catch (final InterruptedException e) {
            throw new IOException("Read operation interrupted.", e);
        } finally {
            notifyAll();
        }
        return count;
    }

    /**
     * Closes this input stream and releases the buffer associated
     * with this stream.
     */
    @Override
    public synchronized void close() {
        inClosed = true;
        buffer = null;
        notifyAll();
    }

    /**
     * Closes this input stream, specifying that an exception has occurred.
     * This will cause all producer calls to be unblocked and throw an
     * ExistIOException with this exception as its cause.
     * Releases the buffer associated with this stream.
     * <code>BlockingInputStream</code> specific method.
     * @param ex the occurred exception
     */
    public synchronized void close(Exception ex) {
        inException = ex;
        close();
    }

    /**
     * The number of bytes that can be read (or skipped over) from
     * this input stream without blocking by the next caller of a method for
     * this input stream.
     * 
     * 
     * @return the number of bytes that can be read from this input stream
     *             without blocking.
     */
    @Override
    public synchronized int available() {
        return (tail - head + SIZE) % SIZE;
    }

    private int availablePart1() {
        return (tail >= head) ? tail - head : SIZE - head; 
    }

    // DWES Never called?
    @SuppressWarnings("unused")
	private int availablePart2() {
        return (tail >= head) ? 0 : tail;
    }

    /* OutputStream methods */

    /**
     * Writes the specified byte to this output stream. The general 
     * contract for <code>write</code> is that one byte is written 
     * to the output stream. The byte to be written is the eight 
     * low-order bits of the argument <code>b</code>. The 24 
     * high-order bits of <code>b</code> are ignored.
     * 
     * 
     * @param b   the <code>byte</code>.
     * @throws ExistIOException  if an I/O error occurs. In particular, 
     *             an <code>ExistIOException</code> may be thrown if the 
     *             output stream has been closed.
     */
    synchronized void writeOutputStream(int b) throws IOException {
        final byte bb[] = { (byte) b };
        writeOutputStream(bb, 0, 1);
    }

    /**
     * Writes <code>len</code> bytes from the specified byte array 
     * starting at offset <code>off</code> to this output stream. 
     * The general contract for <code>write(b, off, len)</code> is that 
     * some of the bytes in the array <code>b</code> are written to the 
     * output stream in order; element <code>b[off]</code> is the first 
     * byte written and <code>b[off+len-1]</code> is the last byte written 
     * by this operation.
     * 
     * 
     * @param b     the data.
     * @param off   the start offset in the data.
     * @param len   the number of bytes to write.
     * @throws IOException  if an I/O error occurs. In particular, 
     *             an <code>IOException</code> is thrown if the output 
     *             stream is closed.
     */
    synchronized void writeOutputStream(byte b[], int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if ((off < 0) || (off > b.length) || (len < 0) ||
                ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        }
        while (len > 0) {
            int count;
            try {
                while (full() && !closed()) wait();
            if (inException != null) {throw new IOException(
              "BlockingInputStream closed with exception.", inException);}
            else if (closed()) {throw new IOException(
                    "Writing to closed stream", inException);}
                count = Math.min(len, free());
                final int count1 = Math.min(count, freePart1());
                System.arraycopy(b, off, buffer, tail, count1);
                final int count2 = count - count1;
                if (count2 > 0) {
                    System.arraycopy(b, off + count1, buffer, 0, count2);                    
                }
                tail = next(tail, count);
            } catch (final InterruptedException e) {
                throw new IOException("Write operation interrupted.", e);
            } finally {
                notifyAll();
            }
            off += count;
            len -= count;
        }
    }

    /**
     * Equivalent of the <code>close()</code> method of an output stream.
     * Renamed to solve the name clash with the <code>close()</code> method
     * of the input stream also implemented by this class.
     * Closes this output stream.
     * A closed stream cannot perform output operations and cannot be reopened.
     *
     * This method blocks its caller until the corresponding input stream is
     * closed or an exception occurs.
     * 
     * @throws IOException  if an I/O error occurs.
     */
    synchronized void closeOutputStream() throws IOException {
        if (outException == null) {flushOutputStream();}
        outClosed = true;
        notifyAll();
        try {
            while(!inClosed) wait();
            if (inException != null) {throw new IOException(
                "BlockingInputStream closed with an exception.", inException);}
            else if (!empty()) {throw new IOException(
                "Closing non empty closed stream.", inException);}
        } catch (final InterruptedException e) {
            throw new IOException(
                "Close OutputStream operation interrupted.", e);
        }
    }

    /**
     * Closes this output stream, specifying that an exception has occurred.
     * This will cause all consumer calls to be unblocked and throw an
     * IOException with this exception as its cause.
     * <code>BlockingInputStream</code> specific method.

     * @throws IOException  if an I/O error occurs.
     */
    synchronized void closeOutputStream(Exception ex) throws IOException {
        outException = ex;
        closeOutputStream();
    }

    /**
     * Flushes this output stream and forces any buffered output bytes 
     * to be written out.
     *
     * This methods blocks its caller until all buffered bytes are actually
     * read by the consuming threads.
     * 
     * 
     * @throws IOException  if an I/O error occurs.
     */
    synchronized void flushOutputStream() throws IOException {
        try {
            while(!empty() && !closed()) wait();
            if (inException != null) {throw new IOException(
                "BlockingInputStream closed with an exception.", inException);}
            else if (!empty()) {throw new IOException(
                "Flushing non empty closed stream.", inException);}
        } catch (final InterruptedException e) {
            throw new IOException("Flush operation interrupted.", e);
        } finally {
            notifyAll();
        }
    }

    /**
     * The number of bytes that can be written to
     * this output stream without blocking by the next caller of a method for
     * this output stream.
     * 
     * 
     * @return the number of bytes that can be written to this output stream
     *             without blocking.
     * @throws IOException  if an I/O error occurs.
     */
    private synchronized int free() {
        final int prevhead = prev(head);
        return (prevhead - tail + SIZE) % SIZE;
    }

    private int freePart1() {
        final int prevhead = prev(head);
        return (prevhead >= tail) ? prevhead - tail : SIZE - tail;
    }

    // DWES Never called?
    @SuppressWarnings("unused")
	private int freePart2() {
        final int prevhead = prev(head);
        return (prevhead >= tail) ? 0 : prevhead;
    }

    /* Buffer management methods */

    private boolean empty() {
        return head == tail;
    }

    private boolean full() {
        return next(tail) == head;
    }

    private static int next(int pos) {
        return next(pos, 1);
    }

    private static int next(int pos, int incr) {
        return (pos + incr) % SIZE;
    }

    private static int prev(int pos) {
        return prev(pos, 1);
    }

    private static int prev(int pos, int decr) {
        return (pos  - decr + SIZE) % SIZE;
    }
}

