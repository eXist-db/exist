/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2016 The eXist-db Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 */
package org.exist;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Optional;
import java.util.Properties;
import javax.xml.transform.OutputKeys;

import org.exist.EXistException;
import org.exist.Indexer;
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
import org.junit.*;

import static org.junit.Assert.assertEquals;

import org.xml.sax.SAXException;

/**
 * Tests the indexer.
 *
 * @author ljo
 */
public class IndexerTest3 {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private final static String XML1 =
            "<?xml version=\"1.0\"?>\n" +
                    "<k>\n" +
                    "<l>a <b>b</b> c <d> d </d>  <e>  </e> f</l>\n" +
                    "<m> a <b>b</b> c <d> d </d>  <e>  </e> f </m>\n" +
                    "<n> a<b>b</b> c <d> d </d>  <e>  </e>f </n>\n" +
                    "<o>  <b>b</b> c <d> d </d>  <e>  </e>  </o>\n" +
                    "</k>\n";

    private final static String XML2 =
            "<?xml version=\"1.0\"?>\n" +
                    "<k>\n" +
                    "<l>a <b>b</b> c <d> d </d>  <e>  </e> f</l>\n" +
                    "</k>\n";

    private final static String XML3 =
            "<?xml version=\"1.0\"?>\n" +
                    "<k>\n" +
                    "<m> a <b>b</b> c <d> d </d>  <e>  </e> f </m>\n" +
                    "</k>\n";

    private final static String XML4 =
            "<?xml version=\"1.0\"?>\n" +
                    "<k>\n" +
                    "<n> a<b>b</b> c <d> d </d>  <e>  </e>f </n>\n" +
                    "</k>\n";

    private final static String XML5 =
            "<?xml version=\"1.0\"?>\n" +
                    "<k>\n" +
                    "<o>  <b>b</b> c <d> d </d>  <e>  </e>  </o>\n" +
                    "</k>\n";

    private final static String XML6 =
            "<?xml version=\"1.0\"?>\n" +
                    "<k>\n" +
                    "<!--    a comment with whitespace    leading, intermediate\n" +
                    " and trailing   -->\n" +
                    "</k>\n";

    private final static String XML7 =
            "<?xml version=\"1.0\"?>\n" +
                    "<k>\n" +
                    "    <o>    leading and trailing    </o>\n" +
                    "</k>\n";

