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

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.security.Account;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.security.SecurityManager;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author Adam Retter <adam@existsolutions.com>
 */
public class XMLDBRemoveUserFromGroup extends BasicFunction {

    protected static final Logger logger = Logger.getLogger(XMLDBRemoveUserFromGroup.class);

    public final static FunctionSignature signature = new FunctionSignature(
            new QName("remove-user-from-group", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Remove a user from a group. $user is the username. $group is the group name" + XMLDBModule.NEED_PRIV_USER,
            new SequenceType[]{
                new FunctionParameterSequenceType("user", Type.STRING, Cardinality.EXACTLY_ONE, "The user name"),
                new FunctionParameterSequenceType("group", Type.STRING, Cardinality.EXACTLY_ONE, "The group name")
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true() or false() indicating the outcome of the operation"));

    /**
     * @param context
     */
    public XMLDBRemoveUserFromGroup(XQueryContext context) {
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

        if(context.getUser().getUsername().equals("guest")) {
            XPathException xPathException = new XPathException(this, "Permission denied, calling user '" + context.getUser().getName() + "' must be an authenticated user to call this function.");
            logger.error("Invalid user", xPathException);
            throw xPathException;
        }

        String userName = args[0].getStringValue();
        String groupName = args[1].getStringValue();


        if(groupName.equals("dba") && !context.getUser().hasDbaRole()) {
            XPathException xPathException = new XPathException(this, "Permission denied, calling user '" + context.getUser().getName() + "' must be a DBA to remove users from the DBA group.");
            logger.error("Invalid user", xPathException);
            throw xPathException;
        }

        if(!context.getUser().hasGroup(groupName)) {
            XPathException xPathException = new XPathException(this, "Permission denied, calling user '" + context.getUser().getName() + "' must be a memeber of '" + groupName + "' to remove users from the '" + groupName + "' group.");
            logger.error("Invalid user", xPathException);
            throw xPathException;
        }

        logger.info("Attempting to remove user '" + userName + "' from group '" + groupName + "'");

        Subject currentSubject = context.getSubject();
        try {

            //elevate privs
            context.getBroker().setSubject(context.getBroker().getBrokerPool().getSecurityManager().getSystemSubject());

            SecurityManager sm = context.getBroker().getBrokerPool().getSecurityManager();

            Group group = sm.getGroup(context.getBroker().getUser(), groupName);

            Account account = sm.getAccount(context.getBroker().getUser(), userName);

            account.remGroup(groupName);
            sm.updateAccount(context.getBroker().getUser(), account);

            return BooleanValue.TRUE;

        } catch(PermissionDeniedException pde) {
            logger.error("Failed to remove user '" + userName + "' from group '" + groupName + "'", pde);
        } catch(EXistException exe) {
            logger.error("Failed to remove user '" + userName + "' from group '" + groupName + "'", exe);
        } finally {
            context.getBroker().setSubject(currentSubject);
        }

        return BooleanValue.FALSE;
    }
}
