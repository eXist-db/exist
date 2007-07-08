/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id: DatabaseInsertResources_NoValidation_Test.java 5986 2007-06-03 15:39:39Z dizzzz $
 */
package org.exist.validation;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.log4j.Logger;
import org.exist.storage.BrokerPool;
import org.exist.storage.io.ExistIOException;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.util.XMLReaderObjectFactory;

/**
 *  Insert documents for validation tests.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class DatabaseInsertResources_NoValidation_Test extends TestCase {
    
    private final static Logger logger = Logger.getLogger(DatabaseInsertResources_NoValidation_Test.class);
    
    private static BrokerPool pool;
    private static String eXistHome;
    private static Configuration config;
    
    public DatabaseInsertResources_NoValidation_Test(String testName) {
        super(testName);
    }
    
    public static Test suite() {
        TestSuite suite = new TestSuite(DatabaseInsertResources_NoValidation_Test.class);
        return suite;
    }
    
    protected BrokerPool startDB() {
        try {
            config = new Configuration();
            config.setProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "auto");
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
        System.out.println(this.getName());
        eXistHome = ConfigurationHelper.getExistHome().getAbsolutePath();
        pool = startDB();
    }
    
    

    
    /**
     * Insert all documents into database, switch of validation.
     */
    public void testInsertValidationResources(){
        System.out.println(this.getName());
        
        try{
            config.setProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "no");
            
            String addressbook=eXistHome+"/samples/validation/addressbook";
            
            TestTools.insertDocumentToURL(addressbook+"/addressbook.xsd",
                "xmldb:exist://"+TestTools.VALIDATION_XSD+"/addressbook.xsd");
            TestTools.insertDocumentToURL(addressbook+"/catalog.xml",
                "xmldb:exist://"+TestTools.VALIDATION_XSD+"/catalog.xml");
            
            TestTools.insertDocumentToURL(addressbook+"/addressbook_valid.xml",
                "xmldb:exist://"+TestTools.VALIDATION_HOME+"/addressbook_valid.xml");
            TestTools.insertDocumentToURL(addressbook+"/addressbook_invalid.xml",
                "xmldb:exist://"+TestTools.VALIDATION_HOME+"/addressbook_invalid.xml");
            
            // ----------------------
            
            String hamlet=eXistHome+"/samples/validation/dtd";
            
            TestTools.insertDocumentToURL(hamlet+"/hamlet.dtd",
                "xmldb:exist://"+TestTools.VALIDATION_DTD+"/hamlet.dtd");
            TestTools.insertDocumentToURL(hamlet+"/catalog.xml",
                "xmldb:exist://"+TestTools.VALIDATION_DTD+"/catalog.xml");
            
            TestTools.insertDocumentToURL(hamlet+"/hamlet_valid.xml",
                "xmldb:exist://"+TestTools.VALIDATION_HOME+"/hamlet_valid.xml");
            TestTools.insertDocumentToURL(hamlet+"/hamlet_invalid.xml",
                "xmldb:exist://"+TestTools.VALIDATION_HOME+"/hamlet_invalid.xml");
            
            // ----------------------
            
            TestTools.insertDocumentToURL(hamlet+"/hamlet_nodoctype.xml",
                "xmldb:exist://"+TestTools.VALIDATION_HOME+"/hamlet_nodoctype.xml");
            TestTools.insertDocumentToURL(hamlet+"/hamlet_wrongdoctype.xml",
                "xmldb:exist://"+TestTools.VALIDATION_HOME+"/hamlet_wrongdoctype.xml");
            
        } catch (ExistIOException ex) {
            
            ex.getCause().printStackTrace();
            logger.error(ex.getCause());
            fail(ex.getCause().getMessage());
            
        } catch (Exception ex) {
            ex.printStackTrace();
            logger.error(ex);
            fail(ex.getMessage());
        }
    }
    
    
    public void testShutdown() {
        System.out.println(this.getName());
        BrokerPool.stopAll(true);
        
    }
    
}
