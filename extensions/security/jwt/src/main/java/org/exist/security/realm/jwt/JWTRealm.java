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
import org.exist.security.realm.jwt.AbstractJWTSearchPrincipal.JWTSearchAttributeKey;
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

    private static final Logger LOG = LogManager.getLogger(JWTRealm.class);
    private static JWTRealm instance = null;

    @ConfigurationFieldAsAttribute("id")
    public final static String ID = "JWT";

    @ConfigurationFieldAsAttribute("version")
    public static final String VERSION = "1.0";

    @ConfigurationFieldAsElement("context")
    protected JWTContextFactory jwtContextFactory;

    public JWTRealm(final SecurityManagerImpl sm, final Configuration config) {
        super(sm, config);
        instance = this;
    }

    protected JWTContextFactory ensureContextFactory() {
        if (this.jwtContextFactory == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No JWTContextFactory specified - creating a default instance.");
            }
            this.jwtContextFactory = new JWTContextFactory(configuration);
        }
        return this.jwtContextFactory;
    }

    public static JWTRealm getInstance() { return instance; }

    @Override
    public String getId() {
        return ID;
    }

    public String getVersion() { return VERSION; }

    @Override
    public void start(final DBBroker broker, final Txn transaction) throws EXistException {
        super.start(broker, transaction);
    }


    @Override
    public boolean deleteAccount(Account account) throws PermissionDeniedException, EXistException, ConfigurationException {
        return false;
    }

    @Override
    public boolean deleteGroup(Group group) throws PermissionDeniedException, EXistException, ConfigurationException {
        return false;
    }

    @Override
    public Subject authenticate(String accountName, Object credentials) throws AuthenticationException {
        final String jwt = deserialize(accountName);
        LOG.info("JWT = " + jwt);
        ReadContext ctx = JsonPath.parse(jwt);
        final AbstractAccount account = (AbstractAccount) getAccount(ctx);
        return new AuthenticatedJWTSubjectAccreditedImpl (account, ctx, String.valueOf(credentials));
    }
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
    public final synchronized Account getAccount(ReadContext ctx) {

        final JWTContextFactory jwtContextFactory = ensureContextFactory();
        LOG.info("JWTContextFactory " + jwtContextFactory.toString());
        final JWTSearchContext search = jwtContextFactory.getSearchContext();
        LOG.info("JWTSearchContext " + search.toString());
        final JWTSearchAccount searchAccount = search.getSearchAccount();
        LOG.info("JWTSearchAccount " + searchAccount.toString());
        JWTSearchAttributeKey key = JWTSearchAttributeKey.NAME;
        LOG.info("key = " + key.toString());
        final String searchAttribute = searchAccount.getSearchAttribute(key);
        LOG.info("searchAttribute = ", searchAttribute);
        final String name = ctx.read(searchAttribute);
        LOG.info("name = ", name);

        //first attempt to get the cached account
        final Account acct = super.getAccount(name);

        try {
            final DBBroker broker = getDatabase().get(Optional.of(getSecurityManager().getSystemSubject()));

            if (acct != null) {

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Cached used.");
                }

                updateGroupsInDatabase(broker, ctx, acct);

                return acct;
            } else {
                return createAccountInDatabase(broker, ctx, name);
            }
        } catch (EXistException e) {
            e.printStackTrace();
        } catch (PermissionDeniedException e) {
            e.printStackTrace();
        }
        return null;
    }

    private List<Group> getGroupMembershipForJWTUser(final ReadContext ctx, final DBBroker broker) {
        final List<Group> memberOf_groups = new ArrayList<>();

        final JWTSearchContext searchContext = ensureContextFactory().getSearchContext();
        final JWTSearchGroup groupContext = searchContext.getSearchGroup();

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

    private Account createAccountInDatabase(DBBroker broker, ReadContext ctx, String name) throws PermissionDeniedException, EXistException {

        final UserAider userAider = new UserAider(ID, name);

        // Add the member groups
        for (final Group memberOf_group : getGroupMembershipForJWTUser(ctx, broker)) {
            userAider.addGroup(memberOf_group);
        }

        // store any requested metadata
        for (final AbstractMap.SimpleEntry<AXSchemaType, String> metadata : getMetadataForJWTUser(ctx)) {
            userAider.setMetadataValue(metadata.getKey(), metadata.getValue());
        }

        final Account account = getSecurityManager().addAccount(userAider);

        return account;
    }

    private Iterable<? extends AbstractMap.SimpleEntry<AXSchemaType, String>> getMetadataForJWTUser(ReadContext ctx) {
        final List<AbstractMap.SimpleEntry<AXSchemaType, String>> metadata = new ArrayList<>();
        final JWTSearchAccount searchAccount = ensureContextFactory().getSearchContext().getSearchAccount();

        for (final AXSchemaType axSchemaType : searchAccount.getMetadataSearchAttributeKeys()) {
            final String searchAttribute = searchAccount.getMetadataSearchAttribute(axSchemaType);

        }
        return metadata;
    }

    private Group createGroupInDatabase(final DBBroker broker, final String groupname) throws AuthenticationException {
        try {
            //return sm.addGroup(instantiateGroup(this, groupname));
            return getSecurityManager().addGroup(broker, new GroupAider(ID, groupname));

        } catch (Exception e) {
            throw new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, e.getMessage(), e);
        }
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


    private final class AuthenticatedJWTSubjectAccreditedImpl extends SubjectAccreditedImpl {

        private final String authenticatedCredentials;

        private AuthenticatedJWTSubjectAccreditedImpl(final AbstractAccount account, final ReadContext jwt, final String authenticatedCredentials) {
            super(account, jwt);
            this.authenticatedCredentials = authenticatedCredentials;
        }

        private String getAuthenticatedCredentials() {
            return authenticatedCredentials;
        }
    }

}
