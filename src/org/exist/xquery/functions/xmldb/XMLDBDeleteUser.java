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
import org.exist.security.Subject;
import org.exist.security.Account;
import org.exist.xmldb.LocalCollection;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.securitymanager.AccountManagementFunction;
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
public class XMLDBDeleteUser extends BasicFunction {
	
	protected static final Logger logger = LogManager.getLogger(XMLDBDeleteUser.class);

	public final static FunctionSignature signature = new FunctionSignature(
			new QName("delete-user", XMLDBModule.NAMESPACE_URI,
					XMLDBModule.PREFIX),
			"Deletes an existing user, $user-id, in the database. " +
            "This does not delete the user's home collection. " +
            XMLDBModule.NEED_PRIV_USER_NOT_CURRENT +
            " You cannot delete the owner of the currently running XQuery.",
			new SequenceType[]{
					new FunctionParameterSequenceType("user-id", Type.STRING, Cardinality.EXACTLY_ONE, "The user-id to delete"),
            },
			new SequenceType(Type.ITEM, Cardinality.EMPTY),
            AccountManagementFunction.FNS_REMOVE_ACCOUNT
    );

	/**
	 * @param context
	 */
	public XMLDBDeleteUser(XQueryContext context) {
		super(context, signature);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet,
	 *         org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence args[], Sequence contextSequence)
			throws XPathException {
		
        final String user = args[0].getStringValue();
        
        final Subject contextUser = context.getSubject();
		if (contextUser.hasDbaRole()) {
			if (contextUser.getName().equals(user)) {
				final XPathException xPathException = new XPathException(this, "Permission denied, calling user '" + context.getSubject().getName() + "' must not be deleting itself");
				logger.error("Invalid user", xPathException);
				throw xPathException;
			} else {
		        Collection collection = null;
				try {
		            collection = new LocalCollection(contextUser, context.getBroker().getBrokerPool(), XmldbURI.ROOT_COLLECTION_URI);
					final UserManagementService ums = (UserManagementService) collection.getService("UserManagementService", "1.0");
		            final Account userObj = ums.getAccount(user);
		            if (null != userObj)
		                {ums.removeAccount(userObj);}
				} catch (final XMLDBException xe) {
					throw new XPathException(this, "Failed to remove user " + user, xe);
		        } finally {
		            if (null != collection)
		                try { collection.close(); } catch (final XMLDBException e) { /* ignore */ }
				}
			}
        	
        } else {
			final XPathException xPathException = new XPathException(this, "Permission denied, calling user '" + context.getSubject().getName() + "' must be a DBA to delete a user");
			logger.error("Invalid user", xPathException);
			throw xPathException;
        }
        
        return Sequence.EMPTY_SEQUENCE;
	}
    
}
