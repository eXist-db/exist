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
     * except for /db/system.
     *
     * @throws EXistException if an error occurs with the database.
     * @throws PermissionDeniedException if the user does not have appropriate permissions.
     * @throws LockException if a lock cannot be obtained.
     * @throws IOException if an IO error occurs.
     * @throws TriggerException if a trigger throws an error.
     */
    public static void cleanupDB() throws EXistException, PermissionDeniedException, LockException, IOException, TriggerException {
        BrokerPool pool = BrokerPool.getInstance();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // Remove all collections below the /db root, except /db/system
            try(final Collection root = broker.openCollection(XmldbURI.ROOT_COLLECTION_URI, Lock.LockMode.WRITE_LOCK)) {
                if(root == null) {
                    transaction.commit();
                    return;
                }

                for (final Iterator<DocumentImpl> i = root.iterator(broker); i.hasNext(); ) {
                    final DocumentImpl doc = i.next();
                    root.removeXMLResource(transaction, broker, doc.getURI().lastSegment());
                }

                for (final Iterator<XmldbURI> i = root.collectionIterator(broker); i.hasNext(); ) {
                    final XmldbURI childName = i.next();
                    if (childName.equals("system")) {
                        continue;
                    }

                    try(final Collection childColl = broker.openCollection(XmldbURI.ROOT_COLLECTION_URI.append(childName), Lock.LockMode.WRITE_LOCK)) {
                        broker.removeCollection(transaction, childColl);
                    }
                }
            }

            // Remove /db/system/config/db and all collection configurations with it
            try(final Collection dbConfig = broker.openCollection(XmldbURI.CONFIG_COLLECTION_URI.append("/db"), Lock.LockMode.WRITE_LOCK)) {
                if(dbConfig == null) {
                    transaction.commit();
                    return;
                }
                broker.removeCollection(transaction, dbConfig);
            }

            pool.getTransactionManager().commit(transaction);
        }
    }

    /**
     * Deletes all data files from the eXist data files directory.
     *
     * @throws IOException if an IO error occurs.
     * @throws DatabaseConfigurationException if an error occurs whilst configuring the database.
     */
    public static void cleanupDataDir() throws IOException, DatabaseConfigurationException {
        final Configuration conf = new Configuration();
        final Path data = (Path) conf.getProperty(BrokerPool.PROPERTY_DATA_DIR);

        try(final Stream<Path> dataFiles  = Files.list(data)) {
            dataFiles
                    .filter(path -> !("RECOVERY".equals(FileUtils.fileName(path)) || "README".equals(FileUtils.fileName(path)) || ".DO_NOT_DELETE".equals(FileUtils.fileName(path))))
                    .forEach(FileUtils::deleteQuietly);
        }
    }

    /**
     * Reads the content of a file.
     *
     * @param directory The directory to read from
     * @param filename the filename in the directory to read from
     *
     * @return The content of the file
     *
     * @throws IOException if an IO error occurs.
     */
    public static byte[] readFile(final Path directory, final String filename) throws IOException {
        return readFile(directory.resolve(filename));
    }

    /**
     * Reads the content of a file.
     *
     * @param file the file to read from
     *
     * @return The content of the file
     *
     * @throws IOException if an IO error occurs.
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
     * Get a file from within the EXIST_HOME directory.
     *
     * @param fileName Just the name of the file.
     *
     * @return The path if it exists
     *
     * @throws IOException if an IO error occurs.
     */
    public static Optional<Path> getExistHomeFile(final String fileName) throws IOException {
        final Path path = getEXistHome().orElseGet(() -> Paths.get(".")).resolve(fileName);
        if(Files.exists(path)) {
            return Optional.of(path);
        } else {
            return Optional.empty();
        }
    }
}
