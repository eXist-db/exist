/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
package org.exist.xquery;

import java.io.File;

import junit.framework.TestCase;

import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.IndexQueryService;
import org.exist.xmldb.XmldbURI;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.modules.XUpdateQueryService;

/**
 * @author wolf
 */
public class ValueIndexTest extends TestCase {

    private final static String URI = XmldbURI.LOCAL_DB;

    private String CONFIG =
    	"<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" + 
    	"	<index xmlns:x=\"http://www.foo.com\" xmlns:xx=\"http://test.com\">" +
    	"		<create path=\"//item/itemno\" type=\"xs:integer\"/>" +
    	"		<create path=\"//item/@id\" type=\"xs:string\"/>" +
    	"		<create path=\"//item/name\" type=\"xs:string\"/>" + 
    	"		<create path=\"//item/stock\" type=\"xs:integer\"/>" + 
    	"		<create path=\"//item/price\" type=\"xs:double\"/>" + 
    	"		<create path=\"//item/price/@specialprice\" type=\"xs:boolean\"/>" + 
    	"		<create path=\"//item/x:rating\" type=\"xs:double\"/>" +
    	"		<create path=\"//item/@xx:test\" type=\"xs:integer\"/>" +
    	"       <create path=\"//item/mixed\" type=\"xs:string\"/>" +
        "       <create path=\"//city/name\" type=\"xs:string\"/>" +
        "	</index>" +
    	"</collection>";

    private String CONFIG_QNAME =
    	"<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index xmlns:x=\"http://www.foo.com\" xmlns:xx=\"http://test.com\">" +
        "       <create qname=\"itemno\" type=\"xs:integer\"/>" +
        "       <create qname=\"mixed\" type=\"xs:string\"/>" +
        "       <create qname=\"stock\" type=\"xs:integer\"/>" +
        "       <create qname=\"name\" type=\"xs:string\"/>" +
        "       <create qname=\"@id\" type=\"xs:string\"/>" +
        "       <create qname=\"price\" type=\"xs:double\"/>" +
        "		<create path=\"x:rating\" type=\"xs:double\"/>" +
        "	</index>" +
    	"</collection>";

    private String CITY =
            "<mondial>" +
            "   <city id=\"cty-Germany-Berlin\" is_country_cap=\"yes\" is_state_cap=\"yes\" " +
            "       country=\"D\" province=\"prov-cid-cia-Germany-4\">" +
            "       <name>Berlin</name>" +
            "       <longitude>13.3</longitude>" +
            "       <latitude>52.45</latitude>" +
            "       <population year=\"95\">3472009</population>" +
            "   </city>" +
            "   <city id=\"cty-cid-cia-Germany-85\" country=\"D\" province=\"prov-cid-cia-Germany-3\">" +
            "       <name>Erlangen</name>" +
            "       <population year=\"95\">101450</population>" +
            "   </city>" +
            "</mondial>";
    
    private Collection testCollection;

