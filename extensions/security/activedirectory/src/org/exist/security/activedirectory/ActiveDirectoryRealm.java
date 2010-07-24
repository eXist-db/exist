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
package org.exist.security.activedirectory;

import java.util.Collection;

import org.exist.EXistException;
import org.exist.security.AuthenticationException;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.security.realm.Realm;
import org.exist.storage.DBBroker;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ActiveDirectoryRealm implements Realm {

	/* (non-Javadoc)
	 * @see org.exist.security.Realm#getId()
	 */
	@Override
	public String getId() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.Realm#getAccount(java.lang.String)
	 */
	@Override
	public User getAccount(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.Realm#getAccounts()
	 */
	@Override
	public Collection<User> getAccounts() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.Realm#hasAccount(java.lang.String)
	 */
	@Override
	public boolean hasAccount(String accountName) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.Realm#updateAccount(org.exist.security.User)
	 */
	@Override
	public boolean updateAccount(User account) throws PermissionDeniedException, EXistException {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.Realm#getRoles()
	 */
	@Override
	public Collection<Group> getRoles() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.Realm#getRole(java.lang.String)
	 */
	@Override
	public Group getRole(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.Realm#hasRole(java.lang.String)
	 */
	@Override
	public boolean hasRole(String name) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.Realm#authenticate(java.lang.String, java.lang.Object)
	 */
	public User authenticate(String username, Object credentials) throws AuthenticationException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.Realm#getAccount(int)
	 */
	@Override
	public User getAccount(int id) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.Realm#getRole(int)
	 */
	@Override
	public Group getRole(int id) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.Realm#startUp(org.exist.storage.DBBroker)
	 */
	@Override
	public void startUp(DBBroker broker) throws EXistException {
		// TODO Auto-generated method stub

	}

}
