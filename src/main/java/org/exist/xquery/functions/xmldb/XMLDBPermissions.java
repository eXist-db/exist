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
import org.exist.security.Permission;
import org.exist.xmldb.UserManagementService;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.securitymanager.PermissionsFunction;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 * @author gvalentino
 *
 */
@Deprecated
public class XMLDBPermissions extends XMLDBAbstractCollectionManipulator {
	protected static final FunctionParameterSequenceType ARG_COLLECTION = new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collection-uri");
	protected static final FunctionParameterSequenceType ARG_RESOURCE = new FunctionParameterSequenceType("resource", Type.STRING, Cardinality.EXACTLY_ONE, "The resource");
	protected static final Logger logger = LogManager.getLogger(XMLDBPermissions.class);
	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("get-permissions", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Returns the permissions assigned to the collection $collection-uri. " +
            XMLDBModule.COLLECTION_URI,
			new SequenceType[] { ARG_COLLECTION },
			new FunctionReturnSequenceType(Type.INT, Cardinality.ZERO_OR_ONE, "the collection permissions"),
            PermissionsFunction.FNS_GET_PERMISSIONS
        ),
		new FunctionSignature(
			new QName("get-permissions", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Returns the permissions assigned to the resource $resource " +
			"in collection $collection-uri. " +
            XMLDBModule.COLLECTION_URI,
			new SequenceType[] { ARG_COLLECTION, ARG_RESOURCE },
			new FunctionReturnSequenceType(Type.INT, Cardinality.ZERO_OR_ONE, "the resource permissions"),
            PermissionsFunction.FNS_GET_PERMISSIONS
		)
	};
	
	public XMLDBPermissions(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence evalWithCollection(Collection collection, Sequence[] args, Sequence contextSequence)
		throws XPathException {
		try {
			final Permission perm = getPermissions(collection, args);
			return new IntegerValue(perm.getMode(), Type.INT);
        } catch (final XMLDBException xe) {
            logger.error("Unable to retrieve resource permissions");
            throw new XPathException(this, "Unable to retrieve resource permissions", xe);
        }
	}

	/**
	 * @param collection
	 * @param args
	 * @return permission
	 * @throws XMLDBException
	 * @throws XPathException
	 */
	protected Permission getPermissions(Collection collection, Sequence[] args)
        throws XMLDBException, XPathException {
		final UserManagementService ums = (UserManagementService) collection.getService("UserManagementService", "1.0");
		Permission perm;
		if(getSignature().getArgumentCount() == 2) {
		    final Resource res = collection.getResource(args[1].getStringValue());
		    if (res != null) {
		        perm = ums.getPermissions(res);
		    } else {
		        throw new XPathException(this, "Unable to locate resource "+args[1].getStringValue());
		    }
		} else {
			perm = ums.getPermissions(collection);
		}
		return perm;
	}

}
