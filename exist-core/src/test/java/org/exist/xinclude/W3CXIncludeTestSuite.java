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
package org.exist.xinclude;

import org.exist.collections.Collection;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Runs the W3C XInclude 1.0 Test Suite against eXist-db's XInclude implementation.
 *
 * Each test case stores the contributor's entire directory tree in eXist
 * (preserving relative path structure for ../ents/ references), then
 * serializes the input document with XInclude expansion and compares
 * the output to the expected result.
 */
@RunWith(Parameterized.class)
public class W3CXIncludeTestSuite {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String TEST_SUITE_DIR = "xinclude-test-suite";
    private static final XmldbURI TEST_COLLECTION = XmldbURI.create("/db/xinclude-test");

    // Track which contributor directories have been stored (avoid redundant uploads)
    private static final Set<String> storedContributors = new HashSet<>();

    private final String testId;
    private final String basedir;
    private final String href;
    private final String type;
    private final String outputPath;
    private final String description;
    private final String features;

    public W3CXIncludeTestSuite(String testId, String basedir, String href, String type,
                                 String outputPath, String description, String features) {
        this.testId = testId;
        this.basedir = basedir;
        this.href = href;
        this.type = type;
        this.outputPath = outputPath;
        this.description = description;
        this.features = features;
    }

    @Parameterized.Parameters(name = "{0}: {5}")
    public static java.util.Collection<Object[]> data() throws Exception {
        final List<Object[]> tests = new ArrayList<>();
        final Path catalogPath = getTestSuitePath().resolve("testdescr.xml");

        final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        final DocumentBuilder db = dbf.newDocumentBuilder();
        final Document doc = db.parse(catalogPath.toFile());

        final NodeList testcasesNodes = doc.getElementsByTagName("testcases");
        for (int i = 0; i < testcasesNodes.getLength(); i++) {
            final Element testcasesEl = (Element) testcasesNodes.item(i);
            final String basedir = testcasesEl.getAttribute("basedir");

            final NodeList testcaseNodes = testcasesEl.getElementsByTagName("testcase");
            for (int j = 0; j < testcaseNodes.getLength(); j++) {
                final Element tc = (Element) testcaseNodes.item(j);
                final String id = tc.getAttribute("id");
                final String tcHref = tc.getAttribute("href");
                final String tcType = tc.getAttribute("type");
                final String tcFeatures = tc.getAttribute("features");

                String output = null;
                final NodeList outputNodes = tc.getElementsByTagName("output");
                if (outputNodes.getLength() > 0) {
                    output = outputNodes.item(0).getTextContent().trim();
                }

                String desc = "";
                final NodeList descNodes = tc.getElementsByTagName("description");
                if (descNodes.getLength() > 0) {
                    desc = descNodes.item(0).getTextContent().trim();
                }

                tests.add(new Object[]{id, basedir, tcHref, tcType, output, desc, tcFeatures});
            }
        }

        return tests;
    }

    @Test
    public void runTestCase() throws Exception {
        // Skip tests requiring features eXist doesn't support
        if (features != null && !features.isEmpty()) {
            Assume.assumeFalse("Skipping: requires xpointer-scheme", features.contains("xpointer-scheme"));
            Assume.assumeFalse("Skipping: requires unexpanded-entities", features.contains("unexpanded-entities"));
            Assume.assumeFalse("Skipping: requires unparsed-entities", features.contains("unparsed-entities"));
        }

        // Skip tests that reference external HTTP URLs (network-dependent)
        final Path testSuitePath = getTestSuitePath();
        final Path inputFile = testSuitePath.resolve(basedir).resolve(href);
        if (Files.exists(inputFile)) {
            final String content = new String(Files.readAllBytes(inputFile), StandardCharsets.UTF_8);
            if (content.contains("href=\"http://") || content.contains("href='http://")) {
                Assume.assumeTrue("Skipping: references external HTTP URL", false);
            }
        }

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();

        // Store the contributor's entire directory tree (preserving relative paths)
        // e.g., for basedir "Imaq/test/XInclude/docs", store all of "Imaq/"
        final String contributorDir = basedir.contains("/") ? basedir.substring(0, basedir.indexOf('/')) : basedir;
        ensureContributorStored(pool, testSuitePath, contributorDir);

        // The input document path in eXist mirrors the filesystem structure
        final String inputDocPath = TEST_COLLECTION + "/" + basedir + "/" + href;

        // Serialize with XInclude expansion
        // Use fn:serialize() to get proper XML output, not just text content
        final String xquery = String.format(
                "let $doc := doc('%s')\n" +
                "let $expanded := util:expand($doc, 'expand-xincludes=yes')\n" +
                "return fn:serialize($expanded, map { 'method': 'xml', 'indent': false(), 'omit-xml-declaration': true() })",
                inputDocPath);

        if ("error".equals(type)) {
            try {
                final String result = executeXQuery(pool, xquery);
                fail("Expected XInclude error for test " + testId + " (" + description + ") but got result: " +
                        (result.length() > 200 ? result.substring(0, 200) + "..." : result));
            } catch (final XPathException | StackOverflowError | OutOfMemoryError e) {
                // Expected — XInclude error (StackOverflow for infinite recursion tests)
            }
        } else {
            assertNotNull("Success test must have expected output: " + testId, outputPath);

            final String result = executeXQuery(pool, xquery);
            final Path expectedPath = testSuitePath.resolve(basedir).resolve(outputPath);
            assertTrue("Expected output file not found: " + expectedPath, Files.exists(expectedPath));

            final String expected = new String(Files.readAllBytes(expectedPath), StandardCharsets.UTF_8).trim();

            final String normalizedExpected = normalizeXml(expected);
            final String normalizedResult = normalizeXml(result);

            assertEquals("Test " + testId + ": " + description, normalizedExpected, normalizedResult);
        }
    }

