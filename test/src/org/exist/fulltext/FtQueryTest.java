/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.fulltext;

import java.io.File;

import junit.textui.TestRunner;

import org.custommonkey.xmlunit.XMLTestCase;
import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.IndexQueryService;
import org.exist.xmldb.XQueryService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.FunctionFactory;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

public class FtQueryTest extends XMLTestCase {

    private static String COLLECTION_CONFIG1 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index>" +
    	"		<fulltext default=\"all\">" +
        "		</fulltext>" +
        "	</index>" +
    	"</collection>";

	private final static String TEST_XML =
		"<test-doc>" +
			"<test-elem id=\"1\" attribute1=\"test some text\"/>" +
			"<test-elem id=\"2\" attribute2=\"test some text\"/>" +
			"<test-elem id=\"3\" attribute1=\"test some text\"/>" +
			"<test-elem id=\"4\" attribute3=\"test some text\"/>" +
		"</test-doc>";

    private final static String QNAME_XML =
            "<root>" +
            "   <test>" +
            "       <node id=\"1\">First node</node>" +
            "       <node id=\"2\">Second node</node>" +
            "   </test>" +
            "</root>";
    
    private final static String NESTED_XML =
        "<root>\n" +
        "    <nested><s>un</s>even</nested>\n" +
        "    <nested>un<s>suitable</s></nested>\n" +
        "    <nested><s>in</s><s>ap</s><s>pro</s><s>pri</s><s>ate</s></nested>\n" +
        "</root>";

    private final static String MATCH_COUNT =
        "<doc> term term <level1>term term</level1><level1>term<level2>term</level2></level1></doc>";
    
    private final static String FILES[] = { "hamlet.xml", "macbeth.xml", "r_and_j.xml" };
        static File existDir;
    static {
      String existHome = System.getProperty("exist.home");
      existDir = existHome==null ? new File(".") : new File(existHome);
    }
    private final static File SHAKES_DIR = new File(existDir,"samples" + File.separator + "shakespeare");
    private final static File MODS_DIR = new File(existDir,"samples" + File.separator + "mods");

	private static final String TEST_COLLECTION_NAME = "testft";
    private static final String TEST_COLLECTION_PATH = XmldbURI.ROOT_COLLECTION + "/" + TEST_COLLECTION_NAME;
    
    private Database database;
    private Collection testCollection;
    
    public void testFtOperators() {
    	try {
	    	System.out.println("----- testFtOperators -----");
	        XQueryService service = (XQueryService)
	            testCollection.getService("XQueryService", "1.0");
	        ResourceSet result = service.query("//SPEECH[LINE &= 'love']");
	        assertEquals(160, result.getSize());
	        result = service.query("//SPEECH[LINE &= 'thou']");
	        assertEquals(290, result.getSize());
	        result = service.query("//SPEECH[LINE &= 'thou']");
	        assertEquals(290, result.getSize());
	        result = service.query("//SPEECH[LINE &= 'fenny snake']/LINE[1]");
	        assertEquals(1, result.getSize());
	        assertXMLEqual(result.getResource(0).getContent().toString(), "<LINE>Fillet of a fenny snake,</LINE>");
            //assertXMLEqual(result.getResource(0).getContent().toString(), "<LINE>Fillet of a <exist:match xmlns:exist='http://exist.sourceforge.net/NS/exist'>fenny</exist:match> <exist:match xmlns:exist='http://exist.sourceforge.net/NS/exist'>snake</exist:match>,</LINE>");
	        result = service.query("//SPEECH[LINE &= 'god*']");
	        assertEquals(79, result.getSize());
	        result = service.query("//SPEECH[LINE &= 'god in heaven']");
	        assertEquals(2, result.getSize());
	        result = service.query("//SPEECH[SPEAKER &= 'Nurse']");
	        assertEquals(90, result.getSize());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
		}
	}

