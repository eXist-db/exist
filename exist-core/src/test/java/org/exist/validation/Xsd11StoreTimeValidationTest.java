/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.validation;

import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.exist.collections.Collection;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.dom.persistent.LockedDocument;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.TestUtils.ADMIN_DB_PWD;
import static org.exist.TestUtils.ADMIN_DB_USER;
import static org.exist.TestUtils.GUEST_DB_USER;
import static org.exist.util.PropertiesBuilder.propertiesBuilder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * At-store-time validation (the path {@code org.exist.collections.MutableCollection} drives for
 * {@code <validation mode="auto"/"yes">}) must work for a genuinely XSD-1.1-only, user-authored
 * schema -- not just the W3C XSD 1.1 meta-schema special case the original regression (storing a
 * {@code .xsd} document itself) surfaced. The bundled Xerces fork's XSD 1.1 support is only wired
 * into the JAXP {@code SchemaFactory}/{@code Validator} API, never into the default
 * dynamic-discovery SAX pipeline, so both of these must be detected and routed up front:
 *
 * <ol>
 *   <li>Storing the schema document itself (root element in the W3C XML Schema namespace, no
 *   {@code schemaLocation} hint at all) -- exercises the namespace-resolution path.</li>
 *   <li>Storing an instance that references that schema via {@code
 *   xsi:noNamespaceSchemaLocation} -- exercises the schemaLocation-hint path shared with {@code
 *   validation:jaxp()} (see {@link Xsd11SchemaDetection}).</li>
 * </ol>
 *
 * <p>Inserts via {@code xmldb:exist://} URL upload ({@link TestTools#insertDocumentToURL}, the
 * same mechanism {@link DatabaseInsertResourcesWithValidationTest} uses), not the XQuery {@code
 * xmldb:store()} function: a document constructed/serialized inline within an XQuery has no
 * meaningful base URI by the time it reaches {@code MutableCollection} (confirmed empirically --
 * its {@code InputSource} system ID is {@code null}), so a relative or same-origin-absolute
 * {@code schemaLocation} hint could never be resolved that way regardless of this fix. The
 * URL-upload path writes through a real temp file ({@code FileInputSource}), which does carry a
 * real {@code file:} system ID -- the same precondition relative/same-origin {@code
 * schemaLocation} resolution already needed for any pre-existing (XSD 1.0, DTD) store-time
 * validation against a relative hint.</p>
 *
 * @see <a href="https://github.com/eXist-db/exist/issues/5541">#5541</a>
 */
