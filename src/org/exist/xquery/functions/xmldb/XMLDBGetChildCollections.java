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
 */
package org.exist.xquery.functions.xmldb;

import org.exist.collections.Collection;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.ValueSequence;
import org.exist.xquery.value.StringValue;

import java.util.Iterator;



public class XMLDBGetChildCollections extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("get-child-collections", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Returns the subcolls of collection",
			new SequenceType[] {
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE));
	
	public XMLDBGetChildCollections(XQueryContext context) {
		super(context, signature);
	}
	
/* (non-Javadoc)
 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
 *
 */
	
	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
		String collectionURI = args[0].getStringValue();
		Collection collection = null;
		collection = context.getBroker().getCollection(collectionURI);
	
		ValueSequence r = new ValueSequence();	
		String child;
			for (Iterator i = collection.collectionIterator(); i.hasNext(); ) 
			{
				child = (String) i.next();
				r.add(new StringValue(child));
			}
		
		return r;
	}

}
