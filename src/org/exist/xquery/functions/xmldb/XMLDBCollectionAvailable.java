/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2010 The eXist Project
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

//import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;

/**
 * Implements eXist's xmldb:collection-available() function.
 * 
 * @author wolf
 * @author Luigi P. Bai, finder@users.sf.net, 2004
 *
 */
public class XMLDBCollectionAvailable extends XMLDBAbstractCollectionManipulator {
//    private static final Logger logger = LogManager.getLogger(XMLDBCollectionAvailable.class);
    public final static FunctionSignature signatures[] = {
	//Just to mimic doc-available()
	new FunctionSignature(
			      new QName("collection-available", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			      "Returns true() if the collection " +
			      "$collection-uri exists and is available, otherwise false(). " +
                  XMLDBModule.COLLECTION_URI,
			      new SequenceType[] {
				  new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collection URI")},
			      new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true() if the collection exists and is available, false() otherwise"))
    };
		

    public XMLDBCollectionAvailable(XQueryContext context, FunctionSignature signature) {
	super(context, signature, false);
    }

    public Sequence evalWithCollection(Collection collection, Sequence[] args, 
				       Sequence contextSequence)
	throws XPathException {
	return (collection == null) ? BooleanValue.FALSE : BooleanValue.TRUE;
    }
}
