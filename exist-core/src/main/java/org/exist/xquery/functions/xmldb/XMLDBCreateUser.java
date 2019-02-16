/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
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
import org.exist.dom.QName;
import org.exist.security.internal.aider.UserAider;
import org.exist.xmldb.LocalCollection;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.securitymanager.AccountManagementFunction;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * @author wolf
 * @author Luigi P. Bai, finder@users.sf.net, 2004
 * 
 */
@Deprecated
public class XMLDBCreateUser extends BasicFunction {

    protected static final Logger logger = LogManager.getLogger(XMLDBCreateUser.class);

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("create-user", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Create a new user, $user-id, in the database. "
            + XMLDBModule.NEED_PRIV_USER
            + " $user-id is the username, $password is the password, "
            + "$groups is the sequence of group memberships. "
            + "The first group in the sequence is the primary group."
            + "$home-collection-uri is the home collection URI."
            + XMLDBModule.COLLECTION_URI, 
            new SequenceType[] {
                new FunctionParameterSequenceType("user-id", Type.STRING, Cardinality.EXACTLY_ONE, "The user-id"),
                new FunctionParameterSequenceType("password", Type.STRING, Cardinality.EXACTLY_ONE, "The password"),
                new FunctionParameterSequenceType("groups", Type.STRING, Cardinality.ONE_OR_MORE, "The group memberships"),
                new FunctionParameterSequenceType("home-collection-uri", Type.STRING, Cardinality.ZERO_OR_ONE, "The home collection URI") 
            }, 
            new SequenceType(Type.ITEM, Cardinality.EMPTY),
            "$home-collection-uri has no effect since 2.0. You should use the sm:create-account function from the SecurityManager module instead."
        ),
        new FunctionSignature(
            new QName("create-user", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Create a new user, $user-id, in the database. "
            + XMLDBModule.NEED_PRIV_USER
            + " $user-id is the username, $password is the password, "
            + "$groups is the sequence of group memberships. "
            + "The first group in the sequence is the primary group."
            + XMLDBModule.COLLECTION_URI, 
            new SequenceType[] {
                new FunctionParameterSequenceType("user-id", Type.STRING, Cardinality.EXACTLY_ONE, "The user-id"),
                new FunctionParameterSequenceType("password", Type.STRING, Cardinality.EXACTLY_ONE, "The password"),
                new FunctionParameterSequenceType("groups", Type.STRING, Cardinality.ONE_OR_MORE, "The group memberships")
            }, 
            new SequenceType(Type.ITEM, Cardinality.EMPTY),
            AccountManagementFunction.FNS_CREATE_ACCOUNT_WITH_PERSONAL_GROUP_WITH_METADATA
        )
    };

	/**
	 * @param context
	 */
	public XMLDBCreateUser(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet,
	 * org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence args[], Sequence contextSequence)
			throws XPathException {

		if (!context.getSubject().hasDbaRole()) {
			final XPathException xPathException = new XPathException(this,
					"Permission denied, calling user '"
							+ context.getSubject().getName()
							+ "' must be a DBA to call this function.");
			logger.error("Invalid user", xPathException);
			throw xPathException;
		}

		final String user = args[0].getStringValue();
		final String pass = args[1].getStringValue();

		logger.info("Attempting to create user " + user);

		final UserAider userObj = new UserAider(user);
		userObj.setPassword(pass);

		// changed by wolf: the first group is always the primary group, so we
		// don't need
		// an additional argument
		final Sequence groups = args[2];
		final int len = groups.getItemCount();
		for (int x = 0; x < len; x++) {
                    userObj.addGroup(groups.itemAt(x).getStringValue());
                }
		
		Collection collection = null;
		try {
			collection = new LocalCollection(
					context.getSubject(), 
					context.getBroker().getBrokerPool(), 
					XmldbURI.ROOT_COLLECTION_URI);
			final UserManagementService ums = (UserManagementService) collection.getService("UserManagementService", "1.0");
			ums.addAccount(userObj);

		} catch (final XMLDBException xe) {
			logger.error("Failed to create user: " + user);
			if (logger.isDebugEnabled())
				{logger.debug("Failed to create user: " + user, xe);}

			throw new XPathException(this, "Failed to create new user '" + user + "' by "+context.getSubject().getName(), xe);
		} finally {
			if (null != collection)
				try {
					collection.close();
				} catch (final XMLDBException e) { /* ignore */
				}
		}
		return Sequence.EMPTY_SEQUENCE;
	}
}
