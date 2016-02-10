/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2013 The eXist Project
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

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JOptionPane;
import javax.swing.table.DefaultTableModel;
import org.exist.client.DialogCompleteWithResponse;
import org.exist.client.InteractiveClient;
import org.exist.client.LabelledBoolean;
import org.exist.client.LabelledBooleanEditor;
import org.exist.client.LabelledBooleanRenderer;
import org.exist.client.ResourceDescriptor;
import org.exist.security.ACLPermission;
import org.exist.security.ACLPermission.ACE_ACCESS_TYPE;
import org.exist.security.ACLPermission.ACE_TARGET;
import org.exist.security.Permission;
import org.exist.security.SecurityManager;
import org.exist.security.internal.aider.ACEAider;
import org.exist.security.internal.aider.PermissionAider;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.util.URIUtils;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class EditPropertiesDialog extends javax.swing.JFrame {
    private final UserManagementService userManagementService;
    private final String currentUser;
    private final Collection parent;
    private final XmldbURI uri;
    private final String internetMediaType;
    private final Date created;
    private final Date lastModified;
    private final PermissionAider permission;
    private final List<ResourceDescriptor> applyTo;
    
    private BasicPermissionsTableModel basicPermissionsTableModel = null;
    private DefaultTableModel aclTableModel = null;

    private final static String ERROR_TITLE = "Edit Properties Error";
    
    /**
     * Creates new form PropertiesDialog
     */
    public EditPropertiesDialog(final UserManagementService userManagementService, final String currentUser, final Collection parent, final XmldbURI uri, final String internetMediaType, final Date created, final Date lastModified, final PermissionAider permission, final List<ResourceDescriptor> applyTo) {
        this.userManagementService = userManagementService;
        this.currentUser = currentUser;
        this.parent = parent;
        this.uri = uri;
        this.internetMediaType = internetMediaType;
        this.created = created;
        this.lastModified = lastModified;
        this.permission = permission;
        this.applyTo = applyTo;
        this.setIconImage(InteractiveClient.getExistIcon(getClass()).getImage());        
        initComponents();
        setFormProperties();
    }

    private void setFormProperties() {
        lblResourceValue.setText(URIUtils.urlDecodeUtf8(uri));
        lblInternetMediaTypeValue.setText(internetMediaType != null ? internetMediaType : "N/A");
        lblCreatedValue.setText(DateFormat.getDateTimeInstance().format(created));
        lblLastModifiedValue.setText(lastModified != null ? DateFormat.getDateTimeInstance().format(lastModified) : "N/A");
        
        lblOwnerValue.setText(permission.getOwner().getName());
        //final Point pntLblOwnerValue = lblOwnerValue.getLocation();
        //btnChangeOwner.setLocation(pntLblOwnerValue.x + (lblOwnerValue.getText().length() * 4), pntLblOwnerValue.y);
        
        lblGroupValue.setText(permission.getGroup().getName());
        //final Point pntLblGroupValue = lblGroupValue.getLocation();
        //btnChangeGroup.setLocation(pntLblGroupValue.x + (lblGroupValue.getText().length() * 4), pntLblGroupValue.y);
        
        try {
            final boolean canModify = canModifyPermissions();
        
            btnChangeOwner.setEnabled(isDba());
            btnChangeGroup.setEnabled(isDba());
            
            tblBasePermissions.setEnabled(canModify);
            
            if(!(permission instanceof ACLPermission)) {
                tblAcl.setEnabled(false);
            } else {
                tblAcl.setEnabled(canModify);
            }
            
            miInsertAceBefore.setEnabled(canModify);
            miInsertAceAfter.setEnabled(canModify);
            btnAddAce.setEnabled(canModify);
        
            miMoveUp.setEnabled(false);
            miMoveDown.setEnabled(false);
        
            miRemoveAce.setEnabled(false);
        } catch(final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not get dba group members: " + xmldbe.getMessage(), ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private BasicPermissionsTableModel getBasicPermissionsTableModel() {
        if(basicPermissionsTableModel == null) {
            basicPermissionsTableModel = new BasicPermissionsTableModel(permission);
        }
        
        return basicPermissionsTableModel;
    }
    
    private DefaultTableModel getAclTableModel() {
        if(aclTableModel == null) {
            aclTableModel = new AclTableModel(permission);
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
        tblBasePermissions.setDefaultRenderer(LabelledBoolean.class, new LabelledBooleanRenderer());
        tblBasePermissions.setDefaultEditor(LabelledBoolean.class, new LabelledBooleanEditor());
        lblAccessControlList = new javax.swing.JLabel();
        jSeparator1 = new javax.swing.JSeparator();
        lblBasePermissions = new javax.swing.JLabel();
        jScrollPane3 = new javax.swing.JScrollPane();
        tblAcl = new javax.swing.JTable();
        jSeparator2 = new javax.swing.JSeparator();
        btnSave = new javax.swing.JButton();
        btnClose = new javax.swing.JButton();
        btnAddAce = new javax.swing.JButton();

        miInsertAceBefore.setText("Insert ACE before...");
        miInsertAceBefore.addActionListener(this::miInsertAceBeforeActionPerformed);
        pmAcl.add(miInsertAceBefore);

        miInsertAceAfter.setText("Insert ACE after...");
        miInsertAceAfter.addActionListener(this::miInsertAceAfterActionPerformed);
        pmAcl.add(miInsertAceAfter);
        pmAcl.add(jSeparator3);

        miMoveUp.setText("Move ACE up");
        miMoveUp.addActionListener(this::miMoveUpActionPerformed);
        pmAcl.add(miMoveUp);

        miMoveDown.setText("Move ACE down");
        miMoveDown.addActionListener(this::miMoveDownActionPerformed);
        pmAcl.add(miMoveDown);
        pmAcl.add(jSeparator4);

        miRemoveAce.setText("Remove ACE");
        miRemoveAce.addActionListener(this::miRemoveAceActionPerformed);
        pmAcl.add(miRemoveAce);

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
        btnChangeOwner.addActionListener(this::btnChangeOwnerActionPerformed);

        btnChangeGroup.setText("...");
        btnChangeGroup.addActionListener(this::btnChangeGroupActionPerformed);

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
        btnSave.addActionListener(this::btnSaveActionPerformed);

        btnClose.setText("Close");
        btnClose.addActionListener(this::btnCloseActionPerformed);

        btnAddAce.setText("Add Access Control Entry...");
        btnAddAce.addActionListener(this::btnAddAceActionPerformed);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jSeparator1))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(25, 25, 25)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblBasePermissions)
                            .addComponent(lblAccessControlList)
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                        .addGroup(layout.createSequentialGroup()
                                            .addComponent(lblOwner)
                                            .addGap(112, 112, 112))
                                        .addComponent(lblLastModified, javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(lblCreated, javax.swing.GroupLayout.Alignment.LEADING))
                                    .addGroup(layout.createSequentialGroup()
                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(lblResource)
                                            .addComponent(lblInternetMediaType)
                                            .addComponent(lblGroup))
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)))
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                        .addComponent(lblResourceValue, javax.swing.GroupLayout.DEFAULT_SIZE, 251, Short.MAX_VALUE)
                                        .addComponent(lblInternetMediaTypeValue, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(lblCreatedValue, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(lblLastModifiedValue, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(lblGroupValue, javax.swing.GroupLayout.PREFERRED_SIZE, 216, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(btnChangeGroup, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(lblOwnerValue, javax.swing.GroupLayout.PREFERRED_SIZE, 216, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(btnChangeOwner, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))))
                            .addComponent(btnAddAce)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                .addComponent(jScrollPane2, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                                .addComponent(jScrollPane3, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 404, Short.MAX_VALUE)))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jSeparator2, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(btnClose)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnSave)))
                .addContainerGap())
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
                .addGap(14, 14, 14)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblOwner)
                    .addComponent(lblOwnerValue)
                    .addComponent(btnChangeOwner, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblGroup)
                    .addComponent(lblGroupValue)
                    .addComponent(btnChangeGroup, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lblBasePermissions)
                .addGap(5, 5, 5)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lblAccessControlList)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnAddAce)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnClose)
                    .addComponent(btnSave))
                .addGap(20, 20, 20))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSaveActionPerformed
        
        try {
            final List<ACEAider> dlgAces = new ArrayList<ACEAider>();
            if(permission instanceof ACLPermission) {
                for(int i = 0; i < tblAcl.getRowCount(); i++) {
                    final ACE_TARGET target = ACE_TARGET.valueOf((String)getAclTableModel().getValueAt(i, 0));
                    final String who = (String)getAclTableModel().getValueAt(i, 1);
                    final ACE_ACCESS_TYPE access = ACE_ACCESS_TYPE.valueOf((String)getAclTableModel().getValueAt(i, 2));
                    int mode = 0;
                    if((Boolean)tblAcl.getValueAt(i, 3)) {
                        mode |= Permission.READ;
                    }
                    if((Boolean)tblAcl.getValueAt(i, 4)) {
                        mode |= Permission.WRITE;
                    }
                    if((Boolean)tblAcl.getValueAt(i, 5)) {
                        mode |= Permission.EXECUTE;
                    }
                    
                    dlgAces.add(new ACEAider(access, target, who, mode));
                }
            }

            for(final ResourceDescriptor desc : applyTo) {
                if (desc.isCollection()) {
                    final Collection coll = parent.getChildCollection(desc.getName().toString());
                    getUserManagementService().setPermissions(coll, lblOwnerValue.getText(), lblGroupValue.getText(), getBasicPermissionsTableModel().getMode(), dlgAces);
                } else {
                    final Resource res = parent.getResource(desc.getName().toString());
                    getUserManagementService().setPermissions(res, lblOwnerValue.getText(), lblGroupValue.getText(), getBasicPermissionsTableModel().getMode(), dlgAces);
                }
            }

            setVisible(false);
            dispose();
        } catch(final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not update properties: " + xmldbe.getMessage(), ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
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

    private boolean isDba() throws XMLDBException {
        final Set<String> dbaMembers = new HashSet<String>(Arrays.asList(getUserManagementService().getGroupMembers(SecurityManager.DBA_GROUP)));
        return dbaMembers.contains(currentUser);
    }
    
    private boolean canModifyPermissions() throws XMLDBException {
        return isDba() || permission.getOwner().getName().equals(currentUser);
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
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JPopupMenu.Separator jSeparator3;
    private javax.swing.JPopupMenu.Separator jSeparator4;
    private javax.swing.JLabel lblAccessControlList;
    private javax.swing.JLabel lblBasePermissions;
    private javax.swing.JLabel lblCreated;
    private javax.swing.JLabel lblCreatedValue;
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
    private javax.swing.JMenuItem miInsertAceAfter;
    private javax.swing.JMenuItem miInsertAceBefore;
    private javax.swing.JMenuItem miMoveDown;
    private javax.swing.JMenuItem miMoveUp;
    private javax.swing.JMenuItem miRemoveAce;
    private javax.swing.JPopupMenu pmAcl;
    private javax.swing.JTable tblAcl;
    private javax.swing.JTable tblBasePermissions;
    // End of variables declaration//GEN-END:variables
}
