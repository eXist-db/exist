/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.functions.xmldb;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

import static org.exist.xquery.XPathException.execAndAddErrorIfMissing;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 *
 */
public class XMLDBRemove extends XMLDBAbstractCollectionManipulator {
	protected static final Logger logger = LogManager.getLogger(XMLDBRemove.class);
	public final static FunctionSignature[] signatures = {
		new FunctionSignature(
			new QName("remove", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Removes the collection $collection-uri and its contents from the database. " +
            XMLDBModule.COLLECTION_URI,
            new SequenceType[] {
                new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collection URI")},
            new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
		),
		new FunctionSignature(
			new QName("remove", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Removes the resource $resource from the collection $collection-uri. " +
            XMLDBModule.COLLECTION_URI,
			new SequenceType[] {
                new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collection URI"),
                new FunctionParameterSequenceType("resource", Type.STRING, Cardinality.EXACTLY_ONE, "The resource")},
            new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
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
		final Expression expression = this;
		if(getSignature().getArgumentCount() == 2) {
			final String doc = execAndAddErrorIfMissing(this, () -> new AnyURIValue(expression, args[1].itemAt(0).getStringValue()).toXmldbURI().toString());
			try {
				final Resource resource = collection.getResource(doc);
				if (resource == null) {
					logger.error("Resource {} not found", doc);
                    throw new XPathException(this, "Resource " + doc + " not found");
                }
				collection.removeResource(resource);
			} catch (final XMLDBException e) {
                logger.error(e.getMessage());
                throw new XPathException(this, "XMLDB exception caught: " + e.getMessage(), e);
			}
		} else {
			try {
				final CollectionManagementService service = collection.getService(CollectionManagementService.class);
				service.removeCollection(collection.getName());
			} catch (final XMLDBException e) {
				logger.error("Cannot remove collection: {}", e.getMessage());
                throw new XPathException(this, "Cannot remove collection: " + e.getMessage(), e);
			}
		}
		return Sequence.EMPTY_SEQUENCE;
	}

}
