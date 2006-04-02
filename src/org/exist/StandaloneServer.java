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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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
import org.apache.xmlrpc.XmlRpc;
import org.exist.memtree.SAXAdapter;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.xmldb.ShutdownListener;
import org.mortbay.http.HttpContext;
import org.mortbay.http.HttpServer;
import org.mortbay.http.SocketListener;
import org.mortbay.http.handler.ForwardHandler;
import org.mortbay.http.handler.NotFoundHandler;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.util.MultiException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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

    private final static Logger LOG = Logger.getLogger(StandaloneServer.class);
    
    //  command-line options
	private final static int HELP_OPT = 'h';
    private final static int DEBUG_OPT = 'd';
    private final static int HTTP_PORT_OPT = 'p';
    private final static int THREADS_OPT = 't';
    
    private final static CLOptionDescriptor OPTIONS[] = new CLOptionDescriptor[] {
        new CLOptionDescriptor( "help", CLOptionDescriptor.ARGUMENT_DISALLOWED,
            HELP_OPT, "print help on command line options and exit." ),
        new CLOptionDescriptor( "debug", CLOptionDescriptor.ARGUMENT_DISALLOWED,
            DEBUG_OPT, "debug XMLRPC calls." ),
        new CLOptionDescriptor( "http-port", CLOptionDescriptor.ARGUMENT_REQUIRED,
            HTTP_PORT_OPT, "set HTTP port." ),
        new CLOptionDescriptor( "threads", CLOptionDescriptor.ARGUMENT_REQUIRED,
            THREADS_OPT, "set max. number of parallel threads allowed by the db." )
    };
    
    private static Properties DEFAULT_PROPERTIES = new Properties();
    static {
        DEFAULT_PROPERTIES.setProperty("port", "8088");
    	DEFAULT_PROPERTIES.setProperty("webdav.enabled", "yes");
    	DEFAULT_PROPERTIES.setProperty("rest.enabled", "yes");
    	DEFAULT_PROPERTIES.setProperty("xmlrpc.enabled", "yes");
    	DEFAULT_PROPERTIES.setProperty("webdav.authentication", "basic");
    	DEFAULT_PROPERTIES.setProperty("rest.form.encoding", "UTF-8");
    	DEFAULT_PROPERTIES.setProperty("rest.container.encoding", "UTF-8");
    }
    
    private HttpServer httpServer;
    
    private Map forwarding = new HashMap();
    
    public StandaloneServer() {
    }
    
    public void run(String[] args) throws Exception {
        printNotice();
        
        Properties props = new Properties(DEFAULT_PROPERTIES);
        
        configure(props);
        
        CLArgsParser optParser = new CLArgsParser( args, OPTIONS );
        if(optParser.getErrorString() != null) {
            System.err.println( "ERROR: " + optParser.getErrorString());
            return;
        }
        List opt = optParser.getArguments();
        int size = opt.size();
        CLOption option;
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
                    props.setProperty("port", option.getArgument());
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
        
        int httpPort = 8088;
        try {
            httpPort = Integer.parseInt(props.getProperty("port"));
        } catch( NumberFormatException e ) {
            System.err.println("port needs to be number");
            return;
        }
        
        System.out.println( "Loading configuration from " + Configuration.getExistHome().getAbsolutePath() +
                File.separatorChar + "conf.xml" );
        Configuration config = new Configuration( "conf.xml");
        BrokerPool.configure( 1, threads, config );
        BrokerPool.getInstance().registerShutdownListener(new ShutdownListenerImpl());
        initXMLDB();
            
        startHTTPServer(httpPort, props);
        
        System.out.println("\nServer launched ...");
        System.out.println("Installed services:");
        System.out.println("-----------------------------------------------");
        if (props.getProperty("rest.enabled").equalsIgnoreCase("yes"))
        	System.out.println("REST servlet:\tlocalhost:" + httpPort);
        if (props.getProperty("webdav.enabled").equalsIgnoreCase("yes"))
        	System.out.println("WebDAV:\t\tlocalhost:" + httpPort + "/webdav");
        if (props.getProperty("xmlrpc.enabled").equalsIgnoreCase("yes"))
        	System.out.println("XMLRPC:\t\tlocalhost:" + httpPort + "/xmlrpc");
    }
    
    public boolean isStarted() {
    	if (httpServer == null)
    		return false;
    	return httpServer.isStarted();    	
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
        listener.setHost(null);
        listener.setPort(httpPort);
        listener.setMinThreads(5);
        listener.setMaxThreads(50);
        httpServer.addListener(listener);
        
        HttpContext context = new HttpContext();
        context.setContextPath("/");
        
        ServletHandler servletHandler = new ServletHandler();
        if (props.getProperty("rest.enabled").equalsIgnoreCase("yes")) {
            String path = props.getProperty("rest.context", "/*");
        	ServletHolder restServlet = 
        		servletHandler.addServlet("EXistServlet", path, "org.exist.http.servlets.EXistServlet");
        	restServlet.setInitParameter("form-encoding", props.getProperty("rest.form-encoding"));
        	restServlet.setInitParameter("container-encoding", props.getProperty("rest.container-encoding"));
                String value = props.getProperty("rest.use-default-user");
                if (value!=null) {
                   restServlet.setInitParameter("use-default-user", value);
                }
                value = props.getProperty("rest.default-user-username");
                if (value!=null) {
                   restServlet.setInitParameter("user", value);
                }
                value = props.getProperty("rest.default-user-password");
                if (value!=null) {
                   restServlet.setInitParameter("password", value);
                }
        }
        if (props.getProperty("webdav.enabled").equalsIgnoreCase("yes")) {
            String path = props.getProperty("webdav.context", "/webdav/*");
        	ServletHolder davServlet =
        		servletHandler.addServlet("WebDAV", path, "org.exist.http.servlets.WebDAVServlet");
        	davServlet.setInitParameter("authentication", props.getProperty("webdav.authentication"));
        }
        if(props.getProperty("xmlrpc.enabled").equalsIgnoreCase("yes")) {
            String path = props.getProperty("xmlrpc.context", "/xmlrpc/*");
            servletHandler.addServlet("RpcServlet", path, "org.exist.xmlrpc.RpcServlet");
        }
        
        if (forwarding.size() > 0) {
            ForwardHandler forward = new ForwardHandler();
            
            for (Iterator i = forwarding.keySet().iterator(); i.hasNext(); ) {
                String path = (String) i.next();
                String destination = (String) forwarding.get(path);
                if (path.length() == 0)
                    forward.setRootForward(destination);
                else
                    forward.addForward(path, destination);
            }
        
            context.addHandler(forward);
        }
        
        context.addHandler(servletHandler);
        context.addHandler(new NotFoundHandler());
        httpServer.addContext(context);
        httpServer.start();
    }
    
    private static void printHelp() {
        System.out.println("Usage: java " + StandaloneServer.class.getName() + " [options]");
        System.out.println(CLUtil.describeOptions(OPTIONS).toString());
    }
    
    public static void printNotice() {
        System.out.println("eXist version 1.0, Copyright (C) 2005 The eXist Project");
        System.out.println("eXist comes with ABSOLUTELY NO WARRANTY.");
        System.out.println("This is free software, and you are welcome to "
                + "redistribute it\nunder certain conditions; "
                + "for details read the license file.\n");
    }
    
    public void shutdown() {
		BrokerPool.stopAll(false);
	}
    
    private void configure(Properties properties) throws ParserConfigurationException, SAXException, IOException {
        // try to read configuration from file. Guess the location if
        // necessary
        InputStream is = null;
        String file = "server.xml";        
        File f = Configuration.lookup(file);
        if (!f.canRead()) {
            is = StandaloneServer.class.getClassLoader().getResourceAsStream("org/exist/server.xml");
            if (is == null)
                throw new IOException("Server configuration not found!");
        } else {
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
            return;
        }
        String port = root.getAttribute("port");
        if (port != null && port.length() > 0)
            properties.setProperty("port", port);
        
        NodeList cl = root.getChildNodes();
        for (int i = 0; i < cl.getLength(); i++) {
            Node node = cl.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) node;
                String name = elem.getLocalName();
                if ("rest".equals(name)) {
                    parseDefaultAttrs(properties, elem, "rest");
                    parseParams(properties, elem, "rest");
                } else if ("webdav".equals(name))
                    parseDefaultAttrs(properties, elem, "webdav");
                else if ("xmlrpc".equals(name))
                    parseDefaultAttrs(properties, elem, "xmlrpc");
                else if ("forwarding".equals(name))
                    configureForwards(elem);
            }
        }
    }

    private void parseParams(Properties properties, Element root, String prefix) {
        NodeList cl = root.getChildNodes();
        for (int i = 0; i < cl.getLength(); i++) {
            Node node = cl.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && "param".equals(node.getLocalName())) {
                Element elem = (Element) node;
                String name = elem.getAttribute("name");
                String value = elem.getAttribute("value");
                if (name != null && name.length() > 0)
                    properties.setProperty(prefix + '.' + name, value);
            }
        }
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
        String attr = elem.getAttribute("enabled");
        if (attr != null && attr.length() > 0)
            properties.setProperty(prefix + ".enabled", attr);
        attr = elem.getAttribute("context");
        if (attr != null && attr.length() > 0)
            properties.setProperty(prefix + ".context", attr);
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
