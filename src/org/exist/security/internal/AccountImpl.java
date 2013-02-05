/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2003-2011 The eXist Project
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

import org.exist.security.AbstractRealm;
import org.exist.security.AbstractAccount;
import org.apache.log4j.Logger;
import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SchemaType;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.security.Account;
import org.exist.security.internal.aider.UserAider;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import org.exist.storage.DBBroker;

/**
 * Represents a user within the database.
 * 
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 * @author {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
 */
@ConfigurationClass("account")
public class AccountImpl extends AbstractAccount {

    private final static Logger LOG = Logger.getLogger(AccountImpl.class);
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

    static public Subject getUserFromServletRequest(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if(principal instanceof Subject) {
            return (Subject) principal;

            //workaroud strange jetty authentication method, why encapsulate user object??? -shabanovd
        } else if(principal != null && "org.eclipse.jetty.plus.jaas.JAASUserPrincipal".equals(principal.getClass().getName())) {
            try {
                Method method = principal.getClass().getMethod("getSubject");
                Object obj = method.invoke(principal);
                if(obj instanceof javax.security.auth.Subject) {
                    javax.security.auth.Subject subject = (javax.security.auth.Subject) obj;
                    for(Principal _principal_ : subject.getPrincipals()) {
                        if(_principal_ instanceof Subject) {
                            return (Subject) _principal_;
                        }
                    }
                }
            } catch(SecurityException e) {
            } catch(IllegalArgumentException e) {
            } catch(IllegalAccessException e) {
            } catch(NoSuchMethodException e) {
            } catch(InvocationTargetException e) {
            }
        }

        return null;
    }
    @ConfigurationFieldAsElement("password")
    private String password = null;
    @ConfigurationFieldAsElement("digestPassword")
    private String digestPassword = null;

    /**
     * Create a new user with name and password
     *
     * @param realm
     * @param name
     * @param password
     * @throws ConfigurationException
     */
    public AccountImpl(AbstractRealm realm, int id, String name, String password) throws ConfigurationException {
        super(realm, id, name);
        setPassword(password);
    }

    public AccountImpl(AbstractRealm realm, int id, String name, String password, Group group, boolean hasDbaRole) throws ConfigurationException {
        super(realm, id, name);
        setPassword(password);
        this.groups.add(group);
        this.hasDbaRole = hasDbaRole;
    }

    public AccountImpl(AbstractRealm realm, int id, String name, String password, Group group) throws ConfigurationException {
        super(realm, id, name);
        setPassword(password);
        this.groups.add(group);
    }

    /**
     * Create a new user with name
     *
     * @param name
     *            The account name
     * @throws ConfigurationException
     */
    public AccountImpl(AbstractRealm realm, String name) throws ConfigurationException {
        super(realm, Account.UNDEFINED_ID, name);
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
    public AccountImpl(AbstractRealm realm, int id, Account from_user) throws ConfigurationException, PermissionDeniedException {
        super(realm, id, from_user.getName());
        instantiate(from_user);
    }
    
    public AccountImpl(DBBroker broker, AbstractRealm realm, int id, Account from_user) throws ConfigurationException, PermissionDeniedException {
        super(broker, realm, id, from_user.getName());
        instantiate(from_user);
    }
    
    private void instantiate(Account from_user) throws PermissionDeniedException {

        //copy metadata
        for(final SchemaType metadataKey : from_user.getMetadataKeys()) {
            final String metadataValue = from_user.getMetadataValue(metadataKey);
            setMetadataValue(metadataKey, metadataValue);
        }
        
        //copy umask
        setUserMask(from_user.getUserMask());

        if(from_user instanceof AccountImpl) {
            AccountImpl user = (AccountImpl) from_user;

            groups = new ArrayList<Group>(user.groups);

            password = user.password;
            digestPassword = user.digestPassword;

            hasDbaRole = user.hasDbaRole;

            _cred = user._cred;
        } else if(from_user instanceof UserAider) {
            UserAider user = (UserAider) from_user;

            String[] gl = user.getGroups();
            for(int i = 0; i < gl.length; i++) {
                addGroup(gl[i]);
            }

            setPassword(user.getPassword());
            digestPassword = user.getDigestPassword();
        } else {
            addGroup(from_user.getDefaultGroup());
            //TODO: groups
        }
    }

    public AccountImpl(AbstractRealm realm, AccountImpl from_user) throws ConfigurationException {
        super(realm, from_user.id, from_user.name);

        //copy metadata
        for(SchemaType metadataKey : from_user.getMetadataKeys()) {
            String metadataValue = from_user.getMetadataValue(metadataKey);
            setMetadataValue(metadataKey, metadataValue);
        }

        groups = from_user.groups;

        password = from_user.password;
        digestPassword = from_user.digestPassword;

        hasDbaRole = from_user.hasDbaRole;

        _cred = from_user._cred;

        //this.realm = realm;   //set via super()
    }

    public AccountImpl(AbstractRealm realm, Configuration configuration) throws ConfigurationException {
        super(realm, configuration);

        //it require, because class's fields initializing after super constructor
        if(this.configuration != null) {
            this.configuration = Configurator.configure(this, this.configuration);
        }
        this.hasDbaRole = this.hasGroup(SecurityManager.DBA_GROUP);
    }

    public AccountImpl(AbstractRealm realm, Configuration configuration, boolean removed) throws ConfigurationException {
        this(realm, configuration);
        this.removed = removed;
    }

    /**
     * Get the user's password
     *
     * @return Description of the Return Value
     * @deprecated
     */
    public final String getPassword() {
        return password;
    }

    @Deprecated
    public final String getDigestPassword() {
        return digestPassword;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.exist.security.User#setPassword(java.lang.String)
     */
    @Override
    public final void setPassword(String passwd) {
        _cred = new Password(this, passwd);

        if(passwd == null) {
            this.password = null;
            this.digestPassword = null;
        } else {
            this.password = _cred.toString();
            this.digestPassword = _cred.getDigest();
        }
    }

    public final static class SecurityProperties {

        private final static boolean DEFAULT_CHECK_PASSWORDS = true;

        private final static String PROP_CHECK_PASSWORDS = "passwords.check";

        private Properties loadedSecurityProperties = null;
        private Boolean checkPasswords = null;

        public synchronized boolean isCheckPasswords() {
            if(checkPasswords == null) {
                String property = getProperty(PROP_CHECK_PASSWORDS);
                if(property == null || property.length() == 0) {
                    checkPasswords = DEFAULT_CHECK_PASSWORDS;
                } else {
                    checkPasswords = property.equalsIgnoreCase("yes") || property.equalsIgnoreCase("true");
                }
            }
            return checkPasswords.booleanValue();
        }

        public synchronized void enableCheckPasswords(boolean enable) {
            this.checkPasswords = enable;
        }

        private synchronized String getProperty(String propertyName) {
            if(loadedSecurityProperties == null) {
                loadedSecurityProperties = new Properties();

                InputStream is = null;
                try {
                    is = AccountImpl.class.getResourceAsStream("security.properties");
                    if(is != null) {
                        loadedSecurityProperties.load(is);
                    }
                } catch(IOException ioe) {
                    LOG.error("Unable to load security.properties, using defaults. " + ioe.getMessage(), ioe);
                } finally {
                    if(is != null) {
                        try { is.close(); } catch(IOException ioe) { };
                    }
                }
            }
            return loadedSecurityProperties.getProperty(propertyName);
        }
    }
   
}