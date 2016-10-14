// ========================================================================
// Copyright (c) 2002 Mort Bay Consulting (Australia) Pty. Ltd.
// $Id$
// ========================================================================

//Modified for eXist-db

/**
 * This is an adopted version of the corresponding classes shipped
 * with Jetty.
 */
package org.exist.start;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Jan Hlavaty (hlavac@code.cz)
 * @author Wolfgang Meier (meier@ifs.tu-darmstadt.de)
 * @version $Revision$
 *          <p/>
 *          TODO:
 *          - finish possible jetty.home locations
 *          - better handling of errors (i.e. when jetty.home cannot be autodetected...)
 *          - include entries from lib _when needed_
 */
public class Main {

    private final static String START_CONFIG = "start.config";

    public static final String STANDARD_ENABLED_JETTY_CONFIGS = "standard.enabled-jetty-configs";
    public static final String STANDALONE_ENABLED_JETTY_CONFIGS = "standalone.enabled-jetty-configs";

    private String _classname = null;
    
    private String _mode = "jetty";

    private static Main exist;

    private boolean _debug = Boolean.getBoolean("exist.start.debug");

    // Stores the path to the "start.config" file that's used to configure
    // the runtime classpath.
    private String startConfigFileName = "";

    // Used to find latest version of jar files that should be added to the
    // classpath.
    private final LatestFileResolver jarFileResolver = new LatestFileResolver();

