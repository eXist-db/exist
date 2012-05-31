package org.exist.xmldb;

import java.io.IOException;
import java.util.Date;
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

/**
 * Local Implementation (i.e. embedded) of an eXist-specific service
 * which provides methods to manage users and
 * permissions.
 *
 * @author Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 * @author Modified by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
 * @author Adam Retter <adam@exist-db.org>
 */
public class LocalUserManagementService implements UserManagementService {
    
    private LocalCollection collection;

    private final BrokerPool pool;
    private final Subject user;

    public LocalUserManagementService(Subject user, BrokerPool pool, LocalCollection collection) {
        this.pool = pool;
        this.collection = collection;
        this.user = user;
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
                    manager.addAccount(u);
                    return null;
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
                    manager.addGroup(group);
                    return null;
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
    public String hasUserLock(final Resource res) throws XMLDBException {
        try {
            return executeWithBroker(new BrokerOperation<String>(){
                @Override
                public String withBroker(DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                    return readResource(broker, res, new DatabaseItemReader<DocumentImpl, String>(){
                        @Override
                        public String read(DocumentImpl document) {
                            Account lockOwner = document.getUserLock();
                            return lockOwner == null ? null : lockOwner.getName();
                        }
                    });
                }
            });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }
	
    @Override
    public void lockResource(final Resource resource, final Account u) throws XMLDBException {
        
        final String resourceId = resource.getId();
        
        try {
            executeWithBroker(new BrokerOperation<Void>(){
                @Override
                public Void withBroker(final DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                    return modifyResource(broker, resource, new DatabaseItemModifier<DocumentImpl, Void>(){
                        @Override
                        public Void modify(DocumentImpl document) throws PermissionDeniedException, SyntaxException, LockException {
                            if(!document.getPermissions().validate(user, Permission.WRITE)) {
                                throw new PermissionDeniedException("User is not allowed to lock resource " + resourceId);
                            }

                            SecurityManager manager = broker.getBrokerPool().getSecurityManager();
                            if(!(user.equals(u) || manager.hasAdminPrivileges(user))) {
                                throw new PermissionDeniedException("User " + user.getName() + " is not allowed to lock resource '" + resourceId + "' for user " + u.getName());
                            }

                            Account lockOwner = document.getUserLock();

                            if(lockOwner != null) {
                                if(lockOwner.equals(u)) {
                                    return null;
                                } else if(!manager.hasAdminPrivileges(user)) {
                                    throw new PermissionDeniedException("Resource '" + resourceId + "' is already locked by user " + lockOwner.getName());
                                }
                            }

                            document.setUserLock(u);

                            return null;
                        }
                    });
                }
            });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Unable to lock resource '" + resourceId + "': " + e.getMessage(), e);
        }
    }
	
    @Override
    public void unlockResource(final Resource resource) throws XMLDBException {
        
        final String resourceId = resource.getId();
        
        try {
            executeWithBroker(new BrokerOperation<Void>(){
                @Override
                public Void withBroker(final DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                    return modifyResource(broker, resource, new DatabaseItemModifier<DocumentImpl, Void>(){
                        @Override
                        public Void modify(DocumentImpl document) throws PermissionDeniedException, SyntaxException, LockException {
                            if(!document.getPermissions().validate(user, Permission.WRITE)) {
				throw new PermissionDeniedException("User is not allowed to lock resource '" + resourceId + "'");
                            }
			
                            
                            Account lockOwner = document.getUserLock();
			
                            SecurityManager manager = broker.getBrokerPool().getSecurityManager();
                            if(lockOwner != null && !(lockOwner.equals(user) || manager.hasAdminPrivileges(user))) {
                                throw new PermissionDeniedException("Resource '" + resourceId + "' is already locked by user " + lockOwner.getName());
                            }
                            
                            document.setUserLock(null);
                            
                            return null;
                        }
                    });
                }
            });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Unable to unlock resource '" + resourceId + "': " + e.getMessage(), e);
        }    
    }

    @Override
    public Permission getPermissions(Collection coll) throws XMLDBException {
        if(coll instanceof LocalCollection) {
            return ((LocalCollection) coll).getCollection().getPermissions();
        }
        return null;
    }

    @Override
    public Permission getSubCollectionPermissions(final Collection parent, final String name) throws XMLDBException {
        try {
            return executeWithBroker(new BrokerOperation<Permission>(){
                @Override
                public Permission withBroker(final DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                    if(parent instanceof LocalCollection) {
                        return ((LocalCollection) parent).getCollection().getSubCollectionEntry(broker, name).getPermissions();
                    }
                    return null;
                }
            });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e);
        }    
    }

    @Override
    public Permission getSubResourcePermissions(final Collection parent, final String name) throws XMLDBException {
        try {
            return executeWithBroker(new BrokerOperation<Permission>(){
                @Override
                public Permission withBroker(final DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                    if(parent instanceof LocalCollection) {
                        return ((LocalCollection) parent).getCollection().getResourceEntry(broker, name).getPermissions();
                    }
                    return null;
                }
            });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e);
        }
    }

