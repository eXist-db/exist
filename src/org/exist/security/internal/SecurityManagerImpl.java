/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2011 The eXist Project
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

import org.exist.scheduler.JobDescription;
import org.exist.security.AbstractRealm;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import org.apache.log4j.Logger;
import org.exist.Database;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.ConfigurationException;
import org.exist.config.annotation.*;
import org.exist.dom.DocumentImpl;
import org.exist.security.AuthenticationException;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.PermissionFactory;
import org.exist.security.SecurityManager;
import org.exist.security.Session;
import org.exist.security.Subject;
import org.exist.security.Account;
import org.exist.security.Permission;
import org.exist.security.Principal;
import org.exist.security.SchemaType;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.realm.Realm;
import org.exist.security.xacml.ExistPDP;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.hashtable.Int2ObjectHashMap;
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
public class SecurityManagerImpl implements SecurityManager {


    public final static int MAX_USER_ID = 1048571;  //1 less than RealmImpl.UNKNOWN_ACCOUNT_ID
    public final static int MAX_GROUP_ID = 1048572; //1 less than RealmImpl.UNKNOWN_GROUP_ID

    public static final String PROPERTY_PERMISSIONS_COLLECTIONS = "indexer.permissions.collection";
    public static final String PROPERTY_PERMISSIONS_RESOURCES = "indexer.permissions.resource";	

    private final static Logger LOG = Logger.getLogger(SecurityManager.class);

    private Database pool;

    protected PrincipalDbById<Group> groupsById = new PrincipalDbById<Group>();
    protected PrincipalDbById<Account> usersById = new PrincipalDbById<Account>();

    private final PrincipalLocks<Account> accountLocks = new PrincipalLocks<Account>();
    private final PrincipalLocks<Group> groupLocks = new PrincipalLocks<Group>();

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
    
    //@ConfigurationField("enableXACML")
    private Boolean enableXACML = false;

    private ExistPDP pdp;
    
    private RealmImpl defaultRealm;
    
    @ConfigurationFieldAsElement("realm")
    @ConfigurationFieldClassMask("org.exist.security.realm.%1$s.%2$sRealm")
    private List<Realm> realms = new ArrayList<Realm>();
    
    @ConfigurationFieldAsElement("events")
    //@ConfigurationFieldClassMask("org.exist.security.internal.SMEvents")
    private SMEvents events = null;
    
    private Collection collection = null;
    
    private Configuration configuration = null;
    
    public SecurityManagerImpl(Database db) throws ConfigurationException {
    	this.pool = db;
    	
    	defaultRealm = new RealmImpl(this, null); //TODO: in-memory configuration???
    	realms.add(defaultRealm);

    	PermissionFactory.sm = this;
    	
        Properties params = new Properties();
        params.put(getClass().getName(), this);
    	pool.getScheduler().createPeriodicJob(TIMEOUT_CHECK_PERIOD, new SessionsCheck(), TIMEOUT_CHECK_PERIOD, params, SimpleTrigger.REPEAT_INDEFINITELY, false);
    }

    /**
     * Initialize the security manager.
     * 
     * Checks if the file users.xml exists in the system collection of the database.
     * If not, it is created with two default users: admin and guest.
     *  
     * @param pool
     * @param broker
     */
    @Override
    public void attach(BrokerPool pool, DBBroker broker) throws EXistException {
//    	groups = new Int2ObjectHashMap<Group>(65);
//    	users = new Int2ObjectHashMap<User>(65);

    	this.pool = pool;
    	
        TransactionManager transaction = pool.getTransactionManager();
        Txn txn = null;
		
        Collection systemCollection = null;
        try {
	        systemCollection = broker.getCollection(XmldbURI.SYSTEM_COLLECTION_URI);
                if(systemCollection == null) {
                    txn = transaction.beginTransaction();
                    systemCollection = broker.getOrCreateCollection(txn, XmldbURI.SYSTEM_COLLECTION_URI);
                    if (systemCollection == null)
                            return;
                    systemCollection.setPermissions(Permission.DEFAULT_SYSTEM_COLLECTION_PERM);
                    broker.saveCollection(txn, systemCollection);

                    transaction.commit(txn);
                }
        } catch (Exception e) {
            transaction.abort(txn);
            e.printStackTrace();
            LOG.debug("loading acl failed: " + e.getMessage());
        }

        try {
            collection = broker.getCollection(SECURITY_COLLECTION_URI);
            if (collection == null) {
                txn = transaction.beginTransaction();
                collection = broker.getOrCreateCollection(txn, SECURITY_COLLECTION_URI);
                if (collection == null){
                    return;
                }
                //if db corrupted it can lead to unrunnable issue
                //throw new ConfigurationException("Collection '/db/system/security' can't be created.");

                collection.setPermissions(Permission.DEFAULT_SYSTEM_SECURITY_COLLECTION_PERM);
                broker.saveCollection(txn, collection);

                transaction.commit(txn);
            } 
        } catch (Exception e) {
            transaction.abort(txn);
            e.printStackTrace();
            LOG.debug("loading configuration failed: " + e.getMessage());
        }
			
        Configuration _config_ = Configurator.parse(this, broker, collection, CONFIG_FILE_URI);
        configuration = Configurator.configure(this, _config_);


        for (Realm realm : realms) {
            realm.start(broker);
        }
		   
        enableXACML = (Boolean)broker.getConfiguration().getProperty("xacml.enable");
        if(enableXACML != null && enableXACML.booleanValue()) {
            pdp = new ExistPDP(pool);
            LOG.debug("XACML enabled");
        }
    }
    