    protected void setUp() {
        try {
            // initialize driver
            Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
            Database database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);

            Collection root = DatabaseManager.getCollection(URI, "admin", null);
            CollectionManagementService service = (CollectionManagementService) root
                    .getService("CollectionManagementService", "1.0");
            testCollection = service.createCollection("test");
            assertNotNull(testCollection);
            
        } catch (ClassNotFoundException e) {
        } catch (InstantiationException e) {
        } catch (IllegalAccessException e) {
        } catch (XMLDBException e) {
            e.printStackTrace();
        }
    }

    /*
     * @see TestCase#tearDown()
     */
    protected void tearDown() throws Exception {
        Collection root = DatabaseManager.getCollection(URI, "admin", null);
        CollectionManagementService service = (CollectionManagementService) root
                    .getService("CollectionManagementService", "1.0");
        service.removeCollection("test");
        DatabaseInstanceManager dim =
            (DatabaseInstanceManager) testCollection.getService("DatabaseInstanceManager", "1.0");
        dim.shutdown();
        testCollection = null;
        //System.out.println("tearDown PASSED");
    }
    
	/**
	 * @throws XMLDBException
	 */
	protected void configureCollection(String config) throws XMLDBException {
		IndexQueryService idxConf = (IndexQueryService)
			testCollection.getService("IndexQueryService", "1.0");
		idxConf.configureCollection(config);
	}

    public void testStrings() throws Exception {
        try {
            configureCollection(CONFIG);
            XPathQueryService service = storeXMLFileAndGetQueryService("items.xml", "test/src/org/exist/xquery/items.xml");
            queryResource(service, "items.xml", "//item[@id = 'i2']", 1);
            queryResource(service, "items.xml", "//item[name = 'Racing Bicycle']", 1);
            queryResource(service, "items.xml", "//item[name > 'Racing Bicycle']", 4);
            queryResource(service, "items.xml", "//item[itemno = 3]", 1);
            queryResource(service, "items.xml", "//item[itemno eq 3]", 1);
            ResourceSet result = queryResource(service, "items.xml", "for $i in //item[stock <= 10] return $i/itemno", 5);
            for (long i = 0; i < result.getSize(); i++) {
                Resource res = result.getResource(i);
                System.out.println(res.getContent());
            }

            queryResource(service, "items.xml", "//item[stock > 20]", 1);
            queryResource(service, "items.xml", "declare namespace x=\"http://www.foo.com\"; //item[x:rating > 8.0]", 2);
            queryResource(service, "items.xml", "declare namespace xx=\"http://test.com\"; //item[@xx:test = 123]", 1);
            queryResource(service, "items.xml", "declare namespace xx=\"http://test.com\"; //item[@xx:test eq 123]", 1);
            queryResource(service, "items.xml", "//item[mixed = 'uneven']", 1);
            queryResource(service, "items.xml", "//item[mixed eq 'uneven']", 1);
            queryResource(service, "items.xml", "//item[mixed = 'external']", 1);
            queryResource(service, "items.xml", "//item[fn:matches(mixed, 'un.*')]", 2);
            queryResource(service, "items.xml", "//item[price/@specialprice = false()]", 2);
            queryResource(service, "items.xml", "//item[price/@specialprice = true()]", 1);
            queryResource(service, "items.xml", "//item[price/@specialprice eq true()]", 1);
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    public void testStrFunctions() {
        try {
            configureCollection(CONFIG);
            XMLResource resource = (XMLResource) testCollection.createResource("mondial-test.xml", "XMLResource");
            resource.setContent(CITY);
            testCollection.storeResource(resource);

            XPathQueryService service = (XPathQueryService) testCollection.getService("XPathQueryService", "1.0");
            queryResource(service, "mondial-test.xml", "//city[starts-with(name, 'Berl')]", 1);
            queryResource(service, "mondial-test.xml", "//city[starts-with(name, 'Berlin')]", 1);
            queryResource(service, "mondial-test.xml", "//city[starts-with(name, 'erlin')]", 0);
            queryResource(service, "mondial-test.xml", "//city[starts-with(name, 'Erl')]", 1);
            queryResource(service, "mondial-test.xml", "//city[contains(name, 'erl')]", 1);
            queryResource(service, "mondial-test.xml", "//city[contains(name, 'Berlin')]", 1);
            queryResource(service, "mondial-test.xml", "//city[contains(name, 'Erl')]", 1);
            queryResource(service, "mondial-test.xml", "//city[ends-with(name, 'Berlin')]", 1);
            queryResource(service, "mondial-test.xml", "//city[ends-with(name, 'erlin')]", 1);
            queryResource(service, "mondial-test.xml", "//city[ends-with(name, 'Ber')]", 0);

            queryResource(service, "mondial-test.xml", "//city[matches(name, 'erl', 'i')]", 2);
            queryResource(service, "mondial-test.xml", "//city[matches(name, 'Erl')]", 1);
            queryResource(service, "mondial-test.xml", "//city[matches(name, 'Berlin', 'i')]", 1);
            queryResource(service, "mondial-test.xml", "//city[matches(name, 'berlin', 'i')]", 1);
            queryResource(service, "mondial-test.xml", "//city[matches(name, 'berlin')]", 0);
            queryResource(service, "mondial-test.xml", "//city[matches(name, '^Berlin$')]", 1);
            queryResource(service, "mondial-test.xml", "//city[matches(name, 'lin$', 'i')]", 1);
            queryResource(service, "mondial-test.xml", "//city[matches(name, '.*lin$', 'i')]", 1);
            queryResource(service, "mondial-test.xml", "//city[matches(name, '^lin$', 'i')]", 0);
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
	
	
	/*
     * Bugfix
     *
     * These following two tests were put in place to demonstrate bugs in how the index matching functions work,
     * as a precursor to a fix, which was committed 2/3/2010. The issue was that the 2nd parameter
	 * to the string matching functions was incorrectly interpreted as a regex, which causd an exception
	 * to be thrown if the string included characters that have special meaning in a regex, eg. '*' for contains.
	 *
	 * andrzej@chaeron.com
     */
	public void testPathIndexStringMatchingFunctions() throws Exception
	{
      	try {
            configureCollection( CONFIG );
            XMLResource resource = (XMLResource)testCollection.createResource( "mondial-test.xml", "XMLResource" );
            resource.setContent( CITY );
            testCollection.storeResource( resource );

            XPathQueryService service = (XPathQueryService) testCollection.getService( "XPathQueryService", "1.0" );
			
			queryResource(service, "mondial-test.xml", "//city[ starts-with( name, '^*' ) ]", 0);
            queryResource(service, "mondial-test.xml", "//city[ contains( name, '^*' ) ]", 0);
            queryResource(service, "mondial-test.xml", "//city[ ends-with( name, '^*' ) ]", 0);
		} 
		catch( XMLDBException e ) {
			e.printStackTrace();
			fail( e.getMessage() );
		}
	}    
    
	
	public void testPathIndexStringMatchingFunctions2() throws Exception
	{
      	try {
            configureCollection( CONFIG );
            XMLResource resource = (XMLResource)testCollection.createResource( "mondial-test.xml", "XMLResource" );
            resource.setContent( CITY );
            testCollection.storeResource( resource );

            XPathQueryService service = (XPathQueryService) testCollection.getService( "XPathQueryService", "1.0" );
			
			try {
            	queryResource(service, "mondial-test.xml", "//city[ starts-with( name, '(*' ) ]", 0);
			}
			catch( Exception e ) {
				e.printStackTrace();
				fail( e.getMessage() );
			}
			
			try {
            	 queryResource(service, "mondial-test.xml", "//city[ contains( name, '*' ) ]", 0);
			}
			catch( Exception e ) {
				e.printStackTrace();
				fail( e.getMessage() );
			}
			
			try {
            	 queryResource(service, "mondial-test.xml", "//city[ ends-with( name, '(*' ) ]", 0);
			}
			catch( Exception e ) {
				e.printStackTrace();
				fail( e.getMessage() );
			}
		} 
		catch( XMLDBException e ) {
			e.printStackTrace();
			fail( e.getMessage() );
		}
	}    
	
	
	/*
     * Bugfix
     *
     * These following two tests were put in place to test a bug fix for QName matching functions, which was committed 2/19/2010. The issue was that the 2nd parameter
	 * to the string matching functions was incorrectly interpreted as a regex, for QName indexes, which causd an exception
	 * to be thrown if the string included characters that have special meaning in a regex, eg. '*' for contains.
	 *
	 * andrzej@chaeron.com
     */
	public void testQNameIndexStringMatchingFunctions() throws Exception
	{
      	try {
            configureCollection( CONFIG_QNAME );
            XMLResource resource = (XMLResource)testCollection.createResource( "mondial-test.xml", "XMLResource" );
            resource.setContent( CITY );
            testCollection.storeResource( resource );

            XPathQueryService service = (XPathQueryService) testCollection.getService( "XPathQueryService", "1.0" );
			
			queryResource(service, "mondial-test.xml", "//city[ starts-with( name, '^*' ) ]", 0);
            queryResource(service, "mondial-test.xml", "//city[ contains( name, '^*' ) ]", 0);
            queryResource(service, "mondial-test.xml", "//city[ ends-with( name, '^*' ) ]", 0);
		} 
		catch( XMLDBException e ) {
			e.printStackTrace();
			fail( e.getMessage() );
		}
	}    
    
	
	public void testQNameIndexStringMatchingFunctions2() throws Exception
	{
      	try {
            configureCollection( CONFIG_QNAME );
            XMLResource resource = (XMLResource)testCollection.createResource( "mondial-test.xml", "XMLResource" );
            resource.setContent( CITY );
            testCollection.storeResource( resource );

            XPathQueryService service = (XPathQueryService) testCollection.getService( "XPathQueryService", "1.0" );
			
			try {
            	queryResource(service, "mondial-test.xml", "//city[ starts-with( name, '(*' ) ]", 0);
			}
			catch( Exception e ) {
				e.printStackTrace();
				fail( e.getMessage() );
			}
			
			try {
            	 queryResource(service, "mondial-test.xml", "//city[ contains( name, '*' ) ]", 0);
			}
			catch( Exception e ) {
				e.printStackTrace();
				fail( e.getMessage() );
			}
			
			try {
            	 queryResource(service, "mondial-test.xml", "//city[ ends-with( name, '(*' ) ]", 0);
			}
			catch( Exception e ) {
				e.printStackTrace();
				fail( e.getMessage() );
			}
		} 
		catch( XMLDBException e ) {
			e.printStackTrace();
			fail( e.getMessage() );
		}
	}    
	

    public void testStrFunctionsQName() {
        try {
            configureCollection(CONFIG_QNAME);
            XMLResource resource = (XMLResource) testCollection.createResource("mondial-test.xml", "XMLResource");
            resource.setContent(CITY);
            testCollection.storeResource(resource);

            XPathQueryService service = (XPathQueryService) testCollection.getService("XPathQueryService", "1.0");
            queryResource(service, "mondial-test.xml", "//city[starts-with(name, 'Berl')]", 1);
            queryResource(service, "mondial-test.xml", "//city[starts-with(name, 'Berlin')]", 1);
            queryResource(service, "mondial-test.xml", "//city[starts-with(name, 'erlin')]", 0);
            queryResource(service, "mondial-test.xml", "//city[starts-with(name, 'Erl')]", 1);
            queryResource(service, "mondial-test.xml", "//city[contains(name, 'erl')]", 1);
            queryResource(service, "mondial-test.xml", "//city[contains(name, 'Berlin')]", 1);
            queryResource(service, "mondial-test.xml", "//city[contains(name, 'Erl')]", 1);
            queryResource(service, "mondial-test.xml", "//city[ends-with(name, 'Berlin')]", 1);
            queryResource(service, "mondial-test.xml", "//city[ends-with(name, 'erlin')]", 1);
            queryResource(service, "mondial-test.xml", "//city[ends-with(name, 'Ber')]", 0);

            queryResource(service, "mondial-test.xml", "//city[matches(name, 'erl', 'i')]", 2);
            queryResource(service, "mondial-test.xml", "//city[matches(name, 'Erl')]", 1);
            queryResource(service, "mondial-test.xml", "//city[matches(name, 'Berlin', 'i')]", 1);
            queryResource(service, "mondial-test.xml", "//city[matches(name, 'berlin', 'i')]", 1);
            queryResource(service, "mondial-test.xml", "//city[matches(name, 'berlin')]", 0);
            queryResource(service, "mondial-test.xml", "//city[matches(name, '^Berlin$')]", 1);
            queryResource(service, "mondial-test.xml", "//city[matches(name, 'lin$', 'i')]", 1);
            queryResource(service, "mondial-test.xml", "//city[matches(name, '.*lin$', 'i')]", 1);
            queryResource(service, "mondial-test.xml", "//city[matches(name, '^lin$', 'i')]", 0);
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    public void testQNameIndex() {
        try {
            configureCollection(CONFIG_QNAME);
            XPathQueryService service = storeXMLFileAndGetQueryService("items.xml", "test/src/org/exist/xquery/items.xml");
            queryResource(service, "items.xml", "//((#exist:optimize#) { item[stock = 10] })", 1);
            queryResource(service, "items.xml", "//((#exist:optimize#) { item[stock > 20] })", 1);
            queryResource(service, "items.xml", "//((#exist:optimize#) { item[stock < 16] })", 6);
            queryResource(service, "items.xml", "declare namespace x=\"http://www.foo.com\"; " +
                    "//((#exist:optimize#) { item[x:rating > 8.0] })", 2);
            queryResource(service, "items.xml", "//((#exist:optimize#) { item[mixed = 'uneven'] })", 1);
		    queryResource(service, "items.xml", "//((#exist:optimize#) { item[mixed = 'external'] })", 1);
            queryResource(service, "items.xml", "//((#exist:optimize#) { item[@id = 'i1'] })",1);
            queryResource(service, "items.xml", "declare namespace xx=\"http://test.com\";" +
                    "//((#exist:optimize#) { item[@xx:test = 123] })", 1);
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    public void testIndexScan() {
        try {
            System.out.println("----- testIndexScan -----");
            configureCollection(CONFIG);
            String queryBody =
                "declare namespace f=\'http://exist-db.org/xquery/test\';\n" + 
                "declare namespace mods='http://www.loc.gov/mods/v3';\n" + 
                "import module namespace u=\'http://exist-db.org/xquery/util\';\n" + 
                "\n" + 
                "declare function f:term-callback($term as item(), $data as xs:int+)\n" +
                "as element()+ {\n" + 
                "    <item>\n" + 
                "        <term>{$term}</term>\n" + 
                "        <frequency>{$data[1]}</frequency>\n" + 
                "    </item>\n" + 
                "};\n" + 
                "\n";
            
            XPathQueryService service = storeXMLFileAndGetQueryService("items.xml", "test/src/org/exist/xquery/items.xml");
            String query = queryBody + "u:index-keys(//item/name, \'\', util:function(xs:QName(\'f:term-callback\'), 2), 1000)";
            ResourceSet result = service.query(query);
            for (ResourceIterator i = result.getIterator(); i.hasMoreResources(); ) {
                System.out.println(i.nextResource().getContent());
            }
            assertEquals(7, result.getSize());

            query = queryBody + "u:index-keys(//item/stock, 0, util:function(xs:QName(\'f:term-callback\'), 2), 1000)";
            result = service.query(query);
            for (ResourceIterator i = result.getIterator(); i.hasMoreResources(); ) {
                System.out.println(i.nextResource().getContent());
            }
            assertEquals(5, result.getSize());
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }
    
    public void testUpdates() throws Exception {
        try {
            configureCollection(CONFIG);
            storeXMLFileAndGetQueryService("items.xml", "test/src/org/exist/xquery/items.xml");
            for (int i = 100; i <= 150; i++) {
                String append =
                    "<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">" +
                    "   <xu:append select=\"/items\">" +
                    "       <item id=\"i" + i + "\">" +
                    "           <itemno>" + i + "</itemno>" +
                    "           <name>New Item</name>" +
                    "           <price>55.50</price>" +
                    "       </item>" +
                    "   </xu:append>" +
                    "</xu:modifications>";
                String remove =
                    "<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">" +
                    "   <xu:remove select=\"/items/item[itemno=" + i + "]\"/>" +
                    "</xu:modifications>";

                XPathQueryService query = (XPathQueryService) testCollection.getService("XPathQueryService", "1.0");
                XUpdateQueryService update = (XUpdateQueryService) testCollection.getService("XUpdateQueryService", "1.0");
                long mods = update.updateResource("items.xml", append);
                assertEquals(mods, 1);
                queryResource(query, "items.xml", "//item[price = 55.50]", 1);
                queryResource(query, "items.xml", "//item[@id = 'i" + i + "']",1);
                mods = update.updateResource("items.xml", remove);
                assertEquals(mods, 1);
                queryResource(query, "items.xml", "//item[itemno = " + i + "]", 0);
            }
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    public void testUpdatesQName() throws Exception {
        try {
            configureCollection(CONFIG_QNAME);
            storeXMLFileAndGetQueryService("items.xml", "test/src/org/exist/xquery/items.xml");
            for (int i = 100; i <= 150; i++) {
                String append =
                    "<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">" +
                    "   <xu:append select=\"/items\">" +
                    "       <item id=\"i" + i + "\">" +
                    "           <itemno>" + i + "</itemno>" +
                    "           <name>New Item</name>" +
                    "           <price>55.50</price>" +
                    "       </item>" +
                    "   </xu:append>" +
                    "</xu:modifications>";
                String remove =
                    "<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">" +
                    "   <xu:remove select=\"/items/item[itemno=" + i + "]\"/>" +
                    "</xu:modifications>";

                XPathQueryService query = (XPathQueryService) testCollection.getService("XPathQueryService", "1.0");
                XUpdateQueryService update = (XUpdateQueryService) testCollection.getService("XUpdateQueryService", "1.0");
                long mods = update.updateResource("items.xml", append);
                assertEquals(mods, 1);
                queryResource(query, "items.xml", "//((#exist:optimize#) { item[price = 55.50] })", 1);
                queryResource(query, "items.xml", "//((#exist:optimize#) { item[@id = 'i" + i + "']})",1);
                queryResource(query, "items.xml", "//((#exist:optimize#) { item[itemno = " + i + "] })", 1);
                mods = update.updateResource("items.xml", remove);
                assertEquals(mods, 1);
                queryResource(query, "items.xml", "//((#exist:optimize#) { item[itemno = " + i + "] })", 0);
            }
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    protected ResourceSet queryResource(XPathQueryService service,
            String resource, String query, int expected) throws XMLDBException {
        return queryResource(service, resource, query, expected, null);
    }

    /**
     * @param service
     * @throws XMLDBException
     */
    private ResourceSet queryResource(XPathQueryService service,
            String resource, String query, int expected, String message)
            throws XMLDBException {
        ResourceSet result = service.queryResource(resource, query);
        if (message == null)
            assertEquals(expected, result.getSize());
        else
            assertEquals(message, expected, result.getSize());
        return result;
    }

    /**
     * @return
     * @throws XMLDBException
     */
    protected XPathQueryService storeXMLFileAndGetQueryService(
            String documentName, String path) throws XMLDBException {
        XMLResource doc = (XMLResource) testCollection.createResource(
                documentName, "XMLResource");
        String existHome = System.getProperty("exist.home");
        File existDir = existHome==null ? new File(".") : new File(existHome);
        File f = new File(existDir,path);
        doc.setContent(f);
        testCollection.storeResource(doc);
        XPathQueryService service = (XPathQueryService) testCollection
                .getService("XPathQueryService", "1.0");
        return service;
    }
    
    public static void main(String[] args) {
        junit.textui.TestRunner.run(ValueIndexTest.class);
	}
}