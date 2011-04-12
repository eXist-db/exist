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
import org.exist.EXistException;
import org.exist.config.Startable;
import org.exist.security.Group;
import org.exist.security.Account;
import org.exist.security.Subject;
import org.exist.security.SecurityManager;
import org.exist.security.management.AccountsManagement;
import org.exist.security.management.GroupsManagement;
import org.exist.storage.DBBroker;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public interface Realm extends AuthenticatingRealm, AuthorizingRealm, 
								AccountsManagement, GroupsManagement, Startable {
	
	public String getId();
	
	public Collection<Account> getAccounts();
	
	public Collection<Group> getGroups();
	@Deprecated //use getGroups (remove after 1.6)
	public Collection<Group> getRoles();

	public void startUp(DBBroker broker) throws EXistException;

	public Database getDatabase();

    public Group getExternalGroup(Subject invokingUser, String name);

    public List<String> findUsernamesWhereNameStarts(Subject invokingUser, String startsWith);
    public List<String> findUsernamesWhereUsernameStarts(Subject invokingUser, String startsWith);
    public List<String> findAllGroupNames(Subject invokingUser);
    public List<String> findAllGroupMembers(Subject invokingUser, String groupName);

    public SecurityManager getSecurityManager();


}
