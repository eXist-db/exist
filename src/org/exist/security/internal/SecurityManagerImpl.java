/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2010 The eXist Project
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
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfiguration;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.ConfigurationException;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsAttribute;
import org.exist.security.AuthenticationException;
import org.exist.security.Group;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.PermissionFactory;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.security.UnixStylePermission;
import org.exist.security.Account;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.realm.Realm;
import org.exist.security.xacml.ExistPDP;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.hashtable.Int2ObjectHashMap;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
@ConfigurationClass("security-manager")
public class SecurityManagerImpl implements SecurityManager {
	
	public static final String CONFIGURATION_ELEMENT_NAME = "default-permissions";
	public static final String COLLECTION_ATTRIBUTE = "collection";
	public static final String RESOURCE_ATTRIBUTE = "resource";
	
	public static final String PROPERTY_PERMISSIONS_COLLECTIONS = "indexer.permissions.collection";
	public static final String PROPERTY_PERMISSIONS_RESOURCES = "indexer.permissions.resource";	

	private final static Logger LOG = Logger.getLogger(SecurityManager.class);

	private BrokerPool pool;

	protected Int2ObjectHashMap<Group> groupsById = new Int2ObjectHashMap<Group>(65);
	protected Int2ObjectHashMap<Account> usersById = new Int2ObjectHashMap<Account>(65);
	
	@ConfigurationFieldAsAttribute("last-account-id")
	protected int lastUserId = 0;

	@ConfigurationFieldAsAttribute("last-group-id")
	protected int lastGroupId = 0;

	@ConfigurationFieldAsAttribute("version")
	private String version = "2.0";

//	@ConfigurationField("enableXACML")
	private Boolean enableXACML = false;

	private ExistPDP pdp;
    
	//default UnixStylePermission
    public final Permission DEFAULT_PERMISSION;
    
    private RealmImpl defaultRealm;
    
    @ConfigurationFieldAsAttribute("realm")
    private List<Realm> realms = new ArrayList<Realm>();
    
    private Collection collection = null;
    
    private Configuration configuration = null;
    
    public SecurityManagerImpl(BrokerPool pool) throws ConfigurationException {
    	this.pool = pool;
    	
    	defaultRealm = new RealmImpl(this, null); //TODO: in-memory configuration???
    	realms.add(defaultRealm);

    	PermissionFactory.sm = this;
       
    	DEFAULT_PERMISSION = new UnixStylePermission(this);
    	
    }

