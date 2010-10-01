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

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.security.AuthenticationException;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.security.Account;
import org.exist.security.SecurityManager;
import org.exist.security.UUIDGenerator;
import org.exist.security.realm.Realm;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class RealmImpl extends AbstractRealm {
	
	public static String ID = "exist"; //TODO: final "eXist-db";

	private final static Logger LOG = Logger.getLogger(Realm.class);

	static public void setPasswordRealm(String value) {
		ID = value;
	}

    protected final AccountImpl ACCOUNT_SYSTEM;
    protected final AccountImpl ACCOUNT_GUEST;
    protected final Group GROUP_DBA;
    protected final Group GROUP_GUEST;

    protected final AccountImpl ACCOUNT_UNKNOW;
    protected final Group GROUP_UNKNOW;

    protected RealmImpl(SecurityManagerImpl sm, Configuration config) throws ConfigurationException { //, Configuration conf

    	super(sm, config);

		//Build-in accounts
		GROUP_UNKNOW = new GroupImpl(this, -1, "");
    	ACCOUNT_UNKNOW = new AccountImpl(this, -1, "", (String)null);
    	ACCOUNT_UNKNOW.groups.add(GROUP_UNKNOW);

    	//DBA group & account
    	GROUP_DBA = new GroupImpl(this, 1, SecurityManager.DBA_GROUP);
    	sm.groupsById.put(GROUP_DBA.getId(), GROUP_DBA);
    	groupsByName.put(GROUP_DBA.getName(), GROUP_DBA);

    	//System account
    	ACCOUNT_SYSTEM = new AccountImpl(this, 0, "SYSTEM", "");
    	ACCOUNT_SYSTEM.groups.add(GROUP_DBA);
    	ACCOUNT_SYSTEM.hasDbaRole = true;
    	sm.usersById.put(ACCOUNT_SYSTEM.getId(), ACCOUNT_SYSTEM);
    	usersByName.put(ACCOUNT_SYSTEM.getName(), ACCOUNT_SYSTEM);

    	//Administrator account
    	AccountImpl ACCOUNT_ADMIN = new AccountImpl(this, 1, SecurityManager.DBA_USER, "");
    	ACCOUNT_ADMIN.groups.add(GROUP_DBA);
    	ACCOUNT_ADMIN.hasDbaRole = true;
    	sm.usersById.put(ACCOUNT_ADMIN.getId(), ACCOUNT_ADMIN);
    	usersByName.put(ACCOUNT_ADMIN.getName(), ACCOUNT_ADMIN);

    	//Guest group & account
    	GROUP_GUEST = new GroupImpl(this, 2, SecurityManager.GUEST_GROUP);
    	sm.groupsById.put(GROUP_GUEST.getId(), GROUP_GUEST);
    	groupsByName.put(GROUP_GUEST.getName(), GROUP_GUEST);

    	ACCOUNT_GUEST = new AccountImpl(this, 2, SecurityManager.GUEST_USER, SecurityManager.GUEST_USER);
    	ACCOUNT_GUEST.groups.add(GROUP_GUEST);
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
			
			if ( ! (account.getName().equals(user.getName()) || user.hasDbaRole()) )
					throw new PermissionDeniedException(
						" you are not allowed to change '"+account.getName()+"' account");
	
	
			Account updatingAccount = getAccount(account.getName());
			if (updatingAccount == null)
				throw new PermissionDeniedException( //XXX: different exception
					"account " + account.getName() + " does not exist");

			//check: add account to group 
			String[] groups = account.getGroups();
			for (int i = 0; i < groups.length; i++) {
				if (!(updatingAccount.hasGroup(groups[i]))) {
						updatingAccount.addGroup(groups[i]);
					}
			}
			//check: remove account from group 
			groups = updatingAccount.getGroups();
			for (int i = 0; i < groups.length; i++) {
				if (!(account.hasGroup(groups[i]))) {
						if ( !user.hasDbaRole() )
							throw new PermissionDeniedException(
								"not allowed to change group memberships");
						
						updatingAccount.remGroup(groups[i]);
					}
			}
				
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
		
		AbstractAccount remove_account = (AbstractAccount)usersByName.get(account.getName());
		if (remove_account == null) return false;
		
		DBBroker broker = null;
		try {
			broker = sm.getDatabase().get(null);
			Account user = broker.getUser();
			
			if ( ! (account.getName().equals(user.getName()) || user.hasDbaRole()) )
					throw new PermissionDeniedException(
						" you are not allowed to delete '"+account.getName()+"' user");

			remove_account.removed = true;
			remove_account.setCollection(broker, collectionRemovedAccounts, XmldbURI.create(UUIDGenerator.getUUID()+".xml"));
			
	        TransactionManager transaction = sm.getDatabase().getTransactionManager();
	        Txn txn = null;
	        try {
				txn = transaction.beginTransaction();
	
				collectionAccounts.removeXMLResource(
						txn, 
						broker, 
						XmldbURI.create( remove_account.getName()+".xml" ) );

				transaction.commit(txn);
	        } catch (Exception e) {
				transaction.abort(txn);
				e.printStackTrace();
				LOG.debug("loading configuration failed: " + e.getMessage());
			}
			
			sm.usersById.put(remove_account.getId(), remove_account);
			usersByName.remove(remove_account.getName());

			return true;
		} finally {
			sm.getDatabase().release(broker);
		}
	}

	public synchronized boolean updateGroup(Group group) throws PermissionDeniedException {
		//nothing to do: the name or id can't be changed
		return false;
	}

	public synchronized boolean deleteGroup(Group group) throws PermissionDeniedException, EXistException {
		if(group == null)
			return false;
		
		AbstractPrincipal remove_group = (AbstractPrincipal)groupsByName.get(group.getName());
		if (remove_group == null) return false;
		
		DBBroker broker = null;
		try {
			broker = sm.getDatabase().get(null);
			Account user = broker.getUser();
			
			if ( ! ( user.hasDbaRole() ) )
					throw new PermissionDeniedException(
						" you ["+user.getName()+"] are not allowed to delete '"+remove_group.getName()+"' group");

			remove_group.removed = true;
			remove_group.setCollection(broker, collectionRemovedGroups, XmldbURI.create(UUIDGenerator.getUUID()+".xml"));
			
	        TransactionManager transaction = sm.getDatabase().getTransactionManager();
	        Txn txn = null;
	        try {
				txn = transaction.beginTransaction();
	
				collectionGroups.removeXMLResource(
						txn, 
						broker, 
						XmldbURI.create( remove_group.getName()+".xml" ) );

				transaction.commit(txn);
	        } catch (Exception e) {
				transaction.abort(txn);
				e.printStackTrace();
				LOG.debug("loading configuration failed: " + e.getMessage());
			}
			
			sm.groupsById.put(remove_group.getId(), (Group)remove_group);
			groupsByName.remove(remove_group.getName());

			return true;
		} finally {
			sm.getDatabase().release(broker);
		}
	}
	
	@Override
	public synchronized Subject authenticate(String accountName, Object credentials) throws AuthenticationException {
		Account user = getAccount(accountName);
		if (user == null)
			throw new AuthenticationException(
					AuthenticationException.ACCOUNT_NOT_FOUND,
					"Acount '" + accountName + "' not found");
			
		Subject newUser = new SubjectImpl((AccountImpl) user, credentials);
			
		if (newUser.isAuthenticated())
			return newUser;

		throw new AuthenticationException(
				AuthenticationException.WRONG_PASSWORD,
				"Wrong password for user [" + accountName + "] ");
	}
}
