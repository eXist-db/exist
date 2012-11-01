package org.exist.client.security;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JOptionPane;
import org.exist.security.AXSchemaType;
import org.exist.security.Account;
import org.exist.security.EXistSchemaType;
import org.exist.security.PermissionDeniedException;
import org.exist.xmldb.UserManagementService;
import org.xmldb.api.base.XMLDBException;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class EditUserDialog extends UserDialog {
    
    private final static String HIDDEN_PASSWORD_CONST = "password";
    
    private final Account account;
    
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
        
        for(final String group : getAccount().getGroups()) {
            getMemberOfGroupsListModel().add(group);
            getAvailableGroupsListModel().removeElement(group);
        }
    }

    @Override
    protected void createUser() {
        //dont create a user, update instead!
        updateUser();
    }
    
    private void updateUser() {
        try {
            setAccountFromFormProperties();
            getUserManagementService().updateAccount(getAccount());
        } catch(PermissionDeniedException pde) {
            JOptionPane.showMessageDialog(this, "Could not update user '" + txtUsername.getText() + "': " + pde.getMessage(), "Edit User Error", JOptionPane.ERROR_MESSAGE);
        } catch(final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not update user '" + txtUsername.getText() + "': " + xmldbe.getMessage(), "Edit User Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void setAccountFromFormProperties() throws PermissionDeniedException {
        getAccount().setMetadataValue(AXSchemaType.FULLNAME, txtFullName.getText());
        getAccount().setMetadataValue(EXistSchemaType.DESCRIPTION, txtDescription.getText());
        
        final String password = new String(txtPassword.getPassword());
        if(!password.equals(HIDDEN_PASSWORD_CONST)) {
            getAccount().setPassword(password);
        }
        
        getAccount().setEnabled(!cbDisabled.isSelected());
        
        getAccount().setUserMask(UmaskSpinnerModel.octalUmaskToInt((String)spnUmask.getValue()));
        
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
                account.remGroup(currentGroup);
            }
        }
        
        //groups to add
        for(final String memberOfGroup : memberOfGroups) {
            if(!currentGroups.contains(memberOfGroup)) {
                account.addGroup(memberOfGroup);
            }
        }
        
    }
    
    protected Account getAccount() {
        return account;
    }
}
