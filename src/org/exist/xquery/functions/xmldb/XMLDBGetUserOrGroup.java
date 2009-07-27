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

import org.apache.log4j.Logger;

import org.exist.dom.QName;
import org.exist.security.Permission;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
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
public class XMLDBGetUserOrGroup extends XMLDBPermissions {
	protected static final Logger logger = Logger.getLogger(XMLDBGetUserOrGroup.class);
	public final static FunctionSignature getGroupSignatures[] = {
			new FunctionSignature(
				new QName("get-group", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
				"Returns the owner group of the collection $collection-uri. The collection can be passed as a simple collection "
				+ "path or an XMLDB URI.",
				new SequenceType[] {
                    new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "the collection-uri")
				},
				new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "owner group")
			),
			new FunctionSignature(
				new QName("get-group", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
				"Returns the owner group of a resource in the collection specified by $collection-uri. " +
				"The collection can be passed as a simple collection " +
				"path or an XMLDB URI.",
				new SequenceType[] {
                    new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "the collection-uri"),
                    new FunctionParameterSequenceType("resource", Type.STRING, Cardinality.EXACTLY_ONE, "the resource")
				},
				new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "owner group")
			)
		};
	
	public final static FunctionSignature getOwnerSignatures[] = {
			new FunctionSignature(
				new QName("get-owner", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
				"Returns the owner of a collection $collection-uri. " +
				"The collection can be passed as a simple collection " +
				"path or an XMLDB URI.",
				new SequenceType[] {
                    new FunctionParameterSequenceType("collection-uri", Type.ITEM, Cardinality.EXACTLY_ONE, "the collection-uri")
				},
				new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "user-id")
			),
			new FunctionSignature(
				new QName("get-owner", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
				"Returns the owner of the specified resource $resource in collection $collection-uri. " +
				"The collection can be passed as a simple collection " +
				"path or an XMLDB URI.",
				new SequenceType[] {
                    new FunctionParameterSequenceType("collection-uri", Type.ITEM, Cardinality.EXACTLY_ONE, "the collection-uri"),
                    new FunctionParameterSequenceType("resource", Type.STRING, Cardinality.EXACTLY_ONE, "the resource")
				},
				new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "user-id")
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
		logger.info("Entering " + XMLDBModule.PREFIX + ":" + getName().getLocalName());
		try {
			Permission perm = getPermissions(collection, args);
            logger.info("Exiting " + XMLDBModule.PREFIX + ":" + getName().getLocalName());
			if("get-owner".equals(getSignature().getName().getLocalName())) {
				return new StringValue(perm.getOwner());
            } else {
				return new StringValue(perm.getOwnerGroup());
            }


        } catch (XMLDBException xe) {
            logger.error("Unable to retrieve resource permissions");
            logger.info("Exiting " + XMLDBModule.PREFIX + ":" + getName().getLocalName());
            throw new XPathException(this, "Unable to retrieve resource permissions", xe);
        }
	}

}
