/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2012 The eXist Project
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

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.*;

import org.exist.xmldb.XmldbURI;
import org.exist.xquery.util.URIUtils;
import se.softhouse.jargo.*;

import static org.exist.util.ArgumentUtil.*;
import static se.softhouse.jargo.Arguments.*;

/**
 * Command Line Options for the {@link InteractiveClient}
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 * @author wessels
 */
public class CommandlineOptions {

    /* general arguments */
    private static final Argument<?> helpArg = helpArgument("-h", "--help");
    private static final Argument<Boolean> quietArg = optionArgument("-q", "--quiet")
            .description("be quiet. Just print errors.")
            .defaultValue(false)
            .build();
    private static final Argument<Boolean> verboseArg = optionArgument("-v", "--verbose")
            .description("be verbose. Display progress information on put.")
            .defaultValue(false)
            .build();
    private static final Argument<File> outputFileArg = fileArgument("-O", "--output")
            .description("write output of command into given file (use with -x, -g).")
            .build();
    private static final Argument<Map<String, String>> optionArg = stringArgument("-o", "--option")
            .description("specify extra options: property=value. For available properties see client.properties.")
            .asKeyValuesWithKeyParser(StringParsers.stringParser())
            .build();


    /* database connection arguments */
    private static final Argument<String> userArg = stringArgument("-u", "--user")
            .description("set username.")
            .defaultValue(null)
            .build();
    private static final Argument<String> passwordArg = stringArgument("-P", "--password")
            .description("specify password.")
            .build();
    private static final Argument<Boolean> useSslArg = optionArgument("-S", "--use-ssl")
            .description("Use SSL by default for remote connections")
            .defaultValue(false)
            .build();
    private static final Argument<Boolean> embeddedArg = optionArgument("-l", "--local")
            .description("launch a local database instance. Otherwise client will connect to URI specified in client.properties.")
            .defaultValue(false)
            .build();
    private static final Argument<File> embeddedConfigArg = fileArgument("-C", "--config")
            .description("specify alternate configuration file. Implies -l.")
            .build();
    private static final Argument<Boolean> noEmbeddedModeArg = optionArgument("-N", "--no-embedded-mode")
            .description("do not make embedded mode available")
            .defaultValue(false)
            .build();


    /* gui arguments */
    private static final Argument<Boolean> noGuiArg = optionArgument("-s", "--no-gui")
            .description("don't start client with GUI. Just use the shell.")
            .defaultValue(false)
            .build();
    private static final Argument<Boolean> guiQueryDialogArg = optionArgument("-Q", "--query")
            .description("directly open the query gui")
            .defaultValue(false)
            .build();


    /* mk/rm/set collection arguments */
    private static final Argument<String> mkColArg = stringArgument("-m", "--mkcol")
            .description("create a collection (and any missing parent collection). Implies -c.")
            .build();
    private static final Argument<String> rmColArg = stringArgument("-R", "--rmcol")
            .description("remove entire collection")
            .build();
    private static final Argument<String> setColArg = stringArgument("-c", "--collection")
            .description("set target collection.")
            .build();


    /* put/get/rm document arguments */
    private static final Argument<List<File>> parseDocsArg = fileArgument("-p", "--parse")
            .description("store files or directories given as extra args on command line.")
            .variableArity()
            .build();
    private static final Argument<String> getDocArg = stringArgument("-g", "--get")
            .description("retrieve a document.")
            .build();
    private static final Argument<String> rmDocArg = stringArgument("-r", "--remove")
            .description("remove a document.")
            .build();


    /* query arguments */
    public static final String XPATH_STDIN = "<<STDIN";
    private static final Argument<String> xpathArg = stringArgument("-x", "--xpath")
            .description("execute XPath query given as argument. Without argument reads query from stdin.")
            .build();
    private static final Argument<List<File>> loadQueriesArg = fileArgument("-F", "--file")
            .description("load queries from file and execute in random order.")
            .variableArity()
            .build();
    private static final Argument<Integer> howManyResultsArg = integerArgument("-n", "--howmany")
            .description("max. number of query results to be displayed.")
            .build();
    private static final Argument<File> traceQueriesArg = fileArgument("-T", "--trace")
            .description("log queries to the file specified by the argument (for debugging).")
            .build();


