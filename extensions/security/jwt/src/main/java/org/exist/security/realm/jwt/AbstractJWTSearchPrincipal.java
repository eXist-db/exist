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

@ConfigurationClass("")
public abstract class AbstractJWTSearchPrincipal implements Configurable {

    @ConfigurationFieldAsElement("search-attribute")
    protected Map<String, String> searchAttributes = new HashMap<>();

    @ConfigurationFieldAsElement("metadata-search-attribute")
    protected Map<String, String> metadataSearchAttributes = new HashMap<>();

    @ConfigurationFieldAsElement("dbalist")
    protected JWTPrincipalDBAList dbaList = null;

    @ConfigurationFieldAsElement("whitelist")
    protected JWTPrincipalWhiteList whiteList = null;

    @ConfigurationFieldAsElement("blacklist")
    protected  JWTPrincipalBlackList blackList = null;

    protected Configuration configuration;

    public AbstractJWTSearchPrincipal(final Configuration config) {
        this.configuration = Configurator.configure(this, config);
    }

    public String getSearchAttribute(final JWTSearchAttributeKey key) {
        return searchAttributes.get(key.getKey());
    };

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

    public JWTPrincipalDBAList getDbaList() { return dbaList; }

    public JWTPrincipalWhiteList getWhiteList() { return whiteList; }

    public JWTPrincipalBlackList getBlackList() { return blackList; }


    public enum JWTSearchAttributeKey {
        NAME("name"),
        FULLNAME("fullName"),
        DESCRIPTION("description");

        private final String key;

        JWTSearchAttributeKey(final String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public static JWTSearchAttributeKey valueOfKey(final String key) {
            for (final JWTSearchAttributeKey jwtSearchAttributeKey: JWTSearchAttributeKey.values()) {
                if (jwtSearchAttributeKey.getKey().equals(key)) {
                    return jwtSearchAttributeKey;
                }
            }
            return null;
        }
    }
}
