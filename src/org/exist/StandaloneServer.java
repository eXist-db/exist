/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.avalon.excalibur.cli.CLArgsParser;
import org.apache.avalon.excalibur.cli.CLOption;
import org.apache.avalon.excalibur.cli.CLOptionDescriptor;
import org.apache.avalon.excalibur.cli.CLUtil;
import org.apache.xmlrpc.WebServer;
import org.apache.xmlrpc.XmlRpc;
import org.apache.xmlrpc.XmlRpcException;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.xmldb.ShutdownListener;
import org.exist.xmlrpc.AuthenticatedHandler;
import org.mortbay.http.HttpContext;
import org.mortbay.http.HttpServer;
import org.mortbay.http.SocketListener;
import org.mortbay.http.handler.NotFoundHandler;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.util.MultiException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;

/**
 * Starts eXist in standalone server mode. In this mode, only the XMLRPC, REST and WebDAV
 * interfaces are provided. By default, the XMLRPC interface runs on port 8081. A minimal Jetty
 * webserver configuration is used for the REST and WebDAV interfaces. The REST interface
 * is accessible on <a href="http://localhost:8088">http://localhost:8088</a> by default. The
 * WebDAV server uses the URL <a href="http://localhost:8088/webdav/db">
 * http://localhost:8088/webdav/db</a> for the database root collection. 
 * 
 * @author wolf
 */
public class StandaloneServer {

    //  command-line options
    private static final String PROPERTY_FILE = "server.properties";
	private final static int HELP_OPT = 'h';
    private final static int DEBUG_OPT = 'd';
    private final static int HTTP_PORT_OPT = 'p';
    private final static int XMLRPC_PORT_OPT = 'x';
    private final static int THREADS_OPT = 't';
    
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
    
    private static Properties DEFAULT_PROPERTIES = new Properties();
    static {
    	DEFAULT_PROPERTIES.setProperty("webdav.enabled", "yes");
    	DEFAULT_PROPERTIES.setProperty("rest.enabled", "yes");
    	DEFAULT_PROPERTIES.setProperty("xmlrpc.enabled", "yes");
    	DEFAULT_PROPERTIES.setProperty("webdav.authentication", "basic");
    	DEFAULT_PROPERTIES.setProperty("rest.form.encoding", "UTF-8");
    	DEFAULT_PROPERTIES.setProperty("rest.container.encoding", "UTF-8");
    }
    
    private WebServer webServer;
    private HttpServer httpServer;
    
    public StandaloneServer() {
    }
    
    public void run(String[] args) throws Exception {
        printNotice();
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
        String home = System.getProperty( "exist.home" );
        if ( home == null )
            home = System.getProperty( "user.dir" );
        System.out.println( "Loading configuration from " + home +
            File.separatorChar + "conf.xml" );
        Configuration config = new Configuration( "conf.xml", home );
        BrokerPool.configure( 1, threads, config );
        BrokerPool.getInstance().registerShutdownListener(new ShutdownListenerImpl());
        initXMLDB();
        
        Properties props = loadProperties(home);
        
        if(props.getProperty("xmlrpc.enabled").equalsIgnoreCase("yes"))
        	startRpcServer(rpcPort, config);
        
        startHTTPServer(httpPort, props);
        
        System.out.println("\nServer launched ...");
        System.out.println("Installed services:");
        System.out.println("-----------------------------------------------");
        if (props.getProperty("rest.enabled").equalsIgnoreCase("yes"))
        	System.out.println("REST servlet:\tlocalhost:" + httpPort);
        if (props.getProperty("webdav.enabled").equalsIgnoreCase("yes"))
        	System.out.println("WebDAV:\t\tlocalhost:" + httpPort + "/webdav");
        if (props.getProperty("xmlrpc.enabled").equalsIgnoreCase("yes"))
        	System.out.println("XMLRPC:\t\tlocalhost:" + rpcPort);
    }

