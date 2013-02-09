/*
 * ServerShutdown.java - Jul 20, 2003
 * 
 * @author wolf
 */
package org.exist.jetty;

import org.apache.avalon.excalibur.cli.CLArgsParser;
import org.apache.avalon.excalibur.cli.CLOption;
import org.apache.avalon.excalibur.cli.CLOptionDescriptor;
import org.apache.avalon.excalibur.cli.CLUtil;

import org.apache.xmlrpc.XmlRpcException;

import org.exist.util.ConfigurationHelper;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import java.util.List;
import java.util.Properties;

/**
 * Call the main method of this class to shut down a running database instance.
 * 
 * @author wolf
 */
public class ServerShutdown {

    // command-line options
    private final static int HELP_OPT = 'h';

    private final static int URI_OPT = 'l';

    private final static int USER_OPT = 'u';

    private final static int PASSWORD_OPT = 'p';

    private final static CLOptionDescriptor OPTIONS[] = new CLOptionDescriptor[] {
            new CLOptionDescriptor("help",
                    CLOptionDescriptor.ARGUMENT_DISALLOWED, HELP_OPT,
                    "print help on command line options and exit."),
            new CLOptionDescriptor("user",
                    CLOptionDescriptor.ARGUMENT_REQUIRED, USER_OPT,
                    "specify username (has to be a member of group dba)."),
            new CLOptionDescriptor("password",
                    CLOptionDescriptor.ARGUMENT_REQUIRED, PASSWORD_OPT,
                    "specify password for the user."),
            new CLOptionDescriptor("uri", CLOptionDescriptor.ARGUMENT_REQUIRED,
                    URI_OPT,
                    "the XML:DB URI of the database instance to be shut down.") };

    @SuppressWarnings("unchecked")
	public static void main(String[] args) {
        final CLArgsParser optParser = new CLArgsParser(args, OPTIONS);
        if (optParser.getErrorString() != null) {
            System.err.println("ERROR: " + optParser.getErrorString());
            return;
        }
        final Properties properties = loadProperties();
        String user = "admin";
        String passwd = "";
        String uri = properties.getProperty("uri", "xmldb:exist://localhost:8080/exist/xmlrpc");
        final List<CLOption> opts = optParser.getArguments();
        for (final CLOption option : opts) {
            switch (option.getId()) {
                case HELP_OPT:
                    System.out.println("Usage: java "
                            + ServerShutdown.class.getName() + " [options]");
                    System.out.println(CLUtil.describeOptions(OPTIONS)
                            .toString());
                    return;
                case USER_OPT:
                    user = option.getArgument();
                    break;
                case PASSWORD_OPT:
                    passwd = option.getArgument();
                    break;
                case URI_OPT:
                    uri = option.getArgument();
            }
        }
        try {
            // initialize database drivers
            final Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
            // create the default database
            final Database database = (Database) cl.newInstance();
            DatabaseManager.registerDatabase(database);
            if (!uri.endsWith(XmldbURI.ROOT_COLLECTION))
                {uri = uri + XmldbURI.ROOT_COLLECTION;}
            final Collection root = DatabaseManager.getCollection(uri, user, passwd);
            final DatabaseInstanceManager manager = (DatabaseInstanceManager) root
                    .getService("DatabaseInstanceManager", "1.0");
            System.out.println("Shutting down database instance at ");
            System.out.println('\t' + uri);
            manager.shutdown();

        } catch (final XMLDBException e) {
            System.err.println("ERROR: " + e.getMessage());

            final Throwable t = e.getCause();
            if(t!=null && t instanceof XmlRpcException){
                System.err.println("CAUSE: "+t.getMessage());
            } else {
                e.printStackTrace();
            }
            
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    private static Properties loadProperties() {

        final Properties clientProps = new Properties();
        final File propFile = ConfigurationHelper.lookup("client.properties");
        InputStream pin = null;

        // Try to load from file
        try {
            pin = new FileInputStream(propFile);
        } catch (final FileNotFoundException ex) {
            // File not found, no exception handling
        }

        if (pin == null) {
            // Try to load via classloader
            pin = ServerShutdown.class.getResourceAsStream("client.properties");
        }

        if (pin != null) {
            // Try to load properties from stream
            try {
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
}
