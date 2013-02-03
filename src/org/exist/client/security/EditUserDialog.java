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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JOptionPane;
import org.exist.client.DialogCompleteWithResponse;
import org.exist.client.DialogWithResponse;
import org.exist.security.AXSchemaType;
import org.exist.security.Account;
import org.exist.security.EXistSchemaType;
import org.exist.security.PermissionDeniedException;
import org.exist.xmldb.EXistUserManagementService;
import org.exist.xmldb.UserManagementService;
import org.xmldb.api.base.XMLDBException;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class EditUserDialog extends UserDialog implements DialogWithResponse<String> {
    
    private static final long serialVersionUID = 9097018734007436201L;

    private final static String HIDDEN_PASSWORD_CONST = "password";
    
    private final Account account;
    private final List<DialogCompleteWithResponse<String>> dialogCompleteWithResponseCallbacks = new ArrayList<DialogCompleteWithResponse<String>>();
    
    public EditUserDialog(final UserManagementService userManagementService, final Account account) {
        super(userManagementService);
        this.account = account;
        setFormPropertiesFromAccount();
    }
    
    private void setFormPropertiesFromAccount() {
        
        setTitle("Edit User: " + getAccount().getName());
        
        btnCreate.setText("Save");
        
        txtUsername.setText(getAccount().getName());
        txtUsername.setEnabled(false);
        
        txtFullName.setText(getAccount().getMetadataValue(AXSchemaType.FULLNAME));
        txtDescription.setText(getAccount().getMetadataValue(EXistSchemaType.DESCRIPTION));
        
        txtPassword.setText(HIDDEN_PASSWORD_CONST);
        txtPasswordConfirm.setText(HIDDEN_PASSWORD_CONST);
        
        cbDisabled.setSelected(!getAccount().isEnabled());
        
        spnUmask.setValue(UmaskSpinnerModel.intToOctalUmask(getAccount().getUserMask()));
        
        cbPersonalGroup.setVisible(false);
        
        boolean first = true;
        for(final String group : getAccount().getGroups()) {
            getMemberOfGroupsListModel().add(group);
            getAvailableGroupsListModel().removeElement(group);
            
            if(first) {
                setPrimaryGroup(group);
                getMemberOfGroupsListCellRenderer().setCellOfInterest(getPrimaryGroup());
                first = false;
            }
        }
    }

    @Override
    protected void createUser() {
        
        if(getMemberOfGroupsListModel().getSize() == 0) {
            JOptionPane.showMessageDialog(this, "A user must be a member of at least one user group", "Edit User Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        //dont create a user, update instead!
        updateUser();
        
        //return updated password
        for(final DialogCompleteWithResponse<String> callback : getDialogCompleteWithResponseCallbacks()) {
            
            //only fire if password changed
            if(isPasswordChanged()) {
                callback.complete(txtPassword.getText());
            }
        }
    }
    
    private void updateUser() {
        try {
            setAccountFromFormProperties();
            getUserManagementService().updateAccount(getAccount());
            
            //group membership has to be modified seperately
            modifyAccountGroupMembership();
        } catch(PermissionDeniedException pde) {
            JOptionPane.showMessageDialog(this, "Could not update user '" + txtUsername.getText() + "': " + pde.getMessage(), "Edit User Error", JOptionPane.ERROR_MESSAGE);
        } catch(final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not update user '" + txtUsername.getText() + "': " + xmldbe.getMessage(), "Edit User Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private boolean isPasswordChanged() {
        final String password = new String(txtPassword.getPassword());
        return !password.equals(HIDDEN_PASSWORD_CONST);
    }
    
    private void modifyAccountGroupMembership() throws XMLDBException {
        //get the current groups of the user
        final Set<String> currentGroups = new HashSet<String>(Arrays.asList(getAccount().getGroups()));
        
        //get the new groups of the user to be set
        final Set<String> memberOfGroups = new HashSet<String>();
        for(int i = 0; i < getMemberOfGroupsListModel().getSize(); i++) {
            memberOfGroups.add((String)getMemberOfGroupsListModel().getElementAt(i));
        }
        
        //groups to remove
        for(final String currentGroup : currentGroups) {
            if(!memberOfGroups.contains(currentGroup)) {
                getUserManagementService().removeGroupMember(currentGroup, getAccount().getName());
            }
        }
        
        //groups to add
        for(final String memberOfGroup : memberOfGroups) {
            if(!currentGroups.contains(memberOfGroup)) {
                getUserManagementService().addAccountToGroup(getAccount().getName(), memberOfGroup);
            }
        }
        
        //set the primary group
        ((EXistUserManagementService)getUserManagementService()).setUserPrimaryGroup(getAccount().getName(), getPrimaryGroup());
    }
    
    private void setAccountFromFormProperties() throws PermissionDeniedException {
        getAccount().setMetadataValue(AXSchemaType.FULLNAME, txtFullName.getText());
        getAccount().setMetadataValue(EXistSchemaType.DESCRIPTION, txtDescription.getText());
        
        if(isPasswordChanged()) {
            final String password = new String(txtPassword.getPassword());
            getAccount().setPassword(password);
        }
        
        getAccount().setEnabled(!cbDisabled.isSelected());
        
        getAccount().setUserMask(UmaskSpinnerModel.octalUmaskToInt((String)spnUmask.getValue()));
    }
    
    protected Account getAccount() {
        return account;
    }

    private List<DialogCompleteWithResponse<String>> getDialogCompleteWithResponseCallbacks() {
        return dialogCompleteWithResponseCallbacks;
    }
    
    @Override
    public void addDialogCompleteWithResponseCallback(final DialogCompleteWithResponse<String> dialogCompleteWithResponseCallback) {
        getDialogCompleteWithResponseCallbacks().add(dialogCompleteWithResponseCallback);
    }
}
