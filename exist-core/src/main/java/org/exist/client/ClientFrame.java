/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.client;

import org.exist.ExistSystemProperties;
import org.exist.backup.Backup;
import org.exist.backup.CreateBackupDialog;
import org.exist.backup.GuiRestoreServiceTaskListener;
import org.exist.client.security.EditPropertiesDialog;
import org.exist.client.security.ModeDisplay;
import org.exist.client.security.UserManagerDialog;
import org.exist.security.*;
import org.exist.security.SecurityManager;
import org.exist.security.internal.aider.SimpleACLPermissionAider;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.FileUtils;
import org.exist.util.MimeTable;
import org.exist.util.SystemExitCodes;
import org.exist.util.crypto.digest.DigestType;
import org.exist.util.crypto.digest.MessageDigest;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.*;
import org.exist.xquery.Constants;
import org.exist.xquery.util.URIUtils;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.xml.transform.OutputKeys;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.util.FileUtils.humanSize;

/**
 * Main frame of the eXist GUI
 */
public class ClientFrame extends JFrame implements WindowFocusListener, KeyListener, ActionListener, MouseListener {

    private static final long serialVersionUID = 1L;

    public final static String CUT = Messages.getString("ClientFrame.0"); //$NON-NLS-1$
    public final static String COPY = Messages.getString("ClientFrame.1"); //$NON-NLS-1$
    public final static String PASTE = Messages.getString("ClientFrame.2"); //$NON-NLS-1$

    public final static int MAX_DISPLAY_LENGTH = 512000;
    public final static int MAX_HISTORY = 50;

    private final static SimpleAttributeSet promptAttrs = new SimpleAttributeSet();
    private final static SimpleAttributeSet defaultAttrs = new SimpleAttributeSet();
    static {
        StyleConstants.setForeground(promptAttrs, Color.blue);
        StyleConstants.setBold(promptAttrs, true);
        StyleConstants.setForeground(defaultAttrs, Color.black);
    }

    public static final String MULTIPLE_INDICATOR = "[...]";
    private static final String NON_APPLICABLE = "N/A";
    private static final String COLLECTION_MIME_TYPE = "exist/collection";

    private int commandStart = 0;

    private boolean gotUp = false;
    private DefaultStyledDocument doc;
    private JLabel statusbar;
    private JTable fileman;
    private final ResourceTableModel resources = new ResourceTableModel();
    private JTextPane shell;
    private JPopupMenu shellPopup;
    private final ProcessRunnable processRunnable;
    private final Thread processThread;
    private Preferences preferences;

    private XmldbURI path = null;
    private Properties properties;
    private final InteractiveClient client;

