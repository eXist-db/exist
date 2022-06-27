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
package org.exist.xquery;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.util.StringInputSource;
import org.exist.util.serializer.XQuerySerializer;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.value.Sequence;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Optional;
import java.util.Properties;

import static org.exist.test.Util.*;
import static org.junit.Assert.assertEquals;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertNotNull;

public class ForwardReferenceTest {

    @ClassRule
    public static final ExistEmbeddedServer EXIST_EMBEDDED_SERVER = new ExistEmbeddedServer(true, true);

    private static final XmldbURI TEST_COLLECTION_URI = XmldbURI.create("/db/test-deferred-function-call");

    private static final XmldbURI CONFIG_MODULE_NAME = XmldbURI.create("config.xqm");
    private static final InputSource CONFIG_MODULE = new StringInputSource(
            ("xquery version \"3.1\";\n" +
            "module namespace config = \"http://example.com/config\";\n" +
            "\n" +
            "import module namespace pages = \"http://example.com/pages\" at \"pages.xqm\";\n" +
            "\n" +
            "declare variable $config:PUBLICATIONS := map {\n" +
            "    \"short-title\" : \"My Non-default Short Title\"\n" +
            "};\n" +
            "\n" +
            "declare variable $config:OPEN_GRAPH as map(xs:string, function(*)) := map {\n" +
            "    \"og:type\" : function($node, $model) {\n" +
            "        <meta property=\"og:type\" content=\"website\"/>\n" +
            "    },\n" +
            "    \"og:title\": function($node, $model) {\n" +
            "        <meta property=\"og:title\" content=\"{pages:generate-short-title($node, $model)}\"/>\n" +
            "    }\n" +
            "};").getBytes(UTF_8));
    private static XmldbURI CONFIG_MODULE_URI = null;

    private static final XmldbURI PAGES_MODULE_NAME = XmldbURI.create("pages.xqm");
    private static final InputSource PAGES_MODULE = new StringInputSource(
            ("xquery version \"3.1\";\n" +
            "module namespace pages = \"http://example.com/pages\";\n" +
            "\n" +
            "import module namespace config = \"http://example.com/config\" at \"config.xqm\";\n" +
            "\n" +
            "declare function pages:generate-short-title($node, $model) as xs:string? {\n" +
            "    (   \n" +
            "         $config:PUBLICATIONS?short-title,\n" +
            "        'My Default Short Title'\n" +
            "    )[. ne ''][1]\n" +
            "};").getBytes(UTF_8));
    private static XmldbURI PAGES_MODULE_URI = null;

    private static final XmldbURI TEST_PAGES_MODULE_NAME = XmldbURI.create("test-pages.xqm");
    private static final InputSource TEST_PAGES_MODULE = new StringInputSource(
            ("xquery version \"3.1\";\n" +
            "module namespace test-pages = \"http://example.com/test-pages\";\n" +
            "\n" +
            "import module namespace pages = \"http://example.com/pages\" at \"pages.xqm\";\n" +
            "import module namespace config = \"http://example.com/config\" at \"config.xqm\";\n" +
            "\n" +
            "declare namespace test = \"http://exist-db.org/xquery/xqsuite\";\n" +
            "\n" +
            "declare\n" +
            "    %test:assertEquals(\"My Non-default Short Title\")\n" +
            "function test-pages:generate-short-title-default() {\n" +
            "    pages:generate-short-title((),())\n" +
            "};\n").getBytes(UTF_8));
    private static XmldbURI TEST_PAGES_MODULE_URI = null;

    @BeforeClass
    public static void setup() throws EXistException, PermissionDeniedException, IOException, SAXException, LockException {
        final BrokerPool brokerPool = EXIST_EMBEDDED_SERVER.getBrokerPool();
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
             final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {


            try (final Collection testCollection = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI)) {

                CONFIG_MODULE_URI = storeQuery(broker, transaction, CONFIG_MODULE, testCollection, CONFIG_MODULE_NAME);
                assertEquals(TEST_COLLECTION_URI.append(CONFIG_MODULE_NAME), CONFIG_MODULE_URI);

                PAGES_MODULE_URI = storeQuery(broker, transaction, PAGES_MODULE, testCollection, PAGES_MODULE_NAME);
                assertEquals(TEST_COLLECTION_URI.append(PAGES_MODULE_NAME), PAGES_MODULE_URI);

                TEST_PAGES_MODULE_URI = storeQuery(broker, transaction, TEST_PAGES_MODULE, testCollection, TEST_PAGES_MODULE_NAME);
                assertEquals(TEST_COLLECTION_URI.append(TEST_PAGES_MODULE_NAME), TEST_PAGES_MODULE_URI);
            }

            transaction.commit();
        }
    }

    @Test
    public void test1() throws EXistException, PermissionDeniedException, IOException, TriggerException, XPathException {
        final StringSource testXquerySource = new StringSource(
                "xquery version \"3.1\";\n" +
                "\n" +
                "import module namespace xqsuite = \"http://exist-db.org/xquery/xqsuite\"\n" +
                "    at \"resource:org/exist/xquery/lib/xqsuite/xqsuite.xql\";\n" +
                "\n" +
                "xqsuite:suite((\n" +
                "    inspect:module-functions(xs:anyURI(\"xmldb:exist://" + TEST_PAGES_MODULE_URI + "\"))\n" +
                "))");

        final BrokerPool brokerPool = EXIST_EMBEDDED_SERVER.getBrokerPool();
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
             final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

            final String xqSuiteXmlResult = withCompiledQuery(broker, testXquerySource, compiledQuery -> {
                final Sequence result = executeQuery(broker, compiledQuery);
                try (final StringWriter writer = new StringWriter()) {
                    final XQuerySerializer xquerySerializer = new XQuerySerializer(broker, new Properties(), writer);
                    xquerySerializer.serialize(result);
                    return writer.toString();
                } catch (final IOException | SAXException e) {
                    throw new XPathException((Expression) null, e);
                }
            });

            assertNotNull(xqSuiteXmlResult);

            transaction.commit();
        }
    }
}
