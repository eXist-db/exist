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

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.SystemUtils;
import org.exist.util.ConfigurationHelper;
import org.rzo.yajsw.os.OperatingSystem;
import org.rzo.yajsw.os.Process;
import org.rzo.yajsw.os.ProcessManager;
import org.rzo.yajsw.os.ms.win.w32.WindowsXPProcess;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

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
        wrapper.launch();
    }

    protected String command;

    public LauncherWrapper(String command) {
        this.command = command;
    }

    public void launch() {
        final String home = System.getProperty("exist.home", ".");
        final String debugLauncher = System.getProperty("exist.debug.launcher", "false");
        final PropertiesConfiguration vmProperties = getVMProperties();

        final OperatingSystem os = OperatingSystem.instance();
        final ProcessManager pm = os.processManagerInstance();
        final Process process = pm.createProcess();
        final StringBuilder cmdLine = new StringBuilder()
                .append(getJavaCmd())
                .append(getJavaOpts(home, vmProperties));
        if(Boolean.parseBoolean(debugLauncher)) {
            cmdLine.append(" -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=4000");
        }
        cmdLine.append(" -jar start.jar ");
        cmdLine.append(command);
        process.setWorkingDir(home);
        process.setVisible(false);
        process.setPipeStreams(false, false);
        final String cmdLineStr = cmdLine.toString();
        process.setCommand(cmdLineStr);
        System.out.println(cmdLineStr);

        if (process instanceof WindowsXPProcess) {
            ((WindowsXPProcess)process).startElevated();
        } else {
            process.start();
        }
    }

    protected String getJavaCmd() {
        final File javaHome = SystemUtils.getJavaHome();
        if (SystemUtils.IS_OS_WINDOWS) {
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

    protected String getJavaOpts(String home, PropertiesConfiguration vmProperties) {
        final StringBuilder opts = new StringBuilder();

        opts.append(getVMOpts(vmProperties));

        if (command.equals(LAUNCHER) && "mac os x".equals(OS)) {
            opts.append(" -Dapple.awt.UIElement=true");
        }
        opts.append(" -Dexist.home=");
        opts.append('"').append(home).append('"');

        opts.append(" -Djava.endorsed.dirs=");
        opts.append('"').append(home).append("/lib/endorsed").append('"');

        return opts.toString();
    }

    protected String getVMOpts(PropertiesConfiguration vmProperties) {
        final StringBuilder opts = new StringBuilder();
        for (final Iterator<String> i = vmProperties.getKeys(); i.hasNext(); ) {
            final String key = i.next();
            if (key.startsWith("memory.")) {
                if ("memory.max".equals(key)) {
                    opts.append(" -Xmx").append(vmProperties.getString(key)).append('m');
                } else if ("memory.min".equals(key)) {
                    opts.append(" -Xms").append(vmProperties.getString(key)).append('m');
                }
            } else if ("vmoptions".equals(key)) {
                opts.append(' ').append(vmProperties.getString(key));
            } else if (key.startsWith("vmoptions.")) {
                final String os = key.substring("vmoptions.".length()).toLowerCase();
                if (OS.contains(os)) {
                    opts.append(' ').append(vmProperties.getString(key));
                }
            }
        }
        return opts.toString();
    }

    public static PropertiesConfiguration getVMProperties() {
        final PropertiesConfiguration vmProperties = new PropertiesConfiguration();
        final java.nio.file.Path propFile = ConfigurationHelper.lookup("vm.properties");
        InputStream is = null;
        try {
            if (Files.isReadable(propFile)) {
                is = Files.newInputStream(propFile);
            }
            if (is == null) {
                is = LauncherWrapper.class.getResourceAsStream("vm.properties");
            }
            if (is != null) {
                vmProperties.read(new InputStreamReader(is, "UTF-8"));
            }
        } catch (final IOException e) {
            System.err.println("vm.properties not found");
        } catch (ConfigurationException e) {
            System.err.println("exception reading vm.properties: " + e.getMessage());
        } finally {
            if(is != null) {
                try {
                    is.close();
                } catch(final IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        }
        return vmProperties;
    }
}
