/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
package org.exist.security.openid;

import java.util.Collection;

import org.exist.EXistException;
import org.exist.config.ConfigurationException;
import org.exist.security.Account;
import org.exist.security.AuthenticationException;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.security.realm.Realm;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ReamlImpl implements Realm {

	/* (non-Javadoc)
	 * @see org.exist.security.realm.AuthenticatingRealm#authenticate(java.lang.String, java.lang.Object)
	 */
	@Override
	public Subject authenticate(String accountName, Object credentials) throws AuthenticationException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.management.AccountsManagement#addAccount(org.exist.security.User)
	 */
	@Override
	public Account addAccount(Account account) throws PermissionDeniedException, EXistException, ConfigurationException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.management.AccountsManagement#getAccount(java.lang.String)
	 */
	@Override
	public Account getAccount(Subject invokingUser, String name) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.management.AccountsManagement#hasAccount(org.exist.security.User)
	 */
	@Override
	public boolean hasAccount(Account account) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.management.AccountsManagement#hasAccount(java.lang.String)
	 */
	@Override
	public boolean hasAccount(String name) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.management.AccountsManagement#updateAccount(org.exist.security.User)
	 */
	@Override
	public boolean updateAccount(Subject invokingUser, Account account) throws PermissionDeniedException, EXistException, ConfigurationException {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.management.AccountsManagement#deleteAccount(org.exist.security.User)
	 */
	@Override
	public boolean deleteAccount(Subject invokingUser, Account account) throws PermissionDeniedException, EXistException, ConfigurationException {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.management.GroupsManagement#addGroup(org.exist.security.Group)
	 */
	@Override
	public Group addGroup(Group role) throws PermissionDeniedException, EXistException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.management.GroupsManagement#getGroup(java.lang.String)
	 */
	@Override
	public Group getGroup(Subject invokingUser, String name) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.management.GroupsManagement#hasGroup(org.exist.security.Group)
	 */
	@Override
	public boolean hasGroup(Group role) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.management.GroupsManagement#hasGroup(java.lang.String)
	 */
	@Override
	public boolean hasGroup(String name) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.management.GroupsManagement#updateGroup(org.exist.security.Group)
	 */
	@Override
	public boolean updateGroup(Group role) throws PermissionDeniedException, EXistException {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.management.GroupsManagement#deleteGroup(org.exist.security.Group)
	 */
	@Override
	public boolean deleteGroup(Group role) throws PermissionDeniedException, EXistException {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.realm.Realm#getId()
	 */
	@Override
	public String getId() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.realm.Realm#getAccounts()
	 */
	@Override
	public Collection<Account> getAccounts() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.realm.Realm#getRoles()
	 */
	@Override
	public Collection<Group> getRoles() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.realm.Realm#startUp(org.exist.storage.DBBroker)
	 */
	@Override
	public void startUp(DBBroker broker) throws EXistException {
		// TODO Auto-generated method stub

	}

	@Override
	public BrokerPool getDatabase() {
		// TODO Auto-generated method stub
		return null;
	}

}
