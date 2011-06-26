/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.jetty;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.SocketException;
import java.util.Observer;

import org.exist.cluster.ClusterComunication;
import org.exist.cluster.ClusterException;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.util.SingleInstanceConfiguration;
import org.exist.validation.XmlLibraryChecker;
import org.exist.xmldb.DatabaseImpl;
import org.exist.xmldb.ShutdownListener;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.MultiException;
import org.eclipse.jetty.xml.XmlConfiguration;

import java.util.Timer;
import java.util.TimerTask;
import javax.servlet.Servlet;
import org.apache.log4j.Logger;
import org.exist.SystemProperties;

/**
 * This class provides a main method to start Jetty with eXist. It registers shutdown
 * handlers to cleanly shut down the database and the webserver.
 * If database is NATIVE-CLUSTER, Clustercomunication is configured and started.
 * 
 * @author wolf
 */
public class JettyStart implements LifeCycle.Listener {

    protected static final Logger logger = Logger.getLogger(JettyStart.class);

    public static void main(String[] args) {
        JettyStart start = new JettyStart();
        start.run(args, null);
    }

    private final static int STATUS_STARTING = 0;
    private final static int STATUS_STARTED = 1;
    private final static int STATUS_STOPPING = 2;
    private final static int STATUS_STOPPED = 3;

    private int status = STATUS_STOPPED;
    private Thread shutdownHook = null;
    
    public JettyStart() {
        // Additional checks XML libs @@@@
        XmlLibraryChecker.check();
    }

    public void run() {
        String jettyProperty = System.getProperty("jetty.home");
        if(jettyProperty==null) {
            File home = ConfigurationHelper.getExistHome();
            File jettyHome = new File(new File(home, "tools"), "jetty");
            jettyProperty = jettyHome.getAbsolutePath();
            System.setProperty("jetty.home", jettyProperty);
        }
        File standaloneFile = new File(new File(jettyProperty, "etc"), "standalone.xml");
        run(new String[]{standaloneFile.getAbsolutePath()}, null);
    }
    
    public void run(String[] args, Observer observer) {
        if (args.length == 0) {
            logger.info("No configuration file specified!");
            return;
        }

        String shutdownHookOption = System.getProperty("exist.register-shutdown-hook", "true");
        boolean registerShutdownHook = shutdownHookOption.equals("true");

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
        msg = "[eXist Version : " + SystemProperties.getInstance().getSystemProperty("product-version", "unknown") + "]";
        logger.info(msg);
        msg = "[eXist Build : " + SystemProperties.getInstance().getSystemProperty("product-build", "unknown") + "]";
        logger.info(msg);
        msg = "[eXist Home : " + SystemProperties.getInstance().getSystemProperty("exist.home", "unknown") + "]";
        logger.info(msg);
        msg = "[SVN Revision : " + SystemProperties.getInstance().getSystemProperty("svn-revision", "unknown") + "]";
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
            if (args.length == 2) {
                config = new SingleInstanceConfiguration(args[1]);
            } else {
                config = new SingleInstanceConfiguration();
            }

            if (observer != null){
                BrokerPool.registerStatusObserver(observer);
            }

            BrokerPool.configure(1, 5, config);

            // register the XMLDB driver
            Database xmldb = new DatabaseImpl();
            xmldb.setProperty("create-database", "false");
            DatabaseManager.registerDatabase(xmldb);

            configureCluster(config);

        } catch (Exception e) {
            logger.error("configuration error: " + e.getMessage(), e);
            e.printStackTrace();
            return;
        }

