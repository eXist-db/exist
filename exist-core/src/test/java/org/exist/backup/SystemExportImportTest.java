/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;

import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.backup.restore.listener.LogRestoreListener;
import org.exist.backup.restore.listener.RestoreListener;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.AuthenticationException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.Txn;
import static org.exist.test.TestConstants.TEST_COLLECTION_URI;

import org.exist.test.ExistEmbeddedServer;
import org.exist.util.FileUtils;
import org.exist.util.LockException;
import org.exist.util.io.InputStreamUtil;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.functions.util.BinaryDoc;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.xml.sax.SAXException;
import org.xmldb.api.base.XMLDBException;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
@RunWith(Parameterized.class)
public class SystemExportImportTest {

    @Parameters(name = "{0} zip:{2}")
    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"direct", true, false},
                {"non-direct", false, false},
                {"direct", true, true},
                {"non-direct", false, true}
        });
    }

    @Parameter
    public String apiName;

    @Parameter(value = 1)
    public boolean direct;

    @Parameter(value = 2)
    public boolean zip;

    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static String COLLECTION_CONFIG =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
        	"	<index>" +
            "	</index>" +
        	"</collection>";

    private static XmldbURI doc01uri = TEST_COLLECTION_URI.append("test1.xml");
    private static XmldbURI doc02uri = TEST_COLLECTION_URI.append("test2.xml");
    private static XmldbURI doc03uri = TEST_COLLECTION_URI.append("test3.xml");
    private static XmldbURI doc11uri = TEST_COLLECTION_URI.append("test.binary");
    
    private static String XML1 = "<test attr=\"test\"/>";
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

    @Test
    public void exportImport() throws EXistException, IOException, PermissionDeniedException, SAXException, ParserConfigurationException, AuthenticationException, URISyntaxException, XMLDBException {
        Path file;
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            final Collection test = broker.getCollection(TEST_COLLECTION_URI);
            assertNotNull(test);

            final SystemExport sysexport = new SystemExport(broker, transaction, null, null, direct);
            final String backupDir = temporaryFolder.newFolder().getAbsolutePath();
            file = sysexport.export(backupDir, false, zip, null);

            transaction.commit();
        }

        clean();

        final SystemImport restore = new SystemImport(pool);
        final RestoreListener listener = new LogRestoreListener();
        restore.restore(TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD, null, file, listener);

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            final Collection test = broker.getCollection(TEST_COLLECTION_URI);
            assertNotNull(test);

            DocumentImpl doc = getDoc(broker, test, doc01uri.lastSegment());
            assertEquals(XML1, serializer(broker, doc));

            doc = getDoc(broker, test, doc02uri.lastSegment());
            assertEquals(XML2_PROPER, serializer(broker, doc));

            doc = getDoc(broker, test, doc03uri.lastSegment());
            assertEquals(XML3_PROPER, serializer(broker, doc));

            doc = getDoc(broker, test, doc11uri.lastSegment());
            assertTrue(doc instanceof BinaryDocument);
            try (final InputStream is = broker.getBinaryResource(transaction, ((BinaryDocument)doc))) {
                assertEquals(BINARY, InputStreamUtil.readString(is, UTF_8));
            }

            transaction.commit();
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
	
	private String serializer(final DBBroker broker, final DocumentImpl document) throws SAXException {
		final Serializer serializer = broker.getSerializer();
		serializer.setUser(broker.getCurrentSubject());
		serializer.setProperties(contentsOutputProps);
		return serializer.serialize(document);
	}

    private void clean() throws PermissionDeniedException, IOException, TriggerException, EXistException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            final Collection test = broker.getCollection(TEST_COLLECTION_URI);
            if(test != null) {
                broker.removeCollection(transaction, test);
            }

            transaction.commit();
        }
    }

	@BeforeClass
    public static void setup() throws EXistException, PermissionDeniedException, IOException, SAXException, CollectionConfigurationException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            final Collection test = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI);
            assertNotNull(test);
            broker.saveCollection(transaction, test);

            final CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(transaction, broker, test, COLLECTION_CONFIG);

            IndexInfo info = test.validateXMLResource(transaction, broker, doc01uri.lastSegment(), XML1);
            assertNotNull(info);
            test.store(transaction, broker, info, XML1);

            info = test.validateXMLResource(transaction, broker, doc02uri.lastSegment(), XML2);
            assertNotNull(info);
            test.store(transaction, broker, info, XML2);

            info = test.validateXMLResource(transaction, broker, doc03uri.lastSegment(), XML3);
            assertNotNull(info);
            test.store(transaction, broker, info, XML3);

            test.addBinaryResource(transaction, broker, doc11uri.lastSegment(), BINARY.getBytes(), null);

            transaction.commit();
        }
    }
}
