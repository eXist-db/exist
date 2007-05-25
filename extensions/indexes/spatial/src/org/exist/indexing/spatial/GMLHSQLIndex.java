package org.exist.indexing.spatial;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.log4j.Logger;
import org.exist.indexing.AbstractIndex;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.StreamListener;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.btree.DBException;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;

/**
 */
public class GMLHSQLIndex extends AbstractIndex {
	
	public final static String ID = GMLHSQLIndex.class.getName();
    
	private final static Logger LOG = Logger.getLogger(GMLHSQLIndex.class);	
	
    private Connection conn = null;

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
    
    public static String db_file_name_prefix = "spatial_index";
    //Keep this upper case ;-)
    public static String TABLE_NAME = "SPATIAL_INDEX_V1";
    
    //Make a pool for each broker ?
    private AbstractGMLJDBCIndexWorker worker;
    private boolean workerHasConnection = false;
    
    public GMLHSQLIndex() {    	
    }  
    
    public String getIndexId() {
    	return ID;
    }
    
    public void configure(BrokerPool pool, String dataDir, Element config) throws DatabaseConfigurationException {        
    	super.configure(pool, dataDir, config);
    	try {
        	checkDatabase();
        } catch (ClassNotFoundException e) {
        	throw new DatabaseConfigurationException(e.getMessage()); 
        } catch (SQLException e) {
        	throw new DatabaseConfigurationException(e.getMessage()); 
        }
    }

    public void open() throws DatabaseConfigurationException {
        //Nothing particular to do : the connection will be opened on request      
    }

    public void close() throws DBException {
		//Provisional workaround to avoid sending flush() events
    	if (worker != null) {
			worker.flush();
			worker.setDocument(null, StreamListener.UNKNOWN);
    	}
    	shutdownDatabase();
    }

    //Seems to never be used
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
    
    public void remove() throws DBException {
		//Provisional workaround to avoid sending flush() events
		worker.flush();
		worker.setDocument(null, StreamListener.UNKNOWN); 
		remove(this.conn);
        shutdownDatabase();
    }
    
    public boolean checkIndex(DBBroker broker) {
    	return getWorker(broker).checkIndex(broker);
    } 
    
    public IndexWorker getWorker(DBBroker broker) {
    	//TODO : see above. We might want a pool here
    	if (worker == null)
    		worker = new GMLHSQLIndexWorker(this, null);
    		//worker = new GMLHSQLIndexWorker(this, broker);
    	return worker;
    } 
    
    protected void checkDatabase() throws ClassNotFoundException, SQLException {
    	//Test to see if we have a HSQL driver in the classpath
    	Class.forName("org.hsqldb.jdbcDriver");		
    }
    
    protected void shutdownDatabase() throws DBException {
		try {
			//No need to shutdown if we haven't opened anything
			if (conn != null) {
				Statement stmt = conn.createStatement();				
				stmt.executeQuery("SHUTDOWN");
				stmt.close();
				conn.close();				
				if (LOG.isDebugEnabled()) 
	                LOG.debug("GML index: " + getDataDir() + "/" + db_file_name_prefix + " closed");
			}
        } catch (SQLException e) {
        	throw new DBException(e.getMessage()); 
        } finally {
        	conn = null;
        }
    }
    
    protected void remove(Connection conn) throws DBException {
		try {
			if (conn != null) {
				Statement stmt = conn.createStatement(); 
		        int nodeCount = stmt.executeUpdate("DELETE FROM " + GMLHSQLIndex.TABLE_NAME + ";");       
		        stmt.close();
		        if (LOG.isDebugEnabled()) 
		            LOG.debug("GML index: " + getDataDir() + "/" + db_file_name_prefix + ". " + nodeCount + " nodes removed");
			}
	        //TODO : should we remove the db files as well ?
	    } catch (SQLException e) {
	    	throw new DBException(e.getMessage()); 
	    } 
    }    
    
    //Horrible "locking" mechanism
    protected Connection acquireConnection(DBBroker broker) {
    	synchronized (this) {	
    		if (!workerHasConnection) {
    			workerHasConnection = true;
    			if (conn == null)
    				initializeConnection();
    	    	return conn;
    		} else {    
	    		long timeOut_ = 100000L;
	    		long waitTime = timeOut_;
				long start = System.currentTimeMillis();
				try {
					for (;;) {
						wait(waitTime);  
			    		if (!workerHasConnection) {			    			
			    			workerHasConnection = true;			    			
			    			if (conn == null)
			    				//We should never get there since the connection should have been initialized
			    				///by the first request from a worker
			    				initializeConnection();			    			
			    	    	return conn; 			
			    		} else {
							waitTime = timeOut_ - (System.currentTimeMillis() - start);
							if (waitTime <= 0) {
								LOG.error("Time out while trying to get connection");
							}
			    		}
					}
				} catch (InterruptedException ex) {
					notify();
					throw new RuntimeException("interrupted while waiting for lock");
				}
    		}
    	}
    }
    
