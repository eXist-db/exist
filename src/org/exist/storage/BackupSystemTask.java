package org.exist.storage;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.backup.Backup;
import org.exist.util.Configuration;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.SAXException;
import org.xmldb.api.base.XMLDBException;

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

    private static final Logger LOG = Logger.getLogger(BackupSystemTask.class);

    private static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HHmm");

    private String user;
    private String password;
    private File directory;
    private String suffix;
    private XmldbURI collection;
    private String prefix;

    public void configure(Configuration config, Properties properties) throws EXistException {
        user = properties.getProperty("user", "guest");
        password = properties.getProperty("password", "guest");
        String collName = properties.getProperty("collection", "xmldb:exist:///db");
        if (!collName.startsWith("xmldb:exist:"))
            collName = "xmldb:exist://" + collName;
        collection = XmldbURI.create(collName);
        LOG.debug("Collection to backup: " + collection.toString() + ". User: " + user);

        suffix = properties.getProperty("suffix", "");
        prefix = properties.getProperty("prefix", "");
        
        String dir = properties.getProperty("dir", "backup");
        directory = new File(dir);
        if (!directory.isAbsolute()) {
            dir = (String)config.getProperty(BrokerPool.PROPERTY_DATA_DIR) +
                File.separatorChar + dir;
            directory = new File(dir);
        }
        directory.mkdirs();
    }


    public void execute(DBBroker broker) throws EXistException {
        String dateTime = df.format(new Date());
        String dest = directory.getAbsolutePath() + File.separatorChar + prefix + dateTime + suffix;

        Backup backup = new Backup(user, password, dest, collection);
        try {
            backup.backup(false, null);
        } catch (XMLDBException e) {
            LOG.debug(e.getMessage(), e);
            throw new EXistException(e.getMessage(), e);
        } catch (IOException e) {
            LOG.debug(e.getMessage(), e);
            throw new EXistException(e.getMessage(), e);
        } catch (SAXException e) {
            LOG.debug(e.getMessage(), e);
            throw new EXistException(e.getMessage(), e);
        }
    }
}
