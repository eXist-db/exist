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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.util.ConfigurationHelper;

import javax.annotation.Nullable;
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
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ServiceManager {

    private final static Logger LOG = LogManager.getLogger(ServiceManager.class);

    private final static Pattern STATUS_REGEX = Pattern.compile("Installed\\s*:\\s*(.*)\n\\s*Running\\s*:\\s*(.*)\n", Pattern.MULTILINE);

    private Launcher launcher;
    private Properties wrapperProperties;
    private Path wrapperDir;
    private boolean canUseServices;
    private boolean isInstalled = false;
    private boolean isRunning = false;

    ServiceManager(Launcher launcher) {
        this.launcher = launcher;

        // only use the YAJSW on Windows!!!
        this.canUseServices = SystemUtils.IS_OS_WINDOWS;

        if(!canUseServices) {
            return;
        }

        final Optional<Path> eXistHome = ConfigurationHelper.getExistHome();
        if (eXistHome.isPresent()) {
            wrapperDir = eXistHome.get().resolve("tools/yajsw");
        } else {
            wrapperDir = Paths.get("tools/yajsw");
        }

        final Path wrapperConfig = wrapperDir.resolve("conf/wrapper.conf");

        System.setProperty("wrapper.config", wrapperConfig.toString());

        wrapperProperties = new Properties();
        wrapperProperties.setProperty("wrapper.working.dir", eXistHome.orElse(Paths.get(".")).toString());
        wrapperProperties.setProperty("wrapper.config", wrapperConfig.toString());
    }

    boolean isInstalled() {
        return isInstalled;
    }

    boolean isRunning() {
        return isRunning;
    }

    boolean canUseServices() {
        return canUseServices;
    }

    void queryState() {
        if (canUseServices) {
            runWrapperCmd("query", (code, output) -> {
                if (code == 0) {

                    final Matcher m = STATUS_REGEX.matcher(output);
                    if (m.find()) {
                        isInstalled = Boolean.valueOf(m.group(1));
                        isRunning = Boolean.valueOf(m.group(2));
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("isInstalled: " + isInstalled + "; isRunning: " + isRunning);
                        }
                    }
                }
                launcher.setServiceState();
            });
        } else {
            launcher.setServiceState();
        }
    }

    void installAsService() {
        if (!canUseServices) {
            launcher.showTrayMessage("It is not possible to use Service installation on this platform", TrayIcon.MessageType.INFO);
        }

        launcher.showTrayMessage("Installing service and starting eXist-db...", TrayIcon.MessageType.INFO);
        runWrapperCmd("install", (code, output) -> {
            if (code == 0) {
                isRunning = false;
                isInstalled = true;
                start();
                launcher.showTrayMessage("Service installed and started", TrayIcon.MessageType.INFO);
            } else {
                LOG.warn("Failed to install service. Exit code: " + code);
                JOptionPane.showMessageDialog(null, "Failed to install service. ", "Install Service Failed", JOptionPane.ERROR_MESSAGE);
                isInstalled = false;
                isRunning = false;
            }
        });
    }

    boolean uninstall() {
        if (isInstalled) {
            runWrapperCmd("uninstall", (code, output) -> {
                if (code == 0) {
                    isInstalled = false;
                    isRunning = false;
                    launcher.showTrayMessage("Service stopped and uninstalled", TrayIcon.MessageType.INFO);
                } else {
                    LOG.warn("Failed to uninstall service. Exit code: " + code);
                    JOptionPane.showMessageDialog(null, "Failed to uninstall service. ", "Uninstalling Service Failed", JOptionPane.ERROR_MESSAGE);
                    isInstalled = true;
                    isRunning = true;
                }
                launcher.setServiceState();
            });
        }
        return false;
    }

    boolean start() {
        if (!isRunning) {
            runWrapperCmd("start", (code, output) -> {
                if (code == 0) {
                    isRunning = true;
                } else {
                    LOG.warn("Failed to start service. Exit code: " + code);
                    JOptionPane.showMessageDialog(null, "Failed to start service. ", "Starting Service Failed", JOptionPane.ERROR_MESSAGE);
                    isRunning = false;
                }
                launcher.setServiceState();
            });
        }
        return isRunning;
    }

    boolean stop() {
        if (isRunning) {
            runWrapperCmd("stop", (code, output) -> {
                if (code == 0) {
                    isRunning = false;
                } else {
                    LOG.warn("Failed to stop service. Exit code: " + code);
                    JOptionPane.showMessageDialog(null, "Failed to stop service.", "Stopping Service Failed", JOptionPane.ERROR_MESSAGE);
                    isRunning = true;
                }
                launcher.setServiceState();
            });
        }
        return !isRunning;
    }

    private String getShellCmd(String cmd) {
        if (SystemUtils.IS_OS_UNIX) {
            return cmd + "Daemon.sh";
        }
        return cmd + "Service.bat";
    }

    private boolean isRoot() {
        final List<String> args = new ArrayList<>(2);
        args.add("id");
        args.add("-u");

        return run(args,
                (code, output) -> "0".equals(output.trim())
        );
    }

    void showServicesConsole() {
        final List<String> args = new ArrayList<>(2);
        args.add("cmd");
        args.add("/c");
        args.add("services.msc");

        run(args, null);
    }

    private void runWrapperCmd(final String cmd, final BiConsumer<Integer, String> consumer) {
        runWrapperCmd(cmd, (code, output) -> {
            consumer.accept(code, output);
            return null;
        });
    }

    private <T> T runWrapperCmd(final String cmd, final BiFunction<Integer, String, T> resultMapper) {
        final Path executable = wrapperDir.resolve("bin/" + getShellCmd(cmd));
        final List<String> args = new ArrayList<>(1);
        args.add(executable.toString());

        return run(args, (code, output) -> {
            if (LOG.isDebugEnabled()) {
                LOG.debug(output);
            }
            return resultMapper.apply(code, output);
        });
    }

    static @Nullable <T> T run(final List<String> args, @Nullable final BiFunction<Integer, String, T> resultMapper) {
        final ProcessBuilder pb = new ProcessBuilder(args);
        final Optional<Path> home = ConfigurationHelper.getExistHome();

        pb.directory(home.orElse(Paths.get(".")).toFile());
        pb.redirectErrorStream(true);
        if (resultMapper == null) {
            pb.inheritIO();
        }
        try {
            final Process process = pb.start();
            if (resultMapper != null) {
                final StringBuilder output = new StringBuilder();
                try (final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(),
                        UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append('\n').append(line);
                    }
                }
                final int exitValue = process.waitFor();
                return resultMapper.apply(exitValue, output.toString());
            }
        } catch (final IOException | InterruptedException e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Error Running Process", JOptionPane.ERROR_MESSAGE);
        }

        return null;
    }
}
