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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XQueryContext;

import java.sql.Connection;
import java.sql.SQLException;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.modules.ModuleUtils.ContextMapEntryModifier;

/**
 * eXist SQL Module Extension.
 *
 * An extension module for the eXist Native XML Database that allows queries against SQL Databases, returning an XML representation of the result
 * set.
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @author   ljo
 * @version  1.2
 * @see      org.exist.xquery.AbstractInternalModule#AbstractInternalModule(org.exist.xquery.FunctionDef[], java.util.Map) 
 * @serial   2010-03-18
 */
public class SQLModule extends AbstractInternalModule {

    protected final static Logger LOG = LogManager.getLogger(SQLModule.class);
    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/sql";
    public final static String PREFIX = "sql";
    public final static String INCLUSION_DATE = "2006-09-25";
    public final static String RELEASED_IN_VERSION = "eXist-1.2";
    private final static FunctionDef[] functions = {
        new FunctionDef(GetConnectionFunction.signatures[0], GetConnectionFunction.class),
        new FunctionDef(GetConnectionFunction.signatures[1], GetConnectionFunction.class),
        new FunctionDef(GetConnectionFunction.signatures[2], GetConnectionFunction.class),
        new FunctionDef(GetJNDIConnectionFunction.signatures[0], GetJNDIConnectionFunction.class),
        new FunctionDef(GetJNDIConnectionFunction.signatures[1], GetJNDIConnectionFunction.class),
        new FunctionDef(ExecuteFunction.signatures[0], ExecuteFunction.class),
        new FunctionDef(ExecuteFunction.signatures[1], ExecuteFunction.class),
        new FunctionDef(PrepareFunction.signatures[0], PrepareFunction.class)
    };
    private static final long currentUID = System.currentTimeMillis();
    public final static String CONNECTIONS_CONTEXTVAR = "_eXist_sql_connections";
    public final static String PREPARED_STATEMENTS_CONTEXTVAR = "_eXist_sql_prepared_statements";

    public SQLModule(Map<String, List<?>> parameters) {
        super(functions, parameters);
    }

    @Override
    public String getNamespaceURI() {
        return (NAMESPACE_URI);
    }

    @Override
    public String getDefaultPrefix() {
        return (PREFIX);
    }

    @Override
    public String getDescription() {
        return ("A module for performing SQL queries against Databases, returning XML representations of the result sets.");
    }

    @Override
    public String getReleaseVersion() {
        return (RELEASED_IN_VERSION);
    }

    /**
     * Retrieves a previously stored Connection from the Context of an XQuery.
     *
     * @param   context        The Context of the XQuery containing the Connection
     * @param   connectionUID  The UID of the Connection to retrieve from the Context of the XQuery
     *
     * @return  DOCUMENT ME!
     */
    public static Connection retrieveConnection(XQueryContext context, long connectionUID) {
        return ModuleUtils.retrieveObjectFromContextMap(context, SQLModule.CONNECTIONS_CONTEXTVAR, connectionUID);
    }

    /**
     * Stores a Connection in the Context of an XQuery.
     *
     * @param   context  The Context of the XQuery to store the Connection in
     * @param   con      The connection to store
     *
     * @return  A unique ID representing the connection
     */
    public static synchronized long storeConnection(XQueryContext context, Connection con) {
        return ModuleUtils.storeObjectInContextMap(context, SQLModule.CONNECTIONS_CONTEXTVAR, con);
    }

    /**
     * Retrieves a previously stored PreparedStatement from the Context of an XQuery.
     *
     * @param   context               The Context of the XQuery containing the PreparedStatement
     * @param   preparedStatementUID  The UID of the PreparedStatement to retrieve from the Context of the XQuery
     *
     * @return  DOCUMENT ME!
     */
    public static PreparedStatementWithSQL retrievePreparedStatement(XQueryContext context, long preparedStatementUID) {
        return ModuleUtils.retrieveObjectFromContextMap(context, SQLModule.PREPARED_STATEMENTS_CONTEXTVAR, preparedStatementUID);
    }

    /**
     * Stores a PreparedStatement in the Context of an XQuery.
     *
     * @param   context  The Context of the XQuery to store the PreparedStatement in
     * @param   stmt     preparedStatement The PreparedStatement to store
     *
     * @return  A unique ID representing the PreparedStatement
     */
    public static synchronized long storePreparedStatement(XQueryContext context, PreparedStatementWithSQL stmt) {
        return ModuleUtils.storeObjectInContextMap(context, SQLModule.PREPARED_STATEMENTS_CONTEXTVAR, stmt);
    }

    /**
     * Resets the Module Context and closes any DB connections for the XQueryContext.
     *
     * @param  xqueryContext  The XQueryContext
     */
    @Override
    public void reset(XQueryContext xqueryContext, boolean keepGlobals) {
        // reset the module context
        super.reset(xqueryContext, keepGlobals);

        // close any open PreparedStatements
        closeAllPreparedStatements(xqueryContext);

        // close any open Connections
        closeAllConnections(xqueryContext);
    }
    
    /**
     * Closes all the open DB Connections for the specified XQueryContext.
     *
     * @param  xqueryContext  The context to close JDBC Connections for
     */
    private static void closeAllConnections(XQueryContext xqueryContext) {
        ModuleUtils.modifyContextMap(xqueryContext, SQLModule.CONNECTIONS_CONTEXTVAR, new ContextMapEntryModifier<Connection>(){
            
            @Override 
            public void modify(Map<Long, Connection> map) {
                super.modify(map);
                
                //empty the map
                map.clear();
            }
            
            @Override
            public void modify(Entry<Long, Connection> entry) {
                final Connection con = entry.getValue();
                try {
                    // close the Connection
                    con.close();
                } catch(SQLException se) {
                    LOG.warn("Unable to close JDBC Connection: " + se.getMessage(), se);
                }
            }
        });
        
        // update the context
        //ModuleUtils.storeContextMap(xqueryContext, SQLModule.CONNECTIONS_CONTEXTVAR, connections);
    }

    /**
     * Closes all the open DB PreparedStatements for the specified XQueryContext.
     *
     * @param  xqueryContext  The context to close JDBC PreparedStatements for
     */
    private static void closeAllPreparedStatements(XQueryContext xqueryContext) {
        ModuleUtils.modifyContextMap(xqueryContext, SQLModule.PREPARED_STATEMENTS_CONTEXTVAR, new ContextMapEntryModifier<PreparedStatementWithSQL>(){
            
            @Override 
            public void modify(Map<Long, PreparedStatementWithSQL> map) {
                super.modify(map);
                
                //empty the map
                map.clear();
            }
            
            @Override
            public void modify(Entry<Long, PreparedStatementWithSQL> entry) {
                final PreparedStatementWithSQL stmt = entry.getValue();
                try {
                    // close the PreparedStatement
                    stmt.getStmt().close();
                } catch(SQLException se) {
                    LOG.warn("Unable to close JDBC PreparedStatement: " + se.getMessage(), se);
                }
            }
        });
        
        // update the context
        //ModuleUtils.storeContextMap(xqueryContext, SQLModule.PREPARED_STATEMENTS_CONTEXTVAR, preparedStatements);
    }
}