/*
 * ClientFrame.java - Mar 31, 2003
 * 
 * @author wolf
 */
package org.exist.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
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
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.border.BevelBorder;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Keymap;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.TextAction;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;

import org.exist.backup.Backup;
import org.exist.backup.CreateBackupDialog;
import org.exist.backup.Restore;
import org.exist.security.Permission;
import org.exist.xmldb.CollectionImpl;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XMLResourceImpl;
import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

public class ClientFrame extends JFrame {

	public final static int MAX_DISPLAY_LENGTH = 512000;
	public final static int MAX_HISTORY = 50;

	private final static SimpleAttributeSet promptAttrs = new SimpleAttributeSet();
	private final static SimpleAttributeSet defaultAttrs = new SimpleAttributeSet();

	{
		StyleConstants.setForeground(promptAttrs, Color.blue);
		StyleConstants.setBold(promptAttrs, true);
		StyleConstants.setForeground(defaultAttrs, Color.black);
	}

	private int lastPosition = 0;
	private int currentHistory = 0;
	private DefaultStyledDocument doc;
	private JLabel statusbar;
	private JTable fileman;
	private ResourceTableModel resources = new ResourceTableModel();
	private JTextPane shell;
	private JComboBox historyCombo;
	private InteractiveClient client;
	private String path = null;
	private ProcessThread process = new ProcessThread();
	private LinkedList history = new LinkedList();
	private TreeSet historyItems = new TreeSet();
	private Properties properties;
	private TransformerHandler transformer = null;

