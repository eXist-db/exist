/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
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
package org.exist.xmldb;

import java.util.Date;
import java.util.List;

import org.exist.security.Group;
import org.exist.security.Permission;
import org.exist.security.Account;
import org.exist.security.User;
import org.exist.security.internal.aider.ACEAider;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.Service;
import org.xmldb.api.base.XMLDBException;

/**
 * An eXist-specific service which provides methods to manage users and
 * permissions.
 *
 * @author <a href="mailto:meier@ifs.tu-darmstadt.de">Wolfgang Meier</a>
 * @author Modified by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public interface UserManagementService extends Service {

    /**
     * Get the name of this service
     *
     * @return The name
     */
    @Override
    String getName();


    /**
     * Get the version of this service
     *
     * @return The version value
     */
    @Override
    String getVersion();

    /**
     * Set permissions for the specified collection.
     *
     * @param child the child collection
     * @param perm the new permissions
     *
     * @throws XMLDBException if an error occurs whilst setting the permissions
     */
    void setPermissions(Collection child, Permission perm) throws XMLDBException;

    void setPermissions(Collection child, String owner, String group, int mode, List<ACEAider> aces) throws XMLDBException;

    /**
     * Set permissions for the specified resource.
     *
     * @param resource the child resource.
     * @param perm the new permissions
     *
     * @throws XMLDBException if an error occurs whilst setting the permissions
     */
    void setPermissions(Resource resource, Permission perm) throws XMLDBException;

    void setPermissions(Resource resource, String owner, String group, int mode, List<ACEAider> aces)
            throws XMLDBException;

    /**
     * Change owner gid of the current collection.
     *
     * @param group The group
     *
     * @throws XMLDBException if an error occurs whilst changing the group.
     */
    void chgrp(String group) throws XMLDBException;

    /**
     * Change owner uid of the current collection.
     *
     * @param u The user
     *
     * @throws XMLDBException if an error occurs whilst changing the owner.
     */
    void chown(Account u) throws XMLDBException;

    /**
     * Change owner uid and gid of the current collection.
     *
     * @param u The user
     * @param group The group
     *
     * @throws XMLDBException if an error occurs whilst changing the owner.
     */
    void chown(Account u, String group) throws XMLDBException;

    /**
     * Change owner gid of the specified resource.
     *
     * @param res The resource
     * @param group The group
     *
     * @throws XMLDBException if an error occurs whilst changing the group.
     */
    void chgrp(Resource res, String group) throws XMLDBException;

    /**
     * Change owner uid of the specified resource.
     *
     * @param res The resource
     * @param u The user
     *
     * @throws XMLDBException if an error occurs whilst changing the owner.
     */
    void chown(Resource res, Account u) throws XMLDBException;

    /**
     * Change owner uid and gid of the specified resource.
     *
     * @param res The resource
     * @param u The user
     * @param group The group
     *
     * @throws XMLDBException if an error occurs whilst changing the owner.
     */
    void chown(Resource res, Account u, String group) throws XMLDBException;

    /**
     * Change permissions for the specified resource.
     *
     * Permissions are specified in a string according to the
     * following format:
     *
     * <pre>[user|group|other]=[+|-][read|write|update]</pre>
     *
     * For example, to grant all permissions to the group and
     * deny everything to others:
     *
     * group=+write,+read,+update,other=-read
     *
     * The changes are applied to the permissions currently
     * active for this resource.
     *
     * @param resource Description of the Parameter
     * @param modeStr  Description of the Parameter
     *
     * @throws XMLDBException if an error occurs whilst changing the mode.
     */
    void chmod(Resource resource, String modeStr) throws XMLDBException;

    /**
     * Change permissions for the current collection
     *
     * @param modeStr String describing the permissions to
     *                grant or deny.
     *
     * @throws XMLDBException if an error occurs whilst changing the mode.
     */
    void chmod(String modeStr) throws XMLDBException;

    void chmod(int mode) throws XMLDBException;

    /**
     * Change permissions for the specified resource.
     *
     * @param resource the resource
     * @param mode the mode
     *
     * @throws XMLDBException if an error occurs whilst changing the mode.
     */
    void chmod(Resource resource, int mode) throws XMLDBException;

    /**
     * Lock the specified resource for the specified user.
     *
     * A locked resource cannot be changed by other users (except
     * users in group DBA) until the lock is released. Users with admin
     * privileges can always change a resource.
     *
     * @param res the resource
     * @param u the user
     *
     * @throws XMLDBException if an error occurs whilst locking the resource.
     */
    void lockResource(Resource res, Account u) throws XMLDBException;

    /**
     * Check if the resource has a user lock.
     *
     * Returns the name of the owner of the lock or null
     * if no lock has been set on the resource.
     *
     * @param res the resource
     *
     * @return Name of the owner of the lock
     *
     * @throws XMLDBException if an error occurs whilst detemining if the resource has a user lock.
     */
    String hasUserLock(Resource res) throws XMLDBException;

    /**
     * Unlock the specified resource.
     *
     * The current user has to be same who locked the resource.
     * Exception: admin users can always unlock a resource.
     *
     * @param res the resource
     *
     * @throws XMLDBException if an error occurs whilst unlocking the resource.
     */
    void unlockResource(Resource res) throws XMLDBException;

    /**
     * Add a new account to the database
     *
     * @param account The feature to be added to the Account
     *
     * @throws XMLDBException if an error occurs whilst adding an account.
     */
    void addAccount(Account account) throws XMLDBException;

    /**
     * Update existing account information
     *
     * @param account Description of the Parameter
     *
     * @throws XMLDBException if an error occurs whilst updating an account.
     */
    void updateAccount(Account account) throws XMLDBException;

    /**
     * Update existing group information
     *
     * @param group The group to update
     *
     * @throws XMLDBException if the group could not be updated
     */
    void updateGroup(Group group) throws XMLDBException;

    /**
     * Get a account record from the database
     *
     * @param name Description of the Parameter
     *
     * @return The user value
     *
     * @throws XMLDBException if an error occurs whilst getting an account.
     */
    Account getAccount(String name) throws XMLDBException;

    void addAccountToGroup(String accountName, String groupName) throws XMLDBException;

    void addGroupManager(String manager, String groupName) throws XMLDBException;

    void removeGroupManager(String groupName, String manager) throws XMLDBException;

    /**
     * Retrieve a list of all existing accounts.
     *
     * @return The accounts.
     *
     * @throws XMLDBException if an error occurs whilst getting the accounts.
     */
    Account[] getAccounts() throws XMLDBException;

    Group getGroup(String name) throws XMLDBException;

    /**
     * Retrieve a list of all existing groups.
     *
     * Please note: new groups are created automatically if a new group
     * is assigned to a user. You can't add or remove them.
     *
     * @return List of all existing groups.
     *
     * @throws XMLDBException if an error occurs whilst getting the groups.
     */
    String[] getGroups() throws XMLDBException;

    /**
     * Get a property defined by this service.
     *
     * @param property the name of the property.
     *
     * @return The property value
     *
     * @throws XMLDBException if an error occurs whilst getting the property.
     */
    @Override
    String getProperty(String property) throws XMLDBException;

    /**
     * Set a property for this service.
     *
     * @param property The new property value
     * @param value    The new property value
     *
     * @throws XMLDBException if an error occurs whilst setting the property.
     */
    @Override
    void setProperty(String property, String value) throws XMLDBException;

    /**
     * Set the current collection for this service
     *
     * @param collection The new collection value
     *
     * @throws XMLDBException if an error occurs whilst setting the collection.
     */
    @Override
    void setCollection(Collection collection) throws XMLDBException;

    /**
     * Get permissions for the specified collections
     *
     * @param coll Description of the Parameter
     *
     * @return The permissions value
     *
     * @throws XMLDBException if an error occurs whilst getting the permissions.
     */
    Permission getPermissions(Collection coll) throws XMLDBException;

    /**
     * Get the permissions of the sub-collection.
     *
     * @param parent the parent collection
     * @param name the name of the sub-collection
     *
     * @return the permissions of the sub-collection.
     *
     * @throws XMLDBException if an error occurs whilst getting the permissions.
     */
    Permission getSubCollectionPermissions(Collection parent, String name) throws XMLDBException;

    /**
     * Get the permissions of the sub-resource.
     *
     * @param parent the parent collection
     * @param name the name of the sub-resource
     *
     * @return the permissions of the sub-resource.
     *
     * @throws XMLDBException if an error occurs whilst getting the permissions.
     */
    Permission getSubResourcePermissions(Collection parent, String name) throws XMLDBException;

    Date getSubCollectionCreationTime(Collection parent, String string) throws XMLDBException;

    /**
     * Get permissions for the specified resource
     *
     * @param res Description of the Parameter
     *
     * @return The permissions value
     *
     * @throws XMLDBException if an error occurs whilst getting the permissions.
     */
    Permission getPermissions(Resource res) throws XMLDBException;

    /**
     * Get permissions for all resources contained in the current
     * collection. Returns a list of permissions in the same order
     * as Collection.listResources().
     *
     * @return Permission[]
     *
     * @throws XMLDBException if an error occurs whilst listing the permissions.
     */
    Permission[] listResourcePermissions() throws XMLDBException;

    /**
     * Get permissions for all child collections contained in the current
     * collection. Returns a list of permissions in the same order
     * as Collection.listChildCollections().
     *
     * @return Permission[]
     *
     * @throws XMLDBException if an error occurs whilst listing the permissions.
     */
    Permission[] listCollectionPermissions() throws XMLDBException;

    /**
     * Delete a user from the database.
     *
     * @param account the user account.
     *
     * @throws XMLDBException if an error occurs whilst removing the account.
     */
    void removeAccount(Account account) throws XMLDBException;

    void removeGroup(Group group) throws XMLDBException;

    /**
     * Update the specified user without update user's password
     * Method added by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
     *
     * @param user the user
     *
     * @throws XMLDBException if an error occurs whilst adding the group.
     */
    void addUserGroup(Account user) throws XMLDBException;

    void removeGroupMember(final String group, final String account) throws XMLDBException;

    void addGroup(Group group) throws XMLDBException;

    @Deprecated //it'll removed after 1.6
    void addUser(User user) throws XMLDBException;

    @Deprecated //it'll removed after 1.6
    void updateUser(User user) throws XMLDBException;

    @Deprecated //it'll removed after 1.6
    User getUser(String name) throws XMLDBException;

    @Deprecated //it'll removed after 1.6
    User[] getUsers() throws XMLDBException;

    @Deprecated //it'll removed after 1.6
    void removeUser(User user) throws XMLDBException;

    @Deprecated //it'll removed after 1.6
    void lockResource(Resource res, User u) throws XMLDBException;

    String[] getGroupMembers(String groupName) throws XMLDBException;
}




