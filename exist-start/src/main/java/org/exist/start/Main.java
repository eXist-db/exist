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
package org.exist.start;

import org.exist.start.classloader.EXistClassLoader;
import org.exist.start.classloader.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * eXist-db bootstrap start helper.
 * <p>
 * The purpose of the class is to setup and configure the (java) environment
 * before the database is actually started, by invoking other classes.
 * <p>
 * The class is designed to have only dependencies with java.* packages.
 * <p>
 * The original class was based on Jetty startup code (Mort Bay Consulting, Apache licenced)
 * but has completely revised and reimplemented over time.
 */
public class Main {

    public static final String CONFIG_DIR_NAME = "etc";
    public static final String ENV_EXIST_HOME = "EXIST_HOME";
    public static final String ENV_EXIST_JETTY_CONFIG = "EXIST_JETTY_CONFIG";
    public static final String ENV_JETTY_HOME = "JETTY_HOME";
    public static final String PROP_EXIST_HOME = "exist.home";
    public static final String PROP_EXIST_JETTY_CONFIG = "exist.jetty.config";
    public static final String PROP_JETTY_HOME = "jetty.home";
    public static final String PROP_LOG4J_DISABLEJMX = "log4j2.disableJmx";
    public static final String PROP_XML_CATALOG_ALWAYS_RESOLVE = "xml.catalog.alwaysResolve";
    public static final String STANDALONE_ENABLED_JETTY_CONFIGS = "standalone.enabled-jetty-configs";
    public static final String STANDARD_ENABLED_JETTY_CONFIGS = "standard.enabled-jetty-configs";
    public static final String MODE_JETTY = "jetty";
    public static final String MODE_STANDALONE = "standalone";
    public static final String MODE_OTHER = "other";
    public static final String MODE_CLIENT = "client";
    public static final String MODE_BACKUP = "backup";
    static final int ERROR_CODE_INCOMPATIBLE_JAVA_DETECTED = 13;
    private static final int ERROR_CODE_GENERAL = 1;
    private static final int ERROR_CODE_NO_JETTY_CONFIG = 7;
    private static final String PROP_EXIST_START_DEBUG = "exist.start.debug";
    private static final String PROP_JAVA_TEMP_DIR = "java.io.tmpdir";
    private static final String PROP_JUL_MANAGER = "java.util.logging.manager";
    private static final String PROP_LOG4J_CONFIGURATION_FILE = "log4j.configurationFile";
    private static Main exist;
    private final boolean inDebugMode = Boolean.getBoolean(PROP_EXIST_START_DEBUG);
    private String _mode = MODE_OTHER;

    private Main() {
    }

    public Main(final String mode) {
        this._mode = mode;
    }

    public static void main(final String[] args) {
        try {
            getMain().run(args);

        } catch (final Exception e) {
            e.printStackTrace();
            System.exit(ERROR_CODE_GENERAL);
        }
    }

    /**
     * Singleton Factory Method
     *
     * @return instance of Main class.
     */
    public static Main getMain() {
        if (exist == null) {
            exist = new Main();
        }
        return exist;
    }

    /**
     * Configures the Log4j2 logging framework by establishing the appropriate logging configuration file
     * and enabling necessary properties to ensure proper logging behavior.
     * <p>
     * This method searches for a Log4j2 configuration file in multiple locations:
     * 1. Directly through a system property.
     * 2. In a specified home directory, if available.
     * <p>
     * It also configures additional system properties, such as setting up a JUL bridge for Log4j2
     * and enabling JMX support.
     *
     * @param existHomeDir an optional path to the eXist-db home directory where the Log4j2 configuration
     *                     file may be located
     */
    private static void setupLog4j2(final Optional<Path> existHomeDir) {

        // Get path from system property
        Optional<Path> log4jConfigurationFile = Optional.ofNullable(System.getProperty(PROP_LOG4J_CONFIGURATION_FILE)).map(Paths::get);

        // Try to find configuration file is not already found.
        if (log4jConfigurationFile.isEmpty()) {

            // Try to find file in eXist-db directory.
            if (existHomeDir.isPresent() && Files.exists(existHomeDir.get().resolve(CONFIG_DIR_NAME))) {
                log4jConfigurationFile = existHomeDir.map(f -> f.resolve(CONFIG_DIR_NAME).resolve("log4j2.xml"));
            }

            // If file was found, update system property.
            if (log4jConfigurationFile.isPresent() && Files.isReadable(log4jConfigurationFile.get())) {
                System.setProperty(PROP_LOG4J_CONFIGURATION_FILE, log4jConfigurationFile.get().toAbsolutePath().toString());
            }
        }

        if (log4jConfigurationFile.isPresent()) {
            // redirect JUL to log4j2 unless otherwise specified
            System.setProperty(PROP_JUL_MANAGER,
                    Optional.ofNullable(System.getProperty(PROP_JUL_MANAGER)).orElse("org.apache.logging.log4j.jul.LogManager"));
        }

        // Enable JMX support log4j since v2.24.0 [2024]
        System.setProperty(PROP_LOG4J_DISABLEJMX, "false");
    }