    @Override
    public Date getSubCollectionCreationTime(final Collection parent, final String name) throws XMLDBException {
        try {
            return executeWithBroker(new BrokerOperation<Date>(){
                @Override
                public Date withBroker(final DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                    if(parent instanceof LocalCollection) {
                        return new Date(((LocalCollection) parent).getCollection().getSubCollectionEntry(broker, name).getCreated());
                    }
                    return null;
                }
            });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e);
        }
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
        
        final XmldbURI collectionUri = collection.getPathURI();
        try {
            return executeWithBroker(new BrokerOperation<Permission[]>() {
                @Override
                public Permission[] withBroker(final DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                    return readCollection(broker, collectionUri, new DatabaseItemReader<org.exist.collections.Collection, Permission[]>(){
                        @Override
                        public Permission[] read(org.exist.collections.Collection collection) throws PermissionDeniedException {
                            if(!collection.getPermissions().validate(user, Permission.READ)) {
                                    return new Permission[0];
                            }
                            
                            Permission perms[] = new Permission[collection.getDocumentCount(broker)];                            
                            Iterator<DocumentImpl> itDocument = collection.iterator(broker);
                            int i = 0;
                            while(itDocument.hasNext()) {
                                DocumentImpl document = itDocument.next();
                                perms[i++] = document.getPermissions();
                            }

                            return perms;
                        }
                    });
                }
            });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }   
    }

    @Override
    public Permission[] listCollectionPermissions() throws XMLDBException {
        
        final XmldbURI collectionUri = collection.getPathURI();
        try {
            return executeWithBroker(new BrokerOperation<Permission[]>() {
                @Override
                public Permission[] withBroker(final DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                    return readCollection(broker, collectionUri, new DatabaseItemReader<org.exist.collections.Collection, Permission[]>(){
                        @Override
                        public Permission[] read(org.exist.collections.Collection collection) throws XMLDBException, PermissionDeniedException {
                            if(!collection.getPermissions().validate(user, Permission.READ)) {
				return new Permission[0];
                            }
                            
                            Permission perms[] = new Permission[collection.getChildCollectionCount(broker)];
                            Iterator<XmldbURI> itChildCollectionUri = collection.collectionIterator(broker);
                            int i = 0;
                            while(itChildCollectionUri.hasNext()) {
                                XmldbURI childCollectionUri = collectionUri.append(itChildCollectionUri.next());
                                Permission childPermission = readCollection(broker, childCollectionUri, new DatabaseItemReader<org.exist.collections.Collection, Permission>(){
                                    @Override
                                    public Permission read(org.exist.collections.Collection childCollection) {
                                        return childCollection.getPermissions();
                                    }
                                });
                                perms[i++] = childPermission;
                            }
                            
			return perms;
                        }
                    });
                }
            });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }   	
    }

    @Override
    public Account getAccount(final String name) throws XMLDBException {
        
        try {
            return executeWithBroker(new BrokerOperation<Account>(){

                @Override
                public Account withBroker(DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                    SecurityManager sm = broker.getBrokerPool().getSecurityManager();
                    return sm.getAccount(name);
                }
            });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public Account[] getAccounts() throws XMLDBException {
        try {
            return executeWithBroker(new BrokerOperation<Account[]>(){

                @Override
                public Account[] withBroker(DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                    SecurityManager sm = broker.getBrokerPool().getSecurityManager();
                    java.util.Collection<Account> users = sm.getUsers();
                    return users.toArray(new Account[users.size()]);
                }
            });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public Group getGroup(final String name) throws XMLDBException {
        try {
            return executeWithBroker(new BrokerOperation<Group>(){

                @Override
                public Group withBroker(DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                    SecurityManager sm = broker.getBrokerPool().getSecurityManager();
                    return sm.getGroup(name);
                }
            });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public String[] getGroups() throws XMLDBException {
        try {
            return executeWithBroker(new BrokerOperation<String[]>(){

                @Override
                public String[] withBroker(DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                    SecurityManager sm = broker.getBrokerPool().getSecurityManager();
                    java.util.Collection<Group> groups = sm.getGroups();
                    String[] groupNames = new String[groups.size()];
                    int i = 0;
                    for (Group group : groups) {
                        groupNames[i++] = group.getName();
                    }
                    return groupNames;
                }
            });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public void removeAccount(final Account u) throws XMLDBException {
        try {
            executeWithBroker(new BrokerOperation<Void>(){

                @Override
                public Void withBroker(DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                    SecurityManager sm = broker.getBrokerPool().getSecurityManager();
                    if(!sm.hasAdminPrivileges(user))
                    	throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, "you are not allowed to remove users");
	        
                    sm.deleteAccount(u);
                    
                    return null;
                }
            });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public void removeGroup(final Group group) throws XMLDBException {
        try {
            executeWithBroker(new BrokerOperation<Void>(){
                @Override
                public Void withBroker(DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                    SecurityManager sm = broker.getBrokerPool().getSecurityManager();
	        
                    sm.deleteGroup(group.getName());
                    
                    return null;
                }
            });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public void setCollection(Collection collection) throws XMLDBException {
        this.collection = (LocalCollection) collection;
    }

    

    @Override
    public void updateAccount(final Account u) throws XMLDBException {
        try {
            executeWithBroker(new BrokerOperation<Void>(){

                @Override
                public Void withBroker(DBBroker broker) throws XMLDBException, LockException, PermissionDeniedException, IOException, EXistException, TriggerException, SyntaxException {
                    SecurityManager sm = broker.getBrokerPool().getSecurityManager();
	        
                    sm.updateAccount(u);
                    
                    return null;
                }
            });
        } catch(Exception e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
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
        
    @Override
    public String getProperty(String property) throws XMLDBException {
        return null;
    }
    
    @Override
    public void setProperty(String property, String value) throws XMLDBException {
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
    
    private <R> R readResource(DBBroker broker, Resource resource, DatabaseItemReader<DocumentImpl, R> reader) throws XMLDBException, PermissionDeniedException {
        
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
    
    private <R> R readCollection(DBBroker broker, XmldbURI collectionURI, DatabaseItemReader<org.exist.collections.Collection, R> reader) throws XMLDBException, PermissionDeniedException {
        org.exist.collections.Collection coll = null;
        
        try {
            coll = broker.openCollection(collectionURI, Lock.READ_LOCK);
            if(coll == null) {
                throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + collectionURI.toString() + " not found");
            }
            
            return reader.read(coll);
            
        } finally {
            if(coll != null) {
                coll.release(Lock.READ_LOCK);
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
        public R read(T databaseItem) throws PermissionDeniedException, XMLDBException;
    }
}