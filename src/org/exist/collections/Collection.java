
/*
 *  Collection.java - eXist Open Source Native XML Database
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
 * $Id:
 */
package org.exist.collections;

import it.unimi.dsi.fastutil.Object2LongRBTreeMap;
import it.unimi.dsi.fastutil.Object2ObjectRBTreeMap;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import org.apache.log4j.Category;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.security.Group;
import org.exist.security.Permission;
import org.exist.security.SecurityManager;
import org.exist.security.User;
import org.exist.storage.DBBroker;
import org.exist.util.SyntaxException;
import org.exist.util.VariableByteInputStream;
import org.exist.util.VariableByteOutputStream;

/**
 * This class represents a collection in the database.
 * 
 * A collection maintains a list of sub-collections and documents.
 *  
 * @author wolf
 */
public class Collection implements Comparable {

	private final static Category LOG =
		Category.getInstance(Collection.class.getName());
		
	private final static String COLLECTION_CONFIG_FILE = "collection.xconf";
	
	// the broker instance that created this collection	
	private DBBroker broker;
	
	private int refCount = 0;
	private short collectionId = -1;
	
	// the documents contained in this collection
	private Object2ObjectRBTreeMap documents = new Object2ObjectRBTreeMap();
	
	// the name of this collection
	private String name;
	
	// the permissions assigned to this collection
	private Permission permissions = new Permission(0755);
	
	// stores child-collections with their storage address
	private Object2LongRBTreeMap subcollections = 
		new Object2LongRBTreeMap();
	
	// temporary field for the storage address
	private long address = -1;

	// creation time
	private long created = 0;
	
	//private CollectionConfiguration configuration = null;
	
	public Collection(DBBroker broker) {
		this.broker = broker;
	}

	public Collection(DBBroker broker, String name) {
		this(broker);
		this.name = name;
	}
	
	/**
	 *  Add a new sub-collection to the collection.
	 *
	 *@param  name
	 */
	synchronized public void addCollection(Collection child) {
		final int p = child.name.lastIndexOf('/') + 1;
		final String childName = child.name.substring(p);
		if (!subcollections.containsKey(childName))
			subcollections.put(childName, child.address);
	}

	/**
	 * Add a new sub-collection to the collection.
	 * 
	 * @param name
	 */
	synchronized public void addCollection(String name) {
		if(!subcollections.containsKey(name))
			subcollections.put(name, -1);
	}
	
	/**
	 * Update the specified child-collection.
	 * 
	 * @param child
	 */
	synchronized public void update(Collection child) {
		final int p = child.name.lastIndexOf('/') + 1;
		final String childName = child.name.substring(p);
		subcollections.remove(childName);
		subcollections.put(childName, child.address);
	}
	
	/**
	 *  Add a document to the collection.
	 *
	 *@param  doc 
	 */
	synchronized public void addDocument(DocumentImpl doc) {
		if (doc.getDocId() < 0)
			doc.setDocId(broker.getNextDocId(this));
		documents.put(doc.getFileName(), doc);
	}

	/**
	 *  Add a document to the collection.
	 *
	 *@param  user
	 *@param  doc
	 */
	synchronized public void addDocument(User user, DocumentImpl doc) {
		addDocument(doc);
	}

	synchronized public void renameDocument(String oldName, String newName) {
		DocumentImpl doc = (DocumentImpl)documents.remove(oldName);
		doc.setFileName(newName);
		if(doc != null)
			documents.put(newName, doc);
	}

	/**
	 *  Return an iterator over all subcollections.
	 * 
	 * The list of subcollections is copied first, so modifications
	 * via the iterator have no affect.
	 *
	 *@return    Description of the Return Value
	 */
	synchronized public Iterator collectionIterator() {
		return new TreeSet(subcollections.keySet()).iterator();
	}

	/**
	 * Load all collections being descendants of this collections
	 * and return them in a List.
	 * 
	 * @return List
	 */
	public synchronized List getDescendants(User user) {
		final ArrayList cl = new ArrayList(subcollections.size());
		Collection child;
		String childName;
		for (Iterator i = subcollections.keySet().iterator(); i.hasNext();) {
			childName = (String) i.next();
			child = broker.getCollection(name + '/' + childName);
			if (permissions.validate(user, Permission.READ)) {
				cl.add(child);
				if (child.getChildCollectionCount() > 0)
					cl.addAll(child.getDescendants(user));
			}
		}
		return cl;
	}

