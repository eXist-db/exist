/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.client;

import java.awt.Dimension;
import java.io.*;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.swing.ImageIcon;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;

import org.apache.tools.ant.DirectoryScanner;
import org.exist.SystemProperties;
import org.exist.dom.persistent.XMLUtil;
import org.exist.security.Account;
import org.exist.security.Group;
import org.exist.security.Permission;
import org.exist.security.SecurityManager;
import org.exist.security.internal.aider.UserAider;
import org.exist.start.CompatibleJavaVersionCheck;
import org.exist.start.StartException;
import org.exist.storage.ElementIndex;
import org.exist.util.*;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.EXistCollectionManagementService;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.ExtendedResource;
import org.exist.xmldb.IndexQueryService;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.EXistXPathQueryService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;
import org.jline.reader.*;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.base.Collection;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.XUpdateQueryService;
import se.softhouse.jargo.ArgumentException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZoneOffset.UTC;
import static javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION;
import static org.exist.storage.serializers.EXistOutputKeys.OMIT_ORIGINAL_XML_DECLARATION;
import static org.exist.storage.serializers.EXistOutputKeys.OUTPUT_DOCTYPE;
import static org.xmldb.api.base.ResourceType.XML_RESOURCE;

/**
 * Command-line client based on the XML:DB API.
 *
 * @author wolf
 */
public class InteractiveClient {

    static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(UTC);

    // ANSI colors for ls display
    // private final static String ANSI_BLUE = "\033[0;34m";
    private static final String ANSI_CYAN = "\033[0;36m";
    private static final String ANSI_WHITE = "\033[0;37m";

    private static final String EOL = System.getProperty("line.separator");
    private static final Pattern UNKNOWN_USER_PATTERN = Pattern.compile("User .* unknown");
    private static final String DONE = "done.";

    // keys
    public static final String USER = "user";
    public static final String PASSWORD = "password";
    public static final String URI = "uri";
    public static final String PERMISSIONS = "permissions";
    public static final String INDENT = "indent";
    public static final String ENCODING = "encoding";
    public static final String COLORS = "colors";
    public static final String EDITOR = "editor";
    public static final String EXPAND_XINCLUDES = "expand-xincludes";
    public static final String CONFIGURATION = "configuration";
    public static final String DRIVER = "driver";
    public static final String SSL_ENABLE = "ssl-enable";
    public static final String CREATE_DATABASE = "create-database";
    public static final String LOCAL_MODE = "local-mode-opt";
    public static final String NO_EMBED_MODE = "NO_EMBED_MODE";

    // values
    protected static final String EDIT_CMD = "emacsclient -t $file";
    protected static final Charset ENCODING_DEFAULT = StandardCharsets.UTF_8;
    protected static final String URI_DEFAULT = "xmldb:exist://localhost:8080/exist/xmlrpc";
    protected static final String SSL_ENABLE_DEFAULT = "FALSE";
    protected static final String LOCAL_MODE_DEFAULT = "FALSE";
    protected static final String NO_EMBED_MODE_DEFAULT = "FALSE";
    protected static final String USER_DEFAULT = SecurityManager.DBA_USER;
    protected static final String DRIVER_IMPL_CLASS = "org.exist.xmldb.DatabaseImpl";

    // Set properties
    private static final Properties DEFAULT_PROPERTIES = new Properties();
    static {
        DEFAULT_PROPERTIES.setProperty(DRIVER, DRIVER_IMPL_CLASS);
        DEFAULT_PROPERTIES.setProperty(URI, URI_DEFAULT);
        DEFAULT_PROPERTIES.setProperty(USER, USER_DEFAULT);
        DEFAULT_PROPERTIES.setProperty(EDITOR, EDIT_CMD);
        DEFAULT_PROPERTIES.setProperty(INDENT, "true");
        DEFAULT_PROPERTIES.setProperty(OMIT_XML_DECLARATION, "no");
        DEFAULT_PROPERTIES.setProperty(OMIT_ORIGINAL_XML_DECLARATION, "no");
        DEFAULT_PROPERTIES.setProperty(OUTPUT_DOCTYPE, "true");
        DEFAULT_PROPERTIES.setProperty(ENCODING, ENCODING_DEFAULT.name());
        DEFAULT_PROPERTIES.setProperty(COLORS, "false");
        DEFAULT_PROPERTIES.setProperty(PERMISSIONS, "false");
        DEFAULT_PROPERTIES.setProperty(EXPAND_XINCLUDES, "true");
        DEFAULT_PROPERTIES.setProperty(SSL_ENABLE, SSL_ENABLE_DEFAULT);
    }
    protected static final int[] COL_SIZES = new int[]{10, 10, 10, -1};

    protected static String configuration = null;

    protected final TreeSet<String> completions = new TreeSet<>();
    protected final LinkedList<String> queryHistory = new LinkedList<>();
    protected final Properties properties = new Properties(DEFAULT_PROPERTIES);
    protected final Map<String, String> namespaceMappings = new HashMap<>();

    protected Path queryHistoryFile;
    protected Path historyFile;

    protected LineReader console = null;

    private Database database = null;
    protected Collection current = null;
    protected int nextInSet = 1;

    protected String[] resources = null;
    protected ResourceSet result = null;

    /**
     * number of files of a recursive store
     */
    protected int filesCount = 0;

    /**
     * total length of a recursive store
     */
    protected long totalLength = 0;

    protected ClientFrame frame;

    //*************************************

    private final CommandlineOptions options;
    protected XmldbURI path = XmldbURI.ROOT_COLLECTION_URI;
    private Optional<Writer> lazyTraceWriter = Optional.empty();

    public InteractiveClient(CommandlineOptions options) {
        this.options = options;
    }

    /**
     * Display help on commands
     */
    protected void displayHelp() {
        messageln("--- general commands ---");
        messageln("ls                   list collection contents");
        messageln("cd [collection|..]   change current collection");
        messageln("put [file pattern]   upload file or directory to the database");
        messageln("putgz [file pattern] upload possibly gzip compressed file or directory to the database");
        messageln("putzip [file pattern] upload the contents of a ZIP archive to the database");
        messageln("edit [resource] open the resource for editing");
        messageln("mkcol collection     create new sub-collection in current collection");
        messageln("rm document          remove document from current collection");
        messageln("rmcol collection     remove collection");
        messageln("set [key=value]      set property. Calling set without ");
        messageln("                     argument shows current settings.");
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
        messageln("                     [user|group|other]=[+|-][read|write|execute].");
        messageln("                     chmod without resource changes permissions for");
        messageln("                     the current collection.");
        messageln("lock resource        put a write lock on the specified resource.");
        messageln("unlock resource      remove a write lock from the specified resource.");
        messageln("quit                 quit the program");
    }

    /**
     * The main program for the InteractiveClient class.
     *
     * @param args The command line arguments
     */
    public static void main(final String[] args) {
        try {
            CompatibleJavaVersionCheck.checkForCompatibleJavaVersion();

            // parse command-line options
            final CommandlineOptions options = CommandlineOptions.parse(args);
            final InteractiveClient client = new InteractiveClient(options);
            if (!client.run()) {
                System.exit(SystemExitCodes.CATCH_ALL_GENERAL_ERROR_EXIT_CODE); // return non-zero exit status on failure
            }

        } catch (final StartException e) {
            if (e.getMessage() != null && !e.getMessage().isEmpty()) {
                consoleErr(e.getMessage());
            }
            System.exit(e.getErrorCode());

        } catch (final ArgumentException e) {
            consoleOut(e.getMessageAndUsage().toString());
            System.exit(SystemExitCodes.INVALID_ARGUMENT_EXIT_CODE);

        } catch (final Exception e) {
            e.printStackTrace();
            System.exit(SystemExitCodes.CATCH_ALL_GENERAL_ERROR_EXIT_CODE); // return non-zero exit status on exception
        }
    }

    /**
     * Create a new thread for this client instance.
     *
     * @param threadName the name of the thread
     * @param runnable   the function to execute on the thread
     * @return the thread
     */
    Thread newClientThread(final String threadName, final Runnable runnable) {
        return new Thread(runnable, "java-admin-client." + threadName);
    }

    /**
     * Register XML:DB driver and retrieve root collection.
     *
     * @throws Exception Description of the Exception
     */
    protected void connect() throws Exception {
        consoleOut("Connecting to database...");

        final String uri = properties.getProperty(InteractiveClient.URI);
        if (options.startGUI && frame != null) {
            frame.setStatus("connecting to " + uri);
        }

        // Create database
        final Class<?> cl = Class.forName(properties.getProperty(DRIVER));
        database = (Database) cl.getConstructor().newInstance();

        // Configure database
        database.setProperty(CREATE_DATABASE, "true");
        database.setProperty(SSL_ENABLE, properties.getProperty(SSL_ENABLE));

        // secure empty configuration
        final String configProp = properties.getProperty(InteractiveClient.CONFIGURATION);

        if (configProp != null && (!configProp.isEmpty())) {
            database.setProperty(CONFIGURATION, configProp);
        }

        DatabaseManager.registerDatabase(database);

        final String collectionUri = uri + path;
        current = DatabaseManager.getCollection(collectionUri, properties.getProperty(USER), properties.getProperty(PASSWORD));
        if (options.startGUI && frame != null) {
            frame.setStatus("connected to " + uri + " as user " + properties.getProperty(USER));
        }

        if (database.getProperty(CONFIGURATION) != null) {
            consoleOut("Using config: " + database.getProperty(CONFIGURATION));
        }

        consoleOut("Connected :-)");
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
        current = DatabaseManager.getCollection(properties.getProperty(URI)
                        + path, properties.getProperty(USER),
                properties.getProperty(PASSWORD));
        getResources();
    }

