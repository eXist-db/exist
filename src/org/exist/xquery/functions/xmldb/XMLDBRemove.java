/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
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

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("remove", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
				"Remove a collection. The collection can be specified either as " +
				"a simple collection path, an XMLDB URI or a collection object.",
				new SequenceType[] {
						new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE)},
						new SequenceType(Type.ITEM, Cardinality.EMPTY)
		),
		new FunctionSignature(
			new QName("remove", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Remove a resource from the collection specified in $a. The collection " +
			"can be either specified as a simple collection path, an XMLDB URI or " +
			"a collection object.",
			new SequenceType[] {
					new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)},
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
			String doc = new AnyURIValue(args[1].itemAt(0).getStringValue()).toXmldbURI().toString();
			try {
				Resource resource = collection.getResource(doc);
				if (resource == null)
					throw new XPathException(getASTNode(), "Resource " + doc + " not found");
				collection.removeResource(resource);
			} catch (XMLDBException e) {
				throw new XPathException(getASTNode(), "XMLDB exception caught: " + e.getMessage(), e);
			}
		} else {
			try {
				CollectionManagementService service = (CollectionManagementService)
					collection.getService("CollectionManagementService", "1.0");
				service.removeCollection(collection.getName());
			} catch (XMLDBException e) {
				throw new XPathException(getASTNode(), "Cannot remove collection: " + e.getMessage(), e);
			}
		}
		return Sequence.EMPTY_SEQUENCE;
	}

}
