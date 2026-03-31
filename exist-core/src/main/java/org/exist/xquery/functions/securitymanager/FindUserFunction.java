/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.functions.securitymanager;

import org.exist.dom.QName;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.storage.DBBroker;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;

import java.util.Collections;
import java.util.List;


/**
 *
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public class FindUserFunction extends BasicFunction {

    private static final QName qnFindUsersByUsername = new QName("find-users-by-username", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private static final QName qnFindUsersByName = new QName("find-users-by-name", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private static final QName qnFindUsersByNamePart = new QName("find-users-by-name-part", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private static final QName qnListUsers = new QName("list-users", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private static final QName qnUserExists = new QName("user-exists", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    
    
    public static final FunctionSignature FNS_FIND_USERS_BY_USERNAME = new FunctionSignature(
        qnFindUsersByUsername,
        "Finds users whoose username starts with a matching string",
        new SequenceType[] {
            new FunctionParameterSequenceType("starts-with", Type.STRING, Cardinality.EXACTLY_ONE, "The starting string against which to match usernames")
        },
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "The list of matching usernames")
    );
    public static final FunctionSignature FNS_FIND_USERS_BY_NAME = new FunctionSignature(
        qnFindUsersByName,
        "Finds users whoose personal name starts with a matching string",
        new SequenceType[] {
            new FunctionParameterSequenceType("starts-with", Type.STRING, Cardinality.EXACTLY_ONE, "The starting string against which to match a personal name")
        },
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "The list of matching usernames")
    );
    
    public static final FunctionSignature FNS_FIND_USERS_BY_NAME_PART = new FunctionSignature(
        qnFindUsersByNamePart,
        "Finds users whoose first name or last name starts with a matching string",
        new SequenceType[] {
            new FunctionParameterSequenceType("starts-with", Type.STRING, Cardinality.EXACTLY_ONE, "The starting string against which to match a first or last name")
        },
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "The list of matching usernames")
    );
    
    public static final FunctionSignature FNS_LIST_USERS = new FunctionSignature(
        qnListUsers,
        "List all users. You must be a DBA to enumerate all users, if you are not a DBA you will just get the username of the currently logged in user.",
        null,
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE_OR_MORE, "The list of users.")
    );
    
    public static final FunctionSignature FNS_USER_EXISTS = new FunctionSignature(
        qnUserExists,
        "Determines whether a user exists.",
        new SequenceType[] {
            new FunctionParameterSequenceType("user", Type.STRING, Cardinality.EXACTLY_ONE, "The username to check for existence.")
        },
        new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if the user account exists, false otherwise.")
    );


    public FindUserFunction(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        final DBBroker broker = getContext().getBroker();
        final Subject currentUser = broker.getCurrentSubject();

        final SecurityManager securityManager = broker.getBrokerPool().getSecurityManager();
        
        final Sequence result;
        
        if(isCalledAs(qnListUsers.getLocalPart())) {
            result = new ValueSequence();
            if(SecurityManager.GUEST_USER.equals(currentUser.getName())) {
                result.add(new StringValue(this, SecurityManager.GUEST_USER));
            } else {
                addUserNamesToSequence(securityManager.findAllUserNames(), result);
            }
        } else {
        
            if(SecurityManager.GUEST_USER.equals(currentUser.getName())) {
                throw new XPathException(this, "You must be an authenticated user");
            }
            
            if(isCalledAs(qnUserExists.getLocalPart())) {
                 final String username = args[0].getStringValue();
                 result = BooleanValue.valueOf(securityManager.hasAccount(username));
            } else {
                result = new ValueSequence();
                final String startsWith = args[0].getStringValue();

                final List<String> usernames;
                if(isCalledAs(qnFindUsersByUsername.getLocalPart())) {
                    usernames = securityManager.findUsernamesWhereUsernameStarts(startsWith);
                } else if(isCalledAs(qnFindUsersByName.getLocalPart())) {
                    usernames = securityManager.findUsernamesWhereNameStarts(startsWith);
                } else if(isCalledAs(qnFindUsersByNamePart.getLocalPart())) {
                    usernames = securityManager.findUsernamesWhereNamePartStarts(startsWith);
                } else {
                    throw new XPathException(this, "Unknown function");
                }

                addUserNamesToSequence(usernames, result);
            }
        }
        
        return result;
    }
    
    private void addUserNamesToSequence(final List<String> userNames, final Sequence sequence) throws XPathException {
        //order a-z
        Collections.sort(userNames);

        for(final String userName : userNames) {
            sequence.add(new StringValue(this, userName));
        }
    }
}
