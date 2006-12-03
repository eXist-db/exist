/*
 * eXist Open Source Native XML Database Copyright (C) 2001-06 Wolfgang M.
 * Meier meier@ifs.tu-darmstadt.de http://exist.sourceforge.net
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 * $Id$
 */
package org.exist.client;

import java.util.ArrayList;
import java.util.List;

import org.apache.avalon.excalibur.cli.CLOptionDescriptor;
import org.exist.xmldb.XmldbURI;

/**
 *
 * @author wessels
 */
public class CommandlineOptions {
    
    final static int HELP_OPT = 'h';
    final static int QUIET_OPT = 'q';
    final static int USER_OPT = 'u';
    final static int PASS_OPT = 'P';
    final static int LOCAL_OPT = 'l';
    final static int CONFIG_OPT = 'C';
    final static int PARSE_OPT = 'p';
    final static int COLLECTION_OPT = 'c';
    final static int RESOURCE_OPT = 'f';
    final static int REMOVE_OPT = 'r';
    final static int GET_OPT = 'g';
    final static int MKCOL_OPT = 'm';
    final static int RMCOL_OPT = 'R';
    final static int OPTION_OPT = 'o';
    final static int FIND_OPT = 'x';
    final static int RESULTS_OPT = 'n';
    final static int VERBOSE_OPT = 'v';
    final static int QUERY_FILE_OPT = 'F';
    final static int XUPDATE_OPT = 'X';
    final static int THREADS_OPT = 't';
    final static int RECURSE_DIRS_OPT = 'd';
    final static int NO_GUI_OPT = 's';
    final static int TRACE_QUERIES_OPT = 'T';
    final static int OUTPUT_FILE_OPT = 'O';
    final static int REINDEX_OPT = 'i';
    final static int QUERY_GUI_OPT = 'Q';
    final static int NO_EMBED_OPT = 'N';
    
