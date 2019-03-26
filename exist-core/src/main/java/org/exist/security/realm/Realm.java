/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2011 The eXist Project
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
package org.exist.security.realm;

import java.util.Collection;
import java.util.List;
import org.exist.Database;
import org.exist.LifeCycle;
import org.exist.security.Account;
import org.exist.security.Group;
import org.exist.security.SecurityManager;
import org.exist.security.management.AccountsManagement;
import org.exist.security.management.GroupsManagement;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public interface Realm extends AuthenticatingRealm, AuthorizingRealm, AccountsManagement, GroupsManagement, LifeCycle {
	
    String getId();

    Collection<Account> getAccounts();

    Collection<Group> getGroups();

    Database getDatabase();

    Group getExternalGroup(final String name);

    List<String> findUsernamesWhereNameStarts(String startsWith);
    List<String> findUsernamesWhereNamePartStarts(String startsWith);
    List<String> findUsernamesWhereUsernameStarts(String startsWith);
    List<String> findAllGroupNames();
    List<String> findAllGroupMembers(final String groupName);
    List<String> findAllUserNames();

    SecurityManager getSecurityManager();

    Collection<? extends String> findGroupnamesWhereGroupnameStarts(String startsWith);
    Collection<? extends String> findGroupnamesWhereGroupnameContains(String fragment);
}
