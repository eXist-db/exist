package org.exist.xmldb;

import java.util.Iterator;

import org.exist.EXistException;
import org.exist.dom.DocumentImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.SyntaxException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

public class LocalUserManagementService implements UserManagementService {
	private LocalCollection collection;

	private BrokerPool pool;
	private User user;

	public LocalUserManagementService(
		User user,
		BrokerPool pool,
		LocalCollection collection) {
		this.pool = pool;
		this.collection = collection;
		this.user = user;
	}

	public void addUser(User u) throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		if (!manager.hasAdminPrivileges(user))
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				" you are not allowed to change this user");
		if (manager.hasUser(u.getName()))
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				"user " + user.getName() + " exists");
		manager.setUser(u);
	}

	public void setPermissions(Resource resource, Permission perm)
		throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			DocumentImpl document;
			if (resource.getResourceType().equals("XMLResource"))
				document = ((LocalXMLResource) resource).getDocument();
			else
				document = ((LocalBinaryResource) resource).getBlob();
			if (!(document.getPermissions().getOwner().equals(user.getName())
				|| manager.hasAdminPrivileges(user)))
				throw new XMLDBException(
					ErrorCodes.PERMISSION_DENIED,
					"you are not the owner of this resource; owner = "
						+ document.getPermissions().getOwner());

			document.setPermissions(perm);
			broker.saveCollection(collection.getCollection());
		} catch (EXistException e) {
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} catch (PermissionDeniedException e) {
			throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e);
		} finally {
			pool.release(broker);
		}
	}

	public void setPermissions(Collection child, Permission perm)
		throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		if (!collection.checkOwner(user) && !manager.hasAdminPrivileges(user))
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				"you are not the owner of this collection");
		org.exist.collections.Collection coll =
			((LocalCollection) child).getCollection();
		coll.setPermissions(perm);
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			broker.saveCollection(coll);
			broker.flush();
		} catch (EXistException e) {
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} catch (PermissionDeniedException e) {
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				e.getMessage(),
				e);
		} finally {
			pool.release(broker);
		}
	}

	public void chmod(String modeStr) throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		if (!collection.checkOwner(user) && !manager.hasAdminPrivileges(user))
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				"you are not the owner of this collection");
		org.exist.collections.Collection coll = collection.getCollection();
		try {
			coll.setPermissions(modeStr);
		} catch (SyntaxException e) {
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		}
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			broker.saveCollection(coll);
			broker.flush();
			//broker.sync();
		} catch (EXistException e) {
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} catch (PermissionDeniedException e) {
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				e.getMessage(),
				e);
		} finally {
			pool.release(broker);
		}
	}

	public void chmod(Resource resource, int mode) throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			DocumentImpl document;
			if (resource.getResourceType().equals("XMLResource"))
				document = ((LocalXMLResource) resource).getDocument();
			else
				document = ((LocalBinaryResource) resource).getBlob();
			if (!document.getPermissions().getOwner().equals(user.getName())
				&& !manager.hasAdminPrivileges(user))
				throw new XMLDBException(
					ErrorCodes.PERMISSION_DENIED,
					"you are not the owner of this resource");

			document.setPermissions(mode);
			broker.saveCollection(collection.getCollection());
		} catch (EXistException e) {
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} catch (PermissionDeniedException e) {
			throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e);
		} finally {
			pool.release(broker);
		}
	}

	public void chmod(int mode) throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		if (!collection.checkOwner(user) && !manager.hasAdminPrivileges(user))
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				"you are not the owner of this collection");
		org.exist.collections.Collection coll = collection.getCollection();
		DBBroker broker = null;
		try {
			coll.setPermissions(mode);
			broker = pool.get(user);
			broker.saveCollection(coll);
			broker.flush();
		} catch (EXistException e) {
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} catch (PermissionDeniedException e) {
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				e.getMessage(),
				e);
		} finally {
			pool.release(broker);
		}
	}

	public void chmod(Resource resource, String modeStr)
		throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			DocumentImpl document;
			if (resource.getResourceType().equals("XMLResource"))
				document = ((LocalXMLResource) resource).getDocument();
			else
				document = ((LocalBinaryResource) resource).getBlob();
			if (!document.getPermissions().getOwner().equals(user.getName())
				&& !manager.hasAdminPrivileges(user))
				throw new XMLDBException(
					ErrorCodes.PERMISSION_DENIED,
					"you are not the owner of this resource");

			document.setPermissions(modeStr);
			broker.saveCollection(collection.getCollection());
		} catch (EXistException e) {
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} catch (SyntaxException e) {
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} catch (PermissionDeniedException e) {
			throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e);
		} finally {
			pool.release(broker);
		}
	}

	public void chown(User u, String group) throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		if (!manager.hasAdminPrivileges(user))
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				"need admin privileges for chown");
		org.exist.collections.Collection coll = collection.getCollection();
		DBBroker broker = null;
		try {
			coll.getPermissions().setOwner(u);
			coll.getPermissions().setGroup(group);

			broker = pool.get(user);
			broker.saveCollection(coll);
			broker.flush();
			//broker.sync();
		} catch (EXistException e) {
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} catch (PermissionDeniedException e) {
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				e.getMessage(),
				e);
		} finally {
			pool.release(broker);
		}
	}

	public void chown(Resource res, User u, String group)
		throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		if (!manager.hasAdminPrivileges(user))
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				"need admin privileges for chown");
			Permission perm;
			if(res.getResourceType().equals("XMLResource"))
				perm = ((LocalXMLResource) res).getDocument().getPermissions();
			else
				perm = ((LocalBinaryResource) res).getBlob().getPermissions();
			perm.setOwner(u);
			perm.setGroup(group);
			DBBroker broker = null;
			try {
				broker = pool.get(user);
				broker.saveCollection(collection.getCollection());
				broker.flush();
			} catch (EXistException e) {
				throw new XMLDBException(
					ErrorCodes.VENDOR_ERROR,
					e.getMessage(),
					e);
			} catch (PermissionDeniedException e) {
				throw new XMLDBException(
					ErrorCodes.PERMISSION_DENIED,
					e.getMessage(),
					e);
			} finally {
				pool.release(broker);
			}
	}

	public String getName() {
		return "UserManagementService";
	}

	public Permission getPermissions(Collection coll) throws XMLDBException {
		if (coll instanceof LocalCollection)
			return ((LocalCollection) coll).getCollection().getPermissions();
		return null;
	}

	public Permission getPermissions(Resource resource) throws XMLDBException {
		if (resource.getResourceType().equals("XMLResource"))
			return ((LocalXMLResource) resource).getDocument().getPermissions();
		else
			return ((LocalBinaryResource) resource).getBlob().getPermissions();
	}

	public Permission[] listResourcePermissions() throws XMLDBException {
		if (!collection
			.collection
			.getPermissions()
			.validate(user, Permission.READ))
			return new Permission[0];
		Permission perms[] =
			new Permission[collection.collection.getDocumentCount()];
		int j = 0;
		DocumentImpl doc;
		for (Iterator i = collection.collection.iterator(); i.hasNext(); j++) {
			doc = (DocumentImpl) i.next();
			perms[j] = doc.getPermissions();
		}
		return perms;
	}

	public Permission[] listCollectionPermissions() throws XMLDBException {
		if (!collection
			.collection
			.getPermissions()
			.validate(user, Permission.READ))
			return new Permission[0];
		Permission perms[] =
			new Permission[collection.collection.getChildCollectionCount()];
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			String child;
			org.exist.collections.Collection childColl;
			int j = 0;
			for (Iterator i = collection.collection.collectionIterator();
				i.hasNext();
				j++) {
				child = (String) i.next();
				childColl =
					broker.getCollection(collection.getPath() + '/' + child);
				perms[j] = childColl.getPermissions();
			}
			return perms;
		} catch (EXistException e) {
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} finally {
			pool.release(broker);
		}
	}

	public String getProperty(String property) throws XMLDBException {
		return null;
	}

	public User getUser(String name) throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		return manager.getUser(name);
	}

	public User[] getUsers() throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		return manager.getUsers();
	}

	public String[] getGroups() throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		return manager.getGroups();
	}

	public String getVersion() {
		return "1.0";
	}

	public void removeUser(User u) throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		if (!manager.hasAdminPrivileges(user))
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				"you are not allowed to remove users");
		try {
			manager.deleteUser(u);
		} catch (PermissionDeniedException e) {
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				"unable to remove user " + u.getName(),
				e);
		}
	}

	public void setCollection(Collection collection) throws XMLDBException {
		this.collection = (LocalCollection) collection;
	}

	public void setProperty(String property, String value)
		throws XMLDBException {
	}

	public void updateUser(User u) throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		if (!(u.getName().equals(user.getName())
			|| manager.hasAdminPrivileges(user)))
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				" you are not allowed to change this user");
		User old = manager.getUser(u.getName());
		if (old == null)
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				"user " + u.getName() + " does not exist");
		u.setUID(old.getUID());
		manager.setUser(u);
	}
}
