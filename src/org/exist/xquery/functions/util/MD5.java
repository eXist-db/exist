/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
package org.exist.xquery.functions.util;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.security.MessageDigester;

/**
 * Generate an MD5 key from a string.
 * 
 * @author wolf
 */
public class MD5 extends BasicFunction 
{
	protected static final Logger logger = Logger.getLogger(MD5.class);
	
	public final static FunctionSignature deprecated[] = {
		new FunctionSignature(
			new QName( "md5", UtilModule.NAMESPACE_URI, UtilModule.PREFIX ),
			"Generates an MD5 key from a string.",
			new SequenceType[] {
				new FunctionParameterSequenceType( "arg", Type.ITEM, Cardinality.EXACTLY_ONE, "The input string" ),
				},
			new FunctionReturnSequenceType( Type.STRING, Cardinality.EXACTLY_ONE, "the MD5 key" ),
                "Use the hash($a, \"MD5\") function instead. SHA-1 is supported as " +
                        "more secure message digest algorithm."),
	
		new FunctionSignature(
			new QName( "md5", UtilModule.NAMESPACE_URI, UtilModule.PREFIX ),
			"Generates an MD5 key from a string. $b specifies whether to return result Base64 encoded",
			new SequenceType[] {
				new FunctionParameterSequenceType( "arg", Type.ITEM, Cardinality.EXACTLY_ONE, "The input string" ),
				new FunctionParameterSequenceType( "base64encoded", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "The flag to determine if the result is Base64 encoded" )
				},
			new FunctionReturnSequenceType( Type.STRING, Cardinality.EXACTLY_ONE, "the MD5 key" ),
                "Use the hash($a, \"MD5\") function instead. SHA-1 is supported as " +
                        "more secure message digest algorithm.")
		};

	public MD5( XQueryContext context , FunctionSignature signature ) 
	{
		super( context, signature );
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval( Sequence[] args, Sequence contextSequence  ) throws XPathException 
	{
		boolean base64 = false;
		
		String arg = args[0].itemAt( 0 ).getStringValue();
		
		if( args.length > 1 ) {	
			base64 = args[1].effectiveBooleanValue();
		}
		
		String md = MessageDigester.md5( arg, base64 );
		
		return( new StringValue( md ) );
	}

}
