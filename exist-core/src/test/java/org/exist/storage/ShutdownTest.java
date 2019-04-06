package org.exist.storage;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.*;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static samples.Samples.SAMPLES;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class ShutdownTest {

    @ClassRule
    public static ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
	public void shutdown() throws EXistException, LockException, TriggerException, PermissionDeniedException, XPathException, IOException, URISyntaxException {
		for (int i = 0; i < 2; i++) {
			storeAndShutdown();
		}
	}
	
	public void storeAndShutdown() throws EXistException, PermissionDeniedException, IOException, TriggerException, LockException, XPathException, URISyntaxException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();

		try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            Collection test;

            try(final Txn transaction = transact.beginTransaction()) {

                test = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
                assertNotNull(test);
                broker.saveCollection(transaction, test);

	            final List<Path> files = FileUtils.list(SAMPLES.getShakespeareSamples(), XMLFilenameFilter.asPredicate());

                // store some documents.
	            for(final Path f : files) {
                    try {
                        final IndexInfo info = test.validateXMLResource(transaction, broker, XmldbURI.create(FileUtils.fileName(f)), new InputSource(f.toUri().toASCIIString()));
                        assertNotNull(info);
                        test.store(transaction, broker, info, new InputSource(f.toUri().toASCIIString()));
                    } catch (SAXException e) {
                        fail("Error found while parsing document: " + FileUtils.fileName(f) + ": " + e.getMessage());
                    }
                }

                final XQuery xquery = pool.getXQueryService();
                assertNotNull(xquery);
                final Sequence result = xquery.execute(broker, "//SPEECH[contains(LINE, 'love')]", Sequence.EMPTY_SEQUENCE);
                assertNotNull(result);
                assertEquals(187, result.getItemCount());

                transact.commit(transaction);
            }

            try(final Txn transaction = transact.beginTransaction()) {
                broker.removeCollection(transaction, test);
                transact.commit(transaction);
            }
        }
	}
}
