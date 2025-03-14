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
package org.exist.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.BrokerPool;
import org.exist.xmldb.DatabaseImpl;

import javax.annotation.Nullable;

public class ConfigurationHelper {
    private final static Logger LOG = LogManager.getLogger(ConfigurationHelper.class); //Logger

    public static final String PROP_EXIST_CONFIGURATION_FILE = "exist.configurationFile";

    /**
     * Returns a file handle for eXist's home directory.
     * Order of tests is designed with the idea, the more precise it is,
     * the more the developer know what he is doing
     * <ol>
     *   <li>Brokerpool      : if eXist was already configured.
     *   <li>exist.home      : if exists
     *   <li>user.home       : if exists, with a conf.xml file
     *   <li>user.dir        : if exists, with a conf.xml file
     *   <li>classpath entry : if exists, with a conf.xml file
     * </ol>
     *
     * @return the path to exist home if known
     */
    public static Optional<Path> getExistHome() {
    	return getExistHome(DatabaseImpl.CONF_XML);
    }

    /**
     * Returns a file handle for eXist's home directory.
     * Order of tests is designed with the idea, the more precise it is,
     * the more the developper know what he is doing
     * <ol>
     *   <li>Brokerpool      : if eXist was already configured.
     *   <li>exist.home      : if exists
     *   <li>user.home       : if exists, with a conf.xml file
     *   <li>user.dir        : if exists, with a conf.xml file
     *   <li>classpath entry : if exists, with a conf.xml file
     * </ol>
     *
     * @param config the path to the config file.
     *
     * @return the path to exist home if known
     */
    public static Optional<Path> getExistHome(final String config) {
    	// If eXist was already configured, then return 
    	// the existHome of this instance.
    	try {
    		final BrokerPool broker = BrokerPool.getInstance();
    		if(broker != null) {
    			final Optional<Path> existHome = broker.getConfiguration().getExistHome().map(Path::normalize);
                if(existHome.isPresent()) {
                    LOG.debug("Got eXist home from broker: {}", existHome);
                    return existHome;
                }
    		}
    	} catch(final Throwable e) {
            // Catch all potential problems
            LOG.debug("Could not retrieve instance of BrokerPool: {}", e.getMessage());
    	}
    	
        // try exist.home
        if (System.getProperty("exist.home") != null) {
            final Path existHome = ConfigurationHelper.decodeUserHome(System.getProperty("exist.home")).normalize();
            if (Files.isDirectory(existHome)) {
                LOG.debug("Got eXist home from system property 'exist.home': {}", existHome.toAbsolutePath().toString());
                return Optional.of(existHome);
            }
        }
        
        // try user.home
        final Path userHome = Paths.get(System.getProperty("user.home"));
        final Path userHomeRelativeConfig = userHome.resolve(config);
        if (Files.isDirectory(userHome) && Files.isRegularFile(userHomeRelativeConfig)) {
            final Path existHome = userHomeRelativeConfig.getParent().normalize();
            LOG.debug("Got eXist home: {} from system property 'user.home': {}", existHome.toAbsolutePath(), userHome.toAbsolutePath());
            return Optional.of(existHome);
        }
        
        
        // try user.dir
        final Path userDir = Paths.get(System.getProperty("user.dir"));
        final Path userDirRelativeConfig = userDir.resolve(config);
        if (Files.isDirectory(userDir) && Files.isRegularFile(userDirRelativeConfig)) {
            final Path existHome = userDirRelativeConfig.getParent().normalize();
            LOG.debug("Got eXist home: {} from system property 'user.dir': {}", existHome.toAbsolutePath(), userDir.toAbsolutePath());
            return Optional.of(existHome);
        }
        
        // try classpath
        final URL configUrl = ConfigurationHelper.class.getClassLoader().getResource(config);
        if (configUrl != null) {
            try {
                Path existHome;
                if ("jar".equals(configUrl.getProtocol())) {
                    existHome = Paths.get(new URI(configUrl.getPath())).getParent().getParent().normalize();
                    LOG.warn("{} file was found on the classpath, but inside a Jar file! Derived EXIST_HOME from Jar's parent folder: {}", config, existHome);
                } else {
                    existHome = Paths.get(configUrl.toURI()).getParent().normalize();
                    if ("etc".equals(FileUtils.fileName(existHome))) {
                        existHome = existHome.getParent().normalize();
                    }
                    LOG.debug("Got EXIST_HOME from classpath: {}", existHome.toAbsolutePath().toString());
                }
                return Optional.of(existHome);
            } catch (final URISyntaxException e) {
                // Catch all potential problems
                LOG.error("Could not derive EXIST_HOME from classpath: {}", e.getMessage(), e);
            }
        }
        
        return Optional.empty();
    }

