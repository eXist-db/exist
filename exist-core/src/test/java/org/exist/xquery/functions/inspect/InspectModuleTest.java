/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
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
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.util.io.FastByteArrayInputStream;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.junit.*;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class InspectModuleTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final XmldbURI TEST_COLLECTION = XmldbURI.ROOT_COLLECTION_URI.append("test-inspectModule");
    private static final XmldbURI TEST_MODULE = XmldbURI.create("test.xqm");
    private static final String MODULE =
            "xquery version \"1.0\";\n" +
            "module namespace x = \"http://xyz.com\";\n" +
            "\n" +
            "(:~\n" +
            " : Some description.\n" +
            " : @return taxonomy[@type = \"reign\"]\n" +
            " :)\n" +
            "declare function x:fun1() as xs:string {\n" +
            "  \"hello from fun1\"\n" +
            "};\n" +
            "\n" +
            "(:~\n" +
            " : Some other description.\n" +
            " : \n" +
            " : @param one first parameter\n" +
            " : @param two second parameter\n" +
            " : \n" +
            " : @return our result\n" +
            " :)\n" +
            "declare function x:fun2($one as xs:int, $two as xs:float) as xs:string {\n" +
            "  \"hello from fun2\"\n" +
            "};\n" +
            "\n" +
            "(:~\n" +
            " : This is a multiline description and therefore\n" +
            " : spans multiple\n" +
            " : lines.\n" +
            " : \n" +
            " : @return another result\n" +
            " :)\n" +
            "declare function x:fun3() {\n" +
            "  \"hello from fun3\"\n" +
            "};\n" +
            "\n" +
            "(:~\n" +
            " : An annotated function.\n" +
            " : \n" +
            " : @return another result\n" +
            " :)\n" +
            "declare %public %x:path(\"/x/y/z\") function x:fun4() {\n" +
            "  \"hello from fun4\"\n" +
            "};\n";

    @Ignore("https://github.com/eXist-db/exist/issues/1386")
    @Test
    public void xqDoc_withAtSignInline() throws PermissionDeniedException, XPathException, EXistException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xqueryService = pool.getXQueryService();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            final String query =
                    "import module namespace inspect = \"http://exist-db.org/xquery/inspection\";\n" +
                    "inspect:inspect-module(xs:anyURI(\"xmldb:exist://" + TEST_COLLECTION.append(TEST_MODULE).toCollectionPathURI() + "\"))\n" +
                    "/function[@name eq \"x:fun1\"]";

            final Sequence result = xqueryService.execute(broker, query, null);

            assertNotNull(result);
            assertEquals(1, result.getItemCount());
            final Item item1 = result.itemAt(0);
            assertTrue(item1 instanceof ElementImpl);

            final Element function = (Element)item1;

            final NodeList descriptions = function.getElementsByTagName("description");
            assertEquals(1, descriptions.getLength());
            assertEquals("Some description.", descriptions.item(0).getFirstChild().getTextContent());

            final NodeList arguments = function.getElementsByTagName("argument");
            assertEquals(0, arguments.getLength());

            final NodeList returns = function.getElementsByTagName("returns");
            assertEquals(1, returns.getLength());
            assertEquals("taxonomy[@type = \"reign\"]", returns.item(0).getFirstChild().getTextContent());

            transaction.commit();
        }
    }

    @Test
    public void xqDoc_withParamsAndReturn() throws PermissionDeniedException, XPathException, EXistException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xqueryService = pool.getXQueryService();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            final String query =
                    "import module namespace inspect = \"http://exist-db.org/xquery/inspection\";\n" +
                            "inspect:inspect-module(xs:anyURI(\"xmldb:exist://" + TEST_COLLECTION.append(TEST_MODULE).toCollectionPathURI() + "\"))\n" +
                            "/function[@name eq \"x:fun2\"]";

            final Sequence result = xqueryService.execute(broker, query, null);

            assertNotNull(result);
            assertEquals(1, result.getItemCount());
            final Item item1 = result.itemAt(0);
            assertTrue(item1 instanceof ElementImpl);

            final Element function = (Element)item1;

            final NodeList descriptions = function.getElementsByTagName("description");
            assertEquals(1, descriptions.getLength());
            assertEquals("Some other description.", descriptions.item(0).getFirstChild().getNodeValue());

            final NodeList arguments = function.getElementsByTagName("argument");
            assertEquals(2, arguments.getLength());
            assertEquals("first parameter", arguments.item(0).getFirstChild().getNodeValue());
            assertEquals("second parameter", arguments.item(1).getFirstChild().getNodeValue());

            final NodeList returns = function.getElementsByTagName("returns");
            assertEquals(1, returns.getLength());
            assertEquals("our result", returns.item(0).getFirstChild().getNodeValue());

            transaction.commit();
        }
    }

    @Test
    public void xqDoc_multilineDesciption() throws PermissionDeniedException, XPathException, EXistException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xqueryService = pool.getXQueryService();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            final String query =
                    "import module namespace inspect = \"http://exist-db.org/xquery/inspection\";\n" +
                            "inspect:inspect-module(xs:anyURI(\"xmldb:exist://" + TEST_COLLECTION.append(TEST_MODULE).toCollectionPathURI() + "\"))\n" +
                            "/function[@name eq \"x:fun3\"]";

            final Sequence result = xqueryService.execute(broker, query, null);

            assertNotNull(result);
            assertEquals(1, result.getItemCount());
            final Item item1 = result.itemAt(0);
            assertTrue(item1 instanceof ElementImpl);

            final Element function = (Element)item1;

            final NodeList descriptions = function.getElementsByTagName("description");
            assertEquals(1, descriptions.getLength());
            assertEquals("This is a multiline description and therefore\n spans multiple\n lines.", descriptions.item(0).getFirstChild().getNodeValue());

            final NodeList arguments = function.getElementsByTagName("argument");
            assertEquals(0, arguments.getLength());

            final NodeList returns = function.getElementsByTagName("returns");
            assertEquals(1, returns.getLength());
            assertEquals("another result", returns.item(0).getFirstChild().getNodeValue());

            transaction.commit();
        }
    }

    @Test
    public void xqDoc_onAnnotatedFunction() throws PermissionDeniedException, XPathException, EXistException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xqueryService = pool.getXQueryService();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            final String query =
                    "import module namespace inspect = \"http://exist-db.org/xquery/inspection\";\n" +
                            "inspect:inspect-module(xs:anyURI(\"xmldb:exist://" + TEST_COLLECTION.append(TEST_MODULE).toCollectionPathURI() + "\"))\n" +
                            "/function[@name eq \"x:fun4\"]";

            final Sequence result = xqueryService.execute(broker, query, null);

            assertNotNull(result);
            assertEquals(1, result.getItemCount());
            final Item item1 = result.itemAt(0);
            assertTrue(item1 instanceof ElementImpl);

            final Element function = (Element)item1;

            final NodeList descriptions = function.getElementsByTagName("description");
            assertEquals(1, descriptions.getLength());
            assertEquals("An annotated function.", descriptions.item(0).getFirstChild().getNodeValue());

            final NodeList annotations = function.getElementsByTagName("annotation");
            assertEquals(2, annotations.getLength());
            assertEquals("public", ((Element)annotations.item(0)).getAttribute("name"));
            assertEquals("x:path", ((Element)annotations.item(1)).getAttribute("name"));
            assertEquals("/x/y/z", annotations.item(1).getFirstChild().getFirstChild().getNodeValue());

            final NodeList arguments = function.getElementsByTagName("argument");
            assertEquals(0, arguments.getLength());

            final NodeList returns = function.getElementsByTagName("returns");
            assertEquals(1, returns.getLength());
            assertEquals("another result", returns.item(0).getFirstChild().getNodeValue());

            transaction.commit();
        }
    }

    @BeforeClass
    public static void setup() throws EXistException, PermissionDeniedException, IOException, TriggerException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            final Collection testCollection = broker.getOrCreateCollection(transaction, TEST_COLLECTION);

            try (final InputStream is = new FastByteArrayInputStream(MODULE.getBytes(UTF_8))) {
                testCollection.addBinaryResource(transaction, broker, TEST_MODULE, is, "application/xquery", MODULE.getBytes(UTF_8).length);
            }

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
