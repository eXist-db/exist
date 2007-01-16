/*
 *  eXist Image Module Extension
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
 *  $Id$
 */

package org.exist.xquery.modules.image;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.exist.util.Base64Decoder;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Base64Binary;


/**
 * eXist Image Module Extension
 * 
 * An extension module for the eXist Native XML Database that allows operations
 * on images stored in the eXist database.
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @serial 2006-03-10
 * @version 1.0
 *
 * @see org.exist.xquery.AbstractInternalModule#AbstractInternalModule(org.exist.xquery.FunctionDef[])
 */

/*
 * TODO: metadata extraction from images, especially JPEG's
 */

public class ImageModule extends AbstractInternalModule {

	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/image";
	
	public final static String PREFIX = "image";
	
	private final static FunctionDef[] functions = {
		new FunctionDef(GetWidthFunction.signature, GetWidthFunction.class),
		new FunctionDef(GetHeightFunction.signature, GetHeightFunction.class),
		new FunctionDef(ScaleFunction.signature, ScaleFunction.class),
		new FunctionDef(GetThumbnailsFunction.signature, GetThumbnailsFunction.class)
	};
	
	public ImageModule() {
		super(functions);
	}

	public String getNamespaceURI() {
		return NAMESPACE_URI;
	}

	public String getDefaultPrefix() {
		return PREFIX;
	}

	public String getDescription() {
		return "A module for performing operations on Images stored in the eXist db";
	}
	
	/**
	 * Get's an Image object from base64 binary encoded image data
	 * 
	 * @param imgBase64Data	The base64 encoded image data
	 * 
	 * @return An Image object
	 */
	protected static Image getImage(Base64Binary imgBase64Data) throws IOException, XPathException
	{
		//decode the base64 image data
		Base64Decoder dec = new Base64Decoder();
        dec.translate(imgBase64Data.getStringValue());
        
        //get the raw binary data
		byte[] imgData = dec.getByteArray();
                
        //Create an Image object from the byte array
        return ImageIO.read(new ByteArrayInputStream(imgData));
	}
	
	/**
	 * @author Rafael Troilo (rtroilo@gmail.com)
	 */
	protected static BufferedImage createThumb(Image image, int height, int width)
	{
		int thumbWidth = 0;
		int thumbHeight = 0;
		double scaleFactor = 0.0;
		BufferedImage thumbImage = null;
		Graphics2D graphics2D = null;

		int imageHeight = image.getHeight(null);
		int imageWidth = image.getWidth(null);

		if (imageHeight >= imageWidth) {
			scaleFactor = (double) height / (double) imageHeight;
			thumbWidth = (int) (imageWidth * scaleFactor);
			thumbHeight = height;
			if (thumbWidth > width) { // thumbwidth is greater than
				// maxThumbWidth, so we have to scale
				// again
				scaleFactor = (double) width / (double) thumbWidth;
				thumbHeight = (int) (thumbHeight * scaleFactor);
				thumbWidth = width;
			}
		} else {
			scaleFactor = (double) width / (double) imageWidth;
			thumbHeight = (int) (imageHeight * scaleFactor);
			thumbWidth = width;
			if (thumbHeight > height) { // thumbHeight is greater than
				// maxThumbHeight, so we have to scale
				// again
				scaleFactor = (double) height / (double) thumbHeight;
				thumbWidth = (int) (thumbWidth * scaleFactor);
				thumbHeight = height;
			}
		}

		thumbImage = new BufferedImage(thumbWidth, thumbHeight,
				BufferedImage.TYPE_INT_RGB);
		graphics2D = thumbImage.createGraphics();
		graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphics2D.drawImage(image, 0, 0, thumbWidth, thumbHeight, null);
		return thumbImage;
	}
}
