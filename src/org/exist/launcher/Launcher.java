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
        String nativeLF = UIManager.getSystemLookAndFeelClassName();
        try {
            UIManager.setLookAndFeel(nativeLF);
        } catch (Exception e) {
            // can be safely ignored
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
            System.out.println("Not supported");
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
                    showMessageAndExit("Error Occurred",
                            "An error occurred during startup. Please check the logs. " +
                                    "By default these should be in EXIST_HOME/webapp/WEB-INF/logs/exist.log");
                }
            }
        });

        Image image = new ImageIcon("icon.png", "eXist-db Logo").getImage();
        trayIcon = new TrayIcon(image);
        final SystemTray tray = SystemTray.getSystemTray();
        PopupMenu popup = new PopupMenu();
        startItem = new MenuItem("Start");
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
                }
            }
        });

        stopItem = new MenuItem("Stop");
        popup.add(stopItem);
        stopItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                jetty.shutdown();
                stopItem.setEnabled(false);
                startItem.setEnabled(true);
            }
        });

        MenuItem item;

        if (Desktop.isDesktopSupported()) {
            popup.addSeparator();
            final Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                item = new MenuItem("Open Browser");
                popup.add(item);
                item.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        try {
                            URI url = new URI("http://localhost:" + jetty.getPrimaryPort() + "/exist/");
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
            }
            if (desktop.isSupported(Desktop.Action.OPEN)) {
                popup.addSeparator();
                item = new MenuItem("exist.log");
                popup.add(item);
                item.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
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
                });
            }

            popup.addSeparator();
            item = new MenuItem("Quit (stop db)");
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

        trayIcon.setPopupMenu(popup);
        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            System.out.println("Tray icon could not be added");
            return;
        }
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
                "An error occurred during startup. Please check the logs.\n" +
                "By default these should be in EXIST_HOME/webapp/WEB-INF/logs/exist.log");
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
        JOptionPane.showMessageDialog(splash, message, title, JOptionPane.WARNING_MESSAGE);
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
}
