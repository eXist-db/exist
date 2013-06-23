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

import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Java;
import org.apache.tools.ant.types.Commandline;
import org.exist.util.ConfigurationHelper;

import java.awt.*;
import java.io.*;
import java.util.Map;
import java.util.Properties;

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
        final boolean spawn = SystemTray.isSupported();

        final LauncherWrapper wrapper = new LauncherWrapper(LAUNCHER);
        wrapper.launch();
    }

    protected String command;
    protected File output;

    public LauncherWrapper(String command) {
        this.command = command;
    }

    public void launch() {
        launch(true);
    }

    public void launch(boolean spawn) {
        final String home = System.getProperty("exist.home", ".");
        final Project project = new Project();
        project.setBasedir(home);
        final DefaultLogger logger = new DefaultLogger();
        logger.setOutputPrintStream(System.out);
        logger.setErrorPrintStream(System.err);
        logger.setMessageOutputLevel(Project.MSG_DEBUG);
        project.addBuildListener(logger);

        final Java java = new Java();
        java.setFork(true);
        java.setSpawn(spawn);
        //java.setClassname(org.exist.start.Main.class.getName());
        java.setProject(project);
        java.setJar(new File(home, "start.jar"));
        //Path path = java.createClasspath();
        //path.setPath("start.jar");

        final Commandline.Argument jvmArgs = java.createJvmarg();
        final String javaOpts = getJavaOpts(home);
        jvmArgs.setLine(javaOpts);
        System.out.println("Java opts: " + javaOpts);

        final Commandline.Argument args = java.createArg();
        args.setLine(command);

        java.init();
        java.execute();
    }

    protected String getJavaOpts(String home) {
        final StringBuilder opts = new StringBuilder();

        opts.append(getVMOpts());

        if (command.equals(LAUNCHER) && "mac os x".equals(OS)) {
            opts.append(" -Dapple.awt.UIElement=true");
        }
        opts.append(" -Dexist.home=");
        opts.append('"').append(home).append('"');

        opts.append(" -Djava.endorsed.dirs=");
        opts.append('"').append(home + "/lib/endorsed").append('"');

        return opts.toString();
    }

    protected String getVMOpts() {
        final StringBuilder opts = new StringBuilder();
        Properties vmProperties = getVMProperties();
        for (final Map.Entry<Object, Object> entry : vmProperties.entrySet())  {
            final String key = entry.getKey().toString();
            if (key.startsWith("memory.")) {
                if ("memory.max".equals(key)) {
                    opts.append(" -Xmx").append(entry.getValue()).append('m');
                } else if ("memory.min".equals(key)) {
                    opts.append(" -Xms").append(entry.getValue()).append('m');
                }
            } else if ("vmoptions".equals(key)) {
                opts.append(' ').append(entry.getValue());
            } else if (key.startsWith("vmoptions.")) {
                final String os = key.substring("vmoptions.".length()).toLowerCase();
                if (OS.contains(os)) {
                    opts.append(' ').append(entry.getValue());
                }
            }
        }
        return opts.toString();
    }

    public static Properties getVMProperties() {
        final Properties vmProperties = new Properties();
        final File propFile = ConfigurationHelper.lookup("vm.properties");
        InputStream is = null;
        try {
            if (propFile.canRead()) {
                is = new FileInputStream(propFile);
            }
            if (is == null) {
                is = LauncherWrapper.class.getResourceAsStream("vm.properties");
            }
            if (is != null) {
                vmProperties.load(is);
                is.close();
            }
        } catch (final IOException e) {
            System.err.println("vm.properties not found");
        }
        return vmProperties;
    }
}
