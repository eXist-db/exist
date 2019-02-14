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
package org.exist.security;

import java.util.Optional;
import java.util.Set;

import org.exist.config.Configuration;
import org.exist.security.realm.Realm;
import org.exist.storage.DBBroker;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public abstract class AbstractSubject implements Subject {

    protected final AbstractAccount account;
    protected final Session session;

    public AbstractSubject(final AbstractAccount account) {
        this.account = account;
        this.session = new Session(this);
    }

    @Override
    public Group addGroup(final String name) throws PermissionDeniedException {
        return account.addGroup(name);
    }

    @Override
    public Group addGroup(final Group group) throws PermissionDeniedException {
        return account.addGroup(group);
    }

    @Override
    public void setPrimaryGroup(final Group group) throws PermissionDeniedException {
        account.setPrimaryGroup(group);
    }

    @Override
    public void remGroup(final String group) throws PermissionDeniedException {
        account.remGroup(group);
    }

    @Override
    public String[] getGroups() {
        return account.getGroups();
    }

    @Override
    public int[] getGroupIds() {
        return account.getGroupIds();
    }

    @Override
    public boolean hasDbaRole() {
        return account.hasDbaRole();
    }

    @Override
    public String getPrimaryGroup() {
        return account.getPrimaryGroup();
    }

    @Override
    public Group getDefaultGroup() {
        return account.getDefaultGroup();
    }

    @Override
    public boolean hasGroup(final String group) {
        return account.hasGroup(group);
    }

    @Override
    public void setPassword(final String passwd) {
        account.setPassword(passwd);
    }

    @Override
    public void setCredential(final Credential credential) {
        account.setCredential(credential);
    }

    @Override
    public Realm getRealm() {
        return account.getRealm();
    }

    @Override
    public String getPassword() {
        return account.getPassword();
    }

    @Override
    public String getDigestPassword() {
        return account.getDigestPassword();
    }

    @Override
    public void setGroups(final String[] groups) {
        account.setGroups(groups);
    }

    @Override
    public String getRealmId() {
        return account.getRealmId();
    }

    @Override
    public int getId() {
        return account.getId();
    }

    @Override
    public String getName() {
        return account.getName();
    }

    @Override
    public boolean isConfigured() {
        return account.isConfigured();
    }

    @Override
    public Configuration getConfiguration() {
        return account.getConfiguration();
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
    public void setEnabled(final boolean enabled) {
        account.setEnabled(enabled);
    }

    @Override
    public boolean isEnabled() {
        return account.isEnabled();
    }

    @Override
    public boolean equals(final Object obj) {
       return Optional
               .ofNullable(obj)
               .flatMap(other -> other instanceof Account ? Optional.of((Account)other) : Optional.empty())
               .map(otherAccount -> account.equals(otherAccount))
               .orElse(false);
    }

    @Override
    public String getSessionId() {
        return session.getId();
    }

    @Override
    public Session getSession() {
        return session;
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
    public String getMetadataValue(final SchemaType schemaType) {
        return account.getMetadataValue(schemaType);
    }

    @Override
    public void setMetadataValue(final SchemaType schemaType, final String value) {
        account.setMetadataValue(schemaType, value);
    }

    @Override
    public Set<SchemaType> getMetadataKeys() {
        return account.getMetadataKeys();
    }

    @Override
    public void assertCanModifyAccount(final Account user) throws PermissionDeniedException {
        account.assertCanModifyAccount(user);
    }

    @Override
    public void clearMetadata() {
        account.clearMetadata();
    }
    
    @Override
    public int getUserMask() {
        return account.getUserMask();
    }
    
    @Override
    public void setUserMask(final int umask) {
        account.setUserMask(umask);
    }
}