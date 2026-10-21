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
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
        final SymbolTable symbols = new SymbolTable();
        final Configuration configuration = createMock(Configuration.class);
        expect(configuration.getProperty(BrokerPool.PROPERTY_DATA_DIR)).andReturn(dir);
        replay(configuration);
        symbols.configure(configuration);
        symbols.prepare(null);
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

    private Path copyOfFileBeforeAnyName() throws Exception {
        open().close();
        return copyOfFile();
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
