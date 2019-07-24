/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-10 The eXist-db Project
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
 *
 * $Id$
 */
package org.exist.xqj;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.EXistException;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.util.serializer.SAXSerializer;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.*;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.xmldb.XmldbURI;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeProxy;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.runner.RunWith;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.StringWriter;
import java.io.StringReader;
import java.util.Optional;
import java.util.Properties;

/**
 * @author Wolfgang Meier
 */

/**
 * Test cases for the Marshaller class methods. The Marshaller class offers serialization services
 * needed by the XQJ interfaces.
 * 
 * @author Cherif YAYA
 *
 */
@RunWith(ParallelRunner.class)
public class MarshallerTest {

    private static XmldbURI TEST_COLLECTION_URI = XmldbURI.ROOT_COLLECTION_URI.append("xqjmarhallertest");

    private static String TEST_DOC =
            "<test>" +
            "   <div xmlns=\"urn:foo\" xmlns:ns1=\"urn:baz\" id=\"div1\">" +
            "       <head>Title</head>" +
            "       <p ns1:attr=\"a\" rend=\"bold\">Some <hi>text</hi>.</p>" +
            "   </div>" +
            "</test>";
    
    
    
    @Test
    public void atomicValues() throws EXistException, XPathException, SAXException, XMLStreamException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            ValueSequence values = new ValueSequence(3);
            values.add(new StringValue("foo"));
            values.add(new IntegerValue(2000, Type.INTEGER));
            values.add(new IntegerValue(1000, Type.LONG));
            values.add(new BooleanValue(false));
            values.add(new DoubleValue(1000.1));

            StringWriter writer = new StringWriter();
            SAXSerializer serializer = new SAXSerializer(writer, new Properties());
            Marshaller.marshall(broker, values, serializer);
            String serialized = writer.toString();

            Sequence seq = Marshaller.demarshall(broker, new StringReader(serialized));
            assertEquals(seq.itemAt(0).getStringValue(), "foo");
            assertEquals(seq.itemAt(1).getStringValue(), "2000");
            assertEquals(seq.itemAt(2).getStringValue(), "1000");
            assertEquals(seq.itemAt(3).getStringValue(), "false");
            assertEquals(seq.itemAt(4).getStringValue(), "1000.1");
        }
    }
    
    

    @Test
    public void nodes() throws EXistException, PermissionDeniedException, SAXException, XPathException, XMLStreamException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            DocumentImpl doc = (DocumentImpl) broker.getXMLResource(TEST_COLLECTION_URI.append("test.xml"));
            NodeProxy p = new NodeProxy(doc, pool.getNodeFactory().createFromString("1.1"));

            StringWriter writer = new StringWriter();
            SAXSerializer serializer = new SAXSerializer(writer, new Properties());
            Marshaller.marshall(broker, p, serializer);
            String serialized = writer.toString();

            Sequence seq = Marshaller.demarshall(broker, new StringReader(serialized));
            assertTrue(Type.subTypeOf(seq.getItemType(), Type.NODE));
            NodeValue n = (NodeValue) seq.itemAt(0);

            writer = new StringWriter();
            serializer.reset();
            serializer.setOutput(writer, new Properties());
            n.toSAX(broker, serializer, new Properties());
        }
    }
    
    @Test
    public void streamToNodeTest() throws XMLStreamException {
        Node n = Marshaller.streamToNode(TEST_DOC);
        StringWriter writer = new StringWriter();
//            SAXSerializer serializer = 
                new SAXSerializer(writer, new Properties());
        //n.toSAX(null, serializer, new Properties());

        assertEquals("test",n.getLocalName());
    }

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @BeforeClass
    public static void startDB() throws EXistException, DatabaseConfigurationException, PermissionDeniedException, IOException, SAXException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

            final Collection root = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI);
            broker.saveCollection(transaction, root);

            final IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"), TEST_DOC);
            root.store(transaction, broker, info, TEST_DOC);

            transact.commit(transaction);
        }
    }

    @AfterClass
    public static void shutdown() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {
            Collection root = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI);
            broker.removeCollection(transaction, root);
            transact.commit(transaction);
        }
    }
}