    /**
	 * Initialize the security manager.
	 * 
	 * Checks if the file users.xml exists in the system collection of the database.
	 * If not, it is created with two default users: admin and guest.
	 *  
	 * @param pool
	 * @param sysBroker
	 */
    public void attach(BrokerPool pool, DBBroker broker) throws EXistException {
//    	groups = new Int2ObjectHashMap<Group>(65);
//    	users = new Int2ObjectHashMap<User>(65);

    	this.pool = pool;
    	
        String COLLECTION_CONFIG =
            "<collection xmlns='http://exist-db.org/collection-config/1.0'>" +
        	"	<exist:triggers>" +
        	"		<exist:trigger event='store,remove' class='org.exist.examples.triggers.ExampleTrigger'>" +
        	"		</exist:trigger>" +
        	"	</exist:triggers>" +
        	"	<index>" +
        	"		<fulltext default=\"none\">" +
        	"		</fulltext>" +
            "		<create path=\"//user/@uid\" type=\"xs:integer\"/>" +
            "	</index>" +
        	"</collection>";

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

//				CollectionConfigurationManager mgr = pool.getConfigurationManager();
//		        mgr.addConfiguration(txn, broker, systemCollection, COLLECTION_CONFIG);

				transaction.commit(txn);
			}
        } catch (Exception e) {
			transaction.abort(txn);
			e.printStackTrace();
			LOG.debug("loading acl failed: " + e.getMessage());
		}

		Document config = systemCollection.getDocument(broker, ACL_FILE_URI);
		if (config != null) {
			Element element = config.getDocumentElement();

			Attr version = element.getAttributeNode("version");
			int major = 0;
			int minor = 0;
			if (version!=null) {
				String [] numbers = version.getValue().split("\\.");
				major = Integer.parseInt(numbers[0]);
				minor = Integer.parseInt(numbers[1]);
			}
			//check version
			
			//load last account & role id
			NodeList nl = element.getChildNodes();
			Element next;
			String lastId;
			for (int i = 0; i < nl.getLength(); i++) {
				if(nl.item(i).getNodeType() != Node.ELEMENT_NODE)
					continue;
				next = (Element) nl.item(i);
				if (next.getTagName().equals("users")) {
					lastId = next.getAttribute("last-id");
					try {
						lastUserId = Integer.parseInt(lastId);
					} catch (NumberFormatException e) {
					}
				} else if (next.getTagName().equals("groups")) {
					lastId = next.getAttribute("last-id");
					try {
						lastGroupId = Integer.parseInt(lastId);
					} catch (NumberFormatException e) {
					}
				}
			}
		}

        try {
	        collection = broker.getCollection(SECURITY_COLLETION_URI);
			if (collection == null) {
				txn = transaction.beginTransaction();
				collection = broker.getOrCreateCollection(txn, SECURITY_COLLETION_URI);
				if (collection == null) return; //throw error???
				collection.setPermissions(0770);
				broker.saveCollection(txn, collection);

//				CollectionConfigurationManager mgr = pool.getConfigurationManager();
//		        mgr.addConfiguration(txn, broker, collection, COLLECTION_CONFIG);

				transaction.commit(txn);
			}
			
			//XXX: move it to BrokerBool initialization
	        CollectionConfigurationManager manager = broker.getBrokerPool().getConfigurationManager();
			CollectionConfiguration collConf = manager.getOrCreateCollectionConfiguration(broker, collection);
			collConf.registerTrigger(broker, 
					"store,update,remove", 
					"org.exist.config.ConfigurationDocumentTrigger", null);

        	Configuration _config_ = Configurator.parse(this, broker, collection, CONFIG_FILE_URI);
    		configuration = Configurator.configure(this, _config_);

        } catch (Exception e) {
			transaction.abort(txn);
			e.printStackTrace();
			LOG.debug("loading configuration failed: " + e.getMessage());
		}

		for (Realm realm : realms) {
    		realm.startUp(broker);
    	}
        
//        
//		Element docElement = null;
//		if (new_config != null)
//			docElement = new_config.getDocumentElement();
//		if (docElement == null) {
//			_save();
//		} else {
//			
//		}

