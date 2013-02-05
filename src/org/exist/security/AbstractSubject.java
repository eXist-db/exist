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
package org.exist.security;

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

    public AbstractSubject(AbstractAccount account) {
        this.account = account;
        this.session = new Session(this);
    }

    /* (non-Javadoc)
     * @see org.exist.security.User#addGroup(java.lang.String)
     */
    @Override
    public Group addGroup(String name) throws PermissionDeniedException {
        return account.addGroup(name);
    }

    /* (non-Javadoc)
     * @see org.exist.security.User#addGroup(org.exist.security.Group)
     */
    @Override
    public Group addGroup(Group group) throws PermissionDeniedException {
        return account.addGroup(group);
    }

    @Override
    public void setPrimaryGroup(final Group group) throws PermissionDeniedException {
        account.setPrimaryGroup(group);
    }

    /* (non-Javadoc)
         * @see org.exist.security.User#remGroup(java.lang.String)
         */
    @Override
    public void remGroup(String group) throws PermissionDeniedException {
        account.remGroup(group);
    }

    /* (non-Javadoc)
     * @see org.exist.security.User#getGroups()
     */
    @Override
    public String[] getGroups() {
        return account.getGroups();
    }

    @Override
    public int[] getGroupIds() {
        return account.getGroupIds();
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

    @Override
    public String getRealmId() {
        return account.getRealmId();
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
    public boolean equals(Object obj) {
        return account.equals(obj);
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
    public void save(DBBroker broker) throws PermissionDeniedException {
        //do nothing
    }

    @Override
    public String getMetadataValue(SchemaType schemaType) {
        return account.getMetadataValue(schemaType);
    }

    @Override
    public void setMetadataValue(SchemaType schemaType, String value) {
        account.setMetadataValue(schemaType, value);
    }

    @Override
    public Set<SchemaType> getMetadataKeys() {
        return account.getMetadataKeys();
    }

    @Override
    public void assertCanModifyAccount(Account user) throws PermissionDeniedException {
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