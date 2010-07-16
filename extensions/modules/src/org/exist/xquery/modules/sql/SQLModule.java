/*
 *  eXist SQL Module Extension
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

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XQueryContext;

import java.sql.Connection;
import java.sql.SQLException;

import java.util.HashMap;
import java.util.Map.Entry;


/**
 * eXist SQL Module Extension.
 *
 * <p>An extension module for the eXist Native XML Database that allows queries against SQL Databases, returning an XML representation of the result
 * set.</p>
 *
 * @author   Adam Retter <adam@exist-db.org>
 * @author   ljo
 * @version  1.2
 * @see      org.exist.xquery.AbstractInternalModule#AbstractInternalModule(org.exist.xquery.FunctionDef[])
 * @serial   2010-03-18
 */

public class SQLModule extends AbstractInternalModule
{
    protected final static Logger      LOG                            = Logger.getLogger( SQLModule.class );

    public final static String         NAMESPACE_URI                  = "http://exist-db.org/xquery/sql";

    public final static String         PREFIX                         = "sql";
    public final static String         INCLUSION_DATE                 = "2006-09-25";
    public final static String         RELEASED_IN_VERSION            = "eXist-1.2";

    private final static FunctionDef[] functions                      = {
        new FunctionDef( GetConnectionFunction.signatures[0], GetConnectionFunction.class ),
        new FunctionDef( GetConnectionFunction.signatures[1], GetConnectionFunction.class ),
        new FunctionDef( GetConnectionFunction.signatures[2], GetConnectionFunction.class ),
        new FunctionDef( GetJNDIConnectionFunction.signatures[0], GetJNDIConnectionFunction.class ),
        new FunctionDef( GetJNDIConnectionFunction.signatures[1], GetJNDIConnectionFunction.class ),
        new FunctionDef( ExecuteFunction.signatures[0], ExecuteFunction.class ),
        new FunctionDef( ExecuteFunction.signatures[1], ExecuteFunction.class ),
        new FunctionDef( PrepareFunction.signatures[0], PrepareFunction.class )
    };

    private static long                currentUID                     = System.currentTimeMillis();
    public final static String         CONNECTIONS_CONTEXTVAR         = "_eXist_sql_connections";
    public final static String         PREPARED_STATEMENTS_CONTEXTVAR = "_eXist_sql_prepared_statements";

    public SQLModule()
    {
        super( functions );
    }

    @Override public String getNamespaceURI()
    {
        return( NAMESPACE_URI );
    }


    @Override public String getDefaultPrefix()
    {
        return( PREFIX );
    }


    @Override public String getDescription()
    {
        return( "A module for performing SQL queries against Databases, returning XML representations of the result sets." );
    }


    @Override public String getReleaseVersion()
    {
        return( RELEASED_IN_VERSION );
    }


    /**
     * Retrieves a previously stored Connection from the Context of an XQuery.
     *
     * @param   context        The Context of the XQuery containing the Connection
     * @param   connectionUID  The UID of the Connection to retrieve from the Context of the XQuery
     *
     * @return  DOCUMENT ME!
     */
    public final static Connection retrieveConnection( XQueryContext context, long connectionUID )
    {
        return( retrieveObjectFromContextMap( context, SQLModule.CONNECTIONS_CONTEXTVAR, connectionUID ) );
    }


    /**
     * Stores a Connection in the Context of an XQuery.
     *
     * @param   context  The Context of the XQuery to store the Connection in
     * @param   con      The connection to store
     *
     * @return  A unique ID representing the connection
     */
    public final static synchronized long storeConnection( XQueryContext context, Connection con )
    {
        return( storeObjectInContextMap( context, SQLModule.CONNECTIONS_CONTEXTVAR, con ) );
    }


    /**
     * Retrieves a previously stored PreparedStatement from the Context of an XQuery.
     *
     * @param   context               The Context of the XQuery containing the PreparedStatement
     * @param   preparedStatementUID  The UID of the PreparedStatement to retrieve from the Context of the XQuery
     *
     * @return  DOCUMENT ME!
     */
    public final static PreparedStatementWithSQL retrievePreparedStatement( XQueryContext context, long preparedStatementUID )
    {
        return( retrieveObjectFromContextMap( context, SQLModule.PREPARED_STATEMENTS_CONTEXTVAR, preparedStatementUID ) );
    }


    /**
     * Stores a PreparedStatement in the Context of an XQuery.
     *
     * @param   context  The Context of the XQuery to store the PreparedStatement in
     * @param   stmt     preparedStatement The PreparedStatement to store
     *
     * @return  A unique ID representing the PreparedStatement
     */
    public final static synchronized long storePreparedStatement( XQueryContext context, PreparedStatementWithSQL stmt )
    {
        return( storeObjectInContextMap( context, SQLModule.PREPARED_STATEMENTS_CONTEXTVAR, stmt ) );
    }


