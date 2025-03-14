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
package org.exist.xquery.modules.file;

import com.evolvedbinary.j8fu.function.Consumer2E;
import org.exist.xmldb.XmldbURI;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.test.TestConstants.TEST_COLLECTION_URI;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for accessing binaries using XQuery via various APIs.
 *
 * @see <a href="https://github.com/eXist-db/exist/issues/790">Binary streaming is broken</a>
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public abstract class AbstractBinariesTest<T, U, E extends Exception> {

    protected static final XmldbURI TEST_COLLECTION = TEST_COLLECTION_URI.append("BinariesTest");
    protected static final String BIN1_FILENAME = "1.bin";
    protected static final byte[] BIN1_CONTENT = "1234567890".getBytes(UTF_8);

    @ClassRule
    public static final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setup() throws Exception {
        storeBinaryFile(TEST_COLLECTION.append(BIN1_FILENAME), BIN1_CONTENT);
    }

    @After
    public void cleanup() throws Exception {
        removeCollection(TEST_COLLECTION);
    }

    /**
     * {@see https://github.com/eXist-db/exist/issues/790#error-case-3}
     */
    @Test
    public void readBinary() throws Exception {
        final byte[] data = randomData(1024 * 1024 * 10);  // 10KB
        final Path tmpFile = createTemporaryFile(data);

        final String query = "import module namespace file = \"http://exist-db.org/xquery/file\";\n" +
                "file:read-binary('" + tmpFile.toAbsolutePath() + "')";

        final QueryResultAccessor<T, E> resultsAccessor = executeXQuery(query);

        resultsAccessor.accept(results -> {
            assertEquals(1, size(results));

            final U item = item(results, 0);
            assertTrue(isBinaryType(item));
            assertArrayEquals(data, getBytes(item));
        });
    }

    /**
     * {@see https://github.com/eXist-db/exist/issues/790#error-case-4}
     */
    @Test
    public void readAndWriteBinary() throws Exception {
        final byte[] data = randomData(1024 * 1024);  // 1MB
        final Path tmpInFile = createTemporaryFile(data);

        final Path tmpOutFile = temporaryFolder.newFile().toPath();

        final String query = "import module namespace file = \"http://exist-db.org/xquery/file\";\n" +
                "let $bin := file:read-binary('" + tmpInFile.toAbsolutePath() + "')\n" +
                "return\n" +
                "    file:serialize-binary($bin, '" + tmpOutFile.toAbsolutePath() + "')";

        final QueryResultAccessor<T, E> resultsAccessor = executeXQuery(query);

        resultsAccessor.accept(results -> {
            assertEquals(1, size(results));

            final U item = item(results, 0);
            assertTrue(isBooleanType(item));
            assertEquals(true, getBoolean(item));
        });

        assertArrayEquals(Files.readAllBytes(tmpInFile), Files.readAllBytes(tmpOutFile));
    }

    protected byte[] randomData(final int size) {
        final byte data[] = new byte[size];
        new Random().nextBytes(data);
        return data;
    }

    protected Path createTemporaryFile(final byte[] data) throws IOException {
        final Path f = temporaryFolder.newFile().toPath();
        Files.write(f, data);
        return f;
    }

    @FunctionalInterface interface QueryResultAccessor<T, E extends Exception> extends Consumer2E<Consumer2E<T, AssertionError, E>, AssertionError, E> {
    }

    protected abstract void storeBinaryFile(final XmldbURI filePath, final byte[] content) throws Exception;
    protected abstract void removeCollection(final XmldbURI collectionUri) throws Exception;
    protected abstract QueryResultAccessor<T, E> executeXQuery(final String query) throws Exception;
    protected abstract long size(T results) throws E;
    protected abstract U item(T results, int index) throws E;
    protected abstract boolean isBinaryType(U item) throws E;
    protected abstract boolean isBooleanType(U item) throws E;
    protected abstract byte[] getBytes(U item) throws E;
    protected abstract boolean getBoolean(U item) throws E;
}
