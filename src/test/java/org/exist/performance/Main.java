/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 * $Id$
 */

package org.exist.performance;

import org.exist.source.ClassLoaderSource;
import org.exist.util.FileUtils;
import org.exist.util.SystemExitCodes;
import org.exist.util.XMLFilenameFilter;
import org.exist.xmldb.EXistXQueryService;
import org.w3c.dom.Document;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import se.softhouse.jargo.Argument;
import se.softhouse.jargo.ArgumentException;
import se.softhouse.jargo.CommandLineParser;
import se.softhouse.jargo.ParsedArguments;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.util.ArgumentUtil.getBool;
import static se.softhouse.jargo.Arguments.*;

public class Main {

    /* general arguments */
    private static final Argument<?> helpArg = helpArgument("-h", "--help");

    /* control arguments */
    private static final Argument<Boolean> reportArg = optionArgument("-r", "--report")
            .description("create an HTML report from all output files in the directory.")
            .defaultValue(false)
            .build();
    private static final Argument<File> inputFileArg = fileArgument("-f", "file")
            .description("XML input file.")
            .required()
            .build();
    private static final Argument<File> outputDirArg = fileArgument("-d", "dir")
            .description("directory for writing test results.")
            .required()
            .build();
    private static final Argument<List<String>> groupArg = stringArgument()
            .description("group of performance tests to run")
            .variableArity()
            .build();

    private final static Path CSS_FILE = Paths.get("test/src/org/exist/performance/style.css");
    
    public static void main(final String[] args) {
        try {
            final ParsedArguments arguments = CommandLineParser
                    .withArguments(reportArg, inputFileArg, outputDirArg)
                    .andArguments(groupArg)
                    .andArguments(helpArg)
                    .parse(args);

            process(arguments);
        } catch (final ArgumentException e) {
            System.out.println(e.getMessageAndUsage());
            System.exit(SystemExitCodes.INVALID_ARGUMENT_EXIT_CODE);

        }
    }

    private static void process(final ParsedArguments arguments) {
        final boolean createReport = getBool(arguments, reportArg);
        final Path xmlFile = arguments.get(inputFileArg).toPath();
        final Path outputDir = arguments.get(outputDirArg).toPath();
        final List<String> groups = arguments.get(groupArg);

        if (xmlFile == null || !Files.isReadable(xmlFile)) {
            System.err.println("Cannot read test definition file: " + xmlFile.toAbsolutePath());
            System.exit(SystemExitCodes.INVALID_ARGUMENT_EXIT_CODE);
        }
        if (outputDir == null || !Files.isWritable(outputDir)) {
            System.err.println("No or not writable output directory specified. Please provide a " +
                "writable directory with option -d");
            System.exit(SystemExitCodes.INVALID_ARGUMENT_EXIT_CODE);
        }

        for (final String group: groups) {
            Runner runner = null;
            try {
                final Path outFile = outputDir.resolve(group + ".xml");
                runner = configure(xmlFile, outFile);
                runner.run(group);
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("ERROR: " + e.getMessage());
            } finally {
                if (runner != null)
                    runner.shutdown();
            }
        }
        if (createReport) {
            Runner runner = null;
            try {
                runner = configure(xmlFile, null);
                createReport(runner, outputDir);
            } finally {
                if (runner != null)
                    runner.shutdown();
            }
        }
    }

    private static Runner configure(Path xmlFile, Path outFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile.toFile());
            TestResultWriter writer = null;
            if (outFile != null) {
                writer = new TestResultWriter(outFile);
            }
            try {
                return new Runner(doc.getDocumentElement(), writer);
            } finally {
                if(writer != null) {
                    writer.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("ERROR: " + e.getMessage());
        }
        return null;
    }

    private static void createReport(Runner runner, Path directory) {
        try {
            Connection con = runner.getConnection();
            Collection collection = con.getCollection("benchmark");
            if (collection == null) {
                Collection root = con.getCollection();
                CollectionManagementService cmgt =
                    (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
                collection = cmgt.createCollection("benchmark");
            }

            for(final Path file : FileUtils.list(directory, XMLFilenameFilter.asPredicate())) {
                Resource resource = collection.createResource(FileUtils.fileName(file), XMLResource.RESOURCE_TYPE);
                resource.setContent(file);
                collection.storeResource(resource);
            }
            EXistXQueryService service = (EXistXQueryService) collection.getService("XQueryService", "1.0");
            ResourceSet result = service.execute(new ClassLoaderSource("/org/exist/performance/log2html.xql"));

            if (directory == null) {
                directory = Paths.get(System.getProperty("user.dir"));
            }

            final Path htmlFile = directory.resolve("results.html");
            try(final BufferedWriter writer = Files.newBufferedWriter(htmlFile, UTF_8)) {
                writer.write(result.getResource(0).getContent().toString());
            }
            Files.copy(CSS_FILE, directory.resolve(FileUtils.fileName(CSS_FILE)));

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("ERROR: " + e.getMessage());
        }
    }
}
