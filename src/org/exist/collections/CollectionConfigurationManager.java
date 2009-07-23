/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.collections;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DocumentImpl;
import org.exist.memtree.SAXAdapter;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.sanity.SanityCheck;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Manages index configurations. Index configurations are stored in a collection
 * hierarchy below /db/system/config. CollectionConfigurationManager is called
 * by {@link org.exist.collections.Collection} to retrieve the
 * {@link org.exist.collections.CollectionConfiguration} instance for a given collection.
 * 
 * @author wolf
 */
public class CollectionConfigurationManager {

	private static final Logger LOG = Logger.getLogger(CollectionConfigurationManager.class);
	
    public final static String CONFIG_COLLECTION = DBBroker.SYSTEM_COLLECTION + "/config";
    public final static String COLLECTION_CONFIG_FILENAME = "collection.xconf";

    public final static CollectionURI COLLECTION_CONFIG_PATH = new CollectionURI(XmldbURI.CONFIG_COLLECTION_URI.getRawCollectionPath());
    
    private Map configurations = new HashMap();

    private Object latch;

    private CollectionConfiguration defaultConfig;

    private BrokerPool pool;
    
    public CollectionConfigurationManager(DBBroker broker) throws EXistException, CollectionConfigurationException {
		this.pool = broker.getBrokerPool();
        this.latch = pool.getCollectionsCache();
        checkConfigCollection(broker);
        loadAllConfigurations(broker);
        defaultConfig = new CollectionConfiguration(broker.getBrokerPool());
        defaultConfig.setIndexConfiguration(broker.getIndexConfiguration());
    }

    /**
	 * Add a new collection configuration. The XML document is passed as a string.
	 * 
     * @param transaction The transaction that will hold the WRITE locks until they are released by commit()/abort()
     * @param broker
	 * @param collection the collection to which the configuration applies.
	 * @param config the xconf document as a String.
	 * @throws CollectionConfigurationException
	 */
    public void addConfiguration(Txn transaction, DBBroker broker, Collection collection, String config)
    throws CollectionConfigurationException {
        try {
    		//TODO : use XmldbURI.resolve() !
			XmldbURI path = XmldbURI.CONFIG_COLLECTION_URI.append(collection.getURI());
			Collection confCol = broker.getOrCreateCollection(transaction, path);
			if(confCol == null)
				throw new CollectionConfigurationException("Failed to create config collection: " + path);
			XmldbURI configurationDocumentName = null;
            //Replaces the current configuration file if there is one
            CollectionConfiguration conf = getConfiguration(broker, collection);
            if (conf != null) {
                configurationDocumentName = conf.getDocName();
                if (configurationDocumentName != null) 
                    LOG.warn("Replacing current configuration file '" + configurationDocumentName + "'");
            }
            if (configurationDocumentName == null)
                configurationDocumentName = CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE_URI;
            broker.saveCollection(transaction, confCol);
			IndexInfo info = confCol.validateXMLResource(transaction, broker, configurationDocumentName, config);
			//TODO : unlock the collection here ?
			confCol.store(transaction, broker, info, config, false);
			//broker.sync(Sync.MAJOR_SYNC);

            synchronized (latch) {
                configurations.remove(new CollectionURI(path.getRawCollectionPath()));
                loadConfiguration(broker, confCol);
            }
        } catch (IOException e) {
			throw new CollectionConfigurationException("Failed to store collection configuration: " + e.getMessage(), e);
		} catch (PermissionDeniedException e) {
			throw new CollectionConfigurationException("Failed to store collection configuration: " + e.getMessage(), e);
		} catch (EXistException e) {
			throw new CollectionConfigurationException("Failed to store collection configuration: " + e.getMessage(), e);
		} catch (TriggerException e) {
			throw new CollectionConfigurationException("Failed to store collection configuration: " + e.getMessage(), e);
		} catch (SAXException e) {
			throw new CollectionConfigurationException("Failed to store collection configuration: " + e.getMessage(), e);
		} catch (LockException e) {
			throw new CollectionConfigurationException("Failed to store collection configuration: " + e.getMessage(), e);
		}
    }

