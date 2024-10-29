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
import org.junit.rules.ExternalResource;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import javax.annotation.Nullable;
import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Exist embedded XML:DB Server Rule for JUnit.
 */
public class ExistXmldbEmbeddedServer extends ExternalResource {

    private final boolean asGuest;
    private final ExistEmbeddedServer existEmbeddedServer;

    private Database database = null;
    private Collection root = null;
    private EXistXQueryService xpathQueryService = null;

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

    @Override
    protected void before() throws Throwable {
        startDb();
        super.before();
    }

    private void startDb() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
        try {
            existEmbeddedServer.startDb();
        } catch (final DatabaseConfigurationException | EXistException | IOException e) {
            throw new XMLDBException(ErrorCodes.INVALID_DATABASE, e);
        }
        startXmldb();
    }

    private void startXmldb() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
        if (database == null) {
            // initialize driver
            final Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
            database = (Database) cl.newInstance();
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

    public void restart() throws ClassNotFoundException, InstantiationException, XMLDBException, IllegalAccessException {
        restart(false);
    }

    public void restart(final boolean clearTemporaryStorage) throws ClassNotFoundException, InstantiationException, XMLDBException, IllegalAccessException {
        stopDb(clearTemporaryStorage);
        startDb();
    }

    @Override
    protected void after() {
        stopDb(true);
        super.after();
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
