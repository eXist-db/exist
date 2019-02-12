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
import org.exist.security.Permission;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.securitymanager.PermissionsFunction;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 *
 */
@Deprecated
public class XMLDBGetUserOrGroup extends XMLDBPermissions {
	protected static final FunctionParameterSequenceType OWNER_COLLECTION_ARG = new FunctionParameterSequenceType("collection-uri", Type.ITEM, Cardinality.EXACTLY_ONE, "The collection URI");
	protected static final FunctionParameterSequenceType GROUP_COLLECTION_ARG = new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collection URI");
	protected static final FunctionParameterSequenceType RESOURCE_ARG = new FunctionParameterSequenceType("resource", Type.STRING, Cardinality.EXACTLY_ONE, "The resource");

	protected static final FunctionReturnSequenceType OWNER_RETURN_TYPE = new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the user-id");
	protected static final FunctionReturnSequenceType GROUP_RETURN_TYPE = new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the owner group");

	protected static final Logger logger = LogManager.getLogger(XMLDBGetUserOrGroup.class);
	public final static FunctionSignature getGroupSignatures[] = {
			new FunctionSignature(
				new QName("get-group", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
				"Returns the owner group of the collection $collection-uri. " +
                XMLDBModule.COLLECTION_URI,
				new SequenceType[] { GROUP_COLLECTION_ARG },
				GROUP_RETURN_TYPE,
                PermissionsFunction.FNS_GET_PERMISSIONS
			),
			new FunctionSignature(
				new QName("get-group", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
				"Returns the owner group of the resource $resource in the collection $collection-uri. " +
                XMLDBModule.COLLECTION_URI,
				new SequenceType[] { GROUP_COLLECTION_ARG, RESOURCE_ARG },
				GROUP_RETURN_TYPE,
                PermissionsFunction.FNS_GET_PERMISSIONS
			)
		};
	
	public final static FunctionSignature getOwnerSignatures[] = {
			new FunctionSignature(
				new QName("get-owner", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
				"Returns the owner user-id of the collection $collection-uri. " +
                XMLDBModule.COLLECTION_URI,
				new SequenceType[] { OWNER_COLLECTION_ARG },
				OWNER_RETURN_TYPE,
                PermissionsFunction.FNS_GET_PERMISSIONS
			),
			new FunctionSignature(
				new QName("get-owner", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
				"Returns the owner user-id of the resource $resource in collection $collection-uri. " +
                XMLDBModule.COLLECTION_URI,
				new SequenceType[] { OWNER_COLLECTION_ARG, RESOURCE_ARG },
				OWNER_RETURN_TYPE,
                PermissionsFunction.FNS_GET_PERMISSIONS
			)
		};
	
	public XMLDBGetUserOrGroup(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence evalWithCollection(Collection collection, Sequence[] args, Sequence contextSequence)
		throws XPathException {

		try {
			final Permission perm = getPermissions(collection, args);
			if("get-owner".equals(getSignature().getName().getLocalPart())) {
				return new StringValue(perm.getOwner().getName());
            } else {
				return new StringValue(perm.getGroup().getName());
            }


        } catch (final XMLDBException xe) {
            logger.error("Unable to retrieve resource permissions");
            throw new XPathException(this, "Unable to retrieve resource permissions", xe);
        }
	}

}
