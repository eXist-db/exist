/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  Changes to this file are:
 *  Copyright (C) 2004 by Luigi P. Bai
 *  finder@users.sf.net
 *  and are licensed under the GNU Lesser General Public License as below.
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
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

public class XMLDBCollectionExists extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("collection-exists", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Returns true as xs:boolean if there is a collection "+
			"with the same name as the first argument as xs:string.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)},
			new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE));

	/**
	 * @param context
	 * @param signature
	 */
	public XMLDBCollectionExists(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(
		Sequence args[],
		Sequence contextSequence)
		throws XPathException {
		String collectionURI = args[0].getStringValue();
		Collection collection = null;
		try {
			collection = DatabaseManager.getCollection(collectionURI);
		} catch (XMLDBException e) {
		    LOG.debug(e.getMessage(), e);
			throw new XPathException(getASTNode(), 
				"exception while retrieving collection: " + e.getMessage(), e);
        } finally {
            if (null != collection)
                try { collection.close(); } catch (Exception e) { throw new XPathException(getASTNode(), "Unable to close collection", e); }
		}
		return (null == collection) ? BooleanValue.FALSE : BooleanValue.TRUE;
	}
}
