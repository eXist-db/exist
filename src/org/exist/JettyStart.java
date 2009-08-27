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
package org.exist;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Observer;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;
import org.exist.cluster.ClusterComunication;
import org.exist.cluster.ClusterException;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.util.SingleInstanceConfiguration;
import org.exist.validation.XmlLibraryChecker;
import org.exist.xmldb.DatabaseImpl;
import org.exist.xmldb.ShutdownListener;
import org.exist.xquery.functions.system.GetVersion;
import org.mortbay.http.HttpContext;
import org.mortbay.http.HttpListener;
import org.mortbay.jetty.Server;
import org.mortbay.util.MultiException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;

/**
 * This class provides a main method to start Jetty with eXist. It registers shutdown
 * handlers to cleanly shut down the database and the webserver.
 * If database is NATIVE-CLUSTER, Clustercomunication is configured and started.
 * 
 * @author wolf
 */
public class JettyStart {
	
	protected static final Logger logger = Logger.getLogger(JettyStart.class);

	public static void main(String[] args) {
		JettyStart start = new JettyStart();
		start.run(args, null);
	}
	
        public JettyStart() {
            // Additional checks XML libs @@@@
    		XmlLibraryChecker.check();
        }
	
	public void run(String[] args, Observer observer) {
		if (args.length == 0) {
			logger.info("No configuration file specified!");
			return;
		}
		
		String shutdownHookOption = System.getProperty("exist.register-shutdown-hook", "true");
		boolean registerShutdownHook = shutdownHookOption.equals("true");
		
		Properties sysProperties = new Properties();
		try
		{
			sysProperties.load(GetVersion.class.getClassLoader().getResourceAsStream("org/exist/system.properties"));
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		
		
		
		// configure database
		logger.info("Configuring eXist from " + SingleInstanceConfiguration.getPath());
        logger.info("");
        logger.info("Running with Java "+
                System.getProperty("java.version", "(unknown java.version)") + " [" +
                System.getProperty("java.vendor", "(unknown java.vendor)") + " (" +
                System.getProperty("java.vm.name", "(unknown java.vm.name)") + ") in " +
                System.getProperty("java.home", "(unknown java.home)") +"]");
        logger.info("");
        
        String msg;
        msg = "[eXist Version : " + sysProperties.get("product-version") + "]";
        logger.info(msg);
        msg = "[eXist Build : " + sysProperties.get("product-build") + "]";
        logger.info(msg);
        msg = "[eXist Home : " + System.getProperty("exist.home") + "]";
        logger.info(msg);
        msg = "[SVN Revision : " + sysProperties.get("svn-revision") + "]";
        logger.info(msg);
        msg = "[Operating System : " + 
        		System.getProperty("os.name") +
        		" " +
        		System.getProperty("os.version") +
                " " +
                System.getProperty("os.arch") + 
                "]";
        logger.info(msg);
        
        msg = "[jetty.home : " + System.getProperty("jetty.home") + "]";
        logger.info(msg);
        msg = "[log4j.configuration : " + System.getProperty("log4j.configuration") + "]";
        logger.info(msg);
        
		try {
			// we register our own shutdown hook
			BrokerPool.setRegisterShutdownHook(false);

			// configure the database instance
			SingleInstanceConfiguration config;
            if (args.length == 2)
                config = new SingleInstanceConfiguration(args[1]);
            else
                config = new SingleInstanceConfiguration();

            if (observer != null)
                BrokerPool.registerStatusObserver(observer);
            
            BrokerPool.configure(1, 5, config);
			
			// register the XMLDB driver
			Database xmldb = new DatabaseImpl();
			xmldb.setProperty("create-database", "false");
			DatabaseManager.registerDatabase(xmldb);

            configureCluster(config);

        } catch (Exception e) {
			logger.error("configuration error: ", e);
			e.printStackTrace();
			return;
		}

		// start Jetty
		final Server server;
        int port = 8080;
		try {
			server = new Server(args[0]);
            BrokerPool.getInstance().registerShutdownListener(new ShutdownListenerImpl(server));
			server.start();

            HttpListener[] listeners = server.getListeners();

            StringBuilder ports = new StringBuilder();

            if (listeners.length > 0) {
                for (HttpListener listener : listeners) {
                    port = listener.getPort();
                    ports.append(" "+port);
                }
                
            } else {
                ports.append(" "+port);
            }

            HttpContext[] contexts = server.getContexts();
            logger.info("----------------------------------------------------------------");
            logger.info("eXist-db has started on port" + ports + ". Configured contexts:");
            for (int i = 0; i < contexts.length; i++) {
                logger.info("http://localhost:" + port + contexts[i].getContextPath());                
            }
            logger.info("----------------------------------------------------------------");
            
            if (registerShutdownHook) {
				// register a shutdown hook for the server
				Thread hook = new Thread() {
					public void run() {
						setName("Shutdown");
						BrokerPool.stopAll(true);
						try {
							server.stop();
						} catch (InterruptedException e) {
						}
						try {
							Thread.sleep(1000);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				};
				Runtime.getRuntime().addShutdownHook(hook);
			}
		} catch (MultiException  e) {

            boolean hasBindException=false;
            for(Object t : e.getExceptions()){
                if(t instanceof java.net.BindException){
                    hasBindException=true;
                    logger.info("----------------------------------------------------------");
                    logger.info("ERROR: Could not start jetty, port "
                            + port + " is already in use.   ");
                    logger.info(t.toString());
                    logger.info("----------------------------------------------------------");
                }
            }

            // If it is another error, print stacktrace
            if(!hasBindException){
                e.printStackTrace();
            }

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void shutdown() {
		BrokerPool.stopAll(false);
	}

	/**
	 * This class gets called after the database received a shutdown request.
	 *
	 * @author wolf
	 */
	private static class ShutdownListenerImpl implements ShutdownListener {
		private Server server;

		public ShutdownListenerImpl(Server server) {
			this.server = server;
		}

		public void shutdown(String dbname, int remainingInstances) {
			logger.error("Database shutdown: stopping server in 1sec ...");
			if (remainingInstances == 0) {
				// give the webserver a 1s chance to complete open requests
				Timer timer = new Timer();
				timer.schedule(new TimerTask() {
					public void run() {
						try {
							// stop the server
							server.stop();
                            ClusterComunication cluster = ClusterComunication.getInstance();
                            if(cluster!=null){
                                cluster.stop();
                            }
                        } catch (InterruptedException e) {
							e.printStackTrace();
						}
						System.exit(0);
					}
				}, 1000);
			}
		}
	}

     private void configureCluster(Configuration c) throws ClusterException {
        String database = (String)c.getProperty("database");
        if(! database.equalsIgnoreCase("NATIVE_CLUSTER"))
            return;

        ClusterComunication.configure(c);
    }
}
