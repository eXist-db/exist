/*
 * LDAPSecurityManager.java
 *
 * Created on January 29, 2006, 8:22 PM
 *
 * (C) R. Alexander Milowski alex@milowski.com
 */

package org.exist.security.ldap;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.apache.log4j.Logger;
import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.security.AuthenticationException;
import org.exist.security.Group;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.security.Account;
import org.exist.security.internal.GroupImpl;
import org.exist.security.internal.SubjectImpl;
import org.exist.security.internal.AccountImpl;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.realm.Realm;
import org.exist.security.xacml.ExistPDP;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;

/**
 * Note: A lot of this code is "borrowed" from Tomcat's JNDIRealm.java
 * @author R. Alexander Milowski
 */
public class SecurityManagerImpl implements SecurityManager
{

   private final static Logger LOG = Logger.getLogger(SecurityManager.class);
   protected Map<String, AccountImpl> userByNameCache = new HashMap<String, AccountImpl>();
   protected Map<Integer, AccountImpl> userByIdCache = new HashMap<Integer, AccountImpl>();
   protected Map<String, GroupImpl> groupByNameCache = new HashMap<String, GroupImpl>();
   protected Map<Integer, GroupImpl> groupByIdCache = new HashMap<Integer, GroupImpl>();
   
   static String getProperty(String name,String defaultValue) {
      String value = System.getProperty(name);
      return value==null ? defaultValue : value;
   }
   
   protected String contextFactory = getProperty("security.ldap.contextFactory","com.sun.jndi.ldap.LdapCtxFactory");

   protected String connectionURL = getProperty("security.ldap.connection.url",null);
   
   protected String userPasswordAttr = getProperty("security.ldap.attr.userPassword", "userPassword");
   protected String userDigestPasswordAttr = getProperty("security.ldap.attr.userDigestPassword", "digestPassword");
   protected String uidAttr = getProperty("security.ldap.attr.uid", "uid");
   protected String uidNumberAttr = getProperty("security.ldap.attr.uidNumber", "uidNumber");
   protected String gidNumberAttr = getProperty("security.ldap.attr.gidNumber", "gidNumber");
   protected String groupNameAttr = getProperty("security.ldap.attr.groupName", "cn");
   protected String groupMemberName = getProperty("security.ldap.attr.groupMemberName", "uniqueMember");
   protected String groupClassName = getProperty("security.ldap.groupClass", "posixGroup");
   protected String userClassName = getProperty("security.ldap.userClass", "posixAccount");
   
   protected String userBase = getProperty("security.ldap.dn.user", null);
   protected String groupBase = getProperty("security.ldap.dn.group", null);
   
   protected DirContext context = null;
   
   
   /**
    * The message format used to form the distinguished name of a
    * user, with "{0}" marking the spot where the specified username
    * goes.
    */
   protected String userByNamePattern = null;
   protected String userByIdPattern = null;
   protected MessageFormat userByNamePatternFormat = null;
   protected MessageFormat userByIdPatternFormat = null;
   protected String groupByIdPattern = null;
   protected String groupByNamePattern = null;
   protected MessageFormat groupByIdPatternFormat = null;
   protected MessageFormat groupByNamePatternFormat = null;
   
   protected ExistPDP pdp = null;
   
   /** Creates a new instance of LDAP SecurityManager */
   public SecurityManagerImpl()
   {
      setUserByNamePattern(uidAttr+"={0},"+userBase);
      setUserByIdPattern(uidNumberAttr+"={0},"+userBase);
      setGroupByIdPattern(gidNumberAttr+"={0},"+groupBase);
      setGroupByNamePattern(groupNameAttr+"={0},"+groupBase);
   }

   /**
    * Set the message format pattern for selecting users in this Realm.
    * This may be one simple pattern, or multiple patterns to be tried,
    * separated by parentheses. (for example, either "cn={0}", or
    * "(cn={0})(cn={0},o=myorg)" Full LDAP search strings are also supported,
    * but only the "OR", "|" syntax, so "(|(cn={0})(cn={0},o=myorg))" is
    * also valid. Complex search strings with &, etc are NOT supported.
    *
    * @param pattern The new user pattern
    */
   public void setUserByNamePattern(String pattern)
   {
      
      this.userByNamePattern = pattern;
      this.userByNamePatternFormat = new MessageFormat(userByNamePattern);
   }
    
   public void setUserByIdPattern(String pattern)
   {
      
      this.userByIdPattern = pattern;
      this.userByIdPatternFormat = new MessageFormat(userByIdPattern);
   }
    
