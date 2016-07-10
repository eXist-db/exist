package org.exist.test;

import org.exist.TestUtils;
import org.exist.jetty.JettyStart;
import org.junit.rules.ExternalResource;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Random;

/**
 * Exist Jetty Web Server Rule to JUnit
 */
public class ExistWebServer extends ExternalResource {
    private final static int MIN_RANDOM_PORT = 49152;
    private final static int MAX_RANDOM_PORT = 65535;
    private final static int MAX_RANDOM_PORT_ATTEMPTS = 10;

    private JettyStart server = null;

    private final Random random = new Random();
    private final boolean useRandomPort;
    private final boolean cleanupDbOnShutdown;

    public ExistWebServer() {
        this(false);
    }

    public ExistWebServer(final boolean useRandomPort) {
        this(useRandomPort, false);
    }

    public ExistWebServer(final boolean useRandomPort, final boolean cleanupDbOnShutdown) {
        this.useRandomPort = useRandomPort;
        this.cleanupDbOnShutdown = cleanupDbOnShutdown;
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
        if (server == null) {
            if(useRandomPort) {
                System.setProperty("jetty.port", Integer.toString(nextFreePort(MIN_RANDOM_PORT, MAX_RANDOM_PORT)));
                System.setProperty("jetty.secure.port", Integer.toString(nextFreePort(MIN_RANDOM_PORT, MAX_RANDOM_PORT)));
                System.setProperty("jetty.ssl.port", Integer.toString(nextFreePort(MIN_RANDOM_PORT, MAX_RANDOM_PORT)));
            }

            server = new JettyStart();
            server.run();
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
                TestUtils.cleanupDB();
            }
            server.shutdown();
            server = null;
        } else {
            throw new IllegalStateException("ExistWebServer already stopped");
        }
        super.after();
    }

    public int nextFreePort(final int from, final int to) {
        for (int attempts = 0; attempts < MAX_RANDOM_PORT_ATTEMPTS; attempts++) {
            final int port = random(from, to);
            if (isLocalPortFree(port)) {
                return port;
            }
        }

        throw new IllegalStateException("Exceeded MAX_RANDOM_PORT_ATTEMPTS");
    }

    private int random(final int min, final int max) {
        return random.nextInt((max - min) + 1) + min;
    }

    private boolean isLocalPortFree(final int port) {
        try {
            new ServerSocket(port).close();
            return true;
        } catch (final IOException e) {
            return false;
        }
    }
}
