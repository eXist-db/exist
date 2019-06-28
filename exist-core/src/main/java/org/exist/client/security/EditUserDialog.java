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

import java.util.*;
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
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
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
        //only fire if password changed
        if(isPasswordChanged()) {
            for(final DialogCompleteWithResponse<String> callback : getDialogCompleteWithResponseCallbacks()) {
                callback.complete(txtPassword.getText());
            }
        }
    }
    
    private void updateUser() {
        try {
            final Optional<String> newPassword = setAccountFromFormProperties();

            /**
             * We update the account in three stages:
             *
             * 1) General account properties
             * 2) Group memebrship
             * 3) Optionally set changed password.
             *
             * The password is always changed last if needed,
             * as it means the admin client must reconnect
             * if we are changing the logged in users password.
             *
             * The reconnection is performed by the registered
             * DialogCompleteWithResponse handler
             */

            //1) Update general account properties
            getUserManagementService().updateAccount(getAccount());

            //2) Update group membership (has to be modified separately from (1))
            modifyAccountGroupMembership();

            //3) Finally, optionally change the password
            if(newPassword.isPresent()) {
                final Account acct = getUserManagementService().getAccount(getAccount().getName());
                acct.setPassword(newPassword.get());
                getUserManagementService().updateAccount(acct);
            }
        } catch(final PermissionDeniedException pde) {
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

    /**
     * Updates the account with all of the fields from the form
     * except the password.
     *
     * If the password on the form has been changed then it is returned
     * as the result of this function
     */
    private Optional<String> setAccountFromFormProperties() throws PermissionDeniedException {
        getAccount().setMetadataValue(AXSchemaType.FULLNAME, txtFullName.getText());
        getAccount().setMetadataValue(EXistSchemaType.DESCRIPTION, txtDescription.getText());
        
        getAccount().setEnabled(!cbDisabled.isSelected());
        
        getAccount().setUserMask(UmaskSpinnerModel.octalUmaskToInt((String)spnUmask.getValue()));

        if(isPasswordChanged()) {
            final String password = new String(txtPassword.getPassword());
            return Optional.of(password);
        } else {
            return Optional.empty();
        }
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
