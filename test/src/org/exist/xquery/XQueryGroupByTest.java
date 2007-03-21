/* 
 *  eXist Open Source Native XML Database 
 *  Copyright (C) 2001-06 The eXist Project 
 *  http://exist-db.org 
 *  http://exist.sourceforge.net 
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

import org.custommonkey.xmlunit.XMLTestCase;
import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.w3c.dom.Element;
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
 
/** concerns the Group By extension for XQuery  
 *  
 * @author Boris Verhaegen (boris.verhaegen@gmail.com) 
 *  
 *  */ 
 
public class XQueryGroupByTest  extends XMLTestCase { 
 
    private static final String BINARYTABLE_XML = "binaryTable.xml"; 
    private static final String BEYER_XML = "beyer.xml"; 
    private static final String ITEMS_XML = "items.xml"; 
    
     
    private final static String binaryTable = 
        "<items>" 
        +     "<item><key1>1</key1><key2>1</key2></item>" 
        +    "<item><key1>1</key1><key2>0</key2></item>" 
        +    "<item><key1>0</key1><key2>1</key2></item>" 
        +    "<item><key1>0</key1><key2>0</key2></item>" 
        +     "<item><key1>1</key1><key2>1</key2></item>" 
        +    "<item><key1>1</key1><key2>0</key2></item>" 
        +    "<item><key1>0</key1><key2>1</key2></item>" 
        +    "<item><key1>0</key1><key2>0</key2></item>" 
        +"</items>"; 
     
    private final static String beyer = 
        "<books>"+ 
        "    <book>"+ 
        "        <title>Transaction Processing</title>"+ 
        "        <publisher>Morgan Kaufmann</publisher>"+ 
        "        <year>1993</year>"+ 
        "        <price>59.00</price>"+ 
        "        <categories>"+ 
        "            <software>"+ 
        "                <db>"+ 
        "                    <concurrency/>"+ 
        "                </db>"+ 
        "                <distributed/>"+ 
        "            </software>"+ 
        "        </categories>"+ 
        "    </book>"+ 
        "    <book>"+ 
        "        <title>Readings in Database Systems</title>"+ 
        "        <publisher>Morgan Kaufmann</publisher>"+ 
        "        <year>1998</year>"+ 
        "        <price>65.00</price>"+ 
        "        <categories>"+ 
        "            <software>"+ 
        "                <db/>"+ 
        "            </software>"+ 
        "            <anthology/>"+ 
        "        </categories>"+ 
        "    </book>"+ 
        "</books>"; 
     
    private final static String items = 
        "<items>" 
        +     "<item><key1>11</key1><key2>1</key2></item>" 
        +    "<item><key1>1</key1><key2>11</key2></item>" 
        +"</items>";     
    
    
    private Collection testCollection; 
    private Database database; 
    private CollectionManagementService testService; 
     
    public XQueryGroupByTest(String arg0) { 
        super(arg0); 
    } 
     
    protected void setUp() { 
        try { 
            // initialize driver 
            Class cl = Class.forName("org.exist.xmldb.DatabaseImpl"); 
            database = (Database) cl.newInstance(); 
            database.setProperty("create-database", "true"); 
            DatabaseManager.registerDatabase(database); 
             
            Collection root = 
                DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin",    null); 
            testService = 
                (CollectionManagementService) root.getService("CollectionManagementService", "1.0"); 
            testCollection = testService.createCollection("testGB"); 
            assertNotNull(testCollection); 
 
        } catch (ClassNotFoundException e) { 
        } catch (InstantiationException e) { 
        } catch (IllegalAccessException e) { 
        } catch (XMLDBException e) { 
            e.printStackTrace(); 
        } 
    } 
 
    protected void tearDown() throws Exception { 
         
        testService.removeCollection("testGB"); 
        DatabaseManager.deregisterDatabase(database); 
        DatabaseInstanceManager dim = 
            (DatabaseInstanceManager) testCollection.getService( 
                "DatabaseInstanceManager", "1.0"); 
        dim.shutdown(); 
        testCollection = null; 
        database = null; 
         
        System.out.println("tearDown PASSED"); 
    } 
     
     
    public void testGroupByOneKey(){ 
        ResourceSet result; 
        String query; 
        try { 
            XPathQueryService service =  
                storeXMLStringAndGetQueryService(BINARYTABLE_XML, binaryTable); 
 
            System.out.println("testGroupBy 1: ========" ); 
            query = "for $item in //item group $item as $partition by $item/key1 "+ 
                    "as $key1 return count($partition)"; 
            result = service.queryResource(BINARYTABLE_XML, query ); 
            printResult(result); 
            assertEquals( "XQuery: " + query, 2, result.getSize() ); 
        } 
        catch (Exception e) { 
               System.out.println("testGroupByClause : XMLDBException: "+e); 
               fail(e.getMessage()); 
        } 
    } 
 
