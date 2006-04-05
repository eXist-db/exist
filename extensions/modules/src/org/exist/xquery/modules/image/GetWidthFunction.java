/*
 * eXist Image Module Extension GetWidthFunction
 *
 * Released under the BSD License
 *
 * Copyright (c) 2006, Adam retter <adam.retter@devon.gov.uk>
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * 		Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *  	Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *  	Neither the name of the Devon Portal Project nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 *  
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 *  IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 *  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 *  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 *  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 *  OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 *  OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 *  ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *  $Id$
 */

package org.exist.xquery.modules.image;

import java.awt.Image;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * eXist Image Module Extension GetWidthFunction 
 * 
 * Get's the width of an Image stored in the eXist db
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @serial 2006-03-10
 * @version 1.0
 *
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext, org.exist.xquery.FunctionSignature)
 */
public class GetWidthFunction extends BasicFunction
{   
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("get-width", ImageModule.NAMESPACE_URI, ImageModule.PREFIX),
			"Get's the width of the image in the db indicated by $a, returning an integer of the images width in pixels or an empty sequence if $a is invalid.",
			new SequenceType[]
			{
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
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
		//was a image speficifed
		if (args[0].isEmpty())
            return Sequence.EMPTY_SEQUENCE;
		
		//get the path of the image
        String imgPath = args[0].getStringValue();
        
        //Get the image document from the db
        DBBroker dbbroker = context.getBroker();
        DocumentImpl docImage = null;
        try
        {
        	docImage = dbbroker.getXMLResource(imgPath, Lock.READ_LOCK);
        }
        catch (PermissionDeniedException e)
        {
        	throw new XPathException(getASTNode(), imgPath + ": permission denied to read resource");
        }
        
        //Valid document?
        if (docImage == null)
        {
        	LOG.error(imgPath + " does not exist!");
                return Sequence.EMPTY_SEQUENCE;
        }
        
        //Binary Document?
        if (docImage.getResourceType() != DocumentImpl.BINARY_FILE)
        {
            LOG.error(imgPath + " exists but is not a binary resource!");
        	return Sequence.EMPTY_SEQUENCE;
        }
       
        //Binary document is an image?
        if(!docImage.getMetadata().getMimeType().startsWith("image/"))
        {
        	LOG.error(imgPath + " exists but is not an image!");
        	return Sequence.EMPTY_SEQUENCE;
        }
        
        //Get the image document as a binary document
        BinaryDocument binImage = (BinaryDocument) docImage;
        
        //get a byte array representing the image
        byte[] imgData = dbbroker.getBinaryResource(binImage);
        
        //close the image from the db
        dbbroker.closeDocument();
        
        Image image = null;
        int iWidth = -1;
        
        //Create an Image object from the byte array
        try
        {
        	image = ImageIO.read(new ByteArrayInputStream(imgData));
        }
        catch(IOException ioe)
        {
        	LOG.error(imgPath + " could not read image data!");
        	return Sequence.EMPTY_SEQUENCE;
        }
        
        //Get the width of the image
        iWidth = image.getWidth(null);
        
        //did we get the width of the image?
        if(iWidth == -1)
        {
        	//no, log the error
        	LOG.error(imgPath + " could not read image data!");
        	return Sequence.EMPTY_SEQUENCE; 
        }
        else
        {
        	//return the width of the image
        	return new IntegerValue(iWidth);	
        }
	}
}
