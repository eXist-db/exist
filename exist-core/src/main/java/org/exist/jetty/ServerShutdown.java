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
package org.exist.jetty;

import org.apache.xmlrpc.XmlRpcException;
import org.exist.client.InteractiveClient;
import org.exist.start.CompatibleJavaVersionCheck;
import org.exist.start.StartException;
import org.exist.util.ConfigurationHelper;
import org.exist.util.SystemExitCodes;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import se.softhouse.jargo.Argument;
import se.softhouse.jargo.ArgumentException;
import se.softhouse.jargo.CommandLineParser;
import se.softhouse.jargo.ParsedArguments;

import java.io.IOException;
import java.util.Properties;

import static org.exist.util.ArgumentUtil.getOpt;
import static se.softhouse.jargo.Arguments.helpArgument;
import static se.softhouse.jargo.Arguments.stringArgument;

/**
 * Call the main method of this class to shut down a running database instance.
 * 
 * @author wolf
 */
public class ServerShutdown {

    /* general arguments */
    private static final Argument<?> helpArg = helpArgument("-h", "--help");

    /* database connection arguments */
    private static final Argument<String> userArg = stringArgument("-u", "--user")
            .description("specify username (has to be a member of group dba).")
            .defaultValue("admin")
            .build();
    private static final Argument<String> passwordArg = stringArgument("-p", "--password")
            .description("specify password for the user.")
            .defaultValue("")
            .build();
    private static final Argument<String> uriArg = stringArgument("-l", "--uri")
            .description("the XML:DB URI of the database instance to be shut down.")
            .build();

    @SuppressWarnings("unchecked")
	public static void main(final String[] args) {
        try {
            CompatibleJavaVersionCheck.checkForCompatibleJavaVersion();

            final ParsedArguments arguments = CommandLineParser
                    .withArguments(userArg, passwordArg, uriArg)
                    .andArguments(helpArg)
                    .parse(args);

            process(arguments);
        } catch (final StartException e) {
            if (e.getMessage() != null && !e.getMessage().isEmpty()) {
                System.err.println(e.getMessage());
            }
            System.exit(e.getErrorCode());
        } catch (final ArgumentException e) {
            System.out.println(e.getMessageAndUsage());
            System.exit(SystemExitCodes.INVALID_ARGUMENT_EXIT_CODE);

        }
    }

    private static void process(final ParsedArguments arguments) {
        final Properties properties = loadProperties();

        final String user = arguments.get(userArg);
        final String passwd = arguments.get(passwordArg);

        String uri = getOpt(arguments, uriArg)
                .orElseGet(() -> properties.getProperty("uri", "xmldb:exist://localhost:8080/exist/xmlrpc"));

        try {
            // initialize database drivers
            final Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
            // create the default database
            final Database database = (Database) cl.newInstance();
            DatabaseManager.registerDatabase(database);
            if (!uri.endsWith(XmldbURI.ROOT_COLLECTION)) {
                uri = uri + XmldbURI.ROOT_COLLECTION;
            }
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
        try {
            final Properties properties = ConfigurationHelper.loadProperties("client.properties", InteractiveClient.class);
            if (properties != null) {
                return properties;
            }

            System.err.println("WARN - Unable to find client.properties");

        } catch (final IOException e) {
            System.err.println("WARN - Unable to load client.properties: " + e.getMessage());
        }

        // return new empty properties
        return new Properties();
    }
}
