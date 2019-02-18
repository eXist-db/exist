/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2016 The eXist Project
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
 */
package org.exist.backup;

import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.backup.restore.listener.LogRestoreListener;
import org.exist.backup.restore.listener.RestoreListener;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;

import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static org.exist.test.TestConstants.TEST_COLLECTION_URI;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class SystemExportFiltersTest {

    public boolean direct = true;

    private static String COLLECTION_CONFIG =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "    <index>" +
            "    </index>" +
            "</collection>";

    private static XmldbURI doc01uri = TEST_COLLECTION_URI.append("test1.xml");
    private static XmldbURI doc02uri = TEST_COLLECTION_URI.append("test2.xml");
    private static XmldbURI doc03uri = TEST_COLLECTION_URI.append("test3.xml");
    private static XmldbURI doc11uri = TEST_COLLECTION_URI.append("test.binary");
    
    private static String XML1 = "<test attr=\"test\"/>";
    private static String XML1_BACKUP = "<test attr=\"test\">test</test>";
    private static String XML2 =
        "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\"\n" +
        "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n" +
        "<html xmlns=\"http://www.w3.org/1999/xhtml\"></html>";
    private static String XML2_PROPER = 
        "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" " +
        "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n" +
        "<html xmlns=\"http://www.w3.org/1999/xhtml\"/>";


    private static String XML3 = "<!DOCTYPE html><html></html>";
    private static String XML3_PROPER = "<!DOCTYPE html>\n<html/>";

    private static String BINARY = "test";

    @Rule
    public final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, false);

    @Before
    public void startDB() throws DatabaseConfigurationException, EXistException, PermissionDeniedException, IOException, SAXException, CollectionConfigurationException, LockException, ClassNotFoundException, InstantiationException, XMLDBException, IllegalAccessException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        pool.getPluginsManager().addPlugin("org.exist.storage.md.Plugin");

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn txn = pool.getTransactionManager().beginTransaction()) {

            final Collection test = createCollection(txn, broker, TEST_COLLECTION_URI);

            final CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(txn, broker, test, COLLECTION_CONFIG);

            storeXMLDocument(txn, broker, test, doc01uri.lastSegment(), XML1);
            storeXMLDocument(txn, broker, test, doc02uri.lastSegment(), XML2);
            storeXMLDocument(txn, broker, test, doc03uri.lastSegment(), XML3);

            test.addBinaryResource(txn, broker, doc11uri.lastSegment(), BINARY.getBytes(), null);

            txn.commit();
        }

        rundb();
    }

    private void rundb() throws ClassNotFoundException, XMLDBException, IllegalAccessException, InstantiationException {
        final Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        final Database database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
    }

    @After
    public void cleanup() throws PermissionDeniedException, IOException, TriggerException, EXistException, LockException {
        TestUtils.cleanupDB();
    }

    @Test
    public void exportImport() throws Exception {
        Path file;
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            List<String> filters = new ArrayList<>();
            filters.add(FilterForBackup.class.getName());

            broker.getConfiguration().setProperty(SystemExport.CONFIG_FILTERS, filters);

            final Collection test = broker.getCollection(TEST_COLLECTION_URI);
            assertNotNull(test);

            final SystemExport sysexport = new SystemExport(broker, null, null, direct);
            file = sysexport.export("backup", false, false, null);
        }

        TestUtils.cleanupDB();

        final SystemImport restore = new SystemImport(pool);
        final RestoreListener listener = new LogRestoreListener();
        restore.restore(listener, "admin", "", "", file, "xmldb:exist://");

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            final Collection test = broker.getCollection(TEST_COLLECTION_URI);
            assertNotNull(test);

            DocumentImpl doc = getDoc(broker, test, doc01uri.lastSegment());
            assertEquals(XML1_BACKUP, serializer(broker, doc));

            doc = getDoc(broker, test, doc02uri.lastSegment());
            assertEquals(XML2_PROPER, serializer(broker, doc));

            doc = getDoc(broker, test, doc03uri.lastSegment());
            assertEquals(XML3_PROPER, serializer(broker, doc));
        }
    }

    private DocumentImpl getDoc(final DBBroker broker, final Collection col, final XmldbURI uri) throws PermissionDeniedException {
        final DocumentImpl doc = col.getDocument(broker, uri);
        assertNotNull(doc);

        return doc;
    }

    private final static Properties contentsOutputProps = new Properties();
    static {
        contentsOutputProps.setProperty( OutputKeys.INDENT, "yes" );
        contentsOutputProps.setProperty( EXistOutputKeys.OUTPUT_DOCTYPE, "yes" );
    }

    private String serializer(final DBBroker broker, final DocumentImpl document) throws SAXException, IOException {
        final Serializer serializer = broker.getSerializer();
        serializer.setUser(broker.getCurrentSubject());
        serializer.setProperties(contentsOutputProps);
        return serializer.serialize(document);
    }

    private Collection createCollection(Txn txn, DBBroker broker, XmldbURI uri) throws PermissionDeniedException, IOException, TriggerException {
        final Collection col = broker.getOrCreateCollection(txn, uri);
        assertNotNull(col);
        broker.saveCollection(txn, col);

        return col;
    }

    private DocumentImpl storeXMLDocument(Txn txn, DBBroker broker, Collection col, XmldbURI name, String data) throws LockException, SAXException, PermissionDeniedException, EXistException, IOException {
        IndexInfo info = col.validateXMLResource(txn, broker, name, data);
        assertNotNull(info);

        col.store(txn, broker, info, data);

        return info.getDocument();
    }
}
