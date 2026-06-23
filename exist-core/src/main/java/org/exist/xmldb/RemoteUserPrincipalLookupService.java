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

import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.security.GroupPrincipal;
import org.xmldb.api.security.UserPrincipal;
import org.xmldb.api.security.UserPrincipalLookupService;

public class RemoteUserPrincipalLookupService extends AbstractRemoteService implements UserPrincipalLookupService {
    private EXistUserManagementService userManagementService;

    RemoteUserPrincipalLookupService(RemoteCollection collection) {
        super(collection);
    }

    private EXistUserManagementService getUserManagementService() throws XMLDBException {
        if (userManagementService == null) {
            userManagementService = collection.getService(EXistUserManagementService.class);
        }
        return userManagementService;
    }

    @Override
    public String getName() throws XMLDBException {
        return "UserPrincipalLookupService";
    }

    @Override
    public String getVersion() throws XMLDBException {
        return "1.0";
    }

    @Override
    public UserPrincipal lookupPrincipalByName(final String name) throws XMLDBException {
        return new EXistUserPrincipal(getUserManagementService().getAccount(name));
    }

    @Override
    public GroupPrincipal lookupPrincipalByGroupName(final String name) throws XMLDBException {
        return new EXistGroupPrincipal(getUserManagementService().getGroup(name));
    }
}