public class Xsd11StoreTimeValidationTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(
            propertiesBuilder()
                    .set(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "yes")
                    .build(),
            true,
            true);

    private static final String TEST_COLLECTION_URI = "/db/xsd11storetimevalidation";

    private static final String XSD_1_1_ONLY_SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning" vc:minVersion="1.1">
                <xs:element name="root">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="value1" type="xs:integer"/>
                            <xs:element name="value2" type="xs:integer"/>
                        </xs:sequence>
                        <xs:assert test="value2 gt value1"/>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
            """;

    private static final String INSTANCE_TEMPLATE = """
            <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
            xsi:noNamespaceSchemaLocation="%s">
                <value1>%d</value1>
                <value2>%d</value2>
            </root>
            """;

    private static final String COMMENT_TEXT = " a comment ";

    private static final String INSTANCE_WITH_COMMENT_AND_CDATA_TEMPLATE = """
            <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
            xsi:noNamespaceSchemaLocation="%s"><!--%s\
            --><value1><![CDATA[%d]]></value1>
                <value2>%d</value2>
            </root>
            """;

    private static final String XCONF_YES = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <validation mode="yes"/>
            </collection>
            """;

    @BeforeClass
    public static void createTestCollection() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().authenticate(ADMIN_DB_USER, ADMIN_DB_PWD)));
             final Txn txn = transact.beginTransaction()) {
            final Collection testCollection = broker.getOrCreateCollection(txn, XmldbURI.create(TEST_COLLECTION_URI));
            testCollection.getPermissions().setOwner(GUEST_DB_USER);
            broker.saveCollection(txn, testCollection);

            final Collection configCollection = broker.getOrCreateCollection(txn,
                    XmldbURI.create("/db/system/config" + TEST_COLLECTION_URI));
            configCollection.getPermissions().setOwner(GUEST_DB_USER);
            broker.saveCollection(txn, configCollection);

            transact.commit(txn);
        }

        // A leading-CollectionConfiguration-without-an-explicit-<validation> element resolves to
        // VALIDATION_SETTING.UNKNOWN ("maybe() == false"), which *disables* validation regardless
        // of any global validation.mode default -- an explicit collection.xconf is required.
        TestTools.insertDocumentToURL(
                new UnsynchronizedByteArrayInputStream(XCONF_YES.getBytes(UTF_8)),
                "xmldb:exist:///db/system/config" + TEST_COLLECTION_URI + "/collection.xconf");
    }

    @AfterClass
    public static void removeTestCollection() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().authenticate(ADMIN_DB_USER, ADMIN_DB_PWD)));
             final Txn txn = transact.beginTransaction()) {
            final Collection testCollection = broker.getOrCreateCollection(txn, XmldbURI.create(TEST_COLLECTION_URI));
            broker.removeCollection(txn, testCollection);
            transact.commit(txn);
        }
    }

    @Test
    public void xsd11SchemaDocumentItselfStoresUnderValidation() {
        // Storing the schema document itself validates it against the W3C meta-schema, purely by
        // namespace (no schemaLocation hint at all) -- exercises resolveXsd11SchemaForNamespace().
        try {
            TestTools.insertDocumentToURL(
                    new UnsynchronizedByteArrayInputStream(XSD_1_1_ONLY_SCHEMA.getBytes(UTF_8)),
                    "xmldb:exist://" + TEST_COLLECTION_URI + "/schema-self.xsd");
        } catch (final IOException e) {
            fail("storing XSD 1.1 schema should not throw: " + e.getMessage());
        }
    }

    @Test
    public void conformingInstanceAgainstXsd11SchemaViaLocationHintStores() throws Exception {
        final Path schema = writeTempSchema("xsd11store-conform-test");
        try {
            // The instance's own xsi:noNamespaceSchemaLocation hint resolves to an XSD 1.1-only
            // schema -- exercises Xsd11SchemaDetection.detectXsd11ViaSchemaLocation() and the
            // dynamic discovery XSD 1.1 Validator.
            final String instance = INSTANCE_TEMPLATE.formatted(schema.toUri(), 1, 2);
            TestTools.insertDocumentToURL(
                    new UnsynchronizedByteArrayInputStream(instance.getBytes(UTF_8)),
                    "xmldb:exist://" + TEST_COLLECTION_URI + "/instance-conform.xml");
        } catch (final Exception e) {
            fail("conforming instance should store without exception: " + e.getMessage());
        } finally {
            Files.deleteIfExists(schema);
            Files.deleteIfExists(schema.getParent());
        }
    }

    @Test
    public void commentAndCdataSurviveXsd11StoreTimeValidation() throws Exception {
        // The XSD 1.1 store-time path drives a ValidatorHandler via xmlReader1.parse(source)
        // rather than Schema.newValidator()'s validate(Source, SAXResult) precisely so that
        // comments/CDATA are not silently dropped -- a SAXResult has no lexical-handler hook.
        // Confirms that fix concretely, not just "store doesn't throw".
        final Path schema = writeTempSchema("xsd11store-lexical-test");
        try {
            final String instance = INSTANCE_WITH_COMMENT_AND_CDATA_TEMPLATE.formatted(schema.toUri(), COMMENT_TEXT, 1, 2);
            TestTools.insertDocumentToURL(
                    new UnsynchronizedByteArrayInputStream(instance.getBytes(UTF_8)),
                    "xmldb:exist://" + TEST_COLLECTION_URI + "/instance-lexical.xml");

            final BrokerPool pool = existEmbeddedServer.getBrokerPool();
            try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().authenticate(ADMIN_DB_USER, ADMIN_DB_PWD)));
                 final LockedDocument lockedDocument = broker.getXMLResource(
                         XmldbURI.create(TEST_COLLECTION_URI + "/instance-lexical.xml"), Lock.LockMode.READ_LOCK)) {
                assertNotNull("stored document should be retrievable", lockedDocument);

                final Document document = lockedDocument.getDocument();
                final Element root = document.getDocumentElement();
                final NodeList rootChildren = root.getChildNodes();

                final Node commentNode = rootChildren.item(0);
                assertEquals("comment should survive store-time XSD 1.1 validation",
                        Node.COMMENT_NODE, commentNode.getNodeType());
                assertEquals(COMMENT_TEXT, commentNode.getNodeValue());

                final Node value1 = rootChildren.item(1);
                final Node value1Child = value1.getFirstChild();
                assertEquals("CDATA section should survive store-time XSD 1.1 validation as a CDATASection node, not plain text",
                        Node.CDATA_SECTION_NODE, value1Child.getNodeType());
                assertEquals("1", ((CDATASection) value1Child).getData());
            }
        } finally {
            Files.deleteIfExists(schema);
            Files.deleteIfExists(schema.getParent());
        }
    }

    @Test
    public void violatingInstanceAgainstXsd11SchemaViaLocationHintFails() throws Exception {
        final Path schema = writeTempSchema("xsd11store-violate-test");
        try {
            final String instance = INSTANCE_TEMPLATE.formatted(schema.toUri(), 2, 1);
            try {
                TestTools.insertDocumentToURL(
                        new UnsynchronizedByteArrayInputStream(instance.getBytes(UTF_8)),
                        "xmldb:exist://" + TEST_COLLECTION_URI + "/instance-violate.xml");
                fail("should have failed: value2 is not greater than value1");
            } catch (final IOException ex) {
                final String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                assertTrue("expected an xs:assert violation message, got: " + msg,
                        msg.contains("cvc-assertion") || msg.contains("value2 gt value1"));
            }
        } finally {
            Files.deleteIfExists(schema);
            Files.deleteIfExists(schema.getParent());
        }
    }

    private static Path writeTempSchema(final String tempDirPrefix) throws Exception {
        final Path tempDir = Files.createTempDirectory(tempDirPrefix);
        final Path schema = tempDir.resolve("schema.xsd");
        Files.writeString(schema, XSD_1_1_ONLY_SCHEMA, UTF_8);
        return schema;
    }
}
