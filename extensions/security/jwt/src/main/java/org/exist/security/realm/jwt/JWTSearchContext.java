package org.exist.security.realm.jwt;

import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;

@ConfigurationClass("search")
public class JWTSearchContext implements Configurable {
    @ConfigurationFieldAsElement("account")
    protected JWTSearchAccount searchAccount = null;

    @ConfigurationFieldAsElement("group")
    protected JWTSearchGroup searchGroup = null;

    private final Configuration configuration;

    public JWTSearchContext(final Configuration config) {
        this.configuration = Configurator.configure(this, config);
    }

    public JWTSearchAccount getSearchAccount() {
        return searchAccount;
    }

    public JWTSearchGroup getSearchGroup() {
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
