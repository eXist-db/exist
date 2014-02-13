package org.exist.xmldb;

import java.util.*;
import org.apache.xmlrpc.XmlRpcException;
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
import org.exist.security.internal.aider.PermissionAiderFactory;


/*************************************************
 * Modified by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
**************************************/

public class RemoteUserManagementService implements EXistUserManagementService {

	private RemoteCollection parent;

	public RemoteUserManagementService(RemoteCollection collection) {
		parent = collection;
	}

	/**
	 *  Add a new user account
	 *
	 *@param  user                The user to be added
	 *@exception  XMLDBException  Description of the Exception
	 */
    @Override
    public void addAccount(final Account user) throws XMLDBException {
        try {
            final List<Object> params = new ArrayList<Object>(12);
            params.add(user.getName());
            params.add(user.getPassword() == null ? "" : user.getPassword());
            params.add(user.getDigestPassword() == null ? "" : user.getDigestPassword());
            final String[] gl = user.getGroups();
            params.add(gl);
            params.add(user.isEnabled());
            params.add(user.getUserMask());
            final Map<String, String> metadata = new HashMap<String, String>();
            for(final SchemaType key : user.getMetadataKeys()) {
                metadata.put(key.getNamespace(), user.getMetadataValue(key));
            }
            params.add(metadata);

            parent.getClient().execute("addAccount", params);
        } catch(final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public void addGroup(final Group role) throws XMLDBException {
        try {
            final List<Object> params = new ArrayList<Object>(2);
            params.add(role.getName());
            
            //TODO what about group managers?
            
            final Map<String, String> metadata = new HashMap<String, String>();
            for(final SchemaType key : role.getMetadataKeys()) {
                metadata.put(key.getNamespace(), role.getMetadataValue(key));
            }
            params.add(metadata);
            
            parent.getClient().execute("addGroup", params);
        } catch(final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public void setUserPrimaryGroup(final String username, final String groupName) throws XMLDBException {
        final List<Object> params = new ArrayList<Object>(2);
        params.add(username);
        params.add(groupName);
        
        try {
            parent.getClient().execute("setUserPrimaryGroup", params);
        } catch(final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }
    
    private List<ACEAider> getACEs(Permission perm) {
        final List<ACEAider> aces = new ArrayList<ACEAider>();
        final ACLPermission aclPermission = (ACLPermission)perm;
        for(int i = 0; i < aclPermission.getACECount(); i++) {
            aces.add(new ACEAider(aclPermission.getACEAccessType(i), aclPermission.getACETarget(i), aclPermission.getACEWho(i), aclPermission.getACEMode(i)));
        }
        return aces;
    }

    /**
     * Change permissions for a resource.
     */
    @Override
    public void setPermissions(Resource res, Permission perm) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
        final String path = ((RemoteCollection)res.getParentCollection()).getPath() + "/" + res.getId();
        try {
            final List<Object> params = new ArrayList<Object>(5);
            params.add(path);
            params.add(perm.getOwner().getName());
            params.add(perm.getGroup().getName());
            params.add(Integer.valueOf(perm.getMode()));
            if(perm instanceof ACLPermission) {
                params.add(getACEs(perm));
            }

            parent.getClient().execute("setPermissions", params);
            
        } catch (final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    /**
     * Change permissions for a resource.
     */
    @Override
    public void setPermissions(Collection child, Permission perm) throws XMLDBException {
        final String path = ((RemoteCollection)child).getPath();
        try {
            final List<Object> params = new ArrayList<Object>(5);
            params.add(path);
            params.add(perm.getOwner().getName());
            params.add(perm.getGroup().getName());
            params.add(Integer.valueOf(perm.getMode()));
            if(perm instanceof ACLPermission) {
                params.add(getACEs(perm));
            }

            parent.getClient().execute("setPermissions", params);

        } catch (final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public void setPermissions(Collection child, String owner, String group, int mode, List<ACEAider> aces) throws XMLDBException {
        final String path = ((RemoteCollection)child).getPath();
        try {
            final List<Object> params = new ArrayList<Object>(5);
            params.add(path);
            params.add(owner);
            params.add(group);
            params.add(Integer.valueOf(mode));
            if(aces != null) {
                params.add(aces);
            }

            parent.getClient().execute("setPermissions", params);

        } catch (final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }
    
    @Override
    public void setPermissions(Resource res, String owner, String group, int mode, List<ACEAider> aces) throws XMLDBException {
        final String path = ((RemoteCollection)res.getParentCollection()).getPath() + "/" + res.getId();
        try {
            final List<Object> params = new ArrayList<Object>(5);
            params.add(path);
            params.add(owner);
            params.add(group);
            params.add(Integer.valueOf(mode));
            if(aces != null) {
                params.add(aces);
            }

            parent.getClient().execute("setPermissions", params);

        } catch (final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }
    
    

	/**
	 *  Change access mode of a resource
	 *
	 *@param  mode                Access mode
	 *@param  res                 Description of the Parameter
	 *@exception  XMLDBException  Description of the Exception
	 */
	public void chmod(Resource res, String mode) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
		final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
		try {
            final List<Object> params = new ArrayList<Object>(2);
			params.add(path);
			params.add(mode);
			parent.getClient().execute("setPermissions", params);
		} catch (final XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }

	/**
	 * @see org.exist.xmldb.UserManagementService#chmod(org.xmldb.api.base.Resource, int)
	 */
	public void chmod(Resource res, int mode) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
		final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
		try {
            final List<Object> params = new ArrayList<Object>(2);
			params.add(path);
			params.add(Integer.valueOf(mode));
			parent.getClient().execute("setPermissions", params);
		} catch (final XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }

	/**
	 *  Change access mode of the current collection
	 *
	 *@param  mode                Access mode
	 *@exception  XMLDBException  Description of the Exception
	 */
    @Override
    public void chmod(String mode) throws XMLDBException {
        try {
            final List<Object> params = new ArrayList<Object>(2);
            params.add(parent.getPath());
            params.add(mode);
            
            parent.getClient().execute("setPermissions", params);
        } catch (final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

	/**
	 * @see org.exist.xmldb.UserManagementService#chmod(int)
	 */
    @Override
    public void chmod(int mode) throws XMLDBException {
        try {
            final List<Object> params = new ArrayList<Object>(2);
            params.add(parent.getPath());
            params.add(Integer.valueOf(mode));

            parent.getClient().execute("setPermissions", params);
        } catch (final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

	/* (non-Javadoc)
	 * @see org.exist.xmldb.UserManagementService#lockResource(org.xmldb.api.base.Resource, org.exist.security.User)
	 */
	public void lockResource(Resource res, Account u) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
		final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
		try {
            final List<Object> params = new ArrayList<Object>(2);
			params.add(path);
			params.add(u.getName());
			parent.getClient().execute("lockResource", params);
		} catch (final XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }
	
	/* (non-Javadoc)
	 * @see org.exist.xmldb.UserManagementService#hasUserLock(org.xmldb.api.base.Resource)
	 */
	public String hasUserLock(Resource res) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
		final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
		try {
            final List<Object> params = new ArrayList<Object>(1);
			params.add(path);
			final String userName = (String)parent.getClient().execute("hasUserLock", params);
			return userName != null && userName.length() > 0 ? userName : null;
		} catch (final XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }
	
	/* (non-Javadoc)
	 * @see org.exist.xmldb.UserManagementService#unlockResource(org.xmldb.api.base.Resource)
	 */
	public void unlockResource(Resource res) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
		final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
		try {
            final List<Object> params = new ArrayList<Object>(1);
			params.add(path);
			parent.getClient().execute("unlockResource", params);
		} catch (final XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }

    /**
     * Change the owner gid of the current collection
     *
     * @param  group                  Description of the Parameter
     * @exception  XMLDBException  Description of the Exception
     */
    @Override
    public void chgrp(String group) throws XMLDBException {
        try {
            final List<Object> params = new ArrayList<Object>(4);
            params.add(parent.getPath());
            params.add(group);

            parent.getClient().execute("chgrp", params);
        } catch (final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    /**
     * Change the owner uid of the current collection
     *
     * @param  u                   Description of the Parameter
     * @exception  XMLDBException  Description of the Exception
     */
    @Override
    public void chown(Account u) throws XMLDBException {
        try {
            final List<Object> params = new ArrayList<Object>(4);
            params.add(parent.getPath());
            params.add(u.getName());

            parent.getClient().execute("chown", params);
        } catch (final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

	/**
	 * Change the owner of the current collection
	 *
	 * @param  u                   Description of the Parameter
	 * @param  group               Description of the Parameter
	 * @exception  XMLDBException  Description of the Exception
	 */
    @Override
    public void chown(Account u, String group) throws XMLDBException {
        try {
            final List<Object> params = new ArrayList<Object>(4);
            params.add(parent.getPath());
            params.add(u.getName());
            params.add(group);

            parent.getClient().execute("chown", params);
        } catch (final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    /**
     * Change the owner uid of a resource
     *
     * @param  res                 Resource
     * @param  u                   The new owner of the resource
     * @exception  XMLDBException  Description of the Exception
     */
    @Override
    public void chgrp(Resource res, String group) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        try {
            final List<Object> params = new ArrayList<Object>(4);
            params.add(path);
            params.add(group);

            parent.getClient().execute("chgrp", params);
        } catch (final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    /**
     * Change the owner uid of a resource
     *
     * @param  res                 Resource
     * @param  u                   The new owner of the resource
     * @exception  XMLDBException  Description of the Exception
     */
    @Override
    public void chown(Resource res, Account u) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        try {
            final List<Object> params = new ArrayList<Object>(4);
            params.add(path);
            params.add(u.getName());

            parent.getClient().execute("chown", params);
        } catch (final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    /**
     *  Change the owner of a resource
     *
     *@param  res                 Resource
     *@param  u                   The new owner of the resource
     *@param  group               The owner group
     *@exception  XMLDBException  Description of the Exception
     */
    @Override
    public void chown(Resource res, Account u, String group) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        try {
            final List<Object> params = new ArrayList<Object>(4);
            params.add(path);
            params.add(u.getName());
            params.add(group);

            parent.getClient().execute("chown", params);
        } catch (final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

	/**
	 *  Gets the name attribute of the UserManagementServiceImpl object
	 *
	 *@return    The name value
	 */
	public String getName() {
		return "UserManagementService";
	}

    @Override
    public Date getSubCollectionCreationTime(Collection cParent, String name) throws XMLDBException {
        if(parent == null) {
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "collection is null");
        }

        Long creationTime;
        try {
            creationTime = ((RemoteCollection)cParent).getSubCollectionCreationTime(name);

            if(creationTime == null) {
            
                final List<Object> params = new ArrayList<Object>(2);
                params.add(((RemoteCollection) cParent).getPath());
                params.add(name);

                creationTime = ((Long)parent.getClient().execute("getSubCollectionCreationTime", params)).longValue();
            }   
        } catch(final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }

        return new Date(creationTime);
    }

    @Override
    public Permission getSubCollectionPermissions(Collection cParent, String name) throws XMLDBException {
        if(parent == null) {
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "collection is null");
        }

        Permission perm = null;
        try {
            perm = ((RemoteCollection)cParent).getSubCollectionPermissions(name);

            if(perm == null) {
            
                final List<Object> params = new ArrayList<Object>(2);
                params.add(((RemoteCollection) cParent).getPath());
                params.add(name);

                final HashMap<?,?> result = (HashMap<?,?>) parent.getClient().execute("getSubCollectionPermissions", params);

                final String owner = (String)result.get("owner");
                final String group = (String)result.get("group");
                final int mode = ((Integer)result.get("permissions")).intValue();
                final Object[] acl = (Object[])result.get("acl");
                List aces = null;
                if (acl != null)
                	{aces = Arrays.asList(acl);}

                perm = getPermission(owner, group, mode, (List<ACEAider>)aces);
            }   
        } catch(final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch(final PermissionDeniedException pde) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, pde.getMessage(), pde);
        }

        return perm;
    }

    @Override
    public Permission getSubResourcePermissions(Collection cParent, String name) throws XMLDBException {
        if(parent == null) {
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "collection is null");
        }

        Permission perm = null;
        try {
            perm = ((RemoteCollection)cParent).getSubCollectionPermissions(name);

            if(perm == null) {
            
                final List<Object> params = new ArrayList<Object>(2);
                params.add(((RemoteCollection) cParent).getPath());
                params.add(name);

                final HashMap<?,?> result = (HashMap<?,?>) parent.getClient().execute("getSubResourcePermissions", params);

                final String owner = (String)result.get("owner");
                final String group = (String)result.get("group");
                final int mode = ((Integer)result.get("permissions")).intValue();
                final Object[] acl = (Object[])result.get("acl");
                List aces = null;
                if (acl != null)
                	{aces = Arrays.asList(acl);}

                perm = getPermission(owner, group, mode, (List<ACEAider>)aces);
            }   
        } catch(final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch(final PermissionDeniedException pde) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, pde.getMessage(), pde);
        }

        return perm;
    }

        
        
        
    /**
     *  Get current permissions for a collection
     *
     *@param  coll                Collection
     *@return                     The permissions value
     *@exception  XMLDBException  Description of the Exception
     */
    @Override
    public Permission getPermissions(Collection coll) throws XMLDBException {
        if(coll == null) {
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "collection is null");
        }

        try {
            final List<Object> params = new ArrayList<Object>(1);
            params.add(((RemoteCollection) coll).getPath());

            final HashMap<?,?> result = (HashMap<?,?>) parent.getClient().execute("getPermissions", params);

            final String owner = (String)result.get("owner");
            final String group = (String)result.get("group");
            final int mode = ((Integer)result.get("permissions")).intValue();
            final Object[] acl = (Object[])result.get("acl");
            List aces = null;
            if(acl != null) {
                    aces = Arrays.asList(acl);
            }

            return getPermission(owner, group, mode, (List<ACEAider>)aces);

        } catch(final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch(final PermissionDeniedException pde) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, pde.getMessage(), pde);
        }
    }

    private Permission getPermission(final String owner, final String group, final int mode, final List listOfAces) throws PermissionDeniedException {
        final Permission perm = PermissionAiderFactory.getPermission(owner, group, mode);
        if(perm instanceof ACLPermission && listOfAces != null && !listOfAces.isEmpty()) {
            final ACLPermission aclPermission = (ACLPermission)perm;
            for(final Object listOfAcesItem : listOfAces) {
                if(listOfAcesItem instanceof ACEAider) {
                    final ACEAider ace = (ACEAider)listOfAcesItem;
                    aclPermission.addACE(ace.getAccessType(), ace.getTarget(), ace.getWho(), ace.getMode());
                }
            }
        }
        return perm;
    }

    /**
     *  Get current permissions for a resource
     *
     *@param  res                 Description of the Parameter
     *@return                     The permissions value
     *@exception  XMLDBException  Description of the Exception
     */
    @Override
    public Permission getPermissions(Resource res) throws XMLDBException {
        if(res == null) {
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "resource is null");
        }

        //TODO : use dedicated function in XmldbURI
        final String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        try {
            final List<Object> params = new ArrayList<Object>(1);
            params.add(path);

            final HashMap<?,?> result = (HashMap<?,?>) parent.getClient().execute("getPermissions", params);

            final String owner = (String)result.get("owner");
            final String group = (String)result.get("group");
            final int mode = ((Integer)result.get("permissions")).intValue();
            final Object[] acl = (Object[])result.get("acl");
            List aces = null;
            if(acl != null) {
                aces = Arrays.asList(acl);
            }

            return getPermission(owner, group, mode, (List<ACEAider>)aces);

        } catch (final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch(final PermissionDeniedException pde) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, pde.getMessage(), pde);
        }
    }

    @Override
    public Permission[] listResourcePermissions() throws XMLDBException {
        try {
            final List<Object> params = new ArrayList<Object>(1);
            params.add(parent.getPath());
            final HashMap<?,?> result = (HashMap<?,?>) parent.getClient().execute("listDocumentPermissions", params);
            final Permission perm[] = new Permission[result.size()];
            final String[] resources = parent.listResources();
            Object[] t;
            for(int i = 0; i < resources.length; i++) {
                t = (Object[]) result.get(resources[i]);

                final String owner = (String)t[0];
                final String group = (String)t[1];
                final int mode = ((Integer)t[2]).intValue();
                List aces = null;
                if(t.length == 4) {
                    aces = Arrays.asList(t[3]);
                }
                perm[i] = getPermission(owner, group, mode, (List<ACEAider>)aces);
            }
            return perm;
        } catch (final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch(final PermissionDeniedException pde) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, pde.getMessage(), pde);
        }
    }

    @Override
    public Permission[] listCollectionPermissions() throws XMLDBException {
        try {
            final List<Object> params = new ArrayList<Object>(1);
            params.add(parent.getPath());
            final HashMap<?,?> result = (HashMap<?,?>) parent.getClient().execute("listCollectionPermissions", params);
            final Permission perm[] = new Permission[result.size()];
            final String collections[] = parent.listChildCollections();
            Object[] t;
            for (int i = 0; i < collections.length; i++) {
                t = (Object[]) result.get(collections[i]);

                final String owner = (String)t[0];
                final String group = (String)t[1];
                final int mode = ((Integer)t[2]).intValue();
                List aces = null;
                if(t.length == 4) {
                    aces = Arrays.asList(t[3]);
                }
                
                perm[i] = getPermission(owner, group, mode, (List<ACEAider>)aces);
            }
            return perm;
        } catch(final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch(final PermissionDeniedException pde) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, pde.getMessage(), pde);
        }
    }

	/**
	 *  Gets the property attribute of the UserManagementServiceImpl object
	 *
	 *@param  property            Description of the Parameter
	 *@return                     The property value
	 *@exception  XMLDBException  Description of the Exception
	 */
	public String getProperty(String property) throws XMLDBException {
		return null;
	}

	/**
	 *  Get user information for specified user
	 *
	 *@param  name                Description of the Parameter
	 *@return                     The user value
	 *@exception  XMLDBException  Description of the Exception
	 */
    @Override
    public Account getAccount(String name) throws XMLDBException { 
        try {
            
            final List<Object> params = new ArrayList<Object>(1);
            params.add(name);
            final HashMap<?,?> tab = (HashMap<?,?>) parent.getClient().execute("getAccount", params);

            if(tab == null || tab.isEmpty()) {
                return null;
            }
                     
            final UserAider u;
            if(tab.get("default-group-id") != null) {
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
            for(final Object group : groups) {
                u.addGroup((String) group);
            }
            
            u.setEnabled(Boolean.valueOf((String)tab.get("enabled")));
            u.setUserMask((Integer)tab.get("umask"));
            
            final Map<String, String> metadata = (Map<String, String>)tab.get("metadata");
            for(final String key : metadata.keySet()) {
                if(AXSchemaType.valueOfNamespace(key) != null) {
                    u.setMetadataValue(AXSchemaType.valueOfNamespace(key), metadata.get(key));
                } else if(EXistSchemaType.valueOfNamespace(key) != null) {
                    u.setMetadataValue(EXistSchemaType.valueOfNamespace(key), metadata.get(key));
                }
            }
            
            return u;
                        
        } catch (final XmlRpcException e) {
            return null;
        }
    }

    /**
     * Get a list of all users currently defined
     *
     * @return The user accounts
     * @exception XMLDBException Description of the Exception
     */
    @Override
    public Account[] getAccounts() throws XMLDBException {
        try {
            final Object[] users = (Object[]) parent.getClient().execute("getAccounts", new ArrayList<Object>());
            
            final UserAider[] u = new UserAider[users.length];
            for (int i = 0; i < u.length; i++) {
                final HashMap<?,?> tab = (HashMap<?,?>) users[i];

                int uid = -1;
                try {
                    uid = (Integer)tab.get("uid");
                } catch (final java.lang.NumberFormatException e) {
                    
                }
					
                u[i] = new UserAider(uid, (String) tab.get("realmId"), (String) tab.get("name"));
                final Object[] groups = (Object[]) tab.get("groups");
                for (int j = 0; j < groups.length; j++) {
                    u[i].addGroup((String) groups[j]);
                }
                
                u[i].setEnabled(Boolean.valueOf((String)tab.get("enabled")));
                u[i].setUserMask((Integer)tab.get("umask"));
                
                
                final Map<String, String> metadata = (Map<String, String>)tab.get("metadata");
                for(final String key : metadata.keySet()) {
                    if(AXSchemaType.valueOfNamespace(key) != null) {
                        u[i].setMetadataValue(AXSchemaType.valueOfNamespace(key), metadata.get(key));
                    } else if(EXistSchemaType.valueOfNamespace(key) != null) {
                        u[i].setMetadataValue(EXistSchemaType.valueOfNamespace(key), metadata.get(key));
                    }
                }
            }
            return u;
        } catch (final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

        @Override
	public Group getGroup(final String name) throws XMLDBException {
            try {
                final List<Object> params = new ArrayList<Object>(1);
                params.add(name);
                
                final Map<String,Object> tab = (HashMap<String,Object>) parent.getClient().execute("getGroup", params);
                
                if(tab != null && !tab.isEmpty()) {
                    final Group group = new GroupAider((Integer)tab.get("id"), (String) tab.get("realmId"), (String) tab.get("name"));
                    
                    final Object[] managers = (Object[]) tab.get("managers");
                    for(final Object manager : managers) {
                        group.addManager(getAccount((String)manager));
                    }
                    
                    final Map<String, String> metadata = (Map<String, String>)tab.get("metadata");
                    for(final String key : metadata.keySet()) {
                        if(AXSchemaType.valueOfNamespace(key) != null) {
                            group.setMetadataValue(AXSchemaType.valueOfNamespace(key), metadata.get(key));
                        } else if(EXistSchemaType.valueOfNamespace(key) != null) {
                            group.setMetadataValue(EXistSchemaType.valueOfNamespace(key), metadata.get(key));
                        }
                    }
                    
                    return group;
                }
                return null;
            } catch(final XmlRpcException xre) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, xre);
            } catch(final PermissionDeniedException pde) {
                throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, pde);
            }
    }

	/**
	 *  Gets the version attribute of the UserManagementServiceImpl object
	 *
	 *@return    The version value
	 */
	public String getVersion() {
		return "1.0";
	}

	/**
	 *  Remove user.
	 *
	 *@param  u   User
	 *@exception  XMLDBException
	 */
	public void removeAccount(Account u) throws XMLDBException {
		try {
            final List<Object> params = new ArrayList<Object>(1);
			params.add(u.getName());
			parent.getClient().execute("removeAccount", params);
		} catch (final XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }

	public void removeGroup(Group role) throws XMLDBException {
		try {
            final List<Object> params = new ArrayList<Object>(1);
			params.add(role.getName());
			parent.getClient().execute("removeGroup", params);
		} catch (final XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }

	/**
	 *  Sets the collection attribute of the UserManagementServiceImpl object
	 *
	 *@param  collection          The new collection value
	 *@exception  XMLDBException  Description of the Exception
	 */
	public void setCollection(Collection collection) throws XMLDBException {
		this.parent = (RemoteCollection) collection;
	}

	/**
	 *  Sets the property attribute of the UserManagementServiceImpl object
	 *
	 *@param  property            The new property value
	 *@param  value               The new property value
	 *@exception  XMLDBException  Description of the Exception
	 */
	public void setProperty(String property, String value) throws XMLDBException {
	}

	/**
	 *  Update the specified user
	 *
	 *@param  user                Description of the Parameter
	 *@exception  XMLDBException  Description of the Exception
	 */
	@Override
        public void updateAccount(final Account user) throws XMLDBException {
            try {
                final List<Object> params = new ArrayList<Object>(12);
                params.add(user.getName());
                params.add(user.getPassword() == null ? "" : user.getPassword());
                params.add(user.getDigestPassword() == null ? "" : user.getDigestPassword());
                final String[] gl = user.getGroups();
                params.add(gl);
                params.add(user.isEnabled());
                params.add(user.getUserMask());
                final Map<String, String> metadata = new HashMap<String, String>();
                for(final SchemaType key : user.getMetadataKeys()) {
                    metadata.put(key.getNamespace(), user.getMetadataValue(key));
                }
                params.add(metadata);
                parent.getClient().execute("updateAccount", params);
            } catch (final XmlRpcException e) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
            }
        }

        @Override
        public void updateGroup(final Group group) throws XMLDBException {
            try {
                final List<Object> params = new ArrayList<Object>(12);
                params.add(group.getName());

                final String managers[] = new String[group.getManagers().size()];
                for(int i = 0; i < managers.length; i++) {
                    managers[i] = group.getManagers().get(i).getName();
                }
                params.add(managers);

                final Map<String, String> metadata = new HashMap<String, String>();
                for(final SchemaType key : group.getMetadataKeys()) {
                    metadata.put(key.getNamespace(), group.getMetadataValue(key));
                }
                params.add(metadata);

                parent.getClient().execute("updateGroup", params);
            } catch (final XmlRpcException e) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);   
            } catch(final PermissionDeniedException pde) {
                throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, pde.getMessage(), pde);   
            }
        }

        @Override
        public String[] getGroupMembers(String groupName) throws XMLDBException {
            try {
                final List<Object> params = new ArrayList<Object>(1);
                params.add(groupName);
                
                final Object[] groupMembersResults = (Object[])parent.getClient().execute("getGroupMembers", params);
                
                final String[] groupMembers = new String[groupMembersResults.length]; 
                for(int i = 0; i < groupMembersResults.length; i++) {
                    groupMembers[i] = groupMembersResults[i].toString();
                }
                return groupMembers;
            } catch (final XmlRpcException e) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);   
            }
        }

    @Override
    public void addAccountToGroup(final String accountName, final String groupName) throws XMLDBException {
        try {
            final List<Object> params = new ArrayList<Object>(2);
            params.add(accountName);
            params.add(groupName);
                
            parent.getClient().execute("addAccountToGroup", params);
        } catch(final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }
    
    @Override
    public void addGroupManager(final String manager, final String groupName) throws XMLDBException {
        try {
            final List<Object> params = new ArrayList<Object>(2);
            params.add(manager);
            params.add(groupName);
                
            parent.getClient().execute("addGroupManager", params);
        } catch(final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }
    
    @Override
    public void removeGroupManager(final String groupName, final String manager) throws XMLDBException {
        try {
            final List<Object> params = new ArrayList<Object>(2);
            params.add(groupName);
            params.add(manager);
            
            parent.getClient().execute("removeGroupManager", params);
        } catch(final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }
        
        
        
        
	
	/**
	 * Update the specified accounts groups 
	 *
	 *@param  user                Description of the Parameter
	 *@exception  XMLDBException  Description of the Exception
	 */
	public void addUserGroup(Account user) throws XMLDBException {
            try {
                final List<Object> params = new ArrayList<Object>(3);
                params.add(user.getName());
                final String[] gl = user.getGroups();
                params.add(gl);
                parent.getClient().execute("updateAccount", params);
            } catch (final XmlRpcException e) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
            }
        }
	
    /**
     *  Remove an account from a group
     */
    @Override
    public void removeGroupMember(final String group, final String account) throws XMLDBException {
        try {
            final List<Object> params = new ArrayList<Object>(3);
            params.add(group);
            params.add(account);
            parent.getClient().execute("removeGroupMember", params);
        } catch (final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

	/* (non-Javadoc)
	 * @see org.exist.xmldb.UserManagementService#getGroups()
	 */
	public String[] getGroups() throws XMLDBException {
		try {
			final Object[] v = (Object[]) parent.getClient().execute("getGroups", new ArrayList<Object>());
			final String[] groups = new String[v.length];
            System.arraycopy(v, 0, groups, 0, v.length);
			return groups;
		} catch (final XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }

	@Override
	public void addUser(User user) throws XMLDBException {
		final Account account = new UserAider(user.getName());
		addAccount(account);
	}

	@Override
	public void updateUser(User user) throws XMLDBException {
		final Account account = new UserAider(user.getName());
		account.setPassword(user.getPassword());
		//TODO: groups
		updateAccount(account);
	}

	@Override
	public User getUser(String name) throws XMLDBException {
		return getAccount(name);
	}

	@Override
	public User[] getUsers() throws XMLDBException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void removeUser(User user) throws XMLDBException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void lockResource(Resource res, User u) throws XMLDBException {
		final Account account = new UserAider(u.getName());
		lockResource(res, account);
	}
}
// -- end class UserManagementServiceImpl

