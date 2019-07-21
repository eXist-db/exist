/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2015 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.security.internal.aider;

import java.util.*;

import org.exist.config.Configuration;
import org.exist.security.Account;
import org.exist.security.Credential;
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
    private final Map<SchemaType, String> metadata = new HashMap<>();
    private String password = null;
    private String passwordDigest = null;
    private Map<String, Group> groups = new LinkedHashMap<>();
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
        addGroup(group);
    }

    public UserAider(final String name, final Group group) {
        this(name);
        addGroup(group);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getRealmId() {
        return realmId;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public Group addGroup(final String name) {
        final Group group = new GroupAider(realmId, name);
        groups.put(name, group);
        return group;
    }

    @Override
    public Group addGroup(final Group group) {
        if (group == null) {
            return null;
        }
        return addGroup(group.getName());
    }

    @Override
    public void setPrimaryGroup(final Group group) throws PermissionDeniedException {

        if(!groups.containsKey(group.getName())) {
            addGroup(group);
        }

        final List<Map.Entry<String, Group>> entries = new ArrayList<>(groups.entrySet());
        Collections.sort(entries, (final Map.Entry<String, Group> o1, final Map.Entry<String, Group> o2) -> {
            if (o1.getKey().equals(group.getName())) {
                return -1;
            } else {
                return 1;
            }
        });

        groups = new LinkedHashMap<>();
        for(final Map.Entry<String, Group> entry : entries) {
            groups.put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void remGroup(final String role) throws PermissionDeniedException {
        if (groups.containsKey(role) && groups.size() <= 1) {
            throw new PermissionDeniedException("You cannot remove the primary group of an account.");
        }
        groups.remove(role);
    }

    @Override
    public void setGroups(final String[] names) {
        groups = new HashMap<>();

        for (final String name : names) {
            addGroup(name);
        }
    }

    @Override
    public String[] getGroups() {
        return groups.keySet().toArray(new String[0]);
    }

    @Override
    public int[] getGroupIds() {
        return new int[0];
    }

    @Override
    public boolean hasDbaRole() {
        return false;
    }

    @Override
    public String getPrimaryGroup() {
        final Group defaultGroup = getDefaultGroup();
        if (defaultGroup != null) {
            return defaultGroup.getName();
        } else {
            return null;
        }
    }

    @Override
    public boolean hasGroup(final String group) {
        return groups.containsKey(group);
    }

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
        if (groups != null && groups.size() > 0) {
            final Iterator<Group> iterator = groups.values().iterator();
            if (iterator.hasNext()) {
                return iterator.next();
            }
        }
        return null;
    }

    public void setEncodedPassword(final String passwd) {
        password = passwd;
    }

    @Override
    public void setPassword(final String passwd) {
        password = passwd;
    }

    @Override
    public void setCredential(final Credential credential) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public String getPassword() {
        return password;
    }

    public void setPasswordDigest(final String password) {
        passwordDigest = password;
    }

    @Override
    public String getDigestPassword() {
        return passwordDigest;
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public Configuration getConfiguration() {
        return null;
    }

    @Override
    public String getUsername() {
        return null;
    }

    @Override
    public boolean isAccountNonExpired() {
        return false;
    }

    @Override
    public boolean isAccountNonLocked() {
        return false;
    }

    @Override
    public boolean isCredentialsNonExpired() {
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
        if (user.getId() != getId() && !user.hasDbaRole()) {
            throw new PermissionDeniedException("Permission denied to modify user");
        }
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
