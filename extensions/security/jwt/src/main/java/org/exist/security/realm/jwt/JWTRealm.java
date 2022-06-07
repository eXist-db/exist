/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2021 The eXist-db Authors
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
package org.exist.security.realm.jwt;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.annotation.*;
import org.exist.security.*;
import org.exist.security.internal.SecurityManagerImpl;
import org.exist.security.internal.SubjectAccreditedImpl;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;

import java.util.*;
import java.util.regex.Pattern;

/**
 * @author <a href="mailto:loren.cahlander@gmail.com">Loren Cahlander</a>
 *
 */
@ConfigurationClass("realm") //TODO: id = JWT
public class JWTRealm extends AbstractRealm {

    /**
     *
     */
    private static final Logger LOG = LogManager.getLogger(JWTRealm.class);
    /**
     *
     */
    private static JWTRealm instance = null;

    /**
     *
     */
    @ConfigurationFieldAsAttribute("id")
    public final static String ID = "JWT";

    /**
     *
     */
    @ConfigurationFieldAsAttribute("version")
    public static final String VERSION = "1.0";

    /**
     *
     */
    @ConfigurationFieldAsElement("context")
    protected JWTContextFactory jwtContextFactory;

    /**
     *
     * @param sm
     * @param config
     */
    public JWTRealm(final SecurityManagerImpl sm, final Configuration config) {
        super(sm, config);
        instance = this;
    }

    /**
     *
     * @return
     */
    protected JWTContextFactory ensureContextFactory() {
        if (this.jwtContextFactory == null) {
            LOG.info("No JWTContextFactory specified - creating a default instance.");
            this.jwtContextFactory = new JWTContextFactory(configuration);
        }
        return this.jwtContextFactory;
    }

    /**
     *
     * @return
     */
    public static JWTRealm getInstance() {
        return instance;
    }

    /**
     *
     * @return
     */
    @Override
    public String getId() {
        return ID;
    }

    /**
     *
     * @return
     */
    public String getVersion() { return VERSION; }

    /**
     *
     * @param broker
     * @param transaction
     * @throws EXistException
     */
    @Override
    public void start(final DBBroker broker, final Txn transaction) throws EXistException {
        super.start(broker, transaction);
    }


    /**
     *
     * @param account
     * @return
     * @throws PermissionDeniedException
     * @throws EXistException
     * @throws ConfigurationException
     */
    @Override
    public boolean deleteAccount(Account account) throws PermissionDeniedException, EXistException, ConfigurationException {
        return false;
    }

    /**
     *
     * @param group
     * @return
     * @throws PermissionDeniedException
     * @throws EXistException
     * @throws ConfigurationException
     */
    @Override
    public boolean deleteGroup(Group group) throws PermissionDeniedException, EXistException, ConfigurationException {
        return false;
    }

    /**
     *
     * @param accountName
     * @param credentials
     * @return
     * @throws AuthenticationException
     */
    @Override
    public Subject authenticate(String accountName, Object credentials) throws AuthenticationException {
        final String jwt = deserialize(accountName);
        LOG.info("JWT = [" + jwt + "]");
        ReadContext ctx = JsonPath.parse(jwt);
        final AbstractAccount account = (AbstractAccount) getAccount(ctx);
        return new AuthenticatedJWTSubjectAccreditedImpl (account, ctx, String.valueOf(credentials));
    }

    /**
     *
     * @param tokenString
     * @return
     */
    public String deserialize(String tokenString) {
        String[] pieces = splitTokenString(tokenString);
        String jwtPayloadSegment = pieces[1];
        return StringUtils.newStringUtf8(Base64.decodeBase64(jwtPayloadSegment));
    }

    /**
     * @param tokenString The original encoded representation of a JWT
     * @return Three components of the JWT as an array of strings
     */
    private String[] splitTokenString(String tokenString) {
        String[] pieces = tokenString.split(Pattern.quote("."));
        if (pieces.length != 3) {
            throw new IllegalStateException("Expected JWT to have 3 segments separated by '"
                    + "." + "', but it has " + pieces.length + " segments");
        }
        return pieces;
    }

