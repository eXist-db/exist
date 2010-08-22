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
package org.exist.security.internal;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Account;
import org.exist.security.realm.Realm;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public abstract class AbstractRealm implements Realm, Configurable {

	protected final Map<String, Group> groupsByName = new HashMap<String, Group>(65);
	protected final Map<String, Account> usersByName = new HashMap<String, Account>(65);

	protected SecurityManagerImpl sm;
	
	protected Configuration configuration;
	
	protected Collection collectionRealm = null;
	public Collection collectionAccounts = null;
	protected Collection collectionGroups = null;
	
	public AbstractRealm(SecurityManagerImpl sm, Configuration config) {
		this.sm = sm;
		
		configuration = Configurator.configure(this, config);
	}
	
	public void save() throws PermissionDeniedException, EXistException, IOException {
		configuration.save();
	}

	//Accounts management methods
	protected final synchronized Account registerAccount(Account account)  {
		if (usersByName.containsKey(account.getName()))
			throw new IllegalArgumentException("User "+account.getName()+" exist.");
		
		usersByName.put(account.getName(), account);
		
		return account;
	}

	@Override
	public final synchronized Account getAccount(String name) {
		return usersByName.get(name);
	}

	@Override
	public final synchronized boolean hasAccount(String accountName) {
		return usersByName.containsKey(accountName);
	}

	@Override
	public final synchronized boolean hasAccount(Account account) {
		return usersByName.containsKey(account.getName());
	}

	@Override
	public final synchronized java.util.Collection<Account> getAccounts() {
		return usersByName.values();
	}

	//Groups management methods
	public final synchronized boolean hasGroup(String name) {
		return groupsByName.containsKey(name);
	}

	public final synchronized boolean hasGroup(Group role) {
		return groupsByName.containsKey(role.getName());
	}

	public final synchronized Group getGroup(String name) {
		return groupsByName.get(name);
	}
	
	public final synchronized java.util.Collection<Group> getRoles() {
		return groupsByName.values();
	}

	//configuration methods
	@Override
	public boolean isConfigured() {
		return (configuration != null);
	}

	@Override
	public Configuration getConfiguration() {
		return configuration;
	}
	
	//collections related methods
	protected Collection getCollection() {
		return collectionRealm;
	}
}
