/*
 * UserDialog.java - Jun 16, 2003
 * 
 * @author wolf
 */
package org.exist.client;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URISyntaxException;
import java.net.URL;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.EtchedBorder;
import javax.swing.table.AbstractTableModel;

import org.exist.security.User;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;
import org.xmldb.api.base.XMLDBException;

class UserDialog extends JFrame {

	UserManagementService service;
	JTextField username;
	JPasswordField password1;
	JPasswordField password2;
	JTextField homedir;
	JList groups;
	JList allGroups;
	DefaultListModel groupsModel;
	DefaultListModel allGroupsModel;
	JTable users;
	UserTableModel userModel;
	InteractiveClient client;

	public UserDialog(UserManagementService service, String title, InteractiveClient client)
		throws XMLDBException {
		super(title);
		this.service = service;
		this.client = client;
		setupComponents();
	}

	private void setupComponents() throws XMLDBException {
		GridBagLayout grid = new GridBagLayout();
		getContentPane().setLayout(grid);
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(5, 5, 5, 5);

		userModel = new UserTableModel(service);
		users = new JTable(userModel);
		users.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		users.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 1)
					tableSelectAction(e);
			}
		});

		JScrollPane scroll = new JScrollPane(users);
		scroll.setPreferredSize(new Dimension(250, 150));
		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.CENTER;
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1;
		c.weighty = 0.5;
		grid.setConstraints(scroll, c);
		getContentPane().add(scroll);

		JComponent toolbar = getToolbar();
		c.gridx = 0;
		c.gridy = 1;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.CENTER;
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1;
		c.weighty = 0;
		grid.setConstraints(toolbar, c);
		getContentPane().add(toolbar);

		JLabel label = new JLabel("Username");
		c.gridx = 0;
		c.gridy = 2;
		c.gridwidth = 1;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		c.weighty = 0;
		grid.setConstraints(label, c);
		getContentPane().add(label);

		username = new JTextField(15);
		c.gridx = 1;
		c.gridy = 2;
		c.anchor = GridBagConstraints.EAST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		c.weighty = 0;
		grid.setConstraints(username, c);
		getContentPane().add(username);

		label = new JLabel("Password");
		c.gridx = 0;
		c.gridy = 3;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		c.weighty = 0;
		grid.setConstraints(label, c);
		getContentPane().add(label);

		password1 = new JPasswordField(15);
		c.gridx = 1;
		c.gridy = 3;
		c.anchor = GridBagConstraints.EAST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		c.weighty = 0;
		grid.setConstraints(password1, c);
		getContentPane().add(password1);

		label = new JLabel("Password (repeat)");
		c.gridx = 0;
		c.gridy = 4;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		c.weighty = 0;
		grid.setConstraints(label, c);
		getContentPane().add(label);

		password2 = new JPasswordField(15);
		c.gridx = 1;
		c.gridy = 4;
		c.anchor = GridBagConstraints.EAST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		c.weighty = 0;
		grid.setConstraints(password2, c);
		getContentPane().add(password2);

		label = new JLabel("Home-Collection");
		c.gridx = 0;
		c.gridy = 5;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		c.weighty = 0;
		grid.setConstraints(label, c);
		getContentPane().add(label);

		homedir = new JTextField(20);
		c.gridx = 1;
		c.gridy = 5;
		c.anchor = GridBagConstraints.EAST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		c.weighty = 0;
		grid.setConstraints(homedir, c);
		getContentPane().add(homedir);

		JPanel groupsPanel = getGroupsPanel();
		c.gridx = 0;
		c.gridy = 6;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.CENTER;
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1;
		c.weighty = 1;
		grid.setConstraints(groupsPanel, c);
		getContentPane().add(groupsPanel);

		pack();
	}

	private JComponent getToolbar() {
		Box box = Box.createHorizontalBox();
		JButton button = new JButton("Create User");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionCreateUser();
			}
		});
		
		box.add(button);
		button = new JButton("Modify User");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionModify();
			}
		});
		box.add(button);
		
		button = new JButton("Remove");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionRemove();
			}
		});
		box.add(button);
				
		button = new JButton("Reset");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionClear();
			}
		});
		box.add(button);
		return box;
	}

	private JPanel getGroupsPanel() throws XMLDBException {
		JPanel panel = new JPanel(false);
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		panel.setBorder(
			BorderFactory.createTitledBorder(
				BorderFactory.createEtchedBorder(EtchedBorder.RAISED),
				"Groups"));

		groupsModel = new DefaultListModel();
		groups = new JList(groupsModel);
		JScrollPane scroll = new JScrollPane(groups);
		scroll.setPreferredSize(new Dimension(150, 150));
		scroll.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createEtchedBorder(EtchedBorder.RAISED),
			"Assigned"));
		panel.add(scroll);

		Box box = Box.createVerticalBox();

		URL url = getClass().getResource("icons/Back16.gif");
		JButton button = new JButton(new ImageIcon(url));
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionAssignGroup();
			}
		});

		button.setToolTipText("Assign group");
		box.add(button);

		url = getClass().getResource("icons/Forward16.gif");
		button = new JButton(new ImageIcon(url));
		button.setToolTipText("Remove group");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionRemoveGroup();
			}
		});
		box.add(button);

		url = getClass().getResource("icons/New16.gif");
		button = new JButton(new ImageIcon(url));
		button.setToolTipText("Create new group");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionNewGroup();
			}
		});
		box.add(button);

		panel.add(box);

		allGroupsModel = new DefaultListModel();
		updateGroups();
		allGroups = new JList(allGroupsModel);
		scroll = new JScrollPane(allGroups);
		scroll.setPreferredSize(new Dimension(150, 150));
		scroll.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createEtchedBorder(EtchedBorder.RAISED),
			"All"));
		panel.add(scroll);

		return panel;
	}

	private void updateGroups() throws XMLDBException {
		String[] gl = service.getGroups();
		for (int i = 0; i < gl.length; i++) {
			allGroupsModel.addElement(gl[i]);
		}
	}

	private void actionAssignGroup() {
		Object[] selected = allGroups.getSelectedValues();
		for (int i = 0; i < selected.length; i++) {
			if (!groupsModel.contains(selected[i]))
				groupsModel.addElement(selected[i]);
		}
	}

	private void actionRemoveGroup() {
		int[] selected = groups.getSelectedIndices();
		for (int i = 0; i < selected.length; i++)
			groupsModel.remove(selected[i]);
	}

	private void actionNewGroup() {
		String newGroup =
			JOptionPane.showInputDialog(
				this,
				"Please enter a name for the new group",
				"New Group",
				JOptionPane.QUESTION_MESSAGE);
		groupsModel.addElement(newGroup);
		allGroupsModel.addElement(newGroup);
	}

	private void actionCreateUser() {
		String name = username.getText();
		if (name.length() == 0)
			return;
		User user = new User(name);
		String pass1 = new String(password1.getPassword());
		String pass2 = new String(password2.getPassword());
		if (!pass1.equals(pass2)) {
			JOptionPane.showMessageDialog(this, "Different passwords. Please check.");
			return;
		}
		user.setPassword(pass1);
		try {
			user.setHome(XmldbURI.xmldbUriFor(homedir.getText()));
		} catch (URISyntaxException e) {
			JOptionPane.showMessageDialog(this, e.getMessage());
			return;
		}
		if(groupsModel.size() == 0) {
			JOptionPane.showMessageDialog(this, "Please assign a group to the new user");
			return;
		}
		for (int i = 0; i < groupsModel.size(); i++)
			user.addGroup((String) groupsModel.elementAt(i));
		try {
			service.addUser(user);
			client.reloadCollection();
			userModel.reload();
		} catch (XMLDBException e) {
			JOptionPane.showMessageDialog(this, e.getMessage());
		}
	}

	private void actionModify() {
		String name = username.getText();
		if (name.length() == 0)
			return;
		User user = new User(name);
		String pass1 = new String(password1.getPassword());
		String pass2 = new String(password2.getPassword());
		if(pass1.length() == 0 &&
			JOptionPane.showConfirmDialog(this, "No password set. Are you sure?",
				"Password missing", JOptionPane.YES_NO_OPTION)
			== JOptionPane.NO_OPTION)
			return;
		if (!pass1.equals(pass2)) {
			JOptionPane.showMessageDialog(this, "Different passwords. Please check.");
			return;
		}
		
		user.setPassword(pass1);
		try {
			user.setHome(XmldbURI.xmldbUriFor(homedir.getText()));
		} catch (URISyntaxException e) {
			JOptionPane.showMessageDialog(this, e.getMessage());
			return;
		}
		for (int i = 0; i < groupsModel.size(); i++)
			user.addGroup((String) groupsModel.elementAt(i));
		try {
			service.updateUser(user);
			String myUser = client.properties.getProperty("user", "admin");
			if(name.equals(myUser)) {
				client.properties.setProperty("password", pass1);
				client.reloadCollection();
			}
			userModel.reload();
		} catch (XMLDBException e) {
			JOptionPane.showMessageDialog(this, e.getMessage());
		}
	}
	
	private void actionRemove() {
		int[] selected = users.getSelectedRows();
		if(selected.length == 0)
			return;
		if(JOptionPane.showConfirmDialog(this, "Are you sure you want to remove the selected users?",
			"Remove users", JOptionPane.YES_NO_OPTION) == JOptionPane.NO_OPTION)
			return;
		for(int i = 0; i < selected.length; i++) {
			User user = userModel.users[selected[i]];
			try {
				service.removeUser(user);
				client.reloadCollection();
				userModel.reload();
			} catch (XMLDBException e) {
				JOptionPane.showMessageDialog(this, e.getMessage());
			}
		}
	}
	private void actionClear() {
		groupsModel.clear();
		username.setText("");
		password1.setText("");
		password2.setText("");
		homedir.setText("");
	}

	private void tableSelectAction(MouseEvent ev) {
		int row = users.rowAtPoint(ev.getPoint());
		User user = userModel.users[row];
		username.setText(user.getName());
		groupsModel.clear();
		password1.setText("");
		password2.setText("");
		if(user.getHome()!=null) {
			homedir.setText(user.getHome().toString());
		} else {
			homedir.setText("");
		}
		String[] groups = user.getGroups();
		for (int i = 0; i < groups.length; i++) {
			groupsModel.addElement(groups[i]);
		}
	}

	class UserTableModel extends AbstractTableModel {

		private final String[] columnNames = new String[] { "UID", "User", "Groups", "Home" };

		private User users[] = null;

		public UserTableModel(UserManagementService service) throws XMLDBException {
			super();
			reload();
		}

		public void reload() throws XMLDBException {
			users = service.getUsers();
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
			return users == null ? 0 : users.length;
		}

		/* (non-Javadoc)
		 * @see javax.swing.table.TableModel#getValueAt(int, int)
		 */
		public Object getValueAt(int rowIndex, int columnIndex) {
			switch (columnIndex) {
				case 0 :
					return new Integer(users[rowIndex].getUID());
				case 1 :
					return users[rowIndex].getName();
				case 2 :
					StringBuilder buf = new StringBuilder();
					String[] groups = users[rowIndex].getGroups();
					for (int i = 0; i < groups.length; i++) {
						buf.append(groups[i]);
						if (i + 1 < groups.length)
							buf.append(';');
					}
					return buf.toString();
				case 3 :
					return users[rowIndex].getHome();
				default :
					return null;
			}
		}
	}
}
