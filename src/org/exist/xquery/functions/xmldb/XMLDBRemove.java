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
import org.exist.xquery.value.FunctionReturnSequenceType;
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
				"Remove a collection $collection-uri. The collection can be specified either as " +
				"a simple collection path or an XMLDB URI.",
				new SequenceType[] {
                new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "the collection-uri")},
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.EMPTY, "empty item sequence")
		),
		new FunctionSignature(
			new QName("remove", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Remove a resource from the collection specified in $collection-uri. The collection " +
			"can be either specified as a simple collection path or an XMLDB URI.",
			new SequenceType[] {
                new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "the collection-uri"),
                new FunctionParameterSequenceType("resource", Type.STRING, Cardinality.EXACTLY_ONE, "the resource")},
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.EMPTY, "empty item sequence")
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
		logger.info("Entering " + XMLDBModule.PREFIX + ":" + getName().getLocalName());
		if(getSignature().getArgumentCount() == 2) {
			String doc = new AnyURIValue(args[1].itemAt(0).getStringValue()).toXmldbURI().toString();
			try {
				Resource resource = collection.getResource(doc);
				if (resource == null) {
                    logger.error("Resource " + doc + " not found");
                    logger.info("Exiting " + XMLDBModule.PREFIX + ":" + getName().getLocalName());			
                    throw new XPathException(this, "Resource " + doc + " not found");
                }
				collection.removeResource(resource);
			} catch (XMLDBException e) {
                logger.error(e.getMessage());
                logger.info("Exiting " + XMLDBModule.PREFIX + ":" + getName().getLocalName());		
                throw new XPathException(this, "XMLDB exception caught: " + e.getMessage(), e);
			}
		} else {
			try {
				CollectionManagementService service = (CollectionManagementService)
					collection.getService("CollectionManagementService", "1.0");
				service.removeCollection(collection.getName());
			} catch (XMLDBException e) {
                logger.error("Cannot remove collection: " + e.getMessage());
                logger.info("Exiting " + XMLDBModule.PREFIX + ":" + getName().getLocalName());	
                throw new XPathException(this, "Cannot remove collection: " + e.getMessage(), e);
			}
		}
        logger.info("Exiting " + XMLDBModule.PREFIX + ":" + getName().getLocalName());
		return Sequence.EMPTY_SEQUENCE;
	}

}
