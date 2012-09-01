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
        LauncherWrapper wrapper = new LauncherWrapper(LAUNCHER);
        wrapper.launch();
    }

    protected String command;
    protected File output;

    public LauncherWrapper(String command) {
        this.command = command;
    }

    public void launch() {
        String home = System.getProperty("exist.home", ".");
        Project project = new Project();
        project.setBasedir(home);

        DefaultLogger logger = new DefaultLogger();
        logger.setOutputPrintStream(System.out);
        logger.setErrorPrintStream(System.err);
        logger.setMessageOutputLevel(Project.MSG_DEBUG);
        project.addBuildListener(logger);

        Java java = new Java();
        java.setFork(true);
        java.setSpawn(true);
        //java.setClassname(org.exist.start.Main.class.getName());
        java.setProject(project);
        java.setJar(new File(home, "start.jar"));
        //Path path = java.createClasspath();
        //path.setPath("start.jar");

        Commandline.Argument jvmArgs = java.createJvmarg();
        String javaOpts = getJavaOpts(home);
        jvmArgs.setLine(javaOpts);
        System.out.println("Java opts: " + javaOpts);

        Commandline.Argument args = java.createArg();
        args.setLine(command);

        java.init();
        java.execute();
    }

    protected String getJavaOpts(String home) {
        StringBuilder opts = new StringBuilder();

        opts.append(getVMOpts());

        if (command.equals(LAUNCHER) && OS.equals("mac os x")) {
            opts.append(" -Dapple.awt.UIElement=true");
        }
        opts.append(" -Dexist.home=");
        opts.append('"').append(home).append('"');

        opts.append(" -Djava.endorsed.dirs=");
        opts.append('"').append(home + "/lib/endorsed").append('"');

        return opts.toString();
    }

    protected String getVMOpts() {
        StringBuilder opts = new StringBuilder();
        InputStream is = null;
        Properties vmProperties = new Properties();
        File propFile = ConfigurationHelper.lookup("vm.properties");
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
        } catch (IOException e) {
            System.err.println("vm.properties not found");
        }
        for (Map.Entry<Object, Object> entry : vmProperties.entrySet())  {
            String key = entry.getKey().toString();
            if (key.equals("vmoptions")) {
                opts.append(' ').append(entry.getValue());
            } else if (key.startsWith("vmoptions.")) {
                String os = key.substring("vmoptions.".length()).toLowerCase();
                if (OS.contains(os)) {
                    opts.append(' ').append(entry.getValue());
                }
            }
        }
        return opts.toString();
    }
}
