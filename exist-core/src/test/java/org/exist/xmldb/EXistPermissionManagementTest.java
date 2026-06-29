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
import org.exist.security.Account;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.security.Attributes;
import org.xmldb.api.security.GroupPrincipal;
import org.xmldb.api.security.Permission;
import org.xmldb.api.security.UserPrincipal;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.xmldb.api.security.Permission.GROUP_EXECUTE;
import static org.xmldb.api.security.Permission.GROUP_READ;
import static org.xmldb.api.security.Permission.GROUP_WRITE;
import static org.xmldb.api.security.Permission.OTHERS_EXECUTE;
import static org.xmldb.api.security.Permission.OTHERS_READ;
import static org.xmldb.api.security.Permission.OTHERS_WRITE;
import static org.xmldb.api.security.Permission.OWNER_EXECUTE;
import static org.xmldb.api.security.Permission.OWNER_READ;
import static org.xmldb.api.security.Permission.OWNER_WRITE;
import static org.xmldb.api.security.Permission.STICKY_BIT;

/**
 * Tests for {@link EXistPermissionManagement}.
 */
@ExtendWith(EasyMockExtension.class)
class EXistPermissionManagementTest {
    @Mock
    EXistUserManagementService userManagementService;
    @Mock
    Account account;
    @Mock
    Group group;
    @Mock
    Collection collection;
    @Mock
    Resource resource;

    EXistPermissionManagement permissionManagement;

    @BeforeEach
    void setUp() {
        permissionManagement = new EXistPermissionManagement(userManagementService);
    }

    @Test
    void asUserWithEXistUserPrincipal() throws XMLDBException {
        UserPrincipal userPrincipal = new EXistUserPrincipal(account);
        
        Account result = permissionManagement.asUser(userPrincipal);
        
        assertThat(result).isSameAs(account);
    }

    @Test
    void asUserWithGenericUserPrincipal() throws XMLDBException {
        UserPrincipal userPrincipal = createMock(UserPrincipal.class);
        
        expect(userPrincipal.getName()).andReturn("testuser");
        expect(userManagementService.getAccount("testuser")).andReturn(account);
        replay(userPrincipal, userManagementService);
        
        Account result = permissionManagement.asUser(userPrincipal);
        
        assertThat(result).isSameAs(account);
        verify(userPrincipal, userManagementService);
    }

    @Test
    void asGroupWithEXistGroupPrincipal() throws XMLDBException {
        GroupPrincipal groupPrincipal = new EXistGroupPrincipal(group);
        
        Group result = permissionManagement.asGroup(groupPrincipal);
        
        assertThat(result).isSameAs(group);
    }

    @Test
    void asGroupWithGenericGroupPrincipal() throws XMLDBException {
        GroupPrincipal groupPrincipal = createMock(GroupPrincipal.class);

        expect(groupPrincipal.getName()).andReturn("testgroup");
        expect(userManagementService.getGroup("testgroup")).andReturn(group);
        replay(groupPrincipal, userManagementService);
        
        Group result = permissionManagement.asGroup(groupPrincipal);
        
        assertThat(result).isSameAs(group);
        verify(groupPrincipal, userManagementService);
    }

    @Test
    void withPermissionCollectionSuccess() throws XMLDBException {
        org.exist.security.Permission permission = createMock(org.exist.security.Permission.class);
        
        expect(userManagementService.getPermissions(collection)).andReturn(permission);
        replay(userManagementService);
        
        permissionManagement.withPermission(collection, p -> {
            assertThat(p).isSameAs(permission);
        });
        
        verify(userManagementService);
    }

    @Test
    void withPermissionCollectionPermissionDenied() throws XMLDBException {
        org.exist.security.Permission permission = createMock(org.exist.security.Permission.class);
        
        expect(userManagementService.getPermissions(collection)).andReturn(permission);
        replay(userManagementService);
        
        assertThatThrownBy(() -> {
            permissionManagement.withPermission(collection, p -> {
                throw new PermissionDeniedException("denied");
            });
        }).isInstanceOf(XMLDBException.class)
                .extracting(e -> ((XMLDBException) e).errorCode)
                .isEqualTo(ErrorCodes.PERMISSION_DENIED);
        
        verify(userManagementService);
    }

