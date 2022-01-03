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
package org.exist.backup;

import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;
import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.DatabaseImpl;
import org.exist.xmldb.XmldbURI;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.XMLDBException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

/**
 * Test that creates a deep and Wide Collection hierarchy
 * and then performs a backup and ensures that the backup
 * completed.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class DeepEmbeddedBackupRestoreTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @ClassRule
    public static final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static final String EOL = "\n";
    private static final long XXHASH64_SEED = 0x79742bc8;

    private final XXHashFactory xxHashFactory = XXHashFactory.fastestJavaInstance();
    private final XXHash64 hash64 = xxHashFactory.hash64();

    @BeforeClass
    public static void registerXmldbDatabaseDriver() throws XMLDBException {
        final DatabaseImpl databaseImpl = new DatabaseImpl();
        DatabaseManager.registerDatabase(databaseImpl);
    }

    @Test
    public void backupThenRestore() throws IOException, XMLDBException, SAXException, LockException, PermissionDeniedException, EXistException {
        // create some collections and documents in the database
        final CollectionsAndDocuments collectionsAndDocs = createHierarchy(XmldbURI.create("/db/exist-EmbeddedBackupRestoreWithAppsTest"), 20, 20, 20, 20);
        assertFalse(collectionsAndDocs.collectionUris.isEmpty());
        assertFalse(collectionsAndDocs.documentInfos.isEmpty());

        final Path backupDir = temporaryFolder.newFolder("exist-EmbeddedBackupRestoreWithAppsTest").toPath();
        final Properties backupProperties = new Properties();

        final Backup backup = new Backup(
                TestUtils.ADMIN_DB_USER,
                TestUtils.ADMIN_DB_PWD,
                backupDir,
                XmldbURI.EMBEDDED_SERVER_URI,
                backupProperties,
                false
        );
        backup.backup(false, null);

        // check all collections were backed up
        for (final XmldbURI collectionUri : collectionsAndDocs.collectionUris) {
            final Path collectionPath = backupDir.resolve(collectionUri.getCollectionPath().substring(1));
            assertTrue(Files.exists(collectionPath));
            assertTrue(Files.isDirectory(collectionPath));
        }

        // check all documents were backed up
        for (final ResourceInfo documentInfo : collectionsAndDocs.documentInfos) {
            final Path documentPath = backupDir.resolve(documentInfo.uri.toString().substring(1));
            assertTrue(Files.exists(documentPath));
            assertTrue(Files.isRegularFile(documentPath));

            final byte[] documentData = Files.readAllBytes(documentPath);
            final long documentHash = hash64.hash(documentData, 0, documentData.length, XXHASH64_SEED);
            assertEquals("Expected hash '" + documentInfo.hash + "' for document '" + documentPath.toAbsolutePath() + "' but found '" + documentHash + "'", documentInfo.hash, documentHash);
        }
    }

    private CollectionsAndDocuments createHierarchy(final XmldbURI baseCollectionUri, final int depth, final int maxWidth, final int xmlDocsPerCollection, final int binDocsPerCollection) throws EXistException, PermissionDeniedException, IOException, SAXException, LockException {
        final List<XmldbURI> collectionUris = new ArrayList<>();
        final List<ResourceInfo> documentInfos = new ArrayList<>();

        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        try (final Txn transaction = brokerPool.getTransactionManager().beginTransaction();
             final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()))) {

            try (final Collection baseCollection = broker.getOrCreateCollection(transaction, baseCollectionUri)) {

                collectionUris.add(baseCollectionUri);

                final Random random = new Random();
                XmldbURI subCollectionUri = baseCollectionUri;
                for (int d = 0; d < depth; d++) {
                    subCollectionUri = subCollectionUri.append("sub_" + d);
                    try (final Collection subCollection = broker.getOrCreateCollection(transaction, subCollectionUri)) {

                        collectionUris.add(subCollectionUri);

                        final int width = random.nextInt(maxWidth - 1) + 1;
                        for (int w = 0; w < width; w++) {
                            final XmldbURI sibCollectionUri = subCollectionUri.append("sib_" + w);
                            try (final Collection sibCollection = broker.getOrCreateCollection(transaction, sibCollectionUri)) {

                                collectionUris.add(sibCollectionUri);

                                for (int x = 0; x < xmlDocsPerCollection; x++) {

                                    // store XML document
                                    final XmldbURI xmlName = XmldbURI.create("doc_" + x + ".xml");
                                    final String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + EOL + "<position id=\"" + x + "\" d=\"" + d + "\" w=\"" + w + "\"/>";
                                    broker.storeDocument(transaction, xmlName, new StringInputSource(xml), MimeType.XML_TYPE, sibCollection);

                                    final byte[] xmlData = xml.getBytes(UTF_8);
                                    final long xmlHash = hash64.hash(xmlData, 0, xmlData.length, XXHASH64_SEED);
                                    documentInfos.add(new ResourceInfo(sibCollectionUri.append(xmlName), xmlHash));

                                    // store Binary document
                                    final XmldbURI binName = XmldbURI.create("doc_" + x + ".bin");
                                    final String bin = x + ":" + d + ":" + w;
                                    broker.storeDocument(transaction, binName, new StringInputSource(bin.getBytes(UTF_8)), MimeType.BINARY_TYPE, sibCollection);

                                    final byte[] binData = bin.getBytes(UTF_8);
                                    final long binHash = hash64.hash(binData, 0, binData.length, XXHASH64_SEED);
                                    documentInfos.add(new ResourceInfo(sibCollectionUri.append(binName), binHash));
                                }
                            }
                        }
                    }
                }
            }

            transaction.commit();
        }

        return new CollectionsAndDocuments(collectionUris, documentInfos);
    }

    private static class CollectionsAndDocuments {
        final List<XmldbURI> collectionUris;
        final List<ResourceInfo> documentInfos;

        private CollectionsAndDocuments(final List<XmldbURI> collectionUris, final List<ResourceInfo> documentInfos) {
            this.collectionUris = collectionUris;
            this.documentInfos = documentInfos;
        }
    }

    private static class ResourceInfo {
        final XmldbURI uri;
        final long hash;

        private ResourceInfo(final XmldbURI uri, final long hash) {
            this.uri = uri;
            this.hash = hash;
        }
    }
}

