/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2011 The eXist Project
 *  http://exist-db.org
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
package org.exist.security.internal;

import java.util.*;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.Reference;
import org.exist.config.ReferenceImpl;
import org.exist.security.AXSchemaType;
import org.exist.security.AbstractAccount;
import org.exist.security.AbstractPrincipal;
import org.exist.security.AbstractRealm;
import org.exist.security.Account;
import org.exist.security.AuthenticationException;
import org.exist.security.EXistSchemaType;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.util.UUIDGenerator;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class RealmImpl extends AbstractRealm {
	
    public static String ID = "exist"; //TODO: final "eXist-db";

    private final static Logger LOG = LogManager.getLogger(RealmImpl.class);

    static public void setPasswordRealm(String value) {
        ID = value;
    }

    public final static int SYSTEM_ACCOUNT_ID = 1048575;
    public final static int ADMIN_ACCOUNT_ID = 1048574;
    public final static int GUEST_ACCOUNT_ID = 1048573;
    public final static int UNKNOWN_ACCOUNT_ID = 1048572;
    public final static int INITIAL_LAST_ACCOUNT_ID = 10;

    public final static int DBA_GROUP_ID = 1048575;
    public final static int GUEST_GROUP_ID = 1048574;
    public final static int UNKNOWN_GROUP_ID = 1048573;
    public final static int INITIAL_LAST_GROUP_ID = 10;

    protected final AccountImpl ACCOUNT_SYSTEM;
    protected final AccountImpl ACCOUNT_UNKNOWN;
    
    protected final GroupImpl GROUP_DBA;
    protected final GroupImpl GROUP_GUEST;
    protected final GroupImpl GROUP_UNKNOWN;
    
    private final static String DEFAULT_ADMIN_PASSWORD = "";
    private final static String DEFAULT_GUEST_PASSWORD = "guest";

    //@ConfigurationFieldAsElement("allow-guest-authentication")
    public boolean allowGuestAuthentication = true;

    protected RealmImpl(final DBBroker broker, final SecurityManagerImpl sm, final Configuration config) throws ConfigurationException { //, Configuration conf

    	super(sm, config);

    	sm.lastUserId = INITIAL_LAST_ACCOUNT_ID;     //TODO this is horrible!
    	sm.lastGroupId = INITIAL_LAST_GROUP_ID;    //TODO this is horrible!
        
        //DBA group
        GROUP_DBA = new GroupImpl(broker, this, DBA_GROUP_ID, SecurityManager.DBA_GROUP);
        GROUP_DBA.setManagers(new ArrayList<>(Arrays.asList(new ReferenceImpl<>(sm, "getAccount", SecurityManager.DBA_USER))));
        GROUP_DBA.setMetadataValue(EXistSchemaType.DESCRIPTION, "Database Administrators");
    	sm.addGroup(GROUP_DBA.getId(), GROUP_DBA);
        registerGroup(GROUP_DBA);
        //sm.groupsById.put(GROUP_DBA.getId(), GROUP_DBA);
    	//groupsByName.put(GROUP_DBA.getName(), GROUP_DBA);
        
        //System account
    	ACCOUNT_SYSTEM = new AccountImpl(broker, this, SYSTEM_ACCOUNT_ID, SecurityManager.SYSTEM, "", GROUP_DBA, true);
        ACCOUNT_SYSTEM.setMetadataValue(AXSchemaType.FULLNAME, SecurityManager.SYSTEM);
        ACCOUNT_SYSTEM.setMetadataValue(EXistSchemaType.DESCRIPTION, "System Internals");
        sm.addUser(ACCOUNT_SYSTEM.getId(), ACCOUNT_SYSTEM);
        registerAccount(ACCOUNT_SYSTEM);
    	//sm.usersById.put(ACCOUNT_SYSTEM.getId(), ACCOUNT_SYSTEM);
    	//usersByName.put(ACCOUNT_SYSTEM.getName(), ACCOUNT_SYSTEM);
        
        //guest group
        GROUP_GUEST = new GroupImpl(broker, this, GUEST_GROUP_ID, SecurityManager.GUEST_GROUP);
        GROUP_GUEST.setManagers(new ArrayList<Reference<SecurityManager, Account>>(){
            { add(new ReferenceImpl<>(sm, "getAccount", SecurityManager.DBA_USER)); }
        });
        GROUP_GUEST.setMetadataValue(EXistSchemaType.DESCRIPTION, "Anonymous Users");
        sm.addGroup(GROUP_GUEST.getId(), GROUP_GUEST);
        registerGroup(GROUP_GUEST);
    	//sm.groupsById.put(GROUP_GUEST.getId(), GROUP_GUEST);
    	//groupsByName.put(GROUP_GUEST.getName(), GROUP_GUEST);
        
        //unknown account and group
        GROUP_UNKNOWN = new GroupImpl(broker, this, UNKNOWN_GROUP_ID, "");
    	ACCOUNT_UNKNOWN = new AccountImpl(broker, this, UNKNOWN_ACCOUNT_ID, "", (String)null, GROUP_UNKNOWN);
        
        //XXX: GROUP_DBA._addManager(ACCOUNT_ADMIN);
    	//XXX: GROUP_GUEST._addManager(ACCOUNT_ADMIN);
    }

    @Override
    public void start(final DBBroker broker) throws EXistException {
        super.start(broker);
        try {
            createAdminAndGuestIfNotExist(broker);
        } catch(final PermissionDeniedException pde) {
            final boolean exportOnly =  (Boolean) broker.getConfiguration().getProperty(BrokerPool.PROPERTY_EXPORT_ONLY, false);
            if(!exportOnly) {
            	throw new EXistException(pde.getMessage(), pde);
            }
        }
    }
    
    private void createAdminAndGuestIfNotExist(final DBBroker broker) throws EXistException, PermissionDeniedException {
    	
        //Admin account
        if(getSecurityManager().getAccount(ADMIN_ACCOUNT_ID) == null) {
            //AccountImpl actAdmin = new AccountImpl(broker, this, ADMIN_ACCOUNT_ID, SecurityManager.DBA_USER, "", GROUP_DBA, true);
            final UserAider actAdmin = new UserAider(ADMIN_ACCOUNT_ID, getId(), SecurityManager.DBA_USER);
            actAdmin.setPassword(DEFAULT_ADMIN_PASSWORD);
            actAdmin.setMetadataValue(AXSchemaType.FULLNAME, SecurityManager.DBA_USER);
            actAdmin.setMetadataValue(EXistSchemaType.DESCRIPTION, "System Administrator");
            actAdmin.addGroup(SecurityManager.DBA_GROUP);
            getSecurityManager().addAccount(broker, actAdmin);
        }

        //Guest account
        if(getSecurityManager().getAccount(GUEST_ACCOUNT_ID) == null) {
            //AccountImpl actGuest = new AccountImpl(broker, this, GUEST_ACCOUNT_ID, SecurityManager.GUEST_USER, SecurityManager.GUEST_USER, GROUP_GUEST, false);
            final UserAider actGuest = new UserAider(GUEST_ACCOUNT_ID, getId(), SecurityManager.GUEST_USER);
            actGuest.setMetadataValue(AXSchemaType.FULLNAME, SecurityManager.GUEST_USER);
            actGuest.setMetadataValue(EXistSchemaType.DESCRIPTION, "Anonymous User");
            actGuest.setPassword(DEFAULT_GUEST_PASSWORD);
            actGuest.addGroup(SecurityManager.GUEST_GROUP);
            getSecurityManager().addAccount(broker, actGuest);
        }
    }
    
    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean deleteAccount(final Account account) throws PermissionDeniedException, EXistException {
        if(account == null) {
            return false;
        }
        
        usersByName.<PermissionDeniedException, EXistException>modify2E(principalDb -> {
            final AbstractAccount remove_account = (AbstractAccount)principalDb.get(account.getName());
            if(remove_account == null){
                throw new IllegalArgumentException("No such account exists!");
            }

            try(final DBBroker broker = getDatabase().getBroker()) {
                final Account user = broker.getCurrentSubject();

                if(!(account.getName().equals(user.getName()) || user.hasDbaRole()) ) {
                    throw new PermissionDeniedException("You are not allowed to delete '" +account.getName() + "' user");
                }

                remove_account.setRemoved(true);
                remove_account.setCollection(broker, collectionRemovedAccounts, XmldbURI.create(UUIDGenerator.getUUID()+".xml"));

                final TransactionManager transaction = getDatabase().getTransactionManager();
                try(final Txn txn = transaction.beginTransaction()) {
                    collectionAccounts.removeXMLResource(txn, broker, XmldbURI.create( remove_account.getName() + ".xml"));

                    transaction.commit(txn);
                } catch(final Exception e) {
                    LOG.warn(e.getMessage(), e);
                }

                getSecurityManager().addUser(remove_account.getId(), remove_account);
                principalDb.remove(remove_account.getName());
            }
        });
        
        return true;
    }

    @Override
    public boolean deleteGroup(final Group group) throws PermissionDeniedException, EXistException {
        if(group == null)
            {return false;}
        
        groupsByName.<PermissionDeniedException, EXistException>modify2E(principalDb -> {
            final AbstractPrincipal remove_group = (AbstractPrincipal)principalDb.get(group.getName());
            if(remove_group == null)
                {throw new IllegalArgumentException("Group does '"+group.getName()+"' not exist!");}

            final DBBroker broker = getDatabase().getActiveBroker();
            final Subject subject = broker.getCurrentSubject();

            ((Group)remove_group).assertCanModifyGroup(subject);

            remove_group.setRemoved(true);
            remove_group.setCollection(broker, collectionRemovedGroups, XmldbURI.create(UUIDGenerator.getUUID() + ".xml"));

            final TransactionManager transaction = getDatabase().getTransactionManager();
            try(final Txn txn = transaction.beginTransaction()) {

                collectionGroups.removeXMLResource(txn, broker, XmldbURI.create(remove_group.getName() + ".xml" ));

                transaction.commit(txn);
            } catch (final Exception e) {
                LOG.warn(e.getMessage(), e);
            }

            getSecurityManager().addGroup(remove_group.getId(), (Group)remove_group);
            principalDb.remove(remove_group.getName());
        });
        
        return true;
    }

    @Override
    public Subject authenticate(final String accountName, Object credentials) throws AuthenticationException {
        final Account account = getAccount(accountName);
        
        if(account == null) {
            throw new AuthenticationException(AuthenticationException.ACCOUNT_NOT_FOUND, "Account '" + accountName + "' not found.");
        }
        
        if("SYSTEM".equals(accountName) || (!allowGuestAuthentication && "guest".equals(accountName))) {
            throw new AuthenticationException(AuthenticationException.ACCOUNT_NOT_FOUND, "Account '" + accountName + "' can not be used.");
        }

        if(!account.isEnabled()) {
            throw new AuthenticationException(AuthenticationException.ACCOUNT_LOCKED, "Account '" + accountName + "' is disabled.");
        }

        final Subject subject = new SubjectImpl((AccountImpl) account, credentials);
        if(!subject.isAuthenticated()) {
            throw new AuthenticationException(AuthenticationException.WRONG_PASSWORD, "Wrong password for user [" + accountName + "] ");
        }
            
        return subject;
    }

    @Override
    public List<String> findUsernamesWhereUsernameStarts(final String prefix) {
        return usersByName.read(principalDb ->
                principalDb.keySet()
                        .stream()
                        .filter(userName -> userName.startsWith(prefix))
                        .collect(Collectors.toList())
        );
    }
    
    @Override
    public List<String> findGroupnamesWhereGroupnameStarts(final String prefix) {
        return groupsByName.read(principalDb -> 
                principalDb.keySet()
                .stream()
                .filter(groupName -> groupName.startsWith(prefix))
                .collect(Collectors.toList())
        );
    }
    
    @Override
    public Collection<? extends String> findGroupnamesWhereGroupnameContains(final String fragment) {
        return groupsByName.read(principalDb -> 
                principalDb.keySet()
                .stream()
                .filter(groupName -> groupName.contains(fragment))
                .collect(Collectors.toList())
        );
    }

    @Override
    public List<String> findAllGroupNames() {
        return groupsByName.read(principalDb -> new ArrayList<>(principalDb.keySet()));
    }
    
    @Override
    public List<String> findAllUserNames() {
        return usersByName.read(principalDb -> new ArrayList<>(principalDb.keySet()));
    }

    @Override
    public List<String> findAllGroupMembers(final String groupName) {
        return usersByName.read(principalDb ->
                principalDb.values()
                .stream()
                .filter(account -> account.hasGroup(groupName))
                .map(account -> account.getName())
                .collect(Collectors.toList())
        );
    }

    @Override
    public List<String> findUsernamesWhereNameStarts(final String startsWith) {
        return Collections.EMPTY_LIST;    //TODO at present exist users cannot have personal name details
    }

    @Override
    public List<String> findUsernamesWhereNamePartStarts(final String startsWith) {
        return Collections.EMPTY_LIST;    //TODO at present exist users cannot have personal name details
    }
}
