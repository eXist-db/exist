package org.exist.security.realm.ldap;

import java.util.ArrayList;
import java.util.List;

import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;

/**
 * @author aretter
 */
@ConfigurationClass("")
public abstract class AbstractLDAPPrincipalRestrictionList implements Configurable {

    @ConfigurationFieldAsElement("principal")
    private List<String> principals = new ArrayList<>();

    protected Configuration configuration;

    public AbstractLDAPPrincipalRestrictionList(final Configuration config) {
        this.configuration = Configurator.configure(this, config);
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public boolean isConfigured() {
        return (configuration != null);
    }

    public List<String> getPrincipals() {
        return principals;
    }

    public void addPrincipal(String principal) {
        this.principals.add(principal);
    }
}