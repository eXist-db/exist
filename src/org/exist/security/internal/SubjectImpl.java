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

import java.util.Set;

import org.exist.config.Configuration;
import org.exist.security.Group;
import org.exist.security.Subject;
import org.exist.security.realm.Realm;
import org.exist.xmldb.XmldbURI;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public final class SubjectImpl implements Subject {

	protected final AbstractAccount account;
	
	public SubjectImpl(AbstractAccount account, Object credentials) {
		this.account = account;
		
		authenticate(credentials);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.security.User#addGroup(java.lang.String)
	 */
	@Override
	public Group addGroup(String name) {
		return account.addGroup(name);
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#addGroup(org.exist.security.Group)
	 */
	@Override
	public Group addGroup(Group group) {
		return account.addGroup(group);
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#remGroup(java.lang.String)
	 */
	@Override
	public void remGroup(String group) {
		account.remGroup(group);
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#getGroups()
	 */
	@Override
	public String[] getGroups() {
		return account.getGroups();
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#hasDbaRole()
	 */
	@Override
	public boolean hasDbaRole() {
		return account.hasDbaRole();
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#getPrimaryGroup()
	 */
	@Override
	public String getPrimaryGroup() {
		return account.getPrimaryGroup();
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#getDefaultGroup()
	 */
	@Override
	public Group getDefaultGroup() {
		return account.getDefaultGroup();
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#hasGroup(java.lang.String)
	 */
	@Override
	public boolean hasGroup(String group) {
		return account.hasGroup(group);
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#setPassword(java.lang.String)
	 */
	@Override
	public void setPassword(String passwd) {
		account.setPassword(passwd);
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#setHome(org.exist.xmldb.XmldbURI)
	 */
	@Override
	public void setHome(XmldbURI homeCollection) {
		account.setHome(homeCollection);
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#getHome()
	 */
	@Override
	public XmldbURI getHome() {
		return account.getHome();
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#getRealm()
	 */
	@Override
	public Realm getRealm() {
		return account.getRealm();
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#getPassword()
	 */
	@Override
	public String getPassword() {
		return account.getPassword();
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#getDigestPassword()
	 */
	@Override
	public String getDigestPassword() {
		return account.getDigestPassword();
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#setGroups(java.lang.String[])
	 */
	@Override
	public void setGroups(String[] groups) {
		account.setGroups(groups);
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#setAttribute(java.lang.String, java.lang.Object)
	 */
	@Override
	public void setAttribute(String name, Object value) {
		account.setAttribute(name, value);
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#getAttribute(java.lang.String)
	 */
	@Override
	public Object getAttribute(String name) {
		return account.getAttribute(name);
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#getAttributeNames()
	 */
	@Override
	public Set<String> getAttributeNames() {
		return account.getAttributeNames();
	}

	/* (non-Javadoc)
	 * @see org.exist.security.Principal#getId()
	 */
	@Override
	public int getId() {
		return account.getId();
	}

	/* (non-Javadoc)
	 * @see java.security.Principal#getName()
	 */
	@Override
	public String getName() {
		return account.getName();
	}

	/* (non-Javadoc)
	 * @see org.exist.config.Configurable#isConfigured()
	 */
	@Override
	public boolean isConfigured() {
		return account.isConfigured();
	}

	/* (non-Javadoc)
	 * @see org.exist.config.Configurable#getConfiguration()
	 */
	@Override
	public Configuration getConfiguration() {
		return account.getConfiguration();
	}

	private boolean authenticated = false;
	
	/* (non-Javadoc)
	 * @see org.exist.security.Subject#authenticate(java.lang.Object)
	 */
	@Override
	public boolean authenticate(Object credentials) {
    	authenticated = account._cred!=null && account._cred.check(credentials);
		return authenticated;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.Subject#isAuthenticated()
	 */
	@Override
	public boolean isAuthenticated() {
		return authenticated;
	}

	@Override
	public String getUsername() {
		return account.getUsername();
	}

	@Override
	public boolean isAccountNonExpired() {
		return account.isAccountNonExpired();
	}

	@Override
	public boolean isAccountNonLocked() {
		return account.isAccountNonLocked();
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return account.isCredentialsNonExpired();
	}

	@Override
	public boolean isEnabled() {
		return account.isEnabled();
	}

	@Override
	public boolean equals(Object obj) {
		return account.equals(obj);
	}
}
