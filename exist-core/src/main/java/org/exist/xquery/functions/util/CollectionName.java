/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
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
package org.exist.xquery.functions.util;

import java.net.URISyntaxException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.QName;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 *
 */
public class CollectionName extends BasicFunction {
	
	protected static final Logger logger = LogManager.getLogger(CollectionName.class);

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("collection-name", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns the name of the collection from a passed node or path string. If the argument is " +
            "a node, the function returns the name of the collection to which the node's document belongs. " +
            "If the argument is a string, it is interpreted as path to a resource and the function returns the " +
            "computed parent collection path for this resource.",
			new SequenceType[] {
					new FunctionParameterSequenceType("node-or-path-string", Type.ITEM, Cardinality.ZERO_OR_ONE, "The document node or a path string.")
			},
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the name of the collection."));
	
	public CollectionName(XQueryContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
		
	    if(args[0].isEmpty()) {
	        return Sequence.EMPTY_SEQUENCE;
	    }
		final Item item = args[0].itemAt(0);
		if(item.getType() == Type.JAVA_OBJECT) {
			final Object o = ((JavaObjectValue) item).getObject();
            if (!(o instanceof Collection))
                {throw new XPathException(this, "Passed Java object should be of type org.xmldb.api.base.Collection");}
            final Collection collection = (Collection)o;
            try {
				return new StringValue(collection.getName());
			} catch (final XMLDBException e) {
				throw new XPathException(this, "Failed to retrieve collection name", e);
			}
        } else if (Type.subTypeOf(item.getType(), Type.STRING)) {
            final String path = item.getStringValue();
            try {
                final XmldbURI uri = XmldbURI.xmldbUriFor(path).removeLastSegment();
                return new StringValue(uri.toString());
            } catch (final URISyntaxException e) {
                throw new XPathException(this, "Illegal URI for resource path: " + path);
            }
        } else if(Type.subTypeOf(item.getType(), Type.NODE)) {
			final NodeValue node = (NodeValue) item;
			if(node.getImplementationType() == NodeValue.PERSISTENT_NODE) {
				final NodeProxy p = (NodeProxy) node;
				//TODO: use xmldbUri
				return new StringValue(p.getOwnerDocument().getCollection().getURI().toString());
			}
		} else
			{throw new XPathException(this, "First argument to util:collection-name should be either " +
				"a Java object of type org.xmldb.api.base.Collection or a node; got: " + 
				Type.getTypeName(item.getType()));}
		return Sequence.EMPTY_SEQUENCE;
	}

}
