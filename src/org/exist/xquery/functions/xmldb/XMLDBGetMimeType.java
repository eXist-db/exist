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
 *  along with this program; if not, write to the Free Software Foundation
 *  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 * $Id: XMLDBGetMimeType.java $
 */
package org.exist.xquery.functions.xmldb;

import org.exist.dom.DocumentImpl;
import org.exist.dom.QName;
import org.exist.storage.lock.Lock;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public class XMLDBGetMimeType extends BasicFunction
{
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("get-mime-type", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Returns the MIME Type of the resource indicated in $a or an empty sequence otherwise.",
			new SequenceType[] {
					new SequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
		);
	
	public XMLDBGetMimeType(XQueryContext context, FunctionSignature signature)
	{
		super(context, signature);
	}
	
	public Sequence eval(Sequence args[], Sequence contextSequence) throws XPathException
	{	
		String path = new AnyURIValue(args[0].itemAt(0).getStringValue()).toString();
		
		if(path.matches("^[a-z]+://.*"))
		{
			//external
			MimeTable mimeTable = MimeTable.getInstance();
			MimeType mimeType = mimeTable.getContentTypeFor(path);
			if(mimeType != null)
				return new StringValue(mimeType.getName());
		}
		else
		{
			//database
			DocumentImpl doc = null;
			
			try
			{
				XmldbURI pathUri = XmldbURI.xmldbUriFor(path);
				// relative collection Path: add the current base URI
				pathUri = context.getBaseURI().toXmldbURI().resolveCollectionPath(pathUri);
				// try to open the document and acquire a lock
				doc = (DocumentImpl)context.getBroker().getXMLResource(pathUri, Lock.READ_LOCK);
				if(doc != null)
				{
					return new StringValue(((DocumentImpl)doc).getMetadata().getMimeType());
				}
			}	
			catch(Exception e)
			{
				throw new XPathException(e);
			}
			finally
			{
				//release all locks
				if(doc != null)
					doc.getUpdateLock().release(Lock.READ_LOCK);
			}
		}
		
		return Sequence.EMPTY_SEQUENCE;
	}
}
