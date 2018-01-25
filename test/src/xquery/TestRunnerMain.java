/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2011 The eXist Project
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
package xquery;

import static org.exist.util.ArgumentUtil.getOpt;
import static org.junit.Assert.fail;
import static se.softhouse.jargo.Arguments.fileArgument;
import static se.softhouse.jargo.Arguments.helpArgument;
import static se.softhouse.jargo.Arguments.stringArgument;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.exist.source.FileSource;
import org.exist.source.Source;
import org.exist.util.SystemExitCodes;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.EXistXQueryService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.value.Sequence;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.XMLResource;
import se.softhouse.jargo.Argument;
import se.softhouse.jargo.ArgumentException;
import se.softhouse.jargo.CommandLineParser;
import se.softhouse.jargo.ParsedArguments;

public class TestRunnerMain {

	/* general arguments */
	private static final Argument<?> helpArg = helpArgument("-h", "--help");

    /* control arguments */
	private static final Argument<String> singleTestArg = stringArgument("-s", "--single")
			.description("run a single test identified by its id attribute.")
			.build();
	private static final Argument<List<File>> fileArg = fileArgument()
			.description("files containing tests to run")
			.variableArity()
			.build();

	private static Collection rootCollection;

	/**
	 * @param args
	 */
	public static void main(final String[] args) {
		try {
			final ParsedArguments arguments = CommandLineParser
					.withArguments(singleTestArg)
					.andArguments(fileArg)
					.andArguments(helpArg)
					.parse(args);

			process(arguments);
		} catch (final ArgumentException e) {
			System.out.println(e.getMessageAndUsage());
			System.exit(SystemExitCodes.INVALID_ARGUMENT_EXIT_CODE);

		}
	}

	private static void process(final ParsedArguments arguments) {
		final String id = getOpt(arguments, singleTestArg).orElse(null);
		final List<Path> files = arguments.get(fileArg).stream().map(File::toPath).collect(Collectors.toList());

		init();
		try {
			runTests(files, id);
		} finally {
			shutdown();
		}
	}

	private static void runTests(final List<Path> files, final String id) {
		try {
			StringBuilder results = new StringBuilder();
			EXistXQueryService xqs = (EXistXQueryService) rootCollection.getService("XQueryService", "1.0");
			Source query = new FileSource(Paths.get("test/src/xquery/runTests.xql"), false);
			for (final Path file : files) {
				if (!Files.isReadable(file)) {
					System.console().printf("Test file not found: %s\n", file.normalize().toAbsolutePath().toString());
					return;
				}
				
				Document doc = TestRunner.parse(file);
				
				xqs.declareVariable("doc", doc);
				
				if (id != null) {
					xqs.declareVariable("id", id);
				} else {
					xqs.declareVariable("id", Sequence.EMPTY_SEQUENCE);
				}

				ResourceSet result = xqs.execute(query);
				XMLResource resource = (XMLResource) result.getResource(0);
                results.append(resource.getContent()).append('\n');
				Element root = (Element) resource.getContentAsDOM();
				NodeList tests = root.getElementsByTagName("test");
				for (int i = 0; i < tests.getLength(); i++) {
					Element test = (Element) tests.item(i);
					String passed = test.getAttribute("pass");
					if (passed.equals("false")) {
						System.err.println(resource.getContent());
						return;
					}
				}
			}
			System.out.println(results);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static void init() {
		// initialize driver
		try {
			Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
			Database database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
			
			rootCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static void shutdown() {
		if (rootCollection != null) {
			try {
				DatabaseInstanceManager dim =
				    (DatabaseInstanceManager) rootCollection.getService("DatabaseInstanceManager", "1.0");
				dim.shutdown();
			} catch (Exception e) {
				e.printStackTrace();
				fail(e.getMessage());
			}
		}
	}
}
