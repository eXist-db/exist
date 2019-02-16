package org.exist.http.servlets;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;

// created    17. Mai 2002
/**
 *  Servlet to configure eXist. Use this servlet in a web 
 * application to launch the database at startup.
 * 
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 */

@Deprecated
public class DatabaseAdminServlet extends AbstractExistHttpServlet {

    private static final long serialVersionUID = 866427121174932091L;

    private final static Logger LOG = LogManager.getLogger(DatabaseAdminServlet.class);
    

    protected Configuration configuration = null;

    @Override
    public void doGet( HttpServletRequest request,
                       HttpServletResponse response )
         throws ServletException, IOException {
        final PrintStream output = new PrintStream( response.getOutputStream() );
        response.setContentType( "text/html" );
        response.addHeader( "pragma", "no-cache" );
        response.addHeader( "Cache-Control", "no-cache" );

        output.println( "<h1>eXist Database Server Status</h1>" );

        final String action = request.getParameter( "action" );
        try {
            if ( action != null ) {
                if ( action.equalsIgnoreCase( "start" ) ) {
                    if ( !BrokerPool.isConfigured() ) {
                        BrokerPool.configure( 1, 5, configuration );
                        output.println( "<p>Server has been started...</p>" );
                    }
                    else
                        {output.println( "<p>Server is already running.</p>" );}
                }
                else if ( action.equalsIgnoreCase( "shutdown" ) ) {
                    if ( BrokerPool.isConfigured() ) {
                        BrokerPool.stopAll(false);
                        output.println( "<p>Server has been shut down...</p>" );
                    }
                    else
                        {output.println( "<p>Server is not running ...</p>" );}
                }
            }

            if ( !BrokerPool.isConfigured() )
                {output.println( "<p>Server is not running ...</p>" );}
            else {
                output.println( "<p>The database server is running ...</p>" );

                final BrokerPool pool = BrokerPool.getInstance();
                final Configuration conf = pool.getConfiguration();
                output.println( "<table  width=\"80%\"><tr>" +
                    "<th colspan=\"2\" align=\"left\" bgcolor=\"#0086b2\"><b>Status</b></th></tr>" );
                output.println( "<tr><td>Address:</td><td>" + request.getRequestURI() +
                    "</td></tr>" );
                output.println( "<tr><td>Configuration:</td><td>" + conf.getConfigFilePath() + "</td></tr>" );
                output.println( "<tr><td>Data directory:</td><td>" +
                        ((Path) conf.getProperty(BrokerPool.PROPERTY_DATA_DIR)).toAbsolutePath().toString() +
                    "</td></tr>" );
                output.println( "<tr><td>Active instances:</td><td>" +
                    pool.countActiveBrokers() + "</td></tr>" );
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
        } catch ( final EXistException e ) {
            throw new ServletException( e.getMessage() );
        } catch (final DatabaseConfigurationException e) {
            throw new ServletException( e.getMessage() );
        }
    }

    @Override
    public Logger getLog() {
        return LOG;
    }
}