    public void testGroupByTwoKeys(){ 
        ResourceSet result; 
        String query; 
        try { 
            XPathQueryService service =  
                storeXMLStringAndGetQueryService(BINARYTABLE_XML, binaryTable); 
             
            System.out.println("testGroupBy 2: ========" ); 
            query = "for $item in //item group $item as $partition by $item/key1 "+ 
                    "as $key1, $item/key2 as $key2 return count($partition)"; 
            result = service.queryResource(BINARYTABLE_XML, query ); 
            printResult(result); 
            assertEquals( "XQuery: " + query, 4, result.getSize() );     
        } 
        catch (Exception e) { 
               System.out.println("testGroupByClause : XMLDBException: "+e); 
               fail(e.getMessage()); 
        } 
    } 
     
    public void testGroupByKeyVariable(){ 
        ResourceSet result; 
        String query; 
        XMLResource resu; 
        try { 
            XPathQueryService service =  
                storeXMLStringAndGetQueryService(BINARYTABLE_XML, binaryTable); 
             
            System.out.println("testGroupBy 3: ========" ); 
            query = "for $item in //item group $item as $partition by $item/key1 "+ 
                    "as $key1 order by $key1 return $key1"; 
            result = service.queryResource(BINARYTABLE_XML, query ); 
            printResult(result); 
            resu = (XMLResource) result.getResource(0); 
            assertEquals( "XQuery: " + query, "0", ((Element)resu.getContentAsDOM()).getNodeValue() );         
            resu = (XMLResource) result.getResource(1); 
            assertEquals( "XQuery: " + query, "1", ((Element)resu.getContentAsDOM()).getNodeValue() );     
        } 
        catch (Exception e) { 
               System.out.println("testGroupByClause : XMLDBException: "+e); 
               fail(e.getMessage()); 
        } 
    } 
     
     
    public void testGroupByLetVariable(){ 
        ResourceSet result; 
        String query; 
        try { 
            XPathQueryService service =  
                storeXMLStringAndGetQueryService(BINARYTABLE_XML, binaryTable); 
             
            //group by a let variable 
            System.out.println("testGroupBy 4: ========" ); 
            query = "for $item in //item let $k1 := $item/key1 group $item as "+ 
                    "$partition by $k1 as $key1 return count($partition)"; 
            result = service.queryResource(BINARYTABLE_XML, query ); 
            printResult(result); 
            assertEquals( "XQuery: " + query, 2, result.getSize() ); 
        } 
        catch (Exception e) { 
               System.out.println("testGroupByClause : XMLDBException: "+e); 
               fail(e.getMessage()); 
        } 
    } 
     
    public void testGroupBySpecialFLWR(){ 
        ResourceSet result; 
        String query; 
        try { 
            XPathQueryService service =  
                storeXMLStringAndGetQueryService(BINARYTABLE_XML, binaryTable); 
             
            //group by in a flwr beginning by a let clause 
            System.out.println("testGroupBy 5: ========" ); 
            query = "let $test := //item/key1 let $brol := //item/key2 "+ 
                    "for $item in //item let $k2 := $item/key2 group $item "+ 
                    "as $partition by $item/key1 as $key1 return count($partition)"; 
            result = service.queryResource(BINARYTABLE_XML, query ); 
            printResult(result); 
            assertEquals( "XQuery: " + query, 2, result.getSize() ); 
         
        } 
        catch (Exception e) { 
               System.out.println("testGroupByClause : XMLDBException: "+e); 
               fail(e.getMessage()); 
        } 
    } 
     
    public void testGroupByGroupedVariable(){ 
        ResourceSet result; 
        String query; 
        try { 
            XPathQueryService service =  
                storeXMLStringAndGetQueryService(BINARYTABLE_XML, binaryTable); 
             
            //test the contents of $partition 
            System.out.println("testGroupBy 6: ========" ); 
            query = "for $item in //item group $item as $partition by $item/key1 "+  
                    "as $key1, $item/key2 as $key2 order by $key1 descending, "+
                    "$key2 descending return <group>{$partition}</group>"; 
            result = service.queryResource(BINARYTABLE_XML, query ); 
            printResult(result); 
             
            assertEquals("XQuery: " + query,  
                    "<group>\n"+ 
                    "    <item>\n"+ 
                    "        <key1>1</key1>\n"+ 
                    "        <key2>1</key2>\n"+ 
                    "    </item>\n"+ 
                    "    <item>\n"+ 
                    "        <key1>1</key1>\n"+ 
                    "        <key2>1</key2>\n"+ 
                    "    </item>\n"+ 
                    "</group>",  ((XMLResource)result.getResource(0)).getContent()); 
        } 
        catch (Exception e) { 
               System.out.println("testGroupByClause : XMLDBException: "+e); 
               fail(e.getMessage()); 
        } 
    } 
     
 
     