    public static void main(final String[] args) {
        try {
            getMain().run(args);

        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Singleton Factory Method
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

    static Path getDirectory(final String name) {
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

    boolean isAvailable(final String classname, final Classpath classpath) {
        try {
            Class.forName(classname);
            return true;

        } catch (final ClassNotFoundException e) {
            //ignore
        }

        final ClassLoader loader = classpath.getClassLoader(null);
        try {
            loader.loadClass(classname);
            return true;

        } catch (final ClassNotFoundException e) {
            //ignore
        }

        return false;
    }

    public static void invokeMain(final ClassLoader classloader, final String classname, final String[] args)
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

    void configureClasspath(final Path home, final Classpath classpath, final InputStream config, final String[] args, final String mode) {

        // Any files referenced in start.config that don't exist or cannot be resolved
        // are placed in this list.
        final List<Path> invalidJars = new ArrayList<>();

        try(final BufferedReader cfg = new BufferedReader(new InputStreamReader(config, StandardCharsets.UTF_8))) {
            final Version java_version = new Version(System.getProperty("java.version"));
            final Version ver = new Version();

            // JAR's already processed
            final Set<Path> done = new HashSet<>();

            String line;
            while ((line = cfg.readLine()) != null) {
                try {
                    if ((line.length() > 0) && (!line.startsWith("#"))) {

                        if (_debug) {
                            System.err.println(">" + line);
                        }

                        final StringTokenizer st = new StringTokenizer(line);
                        final String subject = st.nextToken();

                        boolean include_subject = true;
                        String condition = null;

                        while (include_subject && st.hasMoreTokens()) {
                            condition = st.nextToken();
                            if ("never".equals(condition)) {
                                include_subject = false;

                            } else if ("always".equals(condition)) {
                                // ignore

                            } else if ("available".equals(condition)) {
                                final String class_to_check = st.nextToken();
                                include_subject &= isAvailable(class_to_check, classpath);

                            } else if ("!available".equals(condition)) {
                                final String class_to_check = st.nextToken();
                                include_subject &= !isAvailable(class_to_check, classpath);

                            } else if ("java".equals(condition)) {
                                final String operator = st.nextToken();
                                final String version = st.nextToken();
                                ver.parse(version);
                                include_subject &= ("<".equals(operator) && java_version.compare(ver) < 0)
                                        || (">".equals(operator) && java_version.compare(ver) > 0)
                                        || ("<=".equals(operator) && java_version.compare(ver) <= 0)
                                        || ("=<".equals(operator) && java_version.compare(ver) <= 0)
                                        || ("=>".equals(operator) && java_version.compare(ver) >= 0)
                                        || (">=".equals(operator) && java_version.compare(ver) >= 0)
                                        || ("==".equals(operator) && java_version.compare(ver) == 0)
                                        || ("!=".equals(operator) && java_version.compare(ver) != 0);

                            } else if ("nargs".equals(condition)) {
                                final String operator = st.nextToken();
                                final int number = Integer.parseInt(st.nextToken());
                                include_subject &= ("<".equals(operator) && args.length < number)
                                        || (">".equals(operator) && args.length > number)
                                        || ("<=".equals(operator) && args.length <= number)
                                        || ("=<".equals(operator) && args.length <= number)
                                        || ("=>".equals(operator) && args.length >= number)
                                        || (">=".equals(operator) && args.length >= number)
                                        || ("==".equals(operator) && args.length == number)
                                        || ("!=".equals(operator) && args.length != number);

                            } else if ("mode".equals(condition)) {
                                final String operator = st.nextToken();
                                final String m = st.nextToken();
                                include_subject &= ("==".equals(operator) && mode.equals(m))
                                        || ("!=".equals(operator) && (!mode.equals(m)));

                            } else {
                                System.err.println("ERROR: Unknown condition: " + condition);
                            }
                        }

                        final String file =
                                subject.startsWith("/")
                                ? subject.replace('/', File.separatorChar)
                                : home.toAbsolutePath().toString() + File.separatorChar + subject.replace('/', File.separatorChar);


                        if (_debug) {
                            System.err.println("subject=" + subject + " file=" + file
                                    + " condition=" + condition + " include_subject=" + include_subject);
                        }

                        // ok, should we include?
                        if (subject.endsWith("/*")) {
                            // directory of JAR files
                            final Path extdir = Paths.get(file.substring(0, file.length() - 1));
                            final List<Path> jars = list(extdir, p -> fileName(p).toLowerCase().endsWith(".jar") || fileName(p).toLowerCase().endsWith(".zip"));

                            if (jars != null) {
                                for (final Path jarFile : jars) {

                                    final Path canonicalPath = jarFile.toAbsolutePath();
                                    if (!done.contains(canonicalPath)) {
                                        if (include_subject) {
                                            done.add(canonicalPath);
                                            if (classpath.addComponent(canonicalPath) && _debug) {
                                                System.err.println("Adding JAR from directory: " + canonicalPath);
                                            }
                                        }
                                    }

                                }
                            }

                        } else if (subject.endsWith("/")) {
                            // class directory
                            final Path p = Paths.get(file).toAbsolutePath();
                            if (!done.contains(p)) {
                                done.add(p);
                                if (include_subject) {
                                    if (classpath.addComponent(p) && _debug) {
                                        System.err.println("Adding directory: " + p);
                                    }
                                }
                            }

                        } else if (subject.toLowerCase().endsWith(".class")) {
                            // Class
                            _classname = subject.substring(0, subject.length() - 6);

                        } else {
                            // single JAR file
                            final String resolvedFile = jarFileResolver.getResolvedFileName(file);

                            final Path f = Paths.get(resolvedFile);
                            if (include_subject) {
                                if (!Files.exists(f)) {
                                    invalidJars.add(f.toAbsolutePath());
                                }
                            }

                            final Path d = f.toAbsolutePath();
                            if (!done.contains(d)) {
                                if (include_subject) {
                                    done.add(d);
                                    if (classpath.addComponent(d) && _debug) {
                                        System.err.println("Adding single JAR: " + d);
                                    }
                                }
                            }
                        }
                    }

                } catch (final Exception e) {
                    if (_debug) {
                        System.err.println(line);
                        e.printStackTrace();
                    }
                }
            }

        } catch (final Exception e) {
            e.printStackTrace();
        }

        // Print message if any files from start.config were added
        // to the classpath but they could not be found.
//        if (invalidJars.size() > 0) {
//            final StringBuilder nonexistentJars = new StringBuilder();
//            for (final String invalidJar : invalidJars) {
//                nonexistentJars.append("    " + invalidJar + "\n");
//            }
            /*
            System.err.println(
            "\nWARN: The following JAR file entries from '"
            + startConfigFileName + "' aren't available (this may NOT be a "
            + "problem):\n"
            + nonexistentJars
            );
             */
//        }
    }

    public void run(String[] args) {
        if (args.length > 0) {
            if ("client".equals(args[0])) {
                //_classname = "org.exist.client.InteractiveClient";
                _classname = "org.exist.client.InteractiveClient";
                _mode = "client";

            } else if ("backup".equals(args[0])) {
                _classname = "org.exist.backup.Main";
                _mode = "backup";

            } else if ("jetty".equals(args[0]) || "standalone".equals(args[0])) {
                //_classname = "org.mortbay.jetty.Server";
                _classname = "org.exist.jetty.JettyStart";
                _mode = args[0];

            } else if ("launch".equals(args[0])) {
                _classname = "org.exist.launcher.LauncherWrapper";
                _mode = "jetty";

            } else if ("shutdown".equals(args[0])) {
                _classname = "org.exist.jetty.ServerShutdown";
                _mode = "other";

            } else {
                _classname = args[0];
                _mode = "other";
            }

            String[] nargs = new String[args.length - 1];
            if (args.length > 1) {
                System.arraycopy(args, 1, nargs, 0, args.length - 1);
            }
            args = nargs;

        } else {
            _classname = "org.exist.launcher.LauncherWrapper";
            _mode = "other";
        }

        if (_debug) {
            System.err.println("mode = " + _mode);
        }

        final Path _home_dir = detectHome();

        //TODO: more attempts here...

        if (_home_dir != null) {
            // if we managed to detect exist.home, store it in system property
            if (_debug) {
                System.err.println("EXIST_HOME=" + System.getProperty("exist.home"));
            }

            // DWES #### can this be removed?
            System.setProperty("exist.home", _home_dir.toString());
            System.setProperty("user.dir", _home_dir.toString());

            // try to find Jetty
            if ("jetty".equals(_mode) || "standalone".equals(_mode)) {
                if (System.getProperty("jetty.home") == null) {

                    final Path _tools_dir = _home_dir.resolve("tools");
                    if (!Files.exists(_tools_dir)) {
                        System.err.println("ERROR: tools directory not found in " + _home_dir.toAbsolutePath());
                        return;
                    }

                    try {
                        final List<Path> _dirs = list(_tools_dir, p -> Files.isDirectory(p) && fileName(p).startsWith("jetty"));

                        if(_dirs.size() > 0) {
                            System.setProperty("jetty.home", _dirs.get(0).toAbsolutePath().toString());
                        } else {
                            System.err.println("ERROR: Jetty could not be found in " + _tools_dir.toAbsolutePath());
                            return;
                        }
                    } catch(final IOException e) {
                        System.err.println("ERROR: Jetty could not be found in " + _tools_dir.toAbsolutePath());
                        e.printStackTrace();
                        return;
                    }
                }

                final String config;
                if ("jetty".equals(_mode)) {
                    config = STANDARD_ENABLED_JETTY_CONFIGS;
                } else {
                    config = STANDALONE_ENABLED_JETTY_CONFIGS;
                }

                args = new String[]{System.getProperty("jetty.home") + File.separatorChar + "etc"
                            + File.separatorChar + config};
            }

            // find log4j2.xml
            final Path log4j = Optional.ofNullable(System.getProperty("log4j.configurationFile"))
                    .map(Paths::get)
                    .orElseGet(() -> _home_dir.resolve("log4j2.xml"));

            if (Files.isReadable(log4j)) {
                System.setProperty("log4j.configurationFile", log4j.toUri().toASCIIString());
            }

            //redirect JUL to log4j2 unless otherwise specified
            System.setProperty("java.util.logging.manager", Optional.ofNullable(System.getProperty("java.util.logging.manager")).orElse("org.apache.logging.log4j.jul.LogManager"));

            // clean up tempdir for Jetty...
            try {
                final Path tmpdir = Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath();
                if (Files.isDirectory(tmpdir)) {
                    System.setProperty("java.io.tmpdir", tmpdir.toString());
                }

            } catch (final InvalidPathException e) {
                // ignore
            }

            final Classpath _classpath = constructClasspath(_home_dir, args);
            final EXistClassLoader cl = _classpath.getClassLoader(null);
            Thread.currentThread().setContextClassLoader(cl);

            if (_debug) {
                System.err.println("TEMPDIR=" + System.getProperty("java.io.tmpdir"));
            }

            // Invoke org.mortbay.jetty.Server.main(args) using new classloader.
            try {
                invokeMain(cl, _classname, args);

            } catch (final Exception e) {
                e.printStackTrace();
            }

        } else {
            // if not, warn user
            System.err.println("ERROR: exist.home could not be autodetected, bailing out.");
            System.err.flush();
        }
    }

    /**
     */
    public Path detectHome() {
        //--------------------
        // detect exist.home:
        //--------------------

        // DWES #### use Configuration.getExistHome() ?
        Path _home_dir = getDirectory(System.getProperty("exist.home"));
        if (_home_dir == null) {

            // if eXist is deployed as web application, try to find WEB-INF first
            final Path webinf = Paths.get("WEB-INF");
            if (_debug) {
                System.err.println("trying " + webinf.toAbsolutePath());
            }

            if (Files.exists(webinf)) {
                final Path jar = webinf.resolve("lib").resolve("exist.jar");
                if (Files.exists(jar)) {
                    try {
                        _home_dir = webinf.toAbsolutePath();
                    } catch (final InvalidPathException e) {
                        // ignore
                    }
                }
            }
        }

        if (_home_dir == null) {
            // failed: try exist.jar in current directory
            final Path jar = Paths.get("exist.jar");
            if (_debug) {
                System.err.println("trying " + jar.toAbsolutePath());
            }

            if (Files.isReadable(jar)) {
                try {
                    _home_dir = Paths.get(".").normalize().toAbsolutePath();
                } catch (final InvalidPathException e) {
                    // ignore
                }
            }
        }

        if (_home_dir == null) {
            // failed: try ../exist.jar
            final Path jar = Paths.get("..").resolve("exist.jar").normalize();
            if (_debug) {
                System.err.println("trying " + jar.toAbsolutePath());
            }
            if (Files.exists(jar)) {
                try {
                    _home_dir = jar.getParent().toAbsolutePath();
                } catch (final InvalidPathException e) {
                    // ignore
                }
            }
        }

        // searching exist.jar failed, try conf.xml to have the configuration at least
        if (_home_dir == null) {
            // try conf.xml in current dir
            final Path jar = Paths.get("conf.xml");
            if (_debug) {
                System.err.println("trying " + jar.toAbsolutePath());
            }

            if (Files.isReadable(jar)) {
                try {
                    _home_dir = Paths.get(".").toAbsolutePath();
                } catch (final InvalidPathException e) {
                    // ignore
                }
            }
        }

        if (_home_dir == null) {
            // try ../conf.xml
            final Path jar = Paths.get("..").resolve("conf.xml").normalize();
            if (_debug) {
                System.err.println("trying " + jar.toAbsolutePath());
            }

            if (Files.exists(jar)) {
                try {
                    _home_dir = jar.getParent().toAbsolutePath();
                } catch (final InvalidPathException e) {
                    // ignore
                }
            }
        }
        return _home_dir;
    }

    /**
     * @param args
     */
    public Classpath constructClasspath(final Path homeDir, final String[] args) {
        // set up classpath:
        final Classpath _classpath = new Classpath();

        // prefill existing paths in classpath_dirs...
        if (_debug) {
            System.out.println("existing classpath = " + System.getProperty("java.class.path"));
        }

        _classpath.addClasspath(System.getProperty("java.class.path"));

        // add JARs from ext and lib
        // be smart about it

        try {
            final BiConsumer<String, InputStream> configureClasspath = (path, is) -> {
                if (_debug) {
                    System.err.println("Configuring classpath from: " + path);
                }
                configureClasspath(homeDir, _classpath, is, args, _mode);
            };

            // start.config can be found in one of two locations...
            final Path configFilePath1 = homeDir.resolve(START_CONFIG);
            if(Files.exists(configFilePath1)) {
                try(final InputStream cpcfg = Files.newInputStream(configFilePath1)) {
                    final String cfgPath = configFilePath1.toAbsolutePath().toString();
                    configureClasspath.accept(cfgPath, cpcfg);
                    this.startConfigFileName = cfgPath;
                }
            } else {
                if (_debug) {
                    System.err.println("Configuring classpath from default resource");
                }

                final String configFilePath2 = "org/exist/start/" + START_CONFIG;
                final URL configFilePath2Url = getClass().getClassLoader().getResource(configFilePath2);
                if(configFilePath2Url != null) {
                    try(final InputStream cpcfg = getClass().getClassLoader().getResourceAsStream(configFilePath2)) {
                        configureClasspath.accept(configFilePath2, cpcfg);
                        this.startConfigFileName = configFilePath2;
                    }
                } else {
                    throw new RuntimeException(START_CONFIG + " not found at " + configFilePath1 + " or " + configFilePath2 + ", Bailing out.");
                }
            }

        } catch (final IOException e) {
            e.printStackTrace();
        }

        // try to find javac and add it in classpaths
        final String java_home = System.getProperty("java.home");
        if (java_home != null) {
            Path jdk_home = null;
            try {
                jdk_home = Paths.get(java_home).getParent().toAbsolutePath();

            } catch (final InvalidPathException e) {
                // ignore
            }

            if (jdk_home != null) {
                Path tools_jar_file = null;
                try {
                    tools_jar_file = jdk_home.resolve("lib").resolve("tools.jar").toAbsolutePath();
                    
                } catch (final InvalidPathException e) {
                    // ignore
                }

                if ((tools_jar_file != null) && Files.isRegularFile(tools_jar_file)) {
                    // OK, found tools.jar in java.home/../lib
                    // add it in
                    _classpath.addComponent(tools_jar_file);
                    if (_debug) {
                        System.err.println("JAVAC = " + tools_jar_file);
                    }
                }
            }
        }

        // okay, classpath complete.
        System.setProperty("java.class.path", _classpath.toString());

        if (_debug) {
            System.err.println("CLASSPATH=" + _classpath.toString());
        }
        return _classpath;
    }

    public void shutdown() {
        // only used in test suite
        try {
            final Class brokerPool = Class.forName("org.exist.storage.BrokerPools");
            final Method stopAll = brokerPool.getDeclaredMethod("stopAll", boolean.class);
            stopAll.setAccessible(true);
            stopAll.invoke(null, false);
        } catch (final ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
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
