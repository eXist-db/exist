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
import org.exist.dom.DocumentImpl;
import org.exist.security.realm.Realm;
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

   public final static String ACL_FILE = "users.xml";
   public final static XmldbURI ACL_FILE_URI = XmldbURI.create(ACL_FILE);
   
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

   <A extends Account> A addAccount(Account user) throws PermissionDeniedException, EXistException, ConfigurationException;

   void deleteAccount(Subject invokingUser, String name) throws PermissionDeniedException, EXistException, ConfigurationException;
   <A extends Account> void deleteAccount(Subject invokingUser, A user) throws PermissionDeniedException, EXistException, ConfigurationException;

   <A extends Account> boolean updateAccount(Subject invokingUser, A account) throws PermissionDeniedException, EXistException, ConfigurationException;

   <G extends Group> boolean updateGroup(Subject invokingUser, G group) throws PermissionDeniedException, EXistException, ConfigurationException;

   Account getAccount(Subject invokingUser, String name);

   <G extends Group> G addGroup(Group group) throws PermissionDeniedException, EXistException, ConfigurationException;
   
   @Deprecated
   void addGroup(String group) throws PermissionDeniedException, EXistException, ConfigurationException;

   boolean hasGroup(String name);
   boolean hasGroup(Group group);

   Group getGroup(Subject invokingUser, String name);
   Group getGroup(int gid);

   void deleteGroup(Subject invokingUser, String name) throws PermissionDeniedException, EXistException;

   boolean hasAdminPrivileges(Account user);

   public Subject authenticate(String username, Object credentials) throws AuthenticationException;

   public Subject getSystemSubject();
   public Subject getGuestSubject();
   public Group getDBAGroup();

   public List<Account> getGroupMembers(String groupName);

   @Deprecated //use realm's method
   <A extends Account> java.util.Collection<A> getUsers();

   @Deprecated //use realm's method
   <G extends Group> java.util.Collection<G> getGroups();

   Realm getRealm(String iD);

   //session manager part
   String registerSession(Subject subject);
   
   Subject getSubjectBySessionId(String sessionid);

   void addGroup(int id, Group group);

   void addUser(int id, Account account);

   boolean hasGroup(int id);

   boolean hasUser(int id);

   public int getNextGroupId();

   public int getNextAccountId();

   /**
    * Find users by their personal name
    */
   public List<String> findUsernamesWhereNameStarts(Subject invokingUser, String startsWith);

   /**
    * Find users by their username
    */
   public List<String> findUsernamesWhereUsernameStarts(Subject invokingUser, String startsWith);

   /**
    * Find all groups visible to the invokingUser
    */
   public List<String> findAllGroupNames(Subject invokingUser);

   /**
    * Find groups by their group name
    */
   public List<String> findGroupnamesWhereGroupnameStarts(Subject invokingUser, String startsWith);
   
   /**
    * Find all members of a group
    */
   public List<String> findAllGroupMembers(Subject invokingUser, String groupName);

   /**
    * Process document, possible new sub-instance.
    *  
    * @param document
    * @throws ConfigurationException 
    */
   void processPramatter(DBBroker broker, DocumentImpl document) throws ConfigurationException;
   
   /**
    * Particular web page for authentication.
    * 
    * @return Authentication form location
    */
   public String getAuthenticationEntryPoint();
}
