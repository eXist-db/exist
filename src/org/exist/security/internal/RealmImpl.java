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
import org.exist.util.hashtable.Int2ObjectHashMap;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class RealmImpl implements Realm {
	
	public static String ID = "exist"; //TODO: final "eXist-db";

	static public void setPasswordRealm(String value) {
		ID = value;
	}

	protected final static String ACL_FILE = "users.xml";
	protected final static XmldbURI ACL_FILE_URI = XmldbURI.create(ACL_FILE);
	   
	private Map<String, Group> groupsByName = new HashMap<String, Group>(65);
	private Map<String, User> usersByName = new HashMap<String, User>(65);

	private Int2ObjectHashMap<Group> groupsById = new Int2ObjectHashMap<Group>(65);
	private Int2ObjectHashMap<User> usersById = new Int2ObjectHashMap<User>(65);

	private SecurityManagerImpl sm;
//	Configuration configuration;

    protected final User ACCOUNT_SYSTEM;
    protected final User ACCOUNT_GUEST;
    protected final Group GROUP_DBA;
    protected final Group GROUP_GUEST;

    protected final User ACCOUNT_UNKNOW;
    protected final Group GROUP_UNKNOW;

    protected RealmImpl(SecurityManagerImpl sm) { //, Configuration conf

//		configuration = Configurator.configure(this, conf);

		this.sm = sm;

		//Build-in accounts
		GROUP_UNKNOW = new GroupImpl("", 0);
    	ACCOUNT_UNKNOW = new UserImpl(this, 0, "", null);
    	ACCOUNT_UNKNOW.addGroup(GROUP_UNKNOW);

    	//DBA group & account
    	GROUP_DBA = new GroupImpl(SecurityManager.DBA_GROUP, 1);
    	groupsById.put(GROUP_DBA.getId(), GROUP_DBA);
    	groupsByName.put(GROUP_DBA.getName(), GROUP_DBA);

    	ACCOUNT_SYSTEM = new UserImpl(this, 1, SecurityManager.DBA_USER, "");
    	ACCOUNT_SYSTEM.addGroup(GROUP_DBA);
    	usersById.put(ACCOUNT_SYSTEM.getUID(), ACCOUNT_SYSTEM);
    	usersByName.put(ACCOUNT_SYSTEM.getName(), ACCOUNT_SYSTEM);

    	//Guest group & account
    	GROUP_GUEST = new GroupImpl(SecurityManager.GUEST_GROUP, 2);
    	groupsById.put(GROUP_GUEST.getId(), GROUP_GUEST);
    	groupsByName.put(GROUP_GUEST.getName(), GROUP_GUEST);

    	ACCOUNT_GUEST = new UserImpl(this, 2, SecurityManager.GUEST_USER, SecurityManager.GUEST_USER);
    	ACCOUNT_GUEST.addGroup(GROUP_GUEST);
    	usersById.put(ACCOUNT_GUEST.getUID(), ACCOUNT_GUEST);
    	usersByName.put(ACCOUNT_GUEST.getName(), ACCOUNT_GUEST);
    	
    	sm.nextUserId = 3;
    	sm.nextGroupId = 3;
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

	public void startUp(DBBroker broker) throws EXistException {

		BrokerPool pool = broker.getBrokerPool();
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
								account = new UserImpl(this, major, minor, (Element) node);
								usersById.put(account.getUID(), account);
								usersByName.put(account.getName(), account);
							}
						}
					} else if (next.getTagName().equals("groups")) {
						ul = next.getChildNodes();
						for (int j = 0; j < ul.getLength(); j++) {
							node = ul.item(j);
							if (node.getNodeType() == Node.ELEMENT_NODE
									&& node.getLocalName().equals("group")) {
								group = new GroupImpl((Element) node);
								groupsById.put(group.getId(), group);
								groupsByName.put(group.getName(), group);
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
		if (groupsByName.containsKey(name))
			throw new IllegalArgumentException("Group "+name+" exist.");
		
		Group group = new GroupImpl(name, sm.getNextGroupId());
		groupsById.put(group.getId(), group);
		groupsByName.put(name, group);
		
		return group;
	}

	private Group _addGroup(int id, String name) {
		if (groupsByName.containsKey(name))
			throw new IllegalArgumentException("Group "+name+" exist.");
		
		if (groupsById.containsKey(id))
			throw new IllegalArgumentException("Group id "+id+" allready used.");

		Group group = new GroupImpl(name, id);
		groupsById.put(id, group);
		groupsByName.put(name, group);
		
		return group;
	}

	public synchronized Group addGroup(String name) {
		Group group = _addGroup(name);
		
		_save();
		
		return group;
	}

	public synchronized boolean hasRole(String name) {
		return groupsByName.containsKey(name);
	}

	public synchronized Group getRole(String name) {
		return groupsByName.get(name);
	}
	
	public synchronized java.util.Collection<Group> getRoles() {
		return groupsByName.values();
	}

	private User _addAccount(int id, User account) {
		if (usersByName.containsKey(account.getName()))
			throw new IllegalArgumentException("User "+account.getName()+" exist.");
		
		if (usersById.containsKey(id))
			throw new IllegalArgumentException("User's id "+id+" allready used.");

		User new_account = new UserImpl(this, id, account);
		usersById.put(id, new_account);
		usersByName.put(new_account.getName(), new_account);
		
		_save();

		return account;
	}

	public synchronized User addAccount(String name) {
		return _addAccount(sm.getNextAccoutId(), new UserImpl(this, name));
	}

	public synchronized User addAccount(User account) {
		User added = _addAccount(sm.getNextAccoutId(), account);
		
		return added;
	}

	public synchronized boolean updateAccount(User account) throws PermissionDeniedException, EXistException {
		DBBroker broker = null;
		try {
			broker = sm.getDatabase().get(null);
			User user = broker.getUser();
			
			if ( ! (account.getName().equals(user.getName()) 
					|| user.hasDbaRole()) )
					throw new PermissionDeniedException(
						" you are not allowed to change '"+account.getName()+"' user");
	
	
			User updatingAccount = getAccount(account.getName());
			if (updatingAccount == null)
				throw new PermissionDeniedException( //XXX: different exception
					"user " + account.getName() + " does not exist");
				
			String[] groups = account.getGroups();
			for (int i = 0; i < groups.length; i++) {
				if (!(updatingAccount.hasGroup(groups[i]))) {
						if ( !user.hasDbaRole() )
							throw new PermissionDeniedException(
								"not allowed to change group memberships");
						
						updatingAccount.addGroup(groups[i]);
					}
			}
			//XXX: delete account's group
				
			updatingAccount.setPassword(account.getPassword());
	
			return true;
		} finally {
			sm.getDatabase().release(broker);
		}
	}

	@Override
	public synchronized User getAccount(String name) {
		return usersByName.get(name);
	}

	@Override
	public synchronized java.util.Collection<User> getAccounts() {
		return usersByName.values();
	}

	public synchronized void deleteAccount(User user) throws PermissionDeniedException {
		if(user == null)
			return;
		
		usersById.remove(user.getUID());
		usersByName.remove(user.getName());

//		if(user != null)
//			LOG.debug("user " + user.getName() + " removed");
//		else
//			LOG.debug("user not found");
		
		_save();
	}

	public synchronized void deleteRole(String name) throws PermissionDeniedException {
		if(name == null)
			return;
		
		Group role = groupsByName.get(name);
		if (role == null)
			return;
		
		groupsById.remove(role.getId());
		groupsByName.remove(role.getName());

		_save();
	}

	@Override
	public synchronized User authenticate(String accountName, Object credentials) throws AuthenticationException {
		User user = getAccount(accountName);
		if (user == null)
			throw new AuthenticationException(
					AuthenticationException.ACCOUNT_NOT_FOUND,
					"Acount " + accountName + " not found");
			
		User newUser = new UserImpl(this, (UserImpl)user, credentials);
			
		if (newUser.isAuthenticated())
			return newUser;

		throw new AuthenticationException(
				AuthenticationException.WRONG_PASSWORD,
				"Wrong password for user [" + accountName + "] ");
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
		buf.append("<groups last-id='"+sm.nextGroupId+"'>");
		for (Group group : groupsByName.values())
			buf.append(group.toString());
		buf.append("</groups>");

		//save users
        buf.append("<!-- Please do not remove the admin user. -->");
		buf.append("<users last-id='"+sm.nextUserId+"'>");
		for (User account : usersByName.values())
			buf.append(account.toString());
		buf.append("</users>");
		buf.append("</auth>");
        
		// store users.xml
		//broker.flush();
		//broker.sync(Sync.MAJOR_SYNC);
		
		User currentUser = broker.getUser();
		try {
			broker.setUser(ACCOUNT_SYSTEM);
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
			broker.setUser(currentUser);
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
		return usersByName.containsKey(accountName);
	}

	@Override
	public synchronized User getAccount(int id) {
		return usersById.get(id);
	}

	@Override
	public synchronized Group getRole(int id) {
		return groupsById.get(id);
	}
}
