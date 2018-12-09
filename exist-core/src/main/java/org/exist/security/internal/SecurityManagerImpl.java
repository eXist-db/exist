/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2016 The eXist Project
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

import com.evolvedbinary.j8fu.lazy.AtomicLazyVal;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.jcip.annotations.ThreadSafe;
import org.exist.scheduler.JobDescription;
import org.exist.security.AbstractRealm;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
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
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.ManagedLock;
import org.exist.storage.txn.Txn;
import org.exist.util.ConcurrentValueWrapper;
import org.exist.util.WeakLazyStripes;
import org.exist.xmldb.XmldbURI;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
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

    private static final Logger LOG = LogManager.getLogger(SecurityManager.class);

    public static final int MAX_USER_ID = 1048571;  //1 less than RealmImpl.UNKNOWN_ACCOUNT_ID
    public static final int MAX_GROUP_ID = 1048572; //1 less than RealmImpl.UNKNOWN_GROUP_ID
    static final int INITIAL_LAST_ACCOUNT_ID = 10;
    static final int INITIAL_LAST_GROUP_ID = 10;

    private final PrincipalDbById<Group> groupsById = new PrincipalDbById<>(INITIAL_LAST_GROUP_ID);
    private final PrincipalDbById<Account> usersById = new PrincipalDbById<>(INITIAL_LAST_ACCOUNT_ID);
    private final PrincipalLocks<Account> accountLocks = new PrincipalLocks<>();
    private final PrincipalLocks<Group> groupLocks = new PrincipalLocks<>();
    private final SessionDb sessions = new SessionDb();

    private Database db;

    private AtomicLazyVal<Subject> systemSubject;
    private AtomicLazyVal<Subject> guestSubject;

    private final Map<XmldbURI, Integer> saving = new ConcurrentHashMap<>();

    @ConfigurationFieldAsAttribute("version")
    @SuppressWarnings("unused")
    private String version = "2.1";

    @ConfigurationFieldAsElement("authentication-entry-point")
    private static final String authenticationEntryPoint = "/authentication/login";

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
            this.systemSubject = new AtomicLazyVal<>(() -> new SubjectAccreditedImpl(defaultRealm.ACCOUNT_SYSTEM, this));
            this.guestSubject =  new AtomicLazyVal<>(() -> new SubjectAccreditedImpl((AccountImpl) defaultRealm.getAccount(SecurityManager.GUEST_USER), this));
        } catch(final EXistException e) {
            throw new BrokerPoolServiceException(e);
        }
    }

    @Override
    public void startSystem(final DBBroker systemBroker, final Txn transaction) throws BrokerPoolServiceException {
        try {
            attach(systemBroker, transaction);
        } catch(final EXistException e) {
            throw new BrokerPoolServiceException(e);
        }
    }

    @Override
    public void startPreMultiUserSystem(final DBBroker systemBroker, final Txn transaction) {
        final Properties params = new Properties();
        params.put(getClass().getName(), this);
        db.getScheduler().createPeriodicJob(SessionsCheck.TIMEOUT_CHECK_PERIOD, new SessionsCheck(), SessionsCheck.TIMEOUT_CHECK_PERIOD, params, SimpleTrigger.REPEAT_INDEFINITELY, false);
    }

    /**
     * Initialize the security manager.
     * 
     * Checks if the file users.xml exists in the system collection of the database.
     * If not, it is created with two default users: admin and guest.
     *  
     * @param broker the database broker
     */
    @Override
    public void attach(final DBBroker broker, final Txn transaction) throws EXistException {
        db = broker.getDatabase(); //TODO: check that db is same?

        Collection systemCollection = null;
        try {
            systemCollection = broker.getCollection(XmldbURI.SYSTEM_COLLECTION_URI);
            if(systemCollection == null) {
                systemCollection = broker.getOrCreateCollection(transaction, XmldbURI.SYSTEM_COLLECTION_URI);
                if (systemCollection == null) {
                    return;
                }

                systemCollection.setPermissions(broker, Permission.DEFAULT_SYSTEM_COLLECTION_PERM);
                broker.saveCollection(transaction, systemCollection);
            }
        } catch (final Exception e) {
            LOG.error("Setting /db/system permissions failed: " + e.getMessage(), e);
        }

        try {
            collection = broker.getCollection(SECURITY_COLLECTION_URI);
            if (collection == null) {
                collection = broker.getOrCreateCollection(transaction, SECURITY_COLLECTION_URI);
                if (collection == null) {
                    LOG.error("Collection '/db/system/security' can't be created. Database may be corrupt!");
                    return;
                }

                collection.setPermissions(broker, Permission.DEFAULT_SYSTEM_SECURITY_COLLECTION_PERM);
                broker.saveCollection(transaction, collection);
            }
        } catch (final Exception e) {
            e.printStackTrace();
            LOG.error("Loading security configuration failed: " + e.getMessage(), e);
        }

        final Configuration _config_ = Configurator.parse(this, broker, collection, CONFIG_FILE_URI);
        configuration = Configurator.configure(this, _config_);

        for (final Realm realm : realms) {
            realm.start(broker, transaction);
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

        try (final ManagedLock<ReadWriteLock> lock = ManagedLock.acquire(accountLocks.getLock(account), LockMode.WRITE_LOCK)) {
            return findRealmForRealmId(account.getRealmId()).updateAccount(account);
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

        try (final ManagedLock<ReadWriteLock> lock = ManagedLock.acquire(groupLocks.getLock(group), LockMode.WRITE_LOCK)) {
            return findRealmForRealmId(group.getRealmId()).updateGroup(group);
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

        try (final ManagedLock<ReadWriteLock> lock = ManagedLock.acquire(groupLocks.getLock(group), LockMode.WRITE_LOCK)) {
            return findRealmForRealmId(group.getRealmId()).deleteGroup(group);
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

        try (final ManagedLock<ReadWriteLock> lock = ManagedLock.acquire(accountLocks.getLock(account), LockMode.WRITE_LOCK)) {
            return findRealmForRealmId(account.getRealmId()).deleteAccount(account);
        }
    }

    @Override
    public Account getAccount(final String name) {
        for(final Realm realm : realms) {
            final Account account = realm.getAccount(name);
            if (account != null) {
                return account;
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Account for '" + name + "' not found!");
        }
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
        try (final ManagedLock<ReadWriteLock> lock = ManagedLock.acquire(accountLocks.getLock(user), LockMode.READ_LOCK)) {
            return user.hasDbaRole();
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
            LOG.debug("Authentication try for '" + username + "'.");
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
                final Session session = db1.get(credentials.toString());
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

    @Override
    public Subject getSystemSubject() {
        return systemSubject.get();
    }

    @Override
    public Subject getGuestSubject() {
        return guestSubject.get();
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

    /**
     * For internal testing use only!
     *
     * @return The last group id
     */
    int getLastGroupId() {
        return groupsById.getCurrentPrincipalId();
    }

    /**
     * For internal testing use only!
     *
     * @return The last account id
     */
    int getLastAccountId() {
        return usersById.getCurrentPrincipalId();
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
            id = groupsById.getNextPrincipalId();
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

        try(final ManagedLock<ReadWriteLock> lock = ManagedLock.acquire(groupLocks.getLock(newGroup), LockMode.WRITE_LOCK)) {
            registerGroup(newGroup);
            registeredRealm.registerGroup(newGroup);

            newGroup.save(broker);

            return newGroup;
        }
    }

    @Override
    public final Account addAccount(final Account account) throws  PermissionDeniedException, EXistException{
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
            id = usersById.getNextPrincipalId();
        }

        final AbstractRealm registeredRealm = (AbstractRealm) findRealmForRealmId(account.getRealmId());
        if (registeredRealm.hasAccountLocal(account.getName())) {
            throw new ConfigurationException("The account '" + account.getName() + "' at realm '" + account.getRealmId() + "' already exists.");
        }

        final AccountImpl newAccount = new AccountImpl(broker, registeredRealm, id, account);
        try (final ManagedLock<ReadWriteLock> lock = ManagedLock.acquire(accountLocks.getLock(newAccount), LockMode.WRITE_LOCK)) {
            registerAccount(newAccount);
            registeredRealm.registerAccount(newAccount);

            newAccount.save(broker);

            return newAccount;
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
    public static class SessionsCheck implements JobDescription, org.quartz.Job {
        public static final long TIMEOUT_CHECK_PERIOD = 20000; //20 sec

        @Override
        public String getGroup() {
        	return "eXist.Security";
        }

        @Override
        public String getName() {
            return "Sessions.Check";
        }

        @Override
        public void setName(final String name) {
        }

        @Override
        public final void execute(final JobExecutionContext jec) {
            final JobDataMap jobDataMap = jec.getJobDetail().getJobDataMap();

            final Properties params = (Properties) jobDataMap.get("params");
            if (params == null) {
                return;
            }

            final SecurityManagerImpl sm = (SecurityManagerImpl)params.get(SecurityManagerImpl.class.getName());
            if (sm == null) {
                return;
            }
            
            sm.sessions.write(db -> db.entrySet().removeIf(entry -> entry == null || !entry.getValue().isValid()));
        }
    }

    @Override
    public void registerSession(final Session session) {
        sessions.write(db -> db.put(session.getId(), session));
    }

    @Override
    public Subject getSubjectBySessionId(final String sessionId) {
        return sessions.read(db -> {
            final Session session = db.get(sessionId);
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

    /**
     * Register mapping id to group.
     *
     * @param group thr group.
     */
    @Override
    public void registerGroup(final Group group) {
        groupsById.update((principalDb, principalId) -> {
            final int id = group.getId();

            principalDb.put(id, group);

            if (id < MAX_GROUP_ID) {
                return Math.max(principalId, id);
            } else {
                return principalId;
            }
        });
    }

    /**
     * Register mapping id to account.
     *
     * @param account the account.
     */
    @Override
    public void registerAccount(final Account account) {
        usersById.update((principalDb, principalId) -> {
            final int id = account.getId();

            principalDb.put(id, account);

            if (id < MAX_USER_ID) {
                return Math.max(principalId, id);
            } else {
                return principalId;
            }
        });
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
    
    @Override
    public void processParameterBeforeSave(final DBBroker broker, final DocumentImpl document) {
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
    public void processParameter(final DBBroker broker, final DocumentImpl document) throws ConfigurationException {

        XmldbURI uri = document.getCollection().getURI();

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
                    registerAccount(account);
                } else if(name != null) {
                	if (realm.hasAccount(name)) {
                		final Integer oldId = saving.get(document.getURI());
            			final Integer newId = conf.getPropertyInteger("id");
            			
            			//XXX: resolve conflicts on ids!!! 
            			
            			if (!newId.equals(oldId)) {
                    		final Account current = realm.getAccount(name);
                            try (final ManagedLock<ReadWriteLock> lock = ManagedLock.acquire(accountLocks.getLock(current), LockMode.WRITE_LOCK)) {
	            	            usersById.write(principalDb -> {
                                    principalDb.remove(oldId);
                                    principalDb.put(newId, current);
                                });
	            	        }
            			}
                	} else {
                		final Account account = new AccountImpl( realm, conf );
                		if (account.getGroups().length == 0) {
                		    try {
                                account.setPrimaryGroup(realm.getGroup(SecurityManager.UNKNOWN_GROUP));
                            } catch (final PermissionDeniedException e) {
                		        throw new ConfigurationException("Account has no group, unable to default to " + SecurityManager.UNKNOWN_GROUP + ": " + e.getMessage(), e);
                            }
                        }
                        registerAccount(account);
                		realm.registerAccount(account);
                	}
                } else {
                    //this can't be! log any way
                    LOG.error("Account '" + name + "' already exists in realm: '" + realmId + "', but received notification that a new one was created.");
                }
            
            } else if(isGroup) {
                if (isRemoved && id > 2 && !hasGroup(id)) {
                    final GroupImpl group = new GroupImpl( realm, conf );
                    group.removed = true;
                    registerGroup(group);
                } else if (name != null && !realm.hasGroup(name)) {
                    final GroupImpl group = new GroupImpl( realm, conf );
                    registerGroup(group);
                    realm.registerGroup(group);
                } else {
                    //this can't be! log any way
                    LOG.error("Group '" + name + "' already exists in realm: '" + realmId + "', but received notification that a new one was created.");
                }
                            
            }
            saving.remove(document.getURI());
        }
    }

    @Override
    public String getAuthenticationEntryPoint() {
        return authenticationEntryPoint;
    }

    @ThreadSafe
    private static class PrincipalLocks<T extends Principal> {
        private final WeakLazyStripes<Integer, ReadWriteLock> lockStripes = new WeakLazyStripes<>(id -> new ReentrantReadWriteLock());

        public ReadWriteLock getLock(final T principal) {
            return lockStripes.get(principal.getId());
        }
    }

    @ThreadSafe
    private static class SessionDb extends ConcurrentValueWrapper<Map<String, Session>> {
        public SessionDb() {
            super(new HashMap<>());
        }
    }

    @ThreadSafe
    private static class PrincipalDbById<V extends Principal> extends ConcurrentValueWrapper<Int2ObjectMap<V>> {
        private int principalId;

        public PrincipalDbById(final int initialLastId) {
            super(new Int2ObjectOpenHashMap<>(65));
            this.principalId = initialLastId;
        }

        public int getNextPrincipalId() {
            return writeAndReturn(principalDb -> {
                if(principalId + 1 >= MAX_GROUP_ID) {
                    throw new RuntimeException("System has no more ids available for principal type");
                }
                return ++principalId;
            });
        }

        private int getCurrentPrincipalId() {
            return read(principalDb -> principalId);
        }

        /**
         * Allows updates to the principal db,
         * and principal id.
         *
         * @param updateFn A function which updates the principal db and returns a new principal id.
         */
        public void update(final BiFunction<Int2ObjectMap<V>, Integer, Integer> updateFn) {
            write(principalDb -> {
                this.principalId = updateFn.apply(principalDb, principalId);
            });
        }
    }

    @Override
    public Subject getCurrentSubject() {
        return db.getActiveBroker().getCurrentSubject();
    }

    @Override
    public final void preAllocateAccountId(final PrincipalIdReceiver receiver) {
        receiver.allocate(usersById.getNextPrincipalId());
    }

    @Override
    public final void preAllocateGroupId(final PrincipalIdReceiver receiver) {
        receiver.allocate(groupsById.getNextPrincipalId());
    }
}