    protected synchronized void releaseConnection(DBBroker broker) {   
    	workerHasConnection = false;    	
    }  
    
    private void initializeConnection() {
    	try {
	    	System.setProperty("hsqldb.cache_scale", "11");
			System.setProperty("hsqldb.cache_size_scale", "12");
			System.setProperty("hsqldb.default_table_type", "cached");
			//Get a connection to the DB... and keep it
			this.conn = DriverManager.getConnection("jdbc:hsqldb:" + getDataDir() + "/" + db_file_name_prefix /* + ";shutdown=true" */, "sa", "");
			ResultSet rs = null;
			try {
	        	rs = this.conn.getMetaData().getTables(null, null, TABLE_NAME, new String[] { "TABLE" });
	        	rs.last(); 
	        	if (rs.getRow() == 1) {
		            if (LOG.isDebugEnabled()) 
		                LOG.debug("Opened GML index: " + getDataDir() + "/" + db_file_name_prefix); 
		        //Create the data structure if it doesn't exist
	        	} else if (rs.getRow() == 0) {
		        	Statement stmt = conn.createStatement();        	
		        	//Use CACHED table, not MEMORY one
		        	//TODO : use hsqldb.default_table_type	        	
		        	stmt.executeUpdate("CREATE TABLE " + TABLE_NAME + "(" +
		        			"DOCUMENT_URI VARCHAR, " +        		
		        			//TODO : use binary format ?
		        			"NODE_ID VARCHAR, " +        			
		        			"GEOMETRY_TYPE VARCHAR, " +
		        			"SRS_NAME VARCHAR, " +
		        			"WKT VARCHAR, " +
		        			//TODO : use binary format ?
		        			"BASE64_WKB VARCHAR, " +
		        			"WSG84_WKT VARCHAR, " +
		        			//TODO : use binary format ?
		        			"WSG84_BASE64_WKB VARCHAR, " +
		        			"WSG84_MINX DOUBLE, " +
		        			"WSG84_MAXX DOUBLE, " +
		        			"WSG84_MINY DOUBLE, " +
		        			"WSG84_MAXY DOUBLE, " +
		        			"WSG84_CENTROID_X DOUBLE, " +
		        			"WSG84_CENTROID_Y DOUBLE, " +
		        			"WSG84_AREA DOUBLE, " +
		        			//Enforce uniqueness
		        			"UNIQUE (" +
		        				"DOCUMENT_URI, NODE_ID" +
		        			")" +
		        		")"
	        		);
		        	stmt.executeUpdate("CREATE INDEX DOCUMENT_URI ON " + TABLE_NAME + " (DOCUMENT_URI);");
		        	stmt.executeUpdate("CREATE INDEX NODE_ID ON " + TABLE_NAME + " (NODE_ID);");
		        	stmt.executeUpdate("CREATE INDEX GEOMETRY_TYPE ON " + TABLE_NAME + " (GEOMETRY_TYPE);");
		        	stmt.executeUpdate("CREATE INDEX SRS_NAME ON " + TABLE_NAME + " (SRS_NAME);");
		        	stmt.executeUpdate("CREATE INDEX WSG84_MINX ON " + TABLE_NAME + " (WSG84_MINX);");
		        	stmt.executeUpdate("CREATE INDEX WSG84_MAXX ON " + TABLE_NAME + " (WSG84_MAXX);");
		        	stmt.executeUpdate("CREATE INDEX WSG84_MINY ON " + TABLE_NAME + " (WSG84_MINY);");
		        	stmt.executeUpdate("CREATE INDEX WSG84_MAXY ON " + TABLE_NAME + " (WSG84_MAXY);");        	
		        	stmt.executeUpdate("CREATE INDEX WSG84_CENTROID_X ON " + TABLE_NAME + " (WSG84_CENTROID_X);");
		        	stmt.executeUpdate("CREATE INDEX WSG84_CENTROID_Y ON " + TABLE_NAME + " (WSG84_CENTROID_Y);");
		        	//AREA ?
		        	stmt.close();        	
		            if (LOG.isDebugEnabled()) 
		                LOG.debug("Created GML index: " + getDataDir() + "/" + db_file_name_prefix);  
	        	} else {
	        		throw new SQLException("2 tables with the same name ?"); 
	        	}
			} finally {
				if (rs != null)
					rs.close();    				
	    	}        
    	} catch (SQLException e) {
    		LOG.error(e);
    		this.conn = null;
    	}
    }
     
}
