/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
package org.exist.storage;

import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.util.ValueOccurrences;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.StringReader;

public class RangeIndexUpdateTest {

    private static String COLLECTION_CONFIG =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index>" +
    	"		<fulltext default=\"none\">" +
    	"		</fulltext>" +
    	"		<create qname=\"item\" type=\"xs:string\"/>" +
        "		<create path=\"//item/@attr\" type=\"xs:string\"/>" +
        "        <create path=\"/section/para\" type=\"xs:string\"/>" +
        "	</index>" +
    	"</collection>";

    private static String XML =
            "<test>" +
            "   <item id='1' attr='attribute'><description>Chair</description></item>" +
            "   <item id='2'><description>Table</description><price>892.25</price></item>" +
            "   <item id='3'><description>Cabinet</description><price>1525.00</price></item>" +
            "</test>";

    private static String XML2 =
            "<section>" +
            "   <para>01234</para>" +
            "   <para>56789</para>" +
            "</section>";

    private static String XUPDATE_START =
        "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">";

    private static String XUPDATE_END =
        "</xu:modifications>";

    private final static QName ITEM_QNAME = new QName("item", "", "");

    private static BrokerPool pool;
    private static DocumentSet docs;

    @Test
    public void updates() {
        DBBroker broker = null;
        try {
        	broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            TransactionManager transact = pool.getTransactionManager();
            Txn transaction = transact.beginTransaction();

            checkIndex(broker, docs, ITEM_QNAME, new StringValue("Chair"), 1);
            checkIndex(broker, docs, ITEM_QNAME, new StringValue("Table892.25"), 1);
            checkIndex(broker, docs, ITEM_QNAME, new StringValue("Cabinet1525.00"), 1);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//item[. = 'Chair']", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            XUpdateProcessor proc = new XUpdateProcessor(broker, docs, AccessContext.TEST);
            assertNotNull(proc);
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            String xupdate =
                    XUPDATE_START +
                    "   <xu:update select=\"//item[@id = '1']/description\">Wardrobe</xu:update>" +
                    XUPDATE_END;
            Modification[] modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, ITEM_QNAME, new StringValue("Chair"), 0);
            checkIndex(broker, docs, ITEM_QNAME, new StringValue("Wardrobe"), 1);

            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "   <xu:update select=\"//item[@id = '1']/description/text()\">Wheelchair</xu:update>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, ITEM_QNAME, new StringValue("Wardrobe"), 0);
            checkIndex(broker, docs, ITEM_QNAME, new StringValue("Wheelchair"), 1);

            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "   <xu:update select=\"//item[@id = '1']/@attr\">abc</xu:update>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();
            checkIndex(broker, docs, null, new StringValue("abc"), 1);

            transact.commit(transaction);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null) {
                pool.release(broker);
            }
        }
    }

    private void checkIndex(DBBroker broker, DocumentSet docs, QName qname, StringValue term, int count) {

        ValueOccurrences[] occurrences;
        if (qname == null)
            occurrences = broker.getValueIndex().scanIndexKeys(docs, null, term);
        else
            occurrences = broker.getValueIndex().scanIndexKeys(docs, null, new QName[] { qname }, term);
        int found = 0;
        for (int i = 0; i < occurrences.length; i++) {
            ValueOccurrences occurrence = occurrences[i];
            System.out.println("Found: " + occurrence.getValue());
            if (occurrence.getValue().compareTo(term) == 0)
                found++;
        }
        assertEquals(count, found);
    }

    @BeforeClass
    public static void startDB() {
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            File confFile = ConfigurationHelper.lookup("conf.xml");
            Configuration config = new Configuration(confFile.getAbsolutePath());
            BrokerPool.configure(1, 5, config);
            pool = BrokerPool.getInstance();
        	assertNotNull(pool);
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            assertNotNull(broker);
            transact = pool.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);
            System.out.println("Transaction started ...");

            Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(transaction, broker, root, COLLECTION_CONFIG);

            docs = new DocumentSet();

            IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("test_string.xml"), XML);
            assertNotNull(info);
            root.store(transaction, broker, info, XML, false);

            docs.add(info.getDocument());

            info = root.validateXMLResource(transaction, broker, XmldbURI.create("test_string2.xml"), XML2);
            assertNotNull(info);
            root.store(transaction, broker, info, XML2, false);

            docs.add(info.getDocument());

            transact.commit(transaction);
        } catch (Exception e) {
            e.printStackTrace();
            transact.abort(transaction);
            fail(e.getMessage());
        } finally {
            if (pool != null)
                pool.release(broker);
        }
    }

    @AfterClass
    public static void cleanup() {
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            assertNotNull(broker);
            transact = pool.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);
            System.out.println("Transaction started ...");

            Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
//            broker.removeCollection(transaction, root);

            Collection config = broker.getOrCreateCollection(transaction,
                XmldbURI.create(CollectionConfigurationManager.CONFIG_COLLECTION + "/db"));
            assertNotNull(config);
//            broker.removeCollection(transaction, config);

            transact.commit(transaction);
        } catch (Exception e) {
        	transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null) pool.release(broker);
        }
        BrokerPool.stopAll(false);
        pool = null;
        docs = null;
    }
}
