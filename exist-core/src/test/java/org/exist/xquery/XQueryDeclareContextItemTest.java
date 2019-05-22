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

package org.exist.xquery;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.QName;
import org.exist.dom.memtree.ElementImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xml.sax.SAXException;

import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests for XQuery 3.0's declare context item
 *
 * @author aretter
 */
@RunWith(ParallelRunner.class)
public class XQueryDeclareContextItemTest {

    private static final String SYSEVENT_XML = "<log xmlns=\"http://syslog\">some-event</log>";

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @BeforeClass
    public static void setup() throws EXistException, PermissionDeniedException, IOException, SAXException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            final Collection root = broker.getOrCreateCollection(transaction,
                    TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            final IndexInfo info = root.validateXMLResource(transaction, broker,
                    XmldbURI.create("sysevent.xml"), SYSEVENT_XML);
            assertNotNull(info);
            root.store(transaction, broker, info, SYSEVENT_XML);

            transaction.commit();
        }
    }

    @AfterClass
    public static void cleanup() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            final Collection test = broker.getCollection(TestConstants.TEST_COLLECTION_URI);
            if(test != null) {
                broker.removeCollection(transaction, test);
            }

            transaction.commit();
        }
    }

    @Test
    public void declareContextItem() throws EXistException, PermissionDeniedException, XPathException {
        final String query =
                "xquery version \"3.0\";\n" +
                "declare context item := 3; \n" +
                ". + 4";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xquery = pool.getXQueryService();

        try(final DBBroker broker = pool.getBroker()) {
            final Sequence result = xquery.execute(broker, query, null);
            assertEquals(1, result.getItemCount());
            assertEquals(7, (int)result.itemAt(0).toJavaObject(int.class));
        }
    }

    /**
     * See issue https://github.com/eXist-db/exist/issues/2156
     */
    @Test
    public void declareContextItemIsDocument() throws EXistException, PermissionDeniedException, XPathException {
        final String query =
                "xquery version \"3.0\";\n" +
                "declare context item := document { <root><item>foo</item><item>baz</item></root> }; \n" +
                "(/) instance of document-node()";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xquery = pool.getXQueryService();

        try(final DBBroker broker = pool.getBroker()) {
            final Sequence result = xquery.execute(broker, query, null);
            assertEquals(1, result.getItemCount());
            assertEquals(true, result.effectiveBooleanValue());
        }
    }

    @Test
    public void declareContextItemTyped() throws EXistException, PermissionDeniedException, XPathException {
        final String query =
                "xquery version \"3.0\";\n" +
                        "declare context item as xs:integer := 3; \n" +
                        ". + 4";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xquery = pool.getXQueryService();

        try(final DBBroker broker = pool.getBroker()) {
            final Sequence result = xquery.execute(broker, query, null);
            assertEquals(1, result.getItemCount());
            assertEquals(7, (int)result.itemAt(0).toJavaObject(int.class));
        }
    }

    @Test
    public void declareContextItemExternal() throws EXistException, PermissionDeniedException, XPathException {
        final String query =
                "xquery version \"3.0\";\n" +
                        "declare context item external; \n" +
                        ". + 4";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xquery = pool.getXQueryService();

        try(final DBBroker broker = pool.getBroker()) {
            final Sequence result = xquery.execute(broker, query, new IntegerValue(3));
            assertEquals(1, result.getItemCount());
            assertEquals(7, (int)result.itemAt(0).toJavaObject(int.class));
        }
    }

    @Test
    public void declareContextItemExternalDefault() throws EXistException, PermissionDeniedException, XPathException {
        final String query =
                "xquery version \"3.0\";\n" +
                        "declare context item external := 3; \n" +
                        ". + 4";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xquery = pool.getXQueryService();

        try(final DBBroker broker = pool.getBroker()) {
            final Sequence result = xquery.execute(broker, query, null);
            assertEquals(1, result.getItemCount());
            assertEquals(7, (int)result.itemAt(0).toJavaObject(int.class));
        }
    }

    @Test
    public void declareContextItemExternalDefaultOverrides() throws EXistException, PermissionDeniedException, XPathException {
        final String query =
                "xquery version \"3.0\";\n" +
                        "declare context item external := 3; \n" +
                        ". + 4";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xquery = pool.getXQueryService();

        try(final DBBroker broker = pool.getBroker()) {
            final Sequence result = xquery.execute(broker, query, new IntegerValue(20));
            assertEquals(1, result.getItemCount());
            assertEquals(24, (int)result.itemAt(0).toJavaObject(int.class));
        }
    }

    @Test
    public void declareContextItemExternalElement() throws EXistException, PermissionDeniedException, XPathException, SAXException {
        final String query =
                "xquery version \"3.0\";\n" +
                        "declare namespace env=\"http://www.w3.org/2003/05/soap-envelope\";\n" +
                        "declare context item as element(env:Envelope) external;\n" +
                        "<wrap>{.}</wrap>";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xquery = pool.getXQueryService();

        try(final DBBroker broker = pool.getBroker()) {
            final MemTreeBuilder builder = new MemTreeBuilder();
            builder.startDocument();
            builder.startElement(new QName("Envelope", "http://www.w3.org/2003/05/soap-envelope"), null);
            builder.endElement();
            builder.endDocument();

            final ElementImpl elem = (ElementImpl)builder.getDocument().getDocumentElement();

            final Sequence result = xquery.execute(broker, query, elem);
            assertEquals(1, result.getItemCount());
            assertEquals("<wrap><Envelope xmlns=\"http://www.w3.org/2003/05/soap-envelope\"/></wrap>", serialize(broker, (NodeValue)result.itemAt(0)));
        }
    }

    @Test
    public void contextItemExternalDefaultElement() throws EXistException, SAXException, PermissionDeniedException, XPathException {
        final String query =
                "xquery version \"3.0\";\n" +
                        "declare namespace sys=\"http://syslog\";\n" +
                        "declare context item as element(sys:log) external := doc(\"" + TestConstants.TEST_COLLECTION_URI.getCollectionPath() + "/sysevent.xml\")/sys:log;\n" +
                        "<wrap>{.}</wrap>";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xquery = pool.getXQueryService();

        try(final DBBroker broker = pool.getBroker()) {
            final Sequence result = xquery.execute(broker, query, null);
            assertEquals(1, result.getItemCount());
            assertEquals("<wrap><log xmlns=\"http://syslog\">some-event</log></wrap>", serialize(broker, (NodeValue)result.itemAt(0)));
        }
    }

    private String serialize(final DBBroker broker, final NodeValue nodeValue) throws SAXException {
        final Serializer serializer = broker.newSerializer();
        final Properties properties = new Properties();
        properties.setProperty(OutputKeys.INDENT, "no");
        serializer.setProperties(properties);
        return serializer.serialize(nodeValue);
    }
}
