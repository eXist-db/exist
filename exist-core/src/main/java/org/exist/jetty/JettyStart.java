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
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.Jetty;
import org.eclipse.jetty.util.MultiException;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.resource.PathResource;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.exist.SystemProperties;
import org.exist.http.servlets.ExistExtensionServlet;
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
import java.nio.file.Paths;
import java.util.*;
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
public class JettyStart extends Observable implements LifeCycle.Listener {

    public static final String JETTY_HOME_PROP = "jetty.home";
    public static final String JETTY_BASE_PROP = "jetty.base";

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


    public static void main(final String[] args) {
        try {
            CommandLineParser
                    .withArguments(jettyConfigFilePath, existConfigFilePath)
                    .andArguments(helpArg)
                    .programName("startup" + (OSUtil.isWindows() ? ".bat" : ".sh"))
                    .parse(args);

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

        System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.Slf4jLog");

        final Path jettyConfig;
        if (standalone) {
            jettyConfig = Paths.get(jettyProperty).normalize().resolve("etc").resolve(Main.STANDALONE_ENABLED_JETTY_CONFIGS);
        } else {
            jettyConfig = Paths.get(jettyProperty).normalize().resolve("etc").resolve(Main.STANDARD_ENABLED_JETTY_CONFIGS);
        }
        run(new String[] { jettyConfig.toAbsolutePath().toString() }, null);
    }
    
    public synchronized void run(final String[] args, final Observer observer) {
        if (args.length == 0) {
            logger.error("No configuration file specified!");
            return;
        }

        Path jettyConfig = Paths.get(args[0]).normalize();
        boolean configFromClasspath = false;
        if (Files.notExists(jettyConfig)) {
            logger.warn("Configuration file: {} does not exist!", jettyConfig.toAbsolutePath().toString());

            final String jettyConfigFileName = FileUtils.fileName(jettyConfig.getFileName());
            logger.warn("Fallback... searching for configuration file on classpath: {}!etc/{}", getClass().getPackage().getName(), jettyConfigFileName);

            final URL jettyConfigUrl = getClass().getResource("etc/" + jettyConfigFileName);
            if (jettyConfigUrl != null) {
                try {
                    jettyConfig = Paths.get(jettyConfigUrl.toURI()).normalize();
                    configFromClasspath = true;
                } catch (final URISyntaxException e) {
                    logger.error("Unable to retrieve configuration file from classpath: {}", e.getMessage(), e);
                    return;
                }
            } else {
                logger.error("Unable to find configuration file on classpath!");
                return;
            }
        }

        final Map<String, String> configProperties;
        try {
            configProperties = getConfigProperties(jettyConfig.getParent());

            // modify JETTY_HOME and JETTY_BASE properties when running with classpath config
            if (configFromClasspath) {
                final String jettyClasspathHome = jettyConfig.getParent().getParent().toAbsolutePath().toString();
                System.setProperty(JETTY_HOME_PROP, jettyClasspathHome);
                configProperties.put(JETTY_HOME_PROP, jettyClasspathHome);
                configProperties.put(JETTY_BASE_PROP, jettyClasspathHome);
            }

            if (observer != null) {
                addObserver(observer);
            }

            logger.info("Running with Java {} [{} ({}) in {}]",
                System.getProperty("java.version", "(unknown java.version)"),
                System.getProperty("java.vendor", "(unknown java.vendor)"),
                System.getProperty("java.vm.name", "(unknown java.vm.name)"),
                System.getProperty("java.home", "(unknown java.home)")
            );

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

            // configure the database instance
            SingleInstanceConfiguration config;
            if (args.length == 2) {
                config = new SingleInstanceConfiguration(args[1]);
            } else {
                config = new SingleInstanceConfiguration();
            }
            logger.info("Configuring eXist from {}",
                    config.getConfigFilePath()
                        .map(Path::normalize).map(Path::toAbsolutePath).map(Path::toString)
                        .orElse("<UNKNOWN>"));

            BrokerPool.configure(1, 5, config, Optional.ofNullable(observer));

            // register the XMLDB driver
            final Database xmldb = new DatabaseImpl();
            xmldb.setProperty("create-database", "false");
            DatabaseManager.registerDatabase(xmldb);

        } catch (final Exception e) {
            logger.error("configuration error: {}", e.getMessage(), e);
            e.printStackTrace();
            return;
        }

        try {
            // load jetty configurations
            final List<Path> configFiles = getEnabledConfigFiles(jettyConfig);
            final List<Object> configuredObjects = new ArrayList<>();
            XmlConfiguration last = null;
            for(final Path confFile : configFiles) {
                logger.info("[loading jetty configuration : {}]", confFile.toString());
                final Resource resource = new PathResource(confFile);
                final XmlConfiguration configuration = new XmlConfiguration(resource);
                if (last != null) {
                    configuration.getIdMap().putAll(last.getIdMap());
                }
                configuration.getProperties().putAll(configProperties);
                configuredObjects.add(configuration.configure());
                last = configuration;
            }

            // start Jetty
            final Optional<Server> maybeServer = startJetty(configuredObjects);
            if(!maybeServer.isPresent()) {
                logger.error("Unable to find a server to start in jetty configurations");
                throw new IllegalStateException();
            }

            final Server server = maybeServer.get();

            final Connector[] connectors = server.getConnectors();

            // Construct description of all ports opened.
            final StringBuilder allPorts = new StringBuilder();

            if (connectors.length > 1) {
                // plural s
                allPorts.append("s");
            }

            boolean establishedPrimaryPort = false;
            for(final Connector connector : connectors) {
                if(connector instanceof NetworkConnector networkConnector) {

                    if(!establishedPrimaryPort) {
                        this.primaryPort = networkConnector.getLocalPort();
                        establishedPrimaryPort = true;
                    }

                    allPorts.append(" ");
                    allPorts.append(networkConnector.getLocalPort());
                }
            }
            
            //*************************************************************
            final List<URI> serverUris = getSeverURIs(server);
            if(!serverUris.isEmpty()) {
                this.primaryPort = serverUris.get(0).getPort();

            }
            logger.info("-----------------------------------------------------");
            logger.info("Server has started, listening on:");
            for(final URI serverUri : serverUris) {
                logger.info("{}", serverUri.resolve("/"));
            }

            logger.info("Configured contexts:");
            final LinkedHashSet<Handler> handlers = getAllHandlers(server.getHandler());
            for (final Handler handler: handlers) {
                
                if (handler instanceof ContextHandler) {
                    final ContextHandler contextHandler = (ContextHandler) handler;
                    logger.info("{} ({})", contextHandler.getContextPath(), contextHandler.getDisplayName());
                }

                if (handler instanceof ServletContextHandler) {
                    final ServletContextHandler contextHandler = (ServletContextHandler) handler;
                    final ServiceLoader<ExistExtensionServlet> services = ServiceLoader.load(ExistExtensionServlet.class);

                    for (ExistExtensionServlet existExtensionServlet : services) {
                        final String pathSpec = existExtensionServlet.getPathSpec();
                        final String contextPath = contextHandler.getContextPath();

                        // Avoid "//" as logged prefix
                        final String normalizedPath = "/".equals(contextPath)
                                ? pathSpec
                                : contextPath + pathSpec;

                        logger.info("{} ({})", normalizedPath, existExtensionServlet.getServletInfo());

                        // Register servlet
                        contextHandler.addServlet(new ServletHolder(existExtensionServlet), pathSpec);
                    }
                }
            }

            logger.info("-----------------------------------------------------");

            setChanged();
            notifyObservers(SIGNAL_STARTED);
            
        } catch (final MultiException e) {

            // Mute the BindExceptions

            boolean hasBindException = false;
            for (final Throwable t : e.getThrowables()) {
                if (t instanceof java.net.BindException) {
                    hasBindException = true;
                    logger.error("----------------------------------------------------------");
                    logger.error("ERROR: Could not bind to port because {}", t.getMessage());
                    logger.error(t.toString());
                    logger.error("----------------------------------------------------------");
                }
            }

            // If it is another error, print stacktrace
            if (!hasBindException) {
                e.printStackTrace();
            }
            setChanged();
            notifyObservers(SIGNAL_ERROR);
            
        } catch (final SocketException e) {
            logger.error("----------------------------------------------------------");
            logger.error("ERROR: Could not bind to port because {}", e.getMessage());
            logger.error(e.toString());
            logger.error("----------------------------------------------------------");
            setChanged();
            notifyObservers(SIGNAL_ERROR);
            
        } catch (final Exception e) {
            e.printStackTrace();
            setChanged();
            notifyObservers(SIGNAL_ERROR);
        }
    }

    private LinkedHashSet<Handler> getAllHandlers(final Handler handler) {
        if(handler instanceof HandlerWrapper handlerWrapper) {
            final LinkedHashSet<Handler> handlers = new LinkedHashSet<>();
            handlers.add(handlerWrapper);
            if(handlerWrapper.getHandler() != null) {
                handlers.addAll(getAllHandlers(handlerWrapper.getHandler()));
            }
            return handlers;

        } else if(handler instanceof HandlerContainer handlerContainer) {
            final LinkedHashSet<Handler> handlers = new LinkedHashSet<>();
            handlers.add(handler);
            for(final Handler childHandler : handlerContainer.getChildHandlers()) {
                handlers.addAll(getAllHandlers(childHandler));
            }
            return handlers;

        } else {
            //assuming just Handler
            final LinkedHashSet<Handler> handlers = new LinkedHashSet<>();
            handlers.add(handler);
            return handlers;
        }
    }

    /**
     * See {@link Server#getURI()}
     */
    private List<URI> getSeverURIs(final Server server) {
        final ContextHandler context = server.getChildHandlerByClass(ContextHandler.class);
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
            if (protocol.startsWith("SSL-") || protocol.equals("SSL")) {
                scheme = "https";
            } else {
                scheme = "http";
            }

            String host = null;
            if (context != null && context.getVirtualHosts() != null && context.getVirtualHosts().length > 0) {
                host = context.getVirtualHosts()[0];
            } else {
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
                } catch (final IllegalArgumentException | IllegalStateException e) {
                    // Hook already registered, or Shutdown in progress
                    logger.error("Unable to add BrokerPoolsAndJetty.ShutdownHook hook: {}", e.getMessage(), e);
                    throw e;
                }

                server = Optional.of(_server);
            }

            if (configuredObject instanceof LifeCycle lc) {
                if (!lc.isRunning()) {
                    logger.info("[Starting jetty component : {}]", lc.getClass().getName());
                    lc.start();
                }
            }
        }

        return server;
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
            throw new IOException("Cannot find config enabler: "  + enabledJettyConfigs.toString());
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
                            throw new IOException("Cannot find enabled config: " + configFile.toString());
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
                // ignore
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
                            e.printStackTrace();
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
            }
        }
        return false;
    }

    @Override
    public synchronized void lifeCycleStarting(final LifeCycle lifeCycle) {
        logger.info("Jetty server starting...");
        setChanged();
        notifyObservers(SIGNAL_STARTING);
        status = STATUS_STARTING;
        notifyAll();
    }

    @Override
    public synchronized void lifeCycleStarted(final LifeCycle lifeCycle) {
        logger.info("Jetty server started.");
        setChanged();
        notifyObservers(SIGNAL_STARTED);
        status = STATUS_STARTED;
        notifyAll();
    }

    @Override
    public void lifeCycleFailure(final LifeCycle lifeCycle, final Throwable throwable) {
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
        status = STATUS_STOPPED;
        notifyAll();
    }

    public synchronized int getPrimaryPort() {
        return primaryPort;
    }
}