    public void testFtMatchFunctions() {
        try {
	    	System.out.println("----- testFtOperators -----");
	        XQueryService service = (XQueryService)
	            testCollection.getService("XQueryService", "1.0");
	        ResourceSet result = service.query("//SPEECH[text:match-all(LINE, 'love', 'w')]");
	        assertEquals(160, result.getSize());
            result = service.query("//SPEECH[text:match-all(LINE, 'love')]");
	        assertEquals(190, result.getSize());
            result = service.query("//SPEECH[text:match-all(LINE, 'love', 'w')]");
	        assertEquals(160, result.getSize());

            result = service.query("//SPEECH[text:match-all(LINE, 'fenny', 'snake')]/LINE[1]");
	        assertEquals(1, result.getSize());
	        assertXMLEqual(result.getResource(0).getContent().toString(), "<LINE>Fillet of a fenny snake,</LINE>");
            //assertXMLEqual(result.getResource(0).getContent().toString(), "<LINE>Fillet of a <exist:match xmlns:exist='http://exist.sourceforge.net/NS/exist'>fenny</exist:match> <exist:match xmlns:exist='http://exist.sourceforge.net/NS/exist'>snake</exist:match>,</LINE>");

            result = service.query("//SPEECH[text:match-all(LINE, ('fenny', 'snake'))]/LINE[1]");
	        assertEquals(1, result.getSize());
	        assertXMLEqual(result.getResource(0).getContent().toString(), "<LINE>Fillet of a fenny snake,</LINE>");
            //assertXMLEqual(result.getResource(0).getContent().toString(), "<LINE>Fillet of a <exist:match xmlns:exist='http://exist.sourceforge.net/NS/exist'>fenny</exist:match> <exist:match xmlns:exist='http://exist.sourceforge.net/NS/exist'>snake</exist:match>,</LINE>");
            result = service.query("//SPEECH[text:match-all(LINE, 'god.*')]");
	        assertEquals(79, result.getSize());

            result = service.query("//SPEECH[LINE &= 'god in heaven']");
	        assertEquals(2, result.getSize());
	        result = service.query("//SPEECH[SPEAKER &= 'Nurse']");
	        assertEquals(90, result.getSize());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
		}
    }

