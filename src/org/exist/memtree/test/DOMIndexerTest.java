/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.memtree.test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import junit.framework.TestCase;

import org.exist.collections.Collection;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.SAXAdapter;
import org.exist.security.SecurityManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.Configuration;
import org.exist.util.serializer.SAXSerializer;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * Tests the serializing of constructed in-memory fragments.
 * 
 * @author wolf
 */
public class DOMIndexerTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(DOMIndexerTest.class);
    }

    private final static String XML =
        "<?xml version=\"1.0\"?>" +
        "<!-- A comment -->" +
        "<root xmlns=\"urn:foo\" id=\"1\">" +
        "<?php print('Hello'); ?>" +
        "<item xmlns:x=\"urn:x\" itemno=\"1\" partno=\"54\">" +
        "<name>City bike</name>" +
        "<x:price>555.60</x:price>" +
        "</item>" +
        "<item itemno=\"2\" partno=\"67\">" +
        "<name>Racing bike</name>" +
        "<x:price xmlns:x=\"urn:x\">600.99</x:price>" +
        "</item>" +
        "</root>";
    
    private final static String XQUERY =
        "let $a := " +
        "   (" +
        "		<!-- My test data -->," +
        "       <section>" +
        "           <title>Section 1</title>" +
        "           <para>First paragraph</para>" +
        "       </section>," +
        "       <section>" +
        "           <title>Section 2</title>" +
        "           <para>First paragraph in second section.</para>" +
        "       </section>" +
        "   ) " +
        "return" +
        "   ($a/title, $a//para)";
    
    public void testIndexer() throws Exception {
        DocumentImpl doc = parse(XML);
        BrokerPool pool = BrokerPool.getInstance();
        DBBroker broker = null;
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);
            org.exist.dom.DocumentImpl targetDoc = broker.storeTemporaryDoc(doc);
            System.out.println("testIndexer(): " + targetDoc.printTreeLevelOrder());
            
            Serializer serializer = broker.getSerializer();
            serializer.reset();
            System.out.println(serializer.serialize(targetDoc));
        } finally {
            pool.release(broker);
        }
    }
    
    public void testStore() throws Exception {
        BrokerPool pool = BrokerPool.getInstance();
        DBBroker broker = null;
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);
            Collection collection = broker.getOrCreateCollection("/db/test");
            org.exist.dom.DocumentImpl doc = 
                collection.addDocument(broker, "test.xml", XML, "text/xml");
            broker.flush();
            broker.saveCollection(collection);
            System.out.println("testStore(): " + doc.printTreeLevelOrder());
        } finally {
            pool.release(broker);
        }
    }
    
    public void testXQuery() throws Exception {
        BrokerPool pool = BrokerPool.getInstance();
        DBBroker broker = null;
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);
            XQuery xquery = broker.getXQueryService();
            Sequence result = xquery.execute(XQUERY, null);
            System.out.println("Found: " + result.getLength());
            StringWriter out = new StringWriter();
            SAXSerializer serializer = new SAXSerializer(out, new Properties());
            serializer.startDocument();
            for (SequenceIterator i = result.iterate(); i.hasNext(); ) {
                Item next = i.nextItem();
                next.toSAX(broker, serializer);
            }
            serializer.endDocument();
            System.out.println(out.toString());
        } finally {
            pool.release(broker);
        }
    }
    
    /* (non-Javadoc)
     * @see junit.framework.TestCase#setUp()
     */
    protected void setUp() throws Exception {
        String home, file = "conf.xml";
        home = System.getProperty("exist.home");
        if (home == null)
            home = System.getProperty("user.dir");
        try {
            Configuration config = new Configuration(file, home);
            BrokerPool.configure(1, 5, config);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }  
    
    /* (non-Javadoc)
     * @see junit.framework.TestCase#tearDown()
     */
    protected void tearDown() throws Exception {
        BrokerPool.stopAll(false);
    }
    
    protected DocumentImpl parse(String input) throws ParserConfigurationException, SAXException, IOException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
//        factory.setFeature("http://apache.org/xml/features/validation/schema", true);
//        factory.setFeature("http://apache.org/xml/features/validation/dynamic", true);
        InputSource src = new InputSource(new StringReader(input));
        SAXParser parser = factory.newSAXParser();
        XMLReader reader = parser.getXMLReader();
        SAXAdapter adapter = new SAXAdapter();
        reader.setContentHandler(adapter);
        reader.setProperty(
                "http://xml.org/sax/properties/lexical-handler",
                adapter);
        reader.parse(src);
        
        DocumentImpl doc = (DocumentImpl) adapter.getDocument();
        return doc;
    }
}
