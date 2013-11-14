package org.exist.security;

import java.util.Set;
import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.security.realm.Realm;
import org.exist.storage.DBBroker;

public class EffectiveSubject implements Subject {
    private final Account account;
    
    public EffectiveSubject(final Account account) {
        this.account = account;
    }
    
    @Override
    public String getRealmId() {
        return account.getRealmId();
    }
    
    @Override
    public Realm getRealm() {
        return account.getRealm();
    }
    
    @Override
    public int getId() {
        return account.getId(); //TODO is this correct or need own reserved id?
    }
    
    @Override
    public String getUsername() {
        return "_effective_" + account.getUsername();
    }
    
    @Override
    public String getName() {
        return "Effective: " + account.getName();
    }
    
    @Override
    public boolean authenticate(final Object credentials) {
        return false;
    }

    //<editor-fold desc="account status">
    @Override
    public boolean isAuthenticated() {
        return false;
    }

    @Override
    public boolean isExternallyAuthenticated() {
        return false;
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
    public boolean isEnabled() {
        return account.isEnabled();
    }
    
    @Override
    public void setEnabled(final boolean enabled) {
        throw new UnsupportedOperationException("You cannot change the Enabled status of the Effective User.");
    }
    //</editor-fold>

    @Override
    public String getSessionId() {
        throw new UnsupportedOperationException("The Effective User has no session!");
    }

    @Override
    public Session getSession() {
        throw new UnsupportedOperationException("The Effective User has no session!");
    }

    //<editor-fold desc="group functions">
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
    public Group addGroup(final String name) throws PermissionDeniedException {
        throw new UnsupportedOperationException("You cannot add a group to the Effective User");
    }

    @Override
    public Group addGroup(final Group group) throws PermissionDeniedException {
        throw new UnsupportedOperationException("You cannot add a group to the Effective User");
    }

    @Override
    public void setPrimaryGroup(final Group group) throws PermissionDeniedException {
        throw new UnsupportedOperationException("You cannot add a group to the Effective User");
    }

    @Override
    public void setGroups(final String[] groups) {
        throw new UnsupportedOperationException("You cannot set the groups of the Effective User");
    }
    
    @Override
    public void remGroup(final String group) throws PermissionDeniedException {
        throw new UnsupportedOperationException("You cannot remove a group from the Effective User");
    }
    //</editor-fold>

    @Override
    public void setPassword(final String passwd) {
        throw new UnsupportedOperationException("The Effective User has no password!");
    }

    @Override
    public String getPassword() {
        throw new UnsupportedOperationException("The Effective User has no password!");
    }

    @Override
    public String getDigestPassword() {
        throw new UnsupportedOperationException("The Effective User has no password!");
    }

    @Override
    public void assertCanModifyAccount(final Account user) throws PermissionDeniedException {
        throw new PermissionDeniedException("The Effective User account cannot be modified");
    }

    @Override
    public int getUserMask() {
        return account.getUserMask();
    }

    @Override
    public void setUserMask(final int umask) {
        throw new UnsupportedOperationException("You cannot set the UserMask of the Effective User"); //To change body of generated methods, choose Tools | Templates.
    }
    
    //<editor-fold desc="metadata">
    @Override
    public String getMetadataValue(final SchemaType schemaType) {
        return account.getMetadataValue(schemaType);
    }

    @Override
    public Set<SchemaType> getMetadataKeys() {
        return account.getMetadataKeys();
    }
    
    @Override
    public void setMetadataValue(SchemaType schemaType, String value) {
         throw new UnsupportedOperationException("You cannot modify the metadata of the Effective User");
    }

    @Override
    public void clearMetadata() {
        throw new UnsupportedOperationException("You cannot modify the metadata of the Effective User");
    }
    //</editor-fold>

    //<editor-fold desc="persistence">
    @Override
    public void save() throws ConfigurationException, PermissionDeniedException {
        throw new UnsupportedOperationException("You cannot perist the Effective User.");
    }

    @Override
    public void save(final DBBroker broker) throws ConfigurationException, PermissionDeniedException {
        throw new UnsupportedOperationException("You cannot perist the Effective User.");
    }
    
    @Override
    public boolean isConfigured() {
        return true; //the effective user does not need configuring
    }

    @Override
    public Configuration getConfiguration() {
        return null; //the effective user does not need configuring
    }
    //</editor-fold>
}