    /* in a FLWR, variables binded before groupBy clause are not in scope after the groupBy clause*/ 
    public void testScope1(){ 
        ResourceSet result; 
        String query; 
        try { 
            XPathQueryService service =  
                storeXMLStringAndGetQueryService(BINARYTABLE_XML, binaryTable); 
             
            //test the contents of $partition 
            System.out.println("testGroupBy 7: ========" ); 
            query = "for $item in //item group $item as $partition by "+ 
                    "$item/key1 as $key1 return <group>{$item}</group>"; 
            result = service.queryResource(BINARYTABLE_XML, query ); 
            printResult(result); 
             
            fail("$item variable still in scope !"); 
             
             
        } 
        catch (Exception e) { 
            //ok, $item is not in scope 
        }             
         
    } 
     
    public void testScope2(){ 
        ResourceSet result; 
        String query; 
        try { 
            XPathQueryService service =  
                storeXMLStringAndGetQueryService(BINARYTABLE_XML, binaryTable); 
             
            //test the contents of $partition 
            System.out.println("testGroupBy 8: ========" ); 
            query = "for $item in //item group $item as $partition by $item/key1 "+ 
                    "as $key1 return for $foo in $partition return "+ 
                    "<test>{$foo,$key1}</test>"; 
            result = service.queryResource(BINARYTABLE_XML, query ); 
            printResult(result); 
            assertEquals( "XQuery: " + query, 8, result.getSize() ); 
             
             
        } 
        catch (Exception e) { 
               System.out.println("testGroupByClause : XMLDBException: "+e); 
               fail(e.getMessage()); 
        }             
    } 
     
    public void testScope3(){ 
        ResourceSet result; 
        String query; 
        try { 
            XPathQueryService service =  
                storeXMLStringAndGetQueryService(BINARYTABLE_XML, binaryTable); 
             
            //test the contents of $partition 
            System.out.println("testGroupBy 7: ========" ); 
            query = "for $item in //item return for $key in $item/key1 group "+ 
                    "$key as $partition by $item/key2 as $key2 return "+  
                    "<test>{$partition,$item}</test>"; 
            result = service.queryResource(BINARYTABLE_XML, query ); 
            printResult(result); 
            assertEquals( "XQuery: " + query, 8, result.getSize() ); 
             
        } 
        catch (Exception e) { 
               System.out.println("testGroupByClause : XMLDBException: "+e); 
               fail(e.getMessage()); 
        }             
    } 
     
    //test based on Kevin Beyer's publication "Extending XQuery for Analytics", Q11 
    //this test use a recurcive function and group books by all combination of categories. 
    public void testGroupByBeyerQ11(){ 
        ResourceSet result; 
        String query; 
        try { 
            XPathQueryService service =  
                storeXMLStringAndGetQueryService(BEYER_XML, beyer); 
             
 
            System.out.println("testGroupBy Beyer Q11: ========" ); 
            query = "declare function local:paths($x as element()*) as xs:string* {\n"+ 
                  "for $i in $x\n"+ 
                  "let $name := fn:local-name-from-QName(fn:node-name($i))\n"+ 
                  "return ($name,\n"+ 
                  "  for $j in local:paths($i/*)\n"+ 
                  "        return fn:concat($name, \"/\", $j)\n"+ 
                  ")};\n"+ 
                  "for $b in //book\n"+ 
                  "for $c in local:paths($b/categories/*)\n"+ 
                  "group $b as $partition by $c as $category\n"+ 
                  "return\n"+ 
                  "<result><category>{$category}</category> "+ 
                  "<avg-price>{avg($partition/price)}</avg-price></result>\n"; 
                 
                 
                 
            result = service.queryResource(BEYER_XML, query ); 
            printResult(result); 
            assertEquals( "XQuery: " + query, 5, result.getSize() ); 
        } 
        catch (Exception e) { 
               System.out.println("testGroupByClause : XMLDBException: "+e); 
               fail(e.getMessage()); 
        } 
    }     
     
