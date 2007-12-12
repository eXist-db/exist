/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2006 The eXist team
 *  http://exist-db.org
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
 *  along with this program; if not, write to the Free Software Foundation
 *  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id: XMLDBRemove.java 3309 2006-04-26 14:02:17Z chrisgeorg $
 */

package org.exist.xquery.functions.xmldb;

import org.exist.dom.QName;
import org.exist.xmldb.CollectionManagementServiceImpl;
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

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 *
 */
public class XMLDBMove extends XMLDBAbstractCollectionManipulator {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("move", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
				"Move a collection $a. The collection can be specified either as " +
				"a simple collection path or an XMLDB URI.",
				new SequenceType[] {
						new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
                       new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)},
                       new SequenceType(Type.ITEM, Cardinality.EMPTY)
		),
		new FunctionSignature(
			new QName("move", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Move a resource $c from the collection specified in $a to collection in $b. " +
            "The collection can be either specified as a simple collection path or " +
            "an XMLDB URI.",
			new SequenceType[] {
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
                   new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)},
                   new SequenceType(Type.ITEM, Cardinality.EMPTY)
		)
	};
	
	public XMLDBMove(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence evalWithCollection(Collection collection, Sequence[] args, Sequence contextSequence) throws XPathException {
        String destination = new AnyURIValue(args[1].itemAt(0).getStringValue()).toXmldbURI().toString();
		if(getSignature().getArgumentCount() == 3) {
			String doc = new AnyURIValue(args[2].itemAt(0).getStringValue()).toXmldbURI().toString();
			try {
				Resource resource = collection.getResource(doc);
				if (resource == null)
					throw new XPathException(getASTNode(), "Resource " + doc + " not found");
				CollectionManagementServiceImpl service = (CollectionManagementServiceImpl)
					collection.getService("CollectionManagementService", "1.0");
				service.moveResource(doc,destination,null);
			} catch (XMLDBException e) {
				throw new XPathException(getASTNode(), "XMLDB exception caught: " + e.getMessage(), e);
			}
		} else {
			try {
				CollectionManagementServiceImpl service = (CollectionManagementServiceImpl)
					collection.getService("CollectionManagementService", "1.0");
				service.move(collection.getName(),destination,null);
			} catch (XMLDBException e) {
				throw new XPathException(getASTNode(), "Cannot move collection: " + e.getMessage(), e);
			}
		}
		return Sequence.EMPTY_SEQUENCE;
	}

}
