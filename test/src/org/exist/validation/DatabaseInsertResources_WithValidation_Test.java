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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import org.exist.external.org.apache.commons.io.output.ByteArrayOutputStream;

import org.apache.log4j.Logger;

import org.exist.collections.Collection;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.io.ExistIOException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.fail;

/**
 *  Insert documents for validation tests.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class DatabaseInsertResources_WithValidation_Test {
    
    private final static Logger logger = Logger.getLogger(DatabaseInsertResources_WithValidation_Test.class);
    
    private static String eXistHome = ConfigurationHelper.getExistHome().getAbsolutePath();
    private static BrokerPool pool;
    private static Configuration config;

    
    @BeforeClass
    public static void startup()
    {

        DBBroker broker = null;
        TransactionManager transact = null;
        Txn txn = null;
        try
        {
            config = new Configuration();
            config.setProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "auto");
            BrokerPool.configure(1, 5, config);
            pool = BrokerPool.getInstance();


            broker = pool.get(User.DEFAULT);
            transact = pool.getTransactionManager();
            txn = transact.beginTransaction();

            /** create nessecary collections if they dont exist */

            Collection col = broker.getOrCreateCollection(txn, XmldbURI.create(TestTools.VALIDATION_TMP));
            broker.saveCollection(txn, col);
        }
        catch(Exception e)
        {
            if(transact != null && txn != null)
                transact.abort(txn);

            e.printStackTrace();
            fail(e.getMessage());
        }
        finally
        {
            if(broker != null)
                pool.release(broker);
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
     */
    @Test
    public void testValidDocumentSystemCatalog(){
        
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
            
            URL url = new URL("xmldb:exist://" + TestTools.VALIDATION_TMP + "/hamlet_valid.xml");
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
    @Test
    public void invalidDocumentSystemCatalog(){
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
            
            URL url = new URL("xmldb:exist://" + TestTools.VALIDATION_TMP + "/hamlet_valid.xml");
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
    
    
    @AfterClass
    public static void shutdown() {
        BrokerPool.stopAll(true);
    }
}
