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
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.ConfigurationHelper;
import org.exist.util.FileUtils;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.tanukisoftware.wrapper.WrapperManager;
import org.tanukisoftware.wrapper.WrapperWin32Service;

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
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

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

    private SystemTray tray = null;
    private TrayIcon trayIcon = null;
    private boolean initSystemTray = true;
    private Optional<WrapperWin32Service> runningAsService = Optional.empty();
    private SplashScreen splash;
    private Optional<JettyStart> jetty = Optional.empty();
    private String home;
    private Path wrapperBin;
    private boolean inServiceInstall = false;
    private UtilityPanel utilityPanel;
    private ConfigurationDialog configDialog;

    public Launcher(final String[] args) {
        if (SystemTray.isSupported()) {
            tray = SystemTray.getSystemTray();
        }

        captureConsole();

        home = getJettyHome();
        final Optional<Path> eXistHome = ConfigurationHelper.getExistHome();
        if (eXistHome.isPresent()) {
            wrapperBin = eXistHome.get().resolve("tools/wrapper/bin");
        } else {
            wrapperBin = Paths.get("tools/wrapper/bin");
        }

        if (isSystemTraySupported())
            {initSystemTray = initSystemTray(home);}

        configDialog = new ConfigurationDialog(Launcher.this);

        splash = new SplashScreen(this);
        splash.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent windowEvent) {
                setServiceState();
                if (runningAsService.isPresent()) {
                    splash.setStatus("eXist-db is already installed as service!");
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
            }
        });

        final boolean systemTrayReady = tray != null && initSystemTray && tray.getTrayIcons().length > 0;

        SwingUtilities.invokeLater(() -> utilityPanel = new UtilityPanel(Launcher.this, systemTrayReady));
    }

    protected void startJetty() {
        new Thread() {
            @Override
            public void run() {
                try {
                    if (!jetty.isPresent()) {
                        jetty = Optional.of(new JettyStart());
                        jetty.get().run(new String[]{home}, splash);
                    }
                } catch (final Exception e) {
                    showMessageAndExit("Error Occurred", "An error occurred during eXist-db startup. Please check console output and logs.", true);
                    System.exit(1);
                }
            }
        }.start();
    }

    public boolean isSystemTraySupported() {
        return tray != null;
    }

    private boolean initSystemTray(String home) {
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

        final PopupMenu popup = createMenu(home);
        trayIcon.setPopupMenu(popup);

        trayIcon.addActionListener(actionEvent -> trayIcon.displayMessage(null, "Right click for menu", TrayIcon.MessageType.INFO));

        // add listener for left click on system tray icon. doesn't work well on linux though.
        final String os = System.getProperty("os.name", "");
        if (!os.equals("Linux")) {
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

    private PopupMenu createMenu(final String home) {


        final PopupMenu popup = new PopupMenu();
        startItem = new MenuItem("Start server");
        popup.add(startItem);
        startItem.addActionListener(actionEvent -> {
            if (jetty.isPresent()) {
                jetty.ifPresent(server -> {
                    if (server.isStarted()) {
                        showTrayMessage("Server already started", TrayIcon.MessageType.WARNING);
                    } else {
                        server.run(new String[]{home}, null);
                        if (server.isStarted()) {
                            showTrayMessage("eXist-db server running on port " + server.getPrimaryPort(), TrayIcon
                                    .MessageType.INFO);
                        }
                    }
                    setServiceState();
                });
            } else {
                SwingUtilities.invokeLater(() -> {
                    showTrayMessage("Starting the eXistdb service. Please wait...", TrayIcon.MessageType.INFO);
                    List<String> argsStart = new ArrayList<>();
                    argsStart.add(wrapperBin.resolve("control.bat").toString());
                    argsStart.add("--start");
                    run(argsStart, (code, message) -> {
                        if (code > 0) {
                            JOptionPane.showMessageDialog(null, "Error:\n" + message, "Starting Server Failed",
                                    JOptionPane.ERROR_MESSAGE);
                        } else {
                            showTrayMessage("eXistdb service started", TrayIcon.MessageType.INFO);
                        }
                        setServiceState();
                    });
                });
            }
        });

        stopItem = new MenuItem("Stop server");
        popup.add(stopItem);
        stopItem.addActionListener(actionEvent -> {
            if (jetty.isPresent()) {
                jetty.get().shutdown();
                setServiceState();
                showTrayMessage("eXist-db stopped", TrayIcon.MessageType.INFO);
            } else {
                SwingUtilities.invokeLater(() -> {
                    List<String> argsStart = new ArrayList<>();
                    argsStart.add(wrapperBin.resolve("control.bat").toString());
                    argsStart.add("--stop");
                    showTrayMessage("Stopping the eXistdb service. Please wait...", TrayIcon.MessageType.INFO);
                    run(argsStart, (code, message) -> {
                        if (code > 0) {
                            JOptionPane.showMessageDialog(null, "Error:\n" + message, "Stopping Server Failed",
                                    JOptionPane.ERROR_MESSAGE);
                        } else {
                            showTrayMessage("eXistdb stopped", TrayIcon.MessageType.INFO);
                        }
                        setServiceState();
                    });
                });
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

        if (SystemUtils.IS_OS_WINDOWS) {
            installServiceItem = new MenuItem("Install as service");
            popup.add(installServiceItem);
            installServiceItem.addActionListener(e -> SwingUtilities.invokeLater(() -> installAsService()));

            uninstallServiceItem = new MenuItem("Uninstall service");
            popup.add(uninstallServiceItem);
            uninstallServiceItem.addActionListener(e -> SwingUtilities.invokeLater(() -> uninstallService()));
        }

        popup.addSeparator();

        final MenuItem toolbar = new MenuItem("Show Tool Window");
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
                dashboardItem = new MenuItem("Open dashboard");
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
            quitItem = new MenuItem("Quit (and stop server)");
            popup.add(quitItem);
            quitItem.addActionListener(actionEvent -> shutdown(false));

            if (SystemUtils.IS_OS_WINDOWS) {
                setServiceState();
            }
        }
        return popup;
    }

    protected void installAsService() {
        final List<String> args = new ArrayList<>();
        args.add(wrapperBin.resolve("install.bat").toString());

        jetty.ifPresent(server -> {
            if (server.isStarted()) {
                showTrayMessage("Stopping eXistdb...", TrayIcon.MessageType.INFO);
                server.shutdown();
            }
        });
        jetty = Optional.empty();

        showTrayMessage("Installing service and starting eXistdb ...", TrayIcon.MessageType.INFO);
        run(args, (exitCode, output) -> {
            if (exitCode > 0) {
                JOptionPane.showMessageDialog(null, output, "Install Service Failed", JOptionPane.ERROR_MESSAGE);
            } else {
                runningAsService = installedAsService();
                setServiceState();
                inServiceInstall = false;
                showTrayMessage("Service installed and started", TrayIcon.MessageType.INFO);
            }
        });
    }

    protected void uninstallService() {
        SwingUtilities.invokeLater(() -> {
            final List<String> args = new ArrayList<>();
            args.add(wrapperBin.resolve("uninstall.bat").toString());
            run(args, (exitCode, output) -> {
                if (exitCode > 0) {
                    showTrayMessage("Service removed and db stopped", TrayIcon.MessageType.INFO);
                } else {
                    JOptionPane.showMessageDialog(null, "Error:\n" + output, "Removing Service Failed", JOptionPane.ERROR_MESSAGE);
                }
            });
        });
    }

    private void setServiceState() {
        runningAsService = installedAsService();
        if (runningAsService.isPresent()) {
            final int state = runningAsService.get().getServiceState();
            final boolean serverRunning = state == WrapperWin32Service.SERVICE_STATE_RUNNING || state ==
                    WrapperWin32Service
                    .SERVICE_STATE_START_PENDING || state == WrapperWin32Service.SERVICE_STATE_CONTINUE_PENDING;
            if (installServiceItem != null && uninstallServiceItem != null) {
                installServiceItem.setEnabled(false);
                uninstallServiceItem.setEnabled(true);
            }
            quitItem.setLabel("Quit");
            stopItem.setEnabled(serverRunning);
            startItem.setEnabled(!serverRunning);
            dashboardItem.setEnabled(serverRunning);
            monexItem.setEnabled(serverRunning);
            eXideItem.setEnabled(serverRunning);
        } else {
            final boolean serverRunning = jetty.isPresent() && jetty.get().isStarted();
            if (installServiceItem != null && uninstallServiceItem != null) {
                installServiceItem.setEnabled(true);
                uninstallServiceItem.setEnabled(false);
            }
            quitItem.setLabel("Quit (and stop server)");
            stopItem.setEnabled(serverRunning);
            startItem.setEnabled(!serverRunning);
            dashboardItem.setEnabled(serverRunning);
            monexItem.setEnabled(serverRunning);
            eXideItem.setEnabled(serverRunning);
        }
    }

    private void run(List<String> args, BiConsumer<Integer, String> consumer) {
        final ProcessBuilder pb = new ProcessBuilder(args);
        final Optional<Path> home = ConfigurationHelper.getExistHome();
        if (home.isPresent()) {
            pb.directory(home.get().toFile());
        }
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append('\n').append(line);
                }
            }
            int exitValue = process.waitFor();
            consumer.accept(exitValue, output.toString());
        } catch (IOException | InterruptedException e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Install Service", JOptionPane.ERROR_MESSAGE);
        }
    }

    protected void shutdown(final boolean restart) {
        utilityPanel.setStatus("Shutting down ...");
        SwingUtilities.invokeLater(() -> {
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
            System.exit(0);
        });
    }

    protected void dashboard(Desktop desktop) {
        utilityPanel.setStatus("Opening dashboard in browser ...");
        final int port = jetty.isPresent() ? jetty.get().getPrimaryPort() : 8080;
        try {
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
        }
    }

    protected void eXide(Desktop desktop) {
        utilityPanel.setStatus("Opening dashboard in browser ...");
        final int port = jetty.isPresent() ? jetty.get().getPrimaryPort() : 8080;
        try {
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
        }
    }

    protected void monex(Desktop desktop) {
        utilityPanel.setStatus("Opening Monitoring and Profiling in browser ...");
        final int port = jetty.isPresent() ? jetty.get().getPrimaryPort() : 8080;
        try {
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
        }
    }

    protected void client() {
        final LauncherWrapper wrapper = new LauncherWrapper("client");
        wrapper.launch();
    }

    protected void signalStarted() {
        if (isSystemTraySupported()) {
            final int port = jetty.isPresent() ? jetty.get().getPrimaryPort() : 8080;
            trayIcon.setToolTip("eXist-db server running on port " + port);
            startItem.setEnabled(false);
            stopItem.setEnabled(true);
            checkInstalledApps();
            registerObserver();
        }
        if (!inServiceInstall && !runningAsService.isPresent() && SystemUtils.IS_OS_WINDOWS) {
            inServiceInstall = true;
            SwingUtilities.invokeLater(() -> {
                if (JOptionPane.showConfirmDialog(splash, "It is recommended to run eXist as a service on " +
                                "Windows.\nNot doing so may lead to data loss if you shut down the computer before " +
                                "eXist.\n\nWould you like to install the service?", "Install as Service?",
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
                    installAsService();
                }
            });
        }
    }

    protected void signalShutdown() {
        if (isSystemTraySupported()) {
            trayIcon.setToolTip("eXist-db server stopped");
            if (!inServiceInstall) {
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
                final Sequence pkgs = xquery.execute(broker, "repo:list()", null, AccessContext.INITIALIZE);
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

    private String getJettyHome() {
        final String jettyProperty = Optional.ofNullable(System.getProperty("jetty.home"))
                .orElseGet(() -> {
                    final Optional<Path> home = ConfigurationHelper.getExistHome();
                    final Path jettyHome = FileUtils.resolve(home, "tools").resolve("jetty");
                    final String jettyPath = jettyHome.toAbsolutePath().toString();
                    System.setProperty("jetty.home", jettyPath);
                    return jettyPath;
                });

        final Path standaloneFile = Paths.get(jettyProperty).resolve("etc").resolve("jetty.xml");
        return standaloneFile.toAbsolutePath().toString();
    }

    protected void showMessageAndExit(String title, String message, boolean logs) {
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
        //System.exit(1);
    }

    protected void showTrayMessage(String message, TrayIcon.MessageType type) {
        if (isSystemTraySupported()) {
            trayIcon.displayMessage(null, message, type);
        }
    }

    private Optional<WrapperWin32Service> installedAsService() {
        if (SystemUtils.IS_OS_WINDOWS) {
            WrapperWin32Service[] services = WrapperManager.listServices();
            return Stream.of(services).filter(service -> service.getName().equals("eXist-db")).findFirst();
        }
        return Optional.empty();
    }

    /**
     * Ensure that stdout and stderr messages are also printed
     * to the logs.
     */
    private void captureConsole() {
        System.setOut(createLoggingProxy(System.out));
        System.setErr(createLoggingProxy(System.err));
    }

    public PrintStream createLoggingProxy(final PrintStream realStream) {
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
