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
package org.exist.test;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.start.classloader.Classpath;
import org.exist.start.classloader.EXistClassLoader;
import org.exist.storage.BrokerPool;
import org.exist.storage.journal.Journal;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.FileUtils;
import org.junit.rules.ExternalResource;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.exist.repo.AutoDeploymentTrigger.AUTODEPLOY_PROPERTY;

/**
 * Exist embedded Server Rule for JUnit.
 */
public class ExistEmbeddedServer extends ExternalResource {

    private static final Logger LOG =  LogManager.getLogger(ExistEmbeddedServer.class);

    private final Optional<String> instanceName;
    private final Optional<Path> configFile;
    private final Optional<Properties> configProperties;
    private final boolean useTemporaryStorage;
    private final boolean disableAutoDeploy;
    private Optional<Path> temporaryStorage = Optional.empty();

    private String prevAutoDeploy = "off";
    private BrokerPool pool = null;

    public ExistEmbeddedServer() {
        this(null, null, null, false, false);
    }

    public ExistEmbeddedServer(final boolean useTemporaryStorage) {
        this(null, null, null, false, useTemporaryStorage);
    }

    public ExistEmbeddedServer(final boolean disableAutoDeploy, final boolean useTemporaryStorage) {
        this(null, null, null, disableAutoDeploy, useTemporaryStorage);
    }

    public ExistEmbeddedServer(final Properties configProperties) {
        this(null, null, configProperties, false, false);
    }

    public ExistEmbeddedServer(final Properties configProperties, final boolean disableAutoDeploy, final boolean useTemporaryStorage) {
        this(null, null, configProperties, disableAutoDeploy, useTemporaryStorage);
    }

    public ExistEmbeddedServer(final String instanceName, final Path configFile) {
        this(instanceName, configFile, null, false, false);
    }

    public ExistEmbeddedServer(final String instanceName, final Path configFile, final Properties configProperties) {
        this(instanceName, configFile, configProperties, false, false);
    }

    public ExistEmbeddedServer(final String instanceName, final Path configFile, final Properties configProperties, final boolean disableAutoDeploy) {
        this(instanceName, configFile, configProperties, false, false);
    }

    public ExistEmbeddedServer(@Nullable final String instanceName, @Nullable final Path configFile, @Nullable final Properties configProperties, @Nullable final boolean disableAutoDeploy, @Nullable final boolean useTemporaryStorage) {
        this.instanceName = Optional.ofNullable(instanceName);
        this.configFile = Optional.ofNullable(configFile);
        this.configProperties = Optional.ofNullable(configProperties);
        this.disableAutoDeploy = disableAutoDeploy;
        this.useTemporaryStorage = useTemporaryStorage;

        // setup classloader
        final Classpath _classpath = new Classpath();
        final EXistClassLoader cl = _classpath.getClassLoader(null);
        Thread.currentThread().setContextClassLoader(cl);
    }

    @Override
    protected void before() throws Throwable {
        startDb();
        super.before();
    }

    public void startDb() throws DatabaseConfigurationException, EXistException, IOException {
        if(pool == null) {

            if(disableAutoDeploy) {
                this.prevAutoDeploy = System.getProperty(AUTODEPLOY_PROPERTY, "off");
                System.setProperty(AUTODEPLOY_PROPERTY, "off");
            }

            final String name = instanceName.orElse(BrokerPool.DEFAULT_INSTANCE_NAME);

            final Optional<Path> home = Optional.ofNullable(System.getProperty("exist.home", System.getProperty("user.dir"))).map(Paths::get);
            final Path confFile = configFile.orElseGet(() -> ConfigurationHelper.lookup("conf.xml", home));

            final Configuration config;
            if(confFile.isAbsolute() && Files.exists(confFile)) {
                //TODO(AR) is this correct?
                config = new Configuration(confFile.toAbsolutePath().toString());
            } else {
                config = new Configuration(FileUtils.fileName(confFile), home);
            }

            // override any specified config properties
            configProperties.ifPresent(properties -> {
                for (final Map.Entry<Object, Object> configProperty : properties.entrySet()) {
                    config.setProperty(configProperty.getKey().toString(), configProperty.getValue());
                }
            });

            if (useTemporaryStorage) {
                if (!temporaryStorage.isPresent()) {
                    this.temporaryStorage = Optional.of(Files.createTempDirectory("org.exist.test.ExistEmbeddedServer"));
                }
                config.setProperty(BrokerPool.PROPERTY_DATA_DIR, temporaryStorage.get());
                config.setProperty(Journal.PROPERTY_RECOVERY_JOURNAL_DIR, temporaryStorage.get());
                LOG.info("Using temporary storage location: {}", temporaryStorage.get().toAbsolutePath().toString());
            }

            BrokerPool.configure(name, 1, 5, config, Optional.empty());
            this.pool = BrokerPool.getInstance(name);
        } else {
            throw new IllegalStateException("ExistEmbeddedServer already running");
        }
    }

    public BrokerPool getBrokerPool() {
        return pool;
    }

    public Optional<Path> getTemporaryStorage() {
        return temporaryStorage;
    }

    public void restart() throws EXistException, DatabaseConfigurationException, IOException {
        restart(false);
    }

    public void restart(final boolean clearTemporaryStorage) throws EXistException, DatabaseConfigurationException, IOException {
        if(pool != null) {
            stopDb(clearTemporaryStorage);
            startDb();
        } else {
            throw new IllegalStateException("ExistEmbeddedServer already stopped");
        }
    }

    @Override
    protected void after() {
        stopDb();

        super.after();
    }

    public void stopDb() {
        stopDb(true);
    }

    public void stopDb(final boolean clearTemporaryStorage) {
        if(pool != null) {
            pool.shutdown();

            // clear instance variables
            pool = null;

            if(useTemporaryStorage && temporaryStorage.isPresent() && clearTemporaryStorage) {
                FileUtils.deleteQuietly(temporaryStorage.get());
                temporaryStorage = Optional.empty();
            }

            if(disableAutoDeploy) {
                //set the autodeploy trigger enablement back to how it was before this test class
                System.setProperty(AUTODEPLOY_PROPERTY, this.prevAutoDeploy);
            }

        } else {
            throw new IllegalStateException("ExistEmbeddedServer already stopped");
        }
    }
}
