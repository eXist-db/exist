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
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.functions.Function;
import org.exist.xpath.functions.FunctionSignature;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.JavaObjectValue;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/**
 * @author wolf
 */
public class XMLDBStore extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("store", XMLDB_FUNCTION_NS),
			new SequenceType[] {
				new SequenceType(Type.JAVA_OBJECT, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE)},
			new SequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE));

	/**
	 * @param context
	 * @param signature
	 */
	public XMLDBStore(StaticContext context) {
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
		String docName =
			getArgument(1)
				.eval(docs, contextSequence, contextItem)
				.getStringValue();
		Item item =
			getArgument(2).eval(docs, contextSequence, contextItem).itemAt(0);

		if (!(obj.getObject() instanceof Collection))
			throw new XPathException("Argument 3 should be an instance of org.xmldb.api.base.Collection");
		Collection collection = (Collection) obj.getObject();
		try {
			XMLResource resource =
				(XMLResource) collection.createResource(docName, "XMLResource");
			ContentHandler handler = resource.setContentAsSAX();
			handler.startDocument();
			item.toSAX(context.getBroker(), handler);
			handler.endDocument();
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
