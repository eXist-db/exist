/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.storage.journal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.DBBroker;

/**
 * Read log entries from the journal file. This class is used during recovery to scan the
 * last journal file. It uses a memory-mapped byte buffer on the file.
 * Journal entries can be read forward (during redo) or backward (during undo). 
 * 
 * @author wolf
 *
 */
public class JournalReader {

    private static final Logger LOG = LogManager.getLogger(JournalReader.class);

    private FileChannel fc;
    private ByteBuffer header = ByteBuffer.allocateDirect(Journal.LOG_ENTRY_HEADER_LEN);
    private ByteBuffer payload = ByteBuffer.allocateDirect(8192);

    private int fileNumber;
    private DBBroker broker;

    /**
     * Opens the specified file for reading.
     * 
     * @param broker
     * @param file
     * @param fileNumber
     * @throws LogException
     */
    public JournalReader(DBBroker broker, File file, int fileNumber) throws LogException {
        this.broker = broker;
        this.fileNumber = fileNumber;
        try {
            final FileInputStream is = new FileInputStream(file);
            fc = is.getChannel();
        } catch (final IOException e) {
            throw new LogException("Failed to read log file " + file.getAbsolutePath(), e);
        }
    }

    /**
     * Returns the next entry found from the current position.
     * 
     * @return the next entry
     * @throws LogException if an entry could not be read due to an inconsistency on disk.
     */
    public Loggable nextEntry() throws LogException {
        try {
            if (fc.position() + Journal.LOG_ENTRY_BASE_LEN > fc.size())
                {return null;}
            return readEntry();
        } catch (final IOException e) {
            return null;
        }
    }

    /**
     * Returns the previous entry found by scanning backwards from the current position.
     * 
     * @return the previous entry
     * @throws LogException if an entry could not be read due to an inconsistency on disk.
     * @throws LogException 
     */
    public Loggable previousEntry() throws LogException {
        try {
            if (fc.position() == 0)
                {return null;}
            // go back two bytes and read the back-link of the last entry
            fc.position(fc.position() - 2);
            header.clear().limit(2);
            final int bytes = fc.read(header);
            if (bytes < 2)
                {throw new LogException("Incomplete log entry found!");}
            header.flip();
            final short prevLink = header.getShort();
            // position the channel to the start of the previous entry and mark it
            final long prevStart = fc.position() - 2 -prevLink;
            fc.position(prevStart);
            final Loggable loggable = readEntry();
            // reset to the mark
            fc.position(prevStart);
            return loggable;
        } catch (final IOException e) {
            throw new LogException("Fatal error while reading journal entry: " + e.getMessage(), e);
        }
    }

    public Loggable lastEntry() throws LogException {
        try {
            fc.position(fc.size());
            return previousEntry();
        } catch (final IOException e) {
            throw new LogException("Fatal error while reading journal entry: " + e.getMessage(), e);
        }
    }

    /**
     * Read a single entry.
     * 
     * @return The entry
     * @throws LogException
     */
    private Loggable readEntry() throws LogException {
        try {
            final long lsn = Lsn.create(fileNumber, (int) fc.position() + 1);
            header.clear();
            int bytes = fc.read(header);
            if (bytes <= 0)
                {return null;}
            if (bytes < Journal.LOG_ENTRY_HEADER_LEN)
                {throw new LogException("Incomplete log entry header found: " + bytes);}
            header.flip();
            final byte entryType = header.get();
            final long transactId = header.getLong();
            final short size = header.getShort();
            if (fc.position() + size > fc.size())
                {throw new LogException("Invalid length");}
            final Loggable loggable = LogEntryTypes.create(entryType, broker, transactId);
            if (loggable == null)
                {throw new LogException("Invalid log entry: " + entryType + "; size: " + size + "; id: " +
                        transactId + "; at: " + Lsn.dump(lsn));}
            loggable.setLsn(lsn);
            if (size + 2 > payload.capacity()) {
                // resize the payload buffer
                payload = ByteBuffer.allocate(size + 2);
            }
            payload.clear().limit(size + 2);
            bytes = fc.read(payload);
            if (bytes < size + 2)
                {throw new LogException("Incomplete log entry found!");}
            payload.flip();
            loggable.read(payload);
            final short prevLink = payload.getShort();
            if (prevLink != size + Journal.LOG_ENTRY_HEADER_LEN) {
                LOG.warn("Bad pointer to previous: prevLink = " + prevLink + "; size = " + size + 
                        "; transactId = " + transactId);
                throw new LogException("Bad pointer to previous in entry: " + loggable.dump());
            }
            return loggable;
        } catch (final Exception e) {
            throw new LogException(e.getMessage(), e);
        }
    }

    /**
     * Re-position the file position so it points to the start of the entry
     * with the given LSN.
     * 
     * @param lsn
     * @throws LogException 
     */
    public void position(long lsn) throws LogException {
        try {
            fc.position((int) Lsn.getOffset(lsn) - 1);
        } catch (final IOException e) {
            throw new LogException("Fatal error while reading journal: " + e.getMessage(), e);
        }
    }

    public void close() {
        try {
            fc.close();
        } catch (final IOException e) {
            //Nothing to do
        }
        fc = null;
    }
}
