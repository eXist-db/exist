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

import org.exist.Namespaces;
import org.exist.memtree.SAXAdapter;
import org.exist.source.FileSource;
import org.exist.source.Source;
import org.exist.util.XMLFilenameFilter;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XQueryService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.value.Sequence;
import org.junit.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import static org.junit.Assert.fail;

public abstract class TestRunner {

    private Collection rootCollection;

    protected abstract String getDirectory();

    @Test
    public void runXMLBasedTests() {
    	XMLFilenameFilter filter = new XMLFilenameFilter();
    	
        File dir = new File(getDirectory());
        File[] files;
        if (dir.isDirectory())
        	files = dir.listFiles(filter);
        else if (filter.accept(dir.getParentFile(), dir.getName()))
        	files = new File[] {dir};
        else
        	return;

        try {
            StringBuilder fails = new StringBuilder();
            StringBuilder results = new StringBuilder();
            XQueryService xqs = (XQueryService) rootCollection.getService("XQueryService", "1.0");
            Source query = new FileSource(new File("test/src/xquery/runTests.xql"), "UTF-8", false);
            for (File file : files) {
                Document doc = parse(file);

                xqs.declareVariable("doc", doc);
				xqs.declareVariable("id", Sequence.EMPTY_SEQUENCE);
                ResourceSet result = xqs.execute(query);
                XMLResource resource = (XMLResource) result.getResource(0);
                results.append(resource.getContent()).append('\n');
                Element root = (Element) resource.getContentAsDOM();
                NodeList tests = root.getElementsByTagName("test");
                for (int i = 0; i < tests.getLength(); i++) {
                    Element test = (Element) tests.item(i);
                    String passed = test.getAttribute("pass");
                    if (passed == null || passed.equals("false")) {
                        fails.append("Test '" + test.getAttribute("n") + "' in file '" + file.getName() + "' failed.\n");
                    }
                }
            }
            if (fails.length() > 0) {
                System.err.print(results);
                fail(fails.toString());
            }
            System.out.println(results);
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            fail(e.getMessage());
        } catch (SAXException e) {
            e.printStackTrace();
            fail(e.getMessage());
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void runXQueryBasedTests() {
        File dir = new File(getDirectory());
        File[] suites = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return (file.canRead() && file.getName().startsWith("suite") && file.getName().endsWith(".xql"));
            }
        });
        for (File suite: suites) {
            try {
                StringBuilder fails = new StringBuilder();
                StringBuilder results = new StringBuilder();
                XQueryService xqs = (XQueryService) rootCollection.getService("XQueryService", "1.0");
                xqs.setModuleLoadPath(getDirectory());
                Source query = new FileSource(suite, "UTF-8", false);

                ResourceSet result = xqs.execute(query);
                XMLResource resource = (XMLResource) result.getResource(0);
                results.append(resource.getContent()).append('\n');

                Element root = (Element) resource.getContentAsDOM();
                NodeList testsuites = root.getElementsByTagName("testsuite");
                for (int i = 0; i < testsuites.getLength(); i++) {
                    Element testsuite = (Element) testsuites.item(i);
                    NodeList tests = testsuite.getElementsByTagName("testcase");
                    for (int j = 0; j < tests.getLength(); j++) {
                        Element test = (Element) tests.item(j);
                        NodeList failures = test.getElementsByTagName("failure");
                        if (failures.getLength() > 0) {
                            fails.append("Test '" + test.getAttribute("name") + "' in module '" +
                                    testsuite.getAttribute("package") + "' failed.\n");
                        }

                        NodeList errors = test.getElementsByTagName("error");
                        if (errors.getLength() > 0) {
                            fails.append("Test '" + test.getAttribute("name") + "' in module '" +
                                    testsuite.getAttribute("package") + "' failed with an error.\n");
                        }
                    }
                }
                if (fails.length() > 0) {
                    System.err.print(results);
                    fail(fails.toString());
                }
                System.out.println(results);
            } catch (XMLDBException e) {
                e.printStackTrace();
                fail(e.getMessage());
            }
        }
    }

    @Before
    public void setUpBefore() throws Exception {
        // initialize driver
        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        Database database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);

        rootCollection =
                DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
    }

    @After
    public void tearDownAfter() {
        if (rootCollection != null) {
            try {
                DatabaseInstanceManager dim =
                        (DatabaseInstanceManager) rootCollection.getService(
                        "DatabaseInstanceManager", "1.0");
                dim.shutdown();
            } catch (Exception e) {
                e.printStackTrace();
                fail(e.getMessage());
            }
        }
        rootCollection = null;
    }

    protected static Document parse(File file) throws IOException, SAXException, ParserConfigurationException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        InputSource src = new InputSource(file.toURI().toASCIIString());
        SAXParser parser = factory.newSAXParser();
        XMLReader xr = parser.getXMLReader();

        SAXAdapter adapter = new SAXAdapter();
        xr.setContentHandler(adapter);
        xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
        xr.parse(src);

        return adapter.getDocument();
    }
}
