/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2007 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 *  $Id$
 *  
 *  @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
package org.exist.indexing.spatial;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;

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
public abstract class AbstractGMLJDBCIndex extends AbstractIndex {
	
	
	/**
	 * Holds the index ID. Notice that we delegate this task to the abstract JDBC class,
	 * not to the concrete HSQL (or whatever) one. This allows spatial functions to use
	 * the available JDBC index, whatever its underlying engine is.
	 */
	public final static String ID = AbstractGMLJDBCIndex.class.getName();	
	private final static Logger LOG = Logger.getLogger(AbstractGMLJDBCIndex.class);
    /**
     * An IndexWorker "pool"
     */
    protected HashMap workers = new HashMap();
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
    
    public AbstractGMLJDBCIndex() {    	
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
    	Iterator i = workers.values().iterator();
    	while (i.hasNext()) {
    		AbstractGMLJDBCIndexWorker worker = (AbstractGMLJDBCIndexWorker)i.next();
    		//Flush any pending stuff 
			worker.flush();
			//Reset state
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
    	Iterator i = workers.values().iterator();
    	while (i.hasNext()) {
    		AbstractGMLJDBCIndexWorker worker = (AbstractGMLJDBCIndexWorker)i.next();
    		//Flush any pending stuff 
			worker.flush();
			//Reset state
			worker.setDocument(null, StreamListener.UNKNOWN);
    	}
    	removeIndexContent();
        shutdownDatabase();
        deleteDatabase();
    }
    
    public boolean checkIndex(DBBroker broker) {
    	return getWorker(broker).checkIndex(broker);
    } 
    
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
     * @throws DBException
     */
    protected abstract void shutdownDatabase() throws DBException;
    
    /**
     * Deletes the JDBC database that contains the indexed spatial data.
     * 
     * @throws DBException
     */
    protected abstract void deleteDatabase() throws DBException;
    
    /**
     * Deletes the spatial data contained in the JDBC database.
     * 
     * @throws DBException
     */
    protected abstract void removeIndexContent() throws DBException;
    
    /**
     * Convenience method that can be used by the IndexWorker to acquire a connection 
     * to the JDBC database that contains the indexed spatial data.
     * 
     * @param broker the broker that will use th connection
     * @return the connection
     */
    protected abstract Connection acquireConnection(DBBroker broker) throws SQLException;
    
    /**
     * Convenience method that can be used by the IndexWorker to release a connection 
     * to the JDBC database that contains the indexed spatial data. This connection should have been
     * previously acquired by {@link org.exist.indexing.spatial.AbstractGMLJDBCIndex#acquireConnection(DBBroker)} 
     * 
     * @param broker the broker that will use th connection
     * 
     */    
    protected abstract void releaseConnection(DBBroker broker) throws SQLException;
}
