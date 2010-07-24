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
package org.exist.security.ldap;

import java.util.Collection;

import org.exist.EXistException;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.security.realm.Realm;
import org.exist.storage.DBBroker;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class LDAPRealm implements Realm {

	@Override
	public String getId() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public User getAccount(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<User> getAccounts() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean hasAccount(String accountName) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean updateAccount(User account) throws PermissionDeniedException, EXistException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Collection<Group> getRoles() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Group getRole(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean hasRole(String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public User getAccount(int id) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Group getRole(int id) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void startUp(DBBroker broker) throws EXistException {
		// TODO Auto-generated method stub
		
	}

}