    public void testFtScan() {
    	try {
	    	System.out.println("----- testFtScan -----");
	        String queryBody =
	            "declare namespace f=\'http://exist-db.org/xquery/test\';\n" +
	            "import module namespace t=\'http://exist-db.org/xquery/text\';\n" +
	            "\n" +
	            "declare function f:term-callback($term as xs:string, $data as xs:int+)\n" +
	            "as element()+ {\n" +
	            "    <item>\n" +
	            "        <term>{$term}</term>\n" +
	            "        <frequency>{$data[1]}</frequency>\n" +
	            "    </item>\n" +
	            "};\n" +
	            "\n";

	        XQueryService service = (XQueryService)
	            testCollection.getService("XQueryService", "1.0");
	        String query = queryBody + "t:index-terms(collection('" + TEST_COLLECTION_PATH + "'), \'is\', util:function(xs:QName(\'f:term-callback\'), 2), 1000)";
	        ResourceSet result = service.query(query);
	        assertEquals(6, result.getSize());

	        query = queryBody + "t:index-terms(collection('"  + TEST_COLLECTION_PATH + "')//LINE, \'is\', util:function(xs:QName(\'f:term-callback\'), 2), 1000)";
	        result = service.query(query);
	        assertEquals(6, result.getSize());
		} catch (XMLDBException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
    }

    public void testFtUpdate() {
    	try {
	    	System.out.println("----- testFtUpdate -----");
	        XQueryService service = (XQueryService)
	            testCollection.getService("XQueryService", "1.0");
	        service.query(
	                "update insert <SPEAKER>First Witch</SPEAKER> preceding //SPEECH[LINE &= 'fenny snake']/SPEAKER"
	        );
	        ResourceSet result = service.query("//SPEECH[LINE &= 'fenny snake']/SPEAKER");
	        assertEquals(2, result.getSize());
	        result = service.query("//SPEECH[LINE &= 'fenny snake' and SPEAKER &= 'first']");
	        assertEquals(1, result.getSize());

	        service.query(
	                "update delete //SPEECH[LINE &= 'fenny snake']/SPEAKER[2]"
	        );
	        result = service.query("//SPEECH[LINE &= 'fenny snake' and SPEAKER &= 'first']");
	        assertEquals(1, result.getSize());
		} catch (XMLDBException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
    }

    public void testFtConfiguration() {
    	System.out.println("----- testFtConfiguration -----");
    	try {
    		// check attributes="false"
    		String config =
    			"<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
		    	"    <index>" +
		    	"        <fulltext default=\"all\" attributes=\"false\" alphanum=\"true\">" +
		    	"					<include path=\"//test-elem/@attribute1\"/>" +
		    	"				</fulltext>" +
		    	"    </index>" +
		    	"</collection>";
    		IndexQueryService idxConf = (IndexQueryService)
				testCollection.getService("IndexQueryService", "1.0");
    		idxConf.configureCollection(config);

    		XMLResource doc =
                (XMLResource) testCollection.createResource(
                        "test-attributes.xml", "XMLResource");
            doc.setContent(TEST_XML);
            testCollection.storeResource(doc);

            XQueryService service = (XQueryService)
            	testCollection.getService("XQueryService", "1.0");
            String query = "//test-elem[@* &= 'some text']";
            ResourceSet result = service.query(query);
            assertEquals(2, result.getSize());

            // check attributes="true"
            config =
    			"<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
		    	"   <index>" +
		    	"       <fulltext default=\"all\" attributes=\"true\" alphanum=\"true\">" +
		    	"           <exclude path=\"//test-elem/@attribute2\"/>" +
		    	"       </fulltext>" +
		    	"   </index>" +
		    	"</collection>";
            idxConf.configureCollection(config);
            idxConf.reindexCollection();

            result = service.query(query);
            assertEquals(3, result.getSize());
    	} catch(Exception e) {
    		e.printStackTrace();
    		fail(e.getMessage());
    	}
    }

    public void testMixedConfiguration() {
        System.out.println("----- testMixedConfiguration -----");
    	try {
    		String config =
    			"<collection xmlns=\"http://exist-db.org/collection-config/1.0\">\n" +
                "    <index>\n" +
                "        <fulltext default=\"all\" attributes=\"no\">\n" +
                "            <include path=\"//nested\" content=\"mixed\"/>\n" +
                "        </fulltext>\n" +
                "    </index>\n" +
                "</collection>";

            IndexQueryService idxConf = (IndexQueryService)
				testCollection.getService("IndexQueryService", "1.0");
    		idxConf.configureCollection(config);

    		XMLResource doc =
                (XMLResource) testCollection.createResource(
                        "test-mixed.xml", "XMLResource");
            doc.setContent(NESTED_XML);
            testCollection.storeResource(doc);

            XQueryService service = (XQueryService)
            	testCollection.getService("XQueryService", "1.0");
            String query = "//nested[. &= 'inappropriate']";
            ResourceSet result = service.query(query);
            assertEquals(1, result.getSize());

            query = "//nested[. &= 'pro']";
            result = service.query(query);
            assertEquals(1, result.getSize());

            query = "//nested[. &= 'unsuitable']";
            result = service.query(query);
            assertEquals(1, result.getSize());

            query = "//nested[. &= 'uneven']";
            result = service.query(query);
            assertEquals(1, result.getSize());

            query = "//nested[. &= 'suitable']";
            result = service.query(query);
            assertEquals(1, result.getSize());
        } catch(Exception e) {
    		e.printStackTrace();
    		fail(e.getMessage());
    	}
    }

    public void testQNameConfiguration() {
    	System.out.println("----- testFtConfiguration -----");
    	try {
    		String config =
    			"<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
		    	"    <index>" +
		    	"        <fulltext default=\"none\" attributes=\"false\">" +
                "           <include path=\"//node\"/>" +
                "           <create qname=\"node\"/>" +
                "           <create qname=\"@id\"/>" +
                "	      </fulltext>" +
		    	"    </index>" +
		    	"</collection>";
    		IndexQueryService idxConf = (IndexQueryService)
				testCollection.getService("IndexQueryService", "1.0");
    		idxConf.configureCollection(config);

    		XMLResource doc =
                (XMLResource) testCollection.createResource("test-qname.xml", "XMLResource");
            doc.setContent(QNAME_XML);
            testCollection.storeResource(doc);

            XQueryService service = (XQueryService)
            	testCollection.getService("XQueryService", "1.0");
            doQuery(service, "//node[@id &= '1']", 1);
            doQuery(service, "//test[node &= 'Second node']", 1);
            doQuery(service, "//node[. &= 'node']", 2);

            doQuery(service, "update insert <node id='3'>Third node</node> following //test/node[last()]", 0);
            doQuery(service, "//node[@id &= '3']", 1);
            doQuery(service, "//test[node &= 'Third node']", 1);
            doQuery(service, "//node[. &= 'Third node']", 1);
            doQuery(service, "//node[. &= 'node']", 3);

            doQuery(service, "update insert <node id='4'>Fourth <nested>node</nested></node> following //test/node[last()]", 0);
            doQuery(service, "//node[@id &= '4']", 1);
            doQuery(service, "//test[node &= 'Fourth node']", 1);
            doQuery(service, "//node[. &= 'Fourth node']", 1);
            doQuery(service, "//node[. &= 'node']", 4);

            doQuery(service, "update delete //node[@id = '1']", 0);
            doQuery(service, "//node[@id &= '1']", 0);
            doQuery(service, "//test[node &= 'First node']", 0);
            doQuery(service, "//node[. &= 'First node']", 0);
            doQuery(service, "//node[@id &= '2']", 1);
            doQuery(service, "//test[node &= 'Second node']", 1);
            doQuery(service, "//node[. &= 'Second node']", 1);

            doQuery(service,
                    "for $i in 1 to 100 return" +
                            "   update insert <node id='i{$i}'>Inserted node</node> preceding //test/node[1]", 0);

            doQuery(service, "//node[@id &= 'i1']", 1);
            doQuery(service, "//node[. &= 'Inserted']", 100);
            doQuery(service, "//test[node &= 'Inserted']", 1);

            doQuery(service, "//test[* &= 'Inserted node']", 1);
            doQuery(service, "//test[. &= 'Inserted node']", 1);
            doQuery(service, "//test[node() &= 'Second node']", 1);
            doQuery(service, "//node[text() &= 'Second node']", 1);
            doQuery(service, "//node[text() &= 'Inserted node']", 100);
        } catch(Exception e) {
    		e.printStackTrace();
    		fail(e.getMessage());
    	}
    }

    public void testFtQNameScan() {
    	try {
            System.out.println("----- testFtQNameScan -----");
            String config =
    			"<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
		    	"    <index>" +
		    	"        <fulltext default=\"none\" attributes=\"false\">" +
                "           <create qname=\"node\"/>" +
                "           <create qname=\"@id\"/>" +
                "	      </fulltext>" +
		    	"    </index>" +
		    	"</collection>";
    		IndexQueryService idxConf = (IndexQueryService)
				testCollection.getService("IndexQueryService", "1.0");
    		idxConf.configureCollection(config);

    		XMLResource doc =
                (XMLResource) testCollection.createResource("test-qname.xml", "XMLResource");
            doc.setContent(QNAME_XML);
            testCollection.storeResource(doc);

            XQueryService service = (XQueryService) testCollection.getService("XQueryService", "1.0");
            doQuery(service, "//node", 2);

            String queryBody =
	            "declare namespace f=\'http://exist-db.org/xquery/test\';\n" +
	            "import module namespace t=\'http://exist-db.org/xquery/text\';\n" +
	            "\n" +
	            "declare function f:term-callback($term as xs:string, $data as xs:int+)\n" +
	            "as element()+ {\n" +
	            "    <item>\n" +
	            "        <term>{$term}</term>\n" +
	            "        <frequency>{$data[1]}</frequency>\n" +
	            "    </item>\n" +
	            "};\n" +
	            "\n";

	        String query = queryBody + "t:index-terms(collection('" + TEST_COLLECTION_PATH + "')//node, (), util:function(xs:QName(\'f:term-callback\'), 2), 1000)";
	        ResourceSet result = service.query(query);
            for (ResourceIterator i = result.getIterator(); i.hasMoreResources(); ) {
                Resource resource = i.nextResource();
                System.out.println(resource.getContent());
            }
            assertEquals(8, result.getSize());

            query = queryBody + "t:index-terms(collection('" + TEST_COLLECTION_PATH + "')//node, " +
                "xs:QName('node'), (), util:function(xs:QName(\'f:term-callback\'), 2), 1000)";
	        result = service.query(query);
            for (ResourceIterator i = result.getIterator(); i.hasMoreResources(); ) {
                Resource resource = i.nextResource();
                System.out.println(resource.getContent());
            }
            assertEquals(3, result.getSize());

	        query = "import module namespace t=\'http://exist-db.org/xquery/text\';\n" +
                    "t:fuzzy-index-terms('node')";
	        result = service.query(query);
            for (ResourceIterator i = result.getIterator(); i.hasMoreResources(); ) {
                Resource resource = i.nextResource();
                System.out.println(resource.getContent());
            }
            assertEquals(1, result.getSize());
        } catch (XMLDBException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
    }

    public void testReindex() {
        try {
            String config =
    			"<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
		    	"    <index>" +
		    	"        <fulltext default=\"none\" attributes=\"false\">" +
                "           <include path=\"//node\"/>" +
                "           <create qname=\"node\"/>" +
                "           <create qname=\"@id\"/>" +
                "	      </fulltext>" +
		    	"    </index>" +
		    	"</collection>";
    		IndexQueryService idxConf = (IndexQueryService)
				testCollection.getService("IndexQueryService", "1.0");
    		idxConf.configureCollection(config);

            XMLResource doc =
                (XMLResource) testCollection.createResource("test-qname.xml", "XMLResource");
            doc.setContent(QNAME_XML);
            testCollection.storeResource(doc);

            XQueryService service = (XQueryService)
            	testCollection.getService("XQueryService", "1.0");
            doQuery(service, "update insert <node id='3'>Third node</node> following //test/node[last()]", 0);
            doQuery(service, "//node[@id &= '3']", 1);
            doQuery(service, "//test[node &= 'Third node']", 1);
            doQuery(service, "//node[. &= 'Third node']", 1);
            doQuery(service, "//node[. &= 'node']", 3);

            IndexQueryService mgmt = (IndexQueryService) testCollection.getService("IndexQueryService", "1.0");
            mgmt.reindexCollection();

            doQuery(service, "//node[@id &= '1']", 1);
            doQuery(service, "//test[node &= 'Second node']", 1);
            doQuery(service, "//node[. &= 'Second node']", 1);
            doQuery(service, "//node[. &= 'node']", 3);
            doQuery(service, "//node[@id &= '3']", 1);
            doQuery(service, "//test[node &= 'Third node']", 1);
            doQuery(service, "//node[. &= 'Third node']", 1);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    public void testMatchCount() {
        System.out.println("----- testMatchCount -----");
    	try {
            String config =
    			"<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
		    	"    <index>" +
		    	"        <fulltext default=\"none\" attributes=\"false\">" +
                "           <include path=\"/doc\"/>" +
                "	      </fulltext>" +
		    	"    </index>" +
		    	"</collection>";
    		IndexQueryService idxConf = (IndexQueryService)
				testCollection.getService("IndexQueryService", "1.0");
    		idxConf.configureCollection(config);

    		XMLResource doc =
                (XMLResource) testCollection.createResource(
                        "test-match-count.xml", "XMLResource");
            doc.setContent(MATCH_COUNT);
            testCollection.storeResource(doc);

            XQueryService service = (XQueryService)
            	testCollection.getService("XQueryService", "1.0");
            String query = "for $d in /doc[. &= 'term'] " +
        	"return (text:match-count($d), " +
        	"for $l in $d/level1 return text:match-count($l))";
            ResourceSet result = service.query(query);
            assertEquals(3, result.getSize());
            assertEquals("6", result.getResource(0).getContent().toString());
            assertEquals("2", result.getResource(1).getContent().toString());
            assertEquals("2", result.getResource(2).getContent().toString());
        } catch(Exception e) {
    		e.printStackTrace();
    		fail(e.getMessage());
    	}
    }
    
    //It looks like matches are not copied along all axes 
    public void bugtestMatchCount() {
        System.out.println("----- testMatchCount -----");
    	try {
            String config =
    			"<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
		    	"    <index>" +
		    	"        <fulltext default=\"none\" attributes=\"false\">" +
                "           <include path=\"/doc\"/>" +
                "	      </fulltext>" +
		    	"    </index>" +
		    	"</collection>";
    		IndexQueryService idxConf = (IndexQueryService)
				testCollection.getService("IndexQueryService", "1.0");
    		idxConf.configureCollection(config);

    		XMLResource doc =
                (XMLResource) testCollection.createResource(
                        "test-match-count.xml", "XMLResource");
            doc.setContent(MATCH_COUNT);
            testCollection.storeResource(doc);

            XQueryService service = (XQueryService)
            	testCollection.getService("XQueryService", "1.0");
            String query = "for $d in /doc[. &= 'term'] " +
	        	"return text:match-count($d/level1[1]/..)";
            ResourceSet result = service.query(query);
	        assertEquals(1, result.getSize());
	        assertEquals("2", result.getResource(0).getContent().toString());
	        
            query = "for $node in /doc//*[. &= 'term'] " +
        	"return concat(local-name($node), '(', text:match-count($node), ')')" ;
            result = service.query(query);
            assertEquals(4, result.getSize());            
            assertEquals("doc(6)", result.getResource(0).getContent().toString());
            assertEquals("level1(2)", result.getResource(1).getContent().toString());
            assertEquals("level1(2)", result.getResource(2).getContent().toString());
            assertEquals("level2(1)", result.getResource(3).getContent().toString());
	    	} catch(Exception e) {
    		e.printStackTrace();
    		fail(e.getMessage());
    	}
    }    

    private void doQuery(XQueryService service, String query, int expected) throws XMLDBException {
        ResourceSet result = service.query(query);
        assertEquals(expected, result.getSize());
    }

    protected void setUp() {
        try {        	
        	
        	//Since we use the deprecated fn:match-all() function, we have to be sure is is enabled
            Configuration config = new Configuration();
            config.setProperty(FunctionFactory.PROPERTY_DISABLE_DEPRECATED_FUNCTIONS, new Boolean(false));
            BrokerPool.configure(1, 5, config);       
        	
            // initialize driver
            Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
            database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);         
            
            Collection root =
                DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
            
            CollectionManagementService service =
                (CollectionManagementService) root.getService(
                    "CollectionManagementService",
                    "1.0");
            testCollection = service.createCollection(TEST_COLLECTION_NAME);
            assertNotNull(testCollection);

            IndexQueryService idxConf = (IndexQueryService) testCollection.getService("IndexQueryService", "1.0");
            idxConf.configureCollection(COLLECTION_CONFIG1);
            
            for (int i = 0; i < FILES.length; i++) {
                XMLResource doc =
                    (XMLResource) testCollection.createResource(
                            FILES[i], "XMLResource" );
                doc.setContent(new File(SHAKES_DIR, FILES[i]));
                testCollection.storeResource(doc);                
                assertNotNull(testCollection.getResource(FILES[i]));
            }

        } catch (ClassNotFoundException e) {
        } catch (InstantiationException e) {
        } catch (IllegalAccessException e) {
        } catch (DatabaseConfigurationException e) {
        } catch (EXistException e) {
        } catch (XMLDBException e) {
            e.printStackTrace();
        }
    }

    /*
     * @see TestCase#tearDown()
     */
    protected void tearDown() {
    	try {
//	        TestUtils.cleanupDB();

            DatabaseManager.deregisterDatabase(database);
	        DatabaseInstanceManager dim =
	            (DatabaseInstanceManager) testCollection.getService(
	                "DatabaseInstanceManager", "1.0");
	        dim.shutdown();
            database = null;
            testCollection = null;
	        System.out.println("tearDown PASSED");
		} catch (XMLDBException e) {
			e.printStackTrace();
		}
    }
    
    public static void main(String[] args) {
        TestRunner.run(FtQueryTest.class);
    }
}