    private String getMode() {
        return _mode;
    }

    private void setMode(final String mode) {
        _mode = mode;
    }

    public void run(final String[] args) {
        try {
            startExistdb(args);
        } catch (final StartException e) {
            if (e.getMessage() != null && !e.getMessage().isEmpty()) {
                System.err.println(e.getMessage());
            }
            System.exit(e.getErrorCode());
        }
    }

    /**
     * Starts the eXist-db application by initializing required configurations, verifying
     * Java compatibility, setting up logging, and configuring the appropriate runtime environment
     * based on specified modes such as Jetty or standalone.
     * This method resolves relevant arguments and invokes the main class using a custom
     * class loader for bootstrapping the application.
     *
     * @param args an array of strings representing the command-line arguments; the first
     *             argument determines the mode or main class to be invoked, followed by
     *             additional configuration-specific arguments. It may be modified internally
     *             during the execution to adjust for specific configurations.
     * @throws StartException if any error occurs during the startup process such as
     *                        incompatible Java version, misconfiguration, or failure to
     *                        resolve required resources.
     */
    public void startExistdb(String[] args) throws StartException {

        // Check if the OpenJDK version can corrupt eXist-db
        CompatibleJavaVersionCheck.checkForCompatibleJavaVersion();

        final String mainClassName = getClassName(args);

        args = stripFirstElement(args);

        // try and figure out exist home dir
        final Optional<Path> existHomeDir = getFromSysPropOrEnv(PROP_EXIST_HOME, ENV_EXIST_HOME).map(Paths::get);

        // try to find Jetty
        if (MODE_JETTY.equals(getMode()) || MODE_STANDALONE.equals(getMode())) {
            final Path jettyConfig = configureForJetty(existHomeDir);
            args = addFirstElement(args, jettyConfig);
        }

        setupLog4j2(existHomeDir);

        // Modify behavior XML resolver for > 5.x [2024]
        System.setProperty(PROP_XML_CATALOG_ALWAYS_RESOLVE, "false");

        // Clean up tempdir for Jetty...
        tweakTempDirectory();

        // Setup classloader
        final EXistClassLoader eXistClassLoader = ReflectionUtils.getEXistClassLoader();

        // Invoke main class using new classloader.
        try {
            ReflectionUtils.invokeMain(eXistClassLoader, mainClassName, args);
        } catch (final Exception e) {
            e.printStackTrace();
            throw new StartException(ERROR_CODE_GENERAL);
        }
    }

    /**
     * Adds a specified element, represented by the absolute path of the provided `jettyConfig`,
     * as the first element in a new array and appends all elements of the input array to it.
     * This method creates a new array combining the provided `jettyConfig` as the first argument
     * followed by the elements of the given `args` array.
     *
     * @param args an array of strings representing the original arguments; must not be null
     * @param jettyConfig the `Path` object whose absolute path is added as the first element
     *                    in the new array; must not be null
     * @return a new array of strings where the first element is the absolute path of the
     *         given `jettyConfig` and all subsequent elements are from the provided `args` array
     */
    private static String[] addFirstElement(final String[] args, final Path jettyConfig) {
        final String[] jettyStartArgs = new String[1 + args.length];
        jettyStartArgs[0] = jettyConfig.toAbsolutePath().toString();
        System.arraycopy(args, 0, jettyStartArgs, 1, args.length);
        return jettyStartArgs;
    }