        // start Jetty
        final Server server;
        try {
            server = new Server();
            InputStream is = new FileInputStream(args[0]);
            XmlConfiguration configuration = new XmlConfiguration(is);
            configuration.configure(server);
            
            server.setStopAtShutdown(true);
            server.addLifeCycleListener(this);
            BrokerPool.getInstance().registerShutdownListener(new ShutdownListenerImpl(server));
            server.start();

            Connector[] connectors = server.getConnectors();

            // Construct description of all ports opened.
            StringBuilder allPorts = new StringBuilder();

            if (connectors.length > 1) {
                // plural s
                allPorts.append("s");
            }
            
            for(Connector connector: connectors){
                allPorts.append(" ");
                allPorts.append(connector.getPort());
            }

            //TODO: use pluggable interface
            Class<?> openid = null;
            try {
            	openid = Class.forName("org.exist.security.realm.openid.AuthenticatorOpenIdServlet");
            } catch (ClassNotFoundException e) {
			}
            Class<?> oauth = null;
            try {
            	oauth = Class.forName("org.exist.security.realm.oauth.OAuthServlet");
            } catch (ClassNotFoundException e) {
			}
            //*************************************************************

            logger.info("-----------------------------------------------------");
            logger.info("Server has started on port" + allPorts + ". Configured contexts:");

            HandlerCollection rootHandler = (HandlerCollection)server.getHandler();
            Handler[] handlers = rootHandler.getHandlers();
            for (Handler handler: handlers) {
            	if (handler instanceof ContextHandler) {
					ContextHandler contextHandler = (ContextHandler) handler;
	            	logger.info("'"+contextHandler.getContextPath()+"'");
            	}
            	
            	//TODO: pluggable in future
            	if (openid != null)
            		if (handler instanceof ServletContextHandler) {
            			ServletContextHandler contextHandler = (ServletContextHandler) handler;
            			contextHandler.addServlet(new ServletHolder((Class<? extends Servlet>)openid), "/openid");

            			String suffix;
            			if (contextHandler.getContextPath().endsWith("/"))
            				suffix = "openid";
           				else 
               				suffix = "/openid";
            			logger.info("'"+contextHandler.getContextPath() + suffix+"'");
            		}
            	if (oauth != null)
            		if (handler instanceof ServletContextHandler) {
            			ServletContextHandler contextHandler = (ServletContextHandler) handler;
            			contextHandler.addServlet(new ServletHolder((Class<? extends Servlet>)openid), "/oauth");

            			String suffix;
            			if (contextHandler.getContextPath().endsWith("/"))
            				suffix = "oauth";
           				else 
               				suffix = "/oauth";
            			logger.info("'"+contextHandler.getContextPath() + suffix+"'");
            		}
                //*************************************************************
            }

            logger.info("-----------------------------------------------------");

            if (registerShutdownHook) {
                // register a shutdown hook for the server
                shutdownHook = new Thread() {

                    @Override
                    public void run() {
                        setName("Shutdown");
                        BrokerPool.stopAll(true);
                        if (server.isStopping() || server.isStopped())
                            return;
                        try {
                            server.stop();
                        } catch (Exception e) {
                        }
//                        try {
//                            Thread.sleep(1000);
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }
                    }
                };
                Runtime.getRuntime().addShutdownHook(shutdownHook);
            }
            
        } catch (MultiException e) {

            // Mute the BindExceptions

            boolean hasBindException = false;
            for (Object t : e.getThrowables()) {
                if (t instanceof java.net.BindException) {
                    hasBindException = true;
                    logger.info("----------------------------------------------------------");
                    logger.info("ERROR: Could not bind to port because " +
                        ((Exception) t).getMessage());
                    logger.info(t.toString());
                    logger.info("----------------------------------------------------------");
                }
            }

            // If it is another error, print stacktrace
            if (!hasBindException) {
                e.printStackTrace();
            }
        } catch (SocketException e) {
            logger.info("----------------------------------------------------------");
            logger.info("ERROR: Could not bind to port because " +
                        e.getMessage());
            logger.info(e.toString());
            logger.info("----------------------------------------------------------");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void shutdown() {
        if (shutdownHook != null)
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        BrokerPool.stopAll(false);
        while (status != STATUS_STOPPED) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }
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
            logger.info("Database shutdown: stopping server in 1sec ...");
            if (remainingInstances == 0) {
                // give the webserver a 1s chance to complete open requests
                Timer timer = new Timer("jetty shutdown schedule", true);
                timer.schedule(new TimerTask() {

                    public void run() {
                        try {
                            // stop the server
                            server.stop();
                            ClusterComunication cluster = ClusterComunication.getInstance();
                            if (cluster != null) {
                                cluster.stop();
                            }
                            server.join();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }, 1000);
            }
        }
    }

    private void configureCluster(Configuration c) throws ClusterException {
        String database = (String) c.getProperty("database");
        if (!database.equalsIgnoreCase("NATIVE_CLUSTER")) {
            return;
        }

        ClusterComunication.configure(c);
    }

    public synchronized boolean isStarted() {
        if (status == STATUS_STARTED || status == STATUS_STARTING)
            return true;
        if (status == STATUS_STOPPED)
            return false;
        while (status != STATUS_STOPPED) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }
        return false;
    }

    public synchronized void lifeCycleStarting(LifeCycle lifeCycle) {
        logger.info("Jetty server starting...");
        status = STATUS_STARTING;
        notifyAll();
    }

    public synchronized void lifeCycleStarted(LifeCycle lifeCycle) {
        logger.info("Jetty server started.");
        status = STATUS_STARTED;
        notifyAll();
    }

    public void lifeCycleFailure(LifeCycle lifeCycle, Throwable throwable) {
    }

    public synchronized void lifeCycleStopping(LifeCycle lifeCycle) {
        logger.info("Jetty server stopping...");
        status = STATUS_STOPPING;
        notifyAll();
    }

    public synchronized void lifeCycleStopped(LifeCycle lifeCycle) {
        logger.info("Jetty server stopped");
        status = STATUS_STOPPED;
        notifyAll();
    }
}
