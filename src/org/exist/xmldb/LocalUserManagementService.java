package org.exist.xmldb;

import java.io.IOException;
import java.util.Iterator;

import org.exist.EXistException;
import org.exist.dom.DocumentImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.SyntaxException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

/*************************************************
 * Modified by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
**************************************/
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
				"user " + u.getName() + " exists");
		manager.setUser(u);
	}

	public void setPermissions(Resource resource, Permission perm)
		throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		DocumentImpl document = null;
		DBBroker broker = null;
        TransactionManager transact = pool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
		try {
			broker = pool.get(user);
			document = ((AbstractEXistResource) resource).openDocument(broker, Lock.WRITE_LOCK);
			if (!document.getPermissions().validate(user, Permission.WRITE) && !manager.hasAdminPrivileges(user))
				throw new XMLDBException(
					ErrorCodes.PERMISSION_DENIED,
					"you are not the owner of this resource; owner = "
						+ document.getPermissions().getOwner());

			document.setPermissions(perm);
            if (!manager.hasGroup(perm.getOwnerGroup()))
                manager.addGroup(perm.getOwnerGroup());
            broker.storeXMLResource(transaction, document);
            transact.commit(transaction);
		} catch (EXistException e) {
            transact.abort(transaction);
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} finally {
		    ((AbstractEXistResource)resource).closeDocument(document, Lock.WRITE_LOCK);
			pool.release(broker);
		}
	}

	public void setPermissions(Collection child, Permission perm)
		throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		org.exist.collections.Collection coll = null;
		DBBroker broker = null;
        TransactionManager transact = pool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
		try {
			broker = pool.get(user);
			coll = broker.openCollection(collection.getPathURI(), Lock.WRITE_LOCK);
			if(coll == null)
				throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + collection.getPath() + 
						" not found");
			if (!coll.getPermissions().validate(user, Permission.WRITE) && !manager.hasAdminPrivileges(user)) {
                transact.abort(transaction);
				throw new XMLDBException(
					ErrorCodes.PERMISSION_DENIED,
					"you are not the owner of this collection");
            }
            if (!manager.hasGroup(perm.getOwnerGroup()))
                manager.addGroup(perm.getOwnerGroup());
			coll.setPermissions(perm);
			broker.saveCollection(transaction, coll);
            transact.commit(transaction);
			broker.flush();
		} catch (IOException e) {
            transact.abort(transaction);
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} catch (EXistException e) {
            transact.abort(transaction);
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} catch (PermissionDeniedException e) {
            transact.abort(transaction);
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				e.getMessage(),
				e);
		} catch (LockException e) {
            transact.abort(transaction);
			throw new XMLDBException(
					ErrorCodes.VENDOR_ERROR,
					"Failed to acquire lock on collections.dbx",
					e);
		} finally {
			if(coll != null)
				coll.release(Lock.WRITE_LOCK);
			pool.release(broker);
		}
	}

	public void chmod(String modeStr) throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		org.exist.collections.Collection coll = null;
		DBBroker broker = null;
        TransactionManager transact = pool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
		try {
			broker = pool.get(user);
			coll = broker.openCollection(collection.getPathURI(), Lock.WRITE_LOCK);
			if(coll == null)
				throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + collection.getPath() + 
						" not found");
            if (!coll.getPermissions().validate(user, Permission.WRITE) && !manager.hasAdminPrivileges(user)) {
                transact.abort(transaction);
				   throw new XMLDBException(
					   ErrorCodes.PERMISSION_DENIED,
					   "you are not the owner of this collection");
            }
			coll.setPermissions(modeStr);
			broker.saveCollection(transaction, coll);
            transact.commit(transaction);
			broker.flush();
		} catch (SyntaxException e) {
            transact.abort(transaction);
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} catch (LockException e) {
            transact.abort(transaction);
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				"Failed to acquire lock on collections.dbx",
				e);
		} catch (IOException e) {
            transact.abort(transaction);
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} catch (EXistException e) {
            transact.abort(transaction);
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} catch (PermissionDeniedException e) {
            transact.abort(transaction);
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				e.getMessage(),
				e);
		} finally {
			if(coll != null)
				coll.release(Lock.WRITE_LOCK);
			pool.release(broker);
		}
	}

	public void chmod(Resource resource, int mode) throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		DocumentImpl document = null;
		DBBroker broker = null;
        TransactionManager transact = pool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
		try {
			broker = pool.get(user);
			document = ((AbstractEXistResource) resource).openDocument(broker, Lock.WRITE_LOCK);
			if (!document.getPermissions().validate(user, Permission.WRITE) && !manager.hasAdminPrivileges(user)) {
                transact.abort(transaction);
				throw new XMLDBException(
					ErrorCodes.PERMISSION_DENIED,
					"you are not the owner of this resource");
            }
			document.setPermissions(mode);
			broker.storeXMLResource(transaction, document);
            transact.commit(transaction);
		} catch (EXistException e) {
            transact.abort(transaction);
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} finally {
		    ((AbstractEXistResource) resource).closeDocument(document, Lock.WRITE_LOCK);
			pool.release(broker);
		}
	}

	public void chmod(int mode) throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		org.exist.collections.Collection coll = null;
		DBBroker broker = null;
        TransactionManager transact = pool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
		try {
			broker = pool.get(user);
			coll = broker.openCollection(collection.getPathURI(), Lock.WRITE_LOCK);
			if(coll == null)
				throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + collection.getPath() + 
						" not found");
			if (!coll.getPermissions().validate(user, Permission.WRITE) && !manager.hasAdminPrivileges(user)) {
                transact.abort(transaction);
				throw new XMLDBException(
					ErrorCodes.PERMISSION_DENIED,
					"you are not the owner of this collection");
            }
			coll.setPermissions(mode);
			broker.saveCollection(transaction, coll);
            transact.commit(transaction);
			broker.flush();
		} catch (IOException e) {
            transact.abort(transaction);
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} catch (EXistException e) {
            transact.abort(transaction);
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} catch (PermissionDeniedException e) {
            transact.abort(transaction);
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				e.getMessage(),
				e);
		} catch (LockException e) {
            transact.abort(transaction);
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				"Failed to acquire lock on collections.dbx",
				e);
		} finally {
			if(coll != null)
				coll.release(Lock.WRITE_LOCK);
			pool.release(broker);
		}
	}

	public void chmod(Resource resource, String modeStr)
		throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		DocumentImpl document = null;
		DBBroker broker = null;
        TransactionManager transact = pool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
		try {
			broker = pool.get(user);
			document = ((AbstractEXistResource) resource).openDocument(broker, Lock.WRITE_LOCK);
			if (!document.getPermissions().validate(user, Permission.WRITE) && !manager.hasAdminPrivileges(user)) {
                transact.abort(transaction);
				throw new XMLDBException(
					ErrorCodes.PERMISSION_DENIED,
					"you are not the owner of this resource");
            }
			document.setPermissions(modeStr);
			broker.storeXMLResource(transaction, document);
		} catch (EXistException e) {
            transact.abort(transaction);
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} catch (SyntaxException e) {
            transact.abort(transaction);
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} finally {
			((AbstractEXistResource) resource).closeDocument(document, Lock.WRITE_LOCK);
			pool.release(broker);
		}
	}

	public void chown(User u, String group) throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		if (!manager.hasUser(u.getName()))
			throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, "Unknown user");
		if (!manager.hasAdminPrivileges(user))
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				"need admin privileges for chown");
		org.exist.collections.Collection coll = null;
		DBBroker broker = null;
        TransactionManager transact = pool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
		try {
			broker = pool.get(user);
			coll = broker.openCollection(collection.getPathURI(), Lock.WRITE_LOCK);
			coll.getPermissions().setOwner(u);
            if (!manager.hasGroup(group))
                manager.addGroup(group);
			coll.getPermissions().setGroup(group);
			broker.saveCollection(transaction, coll);
            transact.commit(transaction);
			broker.flush();
			//broker.sync();
		} catch (IOException e) {
            transact.abort(transaction);
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} catch (EXistException e) {
            transact.abort(transaction);
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} catch (PermissionDeniedException e) {
            transact.abort(transaction);
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				e.getMessage(),
				e);
		} finally {
			if(coll != null)
				coll.release(Lock.WRITE_LOCK);
			pool.release(broker);
		}
	}

	public void chown(Resource res, User u, String group)
		throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		if (!manager.hasUser(u.getName()))
			throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, "Unknown user");
		if (!manager.hasAdminPrivileges(user))
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				"need admin privileges for chown");
		DocumentImpl document = null;
		DBBroker broker = null;
        TransactionManager transact = pool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
		try {
			broker = pool.get(user);
			document = ((AbstractEXistResource) res).openDocument(broker, Lock.WRITE_LOCK);
			Permission perm = document.getPermissions();
			perm.setOwner(u);
            if (!manager.hasGroup(group))
                manager.addGroup(group);
			perm.setGroup(group);
            broker.storeXMLResource(transaction, document);
            transact.commit(transaction);
		} catch (EXistException e) {
            transact.abort(transaction);
			throw new XMLDBException(
					ErrorCodes.VENDOR_ERROR,
					e.getMessage(),
					e);
		} finally {
			((AbstractEXistResource) res).closeDocument(document, Lock.WRITE_LOCK);
			pool.release(broker);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.UserManagementService#hasUserLock(org.xmldb.api.base.Resource)
	 */
	public String hasUserLock(Resource res) throws XMLDBException {
		DocumentImpl doc = null;
		DBBroker broker = null;
		try {
		    broker = pool.get(user);
		    doc = ((AbstractEXistResource) res).openDocument(broker, Lock.READ_LOCK);
			User lockOwner = doc.getUserLock();
			return lockOwner == null ? null : lockOwner.getName();
		} catch (EXistException e) {
		    throw new XMLDBException(
					ErrorCodes.VENDOR_ERROR,
					e.getMessage(),
					e);
        } finally {
        	((AbstractEXistResource) res).closeDocument(doc, Lock.READ_LOCK);
		    pool.release(broker);
		}
	}
	
	public void lockResource(Resource res, User u) throws XMLDBException {
		DocumentImpl doc = null;
		DBBroker broker = null;
        TransactionManager transact = pool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
		try {
			broker = pool.get(user);
			doc = ((AbstractEXistResource) res).openDocument(broker, Lock.WRITE_LOCK);
			if (!doc.getPermissions().validate(user, Permission.UPDATE))
				throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, 
						"User is not allowed to lock resource " + res.getId());
			org.exist.security.SecurityManager manager = pool.getSecurityManager();
			if(!(user.equals(u) || manager.hasAdminPrivileges(user))) {
				throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
						"User " + user.getName() + " is not allowed to lock resource for " +
						"user " + u.getName());
			}
			User lockOwner = doc.getUserLock();
			if(lockOwner != null) {
				if(lockOwner.equals(u))
					return;
				else if(!manager.hasAdminPrivileges(user))
					throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
							"Resource is already locked by user " + lockOwner.getName());
			}
			doc.setUserLock(u);
            broker.storeXMLResource(transaction, doc);
            transact.commit(transaction);
		} catch (EXistException e) {
            transact.abort(transaction);
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
					e.getMessage(), e);
		} finally {
			((AbstractEXistResource) res).closeDocument(doc, Lock.WRITE_LOCK);
			pool.release(broker);
		}
	}
	
	public void unlockResource(Resource res) throws XMLDBException {
		DocumentImpl doc = null;
		DBBroker broker = null;
        TransactionManager transact = pool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
		try {
			broker = pool.get(user);
			doc = ((AbstractEXistResource) res).openDocument(broker, Lock.WRITE_LOCK);
			if (!doc.getPermissions().validate(user, Permission.UPDATE))
				throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, 
						"User is not allowed to lock resource " + res.getId());
			org.exist.security.SecurityManager manager = pool.getSecurityManager();
			User lockOwner = doc.getUserLock();
			if(lockOwner != null && !(lockOwner.equals(user) || manager.hasAdminPrivileges(user))) {
				throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
						"Resource is already locked by user " + lockOwner.getName());
			}
			doc.setUserLock(null);
            broker.storeXMLResource(transaction, doc);
            transact.commit(transaction);
		} catch (EXistException e) {
            transact.abort(transaction);
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
					e.getMessage(), e);
		} finally {
			((AbstractEXistResource) res).closeDocument(doc, Lock.WRITE_LOCK);
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
	    DBBroker broker = null;
	    DocumentImpl doc = null;
	    try {
	        broker = pool.get(user);
	        doc = ((AbstractEXistResource) resource).openDocument(broker, Lock.READ_LOCK);
	        return doc.getPermissions();
	    } catch (EXistException e) {
	        throw new XMLDBException(
					ErrorCodes.VENDOR_ERROR,
					e.getMessage(),
					e);
        } finally {
        	((AbstractEXistResource) resource).closeDocument(doc, Lock.READ_LOCK);
	        pool.release(broker);
	    }
	}

	public Permission[] listResourcePermissions() throws XMLDBException {
		DBBroker broker = null;
		org.exist.collections.Collection c = null;
		try {
			broker = pool.get(user);
			c = broker.openCollection(collection.getPathURI(), Lock.READ_LOCK);
			if (!c	.getPermissions().validate(user, Permission.READ))
				return new Permission[0];
			Permission perms[] =
				new Permission[c.getDocumentCount()];
			int j = 0;
			DocumentImpl doc;
			for (Iterator i = c.iterator(broker); i.hasNext(); j++) {
				doc = (DocumentImpl) i.next();
				perms[j] = doc.getPermissions();
			}
			return perms;
		} catch (EXistException e) {
		    throw new XMLDBException(
					ErrorCodes.VENDOR_ERROR,
					e.getMessage(),
					e);
        } finally {
        	if(c != null)
        		c.release(Lock.READ_LOCK);
		    pool.release(broker);
		}
	}

	public Permission[] listCollectionPermissions() throws XMLDBException {
		DBBroker broker = null;
		org.exist.collections.Collection c = null;
		try {
			broker = pool.get(user);
			c = broker.openCollection(collection.getPathURI(), Lock.READ_LOCK);
			if (!c.getPermissions().validate(user, Permission.READ))
				return new Permission[0];
			Permission perms[] =
				new Permission[c.getChildCollectionCount()];
			XmldbURI child;
			org.exist.collections.Collection childColl;
			int j = 0;
			for (Iterator i = c.collectionIterator(); i.hasNext(); j++) {
				child = (XmldbURI) i.next();
 				childColl =
					broker.openCollection(collection.getPathURI().append(child), Lock.READ_LOCK);
				if(childColl != null) {
					try {
						perms[j] = childColl.getPermissions();
					} finally {
						childColl.release(Lock.READ_LOCK);
					}
				}
			}
			return perms;
		} catch (EXistException e) {
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} finally {
			if(c != null)
				c.release(Lock.READ_LOCK);
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
		String[] groups = u.getGroups();
		for (int i = 0; i < groups.length; i++) {
			if(!(old.hasGroup(groups[i]) || manager.hasAdminPrivileges(user)))
				throw new XMLDBException(
					ErrorCodes.PERMISSION_DENIED,
					"not allowed to change group memberships");
		}
		u.setUID(old.getUID());
		manager.setUser(u);
	}
	
	public void addUserGroup(User user) throws XMLDBException {
		
	}
	
	public void removeGroup(User user, String rmgroup) throws XMLDBException {
		
	}
}

