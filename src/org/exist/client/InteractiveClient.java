/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2013 The eXist-db Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.client;

import java.awt.Dimension;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.swing.ImageIcon;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import jline.Completor;
import jline.ConsoleReader;
import jline.History;
import jline.Terminal;
import org.apache.avalon.excalibur.cli.CLArgsParser;
import org.apache.avalon.excalibur.cli.CLOption;
import org.apache.avalon.excalibur.cli.CLUtil;
import org.apache.log4j.Logger;
import org.exist.SystemProperties;
import org.exist.dom.XMLUtil;
import org.exist.security.ACLPermission;
import org.exist.security.Account;
import org.exist.security.Permission;
import org.exist.security.SecurityManager;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.ElementIndex;
import org.exist.storage.TextSearchEngine;
import org.exist.util.CollectionScanner;
import org.exist.util.ConfigurationHelper;
import org.exist.util.DirectoryScanner;
import org.exist.util.GZIPInputSource;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.util.Occurrences;
import org.exist.util.ProgressBar;
import org.exist.util.ProgressIndicator;
import org.exist.util.ZipEntryInputSource;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.CollectionManagementServiceImpl;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.ExtendedResource;
import org.exist.xmldb.IndexQueryService;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XPathQueryServiceImpl;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;
import org.exist.xquery.util.URIUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.modules.XUpdateQueryService;

/**
 * Command-line client based on the XML:DB API.
 *
 * @author wolf
 */
public class InteractiveClient {
    
    // ANSI colors for ls display
    // private final static String ANSI_BLUE = "\033[0;34m";
    private final static String ANSI_CYAN = "\033[0;36m";
    private final static String ANSI_WHITE = "\033[0;37m";
    
    private final static String EOL = System.getProperty("line.separator");
    
    // properties

    // keys
    public static final String USER = "user";
    public static final String PASSWORD = "password";
    public static final String URI = "uri";
    public static final String CONFIGURATION = "configuration";
    public static final String DRIVER = "driver";
    public static final String SSL_ENABLE = "ssl-enable";
    public static final String LOCAL_MODE = "local-mode-opt";
    public static final String NO_EMBED_MODE = "NO_EMBED_MODE";
    
    // values
    protected static final String EDIT_CMD = "emacsclient -t $file";
    protected static final String ENCODING = "UTF-8";
    protected static final String PASS = null;
    protected static final String URI_DEFAULT = "xmldb:exist://localhost:8080/exist/xmlrpc";
    protected static final String SSL_ENABLE_DEFAULT = "FALSE";
    protected static final String LOCAL_MODE_DEFAULT = "FALSE";
    protected static final String NO_EMBED_MODE_DEFAULT = "FALSE";
    protected static final String USER_DEFAULT = SecurityManager.DBA_USER;
    protected static int PARALLEL_THREADS = 5;
    
    // Set
    protected final static Properties defaultProps = new Properties();
    {
        defaultProps.setProperty(DRIVER, driver);
        defaultProps.setProperty(URI, URI_DEFAULT);
        defaultProps.setProperty(USER, USER_DEFAULT);
        defaultProps.setProperty("editor", EDIT_CMD);
        defaultProps.setProperty("indent", "true");
        defaultProps.setProperty("encoding", ENCODING);
        defaultProps.setProperty("colors", "false");
        defaultProps.setProperty("permissions", "false");
        defaultProps.setProperty("expand-xincludes", "true");
        defaultProps.setProperty(SSL_ENABLE, SSL_ENABLE_DEFAULT);
    }
    
    protected static final int colSizes[] = new int[]{10, 10, 10, -1};
    
    protected static String driver = "org.exist.xmldb.DatabaseImpl";
    protected static String configuration = null;
    
    protected final TreeSet<String> completitions = new TreeSet<String>();
    protected final LinkedList<String> queryHistory = new LinkedList<String>();
    protected File queryHistoryFile;
    protected File historyFile;
    
    protected ConsoleReader console = null;
    
    protected Collection current = null;
    protected int nextInSet = 1;
    protected int maxResults = 10;
    protected XmldbURI path = XmldbURI.ROOT_COLLECTION_URI;
    protected Properties properties;
    
    protected String[] resources = null;
    protected ResourceSet result = null;
    protected final HashMap<String, String> namespaceMappings = new HashMap<String, String>();
    /** number of files of a recursive store  */
    protected int filesCount = 0;
    /** total length of a recursive store   */
    protected long totalLength =0;
    
    protected boolean quiet = false;
    protected boolean verbose = false;
    /** default recursive, maybe override by client.properties */
    protected boolean recurseDirs = true;
    protected boolean startGUI = true;
    
    protected Writer traceWriter = null;
    protected ClientFrame frame;
    
    private final static Logger LOG = Logger.getLogger(InteractiveClient.class.getName());
    
    //XXX:make pluggable
    private static boolean havePluggableCommands = false;
    
    static {
        try {
            Class.forName("org.exist.plugin.command.Commands");
            havePluggableCommands = true;
        } catch (final Exception e) {
            havePluggableCommands = false;
        }
    }
    
    //*************************************
    
    public InteractiveClient() {
    }
    
    /** Display help on commands */
    protected void displayHelp() {
        messageln("--- general commands ---");
        messageln("ls                   list collection contents");
        messageln("cd [collection|..]   change current collection");
        messageln("put [file pattern] upload file or directory"
                + " to the database");
        messageln("putgz [file pattern] upload possibly gzip compressed file or directory"
                + " to the database");
        messageln("putzip [file pattern] upload the contents of a ZIP archive"
                + " to the database");
        messageln("edit [resource] open the resource for editing");
        messageln("mkcol collection     create new sub-collection in "
                + "current collection");
        messageln("rm document          remove document from current "
                + "collection");
        messageln("rmcol collection     remove collection");
        messageln("set [key=value]      set property. Calling set without ");
        messageln("                     argument shows current settings.");
        messageln("validate [document]  validate xml document with system xml catalog.");
        messageln("validate [document] [grammar]  validate xml document with ");
        messageln("                     specified grammar document.");
        messageln(EOL + "--- search commands ---");
        messageln("find xpath-expr      execute the given XPath expression.");
        messageln("show [position]      display query result value at position.");
        messageln(EOL + "--- user management (may require dba rights) ---");
        messageln("users                list existing users.");
        messageln("adduser username     create a new user.");
        messageln("passwd username      change password for user. ");
        messageln("chown user group [resource]");
        messageln("                     change resource ownership. chown without");
        messageln("                     resource changes ownership of the current");
        messageln("                     collection.");
        messageln("chmod [resource] permissions");
        messageln("                     change resource permissions. Format:");
        messageln("                     [user|group|other]=[+|-][read|write|update].");
        messageln("                     chmod without resource changes permissions for");
        messageln("                     the current collection.");
        messageln("lock resource        put a write lock on the specified resource.");
        messageln("unlock resource      remove a write lock from the specified resource.");
        if (havePluggableCommands) {
            messageln("svn                  subversion command-line client.");
            messageln("threads              threads debug information.");
        }
        messageln("quit                 quit the program");
    }
    
    /**
     * The main program for the InteractiveClient class.
     *
     * @param args
     *                   The command line arguments
     */
    public static void main(final String[] args) {
        try {
            final InteractiveClient client = new InteractiveClient();
            if (!client.run(args)) {
                System.exit(1); // return non-zero exit status on failure
            }
        } catch (final Exception e) {
            e.printStackTrace();
            System.exit(1); // return non-zero exit status on exception
        }
    }
    
    /**
     * Register XML:DB driver and retrieve root collection.
     *
     * @exception Exception   Description of the Exception
     */
    protected void connect() throws Exception {
        final String uri = properties.getProperty(InteractiveClient.URI);
        if (startGUI && frame != null) {
            frame.setStatus("connecting to " + uri);
        }
        
        // Create database
        final Class<?> cl = Class.forName(properties.getProperty(DRIVER));
        final Database database = (Database) cl.newInstance();
        
        // Configure database
        database.setProperty("create-database", "true");       
        database.setProperty("ssl-enable", properties.getProperty(SSL_ENABLE));       
        
        // secure empty configuration
        final String configProp = properties.getProperty(InteractiveClient.CONFIGURATION);
        
        if(configProp != null && (!configProp.isEmpty())) {
            database.setProperty("configuration", configProp);
        }
        
        DatabaseManager.registerDatabase(database);
        
        final String collectionUri = uri + path;
        current = DatabaseManager.getCollection(collectionUri, properties.getProperty("user"), properties.getProperty("password"));
        if (startGUI && frame != null) {
            frame.setStatus("connected to " + uri + " as user " + properties.getProperty("user"));
        }
    }
    
    /**
     * Returns the current collection.
     *
     * @return the current collection
     */
    protected Collection getCollection() {
        return current;
    }
    
    public Properties getProperties() {
        return properties;
    }
    
    public void reloadCollection() throws XMLDBException {
        current = DatabaseManager.getCollection(properties.getProperty("uri")
        + path, properties.getProperty("user"),
                properties.getProperty("password"));
        getResources();
    }
    
    protected void setProperties() throws XMLDBException {
        String key;
        for(final Iterator i = properties.keySet().iterator(); i.hasNext(); ) {
            key = (String)i.next();
            current.setProperty(key, properties.getProperty(key));
        }
    }
    
    private String getOwnerName(final Permission perm) {
        if (perm.getOwner() == null) {
            return "?";
        } else {
            return perm.getOwner().getName();
        }
    }
    
    private String getGroupName(final Permission perm) {
        if (perm.getOwner() == null) {
            return "?";
        } else {
            return perm.getGroup().getName();
        }
    }

    /**
     * Get list of resources contained in collection.
     *
     * @exception XMLDBException
     *                         Description of the Exception
     */
    protected void getResources() throws XMLDBException {
        if (current == null) {
            return;
        }
        setProperties();
        final UserManagementService mgtService = (UserManagementService) current
                .getService("UserManagementService", "1.0");
        final String childCollections[] = current.listChildCollections();
        final String childResources[] = current.listResources();
        
        resources = new String[childCollections.length + childResources.length];
        //Collection child;
        Permission perm;
        
        final List<ResourceDescriptor> tableData = new ArrayList<ResourceDescriptor>(resources.length); // A list of ResourceDescriptor for the GUI
        
        final String cols[] = new String[4];
        int i = 0;
        for (; i < childCollections.length; i++) {
            //child = current.getChildCollection(childCollections[i]);
            
            perm = mgtService.getSubCollectionPermissions(current, childCollections[i]);
            
            if (properties.getProperty("permissions").equals("true")) {
                cols[0] = perm.toString();
            	cols[1] = getOwnerName(perm);
            	cols[2] = getGroupName(perm);
                cols[3] = URIUtils.urlDecodeUtf8(childCollections[i]);
                resources[i] = 'd' + formatString(cols, colSizes);
            } else {
                resources[i] = URIUtils.urlDecodeUtf8(childCollections[i]);
            }

            final Date created = mgtService.getSubCollectionCreationTime(current, childCollections[i]);

            if (startGUI) {
                tableData.add( new ResourceDescriptor.Collection(
                        XmldbURI.create(childCollections[i]),
                        getOwnerName(perm),
                        getGroupName(perm),
                        "c" + ((perm instanceof ACLPermission && ((ACLPermission)perm).getACECount() > 0) ? perm.toString() + '+' : perm.toString()),
                        created ) );
            }
            completitions.add(childCollections[i]);
        }
        Resource res;
        for (int j = 0; j < childResources.length; i++, j++) {
            res = current.getResource(childResources[j]);
            perm = mgtService.getPermissions(res);
            if (perm == null) {
                System.out.println("null"); //TODO this is not useful!
            }
            if (properties.getProperty("permissions").equals("true")) {
                resources[i] = '-' + perm.toString() + '\t' + perm.getOwner().getName()
                + '\t' + perm.getGroup().getName() + '\t'
                        + URIUtils.urlDecodeUtf8(childResources[j]);
            } else {
                resources[i] = URIUtils.urlDecodeUtf8(childResources[j]);
            }
            
            final Date lastModificationTime = ((EXistResource)res).getLastModificationTime();
            resources[i] += "\t" + lastModificationTime;
            
            if (startGUI) {
                tableData.add(new ResourceDescriptor.Document(
                        XmldbURI.create(childResources[j]),
                        getOwnerName(perm),
                        getGroupName(perm),
                        "-" + ((perm instanceof ACLPermission && ((ACLPermission)perm).getACECount() > 0) ? perm.toString() + '+' : perm.toString()),
                        lastModificationTime ) );
            }
            completitions.add(childResources[j]);
        }
        if (startGUI) {
            frame.setResources(tableData);
        }
    }
    
