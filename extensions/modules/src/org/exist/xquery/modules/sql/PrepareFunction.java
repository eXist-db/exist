/*
 *  eXist SQL Module Extension ExecuteFunction
 *  Copyright (C) 2006-10 Adam Retter <adam@exist-db.org>
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
 *  $Id$
 */

package org.exist.xquery.modules.sql;

import org.apache.log4j.Logger;

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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;


/**
 * eXist SQL Module Extension PrepareFunction.
 *
 * <p>Prepare a SQL statement against a SQL capable Database</p>
 *
 * @author   Adam Retter <adam@exist-db.org>
 * @version  1.0
 * @see      org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext, org.exist.xquery.FunctionSignature)
 * @serial   2010-03-17
 */
public class PrepareFunction extends BasicFunction
{
    private static final Logger             LOG        = Logger.getLogger( PrepareFunction.class );

    public final static FunctionSignature[] signatures = { 
    	new FunctionSignature(
			new QName( "prepare", SQLModule.NAMESPACE_URI, SQLModule.PREFIX ),
			"Prepares a SQL statement against a SQL db using the connection indicated by the connection handle.",
			new SequenceType[] {
				new FunctionParameterSequenceType( "handle", Type.LONG, Cardinality.EXACTLY_ONE, "The connection handle" ),
				new FunctionParameterSequenceType( "sql-statement", Type.STRING, Cardinality.EXACTLY_ONE, "The SQL statement" ),
			},
			new FunctionReturnSequenceType( Type.LONG, Cardinality.ZERO_OR_ONE, "an xs:long representing the statement handle")
    	) 
    };

    /**
     * PrepareFunction Constructor.
     *
     * @param  context    The Context of the calling XQuery
     * @param  signature  DOCUMENT ME!
     */
    public PrepareFunction( XQueryContext context, FunctionSignature signature )
    {
        super( context, signature );
    }

    /**
     * evaluate the call to the XQuery prepare() function, it is really the main entry point of this class.
     *
     * @param   args             arguments from the prepare() function call
     * @param   contextSequence  the Context Sequence to operate on (not used here internally!)
     *
     * @return  A xs:long representing the handle to the prepared statement
     *
     * @throws  XPathException  DOCUMENT ME!
     *
     * @see     org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    @Override public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException
    {
        // was a connection and SQL statement specified?
        if( args[0].isEmpty() || args[1].isEmpty() ) {
            return( Sequence.EMPTY_SEQUENCE );
        }

        // get the Connection
        long       connectionUID = ( (IntegerValue)args[0].itemAt( 0 ) ).getLong();
        Connection con           = SQLModule.retrieveConnection( context, connectionUID );

        if( con == null ) {
            return( Sequence.EMPTY_SEQUENCE );
        }

        // get the SQL statement
        String            sql  = args[1].getStringValue();

        PreparedStatement stmt = null;

        try {

            // execute the SQL statement
            stmt = con.prepareStatement( sql );

            // store the PreparedStatement and return the uid handle of the PreparedStatement
            return( new IntegerValue( SQLModule.storePreparedStatement( context, new PreparedStatementWithSQL( sql, stmt ) ) ) );
        }
        catch( SQLException sqle ) {
            LOG.error( "sql:prepare() Caught SQLException \"" + sqle.getMessage() + "\" for SQL: \"" + sql + "\"", sqle );

            throw( new XPathException( this, "sql:prepare() Caught SQLException \"" + sqle.getMessage() + "\" for SQL: \"" + sql + "\"", sqle ) );
        }
    }
}
