/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.collections;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.*;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.ManagedLock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.XMLReaderPool;
import org.exist.util.sanity.SanityCheck;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * Manages index configurations. Index configurations are stored in a collection
 * hierarchy below /db/system/config. CollectionConfigurationManager is called
 * by {@link org.exist.collections.Collection} to retrieve the
 * {@link org.exist.collections.CollectionConfiguration} instance for a given
 * collection.
 * 
 * @author wolf
 */
public class CollectionConfigurationManager implements BrokerPoolService {

    private static final Logger LOG = LogManager.getLogger(CollectionConfigurationManager.class);

    public static final String CONFIG_COLLECTION = XmldbURI.SYSTEM_COLLECTION + "/config";

    /** /db/system/config **/
    public static final XmldbURI CONFIG_COLLECTION_URI = XmldbURI.create(CONFIG_COLLECTION);

    /** /db/system/config/db **/
    public static final XmldbURI ROOT_COLLECTION_CONFIG_URI = CONFIG_COLLECTION_URI.append(XmldbURI.ROOT_COLLECTION_NAME);

    public static final String COLLECTION_CONFIG_FILENAME = "collection.xconf";

    public static final CollectionURI COLLECTION_CONFIG_PATH = new CollectionURI(CONFIG_COLLECTION_URI.getRawCollectionPath());

    private final Map<CollectionURI, CollectionConfiguration> configurations = new HashMap<>();

    private final ReadWriteLock lock = new java.util.concurrent.locks.ReentrantReadWriteLock();

    private final CollectionConfiguration defaultConfig;

    public CollectionConfigurationManager(final BrokerPool brokerPool) {
        this.defaultConfig = new CollectionConfiguration(brokerPool);
    }

    @Override
    public void startSystem(final DBBroker systemBroker, final Txn transaction) throws BrokerPoolServiceException {
        try {
            checkCreateCollection(systemBroker, transaction, CONFIG_COLLECTION_URI);
            checkCreateCollection(systemBroker, transaction, ROOT_COLLECTION_CONFIG_URI);
            loadAllConfigurations(systemBroker);
            defaultConfig.setIndexConfiguration(systemBroker.getIndexConfiguration());
        } catch(final EXistException | CollectionConfigurationException | PermissionDeniedException | LockException e) {
            throw new BrokerPoolServiceException(e);
        }
    }

    /**
     * Add a new collection configuration. The XML document is passed as a
     * string.
     * 
     * @param txn The transaction that will hold the WRITE locks until they are
     *            released by commit()/abort()
     * @param broker the eXist-db broker
     * @param collection  the collection to which the configuration applies.
     * @param config the xconf document as a String.
     * @throws CollectionConfigurationException if config is invalid
     */
    public void addConfiguration(final Txn txn, final DBBroker broker, final Collection collection, final String config) throws CollectionConfigurationException {
        try {
            final XmldbURI path = CONFIG_COLLECTION_URI.append(collection.getURI());

            final Collection confCol = broker.getOrCreateCollection(txn, path);
            if (confCol == null) {
                throw new CollectionConfigurationException("Failed to create config collection: " + path);
            }

            XmldbURI configurationDocumentName = null;
            // Replaces the current configuration file if there is one
            final CollectionConfiguration conf = getConfiguration(collection);
            if (conf != null) {
                configurationDocumentName = conf.getDocName();
                if (configurationDocumentName != null) {
                    LOG.warn("Replacing current configuration file '" + configurationDocumentName + "'");
                }
            }
            if (configurationDocumentName == null) {
                configurationDocumentName = CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE_URI;
            }

            broker.saveCollection(txn, confCol);
            final IndexInfo info = confCol.validateXMLResource(txn, broker, configurationDocumentName, config);
            // TODO : unlock the collection here ?
            confCol.store(txn, broker, info, config);
            // broker.sync(Sync.MAJOR_SYNC);
        } catch (final CollectionConfigurationException e) {
            throw e;
        } catch (final Exception e) {
            throw new CollectionConfigurationException("Failed to store collection configuration: " + e.getMessage(), e);
        }
    }

    /**
     * Check the passed collection configuration. Throws an exception if errors
     * are detected in the configuration document. Note: some configuration
     * settings depend on the current environment, in particular the
     * availability of trigger or index classes.
     * 
     * @param broker DBBroker
     * @param config the configuration to test
     * @throws CollectionConfigurationException if errors were detected
     */
    public void testConfiguration(DBBroker broker, String config) throws CollectionConfigurationException {
        try {
            final SAXAdapter adapter = new SAXAdapter();
            final InputSource src = new InputSource(new StringReader(config));
            final XMLReaderPool parserPool = broker.getBrokerPool().getParserPool();
            XMLReader reader = null;
            try {
                reader = parserPool.borrowXMLReader();
                reader.setContentHandler(adapter);
                reader.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
                reader.parse(src);
            } finally {
                if (reader != null) {
                    parserPool.returnXMLReader(reader);
                }
            }
            final Document doc = adapter.getDocument();
            final CollectionConfiguration conf = new CollectionConfiguration(broker.getBrokerPool());
            conf.read(broker, doc, true, null, null);
        } catch (final Exception e) {
            throw new CollectionConfigurationException(e);
        }
    }

