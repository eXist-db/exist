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
package org.exist.xquery.functions.xmldb;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/**
 * @author wolf
 */
public class XMLDBStore extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("store", ModuleImpl.NAMESPACE_URI, ModuleImpl.PREFIX),
			"Store a node as a new document into the database. The first " +
			"argument specifies the collection object as returned by the collection or " +
			"create-collection functions. The second argument is the name of the new " +
			"document. The third argument is either a node or a string. A node will be " +
			"serialized to SAX. It becomes the root node of the new document.",
			new SequenceType[] {
				new SequenceType(Type.JAVA_OBJECT, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
				new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE)},
			new SequenceType(Type.ITEM, Cardinality.EMPTY));

	/**
	 * @param context
	 * @param signature
	 */
	public XMLDBStore(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(Sequence args[],
		Sequence contextSequence)
		throws XPathException {
		JavaObjectValue obj =
			(JavaObjectValue) args[0].itemAt(0);
		String docName = args[1].getLength() == 0 ? null : args[1].getStringValue();
		if(docName != null && docName.length() == 0)
			docName = null;
		Item item =
			args[2].itemAt(0);

		if (!(obj.getObject() instanceof Collection))
			throw new XPathException("Argument 1 should be an instance of org.xmldb.api.base.Collection");
		Collection collection = (Collection) obj.getObject();
		try {
			XMLResource resource =
				(XMLResource) collection.createResource(docName, "XMLResource");
			if(Type.subTypeOf(item.getType(), Type.STRING)) {
				resource.setContent(item.getStringValue());
			} else if(Type.subTypeOf(item.getType(), Type.NODE)) {
				ContentHandler handler = resource.setContentAsSAX();
				handler.startDocument();
				item.toSAX(context.getBroker(), handler);
				handler.endDocument();
			} else
				throw new XPathException("Data should be either a node or a string");
			collection.storeResource(resource);
		} catch (XMLDBException e) {
			throw new XPathException(
				"XMLDB reported an exception while storing document",
				e);
		} catch (SAXException e) {
			throw new XPathException(
				"SAX reported an exception while storing document",
				e);
		}
		return Sequence.EMPTY_SEQUENCE;
	}
}
