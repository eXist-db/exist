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
package org.exist.webdav;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.BrokerPool;
import org.exist.util.FileUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public class WebDavConfigurator {

    public static final String PROPFIND_METHOD_XML_SIZE = "org.exist.webdav.PROPFIND_METHOD_XML_SIZE";
    public static final String GET_METHOD_XML_SIZE = "org.exist.webdav.GET_METHOD_XML_SIZE";
    private static final Logger LOG = LogManager.getLogger();

    public static Properties getConfiguration(BrokerPool brokerPool) {

        final Properties webDavOptions = new Properties();

        // load specific options
        try {
            // 1) try and read default config from classpath
            try (final InputStream is = WebDavConfigurator.class.getResourceAsStream("webdav.properties")) {
                if (is != null) {
                    LOG.info("Read default WebDAV configuration from classpath");
                    webDavOptions.load(is);
                } else {
                    LOG.warn("Unable to read default WebDAV configuration from the classpath.");
                }
            }
        } catch (final Throwable ex) {
            LOG.error(ex.getMessage());
        }

        try {
            // 2) try and find overridden config relative to EXIST_HOME/etc
            final Optional<Path> eXistHome = brokerPool.getConfiguration().getExistHome();
            final Path config = FileUtils.resolve(eXistHome, "etc").resolve("webdav.properties");

            // Read from file if existent
            if (Files.isReadable(config)) {
                LOG.info("Read WebDAV configuration from {}", config.toAbsolutePath());
                try (final InputStream is = Files.newInputStream(config)) {
                    webDavOptions.load(is);
                }
            }
        } catch (final Throwable ex) {
            LOG.error(ex.getMessage());
        }

        // Override value from system properties when set
        final String propfindMethod = System.getProperty(PROPFIND_METHOD_XML_SIZE);
        if (StringUtils.isNotBlank(propfindMethod)) {
            LOG.info("Configuring {} from system properties", PROPFIND_METHOD_XML_SIZE);
            webDavOptions.setProperty(PROPFIND_METHOD_XML_SIZE, propfindMethod);
        }

        // Override value from system properties when set
        final String getMethod = System.getProperty(GET_METHOD_XML_SIZE);
        if (StringUtils.isNotBlank(getMethod)) {
            LOG.info("Configuring {} from system properties", GET_METHOD_XML_SIZE);
            webDavOptions.setProperty(GET_METHOD_XML_SIZE, getMethod);
        }

        webDavOptions.forEach((k, v) -> LOG.info("WebDAV configuration: {}={}", k, v));

        return webDavOptions;
    }

}
