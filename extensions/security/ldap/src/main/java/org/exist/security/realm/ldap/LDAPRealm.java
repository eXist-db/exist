/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.security.realm.ldap;

import java.lang.reflect.Field;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import com.evolvedbinary.j8fu.function.BiFunction3E;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.config.Configuration;
import org.exist.config.annotation.*;
import org.exist.security.AXSchemaType;
import org.exist.security.AbstractAccount;
import org.exist.security.AbstractRealm;
import org.exist.security.Account;
import org.exist.security.AuthenticationException;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SchemaType;
import org.exist.security.Subject;
import org.exist.security.internal.SecurityManagerImpl;
import org.exist.security.internal.SubjectAccreditedImpl;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.security.realm.ldap.AbstractLDAPSearchPrincipal.LDAPSearchAttributeKey;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
@ConfigurationClass("realm") //TODO: id = LDAP
public class LDAPRealm extends AbstractRealm {

    private static final Logger LOG = LogManager.getLogger(LDAPRealm.class);

    @ConfigurationFieldAsAttribute("id")
    public static String ID = "LDAP";

    @ConfigurationFieldAsAttribute("version")
    public static final String version = "1.0";

    @ConfigurationFieldAsAttribute("principals-are-case-insensitive")
    private boolean principalsAreCaseInsensitive;

    @ConfigurationFieldAsElement("context")
    protected LdapContextFactory ldapContextFactory;

    public LDAPRealm(final SecurityManagerImpl sm, final Configuration config) {
        super(sm, config);
    }

    protected LdapContextFactory ensureContextFactory() {
        if (this.ldapContextFactory == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No LdapContextFactory specified - creating a default instance.");
            }
            this.ldapContextFactory = new LdapContextFactory(configuration);
        }
        return this.ldapContextFactory;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void start(final DBBroker broker, final Txn transaction) throws EXistException {
        super.start(broker, transaction);
    }

    private String ensureCase(final String username) {
        if (username == null) {
            return null;
        }

        if (principalsAreCaseInsensitive) {
            return username.toLowerCase();
        }

        return username;
    }

