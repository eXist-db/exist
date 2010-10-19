/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2010 The eXist Project
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
 *  
 *  $Id$
 */
package org.exist.xquery.functions.xmldb;

import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.security.Account;
import org.exist.security.AuthenticationException;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.security.internal.aider.GroupAider;
import org.exist.storage.DBBroker;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author Adam Retter <adam@existsolutions.com>
 */
public class XMLDBCreateGroup extends BasicFunction {
	
    protected static final Logger logger = Logger.getLogger(XMLDBCreateUser.class);

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("create-group", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Create a new user group. $group is the group name.",
            new SequenceType[]{
                new FunctionParameterSequenceType("group", Type.STRING, Cardinality.EXACTLY_ONE, "The group name")
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true() or false() indicating the outcome of the operation"),
            "Use xmldb:create-group($group, $group-manager) instead."
        ),

        new FunctionSignature(
            new QName("create-group", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Create a new user group, with an initial member. $group is the group name, $group-manager-username is the groups manager.",
            new SequenceType[]{
                new FunctionParameterSequenceType("group", Type.STRING, Cardinality.EXACTLY_ONE, "The group name"),
                new FunctionParameterSequenceType("group-manager-username", Type.STRING, Cardinality.ONE_OR_MORE, "The name of the user who will be the groups manager")
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true() or false() indicating the outcome of the operation")),
    };
	
    /**
     * @param context
     */
    public XMLDBCreateGroup(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet,
     *         org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    @Override
    public Sequence eval(Sequence args[], Sequence contextSequence) throws XPathException {

        String groupName = args[0].getStringValue();

        if(context.getUser().getUsername().equals("guest") || groupName.equals("dba")) {
            XPathException xPathException = new XPathException(this, "Permission denied, calling user '" + context.getUser().getName() + "' must be an authenticated user to call this function.");
            logger.error("Invalid user", xPathException);
            throw xPathException;
        }

        logger.info("Attempting to create group " + groupName);

        Group group = new GroupAider(groupName);

        DBBroker broker = context.getBroker();
        Subject currentUser = broker.getUser();

	try {

            SecurityManager sm = broker.getBrokerPool().getSecurityManager();

            if(args.length == 2) {


                //find the group managers, this makes sure they all exist first!
                List<Account> groupManagerAccounts = new ArrayList<Account>();
                for(SequenceIterator i = args[1].iterate(); i.hasNext(); ) {
                    String groupManager = i.nextItem().getStringValue();

                    Account groupManagerAccount = sm.getAccount(broker.getUser(), groupManager);
                    if(groupManagerAccount == null) {
                        logger.error("Could not find the user: " + groupManager);
                        return BooleanValue.FALSE;
                    }
                    groupManagerAccounts.add(groupManagerAccount);
                }

                //TODO remove this once the security implementation supports group managers
                //elevate to system user, so we can add groups to the user
                Subject systemUser = sm.authenticate(SecurityManager.SYSTEM, "");
                broker.setUser(systemUser);

                //create the group
                group = sm.addGroup(group);

                //add the managers to the group
                for(Account groupManagerAccount : groupManagerAccounts) {
                    groupManagerAccount.addGroup(group);
                    sm.updateAccount(broker.getUser(), groupManagerAccount);
                }
            } else {
                //deprecated, create the group
                group = sm.addGroup(group);
            }

            return BooleanValue.TRUE;

	} catch (PermissionDeniedException pde) {
	    logger.error("Failed to create group: " + group, pde);
        } catch (EXistException exe) {
            logger.error("Failed to create group: " + group, exe);
        } catch(AuthenticationException ae) {
            logger.error("Failed to create group: " + group, ae);
        } finally {
            //restore the original user
            broker.setUser(currentUser);
        }
        
        return BooleanValue.FALSE;
    }
}