/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.image;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Toolkit;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.CropImageFilter;
import java.awt.image.FilteredImageSource;
import java.io.InputStream;
import javax.imageio.ImageIO;

import org.exist.dom.QName;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
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
 * eXist Image Module Extension CropFunction 
 * 
 * Crops an Image
 * 
 * Written by Christian Wittern, based on the scale function by Adam and Loren, with help from Rafael Troilo
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @author Loren Cahlander
 * @author Christian Wittern
 * @serial 2011-03-05
 * @version 1.0
 *
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext, org.exist.xquery.FunctionSignature)
 */
public class CropFunction extends BasicFunction {
    
    private static final Logger logger = LogManager.getLogger(CropFunction.class);
	
    private final static int MAXHEIGHT = 100;
    private final static int MAXWIDTH = 100;

	
    public final static FunctionSignature signature = new FunctionSignature(
        new QName("crop", ImageModule.NAMESPACE_URI, ImageModule.PREFIX),
        "Crop the image $image to a specified dimension.  If no dimensions are specified, then the default values are 'y1 = 0', 'x1 = 0', 'x2 = 100' and 'y2 = 100'.",
        new SequenceType[] {
            new FunctionParameterSequenceType("image", Type.BASE64_BINARY, Cardinality.EXACTLY_ONE, "The image data"),
            new FunctionParameterSequenceType("dimension", Type.INTEGER, Cardinality.ZERO_OR_MORE, "The maximum dimension of the cropd image. expressed in pixels (x1, y1, x2, y2).  If empty, then the default values are are 'y1 = 0', 'x1 = 0', 'x2 = 100' and 'y2 = 100'."),
            new FunctionParameterSequenceType("mimeType", Type.STRING, Cardinality.EXACTLY_ONE, "The mime-type of the image")
        },
        new FunctionReturnSequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE, "the cropd image or an empty sequence if $image is invalid")
    );

	
    /**
     * CropFunction Constructor
     * 
     * @param context	The Context of the calling XQuery
     */
    public CropFunction(XQueryContext context) {
        super(context, signature);
    }

    /**
     * evaluate the call to the xquery crop() function,
     * it is really the main entry point of this class
     * 
     * @param args		arguments from the crop() function call
     * @param contextSequence	the Context Sequence to operate on (not used here internally!)
     * @return		A sequence representing the result of the crop() function call
     * 
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        
        //was an image and a mime-type speficifed
        if(args[0].isEmpty() || args[2].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        //get the maximum dimensions to crop to
        int x1 = 0;
        int y1 = 0;
        int x2 = MAXHEIGHT;
        int y2 = MAXWIDTH;
        int width = 0;
        int height = 0;
        if(!args[1].isEmpty()) {
            x1 = ((IntegerValue) args[1].itemAt(0)).getInt();
            if(args[1].hasMany()) {
                y1 = ((IntegerValue) args[1].itemAt(1)).getInt();
                x2 = ((IntegerValue) args[1].itemAt(2)).getInt();
                y2 = ((IntegerValue) args[1].itemAt(3)).getInt();
                width = x2 - x1;
                height = y2 - y1;
            }
        }
            if(width < 1 ) {
                logger.error("cropping error: x2 value must be greater than x1");
                return Sequence.EMPTY_SEQUENCE;
            }
           if(height < 1) {
                logger.error("cropping error: y2 must be greater than y1");
                return Sequence.EMPTY_SEQUENCE;
           }
        //get the mime-type
        String mimeType = args[2].itemAt(0).getStringValue();
        String formatName = mimeType.substring(mimeType.indexOf("/")+1);

        //TODO currently ONLY tested for JPEG!!!
        BufferedImage bImage = null;
        try (InputStream inputStream = ((BinaryValue) args[0].itemAt(0)).getInputStream()) {
            //get the image data
            Image image = ImageIO.read(inputStream);

            //			image = ImageModule.getImage((Base64BinaryValueType)args[0].itemAt(0));
            //      			image = ImageIO.read(new ByteArrayInputStream(getImageData((Base64BinaryValueType)args[0].itemAt(0))));

            if(image == null) {
                logger.error("Unable to read image data!");
                return Sequence.EMPTY_SEQUENCE;
            }
			
            //crop the image
            Image cropImage = Toolkit.getDefaultToolkit().createImage(new FilteredImageSource(image.getSource(), new CropImageFilter(x1, y1, width, height)));
            if(cropImage instanceof BufferedImage) {
                // just in case cropImage is allready an BufferedImage
                bImage = (BufferedImage)cropImage;

            } else {
                bImage = new BufferedImage(cropImage.getWidth(null),
                cropImage.getHeight(null),BufferedImage.TYPE_INT_RGB);
                Graphics2D g = bImage.createGraphics(); // Paint the image onto the buffered image
                g.drawImage(cropImage, 0, 0, null);
                g.dispose();
            }

            try (final UnsynchronizedByteArrayOutputStream os = UnsynchronizedByteArrayOutputStream.builder().get()) {
                ImageIO.write(bImage, formatName, os);

                //return the new croped image data
                return BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), os.toInputStream(), this);
            }

        } catch(Exception e) {
            throw new XPathException(this, e.getMessage());
        }
    }
}