    //test based on Kevin Beyer's publication "Extending XQuery for Analytics", Q12 
    public void testGroupByBeyerQ12(){ 
        ResourceSet result; 
        String query; 
        try { 
            XPathQueryService service =  
                storeXMLStringAndGetQueryService(BEYER_XML, beyer); 
 
            System.out.println("testGroupBy Beyer Q12: ========" ); 
            query = "declare function local:cube($dims as item()*) as item()*\n"+ 
            "{\n"+ 
            "    if (fn:empty($dims)) then <group/>\n"+ 
            "    else for $subgroup in local:cube(fn:subsequence($dims, 2))\n"+ 
            "    return ($subgroup, <group>{$dims[1], $subgroup/*}</group>)\n"+ 
            "};\n"+ 
            "for $b in //book\n"+ 
            "let $pub := <publisher>{$b/publisher}</publisher>\n"+ 
            "for $cell in local:cube(($pub,$b/year))\n"+ 
            "group $b as $partition by $cell as $cell2\n"+
            "order by $cell2 \n"+
            "return\n"+  
            "<result>\n"+ 
            "  {$cell2}\n"+ 
            "  <avg-price>{avg($partition//price)}</avg-price>\n"+ 
            "</result>\n"; 
                 
            result = service.queryResource(BEYER_XML, query ); 
             
            printResult(result); 
            assertEquals( "XQuery: " + query, 6, result.getSize() ); 
            assertEquals("XQuery: " + query, "<result>\n" + 
                    "    <group/>\n" + 
                    "    <avg-price>62</avg-price>\n" + 
                    "</result>", ((XMLResource)result.getResource(0)).getContent() );         
            assertEquals("XQuery: " + query,              
                    "<result>\n" + 
                    "    <group>\n" + 
                    "        <publisher>\n" + 
                    "            <publisher>Morgan Kaufmann</publisher>\n" + 
                    "        </publisher>\n" + 
                    "        <year>1998</year>\n" + 
                    "    </group>\n" + 
                    "    <avg-price>65</avg-price>\n" + 
                    "</result>" 
                    , ((XMLResource)result.getResource(5)).getContent() );         
        } 
        catch (Exception e) { 
               System.out.println("testGroupByClause : XMLDBException: "+e); 
               fail(e.getMessage()); 
        } 
    }     
    
    public void testHashKey(){ 
        ResourceSet result; 
        String query; 
        try { 
            XPathQueryService service =  
                storeXMLStringAndGetQueryService(ITEMS_XML, items); 
             
            //test if they are two group (11,1) and (1,11) and not only one
            //bug corrected with the patch 1681499 on subversion tracker
            System.out.println("testGroupBy hashkey: ========" ); 
            query = "for $item in //item group $item as $partition by $item/key1/text() "+ 
                    "as $key1, $item/key2/text() as $key2" +
                    " return <group/>" ;
            result = service.queryResource(ITEMS_XML, query ); 
            printResult(result); 
            assertEquals( "XQuery: " + query, 2, result.getSize() ); 
             
             
        } 
        catch (Exception e) { 
               System.out.println("testGroupByClause : XMLDBException: "+e); 
               fail(e.getMessage()); 
        }             
    } 
    
    
     
    protected XPathQueryService storeXMLStringAndGetQueryService(String documentName, 
            String content) throws XMLDBException { 
        XMLResource doc = 
            (XMLResource) testCollection.createResource( 
                    documentName, "XMLResource" ); 
        doc.setContent(content); 
        testCollection.storeResource(doc); 
        XPathQueryService service = 
            (XPathQueryService) testCollection.getService( 
                "XPathQueryService", 
                "1.0"); 
        return service; 
    }     
     
    protected XPathQueryService storeXMLStringAndGetQueryService(String documentName 
            ) throws XMLDBException { 
        XMLResource doc = 
            (XMLResource) testCollection.createResource( 
                    documentName, "XMLResource" ); 
        doc.setContent(new File(documentName)); 
        testCollection.storeResource(doc); 
        XPathQueryService service = 
            (XPathQueryService) testCollection.getService( 
                "XPathQueryService", 
                "1.0"); 
        return service; 
    } 
 
    protected void printResult(ResourceSet result) throws XMLDBException { 
        for (ResourceIterator i = result.getIterator(); 
            i.hasMoreResources(); ) { 
            Resource r = i.nextResource(); 
            System.out.println(r.getContent()); 
        } 
    } 
     
} 
