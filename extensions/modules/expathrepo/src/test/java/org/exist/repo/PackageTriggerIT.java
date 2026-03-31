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
package org.exist.repo;

import org.exist.EXistException;
import org.exist.Version;
import org.exist.collections.Collection;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.ManagedCollectionLock;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.InputStreamSupplierInputSource;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.*;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.exist.collections.CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE;

/**
 * Integration test: requires the XAR from expathrepo-trigger-test (built in package phase).
 * Runs in Failsafe so the full build has completed and the XAR resource is available.
 */
public class PackageTriggerIT {

    static final String xarFile = "exist-expathrepo-trigger-test-" + Version.getVersion() + ".xar";

    private static java.util.function.Supplier<InputStream> resolveXarSupplier() {
        // 1) Try from test classpath (added via maven-resources-plugin to target/generated-test-resources)
        if (PackageTriggerIT.class.getResource("/" + xarFile) != null ||
                PackageTriggerIT.class.getClassLoader().getResource(xarFile) != null) {
            return () -> PackageTriggerIT.class.getResourceAsStream("/" + xarFile);
        }
        // 2) Try from sibling module build output
        final Path relPath = Path.of("extensions/modules/expathrepo/expathrepo-trigger-test/target", xarFile);
        if (Files.exists(relPath)) {
            return () -> {
                try {
                    return Files.newInputStream(relPath);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            };
        }
        // 3) Try from current module generated-test-resources (in case direct run already copied it)
        final Path generated = Path.of("extensions/modules/expathrepo/target/generated-test-resources", xarFile);
        if (Files.exists(generated)) {
            return () -> {
                try {
                    return Files.newInputStream(generated);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            };
        }
        return null;
    }
    static final XmldbURI triggerTestCollection = XmldbURI.create("/db");
    static final XmldbURI xarUri = triggerTestCollection.append(xarFile);

    @ClassRule
    public static ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(false, true);

    @BeforeClass
    public static void setup() throws PermissionDeniedException, SAXException, EXistException, IOException, LockException, XPathException {

        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();

        // Create a collection to test the trigger in
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
             final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

            final Collection collection = broker.getOrCreateCollection(transaction, triggerTestCollection);
            broker.saveCollection(transaction, collection);
            transaction.commit();
        }

        // Resolve XAR supplier (skip test class if not available in non-Maven runs)
        final java.util.function.Supplier<InputStream> xarSupplier = resolveXarSupplier();
        Assume.assumeTrue("XAR '" + xarFile + "' not found on classpath or filesystem. Run via Maven to generate it.", xarSupplier != null);

        // Store XAR in database
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
             final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

            try (final ManagedCollectionLock collectionLock = brokerPool.getLockManager().acquireCollectionWriteLock(xarUri.removeLastSegment())) {
                final Collection collection = broker.getOrCreateCollection(transaction, xarUri.removeLastSegment());

                broker.storeDocument(transaction, xarUri.lastSegment(), new InputStreamSupplierInputSource(xarSupplier::get), MimeType.EXPATH_PKG_TYPE, collection);
                broker.saveCollection(transaction, collection);
            }

            transaction.commit();
        }

        // Install and deploy XAR
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = brokerPool.getXQueryService();
            final Sequence result = xquery.execute(broker, "repo:install-and-deploy-from-db('/db/" + xarFile + "')", null);
            Assert.assertEquals(1, result.getItemCount());
        }

        // Store collection.xconf in newly created collection under /db/system/config
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = brokerPool.getXQueryService();
            final Sequence result = xquery.execute(broker, "xmldb:create-collection('/db/system/config/db','trigger-test'), " +
                    "xmldb:store('/db/system/config/db/trigger-test', '" + DEFAULT_COLLECTION_CONFIG_FILE + "', " +
                    "<collection xmlns=\"http://exist-db.org/collection-config/1.0\"><triggers><trigger class=\"org.exist.repo.ExampleTrigger\"/></triggers></collection>)", null);
            Assert.assertEquals(2, result.getItemCount());
        }

    }


    @Test
    public void checkTriggerFires() throws EXistException, PermissionDeniedException, XPathException {

        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();

        // Create collection and store document to fire trigger
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = brokerPool.getXQueryService();
            final Sequence result = xquery.execute(broker, "xmldb:create-collection('/db','trigger-test')", null);
            Assert.assertEquals(1, result.getItemCount());
        }

        // Create collection and store document to fire trigger
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = brokerPool.getXQueryService();
            final Sequence result = xquery.execute(broker, "xmldb:store('/db/trigger-test', 'test.xml', <a>b</a>)", null);
            Assert.assertEquals(1, result.getItemCount());
        }

        // Verify two documents are now in collection
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = brokerPool.getXQueryService();
            final Sequence result = xquery.execute(broker, "xmldb:get-child-resources('/db/trigger-test')", null);
            Assert.assertEquals("After trigger execution two documents should be in the collection.", 2, result.getItemCount());
        }

    }
}
