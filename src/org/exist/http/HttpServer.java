
package org.exist.http;

/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 *  $Id$
 */
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Stack;

import org.apache.log4j.Category;
import org.exist.EXistException;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    22 May 2002
 */
public class HttpServer extends Thread {

    protected static int port = 8080;
    protected static String basedir = null;
    protected Configuration config = null;
    protected ConnectionPool pool;
    //protected static StyleSheetCache styles = new HttpServer.StyleSheetCache();
    protected static Category LOG = Category.getInstance( "exist.http" );
	protected boolean stop = false;

    /**
     *  Constructor for the HttpServer object
     *
     *@param  conf                Description of the Parameter
     *@exception  EXistException  Description of the Exception
     */
    public HttpServer( Configuration conf ) throws EXistException {
        this( conf, 8088 );
    }


    /**
     *  Constructor for the HttpServer object
     *
     *@param  confFile                            Description of the Parameter
     *@exception  EXistException                  Description of the Exception
     *@exception  DatabaseConfigurationException  Description of the Exception
     */
    public HttpServer( String confFile ) throws EXistException, DatabaseConfigurationException {
        this( new Configuration( confFile ), 8088 );
    }


    /**
     *  Constructor for the HttpServer object
     *
     *@param  conf                Description of the Parameter
     *@param  port                Description of the Parameter
     *@exception  EXistException  Description of the Exception
     */
    public HttpServer( Configuration conf, int port ) throws EXistException {
        this( conf, port, 1, 5 );
    }


    /**
     *  Constructor for the HttpServer object
     *
     *@param  conf                Description of the Parameter
     *@param  port                Description of the Parameter
     *@param  min                 Description of the Parameter
     *@param  max                 Description of the Parameter
     *@exception  EXistException  Description of the Exception
     */
    public HttpServer( Configuration conf, int port, int min, int max ) throws EXistException {
        this.config = conf;
        this.port = port;
        if ( config.getProperty( "port" ) != null )
            port = ( (Integer) config.getProperty( "port" ) ).intValue();
        if ( ( basedir = (String) config.getProperty( "basedir" ) ) == null )
            basedir = ".";
        pool = new ConnectionPool( min, max, config );
    }


    /**  Main processing method for the HttpServer object */
    public void run() {
        ServerSocket sock;
        try {
            sock = new ServerSocket( port );
            sock.setSoTimeout( 500 );
            LOG.debug( "listening at port " + port );
            while ( !stop )
                try {
                    Socket s = sock.accept();
                    LOG.info( "connection from " +
                            s.getInetAddress().getHostName() );
                    HttpServerConnection con = pool.get();
                    con.process( s );
                } catch ( InterruptedIOException ie ) {
                }

        } catch ( IOException io ) {
            LOG.error( io );
        } catch ( SecurityException sec ) {
            LOG.error( sec );
        }
    }

	public void shutdown() {
		stop = true;
		pool.shutdown();
	}
	
    class ConnectionPool {

        protected Stack pool = new Stack();
        protected ArrayList threads = new ArrayList();
        protected int min = 0;
        protected int max = 1;
        protected int connections = 0;
        protected Configuration conf;


        /**
         *  Constructor for the ConnectionPool object
         *
         *@param  min   Description of the Parameter
         *@param  max   Description of the Parameter
         *@param  conf  Description of the Parameter
         */
        public ConnectionPool( int min, int max, Configuration conf ) {
            this.min = min;
            this.max = max;
            this.conf = conf;
            initialize();
        }


        /**  Description of the Method */
        protected void initialize() {
            HttpServerConnection con;
            for ( int i = 0; i < min; i++ ) {
                con = createConnection();
                pool.push( con );
            }
        }


        /**
         *  Description of the Method
         *
         *@return    Description of the Return Value
         */
        protected HttpServerConnection createConnection() {
            //DBBroker broker = BrokerFactory.getInstance(conf);
            HttpServerConnection con = new HttpServerConnection( conf, this );
            threads.add( con );
            con.start();
            connections++;
            return con;
        }


        /**
         *  Description of the Method
         *
         *@return    Description of the Return Value
         */
        public synchronized HttpServerConnection get() {
            if ( pool.isEmpty() ) {
                if ( connections < max )
                    return createConnection();
                else
                    while ( pool.isEmpty() ) {
                        HttpServer.LOG.debug( "waiting for connection to become available" );
                        try {
                            this.wait();
                        } catch ( InterruptedException e ) {}
                    }

            }
            HttpServerConnection con = (HttpServerConnection) pool.pop();
            this.notifyAll();
            return con;
        }


        /**
         *  Description of the Method
         *
         *@param  con  Description of the Parameter
         */
        public synchronized void release( HttpServerConnection con ) {
            pool.push( con );
            this.notifyAll();
        }


        /**  Description of the Method */
        public synchronized void shutdown() {
            for ( Iterator i = threads.iterator(); i.hasNext();  )
                ( (HttpServerConnection) i.next() ).terminate();
            while ( pool.size() < connections )
                try {
                    this.wait();
                } catch ( InterruptedException e ) {}

        }
    }


    private static void printNotice() {
        System.out.println( "eXist version 0.9.2, Copyright (C) 2001 Wolfgang M. Meier" );
        System.out.println( "eXist comes with ABSOLUTELY NO WARRANTY." );
        System.out.println(
                "This is free software, and you are welcome to " +
                "redistribute it\nunder certain conditions; " + "for details read the license file.\n" );
        System.out.println( "\nType 'q[return]' to shutdown the server. Otherwise data may be lost.\n\n" );
    }


    /**
     *  Description of the Method
     *
     *@param  args  Description of the Parameter
     */
    public static void main( String args[] ) {
        printNotice();
        try {
            HttpServer http = new HttpServer( "conf.xml" );
            http.start();
        } catch ( Exception e ) {
            System.out.println( e );
        }
    }
}


