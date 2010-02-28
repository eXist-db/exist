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
 *  $Id: ClearPersistentCookiesFunction.java 9680 2009-08-06 19:23:51Z ixitar $
 */
package org.exist.xquery.modules.httpclient;

import org.apache.log4j.Logger;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;


/**
 * DOCUMENT ME!
 *
 * @author   Adam Retter <adam.retter@devon.gov.uk>
 * @author   Andrzej Taramina <andrzej@chaeron.com>
 * @version  1.2
 * @serial   20070905
 */

public class ClearFunction extends BaseHTTPClientFunction
{
    protected static final Logger           logger       = Logger.getLogger( ClearFunction.class );

    public final static FunctionSignature[] signatures   = {
        new FunctionSignature( 
			new QName( "clear-persistent-cookies", NAMESPACE_URI, PREFIX ), 
			"Clears any persistent cookies stored in the current session on the client.", 
			null, 
			new SequenceType( Type.ITEM, Cardinality.EMPTY ) 
		)
    };

	
    public ClearFunction( XQueryContext context, FunctionSignature signature  )
    {
        super( context, signature );
    }

	
    public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException
    {
        context.setXQueryContextVar( HTTP_MODULE_PERSISTENT_COOKIES, null );
        return( Sequence.EMPTY_SEQUENCE );
    }

}
