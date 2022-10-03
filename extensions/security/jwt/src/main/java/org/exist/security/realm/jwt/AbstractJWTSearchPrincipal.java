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
package org.exist.security.realm.jwt;

import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;
import org.exist.security.AXSchemaType;

/**
 * @author <a href="mailto:loren.cahlander@gmail.com">Loren Cahlander</a>
 *
 */
@ConfigurationClass("")
public abstract class AbstractJWTSearchPrincipal implements Configurable {

    /**
     *
     */
    private static final Logger LOG = LogManager.getLogger(AbstractJWTSearchPrincipal.class);

    /**
     *
     */
    @ConfigurationFieldAsElement("identifier")
    protected String identifier = null;
    /**
        "http://axschema.org/contact/email": "email",
    */
    @ConfigurationFieldAsElement("email")
    protected String email = null;

    /**
        "http://axschema.org/pref/language": "language",
    */
    @ConfigurationFieldAsElement("language")
    protected String language = null;

    /**
        "http://exist-db.org/security/description": "description",
    */
    @ConfigurationFieldAsElement("description")
    protected String description = null;

    /**
        "http://axschema.org/contact/country/home": "country",
    */
    @ConfigurationFieldAsElement("country")
    protected String country = null;

    /**
        "http://axschema.org/namePerson": "name",
    */
    @ConfigurationFieldAsElement("name")
    protected String name = null;

    /**
        "http://axschema.org/namePerson/first": "firstname",
    */
    @ConfigurationFieldAsElement("firstname")
    protected String firstname = null;

    /**
        "http://axschema.org/namePerson/friendly": "friendly",
    */
    @ConfigurationFieldAsElement("friendly")
    protected String friendly = null;

    /**
        "http://axschema.org/namePerson/last": "lastname",
    */
    @ConfigurationFieldAsElement("lastname")
    protected String lastname = null;

    /**
        "http://axschema.org/pref/timezone": "timezone"
     */
    @ConfigurationFieldAsElement("timezone")
    protected String timezone = null;

    /**
     *
     */
    @ConfigurationFieldAsElement("base-path")
    protected String basePath = null;

    /**
     *
     */
    @ConfigurationFieldAsElement("dbalist")
    protected JWTPrincipalDBAList dbaList = null;

    /**
     *
     */
    @ConfigurationFieldAsElement("whitelist")
    protected JWTPrincipalWhiteList whiteList = null;

    /**
     *
     */
    @ConfigurationFieldAsElement("blacklist")
    protected  JWTPrincipalBlackList blackList = null;

    /**
     *
     */
    protected Configuration configuration;

    /**
     *
     * @param config
     */
    public AbstractJWTSearchPrincipal(final Configuration config) {
        this.configuration = Configurator.configure(this, config);
    }

    /**
     *
     * @return
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     *
     * @return
     */
    public String getEmail() {
        return email;
    }

    /**
     *
     * @return
     */
    public String getLanguage() {
        return language;
    }

    /**
     *
     * @return
     */
    public String getDescription() {
        return description;
    }

    /**
     *
     * @return
     */
    public String getCountry() {
        return country;
    }

    /**
     *
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     *
     * @return
     */
    public String getFirstname() {
        return firstname;
    }

    /**
     *
     * @return
     */
    public String getFriendly() {
        return friendly;
    }

    /**
     *
     * @return
     */
    public String getLastname() {
        return lastname;
    }

    /**
     *
     * @return
     */
    public String getTimezone() {
        return timezone;
    }

    /**
     *
     * @return
     */
    public String getBasePath() {
        return basePath;
    }

    /**
     *
     * @return
     */
    @Override
    public boolean isConfigured() {
        return (configuration != null);
    }

    /**
     *
     * @return
     */
    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     *
     * @return
     */
    public JWTPrincipalDBAList getDbaList() { return dbaList; }

    /**
     *
     * @return
     */
    public JWTPrincipalWhiteList getWhiteList() { return whiteList; }

    /**
     *
     * @return
     */
    public JWTPrincipalBlackList getBlackList() { return blackList; }
}
