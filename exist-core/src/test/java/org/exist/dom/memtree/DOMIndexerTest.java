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
package org.exist.dom.memtree;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Optional;
import java.util.Properties;
import javax.xml.transform.OutputKeys;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.EXistException;

import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.security.AuthenticationException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.LockException;
import org.exist.util.serializer.SAXSerializer;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xml.sax.SAXException;

/**
 * Tests the serializing of constructed in-memory fragments.
 * 
 * @author wolf
 */
@RunWith(ParallelRunner.class)
public class DOMIndexerTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

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
    public void store() throws PermissionDeniedException, IOException, EXistException, SAXException, LockException, AuthenticationException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager txnMgr = pool.getTransactionManager();

    	try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().authenticate("admin", "")));
                final Txn txn = txnMgr.beginTransaction()) {

            final Collection collection = broker.getOrCreateCollection(txn, TestConstants.TEST_COLLECTION_URI);
            final IndexInfo info = collection.validateXMLResource(txn, broker, TestConstants.TEST_XML_URI, XML);
            //TODO : unlock the collection here ?
            collection.store(txn, broker, info, XML);
            @SuppressWarnings("unused")
            final org.exist.dom.persistent.DocumentImpl doc = info.getDocument();
            broker.flush();
            broker.saveCollection(txn, collection);
            txnMgr.commit(txn);
        }
    }

    @Test
    public void xQuery() throws EXistException, PermissionDeniedException, SAXException, XPathException, IOException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final StringWriter out = new StringWriter()) {
            final XQuery xquery = pool.getXQueryService();
            final Sequence result = xquery.execute(broker, XQUERY, null);
            final int count = result.getItemCount();
            final Properties props = new Properties();
            props.setProperty(OutputKeys.INDENT, "yes");
            final SAXSerializer serializer = new SAXSerializer(out, props);
            serializer.startDocument();
            for (final SequenceIterator i = result.iterate(); i.hasNext(); ) {
                final Item next = i.nextItem();
                next.toSAX(broker, serializer, props);
            }
            serializer.endDocument();
            out.toString();
        }
    }
}