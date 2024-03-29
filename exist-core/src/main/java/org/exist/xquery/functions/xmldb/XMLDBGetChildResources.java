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
import org.exist.xquery.value.ValueSequence;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;



public class XMLDBGetChildResources extends XMLDBAbstractCollectionManipulator {
	
	protected static final Logger logger = LogManager.getLogger(XMLDBGetChildResources.class);

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("get-child-resources", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Returns the names of the child resources in collection $collection-uri. " +
            XMLDBModule.COLLECTION_URI,
			new SequenceType[] {
					new FunctionParameterSequenceType("collection-uri", Type.ITEM, Cardinality.EXACTLY_ONE, "The collection URI")
			},
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "the sequence of resource names"));
	
	public XMLDBGetChildResources(XQueryContext context) {
		super(context, signature);
	}
	
	//TODO decode names?
	public Sequence evalWithCollection(Collection collection, Sequence[] args, Sequence contextSequence)
		throws XPathException {
		final ValueSequence result = new ValueSequence();
		try {
			for (String s : collection.listResources()) {
				result.add(new StringValue(this, s));
			}
			return result;
		} catch (final XMLDBException e) {
			throw new XPathException(this, "Failed to retrieve child resources", e);
		}
	}
}
