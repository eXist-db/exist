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
package org.exist.collections;

import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.validation.TestTools;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.util.PropertiesBuilder.propertiesBuilder;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The validation mode of the server applies to a collection unless the configuration of the collection sets one
 * (#7075). That a collection's own mode is applied is covered by {@link org.exist.validation.CollectionConfigurationValidationModeTest},
 * which runs with the default server mode; this class runs with a validating server.
 */
class CollectionValidationModeTest {

    @RegisterExtension
    static final ExistEmbeddedServer SERVER_VALIDATING = new ExistEmbeddedServer(
            propertiesBuilder().set(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "auto").build(), true, true);

    private static final String NS = "xmlns=\"http://exist-db.org/collection-config/1.0\"";

    /** Not valid: the element b is not declared. */
    private static final String INVALID = "<!DOCTYPE a [<!ELEMENT a (#PCDATA)>]><a><b/></a>";

    private static void collection(final String path, final String configuration) throws Exception {
        final BrokerPool pool = SERVER_VALIDATING.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn txn = pool.getTransactionManager().beginTransaction()) {
            final Collection collection = broker.getOrCreateCollection(txn, XmldbURI.create(path));
            collection.getPermissions().setMode(0777);
            broker.saveCollection(txn, collection);
            if (configuration != null) {
                pool.getConfigurationManager().addConfiguration(txn, broker, collection, configuration);
            }
            txn.commit();
        }
    }

    private static void store(final String path) throws Exception {
        TestTools.insertDocumentToURL(new UnsynchronizedByteArrayInputStream(INVALID.getBytes(UTF_8)),
                "xmldb:exist://" + path + "/invalid.xml");
    }

    @Test
    void aCollectionWithoutAConfigurationValidatesAsTheServerDoes() throws Exception {
        collection("/db/validation-mode-none", null);
        assertThrows(Exception.class, () -> store("/db/validation-mode-none"));
    }

    @Test
    void aConfigurationWithoutAValidationModeValidatesAsTheServerDoes() throws Exception {
        collection("/db/validation-mode-index-only", "<collection " + NS + "><index/></collection>");
        assertThrows(Exception.class, () -> store("/db/validation-mode-index-only"));
    }

    @Test
    void aConfigurationThatDisablesValidationWinsOverTheServer() throws Exception {
        collection("/db/validation-mode-no", "<collection " + NS + "><validation mode=\"no\"/></collection>");
        assertDoesNotThrow(() -> store("/db/validation-mode-no"));
    }
}
