/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.xquery.modules.file;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.BinaryValueFromFile;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author Andrzej Taramina
 *
 */
public class FileReadBinary extends BasicFunction {

	private static final Logger logger = Logger.getLogger(FileReadBinary.class);
	
	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName( "read-binary", FileModule.NAMESPACE_URI, FileModule.PREFIX ),
			"Reads the contents of a binary file.  This method is only available to the DBA role.",
			new SequenceType[] {				
				new FunctionParameterSequenceType( "url", Type.ITEM, Cardinality.EXACTLY_ONE, "A string representing a URL, eg file://etc." )
				},				
			new FunctionReturnSequenceType( Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE, "the file contents" ) )
		};
	
	/**
	 * @param context
	 * @param signature
	 */
	public FileReadBinary( XQueryContext context, FunctionSignature signature ) 
	{
		super( context, signature );
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException 
	{
		if (!context.getUser().hasDbaRole()) {
			XPathException xPathException = new XPathException(this, "Permission denied, calling user '" + context.getUser().getName() + "' must be a DBA to call this function.");
			logger.error("Invalid user", xPathException);
			throw xPathException;
		}

		String arg 		= args[0].itemAt(0).getStringValue();
		//byte[] buffer;
		
		try {
			URL 			url  	= new URL( arg );	
			File 			file 	= new File( url.toURI() );

			//DataInputStream in 	 	= new DataInputStream( new FileInputStream( file ) );
			
			//buffer 	= new byte[ (int)file.length() ];
			
			//in.readFully( buffer );

                        new FileInputStream(file).getFD();
                        
                        return(BinaryValueFromFile.getInstance(context, new Base64BinaryValueType(),file));
		} 
		
		catch( MalformedURLException e ) {
			throw( new XPathException(this, e.getMessage() ) );	
		} 
		
		catch( URISyntaxException e ) {
			throw( new XPathException(this, e.getMessage() ) );	
		} 
		
		catch( IOException e ) {
			throw( new XPathException(this, e.getMessage() ) );	
		}
	}
}
