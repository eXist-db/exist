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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 *  $Id$
 */

package org.exist;

import org.xmldb.api.base.Database;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.DatabaseManager;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.security.User;

/**
 * Initial database setup: called from the installer to set the admin password.
 */
public class Setup {

    private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";
    private final static String URI = "xmldb:exist:///db";

    public static void main(String[] args) {
        if (args.length < 1 || args[0].length() == 0) {
            System.err.println("No password specified. Aborting.");
            return;
        }
        initDb(args[0]);
        shutdown(args[0]);
    }

    private static void initDb(String adminPass) {
        System.out.println("--- Starting embedded database instance ---");
        try {
            Class cl = Class.forName(DRIVER);
            Database database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
            Collection root = DatabaseManager.getCollection(URI, "admin", "");
            UserManagementService service =
                    (UserManagementService) root.getService("UserManagementService", "1.0");
            User admin = service.getUser("admin");
            admin.setPassword(adminPass);
            System.out.println("Setting admin user password...");
            service.updateUser(admin);
        } catch (Exception e) {
            System.err.println("Caught an exception while initializing db: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void shutdown(String adminPass) {
        System.out.println("--- Initialization complete. Shutdown embedded database instance ---");
        try {
            Collection root = DatabaseManager.getCollection(URI, "admin", adminPass);
            DatabaseInstanceManager manager = (DatabaseInstanceManager)
                    root.getService("DatabaseInstanceManager", "1.0");
            manager.shutdown();
        } catch (XMLDBException e) {
            System.err.println("Caught an exception while initializing db: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
