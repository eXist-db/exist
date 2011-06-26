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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.config.Configuration;
import org.exist.config.annotation.*;
import org.exist.security.AXSchemaType;
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
import org.exist.security.realm.ldap.AbstractLDAPSearchPrincipal.LDAPSearchAttributeKey;
import org.exist.storage.DBBroker;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
@ConfigurationClass("realm") //TODO: id = LDAP
public class LDAPRealm extends AbstractRealm {

    private final static Logger LOG = Logger.getLogger(LDAPRealm.class);

    @ConfigurationFieldAsAttribute("id")
    public static String ID = "LDAP";

    @ConfigurationFieldAsAttribute("version")
    public final static String version = "1.0";

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
        /*if(account == null) {
            account = (AbstractAccount) createAccountInDatabase(null, username);
        }*/

        return new AuthenticatedLdapSubjectAccreditedImpl(account, ctx, String.valueOf(credentials));
    }

    private Account createAccountInDatabase(Subject invokingUser, String username, SearchResult ldapUser, String primaryGroupName) throws AuthenticationException {

        LDAPSearchAccount searchAccount = ensureContextFactory().getSearch().getSearchAccount();
        
        DBBroker broker = null;

        try {
            broker = getDatabase().get(invokingUser);

            //elevate to system privs
            broker.setUser(getSecurityManager().getSystemSubject());

            //get (or create) the primary group if it doesnt exist
            if(primaryGroupName != null) { //TODO remove this check as the primary group should never be null
                Group primaryGroup = getGroup(invokingUser, primaryGroupName);
            }
            
            //create member groups
            Object members = ldapUser.getAttributes().get(searchAccount.getSearchAttribute(LDAPSearchAttributeKey.MEMBER_OF)).get(); 
            
            //create the user account
            UserAider userAider = new UserAider(ID, username);

            //store any requested metadata
            for(AXSchemaType axSchemaType : searchAccount.getMetadataSearchAttributeKeys()) {
                String searchAttribute = searchAccount.getMetadataSearchAttribute(axSchemaType);
                Attributes userAttributes = ldapUser.getAttributes();
                if(userAttributes != null) {
                    Attribute userAttribute = userAttributes.get(searchAttribute);
                    if(userAttribute != null) {
                        String attributeValue = userAttribute.get().toString();
                        userAider.setMetadataValue(axSchemaType, attributeValue);
                    }
                }
            }

            Account account = getSecurityManager().addAccount(userAider);

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
                broker.setUser(invokingUser);
                getDatabase().release(broker);
            }
        }
    }

    private Group createGroupInDatabase(Subject invokingUser, String groupname) throws AuthenticationException {
        DBBroker broker = null;
        try {
            broker = getDatabase().get(invokingUser);

            //elevate to system privs
            broker.setUser(getSecurityManager().getSystemSubject());

            //return sm.addGroup(instantiateGroup(this, groupname));
            return getSecurityManager().addGroup(new GroupAider(ID, groupname));
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

    private LdapContext getContext(Subject invokingUser) throws NamingException {
        LdapContextFactory ctxFactory = ensureContextFactory();
        LdapContext ctx = null;
        if(invokingUser != null && invokingUser instanceof AuthenticatedLdapSubjectAccreditedImpl) {
            //use the provided credentials for the lookup
            ctx = ctxFactory.getLdapContext(invokingUser.getUsername(), ((AuthenticatedLdapSubjectAccreditedImpl) invokingUser).getAuthenticatedCredentials());
        } else {
            //use the default credentials for lookup
            LDAPSearchContext searchCtx = ctxFactory.getSearch();
            ctx = ctxFactory.getLdapContext(searchCtx.getDefaultUsername(), searchCtx.getDefaultPassword());
        }
        return ctx;
    }

    @Override
    public final synchronized Account getAccount(Subject invokingUser, String name) {

        //first attempt to get the cached account
        Account acct = super.getAccount(invokingUser, name);

        if(acct != null) {
            return acct;
        } else {
            //if the account is not cached, we should try and find it in LDAP and cache it if it exists
            LdapContext ctx = null;
            try{
                ctx = getContext(invokingUser);

                //do the lookup
                SearchResult ldapUser = findAccountByAccountName(ctx, name);
                if(ldapUser == null) {
                    return null;
                } else {
                    //found a user from ldap so cache them and return
                    try {
                        LDAPSearchContext search = ensureContextFactory().getSearch();
                        String primaryGroup = findGroupBySID(ctx, getPrimaryGroupSID(ldapUser));
                        return createAccountInDatabase(invokingUser, name, ldapUser, primaryGroup);
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
    
    /*
    private byte[] longToBytes(long l) throws IOException {
        DataOutputStream dos = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();  
            dos = new DataOutputStream(baos);
            dos.writeLong(l);
            dos.flush();
            return baos.toByteArray();
        } finally {
            dos.close();
        }
    }*/
    
    /*
    private long bytesToLong(byte b[]) throws IOException {
       long val = 0;
       int startOffset = (b.length >= 8 ? 7 : b.length - 1); //restrict size to long i.e. 8 bytes
       for(int i = startOffset; i > -1; i--) {
           val |= (((long)b[i]) << ((startOffset - i) * 8));
       }
       return val;
    }*/
    
    /**
     *
     * byte[0] - revision level
     * byte[1-7] - 48 bit authority
     * byte[8]- count of sub authorities
     * and then count x 32 bit sub authorities
     * 
     * S-Revision-Authority-SubAuths....
     */
    /*
    private String decodeSID(byte[] sid) {
        
        try {
            StringBuilder decodedSid = new StringBuilder("S-");

            //revision level
            decodedSid.append(Integer.toString(sid[0]));

            decodedSid.append("-");

            //authority
            byte[] authority = Arrays.copyOfRange(sid, 1, 7);
            decodedSid.append(Long.toHexString(bytesToLong(authority)));

            //get the count of sub authorities
            int countSubAuths = sid[7];

            int offset = 8;

            for(int i = 0; i < countSubAuths; i++) {
                byte subAuth[] = Arrays.copyOfRange(sid, offset, offset+4);
                decodedSid.append("-");
                decodedSid.append(Long.toHexString(bytesToLong(subAuth)));
                offset+= 4;
            }
            
            return decodedSid.toString();
            
        } catch(IOException ioe) {
            LOG.error(ioe.getMessage(), ioe);
            return null;
        }
    }*/
    
    private String decodeSID(byte[] sid) {
        String strSID = "";
        int version;
        long authority;
        int count;
        String rid = "";
        strSID = "S";

         // get version
        version = sid[0];
        strSID = strSID + "-" + Integer.toString(version);
        for (int i=6; i>0; i--) {
                rid += byte2hex(sid[i]);
        }

        // get authority
        authority = Long.parseLong(rid);
        strSID = strSID + "-" + Long.toString(authority);

        //next byte is the count of sub-authorities
        count = sid[7]&0xFF;

        //iterate all the sub-auths
        for (int i=0;i<count;i++) {
                rid = "";
                for (int j=11; j>7; j--) {
                        rid += byte2hex(sid[j+(i*4)]);
                }
                strSID = strSID + "-" + Long.parseLong(rid,16);
        }
        return strSID;    
    }
  
    public static String byte2hex(byte b) {
        String ret = Integer.toHexString((int)b&0xFF);
        if (ret.length()<2) {
            ret = "0"+ret;
        }
        return ret;
    }
    
    private String getPrimaryGroupSID(SearchResult ldapUser) throws NamingException {
        LDAPSearchContext search = ensureContextFactory().getSearch();
        byte[] objectSID = ((String)ldapUser.getAttributes().get(search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.OBJECT_SID)).get()).getBytes();
        String strPrimaryGroupID = (String)ldapUser.getAttributes().get(search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.PRIMARY_GROUP_ID)).get();
        
        String strObjectSid = decodeSID(objectSID);
        
        return strObjectSid.substring(0, strObjectSid.lastIndexOf('-') + 1) + strPrimaryGroupID;
        
        /*
        
        byte[] sid = objectSID.getBytes();
        final byte primaryGroupId[];
        try {
            primaryGroupId = longToBytes(Long.parseLong(strPrimaryGroupID));
        } catch(IOException ioe) {
            LOG.error(ioe.getMessage(), ioe);
            return null;
        }
        
        for(int i = 0; i < primaryGroupId.length; i++) {
            sid[sid.length - primaryGroupId.length - i] = primaryGroupId[i];
        }
        
        return sid;*/
    }
    
    private String getSIDAsByteString(byte[] sid) {
        String byteSID = "";
        int j;
        //Convert the SID into string using the byte format
        for (int i=0;i<sid.length;i++) {
                j = (int)sid[i] & 0xFF;
                if (j<0xF) {
                //add a leading zero, add two leading \\ to make it easy 
                //to paste into subsequent searches
                        byteSID = byteSID + "\\0" + Integer.toHexString(j);
                }
                else {
                        byteSID = byteSID + "\\" + Integer.toHexString(j);
                }
        }  
        return byteSID;
    }

    @Override
    public final synchronized Group getGroup(Subject invokingUser, String name) {
        Group grp = groupsByName.get(name);
        if(grp != null) {
            return grp;
        } else {
            //if the group is not cached, we should try and find it in LDAP and cache it if it exists
            LdapContext ctx = null;
            try {
                ctx = getContext(invokingUser);

                //do the lookup
                SearchResult ldapGroup = findGroupByGroupName(ctx, removeDomainPostfix(name));
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

    
    private String addDomainPostfix(String principalName) {
        String name = principalName;
        if(name.indexOf("@") == -1){
            name += '@' + ensureContextFactory().getDomain();
        }
        return name;
    }
    
    private String removeDomainPostfix(String principalName) {
        String name = principalName;
        if(name.indexOf('@') > -1 && name.endsWith(ensureContextFactory().getDomain())) {
            name = name.substring(0, name.indexOf('@'));
        }
        return name;
    }

    private boolean checkAccountRestrictionList(String accountname) {
        LDAPSearchContext search = ensureContextFactory().getSearch();
        return checkPrincipalRestrictionList(accountname, search.getSearchAccount());
    }
    
    private boolean checkGroupRestrictionList(String groupname) {
        LDAPSearchContext search = ensureContextFactory().getSearch();
        return checkPrincipalRestrictionList(groupname, search.getSearchGroup());
    }
    
    private boolean checkPrincipalRestrictionList(String principalName, AbstractLDAPSearchPrincipal searchPrinciple) {
        
        if(principalName.indexOf('@') > -1) {
            principalName = principalName.substring(0, principalName.indexOf('@'));
        }
        
        List<String> blackList = null;
        if(searchPrinciple.getBlackList() != null) {
            blackList = searchPrinciple.getBlackList().getRestrictionList();
        }
        
        List<String> whiteList = null;
        if(searchPrinciple.getWhiteList() != null) {
            whiteList = searchPrinciple.getWhiteList().getRestrictionList();
        }
        
        if(blackList != null) {
            for(String blackEntry : blackList) {
                if(blackEntry.equals(principalName)) {
                    return false;
                }
            }
        }
        
        if(whiteList != null && whiteList.size() > 0) {
            for(String whiteEntry : whiteList) {
                if(whiteEntry.equals(principalName)) {
                    return true;
                }
            }
            return false;
        }
        
        return true;
    }
    
    private SearchResult findAccountByAccountName(DirContext ctx, String accountName) throws NamingException {

        if(!checkAccountRestrictionList(accountName)) {
            return null;
        }
        
        String userName = removeDomainPostfix(accountName);

        LDAPSearchContext search = ensureContextFactory().getSearch();
        String searchFilter = buildSearchFilter(search.getSearchAccount().getSearchFilterPrefix(), search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.NAME), userName);

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        NamingEnumeration<SearchResult> results = ctx.search(search.getBase(), searchFilter, searchControls);

        SearchResult searchResult = null;
        if(results.hasMoreElements()) {
             searchResult = (SearchResult) results.nextElement();

            //make sure there is not another item available, there should be only 1 match
            if(results.hasMoreElements()) {
                LOG.error("Matched multiple users for the accountName: " + accountName);
            }
        }
        
        return searchResult;
    }

    private String findGroupBySID(DirContext ctx, String sid) throws NamingException {
        
        LDAPSearchContext search = ensureContextFactory().getSearch();
        String searchFilter = buildSearchFilter(search.getSearchGroup().getSearchFilterPrefix(), search.getSearchGroup().getSearchAttribute(LDAPSearchAttributeKey.OBJECT_SID), sid);

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        NamingEnumeration<SearchResult> results = ctx.search(search.getBase(), searchFilter, searchControls);

        if(results.hasMoreElements()) {
            SearchResult searchResult = (SearchResult) results.nextElement();

            //make sure there is not another item available, there should be only 1 match
            if(results.hasMoreElements()) {
                LOG.error("Matched multiple groups for the group with SID: " + sid);
                return null;
            } else {
                return addDomainPostfix((String)searchResult.getAttributes().get(search.getSearchGroup().getSearchAttribute(LDAPSearchAttributeKey.NAME)).get());
            }
        }
        return null;
    }
    
    private SearchResult findGroupByGroupName(DirContext ctx, String groupName) throws NamingException {

        if(!checkGroupRestrictionList(groupName)) {
            return null;
        }
        
        LDAPSearchContext search = ensureContextFactory().getSearch();
        String searchFilter = buildSearchFilter(search.getSearchGroup().getSearchFilterPrefix(), search.getSearchGroup().getSearchAttribute(LDAPSearchAttributeKey.NAME), groupName);

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        NamingEnumeration<SearchResult> results = ctx.search(search.getBase(), searchFilter, searchControls);

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
    public boolean updateAccount(Subject invokingUser, Account account) throws PermissionDeniedException, EXistException {
        // TODO Auto-generated method stub
        return super.updateAccount(invokingUser, account);
    }

    @Override
    public boolean deleteAccount(Subject invokingUser, Account account) throws PermissionDeniedException, EXistException {
        // TODO we dont support writting to LDAP
        return false;
    }

    @Override
    public boolean updateGroup(Subject invokingUser, Group group) throws PermissionDeniedException, EXistException {
        // TODO we dont support writting to LDAP
        return false;
    }

    @Override
    public boolean deleteGroup(Group group) throws PermissionDeniedException, EXistException {
        // TODO Auto-generated method stub
        return false;
    }

    private String buildSearchFilter(String searchPrefix, String attrName, String attrValue) {

        StringBuilder builder = new StringBuilder();
        builder.append("(");
        builder.append(buildSearchCriteria(searchPrefix));

        if(attrName != null && attrValue != null) {
            builder.append("(");
            builder.append(attrName);
            builder.append("=");
            builder.append(attrValue);
            builder.append(")");
        }
        builder.append(")");
        return builder.toString();
    }

    private String buildSearchCriteria(String searchPrefix) {
        return "&(" + searchPrefix + ")";
    }

    @Override
    public List<String> findUsernamesWhereNameStarts(Subject invokingUser, String startsWith) {
        List<String> usernames = new ArrayList<String>();

        LdapContext ctx = null;
        try {
            ctx = getContext(invokingUser);

            LDAPSearchContext search = ensureContextFactory().getSearch();
            String searchFilter = buildSearchFilter(search.getSearchAccount().getSearchFilterPrefix(), search.getSearchAccount().getMetadataSearchAttribute(AXSchemaType.FULLNAME), startsWith + "*");

            SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            searchControls.setReturningAttributes(new String[] { search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.NAME) });

            NamingEnumeration<SearchResult> results = ctx.search(search.getBase(), searchFilter, searchControls);

            SearchResult searchResult = null;
            while(results.hasMoreElements()) {
                searchResult = (SearchResult) results.nextElement();
                String username = addDomainPostfix((String)searchResult.getAttributes().get(search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.NAME)).get());
                if(checkAccountRestrictionList(username)) {
                    usernames.add(username);
                }
            }
        } catch(NamingException ne) {
            LOG.error(new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, ne.getMessage()));
        } finally {
            if(ctx != null) {
                LdapUtils.closeContext(ctx);
            }
        }

        return usernames;
    }

    @Override
    public List<String> findUsernamesWhereUsernameStarts(Subject invokingUser, String startsWith) {

        List<String> usernames = new ArrayList<String>();

        LdapContext ctx = null;
        try {
            ctx = getContext(invokingUser);

            LDAPSearchContext search = ensureContextFactory().getSearch();
            String searchFilter = buildSearchFilter(search.getSearchAccount().getSearchFilterPrefix(), search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.NAME), startsWith + "*");

            SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            searchControls.setReturningAttributes(new String[] { search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.NAME) });


            NamingEnumeration<SearchResult> results = ctx.search(search.getBase(), searchFilter, searchControls);

            SearchResult searchResult = null;
            while(results.hasMoreElements()) {
                searchResult = (SearchResult) results.nextElement();
                String username = addDomainPostfix((String)searchResult.getAttributes().get(search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.NAME)).get());
                
                if(checkAccountRestrictionList(username)) {
                    usernames.add(username);
                }
            }
        } catch(NamingException ne) {
            LOG.error(new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, ne.getMessage()));
        } finally {
            if(ctx != null) {
                LdapUtils.closeContext(ctx);
            }
        }

        return usernames;
    }
    
    @Override
    public List<String> findGroupnamesWhereGroupnameStarts(Subject invokingUser, String startsWith) {

        List<String> groupnames = new ArrayList<String>();

        LdapContext ctx = null;
        try {
            ctx = getContext(invokingUser);

            LDAPSearchContext search = ensureContextFactory().getSearch();
            String searchFilter = buildSearchFilter(search.getSearchGroup().getSearchFilterPrefix(), search.getSearchGroup().getSearchAttribute(LDAPSearchAttributeKey.NAME), startsWith + "*");

            SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            searchControls.setReturningAttributes(new String[] { search.getSearchGroup().getSearchAttribute(LDAPSearchAttributeKey.NAME) });


            NamingEnumeration<SearchResult> results = ctx.search(search.getBase(), searchFilter, searchControls);

            SearchResult searchResult = null;
            while(results.hasMoreElements()) {
                searchResult = (SearchResult) results.nextElement();
                String groupname = addDomainPostfix((String)searchResult.getAttributes().get(search.getSearchGroup().getSearchAttribute(LDAPSearchAttributeKey.NAME)).get());
                if(checkGroupRestrictionList(groupname)) {
                    groupnames.add(groupname);
                }
            }
        } catch(NamingException ne) {
            LOG.error(new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, ne.getMessage()));
        } finally {
            if(ctx != null) {
                LdapUtils.closeContext(ctx);
            }
        }

        return groupnames;
    }

    @Override
    public List<String> findAllGroupNames(Subject invokingUser) {
        List<String> groupnames = new ArrayList<String>();

        LdapContext ctx = null;
        try {
            ctx = getContext(invokingUser);

            LDAPSearchContext search = ensureContextFactory().getSearch();
            String searchFilter = buildSearchFilter(search.getSearchGroup().getSearchFilterPrefix(), null, null);

            SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            searchControls.setReturningAttributes(new String[] { search.getSearchGroup().getSearchAttribute(LDAPSearchAttributeKey.NAME) });

            NamingEnumeration<SearchResult> results = ctx.search(search.getBase(), searchFilter, searchControls);

            SearchResult searchResult = null;
            while(results.hasMoreElements()) {
                searchResult = (SearchResult) results.nextElement();
                String groupname = addDomainPostfix((String)searchResult.getAttributes().get(search.getSearchGroup().getSearchAttribute(LDAPSearchAttributeKey.NAME)).get());
                if(checkGroupRestrictionList(groupname)) {
                    groupnames.add(groupname);
                }
            }
        } catch(NamingException ne) {
            LOG.error(new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, ne.getMessage()));
        } finally {
            if(ctx != null) {
                LdapUtils.closeContext(ctx);
            }
        }

        return groupnames;
    }

    @Override
    public List<String> findAllGroupMembers(Subject invokingUser, String groupName) {

        List<String> groupMembers = new ArrayList<String>();
        
        if(!checkGroupRestrictionList(groupName)) {
            return groupMembers;
        }

        LdapContext ctx = null;
        try {
            ctx = getContext(invokingUser);

            //find the dn of the group
            SearchResult searchResult = findGroupByGroupName(ctx, removeDomainPostfix(groupName));
            LDAPSearchContext search = ensureContextFactory().getSearch();
            String dnGroup = (String)searchResult.getAttributes().get(search.getSearchGroup().getSearchAttribute(LDAPSearchAttributeKey.DN)).get();

            //find all accounts that are a member of the group
            String searchFilter = buildSearchFilter(search.getSearchAccount().getSearchFilterPrefix(), search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.MEMBER_OF), dnGroup);
            SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            searchControls.setReturningAttributes(new String[] { search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.NAME) });

            NamingEnumeration<SearchResult> results = ctx.search(search.getBase(), searchFilter, searchControls);

            while(results.hasMoreElements()) {
                searchResult = (SearchResult) results.nextElement();
                String member = addDomainPostfix((String)searchResult.getAttributes().get(search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.NAME)).get());
                if(checkAccountRestrictionList(member)) {
                    groupMembers.add(member);
                }
            }

        } catch(NamingException ne) {
            LOG.error(new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, ne.getMessage()));
        } finally {
            if(ctx != null) {
                LdapUtils.closeContext(ctx);
            }
        }

        return groupMembers;
    }