    @Override
    public Subject authenticate(final String username, final Object credentials) throws AuthenticationException {

        final String name = ensureCase(username);

        // Binds using the username and password provided by the user.
        LdapContext ctx = null;
        try {
            ctx = getContextWithCredentials(Optional.of(Tuple(name, String.valueOf(credentials))));

            final AbstractAccount account = (AbstractAccount) getAccount(ctx, name);
            if (account == null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Account '{}' can not be found.", name);
                }
                throw new AuthenticationException(
                        AuthenticationException.ACCOUNT_NOT_FOUND,
                        "Account '" + name + "' can not be found.");
            }

            return new AuthenticatedLdapSubjectAccreditedImpl(account, ctx, String.valueOf(credentials));

        } catch (final NamingException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(e.getMessage(), e);
            }
            if (e instanceof javax.naming.AuthenticationException) {
                throw new AuthenticationException(AuthenticationException.ACCOUNT_NOT_FOUND, e.getMessage());
            } else {
                throw new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, e.getMessage());
            }

        } finally {
            LdapUtils.closeContext(ctx);
        }
    }

    private List<Group> getGroupMembershipForLdapUser(final LdapContext ctx, final DBBroker broker, final SearchResult ldapUser) throws NamingException {
        final List<Group> memberOf_groups = new ArrayList<>();

        final LDAPSearchContext search = ensureContextFactory().getSearch();
        final String userDistinguishedName = (String) ldapUser.getAttributes().get(search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.DN)).get();
        final List<String> memberOf_groupNames = findGroupnamesForUserDistinguishedName(ctx, userDistinguishedName);
        for (final String memberOf_groupName : memberOf_groupNames) {
            memberOf_groups.add(getGroup(ctx, broker, memberOf_groupName));
        }

        //TODO expand to a general method that rewrites the useraider based on the realTransformation
        if (ensureContextFactory().getTransformationContext() != null) {
            final List<String> additionalGroupNames = ensureContextFactory().getTransformationContext().getAdditionalGroups();
            if (additionalGroupNames != null) {
                for (final String additionalGroupName : additionalGroupNames) {
                    final Group additionalGroup = getSecurityManager().getGroup(additionalGroupName);
                    if (additionalGroup != null) {
                        memberOf_groups.add(additionalGroup);
                    }
                }
            }
        }

        return memberOf_groups;
    }

    private List<SimpleEntry<AXSchemaType, String>> getMetadataForLdapUser(final SearchResult ldapUser) throws NamingException {
        final List<SimpleEntry<AXSchemaType, String>> metadata = new ArrayList<>();
        final LDAPSearchAccount searchAccount = ensureContextFactory().getSearch().getSearchAccount();
        final Attributes userAttributes = ldapUser.getAttributes();

        //store any requested metadata
        for (final AXSchemaType axSchemaType : searchAccount.getMetadataSearchAttributeKeys()) {
            final String searchAttribute = searchAccount.getMetadataSearchAttribute(axSchemaType);
            if (userAttributes != null) {
                final Attribute userAttribute = userAttributes.get(searchAttribute);
                if (userAttribute != null) {
                    final String attributeValue = userAttribute.get().toString();
                    metadata.add(new SimpleEntry<>(axSchemaType, attributeValue));
                }
            }
        }

        return metadata;
    }

    public Account refreshAccountFromLdap(final Account account) throws PermissionDeniedException, AuthenticationException {
        final int UPDATE_NONE = 0;
        final int UPDATE_GROUP = 1;
        final int UPDATE_METADATA = 2;

        final Subject invokingUser = getSecurityManager().getCurrentSubject();

        if (!invokingUser.hasDbaRole() && invokingUser.getId() != account.getId()) {
            throw new PermissionDeniedException("You do not have permission to modify the account");
        }

        LdapContext ctx = null;
        try {
             ctx = getContext(invokingUser);
            final SearchResult ldapUser = findAccountByAccountName(ctx, account.getName());
            if (ldapUser == null) {
                throw new AuthenticationException(AuthenticationException.ACCOUNT_NOT_FOUND, "Could not find the account in the LDAP");
            }

            return executeAsSystemUser(ctx, (ctx2, broker) -> {

                    int update = UPDATE_NONE;

                    //1) get the ldap group membership
                    final List<Group> memberOf_groups = getGroupMembershipForLdapUser(ctx2, broker, ldapUser);

                    //2) get the ldap primary group
                    final String primaryGroup = findGroupBySID(ctx2, getPrimaryGroupSID(ldapUser));

                    //append the ldap primaryGroup to the head of the ldap group list, and compare
                    //to the account group list
                    memberOf_groups.addFirst(getGroup(ctx2, broker, primaryGroup));

                    final String accountGroups[] = account.getGroups();

                    if (!accountGroups[0].equals(ensureCase(primaryGroup))) {
                        update |= UPDATE_GROUP;
                    } else {
                        if (accountGroups.length != memberOf_groups.size()) {
                            update |= UPDATE_GROUP;
                        } else {
                            for (final String accountGroup : accountGroups) {
                                boolean found = false;

                                for (final Group memberOf_group : memberOf_groups) {
                                    if (accountGroup.equals(ensureCase(memberOf_group.getName()))) {
                                        found = true;
                                        break;
                                    }
                                }

                                if (!found) {
                                    update |= UPDATE_GROUP;
                                    break;
                                }
                            }
                        }
                    }

                    //3) check metadata
                    final List<SimpleEntry<AXSchemaType, String>> ldapMetadatas = getMetadataForLdapUser(ldapUser);
                    final Set<SchemaType> accountMetadataKeys = account.getMetadataKeys();

                    if (accountMetadataKeys.size() != ldapMetadatas.size()) {
                        update |= UPDATE_METADATA;
                    } else {
                        for (SchemaType accountMetadataKey : accountMetadataKeys) {
                            final String accountMetadataValue = account.getMetadataValue(accountMetadataKey);

                            boolean found = false;

                            for (SimpleEntry<AXSchemaType, String> ldapMetadata : ldapMetadatas) {
                                if (accountMetadataKey.equals(ldapMetadata.getKey()) && accountMetadataValue.equals(ldapMetadata.getValue())) {
                                    found = true;
                                    break;
                                }
                            }

                            if (!found) {
                                update |= UPDATE_METADATA;
                                break;
                            }
                        }
                    }

                    //update the groups?
                    if ((update & UPDATE_GROUP) == UPDATE_GROUP) {
                        try {
                            final Field fld = account.getClass().getSuperclass().getDeclaredField("groups");
                            fld.setAccessible(true);
                            fld.set(account, memberOf_groups);
                        } catch (final NoSuchFieldException | IllegalAccessException nsfe) {
                            throw new EXistException(nsfe.getMessage(), nsfe);
                        }
                    }

                    //update the metdata?
                    if ((update & UPDATE_METADATA) == UPDATE_METADATA) {
                        account.clearMetadata();
                        for (final SimpleEntry<AXSchemaType, String> ldapMetadata : ldapMetadatas) {
                            account.setMetadataValue(ldapMetadata.getKey(), ldapMetadata.getValue());
                        }
                    }

                    if (update != UPDATE_NONE) {
                        final boolean updated = getSecurityManager().updateAccount(account);
                        if (!updated) {
                            LOG.error("Could not update account");
                        }
                    }

                    return account;
            });
        } catch (final NamingException | EXistException ne) {
            throw new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, ne.getMessage(), ne);
        } finally {
            LdapUtils.closeContext(ctx);
        }
    }

    private Account createAccountInDatabase(final LdapContext ctx, final String username, final SearchResult ldapUser, final String primaryGroupName) throws AuthenticationException {

        //final LDAPSearchAccount searchAccount = ensureContextFactory().getSearch().getSearchAccount();

        try {
            return executeAsSystemUser(ctx, (ctx2, broker) -> {

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Saving account '{}'.", username);
                    }

                    //get (or create) the primary group if it doesnt exist
                    final Group primaryGroup = getGroup(ctx, broker, primaryGroupName);

                    //get (or create) member groups
                    /*LDAPSearchContext search = ensureContextFactory().getSearch();
                    String userDistinguishedName = (String)ldapUser.getAttributes().get(search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.DN)).get();
                    List<String> memberOf_groupNames = findGroupnamesForUserDistinguishedName(invokingUser, userDistinguishedName);

                    List<Group> memberOf_groups = new ArrayList<Group>();
                    for(String memberOf_groupName : memberOf_groupNames) {
                        memberOf_groups.add(getGroup(invokingUser, memberOf_groupName));
                    }*/

                    //create the user account
                    final UserAider userAider = new UserAider(ID, username, primaryGroup);

                    //add the member groups
                    for (final Group memberOf_group : getGroupMembershipForLdapUser(ctx, broker, ldapUser)) {
                        userAider.addGroup(memberOf_group);
                    }

                    //store any requested metadata
                    for (final SimpleEntry<AXSchemaType, String> metadata : getMetadataForLdapUser(ldapUser)) {
                        userAider.setMetadataValue(metadata.getKey(), metadata.getValue());
                    }

                    final Account account = getSecurityManager().addAccount(userAider);

                    //LDAPAccountImpl account = sm.addAccount(instantiateAccount(ID, username));

                    //TODO expand to a general method that rewrites the useraider based on the realTransformation
                    /*
                    boolean updatedAccount = false;
                    if(ensureContextFactory().getTransformationContext() != null){
                        List<String> additionalGroupNames = ensureContextFactory().getTransformationContext().getAdditionalGroups();
                        if(additionalGroupNames != null) {
                            for(String additionalGroupName : additionalGroupNames) {
                                Group additionalGroup = getSecurityManager().getGroup(invokingUser, ensureCase(additionalGroupName));
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
                    }*/

                    return account;
            });
        } catch (final Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(e);
            }
            throw new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, e.getMessage(), e);
        }
    }

    private interface LDAPFunction<R> extends BiFunction3E<LdapContext, DBBroker, R, EXistException, PermissionDeniedException, NamingException> {}

    private <R> R executeAsSystemUser(final LdapContext ctx, final LDAPFunction<R> ldapFunction) throws EXistException, PermissionDeniedException, NamingException {
        try (final DBBroker broker = getDatabase().get(Optional.of(getSecurityManager().getSystemSubject()))) {
            //perform as SYSTEM user
            return ldapFunction.apply(ctx, broker);
        }
    }

    private Group createGroupInDatabase(final DBBroker broker, final String groupname) throws AuthenticationException {
        try {
            //return sm.addGroup(instantiateGroup(this, groupname));
            return getSecurityManager().addGroup(broker, new GroupAider(ID, groupname));

        } catch (Exception e) {
            throw new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, e.getMessage(), e);
        }
    }

    private LdapContext getContext(@Nullable final Subject invokingUser) throws NamingException {
        return getContext(Optional.ofNullable(invokingUser));
    }

    private LdapContext getContext(final Optional<Subject> invokingUser) throws NamingException {
        final Optional<Tuple2<String, String>> credentials = invokingUser
                .filter(subject -> subject instanceof AuthenticatedLdapSubjectAccreditedImpl)
                .map(subject -> (AuthenticatedLdapSubjectAccreditedImpl) subject)
                .map(subject -> Tuple(subject.getUsername(), subject.getAuthenticatedCredentials()));

        return getContextWithCredentials(credentials);
    }

    /**
     * Gets an LDAP Context for the provided user details,
     * or if none are provided the default configured
     * credentials are used.
     *
     * @param optCredentials Explicit credentials
     * @return An LDAP Context
     */
    private LdapContext getContextWithCredentials(final Optional<Tuple2<String, String>> optCredentials) throws NamingException {
        final LdapContextFactory ctxFactory = ensureContextFactory();
        final Tuple2<String, String> credentials = optCredentials.orElseGet(() -> defaultCredentials(ctxFactory));
        return ctxFactory.getLdapContext(credentials._1, credentials._2, null);
    }

    private Tuple2<String, String> defaultCredentials(final LdapContextFactory ctxFactory) {
        final LDAPSearchContext searchCtx = ctxFactory.getSearch();
        return Tuple(searchCtx.getDefaultUsername(), searchCtx.getDefaultPassword());
    }

    @Override
    public final synchronized Account getAccount(String name) {
        name = ensureCase(name);

        //first attempt to get the cached account
        final Account acct = super.getAccount(name);

        if (acct != null) {
            return acct;
        } else {
            LdapContext ctx = null;
            try {
                ctx = getContext(getSecurityManager().getDatabase().getActiveBroker().getCurrentSubject());
                return getAccount(ctx, name);
            } catch (final NamingException ne) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(ne.getMessage(), ne);
                }
                LOG.error(new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, ne.getMessage()));
                return null;
            } finally {
                if (ctx != null) {
                    LdapUtils.closeContext(ctx);
                }
            }
        }
    }

    private synchronized Account getAccount(final LdapContext ctx, String name) {
        name = ensureCase(name);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Get request for account '{}'.", name);
        }

        //first attempt to get the cached account
        final Account acct = super.getAccount(name);

        if (acct != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Cached used.");
            }
            //XXX: synchronize with LDAP
            return acct;
        } else {
            //if the account is not cached, we should try and find it in LDAP and cache it if it exists
            try {
                //do the lookup
                final SearchResult ldapUser = findAccountByAccountName(ctx, name);

                if (LOG.isDebugEnabled()) {
                    LOG.debug("LDAP search return '{}'.", ldapUser);
                }

                if (ldapUser == null) {
                    return null;
                } else {
                    //found a user from ldap so cache them and return
                    try {
                        final String primaryGroupSID = getPrimaryGroupSID(ldapUser);
                        final String primaryGroup = findGroupBySID(ctx, primaryGroupSID);
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("LDAP search for primary group by SID '{}', found '{}'.", primaryGroupSID, primaryGroup);
                        }
                        if (primaryGroup == null) {
                            //or exception?
                            return null;
                        }
                        return createAccountInDatabase(ctx, name, ldapUser, ensureCase(primaryGroup));
                        //registerAccount(acct); //TODO do we need this
                    } catch (final AuthenticationException ae) {
                        LOG.error(ae.getMessage(), ae);
                        return null;
                    }
                }
            } catch (final NamingException ne) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(ne.getMessage(), ne);
                }
                //LOG.error(new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, ne.getMessage()));
                return null;
            }
        }
    }

    @Override
    public boolean hasAccount(final String name) {
        return getAccount(name) != null;
    }

    /**
     * The binary data is in form:
     * byte[0] - revision level
     * byte[1] - count of sub-authorities
     * byte[2-7] - 48 bit authority (big-endian)
     * and then count x 32 bit sub authorities (little-endian)
     *
     * The String value is: S-Revision-Authority-SubAuthority[n]...
     *
     * http://forums.oracle.com/forums/thread.jspa?threadID=1155740&tstart=0
     */
    private static String decodeSID(final byte[] sid) {

        final StringBuilder strSid = new StringBuilder("S-");

        // get version
        final int revision = sid[0];
        strSid.append(revision);

        //next byte is the count of sub-authorities
        final int countSubAuths = sid[1] & 0xFF;

        //get the authority
        long authority = 0;
        //String rid = "";
        for (int i = 2; i <= 7; i++) {
            authority |= ((long) sid[i]) << (8 * (5 - (i - 2)));
        }
        strSid.append("-");
        strSid.append(Long.toHexString(authority));

        //iterate all the sub-auths
        int offset = 8;
        int size = 4; //4 bytes for each sub auth
        for (int j = 0; j < countSubAuths; j++) {
            long subAuthority = 0;
            for (int k = 0; k < size; k++) {
                subAuthority |= (long) (sid[offset + k] & 0xFF) << (8 * k);
            }

            strSid.append("-");
            strSid.append(subAuthority);

            offset += size;
        }

        return strSid.toString();
    }

    private String getPrimaryGroupSID(final SearchResult ldapUser) throws NamingException {
        final LDAPSearchContext search = ensureContextFactory().getSearch();

        final Object objSID = ldapUser.getAttributes().get(search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.OBJECT_SID)).get();
        final String strObjectSid;
        if (objSID instanceof String) {
            strObjectSid = objSID.toString();
        } else {
            strObjectSid = decodeSID((byte[]) objSID);
        }

        final String strPrimaryGroupID = (String) ldapUser.getAttributes().get(search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.PRIMARY_GROUP_ID)).get();

        return strObjectSid.substring(0, strObjectSid.lastIndexOf('-') + 1) + strPrimaryGroupID;
    }

    public final synchronized Group getGroup(final Subject invokingUser, final DBBroker broker, String name) {
        name = ensureCase(name);

        final Group grp = getGroup(name);
        if (grp != null) {
            return grp;
        } else {
            //if the group is not cached, we should try and find it in LDAP and cache it if it exists
            LdapContext ctx = null;
            try {
                ctx = getContext(invokingUser);

                return getGroup(ctx, broker, name);
            } catch (final NamingException ne) {
                LOG.error(new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, ne.getMessage()));
                return null;
            } finally {
                if (ctx != null) {
                    LdapUtils.closeContext(ctx);
                }
            }
        }
    }

    private synchronized Group getGroup(final LdapContext ctx, final DBBroker broker, final String name) {
        if (name == null) {
            return null;
        }

        final String gName = ensureCase(name);

        final Group grp = getGroup(gName);
        if (grp != null) {
            return grp;
        } else {
            //if the group is not cached, we should try and find it in LDAP and cache it if it exists
            try {
                //do the lookup
                final SearchResult ldapGroup = findGroupByGroupName(ctx, removeDomainPostfix(gName));
                if (ldapGroup == null) {
                    return null;
                } else {
                    //found a group from ldap so cache them and return
                    try {
                        return createGroupInDatabase(broker, gName);
                        //registerGroup(grp); //TODO do we need to do this?
                    } catch (final AuthenticationException ae) {
                        LOG.error(ae.getMessage(), ae);
                        return null;
                    }
                }
            } catch (final NamingException ne) {
                LOG.error(new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, ne.getMessage()));
                return null;
            }
        }
    }

    @Override
    public boolean hasGroup(final String name) {
        return getGroup((Subject)null, getSecurityManager().getDatabase().getActiveBroker(), name) != null;
    }

    private String addDomainPostfix(final String principalName) {
        String name = principalName;
        if (!name.contains("@")) {
            name += '@' + ensureContextFactory().getDomain();
        }
        return name;
    }

    private String removeDomainPostfix(final String principalName) {
        String name = principalName;
        if (name.contains("@") && name.endsWith(ensureContextFactory().getDomain())) {
            name = name.substring(0, name.indexOf('@'));
        }
        return name;
    }

    private boolean checkAccountRestrictionList(final String accountname) {
        final LDAPSearchContext search = ensureContextFactory().getSearch();
        return checkPrincipalRestrictionList(accountname, search.getSearchAccount());
    }

    private boolean checkGroupRestrictionList(final String groupname) {
        final LDAPSearchContext search = ensureContextFactory().getSearch();
        return checkPrincipalRestrictionList(groupname, search.getSearchGroup());
    }

    private boolean checkPrincipalRestrictionList(final String principalName, final AbstractLDAPSearchPrincipal searchPrinciple) {

        String name = ensureCase(principalName);

        if (name.indexOf('@') > -1) {
            name = name.substring(0, name.indexOf('@'));
        }

        List<String> blackList = null;
        if (searchPrinciple.getBlackList() != null) {
            blackList = searchPrinciple.getBlackList().getPrincipals();
        }

        List<String> whiteList = null;
        if (searchPrinciple.getWhiteList() != null) {
            whiteList = searchPrinciple.getWhiteList().getPrincipals();
        }

        if (blackList != null) {
            for (String blackEntry : blackList) {
                if (ensureCase(blackEntry).equals(name)) {
                    return false;
                }
            }
        }

        if (whiteList != null && !whiteList.isEmpty()) {
            for (String whiteEntry : whiteList) {
                if (ensureCase(whiteEntry).equals(name)) {
                    return true;
                }
            }
            return false;
        }

        return true;
    }

    /**
     * Escapes '\', '(', and ')' characters.
     *
     * @param searchAttribute The search attribute string.
     *
     * @return the escaped search attribute.
     */
    private String escapeSearchAttribute(final String searchAttribute) {
        return searchAttribute
                .replace("\\", "\\5c")
                .replace("(", "\\28")
                .replace(")", "\\29");
    }

    private SearchResult findAccountByAccountName(final DirContext ctx, final String accountName) throws NamingException {

        if (!checkAccountRestrictionList(accountName)) {
            return null;
        }

        final String userName = escapeSearchAttribute(removeDomainPostfix(accountName));

        final LDAPSearchContext search = ensureContextFactory().getSearch();
        final SearchAttribute sa = new SearchAttribute(search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.NAME), userName);
        final String searchFilter = buildSearchFilter(search.getSearchAccount().getSearchFilterPrefix(), sa);

        final SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        final NamingEnumeration<SearchResult> results = ctx.search(search.getBase(), searchFilter, searchControls);

        SearchResult searchResult = null;
        if (results.hasMoreElements()) {
            searchResult = results.nextElement();

            //make sure there is not another item available, there should be only 1 match
            if (results.hasMoreElements()) {
                LOG.error("Matched multiple users for the accountName: {}", accountName);
            }
        }

        return searchResult;
    }

    private String findGroupBySID(final DirContext ctx, final String sid) throws NamingException {

        final LDAPSearchContext search = ensureContextFactory().getSearch();
        final SearchAttribute sa = new SearchAttribute(search.getSearchGroup().getSearchAttribute(LDAPSearchAttributeKey.OBJECT_SID), sid);
        final String searchFilter = buildSearchFilter(search.getSearchGroup().getSearchFilterPrefix(), sa);

        final SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        final NamingEnumeration<SearchResult> results = ctx.search(search.getAbsoluteBase(), searchFilter, searchControls);

        if (results.hasMoreElements()) {
            SearchResult searchResult = results.nextElement();

            //make sure there is not another item available, there should be only 1 match
            if (results.hasMoreElements()) {
                LOG.error("Matched multiple groups for the group with SID: {}", sid);
                return null;
            } else {
                return addDomainPostfix((String) searchResult.getAttributes().get(search.getSearchGroup().getSearchAttribute(LDAPSearchAttributeKey.NAME)).get());
            }
        }
        LOG.error("Matched no group with SID: {}", sid);
        return null;
    }

    private @Nullable SearchResult findGroupByGroupName(final DirContext ctx, final String groupName) throws NamingException {

        if (!checkGroupRestrictionList(groupName)) {
            return null;
        }

        final LDAPSearchContext search = ensureContextFactory().getSearch();
        final SearchAttribute sa = new SearchAttribute(search.getSearchGroup().getSearchAttribute(LDAPSearchAttributeKey.NAME), escapeSearchAttribute(groupName));
        final String searchFilter = buildSearchFilter(search.getSearchGroup().getSearchFilterPrefix(), sa);

        final SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        final NamingEnumeration<SearchResult> results = ctx.search(search.getAbsoluteBase(), searchFilter, searchControls);

        if (results.hasMoreElements()) {
            final SearchResult searchResult = results.nextElement();

            //make sure there is not another item available, there should be only 1 match
            if (results.hasMoreElements()) {
                LOG.error("Matched multiple groups for the groupName: {}", groupName);
                return null;
            } else {
                return searchResult;
            }
        }
        LOG.error("Matched no groups for the groupName: {}", groupName);
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
    public boolean updateAccount(final Account account) throws PermissionDeniedException, EXistException {
        return super.updateAccount(account);
    }

    @Override
    public boolean deleteAccount(final Account account) {
        // TODO we dont support writting to LDAP
        //XXX: delete local cache?
        return false;
    }

    @Override
    public boolean updateGroup(final Group group) throws PermissionDeniedException, EXistException {
        return super.updateGroup(group);
    }

    @Override
    public boolean deleteGroup(final Group group) {
        //XXX: delete local cache?
        return false;
    }

    private class SearchAttribute {
        private final String name;
        private final String value;

        SearchAttribute(final String name, final String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }
    }

    private String buildSearchFilter(final String searchPrefix, final SearchAttribute sa) {

        final StringBuilder builder = new StringBuilder();
        builder.append("(");
        builder.append(buildSearchCriteria(searchPrefix));

        if (sa.getName() != null && sa.getValue() != null) {
            builder.append("(");
            builder.append(sa.getName());
            builder.append("=");
            builder.append(sa.getValue());
            builder.append(")");
        }
        builder.append(")");
        return builder.toString();
    }

    private String buildSearchFilterUnion(final String searchPrefix, final List<SearchAttribute> searchAttributes) {

        final StringBuilder builder = new StringBuilder();
        builder.append("(");
        builder.append(buildSearchCriteria(searchPrefix));

        if (!searchAttributes.isEmpty()) {
            builder.append("(|");

            for (final SearchAttribute sa : searchAttributes) {
                builder.append("(");
                builder.append(sa.getName());
                builder.append("=");
                builder.append(sa.getValue());
                builder.append(")");
            }

            builder.append(")");
        }

        builder.append(")");
        return builder.toString();
    }

    private String buildSearchCriteria(final String searchPrefix) {
        return "&(" + searchPrefix + ")";
    }

    @Override
    public List<String> findUsernamesWhereNameStarts(String startsWith) {

        startsWith = escapeSearchAttribute(ensureCase(startsWith));

        final List<String> usernames = new ArrayList<>();

        LdapContext ctx = null;
        try {
            ctx = getContext(getSecurityManager().getCurrentSubject());

            final LDAPSearchContext search = ensureContextFactory().getSearch();
            final SearchAttribute sa = new SearchAttribute(search.getSearchAccount().getMetadataSearchAttribute(AXSchemaType.FULLNAME), startsWith + "*");
            final String searchFilter = buildSearchFilter(search.getSearchAccount().getSearchFilterPrefix(), sa);

            final SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            searchControls.setReturningAttributes(new String[]{search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.NAME)});

            final NamingEnumeration<SearchResult> results = ctx.search(search.getBase(), searchFilter, searchControls);

            while (results.hasMoreElements()) {
                final SearchResult searchResult = results.nextElement();
                final String username = ensureCase(addDomainPostfix((String) searchResult.getAttributes().get(search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.NAME)).get()));
                if (checkAccountRestrictionList(username)) {
                    usernames.add(username);
                }
            }
        } catch (final NamingException ne) {
            LOG.error(new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, ne.getMessage()));
        } finally {
            if (ctx != null) {
                LdapUtils.closeContext(ctx);
            }
        }

        return usernames;
    }

    @Override
    public List<String> findUsernamesWhereNamePartStarts(final String startsWith) {

        final String sWith = escapeSearchAttribute(ensureCase(startsWith));

        final List<String> usernames = new ArrayList<>();

        LdapContext ctx = null;
        try {
            ctx = getContext(getSecurityManager().getCurrentSubject());

            final LDAPSearchContext search = ensureContextFactory().getSearch();

            final SearchAttribute firstNameSa = new SearchAttribute(search.getSearchAccount().getMetadataSearchAttribute(AXSchemaType.FIRSTNAME), sWith + "*");
            final SearchAttribute lastNameSa = new SearchAttribute(search.getSearchAccount().getMetadataSearchAttribute(AXSchemaType.LASTNAME), sWith + "*");
            final List<SearchAttribute> sas = new ArrayList<>();
            sas.add(firstNameSa);
            sas.add(lastNameSa);

            final String searchFilter = buildSearchFilterUnion(search.getSearchAccount().getSearchFilterPrefix(), sas);

            final SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            searchControls.setReturningAttributes(new String[]{search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.NAME)});

            final NamingEnumeration<SearchResult> results = ctx.search(search.getBase(), searchFilter, searchControls);

            while (results.hasMoreElements()) {
                final SearchResult searchResult = results.nextElement();
                final String username = ensureCase(addDomainPostfix((String) searchResult.getAttributes().get(search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.NAME)).get()));
                if (checkAccountRestrictionList(username)) {
                    usernames.add(username);
                }
            }
        } catch (final NamingException ne) {
            LOG.error(new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, ne.getMessage()));
        } finally {
            if (ctx != null) {
                LdapUtils.closeContext(ctx);
            }
        }

        return usernames;
    }

    @Override
    public List<String> findUsernamesWhereUsernameStarts(final String startsWith) {

        final String sWith = escapeSearchAttribute(ensureCase(startsWith));

        final List<String> usernames = new ArrayList<>();

        LdapContext ctx = null;
        try {
            ctx = getContext(getSecurityManager().getCurrentSubject());

            final LDAPSearchContext search = ensureContextFactory().getSearch();
            final SearchAttribute sa = new SearchAttribute(search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.NAME), sWith + "*");
            final String searchFilter = buildSearchFilter(search.getSearchAccount().getSearchFilterPrefix(), sa);

            final SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            searchControls.setReturningAttributes(new String[]{search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.NAME)});

            final NamingEnumeration<SearchResult> results = ctx.search(search.getBase(), searchFilter, searchControls);

            while (results.hasMoreElements()) {
                final SearchResult searchResult = results.nextElement();
                final String username = ensureCase(addDomainPostfix((String) searchResult.getAttributes().get(search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.NAME)).get()));

                if (checkAccountRestrictionList(username)) {
                    usernames.add(username);
                }
            }
        } catch (final NamingException ne) {
            LOG.error(new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, ne.getMessage()));
        } finally {
            if (ctx != null) {
                LdapUtils.closeContext(ctx);
            }
        }

        return usernames;
    }


    private List<String> findGroupnamesForUserDistinguishedName(final LdapContext ctx, final String userDistinguishedName) {
        final List<String> groupnames = new ArrayList<>();

        try {
            final LDAPSearchContext search = ensureContextFactory().getSearch();
            final SearchAttribute sa = new SearchAttribute(search.getSearchGroup().getSearchAttribute(LDAPSearchAttributeKey.MEMBER), escapeSearchAttribute(userDistinguishedName));
            final String searchFilter = buildSearchFilter(search.getSearchGroup().getSearchFilterPrefix(), sa);

            final SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            searchControls.setReturningAttributes(new String[]{search.getSearchGroup().getSearchAttribute(LDAPSearchAttributeKey.NAME)});


            final NamingEnumeration<SearchResult> results = ctx.search(search.getAbsoluteBase(), searchFilter, searchControls);

            while (results.hasMoreElements()) {
                final SearchResult searchResult = results.nextElement();
                final String groupname = ensureCase(addDomainPostfix((String) searchResult.getAttributes().get(search.getSearchGroup().getSearchAttribute(LDAPSearchAttributeKey.NAME)).get()));
                if (checkGroupRestrictionList(groupname)) {
                    groupnames.add(groupname);
                }
            }
        } catch (final NamingException ne) {
            LOG.error(new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, ne.getMessage()));
        }

        return groupnames;
    }

    @Override
    public List<String> findGroupnamesWhereGroupnameStarts(final String startsWith) {

        final String sWith = escapeSearchAttribute(ensureCase(startsWith));

        final List<String> groupnames = new ArrayList<>();

        LdapContext ctx = null;
        try {
            ctx = getContext(getSecurityManager().getCurrentSubject());

            final LDAPSearchContext search = ensureContextFactory().getSearch();
            final SearchAttribute sa = new SearchAttribute(search.getSearchGroup().getSearchAttribute(LDAPSearchAttributeKey.NAME), sWith + "*");
            final String searchFilter = buildSearchFilter(search.getSearchGroup().getSearchFilterPrefix(), sa);

            final SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            searchControls.setReturningAttributes(new String[]{search.getSearchGroup().getSearchAttribute(LDAPSearchAttributeKey.NAME)});

            final NamingEnumeration<SearchResult> results = ctx.search(search.getBase(), searchFilter, searchControls);

            while (results.hasMoreElements()) {
                final SearchResult searchResult = results.nextElement();
                final String groupname = ensureCase(addDomainPostfix((String) searchResult.getAttributes().get(search.getSearchGroup().getSearchAttribute(LDAPSearchAttributeKey.NAME)).get()));
                if (checkGroupRestrictionList(groupname)) {
                    groupnames.add(groupname);
                }
            }
        } catch (final NamingException ne) {
            LOG.error(new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, ne.getMessage()));
        } finally {
            if (ctx != null) {
                LdapUtils.closeContext(ctx);
            }
        }

        return groupnames;
    }

    @Override
    public List<String> findGroupnamesWhereGroupnameContains(final String fragment) {

        final String part = escapeSearchAttribute(ensureCase(fragment));

        final List<String> groupnames = new ArrayList<>();

        LdapContext ctx = null;
        try {
            ctx = getContext(getSecurityManager().getCurrentSubject());

            final LDAPSearchContext search = ensureContextFactory().getSearch();
            final SearchAttribute sa = new SearchAttribute(search.getSearchGroup().getSearchAttribute(LDAPSearchAttributeKey.NAME), "*" + part + "*");
            final String searchFilter = buildSearchFilter(search.getSearchGroup().getSearchFilterPrefix(), sa);

            final SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            searchControls.setReturningAttributes(new String[]{search.getSearchGroup().getSearchAttribute(LDAPSearchAttributeKey.NAME)});


            final NamingEnumeration<SearchResult> results = ctx.search(search.getBase(), searchFilter, searchControls);

            while (results.hasMoreElements()) {
                final SearchResult searchResult = results.nextElement();
                final String groupname = ensureCase(addDomainPostfix((String) searchResult.getAttributes().get(search.getSearchGroup().getSearchAttribute(LDAPSearchAttributeKey.NAME)).get()));
                if (checkGroupRestrictionList(groupname)) {
                    groupnames.add(groupname);
                }
            }
        } catch (final NamingException ne) {
            LOG.error(ne);
        } finally {
            if (ctx != null) {
                LdapUtils.closeContext(ctx);
            }
        }

        return groupnames;
    }

    @Override
    public List<String> findAllGroupNames() {
        final List<String> groupnames = new ArrayList<>();

        LdapContext ctx = null;
        try {
            ctx = getContext(getSecurityManager().getCurrentSubject());

            final LDAPSearchContext search = ensureContextFactory().getSearch();
            final SearchAttribute sa = new SearchAttribute(null, null);
            final String searchFilter = buildSearchFilter(search.getSearchGroup().getSearchFilterPrefix(), sa);

            final SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            searchControls.setReturningAttributes(new String[]{search.getSearchGroup().getSearchAttribute(LDAPSearchAttributeKey.NAME)});

            final NamingEnumeration<SearchResult> results = ctx.search(search.getBase(), searchFilter, searchControls);

            while (results.hasMoreElements()) {
                final SearchResult searchResult = results.nextElement();
                final String groupname = ensureCase(addDomainPostfix((String) searchResult.getAttributes().get(search.getSearchGroup().getSearchAttribute(LDAPSearchAttributeKey.NAME)).get()));
                if (checkGroupRestrictionList(groupname)) {
                    groupnames.add(groupname);
                }
            }
        } catch (final NamingException ne) {
            LOG.error(new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, ne.getMessage()));
        } finally {
            if (ctx != null) {
                LdapUtils.closeContext(ctx);
            }
        }

        return groupnames;
    }

    @Override
    public List<String> findAllUserNames() {
        final List<String> usernames = new ArrayList<>();

        LdapContext ctx = null;
        try {
            ctx = getContext(getSecurityManager().getCurrentSubject());

            final LDAPSearchContext search = ensureContextFactory().getSearch();
            final SearchAttribute sa = new SearchAttribute(null, null);
            final String searchFilter = buildSearchFilter(search.getSearchAccount().getSearchFilterPrefix(), sa);

            final SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            searchControls.setReturningAttributes(new String[]{search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.NAME)});

            final NamingEnumeration<SearchResult> results = ctx.search(search.getBase(), searchFilter, searchControls);

            while (results.hasMoreElements()) {
                final SearchResult searchResult = results.nextElement();
                final String accountname = ensureCase(addDomainPostfix((String) searchResult.getAttributes().get(search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.NAME)).get()));
                if (checkAccountRestrictionList(accountname)) {
                    usernames.add(accountname);
                }
            }
        } catch (final NamingException ne) {
            LOG.error(new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, ne.getMessage()));
        } finally {
            if (ctx != null) {
                LdapUtils.closeContext(ctx);
            }
        }

        return usernames;
    }

    @Override
    public List<String> findAllGroupMembers(final String groupName) {

        final String name = escapeSearchAttribute(ensureCase(groupName));

        final List<String> groupMembers = new ArrayList<>();

        if (!checkGroupRestrictionList(name)) {
            return groupMembers;
        }

        LdapContext ctx = null;
        try {
            ctx = getContext(getSecurityManager().getCurrentSubject());

            //find the dn of the group
            SearchResult searchResult = findGroupByGroupName(ctx, removeDomainPostfix(name));
            if (searchResult == null) {
                // no such group
                return groupMembers;
            }

            final LDAPSearchContext search = ensureContextFactory().getSearch();
            final String dnGroup = (String) searchResult.getAttributes().get(search.getSearchGroup().getSearchAttribute(LDAPSearchAttributeKey.DN)).get();

            //find all accounts that are a member of the group
            final SearchAttribute sa = new SearchAttribute(search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.MEMBER_OF), escapeSearchAttribute(dnGroup));
            final String searchFilter = buildSearchFilter(search.getSearchAccount().getSearchFilterPrefix(), sa);
            final SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            searchControls.setReturningAttributes(new String[]{search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.NAME)});

            final NamingEnumeration<SearchResult> results = ctx.search(search.getBase(), searchFilter, searchControls);

            while (results.hasMoreElements()) {
                searchResult = results.nextElement();
                final String member = ensureCase(addDomainPostfix((String) searchResult.getAttributes().get(search.getSearchAccount().getSearchAttribute(LDAPSearchAttributeKey.NAME)).get()));
                if (checkAccountRestrictionList(member)) {
                    groupMembers.add(member);
                }
            }

        } catch (final NamingException ne) {
            LOG.error(new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, ne.getMessage()));
        } finally {
            if (ctx != null) {
                LdapUtils.closeContext(ctx);
            }
        }

        return groupMembers;
    }

    private final class AuthenticatedLdapSubjectAccreditedImpl extends SubjectAccreditedImpl {

        private final String authenticatedCredentials;

        private AuthenticatedLdapSubjectAccreditedImpl(final AbstractAccount account, final LdapContext ctx, final String authenticatedCredentials) {
            super(account, ctx);
            this.authenticatedCredentials = authenticatedCredentials;
        }

        private String getAuthenticatedCredentials() {
            return authenticatedCredentials;
        }
    }
}
