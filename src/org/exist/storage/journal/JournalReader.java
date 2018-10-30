/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2013 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.storage.journal;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.DBBroker;
import org.exist.util.ByteConversion;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.READ;
import static org.exist.storage.journal.Journal.*;

/**
 * Read log entries from the journal file. This class is used during recovery to scan the
 * last journal file. It uses a memory-mapped byte buffer on the file.
 * Journal entries can be read forward (during redo) or backward (during undo).
 *
 * @author wolf
 */
public class JournalReader implements AutoCloseable {

    private static final Logger LOG = LogManager.getLogger(JournalReader.class);

    private final DBBroker broker;
    private final int fileNumber;
    private final ByteBuffer header = ByteBuffer.allocateDirect(LOG_ENTRY_HEADER_LEN);
    private ByteBuffer payload = ByteBuffer.allocateDirect(8192);  // 8 KB
    @Nullable
    private SeekableByteChannel fc;

    /**
     * Opens the specified file for reading.
     *
     * @param broker     the database broker
     * @param file       the journal file
     * @param fileNumber the number of the journal file
     * @throws LogException if the journal cannot be opened
     */
    public JournalReader(final DBBroker broker, final Path file, final int fileNumber) throws LogException {
        this.broker = broker;
        this.fileNumber = fileNumber;
        try {
            this.fc = Files.newByteChannel(file, READ);
            validateJournalHeader(file, fc);
        } catch (final IOException e) {
            close();
            throw new LogException("Failed to read journal file " + file.toAbsolutePath().toString(), e);
        }
    }

    private void validateJournalHeader(final Path file, final SeekableByteChannel fc) throws IOException, LogException {
        // read the magic number
        final ByteBuffer buf = ByteBuffer.allocate(JOURNAL_HEADER_LEN);
        fc.read(buf);
        buf.flip();

        // check the magic number
        final boolean validMagic =
                buf.get() == JOURNAL_MAGIC_NUMBER[0]
                && buf.get() == JOURNAL_MAGIC_NUMBER[1]
                && buf.get() == JOURNAL_MAGIC_NUMBER[2]
                && buf.get() == JOURNAL_MAGIC_NUMBER[3];

        if (!validMagic) {
            throw new LogException("File was not recognised as a valid eXist-db journal file: " + file.toAbsolutePath().toString());
        }

        // check the version of the journal format
        final short storedVersion = ByteConversion.byteToShortH(new byte[] {buf.get(), buf.get()}, 0);
        final boolean validVersion =
                storedVersion == JOURNAL_VERSION;

        if (!validVersion) {
            throw new LogException("Journal file was version " + storedVersion + ", but required version " + JOURNAL_VERSION + ": " + file.toAbsolutePath().toString());
        }
    }

    /**
     * Returns the next entry found from the current position.
     *
     * @return the next entry, or null if there are no more entries.
     * @throws LogException if an entry could not be read due to an inconsistency on disk.
     */
    public @Nullable
    Loggable nextEntry() throws LogException {
        try {
            checkOpen();

            // are we at the end of the journal?
            if (fc.position() + LOG_ENTRY_BASE_LEN > fc.size()) {
                return null;
            }
        } catch (final IOException e) {
            throw new LogException("Unable to check journal position and size: " + e.getMessage(), e);
        }

        return readEntry();
    }

