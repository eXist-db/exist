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
package org.exist.test;

import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.EXistCollection;
import org.exist.xmldb.EXistXQueryService;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.CompiledExpression;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import javax.annotation.Nullable;
import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exist embedded XML:DB Server JUnit 5 extension.
 * <p>
 * Use with {@code @RegisterExtension static final} for class-level (once per test
 * class) lifecycle, or {@code @RegisterExtension final} (non-static) for per-test
 * lifecycle.
 */
public class ExistXmldbEmbeddedServer implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

    private final boolean asGuest;
    private final ExistEmbeddedServer existEmbeddedServer;

    private Database database = null;
    private Collection root = null;
    private EXistXQueryService xpathQueryService = null;
    private boolean startedByBeforeAll = false;

    public ExistXmldbEmbeddedServer() {
        this(false, false);
    }

    /**
     * @param asGuest Use the guest account, default is the admin account
     */
    public ExistXmldbEmbeddedServer(final boolean asGuest) {
        this(asGuest, false);
    }

    /**
     * @param asGuest Use the guest account, default is the admin account
     * @param disableAutoDeploy Whether auto-deployment of XARs should be disabled
     */
    public ExistXmldbEmbeddedServer(final boolean asGuest, final boolean disableAutoDeploy) {
        this(asGuest, disableAutoDeploy, false);
    }

    /**
     * @param asGuest Use the guest account, default is the admin account
     * @param disableAutoDeploy Whether auto-deployment of XARs should be disabled
     * @param useTemporaryStorage Whether the data and journal folder should use temporary storage
     */
    public ExistXmldbEmbeddedServer(final boolean asGuest, final boolean disableAutoDeploy, final boolean useTemporaryStorage) {
        this.existEmbeddedServer = new ExistEmbeddedServer(disableAutoDeploy, useTemporaryStorage);
        this.asGuest = asGuest;
    }

    /**
     * @param asGuest Use the guest account, default is the admin account
     * @param disableAutoDeploy Whether auto-deployment of XARs should be disabled
     * @param useTemporaryStorage Whether the data and journal folder should use temporary storage
     * @param configFile path to eXist-db's conf.xml configuration file
     */
    public ExistXmldbEmbeddedServer(final boolean asGuest, final boolean disableAutoDeploy, final boolean useTemporaryStorage, @Nullable final Path configFile) {
        this.existEmbeddedServer = new ExistEmbeddedServer(null, configFile, null, disableAutoDeploy, useTemporaryStorage);
        this.asGuest = asGuest;
    }

    protected void before() throws ReflectiveOperationException, XMLDBException {
        startDb();
    }

    @Override
    public void beforeAll(final ExtensionContext context) throws Exception {
        before();
        this.startedByBeforeAll = true;
    }

    @Override
    public void beforeEach(final ExtensionContext context) throws Exception {
        // a static @RegisterExtension field gets beforeEach/afterEach invoked per-test in
        // addition to beforeAll/afterAll once per class (JUnit5 does not distinguish by the
        // field's static/instance modifier, only by which callback interfaces are implemented),
        // so skip here if the class-level lifecycle already started the server.
        if (!startedByBeforeAll) {
            before();
        }
    }

    private void startDb() throws ReflectiveOperationException, XMLDBException {
        try {
            existEmbeddedServer.startDb();
        } catch (final DatabaseConfigurationException | EXistException | IOException e) {
            throw new XMLDBException(ErrorCodes.INVALID_DATABASE, e);
        }
        startXmldb();
    }

    private void startXmldb() throws ReflectiveOperationException, XMLDBException {
        if (database == null) {
            // initialize driver
            final Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
            database = (Database) cl.getDeclaredConstructor().newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
            if (asGuest) {
                root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, TestUtils.GUEST_DB_USER, TestUtils.GUEST_DB_PWD);
            } else {
                root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
            }
            xpathQueryService = root.getService(EXistXQueryService.class);
        } else {
            throw new IllegalStateException("ExistXmldbEmbeddedServer already running");
        }
    }

    public void restart() throws ReflectiveOperationException, XMLDBException {
        restart(false);
    }

    public void restart(final boolean clearTemporaryStorage) throws ReflectiveOperationException, XMLDBException {
        stopDb(clearTemporaryStorage);
        startDb();
    }

    protected void after() {
        stopDb(true);
    }

    @Override
    public void afterAll(final ExtensionContext context) {
        after();
        this.startedByBeforeAll = false;
    }

    @Override
    public void afterEach(final ExtensionContext context) {
        if (!startedByBeforeAll) {
            after();
        }
    }

    private void stopDb(final boolean clearTemporaryStorage) {
        try {
            stopXmlDb();
        } catch (final XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        existEmbeddedServer.stopDb(clearTemporaryStorage);
    }

    private void stopXmlDb() throws XMLDBException {
        if (database != null) {
            root.close();
            DatabaseManager.deregisterDatabase(database);

            // clear instance variables
            xpathQueryService = null;
            root = null;
            database = null;
        } else {
            throw new IllegalStateException("ExistXmldbEmbeddedServer already stopped");
        }
    }


    public ResourceSet executeQuery(final String query) throws XMLDBException {
        final CompiledExpression compiledQuery = xpathQueryService.compile(query);
        return xpathQueryService.execute(compiledQuery);
    }

    public ResourceSet executeQuery(final String query, final Map<String, Object> externalVariables)
            throws XMLDBException {
        for (final Map.Entry<String, Object> externalVariable : externalVariables.entrySet()) {
            xpathQueryService.declareVariable(externalVariable.getKey(), externalVariable.getValue());
        }
        final CompiledExpression compiledQuery = xpathQueryService.compile(query);
        final ResourceSet result = xpathQueryService.execute(compiledQuery);
        xpathQueryService.clearVariables();
        return result;
    }

    public String executeOneValue(final String query) throws XMLDBException {
        final ResourceSet results = executeQuery(query);
        assertEquals(1, results.getSize());
        return results.getResource(0).getContent().toString();
    }

    public Collection createCollection(final Collection collection, final String collectionName) throws XMLDBException {
        final CollectionManagementService collectionManagementService =
                collection.getService(CollectionManagementService.class);
        Collection newCollection = collection.getChildCollection(collectionName);
        if (newCollection == null) {
            collectionManagementService.createCollection(collectionName);
        }

        final XmldbURI uri = XmldbURI.LOCAL_DB_URI.resolveCollectionPath(((EXistCollection) collection).getPathURI().append(collectionName));
        if (asGuest) {
            newCollection = DatabaseManager.getCollection(uri.toString(), TestUtils.GUEST_DB_USER, TestUtils.GUEST_DB_PWD);
        } else {
            newCollection = DatabaseManager.getCollection(uri.toString(), TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        }

        return newCollection;
    }

    public static void storeResource(final Collection collection, final String documentName, final byte[] content)
            throws XMLDBException {
        final MimeType mime = MimeTable.getInstance().getContentTypeFor(documentName);
        final Class<? extends Resource> type = mime.isXMLType() ? XMLResource.class : BinaryResource.class;
        try (final Resource resource = collection.createResource(documentName, type)) {
            resource.setContent(content);
            collection.storeResource(resource);
        }
    }

    public static String getXMLResource(final Collection collection, final String resource) throws XMLDBException {
        collection.setProperty(OutputKeys.INDENT, "yes");
        collection.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "no");
        collection.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "yes");
        final XMLResource res = (XMLResource) collection.getResource(resource);
        return res.getContent().toString();
    }

    public Collection getRoot() {
        return root;
    }
}
