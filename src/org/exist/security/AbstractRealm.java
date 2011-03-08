/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2011 The eXist Project
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
package org.exist.security;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.exist.Database;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.Configurator;
import org.exist.dom.DocumentImpl;
import org.exist.security.internal.AccountImpl;
import org.exist.security.internal.GroupImpl;
import org.exist.security.realm.Realm;
import org.exist.security.utils.Utils;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public abstract class AbstractRealm implements Realm, Configurable {

	//XXX: this class must be under org.exist.security.internal to be protected
	public final Map<String, Account> usersByName = new HashMap<String, Account>(65);
	public final Map<String, Group> groupsByName = new HashMap<String, Group>(65);

	private SecurityManager sm;
	
	protected Configuration configuration;
	
	protected Collection collectionRealm = null;
	protected Collection collectionAccounts = null;
	protected Collection collectionGroups = null;
	protected Collection collectionRemovedAccounts = null;
	protected Collection collectionRemovedGroups = null;
	
	public AbstractRealm(SecurityManager sm, Configuration config) {
		this.sm = sm;
		
		configuration = Configurator.configure(this, config);
	}

	@Override
	public Database getDatabase() {
		return getSecurityManager().getDatabase();
	}

    @Override
    public SecurityManager getSecurityManager() {
        return sm;
    }

    @Override
	public void startUp(DBBroker broker) throws EXistException {

		XmldbURI realmCollectionURL = SecurityManager.SECURITY_COLLETION_URI.append(getId());
		
		BrokerPool pool = broker.getBrokerPool();
		TransactionManager transact = pool.getTransactionManager();
		Txn txn = null;
		try {
			collectionRealm = broker.getCollection(realmCollectionURL);
			
			collectionAccounts = broker.getCollection(realmCollectionURL.append("accounts"));
			collectionGroups = broker.getCollection(realmCollectionURL.append("groups"));

			collectionRemovedAccounts = broker.getCollection(realmCollectionURL.append("accounts").append("removed"));
			collectionRemovedGroups = broker.getCollection(realmCollectionURL.append("groups").append("removed"));
			
			if (collectionRealm == null || 
					collectionAccounts == null || collectionGroups == null ||
					collectionRemovedAccounts == null || collectionRemovedGroups == null) {
				txn = transact.beginTransaction();

				try {
					if (collectionRealm == null)
						collectionRealm = Utils.createCollection(broker, txn, realmCollectionURL);
					
					if (collectionAccounts == null)
						collectionAccounts = Utils.createCollection(broker, txn, realmCollectionURL.append("accounts"));
					
					if (collectionGroups == null)
						collectionGroups = Utils.createCollection(broker, txn, realmCollectionURL.append("groups"));
	
					if (collectionRemovedAccounts == null)
						collectionRemovedAccounts = Utils.createCollection(broker, txn, realmCollectionURL.append("accounts").append("removed"));
					
					if (collectionRemovedGroups == null)
						collectionRemovedGroups = Utils.createCollection(broker, txn, realmCollectionURL.append("groups").append("removed"));
	
					transact.commit(txn);
				} catch (Exception e) {
					transact.abort(txn);
					e.printStackTrace();
				}
			}
           
            //load groups information
	        if (collectionGroups != null && collectionGroups.getDocumentCount() > 0) {
	            for(Iterator<DocumentImpl> i = collectionGroups.iterator(broker); i.hasNext(); ) {
	            	Configuration conf = Configurator.parse(i.next());

	            	String name = conf.getProperty("name");
	            	if (name != null && !groupsByName.containsKey(name)) {
	            		
//                      Group group = instantiateGroup(this, conf);
                        GroupImpl group = new GroupImpl(this, conf);

	            		getSecurityManager().addGroup(group.getId(), group);
	            		groupsByName.put(group.getName(), group);
	            	}
	            }
	        }

	        //load marked for remove groups information
	        if (collectionRemovedGroups != null && collectionRemovedGroups.getDocumentCount() > 0) {
	            for(Iterator<DocumentImpl> i = collectionRemovedGroups.iterator(broker); i.hasNext(); ) {
	            	Configuration conf = Configurator.parse(i.next());

	            	Integer id = conf.getPropertyInteger("id");
	            	if (id != null && !getSecurityManager().hasGroup(id)) {
//	            		G group = instantiateGroup(this, conf, true);
                        GroupImpl group = new GroupImpl(this, conf);
	            		group.removed = true;
//	            		getSecurityManager().addGroup(group.getId(), group);
	            	}
	            }
	        }

	        //load accounts information
	        if (collectionAccounts != null && collectionAccounts.getDocumentCount() > 0) {
	            for(Iterator<DocumentImpl> i = collectionAccounts.iterator(broker); i.hasNext(); ) {
	            	Configuration conf = Configurator.parse(i.next());
	            	
	            	String name = conf.getProperty("name");
	            	if (name != null && !usersByName.containsKey(name)) {
//                        A account = instantiateAccount(this, conf);
	            		Account account = new AccountImpl( this, conf );
		            	getSecurityManager().addUser(account.getId(), account);
		            	usersByName.put(account.getName(), account);
	            	}
	            }
	        }
	        
			//load marked for remove accounts information
	        if (collectionRemovedAccounts != null && collectionRemovedAccounts.getDocumentCount() > 0) {
	            for(Iterator<DocumentImpl> i = collectionRemovedAccounts.iterator(broker); i.hasNext(); ) {
	            	Configuration conf = Configurator.parse(i.next());
	            	
	            	Integer id = conf.getPropertyInteger("id");
	            	if (id != null && !getSecurityManager().hasUser(id)) {
//                        A account = instantiateAccount(this, conf, true);
	            		AccountImpl account = new AccountImpl( this, conf );
	            		account.removed = true;
		            	getSecurityManager().addUser(account.getId(), account);
	            	}
	            }
	        }


            //set collections
			for (Group group : groupsByName.values()) {
				if (group.getId() > 0)
					((AbstractPrincipal)group).setCollection(broker, collectionGroups);
			}

            for (Account account : usersByName.values()) {
				if (account.getId() > 0)
					((AbstractPrincipal)account).setCollection(broker, collectionAccounts);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void save() throws PermissionDeniedException, EXistException, IOException {
		configuration.save();
	}

	//Accounts management methods
	public final synchronized Account registerAccount(Account account)  {
		if (usersByName.containsKey(account.getName()))
			throw new IllegalArgumentException("Account "+account.getName()+" exist.");
		
		usersByName.put(account.getName(), account);
		
		return account;
	}

	@Override
	public synchronized Account getAccount(Subject invokingUser, String name) {
		return usersByName.get(name);
	}

	@Override
	public final synchronized boolean hasAccount(String accountName) {
		return usersByName.containsKey(accountName);
	}

	@Override
	public final synchronized boolean hasAccount(Account account) {
		return usersByName.containsKey(account.getName());
	}

	@Override
	public final synchronized java.util.Collection<Account> getAccounts() {
		return usersByName.values();
	}

	//Groups management methods
        @Override
	public final synchronized boolean hasGroup(String name) {
		return groupsByName.containsKey(name);
	}

        @Override
	public final synchronized boolean hasGroup(Group role) {
		return groupsByName.containsKey(role.getName());
	}

        @Override
	public synchronized Group getGroup(Subject invokingUser, String name) {
		return groupsByName.get(name);
	}
	
    @Override
	public final synchronized java.util.Collection<Group> getRoles() {
		return groupsByName.values();
	}

    @Override
	public final synchronized java.util.Collection<Group> getGroups() {
		return groupsByName.values();
	}

    //configuration methods
	@Override
	public boolean isConfigured() {
		return (configuration != null);
	}

	@Override
	public Configuration getConfiguration() {
		return configuration;
	}
	
	//collections related methods
	protected Collection getCollection() {
		return collectionRealm;
	}

    private Group _addGroup(String name, List<Account> managers) throws ConfigurationException {
            if (groupsByName.containsKey(name))
                    throw new IllegalArgumentException("Group "+name+" exist.");

            Group group = new GroupImpl(this, sm.getNextGroupId(), name, managers);
//            Group group = instantiateGroup(this, getSecurityManager().getNextGroupId(), name);
            getSecurityManager().addGroup(group.getId(), group);
            groupsByName.put(name, group);

            return group;
    }

    private Group _addGroup(int id, String name, List<Account> managers) throws ConfigurationException {
        if (groupsByName.containsKey(name))
                throw new IllegalArgumentException("Group "+name+" exist.");

        if (getSecurityManager().hasGroup(id))
                throw new IllegalArgumentException("Group id "+id+" allready used.");

        Group group = new GroupImpl(this, id, name, managers);
//      G group = instantiateGroup(this, id, name, managers);
        getSecurityManager().addGroup(id, group);
        groupsByName.put(name, group);

        return group;
    }

    public synchronized Group addGroup(String name, List<Account> managers) throws PermissionDeniedException, EXistException {
        Group created_group = _addGroup(name, managers);

        ((AbstractPrincipal)created_group).save();

        return created_group;
    }

    @Override
    public synchronized Group addGroup(Group group) throws PermissionDeniedException, EXistException {
        return addGroup(group.getName(), group.getManagers());
    }

    @Override
    public synchronized Account addAccount(Account account) throws PermissionDeniedException, EXistException, ConfigurationException {
        if (account.getRealmId() == null)
                throw new ConfigurationException("Account's realmId is null.");

        if (!getId().equals(account.getRealmId()))
                throw new ConfigurationException("Account from different realm");

        return getSecurityManager().addAccount(account);
    }

    @Override
    public synchronized boolean updateAccount(Subject invokingUser, Account account) throws PermissionDeniedException, EXistException {
        DBBroker broker = null;
        try {
        	Account user = getDatabase().getSubject();

            if ( ! (account.getName().equals(user.getName()) || user.hasDbaRole()) )
                            throw new PermissionDeniedException(
                                    " you are not allowed to change '"+account.getName()+"' account");


            Account updatingAccount = getAccount(invokingUser, account.getName());
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

                if(!(account.hasGroup(groups[i]))) {
                        updatingAccount.remGroup(groups[i]);
                }
            }

            updatingAccount.setPassword(account.getPassword());
            updatingAccount.setHome(account.getHome());

            ((AbstractPrincipal)updatingAccount).save();

            return true;
        } finally {
            if(broker != null) {
                getDatabase().release(broker);
            }
        }
    }

    @Override
    public synchronized boolean updateGroup(Subject invokingUser, Group group) throws PermissionDeniedException, EXistException {

        DBBroker broker = null;
        try {
            broker = getDatabase().get(null);
            Account user = broker.getSubject();

            if(!(group.isManager(user) || user.hasDbaRole()) ) {
                throw new PermissionDeniedException(" you are not allowed to change '" + group.getName() + "' group");
            }

            Group updatingGroup = getGroup(invokingUser, group.getName());
            if(updatingGroup == null) {
                throw new PermissionDeniedException("group " + group.getName() + " does not exist");
            }

            //check: add account to group
            for(Account manager : group.getManagers()) {
                if(!updatingGroup.isManager(manager)) {
                    updatingGroup.addManager(manager);
                }
            }

            //check: remove account from group
            for(Account manager : updatingGroup.getManagers()){
                if(!group.isManager(manager)) {
                    updatingGroup.removeManager(manager);
                }
            }

            group.save();

           return true;
        } finally {
            if(broker != null) {
                getDatabase().release(broker);
            }
        }
    }

    @Override
    public Group getExternalGroup(Subject invokingUser, String name) {
        return getSecurityManager().getGroup(invokingUser, name);
    }
}
