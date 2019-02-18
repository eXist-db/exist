package org.exist;

import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
import org.exist.xmldb.XmldbURI;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Utility functions for working with tests
 */
public class TestUtils {

    /**
     * Default Admin username used in tests
     */
    public static final String ADMIN_DB_USER = "admin";

    /**
     * Default Admin password used in tests
     */
    public static final String ADMIN_DB_PWD = "";

    /**
     * Default Guest username used in tests
     */
    public static final String GUEST_DB_USER = "guest";

    /**
     * Default Guest password used in tests
     */
    public static final String GUEST_DB_PWD = "guest";

    /**
     * Removes all sub-collections of /db
     * except for /db/system
     */
    public static void cleanupDB() throws EXistException, PermissionDeniedException, LockException, IOException, TriggerException {
        BrokerPool pool = BrokerPool.getInstance();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // Remove all collections below the /db root, except /db/system
            Collection root = null;
            try {
                root = broker.openCollection(XmldbURI.ROOT_COLLECTION_URI, Lock.LockMode.WRITE_LOCK);
                if(root == null) {
                    transaction.commit();
                    return;
                }

                for (final Iterator<DocumentImpl> i = root.iterator(broker); i.hasNext(); ) {
                    final DocumentImpl doc = i.next();
                    root.removeXMLResource(transaction, broker, doc.getURI().lastSegment());
                }
                broker.saveCollection(transaction, root);

                for (final Iterator<XmldbURI> i = root.collectionIterator(broker); i.hasNext(); ) {
                    final XmldbURI childName = i.next();
                    if (childName.equals("system")) {
                        continue;
                    }

                    Collection childColl = null;
                    try {
                        childColl = broker.openCollection(XmldbURI.ROOT_COLLECTION_URI.append(childName), Lock.LockMode.WRITE_LOCK);
                        broker.removeCollection(transaction, childColl);
                    } finally {
                        childColl.getLock().release(Lock.LockMode.WRITE_LOCK);
                    }
                }
                broker.saveCollection(transaction, root);
            } finally {
                if(root != null) {
                    root.getLock().release(Lock.LockMode.WRITE_LOCK);
                }
            }

            // Remove /db/system/config/db and all collection configurations with it
            Collection dbConfig = null;
            try {
                dbConfig = broker.openCollection(XmldbURI.CONFIG_COLLECTION_URI.append("/db"), Lock.LockMode.WRITE_LOCK);
                if(dbConfig == null) {
                    transaction.commit();
                    return;
                }
                broker.removeCollection(transaction, dbConfig);

            } finally {
                if(dbConfig != null) {
                    dbConfig.getLock().release(Lock.LockMode.WRITE_LOCK);
                }
            }

            pool.getTransactionManager().commit(transaction);
        }
    }

    /**
     * Deletes all data files from the eXist data files directory
     */
    public static void cleanupDataDir() throws IOException, DatabaseConfigurationException {
        final Configuration conf = new Configuration();
        final Path data = (Path) conf.getProperty(BrokerPool.PROPERTY_DATA_DIR);

        try(final Stream<Path> dataFiles  = Files.list(data)) {
            dataFiles
                    .filter(path -> !(FileUtils.fileName(path).equals("RECOVERY") || FileUtils.fileName(path).equals("README") || FileUtils.fileName(path).equals(".DO_NOT_DELETE")))
                    .forEach(FileUtils::deleteQuietly);
        }
    }

    /**
     * Reads the content of a file
     *
     * @param directory The directory to read from
     * @param filename the filename in the directory to read from
     *
     * @return The content of the file
     */
    public static byte[] readFile(final Path directory, final String filename) throws IOException {
        return readFile(directory.resolve(filename));
    }

    /**
     * Reads the content of a file
     *
     * @param file the file to read from
     *
     * @return The content of the file
     */
    public static byte[] readFile(final Path file) throws IOException {
        if(!Files.isReadable(file)) {
            throw new IOException("Cannot read: " + file);
        }
        return Files.readAllBytes(file);
    }

    /**
     * Get the EXIST_HOME directory
     *
     * @return The absolute path to the EXIST_HOME folder
     *   or {@link Optional#empty()}
     */
    public static Optional<Path> getEXistHome() {
        return ConfigurationHelper.getExistHome().map(Path::toAbsolutePath);
    }

    /**
     * Get a file from within the EXIST_HOME directory
     *
     * @param fileName Just the name of the file.
     *
     * @return The path if it exists
     */
    public static Optional<Path> getExistHomeFile(final String fileName) throws IOException {
        final Path path = getEXistHome().orElseGet(() -> Paths.get(".")).resolve(fileName);
        if(Files.exists(path)) {
            return Optional.of(path);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Reads the content of the sample hamlet.xml
     *
     * @return The content of the file
     */
    public static byte[] readHamletSampleXml() throws IOException {
        return readFile(resolveHamletSample());
    }

    /**
     * Reads the content of the sample r_and_j.xml
     *
     * @return The content of the file
     */
    public static byte[] readRomeoAndJulietSampleXml() throws IOException {
        return readFile(resolveRomeoAndJulietSample());
    }

    /**
     * Reads the content of the sample file
     *
     * @param sampleRelativePath The path of the sample file relative to the samples directory
     *
     * @return The content of the file
     */
    public static byte[] readSample(final String sampleRelativePath) throws IOException {
        final Path file = resolveSample(sampleRelativePath);
        return readFile(file);
    }

    /**
     * Resolve the path of a sample file
     *
     * @param relativePath The path of the sample file relative to the samples directory
     *
     * @return The absolute path to the sample file
     */
    public static Path resolveSample(final String relativePath) {
        final Path samples = FileUtils.resolve(getEXistHome(), "samples");
        return samples.resolve(relativePath);
    }

    /**
     * Gets the path of the Shakespeare samples
     *
     * @return The path to the Shakespeare samples
     */
    public static Path shakespeareSamples() {
        return resolveSample("shakespeare");
    }

    /**
     * Resolve the path of a Shakespeare sample file
     *
     * @param relativePath The path of the Shakespeare sample file relative to the Shakespeare samples directory
     *
     * @return The absolute path to the sample file
     */
    public static Path resolveShakespeareSample(final String relativePath) {
        return shakespeareSamples().resolve(relativePath);
    }

    /**
     * Gets the path of the Shakespeare Hamlet sample
     *
     * @return The path to the Shakespeare Hamlet sample
     */
    public static Path resolveHamletSample() {
        return resolveShakespeareSample("hamlet.xml");
    }

    /**
     * Gets the path of the Shakespeare Romeo and Juliet sample
     *
     * @return The path to the Shakespeare Romeo and Juliet sample
     */
    public static Path resolveRomeoAndJulietSample() {
        return resolveShakespeareSample("r_and_j.xml");
    }
}
