/*
 *  eXist Image Module Extension GetWidthFunction
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

import java.awt.Image;
import java.io.IOException;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64Binary;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * eXist Image Module Extension GetWidthFunction 
 * 
 * Get's the width of an Image
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @serial 2006-03-10
 * @version 1.1
 *
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext, org.exist.xquery.FunctionSignature)
 */
public class GetWidthFunction extends BasicFunction
{   
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("get-width", ImageModule.NAMESPACE_URI, ImageModule.PREFIX),
			"Get's the Width of the image passed in $a, returning an integer of the images width in pixels or an empty sequence if $a is invalid.",
			new SequenceType[]
			{
				new SequenceType(Type.BASE64_BINARY, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE));

	/**
	 * GetWidthFunction Constructor
	 * 
	 * @param context	The Context of the calling XQuery
	 */
	public GetWidthFunction(XQueryContext context)
	{
		super(context, signature);
    }

	/**
	 * evaluate the call to the xquery get-width() function,
	 * it is really the main entry point of this class
	 * 
	 * @param args		arguments from the get-width() function call
	 * @param contextSequence	the Context Sequence to operate on (not used here internally!)
	 * @return		A sequence representing the result of the get-width() function call
	 * 
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		//was an image speficifed
		if (args[0].isEmpty())
            return Sequence.EMPTY_SEQUENCE;
		
		//get the image
		Image image = null;
        try
        {
            image = ImageModule.getImage((Base64Binary)args[0].itemAt(0));
        }
        catch(IOException ioe)
        {
        	LOG.error("Unable to read image data!", ioe);
        	return Sequence.EMPTY_SEQUENCE;
        }
        
        if(image == null)
        {
        	LOG.error("Unable to read image data!");
        	return Sequence.EMPTY_SEQUENCE;
        }
        
        //Get the width of the image
        int iWidth = image.getWidth(null);
        
        //did we get the width of the image?
        if(iWidth == -1)
        {
        	//no, log the error
        	LOG.error("Unable to read image data!");
        	return Sequence.EMPTY_SEQUENCE; 
        }
        else
        {
        	//return the width of the image
        	return new IntegerValue(iWidth);	
        }
	}
}