    /**
     * Check the passed collection configuration. Throws an exception if errors are detected in the
     * configuration document. Note: some configuration settings depend on the current environment, in particular
     * the availability of trigger or index classes.
     *
     * @param broker DBBroker
     * @param config the configuration to test
     * @throws CollectionConfigurationException if errors were detected
     */
    public void testConfiguration(DBBroker broker, String config) throws CollectionConfigurationException {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            InputSource src = new InputSource(new StringReader(config));
            SAXParser parser = factory.newSAXParser();
            XMLReader reader = parser.getXMLReader();
            SAXAdapter adapter = new SAXAdapter();
            reader.setContentHandler(adapter);
            reader.parse(src);

            Document doc = adapter.getDocument();
            CollectionConfiguration conf = new CollectionConfiguration(broker.getBrokerPool());
            conf.read(broker, doc, true, null, null);
        } catch (ParserConfigurationException e) {
            throw new CollectionConfigurationException(e.getMessage(), e);
        } catch (SAXException e) {
            throw new CollectionConfigurationException(e.getMessage(), e);
        } catch (IOException e) {
            throw new CollectionConfigurationException(e.getMessage(), e);
        }
    }

    /**
     * Retrieve the collection configuration instance for the given collection. This
     * creates a new CollectionConfiguration object and recursively scans the collection
     * hierarchy for available configurations.
     * 
     * @param broker
     * @param collection
     * @return The collection configuration
     * @throws CollectionConfigurationException
     */
    protected CollectionConfiguration getConfiguration(DBBroker broker, Collection collection) 
        throws CollectionConfigurationException {
        CollectionURI path = new CollectionURI(COLLECTION_CONFIG_PATH);
        path.append(collection.getURI().getRawCollectionPath());

    	/*
    	 * This used to go from the root collection (/db), and continue all the
    	 * way to the end of the path, checking each collection on the way.  I
    	 * modified it to start at the collection path and work its way back to
    	 * the root, stopping at the first config file it finds. This should be
    	 * more efficient, and fit more appropriately will the XmldbURI api
    	 */
        CollectionConfiguration conf;

        synchronized (latch) {
            while(!path.equals(COLLECTION_CONFIG_PATH)) {
                conf = (CollectionConfiguration) configurations.get(path);
                if (conf != null)
                    return conf;
                path.removeLastSegment();
            }
        }
        // use default configuration
        return defaultConfig;
    }

    protected void loadAllConfigurations(DBBroker broker) throws CollectionConfigurationException {
        Collection root = broker.getCollection(XmldbURI.CONFIG_COLLECTION_URI);
        loadAllConfigurations(broker, root);
    }

    protected void loadAllConfigurations(DBBroker broker, Collection configCollection) throws CollectionConfigurationException {
        if (configCollection == null)
            return;
        loadConfiguration(broker, configCollection);
        XmldbURI path = configCollection.getURI();
        for (Iterator i = configCollection.collectionIterator(); i.hasNext(); ) {
            XmldbURI childName = (XmldbURI) i.next();
            Collection child = broker.getCollection(path.appendInternal(childName));
            if (child == null)
                LOG.error("Collection is registered but could not be loaded: " + childName);
            loadAllConfigurations(broker, child);
        }
    }

    protected void loadConfiguration(DBBroker broker, Collection configCollection) throws CollectionConfigurationException {
        if (configCollection != null && configCollection.getDocumentCount() > 0) {
            for(Iterator i = configCollection.iterator(broker); i.hasNext(); ) {
                DocumentImpl confDoc = (DocumentImpl) i.next();
                if(confDoc.getFileURI().endsWith(CollectionConfiguration.COLLECTION_CONFIG_SUFFIX_URI)) {
                    if (LOG.isTraceEnabled())
                        LOG.trace("Reading collection configuration from '" + confDoc.getURI() + "'");
                    CollectionConfiguration conf = new CollectionConfiguration(broker.getBrokerPool());

                    // TODO DWES Temporary workaround for bug 
                    // [ 1807744 ] Invalid collection.xconf causes a non startable database
                    // http://sourceforge.net/tracker/index.php?func=detail&aid=1807744&group_id=17691&atid=117691
                    try {
                        conf.read(broker, confDoc, false, configCollection.getURI(), confDoc.getFileURI());
                    } catch (CollectionConfigurationException e) {
                        String message = "Failed to read configuration document " + confDoc.getFileURI() + " in "
                                + configCollection.getURI() + ". "
                                + e.getMessage();
                        LOG.error(message);
                        System.out.println(message);
                    }

                    synchronized (latch) {
                        configurations.put(new CollectionURI(configCollection.getURI().getRawCollectionPath()), conf);
                    }
                    //Allow just one configuration document per collection
                    //TODO : do not break if a system property allows several ones -pb
                    break;
                }
            }
        }
    }

    /**
     * Notify the manager that a collection.xconf file has changed. All cached configurations
     * for the corresponding collection and its sub-collections will be cleared. 
     * 
     * @param collectionPath
     */
    public void invalidateAll(XmldbURI collectionPath) {
        //TODO : use XmldbURI.resolve !
        if (!collectionPath.startsWith(XmldbURI.CONFIG_COLLECTION_URI))
    		return;
        synchronized (latch) {
            LOG.debug("Invalidating collection " + collectionPath);
            configurations.remove(new CollectionURI(collectionPath.getRawCollectionPath()));
        }
    }
    
    /**
     * Called by the collection cache if a collection is removed from the cache.
     * This will delete the cached configuration instance for this collection.
     * 
     * @param collectionPath
     */
    protected void invalidate(XmldbURI collectionPath) {
    	//TODO : use XmldbURI.resolve !
//    	if (!collectionPath.startsWith(XmldbURI.CONFIG_COLLECTION_URI))
//    		return;
//    	collectionPath = collectionPath.trimFromBeginning(XmldbURI.CONFIG_COLLECTION_URI);
//		CollectionCache collectionCache = pool.getCollectionsCache();
//		synchronized (collectionCache) {
//	    	CollectionConfiguration config = (CollectionConfiguration) cache.get(collectionPath);
//	    	if (config != null) {
//	    		config.getCollection().invalidateConfiguration();
//	    		cache.remove(collectionPath);
//	    	}
//		}
    }
    
	/**
	 * Check if the config collection exists below the system collection. If not, create it.
	 * 
	 * @param broker
	 * @throws EXistException
	 */
    private void checkConfigCollection(DBBroker broker) throws EXistException {
        TransactionManager transact = pool.getTransactionManager();
        Txn txn = null;
    	try {
    		Collection root = broker.getCollection(XmldbURI.CONFIG_COLLECTION_URI);
    		if(root == null) {
    			txn = transact.beginTransaction();
    			root = broker.getOrCreateCollection(txn, XmldbURI.CONFIG_COLLECTION_URI);
                SanityCheck.THROW_ASSERT(root != null);
    			broker.saveCollection(txn, root);
                transact.commit(txn);
    		}
    	} catch (IOException e) {
    		transact.abort(txn);
    		throw new EXistException("Failed to initialize '" + CONFIG_COLLECTION + "' : " + e.getMessage());
    	} catch (PermissionDeniedException e) {
    		transact.abort(txn);
    		throw new EXistException("Failed to initialize '" + CONFIG_COLLECTION + "' : " + e.getMessage());
    	}
    }

    public void checkRootCollectionConfigCollection(DBBroker broker) throws EXistException {
        TransactionManager transact = pool.getTransactionManager();
        Txn txn = null;
    	try {
    		//Create a configuration collection for the root collection
    		Collection rootCollectionConfiguration = broker.getCollection(XmldbURI.ROOT_COLLECTION_CONFIG_URI);
    		if(rootCollectionConfiguration == null) {
    			txn = transact.beginTransaction();
    			rootCollectionConfiguration = broker.getOrCreateCollection(txn, XmldbURI.ROOT_COLLECTION_CONFIG_URI);
                SanityCheck.THROW_ASSERT(rootCollectionConfiguration != null);
    			broker.saveCollection(txn, rootCollectionConfiguration);    			
                transact.commit(txn);
    		}    		
    	} catch (IOException e) {
    		transact.abort(txn);
    		throw new EXistException("Failed to initialize '" + CONFIG_COLLECTION + "' : " + e.getMessage());
    	} catch (PermissionDeniedException e) {
    		transact.abort(txn);
    		throw new EXistException("Failed to initialize '" + CONFIG_COLLECTION + "' : " + e.getMessage());
    	}
    }
    
    /** Create a stored default configuration document for the root collection 
     * @param broker The broker which will do the operation
     * @throws EXistException
     */
    public void checkRootCollectionConfig(DBBroker broker) throws EXistException {
    	String configuration = 
			"<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
	    	"    <index>" +
	    	
	    	//Copied from the legacy conf.xml in order to make the test suite work
	    	//TODO : backward compatibility could be ensured by copying the relevant parts of conf.xml
            "        <fulltext attributes=\"true\" default=\"all\">" +
            "            <exclude path=\"/auth\" />" +
            "        </fulltext>" +
            
	    	"    </index>" +
	    	"</collection>";
    	
        TransactionManager transact = pool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {    
            Collection collection = null;
        	try {
	            collection = broker.openCollection(XmldbURI.ROOT_COLLECTION_URI, Lock.READ_LOCK);
	            if (collection == null) {
	                transact.abort(transaction);
	                throw new EXistException("collection " + XmldbURI.ROOT_COLLECTION_URI + " not found!");
	            }
	            CollectionConfiguration conf = getConfiguration(broker, collection);
	            if (conf != null) {
	            	//We already have a configuration document : do not erase it
	                if (conf.getDocName() != null) { 
	                	transact.abort(transaction);
	                    return;   
	                }
	            }
            } finally {
            	if (collection != null)
            		collection.release(Lock.READ_LOCK);
            }
            //Configure the root collection
            addConfiguration(transaction, broker, collection, configuration);
            transact.commit(transaction);
            LOG.info("Configured '" + collection.getURI() + "'");  
        } catch (CollectionConfigurationException e) {
            transact.abort(transaction);
            throw new EXistException(e.getMessage());
        } 
    }

    private void debugCache() {
        StringBuilder buf = new StringBuilder();
        for (Iterator i = configurations.keySet().iterator(); i.hasNext(); ) {
            buf.append(i.next()).append(' ');
        }
        LOG.debug(buf.toString());
    }
}