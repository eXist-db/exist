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
 *  $Id$
 */
package org.exist.xpath.functions.xmldb;

import org.exist.dom.QName;
import org.exist.xpath.BasicFunction;
import org.exist.xpath.Cardinality;
import org.exist.xpath.FunctionSignature;
import org.exist.xpath.XPathException;
import org.exist.xpath.XQueryContext;
import org.exist.xpath.value.BooleanValue;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class XMLDBAuthenticate extends BasicFunction {

	public final static FunctionSignature signature =
			new FunctionSignature(
				new QName("authenticate", ModuleImpl.NAMESPACE_URI, ModuleImpl.PREFIX),
				"Check if a user is registered as database user. The function simply tries to " +
				"read the database collection specified in the first parameter $a, using the " +
				"supplied username in $b and password in $c. " +
				"It returns true if the attempt succeeds, false otherwise.",
				new SequenceType[] {
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)},
				new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE));
				
	/**
	 * @param context
	 * @param signature
	 */
	public XMLDBAuthenticate(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.BasicFunction#eval(org.exist.xpath.value.Sequence[], org.exist.xpath.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
		String uri = args[0].getStringValue();
		String user = args[1].getStringValue();
		String password = args[2].getStringValue();
		
		try {
			Collection root = DatabaseManager.getCollection(uri, user, password);
			return BooleanValue.TRUE;
		} catch (XMLDBException e) {
			return BooleanValue.FALSE;
		}
	}

}
