// ========================================================================
// Copyright (c) 2002 Mort Bay Consulting (Australia) Pty. Ltd.
// $Id$
// ========================================================================

/**
 * This is an adopted version of the corresponding classes shipped
 * with Jetty.
 */
package org.exist.start;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import org.exist.storage.BrokerPool;

/**
 * @author Jan Hlavaty (hlavac@code.cz)
 * @author Wolfgang Meier (meier@ifs.tu-darmstadt.de)
 * @version $Revision$
 *          <p/>
 *          TODO:
 *          - finish possible jetty.home locations
 *          - use File.toURI.toURL() on JDK 1.4+
 *          - better handling of errors (i.e. when jetty.home cannot be autodetected...)
 *          - include entries from lib _when needed_
 */

public class Main {
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

    public static void main(String[] args) {
        try {
            getMain().run(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * Singleton Factory Method
     */
    public static Main getMain(){
        if (exist==null) exist = new Main();
        return exist;
    }

    public String getMode() {
        return this._mode;
    }

    private Main() {
    }

    public Main(String mode) {
        this._mode = mode;
    }

    static File getDirectory(String name) {
        try {
            if (name != null) {
                File dir = new File(name).getCanonicalFile();
                if (dir.isDirectory()) {
                    return dir;
                }
            }
        } catch (IOException e) {
        }
        return null;
    }

    boolean isAvailable(String classname, Classpath classpath) {
        try {
            Class.forName(classname);
            return true;
        } catch (ClassNotFoundException e) {
        	//ignore
        }

        ClassLoader loader = classpath.getClassLoader(null);
        try {
            loader.loadClass(classname);
            return true;
        } catch (ClassNotFoundException e) {
        	//ignore
        }

        return false;
    }

    public static void invokeMain(ClassLoader classloader, String classname, String[] args)
            throws IllegalAccessException, InvocationTargetException,
            NoSuchMethodException, ClassNotFoundException {
        Class invoked_class = null;
        invoked_class = classloader.loadClass(classname);
        Class[] method_param_types = new Class[1];
        method_param_types[0] = args.getClass();
        Method main = null;
        main = invoked_class.getDeclaredMethod("main", method_param_types);
        Object[] method_params = new Object[1];
        method_params[0] = args;
        main.invoke(null, method_params);
    }

    void configureClasspath(String home, Classpath classpath, InputStream config, String[] args, String mode) {

        // Any files referenced in start.config that don't exist or cannot be resolved
        // are placed in this list.
        List invalidJars = new ArrayList();

        try {
            BufferedReader cfg = new BufferedReader(new InputStreamReader(config, "ISO-8859-1"));
            Version java_version = new Version(System.getProperty("java.version"));
            Version ver = new Version();

            // JAR's already processed
            Hashtable done = new Hashtable();
            String line = cfg.readLine();
            while (line != null) {
                try {
                    if ((line.length() > 0) && (!line.startsWith("#"))) {
                        if (_debug)
                            System.err.println(">" + line);
                        StringTokenizer st = new StringTokenizer(line);
                        String subject = st.nextToken();
                        boolean include_subject = true;
                        String condition = null;
                        while (include_subject && st.hasMoreTokens()) {
                            condition = st.nextToken();
                            if (condition.equals("never")) {
                                include_subject = false;
                            } else if (condition.equals("always")) {
                            } else if (condition.equals("available")) {
                                String class_to_check = st.nextToken();
                                include_subject &= isAvailable(class_to_check, classpath);
                            } else if (condition.equals("!available")) {
                                String class_to_check = st.nextToken();
                                include_subject &= !isAvailable(class_to_check, classpath);
                            } else if (condition.equals("java")) {
                                String operator = st.nextToken();
                                String version = st.nextToken();
                                ver.parse(version);
                                include_subject
                                        &= (operator.equals("<") && java_version.compare(ver) < 0)
                                        || (operator.equals(">") && java_version.compare(ver) > 0)
                                        || (operator.equals("<=") && java_version.compare(ver) <= 0)
                                        || (operator.equals("=<") && java_version.compare(ver) <= 0)
                                        || (operator.equals("=>") && java_version.compare(ver) >= 0)
                                        || (operator.equals(">=") && java_version.compare(ver) >= 0)
                                        || (operator.equals("==") && java_version.compare(ver) == 0)
                                        || (operator.equals("!=") && java_version.compare(ver) != 0);
                            } else if (condition.equals("nargs")) {
                                String operator = st.nextToken();
                                int number = Integer.parseInt(st.nextToken());
                                include_subject &= (operator.equals("<") && args.length < number)
                                        || (operator.equals(">") && args.length > number)
                                        || (operator.equals("<=") && args.length <= number)
                                        || (operator.equals("=<") && args.length <= number)
                                        || (operator.equals("=>") && args.length >= number)
                                        || (operator.equals(">=") && args.length >= number)
                                        || (operator.equals("==") && args.length == number)
                                        || (operator.equals("!=") && args.length != number);
                            } else if (condition.equals("mode")) {
                                String operator = st.nextToken();
                                String m = st.nextToken();
                                include_subject &= (operator.equals("==") && mode.equals(m))
                                        || (operator.equals("!=") && (!mode.equals(m)));
                            } else {
                                System.err.println("ERROR: Unknown condition: " + condition);
                            }
                        }

                        String file =
                                subject.startsWith("/") ?
                                subject.replace('/', File.separatorChar)
                                : home + File.separatorChar + subject.replace('/', File.separatorChar);

                        if (_debug)
                            System.err.println("subject="
                                    + subject
                                    + " file="
                                    + file
                                    + " condition="
                                    + condition
                                    + " include_subject="
                                    + include_subject);

                        // ok, should we include?
                        if (subject.endsWith("/*")) {
                            // directory of JAR files
                            File extdir = new File(file.substring(0, file.length() - 1));
                            File[] jars = extdir.listFiles(new FilenameFilter() {
                                public boolean accept(File dir, String name) {
                                    String namelc = name.toLowerCase();
                                    return namelc.endsWith(".jar") || name.endsWith(".zip");

                                }
                            });

                            if (jars != null) {
                                for (int i = 0; i < jars.length; i++) {
                                    String jar = jars[i].getCanonicalPath();
                                    if (!done.containsKey(jar)) {
                                        if (include_subject) {
                                            done.put(jar, jar);
                                            if (classpath.addComponent(jar) && _debug)
                                                System.err.println("Adding JAR from directory: " + jar);
                                        }
                                    }
                                }
                            }
                        } else if (subject.endsWith("/")) {
                            // class directory
                            File cd = new File(file);
                            String d = cd.getCanonicalPath();
                            if (!done.containsKey(d)) {
                                done.put(d, d);
                                if (include_subject) {
                                    if (classpath.addComponent(d) && _debug)
                                        System.err.println("Adding directory: " + d);
                                }
                            }
                        } else if (subject.toLowerCase().endsWith(".class")) {
                            // Class
                            _classname = subject.substring(0, subject.length() - 6);
                        } else {
                            // single JAR file
                            String resolvedFile = jarFileResolver.getResolvedFileName(file);

                            File f = new File(resolvedFile);
                            if (include_subject) {
                                if (!f.exists()) {
                                    invalidJars.add(f.getAbsolutePath());
                                }
                            }
                            String d = f.getCanonicalPath();
                            if (!done.containsKey(d)) {
                                if (include_subject) {
                                    done.put(d, d);
                                    if (classpath.addComponent(d) && _debug)
                                        System.err.println("Adding single JAR: " + d);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    if (_debug) {
                        System.err.println(line);
                        e.printStackTrace();
                    }
                }
                line = cfg.readLine();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Print message if any files from start.config were added
        // to the classpath but they could not be found.
        if (invalidJars.size() > 0) {
            Iterator it = invalidJars.iterator();
            StringBuilder nonexistentJars = new StringBuilder();
            while (it.hasNext()) {
                String invalidJar = (String) it.next();
                nonexistentJars.append("    " + invalidJar + "\n");
            }
			/*
            System.err.println(
                "\nWARN: The following JAR file entries from '"
                + startConfigFileName + "' aren't available (this may NOT be a "
                + "problem):\n"
                + nonexistentJars
            );
			*/
        }
    }

    public void run(String[] args) {
        if (args.length > 0) {
            if (args[0].equals("client")) {
                //_classname = "org.exist.client.InteractiveClient";
                _classname = "org.exist.client.InteractiveClient";
                _mode = "client";
            } else if (args[0].equals("standalone")) {
                _classname = "org.exist.StandaloneServer";
                _mode = "standalone";
            } else if (args[0].equals("backup")) {
                _classname = "org.exist.backup.Main";
                _mode = "backup";
            } else if (args[0].equals("jetty")) {
                //_classname = "org.mortbay.jetty.Server";
                _classname = "org.exist.JettyStart";
                _mode = "jetty";
            } else if (args[0].equals("shutdown")) {
                _classname = "org.exist.ServerShutdown";
                _mode = "other";
            } else {
                _classname = args[0];
                _mode = "other";
            }
            String[] nargs = new String[args.length - 1];
            if (args.length > 1)
                System.arraycopy(args, 1, nargs, 0, args.length - 1);
            args = nargs;
        } else {
            _classname = "org.exist.client.InteractiveClient";
            _mode = "client";
        }

        if (_debug) {
            System.err.println("mode = " + _mode);
        }
        File _home_dir = detectHome();

        //TODO: more attempts here...
        
        if (_home_dir != null) {
            // if we managed to detect exist.home, store it in system property
            if (_debug)
                System.err.println("EXIST_HOME=" + System.getProperty("exist.home"));
            // DWES #### can this be removed?
            System.setProperty("exist.home", _home_dir.getPath());
            System.setProperty("user.dir", _home_dir.getPath());

            // try to find Jetty
            if (_mode.equals("jetty") | _mode.equals("cluster")) {
                File _tools_dir = new File(_home_dir.getAbsolutePath() + File.separatorChar + "tools");
                if (!_tools_dir.exists()) {
                    System.err.println("ERROR: tools directory not found in " + _home_dir.getAbsolutePath());
                    return;
                }
                String _jetty_dir = null;
                String _dirs[] = _tools_dir.list();
                for (int i = 0; i < _dirs.length; i++)
                    if (_dirs[i].startsWith("jetty")) {
                        _jetty_dir = _dirs[i];
                        break;
                    }
                if (_jetty_dir == null) {
                    System.err.println("ERROR: Jetty could not be found in " + _tools_dir.getPath());
                    return;
                }
                System.setProperty("jetty.home",
                        _tools_dir.getAbsolutePath() + File.separatorChar + _jetty_dir);
                args =
                        new String[]{
                            System.getProperty("jetty.home")
                        + File.separatorChar
                        + "etc"
                        + File.separatorChar
                        + "jetty.xml"};
            }

            // find log4j.xml
            String log4j = System.getProperty("log4j.configuration");
            if (log4j == null) {
                log4j = _home_dir.getPath() + File.separatorChar + "log4j.xml";
                File lf = new File(log4j);
                if (lf.canRead())
                    System.setProperty("log4j.configuration", lf.toURI().toASCIIString());
            }

            // clean up tempdir for Jetty...
            try {
                File tmpdir = new File(System.getProperty("java.io.tmpdir")).getCanonicalFile();
                if (tmpdir.isDirectory()) {
                    System.setProperty("java.io.tmpdir", tmpdir.getPath());
                }
            } catch (IOException e) {
            }

            Classpath _classpath = constructClasspath(_home_dir, args);
            ClassLoader cl = _classpath.getClassLoader(null);
            Thread.currentThread().setContextClassLoader(cl);

            if (_debug)
                System.err.println("TEMPDIR=" + System.getProperty("java.io.tmpdir"));

            // Invoke org.mortbay.jetty.Server.main(args) using new classloader.
            try {
                invokeMain(cl, _classname, args);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // if not, warn user
            System.err.println("ERROR: exist.home cound not be autodetected, bailing out.");
            System.err.flush();
        }
    }

    /**
     */
    public File detectHome() {
        //--------------------
        // detect exist.home:
        //--------------------
        
        // DWES #### use Configuration.getExistHome() ?
        File _home_dir = getDirectory(System.getProperty("exist.home"));
        if (_home_dir == null) {
            // if eXist is deployed as web application, try to find WEB-INF first
            File webinf = new File("WEB-INF");
            if (_debug)
                System.err.println("trying " + webinf.getAbsolutePath());
            if (webinf.exists()) {
                File jar =
                        new File(webinf.getPath()
                        + File.separatorChar
                        + "lib"
                        + File.separatorChar
                        + "exist.jar");
                if (jar.exists())
                    try {
                        _home_dir = webinf.getCanonicalFile();
                    } catch (IOException e) {
                    }
            }
        }

        if (_home_dir == null) {
            // failed: try exist.jar in current directory
            File jar = new File("exist.jar");
            if (_debug)
                System.err.println("trying " + jar.getAbsolutePath());
            if (jar.canRead()) {
                try {
                    _home_dir = new File(".").getCanonicalFile();
                } catch (IOException e) {
                }
            }
        }

        if (_home_dir == null) {
            // failed: try ../exist.jar
            File jar = new File(".." + File.separatorChar + "exist.jar");
            if (_debug)
                System.err.println("trying " + jar.getAbsolutePath());
            if (jar.exists())
                try {
                    _home_dir = new File("..").getCanonicalFile();
                } catch (IOException e) {
                }
        }

        // searching exist.jar failed, try conf.xml to have the configuration
        // at least
        if (_home_dir == null) {
            // try conf.xml in current dir
            File jar = new File("conf.xml");
            if (_debug)
                System.err.println("trying " + jar.getAbsolutePath());
            if (jar.canRead()) {
                try {
                    _home_dir = new File(".").getCanonicalFile();
                } catch (IOException e) {
                }
            }
        }

        if (_home_dir == null) {
            // try ../conf.xml
            File jar = new File(".." + File.separatorChar + "conf.xml");
            if (_debug)
                System.err.println("trying " + jar.getAbsolutePath());
            if (jar.exists())
                try {
                    _home_dir = new File("..").getCanonicalFile();
                } catch (IOException e) {
                }
        }
        return _home_dir;
    }

    /**
     * @param args
     */
    public Classpath constructClasspath(File homeDir, String[] args) {
        // set up classpath:
        Classpath _classpath = new Classpath();

        // prefill existing paths in classpath_dirs...
        if (_debug)
            System.out.println("existing classpath = " + System.getProperty("java.class.path"));

        _classpath.addClasspath(System.getProperty("java.class.path"));

        // add JARs from ext and lib
        // be smart about it

        try {
            InputStream cpcfg = null;
            // start.config can be found in two locations.
            String configFilePath1 = "";
            String configFilePath2 = "";
            try {
                configFilePath1 = homeDir.getPath() + File.separatorChar
                    + "start.config";
                cpcfg = new java.io.FileInputStream(configFilePath1);
                startConfigFileName = configFilePath1;
            } catch (java.io.FileNotFoundException e) {
                cpcfg = null;
            }
            if (cpcfg == null) {
                if (_debug)
                    System.err.println("Configuring classpath from default resource");

                configFilePath2 = "org/exist/start/start.config";
                cpcfg = getClass().getClassLoader()
                    .getResourceAsStream(configFilePath2);
                startConfigFileName = configFilePath2;
            }
            if (cpcfg == null) {
                throw new RuntimeException(
                    "start.config not found at "
                    + configFilePath1 + " or "
                    + configFilePath2 + ", Bailing out."
                );
            }
            if (_debug) {
            	System.err.println(
            		"Configuring classpath from: " + startConfigFileName
            	);
            }
            configureClasspath(homeDir.getPath(), _classpath, cpcfg, args, _mode);
            cpcfg.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // try to find javac and add it in classpaths
        String java_home = System.getProperty("java.home");
        if (java_home != null) {
            File jdk_home = null;
            try {
                jdk_home = new File(java_home).getParentFile().getCanonicalFile();
            } catch (IOException e) {
            }
            if (jdk_home != null) {
                File tools_jar_file = null;
                try {
                    tools_jar_file =
                            new File(jdk_home, "lib" + File.separator + "tools.jar")
                            .getCanonicalFile();
                } catch (IOException e) {
                }

                if ((tools_jar_file != null) && tools_jar_file.isFile()) {
                    // OK, found tools.jar in java.home/../lib
                    // add it in
                    _classpath.addComponent(tools_jar_file);
                    if (_debug)
                        System.err.println("JAVAC = " + tools_jar_file);
                }
            }
        }

        // okay, classpath complete.
        System.setProperty("java.class.path", _classpath.toString());

        if (_debug)
            System.err.println("CLASSPATH=" + _classpath.toString());
        return _classpath;
    }

    public void shutdown() {
        BrokerPool.stopAll(false);
    }
}
