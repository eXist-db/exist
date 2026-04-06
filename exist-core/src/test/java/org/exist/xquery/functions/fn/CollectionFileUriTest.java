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
package org.exist.xquery.functions.fn;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for fn:collection() with file: URIs and Saxon-style query string parameters.
 * <p>
 * Creates a temp directory with a mix of files (XML, non-XML, malformed) and verifies
 * the {@code select}, {@code match}, {@code content-type}, and {@code stable} parameters.
 */
public class CollectionFileUriTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static Path tempDir;

    @BeforeClass
    public static void setUp() throws IOException {
        tempDir = Files.createTempDirectory("exist-collection-file-uri-test-");

        // 5 well-formed XML files
        Files.writeString(tempDir.resolve("doc1.xml"), "<a>1</a>");
        Files.writeString(tempDir.resolve("doc2.xml"), "<a>2</a>");
        Files.writeString(tempDir.resolve("doc3.xml"), "<a>3</a>");
        Files.writeString(tempDir.resolve("alpha.xml"), "<a>alpha</a>");
        Files.writeString(tempDir.resolve("beta.xml"), "<a>beta</a>");

        // Non-XML files (should be excluded by default *.xml glob)
        Files.writeString(tempDir.resolve("readme.txt"), "not xml");
        Files.writeString(tempDir.resolve("data.json"), "{\"k\":1}");
    }

    @AfterClass
    public static void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            try (final Stream<Path> walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (final IOException ignored) {
                    }
                });
            }
        }
    }

    private Sequence runQuery(final String xquery) throws EXistException, PermissionDeniedException, XPathException, IOException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xqueryService = pool.getXQueryService();
            final XQueryContext context = new XQueryContext(pool);
            final CompiledXQuery compiled = xqueryService.compile(context, xquery);
            return xqueryService.execute(broker, compiled, null);
        }
    }

    private String fileUri() {
        return tempDir.toUri().toString();
    }

    @Test
    public void defaultGlobReturnsAllXml() throws Exception {
        // No params: default *.xml glob, returns all 5 XML files
        final Sequence result = runQuery("count(fn:collection('" + fileUri() + "'))");
        assertEquals("default glob should match all 5 XML files", "5", result.getStringValue());
    }

    @Test
    public void selectGlob() throws Exception {
        // ?select=doc*.xml — only doc1, doc2, doc3
        final Sequence result = runQuery("count(fn:collection('" + fileUri() + "?select=doc*.xml'))");
        assertEquals("select=doc*.xml should match 3 files", "3", result.getStringValue());
    }

    @Test
    public void matchRegex() throws Exception {
        // ?match=^doc[0-9]+\.xml$ — exactly the 3 doc files
        final Sequence result = runQuery(
                "count(fn:collection('" + fileUri() + "?match=^doc[0-9]+\\.xml$'))");
        assertEquals("match regex should select 3 doc files", "3", result.getStringValue());
    }

    @Test
    public void selectAndMatchCombined() throws Exception {
        // ?select=*.xml&match=^doc — only doc1/2/3 (excludes alpha, beta)
        // Build URI with concat() to avoid the literal & in XQuery string
        final Sequence result = runQuery(
                "count(fn:collection(concat('" + fileUri() + "?select=*.xml', codepoints-to-string(38), 'match=^doc')))");
        assertEquals("select + match combined", "3", result.getStringValue());
    }

    @Test
    public void stableYesGivesAlphabeticalOrder() throws Exception {
        // ?stable=yes — files sorted alphabetically: alpha, beta, doc1, doc2, doc3
        final Sequence result = runQuery(
                "string-join(\n" +
                "  for $d in fn:collection('" + fileUri() + "?stable=yes')\n" +
                "  return tokenize(document-uri($d), '/')[last()],\n" +
                "  ',')");
        assertEquals("stable=yes should sort alphabetically",
                "alpha.xml,beta.xml,doc1.xml,doc2.xml,doc3.xml", result.getStringValue());
    }

    @Test
    public void stableIsDefaultYes() throws Exception {
        // No stable= param: default is yes (alphabetical)
        final Sequence result = runQuery(
                "string-join(\n" +
                "  for $d in fn:collection('" + fileUri() + "')\n" +
                "  return tokenize(document-uri($d), '/')[last()],\n" +
                "  ',')");
        assertEquals("default ordering should be alphabetical",
                "alpha.xml,beta.xml,doc1.xml,doc2.xml,doc3.xml", result.getStringValue());
    }

    @Test
    public void contentTypeXml() throws Exception {
        // content-type=application/vnd.existdb.document+xml — XML documents only (default for fn:collection)
        final Sequence result = runQuery(
                "count(fn:collection('" + fileUri() + "?content-type=application/vnd.existdb.document+xml'))");
        assertEquals("xml content-type should match all 5 XML files", "5", result.getStringValue());
    }

    @Test
    public void contentTypeBinaryReturnsEmpty() throws Exception {
        // fn:collection() doesn't return binary docs — content-type=binary returns nothing
        final Sequence result = runQuery(
                "count(fn:collection('" + fileUri() + "?content-type=application/vnd.existdb.document+binary'))");
        assertEquals("binary content-type should return 0 documents", "0", result.getStringValue());
    }

    @Test
    public void allParametersCombined() throws Exception {
        // All four parameters together: select=doc*.xml & match=[12] & content-type=xml & stable=yes
        // Build the URI via concat() to avoid the literal & in XQuery string
        final String amp = "', codepoints-to-string(38), '";
        final Sequence result = runQuery(
                "string-join(\n" +
                "  for $d in fn:collection(concat('" + fileUri() + "?select=doc*.xml" + amp +
                "match=doc[12]" + amp +
                "content-type=application/vnd.existdb.document+xml" + amp +
                "stable=yes'))\n" +
                "  return tokenize(document-uri($d), '/')[last()],\n" +
                "  ',')");
        assertEquals("all params combined should give doc1, doc2 in order",
                "doc1.xml,doc2.xml", result.getStringValue());
    }

    @Test
    public void invalidQueryParamRaisesError() throws Exception {
        // Unknown parameter should raise FODC0004
        try {
            runQuery("fn:collection('" + fileUri() + "?bogus=foo')");
            fail("expected FODC0004 for unknown query parameter");
        } catch (final XPathException e) {
            assertTrue("error should be FODC0004 but was " + e.getErrorCode(),
                    e.getErrorCode().getErrorQName().getLocalPart().equals("FODC0004"));
        }
    }

    @Test
    public void invalidStableValueRaisesError() throws Exception {
        // stable=maybe is invalid
        try {
            runQuery("fn:collection('" + fileUri() + "?stable=maybe')");
            fail("expected FODC0004 for invalid stable value");
        } catch (final XPathException e) {
            assertTrue("error should be FODC0004 but was " + e.getErrorCode(),
                    e.getErrorCode().getErrorQName().getLocalPart().equals("FODC0004"));
        }
    }

    @Test
    public void invalidContentTypeRaisesError() throws Exception {
        try {
            runQuery("fn:collection('" + fileUri() + "?content-type=text/plain')");
            fail("expected FODC0004 for invalid content-type value");
        } catch (final XPathException e) {
            assertTrue("error should be FODC0004 but was " + e.getErrorCode(),
                    e.getErrorCode().getErrorQName().getLocalPart().equals("FODC0004"));
        }
    }
}
