
package org.exist;

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

import org.apache.xmlrpc.*;
import org.exist.http.*;
import org.exist.storage.*;
import org.exist.util.*;
import org.exist.xmldb.*;
import org.exist.xmlrpc.*;
import org.apache.avalon.excalibur.cli.*;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 *  Main class to start the stand-alone server. By default,
 *  an XML-RPC listener is started at port 8081. The HTTP server
 *  will be available at port 8088. Use command-line options to
 *  change this.
 *  
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    22 May 2002
 */
public class Server {

	// command-line options
	private final static int HELP_OPT = 'h';
	private final static int DEBUG_OPT = 'd';
	private final static int HTTP_PORT_OPT = 'p';
	private final static int XMLRPC_PORT_OPT = 'x';
	private final static int THREADS_OPT = 't';

	private static WebServer webServer;
	private static HttpServer http;
	
	private final static CLOptionDescriptor OPTIONS[] = new CLOptionDescriptor[] {
		new CLOptionDescriptor( "help", CLOptionDescriptor.ARGUMENT_DISALLOWED,
			HELP_OPT, "print help on command line options and exit." ),
		new CLOptionDescriptor( "debug", CLOptionDescriptor.ARGUMENT_DISALLOWED,
			DEBUG_OPT, "debug XMLRPC calls." ),
		new CLOptionDescriptor( "http-port", CLOptionDescriptor.ARGUMENT_REQUIRED,
			HTTP_PORT_OPT, "set HTTP port." ),
		new CLOptionDescriptor( "xmlrpc-port", CLOptionDescriptor.ARGUMENT_REQUIRED,
			XMLRPC_PORT_OPT, "set XMLRPC port." ),
		new CLOptionDescriptor( "threads", CLOptionDescriptor.ARGUMENT_REQUIRED,
			THREADS_OPT, "set max. number of parallel threads allowed by the db." )
	};
	
    /**
     *  Main method to start the stand-alone server.
     *
     *@param  args           Description of the Parameter
     *@exception  Exception  Description of the Exception
     */
    public static void main( String args[] ) throws Exception {
        InteractiveClient.printNotice();
		CLArgsParser optParser = new CLArgsParser( args, OPTIONS );
		if(optParser.getErrorString() != null) {
			System.err.println( "ERROR: " + optParser.getErrorString());
			return;
		}
		List opt = optParser.getArguments();
		int size = opt.size();
		CLOption option;
        int httpPort = 8088;
        int rpcPort = 8081;
        int threads = 5;
		for(int i = 0; i < size; i++) {
			option = (CLOption)opt.get(i);
			switch(option.getId()) {
				case HELP_OPT :
					printHelp();
					return;
				case DEBUG_OPT :
					XmlRpc.setDebug(true);
					break;
				case HTTP_PORT_OPT :
					try {
						httpPort = Integer.parseInt( option.getArgument() );
					} catch( NumberFormatException e ) {
						System.err.println("option -p requires a numeric argument");
						return;
					}
					break;
				case XMLRPC_PORT_OPT :
					try {
						rpcPort = Integer.parseInt( option.getArgument() );
					} catch( NumberFormatException e ) {
						System.err.println("option -x requires a numeric argument");
						return;
					}
					break;
				case THREADS_OPT :
					try {
						threads = Integer.parseInt( option.getArgument() );
					} catch( NumberFormatException e ) {
						System.err.println("option -t requires a numeric argument");
						return;
					}
					break;
			}
		}
		
        String pathSep = System.getProperty( "file.separator", "/" );
        String home = System.getProperty( "exist.home" );
        if ( home == null )
            home = System.getProperty( "user.dir" );
        System.out.println( "loading configuration from " + home +
            pathSep + "conf.xml" );
        Configuration config = new Configuration( "conf.xml", home );
        BrokerPool.configure( 1, threads, config );
        BrokerPool.getInstance().registerShutdownListener(new ShutdownListenerImpl());
        
        System.out.println( "starting HTTP listener at port " + httpPort );
        http = new HttpServer( config, httpPort, 1, threads );
        http.start();
        System.out.println( "starting XMLRPC listener at port " + rpcPort );
        XmlRpc.setEncoding( "UTF-8" );
        webServer = new WebServer( rpcPort );
        AuthenticatedHandler handler = new AuthenticatedHandler( config );
        webServer.addHandler( "$default", handler );
        webServer.start();
        System.err.println( "waiting for connections ..." );
    }

    private static void printHelp() {
		System.out.println("Usage: java " + Server.class.getName() + " [options]");
		System.out.println(CLUtil.describeOptions(OPTIONS).toString());
    }
    
    static class ShutdownListenerImpl implements ShutdownListener {

		public void shutdown(String dbname, int remainingInstances) {
			if(remainingInstances == 0) {
				// give the server a 1s chance to complete pending requests
				Timer timer = new Timer();
				timer.schedule(new TimerTask() {
					public void run() {
						System.out.println("killing threads ...");
						http.shutdown();
						http.interrupt();
						webServer.shutdown();
						System.exit(0);
					}
				}, 1000);
    		}
		}
    }
}

