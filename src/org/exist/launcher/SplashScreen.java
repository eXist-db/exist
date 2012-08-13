package org.exist.launcher;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

/**
 * Display a splash screen showing the eXist-db logo and a status line.
 *
 * @author Wolfgang Meier
 */
public class SplashScreen extends JFrame {

    private JLabel statusLabel;

    public SplashScreen() {
        setUndecorated(true);
        setBackground(new Color(255, 255, 255, 125));
        setAlwaysOnTop(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        URL imageURL = SplashScreen.class.getResource("logo.jpg");
        ImageIcon icon = new ImageIcon(imageURL, "eXist-db Logo");
        getContentPane().setLayout(new BorderLayout());

        // add the image label
        JLabel imageLabel = new JLabel();
        imageLabel.setIcon(icon);
        getContentPane().add(imageLabel, BorderLayout.CENTER);

        // message label
        statusLabel = new JLabel("Launching eXist-db ...", SwingConstants.CENTER);
        statusLabel.setFont(new Font(statusLabel.getFont().getName(), Font.BOLD, 14));
        statusLabel.setForeground(Color.black);

        getContentPane().add(statusLabel, BorderLayout.SOUTH);
        // show it
        setSize(new Dimension(icon.getIconWidth(), icon.getIconHeight() + 20));
        //pack();
        this.setLocationRelativeTo(null);
        setVisible(true);
    }

    public void setStatus(final String status) {
        statusLabel.setText(status);
    }
}