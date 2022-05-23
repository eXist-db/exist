package org.exist.security.realm.jwt;

import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationClass;

@ConfigurationClass("group")
public class JWTSearchGroup extends AbstractJWTSearchPrincipal implements Configurable {

    public JWTSearchGroup(final Configuration config) {
        super(config);

        //it require, because class's fields initializing after super constructor
        if (this.configuration != null) {
            this.configuration = Configurator.configure(this, this.configuration);
        }
    }
}
