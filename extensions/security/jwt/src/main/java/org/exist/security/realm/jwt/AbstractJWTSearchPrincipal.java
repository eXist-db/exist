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

    @ConfigurationFieldAsElement("name")
    protected String name = null;

    @ConfigurationFieldAsElement("base-path")
    protected String basePath = null;

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

    public String getName() {
        return name;
    }

    public String getBasePath() {
        return basePath;
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

    public JWTPrincipalDBAList getDbaList() { return dbaList; }

    public JWTPrincipalWhiteList getWhiteList() { return whiteList; }

    public JWTPrincipalBlackList getBlackList() { return blackList; }
}
