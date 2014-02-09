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

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.DocumentImpl;
import org.exist.memtree.SAXAdapter;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.IndexSpec;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.Locked;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.sanity.SanityCheck;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import java.io.StringReader;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

/**
 * Manages index configurations. Index configurations are stored in a collection
 * hierarchy below /db/system/config. CollectionConfigurationManager is called
 * by {@link org.exist.collections.Collection} to retrieve the
 * {@link org.exist.collections.CollectionConfiguration} instance for a given
 * collection.
 * 
 * @author wolf
 */
public class CollectionConfigurationManager {

    private static final Logger LOG = Logger.getLogger(CollectionConfigurationManager.class);

    public final static String CONFIG_COLLECTION = XmldbURI.SYSTEM_COLLECTION + "/config";

    /** /db/system/config **/
    public final static XmldbURI CONFIG_COLLECTION_URI = XmldbURI.create(CONFIG_COLLECTION);

    /** /db/system/config/db **/
    public final static XmldbURI ROOT_COLLECTION_CONFIG_URI = CONFIG_COLLECTION_URI.append(XmldbURI.ROOT_COLLECTION_NAME);

    public final static String COLLECTION_CONFIG_FILENAME = "collection.xconf";

    public final static CollectionURI COLLECTION_CONFIG_PATH = new CollectionURI(CONFIG_COLLECTION_URI.getRawCollectionPath());

    private Map<CollectionURI, CollectionConfiguration> configurations = new HashMap<CollectionURI, CollectionConfiguration>();

    private Locked latch = new Locked();

    private CollectionConfiguration defaultConfig;

    public CollectionConfigurationManager(DBBroker broker) throws EXistException, CollectionConfigurationException, PermissionDeniedException, LockException {

        checkCreateCollection(broker, CONFIG_COLLECTION_URI);
        checkCreateCollection(broker, ROOT_COLLECTION_CONFIG_URI);

        loadAllConfigurations(broker);

        defaultConfig = new CollectionConfiguration(broker.getBrokerPool());
        defaultConfig.setIndexConfiguration(broker.getIndexConfiguration());
    }

    /**
     * Add a new collection configuration. The XML document is passed as a
     * string.
     * 
     * @param txn
     *            The transaction that will hold the WRITE locks until they are
     *            released by commit()/abort()
     * @param broker
     * @param collection
     *            the collection to which the configuration applies.
     * @param config
     *            the xconf document as a String.
     * @throws CollectionConfigurationException
     */
    public void addConfiguration(Txn txn, final DBBroker broker, Collection collection, String config) throws CollectionConfigurationException {
        try {
            final XmldbURI path = CONFIG_COLLECTION_URI.append(collection.getURI());

            final Collection confCol = broker.getOrCreateCollection(txn, path);
            if (confCol == null) {
                throw new CollectionConfigurationException("Failed to create config collection: " + path);
            }

            XmldbURI configurationDocumentName = null;
            // Replaces the current configuration file if there is one
            final CollectionConfiguration conf = getConfiguration(broker, collection);
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
            confCol.store(txn, broker, info, config, false);
            // broker.sync(Sync.MAJOR_SYNC);

            latch.writeE(new Callable<Void>() {
                @Override
                public Void call() throws Exception {

                    configurations.remove(new CollectionURI(path.getRawCollectionPath()));
                    loadConfiguration(broker, confCol);

                    return null;
                }
            });

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
     * @param broker
     *            DBBroker
     * @param config
     *            the configuration to test
     * @throws CollectionConfigurationException
     *             if errors were detected
     */
    public void testConfiguration(DBBroker broker, String config) throws CollectionConfigurationException {
        try {
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            final InputSource src = new InputSource(new StringReader(config));
            final SAXParser parser = factory.newSAXParser();
            final XMLReader reader = parser.getXMLReader();
            final SAXAdapter adapter = new SAXAdapter();
            reader.setContentHandler(adapter);
            reader.parse(src);

            final Document doc = adapter.getDocument();
            final CollectionConfiguration conf = new CollectionConfiguration(broker.getBrokerPool());
            conf.read(broker, doc, true, null, null);
        } catch (final Exception e) {
            throw new CollectionConfigurationException(e);
        }
    }

    public List<Object> getCustomIndexSpecs(final String customIndexId) {
        
        return latch.read(new Callable<List<Object>>() {

            @Override
            public List<Object> call() throws Exception {

                List<Object> configs = new ArrayList<Object>(10);

                for (CollectionConfiguration config: configurations.values()) {
                    IndexSpec spec = config.getIndexConfiguration();
                    if (spec != null) {
                        Object customConfig = spec.getCustomIndexSpec(customIndexId);
                        if (customConfig != null) {
                            configs.add(customConfig);
                        }
                    }
                }

                return configs;
            }
        });
    }

    /**
     * Retrieve the collection configuration instance for the given collection.
     * This creates a new CollectionConfiguration object and recursively scans
     * the collection hierarchy for available configurations.
     * 
     * @param broker
     * @param collection
     * @return The collection configuration
     * @throws CollectionConfigurationException
     */
    protected CollectionConfiguration getConfiguration(DBBroker broker, Collection collection) throws CollectionConfigurationException {

        final CollectionURI path = new CollectionURI(COLLECTION_CONFIG_PATH);
        path.append(collection.getURI().getRawCollectionPath());

        /*
         * This used to go from the root collection (/db), and continue all the
         * way to the end of the path, checking each collection on the way. I
         * modified it to start at the collection path and work its way back to
         * the root, stopping at the first config file it finds. This should be
         * more efficient, and fit more appropriately will the XmldbURI api
         */
        return latch.read(new Callable<CollectionConfiguration>() {

            @Override
            public CollectionConfiguration call() throws Exception {

                CollectionConfiguration conf = null;

                while(!path.equals(COLLECTION_CONFIG_PATH)) {
                    conf = configurations.get(path);
                    if (conf != null) {
                        return conf;
                    }
                    path.removeLastSegment();
                }

                // use default configuration
                return defaultConfig;
            }
        });
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
                        System.out.println(message);
                    }
                    
                    latch.write(new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {

                            configurations.put(new CollectionURI(configCollection.getURI().getRawCollectionPath()), conf);

                            return null;
                        }
                    });

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
        
        CollectionConfiguration conf = latch.read(new Callable<CollectionConfiguration>() {
            @Override
            public CollectionConfiguration call() {
                return configurations.get(path);
            }
        });

        if (conf != null) {
            return conf;
        }
        
        return latch.write(new Callable<CollectionConfiguration>() {
            @Override
            public CollectionConfiguration call() {
                
                CollectionConfiguration conf = configurations.get(path);
                
                if (conf != null) {
                    return conf;
                }

                conf = new CollectionConfiguration(broker.getBrokerPool());
                configurations.put(path, conf);

                return conf;
            }
        });
    }

