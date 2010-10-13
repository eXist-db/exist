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

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.config.Configuration;
import org.exist.config.annotation.*;
import org.exist.security.AuthenticationException;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.security.Account;
import org.exist.security.internal.AbstractAccount;
import org.exist.security.internal.AbstractRealm;
import org.exist.security.internal.SecurityManagerImpl;
import org.exist.security.internal.SubjectAccreditedImpl;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.DBBroker;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
@ConfigurationClass("realm") //TODO: id = LDAP
public class LDAPRealm extends AbstractRealm {

    private final static Logger LOG = Logger.getLogger(LDAPRealm.class);
    public static String ID = "LDAP";
    protected LdapContextFactory ldapContextFactory = null;

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
            account = (AbstractAccount) createAccountInDatabase(username);
        }

        return new AuthenticatedLdapSubjectAccreditedImpl(account, ctx, String.valueOf(credentials));
    }

    private Account createAccountInDatabase(String username) throws AuthenticationException {
        Subject currentSubject = getDatabase().getSubject();
        try {
            getDatabase().setSubject(sm.getSystemSubject());
            return sm.addAccount(new UserAider(ID, username));
        } catch(Exception e) {
            throw new AuthenticationException(
                    AuthenticationException.UNNOWN_EXCEPTION,
                    e.getMessage(), e);
        } finally {
            getDatabase().setSubject(currentSubject);
        }
    }

    private Group createGroupInDatabase(String groupname) throws AuthenticationException {
        Subject currentSubject = getDatabase().getSubject();
        try {
            getDatabase().setSubject(sm.getSystemSubject());
            return sm.addGroup(new GroupAider(ID, groupname));
        } catch(Exception e) {
            throw new AuthenticationException(
                    AuthenticationException.UNNOWN_EXCEPTION,
                    e.getMessage(), e);
        } finally {
            getDatabase().setSubject(currentSubject);
        }
    }

    @Override
    public final synchronized Account getAccount(Subject invokingUser, String name) {

        //first attempt to get the cached account
        Account acct = super.getAccount(invokingUser, name);
        if(acct != null) {
            return acct;
        } else {
            if(invokingUser != null) {
                if(invokingUser instanceof AuthenticatedLdapSubjectAccreditedImpl) {

                    //if the account is not cached, we should try and find it in LDAP and cache it if it exists
                    LdapContext ctx = null;
                    try {

                        ctx = ensureContextFactory().getLdapContext(invokingUser.getUsername(), ((AuthenticatedLdapSubjectAccreditedImpl) invokingUser).getAuthenticatedCredentials());

                        SearchResult ldapUser = findUserByAccountName(ctx, name);

                        if(ldapUser == null) {
                            return null;
                        } else {
                            //found a user from ldap so cache them and return
                            try {
                                return createAccountInDatabase(name);
                                //registerAccount(acct); //TODO do we need this
                            } catch(AuthenticationException ae) {
                                LOG.error(ae.getMessage(), ae);
                                return null;
                            }
                        }
                    } catch(NamingException e) {
                        LOG.error(new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, e.getMessage()));
                        return null;
                    } finally {
                        LdapUtils.closeContext(ctx);
                    }
                }
            }
            return null;
        }
    }

    @Override
    public final synchronized Group getGroup(Subject invokingUser, String name) {
        Group grp = groupsByName.get(name);
        if(grp != null) {
            return grp;
        } else {
            if(invokingUser != null) {
                if(invokingUser instanceof AuthenticatedLdapSubjectAccreditedImpl) {
                    //if the group is not cached, we should try and find it in LDAP and cache it if it exists
                    LdapContext ctx = null;
                    try {

                        ctx = ensureContextFactory().getLdapContext(invokingUser.getUsername(), ((AuthenticatedLdapSubjectAccreditedImpl) invokingUser).getAuthenticatedCredentials());

                        SearchResult ldapGroup = findGroupByGroupName(ctx, name);

                        if(ldapGroup == null) {
                            return null;
                        } else {
                            //found a user from ldap so cache them and return
                            try {
                                return createGroupInDatabase(name);
                                //registerGroup(grp); //TODO do we need to do this?
                            } catch(AuthenticationException ae) {
                                LOG.error(ae.getMessage(), ae);
                                return null;
                            }
                        }
                    } catch(NamingException e) {
                        LOG.error(new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, e.getMessage()));
                        return null;
                    } finally {
                        LdapUtils.closeContext(ctx);
                    }
                }
            }
            return null;
        }
    }

    private SearchResult findUserByAccountName(DirContext ctx, String accountName) throws NamingException {

        String userName = accountName;
        if(userName.indexOf("@") > -1) {
            userName = userName.substring(0, userName.indexOf("@"));
        }

        String searchFilter = "(&(objectClass=user)(sAMAccountName=" + userName + "))";

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        //TODO dont hardcode the search base!
        NamingEnumeration<SearchResult> results = ctx.search(ensureContextFactory().getBase(), searchFilter, searchControls);

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

        String searchFilter = "(&(objectClass=group)(sAMAccountName=" + groupName + "))";

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        //TODO dont hardcode the search base!
        NamingEnumeration<SearchResult> results = ctx.search(ensureContextFactory().getBase(), searchFilter, searchControls);

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
    public Account addAccount(Account account) throws PermissionDeniedException, EXistException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean updateAccount(Subject invokingUser, Account account) throws PermissionDeniedException, EXistException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean deleteAccount(Subject invokingUser, Account account) throws PermissionDeniedException, EXistException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Group addGroup(Group group) throws PermissionDeniedException, EXistException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean updateGroup(Group group) throws PermissionDeniedException, EXistException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean deleteGroup(Group group) throws PermissionDeniedException, EXistException {
        // TODO Auto-generated method stub
        return false;
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
