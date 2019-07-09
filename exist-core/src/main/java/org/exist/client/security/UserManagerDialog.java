/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2012 The eXist Project
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
 *
 *  $Id$
 */
package org.exist.client.security;

import org.exist.client.DialogCompleteWithResponse;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.Properties;
import javax.swing.JOptionPane;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import org.exist.client.ClientFrame;
import org.exist.client.HighlightedTableCellRenderer;
import org.exist.client.InteractiveClient;
import org.exist.security.AXSchemaType;
import org.exist.security.Account;
import org.exist.security.AccountComparator;
import org.exist.security.EXistSchemaType;
import org.exist.security.Group;
import org.exist.security.SecurityManager;
import org.exist.xmldb.UserManagementService;
import org.xmldb.api.base.XMLDBException;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class UserManagerDialog extends javax.swing.JFrame {

    private static final long serialVersionUID = 2091215304766070041L;

    private UserManagementService userManagementService;
    private final String currentUser;
    private final ClientFrame client;
    
    private DefaultTableModel usersTableModel = null;
    private DefaultTableModel groupsTableModel = null;

    public UserManagerDialog(final UserManagementService userManagementService, final String currentUser, final ClientFrame client) {
        this.userManagementService = userManagementService;
        this.currentUser = currentUser;
        this.client = client;
        this.setIconImage(InteractiveClient.getExistIcon(getClass()).getImage());
        initComponents();
        tblUsers.setDefaultRenderer(Object.class, new HighlightedTableCellRenderer());
        tblGroups.setDefaultRenderer(Object.class, new HighlightedTableCellRenderer());
    }
    
    private TableModel getUsersTableModel() {
        if(usersTableModel == null) {
            
            try {
                final Account accounts[] = userManagementService.getAccounts();
                
                Arrays.sort(accounts, new AccountComparator());

                final String tableData[][] = new String[accounts.length][3];
                for(int i = 0; i < accounts.length; i++) {
                    tableData[i][0] = accounts[i].getName();
                    tableData[i][1] = accounts[i].getMetadataValue(AXSchemaType.FULLNAME);
                    tableData[i][2] = accounts[i].getMetadataValue(EXistSchemaType.DESCRIPTION);
                }

                usersTableModel = new ReadOnlyDefaultTableModel(
                    tableData,
                    new String [] {
                        "User", "Full Name", "Description"
                    }
                );
            } catch(final XMLDBException xmldbe) {
                JOptionPane.showMessageDialog(this, "Could not get users list: " + xmldbe.getMessage(), "User Manager Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        return usersTableModel;
    }
    
    private TableModel getGroupsTableModel() {
        if(groupsTableModel == null) {
            
            try {
                final String groupNames[] = userManagementService.getGroups();

                Arrays.sort(groupNames);
                
                final String tableData[][] = new String[groupNames.length][2];
                for(int i = 0; i < groupNames.length; i++) {
                    tableData[i][0] = groupNames[i];
                    tableData[i][1] = userManagementService.getGroup(groupNames[i]).getMetadataValue(EXistSchemaType.DESCRIPTION);
                }

                groupsTableModel = new ReadOnlyDefaultTableModel(
                    tableData,
                    new String [] {
                        "Group", "Description"
                    }
                );
            } catch(final XMLDBException xmldbe) {
                JOptionPane.showMessageDialog(this, "Could not get groups list: " + xmldbe.getMessage(), "User Manager Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        return groupsTableModel;
    }
    
    public void refreshUsersTableModel() {
        final int rowCount = usersTableModel.getRowCount();
        for(int i = 0; i < rowCount; i++) {
            usersTableModel.removeRow(0);
        }
        
        try {
            final Account accounts[] = userManagementService.getAccounts();

            Arrays.sort(accounts, new AccountComparator());

            for(int i = 0; i < accounts.length; i++) {
                usersTableModel.addRow(new String[]{
                    accounts[i].getName(),
                    accounts[i].getMetadataValue(AXSchemaType.FULLNAME),
                    accounts[i].getMetadataValue(EXistSchemaType.DESCRIPTION)
                });
            }
        } catch(final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not get users list: " + xmldbe.getMessage(), "User Manager Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    public void refreshGroupsTableModel() {
        final int rowCount = groupsTableModel.getRowCount();
        for(int i = 0; i < rowCount; i++) {
            groupsTableModel.removeRow(0);
        }
        
        try {
            final String groupNames[] = userManagementService.getGroups();

            Arrays.sort(groupNames);

            for(int i = 0; i < groupNames.length; i++) {
                groupsTableModel.addRow(new String[]{
                    groupNames[i],
                    userManagementService.getGroup(groupNames[i]).getMetadataValue(EXistSchemaType.DESCRIPTION)
                });
            }
        } catch(final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not get groups list: " + xmldbe.getMessage(), "User Manager Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void showUserDialog() {
        final UserDialog userDialog = new UserDialog(userManagementService);
        
        userDialog.addWindowListener(new WindowAdapter(){           
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
        
        groupDialog.addWindowListener(new WindowAdapter(){           
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
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        pmUsers = new javax.swing.JPopupMenu();
        miNewUser = new javax.swing.JMenuItem();
        miEditUser = new javax.swing.JMenuItem();
        miRemoveUser = new javax.swing.JMenuItem();
        pmGroups = new javax.swing.JPopupMenu();
        miNewGroup = new javax.swing.JMenuItem();
        miEditGroup = new javax.swing.JMenuItem();
        miRemoveGroup = new javax.swing.JMenuItem();
        tpUserManager = new javax.swing.JTabbedPane();
        spUsers = new javax.swing.JScrollPane();
        tblUsers = new javax.swing.JTable();
        spGroups = new javax.swing.JScrollPane();
        tblGroups = new javax.swing.JTable();
        jSeparator1 = new javax.swing.JSeparator();
        btnCreate = new javax.swing.JButton();
        btnClose = new javax.swing.JButton();

        miNewUser.setText("New User...");
        miNewUser.addActionListener(this::miNewUserActionPerformed);
        pmUsers.add(miNewUser);
        miNewUser.getAccessibleContext().setAccessibleName("New User");

        miEditUser.setText("Edit User...");
        miEditUser.addActionListener(this::miEditUserActionPerformed);
        pmUsers.add(miEditUser);
        miEditUser.getAccessibleContext().setAccessibleName("Edit User");

        miRemoveUser.setText("Remove User");
        miRemoveUser.addActionListener(this::miRemoveUserActionPerformed);
        pmUsers.add(miRemoveUser);

        miNewGroup.setText("New Group...");
        miNewGroup.addActionListener(this::miNewGroupActionPerformed);
        pmGroups.add(miNewGroup);
        miNewGroup.getAccessibleContext().setAccessibleName("New Group");

        miEditGroup.setText("Edit Group...");
        miEditGroup.addActionListener(this::miEditGroupActionPerformed);
        pmGroups.add(miEditGroup);
        miEditGroup.getAccessibleContext().setAccessibleName("Edit Group");

        miRemoveGroup.setText("Remove Group");
        miRemoveGroup.addActionListener(this::miRemoveGroupActionPerformed);
        pmGroups.add(miRemoveGroup);

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("User Manager");

        tblUsers.setModel(getUsersTableModel());
        tblUsers.setAutoCreateRowSorter(true);
        tblUsers.setComponentPopupMenu(pmUsers);
        tblUsers.setShowGrid(true);
        tblUsers.addMouseListener(new java.awt.event.MouseAdapter() {
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
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                tblGroupsMouseClicked(evt);
            }
        });
        spGroups.setViewportView(tblGroups);

        tpUserManager.addTab("Groups", spGroups);
        spGroups.getAccessibleContext().setAccessibleName("Groups");

        btnCreate.setText("Create");
        btnCreate.addActionListener(this::btnCreateActionPerformed);

        btnClose.setText("Close");
        btnClose.addActionListener(this::btnCloseActionPerformed);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap(250, Short.MAX_VALUE)
                .addComponent(btnClose)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnCreate)
                .addGap(20, 20, 20))
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jSeparator1)
                .addContainerGap())
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(tpUserManager, javax.swing.GroupLayout.DEFAULT_SIZE, 439, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGap(0, 291, Short.MAX_VALUE)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(btnCreate)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(btnClose)
                        .addContainerGap())))
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addComponent(tpUserManager, javax.swing.GroupLayout.PREFERRED_SIZE, 291, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGap(0, 51, Short.MAX_VALUE)))
        );

        tpUserManager.getAccessibleContext().setAccessibleName("Users");

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void miNewUserActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_miNewUserActionPerformed
        showUserDialog();
    }//GEN-LAST:event_miNewUserActionPerformed

    private void btnCloseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCloseActionPerformed
        setVisible(false);
        dispose();
    }//GEN-LAST:event_btnCloseActionPerformed

    private String getSelectedUsername() {
        return (String)tblUsers.getValueAt(tblUsers.getSelectedRow(), 0);
    }
    
    private String getSelectedGroup() {
        return (String)tblGroups.getValueAt(tblGroups.getSelectedRow(), 0);
    }
    
    private void miRemoveUserActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_miRemoveUserActionPerformed

        final String selectedUsername = getSelectedUsername();
        try {
            final Account account = userManagementService.getAccount(selectedUsername);
            userManagementService.removeAccount(account);
        
            usersTableModel.removeRow(tblUsers.getSelectedRow());
        } catch(final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not remove user '" + selectedUsername + "': " + xmldbe.getMessage(), "User Manager Error", JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_miRemoveUserActionPerformed

    private void miEditUserActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_miEditUserActionPerformed
        
        final String selectedUsername = getSelectedUsername();
        try {
            final Account account = userManagementService.getAccount(selectedUsername);
            showEditUserDialog(account);
        } catch(final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not edit user '" + selectedUsername + "': " + xmldbe.getMessage(), "User Manager Error", JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_miEditUserActionPerformed
    
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
            } catch(final XMLDBException xmldbe) {
                JOptionPane.showMessageDialog(that, "Could not edit user '" + getSelectedUsername() + "': " + xmldbe.getMessage(), "User Manager Error", JOptionPane.ERROR_MESSAGE);
            }
        };

        final EditUserDialog userDialog = new EditUserDialog(userManagementService, account);
        if(getSelectedUsername().equals(currentUser)) {
            //register for password update event, if we are changing the password
            //of the current user
            userDialog.addDialogCompleteWithResponseCallback(callback);
        }
        
        userDialog.addWindowListener(new WindowAdapter(){           
            @Override
            public void windowClosed(final WindowEvent e) {
                refreshUsersTableModel();
            }
        });
        
        userDialog.setVisible(true);
    }
    
    private void showEditGroupDialog(final Group group) {
        final EditGroupDialog groupDialog = new EditGroupDialog(userManagementService, currentUser, group);
        
        groupDialog.addWindowListener(new WindowAdapter(){           
            @Override
            public void windowClosed(final WindowEvent e) {
                refreshGroupsTableModel();
            }
        });
        
        groupDialog.setVisible(true);
    }
    
    private void btnCreateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCreateActionPerformed
        switch(tpUserManager.getSelectedIndex()) {
            case 0:
                showUserDialog();
                break;
            
            case 1:
                showGroupDialog();
                break;
            
            default:
                return;
        }
    }//GEN-LAST:event_btnCreateActionPerformed

    private void tblUsersMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_tblUsersMouseClicked
        final boolean userSelected = tblUsers.getSelectedRow() > -1;
        final String selectedUsername = getSelectedUsername();
        
        boolean canModify = userSelected && !selectedUsername.equals(SecurityManager.SYSTEM);
        boolean canDelete = userSelected && !(selectedUsername.equals(SecurityManager.SYSTEM) || selectedUsername.equals(SecurityManager.DBA_USER) || selectedUsername.equals(SecurityManager.GUEST_USER));
        miEditUser.setEnabled(canModify);
        miRemoveUser.setEnabled(canDelete);
        
        if(evt.getClickCount() == 2 && canModify) {
            
            try {
                final Account account = userManagementService.getAccount(selectedUsername);
                showEditUserDialog(account);
            } catch(final XMLDBException xmldbe) {
                JOptionPane.showMessageDialog(this, "Could not edit user '" + selectedUsername + "': " + xmldbe.getMessage(), "User Manager Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }//GEN-LAST:event_tblUsersMouseClicked

    private void miEditGroupActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_miEditGroupActionPerformed
        final String selectedGroup = getSelectedGroup();
            try {
                final Group group = userManagementService.getGroup(selectedGroup);
                showEditGroupDialog(group);
            } catch(final XMLDBException xmldbe) {
                JOptionPane.showMessageDialog(this, "Could not edit group '" + selectedGroup + "': " + xmldbe.getMessage(), "User Manager Error", JOptionPane.ERROR_MESSAGE);
            }
    }//GEN-LAST:event_miEditGroupActionPerformed
    
    private void miRemoveGroupActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_miRemoveGroupActionPerformed
        final String selectedGroup = getSelectedGroup();
        
        try {
            final Group group = userManagementService.getGroup(selectedGroup);
            userManagementService.removeGroup(group);
        
            groupsTableModel.removeRow(tblGroups.getSelectedRow());
        } catch(final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not remove group '" + selectedGroup + "': " + xmldbe.getMessage(), "User Manager Error", JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_miRemoveGroupActionPerformed

    private void tblGroupsMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_tblGroupsMouseClicked
        final boolean groupSelected = tblGroups.getSelectedRow() > -1;
        final String selectedGroup = getSelectedGroup();
        
        boolean canDelete = groupSelected && !(selectedGroup.equals(SecurityManager.DBA_GROUP) || selectedGroup.equals(SecurityManager.GUEST_GROUP));
        
        miRemoveGroup.setEnabled(canDelete);
        
         if(evt.getClickCount() == 2) {
            try {
                final Group group = userManagementService.getGroup(selectedGroup);
                showEditGroupDialog(group);
            } catch(final XMLDBException xmldbe) {
                JOptionPane.showMessageDialog(this, "Could not edit group '" + selectedGroup + "': " + xmldbe.getMessage(), "User Manager Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }//GEN-LAST:event_tblGroupsMouseClicked

    private void miNewGroupActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_miNewGroupActionPerformed
       showGroupDialog();
    }//GEN-LAST:event_miNewGroupActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnClose;
    private javax.swing.JButton btnCreate;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JMenuItem miEditGroup;
    private javax.swing.JMenuItem miEditUser;
    private javax.swing.JMenuItem miNewGroup;
    private javax.swing.JMenuItem miNewUser;
    private javax.swing.JMenuItem miRemoveGroup;
    private javax.swing.JMenuItem miRemoveUser;
    private javax.swing.JPopupMenu pmGroups;
    private javax.swing.JPopupMenu pmUsers;
    private javax.swing.JScrollPane spGroups;
    private javax.swing.JScrollPane spUsers;
    private javax.swing.JTable tblGroups;
    private javax.swing.JTable tblUsers;
    private javax.swing.JTabbedPane tpUserManager;
    // End of variables declaration//GEN-END:variables
}
