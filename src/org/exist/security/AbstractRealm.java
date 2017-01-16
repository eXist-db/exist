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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import org.exist.Database;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.Configurator;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.internal.AccountImpl;
import org.exist.security.internal.GroupImpl;
import org.exist.security.internal.SecurityManagerImpl;
import org.exist.security.realm.Realm;
import org.exist.security.utils.Utils;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import com.evolvedbinary.j8fu.function.Consumer2E;
import com.evolvedbinary.j8fu.function.ConsumerE;
import org.exist.xmldb.XmldbURI;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public abstract class AbstractRealm implements Realm, Configurable {

    protected final PrincipalDbByName<Account> usersByName = new PrincipalDbByName<>();
    protected final PrincipalDbByName<Group> groupsByName = new PrincipalDbByName<>();
    

    private SecurityManager sm;

    protected Configuration configuration;

    protected Collection collectionRealm = null;
    protected Collection collectionAccounts = null;
    protected Collection collectionGroups = null;
    protected Collection collectionRemovedAccounts = null;
    protected Collection collectionRemovedGroups = null;
	
    public AbstractRealm(SecurityManager sm, Configuration config) {
        this.sm = sm;
        this.configuration = Configurator.configure(this, config);
    }

    @Override
    public Database getDatabase() {
        return getSecurityManager().getDatabase();
    }

    @Override
    public SecurityManager getSecurityManager() {
        return sm;
    }
    
    protected void initialiseRealmStorage(final DBBroker broker) throws EXistException {
        
        final XmldbURI realmCollectionURL = SecurityManager.SECURITY_COLLECTION_URI.append(getId());
        
        final TransactionManager transact = broker.getBrokerPool().getTransactionManager();
        try(final Txn txn = transact.beginTransaction()) {
            collectionRealm = Utils.getOrCreateCollection(broker, txn, realmCollectionURL);

            collectionAccounts = Utils.getOrCreateCollection(broker, txn, realmCollectionURL.append("accounts"));
            collectionGroups = Utils.getOrCreateCollection(broker, txn, realmCollectionURL.append("groups"));

            collectionRemovedAccounts = Utils.getOrCreateCollection(broker, txn, realmCollectionURL.append("accounts").append("removed"));
            collectionRemovedGroups = Utils.getOrCreateCollection(broker, txn, realmCollectionURL.append("groups").append("removed"));

            transact.commit(txn);
            
        } catch(final PermissionDeniedException | IOException | TriggerException | LockException e) {
            throw new EXistException(e);
        }
    }
    
    private void loadGroupsFromRealmStorage(final DBBroker broker) throws ConfigurationException, PermissionDeniedException, LockException {
        if(collectionGroups != null && collectionGroups.getDocumentCount(broker) > 0) {
            
            final AbstractRealm r = this;
            
            for(final Iterator<DocumentImpl> i = collectionGroups.iterator(broker); i.hasNext(); ) {
                final Configuration conf = Configurator.parse(broker.getBrokerPool(), i.next());
                final String name = conf.getProperty("name");
                
                groupsByName.<ConfigurationException>modifyE(principalDb -> {
                    if(name != null && !principalDb.containsKey(name)) {

                        //Group group = instantiateGroup(this, conf);
                        final GroupImpl group = new GroupImpl(r, conf);

                        getSecurityManager().addGroup(group.getId(), group);
                        principalDb.put(group.getName(), group);

                        //set collection
                        if(group.getId() > 0) {
                            ((AbstractPrincipal)group).setCollection(broker, collectionGroups);
                        }
                    }
                });
            }
        }
    }
    
    private void loadRemovedGroupsFromRealmStorage(final DBBroker broker) throws ConfigurationException, PermissionDeniedException, LockException {
        //load marked for remove groups information
        if (collectionRemovedGroups != null && collectionRemovedGroups.getDocumentCount(broker) > 0) {
            for(final Iterator<DocumentImpl> i = collectionRemovedGroups.iterator(broker); i.hasNext(); ) {
                final Configuration conf = Configurator.parse(broker.getBrokerPool(), i.next());
                final Integer id = conf.getPropertyInteger("id");
                
                if (id != null && !getSecurityManager().hasGroup(id)) {
                    
                    //G group = instantiateGroup(this, conf, true);
                    final GroupImpl group = new GroupImpl(this, conf);
                    group.removed = true;
                    
                    getSecurityManager().addGroup(group.getId(), group);
                }
            }
        }
    }
    
    private void loadAccountsFromRealmStorage(final DBBroker broker) throws ConfigurationException, PermissionDeniedException, LockException {
        //load accounts information
        if (collectionAccounts != null && collectionAccounts.getDocumentCount(broker) > 0) {
            
            final AbstractRealm r = this;
            
            for(final Iterator<DocumentImpl> i = collectionAccounts.iterator(broker); i.hasNext(); ) {
                final DocumentImpl doc = i.next();
                final Configuration conf = Configurator.parse(broker.getBrokerPool(), doc);
                final String name = conf.getProperty("name");
                
                usersByName.<ConfigurationException>modifyE(principalDb -> {
                    if(name != null && !principalDb.containsKey(name)) {
                        //A account = instantiateAccount(this, conf);
                        final Account account;
                        try {
                            account = new AccountImpl(r, conf);
                        } catch (Throwable e) {
                            SecurityManagerImpl.LOG.error("Account object can't build up from '"+doc.getFileURI()+"'", e);
                            return;
                        }

                        getSecurityManager().addUser(account.getId(), account);
                        principalDb.put(account.getName(), account);

                        //set collection
                        if(account.getId() > 0) {
                            ((AbstractPrincipal)account).setCollection(broker, collectionAccounts);
                        }
                    }
                });
            }
        }
    }
    
    private void loadRemovedAccountsFromRealmStorage(final DBBroker broker) throws ConfigurationException, PermissionDeniedException, LockException {
        //load marked for remove accounts information
        if (collectionRemovedAccounts != null && collectionRemovedAccounts.getDocumentCount(broker) > 0) {
            for(final Iterator<DocumentImpl> i = collectionRemovedAccounts.iterator(broker); i.hasNext(); ) {
                final Configuration conf = Configurator.parse(broker.getBrokerPool(), i.next());
	            	
                final Integer id = conf.getPropertyInteger("id");
                if (id != null && !getSecurityManager().hasUser(id)) {
                    
                    //A account = instantiateAccount(this, conf, true);
	            final AccountImpl account = new AccountImpl( this, conf );
	            account.removed = true;
		    
                    getSecurityManager().addUser(account.getId(), account);
                }
            }
        }
    }
    
    
    @Override
    public void start(final DBBroker broker) throws EXistException {

        initialiseRealmStorage(broker);
        
        try {
            loadGroupsFromRealmStorage(broker);
            loadRemovedGroupsFromRealmStorage(broker);
           
            loadAccountsFromRealmStorage(broker);
            loadRemovedAccountsFromRealmStorage(broker);
        } catch(final PermissionDeniedException | LockException pde) {
            throw new EXistException(pde);
        }
    }
    

	@Override
	public void sync(DBBroker broker) throws EXistException {
	}

	@Override
	public void stop(DBBroker broker) throws EXistException {
	}

	public void save() throws PermissionDeniedException, EXistException, IOException {
        configuration.save();
    }

    //Accounts management methods
    public final Account registerAccount(final Account account) {
        usersByName.modify(principalDb -> {
            if(principalDb.containsKey(account.getName())) {
                throw new IllegalArgumentException("Account " + account.getName() + " exist.");
            }

            principalDb.put(account.getName(), account);
        });

        return account;
    }
    
    public final Group registerGroup(final Group group) {
        groupsByName.modify(principalDb -> {
            if(principalDb.containsKey(group.getName())) {
                throw new IllegalArgumentException("Group " + group.getName() + " already exists.");
            }

            principalDb.put(group.getName(), group);
        });
        
        return group;   
    }

    @Override
    public Account getAccount(final String name) {
        return usersByName.read(principalDb -> principalDb.get(name));
    }

    @Override
    public final boolean hasAccount(final String accountName) {
        return usersByName.read(principalDb -> principalDb.containsKey(accountName));
    }

    @Override
    public final boolean hasAccount(Account account) {
        return hasAccount(account.getName());
    }

    @Override
    public final java.util.Collection<Account> getAccounts() {
        return usersByName.read(principalDb -> principalDb.values());
    }

    //Groups management methods
    @Override
    public final boolean hasGroup(final String groupName) {
        return groupsByName.read(principalDb -> principalDb.containsKey(groupName));
    }

    @Override
    public final boolean hasGroup(Group role) {
        return hasGroup(role.getName());
    }

    @Override
    public Group getGroup(final String name) {
        return groupsByName.read(principalDb -> principalDb.get(name));
    }

    @Override
    public final java.util.Collection<Group> getRoles() {
        return getGroups();
    }

    @Override
    public final java.util.Collection<Group> getGroups() {
        return groupsByName.read(principalDb -> principalDb.values());
    }

    //collections related methods
    protected Collection getCollection() {
        return collectionRealm;
    }

    @Override
    public Group addGroup(final DBBroker broker, final Group group) throws PermissionDeniedException, EXistException {
        
        if(group.getRealmId() == null) {
            throw new ConfigurationException("Group's realmId is null.");
        }

        if(!getId().equals(group.getRealmId())) {
            throw new ConfigurationException("Group from different realm");
        }

        return getSecurityManager().addGroup(broker, group);
    }

    @Override
    public Account addAccount(Account account) throws PermissionDeniedException, EXistException, ConfigurationException {
        if(account.getRealmId() == null) {
            throw new ConfigurationException("Account's realmId is null.");
        }

        if(!getId().equals(account.getRealmId())) {
            throw new ConfigurationException("Account from different realm");
        }

        return getSecurityManager().addAccount(account);
    }

    @Override
    public boolean updateAccount(final Account account) throws PermissionDeniedException, EXistException {
        
        //make sure we have permission to modify this account
        final Account user = getDatabase().getActiveBroker().getCurrentSubject();
        account.assertCanModifyAccount(user);
        
        //modify the account
        final Account updatingAccount = getAccount(account.getName());
        if(updatingAccount == null) {
            throw new PermissionDeniedException("account " + account.getName() + " does not exist");
        }

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

        final String passwd = account.getPassword();
        if (passwd != null) {
            // if password is empty, ignore it to keep the old one
            // assumes that empty passwords should never be allowed
            updatingAccount.setPassword(account.getPassword());
        }
        updatingAccount.setUserMask(account.getUserMask());
        
        //update the metadata
        if(account.hashCode() != updatingAccount.hashCode()) {
            updatingAccount.clearMetadata();
            for(final SchemaType key : account.getMetadataKeys()) {
                updatingAccount.setMetadataValue(key, account.getMetadataValue(key));
            }
        }
        

        ((AbstractPrincipal)updatingAccount).save();

        return true;
    }

    @Override
    public boolean updateGroup(final Group group) throws PermissionDeniedException, EXistException {

        //make sure we have permission to modify this account
        final Account user = getDatabase().getActiveBroker().getCurrentSubject();
        group.assertCanModifyGroup(user);

        //modify the group
        final Group updatingGroup = getGroup(group.getName());
        if(updatingGroup == null) {
            throw new PermissionDeniedException("group " + group.getName() + " does not exist");
        }

        //check: add account to group managers
        for(final Account manager : group.getManagers()) {
            if(!updatingGroup.isManager(manager)) {
                updatingGroup.addManager(manager);
            }
        }

        //check: remove account from group managers
        for(final Account manager : updatingGroup.getManagers()){
            if(!group.isManager(manager)) {
                updatingGroup.removeManager(manager);
            }
        }

        //update the metadata
        if(group.hashCode() != updatingGroup.hashCode()) {
            updatingGroup.clearMetadata();
            for(final SchemaType key : group.getMetadataKeys()) {
                updatingGroup.setMetadataValue(key, group.getMetadataValue(key));
            }
        }    

        updatingGroup.save();

        return true;
    }

    @Override
    public Group getExternalGroup(final String name) {
        return getSecurityManager().getGroup(name);
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

    @Override
    public List<String> findUsernamesWhereNameStarts(final String startsWith) {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<String> findUsernamesWhereUsernameStarts(final String startsWith) {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<String> findAllGroupNames() {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<String> findAllUserNames() {
        return Collections.EMPTY_LIST;
    }
    
    @Override
    public List<String> findAllGroupMembers(final String groupName) {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<String> findUsernamesWhereNamePartStarts(final String startsWith) {
        return Collections.EMPTY_LIST;
    }

    @Override
    public java.util.Collection<? extends String> findGroupnamesWhereGroupnameStarts(final String startsWith) {
        return Collections.EMPTY_LIST;
    }

    @Override
    public java.util.Collection<? extends String> findGroupnamesWhereGroupnameContains(final String fragment) {
        return Collections.EMPTY_LIST;
    }
    
    protected static class PrincipalDbByName<V extends Principal> {
        private final Map<String, V> db = new HashMap<>(65);
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final ReadLock readLock = lock.readLock();
        private final WriteLock writeLock = lock.writeLock();

        public <R> R read(final Function<Map<String, V>, R> readOp) {
            readLock.lock();
            try {
                return readOp.apply(db);
            } finally {
                readLock.unlock();
            }
        }

        public final void modify(final Consumer<Map<String, V>> writeOp) {
            writeLock.lock();
            try {
                writeOp.accept(db);
            } finally {
                writeLock.unlock();
            }
        }

        public final <E extends Throwable> void modifyE(final ConsumerE<Map<String, V>, E> writeOp) throws E {
            writeLock.lock();
            try {
                writeOp.accept(db);
            } finally {
                writeLock.unlock();
            }
        }

        public final <E1 extends Exception, E2 extends Exception> void modify2E(final Consumer2E<Map<String, V>, E1, E2> writeOp) throws E1, E2 {
            writeLock.lock();
            try {
                writeOp.accept(db);
            } finally {
                writeLock.unlock();
            }
        }
    }
}