/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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
package org.exist.xpath.functions.xmldb;

import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.xpath.Cardinality;
import org.exist.xpath.Function;
import org.exist.xpath.FunctionSignature;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.JavaObjectValue;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

/**
 * @author wolf
 */
public class XMLDBCreateCollection extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("create-collection", XMLDB_FUNCTION_NS, "xmldb"),
			new SequenceType[] {
				new SequenceType(Type.JAVA_OBJECT, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)},
			new SequenceType(Type.JAVA_OBJECT, Cardinality.ZERO_OR_ONE));

	/**
	 * @param context
	 * @param signature
	 */
	public XMLDBCreateCollection(StaticContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(
		DocumentSet docs,
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		JavaObjectValue obj =
			(JavaObjectValue) getArgument(0)
				.eval(docs, contextSequence, contextItem)
				.itemAt(0);
		String collectionName = getArgument(1).eval(docs, contextSequence, contextItem).getStringValue(); 
		if (!(obj.getObject() instanceof Collection))
			throw new XPathException("Argument 1 should be an instance of org.xmldb.api.base.Collection");
		Collection collection = (Collection) obj.getObject();
		try {
			CollectionManagementService mgtService = (CollectionManagementService)
				collection.getService("CollectionManagementService", "1.0");
			Collection newCollection = mgtService.createCollection(collectionName);
			if(newCollection == null)
				return Sequence.EMPTY_SEQUENCE;
			else
				return new JavaObjectValue(newCollection);
		} catch (XMLDBException e) {
			LOG.warn("failed to create new collection " + collectionName + ": " + e.getMessage(), e);
			return Sequence.EMPTY_SEQUENCE;
		}
	}

}
