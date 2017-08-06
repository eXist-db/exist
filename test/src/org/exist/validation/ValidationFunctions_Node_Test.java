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
 * $Id: ValidationFunctions_XSD_Test.java 5941 2007-05-29 20:27:59Z dizzzz $
 */
package org.exist.validation;

import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.collections.Collection;
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
import static org.exist.validation.TestTools.executeQuery;
import static org.exist.validation.TestTools.storeDocument;
import static org.junit.Assert.*;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Set of Tests for validation:validate($a) and validation:validate($a, $b)
 * regarding validation using XSD's.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class ValidationFunctions_Node_Test {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(
            propertiesBuilder()
                    .set(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "auto")
                    .build(),
            true,
            false);

    private static final String TEST_COLLECTION = "testValidationFunctionsNode";
    private static final XmldbURI VALIDATION_HOME_COLLECTION_URI = XmldbURI.ROOT_COLLECTION_URI.append(TEST_COLLECTION).append(TestTools.VALIDATION_HOME_COLLECTION);
    private static final XmldbURI VALIDATION_XSD_COLLECTION_URI = VALIDATION_HOME_COLLECTION_URI.append(TestTools.VALIDATION_XSD_COLLECTION);

    @Test
    public void storedNode() throws XPathException, PermissionDeniedException, EXistException {
        String query = "validation:validate(doc('" + VALIDATION_HOME_COLLECTION_URI.append("addressbook_valid.xml") + "'), xs:anyURI('" + VALIDATION_XSD_COLLECTION_URI.append("addressbook.xsd") + "'))";
        Sequence result = executeQuery(existEmbeddedServer.getBrokerPool(), query);
        assertEquals(1, result.getItemCount());

        Item r = result.itemAt(0);
        assertTrue(r instanceof BooleanValue);
        assertEquals("valid document as node", BooleanValue.TRUE, r);

        clearGrammarCache();


        query = "validation:validate(doc('" + VALIDATION_HOME_COLLECTION_URI.append("addressbook_invalid.xml") + "'), xs:anyURI('" + VALIDATION_XSD_COLLECTION_URI.append("addressbook.xsd") + "'))";
        result = executeQuery(existEmbeddedServer.getBrokerPool(), query);
        assertEquals(1, result.getItemCount());

        r = result.itemAt(0);
        assertTrue(r instanceof BooleanValue);
        assertEquals("invalid document as node", BooleanValue.FALSE, r);
    }

    @Test
    public void constructedNode() throws XPathException, PermissionDeniedException, EXistException {
        String query = "let $doc := " +
                "<addressBook xmlns=\"http://jmvanel.free.fr/xsd/addressBook\">" +
                "<owner> <cname>John Punin</cname> <email>puninj@cs.rpi.edu</email> </owner>" +
                "<person> <cname>Harrison Ford</cname> <email>hford@famous.org</email> </person>" +
                "<person> <cname>Julia Roberts</cname> <email>jr@pw.com</email> </person>" +
                "</addressBook> " +
                "let $result := validation:validate( $doc, " +
                " xs:anyURI('" + VALIDATION_XSD_COLLECTION_URI.append("addressbook.xsd") + "') ) " +
                "return $result";
        Sequence result = executeQuery(existEmbeddedServer.getBrokerPool(), query);
        assertEquals(1, result.getItemCount());

        Item r = result.itemAt(0);
        assertTrue(r instanceof BooleanValue);
        assertEquals("valid document as node", BooleanValue.TRUE, r);

        clearGrammarCache();


        query = "let $doc := " +
                "<addressBook xmlns=\"http://jmvanel.free.fr/xsd/addressBook\">" +
                "<owner1> <cname>John Punin</cname> <email>puninj@cs.rpi.edu</email> </owner1>" +
                "<person> <cname>Harrison Ford</cname> <email>hford@famous.org</email> </person>" +
                "<person> <cname>Julia Roberts</cname> <email>jr@pw.com</email> </person>" +
                "</addressBook> " +
                "let $result := validation:validate( $doc, " +
                " xs:anyURI('" + VALIDATION_XSD_COLLECTION_URI.append("addressbook.xsd") + "') ) " +
                "return $result";
        result = executeQuery(existEmbeddedServer.getBrokerPool(), query);
        assertEquals(1, result.getItemCount());

        r = result.itemAt(0);
        assertTrue(r instanceof BooleanValue);
        assertEquals("invalid document as node", BooleanValue.FALSE, r);
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
            final Collection validationCol = broker.getOrCreateCollection(txn, VALIDATION_HOME_COLLECTION_URI);
            validationCol.getPermissions().setOwner(GUEST_DB_USER);
            broker.saveCollection(txn, validationCol);

            final Collection xsdCol = broker.getOrCreateCollection(txn, VALIDATION_XSD_COLLECTION_URI);
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

            final Collection xsdCol = broker.getCollection(VALIDATION_XSD_COLLECTION_URI);
            storeDocument(broker, txn, xsdCol, "addressbook.xsd", addressBook.resolve("addressbook.xsd"));
            storeDocument(broker, txn, xsdCol, "catalog.xml", addressBook.resolve("catalog.xml"));

            final Collection validationCol = broker.getCollection(VALIDATION_HOME_COLLECTION_URI);
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

            final Collection validationCol = broker.getOrCreateCollection(txn, VALIDATION_HOME_COLLECTION_URI);
            broker.removeCollection(txn, validationCol);

            transact.commit(txn);
        }
    }
}
