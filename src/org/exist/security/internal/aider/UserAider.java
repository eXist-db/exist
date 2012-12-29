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
import org.exist.security.Account;
import org.exist.security.Group;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SchemaType;
import org.exist.security.internal.RealmImpl;
import org.exist.security.realm.Realm;
import org.exist.storage.DBBroker;

/**
 * Account details.
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */

//TODO UserAider (and all *Aider classes) is evil and must be destroyed. Its too easy to use a UserAider to securityManager.updateAccount
//and it turns out you have forgotten to set some property of the account and so it is removed from the configuration
//Note by Adam Retter 2012-12-29
public class UserAider implements Account {
    
    private final String realmId;
    private final String name;
    private final int id;
    private Map<SchemaType, String> metadata = new HashMap<SchemaType, String>();
    private String password = null;
    private String passwordDigest = null;
    private Group defaultRole = null;
    private Map<String, Group> roles = new LinkedHashMap<String, Group>();
    private int umask = Permission.DEFAULT_UMASK;
    private boolean enabled = true;

    public UserAider(final int id) {
        this(id, null, null);
    }

    public UserAider(final String name) {
        this(RealmImpl.ID, name); //XXX:parse name for realm id
    }

    public UserAider(final String realmId, final String name) {
        this(UNDEFINED_ID, realmId, name);
    }

    public UserAider(final int id, final String realmId, final String name) {
        this.realmId = realmId;
        this.name = name;
        this.id = id;
    }

    public UserAider(final String realmId, final String name, final Group group) {
        this(realmId, name);
        defaultRole = addGroup(group);
    }

    public UserAider(final String name, final Group group) {
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
    public Group addGroup(final String name) {
        final Group role = new GroupAider(realmId, name);	
        roles.put(name, role);
        return role;
    }

    /* (non-Javadoc)
     * @see org.exist.security.User#addGroup(org.exist.security.Group)
     */
    @Override
    public Group addGroup(final Group group) {
        if (group == null) {
            return null;
        }
        return addGroup(group.getName());
    }

    /* (non-Javadoc)
     * @see org.exist.security.User#remGroup(java.lang.String)
     */
    @Override
    public void remGroup(final String role) {
        roles.remove(role);
    }

    /* (non-Javadoc)
     * @see org.exist.security.User#setGroups(java.lang.String[])
     */
    @Override
    public void setGroups(final String[] names) {
        roles = new HashMap<String, Group>();

        for(int i = 0; i < names.length; i++) {
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
        if(defaultRole == null) {
            return null;
        }
        return defaultRole.getName();
    }

    /* (non-Javadoc)
     * @see org.exist.security.User#hasGroup(java.lang.String)
     */
    @Override
    public boolean hasGroup(final String group) {
        return roles.containsKey(group);
    }

    /* (non-Javadoc)
     * @see org.exist.security.User#getRealm()
     */
    @Override
    public Realm getRealm() {
        return null;
    }

    @Override
    public String getMetadataValue(final SchemaType schemaType) {
        return metadata.get(schemaType);
    }

    @Override
    public void setMetadataValue(final SchemaType schemaType, final String value) {
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

    public void setEncodedPassword(final String passwd) {
        password = passwd;
    }

    /* (non-Javadoc)
     * @see org.exist.security.User#setPassword(java.lang.String)
     */
    @Override
    public void setPassword(final String passwd) {
        password = passwd;
    }

    /* (non-Javadoc)
     * @see org.exist.security.User#getPassword()
     */
    @Override
    public String getPassword() {
        return password;
    }

    public void setPasswordDigest(final String password) {
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
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void save() throws PermissionDeniedException {
        //do nothing
    }

    @Override
    public void save(final DBBroker broker) throws PermissionDeniedException {
        //do nothing
    }

    @Override
    public void assertCanModifyAccount(final Account user) throws PermissionDeniedException {
         //do nothing
        //TODO do we need to check any permissions?
    }

    @Override
    public int getUserMask() {
        return umask;
    }
    
    @Override
    public void setUserMask(final int umask) {
        this.umask = umask;
    }
}
