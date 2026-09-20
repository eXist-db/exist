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
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 * @see <a href="https://github.com/eXist-db/exist/issues/790">Binary streaming is broken</a>
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
     * A file-backed binary (file:read-binary -&gt; BinaryValueFromFile) used in an element constructor and
     * then read again must remain readable. Without BinaryValueFromFile's shared-reference reference
     * counting, XQueryContext.exitEnclosedExpr() closed the channel immediately after the constructor and
     * the second read failed with "Underlying channel has been closed". {@code count($w)} forces the
     * constructor to be evaluated before {@code $b} is read again.
     *
     * <p>This runs as a <em>root-context main module</em>, the shape in which the bug was reported.
     * It used to be the only shape that reproduced it: a {@code ModuleContext} (an XQSuite test function,
     * or {@code util:eval}) delegated {@code registerBinaryValueInstance()} to the parent/root context
     * while the enclosed-expression hooks acted on the {@code ModuleContext}'s own (empty) deque, so a
     * module context silently never saw the premature close. {@code ModuleContext} now delegates the
     * whole binary value registry, so XQSuite exercises this path too - see
     * {@code exist-core/src/test/xquery/binary-value.xqm}. Keep the Java tests regardless: they are the
     * ones that run the query as a main module.</p>
     */
    @Test
    public void readBinaryUsedInElementConstructorThenReadAgain() throws Exception {
        final byte[] data = randomData(1024);
        final Path tmpFile = createTemporaryFile(data);

        final String query = """
                import module namespace file = "http://exist-db.org/xquery/file";
                let $b := file:read-binary('%s')
                let $w := <a>{$b}</a>
                return (count($w), $b)[2]""".formatted(tmpFile.toAbsolutePath());

        final QueryResultAccessor<T, E> resultsAccessor = executeXQuery(query);
        resultsAccessor.accept(results -> {
            assertEquals(1, size(results));
            final U item = item(results, 0);
            assertTrue(isBinaryType(item));
            assertArrayEquals(data, getBytes(item));
        });
    }

    /**
     * A file-backed binary (file:read-binary -&gt; BinaryValueFromFile) stored with xmldb:store and then
     * read again must remain readable. xmldb:store wraps its Resource in try-with-resources, and
     * LocalBinaryResource.doClose() close()s the binary value it was given; without XMLDBStore taking a
     * shared reference on the value it lends to the resource, that close() released the <em>caller's</em>
     * value and the second read failed with "Underlying channel has been closed".
     *
     * <p>Like {@link #readBinaryUsedInElementConstructorThenReadAgain()}, this must run as a root-context
     * main module (see that test's javadoc for the ModuleContext rationale).</p>
     *
     * @see <a href="https://github.com/eXist-db/exist/issues/6552">Regression when storing an uploaded binary</a>
     */
    @Test
    public void readBinaryStoreThenReadAgain() throws Exception {
        final byte[] data = randomData(1024);
        final Path tmpFile = createTemporaryFile(data);

        final String query = """
                import module namespace file = "http://exist-db.org/xquery/file";
                let $b := file:read-binary('%s')
                return (xmldb:store('%s', 'stored-file-backed.bin', $b, 'application/octet-stream'), $b)[2]""".formatted(tmpFile.toAbsolutePath(), TEST_COLLECTION);

        final QueryResultAccessor<T, E> resultsAccessor = executeXQuery(query);
        resultsAccessor.accept(results -> {
            assertEquals(1, size(results));
            final U item = item(results, 0);
            assertTrue(isBinaryType(item));
            assertArrayEquals(data, getBytes(item));
        });
    }

    /**
     * The same premature close as {@link #readBinaryStoreThenReadAgain()}, but for a database-backed
     * binary (util:binary-doc -&gt; BinaryValueFromInputStream): without the shared reference taken by
     * XMLDBStore, the second read failed with "The underlying InputStream has been closed".
     *
     * @see <a href="https://github.com/eXist-db/exist/issues/6552">Regression when storing an uploaded binary</a>
     */
    @Test
    public void binaryDocStoreThenReadAgain() throws Exception {
        final String query = """
                let $b := util:binary-doc('%s')
                return (xmldb:store('%s', 'stored-db-backed.bin', $b, 'application/octet-stream'), $b)[2]""".formatted(TEST_COLLECTION.append(BIN1_FILENAME), TEST_COLLECTION);

        final QueryResultAccessor<T, E> resultsAccessor = executeXQuery(query);
        resultsAccessor.accept(results -> {
            assertEquals(1, size(results));
            final U item = item(results, 0);
            assertTrue(isBinaryType(item));
            assertArrayEquals(BIN1_CONTENT, getBytes(item));
        });
    }

    /**
     * Storing the same binary value twice (e.g. a staging copy and a final copy) must work: without the
     * shared reference taken by XMLDBStore, the first xmldb:store closed the value and the second failed
     * in LocalBinaryResource.getStreamLength() with "error while obtaining length of binary value".
     *
     * @see <a href="https://github.com/eXist-db/exist/issues/6552">Regression when storing an uploaded binary</a>
     */
    @Test
    public void readBinaryStoredTwice() throws Exception {
        final byte[] data = randomData(1024);
        final Path tmpFile = createTemporaryFile(data);

        final String query = """
                import module namespace file = "http://exist-db.org/xquery/file";
                let $b := file:read-binary('%1$s')
                return (
                    xmldb:store('%2$s', 'stored-twice-1.bin', $b, 'application/octet-stream'),
                    xmldb:store('%2$s', 'stored-twice-2.bin', $b, 'application/octet-stream'),
                    $b
                )[3]""".formatted(tmpFile.toAbsolutePath(), TEST_COLLECTION);

        final QueryResultAccessor<T, E> resultsAccessor = executeXQuery(query);
        resultsAccessor.accept(results -> {
            assertEquals(1, size(results));
            final U item = item(results, 0);
            assertTrue(isBinaryType(item));
            assertArrayEquals(data, getBytes(item));
        });
    }

    /**
     * A binary value passed to a user-defined function must stay usable in the caller after that
     * function returns. When a function returns, its parameter variables go out of scope and
     * {@code XQueryContext.popLocalVariables} destroyed them - which closed the <em>caller's</em> value,
     * even though the callee never read it and the caller still holds it. The store then failed with
     * "error while obtaining length of binary value".
     *
     * <p>A value now belongs to the scope that created it, so the parameter alias in the callee owns
     * nothing and can release nothing.</p>
     *
     * @see <a href="https://github.com/eXist-db/exist/issues/6725">Passing a binary value to a user-defined function closes it for the caller</a>
     * @see <a href="https://github.com/eXist-db/exist/issues/5030">Error trying to access binary data from an HTTP response</a>
     */
    @Test
    public void passBinaryToUserFunctionThenStore() throws Exception {
        final byte[] data = randomData(1024);
        final Path tmpFile = createTemporaryFile(data);

        final String query = """
                import module namespace file = "http://exist-db.org/xquery/file";
                declare function local:noop($b) { 1 };
                let $b := file:read-binary('%1$s')
                return (local:noop($b), xmldb:store('%2$s', 'passed-to-function.bin', $b, 'application/octet-stream'), $b)[3]""".formatted(tmpFile.toAbsolutePath(), TEST_COLLECTION);

        final QueryResultAccessor<T, E> resultsAccessor = executeXQuery(query);
        resultsAccessor.accept(results -> {
            assertEquals(1, size(results));
            final U item = item(results, 0);
            assertTrue(isBinaryType(item));
            assertArrayEquals(data, getBytes(item));
        });
    }

    /**
     * The same value passed to a user-defined function twice: each return pops a scope, so a single
     * surviving call proves nothing if the second pop can still close the value.
     *
     * @see <a href="https://github.com/eXist-db/exist/issues/6725">Passing a binary value to a user-defined function closes it for the caller</a>
     */
    @Test
    public void passBinaryToUserFunctionTwice() throws Exception {
        final String query = """
                declare function local:size($b) { string-length(util:binary-to-string($b)) };
                let $b := util:binary-doc('%s')
                return (local:size($b), local:size($b), $b)[3]""".formatted(TEST_COLLECTION.append(BIN1_FILENAME));

        final QueryResultAccessor<T, E> resultsAccessor = executeXQuery(query);
        resultsAccessor.accept(results -> {
            assertEquals(1, size(results));
            final U item = item(results, 0);
            assertTrue(isBinaryType(item));
            assertArrayEquals(BIN1_CONTENT, getBytes(item));
        });
    }

    /**
     * A binary value handed down through two levels of user-defined function, and returned back up
     * through both: the value is created in the caller's scope, so neither callee may release it, and
     * returning it must not release it either.
     *
     * @see <a href="https://github.com/eXist-db/exist/issues/6725">Passing a binary value to a user-defined function closes it for the caller</a>
     */
    @Test
    public void passBinaryThroughTwoFunctionLevels() throws Exception {
        final byte[] data = randomData(1024);
        final Path tmpFile = createTemporaryFile(data);

        final String query = """
                import module namespace file = "http://exist-db.org/xquery/file";
                declare function local:inner($b) { $b };
                declare function local:outer($b) { local:inner($b) };
                let $b := file:read-binary('%s')
                return (local:outer($b), $b)[2]""".formatted(tmpFile.toAbsolutePath());

        final QueryResultAccessor<T, E> resultsAccessor = executeXQuery(query);
        resultsAccessor.accept(results -> {
            assertEquals(1, size(results));
            final U item = item(results, 0);
            assertTrue(isBinaryType(item));
            assertArrayEquals(data, getBytes(item));
        });
    }

    /**
     * A binary value created inside a user-defined function and returned to the caller escapes the
     * function's scope, so it must be handed to the caller rather than released with that scope.
     *
     * @see <a href="https://github.com/eXist-db/exist/issues/6725">Passing a binary value to a user-defined function closes it for the caller</a>
     */
    @Test
    public void binaryReturnedFromUserFunction() throws Exception {
        final byte[] data = randomData(1024);
        final Path tmpFile = createTemporaryFile(data);

        final String query = """
                import module namespace file = "http://exist-db.org/xquery/file";
                declare function local:load() { file:read-binary('%s') };
                let $b := local:load()
                return (count($b), $b)[2]""".formatted(tmpFile.toAbsolutePath());

        final QueryResultAccessor<T, E> resultsAccessor = executeXQuery(query);
        resultsAccessor.accept(results -> {
            assertEquals(1, size(results));
            final U item = item(results, 0);
            assertTrue(isBinaryType(item));
            assertArrayEquals(data, getBytes(item));
        });
    }

    /**
     * A binary value created in a user-defined function and captured by an inline function it returns
     * escapes through the closure, not through the returned sequence itself, and must survive the
     * defining function's scope.
     */
    @Test
    public void binaryCapturedInClosureUsedAfterFrame() throws Exception {
        final byte[] data = randomData(1024);
        final Path tmpFile = createTemporaryFile(data);

        final String query = """
                import module namespace file = "http://exist-db.org/xquery/file";
                declare function local:make($path) {
                    let $b := file:read-binary($path)
                    return function() { $b }
                };
                let $f := local:make('%s')
                return $f()""".formatted(tmpFile.toAbsolutePath());

        final QueryResultAccessor<T, E> resultsAccessor = executeXQuery(query);
        resultsAccessor.accept(results -> {
            assertEquals(1, size(results));
            final U item = item(results, 0);
            assertTrue(isBinaryType(item));
            assertArrayEquals(data, getBytes(item));
        });
    }

    /**
     * A binary value carried through a tail-recursive function, whose arguments eXist defers
     * (DeferredFunctionCall) rather than evaluating in the popped scope.
     */
    @Test
    public void binaryPassedThroughTailRecursion() throws Exception {
        final byte[] data = randomData(1024);
        final Path tmpFile = createTemporaryFile(data);

        final String query = """
                import module namespace file = "http://exist-db.org/xquery/file";
                declare function local:loop($n, $b) {
                    if ($n eq 0) then $b else local:loop($n - 1, $b)
                };
                let $b := file:read-binary('%s')
                return (local:loop(3, $b), $b)[2]""".formatted(tmpFile.toAbsolutePath());

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

    @FunctionalInterface
    interface QueryResultAccessor<T, E extends Exception> extends Consumer2E<Consumer2E<T, AssertionError, E>, AssertionError, E> {
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
