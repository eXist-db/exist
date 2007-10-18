/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *
 *  Modifications Copyright (C) 2004 Luigi P. Bai
 *  finder@users.sf.net
 *  Licensed as below under the LGPL.
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

public class XMLDBChmodResource extends XMLDBAbstractCollectionManipulator {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("chmod-resource", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
                        "Sets the mode of the specified Resource. $a is the collection path, $b is the resource name, $c is the mode (as xs:integer). "+
                        "PLEASE REMEMBER that 0755 is 7*64+5*8+5, NOT decimal 755.",
			new SequenceType[] {
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE),
			},
            new SequenceType(Type.ITEM, Cardinality.EMPTY));

	
	public XMLDBChmodResource(XQueryContext context) {
		super(context, signature);
	}
	
/* (non-Javadoc)
 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
 *
 */
	
	public Sequence evalWithCollection(Collection collection, Sequence[] args, Sequence contextSequence)
		throws XPathException {

        try {
            Resource res = collection.getResource(args[1].getStringValue());
            if (res != null) {
                UserManagementService ums = (UserManagementService) collection.getService("UserManagementService", "1.0");
                ums.chmod(res, ((IntegerValue) args[2].convertTo(Type.INTEGER)).getInt());
            } else {
                throw new XPathException(getASTNode(), "Unable to locate resource "+args[1].getStringValue());
            }
        } catch (XMLDBException xe) {
            throw new XPathException(getASTNode(), "Unable to change resource permissions", xe);
        }

		return Sequence.EMPTY_SEQUENCE;
	}

}
