
/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  meier@ifs.tu-darmstadt.de
 *  http://exist.sourceforge.net
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
package org.exist.xmldb;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.Random;

import javax.xml.transform.OutputKeys;

import org.apache.log4j.Category;
import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.LockException;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.Service;
import org.xmldb.api.base.XMLDBException;

/**
 *  A local implementation of the Collection interface. This
 * is used when the database is running in embedded mode.
 *
 * Extends Observable to allow status callbacks during indexing.
 * Methods storeResource notifies registered observers about the
 * progress of the indexer by passing an object of type ProgressIndicator
 * to the observer.
 * 
 *@author     wolf
 */
public class LocalCollection extends Observable implements CollectionImpl {

	private static Logger LOG = Logger.getLogger(LocalCollection.class.getName());

	protected final static Properties defaultProperties = new Properties();
	static {
		defaultProperties.setProperty(OutputKeys.ENCODING, "UTF-8");
		defaultProperties.setProperty(OutputKeys.INDENT, "yes");
		defaultProperties.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "yes");
		defaultProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "no");
	}

	protected String path = null;
	protected BrokerPool brokerPool = null;
	protected Properties properties = new Properties(defaultProperties);
	protected LocalCollection parent = null;
	protected User user = null;
	protected ArrayList observers = new ArrayList(1);
	protected boolean needsSync = false;

	private XMLReader userReader = null;

	/**
	 * Create a collection with no parent (root collection).
	 * 
	 * @param user
	 * @param brokerPool
	 * @param collection
	 * @throws XMLDBException
	 */
	public LocalCollection(User user, BrokerPool brokerPool, String collection)
		throws XMLDBException {
		this(user, brokerPool, null, collection);
	}

	/**
	 * Create a collection identified by its name. Load the collection from the database.
	 * 
	 * @param user
	 * @param brokerPool
	 * @param parent
	 * @param name
	 * @throws XMLDBException
	 */
	public LocalCollection(
		User user,
		BrokerPool brokerPool,
		LocalCollection parent,
		String name)
		throws XMLDBException {
		if (user == null)
			user = new User("guest", "guest", "guest");
		this.user = user;
		this.parent = parent;
		this.brokerPool = brokerPool;
		this.path = name;
		if (path == null)
			path = "/db";
		getCollection();
	}

	protected Collection getCollection() throws XMLDBException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			org.exist.collections.Collection collection = broker.getCollection(path);
			if (collection == null)
				throw new XMLDBException(
					ErrorCodes.NO_SUCH_COLLECTION,
					"collection not found");
			
			collection.setReader(userReader);
			
			return collection;
		} catch (EXistException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		} finally {
			brokerPool.release(broker);
		}
	}

	protected boolean checkOwner(User user) throws XMLDBException {
		return user.getName().equals(getCollection().getPermissions().getOwner());
	}

	protected boolean checkPermissions(int perm) throws XMLDBException {
		return getCollection().getPermissions().validate(user, perm);
	}

	/**
	 * Close the current collection. Calling this method will flush all
	 * open buffers to disk.
	 */
	public void close() throws XMLDBException {
		if (needsSync) {
			DBBroker broker = null;
			try {
				broker = brokerPool.get(user);
				broker.sync();
			} catch (EXistException e) {
				throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
			} finally {
				brokerPool.release(broker);
			}
		}
	}

	public String createId() throws XMLDBException {
		String id;
		Random rand = new Random();
		boolean ok;
		do {
			ok = true;
			id = Integer.toHexString(rand.nextInt()) + ".xml";
			// check if this id does already exist
			if (getCollection().hasDocument(id))
				ok = false;

			if (getCollection().hasSubcollection(id))
				ok = false;

		} while (!ok);
		return id;
	}

	public Resource createResource(String id, String type) throws XMLDBException {
		if (id == null)
			id = createId();

		Resource r = null;
		if (type.equals("XMLResource"))
			r = new LocalXMLResource(user, brokerPool, this, id, -1);
		else if (type.equals("BinaryResource"))
			r = new LocalBinaryResource(user, brokerPool, this, id);
		else
			throw new XMLDBException(
				ErrorCodes.INVALID_RESOURCE,
				"unknown resource type: " + type);
		return r;
	}

	public org.xmldb.api.base.Collection getChildCollection(String name) throws XMLDBException {
		if (!checkPermissions(Permission.READ))
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				"you are not allowed to access this collection");
		String cname;
		for (Iterator i = getCollection().collectionIterator(); i.hasNext();) {
			cname = (String) i.next();
			if (cname.equals(name)) {
				cname = getPath() + '/' + cname;
				return new LocalCollection(user, brokerPool, this, cname);
			}
		}
		return null;
	}

	public int getChildCollectionCount() throws XMLDBException {
	    Collection collection = getCollection();
		if (collection.getPermissions().validate(user, Permission.READ))
			return collection.getChildCollectionCount();
		else
			return 0;
	}

	public String getName() throws XMLDBException {
		return getCollection().getName();
	}

	public org.xmldb.api.base.Collection getParentCollection() throws XMLDBException {
		if (getName().equals("/db"))
			return null;
		Collection collection = getCollection();
		if (parent == null && collection != null) {
			DBBroker broker = null;
			try {
				broker = brokerPool.get(user);
				org.exist.collections.Collection c = collection.getParent(broker);
				parent = new LocalCollection(user, brokerPool, null, c.getName());
			} catch (EXistException e) {
				throw new XMLDBException(
					ErrorCodes.UNKNOWN_ERROR,
					"error while retrieving parent collection: " + e.getMessage(),
					e);
			} finally {
				brokerPool.release(broker);
			}
		}
		return parent;
	}

	public String getPath() throws XMLDBException {
		return path;
	}

	public String getProperty(String property) throws XMLDBException {
		return properties.getProperty(property);
	}

	public Resource getResource(String id) throws XMLDBException {
	    Collection collection = getCollection();
		if (!collection.getPermissions().validate(user, Permission.READ))
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				"not allowed to read collection");
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			DocumentImpl document = collection.getDocument(broker, id);
			if (document == null) {
			    LOG.warn("Resource " + id + " not found");
				return null;
			}
			Resource r;
			if (document.getResourceType() == DocumentImpl.XML_FILE)
				r = new LocalXMLResource(user, brokerPool, this, id, -1);
			else if (document.getResourceType() == DocumentImpl.BINARY_FILE)
				r = new LocalBinaryResource(user, brokerPool, this, id);
			else
				throw new XMLDBException(
					ErrorCodes.INVALID_RESOURCE,
					"unknown resource type");
			return r;
		} catch (EXistException e) {
		    throw new XMLDBException(
					ErrorCodes.UNKNOWN_ERROR,
					"error while retrieving resource: " + e.getMessage(),
					e);
        } finally {
		    brokerPool.release(broker);
		}
	}

	public int getResourceCount() throws XMLDBException {
	    Collection collection = getCollection();
		if (!collection.getPermissions().validate(user, Permission.READ))
			return 0;
		else
			return collection.getDocumentCount();
	}

	public Service getService(String name, String version) throws XMLDBException {
		if (name.equals("XPathQueryService"))
			return new LocalXPathQueryService(user, brokerPool, this);

		if (name.equals("XQueryService"))
			return new LocalXPathQueryService(user, brokerPool, this);

		if (name.equals("CollectionManagementService")
			|| name.equals("CollectionManager"))
			return new LocalCollectionManagementService(user, brokerPool, this);

		if (name.equals("UserManagementService"))
			return new LocalUserManagementService(user, brokerPool, this);

		if (name.equals("DatabaseInstanceManager"))
			return new LocalDatabaseInstanceManager(user, brokerPool);

		if (name.equals("XUpdateQueryService"))
			return new LocalXUpdateQueryService(user, brokerPool, this);

		if (name.equals("IndexQueryService"))
			return new LocalIndexQueryService(user, brokerPool, this);

		throw new XMLDBException(ErrorCodes.NO_SUCH_SERVICE);
	}

	public Service[] getServices() throws XMLDBException {
		Service[] services = new Service[6];
		services[0] = new LocalXPathQueryService(user, brokerPool, this);
		services[1] = new LocalCollectionManagementService(user, brokerPool, this);
		services[2] = new LocalUserManagementService(user, brokerPool, this);
		services[3] = new LocalDatabaseInstanceManager(user, brokerPool);
		services[4] = new LocalXUpdateQueryService(user, brokerPool, this);
		services[5] = new LocalIndexQueryService(user, brokerPool, this);
		return services; // jmv null;
	}

	public boolean isOpen() throws XMLDBException {
		return true;
	}

	public String[] listChildCollections() throws XMLDBException {
	    Collection collection = getCollection();
		if (!checkPermissions(Permission.READ))
			return new String[0];
		String[] collections = new String[collection.getChildCollectionCount()];
		int j = 0;
		for (Iterator i = collection.collectionIterator(); i.hasNext(); j++)
			collections[j] = (String) i.next();
		return collections;
	}
	
	public String[] getChildCollections() throws XMLDBException {
		return listChildCollections();
	}

	public String[] listResources() throws XMLDBException {
	    Collection collection = getCollection();
		if (!collection.getPermissions().validate(user, Permission.READ))
			return new String[0];
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			String[] resources = new String[collection.getDocumentCount()];
			int j = 0;
			int p;
			DocumentImpl doc;
			String resource;
			for (Iterator i = collection.iterator(broker); i.hasNext(); j++) {
				doc = (DocumentImpl) i.next();
				resources[j] = doc.getFileName();
			}
			return resources;
		} catch (EXistException e) {
		    throw new XMLDBException(
					ErrorCodes.UNKNOWN_ERROR,
					"error while retrieving resource: " + e.getMessage(),
					e);
        } finally {
		    brokerPool.release(broker);
		}
	}
	
	public String[] getResources() throws XMLDBException {
		return listResources();
	}

	public void registerService(Service serv) throws XMLDBException {
		throw new XMLDBException(ErrorCodes.NOT_IMPLEMENTED);
	}

	public void removeResource(Resource res) throws XMLDBException {
		if (res == null)
			return;
		Collection collection = getCollection();
		String name = res.getId();
		LOG.debug("removing " + name);
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			DocumentImpl doc = collection.getDocument(broker, name);
			if (doc == null)
				throw new XMLDBException(
					ErrorCodes.INVALID_RESOURCE,
					"resource " + name + " not found");
			if (res.getResourceType().equals("XMLResource"))
				collection.removeDocument(broker, name);
			else
				collection.removeBinaryResource(broker, name);
		} catch (EXistException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		} catch (PermissionDeniedException e) {
			throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
		} catch (TriggerException e) {
			throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e.getMessage(), e);
		} catch (LockException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
					"Failed to acquire lock on collections.dbx", e);
		} finally {
			brokerPool.release(broker);
		}
		needsSync = true;
	}

	public void setProperty(String property, String value) throws XMLDBException {
		properties.setProperty(property, value);
	}

	public void storeResource(Resource resource) throws XMLDBException {
		if (resource.getResourceType().equals("XMLResource")) {
			LOG.debug("storing document " + resource.getId());
			storeXMLResource((LocalXMLResource) resource);
		} else if (resource.getResourceType().equals("BinaryResource")) {
			LOG.debug("storing binary resource " + resource.getId());
			storeBinaryResource((LocalBinaryResource) resource);
		} else
			throw new XMLDBException(
				ErrorCodes.UNKNOWN_RESOURCE_TYPE,
				"unknown resource type: " + resource.getResourceType());
		needsSync = true;
	}

	private void storeBinaryResource(LocalBinaryResource res) throws XMLDBException {
	    Collection collection = getCollection();
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			BinaryDocument blob =
				collection.addBinaryResource(
					broker,
					res.getId(),
					(byte[]) res.getContent());
		} catch (Exception e) {
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				"Exception while storing binary resource: " + e.getMessage(),
				e);
		} finally {
			brokerPool.release(broker);
		}
	}

	private void storeXMLResource(LocalXMLResource res) throws XMLDBException {
	    Collection collection = getCollection();
		DBBroker broker = null;
		String name = res.getDocumentId();
		try {
			broker = brokerPool.get(user);
			//broker.flush();
			Observer observer;
			for (Iterator i = observers.iterator(); i.hasNext();) {
				observer = (Observer) i.next();
				collection.addObserver(observer);
			}
			DocumentImpl newDoc;
			if (res.file != null) {
				String uri = new URI(res.file.toURL().toString()).toASCIIString();
				newDoc =
					collection.addDocument(
						broker,
						name,
						new InputSource(uri));
			} else if (res.root != null)
				newDoc = collection.addDocument(broker, name, res.root);
			else
				newDoc = collection.addDocument(broker, name, res.content);
			//broker.flush();
		} catch (Exception e) {
			e.printStackTrace();
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		} finally {
			collection.deleteObservers();
			brokerPool.release(broker);
		}
	}

	public Date getCreationTime() throws XMLDBException {
		return new Date(getCollection().getCreationTime());
	}

	/**
	 * Add a new observer to the list. Observers are just passed
	 * on to the indexer to be notified about the indexing progress.
	 */
	public void addObserver(Observer o) {
		if (!observers.contains(o))
			observers.add(o);
	}

    /* (non-Javadoc)
     * @see org.exist.xmldb.CollectionImpl#isRemoteCollection()
     */
    public boolean isRemoteCollection() throws XMLDBException {
        return false;
    }

	/** set user-defined Reader 
	 * @param dataSource
*/
	public void setReader(XMLReader reader){
			userReader = reader;
	}
}
