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

import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import org.exist.security.Group;
import org.exist.security.Permission;
import org.exist.security.Account;
import org.exist.security.User;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

import org.exist.security.ACLPermission;
import org.exist.security.AXSchemaType;
import org.exist.security.EXistSchemaType;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SchemaType;
import org.exist.security.internal.aider.ACEAider;

/**
 * @author Modified by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
 */
public class RemoteUserManagementService extends AbstractRemoteService implements EXistUserManagementService {

    public RemoteUserManagementService(final RemoteCollection collection) {
        super(collection);
    }

    @Override
    public String getName() {
        return "UserManagementService";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public void addAccount(final Account user) throws XMLDBException {
        final List<Object> params = new ArrayList<>();
        params.add(user.getName());
        params.add(user.getPassword() == null ? "" : user.getPassword());
        params.add(user.getDigestPassword() == null ? "" : user.getDigestPassword());
        final String[] gl = user.getGroups();
        params.add(gl);
        params.add(user.isEnabled());
        params.add(user.getUserMask());
        final Map<String, String> metadata = new HashMap<>();
        for (final SchemaType key : user.getMetadataKeys()) {
            metadata.put(key.getNamespace(), user.getMetadataValue(key));
        }
        params.add(metadata);
        collection.execute("addAccount", params);
    }

    @Override
    public void addGroup(final Group role) throws XMLDBException {
        final List<Object> params = new ArrayList<>();
        params.add(role.getName());

        //TODO what about group managers?
        final Map<String, String> metadata = new HashMap<>();
        for (final SchemaType key : role.getMetadataKeys()) {
            metadata.put(key.getNamespace(), role.getMetadataValue(key));
        }
        params.add(metadata);

        collection.execute("addGroup", params);
    }

    @Override
    public void setUserPrimaryGroup(final String username, final String groupName) throws XMLDBException {
        final List<Object> params = new ArrayList<>();
        params.add(username);
        params.add(groupName);
        collection.execute("setUserPrimaryGroup", params);
    }

    private List<ACEAider> getACEs(final Permission perm) {
        final List<ACEAider> aces = new ArrayList<>();
        final ACLPermission aclPermission = (ACLPermission) perm;
        for (int i = 0; i < aclPermission.getACECount(); i++) {
            aces.add(new ACEAider(aclPermission.getACEAccessType(i), aclPermission.getACETarget(i), aclPermission.getACEWho(i), aclPermission.getACEMode(i)));
        }
        return aces;
    }

    @Override
    public void setPermissions(final Resource res, final Permission perm) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        final List<Object> params = new ArrayList<>();
        params.add(path);
        params.add(perm.getOwner().getName());
        params.add(perm.getGroup().getName());
        params.add(perm.getMode());
        if (perm instanceof ACLPermission) {
            params.add(getACEs(perm));
        }
        collection.execute("setPermissions", params);
    }

    @Override
    public void setPermissions(final Collection child, final Permission perm) throws XMLDBException {
        final String path = ((RemoteCollection) child).getPath();
        final List<Object> params = new ArrayList<>();
        params.add(path);
        params.add(perm.getOwner().getName());
        params.add(perm.getGroup().getName());
        params.add(perm.getMode());
        if (perm instanceof ACLPermission) {
            params.add(getACEs(perm));
        }
        collection.execute("setPermissions", params);
    }

    @Override
    public void setPermissions(final Collection child, final String owner, final String group, final int mode, final List<ACEAider> aces) throws XMLDBException {
        final String path = ((RemoteCollection) child).getPath();
        final List<Object> params = new ArrayList<>();
        params.add(path);
        params.add(owner);
        params.add(group);
        params.add(mode);
        if (aces != null) {
            params.add(aces);
        }
        collection.execute("setPermissions", params);
    }

    @Override
    public void setPermissions(final Resource res, final String owner, final String group, final int mode, final List<ACEAider> aces) throws XMLDBException {
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        final List<Object> params = new ArrayList<>();
        params.add(path);
        params.add(owner);
        params.add(group);
        params.add(mode);
        if (aces != null) {
            params.add(aces);
        }
        collection.execute("setPermissions", params);
    }

