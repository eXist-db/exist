/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.security.internal;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.exist.scheduler.JobDescription;
import org.exist.security.AbstractRealm;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Database;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.ConfigurationException;
import org.exist.config.annotation.*;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.AuthenticationException;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.Session;
import org.exist.security.Subject;
import org.exist.security.Account;
import org.exist.security.Permission;
import org.exist.security.Principal;
import org.exist.security.SchemaType;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.realm.Realm;
import org.exist.storage.BrokerPool;
import org.exist.storage.BrokerPoolService;
import org.exist.storage.BrokerPoolServiceException;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SimpleTrigger;

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
//<!-- Central user configuration. Editing this document will cause the security to reload and update its internal database. Please handle with care! -->
@ConfigurationClass("security-manager")
public class SecurityManagerImpl implements SecurityManager, BrokerPoolService {


    public final static int MAX_USER_ID = 1048571;  //1 less than RealmImpl.UNKNOWN_ACCOUNT_ID
    public final static int MAX_GROUP_ID = 1048572; //1 less than RealmImpl.UNKNOWN_GROUP_ID

    public final static Logger LOG = LogManager.getLogger(SecurityManager.class);

    private Database db;

    protected PrincipalDbById<Group> groupsById = new PrincipalDbById<>();
    protected PrincipalDbById<Account> usersById = new PrincipalDbById<>();

    private final PrincipalLocks<Account> accountLocks = new PrincipalLocks<>();
    private final PrincipalLocks<Group> groupLocks = new PrincipalLocks<>();

    //TODO: validate & remove if session timeout
    private SessionDb sessions = new SessionDb();

    @ConfigurationFieldAsAttribute("last-account-id")
    protected int lastUserId = 0;

    @ConfigurationFieldAsAttribute("last-group-id")
    protected int lastGroupId = 0;

    @ConfigurationFieldAsAttribute("version")
    private String version = "2.0";

    @ConfigurationFieldAsElement("authentication-entry-point")
    public final static String authenticationEntryPoint = "/authentication/login";
    
    private RealmImpl defaultRealm;
    
    @ConfigurationFieldAsElement("realm")
    @ConfigurationFieldClassMask("org.exist.security.realm.%1$s.%2$sRealm")
    private List<Realm> realms = new ArrayList<>();
    
    @ConfigurationFieldAsElement("events")
    //@ConfigurationFieldClassMask("org.exist.security.internal.SMEvents")
    private SMEvents events = null;
    
    private Collection collection = null;
    
    private Configuration configuration = null;
    
    public SecurityManagerImpl(final Database db) {
        this.db = db;
    }

    @Override
    public void prepare(final BrokerPool brokerPool) throws BrokerPoolServiceException {
        try {
            this.defaultRealm = new RealmImpl(null, this, null);
            realms.add(defaultRealm);
        } catch(final EXistException e) {
            throw new BrokerPoolServiceException(e);
        }
    }

    @Override
    public void startSystem(final DBBroker systemBroker) throws BrokerPoolServiceException {
        try {
            attach(systemBroker);
        } catch(final EXistException e) {
            throw new BrokerPoolServiceException(e);
        }
    }

    @Override
    public void startPreMultiUserSystem(final DBBroker systemBroker) throws BrokerPoolServiceException {
        final Properties params = new Properties();
        params.put(getClass().getName(), this);
        db.getScheduler().createPeriodicJob(TIMEOUT_CHECK_PERIOD, new SessionsCheck(), TIMEOUT_CHECK_PERIOD, params, SimpleTrigger.REPEAT_INDEFINITELY, false);
    }

