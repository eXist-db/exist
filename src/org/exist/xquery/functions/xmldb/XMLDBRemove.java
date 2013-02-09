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
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 *
 */
public class XMLDBRemove extends XMLDBAbstractCollectionManipulator {
	protected static final Logger logger = Logger.getLogger(XMLDBRemove.class);
	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("remove", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Removes the collection $collection-uri and its contents from the database. " +
            XMLDBModule.COLLECTION_URI,
            new SequenceType[] {
                new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collection URI")},
            new SequenceType(Type.ITEM, Cardinality.EMPTY)
		),
		new FunctionSignature(
			new QName("remove", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Removes the resource $resource from the collection $collection-uri. " +
            XMLDBModule.COLLECTION_URI,
			new SequenceType[] {
                new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collection URI"),
                new FunctionParameterSequenceType("resource", Type.STRING, Cardinality.EXACTLY_ONE, "The resource")},
            new SequenceType(Type.ITEM, Cardinality.EMPTY)
		)
	};
	
	public XMLDBRemove(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence evalWithCollection(Collection collection, Sequence[] args, Sequence contextSequence)
		throws XPathException {
		if(getSignature().getArgumentCount() == 2) {
			final String doc = new AnyURIValue(args[1].itemAt(0).getStringValue()).toXmldbURI().toString();
			try {
				final Resource resource = collection.getResource(doc);
				if (resource == null) {
                    logger.error("Resource " + doc + " not found");
                    throw new XPathException(this, "Resource " + doc + " not found");
                }
				collection.removeResource(resource);
			} catch (final XMLDBException e) {
                logger.error(e.getMessage());
                throw new XPathException(this, "XMLDB exception caught: " + e.getMessage(), e);
			}
		} else {
			try {
				final CollectionManagementService service = (CollectionManagementService)
					collection.getService("CollectionManagementService", "1.0");
				service.removeCollection(collection.getName());
			} catch (final XMLDBException e) {
                logger.error("Cannot remove collection: " + e.getMessage());
                throw new XPathException(this, "Cannot remove collection: " + e.getMessage(), e);
			}
		}
		return Sequence.EMPTY_SEQUENCE;
	}

}