    @Override
    public void chmod(final Resource res, final String mode) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        final List<Object> params = new ArrayList<>();
        params.add(path);
        params.add(mode);
        collection.execute("setPermissions", params);
    }

    @Override
    public void chmod(final Resource res, final int mode) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        final List<Object> params = new ArrayList<>();
        params.add(path);
        params.add(mode);
        collection.execute("setPermissions", params);
    }

    @Override
    public void chmod(final String mode) throws XMLDBException {
        final List<Object> params = new ArrayList<>();
        params.add(collection.getPath());
        params.add(mode);
        collection.execute("setPermissions", params);
    }

    @Override
    public void chmod(final int mode) throws XMLDBException {
        final List<Object> params = new ArrayList<>();
        params.add(collection.getPath());
        params.add(mode);
        collection.execute("setPermissions", params);
    }

    @Override
    public void lockResource(final Resource res, final Account u) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        final List<Object> params = new ArrayList<>();
        params.add(path);
        params.add(u.getName());
        collection.execute("lockResource", params);
    }

    @Override
    public String hasUserLock(final Resource res) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        final List<Object> params = new ArrayList<>();
        params.add(path);
        final String userName = (String) collection.execute("hasUserLock", params);
        return userName != null && !userName.isEmpty() ? userName : null;
    }

    @Override
    public void unlockResource(final Resource res) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        final List<Object> params = new ArrayList<>();
        params.add(path);
        collection.execute("unlockResource", params);
    }

    @Override
    public void chgrp(final String group) throws XMLDBException {
        final List<Object> params = new ArrayList<>();
        params.add(collection.getPath());
        params.add(group);
        collection.execute("chgrp", params);
    }

    @Override
    public void chown(final Account u) throws XMLDBException {
        final List<Object> params = new ArrayList<>();
        params.add(collection.getPath());
        params.add(u.getName());
        collection.execute("chown", params);
    }

    @Override
    public void chown(final Account u, final String group) throws XMLDBException {
        final List<Object> params = new ArrayList<>();
        params.add(collection.getPath());
        params.add(u.getName());
        params.add(group);
        collection.execute("chown", params);
    }

    @Override
    public void chgrp(final Resource res, final String group) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        final List<Object> params = new ArrayList<>();
        params.add(path);
        params.add(group);
        collection.execute("chgrp", params);
    }

    @Override
    public void chown(final Resource res, final Account u) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        final List<Object> params = new ArrayList<>();
        params.add(path);
        params.add(u.getName());
        collection.execute("chown", params);
    }

    @Override
    public void chown(final Resource res, final Account u, final String group) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        final List<Object> params = new ArrayList<>();
        params.add(path);
        params.add(u.getName());
        params.add(group);
        collection.execute("chown", params);
    }

    @Override
    public Instant getSubCollectionCreationTime(final Collection cParent, final String name) throws XMLDBException {
        if (collection == null) {
            throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "collection is null");
        }

        Long creationTime = ((RemoteCollection) cParent).getSubCollectionCreationTime(name);

        if (creationTime == null) {

            final List<Object> params = new ArrayList<>();
            params.add(((RemoteCollection) cParent).getPath());
            params.add(name);

            creationTime = (Long)collection.execute("getSubCollectionCreationTime", params);
        }

        return Instant.ofEpochMilli(creationTime);
    }

    @Override
    public Permission getSubCollectionPermissions(final Collection cParent, final String name) throws XMLDBException {
        if (collection == null) {
            throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "collection is null");
        }

        Permission perm;
        try {
            perm = ((RemoteCollection) cParent).getSubCollectionPermissions(name);

            if (perm == null) {

                final List<Object> params = new ArrayList<>();
                params.add(((RemoteCollection) cParent).getPath());
                params.add(name);

                final Map result = (Map) collection.execute("getSubCollectionPermissions", params);

                final String owner = (String) result.get("owner");
                final String group = (String) result.get("group");
                final int mode = (Integer) result.get("permissions");
                final Stream<ACEAider> aces = extractAces(result.get("acl"));

                perm = getPermission(owner, group, mode, aces);
            }
        } catch (final PermissionDeniedException pde) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, pde.getMessage(), pde);
        }

        return perm;
    }

    @Override
    public Permission getSubResourcePermissions(final Collection cParent, final String name) throws XMLDBException {
        if (collection == null) {
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "collection is null");
        }

        Permission perm;
        try {
            perm = ((RemoteCollection) cParent).getSubCollectionPermissions(name);

            if (perm == null) {

                final List<Object> params = new ArrayList<>();
                params.add(((RemoteCollection) cParent).getPath());
                params.add(name);

                final Map result = (Map) collection.execute("getSubResourcePermissions", params);

                final String owner = (String) result.get("owner");
                final String group = (String) result.get("group");
                final int mode = (Integer) result.get("permissions");
                final Stream<ACEAider> aces = extractAces(result.get("acl"));

                perm = getPermission(owner, group, mode, aces);
            }
        } catch (final PermissionDeniedException pde) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, pde.getMessage(), pde);
        }

        return perm;
    }

    @Override
    public Permission getPermissions(final Collection coll) throws XMLDBException {
        if (coll == null) {
            throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "collection is null");
        }

        try {
            final List<Object> params = new ArrayList<>();
            params.add(((RemoteCollection) coll).getPath());

            final Map result = (Map) collection.execute("getPermissions", params);

            final String owner = (String) result.get("owner");
            final String group = (String) result.get("group");
            final int mode = (Integer) result.get("permissions");
            final Stream<ACEAider> aces = extractAces(result.get("acl"));

            return getPermission(owner, group, mode, aces);
        } catch (final PermissionDeniedException pde) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, pde.getMessage(), pde);
        }
    }

    @Override
    public Permission getPermissions(final Resource res) throws XMLDBException {
        if (res == null) {
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "resource is null");
        }

        //TODO : use dedicated function in XmldbURI
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        try {
            final List<Object> params = new ArrayList<>();
            params.add(path);

            final Map result = (Map) collection.execute("getPermissions", params);

            final String owner = (String) result.get("owner");
            final String group = (String) result.get("group");
            final int mode = (Integer) result.get("permissions");
            final Stream<ACEAider> aces = extractAces(result.get("acl"));

            return getPermission(owner, group, mode, aces);

        } catch (final PermissionDeniedException pde) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, pde.getMessage(), pde);
        }
    }

    @Override
    public Permission[] listResourcePermissions() throws XMLDBException {
        try {
            final List<Object> params = new ArrayList<>();
            params.add(collection.getPath());
            final Map result = (Map) collection.execute("listDocumentPermissions", params);
            final Permission perm[] = new Permission[result.size()];
            int index = 0;
            for (String resource : collection.listResources()) {
                Object[] t = (Object[]) result.get(resource);

                final String owner = (String) t[0];
                final String group = (String) t[1];
                final int mode = (Integer) t[2];
                final Stream<ACEAider> aces = extractAces(t[3]);
                perm[index] = getPermission(owner, group, mode, aces);
                index++;
            }
            return perm;
        } catch (final PermissionDeniedException pde) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, pde.getMessage(), pde);
        }
    }

    @Override
    public Permission[] listCollectionPermissions() throws XMLDBException {
        try {
            final List<Object> params = new ArrayList<>();
            params.add(collection.getPath());
            final Map result = (Map) collection.execute("listCollectionPermissions", params);
            final Permission perm[] = new Permission[result.size()];
            int index = 0;
            for (String collection : collection.listChildCollections()) {
                Object[] t = (Object[]) result.get(collection);

                final String owner = (String) t[0];
                final String group = (String) t[1];
                final int mode = (Integer) t[2];
                final Stream<ACEAider> aces = extractAces(t[3]);

                perm[index] = getPermission(owner, group, mode, aces);
            }
            return perm;
        } catch (final PermissionDeniedException pde) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, pde.getMessage(), pde);
        }
    }

    @Override
    public Account getAccount(final String name) throws XMLDBException {
        try {

            final List<Object> params = new ArrayList<>();
            params.add(name);
            final Map tab = (Map) collection.execute("getAccount", params);

            if (tab == null || tab.isEmpty()) {
                return null;
            }

            final UserAider u;
            if (tab.get("default-group-id") != null) {
                final GroupAider defaultGroup = new GroupAider(
                        (Integer) tab.get("default-group-id"),
                        (String) tab.get("default-group-realmId"),
                        (String) tab.get("default-group-name")
                );

                u = new UserAider(
                        (String) tab.get("realmId"),
                        (String) tab.get("name"),
                        defaultGroup
                );
            } else {
                u = new UserAider(
                        (String) tab.get("realmId"),
                        (String) tab.get("name")
                );
            }

            final Object[] groups = (Object[]) tab.get("groups");
            for (final Object group : groups) {
                u.addGroup((String) group);
            }

            u.setEnabled(Boolean.parseBoolean((String) tab.get("enabled")));
            u.setUserMask((Integer) tab.get("umask"));

            final Map<String, String> metadata = (Map<String, String>) tab.get("metadata");
            for (final Map.Entry<String, String> m : metadata.entrySet()) {
                if (AXSchemaType.valueOfNamespace(m.getKey()) != null) {
                    u.setMetadataValue(AXSchemaType.valueOfNamespace(m.getKey()), m.getValue());
                } else if (EXistSchemaType.valueOfNamespace(m.getKey()) != null) {
                    u.setMetadataValue(EXistSchemaType.valueOfNamespace(m.getKey()), m.getValue());
                }
            }

            return u;
        } catch (final XMLDBException e) {
            return null;
        }
    }

    @Override
    public Account[] getAccounts() throws XMLDBException {
        final Object[] users = (Object[]) collection.execute("getAccounts", Collections.EMPTY_LIST);

        final UserAider[] u = new UserAider[users.length];
        for (int i = 0; i < u.length; i++) {
            final Map tab = (Map) users[i];

            int uid = -1;
            try {
                uid = (Integer) tab.get("uid");
            } catch (final NumberFormatException e) {

            }

            u[i] = new UserAider(uid, (String) tab.get("realmId"), (String) tab.get("name"));
            final Object[] groups = (Object[]) tab.get("groups");
            for (Object group : groups) {
                u[i].addGroup((String) group);
            }

            u[i].setEnabled(Boolean.parseBoolean((String) tab.get("enabled")));
            u[i].setUserMask((Integer) tab.get("umask"));

            final Map<String, String> metadata = (Map<String, String>) tab.get("metadata");
            for (final Map.Entry<String, String> m : metadata.entrySet()) {
                if (AXSchemaType.valueOfNamespace(m.getKey()) != null) {
                    u[i].setMetadataValue(AXSchemaType.valueOfNamespace(m.getKey()), m.getValue());
                } else if (EXistSchemaType.valueOfNamespace(m.getKey()) != null) {
                    u[i].setMetadataValue(EXistSchemaType.valueOfNamespace(m.getKey()), m.getValue());
                }
            }
        }
        return u;
    }

    @Override
    public Group getGroup(final String name) throws XMLDBException {
        try {
            final List<Object> params = new ArrayList<>();
            params.add(name);

            final Map<String, Object> tab = (Map<String, Object>) collection.execute("getGroup", params);

            if (tab != null && !tab.isEmpty()) {
                final Group group = new GroupAider((Integer) tab.get("id"), (String) tab.get("realmId"), (String) tab.get("name"));

                final Object[] managers = (Object[]) tab.get("managers");
                for (final Object manager : managers) {
                    group.addManager(getAccount((String) manager));
                }

                final Map<String, String> metadata = (Map<String, String>) tab.get("metadata");
                for (final Map.Entry<String, String> m : metadata.entrySet()) {
                    if (AXSchemaType.valueOfNamespace(m.getKey()) != null) {
                        group.setMetadataValue(AXSchemaType.valueOfNamespace(m.getKey()), m.getValue());
                    } else if (EXistSchemaType.valueOfNamespace(m.getKey()) != null) {
                        group.setMetadataValue(EXistSchemaType.valueOfNamespace(m.getKey()), m.getValue());
                    }
                }

                return group;
            }
            return null;
        } catch (final PermissionDeniedException pde) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, pde);
        }
    }

    @Override
    public void removeAccount(final Account u) throws XMLDBException {
        final List<Object> params = new ArrayList<>();
        params.add(u.getName());
        collection.execute("removeAccount", params);
    }

    @Override
    public void removeGroup(final Group role) throws XMLDBException {
        final List<Object> params = new ArrayList<>();
        params.add(role.getName());
        collection.execute("removeGroup", params);
    }

    @Override
    public void updateAccount(final Account user) throws XMLDBException {
        final List<Object> params = new ArrayList<>();
        params.add(user.getName());
        params.add(user.getPassword() == null ? "" : user.getPassword());
        params.add(user.getDigestPassword() == null ? "" : user.getDigestPassword());
        final String[] gl = user.getGroups();
        params.add(gl);
        params.add(user.isEnabled());
        params.add(user.getUserMask());
        final Map<String, String> metadata = new HashMap<>();
        for (final SchemaType key : user.getMetadataKeys()) {
            metadata.put(key.getNamespace(), user.getMetadataValue(key));
        }
        params.add(metadata);
        collection.execute("updateAccount", params);
    }

    @Override
    public void updateGroup(final Group group) throws XMLDBException {
        try {
            final List<Object> params = new ArrayList<>();
            params.add(group.getName());

            final String managers[] = new String[group.getManagers().size()];
            for (int i = 0; i < managers.length; i++) {
                managers[i] = group.getManagers().get(i).getName();
            }
            params.add(managers);

            final Map<String, String> metadata = new HashMap<>();
            for (final SchemaType key : group.getMetadataKeys()) {
                metadata.put(key.getNamespace(), group.getMetadataValue(key));
            }
            params.add(metadata);

            collection.execute("updateGroup", params);
        } catch (final PermissionDeniedException pde) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, pde.getMessage(), pde);
        }
    }

    @Override
    public String[] getGroupMembers(final String groupName) throws XMLDBException {
        final List<Object> params = new ArrayList<>();
        params.add(groupName);

        final Object[] groupMembersResults = (Object[]) collection.execute("getGroupMembers", params);

        final String[] groupMembers = new String[groupMembersResults.length];
        for (int i = 0; i < groupMembersResults.length; i++) {
            groupMembers[i] = groupMembersResults[i].toString();
        }
        return groupMembers;
    }

    @Override
    public void addAccountToGroup(final String accountName, final String groupName) throws XMLDBException {
        final List<Object> params = new ArrayList<>();
        params.add(accountName);
        params.add(groupName);

        collection.execute("addAccountToGroup", params);
    }

    @Override
    public void addGroupManager(final String manager, final String groupName) throws XMLDBException {
        final List<Object> params = new ArrayList<>();
        params.add(manager);
        params.add(groupName);

        collection.execute("addGroupManager", params);
    }

    @Override
    public void removeGroupManager(final String groupName, final String manager) throws XMLDBException {
        final List<Object> params = new ArrayList<>();
        params.add(groupName);
        params.add(manager);

        collection.execute("removeGroupManager", params);
    }

    @Override
    public void addUserGroup(final Account user) throws XMLDBException {
        final List<Object> params = new ArrayList<>();
        params.add(user.getName());
        final String[] gl = user.getGroups();
        params.add(gl);
        collection.execute("updateAccount", params);
    }

    @Override
    public void removeGroupMember(final String group, final String account) throws XMLDBException {
        final List<Object> params = new ArrayList<>();
        params.add(group);
        params.add(account);
        collection.execute("removeGroupMember", params);
    }

    @Override
    public String[] getGroups() throws XMLDBException {
        final Object[] v = (Object[]) collection.execute("getGroups", Collections.EMPTY_LIST);
        final String[] groups = new String[v.length];
        System.arraycopy(v, 0, groups, 0, v.length);
        return groups;
    }

    @Override
    public void addUser(final User user) throws XMLDBException {
        final Account account = new UserAider(user.getName());
        addAccount(account);
    }

    @Override
    public void updateUser(final User user) throws XMLDBException {
        final Account account = new UserAider(user.getName());
        account.setPassword(user.getPassword());
        //TODO: groups
        updateAccount(account);
    }

    @Override
    public User getUser(final String name) throws XMLDBException {
        return getAccount(name);
    }

    @Override
    public User[] getUsers() throws XMLDBException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void removeUser(final User user) throws XMLDBException {
		// TODO Auto-generated method stub

    }

    @Override
    public void lockResource(final Resource res, final User u) throws XMLDBException {
        final Account account = new UserAider(u.getName());
        lockResource(res, account);
    }
}
