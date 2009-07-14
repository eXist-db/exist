/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
 *  $Id: FileRead.java 7488 2008-03-07 05:27:06Z chaeron $
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
import org.exist.xquery.value.Base64Binary;
import org.exist.xquery.value.FunctionParameterSequenceType;
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
			"Read content of a binary file.",
			new SequenceType[] {				
				new FunctionParameterSequenceType( "url", Type.ITEM, Cardinality.EXACTLY_ONE, "a string representing a URL, eg file://etc." )
				},				
			new FunctionParameterSequenceType( "contents", Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE, "contents" ) )
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
		logger.info("Entering " + FileModule.PREFIX + ":" + getName().getLocalName());
		String arg 		= args[0].itemAt(0).getStringValue();
		byte[] buffer;
		
		try {
			URL 			url  	= new URL( arg );	
			File 			file 	= new File( url.toURI() ); 		
			DataInputStream in 	 	= new DataInputStream( new FileInputStream( file ) );		
			
			buffer 	= new byte[ (int)file.length() ];
			
			in.readFully( buffer );
		} 
		
		catch( MalformedURLException e ) {
			throw( new XPathException( this, e.getMessage() ) );
		} 
		
		catch( URISyntaxException e ) {
			throw( new XPathException( this, e.getMessage() ) );	
		} 
		
		catch( IOException e ) {
			throw( new XPathException( this, e.getMessage() ) );
		}

		logger.info("Exiting " + FileModule.PREFIX + ":" + getName().getLocalName());
		return( new Base64Binary( buffer ) );
	}
}