    final static CLOptionDescriptor OPTIONS[] = new CLOptionDescriptor[]{
        new CLOptionDescriptor("help",
                CLOptionDescriptor.ARGUMENT_DISALLOWED, HELP_OPT,
                "print help on command line options and exit."),
                new CLOptionDescriptor("quiet",
                CLOptionDescriptor.ARGUMENT_DISALLOWED, QUIET_OPT,
                "be quiet. Just print errors."),
                new CLOptionDescriptor("verbose",
                CLOptionDescriptor.ARGUMENT_DISALLOWED, VERBOSE_OPT,
                "be verbose. Display progress information on put."),
                new CLOptionDescriptor("user",
                CLOptionDescriptor.ARGUMENT_REQUIRED, USER_OPT,
                "set username."),
                new CLOptionDescriptor("password",
                CLOptionDescriptor.ARGUMENT_REQUIRED, PASS_OPT,
                "specify password."),
                new CLOptionDescriptor("local",
                CLOptionDescriptor.ARGUMENT_DISALLOWED, LOCAL_OPT,
                "launch a local database instance. Otherwise client will connect to "
                + "URI specified in client.properties."),
                new CLOptionDescriptor("config",
                CLOptionDescriptor.ARGUMENT_REQUIRED, CONFIG_OPT,
                "specify alternate configuration file. Implies -l."),
                new CLOptionDescriptor("parse",
                CLOptionDescriptor.ARGUMENT_OPTIONAL, PARSE_OPT,
                "store files or directories given as extra args on command line."),
                new CLOptionDescriptor("remove",
                CLOptionDescriptor.ARGUMENT_REQUIRED, REMOVE_OPT,
                "remove a document."),
                new CLOptionDescriptor("collection",
                CLOptionDescriptor.ARGUMENT_REQUIRED, COLLECTION_OPT,
                "set target collection."),
                new CLOptionDescriptor(
                "resource",
                CLOptionDescriptor.ARGUMENT_REQUIRED,
                RESOURCE_OPT,
                "specify a resource contained in the current collection. "
                + "Use in conjunction with -u to specify the resource to "
                + "update."),
                new CLOptionDescriptor("get", CLOptionDescriptor.ARGUMENT_REQUIRED,
                GET_OPT, "retrieve a document."),
                new CLOptionDescriptor("mkcol",
                CLOptionDescriptor.ARGUMENT_REQUIRED, MKCOL_OPT,
                "create a collection (and any missing parent collection). Implies -c."),
                new CLOptionDescriptor("rmcol",
                CLOptionDescriptor.ARGUMENT_REQUIRED, RMCOL_OPT,
                "remove entire collection"),
                new CLOptionDescriptor(
                "xpath",
                CLOptionDescriptor.ARGUMENT_OPTIONAL,
                FIND_OPT,
                "execute XPath query given as argument. Without argument reads query from stdin."),
                new CLOptionDescriptor("howmany",
                CLOptionDescriptor.ARGUMENT_REQUIRED, RESULTS_OPT,
                "max. number of query results to be displayed."),
                new CLOptionDescriptor("output",
                CLOptionDescriptor.ARGUMENT_REQUIRED, OUTPUT_FILE_OPT,
                "write output of command into given file (use with -x, -g)."),
                new CLOptionDescriptor("option",
                CLOptionDescriptor.ARGUMENTS_REQUIRED_2
                | CLOptionDescriptor.DUPLICATES_ALLOWED,
                OPTION_OPT,
                "specify extra options: property=value. For available properties see "
                + "client.properties."),
                new CLOptionDescriptor("file",
                CLOptionDescriptor.ARGUMENT_REQUIRED, QUERY_FILE_OPT,
                "load queries from file and execute in random order."),
                new CLOptionDescriptor("threads",
                CLOptionDescriptor.ARGUMENT_REQUIRED, THREADS_OPT,
                "number of parallel threads to test with (use with -f)."),
                new CLOptionDescriptor("recurse-dirs",
                CLOptionDescriptor.ARGUMENT_DISALLOWED, RECURSE_DIRS_OPT,
                "recurse into subdirectories during index?"),
                new CLOptionDescriptor("xupdate",
                CLOptionDescriptor.ARGUMENT_REQUIRED, XUPDATE_OPT,
                "process xupdate commands. Commands are read from the "
                + "file specified in the argument."),
                new CLOptionDescriptor("no-gui",
                CLOptionDescriptor.ARGUMENT_DISALLOWED, NO_GUI_OPT,
                "don't start client with GUI. Just use the shell."),
                new CLOptionDescriptor("trace",
                CLOptionDescriptor.ARGUMENT_REQUIRED, TRACE_QUERIES_OPT,
                "log queries to the file specified by the argument (for debugging)."),
                new CLOptionDescriptor("reindex",
                CLOptionDescriptor.ARGUMENT_DISALLOWED, REINDEX_OPT,
                "reindex the collection specified in the collection argument -c"),
                new CLOptionDescriptor("query", CLOptionDescriptor.ARGUMENT_DISALLOWED, QUERY_GUI_OPT,
                "directly open the query gui"),
                new CLOptionDescriptor("no-embedded-mode", CLOptionDescriptor.ARGUMENT_DISALLOWED, NO_EMBED_OPT,
                "do not make embedded mode available")
    };
    
    boolean needPasswd = false;
    boolean passwdSpecified = false;
    boolean interactive = true;
    boolean foundCollection = false;
    boolean openQueryGui = false;
    boolean doStore = false;
    boolean doReindex = false;
    String optionRemove = null;
    XmldbURI optionGet = null;
    XmldbURI optionMkcol = null;
    XmldbURI optionRmcol = null;
    String optionXpath = null;
    String optionQueryFile = null;
    String optionXUpdate = null;
    String optionResource = null;
    String optionOutputFile = null;
    
    List optionalArgs = new ArrayList();
    
}
