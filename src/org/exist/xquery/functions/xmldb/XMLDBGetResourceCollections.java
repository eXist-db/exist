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
import org.exist.dom.DocumentImpl;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.exist.xquery.value.StringValue;
import org.exist.util.Lock;
import org.exist.storage.DBBroker;

import java.util.Iterator;



public class XMLDBGetResourceCollections extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("get-resource-collections", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Returns the resource of collection",
			new SequenceType[] {
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE));
	
	public XMLDBGetResourceCollections(XQueryContext context) {
		super(context, signature);
	}
	
/* (non-Javadoc)
 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
 *
 */
	
	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
		DBBroker broker = null;
        broker =context.getBroker();
        
		String collectionURI = args[0].getStringValue();
		Collection collection = null;
		collection = broker.openCollection(collectionURI, Lock.READ_LOCK);
	
		ValueSequence r = new ValueSequence();	
		
		if (collection == null) {
			throw new XPathException(getASTNode(), "Collection " + collectionURI + " does not exist");
		}
		
		
		String resource;
		int p;
		for (Iterator i = collection.iterator(broker); i.hasNext(); ) {
			resource = ((DocumentImpl) i.next()).getFileName();
			p = resource.lastIndexOf('/');
			r.add(new StringValue(p < 0 ? resource : resource.substring(p + 1)));
		}
        collection.release();
		return r;
	}

}
