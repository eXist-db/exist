package org.exist.storage;

import org.exist.util.Configuration;
import org.exist.EXistException;
import org.exist.xmldb.XmldbURI;
import org.exist.backup.Backup;
import org.xmldb.api.base.XMLDBException;
import org.xml.sax.SAXException;
import org.apache.log4j.Logger;

import java.util.Properties;
import java.io.File;
import java.io.IOException;

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
 *          <td>the directory (or zip file) into which the backup will be written. If the path ends
 *          with .zip, a zip file will be generated at the specified location and the backup will be
 *          directly written into this file. Otherwise, the task creates a directory for the specified
 *          path. If you pass a relative pass, it will be interpreted relative to the data directory
 *          set in conf.xml.</td>
 *      </tr>
 *  </table>
 */
public class BackupSystemTask implements SystemTask {

    private String user;
    private String password;
    private String dest;
    private XmldbURI collection;
    private static final Logger LOG = Logger.getLogger(BackupSystemTask.class);

    public void configure(Configuration config, Properties properties) throws EXistException {
        user = properties.getProperty("user", "guest");
        password = properties.getProperty("password", "guest");
        String collName = properties.getProperty("collection", "xmldb:exist:///db");
        if (!collName.startsWith("xmldb:exist:"))
            collName = "xmldb:exist://" + collName;
        collection = XmldbURI.create(collName);
        LOG.debug("Collection to backup: " + collection.toString());
        
        dest = properties.getProperty("dir", "backup");
        File f = new File(dest);
        if (!f.isAbsolute()) {
            dest = (String)config.getProperty("db-connection.data-dir") +
                File.separatorChar + dest;
            f = new File(dest);
        }
        dest = f.getAbsolutePath();
    }


    public void execute(DBBroker broker) throws EXistException {
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
