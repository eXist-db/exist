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

import org.easymock.EasyMockExtension;
import org.easymock.Mock;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.security.Attributes;
import org.xmldb.api.security.GroupPrincipal;
import org.xmldb.api.security.Permission;
import org.xmldb.api.security.UserPrincipal;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.xmldb.api.security.Permission.GROUP_EXECUTE;
import static org.xmldb.api.security.Permission.GROUP_READ;
import static org.xmldb.api.security.Permission.OTHERS_EXECUTE;
import static org.xmldb.api.security.Permission.OTHERS_READ;
import static org.xmldb.api.security.Permission.OWNER_EXECUTE;
import static org.xmldb.api.security.Permission.OWNER_READ;
import static org.xmldb.api.security.Permission.OWNER_WRITE;

@ExtendWith(EasyMockExtension.class)
class LocalPermissionManagementServiceTest {
    @Mock
    EXistUserManagementService userManagementService;
    LocalPermissionManagementService service;

    @BeforeEach
    void setUp() throws XMLDBException {
        Subject user = createMock(Subject.class);
        BrokerPool brokerPool = createMock(BrokerPool.class);
        LocalCollection collection = createMock(LocalCollection.class);

        expect(collection.getService(EXistUserManagementService.class)).andReturn(userManagementService).anyTimes();
        replay(collection);

        service = new LocalPermissionManagementService(user, brokerPool, collection);
    }

    @Test
    void getName() throws XMLDBException {
        assertThat(service.getName()).isEqualTo("PermissionManagementService");
    }

    @Test
    void getVersion() throws XMLDBException {
        assertThat(service.getVersion()).isEqualTo("1.0");
    }

    @Test
    void getAttributesForCollection() throws XMLDBException {
        Collection coll = createMock(Collection.class);
        org.exist.security.Permission perm = createMock(org.exist.security.Permission.class);
        expect(userManagementService.getPermissions(coll)).andReturn(perm);
        replay(userManagementService, coll, perm);

        Attributes attrs = service.getAttributes(coll);
        assertThat(attrs.getClass()).isSameAs(EXistAttributes.class);
        verify(userManagementService, coll, perm);
    }

    @Test
    void getPermissionsForResource() throws XMLDBException {
        Resource res = createMock(Resource.class);
        org.exist.security.Permission perm = createMock(org.exist.security.Permission.class);
        expect(perm.getMode()).andReturn(0755);
        expect(userManagementService.getPermissions(res)).andReturn(perm);
        replay(userManagementService, res, perm);

        Set<Permission> permissions = service.getPermissions(res);
        assertThat(permissions).containsExactlyInAnyOrder(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE,
                GROUP_READ, GROUP_EXECUTE, OTHERS_READ, OTHERS_EXECUTE);
    }

    @Test
    void setPermissionsForCollection() throws XMLDBException, org.exist.security.PermissionDeniedException {
        Collection coll = createMock(Collection.class);
        org.exist.security.Permission perm = createMock(org.exist.security.Permission.class);
        expect(userManagementService.getPermissions(coll)).andReturn(perm);
        perm.setMode(0400);
        expectLastCall();
        replay(userManagementService, coll, perm);

        service.setPermissions(coll, Set.of(OWNER_READ));
        verify(userManagementService, coll, perm);
    }

    @Test
    void getOwnerForResource() throws XMLDBException {
        Resource res = createMock(Resource.class);
        org.exist.security.Permission perm = createMock(org.exist.security.Permission.class);
        org.exist.security.Account account = createMock(org.exist.security.Account.class);
        expect(userManagementService.getPermissions(res)).andReturn(perm);
        expect(perm.getOwner()).andReturn(account);
        expect(account.getName()).andReturn("testuser");
        replay(userManagementService, res, perm, account);

        UserPrincipal owner = service.getOwner(res);
        assertThat(owner.getName()).isEqualTo("testuser");
        verify(userManagementService, res, perm, account);
    }

    @Test
    void getGroupForCollection() throws XMLDBException {
        Collection coll = createMock(Collection.class);
        org.exist.security.Permission perm = createMock(org.exist.security.Permission.class);
        org.exist.security.Group group = createMock(org.exist.security.Group.class);
        expect(userManagementService.getPermissions(coll)).andReturn(perm);
        expect(perm.getGroup()).andReturn(group);
        expect(group.getName()).andReturn("testgroup");
        replay(userManagementService, coll, perm, group);

        GroupPrincipal g = service.getGroup(coll);
        assertThat(g.getName()).isEqualTo("testgroup");
        verify(userManagementService, coll, perm, group);
    }
}
