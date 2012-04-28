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
package org.exist.memtree;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Properties;
import javax.xml.transform.OutputKeys;

import org.exist.EXistException;

import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.AuthenticationException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.util.serializer.SAXSerializer;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

/**
 * Tests the serializing of constructed in-memory fragments.
 * 
 * @author wolf
 */
public class DOMIndexerTest {

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
        "declare namespace f='urn:foo'; " +
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
        "           {collection('" + XmldbURI.ROOT_COLLECTION + "/test')//f:item[@itemno='2']/f:name}" +
        "       </section>" +
        "   ) " +
        "return" +
        "   <result>{$a/title, $a/f:name, $a}</result>";

    @Test
    public void store() throws PermissionDeniedException, IOException, TriggerException, EXistException, SAXException, LockException, AuthenticationException {
    	BrokerPool pool = null;
    	DBBroker broker = null;    
    	try {
            pool = BrokerPool.getInstance();
            Subject admin = pool.getSecurityManager().authenticate("admin", "");
            broker = pool.get(admin);
            Collection collection = broker.getOrCreateCollection(null, TestConstants.TEST_COLLECTION_URI);
            IndexInfo info = collection.validateXMLResource(null, broker, TestConstants.TEST_XML_URI, XML);
            //TODO : unlock the collection here ?
            collection.store(null, broker, info, XML, false);
            @SuppressWarnings("unused")
			org.exist.dom.DocumentImpl doc = info.getDocument();
            broker.flush();
            broker.saveCollection(null, collection);
        } finally {
            if (pool != null) {
                pool.release(broker);
            }
        }
    }

    @Test
    public void xQuery() throws EXistException, PermissionDeniedException, SAXException, XPathException {
    	BrokerPool pool = null;
    	DBBroker broker = null;  
        try {
            pool = BrokerPool.getInstance();
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            XQuery xquery = broker.getXQueryService();
            Sequence result = xquery.execute(XQUERY, null, AccessContext.TEST);
            System.out.println("Found: " + result.getItemCount());
            StringWriter out = new StringWriter();
            Properties props = new Properties();
            props.setProperty(OutputKeys.INDENT, "yes");
            SAXSerializer serializer = new SAXSerializer(out, props);
            serializer.startDocument();
            for(SequenceIterator i = result.iterate(); i.hasNext(); ) {
                Item next = i.nextItem();
                next.toSAX(broker, serializer, props);
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
    @Before
    public void setUp() throws DatabaseConfigurationException, EXistException {
        Configuration config = new Configuration();
        BrokerPool.configure(1, 5, config);
    }  
    
    /* (non-Javadoc)
     * @see junit.framework.TestCase#tearDown()
     */
    @After
    public void tearDown() {
        BrokerPool.stopAll(false);
    }
}