    /**
     * Display document on screen, by 24 lines.
     *
     * @param str string containing the document.
     */
    protected void more(final String str) {
        final LineNumberReader reader = new LineNumberReader(new StringReader(str));
        String line;
        // int count = 0;
        int ch;
        try {
            while (System.in.available() > 0) {
                System.in.read();
            }
            
            while ((line = reader.readLine()) != null) {
                if (reader.getLineNumber() % 24 == 0) {
                    System.out.print("line: " + reader.getLineNumber()
                    + "; press [return] for more or [q] for quit.");
                    ch = System.in.read();
                    if (ch == 'q' || ch == 'Q') {
                        return;
                    }
                }
                System.out.println(line);
            }
        } catch (final IOException ioe) {
            System.err.println("IOException: " + ioe);
        }
    }
    
    /**
     * In interactive mode, process a line entered by the user.
     *
     * @param line  the line entered
     * @return      true if command != quit
     */
    protected boolean process(final String line) {
        if (startGUI) {
            frame.setPath(path);
        }
        final String args[];
        if (line.startsWith("find")) {
            args = new String[2];
            args[0] = "find";
            args[1] = line.substring(5);
        } else {
            final StreamTokenizer tok = new StreamTokenizer(new StringReader(line));
            tok.resetSyntax();
            tok.wordChars(0x21, 0x7FFF);
            tok.quoteChar('"');
            tok.whitespaceChars(0x20, 0x20);
            
            final List<String> argList = new ArrayList<String>(3);
            // int i = 0;
            int token;
            try {
                while ((token = tok.nextToken()) != StreamTokenizer.TT_EOF) {
                    if (token == StreamTokenizer.TT_WORD || token == '"') {
                        argList.add(tok.sval);
                    }
                }
            } catch (final IOException e) {
                System.err.println("Could not parse command line.");
                return true;
            }
            args = new String[argList.size()];
            argList.toArray(args);
        }
        
        if (args.length == 0){
            return true;
        }
        
        try {
            XmldbURI newPath = path;
            final XmldbURI currUri = XmldbURI.xmldbUriFor(properties.getProperty("uri")).resolveCollectionPath(path);
            if (args[0].equalsIgnoreCase("ls")) {
                // list collection contents
                getResources();
                if (properties.getProperty("permissions").equals("true")) {
                    for (int i = 0; i < resources.length; i++) {
                        messageln(resources[i]);
                    }
                } else {
                    for (int i = 0; i < resources.length; i++) {
                        final StringBuilder buf = new StringBuilder();
                        int k = 0;
                        for (int j = 0; i < resources.length && j < 5; i++, j++) {
                            buf.append(resources[i]);
                            buf.append('\t');
                            k = j;
                        }
                        if (k == 4 && i < resources.length) {
                            i--;
                        }
                        messageln(buf.toString());
                    }
                }
            } else if (args[0].equalsIgnoreCase("cd")) {
                // change current collection
                completitions.clear();
                Collection temp;
                XmldbURI collectionPath;
                if (args.length < 2 || args[1] == null) {
                    collectionPath = XmldbURI.ROOT_COLLECTION_URI;
                } else {
                    collectionPath = XmldbURI.xmldbUriFor(URIUtils.urlEncodeUtf8(args[1]));
                }
                collectionPath = currUri.resolveCollectionPath(collectionPath);
                if(collectionPath.numSegments()==0) {
                    collectionPath = currUri.resolveCollectionPath(XmldbURI.ROOT_COLLECTION_URI);
                    messageln("cannot go above "+XmldbURI.ROOT_COLLECTION_URI.toString());
                }
                temp = DatabaseManager.getCollection(
                    collectionPath.toString(), 
                    properties.getProperty("user"),
                    properties.getProperty("password"));
                if (temp != null) {
                    current = temp;
                    newPath = collectionPath.toCollectionPathURI();
                    if (startGUI) {
                        frame.setPath(collectionPath.toCollectionPathURI());
                    }
                } else {
                    messageln("no such collection.");
                }
                getResources();
            } else if (args[0].equalsIgnoreCase("cp")) {
                if (args.length != 3) {
                    messageln("cp requires two arguments.");
                    return true;
                }
                final XmldbURI src,dest;
                try {
                    src = URIUtils.encodeXmldbUriFor(args[1]);
                    dest = URIUtils.encodeXmldbUriFor(args[2]);
                } catch(final URISyntaxException e) {
                    errorln("could not parse collection name into a valid URI: "+e.getMessage());
                    return false;
                }
                copy(src,dest);
                getResources();
                
            } else if (args[0].equalsIgnoreCase("edit")) {
                if (args.length == 2) {
                    final XmldbURI resource;
                    try {
                    	resource = URIUtils.encodeXmldbUriFor(args[1]);
                    } catch(final URISyntaxException e) {
                        errorln("could not parse resource name into a valid URI: "+e.getMessage());
                        return false;
                    }
                    editResource(resource);
                } else {
                    messageln("Please specify a resource.");
                }
            } else if (args[0].equalsIgnoreCase("get")) {
                if (args.length < 2) {
                    System.err.println("wrong number of arguments.");
                    return true;
                }
                final XmldbURI resource;
                try {
                    resource = URIUtils.encodeXmldbUriFor(args[1]);
                } catch(final URISyntaxException e) {
                    errorln("could not parse resource name into a valid URI: "+e.getMessage());
                    return false;
                }
                Resource res = retrieve(resource);
                // display document
                if (res != null) {
                    final String data;
                    if (res.getResourceType().equals("XMLResource")) {
                        data = (String) res.getContent();
                    } else {
                        data = new String((byte[]) res.getContent());
                    }
                    if (startGUI) {
                        frame.setEditable(false);
                        frame.display(data);
                        frame.setEditable(true);
                    } else {
                        final String content = data;
                        more(content);
                    }
                }
                return true;
            } else if (args[0].equalsIgnoreCase("find")) {
                // search
                if (args.length < 2) {
                    messageln("no query argument found.");
                    return true;
                }
                messageln(args[1]);
                final long start = System.currentTimeMillis();
                result = find(args[1]);
                if (result == null) {
                    messageln("nothing found");
                } else {
                    messageln("found " + result.getSize() + " hits in "
                            + (System.currentTimeMillis() - start) + "ms.");
                }
                
                nextInSet = 1;
                
            } else if (args[0].equalsIgnoreCase("run")) {
                if (args.length < 2) {
                    messageln("please specify a query file.");
                    return true;
                }
                try {
                    final BufferedReader reader = new BufferedReader(new FileReader(args[1]));
                    final StringBuilder buf = new StringBuilder();
                    String nextLine;
                    while ((nextLine = reader.readLine()) != null) {
                        buf.append(nextLine);
                        buf.append(EOL);
                    }
                    args[1] = buf.toString();
                    final long start = System.currentTimeMillis();
                    result = find(args[1]);
                    if (result == null) {
                        messageln("nothing found");
                    } else {
                        messageln("found " + result.getSize() + " hits in "
                                + (System.currentTimeMillis() - start) + "ms.");
                    }
                    
                    nextInSet = 1;
                } catch (final Exception e) {
                    errorln("An error occurred: " + e.getMessage());
                }
            } else if (args[0].equalsIgnoreCase("show")) {
                // show search results
                if (result == null) {
                    messageln("no result set.");
                    return true;
                }
                try {
                    int start = nextInSet;
                    int count = 1;
                    if (args.length > 1) {
                        start = Integer.parseInt(args[1]);
                    }
                    
                    if (args.length > 2) {
                        count = Integer.parseInt(args[2]);
                    }
                    
                    final int s = (int) result.getSize();
                    if (start < 1 || start > s) {
                        messageln("start offset out of range");
                        return true;
                    }
                    --start;
                    if (start + count > s) {
                        count = s - start;
                    }
                    
                    nextInSet = start + count + 1;
                    for (int i = start; i < start + count; i++) {
                        final Resource r = result.getResource(i);
                        if (startGUI) {
                            frame.display((String) r.getContent());
                        } else {
                            more((String) r.getContent());
                        }
                    }
                    messageln("displayed items " + (start + 1) + " to "
                            + (start + count) + " of " + result.getSize());
                } catch (final NumberFormatException nfe) {
                    errorln("wrong argument");
                    return true;
                }
                
            } else if (args[0].equalsIgnoreCase("mkcol")) {
                // create collection
                if (args.length < 2) {
                    messageln("missing argument.");
                    return true;
                }
                final XmldbURI collUri;
                try {
                    collUri = URIUtils.encodeXmldbUriFor(args[1]);
                } catch(final URISyntaxException e) {
                    errorln("could not parse collection name into a valid URI: "+e.getMessage());
                    return false;
                }
                final CollectionManagementServiceImpl mgtService = (CollectionManagementServiceImpl) current
                        .getService("CollectionManagementService", "1.0");
                final Collection newCollection = mgtService.createCollection(collUri);
                if (newCollection == null) {
                    messageln("could not create collection.");
                } else {
                    messageln("created collection.");
                }
                
                // re-read current collection
                current = DatabaseManager.getCollection(properties
                        .getProperty("uri")
                        + path, properties.getProperty("user"), properties
                        .getProperty("password"));
                getResources();
                
            } else if (args[0].equalsIgnoreCase("put")) {
                // put a document or directory into the database
                if (args.length < 2) {
                    messageln("missing argument.");
                    return true;
                }
                final boolean r = parse(args[1]);
                getResources();
                return r;
                
            } else if (args[0].equalsIgnoreCase("putzip")) {
                // put the contents of a zip archive into the database
                if (args.length < 2) {
                    messageln("missing argument.");
                    return true;
                }
                final boolean r = parseZip(args[1]);
                getResources();
                return r;
                
            } else if (args[0].equalsIgnoreCase("putgz")) {
                // put the contents of a zip archive into the database
                if (args.length < 2) {
                    messageln("missing argument.");
                    return true;
                }
                final boolean r = parseGZip(args[1]);
                getResources();
                return r;
                
            } else if (args[0].equalsIgnoreCase("blob")) {
                // put a document or directory into the database
                if (args.length < 2) {
                    messageln("missing argument.");
                    return true;
                }
                storeBinary(args[1]);
                getResources();
                
            } else if (args[0].equalsIgnoreCase("rm")) {
                // remove document
                if (args.length < 2) {
                    messageln("missing argument.");
                    return true;
                }
                
                remove(args[1]);
                
                // re-read current collection
                current = DatabaseManager.getCollection(properties
                        .getProperty("uri")
                        + path, properties.getProperty("user"), properties
                        .getProperty("password"));
                getResources();
                
            } else if (args[0].equalsIgnoreCase("rmcol")) {
                // remove collection
                if (args.length < 2) {
                    messageln("wrong argument count.");
                    return true;
                }
                final XmldbURI collUri;
                try {
                    collUri = URIUtils.encodeXmldbUriFor(args[1]);
                } catch(final URISyntaxException e) {
                    errorln("could not parse collection name into a valid URI: "+e.getMessage());
                    return false;
                }
                rmcol(collUri);
                // re-read current collection
                current = DatabaseManager.getCollection(properties
                        .getProperty("uri")
                        + path, properties.getProperty("user"), properties
                        .getProperty("password"));
                getResources();
            } else if (args[0].equalsIgnoreCase("adduser")) {
                if (args.length < 2) {
                    System.err.println("Usage: adduser name");
                    return true;
                }
                if (startGUI) {
                    messageln("command not supported in GUI mode. Please use the \"Edit users\" menu option.");
                    return true;
                }
                try {
                    final UserManagementService mgtService = (UserManagementService) current
                            .getService("UserManagementService", "1.0");
                    
                    String p1;
                    String p2;
                    while (true) {
                        p1 = console.readLine("password: ", new Character('*'));
                        p2 = console.readLine("re-enter password: ", new Character('*'));
                        if (p1.equals(p2)) {
                            break;
                        }
                        System.out.println(EOL + "entered passwords differ. Try again...");
                        
                    }
                    final UserAider user = new UserAider(args[1]);
                    user.setPassword(p1);
                    final String groups = console.readLine("enter groups: ");
                    final StringTokenizer tok = new StringTokenizer(groups, " ,");
                    while (tok.hasMoreTokens()) {
                        final String group = tok.nextToken();
                        if (group.length() > 0) {
                            user.addGroup(group);
                        }
                    }
                    
                    mgtService.addAccount(user);
                    System.out.println("user " + user + " created.");
                } catch (final Exception e) {
                    System.out.println("ERROR: " + e.getMessage());
                    e.printStackTrace();
                }
            } else if (args[0].equalsIgnoreCase("users")) {
                final UserManagementService mgtService = (UserManagementService) current
                        .getService("UserManagementService", "1.0");
                final Account users[] = mgtService.getAccounts();
                System.out.println("User\t\tGroups");
                System.out.println("-----------------------------------------");
                for (int i = 0; i < users.length; i++) {
                    System.out.print(users[i].getName() + "\t\t");
                    final String[] groups = users[i].getGroups();
                    for (int j = 0; j < groups.length; j++) {
                        System.out.print(groups[j]);
                        if (j + 1< groups.length) {
                            System.out.print(", ");
                        }
                    }
                    System.out.println();
                }
            } else if (args[0].equalsIgnoreCase("passwd")) {
                if (startGUI) {
                    messageln("command not supported in GUI mode. Please use the \"Edit users\" menu option.");
                    return true;
                }
                if (args.length < 2) {
                    System.out.println("Usage: passwd username");
                    return true;
                }
                try {
                    final UserManagementService mgtService = (UserManagementService) current
                            .getService("UserManagementService", "1.0");
                    final Account user = mgtService.getAccount(args[1]);
                    if (user == null) {
                        System.out.println("no such user.");
                        return true;
                    }
                    String p1;
                    String p2;
                    while (true) {
                        p1 = console.readLine("password: ", new Character('*'));
                        p2 = console.readLine("re-enter password: ", new Character('*'));
                        if (p1.equals(p2)) {
                            break;
                        }
                        System.out.println(EOL + "entered passwords differ. Try again...");
                    }
                    user.setPassword(p1);
                    mgtService.updateAccount(user);
                    properties.setProperty("password", p1);
                } catch (final Exception e) {
                    System.err.println("ERROR: " + e.getMessage());
                }
            } else if (args[0].equalsIgnoreCase("chmod")) {
                if (args.length < 2) {
                    System.out.println("Usage: chmod [resource] mode");
                    return true;
                }
                
                final Collection temp;
                if (args.length == 3) {
                    System.out.println("trying collection: " + args[1]);
                    temp = current.getChildCollection(args[1]);
                    if (temp == null) {
                        System.out.println(EOL + "trying resource: " + args[1]);
                        final Resource r = current.getResource(args[1]);
                        if (r != null) {
                            final UserManagementService mgtService = (UserManagementService) current
                                .getService("UserManagementService", "1.0");
                            mgtService.chmod(r, args[2]);
                        } else {
                            System.err.println("Resource " + args[1]
                                    + " not found.");
                        }
                    } else {
                        final UserManagementService mgtService = (UserManagementService) temp
                            .getService("UserManagementService", "1.0");
                        mgtService.chmod(args[2]);
                    }
                } else {
                    final UserManagementService mgtService = (UserManagementService) current
                        .getService("UserManagementService", "1.0");
                    mgtService.chmod(args[1]);
                }
                // re-read current collection
                current = DatabaseManager.getCollection(properties
                    .getProperty("uri")
                    + path, properties.getProperty("user"), properties
                    .getProperty("password"));
                getResources();
            } else if (args[0].equalsIgnoreCase("chown")) {
                if (args.length < 3) {
                    System.out
                            .println("Usage: chown username group [resource]");
                    return true;
                }
                
                final Collection temp;
                if (args.length == 4) {
                    temp = current.getChildCollection(args[3]);
                } else {
                    temp = current;
                }
                if (temp != null) {
                    final UserManagementService mgtService = (UserManagementService) temp
                            .getService("UserManagementService", "1.0");
                    final Account u = mgtService.getAccount(args[1]);
                    if (u == null) {
                        System.out.println("unknown user");
                        return true;
                    }
                    mgtService.chown(u, args[2]);
                    System.out.println("owner changed.");
                    getResources();
                    return true;
                }
                final Resource res = current.getResource(args[3]);
                if (res != null) {
                    final UserManagementService mgtService = (UserManagementService) current
                            .getService("UserManagementService", "1.0");
                    final Account u = mgtService.getAccount(args[1]);
                    if (u == null) {
                        System.out.println("unknown user");
                        return true;
                    }
                    mgtService.chown(res, u, args[2]);
                    getResources();
                    return true;
                }
                System.err.println("Resource " + args[3] + " not found.");
                
            } else if (args[0].equalsIgnoreCase("lock") || args[0].equalsIgnoreCase("unlock")) {
                if(args.length < 2) {
                    messageln("Usage: lock resource");
                    return true;
                }
                final Resource res = current.getResource(args[1]);
                if (res != null) {
                    final UserManagementService mgtService = (UserManagementService)
                    current.getService("UserManagementService", "1.0");
                    final Account user = mgtService.getAccount(properties.getProperty("user", "guest"));
                    if(args[0].equalsIgnoreCase("lock")) {
                        mgtService.lockResource(res, user);
                    } else {
                        mgtService.unlockResource(res);
                    }
                }
                
            } else if (args[0].equalsIgnoreCase("elements")) {
                System.out.println("Element occurrences in collection "
                        + current.getName());
                System.out
                        .println("--------------------------------------------"
                        + "-----------");
                final IndexQueryService service = (IndexQueryService) current
                        .getService("IndexQueryService", "1.0");
                final Occurrences[] elements = service.getIndexedElements(true);
                for (int i = 0; i < elements.length; i++) {
                    System.out
                            .println(formatString(elements[i].getTerm().toString(),
                            Integer.toString(elements[i]
                            .getOccurrences()), 50));
                }
                return true;
                
            } else if (args[0].equalsIgnoreCase("terms")) {
                if (args.length < 3) {
                    System.out
                            .println("Usage: terms [xpath] sequence-start sequence-end");
                    return true;
                }
                final IndexQueryService service = (IndexQueryService) current
                        .getService("IndexQueryService", "1.0");
                Occurrences[] terms;
                if (args.length == 3) {
                    terms = service.scanIndexTerms(args[1], args[2], true);
                } else {
                    terms = service.scanIndexTerms(args[1], args[2], args[3]);
                }
                System.out.println("Element occurrences in collection " + current.getName());
                System.out.println("-------------------------------------------------------");
                for (int i = 0; i < terms.length; i++) {
                    System.out.println(formatString(terms[i].getTerm().toString(), Integer.toString(terms[i].getOccurrences()), 50));
                }
            } else if (args[0].equalsIgnoreCase("xupdate")) {
                if (startGUI) {
                    messageln("command not supported in GUI mode.");
                    return true;
                }
                String lastLine, command = "";
                try {
                    while (true) {
                        lastLine = console.readLine("| ");
                        if (lastLine == null || lastLine.length() == 0) {
                            break;
                        }
                        command += lastLine;
                    }
                } catch (final EOFException e) {
                    //TODO report error?
                } catch (final IOException e) {
                    //TODO report error?
                }
                final String xupdate = "<xu:modifications version=\"1.0\" "
                        + "xmlns:xu=\"http://www.xmldb.org/xupdate\">"
                        + command + "</xu:modifications>";
                final XUpdateQueryService service = (XUpdateQueryService) current
                        .getService("XUpdateQueryService", "1.0");
                final long mods = service.update(xupdate);
                System.out.println(mods + " modifications processed.");
                
            } else if (args[0].equalsIgnoreCase("map")) {
                final StringTokenizer tok = new StringTokenizer(args[1], "= ");
                final String prefix;
                if (args[1].startsWith("=")) {
                    prefix = "";
                } else {
                    if (tok.countTokens() < 2) {
                        messageln("please specify a namespace/prefix mapping as: prefix=namespaceURI");
                        return true;
                    }
                    prefix = tok.nextToken();
                }
                final String uri = tok.nextToken();
                namespaceMappings.put(prefix, uri);
                
            } else if (args[0].equalsIgnoreCase("set")) {
                if (args.length == 1) {
                    properties.list(System.out);
                } else {
                    try {
                        final StringTokenizer tok = new StringTokenizer(args[1], "= ");
                        if (tok.countTokens() < 2) {
                            System.err.println("please specify a key=value pair");
                            return true;
                        }
                        final String key = tok.nextToken();
                        final String val = tok.nextToken();
                        properties.setProperty(key, val);
                        current.setProperty(key, val);
                        getResources();
                    } catch (final Exception e) {
                        System.err.println("Exception: " + e.getMessage());
                    }
                }
            } else if (args[0].equalsIgnoreCase("shutdown")) {
                final DatabaseInstanceManager mgr = (DatabaseInstanceManager) current
                    .getService("DatabaseInstanceManager", "1.0");
                if (mgr == null) {
                    messageln("Service is not available");
                    return true;
                }
                mgr.shutdown();
                return true;
            } else if (args[0].equalsIgnoreCase("help") || args[0].equals("?")) {
                displayHelp();
            } else if (args[0].equalsIgnoreCase("quit")) {
                return false;
                //XXX:make it pluggable
            } else if (havePluggableCommands) {
                CollectionManagementServiceImpl mgtService = (CollectionManagementServiceImpl) current.getService("CollectionManagementService", "1.0");
                mgtService.runCommand(args);
                //****************************************************************
            } else {
                messageln("unknown command");
                return true;
            }
            path = newPath;
            return true;
        } catch (final Throwable e) {
            if (startGUI) {
                ClientFrame.showErrorMessage(getExceptionMessage(e), e);
            } else {
                errorln(getExceptionMessage(e));
                e.printStackTrace();
            }
            return true;
        }
    }
    
