package org.exist.xquery.functions.securitymanager;

import java.util.ArrayList;
import java.util.List;

import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.security.*;
import org.exist.security.SecurityManager;
import org.exist.storage.DBBroker;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;


/**
 *
 * @author Adam Retter <adam@existsolutions.com>
 */
public class GroupMembershipFunction extends BasicFunction {

    private final static QName qnAddGroupMember = new QName("add-group-member", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnRemoveGroupMember = new QName("remove-group-member", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnGetGroupMembers = new QName("get-group-members", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnAddGroupManager = new QName("add-group-manager", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnRemoveGroupManager = new QName("remove-group-manager", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnGetGroupManagers = new QName("get-group-managers", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnIsDba = new QName("is-dba", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnSetPrimaryGroup = new QName("set-user-primary-group", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);

    public final static FunctionSignature FNS_ADD_GROUP_MEMBER = new FunctionSignature(
        qnAddGroupMember,
        "Adds a user to a group. Can only be called by a group manager or DBA.",
        new SequenceType[] {
            new FunctionParameterSequenceType("group", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the group whoose membership you wish to modify."),
            new FunctionParameterSequenceType("member", Type.STRING, Cardinality.ONE_OR_MORE, "The user(s) to add to the group membership.")
        },
        new SequenceType(Type.EMPTY, Cardinality.EMPTY)
    );

    public final static FunctionSignature FNS_REMOVE_GROUP_MEMBER = new FunctionSignature(
        qnRemoveGroupMember,
        "Removes a user from a group. Can only be called by a group manager of DBA.",
        new SequenceType[] {
            new FunctionParameterSequenceType("group", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the group whoose membership you wish to modify."),
            new FunctionParameterSequenceType("member", Type.STRING, Cardinality.ONE_OR_MORE, "The user(s) to remove from the group membership.")
        },
        new SequenceType(Type.EMPTY, Cardinality.EMPTY)
    );

    public final static FunctionSignature FNS_GET_GROUP_MEMBERS = new FunctionSignature(
        qnGetGroupMembers,
        "Gets a list of the group members.",
        new SequenceType[] {
            new FunctionParameterSequenceType("group", Type.STRING, Cardinality.EXACTLY_ONE, "The group name to retrieve the list of members for.")
        },
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE_OR_MORE, "The list of group members for the group $group")
    );

    public final static FunctionSignature FNS_ADD_GROUP_MANAGER = new FunctionSignature(
        qnAddGroupManager,
        "Adds a manager to a groups managers. Can only be called by a group manager or DBA.",
        new SequenceType[] {
                new FunctionParameterSequenceType("group", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the group to which you wish to add a manager(s)."),
                new FunctionParameterSequenceType("manager", Type.STRING, Cardinality.ONE_OR_MORE, "The user(s) to add to the group managers.")
        },
        new SequenceType(Type.EMPTY, Cardinality.EMPTY)
    );

    public final static FunctionSignature FNS_REMOVE_GROUP_MANAGER = new FunctionSignature(
        qnRemoveGroupManager,
        "Removes a manager from a groups managers. Can only be called by a group manager of DBA.",
        new SequenceType[] {
                new FunctionParameterSequenceType("group", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the group from which you wish to remove a manager(s)"),
                new FunctionParameterSequenceType("manager", Type.STRING, Cardinality.ONE_OR_MORE, "The user(s) to remove from the group managers.")
        },
        new SequenceType(Type.EMPTY, Cardinality.EMPTY)
    );

    public final static FunctionSignature FNS_GET_GROUP_MANAGERS = new FunctionSignature(
        qnGetGroupManagers,
        "Gets a list of the group managers. Can only be called by a group manager.",
        new SequenceType[] {
            new FunctionParameterSequenceType("group", Type.STRING, Cardinality.EXACTLY_ONE, "The group name to retrieve the list of managers for.")
        },
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE_OR_MORE, "The list of group managers for the group $group")
    );

    public final static FunctionSignature FNS_IS_DBA = new FunctionSignature(
        qnIsDba,
        "Determines if the user is a DBA.",
        new SequenceType[] {
            new FunctionParameterSequenceType("username", Type.STRING, Cardinality.EXACTLY_ONE, "The username of the user account to check if they are a member of the DBA group.")
        },
        new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true of the user is a DBA, false otherwise.")
    );

    public final static FunctionSignature FNS_SET_USER_PRIMARY_GROUP = new FunctionSignature(
        qnSetPrimaryGroup,
        "Sets the primary group of a user account. If the user is not yet in the group, then they are added to the group first.",
        new SequenceType[] {
            new FunctionParameterSequenceType("username", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the user account to set the primary group for."),
            new FunctionParameterSequenceType("group", Type.STRING, Cardinality.EXACTLY_ONE, "The group to set as the primary group for the user.")
        },
        new SequenceType(Type.EMPTY, Cardinality.EMPTY)
    );

    public GroupMembershipFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }


    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {

        Sequence result = Sequence.EMPTY_SEQUENCE;

        final DBBroker broker = getContext().getBroker();
        final Subject currentUser = broker.getSubject();
        final SecurityManager securityManager = broker.getBrokerPool().getSecurityManager();

        try {
            if(isCalledAs(qnIsDba.getLocalName())) {
                final String username = args[0].getStringValue();

                if(!securityManager.hasAccount(username)) {
                    throw new XPathException("The user account with username " + username + " does not exist.");
                } else {
                    final Account account = securityManager.getAccount(username);
                    result = BooleanValue.valueOf(securityManager.hasAdminPrivileges(account));
                }
            } else if(isCalledAs(qnSetPrimaryGroup.getLocalName())) {

                final String username = args[0].getStringValue();
                final String groupName = args[1].getStringValue();

                if(!securityManager.hasAccount(username)) {
                    throw new XPathException("The user account with username " + username + " does not exist.");
                }

                if(!securityManager.hasGroup(groupName)) {
                    throw new XPathException("The user group with name " + groupName + " does not exist.");
                }

                final Group group = securityManager.getGroup(groupName);

                if(!isCalledAs(qnGetGroupMembers.getLocalName()) && (!(group.isManager(currentUser) || currentUser.hasDbaRole()))) {
                    throw new XPathException("Only a Group Manager or DBA may modify the group or retrieve sensitive group information.");
                }

                final Account account = securityManager.getAccount(username);

                //set the primary group
                account.setPrimaryGroup(group);
                securityManager.updateAccount(account);

            } else {

                final String groupName = args[0].getStringValue();

                if(!securityManager.hasGroup(groupName)) {
                    throw new XPathException("The user group with name " + groupName + " does not exist.");
                }

                final Group group = securityManager.getGroup(groupName);

                if(!isCalledAs(qnGetGroupMembers.getLocalName()) && (!(group.isManager(currentUser) || currentUser.hasDbaRole()))) {
                    throw new XPathException("Only a Group Manager or DBA may modify the group or retrieve sensitive group information.");
                }

                if(isCalledAs(qnAddGroupMember.getLocalName())) {
                    final List<Account> users = getUsers(securityManager, args[1]);
                    addGroupMembers(securityManager, group, users);
                } else if(isCalledAs(qnRemoveGroupMember.getLocalName())) {
                    final List<Account> users = getUsers(securityManager, args[1]);
                    removeGroupMembers(securityManager, group, users);
                } else if(isCalledAs(qnGetGroupMembers.getLocalName())) {
                    final List<String> groupMembers = securityManager.findAllGroupMembers(groupName);

                    final ValueSequence seq = new ValueSequence();
                    for(final String groupMember : groupMembers) {
                        seq.add(new StringValue(groupMember));
                    }
                    result = seq;
                } else if(isCalledAs(qnAddGroupManager.getLocalName())) {
                    final List<Account> users = getUsers(securityManager, args[1]);
                    addGroupManagers(securityManager, group, users);
                } else if(isCalledAs(qnRemoveGroupManager.getLocalName())) {
                    final List<Account> users = getUsers(securityManager, args[1]);
                    removeGroupManagers(securityManager, group, users);
                } else if(isCalledAs(qnGetGroupManagers.getLocalName())) {
                    final ValueSequence seq = new ValueSequence();
                    for(final Account groupManager : group.getManagers()) {
                        seq.add(new StringValue(groupManager.getName()));
                    }
                    result = seq;
                } else {
                    throw new XPathException("Unknown function call: " + getSignature());
                }
            }
        } catch(final PermissionDeniedException pde) {
            throw new XPathException(this, pde);
        } catch(final EXistException ee) {
            throw new XPathException(this, ee);
        }

        return result;
    }

    private void addGroupMembers(final SecurityManager securityManager, final Group group, final List<Account> accounts) throws PermissionDeniedException, EXistException {
        for(final Account account : accounts) {
            //TEMP - ESCALATE TO DBA :-(
            /**
             * Security Manager has a fundamental flaw
             * Group Membership is stored in the Account XML: so you cannot
             * add a user to a group without modifying the users XML
             * this is a security issue as if you are not that user
             * you have to escalate to DBA - must redesign
             * Consider Unix /etc/groups design!
             * See XMLDBCreateGroup and XMLDRemoveUserFromGroup
             */
            final Subject currentSubject = context.getBroker().getSubject();
            try {
                //escalate
                context.getBroker().setSubject(securityManager.getSystemSubject());

                //perform action
                account.addGroup(group);
                securityManager.updateAccount(account);
            } finally {
                context.getBroker().setSubject(currentSubject);
            }
        }
    }

    private void removeGroupMembers(final SecurityManager securityManager, final Group group, final List<Account> accounts) throws PermissionDeniedException, EXistException {
        for(final Account account : accounts) {
            //TEMP - ESCALATE TO DBA :-(
            /**
             * Security Manager has a fundamental flaw
             * Group Membership is stored in the Account XML: so you cannot
             * add a user to a group without modifying the users XML
             * this is a security issue as if you are not that user
             * you have to escalate to DBA - must redesign
             * Consider Unix /etc/groups design!
             * See XMLDBCreateGroup and XMLDRemoveUserFromGroup
             */
            final Subject currentSubject = context.getBroker().getSubject();
            try {
                //escalate
                context.getBroker().setSubject(securityManager.getSystemSubject());

                //perform action
                account.remGroup(group.getName());
                securityManager.updateAccount(account);
            } finally {
                context.getBroker().setSubject(currentSubject);
            }
        }
    }

    private void addGroupManagers(final SecurityManager securityManager, final Group group, final List<Account> accounts) throws PermissionDeniedException, EXistException {
        group.addManagers(accounts);
        securityManager.updateGroup(group);
    }

    private void removeGroupManagers(final SecurityManager securityManager, final Group group, final List<Account> accounts) throws PermissionDeniedException, EXistException {
        for(final Account account : accounts) {
            group.removeManager(account);
        }
        securityManager.updateGroup(group);
    }

    private List<Account> getUsers(final SecurityManager securityManager, final Sequence seq) throws XPathException {
        final List<Account> accounts = new ArrayList<Account>();
        for(int i = 0; i < seq.getItemCount(); i++) {
            final String user = seq.itemAt(i).toString();
            final Account account = securityManager.getAccount(user);
            if(account == null) {
                throw new XPathException("The user account '" + user + "' does not exist!");
            } else {
                accounts.add(account);
            }
        }
        return accounts;
    }


}