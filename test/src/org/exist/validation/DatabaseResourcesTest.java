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

package org.exist.validation;

import java.io.File;
import java.io.FileInputStream;
import java.net.URISyntaxException;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.validation.internal.DatabaseResources;
import org.exist.xmldb.XmldbURI;


/**
 *  "DatabaseResources.class" jUnit tests.
 *
 * @author dizzzz
 */
public class DatabaseResourcesTest extends TestCase {
    
    private final static String ABOOKFILES="samples/validation/addressbook";
    private final static String DTDFILES="samples/validation/dtd";
    
    private final static String DBGRAMMARS="/db/grammar";
    
    private static DatabaseTools dt = null;
    private static String eXistHome = null;
    private static BrokerPool pool = null;
    private static Validator validator = null;
    private static DatabaseResources dbResources = null;
    
    private static XmldbURI baseURI = null;
    
    public DatabaseResourcesTest(String testName) {
        super(testName);
    }
    
    public static Test suite() {
        TestSuite suite = new TestSuite(DatabaseResourcesTest.class);
        return suite;
    }
    
    protected BrokerPool startDB() {
        try {
            Configuration config = new Configuration();
            config.setProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION,"no");
            BrokerPool.configure(1, 5, config);
            return BrokerPool.getInstance();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        return null;
    }
    
    
    // ---------------------------------------------------
    
    
    public void testStart() {
        System.out.println(">>> testStart");
        
        eXistHome = ConfigurationHelper.getExistHome().getAbsolutePath();
        pool = startDB();
        validator = new Validator(pool);
        dbResources = validator.getDatabaseResources();
        dt = new DatabaseTools(pool);
        
        try {
            baseURI = XmldbURI.xmldbUriFor("xmldb:exist:///db");
        } catch (URISyntaxException ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        
        System.out.println("<<<\n");
    }
    
    public void testInsertXsdGrammar() {
        
        System.out.println(">>> testInsertXsdGrammar");
        System.out.println(eXistHome);
        
        File schema = new File(eXistHome , ABOOKFILES+"/addressbook.xsd");
        byte grammar[] = dt.readFile(schema);
        assertTrue( dbResources.insertResource(DBGRAMMARS+"/schemas/addressbook.xsd",grammar) );
        
        File catalog = new File(eXistHome , ABOOKFILES+"/catalog_schema.xml");
        grammar = dt.readFile(catalog);
        assertTrue( dbResources.insertResource(DBGRAMMARS+"/schemas/catalog_schema.xml",grammar) );
        
        
        System.out.println("<<<");
    }
    
    public void testInsertDtdGrammar() {
        
        System.out.println(">>> testInsertDtdGrammar");
        
        File dtd = new File(eXistHome , DTDFILES+"/play.dtd");
        byte grammar[] = dt.readFile(dtd);
        assertTrue( dbResources.insertResource(DBGRAMMARS+"/dtds/play.dtd",grammar) );
        
        File catalog = new File(eXistHome , DTDFILES+"/catalog.xml");
        grammar = dt.readFile(catalog);
        
        assertTrue( dbResources.insertResource(DBGRAMMARS+"/dtds/catalog.xml",grammar)  );
        
        System.out.println("<<<");
    }
    
    public void testInsertTestDocuments() {
        
        System.out.println(">>> testInsertTestDocuments");
        
        File file = new File(eXistHome , ABOOKFILES+"/addressbook_valid.xml");
        byte data[] = dt.readFile(file);
        assertTrue( dbResources.insertResource(DBGRAMMARS+"/addressbook_valid.xml",data) );
        
        file = new File(eXistHome , ABOOKFILES+"/addressbook_invalid.xml");
        data = dt.readFile(file);
        assertTrue( dbResources.insertResource(DBGRAMMARS+"/addressbook_invalid.xml",data) );
        
        file = new File(eXistHome , DTDFILES+"/hamlet_valid.xml");
        data = dt.readFile(file);
        assertTrue( dbResources.insertResource(DBGRAMMARS+"/hamlet_valid.xml",data) );
        
        file = new File(eXistHome , DTDFILES+"/hamlet_invalid.xml");
        data = dt.readFile(file);
        assertTrue( dbResources.insertResource(DBGRAMMARS+"/hamlet_invalid.xml",data) );
        
        file = new File(eXistHome , DTDFILES+"/hamlet_nodoctype.xml");
        data = dt.readFile(file);
        assertTrue( dbResources.insertResource(DBGRAMMARS+"/hamlet_nodoctype.xml",data) );
        
        file = new File(eXistHome , DTDFILES+"/hamlet_wrongdoctype.xml");
        data = dt.readFile(file);
        assertTrue( dbResources.insertResource(DBGRAMMARS+"/hamlet_wrongdoctype.xml",data) );
        
        System.out.println("<<<");
    }
    
    public void testIsGrammarInDatabase() {
        System.out.println(">>> testIsGrammarInDatabase");
        
//        assertTrue( c.hasGrammar( DatabaseResources.GRAMMAR_XSD,
//                "http://jmvanel.free.fr/xsd/addressBook" ) );
        
        assertNotNull( dbResources.getSchemaPath(baseURI,
                "http://jmvanel.free.fr/xsd/addressBook" ));
        
        System.out.println("<<<");
    }
    
    public void testIsGrammarNotInDatabase() {
        System.out.println(">>> testIsGrammarNotInDatabase");
        
        assertNull( dbResources.getSchemaPath(baseURI,
                "http://jmvanel.free.fr/xsd/addressBooky" ));
        
//        assertFalse( dbResources.hasGrammar( DatabaseResources.GRAMMAR_XSD,
//                "http://jmvanel.free.fr/xsd/addressBooky" ) );
        
        System.out.println("<<<");
    }
    
    public void testXsdValidDocument() {
        try {
            System.out.println(">>> testXsdValidDocument");
            
            validator.getGrammarPool().clear();
            
            File file = new File(eXistHome , ABOOKFILES+"/addressbook_valid.xml");
            
            ValidationReport report = validator.validate(
                    new FileInputStream(file) );
            
            System.out.println(report.toString() );
            
            assertTrue( report.isValid() );
            
        } catch (Exception e) {
            fail(e.getMessage());
        } finally {
            System.out.println("<<<");
        }
    }
    
    public void testXsdInvalidDocument() {
        try {
            System.out.println(">>> testXsdInvalidDocument");
            
            validator.getGrammarPool().clear();
            
            File file = new File(eXistHome , ABOOKFILES+"/addressbook_invalid.xml");
            
            ValidationReport report = validator.validate( new FileInputStream(file) );
            
            System.out.println(report.toString());
            
            assertFalse( report.isValid() );
            
        } catch (Exception e) {
            fail(e.getMessage());
        } finally {
            System.out.println("<<<");
        }
    }
    
    public void testDtdValidDocument() {
        try {
            System.out.println(">>> testDtdValidDocument");
            
            validator.getGrammarPool().clear();
            
            File file = new File(eXistHome , DTDFILES+"/hamlet_valid.xml");
            
            ValidationReport report = validator.validate(
                    new FileInputStream(file), "/db/grammar/dtds/catalog.xml");
            
            System.out.println(report.toString());
            
            assertTrue( report.isValid() );
            
        } catch (Exception e) {
            fail(e.getMessage());
        } finally {
            System.out.println("<<<");
        }
    }
    
    public void testDtdInvalidDocument() {
        try {
            System.out.println(">>> testDtdInvalidDocument");
            
            validator.getGrammarPool().clear();
            
            File file = new File(eXistHome , DTDFILES+"/hamlet_invalid.xml");
            
            ValidationReport report = validator.validate( new FileInputStream( file ) );
            
            System.out.println(report.toString());
            assertFalse( report.isValid() );
            
            
        } catch (Exception e) {
            fail(e.getMessage());
        } finally {
            System.out.println("<<<");
        }
    }
    
    public void testCatalogValidXml(){
        try {
            System.out.println(">>> testCatalogValidXml");
            
            validator.getGrammarPool().clear();
            
            File file = new File(eXistHome , ABOOKFILES+"/addressbook_valid.xml");
            
            ValidationReport report = validator.validate(
                    new FileInputStream(file) , DBGRAMMARS +"/schemas/catalog_schema.xml" );
            
            System.out.println(report.toString() );
            
            assertTrue( report.isValid() );
            
        } catch (Exception e) {
            fail(e.getMessage());
        } finally {
            System.out.println("<<<");
        }
    }
    
    public void testCatalogInvalidXml(){
        try {
            System.out.println(">>> testCatalogInvalidXml");
            
            validator.getGrammarPool().clear();
            
            File file = new File(eXistHome , ABOOKFILES+"/addressbook_invalid.xml");
            
            ValidationReport report = validator.validate(
                    new FileInputStream(file) , DBGRAMMARS +"/schemas/catalog_schema.xml" );
            
            System.out.println(report.toString() );
            assertFalse( report.isValid() );
            
            assertFalse( "Error report indicates that grammar or catalog could not be found", 
                    report.toString().indexOf( "Error (2,61) : cvc-elt.1: Cannot find the declaration of element 'addressBook'." )!=-1 );
            
            assertTrue( "Content error report is different then exptected", 
                    report.toString().indexOf( "Error (12,15) : cvc-complex-type.2.4.a: Invalid content was found starting with element 'name'. One of '{\"http://jmvanel.free.fr/xsd/addressBook\":cname}' is expected.")!=-1);
            
        } catch (Exception e) {
            fail(e.getMessage());
        } finally {
            System.out.println("<<<");
        }
    }
    
    public void testShutdown() {
        System.out.println(">>> testShutdown");
        // TODO why o why, tell me why to leave this one out
        //BrokerPool.stopAll(false);
        System.out.println("<<<\n");
    }
    
}
