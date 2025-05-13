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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.backup.RawDataBackup;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.RawBackupSupport;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.btree.DBException;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.FileUtils;
import org.w3c.dom.Element;

/**
 * @author <a href="mailto:pierrick.brihaye@free.fr">Pierrick Brihaye</a>
 */
public class GMLHSQLIndex extends AbstractGMLJDBCIndex implements RawBackupSupport {

    private final static Logger LOG = LogManager.getLogger(GMLHSQLIndex.class);

    public static String db_file_name_prefix = "spatial_index";
    //Keep this upper case ;-)
    public static String TABLE_NAME = "SPATIAL_INDEX_V1";
    private DBBroker connectionOwner = null;
    private long connectionTimeout = 100000L;
    
    @Override
    public void configure(BrokerPool pool, Path dataDir, Element config) throws DatabaseConfigurationException {
        super.configure(pool, dataDir, config);
        String param = config.getAttribute("connectionTimeout");
        if (!param.isEmpty()) {
            try {
                connectionTimeout = Long.parseLong(param);
            } catch (NumberFormatException e) {
                LOG.error("Invalid value for 'connectionTimeout'", e);
            }
        }

        param = config.getAttribute("max_docs_in_context_to_refine_query");
        if (!param.isEmpty()) {
            try {
                max_docs_in_context_to_refine_query = Integer.parseInt(param);
            } catch (NumberFormatException e) {
                LOG.error("Invalid value for 'max_docs_in_context_to_refine_query', using default:{}", max_docs_in_context_to_refine_query, e);
            }
        }

        if (LOG.isDebugEnabled())
            LOG.debug("max_docs_in_context_to_refine_query = {}", max_docs_in_context_to_refine_query);
    }

    @Override
    public IndexWorker getWorker(DBBroker broker) {
        AbstractGMLJDBCIndexWorker worker = workers.get(broker);
        if (worker == null) {
            worker = new GMLHSQLIndexWorker(this, broker);
            workers.put(broker, worker);
        }
        return worker;
    }

    @Override
    protected void checkDatabase() throws ClassNotFoundException, SQLException {
        //Test to see if we have a HSQL driver in the classpath
        Class.forName("org.hsqldb.jdbcDriver");
    }

    @Override
    protected void shutdownDatabase() throws DBException {
        try {
            //No need to shutdown if we have opened something
            if (conn != null) {
                Statement stmt = conn.createStatement();
                stmt.executeQuery("SHUTDOWN");
                stmt.close();
                conn.close();
                if (LOG.isDebugEnabled())
                    LOG.debug("GML index: {}/{} closed", getDataDir(), db_file_name_prefix);
            }
        } catch (SQLException e) {
            throw new DBException(e.getMessage());
        } finally {
            conn = null;
        }
    }
    
    @Override
    protected void deleteDatabase() throws DBException {
        final Path directory = getDataDir();
        try {
            final List<Path> files = FileUtils.list(directory, path -> FileUtils.fileName(path).startsWith(db_file_name_prefix));
            boolean deleted = true;
            for (final Path file : files) {
                deleted &= FileUtils.deleteQuietly(file);
            }
        } catch(final IOException e) {
            LOG.error(e);
            throw new DBException(e.getMessage());
        }
        //TODO : raise an error if deleted == false ?
    }

    @Override
    protected void removeIndexContent() throws DBException {
        try {
            //Let's be lazy here : we only delete the index content if we have a connection
            //deleteDatabase() should be far more efficient ;-)
            if (conn != null) {
                Statement stmt = conn.createStatement(); 
                int nodeCount = stmt.executeUpdate("DELETE FROM " + GMLHSQLIndex.TABLE_NAME + ";");
                stmt.close();
                if (LOG.isDebugEnabled())
                    LOG.debug("GML index: {}/{}. {} nodes removed", getDataDir(), db_file_name_prefix, nodeCount);
            }
        } catch (SQLException e) {
            throw new DBException(e.getMessage());
        }
    }

    @Override
    protected Connection acquireConnection(DBBroker broker) throws SQLException {
        //Horrible "locking" mechanism
        synchronized (this) {
            if (connectionOwner == null) {
                connectionOwner = broker;
                if (conn == null)
                    initializeConnection();
                return conn;
            }
            long timeOut_ = connectionTimeout;
            long waitTime = timeOut_;
            long start = System.currentTimeMillis();
            try {
                for (;;) {
                    wait(waitTime);
                    if (connectionOwner == null) {
                        connectionOwner = broker;
                        if (conn == null)
                            //We should never get there since the connection should have been initialized
                            //by the first request from a worker
                            initializeConnection();
                        return conn;
                    }
                    waitTime = timeOut_ - (System.currentTimeMillis() - start);
                    if (waitTime <= 0) {
                        LOG.error("Time out while trying to get connection");
                        return null;
                    }
                }
            } catch (InterruptedException ex) {
                notify();
                throw new RuntimeException("interrupted while waiting for lock");
            }
        }
    }

    @Override
    protected synchronized void releaseConnection(DBBroker broker) throws SQLException {   
        if (connectionOwner == null)
            throw new SQLException("Attempted to release a connection that wasn't acquired");
        connectionOwner = null;
    }