    /**
     * @param args
     */
    private void editResource(final XmldbURI name) {
        try {
            final Resource doc = retrieve(name, properties.getProperty(OutputKeys.INDENT, "yes")); //$NON-NLS-1$
            final DocumentView view = new DocumentView(this, name, doc, properties);
            view.setSize(new Dimension(640, 400));
            view.viewDocument();
        } catch (final XMLDBException ex) {
            errorln("XMLDB error: " + ex.getMessage());
        }
    }
        
    private ResourceSet find(String xpath) throws XMLDBException {
        if (xpath.substring(xpath.length() - EOL.length()).equals(EOL)) {
            xpath = xpath.substring(0, xpath.length() - EOL.length());
        }
        if (traceWriter != null) {
            try {
                traceWriter.write("<query>");
                traceWriter.write(xpath);
                traceWriter.write("</query>");
                traceWriter.write(EOL);
            } catch (final IOException e) {
                //TODO report error?
            }
        }
        String sortBy = null;
        final int p = xpath.indexOf(" sort by ");
        if (p != Constants.STRING_NOT_FOUND) {
            final String xp = xpath.substring(0, p);
            sortBy = xpath.substring(p + " sort by ".length());
            xpath = xp;
            System.out.println("XPath =   " + xpath);
            System.out.println("Sort-by = " + sortBy);
        }
        
        final XPathQueryServiceImpl service = (XPathQueryServiceImpl) current.getService("XPathQueryService", "1.0");
        service.setProperty(OutputKeys.INDENT, properties.getProperty("indent"));
        service.setProperty(OutputKeys.ENCODING, properties.getProperty("encoding"));

        for(final Map.Entry<String, String> mapping : namespaceMappings.entrySet()) {
            service.setNamespace(mapping.getKey(), mapping.getValue());
        }
        
        return (sortBy == null) ? service.query(xpath) : service.query(xpath, sortBy);
    }
    
