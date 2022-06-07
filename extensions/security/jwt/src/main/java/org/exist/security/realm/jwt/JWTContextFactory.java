/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2021 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.security.realm.jwt;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;

/**
 * @author <a href="mailto:loren.cahlander@gmail.com">Loren Cahlander</a>
 *
 */
@ConfigurationClass("context")
public class JWTContextFactory implements Configurable {

    /**
     *
     */
    private static final Logger LOG = LogManager.getLogger(JWTContextFactory.class);

    /**
     *
     */
    @ConfigurationFieldAsElement("domain")
    protected String domain = null;

    /**
     *
     */
    @ConfigurationFieldAsElement("secret")
    protected String secret = null;

    /**
     *
     */
    @ConfigurationFieldAsElement("search")
    protected JWTSearchContext searchContext;

    /**
     *
     */
    @ConfigurationFieldAsElement("transformation")
    private JWTTransformationContext transformationContext;

    /**
     *
     */
    private Configuration configuration = null;

    /**
     *
     * @param config
     */
    public JWTContextFactory(final Configuration config) {
        LOG.info("Config = " + config.toString());
        configuration = Configurator.configure(this, config);
    }

    /**
     *
     * @return
     */
    public String getDomain() { return domain; }

    /**
     *
     * @return
     */
    public String getSecret() { return secret; }

    /**
     *
     * @return
     */
    public JWTSearchContext getSearchContext() {
        return searchContext;
    }

    /**
     *
     * @return
     */
    public JWTTransformationContext getTransformationContext() {
        return transformationContext;
    }

    // configurable methods

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
