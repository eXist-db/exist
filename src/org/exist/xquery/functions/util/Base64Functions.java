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
 *  $Id: BuiltinFunctions.java 9598 2009-07-31 05:45:57Z ixitar $
 */
package org.exist.xquery.functions.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.util.Base64Decoder;
import org.exist.util.Base64Encoder;
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
 * Base64 String conversion functions.
 *
 * @author  Andrzej Taramina (andrzej@chaeron.com)
 */

public class Base64Functions extends BasicFunction
{
    protected static final Logger           logger       = LogManager.getLogger( Base64Functions.class );

   public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName( "base64-encode", UtilModule.NAMESPACE_URI, UtilModule.PREFIX ),
            "Encodes the given string as Base64",
            new SequenceType[] {
                new FunctionParameterSequenceType( "string", Type.STRING, Cardinality.ZERO_OR_ONE, "The string to be Base64 encoded" )
            },
            new FunctionReturnSequenceType( Type.STRING, Cardinality.ZERO_OR_ONE, "the Base64 encoded output, with trailing newlines trimmed" )
        ),
		
		 new FunctionSignature(
            new QName( "base64-encode", UtilModule.NAMESPACE_URI, UtilModule.PREFIX ),
            "Encodes the given string as Base64",
            new SequenceType[] {
                new FunctionParameterSequenceType( "string", Type.STRING, Cardinality.ZERO_OR_ONE, "The string to be Base64 encoded" ),
				new FunctionParameterSequenceType( "trim", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "Trim trailing newlines?" )
            },
            new FunctionReturnSequenceType( Type.STRING, Cardinality.ZERO_OR_ONE, "the Base64 encoded output" )
        ),
		
         new FunctionSignature(
            new QName( "base64-decode", UtilModule.NAMESPACE_URI, UtilModule.PREFIX ),
            "Decode the given Base64 encoded string back to clear text",
            new SequenceType[] {
                new FunctionParameterSequenceType( "string", Type.STRING, Cardinality.ZERO_OR_ONE, "The Base64 string to be decoded" )
            },
            new FunctionReturnSequenceType( Type.STRING, Cardinality.ZERO_OR_ONE, "the decoded output" )
        )
    };
	

    public Base64Functions( XQueryContext context, FunctionSignature signature )
    {
        super( context, signature );
    }
	

    public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException
    {
		Sequence value	= Sequence.EMPTY_SEQUENCE;
		boolean  trim	= true;
		
        if( !args[0].isEmpty() ) {       
			final String str = args[0].getStringValue();
			
			if( args.length == 2 ) {
				trim = args[1].effectiveBooleanValue();
			}
	
	        if( isCalledAs( "base64-encode" ) ) {
	           	final Base64Encoder enc = new Base64Encoder();
					
	        	enc.translate( str.getBytes()  );
				
				if( trim ) {
	        		value = new StringValue( new String( enc.getCharArray() ).trim() );
				} else {
					value = new StringValue( new String( enc.getCharArray() ) );
				}
	        } else {
	            final Base64Decoder dec = new Base64Decoder();
				
	            dec.translate( str );
				
				value = new StringValue( new String( dec.getByteArray() ) );
	        }
		}
		
		return( value );
    }

}
