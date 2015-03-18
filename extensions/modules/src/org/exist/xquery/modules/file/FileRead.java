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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.MalformedURLException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author Pierrick Brihaye
 * @author Dizzzz
 * @author Andrzej Taramina
 * @author Loren Cahlander
 */
public class FileRead extends BasicFunction {

	private final static Logger logger = LogManager.getLogger(FileRead.class);
	
	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName( "read", FileModule.NAMESPACE_URI, FileModule.PREFIX ),
			"Reads the content of file.  This method is only available to the DBA role.",
			new SequenceType[] {				
				new FunctionParameterSequenceType( "path", Type.ITEM, 
                        Cardinality.EXACTLY_ONE, "The directory path or URI in the file system." )
				},				
			new FunctionReturnSequenceType( Type.STRING, 
                    Cardinality.ZERO_OR_ONE, "the file contents" ) ),
        
		new FunctionSignature(
			new QName( "read", FileModule.NAMESPACE_URI, FileModule.PREFIX ),
			"Reads the content of file.  This method is only available to the DBA role.",
			new SequenceType[] {
				new FunctionParameterSequenceType( "path", Type.ITEM, 
                        Cardinality.EXACTLY_ONE, "The directory path or URI in the file system." ),
				new FunctionParameterSequenceType( "encoding", Type.STRING, 
                        Cardinality.EXACTLY_ONE, "The encoding type for the file" )
				},
				new FunctionReturnSequenceType( Type.STRING, 
                        Cardinality.ZERO_OR_ONE, "the file contents" ) )
		};
	
	/**
	 * @param context
	 * @param signature
	 */
	public FileRead( XQueryContext context, FunctionSignature signature ) 
	{
		super( context, signature );
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException 
	{
		if (!context.getSubject().hasDbaRole()) {
			XPathException xPathException = new XPathException(this, "Permission denied, calling user '" + context.getSubject().getName() + "' must be a DBA to call this function.");
			logger.error("Invalid user", xPathException);
			throw xPathException;
		}

        String inputPath = args[0].getStringValue();
        File file = FileModuleHelper.getFile(inputPath);
        
		StringWriter sw = new StringWriter();
		
		try {
			FileInputStream fis = new FileInputStream(file);
            
			InputStreamReader reader;
			
			if( args.length > 1 ) {			
				reader = new InputStreamReader( fis, args[1].getStringValue() );
			} else {
				reader = new InputStreamReader( fis );
			}
			
			char[] buf = new char[1024];
			int len;
			while( ( len = reader.read( buf ) ) > 0 ) {
				sw.write( buf, 0, len) ;
			}
            
			reader.close();
			sw.close();
            
		} catch( MalformedURLException e ) {
			throw new XPathException(this, e);	
		} catch( IOException e ) {
			throw new XPathException(this, e);	
		}
		
		//TODO : return an *Item* built with sw.toString()
		
		return new StringValue( sw.toString() );
	}
}
