/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
package org.exist.security.internal;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfiguration;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.config.Configurator;
import org.exist.dom.DocumentImpl;
import org.exist.security.AuthenticationException;
import org.exist.security.Group;
import org.exist.security.GroupImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Realm;
import org.exist.security.User;
import org.exist.security.UserImpl;
import org.exist.security.SecurityManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class RealmImpl implements Realm {
	
	public final static String ID = "eXist-db";

	protected final static String ACL_FILE = "users.xml";
	protected final static XmldbURI ACL_FILE_URI = XmldbURI.create(ACL_FILE);
	   
	private Map<String, Group> groups = new HashMap<String, Group>(65);
	private Map<String, User> users = new HashMap<String, User>(65);

	SecurityManagerImpl sm;
//	Configuration configuration;

	RealmImpl(SecurityManagerImpl sm) { //, Configuration conf

//		configuration = Configurator.configure(this, conf);

		this.sm = sm;

//		// LOG.debug("creating system users");
//		//Build-in accounts
//		sm.DBA_ROLE = _addGroup(0, SecurityManager.DBA_GROUP);
//		sm.SYSTEM_ACCOUNT = _addAccount(0, SecurityManager.DBA_USER, sm.DBA_ROLE);
//
//		//TODO: add if not exist
//		sm.GUEST_ROLE = _addGroup(1, SecurityManager.GUEST_GROUP);
//		sm.GUEST_ACCOUNT = _addAccount(1, SecurityManager.GUEST_USER, sm.GUEST_ROLE);
//		sm.GUEST_ACCOUNT.setPassword(SecurityManager.GUEST_USER);
//
//		//TODO: add if not exist
//		//UNDERSTAND: can it be without admin user?
//		Account account = _addAccount(2, "admin", sm.DBA_ROLE);
//		account.setDefaultRole(sm.DBA_ROLE);
//		account.setPassword("");
	}

//	@Override
//	public boolean isConfigured() {
//		return configuration != null;
//	}
//
//	@Override
//	public Configuration getConfiguration() {
//		return configuration;
//	}

	@Override
	public String getId() {
		return ID;
	}

	public void attach(BrokerPool db) throws EXistException {

		BrokerPool pool = db;
		DBBroker broker = pool.get(pool.getSecurityManager().getSystemAccount());

		TransactionManager transact = pool.getTransactionManager();
		Txn txn = null;
		try {
			Collection sysCollection = broker
					.getCollection(XmldbURI.SYSTEM_COLLECTION_URI);
			if (sysCollection == null) {
				txn = transact.beginTransaction();
				sysCollection = broker.getOrCreateCollection(txn,
						XmldbURI.SYSTEM_COLLECTION_URI);
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

				_save();

			} else {
				// LOG.debug("loading acl");
				Element root = acl.getDocumentElement();
				Attr version = root.getAttributeNode("version");
				int major = 0;
				int minor = 0;
				if (version != null) {
					String[] numbers = version.getValue().split("\\.");
					major = Integer.parseInt(numbers[0]);
					minor = Integer.parseInt(numbers[1]);
				}
				NodeList nl = root.getChildNodes();
				
				Node node;
				Element next;
				
				User account = null; Group group = null;
				NodeList ul;
				
				for (int i = 0; i < nl.getLength(); i++) {
					if (nl.item(i).getNodeType() != Node.ELEMENT_NODE)
						continue;
					next = (Element) nl.item(i);
					if (next.getTagName().equals("users")) {

						ul = next.getChildNodes();
						for (int j = 0; j < ul.getLength(); j++) {
							node = ul.item(j);
							if (node.getNodeType() == Node.ELEMENT_NODE
									&& node.getLocalName().equals("user")) {
								account = new UserImpl(major, minor, (Element) node);
								users.put(account.getName(), account);
							}
						}
					} else if (next.getTagName().equals("groups")) {
						ul = next.getChildNodes();
						for (int j = 0; j < ul.getLength(); j++) {
							node = ul.item(j);
							if (node.getNodeType() == Node.ELEMENT_NODE
									&& node.getLocalName().equals("group")) {
								group = new GroupImpl((Element) node);
								groups.put(group.getName(), group);
							}
						}
					}
				}
			}
		} catch (Exception e) {
			transact.abort(txn);
			e.printStackTrace();
			// LOG.debug("loading acl failed: " + e.getMessage());
		}
	}

	private Group _addGroup(String name) {
		if (groups.containsKey(name))
			throw new IllegalArgumentException("Group "+name+" exist.");
		
		Group group = new GroupImpl(name, -1);//XXX: next id
		groups.put(name, group);
		
		return group;
	}

	private Group _addGroup(int id, String name) {
		if (groups.containsKey(name))
			throw new IllegalArgumentException("Group "+name+" exist.");
		
		Group group = new GroupImpl(name, id);
		groups.put(name, group);
		
		return group;
	}

	public synchronized Group addGroup(String name) {
		Group group = _addGroup(name);
		
		_save();
		
		return group;
	}

	public synchronized boolean hasRole(String name) {
		return groups.containsKey(name);
	}

	public synchronized Group getRole(String name) {
		return groups.get(name);
	}
	
	public synchronized java.util.Collection<Group> getRoles() {
		return groups.values();
	}

	private User _addAccount(int id, String name, Group defaultRole) {
		if (users.containsKey(name))
			throw new IllegalArgumentException("User "+name+" exist.");
		
		User account = new UserImpl(id, name, defaultRole.getName()); //XXX: this as first arg
		users.put(name, account);
		
		return account;
	}

	@Override
	public User getAccount(String name) {
		return users.get(name);
	}

	@Override
	public java.util.Collection<User> getAccounts() {
		return users.values();
	}

	public synchronized void deleteAccount(User user) throws PermissionDeniedException {
		if(user == null)
			return;
		
		user = users.remove(user.getName());
//		if(user != null)
//			LOG.debug("user " + user.getName() + " removed");
//		else
//			LOG.debug("user not found");
		
		_save();
	}

	@Override
	public User authenticate(String accountName, Object credentials) throws AuthenticationException {
		User user = getAccount(accountName);
		if (user == null)
			throw new AuthenticationException("Acount " + accountName + " not found");
			
		User newUser = new UserImpl(this, (UserImpl)user, credentials);
			
		if (newUser.isAuthenticated())
			return newUser;

		throw new AuthenticationException("Wrong password for user [" + accountName + "] ");
	}
	
	private void _save() {
		DBBroker broker = null;
		TransactionManager transact = sm.getDatabase().getTransactionManager();
		Txn txn = transact.beginTransaction();
		try {
			broker = sm.getDatabase().get(null);
			_save(broker, txn);
			transact.commit(txn);
		} catch (EXistException e) {
			transact.abort(txn);
			e.printStackTrace();
		} finally {
			sm.getDatabase().release(broker);
		}
	}

	private synchronized void _save(DBBroker broker, Txn transaction) throws EXistException {
		//LOG.debug("storing acl file");
		StringBuffer buf = new StringBuffer();
        buf.append("<!-- Central user configuration. Editing this document will cause the security " +
                "to reload and update its internal database. Please handle with care! -->");
		buf.append("<auth version='1.0'>");
		
		// save groups
        buf.append("<!-- Please do not remove the guest and admin groups -->");
		buf.append("<groups>");
		for (Group group : groups.values())
			buf.append(group.toString());
		buf.append("</groups>");

		//save users
        buf.append("<!-- Please do not remove the admin user. -->");
		buf.append("<users>");
		for (User account : users.values())
			buf.append(account.toString());
		buf.append("</users>");
		buf.append("</auth>");
        
		// store users.xml
		//broker.flush();
		//broker.sync(Sync.MAJOR_SYNC);
		
		//User currentUser = broker.getUser();
		try {
			//broker.setUser(getUser(DBA_USER));
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
		} finally {
			//broker.setUser(currentUser);
		}
		
		broker.flush();
		broker.sync(Sync.MAJOR_SYNC);
	}
	
	private void createUserHome(DBBroker broker, Txn transaction, User account) 
	throws EXistException, PermissionDeniedException, IOException {
		if(account.getHome() == null)
			return;
		
		User currentUser = broker.getUser();
		
		try {
			broker.setUser(getAccount(SecurityManager.DBA_USER));
			Collection home = broker.getOrCreateCollection(transaction, account.getHome());
			home.getPermissions().setOwner(account);
			CollectionConfiguration config = home.getConfiguration(broker);
			String role = (config!=null) ? config.getDefCollGroup(account) : account.getPrimaryGroup();
			home.getPermissions().setGroup(role);
			broker.saveCollection(transaction, home);
		} finally {
			broker.setUser(currentUser);
		}
	}

	@Override
	public boolean hasAccount(String accountName) {
		// TODO Auto-generated method stub
		return false;
	}
}
