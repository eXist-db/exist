/*
 *  eXist xml document repository and xpath implementation
 *  Copyright (C) 2000,  Wolfgang Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
/**
 *  verwaltet einen Pool von Datenbank-Connections. die Klasse sorgt dafuer,
 *  dass eine maximale Zahl von (gleichzeitigen) Datenbankverbindungen
 *  (JDBC-Connection-Objekte) nicht ueberschritten wird. Das Maximum ist
 *  abhaengig von der Oracle-Installation und liegt standardmaessig bei 40-50.
 *  Die verfuegbaren Verbindungen werden an die konkurrierenden Threads
 *  verteilt. Ist keine Verbindung mehr verfuegbar, wird der aufrufende Thread
 *  in eine Warte- schleife geschickt - solange, bis ein anderer Thread sein
 *  Connection-Objekt zurueckgibt. Die beiden zentralen Methoden sind get() und
 *  release(Connection con). Mit get() erhaelt der aufrufende Thread ein
 *  Connection-Objekt, mit release gibt er es wieder zurueck. Man sollte also
 *  nicht vergessen, nach Ende der Transaktionen release aufzurufen. Der
 *  Konstruktor benoetigt die Klasse des JDBC-Treibers als String. Ueber
 *  Reflection wird davon eine Instanz erzeugt und an den DriverManager
 *  uebergeben.
 */
package org.exist.storage;

import java.util.Stack;
import java.util.Iterator;
import java.sql.*;
import java.lang.reflect.*;
import org.apache.log4j.Category;
import org.exist.EXistException;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    17. Juli 2002
 */
public class DBConnectionPool {

    protected Stack pool = new Stack();
    protected int min = 0;
    protected int max = 1;
    protected String uid = null;
    protected String pass = null;
    protected String url = null;
    protected int connections = 0;
    protected boolean autoCommit = true;

    protected static Category LOG =
            Category.getInstance( DBConnectionPool.class.getName() );


    /**
     *  Constructor for the DBConnectionPool object
     *
     *@param  url                 Description of the Parameter
     *@param  uid                 Description of the Parameter
     *@param  pass                Description of the Parameter
     *@param  driver              Description of the Parameter
     *@param  min                 Description of the Parameter
     *@param  max                 Description of the Parameter
     *@exception  EXistException  Description of the Exception
     */
    public DBConnectionPool( String url, String uid, String pass,
            String driver, int min, int max )
             throws EXistException {
        this( url, uid, pass, driver, min, max, true );
    }


    /**
     *  Constructor for the DBConnectionPool object
     *
     *@param  url                 Description of the Parameter
     *@param  uid                 Description of the Parameter
     *@param  pass                Description of the Parameter
     *@param  driver              Description of the Parameter
     *@param  min                 Description of the Parameter
     *@param  max                 Description of the Parameter
     *@param  autoCommit          Description of the Parameter
     *@exception  EXistException  Description of the Exception
     */
    public DBConnectionPool( String url, String uid, String pass,
            String driver, int min, int max, boolean autoCommit )
             throws EXistException {
        try {
            this.autoCommit = autoCommit;
            Class c = Class.forName( driver );
            Constructor construct = c.getConstructor( null );
            DriverManager.registerDriver(
                    (Driver) construct.newInstance( null ) );
            this.min = min;
            this.max = max;
            this.uid = uid;
            this.pass = pass;
            this.url = url;

            initialize();
        } catch ( Exception e ) {
            LOG.debug( e );
            throw new EXistException( "error in initialization of db connection pool" );
        }
    }


    /**
     *  Gets the user attribute of the DBConnectionPool object
     *
     *@return    The user value
     */
    public String getUser() {
        return uid;
    }


    /**
     *  Gets the pass attribute of the DBConnectionPool object
     *
     *@return    The pass value
     */
    public String getPass() {
        return pass;
    }


    /**  Description of the Method */
    public void initialize() {
        Connection con = null;
        for ( int i = 0; i < min; i++ ) {
            con = createConnection();
            if ( con != null ) {
                try {
                    con.setAutoCommit( autoCommit );
                } catch ( SQLException e ) {
                    LOG.warn( e );
                }
                pool.push( con );
            } else {
                throw new RuntimeException( "cannot create new database connection" );
            }
        }
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public Connection createConnection() {
        Connection con = null;
        LOG.debug( "creating database connection ..." );
        try {
            if ( uid == null ) {
                con = DriverManager.getConnection( url );
            } else {
                con = DriverManager.getConnection( url, uid, pass );
            }
            connections++;
            con.setAutoCommit( autoCommit );
        } catch ( Exception e ) {
            e.printStackTrace();
        }
        return con;
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public synchronized Connection get() {
        if ( pool.isEmpty() ) {
            if ( connections < max ) {
                return createConnection();
            } else {
                while ( pool.isEmpty() ) {
                    LOG.debug( "waiting for connection to become available" );
                    try {
                        wait();
                    } catch ( InterruptedException e ) {}
                }
            }
        }
        Connection con = (Connection) pool.pop();
        notifyAll();
        return con;
    }


    /**
     *  Description of the Method
     *
     *@param  con  Description of the Parameter
     */
    public synchronized void release( Connection con ) {
        //LOG.debug("releasing connection");
        pool.push( con );
        notifyAll();
    }


    /**  Description of the Method */
    public synchronized void commit() {
        if ( autoCommit ) {
            return;
        }
        try {
            for ( Iterator i = pool.iterator(); !i.hasNext();  ) {
                ( (Connection) i.next() ).commit();
            }
        } catch ( SQLException e ) {
            LOG.warn( e );
        }
    }


    /**  Description of the Method */
    public synchronized void closeAll() {
        try {
            for ( Iterator i = pool.iterator(); !i.hasNext();  ) {
                ( (Connection) i.next() ).close();
            }
        } catch ( SQLException e ) {
        }
    }


    /**  Description of the Method */
    public void finalize() {
        notifyAll();
        closeAll();
    }
}

