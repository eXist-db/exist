/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.security;

@Deprecated //use Account
public interface User extends Principal {

    int PLAIN_ENCODING = 0;
    int SIMPLE_MD5_ENCODING = 1;
    int MD5_ENCODING = 2;

    /**
     * Add the user to a group
     *
     * @param group The group to add the user to
     * @return The group the user was added to
     * @throws PermissionDeniedException is user has not sufficient rights
     */
    Group addGroup(String group) throws PermissionDeniedException;

    /**
     * Add the user to a group
     *
     * @param group The group to add the user to
     * @return The group the user was added to
     * @throws PermissionDeniedException is user has not sufficient rights
     */
    Group addGroup(Group group) throws PermissionDeniedException;

    /**
     * Remove the user to a group
     *
     * @param group The group to remove the user from
     * @throws PermissionDeniedException is user has not sufficient rights
     */
    void remGroup(String group) throws PermissionDeniedException;

    /**
     * Get all groups this user belongs to
     *
     * @return The groups that the user belongs to
     */
    String[] getGroups();

    int[] getGroupIds();

    boolean hasDbaRole();

    /**
     * Get the primary group this user belongs to
     *
     * @return The primary group that the use belongs to
     */
    String getPrimaryGroup();
    Group getDefaultGroup();

    /**
     * Is the user a member of group?
     *
     * @param group The group to check if the user is a member of
     * @return true if the user is a member of the group
     */
    boolean hasGroup(String group);

    /**
     * Sets the password attribute of the User object
     *
     * @param passwd The new password value
     * @deprecated See {@link org.exist.security.User#setCredential(org.exist.security.Credential)}
     */
    @Deprecated
    void setPassword(String passwd);

    /**
     * Sets the authentication credential for the user
     *
     * @param credential The authentication credential
     */
    void setCredential(Credential credential);

    /**
     * Get the user's password
     * 
     * @return The users password
     * @deprecated
     */
    @Deprecated
    String getPassword();

    @Deprecated
    String getDigestPassword();

    @Deprecated
    void setGroups(String[] groups);
    
    /**
     * Returns the person full name or account name.
     *
     * @return the person full name or account name
     */
    String getUsername();

    /**
     * Indicates whether the account has expired. Authentication on an expired account is not possible.
     *
     * @return <code>true</code> if the account is valid (ie non-expired), <code>false</code> if no longer valid (ie expired)
     */
    boolean isAccountNonExpired();

    /**
     * Indicates whether the account is locked or unlocked. Authentication on a locked account is not possible.
     *
     * @return <code>true</code> if the account is not locked, <code>false</code> otherwise
     */
    boolean isAccountNonLocked();

    /**
     * Indicates whether the account's credentials has expired. Expired credentials prevent authentication.
     *
     * @return <code>true</code> if the account's credentials are valid (ie non-expired), <code>false</code> if no longer valid (ie expired)
     */
    boolean isCredentialsNonExpired();

    /**
     * Indicates whether the account is enabled or disabled. Authentication on a disabled account is not possible.
     *
     * @return <code>true</code> if the account is enabled, <code>false</code> otherwise
     */
    boolean isEnabled();
    
    /**
     * Sets whether the account is enabled or disabled. Authentication on a disabled account is not possible.
     *
     * @param enabled <code>true</code> if the account is enabled, <code>false</code> otherwise
     */
    void setEnabled(boolean enabled);
}