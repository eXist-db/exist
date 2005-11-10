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

import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.validation.ValidationReport;
import org.exist.validation.Validator;
import org.exist.validation.internal.DatabaseResources;


/**
 *  "DatabaseResources.class "jUnit tests.
 *
 * @author dizzzz
 */
public class DatabaseResourcesTest extends TestCase {
    
    private final static String ABOOKFILES="samples/validation/addressbook";
    private final static String DTDFILES="samples/validation/dtd";
    
    private String eXistHome = null;
    private BrokerPool pool = null;
    private Validator validator = null;
    private DatabaseResources dbResources = null;
    
    
    public DatabaseResourcesTest(String testName) {
        super(testName);
    }
    
    protected void setUp() {
        System.out.println(">>> setUp");
        
        if(eXistHome==null){
            eXistHome = System.getProperty("exist.home");
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
        
        System.out.println("<<<\n");
    }
    
    
    public static Test suite() {
        TestSuite suite = new TestSuite(DatabaseResourcesTest.class);
        return suite;
    }
    
    protected BrokerPool startDB() {
        String home, file = "conf.xml";
        home = System.getProperty("exist.home");
        if (home == null)
            home = System.getProperty("user.dir");
        try {
            Configuration config = new Configuration(file, home);
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
        
        assertTrue( dbResources.insertSchema( new File(eXistHome , ABOOKFILES+"/addressbook.xsd") ,
                           "addressbook.xsd") );
        
        System.out.println("<<<");
    }

    public void testInsertXsdGrammar2() {
        
        System.out.println(">>> testInsertGrammar");
        
        assertTrue( dbResources.insertSchema( new File(eXistHome , ABOOKFILES+"/addressbook.xsd") ,
                           "/other/path/addressbook.xsd") );
        
        assertTrue( dbResources.insertSchema( new File(eXistHome , ABOOKFILES+"/addressbook.xsd") ,
                           "another/path/addressbook.xsd") );
        
        System.out.println("<<<");
    }
        
    public void testInsertDtdGrammar() {
        
        System.out.println(">>> testInsertDtdGrammar");
        
        assertTrue( dbResources.insertDtd( new File(eXistHome , DTDFILES+"/play.dtd") ,
                           "play.dtd") );
        
        assertTrue(
                dbResources.insertCatalog( new File(eXistHome , DTDFILES+"/catalog.xml") )
                );
        
        System.out.println("<<<");
    }
    
    public void testInsertDtdGrammar2() {
        
        System.out.println(">>> testInsertDtdGrammar2");
        
        assertTrue( dbResources.insertDtd( new File(eXistHome , DTDFILES+"/play.dtd") ,
                           "/other/path/play.dtd") );
        
        assertTrue( dbResources.insertDtd( new File(eXistHome , DTDFILES+"/play.dtd") ,
                           "anothother/path/play.dtd") );
        
        System.out.println("<<<");
    }


    
    public void testInsertTestDocuments() {
        
        System.out.println(">>> testInsertTestDocuments");
        
        assertTrue(
                dbResources.insertDocument( new File(eXistHome , ABOOKFILES+"/addressbook_valid.xml") ,
                false, DBBroker.ROOT_COLLECTION, "addressbook_valid.xml") );
        
        assertTrue(
                dbResources.insertDocument( new File(eXistHome , ABOOKFILES+"/addressbook_invalid.xml") ,
                false, DBBroker.ROOT_COLLECTION, "addressbook_invalid.xml") );

        assertTrue(
                dbResources.insertDocument( new File(eXistHome , DTDFILES+"/hamlet_valid.xml") ,
                false, DBBroker.ROOT_COLLECTION, "hamlet_valid.xml") );
        
        assertTrue(
                dbResources.insertDocument( new File(eXistHome , DTDFILES+"/hamlet_invalid.xml") ,
                false, DBBroker.ROOT_COLLECTION, "hamlet_invalid.xml") );

        
        System.out.println("<<<");
    }   
    
    public void testIsGrammarInDatabase() {
        System.out.println(">>> testIsGrammarInDatabase");
        
        assertTrue( dbResources.hasGrammar( DatabaseResources.GRAMMAR_XSD,
                "http://jmvanel.free.fr/xsd/addressBook" ) );
        System.out.println("<<<");
    }   
    
    public void testIsGrammarNotInDatabase() {
        System.out.println(">>> testIsGrammarNotInDatabase");
        
        assertFalse( dbResources.hasGrammar( DatabaseResources.GRAMMAR_XSD,
                "http://jmvanel.free.fr/xsd/addressBooky" ) );
        
        System.out.println("<<<");
    }
    
    public void testXsdValidDocument() {
        try {
        	System.out.println(">>> testXsdValidDocument");        
        
	        ValidationReport report = validator.validate(
	                new FileInputStream(ABOOKFILES +"/addressbook_valid.xml") );
	        
	        assertFalse( report.hasErrorsAndWarnings() );
	        
	        System.out.println(report.getErrorReport());
	        System.out.println(report.getWarningReport());
	        
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
	        
	        assertTrue( report.hasErrorsAndWarnings() );
	        
	        System.out.println(report.getErrorReport());
	        System.out.println(report.getWarningReport());
	        
	        System.out.println("<<<");
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }
    }
    
    public void testDtdValidDocument() {
        try {
	    	System.out.println(">>> testDtdValidDocument");
	        
	        ValidationReport report = validator.validate(
	                new FileInputStream(DTDFILES +"/hamlet_valid.xml") );
	        
	        assertFalse( report.hasErrorsAndWarnings() );
	        
	        System.out.println(report.getErrorReport());
	        System.out.println(report.getWarningReport());
	        
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
	        
	        assertTrue( report.hasErrorsAndWarnings() );
	        
	        System.out.println(report.getErrorReport());
	        System.out.println(report.getWarningReport());
	        
	        System.out.println("<<<");
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }
    }    
    
}
