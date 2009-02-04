/*
 * eXist Open Source Native XML Database
 *
 * Copyright (C) 2001-06 Wolfgang M. Meier wolfgang@exist-db.org
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 * $Id$
 */
package org.exist.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ProgressMonitor;
import javax.swing.border.BevelBorder;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.xml.transform.OutputKeys;

import org.exist.backup.Backup;
import org.exist.backup.CreateBackupDialog;
import org.exist.backup.Restore;
import org.exist.client.xacml.XACMLEditor;
import org.exist.security.Permission;
import org.exist.security.PermissionFactory;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.MimeTable;
import org.exist.util.SingleInstanceConfiguration;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.CollectionImpl;
import org.exist.xmldb.CollectionManagementServiceImpl;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.ExtendedResource;
import org.exist.xmldb.IndexQueryService;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;
import org.exist.xquery.util.URIUtils;
import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/** Main frame of the eXist GUI */
public class ClientFrame extends JFrame
        implements
        WindowFocusListener,
        KeyListener,
        ActionListener,
        MouseListener {
    
	private static final long serialVersionUID = 1L;
	public final static String CUT = Messages.getString("ClientFrame.0"); //$NON-NLS-1$
    public final static String COPY = Messages.getString("ClientFrame.1"); //$NON-NLS-1$
    public final static String PASTE = Messages.getString("ClientFrame.2"); //$NON-NLS-1$
    
    public final static int MAX_DISPLAY_LENGTH = 512000;
    public final static int MAX_HISTORY = 50;
    
    private final static SimpleAttributeSet promptAttrs = new SimpleAttributeSet();
    private final static SimpleAttributeSet defaultAttrs = new SimpleAttributeSet();
    
    {
        StyleConstants.setForeground(promptAttrs, Color.blue);
        StyleConstants.setBold(promptAttrs, true);
        StyleConstants.setForeground(defaultAttrs, Color.black);
    }
    
    private int commandStart = 0;

    private boolean gotUp = false;
    private DefaultStyledDocument doc;
    private JLabel statusbar;
    private JTable fileman;
    private ResourceTableModel resources = new ResourceTableModel();
    private JTextPane shell;
    private JPopupMenu shellPopup;
    private InteractiveClient client;
    private XmldbURI path = null;
    private ProcessThread process = new ProcessThread();
    private Properties properties;
    private Preferences preferences;

    /**
     * @throws java.awt.HeadlessException
     */
    public ClientFrame(InteractiveClient client, XmldbURI path,
            Properties properties) throws HeadlessException {
        super(Messages.getString("ClientFrame.3")); //$NON-NLS-1$
        this.path = path;
        this.properties = properties;
        this.client = client;
        
        setupComponents();
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent ev) {
                close();
            }
        });
        pack();
        
        process.start();
        shell.requestFocus();

        preferences = Preferences.userNodeForPackage(ClientFrame.class);
    }
    
    private void setupComponents() {
        setJMenuBar(createMenuBar());
        
        // create the toolbar
        JToolBar toolbar = new JToolBar();
        URL url = getClass().getResource("icons/Up24.gif"); //$NON-NLS-1$
        JButton button = new JButton(new ImageIcon(url));
        button.setToolTipText(Messages.getString("ClientFrame.5")); //$NON-NLS-1$
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                goUpAction(e);
            }
        });
        toolbar.add(button);
        
        url = getClass().getResource("icons/Refresh24.gif"); //$NON-NLS-1$
        button = new JButton(new ImageIcon(url));
        button.setToolTipText(Messages.getString("ClientFrame.7")); //$NON-NLS-1$
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    client.reloadCollection();
                } catch (XMLDBException e1) {
                }
            }
        });
        toolbar.add(button);
        toolbar.addSeparator();
        
        url = getClass().getResource("icons/New24.gif"); //$NON-NLS-1$
        button = new JButton(new ImageIcon(url));
        button.setToolTipText(Messages.getString("ClientFrame.9")); //$NON-NLS-1$
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                newCollectionAction(e);
            }
        });
        toolbar.add(button);
        
        url = getClass().getResource("icons/Add24.gif"); //$NON-NLS-1$
        button = new JButton(new ImageIcon(url));
        button.setToolTipText(Messages.getString("ClientFrame.11")); //$NON-NLS-1$
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uploadAction(e);
            }
        });
        toolbar.add(button);
        
        url = getClass().getResource("icons/Delete24.gif"); //$NON-NLS-1$
        button = new JButton(new ImageIcon(url));
        button.setToolTipText(Messages.getString("ClientFrame.13")); //$NON-NLS-1$
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                removeAction(e);
            }
        });
        toolbar.add(button);
        
        url = getClass().getResource(Messages.getString("ClientFrame.14")); //$NON-NLS-1$
        button = new JButton(new ImageIcon(url));
        button.setToolTipText(Messages.getString("ClientFrame.15")); //$NON-NLS-1$
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setPermAction(e);
            }
        });
        toolbar.add(button);
        
        toolbar.addSeparator();
        url = getClass().getResource("icons/Export24.gif"); //$NON-NLS-1$
        button = new JButton(new ImageIcon(url));
        button.setToolTipText(Messages.getString("ClientFrame.17")); //$NON-NLS-1$
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                backupAction(e);
            }
        });
        toolbar.add(button);
        
        url = getClass().getResource("icons/Import24.gif"); //$NON-NLS-1$
        button = new JButton(new ImageIcon(url));
        button.setToolTipText(Messages.getString("ClientFrame.19")); //$NON-NLS-1$
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                restoreAction(e);
            }
        });
        toolbar.add(button);
        
        toolbar.addSeparator();
        url = getClass().getResource(Messages.getString("ClientFrame.20")); //$NON-NLS-1$
        button = new JButton(new ImageIcon(url));
        button.setToolTipText(Messages.getString("ClientFrame.21")); //$NON-NLS-1$
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                editUsersAction(e);
            }
        });
        toolbar.add(button);
        
        url = getClass().getResource("icons/Find24.gif"); //$NON-NLS-1$
        button = new JButton(new ImageIcon(url));
        button.setToolTipText(Messages.getString("ClientFrame.23")); //$NON-NLS-1$
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                findAction(e);
            }
        });
        toolbar.add(button);
        
        // the split pane separates the resource view table from the shell
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        split.setResizeWeight(0.5);
        
        // create table for resources and collections
        fileman = new JTable();
        fileman.setModel(resources);
        fileman.addMouseListener(new TableMouseListener());
        
        ResourceTableCellRenderer renderer = new ResourceTableCellRenderer();
        fileman.setDefaultRenderer(Object.class, renderer);
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
        
        statusbar = new JLabel(Messages.getString("ClientFrame.27") + properties.getProperty("user") + "@" + properties.getProperty("uri")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        statusbar.setMinimumSize(new Dimension(400, 15));
        statusbar.setBorder(BorderFactory
                .createBevelBorder(BevelBorder.LOWERED));
        
        getContentPane().add(split, BorderLayout.CENTER);
        getContentPane().add(toolbar, BorderLayout.NORTH);
        getContentPane().add(statusbar, BorderLayout.SOUTH);
    }
    
    private JMenuBar createMenuBar() {
        JMenuBar menubar = new JMenuBar();
        
        JMenu fileMenu = new JMenu(Messages.getString("ClientFrame.31")); //$NON-NLS-1$
        fileMenu.setMnemonic(KeyEvent.VK_F);
        menubar.add(fileMenu);
        
        JMenuItem item = new JMenuItem(Messages.getString("ClientFrame.32"), KeyEvent.VK_S); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke("control S")); //$NON-NLS-1$
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uploadAction(e);
            }
        });
        fileMenu.add(item);
        
        item = new JMenuItem(Messages.getString("ClientFrame.34"), KeyEvent.VK_N); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke("control N")); //$NON-NLS-1$
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                newCollectionAction(e);
            }
        });
        fileMenu.add(item);
        
        item = new JMenuItem(Messages.getString("ClientFrame.36"), KeyEvent.VK_B); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke("control B")); //$NON-NLS-1$
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                //FIXME: Prevent owerwrite. Security?
                Collection collection = client.current;
                XMLResource result = null;
                String nameres = JOptionPane.showInputDialog(null,
                        Messages.getString("ClientFrame.38")); //$NON-NLS-1$
                if (nameres != null) {
                    try {
                        result = (XMLResource) collection.createResource(
                                URIUtils.urlEncodeUtf8(nameres), XMLResource.RESOURCE_TYPE);
                        result.setContent(Messages.getString("ClientFrame.39")); //$NON-NLS-1$
                        collection.storeResource(result);
                        collection.close();
                        client.reloadCollection();
                    } catch (XMLDBException ev) {
                        showErrorMessage(ev.getMessage(), ev);
                    }
                    
                }
            }
        });
        fileMenu.add(item);
        fileMenu.addSeparator();
        
        item = new JMenuItem(Messages.getString("ClientFrame.40")); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke("control D")); //$NON-NLS-1$
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                removeAction(e);
            }
        });
        fileMenu.add(item);
        
        item = new JMenuItem(Messages.getString("ClientFrame.42"), KeyEvent.VK_C); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke("control C")); //$NON-NLS-1$
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                copyAction(e);
            }
        });
        fileMenu.add(item);
        
        item = new JMenuItem(Messages.getString("ClientFrame.44"), KeyEvent.VK_M); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke("control M")); //$NON-NLS-1$
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                moveAction(e);
            }
        });
        fileMenu.add(item);
        
        item = new JMenuItem(Messages.getString("ClientFrame.46"), KeyEvent.VK_R); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke("control R")); //$NON-NLS-1$
        item.addActionListener(new ActionListener() {
        	public void actionPerformed(ActionEvent e) {
        		renameAction(e);
        	}
        });
		fileMenu.add(item);
        
        item = new JMenuItem(Messages.getString("ClientFrame.47"), KeyEvent.VK_E);
        item.setAccelerator(KeyStroke.getKeyStroke("control E"));
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                exportAction(e);
            }
        });
        fileMenu.add(item);
        
        fileMenu.addSeparator();
        
        item = new JMenuItem(Messages.getString("ClientFrame.48"), KeyEvent.VK_I); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke("control I")); //$NON-NLS-1$
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                reindexAction(e);
            }
        });
        fileMenu.add(item);
        
        item = new JMenuItem(Messages.getString("ClientFrame.50")); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke("control P")); //$NON-NLS-1$
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setPermAction(e);
            }
        });
        fileMenu.add(item);
        
        fileMenu.addSeparator();
        item = new JMenuItem(Messages.getString("ClientFrame.52"), KeyEvent.VK_Q); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke("control Q")); //$NON-NLS-1$
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                close();
            }
        });
        fileMenu.add(item);
        
        JMenu toolsMenu = new JMenu(Messages.getString("ClientFrame.54")); //$NON-NLS-1$
        toolsMenu.setMnemonic(KeyEvent.VK_T);
        menubar.add(toolsMenu);
        
        item = new JMenuItem(Messages.getString("ClientFrame.55"), KeyEvent.VK_F); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke("control F")); //$NON-NLS-1$
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                findAction(e);
            }
        });
        toolsMenu.add(item);
        
        toolsMenu.addSeparator();
        
        item = new JMenuItem(Messages.getString("ClientFrame.57"), KeyEvent.VK_U); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke("control U")); //$NON-NLS-1$
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                editUsersAction(e);
            }
        });
        toolsMenu.add(item);
        
        item = new JMenuItem(Messages.getString("ClientFrame.59"), KeyEvent.VK_U); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke("control I")); //$NON-NLS-1$
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                editIndexesAction(e);
            }
        });
        toolsMenu.add(item);
        
        item = new JMenuItem("Edit Triggers", KeyEvent.VK_T);
        item.setAccelerator(KeyStroke.getKeyStroke("control T"));
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                editTriggersAction(e);
            }
        });
        toolsMenu.add(item);
        
        item = new JMenuItem(Messages.getString("ClientFrame.61"), KeyEvent.VK_O); //$NON-NLS-1$
        item.setAccelerator(KeyStroke.getKeyStroke("control O")); //$NON-NLS-1$
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                editPolicies();
            }
        });
        toolsMenu.add(item);

        toolsMenu.addSeparator();

        item = new JMenuItem("Enter service mode");
        item.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                try {
                    DatabaseInstanceManager service = (DatabaseInstanceManager) client.current.getService("DatabaseInstanceManager", "1.0");
                    service.enterServiceMode();
                } catch (XMLDBException ex) {
                    showErrorMessage(ex.getMessage(), ex);
                }
            }
        });
        toolsMenu.add(item);

        item = new JMenuItem("Exit service mode");
        item.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                try {
                    DatabaseInstanceManager service = (DatabaseInstanceManager) client.current.getService("DatabaseInstanceManager", "1.0");
                    service.exitServiceMode();
                } catch (XMLDBException ex) {
                    showErrorMessage(ex.getMessage(), ex);
                }
            }
        });
        toolsMenu.add(item);

        toolsMenu.addSeparator();
        
        item = new JMenuItem(Messages.getString("ClientFrame.63"), KeyEvent.VK_B); //$NON-NLS-1$
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                backupAction(e);
            }
        });
        toolsMenu.add(item);
        
        item = new JMenuItem(Messages.getString("ClientFrame.64"), KeyEvent.VK_R); //$NON-NLS-1$
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                restoreAction(e);
            }
        });
        toolsMenu.add(item);
        
        JMenu connectMenu = new JMenu(Messages.getString("ClientFrame.65")); //$NON-NLS-1$
        connectMenu.setMnemonic(KeyEvent.VK_D);
        menubar.add(connectMenu);
        
        item = new JMenuItem(Messages.getString("ClientFrame.66"), KeyEvent.VK_S); //$NON-NLS-1$
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                display(Messages.getString("ClientFrame.67")); //$NON-NLS-1$
                process.setAction("shutdown"); //$NON-NLS-1$
            }
        });
        connectMenu.add(item);
        
        // Show LoginPanel to Reconnect
        item = new JMenuItem(Messages.getString("ClientFrame.69"), KeyEvent.VK_U); //$NON-NLS-1$
        item.setToolTipText(Messages.getString("ClientFrame.70")); //$NON-NLS-1$
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // load properties modified by the login panel
                Properties loginData=getLoginData(properties);
                if (loginData == null) return;
                Properties oldProps=properties;
                properties.putAll(loginData);
                statusbar.setText(Messages.getString("ClientFrame.71") + properties.getProperty("user") + "@" + properties.getProperty("uri")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                try {
                    client.shutdown(false);
                    client.connect();
                    client.reloadCollection();
                } catch (Exception u) {
                    showErrorMessage(Messages.getString("ClientFrame.75") + properties.getProperty("uri") + Messages.getString("ClientFrame.77"), u); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    properties=oldProps;
                    try { 
                        client.connect();
                    }
                    catch (Exception uu) {
                        showErrorMessage(Messages.getString("ClientFrame.78") + properties.getProperty("uri") , u); //$NON-NLS-1$ //$NON-NLS-2$
                    }
                }
            }
        });
        connectMenu.add(item);

        
        JMenu optionsMenu = new JMenu(Messages.getString("ClientFrame.80")); //$NON-NLS-1$
        optionsMenu.setMnemonic(KeyEvent.VK_O);
        menubar.add(optionsMenu);
        
        JCheckBoxMenuItem check = new JCheckBoxMenuItem(Messages.getString("ClientFrame.81"), properties //$NON-NLS-1$
                .getProperty(OutputKeys.INDENT).equals("yes")); //$NON-NLS-1$
        check.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                properties.setProperty(OutputKeys.INDENT,
                        ((JCheckBoxMenuItem) e.getSource()).isSelected()
                        ? "yes" //$NON-NLS-1$
                        : "no"); //$NON-NLS-1$
                try {
                    client.getResources();
                } catch (XMLDBException e1) {
                }
            }
        });
        optionsMenu.add(check);
        
        check = new JCheckBoxMenuItem(Messages.getString("ClientFrame.85"), properties //$NON-NLS-1$
                .getProperty(EXistOutputKeys.EXPAND_XINCLUDES).equals("yes")); //$NON-NLS-1$
        check.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                properties.setProperty(EXistOutputKeys.EXPAND_XINCLUDES,
                        ((JCheckBoxMenuItem) e.getSource()).isSelected()
                        ? "yes" //$NON-NLS-1$
                        : "no"); //$NON-NLS-1$
                try {
                    client.getResources();
                } catch (XMLDBException e1) {
                }
            }
        });
        optionsMenu.add(check);
        
        
        JMenu HelpMenu = new JMenu(Messages.getString("ClientFrame.89")); //$NON-NLS-1$
        HelpMenu.setMnemonic(KeyEvent.VK_H);
        menubar.add(HelpMenu);
        
        item = new JMenuItem(Messages.getString("ClientFrame.90"), KeyEvent.VK_A); //$NON-NLS-1$
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                AboutAction();
            }
        });
        HelpMenu.add(item);
        
        return menubar;
    }
    
    public void setPath(XmldbURI currentPath) {
        path = currentPath;
    }
    
    protected void displayPrompt() {
    	String pathString = path.getCollectionPath();
        try {
            commandStart = doc.getLength();
            doc.insertString(commandStart, Messages.getString("ClientFrame.91"), promptAttrs); //$NON-NLS-1$
            commandStart += 6;
            doc.insertString(commandStart, pathString + '>', promptAttrs);
            commandStart += pathString.length() + 1;
            doc.insertString(commandStart++, Messages.getString("ClientFrame.92"), defaultAttrs); //$NON-NLS-1$
            shell.setCaretPosition(commandStart);
        } catch (BadLocationException e) {
        }
    }
    
    protected void display(String message) {
        try {
            commandStart = doc.getLength();
            if (commandStart > MAX_DISPLAY_LENGTH) {
                doc.remove(0, MAX_DISPLAY_LENGTH);
                commandStart = doc.getLength();
            }
            doc.insertString(commandStart, message, defaultAttrs);
            commandStart = doc.getLength();
            shell.setCaretPosition(commandStart);
            
        } catch (BadLocationException e) {
        }
    }
    
    protected void setResources(List rows) {
        resources.setData(rows);
    }
    
    protected void setStatus(String message) {
        statusbar.setText(message);
    }
    
    protected void setEditable(boolean enabled) {
        shell.setEditable(enabled);
        shell.setVisible(enabled);
    }
    
    /** @see java.awt.event.KeyListener#keyPressed(java.awt.event.KeyEvent) */
    public void keyPressed(KeyEvent e) {
        type(e);
        gotUp = false;
    }
    
    /** @see java.awt.event.KeyListener#keyReleased(java.awt.event.KeyEvent) */
    public void keyReleased(KeyEvent e) {
        gotUp = true;
        type(e);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see java.awt.event.KeyListener#keyTyped(java.awt.event.KeyEvent)
     */
    public void keyTyped(KeyEvent e) {
        type(e);
    }
    
    private synchronized void type(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_ENTER :
                if (e.getID() == KeyEvent.KEY_PRESSED && gotUp) {
                    enter();
                }
                e.consume();
                break;
            case KeyEvent.VK_HOME :
                shell.setCaretPosition(commandStart);
                e.consume();
                break;
            case KeyEvent.VK_LEFT :
            case KeyEvent.VK_DELETE :
            case KeyEvent.VK_BACK_SPACE :
                if (shell.getCaretPosition() <= commandStart)
                    e.consume();
                break;
            case KeyEvent.VK_UP :
                if (e.getID() == KeyEvent.KEY_PRESSED)
                    historyBack();
                e.consume();
                break;
            case KeyEvent.VK_DOWN :
                if (e.getID() == KeyEvent.KEY_PRESSED)
                    historyForward();
                e.consume();
                break;
            default :
                if ((e.getModifiers() & (InputEvent.CTRL_MASK
                        | InputEvent.META_MASK | InputEvent.ALT_MASK)) == 0) {
                    if (shell.getCaretPosition() < commandStart)
                        shell.setCaretPosition(doc.getLength());
                }
                if (e.paramString().indexOf(Messages.getString("ClientFrame.93")) > Constants.STRING_NOT_FOUND) { //$NON-NLS-1$
                    if (shell.getCaretPosition() <= commandStart)
                        e.consume();
                }
                break;
        }
    }
    
    /** @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent) */
    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        if (cmd.equals(CUT))
            shell.cut();
        else if (cmd.equals(COPY))
            shell.copy();
        else if (cmd.equals(PASTE))
            shell.paste();
    }
    
    private void goUpAction(ActionEvent ev) {
        display(Messages.getString("ClientFrame.94")); //$NON-NLS-1$
        process.setAction("cd .."); //$NON-NLS-1$
    }
    
    private void newCollectionAction(ActionEvent ev) {
        String newCol = JOptionPane.showInputDialog(this,
                Messages.getString("ClientFrame.96")); //$NON-NLS-1$
        if (newCol != null) {
            String command = "mkcol \"" + newCol + '"'; //$NON-NLS-1$
            display(command + "\n"); //$NON-NLS-1$
            process.setAction(command);
        }
    }
    
    private void newServerURIAction(String newURI) {
        if (newURI == null)
            newURI = JOptionPane.showInputDialog(this,
                    Messages.getString("ClientFrame.99") //$NON-NLS-1$
                    + Messages.getString("ClientFrame.100")); //$NON-NLS-1$
        if (newURI != null) {
            properties.setProperty("uri", newURI); //$NON-NLS-1$
            try {
                client.shutdown(false);
                client.connect();
            } catch (Exception e) {
                showErrorMessage(Messages.getString("ClientFrame.102") + newURI + Messages.getString("ClientFrame.103"), e); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
    }
    
    /**
     * Returns an array of user-selected resources.
     */
    private ResourceDescriptor[] getSelectedResources() {
        final int[] selectedRows = fileman.getSelectedRows();
        final ResourceDescriptor[] res = new ResourceDescriptor[selectedRows.length];
        
        for (int i = 0; i < selectedRows.length; i++) {
            res[i] = resources.getRow(selectedRows[i]);
        }
        
        return res;
    }
    
    private void removeAction(ActionEvent ev) {
        
        final ResourceDescriptor[] res = getSelectedResources();
        
        // String cmd;
        if (JOptionPane.showConfirmDialog(this,
                Messages.getString("ClientFrame.104") + Messages.getString("ClientFrame.105"), //$NON-NLS-1$ //$NON-NLS-2$
                Messages.getString("ClientFrame.106"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) { //$NON-NLS-1$
            Runnable removeTask = new Runnable() {
                public void run() {
                    ProgressMonitor monitor = new ProgressMonitor(ClientFrame.this,
                            Messages.getString("ClientFrame.107"), Messages.getString("ClientFrame.108"), 1, res.length); //$NON-NLS-1$ //$NON-NLS-2$
                    monitor.setMillisToDecideToPopup(500);
                    monitor.setMillisToPopup(500);
                    for (int i = 0; i < res.length; i++) {
                        ResourceDescriptor resource = res[i];
                        if (resource.isCollection()) {
                            try {
                                CollectionManagementServiceImpl mgtService = (CollectionManagementServiceImpl) client.current
                                        .getService(
                                        "CollectionManagementService", //$NON-NLS-1$
                                        "1.0"); //$NON-NLS-1$
                                mgtService
                                        .removeCollection(resource.getName());
                            } catch (XMLDBException e) {
                                showErrorMessage(e.getMessage(), e);
                            }
                        } else {
                            try {
                                Resource res = client.current
                                        .getResource(resource.getName().toString());
                                client.current.removeResource(res);
                            } catch (XMLDBException e) {
                                showErrorMessage(e.getMessage(), e);
                            }
                        }
                        monitor.setProgress(i + 1);
                        if (monitor.isCanceled())
                            return;
                    }
                    try {
                        client.getResources();
                    } catch (XMLDBException e) {
                        showErrorMessage(e.getMessage(), e);
                    }
                }
            };
            new Thread(removeTask).start();
        }
    }
    
    private void moveAction(ActionEvent ev) {
        final ResourceDescriptor[] res = getSelectedResources();
        
    	PrettyXmldbURI[] collections = null;
        
    	//get an array of collection paths
        try
		{    
        	Collection root = client.getCollection(DBBroker.ROOT_COLLECTION);
            ArrayList alCollections = getCollections(root, new ArrayList());
            collections = new PrettyXmldbURI[alCollections.size()];
            alCollections.toArray(collections);
        } 
        catch (XMLDBException e)
		{
            showErrorMessage(e.getMessage(), e);
            return;
        }
        
        //prompt the user for a destination collection from the list
        Object val = JOptionPane.showInputDialog(this, Messages.getString("ClientFrame.111"), Messages.getString("ClientFrame.112"), JOptionPane.QUESTION_MESSAGE, null, collections, collections[0]); //$NON-NLS-1$ //$NON-NLS-2$
        if(val == null)
            return;
	    
        final XmldbURI destinationPath = ((PrettyXmldbURI)val).getTargetURI();
        Runnable moveTask = new Runnable() {
            public void run() {
                try {
                    CollectionManagementServiceImpl service = (CollectionManagementServiceImpl)
                    client.current.getService("CollectionManagementService", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
                    for(int i = 0; i < res.length; i++) {
                        setStatus(Messages.getString("ClientFrame.115") + res[i].getName() + Messages.getString("ClientFrame.116") + destinationPath + Messages.getString("ClientFrame.117")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        if(res[i].isCollection())
                            service.move(res[i].getName(), destinationPath, null);
                        else
                            service.moveResource(res[i].getName(), destinationPath, null);
                    }
                    client.reloadCollection();
                } catch (XMLDBException e) {
                    showErrorMessage(e.getMessage(), e);
                }
                setStatus(Messages.getString("ClientFrame.118")); //$NON-NLS-1$
            }
        };
        new Thread(moveTask).start();
    }
    
    private void renameAction(ActionEvent ev) {
        final ResourceDescriptor[] res = getSelectedResources();
        
        Object val = JOptionPane.showInputDialog(this, Messages.getString("ClientFrame.119"), Messages.getString("ClientFrame.120"), JOptionPane.QUESTION_MESSAGE); //$NON-NLS-1$ //$NON-NLS-2$
		
        if(val == null)
            return;
        XmldbURI parseIt;
        try {
        	parseIt = URIUtils.encodeXmldbUriFor((String)val);
        } catch (URISyntaxException e) {
        	showErrorMessage(Messages.getString("ClientFrame.121")+e.getMessage(),e); //$NON-NLS-1$
        	return;
        }
        final XmldbURI destinationFilename = parseIt;
        Runnable renameTask = new Runnable() {
            public void run() {
                try {
                    CollectionManagementServiceImpl service = (CollectionManagementServiceImpl)
                    client.current.getService("CollectionManagementService", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
                    for(int i = 0; i < res.length; i++) {
                        setStatus(Messages.getString("ClientFrame.124") + res[i].getName() + Messages.getString("ClientFrame.125") + destinationFilename + Messages.getString("ClientFrame.126")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        if(res[i].isCollection())
                            service.move(res[i].getName(), null, destinationFilename);
                        else
                            service.moveResource(res[i].getName(), null, destinationFilename);
                    }
                    client.reloadCollection();
                } catch (XMLDBException e) {
                    showErrorMessage(e.getMessage(), e);
                }
                setStatus(Messages.getString("ClientFrame.127")); //$NON-NLS-1$
            }
        };
        new Thread(renameTask).start();
    }
    
    private void copyAction(ActionEvent ev) {
        
    	final ResourceDescriptor[] res = getSelectedResources();
    	PrettyXmldbURI[] collections = null;
        
    	//get an array of collection paths
        try
		{    
        	Collection root = client.getCollection(DBBroker.ROOT_COLLECTION);
            ArrayList alCollections = getCollections(root, new ArrayList());
            collections = new PrettyXmldbURI[alCollections.size()];
            alCollections.toArray(collections);
        } 
        catch (XMLDBException e)
		{
            showErrorMessage(e.getMessage(), e);
            return;
        }
        
        //prompt the user for a destination collection from the list
        Object val = JOptionPane.showInputDialog(this, Messages.getString("ClientFrame.128"), Messages.getString("ClientFrame.129"), JOptionPane.QUESTION_MESSAGE, null, collections, collections[0]); //$NON-NLS-1$ //$NON-NLS-2$
        if(val == null)
            return;
	    
        final XmldbURI destinationPath = ((PrettyXmldbURI)val).getTargetURI();
               
        Runnable moveTask = new Runnable() {
            public void run() {
                try {
                    CollectionManagementServiceImpl service = (CollectionManagementServiceImpl)
                    client.current.getService("CollectionManagementService", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
                    for(int i = 0; i < res.length; i++) {
                        
                    	//TODO
                    	//what happens if the source and destination paths are the same?
                        //we need to check and prompt the user to either skip or choose a new name
                        //this function can copy multiple resources/collections selected by the user,
                    	//so may need to prompt the user multiple times? is in this thread the correct
                    	//place to do it? also need to do something similar for moveAction()
                    	//
                    	//Its too late and brain hurts - deliriumsky
                    	
                        setStatus(Messages.getString("ClientFrame.132") + res[i].getName() + Messages.getString("ClientFrame.133") + destinationPath + Messages.getString("ClientFrame.134")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        if(res[i].isCollection())
                            service.copy(res[i].getName(), destinationPath, null);
                        else
                            service.copyResource(res[i].getName(), destinationPath, null);
                    }
                    client.reloadCollection();
                } catch (XMLDBException e) {
                    showErrorMessage(e.getMessage(), e);
                }
                setStatus(Messages.getString("ClientFrame.135")); //$NON-NLS-1$
            }
        };
        new Thread(moveTask).start();
    }
    
    private ArrayList getCollections(Collection root, ArrayList collectionsList) throws XMLDBException
    {
        collectionsList.add(new PrettyXmldbURI(XmldbURI.create(root.getName())));
        String[] childCollections= root.listChildCollections();
        Collection child;
        for(int i = 0; i < childCollections.length; i++)
        {
            child = root.getChildCollection(childCollections[i]);
            getCollections(child, collectionsList);
        }
        return collectionsList;
    }
    
    private void reindexAction(ActionEvent ev) {
        final int[] selRows = fileman.getSelectedRows();
        ResourceDescriptor[] res;
        if(selRows.length == 0) {
            res = new ResourceDescriptor[1];
            res[0] = new ResourceDescriptor.Collection(client.path);
        } else {
            res = new ResourceDescriptor[selRows.length];
            for (int i = 0; i < selRows.length; i++) {
                res[i] = resources.getRow(selRows[i]);
                if(!(res[i].isCollection())) {
                    JOptionPane.showMessageDialog(this, Messages.getString("ClientFrame.136"), Messages.getString("ClientFrame.137"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$ //$NON-NLS-2$
                    return;
                }
            }
        }
        
        if (JOptionPane.showConfirmDialog(this,
                Messages.getString("ClientFrame.138"), //$NON-NLS-1$
                Messages.getString("ClientFrame.139"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) { //$NON-NLS-1$
            final ResourceDescriptor collections[] = res;
            Runnable reindexThread = new Runnable() {
                public void run() {
                    ClientFrame.this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    IndexQueryService service;
                    try {
                        service = (IndexQueryService)
                        client.current.getService("IndexQueryService", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
                        for(int i = 0; i < collections.length; i++) {
                            ResourceDescriptor next = collections[i];
                            setStatus(Messages.getString("ClientFrame.142") + next.getName() + Messages.getString("ClientFrame.143")); //$NON-NLS-1$ //$NON-NLS-2$
                            service.reindexCollection(next.getName());
                        }
                        setStatus(Messages.getString("ClientFrame.144")); //$NON-NLS-1$
                    } catch (XMLDBException e) {
                        showErrorMessage(e.getMessage(), e);
                    }
                    ClientFrame.this.setCursor(Cursor.getDefaultCursor());
                }
            };
            new Thread(reindexThread).start();
        }
    }
    
    private void uploadAction(ActionEvent ev) {
        // TODO store last file choose in properties
        JFileChooser chooser = new JFileChooser(preferences.get("directory.last", System.getProperty("user.dir")));
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.addChoosableFileFilter(new BinaryFileFilter());
        chooser.addChoosableFileFilter(new XMLFileFilter());
        if (chooser.showDialog(this, Messages.getString("ClientFrame.146")) == JFileChooser.APPROVE_OPTION) { //$NON-NLS-1$
            // remember directory in preferences
            preferences.put("directory.last", chooser.getCurrentDirectory().getAbsolutePath());

            final File[] files = chooser.getSelectedFiles();
            if (files.length > 0) {
                new Thread() {
                    public void run() {
                        UploadDialog upload = new UploadDialog();
                        try {
                            client.parse(files, upload);
                            client.getResources();
                        } catch (XMLDBException e) {
                            showErrorMessage(Messages.getString("ClientFrame.147") //$NON-NLS-1$
                                    + e.getMessage(), e);
                        }
                    }
                }.start();
            }
        }
    }
    
    private boolean deleteDirectory(File target) {
        if (target.isDirectory()) {
            String[] children = target.list();
            for (int i=0; i<children.length; i++) {
                boolean success = deleteDirectory(new File(target, children[i]));
                if (!success) {
                    return false;
                }
            }
        }
    
        // The directory is now empty so delete it
        return target.delete();
    }
    
    private void backupAction(ActionEvent ev) {
        CreateBackupDialog dialog = new CreateBackupDialog(
                properties.getProperty("uri", "xmldb:exist://"),  
                properties.getProperty("user", "admin"),  
                properties.getProperty("password", null), 
                new File(preferences.get("directory.backup", System.getProperty("user.home")))); 
        if (JOptionPane.showOptionDialog(this, dialog, Messages.getString("ClientFrame.157"), 
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, null, null) == JOptionPane.YES_OPTION) {
            
            String collection = dialog.getCollection();
            String backuptarget = dialog.getBackupTarget();
            
            // DWES add check here?
            File target=new File(backuptarget);
            if(target.exists()){
                if (JOptionPane.showConfirmDialog( this,
                        Messages.getString("CreateBackupDialog.6a") + " "
                        + backuptarget + " "
                        + Messages.getString("CreateBackupDialog.6b"),
                        Messages.getString("CreateBackupDialog.6c"),
                        JOptionPane.YES_NO_OPTION)
                        == JOptionPane.NO_OPTION)
                {
                    deleteDirectory(target);
                }
                    
            }
        
            
            try {
            Backup backup = new Backup(
                    properties.getProperty("user", "admin"), 
                    properties.getProperty("password", null), backuptarget, 
                    XmldbURI.xmldbUriFor(properties.getProperty("uri", "xmldb:exist://") 
                    + collection));
                backup.backup(true, this);
            } catch (XMLDBException e) {
                showErrorMessage("XMLDBException: " + e.getMessage(), e); //$NON-NLS-1$
            } catch (IOException e) {
                showErrorMessage("IOException: " + e.getMessage(), e); //$NON-NLS-1$
            } catch (SAXException e) {
                showErrorMessage("SAXException: " + e.getMessage(), e); //$NON-NLS-1$
            } catch (URISyntaxException e) {
                showErrorMessage("URISyntaxException: " + e.getMessage(), e); //$NON-NLS-1$
			}
        }
    }
    

    

    
    private void restoreAction(ActionEvent ev) {
        JFileChooser chooser = new JFileChooser(preferences.get("directory.backup", System.getProperty("user.dir")));
        chooser.setMultiSelectionEnabled(false);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.addChoosableFileFilter(new BackupContentsFilter());
        // re-enable later
        chooser.addChoosableFileFilter(new ZipFilter());
        //chooser.setSelectedFile(new File("eXist-backup.zip"));

        if (chooser.showDialog(null, Messages.getString("ClientFrame.169")) == JFileChooser.APPROVE_OPTION) { //$NON-NLS-1$
            File f = chooser.getSelectedFile();
            preferences.put("directory.backup", chooser.getCurrentDirectory().getAbsolutePath());
        	JPanel askPass = new JPanel(new BorderLayout());
        	askPass.add(new JLabel(Messages.getString("ClientFrame.170")), BorderLayout.NORTH); //$NON-NLS-1$
        	JPasswordField passInput = new JPasswordField(25);
        	askPass.add(passInput, BorderLayout.CENTER);
        	if (JOptionPane.showOptionDialog(this, askPass, Messages.getString("ClientFrame.171"), //$NON-NLS-1$
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, null, null) == JOptionPane.YES_OPTION) {
        		String newDbaPass = passInput.getPassword().length == 0 ? null : new String(passInput.getPassword());
	            String restoreFile = f.getAbsolutePath();
	            try {
	                Restore restore = new Restore(properties.getProperty("user", //$NON-NLS-1$
	                        "admin"), properties.getProperty("password", null), //$NON-NLS-1$ //$NON-NLS-2$
	                        newDbaPass,
	                        new File(restoreFile), properties.getProperty("uri", //$NON-NLS-1$
	                        "xmldb:exist://")); //$NON-NLS-1$
	                restore.restore(true, this);
	                
	                if (properties.getProperty("user", "admin").equals("admin") && newDbaPass != null) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		            	properties.setProperty("password", newDbaPass); //$NON-NLS-1$
		            client.reloadCollection();
	            } catch (Exception e) {
	            	showErrorMessage(Messages.getString("ClientFrame.181") + e.getMessage(), e); //$NON-NLS-1$
	            }
        	}
        }
    }
    
    private void editUsersAction(ActionEvent ev) {
        try {
            Collection collection = client.getCollection();
            UserManagementService service = (UserManagementService) collection
                    .getService("UserManagementService", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
            UserDialog dialog = new UserDialog(service, Messages.getString("ClientFrame.184"), client); //$NON-NLS-1$
            dialog.setVisible(true);
        } catch (XMLDBException e) {
            showErrorMessage(Messages.getString("ClientFrame.185"), e); //$NON-NLS-1$
            e.printStackTrace();
        }
    }
    
    private void exportAction(ActionEvent ev) {
        if (fileman.getSelectedRowCount() == 0)
            return;
        int row = fileman.getSelectedRow();
        ResourceDescriptor desc = resources.getRow(row);
        if (desc.isCollection())
            return;

        JFileChooser chooser = new JFileChooser(preferences.get("directory.last", System.getProperty("user.dir")));
        chooser.setMultiSelectionEnabled(false);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setSelectedFile(new File(desc.getName().toString()));
        if (chooser.showDialog(this, "Select file for export") == JFileChooser.APPROVE_OPTION) {
            preferences.put("directory.last", chooser.getCurrentDirectory().getAbsolutePath());
            File file = chooser.getSelectedFile();
            if (file.exists()
                    && JOptionPane.showConfirmDialog(this,
                    "File exists. Overwrite?", "Overwrite?",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.NO_OPTION)
                return;
            Resource resource;
            FileOutputStream os;
            BufferedWriter writer;
            SAXSerializer contentSerializer;
            try {
                Collection collection = client.getCollection();

                try {
                    resource = collection
                            .getResource(desc.getName().toString());
                    os = new FileOutputStream(file);
                    if (resource.getResourceType().equals("BinaryResource")) {
                        ((ExtendedResource) resource).getContentIntoAStream(os);
                    } else {

                        writer = new BufferedWriter(new OutputStreamWriter(os,
                                "UTF-8"));
                        // write resource to contentSerializer
                        contentSerializer = (SAXSerializer) SerializerPool
                                .getInstance()
                                .borrowObject(SAXSerializer.class);
                        contentSerializer.setOutput(writer, properties);
                        ((EXistResource) resource)
                                .setLexicalHandler(contentSerializer);
                        ((XMLResource) resource)
                                .getContentAsSAX(contentSerializer);
                        SerializerPool.getInstance().returnObject(
                                contentSerializer);
                        writer.close();
                    }
                } catch (Exception e) {
                    System.err
                            .println("An exception occurred while writing the resource: "
                                    + e.getMessage());
                    e.printStackTrace();

                }
            } catch (Exception e) {
                System.err.println("An exception occurred" + e.getMessage());
                e.printStackTrace();
            }

        }
    }
    
    private void editIndexesAction(ActionEvent ev) {
            IndexDialog dialog = new IndexDialog(Messages.getString("ClientFrame.186"), client); //$NON-NLS-1$
            dialog.setVisible(true);
    }
    
    private void editTriggersAction(ActionEvent ev) {
        TriggersDialog dialog = new TriggersDialog("Edit Triggers", client);
        dialog.setVisible(true);
    }
    
    private void editPolicies() {
        Collection systemCollection;
        try {
            systemCollection = client.getCollection(DBBroker.SYSTEM_COLLECTION);
        } catch (XMLDBException e) {
            showErrorMessage(Messages.getString("ClientFrame.187"), e); //$NON-NLS-1$
            return;
        }
        try {
        	DatabaseInstanceManager dim = (DatabaseInstanceManager)systemCollection.getService("DatabaseInstanceManager", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
        	if(!dim.isXACMLEnabled()) {
        		showErrorMessage(Messages.getString("ClientFrame.190"), null); //$NON-NLS-1$
        		return;
        	}
        } catch (XMLDBException e) {
            showErrorMessage(Messages.getString("ClientFrame.191"), e); //$NON-NLS-1$
        	return;
        }
        
        XACMLEditor editor = new XACMLEditor(systemCollection);
        editor.show();
    }
    
    private void findAction(ActionEvent ev) {
        Collection collection = client.getCollection();
        QueryDialog dialog = new QueryDialog(client, collection, properties);
        dialog.setVisible(true);
    }
    
    private void setPermAction(ActionEvent ev) {
        if (fileman.getSelectedRowCount() == 0)
            return;
        try {
            Collection collection = client.getCollection();
            UserManagementService service = (UserManagementService) collection
                    .getService("UserManagementService", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
            Permission perm = null;
            XmldbURI name;
            Date created = new Date();
            Date modified = null;
            String mimeType = null;
            if (fileman.getSelectedRowCount() == 1) {
                int row = fileman.getSelectedRow();
                ResourceDescriptor desc = resources.getRow(row);
                name = desc.getName();
                
                if (desc.isCollection()) {
                    Collection coll = collection.getChildCollection(name.toString());
                    created = ((CollectionImpl) coll).getCreationTime();
                    perm = service.getPermissions(coll);
                } else {
                    Resource res = collection.getResource(name.toString());
                    created = ((EXistResource) res).getCreationTime();
                    modified = ((EXistResource) res).getLastModificationTime();
                    mimeType = ((EXistResource) res).getMimeType();
                    perm = service.getPermissions(res);
                }
            } else {
                name = XmldbURI.create(".."); //$NON-NLS-1$
                perm = PermissionFactory.getPermission("", "", Permission.DEFAULT_PERM); //$NON-NLS-1$ //$NON-NLS-2$
            }
            ResourcePropertyDialog dialog = new ResourcePropertyDialog(this,
                    service, name, perm, created, modified, mimeType);
            dialog.setVisible(true);
            if (dialog.getResult() == ResourcePropertyDialog.APPLY_OPTION) {
                int rows[] = fileman.getSelectedRows();
                for (int i = 0; i < rows.length; i++) {
                    ResourceDescriptor desc = resources.getRow(rows[i]);
                    if (desc.isCollection()) {
                        Collection coll = collection.getChildCollection(desc
                                .getName().toString());
                        service.setPermissions(coll, dialog.permissions);
                    } else {
                        Resource res = collection.getResource(desc.getName().toString());
                        service.setPermissions(res, dialog.permissions);
                    }
                }
                client.reloadCollection();
            }
        } catch (XMLDBException e) {
            showErrorMessage(Messages.getString("ClientFrame.197") + e.getMessage(), e); //$NON-NLS-1$
            e.printStackTrace();
        }
    }
    
    private void enter() {
        int end = doc.getLength();
        if (end - commandStart == 0)
            return;
        try {
            String command = doc.getText(commandStart, end - commandStart);
            commandStart = end;
            doc.insertString(commandStart++, "\n", defaultAttrs); //$NON-NLS-1$
            if (command != null) {
                process.setAction(command);
                client.console.getHistory().addToHistory(command);
            }
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }
    
    private void historyBack() {
        client.console.getHistory().previous();
        String item = client.console.getHistory().current();
        if (item == null)
            return;
        try {
            if (shell.getCaretPosition() > commandStart)
                doc.remove(commandStart, doc.getLength() - commandStart);
            doc.insertString(commandStart, item, defaultAttrs);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }
    
    private void historyForward() {
        client.console.getHistory().next();
        String item = client.console.getHistory().current();
        try {
            if (shell.getCaretPosition() > commandStart)
                doc.remove(commandStart, doc.getLength() - commandStart);
            doc.insertString(commandStart, item, defaultAttrs);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void close() {
        setVisible(false);
        dispose();
        process.terminate();
        System.exit(0);
    }
    
    private void actionFinished() {
        if (!process.getStatus())
            close();
        displayPrompt();
    }
    
    private void AboutAction() {
        
        Properties sysProperties = InteractiveClient.getSystemProperties();
        
        // Original text eXist version 1.0, Copyright (C) 2001-2006 Wolfgang Meier
        JOptionPane.showMessageDialog(this, 
                sysProperties.getProperty("product-name") + " version " 
                + sysProperties.getProperty("product-version")
                + " (revision " + sysProperties.getProperty("svn-revision") + ") \n\n"
                + "Copyright (C) 2001-2008 Wolfgang Meier\n\n"
                + "eXist comes with ABSOLUTELY NO WARRANTY.\n"
                + "This is free software, and you are welcome to\n"
                + "redistribute it under certain conditions;\n"
                + "for details read the license file."  );
        return;
    }
    
    class TableMouseListener extends MouseAdapter {
        
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
                int row = fileman.getSelectedRow();
                final ResourceDescriptor resource = resources.getRow(row);
                if (resource.isCollection()) {
                    // cd into collection
                    String command = "cd " + '"'+URIUtils.urlDecodeUtf8(resource.getName())  +'"'; //$NON-NLS-1$
                    display(command + "\n"); //$NON-NLS-1$
                    process.setAction(command);
                } else {
                    // open a document for editing
                    ClientFrame.this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    try {
                        DocumentView view = new DocumentView(client, resource.getName(), properties);
                        view.setSize(new Dimension(640, 400));
                        view.viewDocument();
                    }
                    catch (XMLDBException ex) {
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
     * @author gpothier
     */
    private static class ResourceComparator implements Comparator {
        public int compare(Object aO1, Object aO2) {
            ResourceDescriptor desc1 = (ResourceDescriptor) aO1;
            ResourceDescriptor desc2 = (ResourceDescriptor) aO2;
            
            if (desc1.isCollection() != desc2.isCollection()) {
                return desc1.isCollection() ? Constants.INFERIOR : Constants.SUPERIOR;
            } else return desc1.getName().compareTo(desc2.getName());
        }
    }
    
    class ProcessThread extends Thread {
        
        private String action = null;
        private boolean terminate = false;
        private boolean status = false;
        
        public ProcessThread() {
            super();
        }
        
        synchronized public void setAction(String action) {
            while (this.action != null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
            this.action = action;
            notify();
        }
        
        synchronized public void terminate() {
            terminate = true;
            notify();
        }
        
        synchronized public boolean getStatus() {
            return status;
        }
        
        public boolean isReady() {
            return action == null;
        }
        
        /*
         * (non-Javadoc)
         *
         * @see java.lang.Runnable#run()
         */
        public void run() {
            while (!terminate) {
                while (action == null)
                    try {
                        synchronized (this) {
                            wait();
                        }
                    } catch (InterruptedException e) {
                    }
                status = client.process(action);
                synchronized (this) {
                    action = null;
                    actionFinished();
                    notify();
                }
            }
        }
        
    }
    
    class ResourceTableModel extends AbstractTableModel {
        
		private static final long serialVersionUID = 1L;
		private final String[] columnNames = new String[]{
                  Messages.getString("ClientFrame.207")  //$NON-NLS-1$
                , Messages.getString("ClientFrame.208") //$NON-NLS-1$
                , Messages.getString("ClientFrame.209") //$NON-NLS-1$
                , Messages.getString("ClientFrame.210")   //$NON-NLS-1$
                , Messages.getString("ClientFrame.211") //$NON-NLS-1$
                };
                
                private List rows = null;
                
                public void setData(List rows) {
                    Collections.sort(rows, new ResourceComparator());
                    this.rows = rows;
                    fireTableDataChanged();
                }
                
                public ResourceDescriptor getRow(int index) {
                    return (ResourceDescriptor) rows.get(index);
                }
                
        /*
         * (non-Javadoc)
         *
         * @see javax.swing.table.TableModel#getColumnCount()
         */
                public int getColumnCount() {
                    return columnNames.length;
                }
                
        /*
         * (non-Javadoc)
         *
         * @see javax.swing.table.TableModel#getColumnName(int)
         */
                public String getColumnName(int column) {
                    return columnNames[column];
                }
                
        /*
         * (non-Javadoc)
         *
         * @see javax.swing.table.TableModel#getRowCount()
         */
                public int getRowCount() {
                    return rows == null ? 0 : rows.size();
                }
                
        /*
         * (non-Javadoc)
         *
         * @see javax.swing.table.TableModel#getValueAt(int, int)
         */
                public Object getValueAt(int rowIndex, int columnIndex) {
                    ResourceDescriptor row = getRow(rowIndex);
                    
                    switch (columnIndex) {
                        case 0: return row.getName();
                        case 1: return row.getDate();
                        case 2: return row.getOwner();
                        case 3: return row.getGroup();
                        case 4: return row.getPermissions();
                        default: throw new RuntimeException(Messages.getString("ClientFrame.212")); //$NON-NLS-1$
                    }
                }
    }
    
    /**
     * @param   properties pass properties to the login panel
     * @return  the modiefied properties
     */
    
    protected static Properties getLoginData(Properties properties) {
        
        Properties sysProperties = InteractiveClient.getSystemProperties();

        LoginPanel login = new LoginPanel(properties);
        if (JOptionPane.showOptionDialog(null, login, 
                sysProperties.getProperty("product-name") + " " 
                + sysProperties.getProperty("product-version") + " Database Login" , 
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, null, null) == JOptionPane.OK_OPTION) {
            return login.getProperties();
        }
        return null;
    }
    
    public static void showErrorMessage(String message, Throwable t) {
        JScrollPane scroll = null;
        JTextArea msgArea = new JTextArea(message);
        msgArea.setBorder(BorderFactory.createTitledBorder(Messages.getString("ClientFrame.214"))); //$NON-NLS-1$
        msgArea.setEditable(false);
        msgArea.setBackground(null);
        if (t != null) {
            StringWriter out = new StringWriter();
            PrintWriter writer = new PrintWriter(out);
            t.printStackTrace(writer);
            JTextArea stacktrace = new JTextArea(out.toString(), 20, 50);
            stacktrace.setBackground(null);
            stacktrace.setEditable(false);
            scroll = new JScrollPane(stacktrace);
            scroll.setPreferredSize(new Dimension(250, 300));
            scroll.setBorder(BorderFactory
                    .createTitledBorder(Messages.getString("ClientFrame.215"))); //$NON-NLS-1$
        }
        JOptionPane optionPane = new JOptionPane();
        optionPane.setMessage(new Object[]{msgArea, scroll});
        optionPane.setMessageType(JOptionPane.ERROR_MESSAGE);
        JDialog dialog = optionPane.createDialog(null, Messages.getString("ClientFrame.216")); //$NON-NLS-1$
        dialog.setResizable(true);
        dialog.pack();
        dialog.setVisible(true);
        return;
    }
    
    public static int showErrorMessageQuery(String message, Throwable t) {
        JScrollPane scroll = null;
        JTextArea msgArea = new JTextArea(message);
        msgArea.setBorder(BorderFactory.createTitledBorder(Messages.getString("ClientFrame.217"))); //$NON-NLS-1$
        msgArea.setEditable(false);
        msgArea.setBackground(null);
        if (t != null) {
            StringWriter out = new StringWriter();
            PrintWriter writer = new PrintWriter(out);
            t.printStackTrace(writer);
            JTextArea stacktrace = new JTextArea(out.toString(), 20, 50);
            stacktrace.setBackground(null);
            stacktrace.setEditable(false);
            scroll = new JScrollPane(stacktrace);
            scroll.setPreferredSize(new Dimension(250, 300));
            scroll.setBorder(BorderFactory
                    .createTitledBorder(Messages.getString("ClientFrame.218"))); //$NON-NLS-1$
        }
        JOptionPane optionPane = new JOptionPane();
        
        optionPane.setMessage(new Object[]{msgArea, scroll});
        optionPane.setMessageType(JOptionPane.ERROR_MESSAGE);
        optionPane.setOptionType(optionPane.OK_CANCEL_OPTION);
        JDialog dialog = optionPane.createDialog(null, Messages.getString("ClientFrame.219")); //$NON-NLS-1$
        dialog.setResizable(true);
        dialog.pack();
        dialog.setVisible(true);
       
        Object result = optionPane.getValue();
        if (result == null) return 2;
        return  ((Integer)optionPane.getValue()).intValue();
    }
    /*
     * (non-Javadoc)
     *
     * @see java.awt.event.WindowFocusListener#windowGainedFocus(java.awt.event.WindowEvent)
     */
    public void windowGainedFocus(WindowEvent e) {
        toFront();
    }
    
    /*
     * (non-Javadoc)
     *
     * @see java.awt.event.WindowFocusListener#windowLostFocus(java.awt.event.WindowEvent)
     */
    public void windowLostFocus(WindowEvent e) {
    }
    
    /*
     * (non-Javadoc)
     *
     * @see java.awt.event.MouseListener#mouseClicked(java.awt.event.MouseEvent)
     */
    public void mouseClicked(MouseEvent e) {
    }
    
    /*
     * (non-Javadoc)
     *
     * @see java.awt.event.MouseListener#mouseEntered(java.awt.event.MouseEvent)
     */
    public void mouseEntered(MouseEvent e) {
    }
    
    /*
     * (non-Javadoc)
     *
     * @see java.awt.event.MouseListener#mouseExited(java.awt.event.MouseEvent)
     */
    public void mouseExited(MouseEvent e) {
    }
    
    /*
     * (non-Javadoc)
     *
     * @see java.awt.event.MouseListener#mousePressed(java.awt.event.MouseEvent)
     */
    public void mousePressed(MouseEvent e) {
        if (e.isPopupTrigger()) {
            shellPopup.show((Component) e.getSource(), e.getX(), e.getY());
        }
    }
    
    /*
     * (non-Javadoc)
     *
     * @see java.awt.event.MouseListener#mouseReleased(java.awt.event.MouseEvent)
     */
    public void mouseReleased(MouseEvent e) {
        if (e.isPopupTrigger()) {
            shellPopup.show((Component) e.getSource(), e.getX(), e.getY());
        }
    }
    
    static class ResourceTableCellRenderer implements TableCellRenderer {
        
        public final static Color collectionBackground = new Color(225, 235, 224);
        public final static Color collectionForeground = Color.black;
        public final static Color highBackground = new Color(115, 130, 189);
        public final static Color highForeground = Color.white;
        public final static Color altBackground = new Color(235, 235, 235);
        
        public static final DefaultTableCellRenderer DEFAULT_RENDERER = new DefaultTableCellRenderer();
        
        /*
         * (non-Javadoc)
         *
         * @see javax.swing.table.TableCellRenderer#getTableCellRendererComponent(javax.swing.JTable,
         *           java.lang.Object, boolean, boolean, int, int)
         */
        public Component getTableCellRendererComponent(JTable table,
                Object value, boolean isSelected, boolean hasFocus, int row,
                int column) {
            if(value instanceof XmldbURI) {
            	value = new PrettyXmldbURI((XmldbURI)value);
            }
            Component renderer = DEFAULT_RENDERER
                    .getTableCellRendererComponent(table, value, isSelected,
                    hasFocus, row, column);
            ((JLabel) renderer).setOpaque(true);
            Color foreground, background;
            
            ResourceTableModel resources = (ResourceTableModel) table.getModel();
            if (isSelected) {
                foreground = highForeground;
                background = highBackground;
            } else if (resources.getRow(row).isCollection()) {
                foreground = collectionForeground;
                background = collectionBackground;
            } else if (row % 2 == 0) {
                background = altBackground;
                foreground = Color.black;
            } else {
                foreground = Color.black;
                background = Color.white;
            }

            renderer.setForeground(foreground);
            renderer.setBackground(background);
            return renderer;
        }
    }
    
    class BinaryFileFilter extends FileFilter {
        
        /* (non-Javadoc)
         * @see javax.swing.filechooser.FileFilter#getDescription()
         */
        public String getDescription() {
            return Messages.getString("ClientFrame.220"); //$NON-NLS-1$
        }
        
        /* (non-Javadoc)
         * @see javax.swing.filechooser.FileFilter#accept(java.io.File)
         */
        public boolean accept(File f) {
            if(f.isDirectory())
                return true;
            return !MimeTable.getInstance().isXMLContent(f.getName());
        }
    }
    
    class XMLFileFilter extends FileFilter {
        
        /* (non-Javadoc)
         * @see javax.swing.filechooser.FileFilter#getDescription()
         */
        public String getDescription() {
            return Messages.getString("ClientFrame.221"); //$NON-NLS-1$
        }
        
        /* (non-Javadoc)
         * @see javax.swing.filechooser.FileFilter#accept(java.io.File)
         */
        public boolean accept(File f) {
            if(f.isDirectory())
                return true;
            return MimeTable.getInstance().isXMLContent(f.getName());
        }
    }
    
    
}
