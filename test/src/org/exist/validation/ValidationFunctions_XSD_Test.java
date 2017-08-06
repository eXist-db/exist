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
package org.exist.validation;

import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.Configuration;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.junit.*;

import static org.exist.TestUtils.GUEST_DB_USER;
import static org.exist.util.PropertiesBuilder.propertiesBuilder;
import static org.exist.validation.TestTools.*;
import static org.junit.Assert.*;

import java.nio.file.Path;
import java.util.Optional;

/**
 *  Set of Tests for validation:validate($a) and validation:validate($a, $b)
 * regaring validatin using XSD's.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class ValidationFunctions_XSD_Test {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(
            propertiesBuilder()
                    .set(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "auto")
                    .build(),
            true,
            false);

    private static final String TEST_COLLECTION = "testValidationFunctionsXSD";
    private static final XmldbURI VALIDATION_HOME_COLLECTION_URI = XmldbURI.ROOT_COLLECTION_URI.append(TEST_COLLECTION).append(TestTools.VALIDATION_HOME_COLLECTION);
    private static final XmldbURI VALIDATION_XSD_COLLECTION_URI = VALIDATION_HOME_COLLECTION_URI.append(TestTools.VALIDATION_XSD_COLLECTION);

    @Test
    public void xsd_NotInSystemCatalog() throws XPathException, PermissionDeniedException, EXistException {
        // XSD for addressbook_valid.xml is *not* registered in system catalog.
        // result should be "document is invalid"
        final Sequence result = executeQuery(existEmbeddedServer.getBrokerPool(),
            "validation:validate(xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI.append("addressbook_valid.xml") + "'))");

        assertEquals(1, result.getItemCount());
        final Item r = result.itemAt(0);
        assertTrue(r instanceof BooleanValue);
        assertEquals( "addressbook_valid.xml not in systemcatalog", BooleanValue.FALSE, r );
    }

    @Test
    public void xsd_SpecifiedCatalog() throws XPathException, PermissionDeniedException, EXistException {
        Sequence result = executeQuery(existEmbeddedServer.getBrokerPool(),
            "validation:validate(xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI.append("addressbook_valid.xml") + "'), "
            +" xs:anyURI('" + VALIDATION_XSD_COLLECTION_URI.append("catalog.xml") + "'))");
        assertEquals(1, result.getItemCount());
        Item r = result.itemAt(0);
        assertTrue(r instanceof BooleanValue);
        assertEquals("valid document", BooleanValue.TRUE, r );

        clearGrammarCache();

        result = executeQuery(existEmbeddedServer.getBrokerPool(),
            "validation:validate(xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI.append("addressbook_invalid.xml") + "'), "
            +" xs:anyURI('" + VALIDATION_XSD_COLLECTION_URI.append("catalog.xml") + "'))");
        assertEquals(1, result.getItemCount());
        r = result.itemAt(0);
        assertTrue(r instanceof BooleanValue);
        assertEquals( "invalid document", BooleanValue.FALSE, r );
    }

    @Test
    public void xsd_SpecifiedGrammar() throws XPathException, PermissionDeniedException, EXistException {
        Sequence result = executeQuery(existEmbeddedServer.getBrokerPool(),
            "validation:validate(xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI.append("addressbook_valid.xml") + "'), "
            +" xs:anyURI('" + VALIDATION_XSD_COLLECTION_URI.append("addressbook.xsd") + "'))");
        assertEquals(1, result.getItemCount());
        Item r = result.itemAt(0);
        assertTrue(r instanceof BooleanValue);
        assertEquals("valid document", BooleanValue.TRUE, r );

        clearGrammarCache();

        result = executeQuery(existEmbeddedServer.getBrokerPool(),
            "validation:validate( xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI.append("addressbook_invalid.xml") + "'), "
            +" xs:anyURI('" + VALIDATION_XSD_COLLECTION_URI.append("addressbook.xsd") + "'))");
        assertEquals(1, result.getItemCount());
        r = result.itemAt(0);
        assertTrue(r instanceof BooleanValue);
        assertEquals( "invalid document", BooleanValue.FALSE, r );
    }

    /**
     * NOTE - seems that a trailing '/' is needed on the Collection path when searching!
     */
    @Test
    public void xsd_SearchedGrammar() throws XPathException, PermissionDeniedException, EXistException {
        Sequence result = executeQuery(existEmbeddedServer.getBrokerPool(),
            "validation:validate(xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI.append("addressbook_valid.xml") + "'), "
            +" xs:anyURI('" + VALIDATION_XSD_COLLECTION_URI + "/'))");
        assertEquals(1, result.getItemCount());
        Item r = result.itemAt(0);
        assertTrue(r instanceof BooleanValue);
        assertEquals("valid document", BooleanValue.TRUE, r );

        clearGrammarCache();

        result = executeQuery(existEmbeddedServer.getBrokerPool(),
            "validation:validate(xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI.append("addressbook_valid.xml") + "'), "
            +" xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI + "/'))");
        assertEquals(1, result.getItemCount());
        r = result.itemAt(0);
        assertTrue(r instanceof BooleanValue);
        assertEquals("valid document", BooleanValue.TRUE, r );

        clearGrammarCache();

        result = executeQuery(existEmbeddedServer.getBrokerPool(),
            "validation:validate(xs:anyURI('" +  VALIDATION_HOME_COLLECTION_URI.append("addressbook_invalid.xml") + "') ,"
            +" xs:anyURI('" + VALIDATION_HOME_COLLECTION_URI + "/'))");
        assertEquals(1, result.getItemCount());
        r = result.itemAt(0);
        assertTrue(r instanceof BooleanValue);
        assertEquals( "invalid document", BooleanValue.FALSE, r );
    }

    @BeforeClass
    public static void start() throws Exception {
        createTestCollections();
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

    private static void createTestCollections() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn txn = transact.beginTransaction()) {

            /* create necessary collections if they don't exist */
            final org.exist.collections.Collection validationCol = broker.getOrCreateCollection(txn, VALIDATION_HOME_COLLECTION_URI);
            validationCol.getPermissions().setOwner(GUEST_DB_USER);
            broker.saveCollection(txn, validationCol);

            final org.exist.collections.Collection xsdCol = broker.getOrCreateCollection(txn, VALIDATION_XSD_COLLECTION_URI);
            xsdCol.getPermissions().setOwner(GUEST_DB_USER);
            broker.saveCollection(txn, xsdCol);

            txn.commit();
        }
    }

    private static void createTestDocuments() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Configuration config = pool.getConfiguration();
        final TransactionManager transact = pool.getTransactionManager();

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getGuestSubject()));
             final Txn txn = transact.beginTransaction()) {

            /* create necessary documents  */
            final Path addressBook = TestUtils.resolveSample("validation/addressbook");

            final String prevValidationMode = (String)config.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE);
            config.setProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "no");

            final org.exist.collections.Collection xsdCol = broker.getCollection(VALIDATION_XSD_COLLECTION_URI);
            storeDocument(broker, txn, xsdCol, "addressbook.xsd", addressBook.resolve("addressbook.xsd"));
            storeDocument(broker, txn, xsdCol, "catalog.xml", addressBook.resolve("catalog.xml"));

            final org.exist.collections.Collection validationCol = broker.getCollection(VALIDATION_HOME_COLLECTION_URI);
            storeDocument(broker, txn, validationCol, "addressbook_valid.xml", addressBook.resolve("addressbook_valid.xml"));
            storeDocument(broker, txn, validationCol, "addressbook_invalid.xml", addressBook.resolve("addressbook_invalid.xml"));


            config.setProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, prevValidationMode);

            txn.commit();
        }
    }

    private static void removeTestCollections() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn txn = transact.beginTransaction()) {

            final org.exist.collections.Collection validationCol = broker.getOrCreateCollection(txn, VALIDATION_HOME_COLLECTION_URI);
            broker.removeCollection(txn, validationCol);

            transact.commit(txn);
        }
    }
    
}
