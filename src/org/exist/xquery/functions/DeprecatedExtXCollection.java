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
package org.exist.xquery.functions;

import org.apache.log4j.Logger;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.xmldb.XMLDBModule;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 */
public class DeprecatedExtXCollection extends ExtCollection {
    private static final Logger logger = Logger.getLogger(DeprecatedExtXCollection.class);

	public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("xcollection", Function.BUILTIN_FUNCTION_NS),
            "Works like fn:collection(), but does not include documents " +
            "found in sub-collections of the specified collections.",
            new SequenceType[] {
                new FunctionParameterSequenceType("document-uris", Type.STRING, Cardinality.ONE_OR_MORE, "the set of paths or uris of the documents")
			},
			new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the document nodes from the specified collection paths only, not including descendant collections"),
            true,
            "Moved to the '" + XMLDBModule.NAMESPACE_URI + "' namespace.");
				
	/**
	 * @param context
	 */
	public DeprecatedExtXCollection(XQueryContext context) {
		super(context, signature, false);
        logger.error("Use of deprecated function fn:xcollection(). " +
                     "It will be removed soon. Please " +
                     "use xmldb:xcollection() instead.");

	}

}