    private final static String RESULT_SUPPRESS_WS_NONE_XML1 =
            "<result>" +
                    "<k>\n" +
                    "<l>a <b>b</b> c <d> d </d>  <e>  </e> f</l>\n" +
                    "<m> a <b>b</b> c <d> d </d>  <e>  </e> f </m>\n" +
                    "<n> a<b>b</b> c <d> d </d>  <e>  </e>f </n>\n" +
                    "<o>  <b>b</b> c <d> d </d>  <e>  </e>  </o>\n" +
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_NONE_XML2 =
            "<result>" +
                    "<k>\n" +
                    "<l>a <b>b</b> c <d> d </d>  <e>  </e> f</l>\n" +
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_NONE_XML3 =
            "<result>" +
                    "<k>\n" +
                    "<m> a <b>b</b> c <d> d </d>  <e>  </e> f </m>\n" +
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_NONE_XML4 =
            "<result>" +
                    "<k>\n" +
                    "<n> a<b>b</b> c <d> d </d>  <e>  </e>f </n>\n" +
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_NONE_XML5 =
            "<result>" +
                    "<k>\n" +
                    "<o>  <b>b</b> c <d> d </d>  <e>  </e>  </o>\n" +
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_NONE_XML6 =
            "<result>" +
                    "<k>\n" +
                    "<!--    a comment with whitespace    leading, intermediate\n" +
                    " and trailing   -->\n" +
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_NONE_XML7 =
            "<result>" +
                    "<k>\n" +
                    "    <o>    leading and trailing    </o>\n" +
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_LEADING_XML1 =
            "<result>" +
                    "<k>" +
                    "<l>a <b>b</b> c <d>d </d> <e>  </e> f</l>" +
                    "<m>a <b>b</b> c <d>d </d> <e>  </e> f </m>" +
                    "<n>a<b>b</b> c <d>d </d>  <e>  </e>f </n>" +
                    "<o> <b>b</b> c <d>d </d> <e>  </e>  </o>" +
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_LEADING_XML2 =
            "<result>" +
                    "<k>" +
                    "<l>a <b>b</b> c <d>d </d> <e>  </e> f</l>" +
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_LEADING_XML3 =
            "<result>" +
                    "<k>" +
                    "<m>a <b>b</b> c <d>d </d> <e>  </e> f </m>" +
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_LEADING_XML4 =
            "<result>" +
                    "<k>" +
                    "<n>a<b>b</b> c <d>d </d>  <e>  </e>f </n>" +
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_LEADING_XML5 =
            "<result>" +
                    "<k>" +
                    "<o> <b>b</b> c <d>d </d> <e>  </e>  </o>" +
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_LEADING_XML6 =
            "<result>" +
                    "<k>\n" +
                    "<!--    a comment with whitespace    leading, intermediate\n" +
                    " and trailing   -->\n" +
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_LEADING_XML7 =
            "<result>" +
                    "<k>\n" +
                    "    <o>leading and trailing    </o>\n" +
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_TRAILING_XML1 =
            "<result>" +
                    "<k>" +
                    "<l>a <b>b</b> c <d> d</d>  <e>  </e> f</l>" +
                    "<m> a <b>b</b> c <d> d</d>  <e>  </e> f</m>" +
                    "<n> a<b>b</b> c <d> d</d>  <e>  </e>f</n>" + // kolla " a" och "f "
                    "<o>  <b>b</b> c <d> d</d>  <e>  </e> </o>" +
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_TRAILING_XML2 =
            "<result>" +
                    "<k>" +
                    "<l>a <b>b</b> c <d> d</d>  <e>  </e> f</l>" +
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_TRAILING_XML3 =
            "<result>" +
                    "<k>" +
                    "<m> a <b>b</b> c <d> d</d>  <e>  </e> f</m>" +
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_TRAILING_XML4 =
            "<result>" +
                    "<k>" +
                    "<n> a<b>b</b> c <d> d</d>  <e>  </e>f</n>" + // kolla " a" och "f "
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_TRAILING_XML5 =
            "<result>" +
                    "<k>" +
                    "<o>  <b>b</b> c <d> d</d>  <e>  </e> </o>" +
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_TRAILING_XML6 =
            "<result>" +
                    "<k>\n" +
                    "<!--    a comment with whitespace    leading, intermediate\n" +
                    " and trailing   -->\n" +
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_TRAILING_XML7 =
            "<result>" +
                    "<k>\n" +
                    "    <o>    leading and trailing</o>\n" +
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_BOTH_XML1 =
            "<result>" +
                    "<k>\n" +
                    "<l>a <b>b</b> c <d>d</d>  <e>  </e> f</l>\n" +  // kolla "a"
                    "<m>a <b>b</b> c <d>d</d>  <e>  </e> f</m>\n" +  // kolla "f "
                    "<n>a<b>b</b> c <d>d</d>  <e>  </e>f</n>\n" +
                    "<o> <b>b</b> c <d>d</d>  <e>  </e> </o>\n" +
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_BOTH_XML2 =
            "<result>" +
                    "<k>\n" +
                    "<l>a <b>b</b> c <d>d</d>  <e>  </e> f</l>\n" +  // kolla "a"
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_BOTH_XML3 =
            "<result>" +
                    "<k>\n" +
                    "<m>a <b>b</b> c <d>d</d>  <e>  </e> f</m>\n" +  // kolla "f "
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_BOTH_XML4 =
            "<result>" +
                    "<k>\n" +
                    "<n>a<b>b</b> c <d>d</d>  <e> </e>f</n>\n" +
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_BOTH_XML5 =
            "<result>" +
                    "<k>\n" +
                    "<o>  <b>b</b>c <d>d</d> <e> </e> </o>\n" +
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_BOTH_XML6 =
            "<result>" +
                    "<k>\n" +
                    "<!--    a comment with whitespace    leading, intermediate\n" +
                    " and trailing   -->\n" +
                    "</k>" +
                    "</result>";

