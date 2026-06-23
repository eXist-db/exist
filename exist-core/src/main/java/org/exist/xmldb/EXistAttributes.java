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

import org.xmldb.api.security.AclEntry;
import org.xmldb.api.security.Attributes;
import org.xmldb.api.security.GroupPrincipal;
import org.xmldb.api.security.Permission;
import org.xmldb.api.security.Permissions;
import org.xmldb.api.security.UserPrincipal;

import java.util.List;
import java.util.Set;

record EXistAttributes(org.exist.security.Permission permission) implements Attributes {
    @Override
    public UserPrincipal owner() {
        return new EXistUserPrincipal(permission.getOwner());
    }

    @Override
    public GroupPrincipal group() {
        return new EXistGroupPrincipal(permission.getGroup());
    }

    @Override
    public Set<Permission> permissions() {
        return Permissions.fromOctal(permission.getMode());
    }

    @Override
    public List<AclEntry> acl() {
        // no supported by eXist?
        return List.of();
    }
}
