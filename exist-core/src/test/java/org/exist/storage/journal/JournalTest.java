/*
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
 */
package org.exist.storage.journal;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.exist.EXistException;
import org.exist.scheduler.Scheduler;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.util.FileUtils;
import org.exist.util.ReadOnlyException;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class JournalTest {

    @ClassRule
    public static final TemporaryFolder TEMPORARY_FOLDER = new TemporaryFolder();

    @Test
    public void getFileName() {
        assertEquals("0000000000.log", Journal.getFileName((short)0));
        assertEquals("0000000001.log", Journal.getFileName((short)1));
        assertEquals("0000000002.log", Journal.getFileName((short)2));
        assertEquals("000000000a.log", Journal.getFileName((short)10));
        assertEquals("000000000b.log", Journal.getFileName((short)11));
        assertEquals("0000000014.log", Journal.getFileName((short)20));
        assertEquals("0000000015.log", Journal.getFileName((short)21));
        assertEquals("000000001e.log", Journal.getFileName((short)30));
        assertEquals("000000001f.log", Journal.getFileName((short)31));

        assertEquals("0000007ffe.log", Journal.getFileName((short)(Short.MAX_VALUE - 1)));
        assertEquals("0000007fff.log", Journal.getFileName(Short.MAX_VALUE));
    }

    @Test(expected = IllegalArgumentException.class)
    public void getFileNameWithFileNumShortMinValueRaisesException() {
        Journal.getFileName(Short.MIN_VALUE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getFileNameWithFileNumMinusOneRaisesException() {
        Journal.getFileName((short)-1);
    }

    @Test
    public void journalFileNum() {
        assertEquals(0, Journal.journalFileNum(Paths.get("0000000000.log")));
        assertEquals(1, Journal.journalFileNum(Paths.get("0000000001.log")));
        assertEquals(2, Journal.journalFileNum(Paths.get("0000000002.log")));
        assertEquals(10, Journal.journalFileNum(Paths.get("000000000a.log")));
        assertEquals(11, Journal.journalFileNum(Paths.get("000000000b.log")));
        assertEquals(20, Journal.journalFileNum(Paths.get("0000000014.log")));
        assertEquals(21, Journal.journalFileNum(Paths.get("0000000015.log")));
        assertEquals(30, Journal.journalFileNum(Paths.get("000000001e.log")));
        assertEquals(31, Journal.journalFileNum(Paths.get("000000001f.log")));

        assertEquals(Short.MAX_VALUE - 1, Journal.journalFileNum(Paths.get("0000007ffe.log")));
        assertEquals(Short.MAX_VALUE, Journal.journalFileNum(Paths.get("0000007fff.log")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void journalFileNumWithPathShortMinValueRaisesException() {
        final String fileName = String.format("%010x", Short.MIN_VALUE) + '.' + Journal.LOG_FILE_SUFFIX;
        Journal.journalFileNum(Paths.get(fileName));
    }

    @Test(expected = IllegalArgumentException.class)
    public void journalFileNumWithPathMinusOneRaisesException() {
        final String fileName = String.format("%010x", -1) + '.' + Journal.LOG_FILE_SUFFIX;
        Journal.journalFileNum(Paths.get(fileName));
    }

    @Test
    public void findLastFile() {
        try (final Stream<Path> paths = Stream.of(
                Paths.get(Journal.getFileName((short)1)),
                Paths.get(Journal.getFileName((short)31)),
                Paths.get(Journal.getFileName((short)11)),
                Paths.get(Journal.getFileName((short)10)),
                Paths.get(Journal.getFileName((short)2)),
                Paths.get(Journal.getFileName(Short.MAX_VALUE)),
                Paths.get(Journal.getFileName((short)20)),
                Paths.get(Journal.getFileName((short)(Short.MAX_VALUE - 1))),
                Paths.get(Journal.getFileName((short)21)),
                Paths.get(Journal.getFileName((short)30))
        )) {
            assertEquals(Short.MAX_VALUE, Journal.findLastFile(paths));
        }

        try (final Stream<Path> paths = Stream.of(
                Paths.get(Journal.getFileName((short)1)),
                Paths.get(Journal.getFileName((short)31)),
                Paths.get(Journal.getFileName((short)11)),
                Paths.get(Journal.getFileName((short)10)),
                Paths.get(Journal.getFileName((short)2)),
                Paths.get(Journal.getFileName((short)20)),
                Paths.get(Journal.getFileName((short)(Short.MAX_VALUE - 1))),
                Paths.get(Journal.getFileName((short)21)),
                Paths.get(Journal.getFileName((short)30))
        )) {
            assertEquals(Short.MAX_VALUE - 1, Journal.findLastFile(paths));
        }

        try (final Stream<Path> paths = Stream.of(
                Paths.get(Journal.getFileName((short)1)),
                Paths.get(Journal.getFileName((short)11)),
                Paths.get(Journal.getFileName((short)2))
        )) {
            assertEquals(11, Journal.findLastFile(paths));
        }

        try (final Stream<Path> paths = Stream.of(
                Paths.get(Journal.getFileName((short)1))
        )) {
            assertEquals(1, Journal.findLastFile(paths));
        }

        try (final Stream<Path> paths = Stream.of(
                Paths.get(Journal.getFileName((short)111)),
                Paths.get(Journal.getFileName((short)1))
        )) {
            assertEquals(111, Journal.findLastFile(paths));
        }

        try (final Stream<Path> paths = Stream.empty()) {
            assertEquals(-1, Journal.findLastFile(paths));
        }
    }

    @Test
    public void getFiles() throws IOException {
        List<String> input = Arrays.asList(new String[]{ "0000000001.log" });
        Path mockJournalDir = createTempDirWithFiles(input);
        List<String> actual = Journal.getFiles(mockJournalDir).map(FileUtils::fileName).collect(Collectors.toList());
        assertEquals(input, actual);

        input = Arrays.asList(new String[]{ "0000000001.log", "0000000002.log", "000000000a.log" });
        mockJournalDir = createTempDirWithFiles(input);
        actual = Journal.getFiles(mockJournalDir).map(FileUtils::fileName).collect(Collectors.toList());
        Collections.sort(input);
        Collections.sort(actual);
        assertEquals(input, actual);

        input = Arrays.asList(new String[]{ "0000000001.log", "0000000001.log" + Journal.BAK_FILE_SUFFIX, "0000000001_index.log", "journal.lck" });
        mockJournalDir = createTempDirWithFiles(input);
        actual = Journal.getFiles(mockJournalDir).map(FileUtils::fileName).collect(Collectors.toList());
        assertEquals(Arrays.asList(new String[] { "0000000001.log" }), actual);
    }

    @Test
    public void getFile() throws IOException {
        List<String> input = Arrays.asList(new String[]{ "0000000001.log" });
        Path mockJournalDir = createTempDirWithFiles(input);

        Path journalFile = Journal.getFile(mockJournalDir, (short)0);
        assertFalse(Files.exists(journalFile));  // no such file!
        assertEquals("0000000000.log", FileUtils.fileName(journalFile));

        journalFile = Journal.getFile(mockJournalDir, (short)1);
        assertTrue(Files.exists(journalFile));
        assertEquals(input.getFirst(), FileUtils.fileName(journalFile));

        journalFile = Journal.getFile(mockJournalDir, (short)2);
        assertFalse(Files.exists(journalFile));  // no such file!
        assertEquals("0000000002.log", FileUtils.fileName(journalFile));

        journalFile = Journal.getFile(mockJournalDir, (short)30);
        assertFalse(Files.exists(journalFile));  // no such file!
        assertEquals("000000001e.log", FileUtils.fileName(journalFile));

        journalFile = Journal.getFile(mockJournalDir, (short)31);
        assertFalse(Files.exists(journalFile));  // no such file!
        assertEquals("000000001f.log", FileUtils.fileName(journalFile));

        journalFile = Journal.getFile(mockJournalDir, (short)(Short.MAX_VALUE - 1));
        assertFalse(Files.exists(journalFile));  // no such file!
        assertEquals("0000007ffe.log", FileUtils.fileName(journalFile));

        journalFile = Journal.getFile(mockJournalDir, Short.MAX_VALUE);
        assertFalse(Files.exists(journalFile));  // no such file!
        assertEquals("0000007fff.log", FileUtils.fileName(journalFile));

        input = Arrays.asList(new String[]{ "0000000001.log", "00000000af.log", "000000000a.log", "000000000f.log" });
        mockJournalDir = createTempDirWithFiles(input);

        journalFile = Journal.getFile(mockJournalDir, (short)1);
        assertTrue(Files.exists(journalFile));
        assertEquals(input.getFirst(), FileUtils.fileName(journalFile));

        journalFile = Journal.getFile(mockJournalDir, (short)175);
        assertTrue(Files.exists(journalFile));
        assertEquals(input.get(1), FileUtils.fileName(journalFile));

        journalFile = Journal.getFile(mockJournalDir, (short)10);
        assertTrue(Files.exists(journalFile));
        assertEquals(input.get(2), FileUtils.fileName(journalFile));

        journalFile = Journal.getFile(mockJournalDir, (short)15);
        assertTrue(Files.exists(journalFile));
        assertEquals(input.get(3), FileUtils.fileName(journalFile));
    }

    @Test(expected = IllegalArgumentException.class)
    public void getFileWithFileNumShortMinValueRaisesException() throws IOException {
        Journal.getFile(TEMPORARY_FOLDER.newFolder().toPath(), Short.MIN_VALUE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getFileWithFileNumMinusOneRaisesException() throws IOException {
        Journal.getFile(TEMPORARY_FOLDER.newFolder().toPath(), (short)-1);
    }


    @Test
    public void writeJournalHeader() throws IOException {
        final SeekableByteChannel mockSeekableByteChannel = mock(SeekableByteChannel.class);
        final Capture<ByteBuffer> captureByteBuffer = newCapture(CaptureType.FIRST);
        expect(mockSeekableByteChannel.write(capture(captureByteBuffer))).andReturn(Journal.JOURNAL_HEADER_LEN);

        replay(mockSeekableByteChannel);

        // call the operation
        Journal.writeJournalHeader(mockSeekableByteChannel);

        final ByteBuffer writtenJournalHeader = captureByteBuffer.getValue();
        assertNotNull(writtenJournalHeader);
        assertEquals(0, writtenJournalHeader.position());
        assertEquals(Journal.JOURNAL_HEADER_LEN, writtenJournalHeader.limit());
        final byte[] bufMagic = new byte[Journal.JOURNAL_MAGIC_NUMBER.length];
        writtenJournalHeader.get(bufMagic);
        assertArrayEquals(Journal.JOURNAL_MAGIC_NUMBER, bufMagic);
        final byte[] bufVersion = new byte[Journal.JOURNAL_HEADER_LEN - Journal.JOURNAL_MAGIC_NUMBER.length];
        writtenJournalHeader.get(bufVersion);
        assertArrayEquals(new byte[] {0, Journal.JOURNAL_VERSION}, bufVersion);

        // verify the mocks
        verify(mockSeekableByteChannel);
    }

    @Test
    public void switchFiles() throws EXistException, IOException, ReadOnlyException, InterruptedException {
        final BrokerPool mockBrokerPool = mock(BrokerPool.class);
        final Configuration mockConfiguration = mock(Configuration.class);
        final Scheduler mockScheduler = createNiceMock(Scheduler.class);
        expect(mockBrokerPool.getConfiguration()).andReturn(mockConfiguration);
        expect(mockConfiguration.getProperty(Journal.PROPERTY_RECOVERY_SYNC_ON_COMMIT, Journal.DEFAULT_SYNC_ON_COMMIT)).andReturn(Journal.DEFAULT_SYNC_ON_COMMIT);
        expect(mockConfiguration.getProperty(Journal.PROPERTY_RECOVERY_JOURNAL_DIR)).andReturn(null);
        expect(mockConfiguration.getProperty(Journal.PROPERTY_RECOVERY_SIZE_MIN, Journal.DEFAULT_MIN_SIZE)).andReturn(Journal.DEFAULT_MIN_SIZE);
        expect(mockConfiguration.getProperty(Journal.PROPERTY_RECOVERY_SIZE_LIMIT, Journal.DEFAULT_MAX_SIZE)).andReturn(Journal.DEFAULT_MAX_SIZE);
        expect(mockBrokerPool.getScheduler()).andReturn(mockScheduler);

        replay(mockBrokerPool, mockConfiguration);

        final Path tempJournalDir = TEMPORARY_FOLDER.newFolder().toPath();
        Files.createDirectories(tempJournalDir);
        assertTrue(Files.exists(tempJournalDir));

        final Journal journal = new Journal(mockBrokerPool, tempJournalDir);
        journal.initialize();

        assertEquals(-1, journal.getCurrentJournalFileNumber());  // journal is not yet operating!

        // start the journal by calling switch files
        journal.switchFiles();

        short currentJournalFileNumber = journal.getCurrentJournalFileNumber();
        assertEquals(0, currentJournalFileNumber);
        final Path journalFile0 = journal.getFile(currentJournalFileNumber);
        assertNotNull(journalFile0);
        assertTrue(Files.exists(journalFile0));

        // advance to a new journal by calling switch files
        journal.switchFiles();
        currentJournalFileNumber = journal.getCurrentJournalFileNumber();
        assertEquals(1, currentJournalFileNumber);
        final Path journalFile1 = journal.getFile(currentJournalFileNumber);
        assertNotNull(journalFile1);
        assertNotEquals(journalFile0.toAbsolutePath().toString(), journalFile1.toAbsolutePath().toString());
        assertTrue(Files.exists(journalFile1));

        verify(mockBrokerPool, mockConfiguration);
    }

    @Test
    public void switchFilesBacksUpOldFileFirst() throws EXistException, IOException, ReadOnlyException {
        final BrokerPool mockBrokerPool = mock(BrokerPool.class);
        final Configuration mockConfiguration = mock(Configuration.class);
        final Scheduler mockScheduler = createNiceMock(Scheduler.class);
        expect(mockBrokerPool.getConfiguration()).andReturn(mockConfiguration);
        expect(mockConfiguration.getProperty(Journal.PROPERTY_RECOVERY_SYNC_ON_COMMIT, Journal.DEFAULT_SYNC_ON_COMMIT)).andReturn(Journal.DEFAULT_SYNC_ON_COMMIT);
        expect(mockConfiguration.getProperty(Journal.PROPERTY_RECOVERY_JOURNAL_DIR)).andReturn(null);
        expect(mockConfiguration.getProperty(Journal.PROPERTY_RECOVERY_SIZE_MIN, Journal.DEFAULT_MIN_SIZE)).andReturn(Journal.DEFAULT_MIN_SIZE);
        expect(mockConfiguration.getProperty(Journal.PROPERTY_RECOVERY_SIZE_LIMIT, Journal.DEFAULT_MAX_SIZE)).andReturn(Journal.DEFAULT_MAX_SIZE);
        expect(mockBrokerPool.getScheduler()).andReturn(mockScheduler);

        replay(mockBrokerPool, mockConfiguration);

        final Path tempJournalDir = TEMPORARY_FOLDER.newFolder().toPath();
        Files.createDirectories(tempJournalDir);
        assertTrue(Files.exists(tempJournalDir));

        final Journal journal = new Journal(mockBrokerPool, tempJournalDir);
        journal.initialize();

        assertEquals(-1, journal.getCurrentJournalFileNumber());  // journal is not yet operating!

        // create an existing (old) journal file first
        final Path existingJournalFile = Files.createFile(tempJournalDir.resolve(Journal.getFileName((short)0)));
        assertTrue(Files.exists(existingJournalFile));

        // start the journal by calling switch files
        journal.switchFiles();

        short currentJournalFileNumber = journal.getCurrentJournalFileNumber();
        assertEquals(0, currentJournalFileNumber);
        final Path journalFile0 = journal.getFile(currentJournalFileNumber);
        assertNotNull(journalFile0);
        assertTrue(Files.exists(journalFile0));

        // check that the existingJournalFile was moved to a backup
        assertTrue(Files.exists(existingJournalFile.resolveSibling(FileUtils.fileName(existingJournalFile) + Journal.BAK_FILE_SUFFIX)));

        verify(mockBrokerPool, mockConfiguration);
    }

    @Test
    public void switchFilesWrapsCurrentJournalFileNumberAround() throws EXistException, IOException, ReadOnlyException {
        final BrokerPool mockBrokerPool = mock(BrokerPool.class);
        final Configuration mockConfiguration = mock(Configuration.class);
        final Scheduler mockScheduler = createNiceMock(Scheduler.class);
        expect(mockBrokerPool.getConfiguration()).andReturn(mockConfiguration);
        expect(mockConfiguration.getProperty(Journal.PROPERTY_RECOVERY_SYNC_ON_COMMIT, Journal.DEFAULT_SYNC_ON_COMMIT)).andReturn(Journal.DEFAULT_SYNC_ON_COMMIT);
        expect(mockConfiguration.getProperty(Journal.PROPERTY_RECOVERY_JOURNAL_DIR)).andReturn(null);
        expect(mockConfiguration.getProperty(Journal.PROPERTY_RECOVERY_SIZE_MIN, Journal.DEFAULT_MIN_SIZE)).andReturn(Journal.DEFAULT_MIN_SIZE);
        expect(mockConfiguration.getProperty(Journal.PROPERTY_RECOVERY_SIZE_LIMIT, Journal.DEFAULT_MAX_SIZE)).andReturn(Journal.DEFAULT_MAX_SIZE);
        expect(mockBrokerPool.getScheduler()).andReturn(mockScheduler);

        replay(mockBrokerPool, mockConfiguration);

        final Path tempJournalDir = TEMPORARY_FOLDER.newFolder().toPath();
        Files.createDirectories(tempJournalDir);
        assertTrue(Files.exists(tempJournalDir));

        final Journal journal = new Journal(mockBrokerPool, tempJournalDir);
        journal.initialize();

        assertEquals(-1, journal.getCurrentJournalFileNumber());  // journal is not yet operating!

        // set the current journal file number to one less than the maximum
        journal.setCurrentJournalFileNumber((short)(Short.MAX_VALUE - 1));
        short currentJournalFileNumber = journal.getCurrentJournalFileNumber();
        assertEquals(Short.MAX_VALUE - 1, currentJournalFileNumber);  // journal is not yet operating!

        // start the journal by calling switch files
        journal.switchFiles();

        currentJournalFileNumber = journal.getCurrentJournalFileNumber();
        assertEquals(Short.MAX_VALUE, currentJournalFileNumber);        // we should now be at the maximum
        final Path journalFileMax = journal.getFile(currentJournalFileNumber);
        assertNotNull(journalFileMax);
        assertTrue(Files.exists(journalFileMax));

        // advance to a new journal by calling switch files, this should cause the journal file number to wrap around
        journal.switchFiles();
        currentJournalFileNumber = journal.getCurrentJournalFileNumber();
        assertEquals(0, currentJournalFileNumber);  // should be back at the start of the range for journal file numbers
        final Path journalFile0 = journal.getFile(currentJournalFileNumber);
        assertNotNull(journalFile0);
        assertNotEquals(journalFileMax.toAbsolutePath().toString(), journalFile0.toAbsolutePath().toString());
        assertTrue(Files.exists(journalFile0));

        verify(mockBrokerPool, mockConfiguration);
    }

    private static Path createTempDirWithFiles(final List<String> fileNames) throws IOException {
        final Path tempFolder = TEMPORARY_FOLDER.newFolder().toPath();
        Files.createDirectories(tempFolder);
        for (final String fileName : fileNames) {
            final Path file = Files.createFile(tempFolder.resolve(fileName));
            assertTrue(Files.exists(file));
        }
        return tempFolder;
    }
}
