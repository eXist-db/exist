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

@ConfigurationClass("")
public abstract class AbstractJWTSearchPrincipal implements Configurable {

    private static final Logger LOG = LogManager.getLogger(AbstractJWTSearchPrincipal.class);

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

    public String getSearchAttribute(final JWTSearchAttributeKey jwtSearchAttributeKey) {

        String key1 = jwtSearchAttributeKey.getKey();
        LOG.info("key = " + key1);

        Iterator it = searchAttributes.keySet().iterator();
        while (it.hasNext()) {
            String key = (String) it.next();
            LOG.info(key + " = " + searchAttributes.get(key));
        }


        return searchAttributes.get(key1);
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
