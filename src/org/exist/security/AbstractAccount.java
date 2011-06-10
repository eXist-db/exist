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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;
import org.exist.config.annotation.ConfigurationReferenceBy;
import org.exist.security.internal.RealmImpl;
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


    @ConfigurationFieldAsElement("metadata")
    private Map<String, String> metadata = new HashMap<String, String>();
    
	protected Credential _cred = null;

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
	public Group addGroup(String name) throws PermissionDeniedException {
        Group group = getRealm().getGroup(null, name);

        //if we cant find the group in our own realm, try the default realm
        if(group == null) {
            Realm internalRealm = getRealm().getSecurityManager().getRealm(RealmImpl.ID);
            group = internalRealm.getGroup(null, name);
        }
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
    public Group addGroup(Group group) throws PermissionDeniedException {

        if(group == null){
            return null;
        }

        Account user = getDatabase().getSubject();
        group.assertCanModifyGroup(user);

        if(!groups.contains(group)) {
            groups.add(group);

            if(SecurityManager.DBA_GROUP.equals(group.getName())) {
                hasDbaRole = true;
            }
        }

        return group;
    }
	
    @Override
    public final void remGroup(String name) throws PermissionDeniedException {

        Account subject = getDatabase().getSubject();

        for (Group group : groups) {
            if (group.getName().equals(name)) {

                group.assertCanModifyGroup(subject);

                //remove from the group
                groups.remove(group);
                break;
            }
        }

        if(SecurityManager.DBA_GROUP.equals(name)){
            hasDbaRole = false;
        }
    }

    @Override
	public final void setGroups(String[] groups) {
//		this.groups = groups;
//		for (int i = 0; i < groups.length; i++)
//			if (SecurityManager.DBA_GROUP.equals(groups[i]))
//				hasDbaRole = true;
	}

    @Override
	public String[] getGroups() {
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
        public int[] getGroupIds() {
            if(groups == null) return new int[0];

		int i = 0;
		int[] ids = new int[groups.size()];
		for (Group group : groups) {
                    ids[i++] = group.getId();
		}

		return ids;
        }

    @Override
	public final boolean hasGroup(String name) {
		if (groups == null) {
			return false;
                }
		
		for (Group group : groups) {
                    if (group.getName().equals(name)) {
				return true;
                    }
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
                            buf.append(group.toString());
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
		
		if (obj instanceof AbstractSubject) {
			other = ((AbstractSubject) obj).account;
			
		} else if (obj instanceof AbstractAccount) {
			other = (AbstractAccount) obj;
		
		} else {
			return false;
		}
	
		if (other != null)
			return (getRealm() == other.getRealm() && name.equals(other.name)); //id == other.id;

		return false;
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

    @Override
    public String getMetadataValue(AXSchemaType axSchemaType) {
        return metadata.get(axSchemaType.getNamespace());
    }

    @Override
    public void setMetadataValue(AXSchemaType axSchemaType, String value) {
        metadata.put(axSchemaType.getNamespace(), value);
    }

    @Override
    public Set<AXSchemaType> getMetadataKeys() {
        Set<AXSchemaType> metadataKeys = new HashSet<AXSchemaType>();
        for(String key : metadata.keySet()) {
            metadataKeys.add(AXSchemaType.valueOfNamespace(key));
        }
        return metadataKeys;
    }

    @Override
    public void assertCanModifyAccount(Account user) throws PermissionDeniedException {
        if(user == null) {
            throw new PermissionDeniedException("Unspecified User is not allowed to modify account '" + getName() + "'");
        } else if(!user.hasDbaRole() && !user.getName().equals(getName())) {
            throw new PermissionDeniedException("User '" + user.getName() + "' is not allowed to modify account '" + getName() + "'");
        }
    }
}