    protected void setProperties() throws XMLDBException {
        for (Map.Entry<Object, Object> properry : properties.entrySet()) {
            current.setProperty((String) properry.getKey(), (String) properry.getValue());
        }
    }

    private String getOwnerName(final Permission perm) {
        return Optional.ofNullable(perm).map(Permission::getOwner).map(Account::getName).orElse("?");
    }

    private String getGroupName(final Permission perm) {
        return Optional.ofNullable(perm).map(Permission::getGroup).map(Group::getName).orElse("?");
    }

    /**
     * Get list of resources contained in collection.
     *
     * @throws XMLDBException Description of the Exception
     */
    protected void getResources() throws XMLDBException {
        if (current == null) {
            return;
        }
        setProperties();
        final UserManagementService mgtService = current.getService(UserManagementService.class);
        final List<String> childCollections = current.listChildCollections();
        final List<String> childResources = current.listResources();

        resources = new String[childCollections.size() + childResources.size()];
        //Collection child;
        Permission perm;

        final List<ResourceDescriptor> tableData = new ArrayList<>(resources.length); // A list of ResourceDescriptor for the GUI

        int i = 0;
        for (String collectionName : childCollections) {
            perm = mgtService.getSubCollectionPermissions(current, collectionName);

            final Instant created = mgtService.getSubCollectionCreationTime(current, collectionName);

            if ("true".equals(properties.getProperty(PERMISSIONS))) {
                resources[i] = 'c' + perm.toString() + '\t' + getOwnerName(perm)
                        + '\t' + getGroupName(perm) + '\t'
                        + DATE_TIME_FORMATTER.format(created) + '\t'
                        + collectionName;
            } else {
                resources[i] = collectionName;
            }

            if (options.startGUI) {
                try {
                    tableData.add(
                            new ResourceDescriptor.Collection(
                                    XmldbURI.xmldbUriFor(collectionName),
                                    perm,
                                    created
                            )
                    );
                } catch (final URISyntaxException e) {
                    errorln("could not parse collection name into a valid URI: " + e.getMessage());
                }
            }
            completions.add(collectionName);
            i++;
        }
        for (String resourceId : childResources) {
            try (final Resource res = current.getResource(resourceId)) {
                perm = mgtService.getPermissions(res);
                if (perm == null) {
                    errorln("no permissions found for resource " + resourceId);
                }

                final Instant lastModificationTime = res.getLastModificationTime();

                if ("true".equals(properties.getProperty(PERMISSIONS))) {
                    resources[i] = '-' + perm.toString() + '\t' + getOwnerName(perm)
                            + '\t' + getGroupName(perm) + '\t'
                            + DATE_TIME_FORMATTER.format(lastModificationTime) + '\t'
                            + resourceId;
                } else {
                    resources[i] = resourceId;
                }

                if (options.startGUI) {
                    try {
                        tableData.add(
                                new ResourceDescriptor.Document(
                                        XmldbURI.xmldbUriFor(resourceId),
                                        perm,
                                        lastModificationTime
                                )
                        );
                    } catch (final URISyntaxException e) {
                        errorln("could not parse document name into a valid URI: " + e.getMessage());
                    }
                }
                completions.add(resourceId);
            }
            i++;
        }
        if (options.startGUI) {
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
                    consoleOut("line: " + reader.getLineNumber()
                            + "; press [return] for more or [q] for quit.");
                    ch = System.in.read();
                    if (ch == 'q' || ch == 'Q') {
                        return;
                    }
                }
                consoleOut(line);
            }
        } catch (final IOException ioe) {
            consoleErr("IOException: " + ioe);
        }
    }

    /**
     * In interactive mode, process a line entered by the user.
     *
     * @param line the line entered
     * @return true if command != quit
     */
    protected boolean process(final String line) {
        if (options.startGUI) {
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

            final List<String> argList = new ArrayList<>(3);
            // int i = 0;
            int token;
            try {
                while ((token = tok.nextToken()) != StreamTokenizer.TT_EOF) {
                    if (token == StreamTokenizer.TT_WORD || token == '"') {
                        argList.add(tok.sval);
                    }
                }
            } catch (final IOException e) {
                consoleErr("Could not parse command line.");
                return true;
            }
            args = new String[argList.size()];
            argList.toArray(args);
        }

        if (args.length == 0) {
            return true;
        }

        try {
            XmldbURI newPath = path;
            final XmldbURI currUri = XmldbURI.xmldbUriFor(properties.getProperty(URI)).resolveCollectionPath(path);
            if (args[0].equalsIgnoreCase("ls")) {
                // list collection contents
                getResources();
                if ("true".equals(properties.getProperty(PERMISSIONS))) {
                    for (String resource : resources) {
                        messageln(resource);
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
                completions.clear();
                Collection temp;
                XmldbURI collectionPath;
                if (args.length < 2 || args[1] == null) {
                    collectionPath = XmldbURI.ROOT_COLLECTION_URI;
                } else {
                    collectionPath = XmldbURI.xmldbUriFor(args[1]);
                }
                collectionPath = currUri.resolveCollectionPath(collectionPath);
                if (collectionPath.numSegments() == 0) {
                    collectionPath = currUri.resolveCollectionPath(XmldbURI.ROOT_COLLECTION_URI);
                    messageln("cannot go above " + XmldbURI.ROOT_COLLECTION_URI.toString());
                }
                temp = DatabaseManager.getCollection(
                        collectionPath.toString(),
                        properties.getProperty(USER),
                        properties.getProperty(PASSWORD));
                if (temp != null) {
                    current.close();
                    current = temp;
                    newPath = collectionPath.toCollectionPathURI();
                    if (options.startGUI) {
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
                final XmldbURI src;
                final XmldbURI dest;
                try {
                    src = XmldbURI.xmldbUriFor(args[1]);
                    dest = XmldbURI.xmldbUriFor(args[2]);
                } catch (final URISyntaxException e) {
                    errorln("could not parse collection name into a valid URI: " + e.getMessage());
                    return false;
                }
                copy(src, dest);
                getResources();

            } else if (args[0].equalsIgnoreCase("edit")) {
                if (args.length == 2) {
                    final XmldbURI resource;
                    try {
                        resource = XmldbURI.xmldbUriFor(args[1]);
                    } catch (final URISyntaxException e) {
                        errorln("could not parse resource name into a valid URI: " + e.getMessage());
                        return false;
                    }
                    editResource(resource);
                } else {
                    messageln("Please specify a resource.");
                }
            } else if (args[0].equalsIgnoreCase("get")) {
                if (args.length < 2) {
                    consoleErr("wrong number of arguments.");
                    return true;
                }
                final XmldbURI resource;
                try {
                    resource = XmldbURI.xmldbUriFor(args[1]);
                } catch (final URISyntaxException e) {
                    errorln("could not parse resource name into a valid URI: " + e.getMessage());
                    return false;
                }
                final Resource res = retrieve(resource);
                // display document
                if (res != null) {
                    final String data;
                    if (XML_RESOURCE.equals(res.getResourceType())) {
                        data = (String) res.getContent();
                    } else {
                        data = new String((byte[]) res.getContent());
                    }
                    if (options.startGUI) {
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
                try (final BufferedReader reader = Files.newBufferedReader(Paths.get(args[1]))) {
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
                        if (options.startGUI) {
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
                    collUri = XmldbURI.xmldbUriFor(args[1]);
                } catch (final URISyntaxException e) {
                    errorln("could not parse collection name into a valid URI: " + e.getMessage());
                    return false;
                }
                final EXistCollectionManagementService mgtService = current.getService(EXistCollectionManagementService.class);
                final Collection newCollection = mgtService.createCollection(collUri);
                if (newCollection == null) {
                    messageln("could not create collection.");
                } else {
                    messageln("created collection.");
                }

                // re-read current collection
                current = DatabaseManager.getCollection(properties
                        .getProperty(URI)
                        + path, properties.getProperty(USER), properties
                        .getProperty(PASSWORD));
                getResources();

            } else if (args[0].equalsIgnoreCase("put")) {
                // put a document or directory into the database
                if (args.length < 2) {
                    messageln("missing argument.");
                    return true;
                }
                final boolean r = parse(Paths.get(args[1]));
                getResources();
                return r;

            } else if (args[0].equalsIgnoreCase("putzip")) {
                // put the contents of a zip archive into the database
                if (args.length < 2) {
                    messageln("missing argument.");
                    return true;
                }
                final boolean r = parseZip(Paths.get(args[1]));
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
                        + path, properties.getProperty(USER), properties
                        .getProperty(PASSWORD));
                getResources();

            } else if (args[0].equalsIgnoreCase("rmcol")) {
                // remove collection
                if (args.length < 2) {
                    messageln("wrong argument count.");
                    return true;
                }
                final XmldbURI collUri;
                try {
                    collUri = XmldbURI.xmldbUriFor(args[1]);
                } catch (final URISyntaxException e) {
                    errorln("could not parse collection name into a valid URI: " + e.getMessage());
                    return false;
                }
                rmcol(collUri);
                // re-read current collection
                current = DatabaseManager.getCollection(properties
                        .getProperty(URI)
                        + path, properties.getProperty(USER), properties
                        .getProperty(PASSWORD));
                getResources();
            } else if (args[0].equalsIgnoreCase("adduser")) {
                if (args.length < 2) {
                    consoleErr("Usage: adduser name");
                    return true;
                }
                if (options.startGUI) {
                    messageln("command not supported in GUI mode. Please use the \"Edit users\" menu option.");
                    return true;
                }
                try {
                    final UserManagementService mgtService = current.getService(UserManagementService.class);

                    String p1;
                    String p2;
                    while (true) {
                        p1 = console.readLine("password: ", '*');
                        p2 = console.readLine("re-enter password: ", '*');
                        if (p1.equals(p2)) {
                            break;
                        }
                        messageln("Entered passwords differ. Try again...");

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

                    if (user.getGroups().length == 0) {
                        messageln("No groups specified, will be a member of the '" + SecurityManager.GUEST_GROUP + "' group!");
                        user.addGroup(SecurityManager.GUEST_GROUP);
                    }

                    mgtService.addAccount(user);
                    messageln("User '" + user.getName() + "' created.");
                } catch (final Exception e) {
                    errorln("ERROR: " + e.getMessage());
                    e.printStackTrace();
                }
            } else if (args[0].equalsIgnoreCase("users")) {
                final UserManagementService mgtService = current.getService(UserManagementService.class);
                final Account users[] = mgtService.getAccounts();
                messageln("User\t\tGroups");
                messageln("-----------------------------------------");
                for (Account user : users) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(user.getName() + "\t\t");
                    final String[] groups = user.getGroups();
                    for (int j = 0; j < groups.length; j++) {
                        sb.append(groups[j]);
                        if (j + 1 < groups.length) {
                            sb.append(", ");
                        }
                    }
                    messageln(sb.toString());
                }
            } else if (args[0].equalsIgnoreCase("passwd")) {
                if (options.startGUI) {
                    messageln("command not supported in GUI mode. Please use the \"Edit users\" menu option.");
                    return true;
                }
                if (args.length < 2) {
                    messageln("Usage: passwd username");
                    return true;
                }
                try {
                    final UserManagementService mgtService = current.getService(UserManagementService.class);
                    final Account user = mgtService.getAccount(args[1]);
                    if (user == null) {
                        messageln("no such user.");
                        return true;
                    }
                    String p1;
                    String p2;
                    while (true) {
                        p1 = console.readLine("password: ", '*');
                        p2 = console.readLine("re-enter password: ", '*');
                        if (p1.equals(p2)) {
                            break;
                        }
                        consoleOut(EOL + "entered passwords differ. Try again...");
                    }
                    user.setPassword(p1);
                    mgtService.updateAccount(user);
                    properties.setProperty(PASSWORD, p1);
                } catch (final Exception e) {
                    errorln("ERROR: " + e.getMessage());
                    e.printStackTrace();
                }
            } else if (args[0].equalsIgnoreCase("chmod")) {
                if (args.length < 2) {
                    consoleOut("Usage: chmod [resource] mode");
                    return true;
                }

                final Collection temp;
                if (args.length == 3) {
                    consoleOut("trying collection: " + args[1]);
                    temp = current.getChildCollection(args[1]);
                    if (temp == null) {
                        consoleOut(EOL + "trying resource: " + args[1]);
                        final Resource r = current.getResource(args[1]);
                        if (r != null) {
                            final UserManagementService mgtService = current.getService(UserManagementService.class);
                            mgtService.chmod(r, args[2]);
                        } else {
                            consoleErr("Resource " + args[1] + " not found.");
                        }
                    } else {
                        final UserManagementService mgtService = temp.getService(UserManagementService.class);
                        mgtService.chmod(args[2]);
                    }
                } else {
                    final UserManagementService mgtService = current.getService(UserManagementService.class);
                    mgtService.chmod(args[1]);
                }
                // re-read current collection
                current = DatabaseManager.getCollection(properties
                        .getProperty(URI)
                        + path, properties.getProperty(USER), properties
                        .getProperty(PASSWORD));
                getResources();
            } else if (args[0].equalsIgnoreCase("chown")) {
                if (args.length < 3) {
                    consoleOut("Usage: chown username group [resource]");
                    return true;
                }

                final Collection temp;
                if (args.length == 4) {
                    temp = current.getChildCollection(args[3]);
                } else {
                    temp = current;
                }
                if (temp != null) {
                    final UserManagementService mgtService = temp.getService(UserManagementService.class);
                    final Account u = mgtService.getAccount(args[1]);
                    if (u == null) {
                        consoleOut("unknown user");
                        return true;
                    }
                    mgtService.chown(u, args[2]);
                    consoleOut("owner changed.");
                    getResources();
                    return true;
                }
                final Resource res = current.getResource(args[3]);
                if (res != null) {
                    final UserManagementService mgtService = current.getService(UserManagementService.class);
                    final Account u = mgtService.getAccount(args[1]);
                    if (u == null) {
                        consoleOut("unknown user");
                        return true;
                    }
                    mgtService.chown(res, u, args[2]);
                    getResources();
                    return true;
                }
                consoleErr("Resource " + args[3] + " not found.");

            } else if (args[0].equalsIgnoreCase("lock") || args[0].equalsIgnoreCase("unlock")) {
                if (args.length < 2) {
                    messageln("Usage: lock resource");
                    return true;
                }
                final Resource res = current.getResource(args[1]);
                if (res != null) {
                    final UserManagementService mgtService = current.getService(UserManagementService.class);
                    final Account user = mgtService.getAccount(properties.getProperty(USER, "guest"));
                    if (args[0].equalsIgnoreCase("lock")) {
                        mgtService.lockResource(res, user);
                    } else {
                        mgtService.unlockResource(res);
                    }
                }

            } else if (args[0].equalsIgnoreCase("elements")) {
                consoleOut("Element occurrences in collection "
                        + current.getName());
                consoleOut("--------------------------------------------"
                                + "-----------");
                final IndexQueryService service = current.getService(IndexQueryService.class);
                final Occurrences[] elements = service.getIndexedElements(true);
                for (Occurrences element : elements) {
                    consoleOut(formatString(element.getTerm().toString(),
                                    Integer.toString(element
                                            .getOccurrences()), 50));
                }
                return true;

            } else if (args[0].equalsIgnoreCase("xupdate")) {
                if (options.startGUI) {
                    messageln("command not supported in GUI mode.");
                    return true;
                }
                final StringBuilder command = new StringBuilder();
                try {
                    while (true) {
                        final String lastLine = console.readLine("| ");
                        if (lastLine == null || lastLine.length() == 0) {
                            break;
                        }
                        command.append(lastLine);
                    }
                } catch (final UserInterruptException e) {
                    //TODO report error?
                }
                final String xupdate = "<xu:modifications version=\"1.0\" "
                        + "xmlns:xu=\"http://www.xmldb.org/xupdate\">"
                        + command + "</xu:modifications>";
                final XUpdateQueryService service = current.getService(XUpdateQueryService.class);
                final long mods = service.update(xupdate);
                consoleOut(mods + " modifications processed.");

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
                            consoleErr("please specify a key=value pair");
                            return true;
                        }
                        final String key = tok.nextToken();
                        final String val = tok.nextToken();
                        properties.setProperty(key, val);
                        current.setProperty(key, val);
                        getResources();
                    } catch (final Exception e) {
                        consoleErr("Exception: " + e.getMessage());
                    }
                }
            } else if (args[0].equalsIgnoreCase("shutdown")) {
                final DatabaseInstanceManager mgr = current.getService(DatabaseInstanceManager.class);
                if (mgr == null) {
                    messageln("Service is not available");
                    return true;
                }
                mgr.shutdown();
                return true;
            } else if (args[0].equalsIgnoreCase("help") || "?".equals(args[0])) {
                displayHelp();
            } else if (args[0].equalsIgnoreCase("quit")) {
                return false;
            } else {
                messageln("unknown command: '" + args[0] + "'");
                return true;
            }
            path = newPath;
            return true;
        } catch (final Throwable e) {
            if (options.startGUI) {
                ClientFrame.showErrorMessage(getExceptionMessage(e), e);
            } else {
                errorln(getExceptionMessage(e));
                e.printStackTrace();
            }
            return true;
        }
    }

    /**
     * @param name
     */
    private void editResource(final XmldbURI name) {
        try {
            final Resource doc = retrieve(name, properties.getProperty(OutputKeys.INDENT, "yes")); //$NON-NLS-1$
            final DocumentView view = new DocumentView(this, name, doc, properties);
            view.setSize(new Dimension(640, 400));
            view.viewDocument();
        } catch (final XMLDBException ex) {
            errorln("XMLDB error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private Optional<Writer> getTraceWriter() {

        //should there be a trace writer?
        if (options.traceQueriesFile.isPresent()) {

            //lazy initialization
            if (!lazyTraceWriter.isPresent()) {
                try (final Writer traceWriter = Files.newBufferedWriter(options.traceQueriesFile.get(), UTF_8)) {
                    traceWriter.write("<?xml version=\"1.0\"?>" + EOL);
                    traceWriter.write("<query-log>" + EOL);
                    this.lazyTraceWriter = Optional.of(traceWriter);
                } catch (final IOException ioe) {
                    errorln("Cannot open file " + options.traceQueriesFile.get());
                    return Optional.empty();
                }
            }

            return lazyTraceWriter;

        } else {
            return Optional.empty();
        }
    }

    private ResourceSet find(String xpath) throws XMLDBException {
        if (xpath.substring(xpath.length() - EOL.length()).equals(EOL)) {
            xpath = xpath.substring(0, xpath.length() - EOL.length());
        }

        final String xpathCopy = xpath;
        getTraceWriter().ifPresent(writer -> {
            try {
                writer.write("<query>");
                writer.write(xpathCopy);
                writer.write("</query>");
                writer.write(EOL);
            } catch (final IOException e) {
                //TODO report error?
            }
        });

        String sortBy = null;
        final int p = xpath.indexOf(" sort by ");
        if (p != Constants.STRING_NOT_FOUND) {
            final String xp = xpath.substring(0, p);
            sortBy = xpath.substring(p + " sort by ".length());
            xpath = xp;
            consoleOut("XPath =   " + xpath);
            consoleOut("Sort-by = " + sortBy);
        }

        final EXistXPathQueryService service = current.getService(EXistXPathQueryService.class);
        service.setProperty(OutputKeys.INDENT, properties.getProperty(INDENT));
        service.setProperty(OutputKeys.ENCODING, properties.getProperty(ENCODING));

        for (final Map.Entry<String, String> mapping : namespaceMappings.entrySet()) {
            service.setNamespace(mapping.getKey(), mapping.getValue());
        }

        return (sortBy == null) ? service.query(xpath) : service.query(xpath, sortBy);
    }

    protected final Resource retrieve(final XmldbURI resource) throws XMLDBException {
        return retrieve(resource, properties.getProperty(INDENT));
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
            consoleErr("path pattern should be relative to current collection");
            return;
        }
        final Resource[] resources;
        final Resource res = collection.getResource(pattern);
        if (res == null) {
            resources = CollectionScanner.scan(collection, "", pattern);
        } else {
            resources = new Resource[1];
            resources[0] = res;
        }
        for (Resource resource : resources) {
            message("removing document " + resource.getId() + " ...");
            final Collection parent = resource.getParentCollection();
            parent.removeResource(resource);
            messageln(DONE);
        }
    }

    private void xupdate(final Optional<String> resource, final Path file) throws XMLDBException, IOException {
        if (!(Files.exists(file) && Files.isReadable(file))) {
            messageln("cannot read file " + file.normalize().toAbsolutePath());
            return;
        }
        final String commands = XMLUtil.readFile(file, UTF_8);
        final XUpdateQueryService service = current.getService(XUpdateQueryService.class);
        final long modifications;
        if (resource.isPresent()) {
            modifications = service.updateResource(resource.get(), commands);
        } else {
            modifications = service.update(commands);

        }
        messageln(modifications + " modifications processed " + "successfully.");
    }

    private void rmcol(final XmldbURI collection) throws XMLDBException {
        final EXistCollectionManagementService mgtService = current.getService(EXistCollectionManagementService.class);
        message("removing collection " + collection + " ...");
        mgtService.removeCollection(collection);
        messageln(DONE);
    }

    private void copy(final XmldbURI source, XmldbURI destination) throws XMLDBException {
        try {
            final EXistCollectionManagementService mgtService = current.getService(EXistCollectionManagementService.class);
            final XmldbURI destName = destination.lastSegment();
            final Collection destCol = resolveCollection(destination);
            if (destCol == null) {
                if (destination.numSegments() == 1) {
                    destination = XmldbURI.xmldbUriFor(current.getName());
                } else {
                    destination = destination.removeLastSegment();
                }
            }
            final Resource srcDoc = resolveResource(source);
            if (srcDoc != null) {
                final XmldbURI resourcePath = XmldbURI.xmldbUriFor(srcDoc.getParentCollection().getName()).append(srcDoc.getId());
                messageln("Copying resource '" + resourcePath + "' to '" + destination + "'");
                mgtService.copyResource(resourcePath, destination, destName);
            } else {
                messageln("Copying collection '" + source + "' to '" + destination + "'");
                mgtService.copy(source, destination, destName);
            }
        } catch (final URISyntaxException e) {
            errorln("could not parse name into a valid URI: " + e.getMessage());
        }
    }

    private void reindex() throws XMLDBException {
        final IndexQueryService service = current.getService(IndexQueryService.class);
        message("reindexing collection " + current.getName());
        service.reindexCollection();
        messageln(DONE);
    }

    private void storeBinary(final String fileName) throws XMLDBException {
        final Path file = Paths.get(fileName).normalize();
        if (Files.isReadable(file)) {
            final MimeType mime = MimeTable.getInstance().getContentTypeFor(FileUtils.fileName(file));
            try (final BinaryResource resource = current.createResource(FileUtils.fileName(file), BinaryResource.class)) {
                resource.setContent(file);
                ((EXistResource) resource).setMimeType(mime == null ? "application/octet-stream" : mime.getName());
                current.storeResource(resource);
            }
        }
    }

    private synchronized boolean findRecursive(final Collection collection, final Path dir, final XmldbURI base) throws XMLDBException {
        Collection c;
        EXistCollectionManagementService mgtService;
        //The XmldbURIs here aren't really used...
        XmldbURI next;
        MimeType mimeType;

        try {
            final List<Path> files = FileUtils.list(dir);
            int i = 0;
            for (final Path file : files) {
                next = base.append(FileUtils.fileName(file));
                try {
                    if (Files.isDirectory(file)) {
                        messageln("entering directory " + file.toAbsolutePath());
                        c = collection.getChildCollection(FileUtils.fileName(file));
                        if (c == null) {
                            mgtService = collection.getService(EXistCollectionManagementService.class);
                            c = mgtService.createCollection(XmldbURI.xmldbUriFor(FileUtils.fileName(file)));
                        }

                        if (c instanceof Observable && options.verbose) {
                            final ProgressObserver observer = new ProgressObserver();
                            ((Observable) c).addObserver(observer);
                        }
                        findRecursive(c, file, next);
                    } else {
                        final long start1 = System.currentTimeMillis();
                        mimeType = MimeTable.getInstance().getContentTypeFor(FileUtils.fileName(file));
                        if (mimeType == null) {
                            messageln("File " + FileUtils.fileName(file) + " has an unknown suffix. Cannot determine file type.");
                            mimeType = MimeType.BINARY_TYPE;
                        }
                        try (final Resource document = collection.createResource(FileUtils.fileName(file), mimeType.getXMLDBType())) {
                            message("storing document " + FileUtils.fileName(file) + " (" + i + " of " + files.size() + ") " + "...");
                            document.setContent(file);
                            ((EXistResource) document).setMimeType(mimeType.getName());
                            collection.storeResource(document);
                            ++filesCount;
                            messageln(" " + FileUtils.sizeQuietly(file) + " bytes in " + (System.currentTimeMillis() - start1) + "ms.");
                        }
                    }
                } catch (final URISyntaxException e) {
                    errorln("uri syntax exception parsing " + file.toAbsolutePath() + ": " + e.getMessage());
                }
                i++;
            }
            return true;
        } catch (final IOException e) {
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e);
        }
    }

    /**
     * Stores given Resource
     *
     * @param file file or directory
     * @return TRUE if file or files in directory were all correctly stored.
     * @throws XMLDBException An error was detected.
     */
    protected synchronized boolean parse(final Path file) throws XMLDBException {
        try {
            // String xml;

            if (current instanceof Observable && options.verbose) {
                final ProgressObserver observer = new ProgressObserver();
                ((Observable) current).addObserver(observer);
            }

            List<Path> files = new ArrayList<>();
            if (Files.isReadable(file)) {
                // TODO, same logic as for the graphic client
                if (Files.isDirectory(file)) {
                    if (options.reindexRecurse) {
                        filesCount = 0;
                        final long start = System.currentTimeMillis();
                        final boolean result = findRecursive(current, file, path);
                        messageln("storing " + filesCount + " files took " + ((System.currentTimeMillis() - start) / 1000) + "sec.");
                        return result;
                    }
                    files = FileUtils.list(file);
                } else {
                    files.add(file);
                }
            } else {
                final DirectoryScanner directoryScanner = new DirectoryScanner();
                directoryScanner.setIncludes(new String[]{file.toString()});
                //TODO(AR) do we need to call scanner.setBasedir()?
                directoryScanner.setCaseSensitive(true);
                directoryScanner.scan();
                for (final String includedFile : directoryScanner.getIncludedFiles()) {
//                    files.add(baseDir.resolve(includedFile));
                    files.add(Paths.get(includedFile));
                }
            }

            final long start0 = System.currentTimeMillis();
            long bytes = 0;
            MimeType mimeType;
            for (int i = 0; i < files.size(); i++) {
                if (Files.isDirectory(files.get(i))) {
                    continue;
                }
                final long start = System.currentTimeMillis();
                mimeType = MimeTable.getInstance().getContentTypeFor(FileUtils.fileName(files.get(i)));
                if (mimeType == null) {
                    mimeType = MimeType.BINARY_TYPE;
                }
                try (final Resource document = current.createResource(FileUtils.fileName(files.get(i)), mimeType.getXMLDBType())) {
                    message("storing document " + FileUtils.fileName(files.get(i)) + " (" + (i + 1) + " of " + files.size() + ") ...");
                    document.setContent(files.get(i));
                    ((EXistResource) document).setMimeType(mimeType.getName());
                    current.storeResource(document);
                    messageln(DONE);
                    messageln("parsing " + FileUtils.sizeQuietly(files.get(i)) + " bytes took " + (System.currentTimeMillis() - start) + "ms." + EOL);
                    bytes += FileUtils.sizeQuietly(files.get(i));
                }
            }
            messageln("parsed " + bytes + " bytes in " + (System.currentTimeMillis() - start0) + "ms.");
            return true;
        } catch (final IOException e) {
            e.printStackTrace();
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e);
        }
    }


    private synchronized boolean findGZipRecursive(final Collection collection, final Path dir, final XmldbURI base) throws XMLDBException, IOException {
        final List<Path> files = FileUtils.list(dir);
        Collection c;
        EXistCollectionManagementService mgtService;
        //The XmldbURIs here aren't really used...
        XmldbURI next;
        MimeType mimeType;
        int i = 0;
        for (final Path file : files) {
            i++;
            next = base.append(FileUtils.fileName(file));
            try {
                if (Files.isDirectory(file)) {
                    messageln("entering directory " + file.toAbsolutePath());
                    c = collection.getChildCollection(FileUtils.fileName(file));
                    if (c == null) {
                        mgtService = collection.getService(EXistCollectionManagementService.class);
                        c = mgtService.createCollection(XmldbURI.xmldbUriFor(FileUtils.fileName(file)));
                    }
                    if (c instanceof Observable && options.verbose) {
                        final ProgressObserver observer = new ProgressObserver();
                        ((Observable) c).addObserver(observer);
                    }
                    findGZipRecursive(c, file, next);
                } else {
                    final long start1 = System.currentTimeMillis();
                    final String compressedName = FileUtils.fileName(file);
                    String localName = compressedName;
                    final String[] cSuffix = {".gz", ".Z"};
                    boolean isCompressed = false;
                    for (final String suf : cSuffix) {
                        if (localName.endsWith(suf)) {
                            // Removing compressed prefix to validate
                            localName = compressedName.substring(0, localName.length() - suf.length());
                            isCompressed = true;
                            break;
                        }
                    }
                    mimeType = MimeTable.getInstance().getContentTypeFor(localName);
                    if (mimeType == null) {
                        messageln("File " + compressedName + " has an unknown suffix. Cannot determine file type.");
                        mimeType = MimeType.BINARY_TYPE;
                    }
                    try (final Resource document = collection.createResource(compressedName, mimeType.getXMLDBType())) {
                        message("storing document " + compressedName + " (" + i + " of " + files.size() + ") " + "...");
                        document.setContent(isCompressed ? new GZIPInputSource(file) : file);
                        ((EXistResource) document).setMimeType(mimeType.getName());
                        collection.storeResource(document);
                        ++filesCount;
                        messageln(" " + Files.size(file) + (isCompressed ? " compressed" : "") + " bytes in "
                                + (System.currentTimeMillis() - start1) + "ms.");
                    }
                }
            } catch (final URISyntaxException e) {
                errorln("uri syntax exception parsing " + file.toAbsolutePath() + ": " + e.getMessage());
            }
        }
        return true;
    }

    /**
     * stores given Resource
     *
     * @param fileName simple file or directory
     * @return true if the operation succeeded
     * @throws XMLDBException in case of database error storing the resource
     * @throws IOException    in case of a read error
     */
    protected synchronized boolean parseGZip(String fileName) throws XMLDBException, IOException {
        //TODO : why is this test for ? Fileshould make it, shouldn't it ? -pb
        fileName = fileName.replace('/', java.io.File.separatorChar).replace('\\',
                java.io.File.separatorChar);
        final Path file = Paths.get(fileName);
        // String xml;
        if (current instanceof Observable && options.verbose) {
            final ProgressObserver observer = new ProgressObserver();
            ((Observable) current).addObserver(observer);
        }
        final List<Path> files;
        if (Files.isReadable(file)) {
            // TODO, same logic as for the graphic client
            if (Files.isDirectory(file)) {
                if (options.reindexRecurse) {
                    filesCount = 0;
                    final long start = System.currentTimeMillis();
                    final boolean result = findGZipRecursive(current, file, path);
                    messageln("storing " + filesCount + " compressed files took "
                            + ((System.currentTimeMillis() - start) / 1000)
                            + "sec.");
                    return result;
                }
                files = FileUtils.list(file);
            } else {
                files = new ArrayList<>();
                files.add(file);
            }
        } else {
            final DirectoryScanner directoryScanner = new DirectoryScanner();
            directoryScanner.setIncludes(new String[]{fileName});
            //TODO(AR) do we need to call scanner.setBasedir()?
            directoryScanner.setCaseSensitive(true);
            directoryScanner.scan();

            final String[] includedFiles = directoryScanner.getIncludedFiles();
            files = new ArrayList<>(includedFiles.length);
            for (final String includedFile : includedFiles) {
//                files.add(baseDir.resolve(includedFile));
                files.add(Paths.get(includedFile));
            }
        }

        final long start0 = System.currentTimeMillis();
        long bytes = 0;
        MimeType mimeType;
        int i = 0;
        for (final Path p : files) {
            i++;
            if (Files.isDirectory(p)) {
                continue;
            }
            final long start = System.currentTimeMillis();
            final String compressedName = FileUtils.fileName(p);
            String localName = compressedName;
            final String[] cSuffix = {".gz", ".Z"};
            boolean isCompressed = false;
            for (final String suf : cSuffix) {
                if (localName.endsWith(suf)) {
                    // Removing compressed prefix to validate
                    localName = compressedName.substring(0, localName.length() - suf.length());
                    isCompressed = true;
                    break;
                }
            }
            mimeType = MimeTable.getInstance().getContentTypeFor(localName);
            if (mimeType == null) {
                mimeType = MimeType.BINARY_TYPE;
            }
            try (final Resource document = current.createResource(compressedName, mimeType.getXMLDBType())) {
                message("storing document " + compressedName + " (" + i
                        + " of " + Files.size(p) + ") ...");
                document.setContent(isCompressed ? new GZIPInputSource(p) : p);
                ((EXistResource) document).setMimeType(mimeType.getName());
                current.storeResource(document);
                messageln(DONE);
                messageln("parsing " + Files.size(p) + (isCompressed ? " compressed" : "") + " bytes took "
                        + (System.currentTimeMillis() - start) + "ms." + EOL);
                bytes += Files.size(p);
            }
        }
        messageln("parsed " + bytes + " compressed bytes in "
                + (System.currentTimeMillis() - start0) + "ms.");
        return true;
    }

    /**
     * stores given Resource.
     *
     * @param zipPath Path to a zip file
     * @return true if operation succeeded
     * @throws XMLDBException in case of error writing to the database
     */
    protected synchronized boolean parseZip(final Path zipPath) throws XMLDBException {
        try (final ZipFile zfile = new ZipFile(zipPath.toFile())) {
            if (current instanceof Observable && options.verbose) {
                final ProgressObserver observer = new ProgressObserver();
                ((Observable) current).addObserver(observer);
            }

            final long start0 = System.currentTimeMillis();
            long bytes = 0;
            final Enumeration<? extends ZipEntry> e = zfile.entries();
            int number = 0;

            Collection base = current;
            String baseStr = "";
            while (e.hasMoreElements()) {
                number++;
                final ZipEntry ze = e.nextElement();
                final String zeName = ze.getName().replace('\\', '/');

                if (!Paths.get("/db").resolve(zeName).normalize().startsWith(Paths.get("/db"))) {
                    throw new IOException("Detected archive exit attack! zipFile=" + zipPath.toAbsolutePath() + ", entry=" + ze.getName());
                }

                final String[] pathSteps = zeName.split("/");
                final StringBuilder currStr = new StringBuilder(pathSteps[0]);
                for (int i = 1; i < pathSteps.length - 1; i++) {
                    currStr
                            .append("/")
                            .append(pathSteps[i]);
                }
                if (!currStr.toString().equals(baseStr)) {
                    base = current;
                    for (int i = 0; i < pathSteps.length - 1; i++) {
                        Collection c = base.getChildCollection(pathSteps[i]);
                        if (c == null) {
                            final EXistCollectionManagementService mgtService = base.getService(EXistCollectionManagementService.class);
                            c = mgtService.createCollection(XmldbURI.xmldbUriFor(pathSteps[i]));
                        }
                        base = c;
                    }
                    if (base instanceof Observable && options.verbose) {
                        final ProgressObserver observer = new ProgressObserver();
                        ((Observable) base).addObserver(observer);
                    }
                    baseStr = currStr.toString();
                    messageln("entering directory " + baseStr);
                }
                if (!ze.isDirectory()) {
                    final String localName = pathSteps[pathSteps.length - 1];
                    final long start = System.currentTimeMillis();
                    MimeType mimeType = MimeTable.getInstance().getContentTypeFor(localName);
                    if (mimeType == null) {
                        mimeType = MimeType.BINARY_TYPE;
                    }
                    try (final Resource document = base.createResource(localName, mimeType.getXMLDBType())) {
                        message("storing Zip-entry document " + localName + " (" + (number)
                                + " of " + zfile.size() + ") ...");
                        document.setContent(new ZipEntryInputSource(zfile, ze));
                        ((EXistResource) document).setMimeType(mimeType.getName());
                        base.storeResource(document);
                        messageln(DONE);
                        messageln("parsing " + ze.getSize() + " bytes took "
                                + (System.currentTimeMillis() - start) + "ms." + EOL);
                        bytes += ze.getSize();
                    }
                }
            }
            messageln("parsed " + bytes + " bytes in "
                    + (System.currentTimeMillis() - start0) + "ms.");
        } catch (final URISyntaxException e) {
            errorln("uri syntax exception parsing a ZIP entry from " + zipPath + ": " + e.getMessage());
        } catch (final IOException e) {
            errorln("could not parse ZIP file " + zipPath.toAbsolutePath() + ": " + e.getMessage());
        }
        return true;
    }

    /**
     * Method called by the store Dialog
     *
     * @param files  : selected
     * @param upload : GUI object
     * @return true if the operation succeeded
     * @throws XMLDBException in case of an error uploading the resources
     */
    protected synchronized boolean parse(final List<Path> files, final UploadDialog upload) throws XMLDBException {
        final Collection uploadRootCollection = current;
        if (!upload.isVisible()) {
            upload.setVisible(true);
        }

        if (uploadRootCollection instanceof Observable) {
            ((Observable) uploadRootCollection).addObserver(upload.getObserver());
        }
        upload.setTotalSize(FileUtils.sizeQuietly(files));
        for (final Path file : files) {
            if (upload.isCancelled()) {
                break;
            }
            // should replace the lines above
            store(uploadRootCollection, file, upload);
        }
        if (uploadRootCollection instanceof Observable) {
            ((Observable) uploadRootCollection).deleteObservers();
        }
        upload.uploadCompleted();
        return true;
    }

    /**
     * Pass to this method a java file object
     * (may be a file or a directory), GUI object
     * will create relative collections or resources
     * recursively
     */

    private void store(final Collection collection, final Path file, final UploadDialog upload) {

        // cancel, stop crawl
        if (upload.isCancelled()) {
            return;
        }

        // can't read there, inform client
        if (!Files.isReadable(file)) {
            upload.showMessage(file.toAbsolutePath() + " impossible to read ");
            return;
        }

        final XmldbURI filenameUri;
        try {
            filenameUri = XmldbURI.xmldbUriFor(FileUtils.fileName(file));
        } catch (final URISyntaxException e1) {
            upload.showMessage(file.toAbsolutePath() + " could not be encoded as a URI");
            return;
        }

        // Directory, create collection, and crawl it
        if (Files.isDirectory(file)) {
            Collection c = null;
            try {
                c = collection.getChildCollection(filenameUri.toString());
                if (c == null) {
                    final EXistCollectionManagementService mgtService = collection.getService(EXistCollectionManagementService.class);
                    c = mgtService.createCollection(filenameUri);
                }
            } catch (final XMLDBException e) {
                upload.showMessage("Impossible to create a collection " + file.toAbsolutePath() + ": " + e.getMessage());
                e.printStackTrace();
            }

            // change displayed collection if it's OK
            upload.setCurrentDir(file.toAbsolutePath().toString());
            if (c instanceof Observable) {
                ((Observable) c).addObserver(upload.getObserver());
            }
            // maybe a depth or recurs flag could be added here
            final Collection childCollection = c;
            try (final Stream<Path> children = Files.list(file)) {
                children.forEach(child -> store(childCollection, child, upload));
            } catch (final IOException e) {
                upload.showMessage("Impossible to upload " + file.toAbsolutePath() + ": " + e.getMessage());
                e.printStackTrace();
            }

            return;
        }

        // File, create and store resource
        if (!Files.isDirectory(file)) {
            upload.reset();
            upload.setCurrent(FileUtils.fileName(file));
            final long fileSize = FileUtils.sizeQuietly(file);
            upload.setCurrentSize(fileSize);

            MimeType mimeType = MimeTable.getInstance().getContentTypeFor(FileUtils.fileName(file));
            // unknown mime type, here prefered is to do nothing
            if (mimeType == null) {
                upload.showMessage(file.toAbsolutePath() +
                        " - unknown suffix. No matching mime-type found in : " +
                        MimeTable.getInstance().getSrc());

                // if some one prefers to store it as binary by default, but dangerous
                mimeType = MimeType.BINARY_TYPE;
            }

            try (final Resource res = collection.createResource(filenameUri.toString(), mimeType.getXMLDBType())) {
                ((EXistResource) res).setMimeType(mimeType.getName());
                res.setContent(file);
                collection.storeResource(res);
                ++filesCount;
                this.totalLength += fileSize;
                upload.setStoredSize(this.totalLength);
            } catch (final XMLDBException e) {
                upload.showMessage("Impossible to store a resource "
                        + file.toAbsolutePath() + ": " + e.getMessage());
            }
        }
    }


    private void mkcol(final XmldbURI collPath) throws XMLDBException {
        messageln("creating '" + collPath + "'");
        final XmldbURI[] segments = collPath.getPathSegments();
        XmldbURI p = XmldbURI.ROOT_COLLECTION_URI;
        for (int i = 1; i < segments.length; i++) {
            p = p.append(segments[i]);
            final Collection c = DatabaseManager.getCollection(properties.getProperty(URI) + p, properties.getProperty(USER), properties.getProperty(PASSWORD));
            if (c == null) {
                final EXistCollectionManagementService mgtService = current.getService(EXistCollectionManagementService.class);
                current = mgtService.createCollection(segments[i]);
            } else {
                current = c;
            }
        }
        path = p;
    }

    protected Collection getCollection(final String path) throws XMLDBException {
        return DatabaseManager.getCollection(properties.getProperty(URI) + path, properties.getProperty(USER), properties.getProperty(PASSWORD));
    }

    private Properties loadClientProperties() {
        try {
            final Properties properties = ConfigurationHelper.loadProperties("client.properties", getClass());
            if (properties != null) {
                return properties;
            }
            consoleErr("WARN - Unable to find client.properties");
        } catch (final IOException e) {
            consoleErr("WARN - Unable to load client.properties: " + e.getMessage());
        }

        // return new empty properties
        return new Properties();
    }

    /**
     * Set any relevant properties from command line arguments
     *
     * @param options CommandLineOptions
     * @param props   Client configuration
     */
    protected void setPropertiesFromCommandLine(final CommandlineOptions options, final Properties props) {
        options.options.forEach(properties::setProperty);

        options.username.ifPresent(username -> props.setProperty(USER, username));
        options.password.ifPresent(password -> props.setProperty(PASSWORD, password));
        boolean needPassword = options.username.isPresent() && !options.password.isPresent();
        if (options.useSSL) {
            props.setProperty(SSL_ENABLE, "TRUE");
        }
        if (options.embedded) {
            props.setProperty(LOCAL_MODE, "TRUE");
            props.setProperty(URI, XmldbURI.EMBEDDED_SERVER_URI.toString());
        }
        options.embeddedConfig.ifPresent(config -> properties.setProperty(CONFIGURATION, config.toAbsolutePath().toString()));
        if (options.noEmbeddedMode) {
            props.setProperty(NO_EMBED_MODE, "TRUE");
        }
    }

    /**
     * Process the command line options
     *
     * @return true if all are successful, otherwise false
     * @throws java.io.IOException
     */
    private boolean processCommandLineActions() throws IOException {
        final boolean foundCollection = options.setCol.isPresent();

        // process command-line actions
        if (options.reindex) {
            if (!foundCollection) {
                consoleErr("Please specify target collection with --collection");
                shutdown(false);
                return false;
            }
            try {
                reindex();
            } catch (final XMLDBException e) {
                consoleErr("XMLDBException while reindexing collection: " + getExceptionMessage(e));
                e.printStackTrace();
                return false;
            }
        }

        if (options.rmCol.isPresent()) {
            try {
                rmcol(options.rmCol.get());
            } catch (final XMLDBException e) {
                consoleErr("XMLDBException while removing collection: " + getExceptionMessage(e));
                e.printStackTrace();
                return false;
            }
        }

        if (options.mkCol.isPresent()) {
            try {
                mkcol(options.mkCol.get());
            } catch (final XMLDBException e) {
                consoleErr("XMLDBException during mkcol: " + getExceptionMessage(e));
                e.printStackTrace();
                return false;
            }
        }

        if (options.getDoc.isPresent()) {
            try {
                final Resource res = retrieve(options.getDoc.get());
                if (res != null) {
                    // String data;
                    if (XML_RESOURCE.equals(res.getResourceType())) {
                        if (options.outputFile.isPresent()) {
                            writeOutputFile(options.outputFile.get(), res.getContent());
                        } else {
                            consoleOut(res.getContent().toString());
                        }
                    } else {
                        if (options.outputFile.isPresent()) {
                            ((ExtendedResource) res).getContentIntoAFile(options.outputFile.get());
                            ((EXistResource) res).freeResources();
                        } else {
                            ((ExtendedResource) res).getContentIntoAStream(System.out);
                            consoleOut("");
                        }
                    }
                }
            } catch (final XMLDBException e) {
                consoleErr("XMLDBException while trying to retrieve document: " + getExceptionMessage(e));
                e.printStackTrace();
                return false;
            }
        } else if (options.rmDoc.isPresent()) {
            if (!foundCollection) {
                consoleErr("Please specify target collection with --collection");
            } else {
                try {
                    remove(options.rmDoc.get());
                } catch (final XMLDBException e) {
                    consoleErr("XMLDBException during parse: " + getExceptionMessage(e));
                    e.printStackTrace();
                    return false;
                }
            }
        } else if (!options.parseDocs.isEmpty()) {
            if (!foundCollection) {
                consoleErr("Please specify target collection with --collection");
            } else {
                for (final Path path : options.parseDocs) {
                    try {
                        parse(path);
                    } catch (final XMLDBException e) {
                        consoleErr("XMLDBException during parse: " + getExceptionMessage(e));
                        e.printStackTrace();
                        return false;
                    }
                }
            }
        } else if (options.xpath.isPresent() || !options.queryFiles.isEmpty()) {
            String xpath = null;
            if (!options.queryFiles.isEmpty()) {
                try (final BufferedReader reader = Files.newBufferedReader(options.queryFiles.getFirst())) {
                    final StringBuilder buf = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        buf.append(line);
                        buf.append(EOL);
                    }
                    xpath = buf.toString();
                }
            }

            // if no argument has been found, read query from stdin
            if (options.xpath.isPresent()) {

                final String xpathStr = options.xpath.get();
                if (!xpathStr.equals(CommandlineOptions.XPATH_STDIN)) {
                    xpath = xpathStr;
                } else {
                    // read from stdin
                    try (final BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in))) {
                        final StringBuilder buf = new StringBuilder();
                        String line;
                        while ((line = stdin.readLine()) != null) {
                            buf.append(line);
                            buf.append(EOL);
                        }
                        xpath = buf.toString();
                    } catch (final IOException e) {
                        consoleErr("failed to read query from stdin");
                        xpath = null;
                        return false;
                    }
                }
            }

            if (xpath != null) {
                try {
                    final ResourceSet result = find(xpath);

                    final int maxResults = options.howManyResults.filter(n -> n > 0).orElse((int) result.getSize());
                    if (options.outputFile.isPresent()) {
                        try (final OutputStream fos = new BufferedOutputStream(Files.newOutputStream(options.outputFile.get()));
                             final BufferedOutputStream bos = new BufferedOutputStream(fos);
                             final PrintStream ps = new PrintStream(bos)
                        ) {

                            for (int i = 0; i < maxResults && i < result.getSize(); i++) {
                                final Resource res = result.getResource(i);
                                if (res instanceof ExtendedResource) {
                                    ((ExtendedResource) res).getContentIntoAStream(ps);
                                } else {
                                    ps.print(res.getContent().toString());
                                }
                            }
                        }
                    } else {
                        for (int i = 0; i < maxResults && i < result.getSize(); i++) {
                            final Resource res = result.getResource(i);
                            if (res instanceof ExtendedResource) {
                                ((ExtendedResource) res).getContentIntoAStream(System.out);
                            } else {
                                consoleOut(String.valueOf(res.getContent()));
                            }
                        }
                    }
                } catch (final XMLDBException e) {
                    consoleErr("XMLDBException during query: " + getExceptionMessage(e));
                    e.printStackTrace();
                    return false;
                }
            }

        } else if (options.xupdateFile.isPresent()) {
            try {
                xupdate(options.setDoc, options.xupdateFile.get());
            } catch (final XMLDBException e) {
                consoleErr("XMLDBException during xupdate: " + getExceptionMessage(e));
                return false;
            } catch (final IOException e) {
                consoleErr("IOException during xupdate: " + getExceptionMessage(e));
                return false;
            }
        }

        return true;
    }

    /**
     * Ask user for login data using gui.
     *
     * @param props Client properties
     * @return FALSE when pressed cancel, TRUE is sucessfull.
     */
    private boolean getGuiLoginData(final Properties props) {
        return getGuiLoginData(props, ClientFrame::getLoginData);
    }

    boolean getGuiLoginData(final Properties props, final UnaryOperator<Properties> loginPropertyOperator) {
        final Properties loginData = loginPropertyOperator.apply(props);
        if (loginData == null || loginData.isEmpty()) {
            // User pressed <cancel>
            return false;
        }
        props.putAll(loginData);

        return true;
    }

    /**
     * Reusable method for connecting to database. Exits process on failure.
     */
    private void connectToDatabase() {
        try {
            connect();
        } catch (final Exception cnf) {
            if (options.startGUI && frame != null) {
                frame.setStatus("Connection to database failed; message: " + cnf.getMessage());
            } else {
                consoleErr("Connection to database failed; message: " + cnf.getMessage());
            }
            cnf.printStackTrace();
            System.exit(SystemExitCodes.CATCH_ALL_GENERAL_ERROR_EXIT_CODE);
        }
    }

    /**
     * Main processing method for the InteractiveClient object
     *
     * @return true on success, false on failure
     * @throws Exception if an error occurs
     */
    public boolean run() throws Exception {
        this.path = options.setCol.orElse(XmldbURI.ROOT_COLLECTION_URI);

        // get eXist home
        final Optional<Path> home = ConfigurationHelper.getExistHome();

        // get default configuration filename from the driver class and set it in properties
        applyDefaultConfig(home);

        properties.putAll(loadClientProperties());

        setPropertiesFromCommandLine(options, properties);

        printNotice();

        // Fix "uri" property: Excalibur CLI can't parse dashes, so we need to URL encode them:
        properties.setProperty(URI, URLDecoder.decode(properties.getProperty(URI), UTF_8));

        final boolean interactive = isInteractive();

        // prompt for password if needed
        if (checkLoginInfos(interactive)) {
            return false;
        }

        historyFile = home.map(h -> h.resolve(".exist_history")).orElse(Paths.get(".exist_history"));
        queryHistoryFile = home.map(h -> h.resolve(".exist_query_history")).orElse(Paths.get(".exist_query_history"));
        readQueryHistory();

        if (interactive) {
            // in gui mode we use Readline for history management
            // initialize Readline library
            final Terminal terminal = TerminalBuilder.builder()
                    .build();

            final History history = new DefaultHistory();

            console = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .variable(LineReader.HISTORY_FILE, historyFile)
                    .history(history)
                    .completer(new CollectionCompleter())
                    .build();
        }

        // connect to the db
        connectToDatabase();

        if (current == null) {
            if (options.startGUI && frame != null) {
                frame.setStatus("Could not retrieve collection " + path);
            } else {
                consoleErr("Could not retrieve collection " + path);
            }
            shutdown(false);
            return false;
        }

        final boolean processingOK = processCommandLineActions();
        if (!processingOK) {
            return false;
        }

        if (interactive) {
            if (initializeGui()) return false;
        } else {
            shutdown(false);
        }
        return true;
    }

    private boolean checkLoginInfos(boolean interactive) {
        if (!hasLoginDetails(options)) {
            if (interactive && options.startGUI) {
                final boolean haveLoginData = getGuiLoginData(properties);
                if (!haveLoginData) {
                    return true;
                }

            } else if (options.username.isPresent() && !options.password.isPresent()) {
                try {
                    properties.setProperty(PASSWORD, console.readLine("password: ", '*'));
                } catch (final Exception e) {
                    // ignore errors
                }
            }
        }
        return false;
    }

    private void applyDefaultConfig(Optional<Path> home) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        Optional<Path> configFile = ConfigurationHelper.getFromSystemProperty();
        if (!configFile.isPresent()) {
            final Class<?> cl = Class.forName(properties.getProperty(DRIVER));
            final Field CONF_XML = cl.getDeclaredField("CONF_XML");
            if (CONF_XML != null && home.isPresent()) {
                configFile = Optional.ofNullable(ConfigurationHelper.lookup((String) CONF_XML.get("")));
            }
        }
        configFile.ifPresent(value -> properties.setProperty(CONFIGURATION, value.toString()));
    }

    final boolean isInteractive() {
        boolean interactive = true;
        if ((!options.parseDocs.isEmpty()) || options.rmDoc.isPresent() || options.getDoc.isPresent()
                || options.rmCol.isPresent() || options.xpath.isPresent() || (!options.queryFiles.isEmpty())
                || options.xupdateFile.isPresent() || options.reindex) {
            interactive = false;
        }
        return interactive;
    }

    final boolean initializeGui() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        initializeFrame();

        // enter interactive mode
        if (!options.startGUI || frame == null) {

            // No gui
            ClientAction.call(this::getResources, e -> {
                consoleErr("XMLDBException while "
                        + "retrieving collection contents: "
                        + getExceptionMessage(e));
                e.getCause().printStackTrace();
            });
            return true;

        } else {

            // with gui ; re-login posibility
            boolean retry = true;

            while (retry) {

                AtomicReference<String> errorMessageReference = new AtomicReference<>("");
                ClientAction.call(this::getResources, e -> {
                    errorMessageReference.set(getExceptionMessage(e));
                    ClientFrame.showErrorMessage(
                            "XMLDBException occurred while retrieving collection: "
                                    + errorMessageReference, e);
                });

                // Determine error text. For special reasons we can retry
                // to connect.
                String errorMessage = errorMessageReference.get();
                if (isRetryableError(errorMessage)) {

                    final boolean haveLoginData = getGuiLoginData(properties);
                    if (!haveLoginData) {
                        // pressed cancel
                        return true;
                    }

                    // Need to shutdown ?? ask wolfgang
                    shutdown(false);

                    // connect to the db
                    connectToDatabase();

                } else if (!errorMessage.isEmpty()) {
                    // No pattern match, but we have an error. stop here
                    frame.dispose();
                    return true;
                } else {
                    // No error message, continue startup.
                    retry = false;
                }
            }
        }

        messageln(EOL + "type help or ? for help.");

        if (options.openQueryGUI) {
            final QueryDialog qd = new QueryDialog(this, current, properties);
            qd.setLocation(100, 100);
            qd.setVisible(true);
        } else if (!options.startGUI) {
            readlineInputLoop();
        } else {
            frame.displayPrompt();
        }
        return false;
    }

    boolean isRetryableError(String errorMessage) {
        return errorMessage.contains("Invalid password for user") ||
                errorMessage.contains("Connection refused: connect") ||
                UNKNOWN_USER_PATTERN.matcher(errorMessage).find();
    }

    private void initializeFrame() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        if (options.startGUI) {
            setLookAndFeel();

            frame = createClientFrame();
            frame.setLocation(100, 100);
            frame.setSize(500, 500);
            frame.setVisible(true);
        }
    }

    private void setLookAndFeel() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (final UnsupportedLookAndFeelException ulafe) {
            consoleErr("Warning: Unable to set native look and feel: " + ulafe.getMessage());
        }
    }

    ClientFrame createClientFrame() {
        return new ClientFrame(this, path, properties);
    }

    private boolean hasLoginDetails(final CommandlineOptions options) {
        return options.username.isPresent()
                && options.password.isPresent()
                && (options.embedded || options.options.containsKey("uri"));
    }

    public static String getExceptionMessage(Throwable e) {
        Throwable cause;
        while ((cause = e.getCause()) != null) {
            e = cause;
        }
        return e.getMessage();
    }

    /**
     * Read Query History file.
     */
    protected void readQueryHistory() {
        if (!Files.isReadable(queryHistoryFile)) {
            return;
        }
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            final DocumentBuilder builder = factory.newDocumentBuilder();
            try (InputStream in = Files.newInputStream(queryHistoryFile)) {
                final Document doc = builder.parse(in);
                final NodeList nodes = doc.getElementsByTagName("query");
                for (int i = 0; i < nodes.getLength(); i++) {
                    final Element query = (Element) nodes.item(i);
                    final StringBuilder value = new StringBuilder();
                    Node next = query.getFirstChild();
                    while (next != null) {
                        value.append(next.getTextContent());
                        next = next.getNextSibling();
                    }
                    queryHistory.addLast(value.toString());
                }
            }
        } catch (final Exception e) {
            if (options.startGUI) {
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
            console.getHistory().save();
        } catch (final IOException e) {
            consoleErr("Could not write history File to " + historyFile.toAbsolutePath());
        }

        final SAXSerializer serializer = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
        try (final BufferedWriter writer = Files.newBufferedWriter(queryHistoryFile, StandardCharsets.UTF_8)) {
            serializer.setOutput(writer, null);
            int p = 0;
            if (queryHistory.size() > 20) {
                p = queryHistory.size() - 20;
            }
            final AttributesImpl attrs = new AttributesImpl();
            serializer.startDocument();
            serializer.startElement(XMLConstants.NULL_NS_URI, "history", "history", attrs);
            for (final ListIterator<String> i = queryHistory.listIterator(p); i.hasNext(); ) {
                serializer.startElement(XMLConstants.NULL_NS_URI, "query", "query", attrs);
                final String next = i.next();
                serializer.characters(next.toCharArray(), 0, next.length());
                serializer.endElement(XMLConstants.NULL_NS_URI, "query", "query");
            }
            serializer.endElement(XMLConstants.NULL_NS_URI, "history", "history");
            serializer.endDocument();
        } catch (final IOException e) {
            consoleErr("IO error while writing query history.");
        } catch (final SAXException e) {
            consoleErr("SAX exception while writing query history.");
        } finally {
            SerializerPool.getInstance().returnObject(serializer);
        }

    }

    public void readlineInputLoop() {
        String line;
        boolean cont = true;
        while (cont) {
            try {
                if ("true".equals(properties.getProperty(COLORS))) {
                    line = console.readLine(ANSI_CYAN + "exist:" + path + "> "
                            + ANSI_WHITE);
                } else {
                    line = console.readLine("exist:" + path + "> ");
                }
                if (line != null) {
                    cont = process(line);
                }

            } catch (final EndOfFileException e) {
                break;
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }

        try {
            console.getHistory().save();
        } catch (final IOException e) {
            consoleErr("Could not write history File to " + historyFile.toAbsolutePath());
        }
        shutdown(false);
        messageln("quit.");
    }

    protected final void shutdown(final boolean force) {
        lazyTraceWriter.ifPresent(writer -> {
            try {
                writer.write("</query-log>");
                writer.close();
            } catch (final IOException e1) {
            }
        });

        try {
            final DatabaseInstanceManager mgr = current.getService(DatabaseInstanceManager.class);
            if (mgr == null) {
                consoleErr("service is not available");
            } else if (mgr.isLocalInstance() || force) {
                consoleOut("shutting down database...");
                mgr.shutdown();
            }
        } catch (final XMLDBException e) {
            consoleErr("database shutdown failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                current.close();
                current = null;

                DatabaseManager.deregisterDatabase(database);
                database = null;
            } catch (final XMLDBException e) {
                consoleErr("unable to close collection: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * print copyright notice - after parsing command line options, or it can't be silenced!
     */
    public void printNotice() {
        if (!options.quiet) {
            messageln(getNotice());
        }
    }

    public String getNotice() {
        return getNotice(SystemProperties.getInstance()::getSystemProperty);
    }

    String getNotice(BinaryOperator<String> propertyAction) {
        final StringBuilder builder = new StringBuilder();
        builder.append(propertyAction.apply("product-name", "eXist-db"));
        builder.append(" version ");
        builder.append(propertyAction.apply("product-version", "unknown"));
        final String gitCommitId = propertyAction.apply("git-commit", "");
        if (!gitCommitId.isEmpty()) {
            builder.append(" (").append(gitCommitId).append(")");
        }
        builder.append(", Copyright (C) 2001-");
        builder.append(Calendar.getInstance().get(Calendar.YEAR));
        builder.append(" The eXist-db Project");
        builder.append(EOL);
        builder.append("eXist-db comes with ABSOLUTELY NO WARRANTY.");
        builder.append(EOL);
        builder.append("This is free software, and you are welcome to redistribute it");
        builder.append(EOL);
        builder.append("under certain conditions; for details read the license file.");
        builder.append(EOL);
        return builder.toString();
    }

    final void message(final String msg) {
        if (options.quiet) {
            return;
        }
        if (options.startGUI && frame != null) {
            frame.display(msg);
        } else {
            consoleOut(msg);
        }
    }

    final void messageln(final String msg) {
        if (options.quiet) {
            return;
        }
        if (options.startGUI && frame != null) {
            frame.display(msg + EOL);
        } else {
            consoleOut(msg);
        }
    }

    static final void consoleOut(final String msg) {
        System.out.println(msg); //NOSONAR this has to go to the console
    }

    final void errorln(final String msg) {
        if (options.startGUI && frame != null) {
            frame.display(msg + EOL);
        } else {
            consoleErr(msg);
        }
    }

    static final void consoleErr(final String msg) {
        System.err.println(msg); //NOSONAR this has to go to the console
    }

    private Collection resolveCollection(final XmldbURI path) throws XMLDBException {
        return DatabaseManager.getCollection(
                properties.getProperty(URI) + path,
                properties.getProperty(USER),
                properties.getProperty(PASSWORD));
    }

    private Resource resolveResource(final XmldbURI path) throws XMLDBException {
        try {
            final XmldbURI collectionPath =
                    path.numSegments() == 1 ?
                            XmldbURI.xmldbUriFor(current.getName()) : path.removeLastSegment();

            final XmldbURI resourceName = path.lastSegment();

            final Collection collection = resolveCollection(collectionPath);

            if (collection == null) {
                messageln("Collection " + collectionPath + " not found.");
                return null;
            }

            messageln("Locating resource " + resourceName + " in collection " + collection.getName());

            return collection.getResource(resourceName.toString());
        } catch (final URISyntaxException e) {
            errorln("could not parse collection name into a valid URI: " + e.getMessage());
        }
        return null;
    }

    private class CollectionCompleter implements Completer {

        @Override
        public void complete(final LineReader lineReader, final ParsedLine parsedLine, final List<Candidate> candidates) {
            final String buffer = parsedLine.line();
            int p = buffer.lastIndexOf(' ');
            final String toComplete;
            if (p > -1 && ++p < buffer.length()) {
                toComplete = buffer.substring(p);
            } else {
                toComplete = buffer;
            }
            final Set<String> set = completions.tailSet(toComplete);
            if (set != null && !set.isEmpty()) {
                for (final String next : completions.tailSet(toComplete)) {
                    if (next.startsWith(toComplete)) {
                        candidates.add(new Candidate(next, next, null, null, null, null, true));
                    }
                }
            }
        }
    }

    public static class ProgressObserver implements Observer {

        final ProgressBar elementsProgress = new ProgressBar("storing elements");
        Observable lastObservable = null;
        final ProgressBar parseProgress = new ProgressBar("storing nodes   ");

        @Override
        public void update(final Observable o, final Object obj) {
            final ProgressIndicator ind = (ProgressIndicator) obj;
            if (lastObservable == null || o != lastObservable) {
                consoleOut("");
            }

            if (o instanceof ElementIndex) {
                elementsProgress.set(ind.getValue(), ind.getMax());
            } else {
                parseProgress.set(ind.getValue(), ind.getMax());
            }

            lastObservable = o;
        }
    }

    private void writeOutputFile(final Path file, final Object data) throws IOException {
        try (final OutputStream os = new BufferedOutputStream(Files.newOutputStream(file))) {
            if (data instanceof byte[]) {
                os.write((byte[]) data);
            } else {
                try (final Writer writer = new OutputStreamWriter(os, Charset.forName(properties.getProperty(ENCODING)))) {
                    writer.write(data.toString());
                }
            }
        }
    }

    private static String formatString(String s1, final String s2, final int width) {
        final StringBuilder buf = new StringBuilder(width);
        if (s1.length() > width) {
            s1 = s1.substring(0, width - 1);
        }
        buf.append(s1);
        final int fill = width - (s1.length() + s2.length());
        for (int i = 0; i < fill; i++) {
            buf.append(' ');
        }
        buf.append(s2);
        return buf.toString();
    }

    public static Properties getSystemProperties() {
        final Properties sysProperties = new Properties();
        try {
            sysProperties.load(InteractiveClient.class.getClassLoader().getResourceAsStream("org/exist/system.properties"));
        } catch (final IOException e) {
            consoleErr("Unable to load system.properties from class loader");
        }

        return sysProperties;
    }

    public static ImageIcon getExistIcon(final Class clazz) {
        return new javax.swing.ImageIcon(clazz.getResource("/org/exist/client/icons/x.png"));
    }
}