    /**
     *
     * @param ctx
     * @return
     */
    public final synchronized Account getAccount(ReadContext ctx) {

        final JWTContextFactory jwtContextFactory = ensureContextFactory();
        final JWTSearchContext search = jwtContextFactory.getSearchContext();
        LOG.info("search: " + search.toString());
        final JWTSearchAccount searchAccount = search.getSearchAccount();
        LOG.info("searchAccount: " + searchAccount.toString());
        final String identifierFieldPath = searchAccount.getIdentifier();
        LOG.info("identifierFieldPath [" + identifierFieldPath + "]");

        //first attempt to get the cached account
        Account acct = null;

        try {
            final String name = ctx.read(identifierFieldPath);
            LOG.info("name = " + name);
            acct = super.getAccount(name);
            final DBBroker broker = getDatabase().get(Optional.of(getSecurityManager().getSystemSubject()));

            if (acct != null) {
                LOG.info("Cached used.");
                LOG.info("account = " + acct.toString() + " " + acct.getName() + " " + acct.getRealmId());
                updateGroupsInDatabase(broker, ctx, acct);
            } else {
                LOG.info("Creating Account");
                acct = createAccountInDatabase(broker, ctx, name);
            }
        } catch (Exception e) {
            LOG.info(e.getMessage(), e);
        }
        LOG.info("account = " + acct.toString() + " " + acct.getName() + " " + acct.getRealmId());
        return acct;
    }

