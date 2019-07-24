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
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.evolvedbinary.j8fu.Either;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.internal.aider.ACEAider;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;
import com.evolvedbinary.j8fu.function.ConsumerE;
import org.exist.util.SyntaxException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;

import static org.exist.security.AbstractUnixStylePermission.SIMPLE_SYMBOLIC_MODE_PATTERN;
import static org.exist.security.AbstractUnixStylePermission.UNIX_SYMBOLIC_MODE_PATTERN;
import static org.exist.security.Permission.*;
import static org.exist.storage.DBBroker.POSIX_CHOWN_RESTRICTED_PROPERTY;

/**
 * Instantiates an appropriate Permission class based on the current configuration
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class PermissionFactory {

    private final static Logger LOG = LogManager.getLogger(PermissionFactory.class);

    /**
     * Get the Default Resource permissions for the current Subject
     * this includes incorporating their umask
     * @param sm the security manager
     * @return a new Permission object, that the caller is free to modify.
     */
    public static Permission getDefaultResourcePermission(final SecurityManager sm) {
        
        //TODO(AR) consider loading Permission.DEFAULT_PERM from conf.xml instead

        final Subject currentSubject = sm.getDatabase().getActiveBroker().getCurrentSubject();
        final int mode = Permission.DEFAULT_RESOURCE_PERM & ~ currentSubject.getUserMask();
        
        return new SimpleACLPermission(sm, currentSubject.getId(), currentSubject.getDefaultGroup().getId(), mode);
    }
    
    /**
     * Get the Default Collection permissions for the current Subject
     * this includes incorporating their umask.
     *
     * @param sm the security manager.
     *
     * @return a new Permission object, that the caller is free to modify.
     */
    public static Permission getDefaultCollectionPermission(final SecurityManager sm) {
        
        //TODO(AR) consider loading Permission.DEFAULT_PERM from conf.xml instead
        
        final Subject currentSubject = sm.getDatabase().getActiveBroker().getCurrentSubject();
        final int mode = Permission.DEFAULT_COLLECTION_PERM & ~ currentSubject.getUserMask();
        
        return new SimpleACLPermission(sm, currentSubject.getId(), currentSubject.getDefaultGroup().getId(), mode);
    }
    
    /**
     * Get permissions for the current Subject
     * @param sm the security manager.
     * @param mode mode for the resource.
     * @return a new Permission object, that the caller is free to modify.
     */
    public static Permission getPermission(final SecurityManager sm, final int mode) {
        final Subject currentSubject = sm.getDatabase().getActiveBroker().getCurrentSubject();
        return new SimpleACLPermission(sm, currentSubject.getId(), currentSubject.getDefaultGroup().getId(), mode);
    }
    
    /**
     * Get permissions for the user, group and mode
     * @param sm the security manager.
     * @param userId id of the user
     * @param groupId id of the group
     * @param mode mode for the resource.
     * @return a new Permission object, that the caller is free to modify.

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

    private static void updatePermissions(final DBBroker broker, final Txn transaction, final XmldbURI pathUri, final ConsumerE<Permission, PermissionDeniedException> permissionModifier) throws PermissionDeniedException {
        final BrokerPool brokerPool = broker.getBrokerPool();
        try {
            try(final Collection collection = broker.openCollection(pathUri, LockMode.WRITE_LOCK)) {
                if (collection == null) {
                    try(final LockedDocument lockedDoc = broker.getXMLResource(pathUri, LockMode.WRITE_LOCK)) {

                        if (lockedDoc == null) {
                            throw new XPathException("Resource or collection '" + pathUri.toString() + "' does not exist.");
                        }

                        final DocumentImpl doc = lockedDoc.getDocument();

//                        // keep a write lock in the transaction
//                        transaction.acquireDocumentLock(() -> brokerPool.getLockManager().acquireDocumentWriteLock(doc.getURI()));


                        final Permission permissions = doc.getPermissions();
                        permissionModifier.accept(permissions);

                        broker.storeXMLResource(transaction, doc);
                    }
                } else {
//                    // keep a write lock in the transaction
//                    transaction.acquireCollectionLock(() -> brokerPool.getLockManager().acquireCollectionWriteLock(collection.getURI()));

                    final Permission permissions = collection.getPermissionsNoLock();
                    permissionModifier.accept(permissions);

                    broker.saveCollection(transaction, collection);
                }
                broker.flush();
            }
        } catch(final XPathException | PermissionDeniedException | IOException e) {
            throw new PermissionDeniedException("Permission to modify permissions is denied for user '" + broker.getCurrentSubject().getName() + "' on '" + pathUri.toString() + "': " + e.getMessage(), e);
        }
    }

    /**
     * Changes the ownership of a resource in the database
     * inline with the rules of POSIX.1-2017 (Issue 7, 2018 edition).
     *
     * Note - this function will persist the updated permissions of the database resource.
     *
     * @param broker the database broker.
     * @param transaction the database transaction;
     * @param pathUri the URI to a resource in the database.
     * @param owner the new owner for the resource.
     * @param group thr new group for the resource.
     *
     * @throws PermissionDeniedException if the calling process has insufficient permissions.
     */
    public static void chown(final DBBroker broker, final Txn transaction, final XmldbURI pathUri, final Optional<String> owner, final Optional<String> group) throws PermissionDeniedException {
        updatePermissions(broker, transaction, pathUri, permission -> chown(broker, permission, owner, group));
    }

    /**
     * Changes the ownership of a Collection in the database
     * inline with the rules of POSIX.1-2017 (Issue 7, 2018 edition).
     *
     * Note - this function does not persist the updated permissions of the Collection.
     *
     * @param broker the database broker.
     * @param collection the URI to a Collection in the database.
     * @param owner the new owner for the collection.
     * @param group thr new group for the collection.
     *
     * @throws PermissionDeniedException if the calling process has insufficient permissions.
     */
    public static void chown(final DBBroker broker, final Collection collection, final Optional<String> owner, final Optional<String> group) throws PermissionDeniedException {
        chown(broker, collection.getPermissions(), owner, group);
    }

    /**
     * Changes the ownership of a Document in the database
     * inline with the rules of POSIX.1-2017 (Issue 7, 2018 edition).
     *
     * Note - this function does not persist the updated permissions of the Document.
     *
     * @param broker the database broker.
     * @param document the URI to a Document in the database.
     * @param owner the new owner for the document.
     * @param group thr new group for the document.
     *
     * @throws PermissionDeniedException if the calling process has insufficient permissions.
     */
    public static void chown(final DBBroker broker, final DocumentImpl document, final Optional<String> owner, final Optional<String> group) throws PermissionDeniedException {
        chown(broker, document.getPermissions(), owner, group);
    }

    public static void chown(final DBBroker broker, final Permission permission, final Optional<String> owner, final Optional<String> group) throws PermissionDeniedException {
        if ((!owner.isPresent()) && !group.isPresent()) {
            throw new IllegalArgumentException("Either owner or group must be provided");
        }

        final boolean changeOwner = owner.map(desiredOwner -> !permission.getOwner().getName().equals(desiredOwner)).orElse(false);
        boolean changeGroup = group.map(desiredGroup -> !permission.getGroup().getName().equals(desiredGroup)).orElse(false);

        // enforce security checks
        final boolean posixChownRestricted = broker.getConfiguration().getProperty(POSIX_CHOWN_RESTRICTED_PROPERTY, true);
        if (posixChownRestricted) {
            if (changeOwner && !permission.isCurrentSubjectDBA()) {
                // Only a superuser process can change the user ID of the file.

                throw new PermissionDeniedException("Only a DBA can change the user ID of a resource when posix-chown-restricted is in effect.");
            }

            if (changeGroup && !permission.isCurrentSubjectDBA()) {

                /*
                    A non-superuser process can change the group ID of the file if the process owns the file
                    (the effective user ID equals the user ID of the file)
                 */
                if (!permission.isCurrentSubjectOwner()) {
                    throw new PermissionDeniedException("You cannot change the group ID of a file you do not own when posix-chown-restricted is in effect.");
                }
                // and, group equals either the effective group ID of the process or one of the processes supplementary group IDs.
                final Group desiredGroup = broker.getBrokerPool().getSecurityManager().getGroup(group.get());
                if (desiredGroup == null) {
                    // guard against attempting to change to a non-existent or removed group
                    changeGroup = false;
                } else {
                    final int desiredGroupId = desiredGroup.getId();
                    if (!permission.isCurrentSubjectInGroup(desiredGroupId)) {
                        throw new PermissionDeniedException("You cannot change the group ID of a file to a group of which you are not a member when posix-chown-restricted is in effect.");
                    }
                }
            }
        } else {
            if (changeOwner) {
                if ((!permission.isCurrentSubjectDBA()) && !permission.isCurrentSubjectOwner()) {
                    throw new PermissionDeniedException("Only a DBA or the resources owner can change the user ID of a resource.");
                }
            }

            if (changeGroup) {
                if ((!permission.isCurrentSubjectDBA()) && !permission.isCurrentSubjectOwner()) {
                    throw new PermissionDeniedException("Only a DBA or the resources owner can change the group ID of a resource.");
                }
            }
        }

        if (!permission.isCurrentSubjectDBA()) {
            /*
                If this is called by a process other than a superuser process, on successful return,
                both the set-user-ID and the set-group-ID bits are cleared.

                MUST be done before changing the owner or group to prevent a privilege escalation attack
             */

            if (permission.isSetUid()) {
                permission.setSetUid(false);
            }

            if (permission.isSetGid()) {
                permission.setSetGid(false);
            }
        }

        // change the owner
        if (changeOwner) {
            permission.setOwner(owner.get());
        }

        // change the group
        if (changeGroup) {
            permission.setGroup(group.get());
        }
    }

    /**
     * Changes the mode of a resource in the database
     * inline with the rules of POSIX.1-2017 (Issue 7, 2018 edition).
     *
     * Note - this function will persist the updated permissions of the database resource.
     *
     * @param broker the database broker.
     * @param transaction the database transaction.
     * @param pathUri the URI to a resource in the database.
     * @param modeStr the new mode for the resource.
     * @param acl the new ACL for the resource.
     *
     * @throws PermissionDeniedException if the calling process has insufficient permissions.
     */
    public static void chmod_str(final DBBroker broker, final Txn transaction, final XmldbURI pathUri, final Optional<String> modeStr, final Optional<List<ACEAider>> acl) throws PermissionDeniedException {
        updatePermissions(broker, transaction, pathUri, permission -> chmod_impl(broker, permission, modeStr.map(Either::Left), acl));
    }

    /**
     * Changes the mode of a Collection in the database
     * inline with the rules of POSIX.1-2017 (Issue 7, 2018 edition).
     *
     * Note - this function does not persist the updated permissions of the Collection.
     *
     * @param broker the database broker.
     * @param collection the URI to a Collection in the database.
     * @param modeStr the new mode for the collection.
     * @param acl the new ACL for the collection.
     *
     * @throws PermissionDeniedException if the calling process has insufficient permissions.
     */
    public static void chmod_str(final DBBroker broker, final Collection collection, final Optional<String> modeStr, final Optional<List<ACEAider>> acl) throws PermissionDeniedException {
        chmod_impl(broker, collection.getPermissions(), modeStr.map(Either::Left), acl);
    }

    /**
     * Changes the mode of a Document in the database
     * inline with the rules of POSIX.1-2017 (Issue 7, 2018 edition).
     *
     * Note - this function does not persist the updated permissions of the Document.
     *
     * @param broker the database broker.
     * @param document the URI to a Document in the database.
     * @param modeStr the new mode for the document.
     * @param acl the new ACL for the document.
     *
     * @throws PermissionDeniedException if the calling process has insufficient permissions.
     */
    public static void chmod_str(final DBBroker broker, final DocumentImpl document, final Optional<String> modeStr, final Optional<List<ACEAider>> acl) throws PermissionDeniedException {
        chmod_impl(broker, document.getPermissions(), modeStr.map(Either::Left), acl);
    }

    /**
     * Changes the mode of a resource in the database
     * inline with the rules of POSIX.1-2017 (Issue 7, 2018 edition).
     *
     * @param broker the database broker.
     * @param transaction the database transaction.
     * @param pathUri the URI to a resource in the database.
     * @param mode the new mode for the resource.
     * @param acl the new ACL for the resource.
     *
     * @throws PermissionDeniedException if the calling process has insufficient permissions.
     */
    public static void chmod(final DBBroker broker, final Txn transaction, final XmldbURI pathUri, final Optional<Integer> mode, final Optional<List<ACEAider>> acl) throws PermissionDeniedException {
        updatePermissions(broker, transaction, pathUri, permission -> chmod_impl(broker, permission, mode.map(Either::Right), acl));
    }

    /**
     * Changes the mode of a Collection in the database
     * inline with the rules of POSIX.1-2017 (Issue 7, 2018 edition).
     *
     * @param broker the database broker.
     * @param collection the URI to a Collection in the database.
     * @param mode the new mode for the collection.
     * @param acl the new ACL for the collection.
     *
     * @throws PermissionDeniedException if the calling process has insufficient permissions.
     */
    public static void chmod(final DBBroker broker, final Collection collection, final Optional<Integer> mode, final Optional<List<ACEAider>> acl) throws PermissionDeniedException {
        chmod_impl(broker, collection.getPermissions(), mode.map(Either::Right), acl);
    }

    /**
     * Changes the mode of a Document in the database
     * inline with the rules of POSIX.1-2017 (Issue 7, 2018 edition).
     *
     * @param broker the database broker.
     * @param document the URI to a Document in the database.
     * @param mode the new mode for the document.
     * @param acl the new ACL for the document.
     *
     * @throws PermissionDeniedException if the calling process has insufficient permissions.
     */
    public static void chmod(final DBBroker broker, final DocumentImpl document, final Optional<Integer> mode, final Optional<List<ACEAider>> acl) throws PermissionDeniedException {
        chmod_impl(broker, document.getPermissions(), mode.map(Either::Right), acl);
    }

    /**
     * Changes the mode of permissions in the database
     * inline with the rules of POSIX.1-2017 (Issue 7, 2018 edition).
     *
     * @param broker the database broker.
     * @param permissions the permissions in the database.
     * @param mode the new mode for the permissions.
     * @param acl the new ACL for the permissions.
     *
     * @throws PermissionDeniedException if the calling process has insufficient permissions.
     */
    public static void chmod_str(final DBBroker broker, final Permission permissions, final Optional<String> mode, final Optional<List<ACEAider>> acl) throws PermissionDeniedException {
        chmod_impl(broker, permissions, mode.map(Either::Left), acl);
    }

    /**
     * Changes the mode of permissions in the database
     * inline with the rules of POSIX.1-2017 (Issue 7, 2018 edition).
     *
     * @param broker the database broker.
     * @param permissions the permissions in the database.
     * @param mode the new mode for the permissions.
     * @param acl the new ACL for the permissions.
     *
     * @throws PermissionDeniedException if the calling process has insufficient permissions.
     */
    public static void chmod(final DBBroker broker, final Permission permissions, final Optional<Integer> mode, final Optional<List<ACEAider>> acl) throws PermissionDeniedException {
        chmod_impl(broker, permissions, mode.map(Either::Right), acl);
    }

    private static void chmod_impl(final DBBroker broker, final Permission permission, final Optional<Either<String, Integer>> mode, final Optional<List<ACEAider>> acl) throws PermissionDeniedException {
        if ((!mode.isPresent()) && !acl.isPresent()) {
            throw new IllegalArgumentException("Either mode or acl must be provided");
        }

        try {
            final boolean changeMode;
            if (mode.isPresent()) {
                if (mode.get().isLeft()) {
                    final Subject effectiveUser = broker.getCurrentSubject();
                    final Permission other = new UnixStylePermission(broker.getBrokerPool().getSecurityManager(), effectiveUser.getId(), effectiveUser.getDefaultGroup().getId(), 0);
                    other.setMode(mode.get().left().get());
                    changeMode = permission.getMode() != other.getMode();
                } else {
                    changeMode = permission.getMode() != mode.get().right().get().intValue();
                }
            } else {
                changeMode = false;
            }
            final boolean changeAcl = acl.map(desiredAces -> !aclEquals(permission, desiredAces)).orElse(false);

            /*
                To change the permission bits of a file, the effective user ID of the process must be equal to the owner ID
                of the file, or the process must have superuser permissions.
            */
            if ((changeMode || changeAcl) && (!permission.isCurrentSubjectDBA()) && !permission.isCurrentSubjectOwner()) {
                throw new PermissionDeniedException("Only a DBA or the resources owner can change the mode of a resource.");
            }

            // change the mode
            if (changeMode) {

                final boolean matchedGroup = permission.isCurrentSubjectInGroup();
                if (permission.isCurrentSubjectDBA() || matchedGroup) {
                    if (mode.get().isLeft()) {
                        permission.setMode(mode.get().left().get());
                    } else {
                        permission.setMode(mode.get().right().get());
                    }

                } else {
                /*
                    If the group ID of the file does not equal either the effective group ID of the process or one of
                    the processâs supplementary group IDs and if the process does not have superuser privileges,
                    then the set-group-ID bit is automatically turned off.
                    This prevents a user from creating a set-group-ID file owned by a group that the user doesnât
                    belong to.
                */
                    if (mode.get().isLeft()) {
                        permission.setMode(removeSetGid(mode.get().left().get()));
                    } else {
                        permission.setMode(removeSetGid(mode.get().right().get()));
                    }
                }
            }

            // change the acl
            if (changeAcl) {
                final ACLPermission aclPermission = (ACLPermission) permission;
                aclPermission.clear();
                for (final ACEAider ace : acl.get()) {
                    aclPermission.addACE(ace.getAccessType(), ace.getTarget(), ace.getWho(), ace.getMode());
                }
            }
        } catch (final SyntaxException se) {
            throw new PermissionDeniedException("Unrecognised mode syntax: " + se.getMessage(), se);
        }
    }

    /**
     * Changes the ACL of a permissioned object in the database
     * inline with the rules for chmod of POSIX.1-2017 (Issue 7, 2018 edition).
     *
     * @param permission the permissions of the object in the database.
     * @param permissionModifier a function which will modify the ACL.
     *
     * @throws PermissionDeniedException if the calling process has insufficient permissions.
     */
    public static void chacl(final Permission permission, final ConsumerE<ACLPermission, PermissionDeniedException> permissionModifier) throws PermissionDeniedException {
        if(permission instanceof SimpleACLPermission) {
            chacl((SimpleACLPermission)permission, permissionModifier);
        } else {
            throw new PermissionDeniedException("ACL like permissions have not been enabled");
        }
    }

    /**
     * Changes the ACL of permissions in the database
     * inline with the rules for chmod of POSIX.1-2017 (Issue 7, 2018 edition).
     *
     * @param broker the database broker.
     * @param transaction the database transaction.
     * @param pathUri the URI to a resource in the database.
     * @param permissionModifier a function which will modify the ACL.
     *
     * @throws PermissionDeniedException if the calling process has insufficient permissions.
     */
    public static void chacl(final DBBroker broker, final Txn transaction, final XmldbURI pathUri, final ConsumerE<ACLPermission, PermissionDeniedException> permissionModifier) throws PermissionDeniedException {
        updatePermissions(broker, transaction, pathUri, permission -> {
            if(permission instanceof SimpleACLPermission) {
                chacl((SimpleACLPermission)permission, permissionModifier);
            } else {
                throw new PermissionDeniedException("ACL like permissions have not been enabled");
            }
        });
    }

    public static void chacl(final SimpleACLPermission permission, final ConsumerE<ACLPermission, PermissionDeniedException> permissionModifier) throws PermissionDeniedException {
        if (permissionModifier == null) {
            throw new IllegalArgumentException("permissionModifier must be provided");
        }

        /*
            To change the permission bits of a file, the effective user ID of the process must be equal to the owner ID
            of the file, or the process must have superuser permissions.
        */
        if ((!permission.isCurrentSubjectDBA()) && !permission.isCurrentSubjectOwner()) {
            throw new PermissionDeniedException("Only a DBA or the resources owner can change the ACL of a resource.");
        }

        // change the acl
        permissionModifier.accept(permission);
    }

    /**
     * Compares the ACEs in a permission's ACL against the provides ACEs.
     *
     * @param permission The permission ACL to compare against the otherAces.
     * @param otherAces The ACEs to compare against the permissions's ACL.
     *
     * @return true if the {@code permission}'s ACL has the same ACEs as {@code otherAces}, false otherwise.
     */
    private static boolean aclEquals(final Permission permission, final List<ACEAider> otherAces) {
        if (!(permission instanceof ACLPermission)) {
            return false;
        }

        final ACLPermission aclPermission = (ACLPermission)permission;
        if (aclPermission.getACECount() != otherAces.size()) {
            return false;
        }

        for (int i = 0; i < otherAces.size(); i++) {
            final ACEAider other = otherAces.get(i);

            if (aclPermission.getACEAccessType(i) != other.getAccessType()
                    || aclPermission.getACETarget(i) != other.getTarget()
                    || (!aclPermission.getACEWho(i).equals(other.getWho()))
                    || aclPermission.getACEMode(i) != other.getMode()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Removes any setGid bit from the provided mode string.
     *
     * @param modeStr The provided mode string.
     *
     * @return The mode string without a setGid bit.
     */
    private static String removeSetGid(final String modeStr) {
        if (SIMPLE_SYMBOLIC_MODE_PATTERN.matcher(modeStr).matches()) {
            final char groupExecute = modeStr.charAt(5);
            if (groupExecute == SETGID_CHAR_NO_EXEC) {
                return modeStr.substring(0, 5) + UNSET_CHAR + modeStr.substring(5);
            } else if (groupExecute == SETGID_CHAR) {
                return modeStr.substring(0, 5) + EXECUTE_CHAR + modeStr.substring(5);
            }
        } else {
            if (UNIX_SYMBOLIC_MODE_PATTERN.matcher(modeStr).matches()) {

                // check for 'g+s' or 'g=s'
                final Pattern ptnExtractGroupMode = Pattern.compile("[^g]*(g\\+|=)([^,s]*s[^,s]*)[^g]*");
                final Matcher mtcExtractGroupMode = ptnExtractGroupMode.matcher(modeStr);
                if (mtcExtractGroupMode.matches()) {
                    final String requestedGroupOp = mtcExtractGroupMode.group(1);
                    final String requestedGroupMode = mtcExtractGroupMode.group(2);
                    final String noSetGidGroupMode = requestedGroupMode.replace("s", "");
                    if (noSetGidGroupMode.isEmpty()) {
                        return modeStr.replace(requestedGroupOp + requestedGroupMode, "");
                    } else {
                        return modeStr.replace(requestedGroupOp + requestedGroupMode, requestedGroupOp + noSetGidGroupMode);
                    }
                } else {
                    // check for 'a+s' or 'a=s'
                    final Pattern ptnExtractAllMode = Pattern.compile("[^a]*a(\\+|=)([^,s]*s[^,s]*)[^a]*");
                    final Matcher mtcExtractAllMode = ptnExtractAllMode.matcher(modeStr);
                    if (mtcExtractAllMode.matches()) {
                        final String requestedAllOpSymbol = mtcExtractAllMode.group(1);
                        final String requestedAllMode = mtcExtractAllMode.group(2);
                        final String noSetGidGroupMode = requestedAllMode.replace("s", "");

                        return
                                USER_CHAR + requestedAllOpSymbol + requestedAllMode + "," +
                                        (noSetGidGroupMode.isEmpty() ? "" : (GROUP_CHAR + requestedAllOpSymbol + noSetGidGroupMode + ",")) +
                                        OTHER_CHAR + requestedAllMode + requestedAllMode;
                    }
                }
            }

            // NOTE: we don't need to do anything for EXIST_SYMBOLIC_MODE_PATTERN as it does not support setting setGid
        }

        return modeStr;
    }

    /**
     * Removes any setGid bit from the provided mode.
     *
     * @param mode The provided mode.
     *
     * @return The mode without a setGid bit.
     */
    private static int removeSetGid(final int mode) {
        return mode & ~0x800;
    }
}
