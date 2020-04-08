package org.exist.repo;

import org.apache.commons.io.IOUtils;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
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
import java.util.Optional;

public class PackageSkipTriggerTest {

    static final String xarFile = "exist-test-expath-skiptriggers-0.99.7.xar";
    static final XmldbURI triggerTestCollection = XmldbURI.create("/db");
    static final XmldbURI xarUri = triggerTestCollection.append(xarFile);

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
        byte[] content;
        try (InputStream resourceAsStream = PackageSkipTriggerTest.class.getResourceAsStream(xarFile)) {
            Assert.assertNotNull(resourceAsStream);
            content = IOUtils.toByteArray(resourceAsStream);
        }

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
            final Sequence result = xquery.execute(broker, "repo:install-and-deploy-from-db('/db/" + xarFile + "')", null);
            Assert.assertEquals(1, result.getItemCount());
        }
    }


    @Test
    public void checkTriggerFiresNot() throws EXistException, PermissionDeniedException, XPathException {

        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();

        // Verify the test document got installed
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = brokerPool.getXQueryService();
            final Sequence result = xquery.execute(broker, "xmldb:get-child-resources('/db/apps/test-expath-skiptriggers/testdata')", null);
            Assert.assertEquals("After trigger execution one document should be in the collection.", 1, result.getItemCount());
        }

        // Verify the triggers-log.xml file does not mention the test document
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = brokerPool.getXQueryService();
            final Sequence result = xquery.execute(broker, "doc('/db/triggers-log.xml')//triggers/trigger[@uri = '/db/apps/test-expath-skiptriggers/testdata/test.xml']", null);
            Assert.assertEquals("No trigger execution should be logged for the test document.", 0, result.getItemCount());
        }

    }
}
