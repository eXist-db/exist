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

import org.apache.log4j.Logger;
import org.exist.jetty.JettyStart;
import org.exist.storage.BrokerPool;
import org.exist.util.ConfigurationHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
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
public class Launcher implements Observer {

    private final static Logger LOG = Logger.getLogger(Launcher.class);

    private MenuItem stopItem;
    private MenuItem startItem;

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
        UIManager.put("swing.boldMetal", Boolean.FALSE);
        //Schedule a job for the event-dispatching thread:
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new Launcher(args);
            }
        });
    }

    private TrayIcon trayIcon = null;
    private SplashScreen splash;
    private JettyStart jetty;

    public Launcher(final String[] args) {
        if (!SystemTray.isSupported()) {
            showMessageAndExit("Not supported", "Running eXist-db via the launcher is not supported on your platform. " +
                    "Please run it using startup.sh/startup.bat.");
            return;
        }
        final String home = getJettyHome();
        captureConsole();

        splash = new SplashScreen();
        splash.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent windowEvent) {
                try {
                    jetty = new JettyStart();
                    jetty.addObserver(Launcher.this);
                    jetty.run(new String[]{home}, Launcher.this);
                } catch (Exception e) {
                    showMessageAndExit("Error Occurred", "An error occurred during startup. Please check the logs.");
                }
            }
        });

        final SystemTray tray = SystemTray.getSystemTray();
        Dimension iconDim = tray.getTrayIconSize();
        LOG.debug("Icon size: " + iconDim.getWidth() + "x" + iconDim.getHeight());
        String iconFile;
        if (iconDim.getWidth() <= 16.0)
            iconFile = "icon16.png";
        else if (iconDim.getWidth() <= 24.0)
            iconFile = "icon24.png";
        else
            iconFile = "icon32.png";
        LOG.debug("Using icon: " + iconFile);
        Image image = Toolkit.getDefaultToolkit().getImage(Launcher.class.getResource(iconFile));
        trayIcon = new TrayIcon(image, "eXist-db Launcher");
        trayIcon.setImageAutoSize(true);

        PopupMenu popup = createMenu(home, tray);
        trayIcon.setPopupMenu(popup);
        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            showMessageAndExit("Launcher failed", "Tray icon could not be added");
            return;
        }
        trayIcon.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                trayIcon.displayMessage(null, "Right click for menu", TrayIcon.MessageType.INFO);
            }
        });
    }

    private PopupMenu createMenu(final String home, final SystemTray tray) {
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
                jetty.run(new String[]{home}, Launcher.this);
                stopItem.setEnabled(true);
                startItem.setEnabled(false);
                trayIcon.setToolTip("eXist-db server running on port " + jetty.getPrimaryPort());
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

        MenuItem item;

        if (Desktop.isDesktopSupported()) {
            popup.addSeparator();
            final Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                item = new MenuItem("Open dashboard");
                popup.add(item);
                item.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        try {
                            URI url = new URI("http://localhost:" + jetty.getPrimaryPort() + "/exist/apps/dashboard/");
                            desktop.browse(url);
                        } catch (URISyntaxException e) {
                            trayIcon.displayMessage(null, "Failed to open URL", TrayIcon.MessageType.ERROR);
                        } catch (IOException e) {
                            trayIcon.displayMessage(null, "Failed to open URL", TrayIcon.MessageType.ERROR);
                        }
                    }
                });
                item = new MenuItem("Open eXide");
                popup.add(item);
                item.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        try {
                            URI url = new URI("http://localhost:" + jetty.getPrimaryPort() + "/exist/eXide/");
                            desktop.browse(url);
                        } catch (URISyntaxException e) {
                            trayIcon.displayMessage(null, "Failed to open URL", TrayIcon.MessageType.ERROR);
                        } catch (IOException e) {
                            trayIcon.displayMessage(null, "Failed to open URL", TrayIcon.MessageType.ERROR);
                        }
                    }
                });
                item = new MenuItem("Open Java Admin Client");
                popup.add(item);
                item.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        LauncherWrapper wrapper = new LauncherWrapper("client");
                        wrapper.launch();
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
                jetty.shutdown();
                tray.remove(trayIcon);
                System.exit(0);
                }
            });
        }
        return popup;
    }

    public void update(Observable o, Object arg) {
        if (splash == null)
            return;
        if (JettyStart.SIGNAL_STARTED.equals(arg)) {
            trayIcon.setToolTip("eXist-db server running on port " + jetty.getPrimaryPort());
            splash.setStatus("Server started!");
            splash.setVisible(false);
            startItem.setEnabled(false);
            stopItem.setEnabled(true);
            splash = null;
        } else if (BrokerPool.SIGNAL_STARTUP.equals(arg)) {
            splash.setStatus("Starting eXist-db ...");
        } else if (BrokerPool.SIGNAL_WRITABLE.equals(arg)) {
            splash.setStatus("eXist-db is up. Waiting for web server ...");
        } else if (JettyStart.SIGNAL_ERROR.equals(arg)) {
            splash.setStatus("An error occurred! Please check the logs.");
            showMessageAndExit("Error Occurred",
                "An error occurred during startup. Please check the logs.");
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

    private void showMessageAndExit(String title, String message) {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        JLabel label = new JLabel(message);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(label, BorderLayout.CENTER);
        JButton displayLogs = new JButton("View Log");
        displayLogs.addActionListener(new LogActionListener());
        label.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(displayLogs, BorderLayout.SOUTH);
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

    public static PrintStream createLoggingProxy(final PrintStream realStream) {
        return new PrintStream(realStream) {
            @Override
            public void print(String s) {
                realStream.print(s);
                LOG.info(s);
            }
        };
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
