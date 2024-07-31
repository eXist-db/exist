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
package org.exist.security.internal;

import org.exist.security.AbstractRealm;
import org.exist.security.AbstractAccount;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SchemaType;
import org.exist.security.SecurityManager;
import org.exist.security.Account;
import org.exist.security.internal.aider.UserAider;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Properties;
import org.exist.security.Credential;

import org.exist.storage.DBBroker;

/**
 * Represents a user within the database.
 * 
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
 * @author <a href="mailto:adam@exist-db.org">Adam retter</a>
 */
@ConfigurationClass("account")
public class AccountImpl extends AbstractAccount {

    private final static Logger LOG = LogManager.getLogger(AccountImpl.class);
    public static boolean CHECK_PASSWORDS = true;

    private final static SecurityProperties securityProperties = new SecurityProperties();

    public static SecurityProperties getSecurityProperties() {
        return securityProperties;
    }

    /*
    static {
        Properties props = new Properties();
        try {
            props.load(AccountImpl.class.getClassLoader().getResourceAsStream(
                    "org/exist/security/security.properties"));
        } catch(IOException e) {
        }
        String option = props.getProperty("passwords.encoding", "md5");
        setPasswordEncoding(option);
        option = props.getProperty("passwords.check", "yes");
        CHECK_PASSWORDS = option.equalsIgnoreCase("yes")
                || option.equalsIgnoreCase("true");
    }

    static public void enablePasswordChecks(boolean check) {
        CHECK_PASSWORDS = check;
    }

    static public void setPasswordEncoding(String encoding) {
        if(encoding != null) {
            LOG.equals("Setting password encoding to " + encoding);
            if(encoding.equalsIgnoreCase("plain")) {
                PASSWORD_ENCODING = PLAIN_ENCODING;
            } else if(encoding.equalsIgnoreCase("md5")) {
                PASSWORD_ENCODING = MD5_ENCODING;
            } else {
                PASSWORD_ENCODING = SIMPLE_MD5_ENCODING;
            }
        }
    }*/

    @ConfigurationFieldAsElement("password")
    private String password = null;
    @ConfigurationFieldAsElement("digestPassword")
    private String digestPassword = null;

    /**
     * Create a new user with name and password
     * @param broker the eXist-db DBBroker
     * @param realm the security realm
     * @param id of the account
     * @param name of the account
     * @param password of the account
     * @throws ConfigurationException if there is an configuration error
     */
    public AccountImpl(final DBBroker broker, final AbstractRealm realm, final int id, final String name,final  String password) throws ConfigurationException {
        super(broker, realm, id, name);
        setPassword(password);
    }

    public AccountImpl(final DBBroker broker, final AbstractRealm realm, final int id, final String name, final String password, final Group group, final boolean hasDbaRole) throws ConfigurationException {
        super(broker, realm, id, name);
        setPassword(password);
        this.groups.add(group);
        this.hasDbaRole = hasDbaRole;
    }

    public AccountImpl(final DBBroker broker, final AbstractRealm realm, final int id, final String name, final String password, final Group group) throws ConfigurationException {
        super(broker, realm, id, name);
        setPassword(password);
        this.groups.add(group);
    }

    /**
     * Create a new user with name
     *
     * @param broker the eXist-db DBBroker
     * @param realm the security realm
     * @param name of the account
     *            The account name
     * @throws ConfigurationException if there is an configuration error
     */
    public AccountImpl(final DBBroker broker, final AbstractRealm realm, final String name) throws ConfigurationException {
        super(broker, realm, Account.UNDEFINED_ID, name);
    }

//    /**
//     * Create a new user with name, password and primary group
//     *
//     * @param name
//     * @param password
//     * @param primaryGroup
//     * @throws ConfigurationException
//     * @throws PermissionDeniedException
//     */
//	public AccountImpl(AbstractRealm realm, int id, String name, String password, String primaryGroup) throws ConfigurationException {
//		this(realm, id, name, password);
//		addGroup(primaryGroup);
//	}
    
    public AccountImpl(final DBBroker broker, final AbstractRealm realm, final int id, final Account from_user) throws ConfigurationException, PermissionDeniedException {
        super(broker, realm, id, from_user.getName());
        instantiate(from_user);
    }
    
