/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.launcher;

import org.exist.util.ConfigurationHelper;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.launcher.ConfigurationUtility.LAUNCHER_PROPERTIES_FILE_NAME;

/**
 * A wrapper to start a Java process using start.jar with correct VM settings.
 * Spawns a new Java VM using Ant. Mainly used when launching
 * eXist by double clicking on start.jar.
 *
 * @author Tobi Krebs
 * @author Wolfgang Meier
 */
public class LauncherWrapper {

    private final static String LAUNCHER = org.exist.launcher.Launcher.class.getName();
    private final static String OS = System.getProperty("os.name").toLowerCase();

    public final static void main(String[] args) {
        final LauncherWrapper wrapper = new LauncherWrapper(LAUNCHER);
        if (ConfigurationUtility.isFirstStart()) {
            System.out.println("First launch: opening configuration dialog");
            ConfigurationDialog configDialog = new ConfigurationDialog(restart -> {
                wrapper.launch();
                // make sure the process dies when the dialog is closed
                System.exit(0);
            });
            configDialog.open(true);
            configDialog.requestFocus();
        } else {
            wrapper.launch();
        }
    }

    protected String command;

    public LauncherWrapper(String command) {
        this.command = command;
    }

    public void launch() {
        final String debugLauncher = System.getProperty("exist.debug.launcher", "false");
        final Properties launcherProperties = getLauncherProperties();

        final List<String> args = new ArrayList<>();
        args.add(getJavaCmd());
        getJavaOpts(args, launcherProperties);

        if (Boolean.parseBoolean(debugLauncher) && !"client".equals(command)) {
            args.add("-Xdebug");
            args.add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5006");

            System.out.println("Debug mode for Launcher on JDWP port 5006. Will await connection...");
        }

        // recreate the classpath
        args.add("-cp");
        args.add(getClassPath());

        // call exist main with our new command
        args.add("org.exist.start.Main");
        args.add(command);

        try {
            run(args);
        } catch (final IOException e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Error Running Process", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String getClassPath() {
        // if we are booted using appassembler-booter, then we should use `app.class.path`
        return System.getProperty("app.class.path", System.getProperty("java.class.path"));
    }

    private void run(final List<String> args) throws IOException {
        final StringBuilder buf = new StringBuilder("Executing: [");
        for (int i = 0; i < args.size(); i++) {
            buf.append('"');
            buf.append(args.get(i));
            buf.append('"');
            if (i != args.size() - 1) {
                buf.append(", ");
            }
        }
        buf.append(']');
        System.out.println(buf.toString());

        final ProcessBuilder pb = new ProcessBuilder(args);
        final Optional<Path> home = ConfigurationHelper.getExistHome();
        pb.directory(home.orElse(Paths.get(".")).toFile());
        pb.redirectErrorStream(true);
        pb.inheritIO();

        pb.start();
    }


    protected String getJavaCmd() {
        final File javaHome = new File(System.getProperty("java.home"));
        if (OS.startsWith("windows")) {
            Path javaBin = Paths.get(javaHome.getAbsolutePath(), "bin", "javaw.exe");
            if (Files.isExecutable(javaBin)) {
                return '"' + javaBin.toString() + '"';
            }
            javaBin = Paths.get(javaHome.getAbsolutePath(), "bin", "java.exe");
            if (Files.isExecutable(javaBin)) {
                return '"' + javaBin.toString() + '"';
            }
        } else {
            Path javaBin = Paths.get(javaHome.getAbsolutePath(), "bin", "java");
            if (Files.isExecutable(javaBin)) {
                return javaBin.toString();
            }
        }
        return "java";
    }

    protected void getJavaOpts(final List<String> args, final Properties launcherProperties) {
        getLauncherOpts(args, launcherProperties);

        boolean foundExistHomeSysProp = false;
        final Properties sysProps = System.getProperties();
        for (final Map.Entry<Object, Object> entry : sysProps.entrySet()) {
            final String key = entry.getKey().toString();
            if (key.startsWith("exist.") || key.startsWith("log4j.") || key.startsWith("jetty.") || key.startsWith("app.")) {
                args.add("-D" + key + "=" + entry.getValue().toString());
                if (key.equals("exist.home")) {
                    foundExistHomeSysProp = true;
                }
            }
        }

        if (!foundExistHomeSysProp) {
            args.add("-Dexist.home=\".\"");
        }

        if (command.equals(LAUNCHER) && "mac os x".equals(OS)) {
            args.add("-Dapple.awt.UIElement=true");
        }
    }

    protected void getLauncherOpts(final List<String> args, final Properties launcherProperties) {
        for (final String key : launcherProperties.stringPropertyNames()) {
            if (key.startsWith("memory.")) {
                if ("memory.max".equals(key)) {
                    args.add("-Xmx" + launcherProperties.getProperty(key) + 'm');
                } else if ("memory.min".equals(key)) {
                    args.add("-Xms" + launcherProperties.getProperty(key) + 'm');
                }
            } else if ("vmoptions".equals(key)) {
                args.add(launcherProperties.getProperty(key));
            } else if (key.startsWith("vmoptions.")) {
                final String os = key.substring("vmoptions.".length()).toLowerCase();
                if (OS.contains(os)) {
                    final String value = launcherProperties.getProperty(key);
                    Arrays.stream(value.split("\\s+")).forEach(args::add);
                }
            }
        }
    }

    public static Properties getLauncherProperties() {
        final Properties launcherProperties = new Properties();
        final java.nio.file.Path propFile = ConfigurationUtility.lookup(LAUNCHER_PROPERTIES_FILE_NAME, false);
        InputStream is = null;
        try {
            if (Files.isReadable(propFile)) {
                is = Files.newInputStream(propFile);
            }
            if (is == null) {
                is = LauncherWrapper.class.getResourceAsStream(LAUNCHER_PROPERTIES_FILE_NAME);
            }

            if (is != null) {
                launcherProperties.load(new InputStreamReader(is, UTF_8));
            }
        } catch (final IOException e) {
            System.err.println(LAUNCHER_PROPERTIES_FILE_NAME + " not found");
        } finally {
            if(is != null) {
                try {
                    is.close();
                } catch(final IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        }
        return launcherProperties;
    }
}