    /* xupdate arguments */
    private static final Argument<String> setDocArg = stringArgument("-f", "--resource")
            .description("specify a resource contained in the current collection. Use in conjunction with --xupdate to specify the resource to update.")
            .build();

    private static final Argument<File> xupdateArg = fileArgument("-X", "--xupdate")
            .description("process XUpdate commands. Commands are read from the file specified in the argument.")
            .build();

    /* reindex arguments */
    private static final Argument<Boolean> reindexArg = optionArgument("-i", "--reindex")
            .description("reindex the collection specified in the collection argument --collection")
            .defaultValue(false)
            .build();
    private static final Argument<Boolean> reindexRecurseDirsArg = optionArgument("-d", "--recurse-dirs")
            .description("recurse into subdirectories during index?")
            .defaultValue(false)
            .build();

    private static Optional<XmldbURI> optUri(final ParsedArguments parsedArguments, final Argument<String> argument) throws URISyntaxException {
        final Optional<String> uriStr = getOpt(parsedArguments, argument);
        if (uriStr.isPresent()) {
            return Optional.of(URIUtils.encodeXmldbUriFor(uriStr.get()));
        } else {
            return Optional.empty();
        }
    }

    public static CommandlineOptions parse(final String[] args) throws ArgumentException, URISyntaxException {
        final ParsedArguments arguments = CommandLineParser
                .withArguments(userArg, passwordArg, useSslArg, embeddedArg, embeddedConfigArg, noEmbeddedModeArg)
                .andArguments(noGuiArg, guiQueryDialogArg)
                .andArguments(mkColArg, rmColArg, setColArg)
                .andArguments(parseDocsArg, getDocArg, rmDocArg)
                .andArguments(xpathArg, loadQueriesArg, howManyResultsArg, traceQueriesArg)
                .andArguments(setDocArg, xupdateArg)
                .andArguments(reindexArg, reindexRecurseDirsArg)
                .andArguments(helpArg, quietArg, verboseArg, outputFileArg, optionArg)
                .parse(args);

        final boolean quiet = getBool(arguments, quietArg);
        final boolean verbose = getBool(arguments, verboseArg);
        final Optional<Path> outputFile = getPathOpt(arguments, outputFileArg);
        final Map<String, String> options = arguments.get(optionArg);

        final Optional<String> username = getOpt(arguments, userArg);
        final Optional<String> password = getOpt(arguments, passwordArg);
        final boolean useSSL = getBool(arguments, useSslArg);
        final boolean embedded = getBool(arguments, embeddedArg);
        final Optional<Path> embeddedConfig = getPathOpt(arguments, embeddedConfigArg);
        final boolean noEmbeddedMode = getBool(arguments, noEmbeddedModeArg);

        final boolean startGUI = !getBool(arguments, noGuiArg);
        final boolean openQueryGUI = getBool(arguments, guiQueryDialogArg);

        final Optional<XmldbURI> mkCol = optUri(arguments, mkColArg);
        final Optional<XmldbURI> rmCol = optUri(arguments, rmColArg);
        final Optional<XmldbURI> setCol = optUri(arguments, setColArg);

        final List<Path> parseDocs = getPathsOpt(arguments, parseDocsArg);
        final Optional<XmldbURI> getDoc = optUri(arguments, getDocArg);
        final Optional<String> rmDoc = getOpt(arguments, rmDocArg);

        final Optional<String> maybeXpath = getOpt(arguments, xpathArg);
        final Optional<String> xpath;
        if(maybeXpath.isPresent()) {
            if(maybeXpath.get().isEmpty()) {
                xpath = Optional.of(XPATH_STDIN);
            } else {
                xpath = maybeXpath;
            }
        } else {
            xpath = Optional.empty();
        }
        final List<Path> queryFiles = getPathsOpt(arguments, loadQueriesArg);
        final Optional<Integer> howManyResults = getOpt(arguments, howManyResultsArg);
        final Optional<Path> traceQueriesFile = getPathOpt(arguments, traceQueriesArg);

        final Optional<String> setDoc = getOpt(arguments, setDocArg);
        final Optional<Path> xupdateFile = getPathOpt(arguments, xupdateArg);

        final boolean reindex = getBool(arguments, reindexArg);
        final boolean reindexRecurse = getBool(arguments, reindexRecurseDirsArg);

        return new CommandlineOptions(
                quiet,
                verbose,
                outputFile,
                options,
                username,
                password,
                useSSL,
                embedded,
                embeddedConfig,
                noEmbeddedMode,
                startGUI,
                openQueryGUI,
                mkCol,
                rmCol,
                setCol,
                parseDocs,
                getDoc,
                rmDoc,
                xpath,
                queryFiles,
                howManyResults,
                traceQueriesFile,
                setDoc,
                xupdateFile,
                reindex,
                reindexRecurse
        );
    }