    @Test
    void withPermissionResourceSuccess() throws XMLDBException {
        org.exist.security.Permission permission = createMock(org.exist.security.Permission.class);
        
        expect(userManagementService.getPermissions(resource)).andReturn(permission);
        replay(userManagementService);
        
        permissionManagement.withPermission(resource, p -> {
            assertThat(p).isSameAs(permission);
        });
        
        verify(userManagementService);
    }

    @Test
    void withPermissionResourcePermissionDenied() throws XMLDBException {
        org.exist.security.Permission permission = createMock(org.exist.security.Permission.class);
        
        expect(userManagementService.getPermissions(resource)).andReturn(permission);
        replay(userManagementService);
        
        assertThatThrownBy(() -> {
            permissionManagement.withPermission(resource, p -> {
                throw new PermissionDeniedException("denied");
            });
        }).isInstanceOf(XMLDBException.class)
                .extracting(e -> ((XMLDBException) e).errorCode)
                .isEqualTo(ErrorCodes.PERMISSION_DENIED);
        
        verify(userManagementService);
    }

    @Test
    void toOctal() {
        assertThat(permissionManagement.toOctal(EnumSet.noneOf(Permission.class))).isZero();
        
        assertThat(permissionManagement.toOctal(EnumSet.of(Permission.SET_UID))).isEqualTo(04000);
        assertThat(permissionManagement.toOctal(EnumSet.of(Permission.SET_GID))).isEqualTo(02000);
        assertThat(permissionManagement.toOctal(EnumSet.of(STICKY_BIT))).isEqualTo(01000);
        
        assertThat(permissionManagement.toOctal(EnumSet.of(OWNER_READ))).isEqualTo(0400);
        assertThat(permissionManagement.toOctal(EnumSet.of(OWNER_WRITE))).isEqualTo(0200);
        assertThat(permissionManagement.toOctal(EnumSet.of(OWNER_EXECUTE))).isEqualTo(0100);
        
        assertThat(permissionManagement.toOctal(EnumSet.of(GROUP_READ))).isEqualTo(040);
        assertThat(permissionManagement.toOctal(EnumSet.of(GROUP_WRITE))).isEqualTo(020);
        assertThat(permissionManagement.toOctal(EnumSet.of(GROUP_EXECUTE))).isEqualTo(010);
        
        assertThat(permissionManagement.toOctal(EnumSet.of(OTHERS_READ))).isEqualTo(04);
        assertThat(permissionManagement.toOctal(EnumSet.of(OTHERS_WRITE))).isEqualTo(02);
        assertThat(permissionManagement.toOctal(EnumSet.of(OTHERS_EXECUTE))).isEqualTo(01);
        
        assertThat(permissionManagement.toOctal(EnumSet.allOf(Permission.class))).isEqualTo(07777);
    }

    @Test
    void getAttributesCollection() throws XMLDBException {
        org.exist.security.Permission permission = createMock(org.exist.security.Permission.class);

        expect(userManagementService.getPermissions(collection)).andReturn(permission);
        expect(permission.getOwner()).andReturn(account);
        expect(permission.getGroup()).andReturn(group);
        expect(permission.getMode()).andReturn(0755);
        expect(account.getName()).andReturn("owner").anyTimes();
        expect(group.getName()).andReturn("group").anyTimes();
        replay(userManagementService, permission, account, group);
        
        Attributes attributes = permissionManagement.getAttributes(collection);
        assertThat(attributes).isNotNull();
        assertThat(attributes.owner().getName()).isEqualTo("owner");
    }

    @Test
    void getAttributesForCollectionRefined() throws XMLDBException {
        org.exist.security.Permission permission = createMock(org.exist.security.Permission.class);

        expect(userManagementService.getPermissions(collection)).andReturn(permission);
        expect(permission.getOwner()).andReturn(account);
        expect(permission.getGroup()).andReturn(group);
        expect(permission.getMode()).andReturn(0755);
        expect(account.getName()).andReturn("owner").anyTimes();
        expect(group.getName()).andReturn("group").anyTimes();
        
        replay(userManagementService, permission, account, group);
        
        Attributes attributes = permissionManagement.getAttributes(collection);
        assertThat(attributes.owner().getName()).isEqualTo("owner");
        assertThat(attributes.group().getName()).isEqualTo("group");
        assertThat(attributes.permissions()).containsExactlyInAnyOrder(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE,
                GROUP_READ, GROUP_EXECUTE, OTHERS_READ, OTHERS_EXECUTE);
        assertThat(attributes.acl()).isEmpty();
    }

