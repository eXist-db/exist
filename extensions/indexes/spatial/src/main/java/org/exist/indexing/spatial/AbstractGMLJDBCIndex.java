/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.indexing.spatial;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.indexing.AbstractIndex;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.StreamListener.ReindexMode;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.btree.DBException;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;

/**
 * @author <a href="mailto:pierrick.brihaye@free.fr">Pierrick Brihaye</a>
 */
public abstract class AbstractGMLJDBCIndex extends AbstractIndex {

    /**
     * Holds the index ID. Notice that we delegate this task to the abstract JDBC class,
     * not to the concrete HSQL (or whatever) one. This allows spatial functions to use
     * the available JDBC index, whatever its underlying engine is.
     */
    public final static String ID = AbstractGMLJDBCIndex.class.getName();	

    @SuppressWarnings("unused")
    private final static Logger LOG = LogManager.getLogger(AbstractGMLJDBCIndex.class);

    /**
     * An IndexWorker "pool"
     */
    protected HashMap<DBBroker, AbstractGMLJDBCIndexWorker> workers = new HashMap<>();

    /**
     * The connection to the DB that will be needed for global operations 
     */
    protected Connection conn = null;

    /**
     * The spatial operators to test spatial relationshipds beween geometries.
     * See http://www.vividsolutions.com/jts/bin/JTS%20Technical%20Specs.pdf (chapter 11).
     */
    public interface SpatialOperator { 
        public static int UNKNOWN = -1;
        public static int EQUALS = 1;
        public static int DISJOINT = 2;
        public static int INTERSECTS = 3;
        public static int TOUCHES = 4;
        public static int CROSSES = 5;
        public static int WITHIN = 6;
        public static int CONTAINS = 7;
        public static int OVERLAPS = 8;
    }

    protected int max_docs_in_context_to_refine_query = 10;

    @Override
    public void configure(BrokerPool pool, Path dataDir, Element config) throws DatabaseConfigurationException {
        super.configure(pool, dataDir, config);
        try {
            checkDatabase();
        } catch (final ClassNotFoundException | SQLException e) {
            throw new DatabaseConfigurationException(e.getMessage());
        }
    }

    public int getMaxDocsInContextToRefineQuery() {
        return max_docs_in_context_to_refine_query;
    }

    @Override
    public void open() throws DatabaseConfigurationException {
        //Nothing particular to do : the connection will be opened on request      
    }

    @Override
    public void close() throws DBException {
        for (AbstractGMLJDBCIndexWorker worker : workers.values()) {
            //Flush any pending stuff 
            worker.flush();
            //Reset state
            worker.setDocument(null, ReindexMode.UNKNOWN);
        }
        shutdownDatabase();
    }

    //Seems to never be used
    @Override
    public void sync() throws DBException {
        //TODO : something useful here
        /*
        try {
            if (conn != null)
                conn.commit();
        } catch (SQLException e) {
            throw new DBException(e.getMessage());
        }
        */
    }

    @Override
    public void remove() throws DBException {
        for (AbstractGMLJDBCIndexWorker worker : workers.values()) {
            //Flush any pending stuff
            worker.flush();
            //Reset state
            worker.setDocument(null, ReindexMode.UNKNOWN);
        }
        removeIndexContent();
        shutdownDatabase();
        deleteDatabase();
    }

    @Override
    public boolean checkIndex(DBBroker broker) {
        return getWorker(broker).checkIndex(broker);
    }

    @Override
    public abstract IndexWorker getWorker(DBBroker broker);

    /**
     * Checks if the JDBC database that contains the indexed spatial data is available an reachable.
     * Creates it if necessary.
     * 
     * @throws ClassNotFoundException if the JDBC driver can not be found
     * @throws SQLException if the database is not reachable
     */
    protected abstract void checkDatabase() throws ClassNotFoundException, SQLException;

    /**
     * Shuts down the JDBC database that contains the indexed spatial data.
     * 
     * @throws DBException if an error occurs with the database
     */
    protected abstract void shutdownDatabase() throws DBException;

    /**
     * Deletes the JDBC database that contains the indexed spatial data.
     *
     * @throws DBException if an error occurs with the database
     */
    protected abstract void deleteDatabase() throws DBException;

    /**
     * Deletes the spatial data contained in the JDBC database.
     *
     * @throws DBException if an error occurs with the database
     */
    protected abstract void removeIndexContent() throws DBException;

    /**
     * Convenience method that can be used by the IndexWorker to acquire a connection 
     * to the JDBC database that contains the indexed spatial data.
     * 
     * @param broker the broker that will use th connection
     *
     * @return the connection
     *
     * @throws SQLException if a JDBC error occurs
     */
    protected abstract Connection acquireConnection(DBBroker broker) throws SQLException;

    /**
     * Convenience method that can be used by the IndexWorker to release a connection 
     * to the JDBC database that contains the indexed spatial data. This connection should have been
     * previously acquired by {@link org.exist.indexing.spatial.AbstractGMLJDBCIndex#acquireConnection(DBBroker)} 
     * 
     * @param broker the broker that will use th connection
     *
     * @throws SQLException if a JDBC error occurs
     */
    protected abstract void releaseConnection(DBBroker broker) throws SQLException;
}
