package org.exist.http.servlets;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;

// created    17. Mai 2002
/**
 *  Servlet to configure eXist. Use this servlet in a web 
 * application to launch the database at startup.
 * 
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 */
public class DatabaseAdminServlet extends HttpServlet {
    protected String confFile;

    protected Configuration configuration = null;
    protected String dbHome;

    public void doGet( HttpServletRequest request,
                       HttpServletResponse response )
         throws ServletException, IOException {
        PrintStream output = new PrintStream( response.getOutputStream() );
        response.setContentType( "text/html" );
        response.addHeader( "pragma", "no-cache" );
        response.addHeader( "Cache-Control", "no-cache" );

        output.println( "<h1>eXist Database Server Status</h1>" );

        String action = request.getParameter( "action" );
        try {
            if ( action != null ) {
                if ( action.equalsIgnoreCase( "start" ) ) {
                    if ( !BrokerPool.isConfigured() ) {
                        BrokerPool.configure( 1, 5, configuration );
                        output.println( "<p>Server has been started...</p>" );
                    }
                    else
                        output.println( "<p>Server is already running.</p>" );
                }
                else if ( action.equalsIgnoreCase( "shutdown" ) ) {
                    if ( BrokerPool.isConfigured() ) {
                        BrokerPool.stopAll(false);
                        output.println( "<p>Server has been shut down...</p>" );
                    }
                    else
                        output.println( "<p>Server is not running ...</p>" );
                }
            }

            if ( !BrokerPool.isConfigured() )
                output.println( "<p>Server is not running ...</p>" );
            else {
                output.println( "<p>The database server is running ...</p>" );

                BrokerPool pool = BrokerPool.getInstance();
                Configuration conf = pool.getConfiguration();
                output.println( "<table  width=\"80%\"><tr>" +
                    "<th colspan=\"2\" align=\"left\" bgcolor=\"#0086b2\"><b>Status</b></th></tr>" );
                output.println( "<tr><td>Address:</td><td>" + request.getRequestURI() +
                    "</td></tr>" );
                output.println( "<tr><td>Configuration:</td><td>" + conf.getConfigFilePath() + "</td></tr>" );
                output.println( "<tr><td>Data directory:</td><td>" +
                    (String) conf.getProperty(BrokerPool.PROPERTY_DATA_DIR) +
                    "</td></tr>" );
                output.println( "<tr><td>Active instances:</td><td>" +
                    pool.active() + "</td></tr>" );
                output.println( "<tr><td>Available instances:</td><td>" +
                    pool.available() + "</td></tr>" );
                output.println( "</table>" );
            }
            output.print( "<p><form action=\"" );
            output.print( response.encodeURL( request.getRequestURI() ) );
            output.println( "\" method=\"GET\">" );
            output.print( "<input type=\"submit\" name=\"action\" value=\"start\">" );
            output.print( "<input type=\"submit\" name=\"action\" value=\"shutdown\">" );
            output.println( "</form></p>" );
            output.flush();
        } catch ( EXistException e ) {
            throw new ServletException( e.getMessage() );
        } catch (DatabaseConfigurationException e) {
            throw new ServletException( e.getMessage() );
        }
    }

	/**
	 * Initialize the servlet. Tries to determine the base directory
	 * for eXist (usually WEB-INF) and the location of the configuration
	 * file. If a valid configuration is found, the database is launched
	 * by configuring the pool of database brokers.
	 * 
	 * In web.xml, add the option 
	 * 
	 * <load-on-startup>2</load-on-startup>
	 * 
	 * for the servlet to be loaded on server startup.
	 * 
	 * @see javax.servlet.Servlet#init(ServletConfig)
	 */
    public void init( ServletConfig config ) throws ServletException {
        super.init( config );
        if(BrokerPool.isConfigured()) {
        	this.log("database already started. Giving up.");
        	return;
        }
        String pathSep = File.separator;
        try {
            confFile = config.getInitParameter( "configuration" );
            dbHome = config.getInitParameter( "basedir" );
            String start = config.getInitParameter( "start" );

            if ( confFile == null )
                confFile = "conf.xml";
            dbHome = ( dbHome == null ) ?
                config.getServletContext().getRealPath( "." ) :
                config.getServletContext().getRealPath( dbHome );
            this.log( "DatabaseAdminServlet: exist.home=" + dbHome );
            File f = new File( dbHome + pathSep + confFile );
            this.log("reading configuration from " + f.getAbsolutePath());
            if ( !f.canRead() )
                throw new ServletException( "configuration file " +
                    confFile + " not found or not readable" );
            configuration =
                new Configuration( confFile, dbHome );
            if ( start != null && start.equals( "true" ) )
                startup();
        } catch ( DatabaseConfigurationException dce ) {
            throw new ServletException( "error in database configuration: " +
                dce.getMessage() );
        }
    }


    public void destroy() {
    	this.log("starting database shutdown ...");
	    BrokerPool.stopAll(false);
    }

    private void startup() throws ServletException {
        if ( configuration == null )
            throw new ServletException( "database has not been " +
                "configured" );
        this.log("configuring eXist instance");
        try {
            if ( !BrokerPool.isConfigured() )
                BrokerPool.configure( 1, 5, configuration );
        } catch ( EXistException e ) {
            throw new ServletException( e.getMessage() );
        } catch (DatabaseConfigurationException e) {
            throw new ServletException( e.getMessage() );
        }
        try {
			this.log("registering XMLDB driver");
			String driver = "org.exist.xmldb.DatabaseImpl";
			Class clazz = Class.forName(driver);
			Database database = (Database)clazz.newInstance();
			database.setProperty("auto-create", "true");
			DatabaseManager.registerDatabase(database);
		} catch (ClassNotFoundException e) {
			this.log("ERROR", e);
		} catch (InstantiationException e) {
			this.log("ERROR", e);
		} catch (IllegalAccessException e) {
			this.log("ERROR", e);
		} catch (XMLDBException e) {
			this.log("ERROR", e);
		}
    }
}


