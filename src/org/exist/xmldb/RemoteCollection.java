
/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001 Wolfgang M. Meier
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
 *  $Id:
 */
package org.exist.xmldb;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;
import org.exist.security.Permission;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.Service;
import org.xmldb.api.base.XMLDBException;

/**
 * A remote implementation of the Collection interface. This 
 * implementation communicates with the server through the XMLRPC
 * protocol.
 * 
 * @author wolf
 */
public class RemoteCollection implements CollectionImpl {
	// max size of a resource to be send to the server
	// if the resource exceeds this limit, the data is split into
	// junks and uploaded to the server via the update() call
	private static final int MAX_CHUNK_LENGTH = 512000;

	protected Map childCollections = null;
	protected String encoding = "UTF-8";
	protected int indentXML = 0;
	protected String name;
	protected Permission permissions = null;
	protected RemoteCollection parent = null;
	protected List resources = null;
	protected XmlRpcClient rpcClient = null;
	protected boolean saxDocumentEvents = true;

	public RemoteCollection(XmlRpcClient client, String host, String collection)
		throws XMLDBException {
		this(client, null, host, collection);
	}

	public RemoteCollection(
		XmlRpcClient client,
		RemoteCollection parent,
		String host,
		String collection)
		throws XMLDBException {
		this.name = collection;
		this.parent = parent;
		this.rpcClient = client;
	}

	protected void addChildCollection(Collection child) throws XMLDBException {
		if (childCollections == null)
			readCollection();
		childCollections.put(child.getName(), child);
	}

	protected void addResource(String id) throws XMLDBException {
		if (childCollections == null)
			readCollection();
		resources.add(id);
	}

