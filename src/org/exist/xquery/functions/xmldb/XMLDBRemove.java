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
import org.exist.xquery.value.JavaObjectValue;
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
public class XMLDBRemove extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("remove-resource", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
				"Remove a resource from the collection. The first " +
				"argument specifies the collection object as returned by the collection or " +
				"create-collection functions. The second argument is the name of the resource " +
				"to be removed. The resource name may be absolute or relative, but it" +
				"should always point to a child resource of the current collection.",
				new SequenceType[] {
						new SequenceType(Type.JAVA_OBJECT, Cardinality.EXACTLY_ONE),
						new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)},
						new SequenceType(Type.ITEM, Cardinality.EMPTY));
	
	public XMLDBRemove(XQueryContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
		JavaObjectValue obj = (JavaObjectValue)args[0].itemAt(0);
		if (!(obj.getObject() instanceof Collection))
			throw new XPathException(getASTNode(), "Argument 1 should be an instance of org.xmldb.api.base.Collection");
		Collection collection = (Collection) obj.getObject();
		String doc = args[1].getStringValue();
		if(doc.startsWith("/db")) {
			int p =doc.lastIndexOf('/');
			String path = doc.substring(0, p);
			if(p + 1 < doc.length())
				doc = doc.substring(p + 1);
			else
				throw new XPathException(getASTNode(), "No resource name found in " + doc);
		}
		try {
			Resource resource = collection.getResource(doc);
			if (resource == null)
				throw new XPathException(getASTNode(), "Resource " + doc + " not found");
			collection.removeResource(resource);
		} catch (XMLDBException e) {
			throw new XPathException(getASTNode(), "XMLDB exception caught: " + e.getMessage(), e);
		}
		return Sequence.EMPTY_SEQUENCE;
	}

}
