package org.exist.security.realm.ldap;

import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;

/**
 *
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

    @ConfigurationFieldAsElement("account-search-filter")
    protected String accountSearchFilter = null;

    @ConfigurationFieldAsElement("group-search-filter")
    protected String groupSearchFilter = null;

    private final Configuration configuration;

    public LDAPSearchContext(Configuration config) {
        this.configuration = Configurator.configure(this, config);
    }

    public String getBase() {
        return base;
    }

    public String getDefaultUsername() {
        return defaultUsername;
    }

    public String getDefaultPassword() {
        return defaultPassword;
    }

    public String getAccountSearchFilter() {
        return accountSearchFilter;
    }

    public String getGroupSearchFilter() {
        return groupSearchFilter;
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
