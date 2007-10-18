/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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
import org.exist.xmldb.UserManagementService;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 *
 */
public class XMLDBHasLock extends XMLDBAbstractCollectionManipulator {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("document-has-lock", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Returns the name of the user that holds a write lock on the resource specified in $b in the collection $a.  " +
			"If no lock is in place, the empty sequence is returned. " +
			"The collection can be passed as a simple collection " +
			"path or an XMLDB URI.",
			new SequenceType[] {
					new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE));
	
	public XMLDBHasLock(XQueryContext context) {
		super(context, signature);
	}
	
	/**
     * 
     * @param contextSequence 
     * @param collection 
     * @param args 
     * @throws XPathException 
     */
	public Sequence evalWithCollection(Collection collection, Sequence[] args, Sequence contextSequence)
	throws XPathException {
		try {
			UserManagementService ums = (UserManagementService) collection.getService("UserManagementService", "1.0");
			Resource res = collection.getResource(new AnyURIValue(args[1].getStringValue()).toXmldbURI().toString());
			if (res != null) {
			    String lockUser = ums.hasUserLock(res);
			    return lockUser == null ? Sequence.EMPTY_SEQUENCE : new StringValue(lockUser);
			} else {
			    throw new XPathException(getASTNode(), "Unable to locate resource "+args[1].getStringValue());
			}
		} catch (XMLDBException e) {
			throw new XPathException(getASTNode(), "Failed to retrieve user lock", e);
		}
	}

}
