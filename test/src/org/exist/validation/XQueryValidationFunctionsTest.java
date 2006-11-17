/*
 * Created on 17.03.2005 - $Id: XQueryValidationFunctionsTest.java 3080 2006-04-07 22:17:14Z dizzzz $
 */
package org.exist.validation;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xquery.XPathException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XPathQueryService;

public class XQueryValidationFunctionsTest extends TestCase {
    
    // TODO figure out in more details
    // The grammars seem to be cached by xerces too good,
    // this makes the validation tests 'not pure
    
    private static XPathQueryService service;
    private static Collection root = null;
    private static Database database = null;
    
    public static void main(String[] args) throws XPathException {
        TestRunner.run(XQueryValidationFunctionsTest.class);
    }
    
    /**
     * Constructor for XQueryValidationFunctionsTest.
     *
     * @param arg0
     */
    public XQueryValidationFunctionsTest(String arg0) {
        super(arg0);
    }
    
    
    public void testValidateValidXmlWithXsd() throws XPathException {
        
        System.out.println("testValidateValidXmlWithXsd");
        
        ResourceSet result = null;
        String r = "";
        try {
            result = service.query("validation:validate("
                   + "'/db/grammar/addressbook_valid.xml',"
                   + "'/db/grammar/schemas/addressbook.xsd')");
            r = (String) result.getResource(0).getContent();
            assertEquals( "true", r );

        } catch (XMLDBException e) {
            System.out.println("testGrammars(): " + e);
            fail(e.getMessage());
        }
        
        System.out.println();
    }
    
    public void testValidateInValidXmlWithXsd() throws XPathException {
        
        System.out.println("testValidateInValidXmlWithXsd");
        
        ResourceSet result = null;
        String r = "";
        try {
            result = service.query("validation:validate("
                   + "'/db/grammar/addressbook_invalid.xml',"
                   + "'/db/grammar/schemas/addressbook.xsd')");
            r = (String) result.getResource(0).getContent();
            assertEquals( "false", r );

        } catch (XMLDBException e) {
            System.out.println("testGrammars(): " + e);
            fail(e.getMessage());
        }
        
        System.out.println();
    }
    
    public void testValidateValidXmlWithXsdInCatalog() throws XPathException {
        
        System.out.println("testValidateValidXmlWithXsdInCatalog");
        
        ResourceSet result = null;
        String r = "";
        try {
            result = service.query("validation:validate("
                   + "'/db/grammar/addressbook_valid.xml',"
                   + "'/db/grammar/schemas/catalog_schema.xml')");
            r = (String) result.getResource(0).getContent();
            assertEquals( "true", r );

        } catch (XMLDBException e) {
            System.out.println("testGrammars(): " + e);
            fail(e.getMessage());
        }
        
        System.out.println();
    }
    
    public void testValidateInvalidXmlWithXsdInCatalog() throws XPathException {
        
        System.out.println("testValidateInvalidXmlWithXsdInCatalog");
        
        ResourceSet result = null;
        String r = "";
        try {
            result = service.query("validation:validate("
                   + "'/db/grammar/addressbook_invalid.xml',"
                   + "'/db/grammar/schemas/catalog_schema.xml')");
            r = (String) result.getResource(0).getContent();
            assertEquals( "false", r );

        } catch (XMLDBException e) {
            System.out.println("testGrammars(): " + e);
            fail(e.getMessage());
        }
        
        System.out.println();
    }
    
    // Need to be tested first, otherwise grammer is cached.
    // TODO don't understand why this is not working.
    public void bugtestValidateValidXmlSearchGrammar1() throws XPathException {
        
        System.out.println("testValidateValidXmlSearchGrammar1");
        
        ResourceSet result = null;
        String r = "";
        try {
            //service.query("validation:grammar-cache-clear()");
            result = service.query("validation:validate("
                   + "'/db/grammar/addressbook_valid.xml',"
                   + "'/db/system/')");
            r = (String) result.getResource(0).getContent();
            assertEquals( "false", r );

        } catch (XMLDBException e) {
            System.out.println("testGrammars(): " + e);
            fail(e.getMessage());
        }
        
        System.out.println();
    }
    
    public void testValidateValidXmlSearchGrammar2() throws XPathException {
        
        System.out.println("testValidateValidXmlSearchGrammar1");
        
        ResourceSet result = null;
        String r = "";
        try {
            result = service.query("validation:validate("
                   + "'/db/grammar/addressbook_valid.xml',"
                   + "'/db/')");
            r = (String) result.getResource(0).getContent();
            assertEquals( "true", r );

        } catch (XMLDBException e) {
            System.out.println("testGrammars(): " + e);
            fail(e.getMessage());
        }
    }
    
    protected void setUp() throws Exception {
        // initialize driver
        System.out.println();
        
        Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
        root = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);
        service = (XPathQueryService) root.getService( "XQueryService", "1.0" );
    }
    
    protected void tearDown() throws Exception {
        
        System.out.println();
        
        DatabaseManager.deregisterDatabase(database);
        DatabaseInstanceManager dim =
                (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
        dim.shutdown();
        //System.out.println("tearDown PASSED");
    }
    
}
