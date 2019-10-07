package org.exist.repo;

import org.apache.commons.io.IOUtils;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.CollectionTrigger;
import org.exist.collections.triggers.DocumentTrigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.collections.triggers.TriggerProxy;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.ManagedCollectionLock;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.util.io.FastByteArrayInputStream;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public class PackageTriggerTest {

    static final XmldbURI triggerTestCollection = XmldbURI.create("/db");
    static final XmldbURI xarUri = triggerTestCollection.append("triggertest-1.0-SNAPSHOT.xar");

    @ClassRule
    public static ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(false, true);

    @BeforeClass
    public static void setup() throws PermissionDeniedException, IOException, TriggerException, EXistException, IOException, LockException, XPathException {

        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();

        // Create a collection to test the trigger in
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
             final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

            final Collection collection = broker.getOrCreateCollection(transaction, triggerTestCollection);
            broker.saveCollection(transaction, collection);
            transaction.commit();
        }

        // Load XAR file
        InputStream resourceAsStream = PackageTriggerTest.class.getResourceAsStream("triggertest-1.0-SNAPSHOT.xar");
        Assert.assertNotNull(resourceAsStream);
        byte[] content = IOUtils.toByteArray(resourceAsStream);

        // Store XAR in database
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
             final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

            try (final ManagedCollectionLock collectionLock = brokerPool.getLockManager().acquireCollectionWriteLock(xarUri.removeLastSegment())) {
                final Collection collection = broker.getOrCreateCollection(transaction, xarUri.removeLastSegment());
                try (final InputStream is = new FastByteArrayInputStream(content)) {

                    collection.addBinaryResource(transaction, broker, xarUri.lastSegment(), is, "application/expath+xar", content.length);
                    broker.saveCollection(transaction, collection);
                }
            }

            transaction.commit();
        }

        // Install and deploy XAR
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = brokerPool.getXQueryService();
            final Sequence result = xquery.execute(broker,"repo:install-and-deploy-from-db('/db/triggertest-1.0-SNAPSHOT.xar')", null);
            Assert.assertEquals(1, result.getItemCount());
        }

        // Store collection.xconf in newly created collection under /db/system/config
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = brokerPool.getXQueryService();
            final Sequence result = xquery.execute(broker, "xmldb:create-collection('/db/system/config/db','trigger-test'), " +
                    "xmldb:store('/db/system/config/db/trigger-test', 'collection.xconf', " +
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