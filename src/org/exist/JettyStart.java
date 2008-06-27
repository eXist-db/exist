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

import org.exist.cluster.ClusterComunication;
import org.exist.cluster.ClusterException;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.util.SingleInstanceConfiguration;
import org.exist.validation.XmlLibraryChecker;
import org.exist.xmldb.DatabaseImpl;
import org.exist.xmldb.ShutdownListener;
import org.mortbay.http.HttpContext;
import org.mortbay.http.HttpListener;
import org.mortbay.jetty.Server;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;

import java.util.Timer;
import java.util.TimerTask;

/**
 * This class provides a main method to start Jetty with eXist. It registers shutdown
 * handlers to cleanly shut down the database and the webserver.
 * If database is NATIVE-CLUSTER, Clustercomunication is configured and started.
 * 
 * @author wolf
 */
public class JettyStart {

	public static void main(String[] args) {
		JettyStart start = new JettyStart();
		start.run(args);
	}
	
        public JettyStart() {
            // Additional checks XML libs @@@@
    		XmlLibraryChecker.check();
        }
	
	public void  run(String[] args) {
		if (args.length == 0) {
			System.out.println("No configuration file specified!");
			return;
		}
		
		String shutdownHookOption = System.getProperty("exist.register-shutdown-hook", "true");
		boolean registerShutdownHook = shutdownHookOption.equals("true");
		
		// configure database
		System.out.println("Configuring eXist from " + SingleInstanceConfiguration.getPath());
		try {
			// we register our own shutdown hook
			BrokerPool.setRegisterShutdownHook(false);
			
			// configure the database instance
			SingleInstanceConfiguration config;
            if (args.length == 2)
                config = new SingleInstanceConfiguration(args[1]);
            else
                config = new SingleInstanceConfiguration();
            
            BrokerPool.configure(1, 5, config);
			
			// register the XMLDB driver
			Database xmldb = new DatabaseImpl();
			xmldb.setProperty("create-database", "false");
			DatabaseManager.registerDatabase(xmldb);

            configureCluster(config);

        } catch (Exception e) {
			System.err.println("configuration error: " + e.getMessage());
			e.printStackTrace();
			return;
		}

		// start Jetty
		final Server server;
		try {
			server = new Server(args[0]);
            BrokerPool.getInstance().registerShutdownListener(new ShutdownListenerImpl(server));
			server.start();

            HttpListener[] listeners = server.getListeners();
            int port = 8080;
            if (listeners.length > 0)
                port = listeners[0].getPort();
            HttpContext[] contexts = server.getContexts();
            System.out.println("-----------------------------------------------------");
            System.out.println("Server has started on port " + port + ". Configured contexts:");
            for (int i = 0; i < contexts.length; i++) {
                System.out.println("http://localhost:" + port + contexts[i].getContextPath());                
            }
            System.out.println("-----------------------------------------------------");
            
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
			System.err.println("Database shutdown: stopping server in 1sec ...");
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
