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
package org.exist.jetty;

import net.jcip.annotations.GuardedBy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.ee10.webapp.WebAppContext;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.util.Jetty;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.exist.SystemProperties;
import org.exist.http.servlets.ExistExtensionServlet;
import org.exist.start.CompatibleJavaVersionCheck;
import org.exist.start.Main;
import org.exist.start.StartException;
import org.exist.storage.BrokerPool;
import org.exist.util.*;
import org.exist.validation.XmlLibraryChecker;
import org.exist.xmldb.DatabaseImpl;
import org.exist.xmldb.ShutdownListener;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import se.softhouse.jargo.Argument;
import se.softhouse.jargo.ArgumentException;
import se.softhouse.jargo.CommandLineParser;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.exist.util.ThreadUtils.newGlobalThread;
import static se.softhouse.jargo.Arguments.helpArgument;
import static se.softhouse.jargo.Arguments.stringArgument;

/**
 * This class provides a main method to start Jetty with eXist. It registers shutdown
 * handlers to cleanly shut down the database and the webserver.
 *
 * @author wolf
 */
public class JettyStart implements LifeCycle.Listener {

    public static final String JETTY_HOME_PROP = "jetty.home";
    public static final String JETTY_BASE_PROP = "jetty.base";
    public static final String STARTUP_TIMEOUT_MS_PROPERTY = "org.exist.jetty.startup.timeout.ms";

    private static final String EXIST_CONTEXT_PATH = "/exist";
    private static final String PORTAL_CONTEXT_PATH = "/";

    private static final String JETTY_PROPETIES_FILENAME = "jetty.properties";
    private static final Logger logger = LogManager.getLogger(JettyStart.class);

    public final static String SIGNAL_STARTING = "jetty starting";
    public final static String SIGNAL_STARTED = "jetty started";
    public final static String SIGNAL_ERROR = "error";

    private final static int STATUS_STARTING = 0;
    private final static int STATUS_STARTED = 1;
    private final static int STATUS_STOPPING = 2;
    private final static int STATUS_STOPPED = 3;

    /* general arguments */
    private static final Argument<String> jettyConfigFilePath = stringArgument()
            .description("Path to Jetty Config File")
            .build();
    private static final Argument<String> existConfigFilePath = stringArgument()
            .description("Path to eXist-db Config File")
            .build();
    private static final Argument<?> helpArg = helpArgument("-h", "--help");

    @GuardedBy("this") private int status = STATUS_STOPPED;
    @GuardedBy("this") private Optional<Thread> shutdownHookThread = Optional.empty();
    @GuardedBy("this") private int primaryPort = 8080;
    @GuardedBy("this") private boolean webAppStartedSuccessfully = false;
    @GuardedBy("this") private String webAppStartupFailureDetail = null;

    private final CopyOnWriteArrayList<JettyStartListener> jettyStartListeners = new CopyOnWriteArrayList<>();


    public static void main(final String[] args) {
        try {
            CompatibleJavaVersionCheck.checkForCompatibleJavaVersion();

            CommandLineParser
                    .withArguments(jettyConfigFilePath, existConfigFilePath)
                    .andArguments(helpArg)
                    .programName("startup" + (OSUtil.isWindows() ? ".bat" : ".sh"))
                    .parse(args);

        } catch (final StartException e) {
            if (e.getMessage() != null && !e.getMessage().isEmpty()) {
                System.err.println(e.getMessage());
            }
            System.exit(e.getErrorCode());
        } catch (final ArgumentException e) {
            consoleOut(e.getMessageAndUsage().toString());
            System.exit(SystemExitCodes.INVALID_ARGUMENT_EXIT_CODE);
        }

        final JettyStart start = new JettyStart();
        start.run(args, null);
    }

    public JettyStart() {
        // Additional checks XML libs @@@@
        XmlLibraryChecker.check();
    }

    private static void consoleOut(final String msg) {
        System.out.println(msg); //NOSONAR this has to go to the console
    }

    private synchronized void recordStartupFailure(final String detail, final Throwable cause) {
        webAppStartedSuccessfully = false;
        webAppStartupFailureDetail = detail;
        if (cause != null) {
            logger.fatal("Jetty startup failed: {}", detail, cause);
        } else {
            logger.fatal("Jetty startup failed: {}", detail);
        }
    }

    public synchronized void run() {
        run(true);
    }

