/*
 * NOTE: This file is in part based on code from Mort Bay Consulting.
 * The original license statement is also included below.
 *
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
 *
 * ---------------------------------------------------------------------
 *
 * Copyright 2002-2005 Mort Bay Consulting Pty. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.exist.start;

import org.exist.start.classloader.Classpath;
import org.exist.start.classloader.EXistClassLoader;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * This is an adopted version of the corresponding classes shipped
 * with Jetty. Modified for eXist-db!
 *
 * @author Jan Hlavaty (hlavac@code.cz)
 * @author Wolfgang Meier (meier@ifs.tu-darmstadt.de)
 * @version $Revision$
 *          TODO:
 *          - finish possible jetty.home locations
 *          - better handling of errors (i.e. when jetty.home cannot be autodetected...)
 *          - include entries from lib _when needed_
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
    private String _mode = MODE_JETTY;

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

    private static void invokeMain(final ClassLoader classloader, final String classname, final String[] args)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException, ClassNotFoundException {

        final Class<?> invoked_class = classloader.loadClass(classname);

        final Class<?>[] method_param_types = new Class[1];
        method_param_types[0] = args.getClass();

        final Method main = invoked_class.getDeclaredMethod("main", method_param_types);

        final Object[] method_params = new Object[1];
        method_params[0] = args;
        main.invoke(null, method_params);
    }

    private static EXistClassLoader getEXistClassLoader() {
        final Classpath _classpath = new Classpath();
        final EXistClassLoader eXistClassLoader = _classpath.getClassLoader(null);
        Thread.currentThread().setContextClassLoader(eXistClassLoader);
        return eXistClassLoader;
    }

    private static void setupLog4j2(final Optional<Path> existHomeDir) {
        // find log4j2.xml
        Optional<Path> log4jConfigurationFile = Optional.ofNullable(System.getProperty(PROP_LOG4J_CONFIGURATION_FILE)).map(Paths::get);
        if (log4jConfigurationFile.isEmpty()) {
            if (existHomeDir.isPresent() && Files.exists(existHomeDir.get().resolve(CONFIG_DIR_NAME))) {
                log4jConfigurationFile = existHomeDir.map(f -> f.resolve(CONFIG_DIR_NAME).resolve("log4j2.xml"));
            }

            if (log4jConfigurationFile.isPresent() && Files.isReadable(log4jConfigurationFile.get())) {
                System.setProperty(PROP_LOG4J_CONFIGURATION_FILE, log4jConfigurationFile.get().toAbsolutePath().toString());
            }
        }

        if (log4jConfigurationFile.isPresent()) {
            //redirect JUL to log4j2 unless otherwise specified
            System.setProperty(PROP_JUL_MANAGER, Optional
                    .ofNullable(System.getProperty(PROP_JUL_MANAGER))
                    .orElse("org.apache.logging.log4j.jul.LogManager"));
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

    public void startExistdb(String[] args) throws StartException {

        // Check if the OpenJDK version can corrupt eXist-db
        CompatibleJavaVersionCheck.checkForCompatibleJavaVersion();

        final String _classname;
        if (args.length > 0) {
            _classname = getClassName(args);

        } else {
            _classname = "org.exist.launcher.LauncherWrapper";
            setMode(MODE_OTHER);
        }

        if (inDebugMode) {
            System.err.println("mode=" + getMode());
        }

        // copy all elements except 1st one
        final String[] newArguments = new String[args.length - 1];
        if (args.length > 1) {
            System.arraycopy(args, 1, newArguments, 0, args.length - 1);
        }
        args = newArguments;

        // try and figure out exist home dir
        final Optional<Path> existHomeDir = getFromSysPropOrEnv(PROP_EXIST_HOME, ENV_EXIST_HOME).map(Paths::get);

        // try to find Jetty
        if (MODE_JETTY.equals(getMode()) || MODE_STANDALONE.equals(getMode())) {
            args = configureJetty(args, existHomeDir);
        }

        setupLog4j2(existHomeDir);

        // Modify behavior XML resolver for > 5.x [2024]
        System.setProperty(PROP_XML_CATALOG_ALWAYS_RESOLVE, "false");

        // Clean up tempdir for Jetty...
        tweakTempDirectory();

        // Setup classloader
        final EXistClassLoader eXistClassLoader = getEXistClassLoader();

        // Invoke main class using new classloader.
        try {
            invokeMain(eXistClassLoader, _classname, args);
        } catch (final Exception e) {
            e.printStackTrace();
            throw new StartException(ERROR_CODE_GENERAL);
        }
    }

    private String getClassName(final String[] args) {
        final String className;
        switch (args[0]) {
            case MODE_CLIENT -> {
                className = "org.exist.client.InteractiveClient";
                setMode(MODE_CLIENT);
            }
            case MODE_BACKUP -> {
                className = "org.exist.backup.Main";
                setMode(MODE_BACKUP);
            }
            case MODE_JETTY, MODE_STANDALONE -> {
                className = "org.exist.jetty.JettyStart";
                setMode(args[0]);
            }
            case "launch" -> {
                className = "org.exist.launcher.LauncherWrapper";
                setMode(MODE_JETTY);
            }
            case "launcher" -> {
                className = "org.exist.launcher.LauncherWrapper";
                setMode(MODE_OTHER);
            }
            case "shutdown" -> {
                className = "org.exist.jetty.ServerShutdown";
                setMode(MODE_OTHER);
            }
            case null, default -> {
                className = args[0];
                setMode(MODE_OTHER);
            }
        }
        return className;
    }

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

    private String[] configureJetty(final String[] arguments, final Optional<Path> existHomeDir) throws StartException {
        final Optional<Path> jettyHomeDir = getFromSysPropOrEnv(PROP_JETTY_HOME, ENV_JETTY_HOME).map(Paths::get);

        Optional<Path> existJettyConfigFile = getFromSysPropOrEnv(PROP_EXIST_JETTY_CONFIG, ENV_EXIST_JETTY_CONFIG).map(Paths::get);
        if (existJettyConfigFile.isEmpty()) {
            final String config;
            if (MODE_JETTY.equals(_mode)) {
                config = STANDARD_ENABLED_JETTY_CONFIGS;
            } else {
                config = STANDALONE_ENABLED_JETTY_CONFIGS;
            }

            if (jettyHomeDir.isPresent() && Files.exists(jettyHomeDir.get().resolve(CONFIG_DIR_NAME))) {
                existJettyConfigFile = jettyHomeDir.map(f -> f.resolve(CONFIG_DIR_NAME).resolve(config));
            }

            if (existHomeDir.isPresent() && Files.exists(existHomeDir.get().resolve(CONFIG_DIR_NAME))) {
                existJettyConfigFile = existHomeDir.map(f -> f.resolve(CONFIG_DIR_NAME).resolve(config));
            }

            if (existJettyConfigFile.isEmpty()) {
                System.err.println("ERROR: jetty config file could not be found! Make sure to set exist.jetty.config or EXIST_JETTY_CONFIG.");
                System.err.flush();
                throw new StartException(ERROR_CODE_NO_JETTY_CONFIG);
            }
        }

        final String[] jettyStartArgs = new String[1 + arguments.length];
        jettyStartArgs[0] = existJettyConfigFile.get().toAbsolutePath().toString();
        System.arraycopy(arguments, 0, jettyStartArgs, 1, arguments.length);
        return jettyStartArgs;
    }

    private Optional<String> getFromSysPropOrEnv(final String sysPropName, final String envVarName) {
        Optional<String> value = Optional.ofNullable(System.getProperty(sysPropName));
        if (value.isEmpty()) {
            value = Optional.ofNullable(System.getenv().get(envVarName));
            // if we managed to detect from environment, store it in a system property
            value.ifPresent(s -> System.setProperty(sysPropName, s));
        }

        if (inDebugMode && value.isPresent()) {
            System.err.println(sysPropName + "=" + System.getProperty(sysPropName));
        }

        return value;
    }

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
