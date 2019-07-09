/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2007-2009 The eXist Project
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
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.fn.ExtCollection;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 * @author ljo
 */
public class FunXCollection extends ExtCollection {
//    private static final Logger logger = LogManager.getLogger(FunXCollection.class);
	public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("xcollection", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Returns the document nodes in the collections $collection-uris " +
            "non-recursively, i.e. does not include document nodes found in " +
            "sub-collections. " +
            "C.f. fn:collection(). " +
            XMLDBModule.COLLECTION_URI,
            new SequenceType[] {
		new FunctionParameterSequenceType("collection-uris", Type.STRING, Cardinality.ONE_OR_MORE, "The collection URIs")},
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the document nodes from the specified collections excluding sub-collections"));

	public FunXCollection(XQueryContext context) {
		super(context, signature, false);
	}
}
