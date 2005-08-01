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
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import org.apache.log4j.Logger;
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
	
    private static final Logger LOG = Logger.getLogger(JournalReader.class);
    
	private MappedByteBuffer mapped;
	private FileChannel fc;
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
			FileInputStream is = new FileInputStream(file);
			fc = is.getChannel();
			mapped = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
		} catch (IOException e) {
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
		if (mapped.position() + Journal.LOG_ENTRY_BASE_LEN > mapped.capacity())
			return null;
		return readEntry();
	}
	
    /**
     * Returns the previous entry found by scanning backwards from the current position.
     * 
     * @return the previous entry
     * @throws LogException if an entry could not be read due to an inconsistency on disk.
     */
    public Loggable previousEntry() throws LogException {
        if (mapped.position() == 0)
            return null;
        // go back two bytes and read the back-link of the last entry
        mapped.position(mapped.position() - 2);
        final short prevLink = mapped.getShort();
        // position the channel to the start of the previous entry and mark it
        final int prevStart = mapped.position() - 2 -prevLink;
        mapped.position(prevStart);
        final Loggable loggable = readEntry();
        // reset to the mark
        mapped.position(prevStart);
        return loggable;
    }
    
    /**
     * Read a single entry.
     * 
     * @return
     * @throws LogException
     */
    private Loggable readEntry() throws LogException {
        final long lsn = Lsn.create(fileNumber, mapped.position() + 1);
        final byte entryType = mapped.get();
        final long transactId = mapped.getLong();
        final short size = mapped.getShort();
        if (mapped.position() + size > mapped.capacity())
            throw new LogException("Invalid length");
        final Loggable loggable = LogEntryTypes.create(entryType, broker, transactId);
        if (loggable == null)
            throw new LogException("Invalid log entry: " + entryType + "; size: " + size + "; id: " +
                    transactId + "; at: " + Lsn.dump(lsn));
        loggable.setLsn(lsn);
        loggable.read(mapped);
        final short prevLink = mapped.getShort();
        if (prevLink != size + Journal.LOG_ENTRY_HEADER_LEN) {
            LOG.warn("Bad pointer to previous: prevLink = " + prevLink + "; size = " + size + 
                    "; transactId = " + transactId);
            throw new LogException("Bad pointer to previous in entry: " + loggable.dump());
        }
        return loggable;
    }
    
    /**
     * Re-position the file position so it points to the start of the entry
     * with the given LSN.
     * 
     * @param lsn
     */
    public void position(long lsn) {
        mapped.position((int) Lsn.getOffset(lsn) - 1);
    }
    
	public void close() {		
		try {
			fc.close();
		} catch (IOException e) {
		}
        mapped = null;
	}
}
