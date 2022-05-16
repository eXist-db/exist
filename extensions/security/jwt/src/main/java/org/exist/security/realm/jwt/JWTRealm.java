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

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import javax.naming.ldap.LdapContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    protected Map jwtMap;

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
        ObjectMapper mapper = new ObjectMapper();
        Map jwtMap;
        try {
            jwtMap = mapper.readValue(jwt, Map.class);
            this.jwtMap = jwtMap;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        final String name1 = "sub"; // this.jwtContextFactory.getAccount().getSearchProperty(JWTAccount.JWTPropertyKey.NAME);
        LOG.info("Name property = " + name1);
        String name = (String) jwtMap.get(name1);
        LOG.info("From JWT = " + name);
        final AbstractAccount account = (AbstractAccount) getAccount(name);
        return new AuthenticatedJWTSubjectAccreditedImpl (account, this.jwtMap, String.valueOf(credentials));
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
    @Override
    public final synchronized Account getAccount(String name) {

        //first attempt to get the cached account
        final Account acct = super.getAccount(name);
        try {
            final DBBroker broker = getDatabase().get(Optional.of(getSecurityManager().getSystemSubject()));

            if (acct != null) {

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Cached used.");
                }

                updateGroupsInDatabase(broker, this.jwtMap, acct);

                return acct;
            } else {
                return createAccountInDatabase(broker, this.jwtMap, name);
            }
        } catch (EXistException e) {
            e.printStackTrace();
        } catch (PermissionDeniedException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void updateGroupsInDatabase(DBBroker broker, Map decodedJWT, Account acct) throws PermissionDeniedException, EXistException {
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

    private Account createAccountInDatabase(DBBroker broker, Map jwtMap, String name) throws PermissionDeniedException, EXistException {
        Account account = null;
//        final String claim = this.jwtContextFactory.getGroup().getClaim();
//        final List<String> jwtGroupNames = ((ArrayList) jwtMap.get(claim)).asList(String.class);
//        final List<String> dbaList = this.jwtContextFactory.getGroup().getDbaList().getPrincipals();
//
        final UserAider userAider = new UserAider(ID, name);
//
//
//        //store any requested metadata
//        for (final AXSchemaType axSchemaType : AXSchemaType.values()) {
//            final String metadataSearchProperty = this.jwtContextFactory.getAccount().getMetadataSearchProperty(axSchemaType);
//            if (metadataSearchProperty != null) {
//                final String s = ((Claim) jwtMap.getClaim(metadataSearchProperty)).asString();
//                if (s != null) {
//                    userAider.setMetadataValue(axSchemaType, s);
//                }
//            }
//        }
//
//        boolean dbaNotAdded = true;
//
//        for (final String jwtGroupName : jwtGroupNames) {
//            if (dbaNotAdded && dbaList.contains(jwtGroupName)) {
//                userAider.addGroup(getSecurityManager().getDBAGroup());
//                dbaNotAdded = false;
//            }
//            final Group group = super.getGroup(jwtGroupName);
//
//            if (group != null) {
//                userAider.addGroup(group);
//            } else {
//                final GroupAider groupAider = new GroupAider(ID, jwtGroupName);
//                final Group group1 = getSecurityManager().addGroup(broker, groupAider);
//                userAider.addGroup(group1);
//            }
//        }

        try {
            account = getSecurityManager().addAccount(broker, userAider);
        } catch (PermissionDeniedException e) {
            e.printStackTrace();
        } catch (EXistException e) {
            e.printStackTrace();
        }

        return account;
    }

    private Group createGroupInDatabase(final DBBroker broker, final String groupname) throws AuthenticationException {
        try {
            //return sm.addGroup(instantiateGroup(this, groupname));
            return getSecurityManager().addGroup(broker, new GroupAider(ID, groupname));

        } catch (Exception e) {
            throw new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, e.getMessage(), e);
        }
    }

    private final class AuthenticatedJWTSubjectAccreditedImpl extends SubjectAccreditedImpl {

        private final String authenticatedCredentials;

        private AuthenticatedJWTSubjectAccreditedImpl(final AbstractAccount account, final Map jwt, final String authenticatedCredentials) {
            super(account, jwt);
            this.authenticatedCredentials = authenticatedCredentials;
        }

        private String getAuthenticatedCredentials() {
            return authenticatedCredentials;
        }
    }

}
