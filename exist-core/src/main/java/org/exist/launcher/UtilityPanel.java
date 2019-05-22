/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2012 The eXist-db Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.launcher;

import org.exist.repo.ExistRepository;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.Observable;
import java.util.Observer;

public class UtilityPanel extends JFrame implements Observer {

    private TextArea messages;
    private JLabel statusLabel;
    private JButton dashboardButton;
    private JButton eXideButton;

    public UtilityPanel(final Launcher launcher, final boolean traySupported, final boolean hideOnStart) {
        this.setAlwaysOnTop(false);

        BufferedImage image = null;
        try {
            image = ImageIO.read(getClass().getResource("icon32.png"));
        } catch (final IOException e) {
        }
        this.setIconImage(image);

        if (!traySupported) {
            setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        }

        getContentPane().setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();

        setBackground(new Color(255, 255, 255, 255));

        final JToolBar toolbar = new JToolBar();
        toolbar.setOpaque(false);
        toolbar.setBorderPainted(false);
        //toolbar.setBackground(new Color(255, 255, 255, 255));

        JButton button;

        if (Desktop.isDesktopSupported()) {
            final Desktop desktop = Desktop.getDesktop();

            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                dashboardButton = createButton(toolbar, "dashboard.png", "Dashboard");
                dashboardButton.setEnabled(false);
                dashboardButton.addActionListener(actionEvent -> launcher.dashboard(desktop));
                toolbar.add(dashboardButton);

                eXideButton = createButton(toolbar, "exide.png", "eXide");
                eXideButton.setEnabled(false);
                eXideButton.addActionListener(actionEvent -> launcher.eXide(desktop));
                toolbar.add(eXideButton);
            }
        }

        button = createButton(toolbar, "browsing.png", "Java Client");
        button.addActionListener(actionEvent -> launcher.client());
        toolbar.add(button);

        button = createButton(toolbar, "shutdown.png", "Quit");
        button.addActionListener(actionEvent -> launcher.shutdown(false));
        toolbar.add(button);

        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        getContentPane().add(toolbar, c);

        statusLabel = new JLabel("", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Dialog", Font.PLAIN, 10));
        statusLabel.setPreferredSize(new Dimension(200, 16));
        //statusLabel.setMinimumSize(new Dimension(200, 16));
        if (!traySupported) {
            statusLabel.setText("System tray icon not supported.");
        }

        c.gridy = 1;
        getContentPane().add(statusLabel, c);

        final JCheckBox showMessages = new JCheckBox("Show console messages");
        showMessages.setHorizontalAlignment(SwingConstants.LEFT);
        showMessages.setOpaque(false);
        showMessages.addItemListener(itemEvent -> {
            final boolean showMessages1 = itemEvent.getStateChange() == ItemEvent.SELECTED;
            if (showMessages1) {
                messages.setVisible(true);
            } else {
                messages.setVisible(false);
            }
            UtilityPanel.this.pack();
        });
        c.gridy = 2;
        getContentPane().add(showMessages, c);

        Font messagesFont = new Font("Monospaced", Font.PLAIN, 12);
        messages = new TextArea();
        messages.setBackground(new Color(20,20, 20, 255));
        messages.setPreferredSize(new Dimension(800, 200));
        messages.setForeground(new Color(255, 255, 255));
        messages.setFont(messagesFont);

        c.gridy = 3;
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;

        getContentPane().add(messages, c);
        messages.setVisible(false);

        setMinimumSize(new Dimension(350, 90));
        pack();

        final Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
        this.setLocation(d.width - this.getWidth() - 40, 60);

        launcher.addObserver(this);

        if (!hideOnStart) {
            setVisible(true);
            toFront();
        }
    }

    private JButton createButton(JToolBar toolbar, String image, String title) {
        final URL imageURL = UtilityPanel.class.getResource(image);
        final ImageIcon icon = new ImageIcon(imageURL, title);
        final JButton button = new JButton(title, icon);

        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.setVerticalTextPosition(SwingConstants.BOTTOM);
        //button.setBorder(new EmptyBorder(10, 10, 10, 10));
        //button.setBackground(new Color(255, 255, 255, 0));
        return button;
    }

    protected void showMessages() {
        messages.setVisible(true);
        UtilityPanel.this.pack();
    }

    protected void setStatus(final String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(message));
    }

    @Override
    public void update(Observable observable, final Object o) {
        if (o instanceof ExistRepository.Notification) {
            final ExistRepository.Notification notification = (ExistRepository.Notification) o;
            if (notification.getPackageURI().equals(Launcher.PACKAGE_DASHBOARD) && dashboardButton != null) {
                dashboardButton.setEnabled(notification.getAction() == ExistRepository.Action.INSTALL);
            } else if (notification.getPackageURI().equals(Launcher.PACKAGE_EXIDE) && eXideButton != null) {
                eXideButton.setEnabled(notification.getAction() == ExistRepository.Action.INSTALL);
            }
        } else {
            SwingUtilities.invokeLater(() -> messages.append(o.toString()));
        }
    }
}