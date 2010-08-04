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
package org.exist.security;

import org.exist.EXistException;
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
 * Users and groups are stored in the system collection, in document
 * users.xml. While it is possible to edit this file by hand, it
 * may lead to unexpected results, since SecurityManager reads 
 * users.xml only during database startup and shutdown.
 */
public interface SecurityManager {

   public final static String ACL_FILE = "users.xml";
   public final static XmldbURI ACL_FILE_URI = XmldbURI.create(ACL_FILE);
   
   public final static String DBA_GROUP = "dba";
   public final static String DBA_USER = "admin";
   public final static String GUEST_GROUP = "guest";
   public final static String GUEST_USER = "guest";

   void attach(BrokerPool pool, DBBroker sysBroker) throws EXistException;
   
   public BrokerPool getDatabase();

   boolean isXACMLEnabled();
   
   ExistPDP getPDP();

   User getUser(int uid);

   boolean hasUser(String name);

   // TODO: this should be addUser
   void setUser(User user) throws PermissionDeniedException, EXistException;

   void deleteUser(String name) throws PermissionDeniedException, EXistException;
   void deleteUser(User user) throws PermissionDeniedException, EXistException;

   boolean updateAccount(User account) throws PermissionDeniedException, EXistException;

   User getUser(String name);

   void addGroup(Group group) throws PermissionDeniedException, EXistException;
   @Deprecated
   void addGroup(String group) throws PermissionDeniedException, EXistException;

   boolean hasGroup(String name);
   boolean hasGroup(Group group);

   Group getGroup(String name);
   Group getGroup(int gid);

   void deleteRole(String name) throws PermissionDeniedException, EXistException;

   boolean hasAdminPrivileges(User user);

   int getResourceDefaultPerms();
   int getCollectionDefaultPerms();
	
   public User authenticate(String username, Object credentials) throws AuthenticationException;

   public User getSystemAccount();
   public User getGuestAccount();
   public Group getDBAGroup();

   @Deprecated
   java.util.Collection<User> getUsers();

   @Deprecated
   java.util.Collection<Group> getGroups();

}
