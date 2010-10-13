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
import java.util.Iterator;
import java.util.Map;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.dom.DocumentImpl;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Account;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
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

	protected final Map<String, Group> groupsByName = new HashMap<String, Group>(65);
	protected final Map<String, Account> usersByName = new HashMap<String, Account>(65);

	protected SecurityManagerImpl sm;
	
	protected Configuration configuration;
	
	protected Collection collectionRealm = null;
	protected Collection collectionAccounts = null;
	protected Collection collectionGroups = null;
	protected Collection collectionRemovedAccounts = null;
	protected Collection collectionRemovedGroups = null;
	
	public AbstractRealm(SecurityManagerImpl sm, Configuration config) {
		this.sm = sm;
		
		configuration = Configurator.configure(this, config);
	}

	@Override
	public BrokerPool getDatabase() {
		return sm.getDatabase();
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
			
			for (Account account : usersByName.values()) {
				if (account.getId() > 0)
					((AbstractPrincipal)account).setCollection(broker, collectionAccounts);
			}
			
			for (Group group : groupsByName.values()) {
				if (group.getId() > 0)
					((AbstractPrincipal)group).setCollection(broker, collectionGroups);
			}
			
	        //load groups information
	        if (collectionGroups != null && collectionGroups.getDocumentCount() > 0) {
	            for(Iterator<DocumentImpl> i = collectionGroups.iterator(broker); i.hasNext(); ) {
	            	Configuration conf = Configurator.parse(i.next());

	            	String name = conf.getProperty("name");
	            	if (name != null && !groupsByName.containsKey(name)) {
	            		GroupImpl group = new GroupImpl(this, conf);
	            		sm.groupsById.put(group.getId(), group);
	            		groupsByName.put(group.getName(), group);
	            	}
	            }
	        }

	        //load marked for remove groups information
	        if (collectionRemovedGroups != null && collectionRemovedGroups.getDocumentCount() > 0) {
	            for(Iterator<DocumentImpl> i = collectionRemovedGroups.iterator(broker); i.hasNext(); ) {
	            	Configuration conf = Configurator.parse(i.next());

	            	Integer id = conf.getPropertyInteger("id");
	            	if (id != null && !sm.groupsById.containsKey(id)) {
	            		GroupImpl group = new GroupImpl(this, conf);
	            		group.removed = true;
	            		sm.groupsById.put(group.getId(), group);
	            	}
	            }
	        }

	        //load accounts information
	        if (collectionAccounts != null && collectionAccounts.getDocumentCount() > 0) {
	            for(Iterator<DocumentImpl> i = collectionAccounts.iterator(broker); i.hasNext(); ) {
	            	Configuration conf = Configurator.parse(i.next());
	            	
	            	String name = conf.getProperty("name");
	            	if (name != null && !usersByName.containsKey(name)) {
	            		AccountImpl account = new AccountImpl( this, conf );
		            	sm.usersById.put(account.getId(), account);
		            	usersByName.put(account.getName(), account);
	            	}
	            }
	        }
	        
			//load marked for remove accounts information
	        if (collectionRemovedAccounts != null && collectionRemovedAccounts.getDocumentCount() > 0) {
	            for(Iterator<DocumentImpl> i = collectionRemovedAccounts.iterator(broker); i.hasNext(); ) {
	            	Configuration conf = Configurator.parse(i.next());
	            	
	            	Integer id = conf.getPropertyInteger("id");
	            	if (id != null && !sm.usersById.containsKey(id)) {
	            		AccountImpl account = new AccountImpl( this, conf );
	            		account.removed = true;
		            	sm.usersById.put(account.getId(), account);
	            	}
	            }
	        }


		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void save() throws PermissionDeniedException, EXistException, IOException {
		configuration.save();
	}

	//Accounts management methods
	protected final synchronized Account registerAccount(Account account)  {
		if (usersByName.containsKey(account.getName()))
			throw new IllegalArgumentException("User "+account.getName()+" exist.");
		
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
}
