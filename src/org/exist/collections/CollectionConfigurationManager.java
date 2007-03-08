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

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.sanity.SanityCheck;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.SAXException;

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
    
    public final static XmldbURI CONFIG_COLLECTION_URI = XmldbURI.create(CONFIG_COLLECTION);

    private BrokerPool pool;
	
    private Map cache = new TreeMap();
    
    public CollectionConfigurationManager(DBBroker broker) throws EXistException {
		this.pool = broker.getBrokerPool();
		checkConfigCollection(broker);
    }
    
	/**
	 * Add a new collection configuration. The XML document is passed as a string.
	 * 
	 * @param broker
	 * @param collection the collection to which the configuration applies.
	 * @param config the xconf document as a String.
	 * @throws CollectionConfigurationException
	 */
    public void addConfiguration(Txn transaction, DBBroker broker, Collection collection, String config) 
    throws CollectionConfigurationException {
    	try {
			XmldbURI path = CONFIG_COLLECTION_URI.append(collection.getURI());
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
            //broker.saveCollection(transaction, confCol);
			IndexInfo info = confCol.validateXMLResource(transaction, broker, configurationDocumentName, config);
			//TODO : unlock the collection here ?
			confCol.store(transaction, broker, info, config, false);
			//broker.sync(Sync.MAJOR_SYNC);
		} catch (IOException e) {
			throw new CollectionConfigurationException("Failed to store collection configuration: " + e.getMessage(), e);
		} catch (PermissionDeniedException e) {
			throw new CollectionConfigurationException("Failed to store collection configuration: " + e.getMessage(), e);
		} catch (CollectionConfigurationException e) {
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

    	CollectionConfiguration conf = new CollectionConfiguration(broker.getBrokerPool(), collection);
        boolean configFound = false;
    	XmldbURI path = CONFIG_COLLECTION_URI.append(collection.getURI());
    	Collection coll = null;
    	/*
    	 * This used to go from the root collection (/db), and continue all the
    	 * way to the end of the path, checking each collection on the way.  I
    	 * modified it to start at the collection path and work its way back to
    	 * the root, stopping at the first config file it finds. This should be
    	 * more efficient, and fit more appropriately will the XmldbURI api
    	 */
    	while(!configFound && !path.equals(CONFIG_COLLECTION_URI)) {
    		try {
    			coll = broker.openCollection(path,Lock.READ_LOCK);
    			if (coll != null && coll.getDocumentCount() > 0) {
    			    for(Iterator i = coll.iterator(broker); i.hasNext(); ) {
    			        DocumentImpl confDoc = (DocumentImpl) i.next();
    			        if(confDoc.getFileURI().endsWith(CollectionConfiguration.COLLECTION_CONFIG_SUFFIX_URI)) {
                            if (!configFound) {
                                if (LOG.isTraceEnabled())
                                    LOG.trace("Reading collection configuration for '" + collection.getURI() + "' from '" + confDoc.getURI() + "'");
        			            conf.read(broker, confDoc, path, confDoc.getFileURI());                            
                                configFound = true;
                                //Allow just one configuration document per collection
                                //TODO : do not break if a system property allows several ones -pb
        			            break;
                            } else {
                                if (LOG.isDebugEnabled())
                                    LOG.debug("Found another collection configuration for '" + collection.getURI() + "' in '" + confDoc.getURI() + "'");
                            }
    			        }
    			    }                    
                }
    		} finally {
    			if(coll != null)
    				coll.release(Lock.READ_LOCK);
    		}
    		path = path.removeLastSegment();
	    }
        if (!configFound) {
            if (LOG.isTraceEnabled())
                LOG.trace("Reading collection configuration for '" + collection.getURI() + "' from index configuration");
            // use default configuration
            conf.setIndexConfiguration(broker.getIndexConfiguration());
        }
        
		// we synchronize on the global CollectionCache to avoid deadlocks.
		// the calling code does mostly already hold a lock on CollectionCache.
		CollectionCache collectionCache = pool.getCollectionsCache();
		synchronized (collectionCache) {
			cache.put(collection.getURI(), conf);
		}
        return conf;
    }
    
    /**
     * Notify the manager that a collection.xconf file has changed. All cached configurations
     * for the corresponding collection and its sub-collections will be cleared. 
     * 
     * @param collectionPath
     */
    protected void invalidateAll(XmldbURI collectionPath) {
        if (!collectionPath.startsWith(CONFIG_COLLECTION_URI))
    		return;
        collectionPath = collectionPath.trimFromBeginning(CONFIG_COLLECTION_URI);
		// we synchronize on the global CollectionCache to avoid deadlocks.
		// the calling code does mostly already hold a lock on CollectionCache.
		CollectionCache collectionCache = pool.getCollectionsCache();
		synchronized (collectionCache) {
	    	Map.Entry next;
	    	CollectionConfiguration config;
	    	for(Iterator i = cache.entrySet().iterator(); i.hasNext(); ) {
	    		next = (Map.Entry) i.next();
	    		if(((XmldbURI)next.getKey()).startsWith(collectionPath)) {
	    			config = (CollectionConfiguration) next.getValue();
	    			if (config != null)
	    				config.getCollection().invalidateConfiguration();
	    			i.remove();
	    		}
	    	}
		}
    }
    
    /**
     * Called by the collection cache if a collection is removed from the cache.
     * This will delete the cached configuration instance for this collection.
     * 
     * @param collectionPath
     */
    protected void invalidate(XmldbURI collectionPath) {
    	if (!collectionPath.startsWith(CONFIG_COLLECTION_URI))
    		return;
    	collectionPath = collectionPath.trimFromBeginning(CONFIG_COLLECTION_URI);
		CollectionCache collectionCache = pool.getCollectionsCache();
		synchronized (collectionCache) {
	    	CollectionConfiguration config = (CollectionConfiguration) cache.get(collectionPath);
	    	if (config != null) {
	    		config.getCollection().invalidateConfiguration();
	    		cache.remove(collectionPath);
	    	}
		}
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
    		Collection root = broker.getCollection(CONFIG_COLLECTION_URI);
    		if(root == null) {
    			txn = transact.beginTransaction();
    			root = broker.getOrCreateCollection(txn, CONFIG_COLLECTION_URI);
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
}
