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

import org.apache.log4j.Logger;

import org.exist.dom.QName;
import org.exist.security.User;
import org.exist.xmldb.LocalCollection;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * Various functions to get information about users.
 * /**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 * @author Luigi P. Bai, finder@users.sf.net, 2004
 *
 */
public class XMLDBUserAccess extends BasicFunction {
	protected static final Logger logger = Logger.getLogger(XMLDBUserAccess.class);
	public final static FunctionSignature fnExistsUser = new FunctionSignature(
			new QName("exists-user", XMLDBModule.NAMESPACE_URI,
					XMLDBModule.PREFIX),
			"Returns true if user exists. Requires username.",
			new SequenceType[]{
                new FunctionParameterSequenceType("user-id", Type.STRING, Cardinality.EXACTLY_ONE, "the user-id"),
            },
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true() if user exists, false() otherwise"));
	
	public final static FunctionSignature fnUserGroups = new FunctionSignature(
			new QName("get-user-groups", XMLDBModule.NAMESPACE_URI,
					XMLDBModule.PREFIX),
			"Receives the sequence of groups the specified user is a member of.",
			new SequenceType[]{
                new FunctionParameterSequenceType("user-id", Type.STRING, Cardinality.EXACTLY_ONE, "the user-id"),
            },
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE_OR_MORE, "group memberships"));
	
	//TODO: let users know about signature change from string to any_uri
	public final static FunctionSignature fnUserHome = new FunctionSignature(
			new QName("get-user-home", XMLDBModule.NAMESPACE_URI,
					XMLDBModule.PREFIX),
			"Returns the home collection of the specified user or the empty sequence " +
			"if no home collection is assigned to the user.",
			new SequenceType[]{
                new FunctionParameterSequenceType("user-id", Type.STRING, Cardinality.EXACTLY_ONE, "the user-id"),
            },
			new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.ZERO_OR_ONE, "home collection of user"));

	/**
	 * @param context
	 * @param signature
	 */
	public XMLDBUserAccess(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet,
	 *         org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence args[], Sequence contextSequence)
			throws XPathException {
		logger.info("Entering " + XMLDBModule.PREFIX + ":" + getName().getLocalName());
        String userName = args[0].getStringValue();
        
        Collection collection = null;
		try {
            collection = new LocalCollection(context.getUser(), context.getBroker().getBrokerPool(), XmldbURI.ROOT_COLLECTION_URI, context.getAccessContext());
			UserManagementService ums = (UserManagementService) collection.getService("UserManagementService", "1.0");
			User user = ums.getUser(userName);
			
			if(isCalledAs("exists-user"))
				return null == user ? BooleanValue.FALSE : BooleanValue.TRUE;
			
			if(user == null) {
                logger.error("User not found: " + userName);
                throw new XPathException(this, "User not found: " + userName);
            }
			if(isCalledAs("get-user-groups")) {
				ValueSequence groups = new ValueSequence();
				String[] gl = user.getGroups();
				for (int i = 0; i < gl.length; i++) {
					groups.add(new StringValue(gl[i]));
				}
				return groups;
			// get-user-home
			} else {
				XmldbURI home = user.getHome();
				return null == home ? Sequence.EMPTY_SEQUENCE : new AnyURIValue(home);
			}
		} catch (XMLDBException e) {
            logger.error(e.getMessage());
			throw new XPathException(this, "Failed to query user " + userName, e);
        } finally {
            if (null != collection)
                try { collection.close(); } catch (XMLDBException e) { /* ignore */ }
            logger.info("Exiting " + XMLDBModule.PREFIX + ":" + getName().getLocalName());			
		}
	}
    
}
