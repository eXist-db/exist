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
import org.exist.config.annotation.ConfigurationFieldClassMask;
import org.exist.security.AXSchemaType;

/**
 *
 * @author aretter
 */
@ConfigurationClass("")
public abstract class AbstractLDAPSearchPrincipal implements Configurable {

    @ConfigurationFieldAsElement("search-filter-prefix")
    protected String searchFilterPrefix = null;

    @ConfigurationFieldAsElement("search-attribute")
    protected Map<String, String> searchAttributes = new HashMap<String, String>();

    @ConfigurationFieldAsElement("metadata-search-attribute")
    protected Map<String, String> metadataSearchAttributes = new HashMap<String, String>();
    
    @ConfigurationFieldAsElement("whitelist")
    protected LDAPPrincipalWhiteList whiteList = null;
    
    @ConfigurationFieldAsElement("blacklist")
    protected LDAPPrincipalBlackList blackList = null;

    protected Configuration configuration;

    public AbstractLDAPSearchPrincipal(Configuration config) {
        this.configuration = Configurator.configure(this, config);
    }

    public String getSearchFilterPrefix() {
        return searchFilterPrefix;
    }

    public String getSearchAttribute(LDAPSearchAttributeKey ldapSearchAttributeKey) {
        return searchAttributes.get(ldapSearchAttributeKey.getKey());
    }

    public String getMetadataSearchAttribute(AXSchemaType axSchemaType) {
        return metadataSearchAttributes.get(axSchemaType.getNamespace());
    }


    public Set<AXSchemaType> getMetadataSearchAttributeKeys() {
        Set<AXSchemaType> metadataSearchAttributeKeys = new HashSet<AXSchemaType>();
        for(String key : metadataSearchAttributes.keySet()) {
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
        LDAPSearchAttributeKey(String key) {
            this.key = key;
        }

        public String getKey(){
            return key;
        }

        public static LDAPSearchAttributeKey valueOfKey(String key) {
            for(LDAPSearchAttributeKey ldapSearchAttributeKey : LDAPSearchAttributeKey.values()) {
                if(ldapSearchAttributeKey.getKey().equals(key)){
                    return ldapSearchAttributeKey;
                }
            }
            return null;
        }
    }
}