    @Test
    void getAttributesForResource() throws XMLDBException {
        org.exist.security.Permission permission = createMock(org.exist.security.Permission.class);

        expect(userManagementService.getPermissions(resource)).andReturn(permission);
        expect(permission.getOwner()).andReturn(account);
        expect(permission.getGroup()).andReturn(group);
        expect(permission.getMode()).andReturn(0644);
        expect(account.getName()).andReturn("owner").anyTimes();
        expect(group.getName()).andReturn("group").anyTimes();
        
        replay(userManagementService, permission, account, group);
        
        Attributes attributes = permissionManagement.getAttributes(resource);
        assertThat(attributes.owner().getName()).isEqualTo("owner");
        assertThat(attributes.group().getName()).isEqualTo("group");
        assertThat(attributes.permissions()).doesNotContain(OWNER_EXECUTE);
    }

    @Test
    void getPermissionsForCollection() throws XMLDBException {
        org.exist.security.Permission permission = createMock(org.exist.security.Permission.class);
        
        expect(userManagementService.getPermissions(collection)).andReturn(permission);
        expect(permission.getMode()).andReturn(0700);
        replay(userManagementService, permission);
        
        Set<Permission> perms = permissionManagement.getPermissions(collection);
        assertThat(perms).containsExactlyInAnyOrder(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE);
    }

    @Test
    void setPermissionsForCollection() throws XMLDBException, PermissionDeniedException {
        org.exist.security.Permission permission = createMock(org.exist.security.Permission.class);
        
        expect(userManagementService.getPermissions(collection)).andReturn(permission);
        permission.setMode(0755);
        replay(userManagementService, permission);
        
        permissionManagement.setPermissions(collection, EnumSet.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE,
                GROUP_READ, GROUP_EXECUTE, OTHERS_READ, OTHERS_EXECUTE));
        
