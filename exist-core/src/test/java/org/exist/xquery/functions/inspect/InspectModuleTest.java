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
            """;
    private static final String MAIN_MODULE = """
            import module namespace inspect = "http://exist-db.org/xquery/inspection";
            
            inspect:inspect-module(xs:anyURI("xmldb:exist://%s"))/function[@name eq "%s"]
            """;

    @Ignore("https://github.com/eXist-db/exist/issues/1386")
    @Test
    public void xqDoc_withAtSignInline() throws PermissionDeniedException, XPathException, EXistException {
        final String functionName = "x:fun1";
        final String expectedDescription = "Some description.";
        final String[] expectedAnnotations = {};
        final String[] expectedAnnotationValues = {};
        final String[] expectedParameters = {};
        final String expectedReturn = "taxonomy[@type = \"reign\"]";

        assertInspection(functionName, expectedDescription, expectedParameters, expectedAnnotations, expectedAnnotationValues, expectedReturn);
    }

    @Test
    public void xqDoc_withParamsAndReturn() throws PermissionDeniedException, XPathException, EXistException {
        final String functionName = "x:fun2";
        final String expectedDescription = "Some other description.";
        final String[] expectedAnnotations = {};
        final String[] expectedAnnotationValues = {};
        final String[] expectedParameters = { "first parameter", "second parameter" };
        final String expectedReturn = "our result";

        assertInspection(functionName, expectedDescription, expectedParameters, expectedAnnotations, expectedAnnotationValues, expectedReturn);
    }

    @Test
    public void xqDoc_multilineDescription() throws PermissionDeniedException, XPathException, EXistException {
        final String functionName = "x:fun3";
        final String expectedDescription = "This is a multiline description and therefore\n spans multiple\n lines.";
        final String[] expectedAnnotations = {};
        final String[] expectedAnnotationValues = {};
        final String[] expectedParameters = {};
        final String expectedReturn = "another result";

        assertInspection(functionName, expectedDescription, expectedParameters, expectedAnnotations, expectedAnnotationValues, expectedReturn);
    }

    @Test
    public void xqDoc_onAnnotatedFunction() throws PermissionDeniedException, XPathException, EXistException {
        final String functionName = "x:fun4";
        final String expectedDescription = "An annotated function.";
        final String[] expectedAnnotations = { "public", "x:path" };
        final String[] expectedAnnotationValues = { null, "/x/y/z" };
        final String[] expectedParameters = {};
        final String expectedReturn = "another result";

        assertInspection(functionName, expectedDescription, expectedParameters, expectedAnnotations, expectedAnnotationValues, expectedReturn);
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
