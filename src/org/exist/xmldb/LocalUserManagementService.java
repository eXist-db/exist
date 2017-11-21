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
import java.util.Iterator;
import java.util.List;

import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.ACLPermission;
import org.exist.security.Group;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.security.Account;
import org.exist.security.User;
import org.exist.security.SecurityManager;
import org.exist.security.internal.aider.ACEAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.BrokerPool;
import com.evolvedbinary.j8fu.function.FunctionE;
import org.exist.xmldb.function.LocalXmldbCollectionFunction;
import org.exist.xmldb.function.LocalXmldbDocumentFunction;
import org.exist.xmldb.function.LocalXmldbFunction;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

/**
 * Local Implementation (i.e. embedded) of an eXist-specific service
 * which provides methods to manage users and
 * permissions.
 *
 * @author Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 * @author Modified by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
 * @author Adam Retter <adam@exist-db.org>
 */
public class LocalUserManagementService extends AbstractLocalService implements EXistUserManagementService {

    public LocalUserManagementService(final Subject user, final BrokerPool pool, final LocalCollection collection) {
        super(user, pool, collection);
    }
    
    @Override
    public String getName() {
        return "UserManagementService";
    }
    
    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public void addAccount(final Account u) throws XMLDBException {
        onlyAsAdmin(user).apply(manager -> {
            if (manager.hasAccount(u.getName())) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "user " + u.getName() + " already exists");
            } else {
                return (broker, transaction) -> manager.addAccount(u);
            }
        });
    }

    @Override
    public void addGroup(final Group group) throws XMLDBException {
        onlyAsAdmin(user).apply(manager -> {
            if (manager.hasGroup(group.getName())) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "group '" + group.getName() + "' already exists");
            } else {
                return (broker, transaction) -> manager.addGroup(broker, group);
            }
        });
    }

    @Override
    public void setUserPrimaryGroup(final String username, final String groupName) throws XMLDBException {
        onlyAsAdmin(user).apply(manager -> {
            if (!manager.hasGroup(groupName)) {
                throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, "Group '" + groupName + "' does not exist!");
            } else {
                return (broker, transaction) -> {
                    final Account account = manager.getAccount(username);
                    final Group group = manager.getGroup(groupName);
                    account.setPrimaryGroup(group);
                    return manager.updateAccount(account);
                };
            }
        });
    }
    
    @Override
    public void setPermissions(final Resource resource, final Permission perm) throws XMLDBException {
        modify(resource).apply((document, broker, transaction) -> {
            document.setPermissions(perm);
            return null;
        });
    }

    @Override
    public void setPermissions(final Collection child, final Permission perm) throws XMLDBException {
        final XmldbURI childUri = XmldbURI.create(child.getName());
        updateCollection(childUri).apply((collection, broker, transaction) -> {
            collection.setPermissions(perm);
            return null;
        });
    }
    
    @Override
    public void setPermissions(final Collection child, final String owner, final String group, final int mode, final List<ACEAider> aces) throws XMLDBException {
        final XmldbURI childUri = XmldbURI.create(child.getName());
        updateCollection(childUri).apply((collection, broker, transaction) -> {
            final Permission permission = collection.getPermissionsNoLock();
            permission.setOwner(owner);
            permission.setGroup(group);
            permission.setMode(mode);
            if (permission instanceof ACLPermission) {
                final ACLPermission aclPermission = (ACLPermission) permission;
                aclPermission.clear();
                for (final ACEAider ace : aces) {
                    aclPermission.addACE(ace.getAccessType(), ace.getTarget(), ace.getWho(), ace.getMode());
                }
            }
            return null;
        });
    }
        
    @Override
    public void setPermissions(final Resource resource, final String owner, final String group, final int mode, final List<ACEAider> aces) throws XMLDBException {
        modify(resource).apply((document, broker, transaction) -> {
            final Permission permission = document.getPermissions();
            permission.setOwner(owner);
            permission.setGroup(group);
            permission.setMode(mode);
            if (permission instanceof ACLPermission) {
                final ACLPermission aclPermission = (ACLPermission) permission;
                aclPermission.clear();
                for (final ACEAider ace : aces) {
                    aclPermission.addACE(ace.getAccessType(), ace.getTarget(), ace.getWho(), ace.getMode());
                }
            }
            return null;
        });
    }

    @Override
    public void chmod(final String modeStr) throws XMLDBException {
        final XmldbURI collUri = collection.getPathURI();
        updateCollection(collUri).apply((collection, broker, transaction) -> {
            collection.setPermissions(modeStr);
            return null;
        });
    }

    @Override
    public void chmod(final Resource resource, final int mode) throws XMLDBException {
        modify(resource).apply((document, broker, transaction) -> {
            document.getPermissions().setMode(mode);
            return null;
        });
    }

    @Override
    public void chmod(final int mode) throws XMLDBException {
        final XmldbURI collUri = collection.getPathURI();
        updateCollection(collUri).apply((collection, broker, transaction) -> {
            collection.setPermissions(mode);
            return null;
        });
    }

    @Override
    public void chmod(final Resource resource, final String modeStr) throws XMLDBException {
        modify(resource).apply((document, broker, transaction) -> {
            document.getPermissions().setMode(modeStr);
            return null;
        });
    }

    @Override
    public void chgrp(final String group) throws XMLDBException {
        final XmldbURI collUri = collection.getPathURI();
        updateCollection(collUri).apply((collection, broker, transaction) -> {
            final Permission permission = collection.getPermissionsNoLock();
            permission.setGroup(group);
            return null;
        });
    }

    @Override
    public void chown(final Account u) throws XMLDBException {
        final XmldbURI collUri = collection.getPathURI();
        updateCollection(collUri).apply((collection, broker, transaction) -> {
            final Permission permission = collection.getPermissionsNoLock();
            permission.setOwner(u);
            return null;
        });
    }

    @Override
    public void chown(final Account u, final String group) throws XMLDBException {
        final XmldbURI collUri = collection.getPathURI();
        updateCollection(collUri).apply((collection, broker, transaction) -> {
            final Permission permission = collection.getPermissionsNoLock();
            permission.setOwner(u);
            permission.setGroup(group);
            return null;
        });
    }

    @Override
    public void chgrp(final Resource resource, final String group) throws XMLDBException {
        modify(resource).apply((document, broker, transaction) -> {
            document.getPermissions().setGroup(group);
            return null;
        });
    }

    @Override
    public void chown(final Resource resource, final Account u) throws XMLDBException {
        modify(resource).apply((document, broker, transaction) -> {
            document.getPermissions().setOwner(u);
            return null;
        });
    }

    @Override
    public void chown(final Resource resource, final Account u, final String group) throws XMLDBException {
        modify(resource).apply((document, broker, transaction) -> {
            document.getPermissions().setOwner(u);
            document.getPermissions().setGroup(group);
            return null;
        });
    }

    @Override
    public String hasUserLock(final Resource resource) throws XMLDBException {
        return withDb((broker, transaction) -> ((AbstractEXistResource)resource).<String>read(broker, transaction).apply((document, broker1, transaction1) -> {
            final Account lockOwner = document.getUserLock();
            return lockOwner == null ? null : lockOwner.getName();
        }));
    }
	
    @Override
    public void lockResource(final Resource resource, final Account u) throws XMLDBException {
        modify(resource).apply((document, broker, transaction) -> {
            final String resourceId = resource.getId();
            if (!document.getPermissions().validate(user, Permission.WRITE)) {
                throw new PermissionDeniedException("User is not allowed to lock resource " + resourceId);
            }

            final SecurityManager manager = broker.getBrokerPool().getSecurityManager();
            if (!(user.equals(u) || manager.hasAdminPrivileges(user))) {
                throw new PermissionDeniedException("User " + user.getName() + " is not allowed to lock resource '" + resourceId + "' for user " + u.getName());
            }

            final Account lockOwner = document.getUserLock();

            if (lockOwner != null) {
                if (lockOwner.equals(u)) {
                    return null;
                } else if (!manager.hasAdminPrivileges(user)) {
                    throw new PermissionDeniedException("Resource '" + resourceId + "' is already locked by user " + lockOwner.getName());
                }
            }

            document.setUserLock(u);

            return null;
        });
    }
	
    @Override
    public void unlockResource(final Resource resource) throws XMLDBException {
        modify(resource).apply((document, broker, transaction) -> {
            final String resourceId = resource.getId();
            if (!document.getPermissions().validate(user, Permission.WRITE)) {
                throw new PermissionDeniedException("User is not allowed to lock resource '" + resourceId + "'");
            }

            final Account lockOwner = document.getUserLock();

            final SecurityManager manager = broker.getBrokerPool().getSecurityManager();
            if (lockOwner != null && !(lockOwner.equals(user) || manager.hasAdminPrivileges(user))) {
                throw new PermissionDeniedException("Resource '" + resourceId + "' is already locked by user " + lockOwner.getName());
            }

            document.setUserLock(null);

            return null;
        });
    }

    @Override
    public Permission getPermissions(final Collection coll) throws XMLDBException {
        if(coll instanceof LocalCollection) {
            return this.<Permission>read(((LocalCollection) coll).getPathURI()).apply((collection, broker, transaction) -> collection.getPermissionsNoLock());
        }
        return null;
    }

    @Override
    public Permission getSubCollectionPermissions(final Collection parent, final String name) throws XMLDBException {
        if(parent instanceof LocalCollection) {
            return this.<Permission>read(((LocalCollection) parent).getPathURI()).apply((collection, broker, transaction) -> collection.getChildCollectionEntry(broker, name).getPermissions());
        } else {
            return null;
        }
    }

    @Override
    public Permission getSubResourcePermissions(final Collection parent, final String name) throws XMLDBException {
        if(parent instanceof LocalCollection) {
            return this.<Permission>read(((LocalCollection) parent).getPathURI()).apply((collection, broker, transaction) -> collection.getResourceEntry(broker, name).getPermissions());
        } else {
            return null;
        }
    }

    @Override
    public Date getSubCollectionCreationTime(final Collection parent, final String name) throws XMLDBException {
        if(parent instanceof LocalCollection) {
            return this.<Date>read(((LocalCollection) parent).getPathURI()).apply((collection, broker, transaction) -> new Date(collection.getChildCollectionEntry(broker, name).getCreated()));
        } else {
            return null;
        }
    }

    @Override
    public Permission getPermissions(final Resource resource) throws XMLDBException {
        return withDb((broker, transaction) -> ((AbstractEXistResource)resource).<Permission>read(broker, transaction).apply((document, broker1, transaction1) -> document.getPermissions()));
    }

    @Override
    public Permission[] listResourcePermissions() throws XMLDBException {
        final XmldbURI collectionUri = collection.getPathURI();
        return this.<Permission[]>read(collectionUri).apply((collection, broker, transaction) -> {
            if(!collection.getPermissionsNoLock().validate(user, Permission.READ)) {
                return new Permission[0];
            }

            final Permission perms[] = new Permission[collection.getDocumentCount(broker)];
            final Iterator<DocumentImpl> itDocument = collection.iterator(broker);
            int i = 0;
            while(itDocument.hasNext()) {
                final DocumentImpl document = itDocument.next();
                perms[i++] = document.getPermissions();
            }

            return perms;
        });
    }

    @Override
    public Permission[] listCollectionPermissions() throws XMLDBException {
        final XmldbURI collectionUri = collection.getPathURI();
        return this.<Permission[]>read(collectionUri).apply((collection, broker, transaction) -> {
            if(!collection.getPermissionsNoLock().validate(user, Permission.READ)) {
                return new Permission[0];
            }

            final Permission perms[] = new Permission[collection.getChildCollectionCount(broker)];
            final Iterator<XmldbURI> itChildCollectionUri = collection.collectionIterator(broker);
            int i = 0;
            while(itChildCollectionUri.hasNext()) {
                final XmldbURI childCollectionUri = collectionUri.append(itChildCollectionUri.next());
                final Permission childPermission = this.<Permission>read(broker, transaction, childCollectionUri).apply((childCollection, broker1, transaction1) -> childCollection.getPermissionsNoLock());
                perms[i++] = childPermission;
            }

            return perms;
        });
    }

    @Override
    public Account getAccount(final String name) throws XMLDBException {
        return withDb((broker, transaction) -> {
            final SecurityManager sm = broker.getBrokerPool().getSecurityManager();
            return sm.getAccount(name);
        });
    }

    @Override
    public Account[] getAccounts() throws XMLDBException {
        return withDb((broker, transaction) -> {
            final SecurityManager sm = broker.getBrokerPool().getSecurityManager();
            final java.util.Collection<Account> users = sm.getUsers();
            return users.toArray(new Account[users.size()]);
        });
    }

    @Override
    public Group getGroup(final String name) throws XMLDBException {
        return withDb((broker, transaction) -> {
            final SecurityManager sm = broker.getBrokerPool().getSecurityManager();
            return sm.getGroup(name);
        });
    }

    @Override
    public String[] getGroups() throws XMLDBException {
        return withDb((broker, transaction) -> {
            final SecurityManager sm = broker.getBrokerPool().getSecurityManager();
            final java.util.Collection<Group> groups = sm.getGroups();
            final String[] groupNames = new String[groups.size()];
            int i = 0;
            for (final Group group : groups) {
                groupNames[i++] = group.getName();
            }
            return groupNames;
        });
    }

    @Override
    public void removeAccount(final Account u) throws XMLDBException {
        onlyAsAdmin(user).apply(manager -> (broker, transaction) -> {
            manager.deleteAccount(u);
            return null;
        });
    }

    @Override
    public void removeGroup(final Group group) throws XMLDBException {
        withDb((broker, transaction) -> {
            final SecurityManager sm = broker.getBrokerPool().getSecurityManager();
            sm.deleteGroup(group.getName());
            return null;
        });
    }

    @Override
    public void updateAccount(final Account u) throws XMLDBException {
        withDb((broker, transaction) -> {
            final SecurityManager sm = broker.getBrokerPool().getSecurityManager();
            sm.updateAccount(u);
            return null;
        });
    }
    
    @Override
    public void updateGroup(final Group g) throws XMLDBException {
        withDb((broker, transaction) -> {
            final SecurityManager sm = broker.getBrokerPool().getSecurityManager();
            sm.updateGroup(g);
            return null;
        });
    }

    @Override
    public String[] getGroupMembers(final String groupName) throws XMLDBException {
        final List<String> groupMembers = withDb((broker, transaction) -> {
            final SecurityManager sm = broker.getBrokerPool().getSecurityManager();
            return sm.findAllGroupMembers(groupName);
        });
        return groupMembers.toArray(new String[groupMembers.size()]);
    }
    
    @Override
    public void addAccountToGroup(final String accountName, final String groupName) throws XMLDBException {
        withDb((broker, transaction) -> {
            final SecurityManager sm = broker.getBrokerPool().getSecurityManager();
            final Account account = sm.getAccount(accountName);
            account.addGroup(groupName);
            sm.updateAccount(account);
            return null;
        });
    }
    
    @Override
    public void addGroupManager(final String manager, final String groupName) throws XMLDBException {
        withDb((broker, transaction) -> {
            final SecurityManager sm = broker.getBrokerPool().getSecurityManager();
            final Account account = sm.getAccount(manager);
            final Group group = sm.getGroup(groupName);
            group.addManager(account);
            sm.updateGroup(group);
            return null;
        });
    }
    
    @Override
    public void removeGroupManager(final String groupName, final String manager) throws XMLDBException {
        withDb((broker, transaction) -> {
            final SecurityManager sm = broker.getBrokerPool().getSecurityManager();
            final Group group = sm.getGroup(groupName);
            final Account account = sm.getAccount(manager);
            group.removeManager(account);
            sm.updateGroup(group);
            return null;
        });
    }
	
    @Override
    public void addUserGroup(final Account user) throws XMLDBException {
        throw new UnsupportedOperationException();
    }
	
    @Override
    public void removeGroupMember(final String group, final String member) throws XMLDBException {
        withDb((broker, transaction) -> {
            final SecurityManager sm = broker.getBrokerPool().getSecurityManager();
            final Account account = sm.getAccount(member);
            account.remGroup(group);
            sm.updateAccount(account);
            return null;
        });
    }

    @Override
    public void addUser(final User user) throws XMLDBException {
        final Account account = new UserAider(user.getName());
        addAccount(account);
    }

    @Override
    public void updateUser(final User user) throws XMLDBException {
        final Account account = new UserAider(user.getName());
        account.setPassword(user.getPassword());
        //TODO: groups
        updateAccount(account);
    }

    @Override
    public User getUser(final String name) throws XMLDBException {
        return getAccount(name);
    }

    @Override
    public User[] getUsers() throws XMLDBException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void removeUser(final User user) throws XMLDBException {
        // TODO Auto-generated method stub	
    }

    @Override
    public void lockResource(final Resource res, final User u) throws XMLDBException {
        final Account account = new UserAider(u.getName());
        lockResource(res, account);
    }
        
    @Override
    public String getProperty(final String property) throws XMLDBException {
        return null;
    }
    
    @Override
    public void setProperty(final String property, final String value) throws XMLDBException {
    }

    /**
     * Executes a LocalXmldbFunction only if the supplied user is an Admin user
     *
     * @param user A user to be tested as an admin user
     */
    private <R> FunctionE<FunctionE<SecurityManager, LocalXmldbFunction<R>, XMLDBException>, R, XMLDBException> onlyAsAdmin(final Subject user) throws XMLDBException {
        final SecurityManager manager = brokerPool.getSecurityManager();
        if (!manager.hasAdminPrivileges(user)) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, " This operation is restricted to Admin users");
        } else {
            return op -> op.andThen((FunctionE<LocalXmldbFunction<R>, R, XMLDBException>)this::withDb).apply(manager);
        }
    }

    /**
     * Higher-order-function for performing read/write operations against a database
     * resource
     *
     * @param resource The resource to perform read/write operations on
     */
    private <R> FunctionE<LocalXmldbDocumentFunction<R>, R, XMLDBException> modify(final Resource resource) throws XMLDBException {
        return modifyOp -> withDb((broker, transaction) -> (((AbstractEXistResource)resource).<R>modify(broker, transaction).apply(modifyOp)));
    }
    
    /**
     * Higher-order-function for updating a collection and its metadata
     *
     * @param collectionUri The collection to perform read/write operations on
     */
    private <R> FunctionE<LocalXmldbCollectionFunction<R>, R, XMLDBException> updateCollection(final XmldbURI collectionUri) throws XMLDBException {
        return updateOp -> this.<R>modify(collectionUri).apply((collection, broker1, transaction1) -> {
            final R result = updateOp.apply(collection, broker1, transaction1);
            broker1.saveCollection(transaction1, collection);
            return result;
        });
    }
}
