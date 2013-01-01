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
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.io.IOException;
import java.util.Observable;
import java.util.Observer;

public class UtilityPanel extends JFrame implements Observer {

    private TextArea messages;
    private JLabel statusLabel;
    private JButton dashboardButton;
    private JButton eXideButton;

    public UtilityPanel(final Launcher launcher, boolean hideOnStart) {
        this.setAlwaysOnTop(true);

        BufferedImage image = null;
        try {
            image = ImageIO.read(getClass().getResource("icon32.png"));
        } catch (IOException e) {
        }
        this.setIconImage(image);

        if (!launcher.isSystemTraySupported())
            setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        getContentPane().setLayout(new BoxLayout(getContentPane(), BoxLayout.PAGE_AXIS));

        setBackground(new Color(255, 255, 255, 255));

        JToolBar toolbar = new JToolBar();
        toolbar.setOpaque(false);
        toolbar.setBorderPainted(false);
        //toolbar.setBackground(new Color(255, 255, 255, 255));

        JButton button;

        if (Desktop.isDesktopSupported()) {
            final Desktop desktop = Desktop.getDesktop();

            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                dashboardButton = createButton(toolbar, "dashboard.png", "Dashboard");
                dashboardButton.setEnabled(false);
                dashboardButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        launcher.dashboard(desktop);
                    }
                });
                toolbar.add(dashboardButton);

                eXideButton = createButton(toolbar, "exide.png", "eXide");
                eXideButton.setEnabled(false);
                eXideButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        launcher.eXide(desktop);
                    }
                });
                toolbar.add(eXideButton);
            }
        }

        button = createButton(toolbar, "browsing.png", "Java Client");
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                launcher.client();
            }
        });
        toolbar.add(button);

        button = createButton(toolbar, "shutdown.png", "Shut Down");
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                launcher.shutdown();
            }
        });
        toolbar.add(button);

        getContentPane().add(toolbar);

        JPanel msgPanel = new JPanel();
        msgPanel.setLayout(new BorderLayout());

        statusLabel = new JLabel("", SwingConstants.CENTER);
        statusLabel.setPreferredSize(new Dimension(300, 20));
        statusLabel.setMinimumSize(new Dimension(200, 20));
        if (!launcher.isSystemTraySupported())
            statusLabel.setText("System tray icon not supported.");

        msgPanel.add(statusLabel, BorderLayout.NORTH);

        JCheckBox showMessages = new JCheckBox("Show console messages");
        showMessages.setHorizontalAlignment(SwingConstants.LEFT);
        showMessages.setOpaque(false);
        showMessages.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent itemEvent) {
                boolean showMessages = itemEvent.getStateChange() == ItemEvent.SELECTED;
                if (showMessages) {
                    messages.setVisible(true);
                } else
                    messages.setVisible(false);
                UtilityPanel.this.pack();
            }
        });
        msgPanel.add(showMessages, BorderLayout.CENTER);

        messages = new TextArea();
        messages.setBackground(new Color(20,20, 20, 255));
        messages.setPreferredSize(new Dimension(300, 200));
        messages.setForeground(new Color(255, 255, 255));
        msgPanel.add(messages, BorderLayout.SOUTH);
        messages.setVisible(false);

        getContentPane().add(msgPanel);

        setMinimumSize(new Dimension(350, 80));
        pack();

        Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
        this.setLocation(d.width - this.getWidth() - 40, 60);

        launcher.addObserver(this);

        if (!hideOnStart)
            setVisible(true);
    }

    private JButton createButton(JToolBar toolbar, String image, String title) {
        URL imageURL = UtilityPanel.class.getResource(image);
        ImageIcon icon = new ImageIcon(imageURL, title);
        JButton button = new JButton(title, icon);

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

    protected void setStatus(final String message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                statusLabel.setText(message);
            }
        });
    }

    @Override
    public void update(Observable observable, final Object o) {
        if (o instanceof ExistRepository.Notification) {
            ExistRepository.Notification notification = (ExistRepository.Notification) o;
            if (notification.getPackageURI().equals(Launcher.PACKAGE_DASHBOARD)) {
                dashboardButton.setEnabled(notification.getAction() == ExistRepository.Action.INSTALL);
            } else if (notification.getPackageURI().equals(Launcher.PACKAGE_EXIDE)) {
                eXideButton.setEnabled(notification.getAction() == ExistRepository.Action.INSTALL);
            }
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    messages.append(o.toString());
                }
            });
        }
    }
}