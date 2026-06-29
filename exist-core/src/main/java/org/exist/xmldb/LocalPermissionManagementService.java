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
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.security.AclEntry;
import org.xmldb.api.security.Attributes;
import org.xmldb.api.security.GroupPrincipal;
import org.xmldb.api.security.Permission;
import org.xmldb.api.security.PermissionManagementService;
import org.xmldb.api.security.UserPrincipal;

import java.util.List;
import java.util.Set;

public class LocalPermissionManagementService extends AbstractLocalService implements PermissionManagementService {
    public LocalPermissionManagementService(Subject user, BrokerPool brokerPool, LocalCollection collection) {
        super(user, brokerPool, collection);
    }

    private EXistPermissionManagement permissionManagement() throws XMLDBException {
       return new EXistPermissionManagement(collection.getService(EXistUserManagementService.class));
    }

    @Override
    public String getName() throws XMLDBException {
        return "PermissionManagementService";
    }

    @Override
    public String getVersion() throws XMLDBException {
        return "1.0";
    }

    @Override
    public Attributes getAttributes(Collection collection) throws XMLDBException {
        return permissionManagement().getAttributes(collection);
    }

    @Override
    public Attributes getAttributes(Resource resource) throws XMLDBException {
        return permissionManagement().getAttributes(resource);
    }

    @Override
    public Set<Permission> getPermissions(Collection collection) throws XMLDBException {
        return permissionManagement().getPermissions(collection);
    }

    @Override
    public void setPermissions(Collection collection, Set<Permission> permissionSet) throws XMLDBException {
        permissionManagement().setPermissions(collection, permissionSet);
    }

    @Override
    public Set<Permission> getPermissions(Resource resource) throws XMLDBException {
        return permissionManagement().getPermissions(resource);
    }

    @Override
    public void setPermissions(Resource resource, Set<Permission> permissionSet) throws XMLDBException {
        permissionManagement().setPermissions(resource, permissionSet);
    }

    @Override
    public List<AclEntry> getAcl(Collection collection) throws XMLDBException {
        return permissionManagement().getAcl(collection);
    }

    @Override
    public void setAcl(Collection collection, List<AclEntry> list) throws XMLDBException {
        permissionManagement().setAcl(collection, list);
    }

    @Override
    public List<AclEntry> getAcl(Resource resource) throws XMLDBException {
        return permissionManagement().getAcl(resource);
    }

    @Override
    public void setAcl(Resource resource, List<AclEntry> list) throws XMLDBException {
        permissionManagement().setAcl(resource, list);
    }

    @Override
    public UserPrincipal getOwner(Collection collection) throws XMLDBException {
        return permissionManagement().getOwner(collection);
    }

    @Override
    public void setOwner(Collection collection, UserPrincipal userPrincipal) throws XMLDBException {
        permissionManagement().setOwner(collection, userPrincipal);
    }

    @Override
    public UserPrincipal getOwner(Resource resource) throws XMLDBException {
        return permissionManagement().getOwner(resource);
    }

    @Override
    public void setOwner(Resource resource, UserPrincipal userPrincipal) throws XMLDBException {
        permissionManagement().setOwner(resource, userPrincipal);
    }

    @Override
    public GroupPrincipal getGroup(Collection collection) throws XMLDBException {
        return permissionManagement().getGroup(collection);
    }

    @Override
    public void setGroup(Collection collection, GroupPrincipal groupPrincipal) throws XMLDBException {
        permissionManagement().setGroup(collection, groupPrincipal);
    }

    @Override
    public GroupPrincipal getGroup(Resource resource) throws XMLDBException {
        return permissionManagement().getGroup(resource);
    }

    @Override
    public void setGroup(Resource resource, GroupPrincipal groupPrincipal) throws XMLDBException {
        permissionManagement().setGroup(resource, groupPrincipal);
    }
}
