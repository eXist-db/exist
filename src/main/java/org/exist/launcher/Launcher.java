/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012-2015 The eXist-db Project
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
 */
package org.exist.launcher;


import org.apache.commons.lang3.SystemUtils;
import org.exist.EXistException;
import org.exist.jetty.JettyStart;
import org.exist.repo.ExistRepository;
import org.exist.security.PermissionDeniedException;
import org.exist.start.Main;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.ConfigurationHelper;
import org.exist.util.FileUtils;
import org.exist.util.SystemExitCodes;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;

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
    private MenuItem showServices;
    private MenuItem quitItem;

    public final static String PACKAGE_DASHBOARD = "http://exist-db.org/apps/dashboard";
    public final static String PACKAGE_EXIDE = "http://exist-db.org/apps/eXide";
    public final static String PACKAGE_MONEX = "http://exist-db.org/apps/monex";

    public static void main(final String[] args) {
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

    private ServiceManager serviceManager;
    private SystemTray tray = null;
    private TrayIcon trayIcon = null;
    private SplashScreen splash;
    private Optional<JettyStart> jetty = Optional.empty();
    private final Path jettyConfig;
    private UtilityPanel utilityPanel;
    private ConfigurationDialog configDialog;
    private boolean isInstallingService = false;

    Launcher(final String[] args) {
        if (SystemTray.isSupported()) {
            tray = SystemTray.getSystemTray();
        }

        captureConsole();

        this.jettyConfig = getJettyConfig();

        serviceManager = new ServiceManager(this);
        
        boolean initSystemTray = true;
        if (isSystemTraySupported()) {
            initSystemTray = initSystemTray();
        }

        serviceManager.queryState();

        configDialog = new ConfigurationDialog(this::shutdown);

        splash = new SplashScreen(this);
        splash.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent windowEvent) {
                serviceLock.lock();
                try {
                    if (serviceManager.isInstalled()) {
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

        final boolean systemTrayReady = tray != null && initSystemTray && tray.getTrayIcons().length > 0;

        SwingUtilities.invokeLater(() -> utilityPanel = new UtilityPanel(Launcher.this, systemTrayReady));
    }

    void startJetty() {
        final Runnable runnable = () -> {
            serviceLock.lock();
            try {
                if (!jetty.isPresent()) {
                    jetty = Optional.of(new JettyStart());
                    jetty.get().run(new String[]{jettyConfig.toAbsolutePath().toString()}, splash);
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

    boolean isSystemTraySupported() {
        return tray != null;
    }

    private boolean initSystemTray() {
        final Dimension iconDim = tray.getTrayIconSize();
        BufferedImage image = null;
        try {
            image = ImageIO.read(getClass().getResource("icon32.png"));
        } catch (final IOException e) {
            showMessageAndExit("Launcher failed", "Failed to read system tray icon.", false);
        }
        trayIcon = new TrayIcon(image.getScaledInstance(iconDim.width, iconDim.height, Image.SCALE_SMOOTH), "eXist-db Launcher");

        final JDialog hiddenFrame = new JDialog();
        hiddenFrame.setUndecorated(true);
        hiddenFrame.setIconImage(image);

        final PopupMenu popup = createMenu();
        trayIcon.setPopupMenu(popup);
        trayIcon.addActionListener(actionEvent -> {
                trayIcon.displayMessage(null, "Right click for menu", TrayIcon.MessageType.INFO);
            }
        );

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
                    if (serviceManager.isInstalled()) {
                        showTrayMessage("Starting the eXist-db service. Please wait...", TrayIcon.MessageType.INFO);
                        if (serviceManager.start()) {
                            showTrayMessage("eXist-db service started", TrayIcon.MessageType.INFO);
                        } else {
                            showTrayMessage("Starting eXist-db service failed", TrayIcon.MessageType.ERROR);
                        }
                    } else if (jetty.isPresent()) {
                        jetty.ifPresent(server -> {
                            if (server.isStarted()) {
                                showTrayMessage("Server already started", TrayIcon.MessageType.WARNING);
                            } else {
                                server.run(new String[]{jettyConfig.toAbsolutePath().toString()}, null);
                                if (server.isStarted()) {
                                    showTrayMessage("eXist-db server running on port " + server.getPrimaryPort(), TrayIcon
                                            .MessageType.INFO);
                                }
                            }
                            setServiceState();
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
                        setServiceState();
                        showTrayMessage("eXist-db stopped", TrayIcon.MessageType.INFO);
                    } else if (serviceManager.isRunning()) {
                        if (serviceManager.stop()) {
                            showTrayMessage("eXist-db service stopped", TrayIcon.MessageType.INFO);
                        } else {
                            showTrayMessage("Stopping eXist-db service failed", TrayIcon.MessageType.ERROR);
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
            if (serviceManager.canUseServices()) {
                requiresRootMsg = "";
            } else {
                requiresRootMsg = " (requires root)";
            }

            installServiceItem = new MenuItem("Install as service" + requiresRootMsg);

            popup.add(installServiceItem);
            installServiceItem.setEnabled(serviceManager.canUseServices());
            installServiceItem.addActionListener(e -> SwingUtilities.invokeLater(this::installAsService));

            uninstallServiceItem = new MenuItem("Uninstall service" + requiresRootMsg);
            popup.add(uninstallServiceItem);
            uninstallServiceItem.setEnabled(serviceManager.canUseServices());
            uninstallServiceItem.addActionListener(e -> SwingUtilities.invokeLater(serviceManager::uninstall));

            if (SystemUtils.IS_OS_WINDOWS) {
                showServices = new MenuItem("Show services console");
                popup.add(showServices);
                showServices.addActionListener(e -> SwingUtilities.invokeLater(serviceManager::showServicesConsole));
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
                    if (serviceManager.isInstalled()) {
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

    private void installAsService() {
        serviceLock.lock();
        try {
            jetty.ifPresent(server -> {
                if (server.isStarted()) {
                    showTrayMessage("Stopping eXist-db...", TrayIcon.MessageType.INFO);
                    server.shutdown();
                }
            });
            jetty = Optional.empty();

            serviceManager.installAsService();
            isInstallingService = false;
        } finally {
            serviceLock.unlock();
        }
    }

    void setServiceState() {
        serviceLock.lock();
        try {
            if (serviceManager.isInstalled()) {

                final boolean serverRunning = serviceManager.isRunning();
                installServiceItem.setEnabled(false);
                uninstallServiceItem.setEnabled(true);

                quitItem.setLabel("Quit");
                stopItem.setEnabled(serverRunning);
                startItem.setEnabled(!serverRunning);
                if (dashboardItem != null) {
                    dashboardItem.setEnabled(serverRunning);
                    monexItem.setEnabled(serverRunning);
                    eXideItem.setEnabled(serverRunning);
                }
            } else {
                final boolean serverRunning = jetty.isPresent() && jetty.get().isStarted();
                if (serviceManager.canUseServices()) {
                    if (installServiceItem != null && uninstallServiceItem != null) {
                        installServiceItem.setEnabled(true);
                        uninstallServiceItem.setEnabled(false);
                    }
                }
                quitItem.setLabel("Quit (and stop server)");
                stopItem.setEnabled(serverRunning);
                startItem.setEnabled(!serverRunning);
                if (dashboardItem != null) {
                    dashboardItem.setEnabled(serverRunning);
                    monexItem.setEnabled(serverRunning);
                    eXideItem.setEnabled(serverRunning);
                }
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
                if (serviceManager.isRunning() && restart) {
                    if (serviceManager.stop()) {
                        serviceManager.start();
                        trayIcon.displayMessage(null, "Database stopped", TrayIcon.MessageType.INFO);
                    } else {
                        trayIcon.displayMessage(null, "Failed to stop. Please stop service manually.", TrayIcon.MessageType.INFO);
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

    void dashboard(Desktop desktop) {
        utilityPanel.setStatus("Opening dashboard in browser ...");
        serviceLock.lock();
        try {
            final int port = jetty.isPresent() ? jetty.get().getPrimaryPort() : 8080;
            final URI url = new URI("http://localhost:" + port + "/exist/apps/dashboard/");
            desktop.browse(url);
        } catch (final URISyntaxException e) {
            if (isSystemTraySupported())
                {trayIcon.displayMessage(null, "Failed to open URL", TrayIcon.MessageType.ERROR);}
            utilityPanel.setStatus("Unable to launch browser");
        } catch (final IOException e) {
            if (isSystemTraySupported())
                {trayIcon.displayMessage(null, "Failed to open URL", TrayIcon.MessageType.ERROR);}
            utilityPanel.setStatus("Unable to launch browser");
        } finally {
            serviceLock.unlock();
        }
    }

    void eXide(Desktop desktop) {
        utilityPanel.setStatus("Opening dashboard in browser ...");
        serviceLock.lock();
        try {
            final int port = jetty.isPresent() ? jetty.get().getPrimaryPort() : 8080;
            final URI url = new URI("http://localhost:" + port + "/exist/apps/eXide/");
            desktop.browse(url);
        } catch (final URISyntaxException e) {
            if (isSystemTraySupported())
                {trayIcon.displayMessage(null, "Failed to open URL", TrayIcon.MessageType.ERROR);}
            utilityPanel.setStatus("Unable to launch browser");
        } catch (final IOException e) {
            if (isSystemTraySupported())
                {trayIcon.displayMessage(null, "Failed to open URL", TrayIcon.MessageType.ERROR);}
            utilityPanel.setStatus("Unable to launch browser");
        } finally {
            serviceLock.unlock();
        }
    }

    void monex(Desktop desktop) {
        utilityPanel.setStatus("Opening Monitoring and Profiling in browser ...");
        serviceLock.lock();
        try {
            final int port = jetty.isPresent() ? jetty.get().getPrimaryPort() : 8080;
            final URI url = new URI("http://localhost:" + port + "/exist/apps/monex/");
            desktop.browse(url);
        } catch (final URISyntaxException e) {
            if (isSystemTraySupported())
                {trayIcon.displayMessage(null, "Failed to open URL", TrayIcon.MessageType.ERROR);}
            utilityPanel.setStatus("Unable to launch browser");
        } catch (final IOException e) {
            if (isSystemTraySupported())
                {trayIcon.displayMessage(null, "Failed to open URL", TrayIcon.MessageType.ERROR);}
            utilityPanel.setStatus("Unable to launch browser");
        } finally {
            serviceLock.unlock();
        }
    }

    void client() {
        final LauncherWrapper wrapper = new LauncherWrapper("client");
        wrapper.launch();
    }

    void signalStarted() {
        if (isSystemTraySupported()) {
            startItem.setEnabled(false);
            stopItem.setEnabled(true);
            checkInstalledApps();
            registerObserver();
        }
        if (SystemUtils.IS_OS_WINDOWS && !isInstallingService && !serviceManager.isInstalled()) {
            isInstallingService = true;
            SwingUtilities.invokeLater(() -> {
                if (JOptionPane.showConfirmDialog(splash, "It is recommended to run eXist-db as a service on " +
                                "Windows.\nNot doing so may lead to data loss if you shut down the computer before " +
                                "eXist-db.\n\nWould you like to install the service?", "Install as Service?",
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
                    SwingUtilities.invokeLater(() -> installAsService());
                }
            });
        }
    }

    void signalShutdown() {
        if (isSystemTraySupported()) {
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
                    Optional<ExistRepository> expathRepo = pool.getExpathRepo();
                    if (expathRepo.isPresent()) {
                        update(expathRepo.get(), notification);
                        utilityPanel.update(expathRepo.get(), notification);
                    }
                    expathRepo.orElseThrow(() -> new XPathException("expath repository is not available."));
                }
            }
        } catch (final EXistException e) {
            System.err.println("Failed to check installed packages: " + e.getMessage());
            e.printStackTrace();
        } catch (final XPathException e) {
            System.err.println("Failed to check installed packages: " + e.getMessage());
            e.printStackTrace();
        } catch (final PermissionDeniedException e) {
            System.err.println("Failed to check installed packages: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void registerObserver() {
        try {
            final BrokerPool pool = BrokerPool.getInstance();
	    Optional<ExistRepository> repo = pool.getExpathRepo();
	    if (repo.isPresent()) {
		repo.get().addObserver(this);
		repo.get().addObserver(utilityPanel);
	    } else {
		System.err.println("expath repository is not available.");
	    }
        } catch (final EXistException e) {
            System.err.println("Failed to register as observer for package manager events");
            e.printStackTrace();
        }
    }

    @Override
    public void update(Observable observable, Object o) {
        final ExistRepository.Notification notification = (ExistRepository.Notification) o;

        if (notification.getPackageURI().equals(PACKAGE_DASHBOARD) && dashboardItem != null) {
            dashboardItem.setEnabled(notification.getAction() == ExistRepository.Action.INSTALL);

        } else if (notification.getPackageURI().equals(PACKAGE_EXIDE) && eXideItem != null) {
            eXideItem.setEnabled(notification.getAction() == ExistRepository.Action.INSTALL);

        } else if (notification.getPackageURI().equals(PACKAGE_MONEX) && monexItem != null) {
            monexItem.setEnabled(notification.getAction() == ExistRepository.Action.INSTALL);
        }
    }

    private Path getJettyConfig() {
        final String jettyProperty = Optional.ofNullable(System.getProperty("jetty.home"))
                .orElseGet(() -> {
                    final Optional<Path> home = ConfigurationHelper.getExistHome();
                    final Path jettyHome = FileUtils.resolve(home, "tools").resolve("jetty");
                    final String jettyPath = jettyHome.toAbsolutePath().toString();
                    System.setProperty("jetty.home", jettyPath);
                    return jettyPath;
                });

        return Paths.get(jettyProperty)
                .normalize()
                .resolve("etc")
                .resolve(Main.STANDARD_ENABLED_JETTY_CONFIGS);
    }

    void showMessageAndExit(String title, String message, boolean logs) {
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

    void showTrayMessage(String message, TrayIcon.MessageType type) {
        if (isSystemTraySupported()) {
            trayIcon.displayMessage(message, message, type);
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
            public void write(int i) throws IOException {
                realStream.write(i);
                String s = String.valueOf((char) i);
                Launcher.this.setChanged();
                Launcher.this.notifyObservers(s);
            }

            @Override
            public void write(byte[] bytes) throws IOException {
                realStream.write(bytes);
                String s = new String(bytes);
                Launcher.this.setChanged();
                Launcher.this.notifyObservers(s);
            }

            @Override
            public void write(byte[] bytes, int offset, int len) throws IOException {
                realStream.write(bytes, offset, len);
                String s = new String(bytes, offset, len);
                Launcher.this.setChanged();
                Launcher.this.notifyObservers(s);
            }
        };
        return new PrintStream(out);
    }

    private class LogActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (!Desktop.isDesktopSupported())
                {return;}
            final Desktop desktop = Desktop.getDesktop();
            final Optional<Path> home = ConfigurationHelper.getExistHome();

            final Path logFile = FileUtils.resolve(home, "webapp/WEB-INF/logs/exist.log");

            if (!Files.isReadable(logFile)) {
                trayIcon.displayMessage(null, "Log file not found", TrayIcon.MessageType.ERROR);
            } else {
                try {
                    desktop.open(logFile.toFile());
                } catch (final IOException e) {
                    trayIcon.displayMessage(null, "Failed to open log file", TrayIcon.MessageType.ERROR);
                }
            }
        }
    }
}
