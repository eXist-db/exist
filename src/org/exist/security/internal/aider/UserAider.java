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
package org.exist.security.internal.aider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.exist.security.Group;
import org.exist.security.Realm;
import org.exist.security.User;
import org.exist.xmldb.XmldbURI;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class UserAider implements User {

	private String name;
	private int id = -1;
	
	private Group defaultRole = null;
	private Set<Group> roles = new HashSet<Group>();
	
	public UserAider(String name) {
		this.name = name;
	}
	
	public UserAider(int id, String name) {
		this(name);
		this.id = id;
	}

	public UserAider(String name, Group group) {
		this(name);
		defaultRole = addGroup(group);
	}

	/* (non-Javadoc)
	 * @see java.security.Principal#getName()
	 */
	@Override
	public String getName() {
		return name;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#addGroup(java.lang.String)
	 */
	@Override
	public Group addGroup(String name) {
		Group role = new GroupAider(name);
		
		roles.add(role);
		
		return role;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#addGroup(org.exist.security.Group)
	 */
	@Override
	public Group addGroup(Group role) {
		return addGroup(role.getName());
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#remGroup(java.lang.String)
	 */
	@Override
	public void remGroup(String group) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#setGroups(java.lang.String[])
	 */
	@Override
	public void setGroups(String[] names) {
		roles = new HashSet<Group>();
		
		for (int i = 0; i < names.length; i++) {
			addGroup(names[i]);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#getGroups()
	 */
	@Override
	public String[] getGroups() {
		int i = 0;
		String[] names = new String[roles.size()];
		for (Group role : roles) {
			names[i++] = role.getName();
		}
		return names;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#hasDbaRole()
	 */
	@Override
	public boolean hasDbaRole() {
		return false;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#getUID()
	 */
	@Override
	public int getUID() {
		return id;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#getPrimaryGroup()
	 */
	@Override
	public String getPrimaryGroup() {
		if (defaultRole == null)
			return null;

		return defaultRole.getName();
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#hasGroup(java.lang.String)
	 */
	@Override
	public boolean hasGroup(String group) {
		// TODO Auto-generated method stub
		return false;
	}

	private XmldbURI homeCollection = null;

	/* (non-Javadoc)
	 * @see org.exist.security.User#setHome(org.exist.xmldb.XmldbURI)
	 */
	@Override
	public void setHome(XmldbURI homeCollection) {
		this.homeCollection = homeCollection;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#getHome()
	 */
	@Override
	public XmldbURI getHome() {
		return homeCollection;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#authenticate(java.lang.Object)
	 */
	@Override
	public boolean authenticate(Object credentials) {
		return false;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#isAuthenticated()
	 */
	@Override
	public boolean isAuthenticated() {
		return false;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#getRealm()
	 */
	@Override
	public Realm getRealm() {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#setUID(int)
	 */
	@Override
	public void setUID(int uid) {
		// TODO Auto-generated method stub

	}
	
	private Map<String, Object> attributes = new HashMap<String, Object>();

	/* (non-Javadoc)
	 * @see org.exist.security.User#setAttribute(java.lang.String, java.lang.Object)
	 */
	@Override
	public void setAttribute(String name, Object value) {
		attributes.put(name, value);

	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#getAttribute(java.lang.String)
	 */
	@Override
	public Object getAttribute(String name) {
		return attributes.get(name);
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#getAttributeNames()
	 */
	@Override
	public Set<String> getAttributeNames() {
		return attributes.keySet();
	}

	@Override
	public Group getDefaultGroup() {
		return defaultRole;
	}

	private String password = null;
	
	public void setEncodedPassword(String passwd) {
		password = passwd;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#setPassword(java.lang.String)
	 */
	@Override
	public void setPassword(String passwd) {
		password = passwd;

	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#getPassword()
	 */
	@Override
	public String getPassword() {
		return password;
	}

	private String passwordDigest = null;
	
	public void setPasswordDigest(String password) {
		passwordDigest = password;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#getDigestPassword()
	 */
	@Override
	public String getDigestPassword() {
		return passwordDigest;
	}

	
}