    /** unused, for testing purposes ?? */
    private void testQuery(final String queryFile) {
        try {
            final File f = new File(queryFile);
            if (!f.canRead()) {
                System.err.println("can't read query file: " + queryFile);
                return;
            }
            final BufferedReader reader = new BufferedReader(new FileReader(f));
            String line;
            final ArrayList<String> queries = new ArrayList<String>(10);
            QueryThread thread = null;
            while ((line = reader.readLine()) != null) {
                queries.add(line);
            }
            
            for (int i = 0; i < PARALLEL_THREADS; i++) {
                thread = new QueryThread(queries);
                thread.setName("QueryThread" + i);
                thread.start();
            }
            try {
                thread.join();
            } catch (InterruptedException e) {
            }
        } catch (final FileNotFoundException e) {
            System.err.println("ERROR: " + e);
        } catch (final IOException e) {
            System.err.println("ERROR: " + e);
        }
    }
    
    private class QueryThread extends Thread {
        
        private final ArrayList<String> queries;
        
        public QueryThread(final ArrayList<String> queries) {
            this.queries = queries;
        }
        
        @Override
        public void run() {
            try {
                // Collection collection =
                DatabaseManager.getCollection(properties.getProperty("uri") + path, properties.getProperty("user"), properties.getProperty("password"));
                final XPathQueryService service = (XPathQueryService) current.getService("XPathQueryService", "1.0");
                service.setProperty(OutputKeys.INDENT, "yes");
                service.setProperty(OutputKeys.ENCODING, properties.getProperty("encoding"));
                final Random r = new Random(System.currentTimeMillis());
                for (int i = 0; i < 10; i++) {
                    final String query = queries.get(r.nextInt(queries.size()));
                    System.out.println(getName() + " query: " + query);
                    final ResourceSet result = service.query(query);
                    System.out.println(getName() + " found: " + result.getSize());
                }
            } catch (final XMLDBException e) {
                System.err.println("ERROR: " + e.getMessage());
            }
            System.out.println(getName() + " finished.");
        }
    }
    
    protected final Resource retrieve(final XmldbURI resource) throws XMLDBException {
        return retrieve(resource, properties.getProperty("indent"));
    }
    
    protected final Resource retrieve(final XmldbURI resource, final String indent) throws XMLDBException {
        final Resource res = current.getResource(resource.toString());
        if (res == null) {
            messageln("document not found.");
            return null;
        } 
        return res;
    }
    
    private void remove(final String pattern) throws XMLDBException {
        final Collection collection = current;
        if (pattern.startsWith("/")) {
            System.err.println("path pattern should be relative to current collection");
            return;
        }
        final Resource resources[];
        final Resource res = collection.getResource(pattern);
        if (res == null) {
            resources = CollectionScanner.scan(collection, "", pattern);
        } else {
            resources = new Resource[1];
            resources[0] = res;
        }
        for (int i = 0; i < resources.length; i++) {
            message("removing document " + resources[i].getId() + " ...");
            final Collection parent = resources[i].getParentCollection();
            parent.removeResource(resources[i]);
            messageln("done.");
        }
    }
    
    private void xupdate(final String resource, final String filename) throws XMLDBException, IOException {
        final File file = new File(filename);
        if (!(file.exists() && file.canRead())) {
            messageln("cannot read file " + filename);
            return;
        }
        final String commands = XMLUtil.readFile(file, "UTF-8");
        final XUpdateQueryService service = (XUpdateQueryService) current.getService("XUpdateQueryService", "1.0");
        final long modifications;
        if (resource == null) {
            modifications = service.update(commands);
        } else {
            modifications = service.updateResource(resource, commands);
        }
        messageln(modifications + " modifications processed " + "successfully.");
    }
    
    private void rmcol(final XmldbURI collection) throws XMLDBException {
    	final CollectionManagementServiceImpl mgtService = (CollectionManagementServiceImpl) current.getService("CollectionManagementService", "1.0");
        message("removing collection " + collection + " ...");
        mgtService.removeCollection(collection);
        messageln("done.");
    }
    
    private void copy(final XmldbURI source, XmldbURI destination) throws XMLDBException {
        final CollectionManagementServiceImpl mgtService = (CollectionManagementServiceImpl)current.getService("CollectionManagementService", "1.0");
        final XmldbURI destName = destination.lastSegment();
        final Collection destCol = resolveCollection(destination);
        if(destCol == null) {
            if(destination.numSegments()==1) {
                destination = XmldbURI.create(current.getName());
            } else {
                destination = destination.removeLastSegment();
            }
        }
        final Resource srcDoc = resolveResource(source);
        if(srcDoc != null) {
            final XmldbURI resourcePath = XmldbURI.create(srcDoc.getParentCollection().getName()).append(srcDoc.getId());
            messageln("Copying resource '" + resourcePath + "' to '" + destination + "'");
            mgtService.copyResource(resourcePath, destination, destName);
        } else {
            messageln("Copying collection '" + source + "' to '" + destination + "'");
            mgtService.copy(source, destination, destName);
        }
    }
    
    private void reindex() throws XMLDBException {
        final IndexQueryService service = (IndexQueryService)
        current.getService("IndexQueryService", "1.0");
        message("reindexing collection " + current.getName());
        service.reindexCollection();
        messageln("done.");
    }
    
    private void storeBinary(final String fileName) throws XMLDBException {
        final File file = new File(fileName);
        if (file.canRead()) {
            final MimeType mime = MimeTable.getInstance().getContentTypeFor(file.getName());
            final BinaryResource resource = (BinaryResource) current.createResource(file.getName(), "BinaryResource");
            resource.setContent(file);
            ((EXistResource)resource).setMimeType(mime == null ? "application/octet-stream" : mime.getName());
            current.storeResource(resource);
        }
    }
    
    private synchronized boolean findRecursive(final Collection collection, final File dir, final XmldbURI base) throws XMLDBException {
        final File files[] = dir.listFiles();
        Collection c;
        Resource document;
        CollectionManagementServiceImpl mgtService;
        //The XmldbURIs here aren't really used...
        XmldbURI next;
        MimeType mimeType;
        for (int i = 0; i < files.length; i++) {
            next = base.append(files[i].getName());
            try {
                if (files[i].isDirectory()) {
                    messageln("entering directory " + files[i].getAbsolutePath());
                    c = collection.getChildCollection(files[i].getName());
                    if (c == null) {
                        mgtService = (CollectionManagementServiceImpl) collection.getService("CollectionManagementService", "1.0");
                        c = mgtService.createCollection(URIUtils.encodeXmldbUriFor(files[i].getName()));
                    }
                    
                    if (c instanceof Observable && verbose) {
                        ProgressObserver observer = new ProgressObserver();
                        ((Observable) c).addObserver(observer);
                    }
                    findRecursive(c, files[i], next);
                } else {
                    final long start1 = System.currentTimeMillis();
                    mimeType = MimeTable.getInstance().getContentTypeFor(files[i].getName());
                    if(mimeType == null) {
                        messageln("File " + files[i].getName() + " has an unknown suffix. Cannot determine file type.");
			mimeType = MimeType.BINARY_TYPE;
                    }
                    message("storing document " + files[i].getName() + " (" + i + " of " + files.length + ") " + "...");
                    document = collection.createResource(URIUtils.urlEncodeUtf8(files[i].getName()), mimeType.getXMLDBType());
                    document.setContent(files[i]);
                    ((EXistResource)document).setMimeType(mimeType.getName());
                    collection.storeResource(document);
                    ++filesCount;
                    messageln(" " + files[i].length() + " bytes in " + (System.currentTimeMillis() - start1) + "ms.");
                }
            } catch (final URISyntaxException e) {
                errorln("uri syntax exception parsing " + files[i].getAbsolutePath() + ": " + e.getMessage());
            }
        }
        return true;
    }
    