    /**
     * Initialize the security manager.
     * 
     * Checks if the file users.xml exists in the system collection of the database.
     * If not, it is created with two default users: admin and guest.
     *  
     * @param broker
     */
    @Override
    public void attach(final DBBroker broker) throws EXistException {
        //groups = new Int2ObjectHashMap<Group>(65);
        //users = new Int2ObjectHashMap<User>(65);

        db = broker.getDatabase(); //TODO: check that db is same?

        final TransactionManager transaction = db.getTransactionManager();

        Collection systemCollection = null;
        try(final Txn txn = transaction.beginTransaction()) {
            systemCollection = broker.getCollection(XmldbURI.SYSTEM_COLLECTION_URI);
            if(systemCollection == null) {
                systemCollection = broker.getOrCreateCollection(txn, XmldbURI.SYSTEM_COLLECTION_URI);
                if (systemCollection == null) {
                    return;
                }

                systemCollection.setPermissions(Permission.DEFAULT_SYSTEM_COLLECTION_PERM);
                broker.saveCollection(txn, systemCollection);
            }
            transaction.commit(txn);
        } catch (final Exception e) {
            e.printStackTrace();
            LOG.debug("loading acl failed: " + e.getMessage());
        }

        try(final Txn txn = transaction.beginTransaction()) {
            collection = broker.getCollection(SECURITY_COLLECTION_URI);
            if (collection == null) {
                collection = broker.getOrCreateCollection(txn, SECURITY_COLLECTION_URI);
                if (collection == null) {
                    return;
                }

                //if db corrupted it can lead to unrunnable issue
                //throw new ConfigurationException("Collection '/db/system/security' can't be created.");

                collection.setPermissions(Permission.DEFAULT_SYSTEM_SECURITY_COLLECTION_PERM);
                broker.saveCollection(txn, collection);
            }
            transaction.commit(txn);
        } catch (final Exception e) {
            e.printStackTrace();
            LOG.debug("loading configuration failed: " + e.getMessage());
        }

        final Configuration _config_ = Configurator.parse(this, broker, collection, CONFIG_FILE_URI);
        configuration = Configurator.configure(this, _config_);


        for (final Realm realm : realms) {
            realm.start(broker);
        }
    }
    
