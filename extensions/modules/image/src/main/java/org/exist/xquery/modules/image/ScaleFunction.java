/*
 *  eXist Image Module Extension ScaleFunction
 *  Copyright (C) 2006-09 Adam Retter <adam.retter@devon.gov.uk>
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;

import org.exist.dom.QName;
import org.exist.util.io.FastByteArrayOutputStream;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.BinaryValueFromInputStream;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * eXist Image Module Extension ScaleFunction 
 * 
 * Scale's an Image
 * 
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @author Loren Cahlander
 * @serial 2007-01-16
 * @version 1.0
 *
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext, org.exist.xquery.FunctionSignature)
 */
public class ScaleFunction extends BasicFunction
{
	private static final Logger logger = LogManager.getLogger(ScaleFunction.class);
	
	private final static int MAXHEIGHT = 100;
	private final static int MAXWIDTH = 100;

	
	public final static FunctionSignature signature = new FunctionSignature(
			new QName("scale", ImageModule.NAMESPACE_URI, ImageModule.PREFIX),
			"Scale the image image to a specified dimension.  If no dimensions are specified, then the default values are 'maxheight = 100' and 'maxwidth = 100'.",
			new SequenceType[] {
				new FunctionParameterSequenceType("image", Type.BASE64_BINARY, Cardinality.EXACTLY_ONE, "The image data"),
				new FunctionParameterSequenceType("dimension", Type.INTEGER, Cardinality.ZERO_OR_MORE, "The maximum dimension of the scaled image. expressed in pixels (maxheight, maxwidth).  If empty, then the default values are 'maxheight = 100' and 'maxwidth = 100'."),
				new FunctionParameterSequenceType("mimeType", Type.STRING, Cardinality.EXACTLY_ONE, "The mime-type of the image")
			},
			new FunctionReturnSequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE, "the scaled image or an empty sequence if $image is invalid"));

	
	/**
	 * ScaleFunction Constructor
	 * 
	 * @param context	The Context of the calling XQuery
	 */
	public ScaleFunction(XQueryContext context)
	{
		super(context, signature);
	}

	/**
	 * evaluate the call to the xquery scale() function,
	 * it is really the main entry point of this class
	 * 
	 * @param args		arguments from the scale() function call
	 * @param contextSequence	the Context Sequence to operate on (not used here internally!)
	 * @return		A sequence representing the result of the scale() function call
	 * 
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		//was an image and a mime-type speficifed
		if(args[0].isEmpty() || args[2].isEmpty()) {
			return Sequence.EMPTY_SEQUENCE;
		}
		
		//get the maximum dimensions to scale to
		int maxHeight = MAXHEIGHT;
		int maxWidth = MAXWIDTH;

		if(!args[1].isEmpty())
		{
			maxHeight = ((IntegerValue) args[1].itemAt(0)).getInt();
			if(args[1].hasMany())
				maxWidth = ((IntegerValue) args[1].itemAt(1)).getInt();
		}

		//get the mime-type
		String mimeType = args[2].itemAt(0).getStringValue();
		String formatName = mimeType.substring(mimeType.indexOf("/")+1);
		
		//TODO currently ONLY tested for JPEG!!!
		Image image = null;
		BufferedImage bImage = null;
		try (//get the image data
			 InputStream inputStream = ((BinaryValue) args[0].itemAt(0)).getInputStream() )
		{

			image = ImageIO.read(inputStream);
		
			if(image == null) {
                            logger.error("Unable to read image data!");
                            return Sequence.EMPTY_SEQUENCE;
			}
			
			//scale the image
			bImage = ImageModule.createThumb(image, maxHeight, maxWidth);
		
			//get the new scaled image
			try (final FastByteArrayOutputStream os = new FastByteArrayOutputStream()) {
				ImageIO.write(bImage, formatName, os);

				//return the new scaled image data
				return BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), os.toFastByteInputStream());
			}
		}
		catch(Exception e)
		{
			throw new XPathException(this, e.getMessage());
		}
	}
}