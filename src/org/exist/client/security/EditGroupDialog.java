package org.exist.client.security;

import java.util.Arrays;
import java.util.List;
import javax.swing.JOptionPane;
import org.exist.security.Account;
import org.exist.security.EXistSchemaType;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.xmldb.UserManagementService;
import org.xmldb.api.base.XMLDBException;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class EditGroupDialog extends GroupDialog {
    
    private final Group group;
    private final String currentUser;
    
    public EditGroupDialog(final UserManagementService userManagementService, final String currentUser, final Group group) {
        super(userManagementService);
        this.currentUser = currentUser;
        this.group = group;
        setFormPropertiesFromGroup();
    }

    private void setFormPropertiesFromGroup() {
        setTitle("Edit Group: " + getGroup().getName());
        
        btnCreate.setText("Save");
        
        txtGroupName.setText(getGroup().getName());
        txtGroupName.setEnabled(false);

        txtDescription.setText(getGroup().getMetadataValue(EXistSchemaType.DESCRIPTION));
        
        //display existing group members and managers
        try {
            final List<Account> groupManagers = group.getManagers();

            final String[] groupMembers = getUserManagementService().getGroupMembers(group.getName());
            Arrays.sort(groupMembers); //order the members a-z

            for(final String groupMember : groupMembers) {
                getGroupMembersTableModel().addRow(new Object[]{
                    groupMember,
                    isGroupManager(groupManagers, groupMember)
                });
            }
        } catch(final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not get group members: " + xmldbe.getMessage(), "Edit Group Error", JOptionPane.ERROR_MESSAGE);
        } catch(final PermissionDeniedException pde) {
            JOptionPane.showMessageDialog(this, "Could not get group members: " + pde.getMessage(), "Edit Group Error", JOptionPane.ERROR_MESSAGE);
        }
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

    @Override
    protected boolean canModifyGroupMembers() {
        try {
            return getUserManagementService().getAccount(currentUser).hasDbaRole() || isGroupManager(group.getManagers(), currentUser);
        } catch(final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not establish user " + currentUser + "'s group permissions: " + xmldbe.getMessage(), "Edit Group Error", JOptionPane.ERROR_MESSAGE);
            return false;
        } catch(final PermissionDeniedException pde) {
            JOptionPane.showMessageDialog(this, "Could not establish user " + currentUser + "'s group permissions: " + pde.getMessage(), "Edit Group Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }
    
    

    private boolean isGroupManager(final List<Account> groupManagers, final String groupMember) {
        for(final Account groupManager : groupManagers) {
            if(groupManager.getName().equals(groupMember)){
                return true;
            }
        }

        return false;
    }
}