    /**
     * Returns the previous entry found by scanning backwards from the current position.
     *
     * @return the previous entry, or null of there was no previous entry.
     * @throws LogException if an entry could not be read due to an inconsistency on disk.
     */
    public @Nullable
    Loggable previousEntry() throws LogException {
        try {
            checkOpen();

            // is there a previous entry to read?
            if (fc.position() < JOURNAL_HEADER_LEN + LOG_ENTRY_BASE_LEN) {
                return null;
            }

            // go back two bytes and read the back-link of the last entry
            fc.position(fc.position() - LOG_ENTRY_BACK_LINK_LEN);
            header.clear().limit(LOG_ENTRY_BACK_LINK_LEN);
            final int read = fc.read(header);
            if (read != LOG_ENTRY_BACK_LINK_LEN) {
                throw new LogException("Unable to read journal entry back-link!");
            }
            header.flip();
            final short prevLink = header.getShort();

            // position the channel to the start of the previous entry and mark it
            final long prevStart = fc.position() - LOG_ENTRY_BACK_LINK_LEN - prevLink;
            fc.position(prevStart);
            final Loggable loggable = readEntry();

            // reset to the mark
            fc.position(prevStart);
            return loggable;
        } catch (final IOException e) {
            throw new LogException("Fatal error while reading previous journal entry: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the last entry in the journal.
     *
     * @return the last entry in the journal, or null if there are no entries in the journal.
     * @throws LogException if an entry could not be read due to an inconsistency on disk.
     */
    public @Nullable
    Loggable lastEntry() throws LogException {
        try {
            checkOpen();
            positionLast();
            return previousEntry();
        } catch (final IOException e) {
            throw new LogException("Fatal error while reading last journal entry: " + e.getMessage(), e);
        }
    }

    /**
     * Read the current entry from the journal.
     *
     * @return The entry, or null if there is no entry.
     * @throws LogException if an entry could not be read due to an inconsistency on disk.
     */
    private @Nullable
    Loggable readEntry() throws LogException {
        try {
            final long offset = fc.position();
            if (offset > Integer.MAX_VALUE) {
                throw new LogException("Journal can only read log files of less that 2GB");
            }
            final long lsn = Lsn.create(fileNumber, ((int)(offset & 0x7FFFFFFF)) + 1);

            // read the entry header
            header.clear();
            int read = fc.read(header);
            if (read <= 0) {
                return null;
            }
            if (read != LOG_ENTRY_HEADER_LEN) {
                throw new LogException("Incomplete journal entry header found, expected  "
                        + LOG_ENTRY_HEADER_LEN + " bytes, but found " + read + " bytes");
            }
            header.flip();

            final byte entryType = header.get();
            final long transactId = header.getLong();
            final short size = header.getShort();
            if (fc.position() + size > fc.size()) {
                throw new LogException("Invalid length");
            }

            final Loggable loggable = LogEntryTypes.create(entryType, broker, transactId);
            if (loggable == null) {
                throw new LogException("Invalid log entry: " + entryType + "; size: " + size + "; id: " +
                        transactId + "; at: " + Lsn.dump(lsn));
            }
            loggable.setLsn(lsn);

            if (size + LOG_ENTRY_BACK_LINK_LEN > payload.capacity()) {
                // resize the payload buffer
                payload = ByteBuffer.allocate(size + LOG_ENTRY_BACK_LINK_LEN);
            }
            payload.clear().limit(size + LOG_ENTRY_BACK_LINK_LEN);
            read = fc.read(payload);
            if (read < size + LOG_ENTRY_BACK_LINK_LEN) {
                throw new LogException("Incomplete log entry found!");
            }
            payload.flip();
            loggable.read(payload);
            final short prevLink = payload.getShort();
            if (prevLink != size + LOG_ENTRY_HEADER_LEN) {
                LOG.error("Bad pointer to previous: prevLink = " + prevLink + "; size = " + size +
                        "; transactId = " + transactId);
                throw new LogException("Bad pointer to previous in entry: " + loggable.dump());
            }
            return loggable;
        } catch (final IOException e) {
            throw new LogException(e.getMessage(), e);
        }
    }

    /**
     * Re-position the file position so it points to the start of the entry
     * with the given LSN.
     *
     * @param lsn the log sequence number
     * @throws LogException if the journal file cannot be re-positioned
     */
    public void position(final long lsn) throws LogException {
        try {
            checkOpen();
            fc.position((int) Lsn.getOffset(lsn) - 1);
        } catch (final IOException e) {
            throw new LogException("Fatal error while seeking journal: " + e.getMessage(), e);
        }
    }

    /**
     * Re-position the file position so it points to the first entry.
     *
     * @throws LogException if the journal file cannot be re-positioned
     */
    public void positionFirst() throws LogException {
        try {
            checkOpen();
            fc.position(JOURNAL_HEADER_LEN);
        } catch (final IOException e) {
            throw new LogException("Fatal error while seeking first journal entry: " + e.getMessage(), e);
        }
    }

    /**
     * Re-position the file position so it points to the last entry.
     *
     * @throws LogException if the journal file cannot be re-positioned
     */
    public void positionLast() throws LogException {
        try {
            checkOpen();
            fc.position(fc.size());
        } catch (final IOException e) {
            throw new LogException("Fatal error while seeking last journal entry: " + e.getMessage(), e);
        }
    }

    private void checkOpen() throws IOException {
        if (fc == null) {
            throw new IOException("Journal file is closed");
        }
    }

    @Override
    public void close() {
        try {
            if (fc != null) {
                fc.close();
            }
        } catch (final IOException e) {
            LOG.warn(e.getMessage(), e);
        }
        fc = null;
    }
}