    private void instantiate(final Account from_user) throws PermissionDeniedException {

        //copy metadata
        for(final SchemaType metadataKey : from_user.getMetadataKeys()) {
            final String metadataValue = from_user.getMetadataValue(metadataKey);
            setMetadataValue(metadataKey, metadataValue);
        }
        
        //copy umask
        setUserMask(from_user.getUserMask());

        if(from_user instanceof AccountImpl) {
            final AccountImpl user = (AccountImpl) from_user;

            groups = new ArrayList<>(user.groups);

            password = user.password;
            digestPassword = user.digestPassword;

            hasDbaRole = user.hasDbaRole;

            _cred = user._cred;
        } else if(from_user instanceof UserAider) {
            final UserAider user = (UserAider) from_user;

            final String[] groups = user.getGroups();
            for (final String group : groups) {
                addGroup(group);
            }

            setPassword(user.getPassword());
            digestPassword = user.getDigestPassword();
        } else {
            addGroup(from_user.getDefaultGroup());
            //TODO: groups
        }
    }

    public AccountImpl(final DBBroker broker, final AbstractRealm realm, final AccountImpl from_user) throws ConfigurationException {
        super(broker, realm, from_user.id, from_user.name);

        //copy metadata
        for(final SchemaType metadataKey : from_user.getMetadataKeys()) {
            final String metadataValue = from_user.getMetadataValue(metadataKey);
            setMetadataValue(metadataKey, metadataValue);
        }

        groups = from_user.groups;

        password = from_user.password;
        digestPassword = from_user.digestPassword;

        hasDbaRole = from_user.hasDbaRole;

        _cred = from_user._cred;

        //this.realm = realm;   //set via super()
    }

    public AccountImpl(final AbstractRealm realm, final Configuration configuration) throws ConfigurationException {
        super(realm, configuration);

        //this is required because the classes fields are initialized after the super constructor
        if(this.configuration != null) {
            this.configuration = Configurator.configure(this, this.configuration);
        }
        this.hasDbaRole = this.hasGroup(SecurityManager.DBA_GROUP);
    }

    public AccountImpl(final AbstractRealm realm, final Configuration configuration, final boolean removed) throws ConfigurationException {
        this(realm, configuration);
        this.removed = removed;
    }

    /**
     * Get the user's password
     *
     * @return Description of the Return Value
     * @deprecated
     */
    @Override @Deprecated
    public final String getPassword() {
        return password;
    }

    @Override
    public final String getDigestPassword() {
        return digestPassword;
    }

    @Override
    public final void setPassword(final String passwd) {
        _cred = new Password(this, passwd);

        if(passwd == null) {
            this.password = null;
            this.digestPassword = null;
        } else {
            this.password = _cred.toString();
            this.digestPassword = _cred.getDigest();
        }
    }

    @Override
    public void setCredential(final Credential credential) {
        this._cred = credential;
        this.password = _cred.toString();
        this.digestPassword = _cred.getDigest();
    }

    public final static class SecurityProperties {

        private final static boolean DEFAULT_CHECK_PASSWORDS = true;

        private final static String PROP_CHECK_PASSWORDS = "passwords.check";

        private Properties loadedSecurityProperties = null;
        private Boolean checkPasswords = null;

        public synchronized boolean isCheckPasswords() {
            if(checkPasswords == null) {
                final String property = getProperty(PROP_CHECK_PASSWORDS);
                if(property == null || property.isEmpty()) {
                    checkPasswords = DEFAULT_CHECK_PASSWORDS;
                } else {
                    checkPasswords = property.equalsIgnoreCase("yes") || property.equalsIgnoreCase("true");
                }
            }
            return checkPasswords;
        }

        public synchronized void enableCheckPasswords(final boolean enable) {
            this.checkPasswords = enable;
        }

        private synchronized String getProperty(final String propertyName) {
            if(loadedSecurityProperties == null) {
                loadedSecurityProperties = new Properties();

                try(final InputStream is = AccountImpl.class.getResourceAsStream("security.properties")) {
                    
                    if(is != null) {
                        loadedSecurityProperties.load(is);
                    }
                } catch(final IOException ioe) {
                    LOG.error("Unable to load security.properties, using defaults. {}", ioe.getMessage(), ioe);
                }
            }
            return loadedSecurityProperties.getProperty(propertyName);
        }
    }

    //this method is used by Configurator
    public final Group insertGroup(final int index, final String name) throws PermissionDeniedException {
        //if we cant find the group in our own realm, try other realms
        final Group group = Optional.ofNullable(getRealm().getGroup(name))
                .orElse(getRealm().getSecurityManager().getGroup(name));

        return insertGroup(index, group);
    }

    private Group insertGroup(final int index, final Group group) throws PermissionDeniedException {

        if(group == null){
            return null;
        }

        final Account user = getDatabase().getActiveBroker().getCurrentSubject();
        group.assertCanModifyGroup(user);

        if(!groups.contains(group)) {
            groups.add(index, group);

            if(SecurityManager.DBA_GROUP.equals(group.getName())) {
                hasDbaRole = true;
            }
        }

        return group;
    }
}