    public CommandlineOptions(boolean quiet, boolean verbose, Optional<Path> outputFile, Map<String, String> options, Optional<String> username, Optional<String> password, boolean useSSL, boolean embedded, Optional<Path> embeddedConfig, boolean noEmbeddedMode, boolean startGUI, boolean openQueryGUI, Optional<XmldbURI> mkCol, Optional<XmldbURI> rmCol, Optional<XmldbURI> setCol, List<Path> parseDocs, Optional<XmldbURI> getDoc, Optional<String> rmDoc, Optional<String> xpath, List<Path> queryFiles, Optional<Integer> howManyResults, Optional<Path> traceQueriesFile, Optional<String> setDoc, Optional<Path> xupdateFile, boolean reindex, boolean reindexRecurse) {
        this.quiet = quiet;
        this.verbose = verbose;
        this.outputFile = outputFile;
        this.options = options;
        this.username = username;
        this.password = password;
        this.useSSL = useSSL;
        this.embedded = embedded;
        this.embeddedConfig = embeddedConfig;
        this.noEmbeddedMode = noEmbeddedMode;
        this.startGUI = startGUI;
        this.openQueryGUI = openQueryGUI;
        this.mkCol = mkCol;
        this.rmCol = rmCol;
        this.setCol = setCol;
        this.parseDocs = parseDocs;
        this.getDoc = getDoc;
        this.rmDoc = rmDoc;
        this.xpath = xpath;
        this.queryFiles = queryFiles;
        this.howManyResults = howManyResults;
        this.traceQueriesFile = traceQueriesFile;
        this.setDoc = setDoc;
        this.xupdateFile = xupdateFile;
        this.reindex = reindex;
        this.reindexRecurse = reindexRecurse;
    }

    final boolean quiet;
    final boolean verbose;
    final Optional<Path> outputFile;
    final Map<String, String> options;

    final Optional<String> username;
    final Optional<String> password;
    final boolean useSSL;
    final boolean embedded;
    final Optional<Path> embeddedConfig;
    final boolean noEmbeddedMode;

    final boolean startGUI;
    final boolean openQueryGUI;

    final Optional<XmldbURI> mkCol;
    final Optional<XmldbURI> rmCol;
    final Optional<XmldbURI> setCol;

    final List<Path> parseDocs;
    final Optional<XmldbURI> getDoc;
    final Optional<String> rmDoc;

    final Optional<String> xpath;
    final List<Path> queryFiles;
    final Optional<Integer> howManyResults;
    final Optional<Path> traceQueriesFile;

    final Optional<String> setDoc;
    final Optional<Path> xupdateFile;

    final boolean reindex;
    final boolean reindexRecurse;
}