    @Override
    public boolean isXACMLEnabled() {
        return pdp != null;
    }
    
    @Override
    public ExistPDP getPDP() {
        return pdp;
    }
	
    @Override
    public boolean updateAccount(Account account) throws PermissionDeniedException, EXistException {
        if (account == null) {
            return false;
        }

        if (account.getRealmId() == null) {
            throw new ConfigurationException("Account must have realm id.");
        }
        
        accountLocks.getWriteLock(account).lock();
        try {
            return findRealmForRealmId(account.getRealmId()).updateAccount(account);
        } finally {
            accountLocks.getWriteLock(account).unlock();
        }
    }

    @Override
    public boolean updateGroup(Group group) throws PermissionDeniedException, EXistException {
        if (group == null) {
            return false;
        }

        if (group.getRealmId() == null) {
            throw new ConfigurationException("Group must have realm id.");
        }

        groupLocks.getWriteLock(group).lock();
        try {
            return findRealmForRealmId(group.getRealmId()).updateGroup(group);
        } finally {
            groupLocks.getWriteLock(group).unlock();
        }
        
    }


    @Override
    public boolean deleteGroup(String name) throws PermissionDeniedException, EXistException {

        Group group = getGroup(name);
        if (group == null) {
            return false;
        }

        if (group.getRealmId() == null) {
            throw new ConfigurationException("Group must have realm id.");
        }
        
        groupLocks.getWriteLock(group).lock();
        try {
            return findRealmForRealmId(group.getRealmId()).deleteGroup(group);
        } finally {
            groupLocks.getWriteLock(group).unlock();
        }
    }

    @Override
    public boolean deleteAccount(String name) throws PermissionDeniedException, EXistException {
        return deleteAccount(getAccount(name));
    }
	
    @Override
    public boolean deleteAccount(Account account) throws PermissionDeniedException, EXistException {

        if (account == null)
            return false;

        if (account.getRealmId() == null) {
            throw new ConfigurationException("Account must have realm id.");
        }
        
        accountLocks.getWriteLock(account).lock();
        try {
            return findRealmForRealmId(account.getRealmId()).deleteAccount(account);
        } finally {
            accountLocks.getWriteLock(account).unlock();
        }
    }

    @Override
    public Account getAccount(String name) {
//    	if (SYSTEM.equals(name))
//    		return defaultRealm.ACCOUNT_SYSTEM;
    	
        for(Realm realm : realms) {
            Account account = realm.getAccount(name);
            if (account != null) {
                return account;
            }
        }
        
        LOG.debug("Account for '" + name + "' not found!");
        return null;
    }

    @Override
    public final Account getAccount(final int id) {

        return usersById.read(new PrincipalDbRead<Account, Account>(){
            @Override
            public Account execute(final Int2ObjectHashMap<Account> principalDb) {
                return principalDb.get(id);
            }
        });
    }

    @Override
    public boolean hasGroup(String name) {
    	for (Realm realm : realms) {
            if(realm.hasGroup(name)) {
                return true;
            }
    	}
    	return false;
    }

    @Override
    public boolean hasGroup(Group group) {
    	return hasGroup(group.getName());
    }

    @Override
    public Group getGroup(String name) {
    	for(Realm realm : realms) {
            Group group = realm.getGroup(name);
            if(group != null){
                return group;
            }
    	}
        return null;
    }

