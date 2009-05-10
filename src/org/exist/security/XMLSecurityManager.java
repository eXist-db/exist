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
 *  $Id$
 */
package org.exist.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfiguration;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DocumentImpl;
import org.exist.security.xacml.ExistPDP;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.hashtable.Int2ObjectHashMap;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * SecurityManager is responsible for managing users and groups.
 * 
 * There's only one SecurityManager for each database instance, which
 * may be obtained by {@link BrokerPool#getSecurityManager()}.
 * 
 * Users and groups are stored in the system collection, in document
 * users.xml. While it is possible to edit this file by hand, it
 * may lead to unexpected results, since SecurityManager reads 
 * users.xml only during database startup and shutdown.
 */
public class XMLSecurityManager implements SecurityManager {
	
	public static final String CONFIGURATION_ELEMENT_NAME = "default-permissions";
	public static final String COLLECTION_ATTRIBUTE = "collection";
	public static final String RESOURCE_ATTRIBUTE = "resource";
	
	public static final String PROPERTY_PERMISSIONS_COLLECTIONS = "indexer.permissions.collection";
	public static final String PROPERTY_PERMISSIONS_RESOURCES = "indexer.permissions.resource";	

	public final static String DBA_GROUP = "dba";
	public final static String DBA_USER = "admin";
	public final static String GUEST_GROUP = "guest";
	public final static String GUEST_USER = "guest";
	
	public final static User SYSTEM_USER = new User(DBA_USER, null, DBA_GROUP);
	
	private final static Logger LOG =
		Logger.getLogger(SecurityManager.class);

	private BrokerPool pool;
	private Int2ObjectHashMap groups = new Int2ObjectHashMap(65);
	private Int2ObjectHashMap users = new Int2ObjectHashMap(65);
	private int nextUserId = 0;
	private int nextGroupId = 0;

	private int defCollectionPermissions = Permission.DEFAULT_PERM;
    private int defResourcePermissions = Permission.DEFAULT_PERM;
    
    private ExistPDP pdp;

    public XMLSecurityManager() {
       
    }
	/**
	 * Initialize the security manager.
	 * 
	 * Checks if the file users.xml exists in the system collection of the database.
	 * If not, it is created with two default users: admin and guest.
	 *  
	 * @param pool
	 * @param sysBroker
	 */
    public void attach(BrokerPool pool, DBBroker sysBroker) {
       this.pool = pool;
       
       TransactionManager transact = pool.getTransactionManager();
       Txn txn = null;
       DBBroker broker = sysBroker;
       try {
          Collection sysCollection = broker.getCollection(XmldbURI.SYSTEM_COLLECTION_URI);
          if (sysCollection == null) {
             txn = transact.beginTransaction();
             sysCollection = broker.getOrCreateCollection(txn, XmldbURI.SYSTEM_COLLECTION_URI);
              if (sysCollection == null)
                return;
             sysCollection.setPermissions(0770);
             broker.saveCollection(txn, sysCollection);
             transact.commit(txn);
          }
          Document acl = sysCollection.getDocument(broker, ACL_FILE_URI);
          Element docElement = null;
          if (acl != null)
             docElement = acl.getDocumentElement();
          if (docElement == null) {
             LOG.debug("creating system users");
             User user = new User(DBA_USER, null);
             user.addGroup(DBA_GROUP);
             user.setUID(++nextUserId);
             users.put(user.getUID(), user);
             user = new User(GUEST_USER, GUEST_USER, GUEST_GROUP);
             user.setUID(++nextUserId);
             users.put(user.getUID(), user);
             newGroup(DBA_GROUP);
             newGroup(GUEST_GROUP);
             txn = transact.beginTransaction();
             save(broker, txn);
             transact.commit(txn);
          } else {
             LOG.debug("loading acl");
             Element root = acl.getDocumentElement();
             Attr version = root.getAttributeNode("version");
             int major = 0;
             int minor = 0;
             if (version!=null) {
                String [] numbers = version.getValue().split("\\.");
                major = Integer.parseInt(numbers[0]);
                minor = Integer.parseInt(numbers[1]);
             }
             NodeList nl = root.getChildNodes();
             Node node;
             Element next;
             User user;
             NodeList ul;
             String lastId;
             Group group;
             for (int i = 0; i < nl.getLength(); i++) {
                if(nl.item(i).getNodeType() != Node.ELEMENT_NODE)
                   continue;
                next = (Element) nl.item(i);
                if (next.getTagName().equals("users")) {
                   lastId = next.getAttribute("last-id");
                   try {
                      nextUserId = Integer.parseInt(lastId);
                   } catch (NumberFormatException e) {
                   }
                   ul = next.getChildNodes();
                   for (int j = 0; j < ul.getLength(); j++) {
                      node = ul.item(j);
                      if(node.getNodeType() == Node.ELEMENT_NODE &&
                              node.getLocalName().equals("user")) {
                         user = new User(major,minor,(Element)node);
                         users.put(user.getUID(), user);
                      }
                   }
                } else if (next.getTagName().equals("groups")) {
                   lastId = next.getAttribute("last-id");
                   try {
                      nextGroupId = Integer.parseInt(lastId);
                   } catch (NumberFormatException e) {
                   }
                   ul = next.getChildNodes();
                   for (int j = 0; j < ul.getLength(); j++) {
                      node = ul.item(j);
                      if(node.getNodeType() == Node.ELEMENT_NODE &&
                              node.getLocalName().equals("group")) {
                         group = new Group((Element)node);
                         groups.put(group.getId(), group);
                      }
                   }
                }
             }
          }
       } catch (Exception e) {
          transact.abort(txn);
          e.printStackTrace();
          LOG.debug("loading acl failed: " + e.getMessage());
       }
       // read default collection and resource permissions
       Integer defOpt = (Integer)
       broker.getConfiguration().getProperty(PROPERTY_PERMISSIONS_COLLECTIONS);
       if (defOpt != null)
          defCollectionPermissions = defOpt.intValue();
       defOpt = (Integer)
       broker.getConfiguration().getProperty(PROPERTY_PERMISSIONS_RESOURCES);
       if (defOpt != null)
          defResourcePermissions = defOpt.intValue();
       
       Boolean enableXACML = (Boolean)broker.getConfiguration().getProperty("xacml.enable");
       if(enableXACML != null && enableXACML.booleanValue()) {
          pdp = new ExistPDP(pool);
          LOG.debug("XACML enabled");
       }
    }
	public boolean isXACMLEnabled() {
		return pdp != null;
	}
	public ExistPDP getPDP() {
		return pdp;
	}
	public synchronized void deleteUser(String name) throws PermissionDeniedException {
		deleteUser(getUser(name));
	}
	
	public synchronized void deleteUser(User user) throws PermissionDeniedException {
		if(user == null)
			return;
		user = (User)users.remove(user.getUID());
		if(user != null)
			LOG.debug("user " + user.getName() + " removed");
		else
			LOG.debug("user not found");
		DBBroker broker = null;
        TransactionManager transact = pool.getTransactionManager();
        Txn txn = transact.beginTransaction();
		try {
			broker = pool.get(SYSTEM_USER);
			save(broker, txn);
            transact.commit(txn);
		} catch (EXistException e) {
            transact.abort(txn);
			e.printStackTrace();
		} finally {
			pool.release(broker);
		}
	}

	public synchronized User getUser(String name) {
		User user;
		for (Iterator i = users.valueIterator(); i.hasNext();) {
			user = (User) i.next();
			if (user.getName().equals(name))
				return user;
		}
		LOG.debug("user " + name + " not found");
		return null;
	}

	public synchronized User getUser(int uid) {
		final User user = (User)users.get(uid);
		if(user == null) {
//			LOG.debug("user with uid " + uid + " not found");
        }
		return user;
	}
	
	public synchronized User[] getUsers() {
		User u[] = new User[users.size()];
		int j = 0;
		for (Iterator i = users.valueIterator(); i.hasNext(); j++)
			u[j] = (User) i.next();
		return u;
	}

	protected void newGroup(String name) {
		Group group = new Group(name, ++nextGroupId);
		groups.put(group.getId(), group);
	}

    public synchronized void addGroup(String name) {
        newGroup(name);
        DBBroker broker = null;
        TransactionManager transact = pool.getTransactionManager();
        Txn txn = transact.beginTransaction();
        try {
            broker = pool.get(SYSTEM_USER);
            save(broker, txn);
            transact.commit(txn);
        } catch (EXistException e) {
            transact.abort(txn);
            e.printStackTrace();
        } finally {
            pool.release(broker);
        }
    }
    
	public synchronized boolean hasGroup(String name) {
		Group group;
		for (Iterator i = groups.valueIterator(); i.hasNext();) {
			group = (Group) i.next();
			if (group.getName().equals(name))
				return true;
		}
		return false;
	}

	public synchronized Group getGroup(String name) {
		Group group;
		for (Iterator i = groups.valueIterator(); i.hasNext();) {
			group = (Group) i.next();
			if (group.getName().equals(name))
				return group;
		}
		return null;
	}

	public synchronized Group getGroup(int gid) {
		return (Group)groups.get(gid);
	}
	
	public synchronized String[] getGroups() {
		ArrayList list = new ArrayList(groups.size());
		Group group;
		for(Iterator i = groups.valueIterator(); i.hasNext(); ) {
			group = (Group) i.next();
			list.add(group.getName());
		}
		String[] gl = new String[list.size()];
		list.toArray(gl);
		return gl;
	}
	
	public synchronized boolean hasAdminPrivileges(User user) {
		return user.hasDbaRole();
	}

	public synchronized boolean hasUser(String name) {
		User user;
		for (Iterator i = users.valueIterator(); i.hasNext();) {
			user = (User) i.next();
			if (user.getName().equals(name))
				return true;
		}
		return false;
	}

	private synchronized void save(DBBroker broker, Txn transaction) throws EXistException {
		LOG.debug("storing acl file");
		StringBuffer buf = new StringBuffer();
        buf.append("<!-- Central user configuration. Editing this document will cause the security " +
                "to reload and update its internal database. Please handle with care! -->");
		buf.append("<auth version='1.0'>");
		// save groups
        buf.append("<!-- Please do not remove the guest and admin groups -->");
		buf.append("<groups last-id=\"");
		buf.append(Integer.toString(nextGroupId));
		buf.append("\">");
		for (Iterator i = groups.valueIterator(); i.hasNext();)
			buf.append(((Group) i.next()).toString());
		buf.append("</groups>");
		//save users
        buf.append("<!-- Please do not remove the admin user. -->");
		buf.append("<users last-id=\"");
		buf.append(Integer.toString(nextUserId));
		buf.append("\">");
		for (Iterator i = users.valueIterator(); i.hasNext();)
			buf.append(((User) i.next()).toString());
		buf.append("</users>");
		buf.append("</auth>");
        
		// store users.xml
		broker.flush();
		broker.sync(Sync.MAJOR_SYNC);
		try {
			broker.setUser(getUser(DBA_USER));
			Collection sysCollection = broker.getCollection(XmldbURI.SYSTEM_COLLECTION_URI);
            String data = buf.toString();
            IndexInfo info = sysCollection.validateXMLResource(transaction, broker, ACL_FILE_URI, data);
            //TODO : unlock the collection here ?
            DocumentImpl doc = info.getDocument();
            doc.getMetadata().setMimeType(MimeType.XML_TYPE.getName());
            sysCollection.store(transaction, broker, info, data, false);
			doc.setPermissions(0770);
			broker.saveCollection(transaction, doc.getCollection());
		} catch (IOException e) {
			throw new EXistException(e.getMessage());
        } catch (TriggerException e) {
            throw new EXistException(e.getMessage());
		} catch (SAXException e) {
			throw new EXistException(e.getMessage());
		} catch (PermissionDeniedException e) {
			throw new EXistException(e.getMessage());
		} catch (LockException e) {
			throw new EXistException(e.getMessage());
		}
		broker.flush();
		broker.sync(Sync.MAJOR_SYNC);
	}

	public synchronized void setUser(User user) {
		if (user.getUID() < 0)
			user.setUID(++nextUserId);
		users.put(user.getUID(), user);
		String[] groups = user.getGroups();
        // if no group is specified, we automatically fall back to the guest group
        if (groups.length == 0)
            user.addGroup(GUEST_GROUP);
		for (int i = 0; i < groups.length; i++) {
			if (!hasGroup(groups[i]))
				newGroup(groups[i]);
		}
        TransactionManager transact = pool.getTransactionManager();
        Txn txn = transact.beginTransaction();
		DBBroker broker = null;
		try {
			broker = pool.get(SYSTEM_USER);
			save(broker, txn);
			createUserHome(broker, txn, user);
            transact.commit(txn);
		} catch (EXistException e) {
            transact.abort(txn);
			LOG.debug("error while creating user", e);
		} catch (IOException e) {
            transact.abort(txn);
			LOG.debug("error while creating home collection", e);
		} catch (PermissionDeniedException e) {
            transact.abort(txn);
			LOG.debug("error while creating home collection", e);
		} finally {
			pool.release(broker);
		}
	}
	
	public int getResourceDefaultPerms() {
		return defResourcePermissions;
	}
	
	public int getCollectionDefaultPerms() {
		return defCollectionPermissions;
	}
	
	private void createUserHome(DBBroker broker, Txn transaction, User user) 
	throws EXistException, PermissionDeniedException, IOException {
		if(user.getHome() == null)
			return;
		broker.setUser(getUser(DBA_USER));
		Collection home = broker.getOrCreateCollection(transaction, user.getHome());
		home.getPermissions().setOwner(user.getName());
		CollectionConfiguration config = home.getConfiguration(broker);
		String group = (config!=null) ? config.getDefCollGroup(user) : user.getPrimaryGroup();
		home.getPermissions().setGroup(group);
		broker.saveCollection(transaction, home);
	}
}
