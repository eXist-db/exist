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
 *  $Id: EchoFunction.java 3063 2006-04-05 20:49:44Z brihaye $
 */
package org.exist.xquery.modules.compression;

import org.exist.collections.Collection;
import org.exist.dom.QName;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
import org.exist.util.Base64Encoder;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64Binary;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Type;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @version 1.0
 */
public class ZipFunction extends BasicFunction
{

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("zip", CompressionModule.NAMESPACE_URI, CompressionModule.PREFIX),
			"Zip's resources and/or collections. $a is a sequence of URI's, if a URI points to a collection" +
			"then the collection, its resources and sub-collections are zipped recursively.",
			new SequenceType[] { new SequenceType(Type.ANY_URI, Cardinality.ONE_OR_MORE)},
			new SequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_MORE));

	public ZipFunction(XQueryContext context)
	{
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		// is argument the empty sequence?
		if (args[0].isEmpty())
			return Sequence.EMPTY_SEQUENCE;
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ZipOutputStream zos = new ZipOutputStream(baos);
		
		// iterate through the argument sequence
		for(SequenceIterator i = args[0].iterate(); i.hasNext();)
		{
			AnyURIValue uri = (AnyURIValue)i.nextItem();
			DocumentImpl doc = null;
			try
			{
				//try for a doc
				doc = context.getBroker().getXMLResource(uri.toXmldbURI(), Lock.READ_LOCK);
				
				if(doc == null)
				{
					//try for a collection
					Collection col = context.getBroker().getCollection(uri.toXmldbURI());
					
					if(col != null)
					{
						//got a collection
						zipCollection(zos, col);
					}
					else
					{
						throw new XPathException(getASTNode(), "Invalid URI: " + uri.toString());
					}
				}
				else
				{
					//got a doc
					zipResource(zos, doc);
				}
			}
			catch(PermissionDeniedException pde)
			{
				throw new XPathException(getASTNode(), pde.getMessage());
			}
			catch(IOException ioe)
			{
				throw new XPathException(getASTNode(), ioe.getMessage());
			}
			finally
			{
				if(doc != null)
				{
					doc.getUpdateLock().release(Lock.READ_LOCK);
				}
			}
		}
		
		try
		{
			zos.close();
		}
		catch(IOException ioe)
		{
			throw new XPathException(getASTNode(), ioe.getMessage());
		}
		
		return new Base64Binary(baos.toByteArray());
	}
	
	private void zipResource(ZipOutputStream zos, DocumentImpl doc) throws IOException
	{
		ZipEntry entry = new ZipEntry(doc.getFileURI().toString());
		zos.putNextEntry(entry);
		
		if(doc.getResourceType() == DocumentImpl.XML_FILE)
		{
			//xml resource
		}
		else if(doc.getResourceType() == DocumentImpl.BINARY_FILE)
		{
			//binary file
			byte[] data = context.getBroker().getBinaryResource((BinaryDocument)doc);
			zos.write(data);
		}
		
		zos.closeEntry();
	}

	private void zipCollection(ZipOutputStream zos, Collection col) throws IOException
	{
		
	}
}