    /**
	 * @param home
	 * @return
     * @throws IOException 
	 */
	private Properties loadProperties(String home) throws IOException {
		Properties properties = new Properties(DEFAULT_PROPERTIES);
		File propFile;
		if (home == null)
			propFile = new File(PROPERTY_FILE);
		else
			propFile = new File(home
					+ System.getProperty("file.separator", "/")
					+ PROPERTY_FILE);

		InputStream pin;
		if (propFile.canRead())
			pin = new FileInputStream(propFile);
		else
			pin = StandaloneServer.class
					.getResourceAsStream(PROPERTY_FILE);

		if (pin != null) {
			System.out.println("Loading properties from " + propFile.getAbsolutePath());
			properties.load(pin);
		}
		return properties;
	}

	/**
     * 
     */
    private void initXMLDB() throws Exception {
        Class clazz = Class.forName("org.exist.xmldb.DatabaseImpl");
        Database database = (Database) clazz.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
    }

    /**
     * Configures a minimal Jetty webserver (no webapplication support,
     * no file system access) and registers the WebDAV and REST servlets.
     * 
     * @param httpPort
     * @throws UnknownHostException
     * @throws IllegalArgumentException
     * @throws MultiException
     */
    private void startHTTPServer(int httpPort, Properties props) 
    throws UnknownHostException, IllegalArgumentException, MultiException {
        httpServer = new HttpServer();
        SocketListener listener = new SocketListener();
        listener.setHost("localhost");
        listener.setPort(httpPort);
        listener.setMinThreads(5);
        listener.setMaxThreads(50);
        httpServer.addListener(listener);
        
        HttpContext context = new HttpContext();
        context.setContextPath("/");
        
        ServletHandler servletHandler = new ServletHandler();
        if (props.getProperty("rest.enabled").equalsIgnoreCase("yes")) {
        	ServletHolder restServlet = 
        		servletHandler.addServlet("EXistServlet", "/*", "org.exist.http.servlets.EXistServlet");
        	restServlet.setInitParameter("form-encoding", props.getProperty("rest.form.encoding"));
        	restServlet.setInitParameter("container-encoding", props.getProperty("rest.container.encoding"));
        }
        if (props.getProperty("webdav.enabled").equalsIgnoreCase("yes")) {
        	ServletHolder davServlet =
        		servletHandler.addServlet("WebDAV", "/webdav/*", "org.exist.http.servlets.WebDAVServlet");
        	davServlet.setInitParameter("authentication", props.getProperty("webdav.authentication"));
        }
        
        context.addHandler(servletHandler);
        context.addHandler(new NotFoundHandler());
        httpServer.addContext(context);
        
        httpServer.start();
    }

    /**
     * @param rpcPort
     * @param config
     * @throws XmlRpcException
     */
    private void startRpcServer(int rpcPort, Configuration config) throws XmlRpcException {
        System.out.println( "starting XMLRPC listener at port " + rpcPort );
        XmlRpc.setEncoding( "UTF-8" );
        webServer = new WebServer( rpcPort );
        AuthenticatedHandler handler = new AuthenticatedHandler( config );
        webServer.addHandler( "$default", handler );
        webServer.start();
    }
    
    private static void printHelp() {
        System.out.println("Usage: java " + StandaloneServer.class.getName() + " [options]");
        System.out.println(CLUtil.describeOptions(OPTIONS).toString());
    }
    
    public static void printNotice() {
        System.out.println("eXist version 1.0, Copyright (C) 2004 Wolfgang Meier");
        System.out.println("eXist comes with ABSOLUTELY NO WARRANTY.");
        System.out.println("This is free software, and you are welcome to "
                + "redistribute it\nunder certain conditions; "
                + "for details read the license file.\n");
    }
    
    public void shutdown() {
		BrokerPool.stopAll(false);
	}
    
    class ShutdownListenerImpl implements ShutdownListener {

        public void shutdown(String dbname, int remainingInstances) {
            if(remainingInstances == 0) {
                // give the server a 1s chance to complete pending requests
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    public void run() {
                        System.out.println("killing threads ...");
                        try {
                            httpServer.stop();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        webServer.shutdown();
                        System.exit(0);
                    }
                }, 1000);
            }
        }
    }
    
    public static void main(String[] args) {
        StandaloneServer server = new StandaloneServer();
        try {
            server.run(args);
        } catch (Exception e) {
            System.err.println("An exception occurred while launching the server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
