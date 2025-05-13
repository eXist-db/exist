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

import java.text.MessageFormat;
import java.util.Hashtable;
import java.util.Map;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
@ConfigurationClass("context")
public class LdapContextFactory implements Configurable {

    private static final Logger LOG = LogManager.getLogger(LdapContextFactory.class);

    private static final String SUN_CONNECTION_POOLING_PROPERTY = "com.sun.jndi.ldap.connect.pool";

    @ConfigurationFieldAsElement("authentication")
    protected String authentication = "simple";

    @ConfigurationFieldAsElement("use-ssl")
    private final boolean ssl = false;

    @ConfigurationFieldAsElement("principal-pattern")
    protected String principalPattern = null;
    protected MessageFormat principalPatternFormat;

    @ConfigurationFieldAsElement("url")
    protected String url = null;

    @ConfigurationFieldAsElement("domain")
    protected String domain = null;

    protected String contextFactoryClassName = "com.sun.jndi.ldap.LdapCtxFactory";

    protected String systemUsername = null;

    protected String systemPassword = null;

    private boolean usePooling = true;

    private Configuration configuration = null;

    @ConfigurationFieldAsElement("search")
    private LDAPSearchContext search;

    @ConfigurationFieldAsElement("transformation")
    private LDAPTransformationContext realmTransformation;

    public LdapContextFactory(final Configuration config) {
        configuration = Configurator.configure(this, config);
        if (principalPattern != null) {
            principalPatternFormat = new MessageFormat(principalPattern);
        }
    }

    public LdapContext getSystemLdapContext() throws NamingException {
        return getLdapContext(systemUsername, systemPassword);
    }

    public LdapContext getLdapContext(final String username, final String password) throws NamingException {
        return getLdapContext(username, password, null);
    }

    public LdapContext getLdapContext(String username, final String password, final Map<String, Object> additionalEnv) throws NamingException {

        if (url == null) {
            throw new IllegalStateException("An LDAP URL must be specified of the form ldap://<hostname>:<port>");
        }

        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Password for LDAP authentication may not be empty.");
        }

        if (username != null && principalPattern != null) {
            username = principalPatternFormat.format(new String[]{username});
        }

        final Hashtable<String, Object> env = new Hashtable<>();

        env.put(Context.SECURITY_AUTHENTICATION, authentication);
        if (ssl) {
            env.put(Context.SECURITY_PROTOCOL, "ssl");
        }

        if (username != null) {
            env.put(Context.SECURITY_PRINCIPAL, username);
        }

        if (password != null) {
            env.put(Context.SECURITY_CREDENTIALS, password);
        }

        env.put(Context.INITIAL_CONTEXT_FACTORY, contextFactoryClassName);
        env.put(Context.PROVIDER_URL, url);

        //Absolutely nessecary for working with Active Directory
        env.put("java.naming.ldap.attributes.binary", "objectSid");

        // the following is helpful in debugging errors
        //env.put("com.sun.jndi.ldap.trace.ber", System.err);

        // Only pool connections for system contexts
        if (usePooling && username != null && username.equals(systemUsername)) {
            // Enable connection pooling
            env.put(SUN_CONNECTION_POOLING_PROPERTY, "true");
        }

        if (additionalEnv != null) {
            env.putAll(additionalEnv);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Initializing LDAP context using URL [{}] and username [{}] with pooling [{}]", url, username, usePooling ? "enabled" : "disabled");
        }

        return new InitialLdapContext(env, null);
    }

    public LDAPSearchContext getSearch() {
        return search;
    }

    public LDAPTransformationContext getTransformationContext() {
        return realmTransformation;
    }

    public String getDomain() {
        return domain;
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
}