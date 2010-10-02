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
import org.exist.security.SecurityManager;
import org.exist.storage.BrokerPool;

/**
 * @author Adam Retter <adam@existsolutions.com>
 */
public class XMLDBAddUserToGroup extends BasicFunction {
	
    protected static final Logger logger = Logger.getLogger(XMLDBCreateUser.class);

    public final static FunctionSignature signature = new FunctionSignature(
        new QName("add-user-to-group", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
        "Add a user to a group. $user us the username. $group is the group name" + XMLDBModule.NEED_PRIV_USER,
        new SequenceType[]{
            new FunctionParameterSequenceType("user", Type.STRING, Cardinality.EXACTLY_ONE, "The user name"),
            new FunctionParameterSequenceType("group", Type.STRING, Cardinality.EXACTLY_ONE, "The group name")
        },
        new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true() or false() indicating the outcome of the operation")
    );
	
    /**
     * @param context
     */
    public XMLDBAddUserToGroup(XQueryContext context) {
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


        if(groupName.equals("dba") && !context.getUser().hasDbaRole()){
            XPathException xPathException = new XPathException(this, "Permission denied, calling user '" + context.getUser().getName() + "' must be a DBA to add users to the DBA group.");
            logger.error("Invalid user", xPathException);
            throw xPathException;
        }
        
        if (!context.getUser().hasGroup(groupName)) {
            XPathException xPathException = new XPathException(this, "Permission denied, calling user '" + context.getUser().getName() + "' must be a memeber of '"+groupName+"' to add users to the '"+groupName+"' group.");
            logger.error("Invalid user", xPathException);
            throw xPathException;
        }
        
        logger.info("Attempting to add user '" + userName + "' to group '" + groupName + "'");
        
		try {
			BrokerPool database = context.getBroker().getBrokerPool();
			
            SecurityManager sm = context.getBroker().getBrokerPool().getSecurityManager();
            
            Subject currentSubject = database.getSubject();
            try {
            	database.setSubject(sm.getSystemSubject());
            	
            	Group group = sm.getGroup(groupName);
            
            	Account account = sm.getAccount(userName);

	            account.addGroup(group);
	            sm.updateAccount(account);
            } finally {
            	database.setSubject(currentSubject);
            }

            return BooleanValue.TRUE;
				
		} catch (PermissionDeniedException pde) {
			logger.error("Failed to add user '" + userName + "' group '" + groupName + "'", pde);
        } catch (EXistException exe) {
            logger.error("Failed to add user '" + userName + "' group '" + groupName + "'", exe);
        }

        return BooleanValue.FALSE;
    }
}