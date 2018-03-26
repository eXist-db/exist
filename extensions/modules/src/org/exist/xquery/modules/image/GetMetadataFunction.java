/*
 *  eXist Image Module Extension GetMetadataFunction
 *  Copyright (C) 2006-10 Adam Retter <adam@exist-db.org>
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
 *  $Id$
 */

package org.exist.xquery.modules.image;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.util.serializer.DOMStreamer;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * eXist Image Module Extension GetMetadataFunction 
 * 
 * Get's the metadata of an Image
 * 
 * @author Adam Retter <adam@exist-db.org>
 * @author Loren Cahlander
 * @serial 2007-02-27
 * @version 1.0
 *
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext, org.exist.xquery.FunctionSignature)
 */
public class GetMetadataFunction extends BasicFunction
{   
	@SuppressWarnings("unused")
	private static final Logger logger = LogManager.getLogger(GetMetadataFunction.class);
	
	public final static FunctionSignature signatures[] = {
            new FunctionSignature(
                    new QName("get-metadata", ImageModule.NAMESPACE_URI, ImageModule.PREFIX),
                    "Gets the metadata of the image passed in, returning the images XML metadata.",
                    new SequenceType[]
                    {
                            new FunctionParameterSequenceType("image", Type.BASE64_BINARY, Cardinality.EXACTLY_ONE, "The image data"),
                            new FunctionParameterSequenceType("native-format", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "When true metadata of the images native format is returned, otherwise common java ImageIO metadata is returned.")
                    },
                    new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_ONE, "the image metadata"),
                    "Use the contentextraction module instead"
            )
        };

	/**
	 * GetMetadataFunction Constructor
	 * 
	 * @param context	The Context of the calling XQuery
	 */
	public GetMetadataFunction(XQueryContext context, FunctionSignature signature) {
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
        @Override
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		//was an image and format speficifed
		if (args[0].isEmpty()) {
                    return Sequence.EMPTY_SEQUENCE;
		}
        
                //get the images raw binary data
		BinaryValue imageData = (BinaryValue)args[0].itemAt(0);

                if(args[1].isEmpty()) {
                    return Sequence.EMPTY_SEQUENCE;
                }
                //get the format of metadata to return
                boolean nativeFormat = args[1].effectiveBooleanValue();

                try (InputStream inputStream = imageData.getInputStream();) {

                    ImageInputStream iis = ImageIO.createImageInputStream(inputStream);
                    return parseWithImageIO(iis, nativeFormat);
                } catch(IOException ioe) {
                    throw (new XPathException(this, ioe.getMessage(), ioe));
                }   
	}

        private Sequence parseWithImageIO(ImageInputStream iis, boolean nativeFormat) throws IOException, XPathException {

            //get an image reader
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if(readers.hasNext())
            {
                ImageReader imageReader = readers.next();
                imageReader.setInput(iis);

                //read the metadata
                IIOMetadata metadata = imageReader.getImageMetadata(0);
                Node nMetadata = null;
                if(nativeFormat) {
                    //native format
                    nMetadata = metadata.getAsTree(metadata.getNativeMetadataFormatName());
                } else {
                    //common format
                    nMetadata = metadata.getAsTree("javax_imageio_1.0");
                }

                //check we have the metadata
                if(nMetadata == null) {
                    return Sequence.EMPTY_SEQUENCE;
                }


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
                    throw new XPathException(this, se.getMessage(), se);
                }
                finally
                {
                    context.popDocumentContext();
                }
            }

            return Sequence.EMPTY_SEQUENCE;
        }
}
