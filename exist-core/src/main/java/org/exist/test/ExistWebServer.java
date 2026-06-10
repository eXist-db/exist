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

import static org.junit.Assert.fail;
import static org.exist.repo.AutoDeploymentTrigger.AUTODEPLOY_PROPERTY;

/**
 * JUnit {@link org.junit.rules.ExternalResource} that starts an embedded eXist Jetty server for tests.
 * <p>
 * Prefer {@link org.junit.ClassRule} over {@link org.junit.Rule} when every test method in the class
 * can share one server instance (for example {@code org.exist.http.urlrewrite.ControllerTest}).
 * <p>
 * <strong>Jetty layout ({@code jettyStandaloneMode})</strong>
 * <ul>
 *   <li>{@code true} (default): standalone deploy — single webapp at {@code /} via
 *       {@code exist.jetty.standalone.webapp.dir}. The context must reach
 *       {@link org.eclipse.jetty.server.handler.ContextHandler#isAvailable()} before HTTP clients
 *       are used; {@link org.exist.jetty.JettyStart} blocks until it is ready.</li>
 *   <li>{@code false}: distribution layout — {@code /exist} (main app) plus portal {@code /}.
 *       {@code /exist} must be available; the portal may only need {@code isStarted()}.
 *       Requires {@code exist.jetty.portal.dir} in Maven test configuration (see {@code exist-core/pom.xml}).</li>
 * </ul>
 * <p>
 * <strong>Common system properties</strong> (usually set in module {@code pom.xml} Surefire config):
 * <ul>
 *   <li>{@code exist.jetty.standalone.webapp.dir} — exploded standalone test webapp root</li>
 *   <li>{@code exist.jetty.portal.dir} — portal webapp for distribution-mode tests</li>
 *   <li>{@code jetty.port}, {@code jetty.secure.port}, {@code jetty.ssl.port} — set to {@code 0} when
 *       {@code useRandomPort} is {@code true}, causing the OS to assign ephemeral ports at bind time.
 *       Call {@link #getPort()} after startup to obtain the actual bound port.</li>
 *   <li>{@code jetty.home} — Jetty configuration directory ({@code exist-jetty-config/target/classes/...})</li>
 * </ul>
 * <p>
 * <strong>Note on naming:</strong> {@code useRandomPort} (and the corresponding builder method) are named
 * for historical compatibility. The underlying mechanism is OS ephemeral port allocation (port {@code 0}),
 * not random selection from a range — which eliminates the TOCTOU bind-failure race that affected
 * concurrent {@code forkCount &gt; 1} test runs.
 * Startup failures throw {@link IllegalStateException} with detail from {@link org.exist.jetty.JettyStart}
 * ({@code webAppStartupFailureDetail} is included in the exception message).
 * <p>
 * Prefer {@link #builder()} over the boolean constructor chain for readable test setup.
 */
public class ExistWebServer extends ExternalResource {

    private static final Logger LOG =  LogManager.getLogger(ExistWebServer.class);

    private static final String CONFIG_PROP_FILES = "org.exist.db-connection.files";
    private static final String CONFIG_PROP_JOURNAL_DIR = "org.exist.db-connection.recovery.journal-dir";

    private static final String PROP_JETTY_PORT = "jetty.port";
    private static final String PROP_JETTY_SECURE_PORT = "jetty.secure.port";
    private static final String PROP_JETTY_SSL_PORT = "jetty.ssl.port";

    private JettyStart server = null;
    private String prevAutoDeploy = "off";

    private final boolean useRandomPort;
    private final boolean cleanupDbOnShutdown;
    private final boolean disableAutoDeploy;
    private final boolean useTemporaryStorage;
    private Optional<Path> temporaryStorage = Optional.empty();
    private final boolean jettyStandaloneMode;

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean useRandomPort;
        private boolean cleanupDbOnShutdown;
        private boolean disableAutoDeploy;
        private boolean useTemporaryStorage;
        private boolean jettyStandaloneMode = true;

        public Builder useRandomPort() {
            return useRandomPort(true);
        }

        public Builder useRandomPort(final boolean useRandomPort) {
            this.useRandomPort = useRandomPort;
            return this;
        }

        public Builder cleanupDbOnShutdown() {
            return cleanupDbOnShutdown(true);
        }

        public Builder cleanupDbOnShutdown(final boolean cleanupDbOnShutdown) {
            this.cleanupDbOnShutdown = cleanupDbOnShutdown;
            return this;
        }

        public Builder disableAutoDeploy() {
            return disableAutoDeploy(true);
        }

        public Builder disableAutoDeploy(final boolean disableAutoDeploy) {
            this.disableAutoDeploy = disableAutoDeploy;
            return this;
        }

        public Builder useTemporaryStorage() {
            return useTemporaryStorage(true);
        }

        public Builder useTemporaryStorage(final boolean useTemporaryStorage) {
            this.useTemporaryStorage = useTemporaryStorage;
            return this;
        }

        public Builder jettyStandaloneMode(final boolean jettyStandaloneMode) {
            this.jettyStandaloneMode = jettyStandaloneMode;
            return this;
        }

