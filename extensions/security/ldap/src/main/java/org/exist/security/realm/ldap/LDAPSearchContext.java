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

import javax.naming.NamingException;

import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;

/**
 * @author aretter
 */
@ConfigurationClass("search")
public class LDAPSearchContext implements Configurable {

    @ConfigurationFieldAsElement("base")
    protected String base = null;

    @ConfigurationFieldAsElement("default-username")
    protected String defaultUsername = null;

    @ConfigurationFieldAsElement("default-password")
    protected String defaultPassword = null;

    @ConfigurationFieldAsElement("account")
    protected LDAPSearchAccount searchAccount = null;

    @ConfigurationFieldAsElement("group")
    protected LDAPSearchGroup searchGroup = null;

    private final Configuration configuration;

    public LDAPSearchContext(final Configuration config) {
        this.configuration = Configurator.configure(this, config);
    }

    public String getBase() {
        return base;
    }

    public String getAbsoluteBase() throws NamingException {
        if (getBase() != null) {
            int index;
            if ((index = getBase().indexOf("dc=")) >= 0) {
                return getBase().substring(index);
            }

            if ((index = getBase().indexOf("DC=")) >= 0) {
                return getBase().substring(index);
            }
        } else {
            throw new NamingException("no 'base' defined");
        }
        throw new NamingException("'base' have no 'dc=' or 'DC='");
    }

    public String getDefaultUsername() {
        return defaultUsername;
    }

    public String getDefaultPassword() {
        return defaultPassword;
    }

    public LDAPSearchAccount getSearchAccount() {
        return searchAccount;
    }

    public LDAPSearchGroup getSearchGroup() {
        return searchGroup;
    }

    @Override
    public boolean isConfigured() {
        return (configuration != null);
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }
}
