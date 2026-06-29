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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.security.GroupPrincipal;
import org.xmldb.api.security.UserPrincipal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

@ExtendWith(EasyMockExtension.class)
class RemoteUserPrincipalLookupServiceTest {
    @Mock
    EXistUserManagementService userManagementService;
    RemoteUserPrincipalLookupService service;

    @BeforeEach
    void setUp() throws XMLDBException {
        RemoteCollection collection = createMock(RemoteCollection.class);

        expect(collection.getService(EXistUserManagementService.class)).andReturn(userManagementService).anyTimes();
        replay(collection);
        
        service = new RemoteUserPrincipalLookupService(collection);
    }

    @Test
    void getName() throws XMLDBException {
        assertThat(service.getName()).isEqualTo("UserPrincipalLookupService");
    }

    @Test
    void getVersion() throws XMLDBException {
        assertThat(service.getVersion()).isEqualTo("1.0");
    }

    @Test
    void lookupPrincipalByName() throws XMLDBException {
        org.exist.security.Account account = createMock(org.exist.security.Account.class);
        expect(userManagementService.getAccount("testuser")).andReturn(account);
        expect(account.getName()).andReturn("testuser");
        replay(userManagementService, account);

        UserPrincipal principal = service.lookupPrincipalByName("testuser");
        assertThat(principal.getName()).isEqualTo("testuser");
        verify(userManagementService, account);
    }

    @Test
    void lookupPrincipalByGroupName() throws XMLDBException {
        org.exist.security.Group group = createMock(org.exist.security.Group.class);
        expect(userManagementService.getGroup("testgroup")).andReturn(group);
        expect(group.getName()).andReturn("testgroup");
        replay(userManagementService, group);

        GroupPrincipal principal = service.lookupPrincipalByGroupName("testgroup");
        assertThat(principal.getName()).isEqualTo("testgroup");
        verify(userManagementService, group);
    }
}
