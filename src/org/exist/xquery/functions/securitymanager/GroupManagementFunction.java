/*
 *  eXist-db SecurityManager Module Extension
 *  Copyright (C) 2013 Adam Retter <adam@existsolutions.com>
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */
package org.exist.xquery.functions.securitymanager;

import org.exist.EXistException;
import org.exist.config.ConfigurationException;
import org.exist.dom.QName;
import org.exist.security.*;
import org.exist.security.SecurityManager;
import org.exist.security.internal.aider.GroupAider;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class GroupManagementFunction extends BasicFunction {

    private final static QName qnCreateGroup = new QName("create-group", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnRemoveGroup = new QName("remove-group", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnDeleteGroup = new QName("delete-group", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);

    public final static FunctionSignature FNS_CREATE_GROUP = new FunctionSignature(
        qnCreateGroup,
        "Creates a User Group. The current user will be set as the group's manager.",
        new SequenceType[]{
            new FunctionParameterSequenceType("group-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the group to create.")
        },
        new SequenceType(Type.ITEM, Cardinality.EMPTY)
    );

    public final static FunctionSignature FNS_CREATE_GROUP_WITH_METADATA = new FunctionSignature(
        qnCreateGroup,
        "Creates a User Group. The current user will be set as the group's manager.",
        new SequenceType[]{
            new FunctionParameterSequenceType("group-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the group to create."),
            new FunctionParameterSequenceType("description", Type.STRING, Cardinality.EXACTLY_ONE, "A description of the group.")
        },
        new SequenceType(Type.ITEM, Cardinality.EMPTY)
    );

    public final static FunctionSignature FNS_CREATE_GROUP_WITH_MANAGERS_WITH_METADATA = new FunctionSignature(
        qnCreateGroup,
        "Creates a User Group. The current user will be set as a manager of the group in addition to the specified managers.",
        new SequenceType[]{
            new FunctionParameterSequenceType("group-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the group to create."),
            new FunctionParameterSequenceType("managers", Type.STRING, Cardinality.ONE_OR_MORE, "The usernames of users that will be a manager of this group."),
            new FunctionParameterSequenceType("description", Type.STRING, Cardinality.EXACTLY_ONE, "A description of the group.")
        },
        new SequenceType(Type.ITEM, Cardinality.EMPTY)
    );

    public final static FunctionSignature FNS_REMOVE_GROUP = new FunctionSignature(
        qnRemoveGroup,
        "Remove a User Group. Any resources owned by the group will be moved to the 'guest' group.",
        new SequenceType[]{
            new FunctionParameterSequenceType("group-name", Type.STRING, Cardinality.EXACTLY_ONE, "The group-id to delete")
        },
        new SequenceType(Type.ITEM, Cardinality.EMPTY)
    );

    public final static FunctionSignature FNS_DELETE_GROUP = new FunctionSignature(
        qnDeleteGroup,
        "Removes a User Group. Any resources owned by the group will be moved to the 'guest' group.",
        new SequenceType[]{
            new FunctionParameterSequenceType("group-id", Type.STRING, Cardinality.EXACTLY_ONE, "The group-id to delete")
        },
        new SequenceType(Type.ITEM, Cardinality.EMPTY),
            FNS_REMOVE_GROUP
    );

    //TODO implement later
    /* public final static FunctionSignature FNS_DELETE_GROUP_WITH_SUCCESSOR = new FunctionSignature(
        qnRemoveGroup
        "Deletes an existing group identified by $group-id, any resources owned by the group will be moved to the group indicated by $successor-group-id.",
        new SequenceType[]{
            new FunctionParameterSequenceType("group-id", Type.STRING, Cardinality.EXACTLY_ONE, "The group-id to delete"),
            new FunctionParameterSequenceType("successor-group-id", Type.STRING, Cardinality.EXACTLY_ONE, "The group-id that should take over ownership of any resources")
        },
        new SequenceType(Type.ITEM, Cardinality.EMPTY)
    ); */

    public GroupManagementFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {

        final SecurityManager securityManager = context.getBroker().getBrokerPool().getSecurityManager();
        final Subject currentSubject = context.getBroker().getSubject();

        try {
            final String groupName = args[0].itemAt(0).getStringValue();

            if(isCalledAs(qnCreateGroup.getLocalName())) {
                if(securityManager.hasGroup(groupName)) {
                    throw new XPathException("The group with name " + groupName + " already exists.");
                }

                if(!currentSubject.hasDbaRole()) {
                    throw new XPathException("Only DBA users may create a user group.");
                }

                final Group group = new GroupAider(groupName);
                group.addManager(currentSubject);

                if(getSignature().getArgumentCount() == 3) {
                    //set group managers
                    final List<Account> groupManagers = getGroupManagers(securityManager, args[1]);
                    group.addManagers(groupManagers);
                }

                //set metadata
                if(getSignature().getArgumentCount() >= 2) {
                    group.setMetadataValue(EXistSchemaType.DESCRIPTION, args[getSignature().getArgumentCount() - 1].toString());
                }

                securityManager.addGroup(group);

            } else if(isCalledAs(qnRemoveGroup.getLocalName()) || isCalledAs(qnDeleteGroup.getLocalName())) {

                if(!securityManager.hasGroup(groupName)) {
                    throw new XPathException("The group with name " + groupName + " does not exist.");
                }

                final Group successorGroup;
                if(getArgumentCount() == 2) {
                    final String successorGroupName = args[1].itemAt(0).getStringValue();
                    if(!currentSubject.hasGroup(successorGroupName)) {
                        throw new PermissionDeniedException("You must be a member of the group for which permissions should be inherited by");
                    }
                    successorGroup = securityManager.getGroup(successorGroupName);

                } else {
                    successorGroup = securityManager.getGroup("guest");
                }

                try {
                    securityManager.deleteGroup(groupName);
                } catch(final EXistException ee) {
                    throw new XPathException(this, ee);
                }
            } else {
                throw new XPathException("Unknown function call: " + getSignature());
            }

            return Sequence.EMPTY_SEQUENCE;
        } catch(final PermissionDeniedException pde) {
            throw new XPathException(this, pde);
        } catch(final ConfigurationException ce) {
            throw new XPathException(this, ce);
        } catch(final EXistException ee) {
            throw new XPathException(this, ee);
        }
    }

    private List<Account> getGroupManagers(final SecurityManager securityManager, final Sequence seq) {
        final List<Account> managers = new ArrayList<Account>();
        for(int i = 0; i < seq.getItemCount(); i++) {
            final Account account = securityManager.getAccount(seq.itemAt(i).toString());
            managers.add(account);
        }
        return managers;
    }
}