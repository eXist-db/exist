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

package org.exist.xquery.functions.inspect;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.memtree.ElementImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.junit.*;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

public class InspectModuleTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final XmldbURI TEST_COLLECTION = XmldbURI.ROOT_COLLECTION_URI.append("test-inspectModule");
    private static final XmldbURI TEST_MODULE = XmldbURI.create("test.xqm");
    private static final XmldbURI MODULE_LOAD_PATH = TEST_COLLECTION.append(TEST_MODULE).toCollectionPathURI();
    private static final String INSPECT_MODULE = """
            xquery version "3.1";
            module namespace x = "http://xyz.com";

            (:~
             : Some description.
             : @return taxonomy[@type = "reign"]
             :)
            declare function x:fun1() as xs:string {
              "hello from fun1"
            };
            
            (:~
             : Some other description.
             :
             : @param one first parameter
             : @param two second parameter
             :
             : @return our result
             :)
            declare function x:fun2($one as xs:int, $two as xs:float) as xs:string {
              "hello from fun2"
            };
            
            (:~
             : This is a multiline description and therefore
             : spans multiple
             : lines.
             :
             : @return another result
             :)
            declare function x:fun3() {
              "hello from fun3"
            };
            
            (:~
             : An annotated function.
             :
             : @return another result
             :)
            declare %public %x:path("/x/y/z") function x:fun4() {
              "hello from fun4"
            };

            (:~
             : Selects taxonomy[@type = "reign"] from the source.
             : THIS SENTENCE MUST SURVIVE.
             :)
            declare function x:fun5() {
              "hello from fun5"
            };

            (:~
             : Costs 5 @ 3 dollars each. Write to info@exist-db.org for a quote@
             :)
            declare function x:fun6() {
              "hello from fun6"
            };

            (:~
             : A description before the tags.
             :
             : @param $one takes x/@attr as its value
             : @return a result mentioning info@exist-db.org
             :)
            declare function x:fun7($one as xs:int) {
              "hello from fun7"
            };

            (:~
             : A description whose later lines open with an '@'.
             : @home is where the heart is.
             : @2024 was a good year.
             : @exist-db.org is the domain.
             :)
            declare function x:fun8() {
              "hello from fun8"
            };

            (:~
             : Tags xqDoc defines still open a tag.
             : @since 1.0
             : @author Some One
             :)
            declare function x:fun9() {
              "hello from fun9"
            };
            """;
    private static final String MAIN_MODULE = """
            import module namespace inspect = "http://exist-db.org/xquery/inspection";
            
            inspect:inspect-module(xs:anyURI("xmldb:exist://%s"))/function[@name eq "%s"]
            """;

    @Test
    public void withAtSignInline() throws PermissionDeniedException, XPathException, EXistException {
        final String functionName = "x:fun1";
        final String expectedDescription = "Some description.";
        final String[] expectedAnnotations = {};
        final String[] expectedAnnotationValues = {};
        final String[] expectedParameters = {};
        final String expectedReturn = "taxonomy[@type = \"reign\"]";

        assertInspection(functionName, expectedDescription, expectedParameters, expectedAnnotations, expectedAnnotationValues, expectedReturn);
    }

    /** eXist-db/exist#1386: an '@' mid-prose must not truncate the description. */
    @Test
    public void atSignInDescriptionDoesNotTruncate() throws PermissionDeniedException, XPathException, EXistException {
        assertDescription("x:fun5",
                "Selects taxonomy[@type = \"reign\"] from the source.\n THIS SENTENCE MUST SURVIVE.");
    }

    /** eXist-db/exist#1386: a bare '@', an email address, and a trailing '@' are all prose. */
    @Test
    public void bareAtSignAndEmailInDescriptionSurvive() throws PermissionDeniedException, XPathException, EXistException {
        assertDescription("x:fun6",
                "Costs 5 @ 3 dollars each. Write to info@exist-db.org for a quote@");
    }

    /**
     * eXist-db/exist#1386, raised in review: a line opening with an unrecognized '@word' is prose,
     * not a tag. Previously such a line vanished from the description and reappeared as an element
     * named after the word — including names XML does not permit, such as "2024".
     */
    @Test
    public void lineStartAtSignThatIsNotAnXQDocTagIsProse() throws PermissionDeniedException, XPathException, EXistException {
        assertDescription("x:fun8",
                "A description whose later lines open with an '@'.\n @home is where the heart is."
                        + "\n @2024 was a good year.\n @exist-db.org is the domain.");
    }

    /** ...while the tags xqDoc does define still open a tag at line start. */
    @Test
    public void lineStartXQDocTagsStillParseAsTags() throws PermissionDeniedException, XPathException, EXistException {
        assertDescription("x:fun9", "Tags xqDoc defines still open a tag.");
    }

    /** eXist-db/exist#1386: tags still parse, and an '@' inside a tag's value survives too. */
    @Test
    public void tagsStillParseWithAtSignsInTheirValues() throws PermissionDeniedException, XPathException, EXistException {
        assertInspection("x:fun7", "A description before the tags.",
                new String[]{ "takes x/@attr as its value" }, new String[]{}, new String[]{},
                "a result mentioning info@exist-db.org");
    }

    @Test
    public void withParamsAndReturn() throws PermissionDeniedException, XPathException, EXistException {
        final String functionName = "x:fun2";
        final String expectedDescription = "Some other description.";
        final String[] expectedAnnotations = {};
        final String[] expectedAnnotationValues = {};
        final String[] expectedParameters = { "first parameter", "second parameter" };
        final String expectedReturn = "our result";

        assertInspection(functionName, expectedDescription, expectedParameters, expectedAnnotations, expectedAnnotationValues, expectedReturn);
    }

    @Test
    public void multilineDescription() throws PermissionDeniedException, XPathException, EXistException {
        final String functionName = "x:fun3";
        final String expectedDescription = "This is a multiline description and therefore\n spans multiple\n lines.";
        final String[] expectedAnnotations = {};
        final String[] expectedAnnotationValues = {};
        final String[] expectedParameters = {};
        final String expectedReturn = "another result";

        assertInspection(functionName, expectedDescription, expectedParameters, expectedAnnotations, expectedAnnotationValues, expectedReturn);
    }

    @Test
    public void onAnnotatedFunction() throws PermissionDeniedException, XPathException, EXistException {
        final String functionName = "x:fun4";
        final String expectedDescription = "An annotated function.";
        final String[] expectedAnnotations = { "public", "x:path" };
        final String[] expectedAnnotationValues = { null, "/x/y/z" };
        final String[] expectedParameters = {};
        final String expectedReturn = "another result";

        assertInspection(functionName, expectedDescription, expectedParameters, expectedAnnotations, expectedAnnotationValues, expectedReturn);
    }

    /**
     * Asserts only the description of a function, for cases that declare no tags.
     *
     * @param functionName the function to inspect
     * @param expectedDescription the description text expected to survive parsing
     */
    private static void assertDescription(final String functionName, final String expectedDescription)
            throws XPathException, PermissionDeniedException, EXistException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xqueryService = pool.getXQueryService();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            final Sequence result = xqueryService.execute(broker,
                    MAIN_MODULE.formatted(MODULE_LOAD_PATH, functionName), null);

            assertEquals(1, result.getItemCount());
            final Element function = (Element) result.itemAt(0);
            final NodeList descriptions = function.getElementsByTagName("description");
            assertEquals(1, descriptions.getLength());
            assertEquals(expectedDescription, descriptions.item(0).getFirstChild().getNodeValue());

            transaction.commit();
        }
    }

    private static void assertInspection(
            String functionName,
            String expectedDescription,
            String[] expectedParameters,
            String[] expectedAnnotations,
            String[] expectedAnnotationValues,
            String expectedReturn
    ) throws XPathException, PermissionDeniedException, EXistException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xqueryService = pool.getXQueryService();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            final String query = MAIN_MODULE.formatted(MODULE_LOAD_PATH, functionName);

            final Sequence result = xqueryService.execute(broker, query, null);

            assertNotNull(result);
            assertEquals(1, result.getItemCount());
            final Item item1 = result.itemAt(0);
            assertTrue(item1 instanceof ElementImpl);

            final Element function = (Element)item1;

            final NodeList descriptions = function.getElementsByTagName("description");
            assertEquals(1, descriptions.getLength());
            assertEquals(expectedDescription, descriptions.item(0).getFirstChild().getNodeValue());

            final NodeList arguments = function.getElementsByTagName("argument");
            assertEquals(expectedParameters.length, arguments.getLength());
            for (int p = 0; p < expectedParameters.length; p++) {
                assertEquals(expectedParameters[p], arguments.item(p).getFirstChild().getNodeValue());
            }

            final NodeList annotations = function.getElementsByTagName("annotation");
            assertEquals(expectedAnnotations.length, annotations.getLength());
            for (int a = 0; a < expectedAnnotations.length; a++) {
                final Element annotation = (Element) annotations.item(a);
                assertEquals(expectedAnnotations[a], annotation.getAttribute("name"));
                if (expectedAnnotationValues[a] == null) {
                    assertNull(annotation.getFirstChild());
                } else {
                    assertEquals(expectedAnnotationValues[a], annotation.getFirstChild().getFirstChild().getNodeValue());
                }
            }

            final NodeList returns = function.getElementsByTagName("returns");
            assertEquals(1, returns.getLength());
            assertEquals(expectedReturn, returns.item(0).getFirstChild().getNodeValue());

            transaction.commit();
        }
    }

    @BeforeClass
    public static void setup() throws EXistException, PermissionDeniedException, IOException, SAXException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            final Collection testCollection = broker.getOrCreateCollection(transaction, TEST_COLLECTION);

            broker.storeDocument(transaction, TEST_MODULE, new StringInputSource(INSPECT_MODULE.getBytes(UTF_8)), MimeType.XQUERY_TYPE, testCollection);
            broker.saveCollection(transaction, testCollection);

            transaction.commit();
        }
    }

    @AfterClass
    public static void teardown() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            try(final Collection testCollection = broker.openCollection(TEST_COLLECTION, Lock.LockMode.WRITE_LOCK)) {
                if (testCollection != null) {
                    broker.removeCollection(transaction, testCollection);
                }
            }

            transaction.commit();
        }
    }
}
