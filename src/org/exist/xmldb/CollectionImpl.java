
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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Random;
import java.util.Vector;
import org.apache.xmlrpc.*;
import org.xmldb.api.base.*;

/**
 * A remote implementation of the Collection interface. This 
 * implementation communicates with the server through the XMLRPC
 * protocol.
 * 
 * @author wolf
 */
public class CollectionImpl implements Collection {
	protected HashMap childCollections = null;
	protected String encoding = "UTF-8";
	protected int indentXML = 1;
	protected String name;

	protected CollectionImpl parent = null;
	protected ArrayList resources = null;
	protected XmlRpcClient rpcClient = null;
	protected boolean saxDocumentEvents = true;

	public CollectionImpl(XmlRpcClient client, String host, String collection)
		throws XMLDBException {
		this(client, null, host, collection);
	}

	public CollectionImpl(
		XmlRpcClient client,
		CollectionImpl parent,
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
				if (((String) resources.get(i)).equals(id))
					ok = false;

			if (childCollections.containsKey(id))
				ok = false;

		} while (!ok);
		return id;
	}

	public Resource createResource(String id, String type) throws XMLDBException {
		if (id == null)
			id = createId();

		XMLResourceImpl r = new XMLResourceImpl(this, -1, -1, id, null, indentXML, encoding);
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
		for (int i = 0; i < resources.size(); i++)
			if (((String) resources.get(i)).equals(id)) {
				XMLResourceImpl r =
					new XMLResourceImpl(
						this,
						-1,
						-1,
						(String) resources.get(i),
						null,
						indentXML,
						encoding);
				r.setSAXDocEvents(this.saxDocumentEvents);
				return r;
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
		throw new XMLDBException(ErrorCodes.NO_SUCH_SERVICE);
	}

	public Service[] getServices() throws XMLDBException {
		Service[] services = new Service[5];
		services[0] = new RemoteXPathQueryService(this);
		services[1] = new CollectionManagementServiceImpl(this, rpcClient);
		services[2] = new UserManagementServiceImpl(this);
		services[3] = new DatabaseInstanceManagerImpl(rpcClient);
		services[4] = new RemoteIndexQueryService(rpcClient, this);
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
		return (String[]) resources.toArray(new String[resources.size()]);
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
		String docName;
		String childName;
		int p;
		for (int i = 0; i < documents.size(); i++) {
			docName = (String) documents.elementAt(i);
			if ((p = docName.lastIndexOf('/')) > -1)
				docName = docName.substring(p + 1);

			addResource(docName);
		}
		for (int i = 0; i < collections.size(); i++) {
			childName = (String) collections.elementAt(i);
			try {
				CollectionImpl child =
					new CollectionImpl(rpcClient, this, null, getPath() + '/' + childName);
				addChildCollection(child);
			} catch (XMLDBException e) {
			}
		}
	}

	public void registerService(Service serv) throws XMLDBException {
		throw new XMLDBException(ErrorCodes.NOT_IMPLEMENTED);
	}

	public void removeChildCollection(String name) throws XMLDBException {
		childCollections.remove(getPath() + '/' + name);
	}

	public void removeResource(Resource res) throws XMLDBException {
		if (!resources.contains(res.getId()))
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
		resources.remove(res.getId());
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
		resources.remove(res.getId());
		long start = System.currentTimeMillis();
		String data = (String) res.getContent();
		byte[] bdata = null;
		try {
			bdata = data.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			bdata = data.getBytes();
		}
		Vector params = new Vector();
		params.addElement(bdata);
		params.addElement(getPath() + '/' + res.getId());
		params.addElement(new Integer(1));
		try {
			rpcClient.execute("parse", params);
		} catch (XmlRpcException xre) {
			throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, xre.getMessage(), xre);
		} catch (IOException ioe) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
		}
		addResource(res.getId());
	}
}