    private final static String RESULT_SUPPRESS_WS_BOTH_XML7 =
            "<result>" +
                    "<k>\n" +
                    "    <o>leading and trailing</o>\n" +
                    "</k>" +
                    "</result>";

    private final static String XQUERY =
            "let $test := doc('" + TestConstants.TEST_COLLECTION_URI.toString() + "/" + TestConstants.TEST_XML_URI.toString() + "') " +
                    "return " +
                    "    <result>{$test/k}</result>";

    private void store_suppress_type(final String propValue, final String xml) throws PermissionDeniedException, IOException, EXistException, SAXException, LockException, AuthenticationException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        pool.getConfiguration().setProperty(Indexer.PROPERTY_SUPPRESS_WHITESPACE, propValue);
        // Make sure to keep preserve whitespace mixed content stable even if default changes. fixme! - should test both. /ljo
        boolean propWSMValue = false;
        pool.getConfiguration().setProperty(Indexer.PROPERTY_PRESERVE_WS_MIXED_CONTENT, propWSMValue);

        final TransactionManager txnMgr = pool.getTransactionManager();

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().authenticate("admin", "")));
                final Txn txn = txnMgr.beginTransaction()) {

            final Collection collection = broker.getOrCreateCollection(txn, TestConstants.TEST_COLLECTION_URI);
            final IndexInfo info = collection.validateXMLResource(txn, broker, TestConstants.TEST_XML_URI, xml);
            //TODO : unlock the collection here ?
            collection.store(txn, broker, info, xml);
            @SuppressWarnings("unused")
            final org.exist.dom.persistent.DocumentImpl doc = info.getDocument();
            broker.flush();
            broker.saveCollection(txn, collection);
            txnMgr.commit(txn);
        }
    }

    private String store_and_retrieve_suppress_type(final String type, final String typeXml, final String typeXquery) throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        store_suppress_type(type, typeXml);
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final StringWriter out = new StringWriter()) {
            final XQuery xquery = pool.getXQueryService();
            final Sequence result = xquery.execute(broker, typeXquery, null);
            final Properties props = new Properties();
            props.setProperty(OutputKeys.INDENT, "no");
            final SAXSerializer serializer = new SAXSerializer(out, props);
            serializer.startDocument();
            for (final SequenceIterator i = result.iterate(); i.hasNext(); ) {
                final Item next = i.nextItem();
                next.toSAX(broker, serializer, props);
            }
            serializer.endDocument();
            return out.toString();
        }
    }

    @Test
    public void retrieve_suppress_ws_none1() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_NONE_XML1, store_and_retrieve_suppress_type("none", XML1, XQUERY));
    }

    @Test
    public void retrieve_suppress_ws_none2() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_NONE_XML2, store_and_retrieve_suppress_type("none", XML2, XQUERY));
    }

    @Test
    public void retrieve_suppress_ws_none3() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_NONE_XML3, store_and_retrieve_suppress_type("none", XML3, XQUERY));
    }

    @Test
    public void retrieve_suppress_ws_none4() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_NONE_XML4, store_and_retrieve_suppress_type("none", XML4, XQUERY));
    }

    @Test
    public void retrieve_suppress_ws_none5() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_NONE_XML5, store_and_retrieve_suppress_type("none", XML5, XQUERY));
    }

    @Test
    public void retrieve_suppress_ws_none6() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_NONE_XML6, store_and_retrieve_suppress_type("none", XML6, XQUERY));
    }

    @Test
    public void retrieve_suppress_ws_none7() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_NONE_XML7, store_and_retrieve_suppress_type("none", XML7, XQUERY));
    }

    @Ignore
    @Test
    public void retrieve_suppress_ws_leading1() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_LEADING_XML1, store_and_retrieve_suppress_type("leading", XML1, XQUERY));
    }

    @Ignore
    @Test
    public void retrieve_suppress_ws_leading2() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_LEADING_XML2, store_and_retrieve_suppress_type("leading", XML2, XQUERY));
    }

    @Ignore
    @Test
    public void retrieve_suppress_ws_leading3() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_LEADING_XML3, store_and_retrieve_suppress_type("leading", XML3, XQUERY));
    }

    @Ignore
    @Test
    public void retrieve_suppress_ws_leading4() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_LEADING_XML4, store_and_retrieve_suppress_type("leading", XML4, XQUERY));
    }

    @Ignore
    @Test
    public void retrieve_suppress_ws_leading5() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_LEADING_XML5, store_and_retrieve_suppress_type("leading", XML5, XQUERY));
    }

    @Test
    public void retrieve_suppress_ws_leading6() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_LEADING_XML6, store_and_retrieve_suppress_type("leading", XML6, XQUERY));
    }

    @Test
    public void retrieve_suppress_ws_leading7() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_LEADING_XML7, store_and_retrieve_suppress_type("leading", XML7, XQUERY));
    }

    @Ignore
    @Test
    public void retrieve_suppress_ws_trailing1() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_TRAILING_XML1, store_and_retrieve_suppress_type("trailing", XML1, XQUERY));
    }

    @Ignore
    @Test
    public void retrieve_suppress_ws_trailing2() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_TRAILING_XML2, store_and_retrieve_suppress_type("trailing", XML2, XQUERY));
    }

    @Ignore
    @Test
    public void retrieve_suppress_ws_trailing3() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_TRAILING_XML3, store_and_retrieve_suppress_type("trailing", XML3, XQUERY));
    }

    @Ignore
    @Test
    public void retrieve_suppress_ws_trailing4() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_TRAILING_XML4, store_and_retrieve_suppress_type("trailing", XML4, XQUERY));
    }

    @Ignore
    @Test
    public void retrieve_suppress_ws_trailing5() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_TRAILING_XML5, store_and_retrieve_suppress_type("trailing", XML5, XQUERY));
    }

    @Test
    public void retrieve_suppress_ws_trailing6() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_TRAILING_XML6, store_and_retrieve_suppress_type("trailing", XML6, XQUERY));
    }

    @Test
    public void retrieve_suppress_ws_trailing7() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_TRAILING_XML7, store_and_retrieve_suppress_type("trailing", XML7, XQUERY));
    }

    @Ignore
    @Test
    public void retrieve_suppress_ws_both1() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_BOTH_XML1, store_and_retrieve_suppress_type("both", XML1, XQUERY));
    }

    @Ignore
    @Test
    public void retrieve_suppress_ws_both2() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_BOTH_XML2, store_and_retrieve_suppress_type("both", XML2, XQUERY));
    }

    @Ignore
    @Test
    public void retrieve_suppress_ws_both3() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_BOTH_XML3, store_and_retrieve_suppress_type("both", XML3, XQUERY));
    }

    @Ignore
    @Test
    public void retrieve_suppress_ws_both4() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_BOTH_XML4, store_and_retrieve_suppress_type("both", XML4, XQUERY));
    }

    @Ignore
    @Test
    public void retrieve_suppress_ws_both5() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_BOTH_XML5, store_and_retrieve_suppress_type("both", XML5, XQUERY));
    }

    @Test
    public void retrieve_suppress_ws_both6() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_BOTH_XML6, store_and_retrieve_suppress_type("both", XML6, XQUERY));
    }

    @Test
    public void retrieve_suppress_ws_both7() throws EXistException, IOException, LockException, AuthenticationException, PermissionDeniedException, SAXException, XPathException {
        assertEquals(RESULT_SUPPRESS_WS_BOTH_XML7, store_and_retrieve_suppress_type("both", XML7, XQUERY));
    }
}
