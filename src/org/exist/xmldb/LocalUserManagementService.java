package org.exist.xmldb;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.exist.EXistException;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DocumentImpl;
import org.exist.security.ACLPermission;
import org.exist.security.Group;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.security.Account;
import org.exist.security.User;
import org.exist.security.SecurityManager;
import org.exist.security.internal.aider.ACEAider;
import org.exist.security.internal.aider.UserAider;
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
	private Subject user;

	public LocalUserManagementService(
		Subject user,
		BrokerPool pool,
		LocalCollection collection) {
		this.pool = pool;
		this.collection = collection;
		this.user = user;
	}

    @Override
    public void addAccount(final Account u) throws XMLDBException {
		
        final SecurityManager manager = pool.getSecurityManager();
        
        if(!manager.hasAdminPrivileges(user)) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, " you are not allowed to change this user");
        }
        
        if(manager.hasAccount(u.getName())) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "user " + u.getName() + " exists");
        }
        
        try {
            executeWithBroker(new BrokerOperation<Void>(){
                @Override
                public Void withBroker(DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException {
                    return manager.addAccount(u);
                }
            });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void addGroup(final Group group) throws XMLDBException {
        final SecurityManager manager = pool.getSecurityManager();
		
        if(!manager.hasAdminPrivileges(user)) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, " you are not allowed to add role");
        }

        if(manager.hasGroup(group.getName())) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "group '" + group.getName() + "' exists");
        }
		
        try {
            executeWithBroker(new BrokerOperation<Void>(){
                @Override
                public Void withBroker(DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException {
                    return manager.addGroup(group);
                }
            });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    @Override
    public void setPermissions(final Resource resource, final Permission perm) throws XMLDBException {
        
        try {
             executeWithBroker(new BrokerOperation<Void>() {
                @Override
                public Void withBroker(final DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                    return modifyResource(broker, resource, new DatabaseItemModifier<DocumentImpl, Void>() {
                        @Override
                        public Void modify(DocumentImpl document) throws PermissionDeniedException, LockException {
                            document.setPermissions(perm);
                            return null;
                        }
                    });
                }
             });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Failed to modify permission on Resource '" + resource.getId() + "'", e);
        }
    }

    @Override
    public void setPermissions(final Collection child, final Permission perm) throws XMLDBException {
	
        final XmldbURI childUri = XmldbURI.create(child.getName());
        
        try {
             executeWithBroker(new BrokerOperation<Void>() {
                @Override
                public Void withBroker(final DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                    return modifyCollection(broker, childUri, new DatabaseItemModifier<org.exist.collections.Collection, Void>() {
                        @Override
                        public Void modify(org.exist.collections.Collection collection) throws PermissionDeniedException, LockException {
                            collection.setPermissions(perm);
                            return null;
                        }
                    });
                }
             });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Failed to modify permission on Collection '" + childUri.toString() + "'", e);
        }
    }
    
    @Override
    public void setPermissions(Collection child, final String owner, final String group, final int mode, final List<ACEAider> aces) throws XMLDBException {
            
        final XmldbURI childUri = XmldbURI.create(child.getName());
        
        try {
             executeWithBroker(new BrokerOperation<Void>() {
                @Override
                public Void withBroker(final DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                    return modifyCollection(broker, childUri, new DatabaseItemModifier<org.exist.collections.Collection, Void>() {
                        @Override
                        public Void modify(org.exist.collections.Collection collection) throws PermissionDeniedException, LockException {
                            Permission permission = collection.getPermissions();
                            permission.setOwner(owner);
                            permission.setGroup(group);
                            permission.setMode(mode);
                            if(permission instanceof ACLPermission) {
                                ACLPermission aclPermission = (ACLPermission)permission;
                                aclPermission.clear();
                                for(ACEAider ace : aces) {
                                    aclPermission.addACE(ace.getAccessType(), ace.getTarget(), ace.getWho(), ace.getMode());
                                }
                            }
                            return null;
                        }
                    });
                }
             });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Failed to modify permission on Collection '" + childUri.toString() + "'", e);
        }
    }
        
    @Override
    public void setPermissions(final Resource resource, final String owner, final String group, final int mode, final List<ACEAider> aces) throws XMLDBException {
            
        try {
             executeWithBroker(new BrokerOperation<Void>() {
                @Override
                public Void withBroker(final DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                    return modifyResource(broker, resource, new DatabaseItemModifier<DocumentImpl, Void>() {
                        @Override
                        public Void modify(DocumentImpl document) throws PermissionDeniedException, LockException {
                            Permission permission = document.getPermissions();
                            permission.setOwner(owner);
                            permission.setGroup(group);
                            permission.setMode(mode);
                            if(permission instanceof ACLPermission) {
                                ACLPermission aclPermission = (ACLPermission)permission;
                                aclPermission.clear();
                                for(ACEAider ace : aces) {
                                    aclPermission.addACE(ace.getAccessType(), ace.getTarget(), ace.getWho(), ace.getMode());
                                }
                            }
                            return null;
                        }
                    });
                }
             });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Failed to modify permission on Resource '" + resource.getId() + "'", e);
        }
    }


    @Override
    public void chmod(final String modeStr) throws XMLDBException {
        
        final XmldbURI collUri = collection.getPathURI();
        
        try {
             executeWithBroker(new BrokerOperation<Void>() {
                @Override
                public Void withBroker(final DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                   return modifyCollection(broker, collUri, new DatabaseItemModifier<org.exist.collections.Collection, Void>() {
                        @Override
                        public Void modify(org.exist.collections.Collection collection) throws PermissionDeniedException, SyntaxException, LockException {
                            collection.setPermissions(modeStr);
                            return null;
                        }
                    });
                }
             });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Failed to modify permission on Collection '" + collUri.toString() + "'", e);
        }
    }

    @Override
    public void chmod(final Resource resource, final int mode) throws XMLDBException {
        try {
             executeWithBroker(new BrokerOperation<Void>() {
                @Override
                public Void withBroker(final DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                    return modifyResource(broker, resource, new DatabaseItemModifier<DocumentImpl, Void>() {
                        @Override
                        public Void modify(DocumentImpl document) throws PermissionDeniedException, LockException {
                            document.getPermissions().setMode(mode);
                            return null;
                        }
                    });
                }
             });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Failed to modify permission on Resource '" + resource.getId() + "'", e);
        }
    }

    @Override
    public void chmod(final int mode) throws XMLDBException {
        final XmldbURI collUri = collection.getPathURI();
        
        try {
             executeWithBroker(new BrokerOperation<Void>() {
                @Override
                public Void withBroker(final DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                    return modifyCollection(broker, collUri, new DatabaseItemModifier<org.exist.collections.Collection, Void>() {
                        @Override
                        public Void modify(org.exist.collections.Collection collection) throws PermissionDeniedException, SyntaxException, LockException {
                            collection.setPermissions(mode);
                            return null;
                        }
                    });
                }
             });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Failed to modify permission on Collection '" + collUri.toString() + "'", e);
        }
    }

    @Override
    public void chmod(final Resource resource, final String modeStr) throws XMLDBException {
        try {
             executeWithBroker(new BrokerOperation<Void>() {
                @Override
                public Void withBroker(final DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                    return modifyResource(broker, resource, new DatabaseItemModifier<DocumentImpl, Void>() {
                        @Override
                        public Void modify(DocumentImpl document) throws SyntaxException, PermissionDeniedException, LockException {
                            document.getPermissions().setMode(modeStr);
                            return null;
                        }
                    });
                }
             });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Failed to modify permission on Resource '" + resource.getId() + "'", e);
        }
    }

    @Override
    public void chown(final Account u, final String group) throws XMLDBException {
        final XmldbURI collUri = collection.getPathURI();
        
        try {
             executeWithBroker(new BrokerOperation<Void>() {
                @Override
                public Void withBroker(final DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                    return modifyCollection(broker, collUri, new DatabaseItemModifier<org.exist.collections.Collection, Void>() {
                        @Override
                        public Void modify(org.exist.collections.Collection collection) throws PermissionDeniedException, SyntaxException, LockException {
                            Permission permission = collection.getPermissions();
                            permission.setOwner(u);
                            permission.setGroup(group);
                            return null;
                        }
                    });
                }
             });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Failed to modify permission on Collection '" + collUri.toString() + "'", e);
        }
    }

    @Override
    public void chown(final Resource resource, final Account u, final String group) throws XMLDBException {
	try {
             executeWithBroker(new BrokerOperation() {
                @Override
                public Void withBroker(final DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                    return modifyResource(broker, resource, new DatabaseItemModifier<DocumentImpl, Void>() {
                        @Override
                        public Void modify(DocumentImpl document) throws PermissionDeniedException, LockException {
                            document.getPermissions().setOwner(u);
                            document.getPermissions().setGroup(group);
                            return null;
                        }
                    });
                }
             });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Failed to modify permission on Resource '" + resource.getId() + "'", e);
        }	

    }

	/* (non-Javadoc)
	 * @see org.exist.xmldb.UserManagementService#hasUserLock(org.xmldb.api.base.Resource)
	 */
    @Override
    public String hasUserLock(Resource res) throws XMLDBException {

            DocumentImpl doc = null;
            DBBroker broker = null;
            try {
                broker = pool.get(user);
                doc = ((AbstractEXistResource) res).openDocument(broker, Lock.READ_LOCK);
                    Account lockOwner = doc.getUserLock();
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
	
    @Override
	public void lockResource(Resource res, Account u) throws XMLDBException {
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
			Account lockOwner = doc.getUserLock();
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
	
    @Override
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
			Account lockOwner = doc.getUserLock();
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
	
    @Override
	public String getName() {
		return "UserManagementService";
	}

    @Override
	public Permission getPermissions(Collection coll) throws XMLDBException {
		if (coll instanceof LocalCollection)
			return ((LocalCollection) coll).getCollection().getPermissions();
		return null;
	}

    @Override
    public Permission getPermissions(final Resource resource) throws XMLDBException {
        
        try {
            return executeWithBroker(new BrokerOperation<Permission>() {
                @Override
                public Permission withBroker(DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                    return readResource(broker, resource, new DatabaseItemReader<DocumentImpl, Permission>(){
                        @Override
                        public Permission read(DocumentImpl document) {
                            return document.getPermissions();
                        }
                    });
                }
            });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    @Override
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
			for (Iterator<DocumentImpl> i = c.iterator(broker); i.hasNext(); j++) {
				doc = i.next();
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

    @Override
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
			for (Iterator<XmldbURI> i = c.collectionIterator(); i.hasNext(); j++) {
				child = i.next();
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

    @Override
	public String getProperty(String property) throws XMLDBException {
		return null;
	}

    @Override
	public Account getAccount(String name) throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		
		DBBroker broker = null;
		try {
	        broker = pool.get(user);

	        return manager.getAccount(user, name);

		} catch (EXistException e) {
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} finally {
			pool.release(broker);
		}
	}

    @Override
	public Account[] getAccounts() throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();

		DBBroker broker = null;
		try {
	        broker = pool.get(user);
	        
	        java.util.Collection<Account> users = manager.getUsers();
	        return users.toArray(new Account[users.size()]);
	        
		} catch (EXistException e) {
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} finally {
			pool.release(broker);
		}
	}

    @Override
	public Group getGroup(String name) throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();

		DBBroker broker = null;
		try {
	        broker = pool.get(user);
	        
	        return manager.getGroup(user, name);

		} catch (EXistException e) {
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} finally {
			pool.release(broker);
		}
	}

    @Override
	public String[] getGroups() throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();

		DBBroker broker = null;
		try {
	        broker = pool.get(user);
	        
			java.util.Collection<Group> roles = manager.getGroups();
			String[] res = new String[roles.size()];
			int i = 0;
			for (Group role : roles) {
				res[i] = role.getName();
				i++;
			}
			return res;

		} catch (EXistException e) {
			throw new XMLDBException(
				ErrorCodes.VENDOR_ERROR,
				e.getMessage(),
				e);
		} finally {
			pool.release(broker);
		}
	}

    @Override
	public String getVersion() {
		return "1.0";
	}

    @Override
	public void removeAccount(Account u) throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		if (!manager.hasAdminPrivileges(user))
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				"you are not allowed to remove users");

		DBBroker broker = null;
		try {
	        broker = pool.get(user);
	        
			manager.deleteAccount(user, u);
		} catch (PermissionDeniedException e) {
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				"unable to remove user " + u.getName(),
				e);
		} catch (Exception e) {
			throw new XMLDBException(
					ErrorCodes.VENDOR_ERROR,
					e.getMessage(),
					e);
		} finally {
			pool.release(broker);
		}
	}

    @Override
	public void removeGroup(Group role) throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		if (!manager.hasAdminPrivileges(user))
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				"you are not allowed to remove users");

		DBBroker broker = null;
		try {
	        broker = pool.get(user);
	        
			manager.deleteGroup(user, role.getName());
		} catch (PermissionDeniedException e) {
			throw new XMLDBException(
				ErrorCodes.PERMISSION_DENIED,
				"unable to remove role " + role.getName(),
				e);
		} catch (EXistException e) {
			throw new XMLDBException(
					ErrorCodes.VENDOR_ERROR,
					e.getMessage(),
					e);
		} finally {
			pool.release(broker);
		}
	}

    @Override
	public void setCollection(Collection collection) throws XMLDBException {
		this.collection = (LocalCollection) collection;
	}

    @Override
	public void setProperty(String property, String value)
		throws XMLDBException {
	}

    @Override
	public void updateAccount(Account u) throws XMLDBException {
		org.exist.security.SecurityManager manager = pool.getSecurityManager();
		
		DBBroker broker = null;
		try {
			broker = pool.get(user);
		
			manager.updateAccount(user, u);
		} catch (PermissionDeniedException e) {
			throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage());
		} catch (Exception e) {
			new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage());
		} finally {
			pool.release(broker);
		}
	}
	
    @Override
	public void addUserGroup(Account user) throws XMLDBException {
		
	}
	
    @Override
	public void removeGroup(Account user, String rmgroup) throws XMLDBException {
		
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

    private interface BrokerOperation<R> {
        public R withBroker(DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException;
    }

    private <R> R executeWithBroker(BrokerOperation<R> brokerOperation) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
        DBBroker broker = null;
        try {
            broker = pool.get(user);
            
            return brokerOperation.withBroker(broker);
            
        } finally {
            if(broker != null) {
                pool.release(broker);
            }
        }
    }
    
    private <R> R readResource(DBBroker broker, Resource resource, DatabaseItemReader<DocumentImpl, R> reader) throws XMLDBException {
        
        DocumentImpl document = null;    
        try {
            document = ((AbstractEXistResource) resource).openDocument(broker, Lock.READ_LOCK);
                
            return reader.read(document);
                
        } finally {
            if(document != null) {
                ((AbstractEXistResource) resource).closeDocument(document, Lock.READ_LOCK);
            }
        }
    }
    
    private <R> R modifyResource(DBBroker broker, Resource resource, DatabaseItemModifier<DocumentImpl, R> modifier) throws XMLDBException, LockException, PermissionDeniedException, EXistException, SyntaxException {
        TransactionManager transact = broker.getBrokerPool().getTransactionManager();
        Txn transaction = transact.beginTransaction();
        
        DocumentImpl document = null;
        try {
            document = ((AbstractEXistResource) resource).openDocument(broker, Lock.WRITE_LOCK);
            SecurityManager sm = broker.getBrokerPool().getSecurityManager();
            if(!document.getPermissions().validate(user, Permission.WRITE) && !sm.hasAdminPrivileges(user)) {
                throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, "you are not the owner of this resource; owner = " + document.getPermissions().getOwner());
            }
            
            R result = modifier.modify(document);
            
            broker.storeXMLResource(transaction, document);
            transact.commit(transaction);
            
            return result;
            
        } catch(EXistException ee) {
            transact.abort(transaction);
            throw ee;
        } catch(XMLDBException xmldbe) {
            transact.abort(transaction);
            throw xmldbe;
        } catch(LockException le) {
            transact.abort(transaction);
            throw le;
        } catch(PermissionDeniedException pde) {
            transact.abort(transaction);
            throw pde;
        } catch(SyntaxException se) {
            transact.abort(transaction);
            throw se;
        } finally {
            if(document != null) {
                ((AbstractEXistResource)resource).closeDocument(document, Lock.WRITE_LOCK);
            }
        }
    }
    
    private <R> R modifyCollection(DBBroker broker, XmldbURI collectionURI, DatabaseItemModifier<org.exist.collections.Collection, R> modifier) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
        TransactionManager transact = broker.getBrokerPool().getTransactionManager();
        Txn transaction = transact.beginTransaction();
        
        org.exist.collections.Collection coll = null;
        
        try {
            coll = broker.openCollection(collectionURI, Lock.WRITE_LOCK);
            if(coll == null) {
                throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + collectionURI.toString() + " not found");
            }
            
            R result = modifier.modify(coll);
            
            broker.saveCollection(transaction, coll);
            transact.commit(transaction);
            broker.flush();
            
            return result;
            
        } catch(EXistException ee) {
            transact.abort(transaction);
            throw ee;
        } catch(XMLDBException xmldbe) {
            transact.abort(transaction);
            throw xmldbe;
        } catch(LockException le) {
            transact.abort(transaction);
            throw le;
        } catch(PermissionDeniedException pde) {
            transact.abort(transaction);
            throw pde;
        } catch(IOException ioe) {
            transact.abort(transaction);
            throw ioe;
        } catch(TriggerException te) {
            transact.abort(transaction);
            throw te;
        } catch(SyntaxException se) {
            transact.abort(transaction);
            throw se;
        } finally {
            if(coll != null) {
                coll.release(Lock.WRITE_LOCK);
            }
        }
    }
    
    private interface DatabaseItemModifier<T, R> {
        public R modify(T databaseItem) throws PermissionDeniedException, SyntaxException, LockException;
    }
    
    private interface DatabaseItemReader<T, R> {
        public R read(T databaseItem);
    }
}