	/**
	 * Retrieve all documents contained in this collections.
	 * 
	 * If recursive is true, documents from sub-collections are
	 * included.
	 * 
	 * @param user
	 * @param recursive
	 * @return
	 */
	public synchronized DocumentSet allDocs(User user, boolean recursive) {
		DocumentSet docs = new DocumentSet();
		getDocuments(docs);
		if(recursive)
			allDocs(user, docs);
		return docs;
	}

	private DocumentSet allDocs(User user, DocumentSet docs) {
		Collection child;
		String childName;
		long addr;
		for (Iterator i = subcollections.keySet().iterator(); i.hasNext();) {
			childName = (String) i.next();
			addr = subcollections.getLong(childName);
			if(addr < 0)
				child = broker.getCollection(name + '/' + childName);
			else
				child = broker.getCollection(name + '/' + childName, addr);
			if (permissions.validate(user, Permission.READ)) {
				child.getDocuments(docs);
				if (child.getChildCollectionCount() > 0)
					child.allDocs(user, docs);
			}
		}
		return docs;
	}

	/**
	 * Add all documents to the specified document set.
	 *  
	 * @param docs
	 */
	public synchronized void getDocuments(DocumentSet docs) {
		docs.addCollection(this);
		docs.addAll(documents.values());
	}

	public int compareTo(Object obj) {
		Collection other = (Collection) obj;
		if (collectionId == other.collectionId)
			return 0;
		else if (collectionId < other.collectionId)
			return -1;
		else
			return 1;
	}

	public boolean equals(Object obj) {
		if (!(obj instanceof Collection))
			return false;
		return ((Collection) obj).collectionId == collectionId;
	}

	/**
	 *  Return the number of child-collections managed by this
	 * collection.
	 *
	 *@return    The childCollectionCount value
	 */
	public synchronized int getChildCollectionCount() {
		return subcollections.size();
	}

	/**
	 *  Get a child-document.
	 *
	 *@param  name  Description of the Parameter
	 *@return       The document value
	 */
	public synchronized DocumentImpl getDocument(String name) {
		return (DocumentImpl) documents.get(name);
	}

	/**
	 *  Returns the number of documents in this collection.
	 *
	 *@return    The documentCount value
	 */
	public synchronized int getDocumentCount() {
		return documents.size();
	}

	/**
	 *  Get the internal id.
	 *
	 *@return    The id value
	 */
	public short getId() {
		return collectionId;
	}

	/**
	 *  Get the name of this collection.
	 *
	 *@return    The name value
	 */
	public String getName() {
		return name;
	}

	/**
	 *  Returns the parent-collection.
	 *
	 *@return    The parent-collection or null if this
	 *is the root collection.
	 */
	public synchronized Collection getParent() {
		if (name.equals("/db"))
			return null;
		String parent =
			(name.lastIndexOf("/") < 1
				? "/db"
				: name.substring(0, name.lastIndexOf("/")));
		return broker.getCollection(parent);
	}

	/**
	 * Returns the broker instance that created this collection.
	 * 
	 * @return
	 */
	protected synchronized DBBroker getBroker() {
		return broker;
	}
	
	public synchronized void setBroker(DBBroker newBroker) {
		broker = newBroker;
	}
	
	/**
	 *  Gets the permissions attribute of the Collection object
	 *
	 *@return    The permissions value
	 */
	public synchronized Permission getPermissions() {
		return permissions;
	}

	/**
	 *  Check if the collection has a child document.
	 *
	 *@param  name  the name (without path) of the document
	 *@return  
	 */
	public synchronized boolean hasDocument(String name) {
		return getDocument(name) != null;
	}

	/**
	 *  Check if the collection has a sub-collection.
	 *
	 *@param  name  the name of the subcollection (without path).
	 *@return  
	 */
	public synchronized boolean hasSubcollection(String name) {
		return subcollections.containsKey(name);
	}

	/**
	 *  Returns an iterator on the child-documents in this collection.
	 *
	 *@return
	 */
	public synchronized Iterator iterator() {
		return documents.values().iterator();
	}

//	public synchronized CollectionConfiguration getConfiguration() 
//	throws CollectionConfigurationException {
//		if(configuration == null)
//			configuration = 
//				new CollectionConfiguration(this, getDocument(COLLECTION_CONFIG_FILE));
//		return configuration;
//	}
	