    private void ensureContributorStored(final BrokerPool pool, final Path testSuitePath,
                                          final String contributorDir) throws Exception {
        if (storedContributors.contains(contributorDir)) {
            return;
        }

        final Path dirPath = testSuitePath.resolve(contributorDir);
        if (!Files.exists(dirPath)) {
            return;
        }

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            storeDirectoryTree(broker, transaction, testSuitePath, dirPath);
            transaction.commit();
        }

        storedContributors.add(contributorDir);
    }

    private void storeDirectoryTree(final DBBroker broker, final Txn transaction,
                                     final Path testSuiteRoot, final Path dir) throws Exception {
        try (final var stream = Files.walk(dir)) {
            for (final Path file : stream.filter(Files::isRegularFile).collect(Collectors.toList())) {
                final String fileName = file.getFileName().toString();

                // Skip CVS directories and non-test files
                if (file.toString().contains("/CVS/")) continue;
                if (fileName.endsWith(".dtd")) continue;

                // Build collection path mirroring filesystem structure
                final String relativeDir = testSuiteRoot.relativize(file.getParent()).toString();
                final XmldbURI collectionUri = TEST_COLLECTION.append(relativeDir);

                final Collection collection = broker.getOrCreateCollection(transaction, collectionUri);
                broker.saveCollection(transaction, collection);

                // Determine MIME type
                final MimeType mimeType = MimeTable.getInstance().getContentTypeFor(fileName);
                // Force .ent and .ent-like files to be stored as XML (they contain XML fragments)
                final boolean forceXml = fileName.endsWith(".ent");
                try {
                    if (forceXml || (mimeType != null && mimeType.isXMLType())) {
                        final String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                        final MimeType xmlMime = forceXml ? MimeType.XML_TYPE :  mimeType;
                        broker.storeDocument(transaction, XmldbURI.create(fileName),
                                new StringInputSource(content), xmlMime, collection);
                    } else {
                        final byte[] bytes = Files.readAllBytes(file);
                        broker.storeDocument(transaction, XmldbURI.create(fileName),
                                new StringInputSource(bytes),
                                mimeType != null ? mimeType : MimeType.BINARY_TYPE, collection);
                    }
                } catch (final Exception e) {
                    // Some test files may be intentionally malformed XML — store as binary
                    if (mimeType != null && mimeType.isXMLType()) {
                        try {
                            final byte[] bytes = Files.readAllBytes(file);
                            broker.storeDocument(transaction, XmldbURI.create(fileName),
                                    new StringInputSource(bytes), MimeType.BINARY_TYPE, collection);
                        } catch (final Exception e2) {
                            // Skip files that can't be stored at all
                        }
                    }
                }
            }
        }
    }

    private String executeXQuery(final BrokerPool pool, final String xquery) throws Exception {
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xqueryService = pool.getXQueryService();
            final Sequence result = xqueryService.execute(broker, xquery, null);
            return result.getStringValue();
        }
    }

    private static String normalizeXml(final String xml) {
        // Strip XML declaration
        String result = xml.replaceAll("<\\?xml[^?]*\\?>", "").trim();
        // Normalize line endings
        result = result.replace("\r\n", "\n").replace("\r", "\n");
        // Normalize whitespace between tags (but preserve significant whitespace)
        result = result.replaceAll(">\\s+<", "><");
        return result.trim();
    }

    private static Path getTestSuitePath() {
        final URL url = W3CXIncludeTestSuite.class.getClassLoader().getResource(TEST_SUITE_DIR);
        if (url != null) {
            try {
                return Paths.get(url.toURI());
            } catch (final Exception e) {
                // fall through
            }
        }
        return Paths.get("src/test/resources", TEST_SUITE_DIR);
    }
}
