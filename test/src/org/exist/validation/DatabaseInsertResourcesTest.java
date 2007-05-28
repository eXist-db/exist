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
 * $Id$
 */
package org.exist.validation;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.commons.io.output.ByteArrayOutputStream;

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
public class DatabaseInsertResourcesTest extends TestCase {
    
    private final static Logger logger = Logger.getLogger(DatabaseInsertResourcesTest.class);
    
    private static BrokerPool pool;
    private static String eXistHome;
    private static Configuration config;
    
    public DatabaseInsertResourcesTest(String testName) {
        super(testName);
    }
    
    public static Test suite() {
        TestSuite suite = new TestSuite(DatabaseInsertResourcesTest.class);
        return suite;
    }
    
    protected BrokerPool startDB() {
        try {
            config = new Configuration();
            config.setProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION, "auto");
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
     * Test for inserting hamlet.xml, while validating using default registered
     * DTD set in system catalog.
     *
     * First the string
     *     <!--!DOCTYPE PLAY PUBLIC "-//PLAY//EN" "play.dtd"-->
     * needs to be modified into
     *     <!DOCTYPE PLAY PUBLIC "-//PLAY//EN" "play.dtd">
     */
    public void testValidDocumentSystemCatalog(){
        
        System.out.println(this.getName());
        try {
            File file = new File(eXistHome, "samples/shakespeare/hamlet.xml");
            InputStream fis = new FileInputStream(file);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            TestTools.copyStream(fis, baos);
            fis.close();
            
            String sb = new String(baos.toByteArray());
            sb=sb.replaceAll("\\Q<!\\E.*DOCTYPE.*\\Q-->\\E",
                "<!DOCTYPE PLAY PUBLIC \"-//PLAY//EN\" \"play.dtd\">" );
            InputStream is = new ByteArrayInputStream(sb.getBytes());
            
            // -----
            
            URL url = new URL("xmldb:exist://"+TestTools.VALIDATION_TMP+"/hamlet_valid.xml");
            URLConnection connection = url.openConnection();
            OutputStream os = connection.getOutputStream();
            
            TestTools.copyStream(is, os);
            
            is.close();
            os.close();
            
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
    
    /**
     * Test for inserting hamlet.xml, while validating using default registered
     * DTD set in system catalog.
     *
     * First the string
     *     <!--!DOCTYPE PLAY PUBLIC "-//PLAY//EN" "play.dtd"-->
     * needs to be modified into
     *     <!DOCTYPE PLAY PUBLIC "-//PLAY//EN" "play.dtd">
     *
     * Aditionally all "TITLE" elements are renamed to "INVALIDTITLE"
     */
    public void testInvalidDocumentSystemCatalog(){
        System.out.println(this.getName());
        try {
            File file = new File(eXistHome, "samples/shakespeare/hamlet.xml");
            InputStream fis = new FileInputStream(file);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            TestTools.copyStream(fis, baos);
            fis.close();
            
            String sb = new String(baos.toByteArray());
            sb=sb.replaceAll("\\Q<!\\E.*DOCTYPE.*\\Q-->\\E",
                "<!DOCTYPE PLAY PUBLIC \"-//PLAY//EN\" \"play.dtd\">" );
            
            sb=sb.replaceAll("TITLE", "INVALIDTITLE" );
            
            InputStream is = new ByteArrayInputStream(sb.getBytes());
            
            // -----
            
            URL url = new URL("xmldb:exist://"+TestTools.VALIDATION_TMP+"/hamlet_valid.xml");
            URLConnection connection = url.openConnection();
            OutputStream os = connection.getOutputStream();
            
            TestTools.copyStream(is, os);
            
            is.close();
            os.close();
            
        } catch (ExistIOException ex) {
            
            if(!ex.getCause().getMessage().matches(".*Element type \"INVALIDTITLE\" must be declared.*")){
                ex.getCause().printStackTrace();
                logger.error(ex.getCause());
                fail(ex.getCause().getMessage());
            }
            
        } catch (Exception ex) {
            ex.printStackTrace();
            logger.error(ex);
            fail(ex.getMessage());
        }
    }
    
    /**
     * Insert all documents into database, switch of validation.
     */
    public void testValidationResources(){
        System.out.println(this.getName());
        
        try{
            config.setProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION, "no");
            
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