   public void setGroupByIdPattern(String pattern)
   {
      
      this.groupByIdPattern = pattern;
      this.groupByIdPatternFormat = new MessageFormat(groupByIdPattern);
   }
    
   public void setGroupByNamePattern(String pattern)
   {
      
      this.groupByNamePattern = pattern;
      this.groupByNamePatternFormat = new MessageFormat(groupByNamePattern);
   }
    
   /**
    * Return a String representing the value of the specified attribute.
    *
    * @param attrId Attribute name
    * @param attrs Attributes containing the required value
    *
    * @exception NamingException if a directory server error occurs
    */
   private String getAttributeValue(String attrId, Attributes attrs)
      throws NamingException 
   {
      
      if (attrId == null || attrs == null) {
         return null;
      }
      
      Attribute attr = attrs.get(attrId);
      if (attr == null) {
         return (null);
      }
      Object value = attr.get();
      if (value == null) {
         return (null);
      }
      String valueString = null;
      if (value instanceof byte[]) {
         valueString = new String((byte[]) value);
      } else {
         valueString = value.toString();
      }
      
      return valueString;
   }

   protected Hashtable<String, String> getDirectoryEnvironment() {

      if (connectionURL==null) {
         throw new IllegalStateException("The security.ldap.connection.url property is not set.");
      }
      if (userBase==null) {
         throw new IllegalStateException("The security.ldap.dn.user property is not set.");
      }
      if (groupBase==null) {
         throw new IllegalStateException("The security.ldap.dn.group property is not set.");
      }
      Hashtable<String, String> env = new Hashtable<String, String>();

      LOG.info("security.ldap.contextFactory="+contextFactory);
      env.put(Context.INITIAL_CONTEXT_FACTORY, contextFactory);
      LOG.info("security.ldap.connection.url="+connectionURL);
      env.put(Context.PROVIDER_URL, connectionURL);
      return env;

   }
   
   // TODO: need an exception to throw
   public void attach(BrokerPool pool, DBBroker sysBroker)
   {
      try {
         context = new InitialDirContext(getDirectoryEnvironment());
         Boolean enableXACML = (Boolean)sysBroker.getConfiguration().getProperty("xacml.enable");
         if (enableXACML != null && enableXACML.booleanValue()) {
            pdp = new ExistPDP(pool);
            LOG.debug("XACML enabled");
         }
      } catch (NamingException ex) {
         LOG.warn("Connecting to context failed for LDAP-based security: "+connectionURL,ex);
      }
   }

   protected AccountImpl getUserByName(DirContext context, String username)
      throws NamingException
   {
      // Form the dn from the user pattern
      String dn = userByNamePatternFormat.format(new String[] { username });

      LOG.info("Attempting to get user by: "+dn);

      return getUser(context,dn);
   }
      
   protected AccountImpl getUserById(DirContext context, int uid)
      throws NamingException
   {
      LOG.info("Searching for "+uidNumberAttr+"="+uid+" in "+userBase);
      SearchControls constraints = new SearchControls();

      constraints.setSearchScope(SearchControls.ONELEVEL_SCOPE);
      NamingEnumeration<SearchResult> users = context.search(userBase,"("+uidNumberAttr+"="+uid+")",constraints);
      while (users.hasMore()) {
         SearchResult result = users.next();
         return newUserFromAttributes(context, result.getAttributes());
      }
      return null;
   }
   
   protected GroupImpl getGroupById(DirContext context,int gid) 
      throws NamingException
   {
      LOG.info("Searching for "+gidNumberAttr+"="+gid+" in "+groupBase);
      SearchControls constraints = new SearchControls();
      constraints.setSearchScope(SearchControls.ONELEVEL_SCOPE);
      NamingEnumeration<SearchResult> groups = context.search(groupBase,"("+gidNumberAttr+"="+gid+")",constraints);
      while (groups.hasMore()) {
         SearchResult result = groups.next();
         String cn = getAttributeValue(groupNameAttr, result.getAttributes());
         LOG.info("Constructing group "+cn);
         try {
			return new GroupImpl(cn, gid);
		} catch (ConfigurationException e) {
			return null;
		}
      }
      return null;
   }
   
   protected GroupImpl getGroupByName(DirContext context,String name)
      throws NamingException
   {
      String g_dn = groupByNamePatternFormat.format(new String[] { name });
      
      LOG.info("Attempting to get group by: "+g_dn);

      try {
         Attributes attrs = context.getAttributes(g_dn);
         String cn = getAttributeValue(groupNameAttr, attrs);
         int gid = Integer.parseInt(getAttributeValue(gidNumberAttr, attrs));
         return new GroupImpl(cn, gid);
      } catch (NameNotFoundException e) {
      } catch (ConfigurationException e) {
	}
      return null;
   }
   