//       TransactionManager transact = pool.getTransactionManager();
//       Txn txn = null;
//       DBBroker broker = sysBroker;
//       try {
//          Collection sysCollection = broker.getCollection(XmldbURI.SYSTEM_COLLECTION_URI);
//          if (sysCollection == null) {
//             txn = transact.beginTransaction();
//             sysCollection = broker.getOrCreateCollection(txn, XmldbURI.SYSTEM_COLLECTION_URI);
//              if (sysCollection == null)
//                return;
//             sysCollection.setPermissions(0770);
//             broker.saveCollection(txn, sysCollection);
//             transact.commit(txn);
//          }
//          Document acl = sysCollection.getDocument(broker, ACL_FILE_URI);
//          Element docElement = null;
//          if (acl != null)
//             docElement = acl.getDocumentElement();
//          if (docElement == null) {
//             LOG.debug("creating system users");
//             UserImpl user = new UserImpl(DBA_USER, "");
//             user.addGroup(DBA_GROUP);
//             user.setUID(++nextUserId);
//             users.put(user.getUID(), user);
//             user = new UserImpl(GUEST_USER, GUEST_USER, GUEST_GROUP);
//             user.setUID(++nextUserId);
//             users.put(user.getUID(), user);
//             newGroup(DBA_GROUP);
//             newGroup(GUEST_GROUP);
//             txn = transact.beginTransaction();
//             save(broker, txn);
//             transact.commit(txn);
//          } else {
//             LOG.debug("loading acl");
//             Element root = acl.getDocumentElement();
//             Attr version = root.getAttributeNode("version");
//             int major = 0;
//             int minor = 0;
//             if (version!=null) {
//                String [] numbers = version.getValue().split("\\.");
//                major = Integer.parseInt(numbers[0]);
//                minor = Integer.parseInt(numbers[1]);
//             }
//             NodeList nl = root.getChildNodes();
//             Node node;
//             Element next;
//             User user;
//             NodeList ul;
//             String lastId;
//             Group group;
//             for (int i = 0; i < nl.getLength(); i++) {
//                if(nl.item(i).getNodeType() != Node.ELEMENT_NODE)
//                   continue;
//                next = (Element) nl.item(i);
//                if (next.getTagName().equals("users")) {
//                   lastId = next.getAttribute("last-id");
//                   try {
//                      nextUserId = Integer.parseInt(lastId);
//                   } catch (NumberFormatException e) {
//                   }
//                   ul = next.getChildNodes();
//                   for (int j = 0; j < ul.getLength(); j++) {
//                      node = ul.item(j);
//                      if(node.getNodeType() == Node.ELEMENT_NODE &&
//                              node.getLocalName().equals("user")) {
//                         user = new UserImpl(major,minor,(Element)node);
//                         users.put(user.getUID(), user);
//                      }
//                   }
//                } else if (next.getTagName().equals("groups")) {
//                   lastId = next.getAttribute("last-id");
//                   try {
//                      nextGroupId = Integer.parseInt(lastId);
//                   } catch (NumberFormatException e) {
//                   }
//                   ul = next.getChildNodes();
//                   for (int j = 0; j < ul.getLength(); j++) {
//                      node = ul.item(j);
//                      if(node.getNodeType() == Node.ELEMENT_NODE &&
//                              node.getLocalName().equals("group")) {
//                         group = new GroupImpl((Element)node);
//                         groups.put(group.getId(), group);
//                      }
//                   }
//                }
//             }
//          }
//       } catch (Exception e) {
//          transact.abort(txn);
//          e.printStackTrace();
//          LOG.debug("loading acl failed: " + e.getMessage());
//       }

		//XXX: move to CollectionManagment
		// read default collection and resource permissions
