/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001 Wolfgang M. Meier
 *  meier@ifs.tu-darmstadt.de
 *  http://exist.sourceforge.net
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

   public final static User SYSTEM_USER = new User(DBA_USER, null, DBA_GROUP);
   
   void attach(BrokerPool pool, DBBroker sysBroker);

   boolean isXACMLEnabled();
   
   ExistPDP getPDP();

   void deleteUser(String name) 
     throws PermissionDeniedException;

   void deleteUser(User user) 
     throws PermissionDeniedException;

   User getUser(String name);

   User getUser(int uid);

   User[] getUsers();

   void addGroup(String name);

   boolean hasGroup(String name);

   Group getGroup(String name);

   Group getGroup(int gid);

   String[] getGroups();

   boolean hasAdminPrivileges(User user);

   boolean hasUser(String name);

   void setUser(User user);

   int getResourceDefaultPerms();

   int getCollectionDefaultPerms();
	
}
