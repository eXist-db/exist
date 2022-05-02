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
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;
import org.exist.security.AXSchemaType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="mailto:loren.cahlander@gmail.com">Loren Cahlander</a>
 *
 */
@ConfigurationClass("group")
public class JWTGroup implements Configurable {

    @ConfigurationFieldAsElement("claim")
    protected String claim = null;

    @ConfigurationFieldAsElement("property")
    protected Map<String, String> searchProperties = new HashMap<>();

    @ConfigurationFieldAsElement("metadata-property")
    protected Map<String, String> metadataProperties = new HashMap<>();

    @ConfigurationFieldAsElement("dba")
    protected JWTPrincipalDBAList dbaList = null;

    @ConfigurationFieldAsElement("whitelist")
    protected JWTPrincipalWhiteList whiteList = null;

    @ConfigurationFieldAsElement("blacklist")
    protected JWTPrincipalBlackList blackList = null;

    protected Configuration configuration;

    public JWTGroup(final Configuration config) {
        this.configuration = Configurator.configure(this, config);
    }

    public String getClaim() { return claim; }

    public JWTPrincipalDBAList getDbaList() { return dbaList; }

    public JWTPrincipalBlackList getBlackList() {
        return blackList;
    }

    public JWTPrincipalWhiteList getWhiteList() {
        return whiteList;
    }

    public String getSearchAttribute(final JWTAccount.JWTPropertyKey jwtPropertyKey) {
        return searchProperties.get(jwtPropertyKey.getKey());
    }

    public String getMetadataSearchAttribute(final AXSchemaType axSchemaType) {
        return metadataProperties.get(axSchemaType.getNamespace());
    }


    public Set<AXSchemaType> getMetadataPropertyKeys() {
        final Set<AXSchemaType> metadataPropertyKeys = new HashSet<>();
        for (final String key : metadataProperties.keySet()) {
            metadataPropertyKeys.add(AXSchemaType.valueOfNamespace(key));
        }
        return metadataPropertyKeys;
    }

    public enum JWTPropertyKey {
        NAME("name");

        private final String key;

        JWTPropertyKey(final String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public static JWTAccount.JWTPropertyKey valueOfKey(final String key) {
            for (final JWTAccount.JWTPropertyKey jwtPropertyKey : JWTAccount.JWTPropertyKey.values()) {
                if (jwtPropertyKey.getKey().equals(key)) {
                    return jwtPropertyKey;
                }
            }
            return null;
        }
    }


    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }
}
