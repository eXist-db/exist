/*
 *  eXist Image Module Extension GetMetadataFunction
 *  Copyright (C) 2006 Adam Retter <adam.retter@devon.gov.uk>
 *  www.adamretter.co.uk
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
 *  $Id: GetWidthFunction.java 5201 2007-01-16 17:40:10Z deliriumsky $
 */

package org.exist.xquery.modules.image;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;

import org.exist.dom.QName;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.util.serializer.DOMStreamer;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64Binary;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;



import org.w3c.dom.Node;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * eXist Image Module Extension GetMetadataFunction 
 * 
 * Get's the metadata of an Image
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @serial 2007-02-27
 * @version 1.0
 *
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext, org.exist.xquery.FunctionSignature)
 */
public class GetMetadataFunction extends BasicFunction
{   
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("get-metadata", ImageModule.NAMESPACE_URI, ImageModule.PREFIX),
			"Get's the metadta of the image passed in $a, returning the images XML metadata. When $b is true() metadata of the images native format is returned, otherwise common java ImageIO metadata is returned.",
			new SequenceType[]
			{
				new SequenceType(Type.BASE64_BINARY, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE));

	/**
	 * GetMetadataFunction Constructor
	 * 
	 * @param context	The Context of the calling XQuery
	 */
	public GetMetadataFunction(XQueryContext context)
	{
		super(context, signature);
    }

	/**
	 * evaluate the call to the xquery get-metadata() function,
	 * it is really the main entry point of this class
	 * 
	 * @param args		arguments from the get-metadata() function call
	 * @param contextSequence	the Context Sequence to operate on (not used here internally!)
	 * @return		A sequence representing the result of the get-metadata() function call
	 * 
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		//was an image and format speficifed
		if (args[0].isEmpty() || args[1].isEmpty())
            return Sequence.EMPTY_SEQUENCE;
        
        //get the images raw binary data
		byte[] imgData = ImageModule.getImageData((Base64Binary)args[0].itemAt(0));
		
		//get the format of metadata to return
		boolean nativeFormat = args[1].effectiveBooleanValue();
		
		try
		{
			//get an input stream
			ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(imgData));
		
			//get an image reader
			Iterator readers = ImageIO.getImageReaders(iis);
			if(readers.hasNext())
			{
				ImageReader imageReader = (ImageReader)readers.next();
				imageReader.setInput(iis);
				
				//read the metadata
				IIOMetadata metadata = imageReader.getImageMetadata(0);
				Node nMetadata = null; 
				if(nativeFormat)
				{
					//native format
					nMetadata = metadata.getAsTree(metadata.getNativeMetadataFormatName());
				}
				else
				{
					//common format
					nMetadata = metadata.getAsTree("javax_imageio_1.0");
				}
				
				//check we have the metadata
				if(nMetadata == null)
					return Sequence.EMPTY_SEQUENCE;
				
				
				//return the metadata
				context.pushDocumentContext();
				try
				{
					MemTreeBuilder builder = context.getDocumentBuilder();
                    DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
					DOMStreamer streamer = new DOMStreamer();
					streamer.setContentHandler(receiver);
					streamer.serialize(nMetadata);
					Document docMetadata = receiver.getDocument();
                    
					return (NodeValue)docMetadata;
				}
				catch(SAXException se)
				{
					throw new XPathException(se);
				}
				finally
				{
					context.popDocumentContext();
				}
			}
		}
		catch(IOException ioe)
		{
			throw new XPathException(ioe);
		}
		
		return Sequence.EMPTY_SEQUENCE;
	}
}