    /**
     * Retrieves a previously stored Object from the Context of an XQuery.
     *
     * @param   context         The Context of the XQuery containing the Object
     * @param   contextMapName  DOCUMENT ME!
     * @param   objectUID       The UID of the Object to retrieve from the Context of the XQuery
     *
     * @return  DOCUMENT ME!
     */
    private static <T> T retrieveObjectFromContextMap( XQueryContext context, String contextMapName, long objectUID )
    {
        // get the existing connections map from the context
        HashMap<Long, T> map = (HashMap<Long, T>)context.getXQueryContextVar( contextMapName );

        if( map == null ) {
            return( null );
        }

        // get the connection
        return( map.get( objectUID ) );
    }


    /**
     * Stores an Object in the Context of an XQuery.
     *
     * @param   context         The Context of the XQuery to store the Object in
     * @param   contextMapName  The name of the context map
     * @param   o               The Object to store
     *
     * @return  A unique ID representing the Object
     */
    private static synchronized <T> long storeObjectInContextMap( XQueryContext context, String contextMapName, T o )
    {
        // get the existing map from the context
        HashMap<Long, T> map = (HashMap<Long, T>)context.getXQueryContextVar( contextMapName );

        if( map == null ) {

            // if there is no map, create a new one
            map = new HashMap<Long, T>();
        }

        // get an id for the map
        long uid = getUID();

        // place the object in the map
        map.put( uid, o );

        // store the map back in the context
        context.setXQueryContextVar( contextMapName, map );

        return( uid );
    }


    /**
     * Closes all the open DB Connections for the specified XQueryContext.
     *
     * @param  xqueryContext  The context to close JDBC Connections for
     */
    private static void closeAllConnections( XQueryContext xqueryContext )
    {
        // get the existing Connections map from the context
        HashMap<Long, Connection> connections = (HashMap<Long, Connection>)xqueryContext.getXQueryContextVar( SQLModule.CONNECTIONS_CONTEXTVAR );

        if( connections != null ) {

            // iterate over each Connection
            for( Entry<Long, Connection> entry : connections.entrySet() ) {
                Long       conID = entry.getKey();
                Connection con   = entry.getValue();

                try {

                    // close the Connection
                    con.close();
                }
                catch( SQLException se ) {
                    LOG.debug( "Unable to close JDBC Connection", se );
                }
                finally {

                    // remove it from the Connections map
                    connections.remove( conID );
                    con = null;
                }
            }

            // update the context
            xqueryContext.setXQueryContextVar( SQLModule.CONNECTIONS_CONTEXTVAR, connections );
        }
    }


    /**
     * Closes all the open DB PreparedStatements for the specified XQueryContext.
     *
     * @param  xqueryContext  The context to close JDBC PreparedStatements for
     */
    private static void closeAllPreparedStatements( XQueryContext xqueryContext )
    {
        // get the existing PreparedStatements map from the context
        HashMap<Long, PreparedStatementWithSQL> preparedStatements = (HashMap<Long, PreparedStatementWithSQL>)xqueryContext.getXQueryContextVar( SQLModule.PREPARED_STATEMENTS_CONTEXTVAR );

        if( preparedStatements != null ) {

            // iterate over each PreparedStatement
            for( Entry<Long, PreparedStatementWithSQL> entry : preparedStatements.entrySet() ) {
                Long                     conID = entry.getKey();
                PreparedStatementWithSQL stmt  = entry.getValue();

                try {

                    // close the PreparedStatement
                    stmt.getStmt().close();
                }
                catch( SQLException se ) {
                    LOG.debug( "Unable to close JDBC PreparedStatement", se );
                }
                finally {

                    // remove it from the connections map
                    preparedStatements.remove( conID );
                    stmt = null;
                }
            }

            // update the context
            xqueryContext.setXQueryContextVar( SQLModule.PREPARED_STATEMENTS_CONTEXTVAR, preparedStatements );
        }
    }


    /**
     * Returns a Unique ID based on the System Time.
     *
     * @return  The Unique ID
     */
    private static synchronized long getUID()
    {
        return( currentUID++ );
    }


    /**
     * Resets the Module Context and closes any DB connections for the XQueryContext.
     *
     * @param  xqueryContext  The XQueryContext
     */
    @Override public void reset( XQueryContext xqueryContext )
    {
        // reset the module context
        super.reset( xqueryContext );

        // close any open PreparedStatements
        closeAllPreparedStatements( xqueryContext );

        // close any open Connections
        closeAllConnections( xqueryContext );
    }
}
