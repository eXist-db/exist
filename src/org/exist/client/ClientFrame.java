/*
 * eXist Open Source Native XML Database
 *   
 * Copyright (C) 2001-03 Wolfgang M. Meier wolfgang@exist-db.org
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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
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
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
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
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
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
import org.exist.security.Permission;
import org.exist.security.User;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.xmldb.CollectionImpl;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.UserManagementService;
import org.gnu.readline.Readline;
import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

public class ClientFrame extends JFrame
		implements
			WindowFocusListener,
			KeyListener,
			ActionListener,
			MouseListener {

	public final static String CUT = "Cut";
	public final static String COPY = "Copy";
	public final static String PASTE = "Paste";

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
	private int currentHistory = 0;
	private boolean gotUp = false;
	private DefaultStyledDocument doc;
	private JLabel statusbar;
	private JTable fileman;
	private ResourceTableModel resources = new ResourceTableModel();
	private JTextPane shell;
	private JPopupMenu shellPopup;
	private InteractiveClient client;
	private String path = null;
	private ProcessThread process = new ProcessThread();
	private Properties properties;

	/**
	 * @throws java.awt.HeadlessException
	 */
	public ClientFrame(InteractiveClient client, String path,
			Properties properties) throws HeadlessException {
		super("eXist Admin Client");
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

		currentHistory = Readline.getHistorySize();
		process.start();
		shell.requestFocus();
	}

	private void setupComponents() {
		setJMenuBar(createMenuBar());

		JToolBar toolbar = new JToolBar();
		URL url = getClass().getResource("icons/Up24.gif");
		JButton button = new JButton(new ImageIcon(url));
		button.setToolTipText("Go to parent collection");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				goUpAction(e);
			}
		});
		toolbar.add(button);

		url = getClass().getResource("icons/Refresh24.gif");
		button = new JButton(new ImageIcon(url));
		button.setToolTipText("Refresh collection view");
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

		url = getClass().getResource("icons/New24.gif");
		button = new JButton(new ImageIcon(url));
		button.setToolTipText("Create new collection");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				newCollectionAction(e);
			}
		});
		toolbar.add(button);

		url = getClass().getResource("icons/Add24.gif");
		button = new JButton(new ImageIcon(url));
		button.setToolTipText("Stores one or more files to the database");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				uploadAction(e);
			}
		});
		toolbar.add(button);

		url = getClass().getResource("icons/Delete24.gif");
		button = new JButton(new ImageIcon(url));
		button.setToolTipText("Delete selected files or collections");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				removeAction(e);
			}
		});
		toolbar.add(button);

		url = getClass().getResource("icons/Preferences24.gif");
		button = new JButton(new ImageIcon(url));
		button.setToolTipText("Edit owners/permissions for selected resource");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setPermAction(e);
			}
		});
		toolbar.add(button);

		toolbar.addSeparator();
		url = getClass().getResource("icons/Export24.gif");
		button = new JButton(new ImageIcon(url));
		button.setToolTipText("Create backup");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				backupAction(e);
			}
		});
		toolbar.add(button);

		url = getClass().getResource("icons/Import24.gif");
		button = new JButton(new ImageIcon(url));
		button.setToolTipText("Restore files from backup");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				restoreAction(e);
			}
		});
		toolbar.add(button);

		toolbar.addSeparator();
		url = getClass().getResource("icons/keyring-small.png");
		button = new JButton(new ImageIcon(url));
		button.setToolTipText("Manage users");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				newUserAction(e);
			}
		});
		toolbar.add(button);

		url = getClass().getResource("icons/Find24.gif");
		button = new JButton(new ImageIcon(url));
		button.setToolTipText("Query the database with XPath");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				findAction(e);
			}
		});
		toolbar.add(button);

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

		shellPopup = new JPopupMenu("Console Menu");
		shellPopup.add(new JMenuItem(CUT)).addActionListener(this);
		shellPopup.add(new JMenuItem(COPY)).addActionListener(this);
		shellPopup.add(new JMenuItem(PASTE)).addActionListener(this);

		// shell window
		doc = new DefaultStyledDocument();
		shell = new JTextPane(doc);
		shell.setContentType("text/plain; charset=UTF-8");
		shell.setFont(new Font("Monospaced", Font.PLAIN, 12));
		shell.setMargin(new Insets(7, 5, 7, 5));
		shell.addKeyListener(this);
		shell.addMouseListener(this);

		scroll = new JScrollPane(shell);

		split.setRightComponent(scroll);

		statusbar = new JLabel();
		statusbar.setBorder(BorderFactory
				.createBevelBorder(BevelBorder.LOWERED));

		getContentPane().add(split, BorderLayout.CENTER);
		getContentPane().add(toolbar, BorderLayout.NORTH);
		getContentPane().add(statusbar, BorderLayout.SOUTH);
	}

	private JMenuBar createMenuBar() {
		JMenuBar menubar = new JMenuBar();

		JMenu fileMenu = new JMenu("File");
		fileMenu.setMnemonic(KeyEvent.VK_F);
		menubar.add(fileMenu);

		JMenuItem item = new JMenuItem("Store files/directories", KeyEvent.VK_S);
		item.setAccelerator(KeyStroke.getKeyStroke("control S"));
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				uploadAction(e);
			}
		});
		fileMenu.add(item);

		item = new JMenuItem("Create collection", KeyEvent.VK_C);
		item.setAccelerator(KeyStroke.getKeyStroke("control N"));
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				newCollectionAction(e);
			}
		});
		fileMenu.add(item);

		item = new JMenuItem("Remove");
		item.setAccelerator(KeyStroke.getKeyStroke("control D"));
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				removeAction(e);
			}
		});
		fileMenu.add(item);

		item = new JMenuItem("Resource properties");
		item.setAccelerator(KeyStroke.getKeyStroke("control P"));
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setPermAction(e);
			}
		});
		fileMenu.add(item);

		item = new JMenuItem("Quit", KeyEvent.VK_Q);
		item.setAccelerator(KeyStroke.getKeyStroke("control Q"));
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				close();
			}
		});
		fileMenu.add(item);

		JMenu toolsMenu = new JMenu("Tools");
		toolsMenu.setMnemonic(KeyEvent.VK_T);
		menubar.add(toolsMenu);

		item = new JMenuItem("Find", KeyEvent.VK_F);
		item.setAccelerator(KeyStroke.getKeyStroke("control F"));
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				findAction(e);
			}
		});
		toolsMenu.add(item);

		toolsMenu.addSeparator();

		item = new JMenuItem("Edit users", KeyEvent.VK_F);
		item.setAccelerator(KeyStroke.getKeyStroke("control U"));
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				newUserAction(e);
			}
		});
		toolsMenu.add(item);

		toolsMenu.addSeparator();

		item = new JMenuItem("Backup", KeyEvent.VK_B);
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				backupAction(e);
			}
		});
		toolsMenu.add(item);

		item = new JMenuItem("Restore", KeyEvent.VK_R);
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				restoreAction(e);
			}
		});
		toolsMenu.add(item);

		JMenu dbMenu = new JMenu("Database");
		dbMenu.setMnemonic(KeyEvent.VK_D);
		menubar.add(dbMenu);

		item = new JMenuItem("Shutdown", KeyEvent.VK_S);
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				display("shutdown\n");
				process.setAction("shutdown");
			}
		});
		dbMenu.add(item);

		dbMenu.addSeparator();

		ButtonGroup group = new ButtonGroup();

		item = new JRadioButtonMenuItem(properties.getProperty("uri"), true);
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				newServerURIAction(properties.getProperty("uri"));
			}
		});
		dbMenu.add(item);
		group.add(item);

		String next;
		for (Enumeration enum = properties.propertyNames(); enum
				.hasMoreElements(); ) {
			next = (String) enum.nextElement();
			if (next.startsWith("alternate_uri_")) {
				final String uri = properties.getProperty(next);
				if (uri.equals(properties.getProperty("uri")))
					continue;
				item = new JRadioButtonMenuItem(uri, false);
				item.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						newServerURIAction(uri);
					}
				});
				dbMenu.add(item);
				group.add(item);
			}
		}

		dbMenu.addSeparator();
		item = new JMenuItem("Connect to alternate URI", KeyEvent.VK_C);
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				newServerURIAction(null);
			}
		});
		dbMenu.add(item);

		JMenu optionsMenu = new JMenu("Options");
		optionsMenu.setMnemonic(KeyEvent.VK_O);
		menubar.add(optionsMenu);

		JCheckBoxMenuItem check = new JCheckBoxMenuItem("Indent", properties
				.getProperty(OutputKeys.INDENT).equals("yes"));
		check.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				properties.setProperty(OutputKeys.INDENT,
						((JCheckBoxMenuItem) e.getSource()).isSelected()
								? "yes"
								: "no");
				try {
					client.getResources();
				} catch (XMLDBException e1) {
				}
			}
		});
		optionsMenu.add(check);

		check = new JCheckBoxMenuItem("Expand-XIncludes", properties
				.getProperty(EXistOutputKeys.EXPAND_XINCLUDES).equals("yes"));
		check.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				properties.setProperty(EXistOutputKeys.EXPAND_XINCLUDES,
						((JCheckBoxMenuItem) e.getSource()).isSelected()
								? "yes"
								: "no");
				try {
					client.getResources();
				} catch (XMLDBException e1) {
				}
			}
		});
		optionsMenu.add(check);

		optionsMenu.addSeparator();
		item = new JMenuItem("Change User-Identity", KeyEvent.VK_U);
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String[] loginData = getLoginData(properties
						.getProperty("user"));
				if (loginData == null)
					return;
				properties.setProperty("user", loginData[0]);
				properties.setProperty("password", loginData[1]);
				try {
					client.reloadCollection();
				} catch (XMLDBException e1) {
					showErrorMessage("Login failed!", e1);
				}
			}
		});
		optionsMenu.add(item);
		return menubar;
	}

	public void setPath(String currentPath) {
		path = currentPath;
	}

	protected void displayPrompt() {
		try {
			commandStart = doc.getLength();
			doc.insertString(commandStart, "exist:", promptAttrs);
			commandStart += 6;
			doc.insertString(commandStart, path + '>', promptAttrs);
			commandStart += path.length() + 1;
			doc.insertString(commandStart++, " ", defaultAttrs);
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

	protected void setResources(Object[][] rows) {
		resources.setData(rows);
	}

	protected void setStatus(String message) {
		statusbar.setText(message);
	}

	protected void setEditable(boolean enabled) {
		shell.setEditable(enabled);
		shell.setVisible(enabled);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.KeyListener#keyPressed(java.awt.event.KeyEvent)
	 */
	public void keyPressed(KeyEvent e) {
		type(e);
		gotUp = false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.KeyListener#keyReleased(java.awt.event.KeyEvent)
	 */
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
				if (e.paramString().indexOf("Backspace") > -1) {
					if (shell.getCaretPosition() <= commandStart)
						e.consume();
				}
				break;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */
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
		display("cd ..\n");
		process.setAction("cd ..");
	}

	private void newCollectionAction(ActionEvent ev) {
		String newCol = JOptionPane.showInputDialog(this,
				"Please enter name of new collection");
		if (newCol != null) {
			String command = "mkcol \"" + newCol + '"';
			display(command + "\n");
			process.setAction(command);
		}
	}

	private void newServerURIAction(String newURI) {
		if (newURI == null)
			newURI = JOptionPane.showInputDialog(this,
					"Please enter a valid XML:DB base URI (without "
							+ "collection path)");
		if (newURI != null) {
			properties.setProperty("uri", newURI);
			try {
				client.shutdown(false);
				client.connect();
			} catch (Exception e) {
				showErrorMessage("Connection to " + newURI + " failed!", e);
			}
		}
	}

	private void removeAction(ActionEvent ev) {
		final int[] rows = fileman.getSelectedRows();
		final Object[] res = new Object[rows.length];
		for (int i = 0; i < rows.length; i++) {
			res[i] = resources.getValueAt(rows[i], 3);
		}
		String cmd;
		if (JOptionPane.showConfirmDialog(this,
				"Are you sure you want to remove the selected " + "resources?",
				"Confirm deletion", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
			final JFrame parent = this;
			Runnable removeTask = new Runnable() {
				public void run() {
					ProgressMonitor monitor = new ProgressMonitor(parent,
							"Remove Progress", "", 1, rows.length);
					monitor.setMillisToDecideToPopup(500);
					monitor.setMillisToPopup(500);
					for (int i = 0; i < rows.length; i++) {
						Object resource = res[i];
						if (resource instanceof InteractiveClient.CollectionName) {
							try {
								CollectionManagementService mgtService = (CollectionManagementService) client.current
										.getService(
												"CollectionManagementService",
												"1.0");
								mgtService
										.removeCollection(resource.toString());
							} catch (XMLDBException e) {
								showErrorMessage(e.getMessage(), e);
							}
						} else {
							try {
								Resource res = client.current
										.getResource(resource.toString());
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

	private void uploadAction(ActionEvent ev) {
		String dir = properties.getProperty("working-dir", System
				.getProperty("exist.home"));
		JFileChooser chooser = new JFileChooser(dir);
		chooser.setMultiSelectionEnabled(true);
		chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		chooser.addChoosableFileFilter(new BinaryFileFilter());
		chooser.addChoosableFileFilter(new XMLFileFilter());
		if (chooser.showDialog(this, "Select files or directories to store") == JFileChooser.APPROVE_OPTION) {
			final File[] files = chooser.getSelectedFiles();
			if (files.length > 0) {
				new Thread() {
					public void run() {
						UploadDialog upload = new UploadDialog();
						try {
							client.parse(files, upload);
							client.getResources();
						} catch (XMLDBException e) {
							showErrorMessage("XMLDBException: "
									+ e.getMessage(), e);
						}
						upload.setVisible(false);
					}
				}.start();
			}
			File selectedDir = chooser.getCurrentDirectory();
			properties
					.setProperty("working-dir", selectedDir.getAbsolutePath());
		}
	}

	private void backupAction(ActionEvent ev) {
		CreateBackupDialog dialog = new CreateBackupDialog(properties
				.getProperty("uri", "xmldb:exist://"), properties.getProperty(
				"user", "admin"), properties.getProperty("password", null),
				properties.getProperty("backup-dir", System
						.getProperty("user.home")
						+ File.separatorChar + "backup"));
		if (JOptionPane.showOptionDialog(this, dialog, "Create Backup",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
				null, null, null) == JOptionPane.YES_OPTION) {
			String collection = dialog.getCollection();
			String dir = dialog.getBackupDir();
			Backup backup = new Backup(properties.getProperty("user", "admin"),
					properties.getProperty("password", null), dir, properties
							.getProperty("uri", "xmldb:exist://")
							+ '/' + collection);
			try {
				backup.backup(true, this);
			} catch (XMLDBException e) {
				showErrorMessage("XMLDBException: " + e.getMessage(), e);
			} catch (IOException e) {
				showErrorMessage("IOException: " + e.getMessage(), e);
			} catch (SAXException e) {
				showErrorMessage("SAXException: " + e.getMessage(), e);
			}
		}
	}

	private void restoreAction(ActionEvent ev) {
		JFileChooser chooser = new JFileChooser();
		chooser.setMultiSelectionEnabled(false);
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setFileFilter(new FileFilter() {
			public boolean accept(File f) {
				if (f.isDirectory())
					return true;
				if (f.getName().equals("__contents__.xml"))
					return true;
				return false;
			}

			public String getDescription() {
				return "__contents__.xml files";
			}
		});

		if (chooser.showDialog(null, "Select backup file for restore") == JFileChooser.APPROVE_OPTION) {
			File f = chooser.getSelectedFile();
			String restoreFile = f.getAbsolutePath();
			try {
				Restore restore = new Restore(properties.getProperty("user",
						"admin"), properties.getProperty("password", null),
						new File(restoreFile), properties.getProperty("uri",
								"xmldb:exist://"));
				restore.restore(true, this);
				client.reloadCollection();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void newUserAction(ActionEvent ev) {
		try {
			Collection collection = client.getCollection();
			UserManagementService service = (UserManagementService) collection
					.getService("UserManagementService", "1.0");
			UserDialog dialog = new UserDialog(service, "Edit Users", client);
			dialog.show();
		} catch (XMLDBException e) {
			showErrorMessage("Failed to retrieve UserManagementService", e);
			e.printStackTrace();
		}
	}

	private void findAction(ActionEvent ev) {
		Collection collection = client.getCollection();
		QueryDialog dialog = new QueryDialog(client, collection, properties);
		dialog.show();
	}

	private void setPermAction(ActionEvent ev) {
		if (fileman.getSelectedRowCount() == 0)
			return;
		try {
			Collection collection = client.getCollection();
			UserManagementService service = (UserManagementService) collection
					.getService("UserManagementService", "1.0");
			Permission perm = null;
			String name;
			Date created = new Date();
			Date modified = null;
			if (fileman.getSelectedRowCount() == 1) {
				int row = fileman.getSelectedRow();
				Object obj = (Object) resources.getValueAt(row, 3);
				if (obj instanceof InteractiveClient.CollectionName) {
					name = obj.toString();
					Collection coll = collection.getChildCollection(name);
					created = ((CollectionImpl) coll).getCreationTime();
					perm = service.getPermissions(coll);
				} else {
					name = (String) obj;
					Resource res = collection.getResource(name);
					created = ((EXistResource) res).getCreationTime();
					modified = ((EXistResource) res).getLastModificationTime();
					perm = service.getPermissions(res);
				}
			} else {
				name = "...";
				perm = new Permission("", "");
			}
			ResourcePropertyDialog dialog = new ResourcePropertyDialog(this,
					service, name, perm, created, modified);
			dialog.show();
			if (dialog.getResult() == ResourcePropertyDialog.APPLY_OPTION) {
				int rows[] = fileman.getSelectedRows();
				for (int i = 0; i < rows.length; i++) {
					Object obj = (Object) resources.getValueAt(rows[i], 3);
					if (obj instanceof InteractiveClient.CollectionName) {
						Collection coll = collection.getChildCollection(obj
								.toString());
						service.setPermissions(coll, dialog.permissions);
					} else {
						Resource res = collection.getResource((String) obj);
						service.setPermissions(res, dialog.permissions);
					}
				}
				client.reloadCollection();
			}
		} catch (XMLDBException e) {
			showErrorMessage("XMLDB Exception: " + e.getMessage(), e);
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
			doc.insertString(commandStart++, "\n", defaultAttrs);
			if (command != null) {
				process.setAction(command);
				Readline.addToHistory(command);
				currentHistory = Readline.getHistorySize();
			}
		} catch (BadLocationException e) {
			e.printStackTrace();
		}
	}

	private void historyBack() {
		if (currentHistory == 0)
			return;
		String item = Readline.getHistoryLine(--currentHistory);
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
		if (currentHistory + 1 == Readline.getHistorySize())
			return;
		String item = Readline.getHistoryLine(++currentHistory);
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

	class TableMouseListener extends MouseAdapter {

		public void mouseClicked(MouseEvent e) {
			if (e.getClickCount() == 2) {
				int row = fileman.getSelectedRow();
				final Object resource = (Object) resources.getValueAt(row, 3);
				if (resource instanceof InteractiveClient.CollectionName) {
					// cd into collection
					String command = "cd \"" + resource + '"';
					display(command + "\n");
					process.setAction(command);
				} else {
					// open a document for editing
					ClientFrame.this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
					try {
						final Resource res = client.retrieve(resource
								.toString(), properties.getProperty(
								OutputKeys.INDENT, "yes"));
						DocumentView view = new DocumentView(client
								.getCollection(), res, properties);
						view.setSize(new Dimension(640, 400));
						if (res.getResourceType().equals("XMLResource"))
							view.setText((String) res.getContent());
						else
							view.setText(new String((byte[]) res.getContent()));
						
						// lock the resource for editing
						UserManagementService service = (UserManagementService)
							client.current.getService("UserManagementService", "1.0");
						User user = service.getUser(properties.getProperty("user"));
						String lockOwner = service.hasUserLock(res);
						if(lockOwner != null) {
							if(JOptionPane.showConfirmDialog(ClientFrame.this,
									"Resource is already locked by user " + lockOwner +
									". Should I try to relock it?",
									"Resource locked",
									JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
								view.dispose();
								ClientFrame.this.setCursor(Cursor.getDefaultCursor());
								return;
							}
						}
						
						try {
							service.lockResource(res, user);
						} catch(XMLDBException ex) {
							System.out.println(ex.getMessage());
							JOptionPane.showMessageDialog(ClientFrame.this,
								"Resource cannot be locked. Opening read-only.");
							view.setReadOnly();
						}
						view.setVisible(true);
					} catch (IllegalArgumentException ex) {
						showErrorMessage("Illegal argument: " + ex.getMessage(), ex);
					} catch (XMLDBException ex) {
						showErrorMessage("XMLDB error: " + ex.getMessage(), ex);
					}
					ClientFrame.this.setCursor(Cursor.getDefaultCursor());
				}
			}
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

		private final String[] columnNames = new String[]{"Permissions",
				"Owner", "Group", "Resource"};

		private Object[][] rows = null;

		public void setData(Object[][] rows) {
			this.rows = rows;
			fireTableDataChanged();
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
			return rows == null ? 0 : rows.length;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see javax.swing.table.TableModel#getValueAt(int, int)
		 */
		public Object getValueAt(int rowIndex, int columnIndex) {
			return rows[rowIndex][columnIndex];
		}
	}

	protected static String[] getLoginData(String defaultUser) {
		LoginPanel login = new LoginPanel(defaultUser);
		if (JOptionPane.showOptionDialog(null, login, "eXist Database Login",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
				null, null, null) == JOptionPane.OK_OPTION) {
			String[] ret = new String[2];
			ret[0] = login.getUsername();
			ret[1] = login.getPassword();
			return ret;
		}
		return null;
	}

	public static void showErrorMessage(String message, Throwable t) {
		JScrollPane scroll = null;
		JTextArea msgArea = new JTextArea(message);
		msgArea.setBorder(BorderFactory.createTitledBorder("Message:"));
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
					.createTitledBorder("Exception Stacktrace:"));
		}
		JOptionPane optionPane = new JOptionPane();
		optionPane.setMessage(new Object[]{msgArea, scroll});
		optionPane.setMessageType(JOptionPane.ERROR_MESSAGE);
		JDialog dialog = optionPane.createDialog(null, "Error");
		dialog.pack();
		dialog.show();
		return;
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

	static class LoginPanel extends JPanel {

		JTextField username;
		JPasswordField password;

		public LoginPanel(String defaultUser) {
			super(false);
			setupComponents(defaultUser);
		}

		private void setupComponents(String defaultUser) {
			GridBagLayout grid = new GridBagLayout();
			setLayout(grid);
			GridBagConstraints c = new GridBagConstraints();
			c.insets = new Insets(5, 5, 5, 5);

			JLabel label = new JLabel("Username");
			c.gridx = 0;
			c.gridy = 0;
			c.anchor = GridBagConstraints.WEST;
			c.fill = GridBagConstraints.NONE;
			grid.setConstraints(label, c);
			add(label);

			username = new JTextField(defaultUser, 12);
			c.gridx = 1;
			c.gridy = 0;
			c.anchor = GridBagConstraints.EAST;
			c.fill = GridBagConstraints.HORIZONTAL;
			grid.setConstraints(username, c);
			add(username);

			label = new JLabel("Password");
			c.gridx = 0;
			c.gridy = 1;
			c.anchor = GridBagConstraints.WEST;
			c.fill = GridBagConstraints.NONE;
			grid.setConstraints(label, c);
			add(label);

			password = new JPasswordField(12);
			c.gridx = 1;
			c.gridy = 1;
			c.anchor = GridBagConstraints.EAST;
			c.fill = GridBagConstraints.HORIZONTAL;
			grid.setConstraints(password, c);
			add(password);
		}

		public String getUsername() {
			return username.getText();
		}

		public String getPassword() {
			return new String(password.getPassword());
		}
	}

	static class ResourceTableCellRenderer implements TableCellRenderer {

		public final static Color collectionBackground = new Color(225, 235,
				224);
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
			Component renderer = DEFAULT_RENDERER
					.getTableCellRendererComponent(table, value, isSelected,
							hasFocus, row, column);
			((JLabel) renderer).setOpaque(true);
			Color foreground, background;
			if (isSelected) {
				foreground = highForeground;
				background = highBackground;
			} else if (table.getValueAt(row, 3) instanceof InteractiveClient.CollectionName) {
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
			return "Binary resources";
		}
		
		/* (non-Javadoc)
		 * @see javax.swing.filechooser.FileFilter#accept(java.io.File)
		 */
		public boolean accept(File f) {
			if(f.isDirectory())
				return true;
			String name = f.getName();
			int p = name.lastIndexOf('.');
			if(p < 0 || p + 1 == name.length())
				return false;
			name = name.substring(p + 1);
			return Arrays.binarySearch(client.binarySuffixes, name) > -1;
		}
	}
	
	class XMLFileFilter extends FileFilter {
		
		/* (non-Javadoc)
		 * @see javax.swing.filechooser.FileFilter#getDescription()
		 */
		public String getDescription() {
			return "XML files";
		}
		
		/* (non-Javadoc)
		 * @see javax.swing.filechooser.FileFilter#accept(java.io.File)
		 */
		public boolean accept(File f) {
			if(f.isDirectory())
				return true;
			String name = f.getName();
			int p = name.lastIndexOf('.');
			if(p < 0 || p + 1 == name.length())
				return false;
			name = name.substring(p + 1);
			return Arrays.binarySearch(client.xmlSuffixes, name) > -1;
		}
	}
}