	/**
	 * @throws java.awt.HeadlessException
	 */
	public ClientFrame(InteractiveClient client, String path, Properties properties)
		throws HeadlessException {
		super("eXist Client Shell");
		this.path = path;
		this.properties = properties;
		this.client = client;
		//prepare();
		setupComponents();
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent ev) {
				close();
			}
		});
		pack();

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
		fileman.addMouseListener(new TableMouseListener(this));
		ResourceTableCellRenderer renderer = new ResourceTableCellRenderer();
		fileman.setDefaultRenderer(Object.class, renderer);
		JScrollPane scroll = new JScrollPane(fileman);
		scroll.setMinimumSize(new Dimension(300, 150));
		split.setLeftComponent(scroll);

		JPanel panel = new JPanel(false);
		panel.setLayout(new BorderLayout());

		// command-history selection
		Box hbox = Box.createHorizontalBox();
		JLabel histLabel = new JLabel("History:");
		hbox.add(histLabel);
		historyCombo = new JComboBox();
		historyCombo.addItemListener(new HistorySelectedAction());
		hbox.add(historyCombo);

		// shell window
		doc = new DefaultStyledDocument();
		shell = new JTextPane(doc);
		shell.setContentType("text/plain; charset=UTF-8");
		shell.setFont(new Font("Monospaced", Font.PLAIN, 12));
		scroll = new JScrollPane(shell);
		KeyStroke enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
		KeyStroke up = KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0);
		KeyStroke down = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0);
		Keymap map = shell.getKeymap();
		EnterAction enterAction = new EnterAction("enter");
		BackHistoryAction backAction = new BackHistoryAction("back");
		ForwardHistoryAction forwardAction = new ForwardHistoryAction("forward");
		map.addActionForKeyStroke(enter, enterAction);
		map.addActionForKeyStroke(up, backAction);
		map.addActionForKeyStroke(down, forwardAction);

		panel.add(hbox, BorderLayout.NORTH);
		panel.add(scroll, BorderLayout.CENTER);

		split.setRightComponent(panel);

		statusbar = new JLabel();
		statusbar.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));

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

		item = new JMenuItem("Edit properties");
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
		for (Enumeration enum = properties.propertyNames(); enum.hasMoreElements();) {
			next = (String) enum.nextElement();
			if (next.startsWith("alternate_uri_")) {
				final String uri = properties.getProperty(next);
				if(uri.equals(properties.getProperty("uri")))
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

		return menubar;
	}

	private void prepare() {
		try {
			URL url = getClass().getResource("xml2html.xsl");
			SAXTransformerFactory factory =
				(SAXTransformerFactory) SAXTransformerFactory.newInstance();
			transformer = factory.newTransformerHandler(new StreamSource(url.openStream()));
		} catch (TransformerConfigurationException e) {
			e.printStackTrace();
		} catch (TransformerFactoryConfigurationError e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void setPath(String currentPath) {
		path = currentPath;
	}

	protected void displayPrompt() {
		try {
			int pos = doc.getEndPosition().getOffset() - 1;
			doc.insertString(pos, "exist:", promptAttrs);
			pos += 6;
			doc.insertString(pos, path + '>', promptAttrs);
			pos += path.length() + 1;
			doc.insertString(pos++, " ", defaultAttrs);
			shell.setCaretPosition(pos);
			lastPosition = doc.getEndPosition().getOffset() - 1;
		} catch (BadLocationException e) {
		}
	}

	protected void display(String message) {
		try {
			int pos = doc.getLength();
			if (pos > MAX_DISPLAY_LENGTH) {
				doc.remove(0, MAX_DISPLAY_LENGTH);
				pos = doc.getLength();
			}
			doc.insertString(pos, message, defaultAttrs);
			shell.setCaretPosition(doc.getLength());
			lastPosition = doc.getLength();
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

	private void goUpAction(ActionEvent ev) {
		display("cd ..\n");
		process.setAction("cd ..");
	}

	private void newCollectionAction(ActionEvent ev) {
		String newCol = JOptionPane.showInputDialog(this, "Please enter name of new collection");
		if (newCol != null) {
			String command = "mkcol " + newCol;
			display(command + "\n");
			process.setAction(command);
		}
	}

	private void newServerURIAction(String newURI) {
		if (newURI == null)
			newURI =
				JOptionPane.showInputDialog(
					this,
					"Please enter a valid XML:DB base URI (without " + "collection path)");
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
		int[] rows = fileman.getSelectedRows();
		List v = new ArrayList(rows.length);
		String cmd;
		if (JOptionPane
			.showConfirmDialog(
				this,
				"Are you sure you want to remove the selected " + "resources?",
				"Confirm deletion",
				JOptionPane.YES_NO_OPTION)
			== JOptionPane.YES_OPTION) {
			for (int i = 0; i < rows.length; i++) {
				Object resource = (Object) resources.getValueAt(rows[i], 3);
				if (resource instanceof InteractiveClient.CollectionName)
					cmd = "rmcol " + resource;
				else
					cmd = "rm " + resource;
				v.add(cmd);
			}
		}
		for(int i = 0; i < v.size(); i++) {
			cmd = (String)v.get(i);
			display(cmd + "\n");
			process.setAction(cmd);
		}
	}

	private void uploadAction(ActionEvent ev) {
		JFileChooser chooser = new JFileChooser(System.getProperty("user.dir"));
		chooser.setMultiSelectionEnabled(true);
		chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		if (chooser.showDialog(this, "Select files or directories to store")
			== JFileChooser.APPROVE_OPTION) {
			final File[] files = chooser.getSelectedFiles();
			if (files.length > 0) {
				new Thread() {
					public void run() {
						UploadDialog upload = new UploadDialog();
						try {
							client.parse(files, upload);
							client.getResources();
						} catch (XMLDBException e) {
							showErrorMessage("XMLDBException: " + e.getMessage(), e);
						}
						upload.setVisible(false);
					}
				}
				.start();
			}
		}
	}

	private void backupAction(ActionEvent ev) {
		CreateBackupDialog dialog =
			new CreateBackupDialog(
				properties.getProperty("uri", "xmldb:exist://"),
				properties.getProperty("user", "admin"),
				properties.getProperty("password", null),
				properties.getProperty("backup-dir", "backup"));
		if (JOptionPane
			.showOptionDialog(
				this,
				dialog,
				"Create Backup",
				JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.QUESTION_MESSAGE,
				null,
				null,
				null)
			== JOptionPane.YES_OPTION) {
			String collection = dialog.getCollection();
			String dir = dialog.getBackupDir();
			Backup backup =
				new Backup(
					properties.getProperty("user", "admin"),
					properties.getProperty("password", null),
					dir,
					properties.getProperty("uri", "xmldb:exist://") + '/' + collection);
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

		if (chooser.showDialog(null, "Select backup file for restore")
			== JFileChooser.APPROVE_OPTION) {
			File f = chooser.getSelectedFile();
			String restoreFile = f.getAbsolutePath();
			try {
				Restore restore =
					new Restore(
						properties.getProperty("user", "admin"),
						properties.getProperty("password", null),
						new File(restoreFile),
						properties.getProperty("uri", "xmldb:exist://"));
				restore.restore(true, this);
				client.getResources();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void newUserAction(ActionEvent ev) {
		try {
			Collection collection = client.getCollection();
			UserManagementService service =
				(UserManagementService) collection.getService("UserManagementService", "1.0");
			UserDialog dialog = new UserDialog(service, "Edit Users", properties);
			dialog.show();
		} catch (XMLDBException e) {
			showErrorMessage("Failed to retrieve UserManagementService", e);
			e.printStackTrace();
		}
	}

	private void findAction(ActionEvent ev) {
		Collection collection = client.getCollection();
		QueryDialog dialog = new QueryDialog(collection, properties);
		dialog.show();
	}

	private void setPermAction(ActionEvent ev) {
		if (fileman.getSelectedRowCount() == 0)
			return;
		try {
			Collection collection = client.getCollection();
			UserManagementService service =
				(UserManagementService) collection.getService("UserManagementService", "1.0");
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
					created = ((XMLResourceImpl) res).getCreationTime();
					modified = ((XMLResourceImpl) res).getLastModificationTime();
					perm = service.getPermissions(res);
				}
			} else {
				name = "...";
				perm = new Permission("", "");
			}
			ResourcePropertyDialog dialog =
				new ResourcePropertyDialog(this, service, name, perm, created, modified);
			dialog.show();
			if (dialog.getResult() == ResourcePropertyDialog.APPLY_OPTION) {
				int rows[] = fileman.getSelectedRows();
				for (int i = 0; i < rows.length; i++) {
					Object obj = (Object) resources.getValueAt(rows[i], 3);
					if (obj instanceof InteractiveClient.CollectionName) {
						Collection coll = collection.getChildCollection(obj.toString());
						service.setPermissions(coll, dialog.permissions);
					} else {
						Resource res = collection.getResource((String) obj);
						service.setPermissions(res, dialog.permissions);
					}
				}
				client.getResources();
			}
		} catch (XMLDBException e) {
			showErrorMessage("XMLDB Exception: " + e.getMessage(), e);
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

	class EnterAction extends TextAction {

		public EnterAction(String name) {
			super(name);
		}

		public void actionPerformed(ActionEvent ev) {
			int end = doc.getEndPosition().getOffset();
			try {
				String command = doc.getText(lastPosition, end - lastPosition - 1);
				doc.insertString(doc.getEndPosition().getOffset() - 1, "\n", defaultAttrs);
				if (command != null) {
					process.setAction(command);
					if (!historyItems.contains(command)) {
						historyCombo.addItem(command);
						historyItems.add(command);
					}
					history.addLast(command);
					if (history.size() == MAX_HISTORY)
						history.removeFirst();
					currentHistory = history.size();
				}
			} catch (BadLocationException e) {
				e.printStackTrace();
			}
		}
	}

	class TableMouseListener extends MouseAdapter {

		JFrame parent;

		public TableMouseListener(JFrame parent) {
			super();
			this.parent = parent;
		}

		public void mouseClicked(MouseEvent e) {
			if (e.getClickCount() == 2) {
				int row = fileman.getSelectedRow();
				final Object resource = (Object) resources.getValueAt(row, 3);
				if (resource instanceof InteractiveClient.CollectionName) {
					String command = "cd " + resource;
					display(command + "\n");
					process.setAction(command);
				} else {
					final DocumentView view = new DocumentView(parent);
					view.setSize(new Dimension(400, 300));
					view.setVisible(true);
					try {
						//final StringWriter writer = new StringWriter();
						//transformer.setResult(new StreamResult(writer));
						final XMLResource res = client.retrieve(resource.toString(), "true");
						//res.getContentAsSAX(transformer);
						//view.setText(writer.toString());
						view.setText((String) res.getContent());
					} catch (IllegalArgumentException e1) {
						e1.printStackTrace();
					} catch (XMLDBException e1) {
						e1.printStackTrace();
					}
				}
			}
		}

	}

	class BackHistoryAction extends TextAction {

		public BackHistoryAction(String name) {
			super(name);
		}

		public void actionPerformed(ActionEvent e) {
			if (history.size() == 0 || currentHistory == 0)
				return;
			String item = (String) history.get(--currentHistory);
			try {
				if (doc.getEndPosition().getOffset() > lastPosition)
					doc.remove(lastPosition, doc.getEndPosition().getOffset() - lastPosition - 1);
				doc.insertString(lastPosition, item, defaultAttrs);
			} catch (BadLocationException e1) {
				e1.printStackTrace();
			}
		}

	}

	class ForwardHistoryAction extends TextAction {

		public ForwardHistoryAction(String name) {
			super(name);
		}

		public void actionPerformed(ActionEvent e) {
			if (history.size() == 0 || currentHistory == history.size() - 1)
				return;
			String item = (String) history.get(++currentHistory);
			try {
				if (doc.getEndPosition().getOffset() > lastPosition)
					doc.remove(lastPosition, doc.getEndPosition().getOffset() - lastPosition - 1);
				doc.insertString(lastPosition, item, defaultAttrs);
			} catch (BadLocationException e1) {
				e1.printStackTrace();
			}
		}

	}

	class HistorySelectedAction implements ItemListener {

		public void itemStateChanged(ItemEvent e) {
			String item = e.getItem().toString();
			try {
				if (doc.getEndPosition().getOffset() > lastPosition)
					doc.remove(lastPosition, doc.getEndPosition().getOffset() - lastPosition - 1);
				doc.insertString(lastPosition, item, defaultAttrs);
			} catch (BadLocationException e1) {
				e1.printStackTrace();
			}
			shell.requestFocus();
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

		/* (non-Javadoc)
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

		private final String[] columnNames =
			new String[] { "Resource", "Permissions", "Owner", "Group" };

		private Object[][] rows = null;

		public void setData(Object[][] rows) {
			this.rows = rows;
			fireTableDataChanged();
		}

		/* (non-Javadoc)
		* @see javax.swing.table.TableModel#getColumnCount()
		*/
		public int getColumnCount() {
			return columnNames.length;
		}

		/* (non-Javadoc)
		 * @see javax.swing.table.TableModel#getColumnName(int)
		 */
		public String getColumnName(int column) {
			return columnNames[column];
		}

		/* (non-Javadoc)
		 * @see javax.swing.table.TableModel#getRowCount()
		 */
		public int getRowCount() {
			return rows == null ? 0 : rows.length;
		}

		/* (non-Javadoc)
		 * @see javax.swing.table.TableModel#getValueAt(int, int)
		 */
		public Object getValueAt(int rowIndex, int columnIndex) {
			return rows[rowIndex][columnIndex];
		}
	}

	class DocumentView extends JDialog {

		JTextArea text;

		public DocumentView(JFrame owner) {
			super(owner, "View Document", false);
			text = new JTextArea();
			text.setText("Loading document...");
			JScrollPane scroll = new JScrollPane(text);
			getContentPane().add(scroll);
			pack();
		}

		public void setText(String content) {
			text.setText(content);
			text.setCaretPosition(0);
		}
	}

	protected static String[] getLoginData(String defaultUser) {
		LoginPanel login = new LoginPanel(defaultUser);
		if (JOptionPane
			.showOptionDialog(
				null,
				login,
				"eXist Database Login",
				JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.QUESTION_MESSAGE,
				null,
				null,
				null)
			== JOptionPane.OK_OPTION) {
			String[] ret = new String[2];
			ret[0] = login.getUsername();
			ret[1] = login.getPassword();
			return ret;
		}
		return null;
	}

	public static void showErrorMessage(String message, Throwable t) {
		ErrorPanel panel = new ErrorPanel(message, t);
		JOptionPane.showOptionDialog(
			null,
			panel,
			"Exception",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.ERROR_MESSAGE,
			null,
			null,
			null);
		return;
	}
}

class LoginPanel extends JPanel {

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

class ErrorPanel extends JPanel {

	JLabel text;
	JTextArea stacktrace;

	public ErrorPanel(String message, Throwable t) {
		super(false);
		setLayout(new BorderLayout(5, 10));
		text = new JLabel(message);
		add(text, BorderLayout.NORTH);

		StringWriter out = new StringWriter();
		PrintWriter writer = new PrintWriter(out);
		t.printStackTrace(writer);
		stacktrace = new JTextArea(out.toString(), 10, 50);
		stacktrace.setEditable(false);
		JScrollPane scroll = new JScrollPane(stacktrace);
		scroll.setPreferredSize(new Dimension(200, 100));
		add(scroll, BorderLayout.CENTER);
	}
}

class ResourceTableCellRenderer implements TableCellRenderer {

	public final static Color collectionBackground = new Color(225, 235, 224);
	public final static Color collectionForeground = Color.black;
	public final static Color highBackground = new Color(115, 130, 189);
	public final static Color highForeground = Color.white;
	public final static Color altBackground = new Color(235, 235, 235);

	public static final DefaultTableCellRenderer DEFAULT_RENDERER = new DefaultTableCellRenderer();

	/* (non-Javadoc)
	 * @see javax.swing.table.TableCellRenderer#getTableCellRendererComponent(javax.swing.JTable, java.lang.Object, boolean, boolean, int, int)
	 */
	public Component getTableCellRendererComponent(
		JTable table,
		Object value,
		boolean isSelected,
		boolean hasFocus,
		int row,
		int column) {
		Component renderer =
			DEFAULT_RENDERER.getTableCellRendererComponent(
				table,
				value,
				isSelected,
				hasFocus,
				row,
				column);
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