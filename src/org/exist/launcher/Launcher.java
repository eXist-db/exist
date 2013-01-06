/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist-db Project
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

import org.exist.EXistException;
import org.exist.jetty.JettyStart;
import org.exist.repo.ExistRepository;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.ConfigurationHelper;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Observable;
import java.util.Observer;

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

    public final static String PACKAGE_DASHBOARD = "http://exist-db.org/apps/dashboard";
    public final static String PACKAGE_EXIDE = "http://exist-db.org/apps/eXide";

    public static void main(final String[] args) {
        String os = System.getProperty("os.name", "");
        // Switch to native look and feel except for Linux (ugly)
        if (!os.equals("Linux")) {
            String nativeLF = UIManager.getSystemLookAndFeelClassName();
            try {
                UIManager.setLookAndFeel(nativeLF);
            } catch (Exception e) {
                // can be safely ignored
            }
        }
        /* Turn off metal's use of bold fonts */
        //UIManager.put("swing.boldMetal", Boolean.FALSE);

        //Schedule a job for the event-dispatching thread:
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new Launcher(args);
            }
        });
    }

    private SystemTray tray = null;
    private TrayIcon trayIcon = null;
    private boolean initSystemTray = true;
    private SplashScreen splash;
    private JettyStart jetty;

    private UtilityPanel utilityPanel;

    public Launcher(final String[] args) {
        if (SystemTray.isSupported()) {
            tray = SystemTray.getSystemTray();
        }

        captureConsole();

        final String home = getJettyHome();

        if (isSystemTraySupported())
            initSystemTray = initSystemTray(home);


        splash = new SplashScreen(this);
        splash.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent windowEvent) {
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            jetty = new JettyStart();
                            jetty.addObserver(splash);
                            jetty.run(new String[]{home}, splash);
                        } catch (Exception e) {
                            showMessageAndExit("Error Occurred", "An error occurred during eXist-db startup. Please check the logs.", true);
                            System.exit(1);
                        }
                    }
                }.start();
            }
        });

        final boolean systemTrayReady = tray != null && initSystemTray && tray.getTrayIcons().length > 0;

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                utilityPanel = new UtilityPanel(Launcher.this, systemTrayReady);
            }
        });
    }

    public boolean isSystemTraySupported() {
        return tray != null;
    }

    private boolean initSystemTray(String home) {
        Dimension iconDim = tray.getTrayIconSize();
        BufferedImage image = null;
        try {
            image = ImageIO.read(getClass().getResource("icon32.png"));
        } catch (IOException e) {
            showMessageAndExit("Launcher failed", "Failed to read system tray icon.", false);
        }
        trayIcon = new TrayIcon(image.getScaledInstance(iconDim.width, iconDim.height, Image.SCALE_SMOOTH), "eXist-db Launcher");

        final JDialog hiddenFrame = new JDialog();
        hiddenFrame.setUndecorated(true);
        hiddenFrame.setIconImage(image);
        
        final PopupMenu popup = createMenu(home);
        trayIcon.setPopupMenu(popup);

        trayIcon.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                trayIcon.displayMessage(null, "Right click for menu", TrayIcon.MessageType.INFO);
            }
        });

        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                if (mouseEvent.getButton() == MouseEvent.BUTTON1) {
                    hiddenFrame.add(popup);
                    popup.show(hiddenFrame, mouseEvent.getXOnScreen(), mouseEvent.getYOnScreen());
                }
            }
        });

        try {
            hiddenFrame.setResizable(false);
            hiddenFrame.pack();
            hiddenFrame.setVisible(true);
            tray.add(trayIcon);
        } catch (AWTException e) {
            return false;
        }

        return true;
    }

    private PopupMenu createMenu(final String home) {
        PopupMenu popup = new PopupMenu();
        startItem = new MenuItem("Start server");
        startItem.setEnabled(false);
        popup.add(startItem);
        startItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
            if (jetty.isStarted()) {
                trayIcon.displayMessage(null, "Server already started", TrayIcon.MessageType.WARNING);
            } else {
                jetty.run(new String[]{home}, null);
                if (jetty.isStarted()) {
                    stopItem.setEnabled(true);
                    startItem.setEnabled(false);
                    trayIcon.setToolTip("eXist-db server running on port " + jetty.getPrimaryPort());
                }
            }
            }
        });

        stopItem = new MenuItem("Stop server");
        popup.add(stopItem);
        stopItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                jetty.shutdown();
                stopItem.setEnabled(false);
                startItem.setEnabled(true);
                trayIcon.setToolTip("eXist-db stopped");
            }
        });

        popup.addSeparator();

        MenuItem toolbar = new MenuItem("Show Tool Window");
        popup.add(toolbar);
        toolbar.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                utilityPanel.setVisible(true);
            }
        });

        MenuItem item;

        if (Desktop.isDesktopSupported()) {
            popup.addSeparator();
            final Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                dashboardItem = new MenuItem("Open dashboard");
                dashboardItem.setEnabled(false);
                popup.add(dashboardItem);
                dashboardItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        dashboard(desktop);
                    }
                });
                eXideItem = new MenuItem("Open eXide");
                eXideItem.setEnabled(false);
                popup.add(eXideItem);
                eXideItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        eXide(desktop);
                    }
                });
                item = new MenuItem("Open Java Admin Client");
                popup.add(item);
                item.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        client();
                    }
                });
            }
            if (desktop.isSupported(Desktop.Action.OPEN)) {
                popup.addSeparator();
                item = new MenuItem("Open exist.log");
                popup.add(item);
                item.addActionListener(new LogActionListener());
            }

            popup.addSeparator();
            item = new MenuItem("Quit (and stop server)");
            popup.add(item);
            item.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    shutdown();
                }
            });
        }
        return popup;
    }

    protected void shutdown() {
        utilityPanel.setStatus("Shutting down ...");
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                jetty.shutdown();
                if (tray != null)
                    tray.remove(trayIcon);
                System.exit(0);
            }
        });
    }

    protected void dashboard(Desktop desktop) {
        utilityPanel.setStatus("Opening dashboard in browser ...");
        try {
            URI url = new URI("http://localhost:" + jetty.getPrimaryPort() + "/exist/apps/dashboard/");
            desktop.browse(url);
        } catch (URISyntaxException e) {
            if (isSystemTraySupported())
                trayIcon.displayMessage(null, "Failed to open URL", TrayIcon.MessageType.ERROR);
            utilityPanel.setStatus("Unable to launch browser");
        } catch (IOException e) {
            if (isSystemTraySupported())
                trayIcon.displayMessage(null, "Failed to open URL", TrayIcon.MessageType.ERROR);
            utilityPanel.setStatus("Unable to launch browser");
        }
    }

    protected void eXide(Desktop desktop) {
        utilityPanel.setStatus("Opening dashboard in browser ...");
        try {
            URI url = new URI("http://localhost:" + jetty.getPrimaryPort() + "/exist/apps/eXide/");
            desktop.browse(url);
        } catch (URISyntaxException e) {
            if (isSystemTraySupported())
                trayIcon.displayMessage(null, "Failed to open URL", TrayIcon.MessageType.ERROR);
            utilityPanel.setStatus("Unable to launch browser");
        } catch (IOException e) {
            if (isSystemTraySupported())
                trayIcon.displayMessage(null, "Failed to open URL", TrayIcon.MessageType.ERROR);
            utilityPanel.setStatus("Unable to launch browser");
        }
    }

    protected void client() {
        LauncherWrapper wrapper = new LauncherWrapper("client");
        wrapper.launch();
    }

    protected void signalStarted() {
        if (isSystemTraySupported()) {
            trayIcon.setToolTip("eXist-db server running on port " + jetty.getPrimaryPort());
            startItem.setEnabled(false);
            stopItem.setEnabled(true);
            checkInstalledApps();
            registerObserver();
        }
    }

    protected void signalShutdown() {
        if (isSystemTraySupported()) {
            trayIcon.setToolTip("eXist-db server stopped");
            startItem.setEnabled(true);
            stopItem.setEnabled(false);
        }
    }

    private void checkInstalledApps() {
        BrokerPool pool = null;
        DBBroker broker = null;
        try {
            pool = BrokerPool.getInstance();
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            XQuery xquery = broker.getXQueryService();
            Sequence pkgs = xquery.execute("repo:list()", null, AccessContext.INITIALIZE);
            for (SequenceIterator i = pkgs.iterate(); i.hasNext(); ) {
                ExistRepository.Notification notification = new ExistRepository.Notification(ExistRepository.Action.INSTALL, i.nextItem().getStringValue());
                update(pool.getExpathRepo(), notification);
                utilityPanel.update(pool.getExpathRepo(), notification);
            }
        } catch (EXistException e) {
            System.err.println("Failed to check installed packages: " + e.getMessage());
            e.printStackTrace();
        } catch (XPathException e) {
            System.err.println("Failed to check installed packages: " + e.getMessage());
            e.printStackTrace();
        } catch (PermissionDeniedException e) {
            System.err.println("Failed to check installed packages: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (pool != null)
                pool.release(broker);
        }
    }

    private void registerObserver() {
        try {
            BrokerPool pool = BrokerPool.getInstance();
            pool.getExpathRepo().addObserver(this);
            pool.getExpathRepo().addObserver(utilityPanel);
        } catch (EXistException e) {
            System.err.println("Failed to register as observer for package manager events");
            e.printStackTrace();
        }
    }

    @Override
    public void update(Observable observable, Object o) {
        ExistRepository.Notification notification = (ExistRepository.Notification) o;
        if (notification.getPackageURI().equals(PACKAGE_DASHBOARD)) {
            dashboardItem.setEnabled(notification.getAction() == ExistRepository.Action.INSTALL);
        } else if (notification.getPackageURI().equals(PACKAGE_EXIDE)) {
            eXideItem.setEnabled(notification.getAction() == ExistRepository.Action.INSTALL);
        }
    }

    private String getJettyHome() {
        String jettyProperty = System.getProperty("jetty.home");
        if(jettyProperty==null) {
            File home = ConfigurationHelper.getExistHome();
            File jettyHome = new File(new File(home, "tools"), "jetty");
            jettyProperty = jettyHome.getAbsolutePath();
            System.setProperty("jetty.home", jettyProperty);
        }
        File standaloneFile = new File(new File(jettyProperty, "etc"), "jetty.xml");
        return standaloneFile.getAbsolutePath();
    }

    protected void showMessageAndExit(String title, String message, boolean logs) {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        JLabel label = new JLabel(message);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(label, BorderLayout.CENTER);
        if (logs) {
            JButton displayLogs = new JButton("View Log");
            displayLogs.addActionListener(new LogActionListener());
            label.setHorizontalAlignment(SwingConstants.CENTER);
            panel.add(displayLogs, BorderLayout.SOUTH);
        }
        JOptionPane.showMessageDialog(splash, panel, title, JOptionPane.WARNING_MESSAGE);
        System.exit(1);
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
        OutputStream out = new OutputStream() {
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
                return;
            Desktop desktop = Desktop.getDesktop();
            File home = ConfigurationHelper.getExistHome();
            File logFile = new File(home, "webapp/WEB-INF/logs/exist.log");
            if (!logFile.canRead()) {
                trayIcon.displayMessage(null, "Log file not found", TrayIcon.MessageType.ERROR);
            } else {
                try {
                    desktop.open(logFile);
                } catch (IOException e) {
                    trayIcon.displayMessage(null, "Failed to open log file", TrayIcon.MessageType.ERROR);
                }
            }
        }
    }
}
