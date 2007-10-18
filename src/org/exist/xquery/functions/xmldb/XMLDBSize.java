/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.xmldb;

import org.exist.dom.QName;
import org.exist.xmldb.EXistResource;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

/**
 * @author wolf
 */
public class XMLDBSize extends XMLDBAbstractCollectionManipulator {

	public final static FunctionSignature signature =
        new FunctionSignature(
			new QName("size", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Returns the estimated size of a resource (in bytes) in the collection specified by $a. " +
			"The estimation is based on the number of pages occupied by a resource. If the " +
			"document is serialized back to a string, it's size may be different, as parts of the " +
			"structural information are stored in compressed form. " +
			"The collection can be passed as a simple collection " +
			"path or an XMLDB URI.",
			new SequenceType[] {
                new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE),
                new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.LONG, Cardinality.EXACTLY_ONE)
        );
	
	public XMLDBSize(XQueryContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.functions.xmldb.XMLDBAbstractCollectionManipulator#evalWithCollection(org.xmldb.api.base.Collection, org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	protected Sequence evalWithCollection(Collection collection, Sequence[] args,
			Sequence contextSequence) throws XPathException {
		try {
			Resource resource = collection.getResource(new AnyURIValue(args[1].getStringValue()).toXmldbURI().toString());
			return new IntegerValue(((EXistResource)resource).getContentLength(), Type.LONG);
		} catch (XMLDBException e) {
			throw new XPathException(getASTNode(), "Failed to retrieve size: " + e.getMessage(), e);
		}
	}
}
