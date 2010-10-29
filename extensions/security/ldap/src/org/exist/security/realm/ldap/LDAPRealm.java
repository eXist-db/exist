/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
package org.exist.security.realm.ldap;

import java.util.List;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.annotation.*;
import org.exist.security.Account;
import org.exist.security.AuthenticationException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.security.AbstractAccount;
import org.exist.security.AbstractRealm;
import org.exist.security.Group;
import org.exist.security.internal.SecurityManagerImpl;
import org.exist.security.internal.SubjectAccreditedImpl;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
@ConfigurationClass("realm") //TODO: id = LDAP
public class LDAPRealm extends AbstractRealm<LDAPAccountImpl, LDAPGroupImpl> {

    private final static Logger LOG = Logger.getLogger(LDAPRealm.class);

    @ConfigurationFieldAsAttribute("id")
    public static String ID = "LDAP";

    @ConfigurationFieldAsElement("context")
    protected LdapContextFactory ldapContextFactory;

    public LDAPRealm(SecurityManagerImpl sm, Configuration config) {
        super(sm, config);
    }

    protected LdapContextFactory ensureContextFactory() {
        if(this.ldapContextFactory == null) {

            if(LOG.isDebugEnabled()) {
                LOG.debug("No LdapContextFactory specified - creating a default instance.");
            }

            LdapContextFactory factory = new LdapContextFactory(configuration);

            this.ldapContextFactory = factory;
        }
        return this.ldapContextFactory;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void startUp(DBBroker broker) throws EXistException {
        super.startUp(broker);
    }

    @Override
    public Subject authenticate(String username, Object credentials) throws AuthenticationException {
        // Binds using the username and password provided by the user.
        LdapContext ctx = null;
        try {
            ctx = ensureContextFactory().getLdapContext(username, String.valueOf(credentials));

        } catch(NamingException e) {
            throw new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, e.getMessage());

        } finally {
            LdapUtils.closeContext(ctx);
        }

        AbstractAccount account = (AbstractAccount) getAccount(null, username);
        if(account == null) {
            account = (AbstractAccount) createAccountInDatabase(null, username);
        }

        return new AuthenticatedLdapSubjectAccreditedImpl(account, ctx, String.valueOf(credentials));
    }

    private LDAPAccountImpl createAccountInDatabase(Subject invokingUser, String username) throws AuthenticationException {

        DBBroker broker = null;
        try {
            broker = getDatabase().get(invokingUser);

            //elevate to system privs
            broker.setUser(getSecurityManager().getSystemSubject());

            LDAPAccountImpl account = (LDAPAccountImpl)getSecurityManager().addAccount(new UserAider(ID, username));
            //LDAPAccountImpl account = sm.addAccount(instantiateAccount(ID, username));

            //TODO expand to a general method that rewrites the useraider based on the realTransformation
            boolean updatedAccount = false;
            if(ensureContextFactory().getTransformationContext() != null){
                List<String> additionalGroupNames = ensureContextFactory().getTransformationContext().getAdditionalGroups();
                if(additionalGroupNames != null) {
                    for(String additionalGroupName : additionalGroupNames) {
                        Group additionalGroup = getSecurityManager().getGroup(invokingUser, additionalGroupName);
                        if(additionalGroup != null) {
                            account.addGroup(additionalGroup);
                            updatedAccount = true;
                        }
                    }
                }
            }
            if(updatedAccount) {
                boolean updated = getSecurityManager().updateAccount(invokingUser, account);
                if(!updated) {
                    LOG.error("Could not update account");
                }
            }

            return account;

        } catch(Exception e) {
            throw new AuthenticationException(
                    AuthenticationException.UNNOWN_EXCEPTION,
                    e.getMessage(), e);
        } finally {
            if(broker != null) {
                getDatabase().release(broker);
            }
        }
    }

