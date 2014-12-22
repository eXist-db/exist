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
package org.exist.security;

import java.util.List;
import org.exist.Database;
import org.exist.EXistException;
import org.exist.config.Configurable;
import org.exist.config.ConfigurationException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.xacml.ExistPDP;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.xmldb.XmldbURI;

/**
 * SecurityManager is responsible for managing users and groups.
 * 
 * There's only one SecurityManager for each database instance, which
 * may be obtained by {@link BrokerPool#getSecurityManager()}.
 * 
 */
public interface SecurityManager extends Configurable {

   public final static XmldbURI SECURITY_COLLECTION_URI = XmldbURI.SYSTEM_COLLECTION_URI.append("security");
   public final static XmldbURI CONFIG_FILE_URI = XmldbURI.create("config.xml");
   
   public final static XmldbURI ACCOUNTS_COLLECTION_URI = XmldbURI.create("accounts");
   public final static XmldbURI GROUPS_COLLECTION_URI = XmldbURI.create("groups");
   public final static XmldbURI REMOVED_COLLECTION_URI = XmldbURI.create("removed");

   public final static String SYSTEM = "SYSTEM";
   public final static String DBA_GROUP = "dba";
   public final static String DBA_USER = "admin";
   public final static String GUEST_GROUP = "guest";
   public final static String GUEST_USER = "guest";

   void attach(BrokerPool pool, DBBroker sysBroker) throws EXistException;
   
   public Database getDatabase();

   boolean isXACMLEnabled();
   
   ExistPDP getPDP();

   Account getAccount(int id);

   boolean hasAccount(String name);

   Account addAccount(Account user) throws PermissionDeniedException, EXistException, ConfigurationException;
   
   Account addAccount(DBBroker broker, Account account) throws  PermissionDeniedException, EXistException, ConfigurationException;

   boolean deleteAccount(String name) throws PermissionDeniedException, EXistException, ConfigurationException;
   boolean deleteAccount(Account account) throws PermissionDeniedException, EXistException, ConfigurationException;

   boolean updateAccount(Account account) throws PermissionDeniedException, EXistException, ConfigurationException;

   boolean updateGroup(Group group) throws PermissionDeniedException, EXistException, ConfigurationException;

   Account getAccount(String name);

   Group addGroup(Group group) throws PermissionDeniedException, EXistException, ConfigurationException;
   
   @Deprecated
   void addGroup(String group) throws PermissionDeniedException, EXistException, ConfigurationException;

   boolean hasGroup(String name);
   boolean hasGroup(Group group);

   Group getGroup(String name);
   Group getGroup(int gid);

   boolean deleteGroup(String name) throws PermissionDeniedException, EXistException;

   boolean hasAdminPrivileges(Account user);

   public Subject authenticate(String username, Object credentials) throws AuthenticationException;

   public Subject getSystemSubject();
   public Subject getGuestSubject();
   public Group getDBAGroup();

   public List<Account> getGroupMembers(String groupName);

   @Deprecated //use realm's method
   java.util.Collection<Account> getUsers();

   @Deprecated //use realm's method
   java.util.Collection<Group> getGroups();

   //session manager part
   void registerSession(Session session);
   
   Subject getSubjectBySessionId(String sessionid);

   void addGroup(int id, Group group);

   void addUser(int id, Account account);

   boolean hasGroup(int id);

   boolean hasUser(int id);

   /**
    * Find users by their personal name
    */
   public List<String> findUsernamesWhereNameStarts(String startsWith);

   /**
    * Find users by their username
    */
   public List<String> findUsernamesWhereUsernameStarts(String startsWith);

   /**
    * Find all groups visible to the invokingUser
    */
   public List<String> findAllGroupNames();
   
   /**
    * Find all users visible to the invokingUser
    */
   public List<String> findAllUserNames();

   /**
    * Find groups by their group name
    */
   public List<String> findGroupnamesWhereGroupnameStarts(String startsWith);
   
   /**
    * Find all members of a group
    */
   public List<String> findAllGroupMembers(String groupName);

   /**
    * Process document, possible new sub-instance.
    *  
    * @param document
    * @throws ConfigurationException 
    */
   void processPramatter(DBBroker broker, DocumentImpl document) throws ConfigurationException;
   void processPramatterBeforeSave(DBBroker broker, DocumentImpl document) throws ConfigurationException;
   
   /**
    * Particular web page for authentication.
    * 
    * @return Authentication form location
    */
   public String getAuthenticationEntryPoint();

   public List<String> findGroupnamesWhereGroupnameContains(String fragment);

   public List<String> findUsernamesWhereNamePartStarts(String startsWith);

   Subject getCurrentSubject();

   /**
    * A receiver that is given the id of
    * a security principal
    */
   public interface PrincipalIdReceiver {

      /**
       * Callback function which received a Principal id
       *
       * @param id The id of the principal
       */
      public void allocate(final int id);
   }

   /**
    * Pre-allocates a new account id
    *
    * @param receiver A receiver that will receive the new account id
    */
   public void preAllocateAccountId(PrincipalIdReceiver receiver) throws PermissionDeniedException, EXistException;

   /**
    * Pre-allocates a new group id
    *
    * @param receiver A receiver that will receive the new group id
    */
   public void preAllocateGroupId(PrincipalIdReceiver receiver) throws PermissionDeniedException, EXistException;
}