    /**
     * Removes the first element from the provided array of strings and returns the resulting array.
     * If the input array has one or no elements, an empty array is returned.
     *
     * @param args an array of strings from which the first element is to be removed;
     *             must not be null, but can be empty
     * @return a new array containing all elements of the input array except the first one;
     *         if the input array contains no elements or only one element, an empty array is returned
     */
    private static String[] stripFirstElement(final String[] args) {
        final String[] newArguments = new String[args.length - 1];
        if (args.length > 1) {
            System.arraycopy(args, 1, newArguments, 0, args.length - 1);
        }
        return newArguments;
    }

    /**
     * Determines and returns the class name to be used based on the provided arguments.
     * The method evaluates the first argument in the provided array and maps it to a
     * corresponding class name. If no arguments are provided or the arguments are invalid,
     * a default class name is returned.
     *
     * @param args an array of strings representing command-line arguments; can be null or empty
     * @return the fully qualified name of the class as a string
     */
    private String getClassName(final String[] args) {

        final String className;

        if (args == null || args.length == 0) {
            className = "org.exist.launcher.LauncherWrapper";
            setMode(MODE_OTHER);

        } else {
            final String firstArgument = args[0];
            switch (firstArgument) {
                case MODE_CLIENT -> {
                    className = "org.exist.client.InteractiveClient";
                    setMode(firstArgument);
                }
                case MODE_BACKUP -> {
                    className = "org.exist.backup.Main";
                    setMode(firstArgument);
                }
                case MODE_JETTY, MODE_STANDALONE -> {
                    className = "org.exist.jetty.JettyStart";
                    setMode(firstArgument);
                }
                case "launch" -> {
                    className = "org.exist.launcher.LauncherWrapper";
                    setMode(MODE_JETTY);
                }
                case "launcher" -> className = "org.exist.launcher.LauncherWrapper";
                case "shutdown" -> className = "org.exist.jetty.ServerShutdown";
                case null, default -> className = firstArgument;
            }
        }

        if (inDebugMode) {
            System.err.println("mode=" + getMode());
        }

        return className;
    }

    /**
     * Adjusts the value of the system property for the temporary directory used by the Java application.
     * This method ensures the value of the temporary directory system property is set to an absolute
     * path if the directory exists, and logs the value if the application is in debug mode.
     * <p>
     * Behavior:
     * - Retrieves the system property for the Java temporary directory.
     * - Converts the property value into an absolute path and verifies if it points to an existing directory.
     * - If the directory exists, the property is updated with the absolute path.
     * - Logs the value of the temporary directory system property to the error stream if debugging is enabled.
     * <p>
     * Exceptions:
     * - Catches and silently ignores {@link InvalidPathException} if the system property contains
     *   an invalid path or cannot be resolved as a valid directory.
     */
    private void tweakTempDirectory() {
        try {
            final Path tmpdir = Paths.get(System.getProperty(PROP_JAVA_TEMP_DIR)).toAbsolutePath();
            if (Files.isDirectory(tmpdir)) {
                System.setProperty(PROP_JAVA_TEMP_DIR, tmpdir.toString());
            }

        } catch (final InvalidPathException e) {
            // ignore
        }

        if (inDebugMode) {
            System.err.println(PROP_JAVA_TEMP_DIR + "=" + System.getProperty(PROP_JAVA_TEMP_DIR));
        }
    }

