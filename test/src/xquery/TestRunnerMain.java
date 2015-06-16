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

import static org.junit.Assert.fail;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.avalon.excalibur.cli.CLArgsParser;
import org.apache.avalon.excalibur.cli.CLOption;
import org.apache.avalon.excalibur.cli.CLOptionDescriptor;
import org.apache.avalon.excalibur.cli.CLUtil;
import org.exist.source.FileSource;
import org.exist.source.Source;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XQueryService;
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

public class TestRunnerMain {

	private final static int HELP_OPT = 'h';
	private final static int SINGLE_OPT = 's';
	
	private final static CLOptionDescriptor[] OPTIONS = new CLOptionDescriptor[] {
		new CLOptionDescriptor( "help", CLOptionDescriptor.ARGUMENT_DISALLOWED, HELP_OPT, "print help on command line options and exit." ),
		new CLOptionDescriptor( "single", CLOptionDescriptor.ARGUMENT_REQUIRED, SINGLE_OPT, "run a single test identified by its id attribute." )
	};
	
	private static Collection rootCollection;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		init();
	
		CLArgsParser optParser = new CLArgsParser( args, OPTIONS );

        if( optParser.getErrorString() != null ) {
            System.err.println( "ERROR: " + optParser.getErrorString() );
            return;
        }
        List<String> files = new ArrayList<String>();
        String id = null;
        
        List<CLOption> opts = optParser.getArguments();

        for( CLOption option : opts ) {

            switch( option.getId() ) {
	            case HELP_OPT:
	                System.out.println( "Usage: java " + TestRunnerMain.class.getName() + " [options]" );
	                System.out.println( CLUtil.describeOptions( OPTIONS ).toString() );
	                System.exit( 0 );
	                break;
	            case SINGLE_OPT: 
	            	id = option.getArgument();
	            	break;
	            case CLOption.TEXT_ARGUMENT :
                    files.add(option.getArgument());
                    break;
            }
        }
        
		runTests(files, id);
		
		shutdown();
	}

	private static void runTests(List<String> files, String id) {
		try {
			StringBuilder results = new StringBuilder();
			XQueryService xqs = (XQueryService) rootCollection.getService("XQueryService", "1.0");
			Source query = new FileSource(new File("test/src/xquery/runTests.xql"), "UTF-8", false);
			for (String fileName : files) {
				File file = new File(fileName);
				if (!file.canRead()) {
					System.console().printf("Test file not found: %s\n", fileName);
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