   protected AccountImpl newUserFromAttributes(DirContext context,Attributes attrs)
      throws NamingException
   {
      String username = getAttributeValue(uidAttr,attrs);
      String password = getAttributeValue(userPasswordAttr, attrs);
      String digestPassword = getAttributeValue(userDigestPasswordAttr, attrs);
      String gid = getAttributeValue(gidNumberAttr, attrs);
      //String g_dn = groupByIdPatternFormat.format(new String[] { gid });
      
      LOG.info("Searching for "+gidNumberAttr+"="+gid+" in "+groupBase);
      String mainGroup = null;
      SearchControls constraints = new SearchControls();

      constraints.setSearchScope(SearchControls.ONELEVEL_SCOPE);
      NamingEnumeration<SearchResult> groups = context.search(groupBase,"("+gidNumberAttr+"="+gid+")",constraints);
      while (mainGroup==null && groups.hasMore()) {
         SearchResult result = groups.next();
         mainGroup = getAttributeValue(groupNameAttr, result.getAttributes());
      }
      
      if (mainGroup==null || mainGroup.length()==0) {
         throw new IllegalStateException("Main group "+gid+" for user "+username+" is not able to be found in LDAP for group property "+gidNumberAttr);
      }

      int uid = Integer.parseInt(getAttributeValue(uidNumberAttr, attrs));
      LOG.info("Constructing user "+username+"/"+uid+" in group "+(mainGroup==null ? "<none>" : mainGroup));
      AccountImpl user;
	try {
		user = new AccountImpl(null, uid, username, null, mainGroup);
	} catch (ConfigurationException e) {
		throw new NamingException(e.getMessage());
	}
      if (password!=null) {
         if (password.charAt(0)=='{') {
            int end = password.indexOf('}');
            String type = password.substring(0,end+1);
            String value = password.substring(end+1);
            LOG.info("  digest: "+type+", "+value);
            if (!type.equals("{MD5}")) {
               throw new IllegalStateException("User "+username+" has a non-md5 digested password: "+type);
            }
            user.setEncodedPassword(value);
         } else {
            user.setPassword(password);
         }
      }
      if (digestPassword!=null) {
         user.setPasswordDigest(digestPassword);
      }
      
      LOG.info("Finding additional groups...");
      String fullName = uidAttr+"="+username+","+userBase;
      groups = context.search(groupBase,"("+groupMemberName+"="+fullName+")",constraints);
      while (groups.hasMore()) {
         SearchResult result = (SearchResult)groups.next();
         String name = getAttributeValue(groupNameAttr, result.getAttributes());
         if (name==null || name.length()==0) {
            throw new IllegalStateException("Group associated with "+username+" does not have a valid name for attribute "+groupNameAttr);
         }
         if (!name.equals(mainGroup)) {
            LOG.info("   ...adding: "+name);
            user.addGroup(name);
         }
      }
      return user;
   }
      
   protected AccountImpl getUser(DirContext context, String dn)
      throws NamingException
   {
      
      // Get required attributes from user entry
      Attributes attrs = null;
      try {
         attrs = context.getAttributes(dn);
      } catch (NameNotFoundException ex) {
         LOG.warn("Cannot find user "+dn,ex);
         return (null);
      }
      if (attrs == null) {
         return (null);
      }
      
      LOG.info("User "+dn+" found, attempting to find group and construct...");
      
      return newUserFromAttributes(context,attrs);

   }
   
   
   public void addGroup(String name) {
      
   }

   public void addGroup(Group group) {
      
   }

   public void deleteGroup(String name) throws PermissionDeniedException
   {
   }

   public void deleteAccount(String name) throws PermissionDeniedException
   {
   }

   public void deleteAccount(Account user) throws PermissionDeniedException
   {
   }

   public boolean updateAccount(Account account) throws PermissionDeniedException {
	   return false;
   }

   public int getCollectionDefaultPerms()
   {
      return Permission.DEFAULT_PERM;
   }

   public Group getGroup(int gid)
   {
      Integer igid = new Integer(gid);
      GroupImpl group = groupByIdCache.get(igid);
      if (group==null) {
         try {
            group = getGroupById(context,gid);
            if (group!=null) {
               groupByIdCache.put(igid,group);
            }
         } catch (NamingException ex) {
            LOG.warn("Cannot get group by #"+gid+" due to exception.",ex);
         }
      }
      return group;
   }

