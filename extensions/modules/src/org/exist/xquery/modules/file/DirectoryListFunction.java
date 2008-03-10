/*
 *  eXist SQL Module Extension ExecuteFunction
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
 *  $Id: ExecuteFunction.java 4126 2006-09-18 21:20:17 +0000 (Mon, 18 Sep 2006) deliriumsky $
 */

package org.exist.xquery.modules.file;


import java.io.File;
import java.util.StringTokenizer;

import org.exist.Namespaces;
import org.exist.dom.QName;

import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;

import org.exist.util.DirectoryScanner;

import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;


/**
 * eXist File Module Extension DirectoryListFunction 
 * 
 * Enumerate a list of files found in a specified directory, using a pattern
 * 
 * @author Andrzej Taramina <andrzej@chaeron.com>
 * @serial 2008-03-06
 * @version 1.0
 *
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext, org.exist.xquery.FunctionSignature)
 */

public class DirectoryListFunction extends BasicFunction
{	
	
	final static String NAMESPACE_URI                       = FileModule.NAMESPACE_URI;
	final static String PREFIX                              = FileModule.PREFIX;
	
	
	public final static FunctionSignature[] signatures =
	{
		new FunctionSignature(
			new QName( "directory-list", NAMESPACE_URI, PREFIX ),
			"List all files found in a directory. Files are located in the server's " +
			"file system, using file patterns. " +
			"The first argument is the directory in the file system where the files are located." +
			"The second argument is the file pattern. File pattern matching is based " +
			"on code from Apache's Ant, thus following the same conventions. For example: " +
			"*.xml matches any file ending with .xml in the current directory, **/*.xml matches files " +
			"in any directory below the current one. " +
			"The function returns a node fragment that shows all matching filenames and the subdirectory they were found in",
			new SequenceType[]
			{
				new SequenceType( Type.STRING, Cardinality.EXACTLY_ONE ),
				new SequenceType( Type.STRING, Cardinality.EXACTLY_ONE )
				},
			new SequenceType( Type.NODE, Cardinality.ZERO_OR_ONE )
			)
		};
	
	
	/**
	 * DirectoryListFunction Constructor
	 * 
	 * @param context	The Context of the calling XQuery
	 */
	
	public DirectoryListFunction( XQueryContext context, FunctionSignature signature )
	{
		super( context, signature );
	}
	
	
	/**
	 * evaluate the call to the XQuery execute() function,
	 * it is really the main entry point of this class
	 * 
	 * @param args		arguments from the execute() function call
	 * @param contextSequence	the Context Sequence to operate on (not used here internally!)
	 * @return		A node representing the SQL result set
	 * 
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	
	public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException
	{
		File 		baseDir 	= new File( args[0].getStringValue() );
		Sequence 	patterns 	= args[1];
		
		LOG.debug( "Listing matching files in directory: " + baseDir );
		
		Sequence    xmlResponse     = null;
		
		MemTreeBuilder builder = context.getDocumentBuilder();
		
		builder.startDocument();
		builder.startElement( new QName( "list", NAMESPACE_URI, PREFIX ), null );
		builder.addAttribute( new QName( "directory", null, null ), baseDir.toString() );
		
		for( SequenceIterator i = patterns.iterate(); i.hasNext(); ) {
			String pattern 	= i.nextItem().getStringValue();
			File[] files 	= DirectoryScanner.scanDir( baseDir, pattern );
			String relDir 	= null;
			
			LOG.debug( "Found: " + files.length );
			
			for( int j = 0; j < files.length; j++ ) {
				LOG.debug( "Found: " + files[j].getAbsolutePath() );
				
				String relPath = files[j].toString().substring( baseDir.toString().length() + 1 );
				
				int p = relPath.lastIndexOf( File.separatorChar );
				relDir = relPath.substring( 0, p );
				relDir = relDir.replace( File.separatorChar, '/' );
				
				builder.startElement( new QName( "file", NAMESPACE_URI, PREFIX ), null );
				
				builder.addAttribute( new QName( "name", null, null ), files[j].getName() );
				
				if( relDir != null && relDir.length() > 0 ) {
					builder.addAttribute( new QName( "subdir", null, null ), relDir );
				}
				
				builder.endElement();
				
			}
		}
		
		builder.endElement();
		
		xmlResponse = (NodeValue)builder.getDocument().getDocumentElement();
		
		return( xmlResponse );
	}
	
}
