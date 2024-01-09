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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;
import org.exist.security.AXSchemaType;

/**
 * @author aretter
 */
@ConfigurationClass("")
public abstract class AbstractLDAPSearchPrincipal implements Configurable {

    @ConfigurationFieldAsElement("search-filter-prefix")
    protected String searchFilterPrefix = null;

    @ConfigurationFieldAsElement("search-attribute")
    protected Map<String, String> searchAttributes = new HashMap<>();

    @ConfigurationFieldAsElement("metadata-search-attribute")
    protected Map<String, String> metadataSearchAttributes = new HashMap<>();

    @ConfigurationFieldAsElement("whitelist")
    protected LDAPPrincipalWhiteList whiteList = null;

    @ConfigurationFieldAsElement("blacklist")
    protected LDAPPrincipalBlackList blackList = null;

    protected Configuration configuration;

    public AbstractLDAPSearchPrincipal(final Configuration config) {
        this.configuration = Configurator.configure(this, config);
    }

    public String getSearchFilterPrefix() {
        return searchFilterPrefix;
    }

    public String getSearchAttribute(final LDAPSearchAttributeKey ldapSearchAttributeKey) {
        return searchAttributes.get(ldapSearchAttributeKey.getKey());
    }

    public String getMetadataSearchAttribute(final AXSchemaType axSchemaType) {
        return metadataSearchAttributes.get(axSchemaType.getNamespace());
    }


    public Set<AXSchemaType> getMetadataSearchAttributeKeys() {
        final Set<AXSchemaType> metadataSearchAttributeKeys = new HashSet<>();
        for (final String key : metadataSearchAttributes.keySet()) {
            metadataSearchAttributeKeys.add(AXSchemaType.valueOfNamespace(key));
        }
        return metadataSearchAttributeKeys;
    }

    @Override
    public boolean isConfigured() {
        return (configuration != null);
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    public LDAPPrincipalBlackList getBlackList() {
        return blackList;
    }

    public LDAPPrincipalWhiteList getWhiteList() {
        return whiteList;
    }

    public enum LDAPSearchAttributeKey {
        NAME("name"),
        DN("dn"),
        MEMBER_OF("memberOf"),
        MEMBER("member"),
        PRIMARY_GROUP_TOKEN("primaryGroupToken"),
        PRIMARY_GROUP_ID("primaryGroupID"),
        OBJECT_SID("objectSid");

        private final String key;

        LDAPSearchAttributeKey(final String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public static LDAPSearchAttributeKey valueOfKey(final String key) {
            for (final LDAPSearchAttributeKey ldapSearchAttributeKey : LDAPSearchAttributeKey.values()) {
                if (ldapSearchAttributeKey.getKey().equals(key)) {
                    return ldapSearchAttributeKey;
                }
            }
            return null;
        }
    }
}
