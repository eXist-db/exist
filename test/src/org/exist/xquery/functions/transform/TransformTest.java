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
package org.exist.xquery.functions.transform;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.ManagedCollectionLock;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.junit.*;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Optional;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @see https://github.com/eXist-db/exist/issues/1506
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class TransformTest {

    @ClassRule
    public static ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, false);

    private static final XmldbURI TEST_COLLECTION = XmldbURI.create("/db/transform-test");

    private static final XmldbURI INPUT_XML_NAME = XmldbURI.create("inputListOps.xml");

    private static final String INPUT_XML =
            "<listOps>\n" +
            "    <ops id=\"IRCANTEC\"/>\n" +
            "    <ops id=\"CIBTP\"/>\n" +
            "    <ops id=\"AGIRC-ARRCO\"/>\n" +
            "    <ops id=\"CTIP-FFSA-FNMF\"/>\n" +
            "</listOps>";

    private static final XmldbURI DICTIONARY_XML_NAME = XmldbURI.create("listOpsErr.xml");

    private static final String DICTIONARY_XML =
            "<listOps>\n" +
            "    <ops id=\"IRCANTEC\" doEntiteAff=\"false\" doGenerateB20=\"true\"> </ops>\n" +
            "    <ops id=\"CIBTP\" doEntiteAff=\"true\" doGenerateB20=\"true\"/>\n" +
            "    <ops id=\"AGIRC-ARRCO\" doEntiteAff=\"true\" doGenerateB20=\"false\"> </ops>\n" +
            "    <ops id=\"CTIP-FFSA-FNMF\" doEntiteAff=\"true\" doGenerateB20=\"true\"> </ops>\n" +
            "    <ops id=\"POLEEMPLOI\" doEntiteAff=\"true\" doGenerateB20=\"true\"> </ops>\n" +
            "</listOps>";

    private static final XmldbURI LIST_OPS_XSLT_NAME = XmldbURI.create("testListOps.xsl");

    private static final String LIST_OPS_XSLT =
            "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" xmlns:ts=\"http://www.talentia-software.fr\" version=\"2.0\">\n" +
            "    <xsl:output method=\"xml\" indent=\"no\" encoding=\"UTF-8\"/>\n" +
            "    <!-- -->\n" +
            "    <xsl:param name=\"listOpsFileUri\" required=\"yes\"/>\n" +
            "\n" +
            "    <!-- -->\n" +
            "    <xsl:variable name=\"ts:listOps\" select=\"doc($listOpsFileUri)\"/>\n" +
            "\n" +
            "    <xsl:key name=\"ts:listOpsById\" match=\"//ops\" use=\"@id\"/>\n" +
            "\n" +
            "    <!-- -->\n" +
            "    <xsl:template match=\"/\">\n" +
            "        <xsl:if test=\"empty($ts:listOps)\">\n" +
            "            <xsl:message terminate=\"yes\">Could not find listOpsFileUri document</xsl:message>\n" +
            "        </xsl:if>\n" +
            "\n" +
            "        <DSN_FLAT>\n" +
            "            <xsl:for-each select=\"//ops\">\n" +
            "                <xsl:variable name=\"keyId\" select=\"@id\"/>\n" +
            "                <xsl:variable name=\"refListOpsEntry\" select=\"$ts:listOps/key('ts:listOpsById', $keyId)\"/>\n" +
            "                <xsl:element name=\"keyId\">\n" +
            "                    <xsl:value-of select=\"$keyId\"/>\n" +
            "                </xsl:element>\n" +
            "                <xsl:element name=\"listOpsEntry\">\n" +
            "                    <xsl:for-each select=\"$refListOpsEntry/@*\">\n" +
            "                        <xsl:value-of select=\"concat(name(), ': ', ., ' ')\"/>\n" +
            "                    </xsl:for-each>\n" +
            "                </xsl:element>\n" +
            "            </xsl:for-each>\n" +
            "        </DSN_FLAT>\n" +
            "    </xsl:template>\n" +
            "</xsl:stylesheet>";

    private static final String LIST_OPS_XQUERY =
            "xquery version \"3.0\";\n" +
            "\n" +
            "(:Read document with xsl:for-each and look for key in the dictionary document :)\n" +
            "declare variable $xsltPath as xs:string := '" + TEST_COLLECTION.getCollectionPath() + "';\n" +
            "declare variable $listOpsFileUri as xs:string := '" + TEST_COLLECTION.getCollectionPath()+ "/listOpsErr.xml';\n" +
            "declare variable $inputFileUri as xs:string := '" + TEST_COLLECTION.getCollectionPath() + "/inputListOps.xml';\n" +
            "\n" +
            "let $params :=  <parameters>\n" +
            "                    <param name=\"listOpsFileUri\" value=\"{$listOpsFileUri}\" />\n" +
            "                </parameters>\n" +
            "\n" +
            "let $xmlData := doc($inputFileUri)\n" +
            "\n" +
            "return transform:transform($xmlData, doc(concat($xsltPath, '/', 'testListOps.xsl')),$params)";

    @Test
    public void keys() throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xquery = pool.getXQueryService();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            /*final Txn transaction = existEmbeddedServer.getBrokerPool().getTransactionManager().beginTransaction() */) {

            final Sequence sequence = xquery.execute(broker, LIST_OPS_XQUERY, null);
            assertNotNull(sequence);

            assertEquals(1, sequence.getItemCount());
            final Item item = sequence.itemAt(0);
            assertTrue(item instanceof Element);
            final Element dsn_flat = ((Element)item);
            assertEquals("DSN_FLAT", dsn_flat.getNodeName());

            final NodeList nodeList = dsn_flat.getElementsByTagName("listOpsEntry");
            assertEquals(4, nodeList.getLength());
            assertEquals("id: IRCANTEC doEntiteAff: false doGenerateB20: true ", nodeList.item(0).getTextContent());
            assertEquals("id: CIBTP doEntiteAff: true doGenerateB20: true ", nodeList.item(1).getTextContent());
            assertEquals("id: AGIRC-ARRCO doEntiteAff: true doGenerateB20: false ", nodeList.item(2).getTextContent());
            assertEquals("id: CTIP-FFSA-FNMF doEntiteAff: true doGenerateB20: true ", nodeList.item(3).getTextContent());
        }
    }

    private static void storeXml(final DBBroker broker, final Txn transaction, final Collection collection, final XmldbURI name, final String xml) throws LockException, SAXException, PermissionDeniedException, EXistException, IOException {
        final IndexInfo indexInfo = collection.validateXMLResource(transaction, broker, name, xml);
        collection.store(transaction, broker, indexInfo, xml);
    }

    @BeforeClass
    public static void storeResources() throws EXistException, PermissionDeniedException, IOException, SAXException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction();
            final ManagedCollectionLock collectionLock = pool.getLockManager().acquireCollectionWriteLock(TEST_COLLECTION)) {
                final Collection testCollection = broker.getOrCreateCollection(transaction, TEST_COLLECTION);

            storeXml(broker, transaction, testCollection, LIST_OPS_XSLT_NAME, LIST_OPS_XSLT);
            storeXml(broker, transaction, testCollection, INPUT_XML_NAME, INPUT_XML);
            storeXml(broker, transaction, testCollection, DICTIONARY_XML_NAME, DICTIONARY_XML);

            broker.saveCollection(transaction, testCollection);

            transaction.commit();
        }
    }

    @AfterClass
    public static void cleanupResources() throws EXistException, PermissionDeniedException, IOException, TriggerException {
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
