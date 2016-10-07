package org.exist.storage.lock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.storage.BrokerPoolService;
import org.exist.storage.BrokerPoolServiceException;
import org.exist.util.Configuration;
import org.exist.util.ReadOnlyException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A Simple Service wrapper for {@link FileLock}
 */
public class FileLockService implements BrokerPoolService {
    private final static Logger LOG = LogManager.getLogger(FileLockService.class);

    private final String lockFileName;
    private final String confDirPropName;
    private final String defaultDirName;

    private Path dataDir;
    private boolean writable;
    private AtomicReference<FileLock> dataLock = new AtomicReference<>();

    public FileLockService(final String lockFileName, final String confDirPropName, final String defaultDirName) {
        this.lockFileName = lockFileName;
        this.confDirPropName = confDirPropName;
        this.defaultDirName = defaultDirName;
    }

    @Override
    public void configure(final Configuration configuration) throws BrokerPoolServiceException {
        dataDir = Optional.ofNullable((Path) configuration.getProperty(confDirPropName))
                .orElse(Paths.get(defaultDirName));

        if(!Files.exists(dataDir)) {
            try {
                //TODO : shall we force the creation ? use a parameter to decide ?
                LOG.info("Data directory '" + dataDir.toAbsolutePath().toString() + "' does not exist. Creating one ...");
                Files.createDirectories(dataDir);
            } catch(final SecurityException | IOException e) {
                throw new BrokerPoolServiceException("Cannot create data directory '" + dataDir.toAbsolutePath().toString() + "'", e);
            }
        }

        //Save it for further use.
        configuration.setProperty(confDirPropName, dataDir);

        if(!Files.isWritable(dataDir)) {
            LOG.warn("Cannot write to data directory: " + dataDir.toAbsolutePath().toString());
            writable = false;
        } else {
            writable = true;
        }
    }

    @Override
    public void prepare(final BrokerPool brokerPool) throws BrokerPoolServiceException {
        // try to acquire lock on the data dir
        final FileLock fileLock = new FileLock(brokerPool, dataDir.resolve(lockFileName));
        this.dataLock.compareAndSet(null, fileLock);

        try {
            final boolean locked = fileLock.tryLock();
            if(!locked) {
                throw new BrokerPoolServiceException(new EXistException("The directory seems to be locked by another " +
                        "database instance. Found a valid lock file: " + fileLock.getFile().toAbsolutePath().toString()));
            }
        } catch(final ReadOnlyException e) {
            LOG.warn(e);
            writable = false;
        }

        if(!writable) {
            brokerPool.setReadOnly();
        }
    }

    /**
     * Is this directory managed by this File Lock read-only?
     *
     * @return true if the directory is read-only
     */
    public boolean isReadOnly() {
        return !writable;
    }

    public Path getFile() {
        final FileLock fileLock = dataLock.get();
        if(fileLock == null) {
            return null;
        } else {
            return fileLock.getFile();
        }
    }

    //TODO(AR) instead we should implement a BrokerPoolService#shutdown() and BrokerPoolServicesManager#shutdown()
    public void release() {
        final FileLock fileLock = dataLock.get();
        if(fileLock != null) {
            fileLock.release();
        }
        dataLock.compareAndSet(fileLock, null);
    }
}
