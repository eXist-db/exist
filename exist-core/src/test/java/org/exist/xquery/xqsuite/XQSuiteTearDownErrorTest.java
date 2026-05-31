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
package org.exist.xquery.xqsuite;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for issue #6422: XQSuite must surface errors thrown
 * from {@code %test:tearDown} functions in the suite output, not silently
 * swallow them.
 *
 * <p>Each test stores a small XQuery module to the database, calls
 * {@code test:suite(inspect:module-functions(...))} on the module, and
 * inspects the resulting {@code <testsuites>} element. A clean tearDown
 * must produce no tearDown marker; a throwing tearDown must produce a
 * {@code <system-err>} child on the {@code <testsuite>} naming the
 * error, and the {@code errors} count must be bumped by one.</p>
 */
public class XQSuiteTearDownErrorTest {

    private static final String COLLECTION = "/db/test-6422";

    @ClassRule
    public static final ExistXmldbEmbeddedServer embedded =
            new ExistXmldbEmbeddedServer(false, true, true);

    @BeforeClass
    public static void createCollection() throws XMLDBException {
        embedded.executeQuery("xmldb:create-collection('/db', 'test-6422')");
    }

    @AfterClass
    public static void cleanup() {
        try {
            embedded.executeQuery("xmldb:remove('" + COLLECTION + "')");
        } catch (final XMLDBException ignored) {
        }
    }

    @Test
    public void cleanTearDownProducesNoMarker() throws XMLDBException {
        final String module = """
                xquery version "3.1";
                module namespace t = "http://exist-db.org/xquery/test/6422-clean";
                declare namespace test = "http://exist-db.org/xquery/xqsuite";

                declare %test:tearDown function t:cleanup() { () };
                declare %test:assertEquals(1) function t:passes() { 1 };
                """;
        final String suiteXml = runSuiteAgainstStoredModule("clean.xqm",
                module, "http://exist-db.org/xquery/test/6422-clean");
        assertFalse("clean tearDown should not emit a system-err marker: " + suiteXml,
                suiteXml.contains("tearDown error"));
        assertTrue("testsuite errors=\"0\" expected for clean tearDown: " + suiteXml,
                suiteXml.contains("errors=\"0\""));
    }

    @Test
    public void throwingTearDownIsSurfaced() throws XMLDBException {
        final String module = """
                xquery version "3.1";
                module namespace t = "http://exist-db.org/xquery/test/6422-throw";
                declare namespace test = "http://exist-db.org/xquery/xqsuite";

                declare %test:tearDown function t:cleanup() {
                    error(xs:QName("t:teardown-boom"), "tearDown blew up here")
                };
                declare %test:assertEquals(1) function t:passes() { 1 };
                """;
        final String suiteXml = runSuiteAgainstStoredModule("throw.xqm",
                module, "http://exist-db.org/xquery/test/6422-throw");

        // The bug was that suiteXml had no mention of the tearDown failure
        // at all — neither a marker element nor a bumped errors count.
        assertTrue("throwing tearDown must produce a <system-err>tearDown error: ...</system-err> on the testsuite, got: "
                        + suiteXml,
                suiteXml.contains("tearDown error: ")
                        && suiteXml.contains("tearDown blew up here"));

        // Test itself passed → tests="1", failures="0". The tearDown failure
        // counts as one additional error.
        assertTrue("testsuite must count the tearDown failure as one error: " + suiteXml,
                suiteXml.contains("errors=\"1\""));
        assertTrue("the passing test should still be present: " + suiteXml,
                suiteXml.contains("tests=\"1\"") && suiteXml.contains("failures=\"0\""));
    }

    @Test
    public void throwingSetUpAndTearDownBothSurfaced() throws XMLDBException {
        // Both setUp and tearDown throw. The original behaviour reported
        // setUp's error and dropped tearDown's; now both must appear.
        final String module = """
                xquery version "3.1";
                module namespace t = "http://exist-db.org/xquery/test/6422-both";
                declare namespace test = "http://exist-db.org/xquery/xqsuite";

                declare %test:setUp function t:setup() {
                    error(xs:QName("t:setup-boom"), "setUp blew up")
                };
                declare %test:tearDown function t:cleanup() {
                    error(xs:QName("t:teardown-boom"), "tearDown also blew up")
                };
                declare %test:assertEquals(1) function t:wouldHavePassed() { 1 };
                """;
        final String suiteXml = runSuiteAgainstStoredModule("both.xqm",
                module, "http://exist-db.org/xquery/test/6422-both");
        assertTrue("setUp error must still be surfaced: " + suiteXml,
                suiteXml.contains("setUp blew up"));
        assertTrue("tearDown error must now be surfaced even when setUp also failed: "
                        + suiteXml,
                suiteXml.contains("tearDown error: ")
                        && suiteXml.contains("tearDown also blew up"));
    }

    /**
     * Store the given module text at {@code /db/test-6422/<filename>}, then
     * execute {@code test:suite(inspect:module-functions(...))} against its
     * URI. Returns the suite XML as a string.
     */
    private static String runSuiteAgainstStoredModule(final String filename,
                                                      final String moduleText,
                                                      final String moduleNs) throws XMLDBException {
        final String storeQuery = "xmldb:store('" + COLLECTION + "', '" + filename + "', "
                + "$moduleText, 'application/xquery')";
        // xmldb:store takes a node or string; pass moduleText through a let.
        embedded.executeQuery(
                "let $moduleText := \"" + escapeXQueryStringLiteral(moduleText) + "\" "
                        + "return " + storeQuery);

        final String suiteQuery = ""
                + "import module namespace test='http://exist-db.org/xquery/xqsuite';\n"
                + "import module namespace t='" + moduleNs + "' "
                + "    at 'xmldb:exist:///" + COLLECTION + "/" + filename + "';\n"
                + "serialize(\n"
                + "    test:suite(inspect:module-functions(xs:anyURI('xmldb:exist:///"
                + COLLECTION + "/" + filename + "'))),\n"
                + "    map { 'method': 'xml' }\n"
                + ")";
        final ResourceSet rs = embedded.executeQuery(suiteQuery);
        assertEquals(1, rs.getSize());
        return (String) rs.getResource(0).getContent();
    }

    /** Escape a string for inclusion in an XQuery double-quoted string literal. */
    private static String escapeXQueryStringLiteral(final String s) {
        return s.replace("\\", "\\\\").replace("\"", "\"\"");
    }
}
