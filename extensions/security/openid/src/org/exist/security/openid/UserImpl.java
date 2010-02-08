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

import java.util.HashMap;
import java.util.Map;

import org.exist.security.Realm;
import org.exist.security.User;
import org.exist.xmldb.XmldbURI;
import org.openid4java.discovery.Identifier;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class UserImpl implements User {

	Identifier  _identifier = null;
	
	public UserImpl(Identifier identifier) {
		_identifier = identifier;
	}

	@Override
	public void addGroup(String group) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void remGroup(String group) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setGroups(String[] groups) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String[] getGroups() {
		return new String[0];
	}

	@Override
	public boolean hasDbaRole() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int getUID() {
		return -1;
	}

	@Override
	public String getPrimaryGroup() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean hasGroup(String group) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setPassword(String passwd) {
	}

	@Override
	public void setHome(XmldbURI homeCollection) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public XmldbURI getHome() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean authenticate(Object credentials) {
		return false;
	}

	@Override
	public boolean isAuthenticated() {
		return (_identifier != null);
	}

	@Override
	public Realm getRealm() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setUID(int uid) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String getPassword() {
		return null;
	}

	@Override
	public String getDigestPassword() {
		return null;
	}

	@Override
	public String getName() {
		return _identifier.getIdentifier();
	}

	private Map<String, Object> attributes = new HashMap<String, Object>();
	
	@Override
	public void setAttribute(String name, Object value) {
		attributes.put(name, value);
	}

	@Override
	public Object getAttribute(String name) {
		return attributes.get(name);
	}
}
