/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.backup.Backup;
import org.exist.storage.txn.Txn;
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

/**
 * BackupSystemTask creates an XML backup of the current database into a directory
 * or zip file. Running the backup as a system task guarantees a consistent backup. No
 * other transactions will be allowed while the backup is in progress.
 *
 * The following properties can be used to configure the backup task if passed to the
 * {@link #configure(org.exist.util.Configuration, java.util.Properties)} method:
 *
 * <table>
 *      <caption>Properties</caption>
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
    private boolean deduplicateBlobs;
    private String prefix;
    // purge old zip backup files
    private int zipFilesMax = -1;

    @Override
    public String getName() {
        return "Backup Task";
    }

    @Override
    public void configure(final Configuration config, final Properties properties) throws EXistException {
        user = properties.getProperty("user", "guest");
        password = properties.getProperty("password", "guest");
        String collName = properties.getProperty("collection", "xmldb:exist:///db");
        if (!collName.startsWith("xmldb:exist:")) {
            collName = "xmldb:exist://" + collName;
        }
        collection = XmldbURI.create(collName);
        LOG.debug("Collection to backup: {}. User: {}", collection.toString(), user);

        deduplicateBlobs = Boolean.parseBoolean(properties.getProperty("deduplucate-blobs", "false"));

        suffix = properties.getProperty("suffix", "");
        prefix = properties.getProperty("prefix", "");
        
        final String dir = properties.getProperty("dir", "backup");
        directory = Paths.get(dir);
        if (!directory.isAbsolute()) {
            directory = ((Path)config.getProperty(BrokerPool.PROPERTY_DATA_DIR)).resolve(dir);
        }

        try {
            Files.createDirectories(directory);
        } catch(final IOException ioe) {
            throw new EXistException("Unable to create backup directory: " + directory.toAbsolutePath(), ioe);
        }

        // check for max zip files
        final String filesMaxStr = properties.getProperty("zip-files-max");
        if (LOG.isDebugEnabled()) {
            LOG.debug("zip-files-max: {}", filesMaxStr);}
        if (null != filesMaxStr) {
            try {
                zipFilesMax = Integer.parseInt(filesMaxStr);
            } catch (final NumberFormatException e) {
                LOG.error("zip-files-max property error", e);
            }
        }
    }

    @Override
    public void execute(final DBBroker broker, final Txn transaction) throws EXistException {
        // see if old zip files need to be purged
        if (zipFilesMax > 0) {
            try {
                purgeZipFiles();
            } catch(final IOException ioe) {
                throw new EXistException("Unable to purge zip files", ioe);
            }
        } 
        
        final String dateTime = creationDateFormat.format(Calendar.getInstance().getTime());
        final Path dest = directory.resolve(prefix + dateTime + suffix);

        final Backup backup = new Backup(user, password, dest, collection, null, deduplicateBlobs);
        try {
            backup.backup(false, null);
        } catch (final XMLDBException | SAXException | IOException e) {
            LOG.error(e.getMessage(), e);
            throw new EXistException(e.getMessage(), e);
        }
    }

    public void purgeZipFiles() throws IOException {
        if (LOG.isDebugEnabled()) {LOG.debug("starting purgeZipFiles()");}
		
		List<Path> entriesPaths = FileUtils.list(directory, FileUtils.getPrefixSuffixFilter(prefix, suffix));
		int entriesNumber = entriesPaths.size();
		int numberOfEntriesToBeDeleted = entriesNumber - zipFilesMax + 1;

		Comparator<Path> timestampComparator = (path1, path2) -> {
            int result = 0;

            try {
                result = Files.getLastModifiedTime(path1).compareTo(Files.getLastModifiedTime(path2));
            } catch (IOException e) {
                LOG.error("Cannot compare files by timestamp: {}, {}", path1, path2, e);
            }

            return result;
        };

		if (numberOfEntriesToBeDeleted > 0) {
			entriesPaths.stream().sorted(timestampComparator).limit(numberOfEntriesToBeDeleted).forEach(path -> {
	            if (LOG.isDebugEnabled()) {
                    LOG.debug("Purging backup : {}", FileUtils.fileName(path));
	            }
	            
	            FileUtils.deleteQuietly(path);
			});	
		}
    }

    @Override
    public boolean afterCheckpoint() {
    	return false;
    }
}
