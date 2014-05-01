/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2007-09 The eXist Project
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
 *  $Id: UUID.java 10203 2009-10-08 15:20:09Z ellefj $
 */
package org.exist.xquery.functions.util;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author Andrzej Taramina
 */
public class Wait extends BasicFunction 
{
    public final static FunctionSignature signatures[] = {
            new FunctionSignature (
                    new QName( "wait", UtilModule.NAMESPACE_URI, UtilModule.PREFIX ),
                    "Wait for the specified number of milliseconds",
                    new SequenceType[]{
                        new FunctionParameterSequenceType( "interval", Type.INTEGER, Cardinality.EXACTLY_ONE, "Number of milliseconds to wait." ),
                    },
                   new FunctionReturnSequenceType( Type.ITEM, Cardinality.EMPTY, "Returns an empty sequence" )
                )
    };

    public Wait( XQueryContext context, FunctionSignature signature )
	{
        super( context, signature );
    }


    @Override
    public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException 
	{
		final long interval = ((IntegerValue)args[0].itemAt(0)).getLong();
		
     	if( interval > 0 ) {
			try {
				Thread.sleep( interval );
			}
			catch( final InterruptedException e ) {
			}
		}

        return( Sequence.EMPTY_SEQUENCE );
    }
}
