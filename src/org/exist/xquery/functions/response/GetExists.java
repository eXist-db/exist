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
 *  $Id: GetExists.java 2436 2006-01-07 21:47:15 +0000 (Sat, 07 Jan 2006) brihaye $
 */
package org.exist.xquery.functions.response;

import org.apache.log4j.Logger;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;


/**
 * DOCUMENT ME!
 *
 * @author  Andrzej Taramina <andrzej@chaeron.com>
 */
public class GetExists extends BasicFunction
{
    protected static final Logger logger = Logger.getLogger(GetExists.class);

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName( "exists", ResponseModule.NAMESPACE_URI, ResponseModule.PREFIX ),
			"Returns whether a response object exists.",
			null,
			new FunctionParameterSequenceType( "result", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if the response object exists" ) );

    /**
     * Creates a new GetExists object.
     *
     * @param  context
     */

    public GetExists( XQueryContext context )
    {
        super( context, signature );
    }


    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */

    public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException
    {
        BooleanValue   exists   = BooleanValue.TRUE;

        ResponseModule myModule = (ResponseModule)context.getModule( ResponseModule.NAMESPACE_URI );

        // response object is read from global variable $response
        Variable       var      = myModule.resolveVariable( ResponseModule.RESPONSE_VAR );

        if( ( var == null ) || ( var.getValue() == null ) ) {
            exists = BooleanValue.FALSE;
        }

        return( exists );
    }

}
