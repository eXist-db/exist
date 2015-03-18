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
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;

/**
 * Instantiates an appropriate Permission class based on the current configuration
 *
 * @author Adam Retter <adam@exist-db.org>
 */
public class PermissionFactory {

    private final static Logger LOG = LogManager.getLogger(PermissionFactory.class);

    public static SecurityManager sm = null;        //TODO The way this gets set is nasty AR

    /**
     * Get the Default Resource permissions for the current Subject
     * this includes incorporating their umask
     */
    public static Permission getDefaultResourcePermission() {
        
        //TODO consider loading Permission.DEFAULT_PERM from conf.xml instead
       
        final Subject currentSubject = sm.getDatabase().getSubject();
        final int mode = Permission.DEFAULT_RESOURCE_PERM & ~ currentSubject.getUserMask();
        
        return new SimpleACLPermission(sm, currentSubject.getId(), currentSubject.getDefaultGroup().getId(), mode);
    }
    
    /**
     * Get the Default Collection permissions for the current Subject
     * this includes incorporating their umask
     */
    public static Permission getDefaultCollectionPermission() {
        
        //TODO consider loading Permission.DEFAULT_PERM from conf.xml instead
        
        final Subject currentSubject = sm.getDatabase().getSubject();
        final int mode = Permission.DEFAULT_COLLECTION_PERM & ~ currentSubject.getUserMask();
        
        return new SimpleACLPermission(sm, currentSubject.getId(), currentSubject.getDefaultGroup().getId(), mode);
    }
    
    /**
     * Get permissions for the current Subject
     */
    public static Permission getPermission(int mode) {
        final Subject currentSubject = sm.getDatabase().getSubject();
        return new SimpleACLPermission(sm, currentSubject.getId(), currentSubject.getDefaultGroup().getId(), mode);
    }
    
    /**
     * Get permissions for the user, group and mode
     */
    public static Permission getPermission(int userId, int groupId, int mode) {
        return new SimpleACLPermission(sm, userId, groupId, mode);
    }

    public static Permission getPermission(String userName, String groupName, int mode) {
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

    public interface PermissionModifier {
        public void modify(Permission permission) throws PermissionDeniedException;
    }

    public static void updatePermissions(DBBroker broker, XmldbURI pathUri, PermissionModifier permissionModifier) throws PermissionDeniedException {
        DocumentImpl doc = null;
        final TransactionManager transact = broker.getBrokerPool().getTransactionManager();
        try(final Txn transaction = transact.beginTransaction()) {
            final Collection collection = broker.openCollection(pathUri, Lock.WRITE_LOCK);
            if (collection == null) {
                doc = broker.getXMLResource(pathUri, Lock.WRITE_LOCK);
                if(doc == null) {
                    transact.abort(transaction);
                    throw new XPathException("Resource or collection '" + pathUri.toString() + "' does not exist.");
                }

                final Permission permissions = doc.getPermissions();
                permissionModifier.modify(permissions);

                broker.storeXMLResource(transaction, doc);
                transact.commit(transaction);
                broker.flush();
            } else {
                // keep the write lock in the transaction
                transaction.registerLock(collection.getLock(), Lock.WRITE_LOCK);

                final Permission permissions = collection.getPermissionsNoLock();
                permissionModifier.modify(permissions);

                broker.saveCollection(transaction, collection);
                transact.commit(transaction);
                broker.flush();
            }
        } catch(final XPathException | PermissionDeniedException | IOException | TriggerException | TransactionException e) {
            throw new PermissionDeniedException("Permission to modify permissions is denied for user '" + broker.getSubject().getName() + "' on '" + pathUri.toString() + "': " + e.getMessage(), e);
        } finally {
            if(doc != null) {
                doc.getUpdateLock().release(Lock.WRITE_LOCK);
            }
        }
    }
}