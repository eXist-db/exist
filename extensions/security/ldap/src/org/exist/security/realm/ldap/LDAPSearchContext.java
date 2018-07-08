package org.exist.security.realm.ldap;

import javax.naming.NamingException;

import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;

/**
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

    public LDAPSearchContext(final Configuration config) {
        this.configuration = Configurator.configure(this, config);
    }

    public String getBase() {
        return base;
    }

    public String getAbsoluteBase() throws NamingException {
        if (getBase() != null) {
            int index;
            if ((index = getBase().indexOf("dc=")) >= 0) {
                return getBase().substring(index);
            }

            if ((index = getBase().indexOf("DC=")) >= 0) {
                return getBase().substring(index);
            }
        } else {
            throw new NamingException("no 'base' defined");
        }
        throw new NamingException("'base' have no 'dc=' or 'DC='");
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