   public Group getGroup(String name)
   {
      GroupImpl group = groupByNameCache.get(name);
      if (group==null) {
         try {
            group = getGroupByName(context,name);
            if (group!=null) {
               groupByNameCache.put(name,group);
            }
         } catch (NamingException ex) {
            LOG.warn("Cannot get group "+name+" due to exception.",ex);
         }
      }
      return group;
   }

   // This needs to be an enumeration
   public java.util.Collection<Group> getGroups()
   {
      try {
         SearchControls constraints = new SearchControls();

         constraints.setSearchScope(SearchControls.ONELEVEL_SCOPE);
         NamingEnumeration<SearchResult> groups = context.search(groupBase,"(objectClass="+groupClassName+")",constraints);
         List<Group> groupList = new ArrayList<Group>();
         while (groups.hasMore()) {
            SearchResult result = groups.next();
            groupList.add(new GroupAider( getAttributeValue(groupNameAttr, result.getAttributes()) ) );
         }
         return groupList;
      } catch (NamingException ex) {
         LOG.warn("Cannot get a list of all groups due to exception.",ex);
      }
      return null;
         
   }

   public boolean isXACMLEnabled() {
      return pdp!=null;
   }
   
   public ExistPDP getPDP()
   {
      return pdp;
   }

   public int getResourceDefaultPerms()
   {
      return Permission.DEFAULT_PERM;
   }

   public Account getAccount(int uid)
   {
      Integer iuid = new Integer(uid);
      AccountImpl user = userByIdCache.get(iuid);
      if (user==null) {
         try {
            user = getUserById(context,uid);
            if (user!=null) {
               userByIdCache.put(iuid,user);
            }
         } catch (NamingException ex) {
            LOG.warn("Cannot get user by #"+uid+" due to exception.",ex);
         }
      }
      return user;
   }

   public AccountImpl getAccount(String name)
   {
      AccountImpl user = userByNameCache.get(name);
      if (user==null) {
         try {
            user = getUserByName(context,name);
            if (user!=null) {
               userByNameCache.put(name,user);
            }
         } catch (NamingException ex) {
            LOG.warn("Cannot get user "+name+" due to exception.",ex);
         }
      }
      return user;
   }

   // This needs to be an enumeration
   public java.util.Collection<Account> getUsers()
   {
      try {
         SearchControls constraints = new SearchControls();

         constraints.setSearchScope(SearchControls.ONELEVEL_SCOPE);
         NamingEnumeration<SearchResult> users = context.search(userBase,"(objectClass="+userClassName+")",constraints);
         List<Account> userList = new ArrayList<Account>();
         while (users.hasMore()) {
            SearchResult result = users.next();
            userList.add(newUserFromAttributes(context,result.getAttributes()));
         }
         return userList;
      } catch (NamingException ex) {
         LOG.warn("Cannot get the list of users due to exception.",ex);
      }
      return null;
   }

   // TODO: this shouldn't be in this interface
   public synchronized boolean hasAdminPrivileges(Account user) {
      return user.hasDbaRole();
   }
   
   // TODO: why is this here?
   public synchronized boolean hasAccount(String name) {
      try {
         return getUserByName(context,name)!=null;
      } catch (NamingException ex) {
         LOG.warn("Cannot check for user "+name+" due to exception",ex);
      }
      return false;
   }

   // TODO: why is this here?
   public synchronized boolean hasGroup(String name) {
      try {
         return getGroupByName(context,name)!=null;
      } catch (NamingException ex) {
         LOG.warn("Cannot check for group "+name+" due to exception",ex);
      }
      return false;
   }

   public boolean hasGroup(Group group) {
      return hasGroup(group.getName());
   }

   // TODO: this should be addUser
   public Account addAccount(Account user) {
	   return null;
   }

   @Override
   public Subject authenticate(String username, Object credentials) throws AuthenticationException {
		Account user = getAccount(username);
		if (user != null) {
			Subject newUser = new SubjectImpl((AccountImpl)user, credentials);

			if (newUser.isAuthenticated())
				return newUser;

			throw new AuthenticationException(
					AuthenticationException.WRONG_PASSWORD,
					"Wrong password for user [" + username + "] ");
		}
		throw new AuthenticationException(
				AuthenticationException.ACCOUNT_NOT_FOUND,
				"User [" + username + "] not found");
   }

	@Override
	public Subject getSystemSubject() {
		return null;
	}
	
	@Override
	public Subject getGuestSubject() {
		return null;
	}

	@Override
	public Group getDBAGroup() {
		return null;
	}

	@Override
	public BrokerPool getDatabase() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isConfigured() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Configuration getConfiguration() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Realm getRealm(String iD) {
		// TODO Auto-generated method stub
		return null;
	}
}