//		Integer defOpt = (Integer) broker.getConfiguration().getProperty(PROPERTY_PERMISSIONS_COLLECTIONS);
//		if (defOpt != null)
//			defaultResourcePermissions = defOpt.intValue();
//		
//		defOpt = (Integer)broker.getConfiguration().getProperty(PROPERTY_PERMISSIONS_RESOURCES);
//		if (defOpt != null)
//			defaultResourcePermissions = defOpt.intValue();
		   
		enableXACML = (Boolean)broker.getConfiguration().getProperty("xacml.enable");
		if(enableXACML != null && enableXACML.booleanValue()) {
			pdp = new ExistPDP(pool);
			LOG.debug("XACML enabled");
		}
    }
    
	public boolean isXACMLEnabled() {
		return pdp != null;
	}
	public ExistPDP getPDP() {
		return pdp;
	}
	
	public synchronized boolean updateAccount(Account account) throws PermissionDeniedException, EXistException {
		return defaultRealm.updateAccount(account);
	}

	public synchronized void deleteRole(String name) throws PermissionDeniedException, EXistException {
		defaultRealm.deleteRole(name);
	}

	public synchronized void deleteUser(String name) throws PermissionDeniedException, EXistException {
		deleteUser(getUser(name));
	}
	
	public synchronized void deleteUser(Account user) throws PermissionDeniedException, EXistException {
		if(user == null)
			return;
		
		defaultRealm.deleteAccount(user);
	}

	public synchronized Account getUser(String name) {
		for (Realm realm : realms) {
			Account account = realm.getAccount(name);
			if (account != null) return account;
		}
		LOG.debug("user " + name + " not found");
		return null;
	}

	public final synchronized Account getUser(int id) {
		return usersById.get(id);
	}
	
    public synchronized void addGroup(Group name) throws PermissionDeniedException, EXistException {
    	defaultRealm.addGroup(name.getName());
    }

    public synchronized boolean hasGroup(String name) {
    	for (Realm realm : realms) {
    		if (realm.hasGroup(name)) return true;
    	}
    	return false;
	}

    public boolean hasGroup(Group group) {
    	return hasGroup(group.getName());
    }

    public synchronized Group getGroup(String name) {
    	for (Realm realm : realms) {
    		Group group = realm.getGroup(name);
    		if (group != null) return group;
    	}
		return null;
	}

	public final synchronized Group getGroup(int id) {
		return groupsById.get(id);
	}
	
	public synchronized boolean hasAdminPrivileges(Account user) {
		return user.hasDbaRole();
	}

	public synchronized boolean hasUser(String name) {
    	for (Realm realm : realms) {
    		if (realm.hasAccount(name)) return true;
    	}
    	return false;
	}

	private synchronized void save(DBBroker broker, Txn transaction) throws EXistException {
		LOG.debug("storing acl file");
//		StringBuffer buf = new StringBuffer();
//        buf.append("<!-- Central user configuration. Editing this document will cause the security " +
//                "to reload and update its internal database. Please handle with care! -->");
//		buf.append("<auth version='1.0'>");
//		// save groups
//        buf.append("<!-- Please do not remove the guest and admin groups -->");
//		buf.append("<groups last-id=\"");
//		buf.append(Integer.toString(nextGroupId));
//		buf.append("\">");
//		for (Iterator i = groups.valueIterator(); i.hasNext();)
//			buf.append(((Group) i.next()).toString());
//		buf.append("</groups>");
//		//save users
//        buf.append("<!-- Please do not remove the admin user. -->");
//		buf.append("<users last-id=\"");
//		buf.append(Integer.toString(nextUserId));
//		buf.append("\">");
//		for (Iterator i = users.valueIterator(); i.hasNext();)
//			buf.append(((User) i.next()).toString());
//		buf.append("</users>");
//		buf.append("</auth>");
//        
//		// store users.xml
//		broker.flush();
//		broker.sync(Sync.MAJOR_SYNC);
//		
//		User currentUser = broker.getUser();
//		try {
//			broker.setUser(getUser(DBA_USER));
//			Collection sysCollection = broker.getCollection(XmldbURI.SYSTEM_COLLECTION_URI);
//            String data = buf.toString();
//            IndexInfo info = sysCollection.validateXMLResource(transaction, broker, ACL_FILE_URI, data);
//            //TODO : unlock the collection here ?
//            DocumentImpl doc = info.getDocument();
//            doc.getMetadata().setMimeType(MimeType.XML_TYPE.getName());
//            sysCollection.store(transaction, broker, info, data, false);
//			doc.setPermissions(0770);
//			broker.saveCollection(transaction, doc.getCollection());
//		} catch (IOException e) {
//			throw new EXistException(e.getMessage());
//        } catch (TriggerException e) {
//            throw new EXistException(e.getMessage());
//		} catch (SAXException e) {
//			throw new EXistException(e.getMessage());
//		} catch (PermissionDeniedException e) {
//			throw new EXistException(e.getMessage());
//		} catch (LockException e) {
//			throw new EXistException(e.getMessage());
//		} finally {
//			broker.setUser(currentUser);
//		}
//		
//		broker.flush();
//		broker.sync(Sync.MAJOR_SYNC);
	}

	public synchronized void setUser(Account user) throws PermissionDeniedException, EXistException, ConfigurationException {
		 defaultRealm.addAccount(user);
		
//		if (user.getUID() < 0)
//			user.setUID(++nextUserId);
//		users.put(user.getUID(), user);
//		String[] groups = user.getGroups();
//        // if no group is specified, we automatically fall back to the guest group
//        if (groups.length == 0)
//            user.addGroup(GUEST_GROUP);
//		for (int i = 0; i < groups.length; i++) {
//			if (!hasGroup(groups[i]))
//				newGroup(groups[i]);
//		}
//        TransactionManager transact = pool.getTransactionManager();
//        Txn txn = transact.beginTransaction();
//		DBBroker broker = null;
//		try {
//			broker = pool.get(SYSTEM_USER);
//			save(broker, txn);
//			createUserHome(broker, txn, user);
//            transact.commit(txn);
//		} catch (EXistException e) {
//            transact.abort(txn);
//			LOG.debug("error while creating user", e);
//		} catch (IOException e) {
//            transact.abort(txn);
//			LOG.debug("error while creating home collection", e);
//		} catch (PermissionDeniedException e) {
//            transact.abort(txn);
//			LOG.debug("error while creating home collection", e);
//		} finally {
//			pool.release(broker);
//		}
	}
	
	private void createUserHome(DBBroker broker, Txn transaction, Account user) throws EXistException, PermissionDeniedException, IOException {
		if(user.getHome() == null)
			return;
		
		Subject currentUser = broker.getUser();
		
		try {
			broker.setUser(getSystemSubject());
			Collection home = broker.getOrCreateCollection(transaction, user.getHome());
			home.getPermissions().setOwner(user.getName());
			
			CollectionConfiguration config = home.getConfiguration(broker);
			String group = (config!=null) ? config.getDefCollGroup(user) : user.getPrimaryGroup();
			
			home.getPermissions().setGroup(group);
			home.getPermissions().setGroup(group);
			
			broker.saveCollection(transaction, home);
		} finally {
			broker.setUser(currentUser);
		}
	}

	public synchronized Subject authenticate(String username, Object credentials) throws AuthenticationException {
		for (Realm realm : realms) {
			try {
				return realm.authenticate(username, credentials);
			} catch (AuthenticationException e) {
				if (e.getType() != AuthenticationException.ACCOUNT_NOT_FOUND)
					throw e;
			}
		}
		throw new AuthenticationException(
				AuthenticationException.ACCOUNT_NOT_FOUND,
				"User [" + username + "] not found");
	}
	
	@Override
	public Subject getSystemSubject() {
		return new SubjectImpl((AccountImpl) defaultRealm.ACCOUNT_SYSTEM, "");
	}
	
	@Override
	public Subject getGuestSubject() {
		return new SubjectImpl((AccountImpl) defaultRealm.ACCOUNT_GUEST, "");
	}
	
	@Override
	public Group getDBAGroup() {
		return defaultRealm.GROUP_DBA;
	}

	@Override
	public BrokerPool getDatabase() {
		return pool;
	}
	
	protected synchronized int getNextGroupId() {
		return ++lastGroupId; 
	}
	
	protected synchronized int getNextAccoutId() {
		return ++lastUserId; 
	}

	@Override
	public java.util.Collection<Account> getUsers() {
		return defaultRealm.getAccounts();
	}

	@Override
	public java.util.Collection<Group> getGroups() {
		return defaultRealm.getRoles();
	}

	@Override
	public void addGroup(String name) throws PermissionDeniedException, EXistException {
		addGroup(new GroupAider(name));
	}
	
	protected final synchronized Account addAccount(Account account) throws EXistException, PermissionDeniedException {
		if (account.getRealm() == null) 
			throw new ConfigurationException("Account must have realm.");
		
		if (account.getName() == null || account.getName().isEmpty()) 
			throw new ConfigurationException("Account must have name.");
		
		AbstractRealm registeredRealm = null;
		for (Realm realm : realms) {
			if (realm.getId().equals( account.getRealm().getId() )) {
				registeredRealm = (AbstractRealm)realm;
				break;
			}
		}
		if (registeredRealm == null) 
			throw new ConfigurationException("The realm id = '"+account.getRealm().getId()+"' not found.");
		
		int id = getNextAccoutId();
		
		Account new_account = new AccountImpl(registeredRealm, id, account);
		
		usersById.put(id, new_account);
		registeredRealm.registerAccount(new_account);
		
		//XXX: one transaction?
		configuration.save();
		new_account.getConfiguration().save();
		
		createUserHome(new_account);

		return account;
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

			Subject currentUser = broker.getUser();
			
			try {
		
				broker.setUser(getSystemSubject());
	
				Collection home = broker.getOrCreateCollection(txn, account.getHome());
				
				home.getPermissions().setOwner(account);
				CollectionConfiguration config = home.getConfiguration(broker);
				String role = (config!=null) ? config.getDefCollGroup(account) : account.getPrimaryGroup();
				home.getPermissions().setGroup(role);
				
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
}
