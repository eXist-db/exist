package org.exist.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.backup.Backup;
import org.exist.util.Configuration;
import org.exist.util.FileUtils;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.SAXException;
import org.xmldb.api.base.XMLDBException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * BackupSystemTask creates an XML backup of the current database into a directory
 * or zip file. Running the backup as a system task guarantees a consistent backup. No
 * other transactions will be allowed while the backup is in progress.
 *
 * The following properties can be used to configure the backup task if passed to the
 * {@link #configure(org.exist.util.Configuration, java.util.Properties)} method:
 *
 * <table>
 *      <tr>
 *          <td>collection</td>
 *          <td>the collection to backup, specified as an absolute path into the db, e.g. /db/back-me-up</td>
 *      </tr>
 *      <tr>
 *          <td>user</td>
 *          <td>a valid user for writing the backup. Usually, this needs to be a user in the dba
 *          database admin group.</td>
 *      </tr>
 *      <tr>
 *          <td>password</td>
 *          <td>the password for the user</td>
 *      </tr>
 *      <tr>
 *          <td>dir</td>
 *          <td>the output directory where the backup will be written</td>
 *      </tr>
 *      <tr>
 *          <td>prefix</td>
 *          <td>a prefix for the generated file name. the final file name will consist of
 *          prefix + current-dateTime + suffix</td>
 *      </tr>
 *      <tr>
 *          <td>suffix</td>
 *          <td>a suffix for the generated file name. If it ends with .zip, BackupSystemTask will
 *          directly write the backup into a zip file. Otherwise, it will write into a plain directory.</td>
 *      </tr>
 *  </table>
 */
public class BackupSystemTask implements SystemTask {

    private static final Logger LOG = LogManager.getLogger(BackupSystemTask.class);

    private final SimpleDateFormat creationDateFormat = new SimpleDateFormat(DataBackup.DATE_FORMAT_PICTURE);
    private String user;
    private String password;
    private Path directory;
    private String suffix;
    private XmldbURI collection;
    private String prefix;
    // purge old zip backup files
    private int zipFilesMax = -1;

    public void configure(Configuration config, Properties properties) throws EXistException {
        user = properties.getProperty("user", "guest");
        password = properties.getProperty("password", "guest");
        String collName = properties.getProperty("collection", "xmldb:exist:///db");
        if (!collName.startsWith("xmldb:exist:"))
            {collName = "xmldb:exist://" + collName;}
        collection = XmldbURI.create(collName);
        LOG.debug("Collection to backup: " + collection.toString() + ". User: " + user);

        suffix = properties.getProperty("suffix", "");
        prefix = properties.getProperty("prefix", "");
        
        String dir = properties.getProperty("dir", "backup");
        directory = Paths.get(dir);
        if (!directory.isAbsolute()) {
            directory = ((Path)config.getProperty(BrokerPool.PROPERTY_DATA_DIR)).resolve(dir);
        }

        try {
            Files.createDirectories(directory);
        } catch(final IOException ioe) {
            throw new EXistException("Unable to create backup directory: " + directory.toAbsolutePath().toString(), ioe);
        }

        // check for max zip files
        final String filesMaxStr = properties.getProperty("zip-files-max");
        if (LOG.isDebugEnabled()) {LOG.debug("zip-files-max: " + filesMaxStr);}
        if (null != filesMaxStr) {
            try {
                zipFilesMax = Integer.parseInt(filesMaxStr);
            } catch (final NumberFormatException e) {
                LOG.debug("zip-files-max property error", e);
            }
        }
    }


    public void execute(DBBroker broker) throws EXistException {
        final String dateTime = creationDateFormat.format(Calendar.getInstance().getTime());
        final Path dest = directory.resolve(prefix + dateTime + suffix);

        final Backup backup = new Backup(user, password, dest, collection);
        try {
            backup.backup(false, null);
        } catch (final XMLDBException e) {
            LOG.debug(e.getMessage(), e);
            throw new EXistException(e.getMessage(), e);
        } catch (final IOException e) {
            LOG.debug(e.getMessage(), e);
            throw new EXistException(e.getMessage(), e);
        } catch (final SAXException e) {
            LOG.debug(e.getMessage(), e);
            throw new EXistException(e.getMessage(), e);
        }

        // see if old zip files need to be purged
        if (".zip".equals(suffix) && zipFilesMax > 0) {
            try {
                purgeZipFiles();
            } catch(final IOException ioe) {
                throw new EXistException("Unable to purge zip files", ioe);
            }
        }
    }

    public void purgeZipFiles() throws IOException {
        if (LOG.isDebugEnabled()) {LOG.debug("starting purgeZipFiles()");}

        // get all files in target directory
        final List<Path> files = FileUtils.list(directory);

        if (!files.isEmpty()) {
            final Map<String, Path> sorted = new TreeMap<>();
            for (final Path file : files) {
                //check for prefix and suffix match
                if (file.getFileName().startsWith(prefix) && FileUtils.fileName(file).endsWith(suffix)) {
                    sorted.put(Long.toString(Files.getLastModifiedTime(file).toMillis()), file);
                }
            }


            if (sorted.size() > zipFilesMax) {
                final Set<String> keys = sorted.keySet();
                final Iterator<String> ki = keys.iterator();
                int i = sorted.size() - zipFilesMax;
                while (ki.hasNext()) {
                    final Path f = sorted.get(ki.next());
                    if (i > 0) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Purging backup : " + FileUtils.fileName(f));
                        }
                        FileUtils.deleteQuietly(f);
                    }
                    i--;
                }
            }
        }
    }

    @Override
    public boolean afterCheckpoint() {
    	return false;
    }
}
