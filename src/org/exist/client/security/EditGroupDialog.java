package org.exist.client.security;

import org.exist.security.EXistSchemaType;
import org.exist.security.Group;
import org.exist.xmldb.UserManagementService;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class EditGroupDialog extends GroupDialog {
    
    private final Group group;
    
    public EditGroupDialog(final UserManagementService userManagementService, final Group group) {
        super(userManagementService);
        this.group = group;
        setFormPropertiesFromGroup();
    }

    private void setFormPropertiesFromGroup() {
        setTitle("Edit Group: " + getGroup().getName());
        
        btnCreate.setText("Save");
        
        txtGroupName.setText(getGroup().getName());
        txtGroupName.setEnabled(false);

        txtDescription.setText(getGroup().getMetadataValue(EXistSchemaType.DESCRIPTION));
        
        //TODO display existing members and managers.
    }
    
    @Override
    protected void createGroup() {
        //dont create a group update instead!
        updateGroup();
    }
    
    private void updateGroup() {
        /*try {
            setAccountFromFormProperties();
            getUserManagementService().updateAccount(getAccount());
        } catch(PermissionDeniedException pde) {
            JOptionPane.showMessageDialog(this, "Could not update user '" + txtUsername.getText() + "': " + pde.getMessage(), "Edit User Error", JOptionPane.ERROR_MESSAGE);
        } catch(final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not update user '" + txtUsername.getText() + "': " + xmldbe.getMessage(), "Edit User Error", JOptionPane.ERROR_MESSAGE);
        }*/
    }

    protected Group getGroup() {
        return group;
    }
}
