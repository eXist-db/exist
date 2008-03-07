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

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;

import org.exist.dom.QName;

import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author Andrzej Taramina
 *
 */
public class FileIsReadable extends BasicFunction {
	
	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName( "is-readable", FileModule.NAMESPACE_URI, FileModule.PREFIX ),
			"Tests if file $a is readable",
			new SequenceType[] {				
				new SequenceType( Type.ITEM, Cardinality.EXACTLY_ONE )
				},				
			new SequenceType( Type.BOOLEAN, Cardinality.EXACTLY_ONE ) )
		};
	
	/**
	 * @param context
	 * @param signature
	 */
	public FileIsReadable( XQueryContext context, FunctionSignature signature ) 
	{
		super( context, signature );
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException 
	{
		Sequence readable 	= BooleanValue.FALSE;
		String path 		= args[0].itemAt(0).getStringValue();
		File file   		= new File( path );
		
		if( file.canRead() ) {
			readable = BooleanValue.TRUE;
		}
		
		return( readable ); 
	}
}
