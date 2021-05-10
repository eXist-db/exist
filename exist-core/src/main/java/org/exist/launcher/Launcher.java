/*
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
 */
package org.exist.launcher;


import org.apache.commons.lang3.SystemUtils;
import org.exist.EXistException;
import org.exist.jetty.JettyStart;
import org.exist.repo.ExistRepository;
import org.exist.security.PermissionDeniedException;
import org.exist.start.CompatibleJavaVersionCheck;
import org.exist.start.Main;
import org.exist.start.StartException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.ConfigurationHelper;
import org.exist.util.FileUtils;
import org.exist.util.SystemExitCodes;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.launcher.ConfigurationUtility.LAUNCHER_PROPERTY_NEVER_INSTALL_SERVICE;
import static org.exist.util.ThreadUtils.newGlobalThread;

/**
 * A launcher for the eXist-db server integrated with the desktop.
 * Shows a splash screen during startup and registers a tray icon
 * in the system bar.
 *
 * @author Wolfgang Meier
 */
public class Launcher extends Observable implements Observer {

    private MenuItem stopItem;
    private MenuItem startItem;
    private MenuItem dashboardItem;
    private MenuItem eXideItem;
    private MenuItem monexItem;
    private MenuItem installServiceItem;
    private MenuItem uninstallServiceItem;
    private MenuItem quitItem;

    static final String PACKAGE_DASHBOARD = "http://exist-db.org/apps/dashboard";
    static final String PACKAGE_EXIDE = "http://exist-db.org/apps/eXide";
    static final String PACKAGE_MONEX = "http://exist-db.org/apps/monex";

    public static void main(final String[] args) {
        try {
            CompatibleJavaVersionCheck.checkForCompatibleJavaVersion();
        } catch (final StartException e) {
            if (e.getMessage() != null && !e.getMessage().isEmpty()) {
                System.err.println(e.getMessage());
            }
            System.exit(e.getErrorCode());
        }

        final String os = System.getProperty("os.name", "");
        // Switch to native look and feel except for Linux (ugly)
        if (!"Linux".equals(os)) {
            final String nativeLF = UIManager.getSystemLookAndFeelClassName();
            try {
                UIManager.setLookAndFeel(nativeLF);
            } catch (final Exception e) {
                // can be safely ignored
            }
        }
        /* Turn off metal's use of bold fonts */
        //UIManager.put("swing.boldMetal", Boolean.FALSE);

        //Schedule a job for the event-dispatching thread:
        SwingUtilities.invokeLater(() -> new Launcher(args));
    }

    private final ReentrantLock serviceLock = new ReentrantLock();

    /**
     * ServiceManager will be null if there is no service
     * manager for the current platform
     */
    @Nullable private final ServiceManager serviceManager;
    @Nullable private final SystemTray tray;
    private TrayIcon trayIcon = null;
    private SplashScreen splash;
    private final Path jettyConfig;
    private Optional<JettyStart> jetty = Optional.empty();
    private UtilityPanel utilityPanel;
    private ConfigurationDialog configDialog;
    private boolean isInstallingService = false;

