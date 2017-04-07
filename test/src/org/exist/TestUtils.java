package org.exist;

import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.FileUtils;
import org.exist.xmldb.XmldbURI;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;

import org.exist.util.ConfigurationHelper;

/**
 * Utility functions for working with tests
 */
public class TestUtils {

    /**
     * Removes all sub-collections of /db
     * except for /db/system
     */
    public static void cleanupDB() {
        try {
            BrokerPool pool = BrokerPool.getInstance();
            assertNotNull(pool);
            try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {

                // Remove all collections below the /db root, except /db/system
                Collection root = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI);
                assertNotNull(root);
                for (Iterator<DocumentImpl> i = root.iterator(broker); i.hasNext(); ) {
                    DocumentImpl doc = i.next();
                    root.removeXMLResource(transaction, broker, doc.getURI().lastSegment());
                }
                broker.saveCollection(transaction, root);
                for (Iterator<XmldbURI> i = root.collectionIterator(broker); i.hasNext(); ) {
                    XmldbURI childName = i.next();
                    if (childName.equals("system"))
                        continue;
                    Collection childColl = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append(childName));
                    assertNotNull(childColl);
                    broker.removeCollection(transaction, childColl);
                }

                // Remove /db/system/config/db and all collection configurations with it
                Collection config = broker.getOrCreateCollection(transaction,
                        XmldbURI.create(XmldbURI.CONFIG_COLLECTION + "/db"));
                assertNotNull(config);
                broker.removeCollection(transaction, config);
                
                pool.getTransactionManager().commit(transaction);
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
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
        assertTrue(Files.isReadable(file));
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
     * Reads the content of the sample hamlet.xml
     *
     * @return The content of the file
     */
    public static byte[] readHamletSampleXml() throws IOException {
        return readSample("shakespeare/hamlet.xml");
    }

    /**
     * Reads the content of the sample r_and_j.xml
     *
     * @return The content of the file
     */
    public static byte[] readRomeoAndJulietSampleXml() throws IOException {
        return readSample("shakespeare/r_and_j.xml");
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
}
