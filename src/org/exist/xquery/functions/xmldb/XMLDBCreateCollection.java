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
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

/**
 * @author wolf
 */
public class XMLDBCreateCollection extends XMLDBAbstractCollectionManipulator {

	public final static FunctionSignature signature = new FunctionSignature(
			new QName("create-collection", XMLDBModule.NAMESPACE_URI,
					XMLDBModule.PREFIX),
					"Create a new collection as a child of the collection specified in the "
					+ "first argument. The collection can be passed as a simple collection "
					+ "path, an XMLDB URI or as a collection object (obtained from the collection function)."
					+ "The second argument specifies the name of the new "
					+ "collection.",
			new SequenceType[]{
					new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)},
			new SequenceType(Type.JAVA_OBJECT, Cardinality.ZERO_OR_ONE));

	/**
	 * @param context
	 */
	public XMLDBCreateCollection(XQueryContext context) {
		super(context, signature);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet,
	 *         org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence evalWithCollection(Collection collection, Sequence args[], Sequence contextSequence)
			throws XPathException {
		String collectionName = args[1].getStringValue();
		try {
			CollectionManagementService mgtService = (CollectionManagementService) collection
					.getService("CollectionManagementService", "1.0");
			Collection newCollection = mgtService
					.createCollection(new AnyURIValue(collectionName).toXmldbURI().toString());
			if (newCollection == null)
				return Sequence.EMPTY_SEQUENCE;
			else
				return new JavaObjectValue(newCollection);
		} catch (XMLDBException e) {
			throw new XPathException(getASTNode(),
					"failed to create new collection " + collectionName + ": "
							+ e.getMessage(), e);
		}
	}
}
