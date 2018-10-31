/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2011 The eXist-db Project
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

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import com.evolvedbinary.j8fu.function.ConsumerE;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;

/**
 * Instantiates an appropriate Permission class based on the current configuration
 *
 * @author Adam Retter <adam@exist-db.org>
 */
public class PermissionFactory {

    private final static Logger LOG = LogManager.getLogger(PermissionFactory.class);

    /**
     * Get the Default Resource permissions for the current Subject
     * this includes incorporating their umask
     */
    public static Permission getDefaultResourcePermission(final SecurityManager sm) {
        
        //TODO consider loading Permission.DEFAULT_PERM from conf.xml instead

        final Subject currentSubject = sm.getDatabase().getActiveBroker().getCurrentSubject();
        final int mode = Permission.DEFAULT_RESOURCE_PERM & ~ currentSubject.getUserMask();
        
        return new SimpleACLPermission(sm, currentSubject.getId(), currentSubject.getDefaultGroup().getId(), mode);
    }
    
    /**
     * Get the Default Collection permissions for the current Subject
     * this includes incorporating their umask
     */
    public static Permission getDefaultCollectionPermission(final SecurityManager sm) {
        
        //TODO consider loading Permission.DEFAULT_PERM from conf.xml instead
        
        final Subject currentSubject = sm.getDatabase().getActiveBroker().getCurrentSubject();
        final int mode = Permission.DEFAULT_COLLECTION_PERM & ~ currentSubject.getUserMask();
        
        return new SimpleACLPermission(sm, currentSubject.getId(), currentSubject.getDefaultGroup().getId(), mode);
    }
    
    /**
     * Get permissions for the current Subject
     */
    public static Permission getPermission(final SecurityManager sm, final int mode) {
        final Subject currentSubject = sm.getDatabase().getActiveBroker().getCurrentSubject();
        return new SimpleACLPermission(sm, currentSubject.getId(), currentSubject.getDefaultGroup().getId(), mode);
    }
    
    /**
     * Get permissions for the user, group and mode
     */
    public static Permission getPermission(final SecurityManager sm, final int userId, final int groupId, final int mode) {
        return new SimpleACLPermission(sm, userId, groupId, mode);
    }

    public static Permission getPermission(final SecurityManager sm, final String userName, final String groupName, final int mode) {
        Permission permission = null;
        try {
            final Account owner = sm.getAccount(userName);
            if(owner == null) {
                throw new IllegalArgumentException("User was not found '" + (userName == null ? "" : userName) + "'");
            }

            final Group group = sm.getGroup(groupName);
            if(group == null) {
        	    throw new IllegalArgumentException("Group was not found '" + (userName == null ? "" : groupName) + "'");
            }

            permission = new SimpleACLPermission(sm, owner.getId(), group.getId(), mode);
        } catch(final Throwable ex) {
        	LOG.error("Exception while instantiating security permission class.", ex);
        }
        return permission;
    }

    public static void updatePermissions(final DBBroker broker, final XmldbURI pathUri, final ConsumerE<Permission, PermissionDeniedException> permissionModifier) throws PermissionDeniedException {
        final TransactionManager transact = broker.getBrokerPool().getTransactionManager();
        try(final Txn transaction = transact.beginTransaction()) {
            Collection collection = null;
            try {
                collection = broker.openCollection(pathUri, LockMode.WRITE_LOCK);
                if (collection == null) {
                    DocumentImpl doc = null;
                    try {
                        doc = broker.getXMLResource(pathUri, LockMode.WRITE_LOCK);
                        if (doc == null) {
                            transact.abort(transaction);
                            throw new XPathException("Resource or collection '" + pathUri.toString() + "' does not exist.");
                        }

//                        // keep a write lock in the transaction
//                        transaction.acquireLock(doc.getUpdateLock(), LockMode.WRITE_LOCK);

                        final Permission permissions = doc.getPermissions();
                        permissionModifier.accept(permissions);

                        broker.storeXMLResource(transaction, doc);
                    } finally {
                        if(doc != null) {
                            doc.getUpdateLock().release(LockMode.WRITE_LOCK);
                        }
                    }
                    transact.commit(transaction);
                    broker.flush();
                } else {
//                    // keep a write lock in the transaction
//                    transaction.acquireLock(collection.getLock(), LockMode.WRITE_LOCK);

                    final Permission permissions = collection.getPermissionsNoLock();
                    permissionModifier.accept(permissions);

                    broker.saveCollection(transaction, collection);
                    transact.commit(transaction);
                    broker.flush();
                }
            } finally {
                if(collection != null) {
                    collection.release(LockMode.WRITE_LOCK);
                }
            }
        } catch(final XPathException | PermissionDeniedException | IOException | TriggerException | TransactionException e) {
            throw new PermissionDeniedException("Permission to modify permissions is denied for user '" + broker.getCurrentSubject().getName() + "' on '" + pathUri.toString() + "': " + e.getMessage(), e);
        }
    }
}