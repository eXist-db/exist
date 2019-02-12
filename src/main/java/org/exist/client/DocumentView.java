/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.client;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.border.BevelBorder;
import javax.xml.transform.OutputKeys;

import org.exist.security.Account;
import org.exist.storage.ElementIndex;
import org.exist.util.ProgressIndicator;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.util.URIUtils;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

class DocumentView extends JFrame {

    private static final long serialVersionUID = 1L;

    protected InteractiveClient client;
    private XmldbURI resourceName;
    protected Resource resource;
    protected Collection collection;
    protected boolean readOnly = false;
    protected RSyntaxTextArea text;
    protected RTextScrollPane textScrollPane;
    protected JButton saveButton;
    protected JButton saveAsButton;
    protected JTextField statusMessage;
    protected JTextField positionDisplay;
    protected JProgressBar progress;
    protected JPopupMenu popup;
    protected Properties properties;

    public DocumentView(InteractiveClient client, XmldbURI resourceName, Resource resource, Properties properties) throws XMLDBException {
        super(URIUtils.urlDecodeUtf8(resourceName.lastSegment()));
        this.resourceName = resourceName;
        this.resource = resource;
        this.client = client;
        this.setIconImage(InteractiveClient.getExistIcon(getClass()).getImage());
        this.collection = client.getCollection();
        this.properties = properties;
        getContentPane().setLayout(new BorderLayout());
        setupComponents();
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent ev) {
                close();
            }
        });
        pack();
    }

    public void viewDocument() {
        try {
            if ("XMLResource".equals(resource.getResourceType())) //$NON-NLS-1$
            {
                setText((String) resource.getContent());
            } else {
                setText(new String((byte[]) resource.getContent()));
            }

            // lock the resource for editing
            final UserManagementService service = (UserManagementService)
                    client.current.getService("UserManagementService", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
            final Account user = service.getAccount(properties.getProperty("user")); //$NON-NLS-1$
            final String lockOwner = service.hasUserLock(resource);
            if (lockOwner != null) {
                if (JOptionPane.showConfirmDialog(this,
                        Messages.getString("DocumentView.6") + lockOwner + //$NON-NLS-1$
                                Messages.getString("DocumentView.7"), //$NON-NLS-1$
                        Messages.getString("DocumentView.8"), //$NON-NLS-1$
                        JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                    dispose();
                    this.setCursor(Cursor.getDefaultCursor());
                    return;
                }
            }

            try {
                service.lockResource(resource, user);
            } catch (final XMLDBException ex) {
                System.out.println(ex.getMessage());
                JOptionPane.showMessageDialog(this,
                        Messages.getString("DocumentView.9")); //$NON-NLS-1$
                setReadOnly();
            }
            setVisible(true);
        } catch (final XMLDBException ex) {
            showErrorMessage(Messages.getString("DocumentView.10") + ex.getMessage(), ex); //$NON-NLS-1$
        }
    }


    private static void showErrorMessage(String message, Throwable t) {
        JScrollPane scroll = null;
        final JTextArea msgArea = new JTextArea(message);
        msgArea.setBorder(BorderFactory.createTitledBorder("Message:")); //$NON-NLS-1$
        msgArea.setEditable(false);
        msgArea.setBackground(null);
        if (t != null) {
            final StringWriter out = new StringWriter();
            final PrintWriter writer = new PrintWriter(out);
            t.printStackTrace(writer);
            final JTextArea stacktrace = new JTextArea(out.toString(), 20, 50);
            stacktrace.setBackground(null);
            stacktrace.setEditable(false);
            scroll = new JScrollPane(stacktrace);
            scroll.setPreferredSize(new Dimension(250, 300));
            scroll.setBorder(BorderFactory
                    .createTitledBorder("Exception Stacktrace:")); //$NON-NLS-1$
        }
        final JOptionPane optionPane = new JOptionPane();
        optionPane.setMessage(new Object[]{msgArea, scroll});
        optionPane.setMessageType(JOptionPane.ERROR_MESSAGE);
        final JDialog dialog = optionPane.createDialog(null, Messages.getString("DocumentView.13")); //$NON-NLS-1$
        dialog.setResizable(true);
        dialog.pack();
        dialog.setVisible(true);
        return;
    }

    public void setReadOnly() {
        text.setEditable(false);
        saveButton.setEnabled(false);
        readOnly = true;
    }

    private void close() {
        unlockView();
    }

    private void unlockView() {
        if (readOnly) {
            return;
        }
        try {
            final UserManagementService service = (UserManagementService) collection
                    .getService("UserManagementService", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
            service.unlockResource(resource);
        } catch (final XMLDBException e) {
            e.printStackTrace();
        }
    }

    private void setupComponents() throws XMLDBException {

        /* start of menubar */
        final JMenuBar menubar = new JMenuBar();

        final JMenu fileMenu = new JMenu(Messages.getString("DocumentView.16")); //$NON-NLS-1$
        fileMenu.setMnemonic(KeyEvent.VK_F);
        menubar.add(fileMenu);

        JMenuItem item;
        // Save to database
        item = new JMenuItem(Messages.getString("DocumentView.17"), KeyEvent.VK_S); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        item.addActionListener(e -> {
            System.out.println("SAVE");
            save();
        });
        fileMenu.add(item);
        /*
        // Refresh
        item = new JMenuItem("Refresh", KeyEvent.VK_R);
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R,
        		Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    refresh() ;
                } catch (XMLDBException u) {
                    u.printStackTrace();
                }
            }
        });
        fileMenu.add(item);
        */

        setJMenuBar(menubar);
        /* end of menubar */
        
        /* The icon toolbar */

        final JToolBar toolbar = new JToolBar();

        //Save button
        URL url = getClass().getResource("icons/Save24.gif"); //$NON-NLS-1$
        saveButton = new JButton(new ImageIcon(url));
        saveButton
                .setToolTipText(Messages.getString("DocumentView.20")); //$NON-NLS-1$
        saveButton.addActionListener(e -> save());
        toolbar.add(saveButton);

        //Save As button
        url = getClass().getResource("icons/SaveAs24.gif"); //$NON-NLS-1$
        saveAsButton = new JButton(new ImageIcon(url));
        saveAsButton
                .setToolTipText(Messages.getString("DocumentView.22")); //$NON-NLS-1$
        saveAsButton.addActionListener(e -> saveAs());
        toolbar.add(saveAsButton);

        //Export button
        url = getClass().getResource("icons/Export24.gif"); //$NON-NLS-1$
        JButton button = new JButton(new ImageIcon(url));
        button.setToolTipText(Messages.getString("DocumentView.24")); //$NON-NLS-1$
        button.addActionListener(e -> {
            try {
                export();
            } catch (final XMLDBException u) {
                u.printStackTrace();
            }
        });
        toolbar.add(button);

        toolbar.addSeparator();

        //Copy button
        url = getClass().getResource("icons/Copy24.gif"); //$NON-NLS-1$
        button = new JButton(new ImageIcon(url));
        button.setToolTipText(Messages.getString("DocumentView.26")); //$NON-NLS-1$
        button.addActionListener(e -> text.copy());
        toolbar.add(button);

        //Cut button
        url = getClass().getResource("icons/Cut24.gif"); //$NON-NLS-1$
        button = new JButton(new ImageIcon(url));
        button.setToolTipText(Messages.getString("DocumentView.28")); //$NON-NLS-1$
        button.addActionListener(e -> text.cut());
        toolbar.add(button);

        //Paste button
        url = getClass().getResource("icons/Paste24.gif"); //$NON-NLS-1$
        button = new JButton(new ImageIcon(url));
        button.setToolTipText(Messages.getString("DocumentView.30")); //$NON-NLS-1$
        button.addActionListener(e -> text.paste());
        toolbar.add(button);

        toolbar.addSeparator();

        //Refresh button
        url = getClass().getResource("icons/Refresh24.gif"); //$NON-NLS-1$
        button = new JButton(new ImageIcon(url));
        button.setToolTipText(Messages.getString("DocumentView.32")); //$NON-NLS-1$
        button.addActionListener(e -> {
            try {
                refresh();
            } catch (final XMLDBException u) {
                u.printStackTrace();
            }
        });
        toolbar.add(button);

        getContentPane().add(toolbar, BorderLayout.NORTH);
        text = new RSyntaxTextArea(14, 80);
        text.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_XML);
        text.setCodeFoldingEnabled(true);
        textScrollPane = new RTextScrollPane(text);

        getContentPane().add(textScrollPane, BorderLayout.CENTER);
        final Box statusbar = Box.createHorizontalBox();
        statusbar.setBorder(BorderFactory
                .createBevelBorder(BevelBorder.LOWERED));
        statusMessage = new JTextField(20);
        statusMessage.setEditable(false);
        statusMessage.setFocusable(false);
        statusMessage.setText(Messages.getString("DocumentView.34") + URIUtils.urlDecodeUtf8(resource.getId()) + Messages.getString("DocumentView.35")); //$NON-NLS-1$ //$NON-NLS-2$
        statusbar.add(statusMessage);
        progress = new JProgressBar();
        progress.setPreferredSize(new Dimension(200, 30));
        progress.setVisible(false);
        statusbar.add(progress);
        positionDisplay = new JTextField(5);
        positionDisplay.setEditable(false);
        positionDisplay.setFocusable(true);
        statusbar.add(positionDisplay);
        text.addCaretListener(e -> {
            final RSyntaxTextArea txt = (RSyntaxTextArea) e.getSource();
            positionDisplay.setText("Line: " + (txt.getCaretLineNumber() + 1) + " Column:" + (txt.getCaretOffsetFromLineStart() + 1));
        });
        getContentPane().add(statusbar, BorderLayout.SOUTH);
    }

    private void save() {
        final Runnable saveTask = () -> {
            try {
                statusMessage.setText(Messages.getString("DocumentView.36") + URIUtils.urlDecodeUtf8(resource.getId())); //$NON-NLS-1$
                if (collection instanceof Observable) {
                    ((Observable) collection)
                            .addObserver(new ProgressObserver());
                }
                progress.setIndeterminate(true);
                progress.setVisible(true);
                resource.setContent(text.getText());
                collection.storeResource(resource);
                if (collection instanceof Observable) {
                    ((Observable) collection).deleteObservers();
                }
            } catch (final XMLDBException e) {
                ClientFrame.showErrorMessage(Messages.getString("DocumentView.37") //$NON-NLS-1$
                        + e.getMessage(), e);
            } finally {
                progress.setVisible(false);
            }
        };
        client.newClientThread("save", saveTask).start();
    }

    private void saveAs() {
        final Runnable saveAsTask = () -> {

            //Get the name to save the resource as
            final String nameres = JOptionPane.showInputDialog(null, Messages.getString("DocumentView.38")); //$NON-NLS-1$
            if (nameres != null) {
                try {
                    //Change status message and display a progress dialog
                    statusMessage.setText(Messages.getString("DocumentView.39") + nameres); //$NON-NLS-1$
                    if (collection instanceof Observable) {
                        ((Observable) collection).addObserver(new ProgressObserver());
                    }
                    progress.setIndeterminate(true);
                    progress.setVisible(true);

                    //Create a new resource as named, set the content, store the resource
                    XMLResource result = null;
                    result = (XMLResource) collection.createResource(URIUtils.encodeXmldbUriFor(nameres).toString(), XMLResource.RESOURCE_TYPE);
                    result.setContent(text.getText());
                    collection.storeResource(result);
                    client.reloadCollection();    //reload the client collection
                    if (collection instanceof Observable) {
                        ((Observable) collection).deleteObservers();
                    }
                } catch (final XMLDBException e) {
                    ClientFrame.showErrorMessage(Messages.getString("DocumentView.40") + e.getMessage(), e); //$NON-NLS-1$
                } catch (final URISyntaxException e) {
                    ClientFrame.showErrorMessage(Messages.getString("DocumentView.41") + e.getMessage(), e); //$NON-NLS-1$
                } finally {
                    //hide the progress dialog
                    progress.setVisible(false);
                }
            }
        };
        client.newClientThread("save-as", saveAsTask).start();
    }

    private void export() throws XMLDBException {
        final String workDir = properties.getProperty("working-dir", System //$NON-NLS-1$
                .getProperty("user.dir")); //$NON-NLS-1$
        final JFileChooser chooser = new JFileChooser(workDir);
        chooser.setMultiSelectionEnabled(false);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setSelectedFile(Paths.get(resource.getId()).toFile());
        if (chooser.showDialog(this, Messages.getString("DocumentView.44")) == JFileChooser.APPROVE_OPTION) { //$NON-NLS-1$
            final File file = chooser.getSelectedFile();
            if (file.exists()
                    && JOptionPane.showConfirmDialog(this,
                    Messages.getString("DocumentView.45"), Messages.getString("DocumentView.46"), //$NON-NLS-1$ //$NON-NLS-2$
                    JOptionPane.YES_NO_OPTION) == JOptionPane.NO_OPTION) {
                return;
            }
            try {
                final OutputStreamWriter writer = new OutputStreamWriter(
                        new FileOutputStream(file), Charset.forName(properties
                        .getProperty("encoding"))); //$NON-NLS-1$
                writer.write(text.getText());
                writer.close();
            } catch (final IOException e) {
                ClientFrame.showErrorMessage(Messages.getString("DocumentView.48") //$NON-NLS-1$
                        + e.getMessage(), e);
            }
            final File selectedDir = chooser.getCurrentDirectory();
            properties
                    .setProperty("working-dir", selectedDir.getAbsolutePath()); //$NON-NLS-1$
        }
    }

    private void refresh() throws XMLDBException {
        //First unlock the resource
        unlockView();

        //Reload the resource
        this.resource = client.retrieve(resourceName, properties.getProperty(OutputKeys.INDENT, "yes")); //$NON-NLS-1$

        //View and lock the resource
        viewDocument();
    }

    public void setText(String content) throws XMLDBException {
        text.setText(content);
        text.setCaretPosition(0);
        statusMessage.setText(Messages.getString("DocumentView.52") + XmldbURI.create(client.getCollection().getName()).append(resourceName) + Messages.getString("DocumentView.53") + properties.getProperty("uri")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    class ProgressObserver implements Observer {
        public void update(Observable o, Object arg) {
            progress.setIndeterminate(false);
            final ProgressIndicator ind = (ProgressIndicator) arg;
            progress.setValue(ind.getPercentage());

            if (o instanceof ElementIndex) {
                progress.setString("Storing elements"); //$NON-NLS-1$
            } else {
                progress.setString("Storing nodes"); //$NON-NLS-1$
            }
        }
    }
}
