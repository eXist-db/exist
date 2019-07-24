/*
 * eXist Open Source Native XML Database
* Copyright (C) 2015 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.xquery.functions.securitymanager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.exist.dom.QName;
import org.exist.security.Account;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.storage.DBBroker;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;


/**
 *
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public class FindGroupFunction extends BasicFunction {

    private final static QName qnFindGroupsByGroupname = new QName("find-groups-by-groupname", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnListGroups = new QName("list-groups", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnFindGroupsWhereGroupnameContains = new QName("find-groups-where-groupname-contains", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnGetUserGroups = new QName("get-user-groups", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnGetUserPrimaryGroup = new QName("get-user-primary-group", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnGroupExists = new QName("group-exists", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    
    
    public final static FunctionSignature FNS_LIST_GROUPS = new FunctionSignature(
        qnListGroups,
        "List all groups",
        null,
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "The list of groups")
    );

    public final static FunctionSignature FNS_FIND_GROUPS_BY_GROUPNAME = new FunctionSignature(
        qnFindGroupsByGroupname,
        "Finds groups whoose group name starts with a matching string",
        new SequenceType[] {
            new FunctionParameterSequenceType("starts-with", Type.STRING, Cardinality.EXACTLY_ONE, "The starting string against which to match group names")
        },
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "The list of matching group names")
    );
    
    public final static FunctionSignature FNS_FIND_GROUPS_WHERE_GROUPNAME_CONTAINS = new FunctionSignature(
        qnFindGroupsWhereGroupnameContains,
        "Finds groups whoose group name contains the string fragment",
        new SequenceType[] {
            new FunctionParameterSequenceType("fragment", Type.STRING, Cardinality.EXACTLY_ONE, "The fragment against which to match group names")
        },
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "The list of matching group names")
    );
    
    public final static FunctionSignature FNS_GET_USER_GROUPS = new FunctionSignature(
        qnGetUserGroups,            
        "Returns the sequence of groups that the user $user is a member of. You must be a DBA or logged in as the user for which you are trying to retrieve group details for.",
        new SequenceType[] {
            new FunctionParameterSequenceType("user", Type.STRING, Cardinality.EXACTLY_ONE, "The username to retrieve the group membership list for.")
        },
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE_OR_MORE, "The users group memberships")
    );
    
    public final static FunctionSignature FNS_GET_USER_PRIMARY_GROUP = new FunctionSignature(
        qnGetUserPrimaryGroup,            
        "Returns the primary group of the user $user. You must be a DBA or logged in as the user for which you are trying to retrieve group details for.",
        new SequenceType[] {
            new FunctionParameterSequenceType("user", Type.STRING, Cardinality.EXACTLY_ONE, "The username to retrieve the primary group of.")
        },
        new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "The users primary group")
    );

    public final static FunctionSignature FNS_GROUP_EXISTS = new FunctionSignature(
        qnGroupExists,
        "Determines whether a user group exists.",
        new SequenceType[] {
            new FunctionParameterSequenceType("group", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the user group to check for existence.")
        },
        new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if the user group exists, false otherwise.")
    );

    public FindGroupFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {

        final DBBroker broker = getContext().getBroker();
        final Subject currentUser = broker.getCurrentSubject();
        
        if(!isCalledAs(qnGetUserGroups.getLocalPart()) && currentUser.getName().equals(SecurityManager.GUEST_USER)) {
            throw new XPathException(this, "You must be an authenticated user");
        }

        
        final SecurityManager securityManager = broker.getBrokerPool().getSecurityManager();

        final Sequence result;
        
        if(isCalledAs(qnGetUserPrimaryGroup.getLocalPart())) {
            final String username = args[0].getStringValue();
            result = new StringValue(securityManager.getAccount(username).getPrimaryGroup());
        } else if(isCalledAs(qnGroupExists.getLocalPart())) {
            final String groupName = args[0].getStringValue();
            result = BooleanValue.valueOf(securityManager.hasGroup(groupName));
        } else {
            final List<String> groupNames;
            if(isCalledAs(qnListGroups.getLocalPart())) {
                groupNames = securityManager.findAllGroupNames();
            } else if(isCalledAs(qnFindGroupsByGroupname.getLocalPart())) {
                final String startsWith = args[0].getStringValue();
                groupNames = securityManager.findGroupnamesWhereGroupnameStarts(startsWith);
            } else if(isCalledAs(qnFindGroupsWhereGroupnameContains.getLocalPart())) {
                final String fragment = args[0].getStringValue();
                groupNames = securityManager.findGroupnamesWhereGroupnameContains(fragment);
            } else if(isCalledAs(qnGetUserGroups.getLocalPart())) {
                final String username = args[0].getStringValue();

                if(!currentUser.hasDbaRole() && !currentUser.getName().equals(username)) {
                    throw new XPathException(this, "You must be a DBA or enquiring about your own user account!");
                }

                final Account user = securityManager.getAccount(username);
                groupNames = Arrays.asList(user.getGroups());
            } else {
                throw new XPathException(this, "Unknown function");
            }

            //order a-z
            Collections.sort(groupNames);

            result = new ValueSequence();
            for(final String groupName : groupNames) {
                result.add(new StringValue(groupName));
            }
        }
        return result;
    }
}