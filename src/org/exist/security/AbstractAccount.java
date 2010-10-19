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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;
import org.exist.config.annotation.ConfigurationReferenceBy;
import org.exist.security.internal.SubjectImpl;
import org.exist.security.realm.Realm;
import org.exist.xmldb.XmldbURI;

@ConfigurationClass("")
public abstract class AbstractAccount extends AbstractPrincipal implements Account {

	@ConfigurationFieldAsElement("home")
	protected XmldbURI home = null;
	
	@ConfigurationFieldAsElement("group")
	@ConfigurationReferenceBy("name")
	protected List<Group> groups = new ArrayList<Group>();
	
	//used for internal locking
	private boolean accountLocked = false;
	
	@ConfigurationFieldAsElement("expired")
	private boolean accountExpired = false;
	
//	@ConfigurationFieldAsElement("credentials-expired")
    private boolean credentialsExpired = false;
	
    @ConfigurationFieldAsElement("enabled")
    private boolean enabled = true;
    
	protected Credential _cred = null;

	private Map<String, Object> attributes = new HashMap<String, Object>();

	/**
	 * Indicates if the user belongs to the dba group, i.e. is a superuser.
	 */
	protected boolean hasDbaRole = false;

	public AbstractAccount(AbstractRealm realm, int id, String name) throws ConfigurationException {
		super(realm, realm.collectionAccounts, id, name);
	}
	
	public AbstractAccount(AbstractRealm realm, Configuration configuration) throws ConfigurationException {
		super(realm, configuration);
	}


        public boolean checkCredentials(Object credentials) {
            return _cred == null ? false : _cred.check(credentials);
        }

        @Override
	public final Group addGroup(String name) throws PermissionDeniedException {
		Group group = realm.getGroup(null, name);
		return addGroup(group);
	}

	//this method used by Configurator
	protected final Group addGroup(Configuration conf) throws PermissionDeniedException {
		if (conf == null) return null;
		
		String name = conf.getProperty("name");
		if (name == null) return null;
		
		return addGroup(name);
	}
	
    @Override
	public final void remGroup(String name) {
		for (Group group : groups) {
			if (group.getName().equals(name)) {
				groups.remove(group);
				break;
			}
		}

		if (SecurityManager.DBA_GROUP.equals(name))
			hasDbaRole = false;
	}

    @Override
	public final void setGroups(String[] groups) {
//		this.groups = groups;
//		for (int i = 0; i < groups.length; i++)
//			if (SecurityManager.DBA_GROUP.equals(groups[i]))
//				hasDbaRole = true;
	}

    @Override
	public final String[] getGroups() {
		if (groups == null) return new String[0];
		
		int i = 0;
		String[] names = new String[groups.size()];
		for (Group role : groups) {
			names[i] = role.getName();
			i++;
		}
		
		return names;
	}

    @Override
	public final boolean hasGroup(String name) {
		if (groups == null)
			return false;
		
		for (Group group : groups) {
			if (group.getName().equals(name))
				return true;
		}
		
		return false;
	}

    @Override
	public final boolean hasDbaRole() {
		return hasDbaRole;
	}

    @Override
	public final String getPrimaryGroup() {
		if (groups != null && groups.size() > 0)
			return groups.get(0).getName();

		return null;
	}

    @Override
	public final String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append("<account name=\"");
		buf.append(name);
		buf.append("\" ");
		buf.append("id=\"");
		buf.append(Integer.toString(id));
		buf.append("\"");
		if (home != null) {
			buf.append(" home=\"");
			buf.append(home);
			buf.append("\">");
		} else
			buf.append(">");
		if (groups != null) {
			for (Group group : groups) {
				buf.append("<group>");
				buf.append(group.getName());
				buf.append("</group>");
			}
		}
		buf.append("</user>");
		return buf.toString();
	}

    @Override
	public XmldbURI getHome() {
		return home;
	}

    @Override
	public boolean equals(Object obj) {
		AbstractAccount other;
		
		if (obj instanceof SubjectImpl) {
			other = ((SubjectImpl) obj).account;
			
		} else if (obj instanceof AbstractAccount) {
			other = (AbstractAccount) obj;
		
		} else {
			return false;
		}
	
		if (other != null)
			return (realm == other.realm && name.equals(other.name)); //id == other.id;

		return false;
	}

	@Override
	public Realm getRealm() {
		return realm;
	}

	/**
	 * Add a named attribute.
	 *
	 * @param name
	 * @param value
	 */
	@Override
	public void setAttribute(String name, Object value) {
		attributes.put(name, value);
	}

	/**
	 * Get the named attribute value.
	 *
	 * @param name The String that is the name of the attribute.
	 * @return The value associated with the name or null if no value is associated with the name.
	 */
	@Override
	public Object getAttribute(String name) {
		return attributes.get(name);
	}

	/**
	 * Returns the set of attributes names.
	 *
	 * @return the Set of attribute names.
	 */
	@Override
	public Set<String> getAttributeNames() {
	    return attributes.keySet();
	}

	@Override
	public Group getDefaultGroup() {
		if (groups != null && groups.size() > 0)
			return groups.get(0);

		return null;
	}

    @Override
	public void setHome(XmldbURI homeCollection) {
		home = homeCollection;
	}

    @Override
    public String getUsername() {
    	return getName();
    }

    @Override
    public boolean isAccountNonExpired() {
    	return !accountExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
    	return !accountLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
    	return !credentialsExpired;
    }

    @Override
    public boolean isEnabled() {
    	return enabled;
    }
}