    Launcher(final String[] args) {
        if (SystemTray.isSupported()) {
            this.tray = SystemTray.getSystemTray();
        } else {
            this.tray = null;
        }

        captureConsole();

        // try and figure out exist home dir
        final Optional<Path> existHomeDir = getFromSysPropOrEnv(Main.PROP_EXIST_HOME, Main.ENV_EXIST_HOME).map(Paths::get);

        this.jettyConfig = getJettyConfig(existHomeDir);

        this.serviceManager = ServiceManagerFactory.getServiceManager();

        final boolean initSystemTray = tray != null && initSystemTray();

        if (serviceManager != null) {
            updateGuiServiceState();
        }

        this.configDialog = new ConfigurationDialog(this::shutdown);

        this.splash = new SplashScreen(this);
        splash.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent windowEvent) {
                serviceLock.lock();
                try {
                    if (serviceManager != null && serviceManager.isInstalled()) {
                        splash.setStatus("eXist-db is already installed as service! Attaching to it ...");
                        final Timer timer = new Timer(3000, (event) -> splash.setVisible(false));
                        timer.setRepeats(false);
                        timer.start();
                    } else {
                        if (ConfigurationUtility.isFirstStart()) {
                            splash.setVisible(false);
                            configDialog.open(true);
                            configDialog.requestFocus();
                        } else {
                            startJetty();
                        }
                    }
                } finally {
                    serviceLock.unlock();
                }
            }
        });

        final boolean systemTrayReady = initSystemTray && tray.getTrayIcons().length > 0;

        SwingUtilities.invokeLater(() -> utilityPanel = new UtilityPanel(Launcher.this, tray != null, systemTrayReady));
    }

    private void startJetty() {
        final Runnable runnable = () -> {
            serviceLock.lock();
            try {
                if (!jetty.isPresent()) {
                    jetty = Optional.of(new JettyStart());

                    final String[] args;
                    final Optional<Path> explicitExistConfigPath = ConfigurationHelper.getFromSystemProperty();
                    if (explicitExistConfigPath.isPresent()) {
                        args = new String[]{
                                jettyConfig.toAbsolutePath().toString(),
                                explicitExistConfigPath.get().toAbsolutePath().toString()};
                    } else {
                        args = new String[]{jettyConfig.toAbsolutePath().toString()};
                    }

                    jetty.get().run(args, splash);
                }
            } catch (final Exception e) {
                showMessageAndExit("Error Occurred", "An error occurred during eXist-db startup. Please check console output and logs.", true);
                System.exit(SystemExitCodes.CATCH_ALL_GENERAL_ERROR_EXIT_CODE);
            } finally {
                serviceLock.unlock();
            }
        };
        newGlobalThread("launcher.startJetty", runnable).start();
    }

    private boolean initSystemTray() {
        if (tray == null) {
            return false;
        }

        final Dimension iconDim = tray.getTrayIconSize();
        BufferedImage image = null;
        try {
            image = ImageIO.read(getClass().getResource("icon32.png"));
            trayIcon = new TrayIcon(image.getScaledInstance(iconDim.width, iconDim.height, Image.SCALE_SMOOTH), "eXist-db Launcher");
        } catch (final IOException e) {
            showMessageAndExit("Launcher failed", "Failed to read system tray icon.", false);
        }

        final JDialog hiddenFrame = new JDialog();
        hiddenFrame.setUndecorated(true);
        hiddenFrame.setIconImage(image);

        final PopupMenu popup = createMenu();
        trayIcon.setPopupMenu(popup);
        trayIcon.addActionListener(actionEvent -> showTrayInfoMessage("Right click for menu"));

        // add listener for left click on system tray icon. doesn't work well on linux though.
        if (!SystemUtils.IS_OS_LINUX) {
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent mouseEvent) {
                    if (mouseEvent.getButton() == MouseEvent.BUTTON1) {
                        hiddenFrame.add(popup);
                        popup.show(hiddenFrame, mouseEvent.getXOnScreen(), mouseEvent.getYOnScreen());
                    }
                }
            });
        }

        try {
            hiddenFrame.setResizable(false);
            hiddenFrame.pack();
            hiddenFrame.setVisible(true);
            tray.add(trayIcon);
        } catch (final AWTException e) {
            return false;
        }

        return true;
    }

    private PopupMenu createMenu() {
        final PopupMenu popup = new PopupMenu();
        serviceLock.lock();
        try {
            startItem = new MenuItem("Start server");
            popup.add(startItem);
            startItem.addActionListener(actionEvent -> {
                serviceLock.lock();
                try {
                    if (serviceManager != null && serviceManager.isInstalled()) {
                        showTrayInfoMessage("Starting the eXist-db service. Please wait...");
                        try {
                            serviceManager.start();
                            updateGuiServiceState();
                            showTrayInfoMessage("eXist-db service started");
                        } catch (final ServiceManagerException e) {
                            showTrayErrorMessage("Starting eXist-db service failed" + e.getMessage());
                            JOptionPane.showMessageDialog(null, "Failed to start service: " + e.getMessage(), "Starting Service Failed", JOptionPane.ERROR_MESSAGE);
                        }
                    } else if (jetty.isPresent()) {
                        jetty.ifPresent(server -> {
                            if (server.isStarted()) {
                                showTrayWarningMessage("Server already started");
                            } else {
                                server.run(new String[]{jettyConfig.toAbsolutePath().toString()}, null);
                                if (server.isStarted()) {
                                    showTrayInfoMessage("eXist-db server running on port " + server.getPrimaryPort());
                                }
                            }
                            updateGuiServiceState();
                        });
                    } else {
                        startJetty();
                    }
                } finally {
                    serviceLock.unlock();
                }
            });

            stopItem = new MenuItem("Stop server");
            popup.add(stopItem);
            stopItem.addActionListener(actionEvent -> {
                serviceLock.lock();
                try {
                    if (jetty.isPresent()) {
                        jetty.get().shutdown();
                        updateGuiServiceState();
                        showTrayInfoMessage("eXist-db stopped");
                    } else if (serviceManager != null && serviceManager.isRunning()) {
                        try {
                            serviceManager.stop();
                            updateGuiServiceState();
                            showTrayInfoMessage("eXist-db service stopped");
                        } catch (final ServiceManagerException e) {
                            showTrayErrorMessage("Stopping eXist-db service failed: " + e.getMessage());
                            JOptionPane.showMessageDialog(null, "Failed to stop service: " + e.getMessage(), "Stopping Service Failed", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                } finally {
                    serviceLock.unlock();
                }
            });

            popup.addSeparator();
            final MenuItem configItem = new MenuItem("System Configuration");
            popup.add(configItem);
            configItem.addActionListener(e -> EventQueue.invokeLater(() -> {
                configDialog.open(false);
                configDialog.toFront();
                configDialog.repaint();
                configDialog.requestFocus();
            }));

            final String requiresRootMsg;
            if (serviceManager != null) {
                requiresRootMsg = "";
            } else {
                requiresRootMsg = " (requires root)";
            }

            installServiceItem = new MenuItem("Install as service" + requiresRootMsg);

            popup.add(installServiceItem);
            installServiceItem.setEnabled(serviceManager != null);
            installServiceItem.addActionListener(e -> SwingUtilities.invokeLater(this::installService));

            uninstallServiceItem = new MenuItem("Uninstall service" + requiresRootMsg);
            popup.add(uninstallServiceItem);
            uninstallServiceItem.setEnabled(serviceManager != null);
            uninstallServiceItem.addActionListener(e -> SwingUtilities.invokeLater(this::uninstallService));

            if (SystemUtils.IS_OS_WINDOWS) {
                final MenuItem showServices = new MenuItem("Show services console");
                popup.add(showServices);
                showServices.addActionListener(e -> SwingUtilities.invokeLater(this::showNativeServiceManagementConsole));
            }
            popup.addSeparator();

            final MenuItem toolbar = new MenuItem("Show tool window");
            popup.add(toolbar);
            toolbar.addActionListener(actionEvent -> EventQueue.invokeLater(() -> {
                utilityPanel.toFront();
                utilityPanel.setVisible(true);
            }));

            MenuItem item;

            if (Desktop.isDesktopSupported()) {
                popup.addSeparator();
                final Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    dashboardItem = new MenuItem("Open Dashboard");
                    popup.add(dashboardItem);
                    dashboardItem.addActionListener(actionEvent -> dashboard(desktop));
                    eXideItem = new MenuItem("Open eXide");
                    popup.add(eXideItem);
                    eXideItem.addActionListener(actionEvent -> eXide(desktop));
                    item = new MenuItem("Open Java Admin Client");
                    popup.add(item);
                    item.addActionListener(actionEvent -> client());
                    monexItem = new MenuItem("Open Monitoring and Profiling");
                    popup.add(monexItem);
                    monexItem.addActionListener(actionEvent -> monex(desktop));
                }
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                    popup.addSeparator();
                    item = new MenuItem("Open exist.log");
                    popup.add(item);
                    item.addActionListener(new LogActionListener());
                }

                popup.addSeparator();
                quitItem = new MenuItem("Quit");
                popup.add(quitItem);
                quitItem.addActionListener(actionEvent -> {
                    if (serviceManager != null && serviceManager.isInstalled()) {
                        if (tray != null) {
                            tray.remove(trayIcon);
                        }
                        System.exit(SystemExitCodes.OK_EXIT_CODE);
                    } else {
                        shutdown(false);
                    }
                });
            }
        } finally {
            serviceLock.unlock();
        }
        return popup;
    }

    private void installService() {
        serviceLock.lock();
        try {
            jetty.ifPresent(server -> {
                if (server.isStarted()) {
                    showTrayInfoMessage("Stopping eXist-db...");
                    server.shutdown();
                }
            });
            jetty = Optional.empty();

            if (serviceManager == null) {
                showTrayWarningMessage("It is not possible to use Service installation on this platform");
            } else {
                showTrayInfoMessage("Installing service and starting eXist-db...");

                try {
                    serviceManager.install();
                    serviceManager.start();
                    updateGuiServiceState();
                    showTrayInfoMessage("Service installed and started");
                } catch (final ServiceManagerException e) {
                    showTrayErrorMessage("Failed to install service.", "Failed to install service: " + e.getMessage());
                    JOptionPane.showMessageDialog(null, "Failed to install service: " + e.getMessage(), "Install Service Failed", JOptionPane.ERROR_MESSAGE);
                }
            }

            isInstallingService = false;
        } finally {
            serviceLock.unlock();
        }
    }

    private void uninstallService() {
        serviceLock.lock();
        try {
            if (serviceManager == null) {
                showTrayWarningMessage("It is not possible to use Service uninstallation on this platform");
            } else {
                showTrayInfoMessage("Uninstalling service...");

                try {
                    serviceManager.stop();
                    serviceManager.uninstall();
                    updateGuiServiceState();
                    showTrayInfoMessage("Service stopped and uninstalled");
                } catch (final ServiceManagerException e) {
                    showTrayErrorMessage("Failed to uninstall service.", "Failed to uninstall service: " + e.getMessage());
                    JOptionPane.showMessageDialog(null, "Failed to uninstall service: " + e.getMessage(), "Uninstalling Service Failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        } finally {
            serviceLock.unlock();
        }
    }

    private void showNativeServiceManagementConsole() {
        if (serviceManager == null) {
            showTrayWarningMessage("It is not possible to use Service Management on this platform");
        } else {
            try {
                serviceManager.showNativeServiceManagementConsole();
            } catch (final UnsupportedOperationException | ServiceManagerException e) {
                showTrayErrorMessage("Failed to open Service Management Console", "Failed to open Service Management Console: " + e.getMessage());
            }
        }
    }

    private void updateGuiServiceState() {
        serviceLock.lock();
        try {
            final boolean serverRunning;
            if (serviceManager != null) {
                if (serviceManager.isInstalled()) {
                    installServiceItem.setEnabled(false);
                    uninstallServiceItem.setEnabled(true);
                } else {
                    installServiceItem.setEnabled(true);
                    uninstallServiceItem.setEnabled(false);
                }
                serverRunning = serviceManager.isRunning();
            } else {
                serverRunning = jetty.isPresent() && jetty.get().isStarted();
            }

            quitItem.setLabel(serverRunning ? "Quit (and stop server)" : "Quit");

            stopItem.setEnabled(serverRunning);
            startItem.setEnabled(!serverRunning);
            if (dashboardItem != null) {
                dashboardItem.setEnabled(serverRunning);
                monexItem.setEnabled(serverRunning);
                eXideItem.setEnabled(serverRunning);
            }

        } finally {
            serviceLock.unlock();
        }
    }

    void shutdown(final boolean restart) {
        utilityPanel.setStatus("Shutting down ...");
        SwingUtilities.invokeLater(() -> {
            serviceLock.lock();
            try {
                if (serviceManager != null && serviceManager.isRunning()) {

                    try {
                        serviceManager.stop();
                        showTrayInfoMessage("Database stopped");

                        if (restart) {
                            try {
                                serviceManager.start();
                                showTrayInfoMessage("Database started");
                            } catch (final ServiceManagerException e) {
                                showTrayErrorMessage("Failed to start. Please start service manually: " + e.getMessage());
                                JOptionPane.showMessageDialog(null, "Failed to start service. ", "Starting Service Failed", JOptionPane.ERROR_MESSAGE);
                            }
                        }

                        updateGuiServiceState();

                    } catch (final ServiceManagerException e) {
                        showTrayErrorMessage("Failed to stop. Please stop service manually: " + e.getMessage());
                        JOptionPane.showMessageDialog(null, "Failed to stop service: " + e.getMessage(), "Stopping Service Failed", JOptionPane.ERROR_MESSAGE);
                    }
                } else {
                    if (tray != null) {
                        tray.remove(trayIcon);
                    }
                    if (jetty.isPresent()) {
                        jetty.get().shutdown();
                        if (restart) {
                            final LauncherWrapper wrapper = new LauncherWrapper(Launcher.class.getName());
                            wrapper.launch();
                        }
                    }
                    System.exit(SystemExitCodes.OK_EXIT_CODE);
                }
            } finally {
                serviceLock.unlock();
            }
        });
    }

    void dashboard(final Desktop desktop) {
        utilityPanel.setStatus("Opening dashboard in browser ...");
        serviceLock.lock();
        try {
            final URI uri = getAppUri("dashboard");
            if (uri != null) {
                openAppInBrowser(desktop, uri);
            }
        } finally {
            serviceLock.unlock();
        }
    }

    void eXide(final Desktop desktop) {
        utilityPanel.setStatus("Opening eXide in browser ...");
        serviceLock.lock();
        try {
            final URI uri = getAppUri("eXide");
            if (uri != null) {
                openAppInBrowser(desktop, uri);
            }
        } finally {
            serviceLock.unlock();
        }
    }

    private void monex(final Desktop desktop) {
        utilityPanel.setStatus("Opening Monitoring and Profiling in browser ...");
        serviceLock.lock();
        try {
            final URI uri = getAppUri("monex");
            if (uri != null) {
                openAppInBrowser(desktop, uri);
            }
        } finally {
            serviceLock.unlock();
        }
    }

    private void openAppInBrowser(final Desktop desktop, final URI uri) {
        try {
            desktop.browse(uri);
        } catch (final IOException e) {
            showTrayWarningMessage("Failed to open URL: " + uri.toString(), e.getMessage());
            utilityPanel.setStatus("Unable to launch browser");
        }
    }

    private @Nullable URI getAppUri(final String appTarget) {
        final int port = jetty.map(JettyStart::getPrimaryPort).orElse(8080);
        final String url = "http://localhost:" + port + "/exist/apps/" + appTarget + "/";
        try {
            return new URI(url);
        } catch (final URISyntaxException e) {
            showTrayWarningMessage("Failed to open URL: " + url, e.getMessage());
            utilityPanel.setStatus("Unable to launch browser");
            return null;
        }
    }

    void client() {
        final LauncherWrapper wrapper = new LauncherWrapper("client");
        wrapper.launch();
    }

    void signalStarted() {
        if (tray != null) {
            startItem.setEnabled(false);
            stopItem.setEnabled(true);
            checkInstalledApps();
            registerObserver();
        }

        final Properties properties = ConfigurationUtility.loadProperties();
        final boolean neverInstallService = Boolean.parseBoolean(properties.getProperty(LAUNCHER_PROPERTY_NEVER_INSTALL_SERVICE, "false"));

        if (!neverInstallService) {
            if (SystemUtils.IS_OS_WINDOWS && !isInstallingService && serviceManager != null && !serviceManager.isInstalled()) {
                isInstallingService = true;
                SwingUtilities.invokeLater(() -> {
                    final int installServiceResult = JOptionPane.showOptionDialog(
                            splash,
                            "It is recommended to run eXist-db as a service on " +
                                    "Windows.\nNot doing so may lead to data loss if you shutdown the computer before " +
                                    "eXist-db.\n\nWould you like to install the service?",
                            "Install as Service?",
                            JOptionPane.YES_NO_CANCEL_OPTION,
                            JOptionPane.QUESTION_MESSAGE,
                            null,
                            new String[]{"Yes", "No", "Never"},
                            "Yes"
                    );

                    if (installServiceResult == JOptionPane.YES_OPTION) {
                        // install the service
                        SwingUtilities.invokeLater(this::installService);

                    } else if (installServiceResult == JOptionPane.CANCEL_OPTION) {
                        // never - record that we should not be prompted again
                        final Properties srvProps = new Properties();
                        srvProps.setProperty(LAUNCHER_PROPERTY_NEVER_INSTALL_SERVICE, "true");
                        try {
                            ConfigurationUtility.saveProperties(srvProps);
                        } catch (final IOException e) {
                            JOptionPane.showMessageDialog(null, "Failed to update service settings in " + ConfigurationUtility.LAUNCHER_PROPERTIES_FILE_NAME + ": " + e.getMessage(), "Failed to save Launcher settings", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                });
            }
        }
    }

    void signalShutdown() {
        if (tray != null) {
            trayIcon.setToolTip("eXist-db server stopped");
            if (!isInstallingService) {
                startItem.setEnabled(true);
                stopItem.setEnabled(false);
            }
        }
    }

    private void checkInstalledApps() {
        try {
            final BrokerPool pool = BrokerPool.getInstance();
            try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

                final XQuery xquery = pool.getXQueryService();
                final Sequence pkgs = xquery.execute(broker, "repo:list()", null);
                for (final SequenceIterator i = pkgs.iterate(); i.hasNext(); ) {
                    final ExistRepository.Notification notification = new ExistRepository.Notification(ExistRepository.Action.INSTALL, i.nextItem().getStringValue());
                    final Optional<ExistRepository> expathRepo = pool.getExpathRepo();
                    if (expathRepo.isPresent()) {
                        update(expathRepo.get(), notification);
                        utilityPanel.update(expathRepo.get(), notification);
                    }
                    expathRepo.orElseThrow(() -> new EXistException("EXPath repository is not available."));
                }
            }
        } catch (final EXistException | XPathException | PermissionDeniedException e) {
            System.err.println("Failed to check installed packages: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void registerObserver() {
        try {
            final BrokerPool pool = BrokerPool.getInstance();
            final Optional<ExistRepository> repo = pool.getExpathRepo();
            if (repo.isPresent()) {
                repo.get().addObserver(this);
                repo.get().addObserver(utilityPanel);
            } else {
                System.err.println("EXPath repository is not available.");
            }
        } catch (final EXistException e) {
            System.err.println("Failed to register as observer for package manager events");
            e.printStackTrace();
        }
    }

    @Override
    public void update(final Observable observable, final Object o) {
        final ExistRepository.Notification notification = (ExistRepository.Notification) o;

        if (notification.getPackageURI().equals(PACKAGE_DASHBOARD) && dashboardItem != null) {
            dashboardItem.setEnabled(notification.getAction() == ExistRepository.Action.INSTALL);

        } else if (notification.getPackageURI().equals(PACKAGE_EXIDE) && eXideItem != null) {
            eXideItem.setEnabled(notification.getAction() == ExistRepository.Action.INSTALL);

        } else if (notification.getPackageURI().equals(PACKAGE_MONEX) && monexItem != null) {
            monexItem.setEnabled(notification.getAction() == ExistRepository.Action.INSTALL);
        }
    }

    private Path getJettyConfig(final Optional<Path> existHomeDir) {

        Optional<Path> existJettyConfigFile = getFromSysPropOrEnv(Main.PROP_EXIST_JETTY_CONFIG, Main.ENV_EXIST_JETTY_CONFIG).map(Paths::get);
        if (!existJettyConfigFile.isPresent()) {
            final Optional<Path> jettyHomeDir = getFromSysPropOrEnv(Main.PROP_JETTY_HOME, Main.ENV_JETTY_HOME).map(Paths::get);

            if (jettyHomeDir.isPresent() && Files.exists(jettyHomeDir.get().resolve(Main.CONFIG_DIR_NAME))) {
                existJettyConfigFile = jettyHomeDir.map(f -> f.resolve(Main.CONFIG_DIR_NAME).resolve(Main.STANDARD_ENABLED_JETTY_CONFIGS));
            }

            if (existHomeDir.isPresent() && Files.exists(existHomeDir.get().resolve(Main.CONFIG_DIR_NAME))) {
                existJettyConfigFile = existHomeDir.map(f -> f.resolve(Main.CONFIG_DIR_NAME).resolve(Main.STANDARD_ENABLED_JETTY_CONFIGS));
            }

            if (!existJettyConfigFile.isPresent()) {
                showMessageAndExit("Error Occurred", "ERROR: jetty config file could not be found! Make sure to set exist.jetty.config or EXIST_JETTY_CONFIG.", true);
                System.exit(SystemExitCodes.CATCH_ALL_GENERAL_ERROR_EXIT_CODE);
            }
        }

        return existJettyConfigFile.get();
    }

    private Optional<String> getFromSysPropOrEnv(final String sysPropName, final String envVarName) {
        Optional<String> value = Optional.ofNullable(System.getProperty(sysPropName));
        if (!value.isPresent()) {
            value = Optional.ofNullable(System.getenv().get(envVarName));
            // if we managed to detect from environment, store it in a system property
            value.ifPresent(s -> System.setProperty(sysPropName, s));
        }
        return value;
    }

    void showMessageAndExit(final String title, final String message, final boolean logs) {
        final JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        final JLabel label = new JLabel(message);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(label, BorderLayout.CENTER);
        if (logs) {
            final JButton displayLogs = new JButton("View Log");
            displayLogs.addActionListener(new LogActionListener());
            label.setHorizontalAlignment(SwingConstants.CENTER);
            panel.add(displayLogs, BorderLayout.SOUTH);
        }

        utilityPanel.showMessages();
        utilityPanel.toFront();
        utilityPanel.setVisible(true);

        JOptionPane.showMessageDialog(splash, panel, title, JOptionPane.WARNING_MESSAGE);
        //System.exit(SystemExitCodes.CATCH_ALL_GENERAL_ERROR_EXIT_CODE);
    }

    private void showTrayInfoMessage(final String message) {
        showTrayInfoMessage(message, message);
    }

    private void showTrayInfoMessage(final String caption, final String message) {
        if (tray != null) {
            trayIcon.displayMessage(caption, message, TrayIcon.MessageType.INFO);
        }
    }

    private void showTrayWarningMessage(final String message) {
        showTrayInfoMessage(message, message);
    }

    private void showTrayWarningMessage(final String caption, final String message) {
        if (tray != null) {
            trayIcon.displayMessage(caption, message, TrayIcon.MessageType.WARNING);
        }
    }

    private void showTrayErrorMessage(final String message) {
        showTrayErrorMessage(message, message);
    }

    private void showTrayErrorMessage(final String caption, final String message) {
        if (tray != null) {
            trayIcon.displayMessage(caption, message, TrayIcon.MessageType.ERROR);
        }
    }

    /**
     * Ensure that stdout and stderr messages are also printed
     * to the logs.
     */
    private void captureConsole() {
        System.setOut(createLoggingProxy(System.out));
        System.setErr(createLoggingProxy(System.err));
    }

    private PrintStream createLoggingProxy(final PrintStream realStream) {
        final OutputStream out = new OutputStream() {
            @Override
            public void write(final int i) {
                realStream.write(i);
                final String s = Character.toString((char)i);
                Launcher.this.setChanged();
                Launcher.this.notifyObservers(s);
            }

            @Override
            public void write(final byte[] bytes) throws IOException {
                realStream.write(bytes);
                final String s = new String(bytes, UTF_8);
                Launcher.this.setChanged();
                Launcher.this.notifyObservers(s);
            }

            @Override
            public void write(final byte[] bytes, final int offset, final int len) {
                realStream.write(bytes, offset, len);
                final String s = new String(bytes, offset, len, UTF_8);
                Launcher.this.setChanged();
                Launcher.this.notifyObservers(s);
            }
        };
        return new PrintStream(out);
    }

    private class LogActionListener implements ActionListener {

        @Override
        public void actionPerformed(final ActionEvent actionEvent) {
            if (!Desktop.isDesktopSupported()) {
                return;
            }
            final Desktop desktop = Desktop.getDesktop();
            final Optional<Path> home = ConfigurationHelper.getExistHome();

            final Path logFile = FileUtils.resolve(home, "logs/exist.log");

            if (!Files.isReadable(logFile)) {
                showTrayErrorMessage("Log file not found: " + logFile.toAbsolutePath().normalize().toString());
            } else {
                try {
                    desktop.open(logFile.toFile());
                } catch (final IOException e) {
                    showTrayErrorMessage("Failed to open log file: " + logFile.toAbsolutePath().normalize().toString() + ". " + e.getMessage());
                }
            }
        }
    }
}
