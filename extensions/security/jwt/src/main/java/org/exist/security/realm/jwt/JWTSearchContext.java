package org.exist.security.realm.jwt;

import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;

/**
 * @author <a href="mailto:loren.cahlander@gmail.com">Loren Cahlander</a>
 *
 */
@ConfigurationClass("search")
public class JWTSearchContext implements Configurable {
    /**
     *
     */
    @ConfigurationFieldAsElement("account")
    protected JWTSearchAccount searchAccount = null;

    /**
     *
     */
    @ConfigurationFieldAsElement("group")
    protected JWTSearchGroup searchGroup = null;

    /**
     *
     */
    private final Configuration configuration;

    /**
     *
     * @param config
     */
    public JWTSearchContext(final Configuration config) {
        this.configuration = Configurator.configure(this, config);
    }

    /**
     *
     * @return
     */
    public JWTSearchAccount getSearchAccount() {
        return searchAccount;
    }

    /**
     *
     * @return
     */
    public JWTSearchGroup getSearchGroup() {
        return searchGroup;
    }

    /**
     *
     * @return
     */
    @Override
    public boolean isConfigured() {
        return (configuration != null);
    }

    /**
     *
     * @return
     */
    @Override
    public Configuration getConfiguration() {
        return configuration;
    }
}
