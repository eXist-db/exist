/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.xmldb;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * Implements eXist's xmldb:create-collection() function.
 *
 * @author wolf
 */
public class XMLDBCreateCollection extends XMLDBAbstractCollectionManipulator {
    private static final Logger logger = LogManager.getLogger(XMLDBCreateCollection.class);
	public final static FunctionSignature signature = new FunctionSignature(
			new QName("create-collection", XMLDBModule.NAMESPACE_URI,
					XMLDBModule.PREFIX),
            "Create a new collection with name $new-collection as a child of " +
            "$target-collection-uri. " + XMLDBModule.COLLECTION_URI +
            "Returns the path to the new collection if successfully created, " +
            "otherwise the empty sequence.",
			new SequenceType[]{
			    new FunctionParameterSequenceType("target-collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The target collection URI"),
			    new FunctionParameterSequenceType("new-collection", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the new collection to create")},
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the path to the new collection if successfully created, otherwise the empty sequence"));

    public XMLDBCreateCollection(XQueryContext context) {
	super(context, signature);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet,
     *         org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence evalWithCollection(Collection collection, Sequence args[], Sequence contextSequence)
	throws XPathException {

	final String collectionName = args[1].getStringValue();
		
	try {
	    final Collection newCollection = createCollectionPath(collection, collectionName);

	    if (newCollection == null)
		{return Sequence.EMPTY_SEQUENCE;}
	    else
		{return new StringValue(newCollection.getName());}

	} catch (final XMLDBException e) {
	    logger.error("Unable to create new collection " + collectionName, e);
	    throw new XPathException(this, "failed to create new collection " + collectionName + ": " + e.getMessage(), e);
	}
    }
}
