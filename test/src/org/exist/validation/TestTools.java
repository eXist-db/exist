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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import org.exist.security.Permission;
import org.exist.security.internal.aider.UnixStylePermission;
import org.exist.storage.DBBroker;
import org.exist.util.ConfigurationHelper;
import org.exist.xmldb.UserManagementService;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XPathQueryService;

/**
 *  A set of helper methods for the validation tests.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class TestTools {
    
    public final static String VALIDATION_HOME="/db/validation";
    public final static String VALIDATION_DTD=VALIDATION_HOME+"/dtd";
    public final static String VALIDATION_XSD=VALIDATION_HOME+"/xsd";
    public final static String VALIDATION_TMP=VALIDATION_HOME+"/tmp";
    

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

            Permission permission = new UnixStylePermission("guest", "guest", 999);

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
    }
    
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
    public static void insertDocumentToURL(String file, String target) throws Exception{
        
        InputStream is = new FileInputStream(file);
        
        URL url = new URL(target);
        URLConnection connection = url.openConnection();
        OutputStream os = connection.getOutputStream();
        
        TestTools.copyStream(is, os);
        
        is.close();
        os.close();
    }
    
}
