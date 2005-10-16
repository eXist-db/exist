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
import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.exist.validation.service.ValidationService;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;


/**
 *  jUnit test for testing the Validation Service.
 *
 * @author dizzzz
 */
public class ValidationServiceTest  extends TestCase {
    
    private final static String URI = "xmldb:exist:///db";
    private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";
    private Collection rootCollection = null;
    private ValidationService service = null;
    
    private String eXistHome = null;
    
    public ValidationServiceTest(String testName) {
        super(testName);
    }
    
    public static Test suite() {
        TestSuite suite = new TestSuite(ValidationServiceTest.class);
        return suite;
    }
    
    public void setUp() throws Exception {
        System.out.println(">>> setUp");
        eXistHome = System.getProperty("exist.home");
        
        Class cl = Class.forName(DRIVER);
        Database database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
        rootCollection = DatabaseManager.getCollection(URI, "admin", null);
        if (rootCollection == null)
            throw new Exception("Could not connect to database.");
        
        
        service = getValidationService();
        
        System.out.println("<<<\n");
    }
    
    protected void tearDown() throws Exception {
        System.out.println(">>> tearDown");
        System.out.println("<<<\n");
    }
    
    private ValidationService getValidationService() throws XMLDBException {
        return (ValidationService) rootCollection.getService("ValidationService", "1.0");
    }
    
    public void testGetName() throws Exception {
        Assert.assertEquals("ValidationService check",
                service.getName(),  "ValidationService" );
    }
    
    public void testGetVersion() throws XMLDBException {
        Assert.assertEquals("ValidationService check",
                service.getVersion(),   "1.0" );
    }
    
    public void testValidDocument() throws XMLDBException {
        
        Assert.assertTrue( service.validateResource("/db/addressbook_valid.xml") );
    }
    
    public void testInvalidDocument() throws XMLDBException {
        
        Assert.assertFalse( service.validateResource("/db/addressbook_invalid.xml") );
    }
    
    public void testNonexistingDocument() throws XMLDBException {
        
        Assert.assertFalse( service.validateResource("/db/foobar.xml") );
    }
}