    public static Optional<Path> getFromSystemProperty() {
        return Optional.ofNullable(System.getProperty(PROP_EXIST_CONFIGURATION_FILE)).map(Paths::get);
    }

	/**
     * Returns a file handle for the given path, while <code>path</code> specifies
     * the path to an eXist configuration file or directory.
     * <br>
     * Note that relative paths are being interpreted relative to <code>exist.home</code>
     * or the current working directory, in case <code>exist.home</code> was not set.
     *
     * @param path the file path
     * @return the file handle
     */
    public static Path lookup(final String path) {
        return lookup(path, Optional.empty());
    }
    
    /**
     * Returns a file handle for the given path, while <code>path</code> specifies
     * the path to an eXist configuration file or directory.
     * <br>
     * If <code>parent</code> is null, then relative paths are being interpreted
     * relative to <code>exist.home</code> or the current working directory, in
     * case <code>exist.home</code> was not set.
     *
     * @param path path to the file or directory
     * @param parent parent directory used to lookup <code>path</code>
     * @return the file handle
     */
    public static Path lookup(final String path, final Optional<Path> parent) {
        // attempt to first resolve the path that is used for things like ~user/folder
        Path p = decodeUserHome(path);
        if (!p.isAbsolute()) {
            p = parent
                    .orElse(getExistHome().orElse(Paths.get(System.getProperty("user.dir"))))
                    .resolve(path);
        }
        return p.normalize().toAbsolutePath();
    }
    
    
    /**
     * Resolves the given path by means of eventually replacing <code>~</code> with the users
     * home directory, taken from the system property <code>user.home</code>.
     *
     * @param path the path to resolve
     * @return the resolved path
     */
    public static Path decodeUserHome(final String path) {
        if (path != null && path.startsWith("~") && path.length() > 1) {
            return Paths.get(System.getProperty("user.home")).resolve(path.substring(1));
        } else {
            return Paths.get(path);
        }
    }

    public static @Nullable Properties loadProperties(final String propertiesFileName,
            @Nullable final Class<?> classPathRef) throws IOException {
        // 1) try and load from config path
        Path propFile = ConfigurationHelper.lookup(propertiesFileName);
        if (Files.isReadable(propFile)) {
            try (final InputStream pin = Files.newInputStream(propFile)) {
                final Properties properties = new Properties();
                properties.load(pin);
                return properties;
            }
        }

        // 2) try and load from config path set by system property
        propFile = ConfigurationHelper.getFromSystemProperty().map(p -> p.resolveSibling(propertiesFileName)).orElse(null);
        if (propFile != null && Files.isReadable(propFile)) {
            try (final InputStream pin = Files.newInputStream(propFile)) {
                final Properties properties = new Properties();
                properties.load(pin);
                return properties;
            }
        }

        if (classPathRef != null) {
            // 3) try and load from classpath classpathRef.getClassName()/client.properties
            try (final InputStream pin = classPathRef.getResourceAsStream(propertiesFileName)) {
                if (pin != null) {
                    final Properties properties = new Properties();
                    properties.load(pin);
                    return properties;
                }
            }

            // 4) try and load from classpath client.properties
            try (final InputStream pin = classPathRef.getResourceAsStream("/" + propertiesFileName)) {
                if (pin != null) {
                    final Properties properties = new Properties();
                    properties.load(pin);
                    return properties;
                }
            }
        }

        return null;
    }
}
