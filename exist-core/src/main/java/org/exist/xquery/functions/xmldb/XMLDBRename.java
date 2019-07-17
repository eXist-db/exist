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

import java.net.URISyntaxException;
import org.exist.dom.QName;
import org.exist.xmldb.EXistCollectionManagementService;
import org.exist.xmldb.XmldbURI;
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

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 *
 */
public class XMLDBRename extends XMLDBAbstractCollectionManipulator {
	protected static final Logger logger = LogManager.getLogger(XMLDBRename.class);
	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("rename", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Renames the collection $source-collection-uri with new name $new-collection-name. " +
            XMLDBModule.COLLECTION_URI,
				new SequenceType[] {
                new FunctionParameterSequenceType("source-collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The source collection URI"),
                new FunctionParameterSequenceType("new-collection-name", Type.STRING, Cardinality.EXACTLY_ONE, "The new collection name")},
            new SequenceType(Type.ITEM, Cardinality.EMPTY)
		),
		new FunctionSignature(
			new QName("rename", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Renames the resource $resource in collection $collection-uri with new name $new-resource-name. " +
            XMLDBModule.COLLECTION_URI,
			new SequenceType[] {
                new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collection URI"),
                new FunctionParameterSequenceType("resource", Type.STRING, Cardinality.EXACTLY_ONE, "The resource"),
                new FunctionParameterSequenceType("new-resource-name", Type.STRING, Cardinality.EXACTLY_ONE, "The new resource name")},
            new SequenceType(Type.ITEM, Cardinality.EMPTY)
		)
	};
	
	public XMLDBRename(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence evalWithCollection(Collection collection, Sequence[] args, Sequence contextSequence)
        throws XPathException {

		if(getSignature().getArgumentCount() == 3) {
			final XmldbURI doc = new AnyURIValue(args[1].itemAt(0).getStringValue()).toXmldbURI();
			try {
				final Resource resource = collection.getResource(doc.toString());
				if (resource == null) {
                    logger.error("Resource " + doc + " not found");

					throw new XPathException(this, "Resource " + doc + " not found");
                }
               final String newName = args[2].itemAt(0).getStringValue();
			   final EXistCollectionManagementService service = (EXistCollectionManagementService)
					collection.getService("CollectionManagementService", "1.0");
				service.moveResource(doc, (XmldbURI) null, 
                        XmldbURI.xmldbUriFor(newName));
			} catch (final XMLDBException e) {
                logger.error(e.getMessage());

				throw new XPathException(this, "XMLDB exception caught: " + e.getMessage(), e);
                
			} catch (final URISyntaxException e) {
                logger.error(e.getMessage());

                throw new XPathException(this, "URI exception: " + e.getMessage(), e);
            }

		} else {
			try {
                final String newName = args[1].itemAt(0).getStringValue();
				final EXistCollectionManagementService service = (EXistCollectionManagementService)
					collection.getService("CollectionManagementService", "1.0");
				service.move(XmldbURI.xmldbUriFor(collection.getName()), null,
                        XmldbURI.xmldbUriFor(newName));

			} catch (final XMLDBException e) {
                logger.error("Cannot rename collection: " + e.getMessage());

				throw new XPathException(this, "Cannot rename collection: " + e.getMessage(), e);
                
			} catch (final URISyntaxException e) {
                logger.error(e.getMessage());

                throw new XPathException(this, "URI exception: " + e.getMessage(), e);
            }
		}
		return Sequence.EMPTY_SEQUENCE;
	}

}
