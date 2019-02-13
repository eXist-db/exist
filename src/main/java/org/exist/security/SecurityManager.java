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
package org.exist.security;

import java.util.List;
import org.exist.Database;
import org.exist.EXistException;
import org.exist.config.Configurable;
import org.exist.config.ConfigurationException;
import org.exist.dom.persistent.DocumentImpl;
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

   XmldbURI SECURITY_COLLECTION_URI = XmldbURI.SYSTEM_COLLECTION_URI.append("security");
   XmldbURI CONFIG_FILE_URI = XmldbURI.create("config.xml");
   
   XmldbURI ACCOUNTS_COLLECTION_URI = XmldbURI.create("accounts");
   XmldbURI GROUPS_COLLECTION_URI = XmldbURI.create("groups");
   XmldbURI REMOVED_COLLECTION_URI = XmldbURI.create("removed");

   String SYSTEM = "SYSTEM";
   String DBA_GROUP = "dba";
   String DBA_USER = "admin";
   String GUEST_GROUP = "guest";
   String GUEST_USER = "guest";

   void attach(DBBroker broker) throws EXistException;
   
   Database getDatabase();
   Database database();

   Account getAccount(int id);

   boolean hasAccount(String name);

   Account addAccount(Account user) throws PermissionDeniedException, EXistException;
   
   Account addAccount(DBBroker broker, Account account) throws  PermissionDeniedException, EXistException;

   boolean deleteAccount(String name) throws PermissionDeniedException, EXistException;
   boolean deleteAccount(Account account) throws PermissionDeniedException, EXistException;

   boolean updateAccount(Account account) throws PermissionDeniedException, EXistException;

   boolean updateGroup(Group group) throws PermissionDeniedException, EXistException;

   Account getAccount(String name);

   Group addGroup(DBBroker broker, Group group) throws PermissionDeniedException, EXistException;
   
   @Deprecated
   void addGroup(DBBroker broker, String group) throws PermissionDeniedException, EXistException;

   boolean hasGroup(String name);
   boolean hasGroup(Group group);

   Group getGroup(String name);
   Group getGroup(int gid);

   boolean deleteGroup(String name) throws PermissionDeniedException, EXistException;

   boolean hasAdminPrivileges(Account user);

   Subject authenticate(String username, Object credentials) throws AuthenticationException;

   Subject getSystemSubject();
   Subject getGuestSubject();
   Group getDBAGroup();

   List<Account> getGroupMembers(String groupName);

   @Deprecated //use realm's method
   java.util.Collection<Account> getUsers();

   @Deprecated //use realm's method
   java.util.Collection<Group> getGroups();

   //session manager part
   void registerSession(Session session);
   
   @Deprecated
   Subject getSubjectBySessionId(String sessionid);

   void addGroup(int id, Group group);

   void addUser(int id, Account account);

   boolean hasGroup(int id);

   boolean hasUser(int id);

   /**
    * Find users by their personal name
    */
   List<String> findUsernamesWhereNameStarts(String startsWith);

   /**
    * Find users by their username
    */
   List<String> findUsernamesWhereUsernameStarts(String startsWith);

   /**
    * Find all groups visible to the invokingUser
    */
   List<String> findAllGroupNames();
   
   /**
    * Find all users visible to the invokingUser
    */
   List<String> findAllUserNames();

   /**
    * Find groups by their group name
    */
   List<String> findGroupnamesWhereGroupnameStarts(String startsWith);
   
   /**
    * Find all members of a group
    */
   List<String> findAllGroupMembers(String groupName);

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
   String getAuthenticationEntryPoint();

   List<String> findGroupnamesWhereGroupnameContains(String fragment);

   List<String> findUsernamesWhereNamePartStarts(String startsWith);

   @Deprecated
   Subject getCurrentSubject();

   /**
    * A receiver that is given the id of
    * a security principal
    */
   interface PrincipalIdReceiver {

      /**
       * Callback function which received a Principal id
       *
       * @param id The id of the principal
       */
      void allocate(final int id);
   }

   /**
    * Pre-allocates a new account id
    *
    * @param receiver A receiver that will receive the new account id
    */
   void preAllocateAccountId(PrincipalIdReceiver receiver) throws PermissionDeniedException, EXistException;

   /**
    * Pre-allocates a new group id
    *
    * @param receiver A receiver that will receive the new group id
    */
   void preAllocateGroupId(PrincipalIdReceiver receiver) throws PermissionDeniedException, EXistException;
}
