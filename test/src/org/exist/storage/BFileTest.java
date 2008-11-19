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
 * \$Id\$
 */
package org.exist.storage;

import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.DocumentSet;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.w3c.dom.Node;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.util.Random;
import java.util.Properties;

public class BFileTest {

    private String CONFIG =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
                    "	<index>" +
                    "		<fulltext default=\"none\">" +
                    "		</fulltext>" +
                    "	</index>" +
                    "</collection>";

    private Random random = new Random(System.currentTimeMillis());

    @Test
    public void storeAndRecover() throws IOException {
        PrintWriter out = new PrintWriter(new FileWriter("BFileTest.log"));
        out.println("<test>");
        for (int i = 0; i < 10; i++) {
            System.out.println("-----------------------------------------------------");
            System.out.println("Run: " + i);
            storeDocuments(i, out);
            out.flush();
            closeDB();
            restart();
            closeDB();
            remove();
            closeDB();
        }
        out.println("</test>");
        out.close();
    }

    private File createDocument(Element docConfig, PrintWriter out) throws IOException {
        File file = File.createTempFile("eXistTest", ".xml");
        OutputStream os = new FileOutputStream(file);
        Writer writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));

        writer.write("<test>");
        int elementCnt;
        int typeCnt;
        if (docConfig == null) {
            elementCnt = random.nextInt(50);
            typeCnt = random.nextInt(20);
        } else {
            elementCnt = Integer.parseInt(docConfig.getAttribute("elements"));
            typeCnt = Integer.parseInt(docConfig.getAttribute("names"));
        }
        out.print("<document elements=\"");
        out.print(elementCnt);
        out.print("\" names=\"");
        out.print(typeCnt);
        out.println("\"/>");
        for (int i = 0; i < elementCnt; i++) {
            for (int j = 0; j < typeCnt; j++) {
                writer.write("<key" + j + " id=\"");
                writer.write(Integer.toString(i));
                writer.write("\"/>");
            }
        }
        writer.write("</test>");
        writer.close();
        return file;
    }

    /**
     * Store some documents, reindex the collection and crash without commit.
     */
    private void storeDocuments(int cnt, PrintWriter out) {
        Element config = readSettings();
        NodeList nl = config.getElementsByTagName("collections");
        config = (Element) nl.item(cnt);
        
        BrokerPool pool = null;
        DBBroker broker = null;
        try {
            pool = startDB();
            assertNotNull(pool);
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            assertNotNull(broker);
            TransactionManager transact = pool.getTransactionManager();
            assertNotNull(transact);
            Txn transaction = transact.beginTransaction();
            assertNotNull(transaction);
            System.out.println("Transaction started ...");

            Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            pool.getConfigurationManager().addConfiguration(transaction, broker, root, CONFIG);

            transact.commit(transaction);
            transact.getJournal().flushToLog(true);

            BrokerPool.FORCE_CORRUPTION = true;

            int collectionCnt;
            NodeList collections = null;
            if (config == null)
                collectionCnt = random.nextInt(20);
            else {
                collectionCnt = Integer.parseInt(config.getAttribute("count"));
                collections = config.getElementsByTagName("collection");
            }

            out.println("<collections count=\"" + collectionCnt + "\">");
            for (int i = 0; i < collectionCnt; i++) {
                transaction = transact.beginTransaction();
                Collection child =
                        broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI.append("c" + i));
                assertNotNull(child);
                broker.saveCollection(transaction, child);
                transact.commit(transaction);
                Element collectionElem = null;
                NodeList documents = null;
                int docCount;
                if (config == null)
                    docCount = random.nextInt(100);
                else {
                    collectionElem = (Element) collections.item(i);
                    docCount = Integer.parseInt(collectionElem.getAttribute("documents"));
                    documents = collectionElem.getElementsByTagName("document");
                }
                out.println("<collection documents=\"" + docCount + "\">");
                for (int j = 0; j < docCount; j++) {
                    transaction = transact.beginTransaction();
                    Element document = null;
                    if (config != null)
                        document = (Element) documents.item(j);
                    File file = createDocument(document, out);
                    IndexInfo info = child.validateXMLResource(transaction, broker, XmldbURI.create("test" + j + ".xml"),
                            new InputSource(file.toURI().toASCIIString()));
                    assertNotNull(info);
                    child.store(transaction, broker, info, new InputSource(file.toURI().toASCIIString()), false);
                    broker.saveCollection(transaction, child);
                    file.delete();
//                    if (j < docCount - 50)
                        transact.commit(transaction);
                }
                out.println("</collection>");
            }
            out.println("</collections>");
            transact.getJournal().flushToLog(true);
            System.out.println("Transaction interrupted ...");
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null)
                pool.release(broker);
        }
    }

    private void restart() {
        BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = null;
        DBBroker broker = null;
        try {
            System.out.println("restart() ...\n");
            pool = startDB();
            assertNotNull(pool);
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            assertNotNull(broker);

            Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, Lock.READ_LOCK);
            assertNotNull(root);

            XQuery xquery = broker.getXQueryService();
            DocumentSet docs = broker.getAllXMLResources(new DocumentSet());
            Sequence result = xquery.execute("//key1/@id/string()", docs.toNodeSet(), AccessContext.TEST);
            System.out.println("------------ Keys: " + result.getItemCount() + " --------------");
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null)
                pool.release(broker);
        }
    }

    private void remove() {
        BrokerPool pool = null;
        DBBroker broker = null;
        try {
            System.out.println("remove() ...\n");
            pool = startDB();
            assertNotNull(pool);
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            assertNotNull(broker);
            TransactionManager transact = pool.getTransactionManager();
            assertNotNull(transact);
            Txn transaction = transact.beginTransaction();
            assertNotNull(transaction);

            Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, Lock.READ_LOCK);
            assertNotNull(root);
            broker.removeCollection(transaction, root);

            transact.commit(transaction);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null)
                pool.release(broker);
        }
    }

    private Element readSettings() {
        File f = new File("settings.xml");
        if (f.canRead()) {
            System.out.println("Parsing " + f.getAbsolutePath());
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(f);
                return doc.getDocumentElement();
            } catch (ParserConfigurationException e) {
                e.printStackTrace();
                fail(e.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
                fail(e.getMessage());
            } catch (SAXException e) {
                e.printStackTrace();
                fail(e.getMessage());
            }
        }
        return null;
    }

    public void closeDB() {
        BrokerPool.stopAll(false);
    }

    protected BrokerPool startDB() {
        try {
            Configuration config = new Configuration();
            BrokerPool.configure(1, 5, config);
            return BrokerPool.getInstance();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        return null;
    }
}
