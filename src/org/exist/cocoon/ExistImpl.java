package org.exist.cocoon;

// avalon classes
import org.apache.avalon.framework.activity.Startable;
import org.apache.avalon.framework.context.Context;
import org.apache.avalon.framework.context.ContextException;
import org.apache.avalon.framework.context.Contextualizable;
import org.apache.avalon.framework.logger.AbstractLoggable;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.avalon.framework.parameters.Parameterizable;
import org.apache.avalon.framework.thread.ThreadSafe;
import org.apache.cocoon.Constants;

// eXist classes
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.EXistException;
import org.exist.http.HttpServer;
import org.exist.xmlrpc.RpcServer;
import org.apache.xmlrpc.XmlRpc;
import org.apache.xmlrpc.WebServer;

// other java classes
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

/**
 *  Überschrift: eXist XML-Datenbankerweitrung um Lokle Interfaces Beschreibung:
 *  Copyright: Copyright (c) 2002 Organisation: C-Ware IT-Service
 *
 *@author     Christofer Dutz
 *@created    14. Februar 2002
 *@version    1.0
 */

public class ExistImpl extends AbstractLoggable implements Exist,
        Parameterizable,
        Contextualizable,
        ThreadSafe,
        Runnable,
        Startable {

    private int httpPort = 0;
    private int rpcPort = 0;
    private int threads = 5;
    private String home = System.getProperty("user.dir");


    /**
     *  Initialize the ServerImpl. A few options can be used :
     *  <UL>
     *    <LI> http-port = port where the http-server is listening</LI>
     *    <LI> rpc-port = port where the rpc-server is listening</LI>
     *  </UL>
     *
     *
     *@param  params  Description of the Parameter
     */
    public void parameterize(Parameters params) {
        if (this.getLogger().isDebugEnabled()) {
            this.getLogger().debug("Parameterize ExistImpl");
        }
        this.httpPort = params.getParameterAsInteger("http-port", 0);
        this.rpcPort = params.getParameterAsInteger("rpc-port", 0);
        if (this.getLogger().isDebugEnabled()) {
            this.getLogger().debug("Configure ExistImpl" +
                    " with " + ((this.httpPort != 0) ? "http-port : " + this.httpPort : "disabed http server") +
                    " with " + ((this.rpcPort != 0) ? "rpc-port : " + this.rpcPort : "disabed rpc server"));
        }
    }


    /**
     *  Contextualize this class
     *
     *@param  context               Description of the Parameter
     *@exception  ContextException  Description of the Exception
     */
    public void contextualize(Context context) throws ContextException {
        //if (this.getLogger().isDebugEnabled()) {
            this.getLogger().debug("Contextualize ExistImpl");
	    //}
        org.apache.cocoon.environment.Context ctx =
                (org.apache.cocoon.environment.Context) context.get(Constants.CONTEXT_ENVIRONMENT_CONTEXT);
        try {
            String dbPath = new File(ctx.getRealPath("/WEB-INF/xmldb")).getCanonicalPath();
            this.home = dbPath;
            if (this.getLogger().isDebugEnabled()) {
                this.getLogger().debug("database is : " + dbPath);
            }
        } catch (MalformedURLException mue) {
            this.getLogger().debug("MalformedURLException - Could not get database directory ", mue);
        } catch (IOException ioe) {
            this.getLogger().debug("IOException - Could not get database directory ", ioe);
        }
    }


    /**
     *  Start the server
     */
    public void start() {
        if (this.getLogger().isDebugEnabled()) {
            this.getLogger().debug("Start ExistImpl");
        }
        try {
            String pathSep = System.getProperty("file.separator", "/");
            if (this.getLogger().isDebugEnabled()) {
                this.getLogger().debug("Creating Configuration");
            }
            Configuration config = new Configuration("conf.xml", home);

            // eventualy create the data-directory
            File dataDir = new File(home + pathSep + "data");
            if (!dataDir.exists() || !dataDir.isDirectory()) {
                if (this.getLogger().isDebugEnabled()) {
                    this.getLogger().debug("Creating Data-Directory");
                }
                dataDir.mkdir();
            }
            if (this.getLogger().isDebugEnabled()) {
                this.getLogger().debug("Configuring Brokerpool");
            }

            try {
                BrokerPool.configure(1, threads, config);
                if (this.getLogger().isDebugEnabled()) {
                    this.getLogger().debug("Brokerpool up and running");
                }

                if (this.httpPort != 0) {
                    // setup the http-server
                    try {
                        HttpServer http = new HttpServer(config, httpPort, 1, threads);
                        http.start();
                        if (this.getLogger().isDebugEnabled()) {
                            this.getLogger().debug("Exist HTTP-Server started");
                        }
                    } catch (EXistException ee) {
                        this.getLogger().debug("ExistException - Error starting HTTP-Server ", ee);
                    }
                }

                if (this.rpcPort != 0) {
                    // setup the rpc-server
                    try {
                        XmlRpc.setEncoding("UTF-8");
                        WebServer webServer = new WebServer(rpcPort);
                        RpcServer rpcserv = new RpcServer(config);
                        webServer.addHandler("$default", rpcserv);
                        if (this.getLogger().isDebugEnabled()) {
                            this.getLogger().debug("Exist RPC-Server started");
                        }
                    } catch (IOException ioe) {
                        this.getLogger().debug("IOException - Error starting RPC-Server ", ioe);
                    }
                }

                if (this.getLogger().isDebugEnabled()) {
                    this.getLogger().debug("Exist started");
                }
            } catch (EXistException ee) {
                this.getLogger().debug("ExistException - Error initializing BrokerPool ", ee);
            }
        } catch (Exception e) {
            this.getLogger().debug("Exception - Error starting Exist ", e);
        }
    }


    /**
     *  Stop the server
     */
    public void stop() {
        if (this.getLogger().isDebugEnabled()) {
            this.getLogger().debug("Stop ExistImpl");
        }
        try {
            if (BrokerPool.getInstance() != null) {
                BrokerPool.getInstance().shutdown();
            }
        } catch (EXistException ee) {
            this.getLogger().error("ExistException - Error stopping Exist ", ee);
        }
    }


    /**
     *  Main processing method for the ServerImpl object
     */
    public void run() {
        if (this.getLogger().isDebugEnabled()) {
            this.getLogger().debug("Run ExistImpl");
        }
        this.start();
    }


    /**
     *  The main program for the ExistImpl class
     *
     *@param  args  The command line arguments
     */
    public static void main(String[] args) { }
}
