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

import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationFieldAsElement;

/**
 * @author <a href="mailto:loren.cahlander@gmail.com">Loren Cahlander</a>
 *
 */
public class JWTContextFactory implements Configurable {

    @ConfigurationFieldAsElement("domain")
    protected String domain = null;

    @ConfigurationFieldAsElement("secret")
    protected String secret = null;

    @ConfigurationFieldAsElement("issuer")
    protected String issuer = null;

    @ConfigurationFieldAsElement("cookie")
    protected String cookie = null;

    @ConfigurationFieldAsElement("header-prefix")
    protected String headerPrefix = null;

    @ConfigurationFieldAsElement("account")
    protected JWTAccount account;

    @ConfigurationFieldAsElement("group")
    protected JWTGroup group;

    private Configuration configuration = null;

    public JWTContextFactory(final Configuration config) {
        configuration = Configurator.configure(this, config);
    }

    public String getDomain() { return domain; }

    public String getSecret() { return secret; }

    public String getIssuer() { return issuer; }

    public String getCookie() { return cookie; }

    public String getHeaderPrefix() { return headerPrefix; }

    public JWTAccount getAccount() { return account; }

    public JWTGroup getGroup() { return group; }

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
