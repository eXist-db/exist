/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.versioning.svn.xquery;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.exist.Namespaces;
import org.exist.memtree.SAXAdapter;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XQueryService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQueryContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
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
import org.xmldb.api.modules.XMLResource;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class XQueryFunctionsTest {

	String common = 
	"xquery version \"3.0\"; " +

	"let $collection := '/db/test/commit' " +
	"let $target-file := 'test.xml'" +
	"let $file-path := concat($collection, '/', $target-file) " +

	"let $url := "+repositoryBaseURI()+" " +
	"let $user := "+testAccount()+" " +
	"let $password := "+testPassword()+" " +

	"let $checkout-rel-path := concat($url, '/commit') " +

	"let $login := xmldb:login($collection, 'guest', 'guest') " +
	"return <result pass='true'>";


	@Test
	public void test_001() {
		test(
			"xquery version \"1.0\"; " +
			"let $destination-path := '/db/test/svn/checkout' " +
			"let $fake := if (xmldb:collection-available($destination-path)) " +
				"then xmldb:remove($destination-path )" +
				"else() " +
			"return <result pass=\"true\">subversion:checkout("+repositoryBaseURI()+", '/db/test/svn/checkout', "+testAccount()+", "+testPassword()+")</result>"
		);
	}

	@Test
	public void test_002() {
		test(
			"xquery version \"1.0\"; " +

			"let $url := "+repositoryBaseURI()+" " +
			"let $user := "+testAccount()+" " +
			"let $password := "+testPassword()+" " +
			
			"let $target-file := 'test.xml' " +
			"let $collection := '/db/test/svn/checkout' " +
			"let $file-path := concat($collection, '/', $target-file) " +
			
			"let $file := " +
				"if (doc-available($file-path)) " +
					"then () " +
					"else xmldb:store($collection, $target-file, <test>{current-dateTime()}</test>) " +
			
			"let $add := subversion:add($file-path) " +
			
			"let $commit-1 := subversion:commit($collection, 'Test of Commit after Add', $user, $password) " +
			"let $list1 := subversion:info($collection) " +
			
			"let $delete := subversion:delete($file-path) " +
			"let $commit-2 := subversion:commit($collection, 'Test of Commit after Delete', $user, $password) " +
			
			"let $list2 := subversion:info($collection) " +
			
			"return " +
			"<result pass=\"true\">" +
				"<list1>{$list1}</list1>" +
				"<list2>{$list2}</list2>" +
			"</result>"
		);
	}
	
	@Test
	public void test_003() {
		test(
			"xquery version \"1.0\"; " +
			"<result pass=\"true\">" +
			"subversion:get-latest-revision-number(" +
			"'/db/test/svn/checkout', " +
			testAccount()+", " +
			testPassword()+")" +
			"</result>"
		);
	}

	@Test
	public void test_004() {
		test(
			"xquery version \"1.0\"; " +
			"<result pass=\"true\">" +
			"subversion:clean-up(" +
			"'/db/test/svn/checkout')" +
			"</result>"
		);
	}

	@Test
	public void test_010() {
		test(
			"xquery version \"3.0\"; " +
			"let $collection := '/db/test/svn/checkout' " +
			"let $target-file := 'test.xml'" +
			"let $file-path := concat($collection, '/', $target-file) " +

			"let $url := "+repositoryBaseURI()+" " +
			"let $user := "+testAccount()+" " +
			"let $password := "+testPassword()+" " +
			
			"let $tmp := subversion:checkout("+repositoryBaseURI()+", $collection, "+testAccount()+", "+testPassword()+") " +

			"let $file := " +
			"if (doc-available($file-path)) " +
				"then () " +
				"else xmldb:store($collection, $target-file, <test>{current-dateTime()}</test>) " +

			"let $initial-owner := xmldb:get-owner($collection, $target-file) " +
			"let $initial-group := xmldb:get-group($collection, $target-file) " +
			"let $initial-permissions := xmldb:get-permissions($collection, $target-file) " +

			"let $commit := system:as-user('guest', 'guest', subversion:commit($file-path, 'Test Commit', $user, $password)) " +

			"let $final-owner := xmldb:get-owner($collection, $target-file) " +
			"let $final-group := xmldb:get-group($collection, $target-file) " +
			"let $final-permissions := xmldb:get-permissions($collection, $target-file) " +

			"return <result pass=\"true\"> " +
				"<BeforeCommit owner='{$initial-owner}' group='{$initial-group}' perms='{xmldb:permissions-to-string($initial-permissions)}'/>" +
				"<AfterCommit owner='{$final-owner}' group='{$final-group}' perms='{xmldb:permissions-to-string($final-permissions)}'/>" +
				"<revision>{$commit}</revision>" +
			"</result>"   
		);
	}

	@Test
	public void test_011() {
		test(
			common +
			"{if (xmldb:collection-available($collection)) " +
				"then ('Removing Old Files', xmldb:remove($collection)) " +
				"else()}" +
			"</result>");

		test(
			common +
	    	"{subversion:checkout($checkout-rel-path, $collection, $test-user, $test-password)}" +
			"</result>"
		);
		
		test(
			common +
		    "{let $result := xmldb:get-child-collections($collection) " +
		    "return " +
		    	"if ( $result = '.svn') " +
		    	"then " +
		    		"<test>" +
		            	"result = {$result} (expecting '.svn') "+
		            	"passed" +
		          	"</test> " +
	          	"else <test>fail</test>" +
		    "}" +
			"</result>"
		);

		test(
			common +
		    "{"+
		        "let $timestamp := current-dateTime() " +
		        
		        "let $new-message := concat('Updated Message ', $timestamp) " +
		        "let $update := update value doc($new-file-path) " +
		        "return " +
		           "doc($new-file-path) "+
		    "}"+
			"</result>"
	    );
					    
		test(
			common +
		    "{"+
		        "subversion:commit($collection, 'Test Modify Commit From Unit Test', $user, $password)" +
		    "}"+
		    "</result>"
	    );
	}

	private String repositoryBaseURI() {
		return "'http://localhost:9080/svn/testRepo'";
	}

	private String testAccount() {
		String str = System.getProperty("svn_username");
		if (str == null)
			return "'svnTest'";
		return str;
	}

	private String testPassword() {
		String str = System.getProperty("svn_password");
		if (str == null)
			return "'testing'";
		return str;
	}

	public void test(String script) {
        try {
            StringBuilder fails = new StringBuilder();
            StringBuilder results = new StringBuilder();
            XQueryService xqs = (XQueryService) rootCollection.getService("XQueryService", "1.0");
            Source query = new StringSource(script);

            ResourceSet result = xqs.execute(query);
            XMLResource resource = (XMLResource) result.getResource(0);
            results.append(resource.getContent()).append('\n');
            Element root = (Element) resource.getContentAsDOM();
            NodeList tests = root.getElementsByTagName("result");
            for (int i = 0; i < tests.getLength(); i++) {
                Element test = (Element) tests.item(i);
                String passed = test.getAttribute("pass");
                if (passed.equals("false")) {
                    fails.append("Test '" + test.getAttribute("n") + "' failed.\n");
                }
            }
            if (fails.length() > 0) {
                System.err.print(results);
                fail(fails.toString());
            }
            System.out.println(results);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    private Collection rootCollection;

	@Before
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void setUpBefore() throws Exception {
        // initialize driver
        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        Database database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        database.setProperty("configuration", "../../conf.xml");
        DatabaseManager.registerDatabase(database);

        rootCollection =
                DatabaseManager.getCollection("xmldb:exist://" + XmldbURI.ROOT_COLLECTION, "admin", "");
        
        Configuration config = BrokerPool.getInstance().getConfiguration();
        Map map = (Map)config.getProperty(XQueryContext.PROPERTY_BUILT_IN_MODULES);
        map.put(
    		"http://exist-db.org/xquery/versioning/svn", 
    		org.exist.versioning.svn.xquery.SVNModule.class);
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
