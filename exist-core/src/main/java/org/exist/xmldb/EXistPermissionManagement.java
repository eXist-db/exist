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
package org.exist.xmldb;

import org.exist.security.Account;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.security.AclEntry;
import org.xmldb.api.security.Attributes;
import org.xmldb.api.security.GroupPrincipal;
import org.xmldb.api.security.Permission;
import org.xmldb.api.security.Permissions;
import org.xmldb.api.security.UserPrincipal;

import java.util.List;
import java.util.Set;

record EXistPermissionManagement(EXistUserManagementService userManagementService) {

    Account asUser(UserPrincipal userPrincipal) throws XMLDBException {
        if (userPrincipal instanceof EXistUserPrincipal(Account account)) {
            return account;
        } else {
            return userManagementService.getAccount(userPrincipal.getName());
        }
    }

    Group asGroup(GroupPrincipal groupPrincipal) throws XMLDBException {
        if (groupPrincipal instanceof EXistGroupPrincipal(Group group)) {
            return group;
        } else {
            return userManagementService.getGroup(groupPrincipal.getName());
        }
    }

    @FunctionalInterface
    interface PermissionAction {
        void accept(org.exist.security.Permission permission) throws PermissionDeniedException, XMLDBException;
    }

    void withPermission(Collection collection, PermissionAction action) throws XMLDBException {
        try {
            action.accept(userManagementService.getPermissions(collection));
        } catch (PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e);
        }
    }

    void withPermission(Resource resource, PermissionAction action) throws XMLDBException {
        try {
            action.accept(userManagementService.getPermissions(resource));
        } catch (PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e);
        }
    }

    /*
     * to be replaced with the Permissions method as soon as available.
     */
    int toOctal(Set<Permission> permissions) {
        int octal = 0;
        // Handle special bits
        if (permissions.contains(Permission.SET_UID)) {
            octal |= 04000; // SUID
        }
        if (permissions.contains(Permission.SET_GID)) {
            octal |= 02000; // SGID
        }
        if (permissions.contains(Permission.STICKY_BIT)) {
            octal |= 01000; // Sticky bit
        }
        // Handle owner permissions
        if (permissions.contains(Permission.OWNER_READ)) {
            octal |= 0400; // Owner read
        }
        if (permissions.contains(Permission.OWNER_WRITE)) {
            octal |= 0200; // Owner write
        }
        if (permissions.contains(Permission.OWNER_EXECUTE)) {
            octal |= 0100; // Owner execute
        }
        // Handle group permissions
        if (permissions.contains(Permission.GROUP_READ)) {
            octal |= 040; // Group read
        }
        if (permissions.contains(Permission.GROUP_WRITE)) {
            octal |= 020; // Group write
        }
        if (permissions.contains(Permission.GROUP_EXECUTE)) {
            octal |= 010; // Group execute
        }
        // Handle others permissions
        if (permissions.contains(Permission.OTHERS_READ)) {
            octal |= 04; // Others read
        }
        if (permissions.contains(Permission.OTHERS_WRITE)) {
            octal |= 02; // Others write
        }
        if (permissions.contains(Permission.OTHERS_EXECUTE)) {
            octal |= 01; // Others execute
        }
        return octal;
    }

    Attributes getAttributes(Collection collection) throws XMLDBException {
        return new EXistAttributes(userManagementService.getPermissions(collection));
    }

    Attributes getAttributes(Resource resource) throws XMLDBException {
        return new EXistAttributes(userManagementService.getPermissions(resource));
    }

    Set<Permission> getPermissions(Collection collection) throws XMLDBException {
        return Permissions.fromOctal(userManagementService.getPermissions(collection).getMode());
    }

    void setPermissions(Collection collection, Set<Permission> permissionSet) throws XMLDBException {
        withPermission(collection, permission -> permission.setMode(toOctal(permissionSet)));
    }

    Set<Permission> getPermissions(Resource resource) throws XMLDBException {
        return Permissions.fromOctal(userManagementService.getPermissions(resource).getMode());
    }

    void setPermissions(Resource resource, Set<Permission> permissionSet) throws XMLDBException {
        withPermission(resource, permission -> permission.setMode(toOctal(permissionSet)));
    }

    List<AclEntry> getAcl(Collection collection) {
        // no supported by eXist?
        return List.of();
    }

    void setAcl(Collection collection, List<AclEntry> list) {
        // no supported by eXist?
    }

    List<AclEntry> getAcl(Resource resource) {
        // no supported by eXist?
        return List.of();
    }

    void setAcl(Resource resource, List<AclEntry> list) {
        // no supported by eXist?
    }

    UserPrincipal getOwner(Collection collection) throws XMLDBException {
        return new EXistUserPrincipal(userManagementService.getPermissions(collection).getOwner());
    }

    void setOwner(Collection collection, UserPrincipal userPrincipal) throws XMLDBException {
        withPermission(collection, permission -> permission.setOwner(asUser(userPrincipal)));
    }

    UserPrincipal getOwner(Resource resource) throws XMLDBException {
        return new EXistUserPrincipal(userManagementService.getPermissions(resource).getOwner());
    }

    void setOwner(Resource resource, UserPrincipal userPrincipal) throws XMLDBException {
        withPermission(resource, permission -> permission.setOwner(asUser(userPrincipal)));
    }

    GroupPrincipal getGroup(Collection collection) throws XMLDBException {
        return new EXistGroupPrincipal(userManagementService.getPermissions(collection).getGroup());
    }

    void setGroup(Collection collection, GroupPrincipal groupPrincipal) throws XMLDBException {
        withPermission(collection, permission -> permission.setGroup(asGroup(groupPrincipal)));
    }

    GroupPrincipal getGroup(Resource resource) throws XMLDBException {
        return new EXistGroupPrincipal(userManagementService.getPermissions(resource).getGroup());
    }

    void setGroup(Resource resource, GroupPrincipal groupPrincipal) throws XMLDBException {
        withPermission(resource, permission -> permission.setGroup(asGroup(groupPrincipal)));
    }
}