    public synchronized void run(final boolean standalone) {
        final String jettyProperty = Optional.ofNullable(System.getProperty(JETTY_HOME_PROP))
                .orElseGet(() -> {
                    final Optional<Path> home = ConfigurationHelper.getExistHome();
                    final Path jettyHome = FileUtils.resolve(home, "tools").resolve("jetty");
                    final String jettyPath = jettyHome.toAbsolutePath().toString();
                    System.setProperty(JETTY_HOME_PROP, jettyPath);
                    return jettyPath;
                });

        final Path jettyConfig;
        if (standalone) {
            jettyConfig = Path.of(jettyProperty).normalize().resolve("etc").resolve(Main.STANDALONE_ENABLED_JETTY_CONFIGS);
        } else {
            jettyConfig = Path.of(jettyProperty).normalize().resolve("etc").resolve(Main.STANDARD_ENABLED_JETTY_CONFIGS);
        }
        run(new String[] { jettyConfig.toAbsolutePath().toString() }, null);
    }

    public void addJettyStartListener(final JettyStartListener listener) {
        if (listener != null) {
            jettyStartListeners.addIfAbsent(listener);
        }
    }

    public void removeJettyStartListener(final JettyStartListener listener) {
        if (listener != null) {
            jettyStartListeners.remove(listener);
        }
    }

    private void notifyJettyStartListeners(final String signal) {
        for (final JettyStartListener listener : jettyStartListeners) {
            listener.onJettyStartEvent(signal);
        }
    }

