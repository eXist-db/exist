package org.exist.storage.structural;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.log4j.Logger;
import org.exist.backup.RawDataBackup;
import org.exist.dom.SymbolTable;
import org.exist.indexing.AbstractIndex;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.RawBackupSupport;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.btree.DBException;
import org.exist.storage.index.BTreeStore;
import org.exist.storage.lock.Lock;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.w3c.dom.Element;

public class NativeStructuralIndex extends AbstractIndex implements RawBackupSupport {

    protected static final Logger LOG = Logger.getLogger(NativeStructuralIndex.class);

    public final static String ID = NativeStructuralIndex.class.getName();
    public static final String FILE_NAME = "structure.dbx";

    public static final String  FILE_KEY_IN_CONFIG = "db-connection.elements";
    public static final double DEFAULT_STRUCTURAL_CACHE_GROWTH = 1.25;
    public static final double DEFAULT_STRUCTURAL_KEY_THRESHOLD = 0.1;

    public static final double DEFAULT_STRUCTURAL_VALUE_THRESHOLD = 0.1;

    public static final byte STRUCTURAL_INDEX_ID = 1;

    /** The datastore for this node index */
    protected BTreeStore btree;

    protected SymbolTable symbols;

    public NativeStructuralIndex() {
        //Nothing to do
    }

    @Override
    public void configure(BrokerPool pool, String dataDir, Element config) throws DatabaseConfigurationException {
        super.configure(pool, dataDir, config);
        symbols = pool.getSymbols();
    }

    @Override
    public void open() throws DatabaseConfigurationException {
        final File file = new File(getDataDir(), FILE_NAME);
        LOG.debug("Creating '" + file.getName() + "'...");
        try {
            btree = new BTreeStore(pool, STRUCTURAL_INDEX_ID, false,
                    file, pool.getCacheManager(), DEFAULT_STRUCTURAL_KEY_THRESHOLD);
        } catch (final DBException e) {
            LOG.error("Failed to initialize structural index: " + e.getMessage(), e);
            throw new DatabaseConfigurationException(e.getMessage(), e);
        }
    }

    @Override
    public void close() throws DBException {
        btree.close();
        btree = null;
    }

    @Override
    public void sync() throws DBException {
        if (btree == null)
            {return;}
        final Lock lock = btree.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            btree.flush();
        } catch (final LockException e) {
            LOG.warn("Failed to acquire lock for '" + btree.getFile().getName() + "'", e);
            //TODO : throw an exception ? -pb
        } catch (final DBException e) {
            LOG.error(e.getMessage(), e);
            //TODO : throw an exception ? -pb
        } finally {
            lock.release(Lock.WRITE_LOCK);
        }
    }

    @Override
    public void remove() throws DBException {
        btree.closeAndRemove();
    }

    @Override
    public IndexWorker getWorker(DBBroker broker) {
        return new NativeStructuralIndexWorker(this);
    }

    @Override
    public boolean checkIndex(DBBroker broker) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

	@Override
	public void backupToArchive(RawDataBackup backup) throws IOException {
        final OutputStream os = backup.newEntry(btree.getFile().getName());
        btree.backupToStream(os);
        backup.closeEntry();
	}
}
