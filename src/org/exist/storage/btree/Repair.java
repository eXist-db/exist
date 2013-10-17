package org.exist.storage.btree;

import org.exist.EXistException;
import org.exist.indexing.StructuralIndex;
import org.exist.security.internal.SubjectImpl;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.structural.NativeStructuralIndex;
import org.exist.storage.structural.NativeStructuralIndexWorker;
import org.exist.storage.sync.Sync;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.TerminatedException;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;

/**
 *
 */
public class Repair {

    private static final String[] INDEXES = {
            "collections", "dom", "structure"
    };

    private BrokerPool pool;

    public Repair() {
        startDB();
    }

    public void repair(String id) {
        DBBroker broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());

            BTree btree = null;
            if ("collections".equals(id)) {
                btree = ((NativeBroker)broker).getStorage(NativeBroker.COLLECTIONS_DBX_ID);
            } else if ("dom".equals(id)) {
                btree = ((NativeBroker)broker).getStorage(NativeBroker.DOM_DBX_ID);
            } else if ("range".equals(id)) {
                btree = ((NativeBroker)broker).getStorage(NativeBroker.VALUES_DBX_ID);
            } else if ("structure".equals(id)) {
                NativeStructuralIndexWorker index = (NativeStructuralIndexWorker)
                        broker.getIndexController().getWorkerByIndexName(StructuralIndex.STRUCTURAL_INDEX_ID);
                btree = index.getStorage();
            }
            if (btree == null) {
                System.console().printf("Unkown index: %s", id);
                return;
            }
            final Lock lock = btree.getLock();
            try {
                lock.acquire(Lock.WRITE_LOCK);

//                btree.scanSequential();
                System.console().printf("Rebuilding %15s ...", btree.getFile().getName());
                btree.rebuild();
                System.out.println("Done");
            } finally {
                lock.release(Lock.WRITE_LOCK);
            }

        } catch (Exception e) {
            System.console().printf("An exception occurred during repair: %s\n", e.getMessage());
            e.printStackTrace();
        } finally {
            pool.release(broker);
        }
    }

    private void startDB() {
        try {
            Configuration config = new Configuration();

            BrokerPool.configure(1, 5, config);
            pool = BrokerPool.getInstance();
        } catch (DatabaseConfigurationException e) {
            e.printStackTrace();
        } catch (EXistException e) {
            e.printStackTrace();
        }
    }

    public void shutdown() {
        pool.shutdown(false);
    }

    public static void main(String[] args) {
        Repair repair = new Repair();

        if (args.length == 0) {
            for (String index : INDEXES) {
                repair.repair(index);
            }
        } else {
            repair.repair(args[0]);
        }
        repair.shutdown();
    }
}
