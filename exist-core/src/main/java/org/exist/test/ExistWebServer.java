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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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

    private static final int SERVER_PROBE_MAX_RETRIES = 30;
    private static final int SERVER_PROBE_MAX_WAIT_SECONDS = 120;

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

        synchronized(ExistWebServer.class) {
            if (server == null) {
                if(useTemporaryStorage) {
                    this.temporaryStorage = Optional.of(Files.createTempDirectory("org.exist.test.ExistWebServer"));
                    final String absTemporaryStorage = temporaryStorage.get().toAbsolutePath().toString();
                    System.setProperty(CONFIG_PROP_FILES, absTemporaryStorage);
                    System.setProperty(CONFIG_PROP_JOURNAL_DIR, absTemporaryStorage);
                    LOG.info("Using temporary storage location: {}", absTemporaryStorage);
                }

                if(useRandomPort) {
                    System.setProperty(PROP_JETTY_PORT, Integer.toString(nextFreePort(MIN_RANDOM_PORT, MAX_RANDOM_PORT, MAX_RANDOM_PORT_ATTEMPTS)));
                    System.setProperty(PROP_JETTY_SECURE_PORT, Integer.toString(nextFreePort(MIN_RANDOM_PORT, MAX_RANDOM_PORT, MAX_RANDOM_PORT_ATTEMPTS)));
                    System.setProperty(PROP_JETTY_SSL_PORT, Integer.toString(nextFreePort(MIN_RANDOM_PORT, MAX_RANDOM_PORT, MAX_RANDOM_PORT_ATTEMPTS)));
                }

                server = new JettyStart();
                server.run(jettyStandaloneMode);
            } else {
                throw new IllegalStateException("ExistWebServer already running");
            }
        }

        final String probeUrl = "http://localhost:" + getPort() + "/exist/rest/db";
        waitForServer(probeUrl, SERVER_PROBE_MAX_RETRIES, SERVER_PROBE_MAX_WAIT_SECONDS);
        super.before();
    }

    /**
     * Probes the given URL until it responds with HTTP 200,
     * giving up after {@code maxRetries} attempts or {@code maxWaitSeconds} total seconds.
     * Uses exponential backoff between retries (starting at 500 ms, doubling each time).
     *
     * @param url            the URL to probe
     * @param maxRetries     maximum number of HTTP probe attempts
     * @param maxWaitSeconds maximum total time to wait in seconds
     * @throws IllegalStateException if the server does not become available in time
     */
    private void waitForServer(final String url, final int maxRetries, final int maxWaitSeconds) {
        final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();

        final long deadline = System.currentTimeMillis() + (maxWaitSeconds * 1000L);
        int attempt = 0;
        long sleepMs = 500;
        while (attempt < maxRetries && System.currentTimeMillis() < deadline) {
            attempt++;
            try {
                final HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 200 || response.statusCode() == 401) {
                    LOG.info("Server available at {} after {} attempt(s)", url, attempt);
                    return;
                }
                LOG.debug("Server probe attempt {}/{} returned HTTP {}", attempt, maxRetries, response.statusCode());
            } catch (final IOException | InterruptedException e) {
                LOG.debug("Server probe attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (attempt < maxRetries && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(sleepMs);
                    sleepMs = Math.min(sleepMs * 2, 5_000);
                } catch (final InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new IllegalStateException(
                "Server at " + url + " did not become available within " + maxRetries + " retries / " + maxWaitSeconds + " seconds");
    }

    public void restart() {
        synchronized(ExistWebServer.class) {
            if(server != null) {
                try {
                    server.shutdown();
                    server.run(jettyStandaloneMode);
                } catch (final Throwable t) {
                    throw new RuntimeException(t);
                }
            } else {
                throw new IllegalStateException("ExistWebServer already stopped");
            }
        }

        final String probeUrl = "http://localhost:" + getPort() + "/exist/rest/db";
        waitForServer(probeUrl, SERVER_PROBE_MAX_RETRIES, SERVER_PROBE_MAX_WAIT_SECONDS);
    }

    @Override
    protected void after() {
        synchronized(ExistWebServer.class) {
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
                    System.clearProperty(PROP_JETTY_SSL_PORT);
                    System.clearProperty(PROP_JETTY_SECURE_PORT);
                    System.clearProperty(PROP_JETTY_PORT);
                }
            } else {
                throw new IllegalStateException("ExistWebServer already stopped");
            }
        }

        if(disableAutoDeploy) {
            //set the autodeploy trigger enablement back to how it was before this test class
            System.setProperty(AUTODEPLOY_PROPERTY, this.prevAutoDeploy);
        }

        super.after();
    }
}
