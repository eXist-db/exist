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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JOptionPane;
import org.exist.security.Account;
import org.exist.security.EXistSchemaType;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.xmldb.UserManagementService;
import org.xmldb.api.base.XMLDBException;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class EditGroupDialog extends GroupDialog {
    
    private static final long serialVersionUID = -9092253443709031810L;
	
    private final Group group;
    
    public EditGroupDialog(final UserManagementService userManagementService, final String currentUser, final Group group) {
        super(userManagementService, currentUser);
        this.group = group;
        setFormPropertiesFromGroup();
    }
    
    @Override
    protected void addSelfAsManager() {
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
        
        //enable additions to the group?
        miAddGroupMember.setEnabled(canModifyGroupMembers());
        btnAddMember.setEnabled(canModifyGroupMembers());
    }
    
    @Override
    protected void createGroup() {
        //dont create a group update instead!
        updateGroup();
    }
    
    private void updateGroup() {
        try {
            updateGroupMembers();
            
            setGroupFromFormProperties();
            
            getUserManagementService().updateGroup(getGroup());
            
        } catch(final PermissionDeniedException pde) {
            JOptionPane.showMessageDialog(this, "Could not update group '" + txtGroupName.getText() + "': " + pde.getMessage(), "Edit Group Error", JOptionPane.ERROR_MESSAGE);
        } catch(final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not update group '" + txtGroupName.getText() + "': " + xmldbe.getMessage(), "Edit Group Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void setGroupFromFormProperties() throws PermissionDeniedException, XMLDBException {
        getGroup().setMetadataValue(EXistSchemaType.DESCRIPTION, txtDescription.getText());
        
        //set managers
        //1) remove all managers
        final List<Account> currentManagers = getGroup().getManagers();
        for(final Account currentManager : currentManagers) {
            getUserManagementService().removeGroupManager(group.getName(), currentManager.getName());
        }
        
        //2) only add those in this dialog
        for(int i = 0; i < getGroupMembersTableModel().getRowCount(); i++) {
            final boolean isManager = (Boolean)getGroupMembersTableModel().getValueAt(i, 1);
            if(isManager) {
                final String manager = (String)getGroupMembersTableModel().getValueAt(i, 0);
                getUserManagementService().addGroupManager(manager, getGroup().getName());
            }
        }
    }
    
    private void updateGroupMembers() throws XMLDBException, PermissionDeniedException {
        final Set<String> currentGroupMembers = new HashSet<String>(Arrays.asList(getUserManagementService().getGroupMembers(group.getName())));
        
        final Set<String> groupMembers = new HashSet<String>();
        for(int i = 0; i < getGroupMembersTableModel().getRowCount(); i++) {
            groupMembers.add((String)getGroupMembersTableModel().getValueAt(i, 0));
        }
        
        //members to remove
        for(final String currentGroupMember : currentGroupMembers) {
            if(!groupMembers.contains(currentGroupMember)) {
                getUserManagementService().removeGroupMember(group.getName(), currentGroupMember);
            }
        }
        
        //members to add
        for(final String groupMember : groupMembers) {
            if(!currentGroupMembers.contains(groupMember)) {
                getUserManagementService().addAccountToGroup(groupMember, group.getName());
            }
        }
    }

    protected Group getGroup() {
        return group;
    }

    @Override
    protected boolean canModifyGroupMembers() {
        try {
            return (getUserManagementService().getAccount(getCurrentUser()).hasDbaRole() || isGroupManager(group.getManagers(), getCurrentUser()));
        } catch(final XMLDBException xmldbe) {
            JOptionPane.showMessageDialog(this, "Could not establish user " + getCurrentUser() + "'s group permissions: " + xmldbe.getMessage(), "Edit Group Error", JOptionPane.ERROR_MESSAGE);
            return false;
        } catch(final PermissionDeniedException pde) {
            JOptionPane.showMessageDialog(this, "Could not establish user " + getCurrentUser() + "'s group permissions: " + pde.getMessage(), "Edit Group Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }
    
    @Override
    protected boolean canModifySelectedGroupMember() {
        final boolean groupMemberSelected = tblGroupMembers.getSelectedRow() > -1;
        
        return
            groupMemberSelected
            && (!(group.getName().equals(org.exist.security.SecurityManager.DBA_GROUP) && (getSelectedMember().equals(org.exist.security.SecurityManager.DBA_USER) || getSelectedMember().equals(org.exist.security.SecurityManager.SYSTEM))))
            && (!(group.getName().equals(org.exist.security.SecurityManager.GUEST_GROUP) && getSelectedMember().equals(org.exist.security.SecurityManager.GUEST_USER)));
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