    private LDAPGroupImpl createGroupInDatabase(Subject invokingUser, String groupname) throws AuthenticationException {
        DBBroker broker = null;
        try {
            broker = getDatabase().get(invokingUser);

            //elevate to system privs
            broker.setUser(getSecurityManager().getSystemSubject());

            //return sm.addGroup(instantiateGroup(this, groupname));
            return (LDAPGroupImpl)getSecurityManager().addGroup(new GroupAider(ID, groupname));
        } catch(Exception e) {
            throw new AuthenticationException(
                    AuthenticationException.UNNOWN_EXCEPTION,
                    e.getMessage(), e);
        } finally {
            if(broker != null) {
                getDatabase().release(broker);
            }
        }
    }

    @Override
    public final synchronized LDAPAccountImpl getAccount(Subject invokingUser, String name) {

        //first attempt to get the cached account
        LDAPAccountImpl acct = super.getAccount(invokingUser, name);

        if(acct != null) {
            return acct;
        } else {
            //if the account is not cached, we should try and find it in LDAP and cache it if it exists
            LdapContext ctx = null;
            try{
                LdapContextFactory ctxFactory = ensureContextFactory();
                if(invokingUser != null && invokingUser instanceof AuthenticatedLdapSubjectAccreditedImpl) {
                    //use the provided credentials for the lookup
                    ctx = ctxFactory.getLdapContext(invokingUser.getUsername(), ((AuthenticatedLdapSubjectAccreditedImpl) invokingUser).getAuthenticatedCredentials());
                } else {
                    //use the default credentials for lookup
                    LDAPSearchContext searchCtx = ctxFactory.getSearch();
                    ctx = ctxFactory.getLdapContext(searchCtx.getDefaultUsername(), searchCtx.getDefaultPassword());
                }

                //do the lookup
                SearchResult ldapUser = findAccountByAccountName(ctx, name);
                if(ldapUser == null) {
                    return null;
                } else {
                    //found a user from ldap so cache them and return
                    try {
                        return createAccountInDatabase(invokingUser, name);
                        //registerAccount(acct); //TODO do we need this
                    } catch(AuthenticationException ae) {
                        LOG.error(ae.getMessage(), ae);
                        return null;
                    }
                }
            } catch(NamingException ne) {
                LOG.error(new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, ne.getMessage()));
                            return null;
            } finally {
                if(ctx != null){
                    LdapUtils.closeContext(ctx);
                }
            }
        }
    }

    @Override
    public final synchronized LDAPGroupImpl getGroup(Subject invokingUser, String name) {
        LDAPGroupImpl grp = groupsByName.get(name);
        if(grp != null) {
            return grp;
        } else {
            //if the group is not cached, we should try and find it in LDAP and cache it if it exists
            LdapContext ctx = null;
            try {
                LdapContextFactory ctxFactory = ensureContextFactory();
                if(invokingUser != null && invokingUser instanceof AuthenticatedLdapSubjectAccreditedImpl) {
                    //use the provided credentials for the lookup
                    ctx = ensureContextFactory().getLdapContext(invokingUser.getUsername(), ((AuthenticatedLdapSubjectAccreditedImpl) invokingUser).getAuthenticatedCredentials());
                } else {
                    //use the default credentials for lookup
                    LDAPSearchContext searchCtx = ctxFactory.getSearch();
                    ctx = ctxFactory.getLdapContext(searchCtx.getDefaultUsername(), searchCtx.getDefaultPassword());
                }

                //do the lookup
                SearchResult ldapGroup = findGroupByGroupName(ctx, name);
                if(ldapGroup == null) {
                    return null;
                } else {
                    //found a group from ldap so cache them and return
                    try {
                        return createGroupInDatabase(invokingUser, name);
                        //registerGroup(grp); //TODO do we need to do this?
                    } catch(AuthenticationException ae) {
                        LOG.error(ae.getMessage(), ae);
                        return null;
                    }
                }
            } catch(NamingException ne) {
                LOG.error(new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, ne.getMessage()));
                return null;
            } finally {
                if(ctx != null) {
                    LdapUtils.closeContext(ctx);
                }
            }
        }
    }

    private SearchResult findAccountByAccountName(DirContext ctx, String accountName) throws NamingException {

        String userName = accountName;
        if(userName.indexOf("@") > -1) {
            userName = userName.substring(0, userName.indexOf("@"));
        }

        String searchFilter = ensureContextFactory().getSearch().getAccountSearchFilter().replace("${account-name}", userName);
        //String searchFilter = "(&(objectClass=user)(sAMAccountName=" + userName + "))";

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        //TODO dont hardcode the search base!
        NamingEnumeration<SearchResult> results = ctx.search(ensureContextFactory().getSearch().getBase(), searchFilter, searchControls);

        if(results.hasMoreElements()) {
            SearchResult searchResult = (SearchResult) results.nextElement();

            //make sure there is not another item available, there should be only 1 match
            if(results.hasMoreElements()) {
                LOG.error("Matched multiple users for the accountName: " + accountName);
                return null;
            } else {
                return searchResult;
            }
        }
        return null;
    }

    private SearchResult findGroupByGroupName(DirContext ctx, String groupName) throws NamingException {

        String searchFilter = ensureContextFactory().getSearch().getGroupSearchFilter().replace("${group-name}", groupName);
        //String searchFilter = "(&(objectClass=group)(sAMAccountName=" + groupName + "))";

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        //TODO dont hardcode the search base!
        NamingEnumeration<SearchResult> results = ctx.search(ensureContextFactory().getSearch().getBase(), searchFilter, searchControls);

        if(results.hasMoreElements()) {
            SearchResult searchResult = (SearchResult) results.nextElement();

            //make sure there is not another item available, there should be only 1 match
            if(results.hasMoreElements()) {
                LOG.error("Matched multiple groups for the groupName: " + groupName);
                return null;
            } else {
                return searchResult;
            }
        }
        return null;
    }

    // configurable methods
    @Override
    public boolean isConfigured() {
        return (configuration != null);
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public boolean updateAccount(Subject invokingUser, LDAPAccountImpl account) throws PermissionDeniedException, EXistException {
        // TODO Auto-generated method stub
        return super.updateAccount(invokingUser, account);
    }

    @Override
    public boolean deleteAccount(Subject invokingUser, LDAPAccountImpl account) throws PermissionDeniedException, EXistException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean updateGroup(LDAPGroupImpl group) throws PermissionDeniedException, EXistException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean deleteGroup(LDAPGroupImpl group) throws PermissionDeniedException, EXistException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public LDAPGroupImpl instantiateGroup(AbstractRealm realm, Configuration config) throws ConfigurationException {
        return new LDAPGroupImpl(realm, config);
    }

    @Override
    public LDAPGroupImpl instantiateGroup(AbstractRealm realm, Configuration config, boolean removed) throws ConfigurationException {
        return new LDAPGroupImpl(realm, config, removed);
    }

    @Override
    public LDAPGroupImpl instantiateGroup(AbstractRealm realm, int id, String name) throws ConfigurationException {
        return new LDAPGroupImpl(realm, id, name);
    }

    @Override
    public LDAPGroupImpl instantiateGroup(AbstractRealm realm, String name) throws ConfigurationException {
        return new LDAPGroupImpl(realm, name);
    }

    @Override
    public LDAPAccountImpl instantiateAccount(AbstractRealm realm, String username) throws ConfigurationException {
        return new LDAPAccountImpl(realm, username);
    }

    @Override
    public LDAPAccountImpl instantiateAccount(AbstractRealm realm, Configuration config) throws ConfigurationException {
        return new LDAPAccountImpl(realm, config);
    }

    @Override
    public LDAPAccountImpl instantiateAccount(AbstractRealm realm, Configuration config, boolean removed) throws ConfigurationException {
        return new LDAPAccountImpl(realm, config, removed);
    }

    @Override
    public LDAPAccountImpl instantiateAccount(AbstractRealm realm, int id, Account from_account) throws ConfigurationException, PermissionDeniedException {
        return new LDAPAccountImpl(realm, id, from_account);
    }

    private final class AuthenticatedLdapSubjectAccreditedImpl extends SubjectAccreditedImpl {

        private final String authenticatedCredentials;

        private AuthenticatedLdapSubjectAccreditedImpl(AbstractAccount account, LdapContext ctx, String authenticatedCredentials) {
            super(account, ctx);
            this.authenticatedCredentials = authenticatedCredentials;
        }

        private String getAuthenticatedCredentials() {
            return authenticatedCredentials;
        }
    }
}