    /**
     *
     * @param ctx
     * @param broker
     * @return
     * @throws AuthenticationException
     */
    private List<Group> getGroupMembershipForJWTUser(final ReadContext ctx, final DBBroker broker) throws AuthenticationException {
        final List<Group> memberOf_groups = new ArrayList<>();

        final JWTSearchContext searchContext = ensureContextFactory().getSearchContext();
        final JWTSearchGroup groupContext = searchContext.getSearchGroup();

        if (groupContext != null) {
            String basePathPath = groupContext.getBasePath();
            String identifierPath = groupContext.getIdentifier();

            if (basePathPath != null) {
                try {
                    ctx.read(basePathPath);
                } catch(Exception e) {
                    LOG.error("Unable to get groups", e);
                }
            } else if(identifierPath != null) {
                List<String> groupNames = null;

                try {
                    groupNames = ctx.read(identifierPath);
                    for (final String groupName: groupNames) {
                        memberOf_groups.add(getGroup(ctx, broker, groupName));
                    }
                } catch(Exception e) {
                    LOG.error("Unable to get groups", e);
                }
            }
        }

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

    /**
     *
     * @param ctx
     * @param broker
     * @param groupName
     * @return
     * @throws AuthenticationException
     */
    private Group getGroup(ReadContext ctx, DBBroker broker, String groupName) throws AuthenticationException {
        final Group grp = getGroup(groupName);
        if (grp != null) {
            return grp;
        } else {
            return createGroupInDatabase(broker, groupName);
        }
    }

    /**
     *
     * @param broker
     * @param decodedJWT
     * @param acct
     * @throws PermissionDeniedException
     * @throws EXistException
     */
    private void updateGroupsInDatabase(DBBroker broker, ReadContext decodedJWT, Account acct) throws PermissionDeniedException, EXistException {
//        final String claim = this.jwtContextFactory.getGroup().getClaim();
//        final List<String> dbaList = this.jwtContextFactory.getGroup().getDbaList().getPrincipals();
//        final List<String> groupNames = ((ArrayList) decodedJWT.get(claim));
//        final String[] acctGroups = acct.getGroups();
//
//        for (final String accountGroup : acctGroups) {
//            if (!groupNames.contains(accountGroup)) {
//                acct.remGroup(accountGroup);
//            }
//        }
//
//        for (final String groupName : groupNames) {
//            if (acct.hasGroup(groupName)) {
//                continue;
//            }
//            if (dbaList.contains(groupName)) {
//                if (!acct.hasDbaRole()) {
//                    acct.addGroup(getSecurityManager().getDBAGroup());
//                }
//            }
//            final Group group = super.getGroup(groupName);
//
//            if (group != null) {
//                acct.addGroup(group);
//            } else {
//                final GroupAider groupAider = new GroupAider(ID, groupName);
//                final Group newGroup = getSecurityManager().addGroup(broker, groupAider);
//                acct.addGroup(newGroup);
//            }
//        }
//
    }

    /**
     *
     * @param broker
     * @param ctx
     * @param name
     * @return
     * @throws PermissionDeniedException
     * @throws EXistException
     * @throws AuthenticationException
     */
    private Account createAccountInDatabase(DBBroker broker, ReadContext ctx, String name) throws PermissionDeniedException, EXistException, AuthenticationException {

        final UserAider userAider = new UserAider(ID, name);

        final JWTSearchContext searchContext = ensureContextFactory().getSearchContext();
        final JWTSearchAccount searchAccount = searchContext.getSearchAccount();

        // store any requested metadata
        setMetadataValue(userAider, ctx, "http://axschema.org/contact/country/home", searchAccount.getCountry());
        setMetadataValue(userAider, ctx, "http://exist-db.org/security/description", searchAccount.getDescription());
        setMetadataValue(userAider, ctx, "http://axschema.org/contact/email", searchAccount.getEmail());
        setMetadataValue(userAider, ctx, "http://axschema.org/namePerson/first", searchAccount.getFirstname());
        setMetadataValue(userAider, ctx, "http://axschema.org/namePerson/friendly", searchAccount.getFriendly());
        setMetadataValue(userAider, ctx, "http://axschema.org/pref/language", searchAccount.getLanguage());
        setMetadataValue(userAider, ctx, "http://axschema.org/namePerson/last", searchAccount.getLastname());
        setMetadataValue(userAider, ctx, "http://axschema.org/namePerson", searchAccount.getName());
        setMetadataValue(userAider, ctx, "http://axschema.org/pref/timezone", searchAccount.getTimezone());

        // Add the member groups
        for (final Group memberOf_group : getGroupMembershipForJWTUser(ctx, broker)) {
            LOG.info("Adding group [" + memberOf_group.getName() + "] from realm [" + memberOf_group.getRealmId() + "] added to user [" + name + "]");
            userAider.addGroup(memberOf_group);
        }

        LOG.info("Creating user");
        final Account account = getSecurityManager().addAccount(userAider);
        LOG.info("User created: " + account.getName() + " " + account.getRealmId());

        return account;
    }

    private void setMetadataValue(UserAider userAider, ReadContext ctx, String axschematype, String valuePath) {
        if (valuePath != null) {

            LOG.info("metadata valuePath for " + axschematype + " = " + valuePath);
            String value;

            try {
                value = ctx.read(valuePath);
            } catch(Exception e) {
                value = null;
            }
            LOG.info("value = " + value);

            if (value != null) {
                AXSchemaType schemaType = AXSchemaType.valueOfNamespace(axschematype);

                if (schemaType != null) {
                    userAider.setMetadataValue(schemaType, value);
                }
            }
        }
    }

    /**
     *
     * @param ctx
     * @return
     */
    /**
     *
     * @param broker
     * @param groupname
     * @return
     * @throws AuthenticationException
     */
    private Group createGroupInDatabase(final DBBroker broker, final String groupname) throws AuthenticationException {
        try {
            return getSecurityManager().addGroup(broker, new GroupAider(ID, groupname));

        } catch (Exception e) {
            throw new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, e.getMessage(), e);
        }
    }

    /**
     *
     * @param principalName
     * @return
     */
    private String addDomainPostfix(final String principalName) {
        String name = principalName;
        if (!name.contains("@")) {
            name += '@' + ensureContextFactory().getDomain();
        }
        return name;
    }

    /**
     *
     * @param principalName
     * @return
     */
    private String removeDomainPostfix(final String principalName) {
        String name = principalName;
        if (name.contains("@") && name.endsWith(ensureContextFactory().getDomain())) {
            name = name.substring(0, name.indexOf('@'));
        }
        return name;
    }


    /**
     *
     */
    private final class AuthenticatedJWTSubjectAccreditedImpl extends SubjectAccreditedImpl {

        /**
         *
         */
        private final String authenticatedCredentials;

        /**
         *
         * @param account
         * @param jwt
         * @param authenticatedCredentials
         */
        private AuthenticatedJWTSubjectAccreditedImpl(final AbstractAccount account, final ReadContext jwt, final String authenticatedCredentials) {
            super(account, jwt);
            this.authenticatedCredentials = authenticatedCredentials;
        }

        /**
         *
         * @return
         */
        private String getAuthenticatedCredentials() {
            return authenticatedCredentials;
        }
    }

}
