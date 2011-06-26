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

    @ConfigurationFieldAsElement("account")
    protected LDAPSearchAccount searchAccount = null;

    @ConfigurationFieldAsElement("group")
    protected LDAPSearchGroup searchGroup = null;

    private final Configuration configuration;

    public LDAPSearchContext(Configuration config) {
        this.configuration = Configurator.configure(this, config);
    }

    public String getBase() {
        return base;
    }
    
    public String getAbsoluteBase() {
        if(base != null) {
            return getBase().substring(getBase().indexOf("dc="));
        }
        return null;
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