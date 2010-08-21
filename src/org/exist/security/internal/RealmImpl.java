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

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.dom.DocumentImpl;
import org.exist.security.AuthenticationException;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.security.Account;
import org.exist.security.SecurityManager;
import org.exist.security.realm.Realm;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class RealmImpl extends AbstractRealm implements Configurable {
	
	public static String ID = "exist"; //TODO: final "eXist-db";

	private final static Logger LOG = Logger.getLogger(Realm.class);

	static public void setPasswordRealm(String value) {
		ID = value;
	}

	protected final static String ACL_FILE = "users.xml";
	protected final static XmldbURI ACL_FILE_URI = XmldbURI.create(ACL_FILE); 
	   
    protected final Account ACCOUNT_SYSTEM;
    protected final Account ACCOUNT_GUEST;
    protected final Group GROUP_DBA;
    protected final Group GROUP_GUEST;

    protected final Account ACCOUNT_UNKNOW;
    protected final Group GROUP_UNKNOW;

    protected RealmImpl(SecurityManagerImpl sm, Configuration config) throws ConfigurationException { //, Configuration conf

    	super(sm, config);

		//Build-in accounts
		GROUP_UNKNOW = new GroupImpl(this, -1, "");
    	ACCOUNT_UNKNOW = new AccountImpl(this, -1, "", null);
    	ACCOUNT_UNKNOW.addGroup(GROUP_UNKNOW);

    	//DBA group & account
    	GROUP_DBA = new GroupImpl(this, 1, SecurityManager.DBA_GROUP);
    	sm.groupsById.put(GROUP_DBA.getId(), GROUP_DBA);
    	groupsByName.put(GROUP_DBA.getName(), GROUP_DBA);

    	//System account
    	ACCOUNT_SYSTEM = new AccountImpl(this, 0, "SYSTEM", "");
    	ACCOUNT_SYSTEM.addGroup(GROUP_DBA);
    	sm.usersById.put(ACCOUNT_SYSTEM.getId(), ACCOUNT_SYSTEM);
    	usersByName.put(ACCOUNT_SYSTEM.getName(), ACCOUNT_SYSTEM);

    	//Administrator account
    	AccountImpl ACCOUNT_ADMIN = new AccountImpl(this, 1, SecurityManager.DBA_USER, "");
    	ACCOUNT_ADMIN.addGroup(GROUP_DBA);
    	sm.usersById.put(ACCOUNT_ADMIN.getId(), ACCOUNT_ADMIN);
    	usersByName.put(ACCOUNT_ADMIN.getName(), ACCOUNT_ADMIN);

    	//Guest group & account
    	GROUP_GUEST = new GroupImpl(this, 2, SecurityManager.GUEST_GROUP);
    	sm.groupsById.put(GROUP_GUEST.getId(), GROUP_GUEST);
    	groupsByName.put(GROUP_GUEST.getName(), GROUP_GUEST);

    	ACCOUNT_GUEST = new AccountImpl(this, 2, SecurityManager.GUEST_USER, SecurityManager.GUEST_USER);
    	ACCOUNT_GUEST.addGroup(GROUP_GUEST);
    	sm.usersById.put(ACCOUNT_GUEST.getId(), ACCOUNT_GUEST);
    	usersByName.put(ACCOUNT_GUEST.getName(), ACCOUNT_GUEST);
    	
    	sm.lastUserId = 3;
    	sm.lastGroupId = 3;
	}

	@Override
	public String getId() {
		return ID;
	}

	public void startUp(DBBroker broker) throws EXistException {

		XmldbURI realmCollectionURL = SecurityManager.SECURITY_COLLETION_URI.append(getId());
		
		BrokerPool pool = broker.getBrokerPool();
		TransactionManager transact = pool.getTransactionManager();
		Txn txn = null;
		try {
			collectionRealm = broker.getCollection(realmCollectionURL);
			collectionAccounts = broker.getCollection(realmCollectionURL.append("accounts"));
			collectionGroups = broker.getCollection(realmCollectionURL.append("groups"));
			
			if (collectionRealm == null || collectionAccounts == null || collectionGroups == null) {
				txn = transact.beginTransaction();
				
				if (collectionRealm == null)
					collectionRealm    = Utils.createCollection(broker, txn, realmCollectionURL);
				
				if (collectionAccounts == null)
					collectionAccounts = Utils.createCollection(broker, txn, realmCollectionURL.append("accounts"));
				
				if (collectionGroups == null)
					collectionGroups   = Utils.createCollection(broker, txn, realmCollectionURL.append("groups"));

				transact.commit(txn);
			}
			
			for (Account account : usersByName.values()) {
				if (account.getId() > 0)
					((AbstractPrincipal)account).setCollection(broker, collectionAccounts);
			}
			
			for (Group group : groupsByName.values()) {
				if (group.getId() > 0)
					((AbstractPrincipal)group).setCollection(broker, collectionGroups);
			}
			
			//1.0 version
			Collection sysCollection = broker.getCollection(SecurityManager.SECURITY_COLLETION_URI);
			Document acl = sysCollection.getDocument(broker, ACL_FILE_URI);
			Element docElement = null;
			if (acl != null)
				docElement = acl.getDocumentElement();

			if (docElement != null) {
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
				
				Account account = null; Group group = null;
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
								account = AccountImpl.createAccount(this, major, minor, (Element) node);
								sm.usersById.put(account.getId(), account);
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
								sm.groupsById.put(group.getId(), group);
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

	private Group _addGroup(String name) throws ConfigurationException {
		if (groupsByName.containsKey(name))
			throw new IllegalArgumentException("Group "+name+" exist.");
		
		Group group = new GroupImpl(this, sm.getNextGroupId(), name);
		sm.groupsById.put(group.getId(), group);
		groupsByName.put(name, group);
		
		return group;
	}

	private Group _addGroup(int id, String name) throws ConfigurationException {
		if (groupsByName.containsKey(name))
			throw new IllegalArgumentException("Group "+name+" exist.");
		
		if (sm.groupsById.containsKey(id))
			throw new IllegalArgumentException("Group id "+id+" allready used.");

		Group group = new GroupImpl(this, id, name);
		sm.groupsById.put(id, group);
		groupsByName.put(name, group);
		
		return group;
	}

	public synchronized Group addGroup(String name) throws PermissionDeniedException, EXistException {
		Group created_group = _addGroup(name);
		
		((AbstractPrincipal)created_group).save();
		
		return created_group;
	}

	public synchronized Group addGroup(Group group) throws PermissionDeniedException, EXistException, IOException {
		return addGroup(group.getName());
	}

	public synchronized Account addAccount(Account account) throws PermissionDeniedException, EXistException, ConfigurationException {
		if (account.getRealmId() == null)
			throw new ConfigurationException("Account's realmId is null.");
		
		if (!getId().equals(account.getRealmId()))
			throw new ConfigurationException("Account from different realm");

		return sm.addAccount(account);
	}

	public synchronized boolean updateAccount(Account account) throws PermissionDeniedException, EXistException {
		DBBroker broker = null;
		try {
			broker = sm.getDatabase().get(null);
			Account user = broker.getUser();
			
			if ( ! (account.getName().equals(user.getName()) 
					|| user.hasDbaRole()) )
					throw new PermissionDeniedException(
						" you are not allowed to change '"+account.getName()+"' user");
	
	
			Account updatingAccount = getAccount(account.getName());
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
	
			((AbstractPrincipal)updatingAccount).save();
			
			return true;
		} finally {
			sm.getDatabase().release(broker);
		}
	}

	public synchronized boolean deleteAccount(Account user) throws PermissionDeniedException, EXistException {
		if(user == null)
			return false;
		
		//lock and check for documents & collestions it can be owner
//		sm.usersById.remove(user.getId());
//		usersByName.remove(user.getName());
//
//		_save();
		
		return false;
	}

	public synchronized boolean deleteRole(String name) throws PermissionDeniedException, EXistException {
		if(name == null)
			return false;
		
		//lock and check for documents & collestions it can be owner
//		Group role = groupsByName.get(name);
//		if (role == null)
//			return false;
//		
//		sm.groupsById.remove(role.getId());
//		groupsByName.remove(role.getName());
//
//		_save();
		
		return false;
	}

	public synchronized boolean updateGroup(Group role) throws PermissionDeniedException {
		throw new PermissionDeniedException("not implemented");
		//TODO: code
	}

	public synchronized boolean deleteGroup(Group role) throws PermissionDeniedException, EXistException {
		return deleteRole(role.getName());
	}
	
	@Override
	public synchronized Subject authenticate(String accountName, Object credentials) throws AuthenticationException {
		Account user = getAccount(accountName);
		if (user == null)
			throw new AuthenticationException(
					AuthenticationException.ACCOUNT_NOT_FOUND,
					"Acount " + accountName + " not found");
			
		Subject newUser = new SubjectImpl((AccountImpl) user, credentials);
			
		if (newUser.isAuthenticated())
			return newUser;

		throw new AuthenticationException(
				AuthenticationException.WRONG_PASSWORD,
				"Wrong password for user [" + accountName + "] ");
	}
	
	private void __save() throws PermissionDeniedException, EXistException {
		DBBroker broker = null;
		TransactionManager transact = sm.getDatabase().getTransactionManager();
		Txn txn = transact.beginTransaction();
		try {
			broker = sm.getDatabase().get(null);
			_save(broker, txn);
			transact.commit(txn);
		} catch (EXistException e) {
			transact.abort(txn);
			
			if (LOG.isDebugEnabled()) {
				LOG.debug(e.getMessage());
			}
			
			e.printStackTrace();
			
			throw e;
		} finally {
			sm.getDatabase().release(broker);
		}
	}

	private synchronized void _save(DBBroker broker, Txn transaction) throws EXistException, PermissionDeniedException {
		//LOG.debug("storing acl file");
		StringBuffer buf = new StringBuffer();
        buf.append("<!-- Central user configuration. Editing this document will cause the security " +
                "to reload and update its internal database. Please handle with care! -->");
		buf.append("<auth version='1.0'>");
		
		// save groups
        buf.append("<!-- Please do not remove the guest and admin groups -->");
		buf.append("<groups last-id='"+sm.lastGroupId+"'>");
		for (Group group : groupsByName.values())
			buf.append(group.toString());
		buf.append("</groups>");

		//save users
        buf.append("<!-- Please do not remove the admin user. -->");
		buf.append("<users last-id='"+sm.lastUserId+"'>");
		for (Account account : usersByName.values())
			buf.append(account.toString());
		buf.append("</users>");
		buf.append("</auth>");
        
		// store users.xml
		//broker.flush();
		//broker.sync(Sync.MAJOR_SYNC);
		
		Subject currentUser = broker.getUser();
		try {
			broker.setUser(sm.getSystemSubject());
			Collection sysCollection = broker.getCollection(XmldbURI.SYSTEM_COLLECTION_URI);
            String data = buf.toString();
            IndexInfo info = sysCollection.validateXMLResource(transaction, broker, ACL_FILE_URI, data);
            //TODO : unlock the collection here ?
            DocumentImpl doc = info.getDocument();
            doc.getMetadata().setMimeType(MimeType.XML_TYPE.getName());
            sysCollection.store(transaction, broker, info, data, false);
			doc.setPermissions(0770);
			broker.saveCollection(transaction, doc.getCollection());
		} catch (PermissionDeniedException e) {
			throw e;
		} catch (Exception e) {
			throw new EXistException(e.getMessage());
		} finally {
			broker.setUser(currentUser);
		}
		
		broker.flush();
		broker.sync(Sync.MAJOR_SYNC);
	}

}
