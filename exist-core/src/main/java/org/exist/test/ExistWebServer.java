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
import org.exist.TestUtils;
import org.exist.collections.triggers.TriggerException;
import org.exist.jetty.JettyStart;
import org.exist.security.PermissionDeniedException;
import org.exist.util.FileUtils;
import org.exist.util.LockException;
import org.junit.rules.ExternalResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.exist.util.IPUtil.nextFreePort;
import static org.junit.Assert.fail;
import static org.exist.repo.AutoDeploymentTrigger.AUTODEPLOY_PROPERTY;

/**
 * Exist Jetty Web Server Rule for JUnit
 */
public class ExistWebServer extends ExternalResource {

    private static final Logger LOG =  LogManager.getLogger(ExistWebServer.class);

    private static final String CONFIG_PROP_FILES = "org.exist.db-connection.files";
    private static final String CONFIG_PROP_JOURNAL_DIR = "org.exist.db-connection.recovery.journal-dir";

    private static final String PROP_JETTY_PORT = "jetty.port";
    private static final String PROP_JETTY_SECURE_PORT = "jetty.secure.port";
    private static final String PROP_JETTY_SSL_PORT = "jetty.ssl.port";

    private static final int MIN_RANDOM_PORT = 49152;
    private static final int MAX_RANDOM_PORT = 65535;
    private static final int MAX_RANDOM_PORT_ATTEMPTS = 10;

    private JettyStart server = null;
    private String prevAutoDeploy = "off";

    private final boolean useRandomPort;
    private final boolean cleanupDbOnShutdown;
    private final boolean disableAutoDeploy;
    private final boolean useTemporaryStorage;
    private Optional<Path> temporaryStorage = Optional.empty();
    private final boolean jettyStandaloneMode;

    public ExistWebServer() {
        this(false);
    }

    public ExistWebServer(final boolean useRandomPort) {
        this(useRandomPort, false);
    }

    public ExistWebServer(final boolean useRandomPort, final boolean cleanupDbOnShutdown) {
        this(useRandomPort, cleanupDbOnShutdown, false);
    }

    public ExistWebServer(final boolean useRandomPort, final boolean cleanupDbOnShutdown, final boolean disableAutoDeploy) {
        this(useRandomPort, cleanupDbOnShutdown, disableAutoDeploy, false);
    }

    public ExistWebServer(final boolean useRandomPort, final boolean cleanupDbOnShutdown, final boolean disableAutoDeploy, final boolean useTemporaryStorage) {
        this(useRandomPort, cleanupDbOnShutdown, disableAutoDeploy, useTemporaryStorage, true);
    }

    public ExistWebServer(final boolean useRandomPort, final boolean cleanupDbOnShutdown, final boolean disableAutoDeploy, final boolean useTemporaryStorage, final boolean jettyStandaloneMode) {
        this.useRandomPort = useRandomPort;
        this.cleanupDbOnShutdown = cleanupDbOnShutdown;
        this.disableAutoDeploy = disableAutoDeploy;
        this.useTemporaryStorage = useTemporaryStorage;
        this.jettyStandaloneMode = jettyStandaloneMode;
    }

    public final int getPort() {
        if(server != null) {
            return server.getPrimaryPort();
        } else {
            throw new IllegalStateException("ExistWebServer is not running");
        }
    }

    @Override
    protected void before() throws Throwable {
        if(disableAutoDeploy) {
            this.prevAutoDeploy = System.getProperty(AUTODEPLOY_PROPERTY, "off");
            System.setProperty(AUTODEPLOY_PROPERTY, "off");
        }

        if (server == null) {
            if(useTemporaryStorage) {
                this.temporaryStorage = Optional.of(Files.createTempDirectory("org.exist.test.ExistWebServer"));
                final String absTemporaryStorage = temporaryStorage.get().toAbsolutePath().toString();
                System.setProperty(CONFIG_PROP_FILES, absTemporaryStorage);
                System.setProperty(CONFIG_PROP_JOURNAL_DIR, absTemporaryStorage);
                LOG.info("Using temporary storage location: {}", absTemporaryStorage);
            }

            if(useRandomPort) {
                synchronized(ExistWebServer.class) {
                    System.setProperty(PROP_JETTY_PORT, Integer.toString(nextFreePort(MIN_RANDOM_PORT, MAX_RANDOM_PORT, MAX_RANDOM_PORT_ATTEMPTS)));
                    System.setProperty(PROP_JETTY_SECURE_PORT, Integer.toString(nextFreePort(MIN_RANDOM_PORT, MAX_RANDOM_PORT, MAX_RANDOM_PORT_ATTEMPTS)));
                    System.setProperty(PROP_JETTY_SSL_PORT, Integer.toString(nextFreePort(MIN_RANDOM_PORT, MAX_RANDOM_PORT, MAX_RANDOM_PORT_ATTEMPTS)));

                    server = new JettyStart();
                    server.run(jettyStandaloneMode);
                }
            } else {
                server = new JettyStart();
                server.run();
            }
        } else {
            throw new IllegalStateException("ExistWebServer already running");
        }
        super.before();
    }

    public void restart() {
        if(server != null) {
            try {
                server.shutdown();
                server.run();
            } catch (final Throwable t) {
                throw new RuntimeException(t);
            }
        } else {
            throw new IllegalStateException("ExistWebServer already stopped");
        }
    }

    @Override
    protected void after() {
        if(server != null) {
            if(cleanupDbOnShutdown) {
                try {
                    TestUtils.cleanupDB();
                } catch (final EXistException | PermissionDeniedException | LockException | IOException | TriggerException e) {
                    fail(e.getMessage());
                }
            }
            server.shutdown();
            server = null;

            if(useTemporaryStorage && temporaryStorage.isPresent()) {
                FileUtils.deleteQuietly(temporaryStorage.get());
                temporaryStorage = Optional.empty();
                System.clearProperty(CONFIG_PROP_JOURNAL_DIR);
                System.clearProperty(CONFIG_PROP_FILES);
            }

            if(useRandomPort) {
                synchronized (ExistWebServer.class) {
                    System.clearProperty(PROP_JETTY_SSL_PORT);
                    System.clearProperty(PROP_JETTY_SECURE_PORT);
                    System.clearProperty(PROP_JETTY_PORT);
                }
            }
        } else {
            throw new IllegalStateException("ExistWebServer already stopped");
        }

        if(disableAutoDeploy) {
            //set the autodeploy trigger enablement back to how it was before this test class
            System.setProperty(AUTODEPLOY_PROPERTY, this.prevAutoDeploy);
        }

        super.after();
    }
}