	public void close() throws XMLDBException {
		try {
			rpcClient.execute("sync", new Vector());
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, "failed to close collection", e);
		} catch (IOException e) {
			throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, "failed to close collection", e);
		}
	}

	public String createId() throws XMLDBException {
		if (childCollections == null)
			readCollection();
		String id;
		Random rand = new Random();
		boolean ok;
		do {
			ok = true;
			id = Integer.toHexString(rand.nextInt()) + ".xml";
			// check if this id does already exist
			for (int i = 0; i < resources.size(); i++)
				if (((DocumentProxy) resources.get(i)).getName().equals(id))
					ok = false;

			if (childCollections.containsKey(id))
				ok = false;

		} while (!ok);
		return id;
	}

	public Resource createResource(String id, String type) throws XMLDBException {
		String newId = id == null ? createId() : id;
		RemoteXMLResource r = new RemoteXMLResource(this, -1, -1, newId, null, indentXML, encoding);
		r.setSAXDocEvents(this.saxDocumentEvents);
		return r;
	}

	public Collection getChildCollection(String name) throws XMLDBException {
		if (childCollections == null)
			readCollection();
		if (name.indexOf('/') > -1)
			return (Collection) childCollections.get(name);
		else
			return (Collection) childCollections.get(getPath() + '/' + name);
	}

	public int getChildCollectionCount() throws XMLDBException {
		if (childCollections == null)
			readCollection();
		return childCollections.size();
	}

	protected XmlRpcClient getClient() {
		return rpcClient;
	}

	public String getName() throws XMLDBException {
		return name;
	}

	public Collection getParentCollection() throws XMLDBException {
		return parent;
	}

	public String getPath() throws XMLDBException {
		if (parent == null)
			return "/db";
		return name;
	}

	public String getProperty(String property) throws XMLDBException {
		if (property.equals("pretty"))
			return (indentXML == 1) ? "true" : "false";
		if (property.equals("encoding"))
			return encoding;
		if (property.equals("sax-document-events"))
			return saxDocumentEvents ? "true" : "false";
		return null;
	}

	public Resource getResource(String id) throws XMLDBException {
		if (childCollections == null)
			readCollection();
		int rlen = resources.size();
		for (int i = 0; i < rlen; i++) {
			DocumentProxy dp = (DocumentProxy) resources.get(i);
			RemoteXMLResource r;
			if (dp.getName().equals(id)) {
				r = new RemoteXMLResource(this, -1, -1, dp.getName(), null, indentXML, encoding);
				r.setSAXDocEvents(this.saxDocumentEvents);
				r.setPermissions(dp.getPermissions());
				return r;
			}
		}
		return null;
	}

	public int getResourceCount() throws XMLDBException {
		if (childCollections == null)
			readCollection();
		return resources.size();
	}

	public Service getService(String name, String version) throws XMLDBException {
		if (name.equals("XPathQueryService"))
			return new RemoteXPathQueryService(this);
		if (name.equals("CollectionManagementService") || name.equals("CollectionManager"))
			return new CollectionManagementServiceImpl(this, rpcClient);
		if (name.equals("UserManagementService"))
			return new UserManagementServiceImpl(this);
		if (name.equals("DatabaseInstanceManager"))
			return new DatabaseInstanceManagerImpl(rpcClient);
		if (name.equals("IndexQueryService"))
			return new RemoteIndexQueryService(rpcClient, this);
		if (name.equals("XUpdateQueryService"))
			return new RemoteXUpdateQueryService(this);
		throw new XMLDBException(ErrorCodes.NO_SUCH_SERVICE);
	}

	public Service[] getServices() throws XMLDBException {
		Service[] services = new Service[6];
		services[0] = new RemoteXPathQueryService(this);
		services[1] = new CollectionManagementServiceImpl(this, rpcClient);
		services[2] = new UserManagementServiceImpl(this);
		services[3] = new DatabaseInstanceManagerImpl(rpcClient);
		services[4] = new RemoteIndexQueryService(rpcClient, this);
		services[5] = new RemoteXUpdateQueryService(this);
		return services;
	}

	protected boolean hasChildCollection(String name) throws XMLDBException {
		if (childCollections == null)
			readCollection();
		return childCollections.containsKey(name);
	}

	public boolean isOpen() throws XMLDBException {
		return true;
	}

	/**
	 *  Returns a list of collection names naming all child collections of the
	 *  current collection. Only the name of the collection is returned - not
	 *  the entire path to the collection.
	 *
	 *@return                     Description of the Return Value
	 *@exception  XMLDBException  Description of the Exception
	 */
	public String[] listChildCollections() throws XMLDBException {
		if (childCollections == null)
			readCollection();
		String coll[] = new String[childCollections.size()];
		int j = 0;
		int p;
		for (Iterator i = childCollections.keySet().iterator(); i.hasNext(); j++) {
			coll[j] = (String) i.next();
			if ((p = coll[j].lastIndexOf('/')) > -1)
				coll[j] = coll[j].substring(p + 1);

		}
		return coll;
	}

	public String[] listResources() throws XMLDBException {
		if (childCollections == null)
			readCollection();
		int lsize = resources.size();
		String[] list = new String[lsize];
		for (int i = 0; i < lsize; i++)
			list[i] = ((DocumentProxy) resources.get(i)).getName();
		return list;
	}

	private void readCollection() throws XMLDBException {
		resources = new ArrayList();
		childCollections = new HashMap();
		Vector params = new Vector();
		params.addElement(getPath());

		Hashtable collection;
		try {
			collection = (Hashtable) rpcClient.execute("getCollectionDesc", params);
		} catch (XmlRpcException xre) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, xre.getMessage(), xre);
		} catch (IOException ioe) {
			throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "an io error occurred", ioe);
		}
		Vector documents = (Vector) collection.get("documents");
		Vector collections = (Vector) collection.get("collections");
		permissions =
			new Permission(
				(String) collection.get("owner"),
				(String) collection.get("group"),
				((Integer) collection.get("permissions")).intValue());
		String docName;
		String childName;
		Hashtable hash;
		DocumentProxy proxy;
		Permission perm;
		int p, dsize = documents.size();
		for (int i = 0; i < dsize; i++) {
			hash = (Hashtable) documents.elementAt(i);
			docName = (String) hash.get("name");
			if ((p = docName.lastIndexOf('/')) > -1)
				docName = docName.substring(p + 1);
			proxy = new DocumentProxy(docName);
			perm =
				new Permission(
					(String) hash.get("owner"),
					(String) hash.get("group"),
					((Integer) hash.get("permissions")).intValue());
			proxy.setPermissions(perm);
			resources.add(proxy);
		}
		for (int i = 0; i < collections.size(); i++) {
			childName = (String) collections.elementAt(i);
			try {
				RemoteCollection child =
					new RemoteCollection(rpcClient, this, null, getPath() + '/' + childName);
				addChildCollection(child);
			} catch (XMLDBException e) {
			}
		}
	}

	public void registerService(Service serv) throws XMLDBException {
		throw new XMLDBException(ErrorCodes.NOT_IMPLEMENTED);
	}

	public void removeChildCollection(String name) throws XMLDBException {
		if (childCollections == null)
			readCollection();
		childCollections.remove(getPath() + '/' + name);
	}

	public void removeResource(Resource res) throws XMLDBException {
		if (resources == null)
			readCollection();
		int pos = -1;
		for (int i = 0; i < resources.size(); i++)
			if (((DocumentProxy) resources.get(i)).getName().equals(res.getId())) {
				pos = i;
				break;
			}
		if (pos < 0)
			throw new XMLDBException(
				ErrorCodes.INVALID_RESOURCE,
				"resource " + res.getId() + " not found");
		Vector params = new Vector();
		params.addElement(getPath() + '/' + res.getId());

		try {
			rpcClient.execute("remove", params);
		} catch (XmlRpcException xre) {
			throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, xre.getMessage(), xre);
		} catch (IOException ioe) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
		}

		resources.remove(pos);
	}

	public Date getCreationTime() throws XMLDBException {
		Vector params = new Vector(1);
		params.addElement(getPath());
		try {
			return (Date) rpcClient.execute("getCreationDate", params);
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
		} catch (IOException e) {
			throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
		}
	}

	public void setProperty(String property, String value) throws XMLDBException {
		if (property.equals("pretty"))
			indentXML = (value.equals("true") ? 1 : -1);

		if (property.equals("encoding"))
			encoding = value;

		if (property.equals("sax-document-events"))
			saxDocumentEvents = value.equals("true");

	}

	public void storeResource(Resource res) throws XMLDBException {
		if (resources == null)
			readCollection();
		for (int i = 0; i < resources.size(); i++)
			if (((DocumentProxy) resources.get(i)).getName().equals(res.getId()))
				resources.remove(i);
		Object content = res.getContent();
		if (content instanceof File) {
			File file = (File) content;
			if (!file.canRead())
				throw new XMLDBException(
					ErrorCodes.INVALID_RESOURCE,
					"failed to read resource from file " + file.getAbsolutePath());
			if (file.length() < MAX_CHUNK_LENGTH) {
				store((RemoteXMLResource)res);
			} else {
				uploadAndStore(res);
			}
		} else
			store((RemoteXMLResource)res);
		resources.add(new DocumentProxy(res.getId()));
	}

	private void store(RemoteXMLResource res) throws XMLDBException {
		byte[] data = res.getData();
		Vector params = new Vector();
		params.addElement(data);
		params.addElement(getPath() + '/' + res.getId());
		params.addElement(new Integer(1));
		try {
			rpcClient.execute("parse", params);
		} catch (XmlRpcException xre) {
			throw new XMLDBException(
				ErrorCodes.INVALID_RESOURCE,
				xre == null ? "unknown error" : xre.getMessage(),
				xre);
		} catch (IOException ioe) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
		}
	}

	private void uploadAndStore(Resource res) throws XMLDBException {
		File file = (File) res.getContent();
		byte[] chunk = new byte[MAX_CHUNK_LENGTH];
		try {
			FileInputStream is = new FileInputStream(file);
			int len;
			String fileName = null;
			Vector params;
			while ((len = is.read(chunk)) > -1) {
				params = new Vector();
				if (fileName != null)
					params.addElement(fileName);
				params.addElement(chunk);
				params.addElement(new Integer(len));
				fileName = (String) rpcClient.execute("upload", params);
			}
			params = new Vector();
			params.addElement(fileName);
			params.addElement(getPath() + '/' + res.getId());
			params.addElement(new Boolean(true));
			rpcClient.execute("parseLocal", params);
		} catch (FileNotFoundException e) {
			throw new XMLDBException(
				ErrorCodes.INVALID_RESOURCE,
				"could not read resource from file " + file.getAbsolutePath(),
				e);
		} catch (IOException e) {
			throw new XMLDBException(
				ErrorCodes.INVALID_RESOURCE,
				"failed to read resource from file " + file.getAbsolutePath(),
				e);
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "networking error", e);
		}
	}

	public Permission getPermissions() {
		return permissions;
	}
}