    /** stores given Resource
     * @param fileName simple file or directory
     * @throws XMLDBException
     */
    protected synchronized boolean parse(String fileName) throws XMLDBException {
        //TODO : why is this test for ? File should make it, shouldn't it ? -pb
        fileName = fileName.replace('/', File.separatorChar).replace('\\',File.separatorChar);
        final File file = new File(fileName);
        Resource document;
        // String xml;
        
        if (current instanceof Observable && verbose) {
            ProgressObserver observer = new ProgressObserver();
            ((Observable) current).addObserver(observer);
        }
        
        final File files[];
        if (file.canRead()) {
            // TODO, same logic as for the graphic client
            if (file.isDirectory()) {
                if (recurseDirs) {
                    filesCount = 0;
                    long start = System.currentTimeMillis();
                    final boolean result = findRecursive(current, file, path);
                    messageln("storing " + filesCount + " files took " + ((System.currentTimeMillis() - start) / 1000) + "sec.");
                    return result;
                } 
                files = file.listFiles();
            } else {
                files = new File[1];
                files[0] = file;
            }
        } else {
            files = DirectoryScanner.scanDir(fileName);
        }
        
        final long start0 = System.currentTimeMillis();
        long bytes = 0;
        MimeType mimeType;
        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                continue;
            }
            final long start = System.currentTimeMillis();
            mimeType = MimeTable.getInstance().getContentTypeFor(files[i].getName());
            if(mimeType == null) {
                mimeType = MimeType.BINARY_TYPE;
            }
            document = current.createResource(files[i].getName(), mimeType.getXMLDBType());
            message("storing document " + files[i].getName() + " (" + (i + 1) + " of " + files.length + ") ...");
            document.setContent(files[i]);
            ((EXistResource)document).setMimeType(mimeType.getName());
            current.storeResource(document);
            messageln("done.");
            messageln("parsing " + files[i].length() + " bytes took " + (System.currentTimeMillis() - start) + "ms." + EOL);
            bytes += files[i].length();
        }
        messageln("parsed " + bytes + " bytes in " + (System.currentTimeMillis() - start0) + "ms.");
        return true;
    }
    
    
    private synchronized boolean findGZipRecursive(final Collection collection, final File dir, final XmldbURI base) throws XMLDBException {
        final File files[] = dir.listFiles();
        Collection c;
        Resource document;
        CollectionManagementServiceImpl mgtService;
        //The XmldbURIs here aren't really used...
        XmldbURI next;
        MimeType mimeType;
        for (int i = 0; i < files.length; i++) {
            next = base.append(files[i].getName());
            try {
                if (files[i].isDirectory()) {
                    messageln("entering directory " + files[i].getAbsolutePath());
                    c = collection.getChildCollection(files[i].getName());
                    if (c == null) {
                        mgtService = (CollectionManagementServiceImpl) collection.getService("CollectionManagementService", "1.0");
                        c = mgtService.createCollection(URIUtils.encodeXmldbUriFor(files[i].getName()));
                    }
                    if (c instanceof Observable && verbose) {
                        ProgressObserver observer = new ProgressObserver();
                        ((Observable) c).addObserver(observer);
                    }
                    findGZipRecursive(c, files[i], next);
                } else {
                    final long start1 = System.currentTimeMillis();
                    final String compressedName = files[i].getName();
                    String localName = compressedName;
                    final String[] cSuffix = {".gz",".Z"};
                    boolean isCompressed = false;
                    for(int isuf = 0; isuf < cSuffix.length; isuf++) {
                    	final String suf = cSuffix[isuf];
                    	if(localName.endsWith(suf)) {
                    		// Removing compressed prefix to validate
                    		localName=compressedName.substring(0, localName.length()-suf.length());
                    		isCompressed=true;
                    		break;
                    	}
                    }
                    mimeType = MimeTable.getInstance().getContentTypeFor(localName);
                    if(mimeType == null) {
                        messageln("File " + compressedName + " has an unknown suffix. Cannot determine file type.");
                    	mimeType = MimeType.BINARY_TYPE;
		    }
                    message("storing document " + compressedName + " (" + i + " of " + files.length + ") " + "...");
                    document = collection.createResource(URIUtils.urlEncodeUtf8(compressedName), mimeType.getXMLDBType());
                    document.setContent(isCompressed?new GZIPInputSource(files[i]):files[i]);
                    ((EXistResource)document).setMimeType(mimeType.getName());
                    collection.storeResource(document);
                    ++filesCount;
                    messageln(" " + files[i].length() + (isCompressed?" compressed":"") + " bytes in "
                    	    + (System.currentTimeMillis() - start1) + "ms.");
                }
            } catch (final URISyntaxException e) {
                errorln("uri syntax exception parsing " + files[i].getAbsolutePath() + ": " + e.getMessage());
            }
        }
        return true;
    }
    
    /** stores given Resource
     * @param fileName simple file or directory
     * @throws XMLDBException
     */
    protected synchronized boolean parseGZip(String fileName) throws XMLDBException {
        //TODO : why is this test for ? Fileshould make it, shouldn't it ? -pb
        fileName = fileName.replace('/', File.separatorChar).replace('\\',
                File.separatorChar);
        final File file = new File(fileName);
        Resource document;
        // String xml;
        if (current instanceof Observable && verbose) {
            ProgressObserver observer = new ProgressObserver();
            ((Observable) current).addObserver(observer);
        }
        final File files[];
        if (file.canRead()) {
            // TODO, same logic as for the graphic client
            if (file.isDirectory()) {
                if (recurseDirs) {
                    filesCount = 0;
                    long start = System.currentTimeMillis();
                    boolean result = findGZipRecursive(current, file, path);
                    messageln("storing " + filesCount + " compressed files took "
                            + ((System.currentTimeMillis() - start) / 1000)
                            + "sec.");
                    return result;
                } 
                files = file.listFiles();
            } else {
                files = new File[1];
                files[0] = file;
            }
        } else {
            files = DirectoryScanner.scanDir(fileName);
        }
        
        final long start0 = System.currentTimeMillis();
        long bytes = 0;
        MimeType mimeType;
        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                continue;
            }
            final long start = System.currentTimeMillis();
            final String compressedName = files[i].getName();
            String localName = compressedName;
            final String[] cSuffix = {".gz",".Z"};
            boolean isCompressed = false;
            for(int isuf = 0; isuf < cSuffix.length; isuf++) {
            	final String suf = cSuffix[isuf];
            	if(localName.endsWith(suf)) {
                    // Removing compressed prefix to validate
                    localName = compressedName.substring(0, localName.length()-suf.length());
                    isCompressed = true;
                    break;
            	}
            }
            mimeType = MimeTable.getInstance().getContentTypeFor(localName);
            if(mimeType == null) {
                mimeType = MimeType.BINARY_TYPE;
            }
            document = current.createResource(compressedName,mimeType.getXMLDBType());
            message("storing document " + compressedName + " (" + (i + 1)
            + " of " + files.length + ") ...");
            document.setContent(isCompressed?new GZIPInputSource(files[i]):files[i]);
            ((EXistResource)document).setMimeType(mimeType.getName());
            current.storeResource(document);
            messageln("done.");
            messageln("parsing " + files[i].length() + (isCompressed?" compressed":"") + " bytes took "
                    + (System.currentTimeMillis() - start) + "ms." + EOL);
            bytes += files[i].length();
        }
        messageln("parsed " + bytes + " compressed bytes in "
                + (System.currentTimeMillis() - start0) + "ms.");
        return true;
    }
    
    /** stores given Resource
     * @param fileName simple file or directory
     * @throws XMLDBException
     */
    protected synchronized boolean parseZip(String fileName) throws XMLDBException {
        fileName = fileName.replace('/', File.separatorChar).replace('\\', File.separatorChar);
        
        try {
            final ZipFile zfile = new ZipFile(fileName);
            Resource document;
            // String xml;
            if (current instanceof Observable && verbose) {
                ProgressObserver observer = new ProgressObserver();
                ((Observable) current).addObserver(observer);
            }

            final long start0 = System.currentTimeMillis();
            long bytes = 0;
            MimeType mimeType;
            final Enumeration<? extends ZipEntry> e = zfile.entries();
            int number = 0;
	    	
            Collection base = current;
            String baseStr="";
            while(e.hasMoreElements()) {
                number++;
                final ZipEntry ze=e.nextElement();
                final String zeName = ze.getName().replace('\\','/');
                final String[] pathSteps = zeName.split("/");
                String currStr = pathSteps[0];
                for(int i=1;i<pathSteps.length-1;i++) {
                    currStr += "/"+pathSteps[i];
                }
                if(!baseStr.equals(currStr)) {
                    base=current;
                    for(int i=0;i<pathSteps.length-1;i++) {
                        Collection c = base.getChildCollection(pathSteps[i]);
                        if (c == null) {
                            final CollectionManagementServiceImpl mgtService = (CollectionManagementServiceImpl) base.getService("CollectionManagementService","1.0");
                            c = mgtService.createCollection(URIUtils.encodeXmldbUriFor(pathSteps[i]));
                        }
                        base=c;
                    }
                    if (base instanceof Observable && verbose) {
                        final ProgressObserver observer = new ProgressObserver();
                        ((Observable) base).addObserver(observer);
                    }
                    baseStr=currStr;
                    messageln("entering directory " + baseStr);
                }
                if (!ze.isDirectory()) {
                    String localName=pathSteps[pathSteps.length-1];
                    final long start = System.currentTimeMillis();
                    mimeType = MimeTable.getInstance().getContentTypeFor(localName);
                    if(mimeType == null) {
                        mimeType = MimeType.BINARY_TYPE;
                    }
                    document = base.createResource(localName,mimeType.getXMLDBType());
                    message("storing Zip-entry document " + localName + " (" + (number)
                                    + " of " + zfile.size() + ") ...");
                    document.setContent(new ZipEntryInputSource(zfile,ze));
                    ((EXistResource)document).setMimeType(mimeType.getName());
                    base.storeResource(document);
                    messageln("done.");
                    messageln("parsing " + ze.getSize() + " bytes took "
                                    + (System.currentTimeMillis() - start) + "ms." + EOL);
                    bytes += ze.getSize();
                }
            }
            messageln("parsed " + bytes + " bytes in "
                + (System.currentTimeMillis() - start0) + "ms.");
        } catch (final URISyntaxException e) {
            errorln("uri syntax exception parsing a ZIP entry from " + fileName + ": " + e.getMessage());
        } catch (final IOException e) {
            errorln("could not parse ZIP file " + fileName+": "+e.getMessage());
        }
        return true;
    }
    
    /**
     * 
     * Method called by the store Dialog
     * 
     * @param files  : selected
     * @param upload : GUI object 
     * @throws XMLDBException
     */
    protected synchronized boolean parse(final File[] files, final UploadDialog upload) throws XMLDBException {
        final Collection uploadRootCollection = current;
        if (!upload.isVisible()) {
            upload.setVisible(true);
        }
        
        if (uploadRootCollection instanceof Observable) {
            ((Observable) uploadRootCollection).addObserver(upload.getObserver());
        }
        upload.setTotalSize(calculateFileSizes(files));
        for (int i = 0; i < files.length; i++) {
            if (upload.isCancelled()) {
                break;
            }
            // should replace the lines above
            store(uploadRootCollection, files[i], upload);
        }
        if (uploadRootCollection instanceof Observable) {
            ((Observable) uploadRootCollection).deleteObservers();
        }
        upload.uploadCompleted();
        return true;
    }
    
    private long calculateFileSizes(final File[] files) throws XMLDBException {
        long size = 0;
        for (int i = 0; i < files.length; i++) {
            if (!files[i].canRead()) {
                continue;
            }
            if (files[i].isDirectory()) {
                size += calculateFileSizes(files[i].listFiles());
            }
            else {
                size += files[i].length();
            }
        }
        return size;
    }
    
    /*
     * This is for a future GUI implementation of ZIP archive loads
     */
    /*private long calculateFileSizes(final ZipFile file) throws XMLDBException {
    	final Enumeration<? extends ZipEntry> e = file.entries();
        long size = 0;
    	while(e.hasMoreElements()) {
            final ZipEntry ze=(ZipEntry)e.nextElement();
            if(!ze.isDirectory()) {
                    size += ze.getSize();
            }
    	}
    	
        return size;
    }*/
    
    /**
     * Pass to this method a java file object 
     * (may be a file or a directory), GUI object
     * will create relative collections or resources
     * recursively
     */
    
    private void store(final Collection collection, final File file, final UploadDialog upload) {
        
        // cancel, stop crawl
        if (upload.isCancelled()) {
            return;
        }
        
        // can't read there, inform client
        if (!file.canRead()) {
            upload.showMessage(file.getAbsolutePath()+ " impossible to read ");
            return;
        }
        
        final XmldbURI filenameUri;
        try {
            filenameUri = URIUtils.encodeXmldbUriFor(file.getName());
        } catch (URISyntaxException e1) {
            upload.showMessage(file.getAbsolutePath()+ " could not be encoded as a URI");
            return;
        }
        
        // Directory, create collection, and crawl it
        if (file.isDirectory()) {
            Collection c=null;
            try {
                c = collection.getChildCollection(filenameUri.toString());
                if(c == null) {
                    final CollectionManagementServiceImpl mgtService = (CollectionManagementServiceImpl) collection.getService("CollectionManagementService","1.0");
                    c = mgtService.createCollection(filenameUri);
                }
            } catch (final XMLDBException e) {
                upload.showMessage("Impossible to create a collection " + file.getAbsolutePath() + ": " + e.getMessage());
            }
            
            // change displayed collection if it's OK
            upload.setCurrentDir(file.getAbsolutePath());
            if (c instanceof Observable) {
                ((Observable) c).addObserver(upload.getObserver());
            }
            // maybe a depth or recurs flag could be added here
            final File temp[] = file.listFiles();
            if (temp != null) {
                for (int i = 0; i < temp.length; i++) {
                    store(c, temp[i], upload);
                }
            }
            return;
        }
        
        // File, create and store resource
        if (file.isFile()) {
            upload.reset();
            upload.setCurrent(file.getName());
            upload.setCurrentSize(file.length());
            
            MimeType mimeType = MimeTable.getInstance().getContentTypeFor(file.getName());
            // unknown mime type, here prefered is to do nothing
            if(mimeType == null) {
                upload.showMessage(file.getAbsolutePath() +
                    " - unknown suffix. No matching mime-type found in : " + 
                    MimeTable.getInstance().getSrc());
                
                // if some one prefers to store it as binary by default, but dangerous
                mimeType = MimeType.BINARY_TYPE;
            }
            
            try {
                final Resource res = collection.createResource(filenameUri.toString(), mimeType.getXMLDBType());
                ((EXistResource) res).setMimeType(mimeType.getName());
                res.setContent(file);
                collection.storeResource(res);
                ++filesCount;
                this.totalLength += file.length();
                upload.setStoredSize(this.totalLength);
            } catch (final XMLDBException e) {
                upload.showMessage("Impossible to store a resource "
                    + file.getAbsolutePath() + ": " + e.getMessage());
            }
        }
    }
        
    
    
    private void mkcol(final XmldbURI collPath) throws XMLDBException {
        messageln("creating '" + collPath + "'");
        final XmldbURI[] segments = collPath.getPathSegments(); 
        XmldbURI p = XmldbURI.ROOT_COLLECTION_URI;
        for(int i=1;i<segments.length;i++) {
            p = p.append(segments[i]);
            final Collection c = DatabaseManager.getCollection(properties.getProperty("uri") + p, properties.getProperty("user"), properties.getProperty("password"));
            if (c == null) {
                final CollectionManagementServiceImpl mgtService = (CollectionManagementServiceImpl) current.getService("CollectionManagementService", "1.0");
                current = mgtService.createCollection(segments[i]);
            } else {
                current = c;
            }
        }
        path = p;
    }
    
    protected Collection getCollection(final String path) throws XMLDBException {
        return DatabaseManager.getCollection(properties.getProperty("uri") + path, properties.getProperty("user"), properties.getProperty("password"));
    }
    
    /*private char[] readPassword(InputStream in) throws IOException {
        
        char[] lineBuffer;
        char[] buf;
        // int i;
        
        buf = lineBuffer = new char[128];
        
        int room = buf.length;
        int offset = 0;
        int c;
        
        loop : while (true)
            switch (c = in.read()) {
                case -1 :
                case '\n' :
                    break loop;
                case '\r' :
                    int c2 = in.read();
                    if ((c2 != '\n') && (c2 != -1)) {
                        if (!(in instanceof PushbackInputStream))
                            in = new PushbackInputStream(in);
                        
                        ((PushbackInputStream) in).unread(c2);
                    } else
                        break loop;
                default :
                    if (--room < 0) {
                        buf = new char[offset + 128];
                        room = buf.length - offset - 1;
                        System.arraycopy(lineBuffer, 0, buf, 0, offset);
                        Arrays.fill(lineBuffer, ' ');
                        lineBuffer = buf;
                    }
                    buf[offset++] = (char) c;
                    break;
            }
            
            if (offset == 0)
                return null;
            
            char[] ret = new char[offset];
            System.arraycopy(buf, 0, ret, 0, offset);
            Arrays.fill(buf, ' ');
            
            return ret;
    }*/
    
    private Properties loadClientProperties(){
        
        final Properties clientProps = new Properties();
        final File propFile = ConfigurationHelper.lookup("client.properties");
        InputStream pin = null;
        
        // Try to load from file
        try{
            pin = new FileInputStream(propFile);
        } catch (final FileNotFoundException ex) {
            // File not found, no exception handling
        }
        
        if(pin == null){
            // Try to load via classloader
            pin = InteractiveClient.class.getResourceAsStream("client.properties");
        }
        
        if (pin != null){
            // Try to load properties from stream
            try{
            	try {
                    clientProps.load(pin);
            	} finally {
                    pin.close();
            	}
            } catch (final IOException ex) {
                //
            }
        }
        
        return clientProps;
    }
    
    /**
     *  Parse command line options, store into dedicated object
     * @param args   Arguments
     * @param props  Client configuration
     * @return       Object representing commandline parametres.
     */
    protected CommandlineOptions getCommandlineOptions(final String args[], final Properties props){
        
        // parse command-line options
        final CLArgsParser optParser = new CLArgsParser(args, CommandlineOptions.OPTIONS);
        
        if (optParser.getErrorString() != null) {
            System.err.println("ERROR: " + optParser.getErrorString());
            return null;
        }
        
        final List<CLOption> opt = optParser.getArguments();
        
        final CommandlineOptions cOpt = new CommandlineOptions();
        
        for (CLOption option : opt) {
            switch (option.getId()) {
                case CommandlineOptions.HELP_OPT :
                    printUsage();
                    return null;
                case CommandlineOptions.NO_GUI_OPT :
                    startGUI = false;
                    break;
                    //
                case CommandlineOptions.NO_EMBED_OPT :
                    props.setProperty(InteractiveClient.NO_EMBED_MODE, "TRUE");
                    break;
                case CommandlineOptions.USE_SSL_OPT :
                    props.setProperty(InteractiveClient.SSL_ENABLE, "TRUE");
                    break;
                case CommandlineOptions.QUIET_OPT :
                    quiet = true;
                    break;
                case CommandlineOptions.VERBOSE_OPT :
                    verbose = true;
                    break;
                case CommandlineOptions.LOCAL_OPT :
                    props.setProperty(InteractiveClient.LOCAL_MODE, "TRUE");
                    props.setProperty(InteractiveClient.URI, XmldbURI.EMBEDDED_SERVER_URI.toString());
                    break;
                case CommandlineOptions.USER_OPT :
                    props.setProperty(InteractiveClient.USER, option.getArgument());
                    if (!cOpt.passwdSpecified) {
                        cOpt.needPasswd = true;
                    }
                    break;
                case CommandlineOptions.PASS_OPT :
                    props.setProperty(InteractiveClient.PASSWORD, option.getArgument());
                    cOpt.needPasswd = false;
                    cOpt.passwdSpecified = true;
                    break;
                case CommandlineOptions.CONFIG_OPT :
                    properties.setProperty(InteractiveClient.CONFIGURATION, option.getArgument());
                    break;
                case CommandlineOptions.COLLECTION_OPT :
                    try {
                        path = URIUtils.encodeXmldbUriFor(option.getArgument());
                        cOpt.foundCollection = true;
                    } catch(final URISyntaxException e) {
                        System.err.println("Invalid collection path specified: "+e.getMessage());
                        return null;
                    }
                    break;
                case CommandlineOptions.RESOURCE_OPT :
                    cOpt.optionResource = option.getArgument();
                    break;
                case CommandlineOptions.OUTPUT_FILE_OPT :
                    cOpt.optionOutputFile = option.getArgument();
                    break;
                case CommandlineOptions.PARSE_OPT :
                    cOpt.doStore = true;
                    if (option.getArgumentCount() == 1) {
                        cOpt.optionalArgs.add(option.getArgument());
                    }
                    cOpt.interactive = false;
                    break;
                case CommandlineOptions.RECURSE_DIRS_OPT :
                    recurseDirs = true;
                    break;
                case CommandlineOptions.REMOVE_OPT :
                    cOpt.optionRemove = option.getArgument();
                    cOpt.interactive = false;
                    break;
                case CommandlineOptions.GET_OPT :
                    try {
                        cOpt.optionGet = URIUtils.encodeXmldbUriFor(option.getArgument());
                        cOpt.interactive = false;
                    } catch(final URISyntaxException e) {
                        System.err.println("Invalid collection path specified: "+e.getMessage());
                        return null;
                    }
                    break;
                case CommandlineOptions.MKCOL_OPT :
                    try {
                        cOpt.optionMkcol = URIUtils.encodeXmldbUriFor(option.getArgument());
                        cOpt.foundCollection = true;
                    } catch(final URISyntaxException e) {
                        System.err.println("Invalid collection path specified: "+e.getMessage());
                        return null;
                    }
                    break;
                case CommandlineOptions.RMCOL_OPT :
                    try {
                        cOpt.optionRmcol = URIUtils.encodeXmldbUriFor(option.getArgument());
                        cOpt.foundCollection = true;
                        cOpt.interactive = false;
                    } catch(final URISyntaxException e) {
                        System.err.println("Invalid collection path specified: "+e.getMessage());
                        return null;
                    }
                    break;
                case CommandlineOptions.FIND_OPT :
                    cOpt.optionXpath = (option.getArgumentCount() == 1 ? option.getArgument() : "stdin");
                    cOpt.interactive = false;
                    break;
                case CommandlineOptions.RESULTS_OPT :
                    try {
                        maxResults = Integer.parseInt(option.getArgument());
                    } catch (final NumberFormatException e) {
                        System.err.println("parameter -n needs a valid number");
                        return null;
                    }
                    break;
                case CommandlineOptions.OPTION_OPT :
                    properties.setProperty(option.getArgument(0), option.getArgument(1));
                    break;
                case CommandlineOptions.QUERY_FILE_OPT :
                    cOpt.optionQueryFile = option.getArgument();
                    cOpt.interactive = false;
                    break;
                case CommandlineOptions.THREADS_OPT :
                    try {
                        PARALLEL_THREADS = Integer.parseInt(option.getArgument());
                    } catch (final NumberFormatException e) {
                        System.err.println("parameter -t needs a valid number");
                        return null;
                    }
                    break;
                case CommandlineOptions.XUPDATE_OPT :
                    cOpt.optionXUpdate = option.getArgument();
                    cOpt.interactive = false;
                    break;
                case CommandlineOptions.TRACE_QUERIES_OPT :
                    final String traceFile = option.getArgument();
                    final File f = new File(traceFile);
                    try {
                        traceWriter = new OutputStreamWriter(new FileOutputStream(f, false), "UTF-8");
                        traceWriter.write("<?xml version=\"1.0\"?>" + EOL);
                        traceWriter.write("<query-log>" + EOL);
                    } catch (final UnsupportedEncodingException e1) {
                    	LOG.warn(e1);
                    } catch (final FileNotFoundException e1) {
                        errorln("Cannot open file " + traceFile);
                        return null;
                    } catch (final IOException e) {
                    }
                    break;
                case CommandlineOptions.REINDEX_OPT :
                    cOpt.doReindex = true;
                    cOpt.interactive = false;
                    break;
                case CommandlineOptions.QUERY_GUI_OPT :
                    cOpt.openQueryGui = true;
                    break;
                case CLOption.TEXT_ARGUMENT :
                    cOpt.optionalArgs.add(option.getArgument());
                    break;
            }
        }
        
        return cOpt;
    }
    
    /**
     *  Process the command line options
     * @param cOpt Object representing commandline options
     * @throws java.lang.Exception
     * @return TRUE is all successfull, FALSE of not.
     */
    private boolean processCommandLineActions(final CommandlineOptions cOpt)  throws Exception {
        // process command-line actions
        if (cOpt.doReindex) {
            if(!cOpt.foundCollection) {
                System.err.println("Please specify target collection with --collection");
                shutdown(false);
                return false;
            }
            try {
                reindex();
            } catch (XMLDBException e) {
                System.err.println("XMLDBException while reindexing collection: " + getExceptionMessage(e));
                e.printStackTrace();
                return false;
            }
        }
        
        if (cOpt.optionRmcol != null) {
            if (!cOpt.foundCollection) {
                System.err.println("Please specify target collection with --collection");
                shutdown(false);
                return false;
            }
            try {
                rmcol(cOpt.optionRmcol);
            } catch (XMLDBException e) {
                System.err.println("XMLDBException while removing collection: " + getExceptionMessage(e));
                e.printStackTrace();
                return false;
            }
        }
        
        if (cOpt.optionMkcol != null) {
            try {
                mkcol(cOpt.optionMkcol);
            } catch (XMLDBException e) {
                System.err.println("XMLDBException during mkcol: " + getExceptionMessage(e));
                e.printStackTrace();
                return false;
            }
        }
        
        if (cOpt.optionGet != null) {
            try {
                final Resource res = retrieve(cOpt.optionGet);
                if (res != null) {
                    // String data;
                    if (res.getResourceType().equals("XMLResource")) {
                        if (cOpt.optionOutputFile != null) {
                            writeOutputFile(cOpt.optionOutputFile, res.getContent());
                        } else {
                            System.out.println(res.getContent().toString());
                        }
                    } else {
                        if (cOpt.optionOutputFile != null) {
                            ((ExtendedResource)res).getContentIntoAFile(new File(cOpt.optionOutputFile));
                            ((EXistResource)res).freeResources();
                        } else {
                            ((ExtendedResource)res).getContentIntoAStream(System.out);
                            System.out.println();
                        }
                    }
                }
            } catch (final XMLDBException e) {
                System.err.println("XMLDBException while trying to retrieve document: " + getExceptionMessage(e));
                e.printStackTrace();
                return false;
            }
        } else if (cOpt.optionRemove != null) {
            if (!cOpt.foundCollection) {
                System.err.println("Please specify target collection with --collection");
            } else {
                try {
                    remove(cOpt.optionRemove);
                } catch (final XMLDBException e) {
                    System.err.println("XMLDBException during parse: " + getExceptionMessage(e));
                    e.printStackTrace();
                    return false;
                }
            }
        } else if (cOpt.doStore) {
            if (!cOpt.foundCollection) {
                System.err.println("Please specify target collection with --collection");
            } else {
                for (final String arg : cOpt.optionalArgs) {
                    try {
                        parse(arg);
                    } catch (final XMLDBException e) {
                        System.err.println("XMLDBException during parse: " + getExceptionMessage(e));
                        e.printStackTrace();
                        return false;
                    }
                }
            }
        } else if (cOpt.optionXpath != null || cOpt.optionQueryFile != null) {
            if (cOpt.optionQueryFile != null) {
                final BufferedReader reader = new BufferedReader(new FileReader(cOpt.optionQueryFile));
                try {
                    final StringBuilder buf = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                            buf.append(line);
                            buf.append(EOL);
                    }
                    cOpt.optionXpath = buf.toString();
                } finally {
                    reader.close();
                }
            }
            // if no argument has been found, read query from stdin
            if (cOpt.optionXpath.equals("stdin")) {
                try {
                    final BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
                    final StringBuilder buf = new StringBuilder();
                    String line;
                    while ((line = stdin.readLine()) != null) {
                        buf.append(line);
                        buf.append(EOL);
                    }
                    cOpt.optionXpath = buf.toString();
                } catch (final IOException e) {
                    System.err.println("failed to read query from stdin");
                    cOpt.optionXpath = null;
                    return false;
                }
            }
            if (cOpt.optionXpath != null) {
                try {
                    final ResourceSet result = find(cOpt.optionXpath);
                    if (maxResults <= 0) {
                        maxResults = (int) result.getSize();
                    }
                    if (cOpt.optionOutputFile == null) {
                        for (int i = 0; i < maxResults && i < result.getSize(); i++) {
                            final Resource res=result.getResource(i);
                            if(res instanceof ExtendedResource) {
                                ((ExtendedResource)res).getContentIntoAStream(System.out);
                            } else {
                                System.out.println(res.getContent());
                            }
                        }
                    } else {
                    	final FileOutputStream fos = new FileOutputStream(cOpt.optionOutputFile);
                    	final BufferedOutputStream bos = new BufferedOutputStream(fos);
                    	final PrintStream ps = new PrintStream(bos);
                        for (int i = 0; i < maxResults && i < result.getSize(); i++) {
                            final Resource res = result.getResource(i);
                            if(res instanceof ExtendedResource) {
                                ((ExtendedResource)res).getContentIntoAStream(ps);
                            } else {
                                ps.print(res.getContent().toString());
                            }
                        }
                        ps.close();
                        bos.close();
                        fos.close();
                    }
                } catch (final XMLDBException e) {
                    System.err.println("XMLDBException during query: " + getExceptionMessage(e));
                    e.printStackTrace();
                    return false;
                }
            }
            //		} else if (optionQueryFile != null) {
            //			testQuery(optionQueryFile);
        } else if (cOpt.optionXUpdate != null) {
            try {
                xupdate(cOpt.optionResource, cOpt.optionXUpdate);
            } catch (final XMLDBException e) {
                System.err.println("XMLDBException during xupdate: " + getExceptionMessage(e));
                return false;
            } catch (final IOException e) {
                System.err.println("IOException during xupdate: " + getExceptionMessage(e));
                return false;
            }
        }
        
        return true;
    }
    
    /**
     *  Ask user for login data using gui.
     * @param props     Client properties
     * @return          FALSE when pressed cancel, TRUE is sucessfull.
     */
    private boolean getGuiLoginData(final Properties props){
        
        final Properties loginData = ClientFrame.getLoginData(props);
        if (loginData == null || loginData.isEmpty()) {
            // User pressed <cancel>
            return false;
        }
        props.putAll(loginData);
        
        return true;
    }
    
    /**
     *  Reusable method for connecting to database. Exits process on failure.
     */
    private void connectToDatabase() {
        try {
            connect();
        } catch(final Exception cnf) {
            if(startGUI && frame != null) {
                frame.setStatus("Connection to database failed; message: " + cnf.getMessage());
            } else {
                System.err.println("Connection to database failed; message: " + cnf.getMessage());
            }
            cnf.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Main processing method for the InteractiveClient object
     *
     * @param args arguments from main()
     * @return true on success, false on failure
     */
    public boolean run(final String args[]) throws Exception {  
        // Get exist home directory
        final  File home = ConfigurationHelper.getExistHome();

        // initialize with default properties, before add client properties
        properties = new Properties(defaultProps);
        
        // get default configuration filename from the driver class and set it in properties
        final Class<?> cl = Class.forName(properties.getProperty(DRIVER));
        final Field CONF_XML = cl.getDeclaredField("CONF_XML");
        if (CONF_XML != null && home != null) {
            final File configuration = ConfigurationHelper.lookup((String)CONF_XML.get(new String()));
            properties.setProperty(CONFIGURATION, configuration.getAbsolutePath());
        }

        properties.putAll(loadClientProperties());
        
        // parse command-line options
        final CommandlineOptions cOpt = getCommandlineOptions(args, properties);
        if(cOpt == null){
            // An error occured during parsing. exit program.
            return false;
        }
        
        // print copyright notice - after parsing command line options, or it can't be silenced!
        if (!quiet) {
            printNotice();
        }
        
        // Fix "uri" property: Excalibur CLI can't parse dashes, so we need to URL encode them:
        properties.setProperty("uri", URLDecoder.decode(properties.getProperty("uri"), "UTF-8"));
        
        // prompt for password if needed
        if (cOpt.interactive && startGUI) {
            
            final boolean haveLoginData = getGuiLoginData(properties);
            if(!haveLoginData){
                return false;
            }
            
        } else if (cOpt.needPasswd) {
            try {
                properties.setProperty("password", console.readLine("password: ", Character.valueOf('*')));
            } catch (final Exception e) {
            }
        }

        
        historyFile = new File(home, ".exist_history");
        queryHistoryFile = new File(home, ".exist_query_history");
        
        if (queryHistoryFile.canRead()) {
            readQueryHistory();
        }
        
        if (cOpt.interactive) {
            // in gui mode we use Readline for history management
            // initialize Readline library
            Terminal.setupTerminal();
            console = new ConsoleReader();
            console.addCompletor(new CollectionCompleter());
            try {
                final History history = new History(historyFile);
                console.setHistory(history);
            } catch (final Exception e) {
                // No error handling
            }
        }
        
        // connect to the db
        connectToDatabase();
        
        if (current == null) {
            if (startGUI && frame != null) {
                frame.setStatus("Could not retrieve collection " + path);
            } else {
                System.err.println("Could not retrieve collection " + path);
            }
            shutdown(false);
            return false;
        }
        
        final boolean processingOK = processCommandLineActions(cOpt);
        if(!processingOK){
            return false;
        }
        
        if (cOpt.interactive) {
            if (startGUI) {
                frame = new ClientFrame(this, path, properties);
                frame.setLocation(100, 100);
                frame.setSize(500, 500);
                frame.setVisible(true);
            }
            
            // enter interactive mode
            if( (!startGUI) || (frame == null) ){
                
                // No gui
                try {
                    getResources();
                } catch (final XMLDBException e) {
                    System.err.println("XMLDBException while "
                            + "retrieving collection contents: "
                            + getExceptionMessage(e));
                    e.getCause().printStackTrace();
                    return false;
                }
                
            } else {
                
                // with gui ; re-login posibility
                boolean retry=true;
                
                while(retry){
                    
                    String errorMessage="";
                    try {
                        getResources();
                    } catch (XMLDBException e) {
                        
                        errorMessage=getExceptionMessage(e);
                        ClientFrame.showErrorMessage(
                                "XMLDBException occurred while retrieving collection: "
                                + errorMessage, e);
                    }
                    
                    // Determine error text. For special reasons we can retry
                    // to connect.
                    if( errorMessage.matches("^.*Invalid password for user.*$") ||
                            errorMessage.matches("^.*User .* unknown.*") ||
                            errorMessage.matches("^.*Connection refused: connect.*") ){
                        
                        final boolean haveLoginData = getGuiLoginData(properties);
                        if(!haveLoginData){
                            // pressed cancel
                            return false;
                        }
                        
                        // Need to shutdown ?? ask wolfgang
                        shutdown(false);
                        
                        // connect to the db
                        connectToDatabase();
                        
                    } else {
                        
                        if(!errorMessage.isEmpty()){
                            // No pattern match, but we have an error. stop here
                            frame.dispose();
                            return false;
                        } else {
                            // No error message, continue startup.
                            retry = false;
                        }
                    }
                }
            }
            
            messageln(EOL + "type help or ? for help.");
            
            if (cOpt.openQueryGui) {
                final QueryDialog qd = new QueryDialog(this, current, properties);
                qd.setLocation(100, 100);
                qd.setVisible(true);
            } else if (!startGUI) {
                readlineInputLoop(home.getAbsolutePath());
            } else {
                frame.displayPrompt();
            }
        } else {
            shutdown(false);
        }
        return true;
    }
    
    public static String getExceptionMessage(Throwable e) {
        Throwable cause;
        while((cause = e.getCause()) != null) {
            e = cause;
        }
        return e.getMessage();
    }
    
    /**
     * Read Query History file.
     */
    protected void readQueryHistory() {
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            final DocumentBuilder builder = factory.newDocumentBuilder();
            final Document doc = builder.parse(queryHistoryFile);
            final NodeList nodes = doc.getElementsByTagName("query");
            for (int i = 0; i < nodes.getLength(); i++) {
                final Element query = (Element) nodes.item(i);
                final StringBuilder value = new StringBuilder();
                Node next = query.getFirstChild();
                while (next != null) {
                    value.append(next.getNodeValue());
                    next = next.getNextSibling();
                }
                queryHistory.addLast(value.toString());
            }
        } catch (final Exception e) {
            if (startGUI) {
                ClientFrame.showErrorMessage(
                        "Error while reading query history: " + e.getMessage(),
                        e);
            } else {
                errorln("Error while reading query history: "
                        + e.getMessage());
            }
        }
    }
    
    protected void addToHistory(final String query) {
        queryHistory.add(query);
    }
    
    protected void writeQueryHistory() {
        try {
            console.getHistory().flushBuffer();
        } catch (final Exception e) {
        }
        try {
            final BufferedWriter writer = new BufferedWriter(new FileWriter(queryHistoryFile));
            final SAXSerializer serializer = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
            serializer.setOutput(writer, null);
            int p = 0;
            if (queryHistory.size() > 20) {
                p = queryHistory.size() - 20;
            }
            final AttributesImpl attrs = new AttributesImpl();
            serializer.startDocument();
            serializer.startElement("", "history", "history", attrs);
            for(final ListIterator<String> i = queryHistory.listIterator(p); i.hasNext(); ) {
                serializer.startElement("", "query", "query", attrs);
                final String next = i.next();
                serializer.characters(next.toCharArray(), 0, next.length());
                serializer.endElement("", "query", "query");
            }
            serializer.endElement("", "history", "history");
            serializer.endDocument();
            writer.close();
            SerializerPool.getInstance().returnObject(serializer);
        } catch (final IOException e) {
            System.err.println("IO error while writing query history.");
        } catch (final SAXException e) {
            System.err.println("SAX exception while writing query history.");
        }
        
    }
    
    public void readlineInputLoop(final String home) {
        String line;
        boolean cont = true;
        while (cont) {
            try {
                if (properties.getProperty("colors").equals("true")) {
                    line = console.readLine(ANSI_CYAN + "exist:" + path + ">"
                            + ANSI_WHITE);
                } else {
                    line = console.readLine("exist:" + path + ">");
                }
                if (line != null) {
                    cont = process(line);
                }
                
            } catch (final EOFException e) {
                break;
            } catch (final IOException ioe) {
                System.err.println(ioe);
            } catch (final Exception e) {
                System.err.println(e);
            }
        }
        
        try {
            console.getHistory().flushBuffer();
        } catch (final Exception e) {
            System.err.println("Could not write history File to " + historyFile.getAbsolutePath() );
        }
        shutdown(false);
        messageln("quit.");
    }
    
    protected final void shutdown(final boolean force) {
        if (traceWriter != null) {
            try {
                traceWriter.write("</query-log>");
                traceWriter.close();
            } catch (final IOException e1) {
            }
        }
        try {
            final DatabaseInstanceManager mgr = (DatabaseInstanceManager) current.getService("DatabaseInstanceManager", "1.0");
            if (mgr == null) {
                System.err.println("service is not available");
            } else if (mgr.isLocalInstance() || force) {
                System.out.println("shutting down database...");
                mgr.shutdown();
            }
        } catch (final XMLDBException e) {
            System.err.println("database shutdown failed: ");
            e.printStackTrace();
        }
    }
    
    private void printUsage() {
        System.out.println("Usage: java " + InteractiveClient.class.getName()
        + " [options]");
        System.out.println(CLUtil.describeOptions(CommandlineOptions.OPTIONS).toString());
    }
    
    public void printNotice() {
        messageln(getNotice());
    }

    public String getNotice() {
        final StringBuilder builder = new StringBuilder();
        builder.append(SystemProperties.getInstance().getSystemProperty("product-name", "eXist-db"));
        builder.append(" version ");
        builder.append(SystemProperties.getInstance().getSystemProperty("product-version", "unknown"));
        builder.append(", Copyright (C) 2001-2013 The eXist-db Project");
        builder.append(EOL);
        builder.append("eXist-db comes with ABSOLUTELY NO WARRANTY.");
        builder.append(EOL);
        builder.append("This is free software, and you are welcome to redistribute it");
        builder.append(EOL);
        builder.append("under certain conditions; for details read the license file.");
        builder.append(EOL);
        return builder.toString();
    }
    
    private void message(final String msg) {
        if (!quiet) {
            if (startGUI && frame != null) {
                frame.display(msg);
            } else {
                System.out.print(msg);
            }
        }
    }
    
    private void messageln(final String msg) {
        if (!quiet) {
            if (startGUI && frame != null) {
                frame.display(msg + EOL);
            } else {
                System.out.println(msg);
            }
        }
    }
    
    private void errorln(final String msg) {
        if (startGUI && frame != null) {
            frame.display(msg + EOL);
        } else {
            System.err.println(msg);
        }
    }
    
    private Collection resolveCollection(final XmldbURI path) throws XMLDBException {
        return DatabaseManager.getCollection(
            properties.getProperty("uri") + path, 
            properties.getProperty("user"), 
            properties.getProperty("password"));
    }
    
    private Resource resolveResource(final XmldbURI path) throws XMLDBException {
    	final XmldbURI collectionPath = path.numSegments()==1?XmldbURI.create(current.getName()):path.removeLastSegment();
    	final XmldbURI resourceName = path.lastSegment();
        
        final Collection collection = resolveCollection(collectionPath);
        if(collection == null) {
            messageln("Collection " + collectionPath + " not found.");
            return null;
        }
        messageln("Locating resource " + resourceName + " in collection " + collection.getName());
        return collection.getResource(resourceName.toString());
    }
    
    private class CollectionCompleter implements Completor {
        
        @Override
        public int complete(final String buffer, final int cursor, final List candidates) {
            int p = buffer.lastIndexOf(' ');
            final String toComplete;
            if (p > -1 && ++p < buffer.length()) {
                toComplete = buffer.substring(p);
            } else {
                toComplete = buffer;
                p = 0;
            }
//            System.out.println("\nbuffer: '" + toComplete + "'; cursor: " + cursor);
            final Set<String> set = completitions.tailSet(toComplete);
            if (set != null && set.size() > 0) {
                for (String next : completitions.tailSet(toComplete)) {
                    if (next.startsWith(toComplete)) {
                        candidates.add(next);
                    }
                }
            }
            return p + 1;
        }
    }
    
    public static class ProgressObserver implements Observer {
        
        final ProgressBar elementsProgress = new ProgressBar("storing elements");
        Observable lastObservable = null;
        final ProgressBar parseProgress = new ProgressBar("storing nodes   ");
        final ProgressBar wordsProgress = new ProgressBar("storing words   ");
        
        @Override
        public void update(final Observable o, final Object obj) {
            final ProgressIndicator ind = (ProgressIndicator) obj;
            if (lastObservable == null || o != lastObservable) {
                System.out.println();
            }
            
            if (o instanceof ElementIndex) {
                elementsProgress.set(ind.getValue(), ind.getMax());
            } else if (o instanceof TextSearchEngine) {
                wordsProgress.set(ind.getValue(), ind.getMax());
            } else {
                parseProgress.set(ind.getValue(), ind.getMax());
            }
            
            lastObservable = o;
        }
    }
    
    private void writeOutputFile(final String fileName, final Object data) throws Exception {
        final File file = new File(fileName);
        final FileOutputStream os = new FileOutputStream(file);
        if (data instanceof byte[]) {
            os.write((byte[]) data);
            os.close();
        } else {
            final OutputStreamWriter writer = new OutputStreamWriter(os, Charset.forName(properties.getProperty("encoding")));
            writer.write(data.toString());
            writer.close();
        }
    }
    
    private static String formatString(String s1, final String s2, final int width) {
        final StringBuilder buf = new StringBuilder(width);
        if (s1.length() > width) {
            s1 = s1.substring(0, width - 1);
        }
        buf.append(s1);
        int fill = width - (s1.length() + s2.length());
        for (int i = 0; i < fill; i++) {
            buf.append(' ');
        }
        buf.append(s2);
        return buf.toString();
    }
    
    private static String formatString(final String[] args, final int[] sizes) {
        final StringBuilder buf = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (sizes[i] < 0) {
                buf.append(args[i]);
            } else {
                for (int j = 0; j < sizes[i] && j < args[i].length(); j++) {
                    buf.append(args[i].charAt(j));
                }
            }
            for (int j = 0; j < sizes[i] - args[i].length(); j++) {
                buf.append(' ');
            }
        }
        return buf.toString();
    }
    
    public static Properties getSystemProperties() {
        
        final Properties sysProperties = new Properties();
        try {
            sysProperties.load(InteractiveClient.class.getClassLoader().getResourceAsStream("org/exist/system.properties"));
        } catch (final IOException e) {
            System.err.println("Unable to load system.properties from class loader");
        }
        
        return sysProperties;
    }
    
    public static ImageIcon getExistIcon(final Class clazz) {
        return new javax.swing.ImageIcon(clazz.getResource("/org/exist/client/icons/x.png"));
    }
}
