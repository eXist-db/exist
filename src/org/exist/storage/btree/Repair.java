package org.exist.storage.btree;

import org.exist.EXistException;
import org.exist.security.internal.SubjectImpl;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
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

    private BrokerPool pool;

    public Repair() {
        startDB();
    }

    public void repair(byte id) {
        DBBroker broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());

            OutputStreamWriter writer = new OutputStreamWriter(System.out);

            BTree btree = ((NativeBroker)broker).getStorage(id);
            btree.dump(writer);
            writer.flush();
            System.out.println("Rebuilding ...");
            btree.rebuild();

            pool.sync(broker, Sync.MAJOR_SYNC);

            btree.dump(writer);
            writer.flush();
        } catch (EXistException e) {
            System.console().printf("An exception occurred during repair: %s", e.getMessage());
        } catch (TerminatedException e) {
            System.console().printf("Process terminated during repair: %s", e.getMessage());
        } catch (DBException e) {
            System.console().printf("An exception occurred during repair: %s", e.getMessage());
        } catch (IOException e) {
            System.console().printf("An exception occurred during repair: %s", e.getMessage());
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
        byte id = NativeBroker.DOM_DBX_ID;
        String dbx = args[0];
        if ("collections".equals(dbx)) {
            id = NativeBroker.COLLECTIONS_DBX_ID;
        } else if ("dom".equals(dbx)) {
            id = NativeBroker.DOM_DBX_ID;
        }

        Repair repair = new Repair();
        repair.repair(id);

        repair.shutdown();
    }
}
