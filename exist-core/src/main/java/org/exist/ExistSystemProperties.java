/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
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
package org.exist;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;

import com.evolvedbinary.j8fu.lazy.AtomicLazyVal;
import net.jcip.annotations.ThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@ThreadSafe
public class ExistSystemProperties {

    public static final String PROP_PRODUCT_NAME = "product-name";
    public static final String PROP_PRODUCT_VERSION = "product-version";
    public static final String PROP_PRODUCT_BUILD = "product-build";
    public static final String PROP_GIT_BRANCH = "git-branch";
    public static final String PROP_GIT_COMMIT = "git-commit";

    private static final Logger LOG = LogManager.getLogger(ExistSystemProperties.class);
    private static final ExistSystemProperties instance = new ExistSystemProperties();

    private final AtomicLazyVal<Properties> properties = new AtomicLazyVal<>(this::load);

    public final static ExistSystemProperties getInstance() {
        return instance;
    }

    private ExistSystemProperties() {
    }

    private Properties load() {
        final Properties properties = new Properties();
        try (final InputStream is = ExistSystemProperties.class.getResourceAsStream("system.properties")) {
            if (is != null) {
                properties.load(is);
            }
        } catch (final IOException ioe) {
            LOG.error("Unable to load system.properties from class loader: {}", ioe.getMessage(), ioe);
        }
        return properties;
    }

    public String getExistSystemProperty(final String propertyName) {
        return properties.get().getProperty(propertyName);
    }

    public String getExistSystemProperty(final String propertyName, final String defaultValue) {
        return properties.get().getProperty(propertyName, defaultValue);
    }

    /**
     * Get the available eXist System Properties.
     *
     * @return the available eXist System Properties.
     */
    public Set<String> getAvailableExistSystemProperties() {
        return properties.get().stringPropertyNames();
    }
}