//    @Override
//    public LDAPGroupImpl instantiateGroup(AbstractRealm realm, Configuration config) throws ConfigurationException {
//        return new LDAPGroupImpl(realm, config);
//    }
//
//    @Override
//    public LDAPGroupImpl instantiateGroup(AbstractRealm realm, Configuration config, boolean removed) throws ConfigurationException {
//        return new LDAPGroupImpl(realm, config, removed);
//    }
//
//    @Override
//    public LDAPGroupImpl instantiateGroup(AbstractRealm realm, int id, String name) throws ConfigurationException {
//        return new LDAPGroupImpl(realm, id, name);
//    }
//
//    @Override
//    public LDAPGroupImpl instantiateGroup(AbstractRealm realm, String name) throws ConfigurationException {
//        return new LDAPGroupImpl(realm, name);
//    }
//
//    @Override
//    public LDAPAccountImpl instantiateAccount(AbstractRealm realm, String username) throws ConfigurationException {
//        return new LDAPAccountImpl(realm, username);
//    }
//
//    @Override
//    public LDAPAccountImpl instantiateAccount(AbstractRealm realm, Configuration config) throws ConfigurationException {
//        return new LDAPAccountImpl(realm, config);
//    }
//
//    @Override
//    public LDAPAccountImpl instantiateAccount(AbstractRealm realm, Configuration config, boolean removed) throws ConfigurationException {
//        return new LDAPAccountImpl(realm, config, removed);
//    }
//
//    @Override
//    public LDAPAccountImpl instantiateAccount(AbstractRealm realm, int id, Account from_account) throws ConfigurationException, PermissionDeniedException {
//        return new LDAPAccountImpl(realm, id, from_account);
//    }

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