    @Override
    public boolean updateAccount(final Account account) throws PermissionDeniedException, EXistException {
        if (account == null) {
            return false;
        }

        if (account.getRealmId() == null) {
            throw new ConfigurationException("Account must have realm id.");
        }

        final Lock lock = accountLocks.getWriteLock(account);
        lock.lock();
        try {
            return findRealmForRealmId(account.getRealmId()).updateAccount(account);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean updateGroup(final Group group) throws PermissionDeniedException, EXistException {
        if (group == null) {
            return false;
        }

        if (group.getRealmId() == null) {
            throw new ConfigurationException("Group must have realm id.");
        }

        final Lock lock = groupLocks.getWriteLock(group);
        lock.lock();
        try {
            return findRealmForRealmId(group.getRealmId()).updateGroup(group);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean deleteGroup(final String name) throws PermissionDeniedException, EXistException {
        final Group group = getGroup(name);
        if (group == null) {
            return false;
        }

        if (group.getRealmId() == null) {
            throw new ConfigurationException("Group must have realm id.");
        }

        final Lock lock = groupLocks.getWriteLock(group);
        lock.lock();
        try {
            return findRealmForRealmId(group.getRealmId()).deleteGroup(group);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean deleteAccount(final String name) throws PermissionDeniedException, EXistException {
        return deleteAccount(getAccount(name));
    }

    @Override
    public boolean deleteAccount(final Account account) throws PermissionDeniedException, EXistException {
        if (account == null) {
            return false;
        }

        if (account.getRealmId() == null) {
            throw new ConfigurationException("Account must have realm id.");
        }

        final Lock lock = accountLocks.getWriteLock(account);
        lock.lock();
        try {
            return findRealmForRealmId(account.getRealmId()).deleteAccount(account);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Account getAccount(final String name) {
//        if (SYSTEM.equals(name)) {
//            return defaultRealm.ACCOUNT_SYSTEM;
//        }

        for(final Realm realm : realms) {
            final Account account = realm.getAccount(name);
            if (account != null) {
                return account;
            }
        }
        
        LOG.debug("Account for '" + name + "' not found!");
        return null;
    }

    @Override
    public final Account getAccount(final int id) {
        return usersById.read(principalDb -> principalDb.get(id));
    }

    @Override
    public boolean hasGroup(final String name) {
        for (final Realm realm : realms) {
            if(realm.hasGroup(name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasGroup(final Group group) {
    	return hasGroup(group.getName());
    }

    @Override
    public Group getGroup(final String name) {
        for(final Realm realm : realms) {
            final Group group = realm.getGroup(name);
            if(group != null) {
                return group;
            }
        }
        return null;
    }

    @Override
    public final Group getGroup(final int id) {
        return groupsById.read(principalDb -> principalDb.get(id));
    }

    @Override
    public boolean hasAdminPrivileges(final Account user) {
        final Lock lock = accountLocks.getReadLock(user);
        lock.lock();
        try {
            return user.hasDbaRole();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean hasAccount(final String name) {
        for(final Realm realm : realms) {
            if(realm.hasAccount(name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Subject authenticate(final String username, final Object credentials) throws AuthenticationException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Authentication try for '"+username+"'.");
        }

        if (username == null) {
            throw new AuthenticationException(
                    AuthenticationException.ACCOUNT_NOT_FOUND,
                    "Account NULL not found"
            );
        }

        if("jsessionid".equals(username)) {

            if (getSystemSubject().getSessionId().equals(credentials)) {
                return getSystemSubject();
            }

            if (getGuestSubject().getSessionId().equals(credentials)) {
                return getGuestSubject();
            }

            final Subject subject = sessions.read(db1 -> {
                final Session session = db1.get(credentials);
                if (session == null) {
                    return null;
                }

                if (session.isValid()) {
                    return session.getSubject();
                }

                return null;
            });

            if(subject == null) {
                throw new AuthenticationException(
                        AuthenticationException.SESSION_NOT_FOUND,
                        "Session [" + credentials + "] not found"
                );
            }

            if (events != null) {
                events.authenticated(subject);
            }

            //TODO: validate session
            return subject;
        }

        for(final Realm realm : realms) {
            try {
                final Subject subject = realm.authenticate(username, credentials);

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Authenticated by '" + realm.getId() + "' as '" + subject + "'.");
                }

                if (events != null) {
                    events.authenticated(subject);
                }

                return subject;
            } catch(final AuthenticationException e) {
                if(e.getType() != AuthenticationException.ACCOUNT_NOT_FOUND) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Realm '" + realm.getId() + "' threw exception for account '" + username + "'. [" + e.getMessage() + "]");
                    }
                    throw e;
                }
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Account '"+username+"' not found, throw error");
        }

        throw new AuthenticationException(
            AuthenticationException.ACCOUNT_NOT_FOUND,
            "Account [" + username + "] not found"
        );
    }
    
    protected Subject systemSubject = null;
    protected Subject guestSubject = null;

    @Override
    public Subject getSystemSubject() {
        if (systemSubject == null) {
            synchronized (this) {
                if (systemSubject == null) {
                    systemSubject = new SubjectAccreditedImpl(defaultRealm.ACCOUNT_SYSTEM, this);
                }
            }
        }
        return systemSubject; 
    }

    @Override
    public Subject getGuestSubject() {
        if (guestSubject == null) {
            synchronized (this) {
                if (guestSubject == null) {
                    guestSubject = new SubjectAccreditedImpl((AccountImpl) defaultRealm.getAccount(SecurityManager.GUEST_USER), this);
                }
            }
        }
        return guestSubject;
    }

    @Override
    public Group getDBAGroup() {
        return defaultRealm.GROUP_DBA;
    }

    @Override
    public Database getDatabase() {
        return db;
    }

    @Override
    public Database database() {
        return db;
    }

    private synchronized int getNextGroupId() {
        if(lastGroupId + 1 == MAX_GROUP_ID) {
            throw new RuntimeException("System has no more group-ids available");            
        }
        return ++lastGroupId;
    }

    private synchronized int getNextAccountId() {
        if(lastUserId +1 == MAX_USER_ID) {
            throw new RuntimeException("System has no more user-ids available");
        }
        return ++lastUserId;
    }

    @Override
    public List<Account> getGroupMembers(final String groupName) {
        final List<Account> groupMembers = new ArrayList<>();

        for(final Realm realm : realms) {
            groupMembers.addAll(
                realm.getAccounts().stream()
                    .filter(account -> account.hasGroup(groupName))
                    .collect(Collectors.toList())
            );
        }
        return groupMembers;
    }

    @Override
    public List<String> findAllGroupMembers(final String groupName) {
        final List<String> userNames = new ArrayList<>();
        for(final Realm realm : realms) {
            userNames.addAll(realm.findAllGroupMembers(groupName));
        }
        return userNames;
    }

    @Deprecated //use realm's getAccounts
    @Override
    public java.util.Collection<Account> getUsers() {
        return defaultRealm.getAccounts();
    }

    @Deprecated //use realm's getGroups 
    @Override
    public java.util.Collection<Group> getGroups() {
        return defaultRealm.getGroups();
    }
    
    @Override
    public void addGroup(final DBBroker broker, final String name) throws PermissionDeniedException, EXistException {
        addGroup(broker, new GroupAider(name));
    }

    @Override
    public Group addGroup(final DBBroker broker, final Group group) throws PermissionDeniedException, EXistException {
        if(group.getRealmId() == null) {
            throw new ConfigurationException("Group must have realm id.");
        }

        if(group.getName() == null || group.getName().isEmpty()) {
            throw new ConfigurationException("Group must have name.");
        }

        final int id;
        if(group.getId() != Group.UNDEFINED_ID) {
            id = group.getId();
        } else {
            id = getNextGroupId();
        }
        
        final AbstractRealm registeredRealm = (AbstractRealm)findRealmForRealmId(group.getRealmId());
        if (registeredRealm.hasGroupLocal(group.getName())) {
            throw new ConfigurationException("The group '" + group.getName() + "' at realm '" + group.getRealmId() + "' already exists.");
        }
        
        final GroupImpl newGroup = new GroupImpl(broker, registeredRealm, id, group.getName(), group.getManagers());
        for(final SchemaType metadataKey : group.getMetadataKeys()) {
            final String metadataValue = group.getMetadataValue(metadataKey);
            newGroup.setMetadataValue(metadataKey, metadataValue);
        }

        final Lock lock = groupLocks.getWriteLock(newGroup);
        lock.lock();
        try {
            groupsById.modify(principalDb -> principalDb.put(id, newGroup));
            
            registeredRealm.registerGroup(newGroup);

            save(broker);
            newGroup.save(broker);

            return newGroup;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public final Account addAccount(final Account account) throws  PermissionDeniedException, EXistException {
        try(final DBBroker broker = db.getBroker()) {
            return addAccount(broker, account);
        }
    }
    
    @Override 
    public final Account addAccount(final DBBroker broker, final Account account) throws  PermissionDeniedException, EXistException{
        if(account.getRealmId() == null) {
            throw new ConfigurationException("Account must have realm id.");
        }

        if(account.getName() == null || account.getName().isEmpty()) {
            throw new ConfigurationException("Account must have name.");
        }

        final int id;
        if(account.getId() != Account.UNDEFINED_ID) {
            id = account.getId();
        } else {
            id = getNextAccountId();
        }

        final AbstractRealm registeredRealm = (AbstractRealm) findRealmForRealmId(account.getRealmId());
        if (registeredRealm.hasAccountLocal(account.getName())) {
            throw new ConfigurationException("The account '" + account.getName() + "' at realm '" + account.getRealmId() + "' already exists.");
        }

        final AccountImpl newAccount = new AccountImpl(broker, registeredRealm, id, account);
        final Lock lock = accountLocks.getWriteLock(newAccount);
        lock.lock();
        try {
            usersById.modify(principalDb -> principalDb.put(id, newAccount));

            registeredRealm.registerAccount(newAccount);

            save(broker);
            newAccount.save(broker);

            return newAccount;
        } finally {
            lock.unlock();
        }
    }

    private void save() throws PermissionDeniedException, EXistException {
        if (configuration != null) {
            configuration.save();
        }
    }
        
    private void save(final DBBroker broker) throws PermissionDeniedException, EXistException {
        if (configuration != null) {
            configuration.save(broker);
        }
    }

    @Override
    public boolean isConfigured() {
            return configuration != null;
    }

    @Override
    public Configuration getConfiguration() {
		return configuration;
    }
    
    //Session management part
    
    public final static long TIMEOUT_CHECK_PERIOD = 20000; //20 sec

    public static class SessionsCheck implements JobDescription, org.quartz.Job {

        boolean firstRun = true;

        public SessionsCheck() {}

        public String getGroup() {
        	return "eXist.Security";
        }

        @Override
        public String getName() {
            return "Sessions.Check";
        }

        @Override
        public void setName(String name) {
        }

        @Override
        public final void execute(final JobExecutionContext jec) throws JobExecutionException {
            final JobDataMap jobDataMap = jec.getJobDetail().getJobDataMap();

            final Properties params = (Properties) jobDataMap.get("params");
            if (params == null) {
                return;
            }

            final SecurityManagerImpl sm = (SecurityManagerImpl)params.get(SecurityManagerImpl.class.getName());
            if (sm == null) {
                return;
            }
            
            sm.sessions.modify(db -> {
                final Iterator<Map.Entry<String, Session>> it = db.entrySet().iterator();
                while (it.hasNext()) {
                    final Map.Entry<String, Session> entry = it.next();
                    if (entry == null || !entry.getValue().isValid()) {
                        it.remove();
                    }
                }
            });
        }
    }

    @Override
    public void registerSession(final Session session) {
        sessions.modify(db -> db.put(session.getId(), session));
    }

    @Override
    public Subject getSubjectBySessionId(String sessionId) {
        return sessions.read(db -> {
            Session session = db.get(sessionId);
            if (session != null) {
                return session.getSubject();
            }

            return null;
        });
    }
        
    private Realm findRealmForRealmId(final String realmId) throws ConfigurationException {
        for(final Realm realm : realms) {
            if(realm.getId().equals(realmId)) {
                return realm;
            }
        }
        throw new ConfigurationException("Realm id = '" + realmId + "' not found.");
    }
    
    @Override
    public void addGroup(final int id, final Group group) {
        groupsById.modify(principalDb -> principalDb.put(id, group));
    }

    @Override
    public void addUser(final int id, final Account account) {
        usersById.modify(principalDb -> principalDb.put(id, account));
    }

    @Override
    public boolean hasGroup(final int id) {
        return groupsById.read(principalDb -> principalDb.containsKey(id));
    }

    @Override
    public boolean hasUser(final int id) {
        return usersById.read(principalDb -> principalDb.containsKey(id));
    }

    @Override
    public List<String> findUsernamesWhereNameStarts(final String startsWith) {
        final List<String> userNames = new ArrayList<>();
        for(final Realm realm : realms) {
            userNames.addAll(realm.findUsernamesWhereNameStarts(startsWith));
        }
        return userNames;
    }

    @Override
    public List<String> findUsernamesWhereUsernameStarts(final String startsWith) {
        final List<String> userNames = new ArrayList<>();
        for(final Realm realm : realms) {
            userNames.addAll(realm.findUsernamesWhereUsernameStarts(startsWith));
        }
        return userNames;
    }
    
    @Override
    public List<String> findUsernamesWhereNamePartStarts(final String startsWith) {
        final List<String> userNames = new ArrayList<>();
        for(final Realm realm : realms) {
            userNames.addAll(realm.findUsernamesWhereNamePartStarts(startsWith));
        }
        return userNames;
    }

    @Override
    public List<String> findGroupnamesWhereGroupnameContains(final String fragment) {
        final List<String> groupNames = new ArrayList<>();
        for(final Realm realm : realms) {
            groupNames.addAll(realm.findGroupnamesWhereGroupnameContains(fragment));
        }
        return groupNames;
    }
    
    @Override
    public List<String> findGroupnamesWhereGroupnameStarts(final String startsWith) {
        final List<String> groupNames = new ArrayList<>();
        for(final Realm realm : realms) {
            groupNames.addAll(realm.findGroupnamesWhereGroupnameStarts(startsWith));
        }
        return groupNames;
    }

    @Override
    public List<String> findAllGroupNames() {
        final List<String> groupNames = new ArrayList<>();
        for(final Realm realm : realms) {
            groupNames.addAll(realm.findAllGroupNames());
        }
        return groupNames;
    }
    
    @Override
    public List<String> findAllUserNames() {
        final List<String> userNames = new ArrayList<>();
        for(final Realm realm : realms) {
            userNames.addAll(realm.findAllUserNames());
        }
        return userNames;
    }
    
    private Map<XmldbURI, Integer> saving = new HashMap<>();
    
    @Override
    public void processPramatterBeforeSave(final DBBroker broker, final DocumentImpl document) throws ConfigurationException {
        XmldbURI uri = document.getCollection().getURI();
        
        final boolean isRemoved = uri.endsWith(SecurityManager.REMOVED_COLLECTION_URI);
        if(isRemoved) {
            uri = uri.removeLastSegment();
        }

        final boolean isAccount = uri.endsWith(SecurityManager.ACCOUNTS_COLLECTION_URI);
        final boolean isGroup = uri.endsWith(SecurityManager.GROUPS_COLLECTION_URI);

        if(isAccount || isGroup) {
            //uri = uri.removeLastSegment();
            //String realmId = uri.lastSegment().toString();
            //AbstractRealm realm = (AbstractRealm)findRealmForRealmId(realmId);
            final Configuration conf = Configurator.parse(broker.getBrokerPool(), document);

            saving.put(document.getURI(), conf.getPropertyInteger("id"));
        }
    }

    @Override
    public void processPramatter(DBBroker broker, DocumentImpl document) throws ConfigurationException {

        XmldbURI uri = document.getCollection().getURI();
        
        //System.out.println(document);

        final boolean isRemoved = uri.endsWith(SecurityManager.REMOVED_COLLECTION_URI);
        if(isRemoved) {
            uri = uri.removeLastSegment();
        }
		
        final boolean isAccount = uri.endsWith(SecurityManager.ACCOUNTS_COLLECTION_URI);
        final boolean isGroup = uri.endsWith(SecurityManager.GROUPS_COLLECTION_URI);
		
        if(isAccount || isGroup) {
            uri = uri.removeLastSegment();

            final String realmId = uri.lastSegment().toString();
			
            final AbstractRealm realm = (AbstractRealm)findRealmForRealmId(realmId);
            final Configuration conf = Configurator.parse(broker.getBrokerPool(), document);

            Integer id = -1;
            if(isRemoved) {
                id = conf.getPropertyInteger("id");
            }

            final String name = conf.getProperty("name");

            if(isAccount) {
                if (isRemoved && id > 2 && !hasUser(id)) {
                    final AccountImpl account = new AccountImpl( realm, conf );
                    account.removed = true;
                    addUser(account.getId(), account);
                } else if(name != null) {
                	if (realm.hasAccount(name)) {
                		final Integer oldId = saving.get(document.getURI());
                		
            			final Integer newId = conf.getPropertyInteger("id");
            			
            			//XXX: resolve conflicts on ids!!! 
            			
            			if (!newId.equals(oldId)) {
                    		final Account current = realm.getAccount(name);
	            	        accountLocks.getWriteLock(current).lock();
	            	        try {
	            	            usersById.modify(principalDb -> {
                                    principalDb.remove(oldId);
                                    principalDb.put(newId, current);
                                });
	            	        } finally {
	            	            accountLocks.getWriteLock(current).unlock();
	            	        }
            			}
                	} else {
                		final Account account = new AccountImpl( realm, conf );
                		addUser(account.getId(), account);
                		realm.registerAccount(account);
                	}
                } else {
                    //this can't be! log any way
                    LOG.error("Account '"+name+"' pressent at '"+realmId+"' realm, but get event that new one created.");
                }
            
            } else if(isGroup) {
                if (isRemoved && id > 2 && !hasGroup(id)) {
                    final GroupImpl group = new GroupImpl( realm, conf );
                    group.removed = true;
                    addGroup(group.getId(), group);
                } else if (name != null && !realm.hasGroup(name)) {
                    final GroupImpl group = new GroupImpl( realm, conf );
                    addGroup(group.getId(), group);
                    realm.registerGroup(group);
                } else {
                    //this can't be! log any way
                    LOG.error("Group '"+name+"' pressent at '"+realmId+"' realm, but get event that new one created.");
                }
                            
            }
            saving.remove(document.getURI());
        }
    }

    @Override
    public String getAuthenticationEntryPoint() {
            return authenticationEntryPoint;
    }

    private static class PrincipalLocks<T extends Principal> {
        private final Map<Integer, ReentrantReadWriteLock> locks = new HashMap<>();

        private synchronized ReentrantReadWriteLock getLock(final T principal) {
            ReentrantReadWriteLock lock = locks.get(principal.getId());
            if(lock == null) {
                lock = new ReentrantReadWriteLock();
                locks.put(principal.getId(), lock);
            }
            return lock;
        }

        public ReadLock getReadLock(T principal) {
            return getLock(principal).readLock();
        }

        public WriteLock getWriteLock(T principal) {
            return getLock(principal).writeLock();
        }
    }
   
    protected static class SessionDb {
        private final Map<String, Session> db = new HashMap<>();
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final ReadLock readLock = lock.readLock();
        private final WriteLock writeLock = lock.writeLock();

        public <R> R read(final Function<Map<String, Session>, R> readFn) {
            readLock.lock();
            try {
                return readFn.apply(db);
            } finally {
                readLock.unlock();
            }
        }

        public final void modify(final Consumer<Map<String, Session>> modifyFn) {
            writeLock.lock();
            try {
                modifyFn.accept(db);
            } finally {
                writeLock.unlock();
            }
        }
    }
   
    protected static class PrincipalDbById<V extends Principal> {
        private final Int2ObjectMap<V> db = new Int2ObjectOpenHashMap<>(65);
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final ReadLock readLock = lock.readLock();
        private final WriteLock writeLock = lock.writeLock();

        public <R> R read(final Function<Int2ObjectMap<V>, R> readFn) {
            readLock.lock();
            try {
                return readFn.apply(db);
            } finally {
                readLock.unlock();
            }
        }

        public final void modify(final Consumer<Int2ObjectMap<V>> writeOp) {
            writeLock.lock();
            try {
                writeOp.accept(db);
            } finally {
                writeLock.unlock();
            }
        }
    }

    @Override
    public Subject getCurrentSubject() {
        return db.getActiveBroker().getCurrentSubject();
    }

    @Override
    public final synchronized void preAllocateAccountId(final PrincipalIdReceiver receiver) throws PermissionDeniedException, EXistException {
        final int id = getNextAccountId();
        save();
        receiver.allocate(id);
    }

    @Override
    public final synchronized void preAllocateGroupId(final PrincipalIdReceiver receiver) throws PermissionDeniedException, EXistException {
        final int id = getNextGroupId();
        save();
        receiver.allocate(id);
    }
}
