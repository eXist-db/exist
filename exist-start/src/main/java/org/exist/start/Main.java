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

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    public static final String STANDARD_ENABLED_JETTY_CONFIGS = "standard.enabled-jetty-configs";
    public static final String STANDALONE_ENABLED_JETTY_CONFIGS = "standalone.enabled-jetty-configs";
    public static final String PROP_LOG4J_DISABLEJMX = "log4j2.disableJmx";
    public static final String PROP_XML_CATALOG_ALWAYS_RESOLVE = "xml.catalog.alwaysResolve";

    private static final int ERROR_CODE_GENERAL = 1;
    private static final int ERROR_CODE_NO_JETTY_CONFIG = 7;
    static final int ERROR_CODE_INCOMPATIBLE_JAVA_DETECTED = 13;

    public static final String CONFIG_DIR_NAME = "etc";

    private static final String PROP_EXIST_START_DEBUG = "exist.start.debug";
    public static final String PROP_EXIST_JETTY_CONFIG = "exist.jetty.config";
    public static final String PROP_EXIST_HOME = "exist.home";
    public static final String PROP_JETTY_HOME = "jetty.home";
    private static final String PROP_LOG4J_CONFIGURATION_FILE = "log4j.configurationFile";
    private static final String PROP_JUL_MANAGER = "java.util.logging.manager";
    private static final String PROP_JAVA_TEMP_DIR = "java.io.tmpdir";

    public static final String ENV_EXIST_JETTY_CONFIG = "EXIST_JETTY_CONFIG";
    public static final String ENV_EXIST_HOME = "EXIST_HOME";
    public static final String ENV_JETTY_HOME = "JETTY_HOME";

    private static Main exist;

    private String _mode = "jetty";
    private boolean _debug = Boolean.getBoolean(PROP_EXIST_START_DEBUG);

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

    public String getMode() {
        return this._mode;
    }

    private Main() {
    }

    public Main(final String mode) {
        this._mode = mode;
    }

    private static Path getDirectory(final String name) {
        try {
            if (name != null) {
                final Path dir = Paths.get(name).normalize().toAbsolutePath();
                if (Files.isDirectory(dir)) {
                    return dir;
                }
            }
        } catch (final InvalidPathException e) {
            // NOP
        }
        return null;
    }

    private static void invokeMain(final ClassLoader classloader, final String classname, final String[] args)
            throws IllegalAccessException, InvocationTargetException,
            NoSuchMethodException, ClassNotFoundException {

        final Class<?> invoked_class = classloader.loadClass(classname);

        final Class<?>[] method_param_types = new Class[1];
        method_param_types[0] = args.getClass();

        final Method main = invoked_class.getDeclaredMethod("main", method_param_types);

        final Object[] method_params = new Object[1];
        method_params[0] = args;
        main.invoke(null, method_params);
    }

    public void run(final String[] args) {
        try {
            runEx(args);
        } catch (final StartException e) {
            if (e.getMessage() != null && !e.getMessage().isEmpty()) {
                System.err.println(e.getMessage());
            }
            System.exit(e.getErrorCode());
        }
    }



    public void runEx(String[] args) throws StartException {

        final String _classname;
        if (args.length > 0) {
            if ("client".equals(args[0])) {
                _classname = "org.exist.client.InteractiveClient";
                _mode = "client";

            } else if ("backup".equals(args[0])) {
                _classname = "org.exist.backup.Main";
                _mode = "backup";

            } else if ("jetty".equals(args[0]) || "standalone".equals(args[0])) {
                _classname = "org.exist.jetty.JettyStart";
                _mode = args[0];

            } else if ("launch".equals(args[0])) {
                _classname = "org.exist.launcher.LauncherWrapper";
                _mode = "jetty";

            } else if ("launcher".equals(args[0])) {
                _classname = "org.exist.launcher.LauncherWrapper";
                _mode = "other";

            } else if ("shutdown".equals(args[0])) {
                _classname = "org.exist.jetty.ServerShutdown";
                _mode = "other";

            } else {
                _classname = args[0];
                _mode = "other";
            }

            final String[] nargs = new String[args.length - 1];
            if (args.length > 1) {
                System.arraycopy(args, 1, nargs, 0, args.length - 1);
            }
            args = nargs;

        } else {
            _classname = "org.exist.launcher.LauncherWrapper";
            _mode = "other";
        }

        if (_debug) {
            System.err.println("mode=" + _mode);
        }

        // try and figure out exist home dir
        final Optional<Path> existHomeDir = getFromSysPropOrEnv(PROP_EXIST_HOME, ENV_EXIST_HOME).map(Paths::get);

        // try to find Jetty
        if ("jetty".equals(_mode) || "standalone".equals(_mode)) {
            final Optional<Path> jettyHomeDir = getFromSysPropOrEnv(PROP_JETTY_HOME, ENV_JETTY_HOME).map(Paths::get);

            Optional<Path> existJettyConfigFile = getFromSysPropOrEnv(PROP_EXIST_JETTY_CONFIG, ENV_EXIST_JETTY_CONFIG).map(Paths::get);
            if (!existJettyConfigFile.isPresent()) {
                final String config;
                if ("jetty".equals(_mode)) {
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

                if (!existJettyConfigFile.isPresent()) {
                    System.err.println("ERROR: jetty config file could not be found! Make sure to set exist.jetty.config or EXIST_JETTY_CONFIG.");
                    System.err.flush();
                    throw new StartException(ERROR_CODE_NO_JETTY_CONFIG);
                }
            }
            final String[] jettyStartArgs = new String[1 + args.length];
            jettyStartArgs[0] = existJettyConfigFile.get().toAbsolutePath().toString();
            System.arraycopy(args, 0, jettyStartArgs, 1, args.length);
            args = jettyStartArgs;
        }

        // find log4j2.xml
        Optional<Path> log4jConfigurationFile = Optional.ofNullable(System.getProperty(PROP_LOG4J_CONFIGURATION_FILE)).map(Paths::get);
        if (!log4jConfigurationFile.isPresent()) {
            if (existHomeDir.isPresent() && Files.exists(existHomeDir.get().resolve(CONFIG_DIR_NAME))) {
                log4jConfigurationFile = existHomeDir.map(f -> f.resolve(CONFIG_DIR_NAME).resolve("log4j2.xml"));
            }

            if (log4jConfigurationFile.isPresent() && Files.isReadable(log4jConfigurationFile.get())) {
                System.setProperty(PROP_LOG4J_CONFIGURATION_FILE, log4jConfigurationFile.get().toAbsolutePath().toString());
            }
        }

        if (log4jConfigurationFile.isPresent()) {
            //redirect JUL to log4j2 unless otherwise specified
            System.setProperty(PROP_JUL_MANAGER, Optional.ofNullable(System.getProperty(PROP_JUL_MANAGER)).orElse("org.apache.logging.log4j.jul.LogManager"));
        }

        // Enable JXM support log4j since v2.24.0 [2024]
        System.setProperty(PROP_LOG4J_DISABLEJMX, "false");

        // Modify behavior XML resolver for > 5.x [2024]
        System.setProperty(PROP_XML_CATALOG_ALWAYS_RESOLVE,"false");

        // clean up tempdir for Jetty...
        try {
            final Path tmpdir = Paths.get(System.getProperty(PROP_JAVA_TEMP_DIR)).toAbsolutePath();
            if (Files.isDirectory(tmpdir)) {
                System.setProperty(PROP_JAVA_TEMP_DIR, tmpdir.toString());
            }

        } catch (final InvalidPathException e) {
            // ignore
        }

        if (_debug) {
            System.err.println(PROP_JAVA_TEMP_DIR + "=" + System.getProperty(PROP_JAVA_TEMP_DIR));
        }

        // setup classloader
        final Classpath _classpath = new Classpath();
        final EXistClassLoader cl = _classpath.getClassLoader(null);
        Thread.currentThread().setContextClassLoader(cl);

        // Invoke main class using new classloader.
        try {
            invokeMain(cl, _classname, args);
        } catch (final Exception e) {
            e.printStackTrace();
            throw new StartException(ERROR_CODE_GENERAL);
        }
    }

    private Optional<String> getFromSysPropOrEnv(final String sysPropName, final String envVarName) {
        Optional<String> value = Optional.ofNullable(System.getProperty(sysPropName));
        if (!value.isPresent()) {
            value = Optional.ofNullable(System.getenv().get(envVarName));
            // if we managed to detect from environment, store it in a system property
            value.ifPresent(s -> System.setProperty(sysPropName, s));
        }

        if (_debug && value.isPresent()) {
            System.err.println(sysPropName + "=" + System.getProperty(sysPropName));
        }

        return value;
    }

    public void shutdown() {
        // only used in test suite
        try {
            shutdownEx();
        } catch (final StopException e) {
            e.printStackTrace();
        }
    }

    public void shutdownEx() throws StopException {
        // only used in test suite
        try {
            final Class<?> brokerPool = Class.forName("org.exist.storage.BrokerPools");
            final Method stopAll = brokerPool.getDeclaredMethod("stopAll", boolean.class);
            stopAll.setAccessible(true);
            stopAll.invoke(null, false);
        } catch (final ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new StopException(e.getMessage(), e);
        }
    }

    /**
     * Copied from {@link org.exist.util.FileUtils#list(Path, Predicate)}
     * as org.exist.start is compiled into a separate Jar and doesn't have
     * the rest of eXist available on the classpath
     */
    static List<Path> list(final Path directory, final Predicate<Path> filter) throws IOException {
        try(final Stream<Path> entries = Files.list(directory).filter(filter)) {
            return entries.collect(Collectors.toList());
        }
    }

    /**
     * Copied from {@link org.exist.util.FileUtils#fileName(Path)}
     * as org.exist.start is compiled into a separate Jar and doesn't have
     * the rest of eXist available on the classpath
     */
    static String fileName(final Path path) {
        return path.getFileName().toString();
    }
}
