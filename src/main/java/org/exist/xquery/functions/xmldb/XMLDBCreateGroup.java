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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.security.Account;
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
import org.exist.xquery.functions.securitymanager.GroupManagementFunction;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author Adam Retter <adam@existsolutions.com>
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
@Deprecated
public class XMLDBCreateGroup extends BasicFunction {

	protected static final Logger logger = LogManager.getLogger(XMLDBCreateUser.class);

	public final static FunctionSignature signatures[] = {
			new FunctionSignature(new QName("create-group", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
					"Create a new user group. $group is the group name. The current user will be the groups manager.",
					new SequenceType[] { new FunctionParameterSequenceType("group", Type.STRING, Cardinality.EXACTLY_ONE, "The group name") },
					new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true() or false() indicating the outcome of the operation"),
                    GroupManagementFunction.FNS_CREATE_GROUP),

			new FunctionSignature(
					new QName("create-group", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
					"Create a new user group, with an initial member. $group is the group name, $group-manager-username are the groups managers in addition to the current user.",
					new SequenceType[] {
							new FunctionParameterSequenceType("group", Type.STRING, Cardinality.EXACTLY_ONE, "The group name"),
							new FunctionParameterSequenceType("group-manager-username", Type.STRING, Cardinality.ONE_OR_MORE,
									"The name of the user(s) who will be the groups manager") }, new FunctionReturnSequenceType(Type.BOOLEAN,
							Cardinality.EXACTLY_ONE, "true() or false() indicating the outcome of the operation"),
                    GroupManagementFunction.FNS_CREATE_GROUP_WITH_MANAGERS_WITH_METADATA), };

	/**
	 * @param context
	 */
	public XMLDBCreateGroup(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet,
	 * org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	@Override
	public Sequence eval(Sequence args[], Sequence contextSequence) throws XPathException {

		final String groupName = args[0].getStringValue();

		if ("guest".equals(context.getSubject().getName()) || "dba".equals(groupName)) {
			final XPathException xPathException = 
				new XPathException(this, "Permission denied, calling account '" + context.getSubject().getName()
					+ "' must be an authenticated account to call this function.");
			logger.error("Invalid user", xPathException);
			throw xPathException;
		}

		logger.info("Attempting to create group " + groupName);

		Group group = new GroupAider(groupName);

		final DBBroker broker = context.getBroker();
		final Subject currentUser = broker.getCurrentSubject();

		try {

			final SecurityManager sm = broker.getBrokerPool().getSecurityManager();

			// add the current user as a group manager
			group.addManager(currentUser);

			if (args.length == 2) {
				// add the additional group managers, this also makes sure they
				// all exist first!
				for (final SequenceIterator i = args[1].iterate(); i.hasNext();) {
					final String groupManager = i.nextItem().getStringValue();

					final Account groupManagerAccount = sm.getAccount(groupManager);
					if (groupManagerAccount == null) {
						logger.error("Could not find the user: " + groupManager);
						// throw exception is better -shabanovd
						return BooleanValue.FALSE;
					}
					group.addManager(groupManagerAccount);
				}
			}

			// create the group
			group = sm.addGroup(context.getBroker(), group);

            //TEMP - ESCALATE TO DBA :-(
            //START TEMP - we also need to make every manager a member of the group otherwise
            //they do not show up as group members automatically - this is a design problem because group
            //membership is managed on the user and not the group, this needs to be fixed!
            //see XMLDBAddUserToGroup and XMLDBRemoveUserFromGroup also
            try {
                //escalate
                context.getBroker().pushSubject(sm.getSystemSubject());

                //perform action
                for(final Account manager : group.getManagers()) {
                    manager.addGroup(group);
                    sm.updateAccount(manager);
                }
            } finally {
                context.getBroker().popSubject();
            }
            //END TEMP

			return BooleanValue.TRUE;

		} catch (final PermissionDeniedException pde) {
			throw new XPathException(this, 
					"Permission denied, calling account '" + context.getSubject().getName()
					+ "' do not authorize to call this function.");
		} catch (final EXistException exe) {
			logger.error("Failed to create group: " + group, exe);
		}

		return BooleanValue.FALSE;
	}
}