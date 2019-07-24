/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2015 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
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
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 *
 * @see org.exist.xquery.AbstractInternalModule#AbstractInternalModule(org.exist.xquery.FunctionDef[], java.util.Map) 
 */
public class SecurityManagerModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/securitymanager";
    public final static String PREFIX = "sm";
    private final static String RELEASED_IN_VERSION = "eXist-2.0";
    private final static String DESCRIPTION = "Module for interacting with the Security Manager";

    private final static FunctionDef[] functions = {
        
        new FunctionDef(AccountManagementFunction.FNS_CREATE_ACCOUNT, AccountManagementFunction.class),
        new FunctionDef(AccountManagementFunction.FNS_CREATE_ACCOUNT_WITH_METADATA, AccountManagementFunction.class),
        new FunctionDef(AccountManagementFunction.FNS_CREATE_ACCOUNT_WITH_PERSONAL_GROUP, AccountManagementFunction.class),
        new FunctionDef(AccountManagementFunction.FNS_CREATE_ACCOUNT_WITH_PERSONAL_GROUP_WITH_METADATA, AccountManagementFunction.class),
        new FunctionDef(AccountManagementFunction.FNS_REMOVE_ACCOUNT, AccountManagementFunction.class),
        new FunctionDef(AccountManagementFunction.FNS_PASSWD, AccountManagementFunction.class),
        new FunctionDef(AccountManagementFunction.FNS_PASSWD_HASH, AccountManagementFunction.class),
        
        new FunctionDef(FindUserFunction.FNS_FIND_USERS_BY_USERNAME, FindUserFunction.class),
        new FunctionDef(FindUserFunction.FNS_FIND_USERS_BY_NAME, FindUserFunction.class),
        new FunctionDef(FindUserFunction.FNS_FIND_USERS_BY_NAME_PART, FindUserFunction.class),
        new FunctionDef(FindUserFunction.FNS_LIST_USERS, FindUserFunction.class),
        new FunctionDef(FindUserFunction.FNS_USER_EXISTS, FindUserFunction.class),
        
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

        new FunctionDef(GroupManagementFunction.FNS_CREATE_GROUP, GroupManagementFunction.class),
        new FunctionDef(GroupManagementFunction.FNS_CREATE_GROUP_WITH_METADATA, GroupManagementFunction.class),
        new FunctionDef(GroupManagementFunction.FNS_CREATE_GROUP_WITH_MANAGERS_WITH_METADATA, GroupManagementFunction.class),
        new FunctionDef(GroupManagementFunction.FNS_REMOVE_GROUP, GroupManagementFunction.class),

        new FunctionDef(GroupMembershipFunction.FNS_ADD_GROUP_MEMBER, GroupMembershipFunction.class),
        new FunctionDef(GroupMembershipFunction.FNS_REMOVE_GROUP_MEMBER, GroupMembershipFunction.class),
        new FunctionDef(GroupMembershipFunction.FNS_ADD_GROUP_MANAGER, GroupMembershipFunction.class),
        new FunctionDef(GroupMembershipFunction.FNS_REMOVE_GROUP_MANAGER, GroupMembershipFunction.class),
        new FunctionDef(GroupMembershipFunction.FNS_GET_GROUP_MANAGERS, GroupMembershipFunction.class),
        new FunctionDef(GroupMembershipFunction.FNS_GET_GROUP_MEMBERS, GroupMembershipFunction.class),
        new FunctionDef(GroupMembershipFunction.FNS_IS_DBA, GroupMembershipFunction.class),
        new FunctionDef(GroupMembershipFunction.FNS_SET_USER_PRIMARY_GROUP, GroupMembershipFunction.class),

        new FunctionDef(FindGroupFunction.FNS_LIST_GROUPS, FindGroupFunction.class),
        new FunctionDef(FindGroupFunction.FNS_FIND_GROUPS_BY_GROUPNAME, FindGroupFunction.class),
        new FunctionDef(FindGroupFunction.FNS_FIND_GROUPS_WHERE_GROUPNAME_CONTAINS, FindGroupFunction.class),
        new FunctionDef(FindGroupFunction.FNS_GET_USER_GROUPS, FindGroupFunction.class),
        new FunctionDef(FindGroupFunction.FNS_GET_USER_PRIMARY_GROUP, FindGroupFunction.class),
        new FunctionDef(FindGroupFunction.FNS_GROUP_EXISTS, FindGroupFunction.class),

        new FunctionDef(PermissionsFunction.FNS_GET_PERMISSIONS, PermissionsFunction.class),
        new FunctionDef(PermissionsFunction.FNS_ADD_USER_ACE, PermissionsFunction.class),
        new FunctionDef(PermissionsFunction.FNS_ADD_GROUP_ACE, PermissionsFunction.class),
        new FunctionDef(PermissionsFunction.FNS_INSERT_USER_ACE, PermissionsFunction.class),
        new FunctionDef(PermissionsFunction.FNS_INSERT_GROUP_ACE, PermissionsFunction.class),
        new FunctionDef(PermissionsFunction.FNS_MODIFY_ACE, PermissionsFunction.class),
        new FunctionDef(PermissionsFunction.FNS_REMOVE_ACE, PermissionsFunction.class),
        new FunctionDef(PermissionsFunction.FNS_CLEAR_ACL, PermissionsFunction.class),
        new FunctionDef(PermissionsFunction.FNS_CHMOD, PermissionsFunction.class),
        new FunctionDef(PermissionsFunction.FNS_CHOWN, PermissionsFunction.class),
        new FunctionDef(PermissionsFunction.FNS_CHGRP, PermissionsFunction.class),
        new FunctionDef(PermissionsFunction.FNS_MODE_TO_OCTAL, PermissionsFunction.class),
        new FunctionDef(PermissionsFunction.FNS_OCTAL_TO_MODE, PermissionsFunction.class),

        //<editor-fold desc="Functions on the broker/context current user">
        new FunctionDef(PermissionsFunction.FNS_HAS_ACCESS, PermissionsFunction.class),
        new FunctionDef(IsAuthenticatedFunction.FNS_IS_AUTHENTICATED, IsAuthenticatedFunction.class),
        new FunctionDef(IsAuthenticatedFunction.FNS_IS_EXTERNALLY_AUTHENTICATED, IsAuthenticatedFunction.class),
        new FunctionDef(IdFunction.FNS_ID, IdFunction.class)
        //</editor-fold>
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