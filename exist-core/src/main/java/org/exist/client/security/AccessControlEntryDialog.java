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

import java.util.*;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.LayoutStyle;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;

import org.exist.client.DialogCompleteWithResponse;
import org.exist.client.DialogWithResponse;
import org.exist.client.InteractiveClient;
import org.exist.security.ACLPermission.ACE_ACCESS_TYPE;
import org.exist.security.ACLPermission.ACE_TARGET;
import org.exist.security.Account;
import org.exist.security.Permission;
import org.exist.security.internal.aider.ACEAider;
import org.exist.xmldb.UserManagementService;
import org.xmldb.api.base.XMLDBException;

/**
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class AccessControlEntryDialog extends JFrame implements DialogWithResponse<ACEAider> {

    private final UserManagementService userManagementService;
    private final Set<String> allUsernames;
    private final Set<String> allGroupNames;
    private final List<DialogCompleteWithResponse<ACEAider>> dialogCompleteWithResponseCallbacks = new ArrayList<>();

    private DefaultTableModel permissionTableModel;
    private DefaultComboBoxModel<String> usernameModel;
    private DefaultComboBoxModel<String> groupNameModel;
    private JButton btnCreate;
    private JComboBox<String> cmbAccess;
    private JComboBox<String> cmbGroupName;
    private JComboBox<String> cmbTarget;
    private JComboBox<String> cmbUsername;
    private JTable tblPermission;

    public AccessControlEntryDialog(final UserManagementService userManagementService, final String title) throws XMLDBException {
        this.userManagementService = userManagementService;
        InteractiveClient.setExistImage(getClass(), this::setIconImage);
        allUsernames = new HashSet<>();
        for (final Account account : userManagementService.getAccounts()) {
            allUsernames.add(account.getName());
        }

        allGroupNames = new HashSet<>();
        allGroupNames.addAll(Arrays.asList(userManagementService.getGroups()));

        initComponents();
        setTitle(title);
    }

    private DefaultTableModel getPermissionTableModel() {
        if (permissionTableModel == null) {
            permissionTableModel = new DefaultTableModel(
                    new Object[][]{
                            new Object[]{false, false, false}
                    },
                    new String[]{"Read", "Write", "Execute"}
            ) {
                @Override
                public Class<?> getColumnClass(int columnIndex) {
                    return Boolean.class;
                }
            };
        }

        return permissionTableModel;
    }

    private ComboBoxModel<String> getUsernameModel() {
        if (usernameModel == null) {
            usernameModel = new DefaultComboBoxModel<>();
            usernameModel.addElement("");
            for (final String username : allUsernames) {
                usernameModel.addElement(username);
            }
        }

        return usernameModel;
    }

    private ComboBoxModel<String> getGroupNameModel() {
        if (groupNameModel == null) {
            groupNameModel = new DefaultComboBoxModel<>();
            groupNameModel.addElement("");
            for (final String groupName : allGroupNames) {
                groupNameModel.addElement(groupName);
            }
        }

        return groupNameModel;
    }

    private boolean isValidUsername(final String username) {
        return allUsernames.contains(username);
    }

    private boolean isValidGroupName(final String groupName) {
        return allGroupNames.contains(groupName);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    private void initComponents() {

        JLabel lblTarget = new JLabel();
        cmbTarget = new JComboBox<>();
        JLabel lblUsername = new JLabel();
        cmbUsername = new JComboBox<>();
        AutoCompletion.enable(cmbUsername);
        JLabel lblGroupName = new JLabel();
        cmbGroupName = new JComboBox<>();
        AutoCompletion.enable(cmbGroupName);
        JLabel lblAccess = new JLabel();
        cmbAccess = new JComboBox<>();
        JLabel lblPermission = new JLabel();
        JScrollPane jScrollPane1 = new JScrollPane();
        tblPermission = new JTable();
        JSeparator jSeparator1 = new JSeparator();
        btnCreate = new JButton();
        JButton btnClose = new JButton();

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        lblTarget.setText("Target:");

        cmbTarget.setModel(new DefaultComboBoxModel<>(new String[]{"USER", "GROUP"}));
        cmbTarget.addActionListener(e -> cmbTargetActionPerformed());

        lblUsername.setText("Username:");

        cmbUsername.setEditable(true);
        cmbUsername.setModel(getUsernameModel());
        cmbUsername.addActionListener(e -> cmbUsernameActionPerformed());

        lblGroupName.setText("Group:");

        cmbGroupName.setEditable(true);
        cmbGroupName.setModel(getGroupNameModel());
        cmbGroupName.setEnabled(false);
        cmbGroupName.addActionListener(e -> cmbGroupNameActionPerformed());

        lblAccess.setText("Access:");

        cmbAccess.setModel(new DefaultComboBoxModel<>(new String[]{"ALLOWED", "DENIED"}));

        lblPermission.setText("Permission");

        tblPermission.setModel(getPermissionTableModel());
        tblPermission.setRowSelectionAllowed(false);
        jScrollPane1.setViewportView(tblPermission);

        btnCreate.setText("Create");
        btnCreate.addActionListener(e -> btnCreateActionPerformed());

        btnClose.setText("Close");
        btnClose.addActionListener(e -> btnCloseActionPerformed());

        GroupLayout layout = new GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addGap(25, 25, 25)
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                        .addGroup(layout.createSequentialGroup()
                                                .addGap(6, 6, 6)
                                                .addComponent(jScrollPane1, GroupLayout.PREFERRED_SIZE, 345, GroupLayout.PREFERRED_SIZE))
                                        .addComponent(lblPermission)
                                        .addGroup(layout.createSequentialGroup()
                                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                                        .addComponent(lblUsername)
                                                        .addComponent(lblTarget)
                                                        .addComponent(lblGroupName)
                                                        .addComponent(lblAccess))
                                                .addGap(28, 28, 28)
                                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                                        .addComponent(cmbAccess, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING, false)
                                                                .addComponent(cmbTarget, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                                                .addComponent(cmbUsername, 0, 257, Short.MAX_VALUE)
                                                                .addComponent(cmbGroupName, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))))
                                .addContainerGap(24, Short.MAX_VALUE))
                        .addGroup(layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                        .addComponent(jSeparator1)
                                        .addGroup(GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                                .addGap(0, 0, Short.MAX_VALUE)
                                                .addComponent(btnClose)
                                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(btnCreate)))
                                .addContainerGap())
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addGap(17, 17, 17)
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                        .addComponent(lblTarget)
                                        .addComponent(cmbTarget, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                        .addComponent(lblUsername)
                                        .addComponent(cmbUsername, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                        .addComponent(lblGroupName)
                                        .addComponent(cmbGroupName, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                        .addComponent(cmbAccess, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                        .addComponent(lblAccess))
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(lblPermission)
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jScrollPane1, GroupLayout.PREFERRED_SIZE, 45, GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(jSeparator1, GroupLayout.PREFERRED_SIZE, 10, GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                        .addComponent(btnCreate)
                                        .addComponent(btnClose))
                                .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }

    private void btnCreateActionPerformed() {

        final ACE_TARGET target = ACE_TARGET.valueOf((String) cmbTarget.getSelectedItem());
        final String who;
        if (target == ACE_TARGET.USER) {
            who = (String) cmbUsername.getSelectedItem();
            if (!isValidUsername(who)) {
                return;
            }
        } else {
            who = (String) cmbGroupName.getSelectedItem();
            if (!isValidGroupName(who)) {
                return;
            }
        }

        final ACE_ACCESS_TYPE accessType = ACE_ACCESS_TYPE.valueOf((String) cmbAccess.getSelectedItem());
        int mode = 0;
        if ((Boolean) tblPermission.getValueAt(0, 0)) {
            mode |= Permission.READ;
        }
        if ((Boolean) tblPermission.getValueAt(0, 1)) {
            mode |= Permission.WRITE;
        }
        if ((Boolean) tblPermission.getValueAt(0, 2)) {
            mode |= Permission.EXECUTE;
        }

        final ACEAider ace = new ACEAider(accessType, target, who, mode);
        for (final DialogCompleteWithResponse<ACEAider> callback : getDialogCompleteWithResponseCallbacks()) {
            callback.complete(ace);
        }

        setVisible(false);
        dispose();
    }

    private void btnCloseActionPerformed() {
        setVisible(false);
        dispose();
    }

    private void cmbTargetActionPerformed() {
        final ACE_TARGET aceTarget = ACE_TARGET.valueOf((String) cmbTarget.getSelectedItem());
        switch (aceTarget) {
            case USER -> {
                cmbGroupName.setEnabled(false);
                cmbUsername.setEnabled(true);
            }
            case GROUP -> {
                cmbUsername.setEnabled(false);
                cmbGroupName.setEnabled(true);
            }
        }
    }

    private void cmbUsernameActionPerformed() {
        final String currentUsername = (String) cmbUsername.getSelectedItem();
        final boolean isValid = isValidUsername(currentUsername);
        btnCreate.setEnabled(isValid);
    }

    private void cmbGroupNameActionPerformed() {
        final String currentGroupName = (String) cmbGroupName.getSelectedItem();
        final boolean isValid = isValidGroupName(currentGroupName);
        btnCreate.setEnabled(isValid);
    }

    private List<DialogCompleteWithResponse<ACEAider>> getDialogCompleteWithResponseCallbacks() {
        return dialogCompleteWithResponseCallbacks;
    }

    @Override
    public void addDialogCompleteWithResponseCallback(final DialogCompleteWithResponse<ACEAider> dialogCompleteWithResponseCallback) {
        getDialogCompleteWithResponseCallbacks().add(dialogCompleteWithResponseCallback);
    }
}