    private void initializeConnection() throws SQLException {
        System.setProperty("hsqldb.cache_scale", "11");
        System.setProperty("hsqldb.cache_size_scale", "12");
        System.setProperty("hsqldb.default_table_type", "cached");
        //Get a connection to the DB... and keep it
        this.conn = DriverManager.getConnection("jdbc:hsqldb:" + getDataDir() + "/" + db_file_name_prefix + ";sql.enforce_size=false" /* + ";shutdown=true" */, "sa", "");
        if (LOG.isDebugEnabled())
            LOG.debug("Connected to GML index: {}/{}", getDataDir(), db_file_name_prefix);
        try (ResultSet rs = this.conn.getMetaData().getTables(null, null, TABLE_NAME, new String[]{"TABLE"})) {
            rs.last();
            if (rs.getRow() == 1) {
                if (LOG.isDebugEnabled())
                    LOG.debug("Opened GML index: {}/{}", getDataDir(), db_file_name_prefix);
                //Create the data structure if it doesn't exist
            } else if (rs.getRow() == 0) {
                Statement stmt = conn.createStatement();
                stmt.executeUpdate("CREATE TABLE " + TABLE_NAME + "(" +
                        /*1*/ "DOCUMENT_URI VARCHAR, " +
                        /*2*/ "NODE_ID_UNITS INTEGER, " +
                        /*3*/ "NODE_ID BINARY, " +
                        /*4*/ "GEOMETRY_TYPE VARCHAR, " +
                        /*5*/ "SRS_NAME VARCHAR, " +
                        /*6*/ "WKT VARCHAR(500000), " +
                        /*7*/ "WKB BINARY, " +
                        /*8*/ "MINX DOUBLE, " +
                        /*9*/ "MAXX DOUBLE, " +
                        /*10*/ "MINY DOUBLE, " +
                        /*11*/ "MAXY DOUBLE, " +
                        /*12*/ "CENTROID_X DOUBLE, " +
                        /*13*/ "CENTROID_Y DOUBLE, " +
                        /*14*/ "AREA DOUBLE, " +
                        //Boundary ?
                        /*15*/ "EPSG4326_WKT VARCHAR(500000), " +
                        /*16*/ "EPSG4326_WKB BINARY, " +
                        /*17*/ "EPSG4326_MINX DOUBLE, " +
                        /*18*/ "EPSG4326_MAXX DOUBLE, " +
                        /*19*/ "EPSG4326_MINY DOUBLE, " +
                        /*20*/ "EPSG4326_MAXY DOUBLE, " +
                        /*21*/ "EPSG4326_CENTROID_X DOUBLE, " +
                        /*22*/ "EPSG4326_CENTROID_Y DOUBLE, " +
                        /*23*/ "EPSG4326_AREA DOUBLE, " +
                        //Boundary ?
                        /*24*/ "IS_CLOSED BOOLEAN, " +
                        /*25*/ "IS_SIMPLE BOOLEAN, " +
                        /*26*/ "IS_VALID BOOLEAN, " +
                        //Enforce uniqueness
                        "UNIQUE (" +
                        "DOCUMENT_URI, NODE_ID_UNITS, NODE_ID" +
                        ")" +
                        ")");
                stmt.executeUpdate("CREATE INDEX DOCUMENT_URI ON " + TABLE_NAME + " (DOCUMENT_URI);");
                stmt.executeUpdate("CREATE INDEX NODE_ID ON " + TABLE_NAME + " (NODE_ID);");
                stmt.executeUpdate("CREATE INDEX GEOMETRY_TYPE ON " + TABLE_NAME + " (GEOMETRY_TYPE);");
                stmt.executeUpdate("CREATE INDEX SRS_NAME ON " + TABLE_NAME + " (SRS_NAME);");
                stmt.executeUpdate("CREATE INDEX WKB ON " + TABLE_NAME + " (WKB);");
                stmt.executeUpdate("CREATE INDEX EPSG4326_WKB ON " + TABLE_NAME + " (EPSG4326_WKB);");
                stmt.executeUpdate("CREATE INDEX EPSG4326_MINX ON " + TABLE_NAME + " (EPSG4326_MINX);");
                stmt.executeUpdate("CREATE INDEX EPSG4326_MAXX ON " + TABLE_NAME + " (EPSG4326_MAXX);");
                stmt.executeUpdate("CREATE INDEX EPSG4326_MINY ON " + TABLE_NAME + " (EPSG4326_MINY);");
                stmt.executeUpdate("CREATE INDEX EPSG4326_MAXY ON " + TABLE_NAME + " (EPSG4326_MAXY);");
                stmt.executeUpdate("CREATE INDEX EPSG4326_CENTROID_X ON " + TABLE_NAME + " (EPSG4326_CENTROID_X);");
                stmt.executeUpdate("CREATE INDEX EPSG4326_CENTROID_Y ON " + TABLE_NAME + " (EPSG4326_CENTROID_Y);");
                //AREA ?
                stmt.close();
                if (LOG.isDebugEnabled())
                    LOG.debug("Created GML index: {}/{}", getDataDir(), db_file_name_prefix);
            } else {
                throw new SQLException("2 tables with the same name ?");
            }
        }
    }

	@Override
	public void backupToArchive(RawDataBackup backup) throws IOException {
        final Path directory = getDataDir();
        final List<Path> files = FileUtils.list(directory, path -> FileUtils.fileName(path).startsWith(db_file_name_prefix));
        for (final Path file : files) {
			try(final OutputStream os = backup.newEntry(FileUtils.fileName(file))) {
                Files.copy(file, os);
            } finally {
                backup.closeEntry();
            }
        }
	}
	
}