    public List<Object> getCustomIndexSpecs(final String customIndexId) {
        try(final ManagedLock<ReadWriteLock> readLock = ManagedLock.acquire(lock, LockMode.READ_LOCK)) {
            final List<Object> configs = new ArrayList<>(10);
            for (final CollectionConfiguration config: configurations.values()) {
                final IndexSpec spec = config.getIndexConfiguration();
                if (spec != null) {
                    final Object customConfig = spec.getCustomIndexSpec(customIndexId);
                    if (customConfig != null) {
                        configs.add(customConfig);
                    }
                }
            }

            return configs;
        }
    }

    /**
     * Retrieve the collection configuration instance for the given collection.
     * This creates a new CollectionConfiguration object and recursively scans
     * the collection hierarchy for available configurations.
     *
     * @param collection to retrieve configuration for
     * @return The collection configuration
     */
    protected CollectionConfiguration getConfiguration(final Collection collection) {

        final CollectionURI path = new CollectionURI(COLLECTION_CONFIG_PATH);
        path.append(collection.getURI().getRawCollectionPath());

        /*
         * This used to go from the root collection (/db), and continue all the
         * way to the end of the path, checking each collection on the way. I
         * modified it to start at the collection path and work its way back to
         * the root, stopping at the first config file it finds. This should be
         * more efficient, and fit more appropriately with the XmldbURI api
         */
        try(final ManagedLock<ReadWriteLock> readLock = ManagedLock.acquire(lock, LockMode.READ_LOCK)) {
            while(!path.equals(COLLECTION_CONFIG_PATH)) {
                final CollectionConfiguration conf = configurations.get(path);
                if (conf != null) {
                    return conf;
                }
                path.removeLastSegment();
            }

            // use default configuration
            return defaultConfig;
        }
    }

    protected void loadAllConfigurations(DBBroker broker) throws CollectionConfigurationException, PermissionDeniedException, LockException {
        final Collection root = broker.getCollection(CONFIG_COLLECTION_URI);
        loadAllConfigurations(broker, root);
    }

    protected void loadAllConfigurations(DBBroker broker, Collection configCollection) throws CollectionConfigurationException, PermissionDeniedException,
            LockException {
        if (configCollection == null) {
            return;
        }
        loadConfiguration(broker, configCollection);
        final XmldbURI path = configCollection.getURI();
        for (final Iterator<XmldbURI> i = configCollection.collectionIterator(broker); i.hasNext();) {
            final XmldbURI childName = i.next();
            final Collection child = broker.getCollection(path.appendInternal(childName));
            if (child == null) {
                LOG.error("Collection is registered but could not be loaded: " + childName);
            }
            loadAllConfigurations(broker, child);
        }
    }