    /**
     * Constructor.
     *
     * @param client Existdb client
     * @param path Database connection URL.
     * @param properties Configuration items.
     * @throws java.awt.HeadlessException Environment  does not support a keyboard, display, or mouse.
     */
    public ClientFrame(final InteractiveClient client, final XmldbURI path, final Properties properties) throws HeadlessException {
        super(Messages.getString("ClientFrame.3")); //$NON-NLS-1$
        this.path = path;
        this.properties = properties;
        this.client = client;
        this.processRunnable = new ProcessRunnable();
        this.processThread = client.newClientThread("process", processRunnable);

        this.setIconImage(InteractiveClient.getExistIcon(getClass()).getImage());

        setupComponents();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent ev) {
                close();
            }
        });
        pack();

        processThread.start();

        shell.requestFocus();

        preferences = Preferences.userNodeForPackage(ClientFrame.class);
    }

    private void setupComponents() {
        setJMenuBar(createMenuBar());

        // create the toolbar
        final JToolBar toolbar = new JToolBar();
        URL url = getClass().getResource("icons/Up24.gif"); //$NON-NLS-1$
        JButton button = new JButton(new ImageIcon(url));
        button.setToolTipText(Messages.getString("ClientFrame.5")); //$NON-NLS-1$
        button.addActionListener(this::goUpAction);
        toolbar.add(button);

        url = getClass().getResource("icons/Refresh24.gif"); //$NON-NLS-1$
        button = new JButton(new ImageIcon(url));
        button.setToolTipText(Messages.getString("ClientFrame.7")); //$NON-NLS-1$
        button.addActionListener(e -> {
            try {
                client.reloadCollection();
            } catch (final XMLDBException e1) {
                //TODO report message
            }
        });
        toolbar.add(button);
        toolbar.addSeparator();

        url = getClass().getResource("icons/New24.gif"); //$NON-NLS-1$
        button = new JButton(new ImageIcon(url));
        button.setToolTipText(Messages.getString("ClientFrame.9")); //$NON-NLS-1$
        button.addActionListener(this::newCollectionAction);
        toolbar.add(button);

        url = getClass().getResource("icons/Add24.gif"); //$NON-NLS-1$
        button = new JButton(new ImageIcon(url));
        button.setToolTipText(Messages.getString("ClientFrame.11")); //$NON-NLS-1$
        button.addActionListener(this::uploadAction);
        toolbar.add(button);

        url = getClass().getResource("icons/Delete24.gif"); //$NON-NLS-1$
        button = new JButton(new ImageIcon(url));
        button.setToolTipText(Messages.getString("ClientFrame.13")); //$NON-NLS-1$
        button.addActionListener(this::removeAction);
        toolbar.add(button);

        url = getClass().getResource(Messages.getString("ClientFrame.14")); //$NON-NLS-1$
        button = new JButton(new ImageIcon(url));
        button.setToolTipText(Messages.getString("ClientFrame.15")); //$NON-NLS-1$
        button.addActionListener(e -> {
            try {
                setPermAction(e);
            } catch (final PermissionDeniedException pde) {
                showErrorMessage(pde.getMessage(), pde);
            }
        });
        toolbar.add(button);

        toolbar.addSeparator();
        url = getClass().getResource("icons/Export24.gif"); //$NON-NLS-1$
        button = new JButton(new ImageIcon(url));
        button.setToolTipText(Messages.getString("ClientFrame.17")); //$NON-NLS-1$
        button.addActionListener(this::backupAction);
        toolbar.add(button);

        url = getClass().getResource("icons/Import24.gif"); //$NON-NLS-1$
        button = new JButton(new ImageIcon(url));
        button.setToolTipText(Messages.getString("ClientFrame.19")); //$NON-NLS-1$
        button.addActionListener(this::restoreAction);
        toolbar.add(button);

        toolbar.addSeparator();
        url = getClass().getResource(Messages.getString("ClientFrame.20")); //$NON-NLS-1$
        button = new JButton(new ImageIcon(url));
        button.setToolTipText(Messages.getString("ClientFrame.21")); //$NON-NLS-1$
        button.addActionListener(this::editUsersAction);
        toolbar.add(button);

        url = getClass().getResource("icons/Find24.gif"); //$NON-NLS-1$
        button = new JButton(new ImageIcon(url));
        button.setToolTipText(Messages.getString("ClientFrame.23")); //$NON-NLS-1$
        button.addActionListener(this::findAction);
        toolbar.add(button);

        // the split pane separates the resource view table from the shell
        final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        split.setResizeWeight(0.5);

        // create table for resources and collections
        fileman = new JTable();
        fileman.setModel(resources);
        fileman.setRowSorter(new TableRowSorter(resources));
        fileman.addMouseListener(new TableMouseListener());
        //fileman.setTransferHandler(new TransferHandler(){  
        //});

        fileman.setDropMode(DropMode.ON);
        final DropTarget filemanDropTarget = new DropTarget(fileman, DnDConstants.ACTION_COPY, new FileListDropTargetListener());
        fileman.setDropTarget(filemanDropTarget);

        fileman.setDefaultRenderer(Object.class, new HighlightedTableCellRenderer<ResourceTableModel>());
        JScrollPane scroll = new JScrollPane(fileman);
        scroll.setMinimumSize(new Dimension(300, 150));
        split.setLeftComponent(scroll);

        shellPopup = new JPopupMenu(Messages.getString("ClientFrame.24")); //$NON-NLS-1$
        shellPopup.add(new JMenuItem(CUT)).addActionListener(this);
        shellPopup.add(new JMenuItem(COPY)).addActionListener(this);
        shellPopup.add(new JMenuItem(PASTE)).addActionListener(this);

        // shell window
        doc = new DefaultStyledDocument();
        shell = new JTextPane(doc);
        shell.setContentType("text/plain; charset=UTF-8"); //$NON-NLS-1$
        shell.setFont(new Font("Monospaced", Font.PLAIN, 12)); //$NON-NLS-1$
        shell.setMargin(new Insets(7, 5, 7, 5));
        shell.addKeyListener(this);
        shell.addMouseListener(this);

        scroll = new JScrollPane(shell);

        split.setRightComponent(scroll);

        statusbar = new JLabel(Messages.getString("ClientFrame.27") + properties.getProperty(InteractiveClient.USER) + "@" + properties.getProperty(InteractiveClient.URI)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        statusbar.setMinimumSize(new Dimension(400, 15));
        statusbar.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));

        getContentPane().add(split, BorderLayout.CENTER);
        getContentPane().add(toolbar, BorderLayout.NORTH);
        getContentPane().add(statusbar, BorderLayout.SOUTH);
    }

    private JMenuBar createMenuBar() {
        final JMenuBar menubar = new JMenuBar();

        final JMenu fileMenu = new JMenu(Messages.getString("ClientFrame.31")); //$NON-NLS-1$
        fileMenu.setMnemonic(KeyEvent.VK_F);
        menubar.add(fileMenu);

        JMenuItem item = new JMenuItem(Messages.getString("ClientFrame.32"), KeyEvent.VK_S); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        item.addActionListener(this::uploadAction);
        fileMenu.add(item);

        item = new JMenuItem(Messages.getString("ClientFrame.34"), KeyEvent.VK_N); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        item.addActionListener(this::newCollectionAction);
        fileMenu.add(item);

        item = new JMenuItem(Messages.getString("ClientFrame.36"), KeyEvent.VK_B); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        item.addActionListener(this::newBlankDocument);
        fileMenu.add(item);
        fileMenu.addSeparator();

        item = new JMenuItem(Messages.getString("ClientFrame.40")); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        item.addActionListener(this::removeAction);
        fileMenu.add(item);

        item = new JMenuItem(Messages.getString("ClientFrame.42"), KeyEvent.VK_C); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        item.addActionListener(this::copyAction);
        fileMenu.add(item);

        item = new JMenuItem(Messages.getString("ClientFrame.44"), KeyEvent.VK_M); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        item.addActionListener(this::moveAction);
        fileMenu.add(item);

        item = new JMenuItem(Messages.getString("ClientFrame.46"), KeyEvent.VK_R); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        item.addActionListener(this::renameAction);
        fileMenu.add(item);

        item = new JMenuItem(Messages.getString("ClientFrame.47"), KeyEvent.VK_E);
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        item.addActionListener(this::exportAction);
        fileMenu.add(item);

        fileMenu.addSeparator();

        item = new JMenuItem(Messages.getString("ClientFrame.48"), KeyEvent.VK_I); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        item.addActionListener(this::reindexAction);
        fileMenu.add(item);

        item = new JMenuItem(Messages.getString("ClientFrame.50")); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        item.addActionListener(e -> {
            try {
                setPermAction(e);
            } catch (final PermissionDeniedException pde) {
                showErrorMessage(pde.getMessage(), pde);
            }
        });
        fileMenu.add(item);

        fileMenu.addSeparator();
        item = new JMenuItem(Messages.getString("ClientFrame.52"), KeyEvent.VK_Q); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        item.addActionListener(e -> close());
        fileMenu.add(item);

        final JMenu toolsMenu = new JMenu(Messages.getString("ClientFrame.54")); //$NON-NLS-1$
        toolsMenu.setMnemonic(KeyEvent.VK_T);
        menubar.add(toolsMenu);

        item = new JMenuItem(Messages.getString("ClientFrame.55"), KeyEvent.VK_F); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        item.addActionListener(this::findAction);
        toolsMenu.add(item);

        toolsMenu.addSeparator();

        item = new JMenuItem(Messages.getString("ClientFrame.57"), KeyEvent.VK_U); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        item.addActionListener(this::editUsersAction);
        toolsMenu.add(item);

        // Disable "Edit Indexes" menu item.
//        item = new JMenuItem(Messages.getString("ClientFrame.59"), KeyEvent.VK_I); //$NON-NLS-1$
//        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I,
//        		Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
//        item.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                editIndexesAction(e);
//            }
//        });
//        toolsMenu.add(item);

        item = new JMenuItem(Messages.getString("ClientFrame.60"), KeyEvent.VK_T);
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        item.addActionListener(this::editTriggersAction);
        toolsMenu.add(item);

        toolsMenu.addSeparator();

        item = new JMenuItem(Messages.getString("ClientFrame.62a"));
        item.addActionListener(e -> {
            try {
                final DatabaseInstanceManager service = (DatabaseInstanceManager) client.current.getService("DatabaseInstanceManager", "1.0");
                service.enterServiceMode();
            } catch (final XMLDBException ex) {
                showErrorMessage(ex.getMessage(), ex);
            }
        });
        toolsMenu.add(item);

        item = new JMenuItem(Messages.getString("ClientFrame.62b"));
        item.addActionListener(e -> {
            try {
                final DatabaseInstanceManager service = (DatabaseInstanceManager) client.current.getService("DatabaseInstanceManager", "1.0");
                service.exitServiceMode();
            } catch (final XMLDBException ex) {
                showErrorMessage(ex.getMessage(), ex);
            }
        });
        toolsMenu.add(item);

        toolsMenu.addSeparator();

        item = new JMenuItem(Messages.getString("ClientFrame.63"), KeyEvent.VK_B); //$NON-NLS-1$
        item.addActionListener(this::backupAction);
        toolsMenu.add(item);

        item = new JMenuItem(Messages.getString("ClientFrame.64"), KeyEvent.VK_R); //$NON-NLS-1$
        item.addActionListener(this::restoreAction);
        toolsMenu.add(item);

        final JMenu connectMenu = new JMenu(Messages.getString("ClientFrame.65")); //$NON-NLS-1$
        connectMenu.setMnemonic(KeyEvent.VK_D);
        menubar.add(connectMenu);

        item = new JMenuItem(Messages.getString("ClientFrame.66"), KeyEvent.VK_S); //$NON-NLS-1$
        item.addActionListener(e -> {
            display(Messages.getString("ClientFrame.67")); //$NON-NLS-1$
            processRunnable.setAction("shutdown"); //$NON-NLS-1$
        });
        connectMenu.add(item);

        // Show LoginPanel to Reconnect
        item = new JMenuItem(Messages.getString("ClientFrame.69"), KeyEvent.VK_U); //$NON-NLS-1$
        item.setToolTipText(Messages.getString("ClientFrame.70")); //$NON-NLS-1$
        item.addActionListener(e -> {
            // load properties modified by the login panel
            final Properties loginData = getLoginData(properties);
            reconnectClient(loginData);
        });
        connectMenu.add(item);


        final JMenu optionsMenu = new JMenu(Messages.getString("ClientFrame.80")); //$NON-NLS-1$
        optionsMenu.setMnemonic(KeyEvent.VK_O);
        menubar.add(optionsMenu);

        JCheckBoxMenuItem check = new JCheckBoxMenuItem(Messages.getString("ClientFrame.81"), "yes".equals(properties.getProperty(OutputKeys.INDENT))); //$NON-NLS-1$
        check.addActionListener(e -> {
            properties.setProperty(OutputKeys.INDENT,
                    ((JCheckBoxMenuItem) e.getSource()).isSelected()
                            ? "yes" //$NON-NLS-1$
                            : "no"); //$NON-NLS-1$
            try {
                client.getResources();
            } catch (final XMLDBException e1) {
                //TODO report error
            }
        });
        optionsMenu.add(check);

        check = new JCheckBoxMenuItem(Messages.getString("ClientFrame.85"), "yes".equals(properties.getProperty(EXistOutputKeys.EXPAND_XINCLUDES))); //$NON-NLS-1$
        check.addActionListener(e -> {
            properties.setProperty(EXistOutputKeys.EXPAND_XINCLUDES,
                    ((JCheckBoxMenuItem) e.getSource()).isSelected()
                            ? "yes" //$NON-NLS-1$
                            : "no"); //$NON-NLS-1$
            try {
                client.getResources();
            } catch (final XMLDBException e1) {
                //TODO report error
            }
        });
        optionsMenu.add(check);


        final JMenu HelpMenu = new JMenu(Messages.getString("ClientFrame.89")); //$NON-NLS-1$
        HelpMenu.setMnemonic(KeyEvent.VK_H);
        menubar.add(HelpMenu);

        item = new JMenuItem(Messages.getString("ClientFrame.90"), KeyEvent.VK_A); //$NON-NLS-1$
        item.addActionListener(e -> AboutAction());
        HelpMenu.add(item);

        return menubar;
    }

    public void reconnectClient(final Properties loginData) {
        if (loginData == null || loginData.isEmpty()) {
            return;
        }

        // make a backup of the current properties
        final Properties oldProps = new Properties();
        oldProps.putAll(properties);

        // update the properties with the new login data
        properties.putAll(loginData);
        statusbar.setText(Messages.getString("ClientFrame.71") + properties.getProperty(InteractiveClient.USER) + "@" + properties.getProperty(InteractiveClient.URI)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

        try {
            client.shutdown(false);
            client.connect();
            client.reloadCollection();
        } catch (final Exception u) {
            showErrorMessage(Messages.getString("ClientFrame.75") + properties.getProperty(InteractiveClient.URI) + Messages.getString("ClientFrame.77"), u); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

            // restore the previous properties
            properties = oldProps;
            try {
                // attempt to re-connect with previous login data
                client.connect();
            } catch (final Exception uu) {
                showErrorMessage(Messages.getString("ClientFrame.78") + properties.getProperty(InteractiveClient.URI), uu); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
    }

    public void setPath(final XmldbURI currentPath) {
        path = currentPath;
    }

    protected void displayPrompt() {
        final String pathString = path.getCollectionPath();
        try {
            commandStart = doc.getLength();
            doc.insertString(commandStart, Messages.getString("ClientFrame.91"), promptAttrs); //$NON-NLS-1$
            commandStart += 6;
            doc.insertString(commandStart, pathString + '>', promptAttrs);
            commandStart += pathString.length() + 1;
            doc.insertString(commandStart++, Messages.getString("ClientFrame.92"), defaultAttrs); //$NON-NLS-1$
            shell.setCaretPosition(commandStart);
        } catch (final BadLocationException e) {
            //TODO show error
        }
    }

    protected void display(final String message) {
        try {
            commandStart = doc.getLength();
            if (commandStart > MAX_DISPLAY_LENGTH) {
                doc.remove(0, MAX_DISPLAY_LENGTH);
                commandStart = doc.getLength();
            }
            doc.insertString(commandStart, message, defaultAttrs);
            commandStart = doc.getLength();
            shell.setCaretPosition(commandStart);

        } catch (final BadLocationException e) {
            //TODO show error
        }
    }

    protected void setResources(final List<ResourceDescriptor> rows) {
        resources.setData(rows);
    }

    protected void setStatus(final String message) {
        statusbar.setText(message);
    }

    protected void setEditable(final boolean enabled) {
        shell.setEditable(enabled);
        shell.setVisible(enabled);
    }

    /**
     * @see java.awt.event.KeyListener#keyPressed(java.awt.event.KeyEvent)
     */
    @Override
    public void keyPressed(final KeyEvent e) {
        type(e);
        gotUp = false;
    }

    /**
     * @see java.awt.event.KeyListener#keyReleased(java.awt.event.KeyEvent)
     */
    @Override
    public void keyReleased(final KeyEvent e) {
        gotUp = true;
        type(e);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.awt.event.KeyListener#keyTyped(java.awt.event.KeyEvent)
     */
    @Override
    public void keyTyped(final KeyEvent e) {
        type(e);
    }

    private synchronized void type(final KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_ENTER:
                if (e.getID() == KeyEvent.KEY_PRESSED && gotUp) {
                    enter();
                }
                e.consume();
                break;
            case KeyEvent.VK_HOME:
                shell.setCaretPosition(commandStart);
                e.consume();
                break;
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_DELETE:
            case KeyEvent.VK_BACK_SPACE:
                if (shell.getCaretPosition() <= commandStart) {
                    e.consume();
                }
                break;
            case KeyEvent.VK_UP:
                if (e.getID() == KeyEvent.KEY_PRESSED) {
                    historyBack();
                }
                e.consume();
                break;
            case KeyEvent.VK_DOWN:
                if (e.getID() == KeyEvent.KEY_PRESSED) {
                    historyForward();
                }
                e.consume();
                break;
            default:
                if ((e.getModifiers() & (InputEvent.CTRL_MASK
                        | InputEvent.META_MASK | InputEvent.ALT_MASK)) == 0) {
                    if (shell.getCaretPosition() < commandStart) {
                        shell.setCaretPosition(doc.getLength());
                    }
                }
                if (e.paramString().contains(Messages.getString("ClientFrame.93"))) { //$NON-NLS-1$
                    if (shell.getCaretPosition() <= commandStart) {
                        e.consume();
                    }
                }
                break;
        }
    }

    /**
     * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
     */
    @Override
    public void actionPerformed(final ActionEvent e) {
        final String cmd = e.getActionCommand();
        if (cmd.equals(CUT)) {
            shell.cut();
        } else if (cmd.equals(COPY)) {
            shell.copy();
        } else if (cmd.equals(PASTE)) {
            shell.paste();
        }
    }

    private void newBlankDocument(final ActionEvent e) {
        final JFrame dialog = new NewResourceDialog(client);
        dialog.setVisible(true);
    }


    private void goUpAction(final ActionEvent ev) {
        display(Messages.getString("ClientFrame.94")); //$NON-NLS-1$
        processRunnable.setAction("cd .."); //$NON-NLS-1$
    }

    private void newCollectionAction(final ActionEvent ev) {
        final String newCol = JOptionPane.showInputDialog(this, Messages.getString("ClientFrame.96")); //$NON-NLS-1$
        if (newCol != null) {
            final String command = "mkcol \"" + newCol + '"'; //$NON-NLS-1$
            display(command + "\n"); //$NON-NLS-1$
            processRunnable.setAction(command);
        }
    }

    /**
     * Returns an array of user-selected resources.
     */
    private ResourceDescriptor[] getSelectedResources() {
        final int[] selectedRows = fileman.getSelectedRows();
        final ResourceDescriptor[] res = new ResourceDescriptor[selectedRows.length];

        for (int i = 0; i < selectedRows.length; i++) {
            res[i] = resources.getRow(fileman.convertRowIndexToModel(selectedRows[i]));
        }

        return res;
    }

    private void removeAction(final ActionEvent ev) {

        final ResourceDescriptor[] res = getSelectedResources();
        final Collection removeRootCollection = client.current;
        // String cmd;
        if (JOptionPane.showConfirmDialog(this,
                Messages.getString("ClientFrame.104") + Messages.getString("ClientFrame.105"), //$NON-NLS-1$ //$NON-NLS-2$
                Messages.getString("ClientFrame.106"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) { //$NON-NLS-1$
            final Runnable removeTask = () -> {
                final ProgressMonitor monitor = new ProgressMonitor(ClientFrame.this, Messages.getString("ClientFrame.107"), Messages.getString("ClientFrame.108"), 1, res.length); //$NON-NLS-1$ //$NON-NLS-2$
                monitor.setMillisToDecideToPopup(500);
                monitor.setMillisToPopup(500);
                for (int i = 0; i < res.length; i++) {
                    final ResourceDescriptor resource = res[i];
                    if (resource.isCollection()) {
                        try {
                            final EXistCollectionManagementService mgtService = (EXistCollectionManagementService) removeRootCollection
                                    .getService(
                                            "CollectionManagementService", //$NON-NLS-1$
                                            "1.0"); //$NON-NLS-1$
                            mgtService
                                    .removeCollection(resource.getName());
                        } catch (final XMLDBException e) {
                            showErrorMessage(e.getMessage(), e);
                        }
                    } else {
                        try {
                            final Resource res1 = removeRootCollection
                                    .getResource(resource.getName().toString());
                            removeRootCollection.removeResource(res1);
                        } catch (final XMLDBException e) {
                            showErrorMessage(e.getMessage(), e);
                        }
                    }
                    monitor.setProgress(i + 1);
                    if (monitor.isCanceled()) {
                        return;
                    }
                }

                try {
                    removeRootCollection.close();
                } catch (final XMLDBException e) {
                    showErrorMessage(e.getMessage(), e);
                }

                try {
                    client.getResources();
                } catch (final XMLDBException e) {
                    showErrorMessage(e.getMessage(), e);
                }
            };
            client.newClientThread("remove", removeTask).start();
        }
    }

    private void moveAction(final ActionEvent ev) {
        final ResourceDescriptor[] res = getSelectedResources();

        PrettyXmldbURI[] collections;

        //get an array of collection paths
        try {
            final Collection root = client.getCollection(XmldbURI.ROOT_COLLECTION);
            final List<PrettyXmldbURI> alCollections = getCollections(root, new ArrayList<>());
            collections = new PrettyXmldbURI[alCollections.size()];
            alCollections.toArray(collections);
        } catch (final XMLDBException e) {
            showErrorMessage(e.getMessage(), e);
            return;
        }

        //prompt the user for a destination collection from the list
        final Object val = JOptionPane.showInputDialog(this, Messages.getString("ClientFrame.111"), Messages.getString("ClientFrame.112"), JOptionPane.QUESTION_MESSAGE, null, collections, collections[0]); //$NON-NLS-1$ //$NON-NLS-2$
        if (val == null) {
            return;
        }

        final XmldbURI destinationPath = ((PrettyXmldbURI) val).getTargetURI();
        final Runnable moveTask = () -> {
            try {
                final EXistCollectionManagementService service = (EXistCollectionManagementService)
                        client.current.getService("CollectionManagementService", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
                for (ResourceDescriptor re : res) {
                    setStatus(Messages.getString("ClientFrame.115") + re.getName() + Messages.getString("ClientFrame.116") + destinationPath + Messages.getString("ClientFrame.117")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    if (re.isCollection()) {
                        service.move(re.getName(), destinationPath, null);
                    } else {
                        service.moveResource(re.getName(), destinationPath, null);
                    }
                }
                client.reloadCollection();
            } catch (final XMLDBException e) {
                showErrorMessage(e.getMessage(), e);
            }
            setStatus(Messages.getString("ClientFrame.118")); //$NON-NLS-1$
        };
        client.newClientThread("move", moveTask).start();
    }

    private void renameAction(final ActionEvent ev) {
        final ResourceDescriptor[] res = getSelectedResources();
        String inputValue = "";
        try {
            inputValue = res[0].getName().toString();
        } catch (Exception npe) {
        }
        final Object val = JOptionPane.showInputDialog(this, Messages.getString("ClientFrame.119"), Messages.getString("ClientFrame.120"), JOptionPane.QUESTION_MESSAGE, null, null, inputValue); //$NON-NLS-1$ //$NON-NLS-2$

        if (val == null) {
            return;
        }

        XmldbURI parseIt;
        try {
            parseIt = URIUtils.encodeXmldbUriFor((String) val);
        } catch (final URISyntaxException e) {
            showErrorMessage(Messages.getString("ClientFrame.121") + e.getMessage(), e); //$NON-NLS-1$
            return;
        }
        final XmldbURI destinationFilename = parseIt;
        final Runnable renameTask = () -> {
            try {
                final EXistCollectionManagementService service = (EXistCollectionManagementService)
                        client.current.getService("CollectionManagementService", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
                boolean changed = false;
                for (final ResourceDescriptor re : res) {
                    if (!re.getName().equals(destinationFilename)) {
                        setStatus(Messages.getString("ClientFrame.124") + re.getName() + Messages.getString("ClientFrame.125") + destinationFilename + Messages.getString("ClientFrame.126")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        if (re.isCollection()) {
                            service.move(re.getName(), null, destinationFilename);
                        } else {
                            service.moveResource(re.getName(), null, destinationFilename);
                        }
                        changed = true;
                    }
                }
                if (changed) {
                    client.reloadCollection();
                }
            } catch (final XMLDBException e) {
                showErrorMessage(e.getMessage(), e);
            }
            setStatus(Messages.getString("ClientFrame.127")); //$NON-NLS-1$
        };
        client.newClientThread("rename", renameTask).start();
    }

    private void copyAction(final ActionEvent ev) {

        final ResourceDescriptor[] res = getSelectedResources();
        PrettyXmldbURI[] collections;

        //get an array of collection paths
        try {
            final Collection root = client.getCollection(XmldbURI.ROOT_COLLECTION);
            final List<PrettyXmldbURI> alCollections = getCollections(root, new ArrayList<>());
            collections = new PrettyXmldbURI[alCollections.size()];
            alCollections.toArray(collections);
        } catch (final XMLDBException e) {
            showErrorMessage(e.getMessage(), e);
            return;
        }

        //prompt the user for a destination collection from the list
        final Object val = JOptionPane.showInputDialog(this, Messages.getString("ClientFrame.128"), Messages.getString("ClientFrame.129"), JOptionPane.QUESTION_MESSAGE, null, collections, collections[0]); //$NON-NLS-1$ //$NON-NLS-2$
        if (val == null) {
            return;
        }

        final XmldbURI destinationPath = ((PrettyXmldbURI) val).getTargetURI();

        final Runnable moveTask = () -> {
            try {
                final EXistCollectionManagementService service = (EXistCollectionManagementService)
                        client.current.getService("CollectionManagementService", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
                for (ResourceDescriptor re : res) {

                    //TODO
                    //what happens if the source and destination paths are the same?
                    //we need to check and prompt the user to either skip or choose a new name
                    //this function can copy multiple resources/collections selected by the user,
                    //so may need to prompt the user multiple times? is in this thread the correct
                    //place to do it? also need to do something similar for moveAction()
                    //
                    //Its too late and brain hurts - deliriumsky

                    setStatus(Messages.getString("ClientFrame.132") + re.getName() + Messages.getString("ClientFrame.133") + destinationPath + Messages.getString("ClientFrame.134")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    if (re.isCollection()) {
                        service.copy(re.getName(), destinationPath, null);
                    } else {
                        service.copyResource(re.getName(), destinationPath, null);
                    }
                }
                client.reloadCollection();
            } catch (final XMLDBException e) {
                showErrorMessage(e.getMessage(), e);
            }
            setStatus(Messages.getString("ClientFrame.135")); //$NON-NLS-1$
        };
        client.newClientThread("move", moveTask).start();
    }

    private ArrayList<PrettyXmldbURI> getCollections(final Collection root, final ArrayList<PrettyXmldbURI> collectionsList) throws XMLDBException {
        collectionsList.add(new PrettyXmldbURI(XmldbURI.create(root.getName())));
        final String[] childCollections = root.listChildCollections();
        Collection child = null;
        for (String childCollection : childCollections) {
            try {
                child = root.getChildCollection(childCollection);
            } catch (final XMLDBException xmldbe) {
                if (xmldbe.getCause() instanceof PermissionDeniedException) {
                    continue;
                } else {
                    throw xmldbe;
                }
            } catch (Exception npe) {
                System.out.println("Corrupted resource/collection skipped: " + child != null ? child.getName() != null ? child.getName() : "unknown" : "unknown");
                continue;
            }
            try {
                getCollections(child, collectionsList);
            } catch (Exception ee) {
                System.out.println("Corrupted resource/collection skipped: " + child != null ? child.getName() != null ? child.getName() : "unknown" : "unknown");
                continue;
            }
        }
        return collectionsList;
    }

    private void reindexAction(final ActionEvent ev) {
        final int[] selRows = fileman.getSelectedRows();
        final ResourceDescriptor[] res;
        if (selRows.length == 0) {
            res = new ResourceDescriptor[1];
            res[0] = new ResourceDescriptor.Collection(client.path);
        } else {
            res = new ResourceDescriptor[selRows.length];

            for (int i = 0; i < selRows.length; i++) {
                res[i] = resources.getRow(fileman.convertRowIndexToModel(selRows[i]));
                if (!(res[i].isCollection())) {
                    JOptionPane.showMessageDialog(this, Messages.getString("ClientFrame.136"), Messages.getString("ClientFrame.137"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$ //$NON-NLS-2$
                    return;
                }
            }
        }

        if (JOptionPane.showConfirmDialog(this,
                Messages.getString("ClientFrame.138"), //$NON-NLS-1$
                Messages.getString("ClientFrame.139"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) { //$NON-NLS-1$
            final ResourceDescriptor collections[] = res;
            final Runnable reindexThread = () -> {
                ClientFrame.this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                final IndexQueryService service;
                try {
                    service = (IndexQueryService)
                            client.current.getService("IndexQueryService", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
                    for (final ResourceDescriptor next : collections) {
                        setStatus(Messages.getString("ClientFrame.142") + next.getName() + Messages.getString("ClientFrame.143")); //$NON-NLS-1$ //$NON-NLS-2$
                        service.reindexCollection(next.getName());
                    }
                    setStatus(Messages.getString("ClientFrame.144")); //$NON-NLS-1$
                } catch (final XMLDBException e) {
                    showErrorMessage(e.getMessage(), e);
                }
                ClientFrame.this.setCursor(Cursor.getDefaultCursor());
            };
            client.newClientThread("reindex", reindexThread).start();
        }
    }

    private void uploadAction(final ActionEvent ev) {
        // TODO store last file choose in properties
        final JFileChooser chooser = new JFileChooser(preferences.get("directory.last", System.getProperty("user.dir")));
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.addChoosableFileFilter(new BinaryFileFilter());
        chooser.addChoosableFileFilter(new XMLFileFilter());
        if (chooser.showDialog(this, Messages.getString("ClientFrame.146")) == JFileChooser.APPROVE_OPTION) { //$NON-NLS-1$
            // remember directory in preferences
            preferences.put("directory.last", chooser.getCurrentDirectory().getAbsolutePath());

            uploadFiles(FileUtils.asPathsList(chooser.getSelectedFiles()));
        }
    }

    private void uploadFiles(final List<Path> files) {
        if (files != null && !files.isEmpty()) {
            final Runnable uploadTask = () -> {
                final UploadDialog upload = new UploadDialog();
                try {
                    client.parse(files, upload);
                    client.getResources();
                } catch (final XMLDBException e) {
                    showErrorMessage(Messages.getString("ClientFrame.147") + e.getMessage(), e);
                }
            };
            client.newClientThread("upload", uploadTask).start();
        }
    }

    private boolean deleteDirectory(final Path target) {
        try {
            FileUtils.delete(target);
            return true;
        } catch (final IOException e) {
            return false;
        }

    }

    private void backupAction(final ActionEvent ev) {

        //get the collection to highlight in the backup dialog
        final String defaultSelectedCollection;
        final ResourceDescriptor selResources[] = getSelectedResources();
        if (selResources != null) {
            if (selResources.length == 1 && selResources[0].isCollection()) {
                //use the selected collection
                defaultSelectedCollection = path.toString() + "/" + selResources[0].getName().toString();
            } else {
                //use the current collection
                defaultSelectedCollection = path.toString();
            }
        } else {
            defaultSelectedCollection = path.toString();
        }

        final CreateBackupDialog dialog = new CreateBackupDialog(
                properties.getProperty(InteractiveClient.URI, "xmldb:exist://"),
                properties.getProperty(InteractiveClient.USER, SecurityManager.DBA_USER),
                properties.getProperty(InteractiveClient.PASSWORD, null),
                Paths.get(preferences.get("directory.backup", System.getProperty("user.home"))),
                defaultSelectedCollection
        );

        if (JOptionPane.showOptionDialog(this, dialog, Messages.getString("ClientFrame.157"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null) == JOptionPane.YES_OPTION) {

            final String collection = dialog.getCollection();
            final String backuptarget = dialog.getBackupTarget();
            final boolean deduplicateBlobs = dialog.getDeduplicateBlobs();

            final Path target = Paths.get(backuptarget).normalize();
            if (Files.exists(target)) {
                final int response = JOptionPane.showConfirmDialog(this,
                        String.format("%s %s %s", Messages.getString("CreateBackupDialog.6a"), backuptarget, Messages.getString("CreateBackupDialog.6b")),
                        Messages.getString("CreateBackupDialog.6c"), JOptionPane.YES_NO_OPTION);

                if (response == JOptionPane.YES_OPTION) {
                    // User wants file/directory to be removed
                    deleteDirectory(target);
                } else {
                    JOptionPane.showMessageDialog(null, "Backup aborted, backup has not been deleted.");
                    return;
                }
            }

            try {
                final Backup backup = new Backup(
                        properties.getProperty(InteractiveClient.USER, SecurityManager.DBA_USER),
                        properties.getProperty(InteractiveClient.PASSWORD, null), Paths.get(backuptarget),
                        XmldbURI.xmldbUriFor(properties.getProperty(InteractiveClient.URI, "xmldb:exist://") + collection),
                        null,
                        deduplicateBlobs
                );
                backup.backup(true, this);
            } catch (final XMLDBException | IOException | SAXException | URISyntaxException e) {
                showErrorMessage(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
            }
        }
    }

    private void restoreAction(final ActionEvent ev) {
        final JFileChooser chooser = new JFileChooser(preferences.get("directory.backup", System.getProperty("user.dir")));
        chooser.setMultiSelectionEnabled(false);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.addChoosableFileFilter(new BackupContentsFilter());
        // re-enable later
        chooser.addChoosableFileFilter(new ZipFilter());

        if (chooser.showDialog(null, Messages.getString("ClientFrame.169")) == JFileChooser.APPROVE_OPTION) { //$NON-NLS-1$
            final Path f = chooser.getSelectedFile().toPath();
            preferences.put("directory.backup", chooser.getCurrentDirectory().getAbsolutePath());
            final JPanel askPass = new JPanel(new BorderLayout());
            askPass.add(new JLabel(Messages.getString("ClientFrame.170")), BorderLayout.NORTH); //$NON-NLS-1$
            final JPasswordField passInput = new JPasswordField(25);
            final JCheckBox overwriteCb = new JCheckBox(Messages.getString("ClientFrame.227"), false);
            askPass.add(passInput, BorderLayout.CENTER);
            askPass.add(overwriteCb, BorderLayout.SOUTH);
            if (JOptionPane.showOptionDialog(this, askPass, Messages.getString("ClientFrame.171"), //$NON-NLS-1$
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, null, null) == JOptionPane.YES_OPTION) {
                final String newDbaPass = passInput.getPassword().length == 0 ? null : new String(passInput.getPassword());
                final String restoreFile = f.toAbsolutePath().toString();
                final boolean overwriteApps = overwriteCb.isSelected();
                final GuiRestoreServiceTaskListener listener = new GuiRestoreServiceTaskListener(this);
                doRestore(listener, properties.getProperty(InteractiveClient.USER, SecurityManager.DBA_USER), properties.getProperty(InteractiveClient.PASSWORD, null), newDbaPass, Paths.get(restoreFile), properties.getProperty(InteractiveClient.URI, "xmldb:exist://"), overwriteApps);
            }
        }
    }

    private void doRestore(final GuiRestoreServiceTaskListener listener, final String username, final String password,
                           final String dbaPassword, final Path f, final String uri, final boolean overwriteApps) {

        final Runnable restoreTask = () -> {

            try {
                final XmldbURI dbUri;
                if(!uri.endsWith(XmldbURI.ROOT_COLLECTION)) {
                    dbUri = XmldbURI.xmldbUriFor(uri + XmldbURI.ROOT_COLLECTION);
                } else {
                    dbUri = XmldbURI.xmldbUriFor(uri);
                }

                final Collection collection = DatabaseManager.getCollection(dbUri.toString(), username, password);
                final EXistRestoreService service = (EXistRestoreService) collection.getService("RestoreService", "1.0");
                service.restore(f.toAbsolutePath().toString(), dbaPassword, listener, overwriteApps);

                if (JOptionPane.showConfirmDialog(null, Messages.getString("ClientFrame.223"), Messages.getString("ClientFrame.224"),
                        JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    setStatus(Messages.getString("ClientFrame.225"));
                    repairRepository(client.getCollection());
                    setStatus(Messages.getString("ClientFrame.226"));
                }

                listener.enableDismissDialogButton();

                if (properties.getProperty(InteractiveClient.USER, SecurityManager.DBA_USER).equals(SecurityManager.DBA_USER) && dbaPassword != null) {
                    properties.setProperty(InteractiveClient.PASSWORD, dbaPassword);
                }

                SwingUtilities.invokeAndWait(() -> {
                    try {
                        client.reloadCollection();
                    } catch (final XMLDBException xe) {
                        xe.printStackTrace();
                    }
                });
            } catch (final Exception e) {
                showErrorMessage(Messages.getString("ClientFrame.181") + e.getMessage(), e); //$NON-NLS-1$
            } finally {
                if (listener.hasProblems()) {
                    showErrorMessage(Messages.getString("ClientFrame.181") + listener.getAllProblems(), null);
                }
            }
        };

        client.newClientThread("restore", restoreTask).start();
    }

    public static void repairRepository(Collection collection) throws XMLDBException {
        final EXistXQueryService service = (EXistXQueryService) collection.getService("XQueryService", "1.0");
        service.query("import module namespace repair=\"http://exist-db.org/xquery/repo/repair\"\n" +
                "at \"resource:org/exist/xquery/modules/expathrepo/repair.xql\";\n" +
                "repair:clean-all(),\n" +
                "repair:repair()");
    }

    public UserManagementService getUserManagementService() throws XMLDBException {
        final Collection collection = client.getCollection();
        return (UserManagementService) collection.getService("UserManagementService", "1.0");
    }

    private void editUsersAction(final ActionEvent ev) {
        try {
            final UserManagementService userManagementService = getUserManagementService();

            final UserManagerDialog userManager = new UserManagerDialog(userManagementService, client.getProperties().getProperty(InteractiveClient.USER), this);
            userManager.setVisible(true);

        } catch (final XMLDBException e) {
            showErrorMessage(Messages.getString("ClientFrame.185"), e); //$NON-NLS-1$
            e.printStackTrace();
        }
    }

    private void exportAction(final ActionEvent ev) {
        if (fileman.getSelectedRowCount() == 0) {
            return;
        }
        final int[] rows = fileman.getSelectedRows();
        for (final int row : rows) {
            final ResourceDescriptor desc = resources.getRow(fileman.convertRowIndexToModel(row));
            if (desc.isCollection()) {
                continue;
            }

            final JFileChooser chooser = new JFileChooser(preferences.get("directory.last", System.getProperty("user.dir")));
            chooser.setMultiSelectionEnabled(false);
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setSelectedFile(Paths.get(desc.getName().getCollectionPath()).toFile());
            if (chooser.showDialog(this, "Select file for export") == JFileChooser.APPROVE_OPTION) {
                preferences.put("directory.last", chooser.getCurrentDirectory().getAbsolutePath());
                final Path file = chooser.getSelectedFile().toPath();
                if (Files.exists(file)
                        && JOptionPane.showConfirmDialog(this,
                        "File exists. Overwrite?", "Overwrite?",
                        JOptionPane.YES_NO_OPTION) == JOptionPane.NO_OPTION) {
                    return;
                }
                final Resource resource;
                final SAXSerializer contentSerializer;
                try {
                    final Collection collection = client.getCollection();
                    resource = collection
                            .getResource(desc.getName().toString());
                    if (resource instanceof ExtendedResource) {
                        try(final OutputStream os = new BufferedOutputStream(Files.newOutputStream(file))) {
                            ((ExtendedResource) resource).getContentIntoAStream(os);
                        }
                    } else {
                        contentSerializer = (SAXSerializer) SerializerPool
                                .getInstance()
                                .borrowObject(SAXSerializer.class);
                        try(final Writer writer = Files.newBufferedWriter(file, UTF_8)) {
                            // write resource to contentSerializer
                            contentSerializer.setOutput(writer, properties);
                            ((EXistResource) resource)
                                    .setLexicalHandler(contentSerializer);
                            ((XMLResource) resource)
                                    .getContentAsSAX(contentSerializer);
                        } finally {
                            SerializerPool.getInstance().returnObject(contentSerializer);
                        }
                    }
                    //TODO finally close os
                } catch (final Exception e) {
                    System.err.println("An exception occurred" + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    private void editIndexesAction(final ActionEvent ev) {
        final IndexDialog dialog = new IndexDialog(Messages.getString("ClientFrame.186"), client); //$NON-NLS-1$
        dialog.setVisible(true);
    }

    private void editTriggersAction(final ActionEvent ev) {
        final TriggersDialog dialog = new TriggersDialog("Edit Triggers", client);
        dialog.setVisible(true);
    }

    private void findAction(final ActionEvent ev) {
        final Collection collection = client.getCollection();
        final QueryDialog dialog = new QueryDialog(client, collection, properties);
        dialog.setVisible(true);
    }

    private void setPermAction(final ActionEvent ev) throws PermissionDeniedException {
        if (fileman.getSelectedRowCount() == 0) {
            return;
        }
        try {
            final Collection collection = client.getCollection();
            final UserManagementService service = getUserManagementService();

            String name = null;
            String created = null;
            String modified = null;
            String size = null;
            String messageDigestType = null;
            String messageDigestValue = null;
            String mimeType = null;
            String owner = null;
            String group = null;
            ModeDisplay mode = null;
            SimpleACLPermissionAider acl = null;

            final DateFormat dateTimeFormat = DateFormat.getDateTimeInstance();

            final List<ResourceDescriptor> selected = new ArrayList<>();

            boolean firstPerm = true;
            for (final int row : fileman.getSelectedRows()) {
                final ResourceDescriptor selectedRow = resources.getRow(row);

                selected.add(selectedRow);

                final XmldbURI thisName = selectedRow.getName();
                final String thisCreated;
                final String thisModified;
                final String thisMessageDigestType;
                final String thisMessageDigestValue;
                final String thisSize;
                final String thisMimeType;
                final Permission thisPerm;

                if (selectedRow.isCollection()) {
                    final Collection coll = collection.getChildCollection(thisName.toString());
                    thisCreated = dateTimeFormat.format(((EXistCollection) coll).getCreationTime());
                    thisModified = NON_APPLICABLE;
                    thisMimeType = COLLECTION_MIME_TYPE;
                    thisMessageDigestType = NON_APPLICABLE;
                    thisMessageDigestValue = NON_APPLICABLE;
                    thisSize = NON_APPLICABLE;
                    thisPerm = service.getPermissions(coll);
                } else {
                    final Resource res = collection.getResource(thisName.toString());
                    thisCreated = dateTimeFormat.format(((EXistResource) res).getCreationTime());
                    thisModified = dateTimeFormat.format(((EXistResource) res).getLastModificationTime());
                    thisMimeType = ((EXistResource) res).getMimeType();
                    if (res instanceof EXistBinaryResource) {
                        final MessageDigest messageDigest = ((EXistBinaryResource) res).getContentDigest(DigestType.BLAKE_256);
                        thisMessageDigestType = messageDigest.getDigestType().getCommonNames()[0];
                        thisMessageDigestValue = messageDigest.toHexString();
                        thisSize = humanSize(((EXistBinaryResource) res).getContentLength());
                    } else {
                        thisMessageDigestType = NON_APPLICABLE;
                        thisMessageDigestValue = NON_APPLICABLE;
                        thisSize = NON_APPLICABLE;
                    }
                    thisPerm = service.getPermissions(res);
                }

                name = getUpdated(name, () ->  URIUtils.urlDecodeUtf8(thisName));
                created = getUpdated(created, thisCreated);
                modified = getUpdated(modified, thisModified);
                mimeType = getUpdated(mimeType, thisMimeType);
                messageDigestType = getUpdated(messageDigestType, thisMessageDigestType);
                messageDigestValue = getUpdated(messageDigestValue, thisMessageDigestValue);
                size = getUpdated(size, thisSize);
                owner = getUpdated(owner, () -> thisPerm.getOwner().getName());
                group = getUpdated(group, () -> thisPerm.getGroup().getName());
                mode = getUpdatedMode(mode, thisPerm);

                if (firstPerm) {
                    if (thisPerm instanceof ACLPermission) {
                        final ACLPermission thisAcl = (ACLPermission) thisPerm;
                        acl = new SimpleACLPermissionAider();
                        for (int i = 0; i < thisAcl.getACECount(); i++) {
                            acl.addACE(thisAcl.getACEAccessType(i), thisAcl.getACETarget(i), thisAcl.getACEWho(i), thisAcl.getACEMode(i));
                        }
                    } else {
                        acl = null;
                    }
                    firstPerm = false;
                } else {
                    if (acl != null && thisPerm instanceof ACLPermission) {
                        final ACLPermission thisAcl = (ACLPermission) thisPerm;
                        if (!acl.aclEquals(thisAcl)) {
                            acl = null;
                        }
                    }
                }
            }

            final EditPropertiesDialog editPropertiesDialog = new EditPropertiesDialog(service, client.getProperties().getProperty(InteractiveClient.USER), collection, name, mimeType, created, modified, size, messageDigestType, messageDigestValue, owner, group, mode, acl, selected);
            editPropertiesDialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(final WindowEvent e) {
                    try {
                        client.reloadCollection();
                    } catch (final XMLDBException xmldbe) {
                        showErrorMessage(Messages.getString("ClientFrame.197") + xmldbe.getMessage(), xmldbe); //$NON-NLS-1$
                        xmldbe.printStackTrace();
                    }
                }
            });
            editPropertiesDialog.setVisible(true);

        } catch (final XMLDBException e) {
            showErrorMessage(Messages.getString("ClientFrame.197") + e.getMessage(), e); //$NON-NLS-1$
            e.printStackTrace();
        }
    }

    private String getUpdated(final String previousValue, final Supplier<String> nextValueSupplier) {
        if (!MULTIPLE_INDICATOR.equals(previousValue)) {
            final String nextValue = nextValueSupplier.get();
            if (previousValue == null) {
                return nextValue;
            } else {
                if (!previousValue.equals(nextValue)) {
                    return MULTIPLE_INDICATOR;
                }
            }
        }

        return previousValue;
    }

    private String getUpdated(final String previousValue, final String nextValue) {
        if (!MULTIPLE_INDICATOR.equals(previousValue)) {
            if (previousValue == null) {
                return nextValue;
            } else {
                if (!previousValue.equals(nextValue)) {
                    return MULTIPLE_INDICATOR;
                }
            }
        }

        return previousValue;
    }

    private ModeDisplay getUpdatedMode(ModeDisplay previousMode, final Permission permission) {
        if (previousMode == null) {
            return ModeDisplay.fromPermission(permission);
        }

        final int ownerMode = permission.getOwnerMode();
        final boolean ownerRead = (ownerMode & Permission.READ) == Permission.READ;
        final boolean ownerWrite = (ownerMode & Permission.WRITE) == Permission.WRITE;
        final boolean ownerExecute = (ownerMode & Permission.EXECUTE) == Permission.EXECUTE;

        final int groupMode = permission.getGroupMode();
        final boolean groupRead = (groupMode & Permission.READ) == Permission.READ;
        final boolean groupWrite = (groupMode & Permission.WRITE) == Permission.WRITE;
        final boolean groupExecute = (groupMode & Permission.EXECUTE) == Permission.EXECUTE;

        final int otherMode = permission.getOtherMode();
        final boolean otherRead = (otherMode & Permission.READ) == Permission.READ;
        final boolean otherWrite = (otherMode & Permission.WRITE) == Permission.WRITE;
        final boolean otherExecute = (otherMode & Permission.EXECUTE) == Permission.EXECUTE;

        final boolean setUid = permission.isSetUid();
        final boolean setGid = permission.isSetGid();
        final boolean sticky = permission.isSticky();

        previousMode.ownerRead = getUpdatedModeBit(previousMode.ownerRead, ownerRead);
        previousMode.ownerWrite = getUpdatedModeBit(previousMode.ownerWrite, ownerWrite);
        previousMode.ownerExecute = getUpdatedModeBit(previousMode.ownerExecute, ownerExecute);

        previousMode.groupRead = getUpdatedModeBit(previousMode.groupRead, groupRead);
        previousMode.groupWrite = getUpdatedModeBit(previousMode.groupWrite, groupWrite);
        previousMode.groupExecute = getUpdatedModeBit(previousMode.groupExecute, groupExecute);

        previousMode.otherRead = getUpdatedModeBit(previousMode.otherRead, otherRead);
        previousMode.otherWrite = getUpdatedModeBit(previousMode.otherWrite, otherWrite);
        previousMode.otherExecute = getUpdatedModeBit(previousMode.otherExecute, otherExecute);

        previousMode.setUid = getUpdatedModeBit(previousMode.setUid, setUid);
        previousMode.setGid = getUpdatedModeBit(previousMode.setGid, setGid);
        previousMode.sticky = getUpdatedModeBit(previousMode.sticky, sticky);

        return previousMode;
    }

    private Boolean getUpdatedModeBit(final Boolean previousModeBit, final boolean nextModeBit) {
        if (previousModeBit == null || previousModeBit.booleanValue() != nextModeBit) {
            return null;
        }
        return previousModeBit;
    }

    private void enter() {
        int end = doc.getLength();
        if (end - commandStart == 0) {
            return;
        }
        try {
            final String command = doc.getText(commandStart, end - commandStart);
            commandStart = end;
            doc.insertString(commandStart++, "\n", defaultAttrs); //$NON-NLS-1$
            if (command != null) {
                processRunnable.setAction(command);
                client.console.getHistory().add(command);
            }
        } catch (final BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void historyBack() {
        client.console.getHistory().previous();
        final String item = client.console.getHistory().current();
        if (item == null) {
            return;
        }
        try {
            if (shell.getCaretPosition() > commandStart) {
                doc.remove(commandStart, doc.getLength() - commandStart);
            }
            doc.insertString(commandStart, item, defaultAttrs);
        } catch (final BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void historyForward() {
        client.console.getHistory().next();
        final String item = client.console.getHistory().current();
        try {
            if (shell.getCaretPosition() > commandStart) {
                doc.remove(commandStart, doc.getLength() - commandStart);
            }
            doc.insertString(commandStart, item, defaultAttrs);
        } catch (final BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void close() {
        setVisible(false);
        dispose();
        processThread.interrupt();
        System.exit(SystemExitCodes.OK_EXIT_CODE);
    }

    private void AboutAction() {
        JOptionPane.showMessageDialog(this, client.getNotice());
    }

    class TableMouseListener extends MouseAdapter {

        @Override
        public void mouseClicked(final MouseEvent e) {
            if (e.getClickCount() == 2) {
                final int row = fileman.convertRowIndexToModel(fileman.getSelectedRow());
                final ResourceDescriptor resource = resources.getRow(row);
                if (resource.isCollection()) {
                    // cd into collection
                    final String command = "cd " + '"' + resource.getName() + '"'; //$NON-NLS-1$
                    display(command + "\n"); //$NON-NLS-1$
                    processRunnable.setAction(command);
                } else {
                    // open a document for editing
                    ClientFrame.this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    try {
                        final Resource doc = client.retrieve(resource.getName(), properties.getProperty(OutputKeys.INDENT, "yes")); //$NON-NLS-1$

                        if ("application/xquery".equals(((EXistResource) doc).getMimeType())) {
                            final Collection collection = client.getCollection();
                            final QueryDialog dialog = new QueryDialog(client, collection, doc, properties);
                            dialog.setVisible(true);
                        } else {
                            final DocumentView view = new DocumentView(client, resource.getName(), doc, properties);
                            view.setSize(new Dimension(640, 400));
                            view.viewDocument();
                        }
                        //doc will be closed in one of the dialogs above when they are closed
                    } catch (final XMLDBException ex) {
                        showErrorMessage(Messages.getString("ClientFrame.206") + ex.getMessage(), ex); //$NON-NLS-1$
                    }

                    ClientFrame.this.setCursor(Cursor.getDefaultCursor());
                }
            }
        }

    }

    /**
     * Compares resources according to their name, ensuring that collections
     * always are before documents.
     *
     * @author gpothier
     */
    private static class ResourceComparator implements Comparator<ResourceDescriptor> {
        @Override
        public int compare(final ResourceDescriptor desc1, final ResourceDescriptor desc2) {
            if (desc1.isCollection() != desc2.isCollection()) {
                return desc1.isCollection() ? Constants.INFERIOR : Constants.SUPERIOR;
            } else {
                return desc1.getName().compareTo(desc2.getName());
            }
        }
    }

    private class ProcessRunnable implements Runnable {
        private final TransferQueue<String> queue = new LinkedTransferQueue<>();

        public void setAction(final String action) {
            queue.add(action);
        }

        @Override
        public void run() {
            while (true) {
                final String action;
                try {
                    action = queue.take();
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                final boolean status = client.process(action);
                displayPrompt();

                if (!status) {
                    close();
                    break;
                }
            }
        }
    }

    static class ResourceTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        private final String[] columnNames = new String[]{
                Messages.getString("ClientFrame.207") //$NON-NLS-1$
                , Messages.getString("ClientFrame.208") //$NON-NLS-1$
                , Messages.getString("ClientFrame.209") //$NON-NLS-1$
                , Messages.getString("ClientFrame.210") //$NON-NLS-1$
                , Messages.getString("ClientFrame.211") //$NON-NLS-1$
        };

        private List<ResourceDescriptor> rows = null;

        private DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        public void setData(final List<ResourceDescriptor> rows) {
            rows.sort(new ResourceComparator());
            this.rows = rows;
            fireTableDataChanged();
        }

        public ResourceDescriptor getRow(final int index) {
            return getRowCount() > 0 ? rows.get(index) : null;
        }

        /*
         * (non-Javadoc)
         *
         * @see javax.swing.table.TableModel#getColumnCount()
         */
        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        /*
         * (non-Javadoc)
         *
         * @see javax.swing.table.TableModel#getColumnName(int)
         */
        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        /*
         * (non-Javadoc)
         *
         * @see javax.swing.table.TableModel#getRowCount()
         */
        @Override
        public int getRowCount() {
            return rows == null ? 0 : rows.size();
        }

        /*
         * (non-Javadoc)
         *
         * @see javax.swing.table.TableModel#getValueAt(int, int)
         */
        @Override
        public Object getValueAt(final int rowIndex, final int columnIndex) {
            if (getRowCount() > 0) {
                final ResourceDescriptor row = getRow(rowIndex);
                switch (columnIndex) {
                    case 0:
                        return row.getName().toString();
                    case 1:
                        return dateFormat.format(row.getDate());
                    case 2:
                        return row.getOwner();
                    case 3:
                        return row.getGroup();
                    case 4:
                        return row.getPermissionsDescription();
                    default:
                        throw new RuntimeException(Messages.getString("ClientFrame.212")); //$NON-NLS-1$
                }
            }
            return "";
        }

        /*
         * (non-Javadoc)
         *
         * @see javax.swing.table.TableModel#getColumnClass(int)
         */
        @Override
        public Class getColumnClass(final int column) {
            return getValueAt(0, column).getClass();
        }
    }

    /**
     * @param props pass properties to the login panel
     * @return the modified properties
     */
    protected static Properties getLoginData(final Properties props) {

        final Properties properties = new Properties();

        final String serverUri;
        if (props.getProperty(InteractiveClient.URI) == null || props.getProperty(InteractiveClient.URI).isEmpty()) {
            serverUri = InteractiveClient.URI_DEFAULT;
        } else {
            if (Boolean.parseBoolean(props.getProperty(InteractiveClient.LOCAL_MODE, "FALSE"))) {
                serverUri = InteractiveClient.URI_DEFAULT;
            } else {
                serverUri = props.getProperty(InteractiveClient.URI);
            }
        }

        final DefaultConnectionSettings defaultConnectionSettings = new DefaultConnectionSettings(
                props.getProperty(InteractiveClient.USER, InteractiveClient.USER_DEFAULT),
                props.getProperty(InteractiveClient.PASSWORD, ""),
                serverUri,
                Boolean.parseBoolean(props.getProperty(InteractiveClient.SSL_ENABLE, InteractiveClient.SSL_ENABLE_DEFAULT))
        );
        defaultConnectionSettings.setConfiguration(props.getProperty(InteractiveClient.CONFIGURATION, ""));

        final ConnectionDialog connectionDialog = new ConnectionDialog(null, true, defaultConnectionSettings, Boolean.parseBoolean(props.getProperty(InteractiveClient.LOCAL_MODE, InteractiveClient.LOCAL_MODE_DEFAULT)), Boolean.parseBoolean(props.getProperty(InteractiveClient.NO_EMBED_MODE, InteractiveClient.NO_EMBED_MODE_DEFAULT)));

        connectionDialog.setTitle(ExistSystemProperties.getInstance().getExistSystemProperty(ExistSystemProperties.PROP_PRODUCT_NAME, "eXist-db") + " " + ExistSystemProperties.getInstance().getExistSystemProperty(ExistSystemProperties.PROP_PRODUCT_VERSION, "unknown") + " Database Login");

        connectionDialog.addDialogCompleteWithResponseCallback(connection -> {
            properties.setProperty(InteractiveClient.USER, connection.getUsername());
            properties.setProperty(InteractiveClient.PASSWORD, connection.getPassword());

            if (!connection.getUri().isEmpty()) {
                properties.setProperty(InteractiveClient.URI, connection.getUri());
                properties.setProperty(InteractiveClient.SSL_ENABLE, Boolean.valueOf(connection.isSsl()).toString().toUpperCase());
                properties.setProperty(InteractiveClient.LOCAL_MODE, "FALSE");
            } else {
                properties.setProperty(InteractiveClient.CONFIGURATION, connection.getConfiguration());
                properties.setProperty(InteractiveClient.URI, XmldbURI.EMBEDDED_SERVER_URI.toString());
            }
        });

        connectionDialog.setVisible(true);

        return properties;
    }

    public static void showErrorMessage(final String message, final Throwable t) {
        JScrollPane scroll = null;
        final JTextArea msgArea = new JTextArea(message);
        msgArea.setBorder(BorderFactory.createTitledBorder(Messages.getString("ClientFrame.214"))); //$NON-NLS-1$
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
                    .createTitledBorder(Messages.getString("ClientFrame.215"))); //$NON-NLS-1$
        }
        final JOptionPane optionPane = new JOptionPane();
        optionPane.setMessage(new Object[]{msgArea, scroll});
        optionPane.setMessageType(JOptionPane.ERROR_MESSAGE);
        final JDialog dialog = optionPane.createDialog(null, Messages.getString("ClientFrame.216")); //$NON-NLS-1$
        dialog.setResizable(true);
        dialog.pack();
        dialog.setVisible(true);
    }

    public static int showErrorMessageQuery(final String message, final Throwable t) {
        final JTextArea msgArea = new JTextArea(message);
        msgArea.setLineWrap(true);
        msgArea.setWrapStyleWord(true);
        msgArea.setEditable(false);
        msgArea.setBackground(null);
        JScrollPane scrollMsgArea = new JScrollPane(msgArea);
        scrollMsgArea.setPreferredSize(new Dimension(600, 300));
        scrollMsgArea.setBorder(BorderFactory
                .createTitledBorder(Messages.getString("ClientFrame.217"))); //$NON-NLS-1$

        JScrollPane scrollStacktrace = null;
        if (t != null) {
            try (final StringWriter out = new StringWriter();
                 final PrintWriter writer = new PrintWriter(out)) {
                t.printStackTrace(writer);
                final JTextArea stacktrace = new JTextArea(out.toString(), 20, 50);
                stacktrace.setLineWrap(true);
                stacktrace.setWrapStyleWord(true);
                stacktrace.setBackground(null);
                stacktrace.setEditable(false);
                scrollStacktrace = new JScrollPane(stacktrace);
                scrollStacktrace.setPreferredSize(new Dimension(600, 300));
                scrollStacktrace.setBorder(BorderFactory
                        .createTitledBorder(Messages.getString("ClientFrame.218"))); //$NON-NLS-1$
            } catch (final IOException ioe) {
                ioe.printStackTrace();
            }
        }

        final JOptionPane optionPane = new JOptionPane();
        optionPane.setMessage(new Object[]{scrollMsgArea, scrollStacktrace});
        optionPane.setMessageType(JOptionPane.ERROR_MESSAGE);
        optionPane.setOptionType(JOptionPane.OK_CANCEL_OPTION);
        final JDialog dialog = optionPane.createDialog(null, Messages.getString("ClientFrame.219")); //$NON-NLS-1$
        dialog.setResizable(true);
        dialog.pack();
        dialog.setVisible(true);

        final Object result = optionPane.getValue();
        if (result == null) {
            return 2;
        }
        return (Integer) optionPane.getValue();
    }

    /*
     * (non-Javadoc)
     *
     * @see java.awt.event.WindowFocusListener#windowGainedFocus(java.awt.event.WindowEvent)
     */
    @Override
    public void windowGainedFocus(final WindowEvent e) {
        toFront();
    }

    /*
     * (non-Javadoc)
     *
     * @see java.awt.event.WindowFocusListener#windowLostFocus(java.awt.event.WindowEvent)
     */
    @Override
    public void windowLostFocus(final WindowEvent e) {
    }

    /*
     * (non-Javadoc)
     *
     * @see java.awt.event.MouseListener#mouseClicked(java.awt.event.MouseEvent)
     */
    @Override
    public void mouseClicked(final MouseEvent e) {
    }

    /*
     * (non-Javadoc)
     *
     * @see java.awt.event.MouseListener#mouseEntered(java.awt.event.MouseEvent)
     */
    @Override
    public void mouseEntered(final MouseEvent e) {
    }

    /*
     * (non-Javadoc)
     *
     * @see java.awt.event.MouseListener#mouseExited(java.awt.event.MouseEvent)
     */
    @Override
    public void mouseExited(final MouseEvent e) {
    }

    /*
     * (non-Javadoc)
     *
     * @see java.awt.event.MouseListener#mousePressed(java.awt.event.MouseEvent)
     */
    @Override
    public void mousePressed(final MouseEvent e) {
        if (e.isPopupTrigger()) {
            shellPopup.show((Component) e.getSource(), e.getX(), e.getY());
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see java.awt.event.MouseListener#mouseReleased(java.awt.event.MouseEvent)
     */
    @Override
    public void mouseReleased(final MouseEvent e) {
        if (e.isPopupTrigger()) {
            shellPopup.show((Component) e.getSource(), e.getX(), e.getY());
        }
    }

    static class BinaryFileFilter extends FileFilter {

        /* (non-Javadoc)
         * @see javax.swing.filechooser.FileFilter#getDescription()
         */
        @Override
        public String getDescription() {
            return Messages.getString("ClientFrame.220"); //$NON-NLS-1$
        }

        /* (non-Javadoc)
         * @see javax.swing.filechooser.FileFilter#accept(java.io.File)
         */
        @Override
        public boolean accept(final File f) {
            if (f.isDirectory()) {
                return true;
            }
            return !MimeTable.getInstance().isXMLContent(f.getName());
        }
    }

    static class XMLFileFilter extends FileFilter {

        /* (non-Javadoc)
         * @see javax.swing.filechooser.FileFilter#getDescription()
         */
        @Override
        public String getDescription() {
            return Messages.getString("ClientFrame.221"); //$NON-NLS-1$
        }

        /* (non-Javadoc)
         * @see javax.swing.filechooser.FileFilter#accept(java.io.File)
         */
        @Override
        public boolean accept(final File f) {
            if (f.isDirectory()) {
                return true;
            }
            return MimeTable.getInstance().isXMLContent(f.getName());
        }
    }

    private class FileListDropTargetListener implements DropTargetListener {

        @Override
        public void dragEnter(final DropTargetDragEvent dtde) {
        }

        @Override
        public void dragOver(final DropTargetDragEvent dtde) {
        }

        @Override
        public void dropActionChanged(final DropTargetDragEvent dtde) {
        }

        @Override
        public void dragExit(final DropTargetEvent dte) {

        }

        @Override
        public void drop(final DropTargetDropEvent dtde) {
            try {

                dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);

                final Transferable transferable = dtde.getTransferable();

                //should work for Win32 systems
                List<Path> files = getFilesWin32(transferable);

                //should work for *nix systems
                if (files == null) {
                    files = getFilesUnix(transferable);
                }

                if (files != null) {
                    uploadFiles(files);
                }
            } catch (final URISyntaxException | IOException | UnsupportedFlavorException | ClassNotFoundException use) {
                System.err.println("An exception occurred while dragging and dropping files: " + use.getMessage());
                use.printStackTrace();
            }
        }

        private List<Path> getFilesWin32(final Transferable transferable) throws UnsupportedFlavorException, IOException {
            return ((List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor)).stream().map(File::toPath).collect(Collectors.toList());
        }

        private List<Path> getFilesUnix(final Transferable transferable) throws ClassNotFoundException, UnsupportedFlavorException, IOException, URISyntaxException {

            List<Path> files = null;
            final DataFlavor unixFileDataFlavour = new DataFlavor("text/uri-list;class=java.lang.String");
            final String data = (String) transferable.getTransferData(unixFileDataFlavour);
            for (final StringTokenizer st = new StringTokenizer(data, "\r\n"); st.hasMoreTokens(); ) {
                final String token = st.nextToken().trim();
                if (token.startsWith("#") || token.isEmpty()) {
                    // comment line, by RFC 2483
                    continue;
                }

                //lazy
                if (files == null) {
                    files = new ArrayList<>();
                }

                files.add(Paths.get(new URI(token)));
            }

            return files;
        }
    }
}
