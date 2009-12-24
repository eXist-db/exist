/*
 * ResourcePropertyDialog.java - Jun 17, 2003
 * 
 * @author wolf
 */
package org.exist.client;

import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.DateFormat;
import java.util.Date;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.border.EtchedBorder;

import org.exist.security.Permission;
import org.exist.security.User;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.util.URIUtils;
import org.xmldb.api.base.XMLDBException;

public class ResourcePropertyDialog extends JDialog {

    public final static int NO_OPTION = -1;
    public final static int APPLY_OPTION = 0;
	public final static int CANCEL_OPTION = 1;

	Permission permissions;
	XmldbURI resource;
	UserManagementService service;
	Date creationDate;
	Date modificationDate;
    String mimeType;
	JComboBox groups;
	JComboBox owners;
	JCheckBox[] worldPerms;
	JCheckBox[] groupPerms;
	JCheckBox[] userPerms;
	int result = NO_OPTION;

	/**
	 * @param owner
	 * @param mgt
	 * @param res
         * @param perm
         * @param created
         * @param modified
         * @param mimeType
	 * @throws java.awt.HeadlessException
	 */
	public ResourcePropertyDialog(
		Frame owner,
		UserManagementService mgt,
		XmldbURI res,
		Permission perm,
		Date created,
		Date modified,
        String mimeType)
		throws HeadlessException, XMLDBException {
		super(owner, "Edit Properties", true);
		this.service = mgt;
		this.permissions = perm;
		this.creationDate = created;
		this.modificationDate = modified;
		this.resource = res; 
        this.mimeType = mimeType == null ? "N/A" : mimeType;
		setupComponents();
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent ev) {
				cancelAction();
			}
		});
		pack();
	}

	public int getResult() {
		return result;
	}

	public Permission getPermissions() {
		return permissions;
	}

	private void setupComponents() throws XMLDBException {
		GridBagLayout grid = new GridBagLayout();
		getContentPane().setLayout(grid);
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(5, 5, 5, 5);

		JLabel label = new JLabel("Resource:");
		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		c.weighty = 0;
		grid.setConstraints(label, c);
		getContentPane().add(label);

		label = new JLabel(URIUtils.urlDecodeUtf8(resource));
		c.gridx = 1;
		c.gridy = 0;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		grid.setConstraints(label, c);
		getContentPane().add(label);

        label = new JLabel("Mime:");
        c.gridx = 0;
        c.gridy = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
        grid.setConstraints(label, c);
        getContentPane().add(label);
        
        label = new JLabel(mimeType);
        c.gridx = 1;
        c.gridy = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
        grid.setConstraints(label, c);
        getContentPane().add(label);
        
		label = new JLabel("Created:");
		c.gridx = 0;
		c.gridy = 2;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		grid.setConstraints(label, c);
		getContentPane().add(label);

		String date = DateFormat.getDateTimeInstance().format(creationDate);
		label = new JLabel(date);
		c.gridx = 1;
		c.gridy = 2;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		grid.setConstraints(label, c);
		getContentPane().add(label);

		label = new JLabel("Last modified:");
		c.gridx = 0;
		c.gridy = 3;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		grid.setConstraints(label, c);
		getContentPane().add(label);

		date = modificationDate != null ? DateFormat.getDateTimeInstance().format(modificationDate) :
			"not available";
		label = new JLabel(date);
		c.gridx = 1;
		c.gridy = 3;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		grid.setConstraints(label, c);
		getContentPane().add(label);

		label = new JLabel("Owner");
		c.gridx = 0;
		c.gridy = 4;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		grid.setConstraints(label, c);
		getContentPane().add(label);

		Vector ol = new Vector();
		User users[] = service.getUsers();
		for (int i = 0; i < users.length; i++) {
			ol.addElement(users[i].getName());
		}
		owners = new JComboBox(ol);
		owners.setSelectedItem(permissions.getOwner());
		c.gridx = 1;
		c.gridy = 4;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		grid.setConstraints(owners, c);
		getContentPane().add(owners);

		label = new JLabel("Group");
		c.gridx = 0;
		c.gridy = 5;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		grid.setConstraints(label, c);
		getContentPane().add(label);

		Vector gl = new Vector();
		String allGroups[] = service.getGroups();
		for (int i = 0; i < allGroups.length; i++)
			gl.addElement(allGroups[i]);
		groups = new JComboBox(gl);
		groups.setSelectedItem(permissions.getOwnerGroup());
		c.gridx = 1;
		c.gridy = 5;
		c.anchor = GridBagConstraints.EAST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		grid.setConstraints(groups, c);
		getContentPane().add(groups);

		JComponent pc = setupPermissions();
		c.gridx = 0;
		c.gridy = 6;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.EAST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		grid.setConstraints(pc, c);
		getContentPane().add(pc);

		Box buttonBox = Box.createHorizontalBox();

		JButton button = new JButton("Apply");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				applyAction();
			}
		});
		buttonBox.add(button);
		button = new JButton("Cancel");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				cancelAction();
			}
		});
		buttonBox.add(button);

		c.gridx = 1;
		c.gridy = 7;
		c.gridwidth = 1;
		c.anchor = GridBagConstraints.EAST;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		grid.setConstraints(buttonBox, c);
		getContentPane().add(buttonBox);
	}

	private JComponent setupPermissions() {
		Box hbox = Box.createHorizontalBox();
		hbox.setBorder(
			BorderFactory.createTitledBorder(
				BorderFactory.createEtchedBorder(EtchedBorder.RAISED),
				"Permissions"));

		userPerms = new JCheckBox[3];
		JComponent c = getPermissionsBox("user", userPerms, permissions.getUserPermissions());
		hbox.add(c);

		groupPerms = new JCheckBox[3];
		c = getPermissionsBox("group", groupPerms, permissions.getGroupPermissions());
		hbox.add(c);

		worldPerms = new JCheckBox[3];
		c = getPermissionsBox("world", worldPerms, permissions.getPublicPermissions());
		hbox.add(c);
		return hbox;
	}

	private JComponent getPermissionsBox(String title, JCheckBox[] perms, int current) {
		Box vbox = Box.createVerticalBox();

		JLabel label = new JLabel(title);
		vbox.add(label);
		perms[0] = new JCheckBox("read", (current & 0x4) == 0x4);
		vbox.add(perms[0]);
		perms[1] = new JCheckBox("write", (current & 0x2) == 0x2);
		vbox.add(perms[1]);
		perms[2] = new JCheckBox("update", (current & 0x1) == 0x1);
		vbox.add(perms[2]);
		return vbox;
	}

	private int checkPermissions(JCheckBox cb[]) {
		int perm = 0;
		if (cb[0].isSelected())
			perm |= 4;
		if (cb[1].isSelected())
			perm |= 2;
		if (cb[2].isSelected())
			perm |= 1;
		return perm;
	}

	private void cancelAction() {
		this.setVisible(false);
		result = CANCEL_OPTION;
	}

	private void applyAction() {
		permissions.setOwner((String) owners.getSelectedItem());
		permissions.setGroup((String) groups.getSelectedItem());
		int perms =
			(checkPermissions(userPerms) << 6)
				| (checkPermissions(groupPerms) << 3)
				| checkPermissions(worldPerms);
		permissions.setPermissions(perms);
		this.setVisible(false);
		result = APPLY_OPTION;
	}
}
