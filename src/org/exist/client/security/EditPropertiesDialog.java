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

import java.awt.Point;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.table.DefaultTableModel;
import org.exist.client.ResourceDescriptor;
import org.exist.security.ACLPermission;
import org.exist.security.Permission;
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
public class EditPropertiesDialog extends javax.swing.JFrame implements DialogWithResponse<Void> {
    private final UserManagementService userManagementService;
    private final Collection parent;
    private final XmldbURI uri;
    private final String internetMediaType;
    private final Date created;
    private final Date lastModified;
    private final PermissionAider permission;
    private final List<ResourceDescriptor> applyTo;
    
    private final List<DialogCompleteWithResponse<Void>> dialogCompleteWithResponseCallbacks = new ArrayList<DialogCompleteWithResponse<Void>>();
    
    private DefaultTableModel basicPermissionsTableModel = null;

    /**
     * Creates new form PropertiesDialog
     */
    public EditPropertiesDialog(final UserManagementService userManagementService, final Collection parent, final XmldbURI uri, final String internetMediaType, final Date created, final Date lastModified, final PermissionAider permission, final List<ResourceDescriptor> applyTo) {
        this.userManagementService = userManagementService;
        this.parent = parent;
        this.uri = uri;
        this.internetMediaType = internetMediaType;
        this.created = created;
        this.lastModified = lastModified;
        this.permission = permission;
        this.applyTo = applyTo;
        
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
    }
    
    private DefaultTableModel getBasicPermissionsTableModel() {
        if(basicPermissionsTableModel == null) {
            
            final Object[][] basicPermissions = new Object [][] {
                {"Read", (permission.getOwnerMode() & Permission.READ) == Permission.READ, (permission.getGroupMode() & Permission.READ) == Permission.READ, (permission.getOtherMode() & Permission.READ) == Permission.READ},
                {"Write", (permission.getOwnerMode() & Permission.WRITE) == Permission.WRITE, (permission.getGroupMode() & Permission.WRITE) == Permission.WRITE, (permission.getOtherMode() & Permission.WRITE) == Permission.WRITE},
                {"Execute", (permission.getOwnerMode() & Permission.EXECUTE) == Permission.EXECUTE, (permission.getGroupMode() & Permission.EXECUTE) == Permission.EXECUTE, (permission.getOtherMode() & Permission.EXECUTE) == Permission.EXECUTE}
            };
            
            basicPermissionsTableModel = new javax.swing.table.DefaultTableModel(
                basicPermissions,
                new String [] {
                    "Permission", "User", "Group", "Other"
                }
            ) {
                final Class[] types = new Class [] {
                    java.lang.String.class, java.lang.Boolean.class, java.lang.Boolean.class, java.lang.Boolean.class
                };
                
                boolean[] canEdit = new boolean [] {
                    false, true, true, true
                };

                @Override
                public Class getColumnClass(int columnIndex) {
                    return types [columnIndex];
                }

                @Override
                public boolean isCellEditable(int rowIndex, int columnIndex) {
                    return canEdit [columnIndex];
                }
            };
        }
        
        return basicPermissionsTableModel;
    }
    
    private int getBasicMode() {
        int mode = 0;
        
        int right = Permission.READ;
        for(int i = 0; i < getBasicPermissionsTableModel().getRowCount(); i++) {
            if((Boolean)getBasicPermissionsTableModel().getValueAt(i, 1)) {
                mode |= (right << 6);
            }
            if((Boolean)getBasicPermissionsTableModel().getValueAt(i, 2)) {
                mode |= (right << 3);
            }
            if((Boolean)getBasicPermissionsTableModel().getValueAt(i, 3)) {
                mode |= right;
            }
            right = right / 2;
        }
        
        return mode;
    }

    private List<DialogCompleteWithResponse<Void>> getDialogCompleteWithResponseCallbacks() {
        return dialogCompleteWithResponseCallbacks;
    }

