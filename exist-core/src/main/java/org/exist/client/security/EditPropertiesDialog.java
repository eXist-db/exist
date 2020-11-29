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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import javax.swing.JOptionPane;
import javax.swing.table.DefaultTableModel;

import org.exist.client.*;
import org.exist.client.tristatecheckbox.TristateCheckboxTableCellEditor;
import org.exist.client.tristatecheckbox.TristateCheckBoxTableCellRenderer;
import org.exist.client.tristatecheckbox.TristateState;
import org.exist.security.ACLPermission;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.internal.aider.ACEAider;
import org.exist.security.internal.aider.SimpleACLPermissionAider;
import org.exist.xmldb.UserManagementService;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static org.exist.client.ClientFrame.MULTIPLE_INDICATOR;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class EditPropertiesDialog extends javax.swing.JFrame {
    private final UserManagementService userManagementService;
    private final String currentUser;
    private final Collection parent;
    private final String uri;
    private final String internetMediaType;
    private final String created;
    private final String lastModified;
    private String size;
    private final String messageDigestType;
    private final String messageDigestValue;
    private final String owner;
    private final String group;
    private final ModeDisplay mode;
    @Nullable private final SimpleACLPermissionAider acl;
    private final List<ResourceDescriptor> applyTo;
    
    private BasicPermissionsTableModel basicPermissionsTableModel = null;
    private DefaultTableModel aclTableModel = null;

    private final static String ERROR_TITLE = "Edit Properties Error";

    public EditPropertiesDialog(final UserManagementService userManagementService, final String currentUser, final Collection parent, final String uri, final String internetMediaType, final String created, final String lastModified, final String size, final String messageDigestType, final String messageDigestValue, final String owner, final String group, final ModeDisplay mode, @Nullable final SimpleACLPermissionAider acl, final List<ResourceDescriptor> applyTo) {
        this.userManagementService = userManagementService;
        this.currentUser = currentUser;
        this.parent = parent;
        this.uri = uri;
        this.internetMediaType = internetMediaType;
        this.created = created;
        this.lastModified = lastModified;
        this.size = size;
        this.messageDigestType = messageDigestType;
        this.messageDigestValue = messageDigestValue;
        this.owner = owner;
        this.group = group;
        this.mode = mode;
        this.acl = acl;
        this.applyTo = applyTo;
        this.setIconImage(InteractiveClient.getExistIcon(getClass()).getImage());        
        initComponents();
        setFormProperties();
    }

    private void setFormProperties() {
        lblResourceValue.setText(uri);
        lblInternetMediaTypeValue.setText(internetMediaType);
        lblCreatedValue.setText(created);
        lblLastModifiedValue.setText(lastModified);
        lblSizeValue.setText(size);
        lblDigestAlgorithmValue.setText(messageDigestType);
        txtDigest.setText(messageDigestValue);
        txtDigest.setVisible(true);
        spDigest.setVisible(true);
        lblOwnerValue.setText(owner);
        lblGroupValue.setText(group);
        
        try {
            final boolean canModify = canModifyPermissions();
        
            btnChangeOwner.setEnabled(isDba());
            btnChangeGroup.setEnabled(isDba());
            
            tblBasePermissions.setEnabled(canModify);

            final boolean canModifyAcl = acl != null && canModify;

            tblAcl.setEnabled(canModifyAcl);
            
            miInsertAceBefore.setEnabled(canModifyAcl);
            miInsertAceAfter.setEnabled(canModifyAcl);
            btnAddAce.setEnabled(canModifyAcl);
        
            miMoveUp.setEnabled(false);
            miMoveDown.setEnabled(false);
        
            miRemoveAce.setEnabled(false);
        } catch(final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not get dba group members: " + xmldbe.getMessage(), ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }

    private BasicPermissionsTableModel getBasicPermissionsTableModel() {
        if(basicPermissionsTableModel == null) {
            basicPermissionsTableModel = new BasicPermissionsTableModel(mode);
        }
        
        return basicPermissionsTableModel;
    }
    
    private DefaultTableModel getAclTableModel() {
        if(aclTableModel == null) {
            aclTableModel = new AclTableModel(acl);
        }
        return aclTableModel;
    }

    private UserManagementService getUserManagementService() {
        return userManagementService;
    }
            
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        pmAcl = new javax.swing.JPopupMenu();
        miInsertAceBefore = new javax.swing.JMenuItem();
        miInsertAceAfter = new javax.swing.JMenuItem();
        jSeparator3 = new javax.swing.JPopupMenu.Separator();
        miMoveUp = new javax.swing.JMenuItem();
        miMoveDown = new javax.swing.JMenuItem();
        jSeparator4 = new javax.swing.JPopupMenu.Separator();
        miRemoveAce = new javax.swing.JMenuItem();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTextArea1 = new javax.swing.JTextArea();
        lblResource = new javax.swing.JLabel();
        lblInternetMediaType = new javax.swing.JLabel();
        lblCreated = new javax.swing.JLabel();
        lblLastModified = new javax.swing.JLabel();
        lblOwner = new javax.swing.JLabel();
        lblGroup = new javax.swing.JLabel();
        lblResourceValue = new javax.swing.JLabel();
        lblInternetMediaTypeValue = new javax.swing.JLabel();
        lblCreatedValue = new javax.swing.JLabel();
        lblLastModifiedValue = new javax.swing.JLabel();
        lblOwnerValue = new javax.swing.JLabel();
        lblGroupValue = new javax.swing.JLabel();
        btnChangeOwner = new javax.swing.JButton();
        btnChangeGroup = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        tblBasePermissions = new javax.swing.JTable();
        if (applyTo.size() > 1) {
            tblBasePermissions.setDefaultRenderer(Boolean.class, new TristateCheckBoxTableCellRenderer<Boolean>(bool -> Tuple(null, TristateState.fromBoolean(bool))));
            tblBasePermissions.setDefaultEditor(Boolean.class, new TristateCheckboxTableCellEditor<>(bool -> Tuple(null, TristateState.fromBoolean(bool)), state -> TristateState.toBoolean(state._2)));
            tblBasePermissions.setDefaultRenderer(LabelledBoolean.class, new TristateCheckBoxTableCellRenderer<LabelledBoolean>(labelledBool -> Tuple(labelledBool.getLabel(), TristateState.fromBoolean(labelledBool.isSet()))));
            tblBasePermissions.setDefaultEditor(LabelledBoolean.class, new TristateCheckboxTableCellEditor<>(labelledBool -> Tuple(labelledBool.getLabel(), TristateState.fromBoolean(labelledBool.isSet())), state -> new LabelledBoolean(state._1, TristateState.toBoolean(state._2))));
        } else {
            tblBasePermissions.setDefaultRenderer(LabelledBoolean.class, new CheckboxTableCellRenderer<LabelledBoolean>(labelledBoolean -> Tuple(labelledBoolean.getLabel(), labelledBoolean.isSet())));
            tblBasePermissions.setDefaultEditor(LabelledBoolean.class, new CheckboxTableCellEditor<>(labelledBoolean -> Tuple(labelledBoolean.getLabel(), labelledBoolean.isSet()), state -> new LabelledBoolean(state._1, state._2)));
        }
        lblAccessControlList = new javax.swing.JLabel();
        jSeparator1 = new javax.swing.JSeparator();
        lblBasePermissions = new javax.swing.JLabel();
        jScrollPane3 = new javax.swing.JScrollPane();
        tblAcl = new javax.swing.JTable();
        jSeparator2 = new javax.swing.JSeparator();
        btnSave = new javax.swing.JButton();
        btnClose = new javax.swing.JButton();
        btnAddAce = new javax.swing.JButton();
        lblDigest = new javax.swing.JLabel();
        lblDigestAlgorithmValue = new javax.swing.JLabel();
        spDigest = new javax.swing.JScrollPane();
        txtDigest = new javax.swing.JTextArea();
        lblSize = new javax.swing.JLabel();
        lblSizeValue = new javax.swing.JLabel();

        miInsertAceBefore.setText("Insert ACE before...");
        miInsertAceBefore.addActionListener(evt -> miInsertAceBeforeActionPerformed(evt));
        pmAcl.add(miInsertAceBefore);

        miInsertAceAfter.setText("Insert ACE after...");
        miInsertAceAfter.addActionListener(evt -> miInsertAceAfterActionPerformed(evt));
        pmAcl.add(miInsertAceAfter);
        pmAcl.add(jSeparator3);

        miMoveUp.setText("Move ACE up");
        miMoveUp.addActionListener(evt -> miMoveUpActionPerformed(evt));
        pmAcl.add(miMoveUp);

        miMoveDown.setText("Move ACE down");
        miMoveDown.addActionListener(evt -> miMoveDownActionPerformed(evt));
        pmAcl.add(miMoveDown);
        pmAcl.add(jSeparator4);

        miRemoveAce.setText("Remove ACE");
        miRemoveAce.addActionListener(evt -> miRemoveAceActionPerformed(evt));
        pmAcl.add(miRemoveAce);

        jTextArea1.setColumns(20);
        jTextArea1.setRows(5);
        jScrollPane1.setViewportView(jTextArea1);

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Properties");

        lblResource.setText("Resource:");

        lblInternetMediaType.setText("Internet Media Type:");

        lblCreated.setText("Created:");

        lblLastModified.setText("Last Modified:");

        lblOwner.setText("Owner:");

        lblGroup.setText("Group:");

        lblResourceValue.setText("<resource>");

        lblInternetMediaTypeValue.setText("<internet media type>");

        lblCreatedValue.setText("<created>");

        lblLastModifiedValue.setText("<last modified>");

        lblOwnerValue.setText("<owner>");

        lblGroupValue.setText("<group>");

        btnChangeOwner.setText("...");
        btnChangeOwner.addActionListener(evt -> btnChangeOwnerActionPerformed(evt));

        btnChangeGroup.setText("...");
        btnChangeGroup.addActionListener(evt -> btnChangeGroupActionPerformed(evt));

        tblBasePermissions.setModel(getBasicPermissionsTableModel());
        tblBasePermissions.setRowSelectionAllowed(false);
        jScrollPane2.setViewportView(tblBasePermissions);

        lblAccessControlList.setText("Access Control List");

        lblBasePermissions.setText("Base Permissions");

        tblAcl.setModel(getAclTableModel());
        tblAcl.setComponentPopupMenu(pmAcl);
        tblAcl.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                tblAclMouseClicked(evt);
            }
        });
        jScrollPane3.setViewportView(tblAcl);

        btnSave.setText("Save");
        btnSave.addActionListener(evt -> btnSaveActionPerformed(evt));

        btnClose.setText("Close");
        btnClose.addActionListener(evt -> btnCloseActionPerformed(evt));

        btnAddAce.setText("Add Access Control Entry...");
        btnAddAce.addActionListener(evt -> btnAddAceActionPerformed(evt));

        lblDigest.setText("Digest:");

        lblDigestAlgorithmValue.setText("<digest algorithm>");

        txtDigest.setEditable(false);
        txtDigest.setColumns(20);
        txtDigest.setLineWrap(true);
        txtDigest.setRows(5);
        txtDigest.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        spDigest.setViewportView(txtDigest);

        lblSize.setText("Size:");

        lblSizeValue.setText("<size>");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jSeparator1))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(25, 25, 25)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblAccessControlList)
                            .addComponent(btnAddAce)
                            .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 404, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 404, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(lblBasePermissions)
                            .addComponent(spDigest, javax.swing.GroupLayout.PREFERRED_SIZE, 404, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(lblResource)
                                    .addComponent(lblInternetMediaType)
                                    .addComponent(lblOwner)
                                    .addComponent(lblLastModified)
                                    .addComponent(lblCreated)
                                    .addComponent(lblGroup)
                                    .addComponent(lblSize)
                                    .addComponent(lblDigest))
                                .addGap(28, 28, 28)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(lblDigestAlgorithmValue, javax.swing.GroupLayout.PREFERRED_SIZE, 159, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(lblSizeValue)
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(lblGroupValue, javax.swing.GroupLayout.PREFERRED_SIZE, 216, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(btnChangeGroup, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(lblOwnerValue, javax.swing.GroupLayout.PREFERRED_SIZE, 216, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(btnChangeOwner, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                        .addComponent(lblResourceValue, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(lblInternetMediaTypeValue, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(lblCreatedValue, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(lblLastModifiedValue, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, 432, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(btnClose)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnSave)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(17, 17, 17)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblResource)
                    .addComponent(lblResourceValue))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblInternetMediaType)
                    .addComponent(lblInternetMediaTypeValue))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblCreated)
                    .addComponent(lblCreatedValue))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblLastModified)
                    .addComponent(lblLastModifiedValue))
                .addGap(12, 12, 12)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblSize)
                    .addComponent(lblSizeValue))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblDigest)
                    .addComponent(lblDigestAlgorithmValue))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(spDigest, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblOwner)
                    .addComponent(lblOwnerValue)
                    .addComponent(btnChangeOwner, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblGroup)
                    .addComponent(lblGroupValue)
                    .addComponent(btnChangeGroup, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addComponent(lblBasePermissions)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblAccessControlList)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnAddAce)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnClose)
                    .addComponent(btnSave))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSaveActionPerformed
        
        try {

            for (final ResourceDescriptor desc : applyTo) {

                final String newOwner;
                if (MULTIPLE_INDICATOR.equals(lblOwnerValue.getText()) || desc.getOwner().equals(lblOwnerValue.getText())) {
                    newOwner = desc.getOwner();
                } else {
                    newOwner = lblOwnerValue.getText();
                }

                final String newGroup;
                if (MULTIPLE_INDICATOR.equals(lblGroupValue.getText()) || desc.getGroup().equals(lblGroupValue.getText())) {
                    newGroup = desc.getGroup();
                } else {
                    newGroup = lblGroupValue.getText();
                }

                final Permission existingPermission = desc.getPermissions();
                final ModeDisplay modeChanges = getBasicPermissionsTableModel().getMode();
                final Permission updatedPermission = getUpdatedPermission(existingPermission, modeChanges);

                final List<ACEAider> dlgAces = new ArrayList<>();
                if (acl == null) {
                    if (existingPermission instanceof ACLPermission) {
                        final ACLPermission existingAclPermission = (ACLPermission)existingPermission;
                        for (int i = 0; i < existingAclPermission.getACECount(); i++) {
                            dlgAces.add(new ACEAider(existingAclPermission.getACEAccessType(i), existingAclPermission.getACETarget(i), existingAclPermission.getACEWho(i), existingAclPermission.getACEMode(i)));
                        }
                    }
                } else {
                    for (int i = 0; i < tblAcl.getRowCount(); i++) {
                        final ACLPermission.ACE_TARGET target = ACLPermission.ACE_TARGET.valueOf((String) getAclTableModel().getValueAt(i, 0));
                        final String who = (String) getAclTableModel().getValueAt(i, 1);
                        final ACLPermission.ACE_ACCESS_TYPE access = ACLPermission.ACE_ACCESS_TYPE.valueOf((String) getAclTableModel().getValueAt(i, 2));
                        int mode = 0;
                        if ((Boolean) tblAcl.getValueAt(i, 3)) {
                            mode |= Permission.READ;
                        }
                        if ((Boolean) tblAcl.getValueAt(i, 4)) {
                            mode |= Permission.WRITE;
                        }
                        if ((Boolean) tblAcl.getValueAt(i, 5)) {
                            mode |= Permission.EXECUTE;
                        }

                        dlgAces.add(new ACEAider(access, target, who, mode));
                    }
                }

                if (desc.isCollection()) {
                    final Collection coll = parent.getChildCollection(desc.getName().toString());
                    getUserManagementService().setPermissions(coll, newOwner, newGroup, updatedPermission.getMode(), dlgAces);
                } else {
                    final Resource res = parent.getResource(desc.getName().toString());
                    getUserManagementService().setPermissions(res, newOwner, newGroup, updatedPermission.getMode(), dlgAces);
                }
            }

            setVisible(false);
            dispose();
        } catch(final PermissionDeniedException | XMLDBException e) {
            JOptionPane.showMessageDialog(this, "Could not update properties: " + e.getMessage(), ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_btnSaveActionPerformed

    private void btnCloseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCloseActionPerformed
        setVisible(false);
        dispose();
    }//GEN-LAST:event_btnCloseActionPerformed

    private void btnChangeOwnerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnChangeOwnerActionPerformed
        final DialogCompleteWithResponse<String> callback = username -> lblOwnerValue.setText(username);
        
        try {
            final FindUserForm findUserForm = new FindUserForm(getUserManagementService());
            findUserForm.addDialogCompleteWithResponseCallback(callback);
            findUserForm.setTitle("Change Owner...");
            findUserForm.setVisible(true);
        } catch(final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not retrieve list of users: " + xmldbe.getMessage(), ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_btnChangeOwnerActionPerformed

    private void btnChangeGroupActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnChangeGroupActionPerformed
        final DialogCompleteWithResponse<String> callback = groupName -> lblGroupValue.setText(groupName);
        
        try {
            final FindGroupForm findGroupForm = new FindGroupForm(getUserManagementService());
            findGroupForm.addDialogCompleteWithResponseCallback(callback);
            findGroupForm.setTitle("Change Group...");
            findGroupForm.setVisible(true);
        } catch(final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not retrieve list of groups: " + xmldbe.getMessage(), ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_btnChangeGroupActionPerformed

    private void miRemoveAceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_miRemoveAceActionPerformed
        getAclTableModel().removeRow(tblAcl.getSelectedRow());
    }//GEN-LAST:event_miRemoveAceActionPerformed

    private void miMoveUpActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_miMoveUpActionPerformed
        if(tblAcl.getSelectedRow() > 0) {
            getAclTableModel().moveRow(tblAcl.getSelectedRow(), tblAcl.getSelectedRow(), tblAcl.getSelectedRow() - 1);
        }
    }//GEN-LAST:event_miMoveUpActionPerformed

    private void miMoveDownActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_miMoveDownActionPerformed
        if(tblAcl.getSelectedRow() < getAclTableModel().getRowCount() - 1) {
            getAclTableModel().moveRow(tblAcl.getSelectedRow(), tblAcl.getSelectedRow(), tblAcl.getSelectedRow() + 1);
        }
    }//GEN-LAST:event_miMoveDownActionPerformed

    private void btnAddAceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddAceActionPerformed
        
        final DialogCompleteWithResponse<ACEAider> callback = ace -> getAclTableModel().addRow(new Object[]{
            ace.getTarget().toString(),
            ace.getWho(),
            ace.getAccessType().toString(),
            (ace.getMode() & Permission.READ) == Permission.READ,
            (ace.getMode() & Permission.WRITE) == Permission.WRITE,
            (ace.getMode() & Permission.EXECUTE) == Permission.EXECUTE,
        });
        
        try {
            final AccessControlEntryDialog aceDialog = new AccessControlEntryDialog(getUserManagementService(), "Create Access Control Entry");
            aceDialog.addDialogCompleteWithResponseCallback(callback);
            aceDialog.setVisible(true);
        } catch(final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not get user/group members: " + xmldbe.getMessage(), ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_btnAddAceActionPerformed

    private void miInsertAceBeforeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_miInsertAceBeforeActionPerformed
        final DialogCompleteWithResponse<ACEAider> callback = ace -> {
            final int insertAt = tblAcl.getSelectedRow();
            getAclTableModel().insertRow(insertAt, new Object[]{
                ace.getTarget().toString(),
                ace.getWho(),
                ace.getAccessType().toString(),
                (ace.getMode() & Permission.READ) == Permission.READ,
                (ace.getMode() & Permission.WRITE) == Permission.WRITE,
                (ace.getMode() & Permission.EXECUTE) == Permission.EXECUTE,
            });
        };
        
        try {
            final AccessControlEntryDialog aceDialog = new AccessControlEntryDialog(getUserManagementService(), "Insert Access Control Entry (before...)");
            aceDialog.addDialogCompleteWithResponseCallback(callback);
            aceDialog.setVisible(true);
        } catch(final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not get user/group members: " + xmldbe.getMessage(), ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_miInsertAceBeforeActionPerformed

    private void miInsertAceAfterActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_miInsertAceAfterActionPerformed
        final DialogCompleteWithResponse<ACEAider> callback = ace -> {
            final int insertAt = tblAcl.getSelectedRow() < getAclTableModel().getRowCount() - 1 ? tblAcl.getSelectedRow() + 1 : getAclTableModel().getRowCount();
            getAclTableModel().insertRow(insertAt, new Object[]{
                ace.getTarget().toString(),
                ace.getWho(),
                ace.getAccessType().toString(),
                (ace.getMode() & Permission.READ) == Permission.READ,
                (ace.getMode() & Permission.WRITE) == Permission.WRITE,
                (ace.getMode() & Permission.EXECUTE) == Permission.EXECUTE,
            });
        };
        
        try {
            final AccessControlEntryDialog aceDialog = new AccessControlEntryDialog(getUserManagementService(), "Insert Access Control Entry (after...)");
            aceDialog.addDialogCompleteWithResponseCallback(callback);
            aceDialog.setVisible(true);
        } catch(final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not get user/group members: " + xmldbe.getMessage(), ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_miInsertAceAfterActionPerformed

    private Permission getUpdatedPermission(final Permission existingMode, final ModeDisplay modeChanges) throws PermissionDeniedException {
        final Permission newMode = existingMode.copy();
        modeChanges.writeToPermission(newMode);
        return newMode;
    }

    private boolean isDba() throws XMLDBException {
        final Set<String> dbaMembers = new HashSet<>(Arrays.asList(getUserManagementService().getGroupMembers(SecurityManager.DBA_GROUP)));
        return dbaMembers.contains(currentUser);
    }
    
    private boolean canModifyPermissions() throws XMLDBException {
        return isDba() || owner.equals(currentUser);
    }
    
    private void tblAclMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_tblAclMouseClicked
        final boolean aclSelected = tblAcl.getSelectedRow() > -1;
        try {
            final boolean canModify = canModifyPermissions();
        
            miInsertAceBefore.setEnabled(canModify);
            miInsertAceAfter.setEnabled(canModify);
        
            miMoveUp.setEnabled(canModify && aclSelected);
            miMoveDown.setEnabled(canModify && aclSelected);
        
            miRemoveAce.setEnabled(canModify && aclSelected);
        } catch(final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not get dba group members: " + xmldbe.getMessage(), ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_tblAclMouseClicked

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnAddAce;
    private javax.swing.JButton btnChangeGroup;
    private javax.swing.JButton btnChangeOwner;
    private javax.swing.JButton btnClose;
    private javax.swing.JButton btnSave;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JPopupMenu.Separator jSeparator3;
    private javax.swing.JPopupMenu.Separator jSeparator4;
    private javax.swing.JTextArea jTextArea1;
    private javax.swing.JLabel lblAccessControlList;
    private javax.swing.JLabel lblBasePermissions;
    private javax.swing.JLabel lblCreated;
    private javax.swing.JLabel lblCreatedValue;
    private javax.swing.JLabel lblDigest;
    private javax.swing.JLabel lblDigestAlgorithmValue;
    private javax.swing.JLabel lblGroup;
    private javax.swing.JLabel lblGroupValue;
    private javax.swing.JLabel lblInternetMediaType;
    private javax.swing.JLabel lblInternetMediaTypeValue;
    private javax.swing.JLabel lblLastModified;
    private javax.swing.JLabel lblLastModifiedValue;
    private javax.swing.JLabel lblOwner;
    private javax.swing.JLabel lblOwnerValue;
    private javax.swing.JLabel lblResource;
    private javax.swing.JLabel lblResourceValue;
    private javax.swing.JLabel lblSize;
    private javax.swing.JLabel lblSizeValue;
    private javax.swing.JMenuItem miInsertAceAfter;
    private javax.swing.JMenuItem miInsertAceBefore;
    private javax.swing.JMenuItem miMoveDown;
    private javax.swing.JMenuItem miMoveUp;
    private javax.swing.JMenuItem miRemoveAce;
    private javax.swing.JPopupMenu pmAcl;
    private javax.swing.JScrollPane spDigest;
    private javax.swing.JTable tblAcl;
    private javax.swing.JTable tblBasePermissions;
    private javax.swing.JTextArea txtDigest;
    // End of variables declaration//GEN-END:variables
}
