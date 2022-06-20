/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012-2014 The eXist-db Project
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

import org.exist.jetty.JettyStart;
import org.exist.storage.BrokerPool;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.URL;
import java.util.Observable;
import java.util.Observer;

import org.exist.ExistSystemProperties;

/**
 * Display a splash screen showing the eXist-db logo and a status line.
 *
 * @author Wolfgang Meier
 */
public class SplashScreen extends JFrame implements Observer, Comparable {

    private static final long serialVersionUID = -8449133653386075548L;

    private JLabel statusLabel;
    private JLabel versionLabel;
    private Launcher launcher;

    public SplashScreen(Launcher launcher) {
        this.launcher = launcher;
        setUndecorated(true);
        setBackground(new Color(255, 255, 255, 255));
        setAlwaysOnTop(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        getContentPane().setBackground(new Color(255, 255, 255, 255));

        final URL imageURL = SplashScreen.class.getResource("logo.png");
        final ImageIcon icon = new ImageIcon(imageURL, "eXist-db Logo");
        getContentPane().setLayout(new BorderLayout());

        // add the image label
        final JLabel imageLabel = new JLabel();
        imageLabel.setIcon(icon);
        final EmptyBorder border = new EmptyBorder(20, 20, 10, 20);
        imageLabel.setBorder(border);
        getContentPane().add(imageLabel, BorderLayout.NORTH);
        // version label
        final StringBuilder builder = new StringBuilder();
	builder.append("Version ");
        builder.append(ExistSystemProperties.getInstance().getExistSystemProperty(ExistSystemProperties.PROP_PRODUCT_VERSION, "unknown"));
	if (!"".equals(ExistSystemProperties.getInstance().getExistSystemProperty(ExistSystemProperties.PROP_GIT_COMMIT, ""))) {
	    builder.append(" (");
	    builder.append(ExistSystemProperties.getInstance().getExistSystemProperty(ExistSystemProperties.PROP_GIT_COMMIT, "(unknown Git commit ID)"));
	    builder.append(")");
	}
        versionLabel = new JLabel(builder.toString(), SwingConstants.CENTER);
        versionLabel.setFont(new Font(versionLabel.getFont().getName(), Font.BOLD, 10));
        versionLabel.setForeground(Color.black);
        versionLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
        versionLabel.setSize(new Dimension(icon.getIconWidth(), 60));

        getContentPane().add(versionLabel, BorderLayout.CENTER);

        // message label
        statusLabel = new JLabel("Launching ...", SwingConstants.CENTER);
        statusLabel.setFont(new Font(statusLabel.getFont().getName(), Font.PLAIN, 16));
        statusLabel.setForeground(Color.black);
        statusLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
        statusLabel.setSize(new Dimension(icon.getIconWidth(), 60));

        getContentPane().add(statusLabel, BorderLayout.SOUTH);
        // show it
        setSize(new Dimension(icon.getIconWidth() + 40, icon.getIconHeight() + 50));
        pack();
        this.setLocationRelativeTo(null);
        setVisible(true);
    }

    public void setStatus(final String status) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(status));
    }

    public void update(Observable o, Object arg) {
        if (JettyStart.SIGNAL_STARTED.equals(arg)) {
            setStatus("Server started!");
            setVisible(false);
            launcher.signalStarted();
        } else if (BrokerPool.SIGNAL_STARTUP.equals(arg)) {
            setStatus("Starting eXist-db ...");
        } else if (BrokerPool.SIGNAL_ABORTED.equals(arg)) {
            setVisible(false);
            launcher.showMessageAndExit("Startup aborted",
                "eXist-db detected an error during recovery. This may not be fatal, " +
                "but to avoid possible damage, the db will now stop. Please consider " +
                "running a consistency check via the export tool and create " +
                "a backup if problems are reported. The db should come up again if you restart " +
                "it.", true);
        } else if (BrokerPool.SIGNAL_WRITABLE.equals(arg)) {
            setStatus("eXist-db is up. Waiting for web server ...");
        } else if (JettyStart.SIGNAL_ERROR.equals(arg)) {
            setVisible(false);
            launcher.showMessageAndExit("Error Occurred",
                    "An error occurred during startup. Please check the logs.", true);
        } else if (BrokerPool.SIGNAL_SHUTDOWN.equals(arg)) {
            launcher.signalShutdown();
        } else {
            setStatus(arg.toString());
        }
    }

    @Override
    public int compareTo(Object other) {
        return other == this ? 0 : -1;
    }
}