    /**
     * Notify the manager that a collection.xconf file has changed. All cached
     * configurations for the corresponding collection and its sub-collections
     * will be cleared.
     * 
     * @param collectionPath
     */
    public void invalidateAll(final XmldbURI collectionPath) {

        if (!collectionPath.startsWith(CONFIG_COLLECTION_URI)) {
            return;
        }
        
        latch.write(new Callable<Void>() {
            @Override
            public Void call() {

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

                return null;
            }
        });
    }

    /**
     * Called by the collection cache if a collection is removed from the cache.
     * This will delete the cached configuration instance for this collection.
     * 
     * @param collectionPath
     * @param pool if not null: clear query cache
     */
    public void invalidate(final XmldbURI collectionPath, final BrokerPool pool) {
        if (!collectionPath.startsWith(CONFIG_COLLECTION_URI)) {
            return;
        }

        latch.write(new Callable<Void>() {
            @Override
            public Void call() {

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Invalidating collection " + collectionPath);
                }

                configurations.remove(new CollectionURI(collectionPath.getRawCollectionPath()));
                return null;
            }
        });
    }

    /**
     * Check if the collection exists below the system collection. If not,
     * create it.
     * 
     * @param broker
     * @param uri
     * @throws EXistException
     */
    private void checkCreateCollection(DBBroker broker, XmldbURI uri) throws EXistException {
        final TransactionManager transact = broker.getDatabase().getTransactionManager();
        Txn txn = null;
        try {
            Collection collection = broker.getCollection(uri);
            if (collection == null) {
                txn = transact.beginTransaction();
                collection = broker.getOrCreateCollection(txn, uri);
                SanityCheck.THROW_ASSERT(collection != null);
                broker.saveCollection(txn, collection);
                transact.commit(txn);
            }
        } catch (final Exception e) {
            transact.abort(txn);
            throw new EXistException("Failed to initialize '" + uri + "' : " + e.getMessage());
        } finally {
            transact.close(txn);
        }
    }

    /**
     * Create a stored default configuration document for the root collection
     * 
     * @param broker
     *            The broker which will do the operation
     * @throws EXistException
     */
    public void checkRootCollectionConfig(DBBroker broker) throws EXistException, PermissionDeniedException {
        // Copied from the legacy conf.xml in order to make the test suite work
        // TODO : backward compatibility could be ensured by copying the
        // relevant parts of conf.xml

        final String configuration = 
          "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">"
        + "    <index>"
        + "        <fulltext attributes=\"true\" default=\"all\">"
        + "            <exclude path=\"/auth\" />"
        + "        </fulltext>"
        + "    </index>"
        + "</collection>";

        final TransactionManager transact = broker.getDatabase().getTransactionManager();
        final Txn txn = transact.beginTransaction();
        try {
            Collection collection = null;
            try {
                collection = broker.openCollection(XmldbURI.ROOT_COLLECTION_URI, Lock.READ_LOCK);
                if (collection == null) {
                    transact.abort(txn);
                    throw new EXistException("collection " + XmldbURI.ROOT_COLLECTION_URI + " not found!");
                }
                final CollectionConfiguration conf = getConfiguration(broker, collection);
                if (conf != null) {
                    // We already have a configuration document : do not erase
                    // it
                    if (conf.getDocName() != null) {
                        transact.abort(txn);
                        return;
                    }
                }
            } finally {
                if (collection != null) {
                    collection.release(Lock.READ_LOCK);
                }
            }
            // Configure the root collection
            addConfiguration(txn, broker, collection, configuration);
            transact.commit(txn);
            LOG.info("Configured '" + collection.getURI() + "'");
        } catch (final CollectionConfigurationException e) {
            transact.abort(txn);
            throw new EXistException(e.getMessage());
        } finally {
            transact.close(txn);
        }
    }

    /*
     * private void debugCache() { StringBuilder buf = new StringBuilder(); for
     * (Iterator i = configurations.keySet().iterator(); i.hasNext(); ) {
     * buf.append(i.next()).append(' '); } LOG.debug(buf.toString()); }
     */
}