    public synchronized void run(final String[] args, final JettyStartListener listener) {
        if (args.length == 0) {
            logger.error("No configuration file specified!");
            return;
        }

        final Optional<ResolvedJettyConfig> resolvedConfig = resolveJettyConfigPath(args[0]);
        if (resolvedConfig.isEmpty()) {
            return;
        }

        final Map<String, String> configProperties;
        try {
            configProperties = bootstrapExistDb(args, listener, resolvedConfig.get());
        } catch (final Exception e) {
            recordStartupFailure("configuration error: " + e.getMessage(), e);
            return;
        }

        try {
            launchJettyServer(resolvedConfig.get().path(), configProperties);
            webAppStartedSuccessfully = true;
            webAppStartupFailureDetail = null;
            notifyJettyStartListeners(SIGNAL_STARTED);
        } catch (final SocketException e) {
            recordStartupFailure("Could not bind to port: " + e.getMessage(), e);
            notifyJettyStartListeners(SIGNAL_ERROR);
        } catch (final Exception e) {
            if (webAppStartupFailureDetail == null) {
                recordStartupFailure(
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), e);
            } else {
                recordStartupFailure(webAppStartupFailureDetail, e);
            }
            notifyJettyStartListeners(SIGNAL_ERROR);
        }
    }

    private record ResolvedJettyConfig(Path path, boolean fromClasspath) {}

    private Optional<ResolvedJettyConfig> resolveJettyConfigPath(final String configArg) {
        Path jettyConfig = Path.of(configArg).normalize();
        if (Files.exists(jettyConfig)) {
            return Optional.of(new ResolvedJettyConfig(jettyConfig, false));
        }

        logger.warn("Configuration file: {} does not exist!", jettyConfig.toAbsolutePath().toString());

        final String jettyConfigFileName = FileUtils.fileName(jettyConfig.getFileName());
        logger.warn("Fallback... searching for configuration file on classpath: {}!etc/{}",
                getClass().getPackage().getName(), jettyConfigFileName);

        final URL jettyConfigUrl = getClass().getResource("etc/" + jettyConfigFileName);
        if (jettyConfigUrl == null) {
            logger.error("Unable to find configuration file on classpath!");
            return Optional.empty();
        }

        try {
            jettyConfig = Path.of(jettyConfigUrl.toURI()).normalize();
            return Optional.of(new ResolvedJettyConfig(jettyConfig, true));
        } catch (final URISyntaxException e) {
            logger.error("Unable to retrieve configuration file from classpath: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    private Map<String, String> bootstrapExistDb(
            final String[] args,
            final JettyStartListener listener,
            final ResolvedJettyConfig resolvedConfig) throws Exception {
        final Path jettyConfig = resolvedConfig.path();
        final Map<String, String> configProperties = getConfigProperties(jettyConfig.getParent());

        if (resolvedConfig.fromClasspath()) {
            final String jettyClasspathHome = jettyConfig.getParent().getParent().toAbsolutePath().toString();
            System.setProperty(JETTY_HOME_PROP, jettyClasspathHome);
            configProperties.put(JETTY_HOME_PROP, jettyClasspathHome);
            configProperties.put(JETTY_BASE_PROP, jettyClasspathHome);
        }

        if (listener != null) {
            addJettyStartListener(listener);
        }

        logStartupEnvironment(configProperties, jettyConfig);

        final SingleInstanceConfiguration config = args.length == 2
                ? new SingleInstanceConfiguration(args[1])
                : new SingleInstanceConfiguration();
        logger.info("Configuring eXist from {}",
                config.getConfigFilePath()
                        .map(Path::normalize).map(Path::toAbsolutePath).map(Path::toString)
                        .orElse("<UNKNOWN>"));

        final Optional<Observer> brokerPoolObserver = listener instanceof Observer observer
                ? Optional.of(observer)
                : Optional.empty();
        BrokerPool.configure(1, 5, config, brokerPoolObserver);

        final Database xmldb = new DatabaseImpl();
        xmldb.setProperty("create-database", "false");
        DatabaseManager.registerDatabase(xmldb);

        return configProperties;
    }

    private void logStartupEnvironment(final Map<String, String> configProperties, final Path jettyConfig) {
        logger.info("Running with Java {} [{} ({}) in {}]",
                System.getProperty("java.version", "(unknown java.version)"),
                System.getProperty("java.vendor", "(unknown java.vendor)"),
                System.getProperty("java.vm.name", "(unknown java.vm.name)"),
                System.getProperty("java.home", "(unknown java.home)"));

        logger.info("Approximate maximum amount of memory for JVM: {}", FileUtils.humanSize(Runtime.getRuntime().maxMemory()));
        logger.info("Number of processors available to JVM: {}", Runtime.getRuntime().availableProcessors());

        logger.info("Running as user '{}'", System.getProperty("user.name", "(unknown user.name)"));
        logger.info("[eXist Home : {}]", System.getProperty("exist.home", "unknown"));
        logger.info("[eXist Version : {}]", SystemProperties.getInstance().getSystemProperty("product-version", "unknown"));
        logger.info("[eXist Build : {}]", SystemProperties.getInstance().getSystemProperty("product-build", "unknown"));
        logger.info("[Git commit : {}]", SystemProperties.getInstance().getSystemProperty("git-commit", "unknown"));
        logger.info("[Git commit timestamp : {}]", SystemProperties.getInstance().getSystemProperty("git-commit-timestamp", "unknown"));

        logger.info("[Operating System : {} {} {}]", System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"));
        logger.info("[log4j.configurationFile : {}]", System.getProperty("log4j.configurationFile"));
        logger.info("[jetty Version: {}]", Jetty.VERSION);
        logger.info("[{} : {}]", JETTY_HOME_PROP, configProperties.get(JETTY_HOME_PROP));
        logger.info("[{} : {}]", JETTY_BASE_PROP, configProperties.get(JETTY_BASE_PROP));
        logger.info("[jetty configuration : {}]", jettyConfig.toAbsolutePath().toString());
    }

    private void launchJettyServer(final Path jettyConfig, final Map<String, String> configProperties) throws Exception {
        webAppStartupFailureDetail = null;
        webAppStartedSuccessfully = false;

        final List<Object> configuredObjects = loadConfiguredJettyObjects(jettyConfig, configProperties);
        configureWebSocket(configuredObjects);

        final Server server = startJetty(configuredObjects)
                .orElseThrow(() -> {
                    logger.error("Unable to find a server to start in jetty configurations");
                    return new IllegalStateException();
                });

        updatePrimaryPortFromConnectors(server);
        logServerStarted(server);

        final List<Handler> handlers = getAllHandlers(server.getHandler());
        registerExtensionServlets(handlers);

        logger.info("-----------------------------------------------------");
        awaitWebAppContextsStarted(handlers);
    }

    private List<Object> loadConfiguredJettyObjects(
            final Path jettyConfig,
            final Map<String, String> configProperties) throws Exception {
        final List<Path> configFiles = getEnabledConfigFiles(jettyConfig);
        final List<Object> configuredObjects = new ArrayList<>();
        XmlConfiguration last = null;
        for (final Path confFile : configFiles) {
            logger.info("[loading jetty configuration : {}]", confFile.toString());
            final Resource resource = ResourceFactory.root().newResource(confFile);
            final XmlConfiguration configuration = new XmlConfiguration(resource);
            if (last != null) {
                configuration.getIdMap().putAll(last.getIdMap());
            }
            configuration.getProperties().putAll(configProperties);
            configuredObjects.add(configuration.configure());
            last = configuration;
        }
        return configuredObjects;
    }

    private void updatePrimaryPortFromConnectors(final Server server) {
        for (final Connector connector : server.getConnectors()) {
            if (connector instanceof NetworkConnector networkConnector) {
                this.primaryPort = networkConnector.getLocalPort();
                return;
            }
        }
    }

    private void logServerStarted(final Server server) {
        final List<URI> serverUris = getSeverURIs(server);
        if (!serverUris.isEmpty()) {
            this.primaryPort = serverUris.getFirst().getPort();
        }

        logger.info("-----------------------------------------------------");
        logger.info("Server has started, listening on:");
        for (final URI serverUri : serverUris) {
            logger.info("{}", serverUri.resolve("/"));
        }

        logger.info("Configured contexts:");
        for (final Handler handler : getAllHandlers(server.getHandler())) {
            if (handler instanceof ContextHandler contextHandler) {
                logger.info("{} ({})", contextHandler.getContextPath(), contextHandler.getDisplayName());
            }
        }
    }

    private void registerExtensionServlets(final List<Handler> handlers) {
        for (final Handler handler : handlers) {
            if (handler instanceof ServletContextHandler contextHandler) {
                final ServiceLoader<ExistExtensionServlet> services = ServiceLoader.load(ExistExtensionServlet.class);
                for (final ExistExtensionServlet existExtensionServlet : services) {
                    final String pathSpec = existExtensionServlet.getPathSpec();
                    final String contextPath = contextHandler.getContextPath();
                    final String normalizedPath = "/".equals(contextPath)
                            ? pathSpec
                            : contextPath + pathSpec;

                    logger.info("{} ({})", normalizedPath, existExtensionServlet.getServletInfo());
                    contextHandler.addServlet(new ServletHolder(existExtensionServlet), pathSpec);
                }
            }
        }
    }

    private void configureWebSocket(final List<Object> configuredObjects) {
        for (final Object obj : configuredObjects) {
            if (obj instanceof Server server) {
                final List<Handler> handlers = getAllHandlers(server.getHandler());
                for (final Handler handler : handlers) {
                    if (handler instanceof ServletContextHandler sch) {
                        try {
                            org.eclipse.jetty.ee10.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer
                                    .configure(sch, (servletContext, serverContainer) -> {
                                        serverContainer.addEndpoint(
                                                org.exist.xquery.functions.websocket.WebSocketEndpoint.class);
                                        logger.info("[WebSocket endpoint registered: /ws]");
                                        serverContainer.addEndpoint(
                                                org.exist.http.ws.EvalWebSocketEndpoint.class);
                                        logger.info("[WebSocket endpoint registered: /ws/eval]");
                                    });
                            org.exist.xquery.functions.websocket.WebSocketEndpoint.initialize();
                            return; // only need to configure once
                        } catch (final Exception e) {
                            logger.warn("Failed to configure WebSocket endpoint: {}", e.getMessage(), e);
                        }
                    }
                }
            }
        }
    }

    private List<Handler> getAllHandlers(final Handler handler) {
        final List<Handler> handlers = new ArrayList<>();
        handlers.add(handler);

        if (handler instanceof Handler.Wrapper wrapper) {
            if (wrapper.getHandler() != null) {
                handlers.addAll(getAllHandlers(wrapper.getHandler()));
            }
        } else if (handler instanceof Handler.Container container) {
            for (final Handler childHandler : container.getHandlers()) {
                handlers.addAll(getAllHandlers(childHandler));
            }
        }

        return handlers;
    }

    /**
     * See {@link Server#getURI()}
     */
    private List<URI> getSeverURIs(final Server server) {
        final ContextHandler context = server.getHandler() instanceof Handler.Container container
                ? container.getDescendant(ContextHandler.class)
                : null;
        return Arrays.stream(server.getConnectors())
                .filter(connector -> connector instanceof NetworkConnector)
                .map(connector -> (NetworkConnector)connector)
                .map(networkConnector -> getURI(networkConnector, context))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * See {@link Server#getURI()}
     */
    private URI getURI(final NetworkConnector networkConnector, final ContextHandler context) {
        try {
            final String protocol = networkConnector.getDefaultConnectionFactory().getProtocol();
            final String scheme;
            if (protocol.startsWith("SSL-") || "SSL".equals(protocol)) {
                scheme = "https";
            } else {
                scheme = "http";
            }

            String host = null;
            if (context != null) {
                final List<String> virtualHosts = context.getVirtualHosts();
                if (virtualHosts != null && !virtualHosts.isEmpty()) {
                    host = virtualHosts.getFirst();
                }
            }
            if (host == null) {
                host = networkConnector.getHost();
            }

            if (host == null) {
                host = InetAddress.getLocalHost().getHostAddress();
            }

            String path = context == null ? null : context.getContextPath();
            if (path == null) {
                path = "/";
            }
            return new URI(scheme, null, host, networkConnector.getLocalPort(), path, null, null);
        }  catch(final UnknownHostException | URISyntaxException e) {
            logger.warn(e);
            return null;
        }
    }

    private Optional<Server> startJetty(final List<Object> configuredObjects) throws Exception {
        // For all objects created by XmlConfigurations, start them if they are lifecycles.
        Optional<Server> server = Optional.empty();
        for (final Object configuredObject : configuredObjects) {
            if(configuredObject instanceof Server _server) {

                //skip this server if we have already started it
                if(server.map(configuredServer -> configuredServer == _server).orElse(false)) {
                    continue;
                }

                //setup server shutdown
                _server.addEventListener(this);
                BrokerPool.getInstance().registerShutdownListener(new ShutdownListenerImpl(_server));

                // register a shutdown hook for the server
                final BrokerPoolAndJettyShutdownHook brokerPoolAndJettyShutdownHook =
                        new BrokerPoolAndJettyShutdownHook(_server);
                final Thread shutdownHookThread = newGlobalThread("BrokerPoolsAndJetty.ShutdownHook", brokerPoolAndJettyShutdownHook);
                this.shutdownHookThread = Optional.of(shutdownHookThread);

                try {
                    Runtime.getRuntime().addShutdownHook(shutdownHookThread);
                    logger.debug("BrokerPoolsAndJetty.ShutdownHook hook registered");
                    // Avoid C2 race: deregister the default BrokerPools static hook so that only
                    // BrokerPoolsAndJetty.ShutdownHook runs at JVM shutdown. The default hook would
                    // otherwise call BrokerPool.stopAll(true) concurrently with this hook.
                    BrokerPool.deregisterDefaultShutdownHook();
                } catch (final IllegalArgumentException | IllegalStateException e) {
                    // Hook already registered, or Shutdown in progress
                    logger.error("Unable to add BrokerPoolsAndJetty.ShutdownHook hook: {}", e.getMessage(), e);
                    throw e;
                }

                server = Optional.of(_server);
            }

            if (configuredObject instanceof LifeCycle lc && !lc.isRunning()) {
                logger.info("[Starting jetty component : {}]", lc.getClass().getName());
                lc.start();
            }
        }

        return server;
    }

    /**
     * Block until deployed webapps reach the readiness level required for tests.
     * <p>
     * Every context except the distribution portal at {@link #PORTAL_CONTEXT_PATH} must be
     * {@link org.eclipse.jetty.server.handler.ContextHandler#isAvailable()} — Jetty returns
     * {@code 503} on all paths while unavailable. The portal coexists with {@link #EXIST_CONTEXT_PATH} and is
     * non-gating.
     */
    private void awaitWebAppContextsStarted(final List<Handler> handlers) throws InterruptedException {
        final List<WebAppContext> webApps = collectWebAppContexts(handlers);
        if (webApps.isEmpty()) {
            return;
        }

        new WebAppReadinessAwaiter(webApps, isDistributionLayout(webApps))
                .await(slowEnvironmentStartupDeadlineMs());
    }

    private static List<WebAppContext> collectWebAppContexts(final List<Handler> handlers) {
        final List<WebAppContext> webApps = new ArrayList<>();
        for (final Handler handler : handlers) {
            if (handler instanceof WebAppContext webApp) {
                webApps.add(webApp);
            }
        }
        return webApps;
    }

    /**
     * Polls {@link WebAppContext} lifecycle events until all required contexts are ready or a failure occurs.
     */
    private static final class WebAppReadinessAwaiter implements LifeCycle.Listener {

        private final List<WebAppContext> webApps;
        private final boolean distributionLayout;
        private final CountDownLatch readyLatch = new CountDownLatch(1);
        private final AtomicReference<IllegalStateException> failure = new AtomicReference<>();

        WebAppReadinessAwaiter(final List<WebAppContext> webApps, final boolean distributionLayout) {
            this.webApps = webApps;
            this.distributionLayout = distributionLayout;
        }

        void await(final long timeoutMs) throws InterruptedException {
            for (final WebAppContext webApp : webApps) {
                webApp.addEventListener(this);
            }
            try {
                awaitReadyOrThrow(timeoutMs);
            } finally {
                for (final WebAppContext webApp : webApps) {
                    webApp.removeEventListener(this);
                }
            }
        }

        private void awaitReadyOrThrow(final long timeoutMs) throws InterruptedException {
            if (allWebAppsReady(webApps, distributionLayout)) {
                logger.info("All required web application contexts are ready.");
                return;
            }
            throwIfAnyWebAppFailed();
            if (!readyLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw readinessTimeoutException(timeoutMs);
            }
            final IllegalStateException startupFailure = failure.get();
            if (startupFailure != null) {
                throw startupFailure;
            }
            if (!allWebAppsReady(webApps, distributionLayout)) {
                throw readinessIncompleteException();
            }
            logger.info("All required web application contexts are ready.");
        }

        private void throwIfAnyWebAppFailed() {
            for (final WebAppContext webApp : webApps) {
                if (webApp.isFailed()) {
                    throw new IllegalStateException(
                            "Web application failed to start: " + webApp.getContextPath());
                }
            }
        }

        private IllegalStateException readinessTimeoutException(final long timeoutMs) {
            return new IllegalStateException(
                    "Web application context did not become ready within " + timeoutMs + "ms: "
                            + describePendingWebApps(webApps, distributionLayout),
                    firstUnavailableCause(webApps));
        }

        private IllegalStateException readinessIncompleteException() {
            return new IllegalStateException(
                    "Web application context did not become ready: "
                            + describePendingWebApps(webApps, distributionLayout),
                    firstUnavailableCause(webApps));
        }

        @Override
        public void lifeCycleStarted(final LifeCycle event) {
            evaluateReadiness();
        }

        @Override
        public void lifeCycleFailure(final LifeCycle event, final Throwable cause) {
            recordLifecycleFailure(event, cause);
            readyLatch.countDown();
        }

        private void recordLifecycleFailure(final LifeCycle event, final Throwable cause) {
            if (event instanceof WebAppContext webApp) {
                failure.compareAndSet(null, new IllegalStateException(
                        "Web application failed to start: " + webApp.getContextPath(), cause));
            } else {
                failure.compareAndSet(null, new IllegalStateException("Web application failed to start", cause));
            }
        }

        private void evaluateReadiness() {
            if (recordFirstFailedWebApp()) {
                return;
            }
            if (allWebAppsReady(webApps, distributionLayout)) {
                readyLatch.countDown();
            }
        }

        private boolean recordFirstFailedWebApp() {
            for (final WebAppContext webApp : webApps) {
                if (webApp.isFailed()) {
                    failure.compareAndSet(null, new IllegalStateException(
                            "Web application failed to start: " + webApp.getContextPath()));
                    readyLatch.countDown();
                    return true;
                }
            }
            return false;
        }
    }

    private static boolean allWebAppsReady(final List<WebAppContext> webApps, final boolean distributionLayout) {
        for (final WebAppContext webApp : webApps) {
            if (!isWebAppContextReady(webApp, distributionLayout)) {
                return false;
            }
        }
        return true;
    }

    private static Throwable firstUnavailableCause(final List<WebAppContext> webApps) {
        for (final WebAppContext webApp : webApps) {
            final Throwable unavailable = webApp.getUnavailableException();
            if (unavailable != null) {
                return unavailable;
            }
        }
        return null;
    }

    private static boolean isDistributionLayout(final List<WebAppContext> webApps) {
        return webApps.stream().anyMatch(webApp -> EXIST_CONTEXT_PATH.equals(webApp.getContextPath()));
    }

    /**
     * Distribution portal {@link #PORTAL_CONTEXT_PATH} only needs {@code isStarted()}. Standalone
     * {@link #PORTAL_CONTEXT_PATH} and {@link #EXIST_CONTEXT_PATH} must be {@code isAvailable()} or HTTP clients see {@code 503}.
     */
    private static boolean isWebAppContextReady(final WebAppContext webApp, final boolean distributionLayout) {
        if (!webApp.isStarted()) {
            return false;
        }
        if (distributionLayout && PORTAL_CONTEXT_PATH.equals(webApp.getContextPath())) {
            return true;
        }
        return webApp.isAvailable();
    }

    private static String describePendingWebApps(final List<WebAppContext> webApps, final boolean distributionLayout) {
        final StringBuilder details = new StringBuilder();
        for (final WebAppContext webApp : webApps) {
            if (webApp.isFailed()) {
                continue;
            }
            if (!isWebAppContextReady(webApp, distributionLayout)) {
                if (!details.isEmpty()) {
                    details.append("; ");
                }
                details.append(webApp.getContextPath())
                        .append(" started=").append(webApp.isStarted())
                        .append(" available=").append(webApp.isAvailable())
                        .append(" requireAvailable=").append(requiresAvailability(webApp, distributionLayout))
                        .append(" war=").append(describeWebAppWar(webApp));
                final Throwable unavailable = webApp.getUnavailableException();
                if (unavailable != null) {
                    details.append(" unavailableCause=").append(unavailable.getClass().getName())
                            .append(": ").append(unavailable.getMessage());
                }
            }
        }
        return details.isEmpty() ? "unknown" : details.toString();
    }

    private static String describeWebAppWar(final WebAppContext webApp) {
        final String war = webApp.getWar();
        if (war != null && !war.isBlank()) {
            return war;
        }
        try {
            final Resource baseResource = webApp.getBaseResource();
            return baseResource != null ? String.valueOf(baseResource) : "null";
        } catch (final Exception e) {
            return "unresolved(" + e.getMessage() + ")";
        }
    }

    private static boolean requiresAvailability(final WebAppContext webApp, final boolean distributionLayout) {
        return !(distributionLayout && PORTAL_CONTEXT_PATH.equals(webApp.getContextPath()));
    }

    private static long slowEnvironmentStartupDeadlineMs() {
        final String override = System.getProperty(STARTUP_TIMEOUT_MS_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Long.parseLong(override);
        }
        if (System.getenv("CI") != null) {
            return 180_000L;
        }
        return 60_000L;
    }

    private Map<String, String> getConfigProperties(final Path configDir) throws IOException {
        final Map<String, String> configProperties = new HashMap<>();

        //load jetty.properties file
        final Path propertiesFile = configDir.resolve(JETTY_PROPETIES_FILENAME);
        if(Files.exists(propertiesFile)) {
            final Properties jettyProperties = new Properties();
            try(final Reader reader = Files.newBufferedReader(propertiesFile)) {
                jettyProperties.load(reader);
                logger.info("Loaded jetty.properties from: {}", propertiesFile.toAbsolutePath().toString());

                for(final Map.Entry<Object, Object> property : jettyProperties.entrySet()) {
                    configProperties.put(property.getKey().toString(), property.getValue().toString());
                }
            }
        }

        // set or override jetty.home and jetty.base with System properties
        configProperties.put(JETTY_HOME_PROP, System.getProperty(JETTY_HOME_PROP));
        configProperties.put(JETTY_BASE_PROP, System.getProperty(JETTY_BASE_PROP, System.getProperty(JETTY_HOME_PROP)));

        return configProperties;
    }

    private List<Path> getEnabledConfigFiles(final Path enabledJettyConfigs) throws IOException {
        if(Files.notExists(enabledJettyConfigs)) {
            throw new IOException("Cannot find config enabler: "  + enabledJettyConfigs);
        } else {
            final List<Path> configFiles = new ArrayList<>();
            try (final LineNumberReader reader = new LineNumberReader(Files.newBufferedReader(enabledJettyConfigs))) {
                String line = null;
                while ((line = reader.readLine()) != null) {
                    final String tl = line.trim();
                    if (tl.isEmpty() || tl.charAt(0) == '#') {
                        continue;
                    } else {
                        final Path configFile = enabledJettyConfigs.getParent().resolve(tl);
                        if (Files.notExists(configFile)) {
                            throw new IOException("Cannot find enabled config: " + configFile);
                        } else {
                            configFiles.add(configFile);
                        }
                    }
                }
            }
            return configFiles;
        }
    }

    public synchronized void shutdown() {
        shutdownHookThread.ifPresent(thread -> {
            try {
                Runtime.getRuntime().removeShutdownHook(thread);
                logger.debug("BrokerPoolsAndJetty.ShutdownHook hook unregistered");
            } catch (final IllegalStateException e) {
                // Shutdown in progress
                logger.warn("Unable to remove BrokerPoolsAndJetty.ShutdownHook hook: {}", e.getMessage());
            }
        });

        BrokerPool.stopAll(false);

        while (status != STATUS_STOPPED) {
            try {
                wait();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * This class gets called after the database received a shutdown request.
     *
     * @author wolf
     */
    private static class ShutdownListenerImpl implements ShutdownListener {
        private final Server server;

        ShutdownListenerImpl(final Server server) {
            this.server = server;
        }

        @Override
        public void shutdown(final String dbname, final int remainingInstances) {
            logger.info("Database shutdown: stopping server in 1sec ...");
            if (remainingInstances == 0) {
                // give the webserver a 1s chance to complete open requests
                final Timer timer = new Timer("jetty shutdown schedule", true);
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            // stop the server
                            server.stop();
                            server.join();

                            // make sure to stop the timer thread!
                            timer.cancel();
                        } catch (final Exception e) {
                            logger.error("An error occurred in the shutdown scheduler: {}", e.getMessage(), e);
                        }
                    }
                }, 1000); // timer.schedule
            }
        }
    }

    private static class BrokerPoolAndJettyShutdownHook implements Runnable {
        private final Server server;

        BrokerPoolAndJettyShutdownHook(final Server server) {
            this.server = server;
        }

        @Override
        public void run() {
            BrokerPool.stopAll(true);
            if (server.isStopping() || server.isStopped()) {
                return;
            }

            try {
                server.stop();
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized boolean isStarted() {
        if (status == STATUS_STARTED || status == STATUS_STARTING) {
            return true;
        }
        if (status == STATUS_STOPPED) {
            return false;
        }
        while (status != STATUS_STOPPED) {
            try {
                wait();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return false;
    }

    @Override
    public synchronized void lifeCycleStarting(final LifeCycle lifeCycle) {
        logger.info("Jetty server starting...");
        notifyJettyStartListeners(SIGNAL_STARTING);
        status = STATUS_STARTING;
        notifyAll();
    }

    @Override
    public synchronized void lifeCycleStarted(final LifeCycle lifeCycle) {
        logger.info("Jetty server started.");
        notifyJettyStartListeners(SIGNAL_STARTED);
        status = STATUS_STARTED;
        notifyAll();
    }

    @Override
    public void lifeCycleFailure(final LifeCycle lifeCycle, final Throwable throwable) {
        // no-op
    }

    @Override
    public synchronized void lifeCycleStopping(final LifeCycle lifeCycle) {
        logger.info("Jetty server stopping...");
        status = STATUS_STOPPING;
        notifyAll();
    }

    @Override
    public synchronized void lifeCycleStopped(final LifeCycle lifeCycle) {
        logger.info("Jetty server stopped");
        org.exist.xquery.functions.websocket.WebSocketEndpoint.shutdown();
        org.exist.http.ws.EvalWebSocketEndpoint.shutdown();
        status = STATUS_STOPPED;
        notifyAll();
    }

    public synchronized int getPrimaryPort() {
        return primaryPort;
    }

    /**
     * {@code true} when all required {@link WebAppContext} instances finished startup. Used by
     * integration tests to detect swallowed startup failures.
     */
    public synchronized boolean isWebAppStartedSuccessfully() {
        return webAppStartedSuccessfully;
    }

    /**
     * When {@link #isWebAppStartedSuccessfully()} is {@code false}, holds the last startup failure
     * message for test diagnostics (surfaced by {@link org.exist.test.ExistWebServer} in thrown
     * {@link IllegalStateException}s).
     */
    public synchronized Optional<String> getWebAppStartupFailureDetail() {
        return Optional.ofNullable(webAppStartupFailureDetail);
    }
}
