/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
package org.exist.test;

import static org.junit.Assert.*;
import org.junit.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.transform.OutputKeys;
import org.apache.log4j.*;

import org.exist.util.MimeTable;
import org.exist.util.MimeType;

import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;

import org.apache.commons.io.output.ByteArrayOutputStream;

import org.exist.storage.serializers.EXistOutputKeys;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.CompiledExpression;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.modules.XQueryService;

/**
 *
 * @author dizzzz
 */
public class EmbeddedExistTester {

    protected final static Logger LOG = Logger.getLogger(EmbeddedExistTester.class);
    protected final static String URI = XmldbURI.LOCAL_DB;
    protected final static String DRIVER = "org.exist.xmldb.DatabaseImpl";
    
    protected static Collection rootCollection = null;

    protected static XPathQueryService xpxqService = null;
    protected static Database database = null;
    protected static CollectionManagementService cmService = null;
    protected static XQueryService xqService = null;

    private static boolean isInitialized=false;

    public static void initLog4J() {
        if(!isInitialized){
            Layout layout = new PatternLayout("%d{ISO8601} [%t] %-5p (%F [%M]:%L) - %m %n");
            Appender appender = new ConsoleAppender(layout);
            BasicConfigurator.resetConfiguration();
            BasicConfigurator.configure(appender);
            LOG.setLevel(Level.INFO);
            isInitialized=true;
        }
    }

    @BeforeClass
    public static void before() {
        try {
            System.out.println("Starting test..");
            initLog4J();
            LOG.info("Starting test..");

            Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
            database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);

            rootCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
            xpxqService = (XPathQueryService) rootCollection.getService("XPathQueryService", "1.0");
            cmService = (CollectionManagementService) rootCollection.getService("CollectionManagementService", "1.0");
            xqService = (XQueryService) rootCollection.getService("XQueryService", "1.0");

        } catch (Throwable ex) {
            LOG.error(ex);
            fail(ex.getMessage());
        }
    }

    @AfterClass
    public static void after() {
        try {
            LOG.info("Stopping test..");
            DatabaseManager.deregisterDatabase(database);
            DatabaseInstanceManager dim = (DatabaseInstanceManager) rootCollection.getService("DatabaseInstanceManager", "1.0");
            dim.shutdown();
            database = null;

        } catch (Exception ex) {
            LOG.error(ex);
            fail(ex.getMessage());
        }
    }

    @Before
    public void before_test(){
        System.out.println("\n-------------------------------------------------------\n");
    }

    protected static Collection createCollection(Collection collection, String collectionName) throws XMLDBException {
        LOG.info("Create collection " + collectionName);
        Collection newCollection = collection.getChildCollection(collectionName);
        if (newCollection == null) {
            cmService.createCollection(collectionName);
        }

        newCollection = DatabaseManager.getCollection(URI + "/" + collectionName, "admin", "");
        assertNotNull(newCollection);
        return newCollection;
    }

    protected static void storeResource(Collection collection, String documentName, byte[] content) throws XMLDBException {

        LOG.info("Store " + documentName);
        MimeType mime = MimeTable.getInstance().getContentTypeFor(documentName);

        String type = mime.isXMLType() ? "XMLResource" : "BinaryResource";
        Resource resource = collection.createResource(documentName, type);
        resource.setContent(content);
        collection.storeResource(resource);
        collection.close();
    }

    protected static ResourceSet executeQuery(String query) throws XMLDBException {
        LOG.info("Executing " + query);
        CompiledExpression compiledQuery = xqService.compile(query);
        ResourceSet result = xqService.execute(compiledQuery);
        return result;
    }
    
    protected static String executeOneValue(String query){
        String r = null;
        try {
            ResourceSet results = executeQuery(query);
            assertEquals(1, results.getSize());
            r = (String) results.getResource(0).getContent();
            
        } catch (Exception ex) {
            LOG.error(ex);
            fail(ex.getMessage());
        }
        return r;
    }
    

    protected static byte[] readFile(File directory, String filename) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        File src = new File(directory, filename);

        LOG.info("Reading file: " + src.getAbsolutePath());

        assertTrue(src.canRead());

        InputStream in = new FileInputStream(src);

        // Transfer bytes from in to out
        byte[] buf = new byte[4096];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();

        return out.toByteArray();
    }

    protected String getXMLResource(Collection collection, String resource) throws XMLDBException{
		collection.setProperty(OutputKeys.INDENT, "yes");
		collection.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "no");
        collection.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "yes");
		XMLResource res = (XMLResource)collection.getResource(resource);
        String retval = res.getContent().toString();
        collection.close();
        return retval;
    }
}
