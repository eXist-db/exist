/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package xquery;

import org.exist.Namespaces;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.source.FileSource;
import org.exist.source.Source;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.FileUtils;
import org.exist.util.XMLFilenameFilter;
import org.exist.xmldb.EXistXQueryService;
import org.exist.xquery.value.Sequence;
import org.junit.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.*;

import static org.junit.Assert.assertArrayEquals;

public abstract class TestRunner {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true);

    protected abstract String getDirectory();

    @Test
    public void runXMLBasedTests() throws TransformerException, XMLDBException, ParserConfigurationException, SAXException, IOException {
        final Path dir = Paths.get(getDirectory());

        final List<Path> files;
        if(Files.isDirectory(dir)) {
            files = FileUtils.list(dir, XMLFilenameFilter.asPredicate());
        } else if(new XMLFilenameFilter().accept(dir.getParent().toFile(), FileUtils.fileName(dir))) {
            files = Arrays.asList(dir);
        } else {
            return;
        }

        final List<TestSuite> all = new ArrayList<>();
        final EXistXQueryService xqs = (EXistXQueryService) existEmbeddedServer.getRoot().getService("XQueryService", "1.0");
        final Source query = new FileSource(Paths.get("test/src/xquery/runTests.xql"), false);

        if(files != null) {
            for (final Path file : files) {
                try {
                    final Document doc = parse(file);

                    xqs.declareVariable("doc", doc);
                    xqs.declareVariable("id", Sequence.EMPTY_SEQUENCE);
                    final ResourceSet result = xqs.execute(query);
                    final XMLResource resource = (XMLResource) result.getResource(0);

                    final List<TestSuite> tsResults = parseXmlResults((Element) resource.getContentAsDOM());
                    all.addAll(tsResults);
                    tsResults.forEach(this::printResults);
                } catch (final Throwable t) {
                    System.err.println(t.getClass().getSimpleName() + " while running: " + file);
                    throw t;
                }
            }
        }

        assertSuccess(all);
    }

    @Test
    public void runXQueryBasedTests() throws XMLDBException, IOException {
        final Path dir = Paths.get(getDirectory());
        final List<Path> suites = FileUtils.list(dir, path -> {
            final String name = FileUtils.fileName(path);
            return (!Files.isDirectory(path)) && Files.isReadable(path) && name.startsWith("suite") && name.endsWith(".xql");
        });

        final List<TestSuite> all = new ArrayList<>();

        if(suites != null) {
            for (final Path suite : suites) {
                final EXistXQueryService xqs = (EXistXQueryService) existEmbeddedServer.getRoot().getService("XQueryService", "1.0");
                xqs.setModuleLoadPath(getDirectory());
                final Source query = new FileSource(suite, false);

                final ResourceSet result = xqs.execute(query);
                final XMLResource resource = (XMLResource) result.getResource(0);

                final List<TestSuite> tsResults = parseXQueryResults((Element) resource.getContentAsDOM());
                all.addAll(tsResults);
                tsResults.forEach(this::printResults);
            }
        }

        assertSuccess(all);
    }

    /**
     * Uses JUnits assertArrayEquals to report test failures and errors
     */
    private void assertSuccess(final List<TestSuite> tss) {
        final List<String> expected = new ArrayList<>();
        final List<String> actual = new ArrayList<>();

        tss.forEach(ts ->
                ts.getTestCases().forEach(tc -> {
                    if (tc instanceof TestCaseFailed) {
                        expected.add(((TestCaseFailed) tc).expected.orElse("{UNKNOWN EXPECTED: " + tc.name + "}"));
                        actual.add(((TestCaseFailed) tc).actual.orElse("{UNKNOWN ACTUAL: " + tc.name + "}"));
                    } else if(tc instanceof TestCaseError) {
                        expected.add("{UNKNOWN EXPECTED: " + tc.name + "}");
                        actual.add("{ERROR: " + ((TestCaseError)tc).reason + "}");
                    }
                })
        );

        assertArrayEquals(expected.toArray(), actual.toArray());
    }

    /**
     * Prints the results of a test suite to the console
     */
    private void printResults(final TestSuite ts) {
        System.out.println("XQuery Test suite: " + ts.getName());
        ts.getTestCases().forEach(testCase -> {
            System.out.println('\t' + testCase.toString());
        });
    }

    /**
     * Parses the output of eXist's XML based XQuery test suite
     *
     * @param testset The XML element <testset> from the test suite output
     * @return The results of the tests
     */
    private List<TestSuite> parseXmlResults(final Element testset) {
        final List<TestSuite> results = new ArrayList<>();

        final TestSuite ts = new TestSuite(getFirstChildElement(testset, "testName").map(this::getText).orElse("{UNKNOWN}"));
        final NodeList nlTests = testset.getElementsByTagName("test");
        for(int i = 0; i < nlTests.getLength(); i++) {
            final Element test = (Element)nlTests.item(i);
            final String name = test.getAttribute("n") + Optional.ofNullable(test.getAttribute("id")).map(id -> " (" + id + ")").orElse("");
            final boolean pass = Boolean.parseBoolean(test.getAttribute("pass"));

            final TestCase tc;
            if(pass) {
                tc = new TestCasePassed(name);
            } else {
                tc = getFirstChildElement(test, "result").map(result -> getFirstChildElement(result, "error").map(this::getText).<TestCase>map(err -> new TestCaseError(name, err))
                    .orElse(
                        new TestCaseFailed(name, getFirstChildElement(test, "task").map(this::getText).orElse("{UNKNOWN}"), getFirstChildElement(test, "expected").map(this::getText), Optional.of(getText(result)))
                    )
                ).orElse(null);
            }
            ts.add(tc);
        }
        results.add(ts);

        return results;
    }

    /**
     * Parses the output of eXist's XQuery based XQuery test suite
     *
     * @param testsuites The XML element <testsuites> from the test suite output
     * @return The results of the tests
     */
    private List<TestSuite> parseXQueryResults(final Element testsuites) {

        final List<TestSuite> results = new ArrayList<>();

        final NodeList nlTestSuite = testsuites.getElementsByTagName("testsuite");
        for(int i = 0; i < nlTestSuite.getLength(); i++) {
            final Element testsuite = (Element)nlTestSuite.item(i);

            final TestSuite ts = new TestSuite(testsuite.getAttribute("package"));
            final NodeList nlTestCase = testsuite.getElementsByTagName("testcase");

            for(int j = 0; j < nlTestCase.getLength(); j++) {
                final Element testcase = (Element)nlTestCase.item(j);
                final Optional<Element> maybeFailure = getFirstChildElement(testcase, "failure");
                final Optional<Element> maybeError = getFirstChildElement(testcase, "error");

                final String name = testcase.getAttribute("name");
                final TestCase tc = maybeFailure.<TestCase>map(failure -> {
                    final Optional<Element> output = getFirstChildElement(testcase, "output");
                    return new TestCaseFailed(name, failure.getAttribute("message"), Optional.of(getText(failure)), output.map(this::getText));
                }).orElse(
                        maybeError.<TestCase>map(error ->
                                new TestCaseError(name, error.getAttribute("message"))
                        ).orElse(
                                new TestCasePassed(name)
                        )
                );

                ts.add(tc);
            }
            results.add(ts);
        }

        return results;
    }

    /**
     * Extracts all child text node values from an element
     * (non-recursive)
     */
    private final String getText(final Element elem) {
        final StringBuilder builder = new StringBuilder();
        final NodeList nlChildren = elem.getChildNodes();
        for(int i = 0; i < nlChildren.getLength(); i++) {
            final Node n = nlChildren.item(i);
            if(n.getNodeType() == Node.TEXT_NODE) {
                builder.append(n.getNodeValue());
            }
        }
        return builder.toString();
    }

    /**
     * Gets the first named child element from a parent element that matches
     */
    private Optional<Element> getFirstChildElement(final Element parent, final String name) {
        return Optional.of(parent.getElementsByTagName(name)).map(nl -> (Element)nl.item(0));
    }

    private class TestSuite {
        private final String name;
        private final List<TestCase> testCases = new ArrayList<>();

        private TestSuite(final String name) {
            this.name = name;
        }

        public final String getName() {
            return name;
        }

        public void add(final TestCase testCase) {
            testCases.add(testCase);
        }

        public List<TestCase> getTestCases() {
            return testCases;
        }
    }

    private abstract class TestCase {
        protected final String name;

        private TestCase(final String name) {
            this.name = name;
        }

        @Override
        public abstract String toString();
    }

    private class TestCasePassed  extends TestCase {
        private TestCasePassed(final String name) {
            super(name);
        }

        @Override
        public String toString() {
            return "PASSED: " + name;
        }
    }

    private class TestCaseError extends TestCase {
        final String reason;

        private TestCaseError(final String name, final String reason) {
            super(name);
            this.reason = reason;
        }

        @Override
        public String toString() {
            return "ERROR: " + name + ". " + reason + ".";
        }
    }

    private class TestCaseFailed extends TestCase {
        private final String reason;
        private final Optional<String> expected;
        private final Optional<String> actual;

        private TestCaseFailed(final String name, final String reason, final Optional<String> expected, final Optional<String> actual) {
            super(name);
            this.reason = reason;
            this.expected = expected;
            this.actual = actual;
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder()
                .append("FAILED: ")
                .append(name)
                .append(". ")
                .append(reason)
                .append(".");

            expected.map(e -> builder
                    .append(" Expected: '")
                    .append(e).append("'"));

            actual.map(a -> builder
                    .append(" Actual: '")
                    .append(a).append("'"));

            return builder.toString();
        }
    }

    protected static Document parse(final Path file) throws IOException, SAXException, ParserConfigurationException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        InputSource src = new InputSource(file.toUri().toASCIIString());
        SAXParser parser = factory.newSAXParser();
        XMLReader xr = parser.getXMLReader();

        SAXAdapter adapter = new SAXAdapter();
        xr.setContentHandler(adapter);
        xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
        xr.parse(src);

        return adapter.getDocument();
    }
}
