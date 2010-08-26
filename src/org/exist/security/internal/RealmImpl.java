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

import java.util.Iterator;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.Configurator;
import org.exist.dom.DocumentImpl;
import org.exist.security.AuthenticationException;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.security.Account;
import org.exist.security.SecurityManager;
import org.exist.security.realm.Realm;
import org.exist.security.utils.Utils;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;

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
    	ACCOUNT_UNKNOW = new AccountImpl(this, -1, "", (String)null);
    	ACCOUNT_UNKNOW.addGroup(GROUP_UNKNOW);

    	//DBA group & account
    	GROUP_DBA = new GroupImpl(this, 1, SecurityManager.DBA_GROUP);
    	sm.groupsById.put(GROUP_DBA.getId(), GROUP_DBA);
    	groupsByName.put(GROUP_DBA.getName(), GROUP_DBA);

    	//System account
    	ACCOUNT_SYSTEM = new AccountImpl(this, 0, "SYSTEM", "");
    	ACCOUNT_SYSTEM.addGroup(GROUP_DBA);
    	sm.usersById.put(ACCOUNT_SYSTEM.getId(), ACCOUNT_SYSTEM);
    	//usersByName.put(ACCOUNT_SYSTEM.getName(), ACCOUNT_SYSTEM);

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
			super.startUp(broker);
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

	public synchronized Group addGroup(Group group) throws PermissionDeniedException, EXistException {
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

	public synchronized boolean deleteAccount(Account account) throws PermissionDeniedException, EXistException {
		if(account == null)
			return false;
		
		//XXX: lock and check for documents & collestions it can be owner
		sm.usersById.remove(account.getId());
		usersByName.remove(account.getName());

//		_save();
		
		return false;
	}

	public synchronized boolean deleteRole(String name) throws PermissionDeniedException, EXistException {
		if(name == null)
			return false;
		
		//XXX:lock and check for documents & collestions it can be owner
		Group group = groupsByName.get(name);
		if (group == null)
			return false;
		
		sm.groupsById.remove(group.getId());
		groupsByName.remove(group.getName());

//		_save();
		
		return false;
	}

	public synchronized boolean updateGroup(Group group) throws PermissionDeniedException {
		//nothing to do: the name or id can't be changed
		return false;
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
			doc.setPermissions(0770);
            sysCollection.store(transaction, broker, info, data, false);
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