	/**
	 * Read collection contents from the stream.
	 * 
	 * @param istream
	 * @throws IOException
	 */
	public void read(VariableByteInputStream istream) throws IOException {
		collectionId = istream.readShort();
		final int collLen = istream.readInt();
		String sub;
		for (int i = 0; i < collLen; i++)
			subcollections.put(istream.readUTF(), istream.readLong());
		
		final SecurityManager secman =
			broker.getBrokerPool().getSecurityManager();
		final int uid = istream.readInt();
		final int gid = istream.readInt();
		final int perm = (istream.readByte() & 0777);
		if (secman == null) {
			permissions.setOwner(SecurityManager.DBA_USER);
			permissions.setGroup(SecurityManager.DBA_GROUP);
		} else {
			permissions.setOwner(secman.getUser(uid));
			permissions.setGroup(secman.getGroup(gid).getName());
		}
		permissions.setPermissions(perm);
		created = istream.readLong();
		DocumentImpl doc;
		try {
			while (istream.available() > 0) {
				doc = new DocumentImpl(broker, this);
				doc.read(istream);
				addDocument(doc);
			}
		} catch (EOFException e) {
		}
	}

	/**
	 *  Remove the specified sub-collection.
	 *
	 *@param  name  Description of the Parameter
	 */
	public synchronized void removeCollection(String name) {
		subcollections.remove(name);
	}

	/**
	 *  Remove the specified document from the collection.
	 *
	 *@param  name  Description of the Parameter
	 */
	synchronized public void removeDocument(String name) {
		documents.remove(name);
	}

	/**
	 *  Set the id of this collection.
	 *
	 *@param  id  The new id value
	 */
	public void setId(short id) {
		this.collectionId = id;
	}

	/**
	 *  Set the name of this collection.
	 *
	 *@param  name  The new name value
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 *  Set permissions for the collection.
	 *
	 *@param  mode  The new permissions value
	 */
	public synchronized void setPermissions(int mode) {
		permissions.setPermissions(mode);
	}

	/**
	 *  Set permissions for the collection.
	 *
	 *@param  mode                 The new permissions value
	 *@exception  SyntaxException  Description of the Exception
	 */
	public synchronized void setPermissions(String mode) throws SyntaxException {
		permissions.setPermissions(mode);
	}

	/**
	 * Set permissions for the collection.
	 * 
	 * @param permissions
	 */
	public synchronized void setPermissions(Permission permissions) {
		this.permissions = permissions;
	}
	
	/**
	 * Write collection contents to stream.
	 * 
	 * @param ostream
	 * @throws IOException
	 */
	public void write(VariableByteOutputStream ostream) throws IOException {
		ostream.writeShort(collectionId);
		ostream.writeInt(subcollections.size());
		String childColl;
		for (Iterator i = subcollections.keySet().iterator(); i.hasNext();) {
			childColl = (String)i.next();
			ostream.writeUTF(childColl);
			ostream.writeLong(subcollections.getLong(childColl));
			
		}
		org.exist.security.SecurityManager secman =
			broker.getBrokerPool().getSecurityManager();
		if (secman == null) {
			ostream.writeInt(1);
			ostream.writeInt(1);
		} else {
			User user = secman.getUser(permissions.getOwner());
			Group group = secman.getGroup(permissions.getOwnerGroup());
			ostream.writeInt(user.getUID());
			ostream.writeInt(group.getId());
		}
		ostream.writeByte((byte) permissions.getPermissions());
		ostream.writeLong(created);
		DocumentImpl doc;
		for (Iterator i = iterator(); i.hasNext();) {
			doc = (DocumentImpl) i.next();
			doc.write(ostream);
		}
	}

	/**
	 * Set the internal storage address of the collection data.
	 * 
	 * @param addr
	 */
	public void setAddress(long addr) {
		this.address = addr;
	}
	
	public void incRefCount() {
		--refCount;
	}

	public void decRefCount() {
		--refCount;
	}

	public void setRefCount(int initialCount) {
		refCount = initialCount;
	}

	public int getRefCount() {
		return refCount;
	}
	
	public void setCreationTime(long ms) {
		created = ms;
	}
	
	public long getCreationTime() {
		return created;
	}
}