        public Builder distributionMode() {
            return jettyStandaloneMode(false);
        }

        public ExistWebServer build() {
            return new ExistWebServer(this);
        }
    }

    private ExistWebServer(final Builder builder) {
        this.useRandomPort = builder.useRandomPort;
        this.cleanupDbOnShutdown = builder.cleanupDbOnShutdown;
        this.disableAutoDeploy = builder.disableAutoDeploy;
        this.useTemporaryStorage = builder.useTemporaryStorage;
        this.jettyStandaloneMode = builder.jettyStandaloneMode;
    }

    public ExistWebServer() {
        this(builder());
    }

    public ExistWebServer(final boolean useRandomPort) {
        this(builder().useRandomPort(useRandomPort));
    }

    public ExistWebServer(final boolean useRandomPort, final boolean cleanupDbOnShutdown) {
        this(builder().useRandomPort(useRandomPort).cleanupDbOnShutdown(cleanupDbOnShutdown));
    }

    public ExistWebServer(final boolean useRandomPort, final boolean cleanupDbOnShutdown, final boolean disableAutoDeploy) {
        this(builder().useRandomPort(useRandomPort).cleanupDbOnShutdown(cleanupDbOnShutdown).disableAutoDeploy(disableAutoDeploy));
    }

    public ExistWebServer(final boolean useRandomPort, final boolean cleanupDbOnShutdown, final boolean disableAutoDeploy, final boolean useTemporaryStorage) {
        this(builder().useRandomPort(useRandomPort).cleanupDbOnShutdown(cleanupDbOnShutdown)
                .disableAutoDeploy(disableAutoDeploy).useTemporaryStorage(useTemporaryStorage));
    }

    public ExistWebServer(final boolean useRandomPort, final boolean cleanupDbOnShutdown, final boolean disableAutoDeploy, final boolean useTemporaryStorage, final boolean jettyStandaloneMode) {
        this(builder().useRandomPort(useRandomPort).cleanupDbOnShutdown(cleanupDbOnShutdown)
                .disableAutoDeploy(disableAutoDeploy).useTemporaryStorage(useTemporaryStorage)
                .jettyStandaloneMode(jettyStandaloneMode));
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

            startJettyServer();
        } else {
            throw new IllegalStateException("ExistWebServer already running");
        }
        super.before();
    }

    /**
     * Shuts down and restarts the embedded Jetty server.
     * <p>
     * When {@code useRandomPort} is {@code true}, the restarted server binds to a new OS-assigned
     * ephemeral port. Callers that cached the value of {@link #getPort()} before the restart must
     * re-read it afterwards.
     */
    public void restart() {
        if(server != null) {
            try {
                server.shutdown();
                server.run(jettyStandaloneMode);
                awaitJettyReadyAfterRun();
            } catch (final Exception e) {
                throw new IllegalStateException("Failed to restart ExistWebServer", e);
            }
        } else {
            throw new IllegalStateException("ExistWebServer already stopped");
        }
    }

    @Override
    protected void after() {
        if(server != null) {
            shutdownJettyServer();
            disposeTemporaryStorage();
        } else {
            throw new IllegalStateException("ExistWebServer already stopped");
        }

        if(disableAutoDeploy) {
            //set the autodeploy trigger enablement back to how it was before this test class
            System.setProperty(AUTODEPLOY_PROPERTY, this.prevAutoDeploy);
        }

        super.after();
    }

    private void startJettyServer() {
        if (useRandomPort) {
            System.setProperty(PROP_JETTY_PORT, "0");
            System.setProperty(PROP_JETTY_SECURE_PORT, "0");
            System.setProperty(PROP_JETTY_SSL_PORT, "0");
        }
        server = new JettyStart();
        server.run(jettyStandaloneMode);
        awaitJettyReadyAfterRun();
    }

    private void awaitJettyReadyAfterRun() {
        if (!server.isWebAppStartedSuccessfully()) {
            final String detail = server.getWebAppStartupFailureDetail()
                    .filter(s -> !s.isBlank())
                    .orElse("no startup detail recorded");
            throw new IllegalStateException(
                    "Jetty web application context did not start successfully: " + detail);
        }
    }

    private void shutdownJettyServer() {
        if (cleanupDbOnShutdown) {
            try {
                TestUtils.cleanupDB();
            } catch (final EXistException | PermissionDeniedException | LockException | IOException | TriggerException e) {
                fail(e.getMessage());
            }
        }
        server.shutdown();
        server = null;

        if(useRandomPort) {
            System.clearProperty(PROP_JETTY_SSL_PORT);
            System.clearProperty(PROP_JETTY_SECURE_PORT);
            System.clearProperty(PROP_JETTY_PORT);
        }
    }

    private void disposeTemporaryStorage() {
        if (useTemporaryStorage && temporaryStorage.isPresent()) {
            FileUtils.deleteQuietly(temporaryStorage.get());
            temporaryStorage = Optional.empty();
            System.clearProperty(CONFIG_PROP_JOURNAL_DIR);
            System.clearProperty(CONFIG_PROP_FILES);
        }
    }
}
