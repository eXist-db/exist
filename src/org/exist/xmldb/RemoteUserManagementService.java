package org.exist.xmldb;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.exist.security.ACLPermission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.internal.aider.ACEAider;
import org.exist.security.internal.aider.PermissionAiderFactory;


/*************************************************
 * Modified by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
**************************************/

public class RemoteUserManagementService implements UserManagementService {

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
	public void addAccount(Account user) throws XMLDBException {
		try {
            List<Object> params = new ArrayList<Object>(12);
			params.add(user.getName());
			params.add(user.getPassword() == null ? "" : user.getPassword());
			params.add(user.getDigestPassword() == null ? "" : user.getDigestPassword());
			String[] gl = user.getGroups();
			params.add(gl);
			if (user.getHome() != null)
				params.add(user.getHome().toString());
			parent.getClient().execute("addAccount", params);
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }

	public void addGroup(Group role) throws XMLDBException {
		try {
            List<Object> params = new ArrayList<Object>(12);
			params.add(role.getName());
			parent.getClient().execute("addGroup", params);
		} catch (XmlRpcException e) {
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
        String path = ((RemoteCollection)res.getParentCollection()).getPath() + "/" + res.getId();
        try {
            List<Object> params = new ArrayList<Object>(5);
            params.add(path);
            params.add(perm.getOwner().getName());
            params.add(perm.getGroup().getName());
            params.add(new Integer(perm.getMode()));
            if(perm instanceof ACLPermission) {
                params.add(getACEs(perm));
            }

            parent.getClient().execute("setPermissions", params);
            
        } catch (XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    /**
     * Change permissions for a resource.
     */
    @Override
    public void setPermissions(Collection child, Permission perm) throws XMLDBException {
        String path = ((RemoteCollection)child).getPath();
        try {
            List<Object> params = new ArrayList<Object>(5);
            params.add(path);
            params.add(perm.getOwner().getName());
            params.add(perm.getGroup().getName());
            params.add(new Integer(perm.getMode()));
            if(perm instanceof ACLPermission) {
                params.add(getACEs(perm));
            }

            parent.getClient().execute("setPermissions", params);

        } catch (XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public void setPermissions(Collection child, String owner, String group, int mode, List<ACEAider> aces) throws XMLDBException {
        String path = ((RemoteCollection)child).getPath();
        try {
            List<Object> params = new ArrayList<Object>(5);
            params.add(path);
            params.add(owner);
            params.add(group);
            params.add(new Integer(mode));
            if(aces != null) {
                params.add(aces);
            }

            parent.getClient().execute("setPermissions", params);

        } catch (XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }
    
    @Override
    public void setPermissions(Resource res, String owner, String group, int mode, List<ACEAider> aces) throws XMLDBException {
        String path = ((RemoteCollection)res.getParentCollection()).getPath() + "/" + res.getId();
        try {
            List<Object> params = new ArrayList<Object>(5);
            params.add(path);
            params.add(owner);
            params.add(group);
            params.add(new Integer(mode));
            if(aces != null) {
                params.add(aces);
            }

            parent.getClient().execute("setPermissions", params);

        } catch (XmlRpcException e) {
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
		String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
		try {
            List<Object> params = new ArrayList<Object>(2);
			params.add(path);
			params.add(mode);
			parent.getClient().execute("setPermissions", params);
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }

	/**
	 * @see org.exist.xmldb.UserManagementService#chmod(org.xmldb.api.base.Resource, int)
	 */
	public void chmod(Resource res, int mode) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
		String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
		try {
            List<Object> params = new ArrayList<Object>(2);
			params.add(path);
			params.add(new Integer(mode));
			parent.getClient().execute("setPermissions", params);
		} catch (XmlRpcException e) {
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
            List<Object> params = new ArrayList<Object>(2);
            params.add(parent.getPath());
            params.add(mode);
            
            parent.getClient().execute("setPermissions", params);
        } catch (XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

	/**
	 * @see org.exist.xmldb.UserManagementService#chmod(int)
	 */
    @Override
    public void chmod(int mode) throws XMLDBException {
        try {
            List<Object> params = new ArrayList<Object>(2);
            params.add(parent.getPath());
            params.add(new Integer(mode));

            parent.getClient().execute("setPermissions", params);
        } catch (XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

	/* (non-Javadoc)
	 * @see org.exist.xmldb.UserManagementService#lockResource(org.xmldb.api.base.Resource, org.exist.security.User)
	 */
	public void lockResource(Resource res, Account u) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
		String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
		try {
            List<Object> params = new ArrayList<Object>(2);
			params.add(path);
			params.add(u.getName());
			parent.getClient().execute("lockResource", params);
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }
	
	/* (non-Javadoc)
	 * @see org.exist.xmldb.UserManagementService#hasUserLock(org.xmldb.api.base.Resource)
	 */
	public String hasUserLock(Resource res) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
		String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
		try {
            List<Object> params = new ArrayList<Object>(1);
			params.add(path);
			String userName = (String)parent.getClient().execute("hasUserLock", params);
			return userName != null && userName.length() > 0 ? userName : null;
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }
	
	/* (non-Javadoc)
	 * @see org.exist.xmldb.UserManagementService#unlockResource(org.xmldb.api.base.Resource)
	 */
	public void unlockResource(Resource res) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
		String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
		try {
            List<Object> params = new ArrayList<Object>(1);
			params.add(path);
			parent.getClient().execute("unlockResource", params);
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }
	
	/**
	 *  Change the owner of the current collection
	 *
	 *@param  u                   Description of the Parameter
	 *@param  group               Description of the Parameter
	 *@exception  XMLDBException  Description of the Exception
	 */
    @Override
    public void chown(Account u, String group) throws XMLDBException {
        try {
            List<Object> params = new ArrayList<Object>(4);
            params.add(parent.getPath());
            params.add(u.getName());
            params.add(group);

            parent.getClient().execute("setPermissions", params);
        } catch (XmlRpcException e) {
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
        String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
        try {
            List<Object> params = new ArrayList<Object>(4);
            params.add(path);
            params.add(u.getName());
            params.add(group);

            parent.getClient().execute("setPermissions", params);
        } catch (XmlRpcException e) {
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

        Permission perm = ((RemoteCollection)coll).getPermissions();

        if(perm == null) {
            try {
                List<Object> params = new ArrayList<Object>(1);
                params.add(((RemoteCollection) coll).getPath());

                HashMap<?,?> result = (HashMap<?,?>) parent.getClient().execute("getPermissions", params);

                final String owner = (String)result.get("owner");
                final String group = (String)result.get("group");
                final int mode = ((Integer)result.get("permissions")).intValue();
                final Object[] acl = (Object[])result.get("acl");
                List aces = null;
                if (acl != null)
                	aces = Arrays.asList(acl);

                perm = getPermission(owner, group, mode, (List<ACEAider>)aces);
                
            } catch(XmlRpcException e) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
            } catch(PermissionDeniedException pde) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, pde.getMessage(), pde);
            }
        }

        return perm;
    }

    private Permission getPermission(String owner, String group, int mode, List listOfAces) throws PermissionDeniedException {
        Permission perm = PermissionAiderFactory.getPermission(owner, group, mode);
        if(perm instanceof ACLPermission && listOfAces != null && !listOfAces.isEmpty()) {
            ACLPermission aclPermission = (ACLPermission)perm;
            for(Object listOfAcesItem : listOfAces) {
                if(listOfAcesItem instanceof ACEAider) {
                    ACEAider ace = (ACEAider)listOfAcesItem;
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

        Permission perm = ((EXistResource)res).getPermissions();

        if(perm == null) {
            //TODO : use dedicated function in XmldbURI
            String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
            try {
                List<Object> params = new ArrayList<Object>(1);
                params.add(path);

                HashMap<?,?> result = (HashMap<?,?>) parent.getClient().execute("getPermissions", params);

                final String owner = (String)result.get("owner");
                final String group = (String)result.get("group");
                final int mode = ((Integer)result.get("permissions")).intValue();
                final Object[] acl = (Object[])result.get("acl");
                List aces = null;
                if (acl != null)
                	aces = Arrays.asList(acl);

                perm = getPermission(owner, group, mode, (List<ACEAider>)aces);
                
            } catch (XmlRpcException e) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
            } catch(PermissionDeniedException pde) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, pde.getMessage(), pde);
            }
        }

        return perm;
    }

    @Override
    public Permission[] listResourcePermissions() throws XMLDBException {
        try {
            List<Object> params = new ArrayList<Object>(1);
            params.add(parent.getPath());
            HashMap<?,?> result = (HashMap<?,?>) parent.getClient().execute("listDocumentPermissions", params);
            Permission perm[] = new Permission[result.size()];
            String[] resources = parent.listResources();
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
        } catch (XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch(PermissionDeniedException pde) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, pde.getMessage(), pde);
        }
    }

    @Override
    public Permission[] listCollectionPermissions() throws XMLDBException {
        try {
            List<Object> params = new ArrayList<Object>(1);
            params.add(parent.getPath());
            HashMap<?,?> result = (HashMap<?,?>) parent.getClient().execute("listCollectionPermissions", params);
            Permission perm[] = new Permission[result.size()];
            String collections[] = parent.listChildCollections();
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
        } catch(XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch(PermissionDeniedException pde) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, pde.getMessage(), pde);
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
	public Account getAccount(String name) throws XMLDBException {
		try {
            List<Object> params = new ArrayList<Object>(1);
			params.add(name);
			HashMap<?,?> tab = (HashMap<?,?>) parent.getClient().execute("getAccount", params);

			GroupAider defaultGroup = new GroupAider(
					(Integer) tab.get("default-group-id"),
					(String) tab.get("default-group-realmId"),
					(String) tab.get("default-group-name")
				);

			UserAider u = new UserAider(
					(String) tab.get("realmId"), 
					(String) tab.get("name"),
					defaultGroup
				);
			
			Object[] groups = (Object[]) tab.get("groups");
            for (int i = 0; i < groups.length; i++) {
				u.addGroup((String) groups[i]);
            }
			String home = (String) tab.get("home");
			u.setHome(home==null?null:XmldbURI.create(home));
			return u;
		} catch (XmlRpcException e) {
			return null;
		}
    }

	/**
	 *  Get a list of all users currently defined
	 *
	 *@return                     The users value
	 *@exception  XMLDBException  Description of the Exception
	 */
	public Account[] getAccounts() throws XMLDBException {
		try {
			Object[] users = (Object[]) parent.getClient().execute("getAccounts", new ArrayList<Object>());
			UserAider[] u = new UserAider[users.length];
			for (int i = 0; i < u.length; i++) {
				final HashMap<?,?> tab = (HashMap<?,?>) users[i];
				
				int uid = -1;
				try {
					uid = (Integer)tab.get("uid");
				} catch (java.lang.NumberFormatException e) {
				}
					
				u[i] = new UserAider(uid, (String) tab.get("realmId"), (String) tab.get("name"));
				Object[] groups = (Object[]) tab.get("groups");
                for (int j = 0; j < groups.length; j++)
					u[i].addGroup((String) groups[j]);
				String home = (String) tab.get("home");
				u[i].setHome(home==null?null:XmldbURI.create(home));
			}
			return u;
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }

        @Override
	public Group getGroup(String name) throws XMLDBException {
            try {
                List<Object> params = new ArrayList<Object>(1);
                params.add(name);
                HashMap<String,Object> tab = (HashMap<String,Object>) parent.getClient().execute("getGroup", params);
                if(tab != null) {
                    Group role = new GroupAider((Integer)tab.get("id"), (String) tab.get("realmId"), (String) tab.get("name"));
                    return role;
                }
                return null;
            } catch (XmlRpcException e) {
                    throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e);
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
            List<Object> params = new ArrayList<Object>(1);
			params.add(u.getName());
			parent.getClient().execute("removeAccount", params);
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }

	public void removeGroup(Group role) throws XMLDBException {
		try {
            List<Object> params = new ArrayList<Object>(1);
			params.add(role.getName());
			parent.getClient().execute("removeGroup", params);
		} catch (XmlRpcException e) {
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
	public void updateAccount(Account user) throws XMLDBException {
		try {
            List<Object> params = new ArrayList<Object>(12);
			params.add(user.getName());
			params.add(user.getPassword() == null ? "" : user.getPassword());
			params.add(user.getDigestPassword() == null ? "" : user.getDigestPassword());
			String[] gl = user.getGroups();
			params.add(gl);
			if (user.getHome() != null)
				params.add(user.getHome().toString());
			parent.getClient().execute("updateAccount", params);
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }
	
	/**
	 * Update the specified account without update user's password
	 * Method added by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
	 *
	 *@param  user                Description of the Parameter
	 *@exception  XMLDBException  Description of the Exception
	 */
	public void addUserGroup(Account user) throws XMLDBException {
		try {
            List<Object> params = new ArrayList<Object>(3);
			params.add(user.getName());
			String[] gl = user.getGroups();
			params.add(gl);
			if (user.getHome() != null)
				params.add(user.getHome());
			parent.getClient().execute("updateAccount", params);
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }
	
	/**
	 *  Update the specified user removing a group from user's group
	 *  method added by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
	 *
	 *@param  user                Description of the Parameter
	 *@param  rmgroup             Description of group to remove 
	 *@exception  XMLDBException  Description of the Exception
	 */
	public void removeGroup(Account user, String rmgroup) throws XMLDBException {
		try {
            List<Object> params = new ArrayList<Object>(1);
			params.add(user.getName());
			String[] gl = user.getGroups();
			params.add(gl);
			if (user.getHome() != null)
				params.add(user.getHome());
			params.add(rmgroup);
			parent.getClient().execute("setUser", params);
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }

	/* (non-Javadoc)
	 * @see org.exist.xmldb.UserManagementService#getGroups()
	 */
	public String[] getGroups() throws XMLDBException {
		try {
			Object[] v = (Object[]) parent.getClient().execute("getGroups", new ArrayList<Object>());
			String[] groups = new String[v.length];
            System.arraycopy(v, 0, groups, 0, v.length);
			return groups;
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }

	@Override
	public void addUser(User user) throws XMLDBException {
		Account account = new UserAider(user.getName());
		addAccount(account);
	}

	@Override
	public void updateUser(User user) throws XMLDBException {
		Account account = new UserAider(user.getName());
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
		Account account = new UserAider(u.getName());
		lockResource(res, account);
	}
}
// -- end class UserManagementServiceImpl

