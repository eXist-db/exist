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
package org.exist.dom.persistent;

import org.exist.storage.BrokerPool;
import org.exist.storage.BrokerPoolServiceException;
import org.exist.util.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The symbol table keeps every name when something other than eXist-db replaces or changes symbols.dbx while the
 * database is running, as a file synchronization or an overlay file system's copy-up can: it writes the table
 * again from memory before the next new name, or when it is closed.
 */
class SymbolTableFileReplacedTest {

    @TempDir
    Path dir;

    @Test
    void replacedByAnEarlierCopyBeforeTheNextName() throws Exception {
        final SymbolTable symbols = open();
        final short a = symbols.getSymbol("a");
        final Path earlier = copyOfFile();
        final short b = symbols.getSymbol("b");
        replaceFileWith(earlier);
        final short c = symbols.getSymbol("c");
        symbols.close();

        assertNames(a, "a", b, "b", c, "c");
    }

    @Test
    void replacedByAnEarlierCopyAfterTheLastName() throws Exception {
        final SymbolTable symbols = open();
        final short a = symbols.getSymbol("a");
        final Path earlier = copyOfFile();
        final short b = symbols.getSymbol("b");
        replaceFileWith(earlier);
        symbols.close();

        assertNames(a, "a", b, "b");
    }

    @Test
    void replacedByTheHeaderOnly() throws Exception {
        final Path header = copyOfFileBeforeAnyName();
        final SymbolTable symbols = open();
        final short a = symbols.getSymbol("a");
        final short b = symbols.getSymbol("b");
        replaceFileWith(header);
        final short c = symbols.getSymbol("c");
        symbols.close();

        assertNames(a, "a", b, "b", c, "c");
    }

    @Test
    void truncatedInPlace() throws Exception {
        final SymbolTable symbols = open();
        final short a = symbols.getSymbol("a");
        final short b = symbols.getSymbol("b");
        try (FileChannel channel = FileChannel.open(file(), StandardOpenOption.WRITE)) {
            channel.truncate(4);
        }
        final short c = symbols.getSymbol("c");
        symbols.close();

        assertNames(a, "a", b, "b", c, "c");
    }

    @Test
    void deleted() throws Exception {
        final SymbolTable symbols = open();
        final short a = symbols.getSymbol("a");
        Files.delete(file());
        final short b = symbols.getSymbol("b");
        symbols.close();

        assertNames(a, "a", b, "b");
    }

    @Test
    void replacedByAnEarlierCopyBeforeTheFirstNameOfASession() throws Exception {
        final SymbolTable first = open();
        final short a = first.getSymbol("a");
        final Path earlier = copyOfFile();
        final short b = first.getSymbol("b");
        first.close();

        final SymbolTable second = open();
        replaceFileWith(earlier);
        final short c = second.getSymbol("c");
        second.close();

        assertNames(a, "a", b, "b", c, "c");
    }

    @Test
    void replacedByAnEarlierCopyInASessionWithoutNewNames() throws Exception {
        final SymbolTable first = open();
        final short a = first.getSymbol("a");
        final Path earlier = copyOfFile();
        final short b = first.getSymbol("b");
        first.close();

        final SymbolTable second = open();
        replaceFileWith(earlier);
        second.close();

        assertNames(a, "a", b, "b");
    }

    /**
     * A crash while an entry is appended leaves part of it at the end of the file: the entries before it load, and
     * the file is written again in full before anything is appended after the part.
     */
    @Test
    void partOfAnEntryAtTheEnd() throws Exception {
        final SymbolTable first = open();
        final short a = first.getSymbol("a");
        first.getSymbol("a-longer-name-of-which-only-part-is-written");
        first.close();
        truncateBy(5);

        final SymbolTable second = open();
        assertEquals("a", second.getName(a));
        final short b = second.getSymbol("b");
        second.close();

        assertNames(a, "a", b, "b");
    }

    /**
     * The case from review: after part of an entry is left at the end, a session appends two names, and the session
     * after it loads every name.
     */
    @Test
    void partOfAnEntryAtTheEndThenTwoNamesThenRestart() throws Exception {
        final SymbolTable first = open();
        final short a = first.getSymbol("a");
        first.getSymbol("a-longer-name-of-which-only-part-is-written");
        first.close();
        truncateBy(5);

        final SymbolTable second = open();
        final short c = second.getSymbol("c");
        final short d = second.getSymbol("d");
        second.close();

        assertNames(a, "a", c, "c", d, "d");
    }

    @Test
    void partOfAnEntryAtTheEndInASessionWithoutNewNames() throws Exception {
        final SymbolTable first = open();
        final short a = first.getSymbol("a");
        first.getSymbol("a-longer-name-of-which-only-part-is-written");
        first.close();
        truncateBy(5);

        open().close();
        final SymbolTable third = open();
        final short b = third.getSymbol("b");
        third.close();

        assertNames(a, "a", b, "b");
    }

    /**
     * The id of an incomplete entry, if it can be read, is not given to the next new symbol.
     */
    @Test
    void idOfAnIncompleteEntryIsNotReused() throws Exception {
        final SymbolTable first = open();
        first.getSymbol("a");
        final short torn = first.getSymbol("a-longer-name-of-which-only-part-is-written");
        first.close();
        truncateBy(5);

        final SymbolTable second = open();
        // as an id the table doesn't know
        assertEquals("", second.getName(torn));
        final short b = second.getSymbol("b");
        second.close();

        assertEquals(torn + 1, b);
    }