    /**
     * Configures the system to use the appropriate Jetty configuration file based on the provided
     * eXist-db home directory and other system parameters. The method attempts to resolve the
     * configuration file either from system properties, environment variables, or defaults to a
     * pre-configured standard or standalone setup. If the configuration cannot be located, a
     * {@code StartException} is thrown.
     *
     * @param existHomeDir an optional path to the eXist-db home directory where the configuration
     *                     may be located
     * @return the path to the resolved Jetty configuration file
     * @throws StartException if no valid configuration file can be located
     */
    private Path configureForJetty(final Optional<Path> existHomeDir) throws StartException {

        // Get configured path for Jetty config file
        Optional<Path> existJettyConfigFile = getFromSysPropOrEnv(PROP_EXIST_JETTY_CONFIG, ENV_EXIST_JETTY_CONFIG).map(Paths::get);

        // If configuration was not found
        if (existJettyConfigFile.isEmpty()) {

            // Detect 'Normal Jetty" or "Standalone" modus.
            final String config = MODE_JETTY.equals(getMode())
                    ? STANDARD_ENABLED_JETTY_CONFIGS
                    : STANDALONE_ENABLED_JETTY_CONFIGS;

            // Get path for jetty homedir
            final Optional<Path> jettyHomeDir = getFromSysPropOrEnv(PROP_JETTY_HOME, ENV_JETTY_HOME).map(Paths::get);

            // Load configuration from Jetty directory
            if (jettyHomeDir.isPresent() && Files.exists(jettyHomeDir.get().resolve(CONFIG_DIR_NAME))) {
                existJettyConfigFile = jettyHomeDir.map(f -> f.resolve(CONFIG_DIR_NAME).resolve(config));
            }

            // Load configuration from eXist-db directory
            if (existHomeDir.isPresent() && Files.exists(existHomeDir.get().resolve(CONFIG_DIR_NAME))) {
                existJettyConfigFile = existHomeDir.map(f -> f.resolve(CONFIG_DIR_NAME).resolve(config));
            }

            // Verify that jetty configuration could be found
            if (existJettyConfigFile.isEmpty()) {
                System.err.println("ERROR: jetty config file could not be found! Make sure to set exist.jetty.config or EXIST_JETTY_CONFIG.");
                System.err.flush();
                throw new StartException(ERROR_CODE_NO_JETTY_CONFIG);
            }
        }

        return existJettyConfigFile.get();
    }

    /**
     * Retrieves a value for a given configuration key either from a system property or an environment variable.
     * If the value is found in the environment variable and not in the system property, the method sets the system
     * property with the retrieved value.
     *
     * @param sysPropName the name of the system property to look up
     * @param envVarName the name of the environment variable to look up
     * @return an {@code Optional<String>} containing the retrieved value if found,
     * or an empty {@code Optional} if no value is available
     */
    private Optional<String> getFromSysPropOrEnv(final String sysPropName, final String envVarName) {
        Optional<String> value = Optional.ofNullable(System.getProperty(sysPropName));

        // Not found in system property, try environment
        if (value.isEmpty()) {
            value = Optional.ofNullable(System.getenv().get(envVarName));

            // if we managed to detect from environment, store it in a system property
            value.ifPresent(s -> System.setProperty(sysPropName, s));
        }

        // Report out
        if (inDebugMode && value.isPresent()) {
            System.err.println(sysPropName + "=" + System.getProperty(sysPropName));
        }

        return value;
    }

    /**
     * Shuts down the eXist-db database by invoking internal methods to stop
     * all broker pools associated with the database.
     * <p>
     * This method utilizes reflection to access and call the internal `stopAll`
     * method of the `org.exist.storage.BrokerPools` class. It ensures a clean
     * shutdown by disabling certain features while stopping the broker pools.
     * <p>
     * Exceptions:
     * - Throws {@link StopException} if any error occurs during the reflective
     *   invocation, such as the target class or method being unavailable,
     *   access restrictions, or runtime invocation issues.
     * <p>
     * Note:
     * This method is primarily intended for use in test environments.
     *
     * @throws StopException if the shutdown process encounters an error
     */
    public void shutdownExistdb() throws StopException {
        // only used in test suite
        try {
            final Class<?> brokerPool = Class.forName("org.exist.storage.BrokerPools");
            final Method stopAll = brokerPool.getDeclaredMethod("stopAll", boolean.class);
            stopAll.setAccessible(true);
            stopAll.invoke(null, false);

        } catch (final ClassNotFoundException | NoSuchMethodException | IllegalAccessException |
                       InvocationTargetException e) {
            throw new StopException(e.getMessage(), e);
        }
    }
}
