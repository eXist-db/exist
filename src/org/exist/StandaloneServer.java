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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.avalon.excalibur.cli.CLArgsParser;
import org.apache.avalon.excalibur.cli.CLOption;
import org.apache.avalon.excalibur.cli.CLOptionDescriptor;
import org.apache.avalon.excalibur.cli.CLUtil;
import org.apache.log4j.Logger;

import org.exist.memtree.SAXAdapter;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.xmldb.ShutdownListener;

import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.handler.ContextHandler;
import org.mortbay.jetty.security.SslSocketConnector;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.FilterHolder;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.thread.QueuedThreadPool;
import org.mortbay.util.MultiException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

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

    public interface ServletBootstrap {

        void bootstrap(Properties properties, Context context);
    }
    private final static Logger LOG = Logger.getLogger(StandaloneServer.class);
    //  command-line options
    private final static int HELP_OPT = 'h';
    private final static int DEBUG_OPT = 'd';
    private final static int HTTP_PORT_OPT = 'p';
    private final static int THREADS_OPT = 't';
    private final static CLOptionDescriptor OPTIONS[] = new CLOptionDescriptor[]{
        new CLOptionDescriptor("help", CLOptionDescriptor.ARGUMENT_DISALLOWED,
        HELP_OPT, "print help on command line options and exit."),
        new CLOptionDescriptor("debug", CLOptionDescriptor.ARGUMENT_DISALLOWED,
        DEBUG_OPT, "debug XMLRPC calls."),
        new CLOptionDescriptor("http-port", CLOptionDescriptor.ARGUMENT_REQUIRED,
        HTTP_PORT_OPT, "set HTTP port."),
        new CLOptionDescriptor("threads", CLOptionDescriptor.ARGUMENT_REQUIRED,
        THREADS_OPT, "set max. number of parallel threads allowed by the db.")
    };
    private final static String DEFAULT_HTTP_LISTENER_PORT = "8088";
    private static Properties DEFAULT_PROPERTIES = new Properties();


    static {
        DEFAULT_PROPERTIES.setProperty("webdav.enabled", "yes");
        DEFAULT_PROPERTIES.setProperty("rest.enabled", "yes");
        DEFAULT_PROPERTIES.setProperty("xmlrpc.enabled", "yes");
        DEFAULT_PROPERTIES.setProperty("webdav.authentication", "basic");
        DEFAULT_PROPERTIES.setProperty("rest.form.encoding", "UTF-8");
        DEFAULT_PROPERTIES.setProperty("rest.container.encoding", "UTF-8");
        DEFAULT_PROPERTIES.setProperty("rest.param.dynamic-content-type", "no");
    }
    private Server server;
    private Map forwarding = new HashMap();
    private Map listeners = new HashMap();
    private Map filters = new HashMap();

    public StandaloneServer() {
    }

    public void run(String[] args) throws Exception {
        printNotice();

        //set default properties
        Properties props = new Properties(DEFAULT_PROPERTIES);

        //set default connector
        Properties defaultListener = new Properties();
        defaultListener.setProperty("port", DEFAULT_HTTP_LISTENER_PORT);
        listeners.put("http", defaultListener);

        //read the configuration file
        List servlets = configure(props);

        CLArgsParser optParser = new CLArgsParser(args, OPTIONS);
        if (optParser.getErrorString() != null) {
            System.err.println("ERROR: " + optParser.getErrorString());
            return;
        }
        List opt = optParser.getArguments();
        int size = opt.size();
        CLOption option;
        int threads = 5;
        for (int i = 0; i < size; i++) {
            option = (CLOption) opt.get(i);
            switch (option.getId()) {
                case HELP_OPT:
                    printHelp();
                    return;
                case DEBUG_OPT:
                    break;
                case HTTP_PORT_OPT:
                    Properties httpListener = (Properties) listeners.get("http");
                    httpListener.put("port", option.getArgument());
                    listeners.put("http", httpListener);
                    break;
                case THREADS_OPT:
                    try {
                        threads = Integer.parseInt(option.getArgument());
                    } catch (NumberFormatException e) {
                        System.err.println("option -t requires a numeric argument");
                        return;
                    }
                    break;
            }
        }

        System.out.println("Loading configuration ...");
        Configuration config = new Configuration("conf.xml");
        BrokerPool.configure(1, threads, config);
        BrokerPool.getInstance().registerShutdownListener(new ShutdownListenerImpl());
        initXMLDB();

        startHttpServer(servlets, props);

        System.out.println("\nServer launched ...");
        System.out.println("Installed services:");
        System.out.println("-----------------------------------------------");
        Set listenerProtocols = listeners.keySet();
        for (int i = 0; i < servlets.size(); i++) {
            String name = (String) servlets.get(i);
            if (props.getProperty(name + ".enabled").equalsIgnoreCase("yes")) {
                for (Iterator itProtocol = listenerProtocols.iterator(); itProtocol.hasNext();) {
                    String listenerProtocol = (String) itProtocol.next();
                    Properties listenerProperties = (Properties) listeners.get(listenerProtocol);
                    String host = listenerProperties.getProperty("host", "localhost");
                    String port = listenerProperties.getProperty("port");
                    System.out.println(name + ":\t" + host + ":" + port + props.getProperty(name + ".context"));
                }
            }
        }
    }

    public boolean isStarted() {
        if (server == null) {
            return false;
        }
        return server.isStarted();
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
     * @throws UnknownHostException
     * @throws IllegalArgumentException
     * @throws MultiException
     */
    private void startHttpServer(List servlets, Properties props) throws Exception {
        server = new Server();

        //setup listeners
        Set listenerProtocols = listeners.keySet();
        for (Iterator itProtocol = listenerProtocols.iterator(); itProtocol.hasNext();) {
            String listenerProtocol = (String) itProtocol.next();
            Properties listenerProps = (Properties) listeners.get(listenerProtocol);
            Connector connector = null;

            /** currently support http and https listeners */
            if (listenerProtocol.equals("http")) {
                connector = new SocketConnector();
            } else if (listenerProtocol.equals("https")) {
                connector = new SslSocketConnector();

                Properties params = (Properties) listenerProps.get("params");

                //set the keystore if specified
                if (params.containsKey("keystore")) {
                    String keystore = params.getProperty("keystore");
                    ((SslSocketConnector) connector).setKeystore(keystore);
                }
            }

            // TODO maybe this whole block can be removed
            if (connector != null) {
                //configure lisetener
                connector.setHost(listenerProps.getProperty("host"));
                String port = (String) listenerProps.get("port");
                connector.setPort(Integer.parseInt(port));
                String address = (String) listenerProps.get("address");

                // TODO can be removed?
//                if (address != null) {
//                    InetAddress iaddress = InetAddress.getByName(address);
//                    ((ThreadedServer) connector).setInetAddress(iaddress);
//                }


                // TODO check this
                QueuedThreadPool pool = new QueuedThreadPool();
                pool.setMinThreads(5);
                pool.setMaxThreads(50);
                server.setThreadPool(pool);

                server.addConnector(connector);
            }
        }


//        HttpContext context = new HttpContext();
//        context.setContextPath("/");

//        ContextHandler contextHandler = new ContextHandler();
//        contextHandler.setContextPath("/");
//
//        // Setting up resourceBase, if it is possible
//        // This one is needed by many Servlets which depend
//        // on a not null context.getResourceBase() value
//        File eXistHome = ConfigurationHelper.getExistHome();
//        if (eXistHome != null) {
//            contextHandler.setResourceBase(eXistHome.getAbsolutePath());
//        }


        Context context = new Context(server, "/", Context.SESSIONS);

//        WebApplicationHandler webappHandler = new WebApplicationHandler();

        // TODO: this should be read from a configuration file
        Map<String, ServletBootstrap> bootstrappers = new HashMap<String, ServletBootstrap>();


        // REST
        bootstrappers.put("rest", new ServletBootstrap() {

            public void bootstrap(Properties props, Context context) {
                String path = props.getProperty("rest.context", "/*");

                ServletHolder restServlet = new ServletHolder(new org.exist.http.servlets.EXistServlet());
                restServlet.setName("EXistServlet");
                restServlet.setInitParameter("form-encoding", props.getProperty("rest.param.form-encoding"));
                restServlet.setInitParameter("container-encoding", props.getProperty("rest.param.container-encoding"));
                restServlet.setInitParameter("dynamic-content-type", props.getProperty("rest.param.dynamic-content-type"));
                String value = props.getProperty("rest.param.use-default-user");
                if (value != null) {
                    restServlet.setInitParameter("use-default-user", value);
                }
                value = props.getProperty("rest.param.default-user-username");
                if (value != null) {
                    restServlet.setInitParameter("user", value);
                }
                value = props.getProperty("rest.param.default-user-password");
                if (value != null) {
                    restServlet.setInitParameter("password", value);
                }
                context.addServlet(restServlet, path);
            }
        });

        // WEBDAV
        bootstrappers.put("webdav", new ServletBootstrap() {

            public void bootstrap(Properties props, Context context) {
                String path = props.getProperty("webdav.context", "/webdav/*");
                ServletHolder davServlet = new ServletHolder(new org.exist.http.servlets.WebDAVServlet());;
                davServlet.setName("WebDAV");
                davServlet.setInitParameter("authentication", props.getProperty("webdav.param.authentication"));
                context.addServlet(davServlet, path);
            }
        });

        // XMLRPC
        bootstrappers.put("xmlrpc", new ServletBootstrap() {

            public void bootstrap(Properties props, Context context) {
                String path = props.getProperty("xmlrpc.context", "/xmlrpc/*");
                ServletHolder rpcServlet = new ServletHolder(new org.exist.xmlrpc.RpcServlet());
                rpcServlet.setName("RpcServlet");
                context.addServlet(rpcServlet, path);
            }
        });

        for (int i = 0; i < servlets.size(); i++) {
            String name = (String) servlets.get(i);
            ServletBootstrap bootstrapper = (ServletBootstrap) bootstrappers.get(name);
            if (bootstrapper != null) {
                bootstrapper.bootstrap(props, context);
            } else {
            	//XXX: need refactoring -shabanovd
//                String path = props.getProperty(name + ".context", "/" + name + "/*");
//                String sname = props.getProperty(name + ".name", name);
//                ServletHolder servlet = context.addServlet(props.getProperty(name + ".class"), path);
//                servlet.setName(sname);
//                String paramPrefix = name + ".param.";
//                for (Enumeration pnames = props.propertyNames(); pnames.hasMoreElements();) {
//                    String pname = (String) pnames.nextElement();
//                    if (pname.startsWith(paramPrefix)) {
//                        String theName = pname.substring(paramPrefix.length());
//                        servlet.setInitParameter(theName, props.getProperty(pname));
//                    }
//                }
            }
        }


        //setup filters
        Set filterClasses = filters.keySet();
        for (Iterator itFilterClass = filterClasses.iterator(); itFilterClass.hasNext();) {
            String filterClass = (String) itFilterClass.next();
            Properties filterProps = (Properties) filters.get(filterClass);

            FilterHolder fh = context.addFilter(filterClass, filterClass, 1); // TODO check 3rd param

            //TODO: putAll may be wrong??? check this
            fh.setInitParameters((Properties) filterProps.get("params"));

        // TODO no idea what to do here. What is the dispatcher thing
//            //TODO: Dispatcher.__DEFAULY may be wrong??? check this
//            webappHandler.addFilterPathMapping(filterProps.getProperty("path"), filterClass, org.mortbay.jetty.servlet.Dispatcher.__DEFAULT);
//
//            newContext.addFilter(filterClass, filterProps.getProperty("path"), Dispatcher.)
        }

        // TODO class disappeared, check alternative
//        if (forwarding.size() > 0) {
//            ForwardHandler forward = new ForwardHandler();
//            //forward.setHandleQueries(true); //TODO needed if you wish to pass querystring parameters - should maybe be a server.xml option?
//            for (Iterator i = forwarding.keySet().iterator(); i.hasNext();) {
//                String path = (String) i.next();
//                String destination = (String) forwarding.get(path);
//                if (path.length() == 0) {
//                    forward.setRootForward(destination);
//                } else {
//                    forward.addForward(path, destination);
//                }
//            }
//
//            context.addHandler(forward);
//        }

//        contextHandler.setHandler(context);
        // TODO has changed
//        context.addHandler(new NotFoundHandler());

        // TODO not sure
        //httpServer.addContext(context);
        server.setHandler(context);

        server.start();
    }

    private static void printHelp() {
        System.out.println("Usage: java " + StandaloneServer.class.getName() + " [options]");
        System.out.println(CLUtil.describeOptions(OPTIONS).toString());
    }

    public static void printNotice() {
        System.out.println("eXist version 1.3, Copyright (C) 2001-2008 The eXist Project");
        System.out.println("eXist comes with ABSOLUTELY NO WARRANTY.");
        System.out.println("This is free software, and you are welcome to " + "redistribute it\nunder certain conditions; " + "for details read the license file.\n");
    }

    public void shutdown() {
        BrokerPool.stopAll(false);
    }

    private List configure(Properties properties) throws ParserConfigurationException, SAXException, IOException {
        // try to read configuration from file. Guess the location if
        // necessary
        InputStream is = null;
        String file = System.getProperty("server.xml", "server.xml");
        File f = ConfigurationHelper.lookup(file);
        if (!f.canRead()) {
            is = StandaloneServer.class.getClassLoader().getResourceAsStream("org/exist/server.xml");
            if (is == null) {
                throw new IOException("Server configuration not found!");
            }
            System.out.println("Reading server configuration from exist.jar");
        } else {
            System.out.println("Reading server configuration from: " + f.getAbsolutePath());
            is = new FileInputStream(f);
        }

        // initialize xml parser
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        InputSource src = new InputSource(is);
        SAXParser parser = factory.newSAXParser();
        XMLReader reader = parser.getXMLReader();
        SAXAdapter adapter = new SAXAdapter();
        reader.setContentHandler(adapter);
        reader.parse(src);

        Document doc = adapter.getDocument();
        Element root = doc.getDocumentElement();
        if (!root.getLocalName().equals("server")) {
            LOG.warn("Configuration should have a root element <server>");
            return new ArrayList();
        }

        List configurations = new ArrayList();
        NodeList cl = root.getChildNodes();
        for (int i = 0; i < cl.getLength(); i++) {
            Node node = cl.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) node;
                String name = elem.getLocalName();
                if ("forwarding".equals(name)) {
                    configureForwards(elem);
                } else if ("listener".equals(name)) {
                    configureListener(elem);
                } else if ("filter".equals(name)) {
                    configureFilter(elem);
                } else if ("servlet".equals(name)) {
                    String className = elem.getAttribute("class");
                    configurations.add(className);
                    parseDefaultAttrs(properties, elem, className);
                    properties.putAll(parseParams(elem, className));
                } else {
                    configurations.add(name);
                    parseDefaultAttrs(properties, elem, name);
                    properties.putAll(parseParams(elem, name));
                }
            }
        }
        return configurations;
    }

    private void configureListener(Element listener) {
        NamedNodeMap listenerAttrs = listener.getAttributes();
        Node listenerProtocol = listenerAttrs.getNamedItem("protocol");
        Node listenerPort = listenerAttrs.getNamedItem("port");
        Node listenerHost = listenerAttrs.getNamedItem("host");
        Node listenerAddress = listenerAttrs.getNamedItem("address");

        if (listenerProtocol != null && listenerPort != null) {
            Properties listenerProps = new Properties();
            listenerProps.put("port", listenerPort.getNodeValue());
            if (listenerHost != null) {
                listenerProps.put("host", listenerHost.getNodeValue());
            }
            if (listenerAddress != null) {
                listenerProps.put("address", listenerAddress.getNodeValue());
            }

            listenerProps.put("params", parseParams(listener, null));

            listeners.put(listenerProtocol.getNodeValue().toLowerCase(), listenerProps);
        }
    }

    private void configureFilter(Element filter) {
        NamedNodeMap filterAttrs = filter.getAttributes();
        Node filterEnabled = filterAttrs.getNamedItem("enabled");
        Node filterPath = filterAttrs.getNamedItem("path");
        Node filterClass = filterAttrs.getNamedItem("class");

        if (filterEnabled != null && filterPath != null && filterClass != null) {
            if (filterEnabled.getNodeValue().equals("yes")) {
                Properties filterProps = new Properties();
                filterProps.put("path", filterPath.getNodeValue());
                filterProps.put("params", parseParams(filter, null));

                filters.put(filterClass.getNodeValue(), filterProps);
            }
        }
    }

    private Properties parseParams(Element root, String prefix) {
        Properties paramProperties = new Properties();

        if (prefix != null) {
            prefix += ".param.";
        } else {
            prefix = "";
        }

        NodeList cl = root.getChildNodes();
        for (int i = 0; i < cl.getLength(); i++) {
            Node node = cl.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && "param".equals(node.getLocalName())) {
                Element elem = (Element) node;
                String name = elem.getAttribute("name");
                String value = elem.getAttribute("value");
                if (name != null && name.length() > 0) {
                    paramProperties.setProperty(prefix + name, value);
                }
            }
        }

        return paramProperties;
    }

    private void configureForwards(Element root) {
        NodeList cl = root.getChildNodes();
        for (int i = 0; i < cl.getLength(); i++) {
            Node node = cl.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) node;
                String name = elem.getLocalName();
                if ("root".equals(name)) {
                    String dest = elem.getAttribute("destination");
                    forwarding.put("", dest);
                } else if ("forward".equals(name)) {
                    String path = elem.getAttribute("path");
                    String dest = elem.getAttribute("destination");
                    forwarding.put(path, dest);
                }
            }
        }
    }

    /**
     * @param properties
     * @param elem
     */
    private void parseDefaultAttrs(Properties properties, Element elem, String prefix) {
        String[] names = {"enabled", "name", "context", "class"};
        for (int i = 0; i < names.length; i++) {
            String attr = elem.getAttribute(names[i]);
            if (attr != null && attr.length() > 0) {
                properties.setProperty(prefix + '.' + names[i], attr);
            }
        }
    }

    class ShutdownListenerImpl implements ShutdownListener {

        public void shutdown(String dbname, int remainingInstances) {
            if (remainingInstances == 0) {
                // give the server a 1s chance to complete pending requests
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {

                    public void run() {
                        System.out.println("killing threads ...");
                        try {
                            server.stop();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
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