    @Test
    void partOfAnEntryAtTheEndCutInItsLength() throws Exception {
        final SymbolTable first = open();
        final short a = first.getSymbol("a");
        first.getSymbol("x");
        first.close();
        // type byte and id only
        truncateBy(2);

        final SymbolTable second = open();
        final short b = second.getSymbol("b");
        second.close();

        assertNames(a, "a", b, "b");
    }

    private void truncateBy(final int bytes) throws IOException {
        final long size = Files.size(file());
        try (FileChannel channel = FileChannel.open(file(), StandardOpenOption.WRITE)) {
            channel.truncate(size - bytes);
        }
    }

    /**
     * In a read-only database, a query may look up a name that isn't in the table: if the file can't be written, the
     * name is kept in memory only, and the query doesn't fail.
     */
    @Test
    void readOnlyDatabase() throws Exception {
        open().close();
        final byte[] before = Files.readAllBytes(file());
        final SymbolTable symbols = open(pool(true));
        assertTrue(file().toFile().setWritable(false));
        // a superuser can write the file anyway
        assumeFalse(Files.isWritable(file()));
        try {
            final short id = symbols.getSymbol("not-in-the-table");
            assertEquals("not-in-the-table", symbols.getName(id));
            symbols.close();
        } finally {
            file().toFile().setWritable(true);
        }

        assertArrayEquals(before, Files.readAllBytes(file()));
    }

    /**
     * In a database that can be written, a symbol that can't be written fails the operation, rather than being used
     * without being on disk.
     */
    @Test
    void symbolThatCannotBeWritten() throws Exception {
        open().close();
        final SymbolTable symbols = open(pool(false));
        assertTrue(file().toFile().setWritable(false));
        // a superuser can write the file anyway
        assumeFalse(Files.isWritable(file()));
        try {
            assertThrows(UncheckedIOException.class, () -> symbols.getSymbol("not-in-the-table"));
            // nor was it kept in memory, to be used without being on disk
            assertThrows(UncheckedIOException.class, () -> symbols.getSymbol("not-in-the-table"));
        } finally {
            file().toFile().setWritable(true);
            symbols.close();
        }
    }

    private static BrokerPool pool(final boolean readOnly) {
        final BrokerPool pool = createMock(BrokerPool.class);
        expect(pool.isReadOnly()).andReturn(readOnly).anyTimes();
        replay(pool);
        return pool;
    }

    @Test
    void noTemporaryFileIsLeft() throws Exception {
        final SymbolTable symbols = open();
        symbols.getSymbol("a");
        replaceFileWith(copyOfFileBeforeAnyName());
        symbols.getSymbol("b");
        symbols.close();

        assertFalse(Files.exists(dir.resolve(SymbolTable.getFileName() + ".tmp")));
    }

    @Test
    void unchangedFileIsOnlyAppendedTo() throws Exception {
        final SymbolTable symbols = open();
        final short a = symbols.getSymbol("a");
        final Object key = Files.readAttributes(file(), BasicFileAttributes.class).fileKey();
        final short b = symbols.getSymbol("b");
        symbols.close();

        assertEquals(key, Files.readAttributes(file(), BasicFileAttributes.class).fileKey());
        assertNames(a, "a", b, "b");
    }

    private SymbolTable open() throws BrokerPoolServiceException {
        return open(null);
    }

    private SymbolTable open(final BrokerPool pool) throws BrokerPoolServiceException {
        final SymbolTable symbols = new SymbolTable();
        final Configuration configuration = createMock(Configuration.class);
        expect(configuration.getProperty(BrokerPool.PROPERTY_DATA_DIR)).andReturn(dir);
        replay(configuration);
        symbols.configure(configuration);
        symbols.prepare(pool);
        return symbols;
    }

    private Path file() {
        return dir.resolve(SymbolTable.getFileName());
    }

    private Path copyOfFile() throws IOException {
        final Path copy = Files.createTempFile(dir, "symbols", ".copy");
        Files.copy(file(), copy, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        return copy;
    }

    /**
     * A copy of the file with its header only, as a new database has it.
     */
    private Path copyOfFileBeforeAnyName() throws Exception {
        if (!Files.exists(file())) {
            open().close();
        }
        final Path copy = Files.createTempFile(dir, "symbols", ".header");
        Files.write(copy, Arrays.copyOf(Files.readAllBytes(file()), 4));
        return copy;
    }

    /**
     * Puts a copy in place of the file, as a new file: what a synchronization or a restore does.
     */
    private void replaceFileWith(final Path copy) throws IOException {
        Files.delete(file());
        Files.copy(copy, file(), StandardCopyOption.COPY_ATTRIBUTES);
    }

    /**
     * Opens the symbol table again, as after a restart, and checks the names of the ids.
     */
    private void assertNames(final Object... idsAndNames) throws Exception {
        final SymbolTable symbols = open();
        try {
            for (int i = 0; i < idsAndNames.length; i += 2) {
                assertEquals(idsAndNames[i + 1], symbols.getName((Short) idsAndNames[i]));
            }
        } finally {
            symbols.close();
        }
    }
}
