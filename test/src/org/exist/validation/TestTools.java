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
import org.apache.commons.io.output.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import org.exist.util.ConfigurationHelper;

/**
 *  A set of helper methods for the validation tests.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class TestTools {

    public final static String VALIDATION_HOME_COLLECTION = "validation";
    public final static String VALIDATION_DTD_COLLECTION = "dtd";
    public final static String VALIDATION_XSD_COLLECTION = "xsd";
    public final static String VALIDATION_TMP_COLLECTION = "tmp";
    

    /*
    public static void insertResources(){

        try {
            String eXistHome = ConfigurationHelper.getExistHome().getAbsolutePath();

            Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
            Database database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
            Collection root = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);
            XPathQueryService service = (XPathQueryService) root.getService("XQueryService", "1.0");

            CollectionManagementService cmservice = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
            Collection col1 = cmservice.createCollection(TestTools.VALIDATION_HOME);
            Collection col2 = cmservice.createCollection(TestTools.VALIDATION_XSD);

            Permission permission = PermissionAiderFactory.getPermission("guest", "guest", 999);

            UserManagementService umservice = (UserManagementService) root.getService("UserManagementService", "1.0");
            umservice.setPermissions(col1, permission);
            umservice.setPermissions(col2, permission);

            String addressbook = eXistHome + "/samples/validation/addressbook";

            TestTools.insertDocumentToURL(addressbook + "/addressbook.xsd",
                    "xmldb:exist://" + TestTools.VALIDATION_XSD + "/addressbook.xsd");
            TestTools.insertDocumentToURL(addressbook + "/catalog.xml",
                    "xmldb:exist://" + TestTools.VALIDATION_XSD + "/catalog.xml");

            TestTools.insertDocumentToURL(addressbook + "/addressbook_valid.xml",
                    "xmldb:exist://" + TestTools.VALIDATION_HOME + "/addressbook_valid.xml");
            TestTools.insertDocumentToURL(addressbook + "/addressbook_invalid.xml",
                    "xmldb:exist://" + TestTools.VALIDATION_HOME + "/addressbook_invalid.xml");

        } catch (Throwable ex) {
            ex.printStackTrace();
        }
    }*/
    
    // Transfer bytes from in to out
    public static void copyStream(InputStream is, OutputStream os) throws IOException {
        byte[] buf = new byte[1024];
        int len;
        while ((len = is.read(buf)) > 0) {
            os.write(buf, 0, len);
        }
    }
    
    /**
     *
     * @param file     File to be uploaded
     * @param target  Target URL (e.g. xmldb:exist:///db/collection/document.xml)
     * @throws java.lang.Exception  Oops.....
     */
    public static void insertDocumentToURL(String file, String target) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(file);
            final URL url = new URL(target);
            final URLConnection connection = url.openConnection();
            os = connection.getOutputStream();
            TestTools.copyStream(is, os);
        } finally {
            if(is != null){
                is.close();
            }
            if(os != null) {
                os.close();
            }
        }
    }

    public static String getEXistHome() {
        return ConfigurationHelper.getExistHome().getAbsolutePath();
    }

    public static byte[] getHamlet() throws IOException {
        return loadSample("shakespeare/hamlet.xml");
    }

    public static byte[] loadSample(String sampleRelativePath) throws IOException {
        File file = new File(getEXistHome(), "samples/" + sampleRelativePath);
        InputStream fis = null;
        ByteArrayOutputStream baos = null;
        try {
            fis = new FileInputStream(file);
            baos = new ByteArrayOutputStream();
            TestTools.copyStream(fis, baos);
        } finally {
            if(fis != null){
                fis.close();
            }
            if(baos != null) {
                baos.close();
            }
        }
        return baos.toByteArray();
    }

    public static void insertDocumentToURL(byte[] data, String target) throws IOException {
        final URL url = new URL(target);
        final URLConnection connection = url.openConnection();
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new ByteArrayInputStream(data);
            os = connection.getOutputStream();
            TestTools.copyStream(is, os);
            os.flush();
         } finally {
            if(is != null){
                is.close();
            }
            if(os != null) {
                os.close();
            }
        }
    }
}