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
import org.junit.jupiter.api.Test;
import org.xmldb.api.security.Permission;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.xmldb.api.security.Permission.GROUP_EXECUTE;
import static org.xmldb.api.security.Permission.GROUP_READ;
import static org.xmldb.api.security.Permission.OTHERS_EXECUTE;
import static org.xmldb.api.security.Permission.OTHERS_READ;
import static org.xmldb.api.security.Permission.OWNER_EXECUTE;
import static org.xmldb.api.security.Permission.OWNER_READ;
import static org.xmldb.api.security.Permission.OWNER_WRITE;

/**
 * Tests for {@link EXistAttributes}.
 */
class EXistAttributesTest {

    @Test
    void owner() {
        org.exist.security.Permission permission = createMock(org.exist.security.Permission.class);
        Account owner = createMock(Account.class);
        expect(permission.getOwner()).andReturn(owner);
        expect(owner.getName()).andReturn("testuser");
        replay(permission, owner);

        EXistAttributes attributes = new EXistAttributes(permission);
        assertThat(attributes.owner().getName()).isEqualTo("testuser");
        verify(permission, owner);
    }

    @Test
    void group() {
        org.exist.security.Permission permission = createMock(org.exist.security.Permission.class);
        Group group = createMock(Group.class);
        expect(permission.getGroup()).andReturn(group);
        expect(group.getName()).andReturn("testgroup");
        replay(permission, group);

        EXistAttributes attributes = new EXistAttributes(permission);
        assertThat(attributes.group().getName()).isEqualTo("testgroup");
        verify(permission, group);
    }

    @Test
    void permissions() {
        org.exist.security.Permission permission = createMock(org.exist.security.Permission.class);
        expect(permission.getMode()).andReturn(0755);
        replay(permission);

        EXistAttributes attributes = new EXistAttributes(permission);
        Set<Permission> permissions = attributes.permissions();
        assertThat(permissions).containsExactly(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE, GROUP_READ, GROUP_EXECUTE, OTHERS_READ, OTHERS_EXECUTE);
        verify(permission);
    }

    @Test
    void acl() {
        org.exist.security.Permission permission = createMock(org.exist.security.Permission.class);
        replay(permission);

        EXistAttributes attributes = new EXistAttributes(permission);
        assertThat(attributes.acl()).isEmpty();
        verify(permission);
    }
}
