/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist-db Project
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
package org.exist.validation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.Configuration;
import org.exist.util.LockException;

import org.exist.xquery.XPathException;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.junit.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.TestUtils.GUEST_DB_USER;
import static org.exist.util.PropertiesBuilder.propertiesBuilder;
import static org.exist.validation.TestTools.executeQuery;
import static org.exist.validation.TestTools.storeDocument;
import static org.exist.validation.TestTools.storeTextDocument;
import static org.junit.Assert.*;

import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.SAXException;

/**
 * Set of Tests for validation:validate($a) and validation:validate($a, $b)
 * regarding validating using DTD's.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class ValidationFunctions_DTD_Test {
    
    private final static String TEST_COLLECTION = "testValidationFunctionsDTD";

    private final static XmldbURI VALIDATION_HOME_COLLECTION_URI = XmldbURI.ROOT_COLLECTION_URI.append(TEST_COLLECTION).append(TestTools.VALIDATION_HOME_COLLECTION);

    private final static XmldbURI VALIDATION_DTD_COLLECTION_URI = VALIDATION_HOME_COLLECTION_URI.append(TestTools.VALIDATION_DTD_COLLECTION);
    private final static XmldbURI VALIDATION_XSD_COLLECTION_URI = VALIDATION_HOME_COLLECTION_URI.append(TestTools.VALIDATION_XSD_COLLECTION);
    private final static XmldbURI VALIDATION_TMP_COLLECTION_URI = VALIDATION_HOME_COLLECTION_URI.append(TestTools.VALIDATION_TMP_COLLECTION);

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(
            propertiesBuilder()
                    .set(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "auto")
                    .build(),
            true,
            false);

    @Test
    public void validateUsingSystemCatalog() throws XPathException, PermissionDeniedException, EXistException {
        // DTD for hamlet_valid.xml is registered in system catalog.
        // result should be "document is valid"
        final Sequence result = executeQuery(existEmbeddedServer.getBrokerPool(), "validation:validate( xs:anyURI('" + VALIDATION_TMP_COLLECTION_URI + "/hamlet_valid.xml'))");
        assertEquals(1, result.getItemCount());
        final Item r = result.itemAt(0);
        assertTrue(r instanceof BooleanValue);
        assertEquals( "hamlet_valid.xml in systemcatalog", BooleanValue.TRUE, r );
    }
    
    @Test
    public void specifiedCatalog_test1() throws XPathException, PermissionDeniedException, EXistException {
        final Sequence result = executeQuery(existEmbeddedServer.getBrokerPool(), "validation:validate(xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI + "/hamlet_valid.xml') ," +" xs:anyURI('" + VALIDATION_DTD_COLLECTION_URI + "/catalog.xml'))");
        assertEquals(1, result.getItemCount());
        final Item r = result.itemAt(0);
        assertTrue(r instanceof BooleanValue);
        assertEquals("valid document", BooleanValue.TRUE, r );
    }

    @Test
    public void specifiedCatalog_test2() throws XPathException, PermissionDeniedException, EXistException {
        final Sequence result = executeQuery(existEmbeddedServer.getBrokerPool(), "validation:validate(xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI + "/hamlet_invalid.xml'), xs:anyURI('" + VALIDATION_DTD_COLLECTION_URI + "/catalog.xml'))");
        assertEquals(1, result.getItemCount());
        final Item r = result.itemAt(0);
        assertTrue(r instanceof BooleanValue);
        assertEquals( "invalid document", BooleanValue.FALSE, r );
    }
            
    @Test
    public void specifiedCatalog_test3() throws XPathException, PermissionDeniedException, EXistException {
        final Sequence result = executeQuery(existEmbeddedServer.getBrokerPool(), "validation:validate(xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI + "/hamlet_valid.xml'), xs:anyURI('" + VALIDATION_XSD_COLLECTION_URI + "/catalog.xml'))");
        assertEquals(1, result.getItemCount());
        final Item r = result.itemAt(0);
        assertTrue(r instanceof BooleanValue);
        assertEquals("wrong catalog", BooleanValue.FALSE, r);
    }

    @Test
    public void specifiedCatalog_test4() throws XPathException, PermissionDeniedException, EXistException {
        final Sequence result = executeQuery(existEmbeddedServer.getBrokerPool(), "validation:validate(xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI + "/hamlet_invalid.xml'), xs:anyURI('" + VALIDATION_XSD_COLLECTION_URI + "/catalog.xml'))");
        assertEquals(1, result.getItemCount());
        final Item r = result.itemAt(0);
        assertTrue(r instanceof BooleanValue);
        assertEquals("wrong catalog, invalid document", BooleanValue.FALSE, r );
    }

    @Test
    public void specifiedGrammar_dtd_forValidDoc() throws XPathException, PermissionDeniedException, EXistException {
        final Sequence result = executeQuery(existEmbeddedServer.getBrokerPool(), "validation:validate(xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI + "/hamlet_valid.xml'), xs:anyURI('" + VALIDATION_DTD_COLLECTION_URI + "/hamlet.dtd'))");
        assertEquals(1, result.getItemCount());
        final Item r = result.itemAt(0);
        assertTrue(r instanceof BooleanValue);
        assertEquals("valid document", BooleanValue.TRUE, r );
    }

    @Test
    public void specifiedGrammar_dtd_forInvalidDoc() throws XPathException, PermissionDeniedException, EXistException {
        final Sequence result = executeQuery(existEmbeddedServer.getBrokerPool(), "validation:validate( xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI + "/hamlet_invalid.xml'), xs:anyURI('" + VALIDATION_DTD_COLLECTION_URI + "/hamlet.dtd') )");
        assertEquals(1, result.getItemCount());
        final Item r = result.itemAt(0);
        assertTrue(r instanceof BooleanValue);
        assertEquals( "invalid document", BooleanValue.FALSE, r );
    }

    @Test
    public void searchedGrammar_valid_dtd() throws XPathException, PermissionDeniedException, EXistException {
        final Sequence result = executeQuery(existEmbeddedServer.getBrokerPool(), "validation:validate( xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI + "/hamlet_valid.xml'), xs:anyURI('" + VALIDATION_DTD_COLLECTION_URI + "/'))");
        assertEquals(1, result.getItemCount());
        final Item r = result.itemAt(0);
        assertTrue(r instanceof BooleanValue);
        assertEquals("valid document", BooleanValue.TRUE, r );
    }

    @Test
    public void searchedGrammar_valid_xsd() throws XPathException, PermissionDeniedException, EXistException {
        final Sequence result = executeQuery(existEmbeddedServer.getBrokerPool(), "validation:validate( xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI + "/hamlet_valid.xml'), xs:anyURI('" + VALIDATION_XSD_COLLECTION_URI + "/') )");
        assertEquals(1, result.getItemCount());
        final Item r = result.itemAt(0);
        assertTrue(r instanceof BooleanValue);
        assertEquals( "valid document, not found", BooleanValue.FALSE, r );
    }
            
    @Test
    public void searchedGrammar_valid() throws XPathException, PermissionDeniedException, EXistException {
        final Sequence result = executeQuery(existEmbeddedServer.getBrokerPool(), "validation:validate(xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI + "/hamlet_valid.xml'), xs:anyURI('/db/'))");
        assertEquals(1, result.getItemCount());
        final Item r = result.itemAt(0);
        assertTrue(r instanceof BooleanValue);
        assertEquals("valid document", BooleanValue.TRUE, r );
    }
            
    @Test
    public void searchedGrammar_invalid() throws XPathException, PermissionDeniedException, EXistException {
        final Sequence result = executeQuery(existEmbeddedServer.getBrokerPool(), "validation:validate(xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI + "/hamlet_invalid.xml'), xs:anyURI('/db/'))");
        assertEquals(1, result.getItemCount());
        final Item r = result.itemAt(0);
        assertTrue(r instanceof BooleanValue);
        assertEquals( "invalid document", BooleanValue.FALSE, r );
    }

    @BeforeClass
    public static void startup() throws SAXException, PermissionDeniedException, EXistException, IOException, LockException {
        //create the collections we need for these tests
        createTestCollections();

        //create the documents we need for the tests
        createTestDocuments();
    }

    @Before
    public void clearGrammarCache() throws XPathException, PermissionDeniedException, EXistException {
        executeQuery(existEmbeddedServer.getBrokerPool(), "validation:clear-grammar-cache()");
    }

    @AfterClass
    public static void shutdown() throws Exception {
        removeTestCollections();
    }

    private static void createTestCollections() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn txn = transact.beginTransaction()) {

            /* create necessary collections if they don't exist */
            final Collection validationCol = broker.getOrCreateCollection(txn, VALIDATION_HOME_COLLECTION_URI);
            validationCol.getPermissions().setOwner(GUEST_DB_USER);
            broker.saveCollection(txn, validationCol);

            final Collection dtdCol = broker.getOrCreateCollection(txn, VALIDATION_DTD_COLLECTION_URI);
            dtdCol.getPermissions().setOwner(GUEST_DB_USER);
            broker.saveCollection(txn, dtdCol);

            final Collection xsdCol = broker.getOrCreateCollection(txn, VALIDATION_XSD_COLLECTION_URI);
            xsdCol.getPermissions().setOwner(GUEST_DB_USER);
            broker.saveCollection(txn, xsdCol);

            final Collection tmpCol = broker.getOrCreateCollection(txn, XmldbURI.create(VALIDATION_HOME_COLLECTION_URI + "/" + TestTools.VALIDATION_TMP_COLLECTION));
            tmpCol.getPermissions().setOwner(GUEST_DB_USER);
            broker.saveCollection(txn, tmpCol);

            transact.commit(txn);
        }
    }

    private static void createTestDocuments() throws EXistException, PermissionDeniedException, LockException, SAXException, IOException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Configuration config = pool.getConfiguration();
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getGuestSubject()));
            final Txn txn = transact.beginTransaction()) {

            /* create necessary documents  */

            //hamlet
            String sb = new String(TestUtils.readHamletSampleXml(), UTF_8);
            sb = sb.replaceAll("\\Q<!\\E.*DOCTYPE.*\\Q-->\\E",
                "<!DOCTYPE PLAY PUBLIC \"-//PLAY//EN\" \"play.dtd\">" );

            final Collection tmpCol = broker.getCollection(VALIDATION_TMP_COLLECTION_URI);
            storeDocument(broker, txn, tmpCol, "hamlet_valid.xml", sb);

            final String prevValidationMode = (String)config.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE);
            config.setProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "no");


            final Path dtd = TestUtils.resolveSample("validation/dtd");

            final Collection dtdCol = broker.getCollection(VALIDATION_DTD_COLLECTION_URI);
            storeTextDocument(broker, txn, dtdCol, "hamlet.dtd", dtd.resolve("hamlet.dtd"));
            storeDocument(broker, txn, dtdCol, "catalog.xml", dtd.resolve("catalog.xml"));

            final Collection validationCol = broker.getCollection(VALIDATION_HOME_COLLECTION_URI);
            storeDocument(broker, txn, validationCol, "hamlet_valid.xml", dtd.resolve("hamlet_valid.xml"));
            storeDocument(broker, txn, validationCol, "hamlet_invalid.xml", dtd.resolve("hamlet_invalid.xml"));

            config.setProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, prevValidationMode);

            transact.commit(txn);
        }
    }

    private static void removeTestCollections() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn txn = transact.beginTransaction()) {

            final Collection testCollection = broker.getOrCreateCollection(txn, VALIDATION_HOME_COLLECTION_URI);
            broker.removeCollection(txn, testCollection);

            transact.commit(txn);
        }
    }
}
