package org.exist.xmldb;

import org.apache.xmlrpc.XmlRpcException;
import org.exist.security.Permission;
import org.exist.security.UnixStylePermission;
import org.exist.security.User;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


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
	public void addUser(User user) throws XMLDBException {
		try {
            List params = new ArrayList(12);
			params.add(user.getName());
			params.add(user.getPassword() == null ? "" : user.getPassword());
			params.add(user.getDigestPassword() == null ? "" : user.getDigestPassword());
			String[] gl = user.getGroups();
			params.add(gl);
			if (user.getHome() != null)
				params.add(user.getHome().toString());
			parent.getClient().execute("setUser", params);
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }

	/**
	 * Change permissions for a resource.
	 */
	public void setPermissions(Resource res, Permission perms) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
		String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
		try {
            List params = new ArrayList(1);
			params.add(path);
			params.add(perms.getOwner());
			params.add(perms.getOwnerGroup());
			params.add(new Integer(perms.getPermissions()));
			parent.getClient().execute("setPermissions", params);
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }

	/**
	 * Change permissions for a resource.
	 */
	public void setPermissions(Collection child, Permission perms) throws XMLDBException {
		String path = ((RemoteCollection) child).getPath();
		try {
            List params = new ArrayList(4);
			params.add(path);
			params.add(perms.getOwner());
			params.add(perms.getOwnerGroup());
			params.add(new Integer(perms.getPermissions()));
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
            List params = new ArrayList(2);
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
            List params = new ArrayList(2);
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
	public void chmod(String mode) throws XMLDBException {
		try {
            List params = new ArrayList(2);
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
	public void chmod(int mode) throws XMLDBException {
		try {
            List params = new ArrayList(2);
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
	public void lockResource(Resource res, User u) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
		String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
		try {
            List params = new ArrayList(2);
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
            List params = new ArrayList(1);
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
            List params = new ArrayList(1);
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
	public void chown(User u, String group) throws XMLDBException {
		try {
            List params = new ArrayList(4);
			params.add(parent.getPath());
			params.add(u.getName());
			params.add(group);
			params.add("");
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
	public void chown(Resource res, User u, String group) throws XMLDBException {
        //TODO : use dedicated function in XmldbURI
		String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
		try {
            List params = new ArrayList(4);
			params.add(path);
			params.add(u.getName());
			params.add(group);
			params.add("");
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
	public Permission getPermissions(Collection coll) throws XMLDBException {
		if (coll == null)
			throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "collection is null");
		Permission perm = ((RemoteCollection)coll).getPermissions();
		if(perm != null)
			return perm;
		try {
            List params = new ArrayList(1);
			params.add(((RemoteCollection) coll).getPath());
			HashMap result = (HashMap) parent.getClient().execute("getPermissions", params);
			perm =
				new UnixStylePermission((String) result.get("owner"), (String) result.get("group"),
						((Integer) result.get("permissions")).intValue());
			return perm;
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }

	/**
	 *  Get current permissions for a resource
	 *
	 *@param  res                 Description of the Parameter
	 *@return                     The permissions value
	 *@exception  XMLDBException  Description of the Exception
	 */
	public Permission getPermissions(Resource res) throws XMLDBException {
		if (res == null)
			throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "resource is null");
		if(((EXistResource)res).getPermissions() != null)
			return ((EXistResource)res).getPermissions();
        //TODO : use dedicated function in XmldbURI
		String path = ((RemoteCollection) res.getParentCollection()).getPath() + "/" + res.getId();
		System.out.println("Retrieving permissions for " + path);
		try {
            List params = new ArrayList(1);
			params.add(path);
			HashMap result = (HashMap) parent.getClient().execute("getPermissions", params);
			Permission perm =
				new UnixStylePermission((String) result.get("owner"), (String) result.get("group"),
						((Integer) result.get("permissions")).intValue());
			return perm;
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }

	public Permission[] listResourcePermissions() throws XMLDBException {
		try {
            List params = new ArrayList(1);
			params.add(parent.getPath());
			HashMap result =
                    (HashMap) parent.getClient().execute("listDocumentPermissions", params);
			Permission perm[] = new Permission[result.size()];
			String[] resources = parent.listResources();
			Object[] t;
			for (int i = 0; i < resources.length; i++) {
				t = (Object[]) result.get(resources[i]);
				perm[i] = new UnixStylePermission();
				perm[i].setOwner((String) t[0]);
				perm[i].setGroup((String) t[1]);
				perm[i].setPermissions(((Integer) t[2]).intValue());
			}
			return perm;
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }

	public Permission[] listCollectionPermissions() throws XMLDBException {
		try {
            List params = new ArrayList(1);
			params.add(parent.getPath());
			HashMap result =
                    (HashMap) parent.getClient().execute("listCollectionPermissions", params);
			Permission perm[] = new Permission[result.size()];
			String collections[] = parent.listChildCollections();
			Object[] t;
			for (int i = 0; i < collections.length; i++) {
				t = (Object[]) result.get(collections[i]);
				perm[i] = new UnixStylePermission();
				perm[i].setOwner((String) t[0]);
				perm[i].setGroup((String) t[1]);
				perm[i].setPermissions(((Integer) t[2]).intValue());
			}
			return perm;
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
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
	public User getUser(String name) throws XMLDBException {
		try {
            List params = new ArrayList(1);
			params.add(name);
			HashMap tab = (HashMap) parent.getClient().execute("getUser", params);
			User u = new User((String) tab.get("name"), null);
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
	public User[] getUsers() throws XMLDBException {
		try {
			Object[] users = (Object[]) parent.getClient().execute("getUsers", new ArrayList());
			User[] u = new User[users.length];
			for (int i = 0; i < u.length; i++) {
				final HashMap tab = (HashMap) users[i];
				u[i] = new User((String) tab.get("name"), null);
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
	public void removeUser(User u) throws XMLDBException {
		try {
            List params = new ArrayList(1);
			params.add(u.getName());
			parent.getClient().execute("removeUser", params);
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
	public void updateUser(User user) throws XMLDBException {
		try {
            List params = new ArrayList(12);
			params.add(user.getName());
			params.add(user.getPassword() == null ? "" : user.getPassword());
			params.add(user.getDigestPassword() == null ? "" : user.getDigestPassword());
			String[] gl = user.getGroups();
			params.add(gl);
			if (user.getHome() != null)
				params.add(user.getHome().toString());
			parent.getClient().execute("setUser", params);
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }
	
	/**
	 *  Update the specified user without update user's password
	 * Method added by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
	 *
	 *@param  user                Description of the Parameter
	 *@exception  XMLDBException  Description of the Exception
	 */
	public void addUserGroup(User user) throws XMLDBException {
		try {
            List params = new ArrayList(3);
			params.add(user.getName());
			String[] gl = user.getGroups();
			params.add(gl);
			if (user.getHome() != null)
				params.add(user.getHome());
			parent.getClient().execute("setUser", params);
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
	public void removeGroup(User user, String rmgroup) throws XMLDBException {
		try {
            List params = new ArrayList(1);
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
			Object[] v = (Object[]) parent.getClient().execute("getGroups", new ArrayList());
			String[] groups = new String[v.length];
            System.arraycopy(v, 0, groups, 0, v.length);
			return groups;
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
    }

}
// -- end class UserManagementServiceImpl

