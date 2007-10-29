package org.exist;

import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.dom.DocumentImpl;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: wolf
 * Date: Oct 29, 2007
 * Time: 4:28:26 PM
 * To change this template use File | Settings | File Templates.
 */
public class TestUtils {

    public static void cleanupDB() {
        BrokerPool pool = null;
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            pool = BrokerPool.getInstance();
            assertNotNull(pool);
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            assertNotNull(broker);
            transact = pool.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);

            // Remove all collections below the /db root, except /db/system
            Collection root = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI);
            assertNotNull(root);
            for (Iterator i = root.iterator(broker); i.hasNext(); ) {
                DocumentImpl doc = (DocumentImpl) i.next();
                root.removeXMLResource(transaction, broker, doc.getURI().lastSegment());
            }
            broker.saveCollection(transaction, root);
            for (Iterator i = root.collectionIterator(); i.hasNext(); ) {
                XmldbURI childName = (XmldbURI) i.next();
                if (childName.equals("system"))
                    continue;
                Collection childColl = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append(childName));
                assertNotNull(childColl);
                broker.removeCollection(transaction, childColl);
            }

            // Remove /db/system/config/db and all collection configurations with it
            Collection config = broker.getOrCreateCollection(transaction,
                XmldbURI.create(CollectionConfigurationManager.CONFIG_COLLECTION + "/db"));
            assertNotNull(config);
            broker.removeCollection(transaction, config);

            transact.commit(transaction);
        } catch (Exception e) {
        	transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null) pool.release(broker);
        }
    }
}
