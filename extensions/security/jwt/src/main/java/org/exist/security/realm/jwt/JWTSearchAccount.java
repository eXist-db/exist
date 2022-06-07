package org.exist.security.realm.jwt;

import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationClass;

/**
 * @author <a href="mailto:loren.cahlander@gmail.com">Loren Cahlander</a>
 *
 */
@ConfigurationClass("account")
public class JWTSearchAccount extends AbstractJWTSearchPrincipal implements Configurable {

    /**
     *
     * @param config
     */
    public JWTSearchAccount(final Configuration config) {
        super(config);

        //it require, because class's fields initializing after super constructor
        if (this.configuration != null) {
            this.configuration = Configurator.configure(this, this.configuration);
        }
    }
}
