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

import org.exist.security.AbstractRealm;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.exist.Database;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfiguration;
import org.exist.collections.triggers.TriggerException;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.ConfigurationException;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsAttribute;
import org.exist.config.annotation.ConfigurationFieldAsElement;
import org.exist.config.annotation.ConfigurationFieldClassMask;
import org.exist.dom.DocumentImpl;
import org.exist.security.AuthenticationException;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.PermissionFactory;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.security.Account;
import org.exist.security.UUIDGenerator;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.realm.Realm;
import org.exist.security.xacml.ExistPDP;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.hashtable.Int2ObjectHashMap;
import org.exist.xmldb.XmldbURI;

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
	
	public static final String CONFIGURATION_ELEMENT_NAME = "default-permissions";
	public static final String COLLECTION_ATTRIBUTE = "collection";
	public static final String RESOURCE_ATTRIBUTE = "resource";
	
	public static final String PROPERTY_PERMISSIONS_COLLECTIONS = "indexer.permissions.collection";
	public static final String PROPERTY_PERMISSIONS_RESOURCES = "indexer.permissions.resource";	

	private final static Logger LOG = Logger.getLogger(SecurityManager.class);

	private Database pool;

	protected Int2ObjectHashMap<Group> groupsById = new Int2ObjectHashMap<Group>(65);
	protected Int2ObjectHashMap<Account> usersById = new Int2ObjectHashMap<Account>(65);
	
	@ConfigurationFieldAsAttribute("last-account-id")
	protected int lastUserId = 0;

	@ConfigurationFieldAsAttribute("last-group-id")
	protected int lastGroupId = 0;

	@ConfigurationFieldAsAttribute("version")
	private String version = "2.0";

    @ConfigurationFieldAsElement("Authentication-Entry-Point")
    public final static String authenticationEntryPoint = "/authentication/login";
    
	//@ConfigurationField("enableXACML")
	private Boolean enableXACML = false;

	private ExistPDP pdp;
    
    private RealmImpl defaultRealm;
    
    @ConfigurationFieldAsElement("realm")
    @ConfigurationFieldClassMask("org.exist.security.realm.%1$s.%2$sRealm")
    private List<Realm> realms = new ArrayList<Realm>();
    
    private Collection collection = null;
    
    private Configuration configuration = null;
    
    public SecurityManagerImpl(Database db) throws ConfigurationException {
    	this.pool = db;
    	
    	defaultRealm = new RealmImpl(this, null); //TODO: in-memory configuration???
    	realms.add(defaultRealm);

    	PermissionFactory.sm = this;
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
			if (systemCollection == null) {
				txn = transaction.beginTransaction();
				systemCollection = broker.getOrCreateCollection(txn, XmldbURI.SYSTEM_COLLECTION_URI);
				if (systemCollection == null)
					return;
				systemCollection.setPermissions(0770);
				broker.saveCollection(txn, systemCollection);

				transaction.commit(txn);
			}
        } catch (Exception e) {
			transaction.abort(txn);
			e.printStackTrace();
			LOG.debug("loading acl failed: " + e.getMessage());
		}

        try {
	        collection = broker.getCollection(SECURITY_COLLETION_URI);
			if (collection == null) {
				txn = transaction.beginTransaction();
				collection = broker.getOrCreateCollection(txn, SECURITY_COLLETION_URI);
				if (collection == null) return;
					//if db corrupted it can lead to unrunnable issue
					//throw new ConfigurationException("Collection '/db/system/security' can't be created.");

				collection.setPermissions(0770);
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
                    realm.startUp(broker);
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
    public synchronized <A extends Account> boolean updateAccount(Subject invokingUser, A account) throws PermissionDeniedException, EXistException {
        if(account == null){
            return false;
        }

        if(account.getRealmId() == null) {
            throw new ConfigurationException("Account must have realm id.");
        }

        Realm registeredRealm = findRealmForRealmId(account.getRealmId());

        return registeredRealm.updateAccount(invokingUser, account);
    }

    @Override
    public synchronized <G extends Group> boolean updateGroup(Subject invokingUser, G group) throws PermissionDeniedException, EXistException {
        if(group == null){
            return false;
        }

        if(group.getRealmId() == null) {
            throw new ConfigurationException("Group must have realm id.");
        }

        Realm registeredRealm = findRealmForRealmId(group.getRealmId());

        return registeredRealm.updateGroup(invokingUser, group);
    }


    @Override
	public synchronized void deleteGroup(Subject invokingUser, String name) throws PermissionDeniedException, EXistException {

        Group group = getGroup(invokingUser, name);
        if(group == null){
            return;
        }

        if(group.getRealmId() == null) {
            throw new ConfigurationException("Group must have realm id.");
        }

        Realm registeredRealm = findRealmForRealmId(group.getRealmId());

        registeredRealm.deleteGroup(group);
	}

    @Override
	public synchronized void deleteAccount(Subject invokingUser, String name) throws PermissionDeniedException, EXistException {
		deleteAccount(invokingUser, getAccount(invokingUser, name));
	}
	
    @Override
    public synchronized <A extends Account> void deleteAccount(Subject invokingUser, A account) throws PermissionDeniedException, EXistException {

        if(account == null){
            return;
        }

        if(account.getRealmId() == null) {
            throw new ConfigurationException("Account must have realm id.");
        }

        Realm registeredRealm = findRealmForRealmId(account.getRealmId());

        registeredRealm.deleteAccount(invokingUser, account);
    }

	public Account getAccount(String name) {
		return getAccount(null, name);
	}

    @Override
	public synchronized Account getAccount(Subject invokingUser, String name) {
		for (Realm realm : realms) {
			Account account = realm.getAccount(invokingUser, name);
			if (account != null) return account;
		}
		LOG.debug("user " + name + " not found");
		return null;
	}

    @Override
	public final synchronized Account getAccount(int id) {
		return usersById.get(id);
	}

    @Override
    public synchronized boolean hasGroup(String name) {
    	for (Realm realm : realms) {
    		if (realm.hasGroup(name)) return true;
    	}
    	return false;
	}

    @Override
    public boolean hasGroup(Group group) {
    	return hasGroup(group.getName());
    }

    @Override
    public synchronized Group getGroup(Subject invokingUser, String name) {
    	for (Realm realm : realms) {
    		Group group = realm.getGroup(invokingUser, name);
    		if (group != null) return group;
    	}
		return null;
	}

    @Override
	public final synchronized Group getGroup(int id) {
		return groupsById.get(id);
	}
	
    @Override
	public synchronized boolean hasAdminPrivileges(Account user) {
		return user.hasDbaRole();
	}

    @Override
	public synchronized boolean hasAccount(String name) {
    	for (Realm realm : realms) {
    		if (realm.hasAccount(name)) return true;
    	}
    	return false;
	}

	private void createUserHome(DBBroker broker, Txn transaction, Account user) throws EXistException, PermissionDeniedException, IOException, TriggerException {
		if(user.getHome() == null)
			return;
		
		Subject currentUser = broker.getSubject();
		
		try {
			broker.setUser(getSystemSubject());
			Collection home = broker.getOrCreateCollection(transaction, user.getHome());
			home.getPermissions().setOwner(user.getName());
			
			CollectionConfiguration config = home.getConfiguration(broker);
			String group = (config!=null) ? config.getDefCollGroup(user) : user.getPrimaryGroup();
			
			//home.getMode().setGroup(group);
			home.getPermissions().setGroup(group);
			
			broker.saveCollection(transaction, home);
		} finally {
			broker.setUser(currentUser);
		}
	}

    @Override
	public synchronized Subject authenticate(String username, Object credentials) throws AuthenticationException {
		if ("jsessionid".equals(username)) {
			Subject subject = sessions.get(credentials);
			
			if (subject == null)
				throw new AuthenticationException(
						AuthenticationException.SESSION_NOT_FOUND,
						"Session [" + credentials + "] not found");
				
			//TODO: validate session
			
			return subject;
		}
		
		for (Realm realm : realms) {
			
			//if (LOG.isDebugEnabled())
			//	LOG.debug("authenticating '"+username+"' with realm '"+realm.getId()+"'...");
			
			try {
				return realm.authenticate(username, credentials);
			} catch (AuthenticationException e) {
				if (e.getType() != AuthenticationException.ACCOUNT_NOT_FOUND) {
					if (LOG.isDebugEnabled())
						LOG.debug("Realm '"+realm.getId()+"' throw exception for account '"+username+"'. ["+e.getMessage()+"]");

					throw e;
				}
			}
		}
		
		if (LOG.isDebugEnabled())
			LOG.debug("Account '"+username+"' not found, throw error");

		throw new AuthenticationException(
				AuthenticationException.ACCOUNT_NOT_FOUND,
				"User [" + username + "] not found");
	}
	
	@Override
	public Subject getSystemSubject() {
		return new SubjectAccreditedImpl((AccountImpl) defaultRealm.ACCOUNT_SYSTEM, this);
	}
	
	@Override
	public Subject getGuestSubject() {
		return new SubjectAccreditedImpl((AccountImpl) defaultRealm.ACCOUNT_GUEST, this);
	}
	
	@Override
	public Group getDBAGroup() {
		return defaultRealm.GROUP_DBA;
	}

	@Override
	public Database getDatabase() {
		return pool;
	}
	
    @Override
	public synchronized int getNextGroupId() {
		return ++lastGroupId; 
	}

    @Override
	public synchronized int getNextAccountId() {
		return ++lastUserId; 
	}

    @Override
    public List<Account> getGroupMembers(String groupName) {

        List<Account> groupMembers = new ArrayList<Account>();

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
    public List<String> findAllGroupMembers(Subject invokingUser, String groupName) {
        List<String> userNames = new ArrayList<String>();
        for(Realm realm : realms) {
            userNames.addAll(realm.findAllGroupMembers(invokingUser, groupName));
        }
        return userNames;
    }

    @Deprecated
	@Override
	public <A extends Account> java.util.Collection<A> getUsers() {
            return (java.util.Collection<A>)defaultRealm.getAccounts();

                //TODO should be refactored to get users from all realms
	}

    @Deprecated
	@Override
	public <G extends Group> java.util.Collection<G> getGroups() {
		return (java.util.Collection<G>)defaultRealm.getRoles();

                //TODO should be refactored to get groups from all realms
	}

	@Override
	public void addGroup(String name) throws PermissionDeniedException, EXistException {
		addGroup(new GroupAider(name));
	}

    @Override
    public synchronized Group addGroup(Group group) throws PermissionDeniedException, EXistException {

        if (group.getRealmId() == null) {
            throw new ConfigurationException("Group must have realm id.");
        }

        if(group.getName() == null || group.getName().isEmpty()) {
            throw new ConfigurationException("Group must have name.");
        }

        Realm registeredRealm = findRealmForRealmId(group.getRealmId());

        Group newGroup = registeredRealm.addGroup(group);
        save();
        return newGroup;
        //return defaultRealm.addGroup(group.getName());
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
	public final synchronized Account addAccount(Account account) throws  PermissionDeniedException, EXistException{
		if(account.getRealmId() == null) {
            throw new ConfigurationException("Account must have realm id.");
        }
		
		if(account.getName() == null || account.getName().isEmpty()) {
            throw new ConfigurationException("Account must have name.");
        }
		
		AbstractRealm registeredRealm = (AbstractRealm) findRealmForRealmId(account.getRealmId());
		
		int id = getNextAccountId();

//        A new_account = registeredRealm.instantiateAccount(registeredRealm, id, account);
		AccountImpl new_account = new AccountImpl(registeredRealm, id, account);
		
		usersById.put(id, new_account);
		registeredRealm.registerAccount(new_account);
		
		//XXX: one transaction?
		save();
        new_account.save();
		
		createUserHome(new_account);

		return new_account;
	}
	
	private void save() throws PermissionDeniedException, EXistException {
        if (configuration != null) {
            configuration.save();
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

	private void createUserHome(Account account) throws EXistException, PermissionDeniedException {
		if(account.getHome() == null)
			return;
		
		DBBroker broker = null;
		TransactionManager transact = getDatabase().getTransactionManager();
		Txn txn = transact.beginTransaction();
		try {
			broker = getDatabase().get(null);

			Subject currentUser = broker.getSubject();
			
			try {
		
				broker.setUser(getSystemSubject());
	
				Collection home = broker.getOrCreateCollection(txn, account.getHome());
				
				home.getPermissions().setOwner(account);
				CollectionConfiguration config = home.getConfiguration(broker);
				String group = (config!=null) ? config.getDefCollGroup(account) : account.getPrimaryGroup();
				home.getPermissions().setGroup(group);
				
				broker.saveCollection(txn, home);
				
				transact.commit(txn);
			
			} finally {
				broker.setUser(currentUser);
			}
		
		} catch (IOException e) {
			transact.abort(txn);
			
			if (LOG.isDebugEnabled()) {
				LOG.debug(e.getMessage());
			}
			e.printStackTrace();
			
			throw new EXistException(e);
		
		} catch (TriggerException e) {
			transact.abort(txn);
			
			if (LOG.isDebugEnabled()) {
				LOG.debug(e.getMessage());
			}
			e.printStackTrace();
			
			throw new EXistException(e);
		
		} catch (PermissionDeniedException e) {
			transact.abort(txn);
			
			if (LOG.isDebugEnabled()) {
				LOG.debug(e.getMessage());
			}
			e.printStackTrace();
			
			throw e;
		
		} catch (EXistException e) {
			transact.abort(txn);
			
			if (LOG.isDebugEnabled()) {
				LOG.debug(e.getMessage());
			}
			e.printStackTrace();
			
			throw e;
		
		} finally {
			getDatabase().release(broker);
		}
	}

	@Override
	public Realm getRealm(String id) {
		for (Realm realm : realms) {
			if (id.equals(realm.getId())) return realm;
		}
		return null;
	}

	//Session management part
	
	//TODO: validate & remove if session timeout
	Map<String, Subject> sessions = new HashMap<String, Subject>();
	
	@Override
	public String registerSession(Subject subject) {
		String sessionId = UUIDGenerator.getUUID();
		
		sessions.put(sessionId, subject);
		
		return sessionId;
	}

	@Override
	public Subject getSubjectBySessionId(String sessionid) {
		//TODO: validate
		return sessions.get(sessionid);
	}

    @Override
    public void addGroup(int id, Group group) {
        groupsById.put(id, group);
    }

    @Override
    public void addUser(int id, Account account) {
        usersById.put(id, account);
    }

    @Override
    public boolean hasGroup(int id) {
        return groupsById.containsKey(id);
    }

    @Override
    public boolean hasUser(int id) {
        return usersById.containsKey(id);
    }

    @Override
    public List<String> findUsernamesWhereNameStarts(Subject invokingUser, String startsWith) {
        List<String> userNames = new ArrayList<String>();
        for(Realm realm : realms) {
            userNames.addAll(realm.findUsernamesWhereNameStarts(invokingUser, startsWith));
        }
        return userNames;
    }

    @Override
    public List<String> findUsernamesWhereUsernameStarts(Subject invokingUser, String startsWith) {
        List<String> userNames = new ArrayList<String>();
        for(Realm realm : realms) {
            userNames.addAll(realm.findUsernamesWhereUsernameStarts(invokingUser, startsWith));
        }
        return userNames;
    }

    @Override
    public List<String> findAllGroupNames(Subject invokingUser) {
        List<String> groupNames = new ArrayList<String>();
        for(Realm realm : realms) {
            groupNames.addAll(realm.findAllGroupNames(invokingUser));
        }
        return groupNames;
    }

	@Override
	public void processPramatter(DBBroker broker, DocumentImpl document) throws ConfigurationException {

		XmldbURI uri = document.getCollection().getURI();
		
		boolean isRemoved = uri.endsWith(SecurityManager.REMOVED_COLLECTION_URI);
		if (isRemoved)
			uri = uri.removeLastSegment();
		
		boolean isAccount = uri.endsWith(SecurityManager.ACCOUNTS_COLLECTION_URI);
		boolean isGroup = uri.endsWith(SecurityManager.GROUPS_COLLECTION_URI);
		
		if (isAccount || isGroup) {
			uri = uri.removeLastSegment();
			
			String realmId = uri.lastSegment().toString();
			SecurityManager sm = broker.getBrokerPool().getSecurityManager();
			
			AbstractRealm realm = (AbstractRealm) sm.getRealm(realmId);
			if (realm == null) {
				//XXX: deleted realm's objects
//				Configuration conf = Configurator.parse(document);
//				if (isAccount) {
//	            	Integer id = conf.getPropertyInteger("id");
//	            	if (id != null && !getSecurityManager().hasUser(id)) {
//						AccountImpl account = new AccountImpl( null, conf );
//	            		account.removed = true;
//		            	addUser(account.getId(), account);
//	            	}
//				} else if (isGroup) {
//	            	Integer id = conf.getPropertyInteger("id");
//	            	if (id != null && !getSecurityManager().hasGroup(id)) {
//	                    GroupImpl group = new GroupImpl(null, conf);
//	                    group.removed = true;
//	            		addGroup(group.getId(), group);
//	            	}
//				}
			} else {
				Configuration conf = Configurator.parse(document);

				Integer id = -1;
				if (isRemoved) id = conf.getPropertyInteger("id");
				
            	String name = conf.getProperty("name");

            	if (isAccount) {
            		if (isRemoved && id > 2 && !hasUser(id)) {
            			AccountImpl account = new AccountImpl( realm, conf );
            			account.removed = true;
		            	addUser(account.getId(), account);
            		} else if (name != null && !realm.usersByName.containsKey(name)) {
	            		Account account = new AccountImpl( realm, conf );
		            	addUser(account.getId(), account);
		            	realm.usersByName.put(account.getName(), account);
	            	} else {
	            		//this can't be! log any way
	            		LOG.error("Account '"+name+"' pressent at '"+realmId+"' realm, but get event that new one created.");
	            	}
				} else if (isGroup) {
            		if (isRemoved && id > 2 && !hasGroup(id)) {
	            		GroupImpl group = new GroupImpl( realm, conf );
	            		group.removed = true;
	            		addGroup(group.getId(), group);
            		} else if (name != null && !realm.groupsByName.containsKey(name)) {
	            		GroupImpl group = new GroupImpl( realm, conf );
	            		addGroup(group.getId(), group);
	            		realm.groupsByName.put(group.getName(), group);
	            	} else {
	            		//this can't be! log any way
	            		LOG.error("Group '"+name+"' pressent at '"+realmId+"' realm, but get event that new one created.");
	            	}
				}
			}
			
		}
	}

	@Override
	public String getAuthenticationEntryPoint() {
		return authenticationEntryPoint;
	}
}