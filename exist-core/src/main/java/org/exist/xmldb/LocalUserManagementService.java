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

import java.util.*;

import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.*;
import org.exist.security.SecurityManager;
import org.exist.security.internal.aider.ACEAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import com.evolvedbinary.j8fu.function.FunctionE;
import org.exist.storage.lock.ManagedDocumentLock;
import org.exist.xmldb.function.LocalXmldbCollectionFunction;
import org.exist.xmldb.function.LocalXmldbDocumentFunction;
import org.exist.xmldb.function.LocalXmldbFunction;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

import javax.annotation.Nullable;

/**
 * Local Implementation (i.e. embedded) of an eXist-specific service
 * which provides methods to manage users and
 * permissions.
 *
 * @author <a href="mailto:meier@ifs.tu-darmstadt.de">Wolfgang Meier</a>
 * @author <a href="mailto:adam@exist-db.org">Modified by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
 * @author Adam Retter</a>
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
            PermissionFactory.chown(broker, document, Optional.of(perm.getOwner().getName()), Optional.of(perm.getGroup().getName()));
            PermissionFactory.chmod(broker, document, Optional.of(perm.getMode()), getAces(perm));
            return null;
        });
    }

    @Override
    public void setPermissions(final Collection child, final Permission perm) throws XMLDBException {
        withDb((broker, transaction) -> {
            final XmldbURI childUri = getCollectionUri(broker, transaction, child);
            updateCollection(broker, transaction, childUri).apply((collection, broker1, transaction1) -> {
                PermissionFactory.chown(broker, collection, Optional.of(perm.getOwner().getName()), Optional.of(perm.getGroup().getName()));
                PermissionFactory.chmod(broker, collection, Optional.of(perm.getMode()), getAces(perm));
                return null;
            });
            return null;
        });
    }
    
    @Override
    public void setPermissions(final Collection child, final String owner, final String group, final int mode, final List<ACEAider> aces) throws XMLDBException {
        withDb((broker, transaction) -> {
            final XmldbURI childUri = getCollectionUri(broker, transaction, child);
            updateCollection(broker, transaction, childUri).apply((collection, broker1, transaction1) -> {
                final Permission permission = collection.getPermissionsNoLock();
                PermissionFactory.chown(broker, collection, Optional.ofNullable(owner), Optional.ofNullable(group));
                PermissionFactory.chmod(broker, collection, Optional.of(mode), Optional.ofNullable(aces));
                return null;
            });
            return null;
        });
    }

    private Optional<List<ACEAider>> getAces(@Nullable final Permission permission) {
        final Optional<List<ACEAider>> maybeAces;
        if (permission != null && permission instanceof ACLPermission) {
            final ACLPermission aclPerm = (ACLPermission)permission;
            final List<ACEAider> aces = new ArrayList<>(aclPerm.getACECount());
            for (int i = 0; i < aclPerm.getACECount(); i++) {
                aces.add(new ACEAider(aclPerm.getACEAccessType(i), aclPerm.getACETarget(i), aclPerm.getACEWho(i), aclPerm.getACEMode(i)));
            }
            maybeAces = Optional.of(aces);
        } else {
            maybeAces = Optional.empty();
        }
        return maybeAces;
    }

    @Override
    public void setPermissions(final Resource resource, final String owner, final String group, final int mode, final List<ACEAider> aces) throws XMLDBException {
        modify(resource).apply((document, broker, transaction) -> {
            PermissionFactory.chown(broker, document, Optional.ofNullable(owner), Optional.ofNullable(group));
            PermissionFactory.chmod(broker, document, Optional.of(mode), Optional.ofNullable(aces));
            return null;
        });
    }

    @Override
    public void chmod(final String modeStr) throws XMLDBException {
        final XmldbURI collUri = collection.getPathURI();
        updateCollection(collUri).apply((collection, broker, transaction) -> {
            PermissionFactory.chmod_str(broker, collection, Optional.ofNullable(modeStr), Optional.empty());
            return null;
        });
    }

    @Override
    public void chmod(final Resource resource, final int mode) throws XMLDBException {
        modify(resource).apply((document, broker, transaction) -> {
            PermissionFactory.chmod(broker, document, Optional.of(mode), Optional.empty());
            return null;
        });
    }

    @Override
    public void chmod(final int mode) throws XMLDBException {
        final XmldbURI collUri = collection.getPathURI();
        updateCollection(collUri).apply((collection, broker, transaction) -> {
            PermissionFactory.chmod(broker, collection, Optional.of(mode), Optional.empty());
            return null;
        });
    }

    @Override
    public void chmod(final Resource resource, final String modeStr) throws XMLDBException {
        modify(resource).apply((document, broker, transaction) -> {
            PermissionFactory.chmod_str(broker, document, Optional.ofNullable(modeStr), Optional.empty());
            return null;
        });
    }

    @Override
    public void chgrp(final String group) throws XMLDBException {
        final XmldbURI collUri = collection.getPathURI();
        updateCollection(collUri).apply((collection, broker, transaction) -> {
            PermissionFactory.chown(broker, collection, Optional.empty(), Optional.ofNullable(group));
            return null;
        });
    }

    @Override
    public void chown(final Account u) throws XMLDBException {
        final XmldbURI collUri = collection.getPathURI();
        updateCollection(collUri).apply((collection, broker, transaction) -> {
            PermissionFactory.chown(broker, collection, Optional.ofNullable(u).map(Account::getName), Optional.empty());
            return null;
        });
    }

    @Override
    public void chown(final Account u, final String group) throws XMLDBException {
        final XmldbURI collUri = collection.getPathURI();
        updateCollection(collUri).apply((collection, broker, transaction) -> {
            PermissionFactory.chown(broker, collection, Optional.ofNullable(u).map(Account::getName), Optional.ofNullable(group));
            return null;
        });
    }

    @Override
    public void chgrp(final Resource resource, final String group) throws XMLDBException {
        modify(resource).apply((document, broker, transaction) -> {
            PermissionFactory.chown(broker, document, Optional.empty(), Optional.ofNullable(group));
            return null;
        });
    }

    @Override
    public void chown(final Resource resource, final Account u) throws XMLDBException {
        modify(resource).apply((document, broker, transaction) -> {
            PermissionFactory.chown(broker, document, Optional.ofNullable(u).map(Account::getName), Optional.empty());
            return null;
        });
    }

    @Override
    public void chown(final Resource resource, final Account u, final String group) throws XMLDBException {
        modify(resource).apply((document, broker, transaction) -> {
            PermissionFactory.chown(broker, document, Optional.ofNullable(u).map(Account::getName), Optional.ofNullable(group));
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
            if (!collection.getPermissionsNoLock().validate(user, Permission.READ)) {
                return new Permission[0];
            }

            final Permission perms[] = new Permission[collection.getDocumentCount(broker)];
            final Iterator<DocumentImpl> itDocument = collection.iterator(broker);
            int i = 0;
            while (itDocument.hasNext()) {
                final DocumentImpl document = itDocument.next();
                try(final ManagedDocumentLock documentLock = broker.getBrokerPool().getLockManager().acquireDocumentReadLock(document.getURI())) {
                    perms[i++] = document.getPermissions();
                }
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

    /**
     * Higher-order-function for updating a collection and its metadata
     *
     * @param broker
     * @param transaction
     * @param collectionUri The collection to perform read/write operations on
     */
    private <R> FunctionE<LocalXmldbCollectionFunction<R>, R, XMLDBException> updateCollection(final DBBroker broker, final Txn transaction, final XmldbURI collectionUri) throws XMLDBException {
        return updateOp -> this.<R>modify(broker, transaction, collectionUri).apply((collection, broker1, transaction1) -> {
            final R result = updateOp.apply(collection, broker1, transaction1);
            broker1.saveCollection(transaction1, collection);
            return result;
        });
    }
}
