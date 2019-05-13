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

import com.evolvedbinary.j8fu.tuple.Tuple2;
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
import org.exist.xquery.value.Type;
import org.junit.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import javax.xml.transform.Source;
import java.io.IOException;
import java.util.Optional;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class TransformTest {

    @ClassRule
    public static ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final XmldbURI TEST_IDS_COLLECTION = XmldbURI.create("/db/transform-ids-test");

    private static final XmldbURI INPUT_LIST_XML_NAME = XmldbURI.create("inputListOps.xml");

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
            "declare variable $xsltPath as xs:string := '" + TEST_IDS_COLLECTION.getCollectionPath() + "';\n" +
            "declare variable $listOpsFileUri as xs:string := '" + TEST_IDS_COLLECTION.getCollectionPath()+ "/listOpsErr.xml';\n" +
            "declare variable $inputFileUri as xs:string := '" + TEST_IDS_COLLECTION.getCollectionPath() + "/inputListOps.xml';\n" +
            "\n" +
            "let $params :=  <parameters>\n" +
            "                    <param name=\"listOpsFileUri\" value=\"{$listOpsFileUri}\" />\n" +
            "                </parameters>\n" +
            "\n" +
            "let $xmlData := doc($inputFileUri)\n" +
            "\n" +
            "return transform:transform($xmlData, doc(concat($xsltPath, '/', 'testListOps.xsl')),$params)";

    private static final XmldbURI TEST_DOCUMENT_XSLT_COLLECTION = XmldbURI.create("/db/transform-doc-test");
    private static final XmldbURI DOCUMENT_XSLT_NAME = XmldbURI.create("xsl-doc.xslt");

    private static final String DOCUMENT_XSLT =
            "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"2.0\">\n" +
            "\t<xsl:template name=\"xsl-doc\">\n" +
            "\t\t<xsl:document><elem1/></xsl:document>\n" +
            "\t</xsl:template>\n" +
            "</xsl:stylesheet>";

    private static final String DOCUMENT_XSLT_QUERY =
            "import module namespace transform=\"http://exist-db.org/xquery/transform\";\n" +
            "\n" +
            "let $xsl := doc('" + TEST_DOCUMENT_XSLT_COLLECTION.append(DOCUMENT_XSLT_NAME).getRawCollectionPath() + "')\n" +
            "return\n" +
            "\ttransform:transform((), $xsl, (), <attributes><attr name=\"http://saxon.sf.net/feature/initialTemplate\" value=\"xsl-doc\"/></attributes>, ())";

    private static final XmldbURI SIMPLE_XML_NAME = XmldbURI.create("simple.xml");
    private static final XmldbURI TEST_SIMPLE_XML_COLLECTION = XmldbURI.create("/db/transform-simple-test");
    private static final XmldbURI TEST_SIMPLE_XML_WITH_COMMENT_COLLECTION = XmldbURI.create("/db/transform-simple-1c-test");
    private static final XmldbURI TEST_SIMPLE_XML_WITH_TWO_COMMENTS_COLLECTION = XmldbURI.create("/db/transform-simple-2c-test");

    private static final String SIMPLE_XML =
            "<n/>";

    private static final String SIMPLE_XML_WITH_COMMENT =
            "<!-- -->\n" +
            "<n/>";

    private static final String SIMPLE_XML_WITH_TWO_COMMENTS =
            "<!-- 1 --><!-- 2 -->\n" +
            "<n/>";

    private static final XmldbURI COUNT_DESCENDANTS_XSLT_NAME = XmldbURI.create("count-descendants.xslt");

    private static final XmldbURI TWO_NODES_XML_NAME = XmldbURI.create("two-nodes.xml");
    private static final XmldbURI TEST_TWO_NODES_COLLECTION = XmldbURI.create("/db/transform-two-nodes-test");
    private static final String TWO_NODES_XML = "<a><b/></a>";

    private static final XmldbURI COUNT_DESCENDANTS_TWO_NODES_XSLT_NAME = XmldbURI.create("count-descendants-two-nodes.xslt");

    private static final String COUNT_DESCENDANTS_TWO_NODES_XSLT =
            "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"2.0\">\n" +
            "\n" +
            "\t<xsl:variable name=\"xml\" select=\"doc('" + TEST_TWO_NODES_COLLECTION.append(TWO_NODES_XML_NAME).getRawCollectionPath() + "')\"/>\n" +
            "\t\n" +
            "\t<xsl:template match=\"/\">\n" +
            "\t\t<counts>\n" +
            "\t\t\t<count1><xsl:value-of select=\"count($xml//*)\"/></count1>\n" +
            "\t\t\t<count2><xsl:value-of select=\"count($xml/a//*)\"/></count2>\n" +
            "\t\t</counts>\n" +
            "\t</xsl:template>\n" +
            "\n" +
            "</xsl:stylesheet>";

    private static final String COUNT_DESCENDANTS_TWO_NODES_QUERY =
            "import module namespace transform=\"http://exist-db.org/xquery/transform\";\n" +
                "\n" +
                "let $xml := doc('" + TEST_TWO_NODES_COLLECTION.append(TWO_NODES_XML_NAME).getRawCollectionPath() + "')\n" +
                "let $xsl := doc('" + TEST_TWO_NODES_COLLECTION.append(COUNT_DESCENDANTS_TWO_NODES_XSLT_NAME).getRawCollectionPath() + "')\n" +
                "return\n" +
                "\ttransform:transform($xml, $xsl, ())";


    /**
     * {@see https://github.com/eXist-db/exist/issues/1506}
     */
    @Test
    public void keys() throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xquery = pool.getXQueryService();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

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

    @Ignore("https://github.com/eXist-db/exist/issues/2096")
    @Test
    public void xslDocument() throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xquery = pool.getXQueryService();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Sequence sequence = xquery.execute(broker, DOCUMENT_XSLT_QUERY, null);

            assertNotNull(sequence);
            assertTrue(sequence.hasOne());

            final Item item = sequence.itemAt(0);

            assertEquals(Type.DOCUMENT, item.getType());

            final Source expected = Input.fromString("<elem1/>").build();
            final Source actual = Input.fromDocument(sequence.itemAt(0).toJavaObject(Document.class)).build();

            final Diff diff = DiffBuilder.compare(actual)
                    .withTest(expected)
                    .checkForSimilar()
                    .build();

            assertFalse(diff.toString(), diff.hasDifferences());
        }
    }

    /**
     * {@see https://github.com/eXist-db/exist/issues/1691}
     */
    @Test
    public void transformReindexTransform() throws XPathException, PermissionDeniedException, EXistException, IOException, LockException {
        transform1(TEST_SIMPLE_XML_COLLECTION);
        reindex(TEST_SIMPLE_XML_COLLECTION);
        transform1(TEST_SIMPLE_XML_COLLECTION);
    }

    /**
     * {@see https://github.com/eXist-db/exist/issues/1691}
     */
    @Test
    public void transformReindexTransform_with_comment() throws XPathException, PermissionDeniedException, EXistException, IOException, LockException {
        transform1(TEST_SIMPLE_XML_WITH_COMMENT_COLLECTION);
        reindex(TEST_SIMPLE_XML_WITH_COMMENT_COLLECTION);
        transform1(TEST_SIMPLE_XML_WITH_COMMENT_COLLECTION);
    }

    /**
     * {@see https://github.com/eXist-db/exist/issues/1691}
     */
    @Test
    public void transformReindexTransform_with_two_comments() throws XPathException, PermissionDeniedException, EXistException, IOException, LockException {
        transform1(TEST_SIMPLE_XML_WITH_TWO_COMMENTS_COLLECTION);
        reindex(TEST_SIMPLE_XML_WITH_TWO_COMMENTS_COLLECTION);
        transform1(TEST_SIMPLE_XML_WITH_TWO_COMMENTS_COLLECTION);
    }

    /**
     * {@see https://github.com/eXist-db/exist/issues/1691}
     */
    @Test
    public void twoNodesCountDescendants() throws EXistException, PermissionDeniedException, XPathException, IOException, LockException {
        transform_twoNodesCountDescendants();
        reindex(TEST_TWO_NODES_COLLECTION);
        transform_twoNodesCountDescendants();
    }

    private static void transform1(final XmldbURI collectionUri) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xquery = pool.getXQueryService();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Sequence sequence = xquery.execute(broker, getCountDescendantsXquery(collectionUri), null);

            assertNotNull(sequence);
            assertTrue(sequence.hasOne());

            final Source expected = Input.fromString("<count-descendants>1</count-descendants>").build();
            final Source actual = Input.fromDocument(sequence.itemAt(0).toJavaObject(Node.class).getOwnerDocument()).build();

            final Diff diff = DiffBuilder.compare(expected)
                    .withTest(actual)
                    .checkForSimilar()
                    .build();

            assertFalse(diff.toString(), diff.hasDifferences());
        }
    }

    private void reindex(final XmldbURI collectionUri) throws EXistException, PermissionDeniedException, IOException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            broker.reindexCollection(transaction, collectionUri);
            transaction.commit();
        }
    }

    private static String getCountDescendantsXquery(final XmldbURI collectionUri) {
        return
                "import module namespace transform=\"http://exist-db.org/xquery/transform\";\n" +
                "\n" +
                "let $xml := doc('" + collectionUri.append(SIMPLE_XML_NAME).getRawCollectionPath() + "')\n" +
                "let $xsl := doc('" + collectionUri.append(COUNT_DESCENDANTS_XSLT_NAME).getRawCollectionPath() + "')\n" +
                "return\n" +
                "\ttransform:transform($xml, $xsl, ())";
    }

    private static String getCountDescendantsXslt(final XmldbURI collectionUri) {
        return
                "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"2.0\">\n" +
                "\n" +
                "\t<xsl:variable name=\"xml\" select=\"document('" + collectionUri.append(SIMPLE_XML_NAME).getRawCollectionPath() + "')\"/>\n" +
                "\t\n" +
                "\t<xsl:template match=\"/\">\n" +
                "\t\t<count-descendants><xsl:value-of select=\"count($xml//*)\"/></count-descendants>\n" +
                "\t</xsl:template>\n" +
                "\n" +
                "</xsl:stylesheet>";
    }

    private static void transform_twoNodesCountDescendants() throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xquery = pool.getXQueryService();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Sequence sequence = xquery.execute(broker, COUNT_DESCENDANTS_TWO_NODES_QUERY, null);

            assertNotNull(sequence);
            assertTrue(sequence.hasOne());

            final Source expected = Input.fromString("<counts><count1>2</count1><count2>1</count2></counts>").build();
            final Source actual = Input.fromDocument(sequence.itemAt(0).toJavaObject(Node.class).getOwnerDocument()).build();

            final Diff diff = DiffBuilder.compare(expected)
                    .withTest(actual)
                    .checkForSimilar()
                    .build();

            assertFalse(diff.toString(), diff.hasDifferences());
        }
    }

    @BeforeClass
    public static void storeResources() throws EXistException, PermissionDeniedException, IOException, SAXException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            createCollection(broker, transaction, TEST_IDS_COLLECTION,
                    Tuple(LIST_OPS_XSLT_NAME, LIST_OPS_XSLT),
                    Tuple(INPUT_LIST_XML_NAME, INPUT_XML),
                    Tuple(DICTIONARY_XML_NAME, DICTIONARY_XML)
            );

            createCollection(broker, transaction, TEST_DOCUMENT_XSLT_COLLECTION,
                    Tuple(DOCUMENT_XSLT_NAME, DOCUMENT_XSLT)
            );

            createCollection(broker, transaction, TEST_SIMPLE_XML_COLLECTION,
                    Tuple(SIMPLE_XML_NAME, SIMPLE_XML),
                    Tuple(COUNT_DESCENDANTS_XSLT_NAME, getCountDescendantsXslt(TEST_SIMPLE_XML_COLLECTION))
            );

            createCollection(broker, transaction, TEST_SIMPLE_XML_WITH_COMMENT_COLLECTION,
                    Tuple(SIMPLE_XML_NAME, SIMPLE_XML_WITH_COMMENT),
                    Tuple(COUNT_DESCENDANTS_XSLT_NAME, getCountDescendantsXslt(TEST_SIMPLE_XML_WITH_COMMENT_COLLECTION))
            );

            createCollection(broker, transaction, TEST_SIMPLE_XML_WITH_TWO_COMMENTS_COLLECTION,
                    Tuple(SIMPLE_XML_NAME, SIMPLE_XML_WITH_TWO_COMMENTS),
                    Tuple(COUNT_DESCENDANTS_XSLT_NAME, getCountDescendantsXslt(TEST_SIMPLE_XML_WITH_TWO_COMMENTS_COLLECTION))
            );

            createCollection(broker, transaction, TEST_TWO_NODES_COLLECTION,
                    Tuple(TWO_NODES_XML_NAME, TWO_NODES_XML),
                    Tuple(COUNT_DESCENDANTS_TWO_NODES_XSLT_NAME, COUNT_DESCENDANTS_TWO_NODES_XSLT)
            );

            transaction.commit();
        }
    }

    @SafeVarargs
    private static void createCollection(final DBBroker broker, final Txn transaction, final XmldbURI collectionUri, final Tuple2<XmldbURI, String>... docs) throws PermissionDeniedException, IOException, SAXException, LockException, EXistException {
        try (final ManagedCollectionLock collectionLock = broker.getBrokerPool().getLockManager().acquireCollectionWriteLock(collectionUri)) {
            final Collection collection = broker.getOrCreateCollection(transaction, collectionUri);
            broker.saveCollection(transaction, collection);
            for (final Tuple2<XmldbURI, String> doc : docs) {
                storeXml(broker, transaction, collection, doc._1, doc._2);
            }
        }
    }

    private static void storeXml(final DBBroker broker, final Txn transaction, final Collection collection, final XmldbURI name, final String xml) throws LockException, SAXException, PermissionDeniedException, EXistException, IOException {
        final IndexInfo indexInfo = collection.validateXMLResource(transaction, broker, name, xml);
        collection.store(transaction, broker, indexInfo, xml);
    }

    private static void deleteCollection(final DBBroker broker, final Txn transaction, final XmldbURI collectionUri) throws PermissionDeniedException, IOException, TriggerException {
        try(final Collection collection = broker.openCollection(collectionUri, Lock.LockMode.WRITE_LOCK)) {
            if (collection != null) {
                broker.removeCollection(transaction, collection);
            }
        }
    }

    @AfterClass
    public static void cleanupResources() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            deleteCollection(broker, transaction, TEST_IDS_COLLECTION);
            deleteCollection(broker, transaction, TEST_DOCUMENT_XSLT_COLLECTION);
            deleteCollection(broker, transaction, TEST_SIMPLE_XML_COLLECTION);
            deleteCollection(broker, transaction, TEST_SIMPLE_XML_WITH_COMMENT_COLLECTION);
            deleteCollection(broker, transaction, TEST_SIMPLE_XML_WITH_TWO_COMMENTS_COLLECTION);
            deleteCollection(broker, transaction, TEST_TWO_NODES_COLLECTION);

            transaction.commit();
        }
    }
}
