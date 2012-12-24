/*
 *  eXist-db SecurityManager Module Extension
 *  Copyright (C) 2012 Adam Retter <adam@existsolutions.com>
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
package org.exist.xquery.functions.securitymanager;

import java.util.List;
import java.util.Map;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * eXist Security Manager Module Extension
 *
 * An extension module for interacting with eXist-db Security Manager
 *
 * @author Adam Retter <adam@existsolutions.com>
 *
 * @see org.exist.xquery.AbstractInternalModule#AbstractInternalModule(org.exist.xquery.FunctionDef[], java.util.Map) 
 */
public class SecurityManagerModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/securitymanager";
    public final static String PREFIX = "sm";
    private final static String RELEASED_IN_VERSION = "eXist-2.0";
    private final static String DESCRIPTION = "Module for interacting with the Security Manager";

    private final static FunctionDef[] functions = {
        new FunctionDef(FindUserFunction.FNS_FIND_USERS_BY_USERNAME, FindUserFunction.class),
        new FunctionDef(FindUserFunction.FNS_FIND_USERS_BY_NAME, FindUserFunction.class),
        new FunctionDef(FindUserFunction.FNS_FIND_USERS_BY_NAME_PART, FindUserFunction.class),
        new FunctionDef(FindUserFunction.FNS_LIST_USERS, FindUserFunction.class),

        new FunctionDef(UMaskFunction.FNS_GET_UMASK, UMaskFunction.class),
        new FunctionDef(UMaskFunction.FNS_SET_UMASK, UMaskFunction.class),
        
        new FunctionDef(GetPrincipalMetadataFunction.FNS_GET_ALL_ACCOUNT_METADATA_KEYS, GetPrincipalMetadataFunction.class),
        new FunctionDef(GetPrincipalMetadataFunction.FNS_GET_ACCOUNT_METADATA_KEYS, GetPrincipalMetadataFunction.class),
        new FunctionDef(GetPrincipalMetadataFunction.FNS_GET_ACCOUNT_METADATA, GetPrincipalMetadataFunction.class),
        new FunctionDef(GetPrincipalMetadataFunction.FNS_GET_ALL_GROUP_METADATA_KEYS, GetPrincipalMetadataFunction.class),
        new FunctionDef(GetPrincipalMetadataFunction.FNS_GET_GROUP_METADATA_KEYS, GetPrincipalMetadataFunction.class),
        new FunctionDef(GetPrincipalMetadataFunction.FNS_GET_GROUP_METADATA, GetPrincipalMetadataFunction.class),
        
        new FunctionDef(SetPrincipalMetadataFunction.FNS_SET_ACCOUNT_METADATA, SetPrincipalMetadataFunction.class),
        new FunctionDef(SetPrincipalMetadataFunction.FNS_SET_GROUP_METADATA, SetPrincipalMetadataFunction.class),

        new FunctionDef(AccountStatusFunction.FNS_IS_ACCOUNT_ENABLED, AccountStatusFunction.class),
        new FunctionDef(AccountStatusFunction.FNS_SET_ACCOUNT_ENABLED, AccountStatusFunction.class),
        
        new FunctionDef(DeleteGroupFunction.signatures[0], DeleteGroupFunction.class),
        new FunctionDef(DeleteGroupFunction.signatures[1], DeleteGroupFunction.class),

        new FunctionDef(GroupMembershipFunctions.FNS_GET_GROUP_MANAGERS, GroupMembershipFunctions.class),
        new FunctionDef(GroupMembershipFunctions.FNS_GET_GROUP_MEMBERS, GroupMembershipFunctions.class),

        new FunctionDef(FindGroupFunction.FNS_LIST_GROUPS, FindGroupFunction.class),
        new FunctionDef(FindGroupFunction.FNS_FIND_GROUPS_BY_GROUPNAME, FindGroupFunction.class),
        new FunctionDef(FindGroupFunction.FNS_FIND_GROUPS_WHERE_GROUPNAME_CONTANINS, FindGroupFunction.class),
        new FunctionDef(FindGroupFunction.FNS_GET_USER_GROUPS, FindGroupFunction.class),
        new FunctionDef(FindGroupFunction.FNS_GET_GROUPS, FindGroupFunction.class),

        new FunctionDef(PermissionsFunctions.signatures[0], PermissionsFunctions.class),
        new FunctionDef(PermissionsFunctions.signatures[1], PermissionsFunctions.class),
        new FunctionDef(PermissionsFunctions.signatures[2], PermissionsFunctions.class),
        new FunctionDef(PermissionsFunctions.signatures[3], PermissionsFunctions.class),
        new FunctionDef(PermissionsFunctions.signatures[4], PermissionsFunctions.class),
        new FunctionDef(PermissionsFunctions.signatures[5], PermissionsFunctions.class),
        new FunctionDef(PermissionsFunctions.signatures[6], PermissionsFunctions.class),
        new FunctionDef(PermissionsFunctions.signatures[7], PermissionsFunctions.class),
        new FunctionDef(PermissionsFunctions.signatures[8], PermissionsFunctions.class),
        new FunctionDef(PermissionsFunctions.signatures[9], PermissionsFunctions.class),
        new FunctionDef(PermissionsFunctions.signatures[10], PermissionsFunctions.class),
        new FunctionDef(PermissionsFunctions.signatures[11], PermissionsFunctions.class),

        new FunctionDef(IsExternallyAuthenticated.signature, IsExternallyAuthenticated.class)
    };

    public SecurityManagerModule(Map<String, List<? extends Object>> parameters) {
        super(functions, parameters);
    }

    @Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}