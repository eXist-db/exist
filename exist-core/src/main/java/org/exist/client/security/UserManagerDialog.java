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
package org.exist.client.security;

import static org.exist.security.SecurityManager.DBA_GROUP;
import static org.exist.security.SecurityManager.DBA_USER;
import static org.exist.security.SecurityManager.GUEST_GROUP;
import static org.exist.security.SecurityManager.GUEST_USER;
import static org.exist.security.SecurityManager.SYSTEM;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.Serial;
import java.util.Arrays;
import java.util.Properties;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.LayoutStyle;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;

import org.exist.client.ClientFrame;
import org.exist.client.DialogCompleteWithResponse;
import org.exist.client.HighlightedTableCellRenderer;
import org.exist.client.InteractiveClient;
import org.exist.security.AXSchemaType;
import org.exist.security.Account;
import org.exist.security.AccountComparator;
import org.exist.security.EXistSchemaType;
import org.exist.security.Group;
import org.exist.xmldb.UserManagementService;
import org.xmldb.api.base.XMLDBException;

/**
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class UserManagerDialog extends JFrame {

    @Serial
    private static final long serialVersionUID = 2091215304766070041L;

    private final String currentUser;
    private final ClientFrame client;

    private UserManagementService userManagementService;
    private JMenuItem miEditUser;
    private JMenuItem miRemoveGroup;
    private JMenuItem miRemoveUser;
    private JTable tblGroups;
    private JTable tblUsers;
    private JTabbedPane tpUserManager;
    private DefaultTableModel usersTableModel;
    private DefaultTableModel groupsTableModel;

    public UserManagerDialog(final UserManagementService userManagementService, final String currentUser, final ClientFrame client) {
        this.userManagementService = userManagementService;
        this.currentUser = currentUser;
        this.client = client;
        InteractiveClient.setExistImage(getClass(), this::setIconImage);
        initComponents();
        tblUsers.setDefaultRenderer(Object.class, new HighlightedTableCellRenderer<>());
        tblGroups.setDefaultRenderer(Object.class, new HighlightedTableCellRenderer<>());
    }

    private TableModel getUsersTableModel() {
        if (usersTableModel == null) {

            try {
                final Account[] accounts = userManagementService.getAccounts();

                Arrays.sort(accounts, new AccountComparator());

                final String[][] tableData = new String[accounts.length][3];
                for (int i = 0; i < accounts.length; i++) {
                    tableData[i][0] = accounts[i].getName();
                    tableData[i][1] = accounts[i].getMetadataValue(AXSchemaType.FULLNAME);
                    tableData[i][2] = accounts[i].getMetadataValue(EXistSchemaType.DESCRIPTION);
                }

                usersTableModel = new ReadOnlyDefaultTableModel(
                        tableData,
                        new String[]{
                                "User", "Full Name", "Description"
                        }
                );
            } catch (final XMLDBException xmldbe) {
                JOptionPane.showMessageDialog(this, "Could not get users list: " + xmldbe.getMessage(), "User Manager Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        return usersTableModel;
    }

    private TableModel getGroupsTableModel() {
        if (groupsTableModel == null) {

            try {
                final String[] groupNames = userManagementService.getGroups();

                Arrays.sort(groupNames);

                final String[][] tableData = new String[groupNames.length][2];
                for (int i = 0; i < groupNames.length; i++) {
                    tableData[i][0] = groupNames[i];
                    tableData[i][1] = userManagementService.getGroup(groupNames[i]).getMetadataValue(EXistSchemaType.DESCRIPTION);
                }

                groupsTableModel = new ReadOnlyDefaultTableModel(
                        tableData,
                        new String[]{
                                "Group", "Description"
                        }
                );
            } catch (final XMLDBException xmldbe) {
                JOptionPane.showMessageDialog(this, "Could not get groups list: " + xmldbe.getMessage(), "User Manager Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        return groupsTableModel;
    }

    public void refreshUsersTableModel() {
        final int rowCount = usersTableModel.getRowCount();
        for (int i = 0; i < rowCount; i++) {
            usersTableModel.removeRow(0);
        }

        try {
            final Account[] accounts = userManagementService.getAccounts();

            Arrays.sort(accounts, new AccountComparator());

            for (Account account : accounts) {
                usersTableModel.addRow(new String[]{
                        account.getName(),
                        account.getMetadataValue(AXSchemaType.FULLNAME),
                        account.getMetadataValue(EXistSchemaType.DESCRIPTION)
                });
            }
        } catch (final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not get users list: " + xmldbe.getMessage(), "User Manager Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void refreshGroupsTableModel() {
        final int rowCount = groupsTableModel.getRowCount();
        for (int i = 0; i < rowCount; i++) {
            groupsTableModel.removeRow(0);
        }

        try {
            final String[] groupNames = userManagementService.getGroups();

            Arrays.sort(groupNames);

            for (String groupName : groupNames) {
                groupsTableModel.addRow(new String[]{
                        groupName,
                        userManagementService.getGroup(groupName).getMetadataValue(EXistSchemaType.DESCRIPTION)
                });
            }
        } catch (final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not get groups list: " + xmldbe.getMessage(), "User Manager Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showUserDialog() {
        final UserDialog userDialog = new UserDialog(userManagementService);

        userDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(final WindowEvent e) {
                refreshUsersTableModel();
                refreshGroupsTableModel(); //creating a user may have created a private group for that user
            }
        });

        userDialog.setVisible(true);
    }

    private void showGroupDialog() {
        final GroupDialog groupDialog = new GroupDialog(userManagementService, currentUser);

        groupDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(final WindowEvent e) {
                refreshGroupsTableModel();
            }
        });

        groupDialog.setVisible(true);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    private void initComponents() {

        JPopupMenu pmUsers = new JPopupMenu();
        JMenuItem miNewUser = new JMenuItem();
        miEditUser = new JMenuItem();
        miRemoveUser = new JMenuItem();
        JPopupMenu pmGroups = new JPopupMenu();
        JMenuItem miNewGroup = new JMenuItem();
        JMenuItem miEditGroup = new JMenuItem();
        miRemoveGroup = new JMenuItem();
        tpUserManager = new JTabbedPane();
        JScrollPane spUsers = new JScrollPane();
        tblUsers = new JTable();
        JScrollPane spGroups = new JScrollPane();
        tblGroups = new JTable();
        JSeparator jSeparator1 = new JSeparator();
        JButton btnCreate = new JButton();
        JButton btnClose = new JButton();

        miNewUser.setText("New User...");
        miNewUser.addActionListener(e -> miNewUserActionPerformed());
        pmUsers.add(miNewUser);
        miNewUser.getAccessibleContext().setAccessibleName("New User");

        miEditUser.setText("Edit User...");
        miEditUser.addActionListener(e -> miEditUserActionPerformed());
        pmUsers.add(miEditUser);
        miEditUser.getAccessibleContext().setAccessibleName("Edit User");

        miRemoveUser.setText("Remove User");
        miRemoveUser.addActionListener(e -> miRemoveUserActionPerformed());
        pmUsers.add(miRemoveUser);

        miNewGroup.setText("New Group...");
        miNewGroup.addActionListener(e -> miNewGroupActionPerformed());
        pmGroups.add(miNewGroup);
        miNewGroup.getAccessibleContext().setAccessibleName("New Group");

        miEditGroup.setText("Edit Group...");
        miEditGroup.addActionListener(e -> miEditGroupActionPerformed());
        pmGroups.add(miEditGroup);
        miEditGroup.getAccessibleContext().setAccessibleName("Edit Group");

        miRemoveGroup.setText("Remove Group");
        miRemoveGroup.addActionListener(e -> miRemoveGroupActionPerformed());
        pmGroups.add(miRemoveGroup);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("User Manager");

        tblUsers.setModel(getUsersTableModel());
        tblUsers.setAutoCreateRowSorter(true);
        tblUsers.setComponentPopupMenu(pmUsers);
        tblUsers.setShowGrid(true);
        tblUsers.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                tblUsersMouseClicked(evt);
            }
        });
        spUsers.setViewportView(tblUsers);

        tpUserManager.addTab("Users", spUsers);

        tblGroups.setModel(getGroupsTableModel());
        tblGroups.setAutoCreateRowSorter(true);
        tblGroups.setComponentPopupMenu(pmGroups);
        tblGroups.setShowGrid(true);
        tblGroups.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                tblGroupsMouseClicked(evt);
            }
        });
        spGroups.setViewportView(tblGroups);

        tpUserManager.addTab("Groups", spGroups);
        spGroups.getAccessibleContext().setAccessibleName("Groups");

        btnCreate.setText("Create");
        btnCreate.addActionListener(e -> btnCreateActionPerformed());

        btnClose.setText("Close");
        btnClose.addActionListener(e -> btnCloseActionPerformed());

        GroupLayout layout = new GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addContainerGap(250, Short.MAX_VALUE)
                                .addComponent(btnClose)
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(btnCreate)
                                .addGap(20, 20, 20))
                        .addGroup(layout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(jSeparator1)
                                .addContainerGap())
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addComponent(tpUserManager, GroupLayout.DEFAULT_SIZE, 439, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addGap(0, 291, Short.MAX_VALUE)
                                .addComponent(jSeparator1, GroupLayout.PREFERRED_SIZE, 10, GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                        .addComponent(btnCreate)
                                        .addGroup(GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                                .addComponent(btnClose)
                                                .addContainerGap())))
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createSequentialGroup()
                                        .addComponent(tpUserManager, GroupLayout.PREFERRED_SIZE, 291, GroupLayout.PREFERRED_SIZE)
                                        .addGap(0, 51, Short.MAX_VALUE)))
        );

        tpUserManager.getAccessibleContext().setAccessibleName("Users");

        pack();
    }

    private void miNewUserActionPerformed() {
        showUserDialog();
    }

    private void btnCloseActionPerformed() {
        setVisible(false);
        dispose();
    }

    private String getSelectedUsername() {
        return (String) tblUsers.getValueAt(tblUsers.getSelectedRow(), 0);
    }

    private String getSelectedGroup() {
        return (String) tblGroups.getValueAt(tblGroups.getSelectedRow(), 0);
    }

    private void miRemoveUserActionPerformed() {

        final String selectedUsername = getSelectedUsername();
        try {
            final Account account = userManagementService.getAccount(selectedUsername);
            userManagementService.removeAccount(account);

            usersTableModel.removeRow(tblUsers.getSelectedRow());
        } catch (final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not remove user '" + selectedUsername + "': " + xmldbe.getMessage(), "User Manager Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void miEditUserActionPerformed() {

        final String selectedUsername = getSelectedUsername();
        try {
            final Account account = userManagementService.getAccount(selectedUsername);
            showEditUserDialog(account);
        } catch (final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not edit user '" + selectedUsername + "': " + xmldbe.getMessage(), "User Manager Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private UserManagementService reconnectClientAndUserManager(final String password) throws XMLDBException {
        //get client to reconnect with edited users new password
        final Properties loginData = new Properties();
        loginData.setProperty(InteractiveClient.PASSWORD, password);
        client.reconnectClient(loginData);

        //get reconnected userManagementService
        return client.getUserManagementService();
    }

    private void showEditUserDialog(final Account account) {

        final UserManagerDialog that = this;

        final DialogCompleteWithResponse<String> callback = response -> {
            //get client to reconnect with edited users new password
            try {
                System.out.println("Detected logged-in user password change, reconnecting to server...");
                that.userManagementService = reconnectClientAndUserManager(response);
                System.out.println("Reconnected.");
            } catch (final XMLDBException xmldbe) {
                JOptionPane.showMessageDialog(that, "Could not edit user '" + getSelectedUsername() + "': " + xmldbe.getMessage(), "User Manager Error", JOptionPane.ERROR_MESSAGE);
            }
        };

        final EditUserDialog userDialog = new EditUserDialog(userManagementService, account);
        if (getSelectedUsername().equals(currentUser)) {
            //register for password update event, if we are changing the password
            //of the current user
            userDialog.addDialogCompleteWithResponseCallback(callback);
        }

        userDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(final WindowEvent e) {
                refreshUsersTableModel();
            }
        });

        userDialog.setVisible(true);
    }

    private void showEditGroupDialog(final Group group) {
        final EditGroupDialog groupDialog = new EditGroupDialog(userManagementService, currentUser, group);

        groupDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(final WindowEvent e) {
                refreshGroupsTableModel();
            }
        });

        groupDialog.setVisible(true);
    }

    private void btnCreateActionPerformed() {
        switch (tpUserManager.getSelectedIndex()) {
            case 0 -> showUserDialog();

            case 1 -> showGroupDialog();

            default -> {
                return;
            }
        }
    }

    private void tblUsersMouseClicked(java.awt.event.MouseEvent evt) {
        final boolean userSelected = tblUsers.getSelectedRow() > -1;
        final String selectedUsername = getSelectedUsername();

        boolean canModify = userSelected && !selectedUsername.equals(SYSTEM);
        boolean canDelete = userSelected && !(selectedUsername.equals(SYSTEM) || selectedUsername.equals(DBA_USER) || selectedUsername.equals(GUEST_USER));
        miEditUser.setEnabled(canModify);
        miRemoveUser.setEnabled(canDelete);

        if (evt.getClickCount() == 2 && canModify) {

            try {
                final Account account = userManagementService.getAccount(selectedUsername);
                showEditUserDialog(account);
            } catch (final XMLDBException xmldbe) {
                JOptionPane.showMessageDialog(this, "Could not edit user '" + selectedUsername + "': " + xmldbe.getMessage(), "User Manager Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void miEditGroupActionPerformed() {
        final String selectedGroup = getSelectedGroup();
        try {
            final Group group = userManagementService.getGroup(selectedGroup);
            showEditGroupDialog(group);
        } catch (final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not edit group '" + selectedGroup + "': " + xmldbe.getMessage(), "User Manager Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void miRemoveGroupActionPerformed() {
        final String selectedGroup = getSelectedGroup();

        try {
            final Group group = userManagementService.getGroup(selectedGroup);
            userManagementService.removeGroup(group);

            groupsTableModel.removeRow(tblGroups.getSelectedRow());
        } catch (final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not remove group '" + selectedGroup + "': " + xmldbe.getMessage(), "User Manager Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void tblGroupsMouseClicked(java.awt.event.MouseEvent evt) {
        final boolean groupSelected = tblGroups.getSelectedRow() > -1;
        final String selectedGroup = getSelectedGroup();

        boolean canDelete = groupSelected && !(selectedGroup.equals(DBA_GROUP) || selectedGroup.equals(GUEST_GROUP));

        miRemoveGroup.setEnabled(canDelete);

        if (evt.getClickCount() == 2) {
            try {
                final Group group = userManagementService.getGroup(selectedGroup);
                showEditGroupDialog(group);
            } catch (final XMLDBException xmldbe) {
                JOptionPane.showMessageDialog(this, "Could not edit group '" + selectedGroup + "': " + xmldbe.getMessage(), "User Manager Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void miNewGroupActionPerformed() {
        showGroupDialog();
    }
}
