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
    
    protected void setUp() throws Exception {
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
    
    protected BrokerPool startDB() throws Exception {
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
    
    protected void tearDown() throws Exception {
        System.out.println(">>> tearDown");
        // TODO why o why, tell me why to leave this one out
        //BrokerPool.stopAll(false);
        System.out.println("<<<\n");
    }
    
    
    public void testInsertXsdGrammar() throws Exception {
        
        System.out.println(">>> testInsertGrammar");
        
        Assert.assertTrue( dbResources.insertGrammar( new File(eXistHome , ABOOKFILES+"/addressbook.xsd") ,
                DatabaseResources.GRAMMAR_XSD,
                "addressbook.xsd") );
        
        System.out.println("<<<");
    }


        
    public void bugTestInsertDtdGrammar() throws Exception {
        
        System.out.println(">>> testInsertDtdGrammar");
        
        Assert.assertTrue( dbResources.insertGrammar( new File(eXistHome , DTDFILES+"/play.dtd") ,
                DatabaseResources.GRAMMAR_DTD,
                "play.dtd") );
        
        Assert.assertTrue(
                dbResources.insertDocumentInDatabase( new File(eXistHome , DTDFILES+"/catalog.xml") ,
                "/db/system/grammar/dtd",
                "catalog.xml") );
        
        System.out.println("<<<");
    }


    
    public void testInsertTestDocuments() throws Exception {
        
        System.out.println(">>> testInsertTestDocuments");
        
        Assert.assertTrue(
                dbResources.insertDocumentInDatabase( new File(eXistHome , ABOOKFILES+"/addressbook_valid.xml") ,
                "/db",
                "addressbook_valid.xml") );
        
        Assert.assertTrue(
                dbResources.insertDocumentInDatabase( new File(eXistHome , ABOOKFILES+"/addressbook_invalid.xml") ,
                "/db",
                "addressbook_invalid.xml") );

        Assert.assertTrue(
                dbResources.insertDocumentInDatabase( new File(eXistHome , DTDFILES+"/hamlet_valid.xml") ,
                "/db",
                "hamlet_valid.xml") );
        
        Assert.assertTrue(
                dbResources.insertDocumentInDatabase( new File(eXistHome , DTDFILES+"/hamlet_invalid.xml") ,
                "/db",
                "hamlet_invalid.xml") );

        
        System.out.println("<<<");
    }
    
    
    
    public void testIsGrammarInDatabase() throws Exception {
        System.out.println(">>> testIsGrammarInDatabase");
        
        Assert.assertTrue( dbResources.hasGrammar( DatabaseResources.GRAMMAR_XSD,
                "http://jmvanel.free.fr/xsd/addressBook" ) );
        System.out.println("<<<");
    }
    
    
    public void testIsGrammarNotInDatabase() throws Exception {
        System.out.println(">>> testIsGrammarNotInDatabase");
        
        Assert.assertFalse( dbResources.hasGrammar( DatabaseResources.GRAMMAR_XSD,
                "http://jmvanel.free.fr/xsd/addressBooky" ) );
        
        System.out.println("<<<");
    }
    
    public void testValidDocument() throws Exception {
        System.out.println(">>> testValidDocument");
        
        ValidationReport report = validator.validate(
                new FileInputStream(ABOOKFILES +"/addressbook_valid.xml") );
        
        Assert.assertFalse( report.hasErrorsAndWarnings() );
        
        System.out.println("<<<");
    }
    
    public void testInvalidDocument() throws Exception {
        System.out.println(">>> testValidDocument");
        
        ValidationReport report = validator.validate(
                new FileInputStream(ABOOKFILES +"/addressbook_invalid.xml") );
        
        Assert.assertTrue( report.hasErrorsAndWarnings() );
        
        System.out.println("<<<");
    }
    
    
}
