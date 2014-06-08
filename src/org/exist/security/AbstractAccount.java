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

import java.util.*;

import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;
import org.exist.config.annotation.ConfigurationFieldSettings;
import static org.exist.config.annotation.ConfigurationFieldSettings.OCTAL_STRING_KEY;
import org.exist.config.annotation.ConfigurationReferenceBy;
import org.exist.storage.DBBroker;

@ConfigurationClass("")
public abstract class AbstractAccount extends AbstractPrincipal implements Account {
	
	@ConfigurationFieldAsElement("group")
	@ConfigurationReferenceBy("name")
	protected List<Group> groups = new ArrayList<Group>();
	
	//used for internal locking
	private boolean accountLocked = false;
	
	@ConfigurationFieldAsElement("expired")
	private boolean accountExpired = false;
	
    //@ConfigurationFieldAsElement("credentials-expired")
    private boolean credentialsExpired = false;

    @ConfigurationFieldAsElement("enabled")
    private boolean enabled = true;
    
    @ConfigurationFieldAsElement("umask")
    @ConfigurationFieldSettings(OCTAL_STRING_KEY)
    private int umask = Permission.DEFAULT_UMASK;

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
	
    protected AbstractAccount(DBBroker broker, AbstractRealm realm, int id, String name) throws ConfigurationException {
		super(broker, realm, realm.collectionAccounts, id, name);
	}
        
	public AbstractAccount(AbstractRealm realm, Configuration configuration) throws ConfigurationException {
		super(realm, configuration);
	}


    public boolean checkCredentials(Object credentials) {
        return _cred == null ? false : _cred.check(credentials);
    }

        @Override
	public Group addGroup(String name) throws PermissionDeniedException {
        Group group = getRealm().getGroup(name);

        //if we cant find the group in our own realm, try other realms
        if(group == null) {
            group = getRealm().getSecurityManager().getGroup(name);
        }
        
        return addGroup(group);
	}

	//this method used by Configurator
	protected final Group addGroup(Configuration conf) throws PermissionDeniedException {
		if (conf == null) {return null;}
		
		final String name = conf.getProperty("name");
		if (name == null) {return null;}
		
		return addGroup(name);
	}

    @Override
    public Group addGroup(Group group) throws PermissionDeniedException {

        if(group == null){
            return null;
        }

        final Account user = getDatabase().getSubject();
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
    public void setPrimaryGroup(final Group group) throws PermissionDeniedException {

        final Account user = getDatabase().getSubject();
        group.assertCanModifyGroup(user);

        if(!groups.contains(group)) {
            addGroup(group);
        }

        Collections.sort(groups, new Comparator<Group>(){
            @Override
            public int compare(final Group o1, final Group o2) {
                if(o1.getName().equals(group.getName())) {
                    return -1;
                } else {
                    return 1;
                }
            }
        });
    }
	
    @Override
    public final void remGroup(String name) throws PermissionDeniedException {

        final Account subject = getDatabase().getSubject();

        for (final Group group : groups) {
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
		if (groups == null) {return new String[0];}
		
		int i = 0;
		final String[] names = new String[groups.size()];
		for (final Group role : groups) {
                    names[i++] = role.getName();
		}
		
		return names;
	}

    @Override
    public int[] getGroupIds() {
        if(groups == null) {return new int[0];}

		int i = 0;
		final int[] ids = new int[groups.size()];
		for (final Group group : groups) {
	                ids[i++] = group.getId();
		}
	
		return ids;
    }

    @Override
	public final boolean hasGroup(String name) {
		if (groups == null) {
			return false;
                }
		
		for (final Group group : groups) {
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
	public final String toString() {
		final StringBuilder buf = new StringBuilder();
		buf.append("<account name=\"");
		buf.append(name);
		buf.append("\" ");
		buf.append("id=\"");
		buf.append(Integer.toString(id));
		buf.append("\"");
                buf.append(">");
		if (groups != null) {
			for (final Group group : groups) {
                            buf.append(group.toString());
			}
		}
		buf.append("</account>");
		return buf.toString();
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
			{return (getRealm() == other.getRealm() && name.equals(other.name));} //id == other.id;

		return false;
	}

        @Override
	public final String getPrimaryGroup() {
            
            //TODO this function should return Group and not String
            
            final Group defaultGroup = getDefaultGroup();
            if(defaultGroup != null) {
                return defaultGroup.getName();
            } else {
                return null;
            }
	}
    
        /**
         * @deprecated user getPrimaryGroup instead;
         */
        @Deprecated
	@Override
	public Group getDefaultGroup() {
            if(groups != null && groups.size() > 0) {
                return groups.get(0);
            }

            return null;
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
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }
    
    @Override
    public boolean isEnabled() {
    	return enabled;
    }

    @Override
    public String getMetadataValue(final SchemaType schemaType) {
        return metadata.get(schemaType.getNamespace());
    }

    @Override
    public void setMetadataValue(final SchemaType schemaType, final String value) {
        metadata.put(schemaType.getNamespace(), value);
    }

    @Override
    public Set<SchemaType> getMetadataKeys() {
        final Set<SchemaType> metadataKeys = new HashSet<SchemaType>();
        
        for(final String key : metadata.keySet()) {
            //XXX: other types?
            if(AXSchemaType.valueOfNamespace(key) != null) {
                metadataKeys.add(AXSchemaType.valueOfNamespace(key));
            } else if(EXistSchemaType.valueOfNamespace(key) != null){
                metadataKeys.add(EXistSchemaType.valueOfNamespace(key));
            }
        }
        return metadataKeys;
    }
    
    @Override
    public void clearMetadata() {
        if(metadata != null) {
            this.metadata.clear();
        }
    }

    @Override
    public void assertCanModifyAccount(Account user) throws PermissionDeniedException {
        if(user == null) {
            throw new PermissionDeniedException("Unspecified User is not allowed to modify account '" + getName() + "'");
        } else if(!user.hasDbaRole() && !user.getName().equals(getName())) {
            throw new PermissionDeniedException("User '" + user.getName() + "' is not allowed to modify account '" + getName() + "'");
        }
    }
    
    @Override
    public void setUserMask(final int umask) {
        this.umask = umask;
    }

    @Override
    public int getUserMask() {
        return umask;
    }
}
