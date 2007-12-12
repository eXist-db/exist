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
 *  $Id: XMLDBCollectionExists.java 2635 2006-02-06 18:29:56Z harrah $
 */
package org.exist.xquery.functions.xmldb;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

public class XMLDBDocumentExists extends XMLDBAbstractCollectionManipulator
{

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("document-exists", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Returns true as xs:boolean if there is a document "+
			"with the name $b in the collection $a.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE));

	public XMLDBDocumentExists(XQueryContext context) {
		super(context, signature, false);
	}

	public Sequence evalWithCollection(Collection collection, Sequence[] args, Sequence contextSequence) throws XPathException {
		
		if(collection == null)
			return BooleanValue.FALSE;
		
		String documentName = args[1].getStringValue();
		if(documentName == null || documentName.length() == 0)
			return BooleanValue.FALSE;
		
		try
		{
			String resources[] = collection.listResources();
			for(int i = 0; i < resources.length; i++)
			{
				if(resources[i].equals(documentName))
					return BooleanValue.TRUE;
			}
		}
		catch(XMLDBException xe)
		{
			throw new XPathException(xe);
		}
		
		return BooleanValue.FALSE;
	}
}

