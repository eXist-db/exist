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

import org.exist.security.AbstractAccount;
import org.exist.security.AbstractRealm;
import org.exist.security.AbstractPrincipal;
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
    protected final GroupImpl GROUP_DBA;
    protected final GroupImpl GROUP_GUEST;

    protected final AccountImpl ACCOUNT_UNKNOW;
    protected final GroupImpl GROUP_UNKNOWN;

    protected RealmImpl(SecurityManagerImpl sm, Configuration config) throws ConfigurationException { //, Configuration conf

    	super(sm, config);

        //Build-in accounts
        GROUP_UNKNOWN = new GroupImpl(this, -1, "");
    	ACCOUNT_UNKNOW = new AccountImpl(this, -1, "", (String)null, GROUP_UNKNOWN);

    	//DBA group & account
    	GROUP_DBA = new GroupImpl(this, 1, SecurityManager.DBA_GROUP);
    	sm.groupsById.put(GROUP_DBA.getId(), GROUP_DBA);
    	groupsByName.put(GROUP_DBA.getName(), GROUP_DBA);

    	//System account
    	ACCOUNT_SYSTEM = new AccountImpl(this, 0, "SYSTEM", "", GROUP_DBA, true);
    	sm.usersById.put(ACCOUNT_SYSTEM.getId(), ACCOUNT_SYSTEM);
    	usersByName.put(ACCOUNT_SYSTEM.getName(), ACCOUNT_SYSTEM);

    	//Administrator account
    	AccountImpl ACCOUNT_ADMIN = new AccountImpl(this, 1, SecurityManager.DBA_USER, "", GROUP_DBA, true);
    	sm.usersById.put(ACCOUNT_ADMIN.getId(), ACCOUNT_ADMIN);
    	usersByName.put(ACCOUNT_ADMIN.getName(), ACCOUNT_ADMIN);

    	//Guest group & account
    	GROUP_GUEST = new GroupImpl(this, 2, SecurityManager.GUEST_GROUP);
    	sm.groupsById.put(GROUP_GUEST.getId(), GROUP_GUEST);
    	groupsByName.put(GROUP_GUEST.getName(), GROUP_GUEST);

    	ACCOUNT_GUEST = new AccountImpl(this, 2, SecurityManager.GUEST_USER, SecurityManager.GUEST_USER, GROUP_GUEST);
    	sm.usersById.put(ACCOUNT_GUEST.getId(), ACCOUNT_GUEST);
    	usersByName.put(ACCOUNT_GUEST.getName(), ACCOUNT_GUEST);
    	
    	sm.lastUserId = 3;
    	sm.lastGroupId = 3;
    }

	@Override
	public String getId() {
		return ID;
	}

    @Override
	public void startUp(DBBroker broker) throws EXistException {
		super.startUp(broker);
	}

    @Override
	public synchronized boolean deleteAccount(Subject invokingUser, Account account) throws PermissionDeniedException, EXistException {
		if(account == null)
			return false;
		
		AbstractAccount remove_account = (AbstractAccount)usersByName.get(account.getName());
		if (remove_account == null) return false;
		
		DBBroker broker = null;
		try {
			broker = getDatabase().get(null);
			Account user = broker.getUser();
			
			if ( ! (account.getName().equals(user.getName()) || user.hasDbaRole()) )
					throw new PermissionDeniedException(
						" you are not allowed to delete '"+account.getName()+"' user");

			remove_account.setRemoved(true);
			remove_account.setCollection(broker, collectionRemovedAccounts, XmldbURI.create(UUIDGenerator.getUUID()+".xml"));
			
	        TransactionManager transaction = getDatabase().getTransactionManager();
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
			
			getSecurityManager().addUser(remove_account.getId(), remove_account);
			usersByName.remove(remove_account.getName());

			return true;
		} finally {
			getDatabase().release(broker);
		}
	}

    @Override
	public synchronized boolean updateGroup(Group group) throws PermissionDeniedException {
		//nothing to do: the name or id can't be changed
		return false;
	}

    @Override
	public synchronized boolean deleteGroup(Group group) throws PermissionDeniedException, EXistException {
		if(group == null)
			return false;
		
		AbstractPrincipal remove_group = (AbstractPrincipal)groupsByName.get(group.getName());
		if (remove_group == null) return false;
		
		DBBroker broker = null;
		try {
			broker = getDatabase().get(null);
			Account user = broker.getUser();
			
			if ( ! ( user.hasDbaRole() ) )
					throw new PermissionDeniedException(
						" you ["+user.getName()+"] are not allowed to delete '"+remove_group.getName()+"' group");

			remove_group.setRemoved(true);
			remove_group.setCollection(broker, collectionRemovedGroups, XmldbURI.create(UUIDGenerator.getUUID()+".xml"));
			
	        TransactionManager transaction = getDatabase().getTransactionManager();
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
			
			getSecurityManager().addGroup(remove_group.getId(), (Group)remove_group);
			groupsByName.remove(remove_group.getName());

			return true;
		} finally {
			getDatabase().release(broker);
		}
	}

	@Override
	public synchronized Subject authenticate(String accountName, Object credentials) throws AuthenticationException {
		Account user = getAccount(null, accountName);
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

//    @Override
//    public GroupImpl instantiateGroup(AbstractRealm realm, Configuration config) throws ConfigurationException {
//        return new GroupImpl(realm, config);
//    }
//
//    @Override
//    public AccountImpl instantiateAccount(AbstractRealm realm, Configuration config) throws ConfigurationException {
//        return new AccountImpl(realm, config);
//    }
//
//    @Override
//    public GroupImpl instantiateGroup(AbstractRealm realm, Configuration config, boolean removed) throws ConfigurationException {
//        return new GroupImpl(realm, config, true);
//    }
//
//    @Override
//    public AccountImpl instantiateAccount(AbstractRealm realm, Configuration config, boolean removed) throws ConfigurationException {
//        return new AccountImpl(realm, config, true);
//    }
//
//    @Override
//    public GroupImpl instantiateGroup(AbstractRealm realm, int id, String name) throws ConfigurationException {
//        return new GroupImpl(realm, id, name);
//    }
//
//    @Override
//    public GroupImpl instantiateGroup(AbstractRealm realm, String name) throws ConfigurationException {
//        return new GroupImpl(realm, name);
//    }
//
//    @Override
//    public AccountImpl instantiateAccount(AbstractRealm realm, int id, Account from_account) throws ConfigurationException, PermissionDeniedException {
//        return new AccountImpl(realm, id, from_account);
//    }
//
//    @Override
//    public AccountImpl instantiateAccount(AbstractRealm realm, String username) throws ConfigurationException {
//        return new AccountImpl(realm, username);
//    }
}
