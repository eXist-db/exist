/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.launcher;

import org.apache.commons.lang3.SystemUtils;
import org.apache.log4j.Logger;
import org.exist.util.ConfigurationHelper;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServiceManager {

    private final static Logger LOG = Logger.getLogger(ServiceManager.class);

    private Launcher launcher;
    private final Properties wrapperProperties;
    private boolean canUseServices;
    private boolean inServiceInstall = false;
    private boolean isInstalled = false;
    private boolean isRunning = false;

    public ServiceManager(Launcher launcher) {
        this.launcher = launcher;

        if (SystemUtils.IS_OS_WINDOWS) {
            canUseServices = true;
        } else {
            isRoot((root) -> canUseServices = root);
        }

        final Optional<Path> eXistHome = ConfigurationHelper.getExistHome();
        final Path wrapperConfig;
        if (eXistHome.isPresent()) {
            wrapperConfig = eXistHome.get().resolve("tools/yajsw/conf/wrapper.conf");
        } else {
            wrapperConfig = Paths.get("tools/yajsw/conf/wrapper.conf");
        }

        System.setProperty("wrapper.config", wrapperConfig.toString());

        wrapperProperties = new Properties();
        wrapperProperties.setProperty("wrapper.working.dir", eXistHome.orElse(Paths.get(".")).toString());
        wrapperProperties.setProperty("wrapper.config", wrapperConfig.toString());

        installedAsService();
    }

    public boolean isInstallingService() {
        return inServiceInstall;
    }

    public boolean isInstalled() {
        return isInstalled;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public boolean canUseServices() {
        return canUseServices;
    }

    private void installedAsService() {
        if (canUseServices) {
            final String cmd;
            if (SystemUtils.IS_OS_UNIX) {
                cmd = "tools/yajsw/bin/queryDaemon.sh";
            } else {
                cmd = "tools/yajsw/bin/queryService.bat";
            }

            runWrapperCmd(cmd, (code, output) -> {
                if (code == 0) {
                    Pattern statusRegex = Pattern.compile("^Installed\\s*:\\s*(.*)$|^Running\\s*:\\s*(.*)$", Pattern.MULTILINE);
                    Matcher m = statusRegex.matcher(output);
                    if (m.find()) {
                        isInstalled = Boolean.valueOf(m.group(1));
                        isRunning = Boolean.valueOf(m.group(2));
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("isInstalled: " + isInstalled + "; isRunning: " + isRunning);
                        }
                    }
                }
            });
        }
    }

    protected void installAsService() {
        launcher.showTrayMessage("Installing service and starting eXistdb ...", TrayIcon.MessageType.INFO);

        inServiceInstall = true;

        if (canUseServices) {
            final String cmd;
            if (SystemUtils.IS_OS_UNIX) {
                cmd = "tools/yajsw/bin/installDaemon.sh";
            } else {
                cmd = "tools/yajsw/bin/installService.bat";
            }

            runWrapperCmd(cmd, (code, output) -> {
                if (code == 0) {
                    isInstalled = true;
                    start();
                    launcher.showTrayMessage("Service installed and started", TrayIcon.MessageType.INFO);
                } else {
                    JOptionPane.showMessageDialog(null, "Failed to install service. ", "Install Service Failed", JOptionPane.ERROR_MESSAGE);
                    isInstalled = false;
                    isRunning = false;
                }
            });
        }
        inServiceInstall = false;
    }

    protected boolean uninstall() {
        if (isInstalled) {
            final String cmd;
            if (SystemUtils.IS_OS_MAC) {
                cmd = "tools/yajsw/bin/uninstallDaemon.sh";
            } else {
                cmd = "tools/yajsw/bin/uninstallService.bat";
            }

            runWrapperCmd(cmd, (code, output) -> {
                if (code == 0) {
                    isInstalled = false;
                    isRunning = false;
                    launcher.showTrayMessage("Service stopped and uninstalled", TrayIcon.MessageType.INFO);
                } else {
                    JOptionPane.showMessageDialog(null, "Failed to uninstall service. ", "Uninstalling Service Failed", JOptionPane.ERROR_MESSAGE);
                    isInstalled = true;
                    isRunning = true;
                }
                launcher.setServiceState();
            });
        }
        return false;
    }

    protected boolean start() {
        if (!isRunning) {
            final String cmd;
            if (SystemUtils.IS_OS_MAC) {
                cmd = "tools/yajsw/bin/startDaemon.sh";
            } else {
                cmd = "tools/yajsw/bin/startService.bat";
            }
            runWrapperCmd(cmd, (code, output) -> {
                if (code == 0) {
                    isRunning = true;
                    launcher.showTrayMessage("Service started", TrayIcon.MessageType.INFO);
                } else {
                    JOptionPane.showMessageDialog(null, "Failed to start service. ", "Starting Service Failed", JOptionPane.ERROR_MESSAGE);
                    isRunning = false;
                }
                launcher.setServiceState();
            });
        }
        return isRunning;
    }

    protected boolean stop() {
        if (isRunning) {
            final String cmd;
            if (SystemUtils.IS_OS_MAC) {
                cmd = "tools/yajsw/bin/stopDaemon.sh";
            } else {
                cmd = "tools/yajsw/bin/stopService.bat";
            }
            runWrapperCmd(cmd, (code, output) -> {
                if (code == 0) {
                    isRunning = false;
                    launcher.showTrayMessage("Service stopped", TrayIcon.MessageType.INFO);
                } else {
                    JOptionPane.showMessageDialog(null, "Failed to stop service. ", "Stopping Service Failed", JOptionPane.ERROR_MESSAGE);
                    isRunning = true;
                }
                launcher.setServiceState();
            });
        }
        return false;
    }

    private void isRoot(Consumer<Boolean> consumer) {
        final List<String> args = new ArrayList<>(2);
        args.add("id");
        args.add("-u");
        run(args, (code, output) -> {
            consumer.accept("0".equals(output.trim()));
        });
    }

    public void showServicesConsole() {
        final List<String> args = new ArrayList<>(2);
        args.add("cmd");
        args.add("/c");
        args.add("services.msc");

        run(args, null);
    }

    private static void runWrapperCmd(final String cmd, final BiConsumer<Integer, String> consumer) {
        final List<String> args = new ArrayList<>(1);
        args.add(cmd);
        run(args, (code, output) -> {
            if (LOG.isDebugEnabled()) {
                LOG.debug(output);
            }
            consumer.accept(code, output);
        });
    }

    public static void run(List<String> args, BiConsumer<Integer, String> consumer) {
        final ProcessBuilder pb = new ProcessBuilder(args);
        final Optional<Path> home = ConfigurationHelper.getExistHome();

        pb.directory(home.orElse(Paths.get(".")).toFile());
        pb.redirectErrorStream(true);
        if (consumer == null) {
            pb.inheritIO();
        }
        try {
            final Process process = pb.start();
            if (consumer != null) {
                final StringBuilder output = new StringBuilder();
                try (final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(),
                        "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append('\n').append(line);
                    }
                }
                final int exitValue = process.waitFor();
                consumer.accept(exitValue, output.toString());
            }
        } catch (IOException | InterruptedException e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Error Running Process", JOptionPane.ERROR_MESSAGE);
        }
    }
}
