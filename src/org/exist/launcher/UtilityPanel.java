package org.exist.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.net.URL;
import java.util.Observable;
import java.util.Observer;

public class UtilityPanel extends JFrame implements Observer {

    private TextArea messages;
    JLabel statusLabel;

    public UtilityPanel(final Launcher launcher) {

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
                button = createButton(toolbar, "dashboard.png", "Dashboard");
                button.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        launcher.dashboard(desktop);
                    }
                });
                toolbar.add(button);

                button = createButton(toolbar, "exide.png", "eXide");
                button.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        launcher.eXide(desktop);
                    }
                });
                toolbar.add(button);
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
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                messages.append(o.toString());
            }
        });
    }
}
