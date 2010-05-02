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

import org.apache.avalon.excalibur.cli.CLArgsParser;
import org.apache.avalon.excalibur.cli.CLOption;
import org.apache.avalon.excalibur.cli.CLOptionDescriptor;
import org.apache.avalon.excalibur.cli.CLUtil;
import org.apache.commons.io.FileUtils;
import org.exist.source.ClassLoaderSource;
import org.exist.xmldb.XQueryService;
import org.w3c.dom.Document;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.CollectionManagementService;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Main {

    // command-line options
    private final static int HELP_OPT = 'h';
    private final static int XML_FILE_OPT = 'f';
    private final static int OUTPUT_DIR_OPT = 'd';
    private final static int REPORT_OPT = 'r';

    private final static CLOptionDescriptor OPTIONS[] = new CLOptionDescriptor[] {
        new CLOptionDescriptor("help", CLOptionDescriptor.ARGUMENT_DISALLOWED, HELP_OPT,
                "print help on command line options and exit."),
        new CLOptionDescriptor("dir", CLOptionDescriptor.ARGUMENT_REQUIRED, OUTPUT_DIR_OPT,
                "directory for writing test results."),
        new CLOptionDescriptor("file", CLOptionDescriptor.ARGUMENT_REQUIRED, XML_FILE_OPT,
                "XML input file."),
        new CLOptionDescriptor("report", CLOptionDescriptor.ARGUMENT_DISALLOWED, REPORT_OPT,
                "create an HTML report from all output files in the directory.")
    };

    private final static File CSS_FILE = new File("test/src/org/exist/performance/style.css");
    
    public static void main(String[] args) {
        CLArgsParser optParser = new CLArgsParser(args, OPTIONS);
        if (optParser.getErrorString() != null) {
            System.err.println("ERROR: " + optParser.getErrorString());
            return;
        }
        for (String arg : args) {
            System.out.println("ARG: " + arg);
        }
        List<?> opt = optParser.getArguments();
        int size = opt.size();
        CLOption option;
        File outputDir = null;
        boolean createReport = false;
        List<String> groups = new ArrayList<String>();
        File xmlFile = null;
        for (int i = 0; i < size; i++) {
            option = (CLOption) opt.get(i);
            switch (option.getId()) {
            case HELP_OPT:
                System.out.println("Usage: java " + Main.class.getName() + " [options] [group ...]");
                System.out.println(CLUtil.describeOptions(OPTIONS).toString());
                System.exit(0);
                break;
            case OUTPUT_DIR_OPT:
                outputDir = new File(option.getArgument().trim());
                break;
            case REPORT_OPT:
                createReport = true;
                break;
            case XML_FILE_OPT:
                xmlFile = new File(option.getArgument().trim());
                break;
            case CLOption.TEXT_ARGUMENT:
                groups.add(option.getArgument());
                break;
            }
        }

        if (xmlFile == null || !xmlFile.canRead()) {
            System.err.println("Cannot read test definition file: " + xmlFile.getAbsolutePath());
            System.exit(1);
        }
        if (outputDir == null || !outputDir.canWrite()) {
            System.err.println("No or not writable output directory specified. Please provide a " +
                "writable directory with option -d");
            System.exit(1);
        }

        for (String group: groups) {
            Runner runner = null;
            try {
                File outFile = new File(outputDir, group + ".xml");
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

    private static Runner configure(File xmlFile, File outFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile);
            TestResultWriter writer = null;
            if (outFile != null)
                writer = new TestResultWriter(outFile.getAbsolutePath());
            return new Runner(doc.getDocumentElement(), writer);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("ERROR: " + e.getMessage());
        }
        return null;
    }

    private static void createReport(Runner runner, File directory) {
        try {
            Connection con = runner.getConnection();
            Collection collection = con.getCollection("benchmark");
            if (collection == null) {
                Collection root = con.getCollection();
                CollectionManagementService cmgt =
                    (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
                collection = cmgt.createCollection("benchmark");
            }
            for (Iterator<?> i = FileUtils.iterateFiles(directory, new String[] { "xml" }, false); i.hasNext(); ) {
                File file = (File) i.next();
                Resource resource = collection.createResource(file.getName(), "XMLResource");
                resource.setContent(file);
                collection.storeResource(resource);
            }
            XQueryService service = (XQueryService) collection.getService("XQueryService", "1.0");
            ResourceSet result = service.execute(new ClassLoaderSource("/org/exist/performance/log2html.xql"));

            if (directory == null)
                directory = new File(System.getProperty("user.dir"));
            File htmlFile = new File(directory, "results.html");
            FileUtils.writeStringToFile(htmlFile, result.getResource(0).getContent().toString(), "UTF-8");
            FileUtils.copyFile(CSS_FILE, new File(directory, CSS_FILE.getName()));
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("ERROR: " + e.getMessage());
        }
    }
}
