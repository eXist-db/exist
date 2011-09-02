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
package org.exist.security.internal.aider;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.exist.config.Configuration;
import org.exist.security.AXSchemaType;
import org.exist.security.Group;
import org.exist.security.Account;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SchemaType;
import org.exist.security.internal.RealmImpl;
import org.exist.security.realm.Realm;
import org.exist.storage.DBBroker;
import org.exist.xmldb.XmldbURI;

/**
 * Account details.
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class UserAider implements Account {

	private String realmId;
	private String name;
	private int id;
	
	private Group defaultRole = null;
	private Map<String, Group> roles = new LinkedHashMap<String, Group>();
	
	public UserAider(int id) {
		this(id, null, null);
	}

	public UserAider(String name) {
		this.realmId = "exist"; //XXX:parse name for realm id
		this.name = name;
		id = -1;
	}

	public UserAider(String realmId, String name) {
		this(-1, realmId, name);
	}
	
	public UserAider(int id, String realmId, String name) {
		this.realmId = realmId;
		this.name = name;
		this.id = id;
	}

	public UserAider(String realmId, String name, Group group) {
		this(realmId, name);
		defaultRole = addGroup(group);
	}

	public UserAider(String name, Group group) {
		this(RealmImpl.ID, name); //XXX: parse name for realmId, use default as workaround 
		defaultRole = addGroup(group);
	}

	/* (non-Javadoc)
	 * @see java.security.Principal#getName()
	 */
	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getRealmId() {
		return realmId;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.Principal#getId()
	 */
	@Override
	public int getId() {
		return id;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#addGroup(java.lang.String)
	 */
	@Override
	public Group addGroup(String name) {
		Group role = new GroupAider(realmId, name);
		
		roles.put(name, role);
		
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
	public void remGroup(String role) {
		roles.remove(role);
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#setGroups(java.lang.String[])
	 */
	@Override
	public void setGroups(String[] names) {
		roles = new HashMap<String, Group>();
		
		for (int i = 0; i < names.length; i++) {
			addGroup(names[i]);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#getGroups()
	 */
	@Override
	public String[] getGroups() {
		return roles.keySet().toArray(new String[0]);
	}

        @Override
	public int[] getGroupIds() {
            return new int[0];
	}

	/* (non-Javadoc)
	 * @see org.exist.security.User#hasDbaRole()
	 */
	@Override
	public boolean hasDbaRole() {
		return false;
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
		return roles.containsKey(group);
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
	 * @see org.exist.security.User#getRealm()
	 */
	@Override
	public Realm getRealm() {
		return null;
	}

	private Map<SchemaType, String> metadata = new HashMap<SchemaType, String>();

	@Override
    public String getMetadataValue(SchemaType schemaType) {
        return metadata.get(schemaType);
    }

    @Override
    public void setMetadataValue(SchemaType schemaType, String value) {
        metadata.put(schemaType, value);
    }

    @Override
    public Set<SchemaType> getMetadataKeys() {
        return metadata.keySet();
    }

    @Override
    public void clearMetadata() {
        metadata.clear();
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

	@Override
	public boolean isConfigured() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Configuration getConfiguration() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getUsername() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isAccountNonExpired() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isAccountNonLocked() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isEnabled() {
		// TODO Auto-generated method stub
		return false;
	}

    @Override
    public void save() throws PermissionDeniedException {
        //do nothing
    }
    
    @Override
    public void save(DBBroker broker) throws PermissionDeniedException {
        //do nothing
    }

    @Override
    public void assertCanModifyAccount(Account user) throws PermissionDeniedException {
         //do nothing
        //TODO do we need to check any permissions?
    }


}
