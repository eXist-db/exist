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

import java.util.Iterator;
import java.util.regex.Pattern;
import javax.swing.InputVerifier;
import javax.swing.JOptionPane;
import org.exist.security.AXSchemaType;
import org.exist.security.EXistSchemaType;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.xmldb.UserManagementService;
import org.xmldb.api.base.XMLDBException;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class UserDialog extends javax.swing.JFrame {

    private static final long serialVersionUID = -7544980948396443454L;

    private final Pattern PTN_USERNAME = Pattern.compile("[a-zA-Z0-9\\-\\._]{3,}");
    private final Pattern PTN_PASSWORD = Pattern.compile(".{3,}");
    private final UserManagementService userManagementService;
    private SortedListModel<String> availableGroupsModel = null;
    private SortedListModel<String> memberOfGroupsModel = null;
    
    /**
     * Creates new form UserDialog
     */
    public UserDialog(final UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
        initComponents();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jSeparator1 = new javax.swing.JSeparator();
        lblUsername = new javax.swing.JLabel();
        txtUsername = new javax.swing.JTextField();
        lblFullName = new javax.swing.JLabel();
        txtFullName = new javax.swing.JTextField();
        lblDescription = new javax.swing.JLabel();
        txtDescription = new javax.swing.JTextField();
        lblPassword = new javax.swing.JLabel();
        txtPassword = new javax.swing.JPasswordField();
        lblPasswordConfirm = new javax.swing.JLabel();
        txtPasswordConfirm = new javax.swing.JPasswordField();
        cbDisabled = new javax.swing.JCheckBox();
        jSeparator2 = new javax.swing.JSeparator();
        spnUmask = new javax.swing.JSpinner();
        lblUmask = new javax.swing.JLabel();
        jSeparator3 = new javax.swing.JSeparator();
        btnClose = new javax.swing.JButton();
        btnCreate = new javax.swing.JButton();
        cbPersonalGroup = new javax.swing.JCheckBox();
        jScrollPane1 = new javax.swing.JScrollPane();
        lstMemberOfGroups = new javax.swing.JList();
        lblMemberOfGroups = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        lstAvailableGroups = new javax.swing.JList();
        lblAvailableGroups = new javax.swing.JLabel();
        btnAddGroup = new javax.swing.JButton();
        btnRemoveGroup = new javax.swing.JButton();
        jSeparator4 = new javax.swing.JSeparator();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("New User");

        lblUsername.setText("User name:");

        txtUsername.setInputVerifier(getUsernameInputVerifier());

        lblFullName.setText("Full name:");

        lblDescription.setText("Description:");

        lblPassword.setText("Password:");

        txtPassword.setInputVerifier(getPasswordInputVerifier());

        lblPasswordConfirm.setText("Confirm password:");

        txtPasswordConfirm.setInputVerifier(getPasswordInputVerifier());

        cbDisabled.setText("Account is disabled");

        spnUmask.setModel(new UmaskSpinnerModel());
        spnUmask.setEditor(new UmaskEditor(spnUmask));
        spnUmask.setValue(getUmask());

        lblUmask.setText("umask:");

        btnClose.setText("Close");
        btnClose.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCloseActionPerformed(evt);
            }
        });

        btnCreate.setText("Create");
        btnCreate.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCreateActionPerformed(evt);
            }
        });

        cbPersonalGroup.setSelected(true);
        cbPersonalGroup.setText("Create personal user group");

        lstMemberOfGroups.setModel(getMemberOfGroupsListModel());
        jScrollPane1.setViewportView(lstMemberOfGroups);

        lblMemberOfGroups.setText("Member of Groups:");

        lstAvailableGroups.setModel(getAvailableGroupsListModel());
        jScrollPane2.setViewportView(lstAvailableGroups);

        lblAvailableGroups.setText("Available Groups:");

        btnAddGroup.setText("->");
        btnAddGroup.setToolTipText("Add to Group");
        btnAddGroup.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddGroupActionPerformed(evt);
            }
        });

        btnRemoveGroup.setText("<-");
        btnRemoveGroup.setToolTipText("Remove from Group");
        btnRemoveGroup.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRemoveGroupActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(btnClose)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(btnCreate)
                                .addGap(6, 6, 6))
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createSequentialGroup()
                                        .addGap(6, 6, 6)
                                        .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 177, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                            .addComponent(btnAddGroup, javax.swing.GroupLayout.PREFERRED_SIZE, 58, javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addComponent(btnRemoveGroup, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)))
                                    .addComponent(lblAvailableGroups))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(lblMemberOfGroups)
                                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 177, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(25, 25, 25)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                            .addComponent(lblDescription)
                                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED))
                                        .addGroup(layout.createSequentialGroup()
                                            .addComponent(lblFullName)
                                            .addGap(24, 24, 24)))
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(lblUsername)
                                        .addGap(3, 3, 3)))
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                    .addComponent(txtUsername, javax.swing.GroupLayout.DEFAULT_SIZE, 296, Short.MAX_VALUE)
                                    .addComponent(txtFullName, javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(txtDescription, javax.swing.GroupLayout.Alignment.LEADING)))
                            .addGroup(layout.createSequentialGroup()
                                .addGap(24, 24, 24)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(lblPasswordConfirm)
                                    .addComponent(lblPassword))
                                .addGap(18, 18, 18)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(txtPassword, javax.swing.GroupLayout.DEFAULT_SIZE, 248, Short.MAX_VALUE)
                                    .addComponent(txtPasswordConfirm))))
                        .addGap(0, 35, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jSeparator4)
                            .addComponent(jSeparator1, javax.swing.GroupLayout.Alignment.LEADING))))
                .addContainerGap())
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(16, 16, 16)
                        .addComponent(lblUmask)
                        .addGap(18, 18, 18)
                        .addComponent(spnUmask, javax.swing.GroupLayout.PREFERRED_SIZE, 76, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jSeparator2)
                            .addComponent(jSeparator3))
                        .addContainerGap())))
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(16, 16, 16)
                        .addComponent(cbDisabled))
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(cbPersonalGroup)))
                .addGap(0, 0, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGap(22, 22, 22)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblUsername)
                    .addComponent(txtUsername, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblFullName)
                    .addComponent(txtFullName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblDescription)
                    .addComponent(txtDescription, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblPassword)
                    .addComponent(txtPassword, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtPasswordConfirm, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblPasswordConfirm))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cbDisabled)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(spnUmask, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblUmask))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jSeparator3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cbPersonalGroup)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblMemberOfGroups, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(lblAvailableGroups))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(52, 52, 52)
                        .addComponent(btnAddGroup)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnRemoveGroup)))
                .addGap(10, 10, 10)
                .addComponent(jSeparator4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(btnClose)
                    .addComponent(btnCreate))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnCloseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCloseActionPerformed
        setVisible(false);
        dispose();
    }//GEN-LAST:event_btnCloseActionPerformed

    private void btnCreateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCreateActionPerformed
        if(!isValidUserDetails()) {
            return;
        }
        
        //create the user
        createUser();
        
        //close the dialog
        setVisible(false);
        dispose();
    }//GEN-LAST:event_btnCreateActionPerformed

    protected void createUser() {
        //1 - create personal group
        GroupAider groupAider = null;
        if(cbPersonalGroup.isSelected()) {
            groupAider = new GroupAider(txtUsername.getText());
            groupAider.setMetadataValue(EXistSchemaType.DESCRIPTION, "Personal group for " + txtUsername.getText());
            try {
                getUserManagementService().addGroup(groupAider);
            } catch(final XMLDBException xmldbe) {
                JOptionPane.showMessageDialog(this, "Could not create personal group '" + txtUsername.getText() + "': " + xmldbe.getMessage(), "Create User Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        
        //2 - create the user
        final UserAider userAider = new UserAider(txtUsername.getText());
        userAider.setMetadataValue(AXSchemaType.FULLNAME, txtFullName.getText());
        userAider.setMetadataValue(EXistSchemaType.DESCRIPTION, txtDescription.getText());
        userAider.setPassword(txtPassword.getText());
        userAider.setEnabled(!cbDisabled.isSelected());
        userAider.setUserMask(UmaskSpinnerModel.octalUmaskToInt((String)spnUmask.getValue()));
        
        //add the personal group to the user
        if(cbPersonalGroup.isSelected()) {
            userAider.addGroup(txtUsername.getText());
        }
        
        //add any other groups to the user
        final Iterator<String> itMemberOfGroups = memberOfGroupsModel.iterator();
        while(itMemberOfGroups.hasNext()) {
            final String memberOfGroup = itMemberOfGroups.next();
            userAider.addGroup(memberOfGroup);
        }
        
        try {
            getUserManagementService().addAccount(userAider);
        } catch(XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not create user '" + txtUsername.getText() + "': " + xmldbe.getMessage(), "Create User Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        //3 - if created personal group, then add us as the manager
        if(cbPersonalGroup.isSelected()) {
            try {
                groupAider.addManager(userAider);
                getUserManagementService().updateGroup(groupAider);
            } catch(XMLDBException xmldbe) {
                JOptionPane.showMessageDialog(this, "Could not set user '" + txtUsername.getText() + "' as manager of personal group '" + txtUsername.getText() + "': " + xmldbe.getMessage(), "Create User Error", JOptionPane.ERROR_MESSAGE);
                return;
            } catch(PermissionDeniedException pde) {
                JOptionPane.showMessageDialog(this, "Could not set user '" + txtUsername.getText() + "' as manager of personal group '" + txtUsername.getText() + "': " + pde.getMessage(), "Create User Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
    }
    
    private void btnAddGroupActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddGroupActionPerformed
        for(final Object value: lstAvailableGroups.getSelectedValues()) {
            memberOfGroupsModel.add(value.toString());
            availableGroupsModel.removeElement(value.toString());
        }
    }//GEN-LAST:event_btnAddGroupActionPerformed

    private void btnRemoveGroupActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRemoveGroupActionPerformed
        for(final Object value: lstMemberOfGroups.getSelectedValues()) {
            availableGroupsModel.add(value.toString());
            memberOfGroupsModel.removeElement(value.toString());
        }
    }//GEN-LAST:event_btnRemoveGroupActionPerformed

    private boolean isValidUserDetails() {
        return isValidUsername() && isValidPassword() && isValidGroups();
    }
    
    private boolean isValidUsername() {
        if(PTN_USERNAME.matcher(txtUsername.getText()).matches()) {
            return true;
        }
        
        JOptionPane.showMessageDialog(this, "Username must be at least 3 characters (" + PTN_USERNAME.toString() + ")");
        return false;
    }
    
    private boolean isValidPassword() {
        if(txtPassword != null && PTN_PASSWORD.matcher(txtPassword.getText()).matches() && txtPassword.getText().equals(txtPasswordConfirm.getText())) {
            return true;
        }
        
        JOptionPane.showMessageDialog(this, "Passwords do not match or are less than 3 characters.");
        return false;
    }
    
    private boolean isValidGroups() {
        if(cbPersonalGroup.isSelected() || memberOfGroupsModel.getSize() > 0) {
            return true;
        }
        
        JOptionPane.showMessageDialog(this, "The user must be in at least one group, or a personal group must be created for them.");
        return false;
    }
    
    private InputVerifier getUsernameInputVerifier() {
        return new RegExpInputVerifier(PTN_USERNAME);
    }
    
    private InputVerifier getPasswordInputVerifier() {
        return new RegExpInputVerifier(PTN_PASSWORD);
    }
    
    private String getUmask() {
        return String.format("%4s", Integer.toString(Permission.DEFAULT_UMASK, UmaskSpinnerModel.OCTAL_RADIX)).replace(' ', '0');
    }
    
    protected SortedListModel getAvailableGroupsListModel() {
        if(availableGroupsModel == null) {
            try {
                final String groupNames[] = getUserManagementService().getGroups();
                availableGroupsModel = new SortedListModel<String>();
                availableGroupsModel.addAll(groupNames);
            } catch (final XMLDBException xmldbe) {
                JOptionPane.showMessageDialog(this, "Could not get available groups: " + xmldbe.getMessage(), "Create User Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        return availableGroupsModel;
    }
    
    protected SortedListModel getMemberOfGroupsListModel() {
        if(memberOfGroupsModel == null) {
            memberOfGroupsModel = new SortedListModel<String>();
        }
        return memberOfGroupsModel;
    }

    protected UserManagementService getUserManagementService() {
        return userManagementService;
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnAddGroup;
    private javax.swing.JButton btnClose;
    protected javax.swing.JButton btnCreate;
    private javax.swing.JButton btnRemoveGroup;
    protected javax.swing.JCheckBox cbDisabled;
    protected javax.swing.JCheckBox cbPersonalGroup;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JSeparator jSeparator4;
    private javax.swing.JLabel lblAvailableGroups;
    private javax.swing.JLabel lblDescription;
    private javax.swing.JLabel lblFullName;
    private javax.swing.JLabel lblMemberOfGroups;
    private javax.swing.JLabel lblPassword;
    private javax.swing.JLabel lblPasswordConfirm;
    private javax.swing.JLabel lblUmask;
    private javax.swing.JLabel lblUsername;
    private javax.swing.JList lstAvailableGroups;
    private javax.swing.JList lstMemberOfGroups;
    protected javax.swing.JSpinner spnUmask;
    protected javax.swing.JTextField txtDescription;
    protected javax.swing.JTextField txtFullName;
    protected javax.swing.JPasswordField txtPassword;
    protected javax.swing.JPasswordField txtPasswordConfirm;
    protected javax.swing.JTextField txtUsername;
    // End of variables declaration//GEN-END:variables
}
