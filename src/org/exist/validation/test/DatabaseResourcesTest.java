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

package org.exist.validation.test;

import java.io.File;
import java.io.FileInputStream;
import java.net.URISyntaxException;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.validation.ValidationReport;
import org.exist.validation.Validator;
import org.exist.validation.internal.DatabaseResources;
import org.exist.xmldb.XmldbURI;


/**
 *  "DatabaseResources.class "jUnit tests.
 *
 * @author dizzzz
 */
public class DatabaseResourcesTest extends TestCase {
    
    private final static String ABOOKFILES="samples/validation/addressbook";
    private final static String DTDFILES="samples/validation/dtd";
    
    private final static String DBGRAMMARS="/db/grammar";
    
    private DatabaseTools dt = null;
    private String eXistHome = null;
    private BrokerPool pool = null;
    private Validator validator = null;
    private DatabaseResources dbResources = null;
    
    private XmldbURI baseURI = null;
    
    public DatabaseResourcesTest(String testName) {
        super(testName);
    }
    
    protected void setUp() {
        System.out.println(">>> setUp");
        
        if (eXistHome == null) {
            eXistHome = Configuration.getExistHome().getAbsolutePath();
        }
        
        if(pool==null){
            pool = startDB();
        }
        
        if(validator==null){
            validator = new Validator(pool);
        }
        
        if(dbResources==null){
            dbResources = validator.getDatabaseResources();
        }
        
        if(dt==null){
            dt = new DatabaseTools(pool);
        }
        
        try {
            baseURI = new XmldbURI("xmldb:exist:///db");
        } catch (URISyntaxException ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        
        System.out.println("<<<\n");
    }
    
    
    public static Test suite() {
        TestSuite suite = new TestSuite(DatabaseResourcesTest.class);
        return suite;
    }
    
    protected BrokerPool startDB() {
        try {
            Configuration config = new Configuration();
            BrokerPool.configure(1, 5, config);
            return BrokerPool.getInstance();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        return null;
    }
    
    protected void tearDown() {
        System.out.println(">>> tearDown");
        // TODO why o why, tell me why to leave this one out
        //BrokerPool.stopAll(false);
        System.out.println("<<<\n");
    }
    
    
    public void testInsertXsdGrammar() {
        
        System.out.println(">>> testInsertGrammar");
        
        File schema = new File(eXistHome , ABOOKFILES+"/addressbook.xsd");
        byte grammar[] = dt.readFile(schema);
        assertTrue( dbResources.insertGrammar(false, DBGRAMMARS+"/schemas/addressbook.xsd",grammar) );
        
        System.out.println("<<<");
    }
    
    public void testInsertDtdGrammar() {
        
        System.out.println(">>> testInsertDtdGrammar");
        
        File dtd = new File(eXistHome , DTDFILES+"/play.dtd");
        byte grammar[] = dt.readFile(dtd);
        assertTrue( dbResources.insertGrammar(true, DBGRAMMARS+"/dtds/play.dtd",grammar) );
        
        File catalog = new File(eXistHome , DTDFILES+"/catalog.xml");
        grammar = dt.readFile(catalog);
        
        assertTrue( dbResources.insertGrammar(false, DBGRAMMARS+"/dtds/catalog.xml",grammar)  );
        
        System.out.println("<<<");
    }
    
    
    
    
    public void testInsertTestDocuments() {
        
        System.out.println(">>> testInsertTestDocuments");
        
        File file = new File(eXistHome , ABOOKFILES+"/addressbook_valid.xml");
        byte data[] = dt.readFile(file);
        assertTrue( dbResources.insertGrammar(false, DBGRAMMARS+"/addressbook_valid.xml",data) );
        
        file = new File(eXistHome , ABOOKFILES+"/addressbook_invalid.xml");
        data = dt.readFile(file);
        assertTrue( dbResources.insertGrammar(false, DBGRAMMARS+"/addressbook_invalid.xml",data) );
        
        file = new File(eXistHome , DTDFILES+"/hamlet_valid.xml");
        data = dt.readFile(file);
        assertTrue( dbResources.insertGrammar(false, DBGRAMMARS+"/hamlet_valid.xml",data) );

        file = new File(eXistHome , DTDFILES+"/hamlet_invalid.xml");
        data = dt.readFile(file);
        assertTrue( dbResources.insertGrammar(false, DBGRAMMARS+"/hamlet_invalid.xml",data) ); 
        
        file = new File(eXistHome , DTDFILES+"/hamlet_nodoctype.xml");
        data = dt.readFile(file);
        assertTrue( dbResources.insertGrammar(false, DBGRAMMARS+"/hamlet_nodoctype.xml",data) );  
        
        file = new File(eXistHome , DTDFILES+"/hamlet_wrongdoctype.xml");
        data = dt.readFile(file);
        assertTrue( dbResources.insertGrammar(false, DBGRAMMARS+"/hamlet_wrongdoctype.xml",data) );  
        
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
            
            ValidationReport report = validator.validate(
                    new FileInputStream(ABOOKFILES +"/addressbook_valid.xml") );
            
            assertTrue( report.isValid() );
            
            System.out.println(report.toString() );
            
            System.out.println("<<<");
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
    
    public void testXsdInvalidDocument() {
        try {
            System.out.println(">>> testXsdInvalidDocument");
            
            ValidationReport report = validator.validate(
                    new FileInputStream(ABOOKFILES +"/addressbook_invalid.xml") );
            
            assertFalse( report.isValid() );
            
            System.out.println(report.toString());
            
            System.out.println("<<<");
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
    
    public void testDtdValidDocument() {
        try {
            System.out.println(">>> testDtdValidDocument");
            
            ValidationReport report = validator.validate(
                    new FileInputStream(DTDFILES +"/hamlet_valid.xml"),
                    "/db/grammar/dtds/catalog.xml");

            assertTrue( report.isValid() );
            
            System.out.println(report.toString());
            
            System.out.println("<<<");
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
    
    public void testDtdInvalidDocument() {
        try {
            System.out.println(">>> testDtdInvalidDocument");
            
            ValidationReport report = validator.validate(
                    new FileInputStream(DTDFILES +"/hamlet_invalid.xml") );
            
            assertFalse( report.isValid() );
            
            System.out.println(report.toString());
            
            System.out.println("<<<");
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
    
}