    @Override
    public void addDialogCompleteWithResponseCallback(final DialogCompleteWithResponse<Void> dialogCompleteWithResponseCallback) {
        getDialogCompleteWithResponseCallbacks().add(dialogCompleteWithResponseCallback);
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

        jScrollPane1 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();
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
        lblAccessControlList = new javax.swing.JLabel();
        jSeparator1 = new javax.swing.JSeparator();
        lblBasePermissions = new javax.swing.JLabel();
        jScrollPane3 = new javax.swing.JScrollPane();
        tblAcl = new javax.swing.JTable();
        jSeparator2 = new javax.swing.JSeparator();
        btnSave = new javax.swing.JButton();
        btnClose = new javax.swing.JButton();

        jTable1.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        jScrollPane1.setViewportView(jTable1);

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Properties");

        lblResource.setText("Resource:");

        lblInternetMediaType.setText("Internet Media Type:");

        lblCreated.setText("Created:");

        lblLastModified.setText("Last Modified:");

        lblOwner.setText("Owner:");

        lblGroup.setText("Group:");

        lblResourceValue.setText("jLabel6");

        lblInternetMediaTypeValue.setText("jLabel6");

        lblCreatedValue.setText("jLabel6");

        lblLastModifiedValue.setText("jLabel6");

        lblOwnerValue.setText("jLabel6");

        lblGroupValue.setText("jLabel6");

        btnChangeOwner.setText("...");
        btnChangeOwner.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnChangeOwnerActionPerformed(evt);
            }
        });

        btnChangeGroup.setText("...");
        btnChangeGroup.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnChangeGroupActionPerformed(evt);
            }
        });

        tblBasePermissions.setModel(getBasicPermissionsTableModel());
        jScrollPane2.setViewportView(tblBasePermissions);
        tblBasePermissions.getColumnModel().getColumn(0).setResizable(false);

        lblAccessControlList.setText("Access Control List");

        lblBasePermissions.setText("Base Permissions");

        tblAcl.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null, null, null},
                {null, null, null, null, null, null},
                {null, null, null, null, null, null},
                {null, null, null, null, null, null}
            },
            new String [] {
                "Target", "Subject", "Access", "Read", "Write", "Execute"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.Object.class, java.lang.Object.class, java.lang.Boolean.class, java.lang.Boolean.class, java.lang.Boolean.class
            };
            boolean[] canEdit = new boolean [] {
                false, true, true, true, true, true
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jScrollPane3.setViewportView(tblAcl);

        btnSave.setText("Save");
        btnSave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSaveActionPerformed(evt);
            }
        });

        btnClose.setText("Close");
        btnClose.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCloseActionPerformed(evt);
            }
        });

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
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 298, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 404, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(0, 15, Short.MAX_VALUE))
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
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 68, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lblAccessControlList)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnSave)
                    .addComponent(btnClose))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSaveActionPerformed
        
        try {
            final List<ACEAider> dlgAces = new ArrayList<ACEAider>();
            if(permission instanceof ACLPermission) {
                final ACLPermission dlgAclPerm = (ACLPermission)permission;
                for(int j = 0; j < dlgAclPerm.getACECount(); j++) {
                    dlgAces.add(new ACEAider(dlgAclPerm.getACEAccessType(j), dlgAclPerm.getACETarget(j), dlgAclPerm.getACEWho(j), dlgAclPerm.getACEMode(j)));
                }
            }

            for(final ResourceDescriptor desc : applyTo) {
                if (desc.isCollection()) {
                    final Collection coll = parent.getChildCollection(desc.getName().toString());
                    getUserManagementService().setPermissions(coll, lblOwnerValue.getText(), lblGroupValue.getText(), getBasicMode(), dlgAces);
                } else {
                    final Resource res = parent.getResource(desc.getName().toString());
                    getUserManagementService().setPermissions(res, lblOwnerValue.getText(), lblGroupValue.getText(), getBasicMode(), dlgAces);
                }
            }

            for(final DialogCompleteWithResponse<Void> callback : getDialogCompleteWithResponseCallbacks()) {
                callback.complete(null);
            }
            
            setVisible(false);
            dispose();
        } catch(final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not update properties: " + xmldbe.getMessage(), "Edit Properties Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
    }//GEN-LAST:event_btnSaveActionPerformed

    private void btnCloseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCloseActionPerformed
        setVisible(false);
        dispose();
    }//GEN-LAST:event_btnCloseActionPerformed

    private void btnChangeOwnerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnChangeOwnerActionPerformed
        final DialogCompleteWithResponse<String> callback = new DialogCompleteWithResponse<String>(){
            public void complete(final String username) {
                lblOwnerValue.setText(username);
            }
        };
        
        try {
            final FindUserForm findUserForm = new FindUserForm(getUserManagementService());
            findUserForm.addDialogCompleteWithResponseCallback(callback);
            findUserForm.setTitle("Change Owner...");
            findUserForm.setVisible(true);
        } catch(final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not retrieve list of users: " + xmldbe.getMessage(), "Edit Properties Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
    }//GEN-LAST:event_btnChangeOwnerActionPerformed

    private void btnChangeGroupActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnChangeGroupActionPerformed
        final DialogCompleteWithResponse<String> callback = new DialogCompleteWithResponse<String>(){
            public void complete(final String groupName) {
                lblGroupValue.setText(groupName);
            }
        };
        
        try {
            final FindGroupForm findGroupForm = new FindGroupForm(getUserManagementService());
            findGroupForm.addDialogCompleteWithResponseCallback(callback);
            findGroupForm.setTitle("Change Group...");
            findGroupForm.setVisible(true);
        } catch(final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not retrieve list of groups: " + xmldbe.getMessage(), "Edit Properties Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
    }//GEN-LAST:event_btnChangeGroupActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnChangeGroup;
    private javax.swing.JButton btnChangeOwner;
    private javax.swing.JButton btnClose;
    private javax.swing.JButton btnSave;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JTable jTable1;
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
    private javax.swing.JTable tblAcl;
    private javax.swing.JTable tblBasePermissions;
    // End of variables declaration//GEN-END:variables
}