        verify(userManagementService, permission);
    }

    @Test
    void getPermissionsForResource() throws XMLDBException {
        org.exist.security.Permission permission = createMock(org.exist.security.Permission.class);
        
        expect(userManagementService.getPermissions(resource)).andReturn(permission);
        expect(permission.getMode()).andReturn(0666);
        replay(userManagementService, permission);
        
        Set<Permission> perms = permissionManagement.getPermissions(resource);
        assertThat(perms).containsExactlyInAnyOrder(OWNER_READ, OWNER_WRITE, GROUP_READ, GROUP_WRITE, OTHERS_READ, OTHERS_WRITE);
    }

    @Test
    void getPermissionsCollectionWithXMLDBException() throws XMLDBException {
        expect(userManagementService.getPermissions(collection)).andThrow(new XMLDBException(ErrorCodes.VENDOR_ERROR, "failed"));
        replay(userManagementService);
        assertThatThrownBy(() -> permissionManagement.getPermissions(collection)).isInstanceOf(XMLDBException.class);
    }

    @Test
    void getPermissionsResourceWithXMLDBException() throws XMLDBException {
        expect(userManagementService.getPermissions(resource)).andThrow(new XMLDBException(ErrorCodes.VENDOR_ERROR, "failed"));
        replay(userManagementService);
        assertThatThrownBy(() -> permissionManagement.getPermissions(resource)).isInstanceOf(XMLDBException.class);
    }

    @Test
    void setPermissionsResource() throws XMLDBException, PermissionDeniedException {
        org.exist.security.Permission permission = createMock(org.exist.security.Permission.class);
        
        expect(userManagementService.getPermissions(resource)).andReturn(permission);
        permission.setMode(0600);
        replay(userManagementService, permission);
        
        permissionManagement.setPermissions(resource, EnumSet.of(OWNER_READ, OWNER_WRITE));
        
        verify(userManagementService, permission);
    }

    @Test
    void getAclForCollection() {
        assertThat(permissionManagement.getAcl(collection)).isEmpty();
    }

    @Test
    void setAclForCollection() {
        assertThatCode(() -> permissionManagement.setAcl(collection, List.of())).doesNotThrowAnyException();
    }

    @Test
    void getAclForResource() {
        assertThat(permissionManagement.getAcl(resource)).isEmpty();
    }

    @Test
    void setAclForResource() {
        assertThatCode(() -> permissionManagement.setAcl(resource, List.of())).doesNotThrowAnyException();
    }

    @Test
    void getOwnerForCollection() throws XMLDBException {
        org.exist.security.Permission permission = createMock(org.exist.security.Permission.class);

        expect(userManagementService.getPermissions(collection)).andReturn(permission);
        expect(permission.getOwner()).andReturn(account);
        expect(account.getName()).andReturn("owner").anyTimes();
        replay(userManagementService, permission, account);
        
        UserPrincipal principal = permissionManagement.getOwner(collection);
        assertThat(principal.getName()).isEqualTo("owner");
    }

    @Test
    void setOwnerForCollection() throws XMLDBException, PermissionDeniedException {
        org.exist.security.Permission permission = createMock(org.exist.security.Permission.class);
        UserPrincipal userPrincipal = new EXistUserPrincipal(account);
        
        expect(userManagementService.getPermissions(collection)).andReturn(permission);
        permission.setOwner(account);
        replay(userManagementService, permission);
        
        permissionManagement.setOwner(collection, userPrincipal);
        
        verify(userManagementService, permission);
    }

    @Test
    void getOwnerForResource() throws XMLDBException {
        org.exist.security.Permission permission = createMock(org.exist.security.Permission.class);

        expect(userManagementService.getPermissions(resource)).andReturn(permission);
        expect(permission.getOwner()).andReturn(account);
        expect(account.getName()).andReturn("owner").anyTimes();
        replay(userManagementService, permission, account);
        
        UserPrincipal principal = permissionManagement.getOwner(resource);
        assertThat(principal.getName()).isEqualTo("owner");
    }

    @Test
    void setOwnerForResource() throws XMLDBException, PermissionDeniedException {
        org.exist.security.Permission permission = createMock(org.exist.security.Permission.class);
        UserPrincipal userPrincipal = new EXistUserPrincipal(account);
        
        expect(userManagementService.getPermissions(resource)).andReturn(permission);
        permission.setOwner(account);
        replay(userManagementService, permission);
        
        permissionManagement.setOwner(resource, userPrincipal);
        
        verify(userManagementService, permission);
    }

    @Test
    void getGroupForCollection() throws XMLDBException {
        org.exist.security.Permission permission = createMock(org.exist.security.Permission.class);

        expect(userManagementService.getPermissions(collection)).andReturn(permission);
        expect(permission.getGroup()).andReturn(group);
        expect(group.getName()).andReturn("group").anyTimes();
        replay(userManagementService, permission, group);
        
        GroupPrincipal principal = permissionManagement.getGroup(collection);
        assertThat(principal.getName()).isEqualTo("group");
    }

    @Test
    void setGroupForCollection() throws XMLDBException, PermissionDeniedException {
        org.exist.security.Permission permission = createMock(org.exist.security.Permission.class);
        GroupPrincipal groupPrincipal = new EXistGroupPrincipal(group);
        
        expect(userManagementService.getPermissions(collection)).andReturn(permission);
        permission.setGroup(group);
        replay(userManagementService, permission);
        
        permissionManagement.setGroup(collection, groupPrincipal);
        
        verify(userManagementService, permission);
    }

    @Test
    void getGroupForResource() throws XMLDBException {
        org.exist.security.Permission permission = createMock(org.exist.security.Permission.class);

        expect(userManagementService.getPermissions(resource)).andReturn(permission);
        expect(permission.getGroup()).andReturn(group);
        expect(group.getName()).andReturn("group").anyTimes();
        replay(userManagementService, permission, group);
        
        GroupPrincipal principal = permissionManagement.getGroup(resource);
        assertThat(principal.getName()).isEqualTo("group");
    }

    @Test
    void setGroupForResource() throws XMLDBException, PermissionDeniedException {
        org.exist.security.Permission permission = createMock(org.exist.security.Permission.class);
        GroupPrincipal groupPrincipal = new EXistGroupPrincipal(group);
        
        expect(userManagementService.getPermissions(resource)).andReturn(permission);
        permission.setGroup(group);
        replay(userManagementService, permission);
        
        permissionManagement.setGroup(resource, groupPrincipal);
        
        verify(userManagementService, permission);
    }
}