    protected void loadConfiguration(DBBroker broker, final Collection configCollection) throws CollectionConfigurationException, PermissionDeniedException,
            LockException {
        if (configCollection != null && configCollection.getDocumentCount(broker) > 0) {
            for (final Iterator<DocumentImpl> i = configCollection.iterator(broker); i.hasNext();) {
                final DocumentImpl confDoc = i.next();
                if (confDoc.getFileURI().endsWith(CollectionConfiguration.COLLECTION_CONFIG_SUFFIX_URI)) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Reading collection configuration from '" + confDoc.getURI() + "'");
                    }
                    final CollectionConfiguration conf = new CollectionConfiguration(broker.getBrokerPool());

                    // [ 1807744 ] Invalid collection.xconf causes a non startable database
                    // http://sourceforge.net/tracker/index.php?func=detail&aid=1807744&group_id=17691&atid=117691
                    try {
                        conf.read(broker, confDoc, false, configCollection.getURI(), confDoc.getFileURI());
                    } catch (final CollectionConfigurationException e) {
                        final String message = "Failed to read configuration document " + confDoc.getFileURI() + " in " + configCollection.getURI() + ". "
                                + e.getMessage();
                        LOG.error(message);
                    }

                    try(final ManagedLock<ReadWriteLock> writeLock = ManagedLock.acquire(lock, LockMode.WRITE_LOCK)) {
                        configurations.put(new CollectionURI(configCollection.getURI().getRawCollectionPath()), conf);
                    }

                    // Allow just one configuration document per collection
                    // TODO : do not break if a system property allows several ones -pb
                    break;
                }
            }
        }
    }

    public CollectionConfiguration getOrCreateCollectionConfiguration(final DBBroker broker, Collection collection) {
        final CollectionURI path = new CollectionURI(COLLECTION_CONFIG_PATH);
        path.append(collection.getURI().getRawCollectionPath());

        try(final ManagedLock<ReadWriteLock> readLock = ManagedLock.acquire(lock, LockMode.READ_LOCK)) {
            final CollectionConfiguration conf = configurations.get(path);
            if(conf != null) {
                return conf;
            }
        }

        try(final ManagedLock<ReadWriteLock> writeLock = ManagedLock.acquire(lock, LockMode.WRITE_LOCK)) {
            CollectionConfiguration conf = configurations.get(path);
            if (conf != null) {
                return conf;
            }

            conf = new CollectionConfiguration(broker.getBrokerPool());
            configurations.put(path, conf);

            return conf;
        }
    }

    /**
     * Notify the manager that a collection.xconf file has changed. All cached
     * configurations for the corresponding collection and its sub-collections
     * will be cleared.
     * 
     * @param collectionPath to the collection for which configuration will be invalidated
     */
    public void invalidateAll(final XmldbURI collectionPath) {

        if (!collectionPath.startsWith(CONFIG_COLLECTION_URI)) {
            return;
        }

        try(final ManagedLock<ReadWriteLock> writeLock = ManagedLock.acquire(lock, LockMode.WRITE_LOCK)) {

            if (LOG.isDebugEnabled()) {
                LOG.debug("Invalidating collection " + collectionPath + " and subcollections");
            }

            CollectionURI uri = new CollectionURI(collectionPath.getRawCollectionPath());

            configurations.remove(uri);

            String str = uri.toString();

            Iterator<Entry<CollectionURI, CollectionConfiguration>> it = configurations.entrySet().iterator();

            while (it.hasNext()) {
                Entry<CollectionURI, CollectionConfiguration> entry = it.next();

                if (entry.getKey().toString().startsWith(str)) {
                    it.remove();
                }
            }
        }
    }

    /**
     * Called by the collection cache if a collection is removed from the cache.
     * This will delete the cached configuration instance for this collection.
     * 
     * @param collectionPath to the collection for which configuration will be invalidated
     * @param pool if not null: clear query cache
     */
    public void invalidate(final XmldbURI collectionPath, final BrokerPool pool) {
        if (!collectionPath.startsWith(CONFIG_COLLECTION_URI)) {
            return;
        }

        try(final ManagedLock<ReadWriteLock> writeLock = ManagedLock.acquire(lock, LockMode.WRITE_LOCK)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Invalidating collection " + collectionPath);
            }

            configurations.remove(new CollectionURI(collectionPath.getRawCollectionPath()));
        }
    }

    /**
     * Check if the collection exists below the system collection. If not,
     * create it.
     * 
     * @param broker eXist-db broker
     * @param txn according transaction
     * @param uri to the collection to create
     * @throws EXistException if something goes wrong
     */
    private void checkCreateCollection(final DBBroker broker, final Txn txn, final XmldbURI uri) throws EXistException {
        try {
            Collection collection = broker.getCollection(uri);
            if (collection == null) {
                collection = broker.getOrCreateCollection(txn, uri);
                SanityCheck.THROW_ASSERT(collection != null);
                broker.saveCollection(txn, collection);
            }
        } catch(final TriggerException | PermissionDeniedException | IOException e) {
            throw new EXistException("Failed to initialize '" + uri + "' : " + e.getMessage());
        }
    }

    /**
     * Create a stored default configuration document for the root collection
     * 
     * @param broker The broker which will do the operation
     * @throws EXistException if something goes wrong
     * @throws PermissionDeniedException if user does not have sufficient rights
     */
    public void checkRootCollectionConfig(DBBroker broker) throws EXistException, PermissionDeniedException {
        // Copied from the legacy conf.xml in order to make the test suite work
        // TODO : backward compatibility could be ensured by copying the
        // relevant parts of conf.xml

        final String configuration = 
          "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">"
        + "    <index>"
        + "    </index>"
        + "</collection>";

        final TransactionManager transact = broker.getDatabase().getTransactionManager();
        try(final Txn txn = transact.beginTransaction()) {

            try(final Collection collection = broker.openCollection(XmldbURI.ROOT_COLLECTION_URI, LockMode.READ_LOCK)) {
                if (collection == null) {
                    transact.abort(txn);
                    throw new EXistException("collection " + XmldbURI.ROOT_COLLECTION_URI + " not found!");
                }
                final CollectionConfiguration conf = getConfiguration(collection);
                if (conf != null) {
                    // We already have a configuration document : do not erase
                    // it
                    if (conf.getDocName() != null) {
                        transact.abort(txn);
                        return;
                    }
                }

                // Configure the root collection
                addConfiguration(txn, broker, collection, configuration);
                LOG.info("Configured '" + collection.getURI() + "'");
            }

            transact.commit(txn);

        } catch (final CollectionConfigurationException e) {
            throw new EXistException(e.getMessage());
        }
    }

    /*
     * private void debugCache() { StringBuilder buf = new StringBuilder(); for
     * (Iterator i = configurations.keySet().iterator(); i.hasNext(); ) {
     * buf.append(i.next()).append(' '); } LOG.debug(buf.toString()); }
     */
}
