/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2004-2009 The eXist Project
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
import org.exist.security.Account;
import org.exist.xmldb.LocalCollection;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.securitymanager.GroupMembershipFunction;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * @author wolf
 */
@Deprecated
public class XMLDBIsAdmin extends BasicFunction {
	protected static final Logger logger = LogManager.getLogger(XMLDBIsAdmin.class);
	public final static FunctionSignature signature = new FunctionSignature(
			new QName("is-admin-user", XMLDBModule.NAMESPACE_URI,
					XMLDBModule.PREFIX),
			"Returns true() if user $user-id has DBA role, false() otherwise.",
			new SequenceType[]{
                new FunctionParameterSequenceType("user-id", Type.STRING, Cardinality.EXACTLY_ONE, "The user-id"),
            },
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.ZERO_OR_ONE, "true() if user has DBA role, false() otherwise"),
            GroupMembershipFunction.FNS_IS_DBA
    );
	
	public XMLDBIsAdmin(XQueryContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		final String userName = args[0].getStringValue();
        
        Collection collection = null;
		try {
            collection = new LocalCollection(context.getSubject(), context.getBroker().getBrokerPool(), XmldbURI.ROOT_COLLECTION_URI);
			final UserManagementService ums = (UserManagementService) collection.getService("UserManagementService", "1.0");
			final Account user = ums.getAccount(userName);

			if(user == null)
                // todo - why not just return false()? /ljo
				{return Sequence.EMPTY_SEQUENCE;}
			return user.hasDbaRole() ? BooleanValue.TRUE : BooleanValue.FALSE;
		} catch (final XMLDBException xe) {
            logger.error("Failed to access user " + userName);
			throw new XPathException(this, "Failed to access user " + userName, xe);
        } finally {
            if (null != collection)
                try { collection.close(); } catch (final XMLDBException e) { /* ignore */ }
		}
	}

}