    @Override
    public final Group getGroup(final int id) {
        return groupsById.read(new PrincipalDbRead<Group, Group>(){
            @Override
            public Group execute(final Int2ObjectHashMap<Group> principalDb) {
                return principalDb.get(id);
            }
        });
    }
	
    @Override
    public boolean hasAdminPrivileges(Account user) {
        
        accountLocks.getReadLock(user).lock();
        try {
            return user.hasDbaRole();
        } finally {
            accountLocks.getReadLock(user).unlock();
        }
    }

    @Override
    public boolean hasAccount(String name) {
    	for(Realm realm : realms) {
            if(realm.hasAccount(name)) {
                return true;
            }
    	}
    	return false;
    }

    @Override
    public Subject authenticate(final String username, final Object credentials) throws AuthenticationException {
        if (LOG.isDebugEnabled())
            LOG.debug("Authentication try for '"+username+"'.");

        if (username == null)
        	throw new AuthenticationException(
        			AuthenticationException.ACCOUNT_NOT_FOUND, "Account NULL not found");

        if("jsessionid".equals(username)) {
    		
    		if (getSystemSubject().getSessionId().equals(credentials))
    			return getSystemSubject();

    		if (getGuestSubject().getSessionId().equals(credentials))
    			return getGuestSubject();

            Subject subject = sessions.read(new SessionDbRead<Subject>(){
                @Override
                public Subject execute(final Map<String, Session> db) {
                	
                	Session session = db.get((String)credentials);
                	if (session == null) return null;
                	
                	if (session.isValid())
                		return session.getSubject();
                	
                	return null; 
                }
            });

            if(subject == null)
                throw new AuthenticationException(AuthenticationException.SESSION_NOT_FOUND, "Session [" + credentials + "] not found");

            if (events != null)
            	events.authenticated(subject);
            
            //TODO: validate session
            return subject;
        }

        for(Realm realm : realms) {
            try {
            	Subject subject = realm.authenticate(username, credentials);
            	
                if (LOG.isDebugEnabled())
                	LOG.debug("Authenticated by '"+realm.getId()+"' as '"+subject+"'.");
                
                if (events != null)
                	events.authenticated(subject);

                return subject;
            } catch(AuthenticationException e) {
                if(e.getType() != AuthenticationException.ACCOUNT_NOT_FOUND) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Realm '"+realm.getId()+"' threw exception for account '"+username+"'. ["+e.getMessage()+"]");
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
    		"Account [" + username + "] not found");
    }
    
    protected Subject systemSubject = null;
    protected Subject guestSubject = null;
	
    @Override
    public Subject getSystemSubject() {
    	if (systemSubject == null)
    		systemSubject = new SubjectAccreditedImpl((AccountImpl) defaultRealm.ACCOUNT_SYSTEM, this);
    	
        return systemSubject; 
    }

    @Override
    public Subject getGuestSubject() {
    	if (guestSubject == null)
    		guestSubject = new SubjectAccreditedImpl((AccountImpl)defaultRealm.getAccount(SecurityManager.GUEST_USER), this);
    	
    	return guestSubject;
    }

    @Override
    public Group getDBAGroup() {
        return defaultRealm.GROUP_DBA;
    }

    @Override
    public Database getDatabase() {
        return pool;
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
    public List<Account> getGroupMembers(String groupName) {

        final List<Account> groupMembers = new ArrayList<Account>();

        for(Realm realm : realms) {
            for(Account account : realm.getAccounts()) {
                if(account.hasGroup(groupName)) {
                    groupMembers.add(account);
                }
            }
        }

        return groupMembers;
    }

    @Override
    public List<String> findAllGroupMembers(String groupName) {
        final List<String> userNames = new ArrayList<String>();
        for(Realm realm : realms) {
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
    public void addGroup(String name) throws PermissionDeniedException, EXistException {
        addGroup(new GroupAider(name));
    }

    @Override
    public Group addGroup(Group group) throws PermissionDeniedException, EXistException {
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
        if (registeredRealm.hasGroup(group.getName())) {
            throw new ConfigurationException("The group '"+group.getName()+"' at realm '" + group.getRealmId() + "' already exist.");
        }
        
        final GroupImpl newGroup = new GroupImpl(registeredRealm, id, group.getName(), group.getManagers());
        for(final SchemaType metadataKey : group.getMetadataKeys()) {
            final String metadataValue = group.getMetadataValue(metadataKey);
            newGroup.setMetadataValue(metadataKey, metadataValue);
        }
        
        groupLocks.getWriteLock(newGroup).lock();
        try {
            groupsById.modify(new PrincipalDbModify<Group>(){
                @Override
                public void execute(final Int2ObjectHashMap<Group> principalDb) {
                    principalDb.put(id, newGroup);
                }
            });
            
            registeredRealm.registerGroup(newGroup);

            save();
            newGroup.save();

            return newGroup;
        } finally {
            groupLocks.getWriteLock(newGroup).unlock();
        }
    }

    @Override
    public final Account addAccount(Account account) throws  PermissionDeniedException, EXistException{
        if(account.getRealmId() == null) {
        	LOG.debug("Account must have realm id.");
            throw new ConfigurationException("Account must have realm id.");
        }
		
        if(account.getName() == null || account.getName().isEmpty()) {
        	LOG.debug("Account must have name.");
            throw new ConfigurationException("Account must have name.");
        }
		
        final int id;
        if(account.getId() != Account.UNDEFINED_ID) {
            id = account.getId();
        } else {
            id = getNextAccountId();
        }

		final AbstractRealm registeredRealm = (AbstractRealm) findRealmForRealmId(account.getRealmId());
		final AccountImpl newAccount = new AccountImpl(registeredRealm, id, account);
	
        accountLocks.getWriteLock(newAccount).lock();
        try {
            usersById.modify(new PrincipalDbModify<Account>(){
                @Override
                public void execute(final Int2ObjectHashMap<Account> principalDb) {
                    principalDb.put(id, newAccount);
                }
            });
            
            registeredRealm.registerAccount(newAccount);

            //XXX: one transaction?
            save();
            newAccount.save();

            return newAccount;
        } finally {
            accountLocks.getWriteLock(newAccount).unlock();
        }
    }
    
    @Override 
    public final Account addAccount(DBBroker broker, Account account) throws  PermissionDeniedException, EXistException{
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
        final AccountImpl newAccount = new AccountImpl(broker, registeredRealm, id, account);

        accountLocks.getWriteLock(newAccount).lock();
        try {
            usersById.modify(new PrincipalDbModify<Account>(){
                @Override
                public void execute(final Int2ObjectHashMap<Account> principalDb) {
                    principalDb.put(id, newAccount);
                }
            });
            
            registeredRealm.registerAccount(newAccount);

            //XXX: one transaction?
            save(broker);
            newAccount.save(broker);

            return newAccount;
        } finally {
            accountLocks.getWriteLock(newAccount).unlock();
        }
    }
	
    private void save() throws PermissionDeniedException, EXistException {
        if (configuration != null) {
            configuration.save();
        }
    }
        
    private void save(DBBroker broker) throws PermissionDeniedException, EXistException {
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
    	
    	public SessionsCheck() {
		}

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
        public final void execute( JobExecutionContext jec ) throws JobExecutionException {
            JobDataMap jobDataMap = jec.getJobDetail().getJobDataMap();

            SecurityManagerImpl sm = ( SecurityManagerImpl )jobDataMap.get( SecurityManagerImpl.class.getName() );

            if (sm == null)
            	return;
            
            sm.sessions.modify(new SessionDbModify(){
	            @Override
	            public void execute(final Map<String, Session> db) {
	            	Iterator<Map.Entry<String, Session>> iter = db.entrySet().iterator();
	            	while (iter.hasNext()) {
	            		Map.Entry<String, Session> entry = iter.next();
	            		if (entry == null || !entry.getValue().isValid()) {
	            			iter.remove();
	            		}
	            	}
	            }
	        });
    	}
    }

    @Override
    public void registerSession(final Session session) {
        sessions.modify(new SessionDbModify(){
            @Override
            public void execute(final Map<String, Session> db) {
                db.put(session.getId(), session);
            }
        });
    }

    @Override
    public Subject getSubjectBySessionId(final String sessionId) {
        return sessions.read(new SessionDbRead<Subject>(){
            @Override
            public Subject execute(final Map<String, Session> db) {
                return db.get(sessionId).getSubject();
            }
        });
    }
        
    private Realm findRealmForRealmId(String realmId) throws ConfigurationException {
        for(Realm realm : realms) {
            if(realm.getId().equals(realmId)) {
                return realm;
            }
        }
        throw new ConfigurationException("The realm id = '" + realmId + "' not found.");
    }
    
    @Override
    public void addGroup(final int id, final Group group) {
        groupsById.modify(new PrincipalDbModify<Group>(){
            @Override
            public void execute(final Int2ObjectHashMap<Group> principalDb) {
               principalDb.put(id, group);
            }
        });
    }

    @Override
    public void addUser(final int id, final Account account) {
        usersById.modify(new PrincipalDbModify<Account>(){
            @Override
            public void execute(final Int2ObjectHashMap<Account> principalDb) {
               principalDb.put(id, account);
            }
        });
    }

    @Override
    public boolean hasGroup(final int id) {
        return groupsById.read(new PrincipalDbRead<Group, Boolean>(){
            @Override
            public Boolean execute(Int2ObjectHashMap<Group> principalDb) {
                return principalDb.containsKey(id);
            }
        });
    }

    @Override
    public boolean hasUser(final int id) {
        return usersById.read(new PrincipalDbRead<Account, Boolean>(){
            @Override
            public Boolean execute(Int2ObjectHashMap<Account> principalDb) {
                return principalDb.containsKey(id);
            }
        });
    }

    @Override
    public List<String> findUsernamesWhereNameStarts(String startsWith) {
        List<String> userNames = new ArrayList<String>();
        for(Realm realm : realms) {
            userNames.addAll(realm.findUsernamesWhereNameStarts(startsWith));
        }
        return userNames;
    }

    @Override
    public List<String> findUsernamesWhereUsernameStarts(String startsWith) {
        List<String> userNames = new ArrayList<String>();
        for(Realm realm : realms) {
            userNames.addAll(realm.findUsernamesWhereUsernameStarts(startsWith));
        }
        return userNames;
    }
    
    @Override
    public List<String> findUsernamesWhereNamePartStarts(String startsWith) {
        List<String> userNames = new ArrayList<String>();
        for(Realm realm : realms) {
            userNames.addAll(realm.findUsernamesWhereNamePartStarts(startsWith));
        }
        return userNames;
    }

    @Override
    public List<String> findGroupnamesWhereGroupnameContains(String fragment) {
        List<String> groupNames = new ArrayList<String>();
        for(Realm realm : realms) {
            groupNames.addAll(realm.findGroupnamesWhereGroupnameContains(fragment));
        }
        return groupNames;
    }
    
    @Override
    public List<String> findGroupnamesWhereGroupnameStarts(String startsWith) {
        List<String> groupNames = new ArrayList<String>();
        for(Realm realm : realms) {
            groupNames.addAll(realm.findGroupnamesWhereGroupnameStarts(startsWith));
        }
        return groupNames;
    }

    @Override
    public List<String> findAllGroupNames() {
        List<String> groupNames = new ArrayList<String>();
        for(Realm realm : realms) {
            groupNames.addAll(realm.findAllGroupNames());
        }
        return groupNames;
    }
    
    @Override
    public List<String> findAllUserNames() {
        final List<String> userNames = new ArrayList<String>();
        for(final Realm realm : realms) {
            userNames.addAll(realm.findAllUserNames());
        }
        return userNames;
    }
    
    private Map<XmldbURI, Integer> saving = new HashMap<XmldbURI, Integer>();
    
    @Override
    public void processPramatterBeforeSave(DBBroker broker, DocumentImpl document) throws ConfigurationException {
        XmldbURI uri = document.getCollection().getURI();
        
        boolean isRemoved = uri.endsWith(SecurityManager.REMOVED_COLLECTION_URI);
        if(isRemoved) {
            uri = uri.removeLastSegment();
        }
		
        boolean isAccount = uri.endsWith(SecurityManager.ACCOUNTS_COLLECTION_URI);
        boolean isGroup = uri.endsWith(SecurityManager.GROUPS_COLLECTION_URI);
		
        if(isAccount || isGroup) {
            //uri = uri.removeLastSegment();
            //String realmId = uri.lastSegment().toString();
            //AbstractRealm realm = (AbstractRealm)findRealmForRealmId(realmId);
            Configuration conf = Configurator.parse(document);

        	saving.put(document.getURI(), conf.getPropertyInteger("id"));
        }
    }

    @Override
    public void processPramatter(DBBroker broker, DocumentImpl document) throws ConfigurationException {

        XmldbURI uri = document.getCollection().getURI();
        
        //System.out.println(document);

        boolean isRemoved = uri.endsWith(SecurityManager.REMOVED_COLLECTION_URI);
        if(isRemoved) {
            uri = uri.removeLastSegment();
        }
		
        boolean isAccount = uri.endsWith(SecurityManager.ACCOUNTS_COLLECTION_URI);
        boolean isGroup = uri.endsWith(SecurityManager.GROUPS_COLLECTION_URI);
		
        if(isAccount || isGroup) {
            uri = uri.removeLastSegment();

            String realmId = uri.lastSegment().toString();
			
            AbstractRealm realm = (AbstractRealm)findRealmForRealmId(realmId);
            Configuration conf = Configurator.parse(document);

            Integer id = -1;
            if(isRemoved) {
                id = conf.getPropertyInteger("id");
            }

            String name = conf.getProperty("name");

            if(isAccount) {
                if (isRemoved && id > 2 && !hasUser(id)) {
                    AccountImpl account = new AccountImpl( realm, conf );
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
	            	            usersById.modify(new PrincipalDbModify<Account>(){
	            	                @Override
	            	                public void execute(final Int2ObjectHashMap<Account> principalDb) {
	            	                    principalDb.remove(oldId);
	            	                    principalDb.put(newId, current);
	            	                }
	            	            });
	            	        } finally {
	            	            accountLocks.getWriteLock(current).unlock();
	            	        }
            			}
                	} else {
                		Account account = new AccountImpl( realm, conf );
                		addUser(account.getId(), account);
                		realm.registerAccount(account);
                	}
                } else {
                    //this can't be! log any way
                    LOG.error("Account '"+name+"' pressent at '"+realmId+"' realm, but get event that new one created.");
                }
            
            } else if(isGroup) {
                if (isRemoved && id > 2 && !hasGroup(id)) {
                    GroupImpl group = new GroupImpl( realm, conf );
                    group.removed = true;
                    addGroup(group.getId(), group);
                } else if (name != null && !realm.hasGroup(name)) {
                    GroupImpl group = new GroupImpl( realm, conf );
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

    private class PrincipalLocks<T extends Principal> {
        private final Map<Integer, ReentrantReadWriteLock> locks = new HashMap<Integer, ReentrantReadWriteLock>();
        private synchronized ReentrantReadWriteLock getLock(T principal) {
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
   
    protected class SessionDb {
        private final Map<String, Session> db = new HashMap<String, Session>();
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final ReadLock readLock = lock.readLock();
        private final WriteLock writeLock = lock.writeLock();

        public <R> R read(final SessionDbRead<R> readOp) {
            readLock.lock();
            try {
                return readOp.execute(db);
            } finally {
                readLock.unlock();
            }
        }

        public final void modify(final SessionDbModify writeOp) {
            writeLock.lock();
            try {
                writeOp.execute(db);
            } finally {
                writeLock.unlock();
            }
        }
    }
    
    protected interface SessionDbRead<R> {
       public R execute(final Map<String, Session> db);
    }

    protected interface SessionDbModify {
        public void execute(final Map<String, Session> db);
    }
   
    protected class PrincipalDbById<V extends Principal> {
    
        private final Int2ObjectHashMap<V> db = new Int2ObjectHashMap<V>(65);
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final ReadLock readLock = lock.readLock();
        private final WriteLock writeLock = lock.writeLock();

        public <R> R read(final PrincipalDbRead<V, R> readOp) {
            readLock.lock();
            try {
                return readOp.execute(db);
            } finally {
                readLock.unlock();
            }
        }

        public final void modify(final PrincipalDbModify<V> writeOp) {
            writeLock.lock();
            try {
                writeOp.execute(db);
            } finally {
                writeLock.unlock();
            }
        }

        public final <E extends Exception> void modifyE(final PrincipalDbModifyE<V, E> writeOp) throws E {
            writeLock.lock();
            try {
                writeOp.execute(db);
            } finally {
                writeLock.unlock();
            }
        }

        public final <E extends Exception, E2 extends Exception> void modify2E(final PrincipalDbModify2E<V, E, E2> writeOp) throws E, E2 {
            writeLock.lock();
            try {
                writeOp.execute(db);
            } finally {
                writeLock.unlock();
            }
        }
    }
    
    protected interface PrincipalDbRead<V extends Principal, R> {
       public R execute(final Int2ObjectHashMap<V> principalDb);
    }

    protected interface PrincipalDbModify<V extends Principal> {
        public void execute(final Int2ObjectHashMap<V> principalDb);
    }

    protected interface PrincipalDbModifyE<V extends Principal, E extends Exception> {
        public void execute(final Int2ObjectHashMap<V> principalDb) throws E;
    }

    protected interface PrincipalDbModify2E<V extends Principal, E extends Exception, E2 extends Exception> {
        public void execute(final Int2ObjectHashMap<V> principalDb) throws E, E2;
    }

	@Override
	public Subject getCurrentSubject() {
		return pool.getSubject();
	}
}
