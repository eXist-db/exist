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
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;

/**
 *
 * @author wessels
 */
public class DatabaseResourcesTest extends TestCase {
    
    private String eXistHome = null;
    private BrokerPool pool = null;
    private Validator validator = null;
    private DatabaseResources dbr = null;
    private final static String ABOOKFILES="samples/validation/addressbook";
    
    public DatabaseResourcesTest(String testName) {
        super(testName);
    }
    
    protected void setUp() throws Exception {
        System.out.println(">>> setUp");
        eXistHome = System.getProperty("exist.home");
        
        if(pool==null){
            pool = startDB();
        }
        
        
        if(validator==null){
            validator = new Validator(pool);
        }
        
        if(dbr==null){
            dbr = validator.getDatabaseResources();
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
        // TODO why o why 
        //BrokerPool.stopAll(false);
        System.out.println("<<<\n");
    }
    
    
    public void testInsertGrammar() throws Exception {
        
        System.out.println(">>> testInsertGrammar");
        
//        Validator va = new Validator(pool);
//        DatabaseResources ga = va.getDatabaseResources();
        
        Assert.assertTrue( dbr.insertGrammar( new File(eXistHome , ABOOKFILES+"/addressbook.xsd") , DatabaseResources.GRAMMAR_XSD , "addressbook.xsd") );
        
        System.out.println("<<<");
    }
    
    public void testInsertTestDocuments() throws Exception {
        
        System.out.println(">>> testInsertTestDocuments");
        
//        Validator va = new Validator(pool);
//        DatabaseResources ga = va.getDatabaseResources();
        
        Assert.assertTrue(
                dbr.insertDocumentInDatabase( new File(eXistHome , ABOOKFILES+"/addressbook_valid.xml") ,
                                             "/db",
                                             "addressbook_valid.xml") );
        Assert.assertTrue(
                dbr.insertDocumentInDatabase( new File(eXistHome , ABOOKFILES+"/addressbook_invalid.xml") , 
                                             "/db", 
                                             "addressbook_invalid.xml") );
        
        System.out.println("<<<");
    }
    
    
    
    public void testIsGrammarInDatabase() throws Exception{
        System.out.println(">>> testIsGrammarInDatabase");
        
//        Validator va = new Validator(pool);
//        DatabaseResources ga = va.getDatabaseResources();
        
        Assert.assertTrue( dbr.hasGrammar( DatabaseResources.GRAMMAR_XSD, "http://jmvanel.free.fr/xsd/addressBook" ) );
        System.out.println("<<<");
    }
    
    
    public void testIsGrammarNotInDatabase() throws Exception{
        System.out.println(">>> testIsGrammarNotInDatabase");
//        Validator va = new Validator(pool);
//        DatabaseResources ga = va.getDatabaseResources();
        Assert.assertFalse( dbr.hasGrammar( DatabaseResources.GRAMMAR_XSD, "http://jmvanel.free.fr/xsd/addressBooky" ) );
        System.out.println("<<<");
    }
    
}
