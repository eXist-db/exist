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

import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.security.*;
import org.exist.security.SecurityManager;
import org.exist.security.internal.Password;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.DBBroker;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class AccountManagementFunction extends BasicFunction {
    
    public final static QName qnCreateAccount = new QName("create-account", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    public final static QName qnRemoveAccount = new QName("remove-account", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    public final static QName qnPasswd = new QName("passwd", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    public final static QName qnPasswdHash = new QName("passwd-hash", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    
    public final static FunctionSignature FNS_CREATE_ACCOUNT = new FunctionSignature(
        qnCreateAccount,
        "Creates a User Account.",
         new SequenceType[] {
            new FunctionParameterSequenceType("username", Type.STRING, Cardinality.EXACTLY_ONE, "The User's username."),
            new FunctionParameterSequenceType("password", Type.STRING, Cardinality.EXACTLY_ONE, "The User's password."),
            new FunctionParameterSequenceType("primary-group", Type.STRING, Cardinality.EXACTLY_ONE, "The primary group of the user."),
            new FunctionParameterSequenceType("groups", Type.STRING, Cardinality.ZERO_OR_MORE, "Any supplementary groups of which the user should be a member.")
            }, 
        new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
    );

    public final static FunctionSignature FNS_CREATE_ACCOUNT_WITH_METADATA = new FunctionSignature(
        qnCreateAccount,
        "Creates a User Account.",
        new SequenceType[] {
            new FunctionParameterSequenceType("username", Type.STRING, Cardinality.EXACTLY_ONE, "The User's username."),
            new FunctionParameterSequenceType("password", Type.STRING, Cardinality.EXACTLY_ONE, "The User's password."),
            new FunctionParameterSequenceType("primary-group", Type.STRING, Cardinality.EXACTLY_ONE, "The primary group of the user."),
            new FunctionParameterSequenceType("groups", Type.STRING, Cardinality.ZERO_OR_MORE, "Any supplementary groups of which the user should be a member."),
            new FunctionParameterSequenceType("full-name", Type.STRING, Cardinality.EXACTLY_ONE, "The full name of the user."),
            new FunctionParameterSequenceType("description", Type.STRING, Cardinality.EXACTLY_ONE, "A description of the user.")
        },
        new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
    );
    
    public final static FunctionSignature FNS_CREATE_ACCOUNT_WITH_PERSONAL_GROUP = new FunctionSignature(
        qnCreateAccount,
        "Creates a User Account and a personal group for that user. The personal group takes the same name as the user, and is set as the user's primary group.",
         new SequenceType[] {
            new FunctionParameterSequenceType("username", Type.STRING, Cardinality.EXACTLY_ONE, "The User's username."),
            new FunctionParameterSequenceType("password", Type.STRING, Cardinality.EXACTLY_ONE, "The User's password."),
            new FunctionParameterSequenceType("groups", Type.STRING, Cardinality.ZERO_OR_MORE, "Any supplementary groups of which the user should be a member.")
        },
        new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
    );

    public final static FunctionSignature FNS_CREATE_ACCOUNT_WITH_PERSONAL_GROUP_WITH_METADATA = new FunctionSignature(
        qnCreateAccount,
        "Creates a User Account and a personal group for that user. The personal group takes the same name as the user, and is set as the user's primary group.",
        new SequenceType[] {
            new FunctionParameterSequenceType("username", Type.STRING, Cardinality.EXACTLY_ONE, "The User's username."),
            new FunctionParameterSequenceType("password", Type.STRING, Cardinality.EXACTLY_ONE, "The User's password."),
            new FunctionParameterSequenceType("groups", Type.STRING, Cardinality.ZERO_OR_MORE, "Any supplementary groups of which the user should be a member."),
            new FunctionParameterSequenceType("full-name", Type.STRING, Cardinality.EXACTLY_ONE, "The full name of the user."),
            new FunctionParameterSequenceType("description", Type.STRING, Cardinality.EXACTLY_ONE, "A description of the user.")
        },
        new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
    );

    public final static FunctionSignature FNS_REMOVE_ACCOUNT = new FunctionSignature(
        qnRemoveAccount,
        "Removes a User Account. If the user has a personal group you are responsible for removing that separately through sm:remove-group. ",
        new SequenceType[] {
            new FunctionParameterSequenceType("username", Type.STRING, Cardinality.EXACTLY_ONE, "The User's username.")
        },
        new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
    );

    public final static FunctionSignature FNS_PASSWD = new FunctionSignature(
            qnPasswd,
            "Changes the password of a User Account.",
            new SequenceType[] {
                new FunctionParameterSequenceType("username", Type.STRING, Cardinality.EXACTLY_ONE, "The User's username."),
                new FunctionParameterSequenceType("password", Type.STRING, Cardinality.EXACTLY_ONE, "The User's new password."),
            },
            new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
    );

    public final static FunctionSignature FNS_PASSWD_HASH = new FunctionSignature(
            qnPasswdHash,
            "Changes the password of a User Account by directly setting the stored digest password. The use-case for this function is migrating a user from one eXist instance to another.",
            new SequenceType[] {
                new FunctionParameterSequenceType("username", Type.STRING, Cardinality.EXACTLY_ONE, "The User's username."),
                new FunctionParameterSequenceType("password-digest", Type.STRING, Cardinality.EXACTLY_ONE, "The encoded digest of the User's new password (assumes eXist's default digest algorithm)."),
            },
            new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
    );

    
    public AccountManagementFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {

        final DBBroker broker = getContext().getBroker();
        final Subject currentUser = broker.getCurrentSubject();
        final SecurityManager securityManager = broker.getBrokerPool().getSecurityManager();

        final String username = args[0].getStringValue();

        try {
            if(isCalledAs(qnRemoveAccount.getLocalPart())) {
                /* remove account */
                if(!currentUser.hasDbaRole()) {
                    throw new XPathException(this, "Only a DBA user may remove accounts.");
                }
                
                if(!securityManager.hasAccount(username)) {
                    throw new XPathException(this, "The user account with username " + username + " does not exist.");
                }

                if(currentUser.getName().equals(username)) {
                    throw new XPathException(this, "You cannot remove yourself i.e. the currently logged in user.");
                }

                securityManager.deleteAccount(username);

            } else {

                final String password = args[1].getStringValue();

                if(isCalledAs(qnPasswd.getLocalPart()) || isCalledAs(qnPasswdHash.getLocalPart())) {
                    /* change password */

                    if(!(currentUser.getName().equals(username) || currentUser.hasDbaRole())) {
                        throw new XPathException(this, "You may only change your own password, unless you are a DBA.");
                    }

                    final Account account = securityManager.getAccount(username);

                    if(isCalledAs(qnPasswdHash.getLocalPart())) {
                        account.setCredential(new Password(account, Password.DEFAULT_ALGORITHM, password));
                    } else {
                        account.setPassword(password);
                    }

                    securityManager.updateAccount(account);
                    
                } else if(isCalledAs(qnCreateAccount.getLocalPart())) {
                    /* create account */
                    if(!currentUser.hasDbaRole()) {
                        throw new XPathException(this, "You must be a DBA to create a User Account.");
                    }
                    
                    if(securityManager.hasAccount(username)) {
                        throw new XPathException(this, "The user account with username " + username + " already exists.");
                    }

                    final Account user = new UserAider(username);
                    user.setPassword(password);

                    if(getSignature().getArgumentCount() >= 5) {
                        //set metadata values if present
                        user.setMetadataValue(AXSchemaType.FULLNAME, args[getSignature().getArgumentCount() - 2].toString());
                        user.setMetadataValue(EXistSchemaType.DESCRIPTION, args[getSignature().getArgumentCount() - 1].toString());
                    }

                    final String[] subGroups;
                    if(getSignature().getArgumentCount() == 3 || getSignature().getArgumentCount() == 5) {
                        //create the personal group
                        final Group group = new GroupAider(username);
                        group.setMetadataValue(EXistSchemaType.DESCRIPTION, "Personal group for " + username);
                        group.addManager(currentUser);
                        securityManager.addGroup(broker, group);

                        //add the personal group as the primary group
                        user.addGroup(username);

                        subGroups = getGroups(args[2]);
                    } else {
                        //add the primary group as the primary group
                        final String primaryGroup = args[2].getStringValue();
                        if (primaryGroup == null || primaryGroup.isEmpty()) {
                            throw new XPathException(this, "You must specify a primary group for the user.");
                        }
                        user.addGroup(primaryGroup);

                        subGroups = getGroups(args[3]);
                    }

                    for (String subGroup : subGroups) {
                        user.addGroup(subGroup);
                    }

                    //create the account
                    securityManager.addAccount(user);

                    //if we created a personal group, then add the new account as a manager of their personal group
                    if(getSignature().getArgumentCount() == 3 || getSignature().getArgumentCount() == 5) {
                        final Group group = securityManager.getGroup(username);
                        group.addManager(securityManager.getAccount(username));
                        securityManager.updateGroup(group);
                    }
                } else {
                    throw new XPathException(this, "Unknown function call: " + getSignature());
                }
            }
        } catch(final PermissionDeniedException | EXistException pde) {
            throw new XPathException(this, pde);
        }
        return Sequence.EMPTY_SEQUENCE;
    }

    private String[] getGroups(final Sequence seq) {
        final String[] groups = new String[seq.getItemCount()];
        for(int i = 0; i < seq.getItemCount(); i++) {
            groups[i] = seq.itemAt(i).toString();
        }
        return groups;
    }
}
