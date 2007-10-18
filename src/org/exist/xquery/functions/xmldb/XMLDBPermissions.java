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
 */
package org.exist.xquery.functions.xmldb;

import org.exist.dom.QName;
import org.exist.security.Permission;
import org.exist.xmldb.UserManagementService;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.IntegerValue;
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
public class XMLDBPermissions extends XMLDBAbstractCollectionManipulator {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("get-permissions", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Returns the permissions assigned to the collection. " +
			"The collection can be specified as a simple collection path or " +
			"an XMLDB URI.",
			new SequenceType[] {
					new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.INT, Cardinality.ZERO_OR_ONE)
		),
		new FunctionSignature(
			new QName("get-permissions", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Returns the permissions assigned to the resource specified in $b " +
			"which is a child of the collection $a. The collection can be specified " +
			"as a simple collection path or an XMLDB URI.",
			new SequenceType[] {
					new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.INT, Cardinality.ZERO_OR_ONE)
		)
	};
	
	public XMLDBPermissions(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence evalWithCollection(Collection collection, Sequence[] args, Sequence contextSequence)
		throws XPathException {
		try {
			Permission perm = getPermissions(collection, args);
			return new IntegerValue(perm.getPermissions(), Type.INT);
        } catch (XMLDBException xe) {
            throw new XPathException(getASTNode(), "Unable to retrieve resource permissions", xe);
        }
	}

	/**
	 * @param collection
	 * @param args
	 * @return
	 * @throws XMLDBException
	 * @throws XPathException
	 */
	protected Permission getPermissions(Collection collection, Sequence[] args) throws XMLDBException, XPathException {
		UserManagementService ums = (UserManagementService) collection.getService("UserManagementService", "1.0");
		Permission perm;
		if(getSignature().getArgumentCount() == 2) {
		    Resource res = collection.getResource(args[1].getStringValue());
		    if (res != null) {
		        perm = ums.getPermissions(res);
		    } else {
		        throw new XPathException(getASTNode(), "Unable to locate resource "+args[1].getStringValue());
		    }
		} else {
			perm = ums.getPermissions(collection);
		}
		return perm;
	}

}
