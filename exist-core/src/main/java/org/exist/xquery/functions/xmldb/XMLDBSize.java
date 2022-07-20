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
import org.exist.xmldb.EXistResource;
import org.exist.xquery.*;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

import static org.exist.xquery.XPathException.execAndAddErrorIfMissing;

/**
 * @author wolf
 */
public class XMLDBSize extends XMLDBAbstractCollectionManipulator {
	protected static final Logger logger = LogManager.getLogger(XMLDBSize.class);
	public final static FunctionSignature signature =
        new FunctionSignature(
			new QName("size", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Returns the estimated size of the resource $resource (in bytes) in the collection $collection-uri. " +
			"The estimation is based on the number of pages occupied by the resource. If the " +
			"document is serialized back to a string, its size may be different, since parts of the " +
			"structural information are stored in compressed form. " +
            XMLDBModule.COLLECTION_URI,
			new SequenceType[] {
                new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collection URI"),
                new FunctionParameterSequenceType("resource", Type.STRING, Cardinality.EXACTLY_ONE, "The resource")
			},
			new FunctionReturnSequenceType(Type.LONG, Cardinality.EXACTLY_ONE, "the size of the pages, occupied by the resource, in bytes")
        );
	
	public XMLDBSize(XQueryContext context) {
		super(context, signature);
	}
	
	@Override
	protected Sequence evalWithCollection(final Collection collection, final Sequence[] args,
			final Sequence contextSequence) throws XPathException {
		final Expression expression = this;
        try {
			final Resource resource = collection.getResource(execAndAddErrorIfMissing(this, () -> new AnyURIValue(expression, args[1].getStringValue()).toXmldbURI().toString()));
			return new IntegerValue(this, ((EXistResource)resource).getContentLength(), Type.LONG);
		} catch (final XMLDBException e) {
			logger.error("Failed to retrieve size: {}", e.getMessage());
			throw new XPathException(this, "Failed to retrieve size: " + e.getMessage(), e);
		}
	}
}
