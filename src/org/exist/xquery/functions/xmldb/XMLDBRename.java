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
 *  $Id$
 */
package org.exist.xquery.functions.xmldb;

import java.net.URISyntaxException;
import org.exist.dom.QName;
import org.exist.xmldb.CollectionManagementServiceImpl;
import org.exist.xmldb.XmldbURI;
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
public class XMLDBRename extends XMLDBAbstractCollectionManipulator {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("rename", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
				"Rename a collection $a. The collection can be specified either as " +
				"a simple collection path or an XMLDB URI.",
				new SequenceType[] {
						new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
                       new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)},
                       new SequenceType(Type.ITEM, Cardinality.EMPTY)
		),
		new FunctionSignature(
			new QName("rename", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Rename a resource $b in the collection specified in $a with name in $c. " +
            "The collection can be either specified as a simple collection path or " +
            "an XMLDB URI.",
			new SequenceType[] {
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
                   new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)},
                   new SequenceType(Type.ITEM, Cardinality.EMPTY)
		)
	};
	
	public XMLDBRename(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence evalWithCollection(Collection collection, Sequence[] args, Sequence contextSequence) throws XPathException {
		if(getSignature().getArgumentCount() == 3) {
			XmldbURI doc = new AnyURIValue(args[1].itemAt(0).getStringValue()).toXmldbURI();
			try {
				Resource resource = collection.getResource(doc.toString());
				if (resource == null)
					throw new XPathException(this, "Resource " + doc + " not found");
               String newName = args[2].itemAt(0).getStringValue();
			   CollectionManagementServiceImpl service = (CollectionManagementServiceImpl)
					collection.getService("CollectionManagementService", "1.0");
				service.moveResource(doc, (XmldbURI) null, 
                        XmldbURI.xmldbUriFor(newName));
			} catch (XMLDBException e) {
				throw new XPathException(this, "XMLDB exception caught: " + e.getMessage(), e);
                
			} catch (URISyntaxException e) {
                throw new XPathException(this, "URI exception: " + e.getMessage(), e);
            }

		} else {
			try {
                String newName = args[1].itemAt(0).getStringValue();
				CollectionManagementServiceImpl service = (CollectionManagementServiceImpl)
					collection.getService("CollectionManagementService", "1.0");
				service.move(XmldbURI.xmldbUriFor(collection.getName()),null,
                        XmldbURI.xmldbUriFor(newName));

			} catch (XMLDBException e) {
				throw new XPathException(this, "Cannot rename collection: " + e.getMessage(), e);
                
			} catch (URISyntaxException e) {
                throw new XPathException(this, "URI exception: " + e.getMessage(), e);
            }
		}
		return Sequence.EMPTY_SEQUENCE;
	}

}
